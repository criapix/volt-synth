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

  @Test fun eachOperatorsGroupsPlaceAllItsParametersOnce() {
    for (n in 1..6) {
      val placed = Panel.operatorEnvelope[n - 1].params + Panel.operatorFrequency[n - 1].params + Panel.operatorScaling[n - 1].params
      val expected = TestParams.params.all.map { it.name }.filter { it.startsWith("fm.op$n.") && !it.endsWith(".on") }
      assertEquals(expected.sorted(), placed.sorted())
      assertEquals(21, placed.size)
      assertTrue(placed.all { Panel.tabOf(it) == Tab.OP })
    }
  }

  @Test fun operatorPagesHaveTheWidgetsTheScreenExpects() {
    val env = Panel.operatorEnvelope[0].params.map { ParamUi.of(it) }
    assertTrue(env.all { it is KnobUi })
    assertEquals(11, env.size)  // R1-R4, L1-L4, level, velocity, rate scaling
    assertEquals(listOf("fm.op1.mode"), Panel.operatorFrequency[0].params.filter { ParamUi.of(it) is ChoiceUi })
    assertEquals(3, Panel.operatorFrequency[0].params.count { ParamUi.of(it) is KnobUi })
    assertEquals(4, Panel.operatorScaling[0].params.count { ParamUi.of(it) is KnobUi })
    assertEquals(
      listOf("Curva E", "Curva D"),
      Panel.operatorScaling[0].params.filter { ParamUi.of(it) is ChoiceUi }.map { ParamUi.of(it).label },
    )
  }

  @Test fun controllersAreOneGroupEachWithRangeAndThreeToggles() {
    assertEquals(listOf("RODA", "AFTERTOUCH", "BREATH"), Panel.controllers.map { it.title })
    for (g in Panel.controllers) {
      assertEquals(1, g.params.count { ParamUi.of(it) is KnobUi })
      assertEquals(3, g.params.count { ParamUi.of(it) is ToggleUi })
    }
    assertEquals("AFTERTOUCH", Panel.groupOf("fm.at.range")?.title)
    assertEquals(Tab.PITCH, Panel.tabOf("fm.breath.eg"))
  }
}
