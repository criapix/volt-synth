#include <algorithm>
#include <cmath>
#include "dsp_probe.h"
#include "rig.h"
#include "test.h"

using namespace volt;

TEST(engine_fm_init_voice_sounds_at_pitch) {
  Rig r;
  r.set(kEngineType, 1);
  r.on(69);
  auto s = r.play(1.0f);
  std::vector<float> tail(s.begin() + 24000, s.end());
  CHECK(probe::rms(tail) > 0.01);
  CHECK_NEAR(probe::zeroCrossHz(tail, 48000.0), 440.0, 2.0);
  CHECK(r.e.activeVoices() == 1);
}

TEST(engine_fm_voices_free_after_release) {
  Rig r;
  r.set(kEngineType, 1);
  for (int n : {60, 64, 67}) r.on(n);
  r.play(0.3f);
  for (int n : {60, 64, 67}) r.off(n);
  r.play(3.0f);
  CHECK(r.e.activeVoices() == 0);
}

TEST(engine_fm_sustain_pedal_holds) {
  Rig r;
  r.set(kEngineType, 1);
  r.e.post({Event::Sustain, 0, 1.0f});
  r.on(60);
  r.play(0.1f);
  r.off(60);
  r.play(1.0f);
  CHECK(r.e.activeVoices() == 1);
  r.e.post({Event::Sustain, 0, 0.0f});
  r.play(3.0f);
  CHECK(r.e.activeVoices() == 0);
}

TEST(engine_fm_sixteen_voices_then_stealing) {
  Rig r;
  r.set(kEngineType, 1);
  for (int n = 40; n < 60; ++n) r.on(n);
  r.play(0.2f);
  CHECK(r.e.activeVoices() == 16);
}

TEST(engine_fm_mono_mode_plays_one_voice) {
  Rig r;
  r.set(kEngineType, 1);
  r.set(kVoiceMode, 3);  // unison: still one FM voice
  for (int n : {60, 64, 67}) r.on(n);
  r.play(0.3f);
  CHECK(r.e.activeVoices() == 1);
  for (int n : {60, 64, 67}) r.off(n);
  r.play(3.0f);
  CHECK(r.e.activeVoices() == 0);
}

TEST(engine_switching_type_mid_note_does_not_click) {
  Rig r;
  r.on(60);
  r.play(0.3f);
  auto patch = defaultPatch();
  patch[kEngineType] = norm(kEngineType, 1);
  r.e.postPatch(patch.data(), kNumParams);
  auto s = r.play(0.2f);
  // The fade-out/fade-in keeps every sample-to-sample step small.
  float maxStep = 0;
  for (size_t i = 1; i < s.size(); ++i) maxStep = std::max(maxStep, std::fabs(s[i] - s[i - 1]));
  CHECK(maxStep < 0.1f);
  CHECK(r.e.activeVoices() == 0);
}

TEST(engine_fm_effects_still_apply) {
  Rig r;
  r.set(kEngineType, 1);
  r.set(kFxReverbOn, 1);
  r.set(kFxReverbMix, 0.5f);
  r.on(60);
  r.play(0.2f);
  r.off(60);
  auto s = r.play(1.0f);
  // The voice is gone quickly, the reverb tail is not.
  CHECK(r.e.activeVoices() == 0);
  CHECK(probe::rms(std::vector<float>(s.begin() + 24000, s.end())) > 1e-4);
}
