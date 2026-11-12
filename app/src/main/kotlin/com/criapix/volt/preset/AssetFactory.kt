package com.criapix.volt.preset

import android.content.res.AssetManager

/** Factory presets shipped in assets/presets/factory, ordered by file name. */
class AssetFactory(private val assets: AssetManager, private val dir: String = "presets/factory") : FactorySource {
  override fun list(): List<Pair<String, String>> =
    (assets.list(dir) ?: emptyArray()).filter { it.endsWith(".json") }.sorted()
      .map { name -> name.removeSuffix(".json") to assets.open("$dir/$name").bufferedReader().use { it.readText() } }
}
