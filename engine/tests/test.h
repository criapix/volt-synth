#pragma once
#include <cmath>
#include <cstdio>
#include <vector>

namespace t {
struct Case { const char* name; void (*fn)(); };
inline std::vector<Case>& cases() { static std::vector<Case> c; return c; }
inline int& failures() { static int f = 0; return f; }
struct Reg { Reg(const char* n, void (*f)()) { cases().push_back({n, f}); } };
}  // namespace t

#define TEST(name) \
  static void name(); \
  static t::Reg reg_##name(#name, name); \
  static void name()

#define CHECK(cond) \
  do { if (!(cond)) { std::printf("    FAIL %s:%d  %s\n", __FILE__, __LINE__, #cond); ++t::failures(); } } while (0)

#define CHECK_NEAR(a, b, tol) \
  do { double a_ = (a), b_ = (b); if (!(std::fabs(a_ - b_) <= (tol))) { \
    std::printf("    FAIL %s:%d  %s = %g, expected %g +- %g\n", __FILE__, __LINE__, #a, a_, b_, (double)(tol)); \
    ++t::failures(); } } while (0)
