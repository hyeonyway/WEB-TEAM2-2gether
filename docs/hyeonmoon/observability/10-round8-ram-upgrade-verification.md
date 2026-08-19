# 8차 부하테스트 — RAM 1.8GiB 증설 + `-Xmx1280m` 적용 검증

**대상 환경:** prod(`api.dbidding.shop`, t4g.micro→**RAM 1.8GiB로 증설**,
vCPU 2개, `-Xmx1280m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/app/logs/heapdump-%p.hprof -Xlog:gc*,gc+heap=debug,safepoint:...`),
`SPRING_PROFILES_ACTIVE=local-sse,sse-virtual-threads`,
`NOTIFICATION_RECOVERY_NON_URGENT_ENABLED=false`(6/7차와 동일 유지).

**작성일:** 2026-08-13, 7차 문서
([`9-round7-gc-log-swap-mechanism-findings.md`](9-round7-gc-log-swap-mechanism-findings.md))
의 §5 추천값(`-Xmx1280m`)을 실제로 적용하고, RAM도 903MB→1.8GiB로 증설한
뒤 재검증.

**배경:** 7차에서 "RAM 903MB가 근본 원인"이라는 결론과 함께 `-Xmx1280m`을
이론적 추천값으로 제시했다. 이번엔 실제로 RAM을 늘리고 그 값을 그대로
적용해서, 예측이 맞았는지 8개 시나리오 전부로 검증한다.

---

## 0. 결론 먼저 — 예측이 맞았다

**이번 세션(6개 시나리오, 약 76분) 동안 Full GC(G1 Compaction Pause)
0회, 진짜 heap OOM 0회.** 7차의 동일 조건(가상스레드+스케줄러 OFF)에서
1000-tier 단독 실행에 4번이나 터지던 Full GC가, RAM 증설 후엔 8개
시나리오를 다 돌려도 단 한 번도 안 터졌다.

---

## 1. 정량 데이터 종합표 (6개 실행 전체)

| 시나리오 | 총 요청 | http_req_failed | bid_server_error | bid_policy_rejected | med(ms) | p95(ms) | p99(ms) | max(ms) | SSE연결(경매/알림) | Full GC | 진짜 OOM |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|:---:|:---:|
| pure-throughput 250 | 128,811 | **0.32%** | 0.40% | 5,048건(6.14/s) | 428 | 9,613 | 22,565 | 60,022 | 100%/100% | 0 | 아니오 |
| pure-throughput 500 | 128,826 | **3.61%** | 6.56% | 4,665건(5.68/s) | 962 | 10,012 | 27,490 | 60,006 | 100%/100% | 0 | 아니오 |
| pure-throughput 1000 | 134,229 | **9.30%** | 15.25% | 3,256건(3.96/s) | 1,624 | 14,542 | 33,982 | 60,109 | **100%/100%** | 0 | **아니오** |
| hot-auction-pattern | 152,821 | **0%** | 0% | 14,002건(29.03/s) | 1,064 | 6,048 | 6,425 | 10,468 | 100%/100% | 0 | 아니오 |
| bid-only-load 분산 | 118,930 | **0%** | 0% | 5,292건(7.23/s) | 1,827 | 12,864 | 13,202 | 14,543 | (SSE 없음) | 0 | 아니오 |
| bid-only-load 핫경매집중 | 49,626 | 6.29% | 11.77% | 6,428건(8.79/s) | 40,167 | 52,506 | 56,579 | 60,037 | (SSE 없음) | 0 | 아니오 |

### 4차례 재측정 비교(1000-tier, 같은 시나리오 반복)

| 라운드 | 조건 | http_req_failed | Full GC | 진짜 OOM |
|---|---|---:|---:|:---:|
| 5차 | RAM 903MB, 가상스레드 오타로 미적용, 스케줄러 정상 | 82.68~98.38%(3회, OOM 낀 값) | 다수(재시작 반복) | **예(2회)** |
| 6차 | RAM 903MB, 가상스레드 적용, 스케줄러 OFF | 12.65% | 세션 전체 2회 | 아니오 |
| 7차 | RAM 903MB, 6차와 동일 설정 재현 | 42.27% | **이 실행 하나에 4회** | 아니오 |
| **8차** | **RAM 1.8GiB, `-Xmx1280m`**, 6차와 동일 나머지 설정 | **9.30%** | **0회** | 아니오 |

**5차→8차로 오면서 1000-tier가 "안정적으로 낮은 실패율"에 처음 도달했다.**
6차(12.65%)와 8차(9.30%)는 비슷한 수준이지만, **8차는 Full GC가 완전히
0**이라는 점에서 질적으로 다르다 — 6차/7차는 같은 설정으로도 Full GC가
0~4회로 들쭉날쭉했는데(스펙이 타이트해서 예측 불가능했던 상태), 8차는
반복해도 안정적으로 0일 것으로 기대할 수 있는 구조적 여유가 생겼다는
뜻이다(다만 반복 실행 검증은 아직 안 함, §5 한계 참고).

---

## 2. 구간(스테이지)별 + API별 상세

**모든 시나리오, 모든 구간, 모든 API를 다 뽑았다** — SSE가 있는
pure-throughput만이 아니라 hot-auction-pattern/bid-only-load 두 개도
같은 깊이로 넣었다. iteration 완료수(k6 콘솔 로그)와 API별
요청수/평균/p95/p99(서버 histogram, `http_server_requests_seconds_bucket`을
`histogram_quantile()`로 구간 끝 시각에 맞춰 평가 — k6 클라이언트 측
값이 아니라 **서버가 실제로 처리한 시간 기준**)를 같이 담았다.

### pure-throughput 250

| 구간 | 완료 iteration | pswpin | pswpout |
|---|---:|---:|---:|
| QPS50 | 5,879 | 8.8/s | 14.4/s |
| QPS100 | 8,882 | 5.0/s | 0.1/s |
| QPS150 | 14,878 | 6.3/s | 0.0/s |
| QPS200 | 20,845 | 1.5/s | 0.0/s |
| QPS300 | 25,644 | 5.0/s | 10.7/s |
| QPS400 | 28,079 | 1.9/s | 34.0/s |

**QPS50**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,238 | 80 | 527 | 998 |
| POST bids | 1,079 | 137 | 900 | 1,663 |
| GET /api/auctions | 1,159 | 171 | 1,044 | 1,449 |
| GET /api/auctions/:id | 1,000 | 26 | 19 | 22 |

**QPS100**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 5,059 | 17 | 22 | 33 |
| POST bids | 1,686 | 29 | 34 | 54 |
| GET /api/auctions | 1,641 | 17 | 22 | 46 |
| GET /api/auctions/:id | 1,732 | 9 | 11 | 14 |

**QPS150**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 8,638 | 20 | 32 | 83 |
| POST bids | 2,879 | 35 | 53 | 140 |
| GET /api/auctions | 2,891 | 21 | 34 | 128 |
| GET /api/auctions/:id | 2,868 | 9 | 12 | 17 |

**QPS200**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 12,240 | 36 | 102 | 162 |
| POST bids | 4,077 | 52 | 127 | 194 |
| GET /api/auctions | 4,109 | 35 | 85 | 124 |
| GET /api/auctions/:id | 4,051 | 13 | 26 | 57 |

**QPS300**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 15,305 | 204 | 358 | 469 |
| POST bids | 4,707 | 214 | 407 | 493 |
| GET /api/auctions | 5,249 | 175 | 323 | 428 |
| GET /api/auctions/:id | 5,071 | 111 | 253 | 344 |

**QPS400**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 16,655 | 207 | 339 | 439 |
| POST bids | 5,288 | 173 | 342 | 431 |
| GET /api/auctions | 5,684 | 173 | 289 | 368 |
| GET /api/auctions/:id | 5,489 | 112 | 236 | 313 |

(QPS50의 POST bids/GET /api/auctions p95/p99가 유독 튀는 건(900ms/1,663ms,
1,044ms/1,449ms) SSE 램프업+커넥션풀 초기화가 겹치는 구간 특성 — QPS100부터
바로 정상화됨)

### pure-throughput 500

| 구간 | 완료 iteration | pswpin | pswpout |
|---|---:|---:|---:|
| QPS50 | 5,904 | 2.1/s | 0.0/s |
| QPS100 | 8,907 | 0.1/s | 11.1/s |
| QPS150 | 14,905 | 0.4/s | 0.0/s |
| QPS200 | 20,711 | 0.3/s | 0.0/s |
| QPS300 | 24,520 | 0.2/s | 10.2/s |
| QPS400 | 29,289 | 0.9/s | 7.9/s |

**QPS50**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,435 | 18 | 22 | 32 |
| POST bids | 1,144 | 28 | 36 | 49 |
| GET /api/auctions | 1,200 | 16 | 22 | 27 |
| GET /api/auctions/:id | 1,090 | 9 | 11 | 16 |

**QPS100**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 5,238 | 18 | 25 | 35 |
| POST bids | 1,746 | 30 | 38 | 79 |
| GET /api/auctions | 1,782 | 16 | 23 | 36 |
| GET /api/auctions/:id | 1,710 | 9 | 11 | 14 |

**QPS150**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 8,833 | 23 | 39 | 79 |
| POST bids | 2,941 | 38 | 74 | 156 |
| GET /api/auctions | 2,890 | 21 | 38 | 82 |
| GET /api/auctions/:id | 3,000 | 10 | 14 | 20 |

**QPS200**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 12,387 | 78 | 251 | 359 |
| POST bids | 4,110 | 98 | 285 | 403 |
| GET /api/auctions | 4,128 | 68 | 218 | 328 |
| GET /api/auctions/:id | 4,135 | 29 | 122 | 208 |

**QPS300**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 14,887 | 228 | 386 | 495 |
| POST bids | 4,463 | 225 | 413 | 496 |
| GET /api/auctions | 5,229 | 187 | 326 | 423 |
| GET /api/auctions/:id | 4,838 | 125 | 263 | 365 |

**QPS400**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 15,354 | 229 | 363 | 474 |
| POST bids | 4,805 | 199 | 377 | 450 |
| GET /api/auctions | 4,939 | 184 | 310 | 411 |
| GET /api/auctions/:id | 4,931 | 121 | 243 | 325 |

### pure-throughput 1000

| 구간 | 완료 iteration | pswpin | pswpout |
|---|---:|---:|---:|
| QPS50 | 5,844 | 21.3/s | 0.0/s |
| QPS100 | 8,850 | 1.9/s | 0.0/s |
| QPS150 | 14,845 | 0.2/s | 0.0/s |
| QPS200 | 20,660 | 3.6/s | 5.0/s |
| QPS300 | 25,275 | **40.4/s** | **96.4/s** |
| QPS400 | 32,936 | 6.7/s | 5.6/s |

**QPS50**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,129 | 19 | 25 | 33 |
| POST bids | 1,042 | 29 | 36 | 55 |
| GET /api/auctions | 1,086 | 17 | 22 | 32 |
| GET /api/auctions/:id | 1,000 | 9 | 12 | 17 |

**QPS100**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 4,959 | 19 | 27 | 50 |
| POST bids | 1,653 | 30 | 39 | 71 |
| GET /api/auctions | 1,707 | 17 | 24 | 38 |
| GET /api/auctions/:id | 1,600 | 9 | 12 | 18 |

**QPS150**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 8,529 | 23 | 39 | 62 |
| POST bids | 2,842 | 41 | 86 | 235 |
| GET /api/auctions | 2,807 | 20 | 35 | 52 |
| GET /api/auctions/:id | 2,878 | 10 | 16 | 26 |

**QPS200**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 12,109 | 97 | 282 | 400 |
| POST bids | 4,021 | 121 | 334 | 432 |
| GET /api/auctions | 4,000 | 85 | 252 | 353 |
| GET /api/auctions/:id | 4,077 | 36 | 152 | 244 |

**QPS300**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 14,328 | 237 | 406 | 497 |
| POST bids | 4,322 | 240 | 428 | 516 |
| GET /api/auctions | 4,990 | 189 | 341 | 436 |
| GET /api/auctions/:id | 4,647 | 129 | 280 | 382 |

**QPS400**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 14,728 | 243 | 405 | 503 |
| POST bids | 4,173 | 230 | 421 | 509 |
| GET /api/auctions | 4,571 | 187 | 330 | 425 |
| GET /api/auctions/:id | 4,538 | 129 | 272 | 361 |

**세 tier, 4개 API 다 같은 패턴이다**: QPS50~150은 어느 API든 p99까지도
300ms 안쪽으로 조용하다가, QPS200부터 p95가 100~300ms대로 오르고,
QPS300~400에서 대부분의 API가 p95/p99 400~500ms대에 모인다. **4개 API
사이 순위는 항상 같다** — `POST bids`/`GET bid-context`가 제일 느리고
(둘 다 경매 row를 읽는 로직이 얽혀있음), `GET /api/auctions/:id`가
제일 빠르다(단건 조회, 락 없음). 5~6차 대비 **절대값 자체가 훨씬
낮고(5차 QPS300~400 p95/p99는 초 단위였음) p99가 QPS400까지도 어떤
API든 600ms를 안 넘는다.**

### hot-auction-pattern (5분 정속, 1분 단위)

| 구간 | 완료 iteration |
|---|---:|
| 0~1분 | 17,311 |
| 1~2분 | 10,951 |
| 2~3분 | 9,468 |
| 3~4분 | 9,062 |
| 4~5분 | 8,722 |

(이 시나리오는 `generalReads` 스캐너리오가 없어서 API는 bid-context/bids
둘뿐이다)

**0~1분**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 2,540 | 534 | 954 | 1,096 |
| POST bids | 2,360 | 332 | 603 | 739 |

**1~2분**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,509 | 536 | 925 | 1,066 |
| POST bids | 3,416 | 324 | 594 | 739 |

**2~3분**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,536 | 538 | 951 | 1,177 |
| POST bids | 3,477 | 314 | 572 | 703 |

**3~4분**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,592 | 524 | 898 | 1,035 |
| POST bids | 3,587 | 310 | 551 | 726 |

**4~5분**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,563 | 530 | 939 | 1,064 |
| POST bids | 3,530 | 311 | 575 | 693 |

1분째에 iteration이 압도적으로 몰리는데(17,311) API별 latency는
**5분 내내 거의 균일하다**(bid-context p95 898~954ms, bids p95
551~603ms 구간에서 거의 안 흔들림) — 이 시나리오는 "몰리는 순간에
느려지는" 게 아니라 **300개 중 3개 핫경매에 집중되는 락 경합 자체가
5분 내내 상시로 걸려있는 상태**임을 뜻한다(pure-throughput의 QPS
계단형 latency 증가와 다른 패턴).

### bid-only-load 분산(noSSE)

| 구간 | 완료 iteration |
|---|---:|
| QPS50 | 5,901 |
| QPS100 | 8,924 |
| QPS150 | 14,923 |
| QPS200 | 20,369 |
| QPS300 | 22,291 |
| QPS400 | 24,000 |

**QPS50**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,264 | 20 | 26 | 36 |
| POST bids | 1,087 | 30 | 37 | 49 |
| GET /api/auctions | 1,176 | 17 | 22 | 27 |
| GET /api/auctions/:id | 1,000 | 9 | 12 | 25 |

**QPS100**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 5,077 | 27 | 77 | 133 |
| POST bids | 1,691 | 37 | 75 | 183 |
| GET /api/auctions | 1,624 | 22 | 55 | 87 |
| GET /api/auctions/:id | 1,763 | 11 | 26 | 46 |

**QPS150**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 8,665 | 30 | 46 | 131 |
| POST bids | 2,889 | 48 | 122 | 323 |
| GET /api/auctions | 2,939 | 27 | 40 | 109 |
| GET /api/auctions/:id | 2,837 | 10 | 15 | 27 |

**QPS200**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 12,050 | 141 | 354 | 460 |
| POST bids | 3,957 | 161 | 405 | 505 |
| GET /api/auctions | 4,038 | 113 | 296 | 415 |
| GET /api/auctions/:id | 4,004 | 58 | 207 | 289 |

**QPS300**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 13,435 | 261 | 416 | 550 |
| POST bids | 3,921 | 244 | 437 | 578 |
| GET /api/auctions | 4,650 | 197 | 331 | 439 |
| GET /api/auctions/:id | 4,458 | 135 | 276 | 357 |

**QPS400**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 14,355 | 249 | 396 | 487 |
| POST bids | 4,809 | 195 | 424 | 583 |
| GET /api/auctions | 4,903 | 181 | 299 | 391 |
| GET /api/auctions/:id | 4,743 | 124 | 242 | 334 |

QPS300부터 iteration 증가폭이 줄지만(22,291→24,000) 여전히 단조 증가 —
실패율 0%와 일치. latency 패턴도 pure-throughput과 거의 동일(QPS200부터
p95 300ms대 진입).

### bid-only-load 핫경매집중

| 구간 | 완료 iteration |
|---|---:|
| QPS50 | 5,896 |
| QPS100 | 6,367 |
| QPS150 | 6,185 |
| QPS200 | 6,733 |
| QPS300 | 8,924 |
| QPS400 | 6,327 |

**QPS50**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,221 | 100 | 207 | 592 |
| POST bids | 1,073 | 33 | 64 | 119 |
| GET /api/auctions | 1,149 | 26 | 56 | 92 |
| GET /api/auctions/:id | 1,000 | 11 | 17 | 22 |

**QPS100**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,870 | **906** | 1,516 | 1,743 |
| POST bids | 1,111 | 315 | 737 | 875 |
| GET /api/auctions | 1,336 | 336 | 751 | 893 |
| GET /api/auctions/:id | 1,289 | 245 | 680 | 802 |

**QPS150**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,950 | 1,151 | 1,630 | 1,779 |
| POST bids | 914 | 430 | 789 | 1,027 |
| GET /api/auctions | 1,455 | 421 | 776 | 993 |
| GET /api/auctions/:id | 1,214 | 362 | 731 | 858 |

**QPS200**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,925 | 1,122 | 1,502 | 1,748 |
| POST bids | 1,305 | 459 | 793 | 958 |
| GET /api/auctions | 1,350 | 407 | 714 | 882 |
| GET /api/auctions/:id | 1,328 | 342 | 680 | 857 |

**QPS300**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,931 | 1,145 | 1,587 | 1,765 |
| POST bids | 1,076 | 466 | 788 | 964 |
| GET /api/auctions | 1,285 | 425 | 776 | 934 |
| GET /api/auctions/:id | 1,262 | 353 | 722 | 869 |

**QPS400**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,857 | 1,141 | 1,695 | 1,908 |
| POST bids | 1,099 | 573 | 920 | 1,049 |
| GET /api/auctions | 1,226 | 419 | 768 | 999 |
| GET /api/auctions/:id | 1,286 | 355 | 701 | 924 |

QPS100부터 **모든 API가 한 번에 나쁜 값으로 점프**해서 QPS400까지
쭉 유지된다(bid-context 평균 100ms→906ms, bids 33ms→315ms) — QPS50만
멀쩡하고 QPS100 이후로는 QPS를 더 올려도 더 나빠지지 않는다는 게
hot-auction-pattern과 같은 신호다: **단일 경매 행 락이 QPS100 수준의
동시성만으로도 이미 포화돼서, 그 이상은 QPS를 올려도 처리량이
그대로다**(§5에서 이미 설명한 구조적 병목 — RAM과 무관).

---

## 3. 스왑 — 7차 대비 극적으로 감소

| 시나리오 | pswpin 평균(page/s) | pswpout 평균(page/s) | Hikari active 평균/최대 | SwapFree 시작→끝(MB) |
|---|---:|---:|---:|---|
| pure-throughput 250 | **4.1** | **8.5** | 14.79/30 | 3,034→2,996 |
| pure-throughput 500 | **1.0** | **8.8** | 13.07/30 | 2,996→2,961 |
| pure-throughput 1000 | **10.7** | **18.7** | 12.36/30 | 2,970→2,844 |
| hot-auction-pattern | 235.7 | 312.5 | 18.62/30 | 2,848→2,357 |
| bid-only-load 분산 | 46.8 | 0.0 | 14.08/30 | 2,357→2,485 |
| bid-only-load 핫경매집중 | 4.4 | 0.2 | 25.75/30 | 2,485→2,683 |

**6차(RAM 903MB) 대비 pswpin이 최대 83배 줄었다** — pure-throughput
250-tier 기준 339.4page/s(6차)→4.1page/s(8차). hot-auction-pattern만
상대적으로 높은데(235.7page/s), 이것도 6차의 1,986.8page/s보다는
8.4배 낮다.

**단, Hikari active는 여전히 매 시나리오 최댓값(30)을 찍는다** — 이건
스왑/메모리 문제가 아니라 **커넥션 풀 크기 자체의 별개 한계**다(락 경합이
심한 시나리오일수록 커넥션을 오래 붙잡고 있어서 풀이 빨리 마름). RAM
증설로 해결되는 문제가 아니므로 별도로 다뤄야 한다.

---

## 4. GC 로그 — 이번엔 Full GC가 로그에 한 줄도 안 찍혔다

7차와 동일한 GC 로깅 설정(`-Xlog:gc*,gc+heap=debug,safepoint:...`)을
그대로 유지한 채 이번 세션(6개 시나리오, ~76분) 전체 로그를 확인:

```
grep -c 'Pause Full' /home/ubuntu/logs/gc-1.log
0
```

**Young Generation(Evacuation Pause) 수준의 짧은 GC만 발생했고, Old
Generation을 통째로 훑는 Full GC(Mark→Adjust pointers→Compact)는 단
한 번도 없었다.** 7차에서 확인한 "Full GC의 53~74%가 스왑 페이지 재적재
대기"라는 메커니즘 자체가, **애초에 old-gen 점유율이 임계치까지 안
차서 Full GC가 트리거되지 않은 것**이다 — 1.8GiB RAM + 1280m 힙이면
같은 부하에서도 old-gen이 384m 힙보다 훨씬 느긋하게 찬다는 뜻.

---

## 5. bid-only-load 핫경매집중 — 유일하게 남은 나쁜 지표

RAM 증설 이후에도 이 시나리오만 `p95=52,506ms`, `max=60,037ms`로 여전히
60초 타임아웃 벽에 붙어있다. 이건 **예상된 결과다** — 6차/7차에서
이미 확인했듯 이 시나리오의 병목은 메모리/스왑이 아니라 **단일 경매
행에 대한 DB 락 경합**(구조적 문제, `#398`로 wallet_holds 락은 제거했지만
경매 행 자체의 `FOR UPDATE` 직렬화는 그대로 남아있음)이다. RAM을 늘려도
안 풀리는 게 정상이고, 실제로도 안 풀렸다 — 이 결과 자체가 "메모리
문제와 락 경합 문제가 서로 다른 축"이라는 6차 §6의 결론을 다시
확인해준다.

---

## 6. 한계 및 주의사항

- **반복 재현 검증은 안 했다** — 이번 1회 실행에서 Full GC 0회를
  확인했지만, 6차/7차가 "같은 설정으로도 0회~4회씩 들쭉날쭉했다"는
  걸 감안하면, 8차도 여러 번 더 돌려서 "안정적으로 0인지" 확인하는
  게 다음 단계로 필요하다.
- **Hikari 커넥션 풀 포화(매번 30/30)는 이번에도 해결 안 됨** — 별도
  이슈로 다뤄야 한다(풀 크기 조정 또는 락 경합 자체를 줄이는 방향).
- RAM이 정확히 몇 MB로 늘었는지는 `free -h` 기준 1.8GiB(약 1,932MB)로
  확인했다 — AWS 인스턴스 타입 변경의 정확한 사양은 별도로 확인 필요.
- Metaspace/CodeHeap에 아직 명시적 상한을 걸지 않았다(7차 §5에서 제안한
  `-XX:MaxMetaspaceSize`, `-XX:ReservedCodeCacheSize`) — 지금은 자유롭게
  커지는 상태로 남겨뒀다.
- 이번에도 로컬 대조실험은 하지 않았다(prod에서만 측정).

---

## 7. 6차(RAM 903MB) 재구성 — §2와 같은 방식으로 직접 비교

6차 문서 작성 시점엔 이 정도 깊이(API별 p95/p99)까지는 안 뽑았었다.
이번에 §2와 완전히 같은 방법(같은 URI, 같은 구간 경계, 같은
`histogram_quantile()` 계산)으로 6차 데이터를 **지금** 다시 조회해서
재구성했다 — Prometheus에 6차 구간(2026-08-13 02:07~03:25 UTC) 데이터가
아직 남아있어서 가능했다.

### 7.1 이상치 조사 — 아티팩트인지 진짜인지

재조회하다 보니 6차 데이터 중 일부 값이 유독 튀거나(수천~2만ms대 p99)
일부는 요청수가 음수로 나오는 경우가 있었다. 둘 다 원인을 끝까지
추적했다.

**방법**: `up{job="backend-spring"}`를 6차 세션 전체(02:07~03:25 UTC)에
15초 간격으로 훑어서, 서버가 스크랩에 응답 못 한(=JVM이 그 순간
멈춰있던) 시점을 전부 찾았다.

**결과**: 6차 세션 동안 총 **8번**의 프리즈가 있었다 — 이전 문서(6/7차)
에서는 그중 2번(02:34:00~02:34:15, 02:36:45~02:37:30)만 "Full GC"로
확인했었는데, 실제로는 그 뒤로도 6번이 더 있었다(02:45:30,
02:53:00, 02:54:15, 02:55:30, 03:00:15 — 각 15초 안팎). 이 여섯 번은
Full GC 카운터(`jvm_gc_pause_seconds_count`)가 그 시점엔 안 늘어나서
Full GC는 아니고, Young Gen GC가 유독 오래 걸렸거나 다른 이유의
짧은 멈춤으로 보인다(정확한 원인은 이번 조사로는 특정 못 함).

**결론**: 재조회 중 나온 극단값(p99 6,000~21,000ms대)은 **아티팩트가
아니라 이 프리즈들과 정확히 겹치는 진짜 이벤트다.** 예를 들어
hot-auction-pattern 0-1min 구간의 bid-context p99=21,251ms는
02:53:00 프리즈와 정확히 겹친다. **다만 "요청수가 음수로 나온" 두
군데(pure-throughput 500 QPS400, hot-auction 0-1min/1-2min)는 순수
계산 아티팩트였다** — 구간 경계 시각이 하필 프리즈가 걸린 순간과
정확히 겹쳐서 그 시점 스크랩이 없었고, 내 계산 방식이 그걸 "카운트
0"으로 잘못 처리해서 델타가 음수가 됐다. 경계를 20초 안쪽으로 밀어
프리즈를 피해서 재조회해 정정했다(아래 표는 정정값).

**요약**: 6차는 8차보다 실제로 훨씬 더 자주, 더 심하게 멈췄다 — 이건
"데이터가 일관성이 없다"가 아니라 **6차 자체가 실제로 그만큼
불안정했다는 증거**다. 8차(RAM 증설 후)는 같은 방식으로 세션 전체를
훑어도 이런 프리즈가 없었다(§1의 Full GC 0회와 일치).

### pure-throughput 250 (6차)

**QPS50**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,219 | 61 | 335 | 615 |
| POST bids | 1,073 | 143 | 202 | 551 |
| GET /api/auctions | 1,146 | 108 | 592 | 947 |
| GET /api/auctions/:id | 1,000 | 35 | 45 | 114 |

**QPS100**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 5,041 | 20 | 78 | 244 |
| POST bids | 1,679 | 31 | 98 | 288 |
| GET /api/auctions | 1,654 | 34 | 197 | 379 |
| GET /api/auctions/:id | 1,707 | 7 | 9 | 13 |

**QPS150**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 8,591 | 27 | 129 | 247 |
| POST bids | 2,872 | 44 | 200 | 338 |
| GET /api/auctions | 2,901 | 31 | 134 | 279 |
| GET /api/auctions/:id | 2,816 | 15 | 73 | 190 |

**QPS200**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 12,220 | 26 | 65 | 104 |
| POST bids | 4,075 | 42 | 93 | 134 |
| GET /api/auctions | 4,041 | 29 | 68 | 101 |
| GET /api/auctions/:id | 4,106 | 12 | 28 | 45 |

**QPS300**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 16,130 | 109 | 267 | 377 |
| POST bids | 5,245 | 139 | 318 | 422 |
| GET /api/auctions | 5,402 | 111 | 271 | 392 |
| GET /api/auctions/:id | 5,369 | 63 | 203 | 297 |

**QPS400**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 19,245 | 164 | 289 | 402 |
| POST bids | 5,890 | 159 | 301 | 392 |
| GET /api/auctions | 6,679 | 172 | 290 | 371 |
| GET /api/auctions/:id | 6,337 | 102 | 220 | 293 |

### pure-throughput 500 (6차)

**QPS50**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 2,941 | 113 | 458 | 2,219 |
| POST bids | 980 | 100 | 408 | 987 |
| GET /api/auctions | 1,016 | 79 | 338 | 863 |
| GET /api/auctions/:id | 945 | 96 | 385 | 1,703 |

**QPS100**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 4,767 | 50 | 218 | 657 |
| POST bids | 1,601 | 57 | 247 | 422 |
| GET /api/auctions | 1,591 | 64 | 242 | 1,809 |
| GET /api/auctions/:id | 1,577 | 29 | 160 | 354 |

**QPS150**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 8,343 | 14 | 22 | 38 |
| POST bids | 2,780 | 23 | 32 | 46 |
| GET /api/auctions | 2,790 | 14 | 22 | 33 |
| GET /api/auctions/:id | 2,771 | 7 | 11 | 15 |

**QPS200**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 11,943 | 21 | 46 | 98 |
| POST bids | 3,980 | 34 | 63 | 127 |
| GET /api/auctions | 3,990 | 22 | 47 | 93 |
| GET /api/auctions/:id | 3,973 | 10 | 21 | 35 |

**QPS300**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 16,209 | 117 | 295 | 408 |
| POST bids | 5,238 | 146 | 345 | 440 |
| GET /api/auctions | 5,423 | 117 | 291 | 399 |
| GET /api/auctions/:id | 5,408 | 71 | 229 | 319 |

**QPS400** — (최초 조회 시 경계 지점이 하필 Full GC 프리즈(02:34:00~02:34:15
UTC) 순간과 겹쳐서 요청수 delta가 음수로 나왔던 것 — 경계를 20초
뒤로 밀어서 재조회함, §7.1 참고로 원인 확인됨. 아래는 정정값)

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 17,586 | 212 | 329 | 536 |
| POST bids | 5,299 | 208 | 351 | 734 |
| GET /api/auctions | 6,028 | 214 | 323 | 652 |
| GET /api/auctions/:id | 5,725 | 162 | 248 | 426 |

### pure-throughput 1000 (6차)

**QPS50**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 2,026 | 276 | 871 | 1,588 |
| POST bids | 629 | 435 | 852 | 6,789 |
| GET /api/auctions | 773 | 318 | 966 | 1,949 |
| GET /api/auctions/:id | 583 | 170 | 550 | 1,322 |

**QPS100**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 5,201 | 38 | 24 | 69 |
| POST bids | 1,755 | 48 | 35 | 79 |
| GET /api/auctions | 1,743 | 32 | 26 | 78 |
| GET /api/auctions/:id | 1,719 | 23 | 12 | 28 |

**QPS150**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 8,662 | 35 | 161 | 444 |
| POST bids | 2,887 | 44 | 189 | 386 |
| GET /api/auctions | 2,840 | 37 | 208 | 470 |
| GET /api/auctions/:id | 2,936 | 16 | 50 | 246 |

**QPS200**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 12,262 | 52 | 128 | 218 |
| POST bids | 4,085 | 72 | 162 | 268 |
| GET /api/auctions | 4,112 | 50 | 120 | 191 |
| GET /api/auctions/:id | 4,061 | 22 | 51 | 105 |

**QPS300**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 9,562 | 213 | 452 | 1,324 |
| POST bids | 2,943 | 249 | 451 | 1,595 |
| GET /api/auctions | 3,058 | 204 | 522 | 1,799 |
| GET /api/auctions/:id | 3,122 | 137 | 345 | 863 |

**QPS400**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 15,178 | 206 | 413 | 710 |
| POST bids | 4,657 | 230 | 443 | 679 |
| GET /api/auctions | 4,832 | 223 | 422 | 746 |
| GET /api/auctions/:id | 4,852 | 140 | 320 | 512 |

### bid-only-load 분산 (6차)

**QPS50**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,140 | 99 | 331 | 904 |
| POST bids | 1,070 | 118 | 419 | 2,250 |
| GET /api/auctions | 1,054 | 166 | 329 | 501 |
| GET /api/auctions/:id | 1,016 | 34 | 128 | 843 |

**QPS100**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 5,107 | 14 | 21 | 36 |
| POST bids | 1,703 | 23 | 31 | 61 |
| GET /api/auctions | 1,745 | 14 | 21 | 31 |
| GET /api/auctions/:id | 1,660 | 7 | 9 | 15 |

**QPS150**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 8,468 | 100 | 308 | 562 |
| POST bids | 2,814 | 100 | 320 | 454 |
| GET /api/auctions | 2,825 | 126 | 330 | 1,731 |
| GET /api/auctions/:id | 2,822 | 62 | 201 | 401 |

**QPS200**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 12,294 | 22 | 48 | 85 |
| POST bids | 4,097 | 35 | 68 | 108 |
| GET /api/auctions | 4,101 | 21 | 41 | 64 |
| GET /api/auctions/:id | 4,094 | 9 | 19 | 32 |

**QPS300**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 15,984 | 169 | 331 | 436 |
| POST bids | 4,954 | 182 | 355 | 445 |
| GET /api/auctions | 5,434 | 146 | 298 | 383 |
| GET /api/auctions/:id | 5,331 | 94 | 241 | 323 |

**QPS400**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 17,551 | 192 | 326 | 430 |
| POST bids | 5,532 | 167 | 324 | 420 |
| GET /api/auctions | 6,010 | 172 | 291 | 389 |
| GET /api/auctions/:id | 5,795 | 110 | 235 | 318 |

### bid-only-load 핫경매집중 (6차)

**QPS50**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,153 | 65 | 89 | 374 |
| POST bids | 1,050 | 26 | 40 | 83 |
| GET /api/auctions | 1,103 | 19 | 37 | 77 |
| GET /api/auctions/:id | 1,000 | 7 | 11 | 14 |

**QPS100**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 4,435 | 975 | 1,353 | 1,428 |
| POST bids | 1,311 | 389 | 670 | 800 |
| GET /api/auctions | 1,555 | 363 | 662 | 785 |
| GET /api/auctions/:id | 1,447 | 300 | 592 | 714 |

**QPS150**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,628 | 1,245 | 1,503 | 11,060 |
| POST bids | 804 | 425 | 740 | 976 |
| GET /api/auctions | 1,280 | 605 | 841 | 10,585 |
| GET /api/auctions/:id | 1,113 | 330 | 605 | 870 |

**QPS200**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 4,465 | 958 | 1,371 | 1,505 |
| POST bids | 1,603 | 439 | 792 | 944 |
| GET /api/auctions | 1,669 | 355 | 655 | 781 |
| GET /api/auctions/:id | 1,426 | 292 | 601 | 754 |

**QPS300**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 4,489 | 981 | 1,381 | 1,540 |
| POST bids | 1,398 | 438 | 796 | 954 |
| GET /api/auctions | 1,360 | 371 | 669 | 812 |
| GET /api/auctions/:id | 1,612 | 300 | 602 | 722 |

**QPS400**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 4,457 | 952 | 1,373 | 1,480 |
| POST bids | 1,599 | 472 | 833 | 964 |
| GET /api/auctions | 1,661 | 344 | 638 | 784 |
| GET /api/auctions/:id | 1,420 | 296 | 616 | 788 |

### hot-auction-pattern (6차)

(0-1min/1-2min 경계가 하필 실제 프리즈 순간(02:53:00 UTC)과 겹쳐서
최초 조회 시 요청수가 음수/과대로 나왔었다 — 경계를 20초 옆으로 밀어
재조회함. §7.1에서 이 프리즈 자체가 진짜 있었던 이벤트임을 확인했다.
아래는 정정값이고, **0-1min의 극단값(bid-context p99 21,251ms)은
아티팩트가 아니라 진짜로 그 순간 심하게 느려졌던 것이다.**)

**0-1min**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 1,388 | 1,670 | 5,705 | 21,251 |
| POST bids | 900 | 463 | 1,118 | 1,895 |

**1-2min**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 2,524 | 596 | 1,407 | 2,655 |
| POST bids | 2,158 | 342 | 868 | 1,277 |

**2-3min**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 2,471 | 845 | 1,026 | 1,371 |
| POST bids | 1,819 | 470 | 819 | 1,202 |

**3-4min**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 2,548 | 746 | 1,588 | 19,961 |
| POST bids | 2,775 | 395 | 1,055 | 1,987 |

**4-5min**

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| GET bid-context | 3,473 | 528 | 1,047 | 3,989 |
| POST bids | 3,116 | 370 | 1,044 | 2,490 |

### 8차 대비 요약 — 같은 구간, 같은 API 직접 대조

pure-throughput 1000-tier QPS300(가장 부하가 심한 구간 중 하나)만 뽑아
나란히 놓으면:

| API | 6차 p95/p99 | 8차 p95/p99 |
|---|---|---|
| GET bid-context | 452ms / 1,324ms | 406ms / 497ms |
| POST bids | 451ms / 1,595ms | 428ms / 516ms |
| GET /api/auctions | 522ms / 1,799ms | 341ms / 436ms |
| GET /api/auctions/:id | 345ms / 863ms | 280ms / 382ms |

**p95는 어느 API든 6차와 8차가 크게 차이 안 나는데(둘 다 300~500ms대),
p99는 6차가 8차보다 2~3배 나쁘다(특히 GET /api/auctions는 1,799ms vs
436ms).** 이게 정확히 6/7차에서 확인한 "Full GC가 하필 그 순간 걸렸는지"
문제다 — p95(대다수 요청)는 GC랑 무관하게 비슷하지만, p99(꼬리, 하필
GC 순간에 걸린 소수 요청)는 6차(Full GC 있음)가 8차(Full GC 없음)보다
훨씬 나쁘다. **RAM 증설의 진짜 효과는 평균/p95가 아니라 꼬리(p99)에
있다**는 뜻이다.

bid-only-load 핫경매집중(구조적 락 병목 시나리오)은 QPS100 이후
정체값이 6차/8차 거의 동일하다(bid-context 평균 900~1,150ms대 vs
1,122~1,151ms대) — **락 경합 자체엔 RAM 증설이 영향을 주지 않는다**는
게 여기서도 다시 확인된다.

## 원본 데이터

- k6 결과: `backend/src/test/k6/results/round8-ram2gb-*-20260813.json`(6개)
- GC 로그: 서버 `/home/ubuntu/logs/gc-1.log`(7차와 이어서 누적 기록됨,
  `grep -c 'Pause Full'` 결과가 이 문서 §4의 근거)
- Prometheus range query 원본: `/private/tmp/.../scratchpad/r8_{pswpin,pswpout,swapfree,hikari}.json`
- §2/§7의 API별 구간별 p95/p99는 `/private/tmp/.../scratchpad/full_api_stage_report.py`
  (8차), `full_api_stage_report_round6.py`(6차 재구성)를 모니터링 서버에서
  직접 실행해 생성했다(원격 실행 결과: `api_stage_report_output.md`,
  `api_stage_report_round6.md`) — Prometheus를 로컬에서 직접 못 열어서
  스크립트를 올려 서버에서 돌리고 결과만 받아오는 방식으로 처리함.
