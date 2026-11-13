package com.criapix.volt.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import com.criapix.volt.state.SynthState

/**
 * One of the eight performance knobs of the Palco. In the analog voice a macro moves real parameters (cutoff, release,
 * delay and reverb mix...). In the FM voice there is no single "brightness", so a macro is relative: it rests in the
 * middle and pushes a group of operator parameters up or down from what the patch has.
 */
class MacroDef(
  val id: String, val label: String, val hue: Color,
  /** Analog: the parameters it sets (the first one is what it shows); the others follow at [followScale]. */
  val keys: List<String> = emptyList(),
  /** Analog: effects that switch on when the macro leaves zero, and off when it returns. */
  val switches: List<String> = emptyList(),
  /** FM: which operator parameter group it moves. */
  val target: FmTarget? = null,
  /** FM: true when raising the macro should lower the parameter (a longer attack is a lower rate). */
  val inverted: Boolean = false,
  /** The parameter whose feedback the knob borrows, when it has one of its own. */
  val like: String? = null,
) {
  val relative get() = target != null
}

enum class FmTarget { MODULATOR_LEVELS, CARRIER_ATTACK_RATE, CARRIER_RELEASE_RATE }

object MacroDefs {
  private val space = listOf("fx.reverb.mix", "fx.delay.mix")
  private val spaceOn = listOf("fx.reverb.on", "fx.delay.on")

  val analog = listOf(
    MacroDef("brilho", "Brilho", Color(0xFFF6D84A), keys = listOf("filter.cutoff"), like = "filter.cutoff"),
    MacroDef("corpo", "Corpo", Color(0xFFFF9A5A), keys = listOf("filter.reso"), like = "filter.reso"),
    MacroDef("ataque", "Ataque", Color(0xFF4FE0A5), keys = listOf("aenv.attack", "fenv.attack"), like = "aenv.attack"),
    MacroDef("cauda", "Cauda", Color(0xFF3FD6C6), keys = listOf("aenv.release", "fenv.release"), like = "aenv.release"),
    MacroDef("calor", "Calor", Color(0xFFFF5A4E), keys = listOf("mix.drive"), like = "mix.drive"),
    MacroDef("movimento", "Movimento", Color(0xFFA98BFF), keys = listOf("lfo1.cutoff"), like = "lfo1.cutoff"),
    MacroDef("ritmo", "Ritmo", Color(0xFFFF5FC8), keys = listOf("lfo1.rate"), like = "lfo1.rate"),
    MacroDef("espaco", "Espaço", Color(0xFF4FC3F7), keys = space, switches = spaceOn),
  )

  val fm = listOf(
    MacroDef("brilho", "Brilho", Color(0xFFF6D84A), target = FmTarget.MODULATOR_LEVELS),
    MacroDef("corpo", "Corpo", Color(0xFFFF9A5A), keys = listOf("fm.feedback"), like = "fm.feedback"),
    MacroDef("ataque", "Ataque", Color(0xFF4FE0A5), target = FmTarget.CARRIER_ATTACK_RATE, inverted = true),
    MacroDef("cauda", "Cauda", Color(0xFF3FD6C6), target = FmTarget.CARRIER_RELEASE_RATE, inverted = true),
    MacroDef("calor", "Calor", Color(0xFFFF5A4E), keys = listOf("fx.dist.drive"), switches = listOf("fx.dist.on"), like = "fx.dist.drive"),
    MacroDef("movimento", "Movimento", Color(0xFFA98BFF), keys = listOf("fm.lfo.pmd"), like = "fm.lfo.pmd"),
    MacroDef("ritmo", "Ritmo", Color(0xFFFF5FC8), keys = listOf("fm.lfo.speed"), like = "fm.lfo.speed"),
    MacroDef("espaco", "Espaço", Color(0xFF4FC3F7), keys = space, switches = spaceOn),
  )

  fun of(fm: Boolean): List<MacroDef> = if (fm) this.fm else analog
}

/** Reads and writes macros. [algo] gives the current FM algorithm (which operators are carriers). */
class Macros(private val state: SynthState, private val algo: () -> Algo?) {
  companion object {
    /** How far a relative macro pushes its parameters at either end, in the parameter's own steps. */
    const val SPAN = 45f
    const val FOLLOW = 0.85f
  }

  private val positions = mutableStateMapOf<String, Float>()
  private var base = HashMap<String, Float>()

  private fun real(name: String): Float { val p = state.params[name]!!; return p.toReal(state.norm(p.id)) }
  private fun setReal(name: String, v: Float) { val p = state.params[name]!!; state.set(p.id, p.toNorm(v)) }

  /** Back to the middle, forgetting what a relative macro was relative to (a new patch was loaded). */
  fun reset() { positions.clear(); base.clear() }

  /** The macro's position 0..1: a parameter's value (analog) or the offset from the middle (FM, 0.5 at rest). */
  fun position(def: MacroDef): Float =
    if (def.relative) positions[def.id] ?: 0.5f else state.norm(state.params[def.keys[0]]!!.id)

  /** The operator parameters a relative macro moves in the current algorithm. */
  fun targets(def: MacroDef): List<String> {
    val carriers = algo()?.carriers ?: setOf(1)
    return when (def.target) {
      FmTarget.MODULATOR_LEVELS -> (1..6).filter { it !in carriers }.map { "fm.op$it.level" }
      FmTarget.CARRIER_ATTACK_RATE -> carriers.sorted().map { "fm.op$it.r1" }
      FmTarget.CARRIER_RELEASE_RATE -> carriers.sorted().map { "fm.op$it.r4" }
      null -> emptyList()
    }
  }

  /** A finger went down on a relative macro: remember what it moves, so the movement is relative to that. */
  fun begin(def: MacroDef) {
    if (!def.relative) return
    base = HashMap(base)
    for (k in targets(def)) base[k] = real(k)
    if (positions[def.id] == null) positions[def.id] = 0.5f
  }

  fun set(def: MacroDef, n: Float) {
    if (def.relative) {
      positions[def.id] = n
      val delta = (n - 0.5f) * 2f * SPAN * (if (def.inverted) -1f else 1f)
      for (k in targets(def)) setReal(k, (base[k] ?: real(k)) + delta)
      return
    }
    def.keys.forEachIndexed { i, k ->
      val p = state.params[k]!!
      state.set(p.id, if (i == 0) n else n * FOLLOW)
    }
    for (s in def.switches) setReal(s, if (n > 0.02f) 1f else 0f)
  }

  fun resetToDefault(def: MacroDef) {
    if (def.relative) {
      for (k in targets(def)) base[k]?.let { setReal(k, it) }
      positions[def.id] = 0.5f
    } else def.keys.forEach { k -> val p = state.params[k]!!; state.set(p.id, p.toNorm(p.def)) }
  }

  /** Text under the knob: the parameter's value, a percentage when several move together, the offset for a relative macro. */
  fun text(def: MacroDef): String {
    if (def.relative) { val d = ((position(def) - 0.5f) * 200f).toInt(); return (if (d > 0) "+" else "") + "$d%" }
    if (def.keys.size > 1) return "${(position(def) * 100).toInt()}%"
    val p = state.params[def.keys[0]]!!
    return ParamUi.format(p, p.toReal(state.norm(p.id)))
  }

  /** What the knob shows of the sound, borrowing a parameter's picture or making its own. */
  fun feedback(def: MacroDef, live: Live): ((Fx) -> Unit)? {
    def.like?.let { name ->
      val kind = Feedback.of(name) ?: return null
      return { fx -> Feedback.eval(kind, name, state, live, fx) }
    }
    if (def.id == "espaco") return { fx -> fx.glow = maxOf(live.echo, live.halo); fx.halo = live.halo }
    if (def.id == "brilho") return { fx ->
      var m = 0f
      val carriers = live.algo?.carriers ?: setOf(1)
      for (o in 1..6) if (o !in carriers) m = maxOf(m, live.op[o])
      fx.glow = (m * 1.3f).coerceIn(0f, 1f)
    }
    if (def.id == "ataque") return { fx -> fx.glow = carrierStage(live, 1) }
    if (def.id == "cauda") return { fx -> fx.glow = carrierStage(live, 4) }
    return null
  }

  private fun carrierStage(live: Live, stage: Int): Float {
    var g = 0f
    for (c in live.algo?.carriers ?: setOf(1)) if (live.opStage[c] == stage) g = maxOf(g, live.op[c] * 1.5f + 0.3f)
    return g.coerceIn(0f, 1f)
  }
}
