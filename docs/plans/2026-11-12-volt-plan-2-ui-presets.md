# Volt Plan 2 — Touch UI and Presets Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the debug screen with the real touch interface (top bar, tabs OSC · FILTRO · ENV · MOD, knobs, selectors, live filter and envelope drawings) and add presets (file format, factory starter set, user presets, browser, save, import/export, session restore), with click-free preset switching in the engine.

**Architecture:** One Kotlin `SynthState` holds the normalized value of every parameter as a `StateFlow`; the screen, the MIDI layer and the preset code all change parameters through it, so the knobs always show what the engine plays. A plain-Kotlin `AppModel` (no Android types, unit-tested on the JVM) owns the current preset, the `PresetLibrary` and the session file. The engine gains one atomic "load patch" call that fades sounding voices out for ~5 ms, applies every value, and fades back in.

**Tech Stack:** C++20 engine (existing), Kotlin 2.3, Jetpack Compose (Material 3, `Canvas`), `kotlinx.serialization` JSON, JUnit 4 with `TemporaryFolder`.

---

## 0. Read this first

**Design document:** `docs/plans/2026-11-11-volt-design.md` (Portuguese) — sections 5 (screen) and 7 (presets) are what this plan builds. **Plan 1** (`docs/plans/2026-11-11-volt-plan-1-engine.md`) built the engine and the debug app; read its section 0 for the machine facts, build commands and rules. They all still hold. The short version:

- Repository: `C:\Users\criap\OneDrive\Documentos\Github\volt-synth`, branch `main`. Work on a branch `feature/ui-presets` (plan 1 used `feature/engine-plan-1` and merged it). Do not push.
- Before building on the PC: `. .\tools\env.ps1`. Engine tests: `.\tools\test-engine.ps1 [filter]`. App: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.
- `docs/NOTES.md` has what plan 1 learned: read it whole before starting. Two traps from it: PowerShell 5.1 `Set-Content -Encoding utf8` writes a BOM that Gradle rejects; this PC's locale is pt-BR, so Kotlin tests that format numbers must pass an explicit `Locale`.
- Audio-thread rules (no allocation, no locks, no I/O, no JNI in `Engine::render`) still hold; `test_alloc.cpp` enforces them.
- Code in this plan was written without a compiler. Treat it as a careful draft: fix what does not compile; when a test fails, look for the cause in the code first, and change a test's expectation only with a written reason in `docs/NOTES.md`.
- Language: code, comments, commits in English; on-screen text in Brazilian Portuguese; `NOTES.md` / `DEVICE-TEST.md` in Portuguese.
- End every commit message with the co-author line your session's instructions give you.

**Honesty rule:** the emulator cannot be heard and a screenshot is not a hand. Report what you saw on screenshots and in tests; touch feel, sound of presets and anything on the S25 Ultra are the user's to judge (Task 17's checklist).

### What plan 1 left that this plan uses

| Thing | Where | Notes |
|---|---|---|
| Parameter table | `engine/src/params.def` | 67 parameters; **names are permanent** (presets use them). Kotlin gets the table at runtime from `Native.paramTable()` and parses it with `Params.parse` (`app/src/main/kotlin/com/criapix/volt/Params.kt`). |
| JNI surface | `app/src/main/cpp/jni.cpp`, `Native.kt` | `setParam(id, normalized)`, notes, bend, wheel, sustain, `allNotesOff`, `audioInfo`, `peak` |
| MIDI | `midi/MidiInput.kt`, `MidiParser.kt`, `MidiDispatch.kt` | `MidiDispatch(synth: Synth, params, log)` applies the factory CC map by calling `synth.setParam` |
| On-device log | `AppLog.kt`, `tools/pull-log.ps1` | Keep logging user actions; the device results in NOTES.md came from this log |
| Debug screen | `DebugScreen.kt` | Replaced by this plan; its `formatInfo` moves to the settings dialog |

### Device facts that shape this plan (from `docs/NOTES.md`)

- S25 Ultra landscape is about **891 × 411 dp**. Height is the scarce dimension: after a 52 dp top bar and a 40 dp tab row, a tab has about 310 dp. Knob sizes are therefore computed from the space (Task 12), not fixed.
- The CK61 sends CC 71, 72, 73, 74, 91, 92, 93, 13, 14, 15, 9. Only 71 and 74 are mapped (resonance, cutoff). MIDI Learn is plan 3; this plan only makes the screen follow whatever the MIDI layer changes.

### Decisions taken while writing this plan (report them to the user at the end)

1. **Sync is now Prophet-style.** Plan 1 synced osc 2 to osc 1 while poly-mod modulates osc 1. Then the classic "sync lead" is impossible: sweeping the master's pitch only changes the note. The Prophet syncs the oscillator that poly-mod sweeps. Task 2 makes **osc 1 the synced oscillator, following osc 2**, and renames the parameter `osc2.sync` → `osc1.sync`. No preset exists yet, so the rename costs nothing. After today it would.
2. **Filter gain compensation.** Ladder and Prophet are ~6 dB quieter than the Roland models (NOTES.md). Task 1 compensates it, so presets and the model selector do not jump in volume.
3. **Scope cuts, as the design's build order intends:** no ARP and FX tabs (plan 4), no MIDI button or Learn mode (plan 3), no keyboard drawer (plan 4), no Program Change (plan 3). The factory bank here is a **starter set of 10** presets so the browser has content; the full ~64 is plan 5.
4. **Knob minimum size.** The design asks for ≥ 56 dp. On the S25 Ultra the layout reaches that. On a narrower 16:9 phone, the tab with the most controls (MOD) cannot fit 56 dp knobs in 411 dp of height, so a group that does not fit becomes vertically scrollable instead of shrinking knobs below 52 dp.

### Layout at the end of this plan (new and changed files only)

```
engine/src/   event_queue.h  engine.h  engine.cpp  filter.cpp  voice.h  voice.cpp  params.def
engine/tests/ rig.h  test_patch.cpp  test_engine.cpp  test_filter.cpp  test_alloc.cpp
app/build.gradle.kts
app/src/main/cpp/jni.cpp
app/src/main/assets/presets/factory/*.json                       (10 files)
app/src/main/kotlin/com/criapix/volt/
  MainActivity.kt  Native.kt  Prefs.kt  Haptics.kt              (DebugScreen.kt deleted)
  midi/MidiDispatch.kt
  state/SynthState.kt
  preset/PresetCodec.kt  preset/PresetLibrary.kt  preset/Session.kt  preset/AppModel.kt  preset/AssetFactory.kt
  ui/Theme.kt  ui/ParamUi.kt  ui/Panel.kt  ui/KnobMath.kt  ui/Knob.kt  ui/Selector.kt
  ui/FilterCurve.kt  ui/EnvelopeGeometry.kt  ui/Drawings.kt  ui/GroupPanel.kt
  ui/TopBar.kt  ui/VoltScreen.kt  ui/BrowserFilter.kt  ui/PresetBrowser.kt  ui/Dialogs.kt  ui/Diagnostics.kt
app/src/test/kotlin/com/criapix/volt/
  TestParams.kt  state/SynthStateTest.kt  preset/PresetCodecTest.kt  preset/PresetLibraryTest.kt
  preset/AppModelTest.kt  preset/FactoryPresetsTest.kt  ui/ParamUiTest.kt  ui/PanelTest.kt
  ui/KnobMathTest.kt  ui/FilterCurveTest.kt  ui/EnvelopeGeometryTest.kt  ui/BrowserFilterTest.kt
  midi/MidiDispatchTest.kt
```

---

### Task 1: Filter gain compensation

**Files:**
- Modify: `engine/src/filter.cpp`, `engine/tests/test_filter.cpp`, `engine/tests/test_engine.cpp:43`

**Step 1: Failing test** — append to `engine/tests/test_filter.cpp`:

```cpp
TEST(filter_every_model_has_unity_passband_gain) {
  // Cutoff fully open, low resonance, a 440 Hz sine well inside the passband.
  for (int model = 0; model < kNumFilterModels; ++model) {
    double out = sineThrough(model, 20000.0f, 0.1f, 440.0f);
    CHECK_NEAR(out / kIn, 1.0, 0.19);  // within about +-1.5 dB
  }
}
```

**Step 2: Run** `.\tools\test-engine.ps1 filter_every_model_has` — expected: FAIL for models 0 and 3 (ratio ≈ 0.51).

**Step 3: Implement** in `engine/src/filter.cpp`:
- Add `float gain = 1.0f;` to `struct QuadFilter::Impl`.
- In `setModel`, after choosing the model: `static const float kGain[kNumFilterModels] = {1.96f, 1.0f, 1.0f, 1.96f};  // measured passband gain 0.51 (NOTES.md)` and `impl_->gain = kGain[model];`.
- In `process`:

```cpp
void QuadFilter::process(float (*io)[4]) {
  if (!impl_->fn) return;
  const auto g = SIMD_MM(set1_ps)(impl_->gain);
  for (int s = 0; s < kBlock; ++s) {
    auto in = SIMD_MM(load_ps)(io[s]);
    SIMD_MM(store_ps)(io[s], SIMD_MM(mul_ps)(impl_->fn(&impl_->state, in), g));
  }
}
```

**Step 4:** In `engine/tests/test_engine.cpp`, `engine_plays_a_note_at_concert_pitch`: restore `CHECK(probe::rms(x) > 0.05);` and delete the comment about the Ladder's 0.5 gain.

**Step 5: Run** `.\tools\test-engine.ps1` — all pass. `filter_stays_finite_at_the_extremes` allows peaks up to 20, so doubling the Ladder's output with full resonance must still pass. If it does not, print the peak and write it in NOTES.md before touching the threshold.

**Step 6: Commit** — `feat(engine): compensate the Ladder and Prophet passband gain`.

---

### Task 2: Prophet-style sync — osc 1 follows osc 2

**Files:**
- Modify: `engine/src/params.def`, `engine/src/voice.h`, `engine/src/voice.cpp`, `engine/tests/test_engine.cpp`
- Modify: `docs/plans/2026-11-11-volt-design.md` (one line, Step 5)

**Step 1: Failing test** — append to `engine/tests/test_engine.cpp`:

```cpp
TEST(engine_sync_locks_osc1_to_osc2s_period) {
  // osc 2 (silent) plays the note, 110 Hz; osc 1 is a fifth above, 164.8 Hz.
  auto energy = [](bool sync) {
    Rig r;
    r.set(kVoiceDrift, 0.0f);
    r.set(kFilterModel, 1);
    r.set(kOsc1Semi, 7);
    r.set(kOsc2Level, 0.0f);
    r.set(kOsc1Sync, sync ? 1.0f : 0.0f);
    r.play(0.01f);
    r.on(45);
    r.play(0.1f);
    auto x = r.play(1.0f);
    return std::make_pair(probe::tone(x, 110.0, 48000.0), probe::tone(x, 164.81, 48000.0));
  };
  auto free = energy(false), locked = energy(true);
  CHECK(free.second > 5.0 * free.first);     // unsynced: osc 1 sounds at its own pitch
  CHECK(locked.first > 5.0 * locked.second);  // synced: osc 1 restarts every osc 2 cycle
}
```

**Step 2: Run** — build error: `kOsc1Sync` undeclared.

**Step 3: `engine/src/params.def`** — replace the sync line with:

```cpp
P(kOsc1Sync,   "osc1.sync",   0, 1, 0, Step)      // osc 1 hard-synced to osc 2 (Prophet: the synced osc is the one poly-mod sweeps)
```

**Step 4: `engine/src/voice.cpp`** — osc 2 now runs first each sample, because it is the master and the FM/PWM modulator.
- Change `const bool sync = p[kOsc2Sync] > 0.5f;` to `const bool sync = p[kOsc1Sync] > 0.5f;`.
- Replace the first five lines of the audio-rate loop body (from `const float inc0 = …` to `osc2Prev_ = b;`) with:

```cpp
    const float b = osc_[1].tick(inc[1], wave[1], pw[1]);
    float inc0 = inc[0] * (1.0f + fm * b);
    if (inc0 < 0.0f) inc0 = 0.0f;
    if (sync && osc_[1].wrapped) osc_[0].syncTo(osc_[1].wrapFrac, inc0);
    const float a = osc_[0].tick(inc0, wave[0], clampf(pw[0] + pwm * b, 0.05f, 0.95f));
```

- Delete `osc2Prev_` from `voice.h` and from the reset line in `Voice::start`. FM and PWM now use osc 2's current sample, not the previous one.
- `grep -rn "Osc2Sync\|osc2.sync" engine app` must return nothing.

**Step 5: Design document** — in `docs/plans/2026-11-11-volt-design.md`, section 4.1, replace "Sync rígido do osc 2 pelo osc 1." with "Sync rígido do osc 1 pelo osc 2 (como no Prophet: o oscilador sincronizado é o mesmo que o poly-mod varre)."

**Step 6: Run** `.\tools\test-engine.ps1` — all pass, including the new test and `engine_stays_finite_and_bounded_under_random_abuse`.

**Step 7: Commit** — `feat(engine): Prophet-style sync, osc 1 follows osc 2`.

---

### Task 3: Atomic, click-free patch loading

A preset change must reach the engine as one unit, not as 67 separate `SetParam` events that could straddle audio callbacks. The new `postPatch` queues all values plus a commit marker under one producer lock, or queues nothing. On commit, if voices are sounding, the output fades to silence over 256 samples (~5 ms), every value snaps into place (no smoothing; nothing is audible), and the output fades back in. With no voices sounding, the values apply at once.

**Files:**
- Create: `engine/tests/rig.h`, `engine/tests/test_patch.cpp`
- Modify: `engine/src/event_queue.h`, `engine/src/engine.h`, `engine/src/engine.cpp`, `engine/tests/test_engine.cpp`, `engine/tests/test_alloc.cpp`, `engine/CMakeLists.txt`

**Step 1: Share the test rig.** Move `struct Rig` out of the anonymous namespace in `test_engine.cpp` into a new header and include it from there:

```cpp
// engine/tests/rig.h
#pragma once
#include <vector>
#include "engine.h"

namespace volt {
// Drives an Engine at 48 kHz from tests.
struct Rig {
  Engine e;
  Rig() { e.setSampleRate(48000.0f); }
  void set(int id, float real) { e.post({Event::SetParam, id, norm(id, real)}); }
  void on(int note, float vel = 0.8f) { e.post({Event::NoteOn, note, vel}); }
  void off(int note) { e.post({Event::NoteOff, note, 0}); }
  // Renders `seconds` and returns the left channel.
  std::vector<float> play(float seconds, int chunk = 96) {
    std::vector<float> left, buf(2 * chunk);
    int frames = (int)(seconds * 48000.0f);
    while (frames > 0) {
      int n = frames < chunk ? frames : chunk;
      e.render(buf.data(), n);
      for (int i = 0; i < n; ++i) left.push_back(buf[2 * i]);
      frames -= n;
    }
    return left;
  }
};
// Every parameter at its default, normalized: the starting point of a test patch.
inline std::vector<float> defaultPatch() {
  std::vector<float> v(kNumParams);
  for (int i = 0; i < kNumParams; ++i) v[i] = norm(i, kParams[i].def);
  return v;
}
}  // namespace volt
```

Run `.\tools\test-engine.ps1` to confirm the refactor changed nothing.

**Step 2: Failing tests — `engine/tests/test_patch.cpp`**

```cpp
#include "dsp_probe.h"
#include "rig.h"
#include "test.h"

using namespace volt;

namespace {
double maxStep(const std::vector<float>& x) {
  double m = 0;
  for (size_t i = 1; i < x.size(); ++i) m = std::fmax(m, std::fabs((double)x[i] - x[i - 1]));
  return m;
}
}  // namespace

TEST(patch_applies_at_once_when_nothing_sounds) {
  Rig r;
  auto p = defaultPatch();
  p[kFilterCutoff] = norm(kFilterCutoff, 500.0f);
  p[kOsc1Wave] = norm(kOsc1Wave, 2);
  CHECK(r.e.postPatch(p.data(), kNumParams));
  r.play(16.0f / 48000.0f);  // one block
  CHECK_NEAR(r.e.value(kFilterCutoff), 500.0f, 0.01);
  CHECK_NEAR(r.e.value(kOsc1Wave), 2.0f, 0);
}

TEST(patch_waits_for_the_fade_while_a_note_sounds) {
  Rig r;
  r.on(57);
  r.play(0.1f);
  auto p = defaultPatch();
  p[kFilterCutoff] = norm(kFilterCutoff, 500.0f);
  CHECK(r.e.postPatch(p.data(), kNumParams));
  r.play(0.002f);  // inside the 5 ms fade-out
  CHECK(r.e.value(kFilterCutoff) > 10000.0f);
  r.play(0.010f);
  CHECK_NEAR(r.e.value(kFilterCutoff), 500.0f, 0.01);  // snapped, not smoothed
  CHECK(r.e.activeVoices() == 1);                       // the note survives the switch
}

TEST(patch_switch_does_not_click) {
  // Sine through an open filter; the patch changes the filter model, which resets the filter state.
  // Without the fade, that reset is an audible step.
  Rig r;
  r.set(kVoiceDrift, 0.0f);
  r.set(kOsc1Wave, 3);
  r.set(kFilterModel, 1);
  r.play(0.01f);
  r.on(57);
  r.play(0.2f);
  const double steady = maxStep(r.play(0.1f));
  auto p = defaultPatch();
  p[kVoiceDrift] = 0.0f;
  p[kOsc1Wave] = norm(kOsc1Wave, 3);
  p[kFilterModel] = norm(kFilterModel, 2);
  CHECK(r.e.postPatch(p.data(), kNumParams));
  const double during = maxStep(r.play(0.1f));
  CHECK(during < 1.5 * steady);
  CHECK(during > 0.0);
}

TEST(patch_without_commit_never_applies) {
  Rig r;
  r.e.post({Event::PatchParam, kFilterCutoff, norm(kFilterCutoff, 500.0f)});
  r.play(0.05f);
  CHECK(r.e.value(kFilterCutoff) > 10000.0f);
}

TEST(patch_is_refused_whole_when_the_queue_is_too_full) {
  Rig r;
  while (r.e.post({Event::SetParam, kMixNoise, 0.0f})) {}  // fill the queue
  auto p = defaultPatch();
  p[kFilterCutoff] = norm(kFilterCutoff, 500.0f);
  CHECK(!r.e.postPatch(p.data(), kNumParams));
  r.play(0.05f);
  CHECK(r.e.value(kFilterCutoff) > 10000.0f);  // nothing half-applied
  CHECK(r.e.postPatch(p.data(), kNumParams));  // room again after the drain
}

TEST(patch_a_later_set_param_wins) {
  Rig r;
  r.on(57);
  r.play(0.05f);
  auto p = defaultPatch();
  p[kFilterCutoff] = norm(kFilterCutoff, 500.0f);
  CHECK(r.e.postPatch(p.data(), kNumParams));
  r.set(kFilterCutoff, 3000.0f);  // the user turns the knob right after choosing the preset
  r.play(0.1f);
  CHECK_NEAR(r.e.value(kFilterCutoff), 3000.0f, 1.0);
}
```

Add `tests/test_patch.cpp` to `volt_tests` in `engine/CMakeLists.txt`. Run — build errors (`postPatch`, `value`, `PatchParam` missing).

**Step 3: `engine/src/event_queue.h`**
- Extend the enum: `enum Type : int32_t { NoteOn, NoteOff, SetParam, PitchBend, ModWheel, Sustain, AllNotesOff, PatchParam, PatchCommit };`
- Add to `EventQueue`, public:

```cpp
  // Producer side: how many more events push() would accept right now.
  int freeSpace() const {
    uint32_t w = write_.load(std::memory_order_relaxed);
    uint32_t r = read_.load(std::memory_order_acquire);
    return (int)((r - w - 1) & (kCapacity - 1));
  }
```

**Step 4: `engine/src/engine.h`** — add, public:

```cpp
  // Any non-audio thread. Loads a whole patch (normalized values for ids 0..count-1) as one unit:
  // either every value is queued or none is (returns false). Sounding voices fade out, the patch
  // snaps into place, and they fade back in. A later post() of the same parameter wins.
  bool postPatch(const float* normalized, int count);

  // Tests only, not thread-safe: the smoothed real value the voices read.
  float value(int id) const { return value_[id]; }
```

and, private:

```cpp
  void applyPending();
  float pending_[kNumParams];
  bool pendingSet_[kNumParams] = {};
  enum class Fade { None, Out, In };
  Fade fade_ = Fade::None;
  float fadeGain_ = 1.0f;
```

**Step 5: `engine/src/engine.cpp`**

Add to the anonymous namespace: `const int kFadeSamples = 256;  // ~5 ms at 48 kHz`.

```cpp
bool Engine::postPatch(const float* n, int count) {
  if (count > kNumParams) count = kNumParams;
  std::lock_guard<std::mutex> lock(postMutex_);
  if (queue_.freeSpace() < count + 1) return false;
  for (int i = 0; i < count; ++i) queue_.push({Event::PatchParam, i, n[i]});
  queue_.push({Event::PatchCommit, 0, 0.0f});
  return true;
}

void Engine::applyPending() {
  for (int i = 0; i < kNumParams; ++i)
    if (pendingSet_[i]) { target_[i] = value_[i] = pending_[i]; pendingSet_[i] = false; }
}
```

In `handle`:
- In `case Event::SetParam`, inside the `if`, add `pendingSet_[e.a] = false;` (an explicit change after a patch overrides the patch's value for that parameter).
- New cases:

```cpp
    case Event::PatchParam:
      if (e.a >= 0 && e.a < kNumParams) { pending_[e.a] = denorm(e.a, e.b); pendingSet_[e.a] = true; }
      break;
    case Event::PatchCommit: {
      bool sounding = false;
      for (auto& v : voices_) if (v.active) { sounding = true; break; }
      if (sounding) fade_ = Fade::Out;
      else { applyPending(); fade_ = Fade::None; fadeGain_ = 1.0f; }
      break;
    }
```

At the very top of `renderBlock`, before the smoothing loop:

```cpp
  if (fade_ == Fade::Out && fadeGain_ <= 0.0f) { applyPending(); fade_ = Fade::In; }
```

The filter-model check that follows the smoothing loop then sees the new model and resets the filters while the output is silent.

In the master loop at the end of `renderBlock`, apply the fade after the limiter:

```cpp
  const float fadeStep = 1.0f / (float)kFadeSamples;
  float pk = 0.0f;
  for (int s = 0; s < kBlock; ++s) {
    if (fade_ == Fade::Out) { fadeGain_ -= fadeStep; if (fadeGain_ < 0.0f) fadeGain_ = 0.0f; }
    else if (fade_ == Fade::In) { fadeGain_ += fadeStep; if (fadeGain_ >= 1.0f) { fadeGain_ = 1.0f; fade_ = Fade::None; } }
    float l = std::tanh(blockL_[s] * vol) * fadeGain_, r = std::tanh(blockR_[s] * vol) * fadeGain_;
    blockL_[s] = l; blockR_[s] = r;
    const float a = std::fabs(l) > std::fabs(r) ? std::fabs(l) : std::fabs(r);
    if (a > pk) pk = a;
  }
```

(Keep the existing `vol` line and the stores to `peak_` / `activeVoices_` after the loop.)

**Step 6: Allocation test** — in `test_alloc.cpp`, inside the loop, every 50th round also post a patch:

```cpp
    if (round % 50 == 0) {
      static float patch[kNumParams];
      for (int i = 0; i < kNumParams; ++i) patch[i] = rng.unipolar();
      e.postPatch(patch, kNumParams);
    }
```

**Step 7: Run** `.\tools\test-engine.ps1` — every test passes. If `patch_switch_does_not_click` fails, print `steady` and `during`. If `during` is huge, the fade is not applied (check the order in `renderBlock`). If it is just above 1.5×, look at whether the fade-in (5 ms ramp on a 220 Hz sine) is the cause before changing anything.

**Step 8: Commit** — `feat(engine): atomic patch loading with a click-free fade`.

---

### Task 4: JNI and the Synth interface

**Files:**
- Modify: `app/src/main/cpp/jni.cpp`, `app/src/main/kotlin/com/criapix/volt/Native.kt`, `midi/MidiDispatch.kt`, `MainActivity.kt` (the `NativeSynth` object only)

**Step 1: `jni.cpp`** — add:

```cpp
JNI(jboolean, loadPatch)(JNIEnv* env, jobject, jfloatArray values) {
  const jsize n = env->GetArrayLength(values);
  float buf[volt::kNumParams];
  const jsize count = n < volt::kNumParams ? n : volt::kNumParams;
  env->GetFloatArrayRegion(values, 0, count, buf);
  return g_audio.engine().postPatch(buf, count) ? JNI_TRUE : JNI_FALSE;
}
JNI(jint, activeVoices)(JNIEnv*, jobject) { return g_audio.engine().activeVoices(); }
```

**Step 2: `Native.kt`** — add:

```kotlin
  /** Every parameter at once, normalized, indexed by id. False when the engine queue is full (nothing applied). */
  external fun loadPatch(values: FloatArray): Boolean
  external fun activeVoices(): Int
```

**Step 3: `midi/MidiDispatch.kt`** — give `Synth` a default patch method, so existing fakes keep compiling:

```kotlin
  /** All parameters at once (normalized, indexed by id). Default: one setParam per value. */
  fun loadPatch(values: FloatArray): Boolean {
    values.forEachIndexed { id, v -> setParam(id, v) }
    return true
  }
```

**Step 4:** In `MainActivity.kt`, add to `NativeSynth`: `override fun loadPatch(values: FloatArray) = Native.loadPatch(values)`.

**Step 5: Build** — `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` succeeds; existing tests pass.

**Step 6: Commit** — `feat(app): patch loading and voice count over JNI`.

---

### Task 5: Real parameter table in JVM tests, JSON dependency

JVM tests cannot call `Native`. The helper below builds `Params` from `engine/src/params.def` itself, so the Kotlin tests always use the real table and notice when it changes.

**Files:**
- Create: `app/src/test/kotlin/com/criapix/volt/TestParams.kt`
- Modify: `app/build.gradle.kts`

**Step 1: `app/build.gradle.kts`** — add to `dependencies`:

```kotlin
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```

(1.7.3 is what the FM1 Droid builds with on this machine with the same Kotlin. The serialization plugin is already applied.)

**Step 2: `TestParams.kt`**

```kotlin
package com.criapix.volt

import java.io.File

/** The engine's parameter table, read from engine/src/params.def (Gradle runs unit tests from app/). */
object TestParams {
  private val line = Regex("""^\s*P\(\s*\w+\s*,\s*"([^"]+)"\s*,\s*([-0-9.]+)f?\s*,\s*([-0-9.]+)f?\s*,\s*([-0-9.]+)f?\s*,\s*(\w+)\s*\)""")

  val params: Params by lazy {
    val def = File("../engine/src/params.def")
    check(def.exists()) { "params.def not found from ${File(".").absolutePath}" }
    val table = def.readLines().mapNotNull { line.find(it) }.joinToString("\n") { m ->
      val (name, min, max, d, curve) = m.destructured
      "$name|$min|$max|$d|${curve.lowercase()}"
    }
    Params.parse(table)
  }
}
```

**Step 3: Sanity test** — append to `app/src/test/kotlin/com/criapix/volt/ParamsTest.kt`:

```kotlin
  @Test fun theRealTableIsReadFromTheEngineSource() {
    val p = TestParams.params
    assertEquals(67, p.all.size)
    assertEquals(Curve.EXP, p["filter.cutoff"]?.curve)
    assertEquals(null, p["osc2.sync"])
    assertEquals(Curve.STEP, p["osc1.sync"]?.curve)
  }
```

If the count is not 67, count the `P(` lines in `params.def` and fix the regex, not the number.

**Step 4: Run** `.\gradlew.bat :app:testDebugUnitTest` — passes.

**Step 5: Commit** — `test(app): JVM tests read the real parameter table`.

---

### Task 6: SynthState — one source of truth for parameter values

**Files:**
- Create: `app/src/main/kotlin/com/criapix/volt/state/SynthState.kt`, `app/src/test/kotlin/com/criapix/volt/state/SynthStateTest.kt`
- Modify: `midi/MidiDispatch.kt`, `app/src/test/kotlin/com/criapix/volt/midi/MidiDispatchTest.kt`

**Step 1: Failing test — `SynthStateTest.kt`**

```kotlin
package com.criapix.volt.state

import com.criapix.volt.TestParams
import com.criapix.volt.midi.Synth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthStateTest {
  private class Fake : Synth {
    val params = ArrayList<Pair<Int, Float>>()
    var patches = 0
    var acceptPatch = true
    var notes = 0
    override fun noteOn(note: Int, velocity: Float) { notes++ }
    override fun noteOff(note: Int) {}
    override fun setParam(id: Int, normalized: Float) { params += id to normalized }
    override fun pitchBend(value: Float) {}
    override fun modWheel(value: Float) {}
    override fun sustain(on: Boolean) {}
    override fun allNotesOff() {}
    override fun loadPatch(values: FloatArray): Boolean { patches++; return acceptPatch }
  }

  private val p = TestParams.params
  private val cutoff = p["filter.cutoff"]!!
  private val wave = p["osc1.wave"]!!

  @Test fun startsAtTheDefaults() {
    val s = SynthState(p, Fake())
    assertEquals(cutoff.toNorm(cutoff.def), s.norm(cutoff.id), 1e-6f)
    assertEquals(cutoff.def, s.real(cutoff.id), 0.5f)
    assertFalse(s.dirty.value)
  }

  @Test fun aScreenChangeReachesTheEngineAndMarksDirtyWithoutFlashing() {
    val f = Fake()
    val s = SynthState(p, f)
    s.set(cutoff.id, 0.25f)
    assertEquals(cutoff.id to 0.25f, f.params.last())
    assertEquals(0.25f, s.value(cutoff.id).value, 1e-6f)
    assertTrue(s.dirty.value)
    assertEquals(-1, s.external.value.id)
  }

  @Test fun aMidiChangeIsReportedAsExternal() {
    val s = SynthState(p, Fake())
    s.setParam(cutoff.id, 0.5f)  // the Synth interface: what MidiDispatch calls
    assertEquals(cutoff.id, s.external.value.id)
    val first = s.external.value.seq
    s.setParam(cutoff.id, 0.6f)
    assertTrue(s.external.value.seq > first)
  }

  @Test fun stepParametersAreQuantized() {
    val s = SynthState(p, Fake())
    s.set(wave.id, 0.40f)  // 0..3 -> 1.2 -> 1
    assertEquals(1f, s.real(wave.id), 0f)
    assertEquals(wave.toNorm(1f), s.norm(wave.id), 1e-6f)
  }

  @Test fun loadingAPatchSetsEveryValueSendsOnePatchAndIsClean() {
    val f = Fake()
    val s = SynthState(p, f)
    s.set(cutoff.id, 0.1f)
    val values = s.snapshot().also { it[cutoff.id] = 0.9f }
    s.load(values)
    assertEquals(1, f.patches)
    assertEquals(0.9f, s.norm(cutoff.id), 1e-6f)
    assertFalse(s.dirty.value)
  }

  @Test fun aRefusedPatchFallsBackToSingleChanges() {
    val f = Fake().apply { acceptPatch = false }
    val s = SynthState(p, f)
    s.load(s.snapshot())
    assertEquals(p.all.size, f.params.size)
  }

  @Test fun notesPassStraightThrough() {
    val f = Fake()
    SynthState(p, f).noteOn(60, 1f)
    assertEquals(1, f.notes)
  }
}
```

**Step 2: Run** — compile error.

**Step 3: `SynthState.kt`**

```kotlin
package com.criapix.volt.state

import com.criapix.volt.Params
import com.criapix.volt.midi.Synth
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The normalized (0..1) value of every engine parameter, as the engine plays it. Every change
 * (screen, MIDI, preset) goes through here, so the screen always shows what is heard.
 * Thread-safe: the MIDI binder thread calls [setParam]; the UI thread calls the rest.
 * As a [Synth], it is what MidiDispatch talks to; notes pass straight through to [out].
 */
class SynthState(val params: Params, private val out: Synth) : Synth {
  /** The last parameter a MIDI controller changed; [seq] grows on every such change. */
  data class External(val id: Int, val seq: Long)

  private val flows = params.all.map { MutableStateFlow(it.toNorm(it.def)) }
  private val seq = AtomicLong()
  private val _dirty = MutableStateFlow(false)
  private val _external = MutableStateFlow(External(-1, 0))

  /** True once the sound differs from the loaded preset. */
  val dirty: StateFlow<Boolean> = _dirty
  val external: StateFlow<External> = _external

  fun value(id: Int): StateFlow<Float> = flows[id]
  fun norm(id: Int): Float = flows[id].value
  fun real(id: Int): Float = params.all[id].toReal(norm(id))

  /** A change made on the screen. */
  fun set(id: Int, normalized: Float) = change(id, normalized, external = false)

  /** A change from a MIDI controller: the screen follows it and flashes the parameter's tab. */
  override fun setParam(id: Int, normalized: Float) = change(id, normalized, external = true)

  private fun change(id: Int, normalized: Float, external: Boolean) {
    if (id !in flows.indices) return
    val p = params.all[id]
    val n = p.toNorm(p.toReal(normalized))  // clamps, and snaps steps to whole values
    out.setParam(id, n)
    if (flows[id].value == n) return
    flows[id].value = n
    _dirty.value = true
    if (external) _external.value = External(id, seq.incrementAndGet())
  }

  fun snapshot(): FloatArray = FloatArray(flows.size) { flows[it].value }

  /** Replaces every value at once (a preset). The engine switches without a click. */
  fun load(values: FloatArray) {
    require(values.size == flows.size) { "patch has ${values.size} values, engine has ${flows.size}" }
    for (i in flows.indices) flows[i].value = values[i]
    _dirty.value = false
    if (!out.loadPatch(values)) values.forEachIndexed { id, v -> out.setParam(id, v) }
  }

  override fun loadPatch(values: FloatArray): Boolean { load(values); return true }

  fun markClean() { _dirty.value = false }
  fun markDirty() { _dirty.value = true }

  override fun noteOn(note: Int, velocity: Float) = out.noteOn(note, velocity)
  override fun noteOff(note: Int) = out.noteOff(note)
  override fun pitchBend(value: Float) = out.pitchBend(value)
  override fun modWheel(value: Float) = out.modWheel(value)
  override fun sustain(on: Boolean) = out.sustain(on)
  override fun allNotesOff() = out.allNotesOff()
}
```

**Step 4: MIDI activity counter** — the top bar's MIDI light needs a signal for *every* message, while `lastMessage` dedupes repeated text. In `MidiDispatch`:

```kotlin
  private val _activity = MutableStateFlow(0L)
  /** Grows on every channel message that passes the channel filter (the MIDI light). */
  val activity: StateFlow<Long> = _activity
```

and right after `if (channel >= 0 && ch != channel) return` add `_activity.value = _activity.value + 1`.

Test, appended to `MidiDispatchTest`:

```kotlin
  @Test fun everyMessageBumpsTheActivityCounter() {
    send(0x90, 60, 100); send(0x90, 60, 100); send(0xB0, 74, 3)
    assertEquals(3L, d.activity.value)
  }
```

**Step 5: Run** the Kotlin tests — all pass.

**Step 6: Commit** — `feat(app): SynthState, the single source of parameter values`.

---

### Task 7: Preset file format

**Files:**
- Create: `app/src/main/kotlin/com/criapix/volt/preset/PresetCodec.kt`, `app/src/test/kotlin/com/criapix/volt/preset/PresetCodecTest.kt`

The format (design section 7): JSON, `format` version, `name`, `category`, and `params` as a map from the permanent parameter **name** to its **real** value (Hz, seconds, semitones…). A bank file (export/import) is `{ "format": 1, "presets": [ … ] }`. Reading is tolerant: a missing parameter takes the engine default, an out-of-range value is clamped, an unknown name is ignored.

Categories are stored as English keys: `Bass, Lead, Pad, Brass, Strings, Keys, Pluck, FX`, or `""` (none). The screen translates them (Task 11).

**Step 1: Failing test — `PresetCodecTest.kt`**

```kotlin
package com.criapix.volt.preset

import com.criapix.volt.TestParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetCodecTest {
  private val p = TestParams.params

  @Test fun missingParametersTakeTheirDefaults() {
    val v = PresetCodec.toValues(PresetCodec.decode("""{"format":1,"name":"X","params":{}}"""), p)
    for (prm in p.all) assertEquals(prm.name, prm.toNorm(prm.def), v[prm.id], 1e-6f)
  }

  @Test fun realValuesAreReadByNameAndClamped() {
    val text = """{"format":1,"name":"X","category":"Bass","params":{"filter.cutoff":500,"filter.reso":7,"osc1.semi":-40}}"""
    val v = PresetCodec.toValues(PresetCodec.decode(text), p)
    assertEquals(500f, p["filter.cutoff"]!!.toReal(v[p["filter.cutoff"]!!.id]), 0.5f)
    assertEquals(1f, v[p["filter.reso"]!!.id], 0f)
    assertEquals(0f, v[p["osc1.semi"]!!.id], 0f)
  }

  @Test fun unknownParametersAndFieldsAreIgnored() {
    val text = """{"format":7,"name":"Futuro","params":{"fx.reverb.size":0.5},"author":"alguém"}"""
    val preset = PresetCodec.decode(text)
    assertEquals("Futuro", preset.name)
    PresetCodec.toValues(preset, p)  // must not throw
  }

  @Test fun brokenFilesAreRefusedWithAMessage() {
    val e = assertThrows(PresetError::class.java) { PresetCodec.decode("{ isto não é json") }
    assertTrue(e.message!!.isNotBlank())
    assertThrows(PresetError::class.java) { PresetCodec.decode("""{"format":1,"name":"  "}""") }
    assertThrows(PresetError::class.java) { PresetCodec.decode("""[1,2,3]""") }
  }

  @Test fun valuesRoundTripThroughAFile() {
    val values = FloatArray(p.all.size) { i -> p.all[i].toNorm(p.all[i].toReal((i * 37 % 100) / 100f)) }
    val file = PresetCodec.fromValues("Meu som", "Pad", values, p)
    val back = PresetCodec.toValues(PresetCodec.decode(PresetCodec.encode(file)), p)
    for (i in values.indices) assertEquals(p.all[i].name, values[i], back[i], 1e-3f)
    assertEquals("Pad", PresetCodec.decode(PresetCodec.encode(file)).category)
  }

  @Test fun aSavedFileListsEveryParameterInTableOrder() {
    val file = PresetCodec.fromValues("A", "", FloatArray(p.all.size), p)
    assertEquals(p.all.map { it.name }, file.params.keys.toList())
  }

  @Test fun decodeAnyReadsASingleFileOrABank() {
    val one = PresetCodec.encode(PresetFile(name = "Um"))
    val bank = PresetCodec.encodeBank(listOf(PresetFile(name = "A"), PresetFile(name = "B")))
    assertEquals(listOf("Um"), PresetCodec.decodeAny(one).map { it.name })
    assertEquals(listOf("A", "B"), PresetCodec.decodeAny(bank).map { it.name })
  }
}
```

**Step 2: Run** — compile error.

**Step 3: `PresetCodec.kt`**

```kotlin
package com.criapix.volt.preset

import com.criapix.volt.Params
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class PresetFile(
  val format: Int = PresetCodec.FORMAT,
  val name: String,
  val category: String = "",
  /** Permanent parameter name -> real value (Hz, seconds, semitones...). */
  val params: Map<String, Float> = emptyMap(),
)

@Serializable
data class BankFile(val format: Int = PresetCodec.FORMAT, val presets: List<PresetFile>)

/** A preset file that cannot be used. The message is shown to the user (Portuguese). */
class PresetError(message: String) : Exception(message)

object PresetCodec {
  const val FORMAT = 1
  val CATEGORIES = listOf("Bass", "Lead", "Pad", "Brass", "Strings", "Keys", "Pluck", "FX")

  private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

  fun decode(text: String): PresetFile {
    val preset = try {
      json.decodeFromString(PresetFile.serializer(), text)
    } catch (e: Exception) {
      throw PresetError("Arquivo de preset inválido")
    }
    if (preset.name.isBlank()) throw PresetError("Preset sem nome")
    return preset
  }

  /** A single preset file or a bank (export). */
  fun decodeAny(text: String): List<PresetFile> {
    val element = try { json.parseToJsonElement(text) } catch (e: Exception) { throw PresetError("Arquivo inválido") }
    val obj = element as? JsonObject ?: throw PresetError("Arquivo inválido")
    if ("presets" !in obj) return listOf(decode(text))
    val bank = try { json.decodeFromJsonElement(BankFile.serializer(), obj) } catch (e: Exception) { throw PresetError("Banco de presets inválido") }
    return bank.presets.filter { it.name.isNotBlank() }
  }

  fun encode(p: PresetFile): String = json.encodeToString(PresetFile.serializer(), p)
  fun encodeBank(list: List<PresetFile>): String = json.encodeToString(BankFile.serializer(), BankFile(FORMAT, list))

  /** Normalized values indexed by parameter id: defaults for what is missing, clamped, unknown names ignored. */
  fun toValues(p: PresetFile, params: Params): FloatArray = FloatArray(params.all.size) { i ->
    val prm = params.all[i]
    val v = p.params[prm.name]
    if (v == null || !v.isFinite()) prm.toNorm(prm.def) else prm.toNorm(v)
  }

  /** A complete file (every parameter, in table order) from normalized values. */
  fun fromValues(name: String, category: String, values: FloatArray, params: Params): PresetFile =
    PresetFile(FORMAT, name, category, params.all.associate { it.name to round5(it.toReal(values[it.id])) })

  // Five significant digits: readable files, far below what anyone can hear.
  private fun round5(v: Float): Float = String.format(Locale.ROOT, "%.5g", v).toFloat()
}
```

**Step 4: Run** — passes.

**Step 5: Commit** — `feat(app): preset file format`.

---

### Task 8: Preset library — factory, user, favorites

**Files:**
- Create: `app/src/main/kotlin/com/criapix/volt/preset/PresetLibrary.kt`, `app/src/main/kotlin/com/criapix/volt/preset/AssetFactory.kt`, `app/src/test/kotlin/com/criapix/volt/preset/PresetLibraryTest.kt`

Ids: `factory:<file stem>` (read-only, from `assets/presets/factory/`) and `user:<uuid>` (files in the app's private `files/presets/user/`). **Order** (browser, previous/next, and Program Change in plan 3): factory presets by file name, then user presets by name, case-insensitive.

**Step 1: Failing test — `PresetLibraryTest.kt`**

```kotlin
package com.criapix.volt.preset

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PresetLibraryTest {
  @get:Rule val tmp = TemporaryFolder()

  private class MemFavorites : FavoriteStore {
    var ids = emptySet<String>()
    override fun load() = ids
    override fun save(ids: Set<String>) { this.ids = ids }
  }

  private fun factoryDir(): File = tmp.newFolder("factory").also { d ->
    File(d, "020-b.json").writeText(PresetCodec.encode(PresetFile(name = "Bravo", category = "Lead")))
    File(d, "010-a.json").writeText(PresetCodec.encode(PresetFile(name = "Alfa", category = "Bass")))
    File(d, "030-broken.json").writeText("{ quebrado")
  }

  private fun library(favs: FavoriteStore = MemFavorites()) =
    PresetLibrary(DirFactory(factoryDir()), File(tmp.root, "user"), favs)

  @Test fun factoryComesFirstByFileNameAndBrokenFilesAreSkipped() {
    val lib = library()
    assertEquals(listOf("factory:010-a", "factory:020-b"), lib.all().map { it.id })
    assertEquals("Alfa", lib.read("factory:010-a").name)
  }

  @Test fun userPresetsFollowSortedByName() {
    val lib = library()
    lib.create(PresetFile(name = "zebra"))
    lib.create(PresetFile(name = "Meio"))
    assertEquals(listOf("Alfa", "Bravo", "Meio", "zebra"), lib.all().map { it.name })
    assertTrue(lib.all().last().isUser)
  }

  @Test fun createReadOverwriteRenameDelete() {
    val lib = library()
    val ref = lib.create(PresetFile(name = "Som", params = mapOf("filter.cutoff" to 500f)))
    assertEquals(500f, lib.read(ref.id).params["filter.cutoff"])
    lib.overwrite(ref.id, PresetFile(name = "Som", params = mapOf("filter.cutoff" to 900f)))
    assertEquals(900f, lib.read(ref.id).params["filter.cutoff"])
    lib.rename(ref.id, "Novo nome")
    assertEquals("Novo nome", lib.all().first { it.id == ref.id }.name)
    lib.delete(ref.id)
    assertFalse(lib.all().any { it.id == ref.id })
    assertThrows(PresetError::class.java) { lib.read(ref.id) }
  }

  @Test fun factoryPresetsCannotBeChanged() {
    val lib = library()
    assertThrows(IllegalArgumentException::class.java) { lib.overwrite("factory:010-a", PresetFile(name = "X")) }
    assertThrows(IllegalArgumentException::class.java) { lib.delete("factory:010-a") }
  }

  @Test fun favoritesAreStoredAndDeletingForgetsThem() {
    val favs = MemFavorites()
    val lib = library(favs)
    val ref = lib.create(PresetFile(name = "Fav"))
    lib.setFavorite("factory:010-a", true)
    lib.setFavorite(ref.id, true)
    assertEquals(setOf("factory:010-a", ref.id), favs.ids)
    lib.delete(ref.id)
    assertEquals(setOf("factory:010-a"), favs.ids)
    assertTrue(PresetLibrary(DirFactory(File(tmp.root, "factory")), File(tmp.root, "user"), favs).isFavorite("factory:010-a"))
  }

  @Test fun importRenamesCollisionsAndExportRoundTrips() {
    val lib = library()
    lib.create(PresetFile(name = "Pad"))
    val bank = PresetCodec.encodeBank(listOf(PresetFile(name = "Pad"), PresetFile(name = "Pad"), PresetFile(name = "Baixo")))
    lib.import(bank)
    assertEquals(listOf("Baixo", "Pad", "Pad (2)", "Pad (3)"), lib.all().filter { it.isUser }.map { it.name })
    val other = PresetLibrary(DirFactory(File(tmp.root, "factory")), tmp.newFolder("user2"), MemFavorites())
    assertEquals(4, other.import(lib.exportUser()).size)
  }

  @Test fun aBrokenUserFileIsSkipped() {
    val lib = library()
    File(tmp.root, "user").apply { mkdirs() }.resolve("lixo.json").writeText("nada")
    assertEquals(2, lib.all().size)
  }
}
```

**Step 2: `PresetLibrary.kt`**

```kotlin
package com.criapix.volt.preset

import java.io.File
import java.util.UUID

data class PresetRef(val id: String, val name: String, val category: String) {
  val isUser: Boolean get() = id.startsWith(PresetLibrary.USER)
}

/** Read-only presets shipped with the app: (file stem, JSON text), in order. */
fun interface FactorySource { fun list(): List<Pair<String, String>> }

interface FavoriteStore {
  fun load(): Set<String>
  fun save(ids: Set<String>)
}

/** Factory presets from a directory (tests). The app uses [AssetFactory]. */
class DirFactory(private val dir: File) : FactorySource {
  override fun list() = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
    .sortedBy { it.name }.map { it.name.removeSuffix(".json") to it.readText() }
}

/**
 * Factory presets (read-only) followed by the user's presets (one JSON file each in [userDir]).
 * Not thread-safe: call from the UI thread. Files are tiny, so plain blocking I/O is fine.
 */
class PresetLibrary(factory: FactorySource, private val userDir: File, private val favorites: FavoriteStore) {
  companion object {
    const val FACTORY = "factory:"
    const val USER = "user:"
  }

  private val factoryPresets: List<Pair<PresetRef, PresetFile>> = factory.list().mapNotNull { (stem, text) ->
    runCatching { PresetCodec.decode(text) }.getOrNull()?.let { PresetRef(FACTORY + stem, it.name, it.category) to it }
  }
  private val favs = favorites.load().toMutableSet()
  private var cache: List<PresetRef>? = null

  fun all(): List<PresetRef> = cache ?: (factoryPresets.map { it.first } + userRefs()).also { cache = it }

  fun read(id: String): PresetFile = when {
    id.startsWith(FACTORY) -> factoryPresets.firstOrNull { it.first.id == id }?.second
    id.startsWith(USER) -> fileOf(id).takeIf { it.exists() }?.let { PresetCodec.decode(it.readText()) }
    else -> null
  } ?: throw PresetError("Preset não encontrado")

  fun create(p: PresetFile): PresetRef {
    val stem = UUID.randomUUID().toString()
    write(File(userDir, "$stem.json"), p)
    return PresetRef(USER + stem, p.name, p.category)
  }

  fun overwrite(id: String, p: PresetFile) {
    require(id.startsWith(USER)) { "factory presets are read-only" }
    write(fileOf(id), p)
  }

  fun rename(id: String, name: String) = overwrite(id, read(id).copy(name = name))

  fun delete(id: String) {
    require(id.startsWith(USER)) { "factory presets are read-only" }
    fileOf(id).delete()
    if (favs.remove(id)) favorites.save(favs.toSet())
    cache = null
  }

  fun isFavorite(id: String) = id in favs
  fun favoriteIds(): Set<String> = favs.toSet()

  fun setFavorite(id: String, on: Boolean) {
    if (on) favs += id else favs -= id
    favorites.save(favs.toSet())
  }

  /** Every user preset as one bank file (backup). */
  fun exportUser(): String = PresetCodec.encodeBank(all().filter { it.isUser }.map { read(it.id) })

  /** Adds every preset in [text] (a preset or a bank) as a new user preset. Names that exist get " (2)", " (3)"... */
  fun import(text: String): List<PresetRef> = PresetCodec.decodeAny(text).map { create(it.copy(name = uniqueName(it.name))) }

  private fun uniqueName(name: String): String {
    val taken = all().filter { it.isUser }.map { it.name.lowercase() }.toSet()
    if (name.lowercase() !in taken) return name
    var i = 2
    while ("$name ($i)".lowercase() in taken) i++
    return "$name ($i)"
  }

  private fun userRefs(): List<PresetRef> = userDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
    .mapNotNull { f ->
      runCatching { PresetCodec.decode(f.readText()) }.getOrNull()
        ?.let { PresetRef(USER + f.name.removeSuffix(".json"), it.name, it.category) }
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

  private fun write(f: File, p: PresetFile) {
    userDir.mkdirs()
    val tmp = File(userDir, f.name + ".tmp")
    tmp.writeText(PresetCodec.encode(p))
    if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }  // Windows (tests) cannot rename over a file
    cache = null
  }

  private fun fileOf(id: String) = File(userDir, id.removePrefix(USER) + ".json")
}
```

**Step 3: `AssetFactory.kt`** (Android only; not unit-tested)

```kotlin
package com.criapix.volt.preset

import android.content.res.AssetManager

/** Factory presets shipped in assets/presets/factory, ordered by file name. */
class AssetFactory(private val assets: AssetManager, private val dir: String = "presets/factory") : FactorySource {
  override fun list(): List<Pair<String, String>> =
    (assets.list(dir) ?: emptyArray()).filter { it.endsWith(".json") }.sorted()
      .map { name -> name.removeSuffix(".json") to assets.open("$dir/$name").bufferedReader().use { it.readText() } }
}
```

**Step 4: Run** the Kotlin tests — pass.

**Step 5: Commit** — `feat(app): preset library with factory, user and favorite presets`.

---

### Task 9: Factory starter presets

Ten presets so the browser, previous/next and the save flow have real content. They are a first pass written from parameter values, not by ear. Plan 5 replaces this set with the full bank. A missing parameter takes the engine default, so **after a release, never change a default in `params.def` without checking the factory files**.

**Files:**
- Create: `app/src/main/assets/presets/factory/*.json` (10 files), `app/src/test/kotlin/com/criapix/volt/preset/FactoryPresetsTest.kt`

**Step 1: Failing test — `FactoryPresetsTest.kt`**

```kotlin
package com.criapix.volt.preset

import com.criapix.volt.TestParams
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FactoryPresetsTest {
  private val dir = File("src/main/assets/presets/factory")
  private val files = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
  private val p = TestParams.params

  @Test fun thereIsAStarterSetAndInitComesFirst() {
    assertTrue("found ${files.size}", files.size >= 10)
    assertEquals("Init", PresetCodec.decode(files.first().readText()).name)
  }

  @Test fun everyFileIsValidWithKnownNamesInRangeValuesAndAKnownCategory() {
    val names = HashSet<String>()
    for (f in files) {
      val preset = PresetCodec.decode(f.readText())
      assertTrue("${f.name}: duplicate name", names.add(preset.name))
      assertTrue("${f.name}: category ${preset.category}", preset.category == "" || preset.category in PresetCodec.CATEGORIES)
      for ((name, value) in preset.params) {
        val prm = p[name]
        assertTrue("${f.name}: unknown parameter $name", prm != null)
        assertTrue("${f.name}: $name = $value outside ${prm!!.min}..${prm.max}", value >= prm.min && value <= prm.max)
      }
    }
  }
}
```

**Step 2: Write the files.** Each file is `{"format": 1, "name": …, "category": …, "params": {…}}`. Values are real units.

`001-init.json`

```json
{ "format": 1, "name": "Init", "category": "", "params": {} }
```

`010-baixo-gordo.json` — three detuned oscillators into the ladder, mono with legato glide.

```json
{ "format": 1, "name": "Baixo Gordo", "category": "Bass", "params": {
  "voice.mode": 1, "voice.glide": 0.04, "voice.glide_legato": 1, "voice.drift": 0.15,
  "osc1.wave": 0, "osc1.octave": -1,
  "osc2.wave": 0, "osc2.octave": -1, "osc2.fine": 7, "osc2.level": 0.8,
  "osc3.wave": 1, "osc3.octave": -2, "osc3.level": 0.6,
  "mix.drive": 0.3,
  "filter.model": 0, "filter.cutoff": 180, "filter.reso": 0.35, "filter.env": 0.45, "filter.keytrack": 0.3,
  "fenv.attack": 0.002, "fenv.decay": 0.35, "fenv.sustain": 0.15, "fenv.release": 0.12,
  "aenv.attack": 0.002, "aenv.decay": 0.5, "aenv.sustain": 0.85, "aenv.release": 0.1, "aenv.vel": 0.3 } }
```

`011-baixo-redondo.json`

```json
{ "format": 1, "name": "Baixo Redondo", "category": "Bass", "params": {
  "voice.mode": 2, "voice.drift": 0.1,
  "osc1.wave": 1, "osc1.octave": -1, "osc1.pw": 0.5, "mix.sub": 0.6,
  "filter.model": 2, "filter.cutoff": 300, "filter.reso": 0.15, "filter.env": 0.35, "filter.keytrack": 0.3,
  "fenv.attack": 0.002, "fenv.decay": 0.25, "fenv.sustain": 0.0, "fenv.release": 0.1,
  "aenv.attack": 0.002, "aenv.decay": 0.3, "aenv.sustain": 1.0, "aenv.release": 0.08 } }
```

`020-lead-sync.json` — the case Task 2 made possible: osc 1 synced to osc 2, swept by the filter envelope through poly-mod.

```json
{ "format": 1, "name": "Lead Sync", "category": "Lead", "params": {
  "voice.mode": 2, "voice.glide": 0.03, "voice.drift": 0.1,
  "osc1.wave": 0, "osc1.sync": 1, "osc1.level": 1.0,
  "osc2.wave": 0, "osc2.level": 0.25,
  "pmod.fenv.pitch": 0.35,
  "filter.model": 3, "filter.cutoff": 6000, "filter.reso": 0.2, "filter.env": 0.1, "filter.keytrack": 0.5,
  "fenv.attack": 0.001, "fenv.decay": 0.6, "fenv.sustain": 0.2, "fenv.release": 0.3,
  "aenv.attack": 0.003, "aenv.decay": 0.2, "aenv.sustain": 1.0, "aenv.release": 0.2,
  "lfo1.wave": 0, "lfo1.rate": 5.5, "lfo1.pitch": 0.1, "lfo1.wheel": 1.0 } }
```

`021-lead-classico.json`

```json
{ "format": 1, "name": "Lead Clássico", "category": "Lead", "params": {
  "voice.mode": 1, "voice.glide": 0.05, "voice.drift": 0.15,
  "osc1.wave": 0, "osc2.wave": 0, "osc2.fine": 6, "osc2.level": 1.0,
  "osc3.wave": 1, "osc3.octave": -1, "osc3.level": 0.5,
  "mix.drive": 0.4,
  "filter.model": 0, "filter.cutoff": 1200, "filter.reso": 0.45, "filter.env": 0.35, "filter.keytrack": 0.6,
  "fenv.attack": 0.01, "fenv.decay": 0.4, "fenv.sustain": 0.4, "fenv.release": 0.2,
  "aenv.attack": 0.005, "aenv.decay": 0.2, "aenv.sustain": 0.9, "aenv.release": 0.15,
  "lfo1.wave": 0, "lfo1.rate": 5.5, "lfo1.pitch": 0.1, "lfo1.wheel": 1.0 } }
```

`030-pad-analogico.json`

```json
{ "format": 1, "name": "Pad Analógico", "category": "Pad", "params": {
  "voice.drift": 0.3,
  "osc1.wave": 0, "osc1.fine": -6, "osc2.wave": 0, "osc2.fine": 6, "osc2.level": 0.9,
  "filter.model": 2, "filter.cutoff": 1800, "filter.reso": 0.15, "filter.env": 0.2, "filter.keytrack": 0.4,
  "fenv.attack": 1.2, "fenv.decay": 2.0, "fenv.sustain": 0.6, "fenv.release": 2.0,
  "aenv.attack": 0.9, "aenv.decay": 1.0, "aenv.sustain": 0.9, "aenv.release": 2.5,
  "lfo1.global": 1, "lfo1.wave": 1, "lfo1.rate": 0.3, "lfo1.cutoff": 0.05 } }
```

`040-metais-poly.json`

```json
{ "format": 1, "name": "Metais Poly", "category": "Brass", "params": {
  "voice.drift": 0.2,
  "osc1.wave": 0, "osc2.wave": 0, "osc2.fine": 5, "osc2.level": 0.8,
  "filter.model": 1, "filter.cutoff": 500, "filter.reso": 0.1, "filter.env": 0.5, "filter.keytrack": 0.5, "filter.vel": 0.2,
  "fenv.attack": 0.08, "fenv.decay": 0.5, "fenv.sustain": 0.55, "fenv.release": 0.3,
  "aenv.attack": 0.05, "aenv.decay": 0.3, "aenv.sustain": 0.85, "aenv.release": 0.3, "aenv.vel": 0.4 } }
```

`050-cordas-suaves.json`

```json
{ "format": 1, "name": "Cordas Suaves", "category": "Strings", "params": {
  "voice.drift": 0.25,
  "osc1.wave": 0, "osc2.wave": 0, "osc2.fine": 10, "osc2.level": 0.7,
  "hpf.cutoff": 150,
  "filter.model": 2, "filter.cutoff": 3000, "filter.reso": 0.05, "filter.env": 0.1, "filter.keytrack": 0.5,
  "fenv.attack": 0.5, "fenv.decay": 1.0, "fenv.sustain": 0.8, "fenv.release": 1.0,
  "aenv.attack": 0.45, "aenv.decay": 0.5, "aenv.sustain": 1.0, "aenv.release": 1.2,
  "lfo1.wave": 0, "lfo1.rate": 5.0, "lfo1.delay": 1.5, "lfo1.pitch": 0.08 } }
```

`060-pluck-vidro.json`

```json
{ "format": 1, "name": "Pluck de Vidro", "category": "Pluck", "params": {
  "osc1.wave": 1, "osc1.pw": 0.3, "osc2.wave": 2, "osc2.octave": 1, "osc2.level": 0.5,
  "filter.model": 0, "filter.cutoff": 400, "filter.reso": 0.5, "filter.env": 0.6, "filter.keytrack": 0.7, "filter.vel": 0.4,
  "fenv.attack": 0.001, "fenv.decay": 0.25, "fenv.sustain": 0.0, "fenv.release": 0.25,
  "aenv.attack": 0.001, "aenv.decay": 0.6, "aenv.sustain": 0.0, "aenv.release": 0.4, "aenv.vel": 0.6 } }
```

`070-sino-metalico.json` — poly-mod FM (osc 2 → osc 1 frequency).

```json
{ "format": 1, "name": "Sino Metálico", "category": "Keys", "params": {
  "osc1.wave": 2, "osc1.level": 1.0,
  "osc2.wave": 3, "osc2.octave": 1, "osc2.semi": 7, "osc2.level": 0.0,
  "pmod.osc2.pitch": 0.4,
  "filter.model": 1, "filter.cutoff": 8000, "filter.reso": 0.05, "filter.keytrack": 0.5,
  "aenv.attack": 0.001, "aenv.decay": 1.5, "aenv.sustain": 0.0, "aenv.release": 1.2, "aenv.vel": 0.5 } }
```

**Step 3: Run** the Kotlin tests — pass.

**Step 4: Commit** — `feat(app): factory starter presets`.

---

### Task 10: Session and AppModel

`AppModel` is the screen's brain, with no Android types so it is tested on the JVM: the current preset, previous/next, save, save as, rename, delete, favorites, import/export, and the session file. The session file restores the last preset and any unsaved edits at the next start.

**Files:**
- Create: `app/src/main/kotlin/com/criapix/volt/preset/Session.kt`, `preset/AppModel.kt`, `app/src/test/kotlin/com/criapix/volt/preset/AppModelTest.kt`

**Step 1: Failing test — `AppModelTest.kt`**

```kotlin
package com.criapix.volt.preset

import com.criapix.volt.TestParams
import com.criapix.volt.midi.Synth
import com.criapix.volt.state.SynthState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppModelTest {
  @get:Rule val tmp = TemporaryFolder()

  private object Silent : Synth {
    override fun noteOn(note: Int, velocity: Float) {}
    override fun noteOff(note: Int) {}
    override fun setParam(id: Int, normalized: Float) {}
    override fun pitchBend(value: Float) {}
    override fun modWheel(value: Float) {}
    override fun sustain(on: Boolean) {}
    override fun allNotesOff() {}
  }

  private object NoFavorites : FavoriteStore {
    override fun load() = emptySet<String>()
    override fun save(ids: Set<String>) {}
  }

  private val p = TestParams.params
  private val cutoff = p["filter.cutoff"]!!

  private fun factory(): File = File(tmp.root, "factory").apply {
    if (!exists()) {
      mkdirs()
      File(this, "001-init.json").writeText(PresetCodec.encode(PresetFile(name = "Init")))
      File(this, "010-dark.json").writeText(PresetCodec.encode(PresetFile(name = "Escuro", category = "Pad", params = mapOf("filter.cutoff" to 300f))))
      File(this, "020-bright.json").writeText(PresetCodec.encode(PresetFile(name = "Claro", category = "Lead", params = mapOf("filter.cutoff" to 9000f))))
    }
  }

  private fun model(): AppModel {
    val state = SynthState(p, Silent)
    val lib = PresetLibrary(DirFactory(factory()), File(tmp.root, "user"), NoFavorites)
    return AppModel(state, lib, File(tmp.root, "session.json"))
  }

  @Test fun firstStartLoadsTheFirstFactoryPreset() {
    val m = model().apply { restore() }
    assertEquals("factory:001-init", m.current.value.id)
    assertEquals("Init", m.current.value.name)
    assertFalse(m.state.dirty.value)
  }

  @Test fun nextAndPreviousFollowTheLibraryOrderAndWrap() {
    val m = model().apply { restore() }
    m.step(1)
    assertEquals("Escuro", m.current.value.name)
    assertEquals(300f, m.state.real(cutoff.id), 1f)
    m.step(1); m.step(1)
    assertEquals("Init", m.current.value.name)
    m.step(-1)
    assertEquals("Claro", m.current.value.name)
    assertEquals(3, m.number(m.current.value.id))
  }

  @Test fun editingMarksDirtyAndSelectingClears() {
    val m = model().apply { restore() }
    m.state.set(cutoff.id, 0.2f)
    assertTrue(m.state.dirty.value)
    m.select("factory:010-dark")
    assertFalse(m.state.dirty.value)
  }

  @Test fun saveAsCreatesAUserPresetThatSaveCanOverwrite() {
    val m = model().apply { restore() }
    assertFalse(m.canOverwrite())
    m.state.set(cutoff.id, cutoff.toNorm(700f))
    val ref = m.saveAs("Meu", "Bass")
    assertEquals(ref.id, m.current.value.id)
    assertTrue(m.canOverwrite())
    assertFalse(m.state.dirty.value)
    m.state.set(cutoff.id, cutoff.toNorm(1500f))
    assertTrue(m.save("Meu 2", "Lead"))
    assertEquals(1500f, m.library.read(ref.id).params["filter.cutoff"]!!, 1f)
    assertEquals("Meu 2", m.current.value.name)
    assertTrue(m.presets.value.any { it.name == "Meu 2" })
  }

  @Test fun deletingTheCurrentPresetKeepsTheSoundAsUnsaved() {
    val m = model().apply { restore() }
    val ref = m.saveAs("Temp", "")
    m.delete(ref.id)
    assertNull(m.current.value.id)
    assertEquals("Temp", m.current.value.name)
    assertTrue(m.state.dirty.value)
  }

  @Test fun theSessionRestoresPresetAndUnsavedEdits() {
    val a = model().apply { restore() }
    a.select("factory:020-bright")
    a.state.set(cutoff.id, cutoff.toNorm(2500f))
    a.persist()
    val b = model().apply { restore() }
    assertEquals("factory:020-bright", b.current.value.id)
    assertEquals(2500f, b.state.real(cutoff.id), 2f)
    assertTrue(b.state.dirty.value)
  }

  @Test fun aSessionPointingAtADeletedPresetStillRestoresTheSound() {
    val a = model().apply { restore() }
    val ref = a.saveAs("Some", "")
    a.persist()
    a.library.delete(ref.id)
    val b = model().apply { restore() }
    assertNull(b.current.value.id)
    assertEquals("Some", b.current.value.name)
  }

  @Test fun aBrokenImportReportsAMessage() {
    val m = model().apply { restore() }
    assertEquals(0, m.importText("nada"))
    assertNotNull(m.message.value)
  }
}
```

**Step 2: `Session.kt`**

```kotlin
package com.criapix.volt.preset

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SessionFile(val presetId: String? = null, val dirty: Boolean = false, val current: PresetFile)

/** The sound on screen when the app stopped, so the next start continues from it. */
object Session {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  fun save(file: File, presetId: String?, dirty: Boolean, current: PresetFile) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(json.encodeToString(SessionFile.serializer(), SessionFile(presetId, dirty, current)))
    if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
  }

  fun load(file: File): SessionFile? =
    if (!file.exists()) null else runCatching { json.decodeFromString(SessionFile.serializer(), file.readText()) }.getOrNull()
}
```

**Step 3: `AppModel.kt`**

```kotlin
package com.criapix.volt.preset

import com.criapix.volt.state.SynthState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The current preset and everything done to presets. No Android types: unit-tested on the JVM.
 * Call from the UI thread.
 */
class AppModel(
  val state: SynthState,
  val library: PresetLibrary,
  private val sessionFile: File,
  private val log: (String) -> Unit = {},
) {
  /** The preset the sound came from. [id] is null when it no longer exists (deleted). */
  data class Current(val id: String?, val name: String, val category: String)

  private val _current = MutableStateFlow(Current(null, "Init", ""))
  private val _presets = MutableStateFlow(library.all())
  private val _favorites = MutableStateFlow(library.favoriteIds())
  private val _message = MutableStateFlow<String?>(null)

  val current: StateFlow<Current> = _current
  val presets: StateFlow<List<PresetRef>> = _presets
  val favorites: StateFlow<Set<String>> = _favorites
  /** A short message for the user (Portuguese); the screen clears it after showing it. */
  val message: StateFlow<String?> = _message

  fun restore() {
    val s = Session.load(sessionFile)
    if (s == null) {
      _presets.value.firstOrNull()?.let { select(it.id) }
      return
    }
    state.load(PresetCodec.toValues(s.current, state.params))
    if (s.dirty) state.markDirty()
    val id = s.presetId?.takeIf { id -> _presets.value.any { it.id == id } }
    _current.value = Current(id, s.current.name, s.current.category)
    log("PRESET sessão restaurada: ${s.current.name}${if (s.dirty) " (com edições)" else ""}")
  }

  fun select(id: String): Boolean = try {
    val p = library.read(id)
    state.load(PresetCodec.toValues(p, state.params))
    _current.value = Current(id, p.name, p.category)
    log("PRESET carregado: ${p.name} ($id)")
    true
  } catch (e: PresetError) {
    _message.value = e.message
    false
  }

  /** Previous (-1) / next (+1) in library order, wrapping around. */
  fun step(delta: Int) {
    val list = _presets.value
    if (list.isEmpty()) return
    val i = list.indexOfFirst { it.id == _current.value.id }
    val next = if (i < 0) (if (delta > 0) 0 else list.lastIndex) else Math.floorMod(i + delta, list.size)
    select(list[next].id)
  }

  /** 1-based position in library order, or 0 when the current sound is not a stored preset. */
  fun number(id: String?): Int = _presets.value.indexOfFirst { it.id == id } + 1

  fun canOverwrite(): Boolean = _current.value.id?.startsWith(PresetLibrary.USER) == true

  /** Overwrites the current user preset with the sound on screen. False when it is a factory preset. */
  fun save(name: String, category: String): Boolean {
    val id = _current.value.id
    if (!canOverwrite() || id == null) return false
    val clean = name.trim().ifEmpty { "Sem nome" }
    library.overwrite(id, snapshot(clean, category))
    _current.value = Current(id, clean, category)
    state.markClean()
    refresh()
    log("PRESET salvo: $clean ($id)")
    return true
  }

  fun saveAs(name: String, category: String): PresetRef {
    val ref = library.create(snapshot(name.trim().ifEmpty { "Sem nome" }, category))
    _current.value = Current(ref.id, ref.name, ref.category)
    state.markClean()
    refresh()
    log("PRESET criado: ${ref.name} (${ref.id})")
    return ref
  }

  fun rename(id: String, name: String) {
    val clean = name.trim().ifEmpty { return }
    library.rename(id, clean)
    if (_current.value.id == id) _current.value = _current.value.copy(name = clean)
    refresh()
  }

  fun delete(id: String) {
    library.delete(id)
    if (_current.value.id == id) {
      _current.value = _current.value.copy(id = null)
      state.markDirty()
    }
    refresh()
    log("PRESET apagado: $id")
  }

  fun toggleFavorite(id: String) {
    library.setFavorite(id, !library.isFavorite(id))
    refresh()
  }

  fun exportBank(): String = library.exportUser()

  /** Adds the presets in a file the user picked. Returns how many were added. */
  fun importText(text: String): Int = try {
    val n = library.import(text).size
    refresh()
    _message.value = if (n == 1) "1 preset importado" else "$n presets importados"
    log("PRESET importados: $n")
    n
  } catch (e: PresetError) {
    _message.value = e.message
    0
  }

  fun showMessage(text: String) { _message.value = text }
  fun clearMessage() { _message.value = null }

  /** Writes the session file. Call when the app goes to the background. */
  fun persist() {
    val c = _current.value
    Session.save(sessionFile, c.id, state.dirty.value, snapshot(c.name, c.category))
  }

  private fun snapshot(name: String, category: String) =
    PresetCodec.fromValues(name, category, state.snapshot(), state.params)

  private fun refresh() {
    _presets.value = library.all()
    _favorites.value = library.favoriteIds()
  }
}
```

**Step 4: Run** the Kotlin tests — pass.

**Step 5: Commit** — `feat(app): AppModel and session restore`.

---

### Task 11: What each parameter looks like — labels, units, tabs

Two tables, both tested against the real parameter table so a parameter added later (plan 4's effects) cannot be forgotten on screen:
- `ParamUi`: every parameter's on-screen label, its widget (knob, choice, toggle) and how its value is written (unit, in pt-BR with a decimal comma).
- `Panel`: which tab and group each parameter lives in.

**Files:**
- Create: `ui/ParamUi.kt`, `ui/Panel.kt`, `ui/Theme.kt`, `app/src/test/kotlin/com/criapix/volt/ui/ParamUiTest.kt`, `ui/PanelTest.kt`

**Step 1: Failing tests**

`ParamUiTest.kt`

```kotlin
package com.criapix.volt.ui

import com.criapix.volt.Curve
import com.criapix.volt.TestParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParamUiTest {
  private val p = TestParams.params

  @Test fun everyParameterHasAWidgetThatFitsItsRange() {
    for (prm in p.all) {
      when (val ui = ParamUi.of(prm.name)) {
        is ChoiceUi -> {
          assertEquals(prm.name, Curve.STEP, prm.curve)
          assertEquals(prm.name, (prm.max - prm.min).toInt() + 1, ui.options.size)
        }
        is ToggleUi -> assertTrue(prm.name, prm.curve == Curve.STEP && prm.min == 0f && prm.max == 1f)
        is KnobUi -> assertTrue(prm.name, ui.label.isNotBlank())
      }
    }
  }

  @Test fun valuesAreWrittenWithUnitsInPortuguese() {
    fun f(name: String, v: Float) = ParamUi.format(p[name]!!, v)
    assertEquals("440 Hz", f("filter.cutoff", 440f))
    assertEquals("1,2 kHz", f("filter.cutoff", 1234.5f))
    assertEquals("20,0 kHz", f("filter.cutoff", 20000f))
    assertEquals("0,30 Hz", f("lfo1.rate", 0.3f))
    assertEquals("250 ms", f("aenv.attack", 0.25f))
    assertEquals("3,0 ms", f("aenv.attack", 0.003f))
    assertEquals("1,50 s", f("aenv.release", 1.5f))
    assertEquals("desl.", f("voice.glide", 0f))
    assertEquals("50 %", f("filter.reso", 0.5f))
    assertEquals("-30 %", f("filter.env", -0.3f))
    assertEquals("+30 %", f("filter.env", 0.3f))
    assertEquals("+7 st", f("osc1.semi", 7f))
    assertEquals("-12 ct", f("osc1.fine", -12.4f))
    assertEquals("-1 oct", f("osc1.octave", -1f))
    assertEquals("0 oct", f("osc1.octave", 0f))
    assertEquals("±2 st", f("voice.bend", 2f))
    assertEquals("4", f("voice.unison", 4f))
    assertEquals("Ladder", f("filter.model", 0f))
    assertEquals("lig.", f("osc1.sync", 1f))
  }
}
```

`PanelTest.kt`

```kotlin
package com.criapix.volt.ui

import com.criapix.volt.TestParams
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelTest {
  @Test fun everyParameterIsOnExactlyOneTab() {
    val placed = Panel.groups.values.flatten().flatMap { it.params }
    val names = TestParams.params.all.map { it.name }
    assertEquals(names.sorted(), placed.sorted())
  }

  @Test fun tabOfFindsTheTab() {
    assertEquals(Tab.FILTER, Panel.tabOf("filter.cutoff"))
    assertEquals(Tab.ENV, Panel.tabOf("aenv.release"))
    assertEquals(Tab.MOD, Panel.tabOf("voice.mode"))
    assertEquals(Tab.OSC, Panel.tabOf("master.volume"))
    assertEquals(null, Panel.tabOf("no.such"))
  }
}
```

**Step 2: `ui/ParamUi.kt`**

```kotlin
package com.criapix.volt.ui

import com.criapix.volt.Param
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Unit { HZ, SEC, GLIDE, PCT, BIPCT, SEMI, CENT, OCT, BEND, COUNT }

sealed interface Widget { val label: String }
data class KnobUi(override val label: String, val unit: Unit, val bipolar: Boolean = false) : Widget
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
      put("osc$n.octave", KnobUi("Oitava", Unit.OCT, bipolar = true))
      put("osc$n.semi", KnobUi("Semitom", Unit.SEMI, bipolar = true))
      put("osc$n.fine", KnobUi("Fino", Unit.CENT, bipolar = true))
      put("osc$n.pw", KnobUi("Largura", Unit.PCT))
      put("osc$n.level", KnobUi("Nível", Unit.PCT))
    }
    put("osc1.sync", ToggleUi("Sync no osc 2"))
    put("mix.sub", KnobUi("Sub", Unit.PCT))
    put("mix.noise", KnobUi("Ruído", Unit.PCT))
    put("mix.drive", KnobUi("Drive", Unit.PCT))
    put("master.volume", KnobUi("Volume", Unit.PCT))

    put("hpf.cutoff", KnobUi("Passa-altas", Unit.HZ))
    put("filter.model", ChoiceUi("Modelo", listOf("Ladder", "Roland 12", "Roland 24", "Prophet")))
    put("filter.cutoff", KnobUi("Cutoff", Unit.HZ))
    put("filter.reso", KnobUi("Resson.", Unit.PCT))
    put("filter.env", KnobUi("Envelope", Unit.BIPCT, bipolar = true))
    put("filter.keytrack", KnobUi("Teclado", Unit.PCT))
    put("filter.vel", KnobUi("Velocity", Unit.PCT))

    for (e in listOf("fenv", "aenv")) {
      put("$e.attack", KnobUi("Ataque", Unit.SEC))
      put("$e.decay", KnobUi("Decay", Unit.SEC))
      put("$e.sustain", KnobUi("Sustain", Unit.PCT))
      put("$e.release", KnobUi("Release", Unit.SEC))
    }
    put("aenv.vel", KnobUi("Velocity", Unit.PCT))

    for (n in 1..2) {
      put("lfo$n.wave", ChoiceUi("Onda", LFO_WAVES))
      put("lfo$n.rate", KnobUi("Veloc.", Unit.HZ))
      put("lfo$n.delay", KnobUi("Atraso", Unit.SEC))
      put("lfo$n.pitch", KnobUi("Afinação", Unit.PCT))
      put("lfo$n.pw", KnobUi("Largura", Unit.PCT))
      put("lfo$n.cutoff", KnobUi("Cutoff", Unit.PCT))
      put("lfo$n.amp", KnobUi("Volume", Unit.PCT))
    }
    put("lfo1.global", ToggleUi("Global"))
    put("lfo1.wheel", KnobUi("Roda", Unit.PCT))

    put("pmod.fenv.pitch", KnobUi("Env→Afin.", Unit.BIPCT, bipolar = true))
    put("pmod.fenv.pw", KnobUi("Env→Larg.", Unit.BIPCT, bipolar = true))
    put("pmod.osc2.pitch", KnobUi("Osc2→Afin.", Unit.PCT))
    put("pmod.osc2.pw", KnobUi("Osc2→Larg.", Unit.PCT))

    put("voice.mode", ChoiceUi("Modo", listOf("Poly", "Mono", "Legato", "Unison")))
    put("voice.unison", KnobUi("Vozes", Unit.COUNT))
    put("voice.detune", KnobUi("Detune", Unit.PCT))
    put("voice.spread", KnobUi("Abertura", Unit.PCT))
    put("voice.glide", KnobUi("Glide", Unit.GLIDE))
    put("voice.glide_legato", ToggleUi("Glide só em legato"))
    put("voice.drift", KnobUi("Drift", Unit.PCT))
    put("voice.bend", KnobUi("Bend", Unit.BEND))
  }

  fun of(name: String): Widget = table[name] ?: error("no on-screen description for parameter $name")

  /** The real value as shown on screen, e.g. "1,2 kHz", "250 ms", "+7 st". */
  fun format(p: Param, real: Float): String = when (val w = of(p.name)) {
    is ChoiceUi -> w.options.getOrElse((real - p.min).roundToInt()) { "?" }
    is ToggleUi -> if (real >= 0.5f) "lig." else "desl."
    is KnobUi -> when (w.unit) {
      Unit.HZ -> when {
        real >= 1000f -> fmt("%.1f kHz", real / 1000f)
        real >= 10f -> fmt("%.0f Hz", real)
        else -> fmt("%.2f Hz", real)
      }
      Unit.SEC -> seconds(real)
      Unit.GLIDE -> if (real < 0.0005f) "desl." else seconds(real)
      Unit.PCT -> "${(real * 100).roundToInt()} %"
      Unit.BIPCT -> signed((real * 100).roundToInt(), " %")
      Unit.SEMI -> signed(real.roundToInt(), " st")
      Unit.CENT -> signed(real.roundToInt(), " ct")
      Unit.OCT -> signed(real.roundToInt(), " oct")
      Unit.BEND -> "±${real.roundToInt()} st"
      Unit.COUNT -> real.roundToInt().toString()
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
```

Check against the test: `PCT` for `filter.reso 0.5` gives `"50 %"`; `BIPCT -0.3` gives `"-30 %"`; zero gives `"0 %"` (no sign).

**Step 3: `ui/Panel.kt`**

```kotlin
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

  fun tabOf(name: String): Tab? = groups.entries.firstOrNull { (_, gs) -> gs.any { name in it.params } }?.key
}
```

**Step 4: `ui/Theme.kt`**

```kotlin
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
```

**Step 5: Run** the Kotlin tests — pass.

**Step 6: Commit** — `feat(app): on-screen labels, units and tab layout for every parameter`.

---

### Task 12: Knob, selector, toggle

**Files:**
- Create: `ui/KnobMath.kt`, `ui/Knob.kt`, `ui/Selector.kt`, `app/src/test/kotlin/com/criapix/volt/ui/KnobMathTest.kt`

Behaviour (design 5.3):
- **Drag vertically** anywhere on the knob: 200 dp of travel covers the whole range. **Horizontal** movement adjusts at one tenth of that speed (fine adjust).
- **Double tap**: back to the parameter's default.
- While the finger is down, a bubble above the knob shows the value large, in real units.
- Step parameters (octave, semitone, unison voices, bend range) snap to whole values and tick the haptic motor at each new value.
- Each knob handles its own pointer, so two knobs can be turned at once with two fingers.

`KnobGrid.fit` chooses how many knob columns a group uses and how large the knobs can be in the space it has. When even the best layout is below 52 dp, the group scrolls vertically at 52 dp instead.

**Step 1: Failing test — `KnobMathTest.kt`**

```kotlin
package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class KnobMathTest {
  @Test fun verticalDragCoversTheRangeInOneTravel() {
    assertEquals(1f, KnobMath.drag(0f, 0f, -200f, 200f, 0), 1e-6f)
    assertEquals(0.25f, KnobMath.drag(0.5f, 0f, 50f, 200f, 0), 1e-6f)
  }

  @Test fun horizontalDragIsTenTimesFiner() {
    assertEquals(0.55f, KnobMath.drag(0.5f, 100f, 0f, 200f, 0), 1e-6f)
  }

  @Test fun resultIsClampedAndStepsSnap() {
    assertEquals(0f, KnobMath.drag(0.1f, 0f, 500f, 200f, 0), 0f)
    assertEquals(0.5f, KnobMath.drag(0.5f, 0f, -10f, 200f, 4), 0f)  // 0.55 on 4 steps -> 0.5
    assertEquals(0.75f, KnobMath.drag(0.5f, 0f, -30f, 200f, 4), 0f) // 0.65 -> 0.75
  }

  @Test fun gridPicksTheColumnCountThatGivesTheLargestKnob() {
    val fit = KnobGrid.fit(n = 6, width = 220f, height = 200f)
    assertEquals(3, fit.columns)
    assertEquals(62f, fit.knob, 0.1f)  // limited by height: 200/2 rows - 30 label - 8 gap
  }

  @Test fun gridCapsTheSizeAndHandlesEmptyGroups() {
    assertEquals(KnobGrid.MAX, KnobGrid.fit(1, 500f, 500f).knob, 0f)
    assertEquals(KnobGrid.MAX, KnobGrid.fit(0, 10f, 10f).knob, 0f)
  }
}
```

**Step 2: `ui/KnobMath.kt`**

```kotlin
package com.criapix.volt.ui

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

object KnobMath {
  const val FINE = 10f

  /**
   * New normalized value after dragging (dx, dy) pixels from [start]. Up = more. [travel] pixels
   * of vertical drag cover the whole range; horizontal drag is [FINE] times slower.
   * [steps] > 0 snaps to that many intervals (a step parameter with steps+1 values).
   */
  fun drag(start: Float, dx: Float, dy: Float, travel: Float, steps: Int): Float {
    val raw = (start - dy / travel + dx / (FINE * travel)).coerceIn(0f, 1f)
    return if (steps > 0) (raw * steps).roundToInt().toFloat() / steps else raw
  }
}

object KnobGrid {
  const val MAX = 76f       // dp
  const val MIN = 52f       // dp; below this the group scrolls instead
  const val LABEL = 30f     // dp under each knob: label line + small value line
  const val GAP = 8f

  data class Fit(val columns: Int, val knob: Float)

  /** Columns and knob size (dp) for [n] knobs in a [width] x [height] dp area. */
  fun fit(n: Int, width: Float, height: Float): Fit {
    if (n <= 0) return Fit(1, MAX)
    var best = Fit(1, Float.NEGATIVE_INFINITY)
    for (c in 1..n) {
      val rows = ceil(n / c.toFloat())
      val size = min(min(width / c - GAP, height / rows - LABEL - GAP), MAX)
      if (size > best.knob) best = Fit(c, size)
    }
    return best
  }
}
```

**Step 3: `ui/Knob.kt`**

```kotlin
package com.criapix.volt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.Curve
import com.criapix.volt.Param
import com.criapix.volt.state.SynthState
import kotlin.math.cos
import kotlin.math.sin

private const val START_DEG = 135f
private const val SWEEP_DEG = 270f

@Composable
fun Knob(param: Param, ui: KnobUi, state: SynthState, size: Dp, onStep: () -> Unit) {
  val norm by state.value(param.id).collectAsStateWithLifecycle()
  var active by remember { mutableStateOf(false) }
  var lastUp by remember { mutableLongStateOf(0L) }
  val travel = with(LocalDensity.current) { 200.dp.toPx() }
  val steps = if (param.curve == Curve.STEP) (param.max - param.min).toInt() else 0
  val text = ParamUi.format(param, param.toReal(norm))

  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 8.dp)) {
    Box(contentAlignment = Alignment.Center) {
      Canvas(
        Modifier.size(size).pointerInput(param.id, travel) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val doubleTap = down.uptimeMillis - lastUp < 300
            val start = state.norm(param.id)
            var dx = 0f
            var dy = 0f
            var moved = 0f
            var shown = start
            active = true
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: break
              if (!change.pressed) { lastUp = change.uptimeMillis; break }
              val d = change.positionChange()
              change.consume()
              dx += d.x; dy += d.y; moved += d.getDistance()
              val n = KnobMath.drag(start, dx, dy, travel, steps)
              if (n != shown) {
                if (steps > 0) onStep()
                shown = n
                state.set(param.id, n)
              }
            }
            if (doubleTap && moved < 12f) { state.set(param.id, param.toNorm(param.def)); onStep() }
            active = false
          }
        },
      ) {
        val stroke = size.toPx() * 0.09f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        drawArc(VoltColors.track, START_DEG, SWEEP_DEG, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val from = if (ui.bipolar) 0.5f else 0f
        drawArc(
          if (active) VoltColors.text else VoltColors.accent,
          START_DEG + SWEEP_DEG * minOf(from, norm), SWEEP_DEG * kotlin.math.abs(norm - from),
          false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val angle = Math.toRadians((START_DEG + SWEEP_DEG * norm).toDouble())
        val r = this.size.minDimension / 2 - stroke * 1.6f
        val c = center
        drawLine(VoltColors.text, c, Offset(c.x + r * cos(angle).toFloat(), c.y + r * sin(angle).toFloat()), stroke * 0.6f, StrokeCap.Round)
      }
      if (active) {
        Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, with(LocalDensity.current) { -(size + 24.dp).roundToPx() })) {
          Text(
            text, color = VoltColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(VoltColors.raised, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
      }
    }
    Text(ui.label, fontSize = 12.sp, color = VoltColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    Text(text, fontSize = 10.sp, color = VoltColors.dim, maxLines = 1, textAlign = TextAlign.Center)
  }
}
```

**Step 4: `ui/Selector.kt`**

```kotlin
package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.Param
import com.criapix.volt.state.SynthState
import kotlin.math.roundToInt

/** Segmented buttons for a choice parameter (wave shape, filter model, voice mode). */
@Composable
fun Selector(param: Param, ui: ChoiceUi, state: SynthState, onStep: () -> Unit) {
  val norm by state.value(param.id).collectAsStateWithLifecycle()
  val selected = (param.toReal(norm) - param.min).roundToInt()
  Column {
    Text(ui.label, fontSize = 11.sp, color = VoltColors.dim)
    Row(Modifier.fillMaxWidth().height(34.dp).border(1.dp, VoltColors.track, RoundedCornerShape(6.dp))) {
      ui.options.forEachIndexed { i, option ->
        val on = i == selected
        Box(
          Modifier.weight(1f).height(34.dp)
            .background(if (on) VoltColors.accent else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { if (!on) { state.set(param.id, param.toNorm(param.min + i)); onStep() } },
          contentAlignment = Alignment.Center,
        ) {
          Text(option, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Clip, color = if (on) Color.Black else VoltColors.text)
        }
      }
    }
  }
}

/** An on/off chip for a 0..1 step parameter. */
@Composable
fun Toggle(param: Param, ui: ToggleUi, state: SynthState, onStep: () -> Unit) {
  val norm by state.value(param.id).collectAsStateWithLifecycle()
  val on = norm >= 0.5f
  Box(
    Modifier.height(30.dp)
      .background(if (on) VoltColors.accent else VoltColors.raised, RoundedCornerShape(15.dp))
      .clickable { state.set(param.id, if (on) 0f else 1f); onStep() },
    contentAlignment = Alignment.Center,
  ) {
    Text("  ${ui.label}  ", fontSize = 12.sp, color = if (on) Color.Black else VoltColors.text)
  }
}
```

**Step 5: Run** the Kotlin tests and `:app:assembleDebug` — pass and compile.

**Step 6: Commit** — `feat(app): touch knob, selector and toggle`.

---

### Task 13: Filter curve and envelope drawings

The drawings follow the parameters live. They are illustrations, not measurements: the filter curve is an analytic approximation (2-pole sections with a resonance Q), close enough to show slope, cutoff and resonance peak. The envelope drawing has three draggable handles (attack, decay/sustain, release) that change the same parameters as the knobs below it.

**Files:**
- Create: `ui/FilterCurve.kt`, `ui/EnvelopeGeometry.kt`, `ui/Drawings.kt`, `app/src/test/kotlin/com/criapix/volt/ui/FilterCurveTest.kt`, `ui/EnvelopeGeometryTest.kt`

**Step 1: Failing tests**

`FilterCurveTest.kt`

```kotlin
package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterCurveTest {
  private fun db(f: Float, model: Int, reso: Float = 0f, hpf: Float = 20f) = FilterCurve.magnitudeDb(f, 1000f, reso, model, hpf)

  @Test fun passbandIsFlat() {
    for (m in 0..3) assertEquals(0f, db(100f, m), 0.5f)
  }

  @Test fun slopesMatchTheModels() {
    val twelve = db(8000f, 1)
    assertTrue("12 dB model at 3 octaves: $twelve", twelve < -30f && twelve > -42f)
    for (m in listOf(0, 2, 3)) assertTrue("24 dB model $m: ${db(8000f, m)}", db(8000f, m) < -60f)
  }

  @Test fun resonancePeaksAtTheCutoff() {
    assertTrue(db(1000f, 2, reso = 0.9f) > 10f)
    assertTrue(db(1000f, 2, reso = 0f) < 0.5f)
  }

  @Test fun theHighPassCutsLows() {
    val v = FilterCurve.magnitudeDb(50f, 20000f, 0f, 1, 400f)
    assertTrue("$v", v < -15f && v > -21f)
  }

  @Test fun pointsAreLogSpaced() {
    val pts = FilterCurve.points(5, 1000f, 0f, 1, 20f)
    assertEquals(5, pts.size)
    assertEquals(0f, pts[0], 0.5f)
  }
}
```

`EnvelopeGeometryTest.kt`

```kotlin
package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvelopeGeometryTest {
  @Test fun segmentsShareTheWidth() {
    val p = EnvelopeGeometry.points(1f, 1f, 0.5f, 1f)
    assertEquals(EnvelopeGeometry.SEG, p.attackX, 1e-6f)
    assertEquals(2 * EnvelopeGeometry.SEG, p.decayX, 1e-6f)
    assertEquals(2 * EnvelopeGeometry.SEG + EnvelopeGeometry.HOLD, p.holdEndX, 1e-6f)
    assertEquals(1f, p.releaseX, 1e-6f)
    assertEquals(0.5f, p.sustain, 0f)
  }

  @Test fun nearestHandleIsFound() {
    val p = EnvelopeGeometry.points(0.5f, 0.5f, 0.5f, 0.5f)
    assertEquals(EnvelopeGeometry.Handle.ATTACK, EnvelopeGeometry.nearest(p, p.attackX, 0f))
    assertEquals(EnvelopeGeometry.Handle.DECAY_SUSTAIN, EnvelopeGeometry.nearest(p, p.decayX, 0.5f))
    assertEquals(EnvelopeGeometry.Handle.RELEASE, EnvelopeGeometry.nearest(p, p.releaseX, 1f))
  }

  @Test fun draggingMovesTheRightParameterAndClamps() {
    assertEquals(0.75f, EnvelopeGeometry.time(0.5f, 0.25f * EnvelopeGeometry.SEG), 1e-6f)
    assertEquals(1f, EnvelopeGeometry.time(0.9f, 1f), 0f)
    assertEquals(0.7f, EnvelopeGeometry.level(0.5f, -0.2f), 1e-6f)  // dragging up raises sustain
    assertEquals(0f, EnvelopeGeometry.level(0.1f, 0.5f), 0f)
  }
}
```

**Step 2: `ui/FilterCurve.kt`**

```kotlin
package com.criapix.volt.ui

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** Approximate magnitude response of the filter section, for drawing only. */
object FilterCurve {
  /** Resonance 0..1 -> Q of the resonant 2-pole section. */
  fun q(reso: Float) = 0.5f + 9.5f * reso * reso

  private fun twoPole(x: Float, q: Float): Float {
    val re = 1f - x * x
    val im = x / q
    return 1f / sqrt(re * re + im * im)
  }

  /** Gain in dB at [f] Hz. model: 0 Ladder, 1 Roland 12, 2 Roland 24, 3 Prophet; [hpf] is the 1-pole high-pass cutoff. */
  fun magnitudeDb(f: Float, cutoff: Float, reso: Float, model: Int, hpf: Float): Float {
    val x = f / cutoff
    var g = twoPole(x, q(reso))
    if (model != 1) g *= twoPole(x, 0.5f)  // the 24 dB models add a second, non-resonant section
    val xh = f / hpf
    g *= xh / sqrt(1f + xh * xh)
    return 20f * log10(g.coerceAtLeast(1e-6f))
  }

  /** [n] gains in dB at log-spaced frequencies from [fMin] to [fMax]. */
  fun points(n: Int, cutoff: Float, reso: Float, model: Int, hpf: Float, fMin: Float = 20f, fMax: Float = 20000f): FloatArray =
    FloatArray(n) { i -> magnitudeDb(fMin * (fMax / fMin).pow(i / (n - 1f)), cutoff, reso, model, hpf) }
}
```

Check: at x = 8 a 2-pole section is ≈ 1/63 → −36 dB, so the 12 dB model sits between −30 and −42, and the 24 dB models are ≈ −72 dB. A 1-pole high-pass at f = hpf/8 is ≈ −18 dB.

**Step 3: `ui/EnvelopeGeometry.kt`**

```kotlin
package com.criapix.volt.ui

import kotlin.math.abs

/**
 * Layout of the ADSR drawing in fractions of its width (x) and of the level (0 = silent, 1 = full).
 * Attack, decay and release each take up to [SEG] of the width, in proportion to their normalized
 * value (already logarithmic for times); the sustain plateau always takes [HOLD].
 */
object EnvelopeGeometry {
  const val SEG = 0.28f
  const val HOLD = 0.16f

  data class Points(val attackX: Float, val decayX: Float, val sustain: Float, val holdEndX: Float, val releaseX: Float)
  enum class Handle { ATTACK, DECAY_SUSTAIN, RELEASE }

  fun points(attack: Float, decay: Float, sustain: Float, release: Float): Points {
    val a = attack * SEG
    val d = a + decay * SEG
    val h = d + HOLD
    return Points(a, d, sustain, h, h + release * SEG)
  }

  /** Handle closest to (x, level). */
  fun nearest(p: Points, x: Float, level: Float): Handle {
    val da = abs(x - p.attackX) + abs(level - 1f)
    val dd = abs(x - p.decayX) + abs(level - p.sustain)
    val dr = abs(x - p.releaseX) + abs(level)
    return when (minOf(da, dd, dr)) { da -> Handle.ATTACK; dd -> Handle.DECAY_SUSTAIN; else -> Handle.RELEASE }
  }

  /** A time parameter after dragging its handle by [dxFraction] of the width. */
  fun time(start: Float, dxFraction: Float) = (start + dxFraction / SEG).coerceIn(0f, 1f)

  /** Sustain after dragging by [dyFraction] of the height (down is positive, so it lowers the level). */
  fun level(start: Float, dyFraction: Float) = (start - dyFraction).coerceIn(0f, 1f)
}
```

**Step 4: `ui/Drawings.kt`** — the two composables.

```kotlin
package com.criapix.volt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.state.SynthState
import kotlin.math.log10

private const val TOP_DB = 24f
private const val BOTTOM_DB = -48f

@Composable
fun FilterCurveView(state: SynthState, modifier: Modifier = Modifier) {
  val p = state.params
  val ids = listOf("filter.cutoff", "filter.reso", "filter.model", "hpf.cutoff").map { p[it]!!.id }
  // Reading the flows makes the drawing follow every change.
  val cut by state.value(ids[0]).collectAsStateWithLifecycle()
  val res by state.value(ids[1]).collectAsStateWithLifecycle()
  val mod by state.value(ids[2]).collectAsStateWithLifecycle()
  val hp by state.value(ids[3]).collectAsStateWithLifecycle()
  val cutoff = p.all[ids[0]].toReal(cut)
  val reso = p.all[ids[1]].toReal(res)
  val model = p.all[ids[2]].toReal(mod).toInt()
  val hpf = p.all[ids[3]].toReal(hp)

  Canvas(modifier.fillMaxSize()) {
    val n = 160
    val pts = FilterCurve.points(n, cutoff, reso, model, hpf)
    fun y(db: Float) = size.height * (TOP_DB - db.coerceIn(BOTTOM_DB, TOP_DB)) / (TOP_DB - BOTTOM_DB)
    drawLine(VoltColors.track, Offset(0f, y(0f)), Offset(size.width, y(0f)), 1f)
    val cx = size.width * (log10(cutoff / 20f) / 3f)  // 20 Hz .. 20 kHz = 3 decades
    drawLine(VoltColors.accent.copy(alpha = 0.5f), Offset(cx, 0f), Offset(cx, size.height), 2f)
    val path = Path()
    for (i in 0 until n) {
      val x = size.width * i / (n - 1f)
      if (i == 0) path.moveTo(x, y(pts[i])) else path.lineTo(x, y(pts[i]))
    }
    drawPath(path, VoltColors.accent2, style = Stroke(4f))
  }
}

/** ADSR drawing with draggable handles. [prefix] is "fenv" or "aenv". */
@Composable
fun EnvelopeView(state: SynthState, prefix: String, modifier: Modifier = Modifier) {
  val p = state.params
  val a = p["$prefix.attack"]!!.id
  val d = p["$prefix.decay"]!!.id
  val s = p["$prefix.sustain"]!!.id
  val r = p["$prefix.release"]!!.id
  val av by state.value(a).collectAsStateWithLifecycle()
  val dv by state.value(d).collectAsStateWithLifecycle()
  val sv by state.value(s).collectAsStateWithLifecycle()
  val rv by state.value(r).collectAsStateWithLifecycle()

  Canvas(
    modifier.fillMaxSize().pointerInput(prefix) {
      awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val pts = EnvelopeGeometry.points(state.norm(a), state.norm(d), state.norm(s), state.norm(r))
        val handle = EnvelopeGeometry.nearest(pts, down.position.x / size.width, 1f - down.position.y / size.height)
        val start = mapOf(a to state.norm(a), d to state.norm(d), s to state.norm(s), r to state.norm(r))
        var dx = 0f
        var dy = 0f
        while (true) {
          val ev = awaitPointerEvent()
          val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
          if (!ch.pressed) break
          val m = ch.positionChange(); ch.consume()
          dx += m.x / size.width; dy += m.y / size.height
          when (handle) {
            EnvelopeGeometry.Handle.ATTACK -> state.set(a, EnvelopeGeometry.time(start[a]!!, dx))
            EnvelopeGeometry.Handle.DECAY_SUSTAIN -> {
              state.set(d, EnvelopeGeometry.time(start[d]!!, dx))
              state.set(s, EnvelopeGeometry.level(start[s]!!, dy))
            }
            EnvelopeGeometry.Handle.RELEASE -> state.set(r, EnvelopeGeometry.time(start[r]!!, dx))
          }
        }
      }
    },
  ) {
    val pts = EnvelopeGeometry.points(av, dv, sv, rv)
    val w = size.width
    val h = size.height
    fun at(x: Float, level: Float) = Offset(x * w, h - level * h)
    val path = Path().apply {
      moveTo(0f, h)
      val pa = at(pts.attackX, 1f)
      quadraticTo(pa.x * 0.6f, pa.y + h * 0.15f, pa.x, pa.y)            // concave attack, like an RC charge
      val pd = at(pts.decayX, pts.sustain)
      quadraticTo(pa.x + (pd.x - pa.x) * 0.2f, pd.y, pd.x, pd.y)         // exponential decay
      val ph = at(pts.holdEndX, pts.sustain)
      lineTo(ph.x, ph.y)
      val pr = at(pts.releaseX, 0f)
      quadraticTo(ph.x + (pr.x - ph.x) * 0.2f, pr.y, pr.x, pr.y)
    }
    drawPath(path, VoltColors.accent2, style = Stroke(4f))
    for (o in listOf(at(pts.attackX, 1f), at(pts.decayX, pts.sustain), at(pts.releaseX, 0f))) {
      drawCircle(VoltColors.accent, 12f, o)
    }
  }
}
```

**Step 5: Run** the Kotlin tests and `:app:assembleDebug`.

**Step 6: Commit** — `feat(app): live filter curve and draggable envelope drawings`.

---

### Task 14: Groups, tabs and the main screen

**Files:**
- Create: `ui/GroupPanel.kt`, `ui/TopBar.kt`, `ui/VoltScreen.kt`, `ui/Diagnostics.kt`
- Delete: `app/src/main/kotlin/com/criapix/volt/DebugScreen.kt` (after moving `formatInfo`)

**Step 1: `ui/Diagnostics.kt`** — move `INFO_LABELS` and `formatInfo` here unchanged from `DebugScreen.kt` (package `com.criapix.volt.ui`). Then delete `DebugScreen.kt`. `MainActivity` will not compile until Task 16 rewires it. That is expected: do Steps 2–4 and keep going; the build is checked in Task 16.

**Step 2: `ui/GroupPanel.kt`** — renders any `Group` from `Panel`: choices and toggles first, then the drawing (if any), then the knobs on the grid from `KnobGrid.fit`.

```kotlin
package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criapix.volt.state.SynthState

@Composable
fun GroupPanel(group: Group, state: SynthState, onStep: () -> Unit, modifier: Modifier = Modifier) {
  val params = group.params.map { state.params[it]!! }
  val choices = params.filter { ParamUi.of(it.name) is ChoiceUi }
  val toggles = params.filter { ParamUi.of(it.name) is ToggleUi }
  val knobs = params.filter { ParamUi.of(it.name) is KnobUi }

  Column(modifier.background(VoltColors.panel, RoundedCornerShape(10.dp)).padding(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(group.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VoltColors.dim, modifier = Modifier.weight(1f))
      toggles.forEach { Toggle(it, ParamUi.of(it.name) as ToggleUi, state, onStep) }
    }
    choices.forEach { Selector(it, ParamUi.of(it.name) as ChoiceUi, state, onStep) }
    when (group.drawing) {
      Drawing.FILTER_CURVE -> FilterCurveView(state, Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp))
      Drawing.FENV -> EnvelopeView(state, "fenv", Modifier.weight(0.45f).fillMaxWidth().padding(vertical = 6.dp))
      Drawing.AENV -> EnvelopeView(state, "aenv", Modifier.weight(0.45f).fillMaxWidth().padding(vertical = 6.dp))
      Drawing.NONE -> {}
    }
    if (knobs.isNotEmpty()) {
      BoxWithConstraints(Modifier.weight(if (group.drawing == Drawing.NONE) 1f else 0.55f).fillMaxWidth()) {
        val fit = KnobGrid.fit(knobs.size, maxWidth.value, maxHeight.value)
        val scroll = fit.knob < KnobGrid.MIN
        val size = (if (scroll) KnobGrid.MIN else fit.knob).dp
        val columns = if (scroll) ((maxWidth.value / (KnobGrid.MIN + KnobGrid.GAP)).toInt()).coerceAtLeast(1) else fit.columns
        Column(
          Modifier.fillMaxSize().let { if (scroll) it.verticalScroll(rememberScrollState()) else it },
          verticalArrangement = Arrangement.SpaceEvenly,
        ) {
          knobs.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
              row.forEach { Knob(it, ParamUi.of(it.name) as KnobUi, state, size, onStep) }
              repeat(columns - row.size) { Box(Modifier.height(1.dp).padding(horizontal = (size / 2))) }
            }
          }
        }
      }
    }
  }
}
```

**Step 3: `ui/TopBar.kt`**

```kotlin
package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopBar(
  number: Int, name: String, category: String, dirty: Boolean,
  midiDevices: Int, midiLit: Boolean, voices: Int, level: Float,
  onPrev: () -> Unit, onNext: () -> Unit, onBrowse: () -> Unit,
  onSave: () -> Unit, onPanic: () -> Unit, onMenu: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().height(52.dp).background(VoltColors.panel).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    TextButton(onClick = onPrev) { Text("◀", fontSize = 20.sp) }
    Column(
      Modifier.weight(1f).fillMaxHeight().background(VoltColors.raised, RoundedCornerShape(8.dp)).clickable(onClick = onBrowse).padding(horizontal = 10.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (number > 0) "%03d ".format(number) else "--- ", fontSize = 13.sp, color = VoltColors.dim)
        Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (dirty) Text("  •  editado", fontSize = 12.sp, color = VoltColors.accent)
      }
      Text(categoryLabel(category), fontSize = 11.sp, color = VoltColors.dim)
    }
    TextButton(onClick = onNext) { Text("▶", fontSize = 20.sp) }
    Button(onClick = onSave) { Text("Salvar") }
    TextButton(onClick = onPanic) { Text("Pânico", color = VoltColors.danger) }
    Box(Modifier.size(10.dp).background(if (midiLit) VoltColors.accent else if (midiDevices > 0) VoltColors.track else VoltColors.raised, CircleShape))
    Text(if (midiDevices > 0) "MIDI" else "sem MIDI", fontSize = 11.sp, color = VoltColors.dim)
    Text("$voices vozes", fontSize = 11.sp, color = VoltColors.dim, modifier = Modifier.width(54.dp))
    Box(Modifier.width(56.dp).height(8.dp).background(VoltColors.raised, RoundedCornerShape(4.dp))) {
      Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0f, 1f)).background(if (level > 0.95f) VoltColors.danger else VoltColors.accent2, RoundedCornerShape(4.dp)))
    }
    TextButton(onClick = onMenu) { Text("⋮", fontSize = 22.sp) }
  }
}
```

(`"%03d".format(number)` is locale-safe for integers.)

**Step 4: `ui/VoltScreen.kt`** — top bar, tab row with the "external change" flash, the selected tab's groups side by side, and the dialogs of Task 15.

```kotlin
package com.criapix.volt.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.preset.AppModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** What the screen reads from outside the model; lambdas keep Native out of the UI code. */
class ScreenIo(
  val midiDevices: StateFlow<Int>,
  val midiActivity: StateFlow<Long>,
  val lastMidi: StateFlow<String>,
  val peak: () -> Float,
  val voices: () -> Int,
  val audioInfo: () -> String,
  val haptics: Haptics,
  val onExport: () -> Unit,
  val onImport: () -> Unit,
)

@Composable
fun VoltScreen(model: AppModel, io: ScreenIo) {
  val state = model.state
  val current by model.current.collectAsStateWithLifecycle()
  val presets by model.presets.collectAsStateWithLifecycle()
  val dirty by state.dirty.collectAsStateWithLifecycle()
  val external by state.external.collectAsStateWithLifecycle()
  val devices by io.midiDevices.collectAsStateWithLifecycle()
  val activity by io.midiActivity.collectAsStateWithLifecycle()
  val message by model.message.collectAsStateWithLifecycle()

  var tab by rememberSaveable { mutableStateOf(Tab.OSC) }
  var flash by remember { mutableStateOf<Tab?>(null) }
  var midiLit by remember { mutableStateOf(false) }
  var level by remember { mutableFloatStateOf(0f) }
  var voices by remember { mutableIntStateOf(0) }
  var browser by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var settings by remember { mutableStateOf(false) }

  // A MIDI controller moved a parameter on another tab: flash that tab.
  LaunchedEffect(external.seq) {
    val t = state.params.all.getOrNull(external.id)?.let { Panel.tabOf(it.name) } ?: return@LaunchedEffect
    if (t != tab) { flash = t; delay(600); flash = null }
  }
  LaunchedEffect(activity) { if (activity > 0) { midiLit = true; delay(120); midiLit = false } }
  LaunchedEffect(Unit) {
    var n = 0
    while (true) {
      level = maxOf(io.peak(), level * 0.85f)  // fast attack, slow fall
      if (n++ % 8 == 0) voices = io.voices()
      delay(33)
    }
  }
  LaunchedEffect(message) { if (message != null) { delay(3000); model.clearMessage() } }

  VoltTheme {
    Surface(Modifier.fillMaxSize(), color = VoltColors.background) {
      Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        TopBar(
          number = model.number(current.id), name = current.name, category = current.category, dirty = dirty,
          midiDevices = devices, midiLit = midiLit, voices = voices, level = level,
          onPrev = { model.step(-1) }, onNext = { model.step(1) }, onBrowse = { browser = true },
          onSave = { saving = true }, onPanic = { state.allNotesOff() }, onMenu = { settings = true },
        )
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Tab.entries.forEach { t ->
            val bg by animateColorAsState(
              when { t == tab -> VoltColors.accent; t == flash -> VoltColors.accent.copy(alpha = 0.45f); else -> VoltColors.panel },
              label = "tab",
            )
            Box(
              Modifier.weight(1f).fillMaxHeight().background(bg, RoundedCornerShape(8.dp)).clickable { tab = t; io.haptics.tick() },
              contentAlignment = Alignment.Center,
            ) {
              Text(t.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (t == tab) Color.Black else VoltColors.text)
            }
          }
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Panel.groups.getValue(tab).forEach { g ->
            GroupPanel(g, state, onStep = io.haptics::tick, modifier = Modifier.weight(g.weight).fillMaxHeight())
          }
        }
      }
      message?.let { m ->
        Box(Modifier.fillMaxSize().padding(bottom = 16.dp), contentAlignment = Alignment.BottomCenter) {
          Text(m, color = VoltColors.text, modifier = Modifier.background(VoltColors.raised, RoundedCornerShape(8.dp)).padding(12.dp))
        }
      }
      if (browser) PresetBrowser(model, io, onClose = { browser = false })
      if (saving) SaveDialog(model, onClose = { saving = false })
      if (settings) SettingsDialog(state, io, onClose = { settings = false })
    }
  }
}
```

`PresetBrowser`, `SaveDialog`, `SettingsDialog` and `Haptics` come in Tasks 15–16; the app compiles again at the end of Task 16.

**Step 5: Commit** (work in progress, does not build yet; say so in the message) — `wip(app): groups, top bar and main screen`.

---

### Task 15: Preset browser, save dialog, settings

**Files:**
- Create: `ui/BrowserFilter.kt`, `ui/PresetBrowser.kt`, `ui/Dialogs.kt`, `app/src/test/kotlin/com/criapix/volt/ui/BrowserFilterTest.kt`

**Step 1: Failing test — `BrowserFilterTest.kt`**

```kotlin
package com.criapix.volt.ui

import com.criapix.volt.preset.PresetRef
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserFilterTest {
  private val list = listOf(
    PresetRef("factory:1", "Baixo Gordo", "Bass"),
    PresetRef("factory:2", "Pad Analógico", "Pad"),
    PresetRef("user:a", "Meu baixo", "Bass"),
  )

  @Test fun filtersByShelf() {
    assertEquals(3, BrowserFilter.apply(list, Shelf.All, "", emptySet()).size)
    assertEquals(listOf("user:a"), BrowserFilter.apply(list, Shelf.User, "", emptySet()).map { it.id })
    assertEquals(listOf("factory:2"), BrowserFilter.apply(list, Shelf.Favorites, "", setOf("factory:2")).map { it.id })
    assertEquals(listOf("factory:1", "user:a"), BrowserFilter.apply(list, Shelf.Category("Bass"), "", emptySet()).map { it.id })
  }

  @Test fun searchIgnoresCaseAndAccents() {
    assertEquals(listOf("factory:2"), BrowserFilter.apply(list, Shelf.All, "analogico", emptySet()).map { it.id })
    assertEquals(listOf("factory:1", "user:a"), BrowserFilter.apply(list, Shelf.All, "BAIXO", emptySet()).map { it.id })
  }
}
```

**Step 2: `ui/BrowserFilter.kt`**

```kotlin
package com.criapix.volt.ui

import com.criapix.volt.preset.PresetCodec
import com.criapix.volt.preset.PresetRef
import java.text.Normalizer

sealed interface Shelf {
  val label: String
  data object All : Shelf { override val label = "Todos" }
  data object Favorites : Shelf { override val label = "Favoritos" }
  data object User : Shelf { override val label = "Meus presets" }
  data class Category(val key: String) : Shelf { override val label = categoryLabel(key) }

  companion object {
    val ALL: List<Shelf> = listOf(All, Favorites, User) + PresetCodec.CATEGORIES.map { Category(it) }
  }
}

object BrowserFilter {
  fun apply(list: List<PresetRef>, shelf: Shelf, query: String, favorites: Set<String>): List<PresetRef> {
    val q = fold(query.trim())
    return list.filter { ref ->
      when (shelf) {
        Shelf.All -> true
        Shelf.Favorites -> ref.id in favorites
        Shelf.User -> ref.isUser
        is Shelf.Category -> ref.category == shelf.key
      } && (q.isEmpty() || fold(ref.name).contains(q))
    }
  }

  private fun fold(s: String) =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
}
```

**Step 3: `ui/PresetBrowser.kt`** — a full-screen dialog: shelves on the left, search and import/export on top, preset cards in a grid. Tapping a card loads it at once (the dialog stays open so presets can be auditioned); the star toggles the favorite; long-pressing a user preset opens rename/delete.

```kotlin
package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.preset.AppModel
import com.criapix.volt.preset.PresetRef

@Composable
fun PresetBrowser(model: AppModel, io: ScreenIo, onClose: () -> Unit) {
  val presets by model.presets.collectAsStateWithLifecycle()
  val favorites by model.favorites.collectAsStateWithLifecycle()
  val current by model.current.collectAsStateWithLifecycle()
  var shelf by remember { mutableStateOf<Shelf>(Shelf.All) }
  var query by remember { mutableStateOf("") }
  var editing by remember { mutableStateOf<PresetRef?>(null) }
  val shown = BrowserFilter.apply(presets, shelf, query, favorites)

  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxSize(), color = VoltColors.background) {
      Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.width(150.dp).fillMaxHeight()) {
          items(Shelf.ALL) { s ->
            Text(
              s.label, fontSize = 14.sp,
              color = if (s == shelf) Color.Black else VoltColors.text,
              modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                .background(if (s == shelf) VoltColors.accent else Color.Transparent, RoundedCornerShape(6.dp))
                .clickable { shelf = s }.padding(10.dp),
            )
          }
        }
        Column(Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Buscar") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = io.onImport) { Text("Importar") }
            TextButton(onClick = io.onExport) { Text("Exportar") }
            TextButton(onClick = onClose) { Text("Fechar") }
          }
          if (shown.isEmpty()) Text("Nenhum preset aqui.", color = VoltColors.dim, modifier = Modifier.padding(16.dp))
          LazyVerticalGrid(GridCells.Adaptive(190.dp), Modifier.fillMaxSize().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { ref ->
              val selected = ref.id == current.id
              Row(
                Modifier.height(64.dp).background(VoltColors.panel, RoundedCornerShape(8.dp))
                  .border(2.dp, if (selected) VoltColors.accent else Color.Transparent, RoundedCornerShape(8.dp))
                  .pointerInput(ref.id) {
                    detectTapGestures(
                      onTap = { model.select(ref.id); io.haptics.tick() },
                      onLongPress = { if (ref.isUser) editing = ref },
                    )
                  }
                  .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(Modifier.weight(1f)) {
                  Text(ref.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text("${model.number(ref.id).toString().padStart(3, '0')} · ${categoryLabel(ref.category)} · ${if (ref.isUser) "meu" else "fábrica"}", fontSize = 11.sp, color = VoltColors.dim)
                }
                Text(
                  if (ref.id in favorites) "★" else "☆", fontSize = 22.sp,
                  color = if (ref.id in favorites) VoltColors.accent else VoltColors.dim,
                  modifier = Modifier.clickable { model.toggleFavorite(ref.id) }.padding(8.dp),
                )
              }
            }
          }
        }
      }
    }
  }
  editing?.let { ref -> EditPresetDialog(model, ref, onClose = { editing = null }) }
}

/** Rename or delete one user preset. */
@Composable
private fun EditPresetDialog(model: AppModel, ref: PresetRef, onClose: () -> Unit) {
  var name by remember { mutableStateOf(ref.name) }
  var confirmDelete by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text(if (confirmDelete) "Apagar \"${ref.name}\"?" else "Editar preset") },
    text = { if (!confirmDelete) OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true) else Text("Não dá para desfazer.") },
    confirmButton = {
      if (confirmDelete) TextButton(onClick = { model.delete(ref.id); onClose() }) { Text("Apagar", color = VoltColors.danger) }
      else TextButton(onClick = { model.rename(ref.id, name); onClose() }) { Text("Renomear") }
    },
    dismissButton = {
      Row {
        if (!confirmDelete) TextButton(onClick = { confirmDelete = true }) { Text("Apagar…", color = VoltColors.danger) }
        TextButton(onClick = onClose) { Text("Cancelar") }
      }
    },
  )
}
```

**Step 4: `ui/Dialogs.kt`** — save dialog, settings dialog and the "all parameters" list (the old debug sliders, now bound to `SynthState` so they and the knobs agree).

```kotlin
package com.criapix.volt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.Curve
import com.criapix.volt.Param
import com.criapix.volt.preset.AppModel
import com.criapix.volt.preset.PresetCodec
import com.criapix.volt.state.SynthState
import kotlinx.coroutines.delay

@Composable
fun SaveDialog(model: AppModel, onClose: () -> Unit) {
  val current = model.current.value
  var name by remember { mutableStateOf(current.name) }
  var category by remember { mutableStateOf(current.category) }
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text("Salvar preset") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          (listOf("") + PresetCodec.CATEGORIES).forEach { key ->
            Text(
              categoryLabel(key), fontSize = 13.sp,
              color = if (key == category) Color.Black else VoltColors.text,
              modifier = Modifier.background(if (key == category) VoltColors.accent else VoltColors.raised, RoundedCornerShape(14.dp))
                .clickable { category = key }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }
        if (!model.canOverwrite()) Text("Presets de fábrica não podem ser alterados: salve como novo.", fontSize = 12.sp, color = VoltColors.dim)
      }
    },
    confirmButton = {
      Row {
        if (model.canOverwrite()) TextButton(onClick = { model.save(name, category); onClose() }) { Text("Substituir") }
        TextButton(onClick = { model.saveAs(name, category); onClose() }) { Text("Salvar como novo") }
      }
    },
    dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
  )
}

@Composable
fun SettingsDialog(state: SynthState, io: ScreenIo, onClose: () -> Unit) {
  var info by remember { mutableStateOf(io.audioInfo()) }
  var allParams by remember { mutableStateOf(false) }
  val last by io.lastMidi.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { while (true) { info = io.audioInfo(); delay(500) } }
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text("Configurações") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Áudio", color = VoltColors.dim, fontSize = 12.sp)
        Text(formatInfo(info), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text("Última mensagem MIDI: ${last.ifEmpty { "nenhuma" }}", fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Vibração", modifier = Modifier.weight(1f))
          var on by remember { mutableStateOf(io.haptics.enabled) }
          Switch(on, { on = it; io.haptics.enabled = it })
        }
        TextButton(onClick = { allParams = true }) { Text("Todos os parâmetros (lista)") }
      }
    },
    confirmButton = { TextButton(onClick = onClose) { Text("Fechar") } },
  )
  if (allParams) AllParamsDialog(state, onClose = { allParams = false })
}

/** Every parameter as a slider: a fallback view, and a way to reach anything quickly. */
@Composable
private fun AllParamsDialog(state: SynthState, onClose: () -> Unit) {
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxSize(), color = VoltColors.background) {
      Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Todos os parâmetros", fontSize = 18.sp, modifier = Modifier.weight(1f))
          TextButton(onClick = onClose) { Text("Fechar") }
        }
        LazyColumn(Modifier.fillMaxWidth()) { items(state.params.all, key = { it.id }) { ParamRow(it, state) } }
      }
    }
  }
}

@Composable
private fun ParamRow(p: Param, state: SynthState) {
  val norm by state.value(p.id).collectAsStateWithLifecycle()
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(p.name, Modifier.width(170.dp), fontSize = 13.sp)
    Slider(
      value = norm, onValueChange = { state.set(p.id, it) },
      steps = if (p.curve == Curve.STEP) (p.max - p.min).toInt() - 1 else 0,
      modifier = Modifier.weight(1f),
    )
    Text(ParamUi.format(p, p.toReal(norm)), Modifier.width(90.dp), fontSize = 13.sp)
  }
}
```

**Step 5: Run** `.\gradlew.bat :app:testDebugUnitTest` — `BrowserFilterTest` passes. (The app build is checked in Task 16.)

**Step 6: Commit** — `wip(app): preset browser, save and settings dialogs`.

---

### Task 16: Wiring — MainActivity, preferences, haptics, full screen

**Files:**
- Create: `app/src/main/kotlin/com/criapix/volt/Prefs.kt`, `app/src/main/kotlin/com/criapix/volt/ui/Haptics.kt`
- Modify: `MainActivity.kt`, `AppLog.kt` (version string), `app/build.gradle.kts` (version)

**Step 1: `Prefs.kt`**

```kotlin
package com.criapix.volt

import android.content.Context
import com.criapix.volt.preset.FavoriteStore

class Prefs(context: Context) : FavoriteStore {
  private val p = context.getSharedPreferences("volt", Context.MODE_PRIVATE)

  var haptics: Boolean
    get() = p.getBoolean("haptics", true)
    set(v) = p.edit().putBoolean("haptics", v).apply()

  // getStringSet returns a set that must not be modified; copy it.
  override fun load(): Set<String> = p.getStringSet("favorites", emptySet())!!.toSet()
  override fun save(ids: Set<String>) = p.edit().putStringSet("favorites", HashSet(ids)).apply()
}
```

**Step 2: `ui/Haptics.kt`** — adapted from the reference `"C:\Users\criap\OneDrive\Documentos\Github\mvave fm1\app\src\main\kotlin\com\criapix\fm1droid\panel\Haptics.kt"`, reduced to a tick:

```kotlin
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
```

Add `<uses-permission android:name="android.permission.VIBRATE" />` to `AndroidManifest.xml` (the reference has it; check).

**Step 3: `MainActivity.kt`** — replace the class body. Keep `NativeSynth` (with `loadPatch` from Task 4) and the `AUDIO` logging loop from plan 1, now as a coroutine in `lifecycleScope`.

```kotlin
class MainActivity : ComponentActivity() {
  private lateinit var model: AppModel
  private lateinit var dispatch: MidiDispatch
  private lateinit var midi: MidiInput
  private lateinit var prefs: Prefs

  private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri == null) return@registerForActivityResult
    runCatching { contentResolver.openOutputStream(uri)?.use { it.write(model.exportBank().toByteArray()) } }
      .onSuccess { model.showMessage("Presets exportados") }
      .onFailure { model.showMessage("Não foi possível exportar") }
  }

  private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri == null) return@registerForActivityResult
    val text = runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
    if (text == null) model.showMessage("Não foi possível ler o arquivo") else model.importText(text)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppLog.init(this)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    prefs = Prefs(this)
    val params = Params.parse(Native.paramTable())
    val state = SynthState(params, NativeSynth)
    val library = PresetLibrary(AssetFactory(assets), File(filesDir, "presets/user"), prefs)
    model = AppModel(state, library, File(filesDir, "session.json")) { AppLog.log(it) }
    model.restore()

    dispatch = MidiDispatch(state, params) { AppLog.log(it) }  // MIDI changes go through SynthState
    val parser = MidiParser { bytes, len -> dispatch.message(bytes, len) }
    midi = MidiInput(this, onDisconnect = { Native.allNotesOff() }) { bytes, offset, count -> parser.feed(bytes, offset, count) }

    val io = ScreenIo(
      midiDevices = midi.count, midiActivity = dispatch.activity, lastMidi = dispatch.lastMessage,
      peak = Native::peak, voices = Native::activeVoices, audioInfo = Native::audioInfo,
      haptics = Haptics(this, prefs),
      onExport = { exportLauncher.launch("volt-presets.json") },
      onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
    )
    setContent { VoltScreen(model, io) }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) {
          AppLog.log("AUDIO " + Native.audioInfo().trim().replace('\n', ' '))
          delay(1000)
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    val ok = Native.start()
    AppLog.log("ciclo de vida: onStart; Native.start() = $ok")
    midi.start()
  }

  override fun onStop() {
    AppLog.log("ciclo de vida: onStop; AUDIO " + Native.audioInfo().trim().replace('\n', ' '))
    model.persist()
    midi.stop()
    Native.allNotesOff()
    Native.stop()
    AppLog.flush()
    super.onStop()
  }
}
```

Imports this needs (add what the compiler asks for): `androidx.activity.result.contract.ActivityResultContracts`, `androidx.core.view.WindowCompat`, `WindowInsetsCompat`, `WindowInsetsControllerCompat`, `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.lifecycleScope`, `androidx.lifecycle.repeatOnLifecycle`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.delay`, `java.io.File`, and the app's `state`, `preset`, `ui` classes. `AppLog`'s old per-parameter slider logging is gone. Preset actions are logged by `AppModel`; MIDI is logged by `MidiDispatch`.

**Step 4: Version** — `app/build.gradle.kts`: `versionCode = 2`, `versionName = "0.2.0"`. In `AppLog.init`, replace the hard-coded `app 0.1.0` with the real version:

```kotlin
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
```

and use `app $version` in the session line.

**Step 5: Build and test everything**

```powershell
. .\tools\env.ps1
.\tools\test-engine.ps1
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

All engine and Kotlin tests pass; the APK builds.

**Step 6: Look at it on the emulator, at the S25 Ultra's size and at 16:9**

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell wm size 1440x3120; adb shell wm density 560     # S25 Ultra
adb shell am start -n com.criapix.volt/.MainActivity
adb exec-out screencap -p > build\osc.png
```

Then tap each tab (find coordinates from the screenshot) and screenshot each: `build\filtro.png`, `build\env.png`, `build\mod.png`. Open each PNG with the Read tool and check:
- landscape, full screen (no status/navigation bar), nothing hidden by the camera cutout;
- top bar shows `001 Init` (first start) and the category `—`;
- OSC: four groups side by side, every knob has a label and value, no text overlaps;
- FILTRO: curve drawn and a vertical line at the cutoff;
- ENV: two envelope drawings above their knobs;
- MOD: four groups; write down in NOTES.md whether any group fell back to scrolling at this size.

Interact with `adb shell input`:
- drag a knob up: `adb shell input swipe X Y X (Y-150) 400` → screenshot: the value changed and the top bar shows `• editado`;
- tap `▶` → the top bar shows `002 Baixo Gordo`, `• editado` gone, and the OSC knobs moved to the preset's values;
- tap the preset name → the browser opens with 10 cards; tap `Pad Analógico` → it is highlighted; tap ☆ → becomes ★; `Favoritos` shelf shows it;
- `Salvar` → dialog → "Salvar como novo" with name `Teste` → the top bar shows `Teste`, number `011`;
- `adb shell am force-stop com.criapix.volt` (onStop runs only on a normal stop; use `adb shell input keyevent KEYCODE_HOME` first, then force-stop), start again → `Teste` comes back.

Repeat the OSC and MOD screenshots at 16:9 (`adb shell wm size 1080x1920; adb shell wm density 420`) and note what scrolls. Reset with `adb shell wm size reset; adb shell wm density reset`.

`adb logcat -d -s AndroidRuntime volt` must show no crash. Pull the app log (`.\tools\pull-log.ps1`) and check `PRESET` lines appear.

**Step 7: Commit** — `feat(app): touch interface with presets replaces the debug screen`.

---

### Task 17: Notes, device checklist, README

**Files:**
- Modify: `docs/NOTES.md`, `docs/DEVICE-TEST.md`, `README.md`

**Step 1: `docs/NOTES.md`** — update `## Estado` to plan 2 (what exists now, what does not: MIDI Learn, Program Change, effects, arpeggiator, on-screen keyboard, full bank), with the test counts you saw. Add a section:

```markdown
## Plano 2 (tela de toque e presets) — 2026-11-12
- Mudanças no motor: compensação de ganho do Ladder e do Prophet (+5,8 dB, os quatro modelos agora têm ganho ~1 na banda passante); sync no estilo Prophet (o osc 1 segue o osc 2; parâmetro renomeado de `osc2.sync` para `osc1.sync`); troca de preset atômica, com fade de ~5 ms quando há vozes soando.
- Presets: JSON com nome permanente do parâmetro → valor real; parâmetro ausente = padrão do motor. **Depois de publicar, não mudar um padrão em `params.def` sem revisar os presets de fábrica.**
- Banco de fábrica: 10 presets iniciais escritos por valores, sem audição. O banco completo é o plano 5.
- Tamanhos de knob observados no emulador: <S25 Ultra: …; 16:9: … (quais grupos rolam)>.
- Verificado por: <N> testes do motor, <N> testes Kotlin, capturas de tela no emulador. **Nada foi tocado nem ouvido no celular.**
```

**Step 2: `docs/DEVICE-TEST.md`** — add a `# Teste no aparelho (plano 2)` section above the plan 1 one (keep plan 1's for history):

```markdown
# Teste no aparelho (plano 2: tela e presets)

Instalar: `adb install -r app\build\outputs\apk\debug\app-debug.apk`. Depois do teste, rodar `tools\pull-log.ps1`.

| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 1 | Abrir o app | Tela cheia em paisagem; barra superior com preset; abas OSC · FILTRO · ENV · MOD | |
| 2 | Girar knobs arrastando para cima/baixo | Responde ao dedo sem pular; o valor aparece grande acima do knob | |
| 3 | Arrastar para o lado depois de tocar | Ajuste fino (bem mais lento) | |
| 4 | Dois toques num knob | Volta ao valor padrão | |
| 5 | Dois knobs ao mesmo tempo com dois dedos | Os dois se movem | |
| 6 | Tamanho dos knobs e textos | Anotar: confortável / pequeno demais; alguma aba rola? | |
| 7 | Aba FILTRO: mexer em cutoff e ressonância | A curva acompanha; o som acompanha | |
| 8 | Aba ENV: arrastar os pontos do desenho | Os knobs abaixo acompanham | |
| 9 | ◀ ▶ com uma nota soando (pedal) | Troca de som sem estalo; a nota continua | |
| 10 | Ouvir os 10 presets de fábrica com o CK61 | Anotar por preset: bom / ajustar (o quê) / ruim | |
| 11 | Lead Sync: tocar e segurar | O timbre "varre" no ataque sem mudar a nota (sync com poly-mod) | |
| 12 | Trocar o modelo do filtro com o mesmo preset | Volume parecido entre os quatro modelos | |
| 13 | CK61: mexer no knob que manda CC 74 (cutoff) estando na aba OSC | A aba FILTRO pisca; na aba FILTRO, o knob Cutoff gira junto | |
| 14 | Salvar como novo, renomear, favoritar, apagar | Tudo funciona; o preset apagado some da lista | |
| 15 | Exportar e importar (navegador de presets) | Arquivo criado; importar de volta cria cópias "(2)" | |
| 16 | Fechar o app (botão home) com edição não salva e abrir de novo | Volta no mesmo preset, com a edição e o "• editado" | |
| 17 | Vibração | Tique nos seletores, abas e knobs de passo; desligável em ⋮ | |
```

**Step 3: `README.md`** — update `## Estado` to plan 2 in one paragraph, and add a short `## Presets` section: where user presets live (inside the app; use Exportar for backup), the file format in one sentence, factory presets are read-only.

**Step 4: Full verification**

```powershell
.\tools\test-engine.ps1
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
git status
```

All pass; no untracked build output.

**Step 5: Commit** — `docs: state, device checklist and README for plan 2`.

**Step 6: Finish the branch** — use superpowers:finishing-a-development-branch (plan 1 was merged into `main` locally; do the same unless the user says otherwise).

**Step 7: Final report to the user** (Portuguese): what was built; the test counts you saw pass; what the screenshots showed at both sizes (and which groups scroll); the two engine changes (gain compensation, Prophet-style sync and the parameter rename) and why; that nothing was touched or heard on the phone; and that the next step is theirs: `docs/DEVICE-TEST.md` (plan 2), especially presets by ear (step 10) and knob comfort (step 6), because both feed plans 3 and 5.

---

## 99. What comes after this plan

| Plan | Design sections | Contents | Depends on from this plan |
|---|---|---|---|
| 3 — MIDI Learn | 6.3 | Learn mode (MIDI button in the top bar; tap a knob, move a CK61 control), soft takeover, Program Change in library order, preset previous/next as learnable actions, mapping screen, defaults for the CK61's CC 72/73/91/92/93/13/14/15 | `SynthState` (every learned CC calls `setParam`, so the screen already follows), `AppModel.step/select/number` |
| 4 — Effects, arpeggiator, keyboard | 4.3, 4.4, 5.4 | Six effects, arpeggiator, ARP and FX tabs (new `Panel` entries; `PanelTest` forces it), keyboard drawer | `Panel`, `ParamUi`, `GroupPanel` |
| 5 — Factory bank and polish | 7, 8 | ~64 factory presets tuned with the user's feedback from step 10, a render test per preset, polyphony reduction under overload, app icon, release build | `PresetCodec`, `FactoryPresetsTest` |
