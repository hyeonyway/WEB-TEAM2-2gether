# NotificationEventListener 알림 저장/SSE push 배치 최적화 (계획)

refs #506 (선행: #505, dev에 머지됨)

## 배경

`NotificationEventListener`의 알림 저장 경로 세 곳이 필요 이상으로 DB를 왕복한다.

1. `handleAuctionOpened()` → `saveAllIgnoringDuplicates()`가 배치 INSERT IGNORE 후 재조회 SELECT로 각 유저의 실제 row(id 포함)를 다시 읽어와 push 페이로드를 만든다.
2. 그 재조회 결과로 유저마다 별도 `NotificationResponse` 객체를 새로 만드는데, 이 fan-out은 전원이 완전히 동일한 내용(같은 type/auctionId/message)을 받는다.
3. `handleAuctionClosed()`/`handleOrderCompleted()`/`handleOrderCancelled()`는 `saveAndPush()`를 2번 호출해서 INSERT를 2번 따로 한다.

#505에서 markAsRead/SSE dedup이 `id` 대신 `(userId, type, auctionId/bidId)` 복합키 기준으로 바뀌었으므로, 이제 push 경로가 유저별 정확한 DB `id`를 몰라도 안전하다.

## 변경 사항

1. **readback 제거**: `NotificationService.saveAllIgnoringDuplicates`에서 재조회 SELECT(`findByAuctionIdAndTypeAndBidIdAndUserIdIn`)를 없애고 반환 타입을 `void`로 변경. 이제 아무 곳에서도 안 쓰는 `NotificationRepository.findByAuctionIdAndTypeAndBidIdAndUserIdIn`도 함께 제거.
2. **공유 DTO 재사용**: `handleAuctionOpened`는 `NotificationResponse` 객체 하나만 만들어(`id`는 placeholder `0L`, `createdAt`은 `Instant.now()`로 근사, `isRead=false`) `userIds` 전원에게 재사용한다.
3. **INSERT 합치기**: `handleAuctionClosed`/`handleOrderCompleted`/`handleOrderCancelled`의 2회 `saveAndPush` 단건 INSERT를 새 private 헬퍼 `saveAllAndPush(auctionId, type, recipients)`로 통합 — `NotificationService.insertAllIgnoringDuplicates`(이미 있는 멀티로우 INSERT IGNORE)로 한 번에 저장하고, push는 수신자별로 다른 메시지를 담아 개별 발행한다. `handleBidPlaced`(OUTBID, 단건+실제 bidId)는 변경 없음.

두 경로 모두 INSERT IGNORE만 쓰고 재조회를 하지 않으므로, push 페이로드의 `id`/`createdAt`은 실제 DB 값과 밀리초 수준 오차가 있을 수 있는 근사값이다 — 화면 표시(실시간 토스트/목록 prepend)용이라 문제없고, 다음 `GET /notifications` 조회 시 실제 값으로 자연히 교정된다.

## 검증

- `./gradlew test --tests "com.dbidding.notification.*"` 통과
- `NotificationServiceTest`: `saveAllIgnoringDuplicates`가 재조회 없이 INSERT만 호출하는지, `findByAuctionIdAndTypeAndBidIdAndUserIdIn` 관련 테스트 제거
- `NotificationEventListenerTest`: 공유 DTO 재사용(동일 인스턴스 또는 동일 값), 배치 INSERT 반영(각 3개 이벤트가 `insertAllIgnoringDuplicates`를 1번만 호출) 검증
