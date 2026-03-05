
---
## Review: Step 1-B (2026-03-04)

### 핵심 차단 위험 (즉시 수정 필요)
1. `python:3.12-slim`에 curl 없음 → healthcheck 항상 실패 → 인수조건 1번 차단
2. liboqs.so COPY 경로 미검증 → 런타임 ImportError 위험 → 인수조건 2번 차단
3. oqs PyPI 번들 liboqs vs cmake 빌드 liboqs 충돌 가능성

### USER nonroot 전략
- Dockerfile USER nonroot 적용 ✓
- /app COPY --chown 누락 → gunicorn 쓰기 실패 가능 ⚠️

### Task A / Task B
- Task A: CRYPTO_ENGINE_CPU_LIMIT 환경변수 fallback 없어 전구간 up -d 실패 가능
- Task B: Resource Limit 검증 명령어 부재 (docker inspect 기반 테스트 필요)

---
## Review: Step 1-B rev.9 (2026-03-04) — 인수조건 통과 여부

| AC | 판정 |
|----|------|
| 1. make build-secure | ⚠️ 조건부 (liboqs.so COPY 경로 미검증) |
| 2. compose healthy + HTTP 200 | ⚠️ 개선 (wget/chown/fallback 수정됨, liboqs 경로 잔존) |
| 3. make inspect-limits | ❌ 비교 로직 없음 — 출력만 수행 |

### 즉시 해결 필요
- inspect-limits: .env 값 파싱 후 수치 비교 + exit 1 로직 추가
- Dockerfile: liboqs.so find 검증 RUN 삽입

---
## Review: Step 1-B Final (2026-03-05)

### [HIGH] secret_key API 응답 노출 — dsa.py:30, kem.py:27
### [HIGH] dsa_sign 매 요청 키쌍 재생성 — dsa.py:21
### [HIGH] algorithm_factory get_kem/get_signature 미사용 (dead code + 암호 민첩성 미작동)
### [MEDIUM] Exception str(e) 직접 노출 — kem.py:45, dsa.py:46
### [MEDIUM] KEM encrypt = 키생성+캡슐화 1-step → KEM 보안 모델 훼손
### [LOW] Dockerfile RUN ls 디버그 라인 잔존

### 테스트 공백
- KEM/DSA 자동화 테스트 없음
- 잘못된 입력(빈 message, 비호환 키쌍, 지원 외 알고리즘) 엣지 케이스 미검증
- inspect-limits 수치 비교 로직 미해결(이전 리뷰 잔존)