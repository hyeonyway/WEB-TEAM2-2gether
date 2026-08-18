# SSE 가상스레드 send 캡이 broadcast() 순회 자체를 블로킹하는 문제 수정

이슈: #575

## 배경

`AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY`(`sse-virtual-threads` 프로필)로 send용 가상스레드
동시성을 캡 걸어두면, `broadcast()`/`auctionSseBroadcastTaskExecutor`와 실제 send/
`auctionSseTaskExecutor`가 이름이 다른 별도 executor라 서로 안 막힐 거라 기대했는데, 순수 SSE
fan-out 부하테스트(#569)에서 실측해보니 **캡을 걸수록 유저 체감 배달 지연(`auction_sse_delivery_latency`)이
오히려 5~9배 나빠지는 현상**을 확인함(무제한 avg 1,527.7ms → cap 50~10000 전 구간 7,600~13,500ms대,
QPS130/구독자500/경매15, 2회 반복으로 재현 확인됨).

## 원인

- `AuctionSseConnectionManager.broadcast()`의 `emitters.forEach(emitter -> sendDispatcher.dispatch(...))`
  루프가 `PerConnectionSseSendDispatcher.dispatch()`(`executor.execute(sendTask)`만 호출)를 그대로 부른다.
- `VirtualThreadSseTaskExecutor.execute()`는 캡이 걸려있으면 `concurrencyLimiter.acquire()`를
  **`execute()`를 호출한 스레드(=broadcast() 자신)에서 블로킹**한다.
- 즉 broadcast()가 자기 전용 executor(`auctionSseBroadcastTaskExecutor`)에 제출되는 순간 자체는
  안 막히지만, 그 안에서 emitter를 순회하는 루프가 send용 executor(`auctionSseTaskExecutor`) 캡에
  하나씩 걸려서 **결국 broadcast() 완료(=전원 배달) 자체가 캡만큼 여러 번 대기하며 늘어진다.**

## 변경 사항

**`VirtualThreadSseTaskExecutor.execute()`**: `concurrencyLimiter.acquire()`를 호출자 스레드가 아니라
`super.execute()`가 새로 띄우는 가상스레드 **안에서** 하도록 이동. `SimpleAsyncTaskExecutor.execute()`
(가상스레드 모드)는 태스크마다 새 가상스레드를 띄우고 즉시 리턴하는 방식이라, acquire()를 그 안으로
옮기면:
- `execute()` 호출자(=broadcast() 루프)는 캡 상태와 무관하게 항상 즉시 리턴 — 더 이상 안 막힘.
- 실제 동시 실행 개수는 여전히 세마포어로 제한됨 — 새로 뜬 가상스레드가 permit을 기다리며 대기하는
  것뿐이라, "동시성 캡"이라는 기능 자체는 그대로 유지.
- `submitted`/`active`/`completed`/`failures`/`taskDuration` 계측 지점도 acquire() 이후로 옮겨서,
  "제출"과 "실제 실행 시작"의 의미가 이전과 동일하게 유지되도록 함(active는 이제 acquire 성공
  이후부터 세므로 오히려 더 정확해짐).

## 범위 밖

- `AuctionSseConnectionManager`/`PerConnectionSseSendDispatcher`/executor 설정(bean 이름, 기본
  캡 값 등)은 안 건드림 — `VirtualThreadSseTaskExecutor` 내부 구현만 수정.
- notification 쪽(`notification-fanout` executor)도 같은 클래스를 쓰므로 자동으로 같은 효과를
  받지만, 이번 실측(#569)은 auction 채널 위주라 notification 쪽 개선 효과는 별도 검증 안 함.

## 완료 기준

- [ ] `VirtualThreadSseTaskExecutor` 관련 기존 테스트 통과
- [ ] `./gradlew compileJava compileTestJava` 통과
- [ ] (가능하면) `~/dbidding-loadtest-local`에서 같은 조건(QPS130/500명/15경매, cap=100)으로
      재실행해 `auction_sse_delivery_latency`가 무제한 수준(1,500ms대)에 가까워지는지 확인

> 이 문서는 claude의 도움을 받아 작성되었습니다.
