# Phase 3 진입 범위 정의

- **작성일:** 2026-03-20
- **전제조건:** Phase 2 완료 (phase2-complete gate green)

## 목표

Vite + React 18 + TypeScript 기반 PQC 보안 대시보드(`crypto-dashboard/`)를 신규 구현하여
암호화 API·CBOM 현황·알고리즘 전환 이력을 시각화한다.

## 기술 스택

| 역할 | 라이브러리 | 선택 근거 |
|------|-----------|-----------|
| 번들러 | Vite 5 | 빠른 HMR, ESM 네이티브 |
| UI 프레임워크 | React 18 | 동시성 모드, Suspense |
| 언어 | TypeScript 5 | 타입 안전성 |
| 서버 상태 | TanStack Query v5 | [ADR 0001](adr/0001-frontend-state-management.md) |
| 클라이언트 상태 | Zustand v4 | [ADR 0001](adr/0001-frontend-state-management.md) |
| 스타일 | Tailwind CSS v3 | 유틸리티 우선, 빠른 프로토타이핑 |

## 초기화 범위 (Day 11 작업)

1. `crypto-dashboard/` 디렉토리 생성 (Vite scaffold)
   ```
   npm create vite@latest crypto-dashboard -- --template react-ts
   ```
2. 의존성 추가
   ```
   @tanstack/react-query @tanstack/react-query-devtools zustand
   tailwindcss postcss autoprefixer
   ```
3. API 클라이언트 레이어 (`src/api/`) 초기 구조 정의
   - `src/api/auth.ts` — 로그인 / JWT 갱신
   - `src/api/encrypt.ts` — `/api/encrypt`, `/kem/decrypt`
   - `src/api/cbom.ts` — CBOM 목록 조회 (Phase 3 신규 엔드포인트)
4. QueryClient 설정 + ZustandStore 초기 정의
5. 페이지 라우팅 골격 (`react-router-dom v6`)
   - `/login` — 인증
   - `/dashboard` — 암호화 상태 개요
   - `/cbom` — CBOM 자산 목록

## 인수조건 (Phase 3 Day 11)

1. `crypto-dashboard/` Vite dev server 정상 기동 (`npm run dev`)
2. `/api/encrypt` 호출 결과를 TanStack Query로 표시하는 최소 컴포넌트 1개 존재
3. Zustand store에 `selectedAlgorithm` 상태 초기 정의 완료

## Hot-swap 구현 현황 (Day 10 완료)

Runtime API 알고리즘 전환(재기동 없음)은 **Phase 2에서 구현 완료**됐다.

- **구현 내용:** `AlgorithmHotSwapService` (AtomicReference 기반) + `AlgorithmAdminEndpoint`
  (`POST /actuator/algorithm`, 관리 포트 8081) — api-gateway 재기동 없이 KEM/DSA 전환
- **보안:** whitelist 검증(ML-KEM-512/768/1024, ML-DSA-44/65/87 외 → 400),
  포트 8081 docker-compose 미게시(네트워크 격리)
- **CI 검증:** `algorithm-agility-test` 잡에 hot-swap 3-step 포함
  (전환 → `/api/encrypt` algorithm 확인 → 불허용값 400)
- **Phase 3 추가 범위:** crypto-engine 측 `PATCH /kem/algorithm` API 연동 및
  대시보드 UI 알고리즘 전환 화면 (Day 13–14 예정)
