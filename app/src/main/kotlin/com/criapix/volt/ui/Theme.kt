package com.criapix.volt.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VoltColors {
  val background = Color(0xFF121417)
  val panel = Color(0xFF1C1F24)
  val raised = Color(0xFF262A31)
  val track = Color(0xFF3A3F47)
  val accent = Color(0xFFFF8A3D)   // knobs, selection, the "volt" orange
  val accent2 = Color(0xFF4FC3F7)  // drawings
  val text = Color(0xFFE8E8E8)
  val dim = Color(0xFF8A9099)
  val danger = Color(0xFFFF5252)
}

/** Category key in preset files -> label on screen. */
fun categoryLabel(key: String): String = when (key) {
  "Bass" -> "Baixo"; "Lead" -> "Lead"; "Pad" -> "Pad"; "Brass" -> "Metais"
  "Strings" -> "Cordas"; "Keys" -> "Teclas"; "Pluck" -> "Pluck"; "FX" -> "Efeitos"
  else -> "—"
}

@Composable
fun VoltTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = darkColorScheme(
      primary = VoltColors.accent, secondary = VoltColors.accent2,
      background = VoltColors.background, surface = VoltColors.panel,
      onPrimary = Color.Black, onBackground = VoltColors.text, onSurface = VoltColors.text,
    ),
    content = content,
  )
}
