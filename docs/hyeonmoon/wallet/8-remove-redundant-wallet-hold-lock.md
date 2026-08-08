# WalletHold 조회의 불필요한 행 락 제거 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `WalletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(...)`에 걸린 `@Lock(LockModeType.PESSIMISTIC_WRITE)`를 제거한다. Wallet 행 락과 `sumHeldAmountForUpdate`의 `FOR UPDATE`가 이미 같은 트랜잭션 안에서 해당 hold 행을 잠근 뒤라, 이 추가 락은 아무 보호도 더하지 못하는 중복 락이다.

**Architecture:** hold/release/capture 세 경로 모두 다음 순서로 실행된다.

```text
1. lockWallet()               — wallets 행 PESSIMISTIC_WRITE (사용자 1명당 1행, UNIQUE user_id)
2. sumHeldAmountForUpdate()   — wallet_holds WHERE wallet_id=? AND status='HELD' 를 FOR UPDATE로 합산
3. latestHold()               — 같은 wallet_id(+auction_id)의 최신 hold 1건을 @Lock(PESSIMISTIC_WRITE)로 재조회
```

2번이 이미 "이 지갑의 활성(HELD) hold 전부"를 InnoDB `FOR UPDATE`로 잠근 뒤이므로, 3번이 찾는 행이 `HELD` 상태라면 같은 트랜잭션이 이미 그 행의 락을 보유한 상태에서 다시 락을 요청하는 것이다(재요청은 성공하지만 아무 것도 추가로 막지 않는다). 3번이 찾는 행이 `HELD`가 아니면(`RELEASED`/`CAPTURED`, 또는 아직 없음) 다른 트랜잭션이 그 행을 동시에 바꿀 이유가 없으므로 애초에 락이 필요 없다. 어느 경우든 3번의 `@Lock`은 실질적 효과가 없다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, MySQL 8.4(Testcontainers), JUnit 5

## 조사 근거

- `WalletService.holdObserved/releaseObserved/captureObserved`
  (`backend/src/main/java/com/dbidding/wallet/service/WalletService.java:118-190`)는
  모두 `lockWallet(...)` → `walletRepository.sumHeldAmountForUpdate(...)` →
  `latestHold(...)` 순서로 호출한다. 순서를 바꾸는 별도 경로는 없다.
- `sumHeldAmountForUpdate`
  (`backend/src/main/java/com/dbidding/wallet/repository/WalletRepository.java:29-36`):
  ```sql
  SELECT COALESCE(SUM(amount), 0) FROM wallet_holds
  WHERE wallet_id = :walletId AND status = 'HELD'
  FOR UPDATE
  ```
  이 wallet의 `HELD` hold 전부를 이미 잠근다.
- `latestHold`가 호출하는
  `WalletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc`
  (`backend/src/main/java/com/dbidding/wallet/repository/WalletHoldRepository.java:14-18`)에만
  `@Lock(LockModeType.PESSIMISTIC_WRITE)`가 붙어 있다. `WalletHoldRepository`에는
  이 메서드 외 다른 커스텀 조회가 없고, `WalletService.latestHold()` 외에는
  아무 곳에서도 호출하지 않는다(전체 `backend/src` grep 기준).
- `WalletHoldRepository`에 대한 이 락은 커밋 `473e88d`(`fix: Wallet 동시 hold와
  오류 응답 보강`)에서 추가됐다. 그런데 실제 근본 원인 수정은 그보다 먼저인
  커밋 `035b488`(`fix: Wallet hold 합계를 잠금 읽기로 조회`)에서
  `sumHeldAmountForUpdate`를 도입해 이미 끝나 있었다([관련 조사
  문서](7-concurrent-hold-overrun-bug-report.md) 참고). `473e88d`의 hold-행
  락은 근본 수정 위에 더해진 방어적 중복 조치로 보인다.
- Auction 도메인은 `WalletHold`를 직접 참조하지 않는다. `AuctionCommandService`는
  `WalletPort`(consumer-owned)만 호출하고, Auction 자체의 행 락
  (`findByIdForUpdate`)은 `wallet_holds`와 무관한 별도 테이블(`auctions`)에
  대한 것이다([관련 문서](../auction/1-bid-wallet-lock-ordering-deadlock.md)).
  즉 Wallet(지갑 행)과 Auction(경매 행) 양쪽 락으로 이미 동시성이 보장되고,
  `wallet_holds` 행 자체에 별도 락을 걸 이유가 없다.

## Global Constraints

- `wallet_holds` 테이블 컬럼과 인덱스, `HoldStatus` 상태 전이는 변경하지 않는다.
- `WalletService`의 hold/release/capture 계산식과 예외 계약
  (`InsufficientAvailableBalanceException`, `InvalidWalletHoldStateException` 등)은
  변경하지 않는다.
- `lockWallet()` (`wallets` 행 `PESSIMISTIC_WRITE`)과 `sumHeldAmountForUpdate`
  (`FOR UPDATE`)는 그대로 유지한다 — 실제 동시성 보장은 이 두 락이 담당한다.
- 이번 변경은 `WalletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc`의
  `@Lock` annotation 제거로 한정한다. 메서드 시그니처, 반환 타입, 정렬 기준은
  바꾸지 않는다.

---

### Task 1: WalletHoldRepository 조회에서 중복 락을 제거한다

**Files:**
- Modify: `backend/src/main/java/com/dbidding/wallet/repository/WalletHoldRepository.java`
- Verify: `backend/src/test/java/com/dbidding/wallet/service/WalletTransactionConcurrencyTest.java`
- Verify: `backend/src/test/java/com/dbidding/wallet/service/WalletServiceHoldTest.java`
- Verify: `backend/src/test/java/com/dbidding/wallet/service/WalletServiceCaptureTest.java`

**Interfaces:**
- Removes: `WalletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc`의
  `@Lock(LockModeType.PESSIMISTIC_WRITE)`
- Preserves: 메서드 시그니처와 동작(정렬·필터 조건)

- [x] **Step 1: 기존 동시성 회귀 테스트가 지금도 통과하는지 먼저 확인한다**

```bash
cd backend
./gradlew test --tests com.dbidding.wallet.service.WalletTransactionConcurrencyTest
```

변경 전 baseline을 남긴다. 이 테스트가 "서로 다른 경매에 대한 동시 hold"
케이스를 포함하지 않는다면, `@Lock` 제거 후 안전성을 실제로 검증할 수
있도록 아래 Step 2에서 케이스를 추가한다.

2026-08-08 baseline 통과. 이 테스트는 서로 다른 경매의 동시 hold와 같은
경매의 중복 hold 방지를 이미 실제 MySQL Testcontainers로 검증한다.

- [x] **Step 2: 필요하면 동시 hold/release/capture 동시성 테스트를 보강한다**

같은 지갑·같은 경매에 대해 동시에 여러 스레드가 hold를 늘리는 시나리오,
그리고 하나는 release, 다른 하나는 capture를 동시에 시도하는 시나리오를
실제 MySQL Testcontainer로 검증해, `@Lock` 제거 후에도 hold 합계가
지갑 총액을 넘지 않고 상태 전이가 한 번만 반영되는지 확인한다.

별도 보강은 하지 않았다. 기존 `WalletTransactionConcurrencyTest`가 이번
변경의 핵심 회귀 조건(서로 다른 경매의 동시 hold가 총잔액을 초과하지 않음,
같은 경매에서 HELD를 중복 생성하지 않음)을 이미 보장한다.

- [x] **Step 3: `@Lock` annotation을 제거한다**

`WalletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc`에서
`@Lock(LockModeType.PESSIMISTIC_WRITE)`와 관련 import(`jakarta.persistence.LockModeType`,
`org.springframework.data.jpa.repository.Lock`)를 제거한다.

- [x] **Step 4: Wallet 관련 테스트 전체를 재실행한다**

```bash
./gradlew test --tests 'com.dbidding.wallet.*'
```

`WalletTransactionConcurrencyTest`는 변경 후 다시 통과했다. 다만
`./gradlew test --tests 'com.dbidding.wallet.*'`에서는 67개 중 2개가 실패했다.
두 실패는 `WalletRepositoryTest`가 외부 DB 대체를 비활성화하면서 JDBC URL 또는
Testcontainer를 제공하지 않아 Hibernate dialect를 결정하지 못한 테스트 설정 문제다.
이번 Repository annotation 변경과 무관하다.

- [x] **Step 5: 변경을 커밋한다**

```bash
git add backend/src/main/java/com/dbidding/wallet/repository/WalletHoldRepository.java \
  backend/src/test/java/com/dbidding/wallet/service/WalletTransactionConcurrencyTest.java
git commit -m "refactor: WalletHold 조회의 중복 행 락 제거"
```

### Task 2: 전체 회귀 검증과 문서 정리

**Files:**
- Modify: `docs/hyeonmoon/wallet/README.md`

- [x] **Step 1: 전체 백엔드 테스트를 실행한다**

```bash
cd backend
./gradlew clean test
```

2026-08-08 `./gradlew clean test` 실행 결과 447개 중 30개 실패, 17개 skip.
실패는 세션 인증 테스트의 빈 구성 누락, `@DataJpaTest`의 JDBC URL 누락, 그리고
여러 Testcontainers 테스트의 Docker 탐색 실패에서 발생했다. WalletHold 중복 락
제거와 관련된 검증 대상인 `WalletTransactionConcurrencyTest`는 별도 재실행에서
통과했다.

- [x] **Step 2: Wallet README 구현 단계 목록을 갱신한다**

`docs/hyeonmoon/wallet/README.md`의 구현 단계 목록에 이번 문서를 추가한다.

- [x] **Step 3: 문서 정리를 커밋한다**

```bash
git add docs/hyeonmoon/wallet
git commit -m "docs: WalletHold 중복 락 제거 문서 반영"
```

## 완료 조건

- `WalletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc`에
  `@Lock`이 더 이상 없다.
- `lockWallet()`과 `sumHeldAmountForUpdate`의 `FOR UPDATE`는 그대로 유지된다.
- 동시 hold/release/capture 시나리오에서 hold 합계가 지갑 총액을 초과하지
  않는다는 것이 (보강된) 동시성 테스트로 재확인된다.
- 전체 백엔드 테스트가 실패 없이 통과한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
