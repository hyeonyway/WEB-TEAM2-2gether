# Refresh Rotation and Logout Implementation Plan

**Goal:** 유효한 Refresh Token을 한 번만 사용할 수 있도록 회전하고 로그아웃 시 서버 저장값과 쿠키를 모두 폐기한다.

**Architecture:** Refresh 요청은 JWT 자체 검증 후 DB hash 비교를 수행한다. Rotation은 Authentication row를 비관적 쓰기 잠금으로 조회하고 기존 hash를 같은 트랜잭션에서 교체한다. 사용자 정보는 `UserAccountPort`로 조회하며 User Entity와 UserRepository를 직접 참조하지 않는다. 현재 스키마는 token family를 저장하지 않으므로 MVP는 재사용 요청을 401로 거절하되 family 추적은 하지 않는다.

**Tech Stack:** Spring Transaction, JJWT 0.13.0, MockMvc, JUnit 5, Mockito

## Global Constraints

- Refresh 요청 본문은 없고 `refreshToken` HttpOnly 쿠키만 사용한다.
- Refresh 성공 시 Access와 Refresh를 모두 새로 발급한다.
- 이전 Refresh Token은 Rotation 커밋 이후 사용할 수 없다.
- 로그아웃은 같은 요청을 반복해도 204를 반환하는 멱등 동작으로 만든다.

---

### Task 1: Refresh Rotation 서비스

**Files:**
- Create: `backend/src/main/java/com/dbidding/auth/dto/RefreshResponse.java`
- Create: `backend/src/main/java/com/dbidding/auth/service/RefreshResult.java`
- Modify: `backend/src/main/java/com/dbidding/auth/service/AuthService.java`
- Test: `backend/src/test/java/com/dbidding/auth/service/AuthServiceRefreshTest.java`

**Interfaces:**
- Consumes: `JwtTokenProvider.parseRefresh(String token)`
- Consumes: `AuthenticationRepository.findByUserIdForUpdate(Integer userId)`
- Consumes: `UserAccountPort.findById(Integer userId)`
- Produces: `RefreshResult AuthService.refresh(String refreshToken)`

- [ ] **Step 1: 저장 hash 불일치 실패 테스트**

```java
@Test
void 이미_회전된_refresh_token은_거절한다() {
    given(jwtTokenProvider.parseRefresh(oldToken)).willReturn(new TokenClaims(1, TokenType.REFRESH));
    given(authenticationRepository.findByUserIdForUpdate(1)).willReturn(Optional.of(authentication));

    assertThatThrownBy(() -> authService.refresh(oldToken))
        .isInstanceOf(InvalidRefreshTokenException.class);
}
```

- [ ] **Step 2: 성공 Rotation 테스트**

새 토큰 발급 후 `Authentication.rotate(newHash)`가 호출되고 응답에는 새 Access만 포함되는지 검증한다.

- [ ] **Step 3: 트랜잭션 서비스 구현**

```java
@Transactional
public RefreshResult refresh(String refreshToken) {
    TokenClaims claims = jwtTokenProvider.parseRefresh(refreshToken);
    Authentication authentication = authenticationRepository.findByUserIdForUpdate(claims.userId())
        .orElseThrow(InvalidRefreshTokenException::new);

    String presentedHash = refreshTokenHasher.hash(refreshToken);
    if (!MessageDigest.isEqual(
            presentedHash.getBytes(StandardCharsets.US_ASCII),
            authentication.getRefreshTokenHash().getBytes(StandardCharsets.US_ASCII))) {
        throw new InvalidRefreshTokenException();
    }

    UserAccount user = userAccountPort.findById(claims.userId())
        .orElseThrow(InvalidRefreshTokenException::new);
    IssuedTokens next = jwtTokenProvider.issue(user.id(), user.role(), clock.instant());
    authentication.rotate(refreshTokenHasher.hash(next.refreshToken()));
    return RefreshResult.of(next);
}
```

- [ ] **Step 4: 두 동시 Refresh 요청 테스트 계획**

동일 토큰으로 두 트랜잭션이 동시에 들어오면 일반 조회로는 둘 다 기존 hash 검증을 통과할 수 있다. `AuthenticationRepository.findByUserIdForUpdate()`에 `PESSIMISTIC_WRITE`를 적용하고 위 서비스 흐름에서도 반드시 이 메서드를 사용해 Rotation을 직렬화한다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from Authentication a where a.userId = :userId")
Optional<Authentication> findByUserIdForUpdate(Integer userId);
```

두 번째 요청은 첫 번째 커밋 후 hash 불일치로 401이어야 한다.

### Task 2: Refresh Controller

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auth/controller/AuthController.java`
- Test: `backend/src/test/java/com/dbidding/auth/controller/AuthControllerRefreshTest.java`

- [ ] **Step 1: 쿠키 누락 테스트**

`refreshToken` 쿠키가 없으면 `401 REFRESH_TOKEN_MISSING`을 반환한다.

- [ ] **Step 2: 성공 응답 테스트**

```java
mockMvc.perform(post("/api/auth/refresh")
        .cookie(new Cookie("refreshToken", oldToken)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.accessToken").value(newAccessToken))
    .andExpect(cookie().value("refreshToken", newRefreshToken))
    .andExpect(cookie().httpOnly("refreshToken", true));
```

### Task 3: 로그아웃

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auth/service/AuthService.java`
- Modify: `backend/src/main/java/com/dbidding/auth/controller/AuthController.java`
- Test: `backend/src/test/java/com/dbidding/auth/service/AuthServiceLogoutTest.java`
- Test: `backend/src/test/java/com/dbidding/auth/controller/AuthControllerLogoutTest.java`

**Interfaces:**
- Produces: `void AuthService.logout(String refreshToken)`
- Produces: `POST /api/auth/logout`

- [ ] **Step 1: 서비스 멱등성 테스트**

제출된 Refresh Token을 SHA-256으로 해싱하고 `findByRefreshTokenHash(hash)`로 Authentication을 찾아 삭제한다. 이 방식은 JWT가 만료됐어도 서버에 남은 동일 hash를 제거할 수 있다. DB row가 없거나 토큰 형식이 잘못돼도 로그아웃은 성공 처리하고 민감한 상태를 노출하지 않는다.

- [ ] **Step 2: 쓰기 트랜잭션 서비스 구현**

로그아웃 서비스 메서드에 `@Transactional`을 적용한다. `AuthenticationRepository.deleteByUserId(...)` 같은 직접 선언 삭제 메서드는 Repository 테스트의 트랜잭션에 기대지 않고 반드시 이 서비스 트랜잭션 안에서 호출한다.

- [ ] **Step 3: 만료 쿠키 생성**

```http
Set-Cookie: refreshToken=; Max-Age=0; HttpOnly; Path=/api/auth; SameSite=Strict
```

발급 쿠키와 삭제 쿠키의 이름, path, secure, same-site 속성을 동일하게 유지한다.

- [ ] **Step 4: Controller 204 테스트**

```java
mockMvc.perform(post("/api/auth/logout")
        .cookie(new Cookie("refreshToken", refreshToken)))
    .andExpect(status().isNoContent())
    .andExpect(cookie().maxAge("refreshToken", 0));
```

- [ ] **Step 5: 전체 테스트 및 커밋**

```bash
JWT_SECRET='local-development-secret-at-least-32-bytes' ./gradlew clean test
git add backend/src/main/java/com/dbidding/auth backend/src/test/java/com/dbidding/auth
git commit -m "feat: Refresh Rotation과 로그아웃 구현"
```

## 완료 조건

- 동일 Refresh Token의 순차 재사용은 401이다.
- 동시 Refresh 요청 중 하나만 성공한다.
- Rotation의 hash 비교와 갱신은 `findByUserIdForUpdate()`가 획득한 쓰기 잠금 안에서 수행된다.
- Rotation 시 새 hash가 커밋되기 전 새 쿠키 응답을 만들지 않는다.
- 로그아웃 후 기존 Refresh Token으로 재발급할 수 없다.
- 로그아웃의 인증정보 삭제는 서비스 `@Transactional` 경계 안에서 실행된다.
- Auth는 User Entity와 UserRepository를 직접 import하지 않는다.
- 현재 스키마에서 지원하지 않는 token family 탐지는 구현했다고 주장하지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
