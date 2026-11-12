package com.criapix.volt.ui

import java.util.Locale

/** Labels for the `key=value` lines of [com.criapix.volt.Native.audioInfo], in the order they are shown. */
private val INFO_LABELS = listOf(
  "sampleRate" to "Taxa (Hz)", "burst" to "Bloco (quadros)", "buffer" to "Buffer (quadros)",
  "lowLatency" to "Baixa latência", "exclusive" to "Exclusivo", "api" to "API",
  "latencyMs" to "Latência de saída (ms)", "xruns" to "Estouros", "load" to "Carga do motor",
  "restarts" to "Reinícios", "voices" to "Vozes", "voiceLimit" to "Polifonia",
)

fun formatInfo(info: String): String {
  val map = info.lineSequence().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
  if (map["running"] != "1") return "Áudio parado"
  return INFO_LABELS.joinToString("   ") { (key, label) ->
    val v = map[key] ?: "?"
    val shown = when (key) {
      "lowLatency", "exclusive" -> if (v == "1") "sim" else "NÃO"
      "load" -> v.toFloatOrNull()?.let { "${(it * 100).toInt()} %" } ?: v
      "latencyMs" -> v.toFloatOrNull()?.let { String.format(Locale.ROOT, "%.1f", it) } ?: v
      else -> v
    }
    "$label: $shown"
  }
}
