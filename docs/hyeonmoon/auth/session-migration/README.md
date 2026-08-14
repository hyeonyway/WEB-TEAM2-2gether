# JWT·세션 비교 및 Redis 세션 전환 설계

## 1. 문서의 목적

이 디렉터리는 현재 JWT 인증을 즉시 폐기하기 위한 지시서가 아니다. 동일한 인증 계약 아래 JWT, 서버 인메모리 세션, Redis 세션을 단계적으로 구현하고 기능·보안·성능을 같은 조건에서 비교한 뒤 최종 인증 방식을 선택하기 위한 설계 묶음이다.

README는 기술 선택의 기승전결과 전체 로드맵을 설명한다. 번호가 붙은 여섯 문서는 구현 이슈 여섯 개와 1:1로 대응한다. 개발할 때는 이 README와 현재 이슈에 대응하는 문서 하나만 읽는 것을 기본으로 한다.

기존 [`../3-login-and-token.md`](../3-login-and-token.md), [`../4-refresh-and-logout.md`](../4-refresh-and-logout.md), [`../5-current-user-and-sse-auth.md`](../5-current-user-and-sse-auth.md)는 현재 JWT 구현에 도달한 과정과 당시 판단을 보존하는 역사적 문서다. 이 전환 설계는 기존 문서를 수정하거나 대체하지 않는다.

## 2. 기: 최초 JWT 선택

프로젝트 초기에는 다음 요구사항을 중심으로 JWT를 선택했다.

- 여러 애플리케이션 인스턴스가 같은 요청을 처리할 수 있어야 한다.
- 입찰 마감 직전 트래픽이 집중될 때 인증 저장소 왕복을 핵심 요청 경로에 추가하고 싶지 않았다.
- Access Token을 각 인스턴스에서 서명과 만료 시간만으로 검증하면 세션 저장소 없이 수평 확장할 수 있다.
- 프론트는 Access Token을 메모리에 보관하고 Refresh Token만 `HttpOnly` 쿠키에 두어 노출 범위를 제한할 수 있다.

이 판단은 Access Token 검증 경로만 보면 타당하다. 정상 요청은 DB나 Redis를 조회하지 않고 로컬 암호 연산만 수행한다.

## 3. 승: 구현하면서 발견한 상태와 복잡성

현재 인증은 다음과 같이 동작한다.

- Access Token은 JSON 응답으로 전달하고 프론트 메모리에만 저장한다.
- Refresh Token은 `HttpOnly`, `SameSite=Strict` 쿠키로 전달한다.
- Refresh Token 원문은 저장하지 않고 SHA-256 해시만 `authentication` 테이블에 저장한다.
- Refresh Rotation은 사용자별 Authentication 행을 비관적 락으로 직렬화한다.
- `JwtAuthFilter`가 Access Token을 검증해 request attribute에 `userId`를 기록한다.
- 컨트롤러는 공통 `@CurrentUser`로 사용자 ID를 받는다.
- 개인화 SSE는 30초·1회용 ticket으로 기본 EventSource의 Authorization 헤더 제약을 우회한다.

이 구현은 동작하지만 인증 생명주기 전체는 완전히 stateless하지 않다.

### 3.1 Refresh Rotation

Refresh Token 재사용을 막기 위해 사용자별 현재 Refresh Token 해시를 DB에 저장하고 비관적 락으로 회전을 직렬화한다. 재발급은 서버 상태와 DB 가용성에 의존한다.

### 3.2 로그아웃과 즉시 무효화

로그아웃은 Refresh 상태를 폐기하지만 이미 발급된 Access Token은 만료 전까지 유효하다. 계정 정지나 탈취 신고를 즉시 반영하려면 Access Token `jti` 또는 사용자 ID 기반 denylist와 모든 인스턴스의 일관된 조회 정책이 추가로 필요하다.

### 3.3 개인화 SSE ticket

브라우저 기본 `EventSource`는 임의의 Authorization 헤더를 지정하지 못한다. 공개 경매 SSE는 인증이 필요 없지만 개인 알림·참여 경매 SSE에는 사용자 인증이 필요하므로 별도의 일회용 ticket 생명주기가 생겼다.

### 3.4 프론트 인증 복구

프론트는 앱 시작 시 Refresh 요청, Access Token 메모리 저장, 동시 401 요청의 단일 Refresh, 성공 후 원요청 재시도를 관리한다. 세션 쿠키 기반 인증보다 관리 지점이 많다.

## 4. 전: 현재 프로젝트 조건으로 재비교

현재 서비스는 다음 조건을 가진다.

- 프론트와 백엔드가 같은 서비스 경계에 있다.
- 인증·경매·지갑은 하나의 모듈러 모놀리스에서 동작한다.
- 독립된 여러 리소스 서버가 같은 토큰을 검증해야 하는 요구사항은 아직 없다.
- 다중 인스턴스의 경매 이벤트와 개인 알림 전달을 위해 Redis를 사용할 가능성이 높다.
- 로그아웃·계정 정지를 신속하게 반영하는 것이 금전 기능에서 중요하다.
- 개인화 SSE에도 기본 EventSource를 사용하고 싶다.

이 조건에서는 Redis 세션도 다중 인스턴스를 지원한다. 세션 쿠키는 EventSource가 자동으로 전송하므로 ticket 계층을 제거할 수 있고, 서버가 세션을 삭제하면 다음 요청부터 인증을 거부할 수 있다.

반대로 Redis 세션은 인증 요청마다 저장소를 조회하며 Redis 장애가 전체 인증 요청에 직접 전파된다. 이 비용이 입찰 부하 구간에서 실제 병목인지 측정하지 않은 상태에서 세션을 느리다고 단정할 수 없다.

## 5. 결: 교체 가능한 비교 실험

JWT 또는 세션을 미리 승자로 정하지 않고 다음 순서로 검증한다.

1. 공통 인증 경계를 추출하고 기존 JWT를 격리한다.
2. 서버 인메모리 세션으로 세션 인증의 기본 계약을 구현한다.
3. 쿠키·CSRF·프론트 인증 복구·개인화 SSE를 세션 방식으로 검증한다.
4. 세션 저장소만 Redis로 교체해 다중 인스턴스와 장애 동작을 검증한다.
5. 동일한 기능·보안 조건으로 성능과 장애 영향을 비교한다.
6. 결과에 따라 하나를 선택하고 다른 구현을 제거한다.

## 6. 이슈와 문서의 1:1 대응

| 순서 | 문서 | 대응 이슈 | 핵심 결과 |
|---:|---|---|---|
| 1 | [`1-auth-boundary-refactoring.md`](1-auth-boundary-refactoring.md) | [#160](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/160) | 공통 인증 경계와 격리된 JWT 구현 |
| 2 | [`2-in-memory-session.md`](2-in-memory-session.md) | [#216](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/216) | 단일 인스턴스 세션 인증 |
| 3a | [`3a-session-browser-auth.md`](3a-session-browser-auth.md) | [#235](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/235) | 브라우저 세션 인증과 CSRF |
| 3b | [`3b-session-personalized-sse.md`](3b-session-personalized-sse.md) | [#236](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/236) | 세션 기반 개인화 SSE |
| 4 | [`4-redis-session-multi-instance.md`](4-redis-session-multi-instance.md) | (미착수, `#469`로 구현) | Redis 세션과 다중 인스턴스 운영 — 정식 이슈 없이 `#469`에 포함되어 진행됨 |
| 5 | [`5-benchmark-and-failure-test.md`](5-benchmark-and-failure-test.md) | (미착수) | 비교 수치와 장애 실험 결과 — **수행하지 않음**, `7-final-decision-and-implementation-plan.md` 2절에 사유 기록 |
| 6 | [`6-final-selection-and-cleanup.md`](6-final-selection-and-cleanup.md) / [`7-final-decision-and-implementation-plan.md`](7-final-decision-and-implementation-plan.md) | [#469](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/469) | 최종 선택(세션)과 JWT 제거 — 성능 측정 없이 기능·보안 요구사항 근거로 결정, 7번 문서에 상세 |

후속 이슈가 생성되면 이 표의 임시 표기만 실제 이슈 링크로 바꾼다. 각 구현 문서의 범위와 이슈의 작업 항목·완료 기준은 동일하게 유지한다.

## 7. 공통 설계 원칙

- `@CurrentUser Integer userId`는 인증 방식과 무관한 컨트롤러 계약으로 유지한다.
- 인증 모드 분기는 구성 계층 한 곳에서만 수행한다.
- JWT 패키지와 세션 패키지는 서로 import하지 않는다.
- 세션 구현은 `HttpSession`만 사용하고 Redis API를 직접 호출하지 않는다.
- 인메모리와 Redis는 별도 인증 방식이 아니라 동일 세션 인증의 저장소 차이다.
- 공개 SSE는 인증하지 않고 개인화 SSE만 활성 인증 방식으로 보호한다.
- 세션 모드의 개인화 SSE는 session cookie를 사용하므로 별도 ticket을 발급하지 않는다.
- 보안 보장이 다른 두 방식을 처리량만으로 비교하지 않는다.
- 실험 종료 후 하나의 인증 방식만 남긴다.

## 8. 선택 기준

### JWT 유지 근거가 강해지는 조건

- Redis 세션 조회가 목표 TPS 또는 p99 지연을 만족하지 못한다.
- 인증 서버와 여러 리소스 서버가 독립 배포돼 로컬 토큰 검증의 가치가 커진다.
- Redis 장애 중에도 인증된 요청을 제한적으로 계속 처리해야 한다.
- Access Token 즉시 무효화가 필수 요구사항이 아니며 짧은 만료 시간의 위험을 수용할 수 있다.
- Refresh Rotation과 SSE ticket의 추가 복잡성이 운영 가능한 수준임을 검증한다.

### Redis 세션 선택 근거가 강해지는 조건

- Redis 세션 조회를 포함해 목표 TPS와 지연 기준을 만족한다.
- 같은 서비스·도메인 경계가 유지된다.
- 즉시 로그아웃·강제 만료의 운영 가치가 크다.
- 개인화 SSE에서 ticket을 제거해 구현과 재연결 흐름을 단순화할 수 있다.
- Redis를 이미 운영해 추가 인프라 비용이 제한적이다.
- Redis 장애 시 명확한 fail-closed·복구 정책을 마련할 수 있다.

## 9. 면접·발표용 설명

> 초기에는 다중 인스턴스와 입찰 요청 경로의 인증 성능을 고려해 JWT를 선택했습니다. 그러나 Refresh Rotation과 로그아웃을 구현하면서 서버 상태가 필요해졌고, Access Token 즉시 무효화에는 별도 denylist가 필요했습니다. 개인화 SSE에는 EventSource의 Authorization 헤더 제약 때문에 일회용 ticket도 추가됐습니다. 저희 서비스는 같은 도메인의 모듈러 모놀리스이고 다중 인스턴스 지원을 위해 Redis를 사용할 예정이므로 Redis 세션도 요구사항을 충족할 수 있다고 판단했습니다. 그래서 기존 JWT를 즉시 폐기하지 않고 동일한 인증 계약 아래 인메모리·Redis 세션을 구현해 성능과 장애 동작을 비교한 뒤, 측정 결과와 운영 복잡성을 기준으로 하나를 선택하기로 했습니다.

## 10. 의사결정 기록 원칙

- 구현하지 않은 보완책을 현재 기능처럼 말하지 않는다.
- 측정하지 않은 성능 차이를 사실처럼 말하지 않는다.
- Redis 장애 시 계속 허용하는 정책은 가용성 이점과 보안 위험을 함께 기록한다.
- 최종 선택뿐 아니라 선택하지 않은 대안과 기각 이유를 남긴다.
- 최종 선택 후 실험 코드를 제거하더라도 이 README는 비교 과정을 보존한다.

## 참고 자료

- [OWASP REST Security Cheat Sheet - JWT](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html#jwt)
- [Spring Session - Redis로 저장소 구성](https://docs.spring.io/spring-session/reference/4.0/configuration/redis.html)

> 이 문서는 codex의 도움을 받아 작성하였습니다
