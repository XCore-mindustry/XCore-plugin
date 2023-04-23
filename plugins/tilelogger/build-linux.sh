chmod +x ./gradlew
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew build
sudo mv build/libs/tileLogger/shared/libTileLogger.so /usr/lib/