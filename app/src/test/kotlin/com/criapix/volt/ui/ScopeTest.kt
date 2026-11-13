package com.criapix.volt.ui

import com.criapix.volt.TestParams
import com.criapix.volt.midi.Synth
import com.criapix.volt.state.SynthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeTest {
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
  private val live = Live(state, algorithms = { List(32) { Algo(setOf(1), emptyMap(), 0, 0) } }, clock = { now }).also { state.observer = it }
  private val vals = Vals(state)
  private val buf = FloatArray(240)

  private fun set(name: String, real: Float) { val p = params[name]!!; state.set(p.id, p.toNorm(real)) }
  private fun play(note: Int) { live.update(); state.noteOn(note, 1f); now += 0.3; live.update() }

  private fun peak(): Float = buf.maxOf { kotlin.math.abs(it) }

  @Test fun silentWithoutANote() {
    live.update()
    Scope.fill(live, vals, buf, 2.5f)
    assertEquals(0f, peak(), 0f)
  }

  @Test fun theAnalogWaveIsFiniteAndAudibleAtEveryCutoffAndPitch() {
    for (cutoff in listOf(40f, 500f, 6800f, 20000f)) for (note in listOf(24, 48, 62, 84, 108)) for (reso in listOf(0f, 0.2f, 0.9f)) {
      set("osc1.level", 1f); set("filter.cutoff", cutoff); set("filter.reso", reso); set("lfo1.cutoff", 0f)
      now = 100.0; live.update(); state.allNotesOff(); now += 5; live.update()
      play(note)
      Scope.fill(live, vals, buf, 2.5f)
      assertTrue("cutoff $cutoff note $note reso $reso: not finite", buf.all { it.isFinite() })
      assertTrue("cutoff $cutoff note $note reso $reso: peak ${peak()}", peak() > 0.003f)
      assertTrue("cutoff $cutoff note $note reso $reso: peak ${peak()} too large", peak() < 4f)
    }
  }

  @Test fun theFmWaveIsFiniteAndAudible() {
    set("engine.type", 1f)
    live.update()
    state.noteOn(60, 1f); now += 0.2; live.update()
    Scope.fill(live, vals, buf, 2.5f)
    assertTrue(buf.all { it.isFinite() })
    assertTrue("peak ${peak()}", peak() > 0.02f)
  }

  @Test fun aLegatoLeadWithGlideDrawsItsWaveAndLightsTheLatestKey() {
    // the factory preset "Lead Sync"
    set("voice.mode", 2f); set("voice.glide", 0.03f); set("osc1.sync", 1f); set("osc2.level", 0.25f); set("filter.model", 3f)
    set("filter.cutoff", 6000f); set("filter.reso", 0.2f); set("filter.env", 0.1f); set("aenv.sustain", 1f); set("lfo1.pitch", 0.1f); set("lfo1.wheel", 1f)
    live.update()
    state.noteOn(62, 1f); now += 0.2; live.update()
    state.noteOn(67, 1f); now += 0.2; live.update()
    assertEquals(1, live.voices.size)
    assertEquals(67, live.voices[0].note)
    assertTrue(live.noteLevels[67] > 0.5f)
    assertEquals(0f, live.noteLevels[62], 0f)
    Scope.fill(live, vals, buf, 2.5f)
    assertTrue(buf.all { it.isFinite() })
    assertTrue("peak ${peak()}", peak() > 0.02f)
  }
}
