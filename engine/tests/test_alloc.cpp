#include "engine.h"
#include "test.h"
#include <atomic>
#include <cstdlib>
#include <new>

// Counts every heap allocation in the test binary.
static std::atomic<long> g_allocs{0};
void* operator new(std::size_t n) { ++g_allocs; if (void* p = std::malloc(n ? n : 1)) return p; throw std::bad_alloc(); }
void* operator new[](std::size_t n) { ++g_allocs; if (void* p = std::malloc(n ? n : 1)) return p; throw std::bad_alloc(); }
void operator delete(void* p) noexcept { std::free(p); }
void operator delete[](void* p) noexcept { std::free(p); }
void operator delete(void* p, std::size_t) noexcept { std::free(p); }
void operator delete[](void* p, std::size_t) noexcept { std::free(p); }

using namespace volt;

TEST(alloc_render_never_touches_the_heap) {
  Engine e;
  e.setSampleRate(48000.0f);
  static float buf[2 * 512];
  Rng rng;
  e.render(buf, 512);  // warm-up
  // Second warm-up with every effect on, so lazy first-block work in the sst effects happens before counting.
  for (int id : {kFxDistOn, kFxPhaserOn, kFxFlangerOn, kFxChorusOn, kFxDelayOn, kFxReverbOn})
    e.post({Event::SetParam, id, 1.0f});
  e.render(buf, 512);
  e.render(buf, 512);
  long before = g_allocs.load();
  for (int round = 0; round < 400; ++round) {
    // post() is a non-audio call but must not allocate either.
    e.post({Event::SetParam, (int)(rng.next() % kNumParams), rng.unipolar()});
    e.post({(rng.next() & 1) ? Event::NoteOn : Event::NoteOff, 30 + (int)(rng.next() % 70), 0.7f});
    if (round % 50 == 0) {
      static float patch[kNumParams];
      for (int i = 0; i < kNumParams; ++i) patch[i] = rng.unipolar();
      e.postPatch(patch, kNumParams);
    }
    if (round % 50 == 25) {
      static const int kOn[] = {kFxDistOn, kFxPhaserOn, kFxFlangerOn, kFxChorusOn, kFxDelayOn, kFxReverbOn, kArpOn};
      e.post({Event::SetParam, kOn[rng.next() % 7], 1.0f});
    }
    e.render(buf, 1 + (int)(rng.next() % 512));
  }
  CHECK(g_allocs.load() == before);
}
