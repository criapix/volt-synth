#pragma once
#include <cmath>

namespace volt {

class Adsr {
 public:
  // Times in seconds, sustain 0..1, dt = seconds per tick. Cheap enough to call every block.
  void set(float attack, float decay, float sustain, float release, float dt) {
    // Attack aims above 1 so the curve arrives with a slope instead of creeping in.
    ca_ = 1.0f - std::exp(-dt * 1.466f / attack);   // 1.466 = ln(1.3 / 0.3)
    cd_ = 1.0f - std::exp(-dt * 4.6f / decay);      // 4.6 = ln(100): 99 % of the way
    cr_ = 1.0f - std::exp(-dt * 4.6f / release);
    sustain_ = sustain;
  }

  void gate(bool on) {
    if (on) state_ = Attack;
    else if (state_ != Idle) state_ = Release;
  }

  float tick() {
    switch (state_) {
      case Attack:
        level_ += (1.3f - level_) * ca_;
        if (level_ >= 1.0f) { level_ = 1.0f; state_ = Decay; }
        break;
      case Decay:
        level_ += (sustain_ - level_) * cd_;
        break;
      case Release:
        level_ -= level_ * cr_;
        if (level_ < 0.0005f) { level_ = 0.0f; state_ = Idle; }
        break;
      default:
        break;
    }
    return level_;
  }

  bool active() const { return state_ != Idle; }
  void reset() { state_ = Idle; level_ = 0.0f; }

 private:
  enum State { Idle, Attack, Decay, Release };
  State state_ = Idle;
  float level_ = 0.0f, sustain_ = 1.0f, ca_ = 1.0f, cd_ = 1.0f, cr_ = 1.0f;
};

}  // namespace volt
