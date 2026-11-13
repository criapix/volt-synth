package com.criapix.volt.ui

import com.criapix.volt.preset.PresetCodec
import com.criapix.volt.preset.PresetRef
import java.text.Normalizer

sealed interface Shelf {
  val label: String
  data object All : Shelf { override val label = "Todos" }
  data object Favorites : Shelf { override val label = "Favoritos" }
  data object User : Shelf { override val label = "Meus presets" }
  data class Category(val key: String) : Shelf { override val label = categoryLabel(key) }

  companion object {
    val ALL: List<Shelf> = listOf(All, Favorites, User) + PresetCodec.CATEGORIES.map { Category(it) }

    /** Fixed shelves, the analog categories, then one shelf per DX7 category in first-appearance order. */
    fun forLibrary(list: List<PresetRef>): List<Shelf> =
      ALL + list.map { it.category }.filter { it.startsWith("DX7 ") }.distinct().map { Category(it) }
  }
}

object BrowserFilter {
  fun apply(list: List<PresetRef>, shelf: Shelf, query: String, favorites: Set<String>): List<PresetRef> {
    val q = fold(query.trim())
    return list.filter { ref ->
      when (shelf) {
        Shelf.All -> true
        Shelf.Favorites -> ref.id in favorites
        Shelf.User -> ref.isUser
        is Shelf.Category -> ref.category == shelf.key
      } && (q.isEmpty() || fold(ref.name).contains(q))
    }
  }

  private fun fold(s: String) =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
}
