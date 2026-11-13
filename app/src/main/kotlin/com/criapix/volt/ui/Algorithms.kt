package com.criapix.volt.ui

/**
 * One DX7 algorithm, decoded from msfa's own flag table. Operators are numbered 1..6 as on the instrument.
 * [feedback] is the operator that receives the feedback (FB_IN) and [feedbackFrom] the operator whose output feeds
 * it back: the same operator for a self-loop (most algorithms), another one for the loops of algorithms 4 and 6.
 */
data class Algo(
  val carriers: Set<Int>,
  val modulators: Map<Int, Set<Int>>,
  val feedback: Int,
  val feedbackFrom: Int,
) {
  /** The operators that [op] modulates. */
  fun targetsOf(op: Int): Set<Int> = modulators.filterValues { op in it }.keys
}

/** A box position: [row] 0 is the top row, `rows - 1` the bottom (carriers) row. */
data class Cell(val column: Int, val row: Int)

data class AlgoLayout(val cells: Map<Int, Cell>, val columns: Int, val rows: Int)

object Algorithms {
  const val OUT_BUS_ADD = 4
  const val FB_IN = 0x40
  const val FB_OUT = 0x80

  /** [flags] is `Native.fmAlgorithms()`: 32 x 6 values, index 0 of each algorithm = OP6 (processing order). */
  fun decode(flags: IntArray): List<Algo> {
    require(flags.size == 32 * 6) { "expected 192 flag values, got ${flags.size}" }
    return List(32) { a ->
      val buses = arrayOf(mutableSetOf<Int>(), mutableSetOf<Int>(), mutableSetOf<Int>())  // [0] unused: main output
      val carriers = mutableSetOf<Int>()
      val modulators = mutableMapOf<Int, Set<Int>>()
      var fbIn = 0
      var fbOut = 0
      for (i in 0 until 6) {
        val f = flags[a * 6 + i]
        val op = 6 - i
        val outBus = f and 3
        val inBus = (f shr 4) and 3
        if (inBus != 0 && buses[inBus].isNotEmpty()) modulators[op] = buses[inBus].toSet()
        if (f and FB_IN != 0) fbIn = op
        // msfa keeps one feedback buffer: the last operator to write it (lowest number) is the one fed back.
        if (f and FB_OUT != 0) fbOut = op
        if (outBus == 0) carriers += op
        else if (f and OUT_BUS_ADD != 0) buses[outBus] += op
        else { buses[outBus].clear(); buses[outBus] += op }
      }
      Algo(carriers, modulators, fbIn, fbOut)
    }
  }

  /** Carriers on the bottom row by operator number; every modulator at least one row above all it modulates. */
  fun layout(algo: Algo): AlgoLayout {
    val level = mutableMapOf<Int, Int>()
    fun levelOf(op: Int): Int = level.getOrPut(op) {
      if (op in algo.carriers) 0 else 1 + (algo.targetsOf(op).maxOfOrNull { levelOf(it) } ?: 0)
    }
    for (op in 1..6) levelOf(op)
    val maxLevel = level.values.max()
    val cells = mutableMapOf<Int, Cell>()
    val taken = mutableSetOf<Pair<Int, Int>>()
    fun place(op: Int, desired: Int) {
      var col = desired
      val row = maxLevel - level[op]!!
      while ((col to row) in taken) col++
      taken += col to row
      cells[op] = Cell(col, row)
    }
    algo.carriers.sorted().forEachIndexed { i, op -> place(op, i) }
    for (lv in 1..maxLevel) {
      for (op in (1..6).filter { level[it] == lv }) {
        place(op, algo.targetsOf(op).mapNotNull { cells[it]?.column }.minOrNull() ?: 0)
      }
    }
    return AlgoLayout(cells, cells.values.maxOf { it.column } + 1, maxLevel + 1)
  }
}
