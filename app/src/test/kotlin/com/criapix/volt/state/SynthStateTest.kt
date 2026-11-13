package com.criapix.volt.state

import com.criapix.volt.TestParams
import com.criapix.volt.midi.Synth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthStateTest {
  private class Fake : Synth {
    val params = ArrayList<Pair<Int, Float>>()
    var patches = 0
    var acceptPatch = true
    var notes = 0
    val log = ArrayList<String>()
    override fun aftertouch(value: Float) { log += "at $value" }
    override fun breath(value: Float) { log += "breath $value" }
    override fun noteOn(note: Int, velocity: Float) { notes++ }
    override fun noteOff(note: Int) {}
    override fun setParam(id: Int, normalized: Float) { params += id to normalized }
    override fun pitchBend(value: Float) {}
    override fun modWheel(value: Float) {}
    override fun sustain(on: Boolean) {}
    override fun allNotesOff() {}
    override fun loadPatch(values: FloatArray): Boolean { patches++; return acceptPatch }
  }

  private val p = TestParams.params
  private val cutoff = p["filter.cutoff"]!!
  private val wave = p["osc1.wave"]!!

  @Test fun startsAtTheDefaults() {
    val s = SynthState(p, Fake())
    assertEquals(cutoff.toNorm(cutoff.def), s.norm(cutoff.id), 1e-6f)
    assertEquals(cutoff.def, s.real(cutoff.id), 0.5f)
    assertFalse(s.dirty.value)
  }

  @Test fun aScreenChangeReachesTheEngineAndMarksDirtyWithoutFlashing() {
    val f = Fake()
    val s = SynthState(p, f)
    s.set(cutoff.id, 0.25f)
    assertEquals(cutoff.id to 0.25f, f.params.last())
    assertEquals(0.25f, s.value(cutoff.id).value, 1e-6f)
    assertTrue(s.dirty.value)
    assertEquals(-1, s.external.value.id)
  }

  @Test fun aMidiChangeIsReportedAsExternal() {
    val s = SynthState(p, Fake())
    s.setParam(cutoff.id, 0.5f)  // the Synth interface: what MidiDispatch calls
    assertEquals(cutoff.id, s.external.value.id)
    val first = s.external.value.seq
    s.setParam(cutoff.id, 0.6f)
    assertTrue(s.external.value.seq > first)
  }

  @Test fun stepParametersAreQuantized() {
    val s = SynthState(p, Fake())
    s.set(wave.id, 0.40f)  // 0..3 -> 1.2 -> 1
    assertEquals(1f, s.real(wave.id), 0f)
    assertEquals(wave.toNorm(1f), s.norm(wave.id), 1e-6f)
  }

  @Test fun loadingAPatchSetsEveryValueSendsOnePatchAndIsClean() {
    val f = Fake()
    val s = SynthState(p, f)
    s.set(cutoff.id, 0.1f)
    val values = s.snapshot().also { it[cutoff.id] = 0.9f }
    s.load(values)
    assertEquals(1, f.patches)
    assertEquals(0.9f, s.norm(cutoff.id), 1e-6f)
    assertFalse(s.dirty.value)
  }

  @Test fun aRefusedPatchFallsBackToSingleChanges() {
    val f = Fake().apply { acceptPatch = false }
    val s = SynthState(p, f)
    s.load(s.snapshot())
    assertEquals(p.all.size, f.params.size)
  }

  @Test fun notesPassStraightThrough() {
    val f = Fake()
    SynthState(p, f).noteOn(60, 1f)
    assertEquals(1, f.notes)
  }

  @Test fun aftertouchAndBreathReachTheEngine() {
    val f = Fake()
    val s = SynthState(p, f)
    s.aftertouch(0.5f)
    s.breath(1f)
    assertEquals(listOf("at 0.5", "breath 1.0"), f.log)
  }

  @Test fun theObserverSeesTheperformance() {
    val f = Fake()
    val s = SynthState(p, f)
    val seen = ArrayList<String>()
    s.observer = object : SynthState.PerformanceObserver {
      override fun noteOn(note: Int, velocity: Float) { seen += "on $note" }
      override fun noteOff(note: Int) { seen += "off $note" }
      override fun sustain(on: Boolean) { seen += "sus $on" }
      override fun allNotesOff() { seen += "panic" }
      override fun pitchBend(value: Float) { seen += "bend $value" }
      override fun modWheel(value: Float) { seen += "mod $value" }
    }
    s.noteOn(60, 0.8f); s.noteOff(60); s.sustain(true); s.allNotesOff(); s.pitchBend(0.5f); s.modWheel(1f)
    assertEquals(listOf("on 60", "off 60", "sus true", "panic", "bend 0.5", "mod 1.0"), seen)
    assertEquals(1, f.notes)  // and the engine still got the note
  }
}
