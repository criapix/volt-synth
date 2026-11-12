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
}
