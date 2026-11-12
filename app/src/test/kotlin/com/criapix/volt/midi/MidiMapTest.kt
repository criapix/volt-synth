package com.criapix.volt.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiMapTest {
  private val cutoff = Target.Param("filter.cutoff")
  private val next = Target.Action(MidiAction.NEXT)

  @Test fun aSourceHasOneTargetAndATargetMayHaveSeveralSources() {
    val m = MidiMap()
    m.bind(Source.Cc(0, 74), cutoff)
    m.bind(Source.Cc(0, 20), cutoff)
    m.bind(Source.Cc(0, 74), next)  // re-learning a source replaces its target
    assertEquals(next, m[Source.Cc(0, 74)]?.target)
    assertEquals(listOf(Source.Cc(0, 20)), m.sourcesOf(cutoff))
  }

  @Test fun encodeDecodeRoundTripsIncludingTapAndDottedNames() {
    val m = MidiMap()
    m.bind(Source.Cc(0, 74), cutoff)
    m.bind(Source.Note(2, 36), next, tap = true)
    val back = MidiMap.decode(m.encode())
    assertEquals(m.entries(), back.entries())
    assertTrue(back[Source.Note(2, 36)]!!.tap)
  }

  @Test fun damagedEntriesAreSkipped() {
    val m = MidiMap.decode("cc.0.74=p:filter.cutoff;lixo;cc.99.1=p:x;note.0.200=a:next;cc.0.5=a:nope;cc.0.6=q:z")
    assertEquals(1, m.entries().size)
    assertNull(m[Source.Cc(0, 5)])
  }

  @Test fun clearRemovesEveryBindingOfATarget() {
    val m = MidiMap()
    m.bind(Source.Cc(0, 74), cutoff)
    m.bind(Source.Cc(0, 20), cutoff)
    m.bind(Source.Cc(0, 30), next)
    m.clear(cutoff)
    assertEquals(listOf(Source.Cc(0, 30)), m.entries().map { it.first })
    m.clearAll()
    assertTrue(m.entries().isEmpty())
  }

  @Test fun sourcesAreDescribedForTheUser() {
    assertEquals("CC 74 · canal 1", describe(Source.Cc(0, 74)))
    assertEquals("Nota C4 · canal 3", describe(Source.Note(2, 60)))
  }
}
