# Wallet Charge and Refund Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실제 PG 없이 로그인 사용자의 포인트를 충전·환불하고, 모든 실제 잔액 변경을 멱등한 감사 원장으로 남긴다.

**Architecture:** `wallets.point`는 현재 총잔액 스냅샷으로 유지하고 `point_records`는 append-only 감사 원장으로 사용한다. 충전·환불은 wallet row를 비관적 쓰기 잠금으로 조회한 뒤 같은 트랜잭션에서 잔액과 원장을 함께 변경한다. `Idempotency-Key`는 Wallet별로 유일하며 같은 요청의 재시도는 기존 결과를 반환한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, MySQL 8.4, JUnit 5, Mockito, MockMvc

## Global Constraints

- 실제 PG 승인·취소는 P1이며 이번 단계에서는 내부 포인트만 증감한다.
- 충전 최소 금액은 1,000원이고 환불 금액은 0원보다 커야 한다.
- 환불은 `wallets.point - SUM(wallet_holds.amount WHERE status='HELD')`로 계산한 가용 잔액까지만 허용한다.
- `point_records.amount`는 충전이 양수, 환불·낙찰 차감이 음수다.
- `point_records.balance`는 거래 반영 직후의 `wallets.point`다.
- 충전·환불의 `point_records.auction_id`는 null이다.
- 충전·환불 API는 비어 있지 않은 최대 64자의 `Idempotency-Key`를 필수로 받는다.
- 동일 키·동일 거래 유형·동일 금액은 최초 결과를 반환하고 잔액을 다시 변경하지 않는다.
- 동일 키를 다른 거래 유형이나 금액으로 재사용하면 `IdempotencyConflictException`으로 거절한다.
- Wallet Entity와 Repository는 다른 도메인의 Entity나 Repository를 import하지 않는다.

---

### Task 1: PointRecord 원장과 idempotency schema

**Files:**
- Modify: `backend/src/main/resources/schema.sql`
- Create: `backend/src/main/java/com/dbidding/wallet/domain/PointTransactionType.java`
- Create: `backend/src/main/java/com/dbidding/wallet/domain/PointRecord.java`
- Modify: `backend/src/main/java/com/dbidding/wallet/domain/Wallet.java`
- Test: `backend/src/test/java/com/dbidding/wallet/domain/PointRecordTest.java`
- Test: `backend/src/test/java/com/dbidding/wallet/domain/WalletTest.java`

**Interfaces:**
- Produces: `PointTransactionType.CHARGE`
- Produces: `PointTransactionType.REFUND`
- Produces: `PointTransactionType.AUCTION_CAPTURE`
- Produces: `PointRecord.charge(Integer walletId, long amount, long balance, String idempotencyKey)`
- Produces: `PointRecord.refund(Integer walletId, long amount, long balance, String idempotencyKey)`
- Produces: `PointRecord.auctionCapture(Integer walletId, Integer auctionId, long amount, long balance)`
- Produces: `void Wallet.credit(long amount)`
- Produces: `void Wallet.debit(long amount)`

- [x] **Step 1: schema에 idempotency key를 추가한다**

`point_records`에 다음 컬럼과 제약을 추가한다. 기존 기록과 낙찰 차감은 key가 없으므로 null을 허용하며, MySQL UNIQUE 인덱스는 여러 null을 허용한다.

```sql
idempotency_key VARCHAR(64)
    CHARACTER SET ascii COLLATE ascii_bin NULL,

CONSTRAINT uk_point_records_wallet_idempotency
    UNIQUE (wallet_id, idempotency_key)
```

- [x] **Step 2: 원장의 금액 부호와 잔액 변경 테스트를 작성한다**

```java
@Test
void 충전은_양수이고_환불과_낙찰차감은_음수다() {
    PointRecord charge = PointRecord.charge(1, 10_000L, 10_000L, "charge-key");
    PointRecord refund = PointRecord.refund(1, 3_000L, 7_000L, "refund-key");
    PointRecord capture = PointRecord.auctionCapture(1, 20, 5_000L, 2_000L);

    assertThat(charge.getAmount()).isEqualTo(10_000L);
    assertThat(refund.getAmount()).isEqualTo(-3_000L);
    assertThat(capture.getAmount()).isEqualTo(-5_000L);
    assertThat(capture.getAuctionId()).isEqualTo(20);
}

@Test
void 지갑은_양수_금액만_증감할_수_있고_잔액은_음수가_될_수_없다() {
    Wallet wallet = Wallet.open(1);
    wallet.credit(10_000L);
    wallet.debit(3_000L);

    assertThat(wallet.getPoint()).isEqualTo(7_000L);
    assertThatThrownBy(() -> wallet.debit(8_000L))
        .isInstanceOf(IllegalStateException.class);
}
```

- [x] **Step 3: 실패 테스트를 실행한다**

```bash
cd backend
./gradlew test \
  --tests com.dbidding.wallet.domain.PointRecordTest \
  --tests com.dbidding.wallet.domain.WalletTest
```

Expected: `PointRecord`, `PointTransactionType`, `Wallet.credit/debit`가 없어 컴파일 실패.

- [x] **Step 4: PointRecord와 Wallet 행위를 구현한다**

`PointRecord`는 setter 없이 정적 팩터리만 제공한다. 요청 금액은 양수로 받고 팩터리에서 원장 부호를 결정한다. `Wallet.credit()`는 `Math.addExact`, `Wallet.debit()`는 양수 검증과 잔액 부족 검증을 수행한다.

- [x] **Step 5: 도메인 테스트를 다시 실행한다**

```bash
./gradlew test \
  --tests com.dbidding.wallet.domain.PointRecordTest \
  --tests com.dbidding.wallet.domain.WalletTest
```

Expected: PASS.

- [x] **Step 6: 첫 구현 단위를 커밋한다**

```bash
git add backend/src/main/resources/schema.sql \
  backend/src/main/java/com/dbidding/wallet/domain \
  backend/src/test/java/com/dbidding/wallet/domain
git commit -m "feat: Wallet 포인트 원장 모델 추가"
```

### Task 2: Wallet 잠금과 원장 Repository

**Files:**
- Modify: `backend/src/main/java/com/dbidding/wallet/repository/WalletRepository.java`
- Create: `backend/src/main/java/com/dbidding/wallet/repository/PointRecordRepository.java`
- Test: `backend/src/test/java/com/dbidding/wallet/repository/WalletLedgerRepositoryTest.java`

**Interfaces:**
- Produces: `Optional<Wallet> WalletRepository.findByUserIdForUpdate(Integer userId)`
- Produces: `long WalletRepository.sumHeldAmount(Integer walletId)`
- Produces: `Optional<PointRecord> PointRecordRepository.findByWalletIdAndIdempotencyKey(Integer walletId, String idempotencyKey)`

- [x] **Step 1: 실제 MySQL Repository 테스트를 작성한다**

```java
@Test
void HELD_금액만_합산하고_idempotency_key로_원장을_조회한다() {
    Wallet wallet = walletRepository.saveAndFlush(Wallet.open(userId));
    jdbcTemplate.update("""
        INSERT INTO wallet_holds(wallet_id, auction_id, amount, status)
        VALUES (?, ?, 3000, 'HELD'), (?, ?, 9000, 'RELEASED')
        """, wallet.getId(), auctionId, wallet.getId(), auctionId);
    PointRecord record = pointRecordRepository.saveAndFlush(
        PointRecord.charge(wallet.getId(), 10_000L, 10_000L, "charge-key")
    );

    assertThat(walletRepository.sumHeldAmount(wallet.getId())).isEqualTo(3_000L);
    assertThat(pointRecordRepository.findByWalletIdAndIdempotencyKey(
        wallet.getId(), "charge-key"
    )).contains(record);
}
```

- [x] **Step 2: 실패 테스트를 실행한다**

```bash
DB_PORT=3306 ./gradlew test \
  --tests com.dbidding.wallet.repository.WalletLedgerRepositoryTest
```

Expected: Repository 메서드가 없어 컴파일 실패.

- [x] **Step 3: 잠금 조회와 합계 query를 구현한다**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select wallet from Wallet wallet where wallet.userId = :userId")
Optional<Wallet> findByUserIdForUpdate(@Param("userId") Integer userId);

@Query(value = """
    SELECT COALESCE(SUM(wallet_hold.amount), 0)
    FROM wallet_holds wallet_hold
    WHERE wallet_hold.wallet_id = :walletId
      AND wallet_hold.status = 'HELD'
    """, nativeQuery = true)
long sumHeldAmount(@Param("walletId") Integer walletId);
```

`PointRecordRepository`는 `JpaRepository<PointRecord, Long>`를 확장한다.

- [x] **Step 4: Repository 테스트를 다시 실행한다**

```bash
DB_PORT=3306 ./gradlew test \
  --tests com.dbidding.wallet.repository.WalletLedgerRepositoryTest
```

Expected: PASS.

- [x] **Step 5: Repository 구현을 커밋한다**

```bash
git add backend/src/main/java/com/dbidding/wallet/repository \
  backend/src/test/java/com/dbidding/wallet/repository
git commit -m "feat: Wallet 잠금과 원장 조회 추가"
```

### Task 3: 멱등 충전·환불 서비스

**Files:**
- Create: `backend/src/main/java/com/dbidding/wallet/service/WalletTransactionService.java`
- Create: `backend/src/main/java/com/dbidding/wallet/dto/WalletTransactionRequest.java`
- Create: `backend/src/main/java/com/dbidding/wallet/dto/WalletTransactionResponse.java`
- Create: `backend/src/main/java/com/dbidding/wallet/exception/WalletNotFoundException.java`
- Create: `backend/src/main/java/com/dbidding/wallet/exception/InvalidWalletAmountException.java`
- Create: `backend/src/main/java/com/dbidding/wallet/exception/InsufficientAvailableBalanceException.java`
- Create: `backend/src/main/java/com/dbidding/wallet/exception/IdempotencyConflictException.java`
- Test: `backend/src/test/java/com/dbidding/wallet/service/WalletTransactionServiceTest.java`

**Interfaces:**
- Consumes: `WalletRepository.findByUserIdForUpdate(Integer userId)`
- Consumes: `WalletRepository.sumHeldAmount(Integer walletId)`
- Consumes: `PointRecordRepository.findByWalletIdAndIdempotencyKey(Integer walletId, String key)`
- Produces: `WalletTransactionRequest(long amount)`
- Produces: `WalletTransactionResponse(Long transactionId, String transactionType, long amount, long balance)`
- Produces: `WalletTransactionResponse charge(Integer userId, long amount, String idempotencyKey)`
- Produces: `WalletTransactionResponse refund(Integer userId, long amount, String idempotencyKey)`

```java
public record WalletTransactionRequest(@Positive long amount) {
}

public record WalletTransactionResponse(
    Long transactionId,
    String transactionType,
    long amount,
    long balance
) {
    public static WalletTransactionResponse from(PointRecord record) {
        return new WalletTransactionResponse(
            record.getId(),
            record.getTransactionType().name(),
            record.getAmount(),
            record.getBalance()
        );
    }
}
```

- [x] **Step 1: 충전 성공과 최소 금액 테스트를 작성한다**

```java
@Test
void 충전하면_잠긴_wallet과_CHARGE_원장을_같이_변경한다() {
    Wallet wallet = walletWithPoint(0L);
    given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
    given(pointRecordRepository.save(any(PointRecord.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    WalletTransactionResponse response = service.charge(1, 10_000L, "charge-key");

    assertThat(wallet.getPoint()).isEqualTo(10_000L);
    assertThat(response.amount()).isEqualTo(10_000L);
    then(pointRecordRepository).should().save(argThat(record ->
        record.getTransactionType() == PointTransactionType.CHARGE
            && record.getAmount() == 10_000L
            && record.getBalance() == 10_000L
    ));
}

@Test
void 충전_금액이_1000원_미만이면_거절한다() {
    assertThatThrownBy(() -> service.charge(1, 999L, "charge-key"))
        .isInstanceOf(InvalidWalletAmountException.class);
}
```

- [x] **Step 2: 환불의 가용 잔액 검증 테스트를 작성한다**

```java
@Test
void 환불은_활성_hold를_제외한_가용_잔액까지만_허용한다() {
    Wallet wallet = walletWithPoint(10_000L);
    given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
    given(walletRepository.sumHeldAmount(wallet.getId())).willReturn(7_000L);
    given(pointRecordRepository.save(any(PointRecord.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(() -> service.refund(1, 3_001L, "refund-key"))
        .isInstanceOf(InsufficientAvailableBalanceException.class);

    WalletTransactionResponse response = service.refund(1, 3_000L, "refund-key-2");
    assertThat(wallet.getPoint()).isEqualTo(7_000L);
    assertThat(response.amount()).isEqualTo(-3_000L);
}
```

- [x] **Step 3: Idempotency-Key 재사용 테스트를 작성한다**

```java
@Test
void 동일한_key와_요청은_기존_결과를_반환한다() {
    Wallet wallet = walletWithPoint(10_000L);
    PointRecord existing = PointRecord.charge(
        wallet.getId(), 10_000L, 10_000L, "same-key"
    );
    given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
    given(pointRecordRepository.findByWalletIdAndIdempotencyKey(
        wallet.getId(), "same-key"
    )).willReturn(Optional.of(existing));

    WalletTransactionResponse response = service.charge(1, 10_000L, "same-key");

    assertThat(response.balance()).isEqualTo(10_000L);
    then(pointRecordRepository).should(never()).save(any());
}

@Test
void 동일한_key를_다른_금액에_사용하면_거절한다() {
    Wallet wallet = walletWithPoint(10_000L);
    PointRecord existing = PointRecord.charge(
        wallet.getId(), 10_000L, 10_000L, "same-key"
    );
    given(walletRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
    given(pointRecordRepository.findByWalletIdAndIdempotencyKey(
        wallet.getId(), "same-key"
    )).willReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.charge(1, 20_000L, "same-key"))
        .isInstanceOf(IdempotencyConflictException.class);
}
```

테스트에서 DB가 부여하는 Wallet ID만 대체하고 실제 잔액 증감 행위는 그대로
검증하도록 다음 helper를 테스트 클래스에 둔다.

```java
private Wallet walletWithPoint(long point) {
    Wallet wallet = spy(Wallet.open(1));
    given(wallet.getId()).willReturn(10);
    if (point > 0) {
        wallet.credit(point);
    }
    return wallet;
}
```

- [x] **Step 4: 서비스 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests com.dbidding.wallet.service.WalletTransactionServiceTest
```

Expected: 서비스와 DTO·예외가 없어 컴파일 실패.

- [x] **Step 5: 트랜잭션 서비스를 최소 구현한다**

두 메서드 모두 `@Transactional`을 사용한다. 처리 순서는 입력 검증 → wallet row 잠금 → 기존 idempotency 원장 확인 → 환불이면 HELD 합계 확인 → Wallet 변경 → PointRecord 저장이다.
`WalletNotFoundException`은 404, `InvalidWalletAmountException`은 400,
`InsufficientAvailableBalanceException`과 `IdempotencyConflictException`은
409로 매핑한다.

```java
@Transactional
public WalletTransactionResponse refund(
    Integer userId,
    long amount,
    String idempotencyKey
) {
    validatePositive(amount);
    Wallet wallet = lockWallet(userId);
    Optional<PointRecord> existing = findReplay(wallet, idempotencyKey);
    if (existing.isPresent()) {
        return replayOrThrow(existing.get(), PointTransactionType.REFUND, -amount);
    }

    long available = wallet.getPoint() - walletRepository.sumHeldAmount(wallet.getId());
    if (available < amount) {
        throw new InsufficientAvailableBalanceException();
    }
    wallet.debit(amount);
    PointRecord record = pointRecordRepository.save(
        PointRecord.refund(wallet.getId(), amount, wallet.getPoint(), idempotencyKey)
    );
    return WalletTransactionResponse.from(record);
}
```

- [x] **Step 6: 서비스 테스트를 다시 실행한다**

```bash
./gradlew test --tests com.dbidding.wallet.service.WalletTransactionServiceTest
```

Expected: PASS.

- [x] **Step 7: 서비스를 커밋한다**

```bash
git add backend/src/main/java/com/dbidding/wallet/service \
  backend/src/main/java/com/dbidding/wallet/dto \
  backend/src/main/java/com/dbidding/wallet/exception \
  backend/src/test/java/com/dbidding/wallet/service
git commit -m "feat: 모의 Wallet 충전과 환불 구현"
```

### Task 4: 충전·환불 API

**Files:**
- Create: `backend/src/main/java/com/dbidding/wallet/controller/WalletTransactionController.java`
- Test: `backend/src/test/java/com/dbidding/wallet/controller/WalletTransactionControllerTest.java`

**Interfaces:**
- Consumes: `@CurrentUser Integer userId`
- Consumes: `Idempotency-Key` request header
- Produces: `POST /api/wallet/charges`
- Produces: `POST /api/wallet/refunds`

- [x] **Step 1: 충전·환불 MockMvc 테스트를 작성한다**

```java
@Test
void 로그인_사용자가_idempotency_key로_포인트를_충전한다() throws Exception {
    given(service.charge(1, 10_000L, "charge-key"))
        .willReturn(new WalletTransactionResponse(
            10L, "CHARGE", 10_000L, 10_000L
        ));

    mockMvc.perform(post("/api/wallet/charges")
            .header("Idempotency-Key", "charge-key")
            .contentType(APPLICATION_JSON)
            .content("{\"amount\":10000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionType").value("CHARGE"))
        .andExpect(jsonPath("$.amount").value(10000))
        .andExpect(jsonPath("$.balance").value(10000));
}

@Test
void idempotency_key가_없으면_400이다() throws Exception {
    mockMvc.perform(post("/api/wallet/refunds")
            .contentType(APPLICATION_JSON)
            .content("{\"amount\":1000}"))
        .andExpect(status().isBadRequest());
}
```

- [x] **Step 2: 실패 테스트를 실행한다**

```bash
./gradlew test \
  --tests com.dbidding.wallet.controller.WalletTransactionControllerTest
```

Expected: Controller가 없어 404 또는 컴파일 실패.

- [x] **Step 3: Controller를 구현한다**

```java
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletTransactionController {
    private final WalletTransactionService walletTransactionService;

    @PostMapping("/charges")
    public WalletTransactionResponse charge(
        @CurrentUser Integer userId,
        @RequestHeader("Idempotency-Key")
        @NotBlank @Size(max = 64) String idempotencyKey,
        @Valid @RequestBody WalletTransactionRequest request
    ) {
        return walletTransactionService.charge(
            userId, request.amount(), idempotencyKey
        );
    }

    @PostMapping("/refunds")
    public WalletTransactionResponse refund(
        @CurrentUser Integer userId,
        @RequestHeader("Idempotency-Key")
        @NotBlank @Size(max = 64) String idempotencyKey,
        @Valid @RequestBody WalletTransactionRequest request
    ) {
        return walletTransactionService.refund(
            userId, request.amount(), idempotencyKey
        );
    }
}
```

헤더의 빈 문자열은 Controller 또는 서비스 검증에서 400으로 변환한다. 실제 PG가 없다는 사실은 API 문서와 프론트 문구에 명시하되 응답 모델에는 가짜 결제수단 정보를 추가하지 않는다.

- [x] **Step 4: Controller 테스트를 다시 실행한다**

```bash
./gradlew test \
  --tests com.dbidding.wallet.controller.WalletTransactionControllerTest
```

Expected: PASS.

- [x] **Step 5: API를 커밋한다**

```bash
git add backend/src/main/java/com/dbidding/wallet/controller \
  backend/src/test/java/com/dbidding/wallet/controller
git commit -m "feat: Wallet 충전과 환불 API 추가"
```

### Task 5: 동시성과 전체 회귀 검증

**Files:**
- Create: `backend/src/test/java/com/dbidding/wallet/service/WalletTransactionConcurrencyTest.java`

**Interfaces:**
- Verifies: 같은 Wallet의 충전·환불이 `findByUserIdForUpdate()`로 직렬화된다.
- Verifies: 동일 `Idempotency-Key` 동시 요청은 원장과 잔액을 한 번만 변경한다.
- Verifies: 활성 hold와 환불이 동시에 실행돼도 가용 잔액이 음수가 되지 않는다.

- [x] **Step 1: 실제 MySQL 동시 요청 테스트를 작성한다**

두 스레드가 같은 Wallet과 같은 `Idempotency-Key`로 10,000원 충전을 동시에 요청한다. 두 응답은 같은 거래 ID와 잔액을 반환하고, DB에는 `CHARGE` 한 건과 총잔액 10,000원만 남아야 한다.

- [x] **Step 2: 테스트가 잠금 제거 시 실패하는지 확인한다**

```bash
DB_PORT=3306 ./gradlew test \
  --tests com.dbidding.wallet.service.WalletTransactionConcurrencyTest
```

먼저 `@Lock(PESSIMISTIC_WRITE)`를 잠시 제거했을 때 중복 원장 UNIQUE 충돌 또는 잘못된 잔액으로 실패하는지 확인하고 즉시 복구한다.

- [x] **Step 3: 전체 테스트를 실행한다**

```bash
DB_PORT=3306 env -u JWT_SECRET ./gradlew clean test
```

Expected: 테스트 소스 수와 실패 0건을 함께 보고한다.

- [x] **Step 4: 구현 계획의 완료 항목을 갱신하고 커밋한다**

```bash
git add backend/src/test/java/com/dbidding/wallet \
  docs/hyeonmoon/wallet/3-charge-and-refund.md
git commit -m "test: Wallet 거래 동시성 검증"
```

## 완료 조건

- 충전·환불은 wallet row 잠금 안에서 `wallets.point`와 `point_records`를 원자적으로 변경한다.
- 같은 `Idempotency-Key` 재요청은 잔액과 원장을 중복 변경하지 않는다.
- 다른 요청에 같은 key를 재사용하면 409다.
- 충전은 1,000원 이상이고 환불은 활성 hold를 제외한 가용 잔액 이하이다.
- 충전 원장은 양수, 환불 원장은 음수이며 거래 직후 잔액을 저장한다.
- 실제 PG 승인·취소를 구현했다고 주장하지 않는다.
- 환불은 seller 정산·출금과 구분되는 사용자 Wallet 포인트 감소다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
