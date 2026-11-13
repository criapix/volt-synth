package com.criapix.volt.preset

import com.criapix.volt.TestParams
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Dx7CodecTest {
  // Cartridge-backed tests are SKIPPED (Assume) when the Yamaha files are absent; see Dx7Fixtures.
  private fun rom(name: String) = Dx7Fixtures.rom(name)
  private val synth get() = Dx7Fixtures.synthBank()

  @Test fun readsKnownValuesFromRom1a() {
    val bank = Dx7Codec.readBank(rom("rom1a"), "ROM1A")
    assertEquals(32, bank.presets.size)
    assertTrue(bank.checksumOk)
    val brass = bank.presets[0]
    assertEquals("BRASS   1", brass.name)
    assertEquals("DX7 ROM1A", brass.category)
    assertEquals(1f, brass.params["engine.type"])
    assertEquals(22f, brass.params["fm.algorithm"])
    assertEquals(7f, brass.params["fm.feedback"])
    assertEquals(1f, brass.params["fm.oscsync"])
    assertEquals(37f, brass.params["fm.lfo.speed"])
    assertEquals(0f, brass.params["fm.transpose"])
    assertEquals(listOf(49f, 99f, 28f, 68f), (1..4).map { brass.params["fm.op6.r$it"] })
    assertEquals(listOf(98f, 98f, 91f, 0f), (1..4).map { brass.params["fm.op6.l$it"] })
    assertEquals(82f, brass.params["fm.op6.level"])
    assertEquals(98f, brass.params["fm.op1.level"])
    assertEquals(0f, brass.params["fm.op1.coarse"])
    assertEquals(7f, brass.params["fm.op1.detune"])
    assertEquals("E.PIANO 1", bank.presets[10].name)
    assertEquals(5f, bank.presets[10].params["fm.algorithm"])
  }

  @Test fun bankRoundTripIsByteIdenticalForTheCleanCartridgesAndStableForTheOthers() {
    // rom3a, rom3b and rom4a hold bits the DX7 ignores and a few out-of-range values; masking and
    // clamping changes those bytes (plan 6, findings). The other five must come back identical.
    val dirty = setOf("rom3a.syx", "rom3b.syx", "rom4a.syx")
    val files = Dx7Fixtures.cartridges()
    for (f in files) {
      val bytes = f.readBytes()
      val bank = Dx7Codec.readBank(bytes, "x")
      val written = Dx7Codec.writeBank(bank.presets)
      if (f.name !in dirty) assertArrayEquals(f.name, bytes, written)
      // Reading what we wrote and writing it again is a fixed point, for every cartridge.
      val reread = Dx7Codec.readBank(written, "x")
      assertArrayEquals(f.name, written, Dx7Codec.writeBank(reread.presets))
      assertEquals(f.name, bank.presets.map { it.params }, reread.presets.map { it.params })
    }
  }

  @Test fun allCartridgesReadWithEveryValueInsideItsParameterRange() {
    for (f in Dx7Fixtures.cartridges()) {
      val bank = Dx7Codec.readBank(f.readBytes(), "x")
      assertEquals(f.name, 32, bank.presets.size)
      assertTrue(f.name, bank.checksumOk)
      for (p in bank.presets) {
        assertTrue(f.name, p.name.isNotBlank() && p.name == p.name.trimEnd())
        for ((k, v) in p.params) {
          val prm = TestParams.params[k]!!
          assertTrue("${f.name} ${p.name} $k=$v", v >= prm.min && v <= prm.max && v == Math.rint(v.toDouble()).toFloat())
        }
      }
    }
  }

  @Test fun everyFmParameterExistsInTheEngineTable() {
    val p = Dx7Codec.readBank(synth, "SYNTH").presets[3]
    for (name in p.params.keys) assertTrue(name, TestParams.params[name] != null)
    // engine.type + 6 operators x 21 fields + 19 globals (8 pitch EG, algorithm, feedback, osc sync, 6 LFO, transpose).
    assertEquals(1 + 6 * 21 + 19, p.params.size)
    assertEquals(146, p.params.size)
    // A .syx cannot carry the operator switches or the controller routing.
    for (name in p.params.keys) {
      assertFalse(name, name.endsWith(".on"))
      assertFalse(name, name.startsWith("fm.wheel.") || name.startsWith("fm.at.") || name.startsWith("fm.breath."))
    }
    // Every other fm.* parameter in the engine table is either here or one of those two groups.
    val missing = TestParams.params.all.map { it.name }.filter {
      it.startsWith("fm.") && it !in p.params && !it.endsWith(".on") &&
        !(it.startsWith("fm.wheel.") || it.startsWith("fm.at.") || it.startsWith("fm.breath."))
    }
    assertEquals(emptyList<String>(), missing)
  }

  @Test fun singleVoiceRoundTrip() {
    val v = Dx7Codec.readBank(rom("rom1a"), "ROM1A").presets[10]
    val sx = Dx7Codec.writeVoice(v)
    assertEquals(163, sx.size)
    val read = Dx7Codec.readAny(sx, "x")
    assertTrue(read.checksumOk)
    val back = read.presets.single()
    assertEquals(v.params, back.params)
    assertEquals("E.PIANO 1", back.name)
  }

  @Test fun badChecksumReadsWithAWarning() {
    val bytes = rom("rom1a").also { it[4102] = (it[4102].toInt() xor 0x11).toByte() }
    val bank = Dx7Codec.readBank(bytes, "x")
    assertEquals(false, bank.checksumOk)
    assertEquals("BRASS   1", bank.presets[0].name)
  }

  @Test fun rejectsFilesThatAreNotDx7() {
    val e1 = assertThrows(PresetError::class.java) { Dx7Codec.readAny(ByteArray(100), "x") }
    assertEquals("Não é um arquivo do DX7", e1.message)
    val roland = rom("rom1a").also { it[1] = 0x41 }
    val e2 = assertThrows(PresetError::class.java) { Dx7Codec.readAny(roland, "x") }
    assertEquals("Não é um arquivo do DX7", e2.message)
    val badVoice = Dx7Codec.writeVoice(PresetFile(name = "x", params = mapOf("engine.type" to 1f))).also { it[1] = 0x41 }
    val e3 = assertThrows(PresetError::class.java) { Dx7Codec.readAny(badVoice, "x") }
    assertEquals("Não é um arquivo do DX7", e3.message)
  }

  @Test fun masksAndClampsGarbage() {
    val bytes = rom("rom1a")
    bytes[6 + 0] = 0xFF.toByte()    // OP6 R1: 127 after masking -> clamped to 99
    bytes[6 + 110] = 0x7F           // algorithm byte with junk in bits 5..6 -> 31 -> fm.algorithm 32
    bytes[6 + 116] = 0x7F           // LPMS 7, LFW 7 (clamped to 5), LKS 1
    val p = Dx7Codec.readBank(bytes, "x").presets[0]
    assertEquals(99f, p.params["fm.op6.r1"])
    assertEquals(32f, p.params["fm.algorithm"])
    assertEquals(7f, p.params["fm.lfo.pms"])
    assertEquals(5f, p.params["fm.lfo.wave"])
    assertEquals(1f, p.params["fm.lfo.sync"])
  }

  @Test fun namesAreAsciiAndTenCharacters() {
    val p = PresetFile(name = "Órgão de igreja longo", params = mapOf("engine.type" to 1f))
    val sx = Dx7Codec.writeVoice(p)
    assertEquals("?rg?o de i", String(sx, 6 + 145, 10, Charsets.US_ASCII))
    val short = Dx7Codec.writeVoice(PresetFile(name = "Ab", params = mapOf("engine.type" to 1f)))
    assertEquals("Ab        ", String(short, 6 + 145, 10, Charsets.US_ASCII))
    assertEquals("Ab", Dx7Codec.readAny(short, "x").presets.single().name)
  }

  @Test fun blankNameFallsBackToSemNome() {
    val sx = Dx7Codec.writeVoice(PresetFile(name = "   ", params = mapOf("engine.type" to 1f)))
    assertEquals("Sem nome", Dx7Codec.readAny(sx, "x").presets.single().name)
  }

  @Test fun missingParametersUseTheInitVoice() {
    val sx = Dx7Codec.writeVoice(PresetFile(name = "Vazio", params = mapOf("engine.type" to 1f)))
    val back = Dx7Codec.readAny(sx, "x").presets.single()
    assertEquals(99f, back.params["fm.op1.level"])
    assertEquals(0f, back.params["fm.op2.level"])
    assertEquals(1f, back.params["fm.algorithm"])
  }

  // ---- Synthetic data: these run without any cartridge on disk ----

  @Test fun syntheticBankReadsBackWithItsValues() {
    val bank = Dx7Codec.readBank(synth, "SYNTH")
    assertEquals(32, bank.presets.size)
    assertTrue(bank.checksumOk)
    assertEquals("SYNTH 01", bank.presets[0].name)
    assertEquals("SYNTH 32", bank.presets[31].name)
    assertEquals("DX7 SYNTH", bank.presets[0].category)
    assertEquals(1f, bank.presets[0].params["engine.type"])
    assertEquals(5f, bank.presets[4].params["fm.algorithm"])
    assertEquals(3f, bank.presets[3].params["fm.feedback"])
    assertEquals(50f, bank.presets[10].params["fm.op1.level"])
    assertEquals(11f, bank.presets[1].params["fm.op6.r1"])
  }

  @Test fun syntheticBankRoundTripIsByteIdentical() {
    val bytes = synth
    assertEquals(4104, bytes.size)
    val read = Dx7Codec.readBank(bytes, "x")
    assertArrayEquals(bytes, Dx7Codec.writeBank(read.presets))
    val reread = Dx7Codec.readBank(Dx7Codec.writeBank(read.presets), "x")
    assertEquals(read.presets.map { it.params }, reread.presets.map { it.params })
    assertEquals(read.presets.map { it.name }, reread.presets.map { it.name })
  }

  @Test fun syntheticParametersStayInsideTheirRange() {
    for (p in Dx7Codec.readBank(synth, "x").presets) {
      assertTrue(p.name.isNotBlank() && p.name == p.name.trimEnd())
      for ((k, v) in p.params) {
        val prm = TestParams.params[k]!!
        assertTrue("${p.name} $k=$v", v >= prm.min && v <= prm.max && v == Math.rint(v.toDouble()).toFloat())
      }
    }
  }

  @Test fun syntheticSingleVoiceRoundTrip() {
    val v = Dx7Codec.readBank(synth, "x").presets[10]
    val sx = Dx7Codec.writeVoice(v)
    assertEquals(163, sx.size)
    val read = Dx7Codec.readAny(sx, "x")
    assertTrue(read.checksumOk)
    val back = read.presets.single()
    assertEquals(v.params, back.params)
    assertEquals("SYNTH 11", back.name)
  }

  @Test fun syntheticBadChecksumReadsWithAWarning() {
    val bytes = synth.also { it[4102] = (it[4102].toInt() xor 0x11).toByte() }
    val bank = Dx7Codec.readBank(bytes, "x")
    assertEquals(false, bank.checksumOk)
    assertEquals("SYNTH 01", bank.presets[0].name)
  }

  @Test fun syntheticRejectsFilesThatAreNotDx7() {
    val e1 = assertThrows(PresetError::class.java) { Dx7Codec.readAny(ByteArray(100), "x") }
    assertEquals("Não é um arquivo do DX7", e1.message)
    val roland = synth.also { it[1] = 0x41 }
    val e2 = assertThrows(PresetError::class.java) { Dx7Codec.readAny(roland, "x") }
    assertEquals("Não é um arquivo do DX7", e2.message)
  }

  @Test fun syntheticMasksAndClampsGarbage() {
    val bytes = synth
    bytes[6 + 0] = 0xFF.toByte()    // OP6 R1: 127 after masking -> clamped to 99
    bytes[6 + 110] = 0x7F           // algorithm byte with junk in bits 5..6 -> 31 -> fm.algorithm 32
    bytes[6 + 116] = 0x7F           // LPMS 7, LFW 7 (clamped to 5), LKS 1
    val p = Dx7Codec.readBank(bytes, "x").presets[0]
    assertEquals(99f, p.params["fm.op6.r1"])
    assertEquals(32f, p.params["fm.algorithm"])
    assertEquals(7f, p.params["fm.lfo.pms"])
    assertEquals(5f, p.params["fm.lfo.wave"])
    assertEquals(1f, p.params["fm.lfo.sync"])
  }
}
