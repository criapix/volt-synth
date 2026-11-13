package com.criapix.volt.ui

import com.criapix.volt.TestParams
import com.criapix.volt.midi.Synth
import com.criapix.volt.state.SynthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
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
  private var now = 10.0
  private val live = Live(state, algorithms = { List(32) { Algo(setOf(1), emptyMap(), 0, 0) } }, clock = { now }).also { state.observer = it }
  private val fx = Fx()

  private fun set(name: String, real: Float) { val p = params[name]!!; state.set(p.id, p.toNorm(real)) }
  private fun step(s: Double) { now += s; live.update() }
  private fun eval(name: String): Fx { Feedback.eval(Feedback.of(name)!!, name, state, live, fx); return fx }

  @Test fun everyKnobHasAFeedback() {
    val knobs = params.all.map { it.name }.filter { ParamUi.of(it) is KnobUi }
    val missing = knobs.filter { Feedback.of(it) == null }
    assertTrue("no feedback for $missing", missing.isEmpty())
  }

  @Test fun everyKnobFeedbackEvaluatesWithoutErrors() {
    val knobs = params.all.map { it.name }.filter { ParamUi.of(it) is KnobUi }
    live.update()
    state.noteOn(60, 0.9f); step(0.05)
    for (n in knobs) eval(n)
    set("engine.type", 1f); live.update(); state.noteOn(62, 0.9f); step(0.05)
    for (n in knobs) eval(n)
  }

  @Test fun aLevelKnobGlowsOnlyWhileTheNoteSounds() {
    set("osc1.level", 1f); set("aenv.release", 0.05f)
    live.update()
    assertEquals(0f, eval("osc1.level").glow, 0f)
    state.noteOn(60, 1f); step(0.2)
    assertTrue(eval("osc1.level").glow > 0.5f)
    state.noteOff(60); step(1.0)
    assertEquals(0f, eval("osc1.level").glow, 0f)
  }

  @Test fun theCutoffKnobShowsWhereTheCutoffReallyIs() {
    set("filter.cutoff", 500f); set("filter.env", 0.5f); set("fenv.attack", 0.05f); set("fenv.sustain", 0f); set("filter.keytrack", 0f)
    set("lfo1.cutoff", 0f); set("lfo2.cutoff", 0f)
    live.update(); step(0.01)
    val rest = eval("filter.cutoff").ghost
    state.noteOn(60, 1f); step(0.06)
    val open = eval("filter.cutoff").ghost
    assertTrue("rest $rest, open $open", open > rest + 0.1f)
  }

  @Test fun theEnvelopeKnobsLightTheirOwnStage() {
    set("aenv.attack", 0.1f); set("aenv.decay", 0.3f); set("aenv.sustain", 0.5f)
    live.update(); state.noteOn(60, 1f); step(0.05)
    assertTrue(eval("aenv.attack").glow > 0.5f)
    assertEquals(0f, eval("aenv.decay").glow, 0f)
    step(0.15)
    assertTrue(eval("aenv.decay").glow > 0.5f)
    assertEquals(0f, eval("aenv.attack").glow, 0f)
  }

  @Test fun drivePushesTheColourTowardsHot() {
    set("mix.drive", 0.8f)
    live.update(); state.noteOn(60, 1f); step(0.2)
    val f = eval("mix.drive")
    assertTrue(f.hot > 0.9f)
    assertTrue(f.jit > 0f)
  }

  @Test fun resonanceRingsOnlyWithSound() {
    set("filter.reso", 0.9f)
    live.update()
    assertEquals(0f, eval("filter.reso").ring, 0f)
    state.noteOn(60, 1f); step(0.2)
    assertTrue(eval("filter.reso").glow > 0f)
  }

  @Test fun anOscillatorOctaveKnobDrawsAWaveDenserWhenHigher() {
    set("osc1.octave", 0f)
    live.update()
    val low = eval("osc1.octave").cycles
    set("osc1.octave", 2f)
    val high = eval("osc1.octave").cycles
    assertEquals(Inner.WAVE, eval("osc1.octave").inner)
    assertEquals(low * 4f, high, 0.01f)
  }

  @Test fun theLfoRateKnobPulsesWithTheLfo() {
    set("lfo1.rate", 1f); set("lfo1.wave", 0f); set("lfo1.delay", 0f)
    live.update()
    val seen = HashSet<Int>()
    repeat(40) { step(0.05); seen += (eval("lfo1.rate").pulse * 10).toInt() }
    assertTrue("pulse never moved: $seen", seen.size > 4)
  }

  @Test fun effectKnobsAreQuietWhenTheEffectIsOff() {
    set("fx.phaser.on", 0f); set("fx.phaser.mix", 1f)
    live.update(); state.noteOn(60, 1f); step(0.2)
    assertEquals(0f, eval("fx.phaser.mix").glow, 0f)
    set("fx.phaser.on", 1f)
    step(0.01)
    assertNotNull(Feedback.of("fx.phaser.mix"))
  }

  @Test fun operatorKnobsLightInTheirOperatorsColourStage() {
    set("engine.type", 1f)
    set("fm.op1.r1", 20f); set("fm.op1.l1", 99f)  // a slow attack
    live.update(); state.noteOn(60, 1f); step(0.05)
    assertEquals(1, live.opStage[1])
    assertTrue(eval("fm.op1.r1").glow > 0f)
    assertEquals(0f, eval("fm.op1.r2").glow, 0f)
  }
}
