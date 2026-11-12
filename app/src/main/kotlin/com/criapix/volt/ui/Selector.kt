package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.Param
import com.criapix.volt.state.SynthState
import kotlin.math.roundToInt

/** Segmented buttons for a choice parameter (wave shape, filter model, voice mode). */
@Composable
fun Selector(param: Param, ui: ChoiceUi, state: SynthState, onStep: () -> Unit) {
  val norm by state.value(param.id).collectAsStateWithLifecycle()
  val selected = (param.toReal(norm) - param.min).roundToInt()
  Column {
    Text(ui.label, fontSize = 11.sp, color = VoltColors.dim)
    Row(Modifier.fillMaxWidth().height(34.dp).border(1.dp, VoltColors.track, RoundedCornerShape(6.dp))) {
      ui.options.forEachIndexed { i, option ->
        val on = i == selected
        Box(
          Modifier.weight(1f).height(34.dp)
            .background(if (on) VoltColors.accent else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { if (!on) { state.set(param.id, param.toNorm(param.min + i)); onStep() } },
          contentAlignment = Alignment.Center,
        ) {
          Text(option, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Clip, color = if (on) Color.Black else VoltColors.text)
        }
      }
    }
  }
}

/** An on/off chip for a 0..1 step parameter. */
@Composable
fun Toggle(param: Param, ui: ToggleUi, state: SynthState, onStep: () -> Unit) {
  val norm by state.value(param.id).collectAsStateWithLifecycle()
  val on = norm >= 0.5f
  Box(
    Modifier.height(30.dp)
      .background(if (on) VoltColors.accent else VoltColors.raised, RoundedCornerShape(15.dp))
      .clickable { state.set(param.id, if (on) 0f else 1f); onStep() },
    contentAlignment = Alignment.Center,
  ) {
    Text("  ${ui.label}  ", fontSize = 12.sp, color = if (on) Color.Black else VoltColors.text)
  }
}
