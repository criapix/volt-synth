package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnobMathTest {
  @Test fun verticalDragCoversTheRangeInOneTravel() {
    assertEquals(1f, KnobMath.drag(0f, 0f, -200f, 200f, 0), 1e-6f)
    assertEquals(0.25f, KnobMath.drag(0.5f, 0f, 50f, 200f, 0), 1e-6f)
  }

  @Test fun horizontalDragIsTenTimesFiner() {
    assertEquals(0.55f, KnobMath.drag(0.5f, 100f, 0f, 200f, 0), 1e-6f)
  }

  @Test fun resultIsClampedAndStepsSnap() {
    assertEquals(0f, KnobMath.drag(0.1f, 0f, 500f, 200f, 0), 0f)
    assertEquals(0.5f, KnobMath.drag(0.5f, 0f, -10f, 200f, 4), 0f)  // 0.55 on 4 steps -> 0.5
    assertEquals(0.75f, KnobMath.drag(0.5f, 0f, -30f, 200f, 4), 0f) // 0.65 -> 0.75
  }

  @Test fun gridPicksTheColumnCountThatGivesTheLargestKnob() {
    val fit = KnobGrid.fit(n = 6, width = 220f, height = 200f)
    assertEquals(3, fit.columns)
    assertEquals(62f, fit.knob, 0.1f)  // limited by height: 200/2 rows - 30 label - 8 gap
  }

  @Test fun gridCapsTheSizeAndHandlesEmptyGroups() {
    assertEquals(KnobGrid.MAX, KnobGrid.fit(1, 500f, 500f).knob, 0f)
    assertEquals(KnobGrid.MAX, KnobGrid.fit(0, 10f, 10f).knob, 0f)
  }

  @Test fun compactGridNeverExceedsItsMaximumAndRespectsFixedColumns() {
    val fit = KnobGrid.fitCompact(5, 300f, 300f)
    assertTrue(fit.knob <= KnobGrid.COMPACT_MAX)
    val fixed = KnobGrid.fitCompact(8, 250f, 200f, columns = 4)
    assertEquals(4, fixed.columns)
    assertTrue(fixed.knob <= (250f / 4 - KnobGrid.COMPACT_GAP) + 0.001f)
  }

  @Test fun compactGridShrinksWithTheHeightItIsGiven() {
    val tall = KnobGrid.fitCompact(6, 240f, 200f)
    val short = KnobGrid.fitCompact(6, 240f, 90f)
    assertTrue(short.knob < tall.knob)
    assertTrue(KnobGrid.fitCompact(0, 100f, 100f).knob > 0f)
  }
}
