
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

---
## Review: Step 1-B Final 해결 검증 (2026-03-06)

### 해결 확인 (6/6)
- [HIGH] secret_key 응답 노출 ✅
- [HIGH] dsa_sign 키쌍 재생성 ✅
- [HIGH] algorithm_factory dead code ✅
- [MEDIUM] str(e) 노출 ✅
- [MEDIUM] KEM 1-step 모델 ✅
- [LOW] Dockerfile 디버그 라인 ✅

### 잔존 위험 요소 / 엣지 케이스
1.  kem_encrypt 비호환 공개키 입력 — encap_secret에 잘못된 길이/포맷 키 전달 시 liboqs 내부 오류가 "캡슐화 오류" 로 masking되어 디버깅 어려움. 현재 HTTP 400 반환은 맞으나 로그 레벨 분리 없음.
2.  _DSA_SECRET_KEY 메모리 상주 — 모듈 전역 변수로 평생 유지. 데모 범위에서는 허용이나, 프로세스 덤프 시 노출 위험 존재.
3. get_signature(_DSA_SECRET_KEY) 호출 시 liboqs Signature 객체 재생성 — 매 요청 with get_signature() as sig: context 진입 시 객체를 새로 만든 뒤 secret key를 import함. 기능상 문제는 없으나 sign 인스턴스가 공개키와 쌍을 맞추는지 liboqs 내부 검증 여부 불명확.
4. DSA public_key 재시작 문제: Step 2 이후 인증 세션에서 public_key를 DB에 저장하고 재사용하는 흐름이 생길 때. 그때 키 영속화(파일/HSM/DB 저장)를 설계하면 됨.

### 테스트 공백 (잔존)
1. 빈 message ("") 입력 시 DSA sign 동작 미검증
2. KEM public_key 필드 누락·잘못된 base64 → 422 vs 400 분기 미검증
3. inspect-limits 수치 비교 자동화 — 이전 리뷰부터 이월, diff에 미포함
---
## Review: Step 1-D 통합 헬스체크 (2026-03-06)

### [HIGH] Shebang 오타 — babash → bash (즉시 차단)
### [MEDIUM] test_edge.py 미연결 — 로컬/CI 검증 불일치
### [MEDIUM] check-limits.sh 미연결 — plan 인수조건 3번 미달성
### [LOW] Trivy 옵션 플래그 미구현

### 테스트 공백
1. wait_healthy 타임아웃 후 컨테이너 정리 없음
2. DB CID 빈값 시 오류 메시지 불명확
3. 재검증(--no-build) 플래그 없음
