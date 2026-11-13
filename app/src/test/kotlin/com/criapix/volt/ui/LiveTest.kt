package com.criapix.volt.ui

import com.criapix.volt.TestParams
import com.criapix.volt.midi.Synth
import com.criapix.volt.state.SynthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTest {
  private object Quiet : Synth {
    override fun noteOn(note: Int, velocity: Float) {}
    override fun noteOff(note: Int) {}
    override fun setParam(id: Int, normalized: Float) {}
    override fun pitchBend(value: Float) {}
    override fun modWheel(value: Float) {}
    override fun sustain(on: Boolean) {}
    override fun allNotesOff() {}
  }

  private val params = TestParams.params
  private val state = SynthState(params, Quiet)
  private var now = 100.0
  private val algos = List(32) { Algo(carriers = setOf(1), modulators = emptyMap(), feedback = 0, feedbackFrom = 0) }
  private val live = Live(state, peak = { 0f }, algorithms = { algos }, clock = { now }).also { state.observer = it }

  private fun set(name: String, real: Float) { val p = params[name]!!; state.set(p.id, p.toNorm(real)) }
  private fun step(seconds: Double) { now += seconds; live.update() }

  @Test fun aNoteRisesThroughTheEnvelopeAndDiesAfterTheRelease() {
    set("aenv.attack", 0.1f); set("aenv.decay", 0.2f); set("aenv.sustain", 0.5f); set("aenv.release", 0.2f); set("aenv.vel", 0f)
    live.update()
    state.noteOn(60, 1f)
    step(0.05)
    assertEquals(0.5f, live.amp, 0.05f)
    assertTrue(live.stage[Live.A] > 0.5f)
    step(0.15)  // 0.2 s: past the attack, inside the decay
    assertTrue(live.stage[Live.D] > 0.5f)
    step(1.0)
    assertTrue(live.stage[Live.S] > 0.5f)
    assertEquals(0.5f, live.amp, 0.03f)
    state.noteOff(60)
    step(0.05)
    assertTrue(live.stage[Live.R] > 0f)
    assertTrue(live.amp < 0.5f)
    step(1.0)
    assertEquals(0, live.voices.size)
    assertEquals(0f, live.amp, 0f)
  }

  @Test fun theSustainPedalHoldsTheNote() {
    live.update()
    state.sustain(true)
    state.noteOn(64, 1f)
    step(0.5)
    state.noteOff(64)
    step(2.0)
    assertEquals(1, live.voices.size)
    state.sustain(false)
    step(2.0)
    assertEquals(0, live.voices.size)
  }

  @Test fun lfoPhaseFollowsItsRate() {
    set("lfo1.rate", 2f); set("lfo1.delay", 0f)
    live.update()
    val p0 = live.lfoPhase1
    repeat(25) { step(0.01) }  // a frame never counts for more than 0.1 s, so step in small frames
    assertEquals(0.5f, live.lfoPhase1 - p0, 0.01f)
  }

  @Test fun theLfoFadesInAfterANote() {
    set("lfo1.delay", 1f)
    live.update()
    state.noteOn(60, 1f)
    step(0.01)
    assertTrue(live.lfoFade1 < 0.1f)
    step(0.5)
    assertEquals(0.5f, live.lfoFade1, 0.05f)
    step(1.0)
    assertEquals(1f, live.lfoFade1, 0f)
  }

  @Test fun theEffectiveCutoffFollowsTheFilterEnvelope() {
    set("filter.cutoff", 500f); set("filter.env", 0.5f); set("fenv.attack", 0.05f); set("fenv.sustain", 0f); set("filter.keytrack", 0f)
    set("lfo1.cutoff", 0f); set("lfo2.cutoff", 0f)
    live.update()
    step(0.01)
    assertEquals(500f, live.cutoff, 1f)
    state.noteOn(60, 1f)
    step(0.06)  // the envelope is at its peak: +0.5 * 7 octaves
    assertTrue("cutoff ${live.cutoff}", live.cutoff > 4000f)
  }

  @Test fun echoesShowUpOneDelayTimeAfterTheNote() {
    set("fx.delay.on", 1f); set("fx.delay.time", 0.4f); set("fx.delay.feedback", 0.5f); set("fx.delay.mix", 0.5f)
    live.update()
    state.noteOn(60, 1f)
    step(0.2)
    assertEquals(0f, live.echo, 0.01f)
    step(0.21)  // just past 0.4 s: the first echo
    assertTrue("echo ${live.echo}", live.echo > 0.5f)
    set("fx.delay.on", 0f)
    step(0.01)
    assertEquals(0f, live.echo, 0f)
  }

  @Test fun theReverbTailOutlivesTheNote() {
    set("fx.reverb.on", 1f); set("fx.reverb.decay", 4f); set("fx.reverb.mix", 0.5f); set("aenv.release", 0.05f)
    live.update()
    state.noteOn(60, 1f)
    step(0.3)
    state.noteOff(60)
    repeat(10) { step(0.1) }
    assertEquals(0, live.voices.size)
    assertTrue("halo ${live.halo}", live.halo > 0.1f)
    repeat(100) { step(0.1) }  // ten seconds
    assertTrue("halo ${live.halo}", live.halo < 0.05f)
  }

  @Test fun theArpeggiatorPlaysTheHeldNotesInOrder() {
    set("arp.on", 1f); set("arp.bpm", 120f); set("arp.division", 2f); set("arp.gate", 0.5f); set("arp.mode", 0f); set("arp.octaves", 1f)
    live.update()
    state.noteOn(60, 1f); state.noteOn(64, 1f)
    step(0.01)
    assertTrue(live.voices.any { it.note == 60 })
    assertTrue(live.arpPulse > 0f)
    step(0.125)  // 120 bpm, 1/16 = 0.125 s per step
    assertTrue(live.voices.any { it.note == 64 })
    assertEquals(listOf(60, 64), live.arpSequence().toList())
  }

  @Test fun keysLightWhileTheirNoteSounds() {
    live.update()
    state.noteOn(72, 1f)
    step(0.05)
    assertTrue(live.noteLevels[72] > 0.1f)
    assertEquals(0f, live.noteLevels[60], 0f)
  }

  @Test fun fmOperatorsFollowTheirEnvelopesAndRelease() {
    set("engine.type", 1f)
    set("fm.op1.r1", 99f); set("fm.op1.l1", 99f); set("fm.op1.r4", 40f); set("fm.op1.l4", 0f)
    set("fm.op1.level", 99f); set("fm.op1.vel", 0f)
    live.update()
    state.noteOn(60, 1f)
    step(0.05)
    assertTrue("op1 ${live.op[1]}", live.op[1] > 0.5f)
    assertTrue(live.amp > 0.5f)  // op 1 is the carrier
    state.noteOff(60)
    step(0.01)
    assertEquals(4, live.opStage[1])
    step(1.0)
    assertEquals(0, live.voices.size)
  }

  @Test fun keyboardScalingLowersAnOperatorAwayFromItsBreakPoint() {
    set("engine.type", 1f)
    set("fm.op1.kls.break", 39f); set("fm.op1.kls.ldepth", 60f); set("fm.op1.kls.lcurve", 0f)  // -LIN to the left
    live.update()
    assertEquals(0f, live.keyScale(1, 60), 0.01f)
    assertTrue(live.keyScale(1, 21) < -40f)
  }

  @Test fun aMonoVoiceReleasesTheOneBeforeIt() {
    set("voice.mode", 1f)
    live.update()
    state.noteOn(60, 1f); step(0.01)
    state.noteOn(64, 1f); step(0.01)
    assertEquals(1, live.voices.count { !it.released })
  }

  @Test fun theWheelsAreRecorded() {
    live.update()
    state.modWheel(0.7f); state.pitchBend(-0.5f)
    step(0.01)
    assertEquals(0.7f, live.wheel, 1e-6f)
    assertEquals(-0.5f, live.bend, 1e-6f)
  }
}
