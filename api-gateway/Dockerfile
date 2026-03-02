# ==============================================================
# api-gateway — Multi-stage Dockerfile (Phase 2에서 완성)
# Stage 1 (builder): Maven 빌드
# Stage 2 (runtime): JRE 런타임 + 비루트 실행
# rev.5: Rootless 전략 = Dockerfile USER nonroot
# ==============================================================

# ── Stage 1: builder ──────────────────────────────────────
# TODO(Phase 2): pom.xml 작성 후 완성
FROM maven:3.9-eclipse-temurin-21 AS builder
# COPY pom.xml .
# COPY src/ src/
# RUN mvn package -DskipTests --no-transfer-progress

# ── Stage 2: runtime ──────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# 비루트 유저 생성 (rev.5 — USER nonroot 전략)
RUN adduser -D -u 10001 nonroot

# TODO(Phase 2): COPY --from=builder
# COPY --from=builder /target/*.jar app.jar

WORKDIR /app

USER nonroot

EXPOSE 8080

# TODO(Phase 2): ENTRYPOINT 확정
# ENTRYPOINT ["java", "-jar", "app.jar"]
