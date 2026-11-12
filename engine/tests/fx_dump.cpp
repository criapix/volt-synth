// Prints every parameter of the sst effects Volt uses: index, name, type, min, max, default.
#include <cmath>
#include <cstdlib>
#include <cstdio>
#include <memory>
#include "sst/basic-blocks/simd/setup.h"
#include "sst/effects/ConcreteConfig.h"
#include "sst/effects/Delay.h"
#include "sst/effects/Flanger.h"
#include "sst/effects/Phaser.h"
#include "sst/effects/Reverb2.h"

namespace sfx = sst::effects;
using Cfg = sfx::core::ConcreteConfig;

template <typename FX>
void dump(const char* title) {
  Cfg::GlobalStorage gs(48000);
  Cfg::EffectStorage es;
  auto fx = std::make_unique<FX>(&gs, &es, nullptr);
  std::printf("== %s (%d params)\n", title, (int)FX::numParams);
  for (int i = 0; i < FX::numParams; ++i) {
    auto m = fx->paramAt(i);
    std::printf("%2d  %-28s type %d  min %10.4f  max %10.4f  default %10.4f  tempo %d\n", i, m.name.c_str(), (int)m.type, m.minVal, m.maxVal, m.defaultVal, (int)m.canTemposync);
  }
}

int main() {
  dump<sfx::reverb2::Reverb2<Cfg>>("Reverb2");
  dump<sfx::delay::Delay<Cfg>>("Delay");
  dump<sfx::flanger::Flanger<Cfg>>("Flanger");
  dump<sfx::phaser::Phaser<Cfg>>("Phaser");
  return 0;
}
