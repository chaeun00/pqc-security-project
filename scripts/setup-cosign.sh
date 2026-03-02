#!/usr/bin/env bash
# ==============================================================
# scripts/setup-cosign.sh — cosign 로컬 설치 스크립트
# Step 1-A rev.7: DCT → Cosign(Sigstore) 전환
#
# 사용법: bash scripts/setup-cosign.sh
#         make setup
# ==============================================================
set -euo pipefail

COSIGN_VERSION="v2.4.1"
INSTALL_DIR="${COSIGN_INSTALL_DIR:-/usr/local/bin}"

# OS / 아키텍처 감지
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)
case "${ARCH}" in
  x86_64)          ARCH="amd64" ;;
  aarch64 | arm64) ARCH="arm64" ;;
  *)
    echo "Error: 지원하지 않는 아키텍처: ${ARCH}" >&2
    exit 1
    ;;
esac

BINARY="cosign-${OS}-${ARCH}"
DOWNLOAD_URL="https://github.com/sigstore/cosign/releases/download/${COSIGN_VERSION}/${BINARY}"

echo "==> cosign ${COSIGN_VERSION} 설치 중... (${OS}/${ARCH})"
echo "    URL: ${DOWNLOAD_URL}"
echo "    설치 경로: ${INSTALL_DIR}/cosign"

# sudo 필요 여부 판단
if [ -w "${INSTALL_DIR}" ]; then
  SUDO=""
else
  SUDO="sudo"
  echo "    (${INSTALL_DIR} 쓰기 권한 없음 — sudo 사용)"
fi

curl -sSfL "${DOWNLOAD_URL}" -o /tmp/cosign-download
${SUDO} install -m 0755 /tmp/cosign-download "${INSTALL_DIR}/cosign"
rm -f /tmp/cosign-download

echo "==> 설치 완료:"
cosign version
