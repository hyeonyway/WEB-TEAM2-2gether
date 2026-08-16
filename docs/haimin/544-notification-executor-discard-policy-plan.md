# SSE/알림 executor 포화 정책: CallerRuns → Discard (origin/wallet)

이슈: #544

## 배경

`notificationTaskExecutor`(알림 저장+발행 origin, `NotificationEventListener`)는
`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`로 동작한다. caller는
트랜잭션을 커밋한 스레드이며, 입찰/주문 등 API 요청 스레드일 수 있다.

현재 `CountingCallerRunsPolicy`가 적용돼 있어 포화 시 API 요청 스레드가 알림
저장(DB write)+발행을 직접 떠안는다 — API 지연 증가와 HikariCP 풀 압박이
겹치는 위험이 있다.

`notificationFanOutTaskExecutor`/`auctionSseTaskExecutor`(heartbeat)는 caller가
Redis subscriber/heartbeat 스케줄러 등 SSE 전용 백그라운드 스레드라 CallerRuns가
API 응답 경로에 영향을 주지 않는다. 이들은 그대로 둔다.

`auctionSseBroadcastTaskExecutor`/`walletSseTaskExecutor`는 기본(Redis) 프로필에서는
caller가 Redis subscriber 스레드라 안전하지만, `local-sse` 프로필(Redis 없는 로컬
개발 환경)에서는 `LocalAuctionStreamPublisher`/`LocalWalletSsePublisher`가
API/커밋 스레드에서 직접 이 executor까지 호출을 이어가 CallerRuns가 그 스레드를
막을 수 있다. `local-sse`는 dev 전용이라 우선순위는 낮지만 이슈에 기록해둔다.

알림은 별도 복구 로직으로 목록 자체는 유실되지 않고(SSE 실시간 push만
재시도되지 않음) — origin executor에 한해 discard로 바꿔도 데이터 정합성
문제가 없다.

`walletSseTaskExecutor`도 순수 SSE send만 하고 다른 부작용(DB write 등)이
없다 — discard해도 잔고 값 자체(DB에 이미 커밋된 상태)는 유실되지 않고,
재연결/재조회 시 최신 값을 다시 받는다. origin과 달리 "이 이벤트가 아예
기록되지 않는" 문제가 없으므로 discard로 전환한다.

## 변경 사항

- `CountingDiscardPolicy` 추가 (`com.dbidding.sse.config`) — task를 버리고
  기존 `dbidding.sse.broadcast.saturated`(tag `executor`) 카운터를 그대로
  증가시킨다(메트릭 이름/태그는 CallerRuns와 동일하게 유지 — 기존 대시보드/알림
  쿼리가 그대로 동작). duration 타이머는 없다(task를 아예 실행하지 않으므로).
- `notificationTaskExecutor`, `walletSseTaskExecutor`의
  `RejectedExecutionHandler`를 `CountingDiscardPolicy`로 교체. 나머지
  executor는 변경 없음.
- `NotificationExecutorConfigTest`의 origin executor 테스트, 신규
  `WalletSseExecutorConfigTest`를 discard 동작 검증으로 작성/갱신 (caller
  스레드에서 실행되지 않고, 기존 카운터만 증가하는지).
- `docs/hyeonmoon/observability/3-metrics-gap-and-instrumentation.md`에
  Task 4의 "DiscardPolicy로 바꾸지 않는다" 결정이 origin/wallet executor에는
  더 이상 유효하지 않다는 각주 추가.

## 완료 기준

- [ ] `NotificationExecutorConfigTest`, `WalletSseExecutorConfigTest` 통과
- [ ] fan-out/heartbeat 계열 executor는 CallerRuns 그대로 유지 확인
- [ ] 이슈에 `local-sse` 프로필 리스크 기록

> 이 문서는 claude의 도움을 받아 작성되었습니다.
