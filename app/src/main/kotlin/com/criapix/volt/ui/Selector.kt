package com.criapix.volt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.Param
import com.criapix.volt.state.SynthState
import kotlin.math.roundToInt

/** Which waves the options of a selector draw instead of text (the selected one moves with the sound). */
enum class WaveIcons { OSC, LFO1, LFO2, FM_LFO }

/** Segmented buttons for a choice parameter (wave shape, filter model, voice mode). */
@Composable
fun Selector(
  param: Param, ui: ChoiceUi, state: SynthState, onStep: () -> Unit,
  height: Dp = 34.dp, fontSize: TextUnit = 12.sp,
  hue: Color = VoltColors.accent, waves: WaveIcons? = null, showLabel: Boolean = true,
) {
  val norm by state.value(param.id).collectAsStateWithLifecycle()
  val selected = (param.toReal(norm) - param.min).roundToInt()
  val frame = LocalFrame.current
  val live = LocalLive.current
  Column {
    if (showLabel) Text(ui.label.uppercase(), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = VoltColors.dim)
    Row(Modifier.fillMaxWidth().height(height).background(VoltColors.background, RoundedCornerShape(8.dp)).border(1.dp, VoltColors.raised, RoundedCornerShape(8.dp)).padding(2.dp)) {
      ui.options.forEachIndexed { i, option ->
        val on = i == selected
        Box(
          Modifier.weight(1f).height(height - 4.dp)
            .background(if (on) hue else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { if (!on) { state.set(param.id, param.toNorm(param.min + i)); onStep() } },
          contentAlignment = Alignment.Center,
        ) {
          if (waves != null) {
            Canvas(Modifier.size(width = 26.dp, height = 16.dp)) {
              frame?.value
              val phase = if (!on || live == null) 0f else when (waves) {
                WaveIcons.OSC -> (live.t * (0.8 + live.amp * 0.6)).toFloat()
                WaveIcons.LFO1 -> live.lfoPhase1
                WaveIcons.LFO2 -> live.lfoPhase2
                WaveIcons.FM_LFO -> live.fmLfoPhase
              }
              val path = Path()
              val steps = 26
              for (k in 0..steps) {
                val x = k / steps.toFloat() * size.width
                val ph = k / steps.toFloat() * (if (waves == WaveIcons.OSC) 2f else 1.5f) + phase
                val v = when (waves) {
                  WaveIcons.OSC -> Waves.osc(i, ph, 0.35f)
                  WaveIcons.FM_LFO -> Waves.fmLfo(i, ph)
                  else -> Waves.lfo(i, ph)
                }
                val y = size.height / 2 - v * size.height * 0.36f
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
              }
              drawPath(path, if (on) Color(0xFF0B0C11) else VoltColors.dim, style = Stroke(if (on) 1.7.dp.toPx() else 1.1.dp.toPx()))
            }
          } else {
            Text(
              option, fontSize = fontSize, lineHeight = fontSize * 1.25f, maxLines = 1, overflow = TextOverflow.Clip, fontWeight = FontWeight.Bold,
              color = if (on) Color(0xFF0B0C11) else VoltColors.dim,
            )
          }
        }
      }
    }
  }
}

/**
 * An on/off chip for a 0..1 step parameter, with a small light. [led] (0..1) says how much the thing it switches is
 * working now, so the light breathes with the sound; without it the light is simply on or off.
 */
@Composable
fun Toggle(
  param: Param, ui: ToggleUi, state: SynthState, onStep: () -> Unit, height: Dp = 30.dp, fontSize: TextUnit = 12.sp,
  hue: Color = VoltColors.accent, led: ((Live) -> Float)? = null, label: String = ui.label,
) {
  val norm by state.value(param.id).collectAsStateWithLifecycle()
  val on = norm >= 0.5f
  val frame = LocalFrame.current
  val live = LocalLive.current
  Row(
    Modifier.height(height)
      .background(VoltColors.background, RoundedCornerShape(8.dp))
      .border(1.dp, if (on) hue else VoltColors.raised, RoundedCornerShape(8.dp))
      .clickable { state.set(param.id, if (on) 0f else 1f); onStep() }
      .padding(horizontal = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Canvas(Modifier.size(9.dp)) {
      frame?.value
      val level = if (on) (if (led != null && live != null) led(live).coerceIn(0f, 1f) else 1f) else 0f
      val col = if (on) Hues.mix(VoltColors.raised, hue, 0.35f + 0.65f * level) else VoltColors.raised
      drawCircle(col, radius = size.minDimension / 2)
    }
    Text(
      label.uppercase(), fontSize = fontSize, lineHeight = fontSize * 1.25f, maxLines = 1, softWrap = false, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
      color = if (on) hue else VoltColors.dim,
    )
  }
}
