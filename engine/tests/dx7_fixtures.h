#pragma once
// DX7 factory cartridges are Yamaha data and are not distributed with the repository: each developer may drop
// their own *.syx into app/src/main/assets/dx7. Tests that need them SKIP (with a visible note) when absent.
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>

namespace dx7fx {

// Sorted paths of the *.syx files in VOLT_DX7_DIR; empty when the folder is missing or has none.
inline std::vector<std::string> dx7Cartridges() {
  namespace fs = std::filesystem;
  std::vector<std::string> out;
  std::error_code ec;
  if (!fs::is_directory(VOLT_DX7_DIR, ec)) return out;
  fs::directory_iterator it(VOLT_DX7_DIR, ec), end;
  for (; !ec && it != end; it.increment(ec)) {
    std::error_code e2;
    if (it->is_regular_file(e2) && it->path().extension() == ".syx") out.push_back(it->path().string());
  }
  std::sort(out.begin(), out.end());
  return out;
}

inline std::string cartridgePath(const char* stem) { return std::string(VOLT_DX7_DIR) + "/" + stem + ".syx"; }

inline bool cartridgeExists(const char* stem) {
  std::error_code ec;
  return std::filesystem::is_regular_file(cartridgePath(stem), ec);
}

}  // namespace dx7fx

#define DX7_SKIP(reason) do { std::printf("    SKIP %s\n", reason); return; } while (0)
// At least one cartridge in the folder.
#define DX7_REQUIRE_ANY() \
  do { if (dx7fx::dx7Cartridges().empty()) DX7_SKIP("no DX7 cartridges in app/src/main/assets/dx7"); } while (0)
// One specific cartridge (known values); skips even when other cartridges are present.
#define DX7_REQUIRE(stem) \
  do { if (!dx7fx::cartridgeExists(stem)) DX7_SKIP("app/src/main/assets/dx7/" stem ".syx not present"); } while (0)
