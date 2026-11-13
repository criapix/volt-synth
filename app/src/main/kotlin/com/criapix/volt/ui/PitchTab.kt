package com.criapix.volt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criapix.volt.midi.Target
import com.criapix.volt.state.SynthState

/** Height under a knob taken by its label and value lines (dp), as in KnobGrid. */
private const val KNOB_TEXT = 32f

/** The PITCH/LFO tab: pitch envelope (drawing + 8 knobs), the LFO and the three controllers. */
@Composable
fun PitchTab(state: SynthState, onStep: () -> kotlin.Unit, modifier: Modifier = Modifier) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    PanelFrame(Panel.pitchEnvelope.title, Modifier.weight(1.1f).fillMaxHeight()) {
      Dx7EnvelopeView(state, "fm.pitchenv", pitch = true, modifier = Modifier.weight(0.5f).fillMaxWidth().padding(vertical = 4.dp))
      val rows = Panel.pitchEnvelope.params.chunked(4)  // R1..R4, L1..L4
      BoxWithConstraints(Modifier.weight(0.5f).fillMaxWidth()) {
        val knob = ((maxHeight.value / rows.size) - KNOB_TEXT - 2f).coerceIn(32f, 56f).dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
          rows.forEach { KnobRow(it, 4, state, knob, onStep) }
        }
      }
    }
    LfoPanel(state, onStep, Modifier.weight(1.1f).fillMaxHeight())
    ControllersPanel(Panel.controllers, state, onStep, Modifier.weight(1.5f).fillMaxHeight())
  }
}

/** The FM LFO: wave and key sync on top, the five knobs below. */
@Composable
private fun LfoPanel(state: SynthState, onStep: () -> kotlin.Unit, modifier: Modifier) {
  val g = Panel.fmLfo
  val wave = g.params.first { ParamUi.of(it) is ChoiceUi }
  val sync = g.params.first { ParamUi.of(it) is ToggleUi }
  val knobs = g.params.filter { ParamUi.of(it) is KnobUi }
  PanelFrame(
    g.title, modifier,
    trailing = { LearnTarget(Target.Param(sync)) { Toggle(state.params[sync]!!, ParamUi.of(sync) as ToggleUi, state, onStep, height = 28.dp, fontSize = 11.sp) } },
  ) {
    LearnTarget(Target.Param(wave), Modifier.fillMaxWidth().padding(top = 2.dp)) {
      Selector(state.params[wave]!!, ParamUi.of(wave) as ChoiceUi, state, onStep, height = 30.dp, fontSize = 10.sp)
    }
    val rows = knobs.chunked(3)
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
      val knob = ((maxHeight.value / rows.size) - KNOB_TEXT - 2f).coerceIn(32f, 56f).dp
      Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        rows.forEach { KnobRow(it, 3, state, knob, onStep) }
      }
    }
  }
}
