# 경매 목록 조회(GET /api/auctions) Redis 중복조회 제거 설계

**관련 이슈:** #529 (필터 없는 목록 조회의 항목별 Redis 중복조회 제거).
**범위 밖:** #530 — keyword/psaGrade 필터가 실제로 걸린 검색의 구조적 확장성 문제는
이 문서·이 작업 범위에 포함하지 않는다.

## 1. 증상

9차 부하테스트(SSE 250/500 tier, QPS 계단)에서 서버측 히스토그램 p95를 endpoint별로
직접 비교한 결과:

| endpoint | p95(ms) |
|---|---:|
| `GET /api/auctions`(목록) | **~1,100~1,400** |
| `GET /api/auctions/:id`(상세) | 66~134 |
| `GET /api/auctions/:id/bid-context` | 86~198 |

목록만 상세·bid-context보다 10배 이상 느리다. 셋 다 같은 Redis 프로필, 같은 부하
조건에서 나온 수치라 인스턴스/네트워크 문제가 아니라 목록 endpoint 자체의 처리
비용 문제다.

## 2. 원인 — 두 겹의 중복

### 2.1 `AuctionQueryService.redisSummary()`가 항목마다 bid-context급 풀조회를 재사용

```java
private AuctionResponses.AuctionSummary redisSummary(RedisAuctionRealtimeStateReader.AuctionState state, Integer userId) {
    CardSnapshot card = redisCardSnapshot(state);                          // Redis 호출 없음(state에서 그대로 뽑음)
    RealtimeState realtime = realtimeStateReader.read(state.auctionId(), userId);  // ← 문제
    return AuctionResponses.AuctionSummary.builder()
            ...
            .myBidStatus(realtime == null ? MyBidStatus.NONE : realtime.myBidStatus())
            .myBidAmount(realtime == null ? null : realtime.myBidAmount()).build();
}
```

`realtimeStateReader.read()`는 원래 경매 1개짜리 bid-context 응답을 만들기 위해
설계된 메서드다:

```java
public RealtimeState read(Integer auctionId, Integer userId) {
    Snapshot snapshot = readSnapshot(auctionId);                    // HGETALL
    List<BidSummary> recentBids = recentBids(auctionId, snapshot.highestBidderId());  // XREVRANGE
    Map<Object, Object> myBid = ... opsForHash().entries(bidderKey(auctionId, userId)); // HGETALL
    return new RealtimeState(snapshot.status(), ..., myBidStatus, myBidAmount, recentBids);
}
```

`redisSummary()`가 실제로 쓰는 건 `realtime.myBidStatus()`/`realtime.myBidAmount()`
둘뿐이다. 근데 그 둘을 얻으려고:

- `readSnapshot()` — `state`(이미 배치 조회로 들고 있는 값)에 이미 있는 필드를
  **중복 재조회**한다(status/currentPrice/bidIncrement/bidCount/closeTime/buyNowPrice
  전부 `state`에 이미 있음).
- `recentBids()` — XREVRANGE로 최근 입찰 5건을 스트림에서 읽어오는데, **그
  결과(`RealtimeState.recentBids()`)를 `redisSummary()`가 아예 참조하지 않는다.**
  완전히 버려지는 계산이다.

목록 항목 하나당 Redis 왕복 3번 중 2번(스냅샷 재조회, recentBids)이 낭비다.

### 2.2 `fetchRedisSortedPage()`가 필터 유무와 무관하게 고정 배치 크기만큼 HGETALL

```java
for (int batch = 0; collected.size() < limit && !exhausted && batch < SORT_ZSET_MAX_BATCHES; batch++) {
    List<TypedTuple<String>> raw = realtimeStateReader.activeIdsBatch(zsetKey, descending, bound, withinBoundOffset, SORT_ZSET_FETCH_BATCH_SIZE);
    ...
    List<AuctionState> filtered = raw.stream()
            .map(tuple -> realtimeStateReader.readAuctionState(Integer.valueOf(tuple.getValue())))  // 배치 전체를 HGETALL
            .filter(Objects::nonNull)
            .filter(state -> request.status() == null || state.status() == request.status())
            .filter(state -> request.keywordOrDefault().isBlank() || ...)
            .filter(state -> request.psaGrade() == null || ...)
            .sorted(redisComparator(sort))
            .filter(state -> cursorForFilter == null || isAfterCursor(state, cursorForFilter, sort))
            .toList();
    ...
}
```

Redis ZSET은 score로 정렬된 순서만 제공하지, keyword 부분일치나 psaGrade 등가비교를
자체적으로 못 한다 — 그래서 후보를 먼저 `SORT_ZSET_FETCH_BATCH_SIZE`(50)개만큼
가져와 전부 `readAuctionState()`(HGETALL)로 실제 필드를 읽은 뒤 애플리케이션
코드(Java)에서 필터링한다. **keyword/psaGrade 필터가 둘 다 비어있으면 아무것도
걸러지지 않는데도(ZSET 자체가 이미 정렬돼 있으므로) 항상 고정 배치(50)를 다
읽는다.**

### 2.3 합산

`size=20`, 필터 없음 기준 요청 1번당:

- 배치 과다조회: 50 HGETALL
- 항목별 중복(2.1): 20 × 3 = 60 (그중 40은 낭비)

**총 ~110회 Redis 왕복, 그중 최소 90회(배치 30개 초과분 + 항목별 낭비 40)가 불필요.**

## 3. 수정

### 3.1 목록 전용 경량 조회로 분리

`redisSummary()`가 필요로 하는 건 "이 유저가 이 경매에 낸 입찰 상태/금액" 하나뿐이다.
`read()`를 재사용하지 않고, `bidderKey` HGETALL 하나만 하는 메서드를 새로 추가한다:

```java
// RedisAuctionRealtimeStateReader
public MyBidSummary readMyBidSummary(Integer auctionId, Integer userId) {
    if (userId == null) return MyBidSummary.NONE;
    Map<Object, Object> myBid = redisTemplate.opsForHash().entries(bidderKey(auctionId, userId));
    if (myBid.isEmpty()) return MyBidSummary.NONE;
    MyBidStatus status = MyBidStatus.valueOf(required(myBid, "status"));
    Long amount = RedisIntegerValue.parseLongExact(required(myBid, "amount"));
    return new MyBidSummary(status, amount);
}
```

`redisSummary()`는 이 메서드로 교체한다. `read()`(스냅샷+recentBids 포함)는
bid-context/상세 조회에서만 그대로 쓴다 — 그쪽은 recentBids를 실제로 응답에
쓰므로 낭비가 아니다.

### 3.2 필터 없을 때 배치 크기를 limit로 축소

```java
int fetchBatchSize = hasNoFilter(request) ? limit : SORT_ZSET_FETCH_BATCH_SIZE;
```

`hasNoFilter`는 keyword/psaGrade가 둘 다 비어있는지만 확인한다(status 필터는
활성 경매 조회 자체가 이미 OPEN/ENDING 상태 집합을 대상으로 하므로 별도 취급).
필터가 하나라도 있으면 지금과 동일하게 여유분(50) + 재시도(`SORT_ZSET_MAX_BATCHES`)
로직을 그대로 유지한다 — #530에서 다룰 구조적 문제라 이번엔 손대지 않는다.

## 4. 기대 효과

`size=20`, 필터 없음 기준:

- 배치 과다조회: 50 → 20 HGETALL
- 항목별 조회: 20×3 → 20×1 HGETALL

**~110회 → ~40회.** 대략 3배 가까운 Redis 왕복 감소를 기대한다(실측은 배포 후
동일 조건 부하테스트로 검증).

## 5. 검증 계획

- 단위 테스트: `redisSummary()`가 더 이상 `recentBids()`/스냅샷 중복 HGETALL을
  호출하지 않는지 mock 호출 횟수로 확인.
- `fetchRedisSortedPage()`: 필터 없을 때 배치 크기가 `limit`으로 줄어드는지,
  필터 있을 때는 기존 동작(50 + 재시도)이 그대로 유지되는지 각각 확인.
- 배포 후 동일 조건(SSE 250, QPS 50~400 계단) 부하테스트로 `GET /api/auctions`
  p95가 상세/bid-context 수준(수백 ms대)으로 내려오는지 실측 비교.

## 6. 한계

- 필터(keyword/psaGrade) 검색 요청은 이 수정으로 개선되지 않는다 — #530에서
  별도로 다룬다.
- `readAuctionState()`를 완전히 없애는 게 아니라 "필터 없을 때 호출 횟수를
  줄이는" 수준이다 — 필터 있는 검색에서는 여전히 배치당 최대 50회 호출된다.
