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
    assertTrue("found ${files.size}", files.size >= 10)
    assertEquals("Init", PresetCodec.decode(files.first().readText()).name)
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
