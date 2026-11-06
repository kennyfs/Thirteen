#!/bin/bash
# Builds the engine (cpp/ and GuiEngine, engine/) as WebAssembly into public/engine (engine.js and engine.wasm), and
# copies the app's built-in network (or $MODEL) into public/models: what the page needs besides its own code. Needs the
# emsdk activated (source emsdk_env.sh, for emcmake), cmake, ninja, and the ONNX Runtime of build-ort-wasm.sh in
# $ORT_WASM_DIR (default build/ort-wasm). The build goes to $ENGINE_BUILD (default build/engine).
set -euo pipefail
cd "$(dirname "$0")"
ort=$(realpath -m "${ORT_WASM_DIR:-build/ort-wasm}")
build=$(realpath -m "${ENGINE_BUILD:-build/engine}")
emcmake cmake -S engine -B "$build" -G Ninja -DCMAKE_BUILD_TYPE=Release -DORT_WASM_DIR="$ort"
cmake --build "$build" --parallel
mkdir -p public/engine public/models
cp "$build/engine.js" "$build/engine.wasm" public/engine/
cp "${MODEL:-../android/app/src/main/assets/run2_net10.onnx}" public/models/run2_net10.onnx
ls -l public/engine public/models
