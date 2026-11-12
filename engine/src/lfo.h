#pragma once
#include "util.h"

namespace volt {

class Lfo {
 public:
  // One control tick. wave: 0 sine, 1 triangle, 2 saw, 3 square, 4 sample & hold. Returns -1..1.
  float tick(int wave, float hz, float delaySeconds, float dt) {
    float out;
    switch (wave) {
      case 0: out = std::sin(6.28318530718f * phase_); break;
      case 1: out = phase_ < 0.5f ? 4.0f * phase_ - 1.0f : 3.0f - 4.0f * phase_; break;
      case 2: out = 1.0f - 2.0f * phase_; break;
      case 3: out = phase_ < 0.5f ? 1.0f : -1.0f; break;
      default: out = held_; break;
    }
    phase_ += hz * dt;
    if (phase_ >= 1.0f) { phase_ -= 1.0f; held_ = rng_.bipolar(); }
    if (delaySeconds > 0.0f) {
      fade_ += dt / delaySeconds;
      if (fade_ > 1.0f) fade_ = 1.0f;
    } else {
      fade_ = 1.0f;
    }
    return out * fade_;
  }

  // Call when a voice starts: the fade-in begins again and the cycle restarts.
  void restart() { phase_ = 0.0f; fade_ = 0.0f; }
  void seed(uint32_t s) { rng_.s = s | 1u; held_ = rng_.bipolar(); }

 private:
  float phase_ = 0.0f, fade_ = 1.0f, held_ = 0.5f;
  Rng rng_;
};

}  // namespace volt
