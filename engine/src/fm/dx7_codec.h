#pragma once
#include <array>
#include <cstdint>
#include <string>
#include <vector>

namespace volt {

// One DX7 voice in VCED layout (155 bytes, OP6 first; see plan 6 section 0). Byte 155 is padding
// so the array can be handed to msfa's Dx7Note, which takes uint8_t[156].
using Dx7Voice = std::array<uint8_t, 156>;

struct Dx7Bank { std::array<Dx7Voice, 32> voices{}; };

enum class Dx7Status { Ok, BadChecksum, NotDx7 };

// 32-voice bank sysex (4104 bytes). BadChecksum still fills `out`.
Dx7Status readDx7Bank(const std::vector<uint8_t>& sysex, Dx7Bank& out);
std::vector<uint8_t> writeDx7Bank(const Dx7Bank& bank);
// Single-voice sysex (163 bytes).
Dx7Status readDx7Voice(const std::vector<uint8_t>& sysex, Dx7Voice& out);
std::vector<uint8_t> writeDx7Voice(const Dx7Voice& v);

void unpackVmem(const uint8_t vmem[128], Dx7Voice& out);  // also masks and clamps
void packVmem(const Dx7Voice& v, uint8_t vmem[128]);
void clampVced(Dx7Voice& v);                              // every field into its DX7 range
std::string dx7Name(const uint8_t* vced);                 // bytes 145..154, trailing spaces trimmed
void setDx7Name(Dx7Voice& v, const std::string& name);    // pad/cut to 10, non-ASCII -> '?'

}  // namespace volt
