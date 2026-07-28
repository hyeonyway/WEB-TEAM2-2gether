# Auth Entity Implementation Plan

**Goal:** `users`와 `authentication` 테이블에 정확히 대응하는 JPA 엔티티와 Repository를 만든다.

**Architecture:** User는 `user` 패키지, Authentication은 `auth` 패키지가 소유한다. 연관관계 객체 대신 `Authentication.userId`를 `Integer` scalar FK로 두어 조회와 생명주기를 명시적으로 관리한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, MySQL 8.4, JUnit 5, AssertJ

## Global Constraints

- `schema.sql`이 원본이며 엔티티는 `ddl-auto=validate`를 통과해야 한다.
- User와 Authentication의 ID는 `Integer`다.
- 이메일은 `VARCHAR(255)`, 닉네임은 `VARCHAR(30)`이다.
- User의 role과 status는 `VARCHAR(20)`이다.
- `encrypted_password`는 hex 64자, `salt`는 hex 32자다.
- DB에는 Refresh Token 원문이 아닌 SHA-256 hex 해시를 `CHAR(64)`로 저장한다.
- enum은 문자열로 저장한다.
- `@Getter`와 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용한다.
- `@Data`, `@Setter`, public 기본 생성자를 노출하지 않는다.

## MVP 범위 결정: User 프로필 이미지 제외

- MVP에서는 사용자 프로필 이미지를 제공하지 않는다.
- `users` 테이블과 `User` 엔티티에 `image_path` 또는 `imagePath`를 두지 않는다.
- `card_metadata.image_path`와 경매의 `images.image_path`는 서로 다른 도메인의 이미지이므로 유지한다.
- 현재 초기 공유 스키마를 수정하는 단계이므로 별도의 운영 DB 마이그레이션 파일은 만들지 않는다.

---

### Task 1: User 엔티티

**Files:**
- Create: `backend/src/main/java/com/dbidding/user/domain/User.java`
- Create: `backend/src/main/java/com/dbidding/user/domain/UserRole.java`
- Create: `backend/src/main/java/com/dbidding/user/domain/UserStatus.java`
- Test: `backend/src/test/java/com/dbidding/user/domain/UserTest.java`

**Interfaces:**
- Produces: `User.create(String email, String nickname, String encryptedPassword, String salt)`
- Produces: `Integer User.getId()`, `String User.getEmail()`, `String User.getNickname()`

- [x] **Step 1: 생성 규칙을 표현하는 실패 테스트 작성**

```java
@Test
void 신규_사용자는_USER_ACTIVE_상태로_생성된다() {
    User user = User.create("collector@example.com", "collector", "a".repeat(64), "b".repeat(32));

    assertThat(user.getRole()).isEqualTo(UserRole.USER);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
}
```

- [x] **Step 2: 테스트가 컴파일 실패하는지 확인**

```bash
cd backend
./gradlew test --tests com.dbidding.user.domain.UserTest
```

Expected: `User`, `UserRole`, `UserStatus`를 찾지 못해 FAIL.

- [x] **Step 3: 스키마와 동일한 엔티티 구현**

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "encrypted_password", nullable = false, length = 64)
    private String encryptedPassword;

    @Column(nullable = false, length = 32)
    private String salt;

    public static User create(String email, String nickname, String encryptedPassword, String salt) {
        return new User(email, nickname, UserRole.USER, UserStatus.ACTIVE, encryptedPassword, salt);
    }
}
```

enum의 최초 값은 `USER`, `ADMIN`과 `ACTIVE`, `SUSPENDED`, `WITHDRAWN`만 둔다. 아직 사용하지 않는 상태를 미리 늘리지 않는다.

- [x] **Step 4: User 단위 테스트 통과 확인**

```bash
./gradlew test --tests com.dbidding.user.domain.UserTest
```

Expected: PASS.

### Task 2: Authentication 엔티티

**Files:**
- Create: `backend/src/main/java/com/dbidding/auth/domain/Authentication.java`
- Test: `backend/src/test/java/com/dbidding/auth/domain/AuthenticationTest.java`

**Interfaces:**
- Produces: `Authentication.issue(Integer userId, String refreshTokenHash)`
- Produces: `void Authentication.rotate(String newRefreshTokenHash)`

- [x] **Step 1: Rotation 실패 테스트 작성**

```java
@Test
void refresh_token_hash를_교체한다() {
    Authentication authentication = Authentication.issue(1, "a".repeat(64));

    authentication.rotate("b".repeat(64));

    assertThat(authentication.getRefreshTokenHash()).isEqualTo("b".repeat(64));
}
```

- [x] **Step 2: 실패 확인 후 엔티티 최소 구현**

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "authentication")
public class Authentication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Integer userId;

    @Column(name = "refresh_token", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    public static Authentication issue(Integer userId, String refreshTokenHash) {
        return new Authentication(userId, refreshTokenHash);
    }

    public void rotate(String newRefreshTokenHash) {
        this.refreshTokenHash = newRefreshTokenHash;
    }
}
```

- [x] **Step 3: 테스트 통과 확인**

```bash
./gradlew test --tests com.dbidding.auth.domain.AuthenticationTest
```

Expected: PASS.

### Task 3: Repository

**Files:**
- Create: `backend/src/main/java/com/dbidding/user/repository/UserRepository.java`
- Create: `backend/src/main/java/com/dbidding/auth/repository/AuthenticationRepository.java`
- Test: `backend/src/test/java/com/dbidding/user/repository/UserRepositoryTest.java`
- Test: `backend/src/test/java/com/dbidding/auth/repository/AuthenticationRepositoryTest.java`

**Interfaces:**
- Produces: `boolean UserRepository.existsByEmail(String email)`
- Produces: `boolean UserRepository.existsByNickname(String nickname)`
- Produces: `Optional<User> UserRepository.findByEmail(String email)`
- Produces: `Optional<Authentication> AuthenticationRepository.findByUserId(Integer userId)`

- [x] **Step 1: Repository 시그니처 작성**

```java
public interface UserRepository extends JpaRepository<User, Integer> {
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    Optional<User> findByEmail(String email);
}

public interface AuthenticationRepository extends JpaRepository<Authentication, Integer> {
    Optional<Authentication> findByUserId(Integer userId);
    Optional<Authentication> findByRefreshTokenHash(String refreshTokenHash);
    void deleteByUserId(Integer userId);
}
```

- [x] **Step 2: 로컬 MySQL 테스트 DB 준비**

`../docker-compose.yml`로 MySQL 8.4 컨테이너를 실행하고 `schema.sql`을 최초 1회 적용한다. JPA 통합 테스트는 각 테스트 트랜잭션을 롤백하여 로컬 개발 데이터를 남기지 않는다.

- [x] **Step 3: 저장·중복 조회 통합 테스트 작성 및 실행**

```java
@DataJpaTest
class UserRepositoryTest {
    @Autowired UserRepository userRepository;

    @Test
    void 이메일과_닉네임_중복을_조회한다() {
        userRepository.saveAndFlush(User.create(
            "collector@example.com", "collector", "a".repeat(64), "b".repeat(32)
        ));

        assertThat(userRepository.existsByEmail("collector@example.com")).isTrue();
        assertThat(userRepository.existsByNickname("collector")).isTrue();
    }
}
```

```bash
./gradlew test --tests com.dbidding.user.repository.UserRepositoryTest
./gradlew test --tests com.dbidding.auth.repository.AuthenticationRepositoryTest
```

Expected: PASS하며 Hibernate schema validation 오류가 없어야 한다.

- [x] **Step 4: 전체 테스트 및 커밋**

```bash
./gradlew clean test
git add backend/src/main/java/com/dbidding/auth backend/src/test/java/com/dbidding/auth \
  backend/src/main/java/com/dbidding/user backend/src/test/java/com/dbidding/user
git commit -m "feat: Auth 엔티티와 Repository 추가"
```

## 완료 조건

- User와 Authentication 매핑이 MySQL 스키마 검증을 통과한다.
- Authentication은 User 객체가 아니라 `Integer userId`만 참조한다.
- `User.create()`가 스키마의 모든 NOT NULL 필드를 채운다.
- 비밀번호와 Refresh Token 원문을 저장하는 API가 엔티티에 존재하지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
