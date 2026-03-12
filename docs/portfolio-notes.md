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
"OkHttp 도입 이유 = 커넥션 풀 명시적 관리 + 재시작/장애 후 첫 요청 latency 안정화
프로덕션 확장을 고려한 커넥션 풀 명시적 관리 도입. 현재 트래픽 수준에서는 성능 차이 미미하며, 고동시성 시나리오 대비 설계
초당 수백 요청 → TCP 연결 비용 누적, 외부 API 호출 (인터넷, 고지연), 연결 풀 고갈로 대기 발생에서 keep-alive가 효과를 봄.

결국 현 프로젝트에서는 "OkHttp 도입보다 gunicorn worker 수 조정이 동시 처리량에 직접적인 영향을 미침을 실험으로 확인"

maxIdleConnections=5	api-gateway (클라이언트)	crypto-engine과의 TCP 연결을 최대 5개 유지 대기
keepAliveDuration=30s	api-gateway (클라이언트)	유휴 연결을 30초간 재사용, 이후 끊음
gunicorn --workers N	crypto-engine (서버)	동시에 서명 요청을 처리할 수 있는 프로세스 수

nproc(코어 개수)값이 28임을 확인. 대시보드, db, 게이트웨이가 코어를 크게 잡아먹지 않는다고 가정한 우리 프로젝트에서 crypto-engine 코어리밋 20언저리까진 테스트 가능.
가용메모리는 14190.4 MB.

--preload ON (메모리 증가 미미)

GUNICORN_WORKERS=1
seq 20 기준 crypto-engine 호출 평균 대기시간 avg=215.35ms n=20
각 처리에 평균적으로 30ms 소요
메모리 사용량: 54.12MiB / 512MiB

GUNICORN_WORKERS=2
seq 20 기준 crypto-engine 호출 평균 대기시간 avg=197.85ms n=20
메모리 사용량: 55.14MiB / 512MiB

GUNICORN_WORKERS=4
seq 20 기준 crypto-engine 호출 평균 대기시간 avg=200.4ms n=20
메모리 사용량: 55.35MiB / 512MiB

"ML-DSA-65는 CPU 바운드 연산. CPU_LIMIT=1.0 환경에서 workers 수 증가는 처리량 개선 효과 미미 (215ms→198ms). 실질적 개선을 위해서는 CPU 할당 증가
(CRYPTO_ENGINE_CPU_LIMIT=2.0)가 필요."

---

CRYPTO_ENGINE_CPU_LIMIT=2.0

GUNICORN_WORKERS=2
seq 20 기준 crypto-engine 호출 평균 대기시간 avg=144.75ms n=20
메모리 사용량: 56.54MiB / 512MiB

GUNICORN_WORKERS=4
seq 20 기준 crypto-engine 호출 평균 대기시간 avg=123ms n=20
메모리 사용량: 57.13MiB / 512MiB

GUNICORN_WORKERS=8
seq 20 기준 crypto-engine 호출 평균 대기시간 avg=222.6ms n=20
메모리 사용량: 57.01MiB / 512MiB

---

CRYPTO_ENGINE_CPU_LIMIT=4.0

GUNICORN_WORKERS=4
seq 20 기준 crypto-engine 호출 평균 대기시간 avg=93.2ms n=20
메모리 사용량: 60.06MiB / 512MiB

GUNICORN_WORKERS=8
seq 20 기준 crypto-engine 호출 평균 대기시간 avg=75.5ms n=20
메모리 사용량: 59.83MiB / 512MiB

---

CRYPTO_ENGINE_CPU_LIMIT=8.0 ("CPU 제한을 높여 진정한 병렬 실행이 가능해질수록 --preload의 CoW 공유 이점이 감소하고 worker당 메모리 오버헤드가 드러남. workers × ~10MiB 선형 증가 패턴 확인.")

GUNICORN_WORKERS=8
seq 20 기준 crypto-engine 호출 평균 대기시간 avg=45.55ms n=20
메모리 사용량: 114.2MiB / 512MiB

GUNICORN_WORKERS=16
seq 20 기준 crypto-engine 호출 평균 대기시간 avg=49.15ms n=20
메모리 사용량: 193MiB / 512MiB

---

CRYPTO_ENGINE_CPU_LIMIT=16.0

GUNICORN_WORKERS=16
seq 20 기준 crypto-engine 호출 평균 대기시간 avg=24.9ms n=20
메모리 사용량: MEM=189.6MiB / 512MiB

---

전체 실험 결과 (유효 데이터 정리):

CPU	workers	avg latency	메모리
1.0	1	215ms	~54MiB
2.0	4	123ms	~57MiB
4.0	4	93ms	~60MiB
4.0	8	75ms	~60MiB
8.0	8	45ms	~114MiB
16.0	16	24.9ms	~190MiB
이 데이터가 말해주는 것:

1. CPU 2배 = latency 약 절반 → ML-DSA 선형 스케일링 확인
2. workers = CPU 코어 수(부터 2배 언저리)가 최적 (과잉 provisioning 시 오히려 증가)
3. 이론적 하한선 ≈ 25ms (20 concurrent 기준)
4. 메모리는 workers × 10MiB 선형 증가 (512MiB 한도 내 안전)

---

"현재 구현은 --preload(이거 덕분에 메모리가 유의미하게 증가하지 않은 것. ML-DSA 키/알고리즘 상태를 master에서 1회 로드 후 fork() 공유. worker마다 복사 없음)로 메모리 효율을 확보했으나, 프로덕션 이식 시에는 ML-DSA 개인키를 HSM(PKCS#11) 또는 AWS KMS Custom Key Store로 이전하여 키 노출 위험을 제거해야 함을 인식하고 있다."

하지만, 현재 포트폴리오에 그것을 적용하기에는 난이도가 높을 뿐더러 시간도 많이 듬. 원래 목적인 파이프라인 확인이 불가능할정도로 규모가 커질 가능성. 개인이 하기에 과하다고 판단. 현재 구현의 한계 (portfolio-notes.md에 기록):
개인키가 프로세스 메모리에 존재 → 프로덕션 금지
해결책: PKCS#11 인터페이스로 HSM 연동 또는 AWS KMS Custom Key Store
마이그레이션 경로: algorithm_factory.py의 키 로딩 부분만 교체하면 되는 구조로 설계됨 (Algorithm Agility)

결국 최선은 preload on/off 메모리 비교.



--preload OFF (시간의 경우 유의미한 차이가 발생하지 않았음)


---

CRYPTO_ENGINE_CPU_LIMIT=4.0

GUNICORN_WORKERS=4
메모리 사용량: 132.6MiB / 512MiB

GUNICORN_WORKERS=8
메모리 사용량: 239MiB / 512MiB

---

CRYPTO_ENGINE_CPU_LIMIT=8.0

GUNICORN_WORKERS=8
메모리 사용량: 238.9MiB / 512MiB

GUNICORN_WORKERS=16
메모리 사용량: 448MiB / 512MiB

---

CRYPTO_ENGINE_CPU_LIMIT=16.0

GUNICORN_WORKERS=16
메모리 사용량: MEM=447.3MiB / 512MiB

---


---

## JwtKeyCache — 기술 부채 (스케일아웃 시 Redis 교체 필요)

현재 `JwtKeyCache`는 단일 JVM 인메모리(`ConcurrentHashMap`)로 구현되어 있다.
api-gateway 인스턴스가 2개 이상으로 스케일아웃되면 **인스턴스 간 캐시 불일치**가 발생하여 유효한 JWT가 거부될 수 있다.

- **해결책:** `ConcurrentHashMap` → Redis(또는 Redis Cluster)로 교체. TTL을 JWT `exp`와 동일하게 설정하면 만료 엔트리 자동 삭제도 지원됨.
- **마이그레이션 경로:** `JwtKeyCache.put/get` 인터페이스만 유지한 채 내부 구현을 `RedisTemplate`으로 교체하면 되는 구조로 설계됨.
- **우선순위:** 단일 인스턴스 데모 범위에서는 현행 유지 가능. 수평 확장 전 반드시 교체 필요.

---

=> 결론. 프로덕션에서 --preload가 제거된다고 가정했을 때, 가용 메모리는 비교적 넉넉하지만 코어 개수가 28로 제한된다는 점에서 crypto-engine의 가용 코어는 최대 16개 언저리. 또한  gunicorn_workers를 무작정 높인다고 성능을 좋아지는게 아니라, 코어개수의 *2 이하에서 높 성능을 뽑아낸다는 점을 보았을 때, crypto-engine의 가용메모리는 적어도 1gb 이상이어야함. workers를 최대 성능을 고려하여 끌어올린 수치가 32일때 1gb에 근접하기 때문.