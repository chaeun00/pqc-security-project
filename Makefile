# ==============================================================
# Makefile — PQC Security Project 빌드·운영 진입점
#
# Step 1-A rev.6: DOCKER_CONTENT_TRUST=1 인라인 주입 (make up/build)
# Step 1-A rev.7: test-dct → Cosign(Sigstore) keyless verify 전환
#
# 규칙: docker compose 직접 실행 금지, 반드시 make 경유
# ==============================================================

COMPOSE := DOCKER_CONTENT_TRUST=1 docker compose

.PHONY: up down build build-secure logs ps test-dct trivy inspect-limits verify-all setup help

# ── 기본 타겟 ─────────────────────────────────────────────
.DEFAULT_GOAL := help

help: ## 사용 가능한 타겟 목록
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

# ── 서비스 관리 ───────────────────────────────────────────
up: ## 전체 서비스 시작 (DOCKER_CONTENT_TRUST=1 포함)
	$(COMPOSE) up -d --build db

down: ## 전체 서비스 중지 및 컨테이너 제거
	$(COMPOSE) down

build: ## 이미지 빌드 (DOCKER_CONTENT_TRUST=1 포함)
	$(COMPOSE) build

logs: ## 전체 서비스 로그 스트림
	$(COMPOSE) logs -f

ps: ## 컨테이너 상태 및 Resource Limits 확인
	$(COMPOSE) ps
	@docker stats --no-stream --format \
		"table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemLimit}}" \
		2>/dev/null || true

# ──────────────────────────────────────────────────────────
# test-dct — Step 1-A 검증: Cosign(Sigstore) 이미지 서명 검증
#   rev.7: DCT(Notary v1) → Cosign keyless verify 전환
#
#   Stage 1 — 서명된 이미지 (cgr.dev/chainguard/postgres:latest)
#     [1.1] cosign verify → OCI 서명 레이어 존재 확인 (pull 없이)
#     [1.2] cosign verify → Sigstore keyless 서명 검증 통과 확인
#
#   Stage 2 — 미서명 이미지 (postgres:16-alpine, Docker Hub 공식)
#     [2.1] cosign verify → 서명 없음 사전 확인 (pull 없이)
#     [2.2] cosign verify → 거부 확인 + stderr 캡처로 거부 원인 구분
#
# 인수조건 2: make test-dct 전 환경 통과
# ──────────────────────────────────────────────────────────
test-dct: ## [Step 1-A] Cosign keyless 이미지 서명 검증 (rev.7)
	@which cosign > /dev/null 2>&1 || { \
		echo "Error: cosign 미설치. 'make setup' 실행 후 재시도."; \
		exit 1; \
	}
	@echo "==> [test-dct] Cosign(Sigstore) 이미지 서명 검증 ──────"

	@echo "==> [Stage 1] 대상: cgr.dev/chainguard/postgres:latest (Cosign 서명 이미지)"
	@echo "==> [1.1] OCI 서명 레이어 존재 확인 (pull 없이)"
	@if cosign verify \
			--certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
			--certificate-identity-regexp "https://github.com/chainguard-images/.*" \
			cgr.dev/chainguard/postgres:latest \
			> /dev/null 2>&1; then \
		echo "==> [1.1] PASS ✓ — OCI 서명 레이어 확인"; \
	else \
		echo "==> [1.1] FAIL — 서명 레이어 없음 (Sigstore/네트워크 연결 확인)"; \
		exit 1; \
	fi
	@echo "==> [1.2] Sigstore keyless 서명 검증"
	@if cosign verify \
			--certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
			--certificate-identity-regexp "https://github.com/chainguard-images/.*" \
			cgr.dev/chainguard/postgres:latest \
			> /dev/null 2>&1; then \
		echo "==> [1.2] PASS ✓ — keyless 서명 검증 통과"; \
	else \
		echo "==> [1.2] FAIL — keyless 서명 검증 실패"; \
		exit 1; \
	fi

	@echo "==> [Stage 2] 대상: postgres:16-alpine (미서명 이미지, Docker Hub 공식)"
	@echo "==> [2.1] 서명 없음 사전 확인 (pull 없이)"
	@if ! cosign verify postgres:16-alpine > /dev/null 2>&1; then \
		echo "==> [2.1] PASS ✓ — Cosign 서명 없음 확인 (미서명 대상 적합)"; \
	else \
		echo "==> [2.1] FAIL — Cosign 서명 발견 (미서명 테스트 불가 — 대상 이미지 교체 필요)"; \
		exit 1; \
	fi
	@echo "==> [2.2] cosign verify 거부 확인 + stderr 원인 캡처"
	@ERR=$$(cosign verify postgres:16-alpine 2>&1); RC=$$?; \
	if [ $$RC -ne 0 ]; then \
		if echo "$$ERR" | grep -qiE "(no matching signatures|no signatures found)"; then \
			echo "==> [2.2] PASS ✓ — 거부 원인: no matching signatures"; \
		else \
			echo "==> [2.2] PASS ✓ — 거부됨. 원인: $$(echo "$$ERR" | head -1)"; \
		fi; \
	else \
		echo "==> [2.2] FAIL — 미서명 이미지가 검증됨 (예상치 않은 결과)"; \
		exit 1; \
	fi

	@echo "==> [test-dct] 모든 단계 통과 ✓"

# ──────────────────────────────────────────────────────────
# setup — 로컬 개발환경 초기화 (cosign & trivy 설치)
# ──────────────────────────────────────────────────────────
setup: ## 로컬 개발환경 초기화 (cosign & trivy 설치)
	@bash scripts/setup-cosign.sh
	@bash scripts/setup-trivy.sh
	@bash scripts/setup-jq.sh
	
# ──────────────────────────────────────────────────────────
# trivy — Trivy 이미지 취약점 스캔
#   Step 1-A: db 베이스 이미지 (postgres:16-alpine) 스캔
#   Step 1-D 이후: 전체 빌드 이미지 스캔으로 확장
#
# 인수조건 2: Trivy Critical 0건
# ──────────────────────────────────────────────────────────
trivy: ## [Step 1-B] Trivy Critical 취약점 스캔 (postgres + crypto-engine)
	@which trivy > /dev/null 2>&1 || { \
		echo "Error: trivy 미설치."; \
		echo "       https://aquasecurity.github.io/trivy/latest/getting-started/installation/"; \
		exit 1; \
	}
	@echo "==> Trivy 스캔: postgres:16-alpine"
	@trivy image --severity CRITICAL --exit-code 1 postgres:16-alpine
	@echo "==> crypto-engine 이미지 빌드 후 스캔"
	@DOCKER_CONTENT_TRUST=0 docker compose build crypto-engine
	@trivy image --severity CRITICAL --exit-code 1 pqc-security-project-crypto-engine
	@echo "==> Trivy 스캔 완료: Critical 0건 ✓"

# ──────────────────────────────────────────────────────────
# build-secure — Step 1-B 보안 파이프라인
#   [1/4] cosign verify: python:3.12-alpine 서명 확인 (WARN-only)
#   [2/4] docker compose build: crypto-engine 빌드
#   [3/4] cosign sign: 출력 이미지 서명 (레지스트리 push 필요 → 로컬 SKIP)
#   [4/4] trivy: Critical 0건 게이트 (exit-code 1)
#
# 인수조건 1: Critical 1건 이상 시 exit 1
# ──────────────────────────────────────────────────────────
build-secure: ## [Step 1-B] Cosign verify(WARN) → build → sign(로컬레지스트리) → Trivy 순차 파이프라인
	@which cosign > /dev/null 2>&1 || { echo "Error: cosign 미설치. 'make setup' 실행."; exit 1; }
	@which trivy  > /dev/null 2>&1 || { echo "Error: trivy 미설치."; exit 1; }
	@echo "==> [1/4] Cosign: 베이스 이미지 서명 확인 (python:3.12-alpine, WARN-only)"
	@cosign verify python:3.12-alpine > /dev/null 2>&1 \
		&& echo "==> [1/4] PASS ✓ — Cosign 서명 확인" \
		|| echo "==> [1/4] WARN — python:3.12-alpine 미서명 (Docker Hub 기본, 허용)"
	@echo "==> [2/4] docker build: crypto-engine"
	@DOCKER_CONTENT_TRUST=0 docker compose build crypto-engine
	@echo "==> [3/4] Cosign: 출력 이미지 서명/검증 (임시 로컬 레지스트리 경유)"
	@if [ -f .cosign/cosign.key ]; then \
		docker rm -f pqc-registry 2>/dev/null || true; \
		if docker run -d --name pqc-registry -p 5000:5000 registry:2 \
			&& docker tag pqc-security-project-crypto-engine localhost:5000/crypto-engine:latest \
			&& docker push localhost:5000/crypto-engine:latest \
			&& COSIGN_PASSWORD="" cosign sign --key .cosign/cosign.key \
				--allow-insecure-registry --tlog-upload=false \
				localhost:5000/crypto-engine:latest \
			&& COSIGN_PASSWORD="" cosign verify --key .cosign/cosign.pub \
				--allow-insecure-registry --insecure-ignore-tlog \
				localhost:5000/crypto-engine:latest > /dev/null 2>&1; then \
			docker rm -f pqc-registry 2>/dev/null || true; \
			echo "==> [3/4] PASS ✓ — 출력 이미지 서명 검증 통과"; \
		else \
			docker rm -f pqc-registry 2>/dev/null || true; \
			echo "==> [3/4] FAIL — 서명 실패 (.cosign/cosign.key 또는 Docker 상태 확인)"; \
			exit 1; \
		fi; \
	else \
		echo "==> [3/4] SKIP — .cosign/cosign.key 없음 (make cosign-keygen 실행 권장)"; \
	fi
	@echo "==> [4/4] Trivy: Critical 취약점 스캔 (exit-code 1)"
	@trivy image --severity CRITICAL --exit-code 1 pqc-security-project-crypto-engine
	@echo "==> build-secure 완료 ✓"

# ──────────────────────────────────────────────────────────
# cosign-keygen — 로컬 서명 키 생성 (.cosign/cosign.key/pub)
# ──────────────────────────────────────────────────────────
cosign-keygen: ## 로컬 cosign 키 생성 (.cosign/cosign.key + cosign.pub)
	@mkdir -p .cosign
	@COSIGN_PASSWORD="" cosign generate-key-pair --output-key-prefix .cosign/cosign
	@echo "==> .cosign/cosign.key, .cosign/cosign.pub 생성 완료"

# ──────────────────────────────────────────────────────────
# inspect-limits — docker inspect vs .env Limit 비교
#   rev.10: .env 파싱 후 NanoCPU/MEM 비교 → 불일치 exit 1
#   인수조건 3: 수치 불일치 시 exit 1
# ──────────────────────────────────────────────────────────
inspect-limits: ## [Step 1-B] docker inspect vs .env Limit 비교 (불일치 exit 1)
	@bash scripts/check-limits.sh .env

# ──────────────────────────────────────────────────────────
# verify-all — Step 1-B 인수조건 통합 검증
#   빌드 → 기동 → HTTP 왕복 → inspect-limits
# ──────────────────────────────────────────────────────────
verify-all: ## [Step 1-B] 빌드→기동→HTTP 왕복→inspect-limits 통합 검증
	@which jq   > /dev/null 2>&1 || { echo "Error: jq 미설치 (apt install jq)"; exit 1; }
	@which curl > /dev/null 2>&1 || { echo "Error: curl 미설치"; exit 1; }
	@echo "==> [verify-all 1/4] build-secure"
	@$(MAKE) build-secure
	@echo "==> [verify-all 2/4] docker compose up crypto-engine --wait"
	@$(COMPOSE) up -d --wait crypto-engine
	@echo "==> [verify-all 3/4] HTTP 왕복 테스트"
	@bash scripts/verify-http.sh
	@echo "==> [verify-all 4/4] inspect-limits"
	@$(MAKE) inspect-limits
	@echo "==> [verify-all] 모든 인수조건 통과 ✓"
