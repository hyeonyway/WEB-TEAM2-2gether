# Wallet Hold/Release Implementation Plan

**Goal:** 입찰 수락 여부를 즉시 응답해야 하므로 Bid가 동기 호출로 잔액을 홀드/해제할 수 있게 한다. 아울러 입찰 처리 순서를 "지갑 홀드 → 경매가 검증"으로 고정해, 두 도메인 사이에서 눈에 보이는 롤백이 생기지 않게 한다.

**Architecture:** 사용하는 쪽인 Bid(이은기)가 `WalletGateway`를 정의하고, 제공하는 쪽인 Wallet(김현문)이 구현한다(`module-interfaces.md` 1절과 동일한 의존성 역전 패턴). Wallet 구현체는 `wallet_holds` 테이블의 `HELD`/`RELEASED`/`CAPTURED` 상태 전이만으로 동작하며, hard delete를 하지 않는다(`erd-review.md` 3절).

**Tech Stack:** Spring DI, Spring Transaction, JPA, JUnit 5, Mockito

## Global Constraints

- 인터페이스는 `bid.port.WalletGateway`에 있고 구현체는 `wallet`에 있다.
- Wallet 구현체는 Bid/Auction의 Entity나 Repository를 참조하지 않는다.
- `release()`/낙찰 시 `wallet_holds` row는 절대 hard delete하지 않는다. `status`만 `HELD → RELEASED` 또는 `HELD → CAPTURED`로 변경한다.
- `hold()` 성공 시 `wallets.point`는 변경하지 않는다. `point_records`에도 기록하지 않는다(실제 잔액이 움직인 게 아니므로).
- `CAPTURED`(낙찰 확정)로 전이할 때만 `wallets.point`를 실제로 차감하고 `point_records`에 한 줄 남긴다.
- 가용 잔액은 `wallets.point - SUM(wallet_holds.amount WHERE status = 'HELD')`로 계산하며, 별도 집계 컬럼을 추가하지 않는다(`erd-review.md` 3절).

---

### Task 1: WalletGateway 어댑터

**Files:**
- Create: `backend/src/main/java/com/dbidding/bid/port/WalletGateway.java`
- Create: `backend/src/main/java/com/dbidding/wallet/adapter/WalletGatewayImpl.java`
- Create: `backend/src/main/java/com/dbidding/wallet/exception/InsufficientBalanceException.java`
- Test: `backend/src/test/java/com/dbidding/wallet/adapter/WalletGatewayImplTest.java`

**Interfaces:**
- Produces: `void WalletGateway.hold(Integer userId, Integer auctionId, long amount)` — 잔액 부족 시 `InsufficientBalanceException`
- Produces: `void WalletGateway.release(Integer userId, Integer auctionId)`

- [ ] **Step 1: Bid 소유 Port 작성**

```java
package com.dbidding.bid.port;

public interface WalletGateway {
    void hold(Integer userId, Integer auctionId, long amount);
    void release(Integer userId, Integer auctionId);
}
```

- [ ] **Step 2: 홀드 성공/실패 테스트**

```java
@Test
void 가용잔액이_충분하면_HELD_row를_생성한다() {
    gateway.hold(1, 42, 11000L);

    then(walletHoldRepository).should().save(argThat(hold ->
        hold.getStatus() == HoldStatus.HELD
            && hold.getAmount() == 11000L
    ));
}

@Test
void 가용잔액이_부족하면_거절한다() {
    given(walletHoldRepository.sumHeldAmount(1)).willReturn(9500L);
    given(walletRepository.findPointByUserId(1)).willReturn(10000L);

    assertThatThrownBy(() -> gateway.hold(1, 42, 11000L))
        .isInstanceOf(InsufficientBalanceException.class);
}
```

- [ ] **Step 3: 해제 테스트**

```java
@Test
void release는_HELD_row를_RELEASED로만_바꾼다() {
    gateway.release(1, 42);

    then(walletHoldRepository).should().updateStatus(1, 42, HoldStatus.RELEASED);
    then(walletHoldRepository).should(never()).delete(any());
}
```

- [ ] **Step 4: 최소 구현**

```java
@Component
public class WalletGatewayImpl implements WalletGateway {
    private final WalletRepository walletRepository;
    private final WalletHoldRepository walletHoldRepository;

    @Override
    public void hold(Integer userId, Integer auctionId, long amount) {
        long available = walletRepository.findPointByUserId(userId)
            - walletHoldRepository.sumHeldAmount(userId);
        if (available < amount) {
            throw new InsufficientBalanceException();
        }
        walletHoldRepository.save(WalletHold.held(userId, auctionId, amount));
    }

    @Override
    public void release(Integer userId, Integer auctionId) {
        walletHoldRepository.updateStatus(userId, auctionId, HoldStatus.RELEASED);
    }
}
```

## Task 2: 호출 순서 규칙 — "지갑 먼저, 가격 검증 나중"

입찰 요청 처리 순서를 다음과 같이 고정한다. 이 규칙은 Bid(이은기) 서비스 코드에서 지켜야 하며, Wallet 쪽 구현과는 별개로 팀 전체가 합의해야 하는 계약이다.

1. **`WalletGateway.hold()` 먼저 호출** — 유저 1인당 자기 wallet row 하나만 다루므로 경합 병목이 아니다. 잔액 부족이면 여기서 즉시 거절, 경매 row에는 손대지 않는다.
2. 홀드 성공 시에만 **경매 row `SELECT ... FOR UPDATE` 검증/갱신** 진행.
3. 2번에서 실패(그 사이 outbid) → 1번에서 잡은 홀드를 즉시 `release()`.

**이 순서인 이유:** 반대로(가격 먼저 → 지갑 나중) 처리하면, 이미 다른 입찰자들에게 SSE로 새 현재가를 알린 뒤 지갑 부족으로 되돌려야 하는 눈에 보이는 롤백이 발생한다(`../realtime/1-sse-architecture.md` 참고). 지갑을 먼저 확인하면 이 상황 자체가 생기지 않는다.

- [ ] **Step 1: 순서 준수 통합 테스트 (Bid 쪽에서 작성)**

```java
@Test
void 잔액_부족시_경매_row는_조회조차_하지_않는다() {
    given(walletGateway.hold(any(), any(), any())).willThrow(new InsufficientBalanceException());

    assertThatThrownBy(() -> bidService.placeBid(1, 42, 11000L))
        .isInstanceOf(InsufficientBalanceException.class);
    then(auctionRepository).should(never()).findByIdForUpdate(any());
}

@Test
void outbid로_가격검증_실패시_홀드를_해제한다() {
    given(auctionRepository.findByIdForUpdate(42)).willReturn(auctionWithHigherPrice());

    assertThatThrownBy(() -> bidService.placeBid(1, 42, 11000L))
        .isInstanceOf(InvalidBidAmountException.class);
    then(walletGateway).should().release(1, 42);
}
```

### Task 3: 단위 테스트와 커밋

```bash
./gradlew test --tests com.dbidding.wallet.adapter.WalletGatewayImplTest
git add backend/src/main/java/com/dbidding/bid/port/WalletGateway.java \
  backend/src/main/java/com/dbidding/wallet backend/src/test/java/com/dbidding/wallet
git commit -m "feat: 입찰 잔액 홀드/해제 연동"
```

## 완료 조건

- Wallet 구현체는 Bid/Auction의 Entity와 Repository를 import하지 않는다.
- `hold()` 성공 시 `wallets.point`는 변하지 않고 `wallet_holds`에만 `HELD` row가 생긴다.
- `release()`/낙찰 확정 어느 쪽도 `wallet_holds` row를 hard delete하지 않는다.
- Bid 서비스는 항상 `hold()` 성공 이후에만 경매 row 락/검증을 진행한다.
- 경매가 검증에 실패하면 반드시 해당 홀드를 `release()`한다.
- 가용 잔액은 `wallets.point - SUM(HELD)`로 매번 계산되며 별도 컬럼에 저장되지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
