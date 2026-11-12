#include "params.h"
#include <cmath>
#include <cstring>

namespace volt {

const ParamInfo kParams[kNumParams] = {
#define P(id, name, min, max, def, curve) {name, (float)(min), (float)(max), (float)(def), Curve::curve},
#include "params.def"
#undef P
};

float denorm(int id, float n) {
  const ParamInfo& p = kParams[id];
  n = n < 0 ? 0 : (n > 1 ? 1 : n);
  switch (p.curve) {
    case Curve::Exp: return p.min * std::pow(p.max / p.min, n);
    case Curve::Step: return std::round(p.min + (p.max - p.min) * n);
    default: return p.min + (p.max - p.min) * n;
  }
}

float norm(int id, float v) {
  const ParamInfo& p = kParams[id];
  v = v < p.min ? p.min : (v > p.max ? p.max : v);
  if (p.curve == Curve::Exp) return std::log(v / p.min) / std::log(p.max / p.min);
  return (v - p.min) / (p.max - p.min);
}

int paramIndex(const char* name) {
  for (int i = 0; i < kNumParams; ++i)
    if (std::strcmp(kParams[i].name, name) == 0) return i;
  return -1;
}

}  // namespace volt
