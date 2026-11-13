#include "fm/dx7_codec.h"
#include <cstring>

namespace volt {

namespace {
// Maximum of each VCED operator field (offsets 0..20).
constexpr uint8_t kOpMax[21] = {99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 3, 3, 7, 3, 7, 99, 1, 31, 99, 14};
// Maximum of each global field, bytes 126..144.
constexpr uint8_t kGlobalMax[19] = {99, 99, 99, 99, 99, 99, 99, 99, 31, 7, 1, 99, 99, 99, 99, 1, 5, 7, 48};

uint8_t checksum(const uint8_t* data, int n) {
  int sum = 0;
  for (int i = 0; i < n; ++i) sum += data[i];
  return (uint8_t)((-sum) & 0x7F);
}
uint8_t clampTo(int v, int max) { return (uint8_t)(v < 0 ? 0 : (v > max ? max : v)); }
}  // namespace

void clampVced(Dx7Voice& v) {
  for (int op = 0; op < 6; ++op)
    for (int k = 0; k < 21; ++k) v[op * 21 + k] = clampTo(v[op * 21 + k] & 0x7F, kOpMax[k]);
  for (int g = 0; g < 19; ++g) v[126 + g] = clampTo(v[126 + g] & 0x7F, kGlobalMax[g]);
  for (int i = 145; i < 155; ++i) { uint8_t c = v[i] & 0x7F; v[i] = (c < 32) ? ' ' : c; }
  v[155] = 0;
}

void unpackVmem(const uint8_t* m, Dx7Voice& v) {
  v.fill(0);
  for (int op = 0; op < 6; ++op) {
    const uint8_t* s = m + op * 17;
    uint8_t* d = v.data() + op * 21;
    for (int k = 0; k < 11; ++k) d[k] = s[k] & 0x7F;
    d[11] = s[11] & 0x03;          // left curve
    d[12] = (s[11] >> 2) & 0x03;   // right curve
    d[13] = s[12] & 0x07;          // rate scaling
    d[20] = (s[12] >> 3) & 0x0F;   // detune
    d[14] = s[13] & 0x03;          // AMS
    d[15] = (s[13] >> 2) & 0x07;   // KVS
    d[16] = s[14] & 0x7F;          // output level
    d[17] = s[15] & 0x01;          // mode
    d[18] = (s[15] >> 1) & 0x1F;   // coarse
    d[19] = s[16] & 0x7F;          // fine
  }
  for (int i = 0; i < 8; ++i) v[126 + i] = m[102 + i] & 0x7F;
  v[134] = m[110] & 0x1F;
  v[135] = m[111] & 0x07;
  v[136] = (m[111] >> 3) & 0x01;
  for (int i = 0; i < 4; ++i) v[137 + i] = m[112 + i] & 0x7F;
  v[141] = m[116] & 0x01;
  v[142] = (m[116] >> 1) & 0x07;
  v[143] = (m[116] >> 4) & 0x07;
  v[144] = m[117] & 0x7F;
  for (int i = 0; i < 10; ++i) v[145 + i] = m[118 + i];
  clampVced(v);
}

void packVmem(const Dx7Voice& v, uint8_t* m) {
  std::memset(m, 0, 128);
  for (int op = 0; op < 6; ++op) {
    const uint8_t* s = v.data() + op * 21;
    uint8_t* d = m + op * 17;
    for (int k = 0; k < 11; ++k) d[k] = s[k];
    d[11] = (uint8_t)((s[12] << 2) | s[11]);
    d[12] = (uint8_t)((s[20] << 3) | s[13]);
    d[13] = (uint8_t)((s[15] << 2) | s[14]);
    d[14] = s[16];
    d[15] = (uint8_t)((s[18] << 1) | s[17]);
    d[16] = s[19];
  }
  for (int i = 0; i < 8; ++i) m[102 + i] = v[126 + i];
  m[110] = v[134];
  m[111] = (uint8_t)((v[136] << 3) | v[135]);
  for (int i = 0; i < 4; ++i) m[112 + i] = v[137 + i];
  m[116] = (uint8_t)((v[143] << 4) | (v[142] << 1) | v[141]);
  m[117] = v[144];
  for (int i = 0; i < 10; ++i) m[118 + i] = v[145 + i];
}

Dx7Status readDx7Bank(const std::vector<uint8_t>& s, Dx7Bank& out) {
  if (s.size() != 4104 || s[0] != 0xF0 || s[1] != 0x43 || (s[2] & 0xF0) != 0x00 || s[3] != 0x09 ||
      s[4] != 0x20 || s[5] != 0x00 || s[4103] != 0xF7)
    return Dx7Status::NotDx7;
  for (int i = 0; i < 32; ++i) unpackVmem(s.data() + 6 + i * 128, out.voices[i]);
  return checksum(s.data() + 6, 4096) == (s[4102] & 0x7F) ? Dx7Status::Ok : Dx7Status::BadChecksum;
}

std::vector<uint8_t> writeDx7Bank(const Dx7Bank& b) {
  std::vector<uint8_t> s = {0xF0, 0x43, 0x00, 0x09, 0x20, 0x00};
  s.resize(6 + 4096);
  for (int i = 0; i < 32; ++i) packVmem(b.voices[i], s.data() + 6 + i * 128);
  s.push_back(checksum(s.data() + 6, 4096));
  s.push_back(0xF7);
  return s;
}

Dx7Status readDx7Voice(const std::vector<uint8_t>& s, Dx7Voice& out) {
  if (s.size() != 163 || s[0] != 0xF0 || s[1] != 0x43 || (s[2] & 0xF0) != 0x00 || s[3] != 0x00 ||
      s[4] != 0x01 || s[5] != 0x1B || s[162] != 0xF7)
    return Dx7Status::NotDx7;
  out.fill(0);
  for (int i = 0; i < 155; ++i) out[i] = s[6 + i];
  clampVced(out);
  return checksum(s.data() + 6, 155) == (s[161] & 0x7F) ? Dx7Status::Ok : Dx7Status::BadChecksum;
}

std::vector<uint8_t> writeDx7Voice(const Dx7Voice& v) {
  std::vector<uint8_t> s = {0xF0, 0x43, 0x00, 0x00, 0x01, 0x1B};
  for (int i = 0; i < 155; ++i) s.push_back(v[i]);
  s.push_back(checksum(v.data(), 155));
  s.push_back(0xF7);
  return s;
}

std::string dx7Name(const uint8_t* v) {
  std::string n(reinterpret_cast<const char*>(v + 145), 10);
  while (!n.empty() && n.back() == ' ') n.pop_back();
  return n;
}

void setDx7Name(Dx7Voice& v, const std::string& name) {
  for (int i = 0; i < 10; ++i) {
    char c = i < (int)name.size() ? name[i] : ' ';
    v[145 + i] = (unsigned char)c >= 32 && (unsigned char)c < 127 ? (uint8_t)c : (uint8_t)'?';
  }
}

}  // namespace volt
