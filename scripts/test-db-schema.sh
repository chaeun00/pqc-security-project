#!/usr/bin/env bash
# =============================================================
# scripts/test-db-schema.sh — Step 1-C DB 스키마 인수조건 검증
#
# 인수조건:
#   1. 테이블 4개 존재 (users/sessions/key_metadata/cbom_assets)
#   2. 인덱스 6개 존재 (idx_* 명명 인덱스)
#   3. su-exec 기반 gosu: 권한 강등 후 uid=70(postgres) 확인
# =============================================================
set -euo pipefail

CONTAINER="${DB_CONTAINER:-pqc-security-project-db-1}"
PSQL="docker exec -i ${CONTAINER} psql -U pqc -d pqc_db -t -A"
PASS=0
FAIL=0

ok()   { echo "==> PASS ✓ — $*"; PASS=$((PASS+1)); }
fail() { echo "==> FAIL ✗ — $*"; FAIL=$((FAIL+1)); }

echo "==> [test-db] DB 스키마 검증 시작 ──────────────────────"

# ── [1] 테이블 4개 확인 ────────────────────────────────────
echo "==> [1] 테이블 수 확인"
TABLE_COUNT=$(${PSQL} -c "
  SELECT count(*) FROM information_schema.tables
  WHERE table_schema='public' AND table_type='BASE TABLE';
" 2>/dev/null | tr -d ' ')

if [ "${TABLE_COUNT}" -eq 4 ]; then
    ok "테이블 4개 확인 (count=${TABLE_COUNT})"
else
    fail "테이블 수 불일치 (기대=4, 실제=${TABLE_COUNT})"
fi

# 각 테이블 존재 여부 개별 확인
for TABLE in users sessions key_metadata cbom_assets; do
    EXISTS=$(${PSQL} -c "
      SELECT count(*) FROM information_schema.tables
      WHERE table_schema='public' AND table_name='${TABLE}';
    " 2>/dev/null | tr -d ' ')
    if [ "${EXISTS}" -eq 1 ]; then
        ok "테이블 존재: ${TABLE}"
    else
        fail "테이블 없음: ${TABLE}"
    fi
done

# ── [2] 인덱스 6개 확인 (idx_* 명명) ──────────────────────
echo "==> [2] 인덱스 수 확인 (idx_* 명명)"
IDX_COUNT=$(${PSQL} -c "
  SELECT count(*) FROM pg_indexes
  WHERE schemaname='public' AND indexname LIKE 'idx_%';
" 2>/dev/null | tr -d ' ')

if [ "${IDX_COUNT}" -eq 6 ]; then
    ok "인덱스 6개 확인 (count=${IDX_COUNT})"
else
    fail "인덱스 수 불일치 (기대=6, 실제=${IDX_COUNT})"
fi

# 각 인덱스 존재 여부 개별 확인
for IDX in \
    idx_cbom_assets_asset \
    idx_key_metadata_payload \
    idx_sessions_user_id \
    idx_sessions_expires_at \
    idx_key_metadata_user_id \
    idx_key_metadata_algorithm_id; do
    EXISTS=$(${PSQL} -c "
      SELECT count(*) FROM pg_indexes
      WHERE schemaname='public' AND indexname='${IDX}';
    " 2>/dev/null | tr -d ' ')
    if [ "${EXISTS}" -eq 1 ]; then
        ok "인덱스 존재: ${IDX}"
    else
        fail "인덱스 없음: ${IDX}"
    fi
done

# ── [3] su-exec uid=70(postgres) 확인 ─────────────────────
echo "==> [3] su-exec 권한 강등 uid=70(postgres) 확인"
GOSU_UID=$(docker exec --user root "${CONTAINER}" /usr/local/bin/gosu postgres id -u 2>/dev/null || echo "ERROR")
if [ "${GOSU_UID}" = "70" ]; then
    ok "su-exec 권한 강등 uid=70 확인"
else
    fail "su-exec uid 불일치 (기대=70, 실제=${GOSU_UID})"
fi

# ── [4] CapEff=0 확인 (no-new-privileges + gosu exec 후 capability 전부 제거) ──
echo "==> [4] CapEff=0 확인"
CAP_EFF=$(docker exec --user root "${CONTAINER}" \
    sh -c "grep CapEff /proc/1/status 2>/dev/null" 2>/dev/null \
    | awk '{print $2}' | tr -d '[:space:]')
if [ "${CAP_EFF}" = "0000000000000000" ]; then
    ok "CapEff=0 확인 (모든 effective capability 제거됨)"
else
    fail "CapEff 불일치 (기대=0000000000000000, 실제=${CAP_EFF:-EMPTY})"
fi

# ── [5] setuid 바이너리 없음 확인 ─────────────────────────
echo "==> [5] setuid 바이너리 없음 확인"
SETUID_FILES=$(docker exec --user root "${CONTAINER}" \
    find / -xdev -perm -4000 -type f 2>/dev/null || true)
if [ -z "${SETUID_FILES}" ]; then
    ok "setuid 바이너리 없음 확인"
else
    fail "setuid 바이너리 발견: ${SETUID_FILES}"
fi

# ── 결과 집계 ──────────────────────────────────────────────
echo ""
echo "==> [test-db] 결과: PASS=${PASS} FAIL=${FAIL}"
if [ "${FAIL}" -gt 0 ]; then
    echo "==> [test-db] FAIL — 위 실패 항목 확인 필요"
    exit 1
fi
echo "==> [test-db] 모든 인수조건 통과 ✓"
