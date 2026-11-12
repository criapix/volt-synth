package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopBar(
  number: Int, name: String, category: String, dirty: Boolean,
  midiDevices: Int, midiLit: Boolean, voices: Int, level: Float,
  onPrev: () -> kotlin.Unit, onNext: () -> kotlin.Unit, onBrowse: () -> kotlin.Unit,
  onSave: () -> kotlin.Unit, onPanic: () -> kotlin.Unit, onMenu: () -> kotlin.Unit,
) {
  Row(
    Modifier.fillMaxWidth().height(52.dp).background(VoltColors.panel).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    TextButton(onClick = onPrev) { Text("◀", fontSize = 20.sp) }
    Column(
      Modifier.weight(1f).fillMaxHeight().background(VoltColors.raised, RoundedCornerShape(8.dp)).clickable(onClick = onBrowse).padding(horizontal = 10.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (number > 0) "%03d ".format(number) else "--- ", fontSize = 13.sp, color = VoltColors.dim)
        Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (dirty) Text("  •  editado", fontSize = 12.sp, color = VoltColors.accent)
      }
      Text(categoryLabel(category), fontSize = 11.sp, color = VoltColors.dim)
    }
    TextButton(onClick = onNext) { Text("▶", fontSize = 20.sp) }
    Button(onClick = onSave) { Text("Salvar") }
    TextButton(onClick = onPanic) { Text("Pânico", color = VoltColors.danger) }
    Box(Modifier.size(10.dp).background(if (midiLit) VoltColors.accent else if (midiDevices > 0) VoltColors.track else VoltColors.raised, CircleShape))
    Text(if (midiDevices > 0) "MIDI" else "sem MIDI", fontSize = 11.sp, color = VoltColors.dim)
    Text("$voices vozes", fontSize = 11.sp, color = VoltColors.dim, modifier = Modifier.width(54.dp))
    Box(Modifier.width(56.dp).height(8.dp).background(VoltColors.raised, RoundedCornerShape(4.dp))) {
      Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0f, 1f)).background(if (level > 0.95f) VoltColors.danger else VoltColors.accent2, RoundedCornerShape(4.dp)))
    }
    TextButton(onClick = onMenu) { Text("⋮", fontSize = 22.sp) }
  }
}
