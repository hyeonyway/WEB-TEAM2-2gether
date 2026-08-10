# SSE/알림 push를 로컬 전용으로 되돌릴 수 있는 프로필 추가

담당: 임하민. 이슈: [#346](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/346)
(브랜치 `feature/346-local-sse-profile`). 관련: [#281](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/281)
(경매/알림 SSE Redis Pub/Sub 전환).

## 배경

#281로 `notification.sse`/`auction.sse`의 push/broadcast가 프로필과 무관하게 항상
Redis pub/sub을 거치도록 바뀌었다. 로컬 개발이나 Redis가 없는 환경(혹은 단일 인스턴스만
있는 상황)에서 과거처럼 로컬에서 직접 push/broadcast하는 경로를 프로필로 병존시킬 수 있게
한다.

## 목표

신규 프로필 `local-sse`를 추가한다. **기본값(프로필 미지정)은 지금과 동일하게 항상 Redis
pub/sub**이다 — 실제 배포 환경 동작을 바꾸지 않기 위해 기존 동작을 기본값으로 유지하고,
`local-sse`를 명시적으로 켰을 때만 로컬 전용 경로로 바뀐다. `DbBidExecutor`(`!redis`)/
`RedisBidExecutor`(`redis`)와 동일한 프로필 배타 패턴을 그대로 따른다.

`redis`(입찰 판단 로직)와 `local-sse`(SSE 전파 방식)는 서로 독립적인 토글이다 — DB 입찰 +
로컬 SSE, DB 입찰 + Redis SSE, Redis 입찰 + 로컬 SSE, Redis 입찰 + Redis SSE 네 조합
모두 가능해야 한다.

## 설계

### 프로필 배타 대상

| 인터페이스 | `!local-sse`(기본) | `local-sse` |
|---|---|---|
| `NotificationPushPublisher` | `RedisNotificationPushPublisher` | `LocalNotificationPushPublisher`(복원) |
| `AuctionStreamPublisher` | `RedisAuctionStreamPublisher` | `LocalAuctionStreamPublisher`(신규) |

`LocalNotificationPushPublisher`/`LocalAuctionStreamPublisher`는 각각
`connectionManager.push(...)`/`connectionManager.broadcast(...)`를 직접 호출한다(#281
이전 코드, #239 당시의 `LocalNotificationPushPublisher`와 동일).

### Redis 인프라도 `local-sse`일 때 연결 시도 자체를 안 하게

`AuctionStreamRedisSubscriber`, `NotificationPushRedisSubscriber`, `RedisPubSubConfig`
(컨테이너 + ObjectMapper 빈)에 `@Profile("!local-sse")`를 추가한다. 그냥 두면 구독자
빈은 생성되지만 아무 컨테이너에도 등록되지 않아 그냥 안 쓰이기만 할 뿐 해는 없지만, 명확성을
위해 구독자도 같이 게이팅한다. `RedisPubSubConfig`는 Redis 연결이 필요한 유일한 지점이라,
`local-sse` 프로필에서는 이 클래스 자체가 로드되지 않아 Redis 연결 시도가 전혀 없다.

`NotificationEventListener`/`AuctionCommandService`의 발행 호출부는 인터페이스만 바라보고
있어서 손댈 필요가 전혀 없다.

## 테스트

- `BidExecutorProfileTest`와 동일한 `ApplicationContextRunner` 패턴으로 프로필 스위칭
  테스트 추가(`NotificationPushPublisherProfileTest`, `AuctionStreamPublisherProfileTest`
  또는 하나로 합쳐서) — 기본 프로필/`local-sse` 각각에서 정확히 하나의 구현체만 등록되는지
  검증.
- 삭제됐던 `LocalNotificationPushPublisherTest` 복원, `LocalAuctionStreamPublisherTest`
  신규 추가 — 각각 `connectionManager.push`/`broadcast`로 그대로 위임하는지만 검증.

## 결론

기존 Redis pub/sub 경로는 기본값으로 그대로 두고, `local-sse` 프로필을 추가해 로컬
전용 경로와 병존시킨다. 배포 환경 동작 변화 없음.

> 이 문서는 Claude의 도움을 받아 작성하였습니다.
