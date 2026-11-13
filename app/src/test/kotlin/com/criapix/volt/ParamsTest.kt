package com.criapix.volt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParamsTest {
  private val table = "osc1.wave|0.000000|3.000000|0.000000|step\n" +
      "filter.cutoff|20.000000|20000.000000|20000.000000|exp\n" +
      "filter.reso|0.000000|1.000000|0.100000|lin\n"

  @Test fun parsesEveryLineInOrder() {
    val p = Params.parse(table)
    assertEquals(3, p.all.size)
    assertEquals("filter.cutoff", p.all[1].name)
    assertEquals(1, p.all[1].id)
    assertEquals(Curve.EXP, p.all[1].curve)
    assertEquals(20000f, p.all[1].max, 0f)
  }

  @Test fun findsAParameterByName() {
    val p = Params.parse(table)
    assertEquals(2, p["filter.reso"]?.id)
    assertNull(p["nope"])
  }

  @Test fun convertsBetweenNormalizedAndRealValues() {
    val p = Params.parse(table)
    val cutoff = p["filter.cutoff"]!!
    assertEquals(632.46f, cutoff.toReal(0.5f), 0.5f)       // geometric middle of 20..20000
    assertEquals(0.5f, cutoff.toNorm(632.46f), 0.001f)
    assertEquals(1f, cutoff.toNorm(cutoff.def), 0f)
    assertEquals(1f, p["osc1.wave"]!!.toReal(0.4f), 0f)    // steps are whole numbers
    assertEquals(0.1f, p["filter.reso"]!!.toNorm(0.1f), 0.0001f)
  }

  @Test fun theRealTableIsReadFromTheEngineSource() {
    val p = TestParams.params
    assertEquals(263, p.all.size)
    assertEquals(Curve.EXP, p["filter.cutoff"]?.curve)
    assertEquals(null, p["osc2.sync"])
    assertEquals(Curve.STEP, p["osc1.sync"]?.curve)
  }
}
