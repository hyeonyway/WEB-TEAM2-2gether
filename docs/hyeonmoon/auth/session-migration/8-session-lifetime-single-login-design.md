# 이슈 8: 절대 세션 수명과 단일 로그인 제한

## 1. 목적과 범위

- 대응 이슈: [#472](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/472)
- 선행 결정: [`7-final-decision-and-implementation-plan.md`](7-final-decision-and-implementation-plan.md)의 Redis Indexed Session 최종 채택
- 목적: 활동 중인 세션도 12시간 뒤에는 반드시 만료시키고, 한 계정의 활성 세션을 한 개로 제한한다.

이번 범위는 세션 인증 경계와 개인화 SSE 종료에 한정한다. 기기 목록, 다중 기기 허용 수, 기기별 세션 관리 UI는 포함하지 않는다.

## 2. 현재 상태와 문제

현재 Redis Session은 idle timeout만 적용한다. 요청이 계속 오면 timeout이 갱신되므로, 탈취된 세션 쿠키가 활동을 유지하는 한 유효 기간이 무한히 연장될 수 있다.

`SessionPrincipal`은 로그인 시각(`authenticatedAt`)을 이미 저장하고, 표준 principal index 속성에 `userId`도 기록한다. 그러나 전자는 만료 판단에 쓰이지 않고, 후자는 신규 로그인 시 기존 세션 정리에 쓰이지 않는다.

## 3. 결정

### 3.1 절대 수명

- 기본 절대 수명은 12시간으로 둔다.
- `app.session.absolute-timeout`을 `Duration` 환경 설정으로 제공하고 기본값은 `12h`로 둔다.
- idle timeout과 독립적으로 계산한다. 요청이 있어도 `authenticatedAt + absoluteTimeout` 이후에는 연장하지 않는다.
- 경과한 세션은 인증 필터에서 즉시 SSE 종료를 전파하고 `invalidate()`한 뒤 401을 반환한다.
- 응답 코드는 기존 보호 API와 같은 `UNAUTHORIZED`를 유지한다. 프론트에 별도 만료 사유 계약을 추가하지 않고 재로그인 흐름을 재사용한다.

### 3.2 단일 활성 세션

- 로그인 성공 뒤 새 세션의 principal index를 기록하기 전에 `findByPrincipalName(userId.toString())`으로 같은 계정의 기존 Redis 세션을 조회한다.
- 기존 세션마다 Redis 저장소에서 삭제하고 `SessionSseTerminationPublisher`로 session ID를 발행한다.
- 이후 현재 요청의 세션 ID를 교체하고, 새 `SessionPrincipal` 및 CSRF 토큰을 기록한다.
- 종료 메시지는 모든 인스턴스의 `SessionSseConnectionRegistry`로 전달된다. 알림·지갑 SSE는 해당 registry에 등록되어 있으므로 기존 브라우저 연결도 닫힌다.

## 4. 처리 흐름

### 4.1 절대 수명 초과 요청

```text
보호 API 요청
  -> SessionRepositoryFilter가 Redis Session 복원
  -> SessionAuthFilter가 SessionPrincipal.authenticatedAt 읽기
  -> now >= authenticatedAt + 12h
  -> SessionSseTerminationPublisher(sessionId)
  -> session.invalidate()
  -> 401 UNAUTHORIZED
```

만료되지 않은 세션만 request attribute의 `userId`를 기록하고 다음 필터·컨트롤러로 전달한다. principal 속성이 없거나 형식이 잘못된 세션은 기존과 동일하게 익명 요청으로 통과시킨다.

### 4.2 두 번째 로그인

```text
기기 B 로그인 성공
  -> principal index로 기기 A 세션 조회
  -> A 세션 ID마다 SSE 종료 Pub/Sub 발행
  -> Redis Session 삭제
  -> B 세션 ID 교체 및 principal/CSRF 저장
  -> B 로그인 성공 응답

기기 A 다음 보호 요청 -> 세션 없음 -> 401
기기 A 개인화 SSE -> 종료 메시지 수신 후 연결 종료
```

삭제와 SSE 종료 신호 사이의 짧은 순서는 보안 경계를 Redis 세션 삭제에 둔다. Pub/Sub은 이미 열린 로컬 emitter를 빨리 닫는 보조 수단이다. 메시지 유실이 있어도 다음 인증 요청과 EventSource 재연결은 삭제된 세션을 복원하지 못한다.

## 5. 코드 변경 경계

| 위치 | 변경 |
| --- | --- |
| `SessionProperties` | `absoluteTimeout` 설정과 양수 검증 추가 |
| `application.yml` | `SESSION_ABSOLUTE_TIMEOUT` 기본값 `12h` 추가 |
| `SessionAuthFilter` | Clock·세션 설정·SSE 종료 publisher를 주입하고 절대 수명 검사 추가 |
| `SessionAuthenticationStrategy` | `FindByIndexNameSessionRepository`로 기존 세션 삭제 및 종료 전파 |
| `SessionAuthConfiguration` | 추가 의존성 wiring |
| session/auth 테스트 | 절대 수명, 활동 중 만료, 단일 로그인, 이전 SSE 종료를 검증 |

컨트롤러의 `@CurrentUser` 계약, CSRF 검증 규칙, 공개 경매 SSE는 변경하지 않는다.

## 6. 예외와 동시성

- 같은 계정의 로그인 요청이 동시에 들어오면 Redis principal index 조회와 삭제가 완전한 compare-and-set은 아니다. 최종적으로 마지막 로그인 세션 하나만 남도록 하기 위해 사용자별 로그인 직렬화가 필요하면 별도 Redis lock을 추가해야 한다.
- 이번 구현에서는 기존 세션 삭제와 새 principal 기록을 순서대로 수행하고, 동시 로그인에 대한 정확한 단일 세션 보장은 Redis repository 계약만으로 단정하지 않는다. 동시 로그인 경쟁 조건은 통합 테스트와 운영 관찰 후 필요 시 후속 이슈로 보강한다.
- Redis 장애에서는 세션 조회·삭제가 실패하므로 인증은 fail-closed로 처리한다.

## 7. 회귀 테스트

- 로그인 시 `authenticatedAt`과 principal index가 기록된다.
- 현재 시각이 절대 수명 이전이면 인증 요청이 통과한다.
- 활동 중이어도 절대 수명 이후면 session invalidation, SSE 종료 전파, 401이 발생한다.
- 다른 기기의 기존 세션이 있으면 새 로그인 시 repository에서 삭제되고 해당 session ID의 SSE 종료가 전파된다.
- 삭제된 기존 세션으로 보호 API를 호출하면 401이며, 새 세션은 정상 인증된다.
- 기존 idle timeout·CSRF·로그아웃 동작은 유지된다.

## 8. 완료 기준

- 절대 수명은 요청 활동으로 연장되지 않는다.
- 새 로그인 뒤 같은 계정의 기존 Redis 세션은 남지 않는다.
- 기존 세션의 알림·지갑 SSE는 다중 인스턴스에서도 종료 신호를 받는다.
- 백엔드 전체 테스트가 통과한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
