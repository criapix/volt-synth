#include "fm/fm_block.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include "fm/fm_params.h"
#include "params.h"
// msfa (third_party/Synth_Dexed/src). Keep these after the engine headers.
#include "controllers.h"
#include "dx7note.h"
#include "EngineMsfa.h"
#include "env.h"
#include "exp2.h"
#include "freqlut.h"
// engine/src/lfo.h (volt::Lfo) shadows msfa's lfo.h on the include path, so name msfa's by path.
#include "../../../third_party/Synth_Dexed/src/lfo.h"
#include "pitchenv.h"
#include "porta.h"
#include "sin.h"

namespace volt {

struct FmBlock::Impl {
  EngineMsfa core;
  Controllers ctrl;
  ::Lfo lfo{};  // value-init: msfa's Lfo has no constructor, phase_ and randstate_ would be garbage
  Dx7Voice patch{};
  uint8_t carriers = 0x20;
  float sr = 48000.0f;

  Dx7Note note[kMaxVoices];
  bool live[kMaxVoices] = {};
  int midiNote[kMaxVoices] = {};
  int velocity[kMaxVoices] = {};
  float gain[kMaxVoices] = {};
  float fade[kMaxVoices] = {};
  float fadeStep[kMaxVoices] = {};

  float chunk[64] = {};
  int pos = 64;

  Impl() {
    ctrl.core = &core;
    ctrl.values_[kControllerPitch] = 0x2000;
    ctrl.values_[kControllerPitchRange] = 2;
    ctrl.values_[kControllerPitchStep] = 0;
    ctrl.values_[kControllerPortamentoGlissando] = 0;
    ctrl.modwheel_cc = ctrl.aftertouch_cc = ctrl.breath_cc = ctrl.foot_cc = 0;
    ctrl.masterTune = 0;
    ctrl.opSwitch = 0x3f;
    ctrl.wheel.setRange(50);
    ctrl.wheel.setTarget(1);
    ctrl.refresh();
    std::memset(patch.data(), 0, patch.size());
  }

  void computeChunk() {
    std::memset(chunk, 0, sizeof(chunk));
    const int32_t lv = lfo.getsample();
    const int32_t ld = lfo.getdelay();
    for (int s = 0; s < kMaxVoices; ++s) {
      if (!live[s]) continue;
      int32_t buf[64];
      std::memset(buf, 0, sizeof(buf));
      note[s].compute(buf, lv, ld, &ctrl);
      const float g = gain[s];
      float f = fade[s];
      const float fs = fadeStep[s];
      for (int i = 0; i < 64; ++i) {
        int32_t v = buf[i] >> 13;
        v = v > 32767 ? 32767 : (v < -32768 ? -32768 : v);
        chunk[i] += (float)v / 32768.0f * g * f;
        if (fs > 0.0f) f = f > fs ? f - fs : 0.0f;
      }
      fade[s] = f;
      bool silent = fs > 0.0f && f <= 0.0f;
      if (!silent) {
        VoiceStatus st;
        note[s].peekVoiceStatus(st);
        silent = true;
        for (int i = 0; i < 6; ++i)
          if ((carriers >> i) & 1) {
            if (!(st.ampStep[i] == 4 && st.amp[i] <= VOICE_SILENCE_LEVEL)) { silent = false; break; }
          }
      }
      if (silent) live[s] = false;
    }
    pos = 0;
  }
};

FmBlock::FmBlock() : m_(std::make_unique<Impl>()) {}
FmBlock::~FmBlock() = default;

void FmBlock::setSampleRate(float sr) {
  m_->sr = sr;
  Exp2::init(); Tanh::init(); Sin::init();
  // msfa's Freqlut/Porta tables are built once (initDone); reset the flags so a later rate change rebuilds them.
  Freqlut::initDone = false; Porta::initDone = false;
  Freqlut::init(sr); ::Lfo::init(sr); PitchEnv::init(sr); Env::init_sr(sr); Porta::init_sr(sr);
}

void FmBlock::setPatch(const float* value) {
  Impl& m = *m_;
  vcedFromValues(value, m.patch);
  m.ctrl.opSwitch = opSwitchFromValues(value);
  m.ctrl.wheel.setRange((uint8_t)std::lround(value[kFmWheelRange]));
  m.ctrl.wheel.setTarget((uint8_t)((value[kFmWheelPitch] >= 0.5f ? 1 : 0) | (value[kFmWheelAmp] >= 0.5f ? 2 : 0) |
                                   (value[kFmWheelEg] >= 0.5f ? 4 : 0)));
  m.ctrl.at.setRange((uint8_t)std::lround(value[kFmAtRange]));
  m.ctrl.at.setTarget((uint8_t)((value[kFmAtPitch] >= 0.5f ? 1 : 0) | (value[kFmAtAmp] >= 0.5f ? 2 : 0) |
                                (value[kFmAtEg] >= 0.5f ? 4 : 0)));
  m.ctrl.breath.setRange((uint8_t)std::lround(value[kFmBreathRange]));
  m.ctrl.breath.setTarget((uint8_t)((value[kFmBreathPitch] >= 0.5f ? 1 : 0) | (value[kFmBreathAmp] >= 0.5f ? 2 : 0) |
                                    (value[kFmBreathEg] >= 0.5f ? 4 : 0)));
  m.ctrl.refresh();
  m.lfo.reset(m.patch.data() + 137);
  m.carriers = FmCore::get_carrier_operators(m.patch[134]);
  for (int s = 0; s < kMaxVoices; ++s)
    if (m.live[s]) m.note[s].update(m.patch.data(), m.midiNote[s], m.velocity[s], -1, &m.ctrl);
}

void FmBlock::noteOn(int slot, int note, float velocity, float gain) {
  if (slot < 0 || slot >= kMaxVoices) return;
  Impl& m = *m_;
  const int midi = std::clamp(note + (int)m.patch[144] - 24, 0, 127);
  const int vel = std::clamp((int)std::lround(velocity * 127.0f), 1, 127);
  m.midiNote[slot] = midi;
  m.velocity[slot] = vel;
  m.lfo.keydown();
  m.note[slot].init(m.patch.data(), midi, vel, midi, -1, &m.ctrl);
  if (m.patch[136]) m.note[slot].oscSync();
  m.live[slot] = true;
  m.fade[slot] = 1.0f;
  m.fadeStep[slot] = 0.0f;
  m.gain[slot] = gain;
}

void FmBlock::noteOff(int slot) {
  if (slot < 0 || slot >= kMaxVoices) return;
  if (m_->live[slot]) m_->note[slot].keyup();
}

void FmBlock::quickRelease(int slot) {
  if (slot < 0 || slot >= kMaxVoices) return;
  m_->fadeStep[slot] = 1.0f / (0.015f * m_->sr);
}

void FmBlock::kill(int slot) {
  if (slot < 0 || slot >= kMaxVoices) return;
  m_->live[slot] = false;
}

bool FmBlock::active(int slot) const {
  return slot >= 0 && slot < kMaxVoices && m_->live[slot];
}

void FmBlock::setBend(float bend, int rangeSemis) {
  m_->ctrl.values_[kControllerPitch] = 0x2000 + (int32_t)std::lround(std::clamp(bend, -1.0f, 1.0f) * 8191.0f);
  m_->ctrl.values_[kControllerPitchRange] = rangeSemis;
}

namespace {
uint8_t cc127(float v) { return (uint8_t)std::clamp((int)std::lround(v * 127.0f), 0, 127); }
}

void FmBlock::setModWheel(float v) { m_->ctrl.modwheel_cc = cc127(v); m_->ctrl.refresh(); }
void FmBlock::setAftertouch(float v) { m_->ctrl.aftertouch_cc = cc127(v); m_->ctrl.refresh(); }
void FmBlock::setBreath(float v) { m_->ctrl.breath_cc = cc127(v); m_->ctrl.refresh(); }

void FmBlock::render(float* out, int frames) {
  Impl& m = *m_;
  while (frames > 0) {
    if (m.pos >= 64) m.computeChunk();
    const int n = std::min(frames, 64 - m.pos);
    for (int i = 0; i < n; ++i) out[i] += m.chunk[m.pos + i];
    m.pos += n;
    out += n;
    frames -= n;
  }
}

}  // namespace volt
