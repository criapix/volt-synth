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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    params = Params.parse(Native.paramTable())
    dispatch = MidiDispatch(NativeSynth, params)
    // The parser is fed from one binder thread at a time; MidiReceiver serialises its calls.
    val parser = MidiParser { bytes, len -> dispatch.message(bytes, len) }
    midi = MidiInput(this, onDisconnect = { Native.allNotesOff() }) { bytes, offset, count ->
      parser.feed(bytes, offset, count)
    }

    setContent {
      val devices by midi.count.collectAsStateWithLifecycle()
      val last by dispatch.lastMessage.collectAsStateWithLifecycle()
      var info by remember { mutableStateOf("") }
      LaunchedEffect(Unit) { while (true) { info = Native.audioInfo(); delay(500) } }
      DebugScreen(
        params = params, info = info, midiDevices = devices, lastMidi = last,
        onParam = { p, n -> Native.setParam(p.id, n) },
        onTestNote = { on -> if (on) Native.noteOn(69, 0.8f) else Native.noteOff(69) },
        onPanic = { Native.allNotesOff() },
      )
    }
  }

  override fun onStart() { super.onStart(); Native.start(); midi.start() }

  override fun onStop() {
    midi.stop()
    Native.allNotesOff()
    Native.stop()
    super.onStop()
  }
}
