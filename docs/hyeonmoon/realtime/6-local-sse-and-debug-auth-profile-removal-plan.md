# local-sse / debug-auth 프로필 제거 Implementation Plan (wallet·security 구간)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로컬 개발 환경이 `deploy/docker-compose.local.yml` 기준 `SPRING_PROFILES_ACTIVE=redis,sse-virtual-threads`(MySQL+Redis 컨테이너 상시 연결)로 정착하면서 죽은 코드가 된 `local-sse`/`debug-auth` 프로필 분기 중, wallet·security 구간을 제거한다. 이슈 [#572](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/572)의 항목 3·4·8.

**Architecture:** 대상은 셋으로 나뉜다.

1. **wallet/sse — `local-sse` 분기**: `WalletSsePublisher` 구현체가 `RedisWalletSsePublisher`(`@Profile("!local-sse")`)와 `LocalWalletSsePublisher`(`@Profile("local-sse")`) 둘이었다. `local-sse`를 아무도 안 쓰므로 후자를 지우고 전자를 무조건 등록으로 바꾼다.
2. **global/security/session — `local-sse` 분기**: `SessionSseTerminationPublisher` 구현체도 같은 모양(`RedisSessionSseTerminationPublisher` / `LocalSessionSseTerminationPublisher`)이라 동일하게 처리한다.
3. **global/security — `debug-auth` 분기**: `TestAuthFilter`(`@Profile("debug-auth")`)는 `X-Debug-User-Id` 헤더로 JWT 없이 로그인 우회하던 로컬 개발용 필터. 로컬도 이제 실제 인증 흐름을 그대로 쓰므로 제거한다.

**범위 제외** (이슈 #572 본문 및 팀 논의 근거):
- `test` 프로필(`AuctionSseTestAuctionReader` 등) — `AuctionSseContractTest`가 물고 있어 사용 중.
- `sse-virtual-threads` on/off 토글 — README에 기록된 가상스레드 A/B 성능 비교용으로 유지.
- redis 마이그레이션 이전 monolith 잔재(`CardAuctionAdapter`, `DbBidExecutor` 등, `@Profile("!redis")`) — 별도 사안, 테스트 다수가 default 프로필로 돌아 CI 전략까지 손봐야 해서 이번 범위 아님.
- `global/config/RedisPubSubConfig.java` — 이 파일 생성자가 auction/notification/wallet/session 4개 도메인 subscriber를 전부 물고 있어 여러 담당자가 동시에 손대면 충돌한다. auction·notification 구간(다른 담당자)이 끝난 뒤 마지막에 한 번에 정리한다. 이번 커밋들에서는 건드리지 않는다.
- `auction/sse`, `notification/sse`의 `local-sse` 분기와 `auction-mock`/`local-upload` 분기 — 다른 담당자 구간, 이슈 #572의 항목 1·2·6·7.

## 조사 근거

```bash
cd backend
grep -rn "local-sse" src/main/java/com/dbidding/wallet src/main/java/com/dbidding/global/security
# -> LocalWalletSsePublisher.java, RedisWalletSsePublisher.java, WalletSseRedisSubscriber.java,
#    WalletSseExecutorConfig.java(주석), LocalSessionSseTerminationPublisher.java,
#    RedisSessionSseTerminationPublisher.java, SessionSseTerminationRedisSubscriber.java

grep -rln "debug-auth" src/main/java src/test/java
# -> TestAuthFilter.java, CurrentUserWebMvcTest.java (CurrentUserDefaultProfileWebMvcTest.java는
#    default 프로필에서 debug-auth 헤더가 무시되는지 검증하는 대조 테스트라 같이 지운다)
```

`RedisWalletSsePublisher`/`WalletSseRedisSubscriber`는 `local-sse` 축과 무관하게 살아있는 코드다 — 알림·지갑 SSE 커넥션 통합([5-notification-wallet-sse-stream-consolidation-plan.md](5-notification-wallet-sse-stream-consolidation-plan.md), #557) 이후에도 `RedisWalletSsePublisher → Redis 채널 → WalletSseRedisSubscriber → WalletSseConnectionManager.push()`로 이어지는 실제 운영 경로는 그대로다. `@Profile("!local-sse")`만 떼고 클래스는 유지한다.

## Global Constraints

- `WalletSsePublisher`/`SessionSseTerminationPublisher` 인터페이스 자체는 유지한다 — redis pub/sub 채널 계약 문서화 목적이 있고, 이번 정리와 별개 사안.
- `RedisWalletSsePublisher`, `WalletSseRedisSubscriber`, `RedisSessionSseTerminationPublisher`, `SessionSseTerminationRedisSubscriber`는 클래스 자체를 건드리지 않는다 — `@Profile` 애노테이션만 제거한다.
- `global/config/RedisPubSubConfig.java`는 이번 범위에서 수정하지 않는다(위 범위 제외 참고).
- `TestAuthFilterTest`가 검증하는 `TestAuthFilter`의 개별 동작(양의 정수만 인정, 0 이하 무시, JWT 우선 등)은 필터 자체를 지우므로 같이 삭제한다 — 대체 테스트를 만들지 않는다.

---

### Task 1: wallet/sse `local-sse` 분기 제거

**Files:**
- Delete: `backend/src/main/java/com/dbidding/wallet/sse/LocalWalletSsePublisher.java`
- Delete: `backend/src/test/java/com/dbidding/wallet/sse/LocalWalletSsePublisherTest.java`
- Edit: `backend/src/main/java/com/dbidding/wallet/sse/RedisWalletSsePublisher.java` — `@Profile("!local-sse")` 제거
- Edit: `backend/src/main/java/com/dbidding/wallet/sse/WalletSseRedisSubscriber.java` — `@Profile("!local-sse")` 제거
- Edit: `backend/src/main/java/com/dbidding/wallet/sse/WalletSseExecutorConfig.java` — javadoc의 `local-sse`/`LocalWalletSsePublisher` 언급 제거

**Interfaces:**
- Removes: `wallet.sse.LocalWalletSsePublisher`
- Preserves: `WalletSsePublisher` 인터페이스, `RedisWalletSsePublisher`(이제 유일한 구현체)

- [x] **Step 1:** 위 5개 파일 삭제/수정
- [x] **Step 2:** `./gradlew compileJava compileTestJava` 통과 확인
- [x] **Step 3:** 커밋

### Task 2: global/security/session `local-sse` 분기 제거

**Files:**
- Delete: `backend/src/main/java/com/dbidding/global/security/session/LocalSessionSseTerminationPublisher.java`
- Edit: `backend/src/main/java/com/dbidding/global/security/session/RedisSessionSseTerminationPublisher.java` — `@Profile("!local-sse")` 제거
- Edit: `backend/src/main/java/com/dbidding/global/security/session/SessionSseTerminationRedisSubscriber.java` — `@Profile("!local-sse")` 제거

**Interfaces:**
- Removes: `global.security.session.LocalSessionSseTerminationPublisher`
- Preserves: `SessionSseTerminationPublisher` 인터페이스, `RedisSessionSseTerminationPublisher`(이제 유일한 구현체)

- [x] **Step 1:** 위 3개 파일 삭제/수정
- [x] **Step 2:** `./gradlew compileJava compileTestJava` 통과 확인
- [x] **Step 3:** 커밋

### Task 3: global/security `debug-auth` 분기 제거

**Files:**
- Delete: `backend/src/main/java/com/dbidding/global/security/TestAuthFilter.java`
- Delete: `backend/src/test/java/com/dbidding/global/security/TestAuthFilterTest.java`
- Delete: `backend/src/test/java/com/dbidding/global/security/CurrentUserWebMvcTest.java`
- Delete: `backend/src/test/java/com/dbidding/global/security/CurrentUserDefaultProfileWebMvcTest.java`

**Interfaces:**
- Removes: `global.security.TestAuthFilter`
- 영향 없음: `RequestUserIdWriter`, `RequestCurrentUserProvider`, `JwtAuthFilter` 등 기본 인증 흐름은 `debug-auth`와 무관하게 그대로 유지

- [x] **Step 1:** 위 4개 파일 삭제
- [x] **Step 2:** `./gradlew compileJava compileTestJava` 통과 확인
- [x] **Step 3:** 커밋
