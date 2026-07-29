# Wallet Balance Query Implementation Plan

**Goal:** 로그인 사용자의 총 잔액, 활성 동결 합계, 가용 잔액을 조회한다.

**Architecture:** Wallet point를 총액으로 읽고 `wallet_holds`의 HELD 합계를 native aggregate query로 계산한다. 활성 hold의 `released_at`은 null이고, 해제 또는 낙찰 차감 시 실제 처리 시각을 기록한다.

**Tech Stack:** Spring Data JPA native query, Spring MVC, JUnit 5, Mockito, MockMvc

## Global Constraints

- 총액·동결액·가용액은 모두 Java `long`이다.
- `wallets.point`를 조회 중 변경하지 않는다.
- 동결액은 `status='HELD'` row만 합산한다.
- `held_amount` 집계 컬럼을 추가하지 않는다.

---

### Task 1: 활성 hold 합계 query

**Files:**
- Modify: `backend/src/main/java/com/dbidding/wallet/repository/WalletRepository.java`
- Test: `backend/src/test/java/com/dbidding/wallet/repository/WalletBalanceQueryTest.java`

**Interfaces:**
- Produces: `long WalletRepository.sumHeldAmount(Integer walletId)`

- [ ] **Step 1: hold가 없을 때 0 테스트**

Wallet만 저장하고 합계를 조회했을 때 null이 아니라 0이 반환되어야 한다.

- [ ] **Step 2: HELD만 합산하는 테스트**

테스트 fixture SQL로 같은 wallet에 HELD 10,000·20,000과 RELEASED 50,000을 넣고 결과가 30,000인지 검증한다.

- [ ] **Step 3: native aggregate query 작성**

```java
@Query(value = """
    SELECT COALESCE(SUM(wh.amount), 0)
    FROM wallet_holds wh
    WHERE wh.wallet_id = :walletId
      AND wh.status = 'HELD'
    """, nativeQuery = true)
long sumHeldAmount(Integer walletId);
```

HELD fixture의 `released_at`은 null로 두며, 잔액 판정은 `released_at`이 아니라 `status`를 기준으로 한다.

### Task 2: WalletBalanceService

**Files:**
- Create: `backend/src/main/java/com/dbidding/wallet/service/WalletBalanceService.java`
- Create: `backend/src/main/java/com/dbidding/wallet/dto/WalletBalanceResponse.java`
- Create: `backend/src/main/java/com/dbidding/wallet/exception/WalletNotFoundException.java`
- Create: `backend/src/main/java/com/dbidding/wallet/exception/InvalidWalletBalanceException.java`
- Test: `backend/src/test/java/com/dbidding/wallet/service/WalletBalanceServiceTest.java`

**Interfaces:**
- Produces: `WalletBalanceResponse getBalance(Integer userId)`

- [ ] **Step 1: 정상 계산 테스트**

```java
@Test
void 총액에서_활성_hold를_빼서_가용액을_계산한다() {
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

- [ ] **Step 2: Wallet 없음과 불변식 위반 테스트**

- Wallet이 없으면 `WalletNotFoundException`
- frozenBalance가 totalBalance보다 크면 음수를 반환하지 않고 `InvalidWalletBalanceException`

- [ ] **Step 3: readOnly 서비스 구현**

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

### Task 3: WalletController

**Files:**
- Create: `backend/src/main/java/com/dbidding/wallet/controller/WalletController.java`
- Test: `backend/src/test/java/com/dbidding/wallet/controller/WalletControllerTest.java`

**Interfaces:**
- Consumes: `@CurrentUser Integer userId`
- Produces: `GET /api/wallet`

- [ ] **Step 1: Controller 작성**

```java
@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    @GetMapping
    public WalletBalanceResponse getBalance(@CurrentUser Integer userId) {
        return walletBalanceService.getBalance(userId);
    }
}
```

- [ ] **Step 2: 응답 테스트**

```java
mockMvc.perform(get("/api/wallet"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.totalBalance").value(100000))
    .andExpect(jsonPath("$.frozenBalance").value(30000))
    .andExpect(jsonPath("$.availableBalance").value(70000));
```

- [ ] **Step 3: 전체 테스트 및 커밋**

```bash
./gradlew clean test
git add backend/src/main/java/com/dbidding/wallet backend/src/test/java/com/dbidding/wallet
git commit -m "feat: 지갑 잔액 조회 구현"
```

## 완료 조건

- hold가 없으면 frozenBalance는 0이다.
- RELEASED와 CAPTURED는 frozenBalance에 포함되지 않는다.
- 가용 잔액은 저장하지 않고 요청 시 계산한다.
- WalletHold 엔티티나 hold/release 로직을 이번 작업에 섞지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
