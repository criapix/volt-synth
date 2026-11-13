#include <algorithm>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>
#include "fm/dx7_codec.h"
#include "fm/fm_params.h"
#include "params.h"
#include "dx7_fixtures.h"
#include "test.h"

using namespace volt;

namespace {
std::vector<uint8_t> readFile(const std::string& p) {
  std::ifstream in(p, std::ios::binary);
  return std::vector<uint8_t>(std::istreambuf_iterator<char>(in), {});
}
}  // namespace

TEST(fm_params_defaults_are_the_init_voice) {
  float v[kNumParams];
  for (int i = 0; i < kNumParams; ++i) v[i] = kParams[i].def;
  Dx7Voice p{};
  vcedFromValues(v, p);
  CHECK(p[105 + 16] == 99);   // OP1 level
  CHECK(p[16] == 0);          // OP6 level
  CHECK(p[105 + 20] == 7);    // detune centre
  CHECK(p[134] == 0);         // algorithm 1
  CHECK(p[144] == 24);        // transpose C3
  CHECK(p[130] == 50);        // pitch EG L1
  CHECK(opSwitchFromValues(v) == 0x3F);
}

TEST(fm_params_offsets_and_operator_order) {
  DX7_REQUIRE("rom1a");
  // Known values of rom1a voice 1 (BRASS 1): algorithm byte 21, transpose byte 24, OP6 L1 = 98,
  // OP6 output level 82, OP1 output level 98, OP1 detune byte 14.
  Dx7Bank bank;
  CHECK(readDx7Bank(readFile(std::string(VOLT_DX7_DIR) + "/rom1a.syx"), bank) == Dx7Status::Ok);
  float v[kNumParams];
  for (int i = 0; i < kNumParams; ++i) v[i] = kParams[i].def;
  valuesFromVced(bank.voices[0], v);
  CHECK(v[kFmAlgorithm] == 22.0f);
  CHECK(v[kFmTranspose] == 0.0f);
  CHECK(v[kFmOp6L1] == 98.0f);
  CHECK(v[kFmOp6Level] == 82.0f);
  CHECK(v[kFmOp1Level] == 98.0f);
  CHECK(v[kFmOp1Detune] == 7.0f);
  CHECK(v[kFmOp6R1] == 49.0f);
}

TEST(fm_params_round_trip_all_256_factory_voices) {
  DX7_REQUIRE_ANY();
  int files = 0, voices = 0;
  for (auto& path : dx7fx::dx7Cartridges()) {
    ++files;
    Dx7Bank bank;
    readDx7Bank(readFile(path), bank);
    for (auto& voice : bank.voices) {
      ++voices;
      float v[kNumParams];
      for (int i = 0; i < kNumParams; ++i) v[i] = kParams[i].def;
      valuesFromVced(voice, v);
      // Through the normalized form the app and the queue use.
      for (int i = 0; i < kNumParams; ++i) v[i] = denorm(i, norm(i, v[i]));
      Dx7Voice back{};
      vcedFromValues(v, back);
      CHECK(std::memcmp(back.data(), voice.data(), 145) == 0);  // name is not a parameter
      CHECK(v[kEngineType] == 1.0f);
    }
  }
  CHECK(files >= 1);
  CHECK(voices == 32 * files);  // 8 files / 256 voices with the full factory set
}
