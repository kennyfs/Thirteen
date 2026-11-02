# The JNI functions are looked up by class and method name (android/app/src/main/cpp/thirteen_jni.cpp).
-keep class io.github.kennyfs.thirteen.Engine {
  native <methods>;
}
