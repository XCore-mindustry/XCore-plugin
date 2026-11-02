FROM eclipse-temurin:25-jdk-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /server
ARG MINDUSTRY_VERSION=v160.7
RUN curl --connect-timeout 30 --max-time 600 --retry 2 -fsSL -o server.jar "https://github.com/Anuken/Mindustry/releases/download/${MINDUSTRY_VERSION}/server-release.jar"
COPY devtools/mindustry/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
EXPOSE 6567/tcp 6567/udp
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["java", "-Dfile.encoding=UTF-8", "-jar", "server.jar"]
