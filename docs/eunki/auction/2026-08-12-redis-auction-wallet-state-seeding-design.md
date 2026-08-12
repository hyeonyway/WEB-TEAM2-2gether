# Redis 경매·지갑 상태 초기화 설계

**관련 이슈:** #397

## 목표

Redis에 없는 활성 경매와 지갑 상태를 필요한 시점에 MySQL projection으로 초기화한다. 서버 기동 시에는
마감이 임박했거나 최근에 생성된 활성 경매만 제한적으로 warm-up하고, 지갑과 그 외 경매는 요청 시
on-demand로 초기화한다.

## 범위

- 활성 경매 Hash(`auction:state:{auctionId}`)와 마감 ZSET(`auction:active:by-close-time`) 초기화
- 지갑 Hash(`wallet:balance:{userId}`)와 HELD 예치금(`wallet:hold:{auctionId}:{userId}`) 초기화
- 기존 지갑 전수 bootstrap 제거
- 활성 경매 warm-up 설정과 상태 miss 초기화 테스트

진행 주문 전체의 Redis 조회·초기화와 Stream replay를 통한 전체 재구성은 이번 범위에서 제외한다.

## 초기화 정책

| 대상 | 기동 시 | 요청 시 |
| --- | --- | --- |
| 마감 임박 활성 경매 | warm-up | state miss면 초기화 |
| 최근 활성 경매 | 설정 개수만 warm-up | state miss면 초기화 |
| 그 외 활성 경매 | 초기화하지 않음 | 상세 조회·입찰 직전에 초기화 |
| 지갑·예치금 | 초기화하지 않음 | 잔액 조회·입찰·충전·환불 직전에 초기화 |

Redis key가 이미 있으면 MySQL 값을 다시 쓰지 않는다. 초기화는 `HSETNX` 기반 Lua로 수행해 동시 state
miss 요청에서도 기존 Redis 상태를 덮어쓰지 않는다.

## 활성 경매 상태

MySQL에서 `OPEN`, `ENDING` 경매와 현재 최고 입찰을 읽어 다음 필드를 구성한다.

- `status`, `sellerId`, `itemId`, `cardName`
- `currentPrice`, `bidIncrement`, `buyNowPrice`
- `closeTime`, `closeTimeEpochMillis`
- `highestBidderId`, `highestHoldAmount`, `sequence`, `bidCount`

Hash 생성과 `auction:active:by-close-time` ZSET 등록은 하나의 Lua 실행으로 처리한다. `ENDED`, `FAILED`,
존재하지 않는 경매는 Redis state를 만들지 않는다.

## 지갑 상태

MySQL projection의 `point`, `projectionVersion`, HELD hold 합계로 아래 값을 만든다.

- `availableBalance = point - frozenBalance`
- `frozenBalance`
- `walletVersion = projectionVersion`

각 HELD hold는 `wallet:hold:{auctionId}:{userId}`에 저장한다. 기존 Redis 지갑 Hash가 있으면 초기화를
건너뛴다.

## Warm-up

기동 시 활성 경매만 제한적으로 초기화한다.

- `auction.state-seeding.warm-up.enabled`
- `auction.state-seeding.warm-up.closing-window-minutes`
- `auction.state-seeding.warm-up.recent-limit`
- `auction.state-seeding.warm-up.batch-size`

마감 임박 경매를 먼저, 부족한 수만 최근 활성 경매로 채운다. Redis가 AOF 복구로 이미 상태를 보유한
경우에도 Lua의 조건부 저장으로 추가 쓰기 없이 건너뛴다.

## 실패와 제약

초기화는 MySQL projection 기준이다. MySQL이 Stream consumer보다 지연된 상태에서 Redis state를 잃으면
최신 상태까지 복구할 수 없다. 이 경우의 Stream replay 기반 재구성은 별도 장애 복구 이슈에서 다룬다.

이번 이슈는 Redis state miss의 정상 운영 경로와 콜드 스타트 완화를 다룬다. Redis 또는 MySQL 접근 실패는
부분 상태를 만들지 않고 기존 인프라 예외 흐름으로 전파한다.

## 검증

- 지갑 전체 bootstrap이 기동 시 실행되지 않는다.
- warm-up은 설정 범위의 활성 경매만 초기화한다.
- 활성 경매 상세 조회와 입찰은 state miss 후 초기화된 state를 사용한다.
- 지갑 명령은 state miss 후 초기화된 지갑·HELD hold를 사용한다.
- 동시에 초기화해도 기존 Redis Hash와 ZSET score를 덮어쓰지 않는다.
