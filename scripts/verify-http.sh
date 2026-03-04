#!/usr/bin/env bash
# ==============================================================
# scripts/verify-http.sh — crypto-engine HTTP 왕복 테스트
# Step 1-B rev.10: docker exec 기반 (포트 미노출 네트워크 대응)
#
# 방식: http_test.py → docker cp → /venv/bin/python 실행 → cleanup
# 사용법: bash scripts/verify-http.sh
#         make verify-all
# ==============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_SCRIPT="${SCRIPT_DIR}/http_test.py"

[ -f "$TEST_SCRIPT" ] || { echo "Error: ${TEST_SCRIPT} 없음" >&2; exit 1; }

CID=$(docker compose ps -q crypto-engine 2>/dev/null || true)
[ -n "$CID" ] || {
    echo "Error: crypto-engine not running (make up 먼저 실행)" >&2
    exit 1
}

echo "==> [HTTP via docker exec] container: ${CID:0:12}"

# 테스트 스크립트를 컨테이너에 복사
docker cp "$TEST_SCRIPT" "${CID}:/tmp/http_test.py"

# 컨테이너 내부에서 실행 (cleanup 보장)
docker exec "$CID" /venv/bin/python /tmp/http_test.py
EXIT_CODE=$?

# 정리
docker exec --user root "$CID" rm -f /tmp/http_test.py 2>/dev/null || true

exit $EXIT_CODE
