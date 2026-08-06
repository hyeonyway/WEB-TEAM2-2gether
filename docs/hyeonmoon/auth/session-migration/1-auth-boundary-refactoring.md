# 이슈 1: 인증 공통 경계 추출과 JWT 격리

## 1. 대응 이슈

- GitHub Issue: [#160 JWT·세션 전환을 위한 인증 경계 분리](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/160)
- 작업 성격: 동작 변경 없는 구조 리팩터링
- 선행 이슈: 없음
- 후속 이슈: [`2-in-memory-session.md`](2-in-memory-session.md)

## 2. 목표

현재 JWT에 직접 결합된 인증 흐름을 공통 인증 경계 뒤로 분리한다. 기본값 `AUTH_MODE=jwt`에서 기존 동작을 유지하면서 이후 세션 구현을 추가할 수 있는 확장 지점을 만든다.

이 이슈에서는 세션 인증을 구현하지 않는다. 성공 기준은 JWT와 세션을 동시에 지원하는 것이 아니라, 현재 JWT가 공통 계약의 첫 번째 구현체로 동작하는 것이다.

## 3. 핵심 규칙

1. 인증 모드 분기는 구성 계층 한 곳에만 둔다.
2. 공통 패키지는 JWT 구체 구현을 참조하지 않는다.
3. `@CurrentUser Integer userId`를 공통 출력 계약으로 유지한다.
4. 경매·지갑·대시보드 코드는 인증 구현을 알지 않는다.
5. 패키지 이동과 동작 변경을 같은 커밋에 섞지 않는다.
6. 잘못된 설정은 애플리케이션 시작 단계에서 실패시킨다.

## 4. 설정 모델

인증 방식과 세션 저장소를 독립된 축으로 설정한다.

```yaml
app:
  auth:
    mode: ${AUTH_MODE:jwt}
  session:
    store: ${SESSION_STORE:memory}
```

| `AUTH_MODE` | `SESSION_STORE` | 이 이슈에서의 상태 |
|---|---|---|
| `jwt` | 무시 | 지원 |
| `session` | `memory` | 다음 이슈에서 지원 |
| `session` | `redis` | 이슈 4에서 지원 |

Spring Profile은 환경별 설정 묶음에 사용할 수 있지만 인증 구현 선택의 단일 기준은 `AUTH_MODE`로 둔다.

## 5. 목표 패키지 구조

```text
com.dbidding
├── account
│   ├── controller
│   │   └── AuthController
│   ├── service
│   │   ├── CredentialAuthenticationService
│   │   └── SignupService
│   └── authentication
│       ├── AuthenticationStrategy
│       ├── AuthenticatedAccount
│       ├── AuthenticationResult
│       └── jwt
│           ├── JwtAuthenticationStrategy
│           ├── JwtRefreshService
│           ├── JwtTokenProvider
│           ├── JwtProperties
│           ├── RefreshCookieFactory
│           ├── RefreshTokenHasher
│           ├── Authentication
│           └── AuthenticationRepository
└── global
    └── security
        ├── CurrentUser
        ├── CurrentUserArgumentResolver
        ├── RequestCurrentUserProvider
        ├── RequestUserIdWriter
        └── jwt
            ├── JwtAuthFilter
            ├── JwtAuthConfiguration
            ├── SseTicketAuthFilter
            └── TicketProvider
```

실제 현재 패키지와 차이가 있으면 이름을 그대로 복사하지 말고 역할 경계부터 맞춘다. 다른 도메인의 Repository나 Entity를 인증 편의를 위해 직접 참조하지 않는다.

## 6. 공통 자격 증명 검증

이메일·비밀번호·계정 상태 검증을 토큰 발급과 분리한다.

```java
public interface CredentialAuthenticationService {
    AuthenticatedAccount authenticate(LoginRequest request);
}

public record AuthenticatedAccount(
        Integer userId,
        AccountRole role
) {}
```

공통 서비스가 담당한다.

- 이메일로 Account 조회
- 존재하지 않는 이메일에도 더미 PBKDF2 검증 수행
- 비밀번호 constant-time 비교
- `ACTIVE` 상태 확인
- 외부 응답에서는 실패 사유를 단일 인증 실패로 변환

JWT 전략은 검증이 끝난 `AuthenticatedAccount`만 받아 Access·Refresh 상태를 생성한다.

## 7. 인증 전략 계약

HTTP 쿠키를 다루므로 전략 경계는 인증 transport 계층에 둔다.

```java
public interface AuthenticationStrategy {
    AuthenticationResult establish(
            AuthenticatedAccount account,
            HttpServletRequest request,
            HttpServletResponse response
    );

    void terminate(
            HttpServletRequest request,
            HttpServletResponse response
    );
}
```

이 이슈의 JWT 전략은 다음을 위임받는다.

- Access·Refresh Token 발급
- Refresh Token 해시 저장과 Rotation
- Refresh cookie 설정과 폐기
- JWT 로그아웃 처리

로그인 응답의 모드별 차이를 공통 DTO의 nullable 필드로 무분별하게 확장하지 않는다. Controller 응답 어댑터가 `AuthenticationResult`를 현재 JWT 응답으로 변환한다.

## 8. 조건부 구성

구체 구현 선택은 `@ConditionalOnProperty` 기반 구성에서만 수행한다.

```java
@Configuration(proxyBeanMethods = false)
class AuthenticationModeConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "app.auth.mode",
            havingValue = "jwt",
            matchIfMissing = true
    )
    AuthenticationStrategy jwtAuthenticationStrategy(...) {
        // 기존 JWT 구현 조립
    }
}
```

서비스와 컨트롤러에 `if (authMode == ...)` 분기를 두지 않는다. JWT 전용 `/refresh`와 SSE ticket 엔드포인트는 JWT 모드 구성에 묶는다.

## 9. Current User 공통 경계

인증 입력이 달라도 공통 출력은 request attribute의 `userId`다.

```text
Authorization: Bearer <access-token>
→ JwtAuthFilter
→ RequestUserIdWriter
→ request attribute userId
→ RequestCurrentUserProvider
→ @CurrentUser
```

`RequestUserIdWriter`가 attribute key와 중복 기록 정책을 관리한다. 컨트롤러와 도메인 서비스는 JWT 클래스를 참조하지 않는다.

## 10. 권장 커밋 순서

1. 전체 전환 설계 문서 추가
2. JWT 현재 동작의 회귀·계약 테스트 보강
3. 공통 자격 증명 검증과 결과 모델 추출
4. Current User request 경계 추출
5. JWT 구현 패키지 이동
6. `AUTH_MODE=jwt` 조건부 구성 적용
7. 금지 import와 설정 실패 테스트 추가

한 커밋에서 패키지 이동과 기능 수정을 동시에 하지 않는다. 이동 커밋은 내용 변경을 최소화해 diff 추적성을 유지한다.

## 11. 테스트

### 공통 계약

- 올바른 로그인은 보호 API 접근을 허용한다.
- 잘못된 자격 증명은 기존과 같은 오류를 반환한다.
- 로그아웃 후 Refresh가 거부된다.
- 비활성 계정은 로그인할 수 없다.
- `@CurrentUser`가 정확한 사용자를 반환한다.
- 사용자 A의 인증 상태가 사용자 B로 섞이지 않는다.

### JWT 회귀

- 서명·만료·token type을 검증한다.
- Refresh Rotation과 재사용 거부가 유지된다.
- Access Token 메모리 복구와 동시 401 단일 Refresh가 유지된다.
- 개인화 SSE ticket의 TTL과 1회성이 유지된다.

### 구성

- 기본 설정에서 JWT 전략 하나만 존재한다.
- JWT 전용 필터·Refresh·ticket 엔드포인트가 등록된다.
- 알 수 없는 `AUTH_MODE`는 시작에 실패한다.
- `auction`, `wallet`, `dashboard`에서 JWT 구체 패키지 import가 0건이다.

## 12. 완료 기준

- 기본값 `AUTH_MODE=jwt`에서 외부 API 동작이 변경되지 않는다.
- 공통 요청 처리 코드가 JWT 구현 클래스를 직접 참조하지 않는다.
- 세션 구현을 추가할 패키지와 구성 확장 지점이 존재한다.
- 기존 JWT 인증 회귀 테스트와 구성 테스트가 통과한다.
- 리팩터링 전후 JWT 기준 성능이 허용 오차 안에 있다.
- 이 문서와 이슈 #160의 작업 항목이 일치한다.

## 13. 롤백

이 이슈는 동작 변경 없이 구조만 분리한다. 문제가 생기면 패키지 이동과 조건부 구성만 되돌리고 기존 JWT 계약 테스트로 복구를 확인한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
