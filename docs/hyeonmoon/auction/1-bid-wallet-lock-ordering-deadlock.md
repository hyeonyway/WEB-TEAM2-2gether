# 입찰 시 Wallet 락 순서 고정 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `AuctionCommandService.participate()`가 서로 다른 두 지갑의 행 락을
매번 다른 순서로 잡아 발생하는 교차 데드락을 제거한다.

**Architecture:** 새 입찰자와 이전 최고 입찰자의 지갑을 모두 잠가야 하는
경우, "누가 새로 입찰했는가"가 아니라 **두 사용자 ID 중 작은 쪽을 항상
먼저** 잠그도록 호출 순서를 고정한다. Wallet의 hold/release 자체 로직과
DB 스키마, HTTP 계약은 변경하지 않는다.

**Tech Stack:** Java 21, Spring Boot 4.1, MySQL 8.4(Testcontainers), JUnit 5,
Mockito, AssertJ

## Global Constraints

- 이 작업의 범위는 `AuctionCommandService.participate()`의 hold/release
  호출 **순서**로 한정한다. `WalletService`의 락·검증 로직(hold, release,
  capture, `sumHeldAmountForUpdate`)은 이번 범위에 포함하지 않는다.
- Wallet의 HTTP API, DB 스키마, `WalletPort` 인터페이스 시그니처는
  변경하지 않는다.
- 같은 사용자가 자신의 이전 입찰을 스스로 outbid하는 경우(현재
  `outbidPreviousLeadingBid`가 이미 처리하는 "본인 재입찰" 분기)의 동작은
  바꾸지 않는다.
- 입찰 낙관적/비관적 락 전략, `findByIdForUpdate`를 통한 Auction row 락은
  변경하지 않는다.

---

## 1. 문제 배경

`AuctionCommandService.participate()`는 한 트랜잭션 안에서 다음 순서로
지갑 두 개를 건드릴 수 있다.

```java
WalletPort.WalletSnapshot wallet = holdBidAmount(userId, auction.getId(), request.price());  // ① 새 입찰자 지갑
outbidPreviousLeadingBid(previousLeadingBid, userId, auction, bidAt);                          // ② 이전 최고 입찰자 지갑(release)
```

이 순서는 "누가 새로 입찰했는가"에 따라 결정되고, 두 지갑 중 어느 쪽이
먼저 잠기는지는 매 요청마다 달라진다. 그래서 다음 조합이 가능하다.

```text
경매 X: 사용자 A가 새로 입찰해 B를 outbid → 지갑 락 순서 A → B
경매 Y: 사용자 B가 새로 입찰해 A를 outbid → 지갑 락 순서 B → A
```

경매 X와 Y는 서로 다른 경매라 `findByIdForUpdate`의 Auction row 락으로
직렬화되지 않는다. 두 트랜잭션이 동시에 진행되면 X는 A를 잠그고 B를
기다리고, Y는 B를 잠그고 A를 기다리는 전형적인 락 순서 역전 데드락이
만들어진다.

2026-08-05 부하 테스트에서 실제로 재현됐다. 당시 사용자 수는 이슈와 초기
문서의 기록이 서로 달라 확정값으로 남기지 않고, 확인 가능한 오류 건수만
기록한다.

```text
Deadlock found when trying to get lock; try restarting transaction  — 3,246건
```

MySQL이 데드락을 감지해 한쪽 트랜잭션을 강제 롤백시키고, 그 예외가 그대로
`500`으로 노출됐다. 이 사이클 자체를 없애는 것이 이 문서의 목표다.

## 2. 해결 방향

락을 요청하는 **순서를 모든 트랜잭션이 동의하는 하나의 기준**으로
고정하면 사이클이 원천적으로 생기지 않는다. 두 사용자 ID를 비교해
**작은 쪽을 항상 먼저** 잠그도록 한다.

```text
경매 X: A(작음) → B(큼)  — A를 먼저
경매 Y: B(큼) → A(작음)이 아니라, 여기서도 A(작음)를 먼저
```

이러면 X와 Y 모두 "A 먼저, B 나중"으로 수렴하므로 반대 방향으로 도는
상황 자체가 없어진다.

`wallets.user_id`에는 UNIQUE 제약이 있어 사용자와 지갑 행이 1:1로
대응한다. 따라서 사용자 ID는 실제로 잠기는 지갑 행에 대해 모든 요청이
공유할 수 있는 안정적인 전역 순서 기준이다.

이전 입찰자의 ID가 더 작으면 release가 hold보다 먼저 실행되지만 두 작업과
입찰 상태 전이는 모두 `participate()`의 같은 트랜잭션에 참여한다. 뒤의 hold가
실패하면 앞의 release와 `Bid.OUTBID` 전이도 함께 롤백되므로 자금 원자성은
유지된다.

## 3. 범위 확인

`participate()`에서 지갑을 건드리는 지점은 두 곳이다.

| 호출 | 대상 지갑 | 현재 순서 |
|---|---|---|
| `holdBidAmount(userId, ...)` | 새 입찰자 | 항상 먼저 |
| `outbidPreviousLeadingBid(...)` → `walletPort.releaseBidHold(...)` | 이전 최고 입찰자 (다른 사용자일 때만) | 항상 나중 |

`previousLeadingBid`가 없거나(첫 입찰) 이전 최고 입찰자가 현재 입찰자
본인이면 지갑은 하나만 건드리므로 순서 문제가 없다. 순서를 바꿔야 하는
경우는 **이전 최고 입찰자가 존재하고, 그 사용자가 현재 입찰자와 다를
때**뿐이다.

---

### Task 1: 지갑 호출 순서 단위 회귀 테스트 작성

**Files:**
- Modify: `backend/src/test/java/com/dbidding/auction/service/AuctionServiceTest.java`

**Interfaces:**
- Consumes: `AuctionCommandService.participate()`
- Verifies: 이전 최고 입찰자와 현재 입찰자의 사용자 ID 중 작은 쪽에 대한
  `WalletPort` 호출이 먼저 발생한다

- [x] **Step 1: 이전 입찰자의 ID가 더 작을 때 release가 먼저인지 검증한다**

이전 최고 입찰자가 사용자 1이고 현재 입찰자가 더 큰 ID인 경매를 준비한다.
`Mockito.inOrder(walletPort)`로 다음 호출 순서를 검증한다.

```text
releaseBidHold(userId=1) → holdBidAmount(userId=2)
```

- [x] **Step 2: 현재 입찰자의 ID가 더 작을 때 hold가 먼저인지 검증한다**

이전 최고 입찰자가 사용자 2이고 현재 입찰자가 사용자 1인 기존 상회 입찰
테스트에서 다음 호출 순서를 검증한다.

```text
holdBidAmount(userId=1) → releaseBidHold(userId=2)
```

- [x] **Step 3: 현재 코드에서 첫 번째 순서 테스트가 실패하는지 확인한다**

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.service.AuctionServiceTest.*지갑*순서*'
```

Expected: 현재 구현은 항상 hold를 먼저 호출하므로, 이전 입찰자 ID가 더 작은
시나리오가 실패한다.

### Task 2: 실제 MySQL 교차 데드락 재현 테스트 작성

**Files:**
- Create: `backend/src/test/java/com/dbidding/auction/service/AuctionBidWalletLockOrderConcurrencyTest.java`
- Verify: `backend/src/test/java/com/dbidding/wallet/service/WalletTransactionConcurrencyTest.java` (락 재현 패턴 참고)

**Interfaces:**
- Consumes: `AuctionCommandService.participate()`
- Verifies: 서로 다른 두 경매에서 사용자 A·B가 서로를 교차로 outbid하는
  두 요청이 동시에 들어와도 `CannotAcquireLockException`(데드락) 없이
  둘 다 성공한다

- [x] **Step 1: 교차 outbid 시나리오의 실패(재현) 테스트를 작성한다**

`WalletTransactionConcurrencyTest`처럼 실제 MySQL Testcontainer를 띄우고,
다음을 준비한다.

```text
사용자 A, B — 각각 지갑 잔액 충분
경매 X — A가 이미 LEADING 상태로 입찰해 있음 (previousLeadingBid = A)
경매 Y — B가 이미 LEADING 상태로 입찰해 있음 (previousLeadingBid = B)
```

`CountDownLatch`로 다음 두 호출을 동시에 시작한다.

```text
스레드 1: participate(userId=B, auctionId=X, ...) → B가 A를 outbid
스레드 2: participate(userId=A, auctionId=Y, ...) → A가 B를 outbid
```

테스트 전용 `CoordinatedWalletPort`는 두 스레드의 첫 Wallet 호출 대상 ID를
모은 뒤 실제 `AuctionWalletAdapter`에 위임한다. 첫 대상이 서로 다르면 각
스레드가 첫 번째 지갑 행 락을 얻은 뒤 두 번째 Wallet 호출로 진행하도록
barrier를 건다.

현재 구현에서는 첫 대상이 B와 A로 다르므로 두 트랜잭션이 각각 첫 락을
보유한 상태에서 상대 지갑을 요청하며 MySQL 데드락이 결정적으로 발생한다.
수정 후에는 두 요청의 첫 대상이 모두 작은 사용자 A가 된다. 이때 barrier를
사용하지 않고 A 지갑 행에서 정상 직렬화하므로 인위적인 테스트 교착도 만들지
않는다.

- [x] **Step 2: 테스트가 현재 코드에서 결정적으로 실패하는지 확인한다**

```bash
cd backend
./gradlew test --tests com.dbidding.auction.service.AuctionBidWalletLockOrderConcurrencyTest
```

Expected: 한 요청이 `CannotAcquireLockException` 계열 원인으로 실패해, 두
요청이 모두 성공해야 한다는 assertion이 안정적으로 실패한다.

### Task 3: 지갑 락 순서를 사용자 ID 기준으로 고정

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auction/service/AuctionCommandService.java`
- Verify: `backend/src/test/java/com/dbidding/auction/service/AuctionServiceBidTest.java`
- Verify: `backend/src/test/java/com/dbidding/auction/service/AuctionBidWalletLockOrderConcurrencyTest.java`

**Interfaces:**
- Modifies: `participate()` 내부의 `holdBidAmount`/`outbidPreviousLeadingBid`
  호출 순서만 변경. 두 메서드의 시그니처와 `WalletPort` 계약은 그대로
  유지한다.

- [x] **Step 1: 순서 결정 로직을 추가한다**

`previousLeadingBid`가 있고 그 입찰자가 현재 입찰자와 다를 때만 순서를
비교한다. 두 사용자 ID 중 작은 쪽의 지갑 작업을 먼저 실행한다.

```java
Bid previousLeadingBid = highestBid(auction.getId()).orElse(null);
boolean releaseFirst = previousLeadingBid != null
        && !previousLeadingBid.getBidderId().equals(userId)
        && previousLeadingBid.getBidderId() < userId;

LocalDateTime bidAt = now();
LocalDateTime previousCloseTime = auction.getCloseTime();
boolean closeTimeExtended = placeBid(auction, request.price(), bidAt);

WalletPort.WalletSnapshot wallet;
if (releaseFirst) {
    outbidPreviousLeadingBid(previousLeadingBid, userId, auction, bidAt);
    wallet = holdBidAmount(userId, auction.getId(), request.price());
} else {
    wallet = holdBidAmount(userId, auction.getId(), request.price());
    outbidPreviousLeadingBid(previousLeadingBid, userId, auction, bidAt);
}
```

`outbidPreviousLeadingBid`가 `previousLeadingBid.markOutbid()`(입찰 상태
전이)와 지갑 release를 함께 수행하므로, 순서를 앞당겨도 상태 전이
자체의 의미는 바뀌지 않는지 기존 테스트로 확인한다.

- [x] **Step 2: Task 1~2의 회귀 테스트가 통과하는지 확인한다**

```bash
./gradlew test \
  --tests com.dbidding.auction.service.AuctionBidWalletLockOrderConcurrencyTest \
  --tests com.dbidding.auction.service.AuctionServiceTest
```

Expected: 반복 실행에서도 데드락 계열 예외가 더 이상 발생하지 않음.

- [x] **Step 3: 기존 입찰 관련 테스트 전체를 회귀 검증한다**

```bash
./gradlew test \
  --tests 'com.dbidding.auction.service.AuctionService*Test' \
  --tests com.dbidding.auction.service.AuctionBidWalletLockOrderConcurrencyTest
```

Expected: 입찰 성공/거절, 상회 입찰 해제, 종료 연장 등 기존 시나리오
모두 통과.

- [x] **Step 4: 설계 문서·테스트·구현을 커밋한다**

```bash
git add docs/hyeonmoon/auction/1-bid-wallet-lock-ordering-deadlock.md \
  backend/src/main/java/com/dbidding/auction/service/AuctionCommandService.java \
  backend/src/test/java/com/dbidding/auction/service/AuctionServiceTest.java \
  backend/src/test/java/com/dbidding/auction/service/AuctionBidWalletLockOrderConcurrencyTest.java
git commit -m "fix: 입찰 시 지갑 락 순서를 사용자 ID 기준으로 고정"
```

### Task 4: 전체 회귀 검증

**Files:**
- Verify: `backend/src/main/java/com/dbidding/auction/**`
- Verify: `backend/src/main/java/com/dbidding/wallet/**`

- [x] **Step 1: 전체 백엔드 테스트를 실행한다**

```bash
cd backend
./gradlew clean test
```

Result: 최신 `schema.sql`을 적용한 임시 MySQL 8.4에서 404개를 실행했다.
이번 변경 범위의 Auction·Wallet 테스트는 통과했고, 전체에서는 기존 비관련
실패 6개(AuctionQuery 1개, CardPrice 1개, StatisticAggregation 4개)가
남았다. 테스트 소스가 없는 패턴은 통과로 표현하지 않는다.

- [ ] **Step 2: 부하 테스트로 실측 재검증한다 (선택)**

가능하면 2026-08-05에 데드락이 재현됐던 것과 같은 부하 조건으로 k6
테스트를 다시 실행해, MySQL의
`mysql_info_schema_innodb_metrics_lock_lock_deadlocks_total` 증가량이
0인지 확인한다.

## 완료 조건

- `participate()`가 두 지갑을 건드릴 때 항상 작은 사용자 ID 쪽을 먼저
  잠근다.
- 서로 다른 두 경매의 교차 outbid 상황에서 데드락이 재현되지 않는다.
- 기존 입찰·지갑 관련 테스트가 모두 통과한다.
- Wallet의 HTTP API, DB 스키마, `WalletPort` 계약에는 변경이 없다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
