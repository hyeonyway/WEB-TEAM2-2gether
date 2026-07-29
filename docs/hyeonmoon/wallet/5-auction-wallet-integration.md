# Auction Wallet Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auction의 입찰·상회 입찰·낙찰 처리와 Wallet의 홀드·해제·실제 차감을 하나의 DB 트랜잭션으로 처리한다.

**Architecture:** 사용하는 쪽인 Auction이 이미 소유한 `auction.port.WalletPort`를 유지하고 Wallet이 실제 adapter를 구현한다. 공개 Port에는 락 획득 메서드를 노출하지 않으며, 각 변경 메서드가 내부에서 wallet row를 `PESSIMISTIC_WRITE`로 잠근다. 입찰과 자금 변경은 동기 호출로 묶고 SSE·알림 이벤트만 커밋 이후 처리한다.

**Tech Stack:** Spring DI, Spring Transaction, Spring Data JPA, MySQL 8.4, JUnit 5, Mockito

## Global Constraints

- 새 `bid.port.WalletGateway`를 만들지 않고 현재 `auction.port.WalletPort`를 사용한다.
- Wallet adapter는 Auction/Bid Entity나 Repository를 import하지 않는다.
- Auction이 전달하는 hold 금액은 `입찰가 + 배송비`의 합계다.
- `holdBidAmount()`는 `wallets.point`와 `point_records`를 변경하지 않는다.
- 같은 사용자가 같은 경매에서 연속 재입찰하면 활성 hold를 새 총액으로 올리고 증가분만 추가 검증한다.
- 한번 RELEASED된 뒤 같은 경매에 재진입하면 이전 row를 되살리지 않고 새 HELD row를 만든다.
- `releaseBidHold()`는 `HELD → RELEASED`, `confirmWinningBid()`는 `HELD → CAPTURED`로만 전이한다.
- RELEASED와 CAPTURED row는 hard delete하지 않는다.
- 낙찰 확정 시에만 `wallets.point`를 차감하고 `AUCTION_CAPTURE` 원장을 한 줄 기록한다.
- WalletPort 변경 메서드는 AuctionService가 연 트랜잭션에 참여하며 `REQUIRES_NEW`를 사용하지 않는다.
- 입찰·낙찰 결과를 외부로 전달하는 이벤트 리스너는 DB 커밋 이후 실행한다.

---

### Task 1: WalletHold 상태 모델과 Repository

**Files:**
- Create: `backend/src/main/java/com/dbidding/wallet/domain/HoldStatus.java`
- Create: `backend/src/main/java/com/dbidding/wallet/domain/WalletHold.java`
- Create: `backend/src/main/java/com/dbidding/wallet/exception/InvalidWalletHoldStateException.java`
- Create: `backend/src/main/java/com/dbidding/wallet/repository/WalletHoldRepository.java`
- Test: `backend/src/test/java/com/dbidding/wallet/domain/WalletHoldTest.java`
- Test: `backend/src/test/java/com/dbidding/wallet/repository/WalletHoldRepositoryTest.java`

**Interfaces:**
- Produces: `WalletHold.held(Integer walletId, Integer auctionId, long amount)`
- Produces: `void WalletHold.increaseTo(long newAmount)`
- Produces: `void WalletHold.release(Instant now)`
- Produces: `void WalletHold.capture(Instant now)`
- Produces: `Optional<WalletHold> WalletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(Integer walletId, Integer auctionId)`

- [ ] **Step 1: 상태 전이 테스트를 작성한다**

```java
@Test
void HELD는_금액을_늘리고_RELEASED나_CAPTURED로_한번만_전이한다() {
    WalletHold hold = WalletHold.held(1, 20, 11_000L);
    hold.increaseTo(16_000L);
    hold.release(Instant.parse("2026-07-29T00:00:00Z"));

    assertThat(hold.getAmount()).isEqualTo(16_000L);
    assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
    assertThat(hold.getReleasedAt()).isNotNull();
    assertThatThrownBy(() -> hold.capture(Instant.now()))
        .isInstanceOf(InvalidWalletHoldStateException.class);
}
```

- [ ] **Step 2: 같은 사용자의 hold 이력 조회 테스트를 작성한다**

RELEASED row를 먼저 저장하고 같은 wallet·auction에 새 HELD row를 저장한다. `findFirstByWalletIdAndAuctionIdOrderByIdDesc()`가 새 HELD row를 반환하는지 실제 MySQL에서 검증한다.

- [ ] **Step 3: 실패 테스트를 실행한다**

```bash
./gradlew test \
  --tests com.dbidding.wallet.domain.WalletHoldTest \
  --tests com.dbidding.wallet.repository.WalletHoldRepositoryTest
```

Expected: WalletHold 관련 타입이 없어 컴파일 실패.

- [ ] **Step 4: 엔티티와 Repository를 구현한다**

`WalletHold.id`는 `Long`, `walletId`와 `auctionId`는 `Integer`, `amount`는 `long`이다. `releasedAt`은 RELEASED와 CAPTURED가 처리된 시각을 기록한다. `increaseTo()`는 HELD 상태이고 새 금액이 기존 금액 이상일 때만 허용한다.

- [ ] **Step 5: 테스트를 다시 실행하고 커밋한다**

```bash
./gradlew test \
  --tests com.dbidding.wallet.domain.WalletHoldTest \
  --tests com.dbidding.wallet.repository.WalletHoldRepositoryTest
git add backend/src/main/java/com/dbidding/wallet/domain \
  backend/src/main/java/com/dbidding/wallet/repository \
  backend/src/main/java/com/dbidding/wallet/exception \
  backend/src/test/java/com/dbidding/wallet
git commit -m "feat: Wallet 입찰 홀드 상태 모델 추가"
```

### Task 2: WalletPort 조회·홀드·해제 adapter

**Files:**
- Create: `backend/src/main/java/com/dbidding/wallet/adapter/AuctionWalletAdapter.java`
- Test: `backend/src/test/java/com/dbidding/wallet/adapter/AuctionWalletAdapterTest.java`

**Interfaces:**
- Consumes: `auction.port.WalletPort`
- Consumes: `WalletRepository.findByUserIdForUpdate(Integer userId)`
- Consumes: `WalletRepository.sumHeldAmount(Integer walletId)`
- Consumes: `InsufficientAvailableBalanceException` from `3-charge-and-refund.md`
- Produces: `WalletSnapshot getWallet(Integer userId)`
- Produces: `WalletSnapshot holdBidAmount(Integer userId, Integer auctionId, long totalAmount)`
- Produces: `WalletSnapshot releaseBidHold(Integer userId, Integer auctionId)`

단위 테스트에서는 JPA가 부여하는 Wallet ID만 spy로 대체한다.

```java
private Wallet walletWithPoint(long point) {
    Wallet wallet = spy(Wallet.open(1));
    given(wallet.getId()).willReturn(10);
    wallet.credit(point);
    return wallet;
}
```

- [ ] **Step 1: 신규 hold와 재입찰 증가분 테스트를 작성한다**

```java
@Test
void 재입찰은_기존_hold와의_차액만_가용잔액에서_검증한다() {
    Wallet wallet = walletWithPoint(20_000L);
    WalletHold hold = WalletHold.held(wallet.getId(), 20, 11_000L);
    given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
    given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(11_000L);
    given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
        wallet.getId(), 20
    )).willReturn(Optional.of(hold));

    WalletSnapshot result = adapter.holdBidAmount(1, 20, 16_000L);

    assertThat(hold.getAmount()).isEqualTo(16_000L);
    assertThat(result.availableBalance()).isEqualTo(4_000L);
    assertThat(result.frozenBalance()).isEqualTo(16_000L);
}
```

기존 hold 11,000원은 이미 전체 HELD 합계에 포함돼 있으므로 추가로 필요한 금액은 5,000원이다.

- [ ] **Step 2: 잔액 부족과 재진입 테스트를 작성한다**

가용 잔액이 증가분보다 작으면 `InsufficientAvailableBalanceException`이고 hold를 변경하지 않는다. 최신 row가 RELEASED면 새 HELD row를 저장하며 이전 row는 변경하지 않는다.

- [ ] **Step 3: 멱등 release 테스트를 작성한다**

```java
@Test
void 상회입찰_해제는_HELD만_RELEASED로_바꾸고_중복호출은_no_op이다() {
    adapter.releaseBidHold(1, 20);
    adapter.releaseBidHold(1, 20);

    assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
    then(walletHoldRepository).should(never()).delete(any());
}
```

- [ ] **Step 4: 실패 테스트를 실행한다**

```bash
./gradlew test --tests com.dbidding.wallet.adapter.AuctionWalletAdapterTest
```

Expected: 실제 adapter가 없어 컴파일 실패.

- [ ] **Step 5: adapter를 구현한다**

```java
@Component
@Profile("!auction-mock")
@RequiredArgsConstructor
public class AuctionWalletAdapter implements WalletPort {
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public WalletSnapshot holdBidAmount(
        Integer userId,
        Integer auctionId,
        long totalAmount
    ) {
        Wallet wallet = lockWallet(userId);
        WalletHold latest = latestHold(wallet.getId(), auctionId).orElse(null);
        long currentAmount = latest != null && latest.isHeld()
            ? latest.getAmount()
            : 0L;
        long additionalAmount = totalAmount - currentAmount;
        long available = availableBalance(wallet);
        if (additionalAmount < 0 || available < additionalAmount) {
            throw new InsufficientAvailableBalanceException();
        }
        if (latest != null && latest.isHeld()) {
            latest.increaseTo(totalAmount);
        } else {
            walletHoldRepository.save(
                WalletHold.held(wallet.getId(), auctionId, totalAmount)
            );
        }
        return snapshot(wallet);
    }
}
```

Wallet row 잠금이 같은 사용자의 충전·환불·다른 경매 hold까지 직렬화한다. Port 호출자에게 `findByUserIdForUpdate()`를 별도로 노출하지 않는다.

- [ ] **Step 6: adapter 테스트를 다시 실행하고 커밋한다**

```bash
./gradlew test --tests com.dbidding.wallet.adapter.AuctionWalletAdapterTest
git add backend/src/main/java/com/dbidding/wallet/adapter \
  backend/src/main/java/com/dbidding/wallet/exception \
  backend/src/test/java/com/dbidding/wallet/adapter
git commit -m "feat: 입찰 금액 홀드와 해제 구현"
```

### Task 3: 낙찰 확정 차감

**Files:**
- Modify: `backend/src/main/java/com/dbidding/wallet/adapter/AuctionWalletAdapter.java`
- Test: `backend/src/test/java/com/dbidding/wallet/adapter/AuctionWalletCaptureTest.java`
- Test: `backend/src/test/java/com/dbidding/wallet/adapter/AuctionWalletCaptureIntegrationTest.java`

**Interfaces:**
- Consumes: `PointRecord.auctionCapture(...)`
- Produces: `WalletSnapshot confirmWinningBid(Integer userId, Integer auctionId, long totalAmount)`

`AuctionWalletCaptureTest`에도 Task 2와 같은 `walletWithPoint()` helper를 둔다.

- [ ] **Step 1: 낙찰 차감과 원장 테스트를 작성한다**

```java
@Test
void 낙찰하면_hold를_CAPTURED로_바꾸고_총잔액을_실제_차감한다() {
    Wallet wallet = walletWithPoint(20_000L);
    WalletHold hold = WalletHold.held(wallet.getId(), 20, 16_000L);
    given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
    given(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
        wallet.getId(), 20
    )).willReturn(Optional.of(hold));

    WalletSnapshot result = adapter.confirmWinningBid(1, 20, 16_000L);

    assertThat(wallet.getPoint()).isEqualTo(4_000L);
    assertThat(hold.getStatus()).isEqualTo(HoldStatus.CAPTURED);
    assertThat(result.frozenBalance()).isZero();
    then(pointRecordRepository).should().save(argThat(record ->
        record.getTransactionType() == PointTransactionType.AUCTION_CAPTURE
            && record.getAmount() == -16_000L
            && record.getAuctionId().equals(20)
            && record.getBalance() == 4_000L
    ));
}
```

- [ ] **Step 2: 중복 확정과 잘못된 금액 테스트를 작성한다**

이미 CAPTURED인 최신 hold에 같은 금액으로 다시 호출하면 기존 결과를 반환하고 재차감·원장 추가를 하지 않는다. HELD 금액과 요청 금액이 다르거나 최신 상태가 RELEASED면 `InvalidWalletHoldStateException`이다.

- [ ] **Step 3: 실패 테스트를 실행한다**

```bash
./gradlew test \
  --tests com.dbidding.wallet.adapter.AuctionWalletCaptureTest
```

Expected: `confirmWinningBid()` 실제 구현이 없어 실패.

- [ ] **Step 4: 낙찰 확정을 구현한다**

wallet row 잠금 → 최신 hold 확인 → 상태·금액 검증 → Wallet 차감 → HELD를 CAPTURED로 전이 → `AUCTION_CAPTURE` 원장 저장 순서로 처리한다. 어느 단계든 실패하면 호출자의 Auction 트랜잭션과 함께 전부 롤백돼야 한다.

- [ ] **Step 5: 실제 MySQL 통합 테스트를 작성한다**

두 스레드가 같은 낙찰 확정을 동시에 호출해도 point 차감과 `AUCTION_CAPTURE` 원장이 정확히 한 번만 발생하는지 검증한다.

- [ ] **Step 6: 테스트를 실행하고 커밋한다**

```bash
DB_PORT=3306 ./gradlew test \
  --tests com.dbidding.wallet.adapter.AuctionWalletCaptureTest \
  --tests com.dbidding.wallet.adapter.AuctionWalletCaptureIntegrationTest
git add backend/src/main/java/com/dbidding/wallet/adapter \
  backend/src/test/java/com/dbidding/wallet/adapter
git commit -m "feat: 낙찰 금액 확정 차감 구현"
```

### Task 4: Auction 트랜잭션 계약

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionService.java`
- Test: `backend/src/test/java/com/dbidding/auction/service/AuctionWalletTransactionTest.java`

**Interfaces:**
- Consumes: `WalletPort.holdBidAmount(...)`
- Consumes: `WalletPort.releaseBidHold(...)`
- Consumes: `WalletPort.confirmWinningBid(...)`
- Produces: 입찰·상회입찰·낙찰과 Wallet 변경의 단일 트랜잭션

- [ ] **Step 1: 입찰 호출 순서 테스트를 작성한다**

```java
@Test
void 경매를_잠그고_검증한_뒤_배송비를_포함한_금액을_hold한다() {
    auctionService.placeBid(auctionId, bidAmount);

    InOrder order = inOrder(auctionRepository, walletPort, bidRepository);
    order.verify(auctionRepository).findByIdForUpdate(auctionId);
    order.verify(walletPort).holdBidAmount(
        bidderId,
        auctionId,
        bidAmount + shippingFee
    );
    order.verify(bidRepository).save(any(Bid.class));
}
```

- [ ] **Step 2: 상회 입찰과 실패 롤백 테스트를 작성한다**

새 최고 입찰자의 hold가 성공하고 Bid가 저장된 뒤 이전 최고 입찰자의 hold를 해제한다. 가격·상태·잔액 검증 또는 Bid 저장이 실패하면 테스트 트랜잭션 종료 후 WalletHold와 Bid가 모두 원상태인지 실제 DB로 검증한다.

- [ ] **Step 3: 낙찰 확정 테스트를 작성한다**

경매 종료 시 `finalPrice + shippingFee`를 `confirmWinningBid()`로 전달한다. 유찰이면 남은 HELD를 `releaseBidHold()`로 해제하고 `confirmWinningBid()`는 호출하지 않는다.

- [ ] **Step 4: 외부 이벤트를 커밋 이후로 제한한다**

SSE·알림처럼 외부에 보이는 소비자는 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`를 사용한다. Wallet hold와 capture 자체는 이벤트 리스너로 구현하지 않는다.

- [ ] **Step 5: 통합 테스트와 전체 테스트를 실행한다**

```bash
DB_PORT=3306 ./gradlew test \
  --tests com.dbidding.auction.service.AuctionWalletTransactionTest
DB_PORT=3306 env -u JWT_SECRET ./gradlew clean test
```

Expected: 입찰·상회입찰·낙찰·유찰과 Wallet 상태가 함께 커밋 또는 롤백되고 전체 테스트 실패 0건.

- [ ] **Step 6: 통합을 커밋한다**

```bash
git add backend/src/main/java/com/dbidding/auction \
  backend/src/test/java/com/dbidding/auction \
  docs/hyeonmoon/wallet/5-auction-wallet-integration.md
git commit -m "feat: Auction과 Wallet 자금 흐름 연동"
```

## 완료 조건

- Auction은 현재 소유한 `WalletPort`만 의존하고 Wallet 내부 Repository를 알지 못한다.
- WalletPort의 변경 메서드는 Auction 트랜잭션 안에서 실행된다.
- Port 바깥으로 DB 락 획득 메서드를 노출하지 않는다.
- 입찰가와 배송비 전체를 hold하고 낙찰 시 같은 금액을 차감한다.
- 재입찰은 증가분만 추가 검증하며 상회 입찰은 이전 HELD를 RELEASED로 바꾼다.
- 낙찰 차감은 WalletHold 상태, Wallet point, PointRecord를 원자적으로 변경한다.
- 중복 낙찰 확정은 재차감되지 않는다.
- Wallet 자금 정합성을 비동기 이벤트 처리에 맡기지 않는다.
- 판매자 정산·출금은 Order/Delivery/Return 소유가 확정될 때 별도 설계한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
