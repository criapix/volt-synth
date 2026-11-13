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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
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

/**
 * The gesture and the picture of a knob, whatever it is bound to. [norm] is its position 0..1; a drag starts from
 * [startNorm] and reports through [onSet]; [onStart] runs when a finger goes down (a macro takes a snapshot of what
 * it moves); a double tap calls [onReset]. [feedback] fills a [Fx] each frame with what the control does to the sound
 * (null: a plain knob). The knob repaints every frame without recomposing.
 */
@Composable
fun KnobCore(
  norm: Float, label: String, text: String, size: Dp, steps: Int,
  startNorm: () -> Float, onSet: (Float) -> Unit, onStep: () -> Unit,
  modifier: Modifier = Modifier, minWidth: Dp = 0.dp, bipolar: Boolean = false, hue: Color = VoltColors.accent,
  feedback: ((Fx) -> Unit)? = null, onStart: () -> Unit = {}, onReset: () -> Unit = {},
) {
  var active by remember { mutableStateOf(false) }
  var lastUp by remember { mutableLongStateOf(0L) }
  val travel = with(LocalDensity.current) { 200.dp.toPx() }
  val frame = LocalFrame.current
  val fx = remember { Fx() }
  val currentSet by rememberUpdatedState(onSet)
  val currentStart by rememberUpdatedState(onStart)
  val currentReset by rememberUpdatedState(onReset)
  val currentStartNorm by rememberUpdatedState(startNorm)
  val currentStep by rememberUpdatedState(onStep)

  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.width(maxOf(size + 8.dp, minWidth))) {
    Box(contentAlignment = Alignment.Center) {
      Canvas(
        Modifier.size(size).pointerInput(travel, steps) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val doubleTap = down.uptimeMillis - lastUp < 300
            currentStart()
            val start = currentStartNorm()
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
                if (steps > 0) currentStep()
                shown = n
                currentSet(n)
              }
            }
            if (doubleTap && moved < 12f) { currentReset(); currentStep() }
            active = false
          }
        },
      ) {
        frame?.value  // reading it here repaints this canvas every frame
        fx.reset()
        feedback?.invoke(fx)
        drawKnob(norm, bipolar, hue, fx, active)
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
    Text(
      label.uppercase(), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp,
      color = VoltColors.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
    )
    Text(text, fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace, color = VoltColors.text, maxLines = 1, textAlign = TextAlign.Center)
  }
}

/** A knob bound to one engine parameter. [hue] is its family's colour; [feedback] says what it does to the sound right now. */
@Composable
fun Knob(
  param: Param, ui: KnobUi, state: SynthState, size: Dp, onStep: () -> Unit, minWidth: Dp = 0.dp,
  hue: Color = VoltColors.accent, feedback: ((Fx) -> Unit)? = null,
) {
  val norm by state.value(param.id).collectAsStateWithLifecycle()
  KnobCore(
    norm = norm, label = ui.label, text = ParamUi.format(param, param.toReal(norm)), size = size,
    steps = if (param.curve == Curve.STEP) (param.max - param.min).toInt() else 0,
    startNorm = { state.norm(param.id) }, onSet = { state.set(param.id, it) }, onStep = onStep,
    minWidth = minWidth, bipolar = ui.bipolar, hue = hue, feedback = feedback,
    onReset = { state.set(param.id, param.toNorm(param.def)) },
  )
}
