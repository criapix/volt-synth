#pragma once

namespace volt {

enum class Curve { Lin, Exp, Step };

struct ParamInfo {
  const char* name;  // permanent: preset files use it
  float min, max, def;
  Curve curve;
};

enum ParamId : int {
#define P(id, name, min, max, def, curve) id,
#include "params.def"
#undef P
  kNumParams
};

constexpr int kOscStride = kOsc2Wave - kOsc1Wave;
constexpr int kLfoStride = kLfo2Wave - kLfo1Wave;
constexpr int kFmOpStride = kFmOp2R1 - kFmOp1R1;
constexpr int kFmGlobalCount = kFmTranspose - kFmPitchEnvR1 + 1;  // 19

extern const ParamInfo kParams[kNumParams];

float denorm(int id, float normalized);  // 0..1 -> real value (clamped)
float norm(int id, float value);         // real value -> 0..1 (clamped)
int paramIndex(const char* name);        // -1 when unknown

}  // namespace volt
