#include "fx_sst.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include "sst/basic-blocks/simd/setup.h"
#include "sst/effects/ConcreteConfig.h"
#include "sst/effects/Delay.h"
#include "sst/effects/Flanger.h"
#include "sst/effects/Phaser.h"
#include "sst/effects/Reverb2.h"

namespace volt {

namespace sfx = sst::effects;
using Cfg = sfx::core::ConcreteConfig;

namespace {

// Parameter indexes, from the fx_dump output (see docs/NOTES.md, "Efeitos").
// Phaser: Center, Feedback(-1..1), Sharpness, Rate(log2 Hz), Depth, Stereo, Mix, Width, Count, Spread, Waveform, Tone
constexpr int kPhCenter = 0, kPhFeedback = 1, kPhRate = 3, kPhDepth = 4, kPhMix = 6;
// Flanger: Mode, Waveform, Rate(log2 Hz), Depth, Count(voices), Base Pitch, Spacing, Feedback, HF Damping, Width, Mix(-1..1)
constexpr int kFlMode = 0, kFlWave = 1, kFlRate = 2, kFlDepth = 3, kFlCount = 4, kFlPitch = 5, kFlSpacing = 6,
              kFlFeedback = 7, kFlDamp = 8, kFlMix = 10;
// Delay: Left(log2 s), Right(log2 s), Feedback, Crossfeed, Low Cut, High Cut, Rate, Depth, Channel, (unused), Mix, Width
constexpr int kDlLeft = 0, kDlRight = 1, kDlFeedback = 2, kDlMix = 10;
// Reverb2: Pre-Delay(log2 s), Room Size, Decay Time(log2; RT60 = 4 * 2^v s), Diffusion, Buildup, Modulation, LF Damping, HF Damping, Width, Mix
constexpr int kRvPredelay = 0, kRvDecay = 2, kRvHfDamp = 7, kRvMix = 9;

template <typename FX>
struct Slot {
  Cfg::GlobalStorage gs;
  Cfg::EffectStorage es;
  std::unique_ptr<FX> fx;
  explicit Slot(float sr) : gs(sr), fx(std::make_unique<FX>(&gs, &es, nullptr)) {
    for (int i = 0; i < FX::numParams; ++i) fx->paramStorage[i] = fx->paramAt(i).defaultVal;
  }
  void set(int idx, float v) { fx->paramStorage[idx] = v; }
  void start() {
    fx->initialize();
    alignas(16) float l[Cfg::blockSize] = {}, r[Cfg::blockSize] = {};
    fx->processBlock(l, r);  // first block off the audio thread: lazy setup
  }
};

inline float lg(float x) { return std::log2(x); }
}  // namespace

struct SstEffects::Impl {
  Slot<sfx::phaser::Phaser<Cfg>> phaser;
  Slot<sfx::flanger::Flanger<Cfg>> flanger;
  Slot<sfx::delay::Delay<Cfg>> delay;
  Slot<sfx::reverb2::Reverb2<Cfg>> reverb;

  explicit Impl(float sr) : phaser(sr), flanger(sr), delay(sr), reverb(sr) {
    // Fixed settings Volt does not expose (decision 2). The Phaser's stage count (Count) keeps its
    // default of 4 and is never written again: raising it would allocate on the audio thread.
    phaser.set(kPhCenter, 0.0f);
    flanger.set(kFlMode, 0); flanger.set(kFlWave, 0);
    flanger.set(kFlCount, 1); flanger.set(kFlPitch, 60); flanger.set(kFlSpacing, 0);
    flanger.set(kFlDamp, 0.1f);
    flanger.set(kFlMix, 1.0f);  // Volt's own dry/wet blend is done in SstEffects::flanger (mix 0 must be exactly dry)
    delay.set(kDlFeedback, 0.0f);
    delay.set(kDlLeft, std::log2(0.375f));  // Volt's default time: no retime crossfade on a default preset
    delay.set(kDlRight, std::log2(0.375f));
    reverb.set(kRvPredelay, -5.5f);  // ~22 ms
    phaser.start(); flanger.start(); delay.start(); reverb.start();
  }
};

SstEffects::SstEffects(float sr) : impl_(std::make_unique<Impl>(sr)) {}
SstEffects::~SstEffects() = default;

void SstEffects::phaser(float* L, float* R, float rate, float depth, float fb, float mix) {
  auto& s = impl_->phaser;
  s.set(kPhRate, lg(rate));
  s.set(kPhDepth, depth);
  s.set(kPhFeedback, fb);
  s.set(kPhMix, mix);
  s.fx->processBlock(L, R);
}

void SstEffects::flanger(float* L, float* R, float rate, float depth, float fb, float mix) {
  auto& s = impl_->flanger;
  s.set(kFlRate, lg(rate));
  s.set(kFlDepth, depth);
  s.set(kFlFeedback, fb);
  float dl[Cfg::blockSize], dr[Cfg::blockSize];
  std::copy(L, L + Cfg::blockSize, dl);
  std::copy(R, R + Cfg::blockSize, dr);
  s.fx->processBlock(L, R);
  for (int i = 0; i < Cfg::blockSize; ++i) {
    L[i] = dl[i] + mix * (L[i] - dl[i]);
    R[i] = dr[i] + mix * (R[i] - dr[i]);
  }
}

void SstEffects::delay(float* L, float* R, float time, float fb, float mix) {
  auto& s = impl_->delay;
  s.set(kDlLeft, lg(time));
  s.set(kDlRight, lg(time));
  s.set(kDlFeedback, std::cbrt(fb));  // sst applies amp_to_linear (x^3): undo it so Volt feedback is a plain gain
  s.set(kDlMix, mix);
  s.fx->processBlock(L, R);
}

void SstEffects::reverb(float* L, float* R, float decay, float damp, float mix) {
  auto& s = impl_->reverb;
  s.set(kRvDecay, std::clamp(lg(decay / 4.0f), -4.0f, 6.0f));
  s.set(kRvHfDamp, damp);
  s.set(kRvMix, mix);
  s.fx->processBlock(L, R);
}

}  // namespace volt
