# 이슈 7: 최종 인증 방식 결정과 구현 계획

## 1. 이슈 경계

- 선행 문서: [`README.md`](README.md), [`6-final-selection-and-cleanup.md`](6-final-selection-and-cleanup.md)
- 대응 이슈: [#469](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/469)
- 목표: 세션(Redis 백킹)을 최종 인증 방식으로 확정하고, JWT 구현을 완전히 제거한다
- 이 문서는 `6-final-selection-and-cleanup.md`가 정의한 일반 절차를, 이 프로젝트의 실제 결정과 실제 코드 위치에 맞춰 구체화한 실행 계획이다

## 2. 로드맵 대비 실제 진행 상태 — 정직하게 기록

원래 로드맵(README 5절)은 4단계(Redis 세션 다중 인스턴스 검증) → 5단계(동일 조건 성능·장애 비교 측정) → 6단계(측정 결과 기반 최종 선택) 순서였다.

**이 결정은 4·5단계를 완료하지 않은 상태에서 내려졌다.** Redis 세션 스토어(`SessionStore.REDIS`)는 아직 코드에 없고(`SessionStore` enum은 현재 `MEMORY`뿐), TPS·p99·Redis 장애 시나리오에 대한 실측 비교도 수행하지 않았다. `README.md` 10절의 원칙("측정하지 않은 성능 차이를 사실처럼 말하지 않는다")에 따라, 이 결정은 **성능 측정에 근거한 것이 아니라 기능·보안 요구사항과 운영 복잡도에 근거한 정성적 판단**임을 명시한다.

이렇게 순서를 건너뛴 이유:
- 팀 일정(부트캠프 종료 시점)상 정식 벤치마크·장애주입 실험을 별도로 수행할 여유가 부족함
- 이 서비스가 이미 "즉시 revoke 필수"(계정 정지, `#470`/`#472`)라는 요구사항을 독립적으로 확정했고, 이 요구사항 자체가 README 8절의 "Redis 세션 선택 근거가 강해지는 조건" 중 가장 무거운 조건("즉시 로그아웃·강제 만료의 운영 가치가 크다")을 이미 충족시킴 — 성능 차이가 크지 않다면(측정 안 됐지만, 두 방식 다 인증 검증에 Redis 조회가 필요해지는 구조로 수렴하므로 격차가 클 것으로 보이지 않음) 이 요구사항 하나만으로도 실질적으로 8절의 결론과 같은 방향을 가리킴
- 남은 위험: 실제 Redis 세션 조회가 입찰 핫패스에서 TPS/p99를 유의미하게 악화시킬 가능성은 **미검증 상태로 남는다.** 이 문서는 이 리스크를 숨기지 않고 아래 6절에 명시한다

## 3. 최종 결정

**세션(Spring Session Data Redis) 채택, JWT 구현 완전 제거.**

### 선택 근거 (기능·보안, 측정 아님)
- 개인화 SSE(입찰/지갑/알림/대시보드)는 세션 쿠키와 `EventSource`의 credential 전송을 사용하면 별도 ticket 발급 시스템이 불필요해진다. 교차 origin 연결은 프론트의 `withCredentials: true`와 서버 CORS credential 허용을 함께 유지한다. JWT 쪽 ticket 구현(`InMemoryTicketProvider`)은 멀티 인스턴스에서 깨지는 실제 버그가 있었다(`#138`, closed-as-superseded)
- 계정 정지·탈취 대응(`#470`, `#472`)의 즉시 revoke 요구사항을 세션은 스토어 삭제만으로 기본 충족. JWT로 동등 수준을 만들려면 버전카운터+로컬캐시+Pub/Sub 무효화를 별도로 설계·구현·유지해야 함
- 세션은 JWT의 서명·알고리즘 검증과 Refresh Token 회전 구현을 제거할 수 있다. 대신 세션 ID도 bearer credential이므로 cookie 탈취·고정 공격과 CSRF 방어는 현재 세션 구성으로 계속 관리한다
- 현재 아키텍처가 auction/wallet/order/dashboard 전부 하나의 Redis에 물려있는 모듈러 모놀리스라, JWT의 "서비스 간 self-contained claim 전달" 이점이 지금은 해당 없음
- 멀티 인스턴스로 확장돼도 이 판단은 유지됨: Spring Session Data Redis는 모든 인스턴스가 같은 Redis를 세션 저장소로 공유하므로 `findByPrincipalName` 같은 조회가 인스턴스 수와 무관하게 동작함. JWT였다면 revoke 요구사항 때문에 결국 동일하게 Redis 기반 메커니즘을 별도로 만들어야 했으므로, 멀티 인스턴스가 JWT를 유리하게 만드는 조건이 아니다

### 기각한 대안: "JWT를 MSA 분리 대비용으로 남긴다"
검토했으나 기각함. 향후 입찰/조회 경로를 독립 서비스로 분리할 가능성이 있어 처음에는 JWT를 완전히 지우지 않고 대비용으로 남기는 안을 고려했다. 기각 이유:
- 실제로 그 분리가 일어나는 시점의 요구사항(서비스 경계, 인증 위임 방식, 시크릿 관리 체계)은 지금 예측 불가능하며, 지금 구현이 그때 맞는 형태라는 보장이 없다
- "혹시 몰라서" 유지하는 코드는 방치되기 쉽다 — 이미 JWT의 ticket 시스템(`#138`)이 방치된 채 멀티 인스턴스 버그를 안고 있었던 전례가 있다
- "대비용으로 남긴다"의 기준을 "실제로 즉시 사용 가능한 상태(revoke 메커니즘까지 완비)"로 잡으면 유지 비용이 완전 삭제와 비슷해지고, 기준을 "설계만 문서화"로 낮추면 실제로 못 쓰는 장식이 된다 — 어느 쪽이든 유지할 실익이 없다고 판단
- 진짜로 독립 데이터스토어를 갖는 MSA 분리가 결정되는 시점에, 그때의 실제 요구사항에 맞춰 새로 설계하는 편이 낫다

## 4. 구현 계획 (실제 코드 위치)

`6-final-selection-and-cleanup.md` 6절 체크리스트를 이 저장소의 실제 파일에 대응시킨다.

### 4.1 세션 스토어 전환
- `spring-session-data-redis` 의존성 추가 (`backend/build.gradle`)
- `SessionStore`, `app.session.store`, `SESSION_STORE`를 제거한다. Redis가 유일한 저장소면 단일 값 enum이나 저장소 선택 설정을 유지하지 않는다. cookie 속성도 `server.servlet.session.cookie` 한 곳만 원본으로 남기고, 중복된 `SessionProperties`는 종료 cookie 처리 방식과 함께 정리한다
- `SessionAuthConfiguration`(`backend/src/main/java/com/dbidding/global/security/session/SessionAuthConfiguration.java`)에서 indexed Redis HttpSession repository를 활성화한다. 자동 구성만으로 index가 생긴다고 가정하지 않고 `RedisIndexedSessionRepository`/`FindByIndexNameSessionRepository` 계약을 통합 테스트로 확인한다
- `SessionAuthenticationStrategy` 로그인 성공 시 표준 principal index attribute에 `userId.toString()`을 저장한다. 이후 `findByPrincipalName(userId.toString())`으로 현재 활성 세션을 찾아 `#470`의 관리자 정지와 `#472`의 단일 세션 제한에서 삭제한다
- Redis session namespace, idle TTL, 연결 timeout과 cookie secure·SameSite는 환경 설정으로 명시한다. Redis key의 eviction 정책과 세션 namespace 용량·만료·오류 alert도 운영 설정으로 승격한다
- `SessionAuthFilter`, `SessionCsrfFilter`, CSRF token 발급 API 및 Origin·Fetch Metadata 검증은 세션 전용 보안 경계로 유지한다

### 4.2 다중 인스턴스 세션 종료와 개인화 SSE
- Redis 세션 삭제만으로는 다른 인스턴스 메모리에 남은 `SseEmitter`가 즉시 종료되지 않는다. 현재 `SessionSseConnectionRegistry`는 로컬 메모리이므로, 로그아웃·관리자 정지·새 로그인에 의한 기존 세션 종료 시 `sessionId` 종료 메시지를 Redis Pub/Sub으로 발행한다
- 모든 인스턴스는 종료 메시지를 구독해 자신의 로컬 `SessionSseConnectionRegistry`에서 해당 emitter를 종료한다. 메시지 유실은 heartbeat의 세션 확인과 EventSource 재연결 시 인증 거부로 보완한다. Pub/Sub은 종료 신호 전달용이며 이벤트 영속 재전송 수단으로 사용하지 않는다
- 통합 검증은 인스턴스 A에서 세션을 삭제했을 때 인스턴스 B의 보호 API가 401이 되고, B에 연결된 개인화 SSE도 종료되는지 확인한다

### 4.3 JWT 제거 (백엔드)
- `backend/src/main/java/com/dbidding/account/authentication/jwt/**` 전체 삭제
- `backend/src/main/java/com/dbidding/global/security/jwt/**` 전체 삭제 (`JwtAuthFilter`, `SseTicketController`, `InMemoryTicketProvider`, `SseTicketAuthFilter` 포함)
- `AuthenticationMode`/`AuthenticationModeProperties` 삭제, `app.auth.mode` 분기 제거 — 세션 전용으로 단순화
- 최종 구현이 하나뿐이면 `AuthenticationStrategy` 추상화 자체의 존속 여부도 재검토 (가치 없으면 단순화)
- JWT 라이브러리 의존성, `JWT_SECRET` 등 설정을 제거한다. `application.yml`뿐 아니라 `backend/scripts/start-server.sh`, Docker Compose·배포 환경, CI 변수와 운영 문서의 JWT 요구도 함께 제거한다
- `authentication` 테이블은 롤백 기간이 끝난 뒤에만 제거한다. 현재 스키마는 수동 SQL 관리이므로 drop SQL의 적용 시점·대상 환경·롤백 기간을 배포 절차에 명시하고, JPA entity·repository 제거와 같은 릴리스에 묶지 않는다

### 4.4 프론트 제거
- Access Token memory store, Bearer header 첨부 로직 제거
- 동시 401 단일 Refresh 로직 제거
- JWT AuthTransport, SSE ticket transport 제거
- `VITE_AUTH_MODE` 분기 제거

### 4.5 후속 이슈와의 의존관계
- `#470`(관리자 계정 정지): 4.1의 principal index와 4.2의 다중 인스턴스 SSE 종료에 의존
- `#472`(절대 세션수명 12시간 + 동시세션 1개 제한): 4.1의 principal index와 4.2의 기존 세션 종료에 의존

## 5. 최종 검증 (`6-final-selection-and-cleanup.md` 8·9절 기준)

- [ ] 선택하지 않은(JWT) 패키지·테스트·환경변수·import·endpoint 0건
- [ ] 기능: 로그인/로그아웃, 계정 정지·강제 만료(`#470`), 입찰/지갑/대시보드 보호, 공개·개인화 SSE
- [ ] 사용자별 세션 조회: principal index로 같은 사용자의 활성 세션을 찾고, 관리자 정지·새 로그인 시 모두 삭제되는지 확인
- [ ] 다중 인스턴스: A에서 로그인한 세션으로 B 보호 API 호출, A에서 삭제한 세션이 B의 다음 요청과 B의 개인화 SSE 연결 모두에 즉시 반영되는지 확인
- [ ] 보안: 인증 우회 0건, 로그아웃/정지 후 잘못 허용된 요청 0건, secret/session ID 로그 노출 0건, session CSRF·Origin 검증 유지
- [ ] 장애: Redis timeout·중단 시 보호 API와 개인화 SSE 신규 연결이 fail-closed이고, 공개 조회를 인증된 사용자로 오인하지 않는지 확인
- [ ] 백엔드 전체 테스트, 프론트 lint/test/build, 다중 인스턴스 smoke test

## 6. 남은 위험 (숨기지 않고 기록)

- **성능 미검증**: Redis 세션 조회가 입찰 핫패스 TPS/p99에 미치는 영향을 정식으로 측정하지 않았다. 배포 후 실사용 부하테스트로 재확인 필요
- **Redis 장애 시 전체 인증 요청 실패**: 세션 방식은 인증 검증마다 Redis 조회가 필요해, Redis 장애가 인증 전체에 직접 전파되는 fail-closed 구조다. 명시적인 장애 대응 정책(예: Redis 장애 시 사용자에게 보여줄 오류, 복구 후 재인증 흐름)은 별도로 정리되지 않았다
- **절대 세션수명(12시간, `#472`)이 실사용 세션 길이를 실제로 넘지 않는지**는 가정이며 실사용 데이터로 재확인이 필요할 수 있음

## 참고 자료

- [`README.md`](README.md) 8절(선택 기준), 10절(의사결정 기록 원칙)
- [`6-final-selection-and-cleanup.md`](6-final-selection-and-cleanup.md)
- [Spring Session - Redis로 저장소 구성](https://docs.spring.io/spring-session/reference/4.0/configuration/redis.html)
