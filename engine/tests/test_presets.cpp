#include <cmath>
#include <fstream>
#include <iterator>
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

namespace {
constexpr bool kCheckFmLoudness = true;  // on since kFmGain was calibrated (plan 6 task 7)
constexpr double kFmLoudTarget = kLoudTarget;  // same target as the analog bank
// Measured after calibration: loudest voice -8.8 dB, so -7.0 leaves ~1.8 dB and still catches a regression.
constexpr double kFmLoudCeiling = -7.0;  // dBFS
// "It sounds" floor. The plan's -60 dB, moved down by the 6.9 dB the calibration (kFmGain 0.45) took off:
// the quietest voice (TAKE OFF, a slow rising jet whose pitch envelope falls) is -61.4 dB.
constexpr double kFmSoundFloor = -67.0;  // dBFS
}  // namespace

TEST(presets_every_dx7_factory_voice_renders_cleanly) {
  std::vector<std::filesystem::path> carts;
  for (auto& e : std::filesystem::directory_iterator(VOLT_DX7_DIR))
    if (e.path().extension() == ".syx") carts.push_back(e.path());
  std::sort(carts.begin(), carts.end());
  CHECK(carts.size() == 8);
  std::vector<double> loud;
  int holding = 0;
  for (const auto& path : carts) {
    std::ifstream in(path, std::ios::binary);
    std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    Dx7Bank bank;
    const bool readOk = readDx7Bank(bytes, bank) == Dx7Status::Ok;
    CHECK(readOk);
    if (!readOk) continue;
    for (int i = 0; i < 32; ++i) {
      Engine eng;
      eng.setSampleRate(48000.0f);
      applyDx7Voice(eng, bank.voices[i]);
      PresetFile p;
      p.category = "Keys";  // a chord phrase plus single notes
      auto r = renderPhrase(eng, phraseFor(p), 2.0, 20.0);
      const double l = loudnessDb(r);
      loud.push_back(l);
      if (r.releaseSeconds < 0) ++holding;
      const bool finite = probe::finite(r.left) && probe::finite(r.right);
      bool ok = finite && l > kFmSoundFloor && limitedShare(r) < 0.01;
      if (kCheckFmLoudness) ok = ok && l <= kFmLoudCeiling;
      if (!ok) {
        std::printf("    FAIL %s %02d %s: finite=%d loud=%.1f dB squash=%.2f %% release=%.2f s\n",
                    path.stem().string().c_str(), i + 1, dx7Name(bank.voices[i].data()).c_str(),
                    (int)finite, l, 100.0 * limitedShare(r), r.releaseSeconds);
        ++t::failures();
      }
    }
  }
  CHECK(loud.size() == 256);
  if (loud.empty()) return;
  std::sort(loud.begin(), loud.end());
  std::printf("    DX7 loudness: min %.1f, median %.1f, max %.1f dB (%zu voices)\n",
              loud.front(), loud[loud.size() / 2], loud.back(), loud.size());
  std::printf("    DX7 voices that never release within the phrase tail: %d of %zu; target median %.1f dB\n",
              holding, loud.size(), kFmLoudTarget);
}
