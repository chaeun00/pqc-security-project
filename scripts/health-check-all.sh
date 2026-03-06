#!/usr/bin/env bash
# ==============================================================
# scripts/health-check-all.sh — Step 1-D 통합 헬스체크
#
# 범위: crypto-engine + db (Step 1 범위, api-gateway/dashboard 제외)
# 사용법: bash scripts/health-check-all.sh [.env_path]
#         make health-check          (빌드 포함)
#         NO_BUILD=1 make health-check (빌드 생략, 재검증)
# ==============================================================
set -euo pipefail

ENV_FILE="${1:-.env}"
[ -f "$ENV_FILE" ] || { echo "Error: $ENV_FILE 없음 (cp .env.example .env 후 설정)" >&2; exit 1; }
NO_BUILD="${NO_BUILD:-0}"

# 임시 파일 정리 trap (EXIT/ERR 공통)
CE_CID=""
_cleanup() {
    [ -n "$CE_CID" ] && \
        docker exec --user root "$CE_CID" rm -f /tmp/http_test.py /tmp/test_edge.py 2>/dev/null || true
}
trap _cleanup EXIT

# .env에서 DB 접속 정보 파싱 (기본값 fallback)
_env_val() {
    grep "^${1}=" "$ENV_FILE" 2>/dev/null \
        | cut -d= -f2 | tr -d '"' | tr -d "'" | tr -d '\r' \
        || true
}
POSTGRES_USER=$(_env_val POSTGRES_USER); POSTGRES_USER="${POSTGRES_USER:-pqc}"
POSTGRES_DB=$(_env_val POSTGRES_DB);    POSTGRES_DB="${POSTGRES_DB:-pqc_db}"

echo "==> Step 1-D 통합 헬스체크 시작 (NO_BUILD=${NO_BUILD})"

# ── [1/5] crypto-engine + db 기동 ────────────────────────
echo "[1/5] docker compose up -d crypto-engine db"
BUILD_FLAG="--build"
[ "$NO_BUILD" = "1" ] && BUILD_FLAG=""
# shellcheck disable=SC2086
docker compose --env-file "$ENV_FILE" up -d ${BUILD_FLAG} crypto-engine db

# ── [2/5] healthy 대기 (최대 120초) ──────────────────────
echo "[2/5] healthy 대기 (최대 120초)"
wait_healthy() {
    local svc="$1"
    local cid
    cid=$(docker compose ps -q "$svc" 2>/dev/null)
    [ -n "$cid" ] || { echo "  Error: $svc 컨테이너 없음" >&2; return 1; }
    echo "  대기: $svc (cid=${cid:0:12})"
    timeout 120 bash -c "
        until [ \"\$(docker inspect ${cid} --format '{{.State.Health.Status}}')\" = 'healthy' ]; do
            sleep 5
        done
    "
    echo "  $svc healthy ✓"
}
wait_healthy crypto-engine
wait_healthy db

# ── [3/5] KEM/DSA HTTP 왕복 + 엣지 케이스 테스트 ────────
echo "[3/5] KEM/DSA 테스트 (http_test.py + test_edge.py)"
CE_CID=$(docker compose ps -q crypto-engine)
[ -n "$CE_CID" ] || { echo "  Error: crypto-engine 컨테이너 없음" >&2; exit 1; }
docker cp scripts/http_test.py "${CE_CID}:/tmp/http_test.py"
docker cp scripts/test_edge.py "${CE_CID}:/tmp/test_edge.py"
docker exec "${CE_CID}" /venv/bin/python /tmp/http_test.py
docker exec "${CE_CID}" /venv/bin/python /tmp/test_edge.py

# ── [4/5] DB 스키마 검증 (테이블 4 + GIN 인덱스 6) ──────
echo "[4/5] DB 스키마 검증"
DB_CID=$(docker compose ps -q db)
[ -n "$DB_CID" ] || { echo "  Error: db 컨테이너 없음" >&2; exit 1; }
PSQL="docker exec -i ${DB_CID} psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} -t -A"

TABLE_COUNT=$(${PSQL} -c \
    "SELECT count(*) FROM information_schema.tables \
     WHERE table_schema='public' AND table_type='BASE TABLE';" \
    2>/dev/null | tr -d ' ')
echo "  테이블 수: ${TABLE_COUNT}"
[ "${TABLE_COUNT}" -eq 4 ] || { echo "FAIL: 테이블 수 불일치 (기대=4, 실제=${TABLE_COUNT})" >&2; exit 1; }
echo "  PASS ✓ 테이블 4개"

IDX_COUNT=$(${PSQL} -c \
    "SELECT count(*) FROM pg_indexes \
     WHERE schemaname='public' AND indexname LIKE 'idx_%';" \
    2>/dev/null | tr -d ' ')
echo "  인덱스 수: ${IDX_COUNT}"
[ "${IDX_COUNT}" -eq 6 ] || { echo "FAIL: 인덱스 수 불일치 (기대=6, 실제=${IDX_COUNT})" >&2; exit 1; }
echo "  PASS ✓ GIN 인덱스 6개"

# ── [5/5] Resource Limits 수치 비교 ──────────────────────
echo "[5/5] Resource Limits 수치 비교 (check-limits.sh)"
bash scripts/check-limits.sh "$ENV_FILE"

# TODO: TRIVY=1 설정 시 로컬 Trivy 스캔 (기본은 CI 위임)
# [ "${TRIVY:-0}" = "1" ] && trivy image --vex security/vex.json --severity CRITICAL --exit-code 1 pqc-db:secure

echo "==> Step 1-D 통합 헬스체크 통과 ✓"
