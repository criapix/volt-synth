package com.criapix.volt.midi

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
}

/**
 * Turns whole MIDI messages into synth calls, with the factory controller map (design section 6.2).
 * MIDI Learn and Program Change arrive in a later plan and will sit in front of this map.
 * [log] receives one line per message that matters (pitch bend and aftertouch are thinned out).
 */
class MidiDispatch(private val synth: Synth, params: Params, private val log: (String) -> Unit = {}) {
  /** -1 = omni, else 0..15. */
  @Volatile var channel = -1

  private val _last = MutableStateFlow("")
  /** Human-readable last note or controller, for the "última mensagem recebida" monitor. */
  val lastMessage: StateFlow<String> = _last

  private val cutoff = params["filter.cutoff"]?.id ?: -1
  private val reso = params["filter.reso"]?.id ?: -1
  private val volume = params["master.volume"]?.id ?: -1

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
    val d1 = if (len > 1) m[1].toInt() and 0x7F else 0
    val d2 = if (len > 2) m[2].toInt() and 0x7F else 0
    when (status and 0xF0) {
      0x90 -> if (len >= 3) {
        _last.value = "Nota $d1 · canal ${ch + 1} = $d2"
        log("MIDI nota ${if (d2 == 0) "off" else "on"} $d1 vel $d2 canal ${ch + 1}")
        if (d2 == 0) synth.noteOff(d1) else synth.noteOn(d1, d2 / 127f)
      }
      0x80 -> if (len >= 3) {
        log("MIDI nota off $d1 canal ${ch + 1}")
        synth.noteOff(d1)
      }
      0xE0 -> if (len >= 3) {
        val v = (d2 shl 7) or d1  // 0..16383, centre 8192
        if (dense()) log("MIDI pitch bend $v canal ${ch + 1}")
        synth.pitchBend(if (v >= 8192) (v - 8192) / 8191f else (v - 8192) / 8192f)
      }
      0xB0 -> if (len >= 3) {
        _last.value = "CC $d1 · canal ${ch + 1} = $d2"
        log("MIDI CC $d1 = $d2 canal ${ch + 1}")
        controller(d1, d2)
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

  private fun controller(cc: Int, value: Int) {
    val n = value / 127f
    when (cc) {
      1 -> synth.modWheel(n)
      7 -> if (volume >= 0) synth.setParam(volume, n)
      64 -> synth.sustain(value >= 64)
      71 -> if (reso >= 0) synth.setParam(reso, n)
      74 -> if (cutoff >= 0) synth.setParam(cutoff, n)
      120, 123 -> synth.allNotesOff()
    }
  }
}
