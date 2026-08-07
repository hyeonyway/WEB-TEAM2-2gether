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

thread name prefix는 `auction-sse-`로 지정해 로그와 스레드 덤프에서 경매 SSE
작업을 구분한다. 큐가 포화되면 `DiscardPolicy`로 신규 작업을 버린다. 이벤트
발행 경로를 막지 않는 대신, 포화 상태에서 일부 실시간 갱신은 클라이언트의 SSE
재연결 또는 다음 payload로 보정될 수 있음을 전제로 한다.

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
