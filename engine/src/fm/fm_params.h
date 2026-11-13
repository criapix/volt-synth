#pragma once
#include "fm/dx7_codec.h"

namespace volt {
// Real parameter values (indexed by ParamId) -> msfa patch bytes. Rounds and clamps.
void vcedFromValues(const float* value, Dx7Voice& out);
// msfa patch bytes -> real values for every fm.* parameter, and engine.type = 1.
// Other parameters (effects, controller routing) are left as they are.
void valuesFromVced(const Dx7Voice& in, float* value);
// msfa Controllers::opSwitch from the fm.opN.on parameters (bit 0 = OP6).
uint8_t opSwitchFromValues(const float* value);
}
