package com.criapix.volt.midi

import kotlin.math.abs

/**
 * Decides whether a controller value may move a parameter (design 6.3, "captura suave").
 * [current] gives a parameter's normalized value now (NaN when unknown).
 * Not thread-safe: used from the MIDI thread only.
 */
class SoftTakeover(private val current: (Int) -> Float) {
  @Volatile var enabled = true
  private val lastSent = HashMap<Int, Float>()
  private val lastIn = HashMap<Int, Float>()

  /** [v] is already snapped to the parameter's steps. True: send it, and it becomes the controller's value. */
  fun accept(param: Int, v: Float, tol: Float): Boolean {
    val cur = current(param)
    val prev = lastIn.put(param, v)
    val sent = lastSent[param]
    val ok = !enabled || cur.isNaN() ||
      (sent != null && abs(sent - cur) < 1e-4f) ||
      abs(v - cur) <= tol ||
      (prev != null && (prev - cur) * (v - cur) <= 0f)
    if (ok) lastSent[param] = v
    return ok
  }

  /** The controller is in control of [param] from now on (right after it was learned). */
  fun attach(param: Int) {
    val c = current(param)
    if (!c.isNaN()) lastSent[param] = c
  }
}
