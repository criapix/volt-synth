package com.criapix.volt.ui

import com.criapix.volt.TestParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelTest {
  @Test fun everyParameterIsOnExactlyOneTab() {
    val placed = Panel.header + Panel.groups.values.flatten().flatMap { it.params }
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

  @Test fun tabsFollowTheVoiceType() {
    val analog = Panel.tabsFor(false)
    val fm = Panel.tabsFor(true)
    assertTrue(analog.none { it in listOf(Tab.ALGO, Tab.OP, Tab.PITCH) })
    assertTrue(fm.none { it in listOf(Tab.OSC, Tab.FILTER, Tab.ENV, Tab.MOD) })
    assertEquals(listOf(Tab.ALGO, Tab.OP, Tab.PITCH, Tab.ARP, Tab.FX), fm)
    assertTrue((analog + fm).all { it in Panel.groups })
  }

  @Test fun fmParametersFindTheirTab() {
    assertEquals(Tab.OP, Panel.tabOf("fm.op3.r2"))
    assertEquals(Tab.PITCH, Panel.tabOf("fm.lfo.speed"))
    assertEquals(Tab.ALGO, Panel.tabOf("fm.op5.on"))
    assertEquals(null, Panel.tabOf("engine.type"))  // header, not in a tab
  }
}
