package com.criapix.volt.ui

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

object KnobMath {
  const val FINE = 10f

  /**
   * New normalized value after dragging (dx, dy) pixels from [start]. Up = more. [travel] pixels
   * of vertical drag cover the whole range; horizontal drag is [FINE] times slower.
   * [steps] > 0 snaps to that many intervals (a step parameter with steps+1 values).
   */
  fun drag(start: Float, dx: Float, dy: Float, travel: Float, steps: Int): Float {
    val raw = (start - dy / travel + dx / (FINE * travel)).coerceIn(0f, 1f)
    return if (steps > 0) (raw * steps).roundToInt().toFloat() / steps else raw
  }
}

object KnobGrid {
  const val MAX = 76f       // dp
  const val MIN = 52f       // dp; below this the group scrolls instead
  const val LABEL = 30f     // dp under each knob: label line + small value line
  const val GAP = 8f

  data class Fit(val columns: Int, val knob: Float)

  /** Columns and knob size (dp) for [n] knobs in a [width] x [height] dp area. */
  fun fit(n: Int, width: Float, height: Float): Fit {
    if (n <= 0) return Fit(1, MAX)
    var best = Fit(1, Float.NEGATIVE_INFINITY)
    for (c in 1..n) {
      val rows = ceil(n / c.toFloat())
      val size = min(min(width / c - GAP, height / rows - LABEL - GAP), MAX)
      if (size > best.knob) best = Fit(c, size)
    }
    return best
  }
}
