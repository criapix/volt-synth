package com.criapix.volt.midi

import com.criapix.volt.Curve
import com.criapix.volt.Param
import com.criapix.volt.Params
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the MIDI layer needs from the synth. [com.criapix.volt.Native] satisfies it through an adapter. */
interface Synth {
  fun noteOn(note: Int, velocity: Float)
  fun noteOff(note: Int)
  fun setParam(id: Int, normalized: Float)
  fun pitchBend(value: Float)
  fun modWheel(value: Float)
  fun sustain(on: Boolean)
  fun allNotesOff()

  /** All parameters at once (normalized, indexed by id). Default: one setParam per value. */
  fun loadPatch(values: FloatArray): Boolean {
    values.forEachIndexed { id, v -> setParam(id, v) }
    return true
  }
}

/**
 * Turns whole MIDI messages into synth calls (design section 6): learned bindings first, then the
 * factory map; Program Change and learnable actions go to callbacks, which the app runs on the UI
 * thread. Every parameter change from a controller passes soft takeover. Runs on the MIDI thread.
 */
class MidiDispatch(
  private val synth: Synth,
  private val params: Params,
  private val log: (String) -> kotlin.Unit = {},
  val learn: MidiLearn = MidiLearn(),
  private val current: (Int) -> Float = { Float.NaN },
  private val onAction: (MidiAction) -> kotlin.Unit = {},
  private val onProgram: (Int) -> kotlin.Unit = {},
  private val clock: () -> Long = System::nanoTime,
) {
  /** A controller is detached from [param] and must be brought to [value] (normalized) to take over. */
  data class Pickup(val param: Int, val value: Float)

  companion object {
    /** Factory controller -> parameter name (design 6.2 plus the General MIDI sound controllers). */
    val FACTORY_PARAMS = linkedMapOf(
      7 to "master.volume", 71 to "filter.reso", 72 to "aenv.release",
      73 to "aenv.attack", 74 to "filter.cutoff", 75 to "aenv.decay",
    )
    private const val DEBOUNCE_NS = 150_000_000L
  }

  private val takeover = SoftTakeover(current)
  var softTakeover: Boolean
    get() = takeover.enabled
    set(v) { takeover.enabled = v }

  private val _pickup = MutableStateFlow<Pickup?>(null)
  val pickup: StateFlow<Pickup?> = _pickup

  private val factory = FACTORY_PARAMS.mapNotNull { (cc, name) -> params[name]?.let { cc to it } }.toMap()
  private val prevValue = HashMap<Source, Int>()
  private val lastFire = HashMap<Source, Long>()

  /** -1 = omni, else 0..15. */
  @Volatile var channel = -1

  private val _activity = MutableStateFlow(0L)
  /** Grows on every channel message that passes the channel filter (the MIDI light). */
  val activity: StateFlow<Long> = _activity

  private val _last = MutableStateFlow("")
  /** Human-readable last note or controller, for the "última mensagem recebida" monitor. */
  val lastMessage: StateFlow<String> = _last

  private var lastDenseLog = 0L              // thins out pitch bend / aftertouch lines
  private val seenSystem = BooleanArray(16)  // each system / real-time status is logged once

  fun message(m: ByteArray, len: Int) {
    if (len < 1) return
    val status = m[0].toInt() and 0xFF
    if (status < 0x80) return
    if (status >= 0xF0) {
      val i = status and 0x0F
      if (!seenSystem[i]) { seenSystem[i] = true; log("MIDI sistema/tempo-real 0x${status.toString(16)} (só a primeira vez)") }
      return
    }
    val ch = status and 0x0F
    if (channel >= 0 && ch != channel) return
    _activity.value = _activity.value + 1
    val d1 = if (len > 1) m[1].toInt() and 0x7F else 0
    val d2 = if (len > 2) m[2].toInt() and 0x7F else 0
    when (status and 0xF0) {
      0x90 -> if (len >= 3) {
        _last.value = "Nota $d1 · canal ${ch + 1} = $d2"
        log("MIDI nota ${if (d2 == 0) "off" else "on"} $d1 vel $d2 canal ${ch + 1}")
        val src = Source.Note(ch, d1)
        when {
          d2 > 0 && learn.capture(src) -> learned(src)
          learn.lookup(src) != null -> apply(learn.lookup(src)!!, src, if (d2 > 0) 127 else 0)
          d2 == 0 -> synth.noteOff(d1)
          else -> synth.noteOn(d1, d2 / 127f)
        }
      }
      0x80 -> if (len >= 3) {
        log("MIDI nota off $d1 canal ${ch + 1}")
        val src = Source.Note(ch, d1)
        val b = learn.lookup(src)
        if (b != null) apply(b, src, 0) else synth.noteOff(d1)
      }
      0xE0 -> if (len >= 3) {
        val v = (d2 shl 7) or d1  // 0..16383, centre 8192
        if (dense()) log("MIDI pitch bend $v canal ${ch + 1}")
        synth.pitchBend(if (v >= 8192) (v - 8192) / 8191f else (v - 8192) / 8192f)
      }
      0xB0 -> if (len >= 3) {
        _last.value = "CC $d1 · canal ${ch + 1} = $d2"
        log("MIDI CC $d1 = $d2 canal ${ch + 1}")
        val src = Source.Cc(ch, d1)
        if (learn.capture(src)) { learned(src); return }
        val b = learn.lookup(src)
        if (b != null) apply(b, src, d2) else controller(d1, d2)
      }
      0xC0 -> if (len >= 2) {
        _last.value = "Programa ${d1 + 1} · canal ${ch + 1}"
        log("MIDI program change ${d1 + 1} canal ${ch + 1}")
        onProgram(d1)
      }
      else -> if (dense()) log("MIDI status 0x${status.toString(16)} dados $d1 $d2 (ignorado)")
    }
  }

  private fun dense(): Boolean {
    val now = System.nanoTime()
    if (now - lastDenseLog < 100_000_000L) return false
    lastDenseLog = now
    return true
  }

  private fun learned(src: Source) {
    val t = learn.lookup(src)?.target
    log("MIDI aprendido: ${describe(src)} -> $t")
    if (t is Target.Param) params[t.name]?.let { takeover.attach(it.id) }
  }

  private fun apply(b: Binding, src: Source, value: Int) {
    val prev = prevValue.put(src, value)
    when (val t = b.target) {
      is Target.Action -> if (fires(b, src, prev, value)) { log("MIDI ação ${t.action.key}"); onAction(t.action) }
      is Target.Param -> {
        val p = params[t.name] ?: return
        val onOff = p.curve == Curve.STEP && p.min == 0f && p.max == 1f
        if (b.tap && onOff) {
          if (fires(b, src, prev, value)) synth.setParam(p.id, if (current(p.id) >= 0.5f) 0f else 1f)
        } else {
          fromController(p, value / 127f)
        }
      }
    }
  }

  /** Rising edge into >= 64, or any message for a tap binding (debounced). */
  private fun fires(b: Binding, src: Source, prev: Int?, value: Int): Boolean {
    val now = clock()
    val ok = if (b.tap) now - (lastFire[src] ?: Long.MIN_VALUE / 2) > DEBOUNCE_NS
             else value >= 64 && (prev == null || prev < 64)
    if (ok) lastFire[src] = now
    return ok
  }

  private fun fromController(p: Param, n: Float) {
    val q = p.toNorm(p.toReal(n))  // snapped like SynthState snaps it
    val steps = if (p.curve == Curve.STEP) (p.max - p.min) else 0f
    val tol = maxOf(1.5f / 127f, if (steps > 0f) 0.5f / steps else 0f)
    if (takeover.accept(p.id, q, tol)) {
      synth.setParam(p.id, q)
      if (_pickup.value?.param == p.id) _pickup.value = null
    } else {
      _pickup.value = Pickup(p.id, current(p.id))
    }
  }

  private fun controller(cc: Int, value: Int) {
    factory[cc]?.let { fromController(it, value / 127f); return }
    when (cc) {
      1 -> synth.modWheel(value / 127f)
      64 -> synth.sustain(value >= 64)
      120, 123 -> synth.allNotesOff()
    }
  }
}

/** MIDI settings the screen edits; the app keeps them in preferences and applies them to the dispatcher. */
interface MidiSettings {
  var channel: Int          // -1 = Omni
  var softTakeover: Boolean
}
