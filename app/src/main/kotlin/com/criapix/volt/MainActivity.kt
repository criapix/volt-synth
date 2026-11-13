package com.criapix.volt

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.criapix.volt.midi.MidiAction
import com.criapix.volt.midi.MidiDispatch
import com.criapix.volt.midi.MidiInput
import com.criapix.volt.midi.MidiLearn
import com.criapix.volt.midi.MidiMap
import com.criapix.volt.midi.MidiParser
import com.criapix.volt.midi.MidiSettings
import com.criapix.volt.midi.Synth
import com.criapix.volt.preset.AppModel
import com.criapix.volt.preset.AssetCartridges
import com.criapix.volt.preset.AssetFactory
import com.criapix.volt.preset.PresetLibrary
import com.criapix.volt.state.SynthState
import com.criapix.volt.ui.Haptics
import com.criapix.volt.ui.ScreenIo
import com.criapix.volt.ui.VoltScreen
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object NativeSynth : Synth {
  override fun noteOn(note: Int, velocity: Float) = Native.noteOn(note, velocity)
  override fun noteOff(note: Int) = Native.noteOff(note)
  override fun setParam(id: Int, normalized: Float) = Native.setParam(id, normalized)
  override fun pitchBend(value: Float) = Native.pitchBend(value)
  override fun modWheel(value: Float) = Native.modWheel(value)
  override fun sustain(on: Boolean) = Native.sustain(on)
  override fun aftertouch(value: Float) = Native.aftertouch(value)
  override fun breath(value: Float) = Native.breath(value)
  override fun allNotesOff() = Native.allNotesOff()
  override fun loadPatch(values: FloatArray) = Native.loadPatch(values)
}

class MainActivity : ComponentActivity() {
  private lateinit var model: AppModel
  private lateinit var dispatch: MidiDispatch
  private lateinit var midi: MidiInput
  private lateinit var prefs: Prefs

  private val logLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
    if (uri == null) return@registerForActivityResult
    runCatching { contentResolver.openOutputStream(uri)?.use { it.write(AppLog.text().toByteArray()) } }
      .onSuccess { model.showMessage("Log salvo") }
      .onFailure { model.showMessage("Não foi possível salvar o log") }
  }

  private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri == null) return@registerForActivityResult
    runCatching { contentResolver.openOutputStream(uri)?.use { it.write(model.exportBank().toByteArray()) } }
      .onSuccess { model.showMessage("Presets exportados") }
      .onFailure { model.showMessage("Não foi possível exportar") }
  }

  private var pendingSyx: Pair<ByteArray, String>? = null  // bytes and the message to show when saved

  private val syxLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
    val (bytes, done) = pendingSyx ?: return@registerForActivityResult
    pendingSyx = null
    if (uri == null) return@registerForActivityResult
    runCatching { contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
      .onSuccess { model.showMessage(done) }
      .onFailure { model.showMessage("Não foi possível exportar") }
  }

  private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri == null) return@registerForActivityResult
    val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    if (bytes == null) model.showMessage("Não foi possível ler o arquivo") else model.importBytes(bytes, displayStem(uri))
  }

  /** The picked document's file name without its extension (the category of imported DX7 banks). */
  private fun displayStem(uri: Uri): String {
    val name = runCatching {
      contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
      }
    }.getOrNull() ?: "importado"
    return name.substringBeforeLast('.', name).trim().ifEmpty { "importado" }
  }

  private fun exportSyx(name: String, bytes: ByteArray, done: String) {
    pendingSyx = bytes to done
    val safe = name.replace(Regex("[\\/:*?\"<>|]"), "_").trim().take(40).trim().ifEmpty { "preset" }
    syxLauncher.launch("$safe.syx")
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppLog.init(this)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    prefs = Prefs(this)
    Native.setMaxVoices(prefs.maxVoices)
    val params = Params.parse(Native.paramTable())
    val state = SynthState(params, NativeSynth)
    val library = PresetLibrary(AssetFactory(assets), File(filesDir, "presets/user"), prefs, AssetCartridges(assets)) { AppLog.log(it) }
    model = AppModel(state, library, File(filesDir, "session.json")) { AppLog.log(it) }
    model.restore()

    val main = Handler(Looper.getMainLooper())
    val learn = MidiLearn(MidiMap.decode(prefs.midiMap)) { prefs.midiMap = it }
    // MIDI changes go through SynthState, so the screen follows them.
    dispatch = MidiDispatch(
      synth = state, params = params, log = { AppLog.log(it) }, learn = learn, current = state::norm,
      onAction = { a ->
        main.post {
          when (a) {
            MidiAction.PREV -> model.step(-1)
            MidiAction.NEXT -> model.step(1)
            MidiAction.PANIC -> state.allNotesOff()
          }
        }
      },
      onProgram = { n -> main.post { model.programChange(n) } },
    ).apply {
      channel = prefs.midiChannel
      softTakeover = prefs.softTakeover
    }
    val midiSettings = object : MidiSettings {
      override var channel: Int
        get() = prefs.midiChannel
        set(v) { prefs.midiChannel = v; dispatch.channel = v }
      override var softTakeover: Boolean
        get() = prefs.softTakeover
        set(v) { prefs.softTakeover = v; dispatch.softTakeover = v }
    }
    val parser = MidiParser { bytes, len -> dispatch.message(bytes, len) }
    midi = MidiInput(this, onDisconnect = { Native.allNotesOff() }) { bytes, offset, count -> parser.feed(bytes, offset, count) }

    val io = ScreenIo(
      midiDevices = midi.count, midiActivity = dispatch.activity, lastMidi = dispatch.lastMessage,
      peak = Native::peak, voices = Native::activeVoices, audioInfo = Native::audioInfo,
      voiceLimit = Native::voiceLimit,
      maxVoices = object : com.criapix.volt.ui.MaxVoices {
        override var value: Int
          get() = prefs.maxVoices
          set(v) { prefs.maxVoices = v; Native.setMaxVoices(v); AppLog.log("polifonia máxima: $v") }
      },
      haptics = Haptics(this, prefs),
      learn = learn, pickup = dispatch.pickup, midiSettings = midiSettings,
      onExport = { exportLauncher.launch("volt-presets.json") },
      onExportLog = { logLauncher.launch("volt-log.txt") },
      version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?",
      onExportSyx = ::exportSyx,
      onImport = { importLauncher.launch(arrayOf("*/*")) },
    )
    setContent { VoltScreen(model, io) }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) {
          AppLog.log("AUDIO " + Native.audioInfo().trim().replace('\n', ' '))
          delay(1000)
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    val ok = Native.start()
    AppLog.log("ciclo de vida: onStart; Native.start() = $ok")
    midi.start()
  }

  override fun onStop() {
    AppLog.log("ciclo de vida: onStop; AUDIO " + Native.audioInfo().trim().replace('\n', ' '))
    model.persist()
    midi.stop()
    Native.allNotesOff()
    Native.stop()
    AppLog.flush()
    super.onStop()
  }
}
