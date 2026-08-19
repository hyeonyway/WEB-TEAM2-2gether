# 9차 부하테스트 — Redis 프로필 전체 재측정 + DB/Redis 원본 데이터 추가

**대상 환경:** prod(`api.dbidding.shop`, RAM 1.8GiB, vCPU 2개,
`-Xmx1280m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/app/logs/heapdump-%p.hprof -Xlog:gc*`),
`SPRING_PROFILES_ACTIVE=redis,sse-virtual-threads`, `HikariCP max=30`.
백엔드 컨테이너는 이 세션 시작 약 1시간 10분 전(`PR #531` 머지 직후) 재배포됨 —
웜업 충분.

**작성일:** 2026-08-16.

**배경:** #529(경매 목록 조회 Redis 중복조회 제거)가 이날 main에 머지된 직후,
8차 문서([`10-round8-ram-upgrade-verification.md`](10-round8-ram-upgrade-verification.md))와
동일한 6개 시나리오 표준 실행 세트를 다시 돌려 (1) #529 수정 효과와 (2) 8차
이후 현재 실제 prod 배포 상태의 전반적 건강도를 함께 확인한다. 이번 회차부터
서버/Prometheus 데이터에 더해 **DB(MySQL)와 Redis 원본 데이터도 직접 수집**한다.

**⚠️ 8차와 완전한 A/B가 아님 — 반드시 먼저 읽을 것:**

**정정(2026-08-16, 사용자 지적으로 재확인):** 이 문서는 원래 9차가
`SPRING_PROFILES_ACTIVE=redis` 단일 프로필(가상스레드 꺼짐)로 돌았다고
적었었다. **틀렸다.** `docker inspect`의 JSON 배열을 `tr ','`로 줄바꿈
처리하면서, 그 배열 안 한 원소의 값 자체에 들어있던 콤마
(`redis,sse-virtual-threads`)까지 같이 쪼개져서 `sse-virtual-threads`
쪽이 grep에 안 걸렸다 — 파싱 방법 자체의 실수였다. `python3 json.loads`로
다시 확인한 실제 값은 `SPRING_PROFILES_ACTIVE=redis,sse-virtual-threads`다.
**가상스레드는 8차와 9차 내내 계속 켜져 있었고, 한 번도 꺼진 적 없다.**

그래서 8차와 9차의 실제 차이는 다음 두 가지뿐이다:
(a) SSE fan-out 방식 — 8차는 `local-sse`(JVM 로컬 broadcast), 9차는
`redis`(Redis Pub/Sub 기반 다중 인스턴스 릴레이).
(b) `NOTIFICATION_RECOVERY_NON_URGENT_ENABLED` — 8차는 `false`로 꺼둠,
9차는 해당 env var 자체가 없어서 애플리케이션 기본값(`true`, 켜짐)으로
돈다.
가상스레드 여부는 두 라운드가 동일해서 더 이상 차이 목록에 없다.

이 차이가 결과에 실제로 영향을 준 지점이 있는지는 §5에서 다룬다. 8차
비교는 "RAM 증설 효과가 재현되는지"를 보는 용도로 쓰고, 절대값 차이를 전부
"이번에 뭔가 고쳐졌다"는 식으로 해석하면 안 된다 — 어떤 프로필을 켰는지부터
먼저 확인해야 한다(그리고 그 확인 자체를 한 번 틀렸다는 게 이번에 드러난
셈이다 — 파싱 결과를 다시 원본 형식으로 검증하는 습관이 필요하다).

**회차 번호에 대해:** `#529` 설계 문서가 언급하는 "9차 부하테스트"는 #529
수정 *전* 목록 조회 문제를 발견했던 바로 그 측정이고, 이 문서가 그 9차를
정식 라운드 문서로(8차와 같은 깊이로, DB/Redis 원본 데이터까지 더해) 처음
기록한 것이다.

---

## 0. 결론 먼저

1. **`GET /api/auctions`(목록) p95가 서버 실측 기준 약 300~630ms대로 안정됨**
   (§2 각 구간 표 참고). #529 수정 전 이 9차 세션에서 측정했던 기준선(~1,100~1,400ms)과 비교하면
   대략 55~75% 감소. 다른 3개 API(상세/bid-context/bids)보다 여전히 2~4배
   높지만(항목당 Redis read가 여전히 20회 남아있어 구조적으로 당연함), 이전의
   8~10배 격차는 사라졌다.
2. **8차가 "구조적이라 RAM으로 안 풀린다"고 결론 낸 `bid-only-load
   핫경매집중`의 DB 락 경합(p95=52,506ms, max=60,037ms)이 이번엔 완전히
   사라졌다**(p95 90~140ms대, QPS400까지). 원인은 RAM이 아니라 **프로필에
   따라 다른 `BidExecutor` 빈이 뜬다는 것**이었다 — 8차는 `DbBidExecutor`
   (`@Profile("!redis")`, MySQL `FOR UPDATE` 직접 락)가, 이번엔
   `RedisBidExecutor`(`@Profile("redis")`, Redis Lua 단독 처리, 입찰 승인
   경로에 MySQL이 아예 안 낌)가 떴다. 근거는 §5.
3. **같은 이유로 HikariCP 커넥션 풀도 이번엔 전혀 안 참** — 6개 시나리오
   전체에서 active 최댓값이 **10/30**(8차는 6개 시나리오 전부 30/30). 8차가
   "RAM 증설로도 못 푼 별도 과제"로 남겨둔 문제이기도 하다.
4. **Full GC 0회, 진짜 OOM 0회** — 8차와 동일(§4). RAM 1.8GiB 유지 확인.
5. **(운영상 발견, 부하테스트 결과 아님) 이 세션 도중 프로젝션 파이프라인이
   완전히 멈춰있는 상태를 발견함** — MySQL `timeline_events` 테이블에 콜드시드
   rewind(#535)로 추정되는 이전최고입찰자 불일치 오류 1건이 낀 채 62,500건
   이상의 후속 이벤트가 막혀 쌓여있었다. 영향받은 경매 100개는 **전부 k6
   시드 테스트 경매**(`seller_id >= 900000`)로 확인, 실고객 데이터 0건. Redis가
   입찰 판정의 authoritative 소스라 유저 체감 영향은 없지만, 이 부하테스트
   결과에서 MySQL 프로젝션 지연/Hikari 사용량이 실제보다 낮게 나왔을 가능성은
   배제 못 한다(§6 한계 참고). 정리는 이 부하테스트가 끝난 뒤 별도로 진행함.

---

## 1. 정량 데이터 종합표 (6개 실행 전체, k6 클라이언트 측 `http_req_duration`)

| 시나리오 | 총 요청 | http_req_failed | bid_server_error | bid_policy_rejected | med(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| pure-throughput 250 | 133,935 | 0.19% | 0.27% | 7,408건(9.0/s) | 382 | 8,527 | 17,522 | 60,003 |
| pure-throughput 500 | 135,111 | 2.47% | 5.16% | 7,025건(8.51/s) | 580 | 10,008 | 20,228 | 59,999 |
| pure-throughput 1000 | 134,567 | 12.55% | 24.68% | 4,175건(5.03/s) | 1,866 | 10,011 | 29,628 | 60,057 |
| hot-auction-pattern | 123,943 | 1.32% | 0% | 6,372건(13.1/s) | 262 | 15,707 | 30,002 | 165,568 |
| bid-only-load 분산 | 132,758 | 0.00% | 0% | 6,358건(8.59/s) | 306 | 9,774 | 19,976 | 20,357 |
| bid-only-load 핫경매집중 | 141,632 | 0.00% | 0% | 17,737건(24.09/s) | 141 | 8,584 | 9,297 | 9,674 |

**이 표의 p95/p99/max는 참고용이다** — `http_req_duration`은 전체 QPS 계단(50~400)과
전체 API를 한데 묶은 블랑켓 지표라, 고QPS 구간의 일부 실제 타임아웃(클라이언트
60초 제한)이 낮은 QPS 구간의 정상값과 섞여 왜곡된다(8차도 이 표에서 같은
현상 — pure-throughput류 max가 전부 60,000ms 근방인 것도 8차와 동일). **API별/구간별
실제 값은 §2의 서버 실측 표를 봐야 한다.**

`bid-only-load 핫경매집중`의 http_req_failed 0.00%는 8차(6.29%)와 다르게
나온 것 자체가 §5의 구조적 변화(락 경합 소멸)를 그대로 반영한다.

### 8차 대비 요약

| 시나리오 | 8차 http_req_failed | 9차 http_req_failed |
|---|---:|---:|
| pure-throughput 250 | 0.32% | 0.19% |
| pure-throughput 500 | 3.61% | 2.47% |
| pure-throughput 1000 | 9.30% | 12.55% |
| hot-auction-pattern | 0% | 1.32% |
| bid-only-load 분산 | 0% | 0.00% |
| bid-only-load 핫경매집중 | **6.29%** | **0.00%** |

1000-tier가 8차보다 실패율이 약간 높게 나온 건(9.30%→12.55%) RAM/GC 문제가
아니다(§4에서 Full GC 0회 확인) — 8차는 SSE fan-out이 JVM 로컬(`local-sse`)
이었는데 이번은 Redis Pub/Sub 경유라, SSE 브로드캐스트 자체의 부하 성격이
다르다(#534에서 이미 지적한 "입찰 응답 전 동기 PUBLISH" 비용이 SSE 연결
1000개 규모에서 더 크게 반영됐을 가능성 — 별도 확인 필요, 이 문서 범위 밖).

---

## 2. 구간(스테이지)별 + API별 상세 (서버 실측, `histogram_quantile`)

방법은 8차와 동일: `http_server_requests_seconds_bucket`을 구간 끝 시각 기준으로
`histogram_quantile(0.95/0.99, sum(rate(...[구간길이]))  by (le))`로 평가.
스테이지 경계 시각은 각 스크립트의 `mainStartTime`/`STAGE_DURATION` 상수와
실측 `setup()` 소요시간(로그인 배치 처리 시간, 6~37초로 시나리오별 상이)을
합산해 계산했다.

### pure-throughput 250

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| **QPS50** GET bid-context | 3,669 | 66.3 | 89.4 | 99.2 |
| **QPS50** POST bids | 1,223 | 59.7 | 85.1 | 89.0 |
| **QPS50** GET /api/auctions | 1,233 | 162.8 | 221.6 | 243.8 |
| **QPS50** GET /api/auctions/:id | 1,209 | 52.0 | 83.0 | 88.7 |
| **QPS100** GET bid-context | 6,286 | 93.2 | 109.4 | 130.0 |
| **QPS100** POST bids | 2,094 | 80.6 | 93.7 | 105.7 |
| **QPS100** GET /api/auctions | 2,127 | 289.2 | 351.3 | 413.5 |
| **QPS100** GET /api/auctions/:id | 2,057 | 73.1 | 88.3 | 89.4 |
| **QPS150** GET bid-context | 9,877 | 103.5 | 129.3 | 164.8 |
| **QPS150** POST bids | 3,288 | 89.8 | 108.6 | 141.0 |
| **QPS150** GET /api/auctions | 3,351 | 325.2 | 402.6 | 475.3 |
| **QPS150** GET /api/auctions/:id | 3,215 | 81.2 | 95.3 | 121.8 |
| **QPS200** GET bid-context | 13,521 | 127.3 | 175.2 | 200.0 |
| **QPS200** POST bids | 4,494 | 109.4 | 152.3 | 173.7 |
| **QPS200** GET /api/auctions | 4,533 | 395.7 | 499.7 | 541.3 |
| **QPS200** GET /api/auctions/:id | 4,474 | 100.3 | 146.3 | 171.3 |
| **QPS300** GET bid-context | 17,136 | 136.8 | 174.7 | 199.4 |
| **QPS300** POST bids | 5,215 | 106.3 | 148.8 | 172.8 |
| **QPS300** GET /api/auctions | 5,946 | 414.8 | 499.8 | 584.4 |
| **QPS300** GET /api/auctions/:id | 5,599 | 109.9 | 143.6 | 166.0 |
| **QPS400** GET bid-context | 16,653 | 128.8 | 164.5 | 190.9 |
| **QPS400** POST bids | 5,882 | 95.7 | 132.7 | 155.2 |
| **QPS400** GET /api/auctions | 5,328 | 390.9 | 474.8 | 521.5 |
| **QPS400** GET /api/auctions/:id | 5,315 | 102.9 | 133.5 | 155.3 |

`GET /api/auctions`가 QPS와 무관하게 220~500ms대에서 다른 API(90~200ms대)보다
꾸준히 2~4배 높다 — #529가 없앤 건 "항목당 중복 재조회"지 "항목당 1회 조회"
자체가 아니므로 구조적으로 남는 차이다(size=20 기준 여전히 20회 HGETALL).

### pure-throughput 500

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| **QPS50** GET bid-context | 3,659 | 67.7 | 96.2 | 105.7 |
| **QPS50** POST bids | 1,219 | 59.5 | 85.4 | 88.9 |
| **QPS50** GET /api/auctions | 1,198 | 175.7 | 269.7 | 320.4 |
| **QPS50** GET /api/auctions/:id | 1,239 | 50.6 | 84.7 | 88.5 |
| **QPS100** GET bid-context | 6,247 | 94.9 | 110.9 | 134.0 |
| **QPS100** POST bids | 2,081 | 81.5 | 95.7 | 111.7 |
| **QPS100** GET /api/auctions | 2,102 | 292.1 | 353.5 | 409.1 |
| **QPS100** GET /api/auctions/:id | 2,057 | 75.0 | 88.8 | 100.0 |
| **QPS150** GET bid-context | 9,845 | 110.1 | 147.5 | 176.3 |
| **QPS150** POST bids | 3,279 | 95.2 | 127.3 | 152.2 |
| **QPS150** GET /api/auctions | 3,269 | 347.2 | 451.6 | 499.3 |
| **QPS150** GET /api/auctions/:id | 3,272 | 85.9 | 110.2 | 137.8 |
| **QPS200** GET bid-context | 13,457 | 139.4 | 189.0 | 216.8 |
| **QPS200** POST bids | 4,467 | 119.7 | 164.3 | 190.5 |
| **QPS200** GET /api/auctions | 4,448 | 432.7 | 547.6 | 610.9 |
| **QPS200** GET /api/auctions/:id | 4,507 | 109.8 | 152.7 | 176.0 |
| **QPS300** GET bid-context | 16,864 | 138.9 | 177.0 | 200.9 |
| **QPS300** POST bids | 5,117 | 106.9 | 151.5 | 176.0 |
| **QPS300** GET /api/auctions | 5,881 | 420.8 | 507.3 | 575.9 |
| **QPS300** GET /api/auctions/:id | 5,519 | 111.4 | 146.3 | 168.2 |
| **QPS400** GET bid-context | 15,731 | 132.8 | 175.8 | 201.9 |
| **QPS400** POST bids | 5,341 | 104.1 | 161.2 | 224.9 |
| **QPS400** GET /api/auctions | 5,415 | 401.1 | 496.0 | 575.3 |
| **QPS400** GET /api/auctions/:id | 5,353 | 106.3 | 143.8 | 167.3 |

### pure-throughput 1000

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| **QPS50** GET bid-context | 3,634 | 69.9 | 93.3 | 101.5 |
| **QPS50** POST bids | 1,197 | 61.6 | 85.5 | 89.0 |
| **QPS50** GET /api/auctions | 1,144 | 188.3 | 261.1 | 285.0 |
| **QPS50** GET /api/auctions/:id | 1,279 | 51.2 | 77.1 | 88.0 |
| **QPS100** GET bid-context | 6,080 | 95.3 | 111.8 | 131.0 |
| **QPS100** POST bids | 2,026 | 82.5 | 98.8 | 109.9 |
| **QPS100** GET /api/auctions | 2,057 | 295.1 | 353.9 | 389.9 |
| **QPS100** GET /api/auctions/:id | 1,998 | 74.3 | 88.8 | 96.9 |
| **QPS150** GET bid-context | 9,675 | 117.9 | 169.4 | 201.8 |
| **QPS150** POST bids | 3,223 | 102.0 | 147.4 | 178.5 |
| **QPS150** GET /api/auctions | 3,240 | 381.0 | 534.0 | 607.6 |
| **QPS150** GET /api/auctions/:id | 3,201 | 88.4 | 119.4 | 149.3 |
| **QPS200** GET bid-context | 13,127 | 166.5 | 224.6 | 251.1 |
| **QPS200** POST bids | 4,310 | 142.5 | 200.2 | 229.9 |
| **QPS200** GET /api/auctions | 4,345 | 505.7 | 638.5 | 702.4 |
| **QPS200** GET /api/auctions/:id | 4,392 | 133.8 | 187.8 | 215.6 |
| **QPS300** GET bid-context | 17,180 | 131.9 | 178.3 | 203.3 |
| **QPS300** POST bids | 3,960 | 114.7 | 156.5 | 184.1 |
| **QPS300** GET /api/auctions | 5,946 | 436.8 | 526.4 | 604.2 |
| **QPS300** GET /api/auctions/:id | 5,641 | 115.1 | 151.7 | 172.3 |
| **QPS400** GET bid-context | 16,880 | 128.9 | 182.4 | 219.5 |
| **QPS400** POST bids | 3,034 | 121.2 | 178.5 | 214.5 |
| **QPS400** GET /api/auctions | 5,478 | 442.1 | 558.1 | 628.7 |
| **QPS400** GET /api/auctions/:id | 5,465 | 117.2 | 156.9 | 187.1 |

**세 tier 다 같은 패턴**: `GET /api/auctions`만 QPS200 부근에서 500~640ms대까지
튀고 나머지는 계속 200ms 안쪽. QPS300~400에서 오히려 QPS200보다 살짝
내려오는 tier도 있는데(1000-tier bid-context: QPS200 224.6→QPS300 178.3),
이건 8차에서도 관찰된 "고QPS 구간에서 처리 못한 요청이 뒤 구간으로 안
넘어가고 그냥 실패 처리되며 성공한 요청만 남아 p95가 오히려 낮아 보이는"
현상과 같은 계열로 추정된다(§1의 http_req_failed 12.55%와 함께 봐야 함).

### hot-auction-pattern (5분 정속, 1분 단위)

이 시나리오는 `generalReads`가 없어서(§코드 확인, 대화 중 재확인함) API는
bid-context/bids 둘뿐이다 — 아래 표에 `GET /api/auctions`/`:id` 행이 요청수
0으로 나오는 게 정상이다.

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| **0-1min** GET bid-context | 3,976 | 102.4 | 129.5 | 154.1 |
| **0-1min** POST bids | 3,984 | 79.8 | 102.7 | 124.4 |
| **1-2min** GET bid-context | 3,595 | 98.8 | 124.7 | 150.2 |
| **1-2min** POST bids | 3,595 | 78.6 | 98.8 | 111.7 |
| **2-3min** GET bid-context | 3,601 | 97.9 | 138.7 | 156.4 |
| **2-3min** POST bids | 3,596 | 78.1 | 107.6 | 130.2 |
| **3-4min** GET bid-context | 3,605 | 96.1 | 111.0 | 128.9 |
| **3-4min** POST bids | 3,601 | 76.3 | 94.4 | 102.1 |
| **4-5min** GET bid-context | 2,624 | 97.2 | 111.3 | 129.7 |
| **4-5min** POST bids | 2,628 | 78.2 | 97.4 | 104.9 |

**8차 대비 극적으로 개선.** 8차는 이 시나리오에서 bid-context p95가
898~954ms대(핫경매 3개에 몰린 락 경합)였는데, 이번엔 111~139ms대 — 약
7~8배. §5에서 다루는 `BidExecutor` 프로필 차이가 여기서도 그대로 반영된다
(핫경매 3개 집중이라 8차의 DB 락 경합 재현 조건과 거의 같은데도, Redis
Lua 경로라 락 자체가 없다).

### bid-only-load 분산(noSSE)

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| **QPS50** GET bid-context | 3,603 | 64.0 | 89.0 | 98.4 |
| **QPS50** POST bids | 1,200 | 56.2 | 81.6 | 88.0 |
| **QPS50** GET /api/auctions | 1,259 | 161.7 | 220.7 | 239.8 |
| **QPS50** GET /api/auctions/:id | 1,143 | 48.5 | 83.2 | 88.4 |
| **QPS100** GET bid-context | 5,296 | 87.3 | 103.3 | 110.9 |
| **QPS100** POST bids | 1,763 | 75.5 | 88.6 | 95.7 |
| **QPS100** GET /api/auctions | 1,741 | 263.8 | 320.9 | 354.5 |
| **QPS100** GET /api/auctions/:id | 1,795 | 68.1 | 87.8 | 89.3 |
| **QPS150** GET bid-context | 8,896 | 98.3 | 110.6 | 125.9 |
| **QPS150** POST bids | 2,965 | 85.3 | 96.9 | 105.3 |
| **QPS150** GET /api/auctions | 2,965 | 307.1 | 354.5 | 357.5 |
| **QPS150** GET /api/auctions/:id | 2,971 | 78.3 | 88.7 | 97.2 |
| **QPS200** GET bid-context | 12,496 | 105.9 | 129.3 | 148.8 |
| **QPS200** POST bids | 4,163 | 91.2 | 107.8 | 127.6 |
| **QPS200** GET /api/auctions | 4,121 | 334.8 | 410.2 | 444.1 |
| **QPS200** GET /api/auctions/:id | 4,210 | 83.1 | 93.3 | 102.4 |
| **QPS300** GET bid-context | 17,082 | 127.2 | 154.4 | 171.0 |
| **QPS300** POST bids | 5,519 | 104.8 | 132.8 | 149.8 |
| **QPS300** GET /api/auctions | 5,728 | 389.2 | 444.4 | 483.7 |
| **QPS300** GET /api/auctions/:id | 5,659 | 100.9 | 128.7 | 134.0 |
| **QPS400** GET bid-context | 16,937 | 139.4 | 166.4 | 347.0 |
| **QPS400** POST bids | 5,162 | 100.1 | 132.1 | 245.5 |
| **QPS400** GET /api/auctions | 5,863 | 424.3 | 475.3 | 1233.6 |
| **QPS400** GET /api/auctions/:id | 5,488 | 112.7 | 133.1 | 281.9 |

QPS400 구간만 p99가 눈에 띄게 튄다(목록 1,233.6ms) — 이 구간이 62,506건
프로젝션 백로그가 한창 쌓이던 시점(§0-5, §6)과 겹친다. Hikari/Redis 자원
경합이 늘어난 시점과 일치할 가능성이 있으나 이 문서에서 인과관계까지
확정하진 않았다.

### bid-only-load 핫경매집중

| API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---:|---:|---:|---:|
| **QPS50** GET bid-context | 3,601 | 77.7 | 96.1 | 100.0 |
| **QPS50** POST bids | 1,200 | 67.1 | 87.3 | 89.1 |
| **QPS50** GET /api/auctions | 1,258 | 214.9 | 243.9 | 263.0 |
| **QPS50** GET /api/auctions/:id | 1,143 | 61.9 | 86.2 | 89.3 |
| **QPS100** GET bid-context | 5,230 | 87.7 | 101.4 | 111.1 |
| **QPS100** POST bids | 1,742 | 69.3 | 87.6 | 89.3 |
| **QPS100** GET /api/auctions | 1,790 | 263.1 | 298.5 | 352.4 |
| **QPS100** GET /api/auctions/:id | 1,701 | 69.9 | 88.1 | 89.4 |
| **QPS150** GET bid-context | 8,831 | 95.1 | 107.6 | 111.8 |
| **QPS150** POST bids | 2,943 | 71.6 | 88.0 | 95.8 |
| **QPS150** GET /api/auctions | 2,919 | 297.2 | 350.1 | 373.4 |
| **QPS150** GET /api/auctions/:id | 2,967 | 75.4 | 88.5 | 89.4 |
| **QPS200** GET bid-context | 12,430 | 98.8 | 110.7 | 136.5 |
| **QPS200** POST bids | 4,143 | 72.1 | 88.4 | 98.3 |
| **QPS200** GET /api/auctions | 4,165 | 309.4 | 356.4 | 439.0 |
| **QPS200** GET /api/auctions/:id | 4,121 | 78.6 | 88.8 | 98.2 |
| **QPS300** GET bid-context | 17,631 | 109.7 | 134.4 | 161.2 |
| **QPS300** POST bids | 5,866 | 78.0 | 99.9 | 115.0 |
| **QPS300** GET /api/auctions | 5,853 | 340.1 | 433.2 | 484.9 |
| **QPS300** GET /api/auctions/:id | 5,904 | 87.0 | 108.3 | 127.8 |
| **QPS400** GET bid-context | 20,511 | 115.6 | 141.1 | 168.7 |
| **QPS400** POST bids | 6,130 | 79.8 | 99.6 | 128.7 |
| **QPS400** GET /api/auctions | 7,095 | 352.3 | 435.9 | 507.9 |
| **QPS400** GET /api/auctions/:id | 6,701 | 92.3 | 111.1 | 137.3 |

**8차에서 유일하게 안 풀렸던 시나리오가 이번엔 QPS400까지도 가장 건강한
축에 든다** — bid-context p95가 QPS100~400 전 구간 100~141ms대로 유지된다
(8차는 QPS100부터 1,516ms로 점프해서 QPS400까지 쭉 그 상태였음). §5 참고.

---

## 3. 스왑 / HikariCP — 8차 대비

| 시나리오 | pswpin(page/s) | pswpout(page/s) | Hikari active 최댓값 | SwapFree 시작→끝(MB) |
|---|---:|---:|---:|---|
| pure-throughput 250 | 0.4 | 0.0 | **10**/30 | 2,745→2,747 |
| pure-throughput 500 | 0.7 | 2.9 | **8**/30 | 2,747→2,736 |
| pure-throughput 1000 | 37.0 | 66.3 | **9**/30 | 2,705→2,491 |
| hot-auction-pattern | 91.1 | 163.0 | **8**/30 | 2,484→2,227 |
| bid-only-load 분산 | 98.4 | 48.0 | **9**/30 | 2,170→2,092 |
| bid-only-load 핫경매집중 | 11.0 | 32.6 | **9**/30 | 2,092→2,057 |

**Hikari active가 6개 시나리오 전부 10을 넘지 않았다.** 8차는 예외없이
30/30(풀 전체)을 찍었다. §5와 같은 이유 — 입찰 승인 경로가 Redis Lua
단독이라 MySQL 커넥션을 그 경로에서 아예 안 쓴다. 8차가 "RAM 증설로도 못
푼 별도 과제"로 남겨둔 문제가, 이번 조건에서는 애초에 발생하지 않는다.

스왑은 여전히 발생한다(pswpout 최대 163page/s, hot-auction-pattern) —
8차의 hot-auction-pattern(235.7/312.5)보다는 낮지만 pure-throughput류보다는
1~2자리수 높다. RAM 증설이 스왑을 완전히 없애진 못했다는 8차의 결론과
같은 방향.

---

## 4. GC 로그 — Full GC 0회, 8차와 동일

```
grep -c 'Pause Full' /home/ubuntu/logs/gc-1.log   # 세션 시작 직전
0
grep -c 'Pause Full' /home/ubuntu/logs/gc-1.log   # 세션 종료 직후
0
```

세션 전체(76분, 6개 시나리오) 동안 Full GC 0회, 진짜 heap OOM 0회. RAM
1.8GiB+`-Xmx1280m` 조합이 이번에도 안정적이었다.

---

## 5. 근본원인 분석 — `bid-only-load 핫경매집중`이 이번엔 왜 안 터졌나

8차 §5는 이 시나리오의 병목을 "단일 경매 행에 대한 DB 락 경합, RAM과
무관한 구조적 문제, `#398`로 wallet_holds 락은 제거했지만 경매 행 자체의
`FOR UPDATE` 직렬화는 그대로 남아있음"으로 결론지었다. 이번 9차에서 같은
시나리오가 완전히 건강하게 나온 걸 보고 "진짜 안 풀리는 문제였는지"부터
코드로 재확인했다.

`RedisBidExecutor`(`backend/src/main/java/com/dbidding/auction/bid/RedisBidExecutor.java:27`)
는 `@Profile("redis")`. `DbBidExecutor`
(`backend/src/main/java/com/dbidding/auction/bid/DbBidExecutor.java:44`)는
`@Profile("!redis")`이고, 그 안의 `execute()`가 `findByIdForUpdate()`
(`DbBidExecutor.java:153-156`)로 경매 row를 직접 잠근다.

- 8차 프로필: `local-sse,sse-virtual-threads` → `redis` 프로필이 없으니
  `DbBidExecutor` 활성화 → 입찰 승인 매 요청마다 MySQL `FOR UPDATE`로
  경매 row 직렬화 → 핫경매 하나에 몰리면 그 row의 락 대기열이 그대로
  쌓여 p95=52,506ms까지 감.
- 9차 프로필: `redis,sse-virtual-threads` → `redis`가 활성 프로필에
  포함돼 있으니 `RedisBidExecutor` 활성화 → 입찰 승인이
  Redis Lua(`bid-accept.lua`) EVAL 하나로 끝나고, **MySQL은 승인 경로에
  전혀 관여하지 않는다**(projection은 완전히 비동기). 그래서 같은
  핫경매 집중 패턴에서도 DB 락 대기 자체가 발생할 수 없다.

**즉 8차의 결론("RAM과 무관한 구조적 문제")은 그 자체로는 맞지만, "이
프로젝트의 영구적인 한계"는 아니었다** — `DbBidExecutor` 경로에서만
유효한 결론이었고, 실제 운영 배포(`redis` 프로필)에서는 애초에 그 경로를
안 탄다. §3의 Hikari 소진 문제도 같은 원인으로 동시에 해소된다.

이 발견이 "그럼 `DbBidExecutor`는 죽은 코드인가"까지는 이 문서에서 확인
안 했다 — 로컬 개발/`redis` 프로필 미적용 배포 시나리오를 위해 의도적으로
남겨둔 것일 수 있어 별도 확인 필요(범위 밖).

---

## 6. 운영상 발견 — 콜드시드 rewind로 인한 프로젝션 파이프라인 정지 (부하테스트 중 실측)

이 세션 진행 중 `AuctionBidStreamConsumer.hasProjectionError()`
(`backend/src/main/java/com/dbidding/auction/stream/AuctionBidStreamConsumer.java:145-146`)
게이트가 걸려 MySQL 프로젝션이 전역으로 멈춘 상태를 직접 확인했다. [#535](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/535)에서
코드 분석으로만 추정했던 콜드시드 rewind 메커니즘이 실제로 재현된 것으로
보인다.

**확인된 사실 (MySQL 직접 조회):**

- `timeline_events`에 ERROR 1건(`id=2262`, `auction_id=3001274`,
  `auction_version=26`, `failure_message`: "이전 최고 입찰자 정보가 DB
  상태와 일치하지 않습니다.", 발생 시각 2026-08-16 02:10:59 UTC — 이
  9차 세션이 시작하기 전부터 이미 있었던 상태).
- 그 뒤로 밀린 PENDING이 세션 종료 시점 기준 **62,506건**까지 증가.
- 영향받은 `auction_id`는 **정확히 100개, 전부 `seller_id >= 900000`**
  (k6 시드 테스트 경매) — 실고객 경매 0건, `event_type`도 전부
  `bid.accepted.v1`뿐(주문/낙찰 이벤트 안 섞임). `auction_id IS NULL`인
  지갑 단독 이벤트도 0건.
- 문제의 경매(3001274) 기준: Redis `sequence=665`인데 MySQL
  `auctions.last_bid_event_version=25`로 640버전 차이 — Redis 쪽이 훨씬
  앞서 있고 MySQL 프로젝션만 초반에 멈춘 채였다.

**의미:** Redis가 입찰 승인의 authoritative 소스라 이 100개 시드 경매를
포함해 유저(테스트 유저)가 실제로 겪는 입찰 성공/실패 판정에는 영향이
없었다 — k6 결과의 `bid_server_error`/`http_req_failed`가 이 문제로
설명되진 않는다. 다만:

- 이 백로그가 쌓이는 동안 `RedisAuctionStateSeeder.seedIfAbsent()`
  (`backend/src/main/java/com/dbidding/auction/bid/RedisAuctionStateSeeder.java:46`)가
  `projectionCatchUpVerifier.isCaughtUp()==false`인 동안 모든 콜드미스
  경매에 대해 `AuctionException.stateRecoveryRequired()`를 던지게
  되므로, 만약 이 세션 중 이 100개 경매 중 하나라도 Redis에서 실제로
  evict/재기동됐다면 그 경매만 시딩 거부로 완전히 막혔을 것이다(이번
  세션에서 실제로 그런 콜드미스가 있었는지는 확인 안 함).
- §2/§3에서 관찰된 QPS400 구간의 튀는 p99(목록 1,233.6ms 등)가 이
  백로그와 시점이 겹친다 — 인과관계는 확정 못 했다(§7 한계).
- 이 백로그를 MySQL에서 정리하는 작업(62,506건 벌크 UPDATE)은 **이
  부하테스트가 다 끝난 뒤에** 하기로 함 — 진행 중에 하면 (a) DB 자원
  경합으로 이 세션 자체의 측정값을 오염시키고 (b) `EXPLAIN`으로 확인한
  결과 인덱스가 있어도 풀스캔(`type: ALL`)이 걸려 62k row 트랜잭션이
  락을 오래 잡을 수 있어서, 부하테스트 종료를 기다렸다.

관련: [#535](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/535)

---

## 7. DB(MySQL) 원본 데이터

접속: 백엔드 컨테이너 자체 env(`DB_HOST`/`DB_PORT`/`DB_USERNAME`/`DB_PASSWORD`)를
컨테이너 안에서 그대로 사용(`docker exec backend sh -c 'mysql -h "$DB_HOST" ...'`) —
비밀번호 값은 이 세션의 어떤 명령/로그에도 리터럴로 남기지 않았다.

### 7.1 세션 시작 직전 / 종료 직후 스냅샷

| 지표 | 시작(04:31 UTC) | 종료(05:47 UTC) | 델타 |
|---|---:|---:|---:|
| `Threads_connected` | 31 | 31 | 0 |
| `Threads_running` | 2 | 2 | 0 |
| `Max_used_connections` | 33 | 33 | 0 |
| `Innodb_row_lock_current_waits` | 0 | 0 | 0 |
| `Innodb_row_lock_waits`(누적) | 9,883 | 24,569 | **+14,686** |
| `Innodb_row_lock_time_avg`(ms) | 1 | 0 | - |
| `Innodb_row_lock_time_max`(ms) | 708 | 708 | 변화 없음(과거값 유지) |
| `Slow_queries` | 0 | 0 | 0 |

세션 내내 `Innodb_row_lock_current_waits=0`(순간 잡힌 대기는 없었음)이면서도
누적 `Innodb_row_lock_waits`는 14,686건 늘었다 — 순간적으로 짧게 걸리는
락 대기가 계속 있었지만 어느 스냅샷 시점에도 "그 순간 걸려있는" 상태로는
안 잡혔다는 뜻(초 단위보다 훨씬 짧게 풀림). `Threads_connected`가 시작부터
이미 31(=Hikari 30 + 여분 1)이었던 건 §3의 Hikari 최대 10 사용량과 별개로
커넥션 자체는 풀 크기만큼 이미 열려있었다는 뜻(HikariCP는 유휴 커넥션도
`minimumIdle` 설정에 따라 유지).

### 7.2 테이블 크기 (세션 전후 동일 — 이 세션은 순수 읽기/`INSERT INTO
timeline_events`만 발생, 다른 테이블 크기 변화는 반영 지연 또는 미미)

| 테이블 | rows(추정치) | data(MB) | index(MB) |
|---|---:|---:|---:|
| bids | 138,465 | 9.5 | 25.1 |
| users | 49,440 | 10.5 | 4.0 |
| wallets | 49,075 | 2.5 | 1.5 |
| point_records | 48,705 | 5.5 | 9.6 |
| auctions | 26,158 | 6.5 | 12.5 |
| images | 26,109 | 2.5 | 0.4 |
| item_daily_statistics | 24,966 | 2.5 | 2.0 |
| timeline_events | 22,893(추정, 실제 PENDING만 62,506+ — `information_schema` 추정치가 급증 구간에서 크게 stale함) | 15.5 | 4.5 |
| notification | 34,320 | 5.5 | 7.1 |
| card_metadata | 13,941 | 2.5 | 2.8 |
| wallet_holds | 2,852 | 0.4 | 0.4 |

`information_schema.tables`의 `TABLE_ROWS`는 InnoDB 추정치라 실측과 크게
어긋날 수 있다는 걸 이번에 직접 확인했다 — `timeline_events`가 추정치는
22,893인데 실제 `COUNT(*)` 기준 PENDING만 62,506건이었다(§6). **이 프로젝트
운영 스크립트에서 InnoDB 테이블 추정 행수를 그대로 신뢰하면 안 된다.**

---

## 8. Redis 원본 데이터

접속 방식은 §7과 동일(컨테이너 env 경유, 비밀번호 미노출).

### 8.1 세션 시작 직전 / 종료 직후 `INFO` 비교

| 지표 | 시작 | 종료 | 델타/비고 |
|---|---:|---:|---|
| `used_memory_human` | 19.75M | 67.28M | +47.5M(테스트 데이터 누적, 아래 키 개수 참고) |
| `maxmemory_policy` | noeviction | noeviction | 변화 없음 — 상한 없이 무한정 커지는 구성 |
| `connected_clients` | 6 | 6 | 변화 없음 |
| `blocked_clients` | 1 | 1 | 변화 없음(리더락/Stream 관련 blocking client로 추정) |
| `instantaneous_ops_per_sec` | 6 | 4 | 스냅샷 순간값(세션 중 피크는 이 값이 아님) |
| `keyspace_hits` | 61,878,497 | 123,671,540 | +61,793,043 |
| `keyspace_misses` | 1,797,779 | 3,090,917 | +1,293,138 (hit rate 여전히 97% 이상) |
| `evicted_keys` | 0 | 0 | maxmemory 무제한이라 당연히 0 |
| `expired_keys` | 9,238 | 12,287 | +3,049 |
| `total_error_replies` | 121,455 | 197,260 | **+75,805** |
| `DBSIZE` | 41,270 | 142,062 | +100,792 |

**`total_error_replies` +75,805건은 우려스러워 보이지만, 같은 세션에서
`auction:bid:idempotency:*` 키가 23,782→98,451(+74,669)로 거의 같은 폭
늘어난 것과 규모가 맞아떨어진다** — k6가 의도적으로 최소가 미만/중복
입찰을 섞어 400/409 응답을 유도하는 구간(§1의 `bid_policy_rejected`
합계도 세션 전체 49,000건 이상)이 있어서, Redis Lua의 정책 거부 응답이
`redis.error_reply()` 경로로 잡히는 게 원인일 가능성이 높다. 정확한
비율까지는 이 문서에서 확정하지 않았다(범위 밖) — Lua 스크립트가 거부
사유별로 몇 번씩 `error_reply`를 호출하는지 코드 확인이 더 필요하다.

### 8.2 키 패턴별 개수

| 패턴 | 시작 | 종료 |
|---|---:|---:|
| `auction:state:*` | 340 | 340 |
| `auction:bid:idempotency:*` | 23,782 | 98,451 |
| `wallet:balance:*` | 522 | 588 |
| `wallet:hold:*` | 100 | 267 |
| `dbidding:session:sessions:*` | 502 | 1,004 |
| `dbidding:session:index:*` | 251 | 502 |

`auction:state:*`가 340에서 안 늘어난 건 정상 — 이미 존재하는 경매만
읽고 새 경매를 안 만드는 시나리오라서다. `dbidding:session:sessions:*`가
502→1,004로 정확히 2배 된 건 세션당 로그인 유저 수가 시나리오별로 계속
바뀌면서 새 세션이 계속 발급된 결과.

### 8.3 SLOWLOG — 과거 FLUSHALL 이력 발견(이번 세션과 무관)

```
1786828038  54244  "FLUSHALL"  "10.0.0.128:55206"
```

타임스탬프 변환: **2026-08-15 21:07:18 UTC — 이 세션(2026-08-16
04:31~05:47 UTC) 하루 전날**. 이번 부하테스트와는 무관한 과거 기록이고,
SLOWLOG 최근 10건 중 이거 하나뿐이라 이번 세션 중 새로 FLUSHALL이 발생한
적은 없음을 확인했다. 다만 prod Redis에 `FLUSHALL`이 실행된 이력 자체는
있다는 뜻이라 참고로 남긴다 — 누가/언제 어떤 목적으로 실행했는지는 이
문서 범위 밖.

---

## 9. 원본 데이터 파일 위치

- **Prometheus 구간별 원시 집계표(전체 35개 구간 × HTTP 히스토그램 + 전
  메트릭 카테고리):**
  [`raw-data/9-round9-prometheus-raw-data.md`](raw-data/9-round9-prometheus-raw-data.md)
  — 3~8차와 같은 형식(§1~§2가 이 문서에서 뽑아 요약한 것). 8차와 달리 Redis
  exporter 데이터도 실제로 수집됨(8차 시점엔 관련 시계열이 없었음).
- k6 결과(6개): `backend/src/test/k6/result/round9-*-20260816.json`
- k6 실행 로그: `/private/tmp/.../scratchpad/round9-raw/k6-logs/*.log`(세션 로컬,
  영구 보관 필요하면 별도 백업 필요)
- 타임라인(각 시나리오 시작/끝 UTC): `/private/tmp/.../scratchpad/round9-raw/timeline.log`
- DB/Redis 라이트 스냅샷(5분 간격, 15개): `/private/tmp/.../scratchpad/round9-raw/samples.log`
- DB/Redis/GC 딥 스냅샷(세션 전/후): `/private/tmp/.../scratchpad/round9-raw/deep_snapshots.log`
- Prometheus 구간별 API 리포트 스크립트: `/private/tmp/.../scratchpad/full_api_stage_report_round9.py`
  (모니터링 서버에 올려서 원격 실행, 원본 출력:
  `/private/tmp/.../scratchpad/round9_stage_report.md`)
- 스왑/Hikari 리포트 스크립트: `/private/tmp/.../scratchpad/swap_hikari_report.py`(같은 방식)
- 스테이지 경계 계산: `/private/tmp/.../scratchpad/build_timeline.py`,
  결과 `round9_timeline.json`

`/private/tmp/...` 경로는 이 세션(에이전트 실행 환경)의 임시 스크래치
디렉터리라 세션 종료 후 사라질 수 있다 — 재현이 필요하면 이 문서에 적힌
방법론과 스크립트 로직을 그대로 다시 구현하면 된다.

---

## 10. 한계 및 다음 단계

- **8차와 완전한 A/B 아님**(문서 서두 경고 참고) — 프로필(SSE fan-out
  방식/스레드모델/notification 스케줄러)이 다르다. 진짜 A/B가 필요하면
  8차와 정확히 같은 프로필로 한 번 더 재현해야 한다.
- **스테이지 경계 시각은 추정치**(setup() 소요시간을 "관측된 총 실행시간 −
  스크립트 내부 예상 시간"으로 역산) — 8차도 §7.1에서 비슷한 경계 오차로
  인한 아티팩트를 발견하고 정정한 전례가 있다. 이번 문서는 그 정도까지
  세밀하게 경계를 재검증하진 않았다 — 값이 특이하게 튀는 지점(예: QPS400
  구간)이 있으면 경계 오차 아티팩트일 가능성을 감안해서 봐야 한다.
- **§6의 프로젝션 백로그가 이 세션 결과에 실제로 얼마나 영향을 줬는지
  정량화 못 했다** — Hikari 사용량/락 대기 수치가 "백로그가 없었다면"
  더 낮았을 수도, 이미 낮아서 상관없었을 수도 있다.
- **Redis `total_error_replies` 급증의 정확한 내역(정책거부 vs 다른
  원인)을 세부 분류하지 않았다** — Lua 스크립트의 `error_reply` 호출
  지점을 코드로 확인하는 후속 작업이 필요하다.
- **DB 원본 데이터가 이번이 처음이라 세션 전/후 스냅샷만 있고 구간별
  변화는 없다** — §3(스왑/Hikari)처럼 시나리오별로 나눠 찍으려면 다음
  회차부터 라이트 스냅샷 주기를 3~5분보다 더 촘촘히 하거나 시나리오
  경계에 맞춰 찍는 방식으로 바꿔야 한다.
- 이번에도 로컬 대조실험은 하지 않았다(prod에서만 측정).

---

## 부록 A. Tomcat 최대 스레드 50→80 실험 — 역효과 확인

§3에서 pure-throughput 1000-tier 고QPS 구간마다 `tomcat_connector_threads_busy`가
설정값(50)에 그대로 붙는 걸 확인한 뒤, 스레드 수를 늘리면 개선되는지 직접
검증했다. `SERVER_TOMCAT_THREADS_MAX`를 80으로 올려 재배포하고 비슷한 부하
수준(QPS400급, 총 ~295req/s, 목록 ~52req/s)으로 재측정.

**결과 — 오히려 나빠졌다.**

| 지표 | 50스레드(round9 QPS400급) | 80스레드(재테스트, 2026-08-16 17:36~17:50 KST) |
|---|---:|---:|
| `GET /api/auctions` p95 | 289~630ms | **852ms** |
| `GET /api/auctions/:id/bid-context` p95 | 111~224ms | **292ms** |
| CPU 사용률 피크 | ~88% | **~94%** |
| threads_busy 피크 | 50/50(설정 최대) | 80/80(설정 최대) |

**원인:** 이 인스턴스는 vCPU 2개뿐이다. 스레드를 50→80으로 늘리면 더 많은
요청을 동시에 받아들이는데, 이 요청들이 처리될 CPU 자체가 늘지 않아서
동시에 실행되려는 스레드끼리 CPU를 놓고 경쟁하게 된다. 그 결과 컨텍스트
스위칭 비용만 늘고 요청당 실제 처리시간이 늘어난다 — **스레드 수가 아니라
CPU가 병목으로 넘어간 상태에서는 스레드를 더 늘리는 게 역효과를 낸다.**

**시사점:**
- 이 프로젝트(2vCPU) 조건에서 스레드풀 확장은 막다른 길이다. 개선하려면
  vCPU를 늘리거나(§0에서 이미 한 번 한 RAM 증설과 같은 방향의 인스턴스
  업그레이드), 혹은 I/O 대기 자체가 스레드/CPU를 붙잡지 않는 가상스레드
  (`sse-virtual-threads` 프로필, 아직 SSE 외 일반 요청 처리엔 미적용)
  쪽으로 가야 한다.
- 실험 종료 후 `SERVER_TOMCAT_THREADS_MAX`는 50으로 되돌리고 재배포
  완료함(`docker compose up -d backend`, 2026-08-16 09:47:08 UTC 재시작,
  이후 `/api/auctions` 200 확인).
- 측정은 Prometheus `tomcat_connector_threads_busy`/`tomcat_connector_threads_max`
  (`connector="main"`)와 `node_cpu_seconds_total{mode="idle",instance="backend"}`
  를 해당 시간대(epoch 1786869360~1786870200)로 직접 조회해 확인했다 — 별도
  k6 실행 파일은 없음(운영 중 관찰 기반 검증).

> 이 문서는 Claude Code의 도움을 받아 작성하였습니다
