package com.criapix.volt.midi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftTakeoverTest {
  private val values = HashMap<Int, Float>()
  private val t = SoftTakeover { values[it] ?: Float.NaN }
  private val tol = 1.5f / 127f

  /** What the dispatcher does on acceptance: the parameter now holds v. */
  private fun send(p: Int, v: Float): Boolean = t.accept(p, v, tol).also { if (it) values[p] = v }

  @Test fun aDetachedControllerWaitsUntilItReachesTheValue() {
    values[1] = 0.8f
    assertFalse(send(1, 0.1f))
    assertFalse(send(1, 0.5f))
    assertTrue(send(1, 0.795f))   // within tolerance
    assertTrue(send(1, 0.2f))     // in control now: follows freely
  }

  @Test fun crossingTheValueCatchesIt() {
    values[1] = 0.5f
    assertFalse(send(1, 0.2f))
    assertTrue(send(1, 0.7f))     // jumped over 0.5 between two messages
  }

  @Test fun somethingElseChangingTheParameterDetachesTheController() {
    values[1] = 0.5f
    assertTrue(send(1, 0.5f))
    assertTrue(send(1, 0.6f))
    values[1] = 0.1f              // a preset was loaded, or the knob was turned on screen
    assertFalse(send(1, 0.65f))
  }

  @Test fun attachPutsAControllerInControlAtOnce() {
    values[1] = 0.5f
    t.attach(1)
    assertTrue(send(1, 0.9f))
  }

  @Test fun offOrUnknownAlwaysAccepts() {
    assertTrue(send(7, 0.3f))     // unknown current value
    values[1] = 0.9f
    t.enabled = false
    assertTrue(send(1, 0.0f))
  }
}
