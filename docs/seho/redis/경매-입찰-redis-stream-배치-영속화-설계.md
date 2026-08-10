# Redis Stream 기반 경매 입찰 배치 영속화

## 목표

Redis Lua Script가 원자적으로 승인한 입찰을 Redis Stream에 기록하고, `redis` 프로필의
비동기 consumer가 DB의 입찰 이력과 경매 스냅샷으로 영속화한다. Stream 전달은
at-least-once이므로 DB inbox와 트랜잭션 후 ACK로 중복 반영을 막는다.

이번 범위는 Stream 소비·재시도·DLQ와 기존 DB 입찰 반영이다. Lua 입찰 검증·HTTP 즉시
응답·Redis Pub/Sub SSE 전파는 다른 담당 영역이다. Lua는 Redis 지갑 mirror의 가용 잔액과
hold 상태까지 원자적으로 예약하고, Consumer는 그 결과를 기존 DB 지갑과 경매 테이블에
영속화한다.

## 프로필과 토폴로지

- 기본 프로필에는 consumer 빈이 없다. `spring.profiles.active=redis`일 때만 실행한다.
- Stream key: `auction:bid-events:v1`
- consumer group: `auction-bid-persistence`
- DLQ key: `auction:bid-events:dlq:v1`
- retry counter hash: `auction:bid-events:retry-count:v1`
- 한 consumer는 `XREADGROUP GROUP auction-bid-persistence <instance-id> COUNT 100 BLOCK 1000`
  으로 읽는다. 시작과 함께 group이 없으면 `MKSTREAM`으로 생성한다.
- PEL의 30초 이상 유휴 메시지는 `XAUTOCLAIM`으로 회수한다.

현재 전제는 단일 Redis 인스턴스다. Lua Script가 경매별 context key와 전역 Stream key를
같은 호출에서 갱신하므로 Redis Cluster 전환 시 hash slot 토폴로지를 별도로 설계한다.

## 생산 이벤트 계약

Lua Script는 일반 입찰 승인 뒤 `bid.accepted.v1`, 즉시 낙찰 승인 뒤
`auction.buy-now.v1` 이벤트를 한 Stream entry에 `XADD`한다. 모든 이름은
camelCase 문자열이고 시각은 UTC ISO-8601 `Instant`다.

| field | 형식 | 설명 |
| --- | --- | --- |
| `eventType` | `bid.accepted.v1` / `auction.buy-now.v1` | 이벤트 타입 |
| `schemaVersion` | `1` | 계약 버전 |
| `auctionId` | integer | 경매 ID |
| `auctionVersion` | long | 경매별 단조 증가 입찰 버전 |
| `bidderId` | integer | 현재 입찰자 |
| `bidPrice` | long | 승인된 입찰가 |
| `previousBidderId` | integer/null | 이전 최고 입찰자 |
| `idempotencyKey` | string | 요청 멱등성 키 |
| `idempotencyRequestHash` | string | 요청 본문 hash |
| `currentPrice` | long | 갱신된 현재가 |
| `bidCount` | integer | 갱신된 입찰 수 |
| `closeTime` | instant | 갱신된 마감 시각 |
| `auctionStatus` | enum | `OPEN` 또는 `ENDING` |
| `occurredAt` | instant | Lua 승인 시각 |

`auctionVersion`은 경매 context에서 승인 때마다 증가한다. 같은 경매의 더 낮거나 같은
버전은 DB 상태를 변경하지 않는다. 생산자는 `idempotencyKey`와 request hash를 생략하지
않아야 하며, 동일 키로 다른 요청을 승인하면 안 된다.

`auction.buy-now.v1`은 `auctionStatus=ENDED`, 최종 `bidPrice/currentPrice`, 종료 시각을
반드시 포함한다. Consumer는 기존 LEADING bid를 OUTBID로 바꾸고 현재 bid를 WON으로 저장하며
경매 스냅샷을 ENDED로 반영한다. 낙찰자 wallet hold와 capture도 같은 DB 트랜잭션에서 처리한다.
주문 생성과 SSE 종료 전파는 별도 도메인 소유자와의 연동이 필요한 후속 범위다.

## DB 영속화와 멱등성

`auction_bid_event_inbox`는 `stream_id` UNIQUE 제약을 가진다. consumer는 각 entry를
처리할 때 inbox를 먼저 기록한다. 이미 존재하면 DB 변경 없이 성공으로 간주하고 ACK한다.

`auctions.last_bid_event_version`은 경매별 마지막 반영 버전이다. Stream ID 중복 방지와는
다른 역할을 한다. Consumer 재시도 또는 다중 consumer의 처리 타이밍 때문에 version 11이
DB에 먼저 반영된 뒤 version 10이 늦게 도착할 수 있다. 이때 `10 <= 11`이면 이전 이벤트의
현재가·입찰 수·마감 시각·상태를 적용하지 않아 최신 상태가 되돌아가는 것을 막는다.

| 저장 값 | 목적 |
| --- | --- |
| `auction_bid_event_inbox.stream_id` | 같은 Redis Stream 메시지의 중복 DB 반영 방지 |
| `auctions.last_bid_event_version` | 같은 경매의 오래된 상태 이벤트가 최신 상태를 덮는 것 방지 |

새 entry는 한 DB 트랜잭션에서 다음 순서로 처리한다.

1. inbox를 저장한다.
2. 대상 auction을 비관적 잠금으로 조회한다.
3. `auctionVersion`이 마지막 적용 버전보다 작거나 같으면 inbox만 유지하고 끝낸다.
4. 새 입찰자 DB wallet에 `hold`하고, 이전 최고 입찰자의 DB wallet hold를 `release`한다.
   두 지갑을 함께 처리할 때는 사용자 ID 오름차순으로 호출해 기존 락 순서를 유지한다.
5. 현재 LEADING bid를 OUTBID로 전환하고 새 `Bid`를 LEADING으로 저장한다.
6. 이벤트 스냅샷으로 현재가, 입찰 수, 마감 시각, 상태, 마지막 적용 버전을 갱신한다.

`auction.buy-now.v1`은 새 입찰자의 hold 직후 같은 트랜잭션에서 `capture`한다. 따라서
이전 최고 입찰자 release, 낙찰자 hold/capture, bid WON, auction ENDED가 DB에서 함께
커밋되며, 실패하면 Stream entry는 ACK되지 않아 재시도한다.

커밋에 성공한 entry만 ACK한다. DB 커밋 전 프로세스가 종료되어도 PEL 재전달과 inbox
UNIQUE 제약이 재처리를 안전하게 만든다.

## 재시도와 DLQ

읽기·DB·락 오류는 retry counter를 증가시켜 최대 3회 재시도한다. 성공 ACK 후에는 retry
counter를 제거한다. 계약 파싱 오류와 존재하지 않는 경매 같은 업무 오류, 또는 3회 초과
실패는 DLQ에 다음 field를 추가해 기록하고 원본을 ACK한다.

- `originalStreamId`, `payload`, `failureType`, `failureMessage`, `failedAt`, `retryCount`

DLQ 발생 시 `auction.bid.stream.dlq` 카운터와 구조화 로그를 남기고 Slack 경고를 보낸다.
Stream은 v1에서 자동 trim하지 않는다. Stream 길이, PEL 수, 재시도 수, DLQ 수를 메트릭으로
관찰한 뒤 보존 정책을 별도 작업에서 정한다.

## 전환 조건

`redis` 프로필은 Lua 생산자와 입찰 HTTP 경로가 완성되고, DB 기준 마감 작업이 Redis context와
충돌하지 않도록 연동된 뒤에만 활성화한다. 기존 동기 `AuctionCommandService.participate()`와
이 consumer를 같은 입찰 요청에 동시에 사용하면 안 된다.

> 이 문서는 Codex의 도움을 받아 작성하였습니다.
