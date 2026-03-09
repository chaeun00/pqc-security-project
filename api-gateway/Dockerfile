# ==============================================================
# api-gateway — Multi-stage Dockerfile (Day 1)
# Stage 1 (builder): Gradle 빌드 (JDK21)
# Stage 2 (runtime): JRE21 + 비루트 실행
# ==============================================================

# ── Stage 1: builder ──────────────────────────────────────
FROM gradle:8.8-jdk21-alpine AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./
COPY src ./src
RUN ./gradlew build -x test --no-daemon

# ── Stage 2: runtime ──────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# apk upgrade: CVE-2026-22184(zlib) 등 Alpine OS 패키지 CRITICAL 패치
# curl: HEALTHCHECK용 (BusyBox wget은 -f 플래그 미지원 → 크래시 감지 불가)
RUN apk upgrade --no-cache && \
    apk add --no-cache curl && \
    adduser -D -u 10001 nonroot

WORKDIR /app

COPY --chown=nonroot:nonroot --from=builder /app/build/libs/*.jar app.jar

USER nonroot

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=60s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
