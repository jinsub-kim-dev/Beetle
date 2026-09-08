# --- Build stage ---
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

# Gradle 래퍼와 빌드 스크립트를 먼저 복사해 의존성 레이어를 캐싱한다.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# --- Runtime stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# 루트가 아닌 사용자로 실행한다.
RUN groupadd --system beetle && useradd --system --gid beetle beetle
COPY --from=builder /workspace/build/libs/*.jar app.jar
USER beetle

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
