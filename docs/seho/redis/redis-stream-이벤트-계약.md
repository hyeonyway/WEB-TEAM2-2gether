# Redis Stream 이벤트 계약

`event:timeline`는 producer의 도메인 상태 전이 결과를 전달하고, consumer는 이벤트를 먼저
`auction_bid_event_inbox`에 기록한 뒤 DB 정합성을 검증하여 projection한다. producer/API와 consumer의
DB projection 책임은 분리한다.

## 전체 쓰기 이벤트 목록과 발생 계약

DB 변경은 테이블 CRUD가 아니라 사용자 행동 또는 하나의 원자적 도메인 전이 단위로 event를 발행한다.
각 producer는 event를 먼저 Stream에 append하고, consumer만 DB projection을 수행한다. 아래 `상태`가
`구현 예정`인 event의 producer/API는 아직 변경하지 않는다.

| 도메인 전이 | Event | 발생 producer | Consumer DB projection | 상태 |
| --- | --- | --- | --- | --- |
| 경매 등록 | `auction.created.v1` | 경매 등록 command producer | `auctions`, `images` 생성 | 구현 |
| 마감 종료 요청 | `auction.close-requested.v1` | 마감 scheduler | DB lock 후 유찰/낙찰 결정, 주문·hold 반영 | 구현 |
| 일반 입찰 승인 | `bid.accepted.v1` | 입찰 승인 producer | `bids`, `auctions`, hold projection | 구현 |
| 즉시 낙찰 승인 | `auction.buy-now.v1` | 입찰 승인 producer | 입찰·경매 종료·주문·hold capture | 구현 |
| 지갑 충전 | `wallet.charged.v1` | 충전 command producer | wallet snapshot, point record | 구현 |
| 지갑 환불 | `wallet.refunded.v1` | 환불 command producer | wallet snapshot, point record | 구현 |
| 판매자 정산 | `wallet.settled.v1` | 구매 확정 producer | wallet snapshot, point record | 구현 |
| 주문 취소 환불 | `wallet.cancel-refunded.v1` | 주문 취소 producer | wallet snapshot, point record | 구현 |

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

## 이벤트 payload 계약표

모든 event는 공통으로 `eventType`, `schemaVersion`, `occurredAt`을 포함한다. 아래 표의 **필수 payload**는
공통 필드를 제외한 값이며, `-`는 아직 구현되지 않은 계약이다.

| Event | Version | 발생 지점 | 필수 payload | Consumer DB 검증·projection | 상태 |
| --- | --- | --- | --- | --- | --- |
| `auction.created.v1` | 1 | 경매 등록 producer | `sellerId`, `itemId`, `auctionName`, `description`, `sellerMemo?`, `psaCertification?`, `selfGrade?`, `psaVerified`, `startPrice`, `buyNowPrice?`, `deliveryFee`, `bidPriceUnit`, `imagePaths`, `closeTime`, `idempotencyKey`, `idempotencyRequestHash` | seller/card 존재 확인 → `auctions`, `images` 생성; seller+idempotency key 중복 차단 | 구현 |
| `auction.updated.v1` | - | 경매 수정 producer | - | 허용 필드 갱신 | 예정 |
| `auction.cancel-requested.v1` | - | 판매자 취소 producer | - | 경매 취소·hold release·환불 후속 처리 | 예정 |
| `auction.close-requested.v1` | 1 | 마감 scheduler | `auctionId` | 경매 row lock, 마감 여부·상태 확인 → 유찰 `FAILED` 또는 낙찰 `WON`/`ENDED`/hold capture/order 생성 | 구현 |
| `auction.failed.v1` | - | 종료 projection 후속 event | - | 유찰 결과 전파 | 예정 |
| `auction.closed.v1` | - | 종료 projection 후속 event | - | 낙찰 결과 전파 | 예정 |
| `bid.accepted.v1` | 1 | 입찰 승인 producer | `auctionId`, `auctionVersion`, `bidderId`, `requestedPrice`, `bidPrice`, `currentPrice`, `bidCount`, `previousBidderId?`, `closeTime`, `auctionStatus`, `idempotencyKey`, `idempotencyRequestHash` | 경매·LEADING bid lock, 버전·가격·이전 입찰자 검증 → bid/auction/hold 반영 | 구현 |
| `auction.buy-now.v1` | 1 | 즉시낙찰 승인 producer | `bid.accepted.v1` payload + `auctionStatus=ENDED` | 입찰·경매 종료·hold capture·order 생성 | 구현 |
| `wallet.charged.v1` | 2 | 충전 producer | `eventId`, `userId`, `walletVersion`, `availableBalance`, `frozenBalance`, `transactionType=CHARGE`, `transactionAmount`, `idempotencyKey?` | wallet snapshot·point record 멱등 projection | 구현 |
| `wallet.refunded.v1` | 2 | 환불 producer | 지갑 v2 snapshot payload, `transactionType=REFUND` | wallet snapshot·point record 멱등 projection | 구현 |
| `wallet.settled.v1` | 2 | 구매 확정 producer | 지갑 v2 snapshot payload, `transactionType=ORDER_SETTLEMENT` | wallet snapshot·point record 멱등 projection | 구현 |
| `wallet.cancel-refunded.v1` | 2 | 주문 취소 producer | 지갑 v2 snapshot payload, `transactionType=ORDER_CANCEL_REFUND` | wallet snapshot·point record 멱등 projection | 구현 |
| `wallet.hold-created.v1` | - | 입찰 producer | - | wallet hold·snapshot projection | 예정 |
| `wallet.hold-released.v1` | - | 상회입찰/취소 producer | - | wallet hold·snapshot projection | 예정 |
| `wallet.hold-captured.v1` | - | 종료 producer | - | wallet hold·snapshot projection | 예정 |
| `order.cancelled.v1` | - | 주문 취소 producer | - | order 상태 변경·환불 후속 요청 | 예정 |
| `order.confirmed.v1` | - | 구매 확정 producer | - | order 상태 변경·정산 후속 요청 | 예정 |
| `notification.created.v1` | - | 도메인 projection | - | `notifications` 생성·SSE 발행 | 예정 |
| `notification.read.v1` | - | 알림 읽음 producer | - | notification read 상태 갱신 | 예정 |
| `user.status-changed.v1` | - | 회원 상태 producer | - | users 상태 갱신 | 예정 |

`imagePaths`는 여러 이미지를 줄바꿈(`\n`)으로 연결한 문자열이다. 지갑 v2 snapshot payload는 공통적으로
`eventId`, `userId`, `walletVersion`, `availableBalance`, `frozenBalance`, `transactionType`,
`transactionAmount`, `idempotencyKey?`를 사용한다.
