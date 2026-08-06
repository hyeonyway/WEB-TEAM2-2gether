# 이슈 3a: 세션 브라우저 인증과 CSRF

## 1. 이슈 경계

- 구현 이슈: [#235 세션 브라우저 인증과 CSRF 적용](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/235)
- 선행 문서: [`2-in-memory-session.md`](2-in-memory-session.md)
- 상위 문서: [`3-session-security-frontend-sse.md`](3-session-security-frontend-sse.md)
- 목표: 브라우저가 세션으로 로그인·복구·보호 요청·로그아웃을 안전하게 수행
- 포함: 운영 쿠키, CSRF, Origin·Fetch Metadata, 프론트 AuthTransport
- 비목표: 개인화 SSE, Redis 세션, 다중 인스턴스

세션 모드는 JWT 운영 경로를 대체하지 않는 개발 검증용 선택지다. `AUTH_MODE=session`과 함께 `SESSION_AUTH_ENABLED=true`를 명시해야만 시작되며, 이 opt-in이 없으면 애플리케이션은 시작을 거부한다.

## 2. 쿠키와 CORS

세션 cookie는 `HttpOnly=true`, `Secure=true`, `Path=/`, host-only를 기본으로 한다. 로컬 HTTP 검증에서만 `SESSION_SECURE_COOKIE=false`를 명시한다. `SameSite`와 idle timeout은 환경변수로 정하고, cross-origin 요청이면 정확한 Origin과 credentials만 허용한다. `SameSite=None`은 반드시 `Secure=true`와 함께 설정한다.

프론트의 세션 fetch는 `credentials: 'include'`를 사용한다. cross-site cookie가 꼭 필요할 때만 `SameSite=None; Secure`를 허용한다.

## 3. CSRF 계약

Spring Security 없이 세션 귀속 CSRF Token을 사용한다.

```text
로그인 성공
→ 난수 CSRF Token 생성
→ HttpSession에 저장
→ 로그인 응답 본문에 token 전달

새로고침
→ GET /api/auth/csrf
→ 현재 세션의 token 반환

상태 변경 요청
→ X-CSRF-Token header
→ 세션 token과 상수 시간 비교
→ 불일치·누락 시 403
```

- Token은 URL, cookie, 로그에 넣지 않는다.
- Token 조회 API는 인증 세션에서만 동작하며 캐시하지 않는다.
- `POST`, `PUT`, `PATCH`, `DELETE`에 검증을 적용한다.
- 로그인처럼 세션 수립 전 요청도 Origin과 `Sec-Fetch-Site`를 검증한다.
- Origin이 없을 때만 Referer를 보조 검증한다.

## 4. 프론트 인증 경계

```text
src/auth
├── AuthProvider.tsx
├── RequireAuth.tsx
├── authTransport.ts
├── authTransportFactory.ts
├── jwt/
└── session/
    ├── sessionAuthTransport.ts
    └── sessionAuthenticatedRequest.ts
```

```typescript
export interface AuthTransport {
  request<T>(path: string, options?: RequestInit): Promise<T>;
  optionallyAuthenticatedRequest<T>(path: string, options?: RequestInit): Promise<T>;
}
```

- JWT transport는 Access Token, Bearer header, 단일 Refresh를 소유한다.
- session transport는 cookie, CSRF header를 소유한다.
- 로그인·로그아웃·인증 복구는 `authApi`와 `AuthProvider` 안에만 남기고, 도메인 API는 모드를 직접 분기하지 않고 factory가 선택한 transport만 사용한다.
- 세션 모드에서 401은 Refresh 없이 익명 상태로 전환한다.

## 5. 테스트와 완료 기준

- 세션 cookie에 환경별 필수 속성이 적용된다.
- CSRF Token이 없거나 다른 세션의 Token이면 상태 변경 요청은 403이다.
- 허용되지 않은 Origin 또는 cross-site Fetch Metadata의 상태 변경 요청은 거부된다.
- 브라우저에서 로그인·새로고침 복구·보호 요청·로그아웃이 동작한다.
- 세션 모드 401에서 JWT Refresh를 호출하지 않는다.
- JWT Access Token과 Refresh 흐름 회귀 테스트가 유지된다.

## 6. 구현 결과

- `SESSION_COOKIE_SAME_SITE`로 SameSite 정책을 설정하고, 로그아웃의 만료 cookie에도 같은 값을 적용한다.
- `SessionCsrfFilter`가 unsafe 요청에서 CSRF Token뿐 아니라 `Origin`·`Referer`·`Sec-Fetch-Site`를 함께 검사한다. Origin/Referer가 없는 비브라우저 요청은 CSRF Token 검증을 계속 적용한다.
- 프론트 `authenticatedRequest`는 `getAuthTransport()`만 호출한다. 세션 transport의 401은 Refresh 없이 호출자에게 전달한다.
- `AuthProvider`는 세션 모드에서 `/api/auth/me`와 `/api/auth/csrf`로 새로고침 복구를 수행한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
