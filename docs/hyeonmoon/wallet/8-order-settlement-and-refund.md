# 주문 정산·환불 Wallet 연동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 구매확정 시 판매자 지갑에 낙찰가를 입금하고, 구매취소(구매자 취소·판매자 취소 공통) 시 구매자 지갑에 낙찰가를 환불하는 실제 Wallet 로직을 추가해, Order 도메인의 `WalletSettlementPort` mock 구현을 대체한다. 이슈 [#237](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/237), 조사 문서 [docs/hamin/order/1-purchase-confirm-cancel-plan.md](../../hamin/order/1-purchase-confirm-cancel-plan.md) 4절의 후속 작업이다.

**Architecture:** 낙찰 시점에 이미 `WalletService.capture()`가 구매자 hold를 `CAPTURED`로 종결하고 `wallet.point`를 실제로 차감한다([AuctionCommandService.java:474](../../../backend/src/main/java/com/dbidding/auction/service/AuctionCommandService.java)). 구매확정/취소 시점엔 그 hold가 이미 종결 상태라 `release()`/`capture()`로 다시 건드릴 수 없고 건드릴 필요도 없다. 대신 순수한 잔액 증가(credit) 오퍼레이션 2개를 새로 추가한다.

```text
구매확정        : 판매자 wallet.point += 낙찰가        (신규: WalletService.settle)
구매취소/판매거부: 구매자 wallet.point += 낙찰가        (신규: WalletService.cancelRefund)
```

두 메서드 모두 `WalletHold`/`HoldStatus`는 전혀 건드리지 않고, 대상 지갑 1개만 잠근 뒤 `charge()`와 같은 방식으로 잔액을 늘리고 `PointRecord`를 남긴다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, MySQL 8.4(Testcontainers), JUnit 5, Mockito

## 설계 검토 결과

- `Order` 행 락 뒤 대상 지갑 한 행만 잠그는 구조, `point_records.auction_id`에 경매 ID를
  저장하는 구조는 적절하다.
- 이번 문서의 금액 범위는 `Order.price`인 낙찰가다. 현재 Auction의 hold/capture가 배송비를 포함하지
  않는 것은 프로젝트 정책과 어긋나는 별도 결함이며, 스키마·이벤트 계약 확장이 필요한 후속 작업으로
  분리한다. 이번 변경은 그 기존 금액 계약을 확장하지 않는다.

### 정정: Port·Adapter 대신 직접 호출로 변경 (리뷰 반영)

Task 3을 처음 구현할 때는 `order.port.WalletSettlementPort` + `order.adapter.OrderWalletAdapter`로
감싸는 구조를 그대로 뒀는데, 리뷰에서 PR [#240](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/pull/240)
(`AuctionCommandService`가 `WalletPort`/`AuctionWalletAdapter`를 걷어내고 `WalletService`를 직접
호출하도록 단순화한 선례)과 같은 방향으로 가는 게 낫다는 지적을 받아 반영했다. `WalletSettlementPort`는
`auction-mock` 같은 대체 프로필이 필요 없는, Wallet을 부르기만 하는 인터페이스였어서 `WalletPort`와
같은 처지였다. `OrderService`가 `WalletService`를 직접 주입받아 `settle`/`cancelRefund`를 바로
부르는 걸로 바꿨고, 그 결과 `orderId → auctionId` 변환을 위해 어댑터가 따로 하던
`OrderRepository` 조회도 필요 없어졌다(`OrderService`가 이미 들고 있는 `Order.getAuctionId()`를
바로 넘기면 된다). 아래 Task 3 절은 이 변경을 반영해 다시 썼다.

또한 이 브랜치에서 한때 같이 커밋됐던 `OrderEventListener`(경매 종료 이벤트 구독으로 주문 생성)
관련 변경은 이 문서(#237, 정산·환불)의 범위가 아니라 이슈
[#232](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/232)(경매 종료 시
주문 생성을 이벤트 대신 같은 트랜잭션에서 직접 호출)의 범위라서 이 브랜치에서 뺐다. 필요하면 #232로
별도 처리한다.

## 락 순서와 데드락 검토

`OrderService`의 모든 상태 변경 메서드는 이미 다음 순서로 동작한다
([OrderService.java:44-79](../../../backend/src/main/java/com/dbidding/order/OrderService.java)).

```text
confirm()      → Order 행 락(findByIdForUpdate) → 지갑 1개(seller)만 credit
cancel()       → Order 행 락                     → 지갑 1개(buyer)만 credit
sellerCancel() → Order 행 락                     → 지갑 1개(buyer)만 credit
```

이전에 발견된 [입찰 지갑 락 순서 데드락](../auction/1-bid-wallet-lock-ordering-deadlock.md)은 **한 트랜잭션이 서로 다른 지갑 2개**를 매번 다른 순서로 잠가서 생긴 사이클이었다. 여기서는 한 트랜잭션이 잠그는 지갑이 항상 정확히 1개이고, 락 순서도 항상 `Order 행 → Wallet 행` 한 방향으로 고정돼 있어 같은 종류의 사이클이 구조적으로 발생하지 않는다. 같은 Order를 동시에 confirm/cancel하는 경쟁은 이미 Order 행 락으로 직렬화되고, 첫 트랜잭션 커밋 후 두 번째는 상태 가드(`InvalidOrderStatusException`)로 막힌다.

## 참조 컬럼 설계

`point_records.auction_id`는 `auctions(id)` FK가 걸려 있어 `orderId`를 그대로 넣을 수 없다. `orders.auction_id`가 `UNIQUE`(경매 1건당 주문 1건)이므로, `settle`/`cancelRefund`는 `orderId`가 아니라 **`auctionId`**를 참조로 받아 기존 `auction_id` 컬럼에 그대로 저장한다. 스키마 변경이 필요 없고 `auctionCapture()`와 동일한 패턴이다.

## Global Constraints

- `wallets`, `wallet_holds`, `point_records` 테이블 컬럼과 인덱스를 변경하지 않는다. 새 컬럼(`orders` 참조용)을 추가하지 않는다.
- `WalletHold`/`HoldStatus`와 hold/release/capture 로직은 변경하지 않는다 — 이번 작업은 이미 종결된 hold 이후의 별도 크레딧 경로다.
- 새 메서드(`settle`, `cancelRefund`)는 **한 호출당 지갑 1개만** 잠근다. 한 트랜잭션에서 지갑 2개를 잠그는 코드를 추가하지 않는다.
- Order 쪽에서 Wallet을 호출하는 순서(Order 행 락 → Wallet 행 락)를 뒤집지 않는다. Wallet 쪽에서 Order를 역참조하지 않는다.
- `Order.confirm()`/`cancel()`의 상태 가드가 이미 중복 정산·중복 환불을 막으므로, `settle`/`cancelRefund`에 별도 멱등키를 추가하지 않는다(`charge`/`refund`와 다르게 HTTP 재시도 경로가 아니라 서버 내부 호출이다).
- Wallet의 기존 HTTP API(`/api/wallet/**`)는 변경하지 않는다 — 이번 정산·환불은 Order 내부 호출로만 트리거된다.

---

### Task 1: PointTransactionType과 PointRecord 부호 규칙을 확장한다

**Files:**
- Modify: `backend/src/main/java/com/dbidding/wallet/domain/PointTransactionType.java`
- Modify: `backend/src/main/java/com/dbidding/wallet/domain/PointRecord.java`
- Modify: `backend/src/test/java/com/dbidding/wallet/domain/PointRecordTest.java`

**Interfaces:**
- Adds: `PointTransactionType.ORDER_SETTLEMENT`, `PointTransactionType.ORDER_CANCEL_REFUND`
- Modifies: `PointRecord`의 생성자 부호 규칙 — "`CHARGE`만 양수" → "`CHARGE`·`ORDER_SETTLEMENT`·`ORDER_CANCEL_REFUND`는 양수, 그 외(`REFUND`, `AUCTION_CAPTURE`)는 음수"
- Preserves: `PointRecord.charge/refund/auctionCapture` 기존 동작과 반환 부호

- [x] **Step 1: 새 타입의 부호를 검증하는 테스트를 추가한다**

`PointRecordTest`에 `PointRecord.orderSettlement(...)`와 `PointRecord.orderCancelRefund(...)`(Step 3에서 만들 정적 팩토리)가 양수 `amount`로 저장되는 케이스를 추가한다. 기존 `refund`/`auctionCapture`가 여전히 음수인 케이스도 회귀로 남긴다.

- [x] **Step 2: 테스트가 타입 부재로 실패하는지 확인한다**

```bash
cd backend
./gradlew test --tests com.dbidding.wallet.domain.PointRecordTest
```

- [x] **Step 3: enum과 부호 규칙, 정적 팩토리를 추가한다**

```java
public enum PointTransactionType {
    CHARGE,
    REFUND,
    AUCTION_CAPTURE,
    ORDER_SETTLEMENT,
    ORDER_CANCEL_REFUND
}
```

`PointRecord` 생성자의 부호 분기를 "양수 타입 집합"으로 일반화한다.

```java
private static final Set<PointTransactionType> POSITIVE_TYPES =
    EnumSet.of(PointTransactionType.CHARGE, PointTransactionType.ORDER_SETTLEMENT, PointTransactionType.ORDER_CANCEL_REFUND);
...
this.amount = POSITIVE_TYPES.contains(transactionType) ? amount : -amount;
```

`PointRecord.orderSettlement(Integer walletId, Integer auctionId, long amount, long balance)`,
`PointRecord.orderCancelRefund(Integer walletId, Integer auctionId, long amount, long balance)`를
`auctionCapture(...)`와 같은 형태(`auctionId` 필수, `idempotencyKey`는 `null`)로 추가한다.

- [x] **Step 4: 테스트를 통과시키고 커밋한다**

```bash
./gradlew test --tests com.dbidding.wallet.domain.PointRecordTest
git add backend/src/main/java/com/dbidding/wallet/domain/PointTransactionType.java \
  backend/src/main/java/com/dbidding/wallet/domain/PointRecord.java \
  backend/src/test/java/com/dbidding/wallet/domain/PointRecordTest.java
git commit -m "feat: 주문 정산·환불 PointTransactionType과 부호 규칙 추가"
```

### Task 2: WalletService에 settle·cancelRefund를 추가한다

**Files:**
- Modify: `backend/src/main/java/com/dbidding/wallet/service/WalletService.java`
- Create: `backend/src/test/java/com/dbidding/wallet/service/WalletServiceSettleTest.java`
- Create: `backend/src/test/java/com/dbidding/wallet/service/WalletServiceCancelRefundTest.java`

**Interfaces:**
- Produces: `WalletTransactionResponse WalletService.settle(Integer sellerId, Integer auctionId, long amount)`
- Produces: `WalletTransactionResponse WalletService.cancelRefund(Integer buyerId, Integer auctionId, long amount)`
- Consumes: `WalletRepository.findByUserIdForUpdate`, `Wallet.credit`, `PointRecordRepository.save`
- Preserves: 기존 `hold`/`release`/`capture`/`charge`/`refund` 시그니처와 동작

- [x] **Step 1: 실패하는 단위 테스트를 먼저 작성한다**

`WalletServiceSettleTest`: 존재하는 판매자 지갑에 `settle(sellerId, auctionId, amount)` 호출 시
`wallet.point`가 `amount`만큼 증가하고, `PointRecord`가 `ORDER_SETTLEMENT`·양수 `amount`로
저장되는지 검증. 지갑이 없으면 `WalletNotFoundException`.

`WalletServiceCancelRefundTest`: 동일한 구조로 `cancelRefund(buyerId, auctionId, amount)` 검증,
타입은 `ORDER_CANCEL_REFUND`.

두 테스트 모두 `amount <= 0`이면 `InvalidWalletAmountException`(기존 `validatePositive` 재사용)을
검증한다.

- [x] **Step 2: 테스트가 메서드 부재로 실패하는지 확인한다**

```bash
cd backend
./gradlew test \
  --tests com.dbidding.wallet.service.WalletServiceSettleTest \
  --tests com.dbidding.wallet.service.WalletServiceCancelRefundTest
```

- [x] **Step 3: settle·cancelRefund를 구현한다**

`charge()`와 같은 모양으로, hold 테이블은 건드리지 않는다.

```java
@Transactional(propagation = Propagation.MANDATORY)
public WalletTransactionResponse settle(Integer sellerId, Integer auctionId, long amount) {
    validatePositive(amount);
    Wallet wallet = lockWallet(sellerId);
    wallet.credit(amount);
    PointRecord record = pointRecordRepository.save(
        PointRecord.orderSettlement(wallet.getId(), auctionId, amount, wallet.getPoint())
    );
    return WalletTransactionResponse.from(record);
}

@Transactional(propagation = Propagation.MANDATORY)
public WalletTransactionResponse cancelRefund(Integer buyerId, Integer auctionId, long amount) {
    validatePositive(amount);
    Wallet wallet = lockWallet(buyerId);
    wallet.credit(amount);
    PointRecord record = pointRecordRepository.save(
        PointRecord.orderCancelRefund(wallet.getId(), auctionId, amount, wallet.getPoint())
    );
    return WalletTransactionResponse.from(record);
}
```

`Propagation.MANDATORY`로 선언해 `OrderService`의 트랜잭션 안에서만 호출되도록 강제한다(기존
`hold`/`release`/`capture`와 동일한 관례 — 이 메서드들이 독자적으로 새 트랜잭션을 열지 않고
호출자의 트랜잭션에 반드시 참여해야, Order 상태 전이와 지갑 크레딧이 원자적으로 묶인다).

- [x] **Step 4: 테스트를 통과시키고 커밋한다**

```bash
./gradlew test \
  --tests com.dbidding.wallet.service.WalletServiceSettleTest \
  --tests com.dbidding.wallet.service.WalletServiceCancelRefundTest
git add backend/src/main/java/com/dbidding/wallet/service/WalletService.java \
  backend/src/test/java/com/dbidding/wallet/service/WalletServiceSettleTest.java \
  backend/src/test/java/com/dbidding/wallet/service/WalletServiceCancelRefundTest.java
git commit -m "feat: Wallet에 주문 정산·환불 메서드 추가"
```

### Task 3: OrderService가 WalletService를 직접 호출하도록 정리한다

**Files:**
- Delete: `backend/src/main/java/com/dbidding/order/port/WalletSettlementPort.java`
- Delete: `backend/src/main/java/com/dbidding/order/adapter/MockWalletSettlementAdapter.java`
- Delete: `backend/src/test/java/com/dbidding/order/adapter/MockWalletSettlementAdapterTest.java`
- Modify: `backend/src/main/java/com/dbidding/order/OrderService.java`
- Modify: `backend/src/test/java/com/dbidding/order/OrderServiceTest.java`

**Interfaces:**
- Removes: `WalletSettlementPort`, `MockWalletSettlementAdapter`와 그 기록(record) 조회 API
- Modifies: `OrderService`의 필드를 `WalletSettlementPort walletSettlementPort` →
  `WalletService walletService`로 교체
- Consumes: `WalletService.settle(Integer sellerId, Integer auctionId, long amount)`,
  `WalletService.cancelRefund(Integer buyerId, Integer auctionId, long amount)`

PR [#240](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/pull/240)에서
`AuctionCommandService`가 `WalletPort`/`AuctionWalletAdapter`를 걷어내고 `WalletService`를
직접 주입받은 것과 같은 모양이다. `WalletSettlementPort`는 `auction-mock` 같은 대체 프로필이
필요 없어서 Port·Adapter로 감쌀 이유가 없었다.

- [x] **Step 1: OrderServiceTest를 WalletService 목으로 바꾼다**

`@Mock private WalletSettlementPort walletSettlementPort`를
`@Mock private WalletService walletService`로 바꾸고, `verify(walletSettlementPort).payoutToSeller(sellerId, order.getId(), price)`류 검증을
`verify(walletService).settle(sellerId, AUCTION_ID, price)`/`verify(walletService).cancelRefund(buyerId, AUCTION_ID, price)`로
바꾼다(두 번째 인자가 `orderId`에서 `auctionId`로 바뀐다).

- [x] **Step 2: 테스트가 컴파일 실패로 막히는지 확인한다**

```bash
cd backend
./gradlew compileTestJava
```

Expected: `OrderService` 생성자가 아직 `WalletSettlementPort`를 받아서 타입 불일치로 컴파일 실패.

- [x] **Step 3: OrderService가 WalletService를 직접 호출하도록 바꾸고 Port·Adapter를 삭제한다**

```java
private final OrderRepository orderRepository;
private final WalletService walletService;
private final OrderEventPort orderEventPort;

// confirm()
order.confirm();
walletService.settle(order.getSellerId(), order.getAuctionId(), order.getPrice());

// cancel(Order, CancelledBy)
order.cancel();
walletService.cancelRefund(order.getBuyerId(), order.getAuctionId(), order.getPrice());
```

`order.getAuctionId()`를 바로 쓰므로 `orderId → auctionId` 변환용 조회가 필요 없다.
`WalletSettlementPort`, `MockWalletSettlementAdapter`, `MockWalletSettlementAdapterTest`를 삭제한다.

- [x] **Step 4: Order·Wallet 관련 테스트를 재실행한다**

```bash
./gradlew test \
  --tests 'com.dbidding.order.OrderServiceTest' \
  --tests 'com.dbidding.wallet.service.WalletServiceSettleTest' \
  --tests 'com.dbidding.wallet.service.WalletServiceCancelRefundTest'
```

- [x] **Step 5: 변경을 커밋한다**

```bash
git add backend/src/main/java/com/dbidding/order/OrderService.java \
  backend/src/test/java/com/dbidding/order/OrderServiceTest.java
git rm backend/src/main/java/com/dbidding/order/port/WalletSettlementPort.java \
  backend/src/main/java/com/dbidding/order/adapter/MockWalletSettlementAdapter.java \
  backend/src/test/java/com/dbidding/order/adapter/MockWalletSettlementAdapterTest.java
git commit -m "refactor: 주문 지갑 정산을 Port·Adapter 없이 직접 호출로 교체"
```

### Task 4: 동시성 회귀 테스트와 전체 검증

**Files:**
- Create: `backend/src/test/java/com/dbidding/order/OrderWalletSettlementConcurrencyTest.java`
- Modify: `docs/hyeonmoon/wallet/README.md`

- [x] **Step 1: 확정·취소 동시 요청 동시성 테스트를 작성한다**

실제 MySQL Testcontainer로 다음을 검증한다.

- 같은 주문에 대해 구매확정과 판매자취소(또는 구매자취소)가 동시에 들어와도 정산·환불이
  중복되지 않는다(하나는 성공, 하나는 `InvalidOrderStatusException`).
- 서로 다른 주문 두 건을 같은 판매자가 동시에 정산받아도 두 크레딧이 모두 반영되고, 지갑
  락 대기로 인한 예외(`CannotAcquireLockException`류)가 발생하지 않는다(직렬화되지만
  실패하지 않아야 한다는 뜻 — Task 시작부 "락 순서와 데드락 검토" 절 참고).

테스트는 추가했다. 2026-08-08 현재 Docker 데몬이 중지되어 Testcontainers가 두 케이스를
skip했으므로 실제 MySQL 동시성 실행 결과는 Docker 기동 후 재검증이 필요하다.

- [x] **Step 2: 전체 백엔드 테스트를 실행한다**

```bash
cd backend
./gradlew clean test
```

관련 단위 테스트는 통과했다. 2026-08-08 `./gradlew clean test`는 456개 중 26개 실패,
19개 skip였다. 실패는 Docker Testcontainers 탐색 실패 또는 외부 DB 대체를 비활성화한
리포지토리 테스트의 JDBC URL 누락에서 발생했다. Docker 데몬을 기동한 뒤 전체 회귀와
동시성 테스트를 다시 실행해야 한다.

- [x] **Step 3: Wallet README 구현 단계 목록을 갱신한다**

`docs/hyeonmoon/wallet/README.md`의 구현 단계 목록에 이번 문서를 추가한다.

- [x] **Step 4: 문서 정리를 커밋한다**

```bash
git add docs/hyeonmoon/wallet/README.md backend/src/test/java/com/dbidding/order/OrderWalletSettlementConcurrencyTest.java
git commit -m "docs: 주문 정산·환불 Wallet 연동 문서 반영"
```

## 완료 조건

- 구매확정 시 판매자 `wallet.point`가 낙찰가만큼 실제로 증가한다.
- 구매취소·판매자취소 시 구매자 `wallet.point`가 낙찰가만큼 실제로 증가한다.
- `WalletHold`/`HoldStatus`와 `wallet_holds` 테이블은 이번 작업으로 변경되지 않는다.
- `point_records`에 새 컬럼 없이 기존 `auction_id`로 정산·환불 내역을 추적할 수 있다.
- `OrderService`가 `WalletService`의 public 메서드만 호출하고 Wallet의 Repository·Entity를
  직접 참조하지 않는다(PR #240과 같은 방식 — Port·Adapter로 감싸지 않는다).
- 정산·환불 경로에서 한 트랜잭션이 지갑을 2개 이상 잠그지 않는다.
- 전체 백엔드 테스트가 실패 없이 통과한다.

> 이 문서는 AI의 도움을 받아 작성하였습니다
