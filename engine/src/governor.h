#pragma once
#include <cstdint>
#include "util.h"

namespace volt {

// Lowers the polyphony when the audio callback runs out of time and raises it back once there is
// room again. Called from the audio callback: no allocation, no locks.
class LoadGovernor {
 public:
  static constexpr int kMin = 8, kStep = 4;
  static constexpr float kHigh = 0.80f;      // load (fraction of the callback period) that counts as overload
  static constexpr float kXrunLoad = 0.50f;  // an xrun above this load also counts
  static constexpr float kLow = 0.40f;       // load that counts as room to grow
  static constexpr float kHighTime = 0.25f;  // seconds of overload before a drop
  static constexpr float kLowTime = 10.0f;   // seconds of room before a raise
  static constexpr float kHold = 2.0f;       // seconds after a drop before the next one

  // load: smoothed engine load; xruns: the stream's running xrun count; dt: seconds since the last call.
  int update(float load, int32_t xruns, float dt) {
    const bool newXrun = lastXruns_ >= 0 && xruns > lastXruns_;
    lastXruns_ = xruns;  // also resets when a reopened stream counts from zero
    sinceDrop_ += dt;
    high_ = load > kHigh ? high_ + dt : 0.0f;
    low_ = load < kLow ? low_ + dt : 0.0f;
    const bool overloaded = high_ >= kHighTime || (newXrun && load > kXrunLoad);
    if (overloaded && limit_ > kMin && sinceDrop_ >= kHold) {
      limit_ -= kStep;
      sinceDrop_ = 0.0f; high_ = 0.0f; low_ = 0.0f;
    } else if (low_ >= kLowTime && limit_ < kMaxVoices) {
      limit_ += kStep;
      low_ = 0.0f;
    }
    return limit_;
  }

  int limit() const { return limit_; }

 private:
  int limit_ = kMaxVoices;
  int32_t lastXruns_ = -1;
  float sinceDrop_ = 1e9f, high_ = 0.0f, low_ = 0.0f;
};

}  // namespace volt
