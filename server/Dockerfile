# The battle server: the C++ engine (`thirteen gui`, with ONNX Runtime) and the Kotlin server around it; and the site:
# the web page (web/, its engine as WebAssembly) served by Caddy, which passes /ws on to the server.
# Build from the repository root: docker compose -f server/compose.yml build (see docs/battle.md). The target runtime
# (the server) takes the network from a volume; bundled has it inside, for Kubernetes; site is Caddy with the page:
#   docker build -f server/Dockerfile --target bundled -t IMAGE .
#   docker build -f server/Dockerfile --target site -t IMAGE .

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

# The engine (cpp/ and GuiEngine) as WebAssembly, with the network beside it. The two pieces that change rarely and
# take long to make come prebuilt from the v2.1.0 release, checked against their SHA-256:
# - the app's reduced ONNX Runtime 1.31.1 (android/ort-ops.config) as a WebAssembly static library, made by
#   web/build-ort-wasm.sh (rebuild it there and upload a new one when the version or the operators change);
# - the built-in network as the app has it, the v1.0.0 release's with its weights as float16 (python/fp16_weights.py).
FROM emscripten/emsdk:6.0.13 AS web-engine
RUN apt-get update && apt-get install -y --no-install-recommends ninja-build python3-venv curl ca-certificates \
  && rm -rf /var/lib/apt/lists/* \
  && python3 -m venv /opt/tools && /opt/tools/bin/pip install --no-cache-dir cmake==4.4.4
ENV PATH=/opt/tools/bin:$PATH
RUN curl -fsSL -o /ort-wasm.tar.gz https://github.com/kennyfs/Thirteen/releases/download/v2.1.0/ort-wasm-1.31.1.tar.gz \
  && echo "b8c7a39adb38f83137eebd39e84ffbbad434cc7218e7f53067e59e94977e67ce  /ort-wasm.tar.gz" | sha256sum -c - \
  && mkdir /opt/ort-wasm && tar -xzf /ort-wasm.tar.gz -C /opt/ort-wasm && rm /ort-wasm.tar.gz \
  && curl -fsSL -o /run2_net10.onnx https://github.com/kennyfs/Thirteen/releases/download/v2.1.0/run2_net10-fp16.onnx \
  && echo "a80f66c0f9500523f48867f95ae71cd49e285070bf17ee0b5033991f312dd86d  /run2_net10.onnx" | sha256sum -c -
WORKDIR /src
COPY cpp cpp
COPY web/engine web/engine
COPY web/build-engine.sh web/
# The emsdk image has its tools on PATH already.
RUN ORT_WASM_DIR=/opt/ort-wasm MODEL=/run2_net10.onnx web/build-engine.sh

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

# A JRE of only the modules the server uses (jdeps finds them in its jars), about 150 MB less than the whole one.
FROM ubuntu:26.04 AS jre
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-21-jdk-headless binutils \
  && rm -rf /var/lib/apt/lists/*
COPY --from=server /src/server/build/install/thirteen-server/lib /server-lib
# jdeps may print warnings (split packages) before the list: the list is its last line, checked.
RUN modules=$(jdeps --ignore-missing-deps --print-module-deps --multi-release 21 /server-lib/*.jar 2>/dev/null | tail -n 1) \
  && echo "modules: $modules" && echo "$modules" | grep -Eq '^[a-z][a-z0-9.]*(,[a-z][a-z0-9.]*)*$' \
  && jlink --add-modules "$modules,jdk.unsupported" --strip-debug --no-man-pages --no-header-files --compress zip-6 \
    --output /opt/jre

# The engine needs the C++ runtime of the system it was built on: the same Ubuntu, with the JRE above.
FROM ubuntu:26.04 AS runtime
COPY --from=jre /opt/jre /opt/jre
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin thirteen
# The engine finds ONNX Runtime by the path it was built with (its rpath).
COPY --from=engine /src/cpp/external/onnxruntime/lib /src/cpp/external/onnxruntime/lib
COPY --from=engine /src/cpp/build/thirteen /opt/thirteen/thirteen
COPY --from=server /src/server/build/install/thirteen-server /opt/thirteen/server
ENV JAVA_HOME=/opt/jre \
    PATH=/opt/jre/bin:$PATH \
    THIRTEEN_BINARY=/opt/thirteen/thirteen \
    THIRTEEN_MODEL=/models/run2_net10.onnx \
    JAVA_OPTS="-Xmx128m -Xss512k -XX:+UseSerialGC -XX:ReservedCodeCacheSize=32m -XX:MaxMetaspaceSize=64m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
USER thirteen
EXPOSE 8080
ENTRYPOINT ["/opt/thirteen/server/bin/thirteen-server"]

FROM runtime AS bundled
COPY --from=model /run2_net10.onnx /opt/thirteen/models/run2_net10.onnx
ENV THIRTEEN_MODEL=/opt/thirteen/models/run2_net10.onnx \
    THIRTEEN_MODEL_SHA256=56b56733ae86e3038c1ebb2ee274e3b1c19cf49b66e494e334e15580a052b7e8
