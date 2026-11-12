package com.criapix.volt

import java.io.File

/** The engine's parameter table, read from engine/src/params.def (Gradle runs unit tests from app/). */
object TestParams {
  private val line = Regex("""^\s*P\(\s*\w+\s*,\s*"([^"]+)"\s*,\s*([-0-9.]+)f?\s*,\s*([-0-9.]+)f?\s*,\s*([-0-9.]+)f?\s*,\s*(\w+)\s*\)""")

  val params: Params by lazy {
    val def = File("../engine/src/params.def")
    check(def.exists()) { "params.def not found from ${File(".").absolutePath}" }
    val table = def.readLines().mapNotNull { line.find(it) }.joinToString("\n") { m ->
      val (name, min, max, d, curve) = m.destructured
      "$name|$min|$max|$d|${curve.lowercase()}"
    }
    Params.parse(table)
  }
}
