#include "governor.h"
#include "test.h"

using namespace volt;

namespace {
// Feeds the same load for `seconds` in 2 ms callbacks; returns the last limit.
int feed(LoadGovernor& g, float load, float seconds, int xruns = 0) {
  int limit = g.limit();
  for (float t = 0; t < seconds; t += 0.002f) limit = g.update(load, xruns, 0.002f);
  return limit;
}
}  // namespace

TEST(governor_keeps_sixteen_voices_under_normal_load) {
  LoadGovernor g;
  CHECK(feed(g, 0.3f, 5.0f) == 16);
}

TEST(governor_drops_four_voices_after_a_quarter_second_of_overload) {
  LoadGovernor g;
  CHECK(feed(g, 0.9f, 0.2f) == 16);
  CHECK(feed(g, 0.9f, 0.1f) == 12);
}

TEST(governor_waits_two_seconds_between_drops_and_stops_at_eight) {
  LoadGovernor g;
  feed(g, 0.9f, 0.3f);
  CHECK(feed(g, 0.9f, 1.5f) == 12);
  CHECK(feed(g, 0.9f, 1.0f) == 8);
  CHECK(feed(g, 0.9f, 10.0f) == 8);
}

TEST(governor_drops_on_an_xrun_only_when_the_load_is_already_high) {
  LoadGovernor g;
  feed(g, 0.2f, 0.1f, 0);
  CHECK(g.update(0.2f, 1, 0.002f) == 16);  // low load: a scheduling hiccup, the buffer tuner's job
  CHECK(g.update(0.6f, 2, 0.002f) == 12);
}

TEST(governor_ignores_an_xrun_counter_that_restarted) {
  LoadGovernor g;
  feed(g, 0.6f, 0.1f, 40);
  CHECK(feed(g, 0.6f, 0.1f, 0) == 16);  // a reopened stream counts from zero again
}

TEST(governor_raises_the_limit_after_ten_quiet_seconds) {
  LoadGovernor g;
  feed(g, 0.9f, 0.3f);
  CHECK(g.limit() == 12);
  CHECK(feed(g, 0.3f, 9.0f) == 12);
  CHECK(feed(g, 0.3f, 1.5f) == 16);
}
