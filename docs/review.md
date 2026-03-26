
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

---

## Day 4 통합 + 보안 위험 추가 수정 리뷰 (2026-03-12)

### 변경 요약
- AuthService: 하드코딩 자격증명 → env var 분리, constant-time 비교, jti 추가
- JwtKeyCache (신규): jti 기준 공개키 인메모리 캐시
- JwtAuthInterceptor (신규): 3단계 JWT 검증 (exp → verifiedCache → crypto-engine)
- RateLimitInterceptor (신규): IP 슬라이딩 윈도우 + 60초 cleanup scheduler
- WebMvcConfig (신규): 인터셉터 등록, /api/auth/**, /api/health 제외

### 위험 요소 및 엣지 케이스
- [HIGH] JwtAuthInterceptor L112: verifiedCache.removeIf() — 매 성공 요청마다 O(n) full-scan
- [MEDIUM] RateLimitInterceptor: getRemoteAddr() — 리버스 프록시 환경에서 Rate Limit 무력화
- [MEDIUM] JwtKeyCache: 미사용 토큰 엔트리 exp까지 잔류
- [LOW] JWT 오류 메시지 세분화로 token state enumeration 가능
- [LOW] DEMO_USER에 기본값 잔존 (DEMO_PASSWORD는 fail-fast, 비대칭)

### 테스트 공백
- JwtAuthInterceptor 단위 테스트 전무 (인수조건 1 자동화 미충족)
- RateLimitInterceptor preHandle 429 반환 테스트 없음 (인수조건 3 자동화 미충족)

### 수정 제안
1. verifiedCache cleanup → ScheduledExecutorService 60초 주기로 분리
2. JWT 오류 메시지 "Unauthorized" 단일화
3. DEMO_USER 기본값 제거 (fail-fast 일관성)
4. RateLimitInterceptorTest: preHandle 429 케이스 추가
5. JwtAuthInterceptor: MockMvc 기반 최소 통합 테스트 2건 추가

### Day 5 진행 가능 여부
조건부 가능 — JwtAuthInterceptor 최소 테스트 + preHandle 429 테스트 추가 후 진행 권장

---
## Day 5 리뷰 (2026-03-13)

### 변경 요약
- `.github/workflows/ci.yml` +63라인: `stack-integration-test` 잡 신설
- `JwtAuthInterceptorTest.java` 2케이스 추가 (헤더 없음 → 401, 캐시 히트 → 200)

### 위험 요소 및 엣지 케이스

**[높음] stack-integration-test — E2E Bearer 플로우 미완성**
- `[AC1]` 스텝에서 `TOKEN`이 `$GITHUB_ENV`로 export되지 않음 (ci.yml:589-600)
- docker compose 전체 스택 기준 Bearer→/dsa/sign 200, 미인증→401 스텝 없음
- Day 5 진입 기준 "로그인→토큰→보호 엔드포인트 E2E 플로우 pass" 미충족

**[중간] docker compose ps -q 공백 오진**
- ci.yml:583: `$(docker compose ps -q api-gateway)` 빈 문자열 시 `docker inspect` 가 모든 컨테이너 조회 또는 오류 없이 통과 가능

**[낮음] JwtAuthInterceptorTest 내부 상태 직접 접근**
- `interceptor.verifiedCache.put(...)` 패키지 접근 — `verifiedCache` 가시성 변경 시 테스트 깨짐

### 테스트 공백
| 누락 케이스 | 중요도 |
|---|---|
| 만료 토큰 (exp 과거값) → 401 | 높음 |
| 잘못된 JWT 구조 (점 1개/3개 이상) → 401 | 중간 |
| stack-integration-test Bearer→/dsa/sign 200 E2E | 높음 |
| stack-integration-test 미인증→/dsa/sign 401 E2E | 중간 |

### 수정 제안
1. stack-integration-test `[AC1]` 말미 `echo "TOKEN=$TOKEN" >> $GITHUB_ENV` 추가 후 Bearer 200 / 미인증 401 스텝 신설
2. `docker compose ps -q` 결과를 변수로 받아 `-z` 검사 후 `docker inspect` 호출
3. JwtAuthInterceptorTest에 `exp = now - 1` 만료 케이스 추가

### Day 6 진입 판단
CI green 조건 충족. E2E Bearer 플로우는 auth-integration-test(java -jar)에서 커버 중이므로 중복 허용 시 Day 6 진입 가능.
[높음] 항목 2개는 Day 6 백로그 등록 권장.

---

## [2026-03-16] Day 6 KEM API 재설계 리뷰

### 변경 요약
Day 6 인수조건 코드 구현 완료. AC1(key_id 반환), AC2(ciphertext만 반환), AC3(keygen 410) 충족.

### 위험 요소 및 엣지 케이스
- [심각-보안] /kem/decrypt 취약점 잔존: 클라이언트 제공 secret_key로 shared_secret 반환 가능
- [심각-CI] KEM_WRAP_KEY 환경변수 미주입: integration-test 잡 / docker-compose 모두 누락 → KeyError 500
- [심각-CI] 003_kem_keys.sql CI 미적용: db-schema-test가 001/002만 실행, kem_keys 테이블 없음
- [중간-CI] http_test.py / test_edge.py 구버전 인터페이스 그대로 → CI 실패

### 테스트 공백
- /kem/init → key_id 반환 happy path 없음
- /kem/encrypt key_id 기반 플로우 없음 (404/503 엣지 포함)
- /kem/keygen 410 자동화 검증 없음
- wrap_secret/unwrap_secret 단위 테스트 없음

### 수정 제안
1. /kem/decrypt를 410으로 임시 차단
2. CI KEM_WRAP_KEY 랜덤 키 주입 추가
3. CI db-schema-test에 003_kem_keys.sql 추가
4. http_test.py + test_edge.py Day 6 플로우로 교체

### 결론
CI 작업(KEM_WRAP_KEY 주입, 003_kem_keys.sql 적용, 테스트 스크립트 업데이트) 완료 및 green 확인 후 Day 7 진입 권장.

---
## [2026-03-17] Day 7 Review — decryption oracle 수정 + /api/encrypt 전 구간

**변경 요약:** /kem/encrypt 하이브리드(KEM+HKDF+AES-256-GCM) 확장, /kem/decrypt 서버사이드 구현, EncryptController(/api/encrypt) 추가, CI Day7 E2E 검증 추가. AC1/AC2/AC3 모두 달성.

**위험 요소 및 엣지 케이스:**
- [HIGH] oqs.KeyEncapsulation(KEM_ALGORITHM, secret_key_bytes) — positional 인자 방식, requirements에 버전 미고정. liboqs-python 버전 변경 시 런타임 오류 가능.
- [MEDIUM] db_cursor() 블록 내부 HTTPException raise — 불필요한 rollback 경로 혼재 (기능 오류 없음).
- [MEDIUM] AES 복호화 실패 시 400 "AES 복호화 오류" — KEM/AES 실패를 구분 노출하여 oracle 공격 면 존재.
- [LOW] CBOM risk_level='NONE' 하드코딩, CI ENCRYPT_RESP 환경변수 JSON 줄바꿈 취약.

**테스트 공백:**
- EncryptControllerTest.java 미존재
- crypto-engine pytest 디렉토리 없음 (_derive_aes_key, _cbom_insert 미검증)
- 잘못된 aes_iv 길이, key_id 404, unwrap 실패 경로 CI 미검증

**수정 제안:**
1. liboqs-python 버전 고정 + 키워드 인자 방식으로 변경
2. AES/KEM 오류 메시지 단일화
3. EncryptControllerTest 유닛 테스트 추가

**Day 8 진행:** 가능. oqs 버전 고정을 Day 8 시작 전 처리 권장.

---
## [2026-03-17] Day 7 후속 구현 검증 — 이슈 해결 확인

**해결된 이슈:** oqs 버전 고정(0.14.1), secret_key 키워드 인자, kem_decrypt 3단계 분리, AES oracle 에러 단일화, EncryptControllerTest, test_kem_utils.py 추가.

**신규 HIGH:** liboqs-python==0.14.1 vs Dockerfile liboqs C 0.15.0 버전 불일치 — PyPI 배포 여부 확인 및 버전 동기화 필요.

**잔존 MEDIUM:** kem_encrypt는 db_cursor 내부 HTTPException 패턴 미분리 (kem_decrypt와 불일치). 기능 오류 없음.

**잔존 LOW:** CI ENCRYPT_RESP 환경변수 JSON 방식 미변경.

**Day 8 진행:** liboqs-python 버전 동기화 확인 후 진행 권장.

---
## [2026-03-18] Day 8 — Algorithm Agility 인터페이스 리뷰

### 변경 요약 (Summary of Change)

| 파일 | 변경 내용 |
|---|---|
| `crypto-engine/app/algorithm_strategy.py` | **신규** — KEM/DSA 화이트리스트 + `validate_algorithm()` + startup fail-fast |
| `crypto-engine/app/algorithm_factory.py` | `os.getenv` 직접 읽기 → `algorithm_strategy` re-export로 교체, 키 길이 하드코딩 → `DSA_META` 참조 |
| `crypto-engine/app/main.py` | `lifespan` 추가 → startup 시 `cbom_assets` INSERT (`_cbom_startup_insert`) |
| `crypto-engine/tests/test_algorithm_strategy.py` | **신규** — `validate_algorithm` 단위 테스트 7개 (정상/비정상/교차오염/메타데이터) |
| `.github/workflows/ci.yml` | `unit-test` 잡 추가 (pytest + INVALID-ALG inline 검증) |
| `api-gateway/src/main/resources/application.yml` | `crypto.algorithm.kem-id / dsa-id` 키 추가 |
| `.env.example`, `docker-compose.yml` | 환경변수 템플릿/override 추가 |

### 인수조건 달성 여부

| AC | 조건 | 상태 |
|---|---|---|
| AC1 | 환경변수만 변경 + docker compose up → API 정상 동작 | **미달성** — CI에 ML-KEM-512 / ML-KEM-1024 전환 후 `/api/encrypt` 호출 통합 테스트 없음 |
| AC2 | 서버 기동 시 `cbom_assets` INSERT 확인 | **부분 달성** — 코드는 존재하나 CI에 DB 연동 검증 없음 |
| AC3 | 화이트리스트 외 ID → startup 오류 | **달성** — pytest + CI inline 모두 검증 |

### 위험 요소 및 엣지 케이스 (Risks/Edge Cases)

**[HIGH] api-gateway Java binding 미구현**
- `application.yml`에 `crypto.algorithm.kem-id / dsa-id` 키만 추가됐고, 이를 읽는 Java `@ConfigurationProperties` 클래스가 없음
- api-gateway가 실제로 알고리즘 식별자를 사용하지 않으면 "두 계층 모두 필요" (plan 2074줄) 요건 미충족

**[MEDIUM] CBOM INSERT 오류 완전 무시**
- main.py `except Exception` → `logger.warning` 처리로 DB 장애 시 AC2가 자동 실패 없이 통과
- 운영 환경에서 CBOM 기록이 누락돼도 감지 불가

**[MEDIUM] 모듈 임포트 순서 의존성**
- `algorithm_strategy.py`가 모듈 로드 시점에 `sys.exit(1)` 호출 → `algorithm_factory.py`가 이를 import하므로 factory를 import하는 모든 코드가 startup fail-fast에 묶임
- 향후 factory import 포함 테스트 작성 시 테스트 프로세스 자체가 종료되는 함정 존재

**[LOW] 환경변수 대소문자 / 공백 처리 없음**
- `" ML-KEM-768"` (앞 공백), `"ml-kem-768"` 모두 화이트리스트 불일치로 exit(1) 유발

### 테스트 공백 (Test Gaps)

1. **AC1 통합 테스트 누락** — ML-KEM-512 또는 ML-KEM-1024 환경변수로 컨테이너를 기동하고 `/api/encrypt → /api/decrypt` 왕복 호출하는 CI 단계 없음 (plan Step 3 첫 번째 항목)
2. **CBOM INSERT 검증 없음** — DB 포함 환경에서 startup 후 `cbom_assets` 레코드 존재 여부를 확인하는 테스트 없음
3. **api-gateway 알고리즘 전달 경로 테스트 없음** — api-gateway가 `KEM_ALGORITHM_ID`를 crypto-engine 요청에 어떻게 전달하는지 검증 없음
4. **환경변수 오염 케이스** — 공백 포함, 빈 문자열 `""` 상태에 대한 테스트 없음

### 수정 제안 (Suggested Fixes)

1. **api-gateway Java binding 구현**: `crypto.algorithm` 속성을 읽는 `@ConfigurationProperties` 클래스(`CryptoAlgorithmProperties`) 추가 후, crypto-engine 호출 헤더 또는 요청 파라미터에 알고리즘 ID 포함
2. **CBOM INSERT 실패 가시성**: 경고 로그에 `exc_info=True` 추가, 또는 startup health-check 엔드포인트에서 CBOM 기록 여부 반환 방안 검토
3. **CI에 알고리즘 전환 통합 테스트 추가**: 기존 `e2e-encrypt` 잡을 `KEM_ALGORITHM_ID=ML-KEM-512`로 오버라이드해 재실행하는 matrix 전략 또는 별도 잡 추가
4. **환경변수 정규화**: `validate_algorithm` 진입 전 `.strip().upper()` 처리 추가

### Day 9 전환 판단

**조건부 진행 가능** — AC3 완전 달성, 핵심 구조(strategy 분리, fail-fast, CBOM INSERT 경로) 구현됨.
단, 아래 두 항목 미해결 시 누적 기술 부채:

- **블로커급**: api-gateway Java binding 미구현 → "두 계층 알고리즘 정책 제어" 요건 미달
- **권고**: AC1 통합 테스트(ML-KEM-512/1024 전환 CI) 추가 후 커밋

---
## [2026-03-18] Day 8 수정 계획 검증 리뷰

### 이전 리뷰 항목별 해소 현황

| # | 이전 지적 | 해소 여부 | 근거 |
|---|---|---|---|
| HIGH | api-gateway Java binding 미구현 | **해소** | `CryptoAlgorithmProperties.java` + `AlgorithmFeignInterceptor.java` 신규, `ApiGatewayApplication.java` 등록 |
| MEDIUM | CBOM INSERT 오류 완전 무시 | **해소** | `exc_info=True` 추가, `/health → cbom_inserted` 플래그, CI 어설션 추가 |
| MEDIUM | 모듈 임포트 sys.exit 함정 | **해소** | `conftest.py` autouse fixture `valid_algorithm_env` 추가 |
| LOW | 환경변수 공백·소문자 오염 | **해소** | `validate_algorithm()` 내 `.strip().upper()` 적용, 공백/소문자/빈 문자열 테스트 추가 |
| AC1 미달 | ML-KEM-512 통합 테스트 없음 | **해소** | `algorithm-agility-test` 잡 — ML-KEM-512 기동 + 왕복 복호화 검증 |
| AC2 부분 | CBOM DB 연동 검증 없음 | **해소** | `GET /health → cbom_inserted: true` CI 어설션 |

### 잔존 위험 요소 (Risks)

**[MEDIUM] crypto-engine이 알고리즘 헤더를 수신하지 않음**
- `AlgorithmFeignInterceptor`가 모든 Feign 요청에 `X-Kem-Algorithm-Id / X-Dsa-Algorithm-Id` 헤더를 주입하지만, crypto-engine 라우터(`kem.py`, `dsa.py`)에서 이 헤더를 읽어 알고리즘을 오버라이드하는 로직이 없음
- 현재는 "전송만 하고 무시"되는 구조 → "두 계층 정책 제어" 요건이 형식적으로만 충족됨

**[LOW] `/health` 외부 노출**
- plan 질문 답변에서 "외부 노출 제한 권장"이라고 명시했으나 인증 없이 접근 가능한 채로 구현됨
- 현재 `cbom_inserted` 정도는 저위험이나 향후 health 필드 확장 시 재검토 필요

**[LOW] `AlgorithmFeignInterceptor` 글로벌 등록**
- `@Component` + `RequestInterceptor`는 모든 Feign 클라이언트에 전역 적용됨
- crypto-engine 이외 Feign 클라이언트가 추가될 경우 불필요한 헤더가 붙음

### 잔존 테스트 공백 (Test Gaps)

1. **crypto-engine의 알고리즘 헤더 수신 테스트 없음** — api-gateway가 헤더를 전송하지만 crypto-engine이 실제로 다른 알고리즘으로 동작하는지 검증 불가 (현재 구조상 수신 로직 자체가 없음)
2. **`algorithm-agility-test` 캐시 의존** — `--no-build --wait` 사용으로 GHA 캐시 미스 시 컨테이너 기동 실패 가능성 존재

### Day 9 전환 판단

**진행 가능** — 이전 리뷰의 블로커(Java binding), AC1, AC2 모두 해소됨.

**[인지 사항]** `AlgorithmFeignInterceptor`는 헤더를 전송하는 인프라만 구축됐고, crypto-engine에서 해당 헤더를 읽는 수신 로직은 없음. 이 구조는 Day 9 이후 hot-swap 기능 구현 시 반드시 마주치게 됨. Day 9 plan에 "crypto-engine `X-Kem-Algorithm-Id` 헤더 수신 및 per-request 알고리즘 오버라이드" 항목 포함 권장.

---

## Day 9 리뷰 (2026-03-19) — SNDL High-Risk 우선순위 라우팅

### 변경 요약
- RiskLevel enum + RiskClassifier 신설 (HIGH/MEDIUM/LOW → ML-KEM-1024/768/512)
- CryptoEngineClient 시그니처 변경 (kemInit·kemEncrypt per-request 헤더)
- EncryptRequest/Response risk_level 필드 추가
- AlgorithmFeignInterceptor: per-request 헤더 우선 처리
- crypto-engine kem.py: X-Risk-Level 헤더 수신 → cbom_assets.risk_level 기록
- CI AC1/AC2/AC3 검증 step 추가
- 인수조건 3건 모두 달성 ✅

### 위험 요소
- R1: AlgorithmFeignInterceptor 헤더 우선순위 라이브러리 버전 의존 (공식 명세 미보장)
- R2: risk_level 소문자 입력 toUpperCase() 허용 — 스펙 미명시
- R3: CBOM MEDIUM·LOW risk_level DB 기록 CI 미검증 (HIGH만 검증)
- R4: 잘못된 risk_level 값 silent MEDIUM 폴백 — 클라이언트 혼란 가능

### 테스트 공백
- T1: RiskClassifier 전용 단위 테스트 없음 (null/blank/invalid/소문자 케이스)
- T2: EncryptControllerTest HIGH·LOW 경로 단위 테스트 없음
- T3: CI AC2·AC3에 CBOM DB 검증 step 없음
- T4: AlgorithmFeignInterceptor 헤더 우선순위 검증 테스트 없음

---
## Day 10 리뷰 (2026-03-20)

### 인수조건 완료 여부
- AC1 (CBOM DB assertion): ✅ 구현 완료
- AC2 (phase2-complete gate): ✅ 구현 완료
- AC3 (ADR + Phase3 범위 문서): ✅ 구현 완료

### 위험 요소 및 엣지 케이스
- 🔴 **문서 불일치**: plan.md L2360 "질문에 대한 답 1번"이 Hot-swap을 "Phase 2 포함"으로 기술하나, phase3-scope.md는 "Phase 3 이관"으로 확정 — 두 문서가 정반대 방향을 기술 중
- 🟡 **AC1 assertion 취약**: `COUNT > 0` 체크는 행 존재만 확인. ML-KEM-768→ML-KEM-512 전환 순서(타임스탬프/id 기반)를 검증하지 않아 초기 기동 시나리오와 구분 불가
- 🟡 **phase2-complete gate**: steps에 echo만 존재, needs 목록 누락 시 해당 잡 비보호 상태

### 테스트 공백
- ML-KEM-512 전환 순서 미검증 (ORDER BY id DESC LIMIT 1 쿼리 미사용)
- ML-KEM-768 선행 이력 존재 여부 assertion 없음

### 수정 제안
- plan.md L2360 Hot-swap 방향을 "Phase 3 이관 확정"으로 수정하여 phase3-scope.md와 일치
- AC1 쿼리를 최신 레코드의 algorithm_id 직접 검증으로 강화
---
## Day 10 수정 계획 리뷰 (2026-03-20)

### 인수조건 완료 여부
- AlgorithmHotSwapService (AtomicReference): ✅ 구현 완료 (untracked)
- AlgorithmAdminEndpoint (actuator/algorithm): ✅ 구현 완료 (untracked)
- AlgorithmHotSwapServiceTest (5개): ✅ 구현 완료 (untracked)
- CI Day10-R1 Hot-swap 3-step: ✅ 구현 완료 (staged)
- CI Day10-AC1 CBOM assertion: ✅ 구현 완료 (staged)
- phase2-complete gate job: ✅ 구현 완료 (staged)
- ADR + Phase3 범위 문서: ✅ 구현 완료 (staged)

### 위험 요소 및 엣지 케이스
- 🔴 phase3-scope.md Hot-swap 이관 섹션이 현실(Phase 2 구현 완료)과 불일치
- 🟡 AlgorithmAdminEndpoint 무인증 — 네트워크 격리에만 의존 (현재 설계 범위 내 허용)
- 🟡 CI $RESP shell injection 패턴 — 통제 환경이므로 현실적 위협 낮음
- 🟡 untracked 3개 파일(AlgorithmHotSwapService, AlgorithmAdminEndpoint, AlgorithmHotSwapServiceTest) 미스테이징 — 커밋 전 필수

### 테스트 공백
- AlgorithmAdminEndpoint 단위 테스트 없음
- DSA hot-swap CI 검증 없음
- AtomicReference 동시성 테스트 없음

### 수정 제안
- 커밋 전 untracked 3개 파일 git add 필수
- phase3-scope.md Hot-swap 섹션을 "Phase 2 구현 완료"로 수정

### 보안 취약점: 없음 ✅
### Phase 3 진입: 가능 (untracked 스테이징 + phase3-scope.md 수정 후 커밋 권고)

---
## Day 11 리뷰 — 2026-03-23

### 결론: Day 12 진행 가능

**위험요소:**
- [저] tailwind.config.js vs 계획의 .ts — 동작 무관, 파일명 불일치
- [저] VITE_API_BASE_URL .env.example 없음 → CI/신규 환경 혼란 가능


**테스트 공백:**
- useAppStore setToken/clearToken 단위 테스트 없음
- axiosClient JWT 주입 인터셉터 (token 있을 때/없을 때) 테스트 없음
- DashboardPage useMutation 오류/성공 렌더링 테스트 없음

---

## Day 12 — 인증 & 공통 레이아웃 리뷰 (2026-03-24)

### 위험 요소 및 엣지 케이스
- `window.location.href = '/login'` (axiosClient.ts:21): 테스트 시 `Not implemented: navigation to another Document` 경고 발생. 실제 브라우저에서 React Router 히스토리 스택 초기화로 뒤로가기 UX 손상 가능
- token 새로고침 소실: useAppStore token은 in-memory 저장이므로 페이지 새로고침 시 null 초기화 → 로그인 화면으로 튕김. Refresh Token HttpOnly 쿠키 구조 미구현
- LoginPage 에러 메시지 (LoginPage.tsx:57): `(mutation.error as Error).message`는 axios 내부 메시지를 노출, 서버 응답 body가 표시되지 않음

### 테스트 공백
- AppLayout 로그아웃 테스트 없음 — handleLogout() 시 clearToken + navigate('/login') 동작 미검증
- LoginPage 실패 케이스 테스트 없음 — 잘못된 자격증명 입력 시 에러 메시지 렌더링 미검증
- token 새로고침 소실 시나리오 테스트 없음

### 수정 제안
- axiosClient 401 핸들러: `window.location.href` → 커스텀 이벤트(`window.dispatchEvent`) 또는 navigate 주입 방식으로 교체 검토
- useAppStore: `zustand/middleware persist`로 localStorage 유지 추가 검토
- LoginPage 에러 표시: `error.response?.data?.message ?? error.message` 형태로 분기 권장

---

## Day 13 리뷰 — CBOM 목록 시각화 (2026-03-25)

### 보안 취약점 및 위험 요소

| 심각도 | 항목 | 위치 |
|--------|------|------|
| 중간 | `registered_at` vs `created_at` 타입 불일치 잔존 — 백엔드 연동 시 silent mismatch | `dashboard/src/api/cbom.ts` |
| 낮음 | `RISK_BADGE[entry.risk_level] ?? ''` — UNKNOWN 값 스타일 없이 렌더링 | `dashboard/src/features/cbom/CbomPage.tsx:12` |
| 낮음 | `registered_at.slice(0, 10)` — 잘못된 날짜 형식 방어 로직 없음 | `dashboard/src/features/cbom/CbomPage.tsx` |
| 낮음 | MSW browser.ts 프로덕션 포함 여부 미확인 — `import.meta.env.DEV` 조건 검토 필요 | `dashboard/src/main.tsx` |

### 테스트 공백

1. **CbomPage 컴포넌트 렌더링 테스트 없음** — 필터 select 변경 → 목록 필터링, 페이지네이션 버튼 클릭 → 페이지 이동 UI 레벨 미검증
2. **빈 배열 반환 시 처리 테스트 없음** — `data = []`일 때 테이블 정상 렌더링 미검증
3. **isLoading / isError 분기 테스트 없음** — 로딩·에러 UI 렌더링 미검증
4. **RISK_BADGE 미정의 값 테스트 없음** — `risk_level: 'UNKNOWN'` 시 렌더링 결과 미검증
5. **Authorization 헤더 검증 없음** — MSW 핸들러에서 axiosClient가 토큰 헤더를 실제로 포함하는지 미검증

### 수정 제안

- **RISK_BADGE fallback**: `??` 대신 `UNKNOWN` 키 명시 또는 `bg-gray-100 text-gray-600` 기본 스타일 지정
- **MSW 조건 분기**: `main.tsx`에서 `import.meta.env.DEV` 조건 아래 MSW 기동 여부 확인
- **registered_at 타입 명세화**: ISO 8601 문자열로 JSDoc 명시 → 백엔드 연동 시 불일치 조기 감지


---

## Day 14 리뷰 — 우선순위 뷰 & 알고리즘 전환 UI (2026-03-26)

### 변경 요약
- CbomPriorityView(신규): HIGH 기본 활성, 탭별 카드 그룹, 건수 표시
- CbomPage(수정): 목록/우선순위 탭 전환 추가
- api/algorithm.ts + useAlgorithmSwitch + AlgorithmSwitchPanel(신규): 알고리즘 전환 로직
- MSW handlers 수정: /actuator/algorithm POST 핸들러 추가
- CbomPriorityView.test + useAlgorithmSwitch.test(신규)

### 인수조건 달성
- ① HIGH/MEDIUM/LOW 카드 그룹 + 건수: 충족
- ② 불허용 알고리즘 선택 시 disabled: 부분 충족 (select 옵션이 허용 목록만 제공하는 방식으로 구조적 회피)
- ③ 전환 성공 후 ['cbom'] invalidate: 충족

### 위험 요소
- MSW 핸들러 거부 케이스 없음 → isError 경로 미검증
- 뷰탭 전환 시 필터/페이지 상태 유지 여부 의도 불명확

### 테스트 공백
- AlgorithmSwitchPanel 단위 테스트 없음
- useAlgorithmSwitch 실패 케이스 없음
