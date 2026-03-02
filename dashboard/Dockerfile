# ==============================================================
# dashboard — Multi-stage Dockerfile (Phase 3에서 완성)
# Stage 1 (builder): Node.js 빌드
# Stage 2 (runtime): Nginx 정적 서빙 + 비루트 실행
# rev.5: Rootless 전략 = Dockerfile USER nonroot (nginx 유저)
# ==============================================================

# ── Stage 1: builder ──────────────────────────────────────
# TODO(Phase 3): package.json 작성 후 완성
FROM node:20-alpine AS builder
# COPY package*.json .
# RUN npm ci
# COPY src/ src/
# RUN npm run build

# ── Stage 2: runtime ──────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

# nginx 유저는 이미지에 포함 (uid=101) — 별도 adduser 불필요
# rev.5 — USER nonroot 전략: nginx 유저로 비루트 실행

# TODO(Phase 3): COPY --from=builder
# COPY --from=builder /app/dist /usr/share/nginx/html
# COPY nginx.conf /etc/nginx/conf.d/default.conf

USER nginx

EXPOSE 3000
