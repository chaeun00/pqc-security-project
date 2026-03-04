#!/usr/bin/env bash
# ==============================================================
# scripts/check-limits.sh — docker inspect vs .env Limit 비교
# Step 1-B rev.10: 수치 불일치 시 exit 1
#
# 사용법: bash scripts/check-limits.sh [.env_path]
#         make inspect-limits
# ==============================================================
set -euo pipefail

ENV_FILE="${1:-.env}"
[ -f "$ENV_FILE" ] || { echo "Error: $ENV_FILE 없음" >&2; exit 1; }

# 메모리 단위 → bytes 변환
mem_to_bytes() {
    local v="$1"
    local num="${v%[gGmMkK]}"
    case "${v}" in
        *[gG]) echo $(( num * 1073741824 )) ;;
        *[mM]) echo $(( num * 1048576 ))    ;;
        *[kK]) echo $(( num * 1024 ))        ;;
        *)     echo "$v"                     ;;
    esac
}

# 서비스별 .env prefix 매핑
declare -A SVC_PREFIX=(
    [crypto-engine]="CRYPTO_ENGINE"
    [api-gateway]="API_GATEWAY"
    [db]="DB"
    [dashboard]="DASHBOARD"
)

# docker-compose.yml ${VAR:-fallback} 기본값 (키 부재 시 사용)
declare -A DEFAULT_CPU=(
    [crypto-engine]="1.0"
    [api-gateway]="0.5"
    [db]="0.5"
    [dashboard]="0.25"
)
declare -A DEFAULT_MEM=(
    [crypto-engine]="512m"
    [api-gateway]="512m"
    [db]="256m"
    [dashboard]="128m"
)

FAIL=0

echo "==> Resource Limits 검증 (docker inspect vs ${ENV_FILE})"

for svc in crypto-engine api-gateway db dashboard; do
    prefix="${SVC_PREFIX[$svc]}"

    cid=$(docker compose ps -q "$svc" 2>/dev/null || true)
    if [ -z "$cid" ]; then
        echo "  $svc — not running (skip)"
        continue
    fi

    actual_nano=$(docker inspect "$cid" --format '{{.HostConfig.NanoCpus}}')
    actual_mem=$(docker inspect  "$cid" --format '{{.HostConfig.Memory}}')

    expected_cpu=$(grep "^${prefix}_CPU_LIMIT=" "$ENV_FILE" \
        | cut -d= -f2 | tr -d '"' | tr -d "'" | tr -d '\r' || true)
    expected_mem=$(grep "^${prefix}_MEM_LIMIT=" "$ENV_FILE" \
        | cut -d= -f2 | tr -d '"' | tr -d "'" | tr -d '\r' || true)

    # 키 부재 시 docker-compose.yml fallback 기본값으로 비교 (skip 대신 FAIL 방지)
    if [ -z "$expected_cpu" ]; then
        expected_cpu="${DEFAULT_CPU[$svc]}"
        echo "  $svc — ${prefix}_CPU_LIMIT 없음, fallback 사용: ${expected_cpu}"
    fi
    if [ -z "$expected_mem" ]; then
        expected_mem="${DEFAULT_MEM[$svc]}"
        echo "  $svc — ${prefix}_MEM_LIMIT 없음, fallback 사용: ${expected_mem}"
    fi

    # CPU: float → nanocpus (Epsilon 허용: ±1 nanocpu)
    expected_nano=$(echo "$expected_cpu * 1000000000" | bc | cut -d. -f1)
    expected_bytes=$(mem_to_bytes "$expected_mem")

    cpu_ok="PASS"; mem_ok="PASS"
    [ "$actual_nano" = "$expected_nano" ] || { cpu_ok="FAIL"; FAIL=1; }
    [ "$actual_mem"  = "$expected_bytes" ] || { mem_ok="FAIL"; FAIL=1; }

    echo "  ${svc}"
    echo "    CPU: actual=${actual_nano} ns  expected=${expected_nano} ns  [${cpu_ok}]"
    echo "    MEM: actual=${actual_mem} B    expected=${expected_bytes} B  [${mem_ok}]"
done

if [ "$FAIL" -ne 0 ]; then
    echo "==> FAIL: Limit 불일치 감지 — .env 값과 컨테이너 설정을 확인하세요" >&2
    exit 1
fi
echo "==> inspect-limits 통과 ✓"
