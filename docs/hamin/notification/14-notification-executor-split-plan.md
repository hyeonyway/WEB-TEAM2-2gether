# Notification origin/subscriber TaskExecutor 분리

담당: D(임하민). 이슈 #305.

## 배경

`NotificationExecutorConfig`의 `notificationTaskExecutor` 하나를 성격이 다른 두 워크로드가 같이 쓰고 있다:

- **origin(저장+발행)**: `NotificationEventListener`의 5개 핸들러 — DB 저장(HikariCP 커넥션 소비)이 걸려 있어 동시 실행 상한이 의미 있음.
- **subscriber(로컬 fan-out)**: `NotificationPushRedisSubscriber.onMessage`, `LocalNotificationPushPublisher.publish()`, `NotificationSseConnectionManager.heartbeat()` — DB 접근 없이 로컬 SSE emitter에 순수 네트워크 send만 함.

#281에서 이 분리를 검토했지만 "가상 스레드 전환(#305)을 실제로 할 때 같이 한다"고 미뤄뒀다. 이후 #305/#362 논의 과정에서, 이 분리 자체가 가상 스레드 전환과 무관하게 **격리(bulkhead) 가치**가 있다는 게 드러났다 — 풀 하나(core 4/max 8)를 공유하면 DB 커넥션에 의존하는 origin 작업이 몰릴 때 순수 네트워크 작업인 subscriber fan-out(과 그 반대 방향)도 같이 밀린다. 그래서 "분리"만 이 이슈에서 하고, 가상 스레드 전환은 #362로 옮겼다.

## 설계

### 신설 executor

`NotificationExecutorConfig`에 subscriber fan-out 전용 executor 빈을 하나 더 만든다. 기존 `notificationTaskExecutor`(origin 전용, 변경 없음)와 같은 구조로, 프로퍼티만 별도 이름(`NOTIFICATION_FANOUT_*`)을 쓴다 — 기본값(core 4/max 8/queue 2000)은 기존과 동일하게 둬서, 분리 자체가 동작을 바꾸지 않게 한다(사이징 튜닝은 범위 밖).

```java
@Bean(name = "notificationFanOutTaskExecutor")
public ThreadPoolTaskExecutor notificationFanOutTaskExecutor() { ... }
```

### `@Async` 대상 변경 — 두 진입점 모두, `push()` 레벨로는 아직 안 내린다

- `NotificationPushRedisSubscriber.onMessage`: `@Async("notificationTaskExecutor")` → `@Async("notificationFanOutTaskExecutor")`
- `LocalNotificationPushPublisher.publish(List<NotificationPushMessage>)`: 지금은 애노테이션이 전혀 없어 `local-sse` 프로필에서는 origin(`NotificationEventListener`)의 async 컨텍스트 안에서 그대로 동기 실행됨 — `@Async("notificationFanOutTaskExecutor")`를 새로 붙여서, 프로필과 무관하게 subscriber fan-out 작업이 항상 신설 executor를 타도록 한다.
- `NotificationSseConnectionManager.heartbeat()`: `@Async("notificationTaskExecutor")` → `@Async("notificationFanOutTaskExecutor")`
- `NotificationEventListener`의 5개 핸들러: 변경 없음(기존 `notificationTaskExecutor` 유지)

배치(유저 N명) 하나를 유저 1명당 독립 task로 쪼개는 세분화는 **이번엔 하지 않는다**. `notificationFanOutTaskExecutor`가 여전히 bounded pool(core 4/max 8/queue 2000, 동시 처리 상한 약 2,008)인 상태에서 그렇게 쪼개면, 청크 크기가 큰 fan-out(#207/#289 기준 청크 10,000)이 이 상한을 가볍게 넘긴다. 넘긴 만큼은 `CountingCallerRunsPolicy`가 **task를 제출한 스레드(Redis 구독자의 메시지 처리 스레드)에서 동기로 실행**시켜버려서, 그 인스턴스의 Redis 채널 메시지 처리 전체가 느린 클라이언트 전송에 막히는 새 회귀가 생긴다. 이 세분화는 캡을 없애는 가상 스레드 전환(#362)과 반드시 같은 PR에서 함께 반영한다.

## 작업 항목

- [ ] `NotificationExecutorConfig`에 `notificationFanOutTaskExecutor` 빈 신설(프로퍼티: `NOTIFICATION_FANOUT_CORE_POOL_SIZE`/`NOTIFICATION_FANOUT_MAX_POOL_SIZE`/`NOTIFICATION_FANOUT_QUEUE_CAPACITY`, 기본값 기존과 동일)
- [ ] `NotificationPushRedisSubscriber.onMessage`의 `@Async` 대상 변경
- [ ] `LocalNotificationPushPublisher.publish()`에 `@Async("notificationFanOutTaskExecutor")` 추가
- [ ] `NotificationSseConnectionManager.heartbeat()`의 `@Async` 대상 변경
- [ ] `NotificationExecutorConfigTest`에 신설 executor 빈의 포화/캐치업 동작 테스트 추가(기존 `notificationTaskExecutor` 테스트와 동일한 형태)
- [ ] 관련 클래스들의 Javadoc에서 "분리하지 않는다(#305)"로 남아있는 설명을 갱신

## 범위 밖

- 유저(커넥션) 1개당 독립 async task로 쪼개는 세분화(`push()`/`broadcast()` 레벨) — #362(가상 스레드 전환)와 함께.
- `auction.sse`의 `auctionSseTaskExecutor` — origin 성격 작업이 처음부터 안 섞여 있어 분리가 필요 없음.
- executor 사이징(core/max/queue capacity 값) 튜닝 — 기존 값을 그대로 복제.

> 이 문서는 claude의 도움을 받아 작성하였습니다.
