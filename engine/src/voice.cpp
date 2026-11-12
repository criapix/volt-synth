#include "voice.h"

namespace volt {

namespace {
// Soft clipper: unity gain for small signals, bounded at +-1.
inline float softClip(float x) {
  x = clampf(x, -3.0f, 3.0f);
  return x * (27.0f + x * x) / (27.0f + 9.0f * x * x);
}
}  // namespace

void Voice::seed(uint32_t s) {
  rng_.s = s | 1u;
  lfo1_.seed(s * 2654435761u);
  lfo2_.seed(s * 40503u + 7u);
}

void Voice::start(int n, float velocity, float fromPitch, float detuneSemis, float pan, float gain) {
  if (!active) {
    // A fresh voice: free-running oscillators start anywhere, like an analogue synth.
    for (auto& o : osc_) o.phase = rng_.unipolar();
    subPhase_ = 0.0f; hpf_ = 0.0f; ampPrev_ = 0.0f;
    fenv_.reset(); aenv_.reset();
    lfo1_.restart(); lfo2_.restart();
  }
  active = true; held = true; sustained = false; quick_ = false;
  note = n; velocity_ = velocity;
  target_ = (float)n; pitch_ = fromPitch;
  detune_ = detuneSemis; pan_ = pan; gain_ = gain;
  fenv_.gate(true); aenv_.gate(true);
}

void Voice::retarget(int n) { note = n; target_ = (float)n; held = true; sustained = false; }

void Voice::release() {
  held = false; sustained = false;
  fenv_.gate(false); aenv_.gate(false);
}

void Voice::quickRelease() {
  release();
  quick_ = true;
}

void Voice::kill() {
  active = false; held = false; sustained = false; note = -1;
  fenv_.reset(); aenv_.reset(); ampPrev_ = 0.0f;
}

float Voice::pre(const BlockCtx& c, float (*io)[4], int lane) {
  const float* p = c.p;

  // --- control rate -------------------------------------------------------
  fenv_.set(p[kFenvAttack], p[kFenvDecay], p[kFenvSustain], p[kFenvRelease], c.dt);
  const float fe = fenv_.tick();

  const float own1 = lfo1_.tick((int)p[kLfo1Wave], p[kLfo1Rate], p[kLfo1Delay], c.dt);
  const float wheelGate = 1.0f - p[kLfo1Wheel] + p[kLfo1Wheel] * c.modWheel;
  lfo1Val_ = (p[kLfo1Global] > 0.5f ? c.lfo1Global : own1) * wheelGate;
  lfo2Val_ = lfo2_.tick((int)p[kLfo2Wave], p[kLfo2Rate], p[kLfo2Delay], c.dt);

  const float glide = p[kVoiceGlide];
  if (glide > 0.001f) pitch_ += (target_ - pitch_) * (1.0f - std::exp(-c.dt * 4.6f / glide));
  else pitch_ = target_;

  drift_ += 0.02f * (rng_.bipolar() - drift_);  // slow random walk, about +-0.1

  const float lfoPitch = 12.0f * (lfo1Val_ * p[kLfo1Pitch] * p[kLfo1Pitch] + lfo2Val_ * p[kLfo2Pitch] * p[kLfo2Pitch]);
  const float base = pitch_ + c.bend + detune_ + drift_ * p[kVoiceDrift] + lfoPitch;
  const float lfoPw = 0.45f * (lfo1Val_ * p[kLfo1Pw] + lfo2Val_ * p[kLfo2Pw]);

  float inc[3], pw[3], level[3];
  int wave[3];
  for (int k = 0; k < 3; ++k) {
    const float* o = p + kOsc1Wave + k * kOscStride;
    float n = base + 12.0f * o[kOsc1Octave - kOsc1Wave] + o[kOsc1Semi - kOsc1Wave] + 0.01f * o[kOsc1Fine - kOsc1Wave];
    float w = o[kOsc1Pw - kOsc1Wave] + lfoPw;
    if (k == 0) { n += 24.0f * fe * p[kPmodFenvPitch]; w += 0.45f * fe * p[kPmodFenvPw]; }
    inc[k] = noteToHz(n) / c.sr;
    pw[k] = clampf(w, 0.05f, 0.95f);
    wave[k] = (int)o[0];
    level[k] = o[kOsc1Level - kOsc1Wave];
  }

  const float fm = 2.0f * p[kPmodOsc2Pitch] * p[kPmodOsc2Pitch];
  const float pwm = 0.45f * p[kPmodOsc2Pw];
  const bool sync = p[kOsc1Sync] > 0.5f;
  const float subLevel = p[kMixSub], noiseLevel = p[kMixNoise];
  const float drive = 1.0f + 4.0f * p[kMixDrive];
  const float hpfG = 1.0f - std::exp(-6.28318530718f * p[kHpfCutoff] / c.sr);

  // --- audio rate ---------------------------------------------------------
  for (int s = 0; s < kBlock; ++s) {
    const float b = osc_[1].tick(inc[1], wave[1], pw[1]);
    float inc0 = inc[0] * (1.0f + fm * b);
    if (inc0 < 0.0f) inc0 = 0.0f;
    if (sync && osc_[1].wrapped) osc_[0].syncTo(osc_[1].wrapFrac, inc0);
    const float a = osc_[0].tick(inc0, wave[0], clampf(pw[0] + pwm * b, 0.05f, 0.95f));
    const float d = osc_[2].tick(inc[2], wave[2], pw[2]);

    subPhase_ += 0.5f * inc[0];
    if (subPhase_ >= 1.0f) subPhase_ -= 1.0f;
    const float sub = subPhase_ < 0.5f ? 1.0f : -1.0f;

    float x = 0.5f * (a * level[0] + b * level[1] + d * level[2] + sub * subLevel + rng_.bipolar() * noiseLevel);
    x = softClip(x * drive);
    hpf_ += hpfG * (x - hpf_);  // one-pole low-pass; subtracting it leaves the high-pass
    io[s][lane] = x - hpf_;
  }

  // --- filter cutoff for this block ----------------------------------------
  float cut = hzToNote(p[kFilterCutoff]);
  cut += 84.0f * fe * p[kFilterEnv];
  cut += (pitch_ - 60.0f) * p[kFilterKeytrack];
  cut += 48.0f * velocity_ * p[kFilterVel];
  cut += 48.0f * (lfo1Val_ * p[kLfo1Cutoff] + lfo2Val_ * p[kLfo2Cutoff]);
  float maxHz = 0.45f * c.sr;
  if (maxHz > 20000.0f) maxHz = 20000.0f;
  return clampf(noteToHz(cut), 20.0f, maxHz);
}

void Voice::post(const BlockCtx& c, const float (*io)[4], int lane, float* left, float* right) {
  const float* p = c.p;
  aenv_.set(p[kAenvAttack], p[kAenvDecay], p[kAenvSustain], quick_ ? 0.015f : p[kAenvRelease], c.dt);
  const float ae = aenv_.tick();

  const float velGain = 1.0f - p[kAenvVel] * (1.0f - velocity_);
  const float trem = 1.0f - 0.5f * (p[kLfo1Amp] * (1.0f + lfo1Val_) + p[kLfo2Amp] * (1.0f + lfo2Val_)) * 0.5f;
  const float target = ae * velGain * clampf(trem, 0.0f, 1.0f) * gain_;

  // Equal-power pan; pan_ is -1..1.
  const float angle = (pan_ + 1.0f) * 0.25f * 3.14159265359f;
  const float gl = std::cos(angle), gr = std::sin(angle);

  float g = ampPrev_;
  const float step = (target - ampPrev_) / (float)kBlock;
  for (int s = 0; s < kBlock; ++s) {
    g += step;
    const float x = io[s][lane] * g;
    left[s] += x * gl;
    right[s] += x * gr;
  }
  ampPrev_ = target;

  if (!aenv_.active()) { active = false; held = false; sustained = false; note = -1; ampPrev_ = 0.0f; }
}

}  // namespace volt
