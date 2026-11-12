package com.criapix.volt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.Curve
import com.criapix.volt.Param
import com.criapix.volt.state.SynthState
import kotlin.math.cos
import kotlin.math.sin

private const val START_DEG = 135f
private const val SWEEP_DEG = 270f

@Composable
fun Knob(param: Param, ui: KnobUi, state: SynthState, size: Dp, onStep: () -> Unit) {
  val norm by state.value(param.id).collectAsStateWithLifecycle()
  var active by remember { mutableStateOf(false) }
  var lastUp by remember { mutableLongStateOf(0L) }
  val travel = with(LocalDensity.current) { 200.dp.toPx() }
  val steps = if (param.curve == Curve.STEP) (param.max - param.min).toInt() else 0
  val text = ParamUi.format(param, param.toReal(norm))

  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 8.dp)) {
    Box(contentAlignment = Alignment.Center) {
      Canvas(
        Modifier.size(size).pointerInput(param.id, travel) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val doubleTap = down.uptimeMillis - lastUp < 300
            val start = state.norm(param.id)
            var dx = 0f
            var dy = 0f
            var moved = 0f
            var shown = start
            active = true
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: break
              if (!change.pressed) { lastUp = change.uptimeMillis; break }
              val d = change.positionChange()
              change.consume()
              dx += d.x; dy += d.y; moved += d.getDistance()
              val n = KnobMath.drag(start, dx, dy, travel, steps)
              if (n != shown) {
                if (steps > 0) onStep()
                shown = n
                state.set(param.id, n)
              }
            }
            if (doubleTap && moved < 12f) { state.set(param.id, param.toNorm(param.def)); onStep() }
            active = false
          }
        },
      ) {
        val stroke = size.toPx() * 0.09f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        drawArc(VoltColors.track, START_DEG, SWEEP_DEG, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val from = if (ui.bipolar) 0.5f else 0f
        drawArc(
          if (active) VoltColors.text else VoltColors.accent,
          START_DEG + SWEEP_DEG * minOf(from, norm), SWEEP_DEG * kotlin.math.abs(norm - from),
          false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val angle = Math.toRadians((START_DEG + SWEEP_DEG * norm).toDouble())
        val r = this.size.minDimension / 2 - stroke * 1.6f
        val c = center
        drawLine(VoltColors.text, c, Offset(c.x + r * cos(angle).toFloat(), c.y + r * sin(angle).toFloat()), stroke * 0.6f, StrokeCap.Round)
      }
      if (active) {
        Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, with(LocalDensity.current) { -(size + 24.dp).roundToPx() })) {
          Text(
            text, color = VoltColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(VoltColors.raised, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
      }
    }
    Text(ui.label, fontSize = 12.sp, color = VoltColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    Text(text, fontSize = 10.sp, color = VoltColors.dim, maxLines = 1, textAlign = TextAlign.Center)
  }
}
