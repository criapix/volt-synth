package com.criapix.volt.ui

import com.criapix.volt.Curve
import com.criapix.volt.TestParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParamUiTest {
  private val p = TestParams.params

  @Test fun everyParameterHasAWidgetThatFitsItsRange() {
    for (prm in p.all) {
      when (val ui = ParamUi.of(prm.name)) {
        is ChoiceUi -> {
          assertEquals(prm.name, Curve.STEP, prm.curve)
          assertEquals(prm.name, (prm.max - prm.min).toInt() + 1, ui.options.size)
        }
        is ToggleUi -> assertTrue(prm.name, prm.curve == Curve.STEP && prm.min == 0f && prm.max == 1f)
        is KnobUi -> assertTrue(prm.name, ui.label.isNotBlank())
      }
    }
  }

  @Test fun valuesAreWrittenWithUnitsInPortuguese() {
    fun f(name: String, v: Float) = ParamUi.format(p[name]!!, v)
    assertEquals("440 Hz", f("filter.cutoff", 440f))
    assertEquals("1,2 kHz", f("filter.cutoff", 1234.5f))
    assertEquals("20,0 kHz", f("filter.cutoff", 20000f))
    assertEquals("0,30 Hz", f("lfo1.rate", 0.3f))
    assertEquals("250 ms", f("aenv.attack", 0.25f))
    assertEquals("3,0 ms", f("aenv.attack", 0.003f))
    assertEquals("1,50 s", f("aenv.release", 1.5f))
    assertEquals("desl.", f("voice.glide", 0f))
    assertEquals("120 BPM", f("arp.bpm", 120f))
    assertEquals("1/16", f("arp.division", 2f))
    assertEquals("I+II", f("fx.chorus.mode", 2f))
    assertEquals("375 ms", f("fx.delay.time", 0.375f))
    assertEquals("2,50 s", f("fx.reverb.decay", 2.5f))
    assertEquals("Sobe", f("arp.mode", 0f))
    assertEquals("50 %", f("filter.reso", 0.5f))
    assertEquals("-30 %", f("filter.env", -0.3f))
    assertEquals("+30 %", f("filter.env", 0.3f))
    assertEquals("+7 st", f("osc1.semi", 7f))
    assertEquals("-12 ct", f("osc1.fine", -12.4f))
    assertEquals("-1 oct", f("osc1.octave", -1f))
    assertEquals("0 oct", f("osc1.octave", 0f))
    assertEquals("±2 st", f("voice.bend", 2f))
    assertEquals("4", f("voice.unison", 4f))
    assertEquals("Ladder", f("filter.model", 0f))
    assertEquals("lig.", f("osc1.sync", 1f))
    assertEquals("99", f("fm.op1.level", 99f))
    assertEquals("-3", f("fm.op1.detune", -3f))
    assertEquals("0", f("fm.op1.detune", 0f))
    assertEquals("+4", f("fm.op1.detune", 4f))
    assertEquals("C3", f("fm.op1.kls.break", 39f))
    assertEquals("A-1", f("fm.op1.kls.break", 0f))
    assertEquals("C8", f("fm.op1.kls.break", 99f))
    assertEquals("C#3", f("fm.op1.kls.break", 40f))
    assertEquals("Seno", f("fm.lfo.wave", 4f))
    assertEquals("+12", f("fm.transpose", 12f))
    assertEquals("FM", f("engine.type", 1f))
  }
}
