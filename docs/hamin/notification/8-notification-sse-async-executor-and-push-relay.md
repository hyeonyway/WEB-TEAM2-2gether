# NotificationEventListener 전용 Executor 지정 및 SSE push 릴레이 구조 분리

담당: D(임하민). 이슈 #239.

## 배경

`NotificationEventListener`의 5개 `@Async` 메서드는 이름 없는 bare `@Async`를 쓴다. 이 앱은 `@EnableAsync`만 있고 `AsyncConfigurer`로 기본 executor를 지정한 곳이 없는데, `auctionSseTaskExecutor`/`auctionCloseTaskExecutor` 두 개의 `TaskExecutor` 빈이 이미 있어서 Spring이 "유일한 `TaskExecutor` 빈"을 못 찾고, `"taskExecutor"`라는 이름의 빈도 없어서 결국 `SimpleAsyncTaskExecutor`로 폴백한다. 이 executor는 풀이 아니라 호출마다 새 스레드를 만들고 재사용하지 않으며 개수 제한이 없다 — 경매 마감 스케줄러가 여러 경매를 한 배치로 닫을 때처럼 이벤트가 몰리면 스레드가 그만큼 계속 생성된다.

반면 `AuctionSseConnectionManager.broadcast()`/`heartbeat()`는 `@Async("auctionSseTaskExecutor")`로 이름 붙은 bounded pool(core 4/max 8/queue 2000, 포화 시 caller-runs)을 명시적으로 쓴다.

추가로, `NotificationSseConnectionManager.heartbeat()`는 `@Async`가 전혀 없어서 전역 `@Primary` 스케줄러(`AuctionSchedulingConfig.taskScheduler()`, pool size 1) 스레드에서 모든 유저의 모든 emitter에 동기로 send를 돈다. Auction의 heartbeat은 `@Async`로 즉시 손을 떼는 반면, Notification은 연결이 많거나 클라이언트가 느리면 앱에 하나뿐인 그 스케줄러 스레드를 오래 붙잡아 다른 `@Scheduled` 작업까지 지연시킬 수 있다.

별개로, 서버 인스턴스를 다중화(Redis 도입)할 계획이 있다. `NotificationSseConnectionManager`가 emitter를 인스턴스 로컬 메모리에만 들고 있어서, 이벤트를 처리한 인스턴스와 그 유저의 SSE 연결을 들고 있는 인스턴스가 다르면 push가 전달되지 않는다. DB 저장은 이벤트를 처리한 인스턴스에서 그대로 하면 되지만(트랜잭션이 이미 그 인스턴스에 묶여있음), push만큼은 "발행하면 모든 인스턴스가 받아서, 자기가 그 유저의 연결을 들고 있으면 전송"하는 구조로 바꿔야 한다. 지금은 Redis가 없어서 실제 다중 인스턴스 전달을 구현할 수는 없지만, 나중에 갈아끼우기 쉽도록 경계를 지금 만들어둔다.

## 설계

### 1. 전용 Executor

`NotificationExecutorConfig`(신규, `com.dbidding.notification.config`)에 `notificationTaskExecutor` 빈을 추가한다. `AuctionSseExecutorConfig`와 동일한 패턴 — `@Value`로 core/max/queue 크기를 받고(기본값 4/8/2000, 동일하게 시작), 포화 시 경고 로그 후 caller-runs, graceful shutdown.

`NotificationEventListener`의 5개 핸들러(`handleAuctionOpened`/`handleBidPlaced`/`handleAuctionClosed`/`handleOrderCompleted`/`handleOrderCancelled`)는 `@Async` → `@Async("notificationTaskExecutor")`로 바꾼다.

`NotificationSseConnectionManager.heartbeat()`에도 `@Async("notificationTaskExecutor")`를 추가한다 (Auction의 `heartbeat()`과 동일 패턴).

### 2. Push 릴레이 추상화 (Redis 대비)

새 인터페이스:

```java
public interface NotificationPushPublisher {
    void publish(Integer userId, NotificationResponse payload);
}
```

현재 유일한 구현체:

```java
@Component
@RequiredArgsConstructor
public class LocalNotificationPushPublisher implements NotificationPushPublisher {
    private final NotificationSseConnectionManager connectionManager;

    @Override
    public void publish(Integer userId, NotificationResponse payload) {
        connectionManager.push(userId, payload);
    }
}
```

`NotificationEventListener`는 `NotificationSseConnectionManager`를 직접 의존하는 대신 `NotificationPushPublisher`에 의존하도록 바꾸고, `.push(...)` 호출을 `.publish(...)`로 바꾼다. DB 저장(`notificationService.save*`)은 그대로 리스너 안에서 동기로 수행 — 바뀌는 건 push 호출 지점뿐이다.

지금은 인스턴스가 1개뿐이라 "발행 → 모든 인스턴스가 받음"이 로컬 직접 호출과 동일하게 동작한다. 나중에 Redis를 도입하면 `RedisNotificationPushPublisher`(publish 쪽)와 별도 구독 컴포넌트(전달 쪽, `NotificationSseConnectionManager.push()`를 호출)로 교체하면 되고, `NotificationEventListener`/`NotificationSseConnectionManager` 코드는 손대지 않아도 된다. 실제 Redis 연동(직렬화, 채널 설계, origin 인스턴스 이중 발송 방지, 구독 컨테이너의 executor 지정 등)은 #281로 분리했다.

## 테스트

- `NotificationEventListenerTest`: mock 필드를 `NotificationSseConnectionManager` → `NotificationPushPublisher`로 바꾸고, `verify(...).push(...)` → `verify(...).publish(...)`로 갱신.
- `NotificationExecutorConfigTest`(신규): `AuctionSseExecutorConfigTest`와 동일하게 포화 시 caller-runs로 동작하는지 검증.
- `NotificationSseConnectionManagerTest`: 변경 없음 — 순수 단위 테스트라 `new NotificationSseConnectionManager(...)`로 직접 생성하므로 `@Async`는 애초에 효과가 없고(AOP 프록시가 없음), heartbeat 자체를 테스트하는 케이스도 없다.

## 범위 밖으로 남긴 것

- Redis pub/sub 실제 구현(발행자/구독자, 직렬화, 채널 전략, origin 인스턴스 처리, 구독 컨테이너 executor) — #281.
- 배치 발행 시 유저별 `notificationId` 조립 없이 `(auctionId, message)` 기반으로 dedup·공유 payload 전환 — #281 구현에 반영할 세부 설계, #289로 별도 정리.
- `AuctionSseConnectionManager`(전역 브로드캐스트)의 다중 인스턴스 대응 — 같은 문제가 있지만 이번엔 notification 패키지만 다룬다.
- `AuctionSseConnectionManager`/`NotificationSseConnectionManager` 통합 검토 — #239 참고 섹션에 별도로 남겨둠, 인증 경계(익명 허용 vs 로그인 필수)가 달라 단순 통합이 어려움.

## 보류된 고려사항 (이슈화 안 함)

이슈로 만들 만큼 구체적인 실행 항목은 아니지만, 리뷰 논의 중 나온 열린 질문이라 기록해둔다.

- **t4g.micro(2 vCPU, 1GiB RAM) 배포 스펙에 맞춘 executor pool 크기 재검토**: `notificationTaskExecutor`의 core/max/queue(4/8/2000)는 새로 정한 값이 아니라 `AuctionSseExecutorConfig`를 그대로 미러링한 것. vCPU가 2개뿐이라 core 4/max 8이 과할 수 있음. `@Value`로 뽑아놨으니 코드 변경 없이 배포 환경 변수로 튜닝 가능 — 실측 후 필요하면 기본값 자체를 낮추는 것도 검토.
- **가상 스레드(Virtual Thread) 전환 검토**: SSE 전송(`emitter.send()`)은 네트워크 블로킹 I/O라 가상 스레드에 잘 맞는 워크로드일 수 있음(Tomcat 10.1+/JDK 21 조합에서 블로킹 I/O가 가상 스레드 park를 지원). 다만 `notificationTaskExecutor`처럼 이미 작게 bound된 풀에서는 스레드 메모리/컨텍스트 스위칭 이득이 미미하고, CPU(2 vCPU)나 DB 커넥션 풀(10개) 같은 다른 자원 제약은 가상 스레드로도 해결되지 않음 — 필요하면 나중에 별도로 재검토.

> 이 문서는 claude의 도움을 받아 작성하였습니다.
