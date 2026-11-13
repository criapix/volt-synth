package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.Native
import com.criapix.volt.midi.Target
import com.criapix.volt.state.SynthState

/** The rounded panel with a small title (and optional [trailing] controls on the title row) that every group sits in. */
@Composable
fun PanelFrame(
  title: String, modifier: Modifier = Modifier, trailing: @Composable () -> kotlin.Unit = {},
  content: @Composable ColumnScope.() -> kotlin.Unit,
) {
  Column(modifier.background(VoltColors.panel, RoundedCornerShape(10.dp)).padding(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(title, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = VoltColors.dim, modifier = Modifier.weight(1f))
      trailing()
    }
    content()
  }
}

/** One parameter's knob with MIDI Learn, written through the same setter the group panels use. */
@Composable
fun ParamKnob(name: String, state: SynthState, size: Dp, onStep: () -> kotlin.Unit) {
  LearnTarget(Target.Param(name)) { Knob(state.params[name]!!, ParamUi.of(name) as KnobUi, state, size, onStep, minWidth = 72.dp) }
}

/** A row of equal cells, each holding one knob (a short row keeps its cells aligned with the rows above it). */
@Composable
fun KnobRow(names: List<String>, cells: Int, state: SynthState, size: Dp, onStep: () -> kotlin.Unit, modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    names.forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ParamKnob(it, state, size, onStep) } }
    repeat(cells - names.size) { Box(Modifier.weight(1f)) }
  }
}

/** Height under a knob taken by its label and value lines (dp), as in KnobGrid. */
private const val KNOB_TEXT = 32f

/**
 * The OPERADOR tab: six operator chips and a two-page toggle (Envelope | Freq/Escala) on top, and the selected
 * operator's page below. Where each parameter lives is decided by [Panel.operatorEnvelope], [Panel.operatorFrequency]
 * and [Panel.operatorScaling].
 */
@Composable
fun OperatorTab(state: SynthState, onStep: () -> kotlin.Unit, modifier: Modifier = Modifier) {
  var operator by rememberSaveable { mutableIntStateOf(1) }
  var page by rememberSaveable { mutableIntStateOf(0) }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      (1..6).forEach { n -> OperatorChip(state, n, n == operator, { operator = n; onStep() }, Modifier.weight(1f).fillMaxHeight()) }
      PageToggle(page, { page = it; onStep() }, Modifier.width(230.dp).fillMaxHeight())
    }
    if (page == 0) EnvelopePage(state, operator, onStep, Modifier.weight(1f).fillMaxWidth())
    else FrequencyPage(state, operator, onStep, Modifier.weight(1f).fillMaxWidth())
  }
}

@Composable
private fun PageToggle(page: Int, onPage: (Int) -> kotlin.Unit, modifier: Modifier) {
  Row(modifier.border(1.dp, VoltColors.track, RoundedCornerShape(8.dp))) {
    listOf("Envelope", "Freq/Escala").forEachIndexed { i, label ->
      val on = i == page
      Box(
        Modifier.weight(1f).fillMaxHeight().background(if (on) VoltColors.accent else Color.Transparent, RoundedCornerShape(8.dp)).clickable { onPage(i) },
        contentAlignment = Alignment.Center,
      ) { Text(label, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, fontWeight = FontWeight.Bold, color = if (on) Color.Black else VoltColors.text) }
    }
  }
}

/** Number, role (C carrier / M modulator in the current algorithm) and output level; dimmed when the operator is off. */
@Composable
private fun OperatorChip(state: SynthState, n: Int, selected: Boolean, onClick: () -> kotlin.Unit, modifier: Modifier) {
  val algos = remember { Algorithms.decode(Native.fmAlgorithms()) }
  val algoParam = state.params["fm.algorithm"]!!
  val algoNorm by state.value(algoParam.id).collectAsStateWithLifecycle()
  val onNorm by state.value(state.params["fm.op$n.on"]!!.id).collectAsStateWithLifecycle()
  val levelParam = state.params["fm.op$n.level"]!!
  val levelNorm by state.value(levelParam.id).collectAsStateWithLifecycle()
  val role = algos[algoParam.toReal(algoNorm).toInt().coerceIn(1, 32) - 1].role(n)
  val on = onNorm >= 0.5f
  val alpha = if (on) 1f else 0.35f
  Column(
    modifier
      .background(if (selected) VoltColors.raised else VoltColors.panel, RoundedCornerShape(8.dp))
      .border(if (selected) 2.dp else 1.dp, if (selected) VoltColors.accent else VoltColors.track, RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Text("OP$n", fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = VoltColors.text.copy(alpha = alpha))
      Text(
        role, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold,
        color = (if (role == "C") VoltColors.accent else VoltColors.accent2).copy(alpha = alpha),
      )
    }
    Box(Modifier.fillMaxWidth().height(5.dp).background(VoltColors.track.copy(alpha = alpha), RoundedCornerShape(3.dp))) {
      Box(Modifier.fillMaxWidth(levelNorm.coerceIn(0f, 1f)).fillMaxHeight().background(VoltColors.accent2.copy(alpha = alpha), RoundedCornerShape(3.dp)))
    }
  }
}

@Composable
private fun EnvelopePage(state: SynthState, op: Int, onStep: () -> kotlin.Unit, modifier: Modifier) {
  val group = Panel.operatorEnvelope[op - 1]
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    PanelFrame("ENVELOPE · OP$op", Modifier.weight(1f).fillMaxHeight()) {
      Dx7EnvelopeView(state, "fm.op$op", modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp))
    }
    PanelFrame("TAXAS, NÍVEIS E SENSIBILIDADES", Modifier.weight(1.25f).fillMaxHeight()) {
      val rows = group.params.chunked(4)  // R1..R4, L1..L4, then level / velocity / rate scaling
      BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val knob = ((maxHeight.value / rows.size) - KNOB_TEXT - 2f).coerceIn(34f, KnobGrid.MAX).dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
          rows.forEach { KnobRow(it, 4, state, knob, onStep) }
        }
      }
    }
  }
}

@Composable
private fun FrequencyPage(state: SynthState, op: Int, onStep: () -> kotlin.Unit, modifier: Modifier) {
  val freq = Panel.operatorFrequency[op - 1]
  val scaling = Panel.operatorScaling[op - 1]
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    PanelFrame("FREQUÊNCIA · OP$op", Modifier.weight(1f).fillMaxHeight()) {
      val mode = state.params["fm.op$op.mode"]!!
      val coarse = state.params["fm.op$op.coarse"]!!
      val fine = state.params["fm.op$op.fine"]!!
      val modeNorm by state.value(mode.id).collectAsStateWithLifecycle()
      val coarseNorm by state.value(coarse.id).collectAsStateWithLifecycle()
      val fineNorm by state.value(fine.id).collectAsStateWithLifecycle()
      val text = FmFrequency.format(mode.toReal(modeNorm) >= 0.5f, coarse.toReal(coarseNorm).toInt(), fine.toReal(fineNorm).toInt())
      Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = VoltColors.accent2,
          maxLines = 1, textAlign = TextAlign.Start, modifier = Modifier.weight(1f),
        )
        LearnTarget(Target.Param(mode.name), Modifier.width(120.dp)) {
          Selector(mode, ParamUi.of(mode.name) as ChoiceUi, state, onStep, height = 30.dp, fontSize = 12.sp)
        }
      }
      BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val knob = (maxHeight.value - KNOB_TEXT - 4f).coerceIn(34f, 76f).dp
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          KnobRow(freq.params.filter { ParamUi.of(it) is KnobUi }, 3, state, knob, onStep)
        }
      }
    }
    PanelFrame("ESCALA DO TECLADO · OP$op", Modifier.weight(1.5f).fillMaxHeight()) {
      Row(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(0.8f).fillMaxHeight().background(VoltColors.raised, RoundedCornerShape(8.dp))) {
          KeyScalingView(state, op, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1.4f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
          val knobs = scaling.params.filter { ParamUi.of(it) is KnobUi }
          val choices = scaling.params.filter { ParamUi.of(it) is ChoiceUi }
          BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val knob = (maxHeight.value - KNOB_TEXT - 4f).coerceIn(34f, 64f).dp
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KnobRow(knobs, 4, state, knob, onStep) }
          }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { name ->
              LearnTarget(Target.Param(name), Modifier.weight(1f)) {
                Selector(state.params[name]!!, ParamUi.of(name) as ChoiceUi, state, onStep, height = 28.dp, fontSize = 11.sp)
              }
            }
          }
        }
      }
    }
  }
}
