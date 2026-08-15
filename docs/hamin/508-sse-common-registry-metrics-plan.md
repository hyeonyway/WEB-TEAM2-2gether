# Auction/Notification/Wallet SSE 커넥션 관리 공통화 (계획)

refs #508 (선행: #507, dev에 머지됨)

## 배경

`AuctionSseConnectionManager`/`NotificationSseConnectionManager`/`WalletSseConnectionManager`가 완전히 따로 구현돼 있고, `*Metrics` 클래스도 별도다. close-reason 메트릭(#491) 추가 때 거의 동일한 로직(connect timer, close reason 기록, send failure 처리)을 세 번 복붙했다.

## 설계 방향

**상속이 아니라 위임(composition).** `broadcast(AuctionStreamPayload)`/`push(Integer, payload)`처럼 도메인별 API 시그니처가 달라서 템플릿 메서드로 묶으면 억지스럽다. 세 도메인에서 기계적으로 동일한 부분만 공유 컴포넌트로 뽑고, 각 `*ConnectionManager`는 자기 API를 유지한 채 위임한다.

**범위: 등록/제거/connectionCount/메트릭 배선만 공유. 비동기·디스패치 계층 구조는 도메인별로 그대로 둔다.** 세 도메인의 "한 번에 몇 명한테 보내는지" 단위가 다르다:
- Auction: 이벤트 1개 → emitter 여러 개(`AuctionSseSendDispatcher`가 emitter 1개당 디스패치, #507의 broadcast/send executor 분리 포함)
- Notification: Redis 메시지 1개 = 유저 여러 명의 리스트 → `onMessage()`/`LocalNotificationPushPublisher`가 배치 전체를 fanout executor에 얹고, `NotificationPushDispatcher`가 유저 1명당 추가로 디스패치
- Wallet: Redis 메시지 1개 = 유저 1명 → `push()`가 그 유저의 emitter들에 직접 `executor.execute()`

이 계층을 하나로 합치면(예: Notification의 바깥쪽 `@Async`를 없애고 `push()` 자체로 옮기는 식) 프로필별 task 분할 단위가 조용히 바뀌어(기본 프로필에서 batch 단위 → 유저 단위) #362가 우려한 bounded pool task 폭발 위험이 재발할 수 있다 — 검토 중 확인했다. 그래서 이 부분은 건드리지 않는다.

## 신규/변경 컴포넌트

1. **`SseMetrics`**(신규, `com.dbidding.sse.metrics`) — `Auction`/`Notification`/`WalletSseMetrics` 대체. `(MeterRegistry, String stream, Clock)`로 파라미터화, `stream` 값으로 지금과 동일한 metric 이름/태그 생성(대시보드 영향 없음). Auction에만 있던 `sendTimer`를 Notification/Wallet에도 추가(순수 추가, 기존 metric 변경 없음).
2. **`SseSendDispatcher`**(`com.dbidding.auction.sse` → `com.dbidding.sse`로 승격, 이름 일반화) — `SynchronousSseSendDispatcher`/`PerConnectionSseSendDispatcher(TaskExecutor)`. Auction은 프로필별 선택 유지. Wallet은 프로필 분기 없이 항상 `PerConnectionSseSendDispatcher(walletSseTaskExecutor)`(동작 동일, 표현만 통일, 가상 스레드 옵션은 추가하지 않음).
3. **`SseEmitterRegistry<K>`**(신규, `com.dbidding.sse`) — `register(Set<K> keys, SseEmitter, @Nullable String sessionId)`, `emittersFor(K key)`, `send(...)`, `remove/removeAndComplete`, `connectionCount`, `heartbeatAll()`. `Set<K> keys`로 일반화해 Auction(emitter 1개 : auctionId 여러 개)과 Notification/Wallet(`Set.of(userId)`)를 같은 코드로 커버. 세션 레지스트리는 생성자에 옵션(nullable)으로 받아 Auction만 비워둔다.

executor config 3개(`AuctionSseExecutorConfig`/`NotificationExecutorConfig`/`WalletSseExecutorConfig`)는 범위 밖 — 그대로 두고 `@Qualifier`로 계속 주입받는다. 각 config에 도메인별 `SseMetrics`/(`AuctionSseSendDispatcher`) `@Bean` 메서드만 추가했다(Wallet은 `PerConnectionSseSendDispatcher`를 `WalletSseConnectionManager` 생성자 안에서 직접 생성 — 프로필 분기가 없어 별도 빈이 불필요).

## 마이그레이션 순서(실제 진행)

1. `SseMetrics` 통합 — 세 `*Metrics` 클래스 삭제, `com.dbidding.sse.metrics.SseMetrics`로 대체. 각 도메인 executor config에 `@Bean(name = "<domain>SseMetrics")`로 배선.
2. `SseSendDispatcher` 승격 — `com.dbidding.auction.sse`의 세 클래스(인터페이스+Synchronous+PerConnection)를 `com.dbidding.sse`로 이동, 이름에서 `Auction` 제거. Auction의 프로필별 빈 선택은 `AuctionSseExecutorConfig`의 `@Bean`으로 이전.
3. `SseEmitterRegistry<K>` 구현 + 자체 단위 테스트.
4. Notification 이관 — 등록/제거/메트릭만, `NotificationPushDispatcher`/배치 단위 `@Async`는 그대로.
5. Wallet 이관 — `SseEmitterRegistry` + `PerConnectionSseSendDispatcher`로 교체(동작 동일). heartbeat도 원래처럼 커넥션별 dispatch를 유지해야 해서, `SseEmitterRegistry`에 `allEmitters()`를 추가해 Wallet이 직접 순회하며 자기 dispatcher로 보내도록 함(`heartbeatAll()`은 Auction/Notification처럼 동기 처리가 맞는 도메인 전용).
6. Auction 이관(가장 복잡 — 다중 키, 세션 없음, #507 executor 분리 보존).

## 구현 중 발견한 버그와 수정

Micrometer의 `Gauge.builder(name, stateObject, valueFunction)`는 `stateObject`를 **약한 참조**로만 붙잡는다(GC를 막지 않기 위한 의도적 설계). 원래 세 `*ConnectionManager`는 `private final Supplier<Number> connectionCountSupplier = this::connectionCount;`처럼 필드에 저장해 강한 참조를 유지했는데, 이관하면서 실수로 `metrics.registerConnectionGauge(registry::totalConnectionCount)`처럼 인라인 메서드 참조를 바로 넘겼다 — 이 경우 아무도 그 메서드 참조 객체를 강하게 참조하지 않아 GC 타이밍에 따라 연결수 gauge가 0으로 굳어버릴 수 있다. Auction 테스트(`연결_등록과_해제에_따라_경매_SSE_연결_Gauge가_변한다`)에서 실제로 재현돼 발견 — 세 도메인 모두 `Supplier<Number> connectionCountSupplier` 필드를 되살려 수정했다.

## 검증

- 각 도메인 기존 `*ConnectionManagerTest`/`*MetricsTest` 통과(동작 무변경 확인) — `./gradlew test --tests "com.dbidding.auction.*" --tests "com.dbidding.notification.*" --tests "com.dbidding.wallet.*" --tests "com.dbidding.sse.*"` 및 전체 `./gradlew test` 통과
- metric 이름/태그가 이관 전후로 동일한지 확인(`SseMetricsTest`)
- `SseEmitterRegistry` 자체 단위 테스트(다중 키 등록/제거, 세션 레지스트리 옵션 있음/없음, heartbeatAll/disconnectAll)
