package com.criapix.volt.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

private const val START_DEG = 135f
private const val SWEEP_DEG = 270f
private val HOT = Color(0xFFFF4B3A)
private val GHOST = Color.White

/**
 * One knob: body, value arc, then the feedback of [fx] (light, ghost pointer, ring, halo, pulse, inner picture).
 * [norm] is the value 0..1; [bipolar] draws the arc from the middle; [active] (a finger on it) whitens the arc.
 */
fun DrawScope.drawKnob(norm: Float, bipolar: Boolean, hue: Color, fx: Fx, active: Boolean) {
  val c = if (fx.hot > 0f) Hues.mix(hue, HOT, fx.hot) else hue
  val stroke = size.minDimension * 0.09f
  val r = size.minDimension / 2f - stroke
  val glow = fx.glow.coerceIn(0f, 1f)
  val jx = if (fx.jit > 0f) (Random.nextFloat() - 0.5f) * fx.jit * stroke * 1.5f else 0f
  val jy = if (fx.jit > 0f) (Random.nextFloat() - 0.5f) * fx.jit * stroke * 1.5f else 0f
  val center = this.center

  if (fx.halo > 0.01f) {
    drawCircle(
      Brush.radialGradient(
        0f to Hues.reverb.copy(alpha = 0f), 0.75f to Hues.reverb.copy(alpha = 0.4f * fx.halo), 1f to Hues.reverb.copy(alpha = 0f),
        center = center, radius = r + stroke,
      ),
      radius = r + stroke, center = center,
    )
  }
  if (glow > 0.01f) {
    drawCircle(
      Brush.radialGradient(
        0f to c.copy(alpha = 0.06f * glow), 0.7f to c.copy(alpha = 0.45f * glow), 1f to c.copy(alpha = 0f),
        center = center, radius = r + stroke,
      ),
      radius = r + stroke, center = center,
    )
  }
  drawCircle(Hues.mix(Hues.mix(VoltColors.raised, c, 0.08f), c, glow * 0.25f), radius = r - stroke, center = Offset(center.x + jx, center.y + jy))

  val arcSize = Size(r * 2, r * 2)
  val topLeft = Offset(center.x - r, center.y - r)
  drawArc(VoltColors.track, START_DEG, SWEEP_DEG, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
  val from = if (bipolar) 0.5f else 0f
  drawArc(
    if (active) VoltColors.text else c,
    START_DEG + SWEEP_DEG * minOf(from, norm), SWEEP_DEG * kotlin.math.abs(norm - from) + 0.01f,
    false, topLeft, arcSize, style = Stroke(stroke * 1.1f, cap = StrokeCap.Round),
  )
  if (fx.ring > 0.02f) drawCircle(c.copy(alpha = fx.ring.coerceIn(0f, 1f)), radius = r + stroke * 0.5f, center = center, style = Stroke(stroke * 0.4f))
  if (!fx.ghost.isNaN() && kotlin.math.abs(fx.ghost - norm) > 0.02f) {  // a ghost on top of the value says nothing
    val g = fx.ghost.coerceIn(0f, 1f)
    drawArc(
      GHOST.copy(alpha = 0.4f), START_DEG + SWEEP_DEG * minOf(norm, g), SWEEP_DEG * kotlin.math.abs(g - norm) + 0.01f,
      false, topLeft, arcSize, style = Stroke(stroke * 0.5f, cap = StrokeCap.Round),
    )
    val a = Math.toRadians((START_DEG + SWEEP_DEG * g).toDouble())
    val tip = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
    drawCircle(c.copy(alpha = 0.5f), radius = stroke * 1.5f, center = tip)
    drawCircle(GHOST, radius = stroke * 0.85f, center = tip)
  }
  if (fx.inner != Inner.NONE) drawGlyph(fx, c, Offset(center.x + jx, center.y + jy), r * 0.5f)
  else {
    val a = Math.toRadians((START_DEG + SWEEP_DEG * norm).toDouble())
    val pointer = Hues.mix(VoltColors.text, c, glow * 0.5f)
    drawLine(
      pointer, Offset(center.x + jx + r * 0.22f * cos(a).toFloat(), center.y + jy + r * 0.22f * sin(a).toFloat()),
      Offset(center.x + jx + (r - stroke * 1.6f) * cos(a).toFloat(), center.y + jy + (r - stroke * 1.6f) * sin(a).toFloat()),
      stroke * 0.6f, StrokeCap.Round,
    )
  }
  if (!fx.pulse.isNaN()) drawCircle(c.copy(alpha = (0.25f + 0.75f * fx.pulse).coerceIn(0f, 1f)), radius = stroke * 0.7f, center = Offset(center.x, center.y + r * 0.62f))
}

/** The small picture in the middle of a knob. [r] is its half width. */
private fun DrawScope.drawGlyph(fx: Fx, c: Color, at: Offset, r: Float) {
  val w = (size.minDimension * 0.026f).coerceAtLeast(1f)
  val col = c.copy(alpha = fx.alpha.coerceIn(0f, 1f))
  when (fx.inner) {
    Inner.WAVE, Inner.SQUARE -> {
      val path = Path()
      for (i in 0..40) {
        val x = at.x - r + i / 40f * 2 * r
        val ph = i / 40f * Waves.TAU * fx.cycles + fx.phase
        val v = if (fx.inner == Inner.SQUARE) sign(sin(ph)) else sin(ph)
        val y = at.y - v * r * 0.42f * fx.amplitude
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      drawPath(path, col, style = Stroke(w))
    }
    Inner.PULSE -> {
      val path = Path()
      for (k in 0 until 2) {
        val x0 = at.x - r + k * r
        if (k == 0) path.moveTo(x0, at.y + r * 0.4f) else path.lineTo(x0, at.y + r * 0.4f)
        path.lineTo(x0, at.y - r * 0.4f); path.lineTo(x0 + r * fx.pw, at.y - r * 0.4f); path.lineTo(x0 + r * fx.pw, at.y + r * 0.4f)
      }
      path.lineTo(at.x + r, at.y + r * 0.4f)
      drawPath(path, col, style = Stroke(w))
    }
    Inner.GRAIN -> repeat(fx.count) {
      val a = Random.nextFloat() * (2 * PI).toFloat()
      val d = kotlin.math.sqrt(Random.nextFloat()) * r * 0.8f
      drawCircle(c.copy(alpha = 0.85f), radius = w * 0.6f, center = Offset(at.x + cos(a) * d, at.y + sin(a) * d))
    }
    Inner.FAN -> for (i in 0 until fx.count.coerceAtLeast(1)) {
      val spread = if (fx.count > 1) i / (fx.count - 1f) - 0.5f else 0f
      val a = -PI.toFloat() / 2 + spread * fx.spread * 2f
      drawLine(col, Offset(at.x, at.y + r * 0.5f), Offset(at.x + cos(a) * r * 0.85f, at.y + r * 0.5f + sin(a) * r * 0.95f), w)
    }
    Inner.SPREAD -> for (d in -1..1) drawCircle(col, radius = w * 1.1f, center = Offset(at.x + d * fx.spread * r * 0.8f, at.y))
    Inner.NONE -> {}
  }
}
