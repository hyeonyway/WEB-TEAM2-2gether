# Login and Token Implementation Plan

**Goal:** 이메일과 비밀번호를 검증하고 Access Token은 응답 본문으로, Refresh Token은 HttpOnly 쿠키로 발급한다.

**Architecture:** JwtTokenProvider가 토큰 생성·검증을 전담하고 AuthService는 `UserAccountPort`를 통한 사용자 검증과 Authentication 저장을 조정한다. Auth는 User Entity와 UserRepository를 직접 참조하지 않으며, DB에는 Refresh Token 원문 대신 SHA-256 hex hash만 저장한다.

**Tech Stack:** JJWT 0.13.0, Java 21, Spring MVC, JUnit 5, Mockito

## Global Constraints

- Access Token은 기본 30분, Refresh Token은 기본 7일이다.
- 토큰 비밀키와 만료시간은 환경변수로 덮어쓸 수 있어야 한다.
- Access와 Refresh의 `type` claim을 반드시 검증한다.
- Refresh 원문은 로그와 응답 JSON에 남기지 않는다.

---

### Task 1: JJWT와 인증 설정

**Files:**
- Modify: `backend/build.gradle`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/dbidding/DbiddingApplication.java`
- Create: `backend/src/main/java/com/dbidding/auth/JwtProperties.java`

- [x] **Step 1: JJWT 의존성 추가**

```gradle
implementation 'io.jsonwebtoken:jjwt-api:0.13.0'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.13.0'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.13.0'
```

- [x] **Step 2: 환경변수 기반 설정 추가**

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-seconds: ${JWT_ACCESS_SECONDS:1800}
    refresh-token-seconds: ${JWT_REFRESH_SECONDS:604800}
    secure-cookie: ${JWT_SECURE_COOKIE:false}
```

JWT secret이 없으면 운영뿐 아니라 로컬에서도 애플리케이션 시작을 실패시켜 하드코딩된 기본 키 사용을 막는다. HS256 비밀키는 최소 32바이트다.

- [x] **Step 3: 설정 바인딩 등록**

```java
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    long accessTokenSeconds,
    long refreshTokenSeconds,
    boolean secureCookie
) {}
```

`DbiddingApplication`에 `@ConfigurationPropertiesScan`을 추가한다.

- [x] **Step 4: 설정 바인딩 테스트**

```bash
JWT_SECRET='local-development-secret-at-least-32-bytes' ./gradlew test
```

Expected: 설정 바인딩 오류 없이 실행된다.

### Task 2: JwtTokenProvider

**Files:**
- Create: `backend/src/main/java/com/dbidding/auth/JwtTokenProvider.java`
- Create: `backend/src/main/java/com/dbidding/auth/IssuedTokens.java`
- Create: `backend/src/main/java/com/dbidding/auth/TokenClaims.java`
- Create: `backend/src/main/java/com/dbidding/auth/TokenType.java`
- Create: `backend/src/main/java/com/dbidding/auth/RefreshTokenHasher.java`
- Test: `backend/src/test/java/com/dbidding/auth/JwtTokenProviderTest.java`

**Interfaces:**
- Produces: `IssuedTokens JwtTokenProvider.issue(Integer userId, UserAccountRole role, Instant now)`
- Produces: `TokenClaims JwtTokenProvider.parseRefresh(String token)`
- Produces: `String RefreshTokenHasher.hash(String token)`

- [ ] **Step 1: 토큰 claim 실패 테스트 작성**

```java
@Test
void access와_refresh에_서로_다른_type과_만료시간을_넣는다() {
    IssuedTokens tokens = provider.issue(1, UserAccountRole.USER, now);

    assertThat(parse(tokens.accessToken()).get("type")).isEqualTo("access");
    assertThat(parse(tokens.refreshToken()).get("type")).isEqualTo("refresh");
    assertThat(tokens.refreshExpiresAt()).isAfter(tokens.accessExpiresAt());
}
```

- [ ] **Step 2: JJWT 0.13 API로 발급 구현**

Access Token에는 최소 claim만 포함한다.

```java
Jwts.builder()
    .subject(userId.toString())
    .claim("role", role.name())
    .claim("type", tokenType.value())
    .issuedAt(Date.from(now))
    .expiration(Date.from(expiresAt))
    .signWith(secretKey)
    .compact();
```

Refresh Token은 동일한 등록 claim 중 `role`을 제외하고 `type=refresh`로 발급한다.
Refresh 검증 결과는 사용자 ID와 token type만 반환한다.

검증은 다음 API를 사용한다.

```java
Jwts.parser()
    .verifyWith(secretKey)
    .build()
    .parseSignedClaims(token)
    .getPayload();
```

- [ ] **Step 3: 잘못된 서명·만료·type·role 테스트**

각 경우 `InvalidTokenException`, `ExpiredTokenException`, `InvalidTokenTypeException`, `InvalidTokenRoleException`으로 변환하고 JJWT 예외를 Controller 밖으로 노출하지 않는다. 발급 API는 `UserAccountRole`만 받아 임의 문자열을 claim에 넣지 못하게 하고, Access Token 파싱 시 서명은 유효해도 허용 집합에 없는 role claim은 거부한다.

- [ ] **Step 4: Refresh SHA-256 해시 테스트**

```java
@Test
void refresh_token은_64자_sha256_hex로_변환한다() {
    assertThat(hasher.hash("refresh-token")).matches("[0-9a-f]{64}");
}
```

### Task 3: 로그인 서비스

**Files:**
- Create: `backend/src/main/java/com/dbidding/auth/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/dbidding/auth/dto/LoginResponse.java`
- Create: `backend/src/main/java/com/dbidding/auth/LoginResult.java`
- Modify: `backend/src/main/java/com/dbidding/auth/AuthService.java`
- Test: `backend/src/test/java/com/dbidding/auth/AuthServiceLoginTest.java`

**Interfaces:**
- Consumes: `UserAccountPort.findByEmail`
- Consumes: `PasswordHasher.matches`
- Produces: `LoginResult AuthService.login(LoginRequest request)`

- [ ] **Step 1: 존재하지 않는 이메일과 오답 비밀번호 테스트**

두 경우 모두 외부 응답은 동일한 `InvalidCredentialsException`으로 처리해 계정 존재 여부를 노출하지 않는다.

- [ ] **Step 2: 로그인 성공 테스트**

```java
@Test
void 로그인하면_refresh_hash를_저장하고_access를_반환한다() {
    given(userAccountPort.findByEmail(request.email())).willReturn(Optional.of(user));
    given(passwordHasher.matches(request.password(), user.salt(), user.encryptedPassword())).willReturn(true);
    given(jwtTokenProvider.issue(eq(1), eq(UserAccountRole.USER), any())).willReturn(tokens);

    LoginResult result = authService.login(request);

    assertThat(result.response().accessToken()).isEqualTo(tokens.accessToken());
    then(authenticationRepository).should().save(argThat(auth ->
        auth.getRefreshTokenHash().equals(refreshTokenHasher.hash(tokens.refreshToken()))
    ));
}
```

- [ ] **Step 3: 기존 Authentication은 새 hash로 교체**

첫 로그인은 새 row를 저장하고 이후 로그인은 같은 `user_id` row의 hash를 rotate한다. 사용자당 활성 Refresh는 하나만 유지한다.

### Task 4: 로그인 Controller와 쿠키

**Files:**
- Create: `backend/src/main/java/com/dbidding/auth/RefreshCookieFactory.java`
- Modify: `backend/src/main/java/com/dbidding/auth/AuthController.java`
- Test: `backend/src/test/java/com/dbidding/auth/AuthControllerLoginTest.java`

- [ ] **Step 1: 쿠키 속성 테스트**

```java
assertThat(cookie.isHttpOnly()).isTrue();
assertThat(cookie.getPath()).isEqualTo("/api/auth");
assertThat(cookie.getSameSite()).isEqualTo("Strict");
```

운영에서는 `Secure=true`, 로컬에서는 설정값에 따라 `false`를 허용한다.

- [ ] **Step 2: 로그인 응답 테스트**

```java
mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"email":"collector@example.com","password":"Password123!"}"""))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.accessToken").isString())
    .andExpect(cookie().httpOnly("refreshToken", true));
```

- [ ] **Step 3: 전체 테스트 및 커밋**

```bash
JWT_SECRET='local-development-secret-at-least-32-bytes' ./gradlew clean test
git add backend/build.gradle backend/src/main/resources/application.yml \
  backend/src/main/java/com/dbidding/auth backend/src/test/java/com/dbidding/auth
git commit -m "feat: 로그인과 JWT 발급 구현"
```

## 완료 조건

- 로그인 실패 응답으로 이메일 존재 여부를 추측할 수 없다.
- Access Token만 JSON에 나타난다.
- Refresh Token 원문은 HttpOnly 쿠키로만 전달된다.
- DB에는 Refresh Token의 64자 SHA-256 hash만 남는다.
- Auth는 User Entity와 UserRepository를 직접 import하지 않는다.
- JWT 발급과 검증에서 역할은 Auth 소유 `UserAccountRole`의 허용 값만 사용한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
