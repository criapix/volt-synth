package com.criapix.volt.ui

import kotlin.math.exp

/**
 * The shape of the keyboard level scaling drawing (a picture, not msfa's table). Curves are the DX7's four:
 * 0 = -LIN, 1 = -EXP, 2 = +EXP, 3 = +LIN. A negative curve lowers the level away from the break point, a
 * positive one raises it; the depth (0..99) scales the largest change.
 */
object KeyScaling {
  const val NOTES = 99          // the DX7 break point is 0..99 (A-1 to C8)
  private const val EXP_K = 3f

  /** Level change in -1..1 at [distance] (0 at the break point, 1 at the end of the keyboard) on one side. */
  fun change(distance: Float, depth: Int, curve: Int): Float {
    val d = distance.coerceIn(0f, 1f)
    val shape = if (curve == 1 || curve == 2) (exp(EXP_K * d) - 1f) / (exp(EXP_K) - 1f) else d
    val sign = if (curve >= 2) 1f else -1f
    return sign * depth.coerceIn(0, 99) / 99f * shape
  }

  /**
   * The polyline from note 0 to note 99 as (x in 0..1, change in -1..1): the left curve for notes below
   * [breakNote], the right one above it. Always [n] + 1 points, and the break point has change 0.
   */
  fun polyline(breakNote: Int, ldepth: Int, lcurve: Int, rdepth: Int, rcurve: Int, n: Int = NOTES): List<Pair<Float, Float>> {
    val b = breakNote.coerceIn(0, NOTES).toFloat()
    return List(n + 1) { i ->
      val note = NOTES * i / n.toFloat()
      val y = when {
        note < b -> change((b - note) / b, ldepth, lcurve)
        note > b -> change((note - b) / (NOTES - b), rdepth, rcurve)
        else -> 0f
      }
      note / NOTES to y
    }
  }
}
