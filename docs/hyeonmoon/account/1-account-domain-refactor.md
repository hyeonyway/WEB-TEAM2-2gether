# Account 도메인 통합 리팩터링 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 같은 `users` 계정 정보를 나눠 소유하던 Auth와 User를 Account 도메인으로 통합하고 불필요한 Port·Adapter·중간 DTO를 제거한다.

**Architecture:** `account`가 `users`, `authentication`과 인증 유스케이스를 함께 소유한다. `AuthService`는 같은 도메인의 `AccountRepository`와 `Account`를 직접 사용하며, 다른 도메인은 기존처럼 `Integer userId`, JWT와 consumer-owned Port만 사용한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, MySQL 8.4, JUnit 5, Mockito

## Global Constraints

- DB의 `users`, `authentication`, `addresses` 테이블명과 컬럼을 변경하지 않는다.
- 다른 테이블의 `user_id` FK와 Java의 `Integer userId` 계약을 변경하지 않는다.
- `/api/auth/**` 요청·응답, Refresh Token 쿠키와 JWT claim을 변경하지 않는다.
- `global.security`의 `@CurrentUser`, SSE 티켓 인증과 필터 동작을 변경하지 않는다.
- Wallet의 회원가입 지갑 생성과 자금 처리 규칙을 변경하지 않는다.
- 다른 도메인의 Repository나 Entity를 직접 import하지 않는다.
- `UserAccountPort`, `UserAccountAdapter`, `UserAccount`, `UserAccountRole`은 통합 완료 후 남기지 않는다.
- 별도 `user_profiles` 테이블과 프로필 필드는 추가하지 않는다.

---

## 목표 구조

```text
com.dbidding.account
├── config/
├── controller/
├── cookie/
├── domain/
│   ├── Account.java
│   ├── AccountRole.java
│   ├── AccountStatus.java
│   └── Authentication.java
├── dto/
├── exception/
├── password/
├── port/
│   └── WalletProvisioningPort.java
├── repository/
│   ├── AccountRepository.java
│   └── AuthenticationRepository.java
├── service/
│   └── AuthService.java
└── token/
```

`Account`는 기존 `users` 테이블에 매핑한다. 외부 API와 DB에서는 기존 용어인 `userId`를 유지하되, Account 도메인 내부 Entity·역할·상태 타입만 Account 용어로 통일한다.

### Task 1: Account 계정 모델과 Repository 도입

**Files:**
- Create: `backend/src/main/java/com/dbidding/account/domain/Account.java`
- Create: `backend/src/main/java/com/dbidding/account/domain/AccountRole.java`
- Create: `backend/src/main/java/com/dbidding/account/domain/AccountStatus.java`
- Create: `backend/src/main/java/com/dbidding/account/repository/AccountRepository.java`
- Create: `backend/src/test/java/com/dbidding/account/domain/AccountTest.java`
- Create: `backend/src/test/java/com/dbidding/account/repository/AccountRepositoryTest.java`

**Interfaces:**
- Produces: `Account.create(String email, String nickname, String encryptedPassword, String salt)`
- Produces: `boolean AccountRepository.existsByEmail(String email)`
- Produces: `boolean AccountRepository.existsByNickname(String nickname)`
- Produces: `Optional<Account> AccountRepository.findByEmail(String email)`
- Preserves: `@Table(name = "users")`, `Integer id`, 기존 컬럼 길이와 UNIQUE 제약

- [x] **Step 1: Account 생성 규칙 테스트를 작성한다**

```java
@Test
void 신규_계정은_USER_ACTIVE_상태로_생성된다() {
    Account account = Account.create(
        "collector@example.com",
        "collector",
        "a".repeat(64),
        "b".repeat(32)
    );

    assertThat(account.getRole()).isEqualTo(AccountRole.USER);
    assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
}
```

- [x] **Step 2: Account Repository 실제 MySQL 테스트를 작성한다**

`Account.create()`로 저장한 계정을 이메일로 조회하고 이메일·닉네임 존재 여부가 true인지 검증한다. 같은 이메일 또는 닉네임을 저장하면 DB UNIQUE 제약으로 실패해야 한다.

- [x] **Step 3: 테스트가 Account 타입 부재로 실패하는지 확인한다**

```bash
cd backend
./gradlew test \
  --tests com.dbidding.account.domain.AccountTest \
  --tests com.dbidding.account.repository.AccountRepositoryTest
```

Expected: `Account`, `AccountRole`, `AccountStatus`, `AccountRepository`가 없어 컴파일 실패.

- [x] **Step 4: Account 모델과 Repository를 구현한다**

기존 User의 필드·JPA 매핑을 그대로 옮기고 이름만 Account 용어로 변경한다. `users` 테이블, 컬럼, ID 타입과 enum 문자열 값은 유지한다.

- [x] **Step 5: Account 테스트를 통과시키고 커밋한다**

```bash
./gradlew test \
  --tests com.dbidding.account.domain.AccountTest \
  --tests com.dbidding.account.repository.AccountRepositoryTest
git add backend/src/main/java/com/dbidding/account \
  backend/src/test/java/com/dbidding/account
git commit -m "refactor: Account 계정 모델 도입"
```

### Task 2: AuthService를 Account 모델에 직접 연결

**Files:**
- Modify: `backend/src/main/java/com/dbidding/auth/service/AuthService.java`
- Modify: `backend/src/main/java/com/dbidding/auth/dto/SignupResponse.java`
- Modify: `backend/src/main/java/com/dbidding/auth/token/JwtTokenProvider.java`
- Delete: `backend/src/main/java/com/dbidding/auth/port/UserAccount.java`
- Delete: `backend/src/main/java/com/dbidding/auth/port/UserAccountPort.java`
- Delete: `backend/src/main/java/com/dbidding/auth/port/UserAccountRole.java`
- Delete: `backend/src/main/java/com/dbidding/user/adapter/UserAccountAdapter.java`
- Delete: `backend/src/main/java/com/dbidding/user/domain/User.java`
- Delete: `backend/src/main/java/com/dbidding/user/domain/UserRole.java`
- Delete: `backend/src/main/java/com/dbidding/user/domain/UserStatus.java`
- Delete: `backend/src/main/java/com/dbidding/user/repository/UserRepository.java`
- Modify: `backend/src/test/java/com/dbidding/auth/service/AuthServiceSignupTest.java`
- Modify: `backend/src/test/java/com/dbidding/auth/service/AuthServiceLoginTest.java`
- Modify: `backend/src/test/java/com/dbidding/auth/service/AuthServiceRefreshTest.java`
- Modify: `backend/src/test/java/com/dbidding/auth/token/JwtTokenProviderTest.java`
- Delete: `backend/src/test/java/com/dbidding/user/adapter/UserAccountAdapterTest.java`
- Delete: `backend/src/test/java/com/dbidding/user/domain/UserTest.java`
- Delete: `backend/src/test/java/com/dbidding/user/repository/UserRepositoryTest.java`

**Interfaces:**
- Consumes: `AccountRepository`
- Consumes: `Account`, `AccountRole`, `AccountStatus`
- Preserves: `SignupResponse`, `LoginResult`, `RefreshResult`와 기존 Auth 예외
- Removes: `UserAccountPort`, `UserAccountAdapter`, `UserAccount`, `UserAccountRole`

- [x] **Step 1: AuthService 테스트를 AccountRepository 계약으로 변경한다**

회원가입 테스트는 이메일·닉네임 존재 여부와 `saveAndFlush(Account)`를 mock한다. 로그인·재발급 테스트는 `findByEmail()`과 `findById()`가 반환한 Account의 비밀번호 해시, salt, 상태와 역할을 사용하도록 변경한다.

- [x] **Step 2: 기존 AuthService에서 변경된 테스트가 실패하는지 확인한다**

```bash
./gradlew test \
  --tests 'com.dbidding.auth.service.AuthService*Test' \
  --tests com.dbidding.auth.token.JwtTokenProviderTest
```

Expected: AuthService가 아직 `UserAccountPort`와 `UserAccountRole`을 사용해 컴파일 또는 테스트 실패.

- [x] **Step 3: AuthService의 회원가입·로그인·재발급을 AccountRepository에 연결한다**

AuthService 생성자에서 `UserAccountPort`를 `AccountRepository`로 교체한다. 회원가입은 중복 사전 확인, 비밀번호 해싱, `Account.create()` 저장, Wallet 생성 순서로 처리한다. 로그인과 재발급은 Account를 조회해 `AccountStatus.ACTIVE`와 `AccountRole`을 사용한다.

- [x] **Step 4: UNIQUE 충돌 예외 계약을 AuthService에 유지한다**

동시 회원가입으로 `uk_users_email` 또는 `uk_users_nickname`이 충돌하면 각각 기존 `DuplicateEmailException`, `DuplicateNicknameException`으로 변환한다. 다른 무결성 예외는 원본 그대로 전파한다.

- [x] **Step 5: SignupResponse와 JWT 역할 타입을 Account로 변경한다**

`SignupResponse.from(Account)`와 `JwtTokenProvider.issue(Integer, AccountRole, Instant)`를 사용한다. JWT role claim의 문자열 `USER`와 `ADMIN`은 변경하지 않는다.

- [x] **Step 6: UserAccount Port·Adapter와 기존 User 모델을 제거한다**

새 Account 모델로 대체된 소스와 테스트만 삭제한다. WalletProvisioningPort는 다음 Task까지 현재 위치에 유지한다.

- [x] **Step 7: Auth와 Account 테스트를 통과시키고 커밋한다**

```bash
./gradlew test \
  --tests 'com.dbidding.auth.service.AuthService*Test' \
  --tests com.dbidding.auth.token.JwtTokenProviderTest \
  --tests 'com.dbidding.account.*'
git add backend/src/main/java/com/dbidding/auth \
  backend/src/main/java/com/dbidding/user \
  backend/src/main/java/com/dbidding/account \
  backend/src/test/java/com/dbidding/auth \
  backend/src/test/java/com/dbidding/user
git commit -m "refactor: Auth 유스케이스 Account 모델 통합"
```

### Task 3: Auth 패키지를 Account 도메인으로 통합

**Files:**
- Move: `backend/src/main/java/com/dbidding/auth/**` → `backend/src/main/java/com/dbidding/account/**`
- Move: `backend/src/test/java/com/dbidding/auth/**` → `backend/src/test/java/com/dbidding/account/**`
- Modify: `backend/src/main/java/com/dbidding/global/config/AuthFilterConfig.java`
- Modify: `backend/src/main/java/com/dbidding/global/security/JwtAuthFilter.java`
- Modify: `backend/src/main/java/com/dbidding/wallet/adapter/WalletProvisioningAdapter.java`
- Modify: 관련 global·wallet 테스트 import

**Interfaces:**
- Produces: `com.dbidding.account.port.WalletProvisioningPort`
- Produces: `com.dbidding.account.token.JwtTokenProvider`
- Produces: `com.dbidding.account.token.TokenClaims`
- Preserves: Spring Bean 이름·Profile·설정 property와 HTTP API

- [x] **Step 1: Account 패키지 경계 검증을 추가한다**

Account 소스에서 `package com.dbidding.auth`와 `com.dbidding.user` 참조가 남지 않아야 한다. Global과 Wallet 외의 다른 도메인은 Account Entity나 Repository를 import하지 않아야 한다.

- [x] **Step 2: Auth 소스와 테스트를 Account 아래로 이동한다**

기존 하위 패키지 구조는 유지하고 Java package 선언과 import의 `com.dbidding.auth`를 `com.dbidding.account`로 일괄 변경한다. 이미 존재하는 Account domain·repository 파일과 Authentication 파일은 같은 디렉터리에 함께 둔다.

- [x] **Step 3: Global JWT 연결 import를 변경한다**

`AuthFilterConfig`와 `JwtAuthFilter`, 관련 테스트가 Account의 `JwtTokenProvider`, `TokenClaims`와 예외를 사용하도록 변경한다. 필터 URL 제외 규칙과 request attribute는 변경하지 않는다.

- [x] **Step 4: Wallet provisioning Port import를 변경한다**

WalletProvisioningAdapter가 `com.dbidding.account.port.WalletProvisioningPort`를 구현하도록 변경한다. adapter는 계속 WalletService에 위임만 한다.

- [x] **Step 5: 패키지 이동 회귀 테스트를 실행한다**

```bash
./gradlew test \
  --tests 'com.dbidding.account.*' \
  --tests 'com.dbidding.global.security.*' \
  --tests com.dbidding.wallet.adapter.WalletProvisioningAdapterTest
```

Expected: Account, Global security와 Wallet 연결 테스트가 모두 통과.

- [x] **Step 6: 패키지 통합을 커밋한다**

```bash
git add backend/src/main/java/com/dbidding/account \
  backend/src/main/java/com/dbidding/auth \
  backend/src/main/java/com/dbidding/global \
  backend/src/main/java/com/dbidding/wallet \
  backend/src/test/java/com/dbidding/account \
  backend/src/test/java/com/dbidding/auth \
  backend/src/test/java/com/dbidding/global \
  backend/src/test/java/com/dbidding/wallet
git commit -m "refactor: Auth 패키지 Account 도메인 통합"
```

### Task 4: 통합 검증과 문서 정리

**Files:**
- Modify: `docs/hyeonmoon/README.md`
- Modify: `docs/hyeonmoon/account/1-account-domain-refactor.md`
- Modify: 필요한 기존 Auth/User 문서의 현재 구조 안내

- [x] **Step 1: 회원가입 원자성과 인증 회귀 테스트를 실행한다**

```bash
./gradlew test \
  --tests com.dbidding.account.integration.SignupTransactionTest \
  --tests 'com.dbidding.account.service.AuthService*Test' \
  --tests 'com.dbidding.account.repository.*' \
  --tests 'com.dbidding.account.token.*'
```

- [x] **Step 2: 외부 계약 회귀 테스트를 실행한다**

```bash
./gradlew test \
  --tests 'com.dbidding.global.security.*' \
  --tests com.dbidding.wallet.adapter.WalletProvisioningAdapterTest
```

- [x] **Step 3: 전체 백엔드 테스트를 실행한다**

```bash
./gradlew clean test
```

Expected: 실패 0건. 테스트 소스가 없는 패턴은 통과로 표현하지 않고 별도로 보고한다.

- [x] **Step 4: 의존성과 비변경 계약을 확인한다**

`auth`와 `user` Java 패키지, UserAccount Port·Adapter·DTO가 남지 않아야 한다. Account 외 다른 도메인에는 Account Entity·Repository 직접 import가 없어야 한다. schema.sql, `/api/auth/**`, JWT claim과 쿠키 설정에는 기능 변경 diff가 없어야 한다.

- [x] **Step 5: 문서와 이슈 체크리스트를 완료 상태로 갱신한다**

`docs/hyeonmoon/README.md`의 Auth/User 구현 순서를 Account로 통합하고, 기존 Auth/User 설계 문서는 역사적 경로임을 안내한다. 이슈 #98 체크리스트는 실제 검증 결과에 맞춰 갱신한다.

- [x] **Step 6: 문서 정리를 커밋한다**

```bash
git add docs/hyeonmoon
git commit -m "docs: Account 도메인 통합 상태 반영"
```

## 구현 결과

- `Account`가 기존 `users` 테이블 매핑과 계정 상태·역할을 소유한다.
- `AuthService`가 `AccountRepository`를 직접 사용하며 중복 사전 검사와 DB
  UNIQUE 충돌 예외 변환을 함께 담당한다.
- 기존 Auth 기능과 테스트는 `com.dbidding.account` 아래로 이동했다.
- Global JWT 필터와 Wallet provisioning은 Account가 공개한 타입만 사용한다.
- `./gradlew clean test` 기준 261개 테스트가 실행됐고 실패·오류·스킵은 없었다.

## 완료 조건

- Account가 users와 authentication, 회원가입·로그인·재발급·로그아웃을 소유한다.
- UserAccountPort, UserAccountAdapter와 UserAccount 중간 DTO가 제거된다.
- DB 스키마, `Integer userId`, Auth API, JWT, 쿠키와 Current User 계약이 유지된다.
- Global과 Wallet 외 다른 도메인은 Account 구현 타입을 import하지 않는다.
- Wallet 회원가입 생성과 Account 저장이 같은 트랜잭션으로 유지된다.
- 전체 백엔드 테스트가 실패 없이 통과한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
