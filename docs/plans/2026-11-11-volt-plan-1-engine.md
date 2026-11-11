# Volt Plan 1 — Engine and Playable App Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** A new Android app, Volt, whose C++ engine is a 16-voice virtual analog synth (3 oscillators, selectable filter, 2 envelopes, 2 LFOs, poly/mono/legato/unison) playable with low latency from a USB MIDI keyboard, with a plain debug screen that exposes every parameter.

**Architecture:** A platform-free C++20 static library (`engine/`) owns all sound and is unit-tested on the PC. A thin NDK layer (`app/src/main/cpp/`) connects it to Oboe and to Kotlin through JNI. Kotlin sends notes and parameter changes into a fixed-size event queue; the audio callback drains the queue and renders. The audio thread never allocates, locks or calls JNI.

**Tech Stack:** C++20, CMake, Oboe (AAudio), `surge-synthesizer/sst-filters` + `sst-basic-blocks` + `simde` (git submodules, GPL-3), Kotlin 2.3, Jetpack Compose, `android.media.midi`, JUnit 4.

---

## 0. Read this first

**Design document:** `docs/plans/2026-11-11-volt-design.md` (Portuguese). It is the source of truth for *what* Volt is. This plan covers build stages 1–3 of its section 10. The real touch UI, presets, MIDI Learn, effects, arpeggiator, on-screen keyboard and the factory bank are later plans (section 99 below); do not build them here.

**Repository:** `C:\Users\criap\OneDrive\Documentos\Github\volt-synth` (git, branch `main`, no remote yet). Do not push. Commit after every task.

**Reference project (read-only, do not modify):** `C:\Users\criap\OneDrive\Documentos\Github\mvave fm1` — "FM1 Droid", an Android app by the same user. Its path contains a space: always quote it. Files are copied from it where this plan says so.

**Machine facts (Windows 11, PowerShell):**
- Android SDK: `C:\Users\criap\AppData\Local\Android\Sdk`. NDK `30.0.16248370` is installed.
- JDK 17: `C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot` — set `JAVA_HOME` to it before `gradlew`.
- MSVC: VS 2022 Build Tools. The machine also has a VS 18 Community **without** the C++ libraries; a bare `cl`/CMake may pick it and fail with `LNK1104 msvcrt.lib`. Always load `tools\env.ps1` (Task 1) before building on the PC.
- `cmake` and `ninja` are **not** on PATH (checked 2026-11-11). Task 1 deals with it.
- `adb` is on PATH. Emulator system image: `android-35/google_apis/x86_64`. Test phone: Samsung Galaxy S25 Ultra (arm64-v8a).
- The repo lives inside OneDrive. Build output must stay in git-ignored folders (`build/`, `.cxx/`, `.gradle/`). If a build fails with a locked file, retry once; OneDrive may be holding it.

**Rules for the audio thread** (`Engine::render` and everything it calls): no `new`/`malloc`, no `std::vector` growth, no mutex, no I/O, no logging, no JNI. A test enforces the no-allocation rule.

**About the code in this plan:** it was written without a compiler. Treat it as a careful draft. When it does not compile, fix it. When a test fails, find the cause in the code first; change a test's expectation only when you can explain why the expectation was wrong, and write that explanation in `docs/NOTES.md`. The sst-filters calls in Task 9 come from upstream's own test helper, but two details are marked "verify" there.

**Honesty rule:** latency and sound quality are only known from what the user measures and hears on the phone. Never write a latency number you did not read from the device, and never claim a model "sounds like" a hardware synth.

**Language:** code, comments and commit messages in English. Text shown on screen in Brazilian Portuguese. `docs/NOTES.md` and `docs/DEVICE-TEST.md` in Portuguese (the user reads them).

**Commit trailer:** end every commit message with the co-author line your session's instructions give you.

### Deviations from the design, already decided

Tell the user about these in your final report; they are also listed in Task 16's NOTES.md.

1. *Oscillator 3 as an LFO source* (design 4.1) is not built: two LFOs plus poly-mod cover it. It can be added later as one parameter.
2. *Poly-mod from oscillator 2 to filter cutoff* (design 4.1) is not built: filter coefficients are updated once per 16-sample block, so audio-rate cutoff modulation is not possible with this filter library. Poly-mod has four routes: filter envelope → osc 1 pitch and pulse width; osc 2 → osc 1 pitch and pulse width.
3. Hard sync is naive (no anti-aliasing at the reset). Good enough for v1; noted as a known limit.

### Layout at the end of this plan

```
volt-synth/
  .gitignore  .gitmodules  LICENSE  README.md
  settings.gradle.kts  build.gradle.kts  gradle.properties  gradlew  gradlew.bat
  gradle/libs.versions.toml  gradle/wrapper/*
  tools/env.ps1  tools/test-engine.ps1
  third_party/sst-filters/  third_party/sst-basic-blocks/  third_party/simde/   (submodules)
  engine/
    CMakeLists.txt
    src/  util.h  params.def  params.h  params.cpp  event_queue.h
          osc.h  adsr.h  lfo.h  filter.h  filter.cpp  voice.h  voice.cpp
          engine.h  engine.cpp
    tests/ test.h  test_main.cpp  dsp_probe.h  test_params.cpp  test_queue.cpp
           test_osc.cpp  test_adsr.cpp  test_lfo.cpp  test_filter.cpp
           test_engine.cpp  test_alloc.cpp
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/cpp/  CMakeLists.txt  audio.h  audio.cpp  jni.cpp
    src/main/kotlin/com/criapix/volt/
        MainActivity.kt  Native.kt  Params.kt  DebugScreen.kt
        midi/MidiInput.kt  midi/MidiParser.kt  midi/MidiDispatch.kt
    src/test/kotlin/com/criapix/volt/  ParamsTest.kt  midi/MidiParserTest.kt  midi/MidiDispatchTest.kt
  docs/  NOTES.md  DEVICE-TEST.md  plans/
```

---

### Task 1: Repository skeleton and PC toolchain

**Files:**
- Create: `.gitignore`, `LICENSE`, `README.md`, `tools/env.ps1`, `tools/test-engine.ps1`, `docs/NOTES.md`

**Step 1: `.gitignore`**

```
.gradle/
build/
.cxx/
.kotlin/
local.properties
*.jks
.idea/
*.iml
```

**Step 2: `LICENSE`** — the full text of the GNU General Public License version 3. Download it rather than typing it:

```powershell
Invoke-WebRequest https://www.gnu.org/licenses/gpl-3.0.txt -OutFile LICENSE
```

Check the file starts with `GNU GENERAL PUBLIC LICENSE` / `Version 3, 29 June 2007`.

**Step 3: `tools/env.ps1`**

```powershell
# Dot-source before any PC build:  . .\tools\env.ps1
# Forces the VS 2022 Build Tools MSVC environment (the machine also has a VS 18 Community
# without the C++ CRT libs, which CMake would otherwise pick and fail with LNK1104 msvcrt.lib).
$vc = "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
cmd /c "`"$vc`" >nul 2>&1 && set" | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { Set-Item -Path "env:$($Matches[1])" -Value $Matches[2] }
}
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

**Step 4: Find or install CMake and Ninja**

```powershell
. .\tools\env.ps1
cmake --version; ninja --version; cl 2>&1 | Select-Object -First 1
```

- `cl` must print a version containing `19.4` (VS 2022). If it does not, stop and report.
- If `cmake` or `ninja` is missing, install them and open a new shell:
  `winget install --id Kitware.CMake -e` and `winget install --id Ninja-build.Ninja -e`.
- If winget is refused, fall back to the generator `"NMake Makefiles"` (ships with MSVC) and a CMake from the Android SDK (`sdkmanager "cmake;3.22.1"`, binary under `$env:ANDROID_HOME\cmake\3.22.1\bin`), adding that folder to `$env:Path` in `tools/env.ps1`.

Write what you ended up with in `docs/NOTES.md` (Step 6).

**Step 5: `tools/test-engine.ps1`**

```powershell
# Builds the engine and runs its tests on the PC.  Usage:  .\tools\test-engine.ps1 [filter]
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$root = Split-Path $PSScriptRoot -Parent
cmake -S "$root\engine" -B "$root\build\engine" -G Ninja -DCMAKE_BUILD_TYPE=Release -DVOLT_TESTS=ON
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
cmake --build "$root\build\engine"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$root\build\engine\volt_tests.exe" @args
exit $LASTEXITCODE
```

(If you had to use NMake, replace `-G Ninja` accordingly.)

**Step 6: `docs/NOTES.md`**

```markdown
# Notas de execução

## Ambiente (Windows)
- Antes de compilar no PC: `. .\tools\env.ps1` (força o MSVC do VS 2022 Build Tools e define JAVA_HOME/ANDROID_HOME).
- CMake: <versão e de onde veio>. Gerador: <Ninja ou NMake>.
- Testes do motor: `.\tools\test-engine.ps1`.
```

**Step 7: `README.md`** — five lines: what Volt is (one sentence from design section 1), "em desenvolvimento", licence GPL-3.0-or-later, link to `docs/plans/2026-11-11-volt-design.md`.

**Step 8: Commit**

```powershell
git add .gitignore LICENSE README.md tools docs/NOTES.md
git commit -m "chore: repository skeleton and PC build environment"
```

---

### Task 2: Engine library, test harness, parameter table

The parameter table is the single source of truth for every sound parameter. Later plans (UI, presets, MIDI Learn) read it through JNI; they never keep a second list. Preset files will refer to parameters by `name`, so **names are permanent**; ids (enum order) may change.

**Files:**
- Create: `engine/CMakeLists.txt`, `engine/src/util.h`, `engine/src/params.def`, `engine/src/params.h`, `engine/src/params.cpp`
- Create: `engine/tests/test.h`, `engine/tests/test_main.cpp`, `engine/tests/test_params.cpp`

**Step 1: `engine/CMakeLists.txt`**

```cmake
cmake_minimum_required(VERSION 3.22)
project(volt_engine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(VOLT_TESTS "Build the engine unit tests" OFF)

add_library(volt_engine STATIC
  src/params.cpp
)
target_include_directories(volt_engine PUBLIC src)
if (MSVC)
  target_compile_definitions(volt_engine PUBLIC _USE_MATH_DEFINES=1 NOMINMAX)
  target_compile_options(volt_engine PUBLIC /Zc:__cplusplus /W3)
else()
  # The audio code must be optimised even in debug APKs, or it cannot keep up.
  target_compile_options(volt_engine PRIVATE -O2 -Wall -Wextra)
endif()

if (VOLT_TESTS)
  add_executable(volt_tests
    tests/test_main.cpp
    tests/test_params.cpp
  )
  target_link_libraries(volt_tests PRIVATE volt_engine)
endif()
```

Every later task that adds a `.cpp` adds it to one of these two lists.

**Step 2: `engine/src/util.h`**

```cpp
#pragma once
#include <cmath>
#include <cstdint>

namespace volt {

constexpr int kBlock = 16;      // control-rate block, in samples
constexpr int kMaxVoices = 16;  // must be a multiple of 4 (filters run 4 voices at a time)

inline float noteToHz(float note) { return 440.0f * std::exp2((note - 69.0f) / 12.0f); }
inline float hzToNote(float hz) { return 69.0f + 12.0f * std::log2(hz / 440.0f); }
inline float clampf(float x, float lo, float hi) { return x < lo ? lo : (x > hi ? hi : x); }

// xorshift32: cheap noise, allocation-free.
struct Rng {
  uint32_t s = 0x1234567u;
  uint32_t next() { s ^= s << 13; s ^= s >> 17; s ^= s << 5; return s; }
  float bipolar() { return (float)(int32_t)next() * (1.0f / 2147483648.0f); }  // -1..1
  float unipolar() { return (float)(next() >> 8) * (1.0f / 16777216.0f); }     // 0..1
};

}  // namespace volt
```

**Step 3: `engine/tests/test.h`** — a tiny test framework, no dependency.

```cpp
#pragma once
#include <cmath>
#include <cstdio>
#include <vector>

namespace t {
struct Case { const char* name; void (*fn)(); };
inline std::vector<Case>& cases() { static std::vector<Case> c; return c; }
inline int& failures() { static int f = 0; return f; }
struct Reg { Reg(const char* n, void (*f)()) { cases().push_back({n, f}); } };
}  // namespace t

#define TEST(name) \
  static void name(); \
  static t::Reg reg_##name(#name, name); \
  static void name()

#define CHECK(cond) \
  do { if (!(cond)) { std::printf("    FAIL %s:%d  %s\n", __FILE__, __LINE__, #cond); ++t::failures(); } } while (0)

#define CHECK_NEAR(a, b, tol) \
  do { double a_ = (a), b_ = (b); if (!(std::fabs(a_ - b_) <= (tol))) { \
    std::printf("    FAIL %s:%d  %s = %g, expected %g +- %g\n", __FILE__, __LINE__, #a, a_, b_, (double)(tol)); \
    ++t::failures(); } } while (0)
```

**Step 4: `engine/tests/test_main.cpp`**

```cpp
#include "test.h"
#include <cstring>

// Usage: volt_tests [substring]  — runs the tests whose name contains the substring.
int main(int argc, char** argv) {
  const char* filter = argc > 1 ? argv[1] : "";
  int ran = 0, failed = 0;
  for (auto& c : t::cases()) {
    if (!std::strstr(c.name, filter)) continue;
    int before = t::failures();
    c.fn();
    ++ran;
    bool ok = t::failures() == before;
    if (!ok) ++failed;
    std::printf("%s %s\n", ok ? "ok  " : "FAIL", c.name);
  }
  std::printf("%d tests, %d failed\n", ran, failed);
  return failed == 0 && ran > 0 ? 0 : 1;
}
```

**Step 5: Write the failing test — `engine/tests/test_params.cpp`**

```cpp
#include "params.h"
#include "test.h"
#include <cstring>

using namespace volt;

TEST(params_names_are_unique_and_dotted) {
  for (int i = 0; i < kNumParams; ++i) {
    CHECK(std::strchr(kParams[i].name, '.') != nullptr);
    for (int j = i + 1; j < kNumParams; ++j) CHECK(std::strcmp(kParams[i].name, kParams[j].name) != 0);
  }
}

TEST(params_defaults_are_inside_their_range) {
  for (int i = 0; i < kNumParams; ++i) {
    CHECK(kParams[i].min < kParams[i].max);
    CHECK(kParams[i].def >= kParams[i].min && kParams[i].def <= kParams[i].max);
    if (kParams[i].curve == Curve::Exp) CHECK(kParams[i].min > 0.0f);
  }
}

TEST(params_denorm_hits_the_ends_and_roundtrips) {
  for (int i = 0; i < kNumParams; ++i) {
    CHECK_NEAR(denorm(i, 0.0f), kParams[i].min, 1e-4 * (1 + std::fabs(kParams[i].min)));
    CHECK_NEAR(denorm(i, 1.0f), kParams[i].max, 1e-4 * (1 + std::fabs(kParams[i].max)));
    float v = denorm(i, 0.37f);
    CHECK_NEAR(denorm(i, norm(i, v)), v, 1e-3 * (1 + std::fabs(v)));
  }
}

TEST(params_step_values_are_whole_numbers) {
  CHECK_NEAR(denorm(kOsc1Wave, 0.40f), 1.0f, 0);   // 0..3 -> 1.2 rounds to 1
  CHECK_NEAR(denorm(kOsc1Octave, 0.5f), 0.0f, 0);  // -2..2
  CHECK_NEAR(denorm(kVoiceUnison, 1.0f), 8.0f, 0);
}

TEST(params_exp_curve_is_geometric) {
  // 20..20000 Hz: the midpoint is the geometric mean.
  CHECK_NEAR(denorm(kFilterCutoff, 0.5f), std::sqrt(20.0f * 20000.0f), 1.0);
}

TEST(params_lookup_by_name) {
  CHECK(paramIndex("filter.cutoff") == kFilterCutoff);
  CHECK(paramIndex("no.such") == -1);
}

TEST(params_oscillator_and_lfo_blocks_have_the_same_shape) {
  CHECK(kOsc2Wave - kOsc1Wave == kOscStride && kOsc3Wave - kOsc2Wave == kOscStride);
  CHECK(kOsc2Level - kOsc2Wave == kOsc1Level - kOsc1Wave);
  CHECK(kLfo2Wave - kLfo1Wave == kLfoStride);
  CHECK(kLfo2Amp - kLfo2Wave == kLfo1Amp - kLfo1Wave);
}
```

**Step 6: Run it to see it fail**

Run: `.\tools\test-engine.ps1`
Expected: build error, `params.h` not found.

**Step 7: `engine/src/params.def`**

One line per parameter: `P(id, "name", min, max, default, curve)`. Curves: `Lin` (linear), `Exp` (geometric, min > 0), `Step` (whole numbers; never smoothed).

```cpp
// Oscillators. The three blocks must keep the same order (kOscStride).
P(kOsc1Wave,   "osc1.wave",   0, 3, 0, Step)      // 0 saw, 1 pulse, 2 triangle, 3 sine
P(kOsc1Octave, "osc1.octave", -2, 2, 0, Step)
P(kOsc1Semi,   "osc1.semi",   -12, 12, 0, Step)
P(kOsc1Fine,   "osc1.fine",   -50, 50, 0, Lin)    // cents
P(kOsc1Pw,     "osc1.pw",     0.05f, 0.95f, 0.5f, Lin)
P(kOsc1Level,  "osc1.level",  0, 1, 1, Lin)
P(kOsc2Wave,   "osc2.wave",   0, 3, 0, Step)
P(kOsc2Octave, "osc2.octave", -2, 2, 0, Step)
P(kOsc2Semi,   "osc2.semi",   -12, 12, 0, Step)
P(kOsc2Fine,   "osc2.fine",   -50, 50, 0, Lin)
P(kOsc2Pw,     "osc2.pw",     0.05f, 0.95f, 0.5f, Lin)
P(kOsc2Level,  "osc2.level",  0, 1, 0, Lin)
P(kOsc3Wave,   "osc3.wave",   0, 3, 0, Step)
P(kOsc3Octave, "osc3.octave", -2, 2, 0, Step)
P(kOsc3Semi,   "osc3.semi",   -12, 12, 0, Step)
P(kOsc3Fine,   "osc3.fine",   -50, 50, 0, Lin)
P(kOsc3Pw,     "osc3.pw",     0.05f, 0.95f, 0.5f, Lin)
P(kOsc3Level,  "osc3.level",  0, 1, 0, Lin)
P(kOsc2Sync,   "osc2.sync",   0, 1, 0, Step)      // osc 2 hard-synced to osc 1

// Mixer
P(kMixSub,     "mix.sub",     0, 1, 0, Lin)       // square, one octave below osc 1
P(kMixNoise,   "mix.noise",   0, 1, 0, Lin)
P(kMixDrive,   "mix.drive",   0, 1, 0, Lin)

// Filters
P(kHpfCutoff,      "hpf.cutoff",      20, 2000, 20, Exp)   // Hz, 1-pole high-pass
P(kFilterModel,    "filter.model",    0, 3, 0, Step)       // 0 Ladder, 1 Roland 12, 2 Roland 24, 3 Prophet
P(kFilterCutoff,   "filter.cutoff",   20, 20000, 20000, Exp)
P(kFilterReso,     "filter.reso",     0, 1, 0.1f, Lin)
P(kFilterEnv,      "filter.env",      -1, 1, 0, Lin)       // +-1 = +-7 octaves
P(kFilterKeytrack, "filter.keytrack", 0, 1, 0.5f, Lin)
P(kFilterVel,      "filter.vel",      0, 1, 0, Lin)

// Envelopes (seconds)
P(kFenvAttack,  "fenv.attack",  0.001f, 10, 0.005f, Exp)
P(kFenvDecay,   "fenv.decay",   0.001f, 10, 0.3f, Exp)
P(kFenvSustain, "fenv.sustain", 0, 1, 0.5f, Lin)
P(kFenvRelease, "fenv.release", 0.001f, 10, 0.3f, Exp)
P(kAenvAttack,  "aenv.attack",  0.001f, 10, 0.003f, Exp)
P(kAenvDecay,   "aenv.decay",   0.001f, 10, 0.3f, Exp)
P(kAenvSustain, "aenv.sustain", 0, 1, 0.8f, Lin)
P(kAenvRelease, "aenv.release", 0.001f, 10, 0.2f, Exp)
P(kAenvVel,     "aenv.vel",     0, 1, 0.5f, Lin)

// LFOs. The two blocks must keep the same order (kLfoStride).
P(kLfo1Wave,   "lfo1.wave",   0, 4, 0, Step)      // 0 sine, 1 triangle, 2 saw, 3 square, 4 sample&hold
P(kLfo1Rate,   "lfo1.rate",   0.05f, 30, 5, Exp)  // Hz
P(kLfo1Delay,  "lfo1.delay",  0, 5, 0, Lin)       // fade-in, seconds
P(kLfo1Pitch,  "lfo1.pitch",  0, 1, 0, Lin)
P(kLfo1Pw,     "lfo1.pw",     0, 1, 0, Lin)
P(kLfo1Cutoff, "lfo1.cutoff", 0, 1, 0, Lin)
P(kLfo1Amp,    "lfo1.amp",    0, 1, 0, Lin)
P(kLfo2Wave,   "lfo2.wave",   0, 4, 0, Step)
P(kLfo2Rate,   "lfo2.rate",   0.05f, 30, 1, Exp)
P(kLfo2Delay,  "lfo2.delay",  0, 5, 0, Lin)
P(kLfo2Pitch,  "lfo2.pitch",  0, 1, 0, Lin)
P(kLfo2Pw,     "lfo2.pw",     0, 1, 0, Lin)
P(kLfo2Cutoff, "lfo2.cutoff", 0, 1, 0, Lin)
P(kLfo2Amp,    "lfo2.amp",    0, 1, 0, Lin)
P(kLfo1Global, "lfo1.global", 0, 1, 0, Step)      // 1 = one LFO shared by all voices
P(kLfo1Wheel,  "lfo1.wheel",  0, 1, 0, Lin)       // how much the mod wheel gates LFO 1's depth

// Poly-mod
P(kPmodFenvPitch, "pmod.fenv.pitch", -1, 1, 0, Lin)  // filter envelope -> osc 1 pitch (+-24 semitones)
P(kPmodFenvPw,    "pmod.fenv.pw",    -1, 1, 0, Lin)
P(kPmodOsc2Pitch, "pmod.osc2.pitch", 0, 1, 0, Lin)   // osc 2 -> osc 1 frequency (FM)
P(kPmodOsc2Pw,    "pmod.osc2.pw",    0, 1, 0, Lin)

// Voices
P(kVoiceMode,        "voice.mode",         0, 3, 0, Step)   // 0 poly, 1 mono, 2 legato, 3 unison
P(kVoiceUnison,      "voice.unison",       2, 8, 4, Step)
P(kVoiceDetune,      "voice.detune",       0, 1, 0.2f, Lin)
P(kVoiceSpread,      "voice.spread",       0, 1, 0.5f, Lin)
P(kVoiceGlide,       "voice.glide",        0, 5, 0, Lin)    // seconds, 0 = off
P(kVoiceGlideLegato, "voice.glide_legato", 0, 1, 0, Step)   // 1 = glide only between overlapping notes
P(kVoiceDrift,       "voice.drift",        0, 1, 0.1f, Lin)
P(kVoiceBend,        "voice.bend",         0, 24, 2, Step)  // pitch-bend range, semitones

P(kMasterVolume, "master.volume", 0, 1, 0.7f, Lin)
```

**Step 8: `engine/src/params.h`**

```cpp
#pragma once

namespace volt {

enum class Curve { Lin, Exp, Step };

struct ParamInfo {
  const char* name;  // permanent: preset files use it
  float min, max, def;
  Curve curve;
};

enum ParamId : int {
#define P(id, name, min, max, def, curve) id,
#include "params.def"
#undef P
  kNumParams
};

constexpr int kOscStride = kOsc2Wave - kOsc1Wave;
constexpr int kLfoStride = kLfo2Wave - kLfo1Wave;

extern const ParamInfo kParams[kNumParams];

float denorm(int id, float normalized);  // 0..1 -> real value (clamped)
float norm(int id, float value);         // real value -> 0..1 (clamped)
int paramIndex(const char* name);        // -1 when unknown

}  // namespace volt
```

**Step 9: `engine/src/params.cpp`**

```cpp
#include "params.h"
#include <cmath>
#include <cstring>

namespace volt {

const ParamInfo kParams[kNumParams] = {
#define P(id, name, min, max, def, curve) {name, (float)(min), (float)(max), (float)(def), Curve::curve},
#include "params.def"
#undef P
};

float denorm(int id, float n) {
  const ParamInfo& p = kParams[id];
  n = n < 0 ? 0 : (n > 1 ? 1 : n);
  switch (p.curve) {
    case Curve::Exp: return p.min * std::pow(p.max / p.min, n);
    case Curve::Step: return std::round(p.min + (p.max - p.min) * n);
    default: return p.min + (p.max - p.min) * n;
  }
}

float norm(int id, float v) {
  const ParamInfo& p = kParams[id];
  v = v < p.min ? p.min : (v > p.max ? p.max : v);
  if (p.curve == Curve::Exp) return std::log(v / p.min) / std::log(p.max / p.min);
  return (v - p.min) / (p.max - p.min);
}

int paramIndex(const char* name) {
  for (int i = 0; i < kNumParams; ++i)
    if (std::strcmp(kParams[i].name, name) == 0) return i;
  return -1;
}

}  // namespace volt
```

**Step 10: Run the tests**

Run: `.\tools\test-engine.ps1`
Expected: `7 tests, 0 failed`, exit code 0.

**Step 11: Commit**

```powershell
git add engine
git commit -m "feat(engine): library skeleton, test harness and parameter table"
```

---

### Task 3: Event queue

Notes and parameter changes cross from Kotlin threads to the audio thread through a fixed-size single-consumer ring. Producers (UI thread and the MIDI binder thread) are serialised by a mutex **on the producer side only**; the audio thread never touches the mutex.

**Files:**
- Create: `engine/src/event_queue.h`, `engine/tests/test_queue.cpp`
- Modify: `engine/CMakeLists.txt` (add `tests/test_queue.cpp` to `volt_tests`)

**Step 1: Failing test — `engine/tests/test_queue.cpp`**

```cpp
#include "event_queue.h"
#include "test.h"
#include <thread>

using namespace volt;

TEST(queue_keeps_order) {
  EventQueue q;
  for (int i = 0; i < 10; ++i) CHECK(q.push({Event::NoteOn, i, 0.5f}));
  Event e;
  for (int i = 0; i < 10; ++i) { CHECK(q.pop(e)); CHECK(e.a == i); }
  CHECK(!q.pop(e));
}

TEST(queue_refuses_when_full_and_recovers) {
  EventQueue q;
  int accepted = 0;
  while (q.push({Event::NoteOn, accepted, 0})) ++accepted;
  CHECK(accepted == EventQueue::kCapacity - 1);
  Event e;
  CHECK(q.pop(e));
  CHECK(q.push({Event::NoteOff, 99, 0}));
}

TEST(queue_survives_a_producer_thread) {
  EventQueue q;
  const int n = 200000;
  std::thread producer([&] {
    for (int i = 0; i < n;) if (q.push({Event::SetParam, i, 0})) ++i;
  });
  int expected = 0;
  Event e;
  while (expected < n) if (q.pop(e)) { CHECK(e.a == expected); ++expected; }
  producer.join();
}
```

**Step 2: Run** `.\tools\test-engine.ps1 queue` — expected: build error, header missing.

**Step 3: `engine/src/event_queue.h`**

```cpp
#pragma once
#include <atomic>
#include <cstdint>

namespace volt {

struct Event {
  enum Type : int32_t { NoteOn, NoteOff, SetParam, PitchBend, ModWheel, Sustain, AllNotesOff };
  Type type;
  int32_t a;  // note, or parameter id
  float b;    // velocity 0..1, normalized parameter value, bend -1..1, wheel 0..1, sustain 0/1
};

// Single-producer / single-consumer ring. push() from one thread at a time, pop() from another.
class EventQueue {
 public:
  static constexpr int kCapacity = 1024;  // power of two

  bool push(const Event& e) {
    uint32_t w = write_.load(std::memory_order_relaxed);
    uint32_t next = (w + 1) & (kCapacity - 1);
    if (next == read_.load(std::memory_order_acquire)) return false;  // full
    buf_[w] = e;
    write_.store(next, std::memory_order_release);
    return true;
  }

  bool pop(Event& e) {
    uint32_t r = read_.load(std::memory_order_relaxed);
    if (r == write_.load(std::memory_order_acquire)) return false;  // empty
    e = buf_[r];
    read_.store((r + 1) & (kCapacity - 1), std::memory_order_release);
    return true;
  }

 private:
  Event buf_[kCapacity];
  std::atomic<uint32_t> write_{0}, read_{0};
};

}  // namespace volt
```

**Step 4: Run** `.\tools\test-engine.ps1 queue` — expected: `3 tests, 0 failed`. On MSVC, linking `std::thread` needs nothing extra.

**Step 5: Commit** — `git commit -am "feat(engine): lock-free event queue"` (after `git add engine`).

---

### Task 4: Oscillator

Band-limited by polyBLEP: the naive waveform plus a two-sample correction at each discontinuity.

**Files:**
- Create: `engine/src/osc.h`, `engine/tests/dsp_probe.h`, `engine/tests/test_osc.cpp`
- Modify: `engine/CMakeLists.txt` (add `tests/test_osc.cpp`)

**Step 1: `engine/tests/dsp_probe.h`** — measuring tools shared by the DSP tests.

```cpp
#pragma once
#include <cmath>
#include <vector>

namespace probe {

// Amplitude of the sinusoid at `hz` in x (Goertzel). Exact when hz * n / sr is an integer.
inline double tone(const std::vector<float>& x, double hz, double sr) {
  const double w = 2.0 * 3.14159265358979323846 * hz / sr;
  const double c = 2.0 * std::cos(w);
  double s1 = 0, s2 = 0;
  for (float v : x) { double s0 = v + c * s1 - s2; s2 = s1; s1 = s0; }
  double re = s1 - s2 * std::cos(w), im = s2 * std::sin(w);
  return 2.0 * std::sqrt(re * re + im * im) / (double)x.size();
}

inline double rms(const std::vector<float>& x, size_t from = 0) {
  double s = 0;
  for (size_t i = from; i < x.size(); ++i) s += (double)x[i] * x[i];
  return std::sqrt(s / (double)(x.size() - from));
}

inline double peak(const std::vector<float>& x) {
  double p = 0;
  for (float v : x) p = std::fmax(p, std::fabs(v));
  return p;
}

inline bool finite(const std::vector<float>& x) {
  for (float v : x) if (!std::isfinite(v)) return false;
  return true;
}

// Frequency from upward zero crossings.
inline double zeroCrossHz(const std::vector<float>& x, double sr) {
  int n = 0;
  for (size_t i = 1; i < x.size(); ++i) if (x[i - 1] < 0 && x[i] >= 0) ++n;
  return n * sr / (double)x.size();
}

}  // namespace probe
```

**Step 2: Failing test — `engine/tests/test_osc.cpp`**

```cpp
#include "dsp_probe.h"
#include "osc.h"
#include "test.h"

using namespace volt;

static std::vector<float> run(int wave, float hz, float pw = 0.5f, int n = 48000) {
  Osc o;
  std::vector<float> x(n);
  for (auto& v : x) v = o.tick(hz / 48000.0f, wave, pw);
  return x;
}

TEST(osc_every_wave_plays_the_asked_frequency) {
  for (int wave = 0; wave < 4; ++wave) {
    auto x = run(wave, 440.0f);
    CHECK_NEAR(probe::tone(x, 440.0, 48000.0) > 0.5, 1, 0);  // strong fundamental
    CHECK(probe::peak(x) <= 1.3);                            // BLEP overshoot is small
    CHECK(probe::finite(x));
  }
}

TEST(osc_sine_and_triangle_cross_zero_once_per_cycle) {
  CHECK_NEAR(probe::zeroCrossHz(run(3, 440.0f), 48000.0), 440.0, 1.0);
  CHECK_NEAR(probe::zeroCrossHz(run(2, 440.0f), 48000.0), 440.0, 1.0);
}

TEST(osc_saw_has_the_harmonics_of_a_saw) {
  auto x = run(0, 100.0f);
  double h1 = probe::tone(x, 100, 48000), h2 = probe::tone(x, 200, 48000), h3 = probe::tone(x, 300, 48000);
  CHECK_NEAR(h1, 2.0 / 3.14159265, 0.02);  // 2/pi
  CHECK_NEAR(h2 / h1, 0.5, 0.02);
  CHECK_NEAR(h3 / h1, 1.0 / 3.0, 0.02);
}

TEST(osc_square_has_only_odd_harmonics_and_pw_changes_that) {
  auto sq = run(1, 100.0f, 0.5f);
  CHECK(probe::tone(sq, 200, 48000) < 0.01);
  CHECK(probe::tone(sq, 300, 48000) > 0.3);
  auto narrow = run(1, 100.0f, 0.25f);
  CHECK(probe::tone(narrow, 200, 48000) > 0.3);
}

TEST(osc_saw_aliases_much_less_than_a_naive_saw) {
  // 3100 Hz saw at 48 kHz: the 11th harmonic (34100 Hz) folds to 13900 Hz, which is not a harmonic.
  const float hz = 3100.0f;
  auto blep = run(0, hz);
  std::vector<float> naive(48000);
  float ph = 0;
  for (auto& v : naive) { v = 2 * ph - 1; ph += hz / 48000.0f; if (ph >= 1) ph -= 1; }
  double a = probe::tone(blep, 13900, 48000), b = probe::tone(naive, 13900, 48000);
  CHECK(b > 0.03);       // the naive saw really aliases there
  CHECK(a < b * 0.25);   // at least 12 dB better
}

TEST(osc_reports_the_wrap_for_hard_sync) {
  Osc o;
  int wraps = 0;
  for (int i = 0; i < 48000; ++i) {
    o.tick(100.0f / 48000.0f, 0, 0.5f);
    if (o.wrapped) { ++wraps; CHECK(o.wrapFrac >= 0.0f && o.wrapFrac < 1.0f); }
  }
  CHECK_NEAR(wraps, 100, 1);
}
```

**Step 3: Run** `.\tools\test-engine.ps1 osc` — expected: build error.

**Step 4: `engine/src/osc.h`**

```cpp
#pragma once
#include "util.h"

namespace volt {

// Residual that rounds off a unit step at phase 0. t = phase (0..1), dt = phase increment.
inline float polyBlep(float t, float dt) {
  if (t < dt) { t /= dt; return t + t - t * t - 1.0f; }
  if (t > 1.0f - dt) { t = (t - 1.0f) / dt; return t * t + t + t + 1.0f; }
  return 0.0f;
}

struct Osc {
  float phase = 0.0f;     // 0..1
  bool wrapped = false;   // true when the last tick crossed phase 1
  float wrapFrac = 0.0f;  // how far past the wrap we are, as a fraction of a sample (0..1)

  // inc = frequency / sample rate. wave: 0 saw, 1 pulse, 2 triangle, 3 sine.
  float tick(float inc, int wave, float pw) {
    inc = clampf(inc, 0.0f, 0.45f);
    float out;
    switch (wave) {
      case 0:
        out = 2.0f * phase - 1.0f;
        if (inc > 0.0f) out -= polyBlep(phase, inc);
        break;
      case 1: {
        out = phase < pw ? 1.0f : -1.0f;
        if (inc > 0.0f) {
          out += polyBlep(phase, inc);
          float t2 = phase + 1.0f - pw;
          if (t2 >= 1.0f) t2 -= 1.0f;
          out -= polyBlep(t2, inc);
        }
        break;
      }
      case 2:
        out = phase < 0.5f ? 4.0f * phase - 1.0f : 3.0f - 4.0f * phase;
        break;
      default:
        out = std::sin(6.28318530718f * phase);
        break;
    }
    phase += inc;
    wrapped = phase >= 1.0f;
    if (wrapped) { phase -= 1.0f; wrapFrac = inc > 0.0f ? phase / inc : 0.0f; }
    return out;
  }

  // Hard sync: restart as if the reset happened `frac` of a sample ago.
  void syncTo(float frac, float inc) { phase = clampf(frac * inc, 0.0f, 0.999f); }
};

}  // namespace volt
```

Note on the triangle test: a triangle that starts at −1 crosses zero upward once per cycle; the sine starts at 0, and the `x[i-1] < 0 && x[i] >= 0` rule counts it once per cycle too.

**Step 5: Run** `.\tools\test-engine.ps1 osc` — expected: all `osc_` tests pass.

**Step 6: Commit** — `feat(engine): polyBLEP oscillator`.

---

### Task 5: ADSR envelope

Runs at control rate: one `tick()` per 16-sample block. Exponential segments, like an analogue RC envelope.

**Files:**
- Create: `engine/src/adsr.h`, `engine/tests/test_adsr.cpp`
- Modify: `engine/CMakeLists.txt`

**Step 1: Failing test — `engine/tests/test_adsr.cpp`**

```cpp
#include "adsr.h"
#include "test.h"

using namespace volt;

static const float dt = 16.0f / 48000.0f;
static int ticksFor(float seconds) { return (int)(seconds / dt); }

TEST(adsr_is_silent_until_gated) {
  Adsr e;
  e.set(0.01f, 0.1f, 0.5f, 0.1f, dt);
  for (int i = 0; i < 100; ++i) CHECK(e.tick() == 0.0f);
  CHECK(!e.active());
}

TEST(adsr_attack_reaches_full_level_in_the_attack_time) {
  Adsr e;
  e.set(0.1f, 1.0f, 1.0f, 0.1f, dt);
  e.gate(true);
  float v = 0;
  for (int i = 0; i < ticksFor(0.08f); ++i) v = e.tick();
  CHECK(v < 1.0f);
  for (int i = 0; i < ticksFor(0.04f); ++i) v = e.tick();
  CHECK_NEAR(v, 1.0f, 0.02);
}

TEST(adsr_decays_to_sustain_and_holds) {
  Adsr e;
  e.set(0.001f, 0.1f, 0.4f, 0.1f, dt);
  e.gate(true);
  float v = 0;
  for (int i = 0; i < ticksFor(0.15f); ++i) v = e.tick();
  CHECK_NEAR(v, 0.4f, 0.02);
  for (int i = 0; i < ticksFor(1.0f); ++i) v = e.tick();
  CHECK_NEAR(v, 0.4f, 0.005);
}

TEST(adsr_release_ends_and_goes_idle) {
  Adsr e;
  e.set(0.001f, 0.01f, 1.0f, 0.2f, dt);
  e.gate(true);
  for (int i = 0; i < ticksFor(0.05f); ++i) e.tick();
  e.gate(false);
  float v = 1;
  for (int i = 0; i < ticksFor(0.2f); ++i) v = e.tick();
  CHECK(v < 0.02f);
  for (int i = 0; i < ticksFor(0.2f); ++i) e.tick();
  CHECK(!e.active());
  CHECK(e.tick() == 0.0f);
}

TEST(adsr_retrigger_continues_from_the_current_level) {
  Adsr e;
  e.set(0.2f, 0.1f, 1.0f, 0.5f, dt);
  e.gate(true);
  for (int i = 0; i < ticksFor(0.3f); ++i) e.tick();
  e.gate(false);
  float before = 0;
  for (int i = 0; i < ticksFor(0.05f); ++i) before = e.tick();
  e.gate(true);
  float after = e.tick();
  CHECK(after >= before - 0.01f);  // no jump down to zero: no click
}
```

**Step 2: Run** — build error.

**Step 3: `engine/src/adsr.h`**

```cpp
#pragma once
#include <cmath>

namespace volt {

class Adsr {
 public:
  // Times in seconds, sustain 0..1, dt = seconds per tick. Cheap enough to call every block.
  void set(float attack, float decay, float sustain, float release, float dt) {
    // Attack aims above 1 so the curve arrives with a slope instead of creeping in.
    ca_ = 1.0f - std::exp(-dt * 1.466f / attack);   // 1.466 = ln(1.3 / 0.3)
    cd_ = 1.0f - std::exp(-dt * 4.6f / decay);      // 4.6 = ln(100): 99 % of the way
    cr_ = 1.0f - std::exp(-dt * 4.6f / release);
    sustain_ = sustain;
  }

  void gate(bool on) {
    if (on) state_ = Attack;
    else if (state_ != Idle) state_ = Release;
  }

  float tick() {
    switch (state_) {
      case Attack:
        level_ += (1.3f - level_) * ca_;
        if (level_ >= 1.0f) { level_ = 1.0f; state_ = Decay; }
        break;
      case Decay:
        level_ += (sustain_ - level_) * cd_;
        break;
      case Release:
        level_ -= level_ * cr_;
        if (level_ < 0.0005f) { level_ = 0.0f; state_ = Idle; }
        break;
      default:
        break;
    }
    return level_;
  }

  bool active() const { return state_ != Idle; }
  void reset() { state_ = Idle; level_ = 0.0f; }

 private:
  enum State { Idle, Attack, Decay, Release };
  State state_ = Idle;
  float level_ = 0.0f, sustain_ = 1.0f, ca_ = 1.0f, cd_ = 1.0f, cr_ = 1.0f;
};

}  // namespace volt
```

**Step 4: Run** `.\tools\test-engine.ps1 adsr` — all pass.

**Step 5: Commit** — `feat(engine): ADSR envelope`.

---

### Task 6: LFO

**Files:**
- Create: `engine/src/lfo.h`, `engine/tests/test_lfo.cpp`
- Modify: `engine/CMakeLists.txt`

**Step 1: Failing test — `engine/tests/test_lfo.cpp`**

```cpp
#include "lfo.h"
#include "test.h"

using namespace volt;

static const float dt = 16.0f / 48000.0f;

TEST(lfo_every_wave_stays_in_range_and_moves) {
  for (int wave = 0; wave < 5; ++wave) {
    Lfo l;
    float lo = 1, hi = -1;
    for (int i = 0; i < 6000; ++i) {
      float v = l.tick(wave, 5.0f, 0.0f, dt);
      CHECK(v >= -1.0001f && v <= 1.0001f);
      lo = v < lo ? v : lo; hi = v > hi ? v : hi;
    }
    CHECK(lo < -0.5f && hi > 0.5f);
  }
}

TEST(lfo_runs_at_the_asked_rate) {
  Lfo l;
  int cycles = 0;
  float prev = 0;
  for (int i = 0; i < 3000 * 2; ++i) {  // 2 seconds at 3000 ticks/s
    float v = l.tick(0, 5.0f, 0.0f, dt);
    if (prev < 0 && v >= 0) ++cycles;
    prev = v;
  }
  CHECK_NEAR(cycles, 10, 1);
}

TEST(lfo_delay_fades_in_after_the_note_starts) {
  Lfo l;
  l.restart();
  float early = 0, late = 0;
  for (int i = 0; i < 300; ++i) { float v = std::fabs(l.tick(3, 5.0f, 1.0f, dt)); early = v > early ? v : early; }
  for (int i = 0; i < 6000; ++i) { float v = std::fabs(l.tick(3, 5.0f, 1.0f, dt)); late = v > late ? v : late; }
  CHECK(early < 0.2f);   // 0.1 s into a 1 s fade
  CHECK_NEAR(late, 1.0f, 0.01);
}

TEST(lfo_sample_and_hold_holds_between_steps) {
  Lfo l;
  int changes = 0;
  float prev = l.tick(4, 5.0f, 0.0f, dt);
  for (int i = 0; i < 3000; ++i) { float v = l.tick(4, 5.0f, 0.0f, dt); if (v != prev) ++changes; prev = v; }
  CHECK_NEAR(changes, 5, 1);
}
```

**Step 2: Run** — build error.

**Step 3: `engine/src/lfo.h`**

```cpp
#pragma once
#include "util.h"

namespace volt {

class Lfo {
 public:
  // One control tick. wave: 0 sine, 1 triangle, 2 saw, 3 square, 4 sample & hold. Returns -1..1.
  float tick(int wave, float hz, float delaySeconds, float dt) {
    float out;
    switch (wave) {
      case 0: out = std::sin(6.28318530718f * phase_); break;
      case 1: out = phase_ < 0.5f ? 4.0f * phase_ - 1.0f : 3.0f - 4.0f * phase_; break;
      case 2: out = 1.0f - 2.0f * phase_; break;
      case 3: out = phase_ < 0.5f ? 1.0f : -1.0f; break;
      default: out = held_; break;
    }
    phase_ += hz * dt;
    if (phase_ >= 1.0f) { phase_ -= 1.0f; held_ = rng_.bipolar(); }
    if (delaySeconds > 0.0f) {
      fade_ += dt / delaySeconds;
      if (fade_ > 1.0f) fade_ = 1.0f;
    } else {
      fade_ = 1.0f;
    }
    return out * fade_;
  }

  // Call when a voice starts: the fade-in begins again and the cycle restarts.
  void restart() { phase_ = 0.0f; fade_ = 0.0f; }
  void seed(uint32_t s) { rng_.s = s | 1u; held_ = rng_.bipolar(); }

 private:
  float phase_ = 0.0f, fade_ = 1.0f, held_ = 0.5f;
  Rng rng_;
};

}  // namespace volt
```

**Step 4: Run** `.\tools\test-engine.ps1 lfo` — all pass.

**Step 5: Commit** — `feat(engine): LFO`.

---

### Task 7: Voice

A voice renders one block of its oscillator mix into one of the four lanes of a filter group (`pre`), then, after the filter ran, applies the amplitude envelope and pan and adds itself to the stereo bus (`post`). The filter sits between the two calls and is added in Task 9; until then the engine passes the signal straight through.

**Files:**
- Create: `engine/src/voice.h`, `engine/src/voice.cpp`
- Modify: `engine/CMakeLists.txt` (add `src/voice.cpp` to `volt_engine`)

No test file of its own: a voice is only meaningful inside the engine, and Task 8 tests it there.

**Step 1: `engine/src/voice.h`**

```cpp
#pragma once
#include "adsr.h"
#include "lfo.h"
#include "osc.h"
#include "params.h"
#include "util.h"

namespace volt {

// What every voice needs to know about the current block.
struct BlockCtx {
  const float* p;    // smoothed real parameter values, indexed by ParamId
  float sr;          // sample rate
  float dt;          // seconds per block (kBlock / sr)
  float bend;        // pitch bend, in semitones
  float modWheel;    // 0..1
  float lfo1Global;  // this block's value of the shared LFO 1
};

class Voice {
 public:
  bool active = false;
  bool held = false;       // key is down
  bool sustained = false;  // key is up but the pedal holds it
  int note = -1;           // the key that owns this voice
  uint64_t age = 0;        // start order; smaller = older

  void seed(uint32_t s);
  // Starts (or retriggers) the voice. fromPitch: where a glide starts; pass `note` for none.
  void start(int note, float velocity, float fromPitch, float detuneSemis, float pan, float gain);
  void retarget(int note);  // legato: new pitch, envelopes keep running
  void release();
  void kill();              // silent immediately (panic)

  // Renders kBlock pre-filter samples into io[s][lane]. Returns the filter cutoff in Hz for this block.
  float pre(const BlockCtx& c, float (*io)[4], int lane);
  // Reads kBlock filtered samples from io[s][lane] and accumulates into the stereo bus.
  void post(const BlockCtx& c, const float (*io)[4], int lane, float* left, float* right);

  float pitch() const { return pitch_; }
  bool releasing() const { return active && !held && !sustained; }

 private:
  Osc osc_[3];
  Adsr fenv_, aenv_;
  Lfo lfo1_, lfo2_;
  Rng rng_;
  float velocity_ = 1.0f, pitch_ = 60.0f, target_ = 60.0f;
  float detune_ = 0.0f, pan_ = 0.0f, gain_ = 1.0f;
  float drift_ = 0.0f, subPhase_ = 0.0f, hpf_ = 0.0f, osc2Prev_ = 0.0f;
  float ampPrev_ = 0.0f, lfo1Val_ = 0.0f, lfo2Val_ = 0.0f;
};

}  // namespace volt
```

**Step 2: `engine/src/voice.cpp`**

```cpp
#include "voice.h"

namespace volt {

namespace {
// Soft clipper: unity gain for small signals, bounded at +-1.
inline float softClip(float x) {
  x = clampf(x, -3.0f, 3.0f);
  return x * (27.0f + x * x) / (27.0f + 9.0f * x * x);
}
}  // namespace

void Voice::seed(uint32_t s) {
  rng_.s = s | 1u;
  lfo1_.seed(s * 2654435761u);
  lfo2_.seed(s * 40503u + 7u);
}

void Voice::start(int n, float velocity, float fromPitch, float detuneSemis, float pan, float gain) {
  if (!active) {
    // A fresh voice: free-running oscillators start anywhere, like an analogue synth.
    for (auto& o : osc_) o.phase = rng_.unipolar();
    subPhase_ = 0.0f; hpf_ = 0.0f; osc2Prev_ = 0.0f; ampPrev_ = 0.0f;
    fenv_.reset(); aenv_.reset();
    lfo1_.restart(); lfo2_.restart();
  }
  active = true; held = true; sustained = false;
  note = n; velocity_ = velocity;
  target_ = (float)n; pitch_ = fromPitch;
  detune_ = detuneSemis; pan_ = pan; gain_ = gain;
  fenv_.gate(true); aenv_.gate(true);
}

void Voice::retarget(int n) { note = n; target_ = (float)n; held = true; sustained = false; }

void Voice::release() {
  held = false; sustained = false;
  fenv_.gate(false); aenv_.gate(false);
}

void Voice::kill() {
  active = false; held = false; sustained = false; note = -1;
  fenv_.reset(); aenv_.reset(); ampPrev_ = 0.0f;
}

float Voice::pre(const BlockCtx& c, float (*io)[4], int lane) {
  const float* p = c.p;

  // --- control rate -------------------------------------------------------
  fenv_.set(p[kFenvAttack], p[kFenvDecay], p[kFenvSustain], p[kFenvRelease], c.dt);
  const float fe = fenv_.tick();

  const float own1 = lfo1_.tick((int)p[kLfo1Wave], p[kLfo1Rate], p[kLfo1Delay], c.dt);
  const float wheelGate = 1.0f - p[kLfo1Wheel] + p[kLfo1Wheel] * c.modWheel;
  lfo1Val_ = (p[kLfo1Global] > 0.5f ? c.lfo1Global : own1) * wheelGate;
  lfo2Val_ = lfo2_.tick((int)p[kLfo2Wave], p[kLfo2Rate], p[kLfo2Delay], c.dt);

  const float glide = p[kVoiceGlide];
  if (glide > 0.001f) pitch_ += (target_ - pitch_) * (1.0f - std::exp(-c.dt * 4.6f / glide));
  else pitch_ = target_;

  drift_ += 0.02f * (rng_.bipolar() - drift_);  // slow random walk, about +-0.1

  const float lfoPitch = 12.0f * (lfo1Val_ * p[kLfo1Pitch] * p[kLfo1Pitch] + lfo2Val_ * p[kLfo2Pitch] * p[kLfo2Pitch]);
  const float base = pitch_ + c.bend + detune_ + drift_ * p[kVoiceDrift] + lfoPitch;
  const float lfoPw = 0.45f * (lfo1Val_ * p[kLfo1Pw] + lfo2Val_ * p[kLfo2Pw]);

  float inc[3], pw[3], level[3];
  int wave[3];
  for (int k = 0; k < 3; ++k) {
    const float* o = p + kOsc1Wave + k * kOscStride;
    float n = base + 12.0f * o[kOsc1Octave - kOsc1Wave] + o[kOsc1Semi - kOsc1Wave] + 0.01f * o[kOsc1Fine - kOsc1Wave];
    float w = o[kOsc1Pw - kOsc1Wave] + lfoPw;
    if (k == 0) { n += 24.0f * fe * p[kPmodFenvPitch]; w += 0.45f * fe * p[kPmodFenvPw]; }
    inc[k] = noteToHz(n) / c.sr;
    pw[k] = clampf(w, 0.05f, 0.95f);
    wave[k] = (int)o[0];
    level[k] = o[kOsc1Level - kOsc1Wave];
  }

  const float fm = 2.0f * p[kPmodOsc2Pitch] * p[kPmodOsc2Pitch];
  const float pwm = 0.45f * p[kPmodOsc2Pw];
  const bool sync = p[kOsc2Sync] > 0.5f;
  const float subLevel = p[kMixSub], noiseLevel = p[kMixNoise];
  const float drive = 1.0f + 4.0f * p[kMixDrive];
  const float hpfG = 1.0f - std::exp(-6.28318530718f * p[kHpfCutoff] / c.sr);

  // --- audio rate ---------------------------------------------------------
  for (int s = 0; s < kBlock; ++s) {
    const float inc0 = inc[0] * (1.0f + fm * osc2Prev_);
    const float a = osc_[0].tick(inc0 > 0.0f ? inc0 : 0.0f, wave[0], clampf(pw[0] + pwm * osc2Prev_, 0.05f, 0.95f));
    if (sync && osc_[0].wrapped) osc_[1].syncTo(osc_[0].wrapFrac, inc[1]);
    const float b = osc_[1].tick(inc[1], wave[1], pw[1]);
    osc2Prev_ = b;
    const float d = osc_[2].tick(inc[2], wave[2], pw[2]);

    subPhase_ += 0.5f * inc[0];
    if (subPhase_ >= 1.0f) subPhase_ -= 1.0f;
    const float sub = subPhase_ < 0.5f ? 1.0f : -1.0f;

    float x = 0.5f * (a * level[0] + b * level[1] + d * level[2] + sub * subLevel + rng_.bipolar() * noiseLevel);
    x = softClip(x * drive);
    hpf_ += hpfG * (x - hpf_);  // one-pole low-pass; subtracting it leaves the high-pass
    io[s][lane] = x - hpf_;
  }

  // --- filter cutoff for this block ----------------------------------------
  float cut = hzToNote(p[kFilterCutoff]);
  cut += 84.0f * fe * p[kFilterEnv];
  cut += (pitch_ - 60.0f) * p[kFilterKeytrack];
  cut += 48.0f * velocity_ * p[kFilterVel];
  cut += 48.0f * (lfo1Val_ * p[kLfo1Cutoff] + lfo2Val_ * p[kLfo2Cutoff]);
  float maxHz = 0.45f * c.sr;
  if (maxHz > 20000.0f) maxHz = 20000.0f;
  return clampf(noteToHz(cut), 20.0f, maxHz);
}

void Voice::post(const BlockCtx& c, const float (*io)[4], int lane, float* left, float* right) {
  const float* p = c.p;
  aenv_.set(p[kAenvAttack], p[kAenvDecay], p[kAenvSustain], p[kAenvRelease], c.dt);
  const float ae = aenv_.tick();

  const float velGain = 1.0f - p[kAenvVel] * (1.0f - velocity_);
  const float trem = 1.0f - 0.5f * (p[kLfo1Amp] * (1.0f + lfo1Val_) + p[kLfo2Amp] * (1.0f + lfo2Val_)) * 0.5f;
  const float target = ae * velGain * clampf(trem, 0.0f, 1.0f) * gain_;

  // Equal-power pan; pan_ is -1..1.
  const float angle = (pan_ + 1.0f) * 0.25f * 3.14159265359f;
  const float gl = std::cos(angle), gr = std::sin(angle);

  float g = ampPrev_;
  const float step = (target - ampPrev_) / (float)kBlock;
  for (int s = 0; s < kBlock; ++s) {
    g += step;
    const float x = io[s][lane] * g;
    left[s] += x * gl;
    right[s] += x * gr;
  }
  ampPrev_ = target;

  if (!aenv_.active()) { active = false; held = false; sustained = false; note = -1; ampPrev_ = 0.0f; }
}

}  // namespace volt
```

**Step 3: Build** — `.\tools\test-engine.ps1` must compile and all earlier tests still pass.

**Step 4: Commit** — `feat(engine): voice`.

---

### Task 8: Engine — voice allocation, modes, rendering

**Files:**
- Create: `engine/src/engine.h`, `engine/src/engine.cpp`, `engine/tests/test_engine.cpp`
- Modify: `engine/CMakeLists.txt` (`src/engine.cpp`, `tests/test_engine.cpp`)

**Step 1: Failing tests — `engine/tests/test_engine.cpp`**

```cpp
#include "dsp_probe.h"
#include "engine.h"
#include "test.h"

using namespace volt;

namespace {
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
}  // namespace

TEST(engine_is_silent_with_no_notes) {
  Rig r;
  auto x = r.play(0.2f);
  CHECK(probe::peak(x) == 0.0);
}

TEST(engine_plays_a_note_at_concert_pitch) {
  Rig r;
  r.set(kVoiceDrift, 0.0f);
  r.set(kOsc1Wave, 3);  // sine: one zero crossing per cycle
  r.on(69);
  r.play(0.1f);
  auto x = r.play(1.0f);
  CHECK(probe::rms(x) > 0.05);
  CHECK_NEAR(probe::zeroCrossHz(x, 48000.0), 440.0, 1.5);
  CHECK(r.e.activeVoices() == 1);
}

TEST(engine_note_off_fades_to_silence_and_frees_the_voice) {
  Rig r;
  r.on(60);
  r.play(0.2f);
  r.off(60);
  r.play(3.0f);
  CHECK(r.e.activeVoices() == 0);
  CHECK(probe::peak(r.play(0.1f)) == 0.0);
}

TEST(engine_never_plays_more_than_sixteen_voices) {
  Rig r;
  for (int n = 40; n < 60; ++n) r.on(n);
  auto x = r.play(0.3f);
  CHECK(r.e.activeVoices() == kMaxVoices);
  CHECK(probe::finite(x));
  CHECK(probe::peak(x) <= 1.0);
}

TEST(engine_the_same_key_twice_reuses_its_voice) {
  Rig r;
  r.on(60); r.play(0.05f);
  r.off(60); r.on(60); r.play(0.05f);
  CHECK(r.e.activeVoices() == 1);
}

TEST(engine_mono_plays_one_voice_and_returns_to_the_held_key) {
  Rig r;
  r.set(kVoiceMode, 1);
  r.set(kVoiceDrift, 0.0f);
  r.set(kOsc1Wave, 3);
  r.play(0.01f);
  r.on(69); r.play(0.1f);
  r.on(81); r.play(0.3f);
  CHECK(r.e.activeVoices() == 1);
  CHECK_NEAR(probe::zeroCrossHz(r.play(0.5f), 48000.0), 880.0, 4.0);
  r.off(81);
  r.play(0.1f);
  CHECK_NEAR(probe::zeroCrossHz(r.play(0.5f), 48000.0), 440.0, 2.0);
}

TEST(engine_unison_stacks_voices_on_one_key) {
  Rig r;
  r.set(kVoiceMode, 3);
  r.set(kVoiceUnison, 4);
  r.play(0.01f);
  r.on(60);
  r.play(0.1f);
  CHECK(r.e.activeVoices() == 4);
}

TEST(engine_sustain_pedal_holds_released_keys) {
  Rig r;
  r.e.post({Event::Sustain, 0, 1.0f});
  r.on(60); r.play(0.1f);
  r.off(60); r.play(1.5f);
  CHECK(r.e.activeVoices() == 1);
  r.e.post({Event::Sustain, 0, 0.0f});
  r.play(3.0f);
  CHECK(r.e.activeVoices() == 0);
}

TEST(engine_pitch_bend_moves_the_pitch_by_the_bend_range) {
  Rig r;
  r.set(kVoiceDrift, 0.0f);
  r.set(kOsc1Wave, 3);
  r.set(kVoiceBend, 12);
  r.play(0.01f);
  r.on(69);
  r.e.post({Event::PitchBend, 0, 1.0f});
  r.play(0.2f);
  CHECK_NEAR(probe::zeroCrossHz(r.play(0.5f), 48000.0), 880.0, 4.0);
}

TEST(engine_all_notes_off_silences_everything_at_once) {
  Rig r;
  for (int n = 50; n < 58; ++n) r.on(n);
  r.play(0.1f);
  r.e.post({Event::AllNotesOff, 0, 0});
  r.play(0.05f);
  CHECK(r.e.activeVoices() == 0);
}

TEST(engine_output_is_the_same_for_any_callback_size) {
  auto run = [](int chunk) { Rig r; r.set(kVoiceDrift, 0.0f); r.on(57); return r.play(0.25f, chunk); };
  // Oscillator start phases are seeded per engine, so two engines are sample-identical.
  auto a = run(96), b = run(7);
  CHECK(a.size() == b.size());
  double diff = 0;
  for (size_t i = 0; i < a.size(); ++i) diff = std::fmax(diff, std::fabs(a[i] - b[i]));
  CHECK(diff < 1e-6);
}

TEST(engine_stays_finite_and_bounded_under_random_abuse) {
  Rig r;
  Rng rng;
  for (int round = 0; round < 200; ++round) {
    for (int k = 0; k < 6; ++k) r.e.post({Event::SetParam, (int)(rng.next() % kNumParams), rng.unipolar()});
    int note = 24 + (int)(rng.next() % 84);
    if (rng.next() & 1) r.on(note, rng.unipolar()); else r.off(note);
    auto x = r.play(0.02f, 1 + (int)(rng.next() % 200));
    CHECK(probe::finite(x));
    CHECK(probe::peak(x) <= 1.0);
  }
}

TEST(engine_reports_a_peak_level) {
  Rig r;
  r.on(60);
  r.play(0.2f);
  CHECK(r.e.peak() > 0.01f);
}
```

**Step 2: Run** `.\tools\test-engine.ps1 engine` — build error.

**Step 3: `engine/src/engine.h`**

```cpp
#pragma once
#include <atomic>
#include <mutex>
#include "event_queue.h"
#include "params.h"
#include "voice.h"

namespace volt {

class Engine {
 public:
  Engine();

  // Not concurrent with render(): call before the stream starts or after it stopped.
  void setSampleRate(float sr);

  // Any non-audio thread. Returns false when the queue is full (the event is dropped).
  bool post(const Event& e);

  // Audio thread only. Writes `frames` interleaved stereo float frames.
  void render(float* out, int frames);

  // Any thread.
  int activeVoices() const { return activeVoices_.load(std::memory_order_relaxed); }
  float peak() const { return peak_.load(std::memory_order_relaxed); }  // last block's peak, 0..1

 private:
  void renderBlock();
  void handle(const Event& e);
  void noteOn(int note, float velocity);
  void noteOff(int note);
  void releaseOrSustain(Voice& v);
  Voice* allocPoly(int note);
  void startVoice(Voice& v, int note, float velocity, float detune, float pan, float gain);

  EventQueue queue_;
  std::mutex postMutex_;  // serialises producers; never taken by the audio thread

  float sr_ = 48000.0f;
  float target_[kNumParams];  // real values the parameters are heading to
  float value_[kNumParams];   // smoothed real values the voices read
  float smooth_ = 1.0f;

  Voice voices_[kMaxVoices];
  uint64_t counter_ = 0;
  int stack_[16];  // held keys in mono modes, oldest first
  int stackSize_ = 0;
  float lastPitch_ = 60.0f;
  bool anyNoteYet_ = false;
  bool sustain_ = false;
  float bend_ = 0.0f, modWheel_ = 0.0f;
  Lfo globalLfo_;

  alignas(16) float io_[kMaxVoices / 4][kBlock][4];
  float blockL_[kBlock], blockR_[kBlock];
  int blockPos_ = kBlock;  // kBlock = nothing buffered

  std::atomic<int> activeVoices_{0};
  std::atomic<float> peak_{0.0f};
};

}  // namespace volt
```

**Step 4: `engine/src/engine.cpp`**

```cpp
#include "engine.h"
#include <cstring>

namespace volt {

namespace {
enum Mode { kPoly = 0, kMono = 1, kLegato = 2, kUnison = 3 };
const float kVoiceGain = 0.35f;
}  // namespace

Engine::Engine() {
  for (int i = 0; i < kNumParams; ++i) target_[i] = value_[i] = kParams[i].def;
  for (int i = 0; i < kMaxVoices; ++i) voices_[i].seed(0x9E3779B9u * (uint32_t)(i + 1));
  globalLfo_.seed(0xC0FFEEu);
  std::memset(io_, 0, sizeof(io_));
  setSampleRate(48000.0f);
}

void Engine::setSampleRate(float sr) {
  sr_ = sr;
  smooth_ = 1.0f - std::exp(-(float)kBlock / (0.010f * sr));  // 10 ms time constant
  blockPos_ = kBlock;
}

bool Engine::post(const Event& e) {
  std::lock_guard<std::mutex> lock(postMutex_);
  return queue_.push(e);
}

void Engine::render(float* out, int frames) {
  Event e;
  while (queue_.pop(e)) handle(e);

  int done = 0;
  while (done < frames) {
    if (blockPos_ == kBlock) { renderBlock(); blockPos_ = 0; }
    int n = frames - done;
    if (n > kBlock - blockPos_) n = kBlock - blockPos_;
    for (int i = 0; i < n; ++i) {
      out[2 * (done + i)] = blockL_[blockPos_ + i];
      out[2 * (done + i) + 1] = blockR_[blockPos_ + i];
    }
    blockPos_ += n;
    done += n;
  }
}

void Engine::handle(const Event& e) {
  switch (e.type) {
    case Event::NoteOn:
      if (e.b <= 0.0f) noteOff(e.a); else noteOn(e.a, e.b);
      break;
    case Event::NoteOff: noteOff(e.a); break;
    case Event::SetParam:
      if (e.a >= 0 && e.a < kNumParams) {
        target_[e.a] = denorm(e.a, e.b);
        if (kParams[e.a].curve == Curve::Step) value_[e.a] = target_[e.a];
      }
      break;
    case Event::PitchBend: bend_ = clampf(e.b, -1.0f, 1.0f); break;
    case Event::ModWheel: modWheel_ = clampf(e.b, 0.0f, 1.0f); break;
    case Event::Sustain:
      sustain_ = e.b > 0.5f;
      if (!sustain_)
        for (auto& v : voices_) if (v.active && v.sustained) v.release();
      break;
    case Event::AllNotesOff:
      for (auto& v : voices_) v.kill();
      stackSize_ = 0;
      break;
  }
}

void Engine::startVoice(Voice& v, int note, float velocity, float detune, float pan, float gain) {
  const bool glide = value_[kVoiceGlide] > 0.001f && anyNoteYet_ && value_[kVoiceGlideLegato] < 0.5f;
  v.start(note, velocity, glide ? lastPitch_ : (float)note, detune, pan, gain);
  v.age = ++counter_;
}

Voice* Engine::allocPoly(int note) {
  for (auto& v : voices_) if (v.active && v.note == note) return &v;  // same key: reuse
  for (auto& v : voices_) if (!v.active) return &v;
  Voice* best = nullptr;
  for (auto& v : voices_) if (v.releasing() && (!best || v.age < best->age)) best = &v;
  if (best) return best;
  best = &voices_[0];
  for (auto& v : voices_) if (v.age < best->age) best = &v;
  return best;
}

void Engine::noteOn(int note, float velocity) {
  const int mode = (int)value_[kVoiceMode];
  if (mode == kPoly) {
    startVoice(*allocPoly(note), note, velocity, 0.0f, 0.0f, kVoiceGain);
  } else {
    // Mono family: remember the key, play it on the first n voices.
    for (int i = 0; i < stackSize_; ++i)
      if (stack_[i] == note) { std::memmove(stack_ + i, stack_ + i + 1, sizeof(int) * (stackSize_ - i - 1)); --stackSize_; break; }
    if (stackSize_ == 16) { std::memmove(stack_, stack_ + 1, sizeof(int) * 15); --stackSize_; }
    stack_[stackSize_++] = note;

    const int n = mode == kUnison ? (int)value_[kVoiceUnison] : 1;
    const bool legato = mode == kLegato && stackSize_ > 1 && voices_[0].active && voices_[0].held;
    const float gain = kVoiceGain / std::sqrt((float)n);
    for (int i = 0; i < kMaxVoices; ++i) {
      Voice& v = voices_[i];
      if (i >= n) { if (v.active && !v.releasing()) v.release(); continue; }
      const float pos = n > 1 ? 2.0f * (float)i / (float)(n - 1) - 1.0f : 0.0f;  // -1..1
      if (legato) v.retarget(note);
      else startVoice(v, note, velocity, 0.5f * value_[kVoiceDetune] * pos, value_[kVoiceSpread] * pos, gain);
    }
  }
  lastPitch_ = (float)note;
  anyNoteYet_ = true;
}

void Engine::releaseOrSustain(Voice& v) {
  if (sustain_) { v.held = false; v.sustained = true; }
  else v.release();
}

void Engine::noteOff(int note) {
  const int mode = (int)value_[kVoiceMode];
  if (mode == kPoly) {
    for (auto& v : voices_) if (v.active && v.held && v.note == note) releaseOrSustain(v);
    return;
  }
  bool wasTop = stackSize_ > 0 && stack_[stackSize_ - 1] == note;
  for (int i = 0; i < stackSize_; ++i)
    if (stack_[i] == note) { std::memmove(stack_ + i, stack_ + i + 1, sizeof(int) * (stackSize_ - i - 1)); --stackSize_; break; }
  if (stackSize_ == 0) {
    for (auto& v : voices_) if (v.active && v.held) releaseOrSustain(v);
  } else if (wasTop) {
    for (auto& v : voices_) if (v.active && v.held) v.retarget(stack_[stackSize_ - 1]);
    lastPitch_ = (float)stack_[stackSize_ - 1];
  }
}

void Engine::renderBlock() {
  for (int i = 0; i < kNumParams; ++i)
    if (kParams[i].curve != Curve::Step) value_[i] += (target_[i] - value_[i]) * smooth_;

  BlockCtx c;
  c.p = value_;
  c.sr = sr_;
  c.dt = (float)kBlock / sr_;
  c.bend = bend_ * value_[kVoiceBend];
  c.modWheel = modWheel_;
  c.lfo1Global = globalLfo_.tick((int)value_[kLfo1Wave], value_[kLfo1Rate], 0.0f, c.dt);

  std::memset(blockL_, 0, sizeof(blockL_));
  std::memset(blockR_, 0, sizeof(blockR_));

  int active = 0;
  for (int q = 0; q < kMaxVoices / 4; ++q) {
    bool any = false;
    float cutoff[4] = {1000.0f, 1000.0f, 1000.0f, 1000.0f};
    for (int lane = 0; lane < 4; ++lane) {
      Voice& v = voices_[q * 4 + lane];
      if (v.active) { cutoff[lane] = v.pre(c, io_[q], lane); any = true; }
      else for (int s = 0; s < kBlock; ++s) io_[q][s][lane] = 0.0f;
    }
    if (!any) continue;
    (void)cutoff;  // Task 9 puts the filter here.
    for (int lane = 0; lane < 4; ++lane) {
      Voice& v = voices_[q * 4 + lane];
      if (!v.active) continue;
      v.post(c, io_[q], lane, blockL_, blockR_);
      if (v.active) ++active;
    }
  }

  // Master volume, then a soft limiter so no preset can clip the output.
  const float vol = value_[kMasterVolume] * value_[kMasterVolume] * 2.0f;
  float pk = 0.0f;
  for (int s = 0; s < kBlock; ++s) {
    float l = std::tanh(blockL_[s] * vol), r = std::tanh(blockR_[s] * vol);
    blockL_[s] = l; blockR_[s] = r;
    const float a = std::fabs(l) > std::fabs(r) ? std::fabs(l) : std::fabs(r);
    if (a > pk) pk = a;
  }
  peak_.store(pk, std::memory_order_relaxed);
  activeVoices_.store(active, std::memory_order_relaxed);
}

}  // namespace volt
```

Notes for whoever debugs a failing test here:
- In `engine_mono_…`, legato retargeting only applies in mode 2; mode 1 (mono) retriggers on every key, and returning to a still-held key after a release uses `retarget` in every mono mode.
- `engine_output_is_the_same_for_any_callback_size` depends on events being drained once per `render` call. Both runs post the note before the first `render`, so they are equal. If you ever make events sample-accurate, keep this property.
- `engine_never_plays_more_than_sixteen_voices` asserts `peak <= 1.0`: `tanh` guarantees it.
- A voice the user steals keeps its filter state and envelope level, on purpose: no click.

**Step 5: Run** `.\tools\test-engine.ps1` — every test passes.

**Step 6: Commit** — `feat(engine): voice allocation, play modes and rendering`.

---

### Task 9: Filters (sst-filters)

This is the plan's main technical risk: the library is written with SSE intrinsics and upstream says nothing about Android or arm64. The task starts with a spike that must succeed on **both** MSVC x64 and NDK arm64 before any more code is written.

**Files:**
- Create: `.gitmodules` (by git), `third_party/sst-filters`, `third_party/sst-basic-blocks`, `third_party/simde`
- Create: `engine/src/filter.h`, `engine/src/filter.cpp`, `engine/tests/test_filter.cpp`
- Modify: `engine/CMakeLists.txt`, `engine/src/engine.h`, `engine/src/engine.cpp`

**Step 1: Vendor the libraries**

```powershell
git submodule add https://github.com/surge-synthesizer/sst-filters.git third_party/sst-filters
git submodule add https://github.com/surge-synthesizer/sst-basic-blocks.git third_party/sst-basic-blocks
git submodule add https://github.com/simd-everywhere/simde.git third_party/simde
```

Do **not** use their CMake files (they fetch Catch2, JUCE and more through CPM). All three are header-only; only include paths are needed. Add to `engine/CMakeLists.txt`, after `add_library`:

```cmake
set(VOLT_THIRD_PARTY ${CMAKE_CURRENT_SOURCE_DIR}/../third_party)
target_include_directories(volt_engine SYSTEM PRIVATE
  ${VOLT_THIRD_PARTY}/sst-filters/include
  ${VOLT_THIRD_PARTY}/sst-basic-blocks/include
  ${VOLT_THIRD_PARTY}/simde
)
```

and add `src/filter.cpp` to the library sources.

**Step 2: Read before writing** (15 minutes, it saves hours)

1. `third_party/sst-filters/tests/TestUtils.h` — upstream's own minimal call sequence. The code in Step 5 follows it.
2. `third_party/sst-filters/include/sst/filters/FilterCoefficientMaker.h` and `FilterCoefficientMaker_Impl.h` — **verify** two things the plan assumes:
   - `updateState(state, lane)` copies the maker's `C` and `dC` into lane `lane` of `state.C` / `state.dC`;
   - `updateCoefficients(state, lane)` copies lane `lane` of `state.C` back into the maker after a block (the filter function advances `state.C` by `state.dC` every sample).
   If the names or meanings differ, adapt `setLane`/`endBlock` in Step 5 and say so in NOTES.md.
3. `third_party/sst-basic-blocks/include/sst/basic-blocks/simd/setup.h` — how `SIMD_M128` / `SIMD_MM(...)` are defined and what it expects on non-x86 (which simde header it includes, which macros select it). Add any macro it needs as a `target_compile_definitions(volt_engine PRIVATE …)`.
4. `third_party/sst-filters/include/sst/filters/FilterConfiguration.h` — confirm the enumerators used in the model table below exist.

sst-filters also has a newer wrapper, `sst::filtersplusplus::Filter` (`include/sst/filters++/api.h`). If the low-level API turns out to be awkward, using the wrapper is fine as long as `filter.h` and its tests stay as written.

**Step 3: Failing tests — `engine/tests/test_filter.cpp`**

```cpp
#include "dsp_probe.h"
#include "filter.h"
#include "test.h"

using namespace volt;

namespace {
// Feeds a sine into lane `lane` and returns the output RMS after the filter settled.
double sineThrough(int model, float cutoffHz, float reso, float hz, int lane = 0) {
  QuadFilter f;
  f.setSampleRate(48000.0f);
  f.setModel(model);
  alignas(16) float io[kBlock][4];
  std::vector<float> out;
  float phase = 0;
  for (int b = 0; b < 3000; ++b) {  // 1 second
    for (int s = 0; s < kBlock; ++s) {
      for (int l = 0; l < 4; ++l) io[s][l] = 0.0f;
      io[s][lane] = 0.25f * std::sin(6.28318530718f * phase);
      phase += hz / 48000.0f; if (phase >= 1) phase -= 1;
    }
    for (int l = 0; l < 4; ++l) f.setLane(l, cutoffHz, reso);
    f.process(io);
    f.endBlock();
    for (int s = 0; s < kBlock; ++s) out.push_back(io[s][lane]);
  }
  return probe::rms(out, 24000);
}
const double kIn = 0.25 / 1.41421356;  // RMS of the test sine
}  // namespace

TEST(filter_every_model_passes_lows_and_cuts_highs) {
  for (int model = 0; model < kNumFilterModels; ++model) {
    double low = sineThrough(model, 1000.0f, 0.1f, 125.0f);
    double high = sineThrough(model, 1000.0f, 0.1f, 8000.0f);
    CHECK(low > 0.4 * kIn);                 // three octaves below: mostly intact
    CHECK(high < (model == 1 ? 0.10 : 0.03) * low);  // three octaves above: 12 dB model vs 24 dB models
  }
}

TEST(filter_resonance_boosts_the_cutoff_frequency) {
  for (int model = 0; model < kNumFilterModels; ++model) {
    double flat = sineThrough(model, 1000.0f, 0.05f, 1000.0f);
    double peaky = sineThrough(model, 1000.0f, 0.85f, 1000.0f);
    CHECK(peaky > 1.5 * flat);
  }
}

TEST(filter_opening_the_cutoff_lets_more_through) {
  for (int model = 0; model < kNumFilterModels; ++model)
    CHECK(sineThrough(model, 8000.0f, 0.1f, 2000.0f) > 4.0 * sineThrough(model, 300.0f, 0.1f, 2000.0f));
}

TEST(filter_lanes_do_not_leak_into_each_other) {
  for (int model = 0; model < kNumFilterModels; ++model) {
    QuadFilter f;
    f.setSampleRate(48000.0f);
    f.setModel(model);
    alignas(16) float io[kBlock][4];
    double other = 0;
    for (int b = 0; b < 300; ++b) {
      for (int s = 0; s < kBlock; ++s) { io[s][0] = io[s][1] = io[s][3] = 0.0f; io[s][2] = (s & 1) ? 0.5f : -0.5f; }
      for (int l = 0; l < 4; ++l) f.setLane(l, 2000.0f, 0.5f);
      f.process(io);
      f.endBlock();
      for (int s = 0; s < kBlock; ++s) other = std::fmax(other, std::fabs(io[s][0]) + std::fabs(io[s][1]) + std::fabs(io[s][3]));
    }
    CHECK(other < 1e-6);
  }
}

TEST(filter_stays_finite_at_the_extremes) {
  const float cutoffs[] = {20.0f, 20000.0f}, resos[] = {0.0f, 1.0f};
  for (int model = 0; model < kNumFilterModels; ++model)
    for (float cut : cutoffs) for (float reso : resos) {
      QuadFilter f;
      f.setSampleRate(48000.0f);
      f.setModel(model);
      alignas(16) float io[kBlock][4];
      bool ok = true;
      double pk = 0;
      for (int b = 0; b < 1500; ++b) {
        for (int s = 0; s < kBlock; ++s) for (int l = 0; l < 4; ++l) io[s][l] = ((b + s) & 8) ? 1.0f : -1.0f;
        for (int l = 0; l < 4; ++l) f.setLane(l, cut, reso);
        f.process(io);
        f.endBlock();
        for (int s = 0; s < kBlock; ++s) for (int l = 0; l < 4; ++l) { ok = ok && std::isfinite(io[s][l]); pk = std::fmax(pk, std::fabs(io[s][l])); }
      }
      CHECK(ok);
      CHECK(pk < 20.0);  // loud is allowed with full resonance; runaway is not
    }
}

TEST(filter_reset_lane_clears_the_ringing) {
  QuadFilter f;
  f.setSampleRate(48000.0f);
  f.setModel(0);
  alignas(16) float io[kBlock][4];
  for (int b = 0; b < 200; ++b) {
    for (int s = 0; s < kBlock; ++s) for (int l = 0; l < 4; ++l) io[s][l] = (s == 0) ? 1.0f : 0.0f;
    for (int l = 0; l < 4; ++l) f.setLane(l, 500.0f, 0.95f);
    f.process(io); f.endBlock();
  }
  f.resetLane(1);
  for (int s = 0; s < kBlock; ++s) for (int l = 0; l < 4; ++l) io[s][l] = 0.0f;
  for (int l = 0; l < 4; ++l) f.setLane(l, 500.0f, 0.95f);
  f.process(io); f.endBlock();
  double lane1 = 0;
  for (int s = 0; s < kBlock; ++s) lane1 = std::fmax(lane1, std::fabs(io[s][1]));
  CHECK(lane1 < 1e-6);
}
```

**Step 4: `engine/src/filter.h`** — the only header the rest of the engine sees. No sst type leaks out of it.

```cpp
#pragma once
#include <memory>
#include "util.h"

namespace volt {

constexpr int kNumFilterModels = 4;  // 0 Ladder, 1 Roland 12 dB, 2 Roland 24 dB, 3 Prophet

// One filter for four voices at once (SIMD lanes).
// Per block:  setLane() for each lane in use  ->  process()  ->  endBlock().
class QuadFilter {
 public:
  QuadFilter();
  ~QuadFilter();
  void setSampleRate(float sr);
  void setModel(int model);   // clears all four lanes
  void resetLane(int lane);   // call when a silent voice starts
  void setLane(int lane, float cutoffHz, float resonance);  // resonance 0..1
  void process(float (*io)[4]);  // io[kBlock][4], 16-byte aligned, filtered in place
  void endBlock();

 private:
  struct Impl;
  std::unique_ptr<Impl> impl_;  // allocated once in the constructor, never on the audio thread
};

}  // namespace volt
```

**Step 5: `engine/src/filter.cpp`**

Model table (the design's three characters; "Roland" is offered in 12 and 24 dB):

| Model | sst type | sst subtype | If it is too heavy on the phone (Task 15) |
|---|---|---|---|
| 0 Ladder | `fut_vintageladder` | `st_vintage_type1_compensated` | `fut_lpmoog` / `st_lpmoog_24dB` |
| 1 Roland 12 | `fut_lp12` | `st_Clean` | — |
| 2 Roland 24 | `fut_lp24` | `st_Clean` | — |
| 3 Prophet | `fut_obxd_4pole` | `st_obxd4pole_24dB` | — |

```cpp
#include "filter.h"

#include "sst/filters.h"

namespace volt {

namespace sf = sst::filters;

struct QuadFilter::Impl {
  sf::QuadFilterUnitState state{};
  sf::FilterCoefficientMaker<> maker[4];
  sf::FilterUnitQFPtr fn = nullptr;
  sf::FilterType type = sf::fut_none;
  sf::FilterSubType sub = sf::st_Standard;
  float sr = 48000.0f;
  bool used[4] = {false, false, false, false};
  // Only comb filters read these, but upstream always points the state at real memory.
  float delay[4][sf::utilities::MAX_FB_COMB + sf::utilities::SincTable::FIRipol_N] = {};
};

QuadFilter::QuadFilter() : impl_(new Impl) {
  for (int l = 0; l < 4; ++l) {
    impl_->state.DB[l] = impl_->delay[l];
    impl_->state.active[l] = (int)0xffffffff;
    impl_->state.WP[l] = 0;
  }
  setSampleRate(48000.0f);
  setModel(0);
}

QuadFilter::~QuadFilter() = default;

void QuadFilter::setSampleRate(float sr) {
  impl_->sr = sr;
  for (auto& m : impl_->maker) m.setSampleRateAndBlockSize(sr, kBlock);
}

void QuadFilter::setModel(int model) {
  static const struct { sf::FilterType t; sf::FilterSubType s; } kModels[kNumFilterModels] = {
      {sf::fut_vintageladder, sf::st_vintage_type1_compensated},
      {sf::fut_lp12, sf::st_Clean},
      {sf::fut_lp24, sf::st_Clean},
      {sf::fut_obxd_4pole, sf::st_obxd4pole_24dB},
  };
  if (model < 0 || model >= kNumFilterModels) model = 0;
  impl_->type = kModels[model].t;
  impl_->sub = kModels[model].s;
  impl_->fn = sf::GetQFPtrFilterUnit(impl_->type, impl_->sub);
  for (int l = 0; l < 4; ++l) resetLane(l);
}

void QuadFilter::resetLane(int lane) {
  alignas(16) float tmp[4];
  for (int r = 0; r < sf::n_filter_registers; ++r) {
    SIMD_MM(store_ps)(tmp, impl_->state.R[r]);
    tmp[lane] = 0.0f;
    impl_->state.R[r] = SIMD_MM(load_ps)(tmp);
  }
  impl_->maker[lane].Reset();
  impl_->maker[lane].setSampleRateAndBlockSize(impl_->sr, kBlock);
}

void QuadFilter::setLane(int lane, float cutoffHz, float resonance) {
  // sst-filters takes the cutoff as a pitch in semitones relative to A440.
  const float pitch = 12.0f * std::log2(cutoffHz / 440.0f);
  impl_->maker[lane].MakeCoeffs(pitch, resonance, impl_->type, impl_->sub, nullptr, false);
  impl_->maker[lane].updateState(impl_->state, lane);
  impl_->used[lane] = true;
}

void QuadFilter::process(float (*io)[4]) {
  if (!impl_->fn) return;
  for (int s = 0; s < kBlock; ++s) {
    auto in = SIMD_MM(load_ps)(io[s]);
    SIMD_MM(store_ps)(io[s], impl_->fn(&impl_->state, in));
  }
}

void QuadFilter::endBlock() {
  for (int l = 0; l < 4; ++l)
    if (impl_->used[l]) { impl_->maker[l].updateCoefficients(impl_->state, l); impl_->used[l] = false; }
}

}  // namespace volt
```

**Step 6: Spike A — MSVC.** Add `tests/test_filter.cpp` to `volt_tests`. Run `.\tools\test-engine.ps1 filter`.

Expected: compiles, the six `filter_` tests pass. Likely first-time problems and what to do:
- *A header is not found* → a fourth include path is needed; look at what sst-filters' own `CMakeLists.txt` adds.
- *`SIMD_MM` undefined* → include what `sst/filters.h` expects (Step 2, item 3).
- *`filter_resonance_boosts…` fails for one model* → print the two RMS values. If resonance works but less than 1.5×, the subtype may be a gain-compensated one; the threshold may drop to 1.2× for that model with a NOTES.md line. If resonance has no effect at all, `updateState`/`updateCoefficients` are being used wrongly — re-read Step 2, item 2.
- *`filter_lanes_do_not_leak…` fails* → `setLane` is writing every lane (channel `-1` semantics). Fix the lane argument.

**Step 7: Spike B — NDK arm64 and x86_64, compile only.** The app does not exist yet, so drive the NDK toolchain directly:

```powershell
. .\tools\env.ps1
$ndk = "$env:ANDROID_HOME\ndk\30.0.16248370"
foreach ($abi in "arm64-v8a", "x86_64") {
  cmake -S engine -B "build\ndk-$abi" -G Ninja `
    "-DCMAKE_TOOLCHAIN_FILE=$ndk\build\cmake\android.toolchain.cmake" `
    "-DANDROID_ABI=$abi" -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release
  cmake --build "build\ndk-$abi"
}
```

Expected: `libvolt_engine.a` builds for both ABIs with no errors.

If arm64 fails inside sst headers:
1. Check the macros `sst/basic-blocks/simd/setup.h` wants for non-x86 and that `third_party/simde` is on the include path it uses (`simde/x86/sse2.h`).
2. If it still cannot be made to build in about an hour of work, **stop the spike** and switch to plan B: keep `filter.h` and `test_filter.cpp` exactly as they are and reimplement `filter.cpp` in plain scalar C++ (four independent lanes), porting only the three filters needed from the sst sources (`VintageLadders.h` RK or Huovilainen model, the 12/24 dB `IIR` biquad cascade, `OBXDFilter.h` 4-pole). The licence stays GPL-3. Record the decision and the reason in `docs/NOTES.md`.

**Step 8: Put the filter in the engine**

`engine/src/engine.h`: add `#include "filter.h"`, and the members `QuadFilter filters_[kMaxVoices / 4];` and `int model_ = -1;`.

`engine/src/engine.cpp`:
- In `setSampleRate`: `for (auto& f : filters_) f.setSampleRate(sr);`
- At the top of `renderBlock`, after the smoothing loop:

```cpp
  const int model = (int)value_[kFilterModel];
  if (model != model_) { model_ = model; for (auto& f : filters_) f.setModel(model); }
```

- Replace the `(void)cutoff;` line with:

```cpp
    for (int lane = 0; lane < 4; ++lane)
      if (voices_[q * 4 + lane].active) filters_[q].setLane(lane, cutoff[lane], value_[kFilterReso]);
    filters_[q].process(io_[q]);
    filters_[q].endBlock();
```

- A silent voice that starts must not inherit the previous note's ringing. In `Engine::startVoice`, before `v.start(...)`:

```cpp
  if (!v.active) {
    const int index = (int)(&v - voices_);
    filters_[index / 4].resetLane(index % 4);
  }
```

**Step 9: Engine tests for the filter** — append to `engine/tests/test_engine.cpp`:

```cpp
TEST(engine_closing_the_filter_removes_the_highs) {
  auto brightness = [](float cutoff) {
    Rig r;
    r.set(kVoiceDrift, 0.0f);
    r.set(kFilterCutoff, cutoff);
    r.set(kFilterKeytrack, 0.0f);
    r.play(0.05f);
    r.on(45);  // A2 saw, 110 Hz
    r.play(0.2f);
    auto x = r.play(1.0f);
    return probe::tone(x, 3300.0, 48000.0) / probe::tone(x, 110.0, 48000.0);  // 30th harmonic vs fundamental
  };
  CHECK(brightness(20000.0f) > 10.0 * brightness(400.0f));
}

TEST(engine_every_filter_model_makes_sound) {
  for (int model = 0; model < kNumFilterModels; ++model) {
    Rig r;
    r.set(kFilterModel, (float)model);
    r.set(kFilterCutoff, 2000.0f);
    r.play(0.05f);
    r.on(57);
    r.play(0.1f);
    auto x = r.play(0.5f);
    CHECK(probe::rms(x) > 0.02);
    CHECK(probe::finite(x));
  }
}

TEST(engine_filter_envelope_opens_the_filter) {
  auto level = [](float amount) {
    Rig r;
    r.set(kVoiceDrift, 0.0f);
    r.set(kFilterCutoff, 200.0f);
    r.set(kFilterKeytrack, 0.0f);
    r.set(kFilterEnv, amount);
    r.set(kFenvSustain, 1.0f);
    r.play(0.05f);
    r.on(57);
    r.play(0.3f);
    return probe::tone(r.play(1.0f), 2200.0, 48000.0);  // 10th harmonic of 220 Hz
  };
  CHECK(level(0.8f) > 5.0 * level(0.0f));
}
```

Add `#include "filter.h"` to the test file for `kNumFilterModels`.

**Step 10: Run everything** — `.\tools\test-engine.ps1`. All tests pass, including the earlier engine tests (they now run through the ladder filter fully open at 20 kHz; `engine_plays_a_note_at_concert_pitch` must still read 440 Hz).

**Step 11: Commit**

```powershell
git add .gitmodules third_party engine
git commit -m "feat(engine): Ladder, Roland and Prophet filter models from sst-filters"
```

Add to `docs/NOTES.md` under a heading `## Filtros (sst-filters)`: the three submodule commit hashes (`git submodule status`), what Step 2 found about `updateState`/`updateCoefficients` and the SIMD macros, and whether plan B was needed.

---

### Task 10: The audio thread must not allocate

**Files:**
- Create: `engine/tests/test_alloc.cpp`
- Modify: `engine/CMakeLists.txt`

**Step 1: The test**

```cpp
#include "engine.h"
#include "test.h"
#include <atomic>
#include <cstdlib>
#include <new>

// Counts every heap allocation in the test binary.
static std::atomic<long> g_allocs{0};
void* operator new(std::size_t n) { ++g_allocs; if (void* p = std::malloc(n ? n : 1)) return p; throw std::bad_alloc(); }
void* operator new[](std::size_t n) { ++g_allocs; if (void* p = std::malloc(n ? n : 1)) return p; throw std::bad_alloc(); }
void operator delete(void* p) noexcept { std::free(p); }
void operator delete[](void* p) noexcept { std::free(p); }
void operator delete(void* p, std::size_t) noexcept { std::free(p); }
void operator delete[](void* p, std::size_t) noexcept { std::free(p); }

using namespace volt;

TEST(alloc_render_never_touches_the_heap) {
  Engine e;
  e.setSampleRate(48000.0f);
  static float buf[2 * 512];
  Rng rng;
  e.render(buf, 512);  // warm-up
  long before = g_allocs.load();
  for (int round = 0; round < 400; ++round) {
    // post() is a non-audio call but must not allocate either.
    e.post({Event::SetParam, (int)(rng.next() % kNumParams), rng.unipolar()});
    e.post({(rng.next() & 1) ? Event::NoteOn : Event::NoteOff, 30 + (int)(rng.next() % 70), 0.7f});
    e.render(buf, 1 + (int)(rng.next() % 512));
  }
  CHECK(g_allocs.load() == before);
}
```

**Step 2: Run** `.\tools\test-engine.ps1 alloc`.

Expected: PASS. If it fails, something in `render` allocates. Find it by putting a breakpoint or an `abort()` inside `operator new` guarded by a flag set during the loop, read the call stack, and remove the allocation (most likely a filter-model change path inside sst-filters — if switching models allocates, move the switch out of the audio thread by pre-building one `QuadFilter` set per model and selecting between them).

**Step 3: Commit** — `test(engine): render does not allocate`.

---

### Task 11: Android project

**Files:**
- Create (copied from the reference, then edited): `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/cpp/CMakeLists.txt`, `app/src/main/cpp/jni.cpp`, `app/src/main/kotlin/com/criapix/volt/MainActivity.kt`, `app/src/main/kotlin/com/criapix/volt/Native.kt`

**Step 1: Copy the Gradle scaffolding**

```powershell
$ref = "C:\Users\criap\OneDrive\Documentos\Github\mvave fm1"
Copy-Item "$ref\gradlew", "$ref\gradlew.bat", "$ref\gradle.properties", "$ref\build.gradle.kts", "$ref\settings.gradle.kts" .
Copy-Item "$ref\gradle" . -Recurse
Copy-Item "$ref\local.properties" .   # git-ignored; holds sdk.dir
```

Then:
- `settings.gradle.kts`: set `rootProject.name = "Volt"`; keep `include(":app")`. Read the file; remove anything that mentions `fm1`.
- `local.properties`: keep only the `sdk.dir=` line (delete any `release.*` keystore lines; Volt has no release signing yet).
- `gradle/libs.versions.toml`: add under `[versions]` `oboe = "1.9.3"` and under `[libraries]` `oboe = { module = "com.google.oboe:oboe", version.ref = "oboe" }`. (If Gradle cannot resolve 1.9.3, look up the newest version on Maven Central / `google/oboe` releases and use it.)

**Step 2: `app/build.gradle.kts`**

```kotlin
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.criapix.volt"
  compileSdk = 36
  ndkVersion = "30.0.16248370"

  defaultConfig {
    applicationId = "com.criapix.volt"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
    ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared" } }
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  buildTypes {
    release { isMinifyEnabled = false }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    prefab = true      // Oboe ships as a prefab package
    buildConfig = false
  }
  packaging {
    resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
  }
}

kotlin { jvmToolchain(17) }

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.oboe)

  testImplementation(libs.junit)
}
```

If Gradle reports the CMake `3.22.1` package missing, let it install it (accept the licence) or change `version` to the CMake you installed in Task 1.

**Step 3: `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

  <uses-feature android:name="android.software.midi" android:required="false" />
  <uses-feature android:name="android.hardware.usb.host" android:required="false" />
  <uses-feature android:name="android.hardware.audio.low_latency" android:required="false" />

  <application
      android:label="Volt"
      android:allowBackup="true"
      android:supportsRtl="true"
      android:theme="@android:style/Theme.Material.NoActionBar">
    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:screenOrientation="sensorLandscape"
        android:configChanges="orientation|screenSize|screenLayout|keyboard|keyboardHidden|navigation|uiMode"
        android:launchMode="singleTask">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>
  </application>
</manifest>
```

No launcher icon yet (Android shows the default one); the icon belongs to the UI plan. Compare with the reference manifest (`"$ref\app\src\main\AndroidManifest.xml"`) and copy anything about USB device attach it has that this one lacks.

**Step 4: `app/src/main/cpp/CMakeLists.txt`**

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(volt LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../engine ${CMAKE_CURRENT_BINARY_DIR}/engine)
find_package(oboe REQUIRED CONFIG)

add_library(volt SHARED jni.cpp)
target_compile_options(volt PRIVATE -O2 -Wall)
target_link_libraries(volt PRIVATE volt_engine oboe::oboe log)
```

**Step 5: `app/src/main/cpp/jni.cpp`** (first version: just proves the library loads and the table crosses JNI)

```cpp
#include <jni.h>
#include <string>
#include "params.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_criapix_volt_Native_paramTable(JNIEnv* env, jobject) {
  // One line per parameter: name|min|max|default|curve   (curve: lin, exp, step)
  std::string s;
  for (int i = 0; i < volt::kNumParams; ++i) {
    const auto& p = volt::kParams[i];
    const char* curve = p.curve == volt::Curve::Exp ? "exp" : p.curve == volt::Curve::Step ? "step" : "lin";
    s += p.name; s += '|';
    s += std::to_string(p.min); s += '|';
    s += std::to_string(p.max); s += '|';
    s += std::to_string(p.def); s += '|';
    s += curve; s += '\n';
  }
  return env->NewStringUTF(s.c_str());
}
```

**Step 6: `Native.kt`**

```kotlin
package com.criapix.volt

/** The C++ engine. Every call is thread-safe and returns at once. */
object Native {
  init { System.loadLibrary("volt") }

  /** One line per parameter: `name|min|max|default|curve`. The line number is the parameter id. */
  external fun paramTable(): String
}
```

**Step 7: `MainActivity.kt`** (temporary body, replaced in Task 14)

```kotlin
package com.criapix.volt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val count = Native.paramTable().lines().count { it.isNotBlank() }
    setContent { Text("Volt: $count parâmetros") }
  }
}
```

**Step 8: Build and run on the emulator**

```powershell
. .\tools\env.ps1
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`, APK at `app\build\outputs\apk\debug\app-debug.apk`, containing `lib/arm64-v8a/libvolt.so` and `lib/x86_64/libvolt.so` (check with `tar -tf` on the APK or `Expand-Archive`).

Start the x86_64 emulator (list AVDs with `emulator -list-avds`; the reference project's notes use an `android-35/google_apis/x86_64` image), then:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.criapix.volt/.MainActivity
adb exec-out screencap -p > build\shot.png
```

Open `build\shot.png` (Read tool) and confirm it shows `Volt: N parâmetros` with N equal to the number of `P(` lines in `params.def`. Check `adb logcat -d -s AndroidRuntime` shows no crash.

**Step 9: Commit** — `feat(app): Android project that loads the engine`.

---

### Task 12: Oboe output and the JNI surface

**Files:**
- Create: `app/src/main/cpp/audio.h`, `app/src/main/cpp/audio.cpp`
- Modify: `app/src/main/cpp/jni.cpp`, `app/src/main/cpp/CMakeLists.txt` (add `audio.cpp`), `Native.kt`, `MainActivity.kt`

**Step 1: `audio.h`**

```cpp
#pragma once
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <oboe/Oboe.h>
#include "engine.h"

// Owns the engine and the output stream.
class Audio : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
 public:
  bool start();
  void stop();
  volt::Engine& engine() { return engine_; }
  std::string info();  // key=value lines for the settings screen

  oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* data, int32_t frames) override;
  void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

 private:
  bool open();

  volt::Engine engine_;
  std::mutex lock_;  // guards stream_ open/close; never taken in onAudioReady
  std::shared_ptr<oboe::AudioStream> stream_;
  std::unique_ptr<oboe::LatencyTuner> tuner_;
  std::atomic<bool> wanted_{false};
  std::atomic<float> load_{0.0f};  // fraction of the callback period spent rendering
  std::atomic<int> restarts_{0};
};
```

**Step 2: `audio.cpp`**

```cpp
#include "audio.h"

#include <ctime>
#include <android/log.h>

#if defined(__aarch64__)
static void flushDenormals() {
  uint64_t fpcr;
  __asm__ __volatile__("mrs %0, fpcr" : "=r"(fpcr));
  fpcr |= (1ull << 24);  // FZ: flush-to-zero
  __asm__ __volatile__("msr fpcr, %0" : : "r"(fpcr));
}
#elif defined(__x86_64__) || defined(__i386__)
#include <xmmintrin.h>
#include <pmmintrin.h>
static void flushDenormals() {
  _MM_SET_FLUSH_ZERO_MODE(_MM_FLUSH_ZERO_ON);
  _MM_SET_DENORMALS_ZERO_MODE(_MM_DENORMALS_ZERO_ON);
}
#else
static void flushDenormals() {}
#endif

static double nowSeconds() {
  timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (double)ts.tv_sec + (double)ts.tv_nsec * 1e-9;
}

bool Audio::open() {
  oboe::AudioStreamBuilder b;
  b.setDirection(oboe::Direction::Output)
      ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
      ->setSharingMode(oboe::SharingMode::Exclusive)
      ->setFormat(oboe::AudioFormat::Float)
      ->setChannelCount(oboe::ChannelCount::Stereo)
      ->setUsage(oboe::Usage::Game)  // the usage Android routes with the least processing
      ->setDataCallback(this)
      ->setErrorCallback(this);
  oboe::Result r = b.openStream(stream_);
  if (r != oboe::Result::OK) {
    __android_log_print(ANDROID_LOG_ERROR, "volt", "openStream: %s", oboe::convertToText(r));
    stream_.reset();
    return false;
  }
  engine_.setSampleRate((float)stream_->getSampleRate());
  // Start with the smallest buffer; the tuner grows it only if the device glitches.
  stream_->setBufferSizeInFrames(stream_->getFramesPerBurst());
  tuner_ = std::make_unique<oboe::LatencyTuner>(*stream_);
  r = stream_->requestStart();
  if (r != oboe::Result::OK) {
    __android_log_print(ANDROID_LOG_ERROR, "volt", "requestStart: %s", oboe::convertToText(r));
    stream_->close();
    stream_.reset();
    return false;
  }
  return true;
}

bool Audio::start() {
  std::lock_guard<std::mutex> g(lock_);
  wanted_ = true;
  if (stream_) return true;
  return open();
}

void Audio::stop() {
  std::lock_guard<std::mutex> g(lock_);
  wanted_ = false;
  if (stream_) { stream_->stop(); stream_->close(); stream_.reset(); }
  tuner_.reset();
}

oboe::DataCallbackResult Audio::onAudioReady(oboe::AudioStream* stream, void* data, int32_t frames) {
  flushDenormals();
  const double t0 = nowSeconds();
  engine_.render(static_cast<float*>(data), frames);
  if (tuner_) tuner_->tune();
  const double period = (double)frames / (double)stream->getSampleRate();
  const float used = (float)((nowSeconds() - t0) / period);
  load_.store(0.95f * load_.load(std::memory_order_relaxed) + 0.05f * used, std::memory_order_relaxed);
  return oboe::DataCallbackResult::Continue;
}

// Called on a separate thread after the stream died (headphones or the USB audio device changed).
void Audio::onErrorAfterClose(oboe::AudioStream*, oboe::Result error) {
  __android_log_print(ANDROID_LOG_WARN, "volt", "stream closed: %s", oboe::convertToText(error));
  std::lock_guard<std::mutex> g(lock_);
  stream_.reset();
  tuner_.reset();
  if (wanted_) { ++restarts_; open(); }
}

std::string Audio::info() {
  std::lock_guard<std::mutex> g(lock_);
  std::string s;
  auto add = [&](const char* k, const std::string& v) { s += k; s += '='; s += v; s += '\n'; };
  add("running", stream_ ? "1" : "0");
  if (stream_) {
    add("sampleRate", std::to_string(stream_->getSampleRate()));
    add("burst", std::to_string(stream_->getFramesPerBurst()));
    add("buffer", std::to_string(stream_->getBufferSizeInFrames()));
    add("lowLatency", stream_->getPerformanceMode() == oboe::PerformanceMode::LowLatency ? "1" : "0");
    add("exclusive", stream_->getSharingMode() == oboe::SharingMode::Exclusive ? "1" : "0");
    add("api", stream_->getAudioApi() == oboe::AudioApi::AAudio ? "AAudio" : "OpenSLES");
    auto x = stream_->getXRunCount();
    add("xruns", x ? std::to_string(x.value()) : "?");
    auto l = stream_->calculateLatencyMillis();
    add("latencyMs", l ? std::to_string(l.value()) : "?");
  }
  add("load", std::to_string(load_.load()));
  add("restarts", std::to_string(restarts_.load()));
  add("voices", std::to_string(engine_.activeVoices()));
  return s;
}
```

The `tuner_` pointer is read in the callback and reset under `lock_` only after the stream is stopped or already dead, so the callback is not running at that moment.

**Step 3: Replace `jni.cpp`**

```cpp
#include <jni.h>
#include <string>
#include "audio.h"
#include "params.h"

static Audio g_audio;
using volt::Event;

#define JNI(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_criapix_volt_Native_##name

JNI(jstring, paramTable)(JNIEnv* env, jobject) {
  std::string s;
  for (int i = 0; i < volt::kNumParams; ++i) {
    const auto& p = volt::kParams[i];
    const char* curve = p.curve == volt::Curve::Exp ? "exp" : p.curve == volt::Curve::Step ? "step" : "lin";
    s += p.name; s += '|';
    s += std::to_string(p.min); s += '|';
    s += std::to_string(p.max); s += '|';
    s += std::to_string(p.def); s += '|';
    s += curve; s += '\n';
  }
  return env->NewStringUTF(s.c_str());
}

JNI(jboolean, start)(JNIEnv*, jobject) { return g_audio.start() ? JNI_TRUE : JNI_FALSE; }
JNI(void, stop)(JNIEnv*, jobject) { g_audio.stop(); }
JNI(jstring, audioInfo)(JNIEnv* env, jobject) { return env->NewStringUTF(g_audio.info().c_str()); }
JNI(jfloat, peak)(JNIEnv*, jobject) { return g_audio.engine().peak(); }

JNI(void, noteOn)(JNIEnv*, jobject, jint note, jfloat velocity) { g_audio.engine().post({Event::NoteOn, note, velocity}); }
JNI(void, noteOff)(JNIEnv*, jobject, jint note) { g_audio.engine().post({Event::NoteOff, note, 0.0f}); }
JNI(void, setParam)(JNIEnv*, jobject, jint id, jfloat normalized) { g_audio.engine().post({Event::SetParam, id, normalized}); }
JNI(void, pitchBend)(JNIEnv*, jobject, jfloat v) { g_audio.engine().post({Event::PitchBend, 0, v}); }
JNI(void, modWheel)(JNIEnv*, jobject, jfloat v) { g_audio.engine().post({Event::ModWheel, 0, v}); }
JNI(void, sustain)(JNIEnv*, jobject, jboolean on) { g_audio.engine().post({Event::Sustain, 0, on ? 1.0f : 0.0f}); }
JNI(void, allNotesOff)(JNIEnv*, jobject) { g_audio.engine().post({Event::AllNotesOff, 0, 0.0f}); }
```

**Step 4: `Native.kt`**

```kotlin
package com.criapix.volt

/** The C++ engine. Every call is thread-safe and returns at once. */
object Native {
  init { System.loadLibrary("volt") }

  /** One line per parameter: `name|min|max|default|curve`. The line number is the parameter id. */
  external fun paramTable(): String

  external fun start(): Boolean
  external fun stop()
  /** `key=value` lines: running, sampleRate, burst, buffer, lowLatency, exclusive, api, xruns, latencyMs, load, restarts, voices. */
  external fun audioInfo(): String
  external fun peak(): Float

  external fun noteOn(note: Int, velocity: Float)   // velocity 0..1
  external fun noteOff(note: Int)
  external fun setParam(id: Int, normalized: Float) // 0..1
  external fun pitchBend(value: Float)              // -1..1
  external fun modWheel(value: Float)               // 0..1
  external fun sustain(on: Boolean)
  external fun allNotesOff()
}
```

**Step 5: `MainActivity.kt`** — start the audio with the activity and add a test button.

```kotlin
package com.criapix.volt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
  private var info by mutableStateOf("")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      Column {
        Button(onClick = { Native.noteOn(57, 0.8f); info = Native.audioInfo() }) { Text("Tocar") }
        Button(onClick = { Native.noteOff(57); info = Native.audioInfo() }) { Text("Soltar") }
        Text(info)
      }
    }
  }

  override fun onStart() { super.onStart(); Native.start(); info = Native.audioInfo() }
  override fun onStop() { Native.allNotesOff(); Native.stop(); super.onStop() }
}
```

**Step 6: Build, install, try**

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.criapix.volt/.MainActivity
adb shell input tap 150 80      # "Tocar" (adjust the coordinates from a screenshot)
adb exec-out screencap -p > build\shot.png
adb logcat -d -s volt AndroidRuntime
```

Expected on the emulator: the text shows `running=1`, a sample rate, `voices=1` after the tap; logcat has no `openStream` error and no crash. You cannot hear the emulator; that the stream is running and a voice is counted is the evidence available here. Say exactly that in your report — do not claim it "sounds right".

**Step 7: Commit** — `feat(app): low-latency Oboe output and JNI surface`.

---

### Task 13: MIDI input

**Files:**
- Create (copied): `app/src/main/kotlin/com/criapix/volt/midi/MidiInput.kt`, `midi/MidiParser.kt`, `app/src/test/kotlin/com/criapix/volt/midi/MidiParserTest.kt`
- Create: `app/src/main/kotlin/com/criapix/volt/Params.kt`, `midi/MidiDispatch.kt`
- Create: `app/src/test/kotlin/com/criapix/volt/ParamsTest.kt`, `midi/MidiDispatchTest.kt`

**Step 1: Copy the parser and the input from the reference**

```powershell
$ref = "C:\Users\criap\OneDrive\Documentos\Github\mvave fm1\app\src"
$dst = "app\src\main\kotlin\com\criapix\volt\midi"
New-Item -ItemType Directory -Force $dst, "app\src\test\kotlin\com\criapix\volt\midi" | Out-Null
Copy-Item "$ref\main\kotlin\com\criapix\fm1droid\midi\MidiInput.kt", "$ref\main\kotlin\com\criapix\fm1droid\midi\MidiParser.kt" $dst
```

In both files change `package com.criapix.fm1droid.midi` to `package com.criapix.volt.midi`. Change nothing else. Find the reference's parser test (`Get-ChildItem "$ref\test" -Recurse -Filter "MidiParser*"`), copy it to `app\src\test\kotlin\com\criapix\volt\midi\`, and fix its package line.

What they do: `MidiInput(context, sink)` opens every MIDI device that sends, as devices come and go, and calls `sink(bytes, offset, count)` on a binder thread. `MidiParser(out)` turns that byte stream into whole messages `(buffer, length)`, expanding running status. `MidiInput.count` is a `StateFlow<Int>` of connected devices.

**Step 2: Failing test — `ParamsTest.kt`**

```kotlin
package com.criapix.volt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParamsTest {
  private val table = "osc1.wave|0.000000|3.000000|0.000000|step\n" +
      "filter.cutoff|20.000000|20000.000000|20000.000000|exp\n" +
      "filter.reso|0.000000|1.000000|0.100000|lin\n"

  @Test fun parsesEveryLineInOrder() {
    val p = Params.parse(table)
    assertEquals(3, p.all.size)
    assertEquals("filter.cutoff", p.all[1].name)
    assertEquals(1, p.all[1].id)
    assertEquals(Curve.EXP, p.all[1].curve)
    assertEquals(20000f, p.all[1].max, 0f)
  }

  @Test fun findsAParameterByName() {
    val p = Params.parse(table)
    assertEquals(2, p["filter.reso"]?.id)
    assertNull(p["nope"])
  }

  @Test fun convertsBetweenNormalizedAndRealValues() {
    val p = Params.parse(table)
    val cutoff = p["filter.cutoff"]!!
    assertEquals(632.46f, cutoff.toReal(0.5f), 0.5f)       // geometric middle of 20..20000
    assertEquals(0.5f, cutoff.toNorm(632.46f), 0.001f)
    assertEquals(1f, cutoff.toNorm(cutoff.def), 0f)
    assertEquals(1f, p["osc1.wave"]!!.toReal(0.4f), 0f)    // steps are whole numbers
    assertEquals(0.1f, p["filter.reso"]!!.toNorm(0.1f), 0.0001f)
  }
}
```

**Step 3: Run** `.\gradlew.bat :app:testDebugUnitTest` — expected: compile error, `Params` unresolved.

**Step 4: `Params.kt`**

```kotlin
package com.criapix.volt

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Curve { LIN, EXP, STEP }

/** One engine parameter, as described by the engine's own table. */
data class Param(val id: Int, val name: String, val min: Float, val max: Float, val def: Float, val curve: Curve) {
  fun toReal(norm: Float): Float {
    val n = norm.coerceIn(0f, 1f)
    return when (curve) {
      Curve.EXP -> min * (max / min).pow(n)
      Curve.STEP -> (min + (max - min) * n).roundToInt().toFloat()
      Curve.LIN -> min + (max - min) * n
    }
  }

  fun toNorm(real: Float): Float {
    val v = real.coerceIn(min, max)
    return if (curve == Curve.EXP) ln(v / min) / ln(max / min) else (v - min) / (max - min)
  }
}

class Params private constructor(val all: List<Param>) {
  private val byName = all.associateBy { it.name }
  operator fun get(name: String): Param? = byName[name]

  companion object {
    /** Parses the text of [Native.paramTable]: one `name|min|max|default|curve` line per parameter. */
    fun parse(table: String): Params = Params(
      table.lineSequence().filter { it.isNotBlank() }.mapIndexed { id, line ->
        val f = line.split('|')
        Param(id, f[0], f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), Curve.valueOf(f[4].uppercase()))
      }.toList()
    )
  }
}
```

**Step 5: Run** the unit tests — `ParamsTest` passes.

**Step 6: Failing test — `midi/MidiDispatchTest.kt`**

```kotlin
package com.criapix.volt.midi

import com.criapix.volt.Params
import org.junit.Assert.assertEquals
import org.junit.Test

class MidiDispatchTest {
  private val log = ArrayList<String>()
  private val synth = object : Synth {
    override fun noteOn(note: Int, velocity: Float) { log += "on $note ${"%.2f".format(velocity)}" }
    override fun noteOff(note: Int) { log += "off $note" }
    override fun setParam(id: Int, normalized: Float) { log += "param $id ${"%.2f".format(normalized)}" }
    override fun pitchBend(value: Float) { log += "bend ${"%.2f".format(value)}" }
    override fun modWheel(value: Float) { log += "wheel ${"%.2f".format(value)}" }
    override fun sustain(on: Boolean) { log += "sustain $on" }
    override fun allNotesOff() { log += "panic" }
  }
  private val params = Params.parse(
    "filter.cutoff|20|20000|20000|exp\nfilter.reso|0|1|0.1|lin\nmaster.volume|0|1|0.7|lin\n"
  )
  private val d = MidiDispatch(synth, params)
  private fun send(vararg b: Int) { val a = ByteArray(b.size) { b[it].toByte() }; d.message(a, a.size) }

  @Test fun notesCarryVelocity() {
    send(0x90, 60, 127); send(0x80, 60, 0)
    assertEquals(listOf("on 60 1.00", "off 60"), log)
  }

  @Test fun noteOnWithZeroVelocityIsANoteOff() {
    send(0x90, 60, 0)
    assertEquals(listOf("off 60"), log)
  }

  @Test fun pitchBendIsCentredAtZero() {
    send(0xE0, 0, 64); send(0xE0, 127, 127); send(0xE0, 0, 0)
    assertEquals(listOf("bend 0.00", "bend 1.00", "bend -1.00"), log)
  }

  @Test fun theStandardControllersWorkOutOfTheBox() {
    send(0xB0, 1, 127); send(0xB0, 64, 127); send(0xB0, 64, 0)
    send(0xB0, 74, 0); send(0xB0, 71, 127); send(0xB0, 7, 127)
    assertEquals(
      listOf("wheel 1.00", "sustain true", "sustain false", "param 0 0.00", "param 1 1.00", "param 2 1.00"), log
    )
  }

  @Test fun allSoundOffAndAllNotesOffPanic() {
    send(0xB0, 120, 0); send(0xB0, 123, 0)
    assertEquals(listOf("panic", "panic"), log)
  }

  @Test fun omniListensToEveryChannelAndAFixedChannelOnlyToItself() {
    send(0x95, 60, 100)
    assertEquals(1, log.size)
    d.channel = 2
    send(0x95, 61, 100)
    assertEquals(1, log.size)
    send(0x92, 62, 100)
    assertEquals(2, log.size)
  }

  @Test fun unknownAndShortMessagesAreIgnored() {
    send(0xF8); send(0xB0, 99, 5); send(0xD0, 40); send(0xC0, 3)
    assertEquals(emptyList<String>(), log)
  }

  @Test fun reportsTheLastMessageForTheMonitor() {
    send(0xB3, 74, 10)
    assertEquals("CC 74 · canal 4 = 10", d.lastMessage.value)
    send(0x90, 60, 100)
    assertEquals("Nota 60 · canal 1 = 100", d.lastMessage.value)
  }
}
```

**Step 7: `midi/MidiDispatch.kt`**

```kotlin
package com.criapix.volt.midi

import com.criapix.volt.Params
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the MIDI layer needs from the synth. [com.criapix.volt.Native] satisfies it through an adapter. */
interface Synth {
  fun noteOn(note: Int, velocity: Float)
  fun noteOff(note: Int)
  fun setParam(id: Int, normalized: Float)
  fun pitchBend(value: Float)
  fun modWheel(value: Float)
  fun sustain(on: Boolean)
  fun allNotesOff()
}

/**
 * Turns whole MIDI messages into synth calls, with the factory controller map (design section 6.2).
 * MIDI Learn and Program Change arrive in a later plan and will sit in front of this map.
 */
class MidiDispatch(private val synth: Synth, params: Params) {
  /** -1 = omni, else 0..15. */
  @Volatile var channel = -1

  private val _last = MutableStateFlow("")
  /** Human-readable last note or controller, for the "última mensagem recebida" monitor. */
  val lastMessage: StateFlow<String> = _last

  private val cutoff = params["filter.cutoff"]?.id ?: -1
  private val reso = params["filter.reso"]?.id ?: -1
  private val volume = params["master.volume"]?.id ?: -1

  fun message(m: ByteArray, len: Int) {
    if (len < 1) return
    val status = m[0].toInt() and 0xFF
    if (status < 0x80 || status >= 0xF0) return
    val ch = status and 0x0F
    if (channel >= 0 && ch != channel) return
    val d1 = if (len > 1) m[1].toInt() and 0x7F else 0
    val d2 = if (len > 2) m[2].toInt() and 0x7F else 0
    when (status and 0xF0) {
      0x90 -> if (len >= 3) {
        _last.value = "Nota $d1 · canal ${ch + 1} = $d2"
        if (d2 == 0) synth.noteOff(d1) else synth.noteOn(d1, d2 / 127f)
      }
      0x80 -> if (len >= 3) synth.noteOff(d1)
      0xE0 -> if (len >= 3) {
        val v = (d2 shl 7) or d1  // 0..16383, centre 8192
        synth.pitchBend(if (v >= 8192) (v - 8192) / 8191f else (v - 8192) / 8192f)
      }
      0xB0 -> if (len >= 3) {
        _last.value = "CC $d1 · canal ${ch + 1} = $d2"
        controller(d1, d2)
      }
    }
  }

  private fun controller(cc: Int, value: Int) {
    val n = value / 127f
    when (cc) {
      1 -> synth.modWheel(n)
      7 -> if (volume >= 0) synth.setParam(volume, n)
      64 -> synth.sustain(value >= 64)
      71 -> if (reso >= 0) synth.setParam(reso, n)
      74 -> if (cutoff >= 0) synth.setParam(cutoff, n)
      120, 123 -> synth.allNotesOff()
    }
  }
}
```

The unit tests run on the JVM, where `kotlinx.coroutines` must be on the test classpath: add `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")` to `app/build.gradle.kts` if `StateFlow` does not resolve (the reference gets it transitively through lifecycle).

**Step 8: Run** `.\gradlew.bat :app:testDebugUnitTest` — all Kotlin tests pass (`ParamsTest`, `MidiDispatchTest`, the copied `MidiParserTest`).

**Step 9: Commit** — `feat(app): USB MIDI input with the factory controller map`.

---

### Task 14: Debug screen

A deliberately plain screen: the audio status and one slider per parameter, generated from the parameter table. It exists so the user can play the CK61, try every parameter and measure latency before the real UI is designed. It is replaced by the touch UI plan, so do not polish it.

**Files:**
- Create: `app/src/main/kotlin/com/criapix/volt/DebugScreen.kt`
- Modify: `app/src/main/kotlin/com/criapix/volt/MainActivity.kt`

**Step 1: `DebugScreen.kt`**

```kotlin
package com.criapix.volt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Labels for the `key=value` lines of [Native.audioInfo], in the order they are shown. */
private val INFO_LABELS = listOf(
  "sampleRate" to "Taxa (Hz)", "burst" to "Bloco (quadros)", "buffer" to "Buffer (quadros)",
  "lowLatency" to "Baixa latência", "exclusive" to "Exclusivo", "api" to "API",
  "latencyMs" to "Latência de saída (ms)", "xruns" to "Estouros", "load" to "Carga do motor",
  "restarts" to "Reinícios", "voices" to "Vozes",
)

fun formatInfo(info: String): String {
  val map = info.lineSequence().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
  if (map["running"] != "1") return "Áudio parado"
  return INFO_LABELS.joinToString("   ") { (key, label) ->
    val v = map[key] ?: "?"
    val shown = when (key) {
      "lowLatency", "exclusive" -> if (v == "1") "sim" else "NÃO"
      "load" -> v.toFloatOrNull()?.let { "${(it * 100).toInt()} %" } ?: v
      "latencyMs" -> v.toFloatOrNull()?.let { "%.1f".format(it) } ?: v
      else -> v
    }
    "$label: $shown"
  }
}

@Composable
fun DebugScreen(
  params: Params,
  info: String,
  midiDevices: Int,
  lastMidi: String,
  onParam: (Param, Float) -> Unit,
  onTestNote: (Boolean) -> Unit,
  onPanic: () -> Unit,
) {
  MaterialTheme(colorScheme = darkColorScheme()) {
    Surface(Modifier.fillMaxSize()) {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Volt", style = MaterialTheme.typography.titleLarge)
          Text(if (midiDevices > 0) "MIDI: $midiDevices conectado(s)" else "MIDI: nenhum teclado")
          Text(lastMidi)
          Button(onClick = { onTestNote(true) }) { Text("Tocar Lá") }
          Button(onClick = { onTestNote(false) }) { Text("Soltar") }
          Button(onClick = onPanic) { Text("Pânico") }
        }
        Text(formatInfo(info), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        LazyColumn(Modifier.fillMaxWidth()) {
          items(params.all, key = { it.id }) { p -> ParamRow(p, onParam) }
        }
      }
    }
  }
}

@Composable
private fun ParamRow(p: Param, onParam: (Param, Float) -> Unit) {
  var norm by remember(p.id) { mutableFloatStateOf(p.toNorm(p.def)) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(p.name, Modifier.width(170.dp), fontSize = 13.sp)
    Slider(
      value = norm,
      onValueChange = { norm = it; onParam(p, it) },
      steps = if (p.curve == Curve.STEP) (p.max - p.min).toInt() - 1 else 0,
      modifier = Modifier.weight(1f),
    )
    Text("%.3f".format(p.toReal(norm)), Modifier.width(90.dp), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
  }
}
```

Known limit, acceptable for a debug screen: a slider does not move when a MIDI controller changes its parameter (CC 74 moves the cutoff but not its slider). The real UI fixes it.

**Step 2: `MainActivity.kt`**

```kotlin
package com.criapix.volt

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.criapix.volt.midi.MidiDispatch
import com.criapix.volt.midi.MidiInput
import com.criapix.volt.midi.MidiParser
import com.criapix.volt.midi.Synth
import kotlinx.coroutines.delay

private object NativeSynth : Synth {
  override fun noteOn(note: Int, velocity: Float) = Native.noteOn(note, velocity)
  override fun noteOff(note: Int) = Native.noteOff(note)
  override fun setParam(id: Int, normalized: Float) = Native.setParam(id, normalized)
  override fun pitchBend(value: Float) = Native.pitchBend(value)
  override fun modWheel(value: Float) = Native.modWheel(value)
  override fun sustain(on: Boolean) = Native.sustain(on)
  override fun allNotesOff() = Native.allNotesOff()
}

class MainActivity : ComponentActivity() {
  private lateinit var params: Params
  private lateinit var dispatch: MidiDispatch
  private lateinit var midi: MidiInput

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    params = Params.parse(Native.paramTable())
    dispatch = MidiDispatch(NativeSynth, params)
    // The parser is fed from one binder thread at a time; MidiReceiver serialises its calls.
    val parser = MidiParser { bytes, len -> dispatch.message(bytes, len) }
    midi = MidiInput(this) { bytes, offset, count -> parser.feed(bytes, offset, count) }

    setContent {
      val devices by midi.count.collectAsStateWithLifecycle()
      val last by dispatch.lastMessage.collectAsStateWithLifecycle()
      var info by remember { mutableStateOf("") }
      LaunchedEffect(Unit) { while (true) { info = Native.audioInfo(); delay(500) } }
      DebugScreen(
        params = params, info = info, midiDevices = devices, lastMidi = last,
        onParam = { p, n -> Native.setParam(p.id, n) },
        onTestNote = { on -> if (on) Native.noteOn(69, 0.8f) else Native.noteOff(69) },
        onPanic = { Native.allNotesOff() },
      )
    }
  }

  override fun onStart() { super.onStart(); Native.start(); midi.start() }

  override fun onStop() {
    midi.stop()
    Native.allNotesOff()
    Native.stop()
    super.onStop()
  }
}
```

Check against the reference's `MidiInput` usage (`"C:\Users\criap\OneDrive\Documentos\Github\mvave fm1\app\src\main\kotlin\com\criapix\fm1droid\MainActivity.kt"`) whether the device-removed path should also call `Native.allNotesOff()`; the design (section 6.1) requires that unplugging the keyboard releases every note. If `MidiInput` gives no hook, add an `onDisconnect: () -> Unit` constructor parameter called from its `disconnect()` and pass `{ Native.allNotesOff() }`.

**Step 3: Build, install, look**

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.criapix.volt/.MainActivity
adb exec-out screencap -p > build\shot.png
```

Read `build\shot.png`. Confirm: landscape; the header row; the info line shows a sample rate; sliders listed with names `osc1.wave`, `osc1.octave`, …; the list scrolls (`adb shell input swipe 800 600 800 200`, screenshot again). Tap "Tocar Lá" and confirm `Vozes: 1` appears within a second. No crash in `adb logcat -d -s AndroidRuntime volt`.

**Step 4: Commit** — `feat(app): debug screen with every parameter and the audio status`.

---

### Task 15: CPU load check

The design's budget is 16 voices under ~30 % of one core on the S25 Ultra. The phone number is the user's to read (Task 16's checklist), but a PC benchmark catches a gross problem now and gives a baseline.

**Files:**
- Create: `engine/tests/bench.cpp`
- Modify: `engine/CMakeLists.txt`

**Step 1: `engine/tests/bench.cpp`**

```cpp
// Renders 16 held voices for 10 seconds of audio and prints how much faster than real time it ran.
#include <chrono>
#include <cstdio>
#include "engine.h"

using namespace volt;

int main() {
  for (int model = 0; model < 4; ++model) {
    Engine e;
    e.setSampleRate(48000.0f);
    e.post({Event::SetParam, kFilterModel, norm(kFilterModel, (float)model)});
    e.post({Event::SetParam, kOsc2Level, 1.0f});
    e.post({Event::SetParam, kOsc3Level, 1.0f});
    e.post({Event::SetParam, kFilterCutoff, norm(kFilterCutoff, 2000.0f)});
    for (int n = 0; n < 16; ++n) e.post({Event::NoteOn, 36 + 3 * n, 0.8f});
    static float buf[2 * 96];
    auto t0 = std::chrono::steady_clock::now();
    for (int i = 0; i < 5000; ++i) e.render(buf, 96);  // 10 s
    double sec = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
    std::printf("model %d: %d voices, %.1fx real time, %.1f %% of one core\n", model, e.activeVoices(), 10.0 / sec, 100.0 * sec / 10.0);
  }
  return 0;
}
```

In `engine/CMakeLists.txt`, inside `if (VOLT_TESTS)`:

```cmake
  add_executable(volt_bench tests/bench.cpp)
  target_link_libraries(volt_bench PRIVATE volt_engine)
```

**Step 2: Run** `.\tools\test-engine.ps1` (builds everything), then `.\build\engine\volt_bench.exe`.

Expected: four lines, `16 voices`, each well above 10× real time on a desktop CPU. Write the four lines into `docs/NOTES.md` under `## Desempenho`, stating they are **PC** numbers.

If a model is below ~10× real time on the PC, it will probably not meet the phone budget. For the Ladder model, switch to the fallback in Task 9's table (`fut_lpmoog` / `st_lpmoog_24dB`), re-run the filter tests, and note the change.

**Step 3: Commit** — `test(engine): CPU benchmark`.

---

### Task 16: Notes, device checklist, README

**Files:**
- Modify: `docs/NOTES.md`, `README.md`
- Create: `docs/DEVICE-TEST.md`

**Step 1: `docs/NOTES.md`** — add these sections (Portuguese), filled with what actually happened:

```markdown
## Estado (plano 1: motor e app tocável)
- O que existe: motor C++ (16 vozes, 3 osciladores, filtro Ladder/Roland 12/Roland 24/Prophet, 2 envelopes, 2 LFOs, poly-mod, poly/mono/legato/unison), saída Oboe de baixa latência, entrada MIDI USB com o mapa de fábrica, tela de depuração com um controle por parâmetro.
- O que não existe ainda: a tela de toque com abas, presets, MIDI Learn, Program Change, efeitos, arpejador, teclado na tela, banco de fábrica.
- Verificado por: <N> testes do motor no PC (`.\tools\test-engine.ps1`), <N> testes Kotlin (`gradlew :app:testDebugUnitTest`), e a tela no emulador x86_64. **Nada foi ouvido nem medido no celular.**

## Diferenças em relação ao design
- Oscilador 3 como fonte de modulação: não feito (os dois LFOs e o poly-mod cobrem).
- Poly-mod do oscilador 2 para o cutoff: não feito (os coeficientes do filtro são atualizados a cada 16 amostras).
- Hard sync sem anti-aliasing no reinício.
- <qualquer outra, por exemplo plano B dos filtros ou troca do modelo Ladder>

## Como ler o diagnóstico de áudio
- "Baixa latência: NÃO" ou "Latência de saída" acima de ~30 ms só com o CK61 como saída de som: o atraso está no caminho de áudio USB do Android; o app não consegue reduzi-lo pelo AAudio. Anotar os números e levar ao planejamento.
- "Estouros" subindo sem parar ou "Carga do motor" acima de 50 %: o motor não está acompanhando; anotar o modelo de filtro e o número de vozes.
```

Keep the `## Filtros (sst-filters)` and `## Desempenho` sections written earlier.

**Step 2: `docs/DEVICE-TEST.md`** — the checklist only the user can run.

```markdown
# Teste no aparelho (plano 1)

Instalar: `adb install -r app\build\outputs\apk\debug\app-debug.apk`. Anotar os resultados em `docs/NOTES.md`.

## Som e latência pelo alto-falante do celular
| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 1 | Abrir o Volt e tocar em "Tocar Lá" | Soa um Lá contínuo; "Soltar" silencia em menos de 1 s | |
| 2 | Ler a linha de diagnóstico | Taxa 48000, "Baixa latência: sim"; anotar Bloco, Buffer, Latência de saída, Estouros | |
| 3 | Mover `filter.cutoff` e `filter.reso` com a nota soando | O timbre abre e fecha sem estalos nem "degraus" | |
| 4 | Trocar `filter.model` entre 0, 1, 2 e 3 | Os quatro soam; anotar se algum estala na troca | |

## Com o Yamaha CK61 (cabo USB-B → USB-C na porta USB TO HOST)
| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 5 | Plugar o CK61 | "MIDI: 1 conectado(s)" | |
| 6 | Tocar teclas, forte e fraco | Notas com dinâmica; acordes de até 16 notas | |
| 7 | Sensação de atraso entre tecla e som (som pelo celular) | Anotar: imperceptível / perceptível / incomoda | |
| 8 | Roda de pitch bend | Sobe e desce 2 semitons | |
| 9 | Pedal de sustain | Segura as notas | |
| 10 | Mexer em cada controle do painel do CK61 e ler "última mensagem" no topo | Anotar em NOTES.md o que cada controle envia (CC e canal) ou "nada" | |
| 11 | Desplugar o CK61 com notas soando | As notas param | |

## Com o CK61 também como saída de som
| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 12 | Som saindo pelo CK61; ler o diagnóstico | Anotar Baixa latência (sim/NÃO), Bloco, Buffer, Latência de saída, Reinícios | |
| 13 | Sensação de atraso | Anotar como no passo 7 | |

## Carga
| # | Passo | Esperado | Resultado |
|---|---|---|---|
| 14 | `osc2.level` e `osc3.level` em 1, segurar 16 notas (pedal), para cada `filter.model` | Anotar "Carga do motor" e se há estouros; meta: abaixo de 30 % | |
| 15 | `voice.mode` em 1 (mono), 2 (legato) e 3 (unison) | Uma nota por vez; em unison o som engrossa | |
```

**Step 3: `README.md`** — expand to: what Volt is; current state (one paragraph, from NOTES); how to build (`. .\tools\env.ps1`, `.\tools\test-engine.ps1`, `.\gradlew.bat :app:assembleDebug`, cloning needs `git submodule update --init`); how to connect a MIDI keyboard; licence GPL-3.0-or-later and the three third-party libraries with their licences (sst-filters GPL-3, sst-basic-blocks GPL-3, simde MIT — confirm each from its `LICENSE` file before writing).

**Step 4: Full verification before the last commit**

```powershell
.\tools\test-engine.ps1
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
git status
```

All engine tests pass, all Kotlin tests pass, the APK builds, and `git status` shows no untracked build output.

**Step 5: Commit** — `docs: state, device checklist and README for plan 1`.

**Step 6: Final report to the user** (Portuguese). Say plainly: what was built; the test counts you saw pass; that nothing was heard or measured on the phone; the three deviations from the design; anything from Task 9 that needed plan B; and that the next step is theirs — run `docs/DEVICE-TEST.md` on the S25 Ultra with the CK61 and bring the numbers back.

---

## 99. What comes after this plan

Written as separate plans once this one is on the phone, because they build on the parameter table and JNI surface this plan produces, and because the device results (latency over USB audio, CPU load per filter model, which CK61 controls send MIDI) change their details.

| Plan | Design sections | Contents |
|---|---|---|
| 2 — Touch UI and presets | 5, 7 | Top bar, tabs OSC/FILTRO/ENV/MOD, knob and selector components, live filter and envelope drawings, engine → UI parameter feedback, preset format/storage/browser, `LoadPatch` event with a click-free switch |
| 3 — MIDI Learn | 6.3 | Learn mode, soft takeover, Program Change, preset previous/next actions, mapping screen (base: the reference's `MidiRouter`, `MidiMap`, `MidiMapSheet`) |
| 4 — Effects, arpeggiator, keyboard | 4.3, 4.4, 5.4 | Distortion, phaser, flanger, chorus, delay, reverb (sst-effects where practical), arpeggiator, on-screen keyboard drawer, ARP and FX tabs |
| 5 — Factory bank and settings | 7, 8 | ~64 factory presets with a render test per preset, settings/diagnostics screen, polyphony reduction under overload, app icon, release build |
