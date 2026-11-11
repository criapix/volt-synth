#pragma once
#include <cmath>
#include <cstdint>

namespace volt {

constexpr int kBlock = 16;      // control-rate block, in samples
constexpr int kMaxVoices = 16;  // must be a multiple of 4 (filters run 4 voices at a time)

inline float noteToHz(float note) { return 440.0f * std::exp2((note - 69.0f) / 12.0f); }
inline float hzToNote(float hz) { return 69.0f + 12.0f * std::log2(hz / 440.0f); }
inline float clampf(float x, float lo, float hi) { return x < lo ? lo : (x > hi ? hi : x); }

// xorshift32: cheap noise, allocation-free.
struct Rng {
  uint32_t s = 0x1234567u;
  uint32_t next() { s ^= s << 13; s ^= s >> 17; s ^= s << 5; return s; }
  float bipolar() { return (float)(int32_t)next() * (1.0f / 2147483648.0f); }  // -1..1
  float unipolar() { return (float)(next() >> 8) * (1.0f / 16777216.0f); }     // 0..1
};

}  // namespace volt
