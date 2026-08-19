# 경매 목록 Redis 조회를 정렬 기준별 ZSET으로 분리 (#448)

> **적용 완료.** 아래 설계대로 `auction:active:by-bid-count`/`by-price`/`by-change-rate`/`by-open-time`
> 4개 ZSET이 `auction-create.lua`/`bid-accept.lua`/`auction-close-request.lua`/
> `auction-state-seed.lua`/`auction-active-index-gc.lua`와 `RedisAuctionActiveIndexCleanupScheduler`,
> `RedisAuctionRealtimeStateReader.activeIdsBatch()`에 코드로 반영되어 있다(2026-08-19 코드 기준 확인).

## 목적

`AuctionQueryService.searchRedisActiveAuctions`는 지금 `auction:active:by-close-time`
ZSET(원래 마감 스케줄러용, score는 마감시각 하나뿐)에서 활성 경매 ID를 전부 읽어온 뒤,
정렬 옵션(LATEST/BID_COUNT/PRICE_HIGH/PRICE_LOW/CHANGE_HIGH)에 맞는 Java `Comparator`로
전체를 인메모리 정렬하고, 정렬된 리스트를 선형 탐색해서 커서 위치를 찾는다. 활성 경매 수가
늘어날수록 목록 조회 1회당 비용이 선형으로 커진다.

정렬 기준별로 별도 ZSET을 두고 Lua가 쓰기 시점마다 함께 갱신하면, 조회는
`ZREVRANGEBYSCORE`/`ZRANGEBYSCORE`로 필요한 페이지만 O(log N)에 가져올 수 있다.

조사 중 함께 확인된 두 가지는 논의를 거쳐 범위를 확정했다:
- **psaGrade 필터 누락** (Redis 경로만 이 필터를 적용하지 않음, MySQL 경로엔 있음) → 이번 작업에 포함
- **ENDING_SOON 정렬** (`AuctionSort`에 애초에 없는 옵션, 최근 추가된 ENDING 상태 전이
  스케줄링 기능과는 무관한 별개 개념) → 이번 작업 범위 밖

## 현재 구성

```text
GET /api/auctions (redis profile)
        │
        ▼
AuctionQueryService.searchRedisActiveAuctions
        │
        ├─ activeAuctionIds() ── ZRANGE auction:active:by-close-time 0 -1  (전체 로드)
        │
        ├─ 각 id마다 readAuctionState (Redis HGETALL)
        │
        ├─ 인메모리 필터 (status, keyword) — psaGrade는 누락
        │
        ├─ 인메모리 Comparator 정렬 (5개 정렬 기준)
        │
        └─ 정렬된 리스트 선형 탐색으로 커서 위치 탐색
```

## 설계

### 새 ZSET 4개 (기존 `auction:active:by-close-time`는 스케줄러용으로 그대로 유지)

| ZSET | score |
| --- | --- |
| `auction:active:by-bid-count` | bidCount |
| `auction:active:by-price` | currentPrice (PRICE_HIGH/PRICE_LOW 공용, 조회 방향만 다름) |
| `auction:active:by-change-rate` | changeRateBasisPoints = `(currentPrice-startPrice)*10000/startPrice` |
| `auction:active:by-open-time` | openTime epoch millis (LATEST용, 생성 후 불변) |

### 커서/tie-break: score에 auctionId를 인코딩하지 않는다

IEEE754 double은 53비트 정밀도(~9×10^15)뿐이다. open-time epoch millis는 현재도 이미
약 1.7×10^12(41비트)를 쓰는데, 여기에 auctionId까지 곱셈/덧셈으로 tie-break용으로 욱여넣으면
정밀도가 깨진다. 그래서 **score는 원값 그대로 두고, 커서 이후 페이지는 2단계 쿼리로 가져온다**:

1. **boundary 쿼리** — 커서와 정확히 같은 score에서, 기존 Java `Comparator`의
   `.thenComparing(auctionId, ...)`와 동일한 방향으로 auctionId를 필터링한다. PRICE_LOW는
   유일하게 primary가 오름차순인데 tiebreak는 내림차순인 비대칭 케이스인데, score 인코딩이
   아니라 이 단계의 필터 조건으로 처리하므로 기존 비대칭 동작을 한 줄도 안 바꾸고 그대로
   재현할 수 있다.
2. **beyond 쿼리** — 커서 score를 지난 나머지 (`(score` exclusive bound의
   `ZRANGEBYSCORE`/`ZREVRANGEBYSCORE`).

두 결과를 이어붙여 필요한 만큼만 사용한다.

### 필터(keyword, psaGrade, status)는 여전히 애플리케이션 레벨

ZSET range 쿼리는 텍스트/등급/상태로 필터링할 수 없다. Redis에서 받은 auctionId를
`readAuctionState`로 읽은 뒤 keyword/psaGrade/status는 지금처럼 Java에서 걸러낸다. 필터링
후 개수가 요청한 size보다 적을 수 있으므로, 부족하면 다음 배치를 추가로 가져오는 loop가
필요하다 — 전체 스캔 방지를 위한 합리적인 상한을 두고, 필터가 거의 다 걸러내는 극단적
케이스에도 무한 루프 없이 "이번 페이지엔 이만큼만 있음"으로 정상 종료한다.

## 변경 지점

### Lua (기존 `auction:active:by-close-time` 쓰기 지점 4곳에 새 ZSET 갱신을 나란히 추가)

- `auction-create.lua` — 생성 시 4개 ZSET ZADD (bidCount=0, price=startPrice, changeRate=0,
  openTime=신규 ARGV). `RedisAuctionCreateExecutor`가 `occurredAt.toEpochMilli()`를 새
  trailing ARGV로 추가 전달
- `bid-accept.lua` — 일반 입찰 시 bid-count/price/change-rate ZSET을 갱신된 값으로 ZADD
  (open-time은 불변이라 갱신 불필요); buyNow로 경매가 끝나는 분기에서는 기존 ZREM 옆에
  4개 ZSET도 함께 ZREM
- `auction-close-request.lua` — 경매 마감 시 기존 ZREM 옆에 4개 ZSET도 함께 ZREM
- `auction-state-seed.lua` — MySQL 콜드시드 시 4개 ZSET ZADD. `RedisAuctionStateSeeder`가
  bidCount/currentPrice/changeRateBasisPoints/openTime epoch millis를 새 trailing ARGV로 전달
- `auction-active-index-gc.lua` — KEYS를 여러 개 받도록 확장. KEYS[1](`by-close-time`)에서
  종료 상태(ENDED/CANCELLED/FAILED)로 확인된 orphan auctionId를 찾으면, 같은 실행 안에서
  KEYS[2..5](신규 ZSET 4개)에서도 함께 ZREM. `RedisAuctionActiveIndexCleanupScheduler`가
  새 키 4개를 추가 전달

### Java

- `RedisAuctionRealtimeStateReader` — 정렬 기준별 페이지 조회 메서드 추가 (boundary+beyond
  2단계 쿼리로 auctionId 목록을 반환)
- `AuctionQueryService.searchRedisActiveAuctions`/`redisComparator`/`redisCursorStart` —
  인메모리 정렬·선형 커서 탐색 제거, 페이지 조회 메서드 기반으로 재작성. keyword/psaGrade/
  status 필터와 부족분 추가 조회 loop 포함
- `AuctionCursor`/`AuctionCursorCodec` — 이미 sort별로 value(Long)/timeValue(Instant)를
  구분해 인코딩하므로 구조 변경 불필요

## 검증

- Lua 통합 테스트에 새 ZSET 4개에 대한 어서션 추가 (생성 시 존재/마감 시 제거/입찰 시 갱신)
- `searchRedisActiveAuctions`의 정렬/커서 로직 신규 테스트 (5개 정렬 기준 × 첫 페이지/커서
  이후 페이지/동점 tie-break)
- psaGrade 필터 회귀 테스트
- 활성 경매를 대량으로 만든 뒤 목록 조회 응답시간 비교

관련 이슈: #448
