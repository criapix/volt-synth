#pragma once
#include <atomic>
#include <cstdint>

namespace volt {

struct Event {
  enum Type : int32_t { NoteOn, NoteOff, SetParam, PitchBend, ModWheel, Sustain, AllNotesOff };
  Type type;
  int32_t a;  // note, or parameter id
  float b;    // velocity 0..1, normalized parameter value, bend -1..1, wheel 0..1, sustain 0/1
};

// Single-producer / single-consumer ring. push() from one thread at a time, pop() from another.
class EventQueue {
 public:
  static constexpr int kCapacity = 1024;  // power of two

  bool push(const Event& e) {
    uint32_t w = write_.load(std::memory_order_relaxed);
    uint32_t next = (w + 1) & (kCapacity - 1);
    if (next == read_.load(std::memory_order_acquire)) return false;  // full
    buf_[w] = e;
    write_.store(next, std::memory_order_release);
    return true;
  }

  bool pop(Event& e) {
    uint32_t r = read_.load(std::memory_order_relaxed);
    if (r == write_.load(std::memory_order_acquire)) return false;  // empty
    e = buf_[r];
    read_.store((r + 1) & (kCapacity - 1), std::memory_order_release);
    return true;
  }

 private:
  Event buf_[kCapacity];
  std::atomic<uint32_t> write_{0}, read_{0};
};

}  // namespace volt
