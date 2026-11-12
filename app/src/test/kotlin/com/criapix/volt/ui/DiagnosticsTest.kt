package com.criapix.volt.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
  private val base = "running=1\nsampleRate=48000\nload=0.25\nvoices=3\n"

  @Test fun showsTheVoiceLimit() {
    val s = formatInfo(base + "voiceLimit=12\nmaxVoices=16\n")
    assertTrue(s, s.contains("Polifonia: 12"))
    assertTrue(s, s.contains("Carga do motor: 25 %"))
  }

  @Test fun stoppedAudioSaysSo() {
    assertTrue(formatInfo("running=0\n") == "Áudio parado")
  }
}
