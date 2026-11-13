package com.criapix.volt.preset

import com.criapix.volt.state.SynthState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The current preset and everything done to presets. No Android types: unit-tested on the JVM.
 * Call from the UI thread.
 */
class AppModel(
  val state: SynthState,
  val library: PresetLibrary,
  private val sessionFile: File,
  private val log: (String) -> Unit = {},
) {
  /** The preset the sound came from. [id] is null when it no longer exists (deleted). */
  data class Current(val id: String?, val name: String, val category: String)

  private val _current = MutableStateFlow(Current(null, "Init", ""))
  private val _presets = MutableStateFlow(library.all())
  private val _favorites = MutableStateFlow(library.favoriteIds())
  private val _message = MutableStateFlow<String?>(null)

  val current: StateFlow<Current> = _current
  val presets: StateFlow<List<PresetRef>> = _presets
  val favorites: StateFlow<Set<String>> = _favorites
  /** A short message for the user (Portuguese); the screen clears it after showing it. */
  val message: StateFlow<String?> = _message

  fun restore() {
    val s = Session.load(sessionFile)
    if (s == null) {
      _presets.value.firstOrNull()?.let { select(it.id) }
      return
    }
    state.load(PresetCodec.toValues(s.current, state.params))
    if (s.dirty) state.markDirty()
    val id = s.presetId?.takeIf { id -> _presets.value.any { it.id == id } }
    _current.value = Current(id, s.current.name, s.current.category)
    log("PRESET sessão restaurada: ${s.current.name}${if (s.dirty) " (com edições)" else ""}")
  }

  fun select(id: String): Boolean = try {
    val p = library.read(id)
    state.load(PresetCodec.toValues(p, state.params))
    _current.value = Current(id, p.name, p.category)
    log("PRESET carregado: ${p.name} ($id)")
    true
  } catch (e: PresetError) {
    _message.value = e.message
    false
  }

  /** Previous (-1) / next (+1) in library order, wrapping around. */
  fun step(delta: Int) {
    val list = _presets.value
    if (list.isEmpty()) return
    val i = list.indexOfFirst { it.id == _current.value.id }
    val next = if (i < 0) (if (delta > 0) 0 else list.lastIndex) else Math.floorMod(i + delta, list.size)
    select(list[next].id)
  }

  /** MIDI Program Change [program] (0-based) loads the preset at position program+1 in library order. */
  fun programChange(program: Int) {
    val ref = _presets.value.getOrNull(program)
    if (ref == null) { _message.value = "Programa ${program + 1}: não há preset nessa posição"; return }
    select(ref.id)
  }

  /** 1-based position in library order, or 0 when the current sound is not a stored preset. */
  fun number(id: String?): Int = _presets.value.indexOfFirst { it.id == id } + 1

  fun canOverwrite(): Boolean = _current.value.id?.startsWith(PresetLibrary.USER) == true

  /** Overwrites the current user preset with the sound on screen. False when it is a factory preset. */
  fun save(name: String, category: String): Boolean {
    val id = _current.value.id
    if (!canOverwrite() || id == null) return false
    val clean = name.trim().ifEmpty { "Sem nome" }
    library.overwrite(id, snapshot(clean, category))
    _current.value = Current(id, clean, category)
    state.markClean()
    refresh()
    log("PRESET salvo: $clean ($id)")
    return true
  }

  fun saveAs(name: String, category: String): PresetRef {
    val ref = library.create(snapshot(name.trim().ifEmpty { "Sem nome" }, category))
    _current.value = Current(ref.id, ref.name, ref.category)
    state.markClean()
    refresh()
    log("PRESET criado: ${ref.name} (${ref.id})")
    return ref
  }

  fun rename(id: String, name: String) {
    val clean = name.trim().ifEmpty { return }
    library.rename(id, clean)
    if (_current.value.id == id) _current.value = _current.value.copy(name = clean)
    refresh()
  }

  fun delete(id: String) {
    library.delete(id)
    if (_current.value.id == id) {
      _current.value = _current.value.copy(id = null)
      state.markDirty()
    }
    refresh()
    log("PRESET apagado: $id")
  }

  fun toggleFavorite(id: String) {
    library.setFavorite(id, !library.isFavorite(id))
    refresh()
  }

  fun exportBank(): String = library.exportUser()

  /** Adds the presets in a file the user picked. Returns how many were added. */
  fun importText(text: String): Int = try {
    val n = library.import(text).size
    refresh()
    _message.value = if (n == 1) "1 preset importado" else "$n presets importados"
    log("PRESET importados: $n")
    n
  } catch (e: PresetError) {
    _message.value = e.message
    0
  }

  /** Adds the presets in a file the user picked (JSON or DX7 sysex). Returns how many were added. */
  fun importBytes(bytes: ByteArray, stem: String): Int = try {
    val r = library.importBytes(bytes, stem)
    val n = r.refs.size
    refresh()
    val count = if (n == 1) "1 preset" else "$n presets"
    _message.value = if (r.checksumOk) "$count importado${if (n == 1) "" else "s"}" else "Importado com checksum inválido: $count"
    log("PRESET importados: $n${if (r.checksumOk) "" else " (checksum inválido)"}")
    n
  } catch (e: PresetError) {
    _message.value = e.message
    0
  }

  /** True when the preset is an FM (DX7) voice. */
  fun isFm(id: String): Boolean = try {
    library.read(id).params["engine.type"] == 1f
  } catch (e: PresetError) {
    false
  }

  /** The preset as a 163-byte single-voice sysex, or null when it is not an FM preset. */
  fun exportSyx(id: String): ByteArray? = try {
    library.read(id).takeIf { it.params["engine.type"] == 1f }?.let(Dx7Codec::writeVoice)
  } catch (e: PresetError) {
    null
  }

  /**
   * The user's FM presets of [category] as a 32-voice bank (library order), with the number of presets dropped
   * beyond 32. Null when there are none. Fewer than 32 are padded with INIT VOICE.
   */
  fun exportSyxBank(category: String): Pair<ByteArray, Int>? {
    val fm = library.all().filter { it.isUser && it.category == category }.mapNotNull { ref ->
      runCatching { library.read(ref.id) }.getOrNull()?.takeIf { it.params["engine.type"] == 1f }
    }
    if (fm.isEmpty()) return null
    return Dx7Codec.writeBank(fm.take(32)) to (fm.size - 32).coerceAtLeast(0)
  }

  fun showMessage(text: String) { _message.value = text }
  fun clearMessage() { _message.value = null }

  /** Writes the session file. Call when the app goes to the background. */
  fun persist() {
    val c = _current.value
    Session.save(sessionFile, c.id, state.dirty.value, snapshot(c.name, c.category))
  }

  private fun snapshot(name: String, category: String) =
    PresetCodec.fromValues(name, category, state.snapshot(), state.params)

  private fun refresh() {
    _presets.value = library.all()
    _favorites.value = library.favoriteIds()
  }
}
