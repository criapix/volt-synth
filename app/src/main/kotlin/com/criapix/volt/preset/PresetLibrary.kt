package com.criapix.volt.preset

import java.io.File
import java.util.UUID

data class PresetRef(val id: String, val name: String, val category: String) {
  val isUser: Boolean get() = id.startsWith(PresetLibrary.USER)
}

/** Read-only presets shipped with the app: (file stem, JSON text), in order. */
fun interface FactorySource { fun list(): List<Pair<String, String>> }

/** DX7 cartridges (.syx banks) shipped with the app: (file stem, bytes), sorted by file name. */
fun interface CartridgeSource { fun list(): List<Pair<String, ByteArray>> }

interface FavoriteStore {
  fun load(): Set<String>
  fun save(ids: Set<String>)
}

/** Factory presets from a directory (tests). The app uses [AssetFactory]. */
class DirFactory(private val dir: File) : FactorySource {
  override fun list() = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
    .sortedBy { it.name }.map { it.name.removeSuffix(".json") to it.readText() }
}

/** DX7 cartridges from a directory (tests). The app uses [AssetCartridges]. */
class DirCartridges(private val dir: File) : CartridgeSource {
  override fun list() = dir.listFiles { f -> f.name.endsWith(".syx") }.orEmpty()
    .sortedBy { it.name }.map { it.name.removeSuffix(".syx") to it.readBytes() }
}

/** What an import added; [checksumOk] is false when a .syx file had a bad checksum (the voices are still imported). */
data class ImportResult(val refs: List<PresetRef>, val checksumOk: Boolean)

/**
 * Factory presets (read-only) followed by the user's presets (one JSON file each in [userDir]).
 * Not thread-safe: call from the UI thread. Files are tiny, so plain blocking I/O is fine.
 */
class PresetLibrary(
  factory: FactorySource,
  private val userDir: File,
  private val favorites: FavoriteStore,
  cartridges: CartridgeSource = CartridgeSource { emptyList() },
  log: (String) -> Unit = {},
) {
  companion object {
    const val FACTORY = "factory:"
    const val USER = "user:"
  }

  private val jsonFactory: List<Pair<PresetRef, PresetFile>> = factory.list().mapNotNull { (stem, text) ->
    runCatching { PresetCodec.decode(text) }.getOrNull()?.let { PresetRef(FACTORY + stem, it.name, it.category) to it }
  }

  /** Cartridge voices: id factory:dx7-<stem>-<NN>, category "DX7 <STEM>". A broken cartridge is skipped. */
  private val cartridgeVoices: List<Pair<PresetRef, PresetFile>> = cartridges.list().sortedBy { it.first }.flatMap { (stem, bytes) ->
    try {
      Dx7Codec.readBank(bytes, stem.uppercase()).presets.mapIndexed { i, p ->
        PresetRef("${FACTORY}dx7-${stem.lowercase()}-${(i + 1).toString().padStart(2, '0')}", p.name, p.category) to p
      }
    } catch (e: PresetError) {
      log("PRESET cartucho ignorado: $stem (${e.message})")
      emptyList()
    }
  }

  private val factoryPresets = jsonFactory + cartridgeVoices
  private val favs = favorites.load().toMutableSet()
  private var cache: List<PresetRef>? = null

  fun all(): List<PresetRef> = cache ?: (factoryPresets.map { it.first } + userRefs()).also { cache = it }

  fun read(id: String): PresetFile = when {
    id.startsWith(FACTORY) -> factoryPresets.firstOrNull { it.first.id == id }?.second
    id.startsWith(USER) -> fileOf(id).takeIf { it.exists() }?.let { PresetCodec.decode(it.readText()) }
    else -> null
  } ?: throw PresetError("Preset não encontrado")

  fun create(p: PresetFile): PresetRef {
    val stem = UUID.randomUUID().toString()
    write(File(userDir, "$stem.json"), p)
    return PresetRef(USER + stem, p.name, p.category)
  }

  fun overwrite(id: String, p: PresetFile) {
    require(id.startsWith(USER)) { "factory presets are read-only" }
    write(fileOf(id), p)
  }

  fun rename(id: String, name: String) = overwrite(id, read(id).copy(name = name))

  fun delete(id: String) {
    require(id.startsWith(USER)) { "factory presets are read-only" }
    fileOf(id).delete()
    if (favs.remove(id)) favorites.save(favs.toSet())
    cache = null
  }

  fun isFavorite(id: String) = id in favs
  fun favoriteIds(): Set<String> = favs.toSet()

  fun setFavorite(id: String, on: Boolean) {
    if (on) favs += id else favs -= id
    favorites.save(favs.toSet())
  }

  /** Every user preset as one bank file (backup). */
  fun exportUser(): String = PresetCodec.encodeBank(all().filter { it.isUser }.map { read(it.id) })

  /** Adds every preset in [text] (a preset or a bank) as a new user preset. Names that exist get " (2)", " (3)"... */
  fun import(text: String): List<PresetRef> = PresetCodec.decodeAny(text).map { create(it.copy(name = uniqueName(it.name))) }

  /**
   * Adds the presets in a file's [bytes] as new user presets. A file starting with 0xF0 is DX7 sysex (category
   * "DX7 <stem>"); anything else is JSON text. Throws [PresetError] when the file cannot be used.
   */
  fun importBytes(bytes: ByteArray, stem: String): ImportResult {
    if (bytes.firstOrNull() != 0xF0.toByte()) return ImportResult(import(bytes.toString(Charsets.UTF_8)), true)
    val read = Dx7Codec.readAny(bytes, stem)
    return ImportResult(read.presets.map { create(it.copy(name = uniqueName(it.name))) }, read.checksumOk)
  }

  private fun uniqueName(name: String): String {
    val taken = all().filter { it.isUser }.map { it.name.lowercase() }.toSet()
    if (name.lowercase() !in taken) return name
    var i = 2
    while ("$name ($i)".lowercase() in taken) i++
    return "$name ($i)"
  }

  private fun userRefs(): List<PresetRef> = userDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
    .mapNotNull { f ->
      runCatching { PresetCodec.decode(f.readText()) }.getOrNull()
        ?.let { PresetRef(USER + f.name.removeSuffix(".json"), it.name, it.category) }
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

  private fun write(f: File, p: PresetFile) {
    userDir.mkdirs()
    val tmp = File(userDir, f.name + ".tmp")
    tmp.writeText(PresetCodec.encode(p))
    if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }  // Windows (tests) cannot rename over a file
    cache = null
  }

  private fun fileOf(id: String) = File(userDir, id.removePrefix(USER) + ".json")
}
