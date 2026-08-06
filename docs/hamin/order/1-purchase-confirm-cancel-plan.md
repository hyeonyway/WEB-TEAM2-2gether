# Order 1단계 구현 계획 — 구매확정/구매취소 (수수료 없음, 실제 정산 제외)

담당: 임하민. 이슈: [#212](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/212)
(브랜치 `feature/212-order-confirm-cancel`). 배경 설계: `notes/order-domain-design.md`
(팀 리뷰용 설계 문서, 배송/반품은 2단계로 이연).

**이 문서의 1~7절은 실제 구현 완료 상태를 반영한 것이다** — 아래 각 절에 계획 대비 실제
달라진 부분을 표시해뒀다.

`AGENTS.md`상 원래 담당 범위는 `notifications`/`psa`/`upload`/`wishlist`인데, 이번 `order`
도메인 작업은 팀 논의를 거쳐 명시적으로 배정받았다.

**이번 라운드는 실제로 돈이 오가는 처리(지갑 잔액 변경)는 구현하지 않는다.** `WalletSettlementPort`
인터페이스만 정의하고, 구현체(`MockWalletSettlementAdapter`)는 실제 잔액을 건드리지 않고 호출
사실만 메모리에 기록한다. `wallet` 패키지 변경이 필요한 실제 정산/환불 로직은 wallet 담당(김현문)과
협의 후 별도 후속 작업으로 진행한다(4절 참고). 그 외 Order 엔티티/API/이벤트/알림 연동/프론트엔드는
전부 이번 라운드에서 구현했다.

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
  `OrderAccessDeniedException`) 후 `WalletSettlementPort` 호출(mock, 아래 3절) → 상태 전이 →
  `OrderEventPort`로 `OrderCompletedEvent`/`OrderCancelledEvent` 발행(아래 3-1절).
  **(계획 대비 추가)** `createFromAuctionClosed(AuctionClosedEvent event)`도 여기서 담당한다 —
  낙찰자가 있으면 `Order.pendingConfirm(...)` 저장, 없으면 무시, 중복 저장 시
  `DataIntegrityViolationException`은 로그만 남기고 삼킴. Spring 이벤트 애노테이션이 전혀 없는
  평범한 메서드로 둬서 구독 방식이 바뀌어도 이 로직/테스트는 그대로 재사용 가능하게 했다.
- `OrderController.java` — **(계획 대비 변경)** `?role=buyer|seller` 쿼리 파라미터 대신,
  기존 `DashboardController`(`/participating-auctions`, `/recent-wins`) 컨벤션에 맞춰 경로를
  분리했다.
  - `GET /api/orders/purchases` — 내 구매 목록
  - `GET /api/orders/sales` — 내 판매 목록
  - `GET /api/orders/{orderId}` — 상세
  - `POST /api/orders/{orderId}/confirm`
  - `POST /api/orders/{orderId}/cancel`
  - 전부 `@CurrentUser Integer userId` 사용 (`global.security` 기존 인프라 그대로).
- `dto/OrderResponse.java` — `{ id, auction_id, price, status, created_at }`(스네이크케이스
  `@JsonProperty`, `DashboardResponse` 컨벤션과 동일) — 목록/상세 공용.
- `event/OrderCompletedEvent.java`, `OrderCancelledEvent.java` — order가 소유하는 이벤트 계약.
  참조 필드(`orderId`, `auctionId`, `buyerId`, `sellerId`)는 전부 `Integer`.
- `OrderEventListener.java` — **(계획 대비 변경)** 실제 생성 로직은 `OrderService.createFromAuctionClosed`로
  옮겼고, 이 클래스는 `auction.event.AuctionClosedEvent`를 구독해 그 메서드를 호출만 하는 얇은
  어댑터로 남았다(`@Async @TransactionalEventListener(phase = AFTER_COMMIT)`). auction 패키지의
  Entity/Repository는 참조하지 않고 이벤트 레코드만 사용 — 이미 `winnerId`/`sellerId`/`winningPrice`
  필드가 있어 auction 쪽에 추가 요청 없이 바로 썼다.
- `exception/OrderNotFoundException.java`(404), `OrderAccessDeniedException.java`(403),
  `InvalidOrderStatusException.java`(409) — 기존 `wallet.exception` 패턴(`@ResponseStatus` +
  `RuntimeException`) 그대로.

## 3. `WalletSettlementPort` — 이번 라운드는 인터페이스 + mock 어댑터까지만

```java
// order/port/WalletSettlementPort.java — order 소유
public interface WalletSettlementPort {
    void payoutToSeller(Integer sellerId, Integer orderId, long amount);
    void refundToBuyer(Integer buyerId, Integer orderId, long amount);
}
```

- `order/adapter/MockWalletSettlementAdapter.java` — **(계획 대비 변경, `NoOpWalletSettlementAdapter`에서
  개명)** `@Component`로 등록되는 유일한 구현체. 실제 지갑 잔액(`wallets.point`)은 건드리지 않고,
  호출 사실(`Action.PAYOUT_TO_SELLER`/`REFUND_TO_BUYER`, 대상 유저, 주문 id, 금액)을 메모리
  리스트에 기록해 `getRecords()`로 조회할 수 있게 했다 — 구매확정/구매취소 흐름(상태 전이,
  이벤트 발행) 자체를 테스트/확인하기 위한 목적. `MockWalletSettlementAdapterTest`로 기록
  동작을 검증한다.
- `OrderService`는 이 포트를 통해서만 정산/환불을 "호출"하고, 실제 잔액 이동은 이번 이슈에서
  다루지 않는다 — `Order`의 상태 전이(`COMPLETED`/`CANCELLED`)와 이벤트 발행은 정상 동작하지만,
  지갑 잔액은 실제로 변하지 않는다는 뜻이다.

## 3-1. `OrderEventPort` — **(계획에 없었음, 추가)** 이벤트 발행도 port-adapter로 분리

처음엔 `OrderService`가 `ApplicationEventPublisher`를 직접 썼는데, 기존 `auction`의
`AuctionEventPort`/`SpringAuctionEventPublisher`와 같은 패턴으로 분리했다.

```java
// order/port/OrderEventPort.java — order 소유
public interface OrderEventPort {
    void publishCompleted(OrderCompletedEvent event);
    void publishCancelled(OrderCancelledEvent event);
}
```

- `order/adapter/SpringOrderEventAdapter.java` — `ApplicationEventPublisher`로 실제 발행하는
  유일한 구현체. `OrderService`는 이제 Spring 이벤트 API를 몰라도 된다.

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
  `cancelRefund` 메서드 추가, `order/adapter/MockWalletSettlementAdapter`를 실제
  `OrderWalletAdapter`로 교체(Repository/Entity 직접 접근 없이 `WalletService`의 신규
  public 메서드만 호출하는 `AuctionWalletAdapter`와 같은 형태로).

## 5. `notification` 연동 (기존 담당 영역이라 바로 가능)

- `NotificationType`에 `ORDER_COMPLETED`, `ORDER_CANCELLED` 추가.
- `NotificationEventListener`에 `handleOrderCompleted(OrderCompletedEvent)`,
  `handleOrderCancelled(OrderCancelledEvent)` 추가 — 구매자/판매자 양쪽에 알림 저장 + SSE push
  (기존 `handleAuctionClosed`와 동일 패턴).

## 6. 프론트엔드

**(계획 대비 변경)** 처음엔 "최근 나의 낙찰" 카드 위에 버튼을 얹는 방식으로 시작했는데,
검토 후 대시보드에 **독립된 "주문" 탭**을 새로 파는 구조로 바꿨다.

- `sections`에 `orders` 탭 추가 — "참여 중인 경매"/"최근 나의 낙찰" 옆.
- `pages/dashboard/components/OrdersPanel.tsx`(신규) — 이 탭 전용 컴포넌트.
  - 서브탭: 내가 산 주문 / 내가 판 주문 (`role=buyer|seller`, `orderQueries.purchases()`/`sales()`)
  - 상태 필터: 전체/확정 대기/거래 완료/거래 취소 (클라이언트 사이드 필터 — 백엔드에 별도
    쿼리 파라미터 없음, 주문 건수 자체가 적어서 전체를 받아 필터링)
  - 정렬은 항상 최신순 — 백엔드가 이미 `id desc`로 내려주므로 프론트에 정렬 UI를 따로 두지 않았다.
  - 구매 목록의 `PENDING_CONFIRM` 주문에만 구매확정/구매취소 버튼 노출.
- `frontend/src/api/orderApi.ts` — `fetchPurchaseOrders`/`fetchSalesOrders`/`confirmOrder`/`cancelOrder`.
- `frontend/src/queries/orderQueries.ts` — `purchases()`/`sales()` queryOptions.
- **지갑 잔액 동기화**: 구매확정/구매취소는 구매자 본인만 호출 가능한 직접 액션이라(다른 유저
  액션으로 잔액이 바뀌는 경우가 아님), `AuctionBidDialog`의 입찰 성공 처리와 동일하게 뮤테이션
  `onSuccess`에서 `walletQueryKeys.balance()`를 invalidate한다. (실시간 SSE 기반 동기화인
  `useAuctionWalletSync`류 패턴은 "내가 안 눌러도 잔액이 바뀌는" 경우를 위한 거라 여기엔
  해당 없음 — order에는 아직 그런 스트림이 없다.)
- `AuctionCatalog`는 원래 형태(확장 없이) 그대로 유지.

## 7. 테스트

- `OrderServiceTest` — 정상 확정/취소, 타인 주문 확정 시도(403), 이미 확정된 주문 재확정 시도(409),
  경매 종료 이벤트로부터 주문 생성(`createFromAuctionClosed` — 낙찰자 있음/없음/중복 이벤트).
- `OrderControllerTest` — 슬라이스 테스트, `/purchases`/`/sales`/`/{orderId}`/`/confirm`/`/cancel`.
- `OrderEventListenerTest` — `OrderService.createFromAuctionClosed`로 그대로 위임하는지만 검증
  (실제 생성 로직 테스트는 `OrderServiceTest`로 이동).
- `MockWalletSettlementAdapterTest` — 호출 시 기록이 정확히 쌓이는지 검증.
- `NotificationEventListener`에 추가된 두 핸들러도 기존 테스트 패턴에 맞춰 케이스 추가.

## 실제 커밋 이력

1. `feat: orders 테이블 및 Order 엔티티/레포지토리 추가`
2. `feat: AuctionClosedEvent 구독해 Order 생성하는 리스너 구현`
3. `feat: 구매확정/구매취소 API 및 WalletSettlementPort 스텁 구현`
4. `feat: order 완료/취소 알림 연동`
5. `feat: 대시보드 구매확정/구매취소 버튼 연동`
6. `refactor: 대시보드에 독립된 주문 탭 추가`
7. `refactor: 이벤트 발행/구독을 port-adapter로 분리`
8. `feat: WalletSettlementPort mock 어댑터로 교체`
9. `feat: 구매확정/구매취소 후 지갑 잔액 재조회`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
