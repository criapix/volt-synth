package com.criapix.volt.preset

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SessionFile(val presetId: String? = null, val dirty: Boolean = false, val current: PresetFile)

/** The sound on screen when the app stopped, so the next start continues from it. */
object Session {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  fun save(file: File, presetId: String?, dirty: Boolean, current: PresetFile) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(json.encodeToString(SessionFile.serializer(), SessionFile(presetId, dirty, current)))
    if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
  }

  fun load(file: File): SessionFile? =
    if (!file.exists()) null else runCatching { json.decodeFromString(SessionFile.serializer(), file.readText()) }.getOrNull()
}
