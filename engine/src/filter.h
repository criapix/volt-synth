#pragma once
#include <memory>
#include "util.h"

namespace volt {

constexpr int kNumFilterModels = 4;  // 0 Ladder, 1 Roland 12 dB, 2 Roland 24 dB, 3 Prophet

// One filter for four voices at once (SIMD lanes).
// Per block:  setLane() for each lane in use  ->  process()  ->  endBlock().
class QuadFilter {
 public:
  QuadFilter();
  ~QuadFilter();
  void setSampleRate(float sr);
  void setModel(int model);   // clears all four lanes
  void resetLane(int lane);   // call when a silent voice starts
  void setLane(int lane, float cutoffHz, float resonance);  // resonance 0..1
  void process(float (*io)[4]);  // io[kBlock][4], 16-byte aligned, filtered in place
  void endBlock();

 private:
  struct Impl;
  std::unique_ptr<Impl> impl_;  // allocated once in the constructor, never on the audio thread
};

}  // namespace volt
