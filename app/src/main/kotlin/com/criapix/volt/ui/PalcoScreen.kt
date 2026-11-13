package com.criapix.volt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.state.SynthState
import kotlin.math.min
import kotlin.math.sin

/** The colour a note lights its key with: one hue per pitch class, so a chord reads as colours. */
fun noteHue(note: Int): Color = Color.hsv(((note % 12) * 30f), 0.85f, 0.95f)

/**
 * The on-screen keyboard (15 white keys from [base]): the height of the touch sets the velocity, several fingers play
 * several notes, and each key lights with the loudness of the note it plays, whoever played it (this keyboard, MIDI or the arpeggiator).
 */
@Composable
fun PerformanceKeyboard(state: SynthState, base: Int, dark: Boolean, modifier: Modifier = Modifier) {
  val live = LocalLive.current
  val frame = LocalFrame.current
  var pressed by remember { mutableStateOf(emptySet<Int>()) }
  Canvas(
    modifier.pointerInput(base) {
      val playing = HashMap<PointerId, Int>()
      try {
        awaitPointerEventScope {
          while (true) {
            val ev = awaitPointerEvent()
            for (c in ev.changes) {
              val w = size.width.toFloat(); val h = size.height.toFloat()
              val note = if (c.pressed) KeyboardLayout.noteAt(c.position.x, c.position.y, w, h, base) else null
              val old = playing[c.id]
              if (note != old) {
                if (old != null) { state.noteOff(old); playing.remove(c.id) }
                if (note != null) { state.noteOn(note, KeyboardLayout.velocity(c.position.y, h)); playing[c.id] = note }
              }
              c.consume()
            }
            pressed = playing.values.toSet()
          }
        }
      } finally {
        playing.values.forEach { state.noteOff(it) }  // octave change or the screen left while touching: no stuck notes
        pressed = emptySet()
      }
    },
  ) {
    frame?.value
    val kw = size.width / KeyboardLayout.WHITES
    val whiteBase = if (dark) Color(0xFF1C1D27) else Color(0xFFEDEDED)
    val blackBase = if (dark) Color(0xFF09090D) else Color(0xFF111111)
    fun lit(note: Int): Float = if (note in pressed) 1f else (live?.noteLevels?.getOrNull(note) ?: 0f)
    for (i in 0 until KeyboardLayout.WHITES) {
      val note = KeyboardLayout.noteAt(i * kw + kw / 2, size.height - 1f, size.width, size.height, base) ?: continue
      val l = lit(note)
      drawRoundRect(Hues.mix(whiteBase, noteHue(note), l * 0.9f), Offset(i * kw + 1f, 0f), Size(kw - 2f, size.height), CornerRadius(0f, 0f))
      if (l > 0.05f) drawRect(noteHue(note).copy(alpha = 0.25f * l), Offset(i * kw - 2f, size.height - 8.dp.toPx()), Size(kw + 4f, 8.dp.toPx()))
    }
    for ((left, note) in KeyboardLayout.blackKeys(base)) {
      val l = lit(note)
      drawRect(Hues.mix(blackBase, noteHue(note), l * 0.9f), Offset(left * kw, 0f), Size(kw * 0.6f, size.height * 0.6f))
    }
  }
}

/** A vertical strip: pitch bend (springs back to the middle) or the modulation wheel (stays where it is left). */
@Composable
private fun WheelStrip(bend: Boolean, state: SynthState, modifier: Modifier) {
  val hue = if (bend) Color(0xFFFF7FB0) else Color(0xFFA98BFF)
  val live = LocalLive.current
  val frame = LocalFrame.current
  DisposableEffect(Unit) { onDispose { if (bend) state.pitchBend(0f) } }
  Canvas(
    modifier.background(VoltColors.background, RoundedCornerShape(9.dp)).border(1.dp, VoltColors.raised, RoundedCornerShape(9.dp)).pointerInput(bend) {
      awaitPointerEventScope {
        while (true) {
          val ev = awaitPointerEvent()
          val c = ev.changes.firstOrNull() ?: continue
          val y = (c.position.y / size.height).coerceIn(0f, 1f)
          if (c.pressed) { if (bend) state.pitchBend(1f - 2f * y) else state.modWheel(1f - y) }
          else if (bend) state.pitchBend(0f)
          c.consume()
        }
      }
    },
  ) {
    frame?.value
    val v = if (bend) (live?.bend ?: 0f) else (live?.wheel ?: 0f)
    if (bend) {
      val y = size.height / 2 - v * (size.height / 2 - 6.dp.toPx())
      drawRect(hue.copy(alpha = 0.25f), Offset(4.dp.toPx(), minOf(y, size.height / 2)), Size(size.width - 8.dp.toPx(), kotlin.math.abs(y - size.height / 2)))
      drawRect(hue, Offset(4.dp.toPx(), y - 2.dp.toPx()), Size(size.width - 8.dp.toPx(), 4.dp.toPx()))
      drawLine(VoltColors.dim, Offset(8.dp.toPx(), size.height / 2), Offset(size.width - 8.dp.toPx(), size.height / 2), 1f)
    } else {
      val hh = (size.height - 8.dp.toPx()) * v
      drawRect(hue.copy(alpha = 0.3f + 0.5f * v), Offset(4.dp.toPx(), size.height - 4.dp.toPx() - hh), Size(size.width - 8.dp.toPx(), hh))
      drawRect(hue, Offset(4.dp.toPx(), size.height - 6.dp.toPx() - hh), Size(size.width - 8.dp.toPx(), 3.dp.toPx()))
    }
  }
}

/** One of the performance buttons; [led] (0..1) breathes with the sound when it is on. */
@Composable
private fun PerfButton(label: String, on: Boolean, hue: Color, modifier: Modifier, led: ((Live) -> Float)? = null, onClick: () -> Unit) {
  val live = LocalLive.current
  val frame = LocalFrame.current
  val shape = RoundedCornerShape(10.dp)
  Column(
    modifier.background(VoltColors.panel, shape).border(1.dp, if (on) hue else VoltColors.raised, shape).clickable(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
  ) {
    Text(label, fontSize = 9.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = if (on) hue else VoltColors.text)
    if (led != null) Canvas(Modifier.padding(top = 3.dp).width(28.dp).height(5.dp)) {
      frame?.value
      val l = if (on && live != null) led(live).coerceIn(0f, 1f) else 0f
      drawRoundRect(if (on) Hues.mix(VoltColors.raised, hue, 0.3f + 0.7f * l) else VoltColors.track, cornerRadius = CornerRadius(2.dp.toPx()))
    }
  }
}

/** The sound as a picture: its waveform, coloured by brightness and drive, with echoes, chorus copies and the reverb halo around it. */
@Composable
fun PortraitView(state: SynthState, modifier: Modifier = Modifier) {
  val buf = remember { FloatArray(240) }
  LiveCanvas(state, modifier) { live, v, m ->
    val w = size.width; val h = size.height
    val c = Offset(w / 2, h / 2)
    drawRoundRect(Color(0xFF0D0C15), cornerRadius = CornerRadius(16.dp.toPx()))
    val dec = v.norm("fx.reverb.decay")
    val rc = Hues.mix(Hues.reverb, Color(0xFF3B2F66), v.real("fx.reverb.damp") * 0.8f)
    val radius = maxOf(w, h) * (0.22f + 0.5f * dec)
    drawCircle(Brush.radialGradient(0f to rc.copy(alpha = (0.45f * live.halo + 0.03f).coerceIn(0f, 1f)), 1f to rc.copy(alpha = 0f), center = c, radius = radius), radius = radius, center = c)

    var col: Color
    if (live.fm) {
      var mod = 0f
      val carriers = live.algo?.carriers ?: setOf(1)
      for (o in 1..6) if (o !in carriers) mod = maxOf(mod, live.op[o])
      val bright = (mod * 1.6f).coerceIn(0f, 1f)
      col = Hues.mix(Color(0xFF2E8F88), Hues.mix(Hues.algorithm, Color(0xFFF2FFFD), (bright * 1.5f - 0.6f).coerceIn(0f, 1f)), (bright * 1.6f).coerceIn(0f, 1f))
    } else {
      val p = state.params["filter.cutoff"]!!
      val bright = v.norm("filter.cutoff") * 0.4f + p.toNorm(live.cutoff) * 0.6f
      col = Hues.mix(Color(0xFFA2482C), Hues.mix(Color(0xFFF4B860), Color(0xFFFFF6E2), (bright * 1.6f - 0.8f).coerceIn(0f, 1f)), (bright * 1.5f).coerceIn(0f, 1f))
      col = Hues.mix(col, Color(0xFFFF4B3A), v.real("mix.drive") * 0.6f)
    }
    if (v.on("fx.dist.on")) col = Hues.mix(col, Hues.dist, v.real("fx.dist.drive") * v.real("fx.dist.mix") * 0.8f)

    Scope.fill(live, v, buf, 2.5f)
    val wobble = (if (live.fm) live.fmLfoValue * v.real("fm.lfo.pmd") / 99f else live.lfo1 * v.real("lfo1.cutoff")) * 14.dp.toPx()
    fun line(dx: Float, yo: Float, alpha: Float, width: Float, color: Color, glow: Float = 0f) {
      val path = Path()
      for (i in buf.indices) {
        val x = i / (buf.size - 1f) * w * 0.92f + w * 0.04f + dx
        val y = h / 2 + yo - buf[i] * h * 0.34f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      if (glow > 0.02f) drawPath(path, color.copy(alpha = (0.2f * glow).coerceIn(0f, 1f)), style = Stroke(width + 8.dp.toPx() * glow, cap = StrokeCap.Round))
      drawPath(path, color.copy(alpha = alpha.coerceIn(0f, 1f)), style = Stroke(width, cap = StrokeCap.Round))
    }
    if (v.on("fx.delay.on")) {
      val dt = v.real("fx.delay.time"); val fb = v.real("fx.delay.feedback"); val dm = v.real("fx.delay.mix")
      for (k in 4 downTo 1) {
        val a = Math.pow(fb.toDouble(), (k - 1).toDouble()).toFloat() * dm * 1.3f * (0.3f + 0.7f * min(1f, live.echo * 2f + 0.2f))
        if (a > 0.02f) line(k * dt * 60.dp.toPx(), wobble, a * 0.7f, 1.2.dp.toPx(), Hues.delay)
      }
    }
    if (v.on("fx.chorus.on")) {
      val ch = v.real("fx.chorus.mix")
      line(sin(Waves.TAU * live.chorusPhase1) * 6.dp.toPx() * ch, wobble + sin(live.t.toFloat() * 3.1f) * 4.dp.toPx() * ch, 0.35f * ch, 1.2.dp.toPx(), Hues.chorus)
    }
    if (v.on("fx.phaser.on") || v.on("fx.flanger.on")) {
      val phaser = v.on("fx.phaser.on")
      val swing = if (phaser) live.phaserSwing else live.flangerSwing
      drawRect((if (phaser) Hues.phaser else Hues.flanger).copy(alpha = 0.08f + 0.12f * kotlin.math.abs(swing)), Offset(w * (0.5f + 0.4f * swing) - 30.dp.toPx(), 0f), Size(60.dp.toPx(), h))
    }
    val strength = maxOf(live.level, live.amp * 0.8f)
    line(0f, wobble, 0.35f + 0.65f * min(1f, live.amp + 0.15f), 1.6.dp.toPx() + 3.4.dp.toPx() * strength, col, glow = strength)

    label(m, if (live.fm) "ALG ${v.int("fm.algorithm")} · ${live.voices.size} vozes" else "corte ${if (live.cutoff >= 1000f) String.format(java.util.Locale.US, "%.1f kHz", live.cutoff / 1000f) else "${live.cutoff.toInt()} Hz"} · ${live.voices.size} vozes", 14.dp.toPx(), h - 22.dp.toPx(), VoltColors.dim, 10f, mono = true)
    var x = 14.dp.toPx()
    val tags = listOf("dist" to "DIST", "phaser" to "PHASER", "flanger" to "FLANGER", "chorus" to "CHORUS", "delay" to "DELAY", "reverb" to "REVERB")
    val hues = listOf(Hues.dist, Hues.phaser, Hues.flanger, Hues.chorus, Hues.delay, Hues.reverb)
    tags.forEachIndexed { i, (key, text) ->
      if (!v.on("fx.$key.on")) return@forEachIndexed
      val tw = text.length * 6.2.dp.toPx() + 10.dp.toPx()
      drawRoundRect(hues[i].copy(alpha = 0.2f + 0.7f * min(1f, live.fxActivity[i] * 1.5f)), Offset(x, 10.dp.toPx()), Size(tw, 16.dp.toPx()), CornerRadius(4.dp.toPx()))
      label(m, text, x + 5.dp.toPx(), 12.dp.toPx(), VoltColors.text, 9f, bold = true)
      x += tw + 5.dp.toPx()
    }
    if (v.on("arp.on")) label(m, "ARP ${v.int("arp.bpm")}", w - 12.dp.toPx(), 12.dp.toPx(), if (live.arpPulse > 0f) Hues.arp else VoltColors.dim, 9f, bold = true, right = true)
  }
}

@Composable
private fun MacroKnob(def: MacroDef, macros: Macros, state: SynthState, size: Dp, onStep: () -> Unit) {
  val live = LocalLive.current
  val norm = if (def.relative) macros.position(def)
  else { val p = state.params[def.keys[0]]!!; val n by state.value(p.id).collectAsStateWithLifecycle(); n }
  val feedback = remember(def, live) { live?.let { macros.feedback(def, it) } }
  KnobCore(
    norm = norm, label = def.label, text = macros.text(def), size = size, steps = 0,
    startNorm = { macros.position(def) }, onSet = { macros.set(def, it) }, onStep = onStep,
    bipolar = def.relative, hue = def.hue, feedback = feedback, onStart = { macros.begin(def) }, onReset = { macros.resetToDefault(def) },
    minWidth = size + 14.dp,
  )
}

/**
 * The PALCO screen, for playing: eight macros around the picture of the sound, and below them the performance controls
 * (arpeggiator, sustain, octave, bend, modulation) and the keyboard.
 */
@Composable
fun PalcoScreen(
  state: SynthState, macros: Macros, fm: Boolean, base: Int, onBase: (Int) -> Unit, sustain: Boolean, onSustain: (Boolean) -> Unit,
  onStep: () -> Unit, modifier: Modifier = Modifier,
) {
  val defs = MacroDefs.of(fm)
  val arpNorm by state.value(state.params["arp.on"]!!.id).collectAsStateWithLifecycle()
  Column(modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF171523), Color(0xFF0B0C11)), center = Offset(450f, 150f), radius = 900f))) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
      val knob = ((maxHeight.value / 2) - 30f).coerceIn(40f, 66f).dp
      val column = (knob + 14.dp) * 2
      Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MacroColumn(defs.subList(0, 4), macros, state, knob, onStep, Modifier.width(column).fillMaxHeight())
        PortraitView(state, Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)))
        MacroColumn(defs.subList(4, 8), macros, state, knob, onStep, Modifier.width(column).fillMaxHeight())
      }
    }
    Row(Modifier.fillMaxWidth().height(104.dp).padding(start = 10.dp, end = 10.dp, bottom = 8.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          PerfButton("ARP", arpNorm >= 0.5f, Hues.arp, Modifier.weight(1f).fillMaxHeight(), led = { it.arpPulse }) {
            val p = state.params["arp.on"]!!; state.set(p.id, if (arpNorm >= 0.5f) 0f else 1f); onStep()
          }
          PerfButton("SEGURAR", sustain, Color(0xFFFFC66D), Modifier.weight(1f).fillMaxHeight()) { onSustain(!sustain); onStep() }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          PerfButton("OITAVA −", false, Hues.mixer, Modifier.weight(1f).fillMaxHeight()) { if (base > 24) onBase(base - 12); onStep() }
          PerfButton("OITAVA +", false, Hues.mixer, Modifier.weight(1f).fillMaxHeight()) { if (base < 84) onBase(base + 12); onStep() }
        }
      }
      Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        WheelStrip(true, state, Modifier.width(36.dp).fillMaxHeight())
        WheelStrip(false, state, Modifier.width(36.dp).fillMaxHeight())
      }
      PerformanceKeyboard(state, base, dark = true, modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)))
    }
  }
}

@Composable
private fun MacroColumn(defs: List<MacroDef>, macros: Macros, state: SynthState, knob: Dp, onStep: () -> Unit, modifier: Modifier) {
  Column(modifier, verticalArrangement = Arrangement.SpaceEvenly) {
    defs.chunked(2).forEach { row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { row.forEach { MacroKnob(it, macros, state, knob, onStep) } }
    }
  }
}
