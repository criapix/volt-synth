#pragma once
#include <memory>
#include "chorus.h"
#include "distortion.h"
#include "fx_sst.h"
#include "util.h"

namespace volt {

// distortion -> phaser -> flanger -> chorus -> delay -> reverb, each with on/off and a 21 ms crossfade.
// Construction allocates (~17 MB): never on the audio thread. process() is allocation-free.
class FxChain {
 public:
  explicit FxChain(float sampleRate);
  // Rebuilds the sst effects (allocates). Not concurrent with process().
  void setSampleRate(float sr);
  // One kBlock stereo block in place. p = real parameter values indexed by ParamId.
  void process(const float* p, float* L, float* R);

 private:
  static constexpr int kSlots = 6;
  void runSlot(int slot, const float* p, float* L, float* R);
  std::unique_ptr<SstEffects> sst_;
  Distortion dist_;
  Chorus chorus_;
  float gate_[kSlots] = {};
  float fadeStep_ = 1.0f / 1024.0f;
};

}  // namespace volt
