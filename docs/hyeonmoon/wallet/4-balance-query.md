# Wallet Balance Query API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인 사용자의 총잔액, 활성 동결 합계, 가용 잔액을 조회한다.

**Architecture:** [충전·환불 계획](3-charge-and-refund.md)에서 만든 `WalletRepository.sumHeldAmount()`를 재사용한다. 총잔액은 `wallets.point`, 동결액은 HELD 합계, 가용액은 두 값의 차이이며 별도 집계 컬럼으로 저장하지 않는다.

**Tech Stack:** Spring Data JPA, Spring MVC, JUnit 5, Mockito, MockMvc

## Global Constraints

- 총잔액·동결액·가용액은 모두 Java `long`이다.
- 조회는 `wallets.point`나 hold 상태를 변경하지 않는다.
- 동결액은 `status='HELD'` row만 합산한다.
- `wallets.held_amount` 같은 중복 집계 컬럼을 추가하지 않는다.
- Wallet이 없으면 `WalletNotFoundException`을 반환한다.
- frozenBalance가 totalBalance보다 크면 손상된 상태로 보고 음수 가용액을 반환하지 않는다.

---

### Task 1: WalletBalanceService

**Files:**
- Create: `backend/src/main/java/com/dbidding/wallet/service/WalletBalanceService.java`
- Create: `backend/src/main/java/com/dbidding/wallet/dto/WalletBalanceResponse.java`
- Create: `backend/src/main/java/com/dbidding/wallet/exception/InvalidWalletBalanceException.java`
- Test: `backend/src/test/java/com/dbidding/wallet/service/WalletBalanceServiceTest.java`

**Interfaces:**
- Consumes: `WalletRepository.findByUserId(Integer userId)`
- Consumes: `WalletRepository.sumHeldAmount(Integer walletId)`
- Consumes: `WalletNotFoundException` from `3-charge-and-refund.md`
- Produces: `WalletBalanceResponse getBalance(Integer userId)`

- [ ] **Step 1: 정상 계산 테스트를 작성한다**

```java
@Test
void 총잔액에서_활성_hold를_빼서_가용액을_계산한다() {
    Wallet wallet = mock(Wallet.class);
    given(wallet.getId()).willReturn(10);
    given(wallet.getPoint()).willReturn(100_000L);
    given(walletRepository.findByUserId(1)).willReturn(Optional.of(wallet));
    given(walletRepository.sumHeldAmount(10)).willReturn(30_000L);

    WalletBalanceResponse result = service.getBalance(1);

    assertThat(result.totalBalance()).isEqualTo(100_000L);
    assertThat(result.frozenBalance()).isEqualTo(30_000L);
    assertThat(result.availableBalance()).isEqualTo(70_000L);
}
```

- [ ] **Step 2: Wallet 없음과 불변식 위반 테스트를 작성한다**

```java
@Test
void wallet이_없으면_404_도메인_예외다() {
    given(walletRepository.findByUserId(1)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getBalance(1))
        .isInstanceOf(WalletNotFoundException.class);
}

@Test
void 동결액이_총잔액보다_크면_손상된_잔액으로_거절한다() {
    Wallet wallet = mock(Wallet.class);
    given(wallet.getId()).willReturn(10);
    given(wallet.getPoint()).willReturn(10_000L);
    given(walletRepository.findByUserId(1)).willReturn(Optional.of(wallet));
    given(walletRepository.sumHeldAmount(10)).willReturn(10_001L);

    assertThatThrownBy(() -> service.getBalance(1))
        .isInstanceOf(InvalidWalletBalanceException.class);
}
```

- [ ] **Step 3: 실패 테스트를 실행한다**

```bash
./gradlew test --tests com.dbidding.wallet.service.WalletBalanceServiceTest
```

Expected: Service, Response, 예외가 없어 컴파일 실패.

- [ ] **Step 4: read-only 서비스를 구현한다**

```java
@Transactional(readOnly = true)
public WalletBalanceResponse getBalance(Integer userId) {
    Wallet wallet = walletRepository.findByUserId(userId)
        .orElseThrow(WalletNotFoundException::new);
    long frozen = walletRepository.sumHeldAmount(wallet.getId());
    long available = wallet.getPoint() - frozen;
    if (available < 0) {
        throw new InvalidWalletBalanceException();
    }
    return new WalletBalanceResponse(wallet.getPoint(), frozen, available);
}
```

- [ ] **Step 5: 서비스 테스트를 다시 실행하고 커밋한다**

```bash
./gradlew test --tests com.dbidding.wallet.service.WalletBalanceServiceTest
git add backend/src/main/java/com/dbidding/wallet/service \
  backend/src/main/java/com/dbidding/wallet/dto \
  backend/src/main/java/com/dbidding/wallet/exception \
  backend/src/test/java/com/dbidding/wallet/service
git commit -m "feat: Wallet 잔액 조회 서비스 구현"
```

### Task 2: WalletController

**Files:**
- Create: `backend/src/main/java/com/dbidding/wallet/controller/WalletController.java`
- Test: `backend/src/test/java/com/dbidding/wallet/controller/WalletControllerTest.java`

**Interfaces:**
- Consumes: `@CurrentUser Integer userId`
- Produces: `GET /api/wallet`

- [ ] **Step 1: 응답 테스트를 작성한다**

```java
@Test
void 로그인_사용자의_총액_동결액_가용액을_조회한다() throws Exception {
    given(walletBalanceService.getBalance(1))
        .willReturn(new WalletBalanceResponse(100_000L, 30_000L, 70_000L));

    mockMvc.perform(get("/api/wallet"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalBalance").value(100000))
        .andExpect(jsonPath("$.frozenBalance").value(30000))
        .andExpect(jsonPath("$.availableBalance").value(70000));
}
```

- [ ] **Step 2: 실패 테스트를 실행한다**

```bash
./gradlew test --tests com.dbidding.wallet.controller.WalletControllerTest
```

Expected: Controller가 없어 404 또는 컴파일 실패.

- [ ] **Step 3: Controller를 구현한다**

```java
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final WalletBalanceService walletBalanceService;

    @GetMapping
    public WalletBalanceResponse getBalance(@CurrentUser Integer userId) {
        return walletBalanceService.getBalance(userId);
    }
}
```

- [ ] **Step 4: Controller와 전체 테스트를 실행한다**

```bash
./gradlew test --tests com.dbidding.wallet.controller.WalletControllerTest
DB_PORT=3306 env -u JWT_SECRET ./gradlew clean test
```

Expected: 전체 테스트 실패 0건.

- [ ] **Step 5: API를 커밋한다**

```bash
git add backend/src/main/java/com/dbidding/wallet/controller \
  backend/src/test/java/com/dbidding/wallet/controller \
  docs/hyeonmoon/wallet/4-balance-query.md
git commit -m "feat: Wallet 잔액 조회 API 구현"
```

## 완료 조건

- hold가 없으면 frozenBalance는 0이다.
- RELEASED와 CAPTURED는 frozenBalance에 포함되지 않는다.
- 가용 잔액은 저장하지 않고 요청 시 계산한다.
- 조회 API는 URL이나 요청 본문에서 userId를 받지 않는다.
- 조회 중 Wallet 또는 WalletHold를 변경하지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
