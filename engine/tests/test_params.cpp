#include "params.h"
#include "test.h"
#include <cstring>

using namespace volt;

TEST(params_names_are_unique_and_dotted) {
  for (int i = 0; i < kNumParams; ++i) {
    CHECK(std::strchr(kParams[i].name, '.') != nullptr);
    for (int j = i + 1; j < kNumParams; ++j) CHECK(std::strcmp(kParams[i].name, kParams[j].name) != 0);
  }
}

TEST(params_defaults_are_inside_their_range) {
  for (int i = 0; i < kNumParams; ++i) {
    CHECK(kParams[i].min < kParams[i].max);
    CHECK(kParams[i].def >= kParams[i].min && kParams[i].def <= kParams[i].max);
    if (kParams[i].curve == Curve::Exp) CHECK(kParams[i].min > 0.0f);
  }
}

TEST(params_denorm_hits_the_ends_and_roundtrips) {
  for (int i = 0; i < kNumParams; ++i) {
    CHECK_NEAR(denorm(i, 0.0f), kParams[i].min, 1e-4 * (1 + std::fabs(kParams[i].min)));
    CHECK_NEAR(denorm(i, 1.0f), kParams[i].max, 1e-4 * (1 + std::fabs(kParams[i].max)));
    float v = denorm(i, 0.37f);
    CHECK_NEAR(denorm(i, norm(i, v)), v, 1e-3 * (1 + std::fabs(v)));
  }
}

TEST(params_step_values_are_whole_numbers) {
  CHECK_NEAR(denorm(kOsc1Wave, 0.40f), 1.0f, 0);   // 0..3 -> 1.2 rounds to 1
  CHECK_NEAR(denorm(kOsc1Octave, 0.5f), 0.0f, 0);  // -2..2
  CHECK_NEAR(denorm(kVoiceUnison, 1.0f), 8.0f, 0);
}

TEST(params_exp_curve_is_geometric) {
  // 20..20000 Hz: the midpoint is the geometric mean.
  CHECK_NEAR(denorm(kFilterCutoff, 0.5f), std::sqrt(20.0f * 20000.0f), 1.0);
}

TEST(params_lookup_by_name) {
  CHECK(paramIndex("filter.cutoff") == kFilterCutoff);
  CHECK(paramIndex("no.such") == -1);
}

TEST(params_oscillator_and_lfo_blocks_have_the_same_shape) {
  CHECK(kOsc2Wave - kOsc1Wave == kOscStride && kOsc3Wave - kOsc2Wave == kOscStride);
  CHECK(kOsc2Level - kOsc2Wave == kOsc1Level - kOsc1Wave);
  CHECK(kLfo2Wave - kLfo1Wave == kLfoStride);
  CHECK(kLfo2Amp - kLfo2Wave == kLfo1Amp - kLfo1Wave);
}

TEST(params_fm_blocks_follow_vced_order) {
  CHECK(paramIndex("engine.type") >= 0);
  // 22 parameters per operator: the 21 VCED bytes in order, then on/off.
  CHECK(kFmOpStride == 22);
  CHECK(kFmOp2R1 - kFmOp1R1 == kFmOpStride);
  CHECK(paramIndex("fm.op6.on") == kFmOp1R1 + 5 * kFmOpStride + 21);
  CHECK(paramIndex("fm.op1.detune") == kFmOp1R1 + 20);
  // 19 global parameters in VCED order 126..144.
  CHECK(kFmGlobalCount == 19);
  CHECK(paramIndex("fm.transpose") == kFmPitchEnvR1 + 18);
  CHECK(paramIndex("fm.algorithm") == kFmPitchEnvR1 + 8);
  // INIT VOICE defaults
  CHECK_NEAR(kParams[paramIndex("fm.op1.level")].def, 99, 0);
  CHECK_NEAR(kParams[paramIndex("fm.op2.level")].def, 0, 0);
  CHECK_NEAR(kParams[paramIndex("fm.algorithm")].def, 1, 0);
  CHECK_NEAR(kParams[paramIndex("fm.op3.detune")].def, 0, 0);
  CHECK(kParams[paramIndex("fm.op3.r2")].curve == Curve::Step);
  // 99 parameters existed before the DX7 work (1 type + 132 operator + 19 global + 12 controller added).
  CHECK(kNumParams == 99 + 164);
}
