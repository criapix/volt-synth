package com.criapix.volt.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf

/** The live model the drawings read; null in previews and tests. */
val LocalLive = staticCompositionLocalOf<Live?> { null }

/**
 * A counter that grows once per frame. A drawing reads it inside its draw block, which makes that drawing (and
 * only it) repaint every frame without recomposing anything.
 */
val LocalFrame = staticCompositionLocalOf<State<Long>?> { null }
