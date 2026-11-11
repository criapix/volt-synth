package com.criapix.volt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val count = Native.paramTable().lines().count { it.isNotBlank() }
    setContent { Text("Volt: $count parâmetros") }
  }
}
