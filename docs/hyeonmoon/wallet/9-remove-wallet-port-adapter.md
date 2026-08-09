# Wallet로 향하는 남은 Port·Adapter 제거 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wallet을 호출하려고 만들어졌던 Port·Adapter 중 아직 남아 있는 것들을 걷어낸다. 이슈 [#285](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/285). PR [#240](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/pull/240)(Auction→Wallet)과 이슈 #237(Order→Wallet, [8-order-settlement-and-refund.md](8-order-settlement-and-refund.md) Task 3)에서 같은 이유로 이미 두 번 반복한 정리를 나머지 대상에도 적용한다.

**Architecture:** 조사 결과 대상은 둘로 나뉜다.

1. **완전히 죽은 코드**: `auction.port.WalletPort` + `auction.adapter.FakeWalletAdapter`. PR #240이
   `AuctionCommandService`를 `WalletService` 직접 호출로 바꾸면서 이 Port를 쓰는 곳이 전혀
   안 남았는데, 인터페이스와 Fake 구현 파일 자체는 안 지워지고 남아 있다.
2. **아직 쓰이는 중인 Port**: `account.port.WalletProvisioningPort` +
   `account.adapter.WalletProvisioningAdapter`. `AuthTransactionService.createAccountWithWallet()`이
   회원가입 트랜잭션 안에서 이 Port로 Wallet 생성을 요청한다. 대체 구현(mock/fake)이 없는 단일
   구현체라 Port로 감쌀 필요가 없다 — `AuthTransactionService`가 `WalletService.provision(userId)`를
   직접 부르면 된다.

`order.port.OrderEventPort`, `auction.event.AuctionEventPublisher`처럼 실제로 프로필별 대체 구현이
있는 이벤트 발행 Port는 이번 범위에 포함하지 않는다 — Wallet을 직접 호출하는 Port만 대상이다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, JUnit 5, Mockito

## 조사 근거

```bash
cd backend
grep -rln "WalletPort" src/main src/test
# -> auction/adapter/FakeWalletAdapter.java, auction/port/WalletPort.java  (이 둘뿐)
```

`WalletPort` 타입을 필드나 파라미터로 갖는 클래스가 전혀 없다 — `AuctionCommandService`는 이미
`WalletService`를 직접 주입받는다. `FakeWalletAdapter`를 검증하는 테스트도 없다.

```bash
grep -rln "WalletProvisioningPort" src/main src/test
# -> account/adapter/WalletProvisioningAdapter.java (구현)
# -> account/service/AuthTransactionService.java (유일한 소비자)
# -> account/port/WalletProvisioningPort.java (정의)
# -> 테스트 4개: AuthServiceSignupTest, AuthServiceRefreshTest,
#    SignupTransactionTest, AuthTransactionScopeTest
```

`WalletProvisioningPort`는 `WalletProvisioningAdapter` 하나만 구현하고(`@Profile` 분기 없음),
`AuthTransactionService` 하나만 소비한다 — 대체 프로필이 필요 없는 단일 구현 Port라
`WalletSettlementPort`/`WalletPort`와 같은 처지다.

## Global Constraints

- `WalletService`의 public 메서드 시그니처(`provision`, `hold`, `release`, `capture`, `charge`,
  `refund`, `settle`, `cancelRefund`)는 변경하지 않는다.
- 회원가입 트랜잭션 계약(Account 저장과 Wallet 생성이 한 트랜잭션, 위반 시 함께 롤백)은
  변경하지 않는다 — `AuthTransactionScopeTest`가 이미 이 계약을 검증한다.
- `/api/auth/**` HTTP 계약, JWT, 쿠키는 변경하지 않는다.
- `auction-mock` Spring 프로필 자체는 유지한다 — `FakeWalletAdapter`만 제거하고,
  `MockAuctionCardAdapter`/`MockImageUploadAdapter`/`InMemoryAuctionEventAdapter` 등 같은
  프로필의 다른 mock 빈은 건드리지 않는다.
- `Wallet`의 Repository·Entity는 여전히 다른 도메인이 직접 import하지 않는다 — 이번 정리로
  `AuthTransactionService`가 얻는 건 `WalletService`(서비스)까지고, `WalletRepository`나
  `Wallet` 엔티티는 아니다.

---

### Task 1: 죽은 Auction→Wallet Port를 삭제한다

**Files:**
- Delete: `backend/src/main/java/com/dbidding/auction/port/WalletPort.java`
- Delete: `backend/src/main/java/com/dbidding/auction/adapter/FakeWalletAdapter.java`

**Interfaces:**
- Removes: `auction.port.WalletPort`, `auction.adapter.FakeWalletAdapter`
- Preserves: `auction-mock` 프로필의 나머지 mock 빈, `AuctionCommandService`의 기존 `WalletService`
  직접 의존(변경 없음)

- [ ] **Step 1: 삭제 전 마지막으로 사용처가 없는지 재확인한다**

```bash
cd backend
grep -rn "WalletPort\b" src/main src/test
```

Expected: 두 파일 자기 자신의 선언/구현부 외에는 히트 없음.

- [ ] **Step 2: 두 파일을 삭제하고 컴파일을 확인한다**

```bash
git rm backend/src/main/java/com/dbidding/auction/port/WalletPort.java \
  backend/src/main/java/com/dbidding/auction/adapter/FakeWalletAdapter.java
./gradlew compileJava compileTestJava
```

- [ ] **Step 3: auction-mock 프로필 관련 테스트를 재실행한다**

```bash
./gradlew test --tests 'com.dbidding.auction.config.AuctionProfileConfigurationTest' \
  --tests 'com.dbidding.auction.config.AuctionSchedulingConfigurationTest'
```

Expected: 실패 0건 — 이 테스트들은 애초에 `WalletPort`/`FakeWalletAdapter`를 참조하지 않았다.

- [ ] **Step 4: 커밋한다**

```bash
git commit -m "chore: 사용되지 않는 Auction WalletPort·FakeWalletAdapter 제거"
```

### Task 2: Account의 WalletProvisioningPort를 직접 호출로 교체한다

**Files:**
- Delete: `backend/src/main/java/com/dbidding/account/port/WalletProvisioningPort.java`
- Delete: `backend/src/main/java/com/dbidding/account/adapter/WalletProvisioningAdapter.java`
- Modify: `backend/src/main/java/com/dbidding/account/service/AuthTransactionService.java`
- Modify: `backend/src/test/java/com/dbidding/account/service/AuthServiceSignupTest.java`
- Modify: `backend/src/test/java/com/dbidding/account/service/AuthServiceRefreshTest.java`
- Modify: `backend/src/test/java/com/dbidding/account/integration/SignupTransactionTest.java`
- Modify: `backend/src/test/java/com/dbidding/account/integration/AuthTransactionScopeTest.java`

**Interfaces:**
- Removes: `account.port.WalletProvisioningPort`, `account.adapter.WalletProvisioningAdapter`
- Modifies: `AuthTransactionService`의 필드를 `WalletProvisioningPort walletProvisioningPort` →
  `WalletService walletService`로 교체, `walletProvisioningPort.createFor(id)` 호출을
  `walletService.provision(id)`로 교체
- Preserves: `createAccountWithWallet(...)`의 트랜잭션 경계와 예외 변환(`DuplicateEmailException`/
  `DuplicateNicknameException`) 동작

- [ ] **Step 1: 단위 테스트 2개를 WalletService 목으로 바꾼다**

`AuthServiceSignupTest`, `AuthServiceRefreshTest`에서 `@Mock private WalletProvisioningPort
walletProvisioningPort`를 `@Mock private WalletService walletService`로 바꾸고,
`verify(walletProvisioningPort).createFor(id)`류 검증을 `verify(walletService).provision(id)`로
바꾼다.

- [ ] **Step 2: 통합 테스트 2개를 WalletService 스파이로 바꾼다**

`SignupTransactionTest`, `AuthTransactionScopeTest`에서 `@MockitoSpyBean private
WalletProvisioningPort walletProvisioningPort`를 `@MockitoSpyBean private WalletService
walletService`로 바꾸고, `doAnswer`/`doThrow` 등 스텁 대상 메서드를 `createFor(...)`에서
`provision(...)`으로 바꾼다. 트랜잭션 활성 여부를 검증하는 로직 자체는 그대로 둔다.

- [ ] **Step 3: 테스트가 컴파일 실패로 막히는지 확인한다**

```bash
cd backend
./gradlew compileTestJava
```

Expected: `AuthTransactionService` 생성자가 아직 `WalletProvisioningPort`를 받아서 타입 불일치로
컴파일 실패.

- [ ] **Step 4: AuthTransactionService가 WalletService를 직접 호출하도록 바꾸고 Port·Adapter를 삭제한다**

```java
private final AccountRepository accountRepository;
private final WalletService walletService;

...
walletService.provision(account.getId());
```

`WalletProvisioningPort`, `WalletProvisioningAdapter`를 삭제한다.

- [ ] **Step 5: Account·Wallet 관련 테스트를 재실행한다**

```bash
./gradlew test \
  --tests 'com.dbidding.account.service.AuthServiceSignupTest' \
  --tests 'com.dbidding.account.service.AuthServiceRefreshTest' \
  --tests 'com.dbidding.account.integration.SignupTransactionTest' \
  --tests 'com.dbidding.account.integration.AuthTransactionScopeTest'
```

- [ ] **Step 6: 커밋한다**

```bash
git add backend/src/main/java/com/dbidding/account/service/AuthTransactionService.java \
  backend/src/test/java/com/dbidding/account/service/AuthServiceSignupTest.java \
  backend/src/test/java/com/dbidding/account/service/AuthServiceRefreshTest.java \
  backend/src/test/java/com/dbidding/account/integration/SignupTransactionTest.java \
  backend/src/test/java/com/dbidding/account/integration/AuthTransactionScopeTest.java
git rm backend/src/main/java/com/dbidding/account/port/WalletProvisioningPort.java \
  backend/src/main/java/com/dbidding/account/adapter/WalletProvisioningAdapter.java
git commit -m "refactor: Account의 지갑 발급을 Port·Adapter 없이 직접 호출로 교체"
```

### Task 3: 문서 정리와 전체 회귀 검증

**Files:**
- Modify: `docs/hyeonmoon/wallet/README.md`

- [ ] **Step 1: 전체 백엔드 테스트를 실행한다**

```bash
cd backend
./gradlew clean test
```

Expected: 실패 0건. 테스트 소스가 없는 패턴은 통과로 표현하지 않고, 이번 변경과 무관한 기존
실패가 있으면 별도로 명시한다.

- [ ] **Step 2: Wallet README 구현 단계 목록을 갱신한다**

`docs/hyeonmoon/wallet/README.md`의 구현 단계 목록에 이번 문서를 추가한다. 문서
[6-consumer-owned-port-adapter-refactor.md](6-consumer-owned-port-adapter-refactor.md)가 원래
"Port·Adapter로 경계를 나누자"는 방향이었던 것과 이번 문서가 반대 방향(대체 구현이 없는 Port는
걷어낸다)이라는 점을 README에 짧게 남긴다 — 두 문서가 모순돼 보이지 않도록, "대체 구현이 필요한
이벤트 발행/실제 정책은 Port 유지, 단일 구현으로 굳어진 값 호출은 직접 호출"이라는 기준을 명시한다.

- [ ] **Step 3: 커밋한다**

```bash
git add docs/hyeonmoon/wallet/README.md
git commit -m "docs: Wallet Port·Adapter 잔여 구조 제거 문서 반영"
```

## 완료 조건

- `auction.port.WalletPort`, `auction.adapter.FakeWalletAdapter`가 더 이상 존재하지 않는다.
- `account.port.WalletProvisioningPort`, `account.adapter.WalletProvisioningAdapter`가 더 이상
  존재하지 않는다.
- `AuthTransactionService`가 `WalletService.provision(...)`을 직접 호출하고, 회원가입 트랜잭션
  계약(Account 저장 실패 시 Wallet도 생성되지 않음, 반대도 마찬가지)은 그대로 유지된다.
- `auction-mock` 프로필의 나머지 mock 빈은 영향받지 않는다.
- 전체 백엔드 테스트가 실패 없이 통과한다.

> 이 문서는 AI의 도움을 받아 작성하였습니다
