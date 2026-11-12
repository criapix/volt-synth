package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.Curve
import com.criapix.volt.midi.MidiDispatch
import com.criapix.volt.midi.Target
import com.criapix.volt.midi.describe
import com.criapix.volt.state.SynthState

@Composable
fun MidiMapDialog(state: SynthState, io: ScreenIo, onClose: () -> kotlin.Unit) {
  val bindings by io.learn.bindings.collectAsStateWithLifecycle()
  val last by io.lastMidi.collectAsStateWithLifecycle()
  var channel by remember { mutableIntStateOf(io.midiSettings.channel) }
  var takeover by remember { mutableStateOf(io.midiSettings.softTakeover) }
  var confirmClear by remember { mutableStateOf(false) }

  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxSize(), color = VoltColors.background) {
      Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Controle MIDI", fontSize = 18.sp)
          Text("Canal", fontSize = 12.sp, color = VoltColors.dim)
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(-1) + (0..15)).forEach { c ->
              val on = c == channel
              Text(
                if (c < 0) "Omni" else "${c + 1}", fontSize = 13.sp,
                color = if (on) Color.Black else VoltColors.text,
                modifier = Modifier.background(if (on) VoltColors.accent else VoltColors.raised, RoundedCornerShape(14.dp))
                  .clickable { channel = c; io.midiSettings.channel = c }.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Captura suave")
              Text("Depois de trocar de preset, o controle só assume o parâmetro quando passa pelo valor atual.", fontSize = 11.sp, lineHeight = 14.sp, color = VoltColors.dim)
            }
            Switch(takeover, { takeover = it; io.midiSettings.softTakeover = it })
          }
          Text("Última mensagem: ${last.ifEmpty { "nenhuma" }}", fontSize = 13.sp)
          Text("Mapa de fábrica", fontSize = 12.sp, color = VoltColors.dim)
          Text(factoryMapText(), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Column(Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Associações aprendidas (${bindings.size})", fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (bindings.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Apagar todas", color = VoltColors.danger) }
            TextButton(onClick = onClose) { Text("Fechar") }
          }
          if (bindings.isEmpty()) {
            Text("Nenhuma associação. Toque em MIDI na barra superior para entrar no modo aprender.", color = VoltColors.dim, modifier = Modifier.padding(16.dp))
          }
          LazyColumn(Modifier.fillMaxSize()) {
            items(bindings, key = { it.first.toString() }) { (source, binding) ->
              val t = binding.target
              val canTap = t is Target.Action || (t is Target.Param && state.params[t.name]?.let { it.curve == Curve.STEP && it.min == 0f && it.max == 1f } == true)
              Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).background(VoltColors.panel, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(describe(source), Modifier.width(150.dp), fontSize = 13.sp)
                Text("→  ${targetLabel(t)}", Modifier.weight(1f), fontSize = 13.sp)
                if (canTap) {
                  Text("qualquer valor dispara", fontSize = 11.sp, color = VoltColors.dim)
                  Switch(binding.tap, { io.learn.setTap(source, it) })
                }
                TextButton(onClick = { io.learn.unbind(source) }) { Text("Apagar", color = VoltColors.danger) }
              }
            }
          }
        }
      }
    }
  }
  if (confirmClear) AlertDialog(
    onDismissRequest = { confirmClear = false },
    title = { Text("Apagar todas as associações?") },
    text = { Text("O mapa de fábrica continua valendo.") },
    confirmButton = { TextButton(onClick = { io.learn.clearAll(); confirmClear = false }) { Text("Apagar", color = VoltColors.danger) } },
    dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
  )
}

private fun factoryMapText(): String {
  val params = MidiDispatch.FACTORY_PARAMS.entries.joinToString("\n") { (cc, name) -> "CC $cc → ${targetLabel(Target.Param(name))}" }
  return "Notas, pitch bend\nCC 1 → roda de modulação (LFO 1)\nCC 64 → sustain\n$params\nCC 120, 123 → pânico\nProgram Change → preset na posição"
}
