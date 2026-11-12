#include "event_queue.h"
#include "test.h"
#include <thread>

using namespace volt;

TEST(queue_keeps_order) {
  EventQueue q;
  for (int i = 0; i < 10; ++i) CHECK(q.push({Event::NoteOn, i, 0.5f}));
  Event e;
  for (int i = 0; i < 10; ++i) { CHECK(q.pop(e)); CHECK(e.a == i); }
  CHECK(!q.pop(e));
}

TEST(queue_refuses_when_full_and_recovers) {
  EventQueue q;
  int accepted = 0;
  while (q.push({Event::NoteOn, accepted, 0})) ++accepted;
  CHECK(accepted == EventQueue::kCapacity - 1);
  Event e;
  CHECK(q.pop(e));
  CHECK(q.push({Event::NoteOff, 99, 0}));
}

TEST(queue_survives_a_producer_thread) {
  EventQueue q;
  const int n = 200000;
  std::thread producer([&] {
    for (int i = 0; i < n;) if (q.push({Event::SetParam, i, 0})) ++i;
  });
  int expected = 0;
  Event e;
  while (expected < n) if (q.pop(e)) { CHECK(e.a == expected); ++expected; }
  producer.join();
}
