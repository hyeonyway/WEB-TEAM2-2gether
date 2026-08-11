# Redis Stream 기반 경매 입찰 단건 영속화

## 목표

Redis Lua Script가 원자적으로 승인한 충전·입찰·지갑 hold/release/capture를 하나의 Redis Stream에
기록하고, `redis` 프로필의 비동기 consumer가 DB의 지갑·입찰 이력·경매 스냅샷으로 영속화한다. Stream 전달은
at-least-once이므로 DB inbox와 트랜잭션 후 ACK로 중복 반영을 막는다.

이번 범위는 Stream 소비·재시도·DLQ와 기존 DB 지갑·입찰 반영이다. Lua 입찰 검증·HTTP 즉시
응답·Redis Pub/Sub SSE 전파는 다른 담당 영역이다. Lua는 Redis 지갑 mirror의 충전·가용 잔액과
hold 상태까지 원자적으로 갱신하고, Consumer는 그 결과를 기존 DB 지갑과 경매 테이블에
영속화한다.

## 프로필과 토폴로지

- 기본 프로필에는 consumer 빈이 없다. `spring.profiles.active=redis`일 때만 실행한다.
- Stream key: `auction:timeline-events`
- consumer group: `auction-timeline-persistence`
- DLQ key: `auction:timeline-events:dlq`
- retry counter hash: `auction:timeline-events:retry-count`
- version pause hash: `auction:timeline-events:paused-auctions`
- consumer lease lock: `auction:timeline-events:consumer-leader-lock`
- 한 consumer는 `XREADGROUP GROUP auction-timeline-persistence <instance-id> COUNT 1 BLOCK 1000`
  으로 읽는다. 한 번의 poll은 최대 100건을 연속 처리하지만, **각 entry는 항상 별도 DB
  트랜잭션과 별도 ACK**를 사용한다. backlog가 있을 때에는 entry 사이에 100ms를 기다리지 않고,
  한도까지 처리한 뒤에만 poll delay를 적용한다. 시작과 함께 group이 없으면 `MKSTREAM`으로 생성한다.
- PEL의 30초 이상 유휴 메시지는 pending 조회 뒤 `XCLAIM`으로 회수한다.
- 여러 애플리케이션 인스턴스가 떠도 Redis lease 락을 획득한 인스턴스만 poll 전체를 실행한다.
  `poll → DB transaction → ACK`가 끝나면 소유자 token을 비교해 락을 해제한다. 기본 최대 lease는
  5분(`AUCTION_REDIS_BID_CONSUMER_LOCK_AT_MOST_FOR`)이며, 운영 환경의 최장 단건 처리 시간보다
  길게 설정해야 한다.

현재 전제는 단일 Redis 인스턴스다. Lua Script가 경매별 context key와 전역 Stream key를
같은 호출에서 갱신하므로 Redis Cluster 전환 시 hash slot 토폴로지를 별도로 설계한다.

## 생산 이벤트 계약

Lua Script는 충전 승인 뒤 `wallet.charged.v1`, 일반 입찰 승인 뒤 `bid.accepted.v1`, 즉시 낙찰 승인 뒤
`auction.buy-now.v1` 이벤트를 **동일 Stream**에 `XADD`한다. 모든 이름은
camelCase 문자열이고 시각은 UTC ISO-8601 `Instant`다.

| field | 형식 | 설명 |
| --- | --- | --- |
| `eventType` | `bid.accepted.v1` / `auction.buy-now.v1` | 이벤트 타입 |
| `schemaVersion` | `1` | 계약 버전 |
| `auctionId` | integer | 경매 ID |
| `auctionVersion` | long | 경매별 단조 증가 입찰 버전 |
| `bidderId` | integer | 현재 입찰자 |
| `requestedPrice` | long | HTTP `BidCreateRequest.price` 원 요청가 |
| `bidPrice` | long | 승인된 입찰가 |
| `previousBidderId` | integer/null | 이전 최고 입찰자 |
| `idempotencyKey` | string | 요청 멱등성 키 |
| `idempotencyRequestHash` | string | 요청 본문 hash |
| `currentPrice` | long | 갱신된 현재가 |
| `bidCount` | integer | 갱신된 입찰 수 |
| `closeTime` | instant | 갱신된 마감 시각 |
| `auctionStatus` | enum | 일반 입찰은 `OPEN`/`ENDING`, 즉시 낙찰은 `ENDED` |
| `occurredAt` | instant | Lua 승인 시각 |

`wallet.charged.v1`은 아래 계약을 사용한다.

| field | 형식 | 설명 |
| --- | --- | --- |
| `eventType` | `wallet.charged.v1` | 지갑 충전 이벤트 |
| `schemaVersion` | `1` | 계약 버전 |
| `userId` | integer | 충전 대상 사용자 |
| `amount` | long | 충전 금액(양수) |
| `idempotencyKey` | string | 충전 요청 멱등성 키(64자 이하) |
| `occurredAt` | instant | Redis 충전 승인 시각 |

`auctionVersion`은 경매 context에서 승인 때마다 증가한다. 같은 경매의 더 낮거나 같은
버전은 DB 상태를 변경하지 않는다. 생산자는 `idempotencyKey`, `requestedPrice`, request hash를
생략하지 않아야 하며, request hash는 기존 HTTP 경로와 동일하게 `SHA-256("{requestedPrice}\\0")`로
계산한다. 일반 입찰은 `requestedPrice == bidPrice`이고, 즉시 낙찰은 `requestedPrice >= buyNowPrice`,
`bidPrice == buyNowPrice`다.

`auction.buy-now.v1`은 `auctionStatus=ENDED`, 최종 `bidPrice/currentPrice`, 종료 시각을
반드시 포함한다. Consumer는 기존 LEADING bid를 OUTBID로 바꾸고 현재 bid를 WON으로 저장하며
경매 스냅샷을 ENDED로 반영한다. 낙찰자 wallet hold와 capture도 같은 DB 트랜잭션에서 처리한다.
같은 트랜잭션에서 `orders`를 생성하고 `AuctionClosedEvent`를 발행해 기존 종료/SSE listener 흐름도
연결한다.

## DB 영속화와 멱등성

`auction_bid_event_inbox`는 이름과 달리 지갑·입찰 타임라인 전체의 inbox이며 `stream_id` UNIQUE 제약을 가진다. consumer는 각 entry를
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

1. 기존 inbox에서 동일 Stream ID를 조회해 이미 처리된 entry면 DB 변경 없이 끝낸다.
2. 대상 auction을 비관적 잠금으로 조회하고 현재 LEADING bid를 조회한다.
3. `auctionVersion`이 마지막 적용 버전보다 작거나 같으면 inbox만 유지하고 끝낸다.
4. 새 입찰자 DB wallet에 `hold`하고, 이전 최고 입찰자의 DB wallet hold를 `release`한다.
   두 지갑을 함께 처리할 때는 사용자 ID 오름차순으로 호출해 기존 락 순서를 유지한다.
5. 현재 LEADING bid를 OUTBID로 전환하고 새 `Bid`를 LEADING으로 저장한다.
6. 이벤트 스냅샷으로 현재가, 입찰 수, 마감 시각, 상태, 마지막 적용 버전을 갱신하고 inbox와 새 bid를 저장한다.

`auction.buy-now.v1`은 새 입찰자의 hold 직후 같은 트랜잭션에서 `capture`한다. 따라서
이전 최고 입찰자 release, 낙찰자 hold/capture, bid WON, auction ENDED가 DB에서 함께
커밋된다. 또한 기존 즉시 낙찰 경로와 동일하게 `orders`를 생성하고 `AuctionClosedEvent`를
발행한다. 실패하면 Stream entry는 ACK되지 않아 재시도한다.

커밋에 성공한 entry만 ACK한다. DB 커밋 전 프로세스가 종료되어도 PEL 재전달과 inbox
UNIQUE 제약이 재처리를 안전하게 만든다.

### DB 반영 전 정합성 검증

Consumer는 Lua 승인 이벤트라도 DB 반영 전에 기존 입찰 규칙을 재검증한다.

- 이벤트 버전은 `last_bid_event_version + 1`이어야 한다. 이미 반영한 이전 버전은 무시하고,
  중간 버전이 누락된 이벤트는 처리하지 않는다.
- 입찰자는 판매자나 현재 최고 입찰자일 수 없고, 이벤트의 `previousBidderId`는 DB의 최고
  입찰자와 일치해야 한다.
- Stream ID·모든 식별자·금액·입찰 수는 양수여야 한다. `currentPrice`는 `bidPrice`와 같고,
  idempotency key는 64자 이하, request hash는 64자리 소문자 SHA-256 형식이면서 `requestedPrice`로
  계산한 값과 일치해야 한다.
- 일반 입찰은 진행 중 경매의 최소 호가 이상이어야 하며, 입찰 수는 DB 값보다 정확히 1 커야 한다.
  입찰 발생 시각은 기존 마감 시각보다 이전이고, 일반 입찰은 마감 시각을 앞당길 수 없다.
- 즉시 낙찰은 `buyNowPrice`와 동일한 승인 가격, `ENDED` 상태, 그리고 승인 시각과 같은 종료 시각이어야 한다.

검증 또는 DB wallet hold/release/capture가 실패하면 같은 DB 트랜잭션의 inbox·bid·auction 변경도
롤백되고 Redis ACK를 보내지 않는다.

## 재시도와 DLQ

읽기·DB·락 오류는 retry counter를 증가시켜 최대 3회 재시도한다. 성공 ACK 후에는 retry
counter를 제거한다. 재시도 대상 entry가 PEL에 남아 있으면 같은 consumer는 즉시 다시 가져오고,
다른 consumer가 남긴 PEL은 30초 유휴 뒤에만 가져온다. 가장 오래된 PEL이 존재하는 동안에는 뒤의
새 entry를 읽지 않는다. 따라서 실패·재시도 중에도 전역 타임라인 순서를 건너뛰지 않는다.
계약 파싱 오류와 존재하지 않는 경매 같은 업무 오류, 또는 3회 초과
실패는 DLQ에 다음 field를 추가해 기록하고 원본을 ACK한다. 각 Stream entry는 독립된 DB
트랜잭션으로 처리하므로, 한 이벤트의 실패가 다른 이벤트의 재시도·DLQ 판단에 영향을 주지 않는다.

단, **버전 단절은 DLQ로 ACK하지 않는다.** `auction:timeline-events:paused-auctions` hash에
경매 ID, 누락 직전 기대 버전, 원본 Stream ID, 사유를 기록하고 해당 PEL 메시지를 보류한다.
이 Stream은 지갑 상태까지 포함한 단일 전역 타임라인이므로, pause 중에는 뒤의 충전·입찰도 소비하지 않는다.
누락된 선행 이벤트를 재생해 정상 커밋하면 pause를 자동 해제하고
보류했던 후속 이벤트를 다시 처리한다. 누락 이벤트가 영구 유실된 경우에는 운영자가 Redis
경매 컨텍스트와 DB `last_bid_event_version`을 대조한 뒤, 선행 이벤트를 재발행하거나 별도
정합성 복구 절차를 수행해야 한다. pause 상태의 이벤트를 임의로 ACK해서는 안 된다.

Consumer Group은 분배 기능만 제공하므로, `auction:timeline-events:consumer-leader-lock` Redis
lease 락을 획득한 인스턴스만 한 번의 poll(DB 반영과 ACK 포함)을 실행한다. 이는 다중 인스턴스의
동시 DB 잠금과 데드락 가능성을 줄이기 위한 단일 실행 제어다.

- `originalStreamId`, `payload`, `failureType`, `failureMessage`, `failedAt`, `retryCount`

DLQ 발생 시 `auction.bid.stream.dlq` 카운터와 구조화 로그를 남기고 Slack 경고를 보낸다.

## 처리량과 운영 기준

이 경로의 우선순위는 처리량보다 정합성이다. 이벤트 하나는 하나의 DB 트랜잭션으로만 반영하고,
그 트랜잭션이 커밋된 뒤에만 ACK한다. 여러 인스턴스가 떠도 lease lock을 가진 단일 consumer만
상태를 바꾼다. 따라서 유입 TPS가 DB 영속 TPS보다 크면 Redis Stream은 유실 없이 backlog를
보관하는 버퍼가 된다.

- `AUCTION_REDIS_BID_MAX_RECORDS_PER_RUN` 기본값은 100이다. 이는 JDBC batch가 아니라 poll당
  연속 단건 처리 상한이다. DB가 느려도 각 이벤트의 원자성·순서·ACK 조건은 바뀌지 않는다.
- Stream 길이(`XLEN`), group lag, PEL 수(`XPENDING`), DLQ 길이, retry 수, consumer 처리 성공·실패
  카운터, Redis `used_memory`를 대시보드와 Slack 알림 대상으로 둔다.
- Stream은 v1에서 자동 trim하지 않는다. PEL 또는 미처리 entry를 trim하면 정합성 복구 근거를 잃기
  때문이다. 보존·trim은 실제 최대 backlog와 Redis 메모리 데이터를 관찰한 뒤 별도 작업으로 정한다.
- 운영 임계치는 Redis 최대 메모리와 평균 이벤트 크기로 산정한다. `warn` 임계치를 넘으면 lag와
  메모리 경고를 보내고, `critical` 임계치를 넘으면 Lua 생산자가 Stream lag 기준으로 새 입찰·충전
  요청을 제한하거나 `처리 대기 중` 응답을 반환하는 백프레셔를 적용한다. Consumer는 이를 위해
  이벤트를 버리거나 순서를 건너뛰지 않는다.

## 전환 조건

`redis` 프로필은 Lua 생산자와 입찰 HTTP 경로가 완성되고, DB 기준 마감 작업이 Redis context와
충돌하지 않도록 연동된 뒤에만 활성화한다. 기존 동기 `AuctionCommandService.participate()`와
이 consumer를 같은 입찰 요청에 동시에 사용하면 안 된다.

> 이 문서는 Codex의 도움을 받아 작성하였습니다.
