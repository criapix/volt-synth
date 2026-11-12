#include "engine.h"
#include <cstring>

namespace volt {

namespace {
enum Mode { kPoly = 0, kMono = 1, kLegato = 2, kUnison = 3 };
const float kVoiceGain = 0.35f;
const int kFadeSamples = 256;  // ~5 ms at 48 kHz
}  // namespace

Engine::Engine() {
  for (int i = 0; i < kNumParams; ++i) target_[i] = value_[i] = kParams[i].def;
  for (int i = 0; i < kMaxVoices; ++i) voices_[i].seed(0x9E3779B9u * (uint32_t)(i + 1));
  globalLfo_.seed(0xC0FFEEu);
  std::memset(io_, 0, sizeof(io_));
  setSampleRate(48000.0f);
}

void Engine::setSampleRate(float sr) {
  sr_ = sr;
  smooth_ = 1.0f - std::exp(-(float)kBlock / (0.010f * sr));  // 10 ms time constant
  blockPos_ = kBlock;
  for (auto& f : filters_) f.setSampleRate(sr);
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
}

void Engine::render(float* out, int frames) {
  Event e;
  while (queue_.pop(e)) handle(e);

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
      if (e.b <= 0.0f) noteOff(e.a); else noteOn(e.a, e.b);
      break;
    case Event::NoteOff: noteOff(e.a); break;
    case Event::SetParam:
      if (e.a >= 0 && e.a < kNumParams) {
        target_[e.a] = denorm(e.a, e.b);
        if (kParams[e.a].curve == Curve::Step) value_[e.a] = target_[e.a];
        pendingSet_[e.a] = false;  // an explicit change after a patch overrides the patch's value
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
    case Event::ModWheel: modWheel_ = clampf(e.b, 0.0f, 1.0f); break;
    case Event::Sustain:
      sustain_ = e.b > 0.5f;
      if (!sustain_)
        for (auto& v : voices_) if (v.active && v.sustained) v.release();
      break;
    case Event::AllNotesOff:
      for (auto& v : voices_) v.kill();
      stackSize_ = 0;
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
}

Voice* Engine::allocPoly(int note) {
  for (auto& v : voices_) if (v.active && v.note == note) return &v;  // same key: reuse
  for (auto& v : voices_) if (!v.active) return &v;
  Voice* best = nullptr;
  for (auto& v : voices_) if (v.releasing() && (!best || v.age < best->age)) best = &v;
  if (best) return best;
  best = &voices_[0];
  for (auto& v : voices_) if (v.age < best->age) best = &v;
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

    const int n = mode == kUnison ? (int)value_[kVoiceUnison] : 1;
    const bool legato = mode == kLegato && stackSize_ > 1 && voices_[0].active && voices_[0].held;
    const float gain = kVoiceGain / std::sqrt((float)n);
    for (int i = 0; i < kMaxVoices; ++i) {
      Voice& v = voices_[i];
      if (i >= n) { if (v.active && !v.releasing()) v.release(); continue; }
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
  else v.release();
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
    for (auto& v : voices_) if (v.active && v.held) v.retarget(stack_[stackSize_ - 1]);
    lastPitch_ = (float)stack_[stackSize_ - 1];
  }
}

void Engine::renderBlock() {
  if (fade_ == Fade::Out && fadeGain_ <= 0.0f) { applyPending(); fade_ = Fade::In; }
  for (int i = 0; i < kNumParams; ++i)
    if (kParams[i].curve != Curve::Step) value_[i] += (target_[i] - value_[i]) * smooth_;

  const int model = (int)value_[kFilterModel];
  if (model != model_) { model_ = model; for (auto& f : filters_) f.setModel(model); }

  BlockCtx c;
  c.p = value_;
  c.sr = sr_;
  c.dt = (float)kBlock / sr_;
  c.bend = bend_ * value_[kVoiceBend];
  c.modWheel = modWheel_;
  c.lfo1Global = globalLfo_.tick((int)value_[kLfo1Wave], value_[kLfo1Rate], 0.0f, c.dt);

  std::memset(blockL_, 0, sizeof(blockL_));
  std::memset(blockR_, 0, sizeof(blockR_));

  int active = 0;
  for (int q = 0; q < kMaxVoices / 4; ++q) {
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
