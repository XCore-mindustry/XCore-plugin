FROM eclipse-temurin:25-jdk-noble@sha256:5b14970485a676b41faa08f4a7bc8716cc20915daa1d581d7a75f37a8ebaf9a8

ARG MINDUSTRY_VERSION
ARG MINDUSTRY_SHA256
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl xvfb xauth libgl1-mesa-dri libglx-mesa0 libegl1 libxext6 libxi6 \
    libxrender1 libxtst6 libasound2t64 libfreetype6 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /renderer
RUN test -n "$MINDUSTRY_VERSION" && test -n "$MINDUSTRY_SHA256" \
    && curl -fL --retry 3 "https://github.com/Anuken/Mindustry/releases/download/$MINDUSTRY_VERSION/Mindustry.jar" -o Mindustry.jar \
    && echo "$MINDUSTRY_SHA256  Mindustry.jar" | sha256sum -c -
COPY src src
RUN javac -cp Mindustry.jar -d classes $(find src -name '*.java')
ENV LIBGL_ALWAYS_SOFTWARE=1 LP_NUM_THREADS=2 HOME=/tmp
ENTRYPOINT ["xvfb-run", "-a", "-f", "/tmp/render.xauth", "-s", "-screen 0 1920x2400x24", \
    "java", "-Xmx512m", "-XX:ActiveProcessorCount=2", "--enable-native-access=ALL-UNNAMED", \
    "-cp", "/renderer/Mindustry.jar:/renderer/classes", "org.xcore.testkit.render.MenuRender"]
