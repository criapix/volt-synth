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
