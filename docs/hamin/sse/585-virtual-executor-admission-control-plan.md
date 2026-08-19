# VirtualThreadSseTaskExecutor 캡: 힙 고갈 막는 진짜 admission control로 변경

이슈: #585

## 배경

`VirtualThreadSseTaskExecutor`의 동시성 캡(`AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY`)은 #575에서
`concurrencyLimiter.acquire()`를 `execute()` 호출자가 아니라 새로 띄운 가상스레드 안에서
하도록 옮겼다 — broadcast() 순회 루프가 캡 때문에 안 막히게 하려는 의도였다.

그런데 이 설계는 캡이 걸려있어도 task가 제출되는 즉시 가상스레드 객체(+힙에 상주하는
StackChunk)가 먼저 만들어지고, 그 다음에야 세마포어를 기다리는 구조다. JDK 가상스레드는
콜스택을 플랫폼 스레드처럼 네이티브 메모리가 아니라 힙에 저장하므로, 캡은 "동시에 몇 개가
실행되냐"만 제한하고 "몇 개가 대기 상태로 살아있냐"는 전혀 제한하지 않는다.

순수 SSE fan-out 부하테스트(#569 실험 B)에서 `SSE_SUBSCRIBERS=2000`(delay=5ms, QPS=20)로
가상스레드 프로필을 돌리자 힙 OOM으로 실제 크래시했고, `AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY=100`
캡을 걸어 재현해도 여전히 크래시했다 — 상세 분석은 이슈 #585 본문 참고.

## 작업 내용

1. `VirtualThreadSseTaskExecutor.execute()`: `Semaphore.acquire()`(블로킹, 새 가상스레드 안)
   대신 `Semaphore.tryAcquire()`(논블로킹, 호출자 스레드)로 먼저 확인한다.
   - permit 획득 성공 → 기존과 동일하게 가상스레드를 띄운다.
   - permit 획득 실패 → 가상스레드를 아예 만들지 않고 그 자리에서 task를 버린다(discard).
     `CountingDiscardPolicy`/`CountingCallerRunsPolicy`와 같은 메트릭 이름
     (`dbidding.sse.broadcast.saturated`, tag `executor`)을 재사용해 기존 "CallerRuns
     발생률(포화)" 대시보드 패널이 이 discard도 함께 잡게 한다.
   - 두 경로 다 `execute()` 호출자는 즉시 리턴 — #575의 "broadcast() 순회 루프가 안 막힌다"는
     목표를 그대로 유지하면서, "동시에 살아있는(대기 포함) 가상스레드 개수"가 permit 개수로
     정확히 상한이 걸려 힙 고갈을 막는다.
2. `AuctionSseConnectionManager.broadcast()`: 캡이 걸린 상태에서 구독자 수가 캡보다 훨씬 많으면,
   `emitters`(순회 순서가 사실상 구독 순서로 고정된 `CopyOnWriteArraySet`)를 매번 같은 순서로
   순회해 앞쪽 emitter만 permit을 따고 뒤쪽은 계속 discard되는 편향(기아)이 생긴다. `eventId`를
   회전 오프셋으로 써서 매 이벤트마다 순회 시작 위치를 돌려 장기적으로 공평하게 만든다.
3. `VirtualThreadSseTaskExecutorTest`의 기존 "캡이 꽉 차면 대기했다가 나중에 실행된다"는
   전제의 테스트를 "캡이 꽉 차면 즉시 discard되고 재시도 없이 영영 실행 안 된다"는 새 동작에
   맞게 수정, discard 시 가상스레드 자체가 안 만들어지는지(`submitted` 카운터로 확인)와
   `dbidding.sse.broadcast.saturated` 메트릭이 오르는지 검증하는 테스트를 추가한다.

## 필요 캡 값 산정 참고 (Little's Law)

`필요 캡 ≈ 구독자 수 × EVENT_QPS × delay(초)` — 이슈 #585 본문 "필요 캡 값 산정" 섹션 참고.
이번 작업 범위는 admission control 메커니즘 자체를 고치는 것이고, 실제 운영 캡 값 튜닝은
범위 밖.

## 범위 밖

- `notificationFanOutTaskExecutor`/`NotificationSseConnectionManager` 쪽도 같은
  `VirtualThreadSseTaskExecutor`를 쓰므로 이번 execute() 수정은 자동으로 적용되지만,
  notification의 fan-out 순회(유저 단위, `push()`)에는 이번 emitter 순회 순서 회전을
  별도로 넣지 않는다 — auction처럼 이벤트 1건이 구독자 수천 명에게 동시에 나가는 대규모
  broadcast 패턴이 아니라(유저 1명당 커넥션 1~2개), 이 편향이 실질적으로 드러나지 않는다.
- 부하테스트 재실행(캡=200 등 Little's Law 계산값으로 재현)은 로컬 loadtest 스택에서 별도
  진행 — 이 커밋 범위에는 포함하지 않음.

## 완료 기준

- [x] `VirtualThreadSseTaskExecutorTest` 신규/수정 테스트 통과
- [x] `AuctionSseContractTest` 등 `AuctionSseConnectionManager` 관련 기존 테스트 통과
- [x] `./gradlew compileJava compileTestJava` 통과

> 이 문서는 claude의 도움을 받아 작성되었습니다.
