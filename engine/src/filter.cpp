#include "filter.h"

#include "sst/filters.h"

namespace volt {

namespace sf = sst::filters;

struct QuadFilter::Impl {
  sf::QuadFilterUnitState state{};
  sf::FilterCoefficientMaker<> maker[4];
  sf::FilterUnitQFPtr fn = nullptr;
  sf::FilterType type = sf::fut_none;
  sf::FilterSubType sub = sf::st_Standard;
  float sr = 48000.0f;
  bool used[4] = {false, false, false, false};
  // Only comb filters read these, but upstream always points the state at real memory.
  float delay[4][sf::utilities::MAX_FB_COMB + sf::utilities::SincTable::FIRipol_N] = {};
};

QuadFilter::QuadFilter() : impl_(new Impl) {
  for (int l = 0; l < 4; ++l) {
    impl_->state.DB[l] = impl_->delay[l];
    impl_->state.active[l] = (int)0xffffffff;
    impl_->state.WP[l] = 0;
  }
  setSampleRate(48000.0f);
  setModel(0);
}

QuadFilter::~QuadFilter() = default;

void QuadFilter::setSampleRate(float sr) {
  impl_->sr = sr;
  for (auto& m : impl_->maker) m.setSampleRateAndBlockSize(sr, kBlock);
}

void QuadFilter::setModel(int model) {
  static const struct { sf::FilterType t; sf::FilterSubType s; } kModels[kNumFilterModels] = {
      {sf::fut_vintageladder, sf::st_vintage_type1_compensated},
      {sf::fut_lp12, sf::st_Clean},
      {sf::fut_lp24, sf::st_Clean},
      {sf::fut_obxd_4pole, sf::st_obxd4pole_24dB},
  };
  if (model < 0 || model >= kNumFilterModels) model = 0;
  impl_->type = kModels[model].t;
  impl_->sub = kModels[model].s;
  impl_->fn = sf::GetQFPtrFilterUnit(impl_->type, impl_->sub);
  for (int l = 0; l < 4; ++l) resetLane(l);
}

void QuadFilter::resetLane(int lane) {
  alignas(16) float tmp[4];
  for (int r = 0; r < sf::n_filter_registers; ++r) {
    SIMD_MM(store_ps)(tmp, impl_->state.R[r]);
    tmp[lane] = 0.0f;
    impl_->state.R[r] = SIMD_MM(load_ps)(tmp);
  }
  impl_->maker[lane].Reset();
  impl_->maker[lane].setSampleRateAndBlockSize(impl_->sr, kBlock);
}

void QuadFilter::setLane(int lane, float cutoffHz, float resonance) {
  // sst-filters takes the cutoff as a pitch in semitones relative to A440.
  const float pitch = 12.0f * std::log2(cutoffHz / 440.0f);
  impl_->maker[lane].MakeCoeffs(pitch, resonance, impl_->type, impl_->sub, nullptr, false);
  impl_->maker[lane].updateState(impl_->state, lane);
  impl_->used[lane] = true;
}

void QuadFilter::process(float (*io)[4]) {
  if (!impl_->fn) return;
  for (int s = 0; s < kBlock; ++s) {
    auto in = SIMD_MM(load_ps)(io[s]);
    SIMD_MM(store_ps)(io[s], impl_->fn(&impl_->state, in));
  }
}

void QuadFilter::endBlock() {
  for (int l = 0; l < 4; ++l)
    if (impl_->used[l]) { impl_->maker[l].updateCoefficients(impl_->state, l); impl_->used[l] = false; }
}

}  // namespace volt
