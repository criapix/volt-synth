package com.criapix.volt.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Layout of a DX7 envelope (four rates, four levels) in fractions of the drawing's width (x) and of the level
 * (0..1 = value / 99). Each of the three rising/falling segments and the release takes `MIN .. SEG` of the width:
 * rate 99 is the shortest, rate 0 the longest. The key-down plateau always takes [HOLD]. The total never exceeds 1.
 */
object Dx7Envelope {
  const val SEG = 0.22f
  const val HOLD = 0.12f
  const val MIN = 0.03f

  data class Pt(val x: Float, val level: Float)

  /** Index of the first point that is a handle's: H1, H2, H3 (indexes 1..3) and H4, the end of the release (index 5). */
  val HANDLE_POINTS = intArrayOf(1, 2, 3, 5)

  fun width(rate: Int): Float = MIN + (SEG - MIN) * (1f - rate.coerceIn(0, 99) / 99f)

  /** Six points: start at L4, L1, L2, L3, the end of the plateau at L3, the end of the release at L4. */
  fun points(rates: IntArray, levels: IntArray): List<Pt> {
    require(rates.size == 4 && levels.size == 4)
    val l = FloatArray(4) { levels[it].coerceIn(0, 99) / 99f }
    val x1 = width(rates[0])
    val x2 = x1 + width(rates[1])
    val x3 = x2 + width(rates[2])
    val x4 = x3 + HOLD
    val x5 = x4 + width(rates[3])
    return listOf(Pt(0f, l[3]), Pt(x1, l[0]), Pt(x2, l[1]), Pt(x3, l[2]), Pt(x4, l[2]), Pt(x5, l[3]))
  }

  /** The four handle positions of [points]: H1, H2, H3 and H4 (the release end). */
  fun handles(points: List<Pt>): List<Pt> = HANDLE_POINTS.map { points[it] }

  /** Index 0..3 of the handle closest to (x, level). */
  fun nearest(points: List<Pt>, x: Float, level: Float): Int {
    val h = handles(points)
    return h.indices.minBy { abs(x - h[it].x) + abs(level - h[it].level) }
  }

  /**
   * The (rate, level) of a handle after dragging it by [dxFraction] of the width (right = positive: a longer
   * segment, so a lower rate) and [dyFraction] of the height (down = positive, as on the screen: a lower level).
   */
  fun drag(startRate: Int, startLevel: Int, dxFraction: Float, dyFraction: Float): Pair<Int, Int> {
    val rate = (startRate - dxFraction / (SEG - MIN) * 99f).roundToInt().coerceIn(0, 99)
    val level = (startLevel - dyFraction * 99f).roundToInt().coerceIn(0, 99)
    return rate to level
  }
}
