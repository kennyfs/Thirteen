#!/bin/bash
# Downloads the prebuilt CPU build of ONNX Runtime (about 6 MB) into a directory, by default cpp/external/onnxruntime.
# Build with: cmake -S cpp -B cpp/build -DONNXRUNTIME_ROOT=<that directory>
set -euo pipefail
VERSION=1.20.1
DEST=${1:-$(dirname "$0")/../cpp/external/onnxruntime}
mkdir -p "$DEST"
TMP=$(mktemp -d)
curl -fL -o "$TMP/ort.tgz" "https://github.com/microsoft/onnxruntime/releases/download/v${VERSION}/onnxruntime-linux-x64-${VERSION}.tgz"
tar -xzf "$TMP/ort.tgz" -C "$TMP"
cp -r "$TMP/onnxruntime-linux-x64-${VERSION}/." "$DEST/"
rm -rf "$TMP"
echo "ONNX Runtime ${VERSION} in $DEST"
