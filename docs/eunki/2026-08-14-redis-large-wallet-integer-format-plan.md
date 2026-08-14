# Redis Large Wallet Integer Format Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redis Lua의 큰 금액을 일반 10진 정수 문자열로 보존하고, 안전 상한 초과 요청을 상태 변경 전 HTTP 400으로 차단한다.

**Architecture:** Java 공통 정책이 외부 명령을 빠르게 검증하고, 각 Lua 스크립트가 현재 Redis 상태를 포함한 상한을 원자적으로 재검증한다. Lua는 모든 금액·잔액을 `string.format("%.0f", value)`로 Hash, Stream, 멱등 결과와 반환값에 기록하며, Java는 exact 정수 호환 파서로 기존 지수 표기 멱등 결과만 안전하게 읽는다.

**Tech Stack:** Java 21, Spring Boot, Spring Data Redis, Redis 7.4 Lua, JUnit 5, AssertJ, Testcontainers, Gradle

## Global Constraints

- 1회 지갑 충전·환불 최대 금액은 `100_000_000_000L`이다.
- 지갑 총 보유액, 경매 가격, 주문 정산 금액 최대값은 `1_000_000_000_000L`이다.
- 정책 거절은 모든 Redis 쓰기 전에 발생하고 HTTP 400 도메인 예외로 변환한다.
- 신규 Hash, Stream, Lua 응답과 멱등 결과에는 지수 표기를 기록하지 않는다.
- 기존 지수 표기 값은 정확한 정수이고 `long` 범위 안일 때만 읽는다.
- DB 스키마, Redis key 구조, Stream consumer, SSE, 프론트 API는 변경하지 않는다.

---

### Task 1: 공통 금액 정책과 Redis exact 정수 파서

**Files:**
- Create: `backend/src/main/java/com/dbidding/wallet/domain/WalletAmountPolicy.java`
- Create: `backend/src/main/java/com/dbidding/global/redis/RedisIntegerValue.java`
- Create: `backend/src/test/java/com/dbidding/wallet/domain/WalletAmountPolicyTest.java`
- Create: `backend/src/test/java/com/dbidding/global/redis/RedisIntegerValueTest.java`

**Interfaces:**
- Produces: `WalletAmountPolicy.MAX_TRANSACTION_AMOUNT`, `WalletAmountPolicy.MAX_BALANCE`, `validateTransactionAmount(long)`, `validateBalanceAmount(long)`
- Produces: `RedisIntegerValue.parseLongExact(String)`

- [ ] **Step 1: 정책 경계와 exact 파싱 실패 테스트 작성**

```java
assertThatCode(() -> WalletAmountPolicy.validateTransactionAmount(100_000_000_000L)).doesNotThrowAnyException();
assertThatThrownBy(() -> WalletAmountPolicy.validateTransactionAmount(100_000_000_001L))
    .isInstanceOf(InvalidWalletAmountException.class);
assertThat(RedisIntegerValue.parseLongExact("1.000000512e+14")).isEqualTo(100_000_051_200_000L);
assertThatThrownBy(() -> RedisIntegerValue.parseLongExact("1.5"))
    .isInstanceOf(IllegalArgumentException.class);
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests WalletAmountPolicyTest --tests RedisIntegerValueTest`

Expected: FAIL because the production classes do not exist.

- [ ] **Step 3: 최소 정책·파서 구현**

```java
public static long parseLongExact(String value) {
    try {
        return new BigDecimal(value).longValueExact();
    } catch (NumberFormatException | ArithmeticException exception) {
        throw new IllegalArgumentException("Redis 정수 값이 올바르지 않습니다: " + value, exception);
    }
}
```

정책 클래스는 거래와 잔액 상한을 상수로 노출하고 `InvalidWalletAmountException`을 던진다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests WalletAmountPolicyTest --tests RedisIntegerValueTest`

Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/dbidding/wallet/domain/WalletAmountPolicy.java backend/src/main/java/com/dbidding/global/redis/RedisIntegerValue.java backend/src/test/java/com/dbidding/wallet/domain/WalletAmountPolicyTest.java backend/src/test/java/com/dbidding/global/redis/RedisIntegerValueTest.java
git commit -m "fix: Redis 금액 안전 상한과 정수 파싱 계약 추가"
```

### Task 2: 지갑 충전·환불 Lua 정수 계약

**Files:**
- Modify: `backend/src/main/resources/lua/wallet-transition.lua`
- Modify: `backend/src/main/java/com/dbidding/wallet/service/RedisWalletService.java`
- Modify: `backend/src/main/java/com/dbidding/wallet/service/WalletService.java`
- Modify: `backend/src/test/java/com/dbidding/wallet/service/RedisWalletTransitionLuaIntegrationTest.java`
- Modify: `backend/src/test/java/com/dbidding/wallet/service/RedisWalletServiceTest.java`
- Modify: `backend/src/test/java/com/dbidding/wallet/service/WalletServiceTransactionTest.java`

**Interfaces:**
- Consumes: `WalletAmountPolicy`, `RedisIntegerValue.parseLongExact(String)`
- Produces: `wallet-transition.lua`의 `BALANCE_LIMIT_EXCEEDED` 거절 코드와 일반 10진 정수 문자열 응답

- [ ] **Step 1: 큰 잔액·멱등·상한 무변경 실패 테스트 작성**

```java
// 100_000_050_000_000 같은 기존 재현값 대신 정책 내 900_000_000_000에서 연산한다.
assertThat(result).doesNotContain("e+").doesNotContain("e-");
assertThat(hash).containsEntry("availableBalance", "1000000000000");
assertThat(event).containsEntry("availableBalance", "1000000000000");
assertThat(replay).isEqualTo(result.substring(0, result.length() - 5) + "true");
```

상한 초과 충전은 `REJECTED|BALANCE_LIMIT_EXCEEDED`이고 Hash, Stream, 멱등 키가 바뀌지 않아야 한다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests RedisWalletTransitionLuaIntegrationTest --tests RedisWalletServiceTest --tests WalletServiceTransactionTest`

Expected: FAIL because large numeric values are emitted in exponent form or limits are not enforced.

- [ ] **Step 3: Lua와 Java 최소 구현**

Lua에 다음 helper를 두고 계산 결과를 Hash, XADD, 응답에 사용한다.

```lua
local function integerString(value)
    return string.format('%.0f', value)
end
```

`ARGV`로 최대 거래액과 최대 잔액을 전달하고 모든 쓰기 전 `AMOUNT_LIMIT_EXCEEDED` 또는
`BALANCE_LIMIT_EXCEEDED`를 반환한다. Java 서비스는 정책을 먼저 검증하고 Lua 거절을
`InvalidWalletAmountException`으로 변환하며 모든 Redis 정수 파싱에 exact 파서를 쓴다.

- [ ] **Step 4: 관련 테스트 통과 확인**

Run: `./gradlew test --tests RedisWalletTransitionLuaIntegrationTest --tests RedisWalletServiceTest --tests WalletServiceTransactionTest`

Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/resources/lua/wallet-transition.lua backend/src/main/java/com/dbidding/wallet/service/RedisWalletService.java backend/src/main/java/com/dbidding/wallet/service/WalletService.java backend/src/test/java/com/dbidding/wallet/service/RedisWalletTransitionLuaIntegrationTest.java backend/src/test/java/com/dbidding/wallet/service/RedisWalletServiceTest.java backend/src/test/java/com/dbidding/wallet/service/WalletServiceTransactionTest.java
git commit -m "fix: 큰 지갑 잔액을 정수 문자열로 전이"
```

### Task 3: 입찰·상회 입찰·즉시 낙찰 정수 계약

**Files:**
- Modify: `backend/src/main/resources/lua/bid-accept.lua`
- Modify: `backend/src/main/java/com/dbidding/auction/bid/RedisBidExecutor.java`
- Modify: `backend/src/test/java/com/dbidding/auction/bid/RedisBidLuaIntegrationTest.java`
- Modify: `backend/src/test/java/com/dbidding/auction/bid/RedisBidExecutorTest.java`

**Interfaces:**
- Consumes: `WalletAmountPolicy.MAX_BALANCE`, `RedisIntegerValue.parseLongExact(String)`
- Produces: `bid-accept.lua`의 `AMOUNT_LIMIT_EXCEEDED`/`BALANCE_LIMIT_EXCEEDED` 거절과 정수 문자열 상태·이벤트·응답

- [ ] **Step 1: 큰 금액 입찰 경로 실패 테스트 작성**

큰 정책 내 잔액에서 일반 입찰과 상회 입찰을 수행해 신규·이전 지갑 Hash, hold, 최근 입찰
Stream, timeline Stream, 최초 응답과 멱등 재응답 어디에도 지수 표기가 없음을 검증한다. 즉시 낙찰은
차감 후 frozen balance와 order price도 일반 정수 문자열이어야 한다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests RedisBidLuaIntegrationTest --tests RedisBidExecutorTest`

Expected: FAIL with the reproduced `NumberFormatException` or exponent-form assertion.

- [ ] **Step 3: Lua와 Java 최소 구현**

입찰가·현재가·hold·available/frozen·Stream 금액·응답 필드에 `integerString`을 적용한다. 최대 경매
금액과 지갑 총액을 쓰기 전에 검증한다. Java는 입력 상한을 확인하고 응답 및 SSE payload 숫자를
exact 파서로 변환하며 Lua 상한 거절을 `AuctionException.invalidBidRequest(...)`로 매핑한다.

- [ ] **Step 4: 관련 테스트 통과 확인**

Run: `./gradlew test --tests RedisBidLuaIntegrationTest --tests RedisBidExecutorTest`

Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/resources/lua/bid-accept.lua backend/src/main/java/com/dbidding/auction/bid/RedisBidExecutor.java backend/src/test/java/com/dbidding/auction/bid/RedisBidLuaIntegrationTest.java backend/src/test/java/com/dbidding/auction/bid/RedisBidExecutorTest.java
git commit -m "fix: 큰 입찰 금액의 Redis 정수 계약 보장"
```

### Task 4: 주문 정산과 경매 마감 정수 계약

**Files:**
- Modify: `backend/src/main/resources/lua/order-wallet-transition.lua`
- Modify: `backend/src/main/resources/lua/auction-close-request.lua`
- Modify: `backend/src/main/java/com/dbidding/order/RedisOrderCommandService.java`
- Modify: `backend/src/main/java/com/dbidding/auction/service/RedisAuctionCloseSchedulerProcessor.java`
- Modify: `backend/src/test/java/com/dbidding/order/RedisOrderWalletTransitionLuaIntegrationTest.java`
- Modify: `backend/src/test/java/com/dbidding/auction/service/RedisAuctionCloseRequestLuaIntegrationTest.java`

**Interfaces:**
- Consumes: `WalletAmountPolicy.MAX_BALANCE`, `RedisIntegerValue.parseLongExact(String)`
- Produces: 주문 정산·취소와 낙찰 차감의 정수 문자열 Hash, Stream, Lua 응답

- [ ] **Step 1: 큰 정산·취소·낙찰 실패 테스트 작성**

정책 내 큰 주문 가격으로 판매자 정산과 구매자 취소 환불을 실행하고 Hash, Stream, 응답의 모든
금액이 일반 10진 정수 문자열인지 검증한다. 마감 경로도 winningPrice와 지갑 차감 결과를 동일하게
검증한다. 지갑 총액 상한 초과 시 상태와 Stream이 변하지 않아야 한다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests RedisOrderWalletTransitionLuaIntegrationTest --tests RedisAuctionCloseRequestLuaIntegrationTest`

Expected: FAIL on exponent-form output or missing limit rejection.

- [ ] **Step 3: Lua와 Java 최소 구현**

두 Lua 스크립트의 금액 산술 결과와 저장·Stream·응답을 `integerString`으로 통일하고 쓰기 전 상한을
검증한다. Java 어댑터는 exact 파서와 정책 거절 매핑을 사용한다.

- [ ] **Step 4: 관련 테스트 통과 확인**

Run: `./gradlew test --tests RedisOrderWalletTransitionLuaIntegrationTest --tests RedisAuctionCloseRequestLuaIntegrationTest`

Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/resources/lua/order-wallet-transition.lua backend/src/main/resources/lua/auction-close-request.lua backend/src/main/java/com/dbidding/order/RedisOrderCommandService.java backend/src/main/java/com/dbidding/auction/service/RedisAuctionCloseSchedulerProcessor.java backend/src/test/java/com/dbidding/order/RedisOrderWalletTransitionLuaIntegrationTest.java backend/src/test/java/com/dbidding/auction/service/RedisAuctionCloseRequestLuaIntegrationTest.java
git commit -m "fix: 주문과 낙찰 지갑 금액의 정수 계약 보장"
```

### Task 5: 전체 회귀 검증과 문서 마감

**Files:**
- Modify: `docs/eunki/2026-08-14-redis-large-wallet-integer-format-design.md` only if implementation details differ

**Interfaces:**
- Consumes: Tasks 1-4의 production code와 tests
- Produces: #484 완료 조건을 입증하는 테스트 결과와 깨끗한 작업 트리

- [ ] **Step 1: Lua 금액 직렬화 누락 정적 점검**

Run: `rg -n 'availableBalance|frozenBalance|highestHoldAmount|winningPrice|transactionAmount|bidPrice|currentPrice' backend/src/main/resources/lua/{wallet-transition,bid-accept,order-wallet-transition,auction-close-request}.lua`

Expected: 계산된 금액을 Redis나 응답에 기록하는 경로가 `integerString`을 거치거나 이미 문자열인 입력을 사용한다.

- [ ] **Step 2: Redis 관련 전체 테스트 실행**

Run: `./gradlew test --tests '*Redis*'`

Expected: PASS.

- [ ] **Step 3: backend 전체 테스트 실행**

Run: `./gradlew test`

Expected: PASS.

- [ ] **Step 4: 변경 범위와 미추적 파일 확인**

Run: `git status --short && git diff --check && git log --oneline origin/dev..HEAD`

Expected: #484 파일만 변경되고 whitespace 오류가 없으며 마이크로 커밋이 목적별로 분리되어 있다.

- [ ] **Step 5: 필요 시 문서 차이 커밋**

```bash
git add docs/eunki/2026-08-14-redis-large-wallet-integer-format-design.md
git commit -m "docs: Redis 큰 금액 정수 계약 구현 결과 반영"
```
