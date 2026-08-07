# 이슈 3b: 세션 기반 개인화 SSE

## 1. 이슈 경계

- 구현 이슈: [#236 세션 기반 개인화 SSE 전환](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/236)
- 선행 문서: [`3a-session-browser-auth.md`](3a-session-browser-auth.md)
- 상위 문서: [`3-session-security-frontend-sse.md`](3-session-security-frontend-sse.md)
- 목표: 세션 cookie로 개인화 SSE를 인증하고 로그아웃 때 열린 연결을 종료
- 포함: `/api/me` endpoint 계약, ConnectionManager, 프론트 session SSE transport
- 비목표: Redis fan-out, 도메인별 emitter payload 구현, 전달 보장 재설계

## 2. 공개·개인화 SSE 경계

공개 경매 스트림은 로그인이 필요 없다.

```http
GET /api/auctions/stream
```

개인화 스트림은 URL userId가 아니라 세션 사용자로 식별한다.

```http
GET /api/me/notifications/stream
GET /api/me/auctions/stream
```

세션 모드의 EventSource는 같은 Origin에서 session cookie를 자동 전송한다. cross-origin 배포에서만 `new EventSource(url, { withCredentials: true })`를 사용한다. 세션 모드에서는 ticket endpoint·`TicketProvider`·`SseTicketAuthFilter`를 등록하지 않는다. JWT 모드는 기존 ticket 흐름을 유지한다.

## 3. 연결 수명

이미 열린 SSE는 인증 필터를 다시 통과하지 않으므로 세션 삭제만으로 즉시 종료되지 않는다.

```text
세션 인증 SSE 연결
→ ConnectionManager에 userId·sessionId·emitter 등록
→ 로그아웃 또는 세션 만료
→ 해당 sessionId 연결 종료·제거
```

단일 인스턴스에서는 메모리 ConnectionManager를 사용한다. Redis 세션과 다중 인스턴스 연결 종료 전파는 이 문서 범위가 아니라 다음 Redis 전환 단계에서 다룬다.

## 4. 재연결과 도메인 연동

- `Last-Event-ID` 이후 재전송 원본은 Notification DB 또는 내구성 있는 저장소다.
- 프론트는 event ID로 중복을 제거한다.
- 공개 경매 화면은 REST 최신 상태를 복구 원본으로 사용할 수 있다.
- Notification·Auction 담당자는 실제 emitter와 payload를 소유한다. 이 이슈는 인증, URL 계약, 연결 등록·종료의 공통 기반만 제공한다.

## 5. 테스트와 완료 기준

- 공개 SSE는 로그인 없이 연결된다.
- 개인화 SSE는 유효 세션에서만 연결된다.
- URL 변조로 다른 사용자의 스트림을 구독할 수 없다.
- 로그아웃 뒤 열린 개인화 SSE가 종료되고 재연결은 401이다.
- 세션 모드에서 ticket Bean과 endpoint가 존재하지 않는다.
- JWT 모드의 ticket SSE 회귀 테스트가 유지된다.

## 6. 구현 결과

- `SessionSseConnectionRegistry`가 sessionId별 emitter를 관리하고, 로그아웃과 `HttpSessionListener`의 세션 종료 시 해당 연결을 완료한다.
- Notification은 세션 모드에서 `/api/me/notifications/stream`으로 연결한다. URL의 userId를 받지 않으므로 다른 사용자의 스트림을 지정할 수 없다.
- JWT 모드의 `/api/users/{userId}/notifications/stream?ticket=...`와 ticket 발급 API는 조건부로 그대로 유지한다.
- 프론트 `useNotificationStream`은 세션 모드에서 ticket 발급 없이 `withCredentials` EventSource를 사용하며, JWT 모드에서는 기존 ticket 재연결 흐름을 유지한다.
- Auction의 사용자별 SSE emitter와 `/api/me/auctions/stream` payload는 Auction 담당 도메인 소유라 이 공통 기반만 사용해 후속 구현한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
