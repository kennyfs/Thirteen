# The battle server: the C++ engine (`thirteen gui`, with ONNX Runtime) and the Kotlin server around it.
# Build from the repository root: docker compose -f server/compose.yml build (see docs/battle.md). The default
# target, runtime, takes the network from a volume; bundled has it inside, for Kubernetes:
#   docker build -f server/Dockerfile --target bundled -t IMAGE .

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
    JAVA_OPTS="-Xmx256m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
USER thirteen
EXPOSE 8080
ENTRYPOINT ["/opt/thirteen/server/bin/thirteen-server"]

FROM runtime AS bundled
COPY --from=model /run2_net10.onnx /opt/thirteen/models/run2_net10.onnx
ENV THIRTEEN_MODEL=/opt/thirteen/models/run2_net10.onnx \
    THIRTEEN_MODEL_SHA256=56b56733ae86e3038c1ebb2ee274e3b1c19cf49b66e494e334e15580a052b7e8
