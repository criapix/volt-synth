package com.criapix.volt.preset

import com.criapix.volt.Params
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class PresetFile(
  val format: Int = PresetCodec.FORMAT,
  val name: String,
  val category: String = "",
  /** Permanent parameter name -> real value (Hz, seconds, semitones...). */
  val params: Map<String, Float> = emptyMap(),
  /** Position (1..32) in the DX7 bank this voice was imported from; 0 when unknown. Orders bank export. */
  val slot: Int = 0,
)

@Serializable
data class BankFile(val format: Int = PresetCodec.FORMAT, val presets: List<PresetFile>)

/** A preset file that cannot be used. The message is shown to the user (Portuguese). */
class PresetError(message: String) : Exception(message)

object PresetCodec {
  const val FORMAT = 1
  val CATEGORIES = listOf("Bass", "Lead", "Pad", "Brass", "Strings", "Keys", "Pluck", "FX")

  private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

  fun decode(text: String): PresetFile {
    val preset = try {
      json.decodeFromString(PresetFile.serializer(), text)
    } catch (e: Exception) {
      throw PresetError("Arquivo de preset inválido")
    }
    if (preset.name.isBlank()) throw PresetError("Preset sem nome")
    return preset
  }

  /** A single preset file or a bank (export). */
  fun decodeAny(text: String): List<PresetFile> {
    val element = try { json.parseToJsonElement(text) } catch (e: Exception) { throw PresetError("Arquivo inválido") }
    val obj = element as? JsonObject ?: throw PresetError("Arquivo inválido")
    if ("presets" !in obj) return listOf(decode(text))
    val bank = try { json.decodeFromJsonElement(BankFile.serializer(), obj) } catch (e: Exception) { throw PresetError("Banco de presets inválido") }
    return bank.presets.filter { it.name.isNotBlank() }
  }

  fun encode(p: PresetFile): String = json.encodeToString(PresetFile.serializer(), p)
  fun encodeBank(list: List<PresetFile>): String = json.encodeToString(BankFile.serializer(), BankFile(FORMAT, list))

  /** Normalized values indexed by parameter id: defaults for what is missing, clamped, unknown names ignored. */
  fun toValues(p: PresetFile, params: Params): FloatArray = FloatArray(params.all.size) { i ->
    val prm = params.all[i]
    val v = p.params[prm.name]
    if (v == null || !v.isFinite()) prm.toNorm(prm.def) else prm.toNorm(v)
  }

  /** A complete file (every parameter, in table order) from normalized values. */
  fun fromValues(name: String, category: String, values: FloatArray, params: Params): PresetFile =
    PresetFile(FORMAT, name, category, params.all.associate { it.name to round5(it.toReal(values[it.id])) })

  // Five significant digits: readable files, far below what anyone can hear.
  private fun round5(v: Float): Float = String.format(Locale.ROOT, "%.5g", v).toFloat()
}
