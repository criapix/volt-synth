package com.criapix.volt.ui

import com.criapix.volt.midi.MidiAction
import com.criapix.volt.midi.Source
import com.criapix.volt.midi.Target
import org.junit.Assert.assertEquals
import org.junit.Test

class LearnLabelsTest {
  @Test fun targetsReadLikeTheScreen() {
    assertEquals("Cutoff · FILTRO", targetLabel(Target.Param("filter.cutoff")))
    assertEquals("Ataque · ENVELOPE DE AMPLITUDE", targetLabel(Target.Param("aenv.attack")))
    assertEquals("Próximo preset", targetLabel(Target.Action(MidiAction.NEXT)))
    assertEquals("no.such (parâmetro removido)", targetLabel(Target.Param("no.such")))
  }

  @Test fun badgesAreShort() {
    assertEquals("CC 74", shortLabel(Source.Cc(0, 74)))
    assertEquals("CC 74/3", shortLabel(Source.Cc(2, 74)))
    assertEquals("C4", shortLabel(Source.Note(0, 60)))
  }
}
