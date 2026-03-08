# ==============================================================
# crypto-engine — Multi-stage Dockerfile (Step 1-B rev.16)
# Stage 1 (builder): python:3.12-alpine + liboqs cmake build
# Stage 2 (runtime): python:3.12-alpine + liboqs.so COPY
# rev.13: bookworm → alpine (CVE-2025-7458 / CVE-2023-45853 제거)
#   sqlite-libs 3.51.2 / zlib 1.3.1 → 두 CRITICAL CVE 패치됨
#   cmake install prefix /usr → musl 기본 탐색 경로, ldconfig 불필요
# rev.16: OQS_INSTALL_PATH=/usr 추가 → oqs.py _load_liboqs() 정상 탐색
# ==============================================================

# ── Stage 1: builder ──────────────────────────────────────
FROM python:3.12-alpine AS builder

RUN apk add --no-cache \
    build-base \
    cmake \
    git \
    openssl-dev \
    linux-headers

# liboqs C 라이브러리 빌드 및 설치
# --prefix /usr: musl은 /usr/lib을 기본 탐색 → ldconfig 불필요
RUN git clone --depth 1 --branch 0.15.0 https://github.com/open-quantum-safe/liboqs /liboqs \
    && cmake -S /liboqs -B /liboqs/build \
        -DBUILD_SHARED_LIBS=ON \
        -DOQS_BUILD_ONLY_LIB=ON \
        -DOQS_DIST_BUILD=ON \
    && cmake --build /liboqs/build --parallel $(nproc) \
    && cmake --install /liboqs/build --prefix /usr \
    && rm -rf /liboqs

# oqs.py _load_liboqs()는 OQS_INSTALL_PATH만 탐색 → /usr 지정 필수
ENV OQS_INSTALL_PATH=/usr

# --copies: 멀티스테이지 복사 시 symlink 끊김 방지
RUN python -m venv --copies /venv

COPY requirements/ requirements/
RUN /venv/bin/pip install --no-cache-dir -r requirements/prod.txt

# 검증 레이어: import oqs 실패 시 빌드 중단
RUN /venv/bin/python -c "import oqs; print('oqs OK:', oqs.get_enabled_kem_mechanisms()[:1])"

# ── Stage 2: runtime ──────────────────────────────────────
FROM python:3.12-alpine AS runtime

# libssl3: liboqs.so 런타임 의존 (python:3.12-alpine 기본 포함이나 명시)
RUN apk add --no-cache libssl3 'zlib>=1.3.2-r0'

# nonroot 유저 생성 (Alpine addgroup/adduser 방식)
RUN addgroup -g 65532 nonroot && \
    adduser -D -u 65532 -G nonroot nonroot

COPY --from=builder /venv /venv

# liboqs.so 복사 (/usr/lib → musl 기본 탐색 경로, ldconfig 불필요)
COPY --from=builder /usr/lib/liboqs.so* /usr/lib/

ENV PATH="/venv/bin:$PATH"
ENV OQS_INSTALL_PATH=/usr

WORKDIR /app
RUN chown -R nonroot:nonroot /app
COPY --chown=nonroot:nonroot app/ /app/app/

USER nonroot

EXPOSE 8000

# --preload: master에서 algorithm_factory.py 1회 로드 → fork()로 worker 상속
# worker별 독립 keypair 생성 방지 → DSA public_key 항상 일치
CMD ["gunicorn", "-k", "uvicorn.workers.UvicornWorker", "-w", "2", "--preload", "-b", "0.0.0.0:8000", "app.main:app"]
