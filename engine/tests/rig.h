#pragma once
#include <vector>
#include "engine.h"

namespace volt {
// Drives an Engine at 48 kHz from tests.
struct Rig {
  Engine e;
  Rig() { e.setSampleRate(48000.0f); }
  void set(int id, float real) { e.post({Event::SetParam, id, norm(id, real)}); }
  void on(int note, float vel = 0.8f) { e.post({Event::NoteOn, note, vel}); }
  void off(int note) { e.post({Event::NoteOff, note, 0}); }
  // Renders `seconds` and returns the left channel.
  std::vector<float> play(float seconds, int chunk = 96) {
    std::vector<float> left, buf(2 * chunk);
    int frames = (int)(seconds * 48000.0f);
    while (frames > 0) {
      int n = frames < chunk ? frames : chunk;
      e.render(buf.data(), n);
      for (int i = 0; i < n; ++i) left.push_back(buf[2 * i]);
      frames -= n;
    }
    return left;
  }
};
// Every parameter at its default, normalized: the starting point of a test patch.
inline std::vector<float> defaultPatch() {
  std::vector<float> v(kNumParams);
  for (int i = 0; i < kNumParams; ++i) v[i] = norm(i, kParams[i].def);
  return v;
}
}  // namespace volt
