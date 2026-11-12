#include "lfo.h"
#include "test.h"

using namespace volt;

static const float dt = 16.0f / 48000.0f;

TEST(lfo_every_wave_stays_in_range_and_moves) {
  for (int wave = 0; wave < 5; ++wave) {
    Lfo l;
    float lo = 1, hi = -1;
    for (int i = 0; i < 6000; ++i) {
      float v = l.tick(wave, 5.0f, 0.0f, dt);
      CHECK(v >= -1.0001f && v <= 1.0001f);
      lo = v < lo ? v : lo; hi = v > hi ? v : hi;
    }
    CHECK(lo < -0.5f && hi > 0.5f);
  }
}

TEST(lfo_runs_at_the_asked_rate) {
  Lfo l;
  int cycles = 0;
  float prev = 0;
  for (int i = 0; i < 3000 * 2; ++i) {  // 2 seconds at 3000 ticks/s
    float v = l.tick(0, 5.0f, 0.0f, dt);
    if (prev < 0 && v >= 0) ++cycles;
    prev = v;
  }
  CHECK_NEAR(cycles, 10, 1);
}

TEST(lfo_delay_fades_in_after_the_note_starts) {
  Lfo l;
  l.restart();
  float early = 0, late = 0;
  for (int i = 0; i < 300; ++i) { float v = std::fabs(l.tick(3, 5.0f, 1.0f, dt)); early = v > early ? v : early; }
  for (int i = 0; i < 6000; ++i) { float v = std::fabs(l.tick(3, 5.0f, 1.0f, dt)); late = v > late ? v : late; }
  CHECK(early < 0.2f);   // 0.1 s into a 1 s fade
  CHECK_NEAR(late, 1.0f, 0.01);
}

TEST(lfo_sample_and_hold_holds_between_steps) {
  Lfo l;
  int changes = 0;
  float prev = l.tick(4, 5.0f, 0.0f, dt);
  for (int i = 0; i < 3000; ++i) { float v = l.tick(4, 5.0f, 0.0f, dt); if (v != prev) ++changes; prev = v; }
  CHECK_NEAR(changes, 5, 1);
}
