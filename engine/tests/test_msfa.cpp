#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>
#include "dsp_probe.h"
#include "test.h"
// msfa (third_party/Synth_Dexed/src)
#include "controllers.h"
#include "dx7note.h"
#include "EngineMsfa.h"
#include "env.h"
#include "exp2.h"
#include "freqlut.h"
// engine/src/lfo.h (volt::Lfo) shadows msfa's lfo.h on the include path, so name msfa's by path.
#include "../../third_party/Synth_Dexed/src/lfo.h"
#include "pitchenv.h"
#include "porta.h"
#include "sin.h"

namespace {
void msfaInit(float sr) {
  Exp2::init(); Tanh::init(); Sin::init();
  Freqlut::init(sr); Lfo::init(sr); PitchEnv::init(sr); Env::init_sr(sr); Porta::init_sr(sr);
}

// DX7 INIT VOICE, VCED layout (OP6 first). Only OP1 (the last block) has output level 99.
void initVoice(uint8_t p[156]) {
  std::memset(p, 0, 156);
  for (int op = 0; op < 6; ++op) {
    uint8_t* o = p + op * 21;
    o[0] = o[1] = o[2] = o[3] = 99;
    o[4] = o[5] = o[6] = 99; o[7] = 0;
    o[8] = 39;
    o[16] = op == 5 ? 99 : 0;
    o[18] = 1;   // coarse 1 = ratio 1.00
    o[20] = 7;   // detune centre
  }
  p[126] = p[127] = p[128] = p[129] = 99;
  p[130] = p[131] = p[132] = p[133] = 50;
  p[136] = 1;           // osc key sync
  p[137] = 35;          // LFO speed
  p[141] = 1;           // LFO key sync
  p[143] = 3;           // LFO pitch-mod sensitivity
  p[144] = 24;          // transpose C3
  std::memcpy(p + 145, "INIT VOICE", 10);
}
}  // namespace

TEST(msfa_init_voice_plays_a_sine_at_the_note_pitch) {
  const float sr = 48000.0f;
  msfaInit(sr);
  EngineMsfa core;
  Controllers c;
  c.values_[kControllerPitch] = 0x2000;
  c.values_[kControllerPitchRange] = 2;
  c.values_[kControllerPitchStep] = 0;
  c.values_[kControllerPortamentoGlissando] = 0;
  c.modwheel_cc = c.aftertouch_cc = c.breath_cc = c.foot_cc = 0;
  c.masterTune = 0;
  c.opSwitch = 0x3f;
  c.core = &core;
  c.refresh();

  uint8_t patch[156];
  initVoice(patch);
  ::Lfo lfo;
  lfo.reset(patch + 137);
  Dx7Note note;
  note.init(patch, 69, 100, 69, -1, &c);   // A4

  std::vector<float> out;
  int32_t buf[64];
  for (int chunk = 0; chunk < 750; ++chunk) {   // 1 s
    std::memset(buf, 0, sizeof(buf));
    note.compute(buf, lfo.getsample(), lfo.getdelay(), &c);
    for (int i = 0; i < 64; ++i) {
      int32_t s = buf[i] >> 13;
      s = s > 32767 ? 32767 : (s < -32768 ? -32768 : s);
      out.push_back((float)s / 32768.0f);
    }
  }
  std::vector<float> tail(out.begin() + 24000, out.end());
  CHECK(probe::finite(tail));
  CHECK(probe::rms(tail) > 0.05);
  CHECK_NEAR(probe::zeroCrossHz(tail, 48000.0), 440.0, 2.0);
}
