package com.criapix.volt.state

import com.criapix.volt.Params
import com.criapix.volt.midi.Synth
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The normalized (0..1) value of every engine parameter, as the engine plays it. Every change
 * (screen, MIDI, preset) goes through here, so the screen always shows what is heard.
 * Thread-safe: the MIDI binder thread calls [setParam]; the UI thread calls the rest.
 * As a [Synth], it is what MidiDispatch talks to; notes pass straight through to [out].
 */
class SynthState(val params: Params, private val out: Synth) : Synth {
  /** The last parameter a MIDI controller changed; [seq] grows on every such change. */
  data class External(val id: Int, val seq: Long)

  private val flows = params.all.map { MutableStateFlow(it.toNorm(it.def)) }
  private val seq = AtomicLong()
  private val _dirty = MutableStateFlow(false)
  private val _external = MutableStateFlow(External(-1, 0))

  /** True once the sound differs from the loaded preset. */
  val dirty: StateFlow<Boolean> = _dirty
  val external: StateFlow<External> = _external

  fun value(id: Int): StateFlow<Float> = flows[id]
  fun norm(id: Int): Float = flows[id].value
  fun real(id: Int): Float = params.all[id].toReal(norm(id))

  /** A change made on the screen. */
  fun set(id: Int, normalized: Float) = change(id, normalized, external = false)

  /** A change from a MIDI controller: the screen follows it and flashes the parameter's tab. */
  override fun setParam(id: Int, normalized: Float) = change(id, normalized, external = true)

  private fun change(id: Int, normalized: Float, external: Boolean) {
    if (id !in flows.indices) return
    val p = params.all[id]
    val n = p.toNorm(p.toReal(normalized))  // clamps, and snaps steps to whole values
    out.setParam(id, n)
    if (flows[id].value == n) return
    flows[id].value = n
    _dirty.value = true
    if (external) _external.value = External(id, seq.incrementAndGet())
  }

  fun snapshot(): FloatArray = FloatArray(flows.size) { flows[it].value }

  /** Replaces every value at once (a preset). The engine switches without a click. */
  fun load(values: FloatArray) {
    require(values.size == flows.size) { "patch has ${values.size} values, engine has ${flows.size}" }
    for (i in flows.indices) flows[i].value = values[i]
    _dirty.value = false
    if (!out.loadPatch(values)) values.forEachIndexed { id, v -> out.setParam(id, v) }
  }

  override fun loadPatch(values: FloatArray): Boolean { load(values); return true }

  fun markClean() { _dirty.value = false }
  fun markDirty() { _dirty.value = true }

  override fun noteOn(note: Int, velocity: Float) = out.noteOn(note, velocity)
  override fun noteOff(note: Int) = out.noteOff(note)
  override fun pitchBend(value: Float) = out.pitchBend(value)
  override fun modWheel(value: Float) = out.modWheel(value)
  override fun sustain(on: Boolean) = out.sustain(on)
  override fun allNotesOff() = out.allNotesOff()
}
