package com.criapix.volt.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiLearnTest {
  private var saved = ""
  private val learn = MidiLearn(MidiMap()) { saved = it }
  private val cutoff = Target.Param("filter.cutoff")
  private val next = Target.Action(MidiAction.NEXT)

  @Test fun nothingIsCapturedOutsideLearnModeOrWithoutAnArmedTarget() {
    assertFalse(learn.capture(Source.Cc(0, 74)))
    learn.setActive(true)
    assertFalse(learn.capture(Source.Cc(0, 74)))
  }

  @Test fun captureBindsDisarmsSavesAndReports() {
    learn.setActive(true)
    learn.arm(cutoff)
    assertTrue(learn.capture(Source.Cc(0, 74)))
    assertEquals(cutoff, learn.lookup(Source.Cc(0, 74))?.target)
    assertNull(learn.armed.value)
    assertTrue(learn.active.value)                     // stays in Learn mode for the next control
    assertEquals("cc.0.74=p:filter.cutoff", saved)
    assertEquals(Source.Cc(0, 74), learn.learned.value?.source)
    assertEquals(1, learn.bindings.value.size)
  }

  @Test fun notesOnlyBindToActions() {
    learn.setActive(true)
    learn.arm(cutoff)
    assertFalse(learn.capture(Source.Note(0, 60)))
    assertEquals(cutoff, learn.armed.value)            // still waiting for a CC
    learn.arm(next)
    assertTrue(learn.capture(Source.Note(0, 36)))
  }

  @Test fun bankSelectAndChannelModeMessagesCannotBeLearned() {
    learn.setActive(true)
    learn.arm(cutoff)
    for (cc in listOf(0, 32, 120, 121, 123, 127)) assertFalse("CC $cc", learn.capture(Source.Cc(0, cc)))
  }

  @Test fun leavingLearnModeDisarms() {
    learn.setActive(true)
    learn.arm(cutoff)
    learn.setActive(false)
    assertNull(learn.armed.value)
    learn.arm(cutoff)                                  // ignored outside Learn mode
    assertNull(learn.armed.value)
  }

  @Test fun editsArePublishedAndSaved() {
    learn.setActive(true)
    learn.arm(cutoff); learn.capture(Source.Cc(0, 74))
    learn.arm(next); learn.capture(Source.Cc(0, 30))
    learn.setTap(Source.Cc(0, 30), true)
    assertTrue(learn.lookup(Source.Cc(0, 30))!!.tap)
    learn.unbind(Source.Cc(0, 74))
    assertEquals(listOf(Source.Cc(0, 30)), learn.bindings.value.map { it.first })
    assertEquals("cc.0.30=a:next!", saved)
    learn.clearAll()
    assertEquals("", saved)
  }
}
