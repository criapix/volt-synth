package com.criapix.volt.midi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The learned MIDI map and the Learn-mode state. The UI thread turns Learn mode on, arms a target
 * and edits the map; the MIDI thread captures and looks up. [save] receives the encoded map after
 * every change (preferences).
 */
class MidiLearn(private val map: MidiMap = MidiMap(), private val save: (String) -> kotlin.Unit = {}) {
  /** The binding just learned; [seq] grows each time (the screen shows a confirmation). */
  data class Learned(val source: Source, val target: Target, val seq: Long)

  private val _active = MutableStateFlow(false)
  private val _armed = MutableStateFlow<Target?>(null)
  private val _bindings = MutableStateFlow(map.entries())
  private val _learned = MutableStateFlow<Learned?>(null)
  private var seq = 0L

  val active: StateFlow<Boolean> = _active
  /** The control the user tapped in Learn mode, waiting for a MIDI message. */
  val armed: StateFlow<Target?> = _armed
  val bindings: StateFlow<List<Pair<Source, Binding>>> = _bindings
  val learned: StateFlow<Learned?> = _learned

  fun setActive(on: Boolean) {
    _active.value = on
    if (!on) _armed.value = null
  }

  fun arm(t: Target) { if (_active.value) _armed.value = t }

  /** MIDI thread. Binds [s] to the armed target. True when the message was used for learning (do not act on it). */
  @Synchronized fun capture(s: Source): Boolean {
    if (!_active.value) return false
    val t = _armed.value ?: return false
    if (s is Source.Note && t !is Target.Action) return false
    if (s is Source.Cc && (s.num == 0 || s.num == 32 || s.num >= 120)) return false
    map.bind(s, t)
    _armed.value = null
    _learned.value = Learned(s, t, ++seq)
    publish()
    return true
  }

  @Synchronized fun lookup(s: Source): Binding? = map[s]
  @Synchronized fun sourcesOf(t: Target): List<Source> = map.sourcesOf(t)
  @Synchronized fun setTap(s: Source, tap: Boolean) { map.setTap(s, tap); publish() }
  @Synchronized fun unbind(s: Source) { map.unbind(s); publish() }
  @Synchronized fun clear(t: Target) { map.clear(t); publish() }
  @Synchronized fun clearAll() { map.clearAll(); publish() }

  private fun publish() {
    _bindings.value = map.entries()
    save(map.encode())
  }
}
