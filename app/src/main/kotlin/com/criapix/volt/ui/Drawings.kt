package com.criapix.volt.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextMeasurer
import com.criapix.volt.Native
import com.criapix.volt.state.SynthState
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

private const val TOP_DB = 24f
private const val BOTTOM_DB = -48f

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

/** The filter's response: the set cutoff dashed, the real one (after envelope and LFO) filled, the notes' harmonics as bars. */
@Composable
fun FilterCurveView(state: SynthState, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val w = size.width; val h = size.height
    val cutoff = v.real("filter.cutoff"); val reso = v.real("filter.reso"); val model = v.int("filter.model"); val hpf = v.real("hpf.cutoff")
    fun x(f: Float) = w * (log10(f / 20f) / 3f)
    fun y(db: Float) = h * (TOP_DB - db.coerceIn(BOTTOM_DB, TOP_DB)) / (TOP_DB - BOTTOM_DB)
    for (f in listOf(100f, 1000f, 10000f)) {
      drawLine(VoltColors.raised, Offset(x(f), 0f), Offset(x(f), h), 1f)
      label(m, if (f >= 1000f) "${(f / 1000f).toInt()}k" else "${f.toInt()}", x(f) + 3.dp.toPx(), h - 12.dp.toPx(), VoltColors.dim, 8f, mono = true)
    }
    drawLine(VoltColors.track, Offset(0f, y(0f)), Offset(w, y(0f)), 1f)
    val lead = live.lead
    if (lead != null && !lead.fm) {
      for (n in 1..60) {
        val fn = live.f0 * n
        if (fn > 20000f) break
        val db = FilterCurve.magnitudeDb(fn, live.cutoff, reso, model, hpf) - 20f * log10(n.toFloat())
        val a = (0.15f + 0.55f * live.amp) * (1f + db / 40f).coerceIn(0f, 1f)
        drawLine(Color.White.copy(alpha = a), Offset(x(fn), h), Offset(x(fn), y(db)), 1.3.dp.toPx())
      }
    }
    val n = 160
    fun curve(c: Float, close: Boolean): Path {
      val pts = FilterCurve.points(n, c, reso, model, hpf)
      return Path().apply {
        for (i in 0 until n) { val px = w * i / (n - 1f); if (i == 0) moveTo(px, y(pts[i])) else lineTo(px, y(pts[i])) }
        if (close) { lineTo(w, h); lineTo(0f, h); close() }
      }
    }
    drawPath(curve(cutoff, false), VoltColors.dim.copy(alpha = 0.8f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
    drawPath(curve(live.cutoff, true), Brush.verticalGradient(listOf(hue.copy(alpha = 0.35f), hue.copy(alpha = 0f)), 0f, h))
    drawPath(curve(live.cutoff, false), hue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    val px = x(live.cutoff)
    val py = y(FilterCurve.magnitudeDb(live.cutoff, live.cutoff, reso, model, hpf))
    val ring = reso * (0.6f + 0.4f * live.amp)
    drawCircle(hue.copy(alpha = 0.35f * ring), radius = (7f + 6f * ring).dp.toPx(), center = Offset(px, py))
    drawCircle(hue, radius = (3.5f + 3f * ring * (0.6f + 0.4f * kotlin.math.sin(live.t.toFloat() * Waves.TAU * (3f + reso * 9f)))).dp.toPx(), center = Offset(px, py))
    val f = live.cutoff
    label(m, if (f >= 1000f) String.format(java.util.Locale.US, "%.1f kHz", f / 1000f) else "${f.toInt()} Hz", w - 6.dp.toPx(), 2.dp.toPx(), VoltColors.text, 9f, mono = true, right = true)
    label(m, if (model == 1) "12 dB/oct" else "24 dB/oct", 6.dp.toPx(), 2.dp.toPx(), VoltColors.dim, 8f, mono = true)
  }
}

/** ADSR drawing with draggable handles; the stage a note is in lights up and a dot per note travels along the curve. [prefix] is "fenv" or "aenv". */
@Composable
fun EnvelopeView(state: SynthState, prefix: String, hue: Color, modifier: Modifier = Modifier) {
  val p = state.params
  val a = p["$prefix.attack"]!!.id
  val d = p["$prefix.decay"]!!.id
  val s = p["$prefix.sustain"]!!.id
  val r = p["$prefix.release"]!!.id
  val base = if (prefix == "aenv") 0 else 4
  LiveCanvas(
    state,
    modifier.fillMaxSize().pointerInput(prefix) {
      awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val pts = EnvelopeGeometry.points(state.norm(a), state.norm(d), state.norm(s), state.norm(r))
        val handle = EnvelopeGeometry.nearest(pts, down.position.x / size.width, 1f - down.position.y / size.height)
        val start = mapOf(a to state.norm(a), d to state.norm(d), s to state.norm(s), r to state.norm(r))
        var dx = 0f
        var dy = 0f
        while (true) {
          val ev = awaitPointerEvent()
          val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
          if (!ch.pressed) break
          val mv = ch.positionChange(); ch.consume()
          dx += mv.x / size.width; dy += mv.y / size.height
          when (handle) {
            EnvelopeGeometry.Handle.ATTACK -> state.set(a, EnvelopeGeometry.time(start[a]!!, dx))
            EnvelopeGeometry.Handle.DECAY_SUSTAIN -> {
              state.set(d, EnvelopeGeometry.time(start[d]!!, dx))
              state.set(s, EnvelopeGeometry.level(start[s]!!, dy))
            }
            EnvelopeGeometry.Handle.RELEASE -> state.set(r, EnvelopeGeometry.time(start[r]!!, dx))
          }
        }
      }
    },
  ) { live, v, m ->
    val pt = EnvelopeGeometry.points(state.norm(a), state.norm(d), state.norm(s), state.norm(r))
    val w = size.width
    val h = size.height - 12.dp.toPx()
    fun at(x: Float, level: Float) = Offset(x * w, h - level * h)
    val pa = at(pt.attackX, 1f); val pd = at(pt.decayX, pt.sustain); val ph = at(pt.holdEndX, pt.sustain); val pr = at(pt.releaseX, 0f)
    fun segment(i: Int): Path = Path().apply {
      when (i) {
        0 -> { moveTo(0f, h); quadraticTo(pa.x * 0.6f, pa.y + h * 0.15f, pa.x, pa.y) }
        1 -> { moveTo(pa.x, pa.y); quadraticTo(pa.x + (pd.x - pa.x) * 0.2f, pd.y, pd.x, pd.y) }
        2 -> { moveTo(pd.x, pd.y); lineTo(ph.x, ph.y) }
        else -> { moveTo(ph.x, ph.y); quadraticTo(ph.x + (pr.x - ph.x) * 0.2f, pr.y, pr.x, pr.y) }
      }
    }
    val full = Path().apply {
      moveTo(0f, h)
      quadraticTo(pa.x * 0.6f, pa.y + h * 0.15f, pa.x, pa.y)
      quadraticTo(pa.x + (pd.x - pa.x) * 0.2f, pd.y, pd.x, pd.y)
      lineTo(ph.x, ph.y)
      quadraticTo(ph.x + (pr.x - ph.x) * 0.2f, pr.y, pr.x, pr.y)
    }
    drawLine(VoltColors.raised, Offset(0f, h), Offset(w, h), 1f)
    drawPath(Path().apply { addPath(full); lineTo(pr.x, h); lineTo(0f, h); close() }, hue.copy(alpha = 0.12f))
    drawPath(full, hue.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
    val letters = listOf("A", "D", "S", "R")
    val centers = listOf((0f + pa.x) / 2, (pa.x + pd.x) / 2, (pd.x + ph.x) / 2, (ph.x + pr.x) / 2)
    for (i in 0..3) {
      val lv = live.stage[base + i]
      if (lv > 0.02f) {
        drawPath(segment(i), hue.copy(alpha = 0.18f * lv), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        drawPath(segment(i), hue.copy(alpha = 0.45f + 0.55f * lv), style = Stroke(3.2.dp.toPx(), cap = StrokeCap.Round))
      }
      label(m, letters[i], centers[i] - 3.dp.toPx(), h + 1.dp.toPx(), if (lv > 0.02f) hue else VoltColors.dim, 8.5f, bold = true)
    }
    val secA = v.real("$prefix.attack"); val secD = v.real("$prefix.decay"); val secR = v.real("$prefix.release")
    for (voice in live.voices) {
      if (voice.fm) continue
      val st = if (base == 0) voice.aStage else voice.fStage
      val value = if (base == 0) voice.a else voice.f
      val dt = (live.t - voice.tOn).toFloat()
      val x = when (st) {
        Live.A -> lerp(0f, pa.x, dt / max(secA, 1e-4f))
        Live.D -> lerp(pa.x, pd.x, (dt - secA) / max(secD, 1e-4f))
        Live.S -> lerp(pd.x, ph.x, dt - secA - secD)
        else -> lerp(ph.x, pr.x, ((live.t - voice.tOff).toFloat()) / max(secR, 1e-4f))
      }
      val c = Offset(x, h - value * h)
      drawCircle(hue.copy(alpha = 0.4f), radius = 6.dp.toPx(), center = c)
      drawCircle(Color.White, radius = 3.dp.toPx(), center = c)
    }
    for (o in listOf(pa, pd, pr)) drawCircle(hue.copy(alpha = 0.9f), radius = 5.dp.toPx(), center = o)
  }
}

/** Geometry of the algorithm diagram, shared by the drawing and by the taps on an operator. */
private class AlgoGeo(val size: Size, val layout: AlgoLayout, density: Float) {
  val cellH = size.height / (layout.rows + 0.5f)
  val cellW = min(size.width / (layout.columns.coerceAtLeast(3) + 0.6f), cellH * 1.6f)
  val box = min(min(cellW * 0.62f, cellH * 0.6f), 56f * density)
  val originX = (size.width - cellW * layout.columns) / 2f
  fun center(op: Int): Offset { val c = layout.cells[op]!!; return Offset(originX + cellW * (c.column + 0.5f), cellH * (c.row + 0.5f)) }
}

/** The DX7 algorithm: one box per operator in its colour, lit by its output; lines carry each modulator's colour down to what it modulates. */
@Composable
fun AlgorithmView(state: SynthState, modifier: Modifier = Modifier, onOperator: (Int) -> Unit = {}) {
  val algos = remember { Algorithms.decode(Native.fmAlgorithms()) }
  val algoParam = state.params["fm.algorithm"]!!
  fun current() = algos[algoParam.toReal(state.norm(algoParam.id)).toInt().coerceIn(1, 32) - 1]
  val layouts = remember { HashMap<Int, AlgoLayout>() }
  fun layoutOf(a: Algo): AlgoLayout = layouts.getOrPut(algos.indexOf(a)) { Algorithms.layout(a) }

  LiveCanvas(
    state,
    modifier.fillMaxSize().pointerInput(Unit) {
      awaitEachGesture {
        val down = awaitFirstDown()
        val up = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
        if (up != null && !up.pressed) {
          val a = current(); val g = AlgoGeo(Size(size.width.toFloat(), size.height.toFloat()), layoutOf(a), density)
          val hit = (1..6).firstOrNull { op -> (g.center(op) - down.position).getDistance() < g.box * 0.8f }
          if (hit != null) onOperator(hit)
        }
      }
    },
  ) { live, v, m ->
    val algo = current()
    val g = AlgoGeo(size, layoutOf(algo), density)
    val box = g.box
    val outY = size.height - 6.dp.toPx()
    // the output bus under the carriers
    val xs = algo.carriers.map { g.center(it).x }
    drawLine(VoltColors.track, Offset(xs.min() - 12.dp.toPx(), outY), Offset(xs.max() + 12.dp.toPx(), outY), 2.dp.toPx(), StrokeCap.Round)
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -(live.t.toFloat() * 60f))
    for (op in 1..6) {
      val hue = Hues.op(op)
      val lvl = live.op[op]
      val from = g.center(op)
      for (target in algo.targetsOf(op)) {
        val to = g.center(target)
        drawLine(hue.copy(alpha = (0.25f + 0.75f * (lvl * 1.4f).coerceIn(0f, 1f))), Offset(from.x, from.y + box / 2), Offset(to.x, to.y - box / 2), (1.5f + 3f * lvl).dp.toPx(), pathEffect = dash)
      }
      if (op in algo.carriers) drawLine(hue.copy(alpha = 0.3f + 0.7f * lvl), Offset(from.x, from.y + box / 2), Offset(from.x, outY), (1.5f + 3f * lvl).dp.toPx())
    }
    // the feedback loop
    val fbv = v.real("fm.feedback") / 7f
    if (fbv > 0f && algo.feedback in 1..6) {
      val to = g.center(algo.feedback); val from = g.center(algo.feedbackFrom)
      val col = Hues.op(algo.feedbackFrom).copy(alpha = 0.3f + 0.7f * fbv * (live.op[algo.feedbackFrom] * 2f).coerceIn(0f, 1f))
      val path = Path()
      if (algo.feedbackFrom == algo.feedback) {
        val x = from.x + box / 2; val rr = box * 0.32f
        path.moveTo(x, from.y - box * 0.15f)
        path.cubicTo(x + rr * 2.2f, from.y - box * 0.9f, x + rr * 2.2f, from.y + box * 0.9f, x, from.y + box * 0.15f)
      } else {
        val x = max(from.x, to.x) + box / 2 + box * 0.5f
        path.moveTo(from.x + box / 2, from.y)
        path.cubicTo(x, from.y, x, to.y, to.x + box / 2, to.y)
      }
      drawPath(path, col, style = Stroke((1f + 3f * fbv).dp.toPx()))
    }
    for (op in 1..6) {
      val c = g.center(op)
      val hue = Hues.op(op)
      val on = v.on("fm.op$op.on")
      val lvl = if (on) (live.op[op] * 1.4f).coerceIn(0f, 1f) else 0f
      val tl = Offset(c.x - box / 2, c.y - box / 2)
      val corner = CornerRadius(8.dp.toPx())
      if (lvl > 0.05f) drawRoundRect(hue.copy(alpha = 0.3f * lvl), Offset(tl.x - 4.dp.toPx(), tl.y - 4.dp.toPx()), Size(box + 8.dp.toPx(), box + 8.dp.toPx()), CornerRadius(11.dp.toPx()))
      drawRoundRect(if (on) Hues.mix(Color(0xFF14161D), hue, 0.18f + 0.82f * lvl) else Color(0xFF14161D), tl, Size(box, box), corner)
      drawRoundRect((if (on) hue else VoltColors.track).copy(alpha = if (on) 1f else 0.5f), tl, Size(box, box), corner, style = Stroke(if (op in algo.carriers) 2.5.dp.toPx() else 1.5.dp.toPx()))
      label(m, "$op", c.x - 3.5.dp.toPx(), c.y - 8.dp.toPx(), if (lvl > 0.45f && on) Color(0xFF0B0C11) else if (on) hue else VoltColors.dim, 13f, bold = true)
    }
    label(m, "${algoParam.toReal(state.norm(algoParam.id)).toInt()}", 8.dp.toPx(), 2.dp.toPx(), Hues.algorithm.copy(alpha = 0.55f), 30f, bold = true)
    label(m, "SAÍDA", size.width - 6.dp.toPx(), outY - 14.dp.toPx(), VoltColors.dim, 8f, bold = true, right = true)
  }
}

/**
 * A DX7 envelope (four rates, four levels) with draggable handles. [prefix] is "fm.op3" or "fm.pitchenv"; the parameters are
 * `$prefix.r1..r4` and `$prefix.l1..l4`, all steps 0..99. [op] is the operator number (1..6), or 0 for the pitch envelope, whose
 * levels are centred on 50 (no shift) with a centre line. The segment a note is in lights up and a dot per note rides the curve.
 */
@Composable
fun Dx7EnvelopeView(state: SynthState, prefix: String, hue: Color, op: Int = 0, modifier: Modifier = Modifier) {
  val p = state.params
  val pitch = op == 0
  val rateParams = (1..4).map { p["$prefix.r$it"]!! }
  val levelParams = (1..4).map { p["$prefix.l$it"]!! }
  LiveCanvas(
    state,
    modifier.fillMaxSize().pointerInput(prefix) {
      awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val startRates = IntArray(4) { rateParams[it].toReal(state.norm(rateParams[it].id)).toInt() }
        val startLevels = IntArray(4) { levelParams[it].toReal(state.norm(levelParams[it].id)).toInt() }
        val pts = Dx7Envelope.points(startRates, startLevels)
        val handle = Dx7Envelope.nearest(pts, down.position.x / size.width, 1f - down.position.y / size.height)
        var dx = 0f
        var dy = 0f
        while (true) {
          val ev = awaitPointerEvent()
          val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
          if (!ch.pressed) break
          val m = ch.positionChange(); ch.consume()
          dx += m.x / size.width; dy += m.y / size.height
          val (rate, level) = Dx7Envelope.drag(startRates[handle], startLevels[handle], dx, dy)
          if (rate != rateParams[handle].toReal(state.norm(rateParams[handle].id)).toInt()) state.set(rateParams[handle].id, rateParams[handle].toNorm(rate.toFloat()))
          if (level != levelParams[handle].toReal(state.norm(levelParams[handle].id)).toInt()) state.set(levelParams[handle].id, levelParams[handle].toNorm(level.toFloat()))
        }
      }
    },
  ) { live, _, m ->
    val rates = IntArray(4) { rateParams[it].toReal(state.norm(rateParams[it].id)).toInt() }
    val levels = IntArray(4) { levelParams[it].toReal(state.norm(levelParams[it].id)).toInt() }
    val pad = 10.dp.toPx()
    val w = size.width - 2 * pad
    val h = size.height - 2 * pad
    fun at(pt: Dx7Envelope.Pt) = Offset(pad + pt.x * w, pad + h - pt.level * h)
    drawLine(VoltColors.raised, Offset(pad, pad + h), Offset(pad + w, pad + h), 1f)
    if (pitch) drawLine(VoltColors.track, Offset(pad, pad + h / 2), Offset(pad + w, pad + h / 2), 2f)
    val pts = Dx7Envelope.points(rates, levels)
    val line = Path()
    pts.forEachIndexed { i, q -> val o = at(q); if (i == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y) }
    drawPath(Path().apply { addPath(line); lineTo(at(pts[5]).x, pad + h); lineTo(at(pts[0]).x, pad + h); close() }, hue.copy(alpha = 0.12f))
    drawPath(line, hue.copy(alpha = 0.65f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    val sus = at(pts[3]); val susEnd = at(pts[4])
    drawLine(hue.copy(alpha = 0.3f), Offset(sus.x, pad), Offset(sus.x, pad + h), 1f)
    drawLine(hue.copy(alpha = 0.3f), Offset(susEnd.x, pad), Offset(susEnd.x, pad + h), 1f)

    // the newest FM note's stage lights its segment: 1..3 rise, 4 release
    val lead = live.lead?.takeIf { it.fm }
    if (lead != null) {
      val stage = if (pitch) lead.peStage else lead.opStage[op]
      val level = (if (pitch) lead.pe else lead.opLevel[op])
      if (stage in 1..4) {
        val i0 = if (stage == 4) 4 else stage - 1
        val i1 = if (stage == 4) 5 else stage
        val a = at(pts[i0]); val b = at(pts[i1])
        drawLine(hue.copy(alpha = 0.25f), a, b, 9.dp.toPx(), StrokeCap.Round)
        drawLine(hue, a, b, 3.2.dp.toPx(), StrokeCap.Round)
      }
    }
    for (voice in live.voices) {
      if (!voice.fm) continue
      val stage = if (pitch) voice.peStage else voice.opStage[op]
      val level = if (pitch) voice.pe else voice.opLevel[op]
      val x = when (stage) {
        1, 2, 3 -> {
          val from = pts[stage - 1]; val to = pts[stage]
          val span = kotlin.math.abs(to.level - from.level)
          val frac = if (span < 1e-4f) 1f else (kotlin.math.abs(level / 99f - from.level) / span)
          lerp(at(from).x, at(to).x, frac)
        }
        4 -> {
          val from = pts[4]; val to = pts[5]
          val span = kotlin.math.abs(to.level - from.level)
          val frac = if (span < 1e-4f) 1f else (kotlin.math.abs(level / 99f - from.level) / span)
          lerp(at(from).x, at(to).x, frac)
        }
        else -> at(pts[5]).x
      }
      val c = Offset(x, pad + h - (level / 99f) * h)
      drawCircle(hue.copy(alpha = 0.4f), radius = 6.dp.toPx(), center = c)
      drawCircle(Color.White, radius = 3.dp.toPx(), center = c)
    }
    Dx7Envelope.handles(pts).forEachIndexed { i, q ->
      val o = at(q)
      drawCircle(hue, radius = 5.dp.toPx(), center = o)
      label(m, (i + 1).toString(), o.x - 3.dp.toPx(), (o.y - 18.dp.toPx()).coerceAtLeast(0f), VoltColors.text, 9f, bold = true)
    }
    if (pitch) {
      val st = (live.pitchEnv - 50f) / 50f * 48f
      label(m, (if (st > 0) "+" else "") + "${st.toInt()} st", size.width - 6.dp.toPx(), 2.dp.toPx(), VoltColors.dim, 8.5f, mono = true, right = true)
    }
  }
}

/** Keyboard level scaling of an operator: the left and right curves around the break point, with the newest note riding the curve. */
@Composable
fun KeyScalingView(state: SynthState, op: Int, hue: Color, modifier: Modifier = Modifier) {
  LiveCanvas(state, modifier.fillMaxSize()) { live, v, m ->
    val k = "fm.op$op.kls."
    val breakNote = v.int(k + "break")
    val ld = v.int(k + "ldepth"); val lc = v.int(k + "lcurve"); val rd = v.int(k + "rdepth"); val rc = v.int(k + "rcurve")
    val pad = 6.dp.toPx()
    val w = size.width - 2 * pad
    val h = size.height - 2 * pad
    fun y(change: Float) = pad + h / 2 - change * h / 2
    drawLine(VoltColors.track, Offset(pad, y(0f)), Offset(pad + w, y(0f)), 1f)
    val bx = pad + w * breakNote / KeyScaling.NOTES.toFloat()
    drawLine(Color.White.copy(alpha = 0.45f), Offset(bx, pad), Offset(bx, pad + h), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
    val pts = KeyScaling.polyline(breakNote, ld, lc, rd, rc)
    val path = Path()
    pts.forEachIndexed { i, (x, c) -> if (i == 0) path.moveTo(pad + x * w, y(c)) else path.lineTo(pad + x * w, y(c)) }
    drawPath(path, hue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    for (voice in live.voices) {
      val idx = (voice.note - 21).coerceIn(0, KeyScaling.NOTES)
      val change = if (idx < breakNote) KeyScaling.change((breakNote - idx) / breakNote.toFloat().coerceAtLeast(1f), ld, lc)
      else KeyScaling.change((idx - breakNote) / (KeyScaling.NOTES - breakNote).toFloat().coerceAtLeast(1f), rd, rc)
      val c = Offset(pad + w * idx / KeyScaling.NOTES, y(if (idx == breakNote) 0f else change))
      drawCircle(hue.copy(alpha = 0.4f), radius = 6.dp.toPx(), center = c)
      drawCircle(Color.White, radius = 3.dp.toPx(), center = c)
    }
    label(m, UiNote.name(breakNote), min(bx + 4.dp.toPx(), size.width - 24.dp.toPx()), 2.dp.toPx(), VoltColors.dim, 8.5f, mono = true)
  }
}

/** The note name behind a DX7 break point (0 = A-1). */
object UiNote {
  fun name(breakPoint: Int): String = ParamUi.noteName(21 + breakPoint)
}
