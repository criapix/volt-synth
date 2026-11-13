package com.criapix.volt.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import com.criapix.volt.state.SynthState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

private fun DrawScope.off(m: TextMeasurer, on: Boolean) {
  if (!on) label(m, "DESLIGADO", 8.dp.toPx(), size.height - 16.dp.toPx(), VoltColors.dim, 9f, bold = true)
}

private fun DrawScope.baseline() = drawLine(VoltColors.raised, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)

/** Distortion: a sine goes in (grey) and comes out flattened (colour). Drive flattens and warms it, tone softens the edges, mix blends the two. */
@Composable
fun DistDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val on = v.on("fx.dist.on"); val drive = v.real("fx.dist.drive"); val mix = v.real("fx.dist.mix"); val tone = v.norm("fx.dist.tone")
    val w = size.width; val h = size.height
    val amp = 0.25f + 0.75f * min(1f, live.amp * 1.2f)
    val k = 1f + drive * 16f; val th = tanh(k)
    val t = live.t.toFloat()
    baseline()
    val n = (w / 2.dp.toPx()).toInt().coerceAtLeast(8)
    val clean = FloatArray(n) { sin(it / (n - 1f) * Waves.TAU * 2f + t * 2f) * amp }
    drawPath(
      Path().apply { clean.forEachIndexed { i, y -> val x = w * i / (n - 1f); if (i == 0) moveTo(x, h / 2 - y * h * 0.32f) else lineTo(x, h / 2 - y * h * 0.32f) } },
      VoltColors.dim.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()),
    )
    val shaped = FloatArray(n) { val s = clean[it]; if (on) s + (tanh(k * s) / th - s) * mix else s }
    val win = ((1f - tone) * 6f).roundToInt()
    val soft = FloatArray(n) { i -> var a = 0f; var c = 0; for (j in max(0, i - win)..min(n - 1, i + win)) { a += shaped[j]; c++ }; a / c }
    val col = Hues.mix(hue, Color(0xFFFFD36B), 1f - drive)
    val act = live.fxActivity[Live.DIST]
    val path = Path().apply { soft.forEachIndexed { i, y -> val x = w * i / (n - 1f); if (i == 0) moveTo(x, h / 2 - y * h * 0.32f) else lineTo(x, h / 2 - y * h * 0.32f) } }
    if (on && act > 0.02f) drawPath(path, col.copy(alpha = 0.2f * act), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, if (on) col else VoltColors.track, style = Stroke((1.5f + 2f * act).dp.toPx(), cap = StrokeCap.Round))
    off(m, on)
  }
}

/** Phaser: a spectrum with two notches that sweep. Rate is the sweep speed, depth its reach, feedback deepens the notches, mix their contrast. */
@Composable
fun PhaserDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val on = v.on("fx.phaser.on"); val depth = v.real("fx.phaser.depth"); val fb = v.real("fx.phaser.feedback"); val mix = v.real("fx.phaser.mix")
    val w = size.width; val h = size.height
    val ph = 0.5f + 0.5f * live.phaserSwing
    val n1 = 0.15f + depth * 0.55f * ph; val n2 = n1 + 0.25f
    val width = 0.08f * (1f - fb * 0.7f) + 0.02f
    val strength = 0.35f + 0.65f * min(1f, live.amp + 0.2f)
    val path = Path().apply {
      moveTo(0f, h)
      var x = 0f
      while (x <= w) {
        val u = x / w
        val base = 0.75f - 0.35f * u + 0.05f * sin(u * 40f)
        val notch = 1f - (if (on) mix else 0f) * (exp(-(((u - n1) / width).pow(2))) + exp(-(((u - n2) / width).pow(2)))) * 0.9f
        lineTo(x, h - (h - 10.dp.toPx()) * base * notch * strength)
        x += 2.dp.toPx()
      }
      lineTo(w, h); close()
    }
    val col = if (on) hue else VoltColors.track
    drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = 0.7f), col.copy(alpha = 0.05f)), 0f, h))
    if (on) for (nn in listOf(n1, n2)) drawRect(Color.White.copy(alpha = 0.4f + 0.6f * live.fxActivity[Live.PHASER]), Offset(nn * w - 1.dp.toPx(), 4.dp.toPx()), Size(2.dp.toPx(), 8.dp.toPx()))
    off(m, on)
  }
}

/** Flanger: a comb of teeth that moves together and apart. Feedback makes the teeth taller, depth moves them further. */
@Composable
fun FlangerDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val on = v.on("fx.flanger.on"); val depth = v.real("fx.flanger.depth"); val gk = v.real("fx.flanger.feedback") * 0.9f; val mix = v.real("fx.flanger.mix")
    val w = size.width; val h = size.height
    val tau = 0.0015f + 0.007f * depth * (0.5f + 0.5f * live.flangerSwing)
    val strength = 0.35f + 0.65f * min(1f, live.amp + 0.2f)
    val path = Path().apply {
      moveTo(0f, h)
      var x = 0f
      while (x <= w) {
        val f = x / w * 8000f
        val re = 1f + gk * cos(Waves.TAU * f * tau); val im = gk * sin(Waves.TAU * f * tau)
        val mag = sqrt(re * re + im * im) / (1f + gk)
        val y = 0.8f + ((if (on) mag else 0.8f) - 0.8f) * (if (on) mix else 0f)
        lineTo(x, h - (h - 10.dp.toPx()) * y * strength)
        x += 1.5.dp.toPx()
      }
      lineTo(w, h); close()
    }
    val col = if (on) hue else VoltColors.track
    drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = 0.75f), col.copy(alpha = 0.05f)), 0f, h))
    off(m, on)
  }
}

/** Chorus: the wave and its detuned copies trembling beside it (mode I slow, II fast, I+II both). */
@Composable
fun ChorusDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val on = v.on("fx.chorus.on"); val mode = v.int("fx.chorus.mode"); val mix = v.real("fx.chorus.mix")
    val w = size.width; val h = size.height
    val amp = 0.3f + 0.7f * min(1f, live.amp * 1.2f)
    val t = live.t.toFloat()
    baseline()
    fun line(off: Float, a: Float, width: Float, col: Color) {
      val p = Path()
      var x = 0f
      while (x <= w) {
        val y = h / 2 - sin(x / w * Waves.TAU * 2f + t * 2f + off) * h * 0.3f * amp
        if (x == 0f) p.moveTo(x, y) else p.lineTo(x, y)
        x += 2.dp.toPx()
      }
      drawPath(p, col.copy(alpha = a.coerceIn(0f, 1f)), style = Stroke(width, cap = StrokeCap.Round))
    }
    line(0f, 0.9f, 1.6.dp.toPx(), if (on) Color.White else VoltColors.dim)
    if (on) {
      if (mode != 1) line(sin(Waves.TAU * live.chorusPhase1) * 0.5f, 0.25f + 0.65f * mix, 1.4.dp.toPx(), hue)
      if (mode != 0) line(sin(Waves.TAU * live.chorusPhase2) * 0.6f + 0.2f, 0.25f + 0.65f * mix, 1.4.dp.toPx(), Hues.mix(hue, Color.White, 0.35f))
    }
    off(m, on)
  }
}

/** Delay: the wave repeated at the delay time, each copy fainter by the repeats; below, a timeline with a tick for every echo as it happens. */
@Composable
fun DelayDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  val buf = remember { FloatArray(90) }
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val on = v.on("fx.delay.on"); val time = v.real("fx.delay.time"); val fb = v.real("fx.delay.feedback"); val mix = v.real("fx.delay.mix")
    val w = size.width; val h = size.height
    Scope.fill(live, v, buf, 2.5f)
    val lineH = h * 0.78f
    for (k in 5 downTo 0) {
      val a = if (k == 0) 1f else if (on) fb.pow(k - 1) * mix * 1.3f * (0.35f + 0.65f * min(1f, live.echo * 2f + 0.2f)) else 0f
      if (a < 0.03f) continue
      val p = Path()
      for (i in buf.indices) {
        val x = i / (buf.size - 1f) * w * 0.7f + k * time * 28.dp.toPx()
        val y = lineH * 0.5f - buf[i] * lineH * 0.35f
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
      }
      drawPath(p, (if (k == 0) Color.White else hue).copy(alpha = if (k == 0) 0.85f else a.coerceIn(0f, 1f)), style = Stroke((if (k == 0) 1.6f else 1.2f).dp.toPx(), cap = StrokeCap.Round))
    }
    val axis = h - 8.dp.toPx()
    drawLine(VoltColors.raised, Offset(0f, axis), Offset(w, axis), 1f)
    if (on) for (start in live.noteStarts) {
      var gk = 1f
      for (k in 0 until 16) {
        val tk = start + k * time
        if (tk > live.t) break
        if (k > 1) gk *= fb
        if (k > 0 && gk < 0.03f) break
        val x = w - ((live.t - tk) / 3.0).toFloat() * w
        if (x < 0f) continue
        val hh = (if (k > 0) gk * mix * 1.6f else 1f) * (h - lineH - 4.dp.toPx())
        drawRect(
          if (k > 0) hue.copy(alpha = 0.4f + 0.6f * exp(-((live.t - tk) / 0.25)).toFloat()) else Color.White.copy(alpha = 0.6f),
          Offset(x - 1.dp.toPx(), axis - hh), Size(2.5.dp.toPx(), hh),
        )
      }
    }
    off(m, on)
  }
}

/** Reverb: a halo that grows with the duration, darkens with damping, and stays after the note has ended. */
@Composable
fun ReverbDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val on = v.on("fx.reverb.on"); val dec = v.norm("fx.reverb.decay"); val damp = v.real("fx.reverb.damp"); val mix = v.real("fx.reverb.mix")
    val w = size.width; val h = size.height
    val c = Offset(w / 2, h / 2)
    val radius = max(w, h) * (0.2f + 0.45f * dec)
    val col = Hues.mix(hue, Color(0xFF3B2F66), damp * 0.8f)
    val base = if (on) col else VoltColors.track
    drawCircle(
      Brush.radialGradient(
        0f to base.copy(alpha = if (on) 0.18f + 0.6f * live.halo else 0.2f),
        0.6f to base.copy(alpha = if (on) 0.08f + 0.3f * live.halo * mix else 0.05f),
        1f to col.copy(alpha = 0f),
        center = c, radius = radius,
      ),
      radius = radius, center = c,
    )
    if (on) {
      val count = (60f * live.halo).roundToInt()
      val seed = (live.t * 6).toInt()
      for (i in 0 until count) {
        val a = Waves.hash(i * 3 + seed) * PI.toFloat()
        val d = (0.5f + 0.5f * Waves.hash(i * 7)) * radius * 0.9f
        drawCircle(
          Hues.mix(Color.White, col, damp).copy(alpha = (0.15f + 0.5f * live.halo).coerceIn(0f, 1f)),
          radius = 0.7.dp.toPx(), center = Offset(c.x + cos(a * 2f) * d, c.y + sin(a * 2f) * d * 0.6f),
        )
      }
    }
    drawCircle(Color.White.copy(alpha = 0.25f + 0.6f * live.amp), radius = (4f + live.amp * 6f).dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
    off(m, on)
  }
}
