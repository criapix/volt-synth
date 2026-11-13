package com.criapix.volt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.state.SynthState
import kotlin.math.log10

private const val TOP_DB = 24f
private const val BOTTOM_DB = -48f

@Composable
fun FilterCurveView(state: SynthState, modifier: Modifier = Modifier) {
  val p = state.params
  val ids = listOf("filter.cutoff", "filter.reso", "filter.model", "hpf.cutoff").map { p[it]!!.id }
  // Reading the flows makes the drawing follow every change.
  val cut by state.value(ids[0]).collectAsStateWithLifecycle()
  val res by state.value(ids[1]).collectAsStateWithLifecycle()
  val mod by state.value(ids[2]).collectAsStateWithLifecycle()
  val hp by state.value(ids[3]).collectAsStateWithLifecycle()
  val cutoff = p.all[ids[0]].toReal(cut)
  val reso = p.all[ids[1]].toReal(res)
  val model = p.all[ids[2]].toReal(mod).toInt()
  val hpf = p.all[ids[3]].toReal(hp)

  Canvas(modifier.fillMaxSize()) {
    val n = 160
    val pts = FilterCurve.points(n, cutoff, reso, model, hpf)
    fun y(db: Float) = size.height * (TOP_DB - db.coerceIn(BOTTOM_DB, TOP_DB)) / (TOP_DB - BOTTOM_DB)
    drawLine(VoltColors.track, Offset(0f, y(0f)), Offset(size.width, y(0f)), 1f)
    val cx = size.width * (log10(cutoff / 20f) / 3f)  // 20 Hz .. 20 kHz = 3 decades
    drawLine(VoltColors.accent.copy(alpha = 0.5f), Offset(cx, 0f), Offset(cx, size.height), 2f)
    val path = Path()
    for (i in 0 until n) {
      val x = size.width * i / (n - 1f)
      if (i == 0) path.moveTo(x, y(pts[i])) else path.lineTo(x, y(pts[i]))
    }
    drawPath(path, VoltColors.accent2, style = Stroke(4f))
  }
}

/** ADSR drawing with draggable handles. [prefix] is "fenv" or "aenv". */
@Composable
fun EnvelopeView(state: SynthState, prefix: String, modifier: Modifier = Modifier) {
  val p = state.params
  val a = p["$prefix.attack"]!!.id
  val d = p["$prefix.decay"]!!.id
  val s = p["$prefix.sustain"]!!.id
  val r = p["$prefix.release"]!!.id
  val av by state.value(a).collectAsStateWithLifecycle()
  val dv by state.value(d).collectAsStateWithLifecycle()
  val sv by state.value(s).collectAsStateWithLifecycle()
  val rv by state.value(r).collectAsStateWithLifecycle()

  Canvas(
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
          val m = ch.positionChange(); ch.consume()
          dx += m.x / size.width; dy += m.y / size.height
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
  ) {
    val pts = EnvelopeGeometry.points(av, dv, sv, rv)
    val w = size.width
    val h = size.height
    fun at(x: Float, level: Float) = Offset(x * w, h - level * h)
    val path = Path().apply {
      moveTo(0f, h)
      val pa = at(pts.attackX, 1f)
      quadraticTo(pa.x * 0.6f, pa.y + h * 0.15f, pa.x, pa.y)            // concave attack, like an RC charge
      val pd = at(pts.decayX, pts.sustain)
      quadraticTo(pa.x + (pd.x - pa.x) * 0.2f, pd.y, pd.x, pd.y)         // exponential decay
      val ph = at(pts.holdEndX, pts.sustain)
      lineTo(ph.x, ph.y)
      val pr = at(pts.releaseX, 0f)
      quadraticTo(ph.x + (pr.x - ph.x) * 0.2f, pr.y, pr.x, pr.y)
    }
    drawPath(path, VoltColors.accent2, style = Stroke(4f))
    for (o in listOf(at(pts.attackX, 1f), at(pts.decayX, pts.sustain), at(pts.releaseX, 0f))) {
      drawCircle(VoltColors.accent, 12f, o)
    }
  }
}

/** The DX7 algorithm of the patch: boxes per operator, lines from modulators down to what they modulate. */
@Composable
fun AlgorithmView(state: SynthState, modifier: Modifier = Modifier) {
  val algos = remember { Algorithms.decode(com.criapix.volt.Native.fmAlgorithms()) }
  val p = state.params
  val algoParam = p["fm.algorithm"]!!
  val algoNorm by state.value(algoParam.id).collectAsStateWithLifecycle()
  val on0 by state.value(p["fm.op1.on"]!!.id).collectAsStateWithLifecycle()
  val on1 by state.value(p["fm.op2.on"]!!.id).collectAsStateWithLifecycle()
  val on2 by state.value(p["fm.op3.on"]!!.id).collectAsStateWithLifecycle()
  val on3 by state.value(p["fm.op4.on"]!!.id).collectAsStateWithLifecycle()
  val on4 by state.value(p["fm.op5.on"]!!.id).collectAsStateWithLifecycle()
  val on5 by state.value(p["fm.op6.on"]!!.id).collectAsStateWithLifecycle()
  val ons = listOf(on0, on1, on2, on3, on4, on5)
  val number = algoParam.toReal(algoNorm).toInt().coerceIn(1, 32)
  val algo = algos[number - 1]
  val layout = remember(number) { Algorithms.layout(algo) }
  val measurer = rememberTextMeasurer()

  Canvas(modifier.fillMaxSize()) {
    val cellH = size.height / layout.rows
    val cellW = minOf(size.width / (layout.columns.coerceAtLeast(3) + 0.6f), cellH * 1.6f)
    val box = minOf(cellW * 0.62f, cellH * 0.6f, 56.dp.toPx())
    val originX = (size.width - cellW * layout.columns) / 2f
    fun center(op: Int): Offset {
      val c = layout.cells[op]!!
      return Offset(originX + cellW * (c.column + 0.5f), cellH * (c.row + 0.5f))
    }
    val lineColor = VoltColors.dim
    // modulation lines: from the bottom edge of the modulator to the top edge of its target
    algo.modulators.forEach { (target, mods) ->
      mods.forEach { m ->
        val a = center(m)
        val b = center(target)
        drawLine(lineColor, Offset(a.x, a.y + box / 2), Offset(b.x, b.y - box / 2), 3f)
      }
    }
    // feedback: a small loop on the right side of the box (self-loop) or an arc from one operator to the other
    val fbColor = VoltColors.accent2
    val from = center(algo.feedbackFrom)
    val to = center(algo.feedback)
    val fbPath = Path()
    if (algo.feedbackFrom == algo.feedback) {
      val r = box * 0.32f
      val x = from.x + box / 2
      fbPath.moveTo(x, from.y - box * 0.15f)
      fbPath.cubicTo(x + r * 2.2f, from.y - box * 0.9f, x + r * 2.2f, from.y + box * 0.9f, x, from.y + box * 0.15f)
    } else {
      val x = maxOf(from.x, to.x) + box / 2
      val bulge = box * 0.5f + cellW * 0.0f
      fbPath.moveTo(from.x + box / 2, from.y)
      fbPath.cubicTo(x + bulge, from.y, x + bulge, to.y, to.x + box / 2, to.y)
    }
    drawPath(fbPath, fbColor, style = Stroke(3f))
    // arrow head at the end of the feedback path
    val tipX = if (algo.feedbackFrom == algo.feedback) from.x + box / 2 else to.x + box / 2
    val tipY = if (algo.feedbackFrom == algo.feedback) from.y + box * 0.15f else to.y
    val ah = box * 0.14f
    val head = Path().apply {
      moveTo(tipX, tipY); lineTo(tipX + ah * 1.5f, tipY - ah); lineTo(tipX + ah * 1.5f, tipY + ah); close()
    }
    drawPath(head, fbColor)

    for (op in 1..6) {
      val c = center(op)
      val on = ons[op - 1] >= 0.5f
      val carrier = op in algo.carriers
      val tl = Offset(c.x - box / 2, c.y - box / 2)
      val corner = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
      drawRoundRect(if (on) VoltColors.raised else VoltColors.background, tl, androidx.compose.ui.geometry.Size(box, box), corner)
      drawRoundRect(
        (if (carrier) VoltColors.accent else VoltColors.track).copy(alpha = if (on) 1f else 0.35f),
        tl, androidx.compose.ui.geometry.Size(box, box), corner, style = Stroke(if (carrier) 5f else 3f),
      )
      val text = measurer.measure(
        op.toString(),
        TextStyle(fontSize = (box / density * 0.45f).sp, lineHeight = (box / density * 0.5f).sp, fontWeight = FontWeight.Bold,
          color = VoltColors.text.copy(alpha = if (on) 1f else 0.3f)),
      )
      drawText(text, topLeft = Offset(c.x - text.size.width / 2f, c.y - text.size.height / 2f))
    }
    val big = measurer.measure(
      number.toString(),
      TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = VoltColors.accent.copy(alpha = 0.55f)),
    )
    drawText(big, topLeft = Offset(6.dp.toPx(), 2.dp.toPx()))
  }
}
