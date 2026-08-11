# 알림 복구 배치의 N+1 쿼리 제거

이슈 #370

## 배경

`NotificationReconciliationService`는 대상 목록을 한 번 조회한 뒤, 루프 안에서 건별로 추가 쿼리를 날리는 N+1 패턴이 3군데 있음.

1. `recoverAuctionOpenedNotifications` - 경매마다 `wishlistService.findUserIdsByCardId(itemId)` 호출
2. `recoverAuctionClosedNotifications` - 경매마다 `bidRepository.findByAuctionIdAndStatus(...)` 호출 + `ensureNotification`에서 경매당 최대 2번 `existsByUserIdAndAuctionIdAndTypeAndBidId` 호출
3. `recoverOutbidNotifications` - 최신 입찰 건마다 `existsByUserIdAndAuctionIdAndTypeAndBidId` 호출

## 설계

### 1. `recoverAuctionOpenedNotifications`

- `WishlistRepository`에 `findByCardIdIn(Collection<Integer> cardIds)` 추가.
- `WishlistService`에 `groupUserIdsByCardIdIn(Collection<Integer> cardIds): Map<Integer, List<Integer>>` 추가 — cardId별 찜 유저 목록으로 그룹핑.
- 서비스에서: `recentlyOpened`의 `itemId`를 모아 한 번에 조회 → 경매별로 맵에서 꺼내 기존과 동일하게 `saveAllIgnoringDuplicates` 호출(이건 이미 유저 배치 insert라 그대로 둠).

### 2. `recoverAuctionClosedNotifications`

- 낙찰 bid 조회: 기존 `BidRepository.findByAuctionIdInAndStatus(Collection<Integer>, BidStatus)`(이미 존재)로 `recentlyClosed`의 auctionId를 모아 한 번에 조회 → `auctionId -> Bid` 맵 구성.
- 알림 존재 여부 배치 확인: `NotificationRepository`에 `findByBidIdAndAuctionIdInAndUserIdIn(Long bidId, Collection<Integer> auctionIds, Collection<Integer> userIds)` 추가.
  - `bidId=NO_BID` 고정, auctionId 목록과 후보 userId(낙찰자+판매자) 목록으로 한 번에 조회.
  - 결과를 `(userId, auctionId, type)` 키의 `Set`으로 변환해 조회 없이 멤버십만 확인.
  - IN×IN이라 이론상 무관한 조합까지 매칭될 수 있지만, 반환되는 각 행은 실제 존재하는 알림이라 행 자체의 (userId, auctionId, type)로 키를 만들면 오탐 없음(과다 조회일 뿐 오차 없음).
- `ensureNotification`은 DB 호출 대신 이 Set을 받아 멤버십만 검사하도록 시그니처 변경.

### 3. `recoverOutbidNotifications`

- `NotificationRepository`에 `findByBidIdIn(Collection<Long> bidIds)` 추가.
- outbid 후보 bid들의 id를 모아 한 번에 조회 → 이미 알림 간 bidId의 `Set<Long>` 구성 후 루프에서 멤버십만 검사.
- `type` 조건은 넣지 않음: `bid_id`가 sentinel(0)이 아닌 경우는 설계상 `OUTBID`뿐이라(`Notification` 클래스 상단 주석 참고, 다른 모든 타입은 항상 `NO_BID`로 저장) `bidId`만으로 존재 확인에 충분함.

## PR #371 리뷰 후 추가 반영

리뷰([#371](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/pull/371))에서 `findByTypeAndBidIdIn`이 `notification` 테이블의 어떤 인덱스도 리딩 컬럼으로 타지 못해(기존 인덱스: `user_id`, `(user_id, is_read)`, 유니크 `(user_id, auction_id, type, bid_id)`) 풀 테이블 스캔이 될 수 있다는 지적이 나와서 다음을 추가로 반영함.

- `schema.sql`의 `notification` 테이블에 `INDEX idx_notification_bid_id (bid_id)` 추가.
  - `bid_id`는 `OUTBID`일 때만 실제 값(사실상 유니크)이고 나머지 타입은 전부 0이라, `type` 없이 `bid_id` 단일 컬럼 인덱스만으로 `bid_id IN (...)` 조회가 충분히 seek됨 — 위 3번 설계의 `type` 제거와 같은 이유.
  - 이 프로젝트는 `schema.sql`이 스키마의 source of truth이고 Docker 기동 스크립트가 스키마 diff를 감지하면 기본값(`reset-on-mismatch`)으로 DB를 스냅샷 후 재생성하므로(`docs/DB_SETUP.md`, `backend/scripts/start-server.sh`), 이 변경으로 배포 환경 DB가 초기화될 수 있음 — 확인 후 진행하기로 함.
  - 로컬 개발 DB에도 `ALTER TABLE notification ADD INDEX idx_notification_bid_id (bid_id);`를 직접 반영해 `schema.sql`과 동기화함.
- `NotificationRepository.findByTypeAndBidIdIn(NotificationType, Collection<Long>)` → `findByBidIdIn(Collection<Long>)`으로 시그니처 단순화(위와 동일한 이유로 `type` 파라미터 자체가 불필요).

## 범위

- `notification`, `wishlist` 패키지 리포지토리/서비스 수정 + `notification` 테이블 인덱스 추가. 다른 패키지 변경 없음.
- `NotificationReconciliationServiceTest`를 새 배치 조회 방식에 맞게 갱신.
- 스케줄러/컨트롤러 등 상위 호출부는 시그니처 변경 없음(모두 내부 구현 디테일).
