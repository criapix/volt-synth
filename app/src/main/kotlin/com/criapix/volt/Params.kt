package com.criapix.volt

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Curve { LIN, EXP, STEP }

/** One engine parameter, as described by the engine's own table. */
data class Param(val id: Int, val name: String, val min: Float, val max: Float, val def: Float, val curve: Curve) {
  fun toReal(norm: Float): Float {
    val n = norm.coerceIn(0f, 1f)
    return when (curve) {
      Curve.EXP -> min * (max / min).pow(n)
      Curve.STEP -> (min + (max - min) * n).roundToInt().toFloat()
      Curve.LIN -> min + (max - min) * n
    }
  }

  fun toNorm(real: Float): Float {
    val v = real.coerceIn(min, max)
    return if (curve == Curve.EXP) ln(v / min) / ln(max / min) else (v - min) / (max - min)
  }
}

class Params private constructor(val all: List<Param>) {
  private val byName = all.associateBy { it.name }
  operator fun get(name: String): Param? = byName[name]

  companion object {
    /** Parses the text of [Native.paramTable]: one `name|min|max|default|curve` line per parameter. */
    fun parse(table: String): Params = Params(
      table.lineSequence().filter { it.isNotBlank() }.mapIndexed { id, line ->
        val f = line.split('|')
        Param(id, f[0], f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), Curve.valueOf(f[4].uppercase()))
      }.toList()
    )
  }
}
