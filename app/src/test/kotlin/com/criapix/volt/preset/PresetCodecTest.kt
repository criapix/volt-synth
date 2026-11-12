package com.criapix.volt.preset

import com.criapix.volt.TestParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetCodecTest {
  private val p = TestParams.params

  @Test fun missingParametersTakeTheirDefaults() {
    val v = PresetCodec.toValues(PresetCodec.decode("""{"format":1,"name":"X","params":{}}"""), p)
    for (prm in p.all) assertEquals(prm.name, prm.toNorm(prm.def), v[prm.id], 1e-6f)
  }

  @Test fun realValuesAreReadByNameAndClamped() {
    val text = """{"format":1,"name":"X","category":"Bass","params":{"filter.cutoff":500,"filter.reso":7,"osc1.semi":-40}}"""
    val v = PresetCodec.toValues(PresetCodec.decode(text), p)
    assertEquals(500f, p["filter.cutoff"]!!.toReal(v[p["filter.cutoff"]!!.id]), 0.5f)
    assertEquals(1f, v[p["filter.reso"]!!.id], 0f)
    assertEquals(0f, v[p["osc1.semi"]!!.id], 0f)
  }

  @Test fun unknownParametersAndFieldsAreIgnored() {
    val text = """{"format":7,"name":"Futuro","params":{"fx.reverb.size":0.5},"author":"alguém"}"""
    val preset = PresetCodec.decode(text)
    assertEquals("Futuro", preset.name)
    PresetCodec.toValues(preset, p)  // must not throw
  }

  @Test fun brokenFilesAreRefusedWithAMessage() {
    val e = assertThrows(PresetError::class.java) { PresetCodec.decode("{ isto não é json") }
    assertTrue(e.message!!.isNotBlank())
    assertThrows(PresetError::class.java) { PresetCodec.decode("""{"format":1,"name":"  "}""") }
    assertThrows(PresetError::class.java) { PresetCodec.decode("""[1,2,3]""") }
  }

  @Test fun valuesRoundTripThroughAFile() {
    val values = FloatArray(p.all.size) { i -> p.all[i].toNorm(p.all[i].toReal((i * 37 % 100) / 100f)) }
    val file = PresetCodec.fromValues("Meu som", "Pad", values, p)
    val back = PresetCodec.toValues(PresetCodec.decode(PresetCodec.encode(file)), p)
    for (i in values.indices) assertEquals(p.all[i].name, values[i], back[i], 1e-3f)
    assertEquals("Pad", PresetCodec.decode(PresetCodec.encode(file)).category)
  }

  @Test fun aSavedFileListsEveryParameterInTableOrder() {
    val file = PresetCodec.fromValues("A", "", FloatArray(p.all.size), p)
    assertEquals(p.all.map { it.name }, file.params.keys.toList())
  }

  @Test fun decodeAnyReadsASingleFileOrABank() {
    val one = PresetCodec.encode(PresetFile(name = "Um"))
    val bank = PresetCodec.encodeBank(listOf(PresetFile(name = "A"), PresetFile(name = "B")))
    assertEquals(listOf("Um"), PresetCodec.decodeAny(one).map { it.name })
    assertEquals(listOf("A", "B"), PresetCodec.decodeAny(bank).map { it.name })
  }
}
