# non-urgent 상회입찰 복구에서 auctions join 제거

이슈 #416.

## 배경

`recoverOpenOutbidNotifications`(non-urgent, 5분 주기)가 `bidRepository.findAuctionIdsByStatusAndAuctionStatus(LEADING, OPEN)`로 `bids ⋈ auctions`를 조인한다. 이 join의 유일한 목적은 "ENDING은 urgent(90초 주기)가 처리하니 non-urgent에서는 제외하자"는 것뿐이다.

`notification`의 `(user_id, auction_id, type, bid_id)` 유니크 제약 + `INSERT IGNORE`(#414) 덕분에, 같은 경매를 urgent/non-urgent 양쪽이 중복 처리해도 안전하다. 즉 non-urgent가 ENDING까지 같이 처리해도(재처리든 백업이든) 문제가 없어졌으므로, "ENDING 제외" 필터 자체가 불필요해졌고 그 필터를 위한 join도 같이 사라진다.

## 변경

### `BidRepository`

`findAuctionIdsByStatusAndAuctionStatus(BidStatus, AuctionStatus)` 삭제(다른 사용처 없음, `NotificationReconciliationService`와 그 테스트 전용). 대신 join 없는 메서드 추가:

```java
@Query("select b.auction.id from Bid b where b.status = :bidStatus")
List<Integer> findAuctionIdsByStatus(@Param("bidStatus") BidStatus bidStatus);
```

`AuctionRepository.findIdsByStatus(AuctionStatus)`와 이름 대칭.

### `NotificationReconciliationService.recoverOpenOutbidNotifications`

```java
public void recoverOpenOutbidNotifications(Instant windowStart) {
    Set<Integer> candidateAuctionIds = new LinkedHashSet<>(
            bidRepository.findAuctionIdsByStatus(BidStatus.LEADING));
    recoverOutbidNotificationsForCandidates(candidateAuctionIds, windowStart);
}
```

Javadoc을 "OPEN만 좁혀서 후보를 뽑는다"에서 "ENDING 제외를 안 하므로 결과적으로 ENDING도 같이 처리되고, urgent가 이미 처리했으면 INSERT IGNORE로 걸러진다"는 취지로 갱신. 메서드/스케줄러 이름(`recoverOpenOutbidNotifications`, `NonUrgentNotificationRecoveryScheduler`)은 그대로 유지 — 이슈 스코프가 쿼리 변경이지 개명이 아니고, non-urgent 스케줄러가 여전히 "OPEN을 반드시 커버해야 하는 담당"이라는 의미는 유효하다(ENDING을 추가로 잡는 건 부가 효과).

`recoverEndingOutbidNotifications`(urgent)는 변경 없음.

## 테스트

- `BidRepositoryTest`: `findAuctionIdsByStatusAndAuctionStatus` 관련 두 테스트를 `findAuctionIdsByStatus`로 교체 — "LEADING이면 경매 상태 무관하게 조회된다"는 걸 보이는 테스트로 재작성(기존 "경매 상태가 일치하지 않으면 조회되지 않는다" 테스트는 이제 반대로 "ENDING이어도 조회된다"가 맞는 동작이라 뒤집어야 함).
- `NotificationReconciliationServiceTest`: `findAuctionIdsByStatusAndAuctionStatus` mock/verify를 `findAuctionIdsByStatus(LEADING)`로 교체. "ENDING 경매는 bids 조인 없이 auctionRepository로 직접 조회한다" 테스트의 `verify(bidRepository, never())` 대상도 새 메서드명으로 변경.
