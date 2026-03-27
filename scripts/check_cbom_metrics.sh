#!/bin/bash
# Day 18 인수조건 2 검증 — cbom_assets_total 메트릭 확인
set -e

# 1. 로그인
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userId":"demo","password":"demo123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

echo "TOKEN: ${TOKEN:0:40}..."

# 2. HIGH 위험도 encrypt 요청
curl -s -X POST http://localhost:8080/api/encrypt \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"plaintext":"aGVsbG8=","risk_level":"HIGH"}' | head -c 200
echo

# 3. 메트릭 확인
echo "--- cbom_assets_total ---"
docker exec pqc-security-project-api-gateway-1 \
  curl -s http://localhost:8081/actuator/prometheus | grep cbom_assets_total
