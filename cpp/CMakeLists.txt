cmake_minimum_required(VERSION 3.18.2)
project(thirteen LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

option(USE_NATIVE "Compile with -march=native" ON)
set(ONNXRUNTIME_ROOT "" CACHE PATH "ONNX Runtime directory (with include/ and lib/), for net players; see scripts/fetch_onnxruntime.sh")

find_package(Threads REQUIRED)

add_executable(thirteen
  core/global.cpp
  core/rand.cpp
  core/rand_helpers.cpp
  core/hash.cpp
  core/md5.cpp
  core/sha2.cpp
  core/timer.cpp
  core/test.cpp
  core/bsearch.cpp
  core/multithread.cpp
  core/threadsafecounter.cpp
  core/threadsafequeue.cpp
  core/fancymath.cpp
  game/cards.cpp
  game/handeval.cpp
  game/arrangement.cpp
  game/rules.cpp
  game/scoring.cpp
  players/players.cpp
  players/montecarlo.cpp
  players/netplayer.cpp
  players/human.cpp
  dataio/npz.cpp
  dataio/trainingrows.cpp
  command/runtests.cpp
  command/match.cpp
  command/analyze.cpp
  command/benchmark.cpp
  command/exploit.cpp
  command/selfplay.cpp
  command/humanfeatures.cpp
  command/netq.cpp
  command/evalkeys.cpp
  command/record.cpp
  command/gui.cpp
  command/guiengine.cpp
  tests/tests.cpp
  main.cpp
)

target_include_directories(thirteen PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}/external/tclap-1.2.5/include
  ${CMAKE_CURRENT_SOURCE_DIR}/external/nlohmann_json
)
target_link_libraries(thirteen PRIVATE Threads::Threads)
target_compile_options(thirteen PRIVATE -Wall -Wextra -Wno-sign-compare)
if(USE_NATIVE)
  target_compile_options(thirteen PRIVATE -march=native)
endif()

# The data library, loaded by python/thirteen/data.py to expand compact training rows.
add_library(thirteen_data SHARED
  core/global.cpp
  core/rand.cpp
  core/rand_helpers.cpp
  core/hash.cpp
  core/md5.cpp
  core/sha2.cpp
  core/timer.cpp
  core/bsearch.cpp
  game/cards.cpp
  game/handeval.cpp
  game/arrangement.cpp
  game/rules.cpp
  game/scoring.cpp
  dataio/trainingrows.cpp
  dataio/trainingrows_c.cpp
)
target_link_libraries(thirteen_data PRIVATE Threads::Threads)
target_compile_options(thirteen_data PRIVATE -Wall -Wextra -Wno-sign-compare)
if(USE_NATIVE)
  target_compile_options(thirteen_data PRIVATE -march=native)
endif()

if(ONNXRUNTIME_ROOT)
  target_compile_definitions(thirteen PRIVATE USE_ONNXRUNTIME)
  target_include_directories(thirteen PRIVATE ${ONNXRUNTIME_ROOT}/include)
  target_link_libraries(thirteen PRIVATE ${ONNXRUNTIME_ROOT}/lib/libonnxruntime.so)
  set_target_properties(thirteen PROPERTIES BUILD_RPATH ${ONNXRUNTIME_ROOT}/lib INSTALL_RPATH ${ONNXRUNTIME_ROOT}/lib)
  message(STATUS "Using ONNX Runtime from ${ONNXRUNTIME_ROOT}")
else()
  message(STATUS "No ONNX Runtime: net players are disabled")
endif()
