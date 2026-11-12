package com.criapix.volt.midi

import com.criapix.volt.TestParams
import com.criapix.volt.state.SynthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiRoutingTest {
  private class Recorder : Synth {
    val notes = ArrayList<Int>()
    override fun noteOn(note: Int, velocity: Float) { notes += note }
    override fun noteOff(note: Int) { notes += -note }
    override fun setParam(id: Int, normalized: Float) {}
    override fun pitchBend(value: Float) {}
    override fun modWheel(value: Float) {}
    override fun sustain(on: Boolean) {}
    override fun allNotesOff() {}
  }

  private val p = TestParams.params
  private val rec = Recorder()
  private val state = SynthState(p, rec)
  private val learn = MidiLearn()
  private val actions = ArrayList<MidiAction>()
  private val programs = ArrayList<Int>()
  private var now = 0L
  private val d = MidiDispatch(
    synth = state, params = p, learn = learn, current = state::norm,
    onAction = { actions += it }, onProgram = { programs += it }, clock = { now },
  )
  private fun send(vararg b: Int) { val a = ByteArray(b.size) { b[it].toByte() }; d.message(a, a.size) }
  private fun id(name: String) = p[name]!!.id
  private fun learnCc(target: Target, cc: Int, value: Int = 64) { learn.setActive(true); learn.arm(target); send(0xB0, cc, value); learn.setActive(false) }

  @Test fun aLearnedControllerDrivesItsParameterAtOnce() {
    learnCc(Target.Param("osc1.level"), 20, value = 10)
    assertEquals(1f, state.real(id("osc1.level")), 0f)      // the learning message itself changes nothing
    send(0xB0, 20, 0)                                       // attached on learn: acts immediately
    assertEquals(0f, state.real(id("osc1.level")), 0f)
  }

  @Test fun aLearnedBindingOverridesTheFactoryMap() {
    learnCc(Target.Param("osc1.level"), 74)
    send(0xB0, 74, 0)
    assertEquals(0f, state.real(id("osc1.level")), 0f)
    assertEquals(20000f, state.real(id("filter.cutoff")), 1f)
  }

  @Test fun theGeneralMidiSoundControllersReachTheAmplitudeEnvelope() {
    d.softTakeover = false
    send(0xB0, 73, 127); send(0xB0, 72, 127); send(0xB0, 75, 0)
    assertEquals(10f, state.real(id("aenv.attack")), 0.01f)
    assertEquals(10f, state.real(id("aenv.release")), 0.01f)
    assertEquals(0.001f, state.real(id("aenv.decay")), 0.0001f)
  }

  @Test fun generalMidiEffectControllersSetTheMixAndSwitchTheEffectOn() {
    d.softTakeover = false
    send(0xB0, 91, 100)
    assertEquals(1f, state.real(id("fx.reverb.on")), 0f)
    assertEquals(100 / 127f, state.norm(id("fx.reverb.mix")), 0.01f)
    send(0xB0, 93, 0)                                   // zero does not switch an effect on
    assertEquals(0f, state.real(id("fx.chorus.on")), 0f)
    send(0xB0, 95, 64)
    assertEquals(1f, state.real(id("fx.phaser.on")), 0f)
  }

  @Test fun softTakeoverWaitsForThePickupAndSaysWhere() {
    val cutoff = id("filter.cutoff")                         // default 20 kHz = 1.0
    send(0xB0, 74, 0)
    assertEquals(1f, state.norm(cutoff), 0f)
    assertEquals(cutoff, d.pickup.value?.param)
    send(0xB0, 74, 127)                                      // reaches the value
    assertNull(d.pickup.value)
    send(0xB0, 74, 0)                                        // in control: follows
    assertEquals(0f, state.norm(cutoff), 0f)
  }

  @Test fun loadingAPresetDetachesTheController() {
    val cutoff = id("filter.cutoff")
    send(0xB0, 74, 127)
    state.load(state.snapshot().also { it[cutoff] = 0.2f })
    send(0xB0, 74, 120)
    assertEquals(0.2f, state.norm(cutoff), 1e-6f)
    assertNotNull(d.pickup.value)
  }

  @Test fun turningTheKnobOnScreenDetachesTheController() {
    val cutoff = id("filter.cutoff")
    send(0xB0, 74, 127)
    state.set(cutoff, 0.3f)
    send(0xB0, 74, 126)
    assertEquals(0.3f, state.norm(cutoff), 1e-6f)
  }

  @Test fun notesLearnOnlyForActionsAndALearnedNoteDoesNotPlay() {
    learn.setActive(true)
    learn.arm(Target.Param("filter.cutoff"))
    send(0x90, 60, 100)                                       // a param target ignores notes: the note plays
    assertEquals(listOf(60), rec.notes)
    learn.arm(Target.Action(MidiAction.NEXT))
    send(0x90, 36, 100)                                       // learned, not played
    learn.setActive(false)
    send(0x90, 36, 100); send(0x80, 36, 0)
    assertEquals(listOf(60), rec.notes)
    assertEquals(listOf(MidiAction.NEXT), actions)
  }

  @Test fun anActionFiresOncePerPress() {
    learnCc(Target.Action(MidiAction.NEXT), 30, value = 0)
    send(0xB0, 30, 127); send(0xB0, 30, 127); send(0xB0, 30, 0); send(0xB0, 30, 127)
    assertEquals(2, actions.size)
  }

  @Test fun tapModeFiresOnAnyValueWithADebounce() {
    learnCc(Target.Action(MidiAction.PREV), 31, value = 0)
    learn.setTap(Source.Cc(0, 31), true)
    now = 1_000_000_000L; send(0xB0, 31, 127)
    now += 50_000_000L; send(0xB0, 31, 0)                     // 50 ms later: bounce, ignored
    now += 400_000_000L; send(0xB0, 31, 0)                    // a real second press
    assertEquals(listOf(MidiAction.PREV, MidiAction.PREV), actions)
  }

  @Test fun tapModeFlipsAnOnOffParameter() {
    val sync = id("osc1.sync")
    learnCc(Target.Param("osc1.sync"), 33, value = 0)
    learn.setTap(Source.Cc(0, 33), true)
    now = 1_000_000_000L; send(0xB0, 33, 127)
    assertEquals(1f, state.real(sync), 0f)
    now += 400_000_000L; send(0xB0, 33, 0)
    assertEquals(0f, state.real(sync), 0f)
  }

  @Test fun programChangeIsReported() {
    send(0xC0, 5)
    assertEquals(listOf(5), programs)
  }

  @Test fun aBindingToAParameterThatNoLongerExistsIsIgnored() {
    learn.setActive(true); learn.arm(Target.Param("no.such")); send(0xB0, 40, 1); learn.setActive(false)
    send(0xB0, 40, 127)  // must not throw
    assertTrue(actions.isEmpty())
  }
}
