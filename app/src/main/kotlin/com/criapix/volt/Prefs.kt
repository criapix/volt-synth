package com.criapix.volt

import android.content.Context
import com.criapix.volt.preset.FavoriteStore

class Prefs(context: Context) : FavoriteStore {
  private val p = context.getSharedPreferences("volt", Context.MODE_PRIVATE)

  var haptics: Boolean
    get() = p.getBoolean("haptics", true)
    set(v) = p.edit().putBoolean("haptics", v).apply()

  /** The learned MIDI map, MidiMap.encode() format. */
  var midiMap: String
    get() = p.getString("midiMap", "") ?: ""
    set(v) = p.edit().putString("midiMap", v).apply()

  /** -1 = Omni (every channel), else 0..15. */
  var midiChannel: Int
    get() = p.getInt("midiChannel", -1)
    set(v) = p.edit().putInt("midiChannel", v).apply()

  var softTakeover: Boolean
    get() = p.getBoolean("softTakeover", true)
    set(v) = p.edit().putBoolean("softTakeover", v).apply()

  // getStringSet returns a set that must not be modified; copy it.
  override fun load(): Set<String> = p.getStringSet("favorites", emptySet())!!.toSet()
  override fun save(ids: Set<String>) = p.edit().putStringSet("favorites", HashSet(ids)).apply()
}
