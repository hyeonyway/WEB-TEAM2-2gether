# Redis Stream 이벤트 계약

`auction:timeline-events`는 producer의 도메인 상태 전이 결과를 전달하고, consumer는 이벤트를 먼저
`auction_bid_event_inbox`에 기록한 뒤 DB 정합성을 검증하여 projection한다. producer/API와 consumer의
DB projection 책임은 분리한다.

## 공통 규칙

- 모든 이벤트는 `eventType`, `schemaVersion`, `occurredAt`을 포함한다.
- 같은 Stream entry는 `streamId`로 inbox에 한 번만 기록한다.
- consumer는 하나의 leader가 글로벌 Stream의 `XADD` 순서대로 처리한다. 따라서 `auction.created.v1`을
  먼저 발행하면, 뒤의 같은 경매 입찰 이벤트보다 먼저 DB projection을 완료한다.
- projection 오류는 inbox에 `ERROR`로 기록한다. 이후 이벤트는 `PENDING`으로 수신 기록만 남긴다.

## `auction.created.v1`

경매 생성 producer가 발행하며 consumer가 `Auction` 및 `images` DB projection을 수행한다.

| Field | Required | 설명 |
| --- | --- | --- |
| `schemaVersion` | Y | `1` |
| `sellerId` | Y | 판매자 user ID |
| `itemId` | Y | `card_metadata` ID |
| `auctionName`, `description` | Y | 경매 제목·설명 |
| `sellerMemo`, `psaCertification`, `selfGrade` | N | 기존 경매 생성 메타데이터 |
| `psaVerified` | Y | PSA 검증 결과 |
| `startPrice`, `buyNowPrice`, `deliveryFee`, `bidPriceUnit` | Y/N | 가격·배송 계약 (`buyNowPrice`는 nullable) |
| `imagePaths` | Y | 줄바꿈으로 연결한 image path 목록 |
| `closeTime` | Y | ISO-8601 UTC 종료 시각 |
| `idempotencyKey`, `idempotencyRequestHash` | Y | 생성 중복 및 요청 충돌 판별값 |
| `occurredAt` | Y | 생성 승인 시각 |

consumer는 card와 판매자 존재를 확인한 후 생성한다. 같은 `sellerId`와 `idempotencyKey`가 이미 있으면
중복 생성하지 않는다.

## `wallet.*` v2

`wallet.charged.v1`, `wallet.refunded.v1`, `wallet.settled.v1`, `wallet.cancel-refunded.v1`은 모두
`schemaVersion=2` snapshot 계약을 사용한다.

필수 필드는 `eventId`, `userId`, `walletVersion`, `availableBalance`, `frozenBalance`, `transactionType`,
`transactionAmount`, `occurredAt`이다. consumer는 `WalletStateChangedStreamEvent`로 파싱하고 DB wallet,
point record, hold를 version/eventId 기준으로 멱등 projection한다.

## 입찰 이벤트

`bid.accepted.v1`, `auction.buy-now.v1`은 `schemaVersion=1`을 사용한다. `auctionId`, `auctionVersion`,
`bidderId`, 가격·입찰 수·이전 최고 입찰자·idempotency 정보·마감/상태 snapshot을 포함한다.

consumer는 DB의 경매와 LEADING bid를 lock하여 이벤트 버전 연속성, 이전 최고 입찰자, 가격, 마감 상태를
검증한 뒤 입찰·경매·hold를 반영한다.

## `auction.close-requested.v1` (예정)

스케줄러가 마감 대상 경매를 발견하면 발행한다. producer는 종료 결과를 계산하지 않는다.

| Field | Required | 설명 |
| --- | --- | --- |
| `schemaVersion` | Y | `1` |
| `auctionId` | Y | 종료 대상 경매 ID |
| `occurredAt` | Y | 종료 요청 시각 |

consumer는 DB에서 경매를 lock하고 진행 상태·마감 시각을 검증한다. LEADING bid가 없으면 `FAILED`로,
있으면 낙찰 입찰 `WON`, 경매 `ENDED`, wallet hold capture, 주문 생성까지 하나의 DB transaction으로 projection한다.
