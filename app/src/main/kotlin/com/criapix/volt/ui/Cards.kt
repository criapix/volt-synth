package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.midi.Target
import com.criapix.volt.state.SynthState
import kotlin.math.ceil

private fun feedbackFor(name: String, state: SynthState, live: Live?): ((Fx) -> Unit)? {
  val kind = Feedback.of(name) ?: return null
  if (live == null) return null
  return { fx -> Feedback.eval(kind, name, state, live, fx) }
}

/** A knob for the named parameter, in its family's colour, showing what it does to the sound, and open to MIDI Learn. */
@Composable
fun ParamKnob(name: String, state: SynthState, size: Dp, onStep: () -> Unit, modifier: Modifier = Modifier, minWidth: Dp = 0.dp) {
  val live = LocalLive.current
  val feedback = remember(name, live, state) { feedbackFor(name, state, live) }
  LearnTarget(Target.Param(name), modifier) {
    Knob(state.params[name]!!, ParamUi.of(name) as KnobUi, state, size, onStep, minWidth, Hues.of(name), feedback)
  }
}

private fun waveIconsOf(name: String): WaveIcons? = when (name) {
  "osc1.wave", "osc2.wave", "osc3.wave" -> WaveIcons.OSC
  "lfo1.wave" -> WaveIcons.LFO1
  "lfo2.wave" -> WaveIcons.LFO2
  "fm.lfo.wave" -> WaveIcons.FM_LFO
  else -> null
}

@Composable
fun ParamSelector(name: String, state: SynthState, onStep: () -> Unit, modifier: Modifier = Modifier, height: Dp = 30.dp) {
  val waves = waveIconsOf(name)
  LearnTarget(Target.Param(name), modifier.fillMaxWidth()) {
    Selector(state.params[name]!!, ParamUi.of(name) as ChoiceUi, state, onStep, height = height, fontSize = 11.sp, hue = Hues.of(name), waves = waves, showLabel = waves == null)
  }
}

/** How much the thing a switch controls is working now, so its light follows the sound. */
private fun ledOf(name: String): ((Live) -> Float)? {
  if (name == "arp.on") return { l -> l.arpPulse }
  if (name.startsWith("fx.") && name.endsWith(".on")) {
    val i = listOf("dist", "phaser", "flanger", "chorus", "delay", "reverb").indexOf(name.split('.')[1])
    return { l -> l.fxActivity[i] * 2f + 0.3f }
  }
  if (Regex("""fm\.op(\d)\.on""").matches(name)) {
    val n = name[5].digitToInt()
    return { l -> l.op[n] * 1.5f }
  }
  return null
}

@Composable
fun ParamToggle(name: String, state: SynthState, onStep: () -> Unit, modifier: Modifier = Modifier, height: Dp = 28.dp, label: String? = null) {
  val ui = ParamUi.of(name) as ToggleUi
  LearnTarget(Target.Param(name), modifier) {
    Toggle(state.params[name]!!, ui, state, onStep, height = height, fontSize = 9.5.sp, hue = Hues.of(name), led = remember(name) { ledOf(name) }, label = label ?: ui.label)
  }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
  Box(Modifier.size(44.dp).background(VoltColors.raised, RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    Text(label, fontSize = 18.sp, lineHeight = 22.sp, color = VoltColors.text)
  }
}

/** The algorithm picture with a step button on each side; a tap on an operator calls [onOperator]. */
@Composable
private fun AlgorithmBody(state: SynthState, onStep: () -> Unit, onOperator: (Int) -> Unit, modifier: Modifier) {
  val param = state.params["fm.algorithm"]!!
  val norm by state.value(param.id).collectAsStateWithLifecycle()
  val current = param.toReal(norm).toInt().coerceIn(1, 32)
  fun step(delta: Int) { state.set(param.id, param.toNorm(((current - 1 + delta + 32) % 32 + 1).toFloat())); onStep() }
  LearnTarget(Target.Param("fm.algorithm"), modifier) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StepButton("◀") { step(-1) }
      AlgorithmView(state, Modifier.weight(1f).fillMaxHeight(), onOperator)
      StepButton("▶") { step(1) }
    }
  }
}

/** The three controllers: for each, the range knob and the three places it is sent (pitch, volume, EG bias). */
@Composable
private fun ControllersBody(card: Card, state: SynthState, onStep: () -> Unit, modifier: Modifier) {
  Column(modifier, verticalArrangement = Arrangement.SpaceEvenly) {
    listOf("wheel" to "RODA", "at" to "AFTERTOUCH", "breath" to "BREATH").forEach { (c, title) ->
      BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val knob = (maxHeight.value - KnobGrid.COMPACT_LABEL - 2f).coerceIn(KnobGrid.COMPACT_MIN, 48f).dp
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          ParamKnob("fm.$c.range", state, knob, onStep)
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Hues.controllers)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              listOf("pitch", "amp", "eg").forEach { ParamToggle("fm.$c.$it", state, onStep, height = 26.dp) }
            }
          }
        }
      }
    }
  }
}

/** Which drawing a card uses, in [modifier]'s box. */
@Composable
private fun CardDrawing(card: Card, state: SynthState, onStep: () -> Unit, onOperator: (Int) -> Unit, modifier: Modifier) {
  val h = card.hue
  when (card.drawing) {
    Drawing.OSC -> OscDrawing(state, card.n, h, modifier)
    Drawing.MIXER -> MixerDrawing(state, modifier)
    Drawing.FILTER -> FilterCurveView(state, h, modifier)
    Drawing.HPF -> HpfDrawing(state, h, modifier)
    Drawing.FENV -> EnvelopeView(state, "fenv", h, modifier)
    Drawing.AENV -> EnvelopeView(state, "aenv", h, modifier)
    Drawing.VOICE -> VoiceDrawing(state, h, modifier)
    Drawing.LFO -> LfoDrawing(state, card.n, h, modifier)
    Drawing.POLYMOD -> PolyModDrawing(state, h, modifier)
    Drawing.ALGORITHM -> AlgorithmBody(state, onStep, onOperator, modifier)
    Drawing.FM_GLOBAL -> FmGlobalDrawing(state, modifier)
    Drawing.OP_FREQ -> OpFreqDrawing(state, card.n, modifier)
    Drawing.OP_ENV -> Dx7EnvelopeView(state, "fm.op${card.n}", h, card.n, modifier)
    Drawing.OP_SCALE -> KeyScalingView(state, card.n, h, modifier)
    Drawing.PITCH_ENV -> Dx7EnvelopeView(state, "fm.pitchenv", h, 0, modifier)
    Drawing.FM_LFO -> FmLfoDrawing(state, h, modifier)
    Drawing.CONTROLLERS -> ControllersDrawing(state, h, modifier)
    Drawing.ARP -> ArpDrawing(state, h, modifier)
    Drawing.DIST -> DistDrawing(state, h, modifier)
    Drawing.PHASER -> PhaserDrawing(state, h, modifier)
    Drawing.FLANGER -> FlangerDrawing(state, h, modifier)
    Drawing.CHORUS -> ChorusDrawing(state, h, modifier)
    Drawing.DELAY -> DelayDrawing(state, h, modifier)
    Drawing.REVERB -> ReverbDrawing(state, h, modifier)
    Drawing.NONE -> {}
  }
}

/** The smallest height a card's drawing is given before its controls shrink. */
private fun minDrawing(card: Card): Float = when (card.drawing) {
  Drawing.NONE -> 0f
  Drawing.ALGORITHM -> 120f
  Drawing.FM_GLOBAL -> 40f
  Drawing.OP_FREQ -> 52f
  Drawing.CONTROLLERS -> 30f
  else -> 44f
}

private fun isController(card: Card) = card.drawing == Drawing.CONTROLLERS

/** The width a card likes, in dp: enough columns for its knobs, never too narrow for its selectors. */
fun preferredWidth(card: Card): Float {
  val knobs = card.params.count { ParamUi.of(it) is KnobUi && !(card.drawing == Drawing.ALGORITHM && it == "fm.algorithm") }
  val choices = card.params.count { ParamUi.of(it) is ChoiceUi }
  val columns = when {
    card.columns > 0 -> card.columns
    knobs <= 2 -> 2
    knobs <= 4 -> if (card.on != null) 4 else 2
    knobs <= 6 -> 3
    else -> 4
  }
  var w = 16f + columns * 58f
  if (choices > 0) w = maxOf(w, 170f)
  return when (card.drawing) {
    Drawing.ALGORITHM -> 440f
    Drawing.CONTROLLERS -> 280f
    Drawing.ARP -> 640f
    Drawing.FM_GLOBAL -> maxOf(w, 320f)
    Drawing.HPF -> maxOf(w, 120f)
    else -> w
  }
}

/**
 * One card: its title (and an effect's switch) in the family's colour, a live drawing, then the controls.
 * The controls take the size the card's height allows; the drawing gets whatever is left.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardView(card: Card, state: SynthState, onStep: () -> Unit, modifier: Modifier = Modifier, onOperator: (Int) -> Unit = {}) {
  val shape = RoundedCornerShape(12.dp)
  Column(modifier.background(Hues.cardFill(card.hue), shape).border(1.dp, Hues.cardLine(card.hue), shape)) {
    Row(Modifier.fillMaxWidth().height(28.dp).padding(start = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
      Text(card.title, fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, color = card.hue, maxLines = 1)
      if (card.sub.isNotEmpty()) Text(card.sub, fontSize = 9.sp, lineHeight = 11.sp, color = VoltColors.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
      Box(Modifier.weight(1f))
      card.on?.let { ParamToggle(it, state, onStep, height = 22.dp, label = "Ligado") }
    }
    val params = card.params.map { state.params[it]!! }
    val choices = params.filter { ParamUi.of(it.name) is ChoiceUi }.map { it.name }
    val toggles = params.filter { ParamUi.of(it.name) is ToggleUi }.map { it.name }
    val knobs = params.filter { ParamUi.of(it.name) is KnobUi && !(card.drawing == Drawing.ALGORITHM && it.name == "fm.algorithm") }.map { it.name }

    if (card.drawing == Drawing.ALGORITHM) {
      Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) { CardDrawing(card, state, onStep, onOperator, Modifier.fillMaxSize()) }
      return@Column
    }
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 6.dp)) {
      val w = maxWidth.value; val h = maxHeight.value
      if (isController(card)) {
        Column(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxWidth().height(24.dp)) { CardDrawing(card, state, onStep, onOperator, Modifier.fillMaxSize()) }
          ControllersBody(card, state, onStep, Modifier.weight(1f).fillMaxWidth())
        }
        return@BoxWithConstraints
      }
      val waveSelectors = choices.count { waveIconsOf(it) != null }
      // a lone selector and one or two knobs (the chorus) share a row instead of taking a row each
      val inline = card.on != null && choices.size == 1 && knobs.size in 1..2 && toggles.isEmpty()
      val selectorsH = if (inline) 0f else waveSelectors * 34f + (choices.size - waveSelectors) * 42f
      val toggleRows = if (toggles.isEmpty()) 0 else ceil(toggles.size * 92f / w).toInt().coerceAtLeast(1)
      val togglesH = toggleRows * 32f
      val knobArea = (h - selectorsH - togglesH - minDrawing(card)).coerceAtLeast(0f)
      val fit = KnobGrid.fitCompact(knobs.size, if (inline) w * 0.4f else w, knobArea, if (inline) knobs.size else card.columns)
      val size = fit.knob.coerceIn(KnobGrid.COMPACT_MIN, KnobGrid.COMPACT_MAX)
      val columns = if (knobs.isEmpty()) 1 else fit.columns
      val cell = (if (inline) w * 0.4f else w) / columns  // each knob gets a whole cell, so labels have room and a full row never squeezes its last knob
      val knobSize = minOf(size, cell - 8f)
      val rows = if (knobs.isEmpty()) 0 else ceil(knobs.size / columns.toFloat()).toInt()
      val knobsH = rows * (knobSize + KnobGrid.COMPACT_LABEL + KnobGrid.COMPACT_GAP)
      val drawingH = (h - selectorsH - togglesH - knobsH).coerceAtLeast(0f)
      val needScroll = drawingH <= 0f && h < selectorsH + togglesH + knobsH - 1f
      Column(Modifier.fillMaxSize().let { if (needScroll) it.verticalScroll(rememberScrollState()) else it }, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (card.drawing != Drawing.NONE && drawingH >= 22f) {
          Box(Modifier.fillMaxWidth().height(drawingH.dp)) { CardDrawing(card, state, onStep, onOperator, Modifier.fillMaxSize()) }
        }
        if (inline) {
          Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ParamSelector(choices[0], state, onStep, Modifier.weight(1f))
            knobs.forEach { ParamKnob(it, state, knobSize.dp, onStep, minWidth = cell.dp) }
          }
        } else choices.forEach { ParamSelector(it, state, onStep, Modifier.padding(top = 2.dp)) }
        if (knobs.isNotEmpty() && !inline) {
          Column(Modifier.fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(KnobGrid.COMPACT_GAP.dp)) {
            knobs.chunked(columns).forEach { row ->
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { ParamKnob(it, state, knobSize.dp, onStep, minWidth = cell.dp) }
                repeat(columns - row.size) { Box(Modifier.width(cell.dp).height(1.dp)) }
              }
            }
          }
        }
        if (toggles.isNotEmpty()) {
          FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            toggles.forEach { ParamToggle(it, state, onStep) }
          }
        }
      }
    }
  }
}
