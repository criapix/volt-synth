#pragma once
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <oboe/Oboe.h>
#include "engine.h"
#include "governor.h"

// Owns the engine and the output stream.
class Audio : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
 public:
  bool start();
  void stop();
  volt::Engine& engine() { return engine_; }
  void setMaxVoices(int n) { maxVoices_.store(n, std::memory_order_relaxed); }
  std::string info();  // key=value lines for the settings screen

  oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* data, int32_t frames) override;
  void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

 private:
  bool open();

  volt::Engine engine_;
  std::mutex lock_;  // guards stream_ open/close; never taken in onAudioReady
  std::shared_ptr<oboe::AudioStream> stream_;
  std::unique_ptr<oboe::LatencyTuner> tuner_;
  std::atomic<bool> wanted_{false};
  volt::LoadGovernor governor_;            // audio thread only (reset in open(), when no callback runs)
  std::atomic<int> maxVoices_{volt::kMaxVoices};  // the user's cap from the settings
  std::atomic<float> load_{0.0f};  // fraction of the callback period spent rendering
  std::atomic<int> restarts_{0};
};
