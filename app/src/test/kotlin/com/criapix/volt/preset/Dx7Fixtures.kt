package com.criapix.volt.preset

import java.io.File
import org.junit.Assume.assumeTrue

/**
 * The DX7 factory cartridges (the .syx files in assets/dx7) are Yamaha data and are not distributed with the repository;
 * each developer may drop their own copies there. Tests that need them call [rom] / [cartridges], which report
 * the test as SKIPPED when the file is absent. Everything else uses [synthBank], hand-made data.
 */
object Dx7Fixtures {
  val dir = File("src/main/assets/dx7")

  /** Bytes of one cartridge; skips the calling test when that file is not there. */
  fun rom(stem: String): ByteArray {
    val f = File(dir, "$stem.syx")
    assumeTrue("DX7 cartridge ${f.path} not present (not distributed with the repository)", f.isFile)
    return f.readBytes()
  }

  /** Every .syx in the folder, sorted by name; skips the calling test when there is none. */
  fun cartridges(): List<File> {
    val files = (dir.listFiles { x -> x.isFile && x.name.endsWith(".syx") } ?: emptyArray()).sortedBy { it.name }
    assumeTrue("no DX7 cartridges in ${dir.path} (not distributed with the repository)", files.isNotEmpty())
    return files
  }

  /** 32 distinct FM voices named "<prefix> 01".."<prefix> 32". */
  fun synthPresets(prefix: String = "SYNTH"): List<PresetFile> = List(32) { i ->
    PresetFile(
      name = "$prefix ${(i + 1).toString().padStart(2, '0')}",
      params = mapOf(
        "engine.type" to 1f,
        "fm.algorithm" to ((i % 32) + 1).toFloat(),
        "fm.feedback" to (i % 8).toFloat(),
        "fm.op1.level" to (40 + i).toFloat(),
        "fm.op6.r1" to (10 + i).toFloat(),
        "fm.op3.detune" to (i % 15).toFloat(),
      ),
    )
  }

  /** A valid 4104-byte bank sysex built by hand. */
  fun synthBank(prefix: String = "SYNTH"): ByteArray = Dx7Codec.writeBank(synthPresets(prefix))
}
