# The battle server: the C++ engine (`thirteen gui`, with ONNX Runtime) and the Kotlin server around it; and the site:
# the web page (web/, its engine as WebAssembly) served by Caddy, which passes /ws on to the server.
# Build from the repository root: docker compose -f server/compose.yml build (see docs/battle.md). The target runtime
# (the server) takes the network from a volume; bundled has it inside, for Kubernetes; site is Caddy with the page:
#   docker build -f server/Dockerfile --target bundled -t IMAGE .
#   docker build -f server/Dockerfile --target site -t IMAGE .
# The first build of site takes about an hour (ONNX Runtime for WebAssembly); its layer is kept until the version of
# ONNX Runtime or the build script changes.

# The engine, without -march=native so the image runs on any x86-64 host.
FROM ubuntu:26.04 AS engine
RUN apt-get update && apt-get install -y --no-install-recommends build-essential cmake curl ca-certificates \
  && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY scripts scripts
# ONNX Runtime's tarball is checked against its SHA-256 in the script.
RUN scripts/fetch_onnxruntime.sh /src/cpp/external/onnxruntime
COPY cpp cpp
RUN cmake -S cpp -B cpp/build -DCMAKE_BUILD_TYPE=Release -DUSE_NATIVE=OFF -DONNXRUNTIME_ROOT=/src/cpp/external/onnxruntime \
  && cmake --build cpp/build -j"$(nproc)" --target thirteen \
  && cpp/build/thirteen runtests

# The network of the v1.0.0 release, checked against its SHA-256 (the server checks it again at start).
FROM engine AS model
RUN curl -fsSL -o /run2_net10.onnx https://github.com/kennyfs/Thirteen/releases/download/v1.0.0/run2_net10.onnx \
  && echo "56b56733ae86e3038c1ebb2ee274e3b1c19cf49b66e494e334e15580a052b7e8  /run2_net10.onnx" | sha256sum -c -

FROM gradle:8.14.3-jdk17 AS server
WORKDIR /src
COPY protocol protocol
COPY server/settings.gradle.kts server/build.gradle.kts server/
COPY server/src server/src
RUN gradle -p server --no-daemon :protocol:test installDist

# ---- The web page ----

# The version of ONNX Runtime, the app's: alone, so that other changes to the app's build keep the next stage's layer.
FROM ubuntu:26.04 AS ort-version
COPY android/app/build.gradle.kts /build.gradle.kts
RUN sed -n 's/^val ortVersion = "\(.*\)"/\1/p' /build.gradle.kts > /ort-version && test -s /ort-version

# The app's reduced ONNX Runtime (its operators, android/ort-ops.config) as a WebAssembly static library, in the
# emsdk's own image. Several GB of source and build, removed in the same layer. build.py reads the operator list
# with the flatbuffers module.
FROM emscripten/emsdk:6.0.13 AS web-ort
RUN apt-get update && apt-get install -y --no-install-recommends git ninja-build python3-venv \
  && rm -rf /var/lib/apt/lists/* \
  && python3 -m venv /opt/tools && /opt/tools/bin/pip install --no-cache-dir cmake==4.4.4 flatbuffers==25.12.19
ENV PATH=/opt/tools/bin:$PATH
WORKDIR /src
COPY --from=ort-version /ort-version /ort-version
COPY android/ort-ops.config android/ort-ops.config
COPY web/build-ort-wasm.sh web/
RUN ORT_VERSION=$(cat /ort-version) EMSDK=/emsdk ORT_WORK=/tmp/ort-work ORT_WASM_DIR=/opt/ort-wasm web/build-ort-wasm.sh \
  && rm -rf /tmp/ort-work

# The built-in network as the app has it: the v1.0.0 release's, its weights as float16 (python/fp16_weights.py), the
# same bytes as the app's asset (checked).
FROM python:3.13-slim AS web-model
RUN pip install --no-cache-dir onnx==1.23.2 numpy==2.5.4
COPY python/fp16_weights.py /fp16_weights.py
RUN python -c "import urllib.request; urllib.request.urlretrieve('https://github.com/kennyfs/Thirteen/releases/download/v1.0.0/run2_net10.onnx', '/fp32.onnx')" \
  && echo "56b56733ae86e3038c1ebb2ee274e3b1c19cf49b66e494e334e15580a052b7e8  /fp32.onnx" | sha256sum -c - \
  && python /fp16_weights.py /fp32.onnx /run2_net10.onnx \
  && echo "a80f66c0f9500523f48867f95ae71cd49e285070bf17ee0b5033991f312dd86d  /run2_net10.onnx" | sha256sum -c -

# The engine (cpp/ and GuiEngine) as WebAssembly, with the network beside it.
FROM web-ort AS web-engine
COPY cpp cpp
COPY web/engine web/engine
COPY web/build-engine.sh web/
COPY --from=web-model /run2_net10.onnx /run2_net10.onnx
RUN . /emsdk/emsdk_env.sh && ORT_WASM_DIR=/opt/ort-wasm MODEL=/run2_net10.onnx web/build-engine.sh

# The page, built by Vite with the app's strings.
FROM node:24-slim AS web-page
WORKDIR /src/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY android/app/src/main/res/values/strings.xml /src/android/app/src/main/res/values/strings.xml
COPY android/app/src/main/res/values-zh-rTW/strings.xml /src/android/app/src/main/res/values-zh-rTW/strings.xml
COPY web/ ./
COPY --from=web-engine /src/web/public/engine public/engine
COPY --from=web-engine /src/web/public/models public/models
RUN npm run build

# Caddy with the page in /srv and its Caddyfile.
FROM caddy:2.8 AS site
COPY server/Caddyfile /etc/caddy/Caddyfile
COPY --from=web-page /src/web/dist /srv

# ---- The battle server ----

# The engine needs the C++ runtime of the system it was built on: the same Ubuntu, with a JRE.
FROM ubuntu:26.04 AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-21-jre-headless \
  && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin thirteen
# The engine finds ONNX Runtime by the path it was built with (its rpath).
COPY --from=engine /src/cpp/external/onnxruntime/lib /src/cpp/external/onnxruntime/lib
COPY --from=engine /src/cpp/build/thirteen /opt/thirteen/thirteen
COPY --from=server /src/server/build/install/thirteen-server /opt/thirteen/server
ENV THIRTEEN_BINARY=/opt/thirteen/thirteen \
    THIRTEEN_MODEL=/models/run2_net10.onnx \
    JAVA_OPTS="-Xmx128m -Xss512k -XX:+UseSerialGC -XX:ReservedCodeCacheSize=32m -XX:MaxMetaspaceSize=64m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
USER thirteen
EXPOSE 8080
ENTRYPOINT ["/opt/thirteen/server/bin/thirteen-server"]

FROM runtime AS bundled
COPY --from=model /run2_net10.onnx /opt/thirteen/models/run2_net10.onnx
ENV THIRTEEN_MODEL=/opt/thirteen/models/run2_net10.onnx \
    THIRTEEN_MODEL_SHA256=56b56733ae86e3038c1ebb2ee274e3b1c19cf49b66e494e334e15580a052b7e8
