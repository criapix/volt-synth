package com.criapix.volt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criapix.volt.midi.Target
import com.criapix.volt.state.SynthState

/**
 * The FM controllers (wheel, aftertouch, breath): one labelled row each with its range knob and its three
 * routing toggles (pitch, amplitude, EG bias). [rows] come from [Panel.controllers].
 */
@Composable
fun ControllersPanel(rows: List<Group>, state: SynthState, onStep: () -> kotlin.Unit, modifier: Modifier = Modifier) {
  PanelFrame("CONTROLES", modifier) {
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
      rows.forEach { g ->
        val knobs = g.params.filter { ParamUi.of(it) is KnobUi }
        val toggles = g.params.filter { ParamUi.of(it) is ToggleUi }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
          val knob = (maxHeight.value - 34f).coerceIn(32f, 56f).dp
          Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            knobs.forEach { ParamKnob(it, state, knob, onStep) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(g.title, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = VoltColors.text, maxLines = 1)
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                toggles.forEach { name ->
                  LearnTarget(Target.Param(name)) { Toggle(state.params[name]!!, ParamUi.of(name) as ToggleUi, state, onStep, height = 28.dp, fontSize = 11.sp) }
                }
              }
            }
          }
        }
      }
    }
  }
}
