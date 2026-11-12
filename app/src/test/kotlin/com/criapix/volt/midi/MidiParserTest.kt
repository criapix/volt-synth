package com.criapix.volt.midi

import org.junit.Assert.assertEquals
import org.junit.Test

class MidiParserTest {
  private val got = ArrayList<List<Int>>()
  private val parser = MidiParser { b, n -> got += List(n) { b[it].toInt() and 0xFF } }
  private fun feed(vararg b: Int) { val a = ByteArray(b.size) { b[it].toByte() }; parser.feed(a, 0, a.size) }

  @Test fun wholeMessagesComeOutWhole() {
    feed(0x90, 60, 100, 0x80, 60, 0)
    assertEquals(listOf(listOf(0x90, 60, 100), listOf(0x80, 60, 0)), got)
  }

  @Test fun runningStatusIsExpanded() {
    feed(0x90, 60, 100, 62, 90)
    assertEquals(listOf(listOf(0x90, 60, 100), listOf(0x90, 62, 90)), got)
  }

  @Test fun aMessageSplitAcrossTwoPacketsIsJoined() {
    feed(0xB0, 74)
    feed(10)
    assertEquals(listOf(listOf(0xB0, 74, 10)), got)
  }

  @Test fun realTimeBytesPassThroughInsideAMessage() {
    feed(0x90, 60, 0xF8, 100)
    assertEquals(listOf(listOf(0xF8), listOf(0x90, 60, 100)), got)
  }

  @Test fun twoByteMessagesAreTwoBytesLong() {
    feed(0xC0, 5, 0xD0, 40)
    assertEquals(listOf(listOf(0xC0, 5), listOf(0xD0, 40)), got)
  }
}
