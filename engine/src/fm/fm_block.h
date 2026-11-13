#pragma once
#include <cstdint>
#include <memory>
#include "util.h"

namespace volt {

// 6-operator FM voices (msfa, DX7-compatible). Mono output, summed into the caller's buffer.
// Audio thread: everything except the constructor and setSampleRate().
// This header must not include any msfa header (synth.h defines macros that would leak).
class FmBlock {
 public:
  FmBlock();
  ~FmBlock();
  void setSampleRate(float sr);          // not on the audio thread: fills msfa's static tables

  // Real parameter values (ParamId order). Rebuilds the msfa patch and updates sounding voices.
  void setPatch(const float* value);

  void noteOn(int slot, int note, float velocity, float gain);  // velocity 0..1
  void noteOff(int slot);                // key up: envelopes go to their release stage
  void quickRelease(int slot);           // 15 ms fade, then free (polyphony limit)
  void kill(int slot);                   // silent and free now (panic, patch change)
  bool active(int slot) const;

  void setBend(float bend, int rangeSemis);   // bend -1..1
  void setModWheel(float v);                  // 0..1
  void setAftertouch(float v);                // 0..1
  void setBreath(float v);                    // 0..1

  // Adds `frames` mono samples (any count; internally 64-sample chunks) into out.
  void render(float* out, int frames);

 private:
  struct Impl;
  std::unique_ptr<Impl> m_;
};

}  // namespace volt
