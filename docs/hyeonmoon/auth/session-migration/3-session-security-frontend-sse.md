# 세션 브라우저 인증과 개인화 SSE 분리

## 1. 분리 목적

기존 이슈 3은 쿠키·CSRF·프론트 인증 전환과 개인화 SSE를 한 범위에 포함했다. 두 영역은 모두 세션을 사용하지만, 전자는 모든 보호 REST API의 브라우저 보안 경계이고 후자는 Notification·Auction의 실제 emitter와 연결 수명까지 다룬다.

리뷰와 병합 단위를 작게 유지하기 위해 다음 두 이슈로 분리한다.

| 순서 | 문서 | 이슈 | 책임 |
|---:|---|---|---|
| 3a | [`3a-session-browser-auth.md`](3a-session-browser-auth.md) | [#235](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/235) | 쿠키·CSRF·프론트 AuthTransport |
| 3b | [`3b-session-personalized-sse.md`](3b-session-personalized-sse.md) | [#236](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/236) | 개인화 SSE·연결 종료 |

3b는 3a가 만든 세션 브라우저 인증 계약을 사용한다. 3a에는 개인화 SSE endpoint나 emitter를 넣지 않고, 3b에는 CSRF 정책을 다시 구현하지 않는다.

## 2. 공통 원칙

- `AUTH_MODE=jwt`가 기본값이며 JWT 흐름은 유지한다.
- 세션 모드는 `AUTH_MODE=session`, `SESSION_STORE=memory`를 명시한 단일 인스턴스 검증 환경에서만 사용한다.
- 프론트와 백엔드의 인증 모드는 같은 배포 단위에서 일치해야 한다.
- 공개 SSE는 인증 없이 유지하고 개인화 SSE만 활성 인증 방식으로 보호한다.
- Redis 세션과 다중 인스턴스 fan-out은 [`4-redis-session-multi-instance.md`](4-redis-session-multi-instance.md)에서 다룬다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
