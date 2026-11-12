package com.criapix.volt

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.midi.MidiDispatch
import com.criapix.volt.midi.MidiInput
import com.criapix.volt.midi.MidiParser
import com.criapix.volt.midi.Synth
import kotlinx.coroutines.delay
import java.util.Locale

private object NativeSynth : Synth {
  override fun noteOn(note: Int, velocity: Float) = Native.noteOn(note, velocity)
  override fun noteOff(note: Int) = Native.noteOff(note)
  override fun setParam(id: Int, normalized: Float) = Native.setParam(id, normalized)
  override fun pitchBend(value: Float) = Native.pitchBend(value)
  override fun modWheel(value: Float) = Native.modWheel(value)
  override fun sustain(on: Boolean) = Native.sustain(on)
  override fun allNotesOff() = Native.allNotesOff()
}

class MainActivity : ComponentActivity() {
  private lateinit var params: Params
  private lateinit var dispatch: MidiDispatch
  private lateinit var midi: MidiInput
  private val lastParamLog = HashMap<Int, Long>()  // thins out slider-drag lines, per parameter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppLog.init(this)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    params = Params.parse(Native.paramTable())
    dispatch = MidiDispatch(NativeSynth, params) { AppLog.log(it) }
    // The parser is fed from one binder thread at a time; MidiReceiver serialises its calls.
    val parser = MidiParser { bytes, len -> dispatch.message(bytes, len) }
    midi = MidiInput(this, onDisconnect = { Native.allNotesOff() }) { bytes, offset, count ->
      parser.feed(bytes, offset, count)
    }

    setContent {
      val devices by midi.count.collectAsStateWithLifecycle()
      val last by dispatch.lastMessage.collectAsStateWithLifecycle()
      var info by remember { mutableStateOf("") }
      LaunchedEffect(Unit) {
        var tick = 0
        var wasRunning = false
        while (true) {
          info = Native.audioInfo()
          val running = info.startsWith("running=1")
          // once a second while the stream runs, plus the line where it stops; nothing while idle
          if (tick++ % 2 == 0 && (running || wasRunning)) AppLog.log("AUDIO " + info.trim().replace('\n', ' '))
          wasRunning = running
          delay(500)
        }
      }
      DebugScreen(
        params = params, info = info, midiDevices = devices, lastMidi = last,
        onParam = { p, n ->
          Native.setParam(p.id, n)
          val now = System.nanoTime()
          if (now - (lastParamLog[p.id] ?: 0L) > 250_000_000L) {
            lastParamLog[p.id] = now
            AppLog.log("UI parâmetro ${p.name} = ${String.format(Locale.ROOT, "%.3f", p.toReal(n))}")
          }
        },
        onTestNote = { on ->
          AppLog.log("UI botão ${if (on) "Tocar Lá" else "Soltar"}")
          if (on) Native.noteOn(69, 0.8f) else Native.noteOff(69)
        },
        onPanic = { AppLog.log("UI botão Pânico"); Native.allNotesOff() },
      )
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
    midi.stop()
    Native.allNotesOff()
    Native.stop()
    AppLog.flush()
    super.onStop()
  }
}
