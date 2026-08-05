# 경매 생성 알림 fan-out을 batch insert로 개선

담당: D(임하민). [6-notification-recovery-batch.md](6-notification-recovery-batch.md) 결정 8에서 발견된 별개 이슈(#190)를 다룬다. 구현 완료, PR 진행 중.

## 배경

`NotificationEventListener.handleAuctionOpened`는 경매가 열리면 그 카드를 찜한 유저 전원에게 알림을 보낸다. 기존 구현은 유저 한 명당 `NotificationService.save(...)`를 개별 호출하는 구조였다 — 찜 유저가 N명이면 DB round trip N번, 트랜잭션 commit N번이 발생하고, SSE push도 유저마다 개별 호출되며 전체가 `@Async` 단일 스레드에서 순차 실행됐다. 인기 카드처럼 찜 유저가 수백 명이면 경매 하나 열릴 때마다 이 처리가 눈에 띄게 오래 걸릴 수 있었다.

## 설계: `INSERT IGNORE` + 재조회로 왕복 2번 고정

`NotificationService.saveAllIgnoringDuplicates(userIds, auctionId, type, message)`(신규)가 핵심이다.

1. **멀티-VALUES `INSERT IGNORE`**: `(?, ?, ?, ?, ?)`를 유저 수만큼 이어붙여 `INSERT IGNORE INTO notification (user_id, auction_id, type, bid_id, message) VALUES (...), (...), ...` 한 문장으로 N명을 전부 저장한다. `bid_id`는 이 fan-out 전용이라 항상 `Notification.NO_BID`(0).
2. **왜 `INSERT IGNORE`인가**: `notification`엔 `(user_id, auction_id, type, bid_id)` 유니크 제약이 있다(6단계 결정 2-1). 복구 배치나 다른 경로가 특정 유저 것을 이미 저장해뒀을 수 있는데, 일반 `INSERT`였으면 그 한 행 때문에 멀티-VALUES 문장 전체가 예외로 실패한다. `INSERT IGNORE`는 유니크 제약에 걸리는 행만 조용히 건너뛰고 나머지는 그대로 저장해줘서, 유저별 try/catch 없이 레이스를 그대로 흡수한다.
3. **재조회**: `INSERT`는 생성된 id/`createdAt`을 편하게 돌려주지 않으므로, `NotificationRepository.findByAuctionIdAndTypeAndBidIdAndUserIdIn(...)`으로 방금 저장된 행과 이미 있던 행을 **한 번의 SELECT**로 함께 가져와 반환한다.

결과적으로 DB 왕복이 찜 유저 수와 무관하게 **INSERT 1번 + SELECT 1번**으로 고정된다. `NotificationEventListener.handleAuctionOpened`는 이 리스트를 받아 유저별로 SSE push만 하면 된다 — push는 유저마다 별도 연결(emitter)이라 원래도 배치가 불가능한 부분이라 그대로 뒀다.

```java
public void handleAuctionOpened(AuctionOpenedEvent event) {
    String message = event.cardName() + " 카드의 경매가 등록되었습니다.";
    List<Integer> userIds = wishlistUserFinder.findUserIdsByCardId(event.itemId());
    if (userIds.isEmpty()) {
        return;
    }
    notificationService.saveAllIgnoringDuplicates(userIds, event.auctionId(), NotificationType.AUCTION_OPENED, message)
            .forEach(notification ->
                    notificationSseConnectionManager.push(notification.getUserId(), NotificationResponse.from(notification)));
}
```

## 테스트

- `NotificationServiceTest`: mock 기반으로 SQL 문자열·파라미터 구성과 재조회 위임을 검증(`여러_유저의_알림을_한번에_저장하고_존재하는_행까지_함께_조회한다`), 빈 목록이면 아무 것도 안 함(`대상_유저가_없으면_저장_없이_빈_목록을_반환한다`).
- `NotificationServiceBulkInsertTest`(신규, 로컬 MySQL 통합 테스트): 실제 `INSERT IGNORE`가 유니크 제약 위반 행을 건너뛰는지 검증 — 유저 3명 중 1명은 미리 알림을 저장해두고 `saveAllIgnoringDuplicates`를 호출해, 그 유저는 기존 행을 그대로 재사용하고 나머지 2명만 새로 저장되는지 확인.
- `NotificationEventListenerTest`: `handleAuctionOpened`가 `saveAllIgnoringDuplicates`를 호출하고 반환된 각 알림을 SSE push하는지로 갱신.

## 범위 밖으로 남긴 것

`handleBidPlaced`(상회 입찰, 대상 1명)와 `handleAuctionClosed`(낙찰자+판매자, 최대 2명)는 애초에 fan-out 대상이 소수라 배치화 이득이 없어 그대로 뒀다. SSE push 자체의 배치화도 검토했지만, `NotificationSseConnectionManager`가 유저별 emitter 집합을 관리하는 구조라 여러 유저에게 한 번에 push하는 API가 성립하지 않는다 — push 루프는 그대로 두는 게 맞다는 결론.

> 이 문서는 claude의 도움을 받아 작성하였습니다.
