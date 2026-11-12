# Volt Plan 4 — Effects, Arpeggiator and On-screen Keyboard Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add the six effects of the design (distortion → phaser → flanger → chorus → delay → reverb), the arpeggiator, the ARP and FX tabs, and a slide-up on-screen keyboard. Give the factory presets that need effects (pads, strings, electric piano, organ, leads) their effects, and add three presets that show the new features.

**Architecture:** Engine first. A new `FxChain` processes the summed stereo voices once per 16-sample block, before the master volume and the limiter. Reverb, delay, flanger and phaser come from `surge-synthesizer/sst-effects`, as the design prefers. The chorus (Roland-ensemble style) and the distortion are written here, because `sst-effects` has no chorus (its `Chorus.h` is an empty stub) and no distortion in its effect set. A new `Arp` class sits between incoming keys and the voices, with its clock counted in samples. Every new control is an ordinary engine parameter, so presets, MIDI Learn, the knobs and the "all parameters" list pick them up with no special code. The keyboard is UI only: it calls `noteOn`/`noteOff` like the CK61 does, so it goes through the arpeggiator too.

**Tech Stack:** C++20 engine, `sst-effects` (git submodule, header-only, GPL-3), existing `sst-basic-blocks` / `sst-filters` / `simde`; Kotlin 2.3 + Jetpack Compose; JUnit 4.

---

## 0. Read this first

**Design document:** `docs/plans/2026-11-11-volt-design.md`, sections 4.3 (effects), 4.4 (arpeggiator), 5.2 (ARP and FX tabs) and 5.4 (keyboard). Plans 1–3 are in `docs/plans/`. Their section 0 has the machine facts and rules, which all still hold:

- Repository `C:\Users\criap\OneDrive\Documentos\Github\volt-synth`, branch `main`. Work on `feature/fx-arp-keyboard`; merge locally at the end. Do not push.
- `. .\tools\env.ps1` before building. Engine tests: `.\tools\test-engine.ps1 [filter]`. App: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.
- **Read `docs/NOTES.md` whole first.** It records the sst-filters integration (submodule hashes, how simde is picked up on arm64), the BOM trap with PowerShell, the pt-BR locale trap in tests, `ValueUnit` (not `Unit`) in the `ui` package with `kotlin.Unit` written explicitly, and the `lineHeight` rule for small texts. With two `adb` devices attached, always pass `-s emulator-5554` (or whatever `adb devices` lists) so nothing is sent to the user's phone.
- Audio-thread rules: no allocation, locks, I/O, logging or JNI inside `Engine::render`; `test_alloc.cpp` enforces it, and this plan extends it to the effects and the arpeggiator.
- Code here was written without a compiler. Fix what does not compile. When a test fails, find the cause in the code first; change an expectation only with a written reason in `docs/NOTES.md`.
- Code, comments and commits in English; on-screen text in Brazilian Portuguese; NOTES/DEVICE-TEST in Portuguese. End every commit message with the co-author line your session's instructions give you.
- **Honesty rule:** the emulator cannot be heard. Effects and arpeggiator are verified by measurements in engine tests (echo arrival time, reverb decay, harmonics, step timing); how they *sound* is the user's judgment on the phone (Task 14's checklist).

### What the research found about `sst-effects` (2026-11-12, main branch, not compiled yet)

- Header-only (`INTERFACE` CMake target). It needs `sst-basic-blocks` and `sst-filters` (both already vendored) plus its own `include/sst/effects-shared`. Its CPM fetches happen only when its tests are built, and this project never uses its CMake.
- Effects present: `Reverb1`, `Reverb2`, `Delay` ("Dual Delay"), `Flanger`, `Phaser`, `Bonsai`, `Nimbus`, `RotarySpeaker`, `TreeMonster`, `FloatyDelay`. **`Chorus.h` is an empty stub. There is no distortion** among the effects.
- Each effect is a template on a config type. `sst::effects::core::ConcreteConfig` (in the repo) is a ready config: `blockSize = 16` (the same as Volt's `kBlock`), never temposynced, parameters in a plain `float` array. Usage from upstream's `tests/concrete-runs.cpp`:
  ```cpp
  auto gs = sst::effects::core::ConcreteConfig::GlobalStorage(48000);
  auto es = sfx::core::ConcreteConfig::EffectStorage();
  auto fx = std::make_unique<FX>(&gs, &es, nullptr);
  for (int i = 0; i < FX::numParams; ++i) fx->paramStorage[i] = fx->paramAt(i).defaultVal;
  fx->initialize();
  fx->processBlock(L, R);   // 16 frames, in place
  ```
- `ConcreteConfig.h` has no `#include` lines of its own but uses `std::round`, `rand`, `pow`: include `<cmath>` and `<cstdlib>` before it.
- Memory: `Reverb2` holds about **14.5 MB** of in-object buffers and `Delay` about 2 MB. Both must be allocated off the audio thread (the `FxChain` constructor), never on the stack. `initialize()` / `suspendProcessing()` memset those buffers: never call them on the audio thread.
- **`Phaser` allocates** (`new BiquadFilter`) in its constructor and again whenever its stage count rises, from inside `processBlock`. Fix: set the stage count once before the first block, and run one block in the constructor. `test_alloc` then proves nothing allocates later.
- `paramAt(i)` builds `std::string`s: call it only at construction.
- Licence GPL-3.0-or-later, the same as Volt.

### Decisions taken while writing this plan (report them to the user at the end)

1. **Effect sources** as above: four from `sst-effects`, chorus and distortion written here. The design allows this ("se um efeito da biblioteca for difícil de isolar, escrever uma versão própria simples é aceitável"); here two simply do not exist in the library.
2. **Controls per effect (2–4 each, design: "2 a 3 controles" plus on/off).** Distortion: drive, tone, mix. Phaser: rate, depth, feedback, mix. Flanger: rate, depth, feedback, mix. Chorus: mode (I, II, I+II, like the Juno) and mix. Delay: time, feedback, mix. Reverb: decay, damping, mix. Everything else inside the sst effects stays at a fixed setting chosen in Task 3.
3. **On/off with a crossfade.** Switching an effect on or off fades it over ~21 ms, so no click. An effect that is off costs no CPU. When every effect is off, the output is bit-identical to plan 3's, so existing presets sound the same.
4. **Factory MIDI controllers for effects, following General MIDI:** CC 91 → reverb mix, CC 93 → chorus mix, CC 95 → phaser mix. The CK61 sends 91 and 93. Moving one of these controllers above zero also switches its effect on, otherwise turning the "reverb" knob would do nothing on a dry preset. CC 92 (GM tremolo) stays unassigned, since Volt has no tremolo effect; it can be learned.
5. **Arpeggiator behaviour.** Modes up, down, up-down (ends not repeated), random, as played. 1–4 octaves; tempo 40–240 BPM; divisions 1/4, 1/8, 1/16 and their triplets; gate 5–100 %. **Hold** latches the chord: releasing the keys keeps it running, and the next chord pressed after all keys were released replaces it. Switching the arpeggiator on while keys are held hands those keys to it, and switching it off hands them back as normal notes, so no note ever sticks. The sustain pedal does not lengthen arpeggio notes. Arpeggio steps are applied at 16-sample resolution (0.33 ms).
6. **Keyboard drawer:** two octaves plus the top C (15 white keys), octave buttons, multitouch, sliding between keys (glissando), velocity from how far down the key is touched (top soft, bottom loud). It covers the lower part of the panel while open.
7. **Presets:** existing factory files keep their names and order; effects are added inside the files that need them. Three new presets go at the **end** of the factory list (positions 41–43), so Program Change numbers of the existing 40 do not change. User presets move down by 3 positions.

### Layout at the end of this plan (new and changed files only)

```
.gitmodules                                   third_party/sst-effects/  (submodule)
engine/CMakeLists.txt
engine/src/params.def  engine/src/engine.h  engine/src/engine.cpp
engine/src/arp.h
engine/src/fx/fx_chain.h  engine/src/fx/fx_chain.cpp  engine/src/fx/fx_sst.cpp  engine/src/fx/fx_sst.h
engine/src/fx/chorus.h  engine/src/fx/distortion.h
engine/tests/test_arp.cpp  engine/tests/test_fx.cpp  engine/tests/fx_dump.cpp  engine/tests/test_engine.cpp
engine/tests/test_alloc.cpp  engine/tests/bench.cpp
app/build.gradle.kts                          (0.4.0)
app/src/main/assets/presets/factory/          (edits + 090, 091, 092)
app/src/main/kotlin/com/criapix/volt/
  midi/MidiDispatch.kt
  ui/ParamUi.kt  ui/Panel.kt  ui/Keyboard.kt  ui/TopBar.kt  ui/VoltScreen.kt
app/src/test/kotlin/com/criapix/volt/
  ParamsTest.kt  midi/MidiRoutingTest.kt  ui/ParamUiTest.kt  ui/KeyboardLayoutTest.kt  preset/FactoryPresetsTest.kt
```

---

### Task 1: New parameters

The whole plan hangs off these names; they are permanent once presets use them.

**Files:**
- Modify: `engine/src/params.def`, `app/src/test/kotlin/com/criapix/volt/ParamsTest.kt`

**Step 1: Append to `engine/src/params.def`** (after `master.volume`; appending keeps every existing id unchanged):

```cpp
// Effects, in signal order. Every effect: on/off (Step) plus its controls.
P(kFxDistOn,       "fx.dist.on",        0, 1, 0, Step)
P(kFxDistDrive,    "fx.dist.drive",     0, 1, 0.4f, Lin)
P(kFxDistTone,     "fx.dist.tone",      500, 12000, 6000, Exp)  // Hz, low-pass after the shaper
P(kFxDistMix,      "fx.dist.mix",       0, 1, 1, Lin)
P(kFxPhaserOn,     "fx.phaser.on",      0, 1, 0, Step)
P(kFxPhaserRate,   "fx.phaser.rate",    0.05f, 8, 0.4f, Exp)    // Hz
P(kFxPhaserDepth,  "fx.phaser.depth",   0, 1, 0.7f, Lin)
P(kFxPhaserFb,     "fx.phaser.feedback",0, 1, 0.4f, Lin)
P(kFxPhaserMix,    "fx.phaser.mix",     0, 1, 0.5f, Lin)
P(kFxFlangerOn,    "fx.flanger.on",     0, 1, 0, Step)
P(kFxFlangerRate,  "fx.flanger.rate",   0.05f, 8, 0.25f, Exp)   // Hz
P(kFxFlangerDepth, "fx.flanger.depth",  0, 1, 0.6f, Lin)
P(kFxFlangerFb,    "fx.flanger.feedback",0, 1, 0.5f, Lin)
P(kFxFlangerMix,   "fx.flanger.mix",    0, 1, 0.5f, Lin)
P(kFxChorusOn,     "fx.chorus.on",      0, 1, 0, Step)
P(kFxChorusMode,   "fx.chorus.mode",    0, 2, 0, Step)          // 0 I, 1 II, 2 I+II
P(kFxChorusMix,    "fx.chorus.mix",     0, 1, 0.5f, Lin)
P(kFxDelayOn,      "fx.delay.on",       0, 1, 0, Step)
P(kFxDelayTime,    "fx.delay.time",     0.02f, 2, 0.375f, Exp)  // seconds
P(kFxDelayFb,      "fx.delay.feedback", 0, 0.95f, 0.35f, Lin)
P(kFxDelayMix,     "fx.delay.mix",      0, 1, 0.25f, Lin)
P(kFxReverbOn,     "fx.reverb.on",      0, 1, 0, Step)
P(kFxReverbDecay,  "fx.reverb.decay",   0.3f, 12, 2.5f, Exp)    // seconds
P(kFxReverbDamp,   "fx.reverb.damp",    0, 1, 0.5f, Lin)
P(kFxReverbMix,    "fx.reverb.mix",     0, 1, 0.25f, Lin)

// Arpeggiator
P(kArpOn,       "arp.on",       0, 1, 0, Step)
P(kArpMode,     "arp.mode",     0, 4, 0, Step)       // 0 up, 1 down, 2 up-down, 3 random, 4 as played
P(kArpOctaves,  "arp.octaves",  1, 4, 1, Step)
P(kArpDivision, "arp.division", 0, 5, 1, Step)       // 0 1/4, 1 1/8, 2 1/16, 3 1/4T, 4 1/8T, 5 1/16T
P(kArpBpm,      "arp.bpm",      40, 240, 120, Step)
P(kArpGate,     "arp.gate",     0.05f, 1, 0.5f, Lin)
P(kArpHold,     "arp.hold",     0, 1, 0, Step)
```

That is 32 new parameters, 99 in total.

**Step 2:** In `ParamsTest.theRealTableIsReadFromTheEngineSource`, change `67` to `99`.

**Step 3: Run** `.\tools\test-engine.ps1 params` (the table tests check ranges and defaults) and `.\gradlew.bat :app:testDebugUnitTest`.

Expected: engine `params_` tests pass. Kotlin: `ParamsTest` passes, but **`PanelTest` and `ParamUiTest` fail**: the new parameters have no place on screen yet. That is the coverage net working; Task 11 makes them pass. Do not commit a red Kotlin suite for long: commit this task with the message noting it, and go straight on.

**Step 4: Commit** — `feat(engine): effect and arpeggiator parameters (UI coverage pending)`.

---

### Task 2: Arpeggiator core

A self-contained class: no engine types, so it is tested alone.

**Files:**
- Create: `engine/src/arp.h`, `engine/tests/test_arp.cpp`
- Modify: `engine/CMakeLists.txt` (add `tests/test_arp.cpp` to `volt_tests`)

**Step 1: Failing tests — `engine/tests/test_arp.cpp`**

```cpp
#include "arp.h"
#include "test.h"
#include <vector>

using namespace volt;

namespace {
struct Ev { bool on; int note; long at; };

// Runs the arpeggiator for `samples` in 16-sample blocks and returns events with absolute times.
struct ArpRig {
  Arp a;
  ArpSettings s{0, 1, 1, 120.0f, 0.5f};  // up, 1 octave, 1/8, 120 BPM, 50 % gate
  long now = 0;
  ArpRig() { a.setSampleRate(48000.0f); }
  std::vector<Ev> run(long samples) {
    std::vector<Ev> out;
    ArpEvent ev[8];
    for (long t = 0; t < samples; t += 16) {
      int n = a.advance(16, s, ev, 8);
      for (int i = 0; i < n; ++i) out.push_back({ev[i].on, ev[i].note, now + ev[i].at});
      now += 16;
    }
    return out;
  }
};
std::vector<int> ons(const std::vector<Ev>& v) { std::vector<int> r; for (auto& e : v) if (e.on) r.push_back(e.note); return r; }
}  // namespace

TEST(arp_first_note_is_immediate_and_steps_follow_the_tempo) {
  ArpRig r;
  r.a.keyDown(60, 0.8f);
  auto ev = r.run(48000);  // 1 s at 120 BPM, 1/8 = 0.25 s per step
  std::vector<long> onAt, offAt;
  for (auto& e : ev) (e.on ? onAt : offAt).push_back(e.at);
  CHECK(onAt.size() == 4);
  for (size_t i = 0; i < onAt.size(); ++i) CHECK_NEAR(onAt[i], 12000.0 * i, 16);
  CHECK(offAt.size() >= 3);
  CHECK_NEAR(offAt[0], 6000, 16);  // 50 % gate
}

TEST(arp_triplets_are_two_thirds_of_the_step) {
  ArpRig r;
  r.s.division = 4;  // 1/8T
  r.a.keyDown(60, 0.8f);
  auto ev = r.run(24000);
  std::vector<long> onAt;
  for (auto& e : ev) if (e.on) onAt.push_back(e.at);
  CHECK(onAt.size() == 3);
  CHECK_NEAR(onAt[1], 8000, 16);
}

TEST(arp_up_spans_the_octaves_in_pitch_order) {
  ArpRig r;
  r.s.octaves = 2;
  r.a.keyDown(64, 1); r.a.keyDown(60, 1); r.a.keyDown(67, 1);
  auto n = ons(r.run(12000L * 7));
  std::vector<int> want = {60, 64, 67, 72, 76, 79, 60};
  CHECK(n == want);
}

TEST(arp_down_and_up_down) {
  ArpRig r;
  r.s.mode = 1;
  r.a.keyDown(60, 1); r.a.keyDown(64, 1); r.a.keyDown(67, 1);
  auto down = ons(r.run(12000L * 3));
  CHECK((down == std::vector<int>{67, 64, 60}));
  ArpRig u;
  u.s.mode = 2;
  u.a.keyDown(60, 1); u.a.keyDown(64, 1); u.a.keyDown(67, 1);
  auto ud = ons(u.run(12000L * 6));
  CHECK((ud == std::vector<int>{60, 64, 67, 64, 60, 64}));
}

TEST(arp_as_played_keeps_the_key_order) {
  ArpRig r;
  r.s.mode = 4;
  r.a.keyDown(64, 1); r.a.keyDown(60, 1); r.a.keyDown(67, 1);
  CHECK((ons(r.run(12000L * 3)) == std::vector<int>{64, 60, 67}));
}

TEST(arp_random_stays_inside_the_chord_and_varies) {
  ArpRig r;
  r.s.mode = 3;
  r.a.keyDown(60, 1); r.a.keyDown(64, 1); r.a.keyDown(67, 1);
  auto n = ons(r.run(12000L * 40));
  bool varied = false;
  for (size_t i = 0; i < n.size(); ++i) {
    CHECK(n[i] == 60 || n[i] == 64 || n[i] == 67);
    if (i && n[i] != n[i - 1]) varied = true;
  }
  CHECK(varied);
}

TEST(arp_releasing_every_key_stops_and_releases_the_sounding_note) {
  ArpRig r;
  r.s.gate = 1.0f;
  r.a.keyDown(60, 1);
  r.run(3000);
  r.a.keyUp(60);
  auto ev = r.run(48000);
  CHECK(ev.size() == 1);
  CHECK(!ev[0].on && ev[0].note == 60);
}

TEST(arp_hold_latches_and_a_new_chord_replaces_it) {
  ArpRig r;
  r.a.setHold(true);
  r.a.keyDown(60, 1); r.a.keyDown(64, 1);
  r.a.keyUp(60); r.a.keyUp(64);
  CHECK((ons(r.run(12000L * 2)) == std::vector<int>{60, 64}));
  r.a.keyDown(67, 1);  // every key was up: this starts a new chord
  auto n = ons(r.run(12000L * 3));
  for (int x : n) CHECK(x == 67);
  r.a.keyUp(67);
  r.a.setHold(false);  // nothing physically held any more: stops
  r.run(12000);
  CHECK(ons(r.run(48000)).empty());
}

TEST(arp_clear_reports_the_sounding_note) {
  ArpRig r;
  r.s.gate = 1.0f;
  r.a.keyDown(62, 1);
  r.run(100);
  CHECK(r.a.clear() == 62);
  CHECK(r.a.clear() == -1);
  CHECK(r.run(48000).empty());
}

TEST(arp_velocity_follows_the_key) {
  Arp a;
  a.setSampleRate(48000.0f);
  a.keyDown(60, 0.3f);
  ArpEvent ev[8];
  ArpSettings s{0, 1, 1, 120.0f, 0.5f};
  int n = a.advance(16, s, ev, 8);
  CHECK(n == 1);
  CHECK_NEAR(ev[0].vel, 0.3f, 1e-6);
}
```

**Step 2: Run** `.\tools\test-engine.ps1 arp` — build error.

**Step 3: `engine/src/arp.h`**

```cpp
#pragma once
#include <cstring>
#include "util.h"

namespace volt {

struct ArpSettings {
  int mode;      // 0 up, 1 down, 2 up-down, 3 random, 4 as played
  int octaves;   // 1..4
  int division;  // 0 1/4, 1 1/8, 2 1/16, 3 1/4T, 4 1/8T, 5 1/16T
  float bpm;
  float gate;    // 0.05..1 of a step
};

struct ArpEvent {
  bool on;
  int note;
  float vel;
  int at;  // sample offset inside the advance() call
};

// Turns held keys into a timed note sequence. Allocation-free; audio thread only.
class Arp {
 public:
  static constexpr int kMaxKeys = 16;

  void setSampleRate(float sr) { sr_ = sr; }

  void keyDown(int note, float vel) {
    if (note < 0 || note > 127) return;
    if (hold_ && !anyDown()) heldN_ = 0;  // latched chord + a fresh press: start a new chord
    down_[note] = true;
    remove(note);
    if (heldN_ == kMaxKeys) remove(held_[0]);
    held_[heldN_] = note;
    vel_[heldN_] = vel;
    ++heldN_;
  }

  void keyUp(int note) {
    if (note < 0 || note > 127) return;
    down_[note] = false;
    if (!hold_) remove(note);
  }

  void setHold(bool on) {
    if (hold_ && !on)
      for (int i = heldN_ - 1; i >= 0; --i) if (!down_[held_[i]]) remove(held_[i]);
    hold_ = on;
  }

  // Forgets everything. Returns the note still sounding (the caller releases it), or -1.
  int clear() {
    const int s = sounding_;
    heldN_ = 0; sounding_ = -1; running_ = false;
    std::memset(down_, 0, sizeof(down_));
    return s;
  }

  int advance(int samples, const ArpSettings& s, ArpEvent* out, int maxOut) {
    int n = 0;
    if (heldN_ == 0) {
      if (sounding_ >= 0 && n < maxOut) out[n++] = {false, sounding_, 0.0f, 0};
      sounding_ = -1;
      running_ = false;
      return n;
    }
    static const double kBeats[6] = {1.0, 0.5, 0.25, 2.0 / 3.0, 1.0 / 3.0, 1.0 / 6.0};
    const double stepLen = (double)sr_ * 60.0 / (double)s.bpm * kBeats[clampi(s.division, 0, 5)];
    if (!running_) { running_ = true; stepIn_ = 0; frac_ = 0.0; index_ = 0; }

    int t = 0;
    while (true) {
      const int toOff = sounding_ >= 0 ? offIn_ : 1 << 30;
      const int next = toOff < stepIn_ ? toOff : stepIn_;
      if (next >= samples - t) {
        const int left = samples - t;
        stepIn_ -= left;
        if (sounding_ >= 0) offIn_ -= left;
        return n;
      }
      t += next;
      stepIn_ -= next;
      if (sounding_ >= 0) offIn_ -= next;
      if (sounding_ >= 0 && offIn_ <= 0 && n < maxOut) { out[n++] = {false, sounding_, 0.0f, t}; sounding_ = -1; }
      if (stepIn_ <= 0) {
        if (sounding_ >= 0 && n < maxOut) { out[n++] = {false, sounding_, 0.0f, t}; sounding_ = -1; }
        float vel = 1.0f;
        const int note = pick(s, vel);
        if (n < maxOut) out[n++] = {true, note, vel, t};
        sounding_ = note;
        frac_ += stepLen;
        stepIn_ = (int)frac_;
        frac_ -= stepIn_;
        offIn_ = (int)(s.gate * (float)stepIn_);
        if (offIn_ < 1) offIn_ = 1;
      }
    }
  }

 private:
  static int clampi(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

  bool anyDown() const { for (bool d : down_) if (d) return true; return false; }

  void remove(int note) {
    for (int i = 0; i < heldN_; ++i)
      if (held_[i] == note) {
        std::memmove(held_ + i, held_ + i + 1, sizeof(int) * (heldN_ - i - 1));
        std::memmove(vel_ + i, vel_ + i + 1, sizeof(float) * (heldN_ - i - 1));
        --heldN_;
        return;
      }
  }

  // The next note of the pattern, built from the held keys each step (cheap: at most 64 entries).
  int pick(const ArpSettings& s, float& vel) {
    int base[kMaxKeys];
    float bvel[kMaxKeys];
    std::memcpy(base, held_, sizeof(int) * heldN_);
    std::memcpy(bvel, vel_, sizeof(float) * heldN_);
    if (s.mode != 4)  // pitch order (insertion sort, tiny)
      for (int i = 1; i < heldN_; ++i)
        for (int j = i; j > 0 && base[j] < base[j - 1]; --j) {
          int tn = base[j]; base[j] = base[j - 1]; base[j - 1] = tn;
          float tv = bvel[j]; bvel[j] = bvel[j - 1]; bvel[j - 1] = tv;
        }
    int up[kMaxKeys * 4];
    float upVel[kMaxKeys * 4];
    int len = 0;
    const int oct = clampi(s.octaves, 1, 4);
    for (int o = 0; o < oct; ++o)
      for (int k = 0; k < heldN_; ++k) {
        int note = base[k] + 12 * o;
        if (note > 127) continue;
        up[len] = note; upVel[len] = bvel[k]; ++len;
      }
    if (len == 0) { vel = 0.0f; return base[0]; }
    int i;
    switch (s.mode) {
      case 1: i = len - 1 - (index_ % len); break;
      case 2: {
        const int cycle = len > 1 ? 2 * len - 2 : 1;
        const int c = index_ % cycle;
        i = c < len ? c : cycle - c;
        break;
      }
      case 3: i = (int)(rng_.next() % (uint32_t)len); break;
      default: i = index_ % len; break;
    }
    ++index_;
    vel = upVel[i];
    return up[i];
  }

  float sr_ = 48000.0f;
  int held_[kMaxKeys];
  float vel_[kMaxKeys];
  int heldN_ = 0;
  bool down_[128] = {};
  bool hold_ = false;
  bool running_ = false;
  int sounding_ = -1;
  int stepIn_ = 0, offIn_ = 0, index_ = 0;
  double frac_ = 0.0;
  Rng rng_;
};

}  // namespace volt
```

Check against the tests: in `arp_up_spans…` the keys are pressed 64, 60, 67; pitch order is 60, 64, 67, then +12. In up-down, `cycle = 4` for three notes gives indexes 0,1,2,1,0,1 → 60,64,67,64,60,64.

**Step 4: Run** `.\tools\test-engine.ps1 arp` — all pass.

**Step 5: Commit** — `feat(engine): arpeggiator core`.

---

### Task 3: sst-effects spike — vendor, compile on PC and NDK, dump the parameters

Like plan 1's filter spike: prove the library builds for **both** MSVC x64 and NDK arm64/x86_64 before writing the wrapper, and learn each effect's real parameter indexes and units.

**Files:**
- Create: `third_party/sst-effects` (submodule), `engine/tests/fx_dump.cpp`
- Modify: `engine/CMakeLists.txt`

**Step 1: Vendor**

```powershell
git submodule add https://github.com/surge-synthesizer/sst-effects.git third_party/sst-effects
```

Add its include path to `volt_engine` in `engine/CMakeLists.txt`, next to the others:

```cmake
  ${VOLT_THIRD_PARTY}/sst-effects/include
```

Do not use its CMake.

**Step 2: Dump program — `engine/tests/fx_dump.cpp`.** It instantiates the four effects with `ConcreteConfig` and prints every parameter, so the mapping in Task 4 is built from facts, not guesses.

```cpp
// Prints every parameter of the sst effects Volt uses: index, name, min, max, default.
#include <cmath>
#include <cstdlib>
#include <cstdio>
#include <memory>
#include "sst/basic-blocks/simd/setup.h"
#include "sst/effects/ConcreteConfig.h"
#include "sst/effects/Delay.h"
#include "sst/effects/Flanger.h"
#include "sst/effects/Phaser.h"
#include "sst/effects/Reverb2.h"

namespace sfx = sst::effects;
using Cfg = sfx::core::ConcreteConfig;

template <typename FX>
void dump(const char* title) {
  Cfg::GlobalStorage gs(48000);
  Cfg::EffectStorage es;
  auto fx = std::make_unique<FX>(&gs, &es, nullptr);
  std::printf("== %s (%d params)\n", title, (int)FX::numParams);
  for (int i = 0; i < FX::numParams; ++i) {
    auto m = fx->paramAt(i);
    std::printf("%2d  %-28s min %10.4f  max %10.4f  default %10.4f\n", i, m.name.c_str(), m.minVal, m.maxVal, m.defaultVal);
  }
}

int main() {
  dump<sfx::reverb2::Reverb2<Cfg>>("Reverb2");
  dump<sfx::delay::Delay<Cfg>>("Delay");
  dump<sfx::flanger::Flanger<Cfg>>("Flanger");
  dump<sfx::phaser::Phaser<Cfg>>("Phaser");
  return 0;
}
```

(`ParamMetaData` field names `name`, `minVal`, `maxVal`, `defaultVal`: verify in `third_party/sst-basic-blocks/include/sst/basic-blocks/params/ParamMetadata.h` and fix the `printf` if they differ. Also print the unit/display type if the struct has one: it tells whether a time is stored in seconds or log2 seconds.)

In `engine/CMakeLists.txt`, inside `if (VOLT_TESTS)`:

```cmake
  add_executable(fx_dump tests/fx_dump.cpp)
  target_link_libraries(fx_dump PRIVATE volt_engine)
  target_include_directories(fx_dump SYSTEM PRIVATE
    ${VOLT_THIRD_PARTY}/sst-effects/include ${VOLT_THIRD_PARTY}/sst-filters/include
    ${VOLT_THIRD_PARTY}/sst-basic-blocks/include ${VOLT_THIRD_PARTY}/simde)
```

**Step 3: Spike A — MSVC.** `.\tools\test-engine.ps1`, then `.\build\engine\fx_dump.exe > build\fx_dump.txt`. Expected: four tables. Typical first problems:
- a missing header → find which repo it lives in (`effects-shared` is inside sst-effects; tables/clippers/block-ops in sst-basic-blocks) and confirm the include path;
- `ConcreteConfig` errors about `round`/`rand`/`pow` → the `<cmath>`/`<cstdlib>` includes must come before it;
- `static_assert(__cplusplus >= 202002L)` → `/Zc:__cplusplus` is already set for MSVC; check `fx_dump` inherits it (it links `volt_engine`, whose options are PUBLIC on MSVC).

**Step 4: Spike B — NDK, compile only.** Temporarily add `tests/fx_dump.cpp` to the `volt_engine` sources (or create a scratch `fx_spike.cpp` that includes the same four headers and instantiates each effect inside a function) and build the library for both ABIs as plan 1 did:

```powershell
$ndk = "$env:ANDROID_HOME\ndk\30.0.16248370"
foreach ($abi in "arm64-v8a", "x86_64") {
  cmake -S engine -B "build\ndk-$abi" -G Ninja "-DCMAKE_TOOLCHAIN_FILE=$ndk\build\cmake\android.toolchain.cmake" "-DANDROID_ABI=$abi" -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release
  cmake --build "build\ndk-$abi"
}
```

Expected: builds for both. Remove the temporary source afterwards; Task 4's `fx_sst.cpp` replaces it.

**Plan B, per effect, if one does not build after about an hour of work.** Write that one effect in plain C++ inside `fx_sst.cpp` behind the same `SstEffects` interface (Task 4), keeping the black-box tests of Task 5 unchanged:
- Reverb: a Dattorro plate (Jon Dattorro, "Effect Design Part 1", 1997): input diffusion of 4 allpasses, a figure-eight tank with 2 modulated allpasses and 2 delays, decay from the RT60 formula `g = 10^(-3·loopTime/decay)`, a one-pole damping filter in the tank.
- Delay: a stereo delay line of `2 s × sr` samples with linear interpolation, feedback through a one-pole low-pass at 6 kHz.
- Flanger: 0.5–10 ms modulated delay, triangle LFO, feedback.
- Phaser: 4 first-order allpasses swept between 200 Hz and 4 kHz (exponential), feedback from the last stage.
If **two or more** fail, stop and record the build errors in NOTES.md before going on: that is a decision for the user (a fully custom effect set), not something to slide through.

**Step 5: Write the parameter tables** from `build\fx_dump.txt` into `docs/NOTES.md` under `## Efeitos (sst-effects)`, with the submodule hash (`git submodule status`).

**Step 6: Commit** — `build(engine): vendor sst-effects; parameter dump`.

---

### Task 4: Effect implementations — sst wrappers, chorus, distortion

**Files:**
- Create: `engine/src/fx/fx_sst.h`, `engine/src/fx/fx_sst.cpp`, `engine/src/fx/chorus.h`, `engine/src/fx/distortion.h`
- Modify: `engine/CMakeLists.txt` (add `src/fx/fx_sst.cpp` to `volt_engine`)

All the heavy sst headers live in **one** translation unit, `fx_sst.cpp`, behind a small interface, so the rest of the engine never includes them.

**Step 1: `engine/src/fx/fx_sst.h`**

```cpp
#pragma once
#include <memory>

namespace volt {

// The four effects taken from sst-effects. Construction allocates ~17 MB and is slow:
// never construct or call setSampleRate on the audio thread. process*() are allocation-free.
class SstEffects {
 public:
  explicit SstEffects(float sampleRate);
  ~SstEffects();

  // One 16-frame block, in place. Arguments are Volt's real parameter values.
  void phaser(float* L, float* R, float rateHz, float depth, float feedback, float mix);
  void flanger(float* L, float* R, float rateHz, float depth, float feedback, float mix);
  void delay(float* L, float* R, float timeSec, float feedback, float mix);
  void reverb(float* L, float* R, float decaySec, float damp, float mix);

 private:
  struct Impl;
  std::unique_ptr<Impl> impl_;
};

}  // namespace volt
```

**Step 2: `engine/src/fx/fx_sst.cpp`** — the indexes and unit conversions come from Task 3's dump. The code below is the shape to fill in; every `// MAP:` line is a decision that the dump settles. The expectations in the comments come from Surge's own parameter types (times and rates usually stored as log2 seconds / log2 Hz, mix as 0..1) **and must be checked**: Task 5's black-box tests (an echo at exactly the delay time, the reverb's decay) catch a wrong mapping.

```cpp
#include "fx_sst.h"

#include <cmath>
#include <cstdlib>
#include "sst/basic-blocks/simd/setup.h"
#include "sst/effects/ConcreteConfig.h"
#include "sst/effects/Delay.h"
#include "sst/effects/Flanger.h"
#include "sst/effects/Phaser.h"
#include "sst/effects/Reverb2.h"

namespace volt {

namespace sfx = sst::effects;
using Cfg = sfx::core::ConcreteConfig;

namespace {
template <typename FX>
struct Slot {
  Cfg::GlobalStorage gs;
  Cfg::EffectStorage es;
  std::unique_ptr<FX> fx;
  explicit Slot(float sr) : gs(sr), fx(std::make_unique<FX>(&gs, &es, nullptr)) {
    for (int i = 0; i < FX::numParams; ++i) fx->paramStorage[i] = fx->paramAt(i).defaultVal;
  }
  void set(int idx, float v) { fx->paramStorage[idx] = v; }
  void start() {
    fx->initialize();
    alignas(16) float l[Cfg::blockSize] = {}, r[Cfg::blockSize] = {};
    fx->processBlock(l, r);  // first block off the audio thread: lazy setup (Phaser allocates here)
  }
};
inline float log2s(float x) { return std::log2(x); }
}  // namespace

struct SstEffects::Impl {
  Slot<sfx::phaser::Phaser<Cfg>> phaser;
  Slot<sfx::flanger::Flanger<Cfg>> flanger;
  Slot<sfx::delay::Delay<Cfg>> delay;
  Slot<sfx::reverb2::Reverb2<Cfg>> reverb;

  explicit Impl(float sr) : phaser(sr), flanger(sr), delay(sr), reverb(sr) {
    // Fixed settings Volt does not expose (decision 2). MAP: indexes and values from the dump.
    //   Phaser:  stages = 4 (classic), Q/sharpness default, spread default, width default, modulation wave sine.
    //   Flanger: mode = classic, wave = triangle, voices = 1, base pitch giving ~2-5 ms, damping low, width full.
    //   Delay:   both sides the same time (no left/right offset), crossfeed 0, low cut ~120 Hz, high cut ~8 kHz,
    //            modulation depth 0, input stereo, width full, feedback clipping "soft" if the mode exists.
    //   Reverb2: room size middle, predelay ~20 ms, diffusion/buildup defaults, LF damping 0, width full.
    phaser.start(); flanger.start(); delay.start(); reverb.start();
  }
};

SstEffects::SstEffects(float sr) : impl_(std::make_unique<Impl>(sr)) {}
SstEffects::~SstEffects() = default;

void SstEffects::phaser(float* L, float* R, float rate, float depth, float fb, float mix) {
  auto& s = impl_->phaser;
  // MAP: s.set(<rate idx>, log2s(rate));  s.set(<depth idx>, depth);
  //      s.set(<feedback idx>, fb * <max feedback from dump>);  s.set(<mix idx>, mix);
  s.fx->processBlock(L, R);
}

void SstEffects::flanger(float* L, float* R, float rate, float depth, float fb, float mix) {
  auto& s = impl_->flanger;
  // MAP: rate (log2 Hz?), depth 0..1, feedback 0..1 (check the dump's max: Surge may allow > 1), mix.
  //      If the dump shows the flanger's mix as bipolar (-1..1), map Volt's 0..1 to 0..1 of it (positive side).
  s.fx->processBlock(L, R);
}

void SstEffects::delay(float* L, float* R, float time, float fb, float mix) {
  auto& s = impl_->delay;
  // MAP: left time = right time = log2s(time) (or seconds, per the dump); feedback; mix.
  s.fx->processBlock(L, R);
}

void SstEffects::reverb(float* L, float* R, float decay, float damp, float mix) {
  auto& s = impl_->reverb;
  // MAP: decay time = log2s(decay) (or seconds); high-frequency damping = damp; mix.
  s.fx->processBlock(L, R);
}

}  // namespace volt
```

When filling in the `MAP` lines, write each index as a named `constexpr int` at the top of the file (`kPhRate = …`) with a comment quoting the dump line, not as bare numbers.

**Step 3: `engine/src/fx/chorus.h`** — a Juno-style stereo chorus: one delay line, two taps swept in opposite phase by a triangle LFO, a dark wet signal (bucket-brigade character). The rates and delay range are the commonly published Juno-60 values (I: 0.513 Hz, II: 0.863 Hz, I+II: 9.75 Hz with a much shallower sweep; delay 1.66–5.35 ms).

```cpp
#pragma once
#include <cstring>
#include "util.h"

namespace volt {

class Chorus {
 public:
  void setSampleRate(float sr) {
    sr_ = sr;
    lp_ = 1.0f - std::exp(-6.28318530718f * 9000.0f / sr);  // dark, like a BBD line
    std::memset(buf_, 0, sizeof(buf_));
    pos_ = 0; phase_ = 0.0f; wl_ = wr_ = 0.0f;
  }

  // mode 0 I, 1 II, 2 I+II. In place, kBlock frames.
  void process(float* L, float* R, int mode, float mix) {
    static const float kRate[3] = {0.513f, 0.863f, 9.75f};
    static const float kMinMs[3] = {1.66f, 1.66f, 3.3f};
    static const float kMaxMs[3] = {5.35f, 5.35f, 3.7f};
    if (mode < 0 || mode > 2) mode = 0;
    const float inc = kRate[mode] / sr_;
    const float lo = kMinMs[mode] * 0.001f * sr_, hi = kMaxMs[mode] * 0.001f * sr_;
    for (int s = 0; s < kBlock; ++s) {
      const float mono = 0.5f * (L[s] + R[s]);
      buf_[pos_] = mono;
      const float tri = phase_ < 0.5f ? 4.0f * phase_ - 1.0f : 3.0f - 4.0f * phase_;  // -1..1
      phase_ += inc;
      if (phase_ >= 1.0f) phase_ -= 1.0f;
      const float mid = 0.5f * (lo + hi), half = 0.5f * (hi - lo);
      wl_ += lp_ * (read(mid + half * tri) - wl_);
      wr_ += lp_ * (read(mid - half * tri) - wr_);
      // The dry signal never fully disappears, as on the hardware.
      L[s] = L[s] * (1.0f - 0.5f * mix) + wl_ * mix;
      R[s] = R[s] * (1.0f - 0.5f * mix) + wr_ * mix;
      pos_ = (pos_ + 1) & (kSize - 1);
    }
  }

 private:
  static constexpr int kSize = 1024;  // > 5.35 ms at 96 kHz (514 samples)
  float read(float delay) const {
    float p = (float)pos_ - delay;
    while (p < 0.0f) p += (float)kSize;
    const int i = (int)p;
    const float f = p - (float)i;
    return buf_[i & (kSize - 1)] * (1.0f - f) + buf_[(i + 1) & (kSize - 1)] * f;
  }
  float buf_[kSize] = {};
  float sr_ = 48000.0f, lp_ = 0.5f, phase_ = 0.0f, wl_ = 0.0f, wr_ = 0.0f;
  int pos_ = 0;
};

}  // namespace volt
```

The tests only require that `mix = 0` leaves the signal unchanged and that L and R differ for a mono input.

**Step 4: `engine/src/fx/distortion.h`** — gain, an asymmetric soft clipper (odd plus a little even harmonic, like an overdriven stage), a one-pole tone low-pass, level compensation, dry/wet mix. No oversampling in v1 (noted as a known limit).

```cpp
#pragma once
#include "util.h"

namespace volt {

class Distortion {
 public:
  void setSampleRate(float sr) { sr_ = sr; zl_ = zr_ = 0.0f; }

  void process(float* L, float* R, float drive, float toneHz, float mix) {
    const float gain = std::pow(10.0f, (drive * 36.0f) / 20.0f);  // 0..36 dB
    const float comp = 1.0f / std::sqrt(gain);                   // keeps the level roughly constant
    const float g = 1.0f - std::exp(-6.28318530718f * toneHz / sr_);
    for (int s = 0; s < kBlock; ++s) {
      zl_ += g * (shape(L[s] * gain) - zl_);
      zr_ += g * (shape(R[s] * gain) - zr_);
      L[s] += mix * (zl_ * comp * 2.0f - L[s]);
      R[s] += mix * (zr_ * comp * 2.0f - R[s]);
    }
  }

 private:
  static float shape(float x) {
    x += 0.15f;                       // bias: a little even harmonic
    const float y = std::tanh(x);
    return y - 0.148885f;             // tanh(0.15): remove the DC the bias adds
  }
  float sr_ = 48000.0f, zl_ = 0.0f, zr_ = 0.0f;
};

}  // namespace volt
```

**Step 5: Build** (`.\tools\test-engine.ps1`) — everything compiles; nothing uses these yet.

**Step 6: Commit** — `feat(engine): effect implementations (sst wrappers, chorus, distortion)`.

---

### Task 5: The effect chain

**Files:**
- Create: `engine/src/fx/fx_chain.h`, `engine/src/fx/fx_chain.cpp`, `engine/tests/test_fx.cpp`
- Modify: `engine/CMakeLists.txt` (`src/fx/fx_chain.cpp`, `tests/test_fx.cpp`)

**Step 1: Failing tests — `engine/tests/test_fx.cpp`.** All black-box: they read the output, not sst internals. Delay timing, reverb decay and so on prove that Task 4's parameter mapping is right.

```cpp
#include "dsp_probe.h"
#include "fx/fx_chain.h"
#include "params.h"
#include "test.h"
#include <vector>

using namespace volt;

namespace {
struct FxRig {
  FxChain fx{48000.0f};
  float p[kNumParams];
  FxRig() { for (int i = 0; i < kNumParams; ++i) p[i] = kParams[i].def; }
  // Feeds `in` (mono, copied to both sides) and returns left and right outputs.
  std::pair<std::vector<float>, std::vector<float>> run(const std::vector<float>& in) {
    std::vector<float> l(in), r(in);
    const size_t n = in.size() / kBlock * kBlock;
    for (size_t i = 0; i < n; i += kBlock) fx.process(p, l.data() + i, r.data() + i);
    l.resize(n); r.resize(n);
    return {l, r};
  }
};
std::vector<float> noise(int n, float amp = 0.3f) { Rng g; std::vector<float> v(n); for (auto& x : v) x = amp * g.bipolar(); return v; }
std::vector<float> sine(int n, float hz, float amp = 0.5f) { std::vector<float> v(n); for (int i = 0; i < n; ++i) v[i] = amp * std::sin(6.28318530718f * hz * i / 48000.0f); return v; }
std::vector<float> impulse(int n) { std::vector<float> v(n, 0.0f); v[0] = 1.0f; return v; }
double diffRms(const std::vector<float>& a, const std::vector<float>& b) { double s = 0; for (size_t i = 0; i < a.size(); ++i) s += (a[i] - b[i]) * (a[i] - b[i]); return std::sqrt(s / a.size()); }
const int kOn[6] = {kFxDistOn, kFxPhaserOn, kFxFlangerOn, kFxChorusOn, kFxDelayOn, kFxReverbOn};
const int kMix[6] = {kFxDistMix, kFxPhaserMix, kFxFlangerMix, kFxChorusMix, kFxDelayMix, kFxReverbMix};
}  // namespace

TEST(fx_all_off_is_bit_identical) {
  FxRig r;
  auto in = noise(48000);
  auto [l, rr] = r.run(in);
  for (size_t i = 0; i < l.size(); ++i) { CHECK(l[i] == in[i]); if (l[i] != in[i]) break; }
}

TEST(fx_each_effect_at_zero_mix_leaves_the_signal_almost_unchanged) {
  for (int k = 0; k < 6; ++k) {
    FxRig r;
    r.p[kOn[k]] = 1; r.p[kMix[k]] = 0;
    auto in = noise(48000);
    auto [l, rr] = r.run(in);
    std::vector<float> settled(in.begin() + 4800, in.begin() + l.size()), out(l.begin() + 4800, l.end());
    CHECK(diffRms(out, settled) < 0.02 * probe::rms(settled));
  }
}

TEST(fx_each_effect_changes_the_signal) {
  for (int k = 0; k < 6; ++k) {
    FxRig r;
    r.p[kOn[k]] = 1; r.p[kMix[k]] = 1.0f;
    if (k == 0) r.p[kFxDistDrive] = 0.8f;
    auto in = sine(48000 * 2, 220.0f);
    auto [l, rr] = r.run(in);
    std::vector<float> a(in.begin() + 48000, in.begin() + l.size()), b(l.begin() + 48000, l.end());
    CHECK(diffRms(b, a) > 0.05 * probe::rms(a));
    CHECK(probe::finite(l) && probe::finite(rr));
  }
}

TEST(fx_delay_echo_arrives_at_the_set_time) {
  FxRig r;
  r.p[kFxDelayOn] = 1; r.p[kFxDelayTime] = 0.25f; r.p[kFxDelayFb] = 0.0f; r.p[kFxDelayMix] = 0.5f;
  auto [l, rr] = r.run(impulse(48000));
  size_t best = 1000;
  for (size_t i = 1000; i < l.size(); ++i) if (std::fabs(l[i]) > std::fabs(l[best])) best = i;
  CHECK_NEAR((double)best, 12000.0, 48.0);  // within 1 ms
  CHECK(std::fabs(l[best]) > 0.1f);
}

TEST(fx_delay_feedback_repeats_quieter) {
  FxRig r;
  r.p[kFxDelayOn] = 1; r.p[kFxDelayTime] = 0.25f; r.p[kFxDelayFb] = 0.5f; r.p[kFxDelayMix] = 0.5f;
  auto [l, rr] = r.run(impulse(48000));
  auto peakNear = [&](size_t c) { float m = 0; for (size_t i = c - 200; i < c + 200; ++i) m = std::fmax(m, std::fabs(l[i])); return m; };
  const float e1 = peakNear(12000), e2 = peakNear(24000);
  CHECK(e2 > 0.2f * e1 && e2 < 0.8f * e1);
}

TEST(fx_reverb_decay_sets_the_tail_length) {
  auto tail = [](float decay) {
    FxRig r;
    r.p[kFxReverbOn] = 1; r.p[kFxReverbDecay] = decay; r.p[kFxReverbMix] = 1.0f;
    auto [l, rr] = r.run(impulse(48000 * 3));
    double e = 0;
    for (size_t i = 72000; i < 96000; ++i) e += (double)l[i] * l[i];  // 1.5 .. 2.0 s after the impulse
    return e;
  };
  CHECK(tail(6.0f) > 4.0 * tail(1.0f));
}

TEST(fx_reverb_damping_darkens_the_tail) {
  auto highs = [](float damp) {
    FxRig r;
    r.p[kFxReverbOn] = 1; r.p[kFxReverbDecay] = 3.0f; r.p[kFxReverbDamp] = damp; r.p[kFxReverbMix] = 1.0f;
    auto [l, rr] = r.run(noise(48000));
    std::vector<float> t(l.begin() + 24000, l.end());
    double hp = 0;  // energy of the first difference: a crude high-frequency measure
    for (size_t i = 1; i < t.size(); ++i) hp += (double)(t[i] - t[i - 1]) * (t[i] - t[i - 1]);
    return hp / (probe::rms(t) * probe::rms(t) * t.size());
  };
  CHECK(highs(1.0f) < 0.8 * highs(0.0f));
}

TEST(fx_chorus_widens_a_mono_input) {
  FxRig r;
  r.p[kFxChorusOn] = 1; r.p[kFxChorusMix] = 1.0f;
  auto [l, rr] = r.run(sine(48000, 440.0f));
  std::vector<float> a(l.begin() + 4800, l.end()), b(rr.begin() + 4800, rr.end());
  CHECK(diffRms(a, b) > 0.01);
}

TEST(fx_distortion_adds_harmonics_with_drive) {
  auto third = [](float drive) {
    FxRig r;
    r.p[kFxDistOn] = 1; r.p[kFxDistDrive] = drive; r.p[kFxDistTone] = 12000.0f;
    auto [l, rr] = r.run(sine(48000, 200.0f, 0.5f));
    std::vector<float> t(l.begin() + 4800, l.end());
    return probe::tone(t, 600.0, 48000.0) / probe::tone(t, 200.0, 48000.0);
  };
  CHECK(third(0.0f) < 0.05);
  CHECK(third(1.0f) > 0.1);
}

TEST(fx_switching_an_effect_off_fades_instead_of_clicking) {
  FxRig r;
  r.p[kFxDistOn] = 1; r.p[kFxDistDrive] = 1.0f;
  auto in = sine(48000, 220.0f, 0.5f);
  std::vector<float> l(in), rr(in);
  for (size_t i = 0; i < 24000; i += kBlock) r.fx.process(r.p, l.data() + i, rr.data() + i);
  r.p[kFxDistOn] = 0;
  for (size_t i = 24000; i < 48000; i += kBlock) r.fx.process(r.p, l.data() + i, rr.data() + i);
  double maxStep = 0;
  for (size_t i = 23000; i < 26000; ++i) maxStep = std::fmax(maxStep, std::fabs(l[i] - l[i - 1]));
  CHECK(maxStep < 0.15);  // a 220 Hz sine at 0.5 moves at most ~0.015 per sample; an abrupt switch jumps ~0.5
}

TEST(fx_everything_on_stays_finite_and_bounded) {
  FxRig r;
  for (int k = 0; k < 6; ++k) { r.p[kOn[k]] = 1; r.p[kMix[k]] = 1.0f; }
  r.p[kFxDelayFb] = 0.95f; r.p[kFxPhaserFb] = 1.0f; r.p[kFxFlangerFb] = 1.0f; r.p[kFxReverbDecay] = 12.0f;
  auto [l, rr] = r.run(noise(48000 * 5, 0.9f));
  CHECK(probe::finite(l) && probe::finite(rr));
  CHECK(probe::peak(l) < 8.0 && probe::peak(rr) < 8.0);  // the engine's limiter follows; runaway is the failure
}
```

**Step 2: `engine/src/fx/fx_chain.h`**

```cpp
#pragma once
#include <memory>
#include "chorus.h"
#include "distortion.h"
#include "fx_sst.h"
#include "util.h"

namespace volt {

// distortion -> phaser -> flanger -> chorus -> delay -> reverb, each with on/off and a 21 ms crossfade.
// Construction allocates (~17 MB): never on the audio thread. process() is allocation-free.
class FxChain {
 public:
  explicit FxChain(float sampleRate);
  // Rebuilds the sst effects (allocates). Not concurrent with process().
  void setSampleRate(float sr);
  // One kBlock stereo block in place. p = real parameter values indexed by ParamId.
  void process(const float* p, float* L, float* R);

 private:
  static constexpr int kSlots = 6;
  void runSlot(int slot, const float* p, float* L, float* R);
  std::unique_ptr<SstEffects> sst_;
  Distortion dist_;
  Chorus chorus_;
  float gate_[kSlots] = {};
  float fadeStep_ = 1.0f / 1024.0f;
};

}  // namespace volt
```

**Step 3: `engine/src/fx/fx_chain.cpp`**

```cpp
#include "fx_chain.h"
#include <cstring>
#include "params.h"

namespace volt {

namespace {
const int kOnParam[6] = {kFxDistOn, kFxPhaserOn, kFxFlangerOn, kFxChorusOn, kFxDelayOn, kFxReverbOn};
}

FxChain::FxChain(float sr) { setSampleRate(sr); }

void FxChain::setSampleRate(float sr) {
  sst_ = std::make_unique<SstEffects>(sr);
  dist_.setSampleRate(sr);
  chorus_.setSampleRate(sr);
  fadeStep_ = 1.0f / (0.021f * sr);
  std::memset(gate_, 0, sizeof(gate_));
}

void FxChain::runSlot(int slot, const float* p, float* L, float* R) {
  switch (slot) {
    case 0: dist_.process(L, R, p[kFxDistDrive], p[kFxDistTone], p[kFxDistMix]); break;
    case 1: sst_->phaser(L, R, p[kFxPhaserRate], p[kFxPhaserDepth], p[kFxPhaserFb], p[kFxPhaserMix]); break;
    case 2: sst_->flanger(L, R, p[kFxFlangerRate], p[kFxFlangerDepth], p[kFxFlangerFb], p[kFxFlangerMix]); break;
    case 3: chorus_.process(L, R, (int)p[kFxChorusMode], p[kFxChorusMix]); break;
    case 4: sst_->delay(L, R, p[kFxDelayTime], p[kFxDelayFb], p[kFxDelayMix]); break;
    case 5: sst_->reverb(L, R, p[kFxReverbDecay], p[kFxReverbDamp], p[kFxReverbMix]); break;
  }
}

void FxChain::process(const float* p, float* L, float* R) {
  for (int k = 0; k < kSlots; ++k) {
    const bool on = p[kOnParam[k]] > 0.5f;
    float& g = gate_[k];
    if (!on && g <= 0.0f) continue;  // off and faded out: no CPU, signal untouched
    if (on && g >= 1.0f) { runSlot(k, p, L, R); continue; }
    alignas(16) float dl[kBlock], dr[kBlock];
    std::memcpy(dl, L, sizeof(dl));
    std::memcpy(dr, R, sizeof(dr));
    runSlot(k, p, L, R);
    for (int s = 0; s < kBlock; ++s) {
      g += on ? fadeStep_ : -fadeStep_;
      g = clampf(g, 0.0f, 1.0f);
      L[s] = dl[s] + g * (L[s] - dl[s]);
      R[s] = dr[s] + g * (R[s] - dr[s]);
    }
  }
}

}  // namespace volt
```

A switched-off effect that later comes back starts with whatever its delay lines held (a ghost of the old tail under the fade-in). Clearing them on the audio thread would mean memsetting megabytes inside a callback, so it is accepted; note it in NOTES.md.

**Step 4: Run** `.\tools\test-engine.ps1 fx_`. These tests are where Task 4's `MAP` lines get corrected:
- `fx_delay_echo_arrives_at_the_set_time` wrong → the delay time unit (seconds vs log2 seconds) or the index is wrong;
- `fx_reverb_decay_sets_the_tail_length` fails → decay unit/index;
- `…zero_mix…` fails for one effect → its mix index, or a "mix 0 is not dry" design; then look at the effect's code and write what you found in NOTES.md;
- the reverb's tests take a few seconds of audio each: the suite gets slower; acceptable.

**Step 5: Commit** — `feat(engine): effect chain with crossfaded on/off`.

---

### Task 6: Effects and arpeggiator in the engine

**Files:**
- Modify: `engine/src/engine.h`, `engine/src/engine.cpp`, `engine/tests/test_engine.cpp`, `engine/tests/test_alloc.cpp`, `engine/tests/bench.cpp`, `engine/CMakeLists.txt` (`src/fx/fx_chain.cpp` already added)

**Step 1: Failing tests** — append to `engine/tests/test_engine.cpp`:

```cpp
TEST(engine_effects_change_the_output_and_every_effect_off_does_not) {
  auto run = [](bool reverb) {
    Rig r;
    r.set(kVoiceDrift, 0.0f);
    if (reverb) { r.set(kFxReverbOn, 1); r.set(kFxReverbMix, 0.5f); }
    r.play(0.01f);
    r.on(57); r.play(0.3f); r.off(57);
    return r.play(1.0f);
  };
  auto dry = run(false), wet = run(true);
  CHECK(probe::rms(std::vector<float>(wet.begin() + 30000, wet.end())) > 3.0 * probe::rms(std::vector<float>(dry.begin() + 30000, dry.end())) + 1e-6);
}

TEST(engine_arpeggiator_repeats_a_held_key_at_the_tempo) {
  Rig r;
  r.set(kArpOn, 1); r.set(kArpBpm, 120); r.set(kArpDivision, 2);  // 1/16 = 0.125 s
  r.set(kArpGate, 0.5f);
  r.set(kAenvAttack, 0.001f); r.set(kAenvRelease, 0.005f); r.set(kAenvSustain, 1.0f);
  r.play(0.01f);
  r.on(60);
  int starts = 0, prev = 0;
  std::vector<float> buf(2 * 16);
  for (int b = 0; b < 48000 / 16; ++b) {  // 1 s
    r.e.render(buf.data(), 16);
    const int v = r.e.activeVoices();
    if (v > 0 && prev == 0) ++starts;
    prev = v;
  }
  CHECK_NEAR(starts, 8, 1);
}

TEST(engine_switching_the_arp_on_and_off_never_leaves_a_stuck_note) {
  Rig r;
  r.set(kAenvRelease, 0.01f);
  r.on(60); r.play(0.1f);
  r.set(kArpOn, 1); r.play(0.3f);   // the held key moves into the arpeggiator
  r.set(kArpOn, 0); r.play(0.1f);   // and back to a normal note
  CHECK(r.e.activeVoices() == 1);
  r.off(60); r.play(0.3f);
  CHECK(r.e.activeVoices() == 0);
  r.set(kArpOn, 1); r.on(62); r.play(0.2f); r.off(62); r.play(0.5f);
  CHECK(r.e.activeVoices() == 0);
}

TEST(engine_arp_notes_ignore_the_sustain_pedal) {
  Rig r;
  r.set(kArpOn, 1); r.set(kArpDivision, 2);
  r.set(kAenvRelease, 0.005f);
  r.e.post({Event::Sustain, 0, 1.0f});
  r.on(60); r.on(64); r.on(67);
  r.play(1.0f);
  CHECK(r.e.activeVoices() <= 2);  // with the pedal holding them, every step would pile up a voice
}
```

**Step 2: `engine.h`** — add `#include "arp.h"` and `#include "fx/fx_chain.h"`, and private members:

```cpp
  void keyDown(int note, float vel);
  void keyUp(int note);
  void noteOffNow(int note);  // release ignoring the sustain pedal (arpeggio steps)
  void runArp();

  std::unique_ptr<FxChain> fx_;  // allocated in the constructor (~17 MB), off the audio thread
  Arp arp_;
  bool arpOn_ = false;
  bool keyHeld_[128] = {};
  float keyVel_[128] = {};
```

(`Engine` becomes non-copyable because of the `unique_ptr`; nothing copies it.)

**Step 3: `engine.cpp`**

- Constructor: `fx_ = std::make_unique<FxChain>(48000.0f);` before the existing `setSampleRate(48000.0f)` call.
- `setSampleRate`: add `if (fx_) fx_->setSampleRate(sr); arp_.setSampleRate(sr);`.
- `handle`: route keys through the new functions and clear the arpeggiator on panic:

```cpp
    case Event::NoteOn:
      if (e.b <= 0.0f) keyUp(e.a); else keyDown(e.a, e.b);
      break;
    case Event::NoteOff: keyUp(e.a); break;
    // ...
    case Event::AllNotesOff:
      for (auto& v : voices_) v.kill();
      stackSize_ = 0;
      arp_.clear();
      std::memset(keyHeld_, 0, sizeof(keyHeld_));
      break;
```

- New functions:

```cpp
void Engine::keyDown(int note, float vel) {
  if (note < 0 || note > 127) return;
  keyHeld_[note] = true;
  keyVel_[note] = vel;
  if (arpOn_) arp_.keyDown(note, vel); else noteOn(note, vel);
}

void Engine::keyUp(int note) {
  if (note < 0 || note > 127) return;
  keyHeld_[note] = false;
  if (arpOn_) arp_.keyUp(note); else noteOff(note);
}

void Engine::noteOffNow(int note) {
  const bool pedal = sustain_;
  sustain_ = false;
  noteOff(note);
  sustain_ = pedal;
}

void Engine::runArp() {
  const bool on = value_[kArpOn] > 0.5f;
  if (on != arpOn_) {
    if (on) {
      for (int n = 0; n < 128; ++n) if (keyHeld_[n]) { noteOffNow(n); arp_.keyDown(n, keyVel_[n]); }
    } else {
      const int s = arp_.clear();
      if (s >= 0) noteOffNow(s);
      for (int n = 0; n < 128; ++n) if (keyHeld_[n]) noteOn(n, keyVel_[n]);
    }
    arpOn_ = on;
  }
  if (!arpOn_) return;
  arp_.setHold(value_[kArpHold] > 0.5f);
  const ArpSettings s{(int)value_[kArpMode], (int)value_[kArpOctaves], (int)value_[kArpDivision], value_[kArpBpm], value_[kArpGate]};
  ArpEvent ev[8];
  const int n = arp_.advance(kBlock, s, ev, 8);
  for (int i = 0; i < n; ++i) {
    if (ev[i].on) noteOn(ev[i].note, ev[i].vel); else noteOffNow(ev[i].note);
  }
}
```

- `renderBlock`: call `runArp();` right after the filter-model check (after smoothing, before building `BlockCtx`), and run the effects on the summed voices, before the master loop:

```cpp
  fx_->process(value_, blockL_, blockR_);

  // Master volume, then a soft limiter so no preset can clip the output.
```

**Step 4: `test_alloc.cpp`** — in the random loop, every 50th round also switch a random effect and the arpeggiator on, so effects and arp run inside the counted region:

```cpp
    if (round % 50 == 25) {
      static const int kOn[] = {kFxDistOn, kFxPhaserOn, kFxFlangerOn, kFxChorusOn, kFxDelayOn, kFxReverbOn, kArpOn};
      e.post({Event::SetParam, kOn[rng.next() % 7], 1.0f});
    }
```

Before the counted loop, after the warm-up render, add a second warm-up with every effect on (one render of 512 frames), so lazy first-block work in the sst effects happens before counting. If `alloc_render_never_touches_the_heap` still fails, break inside the counting `operator new` while the flag is set and read the stack. The Phaser stage allocation is the known suspect: set its stage count once in `Impl`'s constructor (Task 4) and never write that parameter again.

**Step 5: `bench.cpp`** — after the four model lines, add one run with model 0, 16 voices, **all six effects on** at mix 0.5, printed as `model 0 + all effects: …`. Run `.\build\engine\volt_bench.exe` and write the five lines into NOTES.md under `## Desempenho` (PC numbers).

**Step 6: Run** `.\tools\test-engine.ps1` — every test passes, including all the plan 1–3 tests (effects are off by default, so their output is unchanged).

**Step 7: Commit** — `feat(engine): effects and arpeggiator in the signal path`.

---

### Task 7: Factory MIDI controllers for effects

**Files:**
- Modify: `app/src/main/kotlin/com/criapix/volt/midi/MidiDispatch.kt`, `app/src/test/kotlin/com/criapix/volt/midi/MidiRoutingTest.kt`

**Step 1: Failing test** — append to `MidiRoutingTest`:

```kotlin
  @Test fun generalMidiEffectControllersSetTheMixAndSwitchTheEffectOn() {
    d.softTakeover = false
    send(0xB0, 91, 100)
    assertEquals(1f, state.real(id("fx.reverb.on")), 0f)
    assertEquals(100 / 127f, state.norm(id("fx.reverb.mix")), 0.01f)
    send(0xB0, 93, 0)                                   // zero does not switch an effect on
    assertEquals(0f, state.real(id("fx.chorus.on")), 0f)
    send(0xB0, 95, 64)
    assertEquals(1f, state.real(id("fx.phaser.on")), 0f)
  }
```

**Step 2: Implement.** In `FACTORY_PARAMS` add `91 to "fx.reverb.mix", 93 to "fx.chorus.mix", 95 to "fx.phaser.mix"`. In `fromController`, after a value is accepted and sent:

```kotlin
      // A controller raising an effect's mix also switches that effect on (decision 4 of plan 4).
      if (n > 0f && p.name.startsWith("fx.") && p.name.endsWith(".mix")) {
        params[p.name.removeSuffix(".mix") + ".on"]?.let { on -> if (current(on.id) < 0.5f) synth.setParam(on.id, 1f) }
      }
```

This applies to learned mixes too, which is what a user expects. With the default `current = { NaN }` (old tests), `NaN < 0.5f` is false, so nothing changes for them.

**Step 3: Run** — passes. **Step 4: Commit** — `feat(app): factory controllers for reverb, chorus and phaser`.

---

### Task 8: On-screen descriptions and tabs for the new parameters

**Files:**
- Modify: `ui/ParamUi.kt`, `ui/Panel.kt`, `app/src/test/kotlin/com/criapix/volt/ui/ParamUiTest.kt`

**Step 1: Tests** — `PanelTest` and `ParamUiTest` already fail since Task 1 (coverage). Add format checks to `ParamUiTest.valuesAreWrittenWithUnitsInPortuguese`:

```kotlin
    assertEquals("120 BPM", f("arp.bpm", 120f))
    assertEquals("1/16", f("arp.division", 2f))
    assertEquals("I+II", f("fx.chorus.mode", 2f))
    assertEquals("375 ms", f("fx.delay.time", 0.375f))
    assertEquals("2,50 s", f("fx.reverb.decay", 2.5f))
    assertEquals("Sobe", f("arp.mode", 0f))
```

**Step 2: `ParamUi.kt`** — add `BPM` to `ValueUnit` (`ValueUnit.BPM -> "${real.roundToInt()} BPM"` in `format`), and to the table:

```kotlin
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
```

**Step 3: `Panel.kt`** — add the tabs and groups:

```kotlin
enum class Tab(val title: String) { OSC("OSC"), FILTER("FILTRO"), ENV("ENV"), MOD("MOD"), ARP("ARP"), FX("FX") }
```

and to `groups`:

```kotlin
    Tab.ARP to listOf(
      Group("ARPEJADOR", listOf("arp.on", "arp.hold", "arp.mode", "arp.division", "arp.octaves", "arp.bpm", "arp.gate"), weight = 2f),
    ),
    Tab.FX to listOf(
      Group("DISTORÇÃO", listOf("fx.dist.on", "fx.dist.drive", "fx.dist.tone", "fx.dist.mix")),
      Group("PHASER", listOf("fx.phaser.on", "fx.phaser.rate", "fx.phaser.depth", "fx.phaser.feedback", "fx.phaser.mix")),
      Group("FLANGER", listOf("fx.flanger.on", "fx.flanger.rate", "fx.flanger.depth", "fx.flanger.feedback", "fx.flanger.mix")),
      Group("CHORUS", listOf("fx.chorus.on", "fx.chorus.mode", "fx.chorus.mix")),
      Group("DELAY", listOf("fx.delay.on", "fx.delay.time", "fx.delay.feedback", "fx.delay.mix")),
      Group("REVERB", listOf("fx.reverb.on", "fx.reverb.decay", "fx.reverb.damp", "fx.reverb.mix")),
    ),
```

Six FX groups on ~891 dp leave ~140 dp each; `KnobGrid.fit` puts 3–4 knobs in one column at ~56 dp. If a group scrolls at the S25 Ultra size, write it down in NOTES.md (as plan 2 did for LFO 1).

**Step 4: Run** the Kotlin tests — all pass again, including `PanelTest` and `ParamUiTest`.

**Step 5: Commit** — `feat(app): ARP and FX tabs`.

---

### Task 9: On-screen keyboard

**Files:**
- Create: `ui/Keyboard.kt`, `app/src/test/kotlin/com/criapix/volt/ui/KeyboardLayoutTest.kt`
- Modify: `ui/TopBar.kt`, `ui/VoltScreen.kt`

**Step 1: Failing test — `KeyboardLayoutTest.kt`**

```kotlin
package com.criapix.volt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardLayoutTest {
  private val w = 1500f   // 15 white keys of 100 px
  private val h = 300f
  private fun at(whiteIndex: Float, yFrac: Float) = KeyboardLayout.noteAt(whiteIndex * 100f, yFrac * h, w, h, base = 48)

  @Test fun whiteKeysFollowTheScale() {
    assertEquals(48, at(0.5f, 0.9f))   // C3
    assertEquals(50, at(1.5f, 0.9f))   // D3
    assertEquals(52, at(2.5f, 0.9f))   // E3
    assertEquals(53, at(3.5f, 0.9f))   // F3
    assertEquals(60, at(7.5f, 0.9f))   // C4
    assertEquals(72, at(14.5f, 0.9f))  // the top C
  }

  @Test fun blackKeysWinInTheUpperPart() {
    assertEquals(49, at(0.9f, 0.3f))   // C#3, right edge of C
    assertEquals(49, at(1.1f, 0.3f))   // C#3, left edge of D
    assertEquals(48, at(0.9f, 0.9f))   // same x, lower: the white key
    assertEquals(52, at(2.9f, 0.3f))   // E has no sharp: still E
    assertEquals(54, at(3.9f, 0.3f))   // F#3
  }

  @Test fun outsideIsNothing() {
    assertNull(KeyboardLayout.noteAt(-1f, 10f, w, h, 48))
    assertNull(KeyboardLayout.noteAt(10f, 301f, w, h, 48))
  }

  @Test fun velocityGrowsTowardsTheBottom() {
    assertEquals(0.3f, KeyboardLayout.velocity(0f, h), 1e-6f)
    assertEquals(1f, KeyboardLayout.velocity(h, h), 1e-6f)
  }
}
```

**Step 2: `ui/Keyboard.kt`**

```kotlin
package com.criapix.volt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criapix.volt.midi.noteName

/** Geometry of the drawer keyboard: 15 white keys (two octaves and the top C). */
object KeyboardLayout {
  const val WHITES = 15
  private const val BLACK_ZONE = 0.6f   // black keys take the upper 60 %
  private const val BLACK_HALF = 0.3f   // and +-0.3 of a white key around each boundary
  private val STEPS = intArrayOf(0, 2, 4, 5, 7, 9, 11)
  private val SHARP_AFTER = booleanArrayOf(true, true, false, true, true, true, false)

  private fun white(i: Int, base: Int) = base + 12 * (i / 7) + STEPS[i % 7]

  /** The MIDI note under (x, y), or null outside the keyboard. [base] is the first C. */
  fun noteAt(x: Float, y: Float, w: Float, h: Float, base: Int): Int? {
    if (x < 0f || x >= w || y < 0f || y > h) return null
    val kw = w / WHITES
    val i = (x / kw).toInt().coerceAtMost(WHITES - 1)
    val frac = x / kw - i
    if (y < h * BLACK_ZONE) {
      if (frac > 1f - BLACK_HALF && i < WHITES - 1 && SHARP_AFTER[i % 7]) return white(i, base) + 1
      if (frac < BLACK_HALF && i > 0 && SHARP_AFTER[(i - 1) % 7]) return white(i - 1, base) + 1
    }
    return white(i, base)
  }

  /** Touching higher on a key plays softer. */
  fun velocity(y: Float, h: Float): Float = (0.3f + 0.7f * (y / h)).coerceIn(0.3f, 1f)

  /** Left edge (in white-key units) of every black key in the drawing. */
  fun blackKeys(base: Int): List<Pair<Float, Int>> = (0 until WHITES - 1).filter { SHARP_AFTER[it % 7] }
    .map { (it + 1f - BLACK_HALF) to white(it, base) + 1 }
}

@Composable
fun KeyboardDrawer(onNoteOn: (Int, Float) -> kotlin.Unit, onNoteOff: (Int) -> kotlin.Unit, onClose: () -> kotlin.Unit, modifier: Modifier = Modifier) {
  var base by rememberSaveable { mutableIntStateOf(48) }
  var pressed by remember { mutableStateOf(emptySet<Int>()) }
  Column(modifier.background(VoltColors.panel).padding(6.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TextButton(onClick = { if (base > 24) base -= 12 }) { Text("Oitava −") }
      Text("${noteName(base)} – ${noteName(base + 24)}", fontSize = 13.sp, lineHeight = 16.sp)
      TextButton(onClick = { if (base < 84) base += 12 }) { Text("Oitava +") }
      Text("toque mais embaixo na tecla para tocar mais forte", fontSize = 11.sp, lineHeight = 14.sp, color = VoltColors.dim, modifier = Modifier.weight(1f))
      TextButton(onClick = onClose) { Text("Fechar") }
    }
    Canvas(
      Modifier.fillMaxSize().pointerInput(base) {
        val playing = HashMap<PointerId, Int>()
        try {
          awaitPointerEventScope {
            while (true) {
              val ev = awaitPointerEvent()
              for (c in ev.changes) {
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val note = if (c.pressed) KeyboardLayout.noteAt(c.position.x, c.position.y, w, h, base) else null
                val old = playing[c.id]
                if (note != old) {
                  if (old != null) { onNoteOff(old); playing.remove(c.id) }
                  if (note != null) { onNoteOn(note, KeyboardLayout.velocity(c.position.y, h)); playing[c.id] = note }
                }
                c.consume()
              }
              pressed = playing.values.toSet()
            }
          }
        } finally {
          playing.values.forEach(onNoteOff)  // octave change or drawer closed while touching: no stuck notes
          pressed = emptySet()
        }
      },
    ) {
      val kw = size.width / KeyboardLayout.WHITES
      for (i in 0 until KeyboardLayout.WHITES) {
        val note = KeyboardLayout.noteAt(i * kw + kw / 2, size.height - 1f, size.width, size.height, base)
        drawRect(if (note in pressed) VoltColors.accent else Color(0xFFEDEDED), Offset(i * kw + 1f, 0f), Size(kw - 2f, size.height))
      }
      for ((left, note) in KeyboardLayout.blackKeys(base)) {
        drawRect(if (note in pressed) VoltColors.accent else Color(0xFF111111), Offset(left * kw, 0f), Size(kw * 0.6f, size.height * 0.6f))
      }
    }
  }
}
```

**Step 3: Top bar and screen**
- `TopBar`: new parameters `keyboardOpen: Boolean, onKeyboard: () -> kotlin.Unit`; a `TextButton` "Teclas" (orange text when open) between "Pânico" and the MIDI button.
- `VoltScreen`: `var keyboard by rememberSaveable { mutableStateOf(false) }`. Wrap the content area (the groups `Row`) in a `Box(Modifier.weight(1f))` and, at its bottom, show the drawer with a slide animation:

```kotlin
        AnimatedVisibility(
          visible = keyboard,
          enter = slideInVertically { it }, exit = slideOutVertically { it },
          modifier = Modifier.align(Alignment.BottomCenter),
        ) {
          KeyboardDrawer(
            onNoteOn = { n, v -> state.noteOn(n, v) }, onNoteOff = { n -> state.noteOff(n) },
            onClose = { keyboard = false },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.62f),
          )
        }
```

(The drawer takes 62 % of the panel area below the tab row: on the S25 Ultra about 190 dp, of which ~150 dp of keys.)

**Step 4: Run** the Kotlin tests and `:app:assembleDebug`.

**Step 5: Commit** — `feat(app): slide-up on-screen keyboard`.

---

### Task 10: Presets that use the new features

**Files:**
- Modify: factory files listed below; Create: `090-arpejo-classico.json`, `091-cordas-phaser.json`, `092-baixo-distorcido.json` in `app/src/main/assets/presets/factory/`

**Step 1: Add effects to existing presets.** Merge these keys into each file's `params` (keep every existing key; do not rename or renumber files). First read each file to check the preset matches the description; if a file name differs from this list, find it by its `name` field.

| File | Add |
|---|---|
| `030-pad-analogico.json` | `"fx.chorus.on": 1, "fx.chorus.mode": 0, "fx.chorus.mix": 0.4, "fx.reverb.on": 1, "fx.reverb.decay": 3.5, "fx.reverb.mix": 0.3` |
| `031-pad-pwm.json` | `"fx.chorus.on": 1, "fx.chorus.mode": 1, "fx.chorus.mix": 0.5, "fx.reverb.on": 1, "fx.reverb.decay": 4, "fx.reverb.mix": 0.3` |
| `032-pad-escuro.json`, `033-pad-vidro.json`, `034-pad-evolutivo.json` | `"fx.reverb.on": 1, "fx.reverb.decay": 5, "fx.reverb.damp": 0.6, "fx.reverb.mix": 0.35` |
| `050-cordas-suaves.json` | `"fx.chorus.on": 1, "fx.chorus.mode": 0, "fx.chorus.mix": 0.35, "fx.reverb.on": 1, "fx.reverb.decay": 3, "fx.reverb.mix": 0.3` |
| `051-cordas-ensemble.json` | `"fx.chorus.on": 1, "fx.chorus.mode": 2, "fx.chorus.mix": 0.6, "fx.reverb.on": 1, "fx.reverb.decay": 3, "fx.reverb.mix": 0.3` |
| `071-piano-eletrico.json` | `"fx.chorus.on": 1, "fx.chorus.mode": 0, "fx.chorus.mix": 0.35, "fx.reverb.on": 1, "fx.reverb.decay": 1.8, "fx.reverb.mix": 0.2` |
| `073-orgao.json` | `"fx.chorus.on": 1, "fx.chorus.mode": 2, "fx.chorus.mix": 0.5, "fx.reverb.on": 1, "fx.reverb.decay": 1.5, "fx.reverb.mix": 0.15` |
| `020-lead-sync.json` | `"fx.delay.on": 1, "fx.delay.time": 0.375, "fx.delay.feedback": 0.3, "fx.delay.mix": 0.2` |
| `024-lead-trance.json` | `"fx.delay.on": 1, "fx.delay.time": 0.375, "fx.delay.feedback": 0.4, "fx.delay.mix": 0.25, "fx.reverb.on": 1, "fx.reverb.decay": 2.5, "fx.reverb.mix": 0.25` |

**Step 2: New presets** (at the end of the list, decision 7):

`090-arpejo-classico.json`

```json
{ "format": 1, "name": "Arpejo Clássico", "category": "Pluck", "params": {
  "arp.on": 1, "arp.mode": 0, "arp.octaves": 2, "arp.division": 2, "arp.bpm": 120, "arp.gate": 0.45,
  "osc1.wave": 0, "osc2.wave": 1, "osc2.fine": 5, "osc2.level": 0.7,
  "filter.model": 0, "filter.cutoff": 600, "filter.reso": 0.45, "filter.env": 0.45, "filter.keytrack": 0.6,
  "fenv.attack": 0.001, "fenv.decay": 0.18, "fenv.sustain": 0.1, "fenv.release": 0.15,
  "aenv.attack": 0.001, "aenv.decay": 0.3, "aenv.sustain": 0.4, "aenv.release": 0.15,
  "fx.delay.on": 1, "fx.delay.time": 0.375, "fx.delay.feedback": 0.35, "fx.delay.mix": 0.25,
  "fx.reverb.on": 1, "fx.reverb.decay": 2, "fx.reverb.mix": 0.2 } }
```

`091-cordas-phaser.json` — the late-70s string-machine sound: bright detuned saws through a phaser and ensemble chorus.

```json
{ "format": 1, "name": "Cordas com Phaser", "category": "Strings", "params": {
  "osc1.wave": 0, "osc2.wave": 0, "osc2.octave": 1, "osc2.fine": 8, "osc2.level": 0.6,
  "hpf.cutoff": 200,
  "filter.model": 2, "filter.cutoff": 4500, "filter.reso": 0.05, "filter.keytrack": 0.5,
  "aenv.attack": 0.25, "aenv.decay": 0.5, "aenv.sustain": 1.0, "aenv.release": 0.9,
  "fx.phaser.on": 1, "fx.phaser.rate": 0.3, "fx.phaser.depth": 0.8, "fx.phaser.feedback": 0.5, "fx.phaser.mix": 0.6,
  "fx.chorus.on": 1, "fx.chorus.mode": 2, "fx.chorus.mix": 0.4,
  "fx.reverb.on": 1, "fx.reverb.decay": 3, "fx.reverb.mix": 0.25 } }
```

`092-baixo-distorcido.json`

```json
{ "format": 1, "name": "Baixo Distorcido", "category": "Bass", "params": {
  "voice.mode": 1, "voice.glide": 0.03, "voice.glide_legato": 1,
  "osc1.wave": 0, "osc1.octave": -1, "osc2.wave": 1, "osc2.octave": -1, "osc2.fine": 6, "osc2.level": 0.7,
  "filter.model": 0, "filter.cutoff": 350, "filter.reso": 0.3, "filter.env": 0.4, "filter.keytrack": 0.3,
  "fenv.attack": 0.002, "fenv.decay": 0.3, "fenv.sustain": 0.2, "fenv.release": 0.1,
  "aenv.attack": 0.002, "aenv.decay": 0.4, "aenv.sustain": 0.9, "aenv.release": 0.08,
  "fx.dist.on": 1, "fx.dist.drive": 0.6, "fx.dist.tone": 3500, "fx.dist.mix": 0.8 } }
```

**Step 3: Run** `.\gradlew.bat :app:testDebugUnitTest` — `FactoryPresetsTest` checks names, ranges, step values and an audible source in every file.

**Step 4: Commit** — `feat(app): effects in factory presets; arpeggio, phaser strings and distorted bass presets`.

---

### Task 11: Build, look at it, version

**Files:** `app/build.gradle.kts` (`versionCode = 4`, `versionName = "0.4.0"`)

**Step 1: Full build**

```powershell
. .\tools\env.ps1
.\tools\test-engine.ps1
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

All green. The APK grows by the sst-effects code only (no assets).

**Step 2: Emulator** (always `-s emulator-5554`; S25 size `wm size 1440x3120`, `wm density 560`):
1. Open → the tab row shows OSC FILTRO ENV MOD ARP FX.
2. FX tab → screenshot `fx.png`: six groups, each title row with a "Ligado" toggle; note any group that scrolls.
3. ARP tab → screenshot `arp.png`.
4. "Teclas" → screenshot `keys.png`: the drawer slides up with 15 white keys, C3 – C5 label, octave buttons. Tap a key with `adb -s emulator-5554 shell input tap X Y` → the top bar's voice count shows `1 vozes` while held (use `input swipe X Y X Y 1500` to hold for 1.5 s and screenshot during it).
5. Turn ARP on, then hold a key with a 3 s swipe: screenshots at 1 s intervals show the voice count going up and down (the arpeggio retriggers).
6. Load `Arpejo Clássico` from the browser (position 041) → the ARP tab shows Ligado, 2 oitavas, 1/16.
7. `adb logcat -d -s AndroidRuntime volt` — no crash. Pull the app log and check `AUDIO` lines show the load with effects (`Carga do motor`), and write the numbers down. They come from the emulator, not the phone.

**Step 3: Commit** — `chore(app): version 0.4.0`.

---

### Task 12: Notes, device checklist, README

**Files:** `docs/NOTES.md`, `docs/DEVICE-TEST.md`, `README.md`

**Step 1: `docs/NOTES.md`** — update `## Estado`; add `## Plano 4 (efeitos, arpejador, teclado) — <data>`:
- which effects came from sst-effects and which were written here, and why (no chorus, no distortion in the library); submodule hash; the parameter mapping chosen (index, unit) for each sst effect; whether plan B was needed for any effect;
- the fixed internal settings chosen per sst effect (decision 2);
- CPU numbers from `volt_bench` (PC), including "all effects on";
- known limits: distortion without oversampling (aliasing at high drive on high notes); a re-enabled effect may replay a ghost of its old tail under the fade-in; arpeggio steps at 16-sample resolution; Bank Select still ignored;
- factory controllers 91/93/95 and the "raising the mix switches the effect on" rule;
- the three new presets at positions 41–43 and the effects added to existing ones (by ear: not heard);
- what the screenshots showed (groups that scroll).

**Step 2: `docs/DEVICE-TEST.md`** — add above plan 3's section:

```markdown
# Teste no aparelho (plano 4: efeitos, arpejador, teclado)

| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 1 | Aba FX: ligar cada efeito sozinho com uma nota soando | Liga e desliga sem estalo; cada um soa como o nome diz | |
| 2 | Chorus: modos I, II e I+II num pad | I e II largos e lentos (II mais rápido); I+II um tremor rápido e sutil, como num Juno | |
| 3 | Delay: tempo 375 ms, repetições 50 % | Ecos regulares que somem aos poucos | |
| 4 | Reverb: duração 1 s e depois 8 s | Cauda curta e depois longa; "Abafar" escurece a cauda | |
| 5 | Distorção num baixo: drive de 0 a 100 % | De limpo a saturado; "Tom" escurece | |
| 6 | Todos os efeitos ligados, 16 notas (pedal) | Anotar "Carga do motor" (⋮ → Configurações) e se há estouros | |
| 7 | Knob do CK61 que manda CC 91 (reverb) num preset sem reverb | O reverb liga e o mix acompanha | |
| 8 | Aba ARP: ligar, segurar um acorde | Arpejo no tempo; mudar modo, oitavas, divisão, tempo e duração enquanto toca | |
| 9 | Hold: tocar um acorde, soltar | Continua; um acorde novo substitui o anterior | |
| 10 | Ligar/desligar o arpejador com teclas apertadas | Nenhuma nota fica presa | |
| 11 | Preset "Arpejo Clássico" (041) | Arpejo com delay | |
| 12 | Botão "Teclas": tocar com vários dedos, deslizar entre teclas, trocar de oitava | Notas certas, sem notas presas; mais embaixo na tecla = mais forte | |
| 13 | Ouvir os presets com efeitos novos (pads, cordas, piano elétrico, órgão, leads) | Anotar por preset: melhorou / ajustar (o quê) | |
```

**Step 3: `README.md`** — `## Estado`: everything in the design except the tuned final bank. Licences: add sst-effects (GPL-3).

**Step 4: Full verification** — the three build/test commands again, `git status` clean.

**Step 5: Commit** — `docs: state, device checklist and README for plan 4`.

**Step 6:** Finish the branch with superpowers:finishing-a-development-branch (local merge into `main`).

**Step 7: Final report to the user** (Portuguese): what was built; test counts; what the screenshots showed; which effects are sst and which custom, and whether any plan B was needed; CPU numbers (PC only); the factory controller rule for 91/93/95; that nothing was heard; and that the next step is theirs: `docs/DEVICE-TEST.md` (plan 4). Steps 6 (CPU with everything on) and 13 (presets by ear) feed plan 5.

---

## 99. What comes after this plan

| Plan | Design sections | Contents |
|---|---|---|
| 5 — Factory bank and polish | 4.5, 7, 8 | The tuned ~64-preset bank from the user's notes (plan 2's 40 + plan 4's 3 + new ones, keeping existing positions stable for Program Change); a render test per preset in the engine (reads the JSON files, checks sound / no clipping / finite); polyphony reduction under sustained overload (plan 2 measured 36 % with 16 voices before effects existed; plan 4's device step 6 gives the number with effects); app icon; release build and signing |
