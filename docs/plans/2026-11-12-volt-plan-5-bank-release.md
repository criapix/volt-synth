# Volt Plan 5 — Final Bank, Polyphony Under Load, Icon and Release 1.0 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ship Volt 1.0.0:
- a factory bank of 64 presets, checked by a render test and level-balanced, then tuned with the user's ears;
- polyphony that drops by itself when the phone cannot keep up;
- an app icon, an "about" screen and log export;
- a signed release APK.

**Architecture:**
- **Bank tooling (PC only).** A small C++ reader loads the factory JSON files straight into the engine on the PC. It drives two things:
  - an engine test (every preset sounds, stays finite, frees its voices and sits inside a loudness window);
  - a render tool that writes one WAV per preset plus an `index.html`, so the user can listen to the whole bank on the PC and say what to change.
- **Polyphony under load.** The engine gains a voice limit. A small `LoadGovernor`, fed from the Oboe callback, lowers the limit when the callback runs out of time and raises it again later. The app shows the limit, and the settings screen adds a manual cap.
- **Release.** Release builds are signed with a keystore that lives outside the repository. Debug builds get their own application id, so the release and development builds can both be installed.

**Tech Stack:** C++20 engine (existing), `std::filesystem`; Kotlin 2.3 + Jetpack Compose; Android vector drawables (adaptive icon); Gradle signing config; JUnit 4.

---

## 0. Read this first

**Design document:** `docs/plans/2026-11-11-volt-design.md`, especially:
- section 4.6, "Desempenho": 30 % of a core is the target;
- the error table, which covers persistent xruns and reduced polyphony;
- the presets section, "Fábrica: cerca de 64 presets";
- the tests section, which asks for a render of every factory preset.

Plans 1–4 are in `docs/plans/`. Their section 0 rules all still hold:

- **Repository and branch.** The repository is `C:\Users\criap\OneDrive\Documentos\Github\volt-synth`, branch `main`. Work on `feature/bank-release` and merge locally at the end. Do not push; there is no remote.
- **Build commands.**
  - Run `. .\tools\env.ps1` before building.
  - Engine tests: `.\tools\test-engine.ps1 [filter]`.
  - App: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.
- **Read `docs/NOTES.md` whole first.** It records these traps:
  - PowerShell writes a BOM into config files;
  - the PC's pt-BR locale breaks number formatting in tests (use `Locale.ROOT`);
  - the `ui` package uses `ValueUnit`, not `Unit`, and writes `kotlin.Unit` explicitly;
  - small texts need an explicit `lineHeight`;
  - with two devices attached, always pass `adb -s emulator-5554`, so nothing is sent to the user's phone.
- **Audio-thread rules.** Inside `Engine::render` and `Audio::onAudioReady` there must be no allocation, locks, I/O, logging or JNI. `test_alloc.cpp` enforces this.
- **Code was written without a compiler.** Fix whatever does not compile. When a test fails, look for the cause in the code first. Change an expectation only with a written reason in `docs/NOTES.md`.
- **Language.** Code, comments and commits in English. On-screen text in Brazilian Portuguese. NOTES, DEVICE-TEST and PRESET-REVIEW in Portuguese. End every commit message with the co-author line your session's instructions give you.
- **Honesty rule.** You cannot hear anything. The tests measure that a preset sounds, its level, its tail and its CPU cost. Whether it sounds *good* is the user's call: Task 9 is a stop-and-ask checkpoint, and NOTES must never say a preset was "tuned by ear" unless the user listened to it.

### State at the start (from `docs/NOTES.md`, 2026-11-12)

- Plans 1–4 are merged, with 43 factory presets.
- What the user has heard:
  - plan 1: the CK61 works, output latency about 8 ms, 0 xruns;
  - plan 2: the first 10 presets ("pareceu muito bom").
- What nobody has heard: the 30 presets added after plan 2, plus effects, arpeggiator, keyboard and MIDI Learn on the phone.
- Highest measured engine load: **36 % with 16 voices** on the S25 Ultra, just after loading `Sino Metálico`, with no xrun. The design target is 30 %. Which patch caused the peak is unknown; Task 3's stress mode finds the heaviest patches.
- The output stage ends in `tanh` (`engine.cpp`, "soft limiter"). Output can never pass 0 dBFS, so "does not clip" really means "is not squashed by the limiter". The render test measures how often the output sits above 0.95.
- `master.volume` (0..1, default 0.7) is a preset parameter. The output gain is `2·v²`, so small-signal gain in dB is `40·log10(v)`. No factory preset sets it today. Task 4 uses it as the per-preset level.
- There is no `app/src/main/res` folder, so the app has no icon (Android draws the default).

### Decisions taken while writing this plan (report them to the user at the end)

1. **Listening happens on the PC as well as the phone.** `volt_render` plays a short phrase through every factory preset, using the real engine with nothing changed. It writes WAV files and a page, `build\render\index.html`, with one player per preset. The user can audition the whole bank with headphones and answer in `docs/PRESET-REVIEW.md` (good / adjust what / bad). The phone check is still in DEVICE-TEST, because the CK61's dynamics and the phone's speaker matter.
2. **Level balance by measurement first, ears second.**
   - Loudness = the loudest 400 ms RMS window of the phrase.
   - Target = the median of the current bank, rounded.
   - Every preset must land within −3/+2 dB of the target; FX presets are exempt from the lower bound.
   - The fix is `master.volume` inside the preset, so the sound itself is untouched.
3. **New presets go at the end (files 093–113).** Plan 4 promised the user that Program Change numbers stay stable, so the 43 existing presets keep positions 1–43. A test now pins that order. The 21 new presets are grouped by category inside their own block, giving 64 in all.
4. **Polyphony under load:**
   - **Drop by 4 voices, down to 8**, when either:
     - engine load stays above 80 % of the callback period for 250 ms; or
     - an xrun happens while load is above 50 %.
   - **Raise by 4** after 10 s below 40 %.
   - **Wait 2 s** after a drop before the next drop.
   - **Voices above the limit fade out in 15 ms** instead of using their own release, which can be seconds long. A 15 ms fade is short enough to free the CPU and long enough not to click.
   - **Display:** the top bar shows e.g. "6/12 vozes" in red while the limit is below 16.
   - **Manual cap** in settings: "Polifonia máxima" 8 / 12 / 16, for weaker phones and to test the reduction on the S25 Ultra, which may never trigger it on its own.
5. **Release build:**
   - Signed with a keystore the user creates with `tools\make-keystore.ps1`. You cannot type its passwords; the script prompts. The keystore and `keystore.properties` stay outside git.
   - No R8/minify: the APK is side-loaded, and shrinking adds JNI and serialization keep-rule risk for no real gain.
   - Version 1.0.0, versionCode 100.
6. **Debug builds become `com.criapix.volt.debug`, labelled "Volt dev"**, so they can sit next to the release. The app installed on the user's phone today is a *debug-signed* `com.criapix.volt`, and Android refuses to update it with a release-signed APK. So the user must:
   1. export their presets in the old app;
   2. uninstall it;
   3. install the release;
   4. import the presets.

   Learned MIDI mappings live in the app's private preferences and are lost; they must be re-learned. DEVICE-TEST step 1 walks through this.
7. **Log export from the app** (⋮ → "Salvar log…"). A release build is not debuggable, so `tools\pull-log.ps1` (which uses `run-as`) cannot read its log. The script now defaults to the debug package and takes `-Package` for the other one.
8. **Small fixes found in earlier plans and included here:**
   - the "Salvar preset" dialog stays reachable with the on-screen keyboard open: the text field gets a "Concluído" key that hides the keyboard, and the dialog scrolls;
   - an "Sobre o Volt" dialog shows the version, the GPL-3 licence and the third-party libraries.

### Layout at the end of this plan (new and changed files only)

```
engine/CMakeLists.txt
engine/src/engine.h  engine/src/engine.cpp  engine/src/voice.h  engine/src/voice.cpp
engine/src/governor.h
engine/tests/preset_file.h  engine/tests/test_presets.cpp  engine/tests/render.cpp
engine/tests/test_governor.cpp  engine/tests/test_engine.cpp
tools/render-presets.ps1  tools/make-keystore.ps1  tools/pull-log.ps1
app/build.gradle.kts                         (1.0.0, signing, debug suffix)
app/src/main/AndroidManifest.xml             (icon, label placeholder, adjustResize)
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/drawable/ic_launcher_monochrome.xml
app/src/main/res/values/ic_launcher_background.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/cpp/audio.h  app/src/main/cpp/audio.cpp  app/src/main/cpp/jni.cpp
app/src/main/assets/presets/factory/         (master.volume edits, 093–113, Task 9 edits)
app/src/main/kotlin/com/criapix/volt/
  Native.kt  Prefs.kt  AppLog.kt  MainActivity.kt
  ui/TopBar.kt  ui/VoltScreen.kt  ui/Dialogs.kt  ui/Diagnostics.kt
app/src/test/kotlin/com/criapix/volt/
  preset/FactoryPresetsTest.kt  ui/DiagnosticsTest.kt
docs/PRESET-REVIEW.md  docs/NOTES.md  docs/DEVICE-TEST.md  README.md
.gitignore
```

Start with:

```powershell
cd C:\Users\criap\OneDrive\Documentos\Github\volt-synth
git checkout main
git checkout -b feature/bank-release
. .\tools\env.ps1
.\tools\test-engine.ps1          # expect: 81 tests, 0 failed
```

If the count is not 81, write the real number in NOTES and carry on. The count only matters as a baseline.

---

### Task 1: Read factory presets in C++

The engine tests and the render tool need the factory JSON files on the PC. Kotlin's codec is not available there. The files are flat and machine-written:

```
{ "format": 1, "name": "…", "category": "…", "params": { "k": number, … } }
```

So a small scanner is enough; it does not need a JSON library. The same header also defines the test phrase each category is played with.

**Files:**
- Create: `engine/tests/preset_file.h`
- Create: `engine/tests/test_presets.cpp` (first test only)
- Modify: `engine/CMakeLists.txt`

**Step 1: Write the failing test**

`engine/tests/test_presets.cpp`:

```cpp
#include <cmath>
#include "dsp_probe.h"
#include "preset_file.h"
#include "rig.h"
#include "test.h"

using namespace volt;

TEST(presets_reader_parses_a_factory_file) {
  PresetFile p;
  CHECK(readPreset(std::string(VOLT_PRESETS_DIR) + "/020-lead-sync.json", p));
  CHECK(p.name == "Lead Sync");
  CHECK(p.category == "Lead");
  CHECK(p.file == "020-lead-sync");
  bool sawSync = false, sawCutoff = false;
  for (auto& [k, v] : p.params) {
    if (k == "osc1.sync") { sawSync = true; CHECK_NEAR(v, 1.0, 0); }
    if (k == "filter.cutoff") { sawCutoff = true; CHECK_NEAR(v, 6000.0, 0); }
  }
  CHECK(sawSync && sawCutoff);
}

TEST(presets_reader_lists_every_factory_file_in_library_order) {
  auto files = factoryFiles();
  CHECK(files.size() >= 43);
  CHECK(files.front().find("001-init.json") != std::string::npos);
  for (size_t i = 1; i < files.size(); ++i) CHECK(files[i - 1] < files[i]);
}
```

**Step 2: Register it and run it to see it fail**

In `engine/CMakeLists.txt`, add `tests/test_presets.cpp` to the `volt_tests` sources. Then add, inside `if (VOLT_TESTS)` after `add_executable(volt_tests …)`:

```cmake
  set(VOLT_PRESETS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/assets/presets/factory)
  target_compile_definitions(volt_tests PRIVATE VOLT_PRESETS_DIR="${VOLT_PRESETS_DIR}")
```

Run: `.\tools\test-engine.ps1 presets_`
Expected: compile error, `preset_file.h` not found.

**Step 3: Write `engine/tests/preset_file.h`**

```cpp
#pragma once
// Factory presets on the PC: a reader for the flat JSON the app writes, the phrase each category
// is auditioned with, and the code that plays a preset through an Engine. Tests and tools only.
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <initializer_list>
#include <fstream>
#include <sstream>
#include <string>
#include <utility>
#include <vector>
#include "engine.h"

namespace volt {

struct PresetFile {
  std::string file;      // "020-lead-sync" (no folder, no extension)
  std::string name;      // UTF-8, as written
  std::string category;  // "Bass", "Lead", … or ""
  std::vector<std::pair<std::string, double>> params;  // permanent name -> real value
};

namespace detail {
inline void skipWs(const std::string& s, size_t& i) { while (i < s.size() && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) ++i; }
inline bool readString(const std::string& s, size_t& i, std::string& out) {
  skipWs(s, i);
  if (i >= s.size() || s[i] != '"') return false;
  ++i;
  out.clear();
  while (i < s.size() && s[i] != '"') {
    if (s[i] == '\\' && i + 1 < s.size()) { ++i; out += s[i] == 'n' ? '\n' : s[i]; ++i; continue; }
    out += s[i++];
  }
  if (i >= s.size()) return false;
  ++i;
  return true;
}
// Finds `"key"` followed by ':' at any depth and leaves i after the colon.
inline bool findKey(const std::string& s, const std::string& key, size_t& i) {
  const std::string pat = "\"" + key + "\"";
  size_t at = s.find(pat);
  if (at == std::string::npos) return false;
  i = at + pat.size();
  skipWs(s, i);
  if (i >= s.size() || s[i] != ':') return false;
  ++i;
  return true;
}
}  // namespace detail

inline bool readPreset(const std::string& path, PresetFile& out) {
  std::ifstream in(path, std::ios::binary);
  if (!in) return false;
  std::stringstream ss;
  ss << in.rdbuf();
  const std::string s = ss.str();
  out = PresetFile{};
  out.file = std::filesystem::path(path).stem().string();
  size_t i = 0;
  if (!detail::findKey(s, "name", i) || !detail::readString(s, i, out.name)) return false;
  if (detail::findKey(s, "category", i)) detail::readString(s, i, out.category);
  if (!detail::findKey(s, "params", i)) return false;
  detail::skipWs(s, i);
  if (i >= s.size() || s[i] != '{') return false;
  ++i;
  while (true) {
    detail::skipWs(s, i);
    if (i < s.size() && s[i] == '}') break;
    std::string key;
    if (!detail::readString(s, i, key)) return false;
    detail::skipWs(s, i);
    if (i >= s.size() || s[i] != ':') return false;
    ++i;
    detail::skipWs(s, i);
    char* end = nullptr;
    const double v = std::strtod(s.c_str() + i, &end);
    if (end == s.c_str() + i) return false;
    i = (size_t)(end - s.c_str());
    out.params.emplace_back(key, v);
    detail::skipWs(s, i);
    if (i < s.size() && s[i] == ',') { ++i; continue; }
  }
  return true;
}

// Every factory file, sorted by file name: the order the app's library (and Program Change) uses.
inline std::vector<std::string> factoryFiles(const std::string& dir = VOLT_PRESETS_DIR) {
  std::vector<std::string> out;
  for (auto& e : std::filesystem::directory_iterator(dir))
    if (e.path().extension() == ".json") out.push_back(e.path().string());
  std::sort(out.begin(), out.end());
  return out;
}

inline double presetValue(const PresetFile& p, const char* name) {
  for (auto& [k, v] : p.params) if (k == name) return v;
  const int id = paramIndex(name);
  return id >= 0 ? kParams[id].def : 0.0;
}

// Loads the preset into the engine as the app does (missing parameters take their default).
// Returns the number of unknown parameter names (0 for a good file).
inline int applyPreset(Engine& e, const PresetFile& p) {
  std::vector<float> v(kNumParams);
  for (int i = 0; i < kNumParams; ++i) v[i] = norm(i, kParams[i].def);
  int unknown = 0;
  for (auto& [k, val] : p.params) {
    const int id = paramIndex(k.c_str());
    if (id < 0) { ++unknown; continue; }
    v[id] = norm(id, (float)val);
  }
  e.postPatch(v.data(), kNumParams);
  return unknown;
}

struct NoteEvent { double t; int note; float vel; bool on; };

// The phrase a preset is auditioned with. It depends on the category so a bass plays a bass line
// and a pad holds chords; a preset with the arpeggiator on holds one chord and lets it run.
inline std::vector<NoteEvent> phraseFor(const PresetFile& p) {
  std::vector<NoteEvent> ev;
  auto note = [&](double t, double dur, int n, float vel = 0.8f) { ev.push_back({t, n, vel, true}); ev.push_back({t + dur, n, 0.0f, false}); };
  auto chord = [&](double t, double dur, std::initializer_list<int> ns, float vel = 0.75f) { for (int n : ns) note(t, dur, n, vel); };
  const std::string& c = p.category;
  if (presetValue(p, "arp.on") >= 0.5) {
    chord(0.0, 4.0, {48, 52, 55});
  } else if (c == "Bass") {
    const int line[] = {36, 36, 48, 36, 43, 41, 39, 34};
    for (int i = 0; i < 8; ++i) note(0.25 * i, 0.2, line[i], i % 2 ? 0.6f : 0.9f);
  } else if (c == "Lead") {
    const int line[] = {60, 63, 65, 67, 70, 67};
    for (int i = 0; i < 6; ++i) note(0.3 * i, 0.28, line[i]);
    note(1.8, 1.2, 72, 0.9f);
  } else if (c == "Pluck") {
    const int line[] = {60, 64, 67, 72, 67, 64};
    for (int i = 0; i < 6; ++i) note(0.25 * i, 0.2, line[i], i % 2 ? 0.6f : 0.9f);
    chord(1.6, 0.6, {48, 55, 60, 64});
  } else if (c == "Keys") {
    chord(0.0, 1.0, {48, 55, 60, 64});
    chord(1.0, 1.0, {53, 57, 60, 65});
    note(2.0, 0.25, 72); note(2.3, 0.25, 74); note(2.6, 0.25, 76);
  } else if (c == "FX") {
    note(0.0, 2.0, 60);
  } else {  // Pad, Strings, Brass, Init and anything else: held chords
    chord(0.0, 2.0, {48, 55, 60, 63});
    chord(2.0, 1.5, {44, 51, 56, 60});
  }
  std::stable_sort(ev.begin(), ev.end(), [](const NoteEvent& a, const NoteEvent& b) { return a.t < b.t; });
  return ev;
}

struct Rendered {
  std::vector<float> left, right;
  double releaseSeconds = -1;  // from the last note-off until every voice was free; -1 = never
};

// Plays the phrase and keeps rendering after it: until the voices are free plus `tailAfterFree`
// seconds (reverb and delay tails), but never more than `maxTail` seconds after the last note-off.
inline Rendered renderPhrase(Engine& e, const std::vector<NoteEvent>& ev, double tailAfterFree = 2.0, double maxTail = 12.0) {
  constexpr int kChunk = 16;
  constexpr double kSr = 48000.0;
  Rendered r;
  float buf[2 * kChunk];
  e.render(buf, kChunk);  // let the patch land before the first note
  const double end = ev.empty() ? 0.0 : ev.back().t;
  size_t next = 0;
  double t = 0.0, freeAt = -1.0;
  while (true) {
    while (next < ev.size() && ev[next].t <= t) {
      const auto& n = ev[next++];
      e.post(n.on ? Event{Event::NoteOn, n.note, n.vel} : Event{Event::NoteOff, n.note, 0.0f});
    }
    e.render(buf, kChunk);
    for (int i = 0; i < kChunk; ++i) { r.left.push_back(buf[2 * i]); r.right.push_back(buf[2 * i + 1]); }
    t += kChunk / kSr;
    if (next >= ev.size()) {
      if (freeAt < 0 && e.activeVoices() == 0) { freeAt = t; r.releaseSeconds = t - end; }
      if (freeAt >= 0 && t - freeAt >= tailAfterFree) break;
      if (t - end >= maxTail) break;
    }
  }
  return r;
}

// Loudest 400 ms window (100 ms hop) of the stereo signal, in dBFS RMS.
inline double loudnessDb(const Rendered& r) {
  const size_t win = 19200, hop = 4800;
  double best = 0.0;
  for (size_t at = 0; at + win <= r.left.size(); at += hop) {
    double sum = 0.0;
    for (size_t i = at; i < at + win; ++i) sum += 0.5 * ((double)r.left[i] * r.left[i] + (double)r.right[i] * r.right[i]);
    best = std::max(best, sum / win);
  }
  return best > 0.0 ? 10.0 * std::log10(best) : -200.0;
}

// Share of samples the output limiter is squashing hard (|x| > 0.95).
inline double limitedShare(const Rendered& r) {
  size_t n = 0;
  for (size_t i = 0; i < r.left.size(); ++i) if (std::fabs(r.left[i]) > 0.95f || std::fabs(r.right[i]) > 0.95f) ++n;
  return r.left.empty() ? 0.0 : (double)n / r.left.size();
}

}  // namespace volt
```

**Step 4: Run the tests to make sure they pass**

Run: `.\tools\test-engine.ps1 presets_`
Expected: `2 tests, 0 failed`.

**Step 5: Commit**

```powershell
git add engine/tests/preset_file.h engine/tests/test_presets.cpp engine/CMakeLists.txt
git commit -m "test(engine): read factory presets on the PC"
```

---

### Task 2: Render test for every factory preset

This is the design's "para cada preset de fábrica, uma renderização que verifica que soa, não ultrapassa 0 dBFS e não produz NaN/Inf". The limiter makes "above 0 dBFS" impossible, so the test checks that the output is not squashed instead.

**Files:**
- Modify: `engine/tests/test_presets.cpp`

**Step 1: Write the test**

Append to `engine/tests/test_presets.cpp`:

```cpp
namespace {
// Task 4 turns the loudness window on and fills the target from the measured median.
constexpr bool kCheckLoudness = false;
constexpr double kLoudTarget = 0.0;  // dBFS
constexpr double kLoudBelow = 3.0, kLoudAbove = 2.0;
}  // namespace

TEST(presets_every_factory_preset_renders_cleanly) {
  for (const auto& path : factoryFiles()) {
    PresetFile p;
    if (!readPreset(path, p)) { std::printf("    FAIL cannot read %s\n", path.c_str()); ++t::failures(); continue; }
    Engine e;
    e.setSampleRate(48000.0f);
    const int unknown = applyPreset(e, p);
    auto r = renderPhrase(e, phraseFor(p));
    const double loud = loudnessDb(r);
    const double squash = limitedShare(r);
    const bool finite = probe::finite(r.left) && probe::finite(r.right);
    const bool latched = presetValue(p, "arp.hold") >= 0.5;  // a held arpeggio never lets go by design
    bool ok = unknown == 0 && finite && loud > -45.0 && squash < 0.01 && (latched || r.releaseSeconds >= 0);
    if (kCheckLoudness) {
      const bool fx = p.category == "FX";
      ok = ok && loud <= kLoudTarget + kLoudAbove && (fx || loud >= kLoudTarget - kLoudBelow);
    }
    if (!ok) {
      std::printf("    FAIL %s: unknown=%d finite=%d loud=%.1f dB squash=%.2f %% release=%.2f s\n",
                  p.file.c_str(), unknown, (int)finite, loud, 100.0 * squash, r.releaseSeconds);
      ++t::failures();
    }
  }
}
```

**Step 2: Run it**

Run: `.\tools\test-engine.ps1 presets_`

Expected: PASS. If a preset fails:
- **`unknown > 0`:** a typo in a parameter name. Fix the file. Kotlin's `FactoryPresetsTest` should already catch this, so check why it did not.
- **`finite=0`:** a real engine bug. Use @superpowers:systematic-debugging, and do not edit the preset to hide it.
- **`loud <= -45`:** the preset is nearly silent with its phrase. Look at its levels and envelope.
- **`squash >= 1 %`:** the limiter flattens it. Lower that preset's `master.volume` (start at 0.55) and write it down for Task 4.
- **`release = -1`:** the voices never free within 12 s. Look at `aenv.release` and `aenv.sustain`. A release above 8 s is almost certainly a typo.

**Step 3: Run the whole engine suite**

Run: `.\tools\test-engine.ps1`
Expected: every test passes (the baseline plus 3).

**Step 4: Commit**

```powershell
git add engine/tests/test_presets.cpp app/src/main/assets/presets/factory
git commit -m "test(engine): render every factory preset and check it sounds, stays finite and lets go"
```

---

### Task 3: `volt_render` — WAV files, listening page and CPU per preset

**Files:**
- Create: `engine/tests/render.cpp`
- Create: `tools/render-presets.ps1`
- Modify: `engine/CMakeLists.txt`

**Step 1: Write `engine/tests/render.cpp`**

```cpp
// Renders every factory preset to build/render/<file>.wav with the phrase its category is auditioned
// with, writes index.html (one player per preset) and loudness.csv, and prints a table.
// With --stress it instead holds 16 notes on every preset and prints the CPU cost, heaviest first.
// Usage: volt_render <outDir> [--stress]
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <string>
#include <vector>
#include "preset_file.h"

using namespace volt;

static void writeWav(const std::string& path, const Rendered& r) {
  std::ofstream f(path, std::ios::binary);
  const uint32_t frames = (uint32_t)r.left.size(), sr = 48000, bytes = frames * 4;
  auto u32 = [&](uint32_t v) { f.write((const char*)&v, 4); };
  auto u16 = [&](uint16_t v) { f.write((const char*)&v, 2); };
  f.write("RIFF", 4); u32(36 + bytes); f.write("WAVE", 4);
  f.write("fmt ", 4); u32(16); u16(1); u16(2); u32(sr); u32(sr * 4); u16(4); u16(16);
  f.write("data", 4); u32(bytes);
  for (uint32_t i = 0; i < frames; ++i) {
    for (float x : {r.left[i], r.right[i]}) {
      float c = x < -1.0f ? -1.0f : (x > 1.0f ? 1.0f : x);
      u16((uint16_t)(int16_t)std::lround(c * 32767.0f));
    }
  }
}

static std::string html(const std::string& s) {
  std::string o;
  for (char c : s) { if (c == '<') o += "&lt;"; else if (c == '>') o += "&gt;"; else if (c == '&') o += "&amp;"; else o += c; }
  return o;
}

static int stress() {
  struct Row { std::string file; double pct; };
  std::vector<Row> rows;
  for (const auto& path : factoryFiles()) {
    PresetFile p;
    if (!readPreset(path, p)) continue;
    Engine e;
    e.setSampleRate(48000.0f);
    applyPreset(e, p);
    static float buf[2 * 96];
    e.render(buf, 96);
    for (int n = 0; n < 16; ++n) e.post({Event::NoteOn, 36 + 3 * n, 0.8f});
    e.render(buf, 96);
    auto t0 = std::chrono::steady_clock::now();
    for (int i = 0; i < 1500; ++i) e.render(buf, 96);  // 3 s
    const double sec = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
    rows.push_back({p.file, 100.0 * sec / 3.0});
  }
  std::sort(rows.begin(), rows.end(), [](const Row& a, const Row& b) { return a.pct > b.pct; });
  std::printf("16 notes held, %% of one PC core (heaviest first):\n");
  for (auto& r : rows) std::printf("  %5.1f %%  %s\n", r.pct, r.file.c_str());
  return 0;
}

int main(int argc, char** argv) {
  if (argc < 2) { std::printf("usage: volt_render <outDir> [--stress]\n"); return 2; }
  if (argc > 2 && std::strcmp(argv[2], "--stress") == 0) return stress();
  const std::string out = argv[1];
  std::filesystem::create_directories(out);
  std::ofstream page(out + "/index.html", std::ios::binary);
  std::ofstream csv(out + "/loudness.csv", std::ios::binary);
  csv << "position,file,name,category,loudness_db,squashed_pct,release_s\n";
  page << "<!doctype html><meta charset=utf-8><title>Volt - presets de fabrica</title>"
          "<style>body{font:15px sans-serif;background:#121417;color:#e8e8e8;margin:16px}"
          "td{padding:4px 10px;border-bottom:1px solid #262a31}audio{height:32px}.d{color:#8a9099}</style>"
          "<h1>Volt - presets de fábrica</h1><p class=d>Mesma frase por categoria, motor do app sem alteração. "
          "Anote o veredito em docs/PRESET-REVIEW.md.</p><table>"
          "<tr><th>#<th>Preset<th>Categoria<th>Nível (dB)<th>Ouvir</tr>\n";
  std::printf("  #  file                          loud dB  squash %%  release s\n");
  int pos = 0;
  for (const auto& path : factoryFiles()) {
    PresetFile p;
    ++pos;
    if (!readPreset(path, p)) { std::printf("cannot read %s\n", path.c_str()); continue; }
    Engine e;
    e.setSampleRate(48000.0f);
    applyPreset(e, p);
    auto r = renderPhrase(e, phraseFor(p));
    const double loud = loudnessDb(r), squash = 100.0 * limitedShare(r);
    writeWav(out + "/" + p.file + ".wav", r);
    char line[256];
    std::snprintf(line, sizeof line, "%3d  %-28s  %7.1f  %8.2f  %9.2f\n", pos, p.file.c_str(), loud, squash, r.releaseSeconds);
    std::printf("%s", line);
    csv << pos << ',' << p.file << ",\"" << p.name << "\"," << p.category << ',' << loud << ',' << squash << ',' << r.releaseSeconds << '\n';
    char db[16];
    std::snprintf(db, sizeof db, "%.1f", loud);
    page << "<tr><td>" << pos << "<td>" << html(p.name) << "<td class=d>" << html(p.category) << "<td>" << db
         << "<td><audio controls preload=none src=\"" << p.file << ".wav\"></audio></tr>\n";
  }
  page << "</table>\n";
  std::printf("wrote %s/index.html\n", out.c_str());
  return 0;
}
```

**Step 2: Add the target**

In `engine/CMakeLists.txt`, inside `if (VOLT_TESTS)`:

```cmake
  add_executable(volt_render tests/render.cpp)
  target_link_libraries(volt_render PRIVATE volt_engine)
  target_compile_definitions(volt_render PRIVATE VOLT_PRESETS_DIR="${VOLT_PRESETS_DIR}")
```

(`VOLT_PRESETS_DIR` is the CMake variable Task 1 defined.)

**Step 3: Write `tools/render-presets.ps1`**

```powershell
# Renders every factory preset to build\render\*.wav and opens the listening page.
# Usage:  .\tools\render-presets.ps1            (WAVs + index.html)
#         .\tools\render-presets.ps1 -Stress    (CPU cost per preset, 16 notes held)
param([switch]$Stress, [switch]$NoOpen)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$root = Split-Path $PSScriptRoot -Parent
cmake -S "$root\engine" -B "$root\build\engine" -G Ninja -DCMAKE_BUILD_TYPE=Release -DVOLT_TESTS=ON
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
cmake --build "$root\build\engine" --target volt_render
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$out = "$root\build\render"
if ($Stress) { & "$root\build\engine\volt_render.exe" $out --stress; exit $LASTEXITCODE }
& "$root\build\engine\volt_render.exe" $out
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (-not $NoOpen) { Start-Process "$out\index.html" }
```

**Step 4: Run both modes**

Run: `.\tools\render-presets.ps1 -NoOpen`

Expected:
- a 43-row table and `wrote …/index.html`;
- `build\render` holds 43 `.wav` files, `index.html` and `loudness.csv`.

Sanity checks:
- Open one WAV's size: a 5–10 s stereo 16-bit file is 1–2 MB.
- Read the first lines of `index.html` and confirm the accents in the names show correctly (the file is UTF-8 with `<meta charset=utf-8>`).

Run: `.\tools\render-presets.ps1 -Stress`

Expected: 43 lines, heaviest first.

**Step 5: Record the numbers**

In `docs/NOTES.md`, add a "Plano 5" section with:
- **the full loudness table** (copy the console output inside a code block);
- **the median loudness** of the 43 presets;
- **the 5 heaviest presets** from `-Stress`, with their % of a PC core;
- **the 36 % question:** state whether `070-sino-metalico` is among the heaviest. It is the patch that was just loaded when the phone hit 36 %.

**Step 6: Commit**

```powershell
git add engine/tests/render.cpp engine/CMakeLists.txt tools/render-presets.ps1 docs/NOTES.md
git commit -m "feat(tools): render factory presets to WAV with a listening page and CPU per preset"
```

---

### Task 4: Level-balance the bank

**Files:**
- Modify: `app/src/main/assets/presets/factory/*.json` (only the ones out of the window)
- Modify: `engine/tests/test_presets.cpp` (`kLoudTarget`)

**Step 1: Fix the target**

Take the median from Task 3. Round it to a whole dB and call it `T`. Write `T` in NOTES.

Sanity check: `T` should be somewhere between −24 and −10 dBFS. If it is outside that range, write the number in NOTES and stop to ask the user. It would mean the default `master.volume` is badly placed for the whole bank, and changing the default is a design decision, not a per-preset fix.

**Step 2: Turn the window on and see what fails**

In `test_presets.cpp`, set `kCheckLoudness = true` and `kLoudTarget = T` (the number).

Run: `.\tools\test-engine.ps1 presets_every`

Expected: FAIL lines for the presets outside `[T−3, T+2]`. FX presets are only checked against the upper bound.

**Step 3: Fix each failing preset with `master.volume`**

For each failing preset with loudness `L`, add or change `"master.volume"` in its JSON:

```
new = old · 10^((T − L) / 40)       (old = 0.7 when the file does not set it)
```

- Round to 2 decimals and clamp to 0.20 … 1.00.
- Put the key at the end of the `params` object, before the effect keys if there are any. Match the file's existing formatting.
- **When the volume would need to go above 1.00:** leave it at 1.00 and raise the preset's own levels instead. Use the oscillator levels, or `mix.sub` on basses. Never use `mix.drive`, because it changes the sound. Write each such case in NOTES.

The limiter compresses loud presets, so one pass may not be enough. Re-run the test and repeat the formula on what still fails, at most three passes.

**Step 4: Run everything**

Run:
- `.\tools\test-engine.ps1` (all pass)
- `.\gradlew.bat :app:testDebugUnitTest` (`FactoryPresetsTest` checks ranges)

**Step 5: Re-render and record**

Run: `.\tools\render-presets.ps1 -NoOpen`

In NOTES, under "Plano 5", list:
- which presets got a `master.volume` and its value;
- the new spread (min and max loudness).

**Step 6: Commit**

```powershell
git add app/src/main/assets/presets/factory engine/tests/test_presets.cpp docs/NOTES.md
git commit -m "feat(presets): balance factory preset levels around the bank median"
```

---

### Task 5: Voice limit in the engine

**Files:**
- Modify: `engine/src/voice.h`, `engine/src/voice.cpp`
- Modify: `engine/src/engine.h`, `engine/src/engine.cpp`
- Test: `engine/tests/test_engine.cpp`

**Step 1: Write the failing tests**

Append to `engine/tests/test_engine.cpp`:

```cpp
TEST(engine_voice_limit_caps_new_notes) {
  Rig r;
  r.e.setVoiceLimit(8);
  r.play(0.01f);
  for (int n = 40; n < 52; ++n) r.on(n);
  r.play(0.1f);
  CHECK(r.e.activeVoices() == 8);
  CHECK(r.e.voiceLimit() == 8);
}

TEST(engine_lowering_the_limit_fades_extra_voices_quickly_without_a_click) {
  Rig r;
  r.set(kAenvRelease, 5.0f);  // a long pad release must not keep the extra voices alive
  r.set(kOsc1Wave, 3);
  for (int n = 40; n < 56; ++n) r.on(n);
  r.play(0.3f);
  CHECK(r.e.activeVoices() == 16);
  r.e.setVoiceLimit(8);
  auto x = r.play(0.1f);
  CHECK(r.e.activeVoices() == 8);
  CHECK(probe::finite(x));
  double step = 0;
  for (size_t i = 1; i < x.size(); ++i) step = std::fmax(step, std::fabs((double)x[i] - x[i - 1]));
  CHECK(step < 0.1);
}

TEST(engine_raising_the_limit_again_allows_sixteen_voices) {
  Rig r;
  r.e.setVoiceLimit(8);
  r.play(0.01f);
  r.e.setVoiceLimit(16);
  r.play(0.01f);
  for (int n = 40; n < 60; ++n) r.on(n);
  r.play(0.1f);
  CHECK(r.e.activeVoices() == 16);
}

TEST(engine_unison_uses_no_more_voices_than_the_limit) {
  Rig r;
  r.set(kVoiceMode, 3);
  r.set(kVoiceUnison, 8);
  r.e.setVoiceLimit(4);
  r.play(0.01f);
  r.on(60);
  r.play(0.1f);
  CHECK(r.e.activeVoices() == 4);
}
```

Make sure `test_engine.cpp` includes `<cmath>`; `dsp_probe.h` is already included.

**Step 2: Run the tests to see them fail**

Run: `.\tools\test-engine.ps1 engine_`
Expected: compile error, `setVoiceLimit` is not a member.

**Step 3: Add a quick release to `Voice`**

In `voice.h`, in the public part next to `release()`:

```cpp
  void quickRelease();      // release with a fixed 15 ms fade (voices cut by the polyphony limit)
```

and in the private part:

```cpp
  bool quick_ = false;
```

In `voice.cpp`:
- in `Voice::start`, after `active = true; held = true; sustained = false;`, add `quick_ = false;`;
- add the new function:

```cpp
void Voice::quickRelease() {
  release();
  quick_ = true;
}
```

- in `Voice::pre`, change the line `aenv_.set(p[kAenvAttack], p[kAenvDecay], p[kAenvSustain], p[kAenvRelease], c.dt);` to:

```cpp
  aenv_.set(p[kAenvAttack], p[kAenvDecay], p[kAenvSustain], quick_ ? 0.015f : p[kAenvRelease], c.dt);
```

**Step 4: Add the limit to `Engine`**

In `engine.h`, public part:

```cpp
  // Any thread. How many voices new notes may use (4..16). Voices above a lowered limit fade out in 15 ms.
  void setVoiceLimit(int n) { voiceLimitReq_.store(n < 4 ? 4 : (n > kMaxVoices ? kMaxVoices : n), std::memory_order_relaxed); }
  int voiceLimit() const { return voiceLimitReq_.load(std::memory_order_relaxed); }
```

Private part:

```cpp
  std::atomic<int> voiceLimitReq_{kMaxVoices};
  int voiceLimit_ = kMaxVoices;  // audio thread's copy
```

In `engine.cpp`:

1. At the top of `Engine::renderBlock()` (first line):

   ```cpp
     const int limit = voiceLimitReq_.load(std::memory_order_relaxed);
     if (limit != voiceLimit_) {
       voiceLimit_ = limit;
       for (int i = limit; i < kMaxVoices; ++i)
         if (voices_[i].active) voices_[i].quickRelease();
     }
   ```

2. `allocPoly`: every loop over `voices_` must look only at the first `voiceLimit_` voices. Rewrite it:

   ```cpp
   Voice* Engine::allocPoly(int note) {
     const int n = voiceLimit_;
     for (int i = 0; i < n; ++i) if (voices_[i].active && voices_[i].note == note) return &voices_[i];  // same key: reuse
     for (int i = 0; i < n; ++i) if (!voices_[i].active) return &voices_[i];
     Voice* best = nullptr;
     for (int i = 0; i < n; ++i) if (voices_[i].releasing() && (!best || voices_[i].age < best->age)) best = &voices_[i];
     if (best) return best;
     best = &voices_[0];
     for (int i = 0; i < n; ++i) if (voices_[i].age < best->age) best = &voices_[i];
     return best;
   }
   ```

   A key whose voice sits above a lowered limit is still fading in that voice. Pressing the key again starts a new voice inside the limit, which is fine.

3. `noteOn`, mono family: change `const int n = mode == kUnison ? (int)value_[kVoiceUnison] : 1;` to:

   ```cpp
       int n = mode == kUnison ? (int)value_[kVoiceUnison] : 1;
       if (n > voiceLimit_) n = voiceLimit_;
   ```

Voices stay packed at the low indices, so the quads above the limit have no active voice and `renderBlock` skips them (`if (!any) continue;`). That is where the CPU is saved.

**Step 5: Run the tests**

Run: `.\tools\test-engine.ps1`
Expected: all pass, including `alloc_` (nothing new allocates).

If the click test fails at the 0.1 limit:
1. Print `step`.
2. A sine at 0.3 s with 16 voices summed goes through `tanh`, so a 15 ms fade should step far below 0.1.
3. Look first for a voice that was killed instead of released.

**Step 6: Commit**

```powershell
git add engine/src/voice.h engine/src/voice.cpp engine/src/engine.h engine/src/engine.cpp engine/tests/test_engine.cpp
git commit -m "feat(engine): adjustable voice limit with a quick fade for cut voices"
```

---

### Task 6: `LoadGovernor`

The governor holds pure logic with no Android in it, so it is tested on the PC.

**Files:**
- Create: `engine/src/governor.h`
- Create: `engine/tests/test_governor.cpp`
- Modify: `engine/CMakeLists.txt` (add the test file to `volt_tests`)

**Step 1: Write the failing tests**

`engine/tests/test_governor.cpp`:

```cpp
#include "governor.h"
#include "test.h"

using namespace volt;

namespace {
// Feeds the same load for `seconds` in 2 ms callbacks; returns the last limit.
int feed(LoadGovernor& g, float load, float seconds, int xruns = 0) {
  int limit = g.limit();
  for (float t = 0; t < seconds; t += 0.002f) limit = g.update(load, xruns, 0.002f);
  return limit;
}
}  // namespace

TEST(governor_keeps_sixteen_voices_under_normal_load) {
  LoadGovernor g;
  CHECK(feed(g, 0.3f, 5.0f) == 16);
}

TEST(governor_drops_four_voices_after_a_quarter_second_of_overload) {
  LoadGovernor g;
  CHECK(feed(g, 0.9f, 0.2f) == 16);
  CHECK(feed(g, 0.9f, 0.1f) == 12);
}

TEST(governor_waits_two_seconds_between_drops_and_stops_at_eight) {
  LoadGovernor g;
  feed(g, 0.9f, 0.3f);
  CHECK(feed(g, 0.9f, 1.5f) == 12);
  CHECK(feed(g, 0.9f, 1.0f) == 8);
  CHECK(feed(g, 0.9f, 10.0f) == 8);
}

TEST(governor_drops_on_an_xrun_only_when_the_load_is_already_high) {
  LoadGovernor g;
  feed(g, 0.2f, 0.1f, 0);
  CHECK(g.update(0.2f, 1, 0.002f) == 16);  // low load: a scheduling hiccup, the buffer tuner's job
  CHECK(g.update(0.6f, 2, 0.002f) == 12);
}

TEST(governor_ignores_an_xrun_counter_that_restarted) {
  LoadGovernor g;
  feed(g, 0.6f, 0.1f, 40);
  CHECK(feed(g, 0.6f, 0.1f, 0) == 16);  // a reopened stream counts from zero again
}

TEST(governor_raises_the_limit_after_ten_quiet_seconds) {
  LoadGovernor g;
  feed(g, 0.9f, 0.3f);
  CHECK(g.limit() == 12);
  CHECK(feed(g, 0.3f, 9.0f) == 12);
  CHECK(feed(g, 0.3f, 1.5f) == 16);
}
```

Add `tests/test_governor.cpp` to the `volt_tests` sources.

**Step 2: Run the tests to see them fail**

Run: `.\tools\test-engine.ps1 governor_`
Expected: compile error, `governor.h` not found.

**Step 3: Write `engine/src/governor.h`**

```cpp
#pragma once
#include <cstdint>
#include "util.h"

namespace volt {

// Lowers the polyphony when the audio callback runs out of time and raises it back once there is
// room again. Called from the audio callback: no allocation, no locks.
class LoadGovernor {
 public:
  static constexpr int kMin = 8, kStep = 4;
  static constexpr float kHigh = 0.80f;      // load (fraction of the callback period) that counts as overload
  static constexpr float kXrunLoad = 0.50f;  // an xrun above this load also counts
  static constexpr float kLow = 0.40f;       // load that counts as room to grow
  static constexpr float kHighTime = 0.25f;  // seconds of overload before a drop
  static constexpr float kLowTime = 10.0f;   // seconds of room before a raise
  static constexpr float kHold = 2.0f;       // seconds after a drop before the next one

  // load: smoothed engine load; xruns: the stream's running xrun count; dt: seconds since the last call.
  int update(float load, int32_t xruns, float dt) {
    const bool newXrun = lastXruns_ >= 0 && xruns > lastXruns_;
    lastXruns_ = xruns;  // also resets when a reopened stream counts from zero
    sinceDrop_ += dt;
    high_ = load > kHigh ? high_ + dt : 0.0f;
    low_ = load < kLow ? low_ + dt : 0.0f;
    const bool overloaded = high_ >= kHighTime || (newXrun && load > kXrunLoad);
    if (overloaded && limit_ > kMin && sinceDrop_ >= kHold) {
      limit_ -= kStep;
      sinceDrop_ = 0.0f; high_ = 0.0f; low_ = 0.0f;
    } else if (low_ >= kLowTime && limit_ < kMaxVoices) {
      limit_ += kStep;
      low_ = 0.0f;
    }
    return limit_;
  }

  int limit() const { return limit_; }

 private:
  int limit_ = kMaxVoices;
  int32_t lastXruns_ = -1;
  float sinceDrop_ = 1e9f, high_ = 0.0f, low_ = 0.0f;
};

}  // namespace volt
```

**Step 4: Run the tests**

Run: `.\tools\test-engine.ps1 governor_`
Expected: `6 tests, 0 failed`.

The tests step time in 2 ms increments, so float accumulation can put an edge one step early or late. If a boundary check fails by one step:
1. Check the arithmetic first.
2. If the logic is right, move the *test's* time a little away from the boundary (for example 0.2 → 0.18) and write why in NOTES.

**Step 5: Commit**

```powershell
git add engine/src/governor.h engine/tests/test_governor.cpp engine/CMakeLists.txt
git commit -m "feat(engine): load governor that lowers and restores polyphony"
```

---

### Task 7: Governor in the audio callback, manual cap, and the limit on screen

**Files:**
- Modify: `app/src/main/cpp/audio.h`, `app/src/main/cpp/audio.cpp`, `app/src/main/cpp/jni.cpp`
- Modify: `app/src/main/kotlin/com/criapix/volt/Native.kt`, `Prefs.kt`, `MainActivity.kt`
- Modify: `app/src/main/kotlin/com/criapix/volt/ui/Diagnostics.kt`, `TopBar.kt`, `VoltScreen.kt`, `Dialogs.kt`
- Create: `app/src/test/kotlin/com/criapix/volt/ui/DiagnosticsTest.kt`

**Step 1: Write the failing Kotlin test**

`app/src/test/kotlin/com/criapix/volt/ui/DiagnosticsTest.kt`:

```kotlin
package com.criapix.volt.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
  private val base = "running=1\nsampleRate=48000\nload=0.25\nvoices=3\n"

  @Test fun showsTheVoiceLimit() {
    val s = formatInfo(base + "voiceLimit=12\nmaxVoices=16\n")
    assertTrue(s, s.contains("Polifonia: 12"))
    assertTrue(s, s.contains("Carga do motor: 25 %"))
  }

  @Test fun stoppedAudioSaysSo() {
    assertTrue(formatInfo("running=0\n") == "Áudio parado")
  }
}
```

**Step 2: Run it to see it fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*DiagnosticsTest*"`
Expected: FAIL, the output has no "Polifonia".

**Step 3: Add the label**

In `Diagnostics.kt`, add `"voiceLimit" to "Polifonia"` to `INFO_LABELS`, right after `"voices" to "Vozes"`.

Run the test again. Expected: PASS.

**Step 4: Native side**

`audio.h`:
- add `#include "governor.h"`;
- public: `void setMaxVoices(int n) { maxVoices_.store(n, std::memory_order_relaxed); }`;
- private:

```cpp
  volt::LoadGovernor governor_;            // audio thread only (reset in open(), when no callback runs)
  std::atomic<int> maxVoices_{volt::kMaxVoices};  // the user's cap from the settings
```

`audio.cpp`:
- In `open()`, just before `r = stream_->requestStart();`, add `governor_ = volt::LoadGovernor{};`.
- In `onAudioReady`, replace the `load_.store(...)` line and add the governor after it:

```cpp
  const float load = 0.95f * load_.load(std::memory_order_relaxed) + 0.05f * used;
  load_.store(load, std::memory_order_relaxed);
  auto x = stream->getXRunCount();
  const int governed = governor_.update(load, x ? x.value() : 0, (float)period);
  const int cap = maxVoices_.load(std::memory_order_relaxed);
  engine_.setVoiceLimit(governed < cap ? governed : cap);
```

- In `info()`, after `add("voices", …)`:

```cpp
  add("voiceLimit", std::to_string(engine_.voiceLimit()));
  add("maxVoices", std::to_string(maxVoices_.load()));
```

`getXRunCount()` on AAudio reads a counter; `LatencyTuner::tune()` already calls it every callback, so this adds nothing new to the audio thread.

`jni.cpp`:

```cpp
JNI(jint, voiceLimit)(JNIEnv*, jobject) { return g_audio.engine().voiceLimit(); }
JNI(void, setMaxVoices)(JNIEnv*, jobject, jint n) { g_audio.setMaxVoices(n); }
```

`Native.kt`:

```kotlin
  /** Voices new notes may use right now (16, or less while the phone is overloaded or capped). */
  external fun voiceLimit(): Int
  /** The user's polyphony cap: 8, 12 or 16. */
  external fun setMaxVoices(n: Int)
```

Also extend the `audioInfo` KDoc list with `voiceLimit, maxVoices`.

**Step 5: Preference and settings row**

`Prefs.kt`:

```kotlin
  /** Polyphony cap chosen in the settings: 8, 12 or 16. */
  var maxVoices: Int
    get() = p.getInt("maxVoices", 16)
    set(v) = p.edit().putInt("maxVoices", v).apply()
```

`VoltScreen.kt`, `ScreenIo`: add two constructor parameters with defaults, so nothing else breaks:

```kotlin
  val voiceLimit: () -> Int = { 16 },
  val maxVoices: MaxVoices = MaxVoices.Fixed,
```

and, in the same file below `ScreenIo`:

```kotlin
/** The polyphony cap setting, behind an interface so the UI does not touch Prefs or Native. */
interface MaxVoices {
  var value: Int
  object Fixed : MaxVoices { override var value: Int = 16 }
}
```

`MainActivity.kt`:
- in `onCreate`, after `prefs = Prefs(this)`, add `Native.setMaxVoices(prefs.maxVoices)`;
- in the `ScreenIo(...)` call add:

```kotlin
      voiceLimit = Native::voiceLimit,
      maxVoices = object : com.criapix.volt.ui.MaxVoices {
        override var value: Int
          get() = prefs.maxVoices
          set(v) { prefs.maxVoices = v; Native.setMaxVoices(v); AppLog.log("polifonia máxima: $v") }
      },
```

`Dialogs.kt`, `SettingsDialog`: below the "Vibração" row, add:

```kotlin
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Polifonia máxima", modifier = Modifier.weight(1f))
          var cap by remember { mutableStateOf(io.maxVoices.value) }
          listOf(8, 12, 16).forEach { n ->
            Text(
              "$n", fontSize = 14.sp,
              color = if (n == cap) Color.Black else VoltColors.text,
              modifier = Modifier.background(if (n == cap) VoltColors.accent else VoltColors.raised, RoundedCornerShape(14.dp))
                .clickable { cap = n; io.maxVoices.value = n }.padding(horizontal = 14.dp, vertical = 6.dp),
            )
          }
        }
```

**Step 6: Top bar**

`TopBar.kt`:
- add a parameter `voiceLimit: Int = 16` after `voices: Int`;
- replace the `Text("$voices vozes", …)` line with:

```kotlin
    val reduced = voiceLimit < 16
    Text(
      if (reduced) "$voices/$voiceLimit vozes" else "$voices vozes",
      fontSize = 11.sp, lineHeight = 14.sp, color = if (reduced) VoltColors.danger else VoltColors.dim,
      modifier = Modifier.width(66.dp),
    )
```

`VoltScreen.kt`:
- add `var voiceLimit by remember { mutableIntStateOf(16) }` next to `voices`;
- in the polling loop: `if (n++ % 8 == 0) { voices = io.voices(); voiceLimit = io.voiceLimit() }`;
- pass `voiceLimit = voiceLimit` to `TopBar`.

**Step 7: Build and look**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

On the emulator (`adb -s emulator-5554`):
1. Install the APK.
2. Open ⋮ and set "Polifonia máxima" to 8.
3. Open the keyboard drawer and tap a key.

Expected: the bar reads "1/8 vozes" in red, and the diagnostics line shows "Polifonia: 8". Set 16 again and the bar returns to grey "… vozes". Save a screenshot as `build/polyphony.png` (not versioned).

**Step 8: Commit**

```powershell
git add app/src/main/cpp app/src/main/kotlin app/src/test/kotlin/com/criapix/volt/ui/DiagnosticsTest.kt
git commit -m "feat(app): polyphony governor in the audio callback, manual cap, limit shown in the top bar"
```

---

### Task 8: Twenty-one new factory presets (093–113)

These fill the design's coverage list:
- 3-oscillator Ladder basses and leads (Minimoog);
- poly-mod and sync timbres (Prophet);
- chorus pads, brass and strings (Jupiter / Juno);
- plucks and arpeggios with delay;
- more FX.

They go at the end (decision 3), grouped by category inside the block.

**Files:**
- Create: 21 files in `app/src/main/assets/presets/factory/`
- Modify: `app/src/test/kotlin/com/criapix/volt/preset/FactoryPresetsTest.kt`

**Step 1: Write the failing tests**

In `FactoryPresetsTest.kt`:

1. In `thereIsAStarterSetAndInitComesFirst`, change `files.size >= 40` to `files.size >= 64`.
2. Add:

```kotlin
  @Test fun theFirst43KeepTheirPositionsForProgramChange() {
    // Program Change n loads position n+1 (plan 3). These 43 shipped before 1.0 and must never move.
    val pinned = listOf(
      "001-init", "010-baixo-gordo", "011-baixo-redondo", "012-baixo-acido", "013-baixo-sub", "014-baixo-funk", "015-baixo-pulso",
      "020-lead-sync", "021-lead-classico", "022-lead-quadrado", "023-lead-assobio", "024-lead-trance",
      "030-pad-analogico", "031-pad-pwm", "032-pad-escuro", "033-pad-vidro", "034-pad-evolutivo",
      "040-metais-poly", "041-metais-salto", "042-metais-lentos", "043-stab-de-metais",
      "050-cordas-suaves", "051-cordas-ensemble", "052-violoncelo",
      "060-pluck-vidro", "061-marimba", "062-harpa", "063-pizzicato",
      "070-sino-metalico", "071-piano-eletrico", "072-clavinete", "073-orgao", "074-cravo", "075-vibrafone",
      "081-vento", "082-laser", "083-bumbo", "084-caixa", "085-scifi-aleatorio", "086-sirene",
      "090-arpejo-classico", "091-cordas-phaser", "092-baixo-distorcido",
    )
    assertEquals(pinned, files.take(43).map { it.name.removeSuffix(".json") })
  }
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*FactoryPresetsTest*"`

Expected:
- `thereIsAStarterSet…` FAILS (43 < 64);
- `theFirst43…` PASSES. If it fails, the list above has a typo against `git ls-files`. Fix the list, not the files.

**Step 2: Write the 21 files**

Same format as the existing files: UTF-8 without BOM. Write them with the Write tool, not with PowerShell `Set-Content`. Each name is unique.

`093-baixo-tres-osciladores.json`
```json
{ "format": 1, "name": "Baixo Três Osciladores", "category": "Bass", "params": {
  "voice.mode": 1, "voice.glide": 0.03, "voice.glide_legato": 1, "voice.drift": 0.15,
  "osc1.wave": 0, "osc1.octave": -1,
  "osc2.wave": 0, "osc2.octave": -1, "osc2.fine": -6, "osc2.level": 0.9,
  "osc3.wave": 0, "osc3.octave": -2, "osc3.fine": 4, "osc3.level": 0.7,
  "filter.model": 0, "filter.cutoff": 260, "filter.reso": 0.25, "filter.env": 0.38, "filter.keytrack": 0.4,
  "fenv.attack": 0.001, "fenv.decay": 0.28, "fenv.sustain": 0.2, "fenv.release": 0.1,
  "aenv.attack": 0.002, "aenv.decay": 0.4, "aenv.sustain": 0.9, "aenv.release": 0.08, "aenv.vel": 0.35 } }
```

`094-baixo-sync.json`
```json
{ "format": 1, "name": "Baixo Sync", "category": "Bass", "params": {
  "voice.mode": 1, "voice.drift": 0.05,
  "osc1.wave": 0, "osc1.octave": -1, "osc1.sync": 1,
  "osc2.wave": 0, "osc2.octave": -1, "osc2.level": 0.3,
  "mix.sub": 0.5,
  "pmod.fenv.pitch": 0.3,
  "filter.model": 2, "filter.cutoff": 900, "filter.reso": 0.2, "filter.env": 0.25, "filter.keytrack": 0.5,
  "fenv.attack": 0.001, "fenv.decay": 0.25, "fenv.sustain": 0.0, "fenv.release": 0.1,
  "aenv.attack": 0.002, "aenv.decay": 0.3, "aenv.sustain": 0.8, "aenv.release": 0.08, "aenv.vel": 0.4 } }
```

`095-baixo-elastico.json`
```json
{ "format": 1, "name": "Baixo Elástico", "category": "Bass", "params": {
  "voice.mode": 2, "voice.glide": 0.09, "voice.glide_legato": 1,
  "osc1.wave": 2, "osc1.octave": -1,
  "osc2.wave": 3, "osc2.octave": -2, "osc2.level": 0.8,
  "filter.model": 0, "filter.cutoff": 150, "filter.reso": 0.45, "filter.env": 0.5, "filter.keytrack": 0.3,
  "fenv.attack": 0.001, "fenv.decay": 0.18, "fenv.sustain": 0.05, "fenv.release": 0.1,
  "aenv.attack": 0.002, "aenv.decay": 0.6, "aenv.sustain": 0.7, "aenv.release": 0.1, "aenv.vel": 0.3 } }
```

`096-lead-tres-osciladores.json`
```json
{ "format": 1, "name": "Lead Três Osciladores", "category": "Lead", "params": {
  "voice.mode": 2, "voice.glide": 0.06, "voice.drift": 0.15,
  "osc1.wave": 0,
  "osc2.wave": 0, "osc2.fine": 7, "osc2.level": 0.9,
  "osc3.wave": 0, "osc3.octave": -1, "osc3.level": 0.6,
  "mix.drive": 0.2,
  "filter.model": 0, "filter.cutoff": 1400, "filter.reso": 0.3, "filter.env": 0.25, "filter.keytrack": 0.6,
  "fenv.attack": 0.005, "fenv.decay": 0.5, "fenv.sustain": 0.5, "fenv.release": 0.3,
  "aenv.attack": 0.004, "aenv.decay": 0.3, "aenv.sustain": 1.0, "aenv.release": 0.25,
  "lfo1.wave": 1, "lfo1.rate": 5.2, "lfo1.pitch": 0.12, "lfo1.wheel": 1.0,
  "fx.delay.on": 1, "fx.delay.time": 0.33, "fx.delay.feedback": 0.25, "fx.delay.mix": 0.15 } }
```

`097-lead-poly-mod.json`
```json
{ "format": 1, "name": "Lead Poly-Mod", "category": "Lead", "params": {
  "voice.mode": 2, "voice.glide": 0.02,
  "osc1.wave": 1, "osc1.pw": 0.4,
  "osc2.wave": 2, "osc2.octave": -1, "osc2.semi": 7, "osc2.level": 0.0,
  "pmod.osc2.pitch": 0.35, "pmod.fenv.pitch": 0.15,
  "filter.model": 3, "filter.cutoff": 2500, "filter.reso": 0.25, "filter.env": 0.2, "filter.keytrack": 0.6,
  "fenv.attack": 0.002, "fenv.decay": 0.7, "fenv.sustain": 0.3, "fenv.release": 0.3,
  "aenv.attack": 0.003, "aenv.decay": 0.4, "aenv.sustain": 0.9, "aenv.release": 0.3,
  "lfo1.wave": 0, "lfo1.rate": 5.5, "lfo1.pitch": 0.1, "lfo1.wheel": 1.0,
  "fx.reverb.on": 1, "fx.reverb.decay": 1.8, "fx.reverb.mix": 0.15 } }
```

`098-lead-unison.json`
```json
{ "format": 1, "name": "Lead Unison", "category": "Lead", "params": {
  "voice.mode": 3, "voice.unison": 6, "voice.detune": 0.35, "voice.spread": 0.8, "voice.glide": 0.03,
  "osc1.wave": 0,
  "filter.model": 2, "filter.cutoff": 3500, "filter.reso": 0.15, "filter.env": 0.15, "filter.keytrack": 0.5,
  "fenv.attack": 0.003, "fenv.decay": 0.6, "fenv.sustain": 0.6, "fenv.release": 0.3,
  "aenv.attack": 0.004, "aenv.decay": 0.3, "aenv.sustain": 1.0, "aenv.release": 0.3,
  "lfo1.wave": 0, "lfo1.rate": 5.0, "lfo1.pitch": 0.08, "lfo1.wheel": 1.0,
  "fx.reverb.on": 1, "fx.reverb.decay": 2.2, "fx.reverb.mix": 0.2 } }
```

`099-lead-oitavas.json`
```json
{ "format": 1, "name": "Lead em Oitavas", "category": "Lead", "params": {
  "voice.mode": 2, "voice.glide": 0.04,
  "osc1.wave": 1, "osc1.pw": 0.3,
  "osc2.wave": 0, "osc2.octave": 1, "osc2.fine": 3, "osc2.level": 0.55,
  "filter.model": 1, "filter.cutoff": 2200, "filter.reso": 0.2, "filter.env": 0.2, "filter.keytrack": 0.6,
  "fenv.attack": 0.002, "fenv.decay": 0.5, "fenv.sustain": 0.5, "fenv.release": 0.25,
  "aenv.attack": 0.003, "aenv.decay": 0.3, "aenv.sustain": 1.0, "aenv.release": 0.25,
  "lfo1.wave": 1, "lfo1.rate": 5.5, "lfo1.pitch": 0.1, "lfo1.wheel": 1.0,
  "fx.delay.on": 1, "fx.delay.time": 0.25, "fx.delay.feedback": 0.3, "fx.delay.mix": 0.18 } }
```

`100-pad-com-chorus.json`
```json
{ "format": 1, "name": "Pad com Chorus", "category": "Pad", "params": {
  "voice.drift": 0.2,
  "osc1.wave": 1, "osc1.pw": 0.35,
  "mix.sub": 0.4,
  "filter.model": 1, "filter.cutoff": 1600, "filter.reso": 0.15, "filter.env": 0.15, "filter.keytrack": 0.4,
  "fenv.attack": 0.6, "fenv.decay": 1.5, "fenv.sustain": 0.7, "fenv.release": 1.5,
  "aenv.attack": 0.5, "aenv.decay": 1.0, "aenv.sustain": 0.9, "aenv.release": 1.8,
  "lfo1.wave": 1, "lfo1.rate": 0.6, "lfo1.pw": 0.25,
  "fx.chorus.on": 1, "fx.chorus.mode": 2, "fx.chorus.mix": 0.5,
  "fx.reverb.on": 1, "fx.reverb.decay": 3.0, "fx.reverb.mix": 0.25 } }
```

`101-pad-varredura.json`
```json
{ "format": 1, "name": "Pad Varredura", "category": "Pad", "params": {
  "voice.drift": 0.25,
  "osc1.wave": 0, "osc1.fine": -5,
  "osc2.wave": 0, "osc2.fine": 5, "osc2.level": 0.9,
  "filter.model": 2, "filter.cutoff": 300, "filter.reso": 0.35, "filter.env": 0.4, "filter.keytrack": 0.3,
  "fenv.attack": 3.5, "fenv.decay": 3.0, "fenv.sustain": 0.5, "fenv.release": 2.5,
  "aenv.attack": 1.0, "aenv.decay": 1.0, "aenv.sustain": 0.9, "aenv.release": 2.5,
  "fx.phaser.on": 1, "fx.phaser.rate": 0.15, "fx.phaser.depth": 0.8, "fx.phaser.feedback": 0.5, "fx.phaser.mix": 0.45,
  "fx.reverb.on": 1, "fx.reverb.decay": 4.0, "fx.reverb.mix": 0.3 } }
```

`102-pad-coral.json`
```json
{ "format": 1, "name": "Pad Coral", "category": "Pad", "params": {
  "voice.drift": 0.3,
  "osc1.wave": 1, "osc1.pw": 0.45,
  "osc2.wave": 0, "osc2.fine": 9, "osc2.level": 0.6,
  "hpf.cutoff": 180,
  "filter.model": 1, "filter.cutoff": 1100, "filter.reso": 0.45, "filter.env": 0.05, "filter.keytrack": 0.6,
  "aenv.attack": 0.7, "aenv.decay": 1.0, "aenv.sustain": 0.9, "aenv.release": 2.0,
  "lfo1.wave": 0, "lfo1.rate": 4.8, "lfo1.delay": 0.8, "lfo1.pitch": 0.06,
  "lfo2.wave": 1, "lfo2.rate": 0.2, "lfo2.cutoff": 0.08,
  "fx.chorus.on": 1, "fx.chorus.mode": 1, "fx.chorus.mix": 0.45,
  "fx.reverb.on": 1, "fx.reverb.decay": 5.0, "fx.reverb.damp": 0.6, "fx.reverb.mix": 0.35 } }
```

`103-metais-com-chorus.json`
```json
{ "format": 1, "name": "Metais com Chorus", "category": "Brass", "params": {
  "voice.drift": 0.2,
  "osc1.wave": 0,
  "osc2.wave": 0, "osc2.fine": -7, "osc2.level": 0.85,
  "filter.model": 2, "filter.cutoff": 450, "filter.reso": 0.15, "filter.env": 0.45, "filter.keytrack": 0.5, "filter.vel": 0.25,
  "fenv.attack": 0.06, "fenv.decay": 0.6, "fenv.sustain": 0.5, "fenv.release": 0.35,
  "aenv.attack": 0.03, "aenv.decay": 0.3, "aenv.sustain": 0.85, "aenv.release": 0.35, "aenv.vel": 0.4,
  "fx.chorus.on": 1, "fx.chorus.mode": 1, "fx.chorus.mix": 0.4,
  "fx.reverb.on": 1, "fx.reverb.decay": 2.0, "fx.reverb.mix": 0.18 } }
```

`104-trompas.json`
```json
{ "format": 1, "name": "Trompas", "category": "Brass", "params": {
  "voice.drift": 0.15,
  "osc1.wave": 0,
  "osc2.wave": 1, "osc2.pw": 0.4, "osc2.fine": 4, "osc2.level": 0.6,
  "filter.model": 0, "filter.cutoff": 380, "filter.reso": 0.1, "filter.env": 0.32, "filter.keytrack": 0.5, "filter.vel": 0.3,
  "fenv.attack": 0.15, "fenv.decay": 0.8, "fenv.sustain": 0.6, "fenv.release": 0.4,
  "aenv.attack": 0.12, "aenv.decay": 0.4, "aenv.sustain": 0.9, "aenv.release": 0.45, "aenv.vel": 0.5,
  "lfo1.wave": 0, "lfo1.rate": 4.5, "lfo1.delay": 1.0, "lfo1.pitch": 0.04,
  "fx.reverb.on": 1, "fx.reverb.decay": 2.5, "fx.reverb.mix": 0.25 } }
```

`105-cordas-altas.json`
```json
{ "format": 1, "name": "Cordas Altas", "category": "Strings", "params": {
  "voice.drift": 0.25,
  "osc1.wave": 0, "osc1.fine": -4,
  "osc2.wave": 0, "osc2.octave": 1, "osc2.fine": 5, "osc2.level": 0.7,
  "hpf.cutoff": 300,
  "filter.model": 1, "filter.cutoff": 4000, "filter.reso": 0.1, "filter.env": 0.05, "filter.keytrack": 0.5,
  "aenv.attack": 0.35, "aenv.decay": 0.5, "aenv.sustain": 0.9, "aenv.release": 1.2,
  "lfo1.wave": 1, "lfo1.rate": 5.5, "lfo1.delay": 0.6, "lfo1.pitch": 0.04,
  "fx.chorus.on": 1, "fx.chorus.mode": 2, "fx.chorus.mix": 0.5,
  "fx.reverb.on": 1, "fx.reverb.decay": 3.0, "fx.reverb.mix": 0.3 } }
```

`106-cordas-lentas.json`
```json
{ "format": 1, "name": "Cordas Lentas", "category": "Strings", "params": {
  "voice.drift": 0.3,
  "osc1.wave": 0, "osc1.fine": -6,
  "osc2.wave": 0, "osc2.fine": 6, "osc2.level": 0.9,
  "mix.sub": 0.2,
  "filter.model": 2, "filter.cutoff": 2200, "filter.reso": 0.1, "filter.env": 0.1, "filter.keytrack": 0.4,
  "fenv.attack": 1.5, "fenv.decay": 2.0, "fenv.sustain": 0.8, "fenv.release": 2.0,
  "aenv.attack": 1.4, "aenv.decay": 1.0, "aenv.sustain": 1.0, "aenv.release": 2.2,
  "fx.chorus.on": 1, "fx.chorus.mode": 0, "fx.chorus.mix": 0.45,
  "fx.reverb.on": 1, "fx.reverb.decay": 4.5, "fx.reverb.mix": 0.35 } }
```

`107-pluck-com-eco.json`
```json
{ "format": 1, "name": "Pluck com Eco", "category": "Pluck", "params": {
  "osc1.wave": 0,
  "osc2.wave": 1, "osc2.pw": 0.3, "osc2.fine": 6, "osc2.level": 0.5,
  "filter.model": 2, "filter.cutoff": 500, "filter.reso": 0.2, "filter.env": 0.5, "filter.keytrack": 0.7, "filter.vel": 0.3,
  "fenv.attack": 0.001, "fenv.decay": 0.25, "fenv.sustain": 0.0, "fenv.release": 0.2,
  "aenv.attack": 0.001, "aenv.decay": 0.5, "aenv.sustain": 0.0, "aenv.release": 0.3, "aenv.vel": 0.6,
  "fx.delay.on": 1, "fx.delay.time": 0.375, "fx.delay.feedback": 0.45, "fx.delay.mix": 0.3,
  "fx.reverb.on": 1, "fx.reverb.decay": 1.5, "fx.reverb.mix": 0.15 } }
```

`108-kalimba.json`
```json
{ "format": 1, "name": "Kalimba", "category": "Pluck", "params": {
  "osc1.wave": 3,
  "osc2.wave": 3, "osc2.octave": 2, "osc2.semi": 7, "osc2.level": 0.0,
  "pmod.osc2.pitch": 0.12,
  "filter.model": 1, "filter.cutoff": 6000, "filter.reso": 0.05, "filter.keytrack": 0.6,
  "aenv.attack": 0.001, "aenv.decay": 0.7, "aenv.sustain": 0.0, "aenv.release": 0.5, "aenv.vel": 0.7,
  "fx.reverb.on": 1, "fx.reverb.decay": 1.6, "fx.reverb.mix": 0.22 } }
```

`109-orgao-de-tubos.json`
```json
{ "format": 1, "name": "Órgão de Tubos", "category": "Keys", "params": {
  "voice.drift": 0.05,
  "osc1.wave": 2,
  "osc2.wave": 3, "osc2.octave": 1, "osc2.level": 0.6,
  "osc3.wave": 3, "osc3.octave": -1, "osc3.level": 0.5,
  "filter.model": 1, "filter.cutoff": 5000, "filter.reso": 0.0, "filter.keytrack": 0.5,
  "aenv.attack": 0.06, "aenv.decay": 0.2, "aenv.sustain": 1.0, "aenv.release": 0.5, "aenv.vel": 0.0,
  "fx.reverb.on": 1, "fx.reverb.decay": 6.0, "fx.reverb.damp": 0.4, "fx.reverb.mix": 0.4 } }
```

`110-clavinete-com-phaser.json`
```json
{ "format": 1, "name": "Clavinete com Phaser", "category": "Keys", "params": {
  "osc1.wave": 1, "osc1.pw": 0.18,
  "hpf.cutoff": 150,
  "filter.model": 1, "filter.cutoff": 1200, "filter.reso": 0.3, "filter.env": 0.35, "filter.keytrack": 0.8, "filter.vel": 0.4,
  "fenv.attack": 0.001, "fenv.decay": 0.2, "fenv.sustain": 0.2, "fenv.release": 0.1,
  "aenv.attack": 0.001, "aenv.decay": 0.6, "aenv.sustain": 0.4, "aenv.release": 0.08, "aenv.vel": 0.7,
  "fx.phaser.on": 1, "fx.phaser.rate": 0.5, "fx.phaser.depth": 0.7, "fx.phaser.feedback": 0.45, "fx.phaser.mix": 0.5 } }
```

`111-ondas-do-mar.json`
```json
{ "format": 1, "name": "Ondas do Mar", "category": "FX", "params": {
  "osc1.level": 0.0, "mix.noise": 1.0,
  "filter.model": 2, "filter.cutoff": 700, "filter.reso": 0.2, "filter.keytrack": 0.0,
  "aenv.attack": 1.5, "aenv.decay": 1.0, "aenv.sustain": 1.0, "aenv.release": 3.0, "aenv.vel": 0.0,
  "lfo1.global": 1, "lfo1.wave": 1, "lfo1.rate": 0.12, "lfo1.cutoff": 0.45, "lfo1.amp": 0.5,
  "fx.reverb.on": 1, "fx.reverb.decay": 5.0, "fx.reverb.mix": 0.4 } }
```

`112-bolhas.json`
```json
{ "format": 1, "name": "Bolhas", "category": "FX", "params": {
  "osc1.wave": 2,
  "filter.model": 2, "filter.cutoff": 800, "filter.reso": 0.75, "filter.keytrack": 0.5,
  "aenv.attack": 0.01, "aenv.decay": 0.3, "aenv.sustain": 1.0, "aenv.release": 0.6,
  "lfo1.wave": 4, "lfo1.rate": 9.0, "lfo1.cutoff": 0.55,
  "fx.delay.on": 1, "fx.delay.time": 0.18, "fx.delay.feedback": 0.4, "fx.delay.mix": 0.3,
  "fx.reverb.on": 1, "fx.reverb.decay": 2.0, "fx.reverb.mix": 0.2 } }
```

`113-arpejo-com-eco.json`
```json
{ "format": 1, "name": "Arpejo com Eco", "category": "Pluck", "params": {
  "arp.on": 1, "arp.mode": 2, "arp.octaves": 2, "arp.division": 2, "arp.bpm": 110, "arp.gate": 0.4,
  "osc1.wave": 1, "osc1.pw": 0.35,
  "osc2.wave": 0, "osc2.fine": 7, "osc2.level": 0.5,
  "filter.model": 1, "filter.cutoff": 900, "filter.reso": 0.3, "filter.env": 0.35, "filter.keytrack": 0.6,
  "fenv.attack": 0.001, "fenv.decay": 0.18, "fenv.sustain": 0.1, "fenv.release": 0.15,
  "aenv.attack": 0.001, "aenv.decay": 0.3, "aenv.sustain": 0.3, "aenv.release": 0.2,
  "fx.delay.on": 1, "fx.delay.time": 0.409, "fx.delay.feedback": 0.4, "fx.delay.mix": 0.25,
  "fx.reverb.on": 1, "fx.reverb.decay": 2.0, "fx.reverb.mix": 0.15 } }
```

(`0.409` s is a dotted eighth at 110 BPM.)

**Step 3: Run the Kotlin tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*FactoryPresetsTest*"`
Expected: PASS. The range test skips numbers ≥ 90; the source test sees `mix.noise` on 111.

**Step 4: Run the engine render test, then balance the new presets**

Run: `.\tools\test-engine.ps1 presets_`

Expect loudness failures on some new presets. Fix them exactly as in Task 4 Step 3. Any other failure (unknown name, not finite, never lets go) is a mistake in the file or the engine: fix it.

**Step 5: Re-render and record**

Run:
- `.\tools\render-presets.ps1 -NoOpen` (64 rows)
- `.\tools\render-presets.ps1 -Stress`

In NOTES, update the Plano 5 tables: the 64-preset loudness spread and the 5 heaviest presets.

**Step 6: Commit**

```powershell
git add app/src/main/assets/presets/factory app/src/test/kotlin/com/criapix/volt/preset/FactoryPresetsTest.kt docs/NOTES.md
git commit -m "feat(presets): 21 more factory presets (64 in all); pin the first 43 for Program Change"
```

---

### Task 9: Listening checkpoint with the user (STOP AND ASK)

This task needs the user's ears. Do not skip it, and do not mark presets as approved yourself.

**Files:**
- Create: `docs/PRESET-REVIEW.md`
- Modify: preset files the user asks to change

**Step 1: Write the review sheet**

`docs/PRESET-REVIEW.md` holds one row per preset, all 64 in library order:

```markdown
# Revisão dos presets de fábrica

Ouça em `build\render\index.html` (rodar `.\tools\render-presets.ps1`) ou no celular com o CK61.
Veredito: **bom** / **ajustar** (diga o quê, com as palavras que quiser: "muito brilhante", "ataque lento", "cauda longa"…) / **ruim** (tirar ou refazer).

| # | Preset | Categoria | Nível (dB) | Veredito | Observação |
|---|---|---|---|---|---|
| 1 | Init | — | -18.2 | | |
| 2 | Baixo Gordo | Baixo | … | | |
…
```

Fill `#`, `Preset`, `Categoria` (the Portuguese labels the app uses) and `Nível` from `build\render\loudness.csv`.

For presets 1–10, write in Observação: "ouvido no plano 2: pareceu muito bom (parecer geral)". This is the user's only verdict so far.

Commit the sheet:

```powershell
git add docs/PRESET-REVIEW.md
git commit -m "docs: preset review sheet"
```

**Step 2: Stop and ask the user**

Tell the user, in Portuguese:
- the bank has 64 presets;
- they can listen on the PC by running `.\tools\render-presets.ps1` (it opens `build\render\index.html`), or on the phone after installing the debug APK;
- they can fill in `docs/PRESET-REVIEW.md` or answer in the chat;
- presets with no verdict ship as they are.

Then **wait for the answer**. If the session must end before the user answers, write in NOTES "revisão de ouvido pendente" and go on with Task 10. Come back to this task when the answers arrive, before Task 13.

**Step 3: Apply the answers**

Translate each "ajustar" into **one** change at a time, using this table. Use the smaller step first.

| O usuário diz | Mude |
|---|---|
| muito alto / muito baixo | `master.volume` × 10^(±3/40) (≈ ×1,19 / ÷1,19) por 3 dB |
| muito brilhante, estridente, áspero | `filter.cutoff` × 0,7, ou `filter.env` − 0,1 |
| abafado, sem brilho | `filter.cutoff` × 1,4, ou `filter.env` + 0,1 |
| assobia, ressonância demais | `filter.reso` − 0,15 |
| ataque lento, mole | `aenv.attack` ÷ 2 (mín. 0,001), e `fenv.attack` ÷ 2 |
| estala no começo | `aenv.attack` para pelo menos 0,005 |
| curto demais, some rápido | `aenv.decay` × 1,5 ou `aenv.sustain` + 0,15; para a cauda, `aenv.release` × 1,5 |
| cauda longa, embola | `aenv.release` × 0,6; se houver reverb, `fx.reverb.mix` − 0,1 ou `fx.reverb.decay` × 0,7 |
| fino, magro | `osc2.level` + 0,2 com `osc2.fine` ±6; em baixo, `mix.sub` + 0,3 |
| desafinado, batendo demais | `osc*.fine` em direção a 0, `voice.drift` − 0,1, `fx.chorus.mix` − 0,1 |
| parado, sem vida | `voice.drift` + 0,1, ou chorus ligado com mix 0,3 |
| distorcido demais | `mix.drive` − 0,2, ou `fx.dist.drive` − 0,2 |
| vibrato demais / de menos | `lfo1.pitch` × 0,5 / × 1,5 |
| eco demais | `fx.delay.mix` − 0,1 ou `fx.delay.feedback` − 0,15 |
| ruim, tirar | **não apagar o arquivo se o número for < 093** (Program Change). Refazer o som no mesmo arquivo e com o mesmo nome, ou perguntar ao usuário. Presets 093–113 podem ser apagados. |

After each round of changes:
1. Run `.\tools\test-engine.ps1 presets_` and the Kotlin `FactoryPresetsTest`, then re-render.
2. Ask the user to listen again to just the changed presets. List them.
3. Record each change in `PRESET-REVIEW.md`, in "Observação": what changed and why, in the user's words.

**Step 4: Commit after each round**

```powershell
git add app/src/main/assets/presets/factory docs/PRESET-REVIEW.md
git commit -m "feat(presets): adjustments from the user's listening (round N)"
```

---

### Task 10: App icon

The design is a lightning bolt in Volt's orange (`#FF8A3D`) on the panel colour (`#1C1F24`). The app's minSdk is 26, so an adaptive icon covers every device. A monochrome layer is included for Android 13+ themed icons.

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Create: `app/src/main/res/values/ic_launcher_background.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Write the files**

`res/values/ic_launcher_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
  <color name="ic_launcher_background">#1C1F24</color>
</resources>
```

`res/drawable/ic_launcher_foreground.xml`. The viewport is 108×108. The safe zone is the centre circle of diameter 66, i.e. 21..87 on each axis. The bolt spans x 34..74 and y 22..86.

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
  <!-- a ring like a knob's track, and the bolt -->
  <path android:strokeColor="#3A3F47" android:strokeWidth="3"
      android:pathData="M54,24 A30,30 0 1,1 53.99,24 Z" />
  <path android:fillColor="#FF8A3D"
      android:pathData="M60,22 L36,58 L51,58 L46,86 L72,46 L57,46 Z" />
</vector>
```

`res/drawable/ic_launcher_monochrome.xml`: the same bolt only (no ring), with `android:fillColor="#FFFFFFFF"`. The system tints it.

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
  <path android:fillColor="#FFFFFFFF"
      android:pathData="M60,22 L36,58 L51,58 L46,86 L72,46 L57,46 Z" />
</vector>
```

`res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
  <background android:drawable="@color/ic_launcher_background" />
  <foreground android:drawable="@drawable/ic_launcher_foreground" />
  <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

`AndroidManifest.xml`, on `<application>`: add `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher"`.

**Step 2: Build and look**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL. AAPT errors here are almost always a typo in the XML.

On the emulator:
1. Install the APK.
2. Press HOME and open the app drawer.
3. Take a screenshot: `adb -s emulator-5554 exec-out screencap -p > build\icon.png`.
4. Look at it: an orange bolt inside a grey ring on a dark circle or squircle, not clipped.

If the bolt touches the mask edge, scale both bolt paths about the centre: wrap them in `<group android:pivotX="54" android:pivotY="54" android:scaleX="0.85" android:scaleY="0.85">`.

**Step 3: Commit**

```powershell
git add app/src/main/res app/src/main/AndroidManifest.xml
git commit -m "feat(app): adaptive launcher icon"
```

---

### Task 11: About dialog, log export, save dialog with the keyboard open

**Files:**
- Modify: `app/src/main/kotlin/com/criapix/volt/AppLog.kt`, `MainActivity.kt`
- Modify: `app/src/main/kotlin/com/criapix/volt/ui/VoltScreen.kt`, `ui/Dialogs.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Log text**

In `AppLog.kt`, add:

```kotlin
  /** The previous log file followed by the current one, for "Salvar log…". Waits for queued lines first. */
  fun text(): String {
    flush()
    val f = file ?: return ""
    val old = File(f.parentFile, "volt-log.old.txt")
    return buildString {
      if (old.exists()) append(old.readText())
      if (f.exists()) append(f.readText())
    }
  }
```

**Step 2: Export launcher and version**

`VoltScreen.kt`, `ScreenIo`: add two constructor parameters with defaults:

```kotlin
  val onExportLog: () -> kotlin.Unit = {},
  val version: String = "",
```

`MainActivity.kt`:
- add a launcher next to `exportLauncher`:

```kotlin
  private val logLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
    if (uri == null) return@registerForActivityResult
    runCatching { contentResolver.openOutputStream(uri)?.use { it.write(AppLog.text().toByteArray()) } }
      .onSuccess { model.showMessage("Log salvo") }
      .onFailure { model.showMessage("Não foi possível salvar o log") }
  }
```

- and in the `ScreenIo(...)` call:

```kotlin
      onExportLog = { logLauncher.launch("volt-log.txt") },
      version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?",
```

**Step 3: About dialog and the two settings buttons**

In `Dialogs.kt`:

1. Inside `SettingsDialog`'s column, after the "Controle MIDI" button:

   ```kotlin
           TextButton(onClick = { io.onExportLog() }) { Text("Salvar log…") }
           TextButton(onClick = { about = true }) { Text("Sobre o Volt") }
   ```

2. Next to `var allParams …`, add `var about by remember { mutableStateOf(false) }`.

3. After `if (allParams) …`, add `if (about) AboutDialog(io.version, onClose = { about = false })`.

4. Add the dialog itself:

```kotlin
@Composable
private fun AboutDialog(version: String, onClose: () -> kotlin.Unit) {
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text("Volt $version") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sintetizador analógico virtual para teclado MIDI USB.", fontSize = 14.sp)
        Text("Licença: GNU GPL versão 3 ou posterior. Você pode usar, estudar, modificar e redistribuir o Volt nos termos dessa licença; o código-fonte acompanha cada cópia distribuída.", fontSize = 13.sp, lineHeight = 18.sp)
        Text("Bibliotecas de terceiros", color = VoltColors.dim, fontSize = 12.sp)
        Text(
          "Oboe (Google) — Apache 2.0\nsst-filters, sst-basic-blocks, sst-effects (Surge Synth Team) — GPL-3\nfmt — MIT\nSIMDe — MIT\nJetpack Compose, AndroidX, kotlinx.serialization — Apache 2.0",
          fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace,
        )
      }
    },
    confirmButton = { TextButton(onClick = onClose) { Text("Fechar") } },
  )
}
```

**Step 4: Save dialog with the on-screen keyboard**

The problem (NOTES, plan 2): in landscape the soft keyboard covers the dialog's "Salvar como novo" button.

1. `AndroidManifest.xml`, on the `<activity>`: add `android:windowSoftInputMode="adjustResize"`.

2. `Dialogs.kt`, `SaveDialog`:
   - Give the `text` column `Modifier.verticalScroll(rememberScrollState())`.
   - Give the name field a "done" action that hides the keyboard:

```kotlin
        val focus = LocalFocusManager.current
        OutlinedTextField(
          name, { name = it }, label = { Text("Nome") }, singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
```

   with imports:
   - `androidx.compose.foundation.text.KeyboardActions`
   - `androidx.compose.foundation.text.KeyboardOptions`
   - `androidx.compose.ui.platform.LocalFocusManager`
   - `androidx.compose.ui.text.input.ImeAction`

**Step 5: Build and look**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`

On the emulator:
1. **About:** ⋮ → "Sobre o Volt" shows the version and the list.
2. **Log export:** ⋮ → "Salvar log…" opens the system file picker; save to Downloads; the message "Log salvo" appears.
3. **Save dialog:** tap "Salvar", then tap the name field. If the emulator shows the soft keyboard, check whether "Salvar como novo" is reachable, by scrolling or after pressing the keyboard's ✓ ("Concluído"). Take a screenshot of each state into `build/`.
4. **If the button is still hidden behind the keyboard even after ✓:** write it in NOTES as still open for the phone test. Do not spend longer on it; the ✓ key already gives a way out.

**Step 6: Commit**

```powershell
git add app/src/main/kotlin app/src/main/AndroidManifest.xml
git commit -m "feat(app): about dialog, log export, save dialog usable with the soft keyboard"
```

---

### Task 12: Release build

**Files:**
- Create: `tools/make-keystore.ps1`
- Modify: `tools/pull-log.ps1`
- Modify: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `.gitignore`

**Step 1: Keystore script**

`tools/make-keystore.ps1`:

```powershell
# Creates the release signing key (run once, by the user: keytool asks for the passwords).
# Then write keystore.properties in the repository root (never committed):
#   storeFile=C:/Users/<you>/.android/volt-release.jks
#   storePassword=...
#   keyAlias=volt
#   keyPassword=...
# KEEP A BACKUP of the .jks and the passwords: without them no future version can update the installed app.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$jks = Join-Path $env:USERPROFILE ".android\volt-release.jks"
if (Test-Path $jks) { "Already exists: $jks"; exit 0 }
New-Item -ItemType Directory -Force (Split-Path $jks) | Out-Null
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore $jks -alias volt -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Volt"
"Created $jks. Now write keystore.properties (see the top of this script)."
```

Add `keystore.properties` to `.gitignore` (`*.jks` is already there).

**Step 2: Gradle**

In `app/build.gradle.kts`:

1. Put `import java.util.Properties` on line 1, before `plugins { … }`; Kotlin script imports must come first. Right after the `plugins` block, add:

   ```kotlin
   val keystoreProps = Properties().apply {
     val f = rootProject.file("keystore.properties")
     if (f.exists()) f.inputStream().use { load(it) }
   }
   ```

2. In `defaultConfig`: set `versionCode = 100` and `versionName = "1.0.0"`, and add `manifestPlaceholders["appLabel"] = "Volt"`.

3. Before `buildTypes`:

   ```kotlin
     signingConfigs {
       if (keystoreProps.getProperty("storeFile") != null) {
         create("release") {
           storeFile = file(keystoreProps.getProperty("storeFile"))
           storePassword = keystoreProps.getProperty("storePassword")
           keyAlias = keystoreProps.getProperty("keyAlias")
           keyPassword = keystoreProps.getProperty("keyPassword")
         }
       }
     }
   ```

4. Replace `buildTypes { release { isMinifyEnabled = false } }` with:

   ```kotlin
     buildTypes {
       debug {
         applicationIdSuffix = ".debug"
         manifestPlaceholders["appLabel"] = "Volt dev"
       }
       release {
         isMinifyEnabled = false   // side-loaded; R8 adds JNI/serialization keep-rule risk for little gain
         signingConfig = signingConfigs.findByName("release")
       }
     }
   ```

5. In `AndroidManifest.xml`, change `android:label="Volt"` to `android:label="${appLabel}"`.

**Step 3: `pull-log.ps1` for the new debug id**

In `tools/pull-log.ps1`:
- replace the line `$app = "com.criapix.volt"` with a parameter at the top of the script:

```powershell
param([string]$Package = "com.criapix.volt.debug")
```

- use `$Package` where `$app` was used;
- add to the header comment: "A release build is not debuggable: use ⋮ → Salvar log… in the app instead."

**Step 4: Build both**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`

Expected:
- **Debug** builds as before and installs as `com.criapix.volt.debug`, labelled "Volt dev".
- **Release:**
  - **Without `keystore.properties`**, Gradle builds `app-release-unsigned.apk`. That is expected here: **stop and ask the user** to run `.\tools\make-keystore.ps1` and write `keystore.properties`, as the script's header explains. You cannot type the passwords for them. Carry on with Task 13 meanwhile, and build the signed release when they confirm.
  - **With the file**, Gradle builds `app\build\outputs\apk\release\app-release.apk`. Verify it with:

    ```powershell
    & "$env:ANDROID_HOME\build-tools\<version>\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
    ```

    Expected: `CN=Volt`. Use the build-tools version the SDK has (`ls $env:ANDROID_HOME\build-tools`).

Install on the emulator to prove it runs:

```powershell
adb -s emulator-5554 install -r app\build\outputs\apk\release\app-release.apk
```

Open it and play a key on the drawer. Confirm that `adb -s emulator-5554 logcat -d -s volt AndroidRuntime` shows no crash.

**Step 5: Commit**

```powershell
git add tools/make-keystore.ps1 tools/pull-log.ps1 app/build.gradle.kts app/src/main/AndroidManifest.xml .gitignore
git commit -m "build: signed release 1.0.0, debug builds as com.criapix.volt.debug"
```

---

### Task 13: Notes, device checklist, README, merge

**Files:**
- Modify: `docs/NOTES.md`, `docs/DEVICE-TEST.md`, `README.md`

**Step 1: NOTES**

Replace the "Estado" section at the top with plan 5's state:
- **What exists:** 64 presets, the governor, the icon, About, log export, release 1.0.0.
- **Verified by:** the engine and Kotlin test counts (real numbers), `volt_render`, and the emulator screenshots.
- **What was heard by the user** and what was not, from PRESET-REVIEW.

Complete the "Plano 5" section:
- the loudness target and spread;
- the heaviest presets;
- the governor constants;
- the decisions of this plan;
- any deviation from the plan, with its reason.

**Step 2: DEVICE-TEST**

Add a "Teste no aparelho (plano 5: versão 1.0)" section **at the top**. Steps:

| # | Passo | Esperado |
|---|---|---|
| 1 | No app instalado hoje (debug antigo): navegador de presets → **Exportar**. Anotar as associações do MIDI Learn (⋮ → Controle MIDI), porque não sobrevivem | Arquivo `volt-presets.json` salvo |
| 2 | Desinstalar o Volt antigo; `adb install app\build\outputs\apk\release\app-release.apk` | Ícone novo (raio laranja) na gaveta de apps, nome "Volt" |
| 3 | Abrir, **Importar** o arquivo do passo 1 | Os seus presets voltam |
| 4 | ⋮ → Sobre o Volt | Mostra 1.0.0, licença e bibliotecas |
| 5 | Ouvir os presets com o CK61, de 044 a 064 (os novos) e os marcados "ajustar" em `docs/PRESET-REVIEW.md` | Anotar na folha |
| 6 | Trocar de preset com ◀ ▶ tocando sempre a mesma frase | Volume parecido entre presets (o banco foi nivelado por medição) |
| 7 | Carregar `<o mais pesado do -Stress>` e segurar 16 notas (pedal) por 10 s | Anotar "Carga do motor" em ⋮; se passar de 80 %, a barra mostra "x/12 vozes" em vermelho e o som continua sem estalos |
| 8 | ⋮ → Polifonia máxima 8; tocar um acorde de 10 notas | Só 8 soam; a barra mostra "8/8 vozes" em vermelho; voltar para 16 |
| 9 | Plano 4: efeitos (aba FX, ligar cada um com uma nota soando), arpejador (preset 041 e 064, Hold) e teclado na tela | Sem estalo ao ligar/desligar; arpejo no tempo; anotar como soam |
| 10 | Plano 3: passos 3–5 da tabela do plano 3 (nomes dos controles do CK61, aprender um knob, aprender ▶) | Anotar CC de cada controle em NOTES |
| 11 | ⋮ → Salvar log… | Arquivo salvo; mandar junto com o resultado |
| 12 | Salvar preset com o teclado virtual aberto | O botão "Salvar como novo" fica alcançável (rolando ou depois do ✓ do teclado) |

Fill the placeholder in step 7 with the real file name from the stress table. Steps 5 and 9 use library positions. Check them against the final order: 044 is the first new preset (093), 041 is `090-arpejo-classico`, and 064 is `113-arpejo-com-eco`.

**Step 3: README**

- Update "Estado": version 1.0.0; 64 presets; polyphony under load; the plans are all implemented. Keep the honest line about what has and has not been tested on the phone, taken from NOTES.
- Add a "Ouvir os presets no PC" paragraph: `.\tools\render-presets.ps1`.
- Add a "Versão de release" paragraph: `make-keystore.ps1`, `keystore.properties`, `assembleRelease`, plus the warning about backing up the `.jks`.
- In the licence list, add Oboe (Apache 2.0).

**Step 4: Final check and merge**

Run all of these and expect them all green:

```powershell
.\tools\test-engine.ps1
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

Then commit and merge:

```powershell
git add docs/NOTES.md docs/DEVICE-TEST.md README.md
git commit -m "docs: state, device checklist and README for 1.0"
git checkout main
git merge --no-ff feature/bank-release -m "Merge branch 'feature/bank-release': final bank, polyphony governor, icon, release 1.0 (plan 5)"
```

Do not tag or push.

Report to the user, in Portuguese:
- the test counts;
- the loudness target and the presets that needed a volume change;
- the 5 heaviest presets;
- what the user still has to do:
  - the listening review, if it is still pending;
  - `make-keystore.ps1`, if it has not been run;
  - the device test.
