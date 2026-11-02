# The native engine of the app: the repository's C++ (cpp/) plus a JNI wrapper around GuiEngine.
cmake_minimum_required(VERSION 3.18.1)
project(thirteen_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CPP ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../cpp)
if(NOT ORT_DIR)
  message(FATAL_ERROR "ORT_DIR (the unpacked onnxruntime-android AAR) is not set")
endif()

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES IMPORTED_LOCATION ${ORT_DIR}/jni/${ANDROID_ABI}/libonnxruntime.so)

add_library(thirteen_jni SHARED
  thirteen_jni.cpp
  ${CPP}/core/global.cpp
  ${CPP}/core/rand.cpp
  ${CPP}/core/rand_helpers.cpp
  ${CPP}/core/hash.cpp
  ${CPP}/core/md5.cpp
  ${CPP}/core/sha2.cpp
  ${CPP}/core/timer.cpp
  ${CPP}/core/bsearch.cpp
  ${CPP}/game/cards.cpp
  ${CPP}/game/handeval.cpp
  ${CPP}/game/arrangement.cpp
  ${CPP}/game/scoring.cpp
  ${CPP}/game/rules.cpp
  ${CPP}/players/players.cpp
  ${CPP}/players/montecarlo.cpp
  ${CPP}/players/netplayer.cpp
  ${CPP}/players/human.cpp
  ${CPP}/command/guiengine.cpp
)
target_include_directories(thirteen_jni PRIVATE ${CPP}/external/nlohmann_json ${ORT_DIR}/headers)
target_compile_definitions(thirteen_jni PRIVATE USE_ONNXRUNTIME)
# Optimized in debug builds too: the players search thousands of arrangements per hand.
target_compile_options(thirteen_jni PRIVATE -O2 -Wall -Wno-sign-compare)
target_link_libraries(thirteen_jni PRIVATE onnxruntime log)
