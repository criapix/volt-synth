package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.preset.AppModel
import com.criapix.volt.preset.PresetRef

@Composable
fun PresetBrowser(model: AppModel, io: ScreenIo, onClose: () -> kotlin.Unit) {
  val presets by model.presets.collectAsStateWithLifecycle()
  val favorites by model.favorites.collectAsStateWithLifecycle()
  val current by model.current.collectAsStateWithLifecycle()
  var pickedShelf by remember { mutableStateOf<Shelf>(Shelf.All) }
  var query by remember { mutableStateOf("") }
  var editing by remember { mutableStateOf<PresetRef?>(null) }
  val shelves = remember(presets) { Shelf.forLibrary(presets) }
  // The picked shelf may have vanished (its last preset was deleted).
  val shelf = if (pickedShelf in shelves) pickedShelf else Shelf.All
  val shown = BrowserFilter.apply(presets, shelf, query, favorites)
  val positions = remember(presets) { model.positions(presets) }
  // A shelf made only of the user's FM presets can be exported as a 32-voice bank.
  val bankCategory = remember(presets, shelf) {
    (shelf as? Shelf.Category)?.key?.takeIf { key ->
      val inShelf = presets.filter { it.category == key }
      inShelf.isNotEmpty() && inShelf.all { it.isUser } && inShelf.all { model.isFm(it.id) }
    }
  }

  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxSize(), color = VoltColors.background) {
      Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.width(150.dp).fillMaxHeight()) {
          items(shelves) { s ->
            Text(
              s.label, fontSize = 14.sp,
              color = if (s == shelf) Color.Black else VoltColors.text,
              modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                .background(if (s == shelf) VoltColors.accent else Color.Transparent, RoundedCornerShape(6.dp))
                .clickable { pickedShelf = s }.padding(10.dp),
            )
          }
        }
        Column(Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Buscar") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = io.onImport) { Text("Importar") }
            TextButton(onClick = io.onExport) { Text("Exportar") }
            if (bankCategory != null) TextButton(onClick = {
              model.exportSyxBank(bankCategory)?.let { (bytes, dropped) ->
                val done = when (dropped) {
                  0 -> "Banco .syx salvo"
                  1 -> "Banco salvo; 1 preset ficou de fora (máximo 32)"
                  else -> "Banco salvo; $dropped presets ficaram de fora (máximo 32)"
                }
                io.onExportSyx(bankCategory, bytes, done)
              }
            }) { Text("Exportar banco .syx") }
            TextButton(onClick = onClose) { Text("Fechar") }
          }
          if (shown.isEmpty()) Text("Nenhum preset aqui.", color = VoltColors.dim, modifier = Modifier.padding(16.dp))
          LazyVerticalGrid(GridCells.Adaptive(190.dp), Modifier.fillMaxSize().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { ref ->
              val selected = ref.id == current.id
              Row(
                Modifier.height(64.dp).background(VoltColors.panel, RoundedCornerShape(8.dp))
                  .border(2.dp, if (selected) VoltColors.accent else Color.Transparent, RoundedCornerShape(8.dp))
                  .pointerInput(ref.id) {
                    detectTapGestures(
                      onTap = { model.select(ref.id); io.haptics.tick() },
                      onLongPress = { if (ref.isUser || model.isFm(ref.id)) editing = ref },
                    )
                  }
                  .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(Modifier.weight(1f)) {
                  Text(ref.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text("${(positions[ref.id] ?: 0).toString().padStart(3, '0')} · ${categoryLabel(ref.category)} · ${if (ref.isUser) "meu" else "fábrica"}", fontSize = 11.sp, color = VoltColors.dim)
                }
                Text(
                  if (ref.id in favorites) "★" else "☆", fontSize = 22.sp,
                  color = if (ref.id in favorites) VoltColors.accent else VoltColors.dim,
                  modifier = Modifier.clickable { model.toggleFavorite(ref.id) }.padding(8.dp),
                )
              }
            }
          }
        }
      }
    }
  }
  editing?.let { ref -> EditPresetDialog(model, io, ref, onClose = { editing = null }) }
}

/** Rename or delete one user preset; export an FM preset as .syx. Factory presets only get the export. */
@Composable
private fun EditPresetDialog(model: AppModel, io: ScreenIo, ref: PresetRef, onClose: () -> kotlin.Unit) {
  var name by remember { mutableStateOf(ref.name) }
  var confirmDelete by remember { mutableStateOf(false) }
  val canExport = remember(ref.id) { model.isFm(ref.id) }
  val exportButton: @Composable () -> kotlin.Unit = {
    TextButton(onClick = {
      model.exportSyx(ref.id)?.let { io.onExportSyx(ref.name, it, "Preset .syx salvo") }
      onClose()
    }) { Text("Exportar .syx") }
  }
  if (!ref.isUser) {
    AlertDialog(
      onDismissRequest = onClose,
      title = { Text(ref.name) },
      text = { Text("Preset de fábrica: dá para exportar como .syx.", color = VoltColors.dim) },
      confirmButton = exportButton,
      dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
    )
    return
  }
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text(if (confirmDelete) "Apagar \"${ref.name}\"?" else "Editar preset") },
    text = { if (!confirmDelete) OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true) else Text("Não dá para desfazer.") },
    confirmButton = {
      if (confirmDelete) TextButton(onClick = { model.delete(ref.id); onClose() }) { Text("Apagar", color = VoltColors.danger) }
      else TextButton(onClick = { model.rename(ref.id, name); onClose() }) { Text("Renomear") }
    },
    dismissButton = {
      Row {
        if (!confirmDelete && canExport) exportButton()
        if (!confirmDelete) TextButton(onClick = { confirmDelete = true }) { Text("Apagar…", color = VoltColors.danger) }
        TextButton(onClick = onClose) { Text("Cancelar") }
      }
    },
  )
}
