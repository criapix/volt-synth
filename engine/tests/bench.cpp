// Renders 16 held voices for 10 seconds of audio and prints how much faster than real time it ran.
#include <chrono>
#include <cstdio>
#include "engine.h"

using namespace volt;

int main() {
  for (int model = 0; model < 4; ++model) {
    Engine e;
    e.setSampleRate(48000.0f);
    e.post({Event::SetParam, kFilterModel, norm(kFilterModel, (float)model)});
    e.post({Event::SetParam, kOsc2Level, 1.0f});
    e.post({Event::SetParam, kOsc3Level, 1.0f});
    e.post({Event::SetParam, kFilterCutoff, norm(kFilterCutoff, 2000.0f)});
    for (int n = 0; n < 16; ++n) e.post({Event::NoteOn, 36 + 3 * n, 0.8f});
    static float buf[2 * 96];
    auto t0 = std::chrono::steady_clock::now();
    for (int i = 0; i < 5000; ++i) e.render(buf, 96);  // 10 s
    double sec = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
    std::printf("model %d: %d voices, %.1fx real time, %.1f %% of one core\n", model, e.activeVoices(), 10.0 / sec, 100.0 * sec / 10.0);
  }
  return 0;
}
