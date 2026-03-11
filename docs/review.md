
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

---
## [2026-03-08] REVIEWER — 보안 결함 검토

### 변경 요약
현재 diff는 CRLF → LF 줄끝 정규화뿐 (기능 변경 없음).
아래는 커밋된 코드 전체 기준 보안 검토.

### 위험 요소
- [치명적] /kem/keygen 응답에 secret_key 포함 — KEM 기밀성 훼손
- [치명적] /kem/decrypt 요청에 secret_key 수신 — decryption oracle 패턴
- [치명적] /kem/encrypt 응답에 shared_secret 노출
- [고위험] 전 엔드포인트 인증 없음
- [고위험] liboqs git clone 버전 미고정 (공급망 위험)
- [중위험] DSA message 입력 크기 제한 없음

### 결론
내부 데모 서버 기준: 다음 단계 진행 가능.
프로덕션 보안 서비스 기준: KEM API 재설계 필요.

---
## Week 3 Day 1 리뷰 (2026-03-09)
### api-gateway 프로젝트 셋업

**변경 요약:** Dockerfile Maven→Gradle 교체, Spring Boot 앱 초기 셋업, docker-compose healthcheck 추가, CI api-gateway-build 잡 추가

**위험 요소:**
- [HIGH] Spring Security 추가 시 /actuator/health permitAll() 누락 시 healthcheck 실패
- [MEDIUM] gradle wrapper 미사용 — 버전 일관성 로컬 미보장
- [MEDIUM] COPY *.jar 와일드카드 — 향후 멀티jar 빌드 시 실패 가능
- [LOW] curl 설치로 이미지 공격 면적 증가
- [LOW] Actuator 포트 미분리

**테스트 공백:** HealthController 단위 테스트 없음, CI -x test 스킵

**Day 2 진입:** 가능. /actuator/health permitAll() 계획 및 Actuator 포트 분리 결정 필요.

---
## Week 3 Day 1 리뷰 수정판 (2026-03-09)

### [HIGH] Day 4 Spring Security 사전 계획 — permitAll + 관리 포트 분리

**진입 전 필수 조치 (Day 4 작업 시작 전):**
1. `SecurityConfig.java` 작성 시 `/actuator/health` 및 `/api/health` 를 `permitAll()` 로 명시 — 미설정 시 docker-compose HEALTHCHECK 403 → restart loop 재현
2. Actuator 관리 포트 `8081` 분리 (`management.server.port=8081`) — 프로덕션 게이트웨이에서 내부 포트 외부 노출 차단
   - docker-compose api-gateway healthcheck URL도 `8081`로 변경 필요
3. CSRF 비활성화 (`csrf.disable()`) — Stateless REST API 원칙 (세션 없음)

### [관건 5] api-gateway ↔ db ↔ dashboard 기술 스택 정렬 확인

| 서비스 | 스택 | 통신 |
|--------|------|------|
| api-gateway | Spring Boot 3.2 / Java 21 / Gradle 8.8 | REST (pqc-internal) |
| crypto-engine | FastAPI / Python / liboqs | REST (pqc-internal) |
| db | PostgreSQL 17 | JDBC (pqc-internal) |
| dashboard | React (TBD: Vite + TanStack Query + Zustand) | REST (pqc-public → api-gateway:8080) |

**정렬 확인 결과:** 네트워크 분리(pqc-internal / pqc-public) 일치. dashboard↔api-gateway 간 CORS 설정 Day 3 또는 Day 5에 추가 필요.

---
## Week 3 Day 1 리뷰 (2026-03-09)
### api-gateway 프로젝트 셋업

**변경 요약:**
Dockerfile Maven→Gradle 멀티스테이지 교체, Spring Boot 앱 초기 셋업 (HealthController, application.yml),
docker-compose healthcheck 추가, CI api-gateway-build 잡 추가.

**위험 요소:**
- [HIGH] Spring Security 추가 시 /actuator/health permitAll() 누락 → docker-compose healthcheck 즉시 실패
- [MEDIUM] gradle wrapper 미사용 — 로컬 환경 버전 일관성 미보장
- [MEDIUM] COPY *.jar 와일드카드 — 향후 멀티jar 빌드 시 실패 가능
- [LOW] curl 설치로 이미지 공격 면적 증가 (Trivy HIGH 미차단)
- [LOW] Actuator 포트 미분리 (8080 공용) — Day 2 Security 설계에 영향

**테스트 공백:**
- CI -x test 스킵 유지 — 첫 테스트 작성 후 해제 필요
- HealthController 단위 테스트 없음 (testImplementation 의존성은 추가됨)

**해결된 항목:**
- testImplementation 'spring-boot-starter-test' 추가 완료

**Day 2 전제조건:**
- Spring Security Config에서 /actuator/health permitAll() 필수
- Actuator 포트 분리 여부 결정 필요

---
## Review: Week 3 Day 2 — Feign Client 구성 (2026-03-10)

### 변경 요약
- build.gradle: openfeign, circuitbreaker-resilience4j, resilience4j-spring-boot3 의존성 추가
- application.yml: crypto-engine URL, Feign timeout, Resilience4j CB 설정, metrics 노출 추가
- ApiGatewayApplication.java: @EnableFeignClients 추가
- docs/plan.md: CB 인스턴스명 불일치 버그픽스 계획 추가

### 위험 요소 및 엣지 케이스

**[HIGH] HTTP 기본값 — 평문 통신**
`crypto.engine.url: ${CRYPTO_ENGINE_URL:http://crypto-engine:8000}`
PQC 암호화 서비스(DSA sign/verify)가 평문 전송. CRYPTO_ENGINE_URL 미설정 시 서명 데이터가 HTTP로 노출됨.

**[HIGH] /actuator/metrics 무인증 노출**
`include: health,metrics` 변경으로 feign latency, CB 상태, JVM 메모리 외부 노출. 공격자의 내부 구조 파악 및 timing 분석 경로.

**[HIGH] api-gateway → crypto-engine 서비스 간 인증 없음**
mTLS, API Key, Bearer Token 등 서비스 간 인증 메커니즘 부재. crypto-engine URL 직접 접근 가능.

**[MEDIUM] SSRF 잠재 위험**
CRYPTO_ENGINE_URL 환경변수가 오염될 경우 Feign Client가 임의 내부 엔드포인트 호출 가능.

**[MEDIUM] CB 인스턴스명 불일치**
`configs.default` 설정과 @CircuitBreaker 인스턴스명 미매칭 시 CB 미동작 → 스레드 풀 고갈 위험.

**[MEDIUM] SignRequest/VerifyRequest 입력 검증 부재**
요청 페이로드 크기 제한 없음. 이전 DSA 크기 제한 이슈(test_edge.py) 재현 가능.

**[LOW] slowCallDurationThreshold = readTimeout 동일 설정**
slow call과 timeout 중복 집계 가능. 의도한 값인지 재확인 필요.

### 테스트 공백
- CRYPTO_ENGINE_URL 미설정 시 기본값 동작
- /actuator/metrics 무인증 접근 여부
- CB OPEN 상태 Fallback 실제 동작
- 대용량 SignRequest 입력 거부
- HTTP 평문 전송 여부
- CB 인스턴스명 매핑 정확성

### 수정 제안 (텍스트)
1. CRYPTO_ENGINE_URL 기본값 https:// 변경 또는 http:// 시작 시 fail-fast
2. metrics 를 별도 management port로 분리하거나 제거
3. Feign RequestInterceptor로 고정 API Key 주입 계획 추가
4. CRYPTO_ENGINE_URL allowlist 검증으로 SSRF 방어
5. configs.default → instances.crypto-engine 으로 명시적 CB 인스턴스 설정
6. Controller @Size 또는 max-request-size 제한 추가

---
## [Day 3] ML-DSA JWT 발급 보안 리뷰 (2026-03-11)

### 변경 요약 (Summary of change)

Day 3 diff 기준: AuthController + AuthService (ML-DSA JWT 발급), algorithm_factory.py (환경변수 키쌍 주입),
gen-dsa-keypair.sh (키쌍 생성 스크립트), application.yml (OkHttp + Feign logger) 신규 추가.

---

### 위험 요소 및 엣지 케이스 (Risks / Edge cases)

#### HIGH

**1. 평문 비밀번호 소스코드 하드코딩** — AuthService.java:26
  - `private static final String DEMO_PASSWORD = "demo123";`
  - 데모용임을 주석으로 명시했으나, git 히스토리에 영구적으로 평문으로 남는다.
    나중에 "데모용"이 프로덕션으로 미끄러지는 사고의 가장 흔한 원인이다.

**2. Non-constant-time 비밀번호 비교** — AuthService.java:38
  - `if (!DEMO_USER.equals(request.userId()) || !DEMO_PASSWORD.equals(request.password()))`
  - String.equals()는 첫 불일치 바이트에서 즉시 반환된다.
    응답 시간 차이로 userId 존재 여부를 추론하는 타이밍 어택에 노출된다.

#### MEDIUM

**3. DSA 개인키 환경변수 주입 시 포맷 미검증** — algorithm_factory.py:10-11
  - `_DSA_SECRET_KEY: bytes = base64.b64decode(_secret_key_b64)`
  - DSA_SECRET_KEY_B64가 잘못된 Base64이거나 올바른 알고리즘(ML-DSA-65)의 키가 아닐 경우
    디코딩은 성공하지만 이후 서명 시 런타임 오류가 발생한다.
    이 오류는 AuthService에서 HTTP 500으로 뭉개지며 어떤 키를 사용했는지 추적이 불가능해진다.

**4. 개인키가 stdout으로 출력됨** — gen-dsa-keypair.sh:29-30
  - `print('DSA_SECRET_KEY_B64=' + base64.b64encode(sec).decode())`
  - 스크립트 출력이 터미널 히스토리, CI/CD 파이프라인 로그, 쉘 스크립트 리디렉션 실수 등으로
    개인키가 평문으로 잔류할 수 있다. 생성된 파일에 대한 chmod 600 안내가 없다.

**5. 로그인 엔드포인트 Rate Limit 없음** — AuthController.java:23
  - Day 3 범위 내에서 /api/auth/login에 대한 요청 제한이 없다.
    demo123 같은 취약 비밀번호와 조합되면 브루트포스가 사실상 무제한이다.

#### LOW

**6. 예외 메시지 로깅** — AuthService.java:62
  - `log.error("JWT 생성 실패: {}", e.getMessage());`
  - e.getMessage()가 liboqs 내부 오류 문자열이나 키 관련 상세 정보를 포함할 경우
    로그를 통한 내부 상태 노출이 된다.

**7. Feign logger level BASIC** — application.yml:31
  - 현재 BASIC은 URL + 상태코드만 기록하므로 직접적 문제는 없다.
    그러나 signingInput(JWT header+payload)이 Feign 요청 바디에 포함되어 있어,
    향후 logger level을 FULL로 바꾸면 서명 대상 페이로드가 통째로 로그에 찍힌다.

---

### 테스트 공백 (Test gaps)

| 구분 | 누락된 케이스 |
|---|---|
| AuthController | userId / password 빈 값(@NotBlank) 검증 — 400 응답 여부 |
| AuthService | 잘못된 자격증명 → 정확히 401 반환 확인 |
| AuthService | crypto-engine sign() 호출 실패 시 → 500 처리 경로 |
| algorithm_factory | DSA_SECRET_KEY_B64만 주입하고 DSA_PUBLIC_KEY_B64 누락 시 동작 (if 조건 양쪽 다 있어야 분기 진입) |
| algorithm_factory | 잘못된 Base64 값 주입 시 모듈 로드 실패 여부 |
| gen-dsa-keypair.sh | crypto-engine 이미지가 존재하지 않을 때 오류 메시지 출력 확인 |

---

### 수정 제안 (Suggested fixes — 텍스트만)

1. 하드코딩 자격증명: DEMO_USER / DEMO_PASSWORD를 환경변수로 분리하고,
   소스코드에는 기본값 없이 기동 시 명시적 예외로 fail-fast 처리한다.

2. 타이밍 어택: MessageDigest.isEqual(a.getBytes(), b.getBytes()) 방식의
   constant-time 비교로 교체한다.

3. 개인키 환경변수 검증: algorithm_factory.py에서 키 로드 직후
   len(_DSA_SECRET_KEY)가 ML-DSA-65 명세상 기대 크기인지 어설션으로 검증하고,
   불일치 시 기동 중단하는 가드를 추가한다.

4. gen-dsa-keypair.sh 출력: 키 생성 결과를 stdout 대신 chmod 600이 적용된 파일로
   직접 쓰도록 변경하고, 스크립트 헤더에 "출력 파일을 git에 커밋하지 말 것" 경고를 추가한다.

5. Rate Limit: Spring Cloud Gateway의 RequestRateLimiter 필터를 /api/auth/login 경로에 적용하거나,
   최소한 IP 기준 일정 시간 내 요청 횟수 제한을 설정한다.
