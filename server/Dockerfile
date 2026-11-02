# The battle server: the C++ engine (`thirteen gui`, with ONNX Runtime) and the Kotlin server around it.
# Build from the repository root: docker compose -f server/compose.yml build (see docs/battle.md).

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

FROM gradle:8.14.3-jdk17 AS server
WORKDIR /src
COPY protocol protocol
COPY server/settings.gradle.kts server/build.gradle.kts server/
COPY server/src server/src
RUN gradle -p server --no-daemon :protocol:test installDist

# The engine needs the C++ runtime of the system it was built on: the same Ubuntu, with a JRE.
FROM ubuntu:26.04
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
