#include <jni.h>
#include <string>
#include "params.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_criapix_volt_Native_paramTable(JNIEnv* env, jobject) {
  // One line per parameter: name|min|max|default|curve   (curve: lin, exp, step)
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
