# 관측 공백 및 계측 보강

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** [`2-slo-error-budget.md`](2-slo-error-budget.md)에서 정한 SLO 중 지금
계측이 없어 측정 불가능한 항목(SSE 연결 수, SSE 연결 수립 시간, SSE 이벤트
end-to-end 전달, SSE executor 포화와 CallerRuns 소요시간, Tomcat 커넥터별 스레드)을
Micrometer 메트릭으로 노출한다.

**Architecture:** 기존 `AuctionSseConnectionManager`/`NotificationSseConnectionManager`에
Gauge/Timer를 추가하고, SSE executor의 `RejectedExecutionHandler`를
카운팅 가능한 구현으로 교체하되 기존 `CallerRuns` 정책은 유지한다. Tomcat 스레드는 Boot 기본 바인더가 관리포트
분리 구조에서 하나만 잡아내는 문제가 있어, 커넥터별 태그를 붙인 커스텀
`ApplicationListener<WebServerInitializedEvent>`로 대체한다.

**Tech Stack:** Spring Boot, Micrometer, JUnit 5, Mockito.

## Global Constraints

- `docs/eunki/2026-08-09-bid-load-test-metrics-plan.md`에서 정한 제약을 그대로
  따른다: 메트릭 태그에 auction/user/bid/request ID를 넣지 않는다.
- 기존 `dbidding_auction_sse_send_duration_seconds`,
  `dbidding_auction_sse_send_failures_total` 메트릭 이름/의미는 바꾸지 않는다
  — 이번 작업은 그 옆에 신규 메트릭만 추가한다.
- SSE 연결 관리자의 기존 등록/해제/브로드캐스트 로직(동작)은 바꾸지 않는다
  — 계측만 얹는다. 코얼레싱, 직렬화 1회 처리, auctionId 스코프 분리는 이
  문서의 범위가 아니다(필요성이 확인되면 별도 이슈로 분리).

## Task 1: SSE 연결 수 Gauge

**Files:**
- Modify: `backend/src/main/java/com/dbidding/sse/auction/AuctionSseConnectionManager.java`
- Modify: `backend/src/main/java/com/dbidding/notification/NotificationSseConnectionManager.java`
- Test: 각 클래스의 기존 테스트 파일에 추가

**Interfaces:**
- 이미 존재하는 `AuctionSseConnectionManager.connectionCount()`를 `Gauge`로
  등록한다 (`dbidding.sse.connections{stream="auction"}`).
- `NotificationSseConnectionManager`에는 전체 emitter 수를 반환하는 메서드가
  없으므로 추가한 뒤 같은 방식으로 등록한다(`stream="notification"`).

- [ ] **Step 1: 실패하는 테스트 작성** — 두 매니저 각각 등록 시 게이지가
      1 증가, 해제(`onCompletion`/`onTimeout`/`onError`) 시 감소하는지 검증
- [ ] **Step 2: 테스트가 새 API 부재로 실패하는지 확인**
- [ ] **Step 3: `MeterRegistry`를 주입받아 생성자에서 `Gauge.builder(...).tag("stream", ...).register(registry)` 등록**
- [ ] **Step 4: 테스트 통과 확인**

## Task 2: SSE 연결 수립 시간 Timer

**Files:**
- Modify: 위와 동일한 두 클래스

**Interfaces:**
- `register()` 진입 시점부터 최초 `"connected"` 이벤트 전송 완료까지의
  시간을 `dbidding.sse.connect.duration{stream=...}` Timer로 기록한다.

- [ ] **Step 1: 실패하는 테스트 작성** — `register()` 호출 후 Timer 카운트가
      1 증가하는지 검증(값 자체보다 기록 여부를 확인)
- [ ] **Step 2: 테스트 실패 확인**
- [ ] **Step 3: `register()` 시작 시각을 기록하고, `send()`로 `"connected"`
      이벤트를 보낸 직후 `Timer.record(...)` 호출**
- [ ] **Step 4: 테스트 통과 확인**

## Task 3: SSE 이벤트 end-to-end 전달 지연

**Files:**
- 백엔드는 변경 없음 — 이미 payload에 발행 시각(`published_at`/`occurred_at`)이
  포함돼 있다(`b0f552c`, `1a99481` 커밋 참고).
- Modify: `backend/src/test/k6/final-auction-load.js` — `auction-bid.js`에
  이미 있는 `recordAuctionSseDeliveryLatency` 패턴(payload의 발행 시각 대비
  수신 시각 차이를 `Trend`로 기록)을 동일하게 이식한다.

- [ ] **Step 1: `final-auction-load.js`의 `auctionSse()`에 `client.on('event', ...)`
      핸들러가 있는지 확인하고, 없으면 `auction-bid.js`의 구현을 그대로
      가져와 `auctionSseDeliveryLatency` Trend와 `Invalid` Counter를 추가**
- [ ] **Step 2: 로컬에서 짧게 실행해 지표가 0이 아닌 값으로 채워지는지 확인**

## Task 4: SSE executor 포화·CallerRuns 시간

**Files:**
- Modify: `backend/src/main/java/com/dbidding/sse/config/CountingCallerRunsPolicy.java`
- Modify: Auction·Notification SSE executor 설정 클래스
- Test: 각 설정 클래스 테스트

**Interfaces:**
- Auction·Notification SSE executor의 기존 `CallerRuns` 정책을 유지하되, 포화로
  `RejectedExecutionHandler`가 호출된 횟수를 `Counter`
  (`dbidding.sse.broadcast.saturated`, Prometheus 노출명
  `dbidding_sse_broadcast_saturated_total`)로 기록한다. 태그는 저카디널리티
  `executor=auction|notification`만 사용한다.
- `task.run()` 구간은 `Timer`
  (`dbidding.sse.broadcast.saturated.caller-runs.duration`, Prometheus 노출명
  `dbidding_sse_broadcast_saturated_caller_runs_duration_seconds`)로 기록한다.
- 두 메트릭은 **유실된 이벤트 수가 아니라 포화되어 호출 스레드에서 처리한
  작업의 횟수와 소요시간**이다. 작업을 버리는 `DiscardPolicy`로 바꾸지 않는다.

> **갱신(추후, #544)**: 위 "DiscardPolicy로 바꾸지 않는다"는 fan-out/heartbeat류
> executor(caller가 SSE 전용 백그라운드 스레드)에는 여전히 유효하지만, 다음
> 두 executor는 이후 `CountingDiscardPolicy`로 교체됐다.
> - `notificationTaskExecutor`(origin) — caller가 `@TransactionalEventListener(AFTER_COMMIT)`을
>   커밋한 입찰/주문 API 스레드일 수 있다. 알림은 별도 복구 로직으로 목록
>   자체는 유실되지 않고 SSE 실시간 push만 재시도 안 되는 구조라 API 스레드
>   보호를 우선했다.
> - `walletSseTaskExecutor` — 기본(Redis) 프로필에서는 caller가 Redis
>   subscriber 스레드지만, `local-sse` 프로필에서는 `WalletSseEventListener`가
>   비동기가 아니라 커밋 스레드에서 바로 이 executor까지 이어진다. 순수 SSE
>   send만 하고 DB write 등 부작용이 없어, discard해도 잔고 값(이미 커밋됨)은
>   유실되지 않는다.
>
> 메트릭 이름/태그(`dbidding.sse.broadcast.saturated`, tag `executor`)는
> CallerRuns와 동일하게 유지했다 — 기존 대시보드/알림 쿼리가 그대로 동작한다.
> 다만 duration 타이머(`dbidding.sse.broadcast.saturated.caller-runs.duration`)는
> discard executor에서는 더 이상 기록되지 않는다(task를 아예 실행하지 않으므로).
>
> 또한 `auctionSseBroadcastTaskExecutor`/`walletSseTaskExecutor`는 `local-sse`
> 프로필에서 caller가 API/커밋 스레드로 바뀌는 동일한 리스크가 있다(#544
> 이슈 코멘트 참고) — `auctionSseBroadcastTaskExecutor`는 아직 CallerRuns를
> 유지 중이며, 필요성이 확인되면 별도로 재검토한다.

- [ ] **Step 1: 실패하는 테스트 작성** — 풀+큐를 가득 채운 뒤 태스크를
      추가로 제출하면 포화 카운터와 CallerRuns Timer가 증가하고, 호출
      스레드에서 실행되는지 검증
- [ ] **Step 2: 테스트 실패 확인**
- [ ] **Step 3: `RejectedExecutionHandler` 구현체 작성 후 `ThreadPoolTaskExecutor`에 설정**
- [ ] **Step 4: 테스트 통과 확인**

## Task 5: Tomcat 커넥터별 스레드 메트릭

**Files:**
- Add: `backend/src/main/java/com/dbidding/global/metrics/PerConnectorTomcatThreadMetrics.java`
- Test: 신규 클래스 테스트

**Interfaces:**
- `ApplicationListener<WebServerInitializedEvent>`를 구현해, 이벤트가 발생한
  `TomcatWebServer`의 모든 `Connector`를 순회하며 `ThreadPoolExecutor`인
  경우 `tomcat.connector.threads.{busy,current,max}` Gauge를 `connector`
  태그(예: `main`, `management`)와 함께 등록한다.
- Boot 기본 `TomcatMetricsBinder`는 관리포트가 분리된 구조에서 커넥터 하나만
  잡아내 `tomcat_threads_*`가 비어 보이는 문제가 있다 — 이 메트릭이 그
  자리를 대체한다. 기존 `tomcat_threads_*` 메트릭은 그대로 두고 손대지
  않는다(있으면 있는 대로, 없으면 없는 대로 — 신규 메트릭만 추가).

- [ ] **Step 1: 실패하는 테스트 작성** — 가짜/임베디드 Tomcat 컨텍스트로
      이벤트를 발생시켜 게이지가 `connector` 태그별로 등록되는지 검증
- [ ] **Step 2: 테스트 실패 확인**
- [ ] **Step 3: 리스너 구현**
- [ ] **Step 4: 테스트 통과 확인, 로컬에서 `/actuator/prometheus` 응답에
      `tomcat_connector_threads_busy{connector="main"}` 등이 보이는지 수동 확인**

## 완료 기준

- [ ] 5개 Task 전부 테스트 통과
- [ ] `cd backend && ./gradlew test` 전체 통과
- [ ] 로컬 기동 후 `/actuator/prometheus`에서 5개 신규 메트릭 계열 확인

> 이 문서는 Claude의 도움을 받아 작성하였습니다

> 이 문서는 codex의 도움을 받아 작성하였습니다
