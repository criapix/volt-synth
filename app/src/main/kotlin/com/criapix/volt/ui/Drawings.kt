package com.criapix.volt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
