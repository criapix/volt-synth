package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyScalingTest {
  private val eps = 1e-4f

  @Test fun changeIsZeroAtTheBreakPointAndScalesWithDepth() {
    for (c in 0..3) assertEquals(0f, KeyScaling.change(0f, 99, c), eps)
    assertEquals(-1f, KeyScaling.change(1f, 99, 0), eps)
    assertEquals(1f, KeyScaling.change(1f, 99, 3), eps)
    assertEquals(0.5f, KeyScaling.change(1f, 50, 3), 0.01f)
    assertEquals(0f, KeyScaling.change(1f, 0, 1), eps)
  }

  @Test fun negativeCurvesLowerAndPositiveCurvesRaise() {
    for (c in listOf(0, 1)) assertTrue(KeyScaling.change(0.7f, 60, c) < 0f)
    for (c in listOf(2, 3)) assertTrue(KeyScaling.change(0.7f, 60, c) > 0f)
  }

  @Test fun exponentialStaysBelowLinearInTheMiddle() {
    assertTrue(KeyScaling.change(0.5f, 99, 3) > KeyScaling.change(0.5f, 99, 2))
    assertTrue(KeyScaling.change(0.5f, 99, 0) < KeyScaling.change(0.5f, 99, 1))
  }

  @Test fun polylineUsesTheLeftCurveBelowAndTheRightAbove() {
    val pts = KeyScaling.polyline(39, 99, 0, 99, 3)
    assertEquals(KeyScaling.NOTES + 1, pts.size)
    assertEquals(0f, pts.first().first, eps)
    assertEquals(1f, pts.last().first, eps)
    assertEquals(-1f, pts.first().second, eps)   // left end: -LIN at full depth
    assertEquals(1f, pts.last().second, eps)     // right end: +LIN at full depth
    assertEquals(0f, pts[39].second, eps)        // the break point
  }

  @Test fun breakAtTheEdgesDoesNotDivideByZero() {
    assertTrue(KeyScaling.polyline(0, 99, 0, 99, 3).all { it.second.isFinite() })
    assertTrue(KeyScaling.polyline(99, 99, 0, 99, 3).all { it.second.isFinite() })
  }
}
