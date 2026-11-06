#!/bin/bash
# Builds a reduced ONNX Runtime for the app: the release's ONNX Runtime version with only the operators in
# ort-ops.config (those of the networks, as python/thirteen/model.py exports them, and the fused ones ORT's optimizer
# makes of them), without telemetry (ORT's builds send Microsoft usage data by default), the classic ML operators or
# the data types no network has; a library a fraction of the AAR's 34 MB. Its output, $THIRTEEN_ORT_DIR (default build/ort),
# has the AAR's layout (headers/, jni/<abi>/libonnxruntime.so): build the app with THIRTEEN_ORT_DIR set to use it.
# ABIs: $ORT_ABIS (default "arm64-v8a x86_64", the debug build's; the release takes arm64-v8a only). The source and
# the builds, several GB, go to $ORT_WORK (default build/ort-work). Needs python3 (3.10+), cmake, ninja, and the SDK and
# NDK as in build-apk.sh; a network that adds operators needs them added to ort-ops.config.
set -euo pipefail
cd "$(dirname "$0")"
if [ -f local.env ]; then set -a; . ./local.env; set +a; fi
export ANDROID_HOME=${ANDROID_HOME:-~/Android/Sdk}
version=$(sed -n 's/^val ortVersion = "\(.*\)"/\1/p' app/build.gradle.kts)
ndk=$(sed -n 's/^ *ndkVersion = "\(.*\)"/\1/p' app/build.gradle.kts)
api=$(sed -n 's/^ *minSdk = \([0-9]*\)/\1/p' app/build.gradle.kts)
work=$(realpath -m "${ORT_WORK:-build/ort-work}")
out=$(realpath -m "${THIRTEEN_ORT_DIR:-build/ort}")
mkdir -p "$work"
if [ ! -d "$work/src-$version" ]; then
  git clone --depth 1 --branch "v$version" https://github.com/microsoft/onnxruntime.git "$work/src-$version"
fi
for abi in ${ORT_ABIS:-arm64-v8a x86_64}; do
  python3 "$work/src-$version/tools/ci_build/build.py" --build_dir "$work/build-$abi" --config MinSizeRel \
    --android --android_sdk_path "$ANDROID_HOME" --android_ndk_path "$ANDROID_HOME/ndk/$ndk" --android_abi "$abi" \
    --android_api "$api" --build_shared_lib --include_ops_by_config "$PWD/ort-ops.config" --enable_lto --skip_tests \
    --no_telemetry --disable_ml_ops --disable_rtti --disable_types float4 float8 optional sparsetensor string \
    --parallel "${JOBS:-$(nproc)}" --cmake_generator Ninja --skip_submodule_sync --compile_no_warning_as_error \
    --cmake_extra_defines CMAKE_POLICY_VERSION_MINIMUM=3.5 FETCHCONTENT_TRY_FIND_PACKAGE_MODE=NEVER
  mkdir -p "$out/jni/$abi"
  cp "$work/build-$abi/MinSizeRel/libonnxruntime.so" "$out/jni/$abi/"
done
mkdir -p "$out/headers"
cp "$work/src-$version"/include/onnxruntime/core/session/*.h "$out/headers/"
echo "$out"
