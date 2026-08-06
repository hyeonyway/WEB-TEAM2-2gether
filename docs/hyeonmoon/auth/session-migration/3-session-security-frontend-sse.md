# 이슈 3: 세션 보안·프론트 인증·개인화 SSE

## 1. 이슈 경계

- 선행 문서: [`2-in-memory-session.md`](2-in-memory-session.md)
- 목표: 브라우저에서 안전하게 사용할 수 있는 세션 인증 흐름 완성
- 포함: 운영 쿠키, CSRF, 프론트 AuthTransport, 개인화 SSE, 로그아웃 연결 종료
- 비목표: Redis 저장소와 다중 인스턴스 fan-out
- 후속 문서: [`4-redis-session-multi-instance.md`](4-redis-session-multi-instance.md)

이 이슈가 끝나면 단일 인스턴스 환경에서 JWT와 세션을 프론트 설정으로 전환할 수 있어야 한다.

## 2. 운영 세션 쿠키

| 속성 | 기본 정책 | 이유 |
|---|---|---|
| 이름 | `SESSION` 또는 서비스 전용 이름 | 다른 애플리케이션 쿠키와 충돌 방지 |
| `HttpOnly` | `true` | JavaScript의 Session ID 직접 접근 방지 |
| `Secure` | 운영 `true` | HTTPS에서만 전송 |
| `SameSite` | 같은 site면 `Lax` 또는 `Strict` | CSRF 위험 축소 |
| `Path` | `/` | REST와 SSE가 같은 세션 사용 |
| `Domain` | host-only | 불필요한 서브도메인 공유 방지 |
| idle timeout | 환경변수 | 비활성 세션 자동 만료 |

프론트와 API가 cross-origin이면 모든 인증 fetch에 `credentials: 'include'`를 사용한다. 서버는 와일드카드가 아닌 정확한 Origin과 credentials만 허용한다. cross-site 쿠키가 꼭 필요할 때만 `SameSite=None; Secure`를 선택한다.

## 3. CSRF 기본안

세션 쿠키는 브라우저가 자동으로 전송되므로 상태 변경 API에는 명시적인 CSRF 방어가 필요하다. Spring Security를 사용하지 않는 현재 제약에서 **세션 귀속 CSRF Token과 요청 헤더 검증**을 기본안으로 채택한다.

1. 로그인 성공 시 예측하기 어려운 CSRF Token을 생성해 세션에 저장한다.
2. 로그인 응답 본문에 Token을 포함한다.
3. 새로고침 후에는 `GET /api/auth/csrf`로 현재 세션의 Token을 다시 조회한다.
4. 프론트는 Token을 메모리에 보관한다.
5. `POST`, `PUT`, `PATCH`, `DELETE` 요청의 `X-CSRF-Token` 헤더에 담는다.
6. 서버는 요청 헤더와 세션 값을 상수 시간 비교하고 누락되거나 다르면 403을 반환한다.

CSRF Token 조회 API는 인증된 세션에만 값을 반환하고 캐시하지 않는다. Token은 URL, 로그, cookie에 남기지 않으며 세션과 함께 폐기한다.

추가 방어선은 다음과 같다.

- `Origin`을 우선 검증하고 없을 때만 `Referer`를 검증한다.
- `Sec-Fetch-Site: cross-site`인 상태 변경 요청을 거부한다.
- 로그인처럼 세션 생성 전 호출되는 API에도 Origin·Fetch Metadata 검증을 적용한다.
- `SameSite`는 보조 수단으로 사용하고 유일한 방어선으로 간주하지 않는다.
- 임의 Origin과 credentials를 함께 허용하지 않는다.

## 4. 프론트 인증 구조

```text
src/auth
├── AuthProvider.tsx
├── RequireAuth.tsx
├── authTransport.ts
├── authTransportFactory.ts
├── jwt
│   ├── jwtAuthTransport.ts
│   ├── jwtAuthenticatedRequest.ts
│   ├── jwtSseTransport.ts
│   └── accessTokenStore.ts
└── session
    ├── sessionAuthTransport.ts
    ├── sessionAuthenticatedRequest.ts
    └── sessionSseTransport.ts
```

```typescript
export interface AuthTransport {
  login(input: LoginInput): Promise<void>;
  logout(): Promise<void>;
  restore(): Promise<'authenticated' | 'anonymous'>;
  request<T>(path: string, options?: RequestInit): Promise<T>;
  openEventSource(path: string): EventSource;
}
```

- JWT transport는 메모리 Access Token, Bearer 첨부, 단일 Refresh, ticket SSE를 소유한다.
- 세션 transport는 `credentials: 'include'`, CSRF header, 현재 사용자 조회, cookie SSE를 소유한다.
- 도메인 API는 factory가 선택한 transport만 호출한다.
- `VITE_AUTH_MODE`와 백엔드 `AUTH_MODE`는 같은 배포 단위에서 일치해야 한다.
- 세션 모드의 401은 Refresh를 시도하지 않고 익명 상태로 전환한다.

## 5. 공개 SSE와 개인화 SSE

### 공개 SSE

공개 경매 현재가·입찰 수·마감 상태는 로그인 여부와 무관하다.

```http
GET /api/auctions/stream
```

공개 스트림은 사용자 세션이나 SSE ticket을 요구하지 않는다.

### 개인화 SSE

개인 알림·참여 경매는 URL의 사용자 ID가 아니라 세션에서 사용자를 식별한다.

```http
GET /api/me/notifications/stream
GET /api/me/auctions/stream
```

동일 Origin이면 기본 EventSource가 세션 cookie를 전송한다. cross-origin이면 다음처럼 credentials를 명시한다.

```typescript
new EventSource(url, { withCredentials: true });
```

세션 모드에서는 `/api/sse/tickets`, `TicketProvider`, `SseTicketAuthFilter`가 등록되지 않는다. JWT 모드에서는 기존 ticket 흐름을 유지한다.

## 6. 로그아웃과 이미 열린 SSE

로그아웃은 다음 작업을 하나의 유스케이스로 수행한다.

```text
현재 세션 식별
→ session.invalidate()
→ 세션 쿠키 만료
→ 해당 sessionId의 개인화 SSE 연결 종료
→ 204 No Content
```

이미 열린 SSE는 인증 필터를 다시 통과하지 않는다. Redis 또는 메모리에서 세션을 삭제하는 것만으로 즉시 끊기지 않으므로 ConnectionManager가 `userId`, `sessionId`, emitter 관계를 관리한다. 단일 인스턴스에서는 로그아웃과 세션 만료 이벤트로 해당 연결을 닫는다.

## 7. 재연결과 알림 복구

- SSE `id`에는 영속 Notification ID 또는 단조 증가 event sequence를 사용한다.
- EventSource 자동 재연결 시 같은 세션 cookie를 재사용한다.
- 서버는 `Last-Event-ID` 이후 누락 데이터를 Notification DB 또는 내구성 있는 저장소에서 재조회한다.
- 프론트는 ID로 중복을 제거한다.
- Redis Pub/Sub은 인스턴스 간 빠른 fan-out에만 사용하고 재전송 원본으로 사용하지 않는다.
- 공개 경매 화면은 REST 최신 상태를 복구 원본으로 사용할 수 있다.

개인 알림이 반드시 재전송돼야 한다면 Outbox·재시도·DB replay가 별도로 필요하다. 인증 세션 저장소와 알림 전달 보장은 같은 Redis를 사용하더라도 서로 다른 책임이다.

## 8. 테스트

### 쿠키·CSRF

- 세션 cookie에 환경별 필수 속성이 적용된다.
- CSRF Token이 없거나 올바르지 않은 상태 변경 요청은 403이다.
- 다른 세션에서 발급된 CSRF Token은 사용할 수 없다.
- 로그인 전 교차 사이트 요청이 거부된다.
- 허용되지 않은 Origin의 입찰·지갑 요청이 거부된다.

### 프론트

- 새로고침 후 현재 사용자 API로 세션 로그인이 복구된다.
- 세션 모드 401에서 JWT Refresh를 호출하지 않는다.
- 상태 변경 요청에 CSRF header가 포함된다.
- 경매·지갑·대시보드 API 호출부가 모드를 직접 분기하지 않는다.
- JWT 모드의 Access Token과 Refresh 흐름이 유지된다.

### SSE

- 공개 SSE는 로그인 없이 연결된다.
- 개인화 SSE는 유효한 세션이 있어야 연결된다.
- URL의 임의 사용자 ID로 다른 사용자의 스트림을 구독할 수 없다.
- 로그아웃 후 기존 개인화 SSE가 종료되고 재연결이 401이다.
- `Last-Event-ID` 이후 알림이 재조회되고 중복은 제거된다.
- 세션 모드에서 ticket Bean과 엔드포인트가 존재하지 않는다.

## 9. 완료 기준

- 브라우저에서 로그인·새로고침 복구·보호 요청·로그아웃이 동작한다.
- 쿠키·CSRF·Origin 정책이 상태 변경 API에 적용된다.
- 공개 SSE와 개인화 SSE의 인증 경계가 분리된다.
- 세션 모드에서는 개인화 SSE ticket이 필요 없다.
- 로그아웃이 이미 열린 개인화 SSE까지 종료한다.
- JWT와 세션 프론트 transport가 도메인 API 밖에서 교체된다.

## 참고 자료

- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [OWASP Cross-Site Request Forgery Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [Redis Pub/Sub 전달 보장](https://redis.io/docs/latest/develop/pubsub/)

> 이 문서는 codex의 도움을 받아 작성하였습니다
