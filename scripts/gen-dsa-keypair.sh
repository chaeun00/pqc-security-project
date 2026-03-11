#!/bin/sh
# --preload 제거 실험용 DSA 키쌍 생성 스크립트
#
# 사용법:
#   ./scripts/gen-dsa-keypair.sh
#
# 결과를 config/crypto-engine.env 에 자동 기록한다.
# 이후 DISABLE_PRELOAD=1 로 설정하고 재빌드.
#
# ⚠️  WARNING: config/crypto-engine.env 는 절대 git commit 하지 마세요.
#              .gitignore 에 등록되어 있는지 반드시 확인하세요.

set -e

ENV_FILE="$(dirname "$0")/../config/crypto-engine.env"
ENV_FILE="$(realpath "$ENV_FILE")"

IMAGE=$(docker images --format '{{.Repository}}:{{.Tag}}' | grep 'crypto-engine' | head -1)

if [ -z "$IMAGE" ]; then
  echo "[ERROR] crypto-engine 이미지가 없습니다. 먼저 빌드하세요:"
  echo "  docker-compose build crypto-engine"
  exit 1
fi

echo "[INFO] 키쌍 생성 중... (이미지: $IMAGE)"

# 키쌍 생성 (stdout 캡처)
KEYPAIR=$(docker run --rm \
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
\"")

NEW_PUB=$(echo "$KEYPAIR" | grep '^DSA_PUBLIC_KEY_B64=' | cut -d= -f2-)
NEW_SEC=$(echo "$KEYPAIR" | grep '^DSA_SECRET_KEY_B64=' | cut -d= -f2-)

if [ -z "$NEW_PUB" ] || [ -z "$NEW_SEC" ]; then
  echo "[ERROR] 키쌍 생성 실패"
  exit 1
fi

# config/crypto-engine.env 의 기존 값 교체 (sed in-place)
sed -i "s|^DSA_PUBLIC_KEY_B64=.*|DSA_PUBLIC_KEY_B64=${NEW_PUB}|" "$ENV_FILE"
sed -i "s|^DSA_SECRET_KEY_B64=.*|DSA_SECRET_KEY_B64=${NEW_SEC}|" "$ENV_FILE"

# 소유자만 읽기/쓰기 (키 파일 보호)
chmod 600 "$ENV_FILE"

echo "[OK] 키쌍이 ${ENV_FILE} 에 기록되었습니다."
echo ""
echo "⚠️  WARNING: 이 파일을 git commit 하지 마세요!"
echo "   git check-ignore -v config/crypto-engine.env  # .gitignore 등록 확인"
