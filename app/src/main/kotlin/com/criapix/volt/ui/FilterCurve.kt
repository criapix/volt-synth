package com.criapix.volt.ui

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** Approximate magnitude response of the filter section, for drawing only. */
object FilterCurve {
  /** Resonance 0..1 -> Q of the resonant 2-pole section. */
  fun q(reso: Float) = 0.5f + 9.5f * reso * reso

  private fun twoPole(x: Float, q: Float): Float {
    val re = 1f - x * x
    val im = x / q
    return 1f / sqrt(re * re + im * im)
  }

  /** Gain in dB at [f] Hz. model: 0 Ladder, 1 Roland 12, 2 Roland 24, 3 Prophet; [hpf] is the 1-pole high-pass cutoff. */
  fun magnitudeDb(f: Float, cutoff: Float, reso: Float, model: Int, hpf: Float): Float {
    val x = f / cutoff
    var g = twoPole(x, q(reso))
    if (model != 1) g *= twoPole(x, 0.5f)  // the 24 dB models add a second, non-resonant section
    val xh = f / hpf
    g *= xh / sqrt(1f + xh * xh)
    return 20f * log10(g.coerceAtLeast(1e-6f))
  }

  /** [n] gains in dB at log-spaced frequencies from [fMin] to [fMax]. */
  fun points(n: Int, cutoff: Float, reso: Float, model: Int, hpf: Float, fMin: Float = 20f, fMax: Float = 20000f): FloatArray =
    FloatArray(n) { i -> magnitudeDb(fMin * (fMax / fMin).pow(i / (n - 1f)), cutoff, reso, model, hpf) }
}
