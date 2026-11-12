#pragma once
#include <cmath>
#include <cstring>
#include "util.h"

namespace volt {

// Juno-style stereo chorus: one delay line, two taps swept in opposite phase by a triangle LFO,
// a dark wet signal (bucket-brigade character).
class Chorus {
 public:
  void setSampleRate(float sr) {
    sr_ = sr;
    lp_ = 1.0f - std::exp(-6.28318530718f * 9000.0f / sr);
    std::memset(buf_, 0, sizeof(buf_));
    pos_ = 0; phase_ = 0.0f; wl_ = wr_ = 0.0f;
  }

  // mode 0 I, 1 II, 2 I+II. In place, kBlock frames.
  void process(float* L, float* R, int mode, float mix) {
    static const float kRate[3] = {0.513f, 0.863f, 9.75f};
    static const float kMinMs[3] = {1.66f, 1.66f, 3.3f};
    static const float kMaxMs[3] = {5.35f, 5.35f, 3.7f};
    if (mode < 0 || mode > 2) mode = 0;
    const float inc = kRate[mode] / sr_;
    const float lo = kMinMs[mode] * 0.001f * sr_, hi = kMaxMs[mode] * 0.001f * sr_;
    for (int s = 0; s < kBlock; ++s) {
      const float mono = 0.5f * (L[s] + R[s]);
      buf_[pos_] = mono;
      const float tri = phase_ < 0.5f ? 4.0f * phase_ - 1.0f : 3.0f - 4.0f * phase_;  // -1..1
      phase_ += inc;
      if (phase_ >= 1.0f) phase_ -= 1.0f;
      const float mid = 0.5f * (lo + hi), half = 0.5f * (hi - lo);
      wl_ += lp_ * (read(mid + half * tri) - wl_);
      wr_ += lp_ * (read(mid - half * tri) - wr_);
      // The dry signal never fully disappears, as on the hardware.
      L[s] = L[s] * (1.0f - 0.5f * mix) + wl_ * mix;
      R[s] = R[s] * (1.0f - 0.5f * mix) + wr_ * mix;
      pos_ = (pos_ + 1) & (kSize - 1);
    }
  }

 private:
  static constexpr int kSize = 1024;  // > 5.35 ms at 96 kHz (514 samples)
  float read(float delay) const {
    float p = (float)pos_ - delay;
    while (p < 0.0f) p += (float)kSize;
    const int i = (int)p;
    const float f = p - (float)i;
    return buf_[i & (kSize - 1)] * (1.0f - f) + buf_[(i + 1) & (kSize - 1)] * f;
  }
  float buf_[kSize] = {};
  float sr_ = 48000.0f, lp_ = 0.5f, phase_ = 0.0f, wl_ = 0.0f, wr_ = 0.0f;
  int pos_ = 0;
};

}  // namespace volt
