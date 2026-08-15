# 경매 SSE 선택구독 — emitter 동시 send로 인한 연결 대량 붕괴 수정

**관련 이슈:** #520 (원인 도입: #390 경매 SSE 선택 구독, `SseEmitterRegistry` 공통화: #508)

**배경:** 9차 부하테스트(pure-throughput, SSE 500 동시연결) 진행 중 경매 SSE
연결 수가 501까지 정상 도달한 뒤 몇 분 지나 급격히 붕괴하는 현상을 조사하다
발견했다.

---

## 1. 증상

| 경과(UTC) | 연결 수 | send_failures_total(누적, rate) |
|---|---:|---:|
| 램프업 완료 | 501 | ~0 |
| +2~3분 | 461→381→221 | 92→99→123 |
| +4분 | 126→40 | 157→167(피크) |
| +5분 | **0** | 이후 감소(연결 다 끊겨서) |

CPU는 75~92%를 왕복(99%에 안 박힘), HikariCP active 1~7·pending 0 — 과거
3~5차 문서가 기록한 "브로드캐스트 전원방송 → CPU 포화 → nginx 60초 타임아웃"
패턴과 근본적으로 다르다. 이번엔 CPU/DB 어느 쪽도 병목이 아닌데 연결이 죽었다.

## 2. 원인

`#390`(경매 SSE 선택 구독)으로 [`SseEmitterRegistry`](../../../backend/src/main/java/com/dbidding/sse/SseEmitterRegistry.java)가
emitter(연결) 1개당 여러 키(경매ID, 최대 15개)를 동시 구독할 수 있게
일반화됐다(`Set<K>` — Auction은 다중, Notification/Wallet은 `Set.of(userId)`
단일).

[`AuctionSseConnectionManager.broadcast()`](../../../backend/src/main/java/com/dbidding/auction/sse/AuctionSseConnectionManager.java)는
**경매 하나당 한 번씩** `@Async`로 호출되고, 그 경매의 구독자 각각에 대해
`sendDispatcher.dispatch(() -> registry.send(emitter, sharedEvent))`로
**독립 task를 executor에 던진다**:

```java
@Async("auctionSseBroadcastTaskExecutor")
public void broadcast(AuctionStreamPayload event) {
    Set<SseEmitter> emitters = registry.emittersFor(event.auctionId());
    ...
    emitters.forEach(emitter -> sendDispatcher.dispatch(() -> registry.send(emitter, sharedEvent)));
}
```

`PerConnectionSseSendDispatcher.dispatch()`는 그냥 `executor.execute(sendTask)`
— **emitter가 이미 다른 send 작업 중인지 확인하지 않고, 잠금도 없다.**

문제: 한 emitter(연결 하나)가 최대 15개 경매를 동시에 구독한다. 500명이
각자 15개씩 구독하면 300~500개 경매 풀에 겹침이 심하게 남는다. **같은
emitter가 구독한 서로 다른 두 경매에서 거의 동시에 입찰이 들어오면,
`broadcast()`가 경매별로 독립 실행되면서 같은 emitter에 대해
`emitter.send()`가 두 스레드에서 동시에 호출된다.**

`SseEmitter`/`ResponseBodyEmitter`는 concurrent send를 지원하지 않는다
(Spring 문서에 명시된 제약). 동시 호출되면 `IllegalStateException`이 나고,
[`SseEmitterRegistry.send()`](../../../backend/src/main/java/com/dbidding/sse/SseEmitterRegistry.java)가
이를 잡아 `send_failure`로 기록하고 그 연결을 완전히 끊는다
(`removeAndComplete`).

연결 수(501)와 쓰기 QPS(56~80/s)가 오르면서 "같은 emitter가 구독한 두
경매가 동시에 이벤트를 쏘는" 확률이 점점 올라간다 → 실패율이 92→167/s로
눈덩이처럼 불어남 → 실패마다 연결이 하나씩 영구히 끊김 → 501→0 붕괴.

### 왜 8차 이전엔 안 보였나

| 시점 | 커밋 | 의미 |
|---|---|---|
| 8/7 | `31319f8e` 경매 SSE 전송 전역 lock 제거 | 성능 때문에 제거 — 이때는 emitter가 단일 토픽("전체")만 구독해서 문제가 드러나지 않음 |
| 8/12 | PR #399 선택구독 머지 | 8차(8/13) 테스트 이전에 이미 merge된 상태 — 잠재 위험은 이미 있었음 |
| 8/14 | `5e449221` SSE 종료원인/실패 메트릭 추가 | 8차 이후 추가 — 8차 시점엔 이 문제를 볼 지표 자체가 없었음 |
| 8/15(당일) | `0d149748`/`952fb5b6`/`466429ef` broadcast/send executor 분리 + 공통화 리팩터 | 이 세 커밋으로 broadcast와 send가 별도 executor로 쪼개지며 실제 동시성이 늘어 충돌 확률이 더 올라간 것으로 보임 |

즉 잠재 위험은 선택구독 도입(8/12) 시점부터 있었지만, 감지할 메트릭이
없었고(8/14에야 추가) 오늘(8/15) executor 분리 리팩터로 진짜 동시성이
늘면서 처음으로 명확하게 터졌다.

## 3. 수정

`SseEmitterRegistry.send()`에서 emitter 단위로 send를 직렬화한다:

```java
private final ConcurrentMap<SseEmitter, ReentrantLock> sendLocksByEmitter = new ConcurrentHashMap<>();

public boolean send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
    Timer.Sample sample = metrics.startSend();
    ReentrantLock sendLock = sendLocksByEmitter.computeIfAbsent(emitter, ignored -> new ReentrantLock());
    sendLock.lock();
    try {
        emitter.send(event);
    } catch (IOException | IllegalStateException exception) {
        metrics.recordSendFailure();
        metrics.recordConnectionClosed(emitter, CloseReason.SEND_FAILURE);
        removeAndComplete(emitter);
        return false;
    } finally {
        sendLock.unlock();
        metrics.finishSend(sample);
    }
    return true;
}
```

`remove(emitter)`에서 `sendLocksByEmitter.remove(emitter)`로 락 맵도 같이
정리해 누수를 막는다.

### `synchronized` 대신 `ReentrantLock`을 쓰는 이유

이 executor들은 가상스레드(`thread_type=virtual`, 메트릭으로 확인)를 쓴다.
JDK 21(JEP 491 이전)에서는 `synchronized` 블록 안에서 블로킹되면
가상스레드가 캐리어(OS) 스레드에 pinning된다 — 충돌 나서 락 대기하는
가상스레드가 캐리어를 계속 붙잡으면 캐리어 풀 자체가 줄어드는 효과라,
가상스레드를 쓰는 이유 자체가 무력화된다. `ReentrantLock`은 이 pinning이
없다.

### 지연 트레이드오프

락은 "같은 emitter에 동시에 2개 이상 이벤트가 몰릴 때"만 걸리고 emitter별로
독립적이라 다른 연결끼리는 그대로 병렬이다. 지금은 그 충돌이 나면 연결이
통째로 죽고 재접속(TLS+쿠키+재구독) 비용이 드는데, 락으로 막으면 send
하나가 마이크로초~수ms 대기하는 정도로 끝난다 — 트레이드가 명백히 남는다.

## 4. 검증

### 회귀 테스트

[`SseEmitterRegistryTest`](../../../backend/src/test/java/com/dbidding/sse/SseEmitterRegistryTest.java)에
`서로_다른_키의_send가_같은_emitter에_동시에_들어와도_직렬화되어_충돌하지_않는다`
추가 — mock emitter의 send 호출 중 `AtomicBoolean`으로 동시 진입 여부를
직접 검증한다. 두 스레드에서 동시에 `registry.send()`를 호출해도 overlap이
감지되지 않아야 통과.

### 로컬 재현

로컬(mysql8 + dbidding-redis 컨테이너, `PASSWORD_HASH_ITERATIONS=100`,
`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30`으로 prod 정합)에서 동일
시나리오(`pure-throughput.js`, `SSE_VUS=500`) 재현:

| | 수정 전(prod 실측) | 수정 후(로컬) |
|---|---|---|
| 연결 수 추이 | 501→461→381→221→126→40→**0** | **500 유지**(85샘플, 2초 간격, ~7.5분 전체) |
| `dbidding_auction_sse_send_failures_total` | 92→167/s로 폭증 | **전 구간 0** |

## 5. 한계

- 로컬 재현은 prod와 하드웨어가 다르다(Apple Silicon vs t4g.micro) —
  타이밍 자체가 아니라 "충돌이 실제로 나는지/락으로 막히는지" 패턴만
  비교 대상으로 삼았다.
- `removeAndComplete()`(타임아웃/에러 콜백에서도 호출됨)가 `send()`와
  별도 스레드에서 동시에 `emitter.complete()`를 부를 가능성은 이번에
  안 건드렸다 — 기존 코드가 `IllegalStateException`을 이미 방어적으로
  잡고 있어 급한 문제는 아니라고 판단했다.

> 이 문서는 Claude의 도움을 받아 작성하였습니다
