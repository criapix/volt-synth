// Renders every factory preset to build/render/<file>.wav with the phrase its category is auditioned
// with, writes index.html (one player per preset) and loudness.csv, and prints a table.
// With --stress it instead holds 16 notes on every preset and prints the CPU cost, heaviest first.
// Usage: volt_render <outDir> [--stress]
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>
#include "dx7_fixtures.h"
#include "preset_file.h"

using namespace volt;

static void writeWav(const std::string& path, const Rendered& r) {
  std::ofstream f(path, std::ios::binary);
  const uint32_t frames = (uint32_t)r.left.size(), sr = 48000, bytes = frames * 4;
  auto u32 = [&](uint32_t v) { f.write((const char*)&v, 4); };
  auto u16 = [&](uint16_t v) { f.write((const char*)&v, 2); };
  f.write("RIFF", 4); u32(36 + bytes); f.write("WAVE", 4);
  f.write("fmt ", 4); u32(16); u16(1); u16(2); u32(sr); u32(sr * 4); u16(4); u16(16);
  f.write("data", 4); u32(bytes);
  for (uint32_t i = 0; i < frames; ++i) {
    for (float x : {r.left[i], r.right[i]}) {
      float c = x < -1.0f ? -1.0f : (x > 1.0f ? 1.0f : x);
      u16((uint16_t)(int16_t)std::lround(c * 32767.0f));
    }
  }
}

static std::string html(const std::string& s) {
  std::string o;
  for (char c : s) { if (c == '<') o += "&lt;"; else if (c == '>') o += "&gt;"; else if (c == '&') o += "&amp;"; else o += c; }
  return o;
}

static int stress() {
  struct Row { std::string file; double pct; };
  std::vector<Row> rows;
  for (const auto& path : factoryFiles()) {
    PresetFile p;
    if (!readPreset(path, p)) continue;
    Engine e;
    e.setSampleRate(48000.0f);
    applyPreset(e, p);
    static float buf[2 * 96];
    e.render(buf, 96);
    for (int n = 0; n < 16; ++n) e.post({Event::NoteOn, 36 + 3 * n, 0.8f});
    e.render(buf, 96);
    auto t0 = std::chrono::steady_clock::now();
    for (int i = 0; i < 1500; ++i) e.render(buf, 96);  // 3 s
    const double sec = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
    rows.push_back({p.file, 100.0 * sec / 3.0});
  }
  std::sort(rows.begin(), rows.end(), [](const Row& a, const Row& b) { return a.pct > b.pct; });
  std::printf("16 notes held, %% of one PC core (heaviest first):\n");
  for (auto& r : rows) std::printf("  %5.1f %%  %s\n", r.pct, r.file.c_str());
  return 0;
}

// All 256 DX7 factory voices, same phrase and tail as the test, into <out>/dx7/<cartridge>-<NN>.wav.
// Appends a DX7 section to index.html (one sub-heading per cartridge) and rows to loudness.csv.
static void renderDx7(const std::string& out, std::ofstream& page, std::ofstream& csv) {
  namespace fs = std::filesystem;
  std::vector<fs::path> carts;
  for (auto& p : dx7fx::dx7Cartridges()) carts.emplace_back(p);
  if (carts.empty()) {
    std::printf("\nNo DX7 cartridges in app/src/main/assets/dx7: skipping the DX7 section.\n");
    return;
  }
  fs::create_directories(out + "/dx7");
  page << "<h1>DX7 - cartuchos de f&aacute;brica</h1><p class=d>Frase de teclas, motor FM do app. O n&iacute;vel n&atilde;o "
          "&eacute; nivelado por voz (s&atilde;o os n&iacute;veis originais dos cartuchos).</p>\n";
  std::printf("\nDX7 voice     name        loud dB  squash %%  release s\n");
  for (const auto& path : carts) {
    std::ifstream in(path, std::ios::binary);
    std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    Dx7Bank bank;
    if (readDx7Bank(bytes, bank) != Dx7Status::Ok) { std::printf("cannot read %s\n", path.string().c_str()); continue; }
    const std::string cart = path.stem().string();
    page << "<h2>" << html(cart) << "</h2><table>\n";
    for (int i = 0; i < 32; ++i) {
      Engine e;
      e.setSampleRate(48000.0f);
      applyDx7Voice(e, bank.voices[i]);
      PresetFile p;
      p.category = "Keys";
      auto r = renderPhrase(e, phraseFor(p), 2.0, 20.0);
      const double loud = loudnessDb(r), squash = 100.0 * limitedShare(r);
      char id[32];
      std::snprintf(id, sizeof id, "%s-%02d", cart.c_str(), i + 1);
      const std::string name = dx7Name(bank.voices[i].data());
      writeWav(out + "/dx7/" + id + ".wav", r);
      std::printf("%-12s  %-10s  %7.1f  %8.2f  %9.2f\n", id, name.c_str(), loud, squash, r.releaseSeconds);
      csv << ",dx7:" << id << ",\"" << name << "\",DX7," << loud << ',' << squash << ',' << r.releaseSeconds << '\n';
      char db[16];
      std::snprintf(db, sizeof db, "%.1f", loud);
      page << "<tr><td>" << i + 1 << "<td>" << html(name) << "<td>" << db
           << "<td><audio controls preload=none src=\"dx7/" << id << ".wav\"></audio></tr>\n";
    }
    page << "</table>\n";
  }
}

int main(int argc, char** argv) {
  if (argc < 2) { std::printf("usage: volt_render <outDir> [--stress]\n"); return 2; }
  if (argc > 2 && std::strcmp(argv[2], "--stress") == 0) return stress();
  const std::string out = argv[1];
  std::filesystem::create_directories(out);
  std::ofstream page(out + "/index.html", std::ios::binary);
  std::ofstream csv(out + "/loudness.csv", std::ios::binary);
  csv << "position,file,name,category,loudness_db,squashed_pct,release_s\n";
  page << "<!doctype html><meta charset=utf-8><title>Volt - presets de fabrica</title>"
          "<style>body{font:15px sans-serif;background:#121417;color:#e8e8e8;margin:16px}"
          "td{padding:4px 10px;border-bottom:1px solid #262a31}audio{height:32px}.d{color:#8a9099}</style>"
          "<h1>Volt - presets de fábrica</h1><p class=d>Mesma frase por categoria, motor do app sem alteração. "
          "Anote o veredito em docs/PRESET-REVIEW.md.</p><table>"
          "<tr><th>#<th>Preset<th>Categoria<th>Nível (dB)<th>Ouvir</tr>\n";
  std::printf("  #  file                          loud dB  squash %%  release s\n");
  int pos = 0;
  for (const auto& path : factoryFiles()) {
    PresetFile p;
    ++pos;
    if (!readPreset(path, p)) { std::printf("cannot read %s\n", path.c_str()); continue; }
    Engine e;
    e.setSampleRate(48000.0f);
    applyPreset(e, p);
    auto r = renderPhrase(e, phraseFor(p));
    const double loud = loudnessDb(r), squash = 100.0 * limitedShare(r);
    writeWav(out + "/" + p.file + ".wav", r);
    char line[256];
    std::snprintf(line, sizeof line, "%3d  %-28s  %7.1f  %8.2f  %9.2f\n", pos, p.file.c_str(), loud, squash, r.releaseSeconds);
    std::printf("%s", line);
    csv << pos << ',' << p.file << ",\"" << p.name << "\"," << p.category << ',' << loud << ',' << squash << ',' << r.releaseSeconds << '\n';
    char db[16];
    std::snprintf(db, sizeof db, "%.1f", loud);
    page << "<tr><td>" << pos << "<td>" << html(p.name) << "<td class=d>" << html(p.category) << "<td>" << db
         << "<td><audio controls preload=none src=\"" << p.file << ".wav\"></audio></tr>\n";
  }
  page << "</table>\n";
  renderDx7(out, page, csv);
  std::printf("wrote %s/index.html\n", out.c_str());
  return 0;
}
