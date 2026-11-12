#pragma once
#include <cmath>
#include "util.h"

namespace volt {

// Gain, an asymmetric soft clipper, a one-pole tone low-pass, level compensation, dry/wet mix.
// No oversampling in v1 (known limit).
class Distortion {
 public:
  void setSampleRate(float sr) { sr_ = sr; zl_ = zr_ = 0.0f; }

  void process(float* L, float* R, float drive, float toneHz, float mix) {
    const float gain = std::pow(10.0f, (drive * 36.0f) / 20.0f);  // 0..36 dB
    const float comp = 1.0f / std::sqrt(gain);                   // keeps the level roughly constant
    const float g = 1.0f - std::exp(-6.28318530718f * toneHz / sr_);
    for (int s = 0; s < kBlock; ++s) {
      zl_ += g * (shape(L[s] * gain) - zl_);
      zr_ += g * (shape(R[s] * gain) - zr_);
      L[s] += mix * (zl_ * comp * 2.0f - L[s]);
      R[s] += mix * (zr_ * comp * 2.0f - R[s]);
    }
  }

 private:
  static float shape(float x) {
    x += 0.15f;                       // bias: a little even harmonic
    const float y = std::tanh(x);
    return y - 0.148885f;             // tanh(0.15): remove the DC the bias adds
  }
  float sr_ = 48000.0f, zl_ = 0.0f, zr_ = 0.0f;
};

}  // namespace volt
