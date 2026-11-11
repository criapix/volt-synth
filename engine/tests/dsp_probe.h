#pragma once
#include <cmath>
#include <vector>

namespace probe {

// Amplitude of the sinusoid at `hz` in x (Goertzel). Exact when hz * n / sr is an integer.
inline double tone(const std::vector<float>& x, double hz, double sr) {
  const double w = 2.0 * 3.14159265358979323846 * hz / sr;
  const double c = 2.0 * std::cos(w);
  double s1 = 0, s2 = 0;
  for (float v : x) { double s0 = v + c * s1 - s2; s2 = s1; s1 = s0; }
  double re = s1 - s2 * std::cos(w), im = s2 * std::sin(w);
  return 2.0 * std::sqrt(re * re + im * im) / (double)x.size();
}

inline double rms(const std::vector<float>& x, size_t from = 0) {
  double s = 0;
  for (size_t i = from; i < x.size(); ++i) s += (double)x[i] * x[i];
  return std::sqrt(s / (double)(x.size() - from));
}

inline double peak(const std::vector<float>& x) {
  double p = 0;
  for (float v : x) p = std::fmax(p, std::fabs(v));
  return p;
}

inline bool finite(const std::vector<float>& x) {
  for (float v : x) if (!std::isfinite(v)) return false;
  return true;
}

// Frequency from upward zero crossings.
inline double zeroCrossHz(const std::vector<float>& x, double sr) {
  int n = 0;
  for (size_t i = 1; i < x.size(); ++i) if (x[i - 1] < 0 && x[i] >= 0) ++n;
  return n * sr / (double)x.size();
}

}  // namespace probe
