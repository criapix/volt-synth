#include "fm/fm_params.h"
#include <cmath>
#include "params.h"

namespace volt {

namespace {
// Parameter block fm.opN holds OP N; VCED stores OP6 first, so OP N starts at (6 - N) * 21.
int vcedOffsetOfOp(int n) { return (6 - n) * 21; }  // n = 1..6
// byte = value + offset. Algorithm 1..32 -> 0..31, transpose -24..24 -> 0..48.
int globalOffset(int g) { return g == 8 ? -1 : (g == 18 ? 24 : 0); }
// Detune -7..7 -> 0..14.
int opOffset(int k) { return k == 20 ? 7 : 0; }
}  // namespace

void vcedFromValues(const float* v, Dx7Voice& p) {
  p.fill(0);
  for (int n = 1; n <= 6; ++n) {
    const int base = kFmOp1R1 + (n - 1) * kFmOpStride;
    for (int k = 0; k < 21; ++k) p[vcedOffsetOfOp(n) + k] = (uint8_t)std::lround(v[base + k] + opOffset(k));
  }
  for (int g = 0; g < kFmGlobalCount; ++g) p[126 + g] = (uint8_t)std::lround(v[kFmPitchEnvR1 + g] + globalOffset(g));
  for (int i = 145; i < 155; ++i) p[i] = ' ';
  clampVced(p);
}

void valuesFromVced(const Dx7Voice& p, float* v) {
  for (int n = 1; n <= 6; ++n) {
    const int base = kFmOp1R1 + (n - 1) * kFmOpStride;
    for (int k = 0; k < 21; ++k) v[base + k] = (float)(p[vcedOffsetOfOp(n) + k] - opOffset(k));
    v[base + 21] = 1.0f;  // operator on
  }
  for (int g = 0; g < kFmGlobalCount; ++g) v[kFmPitchEnvR1 + g] = (float)(p[126 + g] - globalOffset(g));
  v[kEngineType] = 1.0f;
}

uint8_t opSwitchFromValues(const float* v) {
  uint8_t bits = 0;
  for (int n = 1; n <= 6; ++n)
    if (v[kFmOp1R1 + (n - 1) * kFmOpStride + 21] >= 0.5f) bits |= (uint8_t)(1 << (6 - n));
  return bits;
}

}  // namespace volt
