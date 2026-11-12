package com.criapix.volt.ui

import kotlin.math.abs

/**
 * Layout of the ADSR drawing in fractions of its width (x) and of the level (0 = silent, 1 = full).
 * Attack, decay and release each take up to [SEG] of the width, in proportion to their normalized
 * value (already logarithmic for times); the sustain plateau always takes [HOLD].
 */
object EnvelopeGeometry {
  const val SEG = 0.28f
  const val HOLD = 0.16f

  data class Points(val attackX: Float, val decayX: Float, val sustain: Float, val holdEndX: Float, val releaseX: Float)
  enum class Handle { ATTACK, DECAY_SUSTAIN, RELEASE }

  fun points(attack: Float, decay: Float, sustain: Float, release: Float): Points {
    val a = attack * SEG
    val d = a + decay * SEG
    val h = d + HOLD
    return Points(a, d, sustain, h, h + release * SEG)
  }

  /** Handle closest to (x, level). */
  fun nearest(p: Points, x: Float, level: Float): Handle {
    val da = abs(x - p.attackX) + abs(level - 1f)
    val dd = abs(x - p.decayX) + abs(level - p.sustain)
    val dr = abs(x - p.releaseX) + abs(level)
    return when (minOf(da, dd, dr)) { da -> Handle.ATTACK; dd -> Handle.DECAY_SUSTAIN; else -> Handle.RELEASE }
  }

  /** A time parameter after dragging its handle by [dxFraction] of the width. */
  fun time(start: Float, dxFraction: Float) = (start + dxFraction / SEG).coerceIn(0f, 1f)

  /** Sustain after dragging by [dyFraction] of the height (down is positive, so it lowers the level). */
  fun level(start: Float, dyFraction: Float) = (start - dyFraction).coerceIn(0f, 1f)
}
