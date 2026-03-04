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
1. db trivy 실패 시 전체 차단 여부 — postgres:16-alpine Trivy Critical 발견 시 나머지 서비스 빌드를 막을지, 경고만 출력하고 계속 진행할지?
2. build-secure 실행 순서 — db → crypto-engine 순서(현재 compose depends_on과 일치)로 고정할지, 병렬($(MAKE) -j)로 실행할지?
