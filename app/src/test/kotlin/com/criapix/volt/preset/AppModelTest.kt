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
}
