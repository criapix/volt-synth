#include "engine.h"
#include <cstring>

namespace volt {

namespace {
enum Mode { kPoly = 0, kMono = 1, kLegato = 2, kUnison = 3 };
const float kVoiceGain = 0.35f;
const float kFmGain = 1.0f;  // calibrated in plan 6 task 7
const int kFadeSamples = 256;  // ~5 ms at 48 kHz
}  // namespace

Engine::Engine() {
  for (int i = 0; i < kNumParams; ++i) target_[i] = value_[i] = kParams[i].def;
  for (int i = 0; i < kMaxVoices; ++i) voices_[i].seed(0x9E3779B9u * (uint32_t)(i + 1));
  globalLfo_.seed(0xC0FFEEu);
  std::memset(io_, 0, sizeof(io_));
  std::memset(mono_, 0, sizeof(mono_));
  fx_ = std::make_unique<FxChain>(48000.0f);
  fm_ = std::make_unique<FmBlock>();
  setSampleRate(48000.0f);
  fm_->setPatch(value_);
}

void Engine::setSampleRate(float sr) {
  sr_ = sr;
  smooth_ = 1.0f - std::exp(-(float)kBlock / (0.010f * sr));  // 10 ms time constant
  blockPos_ = kBlock;
  for (auto& f : filters_) f.setSampleRate(sr);
  if (fx_) fx_->setSampleRate(sr);
  arp_.setSampleRate(sr);
  if (fm_) { fm_->setSampleRate(sr); fm_->setPatch(value_); }  // tables first; setPatch reads the rate tables
}

bool Engine::post(const Event& e) {
  std::lock_guard<std::mutex> lock(postMutex_);
  return queue_.push(e);
}

bool Engine::postPatch(const float* n, int count) {
  if (count > kNumParams) count = kNumParams;
  std::lock_guard<std::mutex> lock(postMutex_);
  if (queue_.freeSpace() < count + 1) return false;
  for (int i = 0; i < count; ++i) queue_.push({Event::PatchParam, i, n[i]});
  queue_.push({Event::PatchCommit, 0, 0.0f});
  return true;
}

void Engine::applyPending() {
  for (int i = 0; i < kNumParams; ++i)
    if (pendingSet_[i]) { target_[i] = value_[i] = pending_[i]; pendingSet_[i] = false; }
  syncEngineType();
  fm_->setPatch(value_);
  fmDirty_ = false;
}

void Engine::killVoice(Voice& v) {
  if (fmMode_) fm_->kill((int)(&v - voices_));
  v.kill();
}

void Engine::releaseVoice(Voice& v) {
  if (fmMode_) fm_->noteOff((int)(&v - voices_));
  v.release();
}

void Engine::quickReleaseVoice(Voice& v) {
  if (fmMode_) fm_->quickRelease((int)(&v - voices_));
  v.quickRelease();
}

void Engine::killAllVoices() {
  // Both renderers, whatever the mode: nothing may sound with the wrong one.
  for (int i = 0; i < kMaxVoices; ++i) { fm_->kill(i); voices_[i].kill(); }
}

void Engine::syncEngineType() {
  const bool fm = value_[kEngineType] >= 0.5f;
  if (fm == fmMode_) return;
  killAllVoices();
  stackSize_ = 0;
  fmMode_ = fm;
}

void Engine::render(float* out, int frames) {
  Event e;
  while (queue_.pop(e)) handle(e);
  flushFm();

  int done = 0;
  while (done < frames) {
    if (blockPos_ == kBlock) { renderBlock(); blockPos_ = 0; }
    int n = frames - done;
    if (n > kBlock - blockPos_) n = kBlock - blockPos_;
    for (int i = 0; i < n; ++i) {
      out[2 * (done + i)] = blockL_[blockPos_ + i];
      out[2 * (done + i) + 1] = blockR_[blockPos_ + i];
    }
    blockPos_ += n;
    done += n;
  }
}

void Engine::handle(const Event& e) {
  switch (e.type) {
    case Event::NoteOn:
      flushFm();
      if (e.b <= 0.0f) keyUp(e.a); else keyDown(e.a, e.b);
      break;
    case Event::NoteOff: keyUp(e.a); break;
    case Event::SetParam:
      if (e.a >= 0 && e.a < kNumParams) {
        target_[e.a] = denorm(e.a, e.b);
        if (kParams[e.a].curve == Curve::Step) value_[e.a] = target_[e.a];
        pendingSet_[e.a] = false;  // an explicit change after a patch overrides the patch's value
        if (e.a == kEngineType) { syncEngineType(); fmDirty_ = true; }  // kills voices now, patch pushed once later
        else if (e.a >= kFmOp1R1 && fmMode_) fmDirty_ = true;  // analog: the type switch re-syncs
      }
      break;
    case Event::PatchParam:
      if (e.a >= 0 && e.a < kNumParams) { pending_[e.a] = denorm(e.a, e.b); pendingSet_[e.a] = true; }
      break;
    case Event::PatchCommit: {
      bool sounding = false;
      for (auto& v : voices_) if (v.active) { sounding = true; break; }
      if (sounding) fade_ = Fade::Out;
      else { applyPending(); fade_ = Fade::None; fadeGain_ = 1.0f; }
      break;
    }
    case Event::PitchBend: bend_ = clampf(e.b, -1.0f, 1.0f); break;
    case Event::ModWheel: modWheel_ = clampf(e.b, 0.0f, 1.0f); fm_->setModWheel(modWheel_); break;
    case Event::Aftertouch: fm_->setAftertouch(clampf(e.b, 0.0f, 1.0f)); break;  // analog ignores it
    case Event::Breath: fm_->setBreath(clampf(e.b, 0.0f, 1.0f)); break;
    case Event::Sustain:
      sustain_ = e.b > 0.5f;
      if (!sustain_)
        for (auto& v : voices_) if (v.active && v.sustained) releaseVoice(v);
      break;
    case Event::AllNotesOff:
      for (auto& v : voices_) killVoice(v);
      stackSize_ = 0;
      arp_.clear();
      std::memset(keyHeld_, 0, sizeof(keyHeld_));
      break;
  }
}

void Engine::startVoice(Voice& v, int note, float velocity, float detune, float pan, float gain) {
  const bool glide = value_[kVoiceGlide] > 0.001f && anyNoteYet_ && value_[kVoiceGlideLegato] < 0.5f;
  if (!v.active) {
    const int index = (int)(&v - voices_);
    filters_[index / 4].resetLane(index % 4);
  }
  v.start(note, velocity, glide ? lastPitch_ : (float)note, detune, pan, gain);
  v.age = ++counter_;
  if (fmMode_) fm_->noteOn((int)(&v - voices_), note, velocity, kFmGain);
}

Voice* Engine::allocPoly(int note) {
  const int n = voiceLimit_;
  for (int i = 0; i < n; ++i) if (voices_[i].active && voices_[i].note == note) return &voices_[i];  // same key: reuse
  for (int i = 0; i < n; ++i) if (!voices_[i].active) return &voices_[i];
  Voice* best = nullptr;
  for (int i = 0; i < n; ++i) if (voices_[i].releasing() && (!best || voices_[i].age < best->age)) best = &voices_[i];
  if (best) return best;
  best = &voices_[0];
  for (int i = 0; i < n; ++i) if (voices_[i].age < best->age) best = &voices_[i];
  return best;
}

void Engine::noteOn(int note, float velocity) {
  const int mode = (int)value_[kVoiceMode];
  if (mode == kPoly) {
    startVoice(*allocPoly(note), note, velocity, 0.0f, 0.0f, kVoiceGain);
  } else {
    // Mono family: remember the key, play it on the first n voices.
    for (int i = 0; i < stackSize_; ++i)
      if (stack_[i] == note) { std::memmove(stack_ + i, stack_ + i + 1, sizeof(int) * (stackSize_ - i - 1)); --stackSize_; break; }
    if (stackSize_ == 16) { std::memmove(stack_, stack_ + 1, sizeof(int) * 15); --stackSize_; }
    stack_[stackSize_++] = note;

    int n = mode == kUnison && !fmMode_ ? (int)value_[kVoiceUnison] : 1;  // FM: one voice, retriggered
    if (n > voiceLimit_) n = voiceLimit_;
    const bool legato = !fmMode_ && mode == kLegato && stackSize_ > 1 && voices_[0].active && voices_[0].held;
    const float gain = kVoiceGain / std::sqrt((float)n);
    for (int i = 0; i < kMaxVoices; ++i) {
      Voice& v = voices_[i];
      if (i >= n) { if (v.active && !v.releasing()) releaseVoice(v); continue; }
      const float pos = n > 1 ? 2.0f * (float)i / (float)(n - 1) - 1.0f : 0.0f;  // -1..1
      if (legato) v.retarget(note);
      else startVoice(v, note, velocity, 0.5f * value_[kVoiceDetune] * pos, value_[kVoiceSpread] * pos, gain);
    }
  }
  lastPitch_ = (float)note;
  anyNoteYet_ = true;
}

void Engine::releaseOrSustain(Voice& v) {
  if (sustain_) { v.held = false; v.sustained = true; }
  else releaseVoice(v);
}

void Engine::noteOff(int note) {
  const int mode = (int)value_[kVoiceMode];
  if (mode == kPoly) {
    for (auto& v : voices_) if (v.active && v.held && v.note == note) releaseOrSustain(v);
    return;
  }
  bool wasTop = stackSize_ > 0 && stack_[stackSize_ - 1] == note;
  for (int i = 0; i < stackSize_; ++i)
    if (stack_[i] == note) { std::memmove(stack_ + i, stack_ + i + 1, sizeof(int) * (stackSize_ - i - 1)); --stackSize_; break; }
  if (stackSize_ == 0) {
    for (auto& v : voices_) if (v.active && v.held) releaseOrSustain(v);
  } else if (wasTop) {
    const int top = stack_[stackSize_ - 1];
    for (int i = 0; i < kMaxVoices; ++i) {
      Voice& v = voices_[i];
      if (!(v.active && v.held)) continue;
      if (fmMode_) startVoice(v, top, keyVel_[top] > 0.0f ? keyVel_[top] : 0.7f, 0.0f, 0.0f, kVoiceGain);  // msfa cannot retarget: retrigger
      else v.retarget(top);
    }
    lastPitch_ = (float)stack_[stackSize_ - 1];
  }
}

void Engine::keyDown(int note, float vel) {
  if (note < 0 || note > 127) return;
  keyHeld_[note] = true;
  keyVel_[note] = vel;
  if (arpOn_) arp_.keyDown(note, vel); else noteOn(note, vel);
}

void Engine::keyUp(int note) {
  if (note < 0 || note > 127) return;
  keyHeld_[note] = false;
  if (arpOn_) arp_.keyUp(note); else noteOff(note);
}

void Engine::noteOffNow(int note) {
  const bool pedal = sustain_;
  sustain_ = false;
  noteOff(note);
  sustain_ = pedal;
}

void Engine::runArp() {
  const bool on = value_[kArpOn] > 0.5f;
  if (on != arpOn_) {
    if (on) {
      for (int n = 0; n < 128; ++n) if (keyHeld_[n]) { noteOffNow(n); arp_.keyDown(n, keyVel_[n]); }
    } else {
      const int s = arp_.clear();
      if (s >= 0) noteOffNow(s);
      for (int n = 0; n < 128; ++n) if (keyHeld_[n]) noteOn(n, keyVel_[n]);
    }
    arpOn_ = on;
  }
  if (!arpOn_) return;
  arp_.setHold(value_[kArpHold] > 0.5f);
  const ArpSettings s{(int)value_[kArpMode], (int)value_[kArpOctaves], (int)value_[kArpDivision], value_[kArpBpm], value_[kArpGate]};
  ArpEvent ev[8];
  const int n = arp_.advance(kBlock, s, ev, 8);
  for (int i = 0; i < n; ++i) {
    if (ev[i].on) noteOn(ev[i].note, ev[i].vel); else noteOffNow(ev[i].note);
  }
}

void Engine::renderBlock() {
  const int limit = voiceLimitReq_.load(std::memory_order_relaxed);
  if (limit != voiceLimit_) {
    voiceLimit_ = limit;
    for (int i = limit; i < kMaxVoices; ++i)
      if (voices_[i].active) quickReleaseVoice(voices_[i]);
  }
  if (fade_ == Fade::Out && fadeGain_ <= 0.0f) { applyPending(); fade_ = Fade::In; }
  for (int i = 0; i < kNumParams; ++i)
    if (kParams[i].curve != Curve::Step) value_[i] += (target_[i] - value_[i]) * smooth_;

  const int model = (int)value_[kFilterModel];
  if (model != model_) { model_ = model; for (auto& f : filters_) f.setModel(model); }

  runArp();

  BlockCtx c;
  c.p = value_;
  c.sr = sr_;
  c.dt = (float)kBlock / sr_;
  c.bend = bend_ * value_[kVoiceBend];
  if (fmMode_) fm_->setBend(bend_, (int)value_[kVoiceBend]);
  c.modWheel = modWheel_;
  c.lfo1Global = globalLfo_.tick((int)value_[kLfo1Wave], value_[kLfo1Rate], 0.0f, c.dt);

  std::memset(blockL_, 0, sizeof(blockL_));
  std::memset(blockR_, 0, sizeof(blockR_));

  int active = 0;
  if (fmMode_) {
    std::memset(mono_, 0, sizeof(mono_));
    fm_->render(mono_, kBlock);
    for (int s = 0; s < kBlock; ++s) { blockL_[s] += mono_[s]; blockR_[s] += mono_[s]; }  // mono, centred
    for (int i = 0; i < kMaxVoices; ++i) {
      Voice& v = voices_[i];
      if (v.active && !fm_->active(i)) v.kill();  // the FM envelope ended
      if (v.active) ++active;
    }
  }
  for (int q = 0; q < (fmMode_ ? 0 : kMaxVoices / 4); ++q) {
    bool any = false;
    float cutoff[4] = {1000.0f, 1000.0f, 1000.0f, 1000.0f};
    for (int lane = 0; lane < 4; ++lane) {
      Voice& v = voices_[q * 4 + lane];
      if (v.active) { cutoff[lane] = v.pre(c, io_[q], lane); any = true; }
      else for (int s = 0; s < kBlock; ++s) io_[q][s][lane] = 0.0f;
    }
    if (!any) continue;
    for (int lane = 0; lane < 4; ++lane)
      if (voices_[q * 4 + lane].active) filters_[q].setLane(lane, cutoff[lane], value_[kFilterReso]);
    filters_[q].process(io_[q]);
    filters_[q].endBlock();
    for (int lane = 0; lane < 4; ++lane) {
      Voice& v = voices_[q * 4 + lane];
      if (!v.active) continue;
      v.post(c, io_[q], lane, blockL_, blockR_);
      if (v.active) ++active;
    }
  }

  fx_->process(value_, blockL_, blockR_);

  // Master volume, then a soft limiter so no preset can clip the output.
  const float vol = value_[kMasterVolume] * value_[kMasterVolume] * 2.0f;
  const float fadeStep = 1.0f / (float)kFadeSamples;
  float pk = 0.0f;
  for (int s = 0; s < kBlock; ++s) {
    if (fade_ == Fade::Out) { fadeGain_ -= fadeStep; if (fadeGain_ < 0.0f) fadeGain_ = 0.0f; }
    else if (fade_ == Fade::In) { fadeGain_ += fadeStep; if (fadeGain_ >= 1.0f) { fadeGain_ = 1.0f; fade_ = Fade::None; } }
    float l = std::tanh(blockL_[s] * vol) * fadeGain_, r = std::tanh(blockR_[s] * vol) * fadeGain_;
    blockL_[s] = l; blockR_[s] = r;
    const float a = std::fabs(l) > std::fabs(r) ? std::fabs(l) : std::fabs(r);
    if (a > pk) pk = a;
  }
  peak_.store(pk, std::memory_order_relaxed);
  activeVoices_.store(active, std::memory_order_relaxed);
}

}  // namespace volt
