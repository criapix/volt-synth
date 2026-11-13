package com.criapix.volt.ui

import androidx.compose.ui.graphics.Color

/**
 * One colour per family of controls. The colour marks where you are (family tile, card title, knob arc) and
 * where a modulation comes from (the same hue on the LFO's lines). The six FM operators keep their own six
 * colours everywhere: diagram, tiles, envelope and knobs.
 */
object Hues {
  val osc1 = Color(0xFFFF8A3D)
  val osc2 = Color(0xFFFFAB4F)
  val osc3 = Color(0xFFFF6F5E)
  val mixer = Color(0xFFFFC66D)
  val filter = Color(0xFFF6D84A)
  val hpf = Color(0xFFD6E25A)
  val fenv = Color(0xFFB5E86A)
  val aenv = Color(0xFF4FE0A5)
  val voice = Color(0xFFFF7FB0)
  val lfo1 = Color(0xFFA98BFF)
  val lfo2 = Color(0xFFC99BFF)
  val polyMod = Color(0xFF8B7DFF)
  val dist = Color(0xFFFF5A4E)
  val phaser = Color(0xFF3FD6C6)
  val flanger = Color(0xFF4FA8FF)
  val chorus = Color(0xFF8F9CFF)
  val delay = Color(0xFF4FC3F7)
  val reverb = Color(0xFFB58CFF)
  val arp = Color(0xFFFF5FC8)
  val algorithm = Color(0xFF7EE0D5)
  val pitchEnv = Color(0xFFF783AC)
  val fmLfo = Color(0xFFA98BFF)
  val controllers = Color(0xFF91A7FF)

  /** OP1..OP6, red to violet. */
  val operators = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFA94D), Color(0xFFFFD43B),
    Color(0xFF69DB7C), Color(0xFF4DABF7), Color(0xFFB197FF),
  )

  fun op(n: Int): Color = operators[(n - 1).coerceIn(0, 5)]

  /** The hue of a parameter, by name; the colour of the card it lives on. */
  fun of(name: String): Color {
    Regex("""fm\.op(\d)\.""").find(name)?.let { return op(it.groupValues[1].toInt()) }
    return when {
      name.startsWith("osc1.") -> osc1
      name.startsWith("osc2.") -> osc2
      name.startsWith("osc3.") -> osc3
      name.startsWith("mix.") || name == "master.volume" -> mixer
      name.startsWith("hpf.") -> hpf
      name.startsWith("filter.") -> filter
      name.startsWith("fenv.") -> fenv
      name.startsWith("aenv.") -> aenv
      name.startsWith("voice.") -> voice
      name.startsWith("lfo1.") -> lfo1
      name.startsWith("lfo2.") -> lfo2
      name.startsWith("pmod.") -> polyMod
      name.startsWith("fx.dist.") -> dist
      name.startsWith("fx.phaser.") -> phaser
      name.startsWith("fx.flanger.") -> flanger
      name.startsWith("fx.chorus.") -> chorus
      name.startsWith("fx.delay.") -> delay
      name.startsWith("fx.reverb.") -> reverb
      name.startsWith("arp.") -> arp
      name.startsWith("fm.pitchenv.") -> pitchEnv
      name.startsWith("fm.lfo.") -> fmLfo
      name.startsWith("fm.wheel.") || name.startsWith("fm.at.") || name.startsWith("fm.breath.") -> controllers
      name.startsWith("fm.") -> algorithm
      else -> VoltColors.accent
    }
  }

  /** [c] mixed [t] (0..1) of the way towards [to]. */
  fun mix(c: Color, to: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
      c.red + (to.red - c.red) * k, c.green + (to.green - c.green) * k,
      c.blue + (to.blue - c.blue) * k, c.alpha + (to.alpha - c.alpha) * k,
    )
  }

  /** The card background: the hue at 7 % over the panel colour. */
  fun cardFill(hue: Color): Color = mix(Color(0xFF0F1117), hue, 0.07f)

  /** The card border: the hue at 25 % over a neutral line. */
  fun cardLine(hue: Color): Color = mix(Color(0xFF232733), hue, 0.25f)
}
