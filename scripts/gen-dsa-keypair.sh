#!/bin/sh
# --preload 제거 실험용 DSA 키쌍 생성 스크립트
#
# 사용법:
#   ./scripts/gen-dsa-keypair.sh
#
# 출력된 두 줄을 config/crypto-engine.env 의
#   DSA_SECRET_KEY_B64=<값>
#   DSA_PUBLIC_KEY_B64=<값>
# 에 붙여넣은 뒤 DISABLE_PRELOAD=1 로 설정하고 재빌드.

set -e

IMAGE=$(docker images --format '{{.Repository}}:{{.Tag}}' | grep 'crypto-engine' | head -1)

if [ -z "$IMAGE" ]; then
  echo "[ERROR] crypto-engine 이미지가 없습니다. 먼저 빌드하세요:"
  echo "  docker-compose build crypto-engine"
  exit 1
fi

echo "[INFO] 키쌍 생성 중... (이미지: $IMAGE)"

docker run --rm \
  -e DSA_ALGORITHM_ID="${DSA_ALGORITHM_ID:-ML-DSA-65}" \
  "$IMAGE" \
  sh -c "python3 -c \"
import oqs, base64, os
algo = os.getenv('DSA_ALGORITHM_ID', 'ML-DSA-65')
with oqs.Signature(algo) as sig:
    pub = sig.generate_keypair()
    sec = sig.export_secret_key()
print('DSA_PUBLIC_KEY_B64=' + base64.b64encode(pub).decode())
print('DSA_SECRET_KEY_B64=' + base64.b64encode(sec).decode())
\""
