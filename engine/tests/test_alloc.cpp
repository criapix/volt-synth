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

TEST(alloc_render_fm_never_touches_the_heap) {
  Engine e;
  e.setSampleRate(48000.0f);
  static float buf[2 * 512];
  e.post({Event::SetParam, kEngineType, 1.0f});
  e.render(buf, 512);  // warm-up
  long before = g_allocs.load();
  for (int n = 0; n < 16; ++n) e.post({Event::NoteOn, 40 + n * 3, 0.8f});
  for (int round = 0; round < 200; ++round) {
    if (round == 20) e.post({Event::ModWheel, 0, 0.8f});
    if (round == 40) e.post({Event::Aftertouch, 0, 0.6f});
    if (round == 60) e.post({Event::Breath, 0, 0.5f});
    if (round % 25 == 10) e.post({Event::SetParam, kFmOp1Level, norm(kFmOp1Level, (float)(20 + round % 70))});
    if (round == 100) for (int n = 0; n < 16; ++n) e.post({Event::NoteOff, 40 + n * 3, 0.0f});
    e.render(buf, 1 + (round * 37) % 512);
  }
  CHECK(g_allocs.load() == before);
}
