#!/usr/bin/env bash
# ==============================================================
# scripts/setup-trivy.sh — Trivy 로컬 설치 스크립트
# Step 1-B: Trivy 이미지 취약점 스캐너 설치
#
# 사용법: bash scripts/setup-trivy.sh
#         make setup-trivy
# ==============================================================
set -euo pipefail

TRIVY_VERSION="${TRIVY_VERSION:-0.69.3}"
INSTALL_DIR="${TRIVY_INSTALL_DIR:-/usr/local/bin}"

# OS / 아키텍처 감지
OS=$(uname -s)
ARCH=$(uname -m)

case "${OS}" in
  Linux)  OS_LABEL="Linux" ;;
  Darwin) OS_LABEL="macOS" ;;
  *)
    echo "Error: 지원하지 않는 OS: ${OS}" >&2
    exit 1
    ;;
esac

case "${ARCH}" in
  x86_64)          ARCH_LABEL="64bit" ;;
  aarch64 | arm64) ARCH_LABEL="ARM64" ;;
  *)
    echo "Error: 지원하지 않는 아키텍처: ${ARCH}" >&2
    exit 1
    ;;
esac

ARCHIVE="trivy_${TRIVY_VERSION}_${OS_LABEL}-${ARCH_LABEL}.tar.gz"
DOWNLOAD_URL="https://github.com/aquasecurity/trivy/releases/download/v${TRIVY_VERSION}/${ARCHIVE}"

echo "==> Trivy v${TRIVY_VERSION} 설치 중... (${OS_LABEL}/${ARCH_LABEL})"
echo "    URL: ${DOWNLOAD_URL}"
echo "    설치 경로: ${INSTALL_DIR}/trivy"

# sudo 필요 여부 판단
if [ -w "${INSTALL_DIR}" ]; then
  SUDO=""
else
  SUDO="sudo"
  echo "    (${INSTALL_DIR} 쓰기 권한 없음 — sudo 사용)"
fi

curl -sSfL "${DOWNLOAD_URL}" -o /tmp/trivy-download.tar.gz
tar -xzf /tmp/trivy-download.tar.gz -C /tmp trivy
${SUDO} install -m 0755 /tmp/trivy "${INSTALL_DIR}/trivy"
rm -f /tmp/trivy-download.tar.gz /tmp/trivy

echo "==> 설치 완료:"
trivy --version
