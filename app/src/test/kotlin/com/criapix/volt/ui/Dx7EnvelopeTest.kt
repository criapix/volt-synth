package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Dx7EnvelopeTest {
  private val eps = 1e-4f

  @Test fun pointsStartAtL4AndEndAtL4() {
    val p = Dx7Envelope.points(intArrayOf(50, 50, 50, 50), intArrayOf(99, 80, 60, 10))
    assertEquals(6, p.size)
    assertEquals(0f, p[0].x, eps)
    assertEquals(10 / 99f, p[0].level, eps)
    assertEquals(99 / 99f, p[1].level, eps)
    assertEquals(80 / 99f, p[2].level, eps)
    assertEquals(60 / 99f, p[3].level, eps)
    assertEquals(60 / 99f, p[4].level, eps)   // plateau
    assertEquals(10 / 99f, p[5].level, eps)
  }

  @Test fun xIsMonotonicAndNeverPastTheWidth() {
    for (r in listOf(0, 1, 40, 99)) {
      val p = Dx7Envelope.points(intArrayOf(r, r, r, r), intArrayOf(0, 99, 0, 99))
      for (i in 1 until p.size) assertTrue(p[i].x > p[i - 1].x)
      assertTrue(p.last().x <= 1f + eps)
    }
  }

  @Test fun rate99IsTheMinimumWidthAndRate0TheMaximum() {
    assertEquals(Dx7Envelope.MIN, Dx7Envelope.width(99), eps)
    assertEquals(Dx7Envelope.SEG, Dx7Envelope.width(0), eps)
    val fast = Dx7Envelope.points(intArrayOf(99, 99, 99, 99), intArrayOf(0, 0, 0, 0))
    assertEquals(Dx7Envelope.MIN, fast[1].x, eps)
    assertEquals(4 * Dx7Envelope.MIN + Dx7Envelope.HOLD, fast.last().x, eps)
    val slow = Dx7Envelope.points(intArrayOf(0, 0, 0, 0), intArrayOf(0, 0, 0, 0))
    assertEquals(1f, slow.last().x, eps)
  }

  @Test fun plateauAlwaysTakesHold() {
    val p = Dx7Envelope.points(intArrayOf(10, 20, 30, 40), intArrayOf(1, 2, 3, 4))
    assertEquals(Dx7Envelope.HOLD, p[4].x - p[3].x, eps)
  }

  @Test fun nearestPicksTheClosestHandle() {
    val p = Dx7Envelope.points(intArrayOf(50, 50, 50, 50), intArrayOf(99, 70, 40, 0))
    val h = Dx7Envelope.handles(p)
    for (i in h.indices) assertEquals(i, Dx7Envelope.nearest(p, h[i].x + 0.01f, h[i].level - 0.01f))
    assertEquals(3, Dx7Envelope.nearest(p, 0.99f, 0f))
  }

  @Test fun dragRightLengthensTheSegmentSoTheRateFalls() {
    val (rate, level) = Dx7Envelope.drag(50, 70, 0.05f, 0f)
    assertTrue(rate < 50)
    assertEquals(70, level)
    val (rate2, _) = Dx7Envelope.drag(50, 70, -0.05f, 0f)
    assertTrue(rate2 > 50)
  }

  @Test fun dragUpRaisesTheLevel() {
    val (rate, level) = Dx7Envelope.drag(50, 70, 0f, -0.1f)   // up is negative on the screen
    assertEquals(50, rate)
    assertEquals(80, level)  // 70 + 0.1 * 99 = 79.9
    assertEquals(60, Dx7Envelope.drag(50, 70, 0f, 0.1f).second)
  }

  @Test fun dragClampsAndRounds() {
    assertEquals(99 to 99, Dx7Envelope.drag(95, 95, -1f, -1f))
    assertEquals(0 to 0, Dx7Envelope.drag(5, 5, 1f, 1f))
    // one rate step is (SEG - MIN) / 99 of the width
    val step = (Dx7Envelope.SEG - Dx7Envelope.MIN) / 99f
    assertEquals(49, Dx7Envelope.drag(50, 0, step, 0f).first)
  }
}
