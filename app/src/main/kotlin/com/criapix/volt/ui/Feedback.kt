package com.criapix.volt.ui

import com.criapix.volt.state.SynthState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How a control shows what it does to the sound: the kind of picture that suits the control. Light for levels,
 * a pulse at the rate of a modulator, a ghost pointer where a modulated value really is, a ring for resonance,
 * a warming colour for drive, a tiny wave or scatter of dots inside the knob for waveform-like controls.
 */
enum class Fb {
  LEVEL, DENSITY, BEAT, PULSE_WIDTH, SUB, NOISE, HEAT, METER,
  CUTOFF, RESONANCE, ENV_AMOUNT, KEY_TRACK, VELOCITY, HIGH_PASS, STAGE,
  LFO_RATE, LFO_DEPTH, LFO_DELAY, WHEEL, MOD_ENV, MOD_OSC, UNISON, SPREAD, GLIDE, DRIFT, BEND,
  FX_RATE, FX_DEPTH, FX_FEEDBACK, FX_MIX, TONE, ECHO, HALO, DAMP, ARP_STEP,
  FM_FEEDBACK, ALGORITHM, OP_LEVEL, OP_STAGE, OP_RATIO, OP_BEAT, KEY_SCALE, PITCH_STAGE, FM_LFO_RATE, FM_LFO_DEPTH, CONTROLLER, TRANSPOSE,
}

/** A control's feedback: its [fb] plus the names of the parameters it reads (built once, so a frame allocates nothing). */
class Kind(val fb: Fb, val a: Int = 0, val b: Int = 0, val s1: String = "", val s2: String = "", val s3: String = "")

/** What one control draws this frame. Reused between frames. */
class Fx {
  var glow = 0f
  var ghost = Float.NaN
  var ring = 0f
  /** 0..1 towards the "hot" colour (drive). */
  var hot = 0f
  var pulse = Float.NaN
  var jit = 0f
  /** Reverb halo around the knob. */
  var halo = 0f
  var inner = Inner.NONE
  var cycles = 1.5f
  var phase = 0f
  var alpha = 0.5f
  var amplitude = 1f
  var pw = 0.5f
  var count = 0
  var spread = 0f

  fun reset() {
    glow = 0f; ghost = Float.NaN; ring = 0f; hot = 0f; pulse = Float.NaN; jit = 0f; halo = 0f
    inner = Inner.NONE; cycles = 1.5f; phase = 0f; alpha = 0.5f; amplitude = 1f; pw = 0.5f; count = 0; spread = 0f
  }
}

/** A small picture drawn in the middle of a knob in place of its pointer. */
enum class Inner { NONE, WAVE, PULSE, SQUARE, GRAIN, FAN, SPREAD }

object Feedback {
  private val RATE_OR_SCALE = Regex("""fm\.op(\d)\.(r|l)(\d)""")

  /** The feedback of a knob parameter, or null when the control has none (selectors and switches draw their own). */
  fun of(name: String): Kind? {
    RATE_OR_SCALE.matchEntire(name)?.let { m -> return Kind(Fb.OP_STAGE, m.groupValues[1].toInt(), m.groupValues[3].toInt()) }
    Regex("""fm\.op(\d)\.(.+)""").matchEntire(name)?.let { m ->
      val n = m.groupValues[1].toInt()
      val k = "fm.op$n."
      return when (m.groupValues[2]) {
        "level" -> Kind(Fb.OP_LEVEL, n)
        "vel" -> Kind(Fb.VELOCITY, s1 = name)
        "coarse", "fine" -> Kind(Fb.OP_RATIO, n, s1 = k + "mode", s2 = k + "coarse", s3 = k + "fine")
        "detune" -> Kind(Fb.OP_BEAT, n, s1 = name)
        "ams" -> Kind(Fb.FM_LFO_DEPTH, s1 = name)
        else -> Kind(Fb.KEY_SCALE, n)  // rate.scale and the kls.* knobs
      }
    }
    Regex("""osc(\d)\.(.+)""").matchEntire(name)?.let { m ->
      val n = m.groupValues[1].toInt()
      return when (m.groupValues[2]) {
        "octave", "semi" -> Kind(Fb.DENSITY, n, s1 = "osc$n.octave", s2 = "osc$n.semi")
        "fine" -> Kind(Fb.BEAT, n, s1 = name, s2 = "osc$n.level")
        "pw" -> Kind(Fb.PULSE_WIDTH, n, s1 = "osc$n.wave", s2 = name)
        "level" -> Kind(Fb.LEVEL, s1 = name)
        else -> null
      }
    }
    Regex("""lfo(\d)\.(.+)""").matchEntire(name)?.let { m ->
      val n = m.groupValues[1].toInt()
      return when (m.groupValues[2]) {
        "rate" -> Kind(Fb.LFO_RATE, n)
        "delay" -> Kind(Fb.LFO_DELAY, n)
        "wheel" -> Kind(Fb.WHEEL, s1 = name)
        else -> Kind(Fb.LFO_DEPTH, n, s1 = name)
      }
    }
    Regex("""fx\.(phaser|flanger)\.(.+)""").matchEntire(name)?.let { m ->
      val idx = if (m.groupValues[1] == "phaser") Live.PHASER else Live.FLANGER
      return when (m.groupValues[2]) {
        "rate" -> Kind(Fb.FX_RATE, idx)
        "depth" -> Kind(Fb.FX_DEPTH, idx, s1 = name)
        "feedback" -> Kind(Fb.FX_FEEDBACK, idx, s1 = name)
        else -> Kind(Fb.FX_MIX, idx)
      }
    }
    Regex("""fm\.(wheel|at|breath)\.range""").matchEntire(name)?.let { m -> return Kind(Fb.CONTROLLER, listOf("at", "wheel", "breath").indexOf(m.groupValues[1]), s1 = name) }
    return when (name) {
      "mix.sub" -> Kind(Fb.SUB, s1 = name)
      "mix.noise" -> Kind(Fb.NOISE, s1 = name)
      "mix.drive" -> Kind(Fb.HEAT, s1 = name)
      "master.volume" -> Kind(Fb.METER)
      "hpf.cutoff" -> Kind(Fb.HIGH_PASS, s1 = name)
      "filter.cutoff" -> Kind(Fb.CUTOFF, s1 = name)
      "filter.reso" -> Kind(Fb.RESONANCE, s1 = name)
      "filter.env" -> Kind(Fb.ENV_AMOUNT, s1 = name)
      "filter.keytrack" -> Kind(Fb.KEY_TRACK, s1 = name)
      "filter.vel", "aenv.vel" -> Kind(Fb.VELOCITY, s1 = name)
      "fenv.attack" -> Kind(Fb.STAGE, 4 + Live.A)
      "fenv.decay" -> Kind(Fb.STAGE, 4 + Live.D)
      "fenv.sustain" -> Kind(Fb.STAGE, 4 + Live.S)
      "fenv.release" -> Kind(Fb.STAGE, 4 + Live.R)
      "aenv.attack" -> Kind(Fb.STAGE, Live.A)
      "aenv.decay" -> Kind(Fb.STAGE, Live.D)
      "aenv.sustain" -> Kind(Fb.STAGE, Live.S)
      "aenv.release" -> Kind(Fb.STAGE, Live.R)
      "pmod.fenv.pitch", "pmod.fenv.pw" -> Kind(Fb.MOD_ENV, s1 = name)
      "pmod.osc2.pitch", "pmod.osc2.pw" -> Kind(Fb.MOD_OSC, s1 = name)
      "voice.unison", "voice.detune" -> Kind(Fb.UNISON, s1 = "voice.mode", s2 = "voice.unison", s3 = "voice.detune")
      "voice.spread" -> Kind(Fb.SPREAD, s1 = "voice.mode", s2 = name)
      "voice.glide" -> Kind(Fb.GLIDE)
      "voice.drift" -> Kind(Fb.DRIFT, s1 = name)
      "voice.bend" -> Kind(Fb.BEND, s1 = name)
      "fx.dist.drive" -> Kind(Fb.HEAT, 1, s1 = name)
      "fx.dist.tone" -> Kind(Fb.TONE, s1 = name)
      "fx.dist.mix" -> Kind(Fb.FX_MIX, Live.DIST)
      "fx.chorus.mix" -> Kind(Fb.FX_MIX, Live.CHORUS)
      "fx.delay.time", "fx.delay.feedback", "fx.delay.mix" -> Kind(Fb.ECHO)
      "fx.reverb.decay", "fx.reverb.mix" -> Kind(Fb.HALO, s1 = "fx.reverb.decay")
      "fx.reverb.damp" -> Kind(Fb.DAMP, s1 = name)
      "arp.octaves", "arp.bpm", "arp.gate" -> Kind(Fb.ARP_STEP)
      "fm.algorithm" -> Kind(Fb.ALGORITHM)
      "fm.feedback" -> Kind(Fb.FM_FEEDBACK, s1 = name)
      "fm.transpose" -> Kind(Fb.TRANSPOSE, s1 = name)
      "fm.lfo.speed", "fm.lfo.delay" -> Kind(Fb.FM_LFO_RATE)
      "fm.lfo.pms", "fm.lfo.pmd", "fm.lfo.amd" -> Kind(Fb.FM_LFO_DEPTH, s1 = name)
      else -> if (name.startsWith("fm.pitchenv.")) Kind(Fb.PITCH_STAGE, name.last().digitToInt()) else null
    }
  }

  /** Fills [out] with what [kind] draws now. [name] is the parameter the control belongs to. */
  fun eval(kind: Kind, name: String, state: SynthState, live: Live, out: Fx) {
    out.reset()
    fun norm(n: String): Float { val p = state.params[n]!!; return state.norm(p.id) }
    fun real(n: String): Float { val p = state.params[n]!!; return p.toReal(state.norm(p.id)) }
    val amp = live.amp
    val t = live.t.toFloat()
    when (kind.fb) {
      Fb.LEVEL -> out.glow = norm(kind.s1) * min(1f, amp * 1.1f)
      Fb.DENSITY -> {
        out.glow = amp * 0.3f
        out.inner = Inner.WAVE; out.cycles = 1.5f * 2f.pow(real(kind.s1) + real(kind.s2) / 12f); out.phase = t * 4f; out.alpha = 0.45f + 0.5f * amp
      }
      Fb.BEAT -> {
        val hz = 220f * (2f.pow(abs(real(kind.s1)) / 1200f) - 1f)
        val b = 0.5f + 0.5f * cos(Waves.TAU * hz * t)
        out.glow = amp * max(0.2f, real(kind.s2)) * (0.15f + 0.85f * b)
        out.inner = Inner.WAVE; out.cycles = 1.5f; out.alpha = 0.35f + 0.6f * amp; out.amplitude = 0.25f + 0.75f * b
      }
      Fb.PULSE_WIDTH -> {
        val pulse = real(kind.s1).toInt() == 1
        out.glow = if (pulse) amp * 0.4f else 0f
        out.inner = Inner.PULSE; out.pw = real(kind.s2); out.alpha = if (pulse) 0.5f + 0.5f * amp else 0.3f
      }
      Fb.SUB -> { out.glow = norm(kind.s1) * amp; out.inner = Inner.SQUARE; out.cycles = 0.75f; out.phase = t * 2f; out.alpha = 0.35f + 0.6f * amp }
      Fb.NOISE -> { val n = norm(kind.s1); out.glow = n * amp * 0.6f; out.inner = Inner.GRAIN; out.count = (n * 40f * (0.25f + amp)).roundToInt() }
      Fb.HEAT -> {
        val h = norm(kind.s1)
        val live2 = h * (if (kind.a == 1) live.fxActivity[Live.DIST] * 1.4f else amp)
        out.glow = live2 * 0.9f; out.hot = min(1f, h * 1.2f); out.jit = live2
      }
      Fb.METER -> { out.glow = live.level * 0.7f; out.ghost = live.level }
      Fb.CUTOFF -> {
        val p = state.params[kind.s1]!!
        out.glow = amp * 0.35f; out.ghost = p.toNorm(live.cutoff)
      }
      Fb.RESONANCE -> {
        val r = real(kind.s1)
        out.glow = r * amp * 0.5f; out.ring = r * amp * (0.5f + 0.5f * sin(Waves.TAU * (3f + r * 9f) * t))
      }
      Fb.ENV_AMOUNT -> { val e = real(kind.s1); out.glow = abs(e) * live.fenv * 0.7f; out.ghost = 0.5f + 0.5f * e * live.fenv }
      Fb.KEY_TRACK -> { out.glow = live.noteFlash * norm(kind.s1) * 0.8f; live.lead?.let { out.ghost = ((it.note - 21) / 88f).coerceIn(0f, 1f) } }
      Fb.VELOCITY -> out.glow = live.velFlash * norm(kind.s1)
      Fb.HIGH_PASS -> out.glow = amp * norm(kind.s1) * 0.6f
      Fb.STAGE -> out.glow = live.stage[kind.a]
      Fb.LFO_RATE -> { val v = if (kind.a == 1) live.lfo1 else live.lfo2; out.glow = 0.15f + 0.55f * (0.5f + 0.5f * v); out.pulse = 0.5f + 0.5f * v }
      Fb.LFO_DEPTH -> {
        val v = if (kind.a == 1) live.lfo1 else live.lfo2
        val d = norm(kind.s1)
        out.glow = d * (0.5f + 0.5f * v) * 0.8f; out.ghost = d * (0.5f + 0.5f * v)
      }
      Fb.LFO_DELAY -> {
        val f = if (kind.a == 1) live.lfoFade1 else live.lfoFade2
        if (f < 1f) { out.glow = 0.7f; out.ring = f }
      }
      Fb.WHEEL -> { out.glow = live.wheel * norm(kind.s1); out.ghost = live.wheel }
      Fb.MOD_ENV -> out.glow = abs(real(kind.s1)) * live.fenv
      Fb.MOD_OSC -> out.glow = real(kind.s1) * amp * max(0.2f, real("osc2.level"))
      Fb.UNISON -> {
        val uni = real(kind.s1).toInt() == 3
        out.glow = if (uni) amp * 0.45f else 0f
        out.inner = Inner.FAN; out.count = if (uni) real(kind.s2).toInt() else 1; out.spread = norm(kind.s3); out.alpha = 0.35f + 0.55f * amp
      }
      Fb.SPREAD -> {
        val uni = real(kind.s1).toInt() == 3
        val s = norm(kind.s2)
        out.glow = if (uni) amp * s * 0.6f else 0f
        out.inner = Inner.SPREAD; out.spread = s; out.alpha = 0.4f + 0.5f * amp
      }
      Fb.GLIDE -> out.glow = live.glide
      Fb.DRIFT -> { val d = norm(kind.s1); out.glow = amp * d * 0.5f; out.jit = d * amp * 0.5f }
      Fb.BEND -> { out.glow = abs(live.bend) * 0.8f; out.ghost = 0.5f + 0.5f * live.bend }
      Fb.FX_RATE -> { val s = if (kind.a == Live.PHASER) live.phaserSwing else live.flangerSwing; out.glow = live.fxActivity[kind.a] * (0.5f + 0.5f * s); out.pulse = 0.5f + 0.5f * s }
      Fb.FX_DEPTH -> { val s = if (kind.a == Live.PHASER) live.phaserSwing else live.flangerSwing; out.glow = live.fxActivity[kind.a] * 0.6f; out.ghost = norm(kind.s1) * (0.5f + 0.5f * s) }
      Fb.FX_FEEDBACK -> { out.glow = live.fxActivity[kind.a] * norm(kind.s1); out.ring = live.fxActivity[kind.a] * norm(kind.s1) }
      Fb.FX_MIX -> out.glow = live.fxActivity[kind.a]
      Fb.TONE -> out.glow = live.fxActivity[Live.DIST] * norm(kind.s1)
      Fb.ECHO -> out.glow = live.echo
      Fb.HALO -> { out.glow = live.halo * 0.8f; out.halo = live.halo * (0.4f + 0.6f * norm(kind.s1)) }
      Fb.DAMP -> out.glow = live.halo * (1f - norm(kind.s1)) * 0.8f
      Fb.ARP_STEP -> out.glow = live.arpPulse * 0.9f
      Fb.FM_FEEDBACK -> {
        val x = (norm(kind.s1) * live.op[live.feedbackOp] * 3f).coerceIn(0f, 1f)
        out.glow = x; out.ring = x * (0.5f + 0.5f * sin(t * Waves.TAU * 6f))
      }
      Fb.ALGORITHM -> out.glow = amp * 0.35f
      Fb.OP_LEVEL -> out.glow = (live.op[kind.a] * 1.2f).coerceIn(0f, 1f)
      Fb.OP_STAGE -> out.glow = if (live.opStage[kind.a] == kind.b) (0.35f + live.op[kind.a]).coerceIn(0f, 1f) else 0f
      Fb.OP_RATIO -> {
        val fixed = real(kind.s1) >= 0.5f
        val ratio = if (fixed) 1f else FmFrequency.ratio(real(kind.s2).toInt(), real(kind.s3).toInt()).toFloat()
        out.glow = live.op[kind.a] * 0.5f
        out.inner = Inner.WAVE; out.cycles = min(8f, ratio) * 1.2f; out.phase = t * 3f; out.alpha = 0.4f + 0.6f * min(1f, live.op[kind.a] * 1.5f)
      }
      Fb.OP_BEAT -> {
        val d = real(kind.s1)
        val b = 0.5f + 0.5f * cos(Waves.TAU * abs(d) * 0.6f * t)
        out.glow = live.op[kind.a] * (if (d != 0f) 0.2f + 0.8f * b else 0.3f)
      }
      Fb.KEY_SCALE -> { out.glow = live.op[kind.a] * 0.4f; live.lead?.let { out.ghost = ((it.note - 21) / 99f).coerceIn(0f, 1f) } }
      Fb.PITCH_STAGE -> out.glow = if (live.pitchEnvStage == kind.a && live.voices.isNotEmpty()) 0.8f else 0f
      Fb.FM_LFO_RATE -> { out.glow = 0.15f + 0.55f * (0.5f + 0.5f * live.fmLfoValue); out.pulse = 0.5f + 0.5f * live.fmLfoValue }
      Fb.FM_LFO_DEPTH -> { val d = norm(kind.s1); out.glow = d * (0.5f + 0.5f * live.fmLfoValue) * 0.8f; out.ghost = d * (0.5f + 0.5f * live.fmLfoValue) }
      Fb.CONTROLLER -> {
        val v = when (kind.a) { 1 -> live.wheel; 0 -> live.aftertouch; else -> live.breath }
        out.glow = v * norm(kind.s1); out.ghost = v
      }
      Fb.TRANSPOSE -> {
        out.glow = amp * 0.3f
        out.inner = Inner.WAVE; out.cycles = 1.5f * 2f.pow(real(kind.s1) / 12f); out.phase = t * 4f; out.alpha = 0.45f + 0.5f * amp
      }
    }
  }
}
