package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterCurveTest {
  private fun db(f: Float, model: Int, reso: Float = 0f, hpf: Float = 20f) = FilterCurve.magnitudeDb(f, 1000f, reso, model, hpf)

  @Test fun passbandIsFlat() {
    for (m in 0..3) assertEquals(0f, db(100f, m), 0.5f)
  }

  @Test fun slopesMatchTheModels() {
    val twelve = db(8000f, 1)
    assertTrue("12 dB model at 3 octaves: $twelve", twelve < -30f && twelve > -42f)
    for (m in listOf(0, 2, 3)) assertTrue("24 dB model $m: ${db(8000f, m)}", db(8000f, m) < -60f)
  }

  @Test fun resonancePeaksAtTheCutoff() {
    assertTrue(db(1000f, 2, reso = 0.9f) > 10f)
    assertTrue(db(1000f, 2, reso = 0f) < 0.5f)
  }

  @Test fun theHighPassCutsLows() {
    val v = FilterCurve.magnitudeDb(50f, 20000f, 0f, 1, 400f)
    assertTrue("$v", v < -15f && v > -21f)
  }

  @Test fun pointsAreLogSpaced() {
    val pts = FilterCurve.points(5, 1000f, 0f, 1, 20f)
    assertEquals(5, pts.size)
    assertEquals(-3f, pts[0], 0.5f)  // 20 Hz is the high-pass corner: -3 dB
    assertTrue(pts[4] < -30f)         // 20 kHz, far above a 1 kHz 12 dB low-pass
  }
}
