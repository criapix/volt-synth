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
