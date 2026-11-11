package com.criapix.volt

/** The C++ engine. Every call is thread-safe and returns at once. */
object Native {
  init { System.loadLibrary("volt") }

  /** One line per parameter: `name|min|max|default|curve`. The line number is the parameter id. */
  external fun paramTable(): String
}
