package com.criapix.volt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.criapix.volt.state.SynthState

/** Reads parameters while drawing (real values, by name). */
class Vals(private val state: SynthState) {
  fun real(name: String): Float { val p = state.params[name]!!; return p.toReal(state.norm(p.id)) }
  fun norm(name: String): Float = state.norm(state.params[name]!!.id)
  fun int(name: String): Int = real(name).toInt()
  fun on(name: String): Boolean = real(name) >= 0.5f
}

/** True inside the small pictures of the family strip, which leave out their text labels. */
val LocalMiniDrawing = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Set around each drawing (they are drawn one after the other on the UI thread) so that [label] knows whether to write. */
object LabelPolicy { var show = true }

/** A canvas that repaints every frame with the live model and the parameter values at hand. Draws nothing without a model. */
@Composable
fun LiveCanvas(state: SynthState, modifier: Modifier = Modifier, onDraw: DrawScope.(Live, Vals, TextMeasurer) -> Unit) {
  val live = LocalLive.current
  val frame = LocalFrame.current
  val vals = remember(state) { Vals(state) }
  val measurer = rememberTextMeasurer(cacheSize = 48)
  val mini = LocalMiniDrawing.current
  Canvas(modifier) {
    frame?.value
    if (live != null) {
      LabelPolicy.show = !mini
      try { onDraw(live, vals, measurer) } finally { LabelPolicy.show = true }
    }
  }
}

/** A small label at ([x], [y]) (its top left). */
fun DrawScope.label(m: TextMeasurer, s: String, x: Float, y: Float, color: Color, size: Float = 9f, bold: Boolean = false, mono: Boolean = false, right: Boolean = false) {
  if (!LabelPolicy.show) return
  val style = TextStyle(
    fontSize = size.sp, lineHeight = (size + 3f).sp, color = color,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
  )
  val r = m.measure(s, style)
  drawText(r, topLeft = Offset(if (right) x - r.size.width else x, y))
}
