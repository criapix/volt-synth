package com.criapix.volt.ui

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh
import kotlin.random.Random

/**
 * A picture of the waveform the engine is playing, calculated from the parameters and the live model (the engine
 * does not send samples). Analog: three oscillators with sub and noise, soft drive, a two-pole low-pass at the
 * effective cutoff. FM: the six operators through the algorithm. Silent when no note sounds.
 */
object Scope {
  private val ops = FloatArray(7)

  /** Fills [out] with [cycles] periods of the lead note's wave, scaled to about -1..1. */
  fun fill(live: Live, v: Vals, out: FloatArray, cycles: Float) {
    out.fill(0f)
    val lead = live.lead ?: return
    if (lead.fm) fm(live, v, lead, out, cycles) else analog(live, v, out, cycles)
  }

  private fun analog(live: Live, v: Vals, out: FloatArray, cycles: Float) {
    val n = out.size
    fun ratio(i: Int) = 2f.pow(v.real("osc$i.octave") + v.real("osc$i.semi") / 12f + v.real("osc$i.fine") / 1200f)
    val r1 = ratio(1)
    val rat = floatArrayOf(0f, ratio(1), ratio(2), ratio(3))
    val lev = floatArrayOf(0f, v.real("osc1.level"), v.real("osc2.level"), v.real("osc3.level"))
    val wave = intArrayOf(0, v.int("osc1.wave"), v.int("osc2.wave"), v.int("osc3.wave"))
    val pw = floatArrayOf(0f, v.real("osc1.pw"), v.real("osc2.pw"), v.real("osc3.pw"))
    val sub = v.real("mix.sub"); val noise = v.real("mix.noise")
    val fs = live.f0 * r1 * n / cycles
    val fc = min(live.cutoff, fs * 0.45f)
    // a zero-delay-feedback state-variable low-pass: stable at any cutoff, unlike the simple Chamberlin form
    val g = tan(PI.toFloat() * fc / fs)
    val k1 = 2f - 1.9f * v.real("filter.reso")
    val a1 = 1f / (1f + g * (g + k1)); val a2 = g * a1; val a3 = g * a2
    val b1 = 1f / (1f + g * (g + 2f)); val b2 = g * b1; val b3 = g * b2
    val drive = 1f + v.real("mix.drive") * 14f
    val th = tanh(drive)
    val twoPole = v.int("filter.model") != 1
    var ic1 = 0f; var ic2 = 0f; var jc1 = 0f; var jc2 = 0f
    val t = live.t.toFloat()
    for (pass in 0 until 3) for (i in 0 until n) {
      val ph = i / n.toFloat() * cycles
      var x = 0f
      for (j in 1..3) x += lev[j] * Waves.osc(wave[j], ph * rat[j] / r1 + (if (j > 1) t * 0.7f * j else 0f), pw[j])
      x += sub * Waves.osc(1, ph / 2f) + noise * (Random.nextFloat() * 2f - 1f)
      x = tanh(drive * x * 0.5f) / th
      val v3 = x - ic2
      val v1 = a1 * ic1 + a2 * v3
      val v2 = ic2 + a2 * ic1 + a3 * v3
      ic1 = 2f * v1 - ic1; ic2 = 2f * v2 - ic2
      var y = v2
      if (twoPole) {
        val w3 = y - jc2
        val w1 = b1 * jc1 + b2 * w3
        val w2 = jc2 + b2 * jc1 + b3 * w3
        jc1 = 2f * w1 - jc1; jc2 = 2f * w2 - jc2
        y = w2
      }
      if (pass == 2) out[i] = (if (y.isFinite()) y else 0f).coerceIn(-3f, 3f) * live.amp * 0.8f
    }
  }

  private fun fm(live: Live, v: Vals, lead: LiveVoice, out: FloatArray, cycles: Float) {
    val al = live.algo ?: return
    val n = out.size
    val rat = FloatArray(7)
    for (o in 1..6) {
      val fixed = v.on("fm.op$o.mode")
      val coarse = v.int("fm.op$o.coarse"); val fine = v.int("fm.op$o.fine")
      rat[o] = if (fixed) (FmFrequency.fixedHz(coarse, fine) / lead.freq).toFloat() else FmFrequency.ratio(coarse, fine).toFloat()
    }
    val fbAmount = v.real("fm.feedback") / 7f * 1.6f
    var prev = 0f; var prev2 = 0f
    val scale = 1f / sqrt(max(1, al.carriers.size).toFloat())
    for (i in 0 until n) {
      val ph = i / n.toFloat() * cycles
      for (op in 6 downTo 1) {
        var m = 0f
        al.modulators[op]?.forEach { m += ops[it] * 3f }
        if (op == al.feedback) m += (prev + prev2) * 0.5f * fbAmount
        ops[op] = lead.op[op] * sin(Waves.TAU * ph * rat[op] + m)
        if (op == al.feedbackFrom) { prev2 = prev; prev = ops[op] }
      }
      var s = 0f
      for (c in al.carriers) s += ops[c]
      out[i] = s * scale
    }
  }
}
