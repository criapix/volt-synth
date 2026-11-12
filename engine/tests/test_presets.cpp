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

namespace {
// Task 4 turns the loudness window on and fills the target from the measured median.
constexpr bool kCheckLoudness = true;
constexpr double kLoudTarget = -23.0; // dBFS
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
    // Init is the engine's default state on purpose, so its level is not balanced.
    if (kCheckLoudness && p.file != "001-init") {
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
