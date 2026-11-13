package com.criapix.volt.ui

import java.util.Locale
import kotlin.math.pow

/**
 * The frequency of an FM operator as the DX7 shows it. Verified against msfa's `osc_freq` (dx7note.cpp):
 * ratio mode adds `coarsemul[coarse]` (log2 of 0.5 for coarse 0, of `coarse` otherwise) plus `log2(1 + fine/100)`;
 * fixed mode is `(coarse & 3) * 100 + fine` hundredths of a decade above 1 Hz (logfreq 0 = 1 Hz). The detune
 * is not part of the display (in fixed mode msfa only uses it above +7, which the engine never sends).
 */
object FmFrequency {
  private val BR: Locale = Locale.forLanguageTag("pt-BR")

  fun ratio(coarse: Int, fine: Int): Double = (if (coarse == 0) 0.5 else coarse.toDouble()) * (1.0 + fine / 100.0)

  fun fixedHz(coarse: Int, fine: Int): Double = 10.0.pow((coarse and 3) + fine / 100.0)

  /** "×1,41" in ratio mode, "9772 Hz" in fixed mode. */
  fun format(fixed: Boolean, coarse: Int, fine: Int): String =
    if (fixed) String.format(BR, "%.0f Hz", fixedHz(coarse, fine)) else String.format(BR, "×%.2f", ratio(coarse, fine))
}
