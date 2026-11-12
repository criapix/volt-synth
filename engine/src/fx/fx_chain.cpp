#include "fx_chain.h"
#include <cstring>
#include "params.h"

namespace volt {

namespace {
const int kOnParam[6] = {kFxDistOn, kFxPhaserOn, kFxFlangerOn, kFxChorusOn, kFxDelayOn, kFxReverbOn};
}

FxChain::FxChain(float sr) { setSampleRate(sr); }

void FxChain::setSampleRate(float sr) {
  sst_ = std::make_unique<SstEffects>(sr);
  dist_.setSampleRate(sr);
  chorus_.setSampleRate(sr);
  fadeStep_ = 1.0f / (0.021f * sr);
  std::memset(gate_, 0, sizeof(gate_));
}

void FxChain::runSlot(int slot, const float* p, float* L, float* R) {
  switch (slot) {
    case 0: dist_.process(L, R, p[kFxDistDrive], p[kFxDistTone], p[kFxDistMix]); break;
    case 1: sst_->phaser(L, R, p[kFxPhaserRate], p[kFxPhaserDepth], p[kFxPhaserFb], p[kFxPhaserMix]); break;
    case 2: sst_->flanger(L, R, p[kFxFlangerRate], p[kFxFlangerDepth], p[kFxFlangerFb], p[kFxFlangerMix]); break;
    case 3: chorus_.process(L, R, (int)p[kFxChorusMode], p[kFxChorusMix]); break;
    case 4: sst_->delay(L, R, p[kFxDelayTime], p[kFxDelayFb], p[kFxDelayMix]); break;
    case 5: sst_->reverb(L, R, p[kFxReverbDecay], p[kFxReverbDamp], p[kFxReverbMix]); break;
  }
}

void FxChain::process(const float* p, float* L, float* R) {
  for (int k = 0; k < kSlots; ++k) {
    const bool on = p[kOnParam[k]] > 0.5f;
    float& g = gate_[k];
    if (!on && g <= 0.0f) continue;  // off and faded out: no CPU, signal untouched
    if (on && g >= 1.0f) { runSlot(k, p, L, R); continue; }
    alignas(16) float dl[kBlock], dr[kBlock];
    std::memcpy(dl, L, sizeof(dl));
    std::memcpy(dr, R, sizeof(dr));
    runSlot(k, p, L, R);
    for (int s = 0; s < kBlock; ++s) {
      g += on ? fadeStep_ : -fadeStep_;
      g = clampf(g, 0.0f, 1.0f);
      L[s] = dl[s] + g * (L[s] - dl[s]);
      R[s] = dr[s] + g * (R[s] - dr[s]);
    }
  }
}

}  // namespace volt
