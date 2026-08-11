# Notification/Auction SSE fan-out TaskExecutor를 가상 스레드로 전환

담당: 임하민. 이슈 [#362](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/362)
(브랜치 `feature/362-sse-fanout-virtual-threads`). 선행조건: [#305](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/305)
(notification origin/subscriber TaskExecutor 분리, 완료됨).

## 배경

#362 이슈 분석 결과, subscriber fan-out 계열(notification의 `notificationFanOutTaskExecutor`,
auction의 `auctionSseTaskExecutor`)은 공유 자원(DB 등) 없이 순수 네트워크 send만 하는
워크로드라 가상 스레드로 바꿀 실질적 이득이 있다. 다만 세분화(유저/커넥션 1개당 독립
task)와 가상 스레드 전환(캡 제거)은 반드시 같은 PR에서 함께 반영해야 한다 — 지금의
bounded pool(core 4/max 8/queue 2000)에 세분화만 얹으면 실제 유저·커넥션 수가 상한을
넘기는 순간 `CountingCallerRunsPolicy`가 task를 제출한 스레드(Redis 구독 스레드 등)에서
동기 실행시켜버려 그 인스턴스의 메시지 처리 전체가 느린 클라이언트에 막히는 회귀가 생긴다.

**auction.sse 포함 여부**: 이슈가 `auction.sse`(`AuctionSseConnectionManager`,
`auctionSseTaskExecutor`)도 명시적으로 다루고 있음. `auction`은 이은기님 담당 패키지라
AGENTS.md 스코프 규칙상 별도 허가가 필요한 영역인데, 이번 작업에 포함하기로 확인받았다.

**선행조건 확인**: #305는 이미 코드에 반영되어 있다 —
`NotificationExecutorConfig`에 `notificationTaskExecutor`(origin)/`notificationFanOutTaskExecutor`
(subscriber) 두 빈이 분리돼 있고, `NotificationPushRedisSubscriber.onMessage`/
`LocalNotificationPushPublisher`(class-level)/`NotificationSseConnectionManager.heartbeat()`가
모두 `notificationFanOutTaskExecutor`에 바인딩돼 있다. 착수 가능.

## 설계

### 프로필로 신/구 동작을 병존시킨다

`local-sse`/`redis`와 동일한 프로필 배타 패턴을 그대로 따라, 신규 프로필
`sse-virtual-threads`를 추가한다.

- **기본값(프로필 미지정)**: 오늘 동작을 한 글자도 안 바꾼다 — 배치 단위 `@Async`
  + bounded `ThreadPoolTaskExecutor`(core 4/max 8/queue 2000) 그대로. 비교/롤백용
  baseline으로 유지하는 게 목적이라, 여기서 세분화만 얹거나 풀 사이징을 재산정하지
  않는다(그러면 위에서 설명한 회귀 상태 자체가 baseline이 되어버린다).
- **`sse-virtual-threads`**: executor를 가상 스레드로 바꾸고, 세분화(유저/커넥션
  1개당 독립 task)도 같은 프로필에서 함께 켠다.

프로필 하나로 "executor 구현체"와 "async 경계 위치(배치 단위 vs 개별 단위)"를
같이 스위치해야 하는데, `@Async` 애노테이션 자체는 프로필 조건부로 걸 수 없다.
그래서 배치/개별 여부를 **디스패처 전략 인터페이스**로 뽑아 프로필별 구현체를
갈아 끼운다 — `NotificationPushPublisher`(`Local`/`Redis`)와 같은, 이 코드베이스에
이미 있는 패턴이다.

### executor 빈 — 같은 빈 이름, 프로필별 구현체 교체

```java
// NotificationExecutorConfig
@Bean(name = "notificationFanOutTaskExecutor")
@Profile("!sse-virtual-threads")
public TaskExecutor notificationFanOutTaskExecutor() { /* 기존 ThreadPoolTaskExecutor, 변경 없음 */ }

@Bean(name = "notificationFanOutTaskExecutor")
@Profile("sse-virtual-threads")
public TaskExecutor notificationFanOutVirtualTaskExecutor() {
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("notification-fanout-");
    executor.setVirtualThreads(true);
    return executor;
}
```

`AuctionSseExecutorConfig`의 `auctionSseTaskExecutor`도 동일하게 나눈다. 빈 이름을
유지하기 때문에 `heartbeat()`처럼 이번에 세분화 대상이 아닌 메서드도 `@Async("...")`
참조를 안 건드리고 프로필에 따라 자동으로 가상 스레드를 타게 된다.

`TaskExecutorAdapter`로 `Executors.newVirtualThreadPerTaskExecutor()`를 감싸는
방식도 가능하지만, Spring Boot 3.2+/4.x의 표준 패턴인 `SimpleAsyncTaskExecutor` +
`setVirtualThreads(true)`가 더 idiomatic해서 이걸 채택한다(이 코드베이스에 가상 스레드
executor를 `@Async`용 Bean으로 wiring한 선례가 없어 새로 정하는 것).

### notification — `push()`에 디스패처를 통해서만 진입

```java
public interface NotificationPushDispatcher {
    void dispatch(Integer userId, NotificationResponse payload);
}

@Component
@Profile("!sse-virtual-threads")
@RequiredArgsConstructor
public class SynchronousNotificationPushDispatcher implements NotificationPushDispatcher {
    private final NotificationSseConnectionManager connectionManager;
    public void dispatch(Integer userId, NotificationResponse payload) {
        connectionManager.push(userId, payload); // 오늘과 동일: 호출 스레드에서 그대로
    }
}

@Component
@Profile("sse-virtual-threads")
@RequiredArgsConstructor
public class VirtualThreadNotificationPushDispatcher implements NotificationPushDispatcher {
    private final NotificationSseConnectionManager connectionManager;
    @Async("notificationFanOutTaskExecutor")
    public void dispatch(Integer userId, NotificationResponse payload) {
        connectionManager.push(userId, payload); // 유저 1명당 독립 task
    }
}
```

`NotificationPushRedisSubscriber.onMessage`/`LocalNotificationPushPublisher.publish()`는
`connectionManager.push(...)`를 직접 호출하던 걸 `pushDispatcher.dispatch(...)`로
바꾸고, 기존에 붙어있던 `@Async("notificationFanOutTaskExecutor")`는 제거한다(async
여부는 이제 디스패처가 프로필에 따라 결정). 기본 프로필에서는 이 두 진입점이
호출 스레드(Redis 구독 스레드/origin async 컨텍스트)에서 동기로 유저 N명을 순차
처리하는 오늘 동작 그대로다.

### auction — `broadcast()` 내부 emitter 순회에 동일한 디스패처를 쓴다

notification과 달리 auction은 "여러 유저를 순회하며 개별 엔티티를 호출하는 바깥
루프"가 없고, `broadcast()` 자신이 전역 emitter 집합을 순회하는 게 곧 세분화
대상이다. `send()`가 private 메서드라 `@Async`를 직접 못 붙이므로, 같은 모양의
디스패처를 `Runnable` 기반으로 하나 더 둔다.

```java
public interface AuctionSseSendDispatcher {
    void dispatch(Runnable sendTask);
}

@Component
@Profile("!sse-virtual-threads")
public class SynchronousAuctionSseSendDispatcher implements AuctionSseSendDispatcher {
    public void dispatch(Runnable sendTask) { sendTask.run(); }
}

@Component
@Profile("sse-virtual-threads")
@RequiredArgsConstructor
public class PerConnectionAuctionSseSendDispatcher implements AuctionSseSendDispatcher {
    private final TaskExecutor auctionSseTaskExecutor;
    public void dispatch(Runnable sendTask) { auctionSseTaskExecutor.execute(sendTask); }
}
```

`AuctionSseConnectionManager.broadcast()`는 그대로 `@Async("auctionSseTaskExecutor")`를
유지하되(이벤트 하나 전체를 여전히 executor로 넘기는 바깥 경계는 안 바뀜), 내부
`emitters.forEach(emitter -> send(...))`를
`emitters.forEach(emitter -> sendDispatcher.dispatch(() -> send(...)))`로 바꾼다.
`heartbeat()`는 이번 세분화 대상이 아니다(이슈 할 일에도 없음) — executor 자체가
프로필에 따라 가상 스레드로 바뀌는 이득만 그대로 받는다.

## 열린 질문 (구현 착수 전 확인 필요)

1. **서킷브레이커용 상한**: 가상 스레드는 무제한이라, 버그/폭주 대비 상한을 별도로
   둘지(예: `Semaphore`) — 이슈 본문은 "권장"만 하고 구체적 값/구현이 없다. 이번 PR
   범위에 넣을지, 넣는다면 상한 값을 뭘로 할지 정해야 한다.
2. **관찰성 대체**: `CountingCallerRunsPolicy`가 남기던 `event=...executor.saturated`
   로그가 `sse-virtual-threads` 프로필에서는 없어진다. 대체 지표(활성 가상 스레드 수,
   세마포어 대기 등)를 이번 PR에서 넣을지 후속으로 미룰지.
3. **k6 부하 테스트 범위**: 이슈 할 일에 "세분화 전/후 비교 실측"이 있는데, 이번 PR에
   포함할지 별도 검증 단계로 뺄지. (추천: 코드+테스트까지 이번 PR, k6 비교는 후속)

## 작업 항목

- [ ] `NotificationExecutorConfig`의 `notificationFanOutTaskExecutor`를 프로필 분기
      (기존 `ThreadPoolTaskExecutor` / 신규 `SimpleAsyncTaskExecutor` 가상 스레드)
- [ ] `AuctionSseExecutorConfig`의 `auctionSseTaskExecutor` 동일하게 프로필 분기
- [ ] `NotificationPushDispatcher` 인터페이스 + `Synchronous`/`VirtualThread` 구현체 추가
- [ ] `NotificationPushRedisSubscriber.onMessage`/`LocalNotificationPushPublisher.publish()`가
      `connectionManager.push()` 대신 `pushDispatcher.dispatch()` 경유하도록 변경,
      기존 `@Async` 제거
- [ ] `AuctionSseSendDispatcher` 인터페이스 + `Synchronous`/`PerConnection` 구현체 추가
- [ ] `AuctionSseConnectionManager.broadcast()`의 `emitters.forEach`가 `sendDispatcher`
      경유하도록 변경
- [ ] 위 열린 질문 1~3 결론 반영
- [ ] 기존 테스트 갱신: `NotificationExecutorConfigTest`(fanout 포화 테스트는 기본
      프로필에서만 유효 — 프로필별 분기 또는 조건부 처리 필요),
      `AuctionSseExecutorConfigTest`, `LocalNotificationPushPublisherAsyncWiringVerifyTest`
- [ ] 신규 프로필 전환 테스트: `sse-virtual-threads` on/off 시 각 인터페이스에 정확히
      하나의 구현체만 등록되는지 `ApplicationContextRunner` 기반 테스트
      (`NotificationPushPublisherProfileTest` 패턴 참고)
- [ ] (열린 질문 3의 결론에 따라) k6 부하 테스트로 세분화 전/후 꼬리 지연·격리 개선 실측

## 범위 밖

- Executor 1(origin, `notificationTaskExecutor`) 전환 — 별도 `Semaphore` bulkhead
  설계가 먼저 필요, 이번 이슈에서 다루지 않음.
- 기본(`!sse-virtual-threads`) 프로필의 풀 사이징(core/max/queue capacity) 재산정 —
  baseline 성격을 유지하기 위해 오늘 값 그대로 둔다.
- `heartbeat()`(양쪽)의 추가 세분화 — 이슈 할 일에 없음.

> 이 문서는 Claude의 도움을 받아 작성하였습니다.
