#!/bin/bash
# Builds the reduced ONNX Runtime of the app (android/build-ort.sh: the same version and operators, no telemetry) as a
# WebAssembly static library, with SIMD. Its output, $ORT_WASM_DIR (default build/ort-wasm), has include/ and
# lib/libonnxruntime_webassembly.a, for build-engine.sh. The source and the build, several GB, go to $ORT_WORK (default
# build/ort-work); an existing source checkout there (src-<version>, as android/build-ort.sh leaves it) is used.
# Needs python3 (3.10+), cmake, ninja and the emsdk: ONNX Runtime's own (cmake/external/emsdk, fetched by git) or
# $EMSDK, an emsdk of version $EMSDK_VERSION (default 6.0.13), which is linked there. The version of ONNX Runtime is
# the app's (android/app/build.gradle.kts), or $ORT_VERSION.
set -euo pipefail
cd "$(dirname "$0")"
version=${ORT_VERSION:-$(sed -n 's/^val ortVersion = "\(.*\)"/\1/p' ../android/app/build.gradle.kts)}
work=$(realpath -m "${ORT_WORK:-build/ort-work}")
out=$(realpath -m "${ORT_WASM_DIR:-build/ort-wasm}")
src="$work/src-$version"
mkdir -p "$work"
if [ ! -d "$src" ]; then
  git clone --depth 1 --branch "v$version" https://github.com/microsoft/onnxruntime.git "$src"
fi
if [ -n "${EMSDK:-}" ]; then
  rm -rf "$src/cmake/external/emsdk"
  ln -s "$(realpath "$EMSDK")" "$src/cmake/external/emsdk"
elif [ ! -f "$src/cmake/external/emsdk/emsdk" ]; then
  git -C "$src" submodule update --init --depth 1 cmake/external/emsdk
fi
# In a container the build runs as root, which build.py refuses unless told.
root_flag=()
if [ "$(id -u)" = 0 ]; then root_flag=(--allow_running_as_root); fi
python3 "$src/tools/ci_build/build.py" "${root_flag[@]}" --build_dir "$work/build-wasm" --config MinSizeRel \
  --build_wasm_static_lib --emsdk_version "${EMSDK_VERSION:-6.0.13}" --enable_wasm_simd \
  --include_ops_by_config "$PWD/../android/ort-ops.config" --skip_tests --no_telemetry --disable_ml_ops --disable_rtti \
  --disable_types float4 float8 optional sparsetensor string \
  --parallel --cmake_generator Ninja --skip_submodule_sync --compile_no_warning_as_error \
  --cmake_extra_defines CMAKE_POLICY_VERSION_MINIMUM=3.5 FETCHCONTENT_TRY_FIND_PACKAGE_MODE=NEVER onnxruntime_BUILD_UNIT_TESTS=OFF
mkdir -p "$out/lib" "$out/include"
cp "$work/build-wasm/MinSizeRel/libonnxruntime_webassembly.a" "$out/lib/"
cp "$src"/include/onnxruntime/core/session/*.h "$out/include/"
echo "$out"
