#include "dsp_probe.h"
#include "rig.h"
#include "test.h"

using namespace volt;

namespace {
double maxStep(const std::vector<float>& x) {
  double m = 0;
  for (size_t i = 1; i < x.size(); ++i) m = std::fmax(m, std::fabs((double)x[i] - x[i - 1]));
  return m;
}
}  // namespace

TEST(patch_applies_at_once_when_nothing_sounds) {
  Rig r;
  auto p = defaultPatch();
  p[kFilterCutoff] = norm(kFilterCutoff, 500.0f);
  p[kOsc1Wave] = norm(kOsc1Wave, 2);
  CHECK(r.e.postPatch(p.data(), kNumParams));
  r.play(16.0f / 48000.0f);  // one block
  CHECK_NEAR(r.e.value(kFilterCutoff), 500.0f, 0.01);
  CHECK_NEAR(r.e.value(kOsc1Wave), 2.0f, 0);
}

TEST(patch_waits_for_the_fade_while_a_note_sounds) {
  Rig r;
  r.on(57);
  r.play(0.1f);
  auto p = defaultPatch();
  p[kFilterCutoff] = norm(kFilterCutoff, 500.0f);
  CHECK(r.e.postPatch(p.data(), kNumParams));
  r.play(0.002f);  // inside the 5 ms fade-out
  CHECK(r.e.value(kFilterCutoff) > 10000.0f);
  r.play(0.010f);
  CHECK_NEAR(r.e.value(kFilterCutoff), 500.0f, 0.01);  // snapped, not smoothed
  CHECK(r.e.activeVoices() == 1);                       // the note survives the switch
}

TEST(patch_switch_does_not_click) {
  // Sine through an open filter; the patch changes the filter model, which resets the filter state.
  // Without the fade, that reset is an audible step.
  Rig r;
  r.set(kVoiceDrift, 0.0f);
  r.set(kOsc1Wave, 3);
  r.set(kFilterModel, 1);
  r.play(0.01f);
  r.on(57);
  r.play(0.2f);
  const double steady = maxStep(r.play(0.1f));
  auto p = defaultPatch();
  p[kVoiceDrift] = 0.0f;
  p[kOsc1Wave] = norm(kOsc1Wave, 3);
  p[kFilterModel] = norm(kFilterModel, 2);
  CHECK(r.e.postPatch(p.data(), kNumParams));
  const double during = maxStep(r.play(0.1f));
  CHECK(during < 1.5 * steady);
  CHECK(during > 0.0);
}

TEST(patch_without_commit_never_applies) {
  Rig r;
  r.e.post({Event::PatchParam, kFilterCutoff, norm(kFilterCutoff, 500.0f)});
  r.play(0.05f);
  CHECK(r.e.value(kFilterCutoff) > 10000.0f);
}

TEST(patch_is_refused_whole_when_the_queue_is_too_full) {
  Rig r;
  while (r.e.post({Event::SetParam, kMixNoise, 0.0f})) {}  // fill the queue
  auto p = defaultPatch();
  p[kFilterCutoff] = norm(kFilterCutoff, 500.0f);
  CHECK(!r.e.postPatch(p.data(), kNumParams));
  r.play(0.05f);
  CHECK(r.e.value(kFilterCutoff) > 10000.0f);  // nothing half-applied
  CHECK(r.e.postPatch(p.data(), kNumParams));  // room again after the drain
}

TEST(patch_a_later_set_param_wins) {
  Rig r;
  r.on(57);
  r.play(0.05f);
  auto p = defaultPatch();
  p[kFilterCutoff] = norm(kFilterCutoff, 500.0f);
  CHECK(r.e.postPatch(p.data(), kNumParams));
  r.set(kFilterCutoff, 3000.0f);  // the user turns the knob right after choosing the preset
  r.play(0.1f);
  CHECK_NEAR(r.e.value(kFilterCutoff), 3000.0f, 1.0);
}
