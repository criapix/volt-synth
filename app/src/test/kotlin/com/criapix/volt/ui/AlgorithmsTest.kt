package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgorithmsTest {
  // msfa's FmCore::algorithms, 32 x 6 flag values (index 0 = OP6), copied from fm_core.cpp. Only used to test the decoder.
  private val flags = intArrayOf(
    0xc1, 0x11, 0x11, 0x14, 0x01, 0x14,  // 1
    0x01, 0x11, 0x11, 0x14, 0xc1, 0x14,  // 2
    0xc1, 0x11, 0x14, 0x01, 0x11, 0x14,  // 3
    0xc1, 0x11, 0x94, 0x01, 0x11, 0x14,  // 4
    0xc1, 0x14, 0x01, 0x14, 0x01, 0x14,  // 5
    0xc1, 0x94, 0x01, 0x14, 0x01, 0x14,  // 6
    0xc1, 0x11, 0x05, 0x14, 0x01, 0x14,  // 7
    0x01, 0x11, 0xc5, 0x14, 0x01, 0x14,  // 8
    0x01, 0x11, 0x05, 0x14, 0xc1, 0x14,  // 9
    0x01, 0x05, 0x14, 0xc1, 0x11, 0x14,  // 10
    0xc1, 0x05, 0x14, 0x01, 0x11, 0x14,  // 11
    0x01, 0x05, 0x05, 0x14, 0xc1, 0x14,  // 12
    0xc1, 0x05, 0x05, 0x14, 0x01, 0x14,  // 13
    0xc1, 0x05, 0x11, 0x14, 0x01, 0x14,  // 14
    0x01, 0x05, 0x11, 0x14, 0xc1, 0x14,  // 15
    0xc1, 0x11, 0x02, 0x25, 0x05, 0x14,  // 16
    0x01, 0x11, 0x02, 0x25, 0xc5, 0x14,  // 17
    0x01, 0x11, 0x11, 0xc5, 0x05, 0x14,  // 18
    0xc1, 0x14, 0x14, 0x01, 0x11, 0x14,  // 19
    0x01, 0x05, 0x14, 0xc1, 0x14, 0x14,  // 20
    0x01, 0x14, 0x14, 0xc1, 0x14, 0x14,  // 21
    0xc1, 0x14, 0x14, 0x14, 0x01, 0x14,  // 22
    0xc1, 0x14, 0x14, 0x01, 0x14, 0x04,  // 23
    0xc1, 0x14, 0x14, 0x14, 0x04, 0x04,  // 24
    0xc1, 0x14, 0x14, 0x04, 0x04, 0x04,  // 25
    0xc1, 0x05, 0x14, 0x01, 0x14, 0x04,  // 26
    0x01, 0x05, 0x14, 0xc1, 0x14, 0x04,  // 27
    0x04, 0xc1, 0x11, 0x14, 0x01, 0x14,  // 28
    0xc1, 0x14, 0x01, 0x14, 0x04, 0x04,  // 29
    0x04, 0xc1, 0x11, 0x14, 0x04, 0x04,  // 30
    0xc1, 0x14, 0x04, 0x04, 0x04, 0x04,  // 31
    0xc4, 0x04, 0x04, 0x04, 0x04, 0x04,  // 32
  )
  private val algos = Algorithms.decode(flags)
  private fun alg(n: Int) = algos[n - 1]

  @Test fun algorithm1() {
    val a = alg(1)
    assertEquals(setOf(1, 3), a.carriers)
    assertEquals(mapOf(1 to setOf(2), 3 to setOf(4), 4 to setOf(5), 5 to setOf(6)), a.modulators)
    assertEquals(6, a.feedback); assertEquals(6, a.feedbackFrom)
  }

  @Test fun rolesAreCarrierOrModulator() {
    assertEquals(listOf("C", "M", "C", "M", "C", "M"), (1..6).map { alg(5).role(it) })
    assertEquals(List(6) { "C" }, (1..6).map { alg(32).role(it) })
  }

  @Test fun algorithm5IsThreePairs() {
    val a = alg(5)
    assertEquals(setOf(1, 3, 5), a.carriers)
    assertEquals(mapOf(1 to setOf(2), 3 to setOf(4), 5 to setOf(6)), a.modulators)
  }

  @Test fun algorithm22() {
    val a = alg(22)
    assertEquals(setOf(1, 3, 4, 5), a.carriers)
    assertEquals(mapOf(1 to setOf(2), 3 to setOf(6), 4 to setOf(6), 5 to setOf(6)), a.modulators)
    assertEquals(6, a.feedback)
  }

  @Test fun algorithm32IsAllCarriers() {
    val a = alg(32)
    assertEquals(setOf(1, 2, 3, 4, 5, 6), a.carriers)
    assertTrue(a.modulators.isEmpty())
    assertEquals(6, a.feedback)
  }

  @Test fun algorithms4And6FeedBackThroughALoop() {
    assertEquals(6, alg(4).feedback); assertEquals(4, alg(4).feedbackFrom)
    assertEquals(6, alg(6).feedback); assertEquals(5, alg(6).feedbackFrom)
  }

  @Test fun everyAlgorithmHasCarriersAndOneFeedbackReceiver() {
    algos.forEachIndexed { i, a ->
      assertTrue("alg ${i + 1}", a.carriers.size in 1..6)
      assertTrue("alg ${i + 1}", a.feedback in 1..6 && a.feedbackFrom in 1..6)
      // everything that is not a carrier modulates something, and nothing modulates itself
      for (op in 1..6) {
        assertTrue("alg ${i + 1} op $op", op in a.carriers || a.targetsOf(op).isNotEmpty())
        assertTrue(op !in (a.modulators[op] ?: emptySet()))
      }
    }
  }

  @Test fun layoutHasNoOverlapsAndPutsCarriersOnTheBottom() {
    algos.forEachIndexed { i, a ->
      val l = Algorithms.layout(a)
      assertEquals("alg ${i + 1}", 6, l.cells.values.toSet().size)
      assertEquals(6, l.cells.size)
      assertTrue("alg ${i + 1} width", l.columns <= 6)
      a.carriers.forEach { assertEquals("alg ${i + 1} carrier $it", l.rows - 1, l.cells[it]!!.row) }
      a.carriers.sorted().zipWithNext { x, y -> assertTrue(l.cells[x]!!.column < l.cells[y]!!.column) }
      a.modulators.forEach { (target, mods) ->
        mods.forEach { m -> assertTrue("alg ${i + 1} $m above $target", l.cells[m]!!.row < l.cells[target]!!.row) }
      }
    }
  }

  @Test fun algorithm1LayoutIsTwoChains() {
    val l = Algorithms.layout(alg(1))
    assertEquals(4, l.rows)  // OP3 <- OP4 <- OP5 <- OP6
    assertEquals(Cell(0, 3), l.cells[1])
    assertEquals(Cell(0, 2), l.cells[2])
    assertEquals(Cell(1, 3), l.cells[3])
    assertEquals(Cell(1, 0), l.cells[6])
  }
}
