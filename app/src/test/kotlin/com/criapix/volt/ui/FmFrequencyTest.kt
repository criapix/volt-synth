package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FmFrequencyTest {
  @Test fun ratioModeUsesHalfForCoarseZero() {
    assertEquals("×0,50", FmFrequency.format(false, 0, 0))
    assertEquals("×1,00", FmFrequency.format(false, 1, 0))
    assertEquals("×1,41", FmFrequency.format(false, 1, 41))
    assertEquals("×0,99", FmFrequency.format(false, 0, 98))
    assertEquals("×31,00", FmFrequency.format(false, 31, 0))
  }

  @Test fun fixedModeIsPowersOfTenFromOneHertz() {
    assertEquals("1 Hz", FmFrequency.format(true, 0, 0))
    assertEquals("10 Hz", FmFrequency.format(true, 1, 0))
    assertEquals("100 Hz", FmFrequency.format(true, 2, 0))
    assertEquals("1000 Hz", FmFrequency.format(true, 3, 0))
    assertEquals("9772 Hz", FmFrequency.format(true, 3, 99))
  }

  @Test fun fixedModeWrapsCoarseEveryFour() {
    assertEquals("1 Hz", FmFrequency.format(true, 4, 0))
    assertEquals("100 Hz", FmFrequency.format(true, 30, 0))
  }
}
