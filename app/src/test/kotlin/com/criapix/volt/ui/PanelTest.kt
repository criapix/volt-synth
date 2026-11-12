package com.criapix.volt.ui

import com.criapix.volt.TestParams
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelTest {
  @Test fun everyParameterIsOnExactlyOneTab() {
    val placed = Panel.groups.values.flatten().flatMap { it.params }
    val names = TestParams.params.all.map { it.name }
    assertEquals(names.sorted(), placed.sorted())
  }

  @Test fun groupOfFindsTheGroup() {
    assertEquals("FILTRO", Panel.groupOf("filter.cutoff")?.title)
    assertEquals("ENVELOPE DE AMPLITUDE", Panel.groupOf("aenv.attack")?.title)
    assertEquals(null, Panel.groupOf("no.such"))
  }

  @Test fun tabOfFindsTheTab() {
    assertEquals(Tab.FILTER, Panel.tabOf("filter.cutoff"))
    assertEquals(Tab.ENV, Panel.tabOf("aenv.release"))
    assertEquals(Tab.MOD, Panel.tabOf("voice.mode"))
    assertEquals(Tab.OSC, Panel.tabOf("master.volume"))
    assertEquals(null, Panel.tabOf("no.such"))
  }
}
