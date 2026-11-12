#pragma once
#include <cstring>
#include "util.h"

namespace volt {

struct ArpSettings {
  int mode;      // 0 up, 1 down, 2 up-down, 3 random, 4 as played
  int octaves;   // 1..4
  int division;  // 0 1/4, 1 1/8, 2 1/16, 3 1/4T, 4 1/8T, 5 1/16T
  float bpm;
  float gate;    // 0.05..1 of a step
};

struct ArpEvent {
  bool on;
  int note;
  float vel;
  int at;  // sample offset inside the advance() call
};

// Turns held keys into a timed note sequence. Allocation-free; audio thread only.
class Arp {
 public:
  static constexpr int kMaxKeys = 16;

  void setSampleRate(float sr) { sr_ = sr; }

  void keyDown(int note, float vel) {
    if (note < 0 || note > 127) return;
    if (hold_ && !anyDown()) heldN_ = 0;  // latched chord + a fresh press: start a new chord
    down_[note] = true;
    remove(note);
    if (heldN_ == kMaxKeys) remove(held_[0]);
    held_[heldN_] = note;
    vel_[heldN_] = vel;
    ++heldN_;
  }

  void keyUp(int note) {
    if (note < 0 || note > 127) return;
    down_[note] = false;
    if (!hold_) remove(note);
  }

  void setHold(bool on) {
    if (hold_ && !on)
      for (int i = heldN_ - 1; i >= 0; --i) if (!down_[held_[i]]) remove(held_[i]);
    hold_ = on;
  }

  // Forgets everything. Returns the note still sounding (the caller releases it), or -1.
  int clear() {
    const int s = sounding_;
    heldN_ = 0; sounding_ = -1; running_ = false;
    std::memset(down_, 0, sizeof(down_));
    return s;
  }

  int advance(int samples, const ArpSettings& s, ArpEvent* out, int maxOut) {
    int n = 0;
    if (heldN_ == 0) {
      if (sounding_ >= 0 && n < maxOut) out[n++] = {false, sounding_, 0.0f, 0};
      sounding_ = -1;
      running_ = false;
      return n;
    }
    static const double kBeats[6] = {1.0, 0.5, 0.25, 2.0 / 3.0, 1.0 / 3.0, 1.0 / 6.0};
    const double stepLen = (double)sr_ * 60.0 / (double)s.bpm * kBeats[clampi(s.division, 0, 5)];
    if (!running_) { running_ = true; stepIn_ = 0; frac_ = 0.0; index_ = 0; }

    int t = 0;
    while (true) {
      const int toOff = sounding_ >= 0 ? offIn_ : 1 << 30;
      const int next = toOff < stepIn_ ? toOff : stepIn_;
      if (next >= samples - t) {
        const int left = samples - t;
        stepIn_ -= left;
        if (sounding_ >= 0) offIn_ -= left;
        return n;
      }
      t += next;
      stepIn_ -= next;
      if (sounding_ >= 0) offIn_ -= next;
      if (sounding_ >= 0 && offIn_ <= 0 && n < maxOut) { out[n++] = {false, sounding_, 0.0f, t}; sounding_ = -1; }
      if (stepIn_ <= 0) {
        if (sounding_ >= 0 && n < maxOut) { out[n++] = {false, sounding_, 0.0f, t}; sounding_ = -1; }
        float vel = 1.0f;
        const int note = pick(s, vel);
        if (n < maxOut) out[n++] = {true, note, vel, t};
        sounding_ = note;
        frac_ += stepLen;
        stepIn_ = (int)frac_;
        frac_ -= stepIn_;
        offIn_ = (int)(s.gate * (float)stepIn_);
        if (offIn_ < 1) offIn_ = 1;
      }
    }
  }

 private:
  static int clampi(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

  bool anyDown() const { for (bool d : down_) if (d) return true; return false; }

  void remove(int note) {
    for (int i = 0; i < heldN_; ++i)
      if (held_[i] == note) {
        std::memmove(held_ + i, held_ + i + 1, sizeof(int) * (heldN_ - i - 1));
        std::memmove(vel_ + i, vel_ + i + 1, sizeof(float) * (heldN_ - i - 1));
        --heldN_;
        return;
      }
  }

  // The next note of the pattern, built from the held keys each step (cheap: at most 64 entries).
  int pick(const ArpSettings& s, float& vel) {
    int base[kMaxKeys];
    float bvel[kMaxKeys];
    std::memcpy(base, held_, sizeof(int) * heldN_);
    std::memcpy(bvel, vel_, sizeof(float) * heldN_);
    if (s.mode != 4)  // pitch order (insertion sort, tiny)
      for (int i = 1; i < heldN_; ++i)
        for (int j = i; j > 0 && base[j] < base[j - 1]; --j) {
          int tn = base[j]; base[j] = base[j - 1]; base[j - 1] = tn;
          float tv = bvel[j]; bvel[j] = bvel[j - 1]; bvel[j - 1] = tv;
        }
    int up[kMaxKeys * 4];
    float upVel[kMaxKeys * 4];
    int len = 0;
    const int oct = clampi(s.octaves, 1, 4);
    for (int o = 0; o < oct; ++o)
      for (int k = 0; k < heldN_; ++k) {
        int note = base[k] + 12 * o;
        if (note > 127) continue;
        up[len] = note; upVel[len] = bvel[k]; ++len;
      }
    if (len == 0) { vel = 0.0f; return base[0]; }
    int i;
    switch (s.mode) {
      case 1: i = len - 1 - (index_ % len); break;
      case 2: {
        const int cycle = len > 1 ? 2 * len - 2 : 1;
        const int c = index_ % cycle;
        i = c < len ? c : cycle - c;
        break;
      }
      case 3: i = (int)(rng_.next() % (uint32_t)len); break;
      default: i = index_ % len; break;
    }
    ++index_;
    vel = upVel[i];
    return up[i];
  }

  float sr_ = 48000.0f;
  int held_[kMaxKeys];
  float vel_[kMaxKeys];
  int heldN_ = 0;
  bool down_[128] = {};
  bool hold_ = false;
  bool running_ = false;
  int sounding_ = -1;
  int stepIn_ = 0, offIn_ = 0, index_ = 0;
  double frac_ = 0.0;
  Rng rng_;
};

}  // namespace volt
