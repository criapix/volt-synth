#include "dsp_probe.h"
#include "fx/fx_chain.h"
#include "params.h"
#include "test.h"
#include <vector>

using namespace volt;

namespace {
struct FxRig {
  FxChain fx{48000.0f};
  float p[kNumParams];
  FxRig() { for (int i = 0; i < kNumParams; ++i) p[i] = kParams[i].def; }
  // Feeds `in` (mono, copied to both sides) and returns left and right outputs.
  std::pair<std::vector<float>, std::vector<float>> run(const std::vector<float>& in) {
    std::vector<float> l(in), r(in);
    const size_t n = in.size() / kBlock * kBlock;
    for (size_t i = 0; i < n; i += kBlock) fx.process(p, l.data() + i, r.data() + i);
    l.resize(n); r.resize(n);
    return {l, r};
  }
};
std::vector<float> noise(int n, float amp = 0.3f) { Rng g; std::vector<float> v(n); for (auto& x : v) x = amp * g.bipolar(); return v; }
std::vector<float> sine(int n, float hz, float amp = 0.5f) { std::vector<float> v(n); for (int i = 0; i < n; ++i) v[i] = amp * std::sin(6.28318530718f * hz * i / 48000.0f); return v; }
std::vector<float> impulse(int n) { std::vector<float> v(n, 0.0f); v[0] = 1.0f; return v; }
double diffRms(const std::vector<float>& a, const std::vector<float>& b) { double s = 0; for (size_t i = 0; i < a.size(); ++i) s += (a[i] - b[i]) * (a[i] - b[i]); return std::sqrt(s / a.size()); }
const int kOn[6] = {kFxDistOn, kFxPhaserOn, kFxFlangerOn, kFxChorusOn, kFxDelayOn, kFxReverbOn};
const int kMix[6] = {kFxDistMix, kFxPhaserMix, kFxFlangerMix, kFxChorusMix, kFxDelayMix, kFxReverbMix};
}  // namespace

TEST(fx_all_off_is_bit_identical) {
  FxRig r;
  auto in = noise(48000);
  auto [l, rr] = r.run(in);
  for (size_t i = 0; i < l.size(); ++i) { CHECK(l[i] == in[i]); if (l[i] != in[i]) break; }
}

TEST(fx_each_effect_at_zero_mix_leaves_the_signal_almost_unchanged) {
  for (int k = 0; k < 6; ++k) {
    FxRig r;
    r.p[kOn[k]] = 1; r.p[kMix[k]] = 0;
    auto in = noise(48000);
    auto [l, rr] = r.run(in);
    std::vector<float> settled(in.begin() + 4800, in.begin() + l.size()), out(l.begin() + 4800, l.end());
    CHECK(diffRms(out, settled) < 0.02 * probe::rms(settled));
  }
}

TEST(fx_each_effect_changes_the_signal) {
  for (int k = 0; k < 6; ++k) {
    FxRig r;
    r.p[kOn[k]] = 1; r.p[kMix[k]] = 1.0f;
    if (k == 0) r.p[kFxDistDrive] = 0.8f;
    auto in = sine(48000 * 2, 220.0f);
    auto [l, rr] = r.run(in);
    std::vector<float> a(in.begin() + 48000, in.begin() + l.size()), b(l.begin() + 48000, l.end());
    CHECK(diffRms(b, a) > 0.05 * probe::rms(a));
    CHECK(probe::finite(l) && probe::finite(rr));
  }
}

TEST(fx_delay_echo_arrives_at_the_set_time) {
  FxRig r;
  r.p[kFxDelayOn] = 1; r.p[kFxDelayTime] = 0.25f; r.p[kFxDelayFb] = 0.0f; r.p[kFxDelayMix] = 0.5f;
  r.run(std::vector<float>(48000, 0.0f));  // let the delay finish retiming to 0.25 s (it crossfades on a time change)
  auto [l, rr] = r.run(impulse(48000));
  size_t best = 1000;
  for (size_t i = 1000; i < l.size(); ++i) if (std::fabs(l[i]) > std::fabs(l[best])) best = i;
  CHECK_NEAR((double)best, 12000.0, 48.0);  // within 1 ms
  CHECK(std::fabs(l[best]) > 0.1f);
}

TEST(fx_delay_feedback_repeats_quieter) {
  FxRig r;
  r.p[kFxDelayOn] = 1; r.p[kFxDelayTime] = 0.25f; r.p[kFxDelayFb] = 0.5f; r.p[kFxDelayMix] = 0.5f;
  r.run(std::vector<float>(48000, 0.0f));  // let the delay finish retiming
  auto [l, rr] = r.run(impulse(48000));
  auto peakNear = [&](size_t c) { float m = 0; for (size_t i = c - 200; i < c + 200; ++i) m = std::fmax(m, std::fabs(l[i])); return m; };
  const float e1 = peakNear(12000), e2 = peakNear(24000);
  CHECK(e2 > 0.2f * e1 && e2 < 0.8f * e1);
}

TEST(fx_reverb_decay_sets_the_tail_length) {
  auto tail = [](float decay) {
    FxRig r;
    r.p[kFxReverbOn] = 1; r.p[kFxReverbDecay] = decay; r.p[kFxReverbMix] = 1.0f;
    auto [l, rr] = r.run(impulse(48000 * 3));
    double e = 0;
    for (size_t i = 72000; i < 96000; ++i) e += (double)l[i] * l[i];  // 1.5 .. 2.0 s after the impulse
    return e;
  };
  CHECK(tail(6.0f) > 4.0 * tail(1.0f));
}

TEST(fx_reverb_damping_darkens_the_tail) {
  auto highs = [](float damp) {
    FxRig r;
    r.p[kFxReverbOn] = 1; r.p[kFxReverbDecay] = 3.0f; r.p[kFxReverbDamp] = damp; r.p[kFxReverbMix] = 1.0f;
    auto [l, rr] = r.run(noise(48000));
    std::vector<float> t(l.begin() + 24000, l.end());
    double hp = 0;  // energy of the first difference: a crude high-frequency measure
    for (size_t i = 1; i < t.size(); ++i) hp += (double)(t[i] - t[i - 1]) * (t[i] - t[i - 1]);
    return hp / (probe::rms(t) * probe::rms(t) * t.size());
  };
  CHECK(highs(1.0f) < 0.8 * highs(0.0f));
}

TEST(fx_chorus_widens_a_mono_input) {
  FxRig r;
  r.p[kFxChorusOn] = 1; r.p[kFxChorusMix] = 1.0f;
  auto [l, rr] = r.run(sine(48000, 440.0f));
  std::vector<float> a(l.begin() + 4800, l.end()), b(rr.begin() + 4800, rr.end());
  CHECK(diffRms(a, b) > 0.01);
}

TEST(fx_distortion_adds_harmonics_with_drive) {
  auto third = [](float drive) {
    FxRig r;
    r.p[kFxDistOn] = 1; r.p[kFxDistDrive] = drive; r.p[kFxDistTone] = 12000.0f;
    auto [l, rr] = r.run(sine(48000, 200.0f, 0.5f));
    std::vector<float> t(l.begin() + 4800, l.end());
    return probe::tone(t, 600.0, 48000.0) / probe::tone(t, 200.0, 48000.0);
  };
  CHECK(third(0.0f) < 0.05);
  CHECK(third(1.0f) > 0.1);
}

TEST(fx_switching_an_effect_off_fades_instead_of_clicking) {
  FxRig r;
  r.p[kFxDistOn] = 1; r.p[kFxDistDrive] = 1.0f;
  auto in = sine(48000, 220.0f, 0.5f);
  std::vector<float> l(in), rr(in);
  for (size_t i = 0; i < 24000; i += kBlock) r.fx.process(r.p, l.data() + i, rr.data() + i);
  r.p[kFxDistOn] = 0;
  for (size_t i = 24000; i < 48000; i += kBlock) r.fx.process(r.p, l.data() + i, rr.data() + i);
  // The distorted signal has steep edges of its own, so compare the steepest step after the switch
  // with the steepest step before it; an abrupt switch would add a jump of ~0.5 on top.
  double before = 0, after = 0;
  for (size_t i = 20000; i < 24000; ++i) before = std::fmax(before, std::fabs(l[i] - l[i - 1]));
  for (size_t i = 24000; i < 27000; ++i) after = std::fmax(after, std::fabs(l[i] - l[i - 1]));
  CHECK(after < 1.5 * before);
}

TEST(fx_everything_on_stays_finite_and_bounded) {
  FxRig r;
  for (int k = 0; k < 6; ++k) { r.p[kOn[k]] = 1; r.p[kMix[k]] = 1.0f; }
  r.p[kFxDelayFb] = 0.95f; r.p[kFxPhaserFb] = 1.0f; r.p[kFxFlangerFb] = 1.0f; r.p[kFxReverbDecay] = 12.0f;
  auto [l, rr] = r.run(noise(48000 * 5, 0.9f));
  CHECK(probe::finite(l) && probe::finite(rr));
  CHECK(probe::peak(l) < 8.0 && probe::peak(rr) < 8.0);  // the engine's limiter follows; runaway is the failure
}
