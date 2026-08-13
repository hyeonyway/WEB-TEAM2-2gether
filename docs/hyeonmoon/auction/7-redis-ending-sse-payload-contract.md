# Redis 마감임박 SSE Payload 계약 보완 설계

**관련 이슈:** #446

**선행 작업:** #436 Redis 마감임박 단발 랜덤 연장
**범위:** Redis 프로필의 `AUCTION_ENDING_STARTED` 공개 SSE 이벤트와 이를 소비하는 프론트 캐시 갱신

## 1. 배경

Redis 경매가 마감 5분 전에 `OPEN → ENDING`으로 전이되면,
`RedisAuctionEndingTransitionProcessor`는 `AUCTION_ENDING_STARTED` SSE를 발행한다.
그러나 Redis 전용 `AuctionStreamPayload.endingStarted(...)` overload는
`startPrice`, `currentPrice`, `bidIncrement`, `bidCount`를 채우지 않는다.

프론트 `useAuctionStream`은 모든 경매 SSE에 대해 위 수치 필드를 검증한다.
따라서 Redis ENDING 이벤트는 파싱 단계에서 버려지고, 화면은 새로고침 후 REST
조회가 실행돼야만 `ENDING`/`마감임박` 상태를 표시한다.

## 2. 목표

- Redis ENDING 전이 성공 시 프론트가 현재 연결에서 즉시 `AUCTION_ENDING_STARTED`를 처리한다.
- 이벤트는 기존 경매 stream payload의 공통 필수 필드를 모두 제공한다.
- 공개 `endsAt`은 실제 랜덤 연장 마감이 아니라 기존 `estimatedCloseTime`을 유지한다.
- 프론트 파서 예외나 페이지별 특례 없이 기존 `applyAuctionEvent()` 경로로 목록·상세 캐시를 갱신한다.

## 3. 원인과 선택지

### 선택지 A — 프론트에서 null 수치를 허용

프론트가 ENDING 이벤트의 비어 있는 가격·입찰 수를 기존 캐시 값으로 보완한다.

- 장점: 백엔드 변경량이 작다.
- 단점: 이벤트 생산자가 불완전한 snapshot을 보내며, 캐시 미스·재연결·다른 소비자가 같은
  이벤트를 사용할 때 별도 보완 규칙이 필요하다.

### 선택지 B — Redis 이벤트 생산자가 완전한 snapshot 제공 (채택)

Redis state hash의 현재 수치를 읽어 payload에 넣고, 기존 프론트 파서와 cache 적용 함수를
그대로 사용한다.

- 장점: DB 프로필의 ENDING 이벤트와 동일한 공개 계약을 유지하고, 소비자는 예외 처리가 없다.
- 단점: 전이 시 hash field 네 개를 추가로 읽는다. 경매당 단 한 번 실행되는 경로라 비용은 작다.

## 4. 이벤트 계약

Redis 전이 성공 시 공개되는 `AUCTION_ENDING_STARTED` payload는 아래 필드를 보장한다.

| 필드 | 값 | 용도 |
| --- | --- | --- |
| `auction_id` | 전이된 경매 ID | 구독 및 캐시 대상 식별 |
| `start_price` | Redis hash의 `startPrice` | 프론트 공통 payload 검증 |
| `current_price` | Redis hash의 `currentPrice` | 현재 가격 동기화 |
| `bid_increment` | Redis hash의 `bidIncrement` | 다음 입찰 단위 동기화 |
| `bid_count` | Redis hash의 `bidCount` | 목록·상세 입찰 수 갱신 |
| `ends_at` | Redis hash의 `estimatedCloseTime` | 공개 예정 마감 유지, 실제 연장 시각 비공개 |
| `status` | `ENDING` | UI를 `마감임박` 상태로 전환 |
| `occurred_at` | 전이 실행 시각 | 이벤트 발생 시각 |

`card_*`, seller, 입찰자, 낙찰자 필드는 ENDING 이벤트에 필요하지 않으며 기존 프론트 계약도
요구하지 않는다.

## 5. 백엔드 변경

### 5.1 Redis state 읽기

`RedisAuctionEndingTransitionProcessor`는 Lua 전이 전에 현재 `auction:state:{auctionId}`에서
다음 값을 읽는다.

```text
startPrice, currentPrice, bidIncrement, bidCount, estimatedCloseTime
```

Lua가 `TRANSITIONED`를 반환한 경우에만 읽은 수치와 `estimatedCloseTime`으로 공개 SSE를 만든다.
`NOOP` 또는 Lua 실패에는 SSE를 만들지 않는다.

전이 동안 `startPrice`·`bidIncrement`는 불변이고, `currentPrice`·`bidCount`는 Lua 전이 자체가
바꾸지 않는다. 따라서 같은 전이 EVAL 직전의 값이 공개 snapshot으로 유효하다.

### 5.2 Payload factory

`AuctionStreamPayload`의 Redis용 `endingStarted` factory는 아래 입력을 받도록 확장한다.

```java
static AuctionStreamPayload endingStarted(
    Integer auctionId,
    Long startPrice,
    Long currentPrice,
    Long bidIncrement,
    Integer bidCount,
    Instant estimatedCloseTime,
    Instant occurredAt
)
```

factory는 `AUCTION_ENDING_STARTED`, `ENDING`, 공개 `estimatedCloseTime`과 전달된 수치를
payload에 기록한다. 실제 `closeTime`은 이 factory와 호출부 어느 곳에서도 공개하지 않는다.

## 6. 프론트 영향

프론트 코드 변경은 원칙적으로 필요 없다.

1. `useAuctionStream.parsePayload()`가 완전한 payload를 검증한다.
2. `applyAuctionEvent()`가 해당 `auction_id`의 `status`, 가격, 입찰 수, `endsAt`을 갱신한다.
3. `AuctionCatalog`은 `status === 'ENDING'`일 때 공개 예정 시각 대신 `마감임박`을 표시한다.

따라서 목록을 새로고침하지 않아도 SSE 수신 즉시 카드 표현이 바뀐다.

## 7. 테스트 범위

### 백엔드

- `RedisAuctionEndingTransitionProcessorTest`
  - 성공한 전이가 완전한 `AuctionStreamPayload.endingStarted(...)`를 발행한다.
  - `startPrice`, `currentPrice`, `bidIncrement`, `bidCount`, `estimatedCloseTime`, `ENDING`을 단언한다.
  - 실제 연장 `closeTime`은 공개 payload의 `endsAt`에 들어가지 않음을 단언한다.
- Redis Lua/processor 통합 테스트
  - 실제 Redis hash 기반 전이 후 공개 SSE payload가 필수 필드를 가진다.

### 프론트

- `useAuctionStream` 또는 `auctionStreamCache` 테스트
  - 완전한 `AUCTION_ENDING_STARTED` 이벤트가 파싱·적용된다.
  - 캐시 대상 경매의 상태가 `ENDING`으로 바뀌고, 공개 `endsAt`은 유지된다.

## 8. 완료 조건

- Redis 경매가 ENDING으로 전이될 때 연결 중인 프론트 화면이 새로고침 없이 `마감임박`으로 바뀐다.
- 이벤트는 현재 프론트 parser의 필수 필드 검증을 통과한다.
- 실제 랜덤 연장 마감시각은 REST·SSE 어떤 공개 payload에도 포함되지 않는다.
- DB 프로필과 Redis 프로필 모두 동일한 `AUCTION_ENDING_STARTED` 소비 계약을 만족한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
