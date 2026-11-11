#pragma once
#include <atomic>
#include <mutex>
#include "event_queue.h"
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
  float peak() const { return peak_.load(std::memory_order_relaxed); }  // last block's peak, 0..1

 private:
  void renderBlock();
  void handle(const Event& e);
  void noteOn(int note, float velocity);
  void noteOff(int note);
  void releaseOrSustain(Voice& v);
  Voice* allocPoly(int note);
  void startVoice(Voice& v, int note, float velocity, float detune, float pan, float gain);

  EventQueue queue_;
  std::mutex postMutex_;  // serialises producers; never taken by the audio thread

  float sr_ = 48000.0f;
  float target_[kNumParams];  // real values the parameters are heading to
  float value_[kNumParams];   // smoothed real values the voices read
  float smooth_ = 1.0f;

  Voice voices_[kMaxVoices];
  uint64_t counter_ = 0;
  int stack_[16];  // held keys in mono modes, oldest first
  int stackSize_ = 0;
  float lastPitch_ = 60.0f;
  bool anyNoteYet_ = false;
  bool sustain_ = false;
  float bend_ = 0.0f, modWheel_ = 0.0f;
  Lfo globalLfo_;

  alignas(16) float io_[kMaxVoices / 4][kBlock][4];
  float blockL_[kBlock], blockR_[kBlock];
  int blockPos_ = kBlock;  // kBlock = nothing buffered

  std::atomic<int> activeVoices_{0};
  std::atomic<float> peak_{0.0f};
};

}  // namespace volt
