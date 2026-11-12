package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criapix.volt.midi.Source
import com.criapix.volt.midi.Target
import com.criapix.volt.midi.noteName

/** "Cutoff · FILTRO", "Próximo preset". */
fun targetLabel(t: Target): String = when (t) {
  is Target.Action -> t.action.label
  is Target.Param -> {
    val group = Panel.groupOf(t.name)
    if (group == null) "${t.name} (parâmetro removido)" else "${ParamUi.of(t.name).label} · ${group.title}"
  }
}

/** Badge on a bound control: "CC 74", "CC 74/3" (channel 3), "C4". Channel 1 is not written. */
fun shortLabel(s: Source): String {
  val base = when (s) {
    is Source.Cc -> "CC ${s.num}"
    is Source.Note -> noteName(s.note)
  }
  return if (s.ch == 0) base else "$base/${s.ch + 1}"
}

/** Learn mode as the controls see it; null when Learn mode is off. */
class LearnUi(val armed: Target?, val sources: Map<Target, List<Source>>, val onArm: (Target) -> kotlin.Unit)

val LocalLearn = compositionLocalOf<LearnUi?> { null }

/**
 * Wraps a learnable control. In Learn mode: outline, CC badge, and a transparent overlay that
 * takes every touch (so tapping arms the control instead of turning it). Otherwise: just the control.
 */
@Composable
fun LearnTarget(target: Target, modifier: Modifier = Modifier, content: @Composable () -> kotlin.Unit) {
  val learn = LocalLearn.current
  if (learn == null) {
    Box(modifier) { content() }
  } else {
    val armed = learn.armed == target
    val bound = learn.sources[target].orEmpty()
    Box(
      modifier.border(
        if (armed) 3.dp else 1.dp,
        if (armed) VoltColors.accent else VoltColors.accent2.copy(alpha = 0.6f),
        RoundedCornerShape(8.dp),
      ),
    ) {
      content()
      Box(Modifier.matchParentSize().pointerInput(target) { detectTapGestures { learn.onArm(target) } })
      if (bound.isNotEmpty()) {
        Text(
          shortLabel(bound.first()) + if (bound.size > 1) " +${bound.size - 1}" else "",
          fontSize = 9.sp, lineHeight = 11.sp, color = Color.Black,
          modifier = Modifier.align(Alignment.TopEnd).background(VoltColors.accent2, RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
        )
      }
    }
  }
}

@Composable
fun LearnBanner(
  armed: Target?, armedHasBindings: Boolean, confirmation: String?,
  onForget: () -> kotlin.Unit, onMap: () -> kotlin.Unit, onDone: () -> kotlin.Unit,
) {
  Row(
    Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp).background(VoltColors.raised, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val text = confirmation ?: when (armed) {
      null -> "Toque num controle da tela e depois mexa num controle do teclado"
      else -> "${targetLabel(armed)}: mexa num controle do teclado…"
    }
    Text(text, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
      color = if (confirmation != null) VoltColors.accent else VoltColors.text, modifier = Modifier.weight(1f))
    if (armed != null && armedHasBindings) TextButton(onClick = onForget) { Text("Esquecer", color = VoltColors.danger) }
    TextButton(onClick = onMap) { Text("Mapa…") }
    TextButton(onClick = onDone) { Text("Concluir") }
  }
}
