package com.criapix.volt.ui

import com.criapix.volt.preset.PresetRef
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserFilterTest {
  private val list = listOf(
    PresetRef("factory:1", "Baixo Gordo", "Bass"),
    PresetRef("factory:2", "Pad Analógico", "Pad"),
    PresetRef("user:a", "Meu baixo", "Bass"),
  )

  @Test fun filtersByShelf() {
    assertEquals(3, BrowserFilter.apply(list, Shelf.All, "", emptySet()).size)
    assertEquals(listOf("user:a"), BrowserFilter.apply(list, Shelf.User, "", emptySet()).map { it.id })
    assertEquals(listOf("factory:2"), BrowserFilter.apply(list, Shelf.Favorites, "", setOf("factory:2")).map { it.id })
    assertEquals(listOf("factory:1", "user:a"), BrowserFilter.apply(list, Shelf.Category("Bass"), "", emptySet()).map { it.id })
  }

  @Test fun searchIgnoresCaseAndAccents() {
    assertEquals(listOf("factory:2"), BrowserFilter.apply(list, Shelf.All, "analogico", emptySet()).map { it.id })
    assertEquals(listOf("factory:1", "user:a"), BrowserFilter.apply(list, Shelf.All, "BAIXO", emptySet()).map { it.id })
  }

  @Test fun libraryShelvesAddDx7CategoriesInFirstAppearanceOrder() {
    val dx = list + listOf(
      PresetRef("factory:dx7-rom1b-01", "A", "DX7 ROM1B"),
      PresetRef("factory:dx7-rom1a-01", "B", "DX7 ROM1A"),
      PresetRef("factory:dx7-rom1b-02", "C", "DX7 ROM1B"),
    )
    val shelves = Shelf.forLibrary(dx)
    assertEquals(Shelf.ALL + listOf(Shelf.Category("DX7 ROM1B"), Shelf.Category("DX7 ROM1A")), shelves)
    assertEquals("DX7 ROM1B", shelves[Shelf.ALL.size].label)
    assertEquals(Shelf.ALL, Shelf.forLibrary(list))
    assertEquals(listOf("factory:dx7-rom1a-01"), BrowserFilter.apply(dx, Shelf.Category("DX7 ROM1A"), "", emptySet()).map { it.id })
  }
}
