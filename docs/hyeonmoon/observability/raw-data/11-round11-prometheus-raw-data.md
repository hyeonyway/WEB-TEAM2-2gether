# 11차 부하테스트 — Prometheus 원시 집계 데이터

이 문서는 6개 시나리오 각각의 실제 실행 시각을 기준으로 stage를 재구성하고, 각 구간 끝 시점에 `histogram_quantile`/`increase`/`avg_over_time` 등 Prometheus 순시 쿼리를 평가해 만든 원시 집계표다. 10차와 동일하게 stage 오프셋은 실측 로그 관례(`pure-throughput`/`bid-only-load`: 실행 시작 +36s(SSE 있음)/+6s(SSE 없음), `hot-auction-pattern`: +70s, 각 구간 길이는 스크립트 기본값)를 그대로 적용했다 — 이번 회차는 k6 실행 로그 원문이 보존되지 않아(에이전트 실행 환경 스크래치, 요약 로그만 보고서 작성자에게 전달됨), 10차에서 이미 실측으로 검증된 오프셋 관례와 스크립트(`backend/src/test/k6/scenarios/*.js`, 10차 이후 미변경 확인)의 스테이지 길이 설정을 그대로 적용해 역산했다 — 완전한 실측은 아니라 근사치다(한계 섹션 참고).

모든 HTTP 히스토그램 쿼리는 `job="backend-spring"` 라벨로 스코프했다 — 이 라벨은 blue/green 두 타겟(9091/9092)이 `instance="backend"`로 동일하게 잡혀 있어 활성 컬러가 자동으로 합산된다(둘 중 하나만 값을 내므로 실질적으로 현재 서비스 중인 컬러의 값과 같다 — 본문 한계 섹션 참고). 요청수가 사실상 0인 method/uri/status 조합은 표에서 생략했다(각 표 하단에 생략 건수 명시) — 10차 raw-data는 전량 나열했지만 11차는 stage 수·API 수가 같아 표가 과도하게 길어지는 걸 막기 위해 0건 라인만 접었다. 완전한 원본이 필요하면 `round11_data.json`(에이전트 스크래치)의 재현 커맨드를 본문 방법론대로 다시 실행하면 된다.

## 실행 목록

| 결과 파일 | 시나리오 | 실제 실행 (UTC) |
|---|---|---|
| [`round11-pure250-20260819.json`](../../../../backend/src/test/k6/result/round11-pure250-20260819.json) | pure-throughput (SSE_VUS=250) | 2026-08-18T17:50:42Z ~ 2026-08-18T18:04:25Z |
| [`round11-pure500-20260819.json`](../../../../backend/src/test/k6/result/round11-pure500-20260819.json) | pure-throughput (SSE_VUS=500) | 2026-08-18T18:13:32Z ~ 2026-08-18T18:27:16Z |
| [`round11-pure1000-20260819.json`](../../../../backend/src/test/k6/result/round11-pure1000-20260819.json) | pure-throughput (SSE_VUS=1000) | 2026-08-18T18:35:41Z ~ 2026-08-18T18:49:30Z |
| [`round11-hotauction-20260819.json`](../../../../backend/src/test/k6/result/round11-hotauction-20260819.json) | hot-auction-pattern | 2026-08-18T18:57:52Z ~ 2026-08-18T19:05:58Z |
| [`round11-bidonly-20260819.json`](../../../../backend/src/test/k6/result/round11-bidonly-20260819.json) | bid-only-load (SSE 없음, 분산) | 2026-08-18T19:13:40Z ~ 2026-08-18T19:25:55Z |
| [`round11-bidonly-hot-20260819.json`](../../../../backend/src/test/k6/result/round11-bidonly-hot-20260819.json) | bid-only-load (SSE 없음, 핫경매집중 HOT_AUCTION_ID=3001001) | 2026-08-18T19:36:23Z ~ 2026-08-18T19:48:36Z |

---

## round11-pure250-20260819.json

- 시나리오: `pure-throughput (SSE_VUS=250)`
- K6 실행: 2026-08-18T17:50:42Z ~ 2026-08-18T18:04:25Z

### QPS50 — 2026-08-18T17:51:18+00:00 ~ 2026-08-18T17:53:18+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 3,602.3 | 55.99 | 83.83 | 96.66 |
| GET /api/auctions/{auctionId} [200] | 1,144.0 | 32.43 | 47.13 | 72.67 |
| GET /api/auctions [200] | 1,258.3 | 71.55 | 104.22 | 121.26 |
| POST /api/auctions/{auctionId}/bids [201] | 1,196.6 | 55.82 | 81.28 | 89.02 |
| POST /api/auctions/{auctionId}/bids [400] | 2.3 | 57.22 | 66.55 | 67.00 |
| POST /api/auctions/{auctionId}/bids [409] | 2.3 | 40.79 | 44.21 | 44.63 |

(요청수 0 조합 26/32건 생략)

### QPS100 — 2026-08-18T17:53:18+00:00 ~ 2026-08-18T17:55:18+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 5,323.4 | 77.86 | 94.85 | 109.85 |
| GET /api/auctions/{auctionId} [200] | 1,828.6 | 52.70 | 61.12 | 66.15 |
| GET /api/auctions [200] | 1,720.0 | 100.60 | 132.21 | 166.01 |
| POST /api/auctions/{auctionId}/bids [201] | 1,755.4 | 77.12 | 90.04 | 103.93 |
| POST /api/auctions/{auctionId}/bids [400] | 13.7 | 60.43 | 76.06 | 86.79 |
| POST /api/auctions/{auctionId}/bids [409] | 4.6 | 55.47 | 60.96 | 61.40 |

(요청수 0 조합 26/32건 생략)

### QPS150 — 2026-08-18T17:55:18+00:00 ~ 2026-08-18T17:57:18+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 8,921.1 | 93.16 | 126.75 | 205.61 |
| GET /api/auctions/{auctionId} [200] | 2,977.1 | 59.75 | 67.93 | 86.00 |
| GET /api/auctions [200] | 2,969.1 | 129.02 | 177.96 | 281.12 |
| POST /api/auctions/{auctionId}/bids [201] | 2,886.9 | 91.92 | 125.15 | 169.93 |
| POST /api/auctions/{auctionId}/bids [400] | 70.9 | 71.92 | 98.95 | 165.09 |
| POST /api/auctions/{auctionId}/bids [409] | 13.7 | 68.93 | 104.74 | 110.43 |

(요청수 0 조합 26/32건 생략)

### QPS200 — 2026-08-18T17:57:18+00:00 ~ 2026-08-18T17:59:18+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 12,522.3 | 119.24 | 210.74 | 260.69 |
| GET /api/auctions/{auctionId} [200] | 4,214.9 | 68.55 | 98.96 | 132.37 |
| GET /api/auctions [200] | 4,133.7 | 172.92 | 270.86 | 332.53 |
| POST /api/auctions/{auctionId}/bids [201] | 3,987.4 | 116.72 | 202.23 | 253.38 |
| POST /api/auctions/{auctionId}/bids [400] | 168.0 | 99.69 | 171.45 | 207.25 |
| POST /api/auctions/{auctionId}/bids [409] | 18.3 | 87.54 | 161.06 | 175.38 |

(요청수 0 조합 26/32건 생략)

### QPS300 — 2026-08-18T17:59:18+00:00 ~ 2026-08-18T18:01:18+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 16,955.4 | 174.03 | 240.20 | 312.85 |
| GET /api/auctions/{auctionId} [200] | 5,581.7 | 117.70 | 169.55 | 221.55 |
| GET /api/auctions [200] | 5,754.3 | 211.80 | 295.76 | 373.23 |
| POST /api/auctions/{auctionId}/bids [201] | 3,449.1 | 174.24 | 241.32 | 295.60 |
| POST /api/auctions/{auctionId}/bids [400] | 1,965.7 | 133.97 | 186.51 | 216.69 |
| POST /api/auctions/{auctionId}/bids [409] | 27.4 | 144.89 | 232.64 | 243.38 |

(요청수 0 조합 26/32건 생략)

### QPS400 — 2026-08-18T18:01:18+00:00 ~ 2026-08-18T18:03:18+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /** [404] | 1.1 | 15.84 | 16.71 | 16.76 |
| GET /api/auctions/{auctionId}/bid-context [200] | 19,891.4 | 157.59 | 209.54 | 241.59 |
| GET /api/auctions/{auctionId} [200] | 6,540.6 | 110.52 | 151.13 | 177.44 |
| GET /api/auctions [200] | 6,862.9 | 190.86 | 256.00 | 298.54 |
| POST /api/auctions/{auctionId}/bids [201] | 1,372.6 | 162.69 | 217.71 | 244.74 |
| POST /api/auctions/{auctionId}/bids [400] | 4,738.3 | 122.21 | 165.78 | 197.76 |
| POST /api/auctions/{auctionId}/bids [409] | 27.4 | 127.70 | 155.69 | 263.07 |

(요청수 0 조합 25/32건 생략)

### 전체 구간(scenario full window) 요약

#### GET bid-context 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=200 | 71,463.32 |

#### POST bids 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=400 | 8,179.27 |
| status=409 | 105.59 |
| status=201 | 15,367.84 |

#### HikariCP (avg/max active, idle avg, pending avg/max, timeout delta 순서로 나열)

| 라벨 | 값 |
|---|---:|
| pool=HikariPool-1 | 4.13 |
| pool=HikariPool-1 | 10.00 |
| pool=HikariPool-1 | 25.85 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |

#### JVM GC pause count/sum delta (action/cause/gc, count 항목들 다음에 sum(초) 항목)

| 라벨 | 값 |
|---|---:|
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 420.30 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 26.92 |
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 5.18 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 0.27 |

#### JVM 스레드/CPU/힙 committed 최댓값(MB)

| 라벨 | 값 |
|---|---:|
| (no labels) | 121.69 |
| (no labels) | 131.00 |
| (no labels) | 0.53 |
| (no labels) | 0.82 |
| (no labels) | 476.00 |

#### Tomcat 커넥터 스레드 (busy avg/max, current avg 순서)

| 라벨 | 값 |
|---|---:|
| connector=main | 22.46 |
| connector=management | 1.00 |
| connector=main | 50.00 |
| connector=management | 1.00 |
| connector=main | 42.22 |
| connector=management | 30.00 |

#### SSE 커넥션 수 (avg, max 순서)

| 라벨 | 값 |
|---|---:|
| stream=auction | 247.13 |
| stream=me | 246.13 |
| stream=auction | 251.00 |
| stream=me | 250.00 |

#### sse_broadcast_saturated_total delta

| 라벨 | 값 |
|---|---:|
| (데이터 없음) | N/A |

#### dbidding_me_sse_send_failures_total delta

| 라벨 | 값 |
|---|---:|
| (no labels) | 0.00 |

#### Redis (up, 커넥션 avg, 메모리 avg/max MB, hit/miss delta, evicted/expired delta 순서)

| 라벨 | 값 |
|---|---:|
| instance=redis | 1.00 |
| instance=redis | 7.37 |
| instance=redis | 15.50 |
| instance=redis | 21.75 |
| instance=redis | 13,732,558.33 |
| instance=redis | 81,484.25 |
| instance=redis | 0.00 |
| instance=redis | 0.00 |

#### 노드 load1 avg / CPU 사용률 avg (backend/mysql/redis/monitoring)

| 라벨 | 값 |
|---|---:|
| instance=backend | 4.03 |
| instance=monitoring | 0.04 |
| instance=mysql | 0.86 |
| instance=redis | 1.29 |
| instance=backend | 0.27 |
| instance=monitoring | 0.02 |
| instance=mysql | 0.14 |
| instance=redis | 0.22 |

#### 각 호스트 exporter 프로세스 RSS avg(MB) — 참고용, 애플리케이션 RSS 아님

| 라벨 | 값 |
|---|---:|
| instance=backend, job=backend-node | 15.31 |
| instance=monitoring, job=monitoring-node | 14.96 |
| instance=monitoring-prometheus, job=prometheus | 108.39 |
| instance=mysql, job=mysql-exporter | 15.60 |
| instance=mysql, job=mysql-node | 21.68 |
| instance=redis, job=redis-exporter | 16.04 |
| instance=redis, job=redis-node | 19.51 |

#### major page fault delta

| 라벨 | 값 |
|---|---:|
| instance=backend | 4,270.46 |
| instance=monitoring | 1,063.80 |
| instance=mysql | 621.82 |
| instance=redis | 0.00 |

#### SwapFree 시작(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,675.21 |
| instance=monitoring | 2,757.18 |
| instance=mysql | 2,439.46 |
| instance=redis | 0.00 |
#### SwapFree 종료(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,643.31 |
| instance=monitoring | 2,757.45 |
| instance=mysql | 2,438.44 |
| instance=redis | 0.00 |
#### pswpin avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 5.28 |
| instance=monitoring | 1.45 |
| instance=mysql | 2.22 |
| instance=redis | 0.00 |
#### pswpout avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 6.33 |
| instance=monitoring | 0.36 |
| instance=mysql | 0.45 |
| instance=redis | 0.00 |

#### MySQL (row lock waits delta, row lock time delta(ms), threads connected/running avg, slow queries delta, up 순서)

| 라벨 | 값 |
|---|---:|
| instance=mysql | 1,482.42 |
| instance=mysql | 2,715.90 |
| instance=mysql | 31.00 |
| instance=mysql | 2.31 |
| instance=mysql | 0.00 |
| instance=mysql | 1.00 |

#### Redis Stream `event:timeline` group lag 궤적 (`auction-timeline-persistence`, 30초 간격 샘플)

- 시나리오 실행 구간 내 최고 lag: **8,523** (2026-08-18T18:03:12+00:00)
- 최고치 이후 50 미만으로 복귀한 시각: 2026-08-18T18:12:12+00:00 (최고치 대비 +9.0분)

| 시각(UTC) | lag | pending |
|---|---:|---:|
| 2026-08-18T17:50:42+00:00 | 0 | 0 |
| 2026-08-18T17:51:12+00:00 | 0 | 0 |
| 2026-08-18T17:51:42+00:00 | 0 | 0 |
| 2026-08-18T17:52:12+00:00 | 0 | 0 |
| 2026-08-18T17:52:42+00:00 | 0 | 0 |
| 2026-08-18T17:53:12+00:00 | 0 | 0 |
| 2026-08-18T17:53:42+00:00 | 1 | 0 |
| 2026-08-18T17:54:12+00:00 | 35 | 0 |
| 2026-08-18T17:54:42+00:00 | 167 | 0 |
| 2026-08-18T17:55:12+00:00 | 384 | 1 |
| 2026-08-18T17:55:42+00:00 | 682 | 0 |
| 2026-08-18T17:56:12+00:00 | 1,054 | 0 |
| 2026-08-18T17:56:42+00:00 | 1,506 | 0 |
| 2026-08-18T17:57:12+00:00 | 2,048 | 0 |
| 2026-08-18T17:57:42+00:00 | 2,677 | 0 |
| 2026-08-18T17:58:12+00:00 | 3,379 | 0 |
| 2026-08-18T17:58:42+00:00 | 4,158 | 0 |
| 2026-08-18T17:59:12+00:00 | 5,010 | 0 |
| 2026-08-18T17:59:42+00:00 | 5,939 | 0 |
| 2026-08-18T18:00:12+00:00 | 6,774 | 0 |
| 2026-08-18T18:00:42+00:00 | 7,410 | 0 |
| 2026-08-18T18:01:12+00:00 | 7,852 | 0 |
| 2026-08-18T18:01:42+00:00 | 8,157 | 1 |
| 2026-08-18T18:02:12+00:00 | 8,314 | 0 |
| 2026-08-18T18:02:42+00:00 | 8,421 | 1 |
| 2026-08-18T18:03:12+00:00 | 8,523 | 0 |
| 2026-08-18T18:03:42+00:00 | 8,464 | 0 |
| 2026-08-18T18:04:12+00:00 | 7,955 | 0 |
| 2026-08-18T18:04:42+00:00 | 7,435 | 0 |
| 2026-08-18T18:05:12+00:00 | 6,930 | 0 |
| 2026-08-18T18:05:42+00:00 | 6,431 | 0 |
| 2026-08-18T18:06:12+00:00 | 5,943 | 0 |
| 2026-08-18T18:06:42+00:00 | 5,407 | 0 |
| 2026-08-18T18:07:12+00:00 | 4,889 | 0 |
| 2026-08-18T18:07:42+00:00 | 4,361 | 1 |
| 2026-08-18T18:08:12+00:00 | 3,842 | 0 |
| 2026-08-18T18:08:42+00:00 | 3,320 | 0 |
| 2026-08-18T18:09:12+00:00 | 2,798 | 0 |
| 2026-08-18T18:09:42+00:00 | 2,269 | 0 |
| 2026-08-18T18:10:12+00:00 | 1,753 | 0 |
| 2026-08-18T18:10:42+00:00 | 1,240 | 0 |
| 2026-08-18T18:11:12+00:00 | 731 | 0 |
| 2026-08-18T18:11:42+00:00 | 215 | 0 |
| 2026-08-18T18:12:12+00:00 | 0 | 0 |
| 2026-08-18T18:12:42+00:00 | 0 | 0 |
| 2026-08-18T18:13:12+00:00 | 0 | 0 |
| 2026-08-18T18:13:42+00:00 | 0 | 0 |
| 2026-08-18T18:14:12+00:00 | 0 | 0 |
| 2026-08-18T18:14:42+00:00 | 0 | 0 |
| 2026-08-18T18:15:12+00:00 | 0 | 1 |
| 2026-08-18T18:15:42+00:00 | 0 | 0 |
| 2026-08-18T18:16:12+00:00 | 0 | 0 |
| 2026-08-18T18:16:42+00:00 | 2 | 0 |
| 2026-08-18T18:17:12+00:00 | 83 | 0 |
| 2026-08-18T18:17:42+00:00 | 238 | 0 |
| 2026-08-18T18:18:12+00:00 | 485 | 0 |
| 2026-08-18T18:18:42+00:00 | 811 | 0 |
| 2026-08-18T18:19:12+00:00 | 1,227 | 0 |

---

## round11-pure500-20260819.json

- 시나리오: `pure-throughput (SSE_VUS=500)`
- K6 실행: 2026-08-18T18:13:32Z ~ 2026-08-18T18:27:16Z

### QPS50 — 2026-08-18T18:14:08+00:00 ~ 2026-08-18T18:16:08+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /** [404] | 1.1 | 0.76 | 0.95 | 0.99 |
| GET /api/auctions/{auctionId}/bid-context [200] | 3,514.3 | 59.94 | 89.09 | 104.94 |
| GET /api/auctions/{auctionId} [200] | 1,142.9 | 37.74 | 57.85 | 61.34 |
| GET /api/auctions [200] | 1,198.9 | 70.13 | 109.36 | 131.50 |
| POST /api/auctions/{auctionId}/bids [201] | 1,166.9 | 58.35 | 84.08 | 88.77 |
| POST /api/auctions/{auctionId}/bids [409] | 3.4 | 63.28 | 98.42 | 99.68 |

(요청수 0 조합 27/33건 생략)

### QPS100 — 2026-08-18T18:16:08+00:00 ~ 2026-08-18T18:18:08+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/stream [200] | 2.3 | 1,800,886.83 | 30,000.00 | 30,000.00 |
| GET /api/auctions/{auctionId}/bid-context [200] | 5,099.4 | 77.28 | 101.11 | 124.67 |
| GET /api/auctions/{auctionId} [200] | 1,657.1 | 53.34 | 71.92 | 91.06 |
| GET /api/auctions [200] | 1,744.0 | 94.18 | 133.44 | 155.26 |
| OPTIONS /api/auctions [200] | 1.1 | 1.24 | 1.38 | 1.39 |
| POST /api/auctions/{auctionId}/bids [201] | 1,678.9 | 76.25 | 96.63 | 112.38 |
| POST /api/auctions/{auctionId}/bids [400] | 18.3 | 63.94 | 83.51 | 88.29 |
| POST /api/auctions/{auctionId}/bids [409] | 2.3 | 59.46 | 66.55 | 67.00 |

(요청수 0 조합 25/33건 생략)

### QPS150 — 2026-08-18T18:18:08+00:00 ~ 2026-08-18T18:20:08+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 8,696.0 | 92.49 | 126.36 | 169.98 |
| GET /api/auctions/{auctionId} [200] | 2,856.0 | 59.95 | 68.34 | 85.81 |
| GET /api/auctions [200] | 2,940.6 | 121.00 | 171.75 | 220.61 |
| POST /api/auctions/{auctionId}/bids [201] | 2,828.6 | 91.81 | 122.98 | 162.77 |
| POST /api/auctions/{auctionId}/bids [400] | 65.1 | 73.26 | 106.81 | 127.84 |
| POST /api/auctions/{auctionId}/bids [409] | 4.6 | 66.09 | 87.24 | 89.03 |

(요청수 0 조합 27/33건 생략)

### QPS200 — 2026-08-18T18:20:08+00:00 ~ 2026-08-18T18:22:08+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 12,292.6 | 128.34 | 218.58 | 266.53 |
| GET /api/auctions/{auctionId} [200] | 4,083.4 | 76.77 | 127.82 | 168.24 |
| GET /api/auctions [200] | 4,109.7 | 169.45 | 265.53 | 337.63 |
| POST /api/auctions/{auctionId}/bids [201] | 3,905.1 | 126.69 | 214.11 | 262.90 |
| POST /api/auctions/{auctionId}/bids [400] | 177.1 | 102.36 | 179.89 | 211.39 |
| POST /api/auctions/{auctionId}/bids [409] | 10.3 | 92.53 | 129.18 | 133.21 |

(요청수 0 조합 27/33건 생략)

### QPS300 — 2026-08-18T18:22:08+00:00 ~ 2026-08-18T18:24:08+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 16,778.3 | 181.70 | 242.18 | 274.06 |
| GET /api/auctions/{auctionId} [200] | 5,625.1 | 122.55 | 166.41 | 190.67 |
| GET /api/auctions [200] | 5,571.4 | 210.11 | 289.64 | 344.95 |
| POST /api/auctions/{auctionId}/bids [201] | 3,772.6 | 184.78 | 245.20 | 278.95 |
| POST /api/auctions/{auctionId}/bids [400] | 1,659.4 | 136.17 | 178.54 | 203.87 |
| POST /api/auctions/{auctionId}/bids [409] | 11.4 | 135.63 | 212.51 | 221.46 |

(요청수 0 조합 27/33건 생략)

### QPS400 — 2026-08-18T18:24:08+00:00 ~ 2026-08-18T18:26:08+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 20,005.7 | 158.18 | 207.41 | 243.06 |
| GET /api/auctions/{auctionId} [200] | 6,564.6 | 110.78 | 149.87 | 175.90 |
| GET /api/auctions [200] | 6,917.7 | 183.43 | 244.82 | 298.10 |
| POST /api/auctions/{auctionId}/bids [201] | 1,507.4 | 165.68 | 219.70 | 247.88 |
| POST /api/auctions/{auctionId}/bids [400] | 4,627.4 | 122.36 | 162.92 | 193.81 |
| POST /api/auctions/{auctionId}/bids [409] | 8.0 | 121.88 | 148.76 | 155.02 |

(요청수 0 조합 27/33건 생략)

### 전체 구간(scenario full window) 요약

#### GET bid-context 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=200 | 70,285.17 |

#### POST bids 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=400 | 7,924.64 |
| status=409 | 42.73 |
| status=201 | 15,286.73 |

#### HikariCP (avg/max active, idle avg, pending avg/max, timeout delta 순서로 나열)

| 라벨 | 값 |
|---|---:|
| pool=HikariPool-1 | 4.38 |
| pool=HikariPool-1 | 13.00 |
| pool=HikariPool-1 | 25.62 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |

#### JVM GC pause count/sum delta (action/cause/gc, count 항목들 다음에 sum(초) 항목)

| 라벨 | 값 |
|---|---:|
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 277.72 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 24.41 |
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 3.77 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 0.23 |

#### JVM 스레드/CPU/힙 committed 최댓값(MB)

| 라벨 | 값 |
|---|---:|
| (no labels) | 120.80 |
| (no labels) | 131.00 |
| (no labels) | 0.52 |
| (no labels) | 0.78 |
| (no labels) | 518.00 |

#### Tomcat 커넥터 스레드 (busy avg/max, current avg 순서)

| 라벨 | 값 |
|---|---:|
| connector=main | 22.22 |
| connector=management | 1.00 |
| connector=main | 50.00 |
| connector=management | 1.00 |
| connector=main | 41.42 |
| connector=management | 30.00 |

#### SSE 커넥션 수 (avg, max 순서)

| 라벨 | 값 |
|---|---:|
| stream=auction | 488.89 |
| stream=me | 487.89 |
| stream=auction | 501.00 |
| stream=me | 500.00 |

#### sse_broadcast_saturated_total delta

| 라벨 | 값 |
|---|---:|
| (데이터 없음) | N/A |

#### dbidding_me_sse_send_failures_total delta

| 라벨 | 값 |
|---|---:|
| (no labels) | 0.00 |

#### Redis (up, 커넥션 avg, 메모리 avg/max MB, hit/miss delta, evicted/expired delta 순서)

| 라벨 | 값 |
|---|---:|
| instance=redis | 1.00 |
| instance=redis | 6.95 |
| instance=redis | 25.67 |
| instance=redis | 31.70 |
| instance=redis | 13,589,710.13 |
| instance=redis | 89,098.81 |
| instance=redis | 0.00 |
| instance=redis | 510.68 |

#### 노드 load1 avg / CPU 사용률 avg (backend/mysql/redis/monitoring)

| 라벨 | 값 |
|---|---:|
| instance=backend | 4.11 |
| instance=monitoring | 0.07 |
| instance=mysql | 0.60 |
| instance=redis | 1.20 |
| instance=backend | 0.37 |
| instance=monitoring | 0.02 |
| instance=mysql | 0.27 |
| instance=redis | 0.27 |

#### 각 호스트 exporter 프로세스 RSS avg(MB) — 참고용, 애플리케이션 RSS 아님

| 라벨 | 값 |
|---|---:|
| instance=backend, job=backend-node | 15.40 |
| instance=monitoring, job=monitoring-node | 14.81 |
| instance=monitoring-prometheus, job=prometheus | 87.40 |
| instance=mysql, job=mysql-exporter | 15.56 |
| instance=mysql, job=mysql-node | 21.55 |
| instance=redis, job=redis-exporter | 16.10 |
| instance=redis, job=redis-node | 19.66 |

#### major page fault delta

| 라벨 | 값 |
|---|---:|
| instance=backend | 3,272.60 |
| instance=monitoring | 138.35 |
| instance=mysql | 1,407.92 |
| instance=redis | 0.00 |

#### SwapFree 시작(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,643.38 |
| instance=monitoring | 2,757.46 |
| instance=mysql | 2,435.69 |
| instance=redis | 0.00 |
#### SwapFree 종료(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,599.00 |
| instance=monitoring | 2,757.76 |
| instance=mysql | 2,435.69 |
| instance=redis | 0.00 |
#### pswpin avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 7.24 |
| instance=monitoring | 0.10 |
| instance=mysql | 3.04 |
| instance=redis | 0.00 |
#### pswpout avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 11.49 |
| instance=monitoring | 0.00 |
| instance=mysql | 0.00 |
| instance=redis | 0.00 |

#### MySQL (row lock waits delta, row lock time delta(ms), threads connected/running avg, slow queries delta, up 순서)

| 라벨 | 값 |
|---|---:|
| instance=mysql | 862.35 |
| instance=mysql | 6,295.57 |
| instance=mysql | 31.00 |
| instance=mysql | 2.43 |
| instance=mysql | 0.00 |
| instance=mysql | 1.00 |

#### Redis Stream `event:timeline` group lag 궤적 (`auction-timeline-persistence`, 30초 간격 샘플)

- 시나리오 실행 구간 내 최고 lag: **9,191** (2026-08-18T18:26:32+00:00)
- 최고치 이후 50 미만으로 복귀한 시각: 2026-08-18T18:35:32+00:00 (최고치 대비 +9.0분)

| 시각(UTC) | lag | pending |
|---|---:|---:|
| 2026-08-18T18:13:32+00:00 | 0 | 0 |
| 2026-08-18T18:14:02+00:00 | 0 | 0 |
| 2026-08-18T18:14:32+00:00 | 0 | 0 |
| 2026-08-18T18:15:02+00:00 | 0 | 1 |
| 2026-08-18T18:15:32+00:00 | 0 | 1 |
| 2026-08-18T18:16:02+00:00 | 0 | 0 |
| 2026-08-18T18:16:32+00:00 | 0 | 0 |
| 2026-08-18T18:17:02+00:00 | 17 | 1 |
| 2026-08-18T18:17:32+00:00 | 151 | 0 |
| 2026-08-18T18:18:02+00:00 | 353 | 0 |
| 2026-08-18T18:18:32+00:00 | 640 | 0 |
| 2026-08-18T18:19:02+00:00 | 1,008 | 0 |
| 2026-08-18T18:19:32+00:00 | 1,466 | 0 |
| 2026-08-18T18:20:02+00:00 | 2,009 | 0 |
| 2026-08-18T18:20:32+00:00 | 2,642 | 0 |
| 2026-08-18T18:21:02+00:00 | 3,346 | 0 |
| 2026-08-18T18:21:32+00:00 | 4,127 | 1 |
| 2026-08-18T18:22:02+00:00 | 5,022 | 0 |
| 2026-08-18T18:22:32+00:00 | 5,956 | 0 |
| 2026-08-18T18:23:02+00:00 | 6,943 | 0 |
| 2026-08-18T18:23:32+00:00 | 7,684 | 0 |
| 2026-08-18T18:24:02+00:00 | 8,229 | 0 |
| 2026-08-18T18:24:32+00:00 | 8,614 | 0 |
| 2026-08-18T18:25:02+00:00 | 8,867 | 0 |
| 2026-08-18T18:25:32+00:00 | 8,987 | 1 |
| 2026-08-18T18:26:02+00:00 | 9,080 | 0 |
| 2026-08-18T18:26:32+00:00 | 9,191 | 0 |
| 2026-08-18T18:27:02+00:00 | 8,671 | 0 |
| 2026-08-18T18:27:32+00:00 | 8,153 | 0 |
| 2026-08-18T18:28:02+00:00 | 7,634 | 0 |
| 2026-08-18T18:28:32+00:00 | 7,113 | 0 |
| 2026-08-18T18:29:02+00:00 | 6,592 | 0 |
| 2026-08-18T18:29:32+00:00 | 6,071 | 0 |
| 2026-08-18T18:30:02+00:00 | 5,553 | 0 |
| 2026-08-18T18:30:32+00:00 | 5,046 | 0 |
| 2026-08-18T18:31:02+00:00 | 4,523 | 0 |
| 2026-08-18T18:31:32+00:00 | 4,006 | 0 |
| 2026-08-18T18:32:02+00:00 | 3,486 | 1 |
| 2026-08-18T18:32:32+00:00 | 2,972 | 0 |
| 2026-08-18T18:33:02+00:00 | 2,453 | 0 |
| 2026-08-18T18:33:32+00:00 | 1,937 | 0 |
| 2026-08-18T18:34:02+00:00 | 1,421 | 0 |
| 2026-08-18T18:34:32+00:00 | 906 | 0 |
| 2026-08-18T18:35:02+00:00 | 383 | 0 |
| 2026-08-18T18:35:32+00:00 | 0 | 0 |
| 2026-08-18T18:36:02+00:00 | 0 | 0 |
| 2026-08-18T18:36:32+00:00 | 0 | 0 |
| 2026-08-18T18:37:02+00:00 | 0 | 0 |
| 2026-08-18T18:37:32+00:00 | 0 | 0 |
| 2026-08-18T18:38:02+00:00 | 0 | 0 |
| 2026-08-18T18:38:32+00:00 | 0 | 0 |
| 2026-08-18T18:39:02+00:00 | 13 | 0 |
| 2026-08-18T18:39:32+00:00 | 108 | 0 |
| 2026-08-18T18:40:02+00:00 | 297 | 0 |
| 2026-08-18T18:40:32+00:00 | 597 | 0 |
| 2026-08-18T18:41:02+00:00 | 968 | 0 |
| 2026-08-18T18:41:32+00:00 | 1,443 | 0 |
| 2026-08-18T18:42:02+00:00 | 1,993 | 0 |

---

## round11-pure1000-20260819.json

- 시나리오: `pure-throughput (SSE_VUS=1000)`
- K6 실행: 2026-08-18T18:35:41Z ~ 2026-08-18T18:49:30Z

### QPS50 — 2026-08-18T18:36:17+00:00 ~ 2026-08-18T18:38:17+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /** [404] | 1.1 | 36.74 | 38.87 | 39.09 |
| GET /api/auctions/{auctionId}/bid-context [200] | 3,563.4 | 63.61 | 88.55 | 105.45 |
| GET /api/auctions/{auctionId} [200] | 1,142.9 | 42.25 | 61.32 | 75.24 |
| GET /api/auctions [200] | 1,232.0 | 75.39 | 111.73 | 133.81 |
| POST /api/auctions/{auctionId}/bids [201] | 1,179.4 | 61.91 | 86.53 | 92.42 |
| POST /api/auctions/{auctionId}/bids [400] | 3.4 | 57.40 | 61.24 | 61.46 |
| POST /api/auctions/{auctionId}/bids [409] | 3.4 | 50.65 | 55.09 | 55.76 |

(요청수 0 조합 26/33건 생략)

### QPS100 — 2026-08-18T18:38:17+00:00 ~ 2026-08-18T18:40:17+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 5,140.6 | 79.72 | 99.42 | 127.99 |
| GET /api/auctions/{auctionId} [200] | 1,624.0 | 54.28 | 70.10 | 89.07 |
| GET /api/auctions [200] | 1,802.3 | 99.41 | 133.91 | 153.91 |
| POST /api/auctions/{auctionId}/bids [201] | 1,688.0 | 78.17 | 97.21 | 117.87 |
| POST /api/auctions/{auctionId}/bids [400] | 21.7 | 61.73 | 84.17 | 88.42 |
| POST /api/auctions/{auctionId}/bids [409] | 3.4 | 64.18 | 66.83 | 67.05 |

(요청수 0 조합 27/33건 생략)

### QPS150 — 2026-08-18T18:40:17+00:00 ~ 2026-08-18T18:42:17+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 8,739.4 | 106.04 | 179.37 | 253.13 |
| GET /api/auctions/{auctionId} [200] | 2,857.1 | 64.22 | 86.44 | 97.37 |
| GET /api/auctions [200] | 2,971.4 | 144.46 | 239.28 | 320.05 |
| POST /api/auctions/{auctionId}/bids [201] | 2,827.4 | 104.24 | 171.37 | 242.08 |
| POST /api/auctions/{auctionId}/bids [400] | 82.3 | 85.05 | 138.69 | 207.59 |
| POST /api/auctions/{auctionId}/bids [409] | 2.3 | 171.01 | 296.84 | 299.37 |

(요청수 0 조합 27/33건 생략)

### QPS200 — 2026-08-18T18:42:17+00:00 ~ 2026-08-18T18:44:17+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 12,302.9 | 182.82 | 297.88 | 349.84 |
| GET /api/auctions/{auctionId} [200] | 4,093.7 | 104.89 | 178.74 | 211.06 |
| GET /api/auctions [200] | 4,114.3 | 243.63 | 371.54 | 436.95 |
| POST /api/auctions/{auctionId}/bids [201] | 3,712.0 | 178.14 | 295.21 | 349.00 |
| POST /api/auctions/{auctionId}/bids [400] | 365.7 | 149.45 | 221.66 | 276.85 |
| POST /api/auctions/{auctionId}/bids [409] | 4.6 | 140.69 | 219.22 | 222.80 |

(요청수 0 조합 27/33건 생략)

### QPS300 — 2026-08-18T18:44:17+00:00 ~ 2026-08-18T18:46:17+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 16,182.9 | 194.86 | 275.49 | 338.85 |
| GET /api/auctions/{auctionId} [200] | 5,336.0 | 131.66 | 188.32 | 222.86 |
| GET /api/auctions [200] | 5,500.6 | 221.31 | 335.28 | 416.31 |
| POST /api/auctions/{auctionId}/bids [201] | 2,674.3 | 203.83 | 290.22 | 346.49 |
| POST /api/auctions/{auctionId}/bids [400] | 2,422.9 | 146.99 | 211.56 | 248.05 |
| POST /api/auctions/{auctionId}/bids [409] | 8.0 | 162.11 | 238.24 | 244.50 |

(요청수 0 조합 27/33건 생략)

### QPS400 — 2026-08-18T18:46:17+00:00 ~ 2026-08-18T18:48:17+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/stream [200] | 2.3 | 1,807,313.35 | 30,000.00 | 30,000.00 |
| GET /api/auctions/{auctionId}/bid-context [200] | 19,043.4 | 169.32 | 231.47 | 270.05 |
| GET /api/auctions/{auctionId} [200] | 6,104.0 | 118.79 | 169.24 | 199.82 |
| GET /api/auctions [200] | 6,405.7 | 188.18 | 265.54 | 327.16 |
| OPTIONS /api/auctions [200] | 1.1 | 5.81 | 6.92 | 6.98 |
| POST /api/auctions/{auctionId}/bids [201] | 1,441.1 | 178.99 | 241.88 | 292.72 |
| POST /api/auctions/{auctionId}/bids [400] | 4,269.7 | 131.83 | 183.58 | 217.11 |
| POST /api/auctions/{auctionId}/bids [409] | 6.9 | 119.71 | 149.88 | 155.25 |

(요청수 0 조합 25/33건 생략)

### 전체 구간(scenario full window) 요약

#### GET bid-context 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=200 | 69,210.24 |

#### POST bids 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=400 | 8,295.12 |
| status=409 | 27.63 |
| status=201 | 14,171.81 |

#### HikariCP (avg/max active, idle avg, pending avg/max, timeout delta 순서로 나열)

| 라벨 | 값 |
|---|---:|
| pool=HikariPool-1 | 4.73 |
| pool=HikariPool-1 | 13.00 |
| pool=HikariPool-1 | 25.27 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |

#### JVM GC pause count/sum delta (action/cause/gc, count 항목들 다음에 sum(초) 항목)

| 라벨 | 값 |
|---|---:|
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 307.04 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 26.61 |
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 4.83 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 0.36 |

#### JVM 스레드/CPU/힙 committed 최댓값(MB)

| 라벨 | 값 |
|---|---:|
| (no labels) | 120.95 |
| (no labels) | 131.00 |
| (no labels) | 0.54 |
| (no labels) | 0.77 |
| (no labels) | 602.00 |

#### Tomcat 커넥터 스레드 (busy avg/max, current avg 순서)

| 라벨 | 값 |
|---|---:|
| connector=main | 25.04 |
| connector=management | 1.00 |
| connector=main | 50.00 |
| connector=management | 1.00 |
| connector=main | 41.42 |
| connector=management | 30.00 |

#### SSE 커넥션 수 (avg, max 순서)

| 라벨 | 값 |
|---|---:|
| stream=auction | 978.51 |
| stream=me | 977.53 |
| stream=auction | 1,001.00 |
| stream=me | 1,000.00 |

#### sse_broadcast_saturated_total delta

| 라벨 | 값 |
|---|---:|
| (데이터 없음) | N/A |

#### dbidding_me_sse_send_failures_total delta

| 라벨 | 값 |
|---|---:|
| (no labels) | 0.00 |

#### Redis (up, 커넥션 avg, 메모리 avg/max MB, hit/miss delta, evicted/expired delta 순서)

| 라벨 | 값 |
|---|---:|
| instance=redis | 1.00 |
| instance=redis | 7.14 |
| instance=redis | 35.97 |
| instance=redis | 41.57 |
| instance=redis | 12,471,482.03 |
| instance=redis | 90,829.26 |
| instance=redis | 0.00 |
| instance=redis | 503.43 |

#### 노드 load1 avg / CPU 사용률 avg (backend/mysql/redis/monitoring)

| 라벨 | 값 |
|---|---:|
| instance=backend | 4.84 |
| instance=monitoring | 0.03 |
| instance=mysql | 0.55 |
| instance=redis | 1.35 |
| instance=redis | 0.28 |
| instance=backend | 0.42 |
| instance=monitoring | 0.03 |
| instance=mysql | 0.29 |

#### 각 호스트 exporter 프로세스 RSS avg(MB) — 참고용, 애플리케이션 RSS 아님

| 라벨 | 값 |
|---|---:|
| instance=backend, job=backend-node | 15.47 |
| instance=monitoring, job=monitoring-node | 14.91 |
| instance=monitoring-prometheus, job=prometheus | 102.17 |
| instance=mysql, job=mysql-exporter | 15.51 |
| instance=mysql, job=mysql-node | 21.57 |
| instance=redis, job=redis-exporter | 16.14 |
| instance=redis, job=redis-node | 19.56 |

#### major page fault delta

| 라벨 | 값 |
|---|---:|
| instance=backend | 16,970.89 |
| instance=monitoring | 361.28 |
| instance=mysql | 625.33 |
| instance=redis | 0.00 |

#### SwapFree 시작(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,597.22 |
| instance=monitoring | 2,757.80 |
| instance=mysql | 2,432.53 |
| instance=redis | 0.00 |
#### SwapFree 종료(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,370.45 |
| instance=monitoring | 2,757.82 |
| instance=mysql | 2,431.72 |
| instance=redis | 0.00 |
#### pswpin avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 37.06 |
| instance=monitoring | 0.12 |
| instance=mysql | 0.33 |
| instance=redis | 0.00 |
#### pswpout avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 62.45 |
| instance=monitoring | 0.08 |
| instance=mysql | 0.60 |
| instance=redis | 0.00 |

#### MySQL (row lock waits delta, row lock time delta(ms), threads connected/running avg, slow queries delta, up 순서)

| 라벨 | 값 |
|---|---:|
| instance=mysql | 810.58 |
| instance=mysql | 9,207.02 |
| instance=mysql | 31.00 |
| instance=mysql | 2.49 |
| instance=mysql | 0.00 |
| instance=mysql | 1.00 |

#### Redis Stream `event:timeline` group lag 궤적 (`auction-timeline-persistence`, 30초 간격 샘플)

- 시나리오 실행 구간 내 최고 lag: **8,719** (2026-08-18T18:48:41+00:00)
- 최고치 이후 50 미만으로 복귀한 시각: 2026-08-18T18:57:41+00:00 (최고치 대비 +9.0분)

| 시각(UTC) | lag | pending |
|---|---:|---:|
| 2026-08-18T18:35:41+00:00 | 0 | 0 |
| 2026-08-18T18:36:11+00:00 | 0 | 0 |
| 2026-08-18T18:36:41+00:00 | 0 | 0 |
| 2026-08-18T18:37:11+00:00 | 0 | 0 |
| 2026-08-18T18:37:41+00:00 | 0 | 0 |
| 2026-08-18T18:38:11+00:00 | 0 | 0 |
| 2026-08-18T18:38:41+00:00 | 0 | 0 |
| 2026-08-18T18:39:11+00:00 | 13 | 0 |
| 2026-08-18T18:39:41+00:00 | 108 | 0 |
| 2026-08-18T18:40:11+00:00 | 297 | 0 |
| 2026-08-18T18:40:41+00:00 | 597 | 0 |
| 2026-08-18T18:41:11+00:00 | 968 | 0 |
| 2026-08-18T18:41:41+00:00 | 1,443 | 0 |
| 2026-08-18T18:42:11+00:00 | 1,993 | 0 |
| 2026-08-18T18:42:41+00:00 | 2,647 | 0 |
| 2026-08-18T18:43:11+00:00 | 3,382 | 1 |
| 2026-08-18T18:43:41+00:00 | 4,183 | 0 |
| 2026-08-18T18:44:11+00:00 | 5,035 | 0 |
| 2026-08-18T18:44:41+00:00 | 5,914 | 0 |
| 2026-08-18T18:45:11+00:00 | 6,657 | 1 |
| 2026-08-18T18:45:41+00:00 | 7,169 | 0 |
| 2026-08-18T18:46:11+00:00 | 7,604 | 0 |
| 2026-08-18T18:46:41+00:00 | 7,924 | 0 |
| 2026-08-18T18:47:11+00:00 | 8,134 | 0 |
| 2026-08-18T18:47:41+00:00 | 8,317 | 0 |
| 2026-08-18T18:48:11+00:00 | 8,525 | 1 |
| 2026-08-18T18:48:41+00:00 | 8,719 | 0 |
| 2026-08-18T18:49:11+00:00 | 8,543 | 0 |
| 2026-08-18T18:49:41+00:00 | 8,024 | 0 |
| 2026-08-18T18:50:11+00:00 | 7,506 | 0 |
| 2026-08-18T18:50:41+00:00 | 7,006 | 0 |
| 2026-08-18T18:51:11+00:00 | 6,486 | 0 |
| 2026-08-18T18:51:41+00:00 | 5,980 | 0 |
| 2026-08-18T18:52:11+00:00 | 5,466 | 1 |
| 2026-08-18T18:52:41+00:00 | 4,960 | 0 |
| 2026-08-18T18:53:11+00:00 | 4,442 | 1 |
| 2026-08-18T18:53:41+00:00 | 3,935 | 0 |
| 2026-08-18T18:54:11+00:00 | 3,422 | 0 |
| 2026-08-18T18:54:41+00:00 | 2,914 | 1 |
| 2026-08-18T18:55:11+00:00 | 2,403 | 0 |
| 2026-08-18T18:55:41+00:00 | 1,891 | 0 |
| 2026-08-18T18:56:11+00:00 | 1,365 | 0 |
| 2026-08-18T18:56:41+00:00 | 855 | 0 |
| 2026-08-18T18:57:11+00:00 | 334 | 0 |
| 2026-08-18T18:57:41+00:00 | 0 | 0 |
| 2026-08-18T18:58:11+00:00 | 0 | 0 |
| 2026-08-18T18:58:41+00:00 | 0 | 0 |
| 2026-08-18T18:59:11+00:00 | 0 | 0 |
| 2026-08-18T18:59:41+00:00 | 707 | 0 |
| 2026-08-18T19:00:11+00:00 | 1,613 | 0 |
| 2026-08-18T19:00:41+00:00 | 2,502 | 0 |
| 2026-08-18T19:01:11+00:00 | 3,413 | 0 |
| 2026-08-18T19:01:41+00:00 | 4,320 | 0 |
| 2026-08-18T19:02:11+00:00 | 5,236 | 0 |
| 2026-08-18T19:02:41+00:00 | 6,138 | 0 |
| 2026-08-18T19:03:11+00:00 | 7,042 | 0 |
| 2026-08-18T19:03:41+00:00 | 7,944 | 0 |
| 2026-08-18T19:04:11+00:00 | 8,847 | 0 |

---

## round11-hotauction-20260819.json

- 시나리오: `hot-auction-pattern`
- K6 실행: 2026-08-18T18:57:52Z ~ 2026-08-18T19:05:58Z

### 0-1min — 2026-08-18T18:59:02+00:00 ~ 2026-08-18T19:00:02+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 3,601.3 | 85.59 | 100.83 | 140.84 |
| POST /api/auctions/{auctionId}/bids [201] | 2,317.3 | 86.02 | 99.92 | 133.51 |
| POST /api/auctions/{auctionId}/bids [400] | 1,285.3 | 66.69 | 87.79 | 137.26 |

(요청수 0 조합 31/34건 생략)

### 1-2min — 2026-08-18T19:00:02+00:00 ~ 2026-08-18T19:01:02+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 3,600.0 | 87.53 | 104.32 | 119.52 |
| POST /api/auctions/{auctionId}/bids [201] | 2,314.7 | 87.65 | 103.65 | 114.29 |
| POST /api/auctions/{auctionId}/bids [400] | 1,284.0 | 66.70 | 87.37 | 89.34 |
| POST /api/auctions/{auctionId}/bids [409] | 1.3 | 66.81 | 66.83 | 67.05 |

(요청수 0 조합 30/34건 생략)

### 2-3min — 2026-08-18T19:01:02+00:00 ~ 2026-08-18T19:02:02+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 3,600.0 | 86.37 | 105.57 | 127.30 |
| POST /api/auctions/{auctionId}/bids [201] | 2,324.0 | 87.38 | 106.15 | 126.27 |
| POST /api/auctions/{auctionId}/bids [400] | 1,273.3 | 66.19 | 87.44 | 99.28 |
| POST /api/auctions/{auctionId}/bids [409] | 2.7 | 69.48 | 87.24 | 89.03 |

(요청수 0 조합 30/34건 생략)

### 3-4min — 2026-08-18T19:02:02+00:00 ~ 2026-08-18T19:03:02+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 3,600.0 | 85.00 | 99.97 | 112.86 |
| POST /api/auctions/{auctionId}/bids [201] | 2,320.0 | 85.77 | 101.06 | 111.64 |
| POST /api/auctions/{auctionId}/bids [400] | 1,266.7 | 65.04 | 86.65 | 89.24 |
| POST /api/auctions/{auctionId}/bids [409] | 13.3 | 65.48 | 87.24 | 89.03 |

(요청수 0 조합 30/34건 생략)

### 4-5min — 2026-08-18T19:03:02+00:00 ~ 2026-08-18T19:04:02+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 3,600.0 | 84.87 | 99.88 | 111.18 |
| POST /api/auctions/{auctionId}/bids [201] | 2,325.3 | 86.44 | 101.07 | 114.17 |
| POST /api/auctions/{auctionId}/bids [400] | 1,270.7 | 64.41 | 86.32 | 89.36 |
| POST /api/auctions/{auctionId}/bids [409] | 4.0 | 62.25 | 86.12 | 88.81 |

(요청수 0 조합 30/34건 생략)

### 전체 구간(scenario full window) 요약

#### GET bid-context 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=200 | 18,227.02 |

#### POST bids 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=400 | 6,529.61 |
| status=409 | 22.27 |
| status=201 | 11,675.14 |

#### HikariCP (avg/max active, idle avg, pending avg/max, timeout delta 순서로 나열)

| 라벨 | 값 |
|---|---:|
| pool=HikariPool-1 | 1.55 |
| pool=HikariPool-1 | 4.00 |
| pool=HikariPool-1 | 28.45 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |

#### JVM GC pause count/sum delta (action/cause/gc, count 항목들 다음에 sum(초) 항목)

| 라벨 | 값 |
|---|---:|
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 66.83 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 7.09 |
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 0.76 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 0.05 |

#### JVM 스레드/CPU/힙 committed 최댓값(MB)

| 라벨 | 값 |
|---|---:|
| (no labels) | 122.12 |
| (no labels) | 131.00 |
| (no labels) | 0.34 |
| (no labels) | 0.57 |
| (no labels) | 602.00 |

#### Tomcat 커넥터 스레드 (busy avg/max, current avg 순서)

| 라벨 | 값 |
|---|---:|
| connector=main | 6.24 |
| connector=management | 1.00 |
| connector=main | 20.00 |
| connector=management | 1.00 |
| connector=main | 42.42 |
| connector=management | 30.00 |

#### SSE 커넥션 수 (avg, max 순서)

| 라벨 | 값 |
|---|---:|
| stream=auction | 460.70 |
| stream=me | 459.70 |
| stream=auction | 501.00 |
| stream=me | 500.00 |

#### sse_broadcast_saturated_total delta

| 라벨 | 값 |
|---|---:|
| (데이터 없음) | N/A |

#### dbidding_me_sse_send_failures_total delta

| 라벨 | 값 |
|---|---:|
| (no labels) | 0.00 |

#### Redis (up, 커넥션 avg, 메모리 avg/max MB, hit/miss delta, evicted/expired delta 순서)

| 라벨 | 값 |
|---|---:|
| instance=redis | 1.00 |
| instance=redis | 5.33 |
| instance=redis | 45.57 |
| instance=redis | 50.17 |
| instance=redis | 8,819,160.53 |
| instance=redis | 24,515.66 |
| instance=redis | 0.00 |
| instance=redis | 277.43 |

#### 노드 load1 avg / CPU 사용률 avg (backend/mysql/redis/monitoring)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2.81 |
| instance=monitoring | 0.02 |
| instance=mysql | 0.61 |
| instance=redis | 0.95 |
| instance=monitoring | 0.03 |
| instance=mysql | 0.28 |
| instance=redis | 0.21 |
| instance=backend | 0.31 |

#### 각 호스트 exporter 프로세스 RSS avg(MB) — 참고용, 애플리케이션 RSS 아님

| 라벨 | 값 |
|---|---:|
| instance=backend, job=backend-node | 15.25 |
| instance=monitoring, job=monitoring-node | 15.01 |
| instance=monitoring-prometheus, job=prometheus | 109.37 |
| instance=mysql, job=mysql-exporter | 15.64 |
| instance=mysql, job=mysql-node | 21.47 |
| instance=redis, job=redis-exporter | 16.07 |
| instance=redis, job=redis-node | 19.60 |

#### major page fault delta

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,251.80 |
| instance=monitoring | 174.54 |
| instance=mysql | 503.21 |
| instance=redis | 0.00 |

#### SwapFree 시작(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,371.15 |
| instance=monitoring | 2,757.83 |
| instance=mysql | 2,431.72 |
| instance=redis | 0.00 |
#### SwapFree 종료(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,360.94 |
| instance=monitoring | 2,758.02 |
| instance=mysql | 2,431.52 |
| instance=redis | 0.00 |
#### pswpin avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 7.98 |
| instance=monitoring | 0.26 |
| instance=mysql | 0.27 |
| instance=redis | 0.00 |
#### pswpout avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 3.99 |
| instance=monitoring | 0.00 |
| instance=mysql | 0.90 |
| instance=redis | 0.00 |

#### MySQL (row lock waits delta, row lock time delta(ms), threads connected/running avg, slow queries delta, up 순서)

| 라벨 | 값 |
|---|---:|
| instance=mysql | 969.91 |
| instance=mysql | 6,730.84 |
| instance=mysql | 31.00 |
| instance=mysql | 2.44 |
| instance=mysql | 0.00 |
| instance=mysql | 1.00 |

#### Redis Stream `event:timeline` group lag 궤적 (`auction-timeline-persistence`, 30초 간격 샘플)

- 시나리오 실행 구간 내 최고 lag: **8,878** (2026-08-18T19:04:22+00:00)
- 최고치 이후 50 미만으로 복귀한 시각: 2026-08-18T19:13:22+00:00 (최고치 대비 +9.0분)

| 시각(UTC) | lag | pending |
|---|---:|---:|
| 2026-08-18T18:57:52+00:00 | 0 | 0 |
| 2026-08-18T18:58:22+00:00 | 0 | 0 |
| 2026-08-18T18:58:52+00:00 | 0 | 0 |
| 2026-08-18T18:59:22+00:00 | 265 | 0 |
| 2026-08-18T18:59:52+00:00 | 1,162 | 0 |
| 2026-08-18T19:00:22+00:00 | 2,045 | 0 |
| 2026-08-18T19:00:52+00:00 | 2,958 | 0 |
| 2026-08-18T19:01:22+00:00 | 3,870 | 0 |
| 2026-08-18T19:01:52+00:00 | 4,779 | 0 |
| 2026-08-18T19:02:22+00:00 | 5,686 | 1 |
| 2026-08-18T19:02:52+00:00 | 6,588 | 0 |
| 2026-08-18T19:03:22+00:00 | 7,487 | 0 |
| 2026-08-18T19:03:52+00:00 | 8,396 | 0 |
| 2026-08-18T19:04:22+00:00 | 8,878 | 1 |
| 2026-08-18T19:04:52+00:00 | 8,362 | 1 |
| 2026-08-18T19:05:22+00:00 | 7,855 | 0 |
| 2026-08-18T19:05:52+00:00 | 7,339 | 0 |
| 2026-08-18T19:06:22+00:00 | 6,824 | 0 |
| 2026-08-18T19:06:52+00:00 | 6,314 | 0 |
| 2026-08-18T19:07:22+00:00 | 5,804 | 0 |
| 2026-08-18T19:07:52+00:00 | 5,282 | 0 |
| 2026-08-18T19:08:22+00:00 | 4,769 | 0 |
| 2026-08-18T19:08:52+00:00 | 4,257 | 1 |
| 2026-08-18T19:09:22+00:00 | 3,746 | 0 |
| 2026-08-18T19:09:52+00:00 | 3,231 | 1 |
| 2026-08-18T19:10:22+00:00 | 2,725 | 0 |
| 2026-08-18T19:10:52+00:00 | 2,221 | 1 |
| 2026-08-18T19:11:22+00:00 | 1,711 | 0 |
| 2026-08-18T19:11:52+00:00 | 1,208 | 0 |
| 2026-08-18T19:12:22+00:00 | 704 | 0 |
| 2026-08-18T19:12:52+00:00 | 192 | 0 |
| 2026-08-18T19:13:22+00:00 | 0 | 0 |
| 2026-08-18T19:13:52+00:00 | 0 | 0 |
| 2026-08-18T19:14:22+00:00 | 0 | 0 |
| 2026-08-18T19:14:52+00:00 | 0 | 0 |
| 2026-08-18T19:15:22+00:00 | 0 | 0 |
| 2026-08-18T19:15:52+00:00 | 0 | 0 |
| 2026-08-18T19:16:22+00:00 | 3 | 0 |
| 2026-08-18T19:16:52+00:00 | 49 | 0 |
| 2026-08-18T19:17:22+00:00 | 197 | 0 |
| 2026-08-18T19:17:52+00:00 | 415 | 1 |
| 2026-08-18T19:18:22+00:00 | 717 | 0 |
| 2026-08-18T19:18:52+00:00 | 1,099 | 0 |
| 2026-08-18T19:19:22+00:00 | 1,559 | 0 |
| 2026-08-18T19:19:52+00:00 | 2,085 | 0 |
| 2026-08-18T19:20:22+00:00 | 2,704 | 0 |
| 2026-08-18T19:20:52+00:00 | 3,403 | 0 |

---

## round11-bidonly-20260819.json

- 시나리오: `bid-only-load (SSE 없음, 분산)`
- K6 실행: 2026-08-18T19:13:40Z ~ 2026-08-18T19:25:55Z

### QPS50 — 2026-08-18T19:13:46+00:00 ~ 2026-08-18T19:15:46+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 3,600.0 | 59.60 | 85.88 | 91.87 |
| GET /api/auctions/{auctionId} [200] | 1,246.9 | 38.97 | 61.15 | 73.79 |
| GET /api/auctions [200] | 1,154.3 | 74.73 | 102.46 | 121.67 |
| POST /api/auctions/{auctionId}/bids [201] | 1,195.4 | 57.58 | 83.60 | 88.80 |
| POST /api/auctions/{auctionId}/bids [400] | 4.6 | 57.18 | 60.96 | 61.40 |

(요청수 0 조합 29/34건 생략)

### QPS100 — 2026-08-18T19:15:46+00:00 ~ 2026-08-18T19:17:46+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /** [404] | 136.0 | 4.54 | 9.81 | 14.55 |
| GET /api/auctions/stream [200] | 2.3 | 1,800,361.80 | 30,000.00 | 30,000.00 |
| GET /api/auctions/{auctionId}/bid-context [200] | 5,458.3 | 81.47 | 108.21 | 133.80 |
| GET /api/auctions/{auctionId} [200] | 1,828.6 | 55.00 | 67.50 | 97.13 |
| GET /api/auctions [200] | 1,811.4 | 111.74 | 154.56 | 177.50 |
| POST /api/auctions/{auctionId}/bids [201] | 1,781.7 | 79.89 | 101.49 | 125.68 |
| POST /api/auctions/{auctionId}/bids [400] | 32.0 | 64.54 | 88.20 | 108.53 |
| POST /api/auctions/{auctionId}/bids [409] | 4.6 | 66.99 | 97.90 | 99.58 |

(요청수 0 조합 26/34건 생략)

### QPS150 — 2026-08-18T19:17:46+00:00 ~ 2026-08-18T19:19:46+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /** [404] | 2.3 | 0.35 | 0.95 | 0.99 |
| GET /api/auctions/{auctionId}/bid-context [200] | 9,058.3 | 89.34 | 108.50 | 132.57 |
| GET /api/auctions/{auctionId} [200] | 2,985.1 | 60.46 | 77.23 | 88.21 |
| GET /api/auctions [200] | 3,049.1 | 127.58 | 154.81 | 178.42 |
| POST /api/auctions/{auctionId}/bids [201] | 2,937.1 | 88.49 | 106.56 | 129.23 |
| POST /api/auctions/{auctionId}/bids [400] | 68.6 | 68.75 | 88.65 | 104.74 |
| POST /api/auctions/{auctionId}/bids [409] | 11.4 | 67.27 | 86.68 | 88.92 |

(요청수 0 조합 27/34건 생략)

### QPS200 — 2026-08-18T19:19:46+00:00 ~ 2026-08-18T19:21:46+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /** [404] | 1.1 | 3.03 | 3.13 | 3.14 |
| GET /api/auctions/{auctionId}/bid-context [200] | 12,659.4 | 108.11 | 176.24 | 221.35 |
| GET /api/auctions/{auctionId} [200] | 4,300.6 | 64.56 | 86.36 | 102.25 |
| GET /api/auctions [200] | 4,146.3 | 162.28 | 242.79 | 325.87 |
| POST /api/auctions/{auctionId}/bids [201] | 4,042.3 | 106.08 | 169.35 | 206.17 |
| POST /api/auctions/{auctionId}/bids [400] | 164.6 | 89.70 | 153.90 | 176.99 |
| POST /api/auctions/{auctionId}/bids [409] | 11.4 | 82.85 | 145.40 | 154.35 |

(요청수 0 조합 27/34건 생략)

### QPS300 — 2026-08-18T19:21:46+00:00 ~ 2026-08-18T19:23:46+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 17,865.1 | 151.82 | 199.43 | 241.43 |
| GET /api/auctions/{auctionId} [200] | 5,933.7 | 96.33 | 131.61 | 155.62 |
| GET /api/auctions [200] | 5,976.0 | 205.13 | 260.69 | 298.64 |
| POST /api/auctions/{auctionId}/bids [201] | 4,872.0 | 148.57 | 197.77 | 236.62 |
| POST /api/auctions/{auctionId}/bids [400] | 971.4 | 119.39 | 152.46 | 168.70 |
| POST /api/auctions/{auctionId}/bids [409] | 16.0 | 110.99 | 148.76 | 155.02 |

(요청수 0 조합 28/34건 생략)

### QPS400 — 2026-08-18T19:23:46+00:00 ~ 2026-08-18T19:25:46+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 20,220.6 | 154.36 | 198.35 | 222.47 |
| GET /api/auctions/{auctionId} [200] | 6,625.1 | 108.22 | 142.88 | 166.29 |
| GET /api/auctions [200] | 7,028.6 | 190.78 | 244.63 | 285.86 |
| POST /api/auctions/{auctionId}/bids [201] | 1,690.3 | 156.05 | 199.32 | 224.28 |
| POST /api/auctions/{auctionId}/bids [400] | 4,413.7 | 120.28 | 155.46 | 178.35 |
| POST /api/auctions/{auctionId}/bids [409] | 11.4 | 122.01 | 152.86 | 155.84 |

(요청수 0 조합 28/34건 생략)

### 전체 구간(scenario full window) 요약

#### GET bid-context 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=200 | 68,969.54 |

#### POST bids 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=400 | 5,522.71 |
| status=409 | 59.21 |
| status=201 | 16,725.33 |

#### HikariCP (avg/max active, idle avg, pending avg/max, timeout delta 순서로 나열)

| 라벨 | 값 |
|---|---:|
| pool=HikariPool-1 | 4.29 |
| pool=HikariPool-1 | 12.00 |
| pool=HikariPool-1 | 25.71 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |

#### JVM GC pause count/sum delta (action/cause/gc, count 항목들 다음에 sum(초) 항목)

| 라벨 | 값 |
|---|---:|
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 429.77 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 18.38 |
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 4.62 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 0.22 |

#### JVM 스레드/CPU/힙 committed 최댓값(MB)

| 라벨 | 값 |
|---|---:|
| (no labels) | 119.02 |
| (no labels) | 132.00 |
| (no labels) | 0.53 |
| (no labels) | 0.80 |
| (no labels) | 350.00 |

#### Tomcat 커넥터 스레드 (busy avg/max, current avg 순서)

| 라벨 | 값 |
|---|---:|
| connector=main | 23.61 |
| connector=management | 1.00 |
| connector=main | 50.00 |
| connector=management | 1.00 |
| connector=main | 39.63 |
| connector=management | 30.00 |

#### SSE 커넥션 수 (avg, max 순서)

| 라벨 | 값 |
|---|---:|
| stream=auction | 1.00 |
| stream=me | 0.00 |
| stream=auction | 1.00 |
| stream=me | 0.00 |

#### sse_broadcast_saturated_total delta

| 라벨 | 값 |
|---|---:|
| (데이터 없음) | N/A |

#### dbidding_me_sse_send_failures_total delta

| 라벨 | 값 |
|---|---:|
| (no labels) | 0.00 |

#### Redis (up, 커넥션 avg, 메모리 avg/max MB, hit/miss delta, evicted/expired delta 순서)

| 라벨 | 값 |
|---|---:|
| instance=redis | 1.00 |
| instance=redis | 8.02 |
| instance=redis | 52.75 |
| instance=redis | 59.02 |
| instance=redis | 14,717,253.10 |
| instance=redis | 60,302.67 |
| instance=redis | 0.00 |
| instance=redis | 1,163.75 |

#### 노드 load1 avg / CPU 사용률 avg (backend/mysql/redis/monitoring)

| 라벨 | 값 |
|---|---:|
| instance=backend | 3.95 |
| instance=monitoring | 0.04 |
| instance=mysql | 0.82 |
| instance=redis | 1.46 |
| instance=backend | 0.34 |
| instance=monitoring | 0.02 |
| instance=mysql | 0.31 |
| instance=redis | 0.29 |

#### 각 호스트 exporter 프로세스 RSS avg(MB) — 참고용, 애플리케이션 RSS 아님

| 라벨 | 값 |
|---|---:|
| instance=backend, job=backend-node | 15.19 |
| instance=monitoring, job=monitoring-node | 14.92 |
| instance=monitoring-prometheus, job=prometheus | 98.86 |
| instance=mysql, job=mysql-exporter | 15.38 |
| instance=mysql, job=mysql-node | 21.44 |
| instance=redis, job=redis-exporter | 16.10 |
| instance=redis, job=redis-node | 19.65 |

#### major page fault delta

| 라벨 | 값 |
|---|---:|
| instance=backend | 902.42 |
| instance=monitoring | 51.04 |
| instance=mysql | 294.00 |
| instance=redis | 0.00 |

#### SwapFree 시작(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,466.79 |
| instance=monitoring | 2,758.29 |
| instance=mysql | 2,430.88 |
| instance=redis | 0.00 |
#### SwapFree 종료(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,485.52 |
| instance=monitoring | 2,758.49 |
| instance=mysql | 2,430.84 |
| instance=redis | 0.00 |
#### pswpin avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 1.77 |
| instance=monitoring | 0.05 |
| instance=mysql | 0.64 |
| instance=redis | 0.00 |
#### pswpout avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 0.00 |
| instance=monitoring | 0.00 |
| instance=mysql | 0.01 |
| instance=redis | 0.00 |

#### MySQL (row lock waits delta, row lock time delta(ms), threads connected/running avg, slow queries delta, up 순서)

| 라벨 | 값 |
|---|---:|
| instance=mysql | 3,509.62 |
| instance=mysql | 15,234.92 |
| instance=mysql | 31.00 |
| instance=mysql | 2.47 |
| instance=mysql | 0.00 |
| instance=mysql | 1.00 |

#### Redis Stream `event:timeline` group lag 궤적 (`auction-timeline-persistence`, 30초 간격 샘플)

- 시나리오 실행 구간 내 최고 lag: **9,905** (2026-08-18T19:25:40+00:00)
- 최고치 이후 50 미만으로 복귀한 시각: 2026-08-18T19:36:10+00:00 (최고치 대비 +10.5분)

| 시각(UTC) | lag | pending |
|---|---:|---:|
| 2026-08-18T19:13:40+00:00 | 0 | 0 |
| 2026-08-18T19:14:10+00:00 | 0 | 0 |
| 2026-08-18T19:14:40+00:00 | 0 | 0 |
| 2026-08-18T19:15:10+00:00 | 0 | 0 |
| 2026-08-18T19:15:40+00:00 | 0 | 0 |
| 2026-08-18T19:16:10+00:00 | 0 | 1 |
| 2026-08-18T19:16:40+00:00 | 19 | 0 |
| 2026-08-18T19:17:10+00:00 | 103 | 0 |
| 2026-08-18T19:17:40+00:00 | 296 | 0 |
| 2026-08-18T19:18:10+00:00 | 556 | 0 |
| 2026-08-18T19:18:40+00:00 | 895 | 0 |
| 2026-08-18T19:19:10+00:00 | 1,316 | 1 |
| 2026-08-18T19:19:40+00:00 | 1,808 | 0 |
| 2026-08-18T19:20:10+00:00 | 2,376 | 0 |
| 2026-08-18T19:20:40+00:00 | 3,044 | 0 |
| 2026-08-18T19:21:10+00:00 | 3,778 | 0 |
| 2026-08-18T19:21:40+00:00 | 4,567 | 0 |
| 2026-08-18T19:22:10+00:00 | 5,429 | 0 |
| 2026-08-18T19:22:40+00:00 | 6,434 | 1 |
| 2026-08-18T19:23:10+00:00 | 7,515 | 0 |
| 2026-08-18T19:23:40+00:00 | 8,540 | 0 |
| 2026-08-18T19:24:10+00:00 | 9,212 | 0 |
| 2026-08-18T19:24:40+00:00 | 9,601 | 0 |
| 2026-08-18T19:25:10+00:00 | 9,805 | 0 |
| 2026-08-18T19:25:40+00:00 | 9,905 | 0 |
| 2026-08-18T19:26:10+00:00 | 10,052 | 0 |
| 2026-08-18T19:26:40+00:00 | 9,535 | 0 |
| 2026-08-18T19:27:10+00:00 | 9,020 | 0 |
| 2026-08-18T19:27:40+00:00 | 8,508 | 0 |
| 2026-08-18T19:28:10+00:00 | 7,992 | 0 |
| 2026-08-18T19:28:40+00:00 | 7,477 | 0 |
| 2026-08-18T19:29:10+00:00 | 6,962 | 0 |
| 2026-08-18T19:29:40+00:00 | 6,447 | 0 |
| 2026-08-18T19:30:10+00:00 | 5,928 | 0 |
| 2026-08-18T19:30:40+00:00 | 5,428 | 0 |
| 2026-08-18T19:31:10+00:00 | 4,915 | 0 |
| 2026-08-18T19:31:40+00:00 | 4,405 | 0 |
| 2026-08-18T19:32:10+00:00 | 3,898 | 0 |
| 2026-08-18T19:32:40+00:00 | 3,388 | 0 |
| 2026-08-18T19:33:10+00:00 | 2,873 | 0 |
| 2026-08-18T19:33:40+00:00 | 2,365 | 0 |
| 2026-08-18T19:34:10+00:00 | 1,854 | 0 |
| 2026-08-18T19:34:40+00:00 | 1,346 | 0 |
| 2026-08-18T19:35:10+00:00 | 832 | 0 |
| 2026-08-18T19:35:40+00:00 | 325 | 0 |
| 2026-08-18T19:36:10+00:00 | 0 | 0 |
| 2026-08-18T19:36:40+00:00 | 0 | 0 |
| 2026-08-18T19:37:10+00:00 | 0 | 0 |
| 2026-08-18T19:37:40+00:00 | 0 | 1 |
| 2026-08-18T19:38:10+00:00 | 0 | 1 |
| 2026-08-18T19:38:40+00:00 | 0 | 0 |
| 2026-08-18T19:39:10+00:00 | 0 | 0 |
| 2026-08-18T19:39:40+00:00 | 0 | 0 |
| 2026-08-18T19:40:10+00:00 | 0 | 0 |
| 2026-08-18T19:40:40+00:00 | 0 | 1 |

---

## round11-bidonly-hot-20260819.json

- 시나리오: `bid-only-load (SSE 없음, 핫경매집중 HOT_AUCTION_ID=3001001)`
- K6 실행: 2026-08-18T19:36:23Z ~ 2026-08-18T19:48:36Z

### QPS50 — 2026-08-18T19:36:29+00:00 ~ 2026-08-18T19:38:29+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 3,602.3 | 57.66 | 84.99 | 89.10 |
| GET /api/auctions/{auctionId} [200] | 1,257.1 | 39.09 | 60.28 | 66.49 |
| GET /api/auctions [200] | 1,142.9 | 71.45 | 89.48 | 99.74 |
| POST /api/auctions/{auctionId}/bids [201] | 1,133.7 | 55.79 | 81.98 | 88.23 |
| POST /api/auctions/{auctionId}/bids [400] | 67.4 | 52.74 | 61.61 | 66.01 |

(요청수 0 조합 29/34건 생략)

### QPS100 — 2026-08-18T19:38:29+00:00 ~ 2026-08-18T19:40:29+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /** [404] | 1.1 | 1.03 | 1.05 | 1.05 |
| GET /api/auctions/{auctionId}/bid-context [200] | 5,520.0 | 77.52 | 92.91 | 108.66 |
| GET /api/auctions/{auctionId} [200] | 1,850.3 | 52.26 | 63.79 | 76.41 |
| GET /api/auctions [200] | 1,828.6 | 105.67 | 132.76 | 171.50 |
| POST /api/auctions/{auctionId}/bids [201] | 932.6 | 76.33 | 89.18 | 99.51 |
| POST /api/auctions/{auctionId}/bids [400] | 906.3 | 59.88 | 75.95 | 89.09 |

(요청수 0 조합 28/34건 생략)

### QPS150 — 2026-08-18T19:40:29+00:00 ~ 2026-08-18T19:42:29+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 9,118.9 | 87.03 | 105.48 | 129.95 |
| GET /api/auctions/{auctionId} [200] | 2,972.6 | 58.68 | 69.68 | 87.58 |
| GET /api/auctions [200] | 3,107.4 | 124.42 | 151.61 | 174.79 |
| POST /api/auctions/{auctionId}/bids [201] | 930.3 | 86.36 | 99.94 | 124.82 |
| POST /api/auctions/{auctionId}/bids [400] | 2,108.6 | 67.46 | 87.64 | 96.81 |

(요청수 0 조합 29/34건 생략)

### QPS200 — 2026-08-18T19:42:29+00:00 ~ 2026-08-18T19:44:29+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /** [404] | 1.1 | 3.04 | 3.13 | 3.14 |
| GET /api/auctions/{auctionId}/bid-context [200] | 12,716.6 | 96.25 | 131.39 | 174.65 |
| GET /api/auctions/{auctionId} [200] | 4,228.6 | 61.46 | 79.66 | 90.23 |
| GET /api/auctions [200] | 4,243.4 | 141.28 | 180.12 | 238.89 |
| POST /api/auctions/{auctionId}/bids [201] | 910.9 | 94.09 | 126.12 | 159.88 |
| POST /api/auctions/{auctionId}/bids [400] | 3,326.9 | 74.51 | 99.58 | 130.18 |

(요청수 0 조합 28/34건 생략)

### QPS300 — 2026-08-18T19:44:29+00:00 ~ 2026-08-18T19:46:29+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/{auctionId}/bid-context [200] | 18,237.7 | 114.05 | 163.39 | 178.82 |
| GET /api/auctions/{auctionId} [200] | 6,136.0 | 69.61 | 100.04 | 133.95 |
| GET /api/auctions [200] | 6,027.4 | 166.37 | 214.40 | 233.43 |
| POST /api/auctions/{auctionId}/bids [201] | 810.3 | 107.60 | 155.99 | 176.57 |
| POST /api/auctions/{auctionId}/bids [400] | 5,268.6 | 88.75 | 129.30 | 144.38 |

(요청수 0 조합 29/34건 생략)

### QPS400 — 2026-08-18T19:46:29+00:00 ~ 2026-08-18T19:48:29+00:00

#### HTTP 서버 히스토그램 (서버 측, method/uri/status)

| method/uri/status | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET /api/auctions/stream [200] | 2.3 | 1,801,021.60 | 30,000.00 | 30,000.00 |
| GET /api/auctions/{auctionId}/bid-context [200] | 23,029.7 | 135.53 | 171.30 | 195.87 |
| GET /api/auctions/{auctionId} [200] | 7,616.0 | 92.87 | 121.39 | 143.15 |
| GET /api/auctions [200] | 7,862.9 | 172.95 | 213.79 | 243.78 |
| OPTIONS /api/auctions [200] | 1.1 | 0.61 | 0.95 | 0.99 |
| POST /api/auctions/{auctionId}/bids [201] | 240.0 | 138.47 | 172.03 | 200.21 |
| POST /api/auctions/{auctionId}/bids [400] | 6,977.1 | 105.69 | 133.35 | 155.05 |
| POST /api/auctions/{auctionId}/bids [409] | 3.4 | 108.48 | 130.86 | 133.55 |

(요청수 0 조합 26/34건 생략)

### 전체 구간(scenario full window) 요약

#### GET bid-context 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=200 | 72,090.55 |

#### POST bids 상태코드 분포 (전체 구간 increase)

| 라벨 | 값 |
|---|---:|
| status=400 | 18,496.03 |
| status=409 | 3.05 |
| status=201 | 5,107.58 |

#### HikariCP (avg/max active, idle avg, pending avg/max, timeout delta 순서로 나열)

| 라벨 | 값 |
|---|---:|
| pool=HikariPool-1 | 3.63 |
| pool=HikariPool-1 | 12.00 |
| pool=HikariPool-1 | 26.37 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |
| pool=HikariPool-1 | 0.00 |

#### JVM GC pause count/sum delta (action/cause/gc, count 항목들 다음에 sum(초) 항목)

| 라벨 | 값 |
|---|---:|
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 392.97 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 20.36 |
| action=end of minor GC, cause=CodeCache GC Threshold, gc=G1 Young Generation | 0.00 |
| action=end of minor GC, cause=G1 Evacuation Pause, gc=G1 Young Generation | 3.98 |
| action=end of minor GC, cause=G1 Humongous Allocation, gc=G1 Young Generation | 0.19 |

#### JVM 스레드/CPU/힙 committed 최댓값(MB)

| 라벨 | 값 |
|---|---:|
| (no labels) | 118.02 |
| (no labels) | 131.00 |
| (no labels) | 0.47 |
| (no labels) | 0.76 |
| (no labels) | 324.00 |

#### Tomcat 커넥터 스레드 (busy avg/max, current avg 순서)

| 라벨 | 값 |
|---|---:|
| connector=main | 21.45 |
| connector=management | 1.00 |
| connector=main | 50.00 |
| connector=management | 1.00 |
| connector=main | 38.59 |
| connector=management | 30.00 |

#### SSE 커넥션 수 (avg, max 순서)

| 라벨 | 값 |
|---|---:|
| stream=auction | 1.00 |
| stream=me | 0.00 |
| stream=auction | 1.00 |
| stream=me | 0.00 |

#### sse_broadcast_saturated_total delta

| 라벨 | 값 |
|---|---:|
| (데이터 없음) | N/A |

#### dbidding_me_sse_send_failures_total delta

| 라벨 | 값 |
|---|---:|
| (no labels) | 0.00 |

#### Redis (up, 커넥션 avg, 메모리 avg/max MB, hit/miss delta, evicted/expired delta 순서)

| 라벨 | 값 |
|---|---:|
| instance=redis | 1.00 |
| instance=redis | 7.22 |
| instance=redis | 58.06 |
| instance=redis | 58.82 |
| instance=redis | 6,468,627.27 |
| instance=redis | 53,079.38 |
| instance=redis | 0.00 |
| instance=redis | 497.83 |

#### 노드 load1 avg / CPU 사용률 avg (backend/mysql/redis/monitoring)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2.60 |
| instance=monitoring | 0.02 |
| instance=mysql | 0.58 |
| instance=redis | 1.44 |
| instance=redis | 0.29 |
| instance=backend | 0.32 |
| instance=monitoring | 0.02 |
| instance=mysql | 0.29 |

#### 각 호스트 exporter 프로세스 RSS avg(MB) — 참고용, 애플리케이션 RSS 아님

| 라벨 | 값 |
|---|---:|
| instance=backend, job=backend-node | 15.21 |
| instance=monitoring, job=monitoring-node | 15.03 |
| instance=monitoring-prometheus, job=prometheus | 100.81 |
| instance=mysql, job=mysql-exporter | 15.34 |
| instance=mysql, job=mysql-node | 21.44 |
| instance=redis, job=redis-exporter | 16.07 |
| instance=redis, job=redis-node | 19.57 |

#### major page fault delta

| 라벨 | 값 |
|---|---:|
| instance=backend | 38.69 |
| instance=monitoring | 90.61 |
| instance=mysql | 699.40 |
| instance=redis | 0.00 |

#### SwapFree 시작(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,485.62 |
| instance=monitoring | 2,758.49 |
| instance=mysql | 2,430.28 |
| instance=redis | 0.00 |
#### SwapFree 종료(MB)

| 라벨 | 값 |
|---|---:|
| instance=backend | 2,492.84 |
| instance=monitoring | 2,758.49 |
| instance=mysql | 2,429.46 |
| instance=redis | 0.00 |
#### pswpin avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 0.06 |
| instance=monitoring | 0.04 |
| instance=mysql | 0.92 |
| instance=redis | 0.00 |
#### pswpout avg(page/s)

| 라벨 | 값 |
|---|---:|
| instance=backend | 0.00 |
| instance=monitoring | 0.00 |
| instance=mysql | 1.66 |
| instance=redis | 0.00 |

#### MySQL (row lock waits delta, row lock time delta(ms), threads connected/running avg, slow queries delta, up 순서)

| 라벨 | 값 |
|---|---:|
| instance=mysql | 1,498.58 |
| instance=mysql | 23,306.35 |
| instance=mysql | 31.00 |
| instance=mysql | 2.31 |
| instance=mysql | 0.00 |
| instance=mysql | 1.00 |

#### Redis Stream `event:timeline` group lag 궤적 (`auction-timeline-persistence`, 30초 간격 샘플)

- 시나리오 실행 구간 내 최고 lag: **0** (2026-08-18T19:36:23+00:00)
- 최고치 이후 50 미만으로 복귀한 시각: 2026-08-18T19:36:23+00:00 (최고치 대비 +0.0분)

| 시각(UTC) | lag | pending |
|---|---:|---:|
| 2026-08-18T19:36:23+00:00 | 0 | 0 |
| 2026-08-18T19:36:53+00:00 | 0 | 0 |
| 2026-08-18T19:37:23+00:00 | 0 | 0 |
| 2026-08-18T19:37:53+00:00 | 0 | 1 |
| 2026-08-18T19:38:23+00:00 | 0 | 0 |
| 2026-08-18T19:38:53+00:00 | 0 | 0 |
| 2026-08-18T19:39:23+00:00 | 0 | 0 |
| 2026-08-18T19:39:53+00:00 | 0 | 0 |
| 2026-08-18T19:40:23+00:00 | 0 | 0 |
| 2026-08-18T19:40:53+00:00 | 0 | 0 |
| 2026-08-18T19:41:23+00:00 | 0 | 0 |
| 2026-08-18T19:41:53+00:00 | 0 | 0 |
| 2026-08-18T19:42:23+00:00 | 0 | 0 |
| 2026-08-18T19:42:53+00:00 | 0 | 1 |
| 2026-08-18T19:43:23+00:00 | 0 | 0 |
| 2026-08-18T19:43:53+00:00 | 0 | 0 |
| 2026-08-18T19:44:23+00:00 | 0 | 0 |
| 2026-08-18T19:44:53+00:00 | 0 | 0 |
| 2026-08-18T19:45:23+00:00 | 0 | 0 |
| 2026-08-18T19:45:53+00:00 | 0 | 0 |
| 2026-08-18T19:46:23+00:00 | 0 | 0 |
| 2026-08-18T19:46:53+00:00 | 0 | 0 |
| 2026-08-18T19:47:23+00:00 | 0 | 0 |
| 2026-08-18T19:47:53+00:00 | 0 | 0 |
| 2026-08-18T19:48:23+00:00 | 0 | 0 |
| 2026-08-18T19:48:53+00:00 | 0 | 0 |
| 2026-08-18T19:49:23+00:00 | 0 | 0 |
| 2026-08-18T19:49:53+00:00 | 0 | 0 |
| 2026-08-18T19:50:23+00:00 | 0 | 0 |
| 2026-08-18T19:50:53+00:00 | 0 | 0 |
| 2026-08-18T19:51:23+00:00 | 0 | 0 |
| 2026-08-18T19:51:53+00:00 | 0 | 0 |
| 2026-08-18T19:52:23+00:00 | 0 | 0 |
| 2026-08-18T19:52:53+00:00 | 0 | 0 |
| 2026-08-18T19:53:23+00:00 | 0 | 0 |
| 2026-08-18T19:53:53+00:00 | 0 | 0 |
| 2026-08-18T19:54:23+00:00 | 0 | 0 |
| 2026-08-18T19:54:53+00:00 | 0 | 0 |
| 2026-08-18T19:55:23+00:00 | 0 | 0 |
| 2026-08-18T19:55:53+00:00 | 0 | 0 |
| 2026-08-18T19:56:23+00:00 | 0 | 0 |
| 2026-08-18T19:56:53+00:00 | 0 | 0 |
| 2026-08-18T19:57:23+00:00 | 0 | 0 |
| 2026-08-18T19:57:53+00:00 | 0 | 0 |
| 2026-08-18T19:58:23+00:00 | 0 | 0 |
| 2026-08-18T19:58:53+00:00 | 0 | 0 |
| 2026-08-18T19:59:23+00:00 | 0 | 0 |
| 2026-08-18T19:59:53+00:00 | 0 | 0 |
| 2026-08-18T20:00:23+00:00 | 0 | 0 |
| 2026-08-18T20:00:53+00:00 | 0 | 0 |
| 2026-08-18T20:01:23+00:00 | 0 | 0 |
| 2026-08-18T20:01:53+00:00 | 0 | 0 |
| 2026-08-18T20:02:23+00:00 | 0 | 0 |
| 2026-08-18T20:02:53+00:00 | 0 | 0 |
| 2026-08-18T20:03:23+00:00 | 0 | 0 |

---

## 방법론 메모

- 모든 HTTP 히스토그램/카운트 쿼리는 `job="backend-spring"` 라벨로 스코프했다 — blue(9091)/green(9092) 두 타겟이 `instance="backend"`로 동일해 활성 컬러가 자동 합산된다(본문 §6 한계 참고). 세션 내내 9092(green)만 `up=1`이었다.
- 스테이지 오프셋은 10차(`14-round10-*.md` §3)가 실측 로그로 확정한 관례(`pure-throughput`/`bid-only-load`: 실행 시작 +36s(SSE 있음)/+6s(SSE 없음), 각 구간 120s; `hot-auction-pattern`: +70s, 각 구간 60s)를 그대로 적용했다. 스크립트(`backend/src/test/k6/scenarios/{pure-throughput,bid-only-load,hot-auction-pattern}.js`)가 10차 이후 변경되지 않았음을 `git log --since` 확인 후 사용 — 11차 자체의 k6 로그 원문은 보존되지 않아 완전한 실측은 아니다.
- Hikari/GC/SSE/Redis/Node/MySQL 지표는 시나리오 "전체 구간" 단위로만 수집했다(스테이지 단위는 CLAUDE.md가 HTTP p95/p99에 대해서만 필수로 요구하고, 나머지는 "각 시나리오 window"로 명시했기 때문 — What to collect 섹션 참고).
- Redis Stream `event:timeline` group lag/pending은 각 시나리오 실행 구간 + 종료 후 최대 15분을 30초 간격으로 샘플링했다. pure-throughput 1000의 경우 다음 시나리오(hot-auction-pattern)와 15분 관측 윈도우가 겹쳐, "정점 이후 계속 최댓값 갱신"처럼 보이는 구간은 사실 hot-auction-pattern 자체의 새 lag였다 — 표에 나온 궤적 원본을 시각과 함께 그대로 남겨 이 중첩을 확인할 수 있게 했다(원본 타임스탬프를 보면 pure1000 자신의 lag는 18:57:41에 0으로 드레인된 뒤 18:59:41부터 hot-auction-pattern의 새 lag가 시작된다).
- 재현 방법: 이 파일과 본문에 나온 모든 쿼리 문자열은 그대로 `curl --data-urlencode 'query=...' http://localhost:9090/api/v1/query` (monitoring 호스트, `time=<epoch>` 파라미터 추가)로 재실행 가능하다. Prometheus 레텐션이 만료되지 않은 한(작성 시점 기준 당일 데이터라 문제없음) 동일한 값이 나와야 한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
