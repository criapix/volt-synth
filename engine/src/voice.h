#pragma once
#include "adsr.h"
#include "lfo.h"
#include "osc.h"
#include "params.h"
#include "util.h"

namespace volt {

// What every voice needs to know about the current block.
struct BlockCtx {
  const float* p;    // smoothed real parameter values, indexed by ParamId
  float sr;          // sample rate
  float dt;          // seconds per block (kBlock / sr)
  float bend;        // pitch bend, in semitones
  float modWheel;    // 0..1
  float lfo1Global;  // this block's value of the shared LFO 1
};

class Voice {
 public:
  bool active = false;
  bool held = false;       // key is down
  bool sustained = false;  // key is up but the pedal holds it
  int note = -1;           // the key that owns this voice
  uint64_t age = 0;        // start order; smaller = older

  void seed(uint32_t s);
  // Starts (or retriggers) the voice. fromPitch: where a glide starts; pass `note` for none.
  void start(int note, float velocity, float fromPitch, float detuneSemis, float pan, float gain);
  void retarget(int note);  // legato: new pitch, envelopes keep running
  void release();
  void quickRelease();      // release with a fixed 15 ms fade (voices cut by the polyphony limit)
  void kill();              // silent immediately (panic)

  // Renders kBlock pre-filter samples into io[s][lane]. Returns the filter cutoff in Hz for this block.
  float pre(const BlockCtx& c, float (*io)[4], int lane);
  // Reads kBlock filtered samples from io[s][lane] and accumulates into the stereo bus.
  void post(const BlockCtx& c, const float (*io)[4], int lane, float* left, float* right);

  float pitch() const { return pitch_; }
  bool releasing() const { return active && !held && !sustained; }

 private:
  bool quick_ = false;
  Osc osc_[3];
  Adsr fenv_, aenv_;
  Lfo lfo1_, lfo2_;
  Rng rng_;
  float velocity_ = 1.0f, pitch_ = 60.0f, target_ = 60.0f;
  float detune_ = 0.0f, pan_ = 0.0f, gain_ = 1.0f;
  float drift_ = 0.0f, subPhase_ = 0.0f, hpf_ = 0.0f;
  float ampPrev_ = 0.0f, lfo1Val_ = 0.0f, lfo2Val_ = 0.0f;
};

}  // namespace volt
