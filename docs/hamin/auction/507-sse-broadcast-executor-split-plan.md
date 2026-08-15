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
2. `AuctionSseConnectionManager.broadcast()`의 `@Async` 값을 `"auctionSseTaskExecutor"` → `"auctionSseBroadcastTaskExecutor"`로 변경. `heartbeat()`와 `sendDispatcher`(개별 send)는 그대로 `auctionSseTaskExecutor`를 쓴다.
3. `broadcast()` 안에서 `SseEventBuilder`를 `emitters.forEach` 시작 전 한 번만 생성해 재사용한다.
4. `AuctionStreamRedisSubscriber`의 stale 주석(예전 단일 executor 설명)을 갱신한다.

## 검증

- `AuctionSseContractTest`: `broadcast()`/`heartbeat()` executor 이름 분리 검증(테스트 분리), 공유 `SseEventBuilder` 재사용으로 빌드 호출 1회로 감소한 것 반영
- `AuctionSseExecutorProfileTest`: 두 빈이 프로필과 무관하게 항상 별개 인스턴스인지 검증
- `AuctionSseExecutorConfigTest`: 기본 프로필에서 heartbeat용 executor가 포화돼도 broadcast용 executor는 영향받지 않는지, `sse-virtual-threads`에서 send용 executor가 캡으로 포화돼도 broadcast용 executor는 즉시 task를 받는지 검증
- `./gradlew test --tests "com.dbidding.auction.sse.*"` 통과
