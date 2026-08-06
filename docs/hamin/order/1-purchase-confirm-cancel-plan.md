# Order 1단계 구현 계획 — 구매확정/구매취소 (수수료 없음, 실제 정산 제외)

담당: 임하민. 이슈: [#212](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/212).
배경 설계: `notes/order-domain-design.md` (팀 리뷰용 설계 문서, 배송/반품은 2단계로 이연).

`AGENTS.md`상 원래 담당 범위는 `notifications`/`psa`/`upload`/`wishlist`인데, 이번 `order`
도메인 작업은 팀 논의를 거쳐 명시적으로 배정받았다.

**이번 라운드는 실제로 돈이 오가는 처리(지갑 잔액 변경)는 구현하지 않는다.** `WalletSettlementPort`
인터페이스만 정의하고, 구현체는 아무 동작도 하지 않는 스텁으로 둔다. `wallet` 패키지 변경이 필요한
실제 정산/환불 로직은 wallet 담당(김현문)과 협의 후 별도 후속 작업으로 진행한다(4절 참고). 그 외
Order 엔티티/API/이벤트/알림 연동/프론트엔드는 전부 이번 라운드에서 구현한다.

## 범위

낙찰 후 **구매확정**과 **구매취소** 두 액션의 상태 전이·API·이벤트·알림·프론트엔드 연동까지를
전부 구현하되, 그 트리거로 호출되는 지갑 정산/환불 자체는 스텁으로 대체한다. 배송지 확인/발송/
배송조회/수령확인/반품/수수료/자동확정 스케줄러는 전부 범위 밖(2단계)이다.

## 1. 스키마 (`schema.sql`)

```sql
CREATE TABLE orders (
    id          INT          NOT NULL AUTO_INCREMENT,
    auction_id  INT          NOT NULL,
    buyer_id    INT          NOT NULL,
    seller_id   INT          NOT NULL,
    price       BIGINT       NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_auction UNIQUE (auction_id),
    CONSTRAINT fk_orders_auction FOREIGN KEY (auction_id) REFERENCES auctions (id),
    CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT fk_orders_seller FOREIGN KEY (seller_id) REFERENCES users (id),

    INDEX idx_orders_buyer_status (buyer_id, status),
    INDEX idx_orders_seller_status (seller_id, status)
);
```

- PK는 `Integer`(`erd-review.md`/`package-structure.md` 정책 — 고빈도 append-only 테이블 아님).
- `uk_orders_auction`: 경매 1건당 주문 1건만 생성되게 DB 레벨로 강제(이벤트 리스너가 중복
  실행되더라도 두 번째 insert는 제약 위반으로 막힘 — notification의 유니크 제약과 같은 목적).

## 2. `order` 패키지 — 새로 만들 파일

`backend/src/main/java/com/dbidding/order/`

- `Order.java` — `id`(Integer), `auctionId`(Integer), `buyerId`(Integer), `sellerId`(Integer),
  `price`(long), `status`(`OrderStatus`), `createdAt`. `@Getter`, `@NoArgsConstructor(access = PROTECTED)`
  + 정적 팩토리 `Order.pendingConfirm(auctionId, buyerId, sellerId, price)`. 도메인 메서드
  `confirm()`/`cancel()`이 상태 전이 + 가드(이미 `COMPLETED`/`CANCELLED`면 `InvalidOrderStatusException`)를 담당.
- `OrderStatus.java` — enum `PENDING_CONFIRM`, `COMPLETED`, `CANCELLED`.
- `OrderRepository.java` — `JpaRepository<Order, Integer>` + `findByAuctionId(Integer auctionId)`
  (이벤트 리스너 중복 실행 시 존재 확인용), `findByBuyerIdOrderByIdDesc(Integer buyerId)`,
  `findBySellerIdOrderByIdDesc(Integer sellerId)`.
- `OrderService.java` — `confirm(Integer orderId, Integer currentUserId)`,
  `cancel(Integer orderId, Integer currentUserId)`, `findAllForBuyer(Integer buyerId)`,
  `findAllForSeller(Integer sellerId)`, `findOne(Integer orderId, Integer currentUserId)`.
  구매자 본인 소유 주문인지 확인(`order.getBuyerId().equals(currentUserId)` 아니면
  `OrderAccessDeniedException`) 후 `WalletSettlementPort` 호출(스텁, 아래 3절) → 상태 전이 →
  `OrderCompletedEvent`/`OrderCancelledEvent` 발행.
- `OrderController.java`
  - `GET /api/orders?role=buyer|seller&status=` — `role` 파라미터로 구매/판매 목록 분기.
  - `GET /api/orders/{orderId}` — 상세.
  - `POST /api/orders/{orderId}/confirm`
  - `POST /api/orders/{orderId}/cancel`
  - 전부 `@CurrentUser Integer userId` 사용 (`global.security` 기존 인프라 그대로).
- `dto/OrderResponse.java` — `{ id, auctionId, price, status, createdAt }` (목록/상세 공용).
- `event/OrderCompletedEvent.java`, `OrderCancelledEvent.java` — order가 소유하는 이벤트 계약.
  참조 필드(`orderId`, `auctionId`, `buyerId`, `sellerId`)는 전부 `Integer`.
- `OrderEventListener.java` — `auction.event.AuctionClosedEvent` 구독 (auction 패키지의
  Entity/Repository는 참조하지 않고 이벤트 레코드만 사용 — 이미 `winnerId`/`sellerId`/`winningPrice`
  필드가 있어 바로 씀). `@Async @TransactionalEventListener(phase = AFTER_COMMIT)`,
  `event.winnerId() == null`이면 무시(유찰), `orderRepository.findByAuctionId(...)`로 이미
  생성됐는지 확인 후 없으면 `Order.pendingConfirm(...)` 저장.
- `exception/OrderNotFoundException.java`(404), `OrderAccessDeniedException.java`(403),
  `InvalidOrderStatusException.java`(409) — 기존 `wallet.exception` 패턴(`@ResponseStatus` +
  `RuntimeException`) 그대로.

## 3. `WalletSettlementPort` — 이번 라운드는 인터페이스 + 스텁까지만

```java
// order/port/WalletSettlementPort.java — order 소유
public interface WalletSettlementPort {
    void payoutToSeller(Integer sellerId, Integer orderId, long amount);
    void refundToBuyer(Integer buyerId, Integer orderId, long amount);
}
```

- `order/adapter/NoOpWalletSettlementAdapter.java` — `@Component`로 등록되는 유일한 구현체.
  실제 지갑 잔액은 건드리지 않고, 나중에 실제 구현으로 교체될 자리라는 걸 알 수 있게 로그만 남긴다
  (`module-interfaces.md`가 제시하는 "임시 stub → 실제 구현으로 교체" 패턴 그대로, 다만 이번엔
  대체할 실제 구현 시점이 별도 협의 이후라 `@Profile` 분기 없이 이 하나만 둔다).
- `OrderService`는 이 포트를 통해서만 정산/환불을 "호출"하고, 실제 잔액 이동은 이번 이슈에서
  다루지 않는다 — `Order`의 상태 전이(`COMPLETED`/`CANCELLED`)와 이벤트 발행은 정상 동작하지만,
  지갑 잔액은 실제로 변하지 않는다는 뜻이다.

## 4. (범위 밖, 후속) 실제 wallet 정산/환불 구현 — wallet 담당과 협의 필요

아래는 나중에 스텁을 실제 구현으로 교체할 때 참고할 조사 내용이다. **이번 이슈에서는 착수하지 않는다.**

- `capture(userId, auctionId, amount)`는 구매자 지갑을 `debit`만 한다 — 캡처된 금액이 판매자나
  다른 어디로도 안 들어가고 사라진다.
- `refund(userId, amount, idempotencyKey)`는 이름과 달리 `debit`을 한다(충전한 포인트를 외부
  결제수단으로 출금하는 의미로 보임) — 우리가 원하는 "취소 시 잔액 증가"와 방향이 반대라
  재사용 불가.
- `PointRecord` 생성자가 `transactionType == CHARGE`일 때만 금액을 양수로 저장하고 나머지는
  전부 음수로 저장한다 — 판매자 정산/구매취소 환불처럼 "CHARGE가 아닌데 잔액이 증가하는" 케이스를
  표현하려면 이 부호 규칙도 손봐야 한다.
- 필요한 변경(추후, wallet 담당과 협의): `PointTransactionType`에 `ORDER_SETTLEMENT`/
  `ORDER_CANCEL_REFUND` 추가, `PointRecord` 부호 규칙 일반화, `WalletService`에 `settle`/
  `cancelRefund` 메서드 추가, `order/adapter/NoOpWalletSettlementAdapter`를 실제
  `OrderWalletAdapter`로 교체.

## 5. `notification` 연동 (기존 담당 영역이라 바로 가능)

- `NotificationType`에 `ORDER_COMPLETED`, `ORDER_CANCELLED` 추가.
- `NotificationEventListener`에 `handleOrderCompleted(OrderCompletedEvent)`,
  `handleOrderCancelled(OrderCancelledEvent)` 추가 — 구매자/판매자 양쪽에 알림 저장 + SSE push
  (기존 `handleAuctionClosed`와 동일 패턴).

## 6. 프론트엔드

- 대시보드 "최근 나의 낙찰" 카드의 액션을 "배송 신청하기" 대신 **"구매확정"/"구매취소"** 버튼으로 교체.
- `frontend/src/api/`에 `confirmOrder(orderId)`/`cancelOrder(orderId)` 추가, 클릭 시 호출 후
  목록 갱신.

## 7. 테스트

- `OrderServiceTest` — 정상 확정/취소, 타인 주문 확정 시도(403), 이미 확정된 주문 재확정 시도(409).
- `OrderControllerTest` — 슬라이스 테스트, `role=buyer|seller` 분기.
- `OrderEventListenerTest` — `AuctionClosedEvent(winnerId=null)` 무시, 중복 이벤트 시 `uk_orders_auction`
  제약으로 재생성 안 됨.
- `NotificationEventListener`에 추가되는 두 핸들러도 기존 테스트 패턴에 맞춰 케이스 추가.

## 커밋 단위 (예정)

1. `feat: orders 테이블 및 Order 엔티티/레포지토리 추가`
2. `feat: AuctionClosedEvent 구독해 Order 생성하는 리스너 구현`
3. `feat: 구매확정/구매취소 API 및 WalletSettlementPort 스텁 구현`
4. `feat: order 완료/취소 알림 연동`
5. `feat: 대시보드 구매확정/구매취소 버튼 연동`
6. `test: Order 서비스/컨트롤러/이벤트리스너 단위 테스트 작성`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
