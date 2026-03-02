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


(수정 대상)
## Step 1-B. Crypto Engine 컨테이너 세부 계획
- Cosign에 Trivy 결합. trivy까지 통과해야 이미지 다운 가능. 해당 기능 이미지 다운시 적재.
- USER nonroot 전략
- Task A: 전 구간 docker compose up -d 검증
- Task B: docker stats 전체 컨테이너 Resource Limit 확인