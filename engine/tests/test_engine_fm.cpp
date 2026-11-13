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

namespace {
float maxStep(const std::vector<float>& s) {
  float m = 0;
  for (size_t i = 1; i < s.size(); ++i) m = std::max(m, std::fabs(s[i] - s[i - 1]));
  return m;
}
std::vector<float> cat(std::vector<float> a, const std::vector<float>& b) { a.insert(a.end(), b.begin(), b.end()); return a; }
std::vector<float> patchFor(int type) {
  auto p = defaultPatch();
  p[kEngineType] = norm(kEngineType, (float)type);
  return p;
}
}  // namespace

TEST(engine_switching_type_mid_note_does_not_click) {
  Rig r;
  r.on(60);
  auto before = r.play(0.3f);
  CHECK(probe::peak(before) > 0.05);  // the analog note really sounds, so the test can fail
  auto p = patchFor(1);
  r.e.postPatch(p.data(), kNumParams);
  auto fade = r.play(0.05f);  // fade-out, patch snaps, FM selected
  CHECK(r.e.activeVoices() == 0);
  r.on(60);
  auto after = r.play(0.4f);
  auto whole = cat(cat(before, fade), after);
  // The limit is relative: the transition may not step more than the notes themselves do.
  const float steady = std::max(maxStep(before), maxStep(after));
  std::printf("    measured max step analog->FM: %g (steady notes: %g, pre-switch peak %g)\n", maxStep(whole), steady, probe::peak(before));
  CHECK(probe::rms(std::vector<float>(after.begin() + 9600, after.end())) > 0.01);  // the FM note sounds
  CHECK(maxStep(whole) < 1.25f * steady);
}

TEST(engine_switching_fm_to_analog_does_not_click) {
  Rig r;
  r.set(kEngineType, 1);
  r.on(60);
  auto before = r.play(0.3f);
  CHECK(probe::peak(before) > 0.05);
  auto p = patchFor(0);
  r.e.postPatch(p.data(), kNumParams);
  auto fade = r.play(0.05f);
  CHECK(r.e.activeVoices() == 0);
  r.on(60);
  auto after = r.play(0.4f);
  auto whole = cat(cat(before, fade), after);
  const float steady = std::max(maxStep(before), maxStep(after));
  std::printf("    measured max step FM->analog: %g (steady notes: %g)\n", maxStep(whole), steady);
  CHECK(probe::rms(std::vector<float>(after.begin() + 9600, after.end())) > 0.01);
  CHECK(maxStep(whole) < 1.25f * steady);
}

TEST(engine_setparam_type_switch_kills_old_note_and_new_mode_sounds) {
  Rig r;
  r.on(60);
  r.play(0.3f);
  CHECK(r.e.activeVoices() == 1);
  r.set(kEngineType, 1);
  r.play(0.01f);
  CHECK(r.e.activeVoices() == 0);
  r.on(69);
  auto s = r.play(0.6f);
  CHECK(r.e.activeVoices() == 1);
  std::vector<float> tail(s.begin() + 12000, s.end());
  CHECK(probe::rms(tail) > 0.01);
  CHECK_NEAR(probe::zeroCrossHz(tail, 48000.0), 440.0, 2.0);
  r.set(kEngineType, 0);
  r.play(0.01f);
  CHECK(r.e.activeVoices() == 0);
  r.on(69);
  auto a = r.play(0.3f);
  CHECK(r.e.activeVoices() == 1);
  CHECK(probe::rms(a) > 0.01);
}

TEST(engine_fm_sample_rate_change_keeps_pitch) {
  Engine e;
  e.setSampleRate(44100.0f);
  e.post({Event::SetParam, kEngineType, 1.0f});
  e.post({Event::SetParam, kFmLfoPmd, norm(kFmLfoPmd, 0)});
  e.post({Event::NoteOn, 69, 0.8f});
  std::vector<float> out, buf(2 * 96);
  for (int i = 0; i < 44100 / 96; ++i) { e.render(buf.data(), 96); for (int k = 0; k < 96; ++k) out.push_back(buf[2 * k]); }
  std::vector<float> tail(out.begin() + 22050, out.end());
  CHECK(probe::rms(tail) > 0.01);
  CHECK_NEAR(probe::zeroCrossHz(tail, 44100.0), 440.0, 3.0);
}

TEST(engine_fm_voice_limit_applies) {
  Rig r;
  r.set(kEngineType, 1);
  for (int n = 50; n < 62; ++n) r.on(n);
  r.play(0.1f);
  CHECK(r.e.activeVoices() == 12);
  r.e.setVoiceLimit(8);
  r.play(0.1f);
  CHECK(r.e.activeVoices() <= 8);
}

TEST(engine_fm_mono_release_top_key_retriggers_lower) {
  Rig r;
  r.set(kEngineType, 1);
  r.set(kVoiceMode, 1);
  r.on(60);
  r.on(67);
  r.play(0.2f);
  r.off(67);
  auto s = r.play(0.5f);
  CHECK(r.e.activeVoices() == 1);
  CHECK(probe::rms(s) > 0.01);
  r.off(60);
  r.play(3.0f);
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
