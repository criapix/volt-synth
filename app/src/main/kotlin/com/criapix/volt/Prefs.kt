package com.criapix.volt

import android.content.Context
import com.criapix.volt.preset.FavoriteStore

class Prefs(context: Context) : FavoriteStore {
  private val p = context.getSharedPreferences("volt", Context.MODE_PRIVATE)

  var haptics: Boolean
    get() = p.getBoolean("haptics", true)
    set(v) = p.edit().putBoolean("haptics", v).apply()

  // getStringSet returns a set that must not be modified; copy it.
  override fun load(): Set<String> = p.getStringSet("favorites", emptySet())!!.toSet()
  override fun save(ids: Set<String>) = p.edit().putStringSet("favorites", HashSet(ids)).apply()
}
