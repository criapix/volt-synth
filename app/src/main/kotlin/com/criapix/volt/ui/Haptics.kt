package com.criapix.volt.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.criapix.volt.Prefs

/** A short tick for selectors, toggles, tab changes and each step of a step knob. */
class Haptics(context: Context, private val prefs: Prefs) {
  private val vibrator: Vibrator? =
    if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
  private val primitives = Build.VERSION.SDK_INT >= 30 &&
    vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) == true
  private var last = 0L

  var enabled: Boolean
    get() = prefs.haptics
    set(v) { prefs.haptics = v }

  fun tick() {
    if (!prefs.haptics) return
    val v = vibrator?.takeIf { it.hasVibrator() } ?: return
    val now = SystemClock.uptimeMillis()
    if (now - last < 8) return
    last = now
    v.vibrate(
      when {
        primitives -> VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f).compose()
        Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        else -> VibrationEffect.createOneShot(8L, 100)
      },
    )
  }
}
