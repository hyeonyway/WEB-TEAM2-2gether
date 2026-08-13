# 상회입찰/경매종료 복구도 batch INSERT IGNORE로 통합

이슈 #414. [18-recovery-scheduler-urgency-split-plan.md](18-recovery-scheduler-urgency-split-plan.md)에서 경매 생성 복구(`recoverAuctionOpenedNotifications`)만 `insertAllIgnoringDuplicates`로 배치 INSERT하도록 바꿨고, 나머지 두 복구(상회입찰/경매종료)는 범위 밖으로 남겨뒀다. 이번엔 그 둘도 같은 방식으로 통합한다.

## 배경

`notification` 테이블엔 이미 `(user_id, auction_id, type, bid_id)` 유니크 제약이 있다([6-notification-recovery-batch.md](6-notification-recovery-batch.md) 결정 2-1). `INSERT IGNORE`가 유니크 위반 행을 알아서 건너뛰므로, 사전에 "이미 알림이 있는지" 조회해서 걸러내는 존재 체크(`alreadyNotifiedBidIds`, `alreadyNotified`)는 구조적으로 불필요하다 — 지금은 이미 불필요한 조회(N+1은 아니지만 배치 조회 1회씩)와 애플리케이션 레벨 필터링을 하고 있다.

- 상회입찰 복구(`recoverOutbidNotificationsForCandidates`): 후보 bid마다 `saveIgnoringDuplicate(() -> notificationService.saveForBid(...))`로 개별 INSERT.
- 경매종료 복구(`recoverAuctionClosedNotifications`): 경매마다 `ensureResultNotification(...)`(최대 2회)로 개별 INSERT.

두 경우 모두 DB round trip이 후보 개수만큼 발생한다.

## 변경

### `NotificationInsertRow`에 `type`/`bidId` 추가

현재:
```java
public record NotificationInsertRow(Integer userId, Integer auctionId, String message) {}
```

경매 생성 복구는 행 전체가 같은 타입(`AUCTION_OPENED`)이라 `insertAllIgnoringDuplicates(rows, type)`처럼 타입을 메서드 인자로 따로 받았다. 경매종료 복구는 행마다 타입이 다르고(`AUCTION_WON`/`AUCTION_UNSOLD`), 상회입찰 복구는 행마다 `bid_id`가 다르므로, 타입/`bid_id`를 레코드 필드로 옮긴다.

```java
public record NotificationInsertRow(Integer userId, Integer auctionId, NotificationType type, Long bidId, String message) {
    public static NotificationInsertRow of(Integer userId, Integer auctionId, NotificationType type, String message) {
        return new NotificationInsertRow(userId, auctionId, type, Notification.NO_BID, message);
    }
}
```

bid와 무관한 타입(`AUCTION_OPENED`/`AUCTION_WON`/`AUCTION_UNSOLD`)은 `of(...)` 팩토리로 `bid_id=NO_BID`를 고정하고, `OUTBID`만 4-arg 생성자로 실제 `bid_id`를 넘긴다.

### `NotificationService.insertAllIgnoringDuplicates`에서 `type` 인자 제거

행마다 타입이 다를 수 있으므로 메서드 인자의 공통 `type`을 없애고 각 행의 `row.type()`/`row.bidId()`를 그대로 SQL 바인딩에 쓴다. `saveAllIgnoringDuplicates`(라이브 이벤트 경로, `NotificationInsertRow`를 쓰지 않는 별도 메서드)는 이번 변경과 무관해 그대로 둔다.

### `recoverAuctionClosedNotifications`

`alreadyNotified` 조회(`notificationRepository.findByBidIdAndAuctionIdInAndUserIdIn`)와 `ensureResultNotification`/`resultNotificationKey` 전부 제거. 낙찰/유찰 판단 로직(`winningBidByAuctionId` 조회)은 그대로 두고, 그 결과로 `NotificationInsertRow` 목록을 만들어 한 번에 `insertAllIgnoringDuplicates` 호출.

### `recoverOutbidNotificationsForCandidates`

`candidateBidIds`/`alreadyNotifiedBidIds` 조회(`notificationRepository.findByBidIdIn`)와 개별 `saveIgnoringDuplicate(saveForBid)` 호출 제거. `outbidCandidates`를 그대로 순회하며 `NotificationInsertRow` 목록을 만들어 한 번에 `insertAllIgnoringDuplicates` 호출.

### `saveIgnoringDuplicate`/`DataIntegrityViolationException` 처리 제거

이 두 메서드에서만 쓰던 헬퍼라, 둘 다 배치 INSERT로 바뀌면 `NotificationReconciliationService`에서 완전히 제거된다(단일 `save`/`saveForBid` 경로에는 여전히 필요하지만 그건 라이브 이벤트 경로 얘기라 이 클래스와 무관).

### `notificationRepository` 필드

이 클래스에서 더 쓸 일이 없어지면 필드/의존성 자체를 제거한다.

## 테스트

- `NotificationReconciliationServiceTest`: 낙찰/유찰/상회입찰 관련 테스트를 `verify(notificationService).save(...)`/`saveForBid(...)` 검증에서 `verify(notificationService).insertAllIgnoringDuplicates(List.of(...))` 검증으로 변경. "이미 보낸 알림은 다시 보내지 않는다"/"이미 알림이 갔으면 다시 보내지 않는다" 테스트는 애플리케이션 레벨 dedup이 사라지면서(DB의 INSERT IGNORE가 대신 처리) 삭제. `notificationRepository` mock과 관련 헬퍼(`existingResultNotification`/`existingOutbidNotification`)도 더 이상 쓰이지 않아 함께 제거.
- `NotificationServiceTest`/`NotificationServiceBulkInsertTest`: `NotificationInsertRow` 생성자 시그니처(`type`/`bidId` 추가)와 `insertAllIgnoringDuplicates(rows)`(공통 `type` 인자 제거)에 맞춰 갱신.
- `NotificationReconciliationService`에서 더 안 쓰는 `notificationRepository` 필드/의존성, `saveIgnoringDuplicate`/`ensureResultNotification`/`resultNotificationKey` 헬퍼, `DataIntegrityViolationException`/`Slf4j` import 제거.

## 구현 완료

- `NotificationInsertRow`에 `type`/`bidId` 필드 추가 + `of(...)` 팩토리(bid_id=NO_BID 고정).
- `NotificationService.insertAllIgnoringDuplicates(rows)` — 공통 `type` 인자 제거, 행마다 `type`/`bidId` 사용.
- `recoverAuctionClosedNotifications`/`recoverOutbidNotificationsForCandidates` 둘 다 배치 INSERT로 전환, 사전 존재 체크(`alreadyNotified`, `alreadyNotifiedBidIds`) 및 `ensureResultNotification` 제거.
- 영향받는 테스트 클래스(`NotificationServiceTest`, `NotificationServiceBulkInsertTest`, `NotificationReconciliationServiceTest`, 스케줄러 테스트) 통과 확인.
