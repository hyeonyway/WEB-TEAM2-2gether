# Signup Implementation Plan

**Goal:** 이메일과 닉네임 중복을 차단하고 PBKDF2로 비밀번호를 해싱한 뒤 User와 초기 Wallet을 하나의 트랜잭션으로 생성한다.

**Architecture:** AuthService가 회원가입 유스케이스를 조정한다. Auth는 자신이 소유한 `auth.port.UserAccountPort`와 `auth.port.WalletProvisioningPort`만 알고 User·Wallet Entity와 Repository를 import하지 않는다. User와 Wallet 구현체는 각 Port를 구현하며, 어느 한쪽이 실패하면 같은 트랜잭션에서 모두 롤백된다.

**Tech Stack:** Java 21, Spring Boot Validation, JPA Transaction, JUnit 5, Mockito, PBKDF2WithHmacSHA256

## Global Constraints

- 요청 필드는 `email`, `password`, `nickname`이다.
- PBKDF2 salt는 16바이트, 결과 키는 256비트다.
- salt와 hash는 소문자 hex로 저장한다.
- 기본 Wallet 잔액은 0이다.
- 회원가입 응답에 비밀번호, salt, hash를 포함하지 않는다.

---

### Task 1: PasswordHasher

**Files:**
- Create: `backend/src/main/java/com/dbidding/auth/password/PasswordHasher.java`
- Create: `backend/src/main/java/com/dbidding/auth/password/PasswordHash.java`
- Test: `backend/src/test/java/com/dbidding/auth/password/PasswordHasherTest.java`

**Interfaces:**
- Produces: `PasswordHash PasswordHasher.hash(String rawPassword)`
- Produces: `boolean PasswordHasher.matches(String rawPassword, String salt, String expectedHash)`

- [x] **Step 1: 실패 테스트 작성**

```java
@Test
void 같은_비밀번호도_서로_다른_salt와_hash를_만든다() {
    PasswordHash first = passwordHasher.hash("Password123!");
    PasswordHash second = passwordHasher.hash("Password123!");

    assertThat(first.salt()).hasSize(32).isNotEqualTo(second.salt());
    assertThat(first.encryptedPassword()).hasSize(64).isNotEqualTo(second.encryptedPassword());
    assertThat(passwordHasher.matches("Password123!", first.salt(), first.encryptedPassword())).isTrue();
}
```

- [x] **Step 2: 실패 확인**

```bash
./gradlew test --tests com.dbidding.auth.password.PasswordHasherTest
```

Expected: `PasswordHasher`가 없어 FAIL.

- [x] **Step 3: PBKDF2 구현**

```java
@Component
public class PasswordHasher {
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int ITERATIONS = 600_000;

    public PasswordHash hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = derive(rawPassword, salt);
        return new PasswordHash(HexFormat.of().formatHex(hash), HexFormat.of().formatHex(salt));
    }

    public boolean matches(String rawPassword, String salt, String expectedHash) {
        byte[] actual = derive(rawPassword, HexFormat.of().parseHex(salt));
        return MessageDigest.isEqual(actual, HexFormat.of().parseHex(expectedHash));
    }
}
```

`PBEKeySpec`는 사용 후 `clearPassword()`를 호출하고 `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`를 사용한다.

- [x] **Step 4: 정답·오답 테스트 통과 및 실행시간 기록**

```bash
./gradlew test --tests com.dbidding.auth.password.PasswordHasherTest
```

Expected: PASS. 로컬 1회 검증 시간이 1초를 크게 넘으면 반복 횟수를 임의로 낮추지 말고 팀에 측정값을 공유한다.

### Task 2: 회원가입 계약

**Files:**
- Create: `backend/src/main/java/com/dbidding/auth/dto/SignupRequest.java`
- Create: `backend/src/main/java/com/dbidding/auth/dto/SignupResponse.java`
- Create: `backend/src/main/java/com/dbidding/auth/port/UserAccount.java`
- Create: `backend/src/main/java/com/dbidding/auth/port/UserAccountRole.java`
- Create: `backend/src/main/java/com/dbidding/auth/port/UserAccountPort.java`
- Consumes: `backend/src/main/java/com/dbidding/auth/port/WalletProvisioningPort.java`
- Create: `backend/src/main/java/com/dbidding/user/adapter/UserAccountAdapter.java`
- Create: `backend/src/main/java/com/dbidding/auth/exception/DuplicateEmailException.java`
- Create: `backend/src/main/java/com/dbidding/auth/exception/DuplicateNicknameException.java`
- Test: `backend/src/test/java/com/dbidding/auth/dto/SignupRequestValidationTest.java`
- Test: `backend/src/test/java/com/dbidding/user/adapter/UserAccountAdapterTest.java`

**Interfaces:**
- Consumes: `UserAccountPort`, `WalletProvisioningPort`, `PasswordHasher`
- Produces: `UserAccount UserAccountPort.create(String email, String nickname, String encryptedPassword, String salt)`
- Produces: `void WalletProvisioningPort.createFor(Integer userId)`
- Produces: `SignupResponse AuthService.signup(SignupRequest request)`

- [x] **Step 1: DTO와 port 작성**

```java
public record SignupRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank @Size(min = 2, max = 30) String nickname
) {}

public record SignupResponse(Integer id, String email, String nickname, String role, String status) {}

public enum UserAccountRole {
    USER,
    ADMIN
}

public record UserAccount(
    Integer id,
    String email,
    String nickname,
    UserAccountRole role,
    String status,
    String encryptedPassword,
    String salt
) {}

public interface UserAccountPort {
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    UserAccount create(String email, String nickname, String encryptedPassword, String salt);
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findById(Integer userId);
}

```

`UserAccountAdapter`는 `user` 패키지에서 `UserRepository`를 사용해 `UserAccountPort`를 구현하고, User Entity를 auth에 노출하지 않은 채 `UserAccount`로 변환한다. `user.UserRole`은 명시적인 `switch`로 `auth.port.UserAccountRole`에 매핑하여 역할 추가 시 누락을 컴파일 단계에서 확인한다.

`WalletProvisioningPort`는 선행 Wallet 생성 연동 작업에서 Auth가 소유하도록
정의한다. 회원가입 작업은 이미 등록된 Port와 Wallet 구현체를 소비한다.

- [x] **Step 2: DTO 검증과 UserAccountAdapter 테스트**

유효한 회원가입 요청과 이메일·비밀번호·닉네임 형식 오류를 Bean Validation으로
검증한다. `UserAccountAdapter`는 User 생성·조회 결과를 Auth 소유
`UserAccount`로 변환하고 `user.UserRole`을 `UserAccountRole`로 명시적으로
매핑하는지 확인한다.

### Task 3: 회원가입 서비스와 Controller

**Files:**
- Create: `backend/src/main/java/com/dbidding/auth/service/AuthService.java`
- Create: `backend/src/main/java/com/dbidding/auth/controller/AuthController.java`
- Test: `backend/src/test/java/com/dbidding/auth/service/AuthServiceSignupTest.java`
- Test: `backend/src/test/java/com/dbidding/auth/controller/AuthControllerSignupTest.java`
- Test: `backend/src/test/java/com/dbidding/auth/integration/SignupTransactionTest.java`

**Interfaces:**
- Consumes: `WalletProvisioningPort.createFor(Integer userId)`
- Produces: `POST /api/auth/signup`

- [x] **Step 1: 중복 이메일·닉네임 서비스 실패 테스트**

```java
@Test
void 중복_이메일이면_사용자와_지갑을_생성하지_않는다() {
    given(userAccountPort.existsByEmail("collector@example.com")).willReturn(true);

    assertThatThrownBy(() -> authService.signup(request))
        .isInstanceOf(DuplicateEmailException.class);
    then(userAccountPort).should(never()).create(any(), any(), any(), any());
    then(walletProvisioningPort).shouldHaveNoInteractions();
}
```

- [x] **Step 2: 성공 서비스 테스트 작성**

```java
@Test
void 회원가입하면_사용자와_잔액_0원_지갑을_생성한다() {
    UserAccount savedUser = new UserAccount(
        1, request.email(), request.nickname(), UserAccountRole.USER, "ACTIVE", hash, salt
    );
    given(userAccountPort.existsByEmail(request.email())).willReturn(false);
    given(userAccountPort.existsByNickname(request.nickname())).willReturn(false);
    given(userAccountPort.create(request.email(), request.nickname(), hash, salt))
        .willReturn(savedUser);

    SignupResponse response = authService.signup(request);

    assertThat(response.id()).isEqualTo(1);
    then(walletProvisioningPort).should().createFor(1);
}
```

- [x] **Step 3: 최소 서비스 구현**

```java
@Transactional
public SignupResponse signup(SignupRequest request) {
    if (userAccountPort.existsByEmail(request.email())) {
        throw new DuplicateEmailException();
    }
    if (userAccountPort.existsByNickname(request.nickname())) {
        throw new DuplicateNicknameException();
    }

    PasswordHash password = passwordHasher.hash(request.password());
    UserAccount user = userAccountPort.create(
        request.email(), request.nickname(), password.encryptedPassword(), password.salt()
    );
    walletProvisioningPort.createFor(user.id());
    return SignupResponse.from(user);
}
```

DB UNIQUE 위반도 동일한 409 응답으로 변환해 사전 조회와 실제 INSERT 사이의 경쟁 조건을 처리한다.

- [x] **Step 4: Controller 요청·응답 테스트**

```java
mockMvc.perform(post("/api/auth/signup")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"email":"collector@example.com","password":"Password123!","nickname":"collector"}
            """))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.id").value(1))
    .andExpect(jsonPath("$.password").doesNotExist());
```

- [x] **Step 5: User·Wallet 트랜잭션 통합 테스트**

실제 `UserAccountAdapter`와 `WalletProvisioningAdapter`를 사용해 회원가입 성공 시
`users`와 `wallets`에 각각 한 row가 생성되는지 확인한다. Wallet 생성이 실패하면
같은 트랜잭션에서 User 저장도 롤백되는지 검증한다.

- [x] **Step 6: 전체 테스트와 커밋**

```bash
./gradlew clean test
git add backend/src/main/java/com/dbidding/auth backend/src/test/java/com/dbidding/auth
git commit -m "feat: 회원가입 구현"
```

## 완료 조건

- 이메일과 닉네임 중복이 409 도메인 오류로 응답된다.
- PBKDF2 hash와 salt만 저장된다.
- User 저장 또는 Wallet 생성 중 하나가 실패하면 둘 다 롤백된다.
- 회원가입 시 Authentication row는 생성되지 않는다.
- Auth는 User Entity와 UserRepository를 직접 import하지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
