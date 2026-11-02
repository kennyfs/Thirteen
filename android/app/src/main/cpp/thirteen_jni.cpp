#include "../../../../../cpp/command/guiengine.h"

#include <jni.h>

// io.github.kennyfs.thirteen.Engine: one GuiEngine for the app's lifetime, used from one thread at a time.
extern "C" {

JNIEXPORT jlong JNICALL Java_io_github_kennyfs_thirteen_Engine_nativeCreate(JNIEnv*, jclass) {
  return reinterpret_cast<jlong>(new GuiEngine());
}

JNIEXPORT jstring JNICALL Java_io_github_kennyfs_thirteen_Engine_nativeHandle(
  JNIEnv* env, jclass, jlong handle, jstring request
) {
  const char* chars = env->GetStringUTFChars(request, nullptr);
  std::string line(chars);
  env->ReleaseStringUTFChars(request, chars);
  std::string answer = reinterpret_cast<GuiEngine*>(handle)->handleLine(line);
  return env->NewStringUTF(answer.c_str());
}

}
