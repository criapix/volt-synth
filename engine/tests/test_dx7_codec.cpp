#include <algorithm>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>
#include "fm/dx7_codec.h"
#include "test.h"

using namespace volt;

namespace {
std::vector<uint8_t> readFile(const std::string& path) {
  std::ifstream in(path, std::ios::binary);
  return std::vector<uint8_t>(std::istreambuf_iterator<char>(in), {});
}
std::vector<std::string> cartridges() {
  std::vector<std::string> out;
  for (auto& e : std::filesystem::directory_iterator(VOLT_DX7_DIR))
    if (e.path().extension() == ".syx") out.push_back(e.path().string());
  std::sort(out.begin(), out.end());
  return out;
}
}  // namespace

TEST(dx7_codec_reads_the_eight_factory_cartridges) {
  auto files = cartridges();
  CHECK(files.size() == 8);
  for (auto& f : files) {
    Dx7Bank bank;
    const Dx7Status s = readDx7Bank(readFile(f), bank);
    CHECK(s == Dx7Status::Ok);
  }
}

TEST(dx7_codec_unpacks_known_values) {
  Dx7Bank bank;
  CHECK(readDx7Bank(readFile(std::string(VOLT_DX7_DIR) + "/rom1a.syx"), bank) == Dx7Status::Ok);
  const uint8_t* v = bank.voices[0].data();
  CHECK(dx7Name(v) == "BRASS   1");     // trailing space trimmed
  CHECK(v[134] == 21);                  // algorithm 22
  CHECK(v[135] == 7);                   // feedback
  CHECK(v[136] == 1);                   // osc key sync
  CHECK(v[137] == 37);                  // LFO speed
  CHECK(v[144] == 24);                  // transpose
  CHECK(v[0] == 49 && v[1] == 99 && v[2] == 28 && v[3] == 68);  // OP6 rates
  CHECK(v[4] == 98 && v[7] == 0);                                // OP6 L1, L4
  CHECK(v[16] == 82);                   // OP6 output level
  CHECK(v[105 + 16] == 98);             // OP1 output level
  CHECK(v[105 + 18] == 0);              // OP1 coarse
  CHECK(v[105 + 20] == 14);             // OP1 detune +7
  CHECK(dx7Name(bank.voices[10].data()) == "E.PIANO 1");
  CHECK(bank.voices[10][134] == 4);     // algorithm 5
}

// Three factory cartridges hold bytes that a clamping reader cannot reproduce: unused high bits in the
// packed fields (rom3a: OKS/FB byte and a curve byte), and out-of-range values (rom3a/rom3b/rom4a: a
// level or rate of 100 or 127 where the maximum is 99). The codec drops/clamps these on purpose, so
// those files are checked by unpack -> pack -> unpack; all the others must be byte-identical.
TEST(dx7_codec_bank_round_trip_is_byte_identical) {
  for (auto& f : cartridges()) {
    const auto bytes = readFile(f);
    const std::string name = std::filesystem::path(f).filename().string();
    const bool dirty = name == "rom3a.syx" || name == "rom3b.syx" || name == "rom4a.syx";
    Dx7Bank bank;
    CHECK(readDx7Bank(bytes, bank) == Dx7Status::Ok);
    const auto again = writeDx7Bank(bank);
    CHECK(again.size() == bytes.size());
    if (!dirty) {
      // Byte 2 is the channel; factory files use 0, which is what we write.
      CHECK(std::memcmp(again.data(), bytes.data(), bytes.size()) == 0);
    }
    Dx7Bank bank2;
    CHECK(readDx7Bank(again, bank2) == Dx7Status::Ok);
    for (int i = 0; i < 32; ++i) CHECK(bank2.voices[i] == bank.voices[i]);
    CHECK(writeDx7Bank(bank2) == again);
  }
}

TEST(dx7_codec_single_voice_round_trip) {
  Dx7Bank bank;
  CHECK(readDx7Bank(readFile(std::string(VOLT_DX7_DIR) + "/rom1a.syx"), bank) == Dx7Status::Ok);
  const auto sx = writeDx7Voice(bank.voices[10]);
  CHECK(sx.size() == 163);
  CHECK(sx[0] == 0xF0 && sx[1] == 0x43 && sx[3] == 0x00 && sx[4] == 0x01 && sx[5] == 0x1B && sx[162] == 0xF7);
  Dx7Voice back{};
  CHECK(readDx7Voice(sx, back) == Dx7Status::Ok);
  CHECK(std::memcmp(back.data(), bank.voices[10].data(), 155) == 0);
}

TEST(dx7_codec_bad_checksum_still_reads_with_a_warning) {
  auto bytes = readFile(std::string(VOLT_DX7_DIR) + "/rom1a.syx");
  bytes[4102] ^= 0x11;
  Dx7Bank bank;
  CHECK(readDx7Bank(bytes, bank) == Dx7Status::BadChecksum);
  CHECK(dx7Name(bank.voices[0].data()) == "BRASS   1");
}

TEST(dx7_codec_rejects_what_is_not_a_dx7_bank) {
  Dx7Bank bank;
  CHECK(readDx7Bank(std::vector<uint8_t>(100, 0), bank) == Dx7Status::NotDx7);
  auto bytes = readFile(std::string(VOLT_DX7_DIR) + "/rom1a.syx");
  bytes[1] = 0x41;  // Roland
  CHECK(readDx7Bank(bytes, bank) == Dx7Status::NotDx7);
}

TEST(dx7_codec_masks_and_clamps_garbage) {
  auto bytes = readFile(std::string(VOLT_DX7_DIR) + "/rom1a.syx");
  bytes[6 + 0] = 0xFF;          // OP6 R1: high bit set, 127 after masking -> clamped to 99
  bytes[6 + 110] = 0x7F;        // algorithm byte with junk in bits 5..6 -> 31
  bytes[6 + 116] = 0x7F;        // LPMS 7, LFW 7 (clamped to 5), LKS 1
  Dx7Bank bank;
  readDx7Bank(bytes, bank);
  const uint8_t* v = bank.voices[0].data();
  CHECK(v[0] == 99);
  CHECK(v[134] == 31);
  CHECK(v[142] == 5 && v[143] == 7 && v[141] == 1);
}
