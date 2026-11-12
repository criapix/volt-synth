#pragma once
// Factory presets on the PC: a reader for the flat JSON the app writes, the phrase each category
// is auditioned with, and the code that plays a preset through an Engine. Tests and tools only.
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <initializer_list>
#include <fstream>
#include <sstream>
#include <string>
#include <utility>
#include <vector>
#include "engine.h"

namespace volt {

struct PresetFile {
  std::string file;      // "020-lead-sync" (no folder, no extension)
  std::string name;      // UTF-8, as written
  std::string category;  // "Bass", "Lead", … or ""
  std::vector<std::pair<std::string, double>> params;  // permanent name -> real value
};

namespace detail {
inline void skipWs(const std::string& s, size_t& i) { while (i < s.size() && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) ++i; }
inline bool readString(const std::string& s, size_t& i, std::string& out) {
  skipWs(s, i);
  if (i >= s.size() || s[i] != '"') return false;
  ++i;
  out.clear();
  while (i < s.size() && s[i] != '"') {
    if (s[i] == '\\' && i + 1 < s.size()) { ++i; out += s[i] == 'n' ? '\n' : s[i]; ++i; continue; }
    out += s[i++];
  }
  if (i >= s.size()) return false;
  ++i;
  return true;
}
// Finds `"key"` followed by ':' at any depth and leaves i after the colon.
inline bool findKey(const std::string& s, const std::string& key, size_t& i) {
  const std::string pat = "\"" + key + "\"";
  size_t at = s.find(pat);
  if (at == std::string::npos) return false;
  i = at + pat.size();
  skipWs(s, i);
  if (i >= s.size() || s[i] != ':') return false;
  ++i;
  return true;
}
}  // namespace detail

inline bool readPreset(const std::string& path, PresetFile& out) {
  std::ifstream in(path, std::ios::binary);
  if (!in) return false;
  std::stringstream ss;
  ss << in.rdbuf();
  const std::string s = ss.str();
  out = PresetFile{};
  out.file = std::filesystem::path(path).stem().string();
  size_t i = 0;
  if (!detail::findKey(s, "name", i) || !detail::readString(s, i, out.name)) return false;
  if (detail::findKey(s, "category", i)) detail::readString(s, i, out.category);
  if (!detail::findKey(s, "params", i)) return false;
  detail::skipWs(s, i);
  if (i >= s.size() || s[i] != '{') return false;
  ++i;
  while (true) {
    detail::skipWs(s, i);
    if (i < s.size() && s[i] == '}') break;
    std::string key;
    if (!detail::readString(s, i, key)) return false;
    detail::skipWs(s, i);
    if (i >= s.size() || s[i] != ':') return false;
    ++i;
    detail::skipWs(s, i);
    char* end = nullptr;
    const double v = std::strtod(s.c_str() + i, &end);
    if (end == s.c_str() + i) return false;
    i = (size_t)(end - s.c_str());
    out.params.emplace_back(key, v);
    detail::skipWs(s, i);
    if (i < s.size() && s[i] == ',') { ++i; continue; }
  }
  return true;
}

// Every factory file, sorted by file name: the order the app's library (and Program Change) uses.
inline std::vector<std::string> factoryFiles(const std::string& dir = VOLT_PRESETS_DIR) {
  std::vector<std::string> out;
  for (auto& e : std::filesystem::directory_iterator(dir))
    if (e.path().extension() == ".json") out.push_back(e.path().string());
  std::sort(out.begin(), out.end());
  return out;
}

inline double presetValue(const PresetFile& p, const char* name) {
  for (auto& [k, v] : p.params) if (k == name) return v;
  const int id = paramIndex(name);
  return id >= 0 ? kParams[id].def : 0.0;
}

// Loads the preset into the engine as the app does (missing parameters take their default).
// Returns the number of unknown parameter names (0 for a good file).
inline int applyPreset(Engine& e, const PresetFile& p) {
  std::vector<float> v(kNumParams);
  for (int i = 0; i < kNumParams; ++i) v[i] = norm(i, kParams[i].def);
  int unknown = 0;
  for (auto& [k, val] : p.params) {
    const int id = paramIndex(k.c_str());
    if (id < 0) { ++unknown; continue; }
    v[id] = norm(id, (float)val);
  }
  e.postPatch(v.data(), kNumParams);
  return unknown;
}

struct NoteEvent { double t; int note; float vel; bool on; };

// The phrase a preset is auditioned with. It depends on the category so a bass plays a bass line
// and a pad holds chords; a preset with the arpeggiator on holds one chord and lets it run.
inline std::vector<NoteEvent> phraseFor(const PresetFile& p) {
  std::vector<NoteEvent> ev;
  auto note = [&](double t, double dur, int n, float vel = 0.8f) { ev.push_back({t, n, vel, true}); ev.push_back({t + dur, n, 0.0f, false}); };
  auto chord = [&](double t, double dur, std::initializer_list<int> ns, float vel = 0.75f) { for (int n : ns) note(t, dur, n, vel); };
  const std::string& c = p.category;
  if (presetValue(p, "arp.on") >= 0.5) {
    chord(0.0, 4.0, {48, 52, 55});
  } else if (c == "Bass") {
    const int line[] = {36, 36, 48, 36, 43, 41, 39, 34};
    for (int i = 0; i < 8; ++i) note(0.25 * i, 0.2, line[i], i % 2 ? 0.6f : 0.9f);
  } else if (c == "Lead") {
    const int line[] = {60, 63, 65, 67, 70, 67};
    for (int i = 0; i < 6; ++i) note(0.3 * i, 0.28, line[i]);
    note(1.8, 1.2, 72, 0.9f);
  } else if (c == "Pluck") {
    const int line[] = {60, 64, 67, 72, 67, 64};
    for (int i = 0; i < 6; ++i) note(0.25 * i, 0.2, line[i], i % 2 ? 0.6f : 0.9f);
    chord(1.6, 0.6, {48, 55, 60, 64});
  } else if (c == "Keys") {
    chord(0.0, 1.0, {48, 55, 60, 64});
    chord(1.0, 1.0, {53, 57, 60, 65});
    note(2.0, 0.25, 72); note(2.3, 0.25, 74); note(2.6, 0.25, 76);
  } else if (c == "FX") {
    note(0.0, 2.0, 60);
  } else {  // Pad, Strings, Brass, Init and anything else: held chords
    chord(0.0, 2.0, {48, 55, 60, 63});
    chord(2.0, 1.5, {44, 51, 56, 60});
  }
  std::stable_sort(ev.begin(), ev.end(), [](const NoteEvent& a, const NoteEvent& b) { return a.t < b.t; });
  return ev;
}

struct Rendered {
  std::vector<float> left, right;
  double releaseSeconds = -1;  // from the last note-off until every voice was free; -1 = never
};

// Plays the phrase and keeps rendering after it: until the voices are free plus `tailAfterFree`
// seconds (reverb and delay tails), but never more than `maxTail` seconds after the last note-off.
inline Rendered renderPhrase(Engine& e, const std::vector<NoteEvent>& ev, double tailAfterFree = 2.0, double maxTail = 12.0) {
  constexpr int kChunk = 16;
  constexpr double kSr = 48000.0;
  Rendered r;
  float buf[2 * kChunk];
  e.render(buf, kChunk);  // let the patch land before the first note
  const double end = ev.empty() ? 0.0 : ev.back().t;
  size_t next = 0;
  double t = 0.0, freeAt = -1.0;
  while (true) {
    while (next < ev.size() && ev[next].t <= t) {
      const auto& n = ev[next++];
      e.post(n.on ? Event{Event::NoteOn, n.note, n.vel} : Event{Event::NoteOff, n.note, 0.0f});
    }
    e.render(buf, kChunk);
    for (int i = 0; i < kChunk; ++i) { r.left.push_back(buf[2 * i]); r.right.push_back(buf[2 * i + 1]); }
    t += kChunk / kSr;
    if (next >= ev.size()) {
      if (freeAt < 0 && e.activeVoices() == 0) { freeAt = t; r.releaseSeconds = t - end; }
      if (freeAt >= 0 && t - freeAt >= tailAfterFree) break;
      if (t - end >= maxTail) break;
    }
  }
  return r;
}

// Loudest 400 ms window (100 ms hop) of the stereo signal, in dBFS RMS.
inline double loudnessDb(const Rendered& r) {
  const size_t win = 19200, hop = 4800;
  double best = 0.0;
  for (size_t at = 0; at + win <= r.left.size(); at += hop) {
    double sum = 0.0;
    for (size_t i = at; i < at + win; ++i) sum += 0.5 * ((double)r.left[i] * r.left[i] + (double)r.right[i] * r.right[i]);
    best = std::max(best, sum / win);
  }
  return best > 0.0 ? 10.0 * std::log10(best) : -200.0;
}

// Share of samples the output limiter is squashing hard (|x| > 0.95).
inline double limitedShare(const Rendered& r) {
  size_t n = 0;
  for (size_t i = 0; i < r.left.size(); ++i) if (std::fabs(r.left[i]) > 0.95f || std::fabs(r.right[i]) > 0.95f) ++n;
  return r.left.empty() ? 0.0 : (double)n / r.left.size();
}

}  // namespace volt
