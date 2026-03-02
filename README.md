# PQC 하이브리드 게이트웨이 + CBOM 대시보드

> Post-Quantum Cryptography(PQC) 기반 하이브리드 API 게이트웨이와 암호 자산 관리(CBOM) 대시보드 — 핀테크 포트폴리오 프로젝트

## 개요

ML-KEM(암호화)·ML-DSA(서명) 알고리즘을 활용한 양자내성 게이트웨이와,
암호화 자산 현황을 시각화하는 CBOM 대시보드를 4-레이어 모노레포로 구성합니다.

| 레이어 | 기술 스택 | 역할 |
|---|---|---|
| `crypto-engine` | Python FastAPI + liboqs | ML-KEM/ML-DSA 엔드포인트 |
| `api-gateway` | Spring Boot | ML-DSA JWT 발급·검증, 하이브리드 라우팅 |
| `dashboard` | React + TanStack Query + Zustand | CBOM 시각화·재고 등록 UI |
| `db` | PostgreSQL | 세션·CBOM 자산·키 메타데이터 |

## 아키텍처

```
Client
  │
  ▼
api-gateway (Spring Boot)   ← ML-DSA JWT 검증
  │   │
  │   └─► crypto-engine (FastAPI/liboqs)  ← ML-KEM 암호화 / ML-DSA 서명
  │
  ▼
PostgreSQL  ◄─ CBOM 자동 로깅 + 수동 재고
  ▲
  │
dashboard (React)  ← CBOM 시각화 / Prometheus·Grafana 연동
```

## 구현 계획

### Phase 1 — 기반 인프라 & Crypto Engine (Week 1–2)
- Docker Compose 네트워크 격리 + 리소스 제한 + K8s-Ready Config 외재화
- FastAPI + liboqs: ML-KEM(암호화), ML-DSA(서명) 엔드포인트 + Trivy 스캐닝
- PostgreSQL 스키마: users/sessions(정규화) + cbom_assets/key_metadata(JSONB) + VACUUM 정책

### Phase 2 — API Gateway Spring Boot (Week 3–4)
- ML-DSA JWT 발급·검증 필터 (Python `/sign`, `/verify` 호출)
- Feign Client 비동기 호출 + Fallback + Stateless 원칙
- Algorithm Agility 인터페이스 + SNDL High-Risk 우선순위 라우팅

### Phase 3 — React CBOM 대시보드 (Week 5–6)
- TanStack Query + Zustand 상태 관리
- CBOM 시각화, 우선순위 뷰, 실시간 모니터링 UI
- 수동 재고(Inventory) 등록 UI (레거시 자산 입력·편집·삭제)
- Prometheus/Grafana 알람 연동

## 디렉터리 구조

```
pqc-security-project/
├── docker-compose.yml
├── .env.example
├── config/                          # K8s ConfigMap/Secret 대체용
│   ├── crypto-engine.env.example
│   ├── api-gateway.env.example
│   └── dashboard.env.example
├── crypto-engine/
│   └── app/
│       ├── routers/                 # /kem, /dsa, /sign, /verify
│       ├── services/
│       └── schemas/
├── api-gateway/
│   └── src/main/java/com/pqc/gateway/
│       ├── jwt/                     # ML-DSA JWT 발급·검증
│       ├── config/
│       ├── controller/
│       ├── service/
│       ├── client/
│       ├── entity/
│       └── repository/
├── dashboard/
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
```

## 인수조건

1. `POST /api/encrypt` 전 구간 정상 동작 + CBOM 자동 기록 + 수동 자산 등록·조회 가능
2. ML-DSA JWT 정상 발급·검증 + Rootless Docker + Trivy Critical 0건 + 미인증 403
3. 환경변수 교체만으로 다른 호스트 기동 가능 + 알고리즘 전환 시 CBOM 이력 반영

## 인프라 결정사항

| 항목 | 결정 |
|---|---|
| 컨테이너 | Docker Compose + K8s-Ready (Stateless 설계, 환경변수 외재화) |
| JWT | Spring Boot 직접 발급, ML-DSA 서명을 Python 엔진 호출로 이식 |
| CBOM | 자동 로깅(Primary) + 수동 재고 등록(Secondary) 하이브리드 |

## 빠른 시작 (Phase 1에서 작성 예정)

```bash
# 환경변수 설정
cp .env.example .env

# 서비스 실행
docker compose up -d

# 헬스체크
curl http://localhost:8080/actuator/health
curl http://localhost:8000/health
```

## 문서

- [`docs/plan.md`](docs/plan.md) — 구현 계획 (rev.1)
