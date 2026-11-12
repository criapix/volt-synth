package com.criapix.volt.ui

import com.criapix.volt.Param
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ValueUnit { HZ, SEC, GLIDE, PCT, BIPCT, SEMI, CENT, OCT, BEND, COUNT }

sealed interface Widget { val label: String }
data class KnobUi(override val label: String, val unit: ValueUnit, val bipolar: Boolean = false) : Widget
data class ChoiceUi(override val label: String, val options: List<String>) : Widget
data class ToggleUi(override val label: String) : Widget

/** How each parameter appears on screen. Every engine parameter must have an entry (ParamUiTest). */
object ParamUi {
  private val BR: Locale = Locale.forLanguageTag("pt-BR")
  private val OSC_WAVES = listOf("Serra", "Pulso", "Tri", "Seno")
  private val LFO_WAVES = listOf("Seno", "Tri", "Serra", "Quad", "S&H")

  private val table: Map<String, Widget> = buildMap {
    for (n in 1..3) {
      put("osc$n.wave", ChoiceUi("Onda", OSC_WAVES))
      put("osc$n.octave", KnobUi("Oitava", ValueUnit.OCT, bipolar = true))
      put("osc$n.semi", KnobUi("Semitom", ValueUnit.SEMI, bipolar = true))
      put("osc$n.fine", KnobUi("Fino", ValueUnit.CENT, bipolar = true))
      put("osc$n.pw", KnobUi("Largura", ValueUnit.PCT))
      put("osc$n.level", KnobUi("Nível", ValueUnit.PCT))
    }
    put("osc1.sync", ToggleUi("Sync no osc 2"))
    put("mix.sub", KnobUi("Sub", ValueUnit.PCT))
    put("mix.noise", KnobUi("Ruído", ValueUnit.PCT))
    put("mix.drive", KnobUi("Drive", ValueUnit.PCT))
    put("master.volume", KnobUi("Volume", ValueUnit.PCT))

    put("hpf.cutoff", KnobUi("Passa-altas", ValueUnit.HZ))
    put("filter.model", ChoiceUi("Modelo", listOf("Ladder", "Roland 12", "Roland 24", "Prophet")))
    put("filter.cutoff", KnobUi("Cutoff", ValueUnit.HZ))
    put("filter.reso", KnobUi("Resson.", ValueUnit.PCT))
    put("filter.env", KnobUi("Envelope", ValueUnit.BIPCT, bipolar = true))
    put("filter.keytrack", KnobUi("Teclado", ValueUnit.PCT))
    put("filter.vel", KnobUi("Velocity", ValueUnit.PCT))

    for (e in listOf("fenv", "aenv")) {
      put("$e.attack", KnobUi("Ataque", ValueUnit.SEC))
      put("$e.decay", KnobUi("Decay", ValueUnit.SEC))
      put("$e.sustain", KnobUi("Sustain", ValueUnit.PCT))
      put("$e.release", KnobUi("Release", ValueUnit.SEC))
    }
    put("aenv.vel", KnobUi("Velocity", ValueUnit.PCT))

    for (n in 1..2) {
      put("lfo$n.wave", ChoiceUi("Onda", LFO_WAVES))
      put("lfo$n.rate", KnobUi("Veloc.", ValueUnit.HZ))
      put("lfo$n.delay", KnobUi("Atraso", ValueUnit.SEC))
      put("lfo$n.pitch", KnobUi("Afinação", ValueUnit.PCT))
      put("lfo$n.pw", KnobUi("Largura", ValueUnit.PCT))
      put("lfo$n.cutoff", KnobUi("Cutoff", ValueUnit.PCT))
      put("lfo$n.amp", KnobUi("Volume", ValueUnit.PCT))
    }
    put("lfo1.global", ToggleUi("Global"))
    put("lfo1.wheel", KnobUi("Roda", ValueUnit.PCT))

    put("pmod.fenv.pitch", KnobUi("Env→Afin.", ValueUnit.BIPCT, bipolar = true))
    put("pmod.fenv.pw", KnobUi("Env→Larg.", ValueUnit.BIPCT, bipolar = true))
    put("pmod.osc2.pitch", KnobUi("Osc2→Afin.", ValueUnit.PCT))
    put("pmod.osc2.pw", KnobUi("Osc2→Larg.", ValueUnit.PCT))

    put("voice.mode", ChoiceUi("Modo", listOf("Poly", "Mono", "Legato", "Unison")))
    put("voice.unison", KnobUi("Vozes", ValueUnit.COUNT))
    put("voice.detune", KnobUi("Detune", ValueUnit.PCT))
    put("voice.spread", KnobUi("Abertura", ValueUnit.PCT))
    put("voice.glide", KnobUi("Glide", ValueUnit.GLIDE))
    put("voice.glide_legato", ToggleUi("Glide só em legato"))
    put("voice.drift", KnobUi("Drift", ValueUnit.PCT))
    put("voice.bend", KnobUi("Bend", ValueUnit.BEND))
  }

  fun of(name: String): Widget = table[name] ?: error("no on-screen description for parameter $name")

  /** The real value as shown on screen, e.g. "1,2 kHz", "250 ms", "+7 st". */
  fun format(p: Param, real: Float): String = when (val w = of(p.name)) {
    is ChoiceUi -> w.options.getOrElse((real - p.min).roundToInt()) { "?" }
    is ToggleUi -> if (real >= 0.5f) "lig." else "desl."
    is KnobUi -> when (w.unit) {
      ValueUnit.HZ -> when {
        real >= 1000f -> fmt("%.1f kHz", real / 1000f)
        real >= 10f -> fmt("%.0f Hz", real)
        else -> fmt("%.2f Hz", real)
      }
      ValueUnit.SEC -> seconds(real)
      ValueUnit.GLIDE -> if (real < 0.0005f) "desl." else seconds(real)
      ValueUnit.PCT -> "${(real * 100).roundToInt()} %"
      ValueUnit.BIPCT -> signed((real * 100).roundToInt(), " %")
      ValueUnit.SEMI -> signed(real.roundToInt(), " st")
      ValueUnit.CENT -> signed(real.roundToInt(), " ct")
      ValueUnit.OCT -> signed(real.roundToInt(), " oct")
      ValueUnit.BEND -> "±${real.roundToInt()} st"
      ValueUnit.COUNT -> real.roundToInt().toString()
    }
  }

  private fun seconds(s: Float) = when {
    s >= 1f -> fmt("%.2f s", s)
    s >= 0.01f -> fmt("%.0f ms", s * 1000f)
    else -> fmt("%.1f ms", s * 1000f)
  }

  private fun signed(v: Int, unit: String) = when {
    v > 0 -> "+$v$unit"
    v < 0 -> "-${abs(v)}$unit"
    else -> "0$unit"
  }

  private fun fmt(pattern: String, v: Float) = String.format(BR, pattern, v)
}
