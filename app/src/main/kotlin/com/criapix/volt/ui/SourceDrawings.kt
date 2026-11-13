package com.criapix.volt.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.criapix.volt.state.SynthState
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

private fun DrawScope.centerLine(h: Float) = drawLine(VoltColors.raised, Offset(0f, h / 2), Offset(size.width, h / 2), 1f)

/** A line that glows: a wide faint stroke under the real one. */
private fun DrawScope.glowStroke(path: Path, hue: Color, alpha: Float, width: Float, glow: Float) {
  if (glow > 0.02f) drawPath(path, hue.copy(alpha = (0.18f * glow).coerceIn(0f, 1f)), style = Stroke(width + 6.dp.toPx(), cap = StrokeCap.Round))
  drawPath(path, hue.copy(alpha = alpha.coerceIn(0f, 1f)), style = Stroke(width, cap = StrokeCap.Round))
}

/** One oscillator's wave: its shape, pitch ratio and pulse width, scrolling, drawn as hot as the drive. */
@Composable
fun OscDrawing(state: SynthState, n: Int, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val w = size.width; val h = size.height; val mid = h / 2
    val lev = v.real("osc$n.level"); val wave = v.int("osc$n.wave"); val pw = v.real("osc$n.pw")
    val rat = 2f.pow(v.real("osc$n.octave") + v.real("osc$n.semi") / 12f + v.real("osc$n.fine") / 1200f)
    val cyc = 2f * rat
    val k = 1f + v.real("mix.drive") * 14f
    val th = tanh(k)
    val amp = live.amp
    val t = live.t.toFloat()
    val fmAmount = if (n == 1) v.real("pmod.osc2.pitch") else 0f
    val sync = n == 1 && v.on("osc1.sync")
    val pwMod = if (n == 1) v.real("pmod.fenv.pw") * live.fenv + v.real("lfo1.pw") * live.lfo1 + v.real("lfo2.pw") * live.lfo2 else 0f
    centerLine(h)
    val col = Hues.mix(hue, Color(0xFFFF4B3A), v.real("mix.drive") * amp)
    val path = Path()
    var x = 0f
    val step = 1.5.dp.toPx()
    while (x <= w) {
      var ph = x / w * cyc + t * 0.6f * rat
      ph += fmAmount * 0.25f * sin(Waves.TAU * x / w * cyc * 1.5f)
      if (sync) ph = (x / w * cyc * 1.6f) % 1.6f + t * 0.6f
      val y = tanh(k * Waves.osc(wave, ph, (pw + pwMod * 0.3f).coerceIn(0.05f, 0.95f)) * 0.7f) / th
      val py = mid - y * h * 0.36f * max(0.15f, lev)
      if (x == 0f) path.moveTo(x, py) else path.lineTo(x, py)
      x += step
    }
    glowStroke(path, col, 0.2f + 0.8f * min(1f, amp * 1.2f + 0.15f) * max(0.25f, lev), 1.3.dp.toPx() + 2.dp.toPx() * amp * lev, amp * lev)
    label(m, "×" + String.format(java.util.Locale.US, "%.2f", rat), 6.dp.toPx(), h - 14.dp.toPx(), VoltColors.dim, 8.5f, mono = true)
  }
}

/** The five sources as meters: the three oscillators, the sub and the noise, lit by what sounds, with a red edge for drive. */
@Composable
fun MixerDrawing(state: SynthState, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val items = listOf(
      Triple("O1", v.real("osc1.level"), Hues.osc1), Triple("O2", v.real("osc2.level"), Hues.osc2), Triple("O3", v.real("osc3.level"), Hues.osc3),
      Triple("SUB", v.real("mix.sub"), Hues.mixer), Triple("RUÍDO", v.real("mix.noise"), Color(0xFFD9DCE4)),
    )
    val bw = (size.width - 16.dp.toPx()) / items.size
    val top = 6.dp.toPx(); val barH = size.height - 24.dp.toPx()
    val lit = min(1f, live.amp * 1.2f)
    items.forEachIndexed { i, (name, value, hue) ->
      val x = 8.dp.toPx() + i * bw
      drawRoundRect(VoltColors.track, Offset(x + 4.dp.toPx(), top), androidx.compose.ui.geometry.Size(bw - 8.dp.toPx(), barH), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
      val hh = barH * value
      if (lit * value > 0.05f) drawRect(hue.copy(alpha = 0.25f * lit * value), Offset(x + 1.dp.toPx(), top + barH - hh - 3.dp.toPx()), androidx.compose.ui.geometry.Size(bw - 2.dp.toPx(), hh + 6.dp.toPx()))
      drawRoundRect(hue.copy(alpha = 0.35f + 0.65f * lit * value + 0.1f), Offset(x + 4.dp.toPx(), top + barH - hh), androidx.compose.ui.geometry.Size(bw - 8.dp.toPx(), hh), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
      label(m, name, x + bw / 2 - 5.dp.toPx() * name.length / 2f, size.height - 14.dp.toPx(), VoltColors.dim, 8f, bold = true)
    }
    val drive = v.real("mix.drive")
    if (drive > 0.01f) drawRect(Color(0xFFFF4B3A).copy(alpha = (0.15f + 0.6f * drive * live.amp).coerceIn(0f, 1f)), Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()))
  }
}

/** The high-pass curve with the harmonics of the note being cut. */
@Composable
fun HpfDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val w = size.width; val h = size.height
    val hp = v.real("hpf.cutoff")
    fun x(f: Float) = w * log10(f / 20f) / 3f
    fun resp(f: Float): Float { val r = f / hp; return r / sqrt(1f + r * r) }
    val lead = live.lead
    if (lead != null && !lead.fm) {
      for (n in 1..12) {
        val fn = live.f0 * n
        val mag = (1f / n) * resp(fn)
        drawLine(Color.White.copy(alpha = ((0.2f + 0.6f * live.amp) * mag).coerceIn(0f, 1f)), Offset(x(fn), h - 6.dp.toPx()), Offset(x(fn), h - 6.dp.toPx() - mag * (h - 16.dp.toPx())), 1.5.dp.toPx())
      }
    }
    val path = Path()
    for (i in 0..120) {
      val f = 20f * 1000f.pow(i / 120f)
      val px = w * i / 120f; val py = h - 6.dp.toPx() - resp(f) * (h - 16.dp.toPx())
      if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(path, hue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    drawCircle(hue, radius = 3.5.dp.toPx(), center = Offset(x(hp), h - 6.dp.toPx() - 0.707f * (h - 16.dp.toPx())))
    label(m, if (hp >= 1000f) String.format(java.util.Locale.US, "%.1f kHz", hp / 1000f) else "${hp.toInt()} Hz", 6.dp.toPx(), 2.dp.toPx(), VoltColors.dim, 8.5f, mono = true)
  }
}

/** Voices as dots: spread across the picture by the stereo abertura, up and down by the detune, trembling with drift. */
@Composable
fun VoiceDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val w = size.width; val h = size.height
    val mode = v.int("voice.mode")
    val n = if (mode == 3) v.int("voice.unison") else 1
    val spread = if (mode == 3) v.real("voice.spread") else 0f
    val detune = v.real("voice.detune")
    val drift = v.real("voice.drift")
    drawLine(VoltColors.raised, Offset(w / 2, 6.dp.toPx()), Offset(w / 2, h - 6.dp.toPx()), 1f)
    label(m, "E", 6.dp.toPx(), h / 2 - 5.dp.toPx(), VoltColors.dim, 8f, bold = true)
    label(m, "D", w - 6.dp.toPx(), h / 2 - 5.dp.toPx(), VoltColors.dim, 8f, bold = true, right = true)
    for (i in 0 until n) {
      val u = if (n > 1) i / (n - 1f) * 2f - 1f else 0f
      val x = w / 2 + u * spread * (w / 2 - 24.dp.toPx())
      val y = h / 2 - u * detune * h * 0.35f + (kotlin.random.Random.nextFloat() - 0.5f) * drift * 4.dp.toPx()
      drawCircle(hue.copy(alpha = 0.2f * live.amp), radius = (8f + 6f * live.amp).dp.toPx(), center = Offset(x, y))
      drawCircle(hue.copy(alpha = 0.3f + 0.7f * live.amp), radius = (3.5f + 2.5f * live.amp).dp.toPx(), center = Offset(x, y))
    }
    if (live.glide > 0.01f) {
      val p = Path().apply { moveTo(w * 0.15f, h * 0.8f); quadraticTo(w * 0.4f, h * 0.8f, w * 0.5f, h * 0.5f) }
      drawPath(p, hue.copy(alpha = live.glide), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
    label(m, state.params["voice.mode"]!!.let { ParamUi.format(it, v.real("voice.mode")) }.uppercase(), 8.dp.toPx(), 4.dp.toPx(), VoltColors.text, 9.5f, bold = true)
  }
}

/** An LFO's last two seconds scrolling by, its present value as a dot, and a meter for each place it is sent (pitch, width, cutoff, volume). */
@Composable
fun LfoDrawing(state: SynthState, n: Int, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val w = size.width; val h = size.height
    val rate = v.real("lfo$n.rate"); val delay = v.real("lfo$n.delay"); val wave = v.int("lfo$n.wave")
    val depth = listOf("pitch", "pw", "cutoff", "amp").map { v.real("lfo$n.$it") }
    val amp = (h / 2 - 8.dp.toPx()) * (0.25f + 0.75f * depth.max())
    val phase = if (n == 1) live.lfoPhase1 else live.lfoPhase2
    val cur = if (n == 1) live.lfo1 else live.lfo2
    centerLine(h)
    val graphW = w - 40.dp.toPx()
    val path = Path()
    var x = 0f
    while (x <= graphW) {
      val back = (1f - x / graphW) * 2f
      val ph = phase - back * rate
      val fade = if (delay > 0.01f) ((live.t - back - live.lastNoteOn) / delay).toFloat().coerceIn(0f, 1f) else 1f
      val y = h / 2 - Waves.lfo(wave, ph) * amp * fade
      if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
      x += 1.5.dp.toPx()
    }
    drawPath(path, hue.copy(alpha = 0.9f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    val dot = Offset(graphW, h / 2 - cur * amp)
    drawCircle(hue.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = dot)
    drawCircle(Color.White, radius = 3.5.dp.toPx(), center = dot)
    listOf("P", "L", "C", "V").forEachIndexed { i, name ->
      val d = depth[i]
      val x0 = graphW + 10.dp.toPx() + i * 7.5.dp.toPx()
      val top = 8.dp.toPx(); val barH = h - 24.dp.toPx()
      drawRect(VoltColors.track, Offset(x0, top), androidx.compose.ui.geometry.Size(5.dp.toPx(), barH))
      drawRect(hue.copy(alpha = 0.3f + 0.7f * d * (0.5f + 0.5f * abs(cur))), Offset(x0, top + barH * (1f - d)), androidx.compose.ui.geometry.Size(5.dp.toPx(), barH * d))
      label(m, name, x0, h - 13.dp.toPx(), VoltColors.dim, 7f, bold = true)
    }
  }
}

/** Four dashed lines from a source to a destination; thicker with the amount, running faster and brighter with the source's signal. */
@Composable
fun PolyModDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val rows = listOf(
      Triple("ENV F → AFIN.", v.real("pmod.fenv.pitch"), live.fenv), Triple("ENV F → LARG.", v.real("pmod.fenv.pw"), live.fenv),
      Triple("OSC 2 → AFIN.", v.real("pmod.osc2.pitch"), live.amp * v.real("osc2.level")), Triple("OSC 2 → LARG.", v.real("pmod.osc2.pw"), live.amp * v.real("osc2.level")),
    )
    val rowH = (size.height - 8.dp.toPx()) / rows.size
    rows.forEachIndexed { i, (name, amount, signal) ->
      val y = 4.dp.toPx() + i * rowH + rowH / 2
      val a = abs(amount)
      label(m, name, 6.dp.toPx(), y - 6.dp.toPx(), if (a > 0.01f) VoltColors.text else VoltColors.dim, 8.5f, bold = true)
      drawLine(
        hue.copy(alpha = (0.15f + 0.85f * a * signal + 0.1f * a).coerceIn(0f, 1f)), Offset(88.dp.toPx(), y), Offset(size.width - 8.dp.toPx(), y),
        (1f + a * 5f).dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f), -(live.t.toFloat() * 60f * (0.3f + signal))),
      )
    }
  }
}

/** Sixteen steps: the notes being walked as bar heights, the current step lit for the length of the gate. */
@Composable
fun ArpDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val on = v.on("arp.on")
    val seq = if (live.heldNotes().isEmpty()) IntArray(0) else live.arpSequence()
    val n = 16
    val cw = size.width / n
    val current = ((live.arpStep - 1) % n + n) % n
    val gate = v.real("arp.gate")
    for (i in 0 until n) {
      val lit = if (on && seq.isNotEmpty() && i == current) live.arpPulse else 0f
      val nh = if (seq.isNotEmpty()) (seq[i % seq.size] - 36) / 72f else 0.3f
      val bh = (size.height - 8.dp.toPx()) * (0.2f + nh).coerceIn(0.12f, 1f) * (if (on) 1f else 0.45f)
      val x = i * cw + 1.dp.toPx()
      if (lit > 0f) drawRect(hue.copy(alpha = 0.2f * lit), Offset(x - 3.dp.toPx(), size.height - 4.dp.toPx() - bh - 3.dp.toPx()), androidx.compose.ui.geometry.Size(max(2f, cw * gate) + 6.dp.toPx(), bh + 6.dp.toPx()))
      drawRect(if (lit > 0f) hue.copy(alpha = 0.45f + 0.55f * lit) else if (on) VoltColors.raised else VoltColors.track.copy(alpha = 0.5f), Offset(x, size.height - 4.dp.toPx() - bh), androidx.compose.ui.geometry.Size(max(2f, cw * gate - 1.dp.toPx()), bh))
    }
    val div = v.state(state, "arp.division")
    label(m, if (on) "${v.int("arp.bpm")} BPM · $div · ${v.int("arp.octaves")} oit." else "ARP desligado", 6.dp.toPx(), 2.dp.toPx(), VoltColors.dim, 9f, mono = true)
  }
}

private fun Vals.state(state: SynthState, name: String): String { val p = state.params[name]!!; return ParamUi.format(p, real(name)) }

/** The DX7 LFO, like the analog one: two seconds scrolling by and its present value. */
@Composable
fun FmLfoDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val w = size.width; val h = size.height
    val hz = 0.062f * (47f / 0.062f).pow(v.real("fm.lfo.speed") / 99f)
    val wave = v.int("fm.lfo.wave")
    val depth = max(v.real("fm.lfo.pmd"), v.real("fm.lfo.amd")) / 99f
    val amp = (h / 2 - 8.dp.toPx()) * (0.25f + 0.75f * depth)
    centerLine(h)
    val graphW = w - 14.dp.toPx()
    val path = Path()
    var x = 0f
    while (x <= graphW) {
      val back = (1f - x / graphW) * 2f
      val y = h / 2 - Waves.fmLfo(wave, live.fmLfoPhase - back * hz) * amp
      if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
      x += 1.5.dp.toPx()
    }
    drawPath(path, hue, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    val dot = Offset(graphW, h / 2 - live.fmLfoValue * amp)
    drawCircle(hue.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = dot)
    drawCircle(Color.White, radius = 3.5.dp.toPx(), center = dot)
    label(m, String.format(java.util.Locale.US, "%.2f Hz", hz), 6.dp.toPx(), 2.dp.toPx(), VoltColors.dim, 8.5f, mono = true)
  }
}

/** The three performance controllers as bars: what the wheel, the pressure and the breath are sending now. */
@Composable
fun ControllersDrawing(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, _, m ->
    listOf("RODA" to live.wheel, "AFTERTOUCH" to live.aftertouch, "BREATH" to live.breath).forEachIndexed { i, (name, value) ->
      val bw = (size.width - 16.dp.toPx()) / 3
      val x = 8.dp.toPx() + i * bw
      drawRect(VoltColors.track, Offset(x, 6.dp.toPx()), androidx.compose.ui.geometry.Size(bw - 10.dp.toPx(), 7.dp.toPx()))
      drawRect(hue, Offset(x, 6.dp.toPx()), androidx.compose.ui.geometry.Size((bw - 10.dp.toPx()) * value, 7.dp.toPx()))
      label(m, name, x, 18.dp.toPx(), if (value > 0f) VoltColors.text else VoltColors.dim, 8f, bold = true)
    }
  }
}

/** The six operators as lights: on or off, brightness = output. */
@Composable
fun FmGlobalDrawing(state: SynthState, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val bw = (size.width - 20.dp.toPx()) / 6
    for (n in 1..6) {
      val on = v.on("fm.op$n.on")
      val lvl = live.op[n]
      val c = Offset(10.dp.toPx() + (n - 1) * bw + bw / 2, size.height / 2)
      val r = min(bw, size.height) / 2 - 6.dp.toPx()
      if (on && lvl > 0.05f) drawCircle(Hues.op(n).copy(alpha = 0.25f * lvl), radius = r + 5.dp.toPx(), center = c)
      drawCircle(if (on) Hues.op(n).copy(alpha = 0.2f + 0.8f * min(1f, lvl * 1.4f)) else VoltColors.track, radius = r, center = c)
      label(m, "$n", c.x - 3.dp.toPx(), c.y - 7.dp.toPx(), if (on && lvl > 0.4f) Color(0xFF0B0C11) else VoltColors.text, 10f, bold = true)
    }
  }
}

/** An operator's frequency as the DX7 shows it, in big type, over a wave that gets denser with the ratio. */
@Composable
fun OpFreqDrawing(state: SynthState, n: Int, modifier: Modifier = Modifier) {
  val hue = Hues.op(n)
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val k = "fm.op$n."
    val fixed = v.on(k + "mode"); val coarse = v.int(k + "coarse"); val fine = v.int(k + "fine"); val detune = v.int(k + "detune")
    val lvl = (live.op[n] * 1.5f).coerceIn(0f, 1f)
    label(m, FmFrequency.format(fixed, coarse, fine), 8.dp.toPx(), 2.dp.toPx(), hue, 24f, bold = true)
    label(m, (if (fixed) "FREQUÊNCIA FIXA" else "RAZÃO DA NOTA") + (if (detune != 0) "  ·  DETUNE ${if (detune > 0) "+" else ""}$detune" else ""), 8.dp.toPx(), 34.dp.toPx(), VoltColors.dim, 8f, bold = true)
    val cyc = if (fixed) 2f else min(12f, FmFrequency.ratio(coarse, fine).toFloat() * 1.5f)
    val path = Path()
    var x = 0f
    while (x <= size.width) {
      val y = size.height - 8.dp.toPx() - sin(x / size.width * Waves.TAU * cyc + live.t.toFloat() * 3f) * 6.dp.toPx() * (0.3f + 0.7f * lvl)
      if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
      x += 1.5.dp.toPx()
    }
    drawPath(path, hue.copy(alpha = 0.3f + 0.7f * lvl), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
  }
}
