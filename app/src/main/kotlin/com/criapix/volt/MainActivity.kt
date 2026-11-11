package com.criapix.volt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
  private var info by mutableStateOf("")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      Column {
        Button(onClick = { Native.noteOn(57, 0.8f); info = Native.audioInfo() }) { Text("Tocar") }
        Button(onClick = { Native.noteOff(57); info = Native.audioInfo() }) { Text("Soltar") }
        Text(info)
      }
    }
  }

  override fun onStart() { super.onStart(); Native.start(); info = Native.audioInfo() }
  override fun onStop() { Native.allNotesOff(); Native.stop(); super.onStop() }
}
