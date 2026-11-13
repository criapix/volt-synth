#include <jni.h>
#include <string>
#include "audio.h"
#include "params.h"

static Audio g_audio;
using volt::Event;

#define JNI(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_criapix_volt_Native_##name

JNI(jstring, paramTable)(JNIEnv* env, jobject) {
  std::string s;
  for (int i = 0; i < volt::kNumParams; ++i) {
    const auto& p = volt::kParams[i];
    const char* curve = p.curve == volt::Curve::Exp ? "exp" : p.curve == volt::Curve::Step ? "step" : "lin";
    s += p.name; s += '|';
    s += std::to_string(p.min); s += '|';
    s += std::to_string(p.max); s += '|';
    s += std::to_string(p.def); s += '|';
    s += curve; s += '\n';
  }
  return env->NewStringUTF(s.c_str());
}

JNI(jboolean, start)(JNIEnv*, jobject) { return g_audio.start() ? JNI_TRUE : JNI_FALSE; }
JNI(void, stop)(JNIEnv*, jobject) { g_audio.stop(); }
JNI(jstring, audioInfo)(JNIEnv* env, jobject) { return env->NewStringUTF(g_audio.info().c_str()); }
JNI(jfloat, peak)(JNIEnv*, jobject) { return g_audio.engine().peak(); }

JNI(void, noteOn)(JNIEnv*, jobject, jint note, jfloat velocity) { g_audio.engine().post({Event::NoteOn, note, velocity}); }
JNI(void, noteOff)(JNIEnv*, jobject, jint note) { g_audio.engine().post({Event::NoteOff, note, 0.0f}); }
JNI(void, setParam)(JNIEnv*, jobject, jint id, jfloat normalized) { g_audio.engine().post({Event::SetParam, id, normalized}); }
JNI(void, aftertouch)(JNIEnv*, jobject, jfloat v) { g_audio.engine().post({Event::Aftertouch, 0, v}); }
JNI(void, breath)(JNIEnv*, jobject, jfloat v) { g_audio.engine().post({Event::Breath, 0, v}); }
JNI(void, pitchBend)(JNIEnv*, jobject, jfloat v) { g_audio.engine().post({Event::PitchBend, 0, v}); }
JNI(void, modWheel)(JNIEnv*, jobject, jfloat v) { g_audio.engine().post({Event::ModWheel, 0, v}); }
JNI(void, sustain)(JNIEnv*, jobject, jboolean on) { g_audio.engine().post({Event::Sustain, 0, on ? 1.0f : 0.0f}); }
JNI(void, allNotesOff)(JNIEnv*, jobject) { g_audio.engine().post({Event::AllNotesOff, 0, 0.0f}); }

JNI(jboolean, loadPatch)(JNIEnv* env, jobject, jfloatArray values) {
  const jsize n = env->GetArrayLength(values);
  float buf[volt::kNumParams];
  const jsize count = n < volt::kNumParams ? n : volt::kNumParams;
  env->GetFloatArrayRegion(values, 0, count, buf);
  return g_audio.engine().postPatch(buf, count) ? JNI_TRUE : JNI_FALSE;
}
JNI(jint, activeVoices)(JNIEnv*, jobject) { return g_audio.engine().activeVoices(); }
JNI(jint, voiceLimit)(JNIEnv*, jobject) { return g_audio.engine().voiceLimit(); }
JNI(void, setMaxVoices)(JNIEnv*, jobject, jint n) { g_audio.setMaxVoices(n); }

// msfa's algorithm table is a protected static member of FmCore; a trivial subclass reads it once for the UI.
#include "fm_core.h"
struct AlgoPeek : FmCore {
  void render(int32_t*, FmOpParams*, int32_t, int32_t*, int32_t) override {}
  static int32_t flags(int alg, int op) { return algorithms[alg].ops[op]; }
};
JNI(jintArray, fmAlgorithms)(JNIEnv* env, jobject) {
  jint buf[32 * 6];
  for (int a = 0; a < 32; ++a) for (int o = 0; o < 6; ++o) buf[a * 6 + o] = AlgoPeek::flags(a, o);
  jintArray out = env->NewIntArray(32 * 6);
  env->SetIntArrayRegion(out, 0, 32 * 6, buf);
  return out;
}
