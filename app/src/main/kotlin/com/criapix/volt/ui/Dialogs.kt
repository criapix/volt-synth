package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.Curve
import com.criapix.volt.Param
import com.criapix.volt.preset.AppModel
import com.criapix.volt.preset.PresetCodec
import com.criapix.volt.state.SynthState
import kotlinx.coroutines.delay

@Composable
fun SaveDialog(model: AppModel, onClose: () -> kotlin.Unit) {
  val current = model.current.value
  var name by remember { mutableStateOf(current.name) }
  var category by remember { mutableStateOf(current.category) }
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text("Salvar preset") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          (listOf("") + PresetCodec.CATEGORIES).forEach { key ->
            Text(
              categoryLabel(key), fontSize = 13.sp,
              color = if (key == category) Color.Black else VoltColors.text,
              modifier = Modifier.background(if (key == category) VoltColors.accent else VoltColors.raised, RoundedCornerShape(14.dp))
                .clickable { category = key }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }
        if (!model.canOverwrite()) Text("Presets de fábrica não podem ser alterados: salve como novo.", fontSize = 12.sp, color = VoltColors.dim)
      }
    },
    confirmButton = {
      Row {
        if (model.canOverwrite()) TextButton(onClick = { model.save(name, category); onClose() }) { Text("Substituir") }
        TextButton(onClick = { model.saveAs(name, category); onClose() }) { Text("Salvar como novo") }
      }
    },
    dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
  )
}

@Composable
fun SettingsDialog(state: SynthState, io: ScreenIo, onClose: () -> kotlin.Unit, onMidiMap: () -> kotlin.Unit) {
  var info by remember { mutableStateOf(io.audioInfo()) }
  var allParams by remember { mutableStateOf(false) }
  val last by io.lastMidi.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { while (true) { info = io.audioInfo(); delay(500) } }
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text("Configurações") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Áudio", color = VoltColors.dim, fontSize = 12.sp)
        Text(formatInfo(info), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text("Última mensagem MIDI: ${last.ifEmpty { "nenhuma" }}", fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Vibração", modifier = Modifier.weight(1f))
          var on by remember { mutableStateOf(io.haptics.enabled) }
          Switch(on, { on = it; io.haptics.enabled = it })
        }
        TextButton(onClick = { allParams = true }) { Text("Todos os parâmetros (lista)") }
        TextButton(onClick = { onClose(); onMidiMap() }) { Text("Controle MIDI (mapa, canal, captura suave)") }
      }
    },
    confirmButton = { TextButton(onClick = onClose) { Text("Fechar") } },
  )
  if (allParams) AllParamsDialog(state, onClose = { allParams = false })
}

/** Every parameter as a slider: a fallback view, and a way to reach anything quickly. */
@Composable
private fun AllParamsDialog(state: SynthState, onClose: () -> kotlin.Unit) {
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxSize(), color = VoltColors.background) {
      Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Todos os parâmetros", fontSize = 18.sp, modifier = Modifier.weight(1f))
          TextButton(onClick = onClose) { Text("Fechar") }
        }
        LazyColumn(Modifier.fillMaxWidth()) { items(state.params.all, key = { it.id }) { ParamRow(it, state) } }
      }
    }
  }
}

@Composable
private fun ParamRow(p: Param, state: SynthState) {
  val norm by state.value(p.id).collectAsStateWithLifecycle()
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(p.name, Modifier.width(170.dp), fontSize = 13.sp)
    Slider(
      value = norm, onValueChange = { state.set(p.id, it) },
      steps = if (p.curve == Curve.STEP) (p.max - p.min).toInt() - 1 else 0,
      modifier = Modifier.weight(1f),
    )
    Text(ParamUi.format(p, p.toReal(norm)), Modifier.width(90.dp), fontSize = 13.sp)
  }
}
