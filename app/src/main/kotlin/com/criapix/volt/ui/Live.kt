package com.criapix.volt.ui

import com.criapix.volt.Param
import com.criapix.volt.state.SynthState
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** One sounding note as the screen follows it. */
class LiveVoice(var note: Int, val vel: Float, val tOn: Double, val fm: Boolean, var freq: Float, var from: Float) {
  var tGl = tOn
  var tOff = Double.NaN
  var gateOff = Double.NaN
  val released get() = !tOff.isNaN()

  // analog envelopes: current value 0..1 and stage (Live.A, D, S, R)
  var a = 0f; var aStage = 0; var aOff = 0f
  var f = 0f; var fStage = 0; var fOff = 0f

  // FM: per operator (1..6) the envelope level 0..99, its stage (1..3 rising, 3 hold, 4 release, 0 done) and the output 0..1
  val opLevel = FloatArray(7); val opStage = IntArray(7); val op = FloatArray(7); val opOff = FloatArray(7)
  var pe = 50f; var peStage = 0; var peOff = 50f

  /** How loud this note is now, 0..1. */
  var out = 0f
}

/**
 * What the sound is doing right now, rebuilt every frame from the notes the engine receives and the parameters it
 * plays. The engine does not report its own state yet, so this repeats its envelopes, LFOs, arpeggiator and
 * delay/reverb tails from the same parameters: close enough to draw, not sample-accurate. Notes and wheels come in
 * from any thread (as a [SynthState.PerformanceObserver]); [update] and every read are for the UI thread.
 */
class Live(
  private val state: SynthState,
  private val peak: () -> Float = { 0f },
  private val algorithms: () -> List<Algo> = { emptyList() },
  private val clock: () -> Double = { System.nanoTime() / 1e9 },
) : SynthState.PerformanceObserver {
  companion object {
    const val A = 0; const val D = 1; const val S = 2; const val R = 3
    const val DIST = 0; const val PHASER = 1; const val FLANGER = 2; const val CHORUS = 3; const val DELAY = 4; const val REVERB = 5
    private val DIVISIONS = floatArrayOf(1f, .5f, .25f, 2f / 3f, 1f / 3f, 1f / 6f)
    private const val ON = 0; private const val OFF = 1; private const val SUSTAIN = 2; private const val PANIC = 3; private const val BEND = 4; private const val MOD = 5; private const val AT = 6; private const val BREATH = 7

    /** How long a DX7 segment takes: a high rate or a short level jump is fast. */
    fun dxDuration(rate: Float, levelJump: Float): Float = max(0.002f, 38f * exp(-rate * 0.105f) * max(0.04f, abs(levelJump) / 99f))

    fun noteFreq(note: Float): Float = 440f * 2f.pow((note - 69f) / 12f)
  }

  /** An event stamped with the time it arrived, so a note starts when it was played, not on the next frame. */
  private class Ev(val kind: Int, val t: Double, val a: Int = 0, val v: Float = 0f)

  private val events = ConcurrentLinkedQueue<Ev>()
  override fun noteOn(note: Int, velocity: Float) { events.add(Ev(ON, clock(), note, velocity)) }
  override fun noteOff(note: Int) { events.add(Ev(OFF, clock(), note)) }
  override fun sustain(on: Boolean) { events.add(Ev(SUSTAIN, clock(), if (on) 1 else 0)) }
  override fun allNotesOff() { events.add(Ev(PANIC, clock())) }
  override fun pitchBend(value: Float) { events.add(Ev(BEND, clock(), 0, value)) }
  override fun modWheel(value: Float) { events.add(Ev(MOD, clock(), 0, value)) }
  override fun aftertouch(value: Float) { events.add(Ev(AT, clock(), 0, value)) }
  override fun breath(value: Float) { events.add(Ev(BREATH, clock(), 0, value)) }

  // ---- what the drawings read ------------------------------------------------------------------------------------
  var t = 0.0; private set
  var fm = false; private set
  /** The loudest note, 0..1. */
  var amp = 0f; private set
  var ampSum = 0f; private set
  /** The output level (the engine's own peak, smoothed with a slow fall), 0..1. */
  var level = 0f; private set
  /** Envelope stages lighting up: aA aD aS aR fA fD fS fR. */
  val stage = FloatArray(8)
  var fenv = 0f; private set
  var cutoff = 20000f; private set
  var lfo1 = 0f; private set; var lfo2 = 0f; private set; var lfoPhase1 = 0f; private set; var lfoPhase2 = 0f; private set
  var lfoFade1 = 1f; private set; var lfoFade2 = 1f; private set
  var fmLfoValue = 0f; private set; var fmLfoPhase = 0f; private set; var fmLfoFade = 1f; private set
  var wheel = 0f; private set
  var aftertouch = 0f; private set
  var breath = 0f; private set
  /** When the last note started (seconds on the model's clock). */
  val lastNoteOn: Double get() = lastOn
  /** When the latest notes started (up to 12), for the echo timeline. */
  val noteStarts: Collection<Double> get() = noteTimes
  /** FM: the algorithm in use (null before the first frame or without algorithms). */
  var algo: Algo? = null; private set
  var bend = 0f; private set
  var echo = 0f; private set
  var halo = 0f; private set
  /** Per effect (DIST..REVERB): how much it is working on the sound now, 0..1; 0 when it is off. */
  val fxActivity = FloatArray(6)
  var phaserSwing = 0f; private set; var flangerSwing = 0f; private set
  var chorusPhase1 = 0f; private set; var chorusPhase2 = 0f; private set
  var arpPulse = 0f; private set
  var arpStep = 0; private set
  var velFlash = 0f; private set
  var noteFlash = 0f; private set
  var f0 = 220f; private set
  var glide = 0f; private set
  var lead: LiveVoice? = null; private set
  /** The sounding notes (newest last). Valid until the next [update]. */
  val voices = ArrayList<LiveVoice>()
  /** Per MIDI note, how loud it is now (0..1); lights the keys. */
  val noteLevels = FloatArray(128)
  /** FM: the loudest output of each operator (index 1..6) and the stage of the newest note's. */
  val op = FloatArray(7)
  val opStage = IntArray(7)
  var pitchEnv = 50f; private set
  var pitchEnvStage = 0; private set
  /** FM: the operator that receives the algorithm's feedback loop. */
  var feedbackOp = 1; private set

  // ---- internals --------------------------------------------------------------------------------------------------
  private val cache = HashMap<String, Param>()
  private fun p(name: String): Float {
    val prm = cache.getOrPut(name) { state.params[name] ?: error("no parameter $name") }
    return prm.toReal(state.norm(prm.id))
  }
  private fun on(name: String) = p(name) >= 0.5f

  private var lastT = Double.NaN
  private val held = ArrayList<Int>()
  private val order = ArrayList<Int>()
  private val pending = HashSet<Int>()
  private var pedal = false
  private val noteTimes = ArrayDeque<Double>()
  private var lastOn = -100.0
  private var lastVel = 0.8f
  private var lastFreq = 0f
  private var reverbEnergy = 0f
  private var phaserPhase = 0f; private var flangerPhase = 0f
  private var arpNext = Double.NaN
  private var arpWasOn = false
  private var arpIdx = 0
  private val rng = Random(7)
  private var wasFm = false

  // per-frame parameter cache (analog envelopes)
  private var aa = 0f; private var ad = 0f; private var asus = 0f; private var ar = 0f
  private var fa = 0f; private var fd = 0f; private var fsus = 0f; private var fr = 0f
  // per-frame parameter cache (FM operators 1..6 and pitch envelope)
  private val opR = Array(7) { FloatArray(4) }; private val opL = Array(7) { FloatArray(4) }
  private val opRate = FloatArray(7); private val opVelSens = FloatArray(7); private val opLvl = FloatArray(7); private val opOn = BooleanArray(7)
  private val opBreak = FloatArray(7); private val opLDepth = FloatArray(7); private val opRDepth = FloatArray(7)
  private val opLCurve = IntArray(7); private val opRCurve = IntArray(7)
  private val peR = FloatArray(4); private val peL = FloatArray(4)
  private var carriers: Set<Int> = emptySet()
  private val rr = FloatArray(4)

  /** The held keys, lowest first, as the arpeggiator walks them (for the step drawing). */
  fun arpSequence(): IntArray {
    val base = if (p("arp.mode").toInt() == 4) order.filter { it in held } else held.sorted()
    val octs = p("arp.octaves").toInt().coerceIn(1, 4)
    var s = (0 until octs).flatMap { o -> base.map { it + 12 * o } }
    when (p("arp.mode").toInt()) {
      1 -> s = s.reversed()
      2 -> if (s.size > 2) s = s + s.subList(1, s.size - 1).reversed()
    }
    return s.toIntArray()
  }

  fun heldNotes(): List<Int> = held.sorted()

  /** Moves everything to "now". Call once per frame, UI thread. */
  fun update() {
    val now = clock()
    val dt = if (lastT.isNaN()) 0.0 else min(0.1, max(0.0, now - lastT))
    lastT = now; t = now
    val dtf = dt.toFloat()

    fm = on("engine.type")
    if (fm != wasFm) { voices.clear(); held.clear(); order.clear(); pending.clear(); lastFreq = 0f; wasFm = fm }
    cacheParams()

    drain(now)
    // LFOs
    lfoPhase1 += dtf * p("lfo1.rate"); lfoPhase2 += dtf * p("lfo2.rate")
    lfoFade1 = fade(p("lfo1.delay"), now); lfoFade2 = fade(p("lfo2.delay"), now)
    lfo1 = Waves.lfo(p("lfo1.wave").toInt(), lfoPhase1) * lfoFade1
    lfo2 = Waves.lfo(p("lfo2.wave").toInt(), lfoPhase2) * lfoFade2
    fmLfoPhase += dtf * (0.062f * (47f / 0.062f).pow(p("fm.lfo.speed") / 99f))
    fmLfoFade = fade(p("fm.lfo.delay") / 99f * 5f, now)
    fmLfoValue = Waves.fmLfo(p("fm.lfo.wave").toInt(), fmLfoPhase) * fmLfoFade
    phaserPhase += dtf * p("fx.phaser.rate"); flangerPhase += dtf * p("fx.flanger.rate")
    chorusPhase1 += dtf * 0.5f; chorusPhase2 += dtf * 0.83f
    phaserSwing = sin(Waves.TAU * phaserPhase); flangerSwing = sin(Waves.TAU * flangerPhase)

    runArp(now)

    // voices
    var i = 0
    while (i < voices.size) {
      val v = voices[i]
      if (!v.released && !v.gateOff.isNaN() && now >= v.gateOff) release(v, now)
      evalVoice(v, now)
      val gone = if (v.released) {
        if (v.fm) carriers.all { v.opStage[it] == 0 } || now - v.tOff > 30 else now - v.tOff > ar * 2 + 0.05 || v.out < 0.002f
      } else false
      if (gone) voices.removeAt(i) else i++
    }
    aggregate(now, dtf)
  }

  private fun cacheParams() {
    aa = p("aenv.attack"); ad = p("aenv.decay"); asus = p("aenv.sustain"); ar = p("aenv.release")
    fa = p("fenv.attack"); fd = p("fenv.decay"); fsus = p("fenv.sustain"); fr = p("fenv.release")
    if (!fm) return
    val algos = algorithms()
    val n = p("fm.algorithm").toInt().coerceIn(1, 32)
    algo = algos.getOrNull(n - 1)
    carriers = algo?.carriers ?: setOf(1)
    feedbackOp = algos.getOrNull(n - 1)?.feedback?.takeIf { it in 1..6 } ?: 1
    for (o in 1..6) {
      val k = "fm.op$o."
      for (j in 0..3) { opR[o][j] = p(k + "r" + (j + 1)); opL[o][j] = p(k + "l" + (j + 1)) }
      opRate[o] = p(k + "rate.scale"); opVelSens[o] = p(k + "vel"); opLvl[o] = p(k + "level"); opOn[o] = on(k + "on")
      opBreak[o] = p(k + "kls.break"); opLDepth[o] = p(k + "kls.ldepth"); opRDepth[o] = p(k + "kls.rdepth")
      opLCurve[o] = p(k + "kls.lcurve").toInt(); opRCurve[o] = p(k + "kls.rcurve").toInt()
    }
    for (j in 0..3) { peR[j] = p("fm.pitchenv.r" + (j + 1)); peL[j] = p("fm.pitchenv.l" + (j + 1)) }
  }

  private fun fade(delay: Float, now: Double): Float = if (delay < 0.01f) 1f else ((now - lastOn) / delay).toFloat().coerceIn(0f, 1f)

  // ---- events -----------------------------------------------------------------------------------------------------
  private fun drain(now: Double) {
    while (true) {
      val e = events.poll() ?: break
      val at = min(e.t, now)
      when (e.kind) {
        ON -> {
          if (e.a !in held) held.add(e.a)
          order.remove(e.a); order.add(e.a)
          pending.remove(e.a)
          if (on("arp.on")) {
            if (held.size == 1 || arpNext.isNaN()) { arpIdx = 0; arpNext = at }
            lastVel = e.v
          } else playNote(e.a, e.v, at)
        }
        OFF -> if (pedal) pending.add(e.a) else releaseKey(e.a, at)
        SUSTAIN -> {
          pedal = e.a == 1
          if (!pedal) { pending.toList().forEach { releaseKey(it, at) }; pending.clear() }
        }
        PANIC -> { held.clear(); order.clear(); pending.clear(); voices.forEach { release(it, at) } }
        BEND -> bend = e.v.coerceIn(-1f, 1f)
        MOD -> wheel = e.v.coerceIn(0f, 1f)
        AT -> aftertouch = e.v.coerceIn(0f, 1f)
        BREATH -> breath = e.v.coerceIn(0f, 1f)
      }
    }
  }

  private fun releaseKey(note: Int, now: Double) {
    held.remove(note); order.remove(note)
    if (!on("arp.on")) voices.filter { it.note == note && !it.released && it.gateOff.isNaN() }.forEach { release(it, now) }
  }

  private fun playNote(note: Int, vel: Float, now: Double) {
    val cap = if (fm) 8 else 12
    val live = voices.filter { !it.released }
    if (voices.size >= cap) (voices.firstOrNull { it.released } ?: voices.first()).let { voices.remove(it) }
    val mode = if (fm) 0 else p("voice.mode").toInt()
    if (mode == 2 && live.isNotEmpty()) {  // legato: the held note glides to the new pitch
      val v = live.last()
      val cur = currentFreq(v, now)
      v.from = if (p("voice.glide") > 0.0005f) cur else noteFreq(note.toFloat())
      v.note = note; v.freq = noteFreq(note.toFloat()); v.tGl = now; lastFreq = v.freq; lastVel = vel; markOn(now)
      return
    }
    if (mode in 1..2) live.forEach { release(it, now) }
    val freq = noteFreq(note + if (fm) p("fm.transpose") else 0f)
    val g = p("voice.glide")
    val from = if (!fm && g > 0.0005f && lastFreq > 0f) lastFreq else freq
    voices.add(LiveVoice(note, vel, now, fm, freq, from))
    lastFreq = freq; lastVel = vel
    markOn(now)
    if (fm && on("fm.lfo.sync")) fmLfoPhase = 0f
  }

  private fun markOn(now: Double) {
    lastOn = now
    noteTimes.addLast(now); if (noteTimes.size > 12) noteTimes.removeFirst()
  }

  private fun runArp(now: Double) {
    val arpOn = on("arp.on")
    if (arpWasOn && !arpOn) voices.filter { !it.gateOff.isNaN() && !it.released }.forEach { release(it, now) }
    arpWasOn = arpOn
    if (!arpOn || held.isEmpty()) { arpPulse = 0f; return }
    val step = (60f / p("arp.bpm") * DIVISIONS[p("arp.division").toInt().coerceIn(0, 5)]).toDouble()
    if (arpNext.isNaN() || now - arpNext > 0.5) arpNext = now
    var guard = 0
    while (now >= arpNext && guard++ < 8) {
      val seq = arpSequence()
      if (seq.isEmpty()) break
      val note = if (p("arp.mode").toInt() == 3) seq[rng.nextInt(seq.size)] else seq[arpIdx % seq.size]
      arpIdx++
      voices.filter { !it.released && !it.gateOff.isNaN() }.forEach { release(it, now) }
      playNote(note, lastVel, arpNext)
      voices.lastOrNull { it.note == note }?.gateOff = arpNext + step * p("arp.gate")
      arpStepTime = arpNext
      arpNext += step
    }
    val gate = step * p("arp.gate")
    arpPulse = if (now - arpStepTime < gate) (1.0 - 0.5 * (now - arpStepTime) / gate).toFloat() else 0f
    arpStep = arpIdx
  }
  private var arpStepTime = -100.0

  // ---- envelopes --------------------------------------------------------------------------------------------------
  private fun release(v: LiveVoice, now: Double) {
    if (v.released) return
    evalVoice(v, now)
    v.aOff = v.a; v.fOff = v.f
    for (o in 1..6) v.opOff[o] = v.opLevel[o]
    v.peOff = v.pe
    v.tOff = now
  }

  fun currentFreq(v: LiveVoice, now: Double): Float {
    val g = p("voice.glide")
    if (v.from == v.freq || g < 0.0005f) return v.freq
    val cents = 1200f * ln(v.from / v.freq) / ln(2f) * exp(-((now - v.tGl) / (g / 3f)).toFloat())
    return v.freq * 2f.pow(cents / 1200f)
  }

  private fun evalVoice(v: LiveVoice, now: Double) {
    if (v.fm) evalFm(v, now) else evalAnalog(v, now)
  }

  private fun evalAnalog(v: LiveVoice, now: Double) {
    val dt = (now - v.tOn).toFloat()
    if (!v.released) {
      if (dt < aa) { v.a = dt / max(aa, 1e-4f); v.aStage = A }
      else { val x = dt - aa; v.a = asus + (1 - asus) * exp(-x / (ad / 4f + 1e-4f)); v.aStage = if (x < ad) D else S }
      if (dt < fa) { v.f = dt / max(fa, 1e-4f); v.fStage = A }
      else { val x = dt - fa; v.f = fsus + (1 - fsus) * exp(-x / (fd / 4f + 1e-4f)); v.fStage = if (x < fd) D else S }
    } else {
      val e = (now - v.tOff).toFloat()
      v.a = v.aOff * exp(-e / (ar / 4f + 1e-4f)); v.aStage = R
      v.f = v.fOff * exp(-e / (fr / 4f + 1e-4f)); v.fStage = R
    }
    v.out = v.a * (1 - p("aenv.vel") * (1 - v.vel))
  }

  private fun evalFm(v: LiveVoice, now: Double) {
    for (o in 1..6) {
      val add = opRate[o] * max(0, v.note - 21) / 12f
      val r = rr
      for (j in 0..3) r[j] = min(99f, opR[o][j] + add)
      val l = opL[o]
      var level: Float
      var st: Int
      if (!v.released) {
        var e = (now - v.tOn).toFloat(); var from = l[3]; level = l[2]; st = 3
        for (i in 0..2) {
          val d = dxDuration(r[i], l[i] - from)
          if (e < d) { level = from + (l[i] - from) * e / d; st = i + 1; break }
          e -= d; from = l[i]
        }
      } else {
        val off = v.opOff[o]; val d = dxDuration(r[3], l[3] - off); val e = (now - v.tOff).toFloat()
        if (e < d) { level = off + (l[3] - off) * e / d; st = 4 } else { level = l[3]; st = 0 }
      }
      v.opLevel[o] = level; v.opStage[o] = st
      v.op[o] = if (opOn[o]) (opOut(o, v.note, v.vel) / 99f) * (level / 99f) else 0f
    }
    // pitch envelope (levels centred on 50)
    run {
      var level: Float; var st: Int
      if (!v.released) {
        var e = (now - v.tOn).toFloat(); var from = peL[3]; level = peL[2]; st = 3
        for (i in 0..2) {
          val d = dxDuration(peR[i], peL[i] - from)
          if (e < d) { level = from + (peL[i] - from) * e / d; st = i + 1; break }
          e -= d; from = peL[i]
        }
      } else {
        val d = dxDuration(peR[3], peL[3] - v.peOff); val e = (now - v.tOff).toFloat()
        if (e < d) { level = v.peOff + (peL[3] - v.peOff) * e / d; st = 4 } else { level = peL[3]; st = 0 }
      }
      v.pe = level; v.peStage = st
    }
    v.out = carriers.maxOfOrNull { v.op[it] } ?: 0f
  }

  /** The operator's output level 0..99 for [note] after keyboard scaling and velocity sensitivity. */
  private fun opOut(o: Int, note: Int, vel: Float): Float =
    (opLvl[o] + keyScale(o, note)).coerceIn(0f, 99f) * (1 - opVelSens[o] / 7f * (1 - vel))

  /** Keyboard level scaling in level steps (negative = quieter) for [note]. */
  fun keyScale(o: Int, note: Int): Float {
    val bp = 21 + opBreak[o]
    val left = note < bp
    val depth = if (left) opLDepth[o] else opRDepth[o]
    val curve = if (left) opLCurve[o] else opRCurve[o]
    val x = min(1f, abs(note - bp) / 48f)
    val y = if (curve == 0 || curve == 3) x else (exp(3f * x) - 1f) / (exp(3f) - 1f)
    return (if (curve < 2) -1f else 1f) * depth * y
  }

  // ---- what the drawings read, once per frame ----------------------------------------------------------------------
  private fun aggregate(now: Double, dt: Float) {
    amp = 0f; ampSum = 0f; lead = null
    stage.fill(0f); op.fill(0f); opStage.fill(0); noteLevels.fill(0f)
    for (v in voices) {
      amp = max(amp, v.out); ampSum += v.out
      if (v.note in 0..127) noteLevels[v.note] = max(noteLevels[v.note], v.out)
      if (v.fm) { for (o in 1..6) op[o] = max(op[o], v.op[o]) }
      else {
        stage[v.aStage] = max(stage[v.aStage], if (v.aStage == R) v.a else max(0.55f, v.a))
        stage[4 + v.fStage] = max(stage[4 + v.fStage], if (v.fStage == R) v.f else max(0.55f, v.f))
      }
      if (lead == null || v.tOn >= lead!!.tOn) lead = v
    }
    val L = lead
    if (L != null && L.fm) { for (o in 1..6) opStage[o] = L.opStage[o]; pitchEnv = L.pe; pitchEnvStage = L.peStage }
    else { pitchEnv = 50f; pitchEnvStage = 0 }
    f0 = if (L != null) currentFreq(L, now) else 220f
    glide = if (L != null && L.from != L.freq) (abs(ln(f0 / L.freq) / ln(2f)) * 6f).coerceIn(0f, 1f) else 0f
    fenv = if (L != null && !L.fm) L.f else 0f

    val wheelMul = 1 - p("lfo1.wheel") * (1 - wheel)
    val keyMul = if (L != null && !L.fm) (L.freq / 261.6f).pow(p("filter.keytrack")) * 2f.pow((L.vel - 0.7f) * p("filter.vel") * 3f) else 1f
    cutoff = (p("filter.cutoff") * keyMul * 2f.pow(fenv * p("filter.env") * 7f + (lfo1 * p("lfo1.cutoff") * wheelMul + lfo2 * p("lfo2.cutoff")) * 4f)).coerceIn(20f, 20000f)

    velFlash = (lastVel * exp(-((now - lastOn) / 0.35))).toFloat()
    noteFlash = exp(-((now - lastOn) / 0.35)).toFloat()

    // delay echoes and the reverb tail
    var e = 0f
    if (on("fx.delay.on")) {
      val time = p("fx.delay.time"); val fb = p("fx.delay.feedback")
      for (tOn in noteTimes) {
        var gk = 1f
        for (k in 1 until 16) {
          val tk = tOn + k * time
          if (tk > now) break
          if (k > 1) gk *= fb
          if (gk < 0.03f) break
          e = max(e, gk * exp(-((now - tk) / 0.09)).toFloat())
        }
      }
    }
    echo = e * min(1f, p("fx.delay.mix") * 2.2f)
    reverbEnergy = max(reverbEnergy * exp(-dt * 3f / p("fx.reverb.decay")), if (on("fx.reverb.on")) amp else 0f)
    halo = reverbEnergy * min(1f, p("fx.reverb.mix") * 2f)

    level = max(peak(), level * exp(-dt * 5f))
    val lv = level
    fxActivity[DIST] = if (on("fx.dist.on")) p("fx.dist.mix") * lv else 0f
    fxActivity[PHASER] = if (on("fx.phaser.on")) p("fx.phaser.mix") * lv else 0f
    fxActivity[FLANGER] = if (on("fx.flanger.on")) p("fx.flanger.mix") * lv else 0f
    fxActivity[CHORUS] = if (on("fx.chorus.on")) p("fx.chorus.mix") * lv else 0f
    fxActivity[DELAY] = echo
    fxActivity[REVERB] = if (on("fx.reverb.on")) halo else 0f
  }
}
