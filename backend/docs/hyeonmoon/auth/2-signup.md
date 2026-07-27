# Signup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이메일과 닉네임 중복을 차단하고 PBKDF2로 비밀번호를 해싱한 뒤 User와 초기 Wallet을 하나의 트랜잭션으로 생성한다.

**Architecture:** AuthService가 회원가입 유스케이스를 조정한다. Auth는 `auth.port.WalletProvisioningPort`만 알고, Wallet Repository는 import하지 않는다. Wallet 구현 실패 시 User 저장도 같은 트랜잭션에서 롤백된다.

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
- Create: `backend/src/main/java/com/dbidding/auth/PasswordHasher.java`
- Create: `backend/src/main/java/com/dbidding/auth/PasswordHash.java`
- Test: `backend/src/test/java/com/dbidding/auth/PasswordHasherTest.java`

**Interfaces:**
- Produces: `PasswordHash PasswordHasher.hash(String rawPassword)`
- Produces: `boolean PasswordHasher.matches(String rawPassword, String salt, String expectedHash)`

- [ ] **Step 1: 실패 테스트 작성**

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

- [ ] **Step 2: 실패 확인**

```bash
./gradlew test --tests com.dbidding.auth.PasswordHasherTest
```

Expected: `PasswordHasher`가 없어 FAIL.

- [ ] **Step 3: PBKDF2 구현**

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

- [ ] **Step 4: 정답·오답 테스트 통과 및 실행시간 기록**

```bash
./gradlew test --tests com.dbidding.auth.PasswordHasherTest
```

Expected: PASS. 로컬 1회 검증 시간이 1초를 크게 넘으면 반복 횟수를 임의로 낮추지 말고 팀에 측정값을 공유한다.

### Task 2: 회원가입 계약

**Files:**
- Create: `backend/src/main/java/com/dbidding/auth/dto/SignupRequest.java`
- Create: `backend/src/main/java/com/dbidding/auth/dto/SignupResponse.java`
- Create: `backend/src/main/java/com/dbidding/auth/port/WalletProvisioningPort.java`
- Create: `backend/src/main/java/com/dbidding/auth/exception/DuplicateEmailException.java`
- Create: `backend/src/main/java/com/dbidding/auth/exception/DuplicateNicknameException.java`

**Interfaces:**
- Consumes: `UserRepository`, `PasswordHasher`
- Produces: `void WalletProvisioningPort.createFor(Integer userId)`
- Produces: `SignupResponse AuthService.signup(SignupRequest request)`

- [ ] **Step 1: DTO와 port 작성**

```java
public record SignupRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank @Size(min = 2, max = 30) String nickname
) {}

public record SignupResponse(Integer id, String email, String nickname, String role, String status) {}

public interface WalletProvisioningPort {
    void createFor(Integer userId);
}
```

- [ ] **Step 2: 중복 이메일 서비스 실패 테스트**

```java
@Test
void 중복_이메일이면_사용자와_지갑을_생성하지_않는다() {
    given(userRepository.existsByEmail("collector@example.com")).willReturn(true);

    assertThatThrownBy(() -> authService.signup(request))
        .isInstanceOf(DuplicateEmailException.class);
    then(userRepository).should(never()).save(any());
    then(walletProvisioningPort).shouldHaveNoInteractions();
}
```

### Task 3: 회원가입 서비스와 Controller

**Files:**
- Create: `backend/src/main/java/com/dbidding/auth/AuthService.java`
- Create: `backend/src/main/java/com/dbidding/auth/AuthController.java`
- Test: `backend/src/test/java/com/dbidding/auth/AuthServiceSignupTest.java`
- Test: `backend/src/test/java/com/dbidding/auth/AuthControllerSignupTest.java`

**Interfaces:**
- Consumes: `WalletProvisioningPort.createFor(Integer userId)`
- Produces: `POST /api/auth/signup`

- [ ] **Step 1: 성공 서비스 테스트 작성**

```java
@Test
void 회원가입하면_사용자와_잔액_0원_지갑을_생성한다() {
    User savedUser = mock(User.class);
    given(userRepository.existsByEmail(request.email())).willReturn(false);
    given(userRepository.existsByNickname(request.nickname())).willReturn(false);
    given(userRepository.save(any(User.class))).willReturn(savedUser);
    given(savedUser.getId()).willReturn(1);
    given(savedUser.getEmail()).willReturn(request.email());
    given(savedUser.getNickname()).willReturn(request.nickname());
    given(savedUser.getRole()).willReturn(UserRole.USER);
    given(savedUser.getStatus()).willReturn(UserStatus.ACTIVE);

    SignupResponse response = authService.signup(request);

    assertThat(response.id()).isEqualTo(1);
    then(walletProvisioningPort).should().createFor(1);
}
```

- [ ] **Step 2: 최소 서비스 구현**

```java
@Transactional
public SignupResponse signup(SignupRequest request) {
    if (userRepository.existsByEmail(request.email())) {
        throw new DuplicateEmailException();
    }
    if (userRepository.existsByNickname(request.nickname())) {
        throw new DuplicateNicknameException();
    }

    PasswordHash password = passwordHasher.hash(request.password());
    User user = userRepository.save(User.create(
        request.email(), request.nickname(), password.encryptedPassword(), password.salt()
    ));
    walletProvisioningPort.createFor(user.getId());
    return SignupResponse.from(user);
}
```

DB UNIQUE 위반도 동일한 409 응답으로 변환해 사전 조회와 실제 INSERT 사이의 경쟁 조건을 처리한다.

- [ ] **Step 3: Controller 요청·응답 테스트**

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

- [ ] **Step 4: 전체 테스트와 커밋**

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

> 이 문서는 codex의 도움을 받아 작성하였습니다
