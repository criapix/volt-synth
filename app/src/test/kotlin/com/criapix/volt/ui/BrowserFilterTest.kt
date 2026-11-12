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
}
