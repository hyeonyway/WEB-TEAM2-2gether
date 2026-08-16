# notificationTaskExecutor 포화 정책: CallerRuns → Discard

이슈: #544

## 배경

`notificationTaskExecutor`(알림 저장+발행 origin, `NotificationEventListener`)는
`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`로 동작한다. caller는
트랜잭션을 커밋한 스레드이며, 입찰/주문 등 API 요청 스레드일 수 있다.

현재 `CountingCallerRunsPolicy`가 적용돼 있어 포화 시 API 요청 스레드가 알림
저장(DB write)+발행을 직접 떠안는다 — API 지연 증가와 HikariCP 풀 압박이
겹치는 위험이 있다.

반면 `auctionSseBroadcastTaskExecutor`/`notificationFanOutTaskExecutor`/
`auctionSseTaskExecutor`(heartbeat)/`walletSseTaskExecutor`는 caller가 Redis
subscriber/heartbeat 스케줄러 등 SSE 전용 백그라운드 스레드라 CallerRuns가
API 응답 경로에 영향을 주지 않는다. 이들은 그대로 둔다.

알림은 별도 복구 로직으로 목록 자체는 유실되지 않고(SSE 실시간 push만
재시도되지 않음) — origin executor에 한해 discard로 바꿔도 데이터 정합성
문제가 없다.

## 변경 사항

- `CountingDiscardPolicy` 추가 (`com.dbidding.sse.config`) — task를 버리고
  `dbidding.executor.saturated.discarded`(tag `executor`) 카운터만 증가.
- `notificationTaskExecutor`의 `RejectedExecutionHandler`를
  `CountingDiscardPolicy`로 교체. 다른 executor는 변경 없음.
- `NotificationExecutorConfigTest`의 origin executor 테스트를 discard 동작
  검증으로 갱신 (caller 스레드에서 실행되지 않고, 새 카운터만 증가하는지).
- `docs/hyeonmoon/observability/3-metrics-gap-and-instrumentation.md`에
  Task 4의 "DiscardPolicy로 바꾸지 않는다" 결정이 origin executor에는 더 이상
  유효하지 않다는 각주 추가.

## 완료 기준

- [ ] `NotificationExecutorConfigTest` 통과
- [ ] fan-out/heartbeat 계열 executor는 CallerRuns 그대로 유지 확인

> 이 문서는 claude의 도움을 받아 작성되었습니다.
