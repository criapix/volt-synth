package com.criapix.volt.ui

import com.criapix.volt.Param
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ValueUnit { HZ, SEC, GLIDE, PCT, BIPCT, SEMI, CENT, OCT, BEND, COUNT, BPM, RAW, SIGNED, NOTE }

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

    put("fx.dist.on", ToggleUi("Ligado"))
    put("fx.dist.drive", KnobUi("Drive", ValueUnit.PCT))
    put("fx.dist.tone", KnobUi("Tom", ValueUnit.HZ))
    put("fx.dist.mix", KnobUi("Mix", ValueUnit.PCT))
    for (fx in listOf("phaser", "flanger")) {
      put("fx.$fx.on", ToggleUi("Ligado"))
      put("fx.$fx.rate", KnobUi("Veloc.", ValueUnit.HZ))
      put("fx.$fx.depth", KnobUi("Profund.", ValueUnit.PCT))
      put("fx.$fx.feedback", KnobUi("Realim.", ValueUnit.PCT))
      put("fx.$fx.mix", KnobUi("Mix", ValueUnit.PCT))
    }
    put("fx.chorus.on", ToggleUi("Ligado"))
    put("fx.chorus.mode", ChoiceUi("Modo", listOf("I", "II", "I+II")))
    put("fx.chorus.mix", KnobUi("Mix", ValueUnit.PCT))
    put("fx.delay.on", ToggleUi("Ligado"))
    put("fx.delay.time", KnobUi("Tempo", ValueUnit.SEC))
    put("fx.delay.feedback", KnobUi("Repetições", ValueUnit.PCT))
    put("fx.delay.mix", KnobUi("Mix", ValueUnit.PCT))
    put("fx.reverb.on", ToggleUi("Ligado"))
    put("fx.reverb.decay", KnobUi("Duração", ValueUnit.SEC))
    put("fx.reverb.damp", KnobUi("Abafar", ValueUnit.PCT))
    put("fx.reverb.mix", KnobUi("Mix", ValueUnit.PCT))

    put("arp.on", ToggleUi("Ligado"))
    put("arp.hold", ToggleUi("Hold"))
    put("arp.mode", ChoiceUi("Modo", listOf("Sobe", "Desce", "Sobe-desce", "Aleatório", "Como tocado")))
    put("arp.division", ChoiceUi("Divisão", listOf("1/4", "1/8", "1/16", "1/4T", "1/8T", "1/16T")))
    put("arp.octaves", KnobUi("Oitavas", ValueUnit.COUNT))
    put("arp.bpm", KnobUi("Tempo", ValueUnit.BPM))
    put("arp.gate", KnobUi("Duração", ValueUnit.PCT))

    put("engine.type", ChoiceUi("Motor", listOf("Analógico", "FM")))
    val curves = listOf("-LIN", "-EXP", "+EXP", "+LIN")
    for (n in 1..6) {
      for (i in 1..4) put("fm.op$n.r$i", KnobUi("R$i", ValueUnit.RAW))
      for (i in 1..4) put("fm.op$n.l$i", KnobUi("L$i", ValueUnit.RAW))
      put("fm.op$n.kls.break", KnobUi("Break", ValueUnit.NOTE))
      put("fm.op$n.kls.ldepth", KnobUi("Prof. E", ValueUnit.RAW))
      put("fm.op$n.kls.rdepth", KnobUi("Prof. D", ValueUnit.RAW))
      put("fm.op$n.kls.lcurve", ChoiceUi("Curva E", curves))
      put("fm.op$n.kls.rcurve", ChoiceUi("Curva D", curves))
      put("fm.op$n.rate.scale", KnobUi("Escala taxa", ValueUnit.RAW))
      put("fm.op$n.ams", KnobUi("AMS", ValueUnit.RAW))
      put("fm.op$n.vel", KnobUi("Velocity", ValueUnit.RAW))
      put("fm.op$n.level", KnobUi("Nível", ValueUnit.RAW))
      put("fm.op$n.mode", ChoiceUi("Modo", listOf("Razão", "Fixo")))
      put("fm.op$n.coarse", KnobUi("Coarse", ValueUnit.RAW))
      put("fm.op$n.fine", KnobUi("Fine", ValueUnit.RAW))
      put("fm.op$n.detune", KnobUi("Detune", ValueUnit.SIGNED, bipolar = true))
      put("fm.op$n.on", ToggleUi("OP$n"))
    }
    for (i in 1..4) put("fm.pitchenv.r$i", KnobUi("R$i", ValueUnit.RAW))
    for (i in 1..4) put("fm.pitchenv.l$i", KnobUi("L$i", ValueUnit.RAW))
    put("fm.algorithm", KnobUi("Algoritmo", ValueUnit.RAW))
    put("fm.feedback", KnobUi("Feedback", ValueUnit.RAW))
    put("fm.oscsync", ToggleUi("Key sync"))
    put("fm.transpose", KnobUi("Transp.", ValueUnit.SIGNED, bipolar = true))
    put("fm.lfo.speed", KnobUi("Veloc.", ValueUnit.RAW))
    put("fm.lfo.delay", KnobUi("Atraso", ValueUnit.RAW))
    put("fm.lfo.pmd", KnobUi("PMD", ValueUnit.RAW))
    put("fm.lfo.amd", KnobUi("AMD", ValueUnit.RAW))
    put("fm.lfo.sync", ToggleUi("Key sync"))
    put("fm.lfo.wave", ChoiceUi("Onda", listOf("Tri", "Serra↓", "Serra↑", "Quad", "Seno", "S&H")))
    put("fm.lfo.pms", KnobUi("PMS", ValueUnit.RAW))
    for (c in listOf("wheel", "at", "breath")) {
      put("fm.$c.range", KnobUi("Alcance", ValueUnit.RAW))
      put("fm.$c.pitch", ToggleUi("Afinação"))
      put("fm.$c.amp", ToggleUi("Volume"))
      put("fm.$c.eg", ToggleUi("EG bias"))
    }
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
      ValueUnit.BPM -> "${real.roundToInt()} BPM"
      ValueUnit.RAW -> real.roundToInt().toString()
      ValueUnit.SIGNED -> signed(real.roundToInt(), "")
      ValueUnit.NOTE -> noteName(21 + real.roundToInt())
    }
  }

  private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

  /** MIDI note name with C3 = 60 (the DX7 convention): 21 = "A-1", 60 = "C3". */
  fun noteName(midi: Int) = NOTE_NAMES[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 2)

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
