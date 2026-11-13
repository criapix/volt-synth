package com.criapix.volt.ui

import com.criapix.volt.TestParams
import com.criapix.volt.midi.Synth
import com.criapix.volt.state.SynthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacrosTest {
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
  private val macros = Macros(state) { Algo(carriers = setOf(1, 3), modulators = emptyMap(), feedback = 0, feedbackFrom = 0) }

  private fun real(name: String): Float { val p = params[name]!!; return p.toReal(state.norm(p.id)) }
  private fun setReal(name: String, v: Float) { val p = params[name]!!; state.set(p.id, p.toNorm(v)) }
  private fun def(fm: Boolean, id: String) = MacroDefs.of(fm).first { it.id == id }

  @Test fun everyMacroNamesRealParameters() {
    for (fm in listOf(false, true)) for (d in MacroDefs.of(fm)) {
      d.keys.forEach { assertTrue("${d.id}: $it", params[it] != null) }
      d.switches.forEach { assertTrue("${d.id}: $it", params[it] != null) }
      d.like?.let { assertTrue("${d.id} like $it", params[it] != null && Feedback.of(it) != null) }
    }
    assertEquals(8, MacroDefs.analog.size)
    assertEquals(8, MacroDefs.fm.size)
  }

  @Test fun anAnalogMacroIsTheParameterItMoves() {
    val d = def(false, "brilho")
    macros.set(d, 0.25f)
    assertEquals(0.25f, state.norm(params["filter.cutoff"]!!.id), 1e-6f)
    assertEquals(0.25f, macros.position(d), 1e-6f)
    assertEquals(real("filter.cutoff"), real("filter.cutoff"), 0f)
  }

  @Test fun theSpaceMacroMovesBothMixesAndSwitchesTheEffectsOnAndOff() {
    val d = def(false, "espaco")
    macros.set(d, 0.6f)
    assertEquals(0.6f, real("fx.reverb.mix"), 1e-3f)
    assertEquals(0.6f * Macros.FOLLOW, real("fx.delay.mix"), 1e-3f)
    assertEquals(1f, real("fx.reverb.on"), 0f)
    assertEquals(1f, real("fx.delay.on"), 0f)
    assertEquals("60%", macros.text(d))
    macros.set(d, 0f)
    assertEquals(0f, real("fx.reverb.on"), 0f)
    assertEquals(0f, real("fx.delay.on"), 0f)
  }

  @Test fun anAbsoluteMacroShowsTheParametersOwnValue() {
    setReal("filter.cutoff", 1000f)
    assertEquals("1,0 kHz", macros.text(def(false, "brilho")))
  }

  @Test fun anFmMacroRestsInTheMiddleAndMovesModulatorLevelsFromWhereTheyAre() {
    val d = def(true, "brilho")
    assertEquals(0.5f, macros.position(d), 0f)
    assertEquals(listOf("fm.op2.level", "fm.op4.level", "fm.op5.level", "fm.op6.level"), macros.targets(d))  // 1 and 3 are carriers
    for (n in listOf(2, 4, 5, 6)) setReal("fm.op$n.level", 50f)
    macros.begin(d)
    macros.set(d, 0.75f)
    for (n in listOf(2, 4, 5, 6)) assertEquals(72.5f, real("fm.op$n.level"), 1f)
    assertEquals(99f, real("fm.op1.level"), 0f)  // a carrier is not touched
    macros.set(d, 0.25f)
    for (n in listOf(2, 4, 5, 6)) assertEquals(27.5f, real("fm.op$n.level"), 1f)
    assertEquals("-50%", macros.text(d))
  }

  @Test fun aRelativeMacroStaysWithinTheParametersRange() {
    val d = def(true, "brilho")
    for (n in listOf(2, 4, 5, 6)) setReal("fm.op$n.level", 90f)
    macros.begin(d)
    macros.set(d, 1f)
    for (n in listOf(2, 4, 5, 6)) assertEquals(99f, real("fm.op$n.level"), 0f)
  }

  @Test fun theFmAttackAndReleaseMacrosAreInverted() {
    val attack = def(true, "ataque")
    assertEquals(listOf("fm.op1.r1", "fm.op3.r1"), macros.targets(attack))
    setReal("fm.op1.r1", 60f); setReal("fm.op3.r1", 60f)
    macros.begin(attack)
    macros.set(attack, 0.75f)  // up = a longer attack = a lower rate
    assertTrue(real("fm.op1.r1") < 60f)
    val release = def(true, "cauda")
    assertEquals(listOf("fm.op1.r4", "fm.op3.r4"), macros.targets(release))
  }

  @Test fun resettingARelativeMacroPutsTheValuesBack() {
    val d = def(true, "brilho")
    for (n in listOf(2, 4, 5, 6)) setReal("fm.op$n.level", 40f)
    macros.begin(d)
    macros.set(d, 0.9f)
    macros.resetToDefault(d)
    for (n in listOf(2, 4, 5, 6)) assertEquals(40f, real("fm.op$n.level"), 0f)
    assertEquals(0.5f, macros.position(d), 0f)
  }
}
