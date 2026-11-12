#include "arp.h"
#include "test.h"
#include <vector>

using namespace volt;

namespace {
struct Ev { bool on; int note; long at; };

// Runs the arpeggiator for `samples` in 16-sample blocks and returns events with absolute times.
struct ArpRig {
  Arp a;
  ArpSettings s{0, 1, 1, 120.0f, 0.5f};  // up, 1 octave, 1/8, 120 BPM, 50 % gate
  long now = 0;
  ArpRig() { a.setSampleRate(48000.0f); }
  std::vector<Ev> run(long samples) {
    std::vector<Ev> out;
    ArpEvent ev[8];
    for (long t = 0; t < samples; t += 16) {
      int n = a.advance(16, s, ev, 8);
      for (int i = 0; i < n; ++i) out.push_back({ev[i].on, ev[i].note, now + ev[i].at});
      now += 16;
    }
    return out;
  }
};
std::vector<int> ons(const std::vector<Ev>& v) { std::vector<int> r; for (auto& e : v) if (e.on) r.push_back(e.note); return r; }
}  // namespace

TEST(arp_first_note_is_immediate_and_steps_follow_the_tempo) {
  ArpRig r;
  r.a.keyDown(60, 0.8f);
  auto ev = r.run(48000);  // 1 s at 120 BPM, 1/8 = 0.25 s per step
  std::vector<long> onAt, offAt;
  for (auto& e : ev) (e.on ? onAt : offAt).push_back(e.at);
  CHECK(onAt.size() == 4);
  for (size_t i = 0; i < onAt.size(); ++i) CHECK_NEAR(onAt[i], 12000.0 * i, 16);
  CHECK(offAt.size() >= 3);
  CHECK_NEAR(offAt[0], 6000, 16);  // 50 % gate
}

TEST(arp_triplets_are_two_thirds_of_the_step) {
  ArpRig r;
  r.s.division = 4;  // 1/8T
  r.a.keyDown(60, 0.8f);
  auto ev = r.run(24000);
  std::vector<long> onAt;
  for (auto& e : ev) if (e.on) onAt.push_back(e.at);
  CHECK(onAt.size() == 3);
  CHECK_NEAR(onAt[1], 8000, 16);
}

TEST(arp_up_spans_the_octaves_in_pitch_order) {
  ArpRig r;
  r.s.octaves = 2;
  r.a.keyDown(64, 1); r.a.keyDown(60, 1); r.a.keyDown(67, 1);
  auto n = ons(r.run(12000L * 7));
  std::vector<int> want = {60, 64, 67, 72, 76, 79, 60};
  CHECK(n == want);
}

TEST(arp_down_and_up_down) {
  ArpRig r;
  r.s.mode = 1;
  r.a.keyDown(60, 1); r.a.keyDown(64, 1); r.a.keyDown(67, 1);
  auto down = ons(r.run(12000L * 3));
  CHECK((down == std::vector<int>{67, 64, 60}));
  ArpRig u;
  u.s.mode = 2;
  u.a.keyDown(60, 1); u.a.keyDown(64, 1); u.a.keyDown(67, 1);
  auto ud = ons(u.run(12000L * 6));
  CHECK((ud == std::vector<int>{60, 64, 67, 64, 60, 64}));
}

TEST(arp_as_played_keeps_the_key_order) {
  ArpRig r;
  r.s.mode = 4;
  r.a.keyDown(64, 1); r.a.keyDown(60, 1); r.a.keyDown(67, 1);
  CHECK((ons(r.run(12000L * 3)) == std::vector<int>{64, 60, 67}));
}

TEST(arp_random_stays_inside_the_chord_and_varies) {
  ArpRig r;
  r.s.mode = 3;
  r.a.keyDown(60, 1); r.a.keyDown(64, 1); r.a.keyDown(67, 1);
  auto n = ons(r.run(12000L * 40));
  bool varied = false;
  for (size_t i = 0; i < n.size(); ++i) {
    CHECK(n[i] == 60 || n[i] == 64 || n[i] == 67);
    if (i && n[i] != n[i - 1]) varied = true;
  }
  CHECK(varied);
}

TEST(arp_releasing_every_key_stops_and_releases_the_sounding_note) {
  ArpRig r;
  r.s.gate = 1.0f;
  r.a.keyDown(60, 1);
  r.run(3000);
  r.a.keyUp(60);
  auto ev = r.run(48000);
  CHECK(ev.size() == 1);
  CHECK(!ev[0].on && ev[0].note == 60);
}

TEST(arp_hold_latches_and_a_new_chord_replaces_it) {
  ArpRig r;
  r.a.setHold(true);
  r.a.keyDown(60, 1); r.a.keyDown(64, 1);
  r.a.keyUp(60); r.a.keyUp(64);
  CHECK((ons(r.run(12000L * 2)) == std::vector<int>{60, 64}));
  r.a.keyDown(67, 1);  // every key was up: this starts a new chord
  auto n = ons(r.run(12000L * 3));
  for (int x : n) CHECK(x == 67);
  r.a.keyUp(67);
  r.a.setHold(false);  // nothing physically held any more: stops
  r.run(12000);
  CHECK(ons(r.run(48000)).empty());
}

TEST(arp_clear_reports_the_sounding_note) {
  ArpRig r;
  r.s.gate = 1.0f;
  r.a.keyDown(62, 1);
  r.run(100);
  CHECK(r.a.clear() == 62);
  CHECK(r.a.clear() == -1);
  CHECK(r.run(48000).empty());
}

TEST(arp_velocity_follows_the_key) {
  Arp a;
  a.setSampleRate(48000.0f);
  a.keyDown(60, 0.3f);
  ArpEvent ev[8];
  ArpSettings s{0, 1, 1, 120.0f, 0.5f};
  int n = a.advance(16, s, ev, 8);
  CHECK(n == 1);
  CHECK_NEAR(ev[0].vel, 0.3f, 1e-6);
}
