# AuctionSseConnectionManager.broadcast()를 send와 별도 executor로 분리 (계획)

refs #507

## 배경

세마포어 캡 실험(#495/#496) 중 발견: `AuctionSseConnectionManager.broadcast()`가 `@Async("auctionSseTaskExecutor")`라서, emitter별 실제 전송(`send()`, `sendDispatcher.dispatch(...)`를 통해 같은 executor로 위임)과 **같은 캡(세마포어/스레드풀) 예산을 나눠 쓴다.**

- 순회(coordination) 자체는 가벼운 작업인데도 send용 캡을 갉아먹는다.
- 캡이 꽉 차면 `broadcast()`의 **호출자 스레드**가 그대로 블로킹된다.
  - `redis` 프로필: `AuctionStreamRedisSubscriber.onMessage()`(Redis pub/sub 메시지마다 생기는 플랫폼 스레드) — 캡이 계속 꽉 차 있으면 블로킹된 플랫폼 스레드가 쌓인다.
  - `local-sse` 프로필: `LocalAuctionStreamPublisher.publish()`가 `AuctionCommandService.publishBidEvents()`에서 **입찰 POST 요청을 처리하던 Tomcat 스레드 자체**로 동기 호출됨 — 캡을 걸수록 입찰 응답이 느려지는 역효과.

추가로 `broadcast()` 안에서 `event(...)`(`SseEventBuilder`)를 emitter마다 반복 생성하는데, 인자 3개가 매번 동일하다 — 한 번만 만들어 재사용해도 안전하다(`.build()` 결과는 읽기 전용, `serializedPayload`를 이미 이렇게 재사용 중인 것과 동일한 패턴).

**가상 스레드 사용 여부와 무관하게 분리한다.** 기본(`!sse-virtual-threads`) 프로필은 `SynchronousAuctionSseSendDispatcher`가 send를 broadcast() 호출 스레드에서 바로 실행하므로(#362) 세마포어는 없지만, 여전히 **bounded `ThreadPoolTaskExecutor` + `CountingCallerRunsPolicy`** 조합이 있다 — 느린 클라이언트 여러 개가 이 pool과 queue를 다 채우면 캡이 꽉 찬 것과 동일하게 `broadcast()` 호출자(Redis pub/sub 스레드 또는 입찰 처리 Tomcat 스레드) 쪽에서 caller-runs가 발동할 수 있다. 그래서 프로필과 무관하게 항상 heartbeat/send와 별개의 executor를 쓴다.

## 변경 내용

1. `AuctionSseExecutorConfig`에 `broadcast()` 전용 새 빈 `auctionSseBroadcastTaskExecutor`를 프로필과 무관하게 항상 별개 인스턴스로 추가한다.
   - `sse-virtual-threads` 프로필: `VirtualThreadSseTaskExecutor`의 3-인자 생성자(캡 없음).
   - 기본 프로필: 별도의 bounded `ThreadPoolTaskExecutor`(`AUCTION_SSE_BROADCAST_CORE_POOL_SIZE`/`MAX_POOL_SIZE`/`QUEUE_CAPACITY`, 기본값은 기존 공유 pool과 동일한 4/8/2000 — 이 프로필에서 이 pool이 실제 send I/O까지 떠안으므로 줄이면 안 된다).
2. `AuctionSseConnectionManager.broadcast()`의 `@Async` 값을 `"auctionSseTaskExecutor"` → `"auctionSseBroadcastTaskExecutor"`로 변경. `heartbeat()`는 그대로 `auctionSseTaskExecutor`를 쓴다.
3. `broadcast()` 안에서 `SseEventBuilder`를 `emitters.forEach` 시작 전 한 번만 생성해 재사용한다.
4. `AuctionStreamRedisSubscriber`의 stale 주석(예전 단일 executor 설명)을 갱신한다.

## 이번 이슈에서 안 바꾼 것 — 기본 프로필의 emitter별 send는 여전히 broadcast()와 같은 스레드에서 처리된다

`AuctionSseSendDispatcher`는 프로필별로 구현이 다르다:

- `sse-virtual-threads`: `PerConnectionAuctionSseSendDispatcher`가 emitter마다 `auctionSseTaskExecutor.execute(sendTask)`로 **독립적인 task**를 만들어 위임한다 — 커넥션 1개당 스레드가 사실상 분리된다(#362).
- 기본(`!sse-virtual-threads`) 프로필: `SynchronousAuctionSseSendDispatcher.dispatch()`는 `sendTask.run()`으로 **그 자리에서 바로** 실행한다. 즉 `broadcast()`를 실행 중인 `auctionSseBroadcastTaskExecutor` 스레드가 emitter 순회와 각 emitter의 실제 `send()`를 **전부 한 스레드에서 순차 처리**한다 — `auctionSseTaskExecutor`(send/heartbeat용 pool)는 이 경로에서 전혀 안 쓰인다.

**왜 기본 프로필도 emitter별로 분리하지 않았는가**: `sse-virtual-threads`로 전환하지 않는 한, "커넥션 1개당 독립 task"는 결국 core 4/max 8의 작은 bounded **플랫폼 스레드** pool로 task가 몰린다는 뜻이다. 플랫폼 스레드는 가상 스레드와 달리 느린 클라이언트에 블로킹돼도 그 스레드(귀한 8개 중 하나)를 실제로 붙잡는다 — 가상 스레드처럼 park가 사실상 공짜가 아니다. 이 pool은 heartbeat과도 공유되므로, 여러 경매가 동시에 브로드캐스트하면서 쏟아내는 send task가 쌓이면 heartbeat까지 지연되거나, queue(2000)를 넘겨 `CountingCallerRunsPolicy`가 발동해 결국 broadcast() 호출자(Redis pub/sub 스레드 또는 입찰 처리 Tomcat 스레드) 쪽에서 caller-runs가 일어날 수 있다 — 이번 이슈가 막으려던 바로 그 문제가 다른 경로로 재발하는 셈이다.

이 위험은 #362에서 이미 분석돼 있다: "세분화(유저/커넥션 1개당 독립 task)는 [가상 스레드] 전환과 반드시 같이 한다 ... 세분화만 먼저 넣거나 전환만 먼저 넣는 순서는 둘 다 위험하다." 지금은 브로드캐스트가 경매 구독자로 범위가 좁혀져(#기존 전역 fan-out 대비) 위험의 "규모"는 줄었지만, 인기 경매 하나에 몰리는 동시 구독자 수를 실측하기 전까지는 "이제 안전하다"고 단정할 근거가 없다. 그래서 이번 이슈에서는 **`broadcast()` 자체를 send/heartbeat과 분리하는 것까지만** 하고, emitter별 분리(플랫폼 스레드 확장)는 범위 밖으로 남겨뒀다 — 기본 프로필에서는 같은 broadcast() 호출 안에서 느린 구독자 하나가 다른 구독자의 전송을 지연시킬 수 있다는 트레이드오프를 그대로 받아들인다. 필요해지면 실측 데이터를 먼저 모으고 별도 이슈로 다뤄야 한다.

## 검증

- `AuctionSseContractTest`: `broadcast()`/`heartbeat()` executor 이름 분리 검증(테스트 분리), 공유 `SseEventBuilder` 재사용으로 빌드 호출 1회로 감소한 것 반영
- `AuctionSseExecutorProfileTest`: 두 빈이 프로필과 무관하게 항상 별개 인스턴스인지 검증
- `AuctionSseExecutorConfigTest`: 기본 프로필에서 heartbeat용 executor가 포화돼도 broadcast용 executor는 영향받지 않는지, `sse-virtual-threads`에서 send용 executor가 캡으로 포화돼도 broadcast용 executor는 즉시 task를 받는지 검증
- `./gradlew test --tests "com.dbidding.auction.sse.*"` 통과
