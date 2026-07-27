# Wallet Provisioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원가입 시 Auth가 Wallet 내부 구현을 몰라도 초기 잔액 0인 Wallet을 생성할 수 있게 한다.

**Architecture:** 사용하는 쪽인 Auth가 `WalletProvisioningPort`를 정의하고, 제공하는 쪽인 Wallet이 adapter를 구현한다. 두 모듈은 같은 datasource와 Spring transaction에 참여한다.

**Tech Stack:** Spring DI, Spring Transaction, JPA, JUnit 5, Mockito

## Global Constraints

- port는 `auth.port`에 있고 구현체는 `wallet`에 있다.
- Wallet 구현체는 AuthService나 User Entity를 참조하지 않는다.
- 별도 `REQUIRES_NEW` 트랜잭션을 만들지 않는다.
- 동일 userId의 Wallet을 조용히 중복 생성하지 않는다.

---

### Task 1: WalletProvisioningAdapter

**Files:**
- Consumes: `backend/src/main/java/com/dbidding/auth/port/WalletProvisioningPort.java`
- Create: `backend/src/main/java/com/dbidding/wallet/WalletProvisioningAdapter.java`
- Create: `backend/src/main/java/com/dbidding/wallet/exception/WalletAlreadyExistsException.java`
- Test: `backend/src/test/java/com/dbidding/wallet/WalletProvisioningAdapterTest.java`

**Interfaces:**
- Consumes: `void WalletProvisioningPort.createFor(Integer userId)`
- Produces: 초기 point 0인 Wallet 저장

- [ ] **Step 1: 생성 성공 테스트**

```java
@Test
void 사용자_ID로_잔액_0원_지갑을_생성한다() {
    adapter.createFor(1);

    then(walletRepository).should().save(argThat(wallet ->
        wallet.getUserId().equals(1) && wallet.getPoint() == 0L
    ));
}
```

- [ ] **Step 2: 중복 생성 실패 테스트**

```java
given(walletRepository.existsByUserId(1)).willReturn(true);

assertThatThrownBy(() -> adapter.createFor(1))
    .isInstanceOf(WalletAlreadyExistsException.class);
```

- [ ] **Step 3: 최소 구현**

```java
@Component
public class WalletProvisioningAdapter implements WalletProvisioningPort {
    private final WalletRepository walletRepository;

    @Override
    public void createFor(Integer userId) {
        if (walletRepository.existsByUserId(userId)) {
            throw new WalletAlreadyExistsException();
        }
        walletRepository.save(Wallet.open(userId));
    }
}
```

### Task 2: 회원가입 트랜잭션 통합 검증

**Files:**
- Test: `backend/src/test/java/com/dbidding/auth/SignupTransactionTest.java`

- [ ] **Step 1: 성공 통합 테스트**

실제 UserRepository와 WalletProvisioningAdapter를 사용해 회원가입 후 `users`와 `wallets`에 각각 한 row가 생성되는지 확인한다.

- [ ] **Step 2: Wallet 실패 시 User 롤백 테스트**

동일 userId Wallet 충돌 또는 테스트용 실패 adapter를 사용해 Wallet 생성 예외를 발생시킨다. 트랜잭션 종료 후 신규 User가 존재하지 않아야 한다.

- [ ] **Step 3: 테스트 실행**

```bash
DB_NAME=dbidding_test ./gradlew test --tests com.dbidding.auth.SignupTransactionTest
```

Expected: 성공·롤백 시나리오 PASS.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/dbidding/wallet backend/src/test/java/com/dbidding/wallet \
  backend/src/test/java/com/dbidding/auth/SignupTransactionTest.java
git commit -m "feat: 회원가입 Wallet 생성 연동"
```

## 완료 조건

- Auth는 Wallet Entity와 WalletRepository를 import하지 않는다.
- Wallet 생성은 Auth의 회원가입 트랜잭션에 참여한다.
- Wallet 생성 실패 시 User도 남지 않는다.
- 초기 Wallet point는 항상 0이다.

