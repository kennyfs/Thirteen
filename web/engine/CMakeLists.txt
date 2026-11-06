# The engine for the web page: the repository's C++ (cpp/) and GuiEngine as WebAssembly, with a reduced ONNX Runtime
# built as a WebAssembly static library (web/build-ort-wasm.sh). Configured with emcmake; see web/README.md.
cmake_minimum_required(VERSION 3.18)
project(thirteen_wasm LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CPP ${CMAKE_CURRENT_SOURCE_DIR}/../../cpp)
set(ORT_WASM_DIR "" CACHE PATH "The reduced ONNX Runtime for WebAssembly: include/ and lib/libonnxruntime_webassembly.a")
if(NOT ORT_WASM_DIR)
  message(FATAL_ERROR "ORT_WASM_DIR is not set")
endif()

add_executable(thirteen_engine
  engine.cpp
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
target_include_directories(thirteen_engine PRIVATE ${CPP}/external/nlohmann_json ${ORT_WASM_DIR}/include)
target_compile_definitions(thirteen_engine PRIVATE USE_ONNXRUNTIME)
target_compile_options(thirteen_engine PRIVATE -O3 -msimd128 -fexceptions -Wall -Wno-sign-compare)
target_link_libraries(thirteen_engine PRIVATE ${ORT_WASM_DIR}/lib/libonnxruntime_webassembly.a)
# A module for a worker (engine-worker.js): ES6, growing memory, the engine's two calls, and a file system in memory
# for the networks (written there before they are named in a request).
target_link_options(thirteen_engine PRIVATE
  -O3 -fexceptions -sMODULARIZE=1 -sEXPORT_ES6=1 -sENVIRONMENT=web,worker,node -sALLOW_MEMORY_GROWTH=1 -sINITIAL_MEMORY=64MB
  -sSTACK_SIZE=1MB -sEXPORTED_FUNCTIONS=_engine_handle,_engine_free,_malloc,_free
  -sEXPORTED_RUNTIME_METHODS=FS,stringToNewUTF8,UTF8ToString -sFILESYSTEM=1
)
set_target_properties(thirteen_engine PROPERTIES OUTPUT_NAME engine SUFFIX ".js")
