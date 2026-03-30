# PQC 하이브리드 게이트웨이 + CBOM 대시보드 — 구현 계획 (2026-03-02, rev.1)

## 목표
PQC 하이브리드 게이트웨이와 CBOM 대시보드를 핀테크 포트폴리오로 완성하기 위해,
React/Spring Boot/FastAPI/PostgreSQL 4개 레이어의 구현 단계와 K8s-Ready 모노레포 구조를 확정한다.

## 결정사항
- 인프라: Docker Compose + K8s-Ready (Stateless 설계, 환경변수 외재화)
- JWT: Spring Boot 직접 발급, ML-DSA 서명을 Python 엔진 호출로 이식
- CBOM: 자동 로깅(Primary) + 수동 재고 등록(Secondary) 하이브리드

## Phase 1 — 기반 인프라 & Crypto Engine (Week 1–2)
- Docker Compose 네트워크 격리 + 리소스 제한 + K8s-Ready Config 외재화
- FastAPI + liboqs: ML-KEM(암호화), ML-DSA(서명) 엔드포인트 + Trivy 스캐닝
- PostgreSQL 스키마: users/sessions(정규화) + cbom_assets/key_metadata(JSONB, 자동+수동 필드) + VACUUM 정책

## Phase 2 — API Gateway Spring Boot (Week 3–4)
- ML-DSA JWT 발급·검증 필터 (Python /sign, /verify 호출)
- Feign Client 비동기 호출 + Fallback + Stateless 원칙
- 하이브리드 게이트웨이: Algorithm Agility 인터페이스 + SNDL High-Risk 우선순위 라우팅

## Phase 3 — React CBOM 대시보드 (Week 5–6)
- TanStack Query + Zustand 구성
- CBOM 시각화, 우선순위 뷰, 실시간 모니터링 UI
- 수동 재고(Inventory) 등록 UI (레거시 자산 입력/편집/삭제)
- Prometheus/Grafana 알람 연동

## 인수조건
1. POST /api/encrypt 전 구간 정상 동작 + CBOM 자동 기록 + 수동 자산 등록·조회 가능
2. ML-DSA JWT 정상 발급·검증 + make test-dct 전 환경 통과 + Trivy Critical 0건 + 미인증 403
3. 환경변수 교체만으로 다른 호스트 기동 가능 + 알고리즘 전환 시 CBOM 이력 반영

## 디렉터리 구조 (rev.2 — 변경사항 반영)
pqc-security-project/
├── docker-compose.yml
├── .env.example
├── config/                          # K8s ConfigMap/Secret 대체용
│   ├── crypto-engine.env.example
│   ├── api-gateway.env.example
│   │── dashboard.env.example
│   └── resource-limits.env.example  # ★ 신규 — Limit 전용 파일로 분리
├── crypto-engine/
│   ├── Dockerfile
│   ├── requirements/                # ★ 신규
│   │   ├── base.txt                 # 공통 (fastapi, pydantic 등)
│   │   ├── prod.txt                 # -r base.txt + 운영 전용 (gunicorn 등)
│   │   └── dev.txt                  # -r base.txt + 개발 전용 (pytest, httpx 등)
│   └── app/
│       ├── routers/                 # /kem, /dsa, /sign, /verify
│       ├── services/
│       └── schemas/
├── api-gateway/
│   ├── Dockerfile
│   ├── pom.xml                      # Maven scope으로 의존성 분리 (변경 없음)
│   └── src/main/java/com/pqc/gateway/
│       ├── jwt/                     # ML-DSA JWT 발급·검증
│       ├── config/
│       ├── controller/
│       ├── service/
│       ├── client/
│       ├── entity/
│       └── repository/
├── dashboard/
│   ├── Dockerfile
│   ├── package.json                 # dependencies / devDependencies 분리 (변경 없음)
│   └── src/features/
│       ├── cbom/
│       ├── inventory/               # 수동 재고 등록 UI
│       ├── monitor/
│       └── auth/
├── db/
│   ├── init/
│   └── migrations/
├── infra/
│   ├── prometheus/
│   └── grafana/
└── docs/

## Phase 1 세부 계획 (2026-03-02, rev.1)

### Step 1-A. Docker 보안 기반 (26.03.02)
- Rootless Docker 모드 활성화
- cap_drop: ALL + 필요 권한만 cap_add (최소 권한 원칙)
- DOCKER_CONTENT_TRUST=1 환경변수 설정
- 컨테이너별 CPU/Memory Limit 명세
- 네트워크 분리: pqc-internal(내부) / pqc-public(외부)

### Step 1-B. Crypto Engine 컨테이너 (Day 3–5)
- Multi-stage Dockerfile: Stage1(liboqs 빌드) → Stage2(런타임만 복사)
- FastAPI 라우터: /kem/encrypt, /kem/decrypt, /dsa/sign, /dsa/verify
- algorithm_factory.py: ALGORITHM_ID 환경변수로 알고리즘 교체 (암호 민첩성)
- Trivy 스캔: Critical 0건 확인

### Step 1-C. PostgreSQL 스키마 (Day 4–5, 병렬)
- 정규화: users, sessions (algorithm_id 포함)
- JSONB: key_metadata, cbom_assets (source: auto/manual, risk_level 필드)
- GIN 인덱스: cbom_assets.asset, key_metadata.payload
- VACUUM 정책: autovacuum_vacuum_scale_factor=0.05, analyze_scale_factor=0.02

### Step 1-D. 통합 헬스체크 (Day 6)
- docker compose up -d → 전 컨테이너 healthy
- ML-KEM / ML-DSA 엔드포인트 응답 확인
- Trivy Critical 0건, DB 스키마 + GIN 인덱스 생성 확인

## Phase 1 라이브러리 및 구현 방향 (rev.2)

### crypto-engine/requirements/
- base.txt: fastapi, uvicorn[standard], pydantic, python-dotenv, oqs(liboqs-python)
- prod.txt: -r base.txt + gunicorn
- dev.txt: -r base.txt + pytest, httpx, pytest-asyncio

### api-gateway/pom.xml (Phase 1 기반)
- spring-boot-starter-web, spring-boot-starter-data-jpa, postgresql(runtime), actuator, lombok

### 구현 순서 원칙
- Step 1-A: cap_drop ALL 선언 후 cap_add, 네트워크 pqc-internal/pqc-public 분리
- Step 1-B: Multi-stage Dockerfile(builder/runtime 분리) → algorithm_factory.py(환경변수 교체) → Trivy 스캔
- Step 1-C: 001_schema.sql(테이블+GIN인덱스) → 002_vacuum_policy.sql(VACUUM 분리)
- Step 1-D: healthcheck 블록 + depends_on.condition: service_healthy 순서 보장

## Step 1-A 보완 (rev.3 — Rootless Docker + Resource Limits 구현 방향)

### Resource Limits 환경변수 외재화
- 위치: config/resource-limits.env.example (Limit 전용 분리 파일)
- 적용 방식: docker-compose.yml에서 ${VAR:-기본값} 형태로 주입
- 개발 기본값
  - crypto-engine: CPU=1.0 / MEM=512m
  - api-gateway:   CPU=0.5 / MEM=512m
  - db:            CPU=0.5 / MEM=256m
  - dashboard:     CPU=0.25 / MEM=128m
- 운영 권장값
  - crypto-engine: CPU=2.0 / MEM=1g
  - api-gateway:   CPU=1.0 / MEM=1g
  - db:            CPU=1.0 / MEM=512m
  - dashboard:     CPU=0.5 / MEM=256m
- 검증: docker stats → 컨테이너별 Limit 반영 여부 확인

## REVIEWER 경고 대응 계획 (Step 1-A, rev.4)

### 즉시 수행 (Step 1-B 이전)
- Task A(완): docker-compose.yml 각 서비스 environment 블록에서 DOCKER_CONTENT_TRUST: "1" 제거
- Task B(완): .env에 DOCKER_CONTENT_TRUST=1 설정 후 미서명 이미지 pull → 거부 확인
- Task C(완): docker compose up -d db → docker stats로 CPU/MEM Limit 부분 검증 

### Step 1-B 이후 수행
- Task D: 전 구간 docker compose up -d 검증
- Task E: docker stats 전체 컨테이너 Resource Limit 확인

## Step 1-A 구조 수정 (rev.5 — 환경변수 단일화 + Rootless 전략 교체)

### Resource Limits 단일화
- 삭제: config/resource-limits.env, config/resource-limits.env.example (유령 파일)
- 이유: Docker Compose는 루트 .env만 자동 로드. config/resource-limits.env는 env_file 미참조로 실제 동작 안 함
- 단일 진실 공급원: 루트 .env / .env.example (Resource Limits 변수 이미 포함됨)

### Rootless Docker → Dockerfile USER 전략으로 교체
- 삭제: 호스트 레벨 Rootless Docker 설정 절차 (로컬 의존, 재현성 없음)
- 대체: 각 서비스 Dockerfile Stage 2(runtime)에 USER nonroot 지시어 추가
- 효과: git clone한 모든 환경에서 동일하게 비루트 컨테이너 실행 보장
- Rootless Docker 모드는 프로덕션 서버 운영 정책으로 README 하단에 문서화만 수행

## Step 1-A 재현 가능 테스트 전략 (rev.6 — Makefile 도입)

### 문제
export DOCKER_CONTENT_TRUST=1은 현재 세션에서만 유효 → 재현 불가

### 해결: Makefile 도입
- 위치: 루트 Makefile (git 포함 → 모든 환경 동일 동작)
- 진입점 통일: docker compose 직접 실행 금지, make 타겟 경유
- 타겟: make up / make build (DOCKER_CONTENT_TRUST=1 인라인 주입)
- 타겟: make test-dct (DCT 재현 가능 테스트)
- 타겟: make trivy (Trivy 스캔)

### make test-dct 설계
- Stage 1: DOCKER_CONTENT_TRUST=1 + 서명된 이미지 pull → 정상 확인
- Stage 2: DOCKER_CONTENT_TRUST=1 + 미서명 태그 pull → 거부 오류 확인

### CI/CD 확장
- GitHub Actions: env: DOCKER_CONTENT_TRUST: "1" 선언
- K8s 이관 후: Admission Controller(Kyverno)로 대체

## test-dct 수정 계획 (rev.7 — Cosign 전환)

### 근본 원인
- Stage 2 이미지(chaeunkim/my-repo:v1.0) 미존재 → "image not found" 오류를 "trust 거부"로 오인식
- DOCKER_CONTENT_TRUST(Notary v1): deprecated 방향, 환경별 동작 불일치

### 즉시 수행 (Task 1)
- Stage 2 대상 이미지를 Docker Hub에 실존하는 미서명 이미지로 교체
- stderr 캡처 로직 추가: "No trust data" 문자열 유무로 거부 원인 구분

### Step 1-B 병행 (Task 2 — Cosign 도입)
- DCT → Cosign(Sigstore) 교체
- make test-dct: cosign verify 기반으로 재작성
- make setup: cosign 설치 포함
- .github/workflows/ci.yml: cosign verify 스텝 추가
- scripts/setup-cosign.sh 추가

## Step 1-B. Crypto Engine 컨테이너 세부 계획
- Cosign에 Trivy 결합. trivy까지 통과해야 이미지 다운 가능. 해당 기능 이미지 다운시 적재.
- USER nonroot 전략
- Task A: 전 구간 docker compose up -d 검증
- Task B: docker stats 전체 컨테이너 Resource Limit 확인

## Step 1-B 세부 계획 (2026-03-04, rev.8)

### 목표
crypto-engine Dockerfile(Multi-stage, liboqs + USER nonroot) 완성,
ML-KEM·ML-DSA FastAPI 라우터와 algorithm_factory.py 구현,
Trivy Critical 0건으로 컨테이너 기동 가능 상태 달성.

### 범위
1. Dockerfile 완성 — Stage1(cmake+liboqs) / Stage2(runtime+USER nonroot)
2. FastAPI 구현 — main.py, algorithm_factory.py, routers/kem.py, routers/dsa.py, schemas/
3. 검증 — make trivy Critical 0 + docker compose healthy + curl 왕복 테스트

### 인수조건
1. docker compose up -d crypto-engine → (healthy)
2. /kem/encrypt→/kem/decrypt, /dsa/sign→/dsa/verify 왕복 HTTP 200
3. make trivy → Critical 0건

## Step 1-B 수정 계획 (2026-03-04, rev.9 — review.md 반영)

### 하이브리드 라우팅 위치 확정
- crypto-engine: PQC 알고리즘 실행만 담당 (라우팅 판단 없음)
- api-gateway: Feign Client + Resilience4j Circuit Breaker로 장애 감지 → Classic Fallback (Phase 2)

### review.md 결함 수정
1. healthcheck: wget 기반으로 교체 (curl 미포함 문제 해결)
2. liboqs 전략 단일화: PyPI oqs 번들 OR cmake 빌드 혼용 금지
3. COPY --chown=nonroot:nonroot app/ /app/ 추가
4. docker-compose.yml ${VAR:-fallback} 추가
5. make inspect-limits 타겟 신설 (docker inspect 기반 Resource Limit 검증)

### Cosign → Trivy 순차 파이프라인
- make build-secure: cosign verify(베이스) → docker build → trivy --exit-code 1
- make build: 개발 편의용 유지 (파이프라인 없음)
- CI: make build-secure 호출

### 인수조건
1. make build-secure → 3단계 순서 실행, Critical 1건 이상 시 exit 1
2. docker compose up -d crypto-engine → (healthy) + /kem/encrypt /dsa/sign HTTP 200
3. make inspect-limits → CPU/MEM Limit .env 값 일치 확인

## Step 1-B 재설계 계획 (2026-03-04, rev.10 — pip 실패 + review.md 미통과 해결)

### pip install 실패 근본 원인
- cmake liboqs + oqs PyPI 번들 이중 충돌
- pip install --prefix=/install → .so 경로 오류

### 해결 전략
- cmake 블록 제거: oqs PyPI 번들 단일화 (venv + pip install만 수행하는 방향으로 단순화)
- inspect-limits 비교 허용. bc를 통한 수치 비교 + 오차 범위(Epsilon) 도입이 훨씬 견고.
- --prefix → python -m venv /venv + /venv/bin/pip install
- Stage 2: COPY --from=builder /venv /venv + ENV PATH="/venv/bin:$PATH"
- 검증 레이어: RUN /venv/bin/python -c "import oqs"
- make verify-all 신설. 빌드→기동→HTTP 왕복→inspect-limits를 하나의 make verify-all로 묶는다.

### inspect-limits 수정
- 현재: 출력만 (exit 0 항상)
- 수정: .env 파싱 후 NanoCPU 비교 → 불일치 exit 1

### 인수조건 (빌드→암호화 왕복)
1. make build-secure → Trivy Critical 0 + import oqs 검증 통과
2. POST /kem/encrypt→decrypt 왕복 shared_secret 일치 + POST /dsa/sign→verify valid:true
3. make inspect-limits → 수치 불일치 시 exit 1

## Step 1-B Dockerfile 재수정 (2026-03-04, rev.11 — import oqs 실패 해결)

### 근본 원인
cmake가 apt 설치됐으나 liboqs C 라이브러리 빌드 명령(git clone + cmake build)이 누락됨.
liboqs-python(PyPI)은 liboqs.so를 번들하지 않으므로 import oqs → ImportError.

### 수정 내용
- Stage 1: liboqs git clone + cmake build + install 블록 복원 (pip install 이전)
- Stage 2: COPY --from=builder /usr/local/lib/liboqs.so* + RUN ldconfig 추가
- base.txt: 변경 없음 (liboqs-python 유지)

### 인수조건
1. make build-secure → 'oqs OK' 출력 + exit 0
2. docker exec python -c "import oqs" → 에러 없음
3. /kem 왕복 + /dsa 왕복 모두 통과

## Step 1-B Dockerfile 수정 (2026-03-04, rev.12 — liboqs cmake 빌드 복원)

### 근본 원인
liboqs-python은 import 시 liboqs.so 부재 시 /root/_oqs 자체 빌드 시도.
cmake가 apt 설치됐으나 liboqs git clone + build 명령이 누락됨 → 자체빌드 실패.

### 수정 내용
- Stage 1: git clone liboqs + cmake build + install + ldconfig (pip install 이전)
- Stage 2: COPY --from=builder /usr/local/lib/liboqs.so* + RUN ldconfig (USER nonroot 이전)
- base.txt: 변경 없음

### 인수조건
1. make build-secure → 'oqs OK' 출력 (no /root/_oqs) + exit 0
2. /kem 왕복 shared_secret 일치 + /dsa 왕복 valid:true
3. docker exec python -c "import oqs" 에러 없음

## Step 1-B CVE 해결 계획 (2026-03-04, rev.13 — Trivy CRITICAL 제거)

### 취약점
- CVE-2025-7458: libsqlite3-0 3.40.1 (정수 오버플로우) — Debian 12 backport 없음
- CVE-2023-45853: zlib1g 1.2.13 (힙 버퍼 오버플로우) — zlib < 1.3, Debian 12 backport 없음

### 해결 전략
- 베이스 이미지: python:3.10-slim-bookworm → python:3.12-alpine
- Alpine: sqlite-libs 3.51.2, zlib 1.3.1 → 두 CVE 모두 패치됨
- apt-get → apk add, groupadd/useradd → Alpine addgroup/adduser 방식 변경

### 인수조건
1. Trivy CRITICAL: 0 (CVE-2025-7458, CVE-2023-45853 미검출)
2. docker compose up → (healthy) + import oqs 정상
3. /kem /dsa 왕복 테스트 통과

## Step 1-B liboqs 최종 확정 해결 (2026-03-04, rev.16)

### 소스코드 분석으로 확정된 근본 원인
oqs.py _load_liboqs()는 OQS_INSTALL_PATH 환경변수만 확인.
없으면 ~/._oqs만 탐색 (LD_LIBRARY_PATH, /usr/lib 완전 무시).
Stage 1: root+cmake → ~/root/_oqs 자체빌드 성공 → 검증 통과 (오탐)
Stage 2: nonroot+cmake없음 → ~/nonroot/_oqs 자체빌드 실패 → 무한루프

### 수정 내용 (2줄 추가)
- Stage 1: ENV OQS_INSTALL_PATH=/usr (venv 생성 전)
- Stage 2: ENV OQS_INSTALL_PATH=/usr (ENV 블록에 추가)

### 인수조건
1. make build-secure → 'oqs OK' (자체설치 없음)
2. docker logs → 'liboqs not found' 없음 + (healthy)
3. /kem /dsa 왕복 테스트 통과

## CRLF 개행문자 수정 (2026-03-04, rev.17)

### 근본 원인
Windows에서 편집된 스크립트 파일에 CRLF 개행문자 삽입.
set -euo pipefail\r → bash가 pipefail\r을 invalid option으로 인식.

### 영향 파일
- scripts/verify-http.sh (CRLF)
- scripts/check-limits.sh (CRLF)
- scripts/http_test.py (CRLF)

### 수정 내용
- sed -i 's/\r//' 로 3개 파일 LF 변환
- .gitattributes 추가: *.sh, *.py, *.yml eol=lf 강제

### 인수조건
1. file scripts/verify-http.sh → CRLF 없음
2. make verify-all → exit 0

## inspect-limits CRLF 수정 (2026-03-04, rev.18)

### 근본 원인
.env 파일 CRLF → grep으로 읽은 값이 "1.0\r" → bc에 전달 시 illegal character: ^M
check-limits.sh 자체도 CRLF → set -euo pipefail\r 실패

### 수정 내용
- sed -i 's/\r//' .env
- sed -i 's/\r//' scripts/check-limits.sh
- grep 파싱 라인에 tr -d '\r' 추가 (방어 로직)
- .gitattributes에 .env* eol=lf 추가

### 인수조건
1. file .env → CRLF 없음
2. make inspect-limits → illegal character 없음 + 통과 ✓

## 잔존 위험 4개 해결 계획 (2026-03-04, rev.19)

### ① MEDIUM — Cosign 대상 불일치
- Makefile [1/3]: python:3.12-alpine WARN-only 교체
- 출력 이미지(pqc-security-project-crypto-engine) cosign sign/verify 추가

### ② MEDIUM — 버전 미고정
- base.txt: liboqs-python==0.14.1
- Dockerfile: git clone --branch 0.14.1

### ③ LOW — cleanup 권한
- verify-http.sh: docker exec --user root "$CID" rm -f /tmp/http_test.py

### ④ LOW — LD_LIBRARY_PATH 과잉
- Dockerfile Stage 2: ENV LD_LIBRARY_PATH="/usr/lib" 삭제
- OQS_INSTALL_PATH=/usr로 절대경로 직접 로드, LD_LIBRARY_PATH 불필요

## 잔존 위험 추가 해결 (2026-03-04, rev.20)

### HIGH — .gitignore .cosign/ 추가
- 기존 *.key로 cosign.key 커버됨
- .cosign/ 디렉토리 전체 추가로 pub/기타 파일도 차단

### MEDIUM — cosign sign 로컬 실패
- cosign sign은 레지스트리 push 필요 → 로컬 SKIP으로 변경
- Makefile [2.5/3]: 로컬 SKIP + CI 안내 메시지 출력

### liboqs C 버전 수정
- 0.14.1 태그 미존재 → 0.14.0으로 수정
- liboqs-python 0.14.1은 liboqs C 0.14.0과 호환

### LOW — Makefile 주석 단계 번호
- [1/3]~[3/3] → [1/4]~[4/4] 갱신

## cosign sign 로컬 해결 (2026-03-04, rev.21)

### 해결 방법
임시 로컬 레지스트리(registry:2, localhost:5000) 경유
1. docker run registry:2 (pqc-registry)
2. docker tag → localhost:5000/crypto-engine:latest
3. docker push localhost:5000/...
4. cosign sign --allow-insecure-registry localhost:5000/...
5. cosign verify --allow-insecure-registry localhost:5000/...
6. docker rm -f pqc-registry (cleanup, 성공·실패 모두)

### 조건 분기
- .cosign/cosign.key 존재: 위 흐름 전체 실행
- 없음: SKIP + make cosign-keygen 안내

### 인수조건
1. make cosign-keygen 후 make build-secure → PASS
2. build-secure 완료 후 pqc-registry 컨테이너 없음
3. cosign.key 없을 때 SKIP + exit 0

## Step 1-C 상세 구현 계획 (2026-03-04, rev.22)

### 파일 구조
- db/init/001_schema.sql: users/sessions(정규화) + key_metadata/cbom_assets(JSONB) + GIN 인덱스
- db/init/002_vacuum_policy.sql: cbom_assets + key_metadata VACUUM 파라미터 분리

### 스키마 설계 포인트
- sessions.algorithm_id: 암호 민첩성 이력 추적
- cbom_assets.source: CHECK IN ('auto', 'manual') — 하이브리드 CBOM
- cbom_assets.risk_level: CHECK IN ('HIGH','MEDIUM','LOW','NONE')
- GIN: cbom_assets.asset, key_metadata.payload

### build-secure 전체 서비스 확장 (rev.22)
- 구조: build-secure = build-secure-db + build-secure-crypto + build-secure-gateway(SKIP) + build-secure-dashboard(SKIP)
- db: cosign WARN + trivy (빌드 없음)
- api-gateway/dashboard: Phase 2/3 완성 후 SKIP → 실제 파이프라인 교체

### 인수조건
1. docker compose up db → 4개 테이블 + 2개 GIN 인덱스 확인
2. make build-secure → 4개 서비스 순서 실행, exit 0

### 질문(Questions):
1. db trivy 실패 시 전체 차단 여부 => postgres:16-alpine Trivy Critical 발견 시 전체 빌드를 차단(Fail)
2. build-secure 실행 순서 — db → crypto-engine 순서(현재 compose depends_on과 일치)로 고정한다. 병렬 실행 시 오류로 인해 파이프라인이 불안정해질 위험.

---
## [2026-03-05] CVE-2025-68121 수정 계획

**목표:** postgres:16-alpine 내 gosu Go stdlib CVE-2025-68121 제거 → build-secure-db Trivy 스캔 통과

**질문:**
1. postgres:17-alpine으로 교체 가능한가, 16 유지 필요한가? => 교체 가능하다.
2. gosu가 런타임에서 실제 사용되는가? (삭제 가능 여부) => 런타임에서 사용되므로 삭제하면 안된다.
3. 현재 Trivy 실패가 CI/CD를 블로킹하는가? => 그렇다.

**범위:**
- Step 1: 패치된 postgres:16-alpine 태그에서 gosu Go 버전 확인
- Step 2: 방법 A(태그 핀닝) > 방법 B(gosu 레이어 교체) > 방법 C(.trivyignore) 순 선택
- Step 3: make build-secure-db 재실행으로 CRITICAL 0건 확인

**인수조건:**
1. trivy --exit-code 1 exit-code 0 종료
2. CVE-2025-68121 CRITICAL 미출력
3. make build-secure-db 완료 ✓ 출력

---
## [2026-03-05] CVE-2025-68121 대안 해결 계획 (su-exec 교체)

**목표:** gosu(Go 바이너리) → su-exec(C 구현체) 교체로 Go stdlib 취약점 원천 제거

**질문:**
1. 커스텀 Dockerfile.db 추가 허용 여부? => 가능하다.
2. su-exec으로 권한 강등 기능 완전 대체 가능 여부? => 가능하다.
3. VEX 예외처리가 보안 정책상 허용되는지? => 허용하지 않겠다.

**범위:**
- Step 1: su-exec(A안) 선택 — C 구현체, Go 의존 없음
- Step 2: Dockerfile.db 작성 — gosu 제거 + su-exec 설치 레이어
- Step 3: Makefile build-secure-db 스캔 대상을 pqc-db:secure로 변경

**인수조건:**
1. trivy CRITICAL 0건 (CVE-2025-68121 미검출)
2. su-exec에 Go stdlib 의존성 없음 확인
3. postgres 컨테이너 정상 기동 및 권한 강등 동작 유지

---
## [2026-03-05] CVE-2025-68121 재수정 계획 (rm -f 우회 실패 → Multi-stage 덮어쓰기)

**목표:** Trivy 레이어별 스캔 우회 불가 문제 해결 → COPY 덮어쓰기로 취약 바이너리 대체

**근본원인:** RUN rm -f는 최종 FS에서만 제거, Trivy는 하위 레이어도 독립 스캔

**범위:**
- Step 1: su-exec 대체 + OpenVEX 문서화
- Step 2: Dockerfile.db Multi-stage — alpine:3.21에서 su-exec 복사 → gosu 경로 덮어쓰기
- Step 3: trivy --vex docs/vex.json 플래그 추가

**인수조건:**
1. trivy CRITICAL 0건 (CVE-2025-68121 미검출)
2. gosu 경로에서 권한 강등 정상 동작
3. file 명령으로 최종 바이너리가 Go 바이너리 아님 확인

---
## [2026-03-05] 잔존 위험 및 테스트 공백 수정 계획

**무시 항목:** cbom_assets.updated_at 트리거(앱 레이어 처리), user_id 컬럼(전역 테이블 의도적 설계), SQL 순서(알파벳 보장)

**cbom_assets 설계 의도 명시:**
cbom_assets는 전역(global) CBOM 테이블로 의도적으로 user_id 없이 설계됨.
사용자별 필터링이 필요한 경우 Step 1-D에서 스키마 변경 검토.

**범위:**
- Step 1: Makefile up 타겟에 pqc-db:secure 이미지 존재 guard 추가
- Step 2: 001_schema.sql에 sessions/key_metadata user_id B-Tree 인덱스 추가
- Step 3: scripts/test-db-schema.sh 작성 + make test-db 타겟 등록

**인수조건:**
1. make up 미빌드 상태 에러 메시지 + exit 1
2. make test-db → 테이블 4개 + 인덱스 6개 + su-exec uid=70 확인
3. psql \di 결과에 신규 인덱스 2개 포함

---
## [2026-03-05] USER postgres 제거 및 entrypoint 권한 강등 패턴 전환

**목표:** Dockerfile.db의 USER postgres 제거 → runtime entrypoint gosu 방식으로 최소 권한 유지

**보안 레이어 구조 (확정):**
- Layer 1: cap_drop: ALL (docker-compose.yml) — 위험 capability 원천 차단
- Layer 2: entrypoint gosu (런타임) — root → postgres(uid=70) exec 교체
- Layer 3: Trivy+VEX (CI 게이트) — 빌드 시점 취약점 차단

**범위:**
- Step 1: Dockerfile.db에서 USER postgres 줄 제거
- Step 2: test-db-schema.sh 테스트 명령에 --user root 추가
- Step 3: 보안 레이어 구조 plan.md 명시

**인수조건:**
1. docker run --user root pqc-db:secure gosu postgres id → uid=70(postgres)
2. docker compose up db 후 docker top db → postgres uid=70 확인
3. make test-db → [3/3] PASS ✓

---
## [2026-03-05] no-new-privileges 추가 및 보안 검증 테스트 확장

**목표:** MEDIUM 위험 해소 + CapEff/setuid 검증 추가

**DAC_OVERRIDE 허용 사유 (문서화):**
- postgres entrypoint init 필수 (data dir 접근)
- 보상 통제: cap_drop:ALL + no-new-privileges:true + uid=70 런타임
- 재검토: Step 2-A 커스텀 entrypoint 도입 시

**질문**
- no-new-privileges: true 추가 시 docker-compose.yml의 db 서비스에만 적용할까, 모든 서비스에 일괄 적용할까? => 모든 서비스 일괄 적용
- CapEff 검증 테스트를 make test-db에 통합할까, 별도 make test-security 타겟으로 분리할까? => `make test-db` 통합 후 추후 래핑

**범위:**
- Step 1: docker-compose.yml db 서비스에 security_opt: no-new-privileges:true 추가
- Step 2: test-db-schema.sh에 CapEff=0 확인 + setuid 바이너리 없음 확인 추가
- Step 3: DAC_OVERRIDE 허용 사유 plan.md 명시

**인수조건:**
1. docker inspect → SecurityOpt no-new-privileges:true 확인
2. CapEff: 0000000000000000
3. setuid 바이너리 없음 (find 빈 출력)

---
## CI/CD 계획 (ci.yml 통합) — 2026-03-05

### 목표
1-A~1-C 구현물을 단일 .github/workflows/ci.yml에 통합
(빌드 · 기능 테스트 · Trivy 스캔)

### 질문
1. 기존 ci.yml 처리 방식: 현재 ci.yml은 1-A 전용(Cosign + Trivy postgres)으로 작성되어 있습니다. 기존 job(test-dct, trivy)을 그대로 유지하고 job 추가할지, 아니면 전면 교체할지? => 기존 유지 + 신규 Job 추가
2. crypto-engine 이미지 푸시 여부: CI에서 빌드한 이미지를 레지스트리에 푸시 없이 로컬 빌드(--load)만으로 Trivy 스캔과 통합 테스트를 수행하면 되는가? => 로컬 빌드(--load)로 충분
3. DB 스키마 검증 수준: 001_schema.sql·002_vacuum_policy.sql을 실제 PostgreSQL 서비스 컨테이너에 적용해서 테이블/인덱스 존재를 확인할지, SQL 문법 린트(sqlfluff 등)만으로 충분한지? => 실제 컨테이너 적용 (Integration Test)

### Jobs
| Job | 내용 | 커버 |
|-----|------|------|
| build-and-trivy | crypto-engine 빌드 + Trivy Critical 스캔 | 1-A, 1-B |
| integration-test | http_test.py (KEM/DSA 왕복) | 1-B |
| db-schema-test | PostgreSQL 서비스 + SQL 적용 검증 | 1-C |

### 인수조건
1. Trivy Critical 0건
2. http_test.py 3개 케이스 PASS
3. 4개 테이블 생성 확인

---
## [2026-03-05] CI integration-test liboqs 빌드 시간 최적화 계획

**목표:** integration-test 잡의 liboqs cmake 재빌드 제거
**선택지:** A(GHA Cache) / B(Artifact 전달) / C(GHCR base)
**권장:** A (Dockerfile 무수정)
**인수조건:**
- integration-test docker build 5분 이내
- 두 잡 이미지 동일성 보장
- PR 캐시 적용 또는 main 캐시 적립

---
## [2026-03-05] trivy 잡 오류 수정 계획

**목표:** CVE-2025-68121 VEX 억제 + trivy 잡 green 통과
**원인 후보:**
  A. vex.json PURL 불일치 (pkg:oci/pqc-db vs pqc-db:secure)
  B. install.sh main 브랜치 핀 불안정
  C. --vex 플래그명 변경 (v0.56.1)
**인수조건:**
  - trivy 잡 green 통과
  - CVE-2025-68121 VEX suppressed 확인
  - CRITICAL 0건 확인

---
## [2026-03-05] trivy 잡 install.sh 실패 수정 계획 (v2)

**근본 원인:** install.sh → gh-release-install 다운로드 단계에서
  GitHub API rate limit 또는 checksum 실패로 exit 1.
  -s curl 플래그로 에러 메시지 억제되어 로그 추적 불가.

**수정 방향:** install.sh 제거 → trivy-action@0.28.0 통일
**VEX 전환:** --vex CLI 플래그 → .trivyignore 또는 trivy.yaml config
**인수조건:**
  - 설치 단계 제거, trivy-action 단일 스텝
  - CVE-2025-68121 억제, CRITICAL 0건 green
  - build-and-trivy와 동일 액션 버전 통일

---
## [2026-03-05] trivy 잡 install.sh 실패 수정 계획 (v3)
범위(Scope - Steps):
Step 1 — trivy-action의 trivy-config 파라미터 활용
trivy-action@0.28.0은 --vex 직접 노출은 없지만 trivy-config 입력으로 YAML 설정 파일을 지정할 수 있음. 해당 YAML에서 VEX 파일 경로를 지정

---
## [2026-03-05] trivy 잡 VEX 유지 + install.sh 제거 계획 (v3 확정)

**목표:** `.trivyignore` 없이 `docs/vex.json`(OpenVEX)으로 CVE-2025-68121을 억제하면서, install.sh 방식을 제거하고 trivy-action으로 통일한다.

**보안 판단 근거:**
- `.trivyignore`: 억제 근거 없음, 감사 불가 → 퇴보
- OpenVEX: justification + impact_statement 명시, CSAF/VEX 표준 준수 → 유지

**범위(Scope):**
- Step 1: trivy-action@0.28.0의 `trivy-config` 파라미터로 `trivy.yaml` 지정
- Step 2: `trivy.yaml`에 `vex: [docs/vex.json]` 참조 (새 파일 1개 추가)
- Step 3: `trivyignores` 옵션 제거, install.sh 단계 제거

**인수조건:**
- `.trivyignore` 미사용, VEX 억제만으로 CVE-2025-68121 suppressed
- trivy 잡 green 통과 (CRITICAL 0건)
- build-and-trivy와 동일 trivy-action@0.28.0 버전 통일

---
## [2026-03-05] trivy.yaml 배치 위치 결정 계획

**목표:** 루트에 단독 존재하는 trivy.yaml을 구조적으로 일관된 위치로 이동한다.

**권장: D안 — `security/` 폴더 신설**
- trivy.yaml + vex.json을 보안 산출물 전용 폴더로 통합

**범위(Scope):**
- Step 1: `security/` 폴더 생성, `trivy.yaml` + `docs/vex.json` 이동
- Step 2: `ci.yml` trivy-config 경로 수정 (`security/trivy.yaml`)
- Step 3: `trivy.yaml` 내 vex 경로 수정 (`security/vex.json`)

**인수조건:**
- 루트에 trivy.yaml 미존재
- security/ 폴더에 보안 관련 파일 집중
- trivy 잡 green 통과

---
## [2026-03-05] trivy 잡 로그 가시성 확보 계획

**목표:** exit 1 원인이 "파일 없음"인지 "CVE 검출"인지 구분한다.

**범위(Scope):**
- Step 1: `ls -la security/` 로컬 확인 — security/trivy.yaml 미존재 시 그게 원인
- Step 2: CI에 파일 확인 단계 추가 (`ls -la security/`, `cat security/trivy.yaml`)
- Step 3: `exit-code: "1"` → `"0"` 임시 변경 후 CVE 전체 출력 확인 후 복원

**인수조건:**
- CI 로그에서 실패 원인 명확히 식별
- 원인 파악 후 해당 원인만 수정

---
## [2026-03-05] CI trivy exit code 1 원인 진단 및 수정 계획

**목표:** 실패 잡 특정 후 스캔 설정 결함 제거, 3개 잡 전체 green 달성

**제미나이 의심 포인트 평가:**
- exit-code:1 → 부분 유효 (build-and-trivy 잡만 해당)
- 이미지 부재 → 낮음 (load:true 빌드 선행)
- scan-type 충돌 → 무관 (미지정)
- Syncing repository 메시지 → 오탐 (trivy-action 바이너리 다운로드 정상 동작)

**실제 유력 원인:**
  A. build-and-trivy 잡: crypto-engine CRITICAL CVE + exit-code:"1" + VEX 없음
  B. trivy 잡: security/trivy.yaml의 vex 경로 해석 실패

**질문:**
1. 실패 잡이 trivy인가 build-and-trivy인가? (CI Actions 탭 확인)
2. crypto-engine 이미지에 CRITICAL CVE 잔존 여부?
3. trivy-action vex 경로 해석 워킹 디렉터리 확인

**범위:**
- Step 1: 실패 잡 특정
- Step 2A (build-and-trivy): exit-code 임시 0 전환 → CVE 목록 확인 → 베이스 이미지/VEX 수정
- Step 2B (trivy): vex 경로 절대경로 교체

**인수조건:**
1. 3개 잡 모두 green
2. CRITICAL 0건 로그 확인
3. VEX suppressed 또는 0 CRITICAL 명시

---
## [2026-03-05] trivy-action setup-trivy 설치 실패 수정 계획

**근본 원인:** exit-code:"0" 변경과 무관하게 실패.
setup-trivy@0.2.1이 GitHub Releases에서 Trivy v0.56.1 바이너리 다운로드 중 exit 1.
스캔 단계 이전에 설치 단계 자체가 실패하므로 exit-code 파라미터가 무의미.

**질문(Questions)**
1. GHA ubuntu-latest runner에서 /var/run/docker.sock 마운트가 허용되는가? (Docker-in-Docker 방식 사용 시 필수) => 허용
2. Trivy 버전을 v0.56.1로 고정해야 하는가, 아니면 최신 안정 버전 사용이 허용되는가? => 안정적인 부모 버전까지는 고정
3. trivy 잡의 VEX 파일(security/vex.json)을 Docker 컨테이너에 볼륨 마운트하는 방식이 구조적으로 허용되는가? => 허용

**범위:**
- Step 1: wget + dpkg -i로 Trivy .deb 사전 설치 스텝 추가
- Step 2: trivy-action에 skip-setup-trivy:true 추가 또는 run: trivy CLI 직접 호출
- Step 3: build-and-trivy / trivy 두 잡 모두 동일 패턴 적용

**인수조건:**
1. CI 로그에서 설치 단계 이후 exit 1 없이 스캔 도달
2. exit-code:"0" 실제 작동 확인
3. 두 잡 동일 설치 방식 통일

---
## [2026-03-06] 신규 CVE 7종 수정 계획

**목표:** trivy image --config security/trivy.yaml pqc-db:secure CRITICAL 0건 달성
(CVE-2025-68121 포함 CVE-2025-58183/61726/61728/61729/61730/47912 전부 해소)

**범위:**
- Step 1: trivy --format json으로 PkgName/FixedVersion 진단
- Step 2A: FixedVersion 존재 → Dockerfile.db 베이스 태그 교체 또는 apk upgrade 핀닝
- Step 2B: FixedVersion 없음 / 코드 미도달 → vex.json statements 항목 추가

**인수조건:**
1. CRITICAL 0건 (7개 CVE 전부 해소)
2. VEX 항목은 justification 명시 (감사 가능 유지)
3. CI trivy 잡 green


---
## [2026-03-06] Trivy 설치 단계 wget exit 8 수정 계획

**근본 원인:** v0.56.1 GitHub Releases 미존재 → wget HTTP 오류(exit 8) → 양 잡 설치 실패

**질문:**
1. trivy-action@0.28.0 자체 설치 버전 확인 필요
2. trivy 잡 --vex CLI 플래그 → trivy-config: security/trivy.yaml 대체 가능한가?
3. DOCKER_CONTENT_TRUST=1이 trivy-action 내부 동작에 영향 주는가?

**범위:**
- Step 1: 양 잡 wget/dpkg 수동 설치 스텝 제거, skip-setup-trivy 제거
- Step 2: trivy 잡 스캔 스텝 run→trivy-action 전환, trivy-config: security/trivy.yaml 유지
- Step 3: build-and-trivy 잡 수동 설치 스텝만 제거

**인수조건:**
1. 양 잡 설치 단계 exit 0
2. trivy 잡 VEX 억제 유지 CRITICAL 0건
3. build-and-trivy 잡 ignore-unfixed 유지 CRITICAL 0건

---
## [2026-03-06] Step 1-B 보안 결함 수정 계획

**목표:** crypto-engine API HIGH 2종(비밀키 노출·키쌍 재생성) + KEM 모델 훼손 수정, MEDIUM/LOW 정리

**질문:**
1. dsa_sign 키쌍 재생성: 데모 의도인가? 키 영속성 도입 범위 확인 필요 => 명백한 "데모용 설계"이며, 키 영속성(Key Persistence) 도입 필수
2. KEM 분리 방향: /keygen+/encap 2엔드포인트 vs request body에 public_key 수신 => Request Body에 공개키 포함 (Stateless)
3. algorithm_factory 연결이 기능 변경 없는 dead code 제거인가? => "Factory 패턴으로의 완전한 전환"

**범위:**
- Step 1: kem.py·dsa.py 응답 + schema에서 secret_key 필드 제거
- Step 2: KEM 보안 모델 재설계(키생성/캡슐화 분리) + get_kem()/get_signature() 팩토리 연결
- Step 3: str(e) 에러 마스킹, Dockerfile 디버그 라인 제거

**인수조건:**
1. 응답 JSON에 secret_key 미포함
2. KEM 키 생성자·캡슐화 주체 분리 검증
3. http_test.py 통과

---
## [2026-03-06] Step 1-B Final 해결 검증 계획

**목표:** review.md 6/6 해결 항목 자동화 증명 + 잔존 테스트 공백 3종 커버

**질문:**
1. http_test.py 확장 vs 별도 test_edge.py 분리 — CI 잡 동시 실행 가능한가? => 별도의 test_edge.py로 분리하는 것을 강력히 권장하며, 동시 실행 가능하다.
2. 빈 message DSA sign 기대 동작: 200 허용 vs 422 차단? => 앱 레벨에서 422 Unprocessable Entity로 차단
3. inspect-limits 비교 기준값 필드명 (.env 확인 필요) => CRYPTO_ENGINE_CPU_LIMIT, CRYPTO_ENGINE_MEM_LIMITㄴ

**범위:**
- Step 1: http_test.py 케이스 추가 (secret_key 부재, 키 영속성, 에러 마스킹)
- Step 2: 엣지 케이스 (422/400 분기, 빈 message) 커버
- Step 3: check-limits.sh 수치 비교 + exit 1 자동화

**인수조건:**
1. http_test.py 전 케이스 PASS, CI integration-test green
2. 6/6 해결 항목 단언으로 명시적 증명
3. inspect-limits 수치 불일치 시 exit 1 동작

---
## [2026-03-06] KEM 비호환 공개키 400 미반환 수정 계획

**근본 원인:** liboqs encap_secret()이 Python 예외 미발생 → 길이 불일치 묵인 → 200 반환
**fix 위치:** kem.py encap_secret 호출 전 kem.details['length_public_key'] 길이 사전 검증

**질문:**
1. 길이 동적 조회 vs 상수 하드코딩 (알고리즘 민첩성 고려 시 동적 필수) => 무조건 kem.details['length_public_key']로 동적 조회.
2. 정확한 길이 + 잘못된 내용 케이스도 예외 미발생인가? => liboqs는 예외 없이 200 (성공)을 반환하며, 암호문(Ciphertext)과 공유 비밀(Shared Secret)을 생성함.
3. test_edge.py [4] 입력 변경 여부 => 두 가지 케이스를 분리하여 모두 테스트하는 것이 좋으나, 우선순위는 "길이 불일치".

**범위:**
- Step 1: kem.py 길이 검증 추가 (details['length_public_key'] != len → 400)
- Step 2: test_edge.py [5] 추가 여부 결정

**인수조건:**
1. test_edge.py [4] 400 + "캡슐화 오류" 반환
2. [1~3] regression 없음
3. 정상 KEM 흐름 200 유지

---
## [2026-03-06] urlopen 타임아웃 미설정 수정 계획

**근본 원인:** urlopen timeout 없음 → gunicorn 단일 워커 점유 → docker exec마다 프로세스 누적
**fix 위치:** http_test.py·test_edge.py get()/post()/post_expect_error() urlopen에 timeout=10 추가

**질문:**
1. KEM keygen 정상 응답 시간 → timeout 값 결정
2. gunicorn --workers 2 추가 여부
3. ci.yml 헬스체크 urlopen 동일 문제 여부

**범위:**
- Step 1: 누적 프로세스 kill
- Step 2: urlopen timeout=10 추가
- Step 3: Dockerfile gunicorn 워커 수 검토

**인수조건:**
1. ps aux에 hung 프로세스 0개
2. 타임아웃 시 exit 1 (무한 대기 없음)
3. 정상 흐름 PASS 유지


---
## [2026-03-06] Step 1-D 통합 헬스체크 계획

**목표:** docker compose up -d 후 Step 1 범위 healthy 달성 + 검증 스크립트 완성

**현황:**
- crypto-engine/db healthcheck ✅, api-gateway/dashboard healthcheck ❌
- api-gateway Dockerfile CMD 없음 → 즉시 종료
- health-check-all.sh 미존재, check-limits.sh 수치 비교 미완성

**질문:**
1. Step 1-D 기동 범위: 전체 vs crypto-engine+db 2개만 => docker compose up -d crypto-engine db로 Step 1 범위만 기동
2. check-limits.sh: .env NanoCpus/Memory docker inspect 비교 방식 확정 여부 => docker inspect의 NanoCpus 및 Memory 필드와 비교하는 방식
3. Trivy 0건: CI 결과 대체 vs 로컬 재실행 => CI 잡 결과로 대체 가능하나, CI를 거치지 않고 로컬에서 빌드한 이미지(build: .)를 바로 테스트하는 경우라면, Step 1-D 스크립트에 Trivy 실행을 옵션 플래그로 넣어둔다.

**범위:**
- Step 1: api-gateway 스텁 CMD 추가 또는 기동 범위 한정 결정
- Step 2: scripts/health-check-all.sh 신규 (healthy 대기 + http_test + psql 검증)
- Step 3: check-limits.sh 수치 비교 + exit 1 완성

**인수조건:**
1. 대상 컨테이너 모두 healthy (120초 이내)
2. health-check-all.sh KEM/DSA + 테이블4 + GIN인덱스6 PASS
3. inspect-limits 수치 불일치 시 exit 1

---
## [2026-03-06] Step 1-D Review 수정 계획

**목표:** health-check-all.sh shebang 오타 + test_edge.py/check-limits.sh 미연결 수정 + 방어 처리

**지적 타당성:** 4개 지적 + 3개 테스트 공백 전부 타당

**질문:**
1. check-limits.sh: health-check-all.sh 내 통합 vs 별도 make 타깃 유지 => health-check-all.sh의 5번째 스텝으로 통합.
2. --no-build 플래그 방식: 위치인자 vs 환경변수 => 스크립트 자체는 위치 인자($1)로, Makefile은 환경변수 방식으로 이원화
3. Trivy 로컬 스캔: 이번 범위 포함 vs LOW 제외 => 이번 범위에서는 제외(CI 위임)하되, 스크립트 내에 TODO 주석으로 남기는 것을 권장. TRIVY=1 환경변수가 있을 때만 실행되는 선택적 스텝으로 구성하는 방법 존재.

**범위:**
- Step 1: babash → bash shebang 수정
- Step 2: test_edge.py docker cp + exec + 정리 추가
- Step 3: check-limits.sh 호출 스텝 추가
- Step 4: trap/DB_CID 빈값 체크/--no-build 플래그 방어 처리

**인수조건:**
1. ./health-check-all.sh 직접 실행 shebang 오류 없음
2. http_test.py + test_edge.py 모두 실행 (CI 일치)
3. check-limits.sh 수치 불일치 시 exit 1

---
## [2026-03-06] CI 보강 계획 (inspect-limits + Trivy exit-code 복원)

**목표:** CI Resource Limits 자동 검증 추가, Trivy 게이트 복원, compose 통합 잡 결정

**지적 타당성:** MEDIUM 1개(타당), LOW 2개(타당)

**질문:**
1. inspect-limits 잡: POSTGRES_PASSWORD CI 주입 방식 (secrets vs 고정값) => .env 파일을 직접 사용하는 대신, GHA의 env: 컨텍스트를 활용한 런타임 주입.
2. build-and-trivy exit-code "1" 복원: ignore-unfixed+현재 이미지 CRITICAL 0건 확인 선행 필요 => 먼저 exit-code: 0 상태로 ignore-unfixed: true 결과 보고서를 확인하는 'Dry Run' 단계를 거침.
3. compose-integration 잡: Phase 1 마감 전 vs Phase 2 연동 시 추가 => Phase 1 마감 전에 추가하여 'Core'의 안정성을 먼저 확보.

**범위:**
- Step 1: ci.yml inspect-limits 잡 추가 (compose up → check-limits.sh)
- Step 2: trivy/build-and-trivy exit-code "0" → "1" 복원 + 근거 주석
- Step 3: compose-integration 잡 (타이밍 결정 후)

**인수조건:**
1. .env 수치 불일치 → inspect-limits 잡 FAIL
2. 신규 CRITICAL CVE → trivy 잡 exit 1 (VEX 항목 통과)
3. (Step 3 포함 시) crypto-engine + db 동시 healthy CI 확인

## trivy-action 버전 업그레이드 계획 (2026-03-08)
### 목표
trivy-action@0.28.0 바이너리 설치 실패 해소 → 최신 버전 교체

### Steps
1. aquasecurity/trivy-action 최신 릴리스 태그 확인
2. ci.yml 내 trivy-action@0.28.0 두 곳 최신 버전 교체
3. CI 재실행 → pqc-db + crypto-engine 두 잡 통과 확인

### 인수조건
1. 바이너리 설치 단계 exit 0
2. pqc-db CRITICAL 0건 (VEX 억제)
3. crypto-engine CRITICAL 0건 (zlib 패치)

## pqc-db:secure Trivy CRITICAL 해소 계획 (2026-03-08)
### 목표
pqc-db:secure CRITICAL CVE 2건을 .trivyignore 없이 해소한다.

### CVE 목록
- CVE-2026-22184 (zlib 1.3.1-r2 → 1.3.2-r0): alpine 패키지
- CVE-2025-68121 (gosu stdlib v1.24.6 → 1.24.13): gobinary

### Steps
1. db/Dockerfile.db: apk add --no-cache 'zlib>=1.3.2-r0' 추가
2. security/vex.json: product ID pkg:oci/pqc-db → pkg:oci/pqc-db:secure 교체 (7개 항목)
3. 로컬 검증: trivy image --severity CRITICAL --exit-code 1 --vex security/vex.json pqc-db:secure

### 인수조건
1. 로컬 및 CI 게이트 exit 0
2. VEX product ID 수정만으로 CVE-2025-68121 억제
3. .trivyignore 미사용

## pqc-db VEX → --skip-files 전환 + 로컬/CI 동기화 계획 (2026-03-08)
### 근본원인
OpenVEX product 매칭은 레지스트리 이미지 기준 → 로컬 빌드 이미지 digest 없음 → pkg:oci 매칭 불가

### Steps
1. ci.yml 게이트: --vex → --skip-files /usr/local/bin/gosu 교체
2. db/Dockerfile.db: apk add --no-cache 'zlib>=1.3.2-r0' 추가
3. Makefile: trivy-db 타겟 추가 (CI 게이트 명령어와 동일)

### 인수조건
1. make trivy-db 로컬 = CI 게이트 동일 결과
2. CI 게이트 exit 0
3. .trivyignore 미사용

## compose-integration config/crypto-engine.env 누락 수정 계획 (2026-03-08)
### 원인
config/crypto-engine.env가 .gitignore(*.env)에 의해 CI에 미전달
### Steps
1. ci.yml compose-integration + inspect-limits .env 생성 단계에 cp config/crypto-engine.env.example config/crypto-engine.env 추가
2. api-gateway.env, dashboard.env 누락 여부 확인 (해당 서비스 CI 미기동 시 무시)
### 인수조건
1. docker compose up --wait crypto-engine db exit 0
2. .gitignore 변경 없음

## DSA 키 영속성 근본 수정 계획 (2026-03-08)
### 원인
gunicorn -w 2에서 worker별 독립 keypair 생성 → 요청이 다른 worker로 분산 시 public_key 불일치

### 해결방법
--preload 플래그: master에서 algorithm_factory.py 1회 로드 → fork()로 worker 상속 → 동일 키쌍 공유

### Steps
1. Dockerfile CMD에 --preload 추가 (-w 2 유지)
2. 로컬 연속 /dsa/sign 2회 → public_key 일치 확인
3. CI 왕복 테스트 [5/5] 통과 확인

### 보안
키 재료 메모리 내 유지, 파일/환경변수/네트워크 노출 없음

### 인수조건
1. -w 2 유지
2. public_key 항상 일치
3. secret_key 외부 미노출

## compose-integration api-gateway.env + dashboard.env 누락 수정 (2026-03-08)
### 원인
docker compose ps 실행 시 전체 서비스 env_file 파싱 → api-gateway.env, dashboard.env 미존재 → ps 결과 빈 문자열 → docker inspect 실패

### Steps
1. compose-integration + inspect-limits .env 생성 단계에 api-gateway + dashboard env example 복사 추가
2. docker compose ps -q crypto-engine 정상 반환 확인

### 인수조건
1. docker compose ps -q 컨테이너 ID 반환
2. healthy 확인 단계 exit 0
3. 민감값 미노출

---
## [2026-03-08] CVE-2025-68121 CI --skip-files 경로 불일치 수정 계획

**원인 확정:** trivy v0.69.3에서 컨테이너 경로를 leading `/` 없이 표현
  → --skip-files /usr/local/bin/gosu 매칭 실패

**수정 대상:**
1. ci.yml:105  /usr/local/bin/gosu → usr/local/bin/gosu
2. Makefile:158 동일 경로 값 동기화
3. trivy-action 리포팅 단계 VEX 경로 해석 확인

**AC:** CI trivy 게이트 exit 0, .trivyignore 미사용, Makefile↔CI 경로 일치

---
## [2026-03-08] trivy CI 2단계 구조 → 단일 trivy-action 통합 계획

**원인:** trivy-action이 config/env 잔존 → 게이트 step에서 VEX+skip-files 충돌
**로컬 통과 이유:** VEX 잔존 없이 --skip-files만 적용

**Scope:**
1. CI 진단 step 추가 (which trivy, printenv|grep TRIVY, file /usr/local/bin/gosu)
2. 보고+게이트 2단계 → 단일 trivy-action(exit-code:1) 통합
3. security/trivy.yaml에 skip-files: [usr/local/bin/gosu] 추가

**AC:** 진단 log 확인, C binary 교체 확인, CI trivy 잡 exit 0

---
## [2026-03-08] trivy config skip-files 버그 → CLI 플래그 방식 복원

**원인 확정:**
- trivy v0.69.3에서 config 파일(`trivy.yaml`)의 `skip-files:` 키가 gobinary 스캐너에 미적용 (버그)
- 로컬 CLI `--skip-files usr/local/bin/gosu` → PASS
- config 방식 `--config security/trivy.yaml` → FAIL (로컬 재현 확인)

**변경 파일 목록:**
1. `.github/workflows/ci.yml` — trivy 잡
2. `security/trivy.yaml` — skip-files 제거 + 버그 주석

**변경 내용:**

### 1. ci.yml (trivy 잡, lines 80-96)
현재: 단일 trivy-action (exit-code:1, trivy-config: security/trivy.yaml)
변경: 2단계 복원
  - Step 1: trivy-action@0.35.0 (exit-code:"0") → trivy 바이너리 PATH 설치
  - Step 2: run: trivy image --severity CRITICAL --exit-code 1 --skip-files usr/local/bin/gosu pqc-db:secure
  - 진단 step 제거 (목적 달성)

### 2. security/trivy.yaml
현재: skip-files: [usr/local/bin/gosu]
변경: skip-files 섹션 제거
      주석 추가: "skip-files config 방식은 trivy v0.69.3 gobinary 스캐너에 미적용 (버그) → CI는 CLI --skip-files 플래그 사용"

**검증 방법:**
- CI trivy 잡 → exit 0
- make trivy-db → exit 0 (Makefile:169 CLI 플래그 동일, 변경 불필요)

**AC:**
1. CI trivy 잡 exit 0 (CVE-2025-68121 suppressed)
2. 로컬 make trivy-db / CI 동일 CLI 플래그 사용
3. trivy.yaml에 버그 사유 주석 기록

---
## [2026-03-08] 보안 결함 수정 계획 (Step 1 범위)

**수정 대상 (2개 — 최소 변경):**

### 1. liboqs git clone 버전 고정 [고위험]
- 파일: crypto-engine/Dockerfile:23
- 변경: `--depth 1` → `--depth 1 --branch 0.15.0`
- 근거: 빌드 재현성 + 공급망 위험 제거

### 2. DSA message 입력 크기 제한 [중위험]
- 파일: crypto-engine/app/schemas/dsa.py
- 변경: message 필드에 Field(max_length=65536) 적용 (sign, verify 양쪽)
- 근거: 무제한 페이로드 → 메모리 소진 DoS 차단

**보류 항목 (Step 2 이후):**
- [치명적] KEM secret_key/shared_secret 노출 → KEM API 전체 재설계 필요
- [치명적] decryption oracle 패턴 → 아키텍처 변경
- [고위험] 전 엔드포인트 인증 없음 → 인증 레이어 신규 구현

**검증 방법:**
- make build-secure-crypto → 빌드 성공 + liboqs 0.15.0 로그 확인
- curl -X POST /dsa/sign -d '{"message":"A"*65537}' → 422 응답 확인
- CI build-and-trivy 잡 통과

**AC:**
1. Dockerfile 빌드 로그에 liboqs 0.15.0 태그 클론 확인
2. 65537자 message → 422 Unprocessable Entity
3. CI build-and-trivy exit 0

---
## [2026-03-08] 보안 결함 수정 계획 (Step 1 범위) — rev.3

### 3. test_edge.py DSA 크기 제한 케이스 추가
- 파일: scripts/test_edge.py
- 변경: "A"*65537 message → /dsa/sign 422 응답 검증 케이스 추가
- CI: integration-test 잡이 이 파일을 실행하므로 자동 반영
---
## Phase 2 — API Gateway Spring Boot 세부 계획 (Week 3–4, 2026-03-09 ~ 2026-03-20)

### Week 3 — Spring Boot 기반 + JWT 인증 레이어 (Day 1–5)

**Day 1 (3/9): api-gateway 프로젝트 셋업**
- api-gateway/ Spring Boot 3.x + Gradle 모듈 생성
- Docker Compose 연동 (pqc-network 내부 통신)
- GET /api/health 헬스체크 + CI 빌드 잡 추가
- [관건 5] React + Spring Boot + PostgreSQL 기술 스택 정렬 확인

**Day 2 (3/10): Feign Client 구성**
- CryptoEngineClient: /dsa/sign, /dsa/verify Feign 인터페이스
- Resilience4j CircuitBreaker + Fallback 설정
- Stateless 원칙: 세션 없음, 요청별 독립 호출

**Day 3 (3/11): ML-DSA JWT 발급**
- POST /api/auth/login → JWT 발급
- JWT payload: userId, algorithm, exp
- Feign → crypto-engine /dsa/sign → 서명된 JWT 응답
- [관건 3] NIST 표준 ML-DSA 서명을 JWT 발급 흐름에 적용
- **다양한 방법을 시도해보며 latency를 줄일 최적을 찾고, 포트폴리오에 기입해라**
  1. Connection Pooling
  2. HTTP/2 적용(하려했으나 uvicorn에서 미지원이므로 기각)
  3. Keep-Alive 활성화
  4. gRPC(Protocol Buffers) 도입?? json대신 이걸 사용할 수 있는가? (Day 5 이후)

**Day 4 (3/12): JWT 검증 필터 + 인증 레이어 [보안 이슈 해결]**
- Spring Security JwtAuthFilter: Bearer 토큰 파싱 → Feign → /dsa/verify
- 미인증 요청 → 403 Forbidden
- [관건 6] 하이브리드 게이트웨이 도입 시작 — 기존 알고리즘과 PQC 공존 구간 설계
- ★ 보류 항목 해결: "[고위험] 전 엔드포인트 인증 없음"
- ★ 인수조건 2 달성: 미인증 403
- 진입 체크리스트 (필수)
  1. SecurityConfig.java: /api/health + /actuator/health → permitAll()
  2. SecurityConfig.java: csrf().disable() (Stateless REST)
  3. SecurityConfig.java: 나머지 경로 → authenticated()
  ※ 미이행 시: docker-compose healthcheck 403 → restart loop → 전체 스택 기동 불가
- Day 4 통합 위험 수정(1 + 2 + 5)

**Day 5 (3/13): Week 3 통합 + CI**
- make test-dct 포함 통합 확인
- CI: api-gateway Trivy 스캔 잡 추가
- AC: JWT 발급·검증 왕복 + 미인증 403 확인
- 포트 분리 계획
  - application.yml: management.server.port: 8081
  - docker-compose.yml healthcheck: http://localhost:8081/actuator/health
  - 8081 포트: pqc-internal 전용 (외부 미노출)
---

### Week 4 — KEM 재설계 + 게이트웨이 완성 (Day 6–10)

**Day 6 (3/16): KEM API 재설계 [보안 이슈 해결]**
- ★ 보류 항목 해결: "[치명적] secret_key 외부 노출"
- [관건 3] 암호 민첩성 — KEM 키 관리를 서버사이드로 재설계하여 알고리즘 전환 용이성 확보
- 현재: /kem/keygen → secret_key 클라이언트 반환 (기밀성 훼손)
- 변경: 서버사이드 키 보관 (key_metadata 테이블)
  - POST /api/kem/init → key_id 반환 (secret_key 외부 비노출)
  - POST /api/kem/encrypt → key_id + plaintext → ciphertext

**Day 7 (3/17): decryption oracle 패턴 수정 + /api/encrypt 전 구간 [보안 이슈 해결]**
- [관건 2] 고쓰기 워크로드(CBOM 자동 로깅) 발생 시점 — VACUUM 정책 실부하 검증
- [관건 7] CBOM 기반 PQC 전환 우선순위 데이터 수집 시작
- ★ 보류 항목 해결: "[치명적] decryption oracle 패턴"
- 변경: POST /api/kem/decrypt → key_id + ciphertext (secret_key 수신 제거)
- POST /api/encrypt 전 구간 연결
- CBOM 자동 로깅: 암호화 이벤트 → cbom_assets INSERT
- ★ 인수조건 1 달성: POST /api/encrypt 전 구간 정상 + CBOM 기록

**Day 8 (3/18): Algorithm Agility 인터페이스**
- [관건 3] 암호 민첩성 완성 — 환경변수 교체만으로 KEM/DSA 알고리즘 전환
- [관건 4] 새 표준 등장 시 신속 교체 가능한 구조 완성
- AlgorithmStrategy 인터페이스: KEM/DSA 알고리즘 추상화
- 환경변수(KEM_ALGORITHM_ID, DSA_ALGORITHM_ID) 교체 → 재빌드 없이 전환
- CBOM 이력: 알고리즘 전환 이벤트 cbom_assets 반영
- ★ 인수조건 3 달성: 환경변수 교체만으로 전환 + CBOM 이력

**Day 9 (3/19): SNDL High-Risk 우선순위 라우팅**
- [관건 6] 하이브리드 게이트웨이 완성 — PQC + 기존 알고리즘 혼재 + Fallback
- [관건 7] CBOM High-Risk 자산 식별 → PQC 우선 라우팅 (SNDL 공격 대응)
- RiskClassifier: 요청 민감도 분류 (HIGH/MEDIUM/LOW)
- HIGH → ML-KEM-1024, MEDIUM → ML-KEM-768, LOW → ML-KEM-512
- 라우팅 필터 구현 + CBOM risk_level 필드 연동

**Day 10 (3/20): Phase 2 완성 + CI 전체 검증**
- [관건 4] 표준 변경 시 교체 용이성 최종 검증
- [관건 8] Phase 3 진입을 위한 React 상태관리 도구(TanStack Query + Zustand) 선택 확정
- 전체 인수조건 검증
- CI: Phase 2 잡 완성 (build + trivy + integration)
- Phase 3 진입 준비

---

### Phase 2 인수조건 (plan.md 기준)
1. POST /api/encrypt 전 구간 정상 동작 + CBOM 자동 기록 ← Day 7
2. ML-DSA JWT 정상 발급·검증 + 미인증 403 + Trivy Critical 0건 ← Day 4–5
3. 환경변수 교체만으로 전환 가능 + 알고리즘 전환 시 CBOM 이력 반영 ← Day 8

---
## [2026-03-09] Day 1 세부 계획 확정판 — api-gateway 프로젝트 셋업

### 아키텍처 결정 (확정)
- 웹 스택: Spring MVC (spring-boot-starter-web)
- 모듈 구조: 독립 단일 모듈 (api-gateway/settings.gradle 독립 선언)
- CI 구조: 별도 잡 api-gateway-build (기존 build-and-trivy 잡과 격리 병렬)

### 범위
- Step 1: api-gateway/ 독립 단일 모듈 골격 (settings.gradle, build.gradle, gradlew, application.yml)
- Step 2: HealthController — GET /api/health → {"status":"UP"}, Actuator /actuator/health (Docker 내부용)
- Step 3: Dockerfile 멀티스테이지 (JDK21 빌드 → JRE21 실행) + docker-compose.yml api-gateway 서비스 (pqc-network)
- Step 4: .github/workflows/ci.yml api-gateway-build 잡 추가 (gradlew build → Docker 빌드 → Trivy)

### 인수조건
1. curl localhost:8080/api/health → 200 {"status":"UP"}
2. curl localhost:8080/actuator/health → 200 {"status":"UP"} (컨테이너 HEALTHCHECK 통과)
3. GitHub Actions api-gateway-build 잡 독립 exit 0 + Trivy 통과

---
## [2026-03-09] Day 1 장애 수정 계획 v2 — Connection reset 원인 확정

### 원인
- Spring Boot 3.x Gradle bootJar + jar 동시 실행 → build/libs/에 fat JAR + plain JAR 2개 생성
- Dockerfile `COPY *.jar app.jar` → plain JAR 복사 시 java -jar 크래시
- restart: unless-stopped 재시작 루프 → Docker proxy가 8080 점유 → RST 반환

### 수정 범위
- Step 1: docker compose ps / logs --tail=20 으로 재시작 루프 확정
- Step 2: build.gradle에 jar.enabled = false 추가 (plain JAR 생성 차단)
- Step 3: Dockerfile HEALTHCHECK wget → apk add curl + curl -f 교체
- Step 4: docker build → docker run 격리 검증 후 docker compose up 전체 검증

### 인수조건
1. docker compose ps api-gateway → STATUS healthy
2. docker run 격리 → curl localhost:8080/api/health → 200 {"status":"UP"}
3. docker compose logs --tail=20 → Started ApiGatewayApplication 확인

---
## [2026-03-09] Day 1 리뷰 수정 계획 — review.md Week 3 Day 1 반영

### 질문
1. CI api-gateway-build 잡의 호스트 Gradle 빌드(L338–340)가 Docker 빌드와 중복인 것을 인지하고 있는가? — 호스트 빌드를 gradle test(단위 테스트 실행)로 목적 전환할지, 제거할지 결정 필요. => gradle test(단위 테스트 실행)로 목적 전환
2. config/api-gateway.env.example에 현재 어떤 키가 정의되어 있는가? => CRYPTO_ENGINE_URL, SPRING_PROFILES_ACTIVE=dev

### 타당성 판정 요약
- [HIGH] Spring Security permitAll() 누락 → 타당, Day 4 진입 전 사전 계획 수립
- [MEDIUM] gradle wrapper 미사용 → 타당, 즉시 수정 (로컬/CI/Docker 일관성)
- [MEDIUM] COPY *.jar → 이미 해결됨 (jar.enabled=false), CI 중복 빌드 구조 비효율 잔존
- [LOW] curl 공격 면적 → 수용 (HEALTHCHECK 필수, 트레이드오프 문서화)
- [LOW] Actuator 포트 미분리 → Day 5로 이월
- 테스트 공백 → 즉시 수정 (HealthController 단위 테스트 추가)

### 수정 범위
- Step 1: gradle wrapper 추가 (gradlew + gradle/wrapper/) → Dockerfile + CI 명령어 ./gradlew로 통일
- Step 2: CI api-gateway-build 호스트 Gradle 스텝 → ./gradlew test로 목적 전환
- Step 3: HealthControllerTest.java 추가 + build.gradle spring-boot-starter-test 의존성
- Step 4: Day 4 Spring Security 진입 전 사전 계획 (permitAll + 관리 포트 8081 분리)
- Step 5: [관건 5] api-gateway↔db↔dashboard 기술 스택 정렬 확인 (누락 항목 보완)

### 인수조건
1. cd api-gateway && ./gradlew test → HealthControllerTest 통과
2. CI api-gateway-build: ./gradlew test → docker build → Trivy → 모두 exit 0
3. docker compose up api-gateway --no-deps → curl localhost:8080/api/health → 200

---
## [2026-03-09] gradle-wrapper.jar git 추가 + 보안 완화 계획

### 문제
- .gitignore의 `*.jar` 규칙이 gradle-wrapper.jar 차단
- gradle-wrapper.properties에 distributionSha256Sum 없음 → 다운로드 무결성 미검증

### 수정 범위
- Step 1: .gitignore에 예외 추가 (`!api-gateway/gradle/wrapper/gradle-wrapper.jar`)
- Step 2: gradle-wrapper.properties에 SHA256 추가
  distributionSha256Sum=f8b4f4772d302c8ff580bc40d0f56e715de69b163546944f787a61f5f4fa0c16
- Step 3: git add + chmod +x

### 실행 명령어
# Step 1
echo '!api-gateway/gradle/wrapper/gradle-wrapper.jar' >> .gitignore

# Step 2
sed -i '/^zipStorePath/a distributionSha256Sum=f8b4f4772d302c8ff580bc40d0f56e715de69b163546944f787a61f5f4fa0c16' \
  api-gateway/gradle/wrapper/gradle-wrapper.properties

# Step 3
git add .gitignore \
        api-gateway/gradle/wrapper/gradle-wrapper.jar \
        api-gateway/gradle/wrapper/gradle-wrapper.properties \
        api-gateway/gradlew
git update-index --chmod=+x api-gateway/gradlew

### 검증
cd api-gateway && ./gradlew test --no-daemon

### 인수조건
1. git ls-files api-gateway/gradle/wrapper/gradle-wrapper.jar → 추적됨
2. git ls-files -s api-gateway/gradlew → 100755
3. ./gradlew test --no-daemon → BUILD SUCCESSFUL

---
## [2026-03-09] Day 1 CVE 수정 계획 — Trivy CRITICAL 차단 해제

### CVE 목록
- CVE-2025-24813: app.jar 내 Tomcat RCE (Spring Boot 3.2.5 내장) - tomcat: Potential RCE and/or information disclosure and/or
- CVE-2026-22184: Alpine 3.23.3 패키지 취약점 - zlib: zlib: Arbitrary code execution via buffer overflow in

### 수정 범위
- Step 1: build.gradle Spring Boot 3.2.5 → 3.4.4 (Tomcat 10.1.36 포함)
          dependency-management 1.1.5 → 1.1.7
- Step 2: Dockerfile Stage 2 RUN apk add → apk upgrade --no-cache && apk add --no-cache curl
- Step 3: 로컬 trivy image --severity CRITICAL --exit-code 1 api-gateway-test 검증
- Step 4: CVE-2026-22184 업스트림 미패치 시 .trivyignore 또는 ignore-unfixed:true 결정

### 인수조건
1. 로컬 Trivy CRITICAL 스캔 exit 0
2. CI api-gateway-build Trivy 스캔 CRITICAL 0건
3. ./gradlew dependencies | grep tomcat → 10.1.36 이상

---
### Week 3 Day 2 (3/10) 세부 계획 — Feign Client 구성

**목표:** Spring Cloud OpenFeign으로 CryptoEngineClient(/dsa/sign, /dsa/verify) 구성 + Resilience4j CircuitBreaker Fallback 적용. + CryptoEngine을 호출하는 latency를 기록하라.

**Step 1 — 의존성 추가 (build.gradle)**
- spring-cloud-starter-openfeign
- spring-cloud-starter-circuitbreaker-resilience4j
- resilience4j-spring-boot3

**Step 2 — CryptoEngineClient 인터페이스 + DTO + Fallback 클래스**
- @FeignClient(name="crypto-engine", url="${crypto.engine.url}")
- sign(SignRequest) → POST /dsa/sign
- verify(VerifyRequest) → POST /dsa/verify
- Fallback: CircuitBreaker Open → 503 예외 전파

**Step 3 — application.yml Resilience4j + Feign 타임아웃 설정**
- slidingWindowSize: 10 / failureRateThreshold: 50% / waitDuration: 10s
- connectTimeout: 2000ms / readTimeout: 5000ms
- CRYPTO_ENGINE_URL 환경변수 외부 주입

**인수조건**
1. sign() 정상 호출 → crypto-engine 200 응답 전달
2. crypto-engine 중단 시 → CircuitBreaker Fallback → 503 반환
3. CRYPTO_ENGINE_URL 변경만으로 엔드포인트 교체 가능

---

## 버그픽스 계획 — CB 설정 오류 수정 (2026-03-10)

### 목표
api-gateway Circuit Breaker 인스턴스 이름 불일치 및 timeout 미정렬 수정 후 서버 정상 기동 확인

### 범위
1. CB 인스턴스 이름 수정: `instances.crypto-engine` → `configs.default` 또는 메서드 단위 인스턴스명
2. `slowCallDurationThreshold`를 readTimeout(5s) 기준으로 명시 설정
3. 서버 기동 후 actuator 엔드포인트 및 CB 동작 검증

### 인수조건
1. `curl http://localhost:8080/actuator/metrics/feign.client.requests` → 200
2. `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
3. crypto-engine 강제 다운 시 CB OPEN + Fallback 동작 확인

---

## 취약점 수정 계획 — Week 3 Day 2 리뷰 기반 (2026-03-10)

### 목표
Week 3 Day 2 리뷰의 HIGH/MEDIUM 취약점을 Day 2~4에 순서대로 해결한다.

### Step 1 [Day 2 잔여] — metrics 관리 포트 분리
- management.server.port: 8081 설정
- docker-compose healthcheck → 8081 변경

### Step 2 [Day 2~3] — CB 인스턴스명 명시적 설정
- configs.default → instances.crypto-engine (실제 CB 이름 확인 후 결정)
- @CircuitBreaker 어노테이션 인스턴스명과 일치 검증

### Step 3 [Day 3] — DTO 입력 검증 + 서비스 간 인증 설계
- SignRequest/VerifyRequest @Size 추가
- Feign RequestInterceptor API Key 헤더 주입 계획

### 인수조건
1. 8080에서 /actuator/metrics 접근 불가 (8081에서만 허용)
2. CB 설정 인스턴스명 매핑 → actuator CB 상태 정상 노출
3. 초과 크기 SignRequest → 400 반환

---

## 취약점 수정 계획 v2 — Week 3 Day 2 리뷰 기반 정합성 수정 (2026-03-10)

> 버그픽스 계획(위)은 `configs.default` 설정 완료 상태. 로직 검증은 아래 Step 2에서 수행.
> [LOW] slowCallDurationThreshold = readTimeout(5s): timeout 요청을 CB failureRate에 포함시키기 위한 의도적 설계. **기각.**

### 목표
Week 3 Day 2 리뷰 수정 제안 6건을 Day 2~4에 순서대로 해결한다.

### Step 1 [Day 2 잔여] — metrics 관리 포트 분리 + HTTP fail-fast
- `management.server.port: 8081` 설정 → 8080에서 metrics 접근 차단
- docker-compose api-gateway healthcheck URL → 8081 변경
- `@ConfigurationProperties + @Validated` 로 CRYPTO_ENGINE_URL이 `http://` 로 시작 시 애플리케이션 기동 실패 처리

### Step 2 [Day 3] — CB 인스턴스명 명시적 설정 + 로직 검증
- `configs.default` → `instances.crypto-engine` 으로 명시적 CB 인스턴스 설정
- `@CircuitBreaker(name = "crypto-engine")` 어노테이션 인스턴스명 일치 확인
- crypto-engine 강제 다운 시 CB OPEN → Fallback 503 실제 동작 검증

### Step 3 [Day 3~4] — DTO 입력 검증 + SSRF 방어 + 서비스 간 인증 설계
- `SignRequest` / `VerifyRequest` `@Size(max=65536)` 추가, Controller `@Valid` 바인딩
- SSRF 방어: docker-compose `pqc-internal` 네트워크 격리(1차) + `CryptoEngineUrlValidator` Bean으로 허용 호스트 검증(2차)
- Feign `RequestInterceptor`로 고정 API Key 헤더 주입 계획 (Day 4 Spring Security 도입 시 병행)

### 인수조건
1. `curl http://localhost:8080/actuator/metrics` → 연결 거부 / `curl http://localhost:8081/actuator/metrics` → 200
2. CB `instances.crypto-engine` 매핑 + Fallback 503 실제 동작 확인
3. 초과 크기 `SignRequest` → 400 / `http://` URL 기동 시 → 애플리케이션 시작 실패

---

## Week 3 Day 2 세부 계획 보완 항목 (2026-03-10)

> Day 2 세부 계획(L1328) 작성 시 누락된 항목 — 사후 보완 등록

**[HIGH 보완] CRYPTO_ENGINE_URL 기본값 http:// 차단 전략 미명시**
- L1346에 "환경변수 외부 주입" 만 기술됨
- 실제 application.yml 기본값: `http://crypto-engine:8000` (평문)
- 처리: `@ConfigurationProperties + @Validated` 로 http:// 시작 시 기동 실패
- → **취약점 수정 계획 v2 Step 1로 이관**

**[MEDIUM 보완] SignRequest/VerifyRequest DTO 크기 제한 미명시**
- L1338~1340 DTO 설계에 @Size 제약 없음
- 커밋 f76cacf DSA message 크기 제한 보안 수정 선례 — Feign DTO 동일 경로 재현 가능
- 처리: `@Size(max=65536)` + Controller `@Valid` 바인딩
- → **취약점 수정 계획 v2 Step 3으로 이관**

---

## test-gateway-http-fail 행(Hang) 수정 계획 (2026-03-10)

### 원인
`bootRun` = Gradle 컴파일 + Spring Boot 기동 → 30초 초과 후 kill.
검증 로직(`@AssertTrue`)은 정상. 테스트 실행 방식 문제.

### 수정 방향
- `bootRun` → `java -jar build/libs/api-gateway.jar` 로 교체
- JAR 없을 경우 `./gradlew build -x test` 먼저 실행하도록 의존성 추가
- timeout 30s → 10s 로 단축

### 인수조건
1. `make test-gateway-http-fail` 10초 내 완료
2. `http://` URL → PASS (`must use HTTPS` 확인)
3. JAR 미빌드 시 명확한 에러 메시지

---

## CI 동기화 계획 — Day 2 테스트 (2026-03-10)

### 현황
- test-gateway-unit: CI 포함 ✅
- test-gateway-http-fail: CI 미포함 ❌ → Step 1에서 추가
- test-gateway-ports: CI 미포함 ❌ + management.server.port=8081 구현 선행 필요
- test-gateway-validation: CI 미포함 ❌ + 앱 기동 필요

### Step 1 [즉시] — test-gateway-http-fail CI 추가
- api-gateway-build 잡에 스텝 추가
- ./gradlew build -x test → java -jar + http:// URL → must use HTTPS 확인

### Step 2 [management.server.port=8081 구현 후] — 포트/검증 테스트 CI 추가
- api-gateway:ci 이미지 docker run -d 기동
- test-gateway-ports: 8081 200 / 8080 거부
- test-gateway-validation: 초과 크기 POST → 400

### Step 3 — CI 잡 스텝 순서
빌드 → http-fail → docker run → ports + validation → Trivy

### 인수조건
1. CI api-gateway-build에서 test-gateway-http-fail PASS
2. management.server.port=8081 구현 후 test-gateway-ports CI PASS
3. 로컬 make test-gateway = CI 검증 항목 동일

---

### Week 3 Day 3 (3/11) 세부 계획 — ML-DSA JWT 발급

**목표:** POST /api/auth/login → ML-DSA 서명 JWT 발급 + Feign 커넥션 최적화로 latency 측정·기록

**Step 1 — POST /api/auth/login 구현**
- LoginRequest: userId, password (데모용 고정 인증 허용)
- JwtPayload 생성: userId, algorithm: ML-DSA-65, exp: now+1h
- CryptoEngineClient.sign(payload) → 서명값 수신
- LoginResponse: token (Header.Payload.Signature 구조)

**Step 2 — Feign 커넥션 최적화(미적용으로 시작, 단계적으로 적용하여 성능 개선 포트폴리오에 기입)**
- Feign 기본 HttpURLConnection → OkHttpClient 교체
  - maxIdleConnections: 5 / keepAliveDuration: 30s
- Keep-Alive: uvicorn 기본 지원 확인
- HTTP/2: uvicorn 미지원 → **기각** (근거: uvicorn HTTP/2 미지원)
- gRPC: **Day 5 이후로 이동** (proto 설계 + FastAPI 전환 비용 초과)

**Step 3 — latency 측정 + 포트폴리오 기록**
- 최적화 전/후 feign.client.requests 지표 비교
- 수치 결과를 docs/portfolio-notes.md 에 기록

**인수조건**
1. POST /api/auth/login → JWT 200 응답
2. crypto-engine 다운 시 CB Fallback → 503
3. OkHttpClient 교체 후 latency before/after 수치 기록

**잠재적 위험 (Risks)**
- ObjectMapper.writeValueAsBytes(Map.of(...)) 는 키 순서가 비결정적 → JWT payload 재현 불가. 데모 목적이므로 허용.
- loggerLevel: BASIC 설정 시 요청 URL이 로그에 노출됨. 프로덕션 전 제거 또는 NONE으로 변경 필요.

## Step 2-Day 3 — feign-micrometer 메트릭 누락 수정 계획 (2026-03-11)

### 원인
`spring-cloud-starter-openfeign` (Spring Cloud 2024.0.0) 이 `feign-micrometer` JAR을
자동 포함하지 않아 `feign.client.requests` 메트릭이 빈 배열(`[]`) 반환.
`application.yml`의 `feign.micrometer.enabled: true`는 JAR이 없으면 무효.

### 수정 대상
- 파일: `api-gateway/build.gradle`
- 변경: `implementation 'io.github.openfeign:feign-micrometer'` 1줄 추가

### 검증
docker-compose up -d --build api-gateway 후
`/actuator/metrics/feign.client.requests` 에서 `feign.client.requests` 확인

### feign 메트릭 이름 변경 확인 (2026-03-11)
Spring Cloud OpenFeign 4.x (2024.0.x) + Spring Boot 3.x Observation API 표준화로
`feign.client.requests` → `http.client.requests` 로 변경됨.
올바른 actuator 쿼리:
  GET /actuator/metrics/http.client.requests?tag=uri:/dsa/sign

---

## Step 2-Day 3 — OkHttpClient 교체 계획 (2026-03-11)

### Before 수치 (HttpURLConnection 기본값)
- Cold: 평균 200ms = PQC 알고리즘 초기화 비용
- Warm: 평균 3ms = ML-DSA-65 실제 서명 처리 성능
- 측정 방법: AuthService System.nanoTime() + http.client.requests actuator

### 질문에 대한 답
1. 커넥션 풀 설정값(`maxIdleConnections, keepAliveDuration`)은 여러 조합의 시나리오를 만들고, 부하테스트를 통해 성능을 확인할 수 있도록 코드를 작성해라
2. OkHttp 교체 후 수동 측정 코드를 제거하고 메트릭으로 일원화

### 변경 대상
1. build.gradle: `implementation 'io.github.openfeign:feign-okhttp'` 추가
2. application.yml: `spring.cloud.openfeign.okhttp.enabled: true` 추가
3. 신규 config/OkHttpFeignConfig.java: maxIdleConnections=5, keepAliveDuration=30s

### 인수조건
1. availableTags clientName → OkHttpClient 확인
2. warm latency after ≤ before
3. docs/portfolio-notes.md before/after 표 기록

## Step 2-Day 3 — OkHttpClient 활성화 세부 계획 (2026-03-11)

### 변경 대상
1. application.yml: `spring.cloud.openfeign.okhttp.enabled: true` 추가
2. 신규 config/OkHttpFeignConfig.java:
   - maxIdleConnections: 5
   - keepAliveDuration: 30s
   - connectTimeout: 2s / readTimeout: 5s (기존 feign timeout 동일)

### Before 수치 (HttpURLConnection)
- cold: 190ms / warm: 2ms
- 측정: AUTH-LATENCY 로그 + http.client.requests actuator

### 검증 순서
docker-compose up -d --build api-gateway
→ 100회 로그인
→ availableTags clientName OkHttp 확인
→ AUTH-LATENCY after 평균 측정
→ docs/portfolio-notes.md before/after 표 기록

### 측정 횟수 결정 (2026-03-11)
- 채택: 100회 반복 (포트폴리오 신뢰도)
- 기록 지표: cold 1회, warm p50/p95/p99
- AUTH-LATENCY 로그 100줄 → awk 평균 계산
  docker-compose logs api-gateway | grep AUTH-LATENCY \
    | awk -F'latency=' '{sum+=$2; count++} END {print "avg="sum/count"ms, n="count}'
EOF

### 인수조건
1. clientName → OkHttp 클라이언트 확인
2. warm latency after ≤ 2ms
3. portfolio-notes.md before/after 기록 완료

---

## Step 2-Day 3 — gunicorn workers 튜닝 계획 (2026-03-11)

### 목적
동시 요청 처리량 병목이 crypto-engine worker 수임을 실험으로 검증.
workers=1/2/4 조건에서 동시 요청 latency 비교 → 최적값 결정.

### 질문에 대한 답변
- 리소스 제약: .env에 CRYPTO_ENGINE_MEM_LIMIT=512m — workers=4 시 메모리 초과 가능성 있음. 실험 전 MEM_LIMIT 조정할 것인가? => 당장은 조정하지 않되, workers 수에 따른 메모리 사용량을 반드시 측정하여 기록해야한다.
- 동시 요청 수: workers=N 실험에서 동시 요청 몇 개로 측정할 것인가? => workers × 2 배수 권장: 1,2,4,8

### 제약사항
- --preload 반드시 유지: worker별 독립 키쌍 생성 방지
- CRYPTO_ENGINE_MEM_LIMIT=512m: workers 증가 시 모니터링 필요

### 변경 대상
1. crypto-engine/Dockerfile CMD: `-w 2` → `-w ${GUNICORN_WORKERS:-2}`
2. config/crypto-engine.env: `GUNICORN_WORKERS=2` 추가

### 측정 방법
workers=N 설정 후 동시 N*2개 요청:
  for i in $(seq N); do curl ... > /dev/null & done; wait
  docker-compose logs api-gateway | grep AUTH-LATENCY → avg/p95

### 인수조건
1. GUNICORN_WORKERS 환경변수로 workers 수 제어 가능
2. workers=4 > workers=1 동시 처리 latency 개선 수치 확보
3. --preload 유지로 전 설정에서 JWT 정상 반환

---

## --preload 제거 메모리 실험 계획 (2026-03-11)

**목표:** gunicorn --preload 제거 전/후 crypto-engine RSS 메모리 증가량 측정·비교

**질문에 대한 답변:**
- worker 수 기준: 실험 시 GUNICORN_WORKERS 환경변수 값은 내가 직접 조정하겠다.
- 측정 도구: docker stats (컨테이너 전체 RSS) 로 충분
- DSA 키쌍 불일치 허용 여부: --preload 제거 시 worker별 keypair가 달라져 /dsa/verify가 실패합니다. 키쌍을 외부(파일/환경변수)로 분리하는 방향을 이용하라.

**Step 1 — 베이스라인 (with --preload)**
- GUNICORN_WORKERS=4 고정, 컨테이너 기동
- docker stats --no-stream crypto-engine
- docker exec crypto-engine ps aux (PID별 RSS 합산)

**Step 2 — --preload 제거 후 측정**
- Dockerfile CMD에서 --preload 제거 후 재빌드·재기동
- 동일 측정 반복 → worker별 독립 liboqs 로드 + DSA 키쌍 생성 메모리 확인

**Step 3 — 결과 기록**
- 전/후 RSS 수치를 docs/portfolio-notes.md 테이블로 기록
- 증가량 = worker별 추가 메모리 × worker 수

**인수조건**
1. 두 조건의 docker stats RSS 수치 기록 완료
2. worker 수에 비례한 메모리 증가 패턴 확인
3. docs/portfolio-notes.md before/after 테이블 저장

---

## Step 2-Day 3 — CI 반영 계획 (2026-03-11)

**목표:** Day 3 인수조건(JWT 200 / CB Fallback 503)을 CI로 검증

**질문에 대한 답변:**
1. CB Fallback 503 검증 방식: 통합 잡에서 crypto-engine을 실제로 죽인 후 503을 확인할까요, 아니면 단위 테스트(Mock)로 CB Fallback을 커버할까요? (통합 방식은 잡 복잡도가 올라감) => 할 수 있다면 테스트 코드 내에서 WireMock을 사용해 503 응답을 가상화하세요. 이는 외부 의존성 없이도 CB의 Circuit Open -> Fallback 실행 로직을 가장 빠르고 정확하게 검증하는 방법입니다.
2. api-gateway 빌드 방식: 통합 잡에서 api-gateway도 Docker 이미지로 기동해야 하나요, 아니면 java -jar 기동으로 충분한가요? => 효율성 측면에서는 java -jar 기동이 압도적으로 유리
3. latency 수치(인수조건 3): portfolio-notes.md에 이미 기록됐으니 CI 자동 검증 범위에서 제외해도 되나요? => 제외해라.

**Step 1 — AuthControllerTest 단위 테스트**
- @WebMvcTest + @MockBean CryptoEngineClient
- 정상 로그인 → 200 + token 필드 존재
- 잘못된 인증 → 401
- sign() throws → 503 (CB Fallback 시뮬레이션)

**Step 2 — auth-integration-test CI job 추가**
- needs: [build-and-trivy]
- crypto-engine + api-gateway(dev) 동시 기동
- POST /api/auth/login curl → 200 + JWT 구조("." 2개) 확인

**Step 3 — 인수조건 매핑**
- 인수조건 1 (JWT 200): auth-integration-test
- 인수조건 2 (CB 503): AuthControllerTest 단위 테스트
- 인수조건 3 (latency): portfolio-notes.md 수동 기록, CI 제외

**인수조건**
1. AuthControllerTest 3케이스 ./gradlew test PASS
2. auth-integration-test POST /api/auth/login → JWT 200 PASS
3. GitHub Actions 모든 잡 green

---

## Day 3 보안 위험 수정 계획 (2026-03-11 리뷰 기반)

**목표:** review.md Day 3 타당 위험 6건을 복잡도 기준으로 Day 3 패치 / Day 4 통합으로 분산 반영

### Day 3 즉시 패치 (3건)

**Step 1 — 위험 3: algorithm_factory.py 키 크기 어설션**
- b64decode 후 len(_DSA_SECRET_KEY) == 4032 어설션 추가
- 불일치 시 ValueError 기동 중단 (fail-fast)
- 변경 파일: crypto-engine/app/algorithm_factory.py 1줄

**Step 2 — 위험 4: gen-dsa-keypair.sh stdout → 파일 출력**
- 키 생성 결과를 config/crypto-engine.env 직접 write
- chmod 600 자동 적용, git 커밋 금지 경고 추가

**Step 3 — 위험 6: AuthService 로깅 마스킹**
- log.error("JWT 생성 실패: {}", e.getMessage()) → log.error("JWT 생성 실패")
- 변경 파일: AuthService.java 1줄

### Day 4 통합 (3건)

**Step 4 — 위험 1+2: 인증 정보 환경변수 분리 + constant-time 비교**
- DEMO_USER/DEMO_PASSWORD 환경변수 주입, 소스코드 제거
- String.equals() → MessageDigest.isEqual() 교체

**Step 5 — 위험 5: Rate Limit (Spring MVC 인터셉터)**
- ConcurrentHashMap<String, AtomicInteger> 인메모리 IP 기반 제한
- 의존성 추가 없음

**인수조건**
1. Day 3 패치 3건 커밋 후 CI green 유지
2. Day 4 후 git grep "demo123" 결과 없음
3. 잘못된 키 주입 시 컨테이너 기동 중단 테스트 PASS

**목표:** /dsa/** 엔드포인트를 ML-DSA JWT로 보호하는 검증 필터/인증 레이어를 추가하고, 하드코딩 인증정보·타이밍공격·Rate Limit 3건 보안 이슈를 해결한다.

### 질문에 대한 답
1. JWT 검증 방식: '캐싱'을 결합한 엄격한 검증 (Hybrid Approach). 매번 crypto-engine을 호출하는 것이 부담스럽다면, 검증 결과의 '상태'를 로컬이나 분산 캐시(Redis)에 저장.
  - 1단계 (Local): 필터에서 exp 및 기본 구조 체크 (비용 낮음).
  - 2단계 (Cache): 해당 JWT의 Hash값을 키로 하여 캐시에 "검증 완료" 상태가 있는지 확인.
  - 3단계 (Crypto-engine): 캐시에 없다면 crypto-engine 호출하여 PQC 서명 검증. 결과가 성공이면 캐시에 저장 (TTL은 JWT의 잔여 만료 시간으로 설정).
2. Rate Limit 단위: IP당 '초(second)' 기준 + '슬라이딩 윈도우(Sliding Window)' 방식
3. 보호 대상 엔드포인트 범위: /api/auth/login을 제외한 전체 엔드포인트에 적용. 보안 프로젝트로서의 완성도를 생각한다면, 특정 경로(dsa/**)만 보호하기보다 서비스 전체의 신뢰성을 확보.

### Step 1 — 위험 1+2: 인증 정보 환경변수 분리 + constant-time 비교
- DEMO_USER/DEMO_PASSWORD → application.yml ${ENV_VAR} 패턴으로 분리
- String.equals() → MessageDigest.isEqual() 교체
- 변경 파일: AuthService.java + application.yml

### Step 2 — JWT 검증 필터 + 인증 레이어
- 신규: JwtAuthFilter.java (OncePerRequestFilter 또는 HandlerInterceptor)
- 신규: WebMvcConfig.java (필터/인터셉터 등록, /dsa/** 보호)
- 검증: Bearer 토큰 파싱 → 3파트 분리 → exp 만료 확인 → (서명 검증 방식 결정 후)
- 실패 시 401 반환

### Step 3 — 위험 5: Rate Limit (Spring MVC 인터셉터)
- 신규: RateLimitInterceptor.java
- ConcurrentHashMap<String, AtomicInteger> 인메모리 IP 기반 제한
- 임계 초과 시 429 반환, WebMvcConfig.java에 통합 등록
- 의존성 추가 없음

**인수조건**
1. /dsa/sign JWT 없이 401, 유효 JWT 200 확인
2. git grep "demo123" 결과 없음
3. 임계치 초과 요청 시 429 응답 확인

---

## Day 4 — 보안 위험 추가 수정 계획 (2026-03-12 리뷰 기반)

### 위험 1: JwtKeyCache 인메모리 → 현재 범위 외, 기술 부채 기록
- docs/portfolio-notes.md에 스케일아웃 시 Redis 교체 필요 명시

### 위험 2: ipTimestamps 무한 성장 → 주기 정리 스케줄러 추가
- Step A: RateLimitInterceptor 내 ScheduledExecutorService 60초 주기 정리
- Step B: 빈 Deque 또는 만료 IP 키 제거 로직
- Step C: @PreDestroy executor 종료
- 인수조건: 정리 후 비활성 IP 키 제거 단위 테스트 PASS

---

## Day 4 — 리뷰 후속 수정 계획 (2026-03-12)

**목표:** verifiedCache O(n) 제거·JwtKeyCache 주기 정리를 스케줄러 분리, JWT 오류 메시지 단일화, DEMO_USER fail-fast 적용, 테스트 자동화 완성

### 질문에 대한 답
1. JwtKeyCache의 cleanup과 단일 스케줄러로 통합. 별도의 컴포넌트를 두어, 여기서 두 캐시의 cleanup() 메서드를 순차적으로 호출. 이렇게 하면 파일 수는 절약하면서도, 각 클래스는 자기 자신의 캐시를 비우는 로직만 가지므로 책임 분리(SRP)도 달성.
2. JWT 오류 메시지 단일화 범위: 응답 Body의 message 필드를 "Unauthorized"로 단일화하는 방식.
3. DEMO_USER 기본값 제거 시, application-test.yml(또는 test @TestPropertySource)에서 DEMO_USER를 주입하고 있는지 확인이 필요 — 구현 전, 반드시 확인.

### Step 1 — verifiedCache O(n) removeIf 제거 + 스케줄러 분리 / JwtKeyCache cleanup 추가
- JwtAuthInterceptor: removeIf() 제거, ScheduledExecutorService 60초 주기 cleanup + @PreDestroy
- JwtKeyCache: cleanup() 메서드 + 60초 스케줄러 추가

### Step 2 — JWT 오류 메시지 단일화 + DEMO_USER fail-fast
- JwtAuthInterceptor: 오류 메시지 7종 → "Unauthorized" 단일화
- application.yml: ${DEMO_USER:demo} → ${DEMO_USER} (기본값 제거)
- 테스트 yml에 DEMO_USER=demo 주입 확인

### Step 3 — 테스트 자동화
- 신규: JwtAuthInterceptorTest (Bearer 없음 401, 캐시 히트 200)
- 기존: RateLimitInterceptorTest에 preHandle 429 케이스 추가

**인수조건**
1. git grep "removeIf" 0건
2. JwtAuthInterceptorTest 2건 + preHandle 429 테스트 PASS
3. git grep "DEMO_USER:demo" 0건 + CI green

---

## Day 4 — CI 반영 계획 (2026-03-12)

**목표:** DEMO_USER fail-fast로 파손되는 기존 CI 잡 수정 + JWT 인증 레이어 CI 검증 추가

### 질문에 대한 답
1. JwtAuthInterceptorTest 단위 테스트 파일이 아직 없는데, CI 잡 추가 전에 단위 테스트 파일을 먼저 작성해야 할까요, 아니면 CI 잡과 동시에 계획할까요? => JwtAuthInterceptorTest.java는 이미 존재.
2. JWT 검증 통합 CI 잡의 위치: 기존 auth-integration-test 잡에 스텝 추가를 권장.
3. api-gateway-build 잡(line 368)의 docker run 환경변수 주입 방식: GitHub Actions env: 블록(잡 레벨)으로 관리를 권장. 단, 지금 당장 CI가 깨질수 있는 두 곳() 존재:
  - api-gateway-build L370 docker run	-e DEMO_PASSWORD 없음	${DEMO_PASSWORD} 기본값 없어서 컨테이너 기동 실패
  - auth-integration-test L499 curl	"password":"demo123" 하드코딩	환경변수 분리 후에도 평문 잔존

### Step 1 — 기존 잡 파손 수정
- api-gateway-build: docker run에 -e DEMO_USER=demo -e DEMO_PASSWORD=demo123 추가
- auth-integration-test: java -jar에 -Dauth.demo.user=demo -Dauth.demo.password=demo123 추가
- 변경 파일: .github/workflows/ci.yml (2 스텝)

### Step 2 — JwtAuthInterceptorTest 단위 테스트 신규 작성
- 케이스 1: Bearer 헤더 없음 → false + 401
- 케이스 2: verifiedCache 캐시 히트 → true (crypto-engine Mock 없음)
- ./gradlew test → api-gateway-build 잡 자동 커버

### Step 3 — JWT 검증 통합 CI 스텝 추가 (auth-integration-test 잡 내)
- 스텝 A: 기존 $TOKEN으로 /dsa/sign Bearer 요청 → 200 확인
- 스텝 B: 토큰 없이 /dsa/sign 요청 → 401 확인

**인수조건**
1. api-gateway-build + auth-integration-test CI green (파손 해소)
2. JwtAuthInterceptorTest 2케이스 PASS
3. /dsa/sign 401/200 CI 스텝 PASS

---

## Day 5 — Week 3 통합 + CI 구체화 계획 (2026-03-13)

**목표:** JwtAuthInterceptorTest 단위 테스트 작성 + api-gateway 포함 전체 스택 docker-compose CI 잡 추가

※ Day 4에서 선제 완료된 항목: api-gateway Trivy CI, JWT 발급·검증 왕복 CI, 미인증 401 CI, 포트 분리

### 질문에 대한 답
1. 전체 스택 CI 잡 위치: stack-integration-test 잡 신설을 권장(관심사 분리, 원인 격리)
2. Day 5 후 Day 6(KEM 재설계)로 넘어가는 기준: 
  - CI 전체 green
  - stack-integration-test에서 로그인→토큰→보호 엔드포인트 E2E 플로우 pass
  - DEMO_PASSWORD 환경변수 기본값 없는 상태로 CI pass

### Step 1 — JwtAuthInterceptorTest.java 신규 작성
- 케이스 1: Authorization 헤더 없음 → false + 401
- 케이스 2: verifiedCache 캐시 히트 → true (crypto-engine 미호출)
- @AfterEach shutdown() 스케줄러 누수 방지

### Step 2 — stack-integration-test CI 잡 신설
- needs: [build-and-trivy, api-gateway-build]
- docker compose up crypto-engine + api-gateway --wait
- 8081/actuator/health healthy + POST /api/auth/login 200 확인

### Step 3 — Week 3 인수조건 최종 점검
- AC1 JWT 발급 200 / AC2 미인증 401 / AC3 전체 스택 healthy

**인수조건**
1. JwtAuthInterceptorTest 2케이스 PASS → api-gateway-build CI green
2. stack-integration-test 잡 api-gateway healthy 확인
3. CI 전체 잡 green → Day 6 진입

---
## Day 6 수정 계획 (2026-03-13)

### Plan A — E2E Bearer 플로우 완성 (높음)
- ci.yml L599 TOKEN export 추가
- Bearer→200 / 미인증→401 E2E 스텝 신설

### Plan B — docker inspect 공백 오진 방어 (중간)
- ps -q 결과 -z 검사 후 exit 1 처리

### Plan C — JwtAuthInterceptorTest 보완 (낮음)
- 만료/잘못된구조 케이스 추가
- verifiedCache 내부 접근 캡슐화

---
## [2026-03-16] Day 6 세부 계획 확정판 — KEM API 재설계

### 목표
/kem/keygen → secret_key 외부 반환 취약점 제거. 서버사이드 키 보관으로 전면 재설계.

### 질문에 대한 답
1. crypto-engine DB 연결 방식 -> Day 6에서는 기능 구현과 안정성이 우선입니다. psycopg2를 사용하여 기존 구조와의 일관성을 유지하세요. 성능 최적화가 필요한 시점에 비동기로 전환해도 늦지 않습니다.
2. secret_key 보호 수준: 평문 저장 vs AES 래핑 -> 서버사이드 AES-256-GCM 래핑 포함. PQC(양자 내성 암호) 프로젝트의 본질은 '보안'입니다. DB에 개인키를 평문(Base64)으로 저장하는 것은 프로젝트의 상징성을 저해할 수 있습니다.
전략: env 파일에 KEM_WRAP_KEY를 추가하고, 그것을 활용한 간단한 래핑 로직을 추가하세요. 이는 Day 6 범위 내에서도 충분히 구현 가능하며, "Security by Design" 원칙을 지키는 모습을 보여줄 수 있습니다.
3. /kem/encrypt 내 대칭 암호화 포함 여부 -> Day 6은 '키 관리 재설계(Infrastructure)'에 집중. 이미 DB 연동과 인터페이스 변경(Breaking Change)이라는 큰 과업이 있습니다. 여기에 대칭 암호화 흐름까지 무리하게 포함하면 디버깅 범위가 너무 넓어집니다.
Day 6: /kem/init 및 DB 스키마 완성, /kem/encrypt는 Key ID를 받아 Shared Secret을 생성하고 응답하는 단계까지만 구현.
Day 7: 생성된 Shared Secret을 활용한 실제 데이터 암호화(AES-256-GCM) 통합.

### 잠재적 위험 대응 가이드
1. Breaking Change -> test_edge.py를 즉시 업데이트하고, 가능하다면 엔드포인트 버저닝을 통해 하위 호환성을 일시적으로 유지하세요.
2. DB 연결 실패 -> crypto-engine 시작 시점에 DB 연결을 체크하는 로직을 넣되, docker-compose에서 depends_on: db: condition: service_healthy 설정을 강화하세요.
3. secret_key DB 저장으로 인한 노출 -> 래핑 처리를 하더라도 DB 백업 파일에 대한 접근 제어 정책을 portfolio-notes에 명시하여 운영 보안 측면을 강조하세요.

### 범위
- Step 1: crypto-engine DB 연결(psycopg2) + POST /kem/init 신규 (key_metadata INSERT → key_id 반환)
- Step 2: POST /kem/encrypt 재설계 (key_id+plaintext → DB public_key 조회 → encap+AES-256-GCM → ciphertext)
- Step 3: api-gateway KemController + Feign 메서드 추가 (POST /api/kem/init, POST /api/kem/encrypt)

### 인수조건
1. POST /api/kem/init 응답에 secret_key 없음, key_id만 반환
2. POST /api/kem/encrypt 응답에 shared_secret 없음, ciphertext만 반환
3. POST /kem/keygen → 410 Gone

---
## [2026-03-16] Day 6 보안취약점/CI 수정 계획

### 질문에 대한 답
- Q1. integration-test 잡의 DB 연동 방식 -> 기존 integration-test 잡에 services.postgres 추가 (확장 방식)
- Q2. http_test.py KEM 섹션 재설계 범위 -> init → encrypt → decrypt (410 Gone 확인) 전체 흐름 재구성
- Q3. CI용 KEM_WRAP_KEY 주입 방식 -> config/crypto-engine.env의 개발용 고정값 하드코딩

### 목표
Day 6 리뷰 심각 3건(decryption oracle 잔존, KEM_WRAP_KEY CI 미주입, 003_kem_keys.sql CI 미적용)
+ 중간 1건(테스트 스크립트 구버전) 수정 → CI green 확보

### 범위
- Step 1: /kem/decrypt → 410 임시 차단 (decryption oracle 보안 제거)
- Step 2: CI integration-test 잡에 postgres 서비스 + 003 SQL 적용 + KEM_WRAP_KEY/DB_* 주입
- Step 3: db-schema-test 테이블(4→5)/인덱스(6→7) 기대값 수정 + 테스트 스크립트 Day 6 인터페이스 교체

### 인수조건
1. POST /kem/decrypt → 410 Gone
2. CI integration-test: /kem/init + /kem/encrypt 왕복 exit 0
3. CI db-schema-test: 테이블 5개 + 인덱스 7개 확인 통과

---
## [2026-03-17] Day 7 세부 계획 확정판 — decryption oracle 수정 + /api/encrypt 전 구간

### 현재 흐름 (불완전):
client → POST /api/kem/init       → key_id             ✅ Day 6 완료
client → POST /api/kem/encrypt    → kem_ciphertext만   ⚠️ plaintext 암호화 없음
client → POST /api/kem/decrypt    → 410                ⚠️ Day 6 임시 차단
client → POST /api/encrypt        → 미구현              ❌ AC1 미달

### Day 7 목표 흐름:
client → POST /api/encrypt {plaintext}
  → api-gw Feign: /kem/init → key_id
  → api-gw Feign: /kem/encrypt {key_id, plaintext}
  → crypto-engine: KEM encap → AES-256-GCM(plaintext) → CBOM INSERT
  → return {key_id, kem_ciphertext, aes_ciphertext, aes_iv}

client → POST /api/kem/decrypt {key_id, kem_ciphertext, aes_ciphertext, aes_iv}
  → api-gw Feign: /kem/decrypt
  → crypto-engine: DB unwrap → decap → AES decrypt → CBOM INSERT
  → return {plaintext}

### 목표
/kem/encrypt 하이브리드 확장 + /kem/decrypt 서버사이드 구현 + CBOM 자동 로깅 + /api/encrypt 전 구간 완성

### 질문에 대한 답
1. Q1. /kem/encrypt Breaking Change 수용 여부 -> 하나의 엔드포인트로 통합 (Breaking Change 감수). 프로젝트 초기 단계에서 기능이 유사한 /kem/wrap과 /kem/encrypt를 분리하는 것은 향후 유지보수 비용과 API 복잡도만 높이는 결과가 됩니다.
2. CBOM 로깅 실패 정책 -> Transactional (로깅 실패 시 500 에러). 현재 목적이 **'VACUUM 정책 실부하 검증'**이라면, 로깅 누락은 실험 데이터의 오염을 의미합니다. 데이터가 빠진 상태에서의 테스트는 결과의 신뢰성을 떨어뜨립니다.
3. AES 키 도출 방식 -> HKDF (RFC 5869) 명시적 사용. 단순히 32바이트가 일치한다고 그대로 사용하는 것은 'KDF(Key Derivation Function)'의 설계 목적을 간과하는 것입니다. "Shared Secret을 직접 사용하지 않고 HKDF를 통해 도출했다"는 설명은 암호 민첩성(Crypto Agility)과 도메인 분리(Domain Separation) 개념을 정확히 이해하고 있음을 증명합니다. salt나 info 파라미터를 활용해 향후 다른 알고리즘으로 변경되더라도 동일한 프레임워크를 유지할 수 있는 구조를 만드세요.

### 범위
- Step 1: crypto-engine /kem/encrypt 확장(KEM+AES-256-GCM) + /kem/decrypt 서버사이드(unwrap→decap→AES decrypt)
- Step 2: CBOM 자동 로깅 (encrypt/decrypt 이벤트 → cbom_assets INSERT, fire-and-forget)
- Step 3: api-gateway EncryptController + KemController 확장 + Feign 메서드 추가 + 테스트 스크립트 재수정

### 인수조건
1. POST /api/encrypt → {key_id, kem_ciphertext, aes_ciphertext, aes_iv} + cbom_assets 1건
2. POST /api/kem/decrypt → {plaintext} 원문 일치 + cbom_assets 1건
3. CI integration-test: encrypt→decrypt 왕복 + plaintext 일치 자동 검증 통과 (Phase 2 AC1)

---

## [2026-03-17] Day 7 후속 구현 계획 — Oracle 방어 + oqs 안정화 + 테스트 보강

**목표:** decryption oracle 취약점 제거 + oqs 버전 불안정성 해소 + /api/encrypt 전 구간 테스트 공백 보강

### 사전 확인 필요
- oqs.KeyEncapsulation keyword args 변경 대상이 crypto-engine 내 몇 개 파일에 걸쳐 있는지 확인이 필요 — kem_service.py 단일 파일인지, decrypt_service.py 등 분리되어 있는지?
- db_cursor() 내 HTTPException 이동 시, 현재 코드의 트랜잭션 롤백 정책(커밋 여부)에 영향을 주지 않음을 확인했는가?

### 질문에 대한 답
- 먼저 EncryptControllerTest에서 Mock을 사용해 API 규격을 빠르게 확정. 그 후, PQC 엔진과의 연동이 중요한 만큼 별도의 통합 테스트를 작성하여 실제 암호화 값이 예상대로 돌아오는지(예: 특정 접두사 확인, 복호화 가능 여부 등)를 검증

### Step 1 — oqs 버전 고정 + keyword args
- requirements/base.txt liboqs-python 버전 핀 고정
- oqs.KeyEncapsulation positional → keyword args 변경

### Step 2 — Decryption Oracle 방어
- KEM 실패 / AES 실패 단일 에러 메시지 통일
- db_cursor() 블록 외부로 HTTPException 이동

### Step 3 — 테스트 공백 보강
- EncryptControllerTest.java 신규 작성 (Mock 유닛)
- crypto-engine/tests/ pytest 생성 (_derive_aes_key, _cbom_insert)
- CI 엣지케이스 스텝 추가 (aes_iv 오류, key_id 404, unwrap 실패)

**인수조건:**
1. liboqs-python 버전 고정 + keyword args CI 재현 가능
2. /kem/decrypt KEM/AES 실패 시 동일 메시지 반환
3. EncryptControllerTest + crypto-engine pytest PASS

---

## [2026-03-17] Day 7 후속 구현 계획 — liboqs 버전 동기화 + kem_encrypt 패턴 정리

**목표:** liboqs-python ↔ liboqs C 버전 불일치 해소 + kem_encrypt 코드 패턴 일관성 확보

### 사전 확인 필요:
1. Dockerfile에서 liboqs C를 소스 빌드하는 방식인지, 아니면 apt/pip 등으로 설치하는 방식인지? — 동기화 방향(C 버전을 내리거나 python 버전을 올리거나)이 달라짐.
2. liboqs-python 0.15.0이 PyPI에 배포되어 있다면 python 버전을 올리는 방향이 자연스럽지만, 미배포 상태라면 Dockerfile C 버전을 0.14.x로 내려야 함 — 현재 PyPI 배포 여부를 확인.
3. kem_encrypt의 db_cursor() 블록 범위가 kem_decrypt와 동일한 구조인지, 아니면 트랜잭션 단위가 다른지?

### Step 1 — [HIGH] liboqs 버전 동기화
- PyPI liboqs-python 0.15.0 배포 여부 확인 후 방향 결정
  - 배포됨: requirements/base.txt 0.15.0 업데이트
  - 미배포: Dockerfile liboqs C 버전 다운그레이드

### Step 2 — [MEDIUM] kem_encrypt HTTPException 패턴 분리
- db_cursor() 블록 외부로 HTTPException 이동
- kem_decrypt와 동일한 3단계 분리 구조 적용

### Step 3 — [LOW] CI ENCRYPT_RESP JSON 환경변수 개선
- jq -c 단일라인 압축 또는 toJSON 헬퍼 사용

**인수조건:**
1. liboqs 버전 쌍 일치 + docker build + pytest PASS
2. kem_encrypt HTTPException이 db_cursor 외부에 위치
3. CI ENCRYPT_RESP JSON 파싱 오류 없음

---

## Day 8 — CI 오류 수정 계획 (2026-03-17)

### 질문에 대한 답
1. config/crypto-engine.env.example에 KEM_WRAP_KEY 플레이스홀더(changeme 등)를 추가하는 것이 적절한가? 아니면 CI job 스텝에서만 주입하는 방식이 맞는가? (보안 정책 확인) -> .env.example에 플레이스홀더를 포함. 어떤 환경변수가 필요한지 명시하는 가이드.
2. test_edge.py [4/5]의 의도가 "key_id 없음 → 404"라면, valid한 base64 plaintext를 같이 전송하는 것으로 확정해도 되는가? -> valid한 base64 plaintext를 전송하는 것으로 확정하는 것이 적절.
3. DB_HOST=db(compose 서비스명)를 crypto-engine.env.example에 기본값으로 넣는 것이 개발자 UX상 적합한가? -> 적합.

### 오류 1: test_edge.py [4/5] 422 vs 404
- 수정: scripts/test_edge.py:55 — {"key_id":999999} → {"key_id":999999,"plaintext":"dGVzdA=="}

### 오류 2: stack-integration-test /api/encrypt exit 22
- 수정 A: config/crypto-engine.env.example — DB 변수 + KEM_WRAP_KEY 플레이스홀더 추가
- 수정 B: ci.yml stack-integration-test 스텝 — KEM_WRAP_KEY 및 DB_PASSWORD CI 주입 추가

### 잠재적 위험
- inspect-limits / compose-integration 잡도 crypto-engine.env.example 복사만 하고 KEM_WRAP_KEY 주입이 없음. 해당 잡들이 KEM 연산을 테스트하지 않는다면 무관하나, 암호화 API 호출 시 같은 오류 재현 가능 — Day 8 범위 외이므로 별도 확인 필요.

---

## Day 8 — CI 오류 3 수정 계획 (2026-03-17)

### 질문에 대한 답
1. /kem/encrypt 응답에 key_id를 추가하는 API 변경을 원하는가? 아니면 CI 스크립트만 수정하는가? (API 변경은 breaking change 여부 검토 필요) -> CI 스크립트만 수정
2. 이 스텝의 수정 범위를 "CI yml 1줄 수정"으로 한정할 것인가? -> 1줄 수정으로 한정

### [Day7-AC2] aes_iv 변조 테스트 KeyError: 'key_id'
- 원인: ci.yml:221 에서 enc['key_id'] 접근 — KemEncryptResponse에 key_id 없음
- 수정: 'key_id': enc['key_id'] → 'key_id': $KEY_ID (셸 변수 치환)

---

## [2026-03-17] Day 8 세부 계획 — Algorithm Agility 인터페이스

### 목표
KEM_ALGORITHM_ID / DSA_ALGORITHM_ID 환경변수 교체만으로 재빌드 없이 알고리즘 전환 가능한 AlgorithmStrategy 구현 + CBOM 전환 이벤트 기록 → Phase 2 인수조건 3 달성

### 선행 조건 (Step 0)
- liboqs-python ↔ liboqs C 버전 동기화 완료 후 진입 (Day 7 후속 HIGH)
  - 0.15.0 PyPI 배포 여부 확인 → requirements 또는 Dockerfile 수정
  - docker build + pytest PASS 확인

### 질문에 대한 답
1. AlgorithmStrategy는 crypto-engine(Python) 단일 구현으로 충분한지, 아니면 api-gateway(Java)에도 알고리즘 식별자를 전달하는 계층이 필요한지? -> 두 계층 모두 필요.  API Gateway (Java) - 알고리즘 '식별 및 정책 제어' 계층. Crypto-Engine (Python) - 알고리즘 '실행 및 연산' 계층.
2. CBOM 기록 시점 및 방식 -> 하이브리드(Hybrid) 방식. 모든 API 호출마다 DB나 중앙 저장소의 cbom_assets를 업데이트하는 것은 성능 저하(I/O Overhead)의 주범.
Startup (1회): 컨테이너가 뜰 때 해당 환경에서 사용 가능한(Capable) 알고리즘 리스트와 기본 설정을 기록 (Static CBOM).
Runtime (이벤트 발생 시): 알고리즘 설정이 변경되거나, 특정 주기로 실제 사용된 알고리즘 통계를 기록.
API 호출 시: cbom_assets 테이블에 직접 쓰기보다는, 애플리케이션 로그(ELK/Splunk 등)에 알고리즘 식별자를 남김. 추후 이 로그를 수집하여 '실제 활성 CBOM' 리포트를 생성하는 것이 시스템 부하를 줄임.
3. 알고리즘 범위 및 확장성 (RiskClassifier 연동) -> 확장 가능한 구조(Crypto-Agility)는 필수. 현재 NIST 표준인 ML-KEM/ML-DSA를 고정값으로 두는 것은 위험. PQC 분야는 표준화가 진행 중이며, 향후 취약점이 발견될 경우 신속히 교체해야 하기 때문. 알고리즘 타입을 Enum이나 데이터베이스로 관리. AlgorithmRegistry 같은 컴포넌트를 두어 알고리즘명, 보안 강도(Level 1, 3, 5), 라이브러리 경로 등을 관리.

추후 RiskClassifier가 "현재 ML-KEM-512는 위험함"이라는 신호를 주면, 시스템이 실시간으로 이를 차단하거나 상위 버전으로 Fallback 시킬 수 있는 토대를 마련해야 합니다.

### 범위
- Step 1: AlgorithmStrategy 인터페이스 (crypto-engine)
  - algorithm_strategy.py 신규: 환경변수 로드 + 화이트리스트 검증
  - kem_service.py / dsa_service.py 하드코딩 → strategy 주입
  - api-gateway application.yml / .env.example 키 추가
- Step 2: CBOM 전환 이벤트 기록
  - startup 시 cbom_assets에 algorithm_transition 이벤트 INSERT
  - risk_level 하드코딩 제거 구조 준비
- Step 3: CI 검증
  - ML-KEM-512 / ML-KEM-1024 환경변수 치환 → 재빌드 없이 /api/encrypt 정상 확인
  - INVALID-ALG 주입 → startup 오류(exit code ≠ 0) 확인

### 인수조건
1. 환경변수만 변경 + docker compose up → 알고리즘 전환 + API 정상 동작
2. 서버 기동 시 cbom_assets에 전환 이벤트 INSERT 확인
3. 화이트리스트 외 알고리즘 ID → startup 단계 명시적 오류 발생


---

## Day 8 수정 계획 (2026-03-18) — 리뷰 미완 항목 해소

### 목표
Day 8 리뷰의 미완 인수조건(AC1·AC2), 위험요소 3건, 테스트 공백 4건을 해소하여 Day 9 진입 기준을 충족한다.

### 질문에 대한 답
1. api-gateway `@ConfigurationProperties` 구현 시, 알고리즘 ID를 crypto-engine 요청에 헤더로 전달할지, 요청 파라미터(body)로 전달할지? => HTTP 헤더(Header)로 전달하는 방식을 권장. API Gateway는 요청을 중계하면서 필요한 인증 정보나 처리 옵션을 헤더에 주입하는 것이 일반적인 아키텍처 패턴
2. `/health` 응답에 `cbom_inserted` 필드 추가 시, 인증 없이 외부 접근 가능한 상태인데 노출 허용 여부? => 보안 관점에서는 외부 노출을 제한하거나 필드를 제거하는 것이 안전
3. `conftest.py` fixture 방식으로 sys.exit 함정 방어 시, 기존 test_algorithm_strategy.py 7개 테스트 구조 변경 허용 여부? => 변경을 적극 권장. 테스트의 안정성과 독립성을 확보하는 것이 우선

### 범위

#### Step 1 — api-gateway Java binding 구현 [HIGH, 블로커]
- `CryptoAlgorithmProperties.java` 신규: `@ConfigurationProperties(prefix = "crypto.algorithm")` 으로 kemId/dsaId 바인딩
- EncryptController 또는 Feign Request에서 알고리즘 ID를 crypto-engine 호출에 포함
- application.yml 기존 키와 연결 검증

#### Step 2 — CBOM INSERT 가시성 확보 [MEDIUM + AC2]
- main.py `except` 블록에 `exc_info=True` 추가
- `/health` 응답에 `cbom_inserted: bool` 필드 추가 (startup 플래그)
- CI: 스택 기동 후 `GET /health` → `cbom_inserted: true` 어설션

#### Step 3 — CI AC1 통합 테스트 (알고리즘 전환) [AC1]
- `e2e-encrypt` 잡에 `KEM_ALGORITHM_ID=ML-KEM-512` 오버라이드 matrix 또는 별도 잡 추가
- 전환 후 `/api/encrypt → /api/decrypt` 왕복 200 확인

#### Step 4 — 모듈 임포트 sys.exit 함정 방어 [MEDIUM]
- `crypto-engine/tests/conftest.py` 신규: KEM/DSA 알고리즘 기본 env fixture
- algorithm_factory import 테스트용 monkeypatch + importlib.reload() 패턴 주석 명시

#### Step 5 — 환경변수 오염 케이스 방어 [LOW]
- `validate_algorithm()` 진입 전 `.strip().upper()` 정규화
- test_algorithm_strategy.py에 공백/소문자/빈 문자열 케이스 추가

### 인수조건
1. CI에서 KEM_ALGORITHM_ID=ML-KEM-512 환경변수로 /api/encrypt 왕복 200 통과
2. 스택 기동 후 GET /health 응답에 cbom_inserted: true 포함
3. api-gateway가 CryptoAlgorithmProperties로 알고리즘 ID를 읽어 crypto-engine 요청에 포함하는 경로 존재

---

## Day 8 검증 리뷰 후속 수정 계획 (2026-03-18)

### 목표
Day 8 수정 계획 검증 리뷰의 잔존 위험요소 3건·테스트 공백 2건 중 타당한 항목을 해소하거나 Day 9로 명확히 이관한다.

### 범위

#### Step 1 — AlgorithmFeignInterceptor 범위 제한 [LOW]
- @Component 제거
- CryptoEngineClientConfig 신규: Interceptor를 빈으로 등록
- @FeignClient(configuration = CryptoEngineClientConfig.class) 로 범위 한정

#### Step 2 — /health cbom_inserted 분리 [LOW]
- /health: {"status": "ok"} 만 반환 (docker-compose healthcheck 유지)
- /health/detail: cbom_inserted 포함 (향후 인증 가드 가능 엔드포인트)
- CI 어설션: GET /health/detail → cbom_inserted: true 로 변경

#### Step 3 — [MEDIUM] crypto-engine 헤더 수신
- kem.py에서 X-Kem-Algorithm-Id 헤더 읽기 → validate_algorithm() 재검증 → per-request oqs 인스턴스
- /api/encrypt E2E에서 헤더 오버라이드 동작 확인 테스트
- 테스트 공백 1(헤더 수신 테스트 없음) 동시 해소

### 인수조건
1. AlgorithmFeignInterceptor가 CryptoEngineClient 전용으로 범위 제한됨
2. /health 응답에 cbom_inserted 없음, /health/detail 에서만 확인

---

## Day 8 kem_encrypt 알고리즘 불일치 수정 계획 (2026-03-18)

### 근거
kem_encrypt가 헤더/env var 알고리즘으로 encap하는데 DB의 key algorithm_id를 읽지 않음.
헤더 불일치 시 liboqs 내부 오류로 400이 나오나 원인 불명확. kem_decrypt와 설계 불일치.

### 질문에 대한 답
- kem_encrypt에서 헤더 알고리즘 ≠ DB key 알고리즘 불일치 시 400 반환(요청 거부)할지, DB algorithm_id를 우선해서 헤더를 무시할지? 400 Bad Request (요청 거부) 권장
- 불일치 에러 메시지를 클라이언트에 노출해도 되는가? => 추상화된 메시지 권장

### 범위

#### Step 1 — kem_encrypt DB algorithm_id 명시적 검증
- SELECT public_key, algorithm_id FROM kem_keys WHERE id = %s 로 변경
- 헤더 있고 stored_algorithm 불일치 시 400 명시적 반환
- 헤더 없으면 stored_algorithm으로 encap (kem_decrypt와 대칭)

#### Step 2 — 테스트 케이스 추가
- ML-KEM-512 key_id + 헤더 ML-KEM-768 → 400
- ML-KEM-512 key_id + 헤더 없음 → 200 (DB algorithm_id 기반)

#### Step 3 — DSA 헤더 오버라이드 Day 9 명시
- dsa_sign/dsa_verify per-request 알고리즘 전환 → 키쌍 재생성 필요로 Day 9 처리

### 인수조건
1. kem_encrypt에서 헤더 불일치 시 명시적 400 반환
2. 헤더 없는 kem_encrypt → DB algorithm_id 기반 encap
3. DSA 헤더 오버라이드가 plan.md Day 9 항목으로 명시

---

## Day 8 CI 버그 수정 (2026-03-18) — algorithm-agility-test KEM_ALGORITHM_ID 미주입

### 원인
Docker Compose v2에서 --env-file 플래그는 쉘 인라인 변수보다 우선순위가 높음.
.env에 KEM_ALGORITHM_ID=ML-KEM-768이 그대로 남아 ML-KEM-512 인라인 설정을 덮어씀.

### 수정
ci.yml L826 다음에 추가:
sed -i 's/^KEM_ALGORITHM_ID=.*/KEM_ALGORITHM_ID=ML-KEM-512/' .env

### 인수조건
1. algorithm-agility-test 잡 .env 생성 후 KEM_ALGORITHM_ID=ML-KEM-512 반영
2. [Day8-AC1] /api/encrypt → algorithm: ML-KEM-512 green

---

#### Day 9 이관 항목
- **DSA 헤더 오버라이드**: `dsa_sign` / `dsa_verify` per-request 알고리즘 전환은 키쌍 재생성이 필요하므로 Day 9 처리
- **Hot-swap**: 재기동 없는 Runtime API 기반 알고리즘 전환 아키텍처 계획 추가
---

## Day 9 (2026-03-19) 세부 계획 — SNDL High-Risk 우선순위 라우팅

### 목표
요청 민감도(HIGH/MEDIUM/LOW)를 분류해 SNDL 위협에 비례하는 KEM 알고리즘(ML-KEM-1024/768/512)으로
자동 라우팅하고, CBOM에 실제 위험도를 기록한다.

### 현재 상태 기준선
- EncryptController: risk_level 개념 없음
- AlgorithmFeignInterceptor: env var 기반 정적 헤더 — per-request 오버라이드 없음
- kem.py _resolve_kem_algorithm(): X-Kem-Algorithm-Id 헤더 수신 인프라 완비
- cbom_assets.risk_level: 'NONE' 하드코딩

### 질문에 대한 답
1. 민감도 분류: 클라이언트 제공 risk_level 필드 vs api-gateway 자동 분류? => API Gateway 자동 분류 권장.
보안과 운영 효율성 측면에서 API Gateway(또는 백엔드 서버)가 자동 분류하는 방식이 훨씬 유리
2. Per-request 헤더 주입: ThreadLocal(AlgorithmFeignInterceptor) vs Feign 메서드 파라미터? => Spring Cloud 환경에서 기존의 "Spring다운" 코딩 스타일과 비즈니스 로직의 순수성을 고려한다면 Interceptor를 통한 암시적 전달이 더 적합
3. DSA 이관 항목: 다중 키쌍 관리까지 vs 응답 algorithm 필드 포함으로 제한? => SNDL 분류 기준 응답 포함으로 제한 권장. 클라이언트에게 "이 데이터는 SNDL 기준에 따라 특정 알고리즘(예: ML-DSA-65)으로 보호되옸다"라는 메타데이터를 정확히 전달하는 것만으로도 API 설계의 완성도는 충분히 높게 평가. 다중 키 관리 로직이 없더라도, "보안 정책에 따른 알고리즘 명시"라는 흐름이 완성되면 프로젝트의 논리적 흐름(Sequence)이 깨지지 않음.

### 범위

#### Step 1 — RiskClassifier + 알고리즘 매핑 (api-gateway)
- RiskLevel enum: HIGH/MEDIUM/LOW + toKemAlgorithm() (HIGH→ML-KEM-1024, MEDIUM→768, LOW→512)
- RiskClassifier 서비스: request body risk_level 읽기 → 없으면 MEDIUM 폴백
- EncryptRequest DTO: riskLevel 필드 추가 (optional)

#### Step 2 — Per-request 헤더 주입 + CBOM risk_level 연동
- CryptoEngineClient.kemInit(): @RequestHeader("X-Kem-Algorithm-Id") 파라미터 추가
- EncryptController: RiskClassifier → kemAlgorithm → kemInit 헤더 포함 호출
- EncryptResponse: risk_level 필드 추가
- kem_encrypt: X-Risk-Level 헤더 수신 → cbom_assets.risk_level 기록 ('NONE' 제거)
- _cbom_startup_insert: KEM_META security_level → risk_level 변환

#### Step 3 — DSA 이관 + CI 검증
- DSA: 현재 고정 알고리즘 유지 (SNDL 라우팅 대상 아님), Hot-swap은 Day 10+ 이관
- CI matrix: risk_level HIGH/MEDIUM/LOW(미전송) 세 케이스 각각 algorithm + cbom 검증

### 인수조건
1. POST /api/encrypt { risk_level: "HIGH" } → algorithm: ML-KEM-1024 + cbom_assets.risk_level: HIGH
2. risk_level 미전송 → ML-KEM-768(MEDIUM) 폴백 + CI 자동 검증
3. CI matrix HIGH/MEDIUM/LOW 세 케이스 모두 green

---

## Day 9 리뷰 위험요소·테스트 공백 수정

### 목표
Day 9 리뷰에서 식별된 위험요소(R1~R4)·테스트 공백(T1~T4) 중 타당성 확인 항목을 단위 테스트 추가·CI CBOM DB 검증 보강·폴백 정책 결정으로 해소한다.

### 범위
- Step 1: RiskClassifierTest.java 신설 (null/blank/소문자/invalid/정상 케이스)
- Step 2: ci.yml CBOM DB 검증 step 추가 (AC2 MEDIUM, AC3 LOW)
- Step 3: EncryptControllerTest HIGH·LOW 경로 추가 + AlgorithmFeignInterceptorTest 신설

### 인수조건
- AC1: RiskClassifierTest 전체 케이스 ./gradlew test 통과
- AC2: CI sndl-routing-test에 MEDIUM·LOW CBOM DB 검증 step 추가, 그린 빌드
- AC3: EncryptControllerTest HIGH/LOW + AlgorithmFeignInterceptorTest 추가, ./gradlew test 통과

### 질문에 대한 답
1. R4 폴백 정책 변경 여부 => 잘못된 risk_level 입력 시 400 Bad Request를 통한 명시적 오류 반환으로의 변경을 권장
2. R2 소문자 허용 여부: 정규화(Normalization) 공식화 권장. "high" → HIGH로 변환하는 대소문자 무시(Case-insensitive) 정규화를 허용하고 이를 스펙에 문서화하는 것을 권장.
3. AlgorithmFeignInterceptor는 별도의 단위 테스트 파일로 분리하여 관리
---

## CI 긴급 수정 계획 (2026-03-19) — compose-integration unhealthy 버그

### 근본 원인
DSA_SECRET_KEY_B64=changeme (non-empty) → algorithm_factory.py fail-fast → gunicorn 크래시 → healthcheck 실패

### 질문에 대한 답
1. crypto-engine.env.example 자체에서 민감한 키 설정을 주석 처리하는 방식을 권장
2. full-stack integration 잡도 동일한 수정 범위에 포함하는 것이 맞다
3. KEM_WRAP_KEY=changeme 허용 여부 -> compose-integration 잡에서도 changeme 대신 유효한 Base64 더미 키 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA='로 교체하는 로직을 추가해야 합니다.

### 범위
- Step 1: crypto-engine.env.example — DSA_SECRET_KEY_B64·DSA_PUBLIC_KEY_B64 주석 처리
- Step 2: ci.yml compose-integration 잡 — .env 생성 step에 sed 방어 코드 추가
- Step 3: docker logs 진단 step 추가

### 인수조건
- AC1: compose-integration 잡 crypto-engine healthy → CI 그린 빌드
- AC2: full-stack integration 잡 회귀 없음
- AC3: DISABLE_PRELOAD=0 환경 키 자동 생성 경로 CI 로그 확인 가능

---

## CI 긴급 수정 계획 (2026-03-19) — api-gateway unhealthy (3개 잡 공통)

### 근본 원인
1. Dockerfile HEALTHCHECK 포트 8080 ← application.yml 실제 포트 8081 불일치
2. API_GATEWAY_MEM_LIMIT=512m 하에서 Spring Boot 3.4.4 OOM 가능성 (heap+non-heap > 512m)

### 범위
- Step 1: Dockerfile HEALTHCHECK 포트 8080 → 8081 수정
- Step 2: MEM_LIMIT 768m 상향 또는 -XX:MaxRAMPercentage=50.0 추가 (Question 3 결정 후)
- Step 3: docker-compose start_period 60s → 90s + CI 3개 잡 진단 step 추가

### 인수조건
- AC1: 3개 CI 잡 api-gateway healthy, 그린 빌드
- AC2: Dockerfile·docker-compose 헬스체크 포트 8081 일치
- AC3: 실패 시 docker logs 자동 출력

---

## Day 9 CI 오류 분석 및 수정 계획 (2026-03-19)

### 현상
- stack-integration-test, sndl-routing-test, algorithm-agility-test 세 잡에서
  `container pqc-security-project-api-gateway-1 is unhealthy` 동일 오류 발생

### 근본 원인 (가설)
api-gateway 컨테이너가 docker compose 메모리 제한(768m) 환경에서 JVM OOM으로
startup 중 crash → start_period 이전에 container exit → Docker가 즉시 unhealthy 처리
- heap(60%×768m=460m) + native(~300m) ≈ 768m (한계 도달)
- api-gateway-build 잡(메모리 제한 없는 docker run)은 정상 통과 → 메모리 제한이 핵심 차이

### 수정 계획
1. 진단: CI docker logs에서 exit code 및 오류 메시지 확인
2. 수정: OOM → API_GATEWAY_MEM_LIMIT=1024m / env var → SPRING_PROFILES_ACTIVE docker-compose 명시 / 타이밍 → --wait-timeout 240
3. 검증: CI 재실행 후 3개 잡 통과 확인

### 인수조건
1. api-gateway unhealthy 오류 미발생
2. 3개 E2E 잡 green
3. 기존 통과 잡 유지

---

## Day 10 (2026-03-20) 세부 계획 — Phase 2 완성 + CI 전체 검증

### 목표
Day 9까지 구현된 코드의 미검증 항목(관건 4 CBOM 전환 이력·Hot-swap 범위 결정)과
미결 항목(관건 8 기술 결정·CI gate job·Phase 3 진입 골격)을 완결하여
Phase 2를 공식 종료하고 Phase 3 착수 조건을 충족한다.

### 질문에 대한 답
1. Hot-swap 범위:  "재기동 없는 Runtime API 전환"을 Phase 2 인수조건의 일부로 포함한다
2. CBOM 이력 검증: algorithm-agility-test CI에서 "ML-KEM-768 기동 → ML-KEM-512 전환 후 cbom_assets.algorithm_id 변경 여부"를 DB 쿼리로 검증한다.
3. TanStack Query + Zustand 결정 문서 형식: 기술 선택 근거를 docs/adr/ (Architecture Decision Record) 형식으로 작성한다.

### 범위

#### Step 1 — [관건 4] CBOM 전환 이력 CI assertion 추가 + Hot-swap 이관 확정
- algorithm-agility-test 잡: ML-KEM-512 전환 후 cbom_assets.algorithm_id DB 검증 step
- Hot-swap: Phase 2 외 공식 이관 결정 시 plan.md 반영
- Phase 2 AC3 체크리스트 업데이트

#### Step 2 — CI Phase 2 gate job (phase2-complete) 추가
- needs: 모든 Phase 2 잡 집약 (trivy, build-and-trivy, api-gateway-build,
  unit-test, stack-integration-test, auth-integration-test,
  sndl-routing-test, algorithm-agility-test, compose-integration)

#### Step 3 — [관건 8] TanStack Query + Zustand 결정 + Phase 3 진입 계획
- 기술 결정 문서 작성 (TanStack Query vs SWR, Zustand vs Redux Toolkit)
- Phase 3 React 프로젝트 초기화 범위 정의 (Vite + React 18 + TS + TQ + Zustand)

### 인수조건
1. algorithm-agility-test: CBOM DB assertion green (ML-KEM-512 전환 이력 확인)
2. phase2-complete gate 잡: 전체 Phase 2 잡 단일 집약 확인
3. TanStack Query + Zustand 결정 + Phase 3 진입 범위 docs/ 문서화 완료

---

## Day 10 수정 계획 (2026-03-20) — Phase 2 완성 + Hot-swap 미구현 확인

### 질문에 대한 답
1. Hot-swap 구현 방식: Admin REST API + AtomicReference 추천. spring-cloud-starter-config와 같은 무거운 의존성을 추가하지 않고도 목적을 달성.
2. Hot-swap 롤백 정책: "전환 완료 후 신규 요청부터 적용". 데이터 정합성과 서비스 안정성이 최우선.
3. R1 Feign 버전 의존 문서화: ADR(Architecture Decision Record) 작성 추천

### 변경 요약
- Day 9 R2/R3/R4/T1/T2/T3/T4 전원 해소 확인 (코드·CI 교차 검증)
- R1(Feign 버전 의존) 부분 해소 — 테스트 추가됐으나 위험 미문서화
- Hot-swap: plan.md에 Phase 2 포함 결정됐으나 구현 없음 확인 (@RefreshScope·Admin API 부재)

### 위험 요소 및 엣지 케이스
- R1: Hot-swap 미구현 — CryptoAlgorithmProperties 기동 시 고정 바인딩 (HIGH)
- R2: algorithm-agility-test CBOM algorithm_id DB assertion 공백 (MEDIUM)
- R3: CI phase2-complete gate 잡 미존재 (MEDIUM)
- R4: Hot-swap + ThreadLocal per-request 오버라이드 경합 가능 (LOW)

### 테스트 공백
- T1: algorithm-agility-test에 cbom_assets.algorithm_id DB 검증 step 없음
- T2: Hot-swap Admin API 테스트 — 구현 전 작성 불가

### 수정 제안
1. AlgorithmAdminController (POST /admin/algorithm, 포트 8081) — AtomicReference 기반 Runtime 전환
2. algorithm-agility-test 잡: Hot-swap 후 CBOM DB assertion step 추가
3. phase2-complete gate job 추가 (ci.yml 최하단)
4. docs/adr/0001-tanstack-query-zustand.md 작성

---

## Day 10 수정 계획 리뷰 타당성 검증 (2026-03-20)

### 질문에 대한 답
1. phase3-scope.md 수정: "Phase 2 구현 완료"로 정정
2. AlgorithmAdminEndpoint CI 노출 검증: curl 확인 Step 추가. CI 환경(Runner)에서 8081 포트가 정상적으로 바인딩되어 응답하는지 확인. 메인 서비스 포트(8080)와 관리용 포트(8081)가 명확히 분리되어 동작하는지 curl로 찔러보는 것만으로도 런타임 오류를 조기에 잡을 수 있음.
3. T3 동시성: CountDownLatch 기반 테스트 추가

### 타당성 판정 요약
- 🔴 phase3-scope.md 불일치: **타당 → Step 2 수정**
- 🟡 무인증 endpoint: **조건부 허용 — 현재 설계 범위 내**
- 🟡 shell injection: **낮음 — 선택적 수정**
- 🟡 untracked 3개 파일: **타당 + 긴급 → Step 1 즉시 스테이징**
- T1 AlgorithmAdminEndpoint 단위 테스트: **타당 → Step 3 추가**
- T2 DSA hot-swap CI: **낮음 — Phase 2 스코프 외**
- T3 동시성 테스트: **부분 타당 — AtomicReference 자체 안전, 문서화로 갈음**

### 수정 계획 AC
1. untracked 3개 파일 git add + 스테이징 확인
2. phase3-scope.md Hot-swap 섹션 "Phase 2 구현 완료"로 정정
3. AlgorithmAdminEndpointTest 5개 케이스 추가 + ./gradlew test 통과
---

## Phase 3 — React CBOM 대시보드 일간 계획 (Day 11–20, Week 5–6)

### Day 11 — 프로젝트 초기화 & 상태 관리 구성
- Vite scaffold (`crypto-dashboard/`), 의존성 추가
- TanStack Query QueryClient 설정 + Zustand store 초기 정의
- API 클라이언트 레이어(`src/api/`) 골격, 라우팅(`/login`, `/dashboard`, `/cbom`)
- **인수조건:** `npm run dev` 기동, `useEncrypt` hook 컴포넌트 1개, `selectedAlgorithm` store 정의

### Day 12 — 인증 & 공통 레이아웃
- JWT 로그인, 401 자동 갱신, Protected Route, Sidebar+Header 레이아웃
- **인수조건:** 미인증 403 리다이렉트, 로그인 후 `/dashboard` 진입

### Day 13 — CBOM 목록 시각화
- `useCbomList` hook, 테이블 컴포넌트, 알고리즘 필터, 페이지네이션
- Day 11의 MSW를 CBOM 백엔드 API(GET /api/cbom)로 실제 연동
- **인수조건:** 목록 정상 렌더링, 필터 동작, DevTools 캐시 키 확인

### Day 14 — 우선순위 뷰 & 알고리즘 전환 UI
- High-Risk 우선순위 카드 그룹핑, hot-swap UI (`POST /actuator/algorithm`), `useMutation` + invalidateQueries
- **인수조건:** 전환 후 CBOM 자동 갱신, 불허용 알고리즘 UI 차단

### Day 15 — 수동 재고(Inventory) 등록 UI
- 등록 폼, 편집 모달, 삭제 confirm, optimistic update
- **인수조건:** CRUD 전 동작, 필수 필드 유효성 검사

### Day 16 — 실시간 모니터링 UI
- `refetchInterval: 5000`, 통계 카드, 알고리즘별 사용량 차트, 최근 이력 테이블
- **인수조건:** 5s 자동 갱신, 오류 시 배지 표시

### Day 17 — Prometheus/Grafana 알람 연동
- `docker-compose.yml` 서비스 추가, scrape 설정, Grafana 프로비저닝, 알람 규칙
- **인수조건:** Grafana 접근, Prometheus up==1, 알람 규칙 1개 이상

### Day 18 — 컴포넌트 테스트 & E2E
- Vitest + RTL, Playwright E2E (로그인→CBOM→등록→전환), MSW mock
- **인수조건:** 커버리지 70%+, E2E 2개 이상 통과, `npm run build` 성공

### Day 19 — CI 파이프라인 통합
- `frontend-ci` 잡 (lint/test/build), Trivy Critical 0건, node_modules 캐시
- **인수조건:** PR 시 frontend-ci 자동 실행, 전체 CI 그린

### Day 20 — Phase 3 최종 검증 & 문서화
- 인수조건 체크리스트, `make test-dct`, 환경변수 교체 기동 테스트, docs 업데이트
- **인수조건:** plan.md 인수조건 3개 전체 통과

---

## Day 11 — 프로젝트 초기화 & 상태 관리 구성

### 목표
기존 dashboard/ 골격 위에 Vite + React 18 + TypeScript를 초기화하고,
TanStack Query / Zustand / react-router-dom 레이어와 API 클라이언트 골격을 완성한다.

### 질문에 대한 답
1. dashboard/src/features/ 하위에 이미 auth/, cbom/, inventory/, monitor/ 디렉토리가 있습니다. 이 구조를 그대로 유지하면 되나요? => 유지 권장
2. JWT 토큰을 메모리(Zustand)에만 저장할까요, 아니면 sessionStorage에 함께 저장해도 되나요? (XSS 리스크 vs 새로고침 유지) => Access Token은 메모리에만 두고 Refresh Token을 HttpOnly 쿠키로 받아 자동 로그인을 구현
3. CBOM 백엔드 API(GET /api/cbom)가 아직 없는데? => Day 11에 MSW를 도입하고 Day 13에 교체

### Step 1 — Vite scaffold + 의존성 설치

**목표:** package.json, vite.config.ts, tsconfig.json, tailwind.config.ts 생성

\`\`\`bash
cd dashboard
npm create vite@latest . -- --template react-ts
mkdir -p src/features/{auth,cbom,inventory,monitor,dashboard}
npm install @tanstack/react-query@^5 @tanstack/react-query-devtools@^5 zustand@^4 react-router-dom@^6 axios@^1
npm install -D tailwindcss@^3 postcss autoprefixer @types/node
npx tailwindcss init -p
\`\`\`

**인수조건:**
- [ ] node_modules/ 설치 완료
- [ ] tailwind.config.ts, postcss.config.js 생성됨
- [ ] src/features/{auth,cbom,inventory,monitor,dashboard}/ 디렉토리 존재

### Step 2 — vite.config.ts + tsconfig.json 설정

**목표:** /api proxy(→ localhost:8080), @ path alias 설정

**vite.config.ts 핵심 설정:**
\`\`\`ts
server: {
  proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } }
},
resolve: { alias: { '@': '/src' } }
\`\`\`

**tsconfig.json 핵심 설정:**
\`\`\`json
"paths": { "@/*": ["./src/*"] }
\`\`\`

**인수조건:**
- [ ] npm run dev 실행 시 /api 요청이 8080으로 프록시됨
- [ ] @/ 경로로 import 가능

### Step 3 — src/lib/ (QueryClient + Axios 인스턴스)

**목표:** 전역 QueryClient 옵션 설정, JWT 자동 주입 Axios 인스턴스 생성

**생성 파일:**
- src/lib/queryClient.ts — staleTime: 30s, gcTime: 5min, retry: 1, refetchOnWindowFocus: false
- src/lib/axiosClient.ts — baseURL: import.meta.env.VITE_API_BASE_URL, 요청 인터셉터로 Zustand token → Authorization: Bearer 주입

**인수조건:**
- [ ] QueryClient 옵션 4개 명시적 설정 완료
- [ ] axiosClient가 Zustand store에서 token을 읽어 헤더에 주입

### Step 4 — src/store/useAppStore.ts (Zustand)

**목표:** 인증 토큰과 선택된 알고리즘을 전역 상태로 관리

**상태 정의:**
| 상태 | 타입 | 초기값 |
|------|------|--------|
| token | string \| null | null |
| selectedAlgorithm | string | "ML-KEM-768" |
| setToken | (t: string) => void | — |
| clearToken | () => void | — |
| setSelectedAlgorithm | (a: string) => void | — |

**인수조건:**
- [ ] 브라우저 콘솔: useAppStore.getState().selectedAlgorithm === "ML-KEM-768"
- [ ] setToken → token 갱신, clearToken → null 복귀 동작

### Step 5 — src/api/ (API 클라이언트 레이어)

**목표:** 백엔드 실제 스펙 기반 타입 정의 + axios 호출 함수 작성

**src/api/auth.ts**
\`\`\`
POST /api/auth/login
Body:     { userId: string; password: string }
Response: { token: string }
\`\`\`

**src/api/encrypt.ts**
\`\`\`
POST /api/encrypt
Body:     { plaintext: string; risk_level?: string }
Response: { key_id: number; algorithm: string; kem_ciphertext: string;
            aes_ciphertext: string; aes_iv: string; risk_level: string }
\`\`\`

**src/api/cbom.ts**
\`\`\`
GET /api/cbom  ← 백엔드 미구현 (Day 13 연동), Day 11은 타입 정의만
type CbomEntry = { id: number; algorithm: string; type: string;
                   risk_level: string; registered_at: string; note?: string }
\`\`\`

**인수조건:**
- [ ] 3개 파일 타입 오류 없이 tsc 통과
- [ ] encrypt API 함수가 axiosClient 사용

### Step 6 — 라우팅 + main.tsx + DashboardPage (인수조건 핵심)

**목표:** 라우트 골격 완성, DashboardPage에서 POST /api/encrypt 실제 호출 확인

**src/App.tsx 라우트:**
| 경로 | 컴포넌트 |
|------|---------|
| /login | LoginPage (빈 페이지) |
| /dashboard | DashboardPage ★ |
| /cbom | CbomPage (빈 페이지) |
| /inventory | InventoryPage (빈 페이지) |
| /monitor | MonitorPage (빈 페이지) |
| / | redirect → /dashboard |

**src/main.tsx:** QueryClientProvider → RouterProvider, ReactQueryDevtools(개발 환경)

**src/features/dashboard/DashboardPage.tsx:**
- plaintext 텍스트 입력 + 암호화 버튼
- useMutation으로 POST /api/encrypt 호출
- 결과(algorithm, risk_level, key_id) 화면 출력
- 로딩 스피너, 오류 메시지 처리

**인수조건:**
- [ ] npm run dev → /dashboard 에서 텍스트 입력 후 암호화 버튼 클릭 → 결과 JSON 표시
- [ ] TanStack Query DevTools 패널에서 mutation 상태 확인 가능

### Step 7 — Dockerfile + nginx.conf 완성

**목표:** builder 스테이지 TODO 제거, SPA 라우팅 + /api proxy 지원 nginx 설정

**dashboard/Dockerfile builder 스테이지:**
\`\`\`dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
USER nginx
EXPOSE 3000
\`\`\`

**dashboard/nginx.conf 핵심:**
- listen 3000
- try_files $uri /index.html (SPA 라우팅)
- location /api → proxy_pass http://api-gateway:8080

**인수조건:**
- [ ] docker compose build dashboard 오류 없이 완료
- [ ] npm run build dist/ 생성 확인


### Day 11 최종 인수조건

1. npm run dev 기동 + /dashboard에서 POST /api/encrypt 결과 화면 표시
2. 브라우저 콘솔 useAppStore.getState().selectedAlgorithm === "ML-KEM-768" 확인
3. npm run build 타입 오류 없이 성공 + docker compose build dashboard 통과

---

## Day 12 — 인증 & 공통 레이아웃 세부 계획

### 질문에 대한 답
1. Refresh Token 순환 (HttpOnly) vs 단순 로그아웃 => Day 12에 Refresh Token 순환(HttpOnly 쿠키) 구조를 포함하는 것을 추천.
2. AppLayout Sidebar 네비게이션: 아이콘 라이브러리 vs 텍스트 => lucide-react와 같은 가벼운 아이콘 라이브러리를 추가하는 것을 추천
3. MSW 핸들러 정의 범위 => Day 12는 /api/auth/login과 /api/encrypt만 작성하여 범위를 제한하는 것을 추천합니다.

### 목표
axiosClient 401 인터셉터·MSW·PrivateRoute·AppLayout·LoginPage를 구현하여
미인증 리다이렉트와 api-gateway 미연동 환경 안정성을 확보한다.

### Step 요약
1. MSW 도입 + /api/auth/login, /api/encrypt 핸들러 (AC#1 위험 해소)
2. axiosClient response 401 인터셉터 → clearToken + /login (위험요소 해소)
3. PrivateRoute — token null → /login 리다이렉트
4. AppLayout — Sidebar(4 NavLink) + Header(로그아웃) + Outlet
5. LoginPage — useMutation + MSW mock 로그인
6. App.tsx 라우터 재구성 — PrivateRoute > AppLayout > protected routes
7. Vitest 단위 테스트 3개 (PrivateRoute / LoginPage / axiosClient 401)

### 최종 인수조건
1. 미인증 /dashboard → /login 리다이렉트
2. MSW 로그인 성공 → /dashboard + token !== null
3. 401 응답 → clearToken 호출 (vitest 검증)

---

### Day 12 타당성 검증 & 수정 계획 (2026-03-24)

#### 타당성 검증 결과
- R1 (`window.location.href`): 타당 — axiosClient.ts:21 코드 확인
- R2 (token 새로고침 소실): 타당 — useAppStore에 persist 없음 확인
- R3 (LoginPage 에러 메시지): 타당 — LoginPage.tsx:57 `(mutation.error as Error).message` 확인
- T1 (AppLayout 로그아웃 테스트 없음): 타당 — test/ 디렉토리에 해당 파일 없음 확인
- T2 (LoginPage 실패 케이스 없음): 타당 — LoginPage.test.tsx 성공 케이스만 존재 확인
- T3 (token 소실 시나리오): 조건부 타당 — R2 구현 선행 필요, Day 18로 이관
- S1 (navigate 주입): 타당 — window.dispatchEvent 방식 권장
- S2 (zustand persist): 보류 — "Access Token 메모리 저장" 기존 설계 결정과 상충, 재확인 필요
- S3 (에러 메시지 분기): 타당 — 즉시 반영 가능

#### 수정 범위
1. S3: LoginPage.tsx:57 에러 메시지 → `error.response?.data?.message ?? error.message` 분기
2. S1: axiosClient.ts:21 `window.location.href` → `window.dispatchEvent(new Event('auth:logout'))` + App.tsx listener
3. T1: AppLayout.test.tsx 로그아웃 동작 테스트 추가
4. T2: LoginPage.test.tsx 실패 케이스 1개 추가
5. T3/S2: Day 18 이관 또는 설계 결정 후 재논의

#### 인수조건
1. axiosClient.ts에서 window.location.href 제거 후 기존 axiosClient401.test.ts 통과
2. LoginPage 에러가 서버 응답 body 우선 표시
3. 신규 테스트 포함 npm run test 전체 통과

---

## Day 13 — CBOM 목록 시각화 세부 계획

### 질문에 대한 답
1. 백엔드 GET /api/cbom 미구현 — 현재 controller가 Auth/Encrypt/Health/KEM/DSA만 존재 => Day 13은 MSW로 목업하고, 백엔드 구현은 이관하는 것을 추천. MSW로 응답 구조를 먼저 확정한 뒤, 추후 백엔드 구현 시 해당 규격을 그대로 가져가는 방식이 효율적.
2. CbomEntry 타입 불일치 — api/cbom.ts의 타입(algorithm, registered_at)이 DB 스키마(algorithm_id, created_at, JSONB asset)와 다름 => 백엔드 응답 DTO(Data Transfer Object)를 먼저 설계하고 프론트엔드 타입을 거기에 맞추어야 합니다.
3. 페이지네이션 방식 => 현재 단계에서는 클라이언트사이드를 택하되, 인터페이스는 서버사이드를 고려해 설계하세요.

### 목표
CbomPage stub에 useCbomList hook·테이블·알고리즘 필터·페이지네이션을 구현하여
MSW 기반으로 목록 렌더링과 TanStack Query 캐시 키를 검증한다.

### Step 요약
1. MSW handlers.ts — GET /api/cbom 핸들러 추가 (10건, risk_level 혼합)
2. api/cbom.ts — getCbomList() 함수 + CbomEntry 타입 확정
3. useCbomList hook — useQuery queryKey: ['cbom']
4. CbomPage — 테이블 + 알고리즘 필터(<select>) + 클라이언트사이드 페이지네이션(10건/페이지)
5. Vitest 1개 — useCbomList MSW 연동, 10건 반환 확인

### 최종 인수조건
1. /cbom 목록 10건 렌더링 + 알고리즘 필터 동작
2. DevTools 캐시 키 ['cbom'] 확인
3. npm run test 전체 통과

---

## Day 13 보안 취약점 & 테스트 공백 수정 계획 (2026-03-25)

### 질문에 대한 답
1. registered_at 필드명은 백엔드 API 스펙 기준으로 확정된 이름인가, 아니면 created_at으로 변경 가능성이 있는가? (필드명 통일 전략 결정에 필요) => registered_at을 프론트엔드 표준으로 확정하고, 백엔드 DTO에서 alias 처리
2. Authorization 헤더 검증은 MSW 핸들러 레벨에서 검증하는 것으로 충분한가, 아니면 axiosClient 단위 테스트에 추가해야 하는가? => axiosClient 단위 테스트에 추가. MSW 핸들러 검증은 보조 수단.
3. CbomPage 컴포넌트 테스트는 기존 useCbomList.test.tsx와 같은 파일로 합칠까, 별도 CbomPage.test.tsx로 분리할까? => 별도 CbomPage.test.tsx로 분리

### 목표
Day 13 리뷰 타당 항목(보안 3건 + 테스트 5건) 수정으로 CbomPage 런타임 안정성 및 테스트 커버리지 확보

### 범위
- Step A: CbomPage.tsx RISK_BADGE fallback 스타일 + registered_at null 방어 + cbom.ts JSDoc 추가
- Step B: CbomPage.test.tsx 신규 — 필터/빈배열/isLoading/isError/RISK_BADGE 케이스
- Step C: Authorization 헤더 검증 — handlers.ts 401 시나리오 + useCbomList.test.tsx 케이스 추가

### 인수조건
1. risk_level UNKNOWN 입력 시 기본 배지 스타일 렌더링 (TypeError 없음)
2. registered_at null/undefined 시 '—' 표시 (TypeError 없음)
3. npm test 전체 green (CbomPage.test.tsx 포함)

## Day 14 세부 계획 — 우선순위 뷰 & 알고리즘 전환 UI (2026-03-25)

### 목표
CbomPage에 위험도별 우선순위 카드 뷰와 알고리즘 hot-swap 패널 추가.
High-Risk 자산 즉시 식별 + 허용 알고리즘으로만 전환 가능하도록 구현.

### 질문에 대한 답
1. 우선순위 뷰: 탭 전환 vs 상단 고정 섹션 => 탭 전환 방식으로 결정. HIGH 위험군을 즉시 격리해서 봐야 함. HIGH 탭을 기본 활성. 반면 상단 고정 섹션은 전체를 한 번에 보여줘서 HIGH/MEDIUM/LOW가 섞여 시선이 분산. 
2. POST /actuator/algorithm body 스펙: { algorithm: string } 단일 필드 여부 => { algorithm: string } 단일 필드 확정, 단 asset_id 포함 권장. 최소 스펙은 아래 2필드: { "asset_id": "uuid", "algorithm": "ML-KEM-768" }. MSW 핸들러도 이 스펙으로 설계하면 백엔드 연동 시 Breaking Change 없이 그대로 이어집니다.
3. 불허용 알고리즘 목록: 프론트 상수 하드코딩 vs GET API 조회 => 프론트 상수 하드코딩. 단, 프론트 상수는	UI disabled 처리 (UX 가드)	. 백엔드 POST 검증은	실제 허용/거부 강제 역할. GET API 조회 방식은 백엔드에서 allowlist를 서버가 관리할 수 있게 되는 시점에 도입하면 됩니다. 그 전까지 프론트 상수는 "사용자 실수 방지" 용도로만 간주하고, 보안 강제 수단으로 오해하지 않도록 코드 주석에 명시해두세요.
```
// UX guard only — NOT a security boundary.
// Server-side validation is required in POST /actuator/algorithm handler.
export const ALLOWED_ALGORITHMS = ['ML-KEM-512', 'ML-KEM-768', 'ML-DSA-44'];
```

### 범위
- Step A: CbomPriorityView.tsx 신규 + CbomPage.tsx 탭 전환 추가
- Step B: api/algorithm.ts + useAlgorithmSwitch.ts + AlgorithmSwitchPanel.tsx 신규
  - ALLOWED_ALGORITHMS 상수 (ML-KEM/ML-DSA 계열)
  - 불허용 선택 시 버튼 disabled + MSW handler 추가
- Step C: CbomPriorityView.test.tsx + useAlgorithmSwitch.test.tsx 신규

### 인수조건
1. 우선순위 뷰 탭 — HIGH/MEDIUM/LOW 카드 그룹 렌더링 + 건수 정확
2. 불허용 알고리즘 선택 시 전환 버튼 disabled 유지
3. 전환 성공 후 queryKey ['cbom'] invalidate → 목록 자동 갱신

---

## Day 14 리뷰 위험 요소 & 테스트 공백 해소 (2026-03-26)

### 목표
Day 14 리뷰 지적 4개 항목을 테스트/코드로 확정하여 미검증 경로를 제거한다.

### 범위
- Step 1: useAlgorithmSwitch 실패 케이스 테스트 추가 (500 오버라이드 → isError)
- Step 2: AlgorithmSwitchPanel 단위 테스트 파일 신규 작성 (렌더/disabled/성공/실패)
- Step 3: 뷰탭 전환 시 필터·페이지 동작 명시화 (유지 또는 초기화 결정 후 테스트 고정)

### 인수조건
- useAlgorithmSwitch 오류 케이스 isError 검증 통과
- AlgorithmSwitchPanel 4개 케이스 테스트 통과
- 뷰탭 전환 필터·페이지 동작 테스트로 명시 완료

---

## Day 15 세부 계획 — 수동 재고(Inventory) 등록 UI + AlgorithmSwitchPanel 연결 (2026-03-26)

### 목표
InventoryPage에 CRUD UI 구현 + CbomPage AlgorithmSwitchPanel 미연결 해소

### 질문에 대한 답
Q1. /api/inventory 백엔드 존재 여부 => MSW mock + Authorization 검사 패턴 유지
Q2. 편집 모달 구현 방식 => 인라인 조건부 렌더링 (AlgorithmSwitchPanel 선례 답습)
Q3. Optimistic update 실패 롤백 사용자 피드백 => 인라인 에러 메시지 (보안 오류 휘발 방지)

### 범위
- Step 1: api/inventory.ts + MSW 핸들러 + 4개 React Query 훅 (optimistic update 포함)
- Step 2: InventoryPage 등록 폼 / 편집 / 삭제 confirm UI
- Step 3: CbomPage에 AlgorithmSwitchPanel 연결 + InventoryPage.test.tsx 작성

### 인수조건
- Inventory CRUD 테스트 통과 (optimistic rollback 포함)
- 필수 필드 빈값 시 제출 불가 (disabled)
- AlgorithmSwitchPanel CbomPage 연결 및 테스트 검증

---

## Day 15 리뷰 후속 — 테스트 공백 수정 계획 (2026-03-27)

### 목표
타당성 검증된 테스트 공백 3건을 InventoryPage.test.tsx, CbomPage.test.tsx에 추가하여 인수조건 완전 충족

### 질문에 대한 답
1. 편집 성공 테스트에서 MSW PUT 응답은 현재 handlers.ts의 기본 핸들러를 재사용하면 충분한가, 아니면 별도 override 핸들러가 필요한가? => 충분하다.
2. CbomPage의 AlgorithmSwitchPanel 연결 테스트는 "패널이 렌더됨" 확인으로 충분한가, 아니면 패널 내 전환 완료까지 검증해야 하는가? => 보안 프로젝트 맥락에서도 "렌더됨 + currentAlgorithm props 전달 확인" 수준으로 충분하다.
3. 기각한 리스크(MSW idx, Date.now()) 중 향후 Sprint에서 개선 예정이 있는가? => 없다.

### 기각 항목
- MSW PUT idx 참조: map이 인덱스 보존하므로 실제 버그 없음
- Date.now() 충돌: onSettled invalidate로 즉시 교체되어 무해

### 채택 항목 (Steps)
1. InventoryPage.test.tsx — 편집 성공 케이스
2. InventoryPage.test.tsx — 편집 실패(500) 롤백 케이스
3. CbomPage.test.tsx — AlgorithmSwitchPanel 연결 케이스 1건

### 인수조건
- 편집 성공/실패 롤백 테스트 각 1건 추가 및 green
- CbomPage AlgorithmSwitchPanel 연결 테스트 1건 추가 및 green
- vitest run 전체 통과

---

## Day 15 후속 — CbomPage AlgorithmSwitchPanel 연결 테스트 계획 (2026-03-27)

### 목표
CbomPage.test.tsx에 AlgorithmSwitchPanel 연결 케이스를 추가하여 Day 15 인수조건 완전 충족

### 질문에 대한 답
1. 토글 닫힘까지 검증할지? => 토글 닫힘(재클릭 시 패널 unmount)은 CbomPage 고유 동작이므로 1건 추가 권장합니다. AlgorithmSwitchPanel.test.tsx는 onClose prop을 mock 함수로만 확인하고 실제 닫힘 여부를 검증하지 않습니다. CbomPage의 setSwitchTarget(null) 로직이 실제로 동작하는지는 CbomPage 테스트에서만 확인 가능합니다.
2. 내부 API 호출 재검증 필요 없음? => 렌더 연결만으로 충분합니다
3. 로컬 WSL 수동 확인으로 충분한가? => 현재 플랜 기준 로컬 충분합니다

### 현재 상태
- ✅ InventoryPage 편집 성공 케이스 (line 103)
- ✅ InventoryPage 편집 실패(500) 롤백 케이스 (line 115)
- ✅ CbomPage AlgorithmSwitchPanel 연결 케이스 (line 88, 101)

### 범위 (Steps)
1. CbomPage.test.tsx — 전환 버튼 클릭 시 AlgorithmSwitchPanel 렌더 확인
2. CbomPage.test.tsx — 같은 버튼 재클릭 시 Panel 토글 닫힘 확인
3. plan.md 상태 동기화

### 인수조건
- CbomPage.test.tsx AlgorithmSwitchPanel 연결 케이스 1건 이상 추가 및 green
- 기존 CbomPage 테스트 8개 모두 통과
- Day 15 인수조건 ❌ 항목 전부 해소

---

## Day 16 — 실시간 모니터링 UI 세부 계획 (2026-03-27)

### 목표
MonitorPage를 구현하여 CBOM 데이터를 5초 자동 갱신으로 표시하는 통계 카드·차트·이력 테이블 제공

### 전제
- 데이터 소스: /api/cbom 재활용 (별도 엔드포인트 없을 경우)
- 차트: CSS width% 기반 bar (외부 라이브러리 없이)
- 오류 배지: isError(API fetch 실패) 시 표시

### 질문에 대한 답
1. 데이터 소스 → /api/cbom 재활용. 별도 /api/monitor 없음. 프론트에서 응답 집계.
2. 차트 → CSS width% 기반 bar 직접 구현. recharts 등 외부 라이브러리 미사용.
3. 오류 배지 → isError (API fetch 실패) 시 배지 표시. HIGH 위험도 알림이 아님.

### 범위 (Steps)
1. useMonitor.ts — /api/cbom + refetchInterval:5000, isError 노출
2. MonitorPage.tsx — 통계 카드 4개(전체/HIGH/MEDIUM/LOW) + 알고리즘별 bar + 최근 이력 테이블 5건
3. MonitorPage.test.tsx — 로딩·정상 렌더·isError 배지·최근 5건 정렬 케이스

### 인수조건
- refetchInterval:5000 설정으로 5s 자동 갱신
- isError 시 오류 배지 노출
- MonitorPage.test.tsx 전체 케이스 green

---

## Day 16 리뷰 항목 수정 계획 (2026-03-27)

### 질문에 대한 답
1. isError UX — 배지+early return vs staleTime 유지 => 보안 프로젝트 기준: early return 권장.
2. 로딩 테스트 전략 — delay() 추가 vs 케이스 삭제 => MSW delay('infinite') 핸들러 추가 권장.
3. bar aria-label 테스트 — 텍스트 검증 vs width style % => aria-label 텍스트 검증 권장. width style은 검증 불필요.

### 타당성 검증 요약
- [위험-중] WSL 환경 문제 → 코드 수정 대상 아님 (환경 확인으로 해소)
- [위험-저] 로딩 테스트 flaky → 타당, 수정 필요
- [위험-저] isError 빈 테이블 → 타당, UX 스펙 결정 후 수정
- 테스트 공백 2개 (빈 배열, aria-label) → 타당, 테스트 추가 필요

### 수정 범위 (3 Steps)
1. 로딩 테스트 flaky 수정 (MonitorPage.test.tsx — delay 추가)
2. isError early return 추가 (MonitorPage.tsx)
3. 누락 테스트 2개 추가 (MonitorPage.test.tsx)

### 인수조건
1. vitest 5회 반복 전 케이스 green
2. isError 시 빈 테이블 미노출
3. 총 6케이스 이상 green

---

## Day 17 세부 계획 — Prometheus/Grafana 알람 연동 (2026-03-28)

### 전제 조건 확인
- api-gateway: micrometer-registry-prometheus 미설치, prometheus endpoint 미노출
- docker-compose: Prometheus/Grafana 서비스 없음, monitoring/ 디렉터리 없음

### 질문에 대한 답
1. Grafana 포트 — 3001 고정 => 3001 고정 권장.
2. 알람 규칙 타깃 — PQC 도메인 지표 포함 권장 => up == 0 1개 + HIGH 위험 임계치 1개, 총 2개 권장.

### Step 1 — api-gateway 메트릭 엔드포인트 활성화
- build.gradle: micrometer-registry-prometheus 의존성 추가
- application.yml: management.endpoints.web.exposure.include에 prometheus 추가
- 검증: http://api-gateway:8081/actuator/prometheus 200 응답

### Step 2 — 모니터링 설정 파일 생성 (신규 4개)
- monitoring/prometheus/prometheus.yml: scrape api-gateway:8081, interval 15s
- monitoring/prometheus/rules/pqc_alerts.yml: PqcApiGatewayDown 알람 (up==0, severity: critical)
- monitoring/grafana/provisioning/datasources/prometheus.yml: Prometheus datasource 자동 프로비저닝
- monitoring/grafana/provisioning/dashboards/dashboard.yml: 대시보드 폴더 설정

### Step 3 — docker-compose.yml 서비스 추가
- prometheus: prom/prometheus:v2.51.0, pqc-internal, port 9090
- grafana: grafana/grafana:10.4.2, pqc-internal+pqc-public, port 3001→3000
- 볼륨: prometheus-data, grafana-data 추가

### Step 4 - crypto-engine 메트릭
- requirements/prod.txt — prometheus-client 1줄 추가
- crypto-engine/app/main.py — make_asgi_app() 마운트 (~5줄)
- prometheus.yml — scrape job 1개 추가 (이미 Step 2에서 파일 생성)

### 인수조건
1. http://localhost:3001 Grafana 접근 + Prometheus datasource green
2. http://localhost:9090/targets — api-gateway UP (up==1)
3. http://localhost:9090/api/v1/rules — PqcApiGatewayDown 알람 규칙 로드 확인

---

## Day 17 리뷰 결함 수정 (2026-03-28)

### 목표(Goal)
보안 설정 강화 + `cbom_assets_total` 메트릭 구현으로 Day 17에서 식별된 보안·기능 결함 3건을 모두 제거한다.

### 질문에 대한 답
1. Prometheus 9090 포트를 외부에서 직접 접근해야 하는 운영 요구사항이 있는가? => 필요 없다.
2. `cbom_assets_total` Counter는 어느 서비스 레이어에서 발행해야 하는가? => 발행 지점은 crypto-engine이 맞음. 단, 현재 구현에서 누락된 상태.
3. `.env` 파일을 Git에서 관리하는가, 아니면 CI/CD 시크릿으로 주입하는가? => git에는 추적되지 않으나, `/config/dashboard.env`의 `GRAFANA_ADMIN_PASSWORD`가 관리되므로 해당 것으로 주입. 

### 범위(Scope — Steps)

**Step A — 보안 설정 강화**
- `.env.example`에 `GRAFANA_ADMIN_PASSWORD=changeme` 항목 추가
- `.env`에 실제 강한 패스워드 값 설정 (Git 미추적 파일)
- `docker-compose.yml` prometheus 서비스에서 `pqc-public` 네트워크 제거 → `pqc-internal`만 유지, `ports` 항목 제거

**Step B — cbom_assets_total Counter 구현**
- api-gateway Java 코드에서 CBOM 분석 결과를 처리하는 지점에 Micrometer `Counter.builder("cbom_assets_total").tag("risk_level", ...)` 등록
- `PqcHighRiskAlgorithmDetected` 알람 발동 가능 상태 확보

**Step C — 자동화 테스트 3건 추가**
- `/metrics` 엔드포인트에 `cbom_assets_total` 포함 여부 assert
- Prometheus `/api/v1/rules` 알람 로드 assert
- (통합테스트 환경 기준) Grafana datasource health API 200 assert

### 인수조건(Acceptance Criteria)
1. `docker-compose up` 후 Prometheus UI에서 9090 포트가 localhost 외부에서 접근 불가
2. CBOM 스캔 API 호출 후 `/metrics`에 `cbom_assets_total{risk_level="HIGH"}` 값 증가 확인
3. 신규 테스트 3건이 CI에서 green

---

## Day 18 — 컴포넌트 테스트 & E2E 세부 계획 (2026-03-28)

### 현재 상태 요약
- Vitest + RTL + MSW: 설치됨, 테스트 파일 11개 존재
- @vitest/coverage-v8: 설치됨, but vite.config.ts coverage 블록 없음
- test:coverage 스크립트: package.json에 없음
- Playwright: 미설치 (package.json에 없음)
- npm run build: tsc --noEmit + vite build — 현재 통과 여부 미확인

### 목표(Goal)
기존 Vitest 테스트 11개를 커버리지 70%+ 기준으로 보강하고, Playwright E2E 2개를 신규 추가하여 npm run build까지 CI 체인을 완성한다.

### 질문에 대한 답
1. E2E 테스트는 실제 백엔드를 띄운 상태에서 실행하는가, 아니면 MSW로 브라우저 mock 처리하는가? => MSW browser mock 권장. 단 Playwright + MSW browser worker 조합은 playwright.config.ts에서 webServer + public/mockServiceWorker.js 로드 순서를 맞춰야 함. 설정 누락 시 E2E가 실제 API 호출을 시도해 무조건 실패.
2. 커버리지 기준 파일 범위를 src/features/** + src/components/**로 한정할지, src/** 전체로 잡을지? => src/features/** + src/components/** 한정 권장.

### 범위(Scope — Steps)

**Step A — 커버리지 설정 + 임계값 적용**
- vite.config.ts test 블록에 coverage: { provider: 'v8', reporter: ['text', 'lcov'], thresholds: { lines: 70, functions: 70, branches: 70 } } 추가
- package.json scripts에 "test:coverage": "vitest run --coverage" 추가
- npm run test:coverage 실행 → 70% 미달 파일 식별 → 해당 컴포넌트 테스트 보완

**Step B — Playwright E2E 설치 및 시나리오 2개 작성**
- @playwright/test + Chromium 설치 (npm install -D @playwright/test && npx playwright install chromium)
- playwright.config.ts 생성 (baseURL: http://localhost:5173, MSW browser mock 활성화 또는 백엔드 연동)
- E2E 시나리오 ①: 로그인 → CBOM 목록 진입 → 알고리즘 필터 동작 확인
- E2E 시나리오 ②: CBOM → 수동 등록 폼 작성 → 저장 → 목록 반영 확인
- package.json scripts에 "test:e2e": "playwright test" 추가

**Step C — npm run build 통과 확인**
- tsc --noEmit 오류 0건 확인 후 vite build 실행
- 타입 오류 발생 시 해당 파일만 최소 수정

### 인수조건(Acceptance Criteria)
1. npm run test:coverage 결과에서 lines/functions/branches 모두 70% 이상
2. npm run test:e2e 에서 E2E 시나리오 2개 이상 pass
3. npm run build 오류 0건 완료

---
## Day 18 보안 취약점 수정 계획 (2026-03-29)

### 목표
Day 18 인수조건(coverage ≥ 70% · E2E 2개 pass · build 0건)을 CI 재현 가능하게 완성하고,
자격증명 하드코딩 보안 결함(보안-HIGH)을 제거한다.

### 질문에 대한 답
1. E2E 테스트 실행 전략 => MSW mock 전용 E2E 방식 확정 가능. 단, onUnhandledRequest: 'bypass' 설정 때문에 handlers에 없는 경로는 조용히 실패함. E2E 스펙에서 호출하는 모든 API 엔드포인트(/api/auth/login, /api/cbom, /api/inventory)가 handlers.ts에 등록되어 있는지 반드시 확인 필요.
2. MonitoringIT 자격증명 .env 분리 + .gitignore + CI 시크릿 주입 확정 여부? => .gitignore 패턴은 준비됨, CI 시크릿 주입은 미확정. monitoring.integration=true를 CI에 추가할 때 함께 secrets.GRAFANA_ADMIN_PASSWORD를 -Dgrafana.admin.password=${{ secrets.GRAFANA_ADMIN_PASSWORD }} 형태로 주입하는 스텝을 신규 작성해야 함. 이는 Day 19 CI 구성 작업에서 한 번에 처리하는 것이 적절.
3. monitoring.integration=true — 현재 CI workflow에 있는가? => 없음. Day 19에서 추가 예정.

### Step 1. 보안 결함 제거
- MonitoringIT.java:40 — System.getenv("IT_ADMIN_CREDENTIALS") 치환
- E2E 스펙 — process.env.E2E_USER / E2E_PASS 환경변수화
- .env.e2e.example 템플릿 추가

### Step 2. E2E MSW 설정 확정
- playwright.config.ts webServer 또는 global-setup MSW 방식 결정
- npm run test:e2e 1회 pass 확인

### Step 3. 단위 테스트 경계값 보완
- CbomMetricsTest: null/""/UNKNOWN 3케이스 추가
- EncryptControllerTest: LOW/MEDIUM never() 검증 추가
- npm run test:coverage 70%+ pass 확인

### 인수조건
1. test:coverage Statements ≥ 70%, 종료코드 0
2. test:e2e 2개 passed
3. git grep "admin:admin" 0건

---
## Day 19 세부 계획 — CI 파이프라인 통합 (2026-03-29)

### 목표
dashboard 단위 테스트 70% 충족 확인 + Java 통합 테스트 자격증명 보안 수정
+ frontend-ci 잡 추가로 PR 시 전체 CI 그린 달성.

### 질문에 대한 답
1. E2E CI 전략 — MSW global-setup vs E2E 잡 분리? => global-setup 별도 작업 없이 현재 구조 그대로 MSW mock 전용 E2E 가능.
2. IT_ADMIN_CREDENTIALS — GitHub secret 이미 등록되어 있는가? => 미등록. Day 19에서 신규 추가 필요.
3. CbomMetricsTest / EncryptControllerTest 모듈 위치? => crypto-engine과 무관. api-gateway 모듈 단독. CI unit-test 잡은 현재 crypto-engine/tests/test_algorithm_strategy.py (Python pytest)만 실행. ./gradlew test (Java)를 실행하는 별도 잡이 없음 → Day 19에서 api-gateway-unit-test 잡 신규 추가 필요.

### Step A. dashboard 단위 테스트 보완
- A-1: axiosClient401.test.ts — Authorization 헤더 검증 케이스 추가
- A-2: npm run test:coverage 실행 → lines/functions/branches ≥ 70% 확인
  (실패 시 InventoryPage/useInventory uncovered 분기 최소 케이스 추가)

### Step B. Java 통합 테스트 보안·경계값 수정
- B-1: MonitoringIT.java:40 — System.getenv("IT_ADMIN_CREDENTIALS") 치환
- B-2: CbomMetricsTest — null/""/UNKNOWN riskLevel 3케이스 추가
- B-3: EncryptControllerTest — LOW/MEDIUM never() 검증 추가

### Step C. frontend-ci 잡 추가 (ci.yml)
- C-1: Node 20 + cache → lint → test:coverage → build 잡 추가
- C-2: E2E 전략 확정 (MSW global-setup or 분리 잡)
- C-3: Trivy 프론트엔드 이미지 스캔 (Dockerfile 존재 시)

### 인수조건
1. test:coverage lines ≥ 70%, 종료코드 0
2. git grep "admin:admin" 0건
3. frontend-ci 잡 PR 시 green

---

## Day 20 세부 계획 — Phase 3 최종 검증 & 문서화 (2026-03-30)

### 목표
Phase 3 전체 인수조건 3개를 자동·수동 테스트로 검증하고, 프로젝트 재현 가능성을 확인한 뒤 최종 문서를 갱신한다.

### 질문에 대한 답
1. make test-dct — 스택 기동 없이 단독 실행 가능한가? => 완전 오프라인/스택 불필요. 단, Sigstore 네트워크 연결은 필요.
전제조건: cosign 설치(make setup 경유) + Sigstore Transparency Log(rekor.sigstore.dev) 네트워크 접근
2. 환경변수 교체 기동 테스트 범위 => docker compose ps 상태 확인 + /actuator/health 200 응답이 최소 범위. curl /api/encrypt는 불포함.
```
Day 20 환경변수 교체 테스트 최소 절차:
cp .env.example .env.staging   # 호스트명/포트 교체
docker compose --env-file .env.staging up -d
docker compose ps              # State: running
curl http://localhost:8081/actuator/health  # {"status":"UP"}
```
3. docs 업데이트 범위 — ADR 0001-frontend-state-management.md 존재 여부
결론: 파일 존재 확인됨. Day 20 범위에 포함 불필요.
- docs/adr/0001-frontend-state-management.md 파일이 실제로 존재하고 내용 완성됨 (승인 상태, 2026-03-20)
- TanStack Query + Zustand 채택 근거와 결과까지 작성된 완성 문서
Day 20 docs 업데이트 실제 최소 범위:
|파일|작업|
|:--|:--|
|docs/plan.md|Day 20 인수조건 체크리스트 체크|
|README.md|Phase 3 완료 상태 반영|
|docs/review.md|최종 리뷰 항목 append (reviewer.md 규칙 준수)|
|docs/adr/0001-*.md|이미 완성 — 추가 작업 불필요|

### Step A — Phase 3 인수조건 체크리스트 실행 (자동화 검증)
- A-1: make up → curl POST /api/encrypt → curl GET /api/cbom → curl POST /api/cbom/manual (HTTP 200, DB row 존재)
- A-2: make test-dct → trivy image --exit-code 1 --severity CRITICAL → curl /api/cbom (헤더 없음 → 403)
- A-3: .env.example 복사 후 HOST 변경 → docker-compose up → POST /actuator/algorithm → GET /api/cbom/history (CBOM history 1건)
- 체크리스트 결과를 docs/review.md 하단에 날짜·항목별 ✅/❌ 형태로 기록

### Step B — 환경변수 교체 기동 테스트
- .env.example → .env.test 복사, DB_HOST/GATEWAY_HOST/CRYPTO_ENGINE_HOST 127.0.0.1 명시
- docker-compose --env-file .env.test up -d → /actuator/health smoke-test → down 후 기본 .env 복원

### Step C — 최종 문서화
- docs/plan.md Day 20 인수조건 3개 - [x] 체크
- docs/adr/0001-frontend-state-management.md 존재 확인 (미존재 시 stub 작성)
- README.md Quick Start에 make up && make test-dct 커맨드 기재 여부 확인
- docs/review.md Day 20 최종 항목 Append

### 인수조건
1. make test-dct 종료코드 0, Cosign verify 전 이미지 통과 로그 출력
2. docker-compose --env-file .env.test up -d 후 모든 서비스 Up + /actuator/health HTTP 200
3. docs/plan.md Phase 3 인수조건 3개 전부 - [x] 체크, docs/review.md Day 20 항목 Append 완료