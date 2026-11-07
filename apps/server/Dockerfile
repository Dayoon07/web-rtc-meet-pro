# Spring Boot 서버. 빌드 컨텍스트는 저장소 루트 (deploy/docker-compose.yml 참고)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# 의존성 먼저 받아 두면 소스만 바뀔 때 다시 받지 않는다
COPY apps/server/gradlew apps/server/settings.gradle.kts apps/server/build.gradle.kts ./
COPY apps/server/gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null
COPY apps/server/src src
RUN ./gradlew --no-daemon -q bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app && mkdir -p /data/uploads /data/chat-files && chown app /data/uploads /data/chat-files
COPY --from=build /src/build/libs/*.jar app.jar
USER app
ENV UPLOAD_DIR=/data/uploads CHAT_FILE_DIR=/data/chat-files RECORDING_DIR=/data/recordings
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
