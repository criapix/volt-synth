#include "test.h"
#include <cstring>

// Usage: volt_tests [substring]  - runs the tests whose name contains the substring.
int main(int argc, char** argv) {
  const char* filter = argc > 1 ? argv[1] : "";
  int ran = 0, failed = 0;
  for (auto& c : t::cases()) {
    if (!std::strstr(c.name, filter)) continue;
    int before = t::failures();
    c.fn();
    ++ran;
    bool ok = t::failures() == before;
    if (!ok) ++failed;
    std::printf("%s %s\n", ok ? "ok  " : "FAIL", c.name);
  }
  std::printf("%d tests, %d failed\n", ran, failed);
  return failed == 0 && ran > 0 ? 0 : 1;
}
