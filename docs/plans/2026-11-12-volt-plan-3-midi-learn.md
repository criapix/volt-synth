# Volt Plan 3 — MIDI Learn Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let any control of the Yamaha CK61 (or any MIDI controller) drive any synth parameter or preset action. A MIDI button on the top bar enters Learn mode: tap a control on screen, move a control on the keyboard, done. Add soft takeover, Program Change, previous/next/panic as learnable actions, a MIDI map screen, and a channel setting.

**Architecture:** Kotlin only; the engine does not change. `MidiDispatch` gains three collaborators: `MidiLearn` (the learned map plus the Learn-mode state, saved in preferences), `SoftTakeover` (a pure rule deciding whether a controller value may move a parameter), and two callbacks for actions and Program Change. Every parameter change still goes through `SynthState`, so the screen keeps following MIDI exactly as plan 2 made it. On screen, a `LearnTarget` wrapper (driven by a `CompositionLocal`) puts a tappable overlay and a CC badge on every learnable control while Learn mode is on.

**Tech Stack:** Kotlin 2.3, Jetpack Compose (Material 3), `android.media.midi` (existing), SharedPreferences, JUnit 4.

---

## 0. Read this first

**Design document:** `docs/plans/2026-11-11-volt-design.md`, section 6 (MIDI), is what this plan builds; read it. Plans 1 and 2 (`docs/plans/2026-11-11-volt-plan-1-engine.md`, `docs/plans/2026-11-12-volt-plan-2-ui-presets.md`) built everything this plan touches; their section 0 has the machine facts and rules, which still hold:

- Repository `C:\Users\criap\OneDrive\Documentos\Github\volt-synth`, branch `main`. Work on a branch `feature/midi-learn`; merge locally at the end like plans 1 and 2. Do not push.
- Before building: `. .\tools\env.ps1`. Kotlin tests and APK: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`. The engine is not touched, but run `.\tools\test-engine.ps1` once at the end anyway.
- **Read `docs/NOTES.md` whole before starting.** Traps from it that apply here: PowerShell 5.1 `Set-Content -Encoding utf8` writes a BOM; this PC's locale is pt-BR, so tests that format numbers pass an explicit `Locale`; in the `ui` package the enum that plan 2 called `Unit` was renamed `ValueUnit`, and existing UI code writes `kotlin.Unit` explicitly. Follow that style in `ui`. Material `Text` keeps a 24 sp line height unless `lineHeight` is set; small texts set it.
- Code here was written without a compiler. Fix what does not compile. When a test fails, look for the cause in the code first; change a test's expectation only with a written reason in `docs/NOTES.md` (plan 2 has a section of such deviations: do the same).
- Code, comments and commits in English; on-screen text in Brazilian Portuguese; `NOTES.md` / `DEVICE-TEST.md` in Portuguese. End every commit message with the co-author line your session's instructions give you.
- **Honesty rule:** `adb shell input` cannot send MIDI and nobody can hear the emulator. Learn mode can be checked on screenshots only up to "armed, waiting". Binding, soft takeover and Program Change are verified by unit tests here and by the user on the phone with the CK61 (Task 10's checklist). Say exactly that in the report.

### Where things are now (end of plan 2)

| Thing | File | What matters for this plan |
|---|---|---|
| MIDI routing | `app/src/main/kotlin/com/criapix/volt/midi/MidiDispatch.kt` | `MidiDispatch(synth: Synth, params, log)`; channel filter (`channel`, -1 = omni, no UI yet); `activity` and `lastMessage` flows; factory CC map in `controller()` (1 wheel, 7 volume, 64 sustain, 71 reso, 74 cutoff, 120/123 panic). Called on the MIDI binder thread. |
| Parameter values | `state/SynthState.kt` | Implements `Synth`. `setParam` (what MIDI calls) marks the change as *external*, which flashes the tab. `norm(id)` is the current normalized value. Thread-safe. |
| Presets | `preset/AppModel.kt` | `step(±1)`, `select(id)`, `presets` (library order: factory by file name, then user by name), `number(id)`. **Not thread-safe:** call on the UI thread. |
| Screen | `ui/VoltScreen.kt`, `ui/TopBar.kt`, `ui/GroupPanel.kt`, `ui/Knob.kt`, `ui/Selector.kt`, `ui/Dialogs.kt`, `ui/Panel.kt`, `ui/ParamUi.kt` | `ScreenIo` carries what the screen needs from outside; `Panel.groups` places every parameter; `ParamUi.of(name)` gives its label and widget. |
| Preferences | `Prefs.kt` | `haptics`, favorites. |
| Factory bank | `app/src/main/assets/presets/factory/` | 40 presets, `001-init` … `086-sirene`. Program Change uses library order on purpose (NOTES.md says so). |

### What the CK61 sends (from the device log, `docs/NOTES.md`)

All on channel 1: CC 71, 72, 73, 74, 91, 92, 93, 13, 14, 15, 9, 64 (sustain; values in between, not half-pedal), 1 (mod wheel) and pitch bend; also Active Sensing and one SysEx (ignored). Which physical control sends which CC is **not known**. Learn mode and the "última mensagem" monitor exist so the user can find out.

### Decisions taken while writing this plan (report them to the user at the end)

1. **More factory controllers, following General MIDI.** CC 72 (release time), 73 (attack time) and 75 (decay time) are GM "sound controllers". The CK61 sends 72 and 73, most likely from knobs labelled that way on its panel. They now drive the **amplitude envelope's** release, attack and decay, beside 71 (resonance) and 74 (cutoff). CC 91/92/93 (GM reverb, tremolo, chorus) stay unassigned until plan 4 adds effects. Learned bindings override all of these.
2. **Notes can only be learned for actions** (previous, next, panic), as the design says. A key learned for an action stops playing a note.
3. **Action triggering.** An action fires when a CC goes from below 64 to 64 or above, or on a note-on: one fire per press for buttons that send 127 on press and 0 on release. Some buttons send 127 and 0 on *alternate* presses; for those, a binding can be switched to "qualquer valor dispara" (any message fires, with a 150 ms debounce) on the map screen. The same switch makes a CC button flip an on/off parameter (e.g. `osc1.sync`).
4. **Soft takeover rule.** A controller moves a parameter only while it is "in control": the parameter's value is still the last one this controller sent. Loading a preset, touching the knob on screen, or another controller moving it detaches the controller. A detached controller is ignored until its value comes within 1.5/127 of the parameter or crosses it. While it is detached, the screen shows where to bring it ("Cutoff: leve o controle até 1,2 kHz"). Right after a binding is learned, the controller is in control at once, so the next movement acts immediately. Takeover can be switched off in settings (then values jump).
5. **Program Change** n (0-based) loads the preset at position n+1 in library order. Bank Select (CC 0/32) is ignored, so only the first 128 presets are reachable this way. CC 0, 32 and 120–127 cannot be learned.
6. **The map is keyed by channel.** A binding learned on channel 1 does not react to the same CC on channel 2. The new channel setting (Omni or 1–16) filters everything, as before.
7. **Badges only in Learn mode.** The CC number of a bound control is shown on the control while Learn mode is on, and on the map screen. Outside Learn mode the panel stays clean.
8. **Future targets.** Effect on/off and arpeggiator hold (design 6.3) do not exist yet. In plan 4 they are parameters with a `ToggleUi`, so the generic `Target.Param` already covers them, including the "qualquer valor" flip.

### Layout at the end of this plan (new and changed files only)

```
app/build.gradle.kts                                        (version 0.3.0)
app/src/main/kotlin/com/criapix/volt/
  MainActivity.kt  Prefs.kt
  midi/MidiMap.kt  midi/SoftTakeover.kt  midi/MidiLearn.kt  midi/MidiDispatch.kt
  preset/AppModel.kt                                         (programChange)
  ui/Panel.kt  ui/LearnUi.kt  ui/MidiMapDialog.kt  ui/TopBar.kt  ui/GroupPanel.kt  ui/VoltScreen.kt  ui/Dialogs.kt
app/src/test/kotlin/com/criapix/volt/
  midi/MidiMapTest.kt  midi/SoftTakeoverTest.kt  midi/MidiLearnTest.kt  midi/MidiRoutingTest.kt
  preset/AppModelTest.kt  ui/LearnLabelsTest.kt  ui/PanelTest.kt
```

---

### Task 1: The MIDI map — sources, targets, bindings, persistence

**Files:**
- Create: `app/src/main/kotlin/com/criapix/volt/midi/MidiMap.kt`, `app/src/test/kotlin/com/criapix/volt/midi/MidiMapTest.kt`

The reference project has a similar file (`"C:\Users\criap\OneDrive\Documentos\Github\mvave fm1\app\src\main\kotlin\com\criapix\fm1droid\midi\MidiMap.kt"`); this one keeps its shape but targets synth parameters **by name** (names are permanent, ids are not) and preset actions.

**Step 1: Failing test — `MidiMapTest.kt`**

```kotlin
package com.criapix.volt.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiMapTest {
  private val cutoff = Target.Param("filter.cutoff")
  private val next = Target.Action(MidiAction.NEXT)

  @Test fun aSourceHasOneTargetAndATargetMayHaveSeveralSources() {
    val m = MidiMap()
    m.bind(Source.Cc(0, 74), cutoff)
    m.bind(Source.Cc(0, 20), cutoff)
    m.bind(Source.Cc(0, 74), next)  // re-learning a source replaces its target
    assertEquals(next, m[Source.Cc(0, 74)]?.target)
    assertEquals(listOf(Source.Cc(0, 20)), m.sourcesOf(cutoff))
  }

  @Test fun encodeDecodeRoundTripsIncludingTapAndDottedNames() {
    val m = MidiMap()
    m.bind(Source.Cc(0, 74), cutoff)
    m.bind(Source.Note(2, 36), next, tap = true)
    val back = MidiMap.decode(m.encode())
    assertEquals(m.entries(), back.entries())
    assertTrue(back[Source.Note(2, 36)]!!.tap)
  }

  @Test fun damagedEntriesAreSkipped() {
    val m = MidiMap.decode("cc.0.74=p:filter.cutoff;lixo;cc.99.1=p:x;note.0.200=a:next;cc.0.5=a:nope;cc.0.6=q:z")
    assertEquals(1, m.entries().size)
    assertNull(m[Source.Cc(0, 5)])
  }

  @Test fun clearRemovesEveryBindingOfATarget() {
    val m = MidiMap()
    m.bind(Source.Cc(0, 74), cutoff)
    m.bind(Source.Cc(0, 20), cutoff)
    m.bind(Source.Cc(0, 30), next)
    m.clear(cutoff)
    assertEquals(listOf(Source.Cc(0, 30)), m.entries().map { it.first })
    m.clearAll()
    assertTrue(m.entries().isEmpty())
  }

  @Test fun sourcesAreDescribedForTheUser() {
    assertEquals("CC 74 · canal 1", describe(Source.Cc(0, 74)))
    assertEquals("Nota C4 · canal 3", describe(Source.Note(2, 60)))
  }
}
```

**Step 2: Run** `.\gradlew.bat :app:testDebugUnitTest` — compile error.

**Step 3: `MidiMap.kt`**

```kotlin
package com.criapix.volt.midi

/** What a controller sends; [ch] is 0..15. */
sealed interface Source {
  val ch: Int
  data class Cc(override val ch: Int, val num: Int) : Source
  data class Note(override val ch: Int, val note: Int) : Source
}

/** Preset and safety actions a controller can trigger. [key] is stored in preferences: never change it. */
enum class MidiAction(val key: String, val label: String) {
  PREV("prev", "Preset anterior"),
  NEXT("next", "Próximo preset"),
  PANIC("panic", "Pânico"),
}

/** What a binding drives. Parameters are stored by their permanent name. */
sealed interface Target {
  data class Param(val name: String) : Target
  data class Action(val action: MidiAction) : Target
}

/** [tap]: every message fires (debounced), for buttons that send 127 and 0 on alternate presses. */
data class Binding(val target: Target, val tap: Boolean = false)

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

fun noteName(note: Int) = "${NOTE_NAMES[note % 12]}${note / 12 - 1}"  // 60 = C4

fun describe(s: Source): String = when (s) {
  is Source.Cc -> "CC ${s.num} · canal ${s.ch + 1}"
  is Source.Note -> "Nota ${noteName(s.note)} · canal ${s.ch + 1}"
}

/** Source -> binding, in learning order. Not thread-safe: MidiLearn guards it. */
class MidiMap {
  private val m = LinkedHashMap<Source, Binding>()

  operator fun get(s: Source): Binding? = m[s]
  fun entries(): List<Pair<Source, Binding>> = m.entries.map { it.key to it.value }
  fun sourcesOf(t: Target): List<Source> = m.filterValues { it.target == t }.keys.toList()
  fun bind(s: Source, t: Target, tap: Boolean = false) { m.remove(s); m[s] = Binding(t, tap) }
  fun setTap(s: Source, tap: Boolean) { m[s]?.let { m[s] = it.copy(tap = tap) } }
  fun unbind(s: Source) { m.remove(s) }
  fun clear(t: Target) { m.entries.removeAll { it.value.target == t } }
  fun clearAll() = m.clear()

  /** "cc.0.74=p:filter.cutoff;note.0.36=a:next!" — '!' marks a tap binding. */
  fun encode(): String = m.entries.joinToString(";") { (s, b) ->
    val src = when (s) {
      is Source.Cc -> "cc.${s.ch}.${s.num}"
      is Source.Note -> "note.${s.ch}.${s.note}"
    }
    val dst = when (val t = b.target) {
      is Target.Param -> "p:${t.name}"
      is Target.Action -> "a:${t.action.key}"
    }
    "$src=$dst" + if (b.tap) "!" else ""
  }

  companion object {
    /** Entries that do not parse are skipped: a damaged preference never breaks MIDI. */
    fun decode(text: String): MidiMap {
      val map = MidiMap()
      for (entry in text.split(';')) {
        val eq = entry.indexOf('=')
        if (eq <= 0) continue
        val s = source(entry.substring(0, eq)) ?: continue
        val rest = entry.substring(eq + 1)
        val t = target(rest.removeSuffix("!")) ?: continue
        map.bind(s, t, rest.endsWith("!"))
      }
      return map
    }

    private fun source(text: String): Source? {
      val p = text.split('.')
      if (p.size != 3) return null
      val ch = p[1].toIntOrNull()?.takeIf { it in 0..15 } ?: return null
      val n = p[2].toIntOrNull()?.takeIf { it in 0..127 } ?: return null
      return when (p[0]) {
        "cc" -> Source.Cc(ch, n)
        "note" -> Source.Note(ch, n)
        else -> null
      }
    }

    private fun target(text: String): Target? = when {
      text.startsWith("p:") && text.length > 2 -> Target.Param(text.substring(2))
      text.startsWith("a:") -> MidiAction.entries.firstOrNull { it.key == text.substring(2) }?.let { Target.Action(it) }
      else -> null
    }
  }
}
```

A parameter name that no longer exists in the engine survives decoding; `MidiDispatch` ignores it at runtime. That keeps a map saved by a newer app version from breaking an older one.

**Step 4: Run** — passes.

**Step 5: Commit** — `feat(app): MIDI map of learned bindings`.

---

### Task 2: Soft takeover

**Files:**
- Create: `app/src/main/kotlin/com/criapix/volt/midi/SoftTakeover.kt`, `app/src/test/kotlin/com/criapix/volt/midi/SoftTakeoverTest.kt`

The rule (decision 4): a controller value `v` for parameter `p`, whose current value is `cur`, is accepted when takeover is off, when `cur` is unknown (NaN), when `p` still holds the value this layer last sent, when `v` is within `tol` of `cur`, or when this value and the previous incoming value for `p` lie on opposite sides of `cur` (a crossing). Otherwise it is ignored.

**Step 1: Failing test — `SoftTakeoverTest.kt`**

```kotlin
package com.criapix.volt.midi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftTakeoverTest {
  private val values = HashMap<Int, Float>()
  private val t = SoftTakeover { values[it] ?: Float.NaN }
  private val tol = 1.5f / 127f

  /** What the dispatcher does on acceptance: the parameter now holds v. */
  private fun send(p: Int, v: Float): Boolean = t.accept(p, v, tol).also { if (it) values[p] = v }

  @Test fun aDetachedControllerWaitsUntilItReachesTheValue() {
    values[1] = 0.8f
    assertFalse(send(1, 0.1f))
    assertFalse(send(1, 0.5f))
    assertTrue(send(1, 0.795f))   // within tolerance
    assertTrue(send(1, 0.2f))     // in control now: follows freely
  }

  @Test fun crossingTheValueCatchesIt() {
    values[1] = 0.5f
    assertFalse(send(1, 0.2f))
    assertTrue(send(1, 0.7f))     // jumped over 0.5 between two messages
  }

  @Test fun somethingElseChangingTheParameterDetachesTheController() {
    values[1] = 0.5f
    assertTrue(send(1, 0.5f))
    assertTrue(send(1, 0.6f))
    values[1] = 0.1f              // a preset was loaded, or the knob was turned on screen
    assertFalse(send(1, 0.65f))
  }

  @Test fun attachPutsAControllerInControlAtOnce() {
    values[1] = 0.5f
    t.attach(1)
    assertTrue(send(1, 0.9f))
  }

  @Test fun offOrUnknownAlwaysAccepts() {
    assertTrue(send(7, 0.3f))     // unknown current value
    values[1] = 0.9f
    t.enabled = false
    assertTrue(send(1, 0.0f))
  }
}
```

**Step 2: `SoftTakeover.kt`**

```kotlin
package com.criapix.volt.midi

import kotlin.math.abs

/**
 * Decides whether a controller value may move a parameter (design 6.3, "captura suave").
 * [current] gives a parameter's normalized value now (NaN when unknown).
 * Not thread-safe: used from the MIDI thread only.
 */
class SoftTakeover(private val current: (Int) -> Float) {
  @Volatile var enabled = true
  private val lastSent = HashMap<Int, Float>()
  private val lastIn = HashMap<Int, Float>()

  /** [v] is already snapped to the parameter's steps. True: send it, and it becomes the controller's value. */
  fun accept(param: Int, v: Float, tol: Float): Boolean {
    val cur = current(param)
    val prev = lastIn.put(param, v)
    val sent = lastSent[param]
    val ok = !enabled || cur.isNaN() ||
      (sent != null && abs(sent - cur) < 1e-4f) ||
      abs(v - cur) <= tol ||
      (prev != null && (prev - cur) * (v - cur) <= 0f)
    if (ok) lastSent[param] = v
    return ok
  }

  /** The controller is in control of [param] from now on (right after it was learned). */
  fun attach(param: Int) {
    val c = current(param)
    if (!c.isNaN()) lastSent[param] = c
  }
}
```

**Step 3: Run** — passes.

**Step 4: Commit** — `feat(app): soft takeover rule`.

---

### Task 3: MidiLearn — the map, Learn mode, persistence

**Files:**
- Create: `app/src/main/kotlin/com/criapix/volt/midi/MidiLearn.kt`, `app/src/test/kotlin/com/criapix/volt/midi/MidiLearnTest.kt`

`MidiLearn` owns the `MidiMap` and the Learn-mode state, and saves the map through a callback on every change. The UI thread arms targets; the MIDI thread captures sources and looks bindings up. All map access is `@Synchronized`; state the screen shows is in `StateFlow`s.

**Step 1: Failing test — `MidiLearnTest.kt`**

```kotlin
package com.criapix.volt.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiLearnTest {
  private var saved = ""
  private val learn = MidiLearn(MidiMap()) { saved = it }
  private val cutoff = Target.Param("filter.cutoff")
  private val next = Target.Action(MidiAction.NEXT)

  @Test fun nothingIsCapturedOutsideLearnModeOrWithoutAnArmedTarget() {
    assertFalse(learn.capture(Source.Cc(0, 74)))
    learn.setActive(true)
    assertFalse(learn.capture(Source.Cc(0, 74)))
  }

  @Test fun captureBindsDisarmsSavesAndReports() {
    learn.setActive(true)
    learn.arm(cutoff)
    assertTrue(learn.capture(Source.Cc(0, 74)))
    assertEquals(cutoff, learn.lookup(Source.Cc(0, 74))?.target)
    assertNull(learn.armed.value)
    assertTrue(learn.active.value)                     // stays in Learn mode for the next control
    assertEquals("cc.0.74=p:filter.cutoff", saved)
    assertEquals(Source.Cc(0, 74), learn.learned.value?.source)
    assertEquals(1, learn.bindings.value.size)
  }

  @Test fun notesOnlyBindToActions() {
    learn.setActive(true)
    learn.arm(cutoff)
    assertFalse(learn.capture(Source.Note(0, 60)))
    assertEquals(cutoff, learn.armed.value)            // still waiting for a CC
    learn.arm(next)
    assertTrue(learn.capture(Source.Note(0, 36)))
  }

  @Test fun bankSelectAndChannelModeMessagesCannotBeLearned() {
    learn.setActive(true)
    learn.arm(cutoff)
    for (cc in listOf(0, 32, 120, 121, 123, 127)) assertFalse("CC $cc", learn.capture(Source.Cc(0, cc)))
  }

  @Test fun leavingLearnModeDisarms() {
    learn.setActive(true)
    learn.arm(cutoff)
    learn.setActive(false)
    assertNull(learn.armed.value)
    learn.arm(cutoff)                                  // ignored outside Learn mode
    assertNull(learn.armed.value)
  }

  @Test fun editsArePublishedAndSaved() {
    learn.setActive(true)
    learn.arm(cutoff); learn.capture(Source.Cc(0, 74))
    learn.arm(next); learn.capture(Source.Cc(0, 30))
    learn.setTap(Source.Cc(0, 30), true)
    assertTrue(learn.lookup(Source.Cc(0, 30))!!.tap)
    learn.unbind(Source.Cc(0, 74))
    assertEquals(listOf(Source.Cc(0, 30)), learn.bindings.value.map { it.first })
    assertEquals("cc.0.30=a:next!", saved)
    learn.clearAll()
    assertEquals("", saved)
  }
}
```

**Step 2: `MidiLearn.kt`**

```kotlin
package com.criapix.volt.midi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The learned MIDI map and the Learn-mode state. The UI thread turns Learn mode on, arms a target
 * and edits the map; the MIDI thread captures and looks up. [save] receives the encoded map after
 * every change (preferences).
 */
class MidiLearn(private val map: MidiMap = MidiMap(), private val save: (String) -> kotlin.Unit = {}) {
  /** The binding just learned; [seq] grows each time (the screen shows a confirmation). */
  data class Learned(val source: Source, val target: Target, val seq: Long)

  private val _active = MutableStateFlow(false)
  private val _armed = MutableStateFlow<Target?>(null)
  private val _bindings = MutableStateFlow(map.entries())
  private val _learned = MutableStateFlow<Learned?>(null)
  private var seq = 0L

  val active: StateFlow<Boolean> = _active
  /** The control the user tapped in Learn mode, waiting for a MIDI message. */
  val armed: StateFlow<Target?> = _armed
  val bindings: StateFlow<List<Pair<Source, Binding>>> = _bindings
  val learned: StateFlow<Learned?> = _learned

  fun setActive(on: Boolean) {
    _active.value = on
    if (!on) _armed.value = null
  }

  fun arm(t: Target) { if (_active.value) _armed.value = t }

  /** MIDI thread. Binds [s] to the armed target. True when the message was used for learning (do not act on it). */
  @Synchronized fun capture(s: Source): Boolean {
    if (!_active.value) return false
    val t = _armed.value ?: return false
    if (s is Source.Note && t !is Target.Action) return false
    if (s is Source.Cc && (s.num == 0 || s.num == 32 || s.num >= 120)) return false
    map.bind(s, t)
    _armed.value = null
    _learned.value = Learned(s, t, ++seq)
    publish()
    return true
  }

  @Synchronized fun lookup(s: Source): Binding? = map[s]
  @Synchronized fun sourcesOf(t: Target): List<Source> = map.sourcesOf(t)
  @Synchronized fun setTap(s: Source, tap: Boolean) { map.setTap(s, tap); publish() }
  @Synchronized fun unbind(s: Source) { map.unbind(s); publish() }
  @Synchronized fun clear(t: Target) { map.clear(t); publish() }
  @Synchronized fun clearAll() { map.clearAll(); publish() }

  private fun publish() {
    _bindings.value = map.entries()
    save(map.encode())
  }
}
```

**Step 3: Run** — passes.

**Step 4: Commit** — `feat(app): Learn mode state and persisted map`.

---

### Task 4: Routing — learned bindings, actions, Program Change, takeover in MidiDispatch

**Files:**
- Modify: `app/src/main/kotlin/com/criapix/volt/midi/MidiDispatch.kt`
- Create: `app/src/test/kotlin/com/criapix/volt/midi/MidiRoutingTest.kt`

Order of precedence for a CC: **learning** (capture it) → **learned binding** → **factory map**. For a note-on: learning (actions only) → learned binding (consumed, not played) → play. Every parameter change from a controller goes through soft takeover, then to `synth.setParam` (which is `SynthState` in the app, so the screen follows).

The existing `MidiDispatchTest` must keep passing unchanged: all new constructor parameters have defaults, and with the default `current = { NaN }` takeover always accepts.

**Step 1: Failing test — `MidiRoutingTest.kt`**

It uses the real parameter table (`TestParams`, from plan 2) and a real `SynthState` over a recording synth, so takeover sees real values.

```kotlin
package com.criapix.volt.midi

import com.criapix.volt.TestParams
import com.criapix.volt.state.SynthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiRoutingTest {
  private class Recorder : Synth {
    val notes = ArrayList<Int>()
    override fun noteOn(note: Int, velocity: Float) { notes += note }
    override fun noteOff(note: Int) { notes += -note }
    override fun setParam(id: Int, normalized: Float) {}
    override fun pitchBend(value: Float) {}
    override fun modWheel(value: Float) {}
    override fun sustain(on: Boolean) {}
    override fun allNotesOff() {}
  }

  private val p = TestParams.params
  private val rec = Recorder()
  private val state = SynthState(p, rec)
  private val learn = MidiLearn()
  private val actions = ArrayList<MidiAction>()
  private val programs = ArrayList<Int>()
  private var now = 0L
  private val d = MidiDispatch(
    synth = state, params = p, learn = learn, current = state::norm,
    onAction = { actions += it }, onProgram = { programs += it }, clock = { now },
  )
  private fun send(vararg b: Int) { val a = ByteArray(b.size) { b[it].toByte() }; d.message(a, a.size) }
  private fun id(name: String) = p[name]!!.id
  private fun learnCc(target: Target, cc: Int, value: Int = 64) { learn.setActive(true); learn.arm(target); send(0xB0, cc, value); learn.setActive(false) }

  @Test fun aLearnedControllerDrivesItsParameterAtOnce() {
    learnCc(Target.Param("osc1.level"), 20, value = 10)
    assertEquals(1f, state.real(id("osc1.level")), 0f)      // the learning message itself changes nothing
    send(0xB0, 20, 0)                                       // attached on learn: acts immediately
    assertEquals(0f, state.real(id("osc1.level")), 0f)
  }

  @Test fun aLearnedBindingOverridesTheFactoryMap() {
    learnCc(Target.Param("osc1.level"), 74)
    send(0xB0, 74, 0)
    assertEquals(0f, state.real(id("osc1.level")), 0f)
    assertEquals(20000f, state.real(id("filter.cutoff")), 1f)
  }

  @Test fun theGeneralMidiSoundControllersReachTheAmplitudeEnvelope() {
    d.softTakeover = false
    send(0xB0, 73, 127); send(0xB0, 72, 127); send(0xB0, 75, 0)
    assertEquals(10f, state.real(id("aenv.attack")), 0.01f)
    assertEquals(10f, state.real(id("aenv.release")), 0.01f)
    assertEquals(0.001f, state.real(id("aenv.decay")), 0.0001f)
  }

  @Test fun softTakeoverWaitsForThePickupAndSaysWhere() {
    val cutoff = id("filter.cutoff")                         // default 20 kHz = 1.0
    send(0xB0, 74, 0)
    assertEquals(1f, state.norm(cutoff), 0f)
    assertEquals(cutoff, d.pickup.value?.param)
    send(0xB0, 74, 127)                                      // reaches the value
    assertNull(d.pickup.value)
    send(0xB0, 74, 0)                                        // in control: follows
    assertEquals(0f, state.norm(cutoff), 0f)
  }

  @Test fun loadingAPresetDetachesTheController() {
    val cutoff = id("filter.cutoff")
    send(0xB0, 74, 127)
    state.load(state.snapshot().also { it[cutoff] = 0.2f })
    send(0xB0, 74, 120)
    assertEquals(0.2f, state.norm(cutoff), 1e-6f)
    assertNotNull(d.pickup.value)
  }

  @Test fun turningTheKnobOnScreenDetachesTheController() {
    val cutoff = id("filter.cutoff")
    send(0xB0, 74, 127)
    state.set(cutoff, 0.3f)
    send(0xB0, 74, 126)
    assertEquals(0.3f, state.norm(cutoff), 1e-6f)
  }

  @Test fun notesLearnOnlyForActionsAndALearnedNoteDoesNotPlay() {
    learn.setActive(true)
    learn.arm(Target.Param("filter.cutoff"))
    send(0x90, 60, 100)                                       // a param target ignores notes: the note plays
    assertEquals(listOf(60), rec.notes)
    learn.arm(Target.Action(MidiAction.NEXT))
    send(0x90, 36, 100)                                       // learned, not played
    learn.setActive(false)
    send(0x90, 36, 100); send(0x80, 36, 0)
    assertEquals(listOf(60), rec.notes)
    assertEquals(listOf(MidiAction.NEXT), actions)
  }

  @Test fun anActionFiresOncePerPress() {
    learnCc(Target.Action(MidiAction.NEXT), 30, value = 0)
    send(0xB0, 30, 127); send(0xB0, 30, 127); send(0xB0, 30, 0); send(0xB0, 30, 127)
    assertEquals(2, actions.size)
  }

  @Test fun tapModeFiresOnAnyValueWithADebounce() {
    learnCc(Target.Action(MidiAction.PREV), 31, value = 0)
    learn.setTap(Source.Cc(0, 31), true)
    now = 1_000_000_000L; send(0xB0, 31, 127)
    now += 50_000_000L; send(0xB0, 31, 0)                     // 50 ms later: bounce, ignored
    now += 400_000_000L; send(0xB0, 31, 0)                    // a real second press
    assertEquals(listOf(MidiAction.PREV, MidiAction.PREV), actions)
  }

  @Test fun tapModeFlipsAnOnOffParameter() {
    val sync = id("osc1.sync")
    learnCc(Target.Param("osc1.sync"), 33, value = 0)
    learn.setTap(Source.Cc(0, 33), true)
    now = 1_000_000_000L; send(0xB0, 33, 127)
    assertEquals(1f, state.real(sync), 0f)
    now += 400_000_000L; send(0xB0, 33, 0)
    assertEquals(0f, state.real(sync), 0f)
  }

  @Test fun programChangeIsReported() {
    send(0xC0, 5)
    assertEquals(listOf(5), programs)
  }

  @Test fun aBindingToAParameterThatNoLongerExistsIsIgnored() {
    learn.setActive(true); learn.arm(Target.Param("no.such")); send(0xB0, 40, 1); learn.setActive(false)
    send(0xB0, 40, 127)  // must not throw
    assertTrue(actions.isEmpty())
  }
}
```

**Step 2: Run** — compile errors (new constructor parameters, `pickup`, `softTakeover`).

**Step 3: `MidiDispatch.kt`** — keep the `Synth` interface and the existing parsing, logging and `activity`/`lastMessage` flows. Change the class header and add the routing:

```kotlin
/**
 * Turns whole MIDI messages into synth calls (design section 6): learned bindings first, then the
 * factory map; Program Change and learnable actions go to callbacks, which the app runs on the UI
 * thread. Every parameter change from a controller passes soft takeover. Runs on the MIDI thread.
 */
class MidiDispatch(
  private val synth: Synth,
  private val params: Params,
  private val log: (String) -> kotlin.Unit = {},
  val learn: MidiLearn = MidiLearn(),
  private val current: (Int) -> Float = { Float.NaN },
  private val onAction: (MidiAction) -> kotlin.Unit = {},
  private val onProgram: (Int) -> kotlin.Unit = {},
  private val clock: () -> Long = System::nanoTime,
) {
  /** A controller is detached from [param] and must be brought to [value] (normalized) to take over. */
  data class Pickup(val param: Int, val value: Float)

  companion object {
    /** Factory controller -> parameter name (design 6.2 plus the General MIDI sound controllers). */
    val FACTORY_PARAMS = linkedMapOf(
      7 to "master.volume", 71 to "filter.reso", 72 to "aenv.release",
      73 to "aenv.attack", 74 to "filter.cutoff", 75 to "aenv.decay",
    )
    private const val DEBOUNCE_NS = 150_000_000L
  }

  private val takeover = SoftTakeover(current)
  var softTakeover: Boolean
    get() = takeover.enabled
    set(v) { takeover.enabled = v }

  private val _pickup = MutableStateFlow<Pickup?>(null)
  val pickup: StateFlow<Pickup?> = _pickup

  private val factory = FACTORY_PARAMS.mapNotNull { (cc, name) -> params[name]?.let { cc to it } }.toMap()
  private val prevValue = HashMap<Source, Int>()
  private val lastFire = HashMap<Source, Long>()
  // ... existing fields: channel, _activity, _last, lastDenseLog, seenSystem
```

Delete the old `cutoff`, `reso`, `volume` fields. In `message()`:

```kotlin
      0x90 -> if (len >= 3) {
        _last.value = "Nota $d1 · canal ${ch + 1} = $d2"
        log("MIDI nota ${if (d2 == 0) "off" else "on"} $d1 vel $d2 canal ${ch + 1}")
        val src = Source.Note(ch, d1)
        when {
          d2 > 0 && learn.capture(src) -> learned(src)
          learn.lookup(src) != null -> apply(learn.lookup(src)!!, src, if (d2 > 0) 127 else 0)
          d2 == 0 -> synth.noteOff(d1)
          else -> synth.noteOn(d1, d2 / 127f)
        }
      }
      0x80 -> if (len >= 3) {
        log("MIDI nota off $d1 canal ${ch + 1}")
        val src = Source.Note(ch, d1)
        val b = learn.lookup(src)
        if (b != null) apply(b, src, 0) else synth.noteOff(d1)
      }
      0xB0 -> if (len >= 3) {
        _last.value = "CC $d1 · canal ${ch + 1} = $d2"
        log("MIDI CC $d1 = $d2 canal ${ch + 1}")
        val src = Source.Cc(ch, d1)
        if (learn.capture(src)) { learned(src); return }
        val b = learn.lookup(src)
        if (b != null) apply(b, src, d2) else controller(d1, d2)
      }
      0xC0 -> if (len >= 2) {
        _last.value = "Programa ${d1 + 1} · canal ${ch + 1}"
        log("MIDI program change ${d1 + 1} canal ${ch + 1}")
        onProgram(d1)
      }
```

(Keep the existing `0xE0` branch and the `else` branch.)

New private functions, and the factory map rewritten:

```kotlin
  private fun learned(src: Source) {
    val t = learn.lookup(src)?.target
    log("MIDI aprendido: ${describe(src)} -> $t")
    if (t is Target.Param) params[t.name]?.let { takeover.attach(it.id) }
  }

  private fun apply(b: Binding, src: Source, value: Int) {
    val prev = prevValue.put(src, value)
    when (val t = b.target) {
      is Target.Action -> if (fires(b, src, prev, value)) { log("MIDI ação ${t.action.key}"); onAction(t.action) }
      is Target.Param -> {
        val p = params[t.name] ?: return
        val onOff = p.curve == Curve.STEP && p.min == 0f && p.max == 1f
        if (b.tap && onOff) {
          if (fires(b, src, prev, value)) synth.setParam(p.id, if (current(p.id) >= 0.5f) 0f else 1f)
        } else {
          fromController(p, value / 127f)
        }
      }
    }
  }

  /** Rising edge into >= 64, or any message for a tap binding (debounced). */
  private fun fires(b: Binding, src: Source, prev: Int?, value: Int): Boolean {
    val now = clock()
    val ok = if (b.tap) now - (lastFire[src] ?: Long.MIN_VALUE / 2) > DEBOUNCE_NS
             else value >= 64 && (prev == null || prev < 64)
    if (ok) lastFire[src] = now
    return ok
  }

  private fun fromController(p: Param, n: Float) {
    val q = p.toNorm(p.toReal(n))  // snapped like SynthState snaps it
    val steps = if (p.curve == Curve.STEP) (p.max - p.min) else 0f
    val tol = maxOf(1.5f / 127f, if (steps > 0f) 0.5f / steps else 0f)
    if (takeover.accept(p.id, q, tol)) {
      synth.setParam(p.id, q)
      if (_pickup.value?.param == p.id) _pickup.value = null
    } else {
      _pickup.value = Pickup(p.id, current(p.id))
    }
  }

  private fun controller(cc: Int, value: Int) {
    factory[cc]?.let { fromController(it, value / 127f); return }
    when (cc) {
      1 -> synth.modWheel(value / 127f)
      64 -> synth.sustain(value >= 64)
      120, 123 -> synth.allNotesOff()
    }
  }
```

Imports to add: `com.criapix.volt.Curve`, `com.criapix.volt.Param`. `Synth` stays in this file.

Note on `aLearnedControllerDrivesItsParameterAtOnce`: `osc1.level` defaults to 1.0. Learning attaches the controller (the parameter still holds 1.0, which is now "what it last sent"), so `CC 20 = 0` is accepted although it is far from 1.0. That is decision 4's "acts immediately".

**Step 4: Run** `.\gradlew.bat :app:testDebugUnitTest` — `MidiRoutingTest` and the unchanged `MidiDispatchTest` pass. If an old test breaks, the defaults are wrong; fix the code, not the old test.

**Step 5: Commit** — `feat(app): learned bindings, actions, Program Change and soft takeover in MIDI routing`.

---

### Task 5: Program Change in AppModel

**Files:**
- Modify: `preset/AppModel.kt`, `app/src/test/kotlin/com/criapix/volt/preset/AppModelTest.kt`

**Step 1: Failing test** — append to `AppModelTest`:

```kotlin
  @Test fun programChangeLoadsThePresetAtThatPosition() {
    val m = model().apply { restore() }
    m.programChange(2)                       // third in library order
    assertEquals("Claro", m.current.value.name)
    m.programChange(99)
    assertEquals("Claro", m.current.value.name)
    assertNotNull(m.message.value)
  }
```

**Step 2: Implement** in `AppModel`:

```kotlin
  /** MIDI Program Change [program] (0-based) loads the preset at position program+1 in library order. */
  fun programChange(program: Int) {
    val ref = _presets.value.getOrNull(program)
    if (ref == null) { _message.value = "Programa ${program + 1}: não há preset nessa posição"; return }
    select(ref.id)
  }
```

**Step 3: Run** — passes. **Step 4: Commit** — `feat(app): Program Change selects presets in library order`.

---

### Task 6: Preferences and wiring in MainActivity

**Files:**
- Modify: `Prefs.kt`, `MainActivity.kt`, `midi/MidiDispatch.kt` (only the `MidiSettings` interface below, at the end of the file)

**Step 1: `Prefs.kt`** — add:

```kotlin
  /** The learned MIDI map, MidiMap.encode() format. */
  var midiMap: String
    get() = p.getString("midiMap", "") ?: ""
    set(v) = p.edit().putString("midiMap", v).apply()

  /** -1 = Omni (every channel), else 0..15. */
  var midiChannel: Int
    get() = p.getInt("midiChannel", -1)
    set(v) = p.edit().putInt("midiChannel", v).apply()

  var softTakeover: Boolean
    get() = p.getBoolean("softTakeover", true)
    set(v) = p.edit().putBoolean("softTakeover", v).apply()
```

(`apply()` is safe from the MIDI thread, where `MidiLearn` saves after a capture.)

**Step 2: `MidiSettings`** — append to `midi/MidiDispatch.kt`:

```kotlin
/** MIDI settings the screen edits; the app keeps them in preferences and applies them to the dispatcher. */
interface MidiSettings {
  var channel: Int          // -1 = Omni
  var softTakeover: Boolean
}
```

**Step 3: `MainActivity.onCreate`** — replace the `dispatch = …` line:

```kotlin
    val main = Handler(Looper.getMainLooper())
    val learn = MidiLearn(MidiMap.decode(prefs.midiMap)) { prefs.midiMap = it }
    dispatch = MidiDispatch(
      synth = state, params = params, log = { AppLog.log(it) }, learn = learn, current = state::norm,
      onAction = { a ->
        main.post {
          when (a) {
            MidiAction.PREV -> model.step(-1)
            MidiAction.NEXT -> model.step(1)
            MidiAction.PANIC -> state.allNotesOff()
          }
        }
      },
      onProgram = { n -> main.post { model.programChange(n) } },
    ).apply {
      channel = prefs.midiChannel
      softTakeover = prefs.softTakeover
    }
    val midiSettings = object : MidiSettings {
      override var channel: Int
        get() = prefs.midiChannel
        set(v) { prefs.midiChannel = v; dispatch.channel = v }
      override var softTakeover: Boolean
        get() = prefs.softTakeover
        set(v) { prefs.softTakeover = v; dispatch.softTakeover = v }
    }
```

and pass to `ScreenIo` (fields added in Task 8): `learn = learn, pickup = dispatch.pickup, midiSettings = midiSettings`.

Imports: `android.os.Handler`, `android.os.Looper`, `com.criapix.volt.midi.MidiAction`, `MidiLearn`, `MidiMap`, `MidiSettings`.

**Step 4: Version** — `app/build.gradle.kts`: `versionCode = 3`, `versionName = "0.3.0"`.

**Step 5:** This does not compile until Task 8 adds the `ScreenIo` fields. Continue; build at the end of Task 8. Commit now: `wip(app): wire MIDI learn into the activity`.

---

### Task 7: Labels for targets and sources

**Files:**
- Modify: `ui/Panel.kt`, `app/src/test/kotlin/com/criapix/volt/ui/PanelTest.kt`
- Create: `ui/LearnUi.kt` (labels part only in this task), `app/src/test/kotlin/com/criapix/volt/ui/LearnLabelsTest.kt`

**Step 1: Failing tests**

Append to `PanelTest`:

```kotlin
  @Test fun groupOfFindsTheGroup() {
    assertEquals("FILTRO", Panel.groupOf("filter.cutoff")?.title)
    assertEquals("ENVELOPE DE AMPLITUDE", Panel.groupOf("aenv.attack")?.title)
    assertEquals(null, Panel.groupOf("no.such"))
  }
```

`LearnLabelsTest.kt`:

```kotlin
package com.criapix.volt.ui

import com.criapix.volt.midi.MidiAction
import com.criapix.volt.midi.Source
import com.criapix.volt.midi.Target
import org.junit.Assert.assertEquals
import org.junit.Test

class LearnLabelsTest {
  @Test fun targetsReadLikeTheScreen() {
    assertEquals("Cutoff · FILTRO", targetLabel(Target.Param("filter.cutoff")))
    assertEquals("Ataque · ENVELOPE DE AMPLITUDE", targetLabel(Target.Param("aenv.attack")))
    assertEquals("Próximo preset", targetLabel(Target.Action(MidiAction.NEXT)))
    assertEquals("no.such (parâmetro removido)", targetLabel(Target.Param("no.such")))
  }

  @Test fun badgesAreShort() {
    assertEquals("CC 74", shortLabel(Source.Cc(0, 74)))
    assertEquals("CC 74/3", shortLabel(Source.Cc(2, 74)))
    assertEquals("C4", shortLabel(Source.Note(0, 60)))
  }
}
```

**Step 2:** In `Panel`, add beside `tabOf`:

```kotlin
  fun groupOf(name: String): Group? = groups.values.flatten().firstOrNull { name in it.params }
```

**Step 3:** Create `ui/LearnUi.kt` with the labels (the composables come in Task 8):

```kotlin
package com.criapix.volt.ui

import com.criapix.volt.midi.Source
import com.criapix.volt.midi.Target
import com.criapix.volt.midi.noteName

/** "Cutoff · FILTRO", "Próximo preset". */
fun targetLabel(t: Target): String = when (t) {
  is Target.Action -> t.action.label
  is Target.Param -> {
    val group = Panel.groupOf(t.name)
    if (group == null) "${t.name} (parâmetro removido)" else "${ParamUi.of(t.name).label} · ${group.title}"
  }
}

/** Badge on a bound control: "CC 74", "CC 74/3" (channel 3), "C4". Channel 1 is not written. */
fun shortLabel(s: Source): String {
  val base = when (s) {
    is Source.Cc -> "CC ${s.num}"
    is Source.Note -> noteName(s.note)
  }
  return if (s.ch == 0) base else "$base/${s.ch + 1}"
}
```

**Step 4: Run** — passes. **Step 5: Commit** — `feat(app): labels for MIDI targets and sources`.

---

### Task 8: Learn mode on screen

**Files:**
- Modify: `ui/LearnUi.kt`, `ui/GroupPanel.kt`, `ui/TopBar.kt`, `ui/VoltScreen.kt`

What the user sees:
- The top bar's MIDI indicator becomes a button. Tapped, it turns orange and reads **APRENDER**; tapped again, Learn mode ends.
- In Learn mode every learnable control (knobs, selectors, toggles, and the top bar's ◀ ▶ Pânico) gets a blue outline, plus a small badge with its CC if it is bound. Tapping one **arms** it (thick orange outline) instead of operating it. Tabs still switch normally.
- A banner under the tab row says what to do. Idle: "Toque num controle da tela e depois mexa num controle do teclado". Armed: "Cutoff · FILTRO: mexa num controle do teclado…", with buttons **Esquecer** (removes that control's bindings, shown when it has any). Always: **Mapa…** (opens the map screen, Task 9) and **Concluir**.
- When a binding is learned, the banner shows "Cutoff · FILTRO ← CC 74 · canal 1" for 2 s.
- Outside Learn mode, when a detached controller is ignored, a hint at the bottom says "Cutoff: leve o controle até 1,2 kHz para assumir" and disappears 2 s after the last ignored message.

**Step 1: Learn composables** — append to `ui/LearnUi.kt`:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Learn mode as the controls see it; null when Learn mode is off. */
class LearnUi(val armed: Target?, val sources: Map<Target, List<Source>>, val onArm: (Target) -> kotlin.Unit)

val LocalLearn = compositionLocalOf<LearnUi?> { null }

/**
 * Wraps a learnable control. In Learn mode: outline, CC badge, and a transparent overlay that
 * takes every touch (so tapping arms the control instead of turning it). Otherwise: just the control.
 */
@Composable
fun LearnTarget(target: Target, modifier: Modifier = Modifier, content: @Composable () -> kotlin.Unit) {
  val learn = LocalLearn.current
  if (learn == null) {
    Box(modifier) { content() }
  } else {
    val armed = learn.armed == target
    val bound = learn.sources[target].orEmpty()
    Box(
      modifier.border(
        if (armed) 3.dp else 1.dp,
        if (armed) VoltColors.accent else VoltColors.accent2.copy(alpha = 0.6f),
        RoundedCornerShape(8.dp),
      ),
    ) {
      content()
      Box(Modifier.matchParentSize().pointerInput(target) { detectTapGestures { learn.onArm(target) } })
      if (bound.isNotEmpty()) {
        Text(
          shortLabel(bound.first()) + if (bound.size > 1) " +${bound.size - 1}" else "",
          fontSize = 9.sp, lineHeight = 11.sp, color = Color.Black,
          modifier = Modifier.align(Alignment.TopEnd).background(VoltColors.accent2, RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
        )
      }
    }
  }
}

@Composable
fun LearnBanner(
  armed: Target?, armedHasBindings: Boolean, confirmation: String?,
  onForget: () -> kotlin.Unit, onMap: () -> kotlin.Unit, onDone: () -> kotlin.Unit,
) {
  Row(
    Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp).background(VoltColors.raised, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val text = confirmation ?: when (armed) {
      null -> "Toque num controle da tela e depois mexa num controle do teclado"
      else -> "${targetLabel(armed)}: mexa num controle do teclado…"
    }
    Text(text, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
      color = if (confirmation != null) VoltColors.accent else VoltColors.text, modifier = Modifier.weight(1f))
    if (armed != null && armedHasBindings) TextButton(onClick = onForget) { Text("Esquecer", color = VoltColors.danger) }
    TextButton(onClick = onMap) { Text("Mapa…") }
    TextButton(onClick = onDone) { Text("Concluir") }
  }
}
```

(Merge the imports into the file's import block; `Target`/`Source` imports from Task 7 stay.)

**Step 2: `GroupPanel.kt`** — wrap each control. The three call sites become:

```kotlin
      toggles.forEach { LearnTarget(Target.Param(it.name)) { Toggle(it, ParamUi.of(it.name) as ToggleUi, state, onStep) } }
    // ...
    choices.forEach { LearnTarget(Target.Param(it.name)) { Selector(it, ParamUi.of(it.name) as ChoiceUi, state, onStep) } }
    // ...
              row.forEach { LearnTarget(Target.Param(it.name)) { Knob(it, ParamUi.of(it.name) as KnobUi, state, size, onStep) } }
```

Import `com.criapix.volt.midi.Target`. The selector's `Column` must fill the width as before; if wrapping shrinks it, pass `Modifier.fillMaxWidth()` to `LearnTarget` for choices.

**Step 3: `TopBar.kt`** — add two parameters, `learnActive: Boolean` and `onMidi: () -> kotlin.Unit`. Wrap the three action buttons, and replace the MIDI light and its text with a button:

```kotlin
    LearnTarget(Target.Action(MidiAction.PREV)) { TextButton(onClick = onPrev) { Text("◀", fontSize = 20.sp) } }
    // ... preset box unchanged ...
    LearnTarget(Target.Action(MidiAction.NEXT)) { TextButton(onClick = onNext) { Text("▶", fontSize = 20.sp) } }
    Button(onClick = onSave) { Text("Salvar") }
    LearnTarget(Target.Action(MidiAction.PANIC)) { TextButton(onClick = onPanic) { Text("Pânico", color = VoltColors.danger) } }
    Row(
      Modifier.height(34.dp)
        .background(if (learnActive) VoltColors.accent else VoltColors.raised, RoundedCornerShape(17.dp))
        .clickable(onClick = onMidi).padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(Modifier.size(10.dp).background(if (midiLit) VoltColors.accent2 else if (midiDevices > 0) VoltColors.track else VoltColors.panel, CircleShape))
      Text(
        when { learnActive -> "APRENDER"; midiDevices > 0 -> "MIDI"; else -> "sem MIDI" },
        fontSize = 12.sp, lineHeight = 15.sp, color = if (learnActive) Color.Black else VoltColors.text,
      )
    }
```

(The MIDI light changes from orange to blue so it stays visible on the orange Learn button. Import `Color`, `MidiAction`, `Target`.)

**Step 4: `VoltScreen.kt`**

Add to `ScreenIo`:

```kotlin
  val learn: MidiLearn,
  val pickup: StateFlow<MidiDispatch.Pickup?>,
  val midiSettings: MidiSettings,
```

In `VoltScreen`, collect the new state and build the `LearnUi`:

```kotlin
  val learnActive by io.learn.active.collectAsStateWithLifecycle()
  val armed by io.learn.armed.collectAsStateWithLifecycle()
  val bindings by io.learn.bindings.collectAsStateWithLifecycle()
  val learned by io.learn.learned.collectAsStateWithLifecycle()
  val pickup by io.pickup.collectAsStateWithLifecycle()
  var confirmation by remember { mutableStateOf<String?>(null) }
  var pickupHint by remember { mutableStateOf<String?>(null) }
  var midiMap by remember { mutableStateOf(false) }

  LaunchedEffect(learned?.seq) {
    val l = learned ?: return@LaunchedEffect
    confirmation = "${targetLabel(l.target)} ← ${describe(l.source)}"
    io.haptics.tick()
    delay(2000); confirmation = null
  }
  LaunchedEffect(pickup) {
    val pk = pickup ?: return@LaunchedEffect
    val prm = state.params.all[pk.param]
    pickupHint = "${ParamUi.of(prm.name).label}: leve o controle até ${ParamUi.format(prm, prm.toReal(pk.value))} para assumir"
    delay(2000); pickupHint = null
  }
  val learnUi = if (learnActive) LearnUi(armed, bindings.groupBy({ it.second.target }, { it.first })) { t -> io.learn.arm(t); io.haptics.tick() } else null
```

Wrap the screen's `Column` in `CompositionLocalProvider(LocalLearn provides learnUi) { … }`. Pass `learnActive = learnActive, onMidi = { io.learn.setActive(!learnActive) }` to `TopBar`. Between the tab row and the groups row:

```kotlin
        if (learnActive) LearnBanner(
          armed = armed, armedHasBindings = armed != null && bindings.any { it.second.target == armed }, confirmation = confirmation,
          onForget = { armed?.let { io.learn.clear(it) } }, onMap = { midiMap = true }, onDone = { io.learn.setActive(false) },
        )
```

Show the pickup hint next to the existing message box, only when not in Learn mode:

```kotlin
      if (!learnActive) pickupHint?.let { h ->
        Box(Modifier.fillMaxSize().padding(bottom = 64.dp), contentAlignment = Alignment.BottomCenter) {
          Text(h, color = VoltColors.text, fontSize = 13.sp, modifier = Modifier.background(VoltColors.raised, RoundedCornerShape(8.dp)).padding(10.dp))
        }
      }
```

and add `if (midiMap) MidiMapDialog(state, io, onClose = { midiMap = false })` beside the other dialogs (Task 9 writes it). Pass `onMidiMap = { midiMap = true }` to `SettingsDialog` (Task 9).

**Step 5: Commit** (does not build until Task 9) — `wip(app): Learn mode on screen`.

---

### Task 9: MIDI map screen and settings

**Files:**
- Create: `ui/MidiMapDialog.kt`
- Modify: `ui/Dialogs.kt`

The map screen (full screen, like the preset browser):
- **Left column:** Canal (chips: Omni, 1 … 16, scrolling horizontally), Captura suave (switch), Última mensagem (live), and the factory map as read-only text.
- **Right:** the list of learned bindings, one row each: `describe(source)` → `targetLabel(target)`, a "qualquer valor dispara" switch for actions and on/off parameters, and an Apagar button. Below the list: **Apagar todas** (with a confirmation dialog). An empty list says how to learn: "Nenhuma associação. Toque em MIDI na barra superior para entrar no modo aprender."

**Step 1: `ui/MidiMapDialog.kt`**

```kotlin
package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.Curve
import com.criapix.volt.midi.MidiDispatch
import com.criapix.volt.midi.Target
import com.criapix.volt.midi.describe
import com.criapix.volt.state.SynthState

@Composable
fun MidiMapDialog(state: SynthState, io: ScreenIo, onClose: () -> kotlin.Unit) {
  val bindings by io.learn.bindings.collectAsStateWithLifecycle()
  val last by io.lastMidi.collectAsStateWithLifecycle()
  var channel by remember { mutableIntStateOf(io.midiSettings.channel) }
  var takeover by remember { mutableStateOf(io.midiSettings.softTakeover) }
  var confirmClear by remember { mutableStateOf(false) }

  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxSize(), color = VoltColors.background) {
      Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Controle MIDI", fontSize = 18.sp)
          Text("Canal", fontSize = 12.sp, color = VoltColors.dim)
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(-1) + (0..15)).forEach { c ->
              val on = c == channel
              Text(
                if (c < 0) "Omni" else "${c + 1}", fontSize = 13.sp,
                color = if (on) Color.Black else VoltColors.text,
                modifier = Modifier.background(if (on) VoltColors.accent else VoltColors.raised, RoundedCornerShape(14.dp))
                  .clickable { channel = c; io.midiSettings.channel = c }.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Captura suave")
              Text("Depois de trocar de preset, o controle só assume o parâmetro quando passa pelo valor atual.", fontSize = 11.sp, lineHeight = 14.sp, color = VoltColors.dim)
            }
            Switch(takeover, { takeover = it; io.midiSettings.softTakeover = it })
          }
          Text("Última mensagem: ${last.ifEmpty { "nenhuma" }}", fontSize = 13.sp)
          Text("Mapa de fábrica", fontSize = 12.sp, color = VoltColors.dim)
          Text(factoryMapText(), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Column(Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Associações aprendidas (${bindings.size})", fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (bindings.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Apagar todas", color = VoltColors.danger) }
            TextButton(onClick = onClose) { Text("Fechar") }
          }
          if (bindings.isEmpty()) {
            Text("Nenhuma associação. Toque em MIDI na barra superior para entrar no modo aprender.", color = VoltColors.dim, modifier = Modifier.padding(16.dp))
          }
          LazyColumn(Modifier.fillMaxSize()) {
            items(bindings, key = { it.first.toString() }) { (source, binding) ->
              val t = binding.target
              val canTap = t is Target.Action || (t is Target.Param && state.params[t.name]?.let { it.curve == Curve.STEP && it.min == 0f && it.max == 1f } == true)
              Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).background(VoltColors.panel, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(describe(source), Modifier.width(150.dp), fontSize = 13.sp)
                Text("→  ${targetLabel(t)}", Modifier.weight(1f), fontSize = 13.sp)
                if (canTap) {
                  Text("qualquer valor dispara", fontSize = 11.sp, color = VoltColors.dim)
                  Switch(binding.tap, { io.learn.setTap(source, it) })
                }
                TextButton(onClick = { io.learn.unbind(source) }) { Text("Apagar", color = VoltColors.danger) }
              }
            }
          }
        }
      }
    }
  }
  if (confirmClear) AlertDialog(
    onDismissRequest = { confirmClear = false },
    title = { Text("Apagar todas as associações?") },
    text = { Text("O mapa de fábrica continua valendo.") },
    confirmButton = { TextButton(onClick = { io.learn.clearAll(); confirmClear = false }) { Text("Apagar", color = VoltColors.danger) } },
    dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
  )
}

private fun factoryMapText(): String {
  val params = MidiDispatch.FACTORY_PARAMS.entries.joinToString("\n") { (cc, name) -> "CC $cc → ${targetLabel(Target.Param(name))}" }
  return "Notas, pitch bend\nCC 1 → roda de modulação (LFO 1)\nCC 64 → sustain\n$params\nCC 120, 123 → pânico\nProgram Change → preset na posição"
}
```

**Step 2: `ui/Dialogs.kt`** — `SettingsDialog` gets a parameter `onMidiMap: () -> kotlin.Unit` and a button below "Todos os parâmetros":

```kotlin
        TextButton(onClick = { onClose(); onMidiMap() }) { Text("Controle MIDI (mapa, canal, captura suave)") }
```

**Step 3: Build and test**

```powershell
. .\tools\env.ps1
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\tools\test-engine.ps1
```

All pass; the APK builds.

**Step 4: Look at it on the emulator** (S25 Ultra size: `adb shell wm size 1440x3120; adb shell wm density 560`)

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.criapix.volt/.MainActivity
```

Screenshots (`adb exec-out screencap -p > build\<name>.png`, then open with the Read tool):
1. `learn-off.png`: the top bar shows the MIDI button ("sem MIDI" on the emulator, which has no keyboard).
2. Tap the MIDI button → `learn-on.png`: button orange "APRENDER"; banner with the idle text; every knob, selector and toggle outlined; ◀ ▶ Pânico outlined.
3. Tap the Cutoff knob on the FILTRO tab → `learn-armed.png`: thick orange outline on Cutoff; banner "Cutoff · FILTRO: mexa num controle do teclado…"; the knob's value **did not change**.
4. Tap ▶ while in Learn mode → it arms "Próximo preset", and the preset **did not change**.
5. Banner "Mapa…" → `midimap.png`: the map screen with Omni selected, Captura suave on, the factory map text, and the empty-list message.
6. Tap channel "2", close, reopen → "2" is still selected. Set it back to Omni.
7. "Concluir" → outlines and banner gone; knobs turn normally again (drag one, the value changes).

`adb logcat -d -s AndroidRuntime volt` has no crash. Note in NOTES.md that binding itself could not be exercised: the emulator has no MIDI input.

**Step 5: Commit** — `feat(app): MIDI map screen, channel and soft takeover settings`.

---

### Task 10: Notes, device checklist, README

**Files:**
- Modify: `docs/NOTES.md`, `docs/DEVICE-TEST.md`, `README.md`

**Step 1: `docs/NOTES.md`** — update `## Estado` (plan 3 done; still missing: effects, arpeggiator, on-screen keyboard, the tuned full bank), with the test counts you saw. Add:

```markdown
## Plano 3 (MIDI Learn) — 2026-11-12
- Botão MIDI na barra → modo aprender: tocar num controle da tela e mexer num controle do teclado. Alvos: qualquer parâmetro, preset anterior/próximo, pânico. Notas só para ações. CC 0, 32 e 120–127 não podem ser aprendidos.
- Precedência de um CC: aprender → associação aprendida → mapa de fábrica. Mapa de fábrica ampliado com os controladores de som do General MIDI: CC 72 → release, 73 → ataque, 75 → decay (envelope de amplitude), além de 71 → ressonância e 74 → cutoff. CC 91/92/93 ficam livres até os efeitos (plano 4).
- Captura suave: o controle só move o parâmetro enquanto o parâmetro ainda tem o último valor que ele mandou; carregar preset, mexer na tela ou outro controle o desligam, e ele volta a valer quando chega a 1,5/127 do valor ou cruza o valor. Logo depois de aprender, o controle já assume. Desligável em ⋮ → Controle MIDI.
- Ações disparam na subida para ≥ 64 (ou nota ligada); "qualquer valor dispara" (com 150 ms de antirrepique) para botões que mandam 127 e 0 em toques alternados; o mesmo liga/desliga parâmetros liga/desliga.
- Program Change n → preset na posição n+1 da biblioteca. Bank Select ignorado: só os 128 primeiros presets.
- Mapa salvo nas preferências (`midiMap`), por canal. Canal (Omni/1–16) e captura suave também.
- Verificado por: <N> testes Kotlin (MidiMap, SoftTakeover, MidiLearn, roteamento com SynthState real, Program Change) e capturas no emulador do modo aprender (armar, sem mexer no valor; mapa; canal). **Associar de fato, a captura suave e o Program Change não foram exercitados num aparelho: o emulador não tem entrada MIDI.**
```

**Step 2: `docs/DEVICE-TEST.md`** — add above plan 2's section:

```markdown
# Teste no aparelho (plano 3: MIDI Learn)

Com o CK61 ligado por USB. Depois do teste, rodar `tools\pull-log.ps1` (o log registra cada associação aprendida e cada ação).

| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 1 | Na aba FILTRO, sem aprender nada, girar o knob do CK61 que manda CC 74 | O Cutoff na tela gira junto (depois de passar pelo valor atual, pela captura suave) | |
| 2 | Girar os knobs que mandam CC 73 e CC 72 | Ataque e release do envelope de amplitude mudam (aba ENV) | |
| 3 | Mexer em cada controle do painel do CK61 e ler ⋮ → Controle MIDI → "Última mensagem" | **Anotar em NOTES.md o nome de cada controle físico e o CC que ele manda** | |
| 4 | Tocar em MIDI na barra, tocar no knob Drive (aba OSC) e mexer num controle do CK61 | Aparece "Drive · MIXER ← CC xx"; o Drive passa a seguir esse controle na hora | |
| 5 | Aprender um botão do CK61 para ▶ (próximo preset) | Cada toque no botão avança um preset | |
| 6 | Se no passo 5 só um toque a cada dois funcionar | Em Controle MIDI, ligar "qualquer valor dispara" nessa associação; agora todo toque avança | |
| 7 | Aprender uma tecla para "Pânico" | A tecla para de tocar nota e corta o som | |
| 8 | Captura suave: com o Cutoff associado, trocar de preset e girar o knob do CK61 longe do valor | O som não pula; aparece "Cutoff: leve o controle até …"; ao passar pelo valor, assume | |
| 9 | Desligar a captura suave e repetir | O valor pula para a posição do knob | |
| 10 | Program Change pelo CK61 (se o teclado manda) | O preset da posição correspondente carrega | |
| 11 | Canal: escolher 2 em Controle MIDI | O CK61 (canal 1) para de tocar; voltar para Omni | |
| 12 | Fechar e abrir o app | As associações continuam lá | |
| 13 | Apagar uma associação e depois "Apagar todas" | Somem da lista; o mapa de fábrica continua funcionando | |
```

**Step 3: `README.md`** — in the `## Teclado MIDI` section: the factory map now includes CC 72/73/75; one paragraph on Learn mode (MIDI button → tap a control → move a keyboard control), soft takeover, Program Change, and where the map screen is (⋮ → Controle MIDI, or "Mapa…" in Learn mode). Update `## Estado`.

**Step 4: Full verification**

```powershell
.\tools\test-engine.ps1
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
git status
```

All pass; no untracked build output.

**Step 5: Commit** — `docs: state, device checklist and README for plan 3`.

**Step 6: Finish the branch** with superpowers:finishing-a-development-branch (merge locally into `main`, as plans 1 and 2).

**Step 7: Final report to the user** (Portuguese): what was built; the test counts you saw pass; what the screenshots showed; the decisions of section 0 (especially the GM factory controllers 72/73/75 and how actions trigger); that nothing could be bound for real on the emulator; and that the next step is theirs: `docs/DEVICE-TEST.md` (plan 3), above all step 3. The names of the CK61's controls and their CCs decide the factory defaults for plan 4's effects (CC 91/92/93).

---

## 99. What comes after this plan

| Plan | Design sections | Contents | Depends on from this plan |
|---|---|---|---|
| 4 — Effects, arpeggiator, keyboard | 4.3, 4.4, 5.4 | Six effects, arpeggiator, ARP and FX tabs, on-screen keyboard drawer; factory CC 91 → reverb, 93 → chorus, 92 → tremolo/phaser if the device test confirms what the CK61 sends | Effect on/off and arpeggiator hold become `ToggleUi` parameters, so `Target.Param` and "qualquer valor dispara" already cover them; `MidiDispatch.FACTORY_PARAMS` gets the new entries |
| 5 — Factory bank and polish | 7, 8 | ~64 factory presets tuned by ear, a render test per preset, polyphony reduction under overload (plan 2 measured a 36 % peak with 16 voices), app icon, release build | Program Change order = library order: renumbering factory files changes which program loads which preset, so plan 5 must keep the order stable or say so |
