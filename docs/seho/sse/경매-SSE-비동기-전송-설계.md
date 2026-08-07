# 경매 SSE 비동기 전송 설계

## 목표

경매 상태 변경 이벤트와 주기 heartbeat 전송이 요청 처리 스레드를 점유하지 않도록
경매 SSE 전송을 전용 `@Async` executor에서 처리한다. executor 설정도 범용 `sse`
패키지 대신 실제 소유 도메인인 `auction/sse`에 둔다.

## 구성

```text
AuctionSseEventListener (AFTER_COMMIT)
    -> AuctionSseConnectionManager.broadcast(payload)
        -> @Async("auctionSseTaskExecutor")
            -> 활성 SseEmitter 전체 전송

@Scheduled heartbeat
    -> AuctionSseConnectionManager.heartbeat()
        -> @Async("auctionSseTaskExecutor")
            -> 활성 SseEmitter 전체에 comment 전송
```

`AuctionSseExecutorConfig`는 `com.dbidding.auction.sse.config`에 위치한다.
`auctionSseTaskExecutor` bean은 `ThreadPoolTaskExecutor`로 구성하며, 다음 환경
변수로 전용 전송 풀을 조절한다.

| 환경 변수 | 기본값 | 의미 |
| --- | ---: | --- |
| `AUCTION_SSE_CORE_POOL_SIZE` | 4 | 기본 worker 수 |
| `AUCTION_SSE_MAX_POOL_SIZE` | 8 | 최대 worker 수 |
| `AUCTION_SSE_QUEUE_CAPACITY` | 2000 | 대기 작업 수 |
| `AUCTION_SSE_REPLAY_STATE_CAPACITY` | 1000 | 재연결에 사용할 경매별 최신 상태 수 |

thread name prefix는 `auction-sse-`로 지정해 로그와 스레드 덤프에서 경매 SSE
작업을 구분한다. 큐가 포화되면 거부 handler가 `event=auction.sse.executor.saturated`
warn 로그와 active worker·queue 크기를 남긴 뒤, 호출 스레드에서 해당 작업을 즉시
실행한다. 따라서 executor 포화 때문에 broadcast 전체가 조용히 폐기되지 않는다.

SSE 이벤트에는 애플리케이션 내부의 단조 증가 ID를 부여한다. 재연결용 저장소는
이벤트 이력을 전부 쌓지 않고 `auctionId`별 최신 payload만 유지한다. 따라서 같은
경매에 입찰이 여러 번 발생해도 재연결 클라이언트에는 마지막 현재가·입찰 수·버전만
전달한다. 재연결 클라이언트가 `Last-Event-ID` 헤더를 보내면 해당 ID보다 새로운
경매별 최신 상태를 같은 ID로 보낸다.

저장소의 경매 상태 수가 설정값을 넘어 이전 상태가 제거됐고, 클라이언트 ID가 그
제거 시점보다 오래되면 `replay-reset` 이벤트도 보낸다. 클라이언트는 이 이벤트를
받으면 REST 조회로 전체 경매 상태를 다시 동기화해야 한다. 최초 연결도 REST 조회가
기준이며, SSE는 그 이후의 변경을 보정한다.

프론트는 `EventSource`가 SSE `id`를 기준으로 `Last-Event-ID`를 자동 전송하도록
기본 재연결 동작을 사용한다. `replay-reset` 수신 시에는 경매 목록·대시보드·입찰
context와 입찰 내역·전자지갑 query를 무효화해 활성 화면이 REST API로 최신 상태를
다시 가져오게 한다.

## 브라우저 수동 검증

`test` 프로필에서만 `POST /api/auctions/stream/test-events/disconnect`를 제공한다.
이 API는 현재 SSE emitter를 모두 종료해 브라우저 `EventSource`의 자동 재연결을
재현한다. 연결 종료 직후 3초 재연결 대기 동안
`POST /api/auctions/stream/test-events/random-bid`를 호출하면, 새 stream 요청의
`Last-Event-ID`와 경매별 최신 상태 replay를 Network 탭에서 확인할 수 있다.

replay가 있어도 이벤트 유실을 허용하는 `DiscardPolicy`를 사용하지 않는다. 포화 시
이벤트 발행 경로가 잠시 지연될 수 있지만, 이는 연결된 모든 emitter가 같은 경매
이벤트를 놓치는 것보다 우선한다.
애플리케이션 종료 중 거부된 작업은 새 전송을 보장할 수 없으므로 shutdown 원인을
warn 로그로 남기고, 정상 운영 중 포화와 구분한다.

## 변경 전후

| 항목 | 변경 전 | 변경 후 |
| --- | --- | --- |
| 설정 위치 | `com.dbidding.sse.config` | `com.dbidding.auction.sse.config` |
| 설정 이름 | `SseExecutorConfig` | `AuctionSseExecutorConfig` |
| 실행 방식 | manager가 `Executor.execute()` 호출 | Spring `@Async` proxy가 전용 executor에 위임 |
| executor bean | `sseTaskExecutor` | `auctionSseTaskExecutor` |
| 설정 키 | `SSE_*` | `AUCTION_SSE_*` |

## 전송 실패와 정합성

전송 중 `IOException` 또는 완료된 emitter 접근이 발생하면 기존과 같이 해당
emitter만 제거하고 complete 처리한다. `CopyOnWriteArraySet`을 유지하므로
연결 해제와 비동기 순회가 동시에 발생해도 안전하다.

경매 도메인 이벤트는 `@TransactionalEventListener(AFTER_COMMIT)` 이후에
`broadcast`를 호출한다. 따라서 트랜잭션이 롤백된 상태가 SSE로 먼저 전송되지
않는다. 비동기화는 commit 이후 전달 작업만 분리하며, payload 계약·이벤트 이름·
연결 수명 정책은 바꾸지 않는다.

## 검증

- `AuctionSseContractTest`로 broadcast 실패 emitter 정리와 heartbeat 계약을 검증한다.
- 같은 테스트에서 `broadcast`, `heartbeat`가
  `@Async("auctionSseTaskExecutor")`를 사용하는지 확인한다.
- `AuctionSseControllerTest`로 SSE 응답의 cache·proxy buffering 헤더를 검증한다.
