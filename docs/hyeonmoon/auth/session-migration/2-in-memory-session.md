# 이슈 2: 인메모리 세션 인증 구현

## 1. 이슈 경계

- 구현 이슈: [#216 단일 인스턴스 인메모리 세션 인증 구현](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/216)
- 선행 문서: [`1-auth-boundary-refactoring.md`](1-auth-boundary-refactoring.md)
- 목표: 공통 인증 계약의 두 번째 구현체로 단일 인스턴스 HttpSession 인증 추가
- 비목표: Redis, 다중 인스턴스, 프론트 전환, CSRF 완성, 개인화 SSE 전환
- 후속 문서: [`3-session-security-frontend-sse.md`](3-session-security-frontend-sse.md)

이 단계는 세션 인증의 서버 측 기본 계약을 Redis 없이 검증하는 중간 단계다. 운영 배포 후보가 아니며 단일 인스턴스 개발·통합 테스트에서만 사용한다.

## 2. 활성화 설정

```yaml
app:
  auth:
    mode: session
  session:
    store: memory
```

환경변수 조합은 `AUTH_MODE=session`, `SESSION_STORE=memory`다. JWT가 기본값이며 세션 모드는 명시적으로 선택해야 한다.

세션 모드에서 다음 JWT 전용 구성은 등록하지 않는다.

- `JwtAuthFilter`
- Access·Refresh Token 발급
- `/api/auth/refresh`
- SSE ticket 발급 엔드포인트와 필터

## 3. 패키지 구조

```text
com.dbidding
├── account
│   └── authentication
│       └── session
│           ├── SessionAuthenticationStrategy
│           ├── SessionPrincipal
│           └── SessionProperties
└── global
    └── security
        └── session
            ├── SessionAuthFilter
            └── SessionAuthConfiguration
```

세션 패키지는 JWT 패키지를 import하지 않는다. 공통 `AuthenticationStrategy`, `AuthenticatedAccount`, `RequestUserIdWriter`만 사용한다.

## 4. 세션에 저장할 값

최소한의 단순 값만 저장한다.

```text
AUTHENTICATED_USER_ID: Integer
AUTHENTICATED_USER_ROLE: String
AUTHENTICATED_AT: epoch seconds
```

다음은 저장하지 않는다.

- Account Entity 또는 JPA proxy
- 비밀번호, Refresh Token, Access Token
- 지갑 잔액이나 hold 상태
- 주소와 알림 목록
- 요청마다 바뀔 수 있는 도메인 객체

역할이나 계정 상태가 변경될 때 활성 세션을 폐기하는 정책은 이슈 4에서 다중 인스턴스 기준으로 완성한다.

## 5. 로그인과 세션 고정 방어

공격자가 미리 발급받은 익명 Session ID를 인증 세션으로 승격하지 않는다.

```text
자격 증명 검증 성공
→ 기존 익명 세션 ID 변경 또는 기존 세션 폐기
→ 인증 세션 생성
→ userId·role·authenticatedAt 저장
→ 세션 쿠키 응답
```

`request.changeSessionId()`를 사용하거나 기존 세션을 invalidate한 뒤 새 세션을 만든다. 선택한 방식은 테스트로 로그인 전후 Session ID가 다른지 검증한다. 로그인 실패가 기존 인증 세션의 사용자를 바꾸면 안 된다.

## 6. 요청 인증

```text
SESSION cookie
→ Servlet container의 HttpSession 조회
→ SessionAuthFilter
→ userId attribute 검증
→ RequestUserIdWriter
→ request attribute userId
→ @CurrentUser
```

세션이 없거나 `userId` 타입이 올바르지 않으면 익명 요청으로 처리한다. 보호 API의 기존 인증 실패 응답 계약은 유지한다.

필터가 DB에서 Account를 매 요청 조회하지 않는다. 계정 상태의 즉시 반영이 필요한 고위험 작업은 활성 세션 폐기 또는 해당 유스케이스의 기존 검증으로 해결한다.

## 7. 로그아웃

이 단계의 로그아웃은 다음을 수행한다.

```text
현재 세션 식별
→ session.invalidate()
→ 세션 쿠키 만료
→ 204 No Content
```

세션이 이미 없더라도 로그아웃은 멱등하게 처리한다. 개인화 SSE 연결 종료는 이슈 3에서 추가한다.

## 8. 개발용 쿠키 기본값

| 속성 | 개발 기본값 | 운영 전 확정 위치 |
|---|---|---|
| 이름 | `SESSION` 또는 서비스 전용 이름 | 이슈 3 |
| `HttpOnly` | `true` | 이슈 3 |
| `Secure` | 로컬 HTTP에서는 `false` | 이슈 3 |
| `SameSite` | `Lax` | 이슈 3 |
| `Path` | `/` | 이슈 3 |
| `Domain` | host-only | 이슈 3 |
| idle timeout | 테스트 가능한 짧은 환경값 | 이슈 4 |

CSRF 방어가 완성되기 전에는 세션 모드를 운영 배포하거나 실제 사용자 트래픽에 노출하지 않는다.

## 9. 프론트 없이 검증하는 방법

MockMvc 또는 HTTP 클라이언트 cookie jar로 다음 흐름을 검증한다.

1. 로그인 응답의 세션 쿠키를 저장한다.
2. 같은 쿠키로 보호 API를 호출해 200을 확인한다.
3. 쿠키 없이 같은 API를 호출해 401을 확인한다.
4. 로그아웃 후 이전 쿠키로 다시 호출해 401을 확인한다.
5. 서버 재시작 뒤 이전 쿠키가 무효임을 확인한다.

## 10. 테스트

- 올바른 자격 증명으로 세션이 생성된다.
- 잘못된 자격 증명으로 세션이 생성되지 않는다.
- 로그인 전후 Session ID가 다르다.
- 세션에 허용된 attribute만 저장된다.
- 유효 세션으로 `@CurrentUser`가 정확한 사용자를 반환한다.
- 세션 없이 보호 API에 접근하면 401이다.
- 로그아웃이 세션과 cookie를 폐기한다.
- 로그아웃을 반복해도 실패하지 않는다.
- idle timeout 후 보호 API가 401이다.
- 서버 재시작 후 인메모리 세션이 사라진다.
- `AUTH_MODE=session`, `SESSION_STORE=memory`에서 세션 전략만 등록된다.
- 세션 모드에서 JWT·Refresh·ticket Bean이 등록되지 않는다.
- JWT 모드 회귀 테스트도 계속 통과한다.

## 11. 완료 기준

- 단일 인스턴스에서 로그인·보호 API·로그아웃이 동작한다.
- `@CurrentUser` 이후의 컨트롤러와 도메인 코드는 변경되지 않는다.
- JWT와 세션 패키지가 서로 import하지 않는다.
- 인메모리 세션의 재시작·다중 인스턴스 한계가 테스트와 문서에 드러난다.
- 세션 모드는 운영 배포 설정에서 활성화되지 않는다.

## 12. 롤백

배포 기본값은 계속 JWT다. 문제가 생기면 `AUTH_MODE=jwt`로 실행하고 세션 전용 구성과 패키지만 되돌린다. JWT 데이터나 Refresh 상태는 이 단계에서 변경하지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
