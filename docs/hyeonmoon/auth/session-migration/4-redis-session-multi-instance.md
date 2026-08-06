# 이슈 4: Redis 세션과 다중 인스턴스 운영

## 1. 이슈 경계

- 선행 문서: [`3a-session-browser-auth.md`](3a-session-browser-auth.md), [`3b-session-personalized-sse.md`](3b-session-personalized-sse.md)
- 목표: 동일한 HttpSession 인증 코드를 Spring Session Redis 저장소로 전환
- 포함: 저장소 구성, 다중 인스턴스, 강제 만료, 장애 정책, SSE 연결 전파
- 비목표: JWT와의 최종 성능 판정
- 후속 문서: [`5-benchmark-and-failure-test.md`](5-benchmark-and-failure-test.md)

인메모리와 Redis는 별도 인증 방식이 아니다. `AUTH_MODE=session`의 HTTP·보안 계약은 유지하고 `SESSION_STORE`만 바꾼다.

## 2. 활성화 설정

```yaml
app:
  auth:
    mode: session
  session:
    store: redis
```

환경변수 조합은 `AUTH_MODE=session`, `SESSION_STORE=redis`다. 알 수 없는 저장소 값과 Redis 필수 설정 누락은 애플리케이션 시작 단계에서 실패시킨다.

## 3. 저장소 추상화 원칙

Spring Session이 표준 HttpSession 저장소를 Redis로 교체한다.

```text
SessionAuthenticationStrategy
→ HttpSession API
→ Spring Session repository
→ Redis
```

인증 서비스와 필터가 `RedisTemplate`, Lettuce API, Redis 명령을 직접 호출하지 않는다. 저장소 선택은 configuration에만 존재한다.

## 4. Redis 구성

다음을 환경별로 명시한다.

- Redis host·port·인증·TLS
- 연결 timeout과 명령 timeout
- 세션 namespace
- idle timeout
- 기본 또는 indexed repository
- serializer와 호환 정책
- connection pool 크기
- cookie secure와 proxy header 처리

여러 기능 또는 애플리케이션이 같은 Redis를 사용하면 namespace를 분리한다. `allkeys-lru` 같은 eviction 정책으로 활성 세션이 예고 없이 제거되지 않도록 전용 인스턴스 또는 메모리 정책을 검토한다.

세션에는 Integer·String·epoch seconds·CSRF Token 같은 단순 값만 저장한다. JPA Entity나 클래스 버전에 민감한 객체 직렬화를 피한다.

## 5. 세션 만료와 사용자별 강제 종료

현재 세션만 종료하면 되는 로그아웃은 `session.invalidate()`로 충분하다. 다음 요구사항이 있으면 사용자 ID로 활성 세션을 찾을 수 있어야 한다.

- 계정 정지
- 탈퇴
- 비밀번호 변경 후 전체 로그아웃
- 탈취 신고
- 관리자 강제 로그아웃

이 경우 Spring Session indexed repository 또는 별도 사용자-세션 인덱스를 선택한다. 인덱스의 원본과 정리 정책을 중복 구현하지 않는다. 세션 TTL 만료와 인덱스 정리가 일치하는지 검증한다.

## 6. 다중 인스턴스 인증 시나리오

1. 인스턴스 A에서 로그인하고 B에서 보호 API를 호출한다.
2. A에서 로그아웃한 직후 B와 C의 요청이 401인지 확인한다.
3. A를 재시작해도 B에서 기존 세션을 사용할 수 있는지 확인한다.
4. idle TTL 갱신이 인스턴스마다 동일한지 확인한다.
5. 계정 정지 후 모든 활성 세션이 폐기되는지 확인한다.
6. 서로 다른 배포 버전이 같은 세션 attribute를 읽을 수 있는지 확인한다.

세션 검증 때문에 sticky session을 필수 조건으로 만들지 않는다. 모든 인스턴스가 같은 Redis 저장소를 사용한다.

## 7. 다중 인스턴스 SSE 연결 종료

세션은 Redis에서 공유되지만 실행 중인 `SseEmitter`는 각 애플리케이션 메모리에 있다. 따라서 세션 삭제만으로 다른 인스턴스의 열린 연결이 즉시 종료되지 않는다.

```text
로그아웃·강제 만료
→ Redis 세션 삭제
→ sessionId 또는 userId 기반 연결 종료 메시지 발행
→ 각 인스턴스의 ConnectionManager가 로컬 emitter 종료
```

연결 종료 메시지는 Redis Pub/Sub으로 전달할 수 있다. 메시지를 놓쳐도 다음 heartbeat 또는 주기적 세션 확인, 재연결 인증에서 무효 세션이 거부돼야 한다. Pub/Sub을 영속 알림 재전송 수단으로 사용하지 않는다.

인스턴스 하나가 종료돼 연결이 끊기면 EventSource는 다른 인스턴스로 재연결하고 같은 세션 cookie를 사용한다. 누락 알림은 `Last-Event-ID`와 Notification DB로 복구한다.

## 8. Redis 장애 정책

Redis 세션 장애는 인증 상태를 확인할 수 없는 상황이다. 기본 정책은 보호 API를 fail-closed로 실패시키는 것이다. 로컬 캐시로 임의 허용하지 않는다.

| 요청 | 기본 장애 정책 |
|---|---|
| 공개 카드·경매 조회 | 세션 없이 공개 응답 가능 |
| 선택적 인증 공개 조회 | 인증 정보를 사용하지 않는 공개 응답으로 제한 가능 |
| 대시보드·알림 조회 | 실패 |
| 입찰·지갑·주소 변경 | 반드시 실패 |
| 개인화 SSE 신규 연결 | 실패하고 재연결 대기 |
| 이미 열린 개인화 SSE | heartbeat에서 상태 확인 후 종료 |

인증 정보가 없을 때 401, 저장소 장애로 검증할 수 없을 때 503을 구분할지 API 오류 계약에서 확정한다. 사용자 A를 사용자 B로 오인하거나 인증되지 않은 요청을 성공시키는 fallback은 허용하지 않는다.

## 9. 타임아웃과 복구

- Redis 연결·명령 timeout은 HTTP 전체 timeout보다 짧게 둔다.
- 무제한 재시도로 servlet thread를 점유하지 않는다.
- circuit breaker를 사용하더라도 보호 API를 fail-open하지 않는다.
- Redis 재시작으로 세션이 유실되면 재로그인을 요구한다.
- 장애 복구 후 이전 cookie가 다른 사용자 세션으로 연결되지 않아야 한다.
- Redis HA와 persistence는 세션 유실 허용 범위에 맞춰 결정한다.

## 10. 관측 항목

- 세션 lookup 성공·실패·timeout 수
- Redis command latency p95·p99
- 활성 세션 key 수와 메모리
- 만료·로그아웃·강제 종료 세션 수
- 세션 serializer 오류
- 인증 실패 401과 저장소 장애 5xx
- 인스턴스별 개인화 SSE 연결 수
- 연결 종료 메시지 발행·수신·처리 지연

인증 로그에 Session ID, CSRF Token, cookie 원문을 남기지 않는다. 필요하면 해시 또는 내부 correlation ID를 사용한다.

## 11. 테스트

| 시나리오 | 기대 결과 |
|---|---|
| A 로그인 후 B 보호 API | 기존 세션으로 성공 |
| A 로그아웃 직후 B 요청 | 401 |
| 인스턴스 하나 종료 | 다른 인스턴스에서 세션 유지 |
| Redis 연결 지연 | 설정 timeout 안에 실패 |
| Redis 완전 중단 | 보호 API fail-closed |
| Redis 재시작·세션 유실 | 재로그인 요구, 사용자 오인 없음 |
| 개인화 SSE 인스턴스 종료 | 다른 인스턴스로 재연결 |
| 계정 정지 | 모든 인스턴스의 세션과 SSE 종료 |
| 서로 다른 배포 버전 | 지원 범위 내 attribute 역직렬화 성공 |

장애 테스트는 단순 성공률뿐 아니라 보안상 잘못 허용된 요청이 한 건도 없는지 확인한다.

## 12. 완료 기준

- 인증 서비스 코드가 Redis API를 직접 호출하지 않는다.
- 인스턴스 A에서 생성한 세션을 B가 사용한다.
- 로그아웃·계정 정지가 모든 인스턴스에 반영된다.
- Redis TTL과 cookie·idle timeout 정책이 모순되지 않는다.
- Redis 장애 중 입찰·지갑 요청이 fail-open하지 않는다.
- 로그아웃과 강제 만료가 다른 인스턴스의 개인화 SSE까지 종료한다.
- 장애·복구 지표와 운영 대응 절차가 준비된다.

## 참고 자료

- [Spring Session - Redis로 저장소 구성](https://docs.spring.io/spring-session/reference/4.0/configuration/redis.html)
- [Redis Pub/Sub 전달 보장](https://redis.io/docs/latest/develop/pubsub/)

> 이 문서는 codex의 도움을 받아 작성하였습니다
