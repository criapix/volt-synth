#include "dsp_probe.h"
#include "filter.h"
#include "test.h"

using namespace volt;

namespace {
// Feeds a sine into lane `lane` and returns the output RMS after the filter settled.
double sineThrough(int model, float cutoffHz, float reso, float hz, int lane = 0) {
  QuadFilter f;
  f.setSampleRate(48000.0f);
  f.setModel(model);
  alignas(16) float io[kBlock][4];
  std::vector<float> out;
  float phase = 0;
  for (int b = 0; b < 3000; ++b) {  // 1 second
    for (int s = 0; s < kBlock; ++s) {
      for (int l = 0; l < 4; ++l) io[s][l] = 0.0f;
      io[s][lane] = 0.25f * std::sin(6.28318530718f * phase);
      phase += hz / 48000.0f; if (phase >= 1) phase -= 1;
    }
    for (int l = 0; l < 4; ++l) f.setLane(l, cutoffHz, reso);
    f.process(io);
    f.endBlock();
    for (int s = 0; s < kBlock; ++s) out.push_back(io[s][lane]);
  }
  return probe::rms(out, 24000);
}
const double kIn = 0.25 / 1.41421356;  // RMS of the test sine
}  // namespace

TEST(filter_every_model_passes_lows_and_cuts_highs) {
  for (int model = 0; model < kNumFilterModels; ++model) {
    double low = sineThrough(model, 1000.0f, 0.1f, 125.0f);
    double high = sineThrough(model, 1000.0f, 0.1f, 8000.0f);
    CHECK(low > 0.4 * kIn);                 // three octaves below: mostly intact
    CHECK(high < (model == 1 ? 0.10 : 0.03) * low);  // three octaves above: 12 dB model vs 24 dB models
  }
}

TEST(filter_resonance_boosts_the_cutoff_frequency) {
  for (int model = 0; model < kNumFilterModels; ++model) {
    double flat = sineThrough(model, 1000.0f, 0.05f, 1000.0f);
    double peaky = sineThrough(model, 1000.0f, 0.85f, 1000.0f);
    CHECK(peaky > 1.5 * flat);
  }
}

TEST(filter_opening_the_cutoff_lets_more_through) {
  for (int model = 0; model < kNumFilterModels; ++model)
    CHECK(sineThrough(model, 8000.0f, 0.1f, 2000.0f) > 4.0 * sineThrough(model, 300.0f, 0.1f, 2000.0f));
}

TEST(filter_lanes_do_not_leak_into_each_other) {
  for (int model = 0; model < kNumFilterModels; ++model) {
    QuadFilter f;
    f.setSampleRate(48000.0f);
    f.setModel(model);
    alignas(16) float io[kBlock][4];
    double other = 0;
    for (int b = 0; b < 300; ++b) {
      for (int s = 0; s < kBlock; ++s) { io[s][0] = io[s][1] = io[s][3] = 0.0f; io[s][2] = (s & 1) ? 0.5f : -0.5f; }
      for (int l = 0; l < 4; ++l) f.setLane(l, 2000.0f, 0.5f);
      f.process(io);
      f.endBlock();
      for (int s = 0; s < kBlock; ++s) other = std::fmax(other, std::fabs(io[s][0]) + std::fabs(io[s][1]) + std::fabs(io[s][3]));
    }
    CHECK(other < 1e-6);
  }
}

TEST(filter_stays_finite_at_the_extremes) {
  const float cutoffs[] = {20.0f, 20000.0f}, resos[] = {0.0f, 1.0f};
  for (int model = 0; model < kNumFilterModels; ++model)
    for (float cut : cutoffs) for (float reso : resos) {
      QuadFilter f;
      f.setSampleRate(48000.0f);
      f.setModel(model);
      alignas(16) float io[kBlock][4];
      bool ok = true;
      double pk = 0;
      for (int b = 0; b < 1500; ++b) {
        for (int s = 0; s < kBlock; ++s) for (int l = 0; l < 4; ++l) io[s][l] = ((b + s) & 8) ? 1.0f : -1.0f;
        for (int l = 0; l < 4; ++l) f.setLane(l, cut, reso);
        f.process(io);
        f.endBlock();
        for (int s = 0; s < kBlock; ++s) for (int l = 0; l < 4; ++l) { ok = ok && std::isfinite(io[s][l]); pk = std::fmax(pk, std::fabs(io[s][l])); }
      }
      CHECK(ok);
      CHECK(pk < 20.0);  // loud is allowed with full resonance; runaway is not
    }
}

TEST(filter_reset_lane_clears_the_ringing) {
  QuadFilter f;
  f.setSampleRate(48000.0f);
  f.setModel(0);
  alignas(16) float io[kBlock][4];
  for (int b = 0; b < 200; ++b) {
    for (int s = 0; s < kBlock; ++s) for (int l = 0; l < 4; ++l) io[s][l] = (s == 0) ? 1.0f : 0.0f;
    for (int l = 0; l < 4; ++l) f.setLane(l, 500.0f, 0.95f);
    f.process(io); f.endBlock();
  }
  f.resetLane(1);
  for (int s = 0; s < kBlock; ++s) for (int l = 0; l < 4; ++l) io[s][l] = 0.0f;
  for (int l = 0; l < 4; ++l) f.setLane(l, 500.0f, 0.95f);
  f.process(io); f.endBlock();
  double lane1 = 0;
  for (int s = 0; s < kBlock; ++s) lane1 = std::fmax(lane1, std::fabs(io[s][1]));
  CHECK(lane1 < 1e-6);
}

TEST(filter_every_model_has_unity_passband_gain) {
  // Cutoff fully open, low resonance, a 440 Hz sine well inside the passband.
  for (int model = 0; model < kNumFilterModels; ++model) {
    double out = sineThrough(model, 20000.0f, 0.1f, 440.0f);
    CHECK_NEAR(out / kIn, 1.0, 0.19);  // within about +-1.5 dB
  }
}
