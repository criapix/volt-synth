package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criapix.volt.midi.MidiAction
import com.criapix.volt.midi.Target

/** A small button of the top bar. */
@Composable
private fun BarButton(text: String, modifier: Modifier = Modifier, fill: Color = VoltColors.raised, color: Color = VoltColors.text, width: Dp? = null, onClick: () -> kotlin.Unit) {
  Box(
    modifier.height(34.dp).let { if (width != null) it.width(width) else it }
      .background(fill, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 9.dp),
    contentAlignment = Alignment.Center,
  ) { Text(text, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = color, maxLines = 1, softWrap = false) }
}

/**
 * The bar across the top: preset, the voice-type and screen switches ([engineSwitch], [viewSwitch]), and the small
 * controls (save, panic, MIDI, voices and level, menu). [showKeys] adds the keyboard drawer's button (PAINEL only).
 */
@Composable
fun TopBar(
  number: Int, name: String, category: String, dirty: Boolean,
  midiDevices: Int, midiLit: Boolean, voices: Int, voiceLimit: Int = 16,
  onPrev: () -> kotlin.Unit, onNext: () -> kotlin.Unit, onBrowse: () -> kotlin.Unit,
  onSave: () -> kotlin.Unit, onPanic: () -> kotlin.Unit, onMenu: () -> kotlin.Unit,
  learnActive: Boolean, onMidi: () -> kotlin.Unit,
  engineSwitch: @Composable () -> kotlin.Unit, viewSwitch: @Composable () -> kotlin.Unit,
  showKeys: Boolean = false, keyboardOpen: Boolean = false, onKeyboard: () -> kotlin.Unit = {},
) {
  Row(
    Modifier.fillMaxWidth().height(48.dp).background(VoltColors.panel).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    LearnTarget(Target.Action(MidiAction.PREV)) { BarButton("◀", onClick = onPrev, width = 34.dp) }
    Column(
      Modifier.weight(1f).height(38.dp).background(VoltColors.raised, RoundedCornerShape(8.dp)).clickable(onClick = onBrowse).padding(horizontal = 9.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (number > 0) "%03d ".format(number) else "--- ", fontSize = 11.sp, lineHeight = 14.sp, color = VoltColors.dim)
        Text(name, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (dirty) Text("  •", fontSize = 12.sp, color = VoltColors.accent)
      }
      Text(categoryLabel(category), fontSize = 9.sp, lineHeight = 11.sp, color = VoltColors.dim, maxLines = 1)
    }
    LearnTarget(Target.Action(MidiAction.NEXT)) { BarButton("▶", onClick = onNext, width = 34.dp) }
    engineSwitch()
    viewSwitch()
    if (showKeys) BarButton("TECLAS", fill = if (keyboardOpen) VoltColors.text else VoltColors.raised, color = if (keyboardOpen) Color.Black else VoltColors.text, onClick = onKeyboard)
    BarButton("SALVAR", fill = VoltColors.accent, color = Color.Black, onClick = onSave)
    LearnTarget(Target.Action(MidiAction.PANIC)) { BarButton("PÂNICO", color = VoltColors.danger, onClick = onPanic) }
    Row(
      Modifier.height(34.dp)
        .background(if (learnActive) VoltColors.accent else VoltColors.raised, RoundedCornerShape(17.dp))
        .clickable(onClick = onMidi).padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(Modifier.size(9.dp).background(if (midiLit) VoltColors.accent2 else if (midiDevices > 0) VoltColors.track else VoltColors.panel, CircleShape))
      Text(
        when { learnActive -> "APRENDER"; midiDevices > 0 -> "MIDI"; else -> "sem MIDI" },
        fontSize = 11.sp, lineHeight = 14.sp, color = if (learnActive) Color.Black else VoltColors.text,
      )
    }
    val reduced = voiceLimit < 16
    Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
        if (reduced) "$voices/$voiceLimit" else "$voices vozes",
        fontSize = 10.sp, lineHeight = 12.sp, color = if (reduced) VoltColors.danger else VoltColors.dim, maxLines = 1,
      )
      LevelMeter(Modifier.fillMaxWidth().height(6.dp))
    }
    BarButton("⋮", onClick = onMenu, width = 32.dp)
  }
}

/** A two-option switch in the top bar's style; [options] are the labels, [hues] the colour each one takes when chosen. */
@Composable
fun SegmentSwitch(options: List<String>, selected: Int, hues: List<Color>, modifier: Modifier = Modifier, onSelect: (Int) -> kotlin.Unit) {
  Row(modifier.height(36.dp).background(VoltColors.background, RoundedCornerShape(10.dp)).border(1.dp, VoltColors.raised, RoundedCornerShape(10.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    options.forEachIndexed { i, o ->
      val on = i == selected
      Box(
        Modifier.fillMaxHeight().background(if (on) hues[i] else Color.Transparent, RoundedCornerShape(8.dp)).clickable { if (!on) onSelect(i) }.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
      ) { Text(o, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = if (on) Color(0xFF0B0C11) else VoltColors.dim, maxLines = 1, softWrap = false) }
    }
  }
}

/** The output level as a bar that repaints every frame; it turns red at the top. */
@Composable
private fun LevelMeter(modifier: Modifier) {
  val live = LocalLive.current
  val frame = LocalFrame.current
  androidx.compose.foundation.Canvas(modifier) {
    frame?.value
    val level = (live?.level ?: 0f).coerceIn(0f, 1f)
    drawRoundRect(VoltColors.raised, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
    drawRoundRect(if (level > 0.95f) VoltColors.danger else VoltColors.accent2, size = androidx.compose.ui.geometry.Size(size.width * level, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
  }
}
