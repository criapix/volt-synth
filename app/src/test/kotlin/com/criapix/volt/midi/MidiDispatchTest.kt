package com.criapix.volt.midi

import com.criapix.volt.Params
import org.junit.Assert.assertEquals
import org.junit.Test

class MidiDispatchTest {
  private fun f2(x: Float) = String.format(java.util.Locale.ROOT, "%.2f", x)
  private val log = ArrayList<String>()
  private val synth = object : Synth {
    override fun noteOn(note: Int, velocity: Float) { log += "on $note ${f2(velocity)}" }
    override fun noteOff(note: Int) { log += "off $note" }
    override fun setParam(id: Int, normalized: Float) { log += "param $id ${f2(normalized)}" }
    override fun pitchBend(value: Float) { log += "bend ${f2(value)}" }
    override fun modWheel(value: Float) { log += "wheel ${f2(value)}" }
    override fun sustain(on: Boolean) { log += "sustain $on" }
    override fun allNotesOff() { log += "panic" }
  }
  private val params = Params.parse(
    "filter.cutoff|20|20000|20000|exp\nfilter.reso|0|1|0.1|lin\nmaster.volume|0|1|0.7|lin\n"
  )
  private val d = MidiDispatch(synth, params)
  private fun send(vararg b: Int) { val a = ByteArray(b.size) { b[it].toByte() }; d.message(a, a.size) }

  @Test fun notesCarryVelocity() {
    send(0x90, 60, 127); send(0x80, 60, 0)
    assertEquals(listOf("on 60 1.00", "off 60"), log)
  }

  @Test fun noteOnWithZeroVelocityIsANoteOff() {
    send(0x90, 60, 0)
    assertEquals(listOf("off 60"), log)
  }

  @Test fun pitchBendIsCentredAtZero() {
    send(0xE0, 0, 64); send(0xE0, 127, 127); send(0xE0, 0, 0)
    assertEquals(listOf("bend 0.00", "bend 1.00", "bend -1.00"), log)
  }

  @Test fun theStandardControllersWorkOutOfTheBox() {
    send(0xB0, 1, 127); send(0xB0, 64, 127); send(0xB0, 64, 0)
    send(0xB0, 74, 0); send(0xB0, 71, 127); send(0xB0, 7, 127)
    assertEquals(
      listOf("wheel 1.00", "sustain true", "sustain false", "param 0 0.00", "param 1 1.00", "param 2 1.00"), log
    )
  }

  @Test fun allSoundOffAndAllNotesOffPanic() {
    send(0xB0, 120, 0); send(0xB0, 123, 0)
    assertEquals(listOf("panic", "panic"), log)
  }

  @Test fun omniListensToEveryChannelAndAFixedChannelOnlyToItself() {
    send(0x95, 60, 100)
    assertEquals(1, log.size)
    d.channel = 2
    send(0x95, 61, 100)
    assertEquals(1, log.size)
    send(0x92, 62, 100)
    assertEquals(2, log.size)
  }

  @Test fun unknownAndShortMessagesAreIgnored() {
    send(0xF8); send(0xB0, 99, 5); send(0xD0, 40); send(0xC0, 3)
    assertEquals(emptyList<String>(), log)
  }

  @Test fun reportsTheLastMessageForTheMonitor() {
    send(0xB3, 74, 10)
    assertEquals("CC 74 · canal 4 = 10", d.lastMessage.value)
    send(0x90, 60, 100)
    assertEquals("Nota 60 · canal 1 = 100", d.lastMessage.value)
  }
}
