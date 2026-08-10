# 경매/알림 SSE 이벤트 발행을 Redis Pub/Sub 기반으로 전환

담당: 임하민. 이슈: [#281](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/281)
(브랜치 `feature/281-auction-sse-redis-pubsub`). 관련: [#239](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/239)
(push 릴레이 추상화 도입), [#326](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/326)
(`BidExecutor` 인터페이스 도입), [#305](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/305)
(executor 가상 스레드 전환 검토 — 이번 작업의 선행조건이 아직 없어서 범위 밖).

## 배경

`AuctionSseConnectionManager`/`NotificationSseConnectionManager`는 SSE 연결을 인스턴스
로컬 메모리에만 들고 있다. 이벤트를 처리한 인스턴스와 클라이언트가 SSE로 연결된 인스턴스가
다르면 실시간 전파가 누락된다. 서버 인스턴스를 다중화할 계획이라 이번에 고쳐야 한다.

`#239`에서 이미 `NotificationPushPublisher` 인터페이스(로컬 구현체만 존재)로 대비해뒀고,
`#281`이 "실제 Redis 연동은 나중에"로 분리해둔 후속이었다. 이번 작업으로 그 실제 연동을
구현하면서, 같은 문제를 안고 있던 `auction.sse`(전역 브로드캐스트)까지 같이 처리한다.

추가로 `#326`에서 `DbBidExecutor` 안에 이벤트 발행(`BidPlacedEvent`,
`AuctionClosedEvent`(즉시낙찰), `AuctionCloseScheduleChangedEvent`)이 섞여 있는 게
드러났다. `RedisBidExecutor`(Lua 기반)는 애초에 이런 publish를 Lua 안에 넣을 수 없으므로,
`BidExecutor`가 DB/Redis 두 구현체에서 동일한 계약을 가지려면 이벤트 발행은 항상
`AuctionCommandService`가 담당해야 한다. 이번에 같이 정리한다.

## 목표

1. `BidExecutor`(`DbBidExecutor`/`RedisBidExecutor`)를 입찰 가능여부 판단 + wallet 처리 +
   (buyNow 시) 원자적 쓰기(Bid/Order)만 남기고 이벤트 발행과 완전히 무관하게 만든다.
2. 경매(`auction.sse`)/알림(`notification.sse`) 실시간 전파를 Redis Pub/Sub 기반으로
   바꿔서 다중 인스턴스에서도 동작하게 한다.

## 범위

### 포함

- `BidExecutor.execute()` 반환형 변경, `DbBidExecutor`/`RedisBidExecutor`에서 publish 제거
- `AuctionCommandService`가 result로부터 이벤트를 조립해 발행 + 경매 스트림용 Redis publish를
  직접 호출
- `NotificationPushPublisher`를 Redis 구현체로 교체(저장 로직은 무변경)
- `AuctionStreamPublisher` 신설 + Redis 구현체, `AuctionSseEventListener` 삭제
- `auction.sse`/`notification.sse`에 각각 Redis 구독자 신설(DB 조회 없이 그대로 전달)
- `fallbackExecution=true` 4곳 추가
- Redis pub/sub 인프라(컨테이너, 채널, 직렬화) 최초 구성

### 제외

- `RedisBidExecutor`의 실제 판단 로직(Lua Script) — `#326` 범위 그대로 placeholder 유지
- Lua/Redis 경로의 DB 전용 metric(critical-section/lock-wait/flush) 대응 방안 — 후속 이슈
- notification origin/subscriber executor 분리 및 가상 스레드 전환 — `#305`(이번엔 fan-out도
  `notificationTaskExecutor` 재사용)
- 배치 발행 dedup·공유 payload 전환 — `#289`
- 마감 스케줄러(`AuctionCommandService.closeAuctionInternal`)의 판단 로직 자체 — publish 호출
  위치는 그대로, 리스너만 Redis 경로로 바뀜
- `orderEventPort`/`AuctionEventPublisher` 자체 교체 — 로컬 Spring 이벤트 메커니즘 유지

## 설계

### 전체 흐름

두 갈래로 나뉜다. 저장이 필요한 것(알림)은 로컬 Spring 이벤트를 거쳐 "정확히 한 인스턴스에서
한 번" 저장한 뒤 Redis publish, 저장이 필요 없는 것(경매 스트림)은 발행부에서 곧바로 Redis
publish한다.

```
[알림 경로 — 저장이 필요해서 로컬 이벤트를 한 번 거침]
AuctionCommandService / OrderService
  → auctionEventPublisher / orderEventPort (로컬 Spring 이벤트, 변경 없음)
  → NotificationEventListener (변경 없음: 카드명 조회 → save → 이제 Redis로 publish)
  → Redis 채널 "notification:push"
  → 모든 인스턴스의 구독자 → 자기 로컬 커넥션에 있으면 push (DB 조회 없음)

[경매 스트림 경로 — 저장이 없어서 바로 publish]
AuctionCommandService
  → AuctionStreamPayload 직접 조립 → AuctionStreamPublisher(Redis) 직접 호출
  → Redis 채널 "auction:stream"
  → 모든 인스턴스의 구독자 → connectionManager.broadcast() (DB 조회 없음)
```

### 1. `BidExecutor` 이벤트 무관화

`auction.bid` 패키지에 새 내부 DTO(공개 API `BidResponses.BidResult`와는 분리):

```java
public record BidExecutionResult(
        BidResponses.BidResult result,
        BidEventData eventData          // 멱등 재생이면 null → 아무 이벤트도 발행하지 않음
) {}

public record BidEventData(
        Integer itemId,
        Integer previousBidderId,
        Long previousBidId,
        Long startPrice,
        Long bidIncrement,
        AuctionStatus status,
        boolean closeTimeExtended,      // true면 AuctionCloseScheduleChangedEvent 발행
        AuctionCloseData closeData      // buyNow로 즉시 낙찰된 경우만 non-null
) {}

public record AuctionCloseData(
        Integer cardId,
        String cardName,
        String cardPsaGrade,
        String cardLanguage,
        String cardThumbnailUrl,
        Integer sellerId
) {}
```

`auctionId`/`bidderId`/`currentPrice`/`bidCount`/`closeTime`/`occurredAt`/`closedAt`/
`winnerId`/`winningPrice`는 `participate()`의 파라미터(`userId`, `auctionId`)와 기존
`BidResponses.BidResult`(`result.auction()`, `result.bid()`)에서 이미 나오므로 중복 필드로
안 둔다(`Bid.createdAt`이 `@CreationTimestamp`가 아니라 `execute()`가 만든 `bidAt`을 그대로
저장하므로 `result.bid().createdAt()`이 `occurredAt`/`closedAt`과 항상 같다).

`BidExecutor` 인터페이스:

```java
public interface BidExecutor {
    BidExecutionResult execute(BidCommand command);
}
```

`DbBidExecutor.execute()`는 `publishBidPlaced`/`publishAuctionClosed`/
`publishCloseScheduleChanged` 호출을 전부 제거하고, 대신 `BidEventData`를 채워서
`BidExecutionResult`로 반환한다. `closeLockedAuction`(buyNow) 안의 Order 생성/wallet
capture는 원자적 쓰기라 그대로 남긴다. `AuctionEventPublisher auctionEventPublisher`,
`ApplicationEventPublisher eventPublisher` 필드는 더 이상 안 쓰이므로 제거한다.
멱등 재생 경로(`findIdempotentBidResponse`)는 `eventData = null`.

`RedisBidExecutor`(placeholder)도 `BidExecutionResult(stubResult, null)`로 시그니처만
맞춘다 — 동작 변화 없음.

### 2. `AuctionCommandService` — 조립 + 발행

```java
public BidResponses.BidResult participate(
        Integer userId, Integer auctionId, BidCreateRequest request, String idempotencyKey
) {
    Timer.Sample sample = auctionMetrics.start();
    try {
        BidExecutionResult outcome = bidExecutor.execute(
                new BidCommand(userId, auctionId, request.price(), idempotencyKey));
        publishBidEvents(userId, auctionId, outcome);
        auctionMetrics.finishBid(sample, BidResult.ACCEPTED);
        return outcome.result();
    } catch (AuctionException exception) {
        auctionMetrics.finishBid(sample, BidResult.REJECTED);
        throw exception;
    } catch (RuntimeException exception) {
        auctionMetrics.finishBid(sample, BidResult.ERROR);
        throw exception;
    }
}

private void publishBidEvents(Integer userId, Integer auctionId, BidExecutionResult outcome) {
    BidEventData data = outcome.eventData();
    if (data == null) {
        return; // 멱등 재생
    }
    BidResponses.AuctionSnapshot auction = outcome.result().auction();
    Instant occurredAt = outcome.result().bid().createdAt();

    BidPlacedEvent bidPlaced = new BidPlacedEvent(
            auctionId, data.itemId(), userId, data.previousBidderId(), data.previousBidId(),
            data.startPrice(), auction.currentPrice(), data.bidIncrement(), auction.bidCount(),
            auction.endsAt(), data.status(), occurredAt);
    auctionEventPublisher.publishBidPlaced(bidPlaced);           // 로컬 이벤트 (notification용)
    auctionStreamPublisher.publish(AuctionStreamPayload.bidPlaced(bidPlaced)); // Redis 직접 publish

    if (data.closeData() != null) {
        AuctionCloseData close = data.closeData();
        AuctionClosedEvent closed = new AuctionClosedEvent(
                auctionId, close.cardId(), close.cardName(), close.cardPsaGrade(),
                close.cardLanguage(), close.cardThumbnailUrl(), userId, close.sellerId(),
                data.startPrice(), auction.currentPrice(), auction.currentPrice(),
                data.bidIncrement(), auction.bidCount(), auction.endsAt(), data.status(), occurredAt);
        auctionEventPublisher.publishClosed(closed);
        auctionStreamPublisher.publish(AuctionStreamPayload.closed(closed));
    }
    if (data.closeTimeExtended()) {
        eventPublisher.publishEvent(new AuctionCloseScheduleChangedEvent(
                auctionId, auction.endsAt(), "close_time_extended"));
    }
}
```

`create()`(`AuctionOpenedEvent`)와 `closeAuctionInternal()`(스케줄러 경로의
`AuctionClosedEvent`)도 같은 패턴으로 `auctionStreamPublisher.publish(AuctionStreamPayload.xxx(event))`
호출을 로컬 이벤트 발행 바로 다음에 추가한다. 스케줄러 경로의 판단 로직 자체는 한 줄도 안
건드린다.

### 3. Notification 경로 — 저장 로직 무변경, publish만 Redis

`NotificationEventListener`는 그대로 둔다(카드명 조회, `saveForBid`, 중복 레이스 캐치,
5개 이벤트 핸들러 전부 무변경). 바뀌는 건 `NotificationPushPublisher` 구현체뿐이다.

```java
// notification.sse 패키지
public record NotificationPushMessage(Integer userId, NotificationResponse payload) {}

@Component
@RequiredArgsConstructor
public class RedisNotificationPushPublisher implements NotificationPushPublisher {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(Integer userId, NotificationResponse payload) {
        redisTemplate.convertAndSend(NOTIFICATION_PUSH_CHANNEL,
                writeJson(new NotificationPushMessage(userId, payload)));
    }
}

@Component
@RequiredArgsConstructor
public class NotificationPushRedisSubscriber implements MessageListener {
    private final NotificationSseConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Async("notificationTaskExecutor")   // origin(저장+발행)과 동일 executor 재사용 — #305에서 분리 검토
    @Override
    public void onMessage(Message message, byte[] pattern) {
        NotificationPushMessage parsed = readJson(message.getBody(), NotificationPushMessage.class);
        connectionManager.push(parsed.userId(), parsed.payload());
    }
}
```

`LocalNotificationPushPublisher`는 삭제한다(다중 인스턴스 전제, 로컬 폴백 없음).

### 4. Auction 스트림 경로 — 저장 없음, 바로 publish

```java
// auction.sse 패키지
public interface AuctionStreamPublisher {
    void publish(AuctionStreamPayload payload);
}

@Component
@RequiredArgsConstructor
public class RedisAuctionStreamPublisher implements AuctionStreamPublisher {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(AuctionStreamPayload payload) {
        redisTemplate.convertAndSend(AUCTION_STREAM_CHANNEL, writeJson(payload));
    }
}

@Component
@RequiredArgsConstructor
public class AuctionStreamRedisSubscriber implements MessageListener {
    private final AuctionSseConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        connectionManager.broadcast(readJson(message.getBody(), AuctionStreamPayload.class));
        // broadcast() 자체가 이미 @Async("auctionSseTaskExecutor") — origin이 이 executor를
        // 더 이상 안 쓰므로 경쟁 없이 재사용 가능.
    }
}
```

`AuctionSseEventListener`(로컬 이벤트 → `connectionManager.broadcast()` 직접 호출, 순수
배관)는 삭제한다. `auction.sse` 패키지에는 이제 커넥션 매니저, 컨트롤러, 위 publisher/구독자만
남는다. 로드테스트 스캐폴딩(`AuctionSseTestBidApplicationService` 등, `connectionManager`를
이벤트 파이프라인 없이 직접 찌르는 헬퍼)은 손대지 않는다.

### 5. Redis 인프라

`global.config.RedisPubSubConfig`(신규):

```java
@Configuration
public class RedisPubSubConfig {
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            AuctionStreamRedisSubscriber auctionStreamSubscriber,
            NotificationPushRedisSubscriber notificationPushSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(auctionStreamSubscriber, new ChannelTopic(AUCTION_STREAM_CHANNEL));
        container.addMessageListener(notificationPushSubscriber, new ChannelTopic(NOTIFICATION_PUSH_CHANNEL));
        return container;
    }
}
```

직렬화는 Spring Boot가 자동 구성하는 `ObjectMapper` 빈을 그대로 쓴다(JSON 문자열로
`convertAndSend`, 수신 시 `message.getBody()`를 UTF-8 문자열로 읽어 역직렬화) — `AuctionStreamPayload`/
`BidPlacedEvent`/`AuctionClosedEvent`/`NotificationResponse` 모두 이미 Jackson으로 직렬화되는
평범한 record라 별도 커스텀 직렬화가 필요 없다. 채널 이름 상수(`"auction:stream"`,
`"notification:push"`)는 각 publisher/subscriber와 같은 패키지에 둔다.

### 6. `fallbackExecution` 수정

publish 위치가 `DbBidExecutor`(트랜잭션 내부)에서 `AuctionCommandService`(즉시낙찰/입찰
경로는 이미 커밋된 뒤)로 옮겨지면서, 활성 트랜잭션 없이 발행된 이벤트가 조용히 드랍되지 않게
아래 4곳에 `fallbackExecution = true`를 추가한다(`AuctionSseEventListener`는 이미 전부
설정돼 있었으나 이번에 삭제됨):

- `NotificationEventListener.handleBidPlaced`
- `NotificationEventListener.handleAuctionClosed`
- `AuctionDeadlineScheduler.reschedule`
- `AuctionMetricEventListener.recordExtension`

## 테스트 영향

- `DbBidExecutorTest`(#326에서 이관된 것): 반환형이 `BidExecutionResult`로 바뀌므로 단언 갱신.
  publish 관련 mock 검증(`auctionEventPublisher` 호출 검증)은 제거하고, `eventData` 필드값
  검증으로 대체.
- `AuctionCommandServiceBidTest`(신규 또는 `participate()` 대상 테스트에 추가): `publishBidEvents`
  조립 로직 — 상회입찰/최초입찰(`previousBidderId=null`)/즉시낙찰/마감연장 4가지 케이스에서
  올바른 이벤트가 조립되는지 검증.
- `NotificationEventListenerTest`: 변경 없음(구현이 안 바뀌므로) — `NotificationPushPublisher`는
  여전히 mock.
- `AuctionSseEventListenerTest`: 클래스 삭제와 함께 제거.
- 신규 `RedisNotificationPushPublisherTest`/`RedisAuctionStreamPublisherTest`: convertAndSend
  호출 인자(채널, JSON) 검증.
- 신규 `NotificationPushRedisSubscriberTest`/`AuctionStreamRedisSubscriberTest`: 역직렬화 후
  connectionManager 호출 검증.
- 신규 `RedisPubSubConfigTest`(선택): 컨테이너 빈이 두 구독자를 각자 채널에 등록하는지 검증.

## 결론

`BidExecutor`는 판단 + wallet + (buyNow 시) 원자적 쓰기만 담당하고, 이벤트 발행은 전부
`AuctionCommandService`로 옮긴다. 알림처럼 저장이 필요한 경로는 기존 로컬 이벤트 리스너
구조(정확히 한 인스턴스에서 한 번 저장)를 그대로 두고 마지막 push만 Redis로 바꾸며, 경매
스트림처럼 저장이 없는 경로는 로컬 이벤트를 한 번 더 거치지 않고 `AuctionCommandService`가
바로 Redis publish한다. notification의 origin/subscriber executor 분리는 `#305`(가상 스레드
전환) 착수 시점으로 미룬다.

> 이 문서는 Claude의 도움을 받아 작성하였습니다.
