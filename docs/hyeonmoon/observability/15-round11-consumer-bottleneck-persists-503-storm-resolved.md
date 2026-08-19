# 11차 부하테스트 — 프로젝션 컨슈머 병목은 재현, `bid-context` 503 스톰은 해소

**대상 환경:** prod(`api.dbidding.shop`), blue-green 배포(`backend-green`, 8080),
RAM 1.8GiB, vCPU 2개, `-Xmx1000m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError
-Xlog:gc*`, `SPRING_PROFILES_ACTIVE=redis,sse-virtual-threads`,
`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30`,
`SERVER_TOMCAT_THREADS_MAX=50`(`ACCEPT_COUNT=300`, `MAX_CONNECTIONS=4000`,
`THREADS_MIN_SPARE=30`). 컨테이너는 `2026-08-18T15:18:38Z`에 떠서 이 세션
시작(17:50Z)까지 약 2시간 32분 웜업됐다(`docker inspect backend-green`
실측, `RestartCount=0`). **10차 대비 조건 차이 — `-Xmx`가 1280m→1000m로
낮아져 있었다.** 리포지토리 안에는 이 값을 바꾸는 커밋이 없어(호스트
`~/.env`/compose 설정은 이 리포 밖) 의도된 변경인지 실험값이 그대로
남았는지 이 문서에서 확정할 수 없다 — CLAUDE.md가 경고하는 "stale
실험 `-Xmx`"일 가능성을 배제할 수 없으므로, 다음 라운드 전에
`docker exec backend-green env`로 재확인할 것을 권한다(§5 캐비어트).
그 외 RAM/vCPU/Hikari/Tomcat 설정은 10차와 동일.

**작성일:** 2026-08-19.

**배경:** 10차(`14-round10-merged-wallet-notification-sse-validation.md`)는
`GET .../bid-context`가 세션 전체의 39.2%(144,073건)를 503으로 반환한
근본 원인을 `AuctionBidStreamConsumer`(`AuctionBidStreamConsumer.java:56-57`,
`Executors.newSingleThreadExecutor`)의 구조적 단일 스레드 처리량 부족과,
`RedisProjectionCatchUpVerifier.isCaughtUp()`이 **경매 단위가 아니라
시스템 전역 상태 하나**만 보고 콜드미스를 막는 설계로 지목했다(10차
§4.2, §4.8). §4.8은 "전역 단일 게이트를 경매 단위로 좁히는 게 근본
해법 후보"라고 권고했다.

10차 이후 2026-08-18 하루 동안 main에 여러 PR이 머지됐고(전부 KST
기준 08-18, UTC로는 06:06~11:51 사이), 이번 라운드는 그 배포분이 실제로
효과가 있었는지 확인하는 것이 목적이다. 사용자가 제공한 가이드북
(`LOAD_TEST_ROUND11_GUIDEBOOK.md`, 작업 후 삭제 예정)은 그중 4개
(#590/#589/#583/#548)를 "이번 라운드에 영향 줄 만한 것"으로 꼽았는데,
**정작 10차가 직접 권고한 해법과 정확히 일치하는 PR #577
(`fix/573-projection-catchup-hol-blocking`, 06:06 UTC 머지)은 가이드북
목록에서 빠져 있었다** — 이 문서 §2에서 그 코드를 직접 diff로 확인해
빈틈을 메운다.

---

## 0. 결론 먼저 — 가이드북 §1의 두 질문에 대한 답

**질문 1. 오늘 배포분이 실제로 지표에 영향을 줬는가?**

**그렇다 — `GET .../bid-context` 503이 6개 시나리오 전체, 모든 구간에서
0건이었다(§1, §3).** 10차의 39.2%(144,073/367,636)에서 이번엔 정확히
0%로 떨어졌다. 다만 **가이드북이 지목한 4개 PR이 아니라, 가이드북이
놓친 PR #577(및 그걸 다듬은 #583)이 이 결과의 직접적인 메커니즘이고,
PR #590의 "활성 경매 전체 웜업" 확장이 그 메커니즘이 애초에 시험대에
오를 필요조차 없게 만든 1차 요인이다** — 근거는 §2에 코드 diff와
Redis 텔레메트리로 정리했다. 요약하면:

- PR #590(`feat/586-full-warmup-readiness-blue-green`, 11:51 UTC)이
  기동 시 웜업 대상을 "정렬 기준별 상위 500개"에서 "활성(OPEN/ENDING)
  경매 전체"로 확장했다. 이번 세션에 쓴 300개 시드 경매(`3001001`~
  `3001300`)를 포함해 사실상 모든 활성 경매가 배포 직후(15:18Z) 이미
  Redis에 올라가 있었다는 뜻이다.
- `auction:state:*` 키에는 TTL이 없고, `redis_evicted_keys_total`
  델타가 6개 시나리오 전체에서 **정확히 0**이었다(§3 전체구간 표) —
  즉 세션 내내 콜드미스 자체가 사실상 발생하지 않았다. 콜드미스가
  없으면 `RedisAuctionStateSeeder.seedIfAbsent()`의 `hasKey` 조기
  반환에서 끝나고, 뒤에 있는 catch-up 게이트(문제의 그 코드)까지
  아예 도달하지 않는다.
- 그럼에도 게이트 코드 자체는 오늘 실제로 고쳐졌다 — PR #577이
  `RedisProjectionCatchUpVerifier.isCaughtUp()`(전역, 인자 없음)을
  경매 단위 `isCaughtUp(Integer auctionId)`로 바꿨고(→ #583이
  `isCaughtUpForAuction`/`isCaughtUpForAuctionFresh`로 다시 다듬음),
  `RedisAuctionStateSeeder.seedIfAbsent()`가 이 스코프드 버전을 쓰도록
  변경했다. 컨슈머(`AuctionBidStreamConsumer.projectOldestPending()`)도
  ERROR 상태인 경매만 건너뛰도록 바뀌어(`AuctionBidStreamPersistenceService
  .findNextEligiblePending()`), 한 경매의 ERROR가 무관한 다른 경매·지갑
  이벤트 처리까지 막던 head-of-line blocking이 제거됐다. **콜드미스가
  드물게라도 발생하면(키 축출, 프로세스 재시작, 신규 경매 등) 이 코드가
  10차의 "글로벌 게이트가 모든 경매를 같이 막는" 실패 모드를 막아준다
  — 이번 세션에선 콜드미스 자체가 거의 없어서 이 방어선이 실제로 얼마나
  자주 발동됐는지까지는 관측하지 못했다(로그 레벨에서 호출 여부를
  확인하지 않음, §5 한계).**
- 나머지 PR: #548(`fix/518-redis-coldseed-consistency-gaps`, 10:04
  UTC)은 콜드시드 검증 무력화·ID 충돌 덮어쓰기·지갑 프로젝션 정합성
  갭을 고쳐 ERROR 이벤트 발생 자체를 줄이는 방향으로 기여했을 수
  있다(직접 인과 확인은 못 함). #589(`feature/585-virtual-executor
  -admission-control`, 11:10 UTC)는 SSE 가상스레드 캡을 admission
  control로 바꿔 힙 안정성을 높이는 변경으로, bid-context 503과는
  직접 관련이 없다(§4에서 별도로 검증 — Full GC/OOM 0건, 10차와 동일).

가이드북 §2.3이 언급한 "즉시낙찰가 NULL 풀 정합성"은 이번 발견과는
**무관한 별개의 사안**이다 — 그건 `hot-auction-pattern.js`의
`AUCTION_IDS` 검증(정확히 200개 요구)을 위한 사전 조건이지,
`bid-context` 503 메커니즘(`RedisAuctionStateSeeder`↔
`RedisProjectionCatchUpVerifier`)과는 코드상 아무 연결이 없다. 이
가설을 배제한 것도 이번 조사의 성과다.

**질문 2. `AuctionBidStreamConsumer` 단일 스레드 병목이 이번에도
재현되는가?**

**그렇다, 규모도 10차와 비슷하거나 조금 더 크다 — 코드 자체
(`AuctionBidStreamConsumer.java`의 `Executors.newSingleThreadExecutor`)는
오늘 손대지 않았다(가이드북 §0-5의 자체 진단과 일치, §2에서 diff로
재확인).** `redis_stream_group_lag{stream="event:timeline"}`이 6개
시나리오 중 5개에서 실행 구간 내 **8,523~9,905**까지 치솟았고, 각각
정점 이후 **약 9~10.5분**만에 50 미만으로 복귀했다(§3.2). 유일한
예외는 마지막 시나리오(`bid-only-load` 핫경매집중)로, lag가 세션
내내 0에 머물렀다 — 이유는 §4.3에서 분석했듯 단일 경매 집중이 실제
낙찰 성공(따라서 프로젝션 이벤트 발행) 자체를 줄이기 때문이지,
컨슈머가 고쳐졌기 때문이 아니다. **10차처럼 완전히 막혀버리는(poison
-pill) 사례는 없었고, 부하가 끝나면 항상 자체 회복했다** — 이 역시
10차 §4.3의 결론과 일치한다. 코드가 안 바뀐 게 확인된 이상, 이 병목은
지금까지 9~11차 연속 3회 재현됐다 — **이번 문서에서 별도 GitHub
이슈로 등록할 것을 권한다**(§4.5).

---

## 1. 정량 데이터 종합표 (6개 실행, k6 클라이언트 측 `http_req_duration`)

k6 값은 **클라이언트 측 측정**이라 참고용이다 — QPS 계단 전체와 모든
API를 한데 묶은 블랑켓 지표라 고QPS 구간의 지연이 저QPS 구간의 정상값과
섞여 왜곡된다(9~10차와 동일 지적). **서버 실측 p95/p99는 §3을 봐야 한다.**

| 시나리오 | 총 요청(`http_reqs`) | http_req_failed | bid_server_error | med(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|---:|
| pure-throughput 250 | 138,317 | 0% | 0% | 267.66 | 8,905.80 | 9,054.25 | 9,255.85 |
| pure-throughput 500 | 138,664 | 0% | 0% | 378.66 | 8,909.93 | 9,863.05 | 10,017.59 |
| pure-throughput 1000 | 137,975 | 1.88% | 3.82% | 1,257.57 | 7,388.99 | 18,508.26 | 60,004.37 |
| hot-auction-pattern | 36,565 | 0% | 0% | 96.33 | 119.36 | 228.03 | 753.62 |
| bid-only-load 분산 | 140,453 | 0% | 0% | 185.14 | 8,835.12 | 8,971.73 | 9,156.96 |
| bid-only-load 핫경매집중 | 145,810 | 0% | 0% | 120.50 | 4,380.21 | 6,060.91 | 6,464.67 |

**pure-throughput 500 실행의 `http_req_duration.min`이 -2087.07ms로
나왔다** — 물리적으로 불가능한 값이라 실제 지연이 아니라 커스텀
`sse/k6-sse`(xk6-sse 확장) 바이너리의 SSE 요청 타이밍 계측 버그로
본다(§5 한계, 값을 억지로 설명하지 않고 그대로 결함으로 기록).

**pure-throughput 1000만 실패율이 0을 넘었다(1.88%/3.82%)** — 로그에
클라이언트 측 `"Request Failed" error="...request timeout"`이 실제로
찍혀 있고(2026-08-18T18:48:27Z 부근), 같은 구간 Prometheus 상태코드
분포에는 5xx가 전혀 없다(§3.4에서 직접 재확인) — **서버가 5xx를 준 게
아니라 클라이언트가 타임아웃으로 포기한 것**이다. p99(18,508ms)와
max(60,004ms)가 다른 두 tier보다 크게 튄 것도 이 타임아웃과 일치한다.

### 직전 차수(10차) 비교 — **조건 차이: 10차는 `-Xmx1280m`, 이번은
`-Xmx1000m`(위 헤더 참고, 그 외 하드웨어/풀 크기 동일)**

| 시나리오 | 지표 | 10차 | 11차 |
|---|---|---:|---:|
| pure-throughput 250 | 총 요청 | 139,010 | 138,317 |
| | http_req_failed | 0.12% | **0%** |
| | bid_server_error | 0.52% | **0%** |
| | p95/p99(ms) | 8,941 / 9,108 | 8,906 / 9,054 |
| pure-throughput 500 | 총 요청 | 130,664 | 138,664 |
| | http_req_failed | **24.41%** | **0%** |
| | bid_server_error | **50.83%** | **0%** |
| | p95/p99(ms) | 8,501 / 8,740 | 8,910 / 9,863 |
| pure-throughput 1000 | 총 요청 | 128,334 | 137,975 |
| | http_req_failed | **35.75%** | **1.88%** |
| | bid_server_error | **66.48%** | **3.82%** |
| | p95/p99/max(ms) | 6,988 / 10,185 / 37,993 | 7,389 / 18,508 / 60,004 |
| hot-auction-pattern | 총 요청 | 29,749 | 36,565 |
| | http_req_failed | 22.92% | **0%** |
| | bid_server_error | 37.87% | **0%** |
| | p95/p99(ms) | 108 / 150 | 119 / 228 |
| bid-only-load 분산 | 총 요청 | 134,677 | 140,453 |
| | http_req_failed | 24.09% | **0%** |
| | bid_server_error | 47.18% | **0%** |
| | p95/p99(ms) | 4,347 / 5,772 | 8,835 / 8,972 |
| bid-only-load 핫경매집중 | 총 요청 | 136,580 | 145,810 |
| | http_req_failed | 23.11% | **0%** |
| | bid_server_error | 43.84% | **0%** |
| | p95/p99(ms) | 2,339 / 3,104 | 4,380 / 6,061 |
| **`bid-context` 503 합계(세션 전체)** | | **144,073건 / 39.2%** | **0건 / 0%** |

**`http_req_failed`/`bid_server_error`가 6개 시나리오 전부 사실상 0으로
떨어진 것이 이번 회차 최대 변화다** — §0에서 설명한 대로 `bid-context`
503이 이 두 지표의 압도적 비중을 차지했기 때문이다(10차 §1도 동일하게
지적: 실제 `POST /bids` 5xx는 세션 전체 1건뿐이었다). **반대로
med/p95/p99 자체는 개선되지 않았고 일부(pure500/1000, bid-only 분산·
핫경매집중)는 오히려 10차보다 높아졌다** — 이건 퇴보가 아니라 정반대
의미다: 10차는 상당수 요청이 503으로 "빠르게 거부"되어 블랑켓 지연
분포가 낮게 나온 반면(10차 §1 자체 지적), 11차는 그 요청들이 실제로
서버까지 도달해 처리를 기다리므로 지연시간에 그대로 반영된다 —
**"거부돼서 빠른 것"에서 "실제로 처리되느라 오래 걸리는 것"으로
바뀐 것**이라, 실패율과 지연시간을 같이 봐야 이 표가 뜻하는 바를
오독하지 않는다(10차 §1 말미의 지적과 같은 함정).

---

## 2. 근본원인 분석 (1) — `bid-context` 503이 사라진 메커니즘

### 2.1 가이드북이 놓친 PR — `RedisProjectionCatchUpVerifier` 스코프 변경

PR #577(`fix/573-projection-catchup-hol-blocking`, 머지 커밋
`01a6f7e3`, 2026-08-18T06:06:32Z)의 diff를 직접 확인했다:

```diff
--- a/backend/src/main/java/com/dbidding/auction/bid/RedisAuctionStateSeeder.java
-            if (!projectionCatchUpVerifier.isCaughtUp()) throw AuctionException.stateRecoveryRequired();
+            if (!projectionCatchUpVerifier.isCaughtUp(auctionId)) throw AuctionException.stateRecoveryRequired();
```

그리고 `RedisProjectionCatchUpVerifier`에 경매 단위 오버로드가
새로 생겼다:

```java
public boolean isCaughtUp(Integer auctionId) {
    ...
    boolean result = checkCaughtUp(auctionId);   // !existsByAuctionIdAndProjectionStatusIn(auctionId, [PENDING, ERROR])
    ...
}
```

즉 10차 §4.2가 지목한 "전역 상태 하나만 보는" 설계(`existsByProjectionStatus`,
경매 무관)가 "그 경매 자신의 PENDING/ERROR만 확인"으로 바뀌었다 — 10차
§4.8이 권고한 해법과 정확히 같은 방향이다. `AuctionBidStreamConsumer
.projectOldestPending()`도 같은 PR에서 바뀌어, 이제
`AuctionBidStreamPersistenceService.findNextEligiblePending()`이
ERROR로 막힌 `auctionId` 목록(`findAuctionIdsWithError()`)을 제외하고
가장 오래된 PENDING을 고른다 — 한 경매의 ERROR가 무관한 경매/지갑
이벤트까지 막던 head-of-line blocking이 제거됐다.

같은 날 뒤이어 머지된 PR #583(`fix/580-wallet-userid-catchup-scope`,
`a1947e68`, 2026-08-18T09:54:44Z)이 이 메서드를 `isCaughtUpForAuction`/
`isCaughtUpForAuctionFresh`(캐시 우회 재확인 버전)로 다듬고, 지갑
콜드시드에도 동일 패턴(`isCaughtUpForUser`/`isCaughtUpForUserFresh`,
userId 스코프)을 적용했다 — `RedisAuctionStateSeeder.seedIfAbsent()`가
현재 호출하는 게 이 `isCaughtUpForAuctionFresh(auctionId)`다(현재
저장소 워킹트리 파일은 이후(이 문서 작성 시점 기준 최신) 리팩터링으로
`isCaughtUpScoped()` 공통 헬퍼로 한 번 더 추출돼 있는데, 이건 순수
구조 정리이고 판정 로직 자체는 #583 시점과 동일하다 — round11 세션이
실행된 시점의 배포 이미지는 #583까지 반영된 버전이었다, 아래 §2.2에서
타이밍으로 확인).

### 2.2 배포 이미지에 실제로 포함됐는지 시각으로 확인

`backend-green` 컨테이너의 `StartedAt=2026-08-18T15:18:38Z`(`docker
inspect` 실측). 관련 PR 머지 시각(모두 UTC로 환산):

| PR | 내용 | 머지 시각(UTC) |
|---|---|---|
| #577 | catch-up 경매 단위 스코프 + HoL 제거 | 06:06:32 |
| #583 | catch-up 경매/유저 단위 재정리 + fresh 재확인 | 09:54:44 |
| #548 | 콜드시드 검증 무력화·ID충돌·지갑 정합성 갭 수정 | 10:04:56 |
| #589 | SSE 가상스레드 admission control | 11:10:41 |
| #590 | blue-green + 활성경매 전체 웜업 + readiness gate | 11:51:23 |
| **컨테이너 기동** | | **15:18:38** |
| **11차 세션 시작(pure250)** | | **17:50:42** |

5개 PR 전부 컨테이너 기동보다 최소 3시간 27분 앞서 머지됐다 — 이번에
돈 이미지에 전부 포함됐다고 봐도 무방하다.

### 2.3 콜드미스 자체가 거의 없었다 — PR #590이 1차 요인

PR #590(`feat/586-full-warmup-readiness-blue-green`)이
`RedisAuctionStateWarmUp`을 "정렬 기준별 상위 500개씩"에서
"활성(OPEN/ENDING) 경매 전체(안전 상한 5만 건)"로 바꿨다(diff 확인,
`AuctionRepository`의 정렬별 전용 쿼리 4개 삭제). 이번 세션에 쓴 시드
풀(`3001001`~`3001300`, 300개)도 이 전체 웜업 대상에 포함되므로,
기동 시점(15:18:38Z)에 이미 Redis에 올라가 있었을 것이다.

`auction:state:{id}` 키에는 TTL이 없다(코드에 만료 설정 없음 확인).
6개 시나리오 전 구간의 `redis_evicted_keys_total` 델타를 확인한
결과 **전부 정확히 0**이었다(§3.4 표) — 메모리 압박에 의한 축출도
없었다(Redis 메모리 사용량 최대 59MB, 인스턴스 한도 대비 여유가 큼).
**즉 세션 내내 `auction:state:*` 콜드미스가 사실상 발생하지 않았고,
`seedIfAbsent()`의 `hasKey()` 조기 반환에서 대부분 끝났다** — §2.1의
스코프 변경 코드가 실제로 게이트 판정을 내려야 했던 횟수 자체가
거의 없었을 가능성이 높다는 뜻이다.

이건 10차와의 결정적 차이다 — 10차 당시 웜업은 "정렬 기준별 상위
500개"로 제한적이었고(diff의 이전 버전), 300개짜리 테스트 풀이 그
상위권에 전부 들었다는 보장이 없다. 10차 §4.1(500-tier부터 이미
44.6% 503, §4.3의 89.9% 지연 이벤트)과 결합해 보면, **10차의 503
스톰은 "웜업 커버리지가 좁아 실제 콜드미스가 자주 발생했고, 그때마다
전역 게이트가 무관한 경매까지 같이 막았던 것"으로 설명된다** — 이
문서가 10차를 다시 판정한 건 아니지만(범위 밖), 이번 코드 diff와
정확히 들어맞는 설명이다.

**정리: 0% 503이라는 관측 결과는 (a) PR #590의 전체 웜업으로 콜드미스
발생 빈도 자체가 급감한 것과 (b) PR #577/#583의 게이트 스코프 변경으로
콜드미스가 나더라도 블라스트 반경이 그 경매 하나로 좁아진 것, 두 가지가
겹친 결과다. 이번 세션 데이터만으로는 (a)가 지배적 요인이었을
가능성이 높다(콜드미스 자체가 관측되지 않았으므로) — (b)가 실제로
몇 번 발동했는지는 애플리케이션 로그 레벨 확인이 필요한데 이번엔
하지 않았다(§5 한계). 두 요인 모두 오늘 배포분이라는 점은 변하지
않는다.**

---

## 3. 근본원인 분석 (2) — 프로젝션 컨슈머 단일 스레드 병목, 재현 확인

### 3.1 코드 미변경 확인

`AuctionBidStreamConsumer.java`의 워커 정의는 오늘 머지된 어떤 PR
diff에도 나타나지 않는다(§2.1의 diff에서도 `worker` 필드 선언부는
그대로):

```java
private final ExecutorService worker = Executors.newSingleThreadExecutor(
        Thread.ofVirtual().name("auction-timeline-single-", 0).factory()
);
```

가상 스레드라 I/O 대기 자체는 스레드를 붙잡지 않지만, `projectOldestPending()`
호출 자체가 한 번에 이벤트 하나씩 순차 처리하는 구조라 동시성이 여전히
1이다 — 유입 이벤트가 이 처리 속도를 넘으면 큐(=`event:timeline` 그룹
lag)가 쌓이는 구조 자체는 10차와 동일하다.

### 3.2 Redis Stream `event:timeline` 그룹 lag 궤적

`redis_stream_group_lag{stream="event:timeline",group="auction-timeline
-persistence"}`를 각 시나리오 실행 구간(및 그 전후 여유분)에 30초
간격으로 조회했다(전체 원본은 raw-data §각 시나리오 "Redis Stream
lag 궤적" 참고):

| 시나리오 | 실행 구간 내 최고 lag | 정점 시각(UTC) | 50 미만 복귀까지(정점 대비) |
|---|---:|---|---:|
| pure-throughput 250 | 8,523 | 18:03:12 | +9.0분 |
| pure-throughput 500 | 9,191 | 18:26:32 | +9.0분 |
| pure-throughput 1000 | 8,719 | 18:48:41 | +9.0분(다음 시나리오 시작 전 완전 드레인 확인, §3.3) |
| hot-auction-pattern | 8,878 | 19:04:22 | +9.0분 |
| bid-only-load 분산 | 9,905 | 19:25:40 | +10.5분 |
| bid-only-load 핫경매집중 | **0(관측 안 됨)** | — | — (§3.3 별도 설명) |

가이드북이 사전 공유한 "직후 확인 시 6,800~9,800, 드레인 5~13분"과
큰 틀에서 일치한다 — 이번에 스테이지 경계와 정점 시각을 정확히 짚어
재확인해 보니, **핫경매집중을 제외한 5개 시나리오는 정점 규모
(8,500~9,900)와 드레인 소요시간(9~10.5분)이 상당히 일정했다** —
가이드북의 폭넓은 범위(5~13분)보다 좁게 수렴한다. 드레인 속도로 역산하면
초당 약 14~18건 처리 — 가이드북의 "10~26건/s 추정"과 같은 자릿수다.

### 3.3 왜 핫경매집중(마지막 실행)만 lag가 없었나 — 컨슈머가 고쳐진 게 아니다

`bid-only-load` 핫경매집중 실행 구간(19:36:23~19:48:36) 전체에서
`redis_stream_group_lag`이 계속 0이었다(raw-data 원본 궤적 참고).
직전 시나리오(`bid-only-load` 분산)의 lag는 19:36:10에 이미 0으로
드레인돼 있었다(정점 19:25:40 대비 +10.5분) — 즉 "게이트 조건"(다음
시나리오 시작 전 lag 드레인 확인, CLAUDE.md 필수 절차)은 충족된 채
시작됐다.

lag가 안 쌓인 이유는 **이 시나리오의 낙찰 성공률 자체가 크게 낮기
때문**이다. Prometheus `POST /bids` 상태코드 전체구간 집계(§3.4의
방법과 동일):

| 시나리오 | 201(성공, ≈stream에 실제로 쌓이는 이벤트) | 400(정책 거부) |
|---|---:|---:|
| bid-only-load 분산 | 16,725 | 5,523 |
| bid-only-load 핫경매집중 | **5,108** | **18,496** |

한 경매에 모든 VU가 몰리면 "누군가 먼저 갱신한 최소입찰가보다 낮음"류의
정책 거부(400)가 지배적이 되고(9~10차의 `bid_policy_rejected` 패턴과
동일), 실제로 `event:timeline`에 이벤트를 발행하는 낙찰 성공(201) 건수
자체가 분산 시나리오의 약 1/3로 줄어든다. **컨슈머의 처리 속도가
빨라진 게 아니라, 입력 이벤트 자체가 적어서 병목이 드러날 기회가
없었던 것** — 10차 §0가 지적한 "핫경매집중에서 응답이 빠른 건 락
경합이 없어서"와 같은 계열의, 시나리오 설계 자체의 특성이다.

### 3.4 상태코드 분포로 재확인 — 5xx 없음, 503 없음

`sum(increase(http_server_requests_seconds_count{uri=~".*bid-context.*"}
[<시나리오 전체 구간>s])) by (status)`를 각 시나리오 종료 시각에
평가한 결과(raw-data에 쿼리와 원본 그대로 기재):

| 시나리오 | status=200 | status=503 |
|---|---:|---:|
| pure250 | 71,463 | **0** |
| pure500 | 70,285 | **0** |
| pure1000 | 69,210 | **0** |
| hotauction | 18,227 | **0** |
| bidonly | 68,970 | **0** |
| bidonlyhot | 72,091 | **0** |

pure-throughput 1000의 client 실패율 1.88%/타임아웃 관련해서도 별도로
`sum(increase(http_server_requests_seconds_count{job="backend-spring"}
[800s])) by (method,uri,status)`를 그 실행 전체 구간에 대해 확인했다
— 어떤 URI에서도 5xx 상태코드가 잡히지 않았다(raw-data 전체 표
참고). **로그의 클라이언트 측 타임아웃 문구와 정확히 일치 —
서버는 계속 200/201/400/409만 반환했고, 느려서 클라이언트가 먼저
포기한 요청만 실패로 집계됐다.**

### 3.5 권고 — GitHub 이슈 등록

10차(§4.8)가 "별도 이슈로 추적"을 권했고, 이번까지 **9~11차 연속
3회** 같은 패턴(단일 스레드 컨슈머가 지속 고부하에서 유입을 못 따라가
lag가 수천 단위로 쌓임, 부하가 끝나면 자체 회복)이 재현됐다. 코드
자체(`AuctionBidStreamConsumer.java:56-57` 부근)는 이번 회차에도
손대지 않았다는 게 diff로 확인됐으므로, **지금 시점에 GitHub 이슈를
등록해 추적을 권한다** — 후보 해법은 10차 §4.8과 동일(배치 처리,
컨슈머 파티셔닝, 혹은 워커 전용 실행 자원 분리로 SSE fan-out과의
CPU 경쟁 완화).

---

## 4. 구간별·API별 상세 (서버 실측, `histogram_quantile`)

방법: `http_server_requests_seconds_bucket{job="backend-spring"}`를
구간 끝 시각 기준 `histogram_quantile(0.95/0.99, sum(rate(...[구간
길이s])) by (le,method,uri,status))`로 평가. 스테이지 경계는 10차가
실측 로그로 확정한 오프셋 관례를 그대로 적용했다(`pure-throughput`/
`bid-only-load`: 실행 시작 +36s(SSE 있음)/+6s(SSE 없음), 각 구간
120s; `hot-auction-pattern`: +70s, 각 구간 60s) — 이번 회차는 k6 실행
로그 원문이 보존되지 않아 완전한 실측은 아니고 근사치다(§5 한계).
`job="backend-spring"` 라벨은 blue/green 두 타겟이 `instance="backend"`로
동일해 활성 컬러가 자동으로 합산된다(§5 한계의 모니터링 구성 이슈
참고).

**아래는 핵심 API(`bid-context` GET 200, `bids` POST 201)만 추린
축약판이다.** 55개 이상의 URI·상태 조합 전체 원본(요청수·평균·p95·p99,
0건 조합은 표에서 생략하되 생략 건수 명시)은 [raw-data
파일](raw-data/11-round11-prometheus-raw-data.md)에 있다.

### 4.1 `bid-context` 200 성공 응답의 QPS 계단별 p95/p99 (ms)

| 시나리오 | 지표 | QPS50 | QPS100 | QPS150 | QPS200 | QPS300 | QPS400 |
|---|---|---:|---:|---:|---:|---:|---:|
| pure-throughput 250 | p95 | 84 | 95 | 127 | 211 | 240 | 210 |
| | p99 | 97 | 110 | 206 | 261 | 313 | 242 |
| pure-throughput 500 | p95 | 89 | 101 | 126 | 219 | 242 | 207 |
| | p99 | 105 | 125 | 170 | 267 | 274 | 243 |
| pure-throughput 1000 | p95 | 89 | 99 | 179 | 298 | 275 | 231 |
| | p99 | 105 | 128 | 253 | 350 | 339 | 270 |
| bid-only-load 분산 | p95 | 86 | 108 | 109 | 176 | 199 | 198 |
| | p99 | 92 | 134 | 133 | 221 | 241 | 222 |
| bid-only-load 핫경매집중 | p95 | 85 | 93 | 105 | 131 | 163 | 171 |
| | p99 | 89 | 109 | 130 | 175 | 179 | 196 |

`POST /bids` 201(성공) 응답도 같은 패턴을 그대로 따른다(raw-data
참고, 거의 항상 `bid-context` p95/p99와 10ms 이내 차이 — 두 호출이
같은 요청 경로 안에서 순차 실행되기 때문으로 보인다).

**"실제 처리 자체는 QPS가 오를수록 정직하게 느려진다"는 게 이
표의 요지다** — 세 pure-throughput tier가 거의 같은 곡선을 그린다
(QPS200~300 부근에서 p95 200ms대로 꺾이고, QPS400에서 오히려 살짝
낮아지는 것도 3개 tier 공통) — **10차에서 관측했던 "고QPS 구간에서
503으로 빠르게 거부돼 지연시간 곡선이 왜곡되는" 현상이 사라지고,
QPS-지연시간 관계가 정상적인 부하 곡선 형태로 돌아왔다.**

### 4.2 pure-throughput 1000 tier — 클라이언트 vs 서버 지연시간 괴리, Tomcat 스레드풀 포화

§1에서 pure-throughput 1000의 k6 클라이언트 p95가 7,389ms인데 위 표의
서버 실측 QPS400 p95는 231ms다 — **약 32배 차이.** 이 괴리는 10차에서도
지적했던 것과 같은 계열의 현상으로, `http_server_requests_seconds_bucket`이
Spring MVC 디스패치가 시작된 시점부터만 재는 반면, 클라이언트는 TCP
연결·Tomcat accept queue 대기 시간까지 전부 포함해서 잰다. 이번에
`tomcat_connector_threads_busy{connector="main"}`의 **구간 최댓값이
pure250/500/1000/bid-only 분산/핫경매집중 5개 시나리오 전부에서
정확히 50** — `SERVER_TOMCAT_THREADS_MAX=50` 설정값과 일치한다(hot
-auction-pattern만 최댓값 20, 그만큼 부하가 낮았다는 뜻과 일치, §5.1
표). **Tomcat 메인 커넥터 스레드 풀이 캡에 도달해 요청이 accept
단계에서 대기하는 시간이 서버 히스토그램에 잡히지 않고 그대로
클라이언트 체감 지연에 쌓이는 것으로 보인다** — 확정적 인과관계까지는
아니지만(대기열 자체를 직접 재는 지표는 수집하지 않음, §5 한계),
풀 포화 시점과 지연 규모가 정합적이다.

### 4.3 hot-auction-pattern — 분당 breakdown

| 구간 | GET bid-context [200] p95/p99(ms) | POST bids [201] p95/p99(ms) |
|---|---|---|
| 0-1min | 101 / 141 | 100 / 134 |
| 1-2min | 104 / 120 | 104 / 114 |
| 2-3min | 106 / 127 | 106 / 126 |
| 3-4min | 100 / 113 | 101 / 112 |
| 4-5min | 100 / 111 | 101 / 114 |

hot-auction-pattern은 5분 동안 고정 부하(핫경매 3개×14/s + 콜드
18/s)라 계단식 증가가 없다 — 전체구간 p95 119ms/p99 228ms(§1)로 6개
시나리오 중 가장 안정적이었고, 10차와 마찬가지로 "핫경매 집중이어도
`RedisBidExecutor`가 MySQL 락을 안 타서 락 경합이 없다"는 결론이
이번에도 유효하다(Hikari active 최댓값 4/30, §5.1). 원문 전체(분당
세부 표)는 raw-data에 있다.

---

## 5. HikariCP / GC / Tomcat / SSE / Redis / MySQL / 노드 — 전체구간 요약

전체 원본은 raw-data(시나리오별 "전체 구간 요약" 섹션)에 있다. 핵심만
요약:

| 시나리오 | Hikari active avg/max | GC pause count 합 | node_load1 avg(backend) | Tomcat busy max(main) | SSE conn avg(auction/me) | Redis mem max(MB) | MySQL row-lock-wait delta |
|---|---|---:|---:|---:|---|---:|---:|
| pure-throughput 250 | 4.13 / 10 | 447 | 4.03 | 50 | 247 / 246 | 21.8 | 1,482 |
| pure-throughput 500 | 4.38 / 13 | 302 | 4.11 | 50 | 489 / 488 | 31.7 | 862 |
| pure-throughput 1000 | 4.73 / 13 | 334 | 4.84 | 50 | 979 / 978 | 41.6 | 811 |
| hot-auction-pattern | 1.55 / 4 | 74 | 2.81 | 20 | 461 / 460 | 50.2 | 970 |
| bid-only-load 분산 | 4.29 / 12 | 448 | 3.95 | 50 | SSE 없음 | 59.0 | 3,510 |
| bid-only-load 핫경매집중 | 3.63 / 12 | 413 | 2.60 | 50 | SSE 없음 | 58.8 | 1,499 |

- **Hikari active가 6개 시나리오 전부 13을 넘지 않았다** — 풀 크기
  30 대비 여유가 크다. 10차(최대 14)와 같은 수준 — `RedisBidExecutor`가
  MySQL 커넥션을 거의 안 쓰는 구조가 이번에도 유효하다. `hikaricp_
  connections_pending`/`_timeout_total`은 6개 시나리오 전부 0(raw
  -data 참고) — 풀 대기·타임아웃 없음.
- **Full GC 0회, heap dump 0개, 컨테이너 재시작 0회** — 백엔드 호스트
  `gc-1.log`(및 회전된 3개 파일) 전체에서 `grep -c 'Pause Full'` 결과
  전부 0, `docker logs`에 OOM/heap dump 로그 없음, `RestartCount=0`.
  `-Xmx`가 1000m로 10차(1280m)보다 낮아졌는데도 이번 세션 전체에서
  안정적이었다 — 힙 committed 최댓값은 시나리오별로 raw-data에
  기록했다(pure1000 기준 약 602MB, 10차 968MB보다 낮다 — 503으로
  억눌리지 않고 실제로 더 많이 처리했다는 점을 고려하면 다소 의외지만,
  `-Xmx` 하향 자체가 GC를 더 자주 도는 방향으로 유도해 각 세대의
  피크 점유량을 낮췄을 가능성이 있다, 확정 아님).
- **MySQL row-lock-wait delta가 전체구간 기준 810~3,510건으로,
  10차의 스테이지(120s)별 값(0~900대, 10차 §5 raw-data)과 견줘보면
  같은 자릿수다** — 요청량(시나리오당 13~14만 건) 대비 낮은 비율이라
  9~10차의 "락 경합 없음"(`RedisBidExecutor`가 MySQL 락을 안 탐)
  결론과 배치되지 않는다. `performance_schema.data_lock_waits` 직접
  폴링은 이번에도 하지 않았다(10차와 같은 권한 문제 가능성, §6 한계).
- **SSE**: `dbidding_sse_connections{stream="auction"}`과
  `{stream="me"}`가 이번에도 거의 항상 같은 값으로 유지됐다(예:
  pure1000 tier 979/978, 소수점 차이는 램프업/다운 구간 포함 평균이라
  발생) — 10차가 검증한 `/api/me/stream` 병합 커넥션 안정성이 이번
  세션에도 깨지지 않았다. `sse_broadcast_saturated_total`/
  `dbidding_me_sse_send_failures_total` 델타는 6개 시나리오 전체에서
  0(raw-data).
- **Redis exporter**: `redis_up=1` 전 구간, `redis_evicted_keys_total`
  델타 전부 0(§2.3), 메모리 사용량 최대 59MB로 여유 큼.

---

## 6. 한계 및 캐비어트

- **스테이지 경계가 이번 회차 실측이 아니라 10차 실측값의 재적용이다.**
  이번 세션의 k6 실행 로그 원문("메인 구간이 실제로 값을 찍기 시작한
  시각")이 보존되지 않았고, 요약된 시작/종료 UTC 시각과 k6 결과 JSON만
  전달받았다. 스크립트(`pure-throughput.js`/`bid-only-load.js`/
  `hot-auction-pattern.js`)가 10차 이후 변경되지 않았음을 `git log`로
  확인하고 10차가 검증한 오프셋(+36s/+6s/+70s)을 그대로 적용했지만,
  완전한 실측은 아니다 — 오차는 수 초 이내로 추정되며, 표의 상대적
  경향(계단별 증가 패턴, 503 유무)에는 영향이 없을 것으로 본다.
- **PR #577/#583의 경매 단위 스코프 게이트가 이번 세션 중 실제로
  몇 번 호출·평가됐는지는 확인하지 못했다** — 콜드미스가 관측되지
  않았다는 것(Redis eviction 0)과 정합적이지만, 애플리케이션 로그에서
  `seedIfAbsent`/`isCaughtUpForAuctionFresh` 호출 자체를 직접 세지는
  않았다. §0/§2의 "PR #590이 1차 요인, #577/#583은 방어선" 결론은
  이 부재 증거(콜드미스 없음)에 기반한 추론이지, 로그로 직접 확인한
  사실은 아니다.
- **`http_req_duration.min=-2087.07ms`(pure-throughput 500)는 원인을
  더 파지 않았다** — `sse/k6-sse`(xk6-sse) 바이너리의 계측 버그로
  본다는 것 이상은 이 문서 범위 밖이다.
- **`performance_schema.data_lock_waits` 직접 폴링은 이번에도 하지
  않았다** — 10차가 겪은 `dbidding` 유저 권한 문제(`ERROR 1142`)가
  이번에도 있는지 재확인하지 않고, mysql-exporter의
  `mysql_global_status_innodb_row_lock_waits`로 대체했다(10차와 동일한
  타협).
- **`job="backend-spring"` 라벨이 blue/green 두 타겟을 구분하지
  않는다** — `/etc/prometheus/prometheus.yml`에 9091(blue)/9092(green)
  둘 다 `instance="backend"`로 잡혀 있어(가이드북 §0-3/0-4가 언급한
  구성 그대로), `up{job="backend-spring"}` 같은 단순 쿼리로는 어느
  컬러가 응답 중인지 구분이 안 된다. 이번 세션 내내 9092(green)만
  `up=1`이었고 9091(blue)은 `down`이라(`/api/v1/targets` 확인) 이
  문서의 모든 `job="backend-spring"` 쿼리는 실질적으로 green 하나만의
  값이지만, 쿼리 문법만으로는 이걸 보장할 수 없다 — 모니터링 구성
  품질 이슈로, 이번 라운드의 실제 측정 결과에 영향을 준 건 아니다.
- **node_load1/CPU를 10차 §4.7처럼 QPS50/QPS100 단일 스테이지로
  좁혀 정밀 대조하지 못했다** — 이번엔 시나리오 전체 평균만 수집했다
  (§5 표). `-Xmx` 조건이 달라진 데다(위 헤더), 스테이지 단위 대조까지
  하면 예산상 시간이 부족해 전체 평균으로 갈음했다 — 필요하면 raw
  -data의 원본 쿼리를 스테이지별로 재실행하면 된다.
- **로컬 대조실험은 이번에도 하지 않았다**(prod에서만 측정, 9~10차와
  동일).
- **`-Xmx1000m`(10차 1280m)로 바뀐 이유를 이 문서에서 확정하지
  못했다** — 저장소 안에 이 값을 바꾸는 커밋이 없다(호스트 `.env`/
  compose 설정은 리포 밖). 의도된 변경인지 실험값이 남은 것인지
  다음 라운드 전에 확인 권고(§0 헤더).
- k6 실행 중 재시도 로그: `pure-throughput SSE_VUS=500`는 실행 전
2번의 시도가 에이전트 스크립팅 버그(작업 디렉터리 경로 이중 접두,
`backend/src/test/k6/backend/src/test/k6/sse/k6-sse`)로 exit 127
실패했다 — 인프라·애플리케이션 문제가 아니라 명령어 조합 실수였고,
세 번째 시도에서 정상 실행됐다(위 §1 표는 그 세 번째 실행 기준).

---

## 7. 원본 데이터 파일 위치

- **Prometheus 구간별/전체구간 원시 집계표(6개 시나리오 전체):**
  [`raw-data/11-round11-prometheus-raw-data.md`](raw-data/11-round11-prometheus-raw-data.md)
- k6 결과(6개): `backend/src/test/k6/result/round11-*-20260819.json`
- 관련 PR diff 재현: `git show 01a6f7e3`(#577), `git show a1947e68`(#583),
  `git show 1b6aa734`(#548), `git show c24aeaa7`(#589), `git show
  1b53b83c`(#590) — 전부 이 리포지토리의 main 히스토리에 있다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
