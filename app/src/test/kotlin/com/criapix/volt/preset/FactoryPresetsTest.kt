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
    assertTrue("found ${files.size}", files.size >= 40)
    assertEquals("Init", PresetCodec.decode(files.first().readText()).name)
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
