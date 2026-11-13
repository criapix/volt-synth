#pragma once
#include <atomic>
#include <mutex>
#include <memory>
#include "arp.h"
#include "event_queue.h"
#include "filter.h"
#include "fm/fm_block.h"
#include "fx/fx_chain.h"
#include "params.h"
#include "voice.h"

namespace volt {

class Engine {
 public:
  Engine();

  // Not concurrent with render(): call before the stream starts or after it stopped.
  void setSampleRate(float sr);

  // Any non-audio thread. Returns false when the queue is full (the event is dropped).
  bool post(const Event& e);

  // Audio thread only. Writes `frames` interleaved stereo float frames.
  void render(float* out, int frames);

  // Any thread.
  int activeVoices() const { return activeVoices_.load(std::memory_order_relaxed); }
  // Any thread. How many voices new notes may use (4..16). Voices above a lowered limit fade out in 15 ms.
  void setVoiceLimit(int n) { voiceLimitReq_.store(n < 4 ? 4 : (n > kMaxVoices ? kMaxVoices : n), std::memory_order_relaxed); }
  int voiceLimit() const { return voiceLimitReq_.load(std::memory_order_relaxed); }
  float peak() const { return peak_.load(std::memory_order_relaxed); }  // last block's peak, 0..1

  // Any non-audio thread. Loads a whole patch (normalized values for ids 0..count-1) as one unit:
  // either every value is queued or none is (returns false). Sounding voices fade out, the patch
  // snaps into place, and they fade back in. A later post() of the same parameter wins.
  bool postPatch(const float* normalized, int count);

  // Tests only, not thread-safe: the smoothed real value the voices read.
  float value(int id) const { return value_[id]; }

 private:
  void renderBlock();
  void applyPending();
  void handle(const Event& e);
  void noteOn(int note, float velocity);
  void noteOff(int note);
  void keyDown(int note, float vel);
  void keyUp(int note);
  void noteOffNow(int note);  // release ignoring the sustain pedal (arpeggio steps)
  void runArp();
  void releaseOrSustain(Voice& v);
  // Every voice state change goes through these so FM mode mirrors it to fm_ (Voice keeps the bookkeeping).
  void releaseVoice(Voice& v);
  void quickReleaseVoice(Voice& v);
  void killVoice(Voice& v);
  void killAllVoices();
  void syncEngineType();  // fmMode_ follows engine.type; a change silences every voice
  Voice* allocPoly(int note);
  void startVoice(Voice& v, int note, float velocity, float detune, float pan, float gain);

  EventQueue queue_;
  std::mutex postMutex_;  // serialises producers; never taken by the audio thread

  float sr_ = 48000.0f;
  float target_[kNumParams];  // real values the parameters are heading to
  float value_[kNumParams];   // smoothed real values the voices read
  float smooth_ = 1.0f;
  float pending_[kNumParams];
  bool pendingSet_[kNumParams] = {};
  enum class Fade { None, Out, In };
  Fade fade_ = Fade::None;
  float fadeGain_ = 1.0f;

  std::unique_ptr<FxChain> fx_;  // allocated in the constructor (~17 MB), off the audio thread
  std::unique_ptr<FmBlock> fm_;  // allocated in the constructor, off the audio thread
  bool fmMode_ = false;
  bool fmDirty_ = false;  // fm.* or engine.type changed: setPatch once after the event drain
  void flushFm() { if (fmDirty_) { fm_->setPatch(value_); fmDirty_ = false; } }
  alignas(16) float mono_[kBlock];
  Arp arp_;
  bool arpOn_ = false;
  bool keyHeld_[128] = {};
  float keyVel_[128] = {};

  Voice voices_[kMaxVoices];
  uint64_t counter_ = 0;
  int stack_[16];  // held keys in mono modes, oldest first
  int stackSize_ = 0;
  float lastPitch_ = 60.0f;
  bool anyNoteYet_ = false;
  bool sustain_ = false;
  float bend_ = 0.0f, modWheel_ = 0.0f;
  Lfo globalLfo_;
  QuadFilter filters_[kMaxVoices / 4];
  int model_ = -1;

  alignas(16) float io_[kMaxVoices / 4][kBlock][4];
  float blockL_[kBlock], blockR_[kBlock];
  int blockPos_ = kBlock;  // kBlock = nothing buffered

  std::atomic<int> voiceLimitReq_{kMaxVoices};
  int voiceLimit_ = kMaxVoices;  // audio thread's copy
  std::atomic<int> activeVoices_{0};
  std::atomic<float> peak_{0.0f};
};

}  // namespace volt
