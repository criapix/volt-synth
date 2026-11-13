package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criapix.volt.state.SynthState
import kotlin.math.abs
import kotlin.math.min

// ---- the strip of families ---------------------------------------------------------------------------------------------

/** A non-interactive ADSR, small: the curve and a dot per note. */
@Composable
private fun MiniEnvelope(state: SynthState, prefix: String, hue: Color, modifier: Modifier) {
  val base = if (prefix == "aenv") 0 else 4
  LiveCanvas(state, modifier) { live, v, _ ->
    val pt = EnvelopeGeometry.points(v.norm("$prefix.attack"), v.norm("$prefix.decay"), v.norm("$prefix.sustain"), v.norm("$prefix.release"))
    val w = size.width; val h = size.height - 6.dp.toPx()
    fun at(x: Float, l: Float) = Offset(x * w, 3.dp.toPx() + h - l * h)
    val path = Path().apply {
      val a = at(pt.attackX, 1f); val d = at(pt.decayX, pt.sustain); val hd = at(pt.holdEndX, pt.sustain); val r = at(pt.releaseX, 0f)
      moveTo(0f, at(0f, 0f).y); lineTo(a.x, a.y); lineTo(d.x, d.y); lineTo(hd.x, hd.y); lineTo(r.x, r.y)
    }
    drawPath(path, hue.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    for (i in 0..3) if (live.stage[base + i] > 0.02f) drawCircle(hue.copy(alpha = 0.25f * live.stage[base + i]), radius = 7.dp.toPx(), center = Offset(w * (0.1f + 0.25f * i), size.height / 2))
    for (voice in live.voices) if (!voice.fm) {
      val st = if (base == 0) voice.aStage else voice.fStage
      val value = if (base == 0) voice.a else voice.f
      drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(w * (0.05f + 0.22f * st + 0.1f), 3.dp.toPx() + h - value * h))
    }
  }
}

@Composable
private fun MiniDx7(state: SynthState, hue: Color, modifier: Modifier) {
  LiveCanvas(state, modifier) { live, v, m ->
    val rates = IntArray(4) { v.int("fm.pitchenv.r${it + 1}") }; val levels = IntArray(4) { v.int("fm.pitchenv.l${it + 1}") }
    val pts = Dx7Envelope.points(rates, levels)
    val w = size.width; val h = size.height - 8.dp.toPx()
    fun at(p: Dx7Envelope.Pt) = Offset(p.x * w, 4.dp.toPx() + h - p.level * h)
    drawLine(VoltColors.track, Offset(0f, 4.dp.toPx() + h / 2), Offset(w, 4.dp.toPx() + h / 2), 1f)
    val path = Path()
    pts.forEachIndexed { i, p -> val o = at(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    drawPath(path, hue, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    val st = ((live.pitchEnv - 50f) / 50f * 48f).toInt()
    label(m, (if (st > 0) "+" else "") + "$st st", size.width - 4.dp.toPx(), 2.dp.toPx(), VoltColors.dim, 8f, mono = true, right = true)
  }
}

/** The six operators as small bars, as high as their level, lit by their output. */
@Composable
private fun OpsMini(state: SynthState, modifier: Modifier) {
  LiveCanvas(state, modifier) { live, v, _ ->
    val bw = size.width / 6
    for (n in 1..6) {
      drawRect(VoltColors.track.copy(alpha = 0.6f), Offset((n - 1) * bw + 2.dp.toPx(), 4.dp.toPx()), Size(bw - 4.dp.toPx(), size.height - 8.dp.toPx()))
      val hh = (size.height - 8.dp.toPx()) * v.real("fm.op$n.level") / 99f
      drawRect(Hues.op(n).copy(alpha = 0.35f + 0.65f * min(1f, live.op[n] * 1.4f)), Offset((n - 1) * bw + 2.dp.toPx(), size.height - 4.dp.toPx() - hh), Size(bw - 4.dp.toPx(), hh))
    }
  }
}

/** The six effects as small bars, lit by how much each one is doing. */
@Composable
private fun FxMini(state: SynthState, modifier: Modifier) {
  LiveCanvas(state, modifier) { live, v, _ ->
    val hues = listOf(Hues.dist, Hues.phaser, Hues.flanger, Hues.chorus, Hues.delay, Hues.reverb)
    val names = listOf("dist", "phaser", "flanger", "chorus", "delay", "reverb")
    val bw = size.width / 6
    for (i in 0 until 6) {
      val on = v.on("fx.${names[i]}.on")
      drawRect(if (on) hues[i].copy(alpha = 0.25f + 0.75f * min(1f, live.fxActivity[i] * 1.6f)) else VoltColors.track.copy(alpha = 0.6f), Offset(i * bw + 2.dp.toPx(), 4.dp.toPx()), Size(bw - 4.dp.toPx(), size.height - 8.dp.toPx()))
    }
  }
}

@Composable
private fun FamilyMini(family: Family, state: SynthState, modifier: Modifier) {
  when (family.id) {
    "fonte" -> OscDrawing(state, 1, family.hue, modifier)
    "filtro" -> FilterCurveView(state, family.hue, modifier)
    "amp" -> MiniEnvelope(state, "aenv", family.hue, modifier)
    "mov" -> LfoDrawing(state, 1, family.hue, modifier)
    "fx" -> FxMini(state, modifier)
    "arp" -> ArpDrawing(state, family.hue, modifier)
    "algo", "ops" -> OpsMini(state, modifier)
    "pitch" -> MiniDx7(state, family.hue, modifier)
  }
}

/** One tile per family, each with a live picture of what is in it. [flash] lights a tile a MIDI controller just moved something in. */
@Composable
fun FamilyStrip(families: List<Family>, selected: String, flash: String?, state: SynthState, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    families.forEach { f ->
      val on = f.id == selected
      val shape = RoundedCornerShape(10.dp)
      Box(
        Modifier.weight(1f).fillMaxHeight()
          .background(Hues.mix(Color(0xFF11131A), f.hue, 0.08f), shape)
          .border(if (on || f.id == flash) 2.dp else 1.dp, if (on || f.id == flash) f.hue.copy(alpha = if (on) 1f else 0.7f) else VoltColors.raised, shape)
          .clickable { onSelect(f.id) },
      ) {
        CompositionLocalProvider(LocalMiniDrawing provides true) {
          FamilyMini(f, state, Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp))
        }
        Text(
          f.title, fontSize = 9.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = VoltColors.text, maxLines = 1,
          modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 3.dp),
        )
      }
    }
  }
}

// ---- operators ---------------------------------------------------------------------------------------------------------

/** An operator block: number, role in this algorithm, frequency, a small envelope with the note's position, and its level. */
@Composable
private fun OperatorTile(n: Int, selected: Boolean, state: SynthState, onClick: () -> Unit, modifier: Modifier) {
  val hue = Hues.op(n)
  val shape = RoundedCornerShape(10.dp)
  Box(modifier.background(Hues.mix(Color(0xFF12141B), hue, 0.1f), shape).border(if (selected) 2.dp else 1.dp, if (selected) hue else VoltColors.raised, shape).clickable(onClick = onClick)) {
    LiveCanvas(state, Modifier.fillMaxSize()) { live, v, m ->
      val on = v.on("fm.op$n.on")
      val lvl = live.op[n]
      if (on && lvl > 0.02f) drawRect(hue.copy(alpha = 0.18f * min(1f, lvl * 1.5f)), Offset(0f, 0f), size)
      val carrier = live.algo?.carriers?.contains(n) ?: false
      val l = FloatArray(4) { v.real("fm.op$n.l${it + 1}") / 99f }
      val x0 = size.width * 0.45f; val ew = size.width * 0.5f
      val path = Path().apply {
        moveTo(x0, size.height - 6.dp.toPx() - l[3] * (size.height - 14.dp.toPx()))
        for (i in 0..2) lineTo(x0 + ew * (i + 1) / 4f, size.height - 6.dp.toPx() - l[i] * (size.height - 14.dp.toPx()))
        lineTo(x0 + ew, size.height - 6.dp.toPx() - l[3] * (size.height - 14.dp.toPx()))
      }
      drawPath(path, hue.copy(alpha = if (on) 0.8f else 0.3f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
      val st = live.opStage[n]
      if (st in 1..4 && live.voices.isNotEmpty()) {
        val i = if (st == 4) 4 else st
        drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(x0 + ew * i / 4f, size.height - 6.dp.toPx() - (if (i == 4) l[3] else l[i - 1]) * (size.height - 14.dp.toPx())))
      }
      label(m, "OP$n", 8.dp.toPx(), 3.dp.toPx(), if (on) hue else VoltColors.dim, 13f, bold = true)
      label(m, if (carrier) "PORTADORA" else "MODULADORA", 8.dp.toPx(), 22.dp.toPx(), if (carrier && on) hue else VoltColors.dim, 7f, bold = true)
      label(m, FmFrequency.format(v.on("fm.op$n.mode"), v.int("fm.op$n.coarse"), v.int("fm.op$n.fine")), 8.dp.toPx(), size.height - 15.dp.toPx(), VoltColors.text, 9f, mono = true)
      drawRect(VoltColors.track, Offset(size.width - 6.dp.toPx(), 6.dp.toPx()), Size(3.dp.toPx(), size.height - 12.dp.toPx()))
      val lh = (size.height - 12.dp.toPx()) * v.real("fm.op$n.level") / 99f
      drawRect(hue, Offset(size.width - 6.dp.toPx(), size.height - 6.dp.toPx() - lh), Size(3.dp.toPx(), lh))
    }
  }
}

@Composable
private fun OperatorsPage(state: SynthState, op: Int, onOp: (Int) -> Unit, onStep: () -> Unit, cards: List<Card>, modifier: Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      for (n in 1..6) OperatorTile(n, n == op, state, { onOp(n); onStep() }, Modifier.weight(1f).fillMaxHeight())
    }
    CardRow(cards, state, onStep, Modifier.weight(1f).fillMaxWidth())
  }
}

// ---- pages ------------------------------------------------------------------------------------------------------------

/** The cards side by side, sharing the width in proportion to what each likes; scrolling sideways when they do not fit. */
@Composable
private fun CardRow(cards: List<Card>, state: SynthState, onStep: () -> Unit, modifier: Modifier, onOperator: (Int) -> Unit = {}) {
  BoxWithConstraints(modifier) {
    val pref = cards.map { preferredWidth(it) }
    val gaps = 8f * (cards.size - 1)
    if (pref.sum() + gaps <= maxWidth.value) {
      Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.forEachIndexed { i, c -> CardView(c, state, onStep, Modifier.weight(pref[i]).fillMaxHeight(), onOperator) }
      }
    } else {
      Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.forEachIndexed { i, c -> CardView(c, state, onStep, Modifier.width(pref[i].dp).fillMaxHeight(), onOperator) }
      }
    }
  }
}

/**
 * The PAINEL screen: the strip of families on top, the chosen family's cards below. Every parameter of the engine is on
 * one card, at most two touches away. [flash] is the family a MIDI controller just changed something in.
 */
@Composable
fun PainelScreen(
  state: SynthState, fm: Boolean, selected: String, onSelect: (String) -> Unit, operator: Int, onOperator: (Int) -> Unit,
  flash: String?, onStep: () -> Unit, modifier: Modifier = Modifier,
) {
  val families = Panel.familiesFor(fm)
  val family = families.firstOrNull { it.id == selected } ?: families.first()
  Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FamilyStrip(families, family.id, flash, state, { onSelect(it); onStep() }, Modifier.padding(top = 6.dp))
    Box(Modifier.weight(1f).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
      when (family.id) {
        "ops" -> OperatorsPage(state, operator, onOperator, onStep, Panel.operatorCardsOf(operator), Modifier.fillMaxSize())
        "fx" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          family.cards.chunked(3).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              row.forEach { CardView(it, state, onStep, Modifier.weight(1f).fillMaxHeight()) }
            }
          }
        }
        else -> CardRow(family.cards, state, onStep, Modifier.fillMaxSize()) { op -> onOperator(op); onSelect("ops") }
      }
    }
  }
}
