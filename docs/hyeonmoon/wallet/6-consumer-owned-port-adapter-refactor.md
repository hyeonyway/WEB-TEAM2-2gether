# Consumer-owned Port·Adapter 경계 리팩터링 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Account와 Auction이 사용하는 Wallet Port와 연결 Adapter를 소비자 도메인에 함께 배치해, Wallet이 자신을 호출하는 도메인의 계약을 알지 않도록 의존 방향을 정리한다.

**Architecture:** Notification의 `WishlistUserFinder → WishlistUserFinderAdapter → WishlistService` 구조와 같이 소비자가 Port와 로컬 연결 Adapter를 소유한다. 현재 모듈러 모놀리스에서는 Adapter가 `WalletService`를 직접 호출하고 기존 Spring 트랜잭션에 참여하며, 실제 서비스 분리 시에는 호출부와 Port를 유지한 채 Adapter 구현만 REST 클라이언트로 교체한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring DI, Spring Transaction, JUnit 5, Mockito, AssertJ

## Global Constraints

- 리팩터링 대상은 Account가 사용하는 Wallet 생성 접점과 Auction이 사용하는 Wallet 자금 처리 접점으로 한정한다.
- `WalletProvisioningPort`는 Account가 소유하고 `WalletPort`와 `WalletSnapshot`은 Auction이 소유한다.
- Port의 실제 연결 Adapter도 Port를 사용하는 Account와 Auction이 각각 소유한다.
- Wallet은 Account와 Auction의 Port, Entity, Repository를 import하지 않는다.
- Adapter는 Wallet의 Repository나 Entity를 직접 사용하지 않고 `WalletService`에만 위임한다.
- Wallet의 락, 잔액 검증, hold 상태 전이, 실제 차감과 원장 기록은 `WalletService`가 계속 소유한다.
- `WalletService.provision()`, `hold()`, `release()`, `capture()`의 `Propagation.MANDATORY`를 유지한다.
- 회원가입과 Wallet 생성, 입찰과 Wallet 자금 처리는 현재처럼 각각 하나의 DB 트랜잭션으로 처리한다.
- HTTP API, DB 스키마, Port 메서드, DTO 필드, 예외와 금액 계산 규칙은 변경하지 않는다.
- `auction-mock` 프로필에서는 `FakeWalletAdapter`, 그 외 프로필에서는 실제 `AuctionWalletAdapter`가 선택되는 현재 동작을 유지한다.
- Account JWT 발급·검증과 `global.security`는 이번 리팩터링에서 변경하지 않는다.
- 다른 담당 도메인의 Port·Adapter 구조는 이번 범위에 포함하지 않는다.

---

## 1. 변경 이유

현재 구현은 Port를 사용하는 쪽이 소유하지만 실제 Adapter는 Wallet에 있다.

```text
Account                            Wallet
└── WalletProvisioningPort  <──  wallet.adapter.WalletProvisioningAdapter
                                      └── WalletService

Auction                            Wallet
└── WalletPort              <──  wallet.adapter.AuctionWalletAdapter
                                      └── WalletService
```

이 구조에서는 Wallet이 `com.dbidding.account.port`와
`com.dbidding.auction.port`를 import한다. 새로운 소비자가 생길 때마다 Wallet의
`adapter` 패키지에 소비자별 구현체가 추가되므로, Wallet의 변경 이유가 자신의
자금 규칙이 아니라 호출자 증가까지 포함하게 된다.

리팩터링 후에는 Notification에 적용된 방식처럼 Port와 Adapter를 소비자가 함께
소유한다.

```text
Account
├── service.AuthService
├── port.WalletProvisioningPort
└── adapter.WalletProvisioningAdapter ──> WalletService

Auction
├── service.AuctionCommandService / AuctionQueryService
├── port.WalletPort
└── adapter.AuctionWalletAdapter ───────> WalletService

Wallet
├── service.WalletService
├── domain
└── repository
```

현재는 같은 애플리케이션이므로 Adapter가 Java 메서드로 `WalletService`를
호출한다. 나중에 Wallet을 별도 서비스로 분리할 때는 다음처럼 Adapter 내부만
바꾼다.

```text
현재:  AuctionService → WalletPort → AuctionWalletAdapter → WalletService
분리:  AuctionService → WalletPort → AuctionWalletAdapter → Wallet REST API
```

서비스 분리 뒤에는 같은 DB 트랜잭션을 공유할 수 없다. 그 시점에는 동기 REST
호출의 멱등키, 타임아웃, 재시도와 보상 처리를 별도로 설계해야 한다. 이번
리팩터링은 원격 통신이나 분산 트랜잭션을 미리 구현하지 않고, 교체할 위치를
명확하게 만드는 작업이다.

## 2. Account 범위 확인

Account 내부에서 다른 도메인의 기능을 동기 호출하는 접점은 회원가입 시 초기
Wallet을 만드는 `WalletProvisioningPort` 하나다.

```text
AuthService.signup()
  → AccountRepository.saveAndFlush(account)
  → WalletProvisioningPort.createFor(account.getId())
  → WalletProvisioningAdapter
  → WalletService.provision(userId)
```

`AuthService.signup()`이 연 트랜잭션에
`WalletService.provision()`이 `MANDATORY`로 참여하므로 Wallet 생성에 실패하면
Account 저장도 함께 롤백된다. Adapter의 패키지를 옮겨도 이 호출 순서와
트랜잭션 전파는 바꾸지 않는다.

`global.security`가 사용하는 `JwtTokenProvider`는 도메인 정보를 조회하는
Port·Adapter 관계가 아니라 모든 요청에서 수행하는 로컬 JWT 검증 인프라다.
서비스 분리 후에도 각 인스턴스가 JWT를 로컬 검증해야 하므로 이번 Adapter 이동
범위에서는 제외한다.

## 3. Wallet 범위 확인

Wallet은 다음 공개 유스케이스를 `WalletService`에서 제공한다.

| 메서드 | 소비자 | 역할 |
|---|---|---|
| `provision(Integer userId)` | Account | 초기 잔액 0원 Wallet 생성 |
| `getBalance(Integer userId)` | Wallet Controller, Auction | 총액·동결액·가용액 조회 |
| `hold(Integer userId, Integer auctionId, long totalAmount)` | Auction | 배송비 포함 입찰 금액 hold |
| `release(Integer userId, Integer auctionId)` | Auction | 상회 입찰로 이전 hold 해제 |
| `capture(Integer userId, Integer auctionId, long amount)` | Auction | 낙찰 확정 차감과 원장 기록 |

Adapter 이동 뒤에도 Wallet의 Controller는 자신의 `WalletService`를 직접
사용한다. 외부 도메인은 Wallet Entity와 Repository를 알지 않으며, Adapter도
Wallet 규칙을 재구현하지 않는다.

---

### Task 1: Account가 Wallet 생성 Adapter를 소유하도록 이동

**Files:**
- Create: `backend/src/main/java/com/dbidding/account/adapter/WalletProvisioningAdapter.java`
- Create: `backend/src/test/java/com/dbidding/account/adapter/WalletProvisioningAdapterTest.java`
- Delete: `backend/src/main/java/com/dbidding/wallet/adapter/WalletProvisioningAdapter.java`
- Delete: `backend/src/test/java/com/dbidding/wallet/adapter/WalletProvisioningAdapterTest.java`
- Verify: `backend/src/test/java/com/dbidding/account/integration/SignupTransactionTest.java`

**Interfaces:**
- Consumes: `void WalletService.provision(Integer userId)`
- Implements: `void WalletProvisioningPort.createFor(Integer userId)`
- Preserves: Wallet 생성 실패 시 Account 저장까지 롤백되는 회원가입 원자성

- [x] **Step 1: Account 패키지의 실패 테스트를 작성한다**

`backend/src/test/java/com/dbidding/account/adapter/WalletProvisioningAdapterTest.java`에
다음 테스트를 작성한다. 아직 Account 패키지에 Adapter가 없으므로 컴파일이
실패해야 한다.

```java
package com.dbidding.account.adapter;

import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.wallet.service.WalletService;

@ExtendWith(MockitoExtension.class)
class WalletProvisioningAdapterTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private WalletProvisioningAdapter adapter;

    @Test
    void 지갑_생성을_WalletService에_위임한다() {
        adapter.createFor(1);

        then(walletService).should().provision(1);
    }
}
```

- [x] **Step 2: 새 패키지의 테스트가 실패하는지 확인한다**

```bash
cd backend
./gradlew test --tests com.dbidding.account.adapter.WalletProvisioningAdapterTest
```

Expected: `com.dbidding.account.adapter.WalletProvisioningAdapter`가 없어 test
compile 실패.

- [x] **Step 3: Account 소유 Adapter를 최소 구현한다**

`backend/src/main/java/com/dbidding/account/adapter/WalletProvisioningAdapter.java`를
다음과 같이 작성한다.

```java
package com.dbidding.account.adapter;

import org.springframework.stereotype.Component;

import com.dbidding.account.port.WalletProvisioningPort;
import com.dbidding.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WalletProvisioningAdapter implements WalletProvisioningPort {

    private final WalletService walletService;

    @Override
    public void createFor(Integer userId) {
        walletService.provision(userId);
    }
}
```

- [x] **Step 4: Wallet 소유의 기존 Adapter와 테스트를 삭제한다**

다음 두 파일을 삭제해 같은 Port 구현 Bean과 중복 테스트가 남지 않게 한다.

```text
backend/src/main/java/com/dbidding/wallet/adapter/WalletProvisioningAdapter.java
backend/src/test/java/com/dbidding/wallet/adapter/WalletProvisioningAdapterTest.java
```

- [x] **Step 5: Adapter 위임과 회원가입 원자성을 검증한다**

```bash
./gradlew test \
  --tests com.dbidding.account.adapter.WalletProvisioningAdapterTest \
  --tests com.dbidding.account.integration.SignupTransactionTest \
  --tests com.dbidding.account.service.AuthServiceSignupTest \
  --tests com.dbidding.wallet.service.WalletServiceProvisioningTest
```

Expected: Adapter 위임, Account·Wallet 동시 생성과 Wallet 실패 시 Account 롤백
테스트 모두 PASS.

- [x] **Step 6: Account Adapter 이동을 커밋한다**

```bash
git add \
  backend/src/main/java/com/dbidding/account/adapter/WalletProvisioningAdapter.java \
  backend/src/test/java/com/dbidding/account/adapter/WalletProvisioningAdapterTest.java \
  backend/src/main/java/com/dbidding/wallet/adapter/WalletProvisioningAdapter.java \
  backend/src/test/java/com/dbidding/wallet/adapter/WalletProvisioningAdapterTest.java
git commit -m "refactor: Wallet 생성 adapter를 Account로 이동"
```

### Task 2: Auction이 Wallet 자금 Adapter를 소유하도록 이동

**Files:**
- Create: `backend/src/main/java/com/dbidding/auction/adapter/AuctionWalletAdapter.java`
- Create: `backend/src/test/java/com/dbidding/auction/adapter/AuctionWalletAdapterTest.java`
- Delete: `backend/src/main/java/com/dbidding/wallet/adapter/AuctionWalletAdapter.java`
- Delete: `backend/src/test/java/com/dbidding/wallet/adapter/AuctionWalletAdapterTest.java`
- Verify: `backend/src/main/java/com/dbidding/auction/adapter/FakeWalletAdapter.java`
- Verify: `backend/src/test/java/com/dbidding/auction/service/AuctionServiceBidTest.java`
- Verify: `backend/src/test/java/com/dbidding/auction/service/AuctionServiceCloseTest.java`
- Verify: `backend/src/test/java/com/dbidding/auction/service/AuctionQueryServiceTest.java`

**Interfaces:**
- Consumes: `WalletService.getBalance()`, `hold()`, `release()`, `capture()`
- Implements: `WalletPort.getWallet()`, `holdBidAmount()`, `releaseBidHold()`, `confirmWinningBid()`
- Produces: `WalletPort.WalletSnapshot(long availableBalance, long frozenBalance)`
- Preserves: `@Profile("!auction-mock")` 실제 구현과 `@Profile("auction-mock")` Fake 구현의 상호 배타성

- [x] **Step 1: Auction 패키지의 실패 테스트를 작성한다**

기존 `AuctionWalletAdapterTest`의 네 테스트를
`backend/src/test/java/com/dbidding/auction/adapter/AuctionWalletAdapterTest.java`로
옮기고 package를 다음처럼 변경한다.

```java
package com.dbidding.auction.adapter;
```

테스트는 다음 네 가지 위임과 변환을 모두 유지한다.

```text
getWallet()            → WalletService.getBalance()
holdBidAmount()        → WalletService.hold()
releaseBidHold()       → WalletService.release()
confirmWinningBid()    → WalletService.capture()
```

- [x] **Step 2: 새 패키지의 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests com.dbidding.auction.adapter.AuctionWalletAdapterTest
```

Expected: `com.dbidding.auction.adapter.AuctionWalletAdapter`가 없어 test compile
실패.

- [x] **Step 3: Auction 소유 Adapter를 구현한다**

기존 구현을
`backend/src/main/java/com/dbidding/auction/adapter/AuctionWalletAdapter.java`로
옮기고 package만 다음과 같이 변경한다.

```java
package com.dbidding.auction.adapter;
```

다음 어노테이션과 의존성은 그대로 유지한다.

```java
@Component
@Profile("!auction-mock")
@RequiredArgsConstructor
public class AuctionWalletAdapter implements WalletPort {

    private final WalletService walletService;
}
```

각 Port 메서드는 `WalletService`의 대응 메서드를 한 번만 호출한다.
`WalletBalanceResponse`는 Adapter의 private `snapshot()`에서
`WalletPort.WalletSnapshot`으로 변환하며 total balance를 Auction 계약에 새로
노출하지 않는다.

- [x] **Step 4: Wallet 소유의 기존 Adapter와 테스트를 삭제한다**

```text
backend/src/main/java/com/dbidding/wallet/adapter/AuctionWalletAdapter.java
backend/src/test/java/com/dbidding/wallet/adapter/AuctionWalletAdapterTest.java
```

- [x] **Step 5: 실제·Mock Adapter와 Auction 호출 회귀를 검증한다**

```bash
./gradlew test \
  --tests com.dbidding.auction.adapter.AuctionWalletAdapterTest \
  --tests com.dbidding.auction.service.AuctionServiceBidTest \
  --tests com.dbidding.auction.service.AuctionServiceCloseTest \
  --tests com.dbidding.auction.service.AuctionQueryServiceTest \
  --tests com.dbidding.wallet.service.WalletServiceBalanceTest \
  --tests com.dbidding.wallet.service.WalletServiceHoldTest \
  --tests com.dbidding.wallet.service.WalletServiceCaptureTest \
  --tests com.dbidding.wallet.service.WalletCaptureIntegrationTest
```

Expected: Adapter의 네 위임·변환 테스트와 Auction 입찰·마감·조회, Wallet
hold·release·capture 회귀 테스트 모두 PASS.

- [x] **Step 6: Auction Adapter 이동을 커밋한다**

```bash
git add \
  backend/src/main/java/com/dbidding/auction/adapter/AuctionWalletAdapter.java \
  backend/src/test/java/com/dbidding/auction/adapter/AuctionWalletAdapterTest.java \
  backend/src/main/java/com/dbidding/wallet/adapter/AuctionWalletAdapter.java \
  backend/src/test/java/com/dbidding/wallet/adapter/AuctionWalletAdapterTest.java
git commit -m "refactor: Wallet 연동 adapter를 Auction으로 이동"
```

### Task 3: 의존 방향과 전체 회귀 검증

**Files:**
- Modify: `docs/hyeonmoon/README.md`
- Modify: `docs/hyeonmoon/wallet/README.md`
- Verify: `backend/src/main/java/com/dbidding/wallet/**`
- Verify: `backend/src/test/java/com/dbidding/wallet/**`

**Interfaces:**
- Verifies: Wallet main/test 소스에서 `com.dbidding.account`와 `com.dbidding.auction` 의존 제거
- Preserves: 전체 Backend HTTP API, Spring Bean 구성과 테스트 계약

- [x] **Step 1: Wallet의 역방향 의존이 제거됐는지 확인한다**

```bash
rg -n \
  'import com\.dbidding\.(account|auction)' \
  backend/src/main/java/com/dbidding/wallet \
  backend/src/test/java/com/dbidding/wallet
```

Expected: 출력 없음, exit code 1. `rg`의 exit code 1은 검색 결과가 없다는
뜻이므로 이 검증에서는 성공이다.

- [x] **Step 2: Adapter와 Port 위치를 확인한다**

```bash
test -f backend/src/main/java/com/dbidding/account/port/WalletProvisioningPort.java
test -f backend/src/main/java/com/dbidding/account/adapter/WalletProvisioningAdapter.java
test -f backend/src/main/java/com/dbidding/auction/port/WalletPort.java
test -f backend/src/main/java/com/dbidding/auction/adapter/AuctionWalletAdapter.java
test ! -d backend/src/main/java/com/dbidding/wallet/adapter
```

Expected: 모든 명령 exit code 0. Wallet의 `adapter` 디렉터리에 다른 파일이
추가되어 있다면 마지막 검증은 디렉터리 부재 대신 두 이전 Adapter 파일의 부재
검증으로 바꾼다.

- [x] **Step 3: 문서 인덱스의 현재 구조를 갱신한다**

`docs/hyeonmoon/wallet/README.md`에는 6번 단계로 이 문서를 연결하고 완료 후
상태를 `완료`로 바꾼다. `docs/hyeonmoon/README.md`의 패키지 경계 설명은
다음 원칙으로 갱신한다.

```text
Port와 연결 Adapter는 사용하는 도메인이 함께 소유한다.
Provider 도메인은 자신의 범용 Service, Entity, Repository를 소유한다.
로컬 Adapter는 현재 Provider Service를 호출하고, 분리 시 원격 Adapter로 교체한다.
```

기존 `wallet/2-wallet-provisioning.md`와
`wallet/5-auction-wallet-integration.md`는 당시 구현 판단을 남기는 역사 문서로
유지하며, 현재 구조는 이 문서가 대체한다고 각 문서의 본문을 다시 쓰지 않는다.

- [x] **Step 4: 전체 Backend 테스트를 실행한다**

```bash
./gradlew clean test
```

Expected: 모든 Backend 테스트 PASS. 특정 test task가 `NO-SOURCE`라면 해당
사실을 별도로 보고하고 테스트 통과로 표현하지 않는다.

- [x] **Step 5: 문서와 최종 검증을 커밋한다**

```bash
git add \
  docs/hyeonmoon/README.md \
  docs/hyeonmoon/wallet/README.md \
  docs/hyeonmoon/wallet/6-consumer-owned-port-adapter-refactor.md
git commit -m "docs: 소비자 소유 Wallet adapter 구조 반영"
```

## 완료 후 구조

```text
com.dbidding.account
├── adapter
│   └── WalletProvisioningAdapter
├── port
│   └── WalletProvisioningPort
└── service
    └── AuthService

com.dbidding.auction
├── adapter
│   ├── AuctionWalletAdapter
│   └── FakeWalletAdapter
├── port
│   └── WalletPort
└── service
    ├── AuctionCommandService
    └── AuctionQueryService

com.dbidding.wallet
├── controller
├── domain
├── dto
├── exception
├── repository
└── service
    └── WalletService
```

> 이 문서는 codex의 도움을 받아 작성하였습니다
