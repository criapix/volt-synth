#pragma once
#include "util.h"

namespace volt {

// Residual that rounds off a unit step at phase 0. t = phase (0..1), dt = phase increment.
inline float polyBlep(float t, float dt) {
  if (t < dt) { t /= dt; return t + t - t * t - 1.0f; }
  if (t > 1.0f - dt) { t = (t - 1.0f) / dt; return t * t + t + t + 1.0f; }
  return 0.0f;
}

struct Osc {
  float phase = 0.0f;     // 0..1
  bool wrapped = false;   // true when the last tick crossed phase 1
  float wrapFrac = 0.0f;  // how far past the wrap we are, as a fraction of a sample (0..1)

  // inc = frequency / sample rate. wave: 0 saw, 1 pulse, 2 triangle, 3 sine.
  float tick(float inc, int wave, float pw) {
    inc = clampf(inc, 0.0f, 0.45f);
    float out;
    switch (wave) {
      case 0:
        out = 2.0f * phase - 1.0f;
        if (inc > 0.0f) out -= polyBlep(phase, inc);
        break;
      case 1: {
        out = phase < pw ? 1.0f : -1.0f;
        if (inc > 0.0f) {
          out += polyBlep(phase, inc);
          float t2 = phase + 1.0f - pw;
          if (t2 >= 1.0f) t2 -= 1.0f;
          out -= polyBlep(t2, inc);
        }
        break;
      }
      case 2:
        out = phase < 0.5f ? 4.0f * phase - 1.0f : 3.0f - 4.0f * phase;
        break;
      default:
        out = std::sin(6.28318530718f * phase);
        break;
    }
    phase += inc;
    wrapped = phase >= 1.0f;
    if (wrapped) { phase -= 1.0f; wrapFrac = inc > 0.0f ? phase / inc : 0.0f; }
    return out;
  }

  // Hard sync: restart as if the reset happened `frac` of a sample ago.
  void syncTo(float frac, float inc) { phase = clampf(frac * inc, 0.0f, 0.999f); }
};

}  // namespace volt
