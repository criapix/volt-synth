#pragma once
#include <memory>

namespace volt {

// The four effects taken from sst-effects. Construction allocates ~17 MB and is slow:
// never construct or call setSampleRate on the audio thread. process*() are allocation-free.
class SstEffects {
 public:
  explicit SstEffects(float sampleRate);
  ~SstEffects();

  // One 16-frame block, in place. Arguments are Volt's real parameter values.
  void phaser(float* L, float* R, float rateHz, float depth, float feedback, float mix);
  void flanger(float* L, float* R, float rateHz, float depth, float feedback, float mix);
  void delay(float* L, float* R, float timeSec, float feedback, float mix);
  void reverb(float* L, float* R, float decaySec, float damp, float mix);

 private:
  struct Impl;
  std::unique_ptr<Impl> impl_;
};

}  // namespace volt
