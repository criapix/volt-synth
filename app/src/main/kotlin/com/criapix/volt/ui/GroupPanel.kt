package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criapix.volt.midi.Target
import com.criapix.volt.state.SynthState

@Composable
fun GroupPanel(group: Group, state: SynthState, onStep: () -> kotlin.Unit, modifier: Modifier = Modifier) {
  val params = group.params.map { state.params[it]!! }
  val choices = params.filter { ParamUi.of(it.name) is ChoiceUi }
  val toggles = params.filter { ParamUi.of(it.name) is ToggleUi }
  val knobs = params.filter { ParamUi.of(it.name) is KnobUi }

  Column(modifier.background(VoltColors.panel, RoundedCornerShape(10.dp)).padding(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(group.title, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = VoltColors.dim, modifier = Modifier.weight(1f))
      toggles.forEach { LearnTarget(Target.Param(it.name)) { Toggle(it, ParamUi.of(it.name) as ToggleUi, state, onStep) } }
    }
    choices.forEach { LearnTarget(Target.Param(it.name), Modifier.fillMaxWidth()) { Selector(it, ParamUi.of(it.name) as ChoiceUi, state, onStep) } }
    when (group.drawing) {
      Drawing.FILTER_CURVE -> FilterCurveView(state, Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp))
      Drawing.FENV -> EnvelopeView(state, "fenv", Modifier.weight(0.45f).fillMaxWidth().padding(vertical = 6.dp))
      Drawing.AENV -> EnvelopeView(state, "aenv", Modifier.weight(0.45f).fillMaxWidth().padding(vertical = 6.dp))
      Drawing.NONE -> {}
    }
    if (knobs.isNotEmpty()) {
      BoxWithConstraints(Modifier.weight(if (group.drawing == Drawing.NONE) 1f else 0.55f).fillMaxWidth()) {
        val fit = KnobGrid.fit(knobs.size, maxWidth.value, maxHeight.value)
        val scroll = fit.knob < KnobGrid.MIN
        val size = (if (scroll) KnobGrid.MIN else fit.knob).dp
        val columns = if (scroll) ((maxWidth.value / (KnobGrid.MIN + KnobGrid.GAP)).toInt()).coerceAtLeast(1) else fit.columns
        Column(
          Modifier.fillMaxSize().let { if (scroll) it.verticalScroll(rememberScrollState()) else it },
          verticalArrangement = Arrangement.SpaceEvenly,
        ) {
          knobs.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
              row.forEach { LearnTarget(Target.Param(it.name)) { Knob(it, ParamUi.of(it.name) as KnobUi, state, size, onStep) } }
              repeat(columns - row.size) { Box(Modifier.height(1.dp).padding(horizontal = (size / 2))) }
            }
          }
        }
      }
    }
  }
}
