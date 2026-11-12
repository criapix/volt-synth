#include "adsr.h"
#include "test.h"

using namespace volt;

static const float dt = 16.0f / 48000.0f;
static int ticksFor(float seconds) { return (int)(seconds / dt); }

TEST(adsr_is_silent_until_gated) {
  Adsr e;
  e.set(0.01f, 0.1f, 0.5f, 0.1f, dt);
  for (int i = 0; i < 100; ++i) CHECK(e.tick() == 0.0f);
  CHECK(!e.active());
}

TEST(adsr_attack_reaches_full_level_in_the_attack_time) {
  Adsr e;
  e.set(0.1f, 1.0f, 1.0f, 0.1f, dt);
  e.gate(true);
  float v = 0;
  for (int i = 0; i < ticksFor(0.08f); ++i) v = e.tick();
  CHECK(v < 1.0f);
  for (int i = 0; i < ticksFor(0.04f); ++i) v = e.tick();
  CHECK_NEAR(v, 1.0f, 0.02);
}

TEST(adsr_decays_to_sustain_and_holds) {
  Adsr e;
  e.set(0.001f, 0.1f, 0.4f, 0.1f, dt);
  e.gate(true);
  float v = 0;
  for (int i = 0; i < ticksFor(0.15f); ++i) v = e.tick();
  CHECK_NEAR(v, 0.4f, 0.02);
  for (int i = 0; i < ticksFor(1.0f); ++i) v = e.tick();
  CHECK_NEAR(v, 0.4f, 0.005);
}

TEST(adsr_release_ends_and_goes_idle) {
  Adsr e;
  e.set(0.001f, 0.01f, 1.0f, 0.2f, dt);
  e.gate(true);
  for (int i = 0; i < ticksFor(0.05f); ++i) e.tick();
  e.gate(false);
  float v = 1;
  for (int i = 0; i < ticksFor(0.2f); ++i) v = e.tick();
  CHECK(v < 0.02f);
  for (int i = 0; i < ticksFor(0.2f); ++i) e.tick();
  CHECK(!e.active());
  CHECK(e.tick() == 0.0f);
}

TEST(adsr_retrigger_continues_from_the_current_level) {
  Adsr e;
  e.set(0.2f, 0.1f, 1.0f, 0.5f, dt);
  e.gate(true);
  for (int i = 0; i < ticksFor(0.3f); ++i) e.tick();
  e.gate(false);
  float before = 0;
  for (int i = 0; i < ticksFor(0.05f); ++i) before = e.tick();
  e.gate(true);
  float after = e.tick();
  CHECK(after >= before - 0.01f);  // no jump down to zero: no click
}
