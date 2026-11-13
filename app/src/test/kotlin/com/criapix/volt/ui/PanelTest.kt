package com.criapix.volt.ui

import com.criapix.volt.TestParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelTest {
  private val allNames = TestParams.params.all.map { it.name }

  private fun placed(): List<String> =
    Panel.header + Panel.families.flatMap { f -> f.cards.flatMap { c -> c.params + listOfNotNull(c.on) } }

  @Test fun everyParameterIsOnExactlyOneCard() {
    assertEquals(allNames.sorted(), placed().sorted())
  }

  @Test fun theFamiliesFollowTheVoiceType() {
    val analog = Panel.familiesFor(false).map { it.id }
    val fm = Panel.familiesFor(true).map { it.id }
    assertEquals(listOf("fonte", "filtro", "amp", "mov", "fx", "arp"), analog)
    assertEquals(listOf("algo", "ops", "pitch", "fx", "arp"), fm)
    assertTrue((analog + fm).all { id -> Panel.families.any { it.id == id } })
  }

  @Test fun theEffectsAndArpAreSharedByBothVoiceTypes() {
    assertTrue(Panel.familiesFor(false).containsAll(listOf(Panel.fxFamily, Panel.arpFamily)))
    assertTrue(Panel.familiesFor(true).containsAll(listOf(Panel.fxFamily, Panel.arpFamily)))
    assertNull(Panel.fxFamily.fm)
  }

  @Test fun cardOfAndFamilyOfFindWhereAParameterLives() {
    assertEquals("FILTRO", Panel.cardOf("filter.cutoff")?.title)
    assertEquals("ENV DE AMPLITUDE", Panel.cardOf("aenv.attack")?.title)
    assertEquals("filtro", Panel.familyOf("filter.cutoff")?.id)
    assertEquals("mov", Panel.familyOf("pmod.osc2.pw")?.id)
    assertEquals("amp", Panel.familyOf("voice.mode")?.id)
    assertEquals("fonte", Panel.familyOf("master.volume")?.id)
    assertNull(Panel.cardOf("no.such"))
    assertNull(Panel.cardOf("engine.type"))  // the header, not a card
  }

  @Test fun anEffectsSwitchLivesInItsCardHeader() {
    val card = Panel.cardOf("fx.delay.on")
    assertEquals("DELAY", card?.title)
    assertEquals("fx.delay.on", card?.on)
    assertTrue("fx.delay.on" !in card!!.params)
    assertEquals("fx", Panel.familyOf("fx.reverb.on")?.id)
  }

  @Test fun eachOperatorsCardsPlaceAllItsParametersOnce() {
    for (n in 1..6) {
      val cards = Panel.operatorCardsOf(n)
      assertEquals(3, cards.size)
      val placedHere = cards.flatMap { it.params }
      val expected = allNames.filter { it.startsWith("fm.op$n.") && !it.endsWith(".on") }
      assertEquals(expected.sorted(), placedHere.sorted())
      assertEquals(21, placedHere.size)
      assertTrue(cards.all { it.n == n })
      assertTrue(placedHere.all { Panel.familyOf(it)?.id == "ops" })
    }
  }

  @Test fun theOperatorSwitchesAreOnTheGlobalCard() {
    for (n in 1..6) assertEquals("GLOBAL", Panel.cardOf("fm.op$n.on")?.title)
  }

  @Test fun operatorCardsHaveTheWidgetsTheScreenExpects() {
    val (freq, env, scale) = Panel.operatorCardsOf(1)
    assertEquals(listOf("fm.op1.mode"), freq.params.filter { ParamUi.of(it) is ChoiceUi })
    assertEquals(3, freq.params.count { ParamUi.of(it) is KnobUi })
    assertEquals(11, env.params.size)  // R1-R4, L1-L4, level, velocity, rate scaling
    assertTrue(env.params.all { ParamUi.of(it) is KnobUi })
    assertEquals(6, env.columns)
    assertEquals(4, scale.params.count { ParamUi.of(it) is KnobUi })
    assertEquals(listOf("Curva E", "Curva D"), scale.params.filter { ParamUi.of(it) is ChoiceUi }.map { ParamUi.of(it).label })
  }

  @Test fun theEnvelopeCardsKeepRatesAndLevelsInRowsOfTheirOwn() {
    val env = Panel.operatorCardsOf(1)[1].params
    assertEquals(listOf("r1", "r2", "r3", "r4").map { "fm.op1.$it" }, env.take(4))
    assertEquals(listOf("l1", "l2", "l3", "l4").map { "fm.op1.$it" }, env.drop(6).take(4))
  }

  @Test fun theControllersCardHasARangeAndThreeSwitchesPerController() {
    val card = Panel.cardOf("fm.at.range")!!
    assertEquals("CONTROLADORES", card.title)
    for (c in listOf("wheel", "at", "breath")) {
      assertEquals(1, card.params.count { it.startsWith("fm.$c.") && ParamUi.of(it) is KnobUi })
      assertEquals(3, card.params.count { it.startsWith("fm.$c.") && ParamUi.of(it) is ToggleUi })
    }
    assertEquals("pitch", Panel.familyOf("fm.breath.eg")?.id)
  }

  @Test fun everyCardHasItsDrawingAndAHue() {
    for (c in Panel.families.flatMap { it.cards }) {
      assertNotNull(c.title, c.hue)
      assertTrue("${c.title} has no drawing", c.drawing != Drawing.NONE)
    }
  }

  @Test fun aParametersHueIsItsCardsHue() {
    for (card in Panel.families.flatMap { it.cards }) {
      for (name in card.params.filter { ParamUi.of(it) is KnobUi }) {
        if (card.title == "GLOBAL" || card.title == "ALGORITMO") continue
        assertEquals("$name on ${card.title}", card.hue, Hues.of(name))
      }
    }
  }
}
