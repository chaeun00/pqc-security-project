# ADR 0001: 프론트엔드 상태 관리 — TanStack Query + Zustand 채택

- **날짜:** 2026-03-20
- **상태:** 승인(Accepted)

## 컨텍스트

Phase 3에서 React 기반 대시보드를 신규 구현한다.
서버 상태(Server State)와 클라이언트 UI 상태(Client State)를 명확히 분리해야 하며,
PQC 암호화 결과·CBOM 목록 등 비동기 API 데이터를 효율적으로 캐싱·갱신해야 한다.

## 결정

| 구분 | 채택 | 탈락 후보 |
|------|------|-----------|
| 서버 상태 | **TanStack Query v5** | SWR |
| 클라이언트 UI 상태 | **Zustand v4** | Redux Toolkit |

## 근거

### TanStack Query vs SWR

| 항목 | TanStack Query | SWR |
|------|---------------|-----|
| 캐시 무효화 제어 | 세밀한 `invalidateQueries` API | 단순 key 기반 |
| Mutation 처리 | `useMutation` + optimistic update 내장 | 수동 구현 필요 |
| Devtools | 독립 패키지로 시각화 지원 | 없음 |
| 학습 곡선 | 중간 | 낮음 |
| 결정 이유 | CBOM 업데이트 후 즉시 refetch가 필요한 mutation 패턴에 최적 | - |

### Zustand vs Redux Toolkit

| 항목 | Zustand | Redux Toolkit |
|------|---------|---------------|
| 보일러플레이트 | 최소 (slice 불필요) | 상대적으로 많음 |
| 번들 크기 | ~1 KB | ~10 KB |
| Devtools | Redux DevTools 연동 가능 | 기본 지원 |
| 결정 이유 | 대시보드 UI 상태(선택된 알고리즘, 모달 열림 여부 등) 범위가 좁아 경량 솔루션으로 충분 | - |

## 결과

- `crypto-dashboard/` 초기화 시 `vite`, `react`, `typescript`, `@tanstack/react-query`, `zustand` 의존성 포함
- 서버 데이터는 TanStack Query hook으로만 접근; Zustand는 UI 전용 상태만 관리
- 관련 계획: [docs/phase3-scope.md](../phase3-scope.md)
