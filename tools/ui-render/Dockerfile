FROM eclipse-temurin:25-jdk-noble@sha256:5b14970485a676b41faa08f4a7bc8716cc20915daa1d581d7a75f37a8ebaf9a8

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl xvfb xauth libgl1-mesa-dri libglx-mesa0 libegl1 libxext6 libxi6 \
    libxrender1 libxtst6 libasound2t64 libfreetype6 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /renderer
RUN curl -fL --retry 3 https://github.com/Anuken/Mindustry/releases/download/v160.7/Mindustry.jar -o Mindustry.jar \
    && echo '36d94941a1639478ad1d9be25a9d802c6f77552f400c6da0338b7b019f63a0d1  Mindustry.jar' | sha256sum -c -
COPY Render.java .
RUN javac -cp Mindustry.jar Render.java
ENV LIBGL_ALWAYS_SOFTWARE=1 LP_NUM_THREADS=2 HOME=/tmp
# The container owns cleanup; temporary-directory cleanup must not mask the renderer's exit status.
ENTRYPOINT ["timeout", "--kill-after=5s", "45s", "xvfb-run", "-a", "-f", "/tmp/render.xauth", "-s", "-screen 0 1920x2400x24", "java", "-Xmx512m", "-XX:ActiveProcessorCount=2", "-cp", "/renderer/Mindustry.jar:/renderer", "Render"]
