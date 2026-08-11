# 위시리스트 fan-out SSE push를 배치 발행 1번으로 묶기

담당: D(임하민). 이슈 #289.

## 배경

`NotificationEventListener.handleAuctionOpened`는 카드를 찜한 유저 전원에게 알림을 저장한 뒤, `saveAllIgnoringDuplicates`가 돌려준 `List<Notification>`을 `forEach`로 순회하며 유저마다 `notificationPushPublisher.publish(userId, payload)`를 개별 호출한다. `RedisNotificationPushPublisher` 구현에서는 이 호출 하나가 Redis `PUBLISH` 1번이므로, 찜 유저가 N명이면 같은 경매 오픈 이벤트 하나에 Redis PUBLISH가 N번 나간다 — 유저마다 콘텐츠(auctionId, type, message, isRead, createdAt)는 완전히 같고 `notificationId`만 다른데도 그렇다.

## 검토했던 대안과 폐기 이유

- **SSE payload에서 `id` 제거 + 프론트 dedup 키를 `(auctionId, message)`로 변경**: 처음 검토한 방향. 하지만 프론트 `markAsRead(notification.id)`가 실제 DB row id에 의존하므로(`NotificationController.markAsRead`), id를 없애면 SSE로 막 받은 배치 알림은 REST 재조회 전까지 읽음 처리가 안 된다. `NotificationDto.id`를 nullable로 바꾸면 파생 타입/널체크가 여러 곳(React key, 읽음 처리, 정렬)에 번져 실익보다 비용이 크다고 판단해 폐기.
- **id는 유지하고 dedup 키만 바꾸기**: id를 유지하면 Redis PUBLISH를 유저마다 여전히 개별로 날리게 되어(콘텐츠만 같고 유저별 튜플은 여전히 N개) 정작 얻으려던 절감이 없다는 게 드러나 폐기.
- **최종 채택**: 콘텐츠도, id도 그대로 유지한 채 "발행 호출 자체"만 배치로 묶는다. 유저마다 실제로 다른 값(`userId`, `notificationId`)은 그대로 각자 payload에 싣고, 그 묶음을 Redis PUBLISH 1번으로 보낸다. 프론트/DTO는 전혀 안 건드려도 된다 — 브라우저가 받는 개별 SSE 이벤트 모양은 지금과 동일.

## 설계

- `NotificationPushPublisher`: 기존 `publish(Integer userId, NotificationResponse payload)`를 새 메서드 `publish(List<NotificationPushMessage> messages)` 위에 얹은 default 메서드로 바꾼다(`List.of(new NotificationPushMessage(userId, payload))`로 위임). 구현체는 배치 메서드 하나만 구현하면 되고, 단건 호출부(OUTBID/AUCTION_WON/AUCTION_UNSOLD/ORDER_COMPLETED/ORDER_CANCELLED — `NotificationEventListener.saveAndPush`)는 코드 변경 없이 default 메서드를 그대로 탄다.
- `RedisNotificationPushPublisher.publish(List<NotificationPushMessage>)`: 리스트 전체를 JSON 배열 하나로 직렬화해 Redis `PUBLISH`를 1번만 호출한다. 단건도 원소 1개짜리 배열이 되어, 이제 이 채널의 wire 포맷이 "항상 배열"로 통일된다(기존엔 단일 객체였음 — 브레이킹이지만 발행/구독 둘 다 이 PR에서 같이 바꾸는 내부 프로토콜이라 문제없음).
- `NotificationPushRedisSubscriber.onMessage`: `NotificationPushMessage` 단건 대신 `List<NotificationPushMessage>`로 역직렬화해서 각 원소를 로컬 커넥션에 push한다. 로컬 커넥션 매니저로의 dispatch는 원래도 인메모리 루프라 배치로 묶어도 비용 차이가 없다 — 줄어드는 건 Redis PUBLISH/구독 인스턴스별 `@Async` 태스크 디스패치 횟수(유저 수 → 1)뿐이다.
- `LocalNotificationPushPublisher.publish(List<NotificationPushMessage>)`: 리스트를 순회하며 `connectionManager.push(userId, payload)` — Redis가 없는 프로필이라 원래도 배치의 이점이 크지 않지만, 인터페이스 계약을 맞추기 위해 구현한다.
- `NotificationEventListener.handleAuctionOpened`: `saveAllIgnoringDuplicates`가 돌려준 `List<Notification>`을 `forEach` 대신 `List<NotificationPushMessage>`로 한 번에 매핑해서 `publish(List<NotificationPushMessage>)`를 1번만 호출한다. 재조회 SELECT(#207에서 결정한 대로 청크 없이 1회 조회)는 그대로 유지 — 여기서 얻은 유저별 진짜 id를 배치 payload에 그대로 싣는다.

## 범위 밖으로 남긴 것

- 프론트 `useNotificationStream.ts`의 dedup 키, `NotificationDto`/`NotificationResponse`의 `id` 필드 — 이번엔 안 건드림. 브라우저가 받는 SSE 이벤트는 지금과 동일한 모양(유저당 1건, 각자의 실제 id 포함)이라 프론트 변경이 필요 없다.
- `saveAllIgnoringDuplicates`의 재조회 SELECT를 없애는 것 — id를 계속 쓰므로 재조회는 여전히 필요.

## 작업 항목

- [ ] `NotificationPushPublisher`를 배치 메서드 기반으로 변경
- [ ] `RedisNotificationPushPublisher`/`NotificationPushRedisSubscriber`/`LocalNotificationPushPublisher` 배치 처리로 변경
- [ ] `NotificationEventListener.handleAuctionOpened`에서 forEach 제거하고 배치 publish 1번으로 변경
- [ ] `NotificationEventListenerTest`의 AUCTION_OPENED 테스트를 배치 publish 검증으로 갱신
- [ ] `RedisNotificationPushPublisherTest`/`NotificationPushRedisSubscriberTest`/`LocalNotificationPushPublisherTest`를 배열 wire 포맷 + 배치(다건) 케이스로 갱신/추가
- [ ] `NotificationPushPublisherProfileTest`는 프로필별 빈 등록만 검증하므로 영향 없는지 확인만

> 이 문서는 claude의 도움을 받아 작성하였습니다.
