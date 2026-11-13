package com.criapix.volt.preset

import android.content.res.AssetManager

/** Factory presets shipped in assets/presets/factory, ordered by file name. */
class AssetFactory(private val assets: AssetManager, private val dir: String = "presets/factory") : FactorySource {
  override fun list(): List<Pair<String, String>> =
    (assets.list(dir) ?: emptyArray()).filter { it.endsWith(".json") }.sorted()
      .map { name -> name.removeSuffix(".json") to assets.open("$dir/$name").bufferedReader().use { it.readText() } }
}

/** DX7 cartridges shipped in assets/dx7 (.syx banks), ordered by file name. */
class AssetCartridges(private val assets: AssetManager, private val dir: String = "dx7") : CartridgeSource {
  override fun list(): List<Pair<String, ByteArray>> =
    (assets.list(dir) ?: emptyArray()).filter { it.endsWith(".syx") }.sorted()
      .map { name -> name.removeSuffix(".syx") to assets.open("$dir/$name").use { it.readBytes() } }
}
