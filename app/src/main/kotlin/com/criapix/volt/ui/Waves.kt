package com.criapix.volt.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** The wave shapes the screen draws, evaluated at a phase in cycles (any real number). */
object Waves {
  const val TAU = (2 * PI).toFloat()

  /** A repeatable "random" value in -1..1 for an integer step (the sample-and-hold waves). */
  fun hash(i: Int): Float {
    val x = sin(i * 127.1 + 311.7) * 43758.5453
    return ((x - floor(x)) * 2 - 1).toFloat()
  }

  private fun frac(ph: Float) = ph - floor(ph)

  /** Oscillator waves: 0 saw, 1 pulse (width [pw]), 2 triangle, 3 sine. */
  fun osc(w: Int, ph: Float, pw: Float = 0.5f): Float {
    val p = frac(ph)
    return when (w) {
      0 -> 2 * p - 1
      1 -> if (p < pw) 1f else -1f
      2 -> 1 - 4 * abs(p - 0.5f)
      else -> sin(TAU * p)
    }
  }

  /** Analog LFO waves: 0 sine, 1 triangle, 2 saw, 3 square, 4 sample and hold. */
  fun lfo(w: Int, ph: Float): Float {
    val p = frac(ph)
    return when (w) {
      0 -> sin(TAU * p)
      1 -> 1 - 4 * abs(p - 0.5f)
      2 -> 2 * p - 1
      3 -> if (p < 0.5f) 1f else -1f
      else -> hash(floor(ph).toInt())
    }
  }

  /** DX7 LFO waves: 0 triangle, 1 saw down, 2 saw up, 3 square, 4 sine, 5 sample and hold. */
  fun fmLfo(w: Int, ph: Float): Float {
    val p = frac(ph)
    return when (w) {
      0 -> 1 - 4 * abs(p - 0.5f)
      1 -> 1 - 2 * p
      2 -> 2 * p - 1
      3 -> if (p < 0.5f) 1f else -1f
      4 -> sin(TAU * p)
      else -> hash(floor(ph).toInt())
    }
  }
}
