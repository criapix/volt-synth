package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvelopeGeometryTest {
  @Test fun segmentsShareTheWidth() {
    val p = EnvelopeGeometry.points(1f, 1f, 0.5f, 1f)
    assertEquals(EnvelopeGeometry.SEG, p.attackX, 1e-6f)
    assertEquals(2 * EnvelopeGeometry.SEG, p.decayX, 1e-6f)
    assertEquals(2 * EnvelopeGeometry.SEG + EnvelopeGeometry.HOLD, p.holdEndX, 1e-6f)
    assertEquals(1f, p.releaseX, 1e-6f)
    assertEquals(0.5f, p.sustain, 0f)
  }

  @Test fun nearestHandleIsFound() {
    val p = EnvelopeGeometry.points(0.5f, 0.5f, 0.5f, 0.5f)
    assertEquals(EnvelopeGeometry.Handle.ATTACK, EnvelopeGeometry.nearest(p, p.attackX, 1f))
    assertEquals(EnvelopeGeometry.Handle.DECAY_SUSTAIN, EnvelopeGeometry.nearest(p, p.decayX, 0.5f))
    assertEquals(EnvelopeGeometry.Handle.RELEASE, EnvelopeGeometry.nearest(p, p.releaseX, 0f))
  }

  @Test fun draggingMovesTheRightParameterAndClamps() {
    assertEquals(0.75f, EnvelopeGeometry.time(0.5f, 0.25f * EnvelopeGeometry.SEG), 1e-6f)
    assertEquals(1f, EnvelopeGeometry.time(0.9f, 1f), 0f)
    assertEquals(0.7f, EnvelopeGeometry.level(0.5f, -0.2f), 1e-6f)  // dragging up raises sustain
    assertEquals(0f, EnvelopeGeometry.level(0.1f, 0.5f), 0f)
  }
}
