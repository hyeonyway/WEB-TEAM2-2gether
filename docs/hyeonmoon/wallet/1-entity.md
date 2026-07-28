# Wallet Entity Implementation Plan

**Goal:** `wallets` 테이블에 정확히 대응하는 Wallet 엔티티와 Repository를 만든다.

**Architecture:** Wallet은 User 객체 대신 `Integer userId`를 보유하며 사용자당 하나만 존재한다. 금액은 DB `BIGINT`, Java `long`으로 저장한다.

**Tech Stack:** Java 21, Spring Data JPA, MySQL 8.4, JUnit 5, AssertJ

## Global Constraints

- Wallet ID와 userId는 `Integer`다.
- point는 `long`이며 음수 잔액을 허용하지 않는다.
- User Entity와 UserRepository를 import하지 않는다.
- `wallets.held_amount`를 추가하지 않는다.
- `@Getter`와 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용한다.
- `@Data`, `@Setter`, public 기본 생성자를 노출하지 않는다.

---

### Task 1: Wallet 엔티티

**Files:**
- Create: `backend/src/main/java/com/dbidding/wallet/domain/Wallet.java`
- Test: `backend/src/test/java/com/dbidding/wallet/domain/WalletTest.java`

**Interfaces:**
- Produces: `Wallet.open(Integer userId)`
- Produces: `long Wallet.getPoint()`

- [x] **Step 1: 초기 잔액 테스트 작성**

```java
@Test
void 신규_지갑은_잔액_0으로_생성된다() {
    Wallet wallet = Wallet.open(1);

    assertThat(wallet.getUserId()).isEqualTo(1);
    assertThat(wallet.getPoint()).isZero();
}
```

- [x] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests com.dbidding.wallet.domain.WalletTest
```

Expected: Wallet 클래스가 없어 FAIL.

- [x] **Step 3: 엔티티 구현**

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "wallets")
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Integer userId;

    @Column(nullable = false)
    private long point;

    private Wallet(Integer userId, long point) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        this.userId = userId;
        this.point = point;
    }

    public static Wallet open(Integer userId) {
        return new Wallet(userId, 0L);
    }
}
```

충전·차감 메서드는 해당 유스케이스의 불변식이 확정될 때 추가한다. 이번 단계에서는 임의의 `setPoint`를 만들지 않는다.

- [x] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests com.dbidding.wallet.domain.WalletTest
```

Expected: PASS.

### Task 2: WalletRepository

**Files:**
- Create: `backend/src/main/java/com/dbidding/wallet/repository/WalletRepository.java`
- Test: `backend/src/test/java/com/dbidding/wallet/repository/WalletRepositoryTest.java`

**Interfaces:**
- Produces: `Optional<Wallet> findByUserId(Integer userId)`
- Produces: `boolean existsByUserId(Integer userId)`

- [x] **Step 1: Repository 작성**

```java
public interface WalletRepository extends JpaRepository<Wallet, Integer> {
    Optional<Wallet> findByUserId(Integer userId);
    boolean existsByUserId(Integer userId);
}
```

- [x] **Step 2: MySQL 매핑 통합 테스트**

Wallet을 저장한 뒤 userId 조회와 초기 point 0을 확인한다. 동일 userId 두 건 저장은 unique constraint로 실패해야 한다.

- [x] **Step 3: 전체 테스트와 커밋**

```bash
./gradlew clean test
git add backend/src/main/java/com/dbidding/wallet backend/src/test/java/com/dbidding/wallet
git commit -m "feat: Wallet 엔티티와 Repository 추가"
```

## 완료 조건

- Hibernate schema validation을 통과한다.
- Wallet 생성 경로는 `Wallet.open(userId)` 하나뿐이다.
- null userId로 Wallet을 생성할 수 없다.
- 외부 코드가 point를 임의로 변경할 수 없다.
- userId 중복이 DB와 애플리케이션 양쪽에서 차단된다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
