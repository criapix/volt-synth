#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>
#include "dsp_probe.h"
#include "fm/fm_block.h"
#include "fm/fm_params.h"
#include "params.h"
#include "test.h"
#include <filesystem>
#include <fstream>
#include <iterator>
#include "fm/dx7_codec.h"
#include "dx7_fixtures.h"
// raw msfa for the reference path (msfa headers last: synth.h defines macros)
#include "controllers.h"
#include "dx7note.h"
#include "EngineMsfa.h"
#include "../../third_party/Synth_Dexed/src/lfo.h"

using namespace volt;

namespace {
struct FmRig {
  FmBlock fm;
  float value[kNumParams];
  FmRig() {
    for (int i = 0; i < kNumParams; ++i) value[i] = kParams[i].def;
    value[kEngineType] = 1;
    fm.setSampleRate(48000.0f);
    fm.setPatch(value);
  }
  std::vector<float> play(int blocks) {
    std::vector<float> out;
    float buf[16];
    for (int b = 0; b < blocks; ++b) {
      std::memset(buf, 0, sizeof(buf));
      fm.render(buf, 16);
      out.insert(out.end(), buf, buf + 16);
    }
    return out;
  }
};
}  // namespace

TEST(fm_block_init_voice_plays_concert_a) {
  FmRig r;
  r.fm.noteOn(0, 69, 0.8f, 1.0f);
  auto s = r.play(3000);  // 1 s
  std::vector<float> tail(s.begin() + 24000, s.end());
  CHECK(probe::finite(tail));
  CHECK(probe::rms(tail) > 0.02f);
  CHECK_NEAR(probe::zeroCrossHz(tail, 48000.0), 440.0, 2.0);
  CHECK(r.fm.active(0));
}

TEST(fm_block_voice_ends_after_release) {
  FmRig r;
  r.fm.noteOn(3, 60, 0.8f, 1.0f);
  r.play(300);
  r.fm.noteOff(3);
  r.play(3000 * 3);  // INIT VOICE R4 = 99: very short release
  CHECK(!r.fm.active(3));
}

TEST(fm_block_transpose_and_bend_move_the_pitch) {
  FmRig r;
  r.value[kFmTranspose] = 12;
  r.fm.setPatch(r.value);
  r.fm.noteOn(0, 57, 0.8f, 1.0f);           // A3 + 12 = A4
  auto s = r.play(3000);
  CHECK_NEAR(probe::zeroCrossHz(std::vector<float>(s.begin() + 24000, s.end()), 48000.0), 440.0, 2.0);
  r.fm.setBend(1.0f, 2);                    // +2 semitones
  s = r.play(3000);
  CHECK_NEAR(probe::zeroCrossHz(std::vector<float>(s.begin() + 24000, s.end()), 48000.0), 493.9, 3.0);
}

TEST(fm_block_operator_switch_silences_op1) {
  FmRig r;
  r.value[kFmOp1R1 + 21] = 0;  // fm.op1.on
  r.fm.setPatch(r.value);
  r.fm.noteOn(0, 69, 0.8f, 1.0f);
  auto s = r.play(1500);
  CHECK(probe::rms(s) < 1e-4f);
}

TEST(fm_block_quick_release_frees_the_voice_in_20_ms) {
  FmRig r;
  r.value[kFmOp1R1 + 3] = 0;  // R4 = 0: the slowest release there is
  r.fm.setPatch(r.value);
  r.fm.noteOn(5, 60, 0.8f, 1.0f);
  r.play(300);
  r.fm.quickRelease(5);
  r.play(60);                 // 20 ms
  CHECK(!r.fm.active(5));
}

TEST(fm_block_kill_is_immediate) {
  FmRig r;
  r.fm.noteOn(2, 60, 0.8f, 1.0f);
  r.play(10);
  r.fm.kill(2);
  CHECK(!r.fm.active(2));
}

TEST(fm_block_matches_raw_msfa_for_factory_voices) {
  DX7_REQUIRE_ANY();
  int cartridges = 0, compared = 0;
  for (auto& cpath : dx7fx::dx7Cartridges()) {
    const std::filesystem::path e_path(cpath);
    ++cartridges;
    std::ifstream in(e_path, std::ios::binary);
    std::vector<uint8_t> bytes(std::istreambuf_iterator<char>(in), {});
    Dx7Bank bank;
    readDx7Bank(bytes, bank);
    for (int vi : {0, 7, 15, 31}) {
      FmRig r;
      valuesFromVced(bank.voices[vi], r.value);
      r.fm.setPatch(r.value);
      r.fm.noteOn(0, 60, 100 / 127.0f, 1.0f);
      auto got = r.play(4 * 750);  // 1 s, 750 chunks of 64

      EngineMsfa core; Controllers c;
      c.values_[kControllerPitch] = 0x2000; c.values_[kControllerPitchRange] = 2;
      c.values_[kControllerPitchStep] = 0; c.values_[kControllerPortamentoGlissando] = 0;
      c.modwheel_cc = c.aftertouch_cc = c.breath_cc = c.foot_cc = 0; c.masterTune = 0;
      c.opSwitch = 0x3f; c.core = &core;
      c.wheel.setRange(50); c.wheel.setTarget(1); c.refresh();
      Dx7Voice p = bank.voices[vi];
      ::Lfo lfo{}; lfo.reset(p.data() + 137); lfo.keydown();
      Dx7Note n;
      const int midi = 60 + p[144] - 24;
      n.init(p.data(), midi, 100, midi, -1, &c);
      if (p[136]) n.oscSync();
      double maxDiff = 0, peak = 0;
      int32_t buf[64];
      for (int ch = 0; ch < 750; ++ch) {
        std::memset(buf, 0, sizeof(buf));
        n.compute(buf, lfo.getsample(), lfo.getdelay(), &c);
        for (int i = 0; i < 64; ++i) {
          int32_t s = buf[i] >> 13;
          s = s > 32767 ? 32767 : (s < -32768 ? -32768 : s);
          maxDiff = std::max(maxDiff, std::fabs(s / 32768.0 - got[ch * 64 + i]));
          peak = std::max(peak, std::fabs(s / 32768.0));
        }
      }
      ++compared;
      // A voice that is silent on both paths would pass the comparison without proving anything.
      if (peak <= 0.0) { std::printf("    FAIL %s voice %d is silent\n", e_path.filename().string().c_str(), vi + 1); ++t::failures(); }
      if (maxDiff > 1e-6) { std::printf("    FAIL %s voice %d: max diff %g\n", e_path.filename().string().c_str(), vi + 1, maxDiff); ++t::failures(); }
    }
  }
  CHECK(cartridges >= 1);
  CHECK(compared == 4 * cartridges);  // 8 cartridges / 32 voices with the full factory set
}
