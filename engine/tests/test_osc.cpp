#include "dsp_probe.h"
#include "osc.h"
#include "test.h"

using namespace volt;

static std::vector<float> run(int wave, float hz, float pw = 0.5f, int n = 48000) {
  Osc o;
  std::vector<float> x(n);
  for (auto& v : x) v = o.tick(hz / 48000.0f, wave, pw);
  return x;
}

TEST(osc_every_wave_plays_the_asked_frequency) {
  for (int wave = 0; wave < 4; ++wave) {
    auto x = run(wave, 440.0f);
    CHECK_NEAR(probe::tone(x, 440.0, 48000.0) > 0.5, 1, 0);  // strong fundamental
    CHECK(probe::peak(x) <= 1.3);                            // BLEP overshoot is small
    CHECK(probe::finite(x));
  }
}

TEST(osc_sine_and_triangle_cross_zero_once_per_cycle) {
  CHECK_NEAR(probe::zeroCrossHz(run(3, 440.0f), 48000.0), 440.0, 1.0);
  CHECK_NEAR(probe::zeroCrossHz(run(2, 440.0f), 48000.0), 440.0, 1.0);
}

TEST(osc_saw_has_the_harmonics_of_a_saw) {
  auto x = run(0, 100.0f);
  double h1 = probe::tone(x, 100, 48000), h2 = probe::tone(x, 200, 48000), h3 = probe::tone(x, 300, 48000);
  CHECK_NEAR(h1, 2.0 / 3.14159265, 0.02);  // 2/pi
  CHECK_NEAR(h2 / h1, 0.5, 0.02);
  CHECK_NEAR(h3 / h1, 1.0 / 3.0, 0.02);
}

TEST(osc_square_has_only_odd_harmonics_and_pw_changes_that) {
  auto sq = run(1, 100.0f, 0.5f);
  CHECK(probe::tone(sq, 200, 48000) < 0.01);
  CHECK(probe::tone(sq, 300, 48000) > 0.3);
  auto narrow = run(1, 100.0f, 0.25f);
  CHECK(probe::tone(narrow, 200, 48000) > 0.3);
}

TEST(osc_saw_aliases_much_less_than_a_naive_saw) {
  // 3100 Hz saw at 48 kHz: the 11th harmonic (34100 Hz) folds to 13900 Hz, which is not a harmonic.
  const float hz = 3100.0f;
  auto blep = run(0, hz);
  std::vector<float> naive(48000);
  float ph = 0;
  for (auto& v : naive) { v = 2 * ph - 1; ph += hz / 48000.0f; if (ph >= 1) ph -= 1; }
  double a = probe::tone(blep, 13900, 48000), b = probe::tone(naive, 13900, 48000);
  CHECK(b > 0.03);       // the naive saw really aliases there
  CHECK(a < b * 0.25);   // at least 12 dB better
}

TEST(osc_reports_the_wrap_for_hard_sync) {
  Osc o;
  int wraps = 0;
  for (int i = 0; i < 48000; ++i) {
    o.tick(100.0f / 48000.0f, 0, 0.5f);
    if (o.wrapped) { ++wraps; CHECK(o.wrapFrac >= 0.0f && o.wrapFrac < 1.0f); }
  }
  CHECK_NEAR(wraps, 100, 1);
}
