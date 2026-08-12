# Redis Stream 이벤트 계약

`auction:timeline-events`는 producer의 도메인 상태 전이 결과를 전달하고, consumer는 이벤트를 먼저
`auction_bid_event_inbox`에 기록한 뒤 DB 정합성을 검증하여 projection한다. producer/API와 consumer의
DB projection 책임은 분리한다.

## 전체 쓰기 이벤트 목록과 발생 계약

DB 변경은 테이블 CRUD가 아니라 사용자 행동 또는 하나의 원자적 도메인 전이 단위로 event를 발행한다.
각 producer는 event를 먼저 Stream에 append하고, consumer만 DB projection을 수행한다. 아래 `상태`가
`구현 예정`인 event의 producer/API는 아직 변경하지 않는다.

| 도메인 전이 | Event | 발생 producer | Consumer DB projection | 상태 |
| --- | --- | --- | --- | --- |
| 경매 등록 | `auction.created.v1` | 경매 등록 command producer | `auctions`, `images` 생성 | 구현 |
| 경매 수정 | `auction.updated.v1` | 경매 수정 command producer | 허용 필드 갱신 | 구현 예정 |
| 판매자 취소 | `auction.cancel-requested.v1` | 취소 command producer | 경매 취소, hold release, 후속 환불 | 구현 예정 |
| 마감 종료 요청 | `auction.close-requested.v1` | 마감 scheduler | DB lock 후 유찰/낙찰 결정, 주문·hold 반영 | 구현 예정 |
| 유찰 결과 | `auction.failed.v1` | 종료 projection의 후속 도메인 event | `FAILED`, hold release 결과 전파 | 구현 예정 |
| 낙찰 결과 | `auction.closed.v1` | 종료 projection의 후속 도메인 event | `ENDED`, winner, order 결과 전파 | 구현 예정 |
| 일반 입찰 승인 | `bid.accepted.v1` | 입찰 승인 producer | `bids`, `auctions`, hold projection | 구현 |
| 즉시 낙찰 승인 | `auction.buy-now.v1` | 입찰 승인 producer | 입찰·경매 종료·주문·hold capture | 구현 |
| 지갑 충전 | `wallet.charged.v1` | 충전 command producer | wallet snapshot, point record | 구현 |
| 지갑 환불 | `wallet.refunded.v1` | 환불 command producer | wallet snapshot, point record | 구현 |
| hold 생성/해제/capture | `wallet.hold-created.v1`, `wallet.hold-released.v1`, `wallet.hold-captured.v1` | 입찰·종료 producer | wallet hold 및 wallet snapshot | 구현 예정 |
| 판매자 정산 | `wallet.settled.v1` | 구매 확정 producer | wallet snapshot, point record | 구현 |
| 주문 취소 환불 | `wallet.cancel-refunded.v1` | 주문 취소 producer | wallet snapshot, point record | 구현 |
| 주문 취소 | `order.cancelled.v1` | 주문 취소 command producer | order 상태, 환불 요청 | 구현 예정 |
| 구매 확정 | `order.confirmed.v1` | 구매 확정 command producer | order 상태, 정산 요청 | 구현 예정 |
| 알림 생성 | `notification.created.v1` | 각 도메인 projection | notifications 생성·SSE 발행 | 구현 예정 |
| 알림 읽음 | `notification.read.v1` | 알림 읽음 command producer | notification read 상태 갱신 | 구현 예정 |
| 사용자 상태 변경 | `user.status-changed.v1` | 회원 탈퇴/정지/복구 producer | users 상태 갱신 | 구현 예정 |

### 순서와 식별자 계약

- 같은 aggregate의 후속 event는 반드시 선행 event가 `XADD`된 뒤에 append한다. 예: `auction.created.v1`
  다음에 같은 경매의 `bid.accepted.v1`을 append한다.
- `auction.created.v1`의 DB ID는 consumer projection 결과이므로, 이후 producer는 DB ID가 필요한 event를
  발행하기 전에 생성 projection 완료를 확인하거나 producer가 사용할 stable aggregate ID 계약을 별도로 둔다.
- consumer는 글로벌 Stream의 단일 leader를 사용하므로 Redis Stream ID 순서대로 DB projection한다.
  producer가 역순으로 append한 event를 consumer가 재정렬하지는 않는다.
- 모든 event는 producer 단위 멱등 key와 request hash 또는 event UUID를 가져야 하며, consumer는 inbox
  `stream_id`와 aggregate별 version으로 재전달을 중복 반영하지 않는다.

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
