package com.criapix.volt.ui

import androidx.compose.ui.graphics.Color

/** The drawing at the top of a card; each one is a live picture of what the card's controls do to the sound. */
enum class Drawing {
  NONE, OSC, MIXER, FILTER, HPF, FENV, AENV, VOICE, LFO, POLYMOD,
  ALGORITHM, FM_GLOBAL, OP_FREQ, OP_ENV, OP_SCALE, PITCH_ENV, FM_LFO, CONTROLLERS, ARP,
  DIST, PHASER, FLANGER, CHORUS, DELAY, REVERB,
}

/**
 * One card of the panel. [params] are engine parameter names; [on] (an effect's switch) sits in the header and is
 * not repeated in [params]. [n] is the oscillator, LFO or operator number a drawing needs (0 when it has none).
 */
data class Card(
  val title: String,
  val params: List<String>,
  val drawing: Drawing = Drawing.NONE,
  val hue: Color,
  val sub: String = "",
  val on: String? = null,
  val n: Int = 0,
  /** Knob columns, when the card wants its knobs in rows of its own (envelope rates over levels); 0 lets the layout choose. */
  val columns: Int = 0,
)

/** A family of cards (one tile in the strip). [fm] is null for the families both voice types share. */
data class Family(val id: String, val title: String, val hue: Color, val fm: Boolean?, val cards: List<Card>)

/** Where every parameter lives on screen. Every engine parameter appears exactly once (PanelTest). */
object Panel {
  /** Parameters shown outside any family: the voice-type switch in the top bar. */
  val header: List<String> = listOf("engine.type")

  private fun osc(n: Int) = Card(
    "OSC $n", listOf("osc$n.wave", "osc$n.octave", "osc$n.semi", "osc$n.fine", "osc$n.pw", "osc$n.level") +
      (if (n == 1) listOf("osc1.sync") else emptyList()),
    Drawing.OSC, Hues.of("osc$n.wave"), n = n,
  )

  private fun lfo(n: Int) = Card(
    "LFO $n",
    listOf("lfo$n.wave", "lfo$n.rate", "lfo$n.delay", "lfo$n.pitch", "lfo$n.pw", "lfo$n.cutoff", "lfo$n.amp") +
      (if (n == 1) listOf("lfo1.global", "lfo1.wheel") else emptyList()),
    Drawing.LFO, Hues.of("lfo$n.wave"), n = n,
  )

  private fun fx(key: String, title: String, hue: Color, drawing: Drawing, vararg params: String) =
    Card(title, params.map { "fx.$key.$it" }, drawing, hue, on = "fx.$key.on")

  /** The three cards of operator [n]: frequency, envelope, keyboard scaling. All 21 of its parameters, but `on` (GLOBAL). */
  fun operatorCards(n: Int): List<Card> {
    val h = Hues.op(n)
    fun p(vararg s: String) = s.map { "fm.op$n.$it" }
    return listOf(
      Card("OP$n · FREQUÊNCIA", p("mode", "coarse", "fine", "detune"), Drawing.OP_FREQ, h, n = n),
      Card("OP$n · ENVELOPE", p("r1", "r2", "r3", "r4", "level", "vel", "l1", "l2", "l3", "l4", "rate.scale"), Drawing.OP_ENV, h, n = n, columns = 6),
      Card("OP$n · ESCALA", p("kls.break", "kls.ldepth", "kls.lcurve", "kls.rdepth", "kls.rcurve", "ams"), Drawing.OP_SCALE, h, n = n, columns = 4),
    )
  }

  val fxFamily = Family(
    "fx", "EFEITOS", Hues.delay, null,
    listOf(
      fx("dist", "DISTORÇÃO", Hues.dist, Drawing.DIST, "drive", "tone", "mix"),
      fx("phaser", "PHASER", Hues.phaser, Drawing.PHASER, "rate", "depth", "feedback", "mix"),
      fx("flanger", "FLANGER", Hues.flanger, Drawing.FLANGER, "rate", "depth", "feedback", "mix"),
      fx("chorus", "CHORUS", Hues.chorus, Drawing.CHORUS, "mode", "mix"),
      fx("delay", "DELAY", Hues.delay, Drawing.DELAY, "time", "feedback", "mix"),
      fx("reverb", "REVERB", Hues.reverb, Drawing.REVERB, "decay", "damp", "mix"),
    ),
  )

  val arpFamily = Family(
    "arp", "ARP", Hues.arp, null,
    listOf(Card("ARPEJADOR", listOf("arp.on", "arp.hold", "arp.mode", "arp.division", "arp.octaves", "arp.bpm", "arp.gate"), Drawing.ARP, Hues.arp)),
  )

  private val analog = listOf(
    Family(
      "fonte", "FONTE", Hues.osc1, false,
      listOf(
        osc(1), osc(2), osc(3),
        Card("MIXER", listOf("mix.sub", "mix.noise", "mix.drive", "master.volume"), Drawing.MIXER, Hues.mixer),
      ),
    ),
    Family(
      "filtro", "FILTRO", Hues.filter, false,
      listOf(
        Card("FILTRO", listOf("filter.model", "filter.cutoff", "filter.reso", "filter.env", "filter.keytrack", "filter.vel"), Drawing.FILTER, Hues.filter, "passa-baixas"),
        Card("PASSA-ALTAS", listOf("hpf.cutoff"), Drawing.HPF, Hues.hpf),
        Card("ENV DO FILTRO", listOf("fenv.attack", "fenv.decay", "fenv.sustain", "fenv.release"), Drawing.FENV, Hues.fenv, columns = 4),
      ),
    ),
    Family(
      "amp", "AMP · VOZ", Hues.aenv, false,
      listOf(
        Card("ENV DE AMPLITUDE", listOf("aenv.attack", "aenv.decay", "aenv.sustain", "aenv.release", "aenv.vel"), Drawing.AENV, Hues.aenv, columns = 5),
        Card(
          "VOZ", listOf("voice.mode", "voice.unison", "voice.detune", "voice.spread", "voice.glide", "voice.glide_legato", "voice.drift", "voice.bend"),
          Drawing.VOICE, Hues.voice, "polifonia · glide · bend",
        ),
      ),
    ),
    Family(
      "mov", "MOVIMENTO", Hues.lfo1, false,
      listOf(
        lfo(1), lfo(2),
        Card("POLY-MOD", listOf("pmod.fenv.pitch", "pmod.fenv.pw", "pmod.osc2.pitch", "pmod.osc2.pw"), Drawing.POLYMOD, Hues.polyMod),
      ),
    ),
  )

  private val fm = listOf(
    Family(
      "algo", "ALGORITMO", Hues.algorithm, true,
      listOf(
        Card("ALGORITMO", listOf("fm.algorithm"), Drawing.ALGORITHM, Hues.algorithm, "toque num operador para editá-lo"),
        Card("GLOBAL", listOf("fm.feedback", "fm.oscsync", "fm.transpose") + (1..6).map { "fm.op$it.on" }, Drawing.FM_GLOBAL, Hues.algorithm),
      ),
    ),
    Family("ops", "OPERADORES", Hues.op(1), true, (1..6).flatMap { operatorCards(it) }),
    Family(
      "pitch", "PITCH · LFO", Hues.pitchEnv, true,
      listOf(
        Card("ENV DE PITCH", (1..4).map { "fm.pitchenv.r$it" } + (1..4).map { "fm.pitchenv.l$it" }, Drawing.PITCH_ENV, Hues.pitchEnv, columns = 4),
        Card("LFO", listOf("fm.lfo.wave", "fm.lfo.speed", "fm.lfo.delay", "fm.lfo.pmd", "fm.lfo.amd", "fm.lfo.pms", "fm.lfo.sync"), Drawing.FM_LFO, Hues.fmLfo),
        Card(
          "CONTROLADORES", listOf("wheel", "at", "breath").flatMap { c -> listOf("range", "pitch", "amp", "eg").map { "fm.$c.$it" } },
          Drawing.CONTROLLERS, Hues.controllers,
        ),
      ),
    ),
  )

  /** Every family once (the effects and the arpeggiator are shared by both voice types). */
  val families: List<Family> = analog + fm + fxFamily + arpFamily

  /** The strip's tiles for a voice type, in order. */
  fun familiesFor(fm: Boolean): List<Family> =
    (if (fm) this.fm else analog) + fxFamily + arpFamily

  fun cardOf(name: String): Card? = families.firstNotNullOfOrNull { f -> f.cards.firstOrNull { name in it.params || name == it.on } }

  fun familyOf(name: String): Family? = families.firstOrNull { f -> f.cards.any { name in it.params || name == it.on } }

  /** Operator [n]'s cards, in the order they are shown. */
  fun operatorCardsOf(n: Int): List<Card> = families.first { it.id == "ops" }.cards.filter { it.n == n }
}
