#!/usr/bin/env bash
# ==============================================================
# scripts/setup-jq.sh — jq 로컬 설치 스크립트 (Step 1-B 의존성)
#
# 사용법: bash scripts/setup-jq.sh
#         또는 Makefile의 setup 타겟에 추가
# ==============================================================
set -euo pipefail

# 최신 안정 버전 (2024년 기준 1.7.1)
JQ_VERSION="1.7.1"
INSTALL_DIR="${JQ_INSTALL_DIR:-/usr/local/bin}"

# 1. OS / 아키텍처 감지
OS_RAW=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH_RAW=$(uname -m)

# OS 명칭 매핑 (jq 릴리즈 파일명 기준)
case "${OS_RAW}" in
  linux)  OS="linux" ;;
  darwin) OS="macos" ;; # jq는 macos라는 명칭 사용
  *)
    echo "Error: 지원하지 않는 운영체제: ${OS_RAW}" >&2
    exit 1
    ;;
esac

# 아키텍처 명칭 매핑
case "${ARCH_RAW}" in
  x86_64)          ARCH="amd64" ;;
  aarch64 | arm64) ARCH="arm64" ;;
  *)
    echo "Error: 지원하지 않는 아키텍처: ${ARCH_RAW}" >&2
    exit 1
    ;;
esac

# 2. 다운로드 정보 설정
# 예: https://github.com/jqlang/jq/releases/download/jq-1.7.1/jq-linux-amd64
BINARY="jq-${OS}-${ARCH}"
DOWNLOAD_URL="https://github.com/jqlang/jq/releases/download/jq-${JQ_VERSION}/${BINARY}"

echo "==> jq ${JQ_VERSION} 설치 중... (${OS}/${ARCH})"
echo "    URL: ${DOWNLOAD_URL}"
echo "    설치 경로: ${INSTALL_DIR}/jq"

# 3. sudo 필요 여부 판단
if [ -w "${INSTALL_DIR}" ]; then
  SUDO=""
else
  SUDO="sudo"
  echo "    (${INSTALL_DIR} 쓰기 권한 없음 — sudo 사용)"
fi

# 4. 설치 진행
curl -sSfL "${DOWNLOAD_URL}" -o /tmp/jq-download
${SUDO} install -m 0755 /tmp/jq-download "${INSTALL_DIR}/jq"
rm -f /tmp/jq-download

echo "==> 설치 완료:"
jq --version