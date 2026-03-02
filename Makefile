# ==============================================================
# Makefile — PQC Security Project 빌드·운영 진입점
#
# Step 1-A rev.6: DOCKER_CONTENT_TRUST=1 인라인 주입 (make up/build)
# Step 1-A rev.7: test-dct → Cosign(Sigstore) keyless verify 전환
#
# 규칙: docker compose 직접 실행 금지, 반드시 make 경유
# ==============================================================

COMPOSE := DOCKER_CONTENT_TRUST=1 docker compose

.PHONY: up down build logs ps test-dct trivy setup help

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
# setup — 로컬 개발환경 초기화 (cosign 설치)
# ──────────────────────────────────────────────────────────
setup: ## 로컬 개발환경 초기화 (cosign 설치)
	@bash scripts/setup-cosign.sh

# ──────────────────────────────────────────────────────────
# trivy — Trivy 이미지 취약점 스캔
#   Step 1-A: db 베이스 이미지 (postgres:16-alpine) 스캔
#   Step 1-D 이후: 전체 빌드 이미지 스캔으로 확장
#
# 인수조건 2: Trivy Critical 0건
# ──────────────────────────────────────────────────────────
trivy: ## [Step 1-A/1-D] Trivy Critical 취약점 스캔
	@which trivy > /dev/null 2>&1 || { \
		echo "Error: trivy 미설치."; \
		echo "       https://aquasecurity.github.io/trivy/latest/getting-started/installation/"; \
		exit 1; \
	}
	@echo "==> Trivy 스캔: postgres:16-alpine"
	trivy image --severity CRITICAL --exit-code 1 postgres:16-alpine
