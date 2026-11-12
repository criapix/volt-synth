package com.criapix.volt.ui

/** The tabs. ARP and FX arrive with plan 4. */
enum class Tab(val title: String) { OSC("OSC"), FILTER("FILTRO"), ENV("ENV"), MOD("MOD") }

/** A drawing shown at the top of a group. */
enum class Drawing { NONE, FILTER_CURVE, FENV, AENV }

data class Group(val title: String, val params: List<String>, val drawing: Drawing = Drawing.NONE, val weight: Float = 1f)

/** Where every parameter lives on screen. Every engine parameter appears exactly once (PanelTest). */
object Panel {
  private fun osc(n: Int) = Group(
    "OSC $n",
    listOf("osc$n.wave", "osc$n.octave", "osc$n.semi", "osc$n.fine", "osc$n.pw", "osc$n.level") +
      (if (n == 1) listOf("osc1.sync") else emptyList()),
  )

  private fun lfo(n: Int) = Group(
    "LFO $n",
    listOf("lfo$n.wave", "lfo$n.rate", "lfo$n.delay", "lfo$n.pitch", "lfo$n.pw", "lfo$n.cutoff", "lfo$n.amp") +
      (if (n == 1) listOf("lfo1.global", "lfo1.wheel") else emptyList()),
  )

  val groups: Map<Tab, List<Group>> = linkedMapOf(
    Tab.OSC to listOf(osc(1), osc(2), osc(3), Group("MIXER", listOf("mix.sub", "mix.noise", "mix.drive", "master.volume"), weight = 0.8f)),
    Tab.FILTER to listOf(
      Group("RESPOSTA", emptyList(), Drawing.FILTER_CURVE, weight = 1.4f),
      Group("FILTRO", listOf("filter.model", "filter.cutoff", "filter.reso", "filter.env", "filter.keytrack", "filter.vel"), weight = 1.3f),
      Group("PASSA-ALTAS", listOf("hpf.cutoff"), weight = 0.5f),
    ),
    Tab.ENV to listOf(
      Group("ENVELOPE DO FILTRO", listOf("fenv.attack", "fenv.decay", "fenv.sustain", "fenv.release"), Drawing.FENV),
      Group("ENVELOPE DE AMPLITUDE", listOf("aenv.attack", "aenv.decay", "aenv.sustain", "aenv.release", "aenv.vel"), Drawing.AENV),
    ),
    Tab.MOD to listOf(
      lfo(1),
      lfo(2),
      Group("POLY-MOD", listOf("pmod.fenv.pitch", "pmod.fenv.pw", "pmod.osc2.pitch", "pmod.osc2.pw"), weight = 0.8f),
      Group("VOZ", listOf("voice.mode", "voice.unison", "voice.detune", "voice.spread", "voice.glide", "voice.glide_legato", "voice.drift", "voice.bend")),
    ),
  )

  fun groupOf(name: String): Group? = groups.values.flatten().firstOrNull { name in it.params }

  fun tabOf(name: String): Tab? = groups.entries.firstOrNull { (_, gs) -> gs.any { name in it.params } }?.key
}
