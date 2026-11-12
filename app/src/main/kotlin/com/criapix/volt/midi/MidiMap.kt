package com.criapix.volt.midi

/** What a controller sends; [ch] is 0..15. */
sealed interface Source {
  val ch: Int
  data class Cc(override val ch: Int, val num: Int) : Source
  data class Note(override val ch: Int, val note: Int) : Source
}

/** Preset and safety actions a controller can trigger. [key] is stored in preferences: never change it. */
enum class MidiAction(val key: String, val label: String) {
  PREV("prev", "Preset anterior"),
  NEXT("next", "Próximo preset"),
  PANIC("panic", "Pânico"),
}

/** What a binding drives. Parameters are stored by their permanent name. */
sealed interface Target {
  data class Param(val name: String) : Target
  data class Action(val action: MidiAction) : Target
}

/** [tap]: every message fires (debounced), for buttons that send 127 and 0 on alternate presses. */
data class Binding(val target: Target, val tap: Boolean = false)

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

fun noteName(note: Int) = "${NOTE_NAMES[note % 12]}${note / 12 - 1}"  // 60 = C4

fun describe(s: Source): String = when (s) {
  is Source.Cc -> "CC ${s.num} · canal ${s.ch + 1}"
  is Source.Note -> "Nota ${noteName(s.note)} · canal ${s.ch + 1}"
}

/** Source -> binding, in learning order. Not thread-safe: MidiLearn guards it. */
class MidiMap {
  private val m = LinkedHashMap<Source, Binding>()

  operator fun get(s: Source): Binding? = m[s]
  fun entries(): List<Pair<Source, Binding>> = m.entries.map { it.key to it.value }
  fun sourcesOf(t: Target): List<Source> = m.filterValues { it.target == t }.keys.toList()
  fun bind(s: Source, t: Target, tap: Boolean = false) { m.remove(s); m[s] = Binding(t, tap) }
  fun setTap(s: Source, tap: Boolean) { m[s]?.let { m[s] = it.copy(tap = tap) } }
  fun unbind(s: Source) { m.remove(s) }
  fun clear(t: Target) { m.entries.removeAll { it.value.target == t } }
  fun clearAll() = m.clear()

  /** "cc.0.74=p:filter.cutoff;note.0.36=a:next!" — '!' marks a tap binding. */
  fun encode(): String = m.entries.joinToString(";") { (s, b) ->
    val src = when (s) {
      is Source.Cc -> "cc.${s.ch}.${s.num}"
      is Source.Note -> "note.${s.ch}.${s.note}"
    }
    val dst = when (val t = b.target) {
      is Target.Param -> "p:${t.name}"
      is Target.Action -> "a:${t.action.key}"
    }
    "$src=$dst" + if (b.tap) "!" else ""
  }

  companion object {
    /** Entries that do not parse are skipped: a damaged preference never breaks MIDI. */
    fun decode(text: String): MidiMap {
      val map = MidiMap()
      for (entry in text.split(';')) {
        val eq = entry.indexOf('=')
        if (eq <= 0) continue
        val s = source(entry.substring(0, eq)) ?: continue
        val rest = entry.substring(eq + 1)
        val t = target(rest.removeSuffix("!")) ?: continue
        map.bind(s, t, rest.endsWith("!"))
      }
      return map
    }

    private fun source(text: String): Source? {
      val p = text.split('.')
      if (p.size != 3) return null
      val ch = p[1].toIntOrNull()?.takeIf { it in 0..15 } ?: return null
      val n = p[2].toIntOrNull()?.takeIf { it in 0..127 } ?: return null
      return when (p[0]) {
        "cc" -> Source.Cc(ch, n)
        "note" -> Source.Note(ch, n)
        else -> null
      }
    }

    private fun target(text: String): Target? = when {
      text.startsWith("p:") && text.length > 2 -> Target.Param(text.substring(2))
      text.startsWith("a:") -> MidiAction.entries.firstOrNull { it.key == text.substring(2) }?.let { Target.Action(it) }
      else -> null
    }
  }
}
