package com.criapix.volt

import android.os.Bundle
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
import com.criapix.volt.midi.MidiDispatch
import com.criapix.volt.midi.MidiInput
import com.criapix.volt.midi.MidiParser
import com.criapix.volt.midi.Synth
import com.criapix.volt.preset.AppModel
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
  override fun allNotesOff() = Native.allNotesOff()
  override fun loadPatch(values: FloatArray) = Native.loadPatch(values)
}

class MainActivity : ComponentActivity() {
  private lateinit var model: AppModel
  private lateinit var dispatch: MidiDispatch
  private lateinit var midi: MidiInput
  private lateinit var prefs: Prefs

  private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri == null) return@registerForActivityResult
    runCatching { contentResolver.openOutputStream(uri)?.use { it.write(model.exportBank().toByteArray()) } }
      .onSuccess { model.showMessage("Presets exportados") }
      .onFailure { model.showMessage("Não foi possível exportar") }
  }

  private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri == null) return@registerForActivityResult
    val text = runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
    if (text == null) model.showMessage("Não foi possível ler o arquivo") else model.importText(text)
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
    val params = Params.parse(Native.paramTable())
    val state = SynthState(params, NativeSynth)
    val library = PresetLibrary(AssetFactory(assets), File(filesDir, "presets/user"), prefs)
    model = AppModel(state, library, File(filesDir, "session.json")) { AppLog.log(it) }
    model.restore()

    dispatch = MidiDispatch(state, params) { AppLog.log(it) }  // MIDI changes go through SynthState
    val parser = MidiParser { bytes, len -> dispatch.message(bytes, len) }
    midi = MidiInput(this, onDisconnect = { Native.allNotesOff() }) { bytes, offset, count -> parser.feed(bytes, offset, count) }

    val io = ScreenIo(
      midiDevices = midi.count, midiActivity = dispatch.activity, lastMidi = dispatch.lastMessage,
      peak = Native::peak, voices = Native::activeVoices, audioInfo = Native::audioInfo,
      haptics = Haptics(this, prefs),
      onExport = { exportLauncher.launch("volt-presets.json") },
      onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
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
