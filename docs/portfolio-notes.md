# PQC Security Project — 성능 포트폴리오 노트

## Feign HTTP 클라이언트 교체 — HttpURLConnection → OkHttpClient

**측정 환경**
- api-gateway: Spring Boot 3.4.4 / Java 21
- crypto-engine: FastAPI + ML-DSA-65 (liboqs)
- 측정 지표: `[AUTH-LATENCY]` 로그 (`System.nanoTime()` 기반)
- 측정 횟수: 100회 반복 로그인 요청

---

#### ML-DSA 서명 처리 시간 (1000회 순차시도)
cold: avg=150ms
warm: avg=4.45ms  p50=2.00ms  p95=2.00ms  p99=2.00ms  max=79.00ms  n=999

### Before — HttpURLConnection (기본값)

| 지표 | 수치 |
|---|---|
| cold (1회차) | 150 ms |
| warm p50 | 3 ms |
| warm p95 | - ms |
| warm p99 | - ms |
| 평균 (100회) | - ms |

> 측정 명령어:
> ```bash
> docker-compose logs api-gateway | grep AUTH-LATENCY \
>   | awk -F'latency=' '{sum+=$2; count++} END {print "avg="sum/count"ms, n="count}'
> ```

---

### After — OkHttpClient
#### maxIdle=5, keepAlive=30s
avg=12023.82ms  p50=10805.00ms  p95=10805.00ms  p99=10805.00ms  max=13012.00ms  n=95

#### maxIdle=20, keepAlive=60s

| 지표 | 수치 |
|---|---|
| cold (1회차) | 202 ms |
| warm p50 | 3 ms |
| warm p95 | 2 ms |
| warm p99 | 3 ms |
| 평균 (100회) | 10.9 ms |

---

#### maxIdle=1, keepAlive=10s

| 지표 | 수치 |
|---|---|
| cold (1회차) | 112 ms |
| warm p50 | 3 ms |
| warm p95 | 2 ms |
| warm p99 | 3 ms |
| 평균 (100회) | 10.24 ms |

---

### 결론

| 항목 | Before | After | 개선율 |
|---|---|---|---|
| warm 평균 | - ms | - ms | - % |
| cold | 190 ms | - ms | - % |

---

## 다음 단계 (예정)

- [ ] 시나리오 B (maxIdle=20) 부하테스트 비교
- [ ] 시나리오 C (maxIdle=1) 부하테스트 비교
- [ ] gRPC 전환 (Day 5 이후)
