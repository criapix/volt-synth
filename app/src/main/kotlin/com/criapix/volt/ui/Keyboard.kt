package com.criapix.volt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criapix.volt.midi.noteName

/** Geometry of the drawer keyboard: 15 white keys (two octaves and the top C). */
object KeyboardLayout {
  const val WHITES = 15
  private const val BLACK_ZONE = 0.6f   // black keys take the upper 60 %
  private const val BLACK_HALF = 0.3f   // and +-0.3 of a white key around each boundary
  private val STEPS = intArrayOf(0, 2, 4, 5, 7, 9, 11)
  private val SHARP_AFTER = booleanArrayOf(true, true, false, true, true, true, false)

  private fun white(i: Int, base: Int) = base + 12 * (i / 7) + STEPS[i % 7]

  /** The MIDI note under (x, y), or null outside the keyboard. [base] is the first C. */
  fun noteAt(x: Float, y: Float, w: Float, h: Float, base: Int): Int? {
    if (x < 0f || x >= w || y < 0f || y > h) return null
    val kw = w / WHITES
    val i = (x / kw).toInt().coerceAtMost(WHITES - 1)
    val frac = x / kw - i
    if (y < h * BLACK_ZONE) {
      if (frac > 1f - BLACK_HALF && i < WHITES - 1 && SHARP_AFTER[i % 7]) return white(i, base) + 1
      if (frac < BLACK_HALF && i > 0 && SHARP_AFTER[(i - 1) % 7]) return white(i - 1, base) + 1
    }
    return white(i, base)
  }

  /** Touching higher on a key plays softer. */
  fun velocity(y: Float, h: Float): Float = (0.3f + 0.7f * (y / h)).coerceIn(0.3f, 1f)

  /** Left edge (in white-key units) of every black key in the drawing. */
  fun blackKeys(base: Int): List<Pair<Float, Int>> = (0 until WHITES - 1).filter { SHARP_AFTER[it % 7] }
    .map { (it + 1f - BLACK_HALF) to white(it, base) + 1 }
}

/** The keyboard drawer of the PAINEL screen: octave buttons, a close button and the keyboard that lights with the notes. */
@Composable
fun KeyboardDrawer(state: com.criapix.volt.state.SynthState, base: Int, onBase: (Int) -> kotlin.Unit, onClose: () -> kotlin.Unit, modifier: Modifier = Modifier) {
  Column(modifier.background(VoltColors.panel).padding(6.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TextButton(onClick = { if (base > 24) onBase(base - 12) }) { Text("Oitava −") }
      Text("${noteName(base)} – ${noteName(base + 24)}", fontSize = 13.sp, lineHeight = 16.sp)
      TextButton(onClick = { if (base < 84) onBase(base + 12) }) { Text("Oitava +") }
      Text("toque mais embaixo na tecla para tocar mais forte", fontSize = 11.sp, lineHeight = 14.sp, color = VoltColors.dim, modifier = Modifier.weight(1f))
      TextButton(onClick = onClose) { Text("Fechar") }
    }
    PerformanceKeyboard(state, base, dark = false, modifier = Modifier.fillMaxSize())
  }
}
