# 복구 배치의 경매 생성 알림 fan-out을 saveAllIgnoringDuplicates로 전환

담당: D(임하민). 이슈 #306. [7-notification-fanout-batch-insert.md](7-notification-fanout-batch-insert.md)(#190)에서 라이브 경로만 고쳤던 부분을 복구 배치에도 반영한다.

## 배경

`NotificationReconciliationService.recoverAuctionOpenedNotifications`는 최근 열린 경매마다 그 카드를 찜한 유저 전원을 순회하며 `ensureNotification`(존재 체크 SELECT 1번 + 필요하면 `save()` INSERT 1번)을 개별 호출한다. #190이 라이브 fan-out(`NotificationEventListener.handleAuctionOpened`)의 이 문제를 고쳤을 때, 6단계 문서([6-notification-recovery-batch.md](6-notification-recovery-batch.md) 범위 밖 항목)에서 복구 배치 쪽은 의도적으로 범위 밖으로 남겨뒀다.

인기 카드는 찜 유저가 수백~수천 명일 수 있어서, 라이브 경로가 어떤 이유로든(인스턴스 재시작으로 비동기 이벤트 유실, `wishlistService.findUserIdsByCardId()`가 그 시점에 불완전한 목록을 돌려줌 등) 다수를 놓쳤을 때 복구 배치가 처리해야 하는 규모도 라이브 fan-out과 같아질 수 있다. 그런데 지금 복구 배치는 유저 수만큼 SELECT+INSERT를 개별로 하고 있어, 최선의 경우(전부 이미 있음)에도 유저 수만큼 SELECT가 든다.

## 설계

`recoverAuctionOpenedNotifications`가 경매당 `wishlistService.findUserIdsByCardId(...)`로 얻은 유저 목록을 그대로 `notificationService.saveAllIgnoringDuplicates(userIds, auctionId, NotificationType.AUCTION_OPENED, message)`에 한 번에 넘기도록 바꾼다. `INSERT IGNORE`가 이미 있는 유저는 자동으로 스킵하므로, `ensureNotification`의 존재 체크(SELECT) 자체가 필요 없어진다 — 왕복이 경매당 규모와 무관하게 ~2번(INSERT 1 + 재조회 SELECT 1)으로 고정된다.

`ensureNotification`/`saveIgnoringDuplicate`는 그대로 둔다. `recoverAuctionClosedNotifications`(낙찰자+판매자, 대상 최대 2명)와 `recoverOutbidNotifications`(대상 1명)는 원래도 대상 규모가 작아 개별 처리 방식이 적합하다.

복구 배치는 push를 안 하는 기존 설계를 그대로 유지한다 — `saveAllIgnoringDuplicates`의 반환값(저장/기존 알림 목록)은 사용하지 않고 버린다. 라이브로 연결돼 있던 유저는 이미 라이브 경로가 push했을 것이고, 복구 배치가 처리하는 시점엔 대부분 오프라인 상태라 push할 대상이 없다.

## 테스트

`NotificationReconciliationServiceTest`의 `recoverAuctionOpenedNotifications` 관련 테스트 2개를 갱신한다:
- 존재 체크 mock(`existsByUserIdAndAuctionIdAndTypeAndBidId`) 기반 검증 → `saveAllIgnoringDuplicates`가 찜 유저 전체 목록으로 호출됐는지 검증으로 교체.
- "유니크 제약 위반이 나도 예외를 삼키고 계속한다" 테스트는 삭제 — `INSERT IGNORE`가 SQL 레벨에서 처리하므로 이 메서드에서는 더 이상 `DataIntegrityViolationException`이 나지 않는다(이 예외 처리 로직 자체가 `recoverAuctionClosedNotifications`/`recoverOutbidNotifications`에는 그대로 남아있으므로 그쪽 테스트는 유지).

## 범위 밖으로 남긴 것

- `recoverAuctionClosedNotifications`/`recoverOutbidNotifications`의 개별 처리 방식 — 대상 규모가 원래 작아 바꿀 이유 없음.
- 라이브 경로가 왜 유실됐는지(인스턴스 재시작, wishlist 조회 불완전 등) 자체를 막는 것 — 이건 복구 배치의 존재 이유이자 범위 밖.

> 이 문서는 claude의 도움을 받아 작성하였습니다.
