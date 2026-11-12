package com.criapix.volt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Labels for the `key=value` lines of [Native.audioInfo], in the order they are shown. */
private val INFO_LABELS = listOf(
  "sampleRate" to "Taxa (Hz)", "burst" to "Bloco (quadros)", "buffer" to "Buffer (quadros)",
  "lowLatency" to "Baixa latência", "exclusive" to "Exclusivo", "api" to "API",
  "latencyMs" to "Latência de saída (ms)", "xruns" to "Estouros", "load" to "Carga do motor",
  "restarts" to "Reinícios", "voices" to "Vozes",
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

@Composable
fun DebugScreen(
  params: Params,
  info: String,
  midiDevices: Int,
  lastMidi: String,
  onParam: (Param, Float) -> Unit,
  onTestNote: (Boolean) -> Unit,
  onPanic: () -> Unit,
) {
  MaterialTheme(colorScheme = darkColorScheme()) {
    Surface(Modifier.fillMaxSize()) {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Volt", style = MaterialTheme.typography.titleLarge)
          Text(if (midiDevices > 0) "MIDI: $midiDevices conectado(s)" else "MIDI: nenhum teclado")
          Text(lastMidi)
          Button(onClick = { onTestNote(true) }) { Text("Tocar Lá") }
          Button(onClick = { onTestNote(false) }) { Text("Soltar") }
          Button(onClick = onPanic) { Text("Pânico") }
        }
        Text(formatInfo(info), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        LazyColumn(Modifier.fillMaxWidth()) {
          items(params.all, key = { it.id }) { p -> ParamRow(p, onParam) }
        }
      }
    }
  }
}

@Composable
private fun ParamRow(p: Param, onParam: (Param, Float) -> Unit) {
  var norm by remember(p.id) { mutableFloatStateOf(p.toNorm(p.def)) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(p.name, Modifier.width(170.dp), fontSize = 13.sp)
    Slider(
      value = norm,
      onValueChange = { norm = it; onParam(p, it) },
      steps = if (p.curve == Curve.STEP) (p.max - p.min).toInt() - 1 else 0,
      modifier = Modifier.weight(1f),
    )
    Text(String.format(Locale.ROOT, "%.3f", p.toReal(norm)), Modifier.width(90.dp), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
  }
}
