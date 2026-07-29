# Auth 개발 계획

Auth는 User 계정, 비밀번호 검증, JWT 발급과 Refresh Token 생명주기를 소유한다. 인증 구현이 완료되기 전까지 다른 도메인은 `debug-auth` 프로필에서만 `X-Debug-User-Id`를 사용하므로, Auth 구현 중간 결과를 전역 필터에 연결하지 않는다.

## 구현 단계

1. [User·Authentication 엔티티](1-entity.md)
2. [회원가입](2-signup.md)
3. [로그인과 Access/Refresh 발급](3-login-and-token.md)
4. [Refresh Rotation과 로그아웃](4-refresh-and-logout.md)
5. [CurrentUserProvider와 SSE 티켓 인증](5-current-user-and-sse-auth.md) — 다른 도메인이 지금 바로 `@CurrentUser`를 쓸 수 있게 공통 계약과 디버그 필터를 먼저 공유

## API

| Method | Path | 인증 | 기능 |
|---|---|---|---|
| POST | `/api/auth/signup` | 불필요 | User와 Wallet 생성 |
| POST | `/api/auth/login` | 불필요 | Access 응답, Refresh 쿠키 발급 |
| POST | `/api/auth/refresh` | Refresh 쿠키 | Access 재발급, Refresh 회전 |
| POST | `/api/auth/logout` | Refresh 쿠키 | Refresh 폐기 및 쿠키 만료 |

## 주요 규칙

- 회원가입 입력은 `email`, `password`, `nickname`만 사용한다.
- User 기본값은 `role=USER`, `status=ACTIVE`이며 MVP에서는 프로필 이미지를 저장하지 않는다.
- Authentication row는 회원가입 시 만들지 않고 첫 로그인 성공 시 생성한다.
- 사용자당 Authentication row가 하나이므로 MVP는 한 사용자의 활성 Refresh Token을 하나만 허용한다.
- Access Token에는 `sub=userId`, `role`, `type=access`, `iat`, `exp`만 넣는다.
- Refresh Token에는 `sub=userId`, `type=refresh`, `iat`, `exp`, 고유한 `jti`를
  넣어 같은 시각의 Rotation에서도 새 토큰이 기존 토큰과 달라지게 한다.
- JWT 비밀키와 만료시간은 환경변수로 받는다.

## 완료 기준

- 회원가입, 로그인, refresh, logout 서비스 테스트가 독립적으로 통과한다.
- Refresh Token 원문과 비밀번호 평문이 DB에 저장되지 않는다.
- 잘못된 서명, 만료, token type과 Access Token의 잘못된 role을 각각 정의된
  인증 실패로 처리한다.
- 실제 `JwtAuthFilter` 전역 적용은 인증 통합 스프린트까지 보류한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
