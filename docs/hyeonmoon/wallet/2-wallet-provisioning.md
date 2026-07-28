# Wallet Provisioning Implementation Plan

**Goal:** 회원가입 시 Auth가 Wallet 내부 구현을 몰라도 초기 잔액 0인 Wallet을 생성할 수 있게 한다.

**Architecture:** 사용하는 쪽인 Auth가 `WalletProvisioningPort`를 정의하고, 제공하는 쪽인 Wallet이 adapter를 구현한다. 두 모듈은 같은 datasource와 Spring transaction에 참여한다.

**Tech Stack:** Spring DI, Spring Transaction, JPA, JUnit 5, Mockito

## Global Constraints

- port는 `auth.port`에 있고 구현체는 `wallet`에 있다.
- Wallet 구현체는 AuthService나 User Entity를 참조하지 않는다.
- 별도 `REQUIRES_NEW` 트랜잭션을 만들지 않는다.
- 동일 userId의 Wallet을 조용히 중복 생성하지 않는다.
- 사전 조회 이후 발생한 `uk_wallets_user_id` 경쟁 충돌만
  `WalletAlreadyExistsException`으로 변환하고 다른 DB 오류는 그대로 전파한다.

---

### Task 1: WalletProvisioningAdapter

**Files:**
- Create: `backend/src/main/java/com/dbidding/auth/port/WalletProvisioningPort.java`
- Create: `backend/src/main/java/com/dbidding/wallet/adapter/WalletProvisioningAdapter.java`
- Create: `backend/src/main/java/com/dbidding/wallet/exception/WalletAlreadyExistsException.java`
- Test: `backend/src/test/java/com/dbidding/wallet/adapter/WalletProvisioningAdapterTest.java`

**Interfaces:**
- Produces: `void WalletProvisioningPort.createFor(Integer userId)`
- Produces: 초기 point 0인 Wallet 저장

- [x] **Step 1: Auth 소유 Port 작성**

```java
package com.dbidding.auth.port;

public interface WalletProvisioningPort {
    void createFor(Integer userId);
}
```

- [x] **Step 2: 생성 성공 테스트**

```java
@Test
void 사용자_ID로_잔액_0원_지갑을_생성한다() {
    adapter.createFor(1);

    then(walletRepository).should().save(argThat(wallet ->
        wallet.getUserId().equals(1) && wallet.getPoint() == 0L
    ));
}
```

- [x] **Step 3: 중복 생성 실패 테스트**

```java
given(walletRepository.existsByUserId(1)).willReturn(true);

assertThatThrownBy(() -> adapter.createFor(1))
    .isInstanceOf(WalletAlreadyExistsException.class);
```

- [x] **Step 4: 최소 구현**

```java
@Component
public class WalletProvisioningAdapter implements WalletProvisioningPort {
    private final WalletRepository walletRepository;

    @Override
    public void createFor(Integer userId) {
        if (walletRepository.existsByUserId(userId)) {
            throw new WalletAlreadyExistsException();
        }
        try {
            walletRepository.saveAndFlush(Wallet.open(userId));
        } catch (DataIntegrityViolationException exception) {
            if (isUserIdUniqueConstraintViolation(exception)) {
                throw new WalletAlreadyExistsException(exception);
            }
            throw exception;
        }
    }
}
```

`saveAndFlush()`로 이 메서드 안에서 INSERT를 실행해 UNIQUE 충돌을 잡는다.
Hibernate `ConstraintViolationException`의 제약조건명이
`uk_wallets_user_id`일 때만 중복 지갑 예외로 변환한다.

### Task 2: 단위 테스트와 커밋

**Files:**
- Test: `backend/src/test/java/com/dbidding/wallet/adapter/WalletProvisioningAdapterTest.java`

- [x] **Step 1: 테스트 실행**

```bash
./gradlew test --tests com.dbidding.wallet.adapter.WalletProvisioningAdapterTest
```

Expected: 생성 성공과 중복 생성 거절 시나리오 PASS.

- [x] **Step 2: 커밋**

```bash
git add backend/src/main/java/com/dbidding/auth/port/WalletProvisioningPort.java \
  backend/src/main/java/com/dbidding/wallet backend/src/test/java/com/dbidding/wallet
git commit -m "feat: 회원가입 Wallet 생성 연동"
```

회원가입 트랜잭션 통합은 AuthService와 UserAccountPort 구현이 필요한 후속
[회원가입 계획](../auth/2-signup.md)에서 검증한다.

## 완료 조건

- Auth는 Wallet Entity와 WalletRepository를 import하지 않는다.
- Auth 소유 Port를 Wallet adapter가 구현한다.
- Wallet adapter는 별도 트랜잭션을 열지 않아 호출자의 트랜잭션에 참여할 수 있다.
- 같은 userId로 Wallet을 중복 생성하지 않는다.
- 동시 중복 생성도 `WalletAlreadyExistsException`으로 일관되게 처리한다.
- userId UNIQUE 이외의 DB 무결성 예외는 숨기지 않는다.
- 초기 Wallet point는 항상 0이다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
