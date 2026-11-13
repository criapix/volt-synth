package com.criapix.volt.preset

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PresetLibraryTest {
  @get:Rule val tmp = TemporaryFolder()

  private class MemFavorites : FavoriteStore {
    var ids = emptySet<String>()
    override fun load() = ids
    override fun save(ids: Set<String>) { this.ids = ids }
  }

  private fun factoryDir(): File = tmp.newFolder("factory").also { d ->
    File(d, "020-b.json").writeText(PresetCodec.encode(PresetFile(name = "Bravo", category = "Lead")))
    File(d, "010-a.json").writeText(PresetCodec.encode(PresetFile(name = "Alfa", category = "Bass")))
    File(d, "030-broken.json").writeText("{ quebrado")
  }

  private fun library(favs: FavoriteStore = MemFavorites()) =
    PresetLibrary(DirFactory(factoryDir()), File(tmp.root, "user"), favs)

  @Test fun factoryComesFirstByFileNameAndBrokenFilesAreSkipped() {
    val lib = library()
    assertEquals(listOf("factory:010-a", "factory:020-b"), lib.all().map { it.id })
    assertEquals("Alfa", lib.read("factory:010-a").name)
  }

  @Test fun userPresetsFollowSortedByName() {
    val lib = library()
    lib.create(PresetFile(name = "zebra"))
    lib.create(PresetFile(name = "Meio"))
    assertEquals(listOf("Alfa", "Bravo", "Meio", "zebra"), lib.all().map { it.name })
    assertTrue(lib.all().last().isUser)
  }

  @Test fun createReadOverwriteRenameDelete() {
    val lib = library()
    val ref = lib.create(PresetFile(name = "Som", params = mapOf("filter.cutoff" to 500f)))
    assertEquals(500f, lib.read(ref.id).params["filter.cutoff"])
    lib.overwrite(ref.id, PresetFile(name = "Som", params = mapOf("filter.cutoff" to 900f)))
    assertEquals(900f, lib.read(ref.id).params["filter.cutoff"])
    lib.rename(ref.id, "Novo nome")
    assertEquals("Novo nome", lib.all().first { it.id == ref.id }.name)
    lib.delete(ref.id)
    assertFalse(lib.all().any { it.id == ref.id })
    assertThrows(PresetError::class.java) { lib.read(ref.id) }
  }

  @Test fun factoryPresetsCannotBeChanged() {
    val lib = library()
    assertThrows(IllegalArgumentException::class.java) { lib.overwrite("factory:010-a", PresetFile(name = "X")) }
    assertThrows(IllegalArgumentException::class.java) { lib.delete("factory:010-a") }
  }

  @Test fun favoritesAreStoredAndDeletingForgetsThem() {
    val favs = MemFavorites()
    val lib = library(favs)
    val ref = lib.create(PresetFile(name = "Fav"))
    lib.setFavorite("factory:010-a", true)
    lib.setFavorite(ref.id, true)
    assertEquals(setOf("factory:010-a", ref.id), favs.ids)
    lib.delete(ref.id)
    assertEquals(setOf("factory:010-a"), favs.ids)
    assertTrue(PresetLibrary(DirFactory(File(tmp.root, "factory")), File(tmp.root, "user"), favs).isFavorite("factory:010-a"))
  }

  @Test fun importRenamesCollisionsAndExportRoundTrips() {
    val lib = library()
    lib.create(PresetFile(name = "Pad"))
    val bank = PresetCodec.encodeBank(listOf(PresetFile(name = "Pad"), PresetFile(name = "Pad"), PresetFile(name = "Baixo")))
    lib.import(bank)
    assertEquals(listOf("Baixo", "Pad", "Pad (2)", "Pad (3)"), lib.all().filter { it.isUser }.map { it.name })
    val other = PresetLibrary(DirFactory(File(tmp.root, "factory")), tmp.newFolder("user2"), MemFavorites())
    assertEquals(4, other.import(lib.exportUser()).size)
  }

  @Test fun aBrokenUserFileIsSkipped() {
    val lib = library()
    File(tmp.root, "user").apply { mkdirs() }.resolve("lixo.json").writeText("nada")
    assertEquals(2, lib.all().size)
  }

  // Hand-made cartridges: the Yamaha files are not distributed with the repository (see Dx7Fixtures).
  private fun cartridgeDir(): File = tmp.newFolder("dx7").also { d ->
    File(d, "rom1a.syx").writeBytes(Dx7Fixtures.synthBank("VOZ A"))
    File(d, "rom1b.syx").writeBytes(Dx7Fixtures.synthBank("VOZ B"))
  }

  private fun libraryWithDx7() =
    PresetLibrary(DirFactory(factoryDir()), File(tmp.root, "user"), MemFavorites(), DirCartridges(cartridgeDir()))

  @Test fun cartridgesFollowTheJsonFactoryPresets() {
    val lib = libraryWithDx7()
    val ids = lib.all().map { it.id }
    assertEquals(listOf("factory:010-a", "factory:020-b", "factory:dx7-rom1a-01"), ids.take(3))
    assertEquals("factory:dx7-rom1b-32", ids[2 + 63])
    assertEquals("VOZ A 01", lib.read("factory:dx7-rom1a-01").name)
    assertEquals("DX7 ROM1A", lib.all()[2].category)
    assertThrows(IllegalArgumentException::class.java) { lib.delete("factory:dx7-rom1a-01") }
    assertThrows(IllegalArgumentException::class.java) { lib.overwrite("factory:dx7-rom1a-01", PresetFile(name = "X")) }
  }

  @Test fun userPresetsComeAfterTheCartridges() {
    val lib = libraryWithDx7()
    lib.create(PresetFile(name = "Meu"))
    assertEquals(2 + 64 + 1, lib.all().size)
    assertTrue(lib.all().last().isUser)
  }

  @Test fun aBrokenCartridgeIsSkippedAndLogged() {
    val dir = cartridgeDir()
    File(dir, "aaa.syx").writeBytes(ByteArray(100) { 0x11 })
    File(dir, "bbb.syx").writeBytes(Dx7Fixtures.synthBank().also { it[1] = 0x41 })
    val logs = mutableListOf<String>()
    val lib = PresetLibrary(DirFactory(factoryDir()), File(tmp.root, "user"), MemFavorites(), DirCartridges(dir)) { logs += it }
    assertEquals(2 + 64, lib.all().size)
    assertEquals("factory:dx7-rom1a-01", lib.all()[2].id)
    assertEquals(2, logs.size)
    assertTrue(logs[0].contains("aaa") && logs[1].contains("bbb"))
  }

  @Test fun importingASyxBankAddsThirtyTwoUserPresets() {
    val lib = libraryWithDx7()
    val r = lib.importBytes(Dx7Fixtures.synthBank(), "meu-cartucho")
    assertEquals(32, r.refs.size)
    assertTrue(r.checksumOk)
    assertTrue(r.refs.all { it.isUser && it.category == "DX7 meu-cartucho" })
    assertEquals(1f, lib.read(r.refs[0].id).params["engine.type"])
  }

  @Test fun importBytesStillTakesJson() {
    val lib = library()
    val r = lib.importBytes(PresetCodec.encode(PresetFile(name = "Json")).toByteArray(), "x")
    assertEquals(listOf("Json"), r.refs.map { it.name })
  }

  @Test fun aLeadingBomIsIgnoredInJson() {
    val lib = library()
    val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + PresetCodec.encode(PresetFile(name = "Bom")).toByteArray()
    assertEquals(listOf("Bom"), lib.importBytes(bytes, "x").refs.map { it.name })
    assertEquals(listOf("Bom 2"), lib.import("﻿" + PresetCodec.encode(PresetFile(name = "Bom"))).map { it.name }.map { it.replace("(", "").replace(")", "") })
  }

  @Test fun duplicateNamesInsideABankGetSuffixesEvenInABigLibrary() {
    val lib = library()
    repeat(150) { lib.create(PresetFile(name = "Existente $it")) }
    val bank = PresetCodec.encodeBank(List(3) { PresetFile(name = "INIT VOICE") } + PresetFile(name = "Existente 7"))
    val names = lib.import(bank).map { it.name }
    assertEquals(listOf("INIT VOICE", "INIT VOICE (2)", "INIT VOICE (3)", "Existente 7 (2)"), names)
    val r = lib.importBytes(Dx7Fixtures.synthBank(), "a")
    assertEquals(32, r.refs.size)
    assertEquals(150 + 4 + 32, lib.all().count { it.isUser })
  }

  @Test fun slotsSurviveJsonAndOldFilesStillDecode() {
    assertEquals(5, PresetCodec.decode(PresetCodec.encode(PresetFile(name = "S", slot = 5))).slot)
    assertEquals(0, PresetCodec.decode("""{"format":1,"name":"Velho"}""").slot)
  }

  @Test fun bankCategoriesDropALeadingDx7FromTheFileName() {
    val bank = Dx7Fixtures.synthBank()
    val factory = factoryDir()
    for ((stem, cat) in listOf("DX7 rom3a" to "DX7 rom3a", "dx7_rom3a" to "DX7 rom3a", "DX7" to "DX7 importado", "meu cartucho" to "DX7 meu cartucho")) {
      val lib = PresetLibrary(DirFactory(factory), tmp.newFolder(), MemFavorites())
      assertEquals(stem, cat, lib.importBytes(bank, stem).refs.first().category)
    }
  }

  @Test fun singleVoicesShareOneImportedCategory() {
    val lib = library()
    val voice = Dx7Codec.writeVoice(Dx7Codec.readBank(Dx7Fixtures.synthBank(), "x").presets[0])
    val a = lib.importBytes(voice, "um").refs.single()
    val b = lib.importBytes(voice, "dois").refs.single()
    assertEquals("DX7 Importados", a.category)
    assertEquals("DX7 Importados", b.category)
  }

  @Test fun aMissingOrEmptyCartridgeFolderMeansNoDx7Shelves() {
    val missing = PresetLibrary(DirFactory(factoryDir()), File(tmp.root, "user"), MemFavorites(), DirCartridges(File(tmp.root, "nao-existe")))
    assertEquals(listOf("factory:010-a", "factory:020-b"), missing.all().map { it.id })
    val empty = PresetLibrary(DirFactory(tmp.newFolder("f2")), File(tmp.root, "user2"), MemFavorites(), DirCartridges(tmp.newFolder("vazio")))
    assertEquals(emptyList<String>(), empty.all().map { it.id })
  }

  @Test fun theRealFactoryCartridgesLoadWithTheirKnownNames() {
    val rom1a = Dx7Fixtures.rom("rom1a")
    val dir = tmp.newFolder("dx7real").also { File(it, "rom1a.syx").writeBytes(rom1a) }
    val lib = PresetLibrary(DirFactory(factoryDir()), File(tmp.root, "user"), MemFavorites(), DirCartridges(dir))
    assertEquals("BRASS   1", lib.read("factory:dx7-rom1a-01").name)
    assertEquals(2 + 32, lib.all().size)
  }
}
