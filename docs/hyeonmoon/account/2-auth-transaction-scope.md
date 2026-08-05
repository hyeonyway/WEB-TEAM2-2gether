# Auth 트랜잭션 범위 축소 Implementation Plan

**Goal:** `login()`/`signup()`의 `@Transactional` 범위에서 DB가 필요 없는
PBKDF2 해싱(CPU 작업)을 빼서, HikariCP 커넥션 점유 시간을 줄인다.

**Architecture:** 계정 조회·비밀번호 검증/해싱·JWT 발급은 트랜잭션 밖에서
수행한다. DB 쓰기(refresh token upsert, account 저장 + wallet 생성)는 별도
`AuthTransactionService`의 짧은 `@Transactional` 메서드로 감싼다. 외부 API
계약(`/api/auth/**` 요청/응답, JWT claim, 쿠키)은 변경하지 않는다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, JUnit 5, Mockito

## Global Constraints

- `/api/auth/**` 요청·응답, JWT claim, 쿠키 동작은 바꾸지 않는다.
- 회원가입의 account 저장과 wallet 생성 원자성(`WalletProvisioningPort`
  MANDATORY 전파)은 유지한다.
- 비밀번호 해싱 규칙(PBKDF2WithHmacSHA256, 60만 iteration)은 바꾸지 않는다.
- 존재하지 않는 계정에 대한 timing-attack 방지용 dummy hash 비교
  (`login()`의 `DUMMY_PASSWORD_HASH`/`DUMMY_PASSWORD_SALT` 경로)는 그대로
  유지한다.

## 배경

`AuthService.login()`/`signup()`은 메서드 전체가 `@Transactional`이라,
`passwordHasher.matches()`/`hash()`(PBKDF2 60만 회, ~60~100ms CPU) 동안에도
DB 커넥션을 붙잡는다. HikariCP 풀이 10개뿐인 환경(`hikaricp_connections_max=10`,
운영 backend 확인됨)에서, 로그인/가입 폭주가 입찰 트랜잭션과 같은 풀을 두고
경쟁해 커넥션 획득 대기시간이 늘어난다. 이는 Wallet hold 초과 버그(#198)
조사 중 확인된 p99 저하 원인(HikariCP 풀 포화)과 같은 종류의 문제다.

### Task 1: `login()` 트랜잭션 범위 축소

**Files:**
- Modify: `backend/src/main/java/com/dbidding/account/service/AuthService.java`
- Create: `backend/src/main/java/com/dbidding/account/service/AuthTransactionService.java`
- Modify: `backend/src/test/java/com/dbidding/account/service/AuthServiceLoginTest.java`
- Create: `backend/src/test/java/com/dbidding/account/integration/AuthTransactionScopeTest.java`

**Interfaces:**
- Produces: `void AuthTransactionService.persistRefreshToken(Integer accountId, String refreshTokenHash)` (`@Transactional`)
- Preserves: `LoginResult login(LoginRequest)` 시그니처와 예외 계약
  (`InvalidCredentialsException`)

- [x] **Step 1: 커넥션 점유 범위 검증 테스트를 작성한다**

  `login()` 호출 중 `passwordHasher.matches()` 실행 시점에는 어떤
  Repository도 트랜잭션 참여 상태가 아님을 확인하는 테스트를 추가한다
  (Mockito로 `passwordHasher.matches()` 호출 시점에
  `TransactionSynchronizationManager.isActualTransactionActive()`가
  `false`인지 검증).

- [x] **Step 2: 테스트가 실패하는지 확인한다**

  ```bash
  cd backend
  ./gradlew test --tests com.dbidding.account.integration.AuthTransactionScopeTest
  ```

  Expected: 현재 구조에서는 해싱 시점에도 트랜잭션이 활성 상태라 실패.

- [x] **Step 3: `login()`에서 `@Transactional`을 제거하고 쓰기 부분만
  분리한다**

  ```java
  public LoginResult login(LoginRequest request) {
      Account account = accountRepository.findByEmail(request.email()).orElse(null);
      if (account == null) {
          passwordHasher.matches(request.password(), DUMMY_PASSWORD_SALT, DUMMY_PASSWORD_HASH);
          throw new InvalidCredentialsException();
      }
      boolean passwordMatches = passwordHasher.matches(
          request.password(), account.getSalt(), account.getEncryptedPassword()
      );
      if (!passwordMatches || account.getStatus() != AccountStatus.ACTIVE) {
          throw new InvalidCredentialsException();
      }
      IssuedTokens tokens = jwtTokenProvider.issue(account.getId(), account.getRole(), Instant.now());
      String refreshTokenHash = refreshTokenHasher.hash(tokens.refreshToken());
      authTransactionService.persistRefreshToken(account.getId(), refreshTokenHash);
      return new LoginResult(new LoginResponse(tokens.accessToken()), tokens.refreshToken());
  }

  // AuthTransactionService
  @Transactional
  public void persistRefreshToken(Integer accountId, String refreshTokenHash) {
      authenticationRepository.upsertRefreshTokenHash(accountId, refreshTokenHash);
  }
  ```

  Same-class self-invocation은 Spring AOP 프록시를 타지 않으므로
  `AuthTransactionService`를 별도 Spring Bean으로 분리한다. 통합 테스트는
  PBKDF2 구간의 트랜잭션 비활성과 upsert 구간의 활성 상태를 함께 확인한다.

- [x] **Step 4: 로그인 관련 테스트를 통과시키고 커밋한다**

  ```bash
  ./gradlew test --tests 'com.dbidding.account.service.AuthService*Test'
  git add backend/src/main/java/com/dbidding/account/service/AuthService.java \
    backend/src/test/java/com/dbidding/account/service/AuthServiceLoginTest.java
  git commit -m "refactor: login 트랜잭션 범위를 쓰기 구간으로 축소"
  ```

### Task 2: `signup()` 트랜잭션 범위 축소

**Files:**
- Modify: `backend/src/main/java/com/dbidding/account/service/AuthService.java`
- Create: `backend/src/main/java/com/dbidding/account/service/AuthTransactionService.java`
- Modify: `backend/src/test/java/com/dbidding/account/service/AuthServiceSignupTest.java`
- Create: `backend/src/test/java/com/dbidding/account/integration/AuthTransactionScopeTest.java`

**Interfaces:**
- Preserves: `SignupResponse signup(SignupRequest)` 시그니처, 원자성
  (`DuplicateEmailException`/`DuplicateNicknameException` 변환 포함),
  account 저장 + wallet 생성 원자성

- [x] **Step 1: 해싱이 트랜잭션 밖에서 실행되는지 검증하는 테스트를
  추가한다** (Task 1의 Step 1과 동일한 방식)

- [x] **Step 2: 실패 확인**

  ```bash
  ./gradlew test --tests com.dbidding.account.integration.AuthTransactionScopeTest
  ```

- [x] **Step 3: 중복 체크·해싱을 트랜잭션 밖으로, 저장+지갑생성만 안으로
  재구성한다**

  ```java
  public SignupResponse signup(SignupRequest request) {
      if (accountRepository.existsByEmail(request.email())) throw new DuplicateEmailException();
      if (accountRepository.existsByNickname(request.nickname())) throw new DuplicateNicknameException();
      PasswordHash password = passwordHasher.hash(request.password());
      return authTransactionService.createAccountWithWallet(request, password);
  }

  // AuthTransactionService
  @Transactional
  public SignupResponse createAccountWithWallet(SignupRequest request, PasswordHash password) {
      Account account = Account.create(request.email(), request.nickname(),
          password.encryptedPassword(), password.salt());
      try {
          account = accountRepository.saveAndFlush(account);
      } catch (DataIntegrityViolationException exception) {
          if (isConstraintViolation(exception, EMAIL_UNIQUE_CONSTRAINT)) throw new DuplicateEmailException(exception);
          if (isConstraintViolation(exception, NICKNAME_UNIQUE_CONSTRAINT)) throw new DuplicateNicknameException(exception);
          throw exception;
      }
      walletProvisioningPort.createFor(account.getId());
      return SignupResponse.from(account);
  }
  ```

  DB UNIQUE 제약 기반 동시 가입 충돌 처리(`DataIntegrityViolationException`
  변환)는 트랜잭션 안(`createAccountWithWallet`)에 그대로 둔다 — 사전
  존재 체크는 이제 트랜잭션 밖의 참고용 빠른 실패일 뿐, 실제 무결성 보장은
  여전히 DB 제약이 한다.

- [x] **Step 4: 가입 관련 테스트와 원자성 통합 테스트를 통과시키고
  커밋한다**

  ```bash
  ./gradlew test --tests 'com.dbidding.account.service.AuthService*Test' \
    --tests com.dbidding.account.integration.SignupTransactionTest
  git add backend/src/main/java/com/dbidding/account/service/AuthService.java \
    backend/src/test/java/com/dbidding/account/service/AuthServiceSignupTest.java
  git commit -m "refactor: signup 트랜잭션 범위를 저장 구간으로 축소"
  ```

### Task 3: 전체 검증

**Files:**
- Modify: `docs/hyeonmoon/README.md`

- [ ] **Step 1: 전체 Backend 테스트 실행**

  ```bash
  ./gradlew clean test
  ```

  Expected: 실패 0건. 테스트 소스 없는 항목은 `NO-SOURCE`로 별도 보고.

- [x] **Step 2: 문서 인덱스 갱신 후 커밋**

  ```bash
  git add docs/hyeonmoon/README.md docs/hyeonmoon/account/2-auth-transaction-scope.md
  git commit -m "docs: Auth 트랜잭션 범위 축소 계획 반영"
  ```

## 완료 조건

- `login()`/`signup()`의 PBKDF2 해싱 구간에서 DB 트랜잭션이 활성 상태가
  아니다.
- 회원가입의 account 저장 + wallet 생성 원자성, 로그인/가입 API 계약,
  JWT/쿠키 동작은 변경 없다.
- 전체 Backend 테스트가 실패 없이 통과한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
