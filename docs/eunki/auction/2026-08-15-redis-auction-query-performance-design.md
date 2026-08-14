# Redis 경매 조회 성능 개선 설계

## 배경

Redis 프로필 부하 테스트는 경매 목록, 입찰 내역, 입찰 참여를 4:4:2 비율로 호출한다. 50 QPS부터 400 QPS까지 증가할 때 Redis connection 사용량과 Tomcat busy thread가 주기적으로 상승하고, 약 4~5초 간격으로 Redis cache hit ratio가 크게 떨어진다.

현재 로그인 사용자의 기본 경매 목록(`size=20`)은 필터 후보 50개가 첫 배치에서 충분하더라도 다음 조회를 수행한다.

1. 정렬 ZSET 조회 1회
2. 후보 50개 `auction:state:{id}` 개별 조회 50회
3. 최종 20개마다 `read()`를 호출하면서 같은 state를 2회, recent-bids를 1회, bidder state를 1회 조회

따라서 한 요청이 최소 131개 Redis 명령과 순차 네트워크 왕복을 만든다. 목록 응답에는 recent-bids가 필요하지 않는데도 읽고, 이미 필터링할 때 읽은 state를 다시 읽는다. 또한 사용자가 참여하지 않은 경매도 `auction:bidder:{auctionId}:{userId}`를 조회하므로 정상적인 "미참여"가 keyspace miss로 누적된다.

입찰 내역 조회도 state 존재 확인 뒤 `read()`가 같은 state를 두 번 더 읽어 state hash를 총 3회 조회한다. 입찰 컨텍스트의 hit 경로도 seed 필요 여부 확인과 `read()` 내부 중복으로 state를 총 3회 조회한다.

## 목표

- 목록 후보 state 조회를 한 번의 pipeline 왕복으로 제한한다.
- 목록 응답 조립에서 이미 읽은 state를 재사용하고 recent-bids를 조회하지 않는다.
- 사용자 참여 집합을 한 번에 확인하고 실제 참여 경매의 bidder hash만 조회한다.
- 입찰 내역과 입찰 컨텍스트에서 동일 state hash 중복 조회를 제거한다.
- 실제 key miss와 Redis command 오류/timeout을 같은 fallback 신호로 취급하지 않는다.
- Redis/DB 프로필 API 응답 계약과 정렬·커서·필터 정합성을 유지한다.

## 범위

### 포함

- `RedisAuctionRealtimeStateReader`의 state batch pipeline 조회
- 목록 전용 사용자 입찰 상태 batch 조회
- 단건 state fields를 한 번만 파싱하는 reader 내부 구조
- `AuctionQueryService` 목록, 입찰 내역, 입찰 컨텍스트 read path 개선
- Redis 명령 패턴, key miss, 응답 정합성 회귀 테스트
- 기존 50→400 QPS, 4:4:2 시나리오 재검증

### 제외

- 카드 snapshot 전체 선적재 정책 변경
- Redis key schema와 입찰 Lua write path 재설계
- Redis pool 크기 상향으로 병목을 가리는 설정 변경
- 경매 이외 도메인의 조회 최적화

## 대안 비교

### Lua 다중 키 조회

클라이언트 명령 수는 가장 크게 줄지만, 큰 후보군 파싱 동안 Redis 단일 실행 스레드를 점유한다. 현재 키에는 Redis Cluster hash tag도 없어 Cluster 전환 시 제약이 커진다. 채택하지 않는다.

### 모든 경매 state를 단일 HASH에 저장

`HMGET` 한 번으로 조회할 수 있지만 입찰 승인·종료 Lua와 시딩/정리 로직의 key schema를 모두 바꿔야 한다. #503의 read path 개선 범위를 넘어선다. 채택하지 않는다.

### 기존 key schema + pipeline + 목적별 조회

각 경매의 독립적인 hash와 현재 write 원자성을 유지한다. 서버가 처리하는 hash 명령 수 자체는 후보 수에 비례하지만 connection 대여와 네트워크 왕복은 batch당 한 번으로 제한된다. 불필요한 state·stream·bidder 조회도 제거할 수 있다. 이 방식을 채택한다.

## 상세 설계

### 상태 batch 조회

`readAuctionStates(List<Integer>)`는 입력 순서를 유지해 `Map<Integer, AuctionState>`를 반환한다. `StringRedisTemplate.executePipelined` 안에서 후보 state hash를 조회하고, 응답을 기존 단건 parser로 해석한다. 없는 키와 형식이 잘못된 state는 결과 map에서 제외한다. Redis timeout과 connection 오류는 잡지 않고 상위로 전파한다.

필터가 좁아 여러 ZSET batch를 읽더라도 각 batch는 ZSET 1회와 state pipeline 1회만 사용한다. pipeline 크기는 기존 `SORT_ZSET_FETCH_BATCH_SIZE=50`으로 제한된다.

### 목록 사용자 상태 조회

최종 content가 정해진 뒤 `auction:dashboard:participating:{userId}`에 `SMISMEMBER`를 한 번 호출한다. 참여로 확인된 auctionId에 대해서만 bidder hash를 pipeline 조회한다. 익명 사용자는 Redis를 추가 조회하지 않는다.

목록 응답은 필터 단계에서 읽은 `AuctionState`와 batch로 읽은 `MyBidState`만 사용한다. recent-bids와 state 재조회는 하지 않는다. 참여 집합은 `bid-accept.lua`와 `auction-state-seed.lua`가 이미 유지하므로 새 write path는 추가하지 않는다.

참여 집합에 id가 있으나 bidder hash가 없는 비정상 상태는 `MyBidStatus.NONE`으로 응답한다. Redis 오류는 DB fallback으로 바꾸지 않고 예외로 전파한다.

### 단건 조회

state hash 응답을 `StoredAuctionState(AuctionState state, Integer highestBidderId)`로 한 번 파싱한다. `read()`는 이 저장 상태, recent-bids, 선택적인 bidder state로 `RealtimeState`를 만든다. 이로써 `readSnapshot()`이 동일 hash를 두 번 읽는 구조를 제거한다.

입찰 내역은 저장 상태 1회와 recent-bids 1회만 읽는다. 입찰 컨텍스트는 최초 state 조회 결과를 재사용하며, 실제 state miss일 때만 기존 seeder를 호출하고 한 번 다시 읽는다. hit 경로에서는 seed 확인용 중복 state 조회가 없다.

### 오류와 fallback

- 빈 state hash: 실제 cache miss로 간주하며 기존 cold seed/DB fallback 정책을 적용한다.
- 필수 field 누락 또는 숫자/enum 파싱 실패: 손상 state로 간주하고 null로 처리하는 기존 외부 계약을 유지하되 테스트로 구분한다.
- timeout, connection failure, command failure: 예외를 삼키지 않으며 DB fallback을 실행하지 않는다.

## 예상 명령 패턴

첫 ZSET batch에서 `size=20`을 채우고 참여 경매가 없다고 가정한다.

| 요청 | 변경 전 | 변경 후 |
| --- | ---: | ---: |
| 익명 목록 | 111개, 대부분 순차 왕복 | 51개, 2회 왕복 |
| 로그인 목록 | 131개, 대부분 순차 왕복 | 52개, 3회 왕복 |
| 입찰 내역 | state 3회 + stream 1회 | state 1회 + stream 1회 |
| 입찰 컨텍스트(hit) | state 3회 + stream/bidder | state 1회 + stream/bidder |

목록의 state hash 명령 50개는 유지되지만 pipeline 한 번으로 전송한다. 사용자 미참여 bidder hash 20개는 제거되어 cache hit ratio 분모의 주기적 miss도 감소한다.

## 검증

- reader 통합 테스트에서 pipeline 결과 순서, miss 제외, malformed 제외를 검증한다.
- service 테스트에서 목록이 batch API를 사용하고 state/recent-bids를 다시 읽지 않는지 응답 결과로 검증한다.
- 참여/미참여 사용자의 `myBidStatus`, `myBidAmount` 정합성을 검증한다.
- 입찰 내역과 컨텍스트의 응답 내용 및 cold miss fallback을 검증한다.
- Redis 오류가 DB fallback으로 바뀌지 않는 테스트를 유지/추가한다.
- 전체 backend 테스트를 통과시킨다.
- 로컬 부하 테스트에서 Redis connections, Tomcat busy threads, Hikari active/pending, cache hit ratio, API p95/p99, 오류율을 변경 전과 비교한다.

