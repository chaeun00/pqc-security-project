# ==============================================================
# crypto-engine — Multi-stage Dockerfile (Step 1-B에서 완성)
# Stage 1 (builder): liboqs C 라이브러리 빌드
# Stage 2 (runtime): 런타임 아티팩트만 복사 + 비루트 실행
# rev.5: Rootless 전략 = Dockerfile USER nonroot
# ==============================================================

# ── Stage 1: builder ──────────────────────────────────────
# TODO(Step 1-B): liboqs 빌드 완성
FROM python:3.12-slim AS builder
# RUN apt-get update && apt-get install -y --no-install-recommends \
#       cmake libssl-dev \
#  && git clone --depth 1 https://github.com/open-quantum-safe/liboqs \
#  && cmake -S liboqs -B liboqs/build -DBUILD_SHARED_LIBS=ON \
#  && cmake --build liboqs/build --parallel $(nproc) \
#  && cmake --install liboqs/build
# COPY requirements/prod.txt .
# RUN pip install --no-cache-dir --prefix=/install -r prod.txt

# ── Stage 2: runtime ──────────────────────────────────────
FROM python:3.12-slim AS runtime

# 비루트 유저 생성 (rev.5 — USER nonroot 전략)
RUN adduser --disabled-password --no-create-home --uid 10001 nonroot

# TODO(Step 1-B): COPY --from=builder, app 코드 복사
# COPY --from=builder /install /usr/local
# COPY --from=builder /usr/local/lib/liboqs.so* /usr/local/lib/
# COPY app/ /app/

WORKDIR /app

USER nonroot

EXPOSE 8000

# TODO(Step 1-B): CMD 확정
# CMD ["gunicorn", "-k", "uvicorn.workers.UvicornWorker", "-b", "0.0.0.0:8000", "app.main:app"]
