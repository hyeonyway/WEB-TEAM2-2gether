# A 도메인 개발 계획

김현문 담당 영역인 Auth, User, Wallet의 구현 계획을 실행 순서대로 정리한다. 이 문서는 전체 진행 순서를 안내하고, 구체적인 파일과 테스트 절차는 각 도메인 문서에서 다룬다.

## 목표

1. User, Authentication, Wallet 엔티티를 현재 MySQL 스키마와 일치시킨다.
2. 회원가입 시 User와 초기 Wallet을 하나의 트랜잭션으로 생성한다.
3. 로그인과 JWT Access/Refresh Token 발급을 구현한다.
4. Refresh Token Rotation과 로그아웃을 구현한다.
5. 배송지 CRUD와 지갑 잔액 조회 API를 구현한다.

## 확정 계약

- Java 21, Spring Boot 4.1.0, MySQL 8.4를 사용한다.
- `schema.sql`이 데이터 모델의 원본이며 JPA는 `ddl-auto=validate`로 검증만 한다.
- User, Authentication, Wallet, Address의 ID와 관련 FK는 `Integer`다.
- 금액은 MySQL `BIGINT`, Java `long`으로 통일한다.
- API 경로는 `/api/**`를 사용한다.
- Spring Security는 사용하지 않는다.
- 비밀번호는 `PBKDF2WithHmacSHA256`으로 해싱한다.
- Access Token은 응답 본문으로 반환하고 이후 `Authorization: Bearer` 헤더로 받는다.
- Refresh Token은 HttpOnly 쿠키로 전달하며 DB에는 SHA-256 해시를 저장한다.
- 실제 JWT 필터를 전역 적용하기 전에는 비운영 환경의 `X-Debug-User-Id` 필터를 사용한다.
- 다른 도메인의 Entity나 Repository를 직접 import하지 않고 consumer-owned port로 연결한다.
- JPA 엔티티는 Lombok의 `@Getter`와
  `@NoArgsConstructor(access = AccessLevel.PROTECTED)`만 기본으로 사용한다.
  `@Data`, `@Setter`, 공개 기본 생성자는 사용하지 않는다.

## 패키지 경계

```text
auth
├── controller
├── service
├── domain
├── repository
├── dto
├── port
├── token
├── config
├── cookie
├── password
└── exception

user
├── domain
├── repository
├── adapter
├── controller
├── service
└── dto

wallet
├── domain
├── repository
├── adapter
├── controller
├── service
├── dto
└── exception
```

엔티티는 `domain`, Spring Data 인터페이스는 `repository`, 다른 도메인이 소유한
Port의 구현체는 `adapter`에 둔다. 유스케이스와 HTTP 진입점은 각각 `service`,
`controller`에 두고 요청·응답 모델은 `dto`에 둔다. Auth의 JWT 구현은 `token`,
JWT 설정은 `config`, Refresh 쿠키 생성은 `cookie`, 비밀번호 해시는
`password`가 소유한다.

`User`와 `UserRepository`는 계정 정보를 소유하는 `user.domain`과
`user.repository`에 둔다. `auth`는 사용자 조회·등록에 필요한
`auth.port.UserAccountPort`를 소유하고 해당 Port만 의존한다. `user.adapter`는
`UserRepository`를 사용해 이 Port를 구현하며, `auth`는 `user`의 Entity나
Repository를 직접 import하지 않는다.

## 실행 순서

| 순서 | 문서 | 완료 결과 |
|---|---|---|
| 1 | [Auth 엔티티](auth/1-entity.md) | User와 Authentication 매핑 및 Repository |
| 2 | [Wallet 엔티티](wallet/1-entity.md) | Wallet 매핑 및 Repository |
| 3 | [Wallet 생성 연동](wallet/2-wallet-provisioning.md) | Auth가 사용할 WalletProvisioningPort와 구현체 |
| 4 | [회원가입](auth/2-signup.md) | User와 Wallet의 원자적 생성 |
| 5 | [로그인과 토큰](auth/3-login-and-token.md) | 로그인, Access/Refresh 발급 |
| 6 | [Refresh와 로그아웃](auth/4-refresh-and-logout.md) | Rotation, 로그아웃 |
| 7 | [배송지 CRUD](user/1-address-crud.md) | 로그인 사용자 배송지 관리 |
| 8 | [지갑 잔액 조회](wallet/3-balance-query.md) | 총액·동결액·가용액 조회 |

문서 번호는 도메인 안의 책임 순서를 나타낸다. 도메인 사이의 실제 구현은
Auth 1 → Wallet 1·2 → Auth 2 순서로 진행한다.

## 공통 테스트 규칙

- 서비스 단위 테스트는 JUnit 5, AssertJ, Mockito를 사용한다.
- Controller 테스트는 `MockMvc`를 사용한다.
- Repository 매핑 검증은 로컬 MySQL 테스트 DB에 `schema.sql`을 적용한 뒤 실행한다.
- 각 작업은 실패 테스트 작성 → 실패 확인 → 최소 구현 → 전체 테스트 순서로 진행한다.
- 기본 검증 명령은 다음과 같다.

```bash
cd backend
./gradlew clean test
```

현재 테스트 소스가 없는 경우 `NO-SOURCE`를 성공한 테스트처럼 보고하지 않는다.

## Wallet 원장 제약

- 일반 충전·환불에는 경매가 없으므로 `point_records.auction_id`는 nullable이다.
- 활성 hold에는 해제 시각이 없으므로 `wallet_holds.released_at`은 nullable이다.
- Wallet 상태와 거래 유형 문자열은 각각 `VARCHAR(20)`, `VARCHAR(32)`를 사용한다.

## 참고 문서

- `docs/DB_SETUP.md`
- `backend/src/main/resources/schema.sql`
- `../docs/module-interfaces.md`
- `../docs/package-structure.md`
- `../docs/erd-review.md`

> 이 문서는 codex의 도움을 받아 작성하였습니다
