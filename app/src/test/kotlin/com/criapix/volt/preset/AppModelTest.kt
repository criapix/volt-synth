package com.criapix.volt.preset

import com.criapix.volt.TestParams
import com.criapix.volt.midi.Synth
import com.criapix.volt.state.SynthState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppModelTest {
  @get:Rule val tmp = TemporaryFolder()

  private object Silent : Synth {
    override fun noteOn(note: Int, velocity: Float) {}
    override fun noteOff(note: Int) {}
    override fun setParam(id: Int, normalized: Float) {}
    override fun pitchBend(value: Float) {}
    override fun modWheel(value: Float) {}
    override fun sustain(on: Boolean) {}
    override fun allNotesOff() {}
  }

  private object NoFavorites : FavoriteStore {
    override fun load() = emptySet<String>()
    override fun save(ids: Set<String>) {}
  }

  private val p = TestParams.params
  private val cutoff = p["filter.cutoff"]!!

  private fun factory(): File = File(tmp.root, "factory").apply {
    if (!exists()) {
      mkdirs()
      File(this, "001-init.json").writeText(PresetCodec.encode(PresetFile(name = "Init")))
      File(this, "010-dark.json").writeText(PresetCodec.encode(PresetFile(name = "Escuro", category = "Pad", params = mapOf("filter.cutoff" to 300f))))
      File(this, "020-bright.json").writeText(PresetCodec.encode(PresetFile(name = "Claro", category = "Lead", params = mapOf("filter.cutoff" to 9000f))))
    }
  }

  private fun model(): AppModel {
    val state = SynthState(p, Silent)
    val lib = PresetLibrary(DirFactory(factory()), File(tmp.root, "user"), NoFavorites)
    return AppModel(state, lib, File(tmp.root, "session.json"))
  }

  @Test fun firstStartLoadsTheFirstFactoryPreset() {
    val m = model().apply { restore() }
    assertEquals("factory:001-init", m.current.value.id)
    assertEquals("Init", m.current.value.name)
    assertFalse(m.state.dirty.value)
  }

  @Test fun nextAndPreviousFollowTheLibraryOrderAndWrap() {
    val m = model().apply { restore() }
    m.step(1)
    assertEquals("Escuro", m.current.value.name)
    assertEquals(300f, m.state.real(cutoff.id), 1f)
    m.step(1); m.step(1)
    assertEquals("Init", m.current.value.name)
    m.step(-1)
    assertEquals("Claro", m.current.value.name)
    assertEquals(3, m.number(m.current.value.id))
  }

  @Test fun programChangeLoadsThePresetAtThatPosition() {
    val m = model().apply { restore() }
    m.programChange(2)                       // third in library order
    assertEquals("Claro", m.current.value.name)
    m.programChange(99)
    assertEquals("Claro", m.current.value.name)
    assertNotNull(m.message.value)
  }

  @Test fun editingMarksDirtyAndSelectingClears() {
    val m = model().apply { restore() }
    m.state.set(cutoff.id, 0.2f)
    assertTrue(m.state.dirty.value)
    m.select("factory:010-dark")
    assertFalse(m.state.dirty.value)
  }

  @Test fun saveAsCreatesAUserPresetThatSaveCanOverwrite() {
    val m = model().apply { restore() }
    assertFalse(m.canOverwrite())
    m.state.set(cutoff.id, cutoff.toNorm(700f))
    val ref = m.saveAs("Meu", "Bass")
    assertEquals(ref.id, m.current.value.id)
    assertTrue(m.canOverwrite())
    assertFalse(m.state.dirty.value)
    m.state.set(cutoff.id, cutoff.toNorm(1500f))
    assertTrue(m.save("Meu 2", "Lead"))
    assertEquals(1500f, m.library.read(ref.id).params["filter.cutoff"]!!, 1f)
    assertEquals("Meu 2", m.current.value.name)
    assertTrue(m.presets.value.any { it.name == "Meu 2" })
  }

  @Test fun deletingTheCurrentPresetKeepsTheSoundAsUnsaved() {
    val m = model().apply { restore() }
    val ref = m.saveAs("Temp", "")
    m.delete(ref.id)
    assertNull(m.current.value.id)
    assertEquals("Temp", m.current.value.name)
    assertTrue(m.state.dirty.value)
  }

  @Test fun theSessionRestoresPresetAndUnsavedEdits() {
    val a = model().apply { restore() }
    a.select("factory:020-bright")
    a.state.set(cutoff.id, cutoff.toNorm(2500f))
    a.persist()
    val b = model().apply { restore() }
    assertEquals("factory:020-bright", b.current.value.id)
    assertEquals(2500f, b.state.real(cutoff.id), 2f)
    assertTrue(b.state.dirty.value)
  }

  @Test fun aSessionPointingAtADeletedPresetStillRestoresTheSound() {
    val a = model().apply { restore() }
    val ref = a.saveAs("Some", "")
    a.persist()
    a.library.delete(ref.id)
    val b = model().apply { restore() }
    assertNull(b.current.value.id)
    assertEquals("Some", b.current.value.name)
  }

  @Test fun aBrokenImportReportsAMessage() {
    val m = model().apply { restore() }
    assertEquals(0, m.importText("nada"))
    assertNotNull(m.message.value)
  }

  private fun rom(name: String) = File("src/main/assets/dx7/$name.syx").readBytes()

  @Test fun importingASyxBankReportsTheCount() {
    val m = model().apply { restore() }
    assertEquals(32, m.importBytes(rom("rom1a"), "rom1a"))
    assertEquals("32 presets importados", m.message.value)
  }

  @Test fun aBadChecksumIsImportedWithAWarning() {
    val m = model().apply { restore() }
    val b = rom("rom1a").also { it[4102] = ((it[4102] + 1) and 0x7F).toByte() }
    assertEquals(32, m.importBytes(b, "rom1a"))
    assertEquals("Importado com checksum inválido: 32 presets", m.message.value)
  }

  @Test fun aSyxThatIsNotDx7DataIsRejected() {
    val m = model().apply { restore() }
    val b = ByteArray(100).also { it[0] = 0xF0.toByte() }
    assertEquals(0, m.importBytes(b, "x"))
    assertEquals("Não é um arquivo do DX7", m.message.value)
    assertEquals(3, m.presets.value.size)
  }

  @Test fun jsonStillImportsThroughImportBytes() {
    val m = model().apply { restore() }
    assertEquals(1, m.importBytes(PresetCodec.encode(PresetFile(name = "Json")).toByteArray(), "x"))
    assertEquals("1 preset importado", m.message.value)
  }

  @Test fun exportSyxIsOneVoiceForFmAndNullForAnalog() {
    val m = model().apply { restore() }
    assertNull(m.exportSyx("factory:001-init"))
    val ref = m.library.importBytes(rom("rom1a"), "rom1a").refs.first()
    val b = m.exportSyx(ref.id)!!
    assertEquals(163, b.size)
    assertEquals("BRASS   1", Dx7Codec.readAny(b, "x").presets.single().name)
    assertTrue(m.isFm(ref.id))
    assertFalse(m.isFm("factory:001-init"))
  }

  @Test fun factoryCartridgeVoicesExportAsSingleVoices() {
    val state = SynthState(p, Silent)
    val lib = PresetLibrary(DirFactory(factory()), File(tmp.root, "user"), NoFavorites, CartridgeSource { listOf("rom1a" to rom("rom1a")) })
    val m = AppModel(state, lib, File(tmp.root, "session.json"))
    val b = m.exportSyx("factory:dx7-rom1a-01")!!
    assertEquals(163, b.size)
    assertTrue(m.isFm("factory:dx7-rom1a-32"))
  }

  @Test fun exportSyxBankDropsBeyond32AndPadsFewer() {
    val m = model().apply { restore() }
    val voice = Dx7Codec.readBank(rom("rom1a"), "rom1a").presets[0]
    assertNull(m.exportSyxBank("DX7 mix"))
    repeat(3) { i -> m.library.create(voice.copy(name = "V$i", category = "DX7 mix")) }
    val (few, droppedFew) = m.exportSyxBank("DX7 mix")!!
    assertEquals(0, droppedFew)
    val back = Dx7Codec.readBank(few, "mix")
    assertTrue(back.checksumOk)
    assertEquals(listOf("V0", "V1", "V2", "INIT VOICE"), back.presets.take(4).map { it.name })
    assertEquals("INIT VOICE", back.presets[31].name)
    repeat(32) { i -> m.library.create(voice.copy(name = "W$i", category = "DX7 mix")) }  // 35 in total
    val (full, dropped) = m.exportSyxBank("DX7 mix")!!
    assertEquals(3, dropped)
    assertEquals(32, Dx7Codec.readBank(full, "mix").presets.size)
  }
}
