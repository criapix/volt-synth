package com.criapix.volt.preset

import com.criapix.volt.TestParams
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FactoryPresetsTest {
  private val dir = File("src/main/assets/presets/factory")
  private val files = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
  private val p = TestParams.params

  @Test fun thereIsAStarterSetAndInitComesFirst() {
    assertTrue("found ${files.size}", files.size >= 64)
    assertEquals("Init", PresetCodec.decode(files.first().readText()).name)
  }

  @Test fun theFirst43KeepTheirPositionsForProgramChange() {
    // Program Change n loads position n+1 (plan 3). These 43 shipped before 1.0 and must never move.
    val pinned = listOf(
      "001-init", "010-baixo-gordo", "011-baixo-redondo", "012-baixo-acido", "013-baixo-sub", "014-baixo-funk", "015-baixo-pulso",
      "020-lead-sync", "021-lead-classico", "022-lead-quadrado", "023-lead-assobio", "024-lead-trance",
      "030-pad-analogico", "031-pad-pwm", "032-pad-escuro", "033-pad-vidro", "034-pad-evolutivo",
      "040-metais-poly", "041-metais-salto", "042-metais-lentos", "043-stab-de-metais",
      "050-cordas-suaves", "051-cordas-ensemble", "052-violoncelo",
      "060-pluck-vidro", "061-marimba", "062-harpa", "063-pizzicato",
      "070-sino-metalico", "071-piano-eletrico", "072-clavinete", "073-orgao", "074-cravo", "075-vibrafone",
      "081-vento", "082-laser", "083-bumbo", "084-caixa", "085-scifi-aleatorio", "086-sirene",
      "090-arpejo-classico", "091-cordas-phaser", "092-baixo-distorcido",
    )
    assertEquals(pinned, files.take(43).map { it.name.removeSuffix(".json") })
  }

  @Test fun fileNamesStartWithAUniqueNumberAndFollowTheirCategoryRange() {
    // The library order is the file-name order, so the numbers group presets by category.
    val ranges = mapOf("Bass" to 10..19, "Lead" to 20..29, "Pad" to 30..39, "Brass" to 40..49,
      "Strings" to 50..59, "Pluck" to 60..69, "Keys" to 70..79, "FX" to 80..89)
    val numbers = HashSet<Int>()
    for (f in files) {
      val n = f.name.substringBefore('-').toInt()
      assertTrue("${f.name}: duplicate number", numbers.add(n))
      val preset = PresetCodec.decode(f.readText())
      // 090 and up: presets added later go at the end of the list so Program Change numbers stay stable (plan 4, decision 7).
      if (n < 90 && preset.category.isNotEmpty()) assertTrue("${f.name}: $n is outside the ${preset.category} range", n in ranges.getValue(preset.category))
    }
  }

  @Test fun stepParametersHoldWholeValues() {
    for (f in files) for ((name, value) in PresetCodec.decode(f.readText()).params) {
      val prm = p[name]!!
      if (prm.curve == com.criapix.volt.Curve.STEP) assertEquals("${f.name}: $name = $value", Math.round(value).toFloat(), value, 0f)
    }
  }

  @Test fun everyPresetHasSomethingThatMakesSound() {
    // A preset that silences every source (oscillator levels, sub and noise) would be mute.
    fun v(preset: PresetFile, name: String) = preset.params[name] ?: p[name]!!.def
    for (f in files) {
      val preset = PresetCodec.decode(f.readText())
      val sources = listOf("osc1.level", "osc2.level", "osc3.level", "mix.sub", "mix.noise").sumOf { v(preset, it).toDouble() }
      assertTrue("${f.name}: no audible source", sources > 0.1)
      assertTrue("${f.name}: master volume is zero", v(preset, "master.volume") > 0.05f)
    }
  }

  @Test fun everyFileIsValidWithKnownNamesInRangeValuesAndAKnownCategory() {
    val names = HashSet<String>()
    for (f in files) {
      val preset = PresetCodec.decode(f.readText())
      assertTrue("${f.name}: duplicate name", names.add(preset.name))
      assertTrue("${f.name}: category ${preset.category}", preset.category == "" || preset.category in PresetCodec.CATEGORIES)
      for ((name, value) in preset.params) {
        val prm = p[name]
        assertTrue("${f.name}: unknown parameter $name", prm != null)
        assertTrue("${f.name}: $name = $value outside ${prm!!.min}..${prm.max}", value >= prm.min && value <= prm.max)
      }
    }
  }
}
