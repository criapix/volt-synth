package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardLayoutTest {
  private val w = 1500f   // 15 white keys of 100 px
  private val h = 300f
  private fun at(whiteIndex: Float, yFrac: Float) = KeyboardLayout.noteAt(whiteIndex * 100f, yFrac * h, w, h, base = 48)

  @Test fun whiteKeysFollowTheScale() {
    assertEquals(48, at(0.5f, 0.9f))   // C3
    assertEquals(50, at(1.5f, 0.9f))   // D3
    assertEquals(52, at(2.5f, 0.9f))   // E3
    assertEquals(53, at(3.5f, 0.9f))   // F3
    assertEquals(60, at(7.5f, 0.9f))   // C4
    assertEquals(72, at(14.5f, 0.9f))  // the top C
  }

  @Test fun blackKeysWinInTheUpperPart() {
    assertEquals(49, at(0.9f, 0.3f))   // C#3, right edge of C
    assertEquals(49, at(1.1f, 0.3f))   // C#3, left edge of D
    assertEquals(48, at(0.9f, 0.9f))   // same x, lower: the white key
    assertEquals(52, at(2.9f, 0.3f))   // E has no sharp: still E
    assertEquals(54, at(3.9f, 0.3f))   // F#3
  }

  @Test fun outsideIsNothing() {
    assertNull(KeyboardLayout.noteAt(-1f, 10f, w, h, 48))
    assertNull(KeyboardLayout.noteAt(10f, 301f, w, h, 48))
  }

  @Test fun velocityGrowsTowardsTheBottom() {
    assertEquals(0.3f, KeyboardLayout.velocity(0f, h), 1e-6f)
    assertEquals(1f, KeyboardLayout.velocity(h, h), 1e-6f)
  }
}
