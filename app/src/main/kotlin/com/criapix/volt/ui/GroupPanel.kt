package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
  val knobs = params.filter { ParamUi.of(it.name) is KnobUi && !(group.drawing == Drawing.ALGORITHM && it.name == "fm.algorithm") }

  Column(modifier.background(VoltColors.panel, RoundedCornerShape(10.dp)).padding(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(group.title, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = VoltColors.dim, modifier = Modifier.weight(1f))
      if (toggles.size <= 2) toggles.forEach { LearnTarget(Target.Param(it.name)) { Toggle(it, ParamUi.of(it.name) as ToggleUi, state, onStep) } }
    }
    if (toggles.size > 2) {
      // many toggles (the FM GLOBAL group): wrap on their own rows so the title keeps its width
      @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
      androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        toggles.forEach { LearnTarget(Target.Param(it.name)) { Toggle(it, ParamUi.of(it.name) as ToggleUi, state, onStep) } }
      }
    }
    choices.forEach { LearnTarget(Target.Param(it.name), Modifier.fillMaxWidth()) { Selector(it, ParamUi.of(it.name) as ChoiceUi, state, onStep) } }
    when (group.drawing) {
      Drawing.FILTER_CURVE -> FilterCurveView(state, Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp))
      Drawing.FENV -> EnvelopeView(state, "fenv", Modifier.weight(0.45f).fillMaxWidth().padding(vertical = 6.dp))
      Drawing.AENV -> EnvelopeView(state, "aenv", Modifier.weight(0.45f).fillMaxWidth().padding(vertical = 6.dp))
      Drawing.ALGORITHM -> AlgorithmStepper(state, onStep, Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp))
      Drawing.PITCH_ENV -> Dx7EnvelopeView(state, "fm.pitchenv", pitch = true, modifier = Modifier.weight(0.5f).fillMaxWidth().padding(vertical = 4.dp))
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

/** The algorithm drawing with a step button on each side (the same setter the knobs use; wraps 1..32). */
@Composable
private fun AlgorithmStepper(state: SynthState, onStep: () -> kotlin.Unit, modifier: Modifier) {
  val param = state.params["fm.algorithm"]!!
  val norm by state.value(param.id).collectAsStateWithLifecycle()
  val current = param.toReal(norm).toInt().coerceIn(1, 32)
  fun step(delta: Int) {
    val next = (current - 1 + delta + 32) % 32 + 1
    state.set(param.id, param.toNorm(next.toFloat()))
    onStep()
  }
  LearnTarget(Target.Param("fm.algorithm"), modifier) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StepButton("◀") { step(-1) }
      AlgorithmView(state, Modifier.weight(1f).fillMaxHeight())
      StepButton("▶") { step(1) }
    }
  }
}

@Composable
private fun StepButton(label: String, onClick: () -> kotlin.Unit) {
  Box(
    Modifier.size(52.dp).background(VoltColors.raised, RoundedCornerShape(10.dp)).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { Text(label, fontSize = 20.sp, lineHeight = 24.sp, color = VoltColors.text) }
}
