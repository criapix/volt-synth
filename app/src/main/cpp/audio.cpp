#include "audio.h"

#include <ctime>
#include <android/log.h>

#if defined(__aarch64__)
static void flushDenormals() {
  uint64_t fpcr;
  __asm__ __volatile__("mrs %0, fpcr" : "=r"(fpcr));
  fpcr |= (1ull << 24);  // FZ: flush-to-zero
  __asm__ __volatile__("msr fpcr, %0" : : "r"(fpcr));
}
#elif defined(__x86_64__) || defined(__i386__)
#include <xmmintrin.h>
#include <pmmintrin.h>
static void flushDenormals() {
  _MM_SET_FLUSH_ZERO_MODE(_MM_FLUSH_ZERO_ON);
  _MM_SET_DENORMALS_ZERO_MODE(_MM_DENORMALS_ZERO_ON);
}
#else
static void flushDenormals() {}
#endif

static double nowSeconds() {
  timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (double)ts.tv_sec + (double)ts.tv_nsec * 1e-9;
}

bool Audio::open() {
  oboe::AudioStreamBuilder b;
  b.setDirection(oboe::Direction::Output)
      ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
      ->setSharingMode(oboe::SharingMode::Exclusive)
      ->setFormat(oboe::AudioFormat::Float)
      ->setChannelCount(oboe::ChannelCount::Stereo)
      ->setUsage(oboe::Usage::Game)  // the usage Android routes with the least processing
      ->setDataCallback(this)
      ->setErrorCallback(this);
  oboe::Result r = b.openStream(stream_);
  if (r != oboe::Result::OK) {
    __android_log_print(ANDROID_LOG_ERROR, "volt", "openStream: %s", oboe::convertToText(r));
    stream_.reset();
    return false;
  }
  engine_.setSampleRate((float)stream_->getSampleRate());
  // Start with the smallest buffer; the tuner grows it only if the device glitches.
  stream_->setBufferSizeInFrames(stream_->getFramesPerBurst());
  tuner_ = std::make_unique<oboe::LatencyTuner>(*stream_);
  governor_ = volt::LoadGovernor{};
  r = stream_->requestStart();
  if (r != oboe::Result::OK) {
    __android_log_print(ANDROID_LOG_ERROR, "volt", "requestStart: %s", oboe::convertToText(r));
    stream_->close();
    stream_.reset();
    return false;
  }
  return true;
}

bool Audio::start() {
  std::lock_guard<std::mutex> g(lock_);
  wanted_ = true;
  if (stream_) return true;
  return open();
}

void Audio::stop() {
  std::lock_guard<std::mutex> g(lock_);
  wanted_ = false;
  if (stream_) { stream_->stop(); stream_->close(); stream_.reset(); }
  tuner_.reset();
}

oboe::DataCallbackResult Audio::onAudioReady(oboe::AudioStream* stream, void* data, int32_t frames) {
  flushDenormals();
  const double t0 = nowSeconds();
  engine_.render(static_cast<float*>(data), frames);
  if (tuner_) tuner_->tune();
  const double period = (double)frames / (double)stream->getSampleRate();
  const float used = (float)((nowSeconds() - t0) / period);
  const float load = 0.95f * load_.load(std::memory_order_relaxed) + 0.05f * used;
  load_.store(load, std::memory_order_relaxed);
  auto x = stream->getXRunCount();
  const int governed = governor_.update(load, x ? x.value() : 0, (float)period);
  const int cap = maxVoices_.load(std::memory_order_relaxed);
  engine_.setVoiceLimit(governed < cap ? governed : cap);
  return oboe::DataCallbackResult::Continue;
}

// Called on a separate thread after the stream died (headphones or the USB audio device changed).
void Audio::onErrorAfterClose(oboe::AudioStream*, oboe::Result error) {
  __android_log_print(ANDROID_LOG_WARN, "volt", "stream closed: %s", oboe::convertToText(error));
  std::lock_guard<std::mutex> g(lock_);
  stream_.reset();
  tuner_.reset();
  if (wanted_) { ++restarts_; open(); }
}

std::string Audio::info() {
  std::lock_guard<std::mutex> g(lock_);
  std::string s;
  auto add = [&](const char* k, const std::string& v) { s += k; s += '='; s += v; s += '\n'; };
  add("running", stream_ ? "1" : "0");
  if (stream_) {
    add("sampleRate", std::to_string(stream_->getSampleRate()));
    add("burst", std::to_string(stream_->getFramesPerBurst()));
    add("buffer", std::to_string(stream_->getBufferSizeInFrames()));
    add("lowLatency", stream_->getPerformanceMode() == oboe::PerformanceMode::LowLatency ? "1" : "0");
    add("exclusive", stream_->getSharingMode() == oboe::SharingMode::Exclusive ? "1" : "0");
    add("api", stream_->getAudioApi() == oboe::AudioApi::AAudio ? "AAudio" : "OpenSLES");
    auto x = stream_->getXRunCount();
    add("xruns", x ? std::to_string(x.value()) : "?");
    auto l = stream_->calculateLatencyMillis();
    add("latencyMs", l ? std::to_string(l.value()) : "?");
  }
  add("load", std::to_string(load_.load()));
  add("restarts", std::to_string(restarts_.load()));
  add("voices", std::to_string(engine_.activeVoices()));
  add("voiceLimit", std::to_string(engine_.voiceLimit()));
  add("maxVoices", std::to_string(maxVoices_.load()));
  return s;
}
