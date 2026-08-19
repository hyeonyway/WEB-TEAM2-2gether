# 마감임박(ENDING) 진입 시간 기준 전환 + 단발 랜덤 연장 — Redis 프로필 설계 문서

**관련 이슈:** #418 후속 (Redis 프로필). 이슈 번호는 아직 미생성 — 이 문서 승인 뒤 별도로 생성한다.
**스코프:** `redis` 프로필 전용. DB 기반 경로(`!redis`)는 #418/PR #435로 이미 완료·머지됨(참고: [2-ending-status-random-extension-design.md](2-ending-status-random-extension-design.md)).
**전제:** PR #387(`redis-auction-close-scheduler`, closes #386)로 `AuctionDeadlineScheduler`/`AuctionClosingScheduler`가 `AuctionCloseSchedulerProcessor` 전략 인터페이스로 이미 프로필 통합돼 있다. 이 설계는 그 위에 얹는다.

## 1. 배경 및 문제

DB 경로는 이미 "OPEN → 시간 기준 ENDING 전환 → 단발 랜덤 연장 → 마스킹"으로 바뀌었지만, Redis 경로는 손대지 않아 옛날 방식이 그대로 남아있다.

- `bid-accept.lua` 96~101줄에 **입찰 트리거 반복 연장** 로직이 그대로 있다(마감 5분 이내 입찰마다 5분씩 반복 연장, DB 경로에서 없앤 바로 그 버그).
- `RedisAuctionCloseSchedulerProcessor`/`auction-close-request.lua`는 진짜 마감만 처리하고, ENDING 전환 개념 자체가 없다.
- `AuctionQueryService.redisSummary()`/`redisDetail()`은 `state.closeTime()`을 그대로 노출한다 — 마스킹 없음.
- Redis 경매 상태 hash(`auction:state:{id}`)엔 `estimatedCloseTime`에 해당하는 필드 자체가 없다.

## 2. 목표 동작 (DB 경로와 동일한 계약)

1. `ENDING` 진입은 입찰과 무관하게 순수 시간 기준 — OPEN 경매가 마감 5분 전에 도달하면 자동 전환.
2. 전환 시점에 실제 마감시각에 60~120초 사이 랜덤 값을 **딱 한 번만** 더한다. 이후 입찰이 더 들어와도 추가 연장 없음.
3. 고객 노출 마감시각은 전환 시점 값으로 얼려서, 랜덤 연장 반영된 진짜 마감시각과 분리한다.

## 3. Redis 데이터 구조 변경

**신규 hash 필드** (`auction:state:{id}`): `estimatedCloseTime`, `estimatedCloseTimeEpochMillis`.
`auction-create.lua`에서 생성 시 `closeTime`/`closeTimeEpochMillis`와 동일값으로 세팅하고, 이후 ENDING 전환 스크립트가 **절대 건드리지 않는다** — DB 경로의 "얼리기" 트릭을 Redis hash 필드로 그대로 옮긴 것.

**신규 ZSET** `auction:ending-window:by-close-time` — OPEN 경매만 담는다. `score = closeTimeEpochMillis - 300000`(ENDING 진입 시각), `member = auctionId`.

- 생성 시(`auction-create.lua`): ZADD
- 구매확정(buy-now)/마감 시: 기존 `auction:active:by-close-time`처럼 ZREM
- ENDING 전환 시(신규 스크립트): ZREM (전환 끝났으니 더 이상 후보 아님)

기존 `auction:active:by-close-time`(진짜 마감 스케줄, close-processor가 읽음)은 그대로 둔다 — 안전망 역할 불변. 단 ENDING 전환 시 이 ZSET의 score를 **연장된 새 closeTime**으로 덮어쓴다(ZADD 재호출, DB 경로의 "OPEN 타겟 → ENDING 타겟으로 갈아끼움"과 동일한 개념).

**왜 ZSET을 2개 두나(하나로 재활용 안 하는 이유):** `auction:active:by-close-time`은 `RedisAuctionCloseSchedulerProcessor.processDueAuctions()`가 "score ≤ now인 건 무조건 마감 처리"하는 데 쓰인다. 만약 이 ZSET의 score를 OPEN일 땐 ending-window 시각으로 바꿔치기하면, 타이머가 ending-window 시각에 도달했을 때 close-processor가 **아직 OPEN인 경매를 잘못 마감 처리**해버린다. 두 ZSET을 분리하면 이 오탐이 구조적으로 불가능하다.

## 4. Lua 스크립트 변경

### 4.1 `bid-accept.lua` — 반복 연장 블록 제거

96~101줄의 `elseif tonumber(ARGV[5]) >= closeTimeEpochMillis - 300000 then ...` 분기를 삭제한다. `nextCloseTime`/`nextCloseTimeEpochMillis`/`nextStatus`는 buy-now 분기만 남고, 일반 입찰은 `closeTime`/`status`를 그대로 둔다. `closeTimeExtended` 필드는 응답 프로토콜 호환을 위해 남기되 항상 `false`를 반환한다(파서 하위 호환, 값의 의미만 폐기).

### 4.2 `auction-ending-transition.lua` (신규)

**KEYS:** 경매 상태 hash, ending-window ZSET, active-by-close-time ZSET, 타임라인 스트림
**ARGV:** auctionId, extensionSeconds(Java에서 미리 뽑아 전달 — Lua 자체 난수는 재현·감사 불가능해서 안 씀), nowEpochMillis, nowIsoInstant

```lua
local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'OPEN' then return 'NOOP|' .. (status or 'MISSING') end

local closeTimeEpochMillis = tonumber(redis.call('HGET', KEYS[1], 'closeTimeEpochMillis'))
if not closeTimeEpochMillis or closeTimeEpochMillis - 300000 > tonumber(ARGV[3]) then
    return 'NOOP|TOO_EARLY'
end

local extensionMillis = tonumber(ARGV[2]) * 1000
local newCloseTimeEpochMillis = closeTimeEpochMillis + extensionMillis
local newCloseTime = iso8601(newCloseTimeEpochMillis)

redis.call('HSET', KEYS[1], 'status', 'ENDING', 'closeTime', newCloseTime, 'closeTimeEpochMillis', newCloseTimeEpochMillis)
redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('ZADD', KEYS[3], newCloseTimeEpochMillis, ARGV[1])

local streamId = redis.call('XADD', KEYS[4], '*',
    'schemaVersion', '1', 'eventType', 'auction.ending-started.v1',
    'auctionId', ARGV[1], 'closeTime', newCloseTime, 'closeTimeEpochMillis', newCloseTimeEpochMillis,
    'occurredAt', ARGV[4])

return 'TRANSITIONED|' .. streamId .. '|' .. newCloseTime
```

`status ~= 'OPEN'`이면 바로 `NOOP` — 이미 ENDING이거나 마감된 경매를 정밀타이머/백업폴러가 중복 호출해도 안전(idempotent). `closeTimeEpochMillis - 300000 > now` 가드는 시계 오차나 백업 폴러의 조기 호출을 막는 이중 안전장치(DB 경로 `AuctionEndingTransitionService`의 `!closeTime.minus(WINDOW).isAfter(now)` 필터와 동일 목적).

## 5. 스케줄러/인터페이스 통일

기존 `Optional<AuctionEndingTransitionService>`(구체 클래스) 패턴을 `AuctionCloseSchedulerProcessor`와 같은 전략 인터페이스로 교체한다.

```java
public interface AuctionEndingTransitionProcessor {
    List<Integer> transitionDueAuctions(Instant now, int limit);
}
```

- **`DbAuctionEndingTransitionProcessor`** (`@Profile("!redis")`, 기존 `AuctionEndingTransitionService` 리네임): `findDueAuctionIds(OPEN, now+WINDOW, limit)`로 후보 조회 후 각각 기존 전환 로직(`enterEnding` 호출 + 이벤트 발행) 적용, 성공한 ID 리스트 반환.
- **`RedisAuctionEndingTransitionProcessor`** (신규, `@Profile("redis")`): `ZRANGEBYSCORE auction:ending-window:by-close-time 0 now LIMIT 0 limit`로 후보 조회 후 각각 `auction-ending-transition.lua` 실행, `TRANSITIONED` 응답만 성공으로 집계.

`AuctionDeadlineScheduler`/`AuctionClosingScheduler`는 필드를 `Optional<AuctionEndingTransitionService>` → `AuctionEndingTransitionProcessor`(Optional 제거)로 바꾸고:

- `closeDueAuctionsAtDeadline()`: `firedAuctionId != null` 특수 케이스 삭제, close 처리와 동일하게 `auctionEndingTransitionProcessor.transitionDueAuctions(now, BATCH_SIZE)` 무조건 호출.
- `AuctionClosingScheduler.transitionOverdueEndingAuctions()`: 안에 박혀있던 DB 전용 쿼리(`auctionRepository.findDueAuctionIds(...)`)를 통째로 삭제하고 `auctionEndingTransitionProcessor.transitionDueAuctions(now, CLOSE_BATCH_SIZE)` 호출로 대체 — 스케줄러 클래스가 close 처리와 마찬가지로 완전히 프로필-무관해진다.
- `nextTarget()`의 redis 분기: 기존엔 `auction:active:by-close-time` 최솟값 하나만 봤는데, `auction:ending-window:by-close-time` 최솟값도 같이 읽어 더 이른 쪽을 고른다(DB 분기의 openTarget/endingTarget 비교와 동일 모양, ZRANGE 호출 1번 추가).

**`RandomEndingExtensionProvider`**: `@Profile("!redis")` 제거, `@Component`만 남긴다 — 순수 `Duration` 난수 로직이라 프로필과 무관하고, 두 `AuctionEndingTransitionProcessor` 구현체가 그대로 공유한다.

## 6. Stream 이벤트 + MySQL projection

신규 이벤트 타입 `auction.ending-started.v1`을 기존 디스패치 체계에 추가한다.

- `AuctionWalletTimelineEvent.from(...)`에 `if ("auction.ending-started.v1".equals(eventType)) return AuctionEndingStartedStreamEvent.from(...)` 분기 추가, 신규 sealed record `AuctionEndingStartedStreamEvent(auctionId, closeTime, closeTimeEpochMillis, occurredAt, streamId)`.
- `AuctionBidStreamPersistenceService.project(...)`에 분기 추가 → `transitionEnding(AuctionEndingStartedStreamEvent event)`:
  - `auctionRepository.findByIdForUpdate(event.auctionId())` 락
  - `status == OPEN`일 때만 적용(멱등) — Redis가 이미 확정한 `closeTime`을 그대로 반영하는 신규 도메인 메서드 필요(`Auction.enterEnding(Duration)`은 Java 쪽에서 랜덤값을 새로 뽑는 계약이라 재사용 불가 — Redis Lua가 이미 정답을 계산했으므로 "이미 정해진 값을 그대로 적용"하는 별도 메서드가 필요하다. `applyStreamBid()`가 입찰 결과를 그대로 반영하는 것과 같은 패턴):
    ```java
    public boolean applyEndingTransition(Instant newCloseTime) {
        if (status != AuctionStatus.OPEN) return false;
        closeTime = newCloseTime;
        status = AuctionStatus.ENDING;
        return true;
    }
    ```
  - 적용 성공 시 기존 `AuctionStreamPayload.endingStarted(auction, occurredAt)`을 그대로 재사용해 SSE 발행(신규 payload 팩토리 불필요 — DB Auction 엔티티 기준으로 이미 잘 만들어져 있음).

## 7. 읽기 경로 마스킹

- `RedisAuctionRealtimeStateReader.AuctionState`에 `estimatedCloseTime` 필드 추가, `readAuctionState()`에서 `Instant.parse(required(fields, "estimatedCloseTime"))` 파싱 추가.
- `AuctionQueryService`에 DB 쪽과 대칭되는 헬퍼 추가:
  ```java
  private Instant publicCloseTime(RedisAuctionRealtimeStateReader.AuctionState state) {
      return state.status() == OPEN || state.status() == ENDING ? state.estimatedCloseTime() : state.closeTime();
  }
  ```
- `redisSummary()`/`redisDetail()`의 `.endsAt(state.closeTime())` → `.endsAt(publicCloseTime(state))`로 교체.
- `RealtimeState`/`Snapshot`(내부 판정용, `myBidStatus` 계산 등)은 변경 불필요 — 실제 응답 조립은 `AuctionState` 경유이므로 마스킹은 그 지점에서만 하면 충분.

## 8. 관측성

신규 카운터 없음 — 기존 `AuctionMetrics.recordEndingTransition()`이 프로필 무관 공용 카운터이므로 `RedisAuctionEndingTransitionProcessor`도 그대로 호출해서 재사용한다.

## 9. 프론트엔드

변경 없음. DB 경로 작업 때 이미 `status` 값 기준으로 처리하도록 바꿔놨고(`isAuctionEnded`, `AuctionCatalog`), 백엔드가 어느 프로필이든 API 응답 계약(`status`/`endsAt`)이 동일하므로 프론트는 프로필을 몰라도 된다.

## 10. 부하 영향 및 후속 과제

- **입찰 경로**: `bid-accept.lua`에서 반복연장 조건분기+HSET+ZADD가 빠지므로, 마감 임박 구간의 입찰 하나당 오히려 일이 줄어든다.
- **신규 고정비용**: 경매 하나당 딱 1번(ENDING 진입 시) Lua 스크립트 1회 + MySQL projection 1회(동일 컨슈머 큐에 이벤트 1건 추가) — 입찰 QPS와 무관하게 경매 수에만 비례하는 작은 비용.
- **스케줄러 재조회 비용**: `nextTarget()`의 redis 분기가 ZRANGE 호출 1번 더 하는데, 이건 리스케줄될 때만(경매 생성/전환/마감 시점) 도는 거라 입찰마다 도는 게 아니라 무시 가능.
- **미검증 지점(이번 기능과 무관한 기존 갭)**: `redis` 프로필 자체가 DB 프로필처럼 Prometheus 낀 정식 k6 부하테스트를 받은 적이 없다(로컬 임시 테스트로 QPS 200~300대는 버티는 걸 확인한 정도). 이 기능이 추가하는 부하는 위에서 보듯 미미하지만, `AuctionBidStreamConsumer`(전역 단일 스레드 리더락 컨슈머)의 실제 처리량 상한 자체는 이 설계 밖에서 별도로 검증이 필요하다 — **후속 이슈로 분리 제안**.

## 11. 완료 조건

- `redis` 프로필에서 OPEN 경매가 입찰 없이도 ending-window(마감 5분 전) 도달 시 자동으로 `ENDING` 전환된다.
- 전환 시 정확히 1회만 60~120초 랜덤 값이 실제 `closeTime`에 더해지고, 이후 같은 경매에 입찰이 더 들어와도 추가 연장이 없다(`bid-accept.lua` 반복연장 로직 완전 제거 확인).
- `redisSummary`/`redisDetail`/SSE(`AUCTION_ENDING_STARTED` 포함) 어디에도 ENDING 이후의 진짜 `closeTime`(랜덤 연장 반영값)이 노출되지 않는다.
- MySQL projection도 `status`/`closeTime`을 정확히 반영해, redis 장애·재기동 시 DB fallback 조회 결과가 어긋나지 않는다.
- 60초 백업 폴러(`AuctionClosingScheduler`)가 redis 프로필에서도 `RedisAuctionEndingTransitionProcessor` 경유로 정상 동작한다.
- 기존 redis 관련 테스트(bid-accept 계약, close-processor, stream projection)에 회귀가 없다.

## 12. 확정된 결정 사항

- **인터페이스 통일**: `Optional<AuctionEndingTransitionService>` → `AuctionEndingTransitionProcessor` 전략 인터페이스로 교체, `firedAuctionId` 특수 케이스 삭제, `AuctionClosingScheduler`의 DB 전용 쿼리도 processor 내부로 이동 — 확정.
- **`RandomEndingExtensionProvider` 프로필 오픈**: `@Profile("!redis")` 제거, DB/Redis 공용 컴포넌트로 확정.
- **MySQL projection 갱신**: `auction.ending-started.v1` 수신 시 DB Auction row도 갱신 — 확정(단일 스레드 리더락 파이프라인이라 컨텐션 없음, 경매당 1회 추가 비용은 무시 가능하다는 근거로 확정, 10장 참고).
- **랜덤 연장 분포**: DB 경로와 동일하게 60~120초 균등분포, 같은 `RandomEndingExtensionProvider` 재사용.
- **ZSET 2개 유지**(단일 ZSET 재활용 안 함): close-processor가 `auction:active:by-close-time`을 무조건 마감 처리 대상으로 취급하기 때문에 재활용 시 OPEN 경매 오탐 마감 위험 — 3장 참고.
- **정식 k6 부하테스트는 이 기능 스코프에서 제외**, 후속 이슈로 분리(10장).

## 13. 회귀 테스트 범위

### 13.1 Lua 스크립트 계약

- `bid-accept.lua`: 마감 5분 이내 입찰이 더 이상 `closeTime`/`status`를 바꾸지 않는지 검증(반복연장 삭제 확인). buy-now 분기는 기존과 동일하게 유지되는지 확인.
- `auction-ending-transition.lua`(신규): OPEN + window 도달 시 `TRANSITIONED` 응답과 `status=ENDING`/새 `closeTime`/`estimatedCloseTime` 불변을 검증. 이미 `ENDING`이거나 `ENDED`인 경매에 재호출 시 `NOOP` 및 상태 불변(멱등성). window 도달 전 호출 시 `NOOP|TOO_EARLY`.

### 13.2 프로세서/스케줄러

- `RedisAuctionEndingTransitionProcessor` 단위 테스트: ZSET에서 due 후보만 뽑는지, `TRANSITIONED` 아닌 응답은 결과 리스트에서 제외되는지.
- `AuctionDeadlineScheduler`의 redis `nextTarget()` 분기: 두 ZSET 중 더 이른 쪽을 고르는지(DB 경로 11.2절과 동일한 표 형태로 케이스 작성).
- `AuctionClosingScheduler`: redis 프로필에서 `transitionOverdueEndingAuctions()`가 DB 쿼리 없이 processor 위임만으로 동작하는지.

### 13.3 Stream projection + 마스킹

- `AuctionWalletTimelineEvent.from()`/`AuctionBidStreamPersistenceService.project()`: `auction.ending-started.v1` 분기가 올바른 레코드 타입으로 디스패치되고, DB Auction row에 반영되는지(멱등성: 이미 ENDING이면 재적용 안 함).
- `AuctionQueryService.redisSummary`/`redisDetail`: `estimatedCloseTime != closeTime`인 fixture로 `endsAt`이 `estimatedCloseTime`인지 검증. OPEN에서는 두 값이 같아 기존 계약 유지되는 것도 확인.

### 13.4 실행 기준

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.RedisAuctionEndingTransitionProcessorTest' \
  --tests 'com.dbidding.auction.service.AuctionDeadlineSchedulerTest' \
  --tests 'com.dbidding.auction.service.AuctionClosingSchedulerTest' \
  --tests 'com.dbidding.auction.stream.AuctionBidStreamPersistenceServiceTest' \
  --tests 'com.dbidding.auction.service.AuctionQueryServiceTest'
```

Lua 스크립트 자체 테스트(임베디드 Redis 또는 통합 테스트 환경)는 기존 `bid-accept.lua`/`auction-close-request.lua` 테스트가 위치한 동일 스위트에 추가한다.

## 14. 제외 범위

- Redis Cluster 대응(멀티 샤드 hash tag)은 기존 `RedisAuctionRealtimeStateReader` javadoc에 이미 명시된 기존 스코프 밖 사항 — 이번에도 손대지 않는다.
- 정식 k6+Prometheus 부하테스트(10장) — 후속 이슈.
- 프론트엔드 변경 — 9장 참고, 없음.

> 이 문서는 Claude의 도움을 받아 작성하였습니다
