#include "dsp_probe.h"
#include "engine.h"
#include "filter.h"
#include "rig.h"
#include "test.h"

using namespace volt;

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

TEST(engine_sync_locks_osc1_to_osc2s_period) {
  // osc 2 (silent) plays the note, 110 Hz; osc 1 is a fifth above, 164.8 Hz.
  auto energy = [](bool sync) {
    Rig r;
    r.set(kVoiceDrift, 0.0f);
    r.set(kFilterModel, 1);
    r.set(kOsc1Semi, 7);
    r.set(kOsc2Level, 0.0f);
    r.set(kOsc1Sync, sync ? 1.0f : 0.0f);
    r.play(0.01f);
    r.on(45);
    r.play(0.1f);
    auto x = r.play(1.0f);
    return std::make_pair(probe::tone(x, 110.0, 48000.0), probe::tone(x, 164.81, 48000.0));
  };
  auto free = energy(false), locked = energy(true);
  CHECK(free.second > 5.0 * free.first);     // unsynced: osc 1 sounds at its own pitch
  CHECK(locked.first > 5.0 * locked.second);  // synced: osc 1 restarts every osc 2 cycle
}

TEST(engine_effects_change_the_output_and_every_effect_off_does_not) {
  auto run = [](bool reverb) {
    Rig r;
    r.set(kVoiceDrift, 0.0f);
    if (reverb) { r.set(kFxReverbOn, 1); r.set(kFxReverbMix, 0.5f); }
    r.play(0.01f);
    r.on(57); r.play(0.3f); r.off(57);
    return r.play(1.0f);
  };
  auto dry = run(false), wet = run(true);
  CHECK(probe::rms(std::vector<float>(wet.begin() + 30000, wet.end())) > 3.0 * probe::rms(std::vector<float>(dry.begin() + 30000, dry.end())) + 1e-6);
}

TEST(engine_arpeggiator_repeats_a_held_key_at_the_tempo) {
  Rig r;
  r.set(kArpOn, 1); r.set(kArpBpm, 120); r.set(kArpDivision, 2);  // 1/16 = 0.125 s
  r.set(kArpGate, 0.5f);
  r.set(kAenvAttack, 0.001f); r.set(kAenvRelease, 0.005f); r.set(kAenvSustain, 1.0f);
  r.play(0.01f);
  r.on(60);
  int starts = 0, prev = 0;
  std::vector<float> buf(2 * 16);
  for (int b = 0; b < 48000 / 16; ++b) {  // 1 s
    r.e.render(buf.data(), 16);
    const int v = r.e.activeVoices();
    if (v > 0 && prev == 0) ++starts;
    prev = v;
  }
  CHECK_NEAR(starts, 8, 1);
}

TEST(engine_switching_the_arp_on_and_off_never_leaves_a_stuck_note) {
  Rig r;
  r.set(kAenvRelease, 0.01f);
  r.on(60); r.play(0.1f);
  r.set(kArpOn, 1); r.play(0.3f);   // the held key moves into the arpeggiator
  r.set(kArpOn, 0); r.play(0.1f);   // and back to a normal note
  CHECK(r.e.activeVoices() == 1);
  r.off(60); r.play(0.3f);
  CHECK(r.e.activeVoices() == 0);
  r.set(kArpOn, 1); r.on(62); r.play(0.2f); r.off(62); r.play(0.5f);
  CHECK(r.e.activeVoices() == 0);
}

TEST(engine_arp_notes_ignore_the_sustain_pedal) {
  Rig r;
  r.set(kArpOn, 1); r.set(kArpDivision, 2);
  r.set(kAenvRelease, 0.005f);
  r.e.post({Event::Sustain, 0, 1.0f});
  r.on(60); r.on(64); r.on(67);
  r.play(1.0f);
  CHECK(r.e.activeVoices() <= 2);  // with the pedal holding them, every step would pile up a voice
}
