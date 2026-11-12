package com.criapix.volt

/** The C++ engine. Every call is thread-safe and returns at once. */
object Native {
  init { System.loadLibrary("volt") }

  /** One line per parameter: `name|min|max|default|curve`. The line number is the parameter id. */
  external fun paramTable(): String

  external fun start(): Boolean
  external fun stop()
  /** `key=value` lines: running, sampleRate, burst, buffer, lowLatency, exclusive, api, xruns, latencyMs, load, restarts, voices, voiceLimit, maxVoices. */
  external fun audioInfo(): String
  external fun peak(): Float
  /** Voices new notes may use right now (16, or less while the phone is overloaded or capped). */
  external fun voiceLimit(): Int
  /** The user's polyphony cap: 8, 12 or 16. */
  external fun setMaxVoices(n: Int)

  external fun noteOn(note: Int, velocity: Float)   // velocity 0..1
  external fun noteOff(note: Int)
  external fun setParam(id: Int, normalized: Float) // 0..1
  external fun pitchBend(value: Float)              // -1..1
  external fun modWheel(value: Float)               // 0..1
  external fun sustain(on: Boolean)
  external fun allNotesOff()

  /** Every parameter at once, normalized, indexed by id. False when the engine queue is full (nothing applied). */
  external fun loadPatch(values: FloatArray): Boolean
  external fun activeVoices(): Int
}
