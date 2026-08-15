# 알림 읽음 처리 복합키 전환 (계획)

refs #505

## 배경

지금 `markAsRead`와 프론트 SSE dedup은 알림의 DB PK인 `id`를 기준으로 동작한다. 이 때문에 `NotificationEventListener`의 알림 저장/push 경로가 "각 유저의 실제 DB row(정확한 id)를 알아야만" push 페이로드를 만들 수 있는 구조로 묶여 있다 — 배치 INSERT 후 재조회(SELECT)를 없애거나, 동일 내용의 push 객체를 여러 유저에게 재사용하는 최적화(#506)를 막고 있다. 이 이슈는 #506을 위한 선행 작업이다.

## 변경 사항

1. `NotificationResponse`에 `bidId` 필드 추가(`NotificationResponse.from`에서 `notification.getBidId()` 반영) — 프론트가 타입 보고 auctionId/bidId 중 뭘 키로 쓸지 판단할 수 있게.
2. `markAsRead`를 숫자 `id` 대신 `(현재 로그인 유저, type, auctionId 또는 bidId)` 복합키로 특정하도록 변경
   - 엔드포인트: `PATCH /api/notifications/{notificationId}/read` → `PATCH /api/notifications/read?type=&auctionId=&bidId=`(bidId 기본값 0)
   - `NotificationService.markAsRead`가 기존 `NotificationRepository.findByUserIdAndAuctionIdAndTypeAndBidId(...)`를 그대로 재사용(신규 리포지토리 메서드 불필요)
   - `type=OUTBID`는 `bidId` 기준, 그 외 타입은 `auctionId` 기준(`bidId`는 sentinel 0)
3. 프론트 SSE dedup(`useNotificationStream.ts`)을 `notification.id` 대신 같은 복합키(`utils/notificationKey.ts`의 `notificationDedupKey`)로 변경
4. 프론트 markAsRead 호출 체인 전체(`NotificationBell.tsx` → `useNotifications.ts` → `notificationMutations.ts` → `notificationApi.ts`)를 숫자 id 대신 복합키(`{type, auctionId, bidId}`) 전달로 변경
5. `NotificationDto`에 `bidId` 필드 추가, mock 데이터/테스트 픽스처 반영

## 이미 준비돼 있어 추가 작업 불필요

- DB 인덱스: `uk_notification_user_auction_type_bid UNIQUE (user_id, auction_id, type, bid_id)` + `idx_notification_bid_id`
- Repository 메서드: `NotificationRepository.findByUserIdAndAuctionIdAndTypeAndBidId(...)`
- `markAllAsRead`: 이미 `NotificationRepository.markAllAsReadByUserId`가 `@Modifying @Query`로 한 번의 UPDATE만 수행 — 유저 요청으로 확인했으나 신규 구현 불필요.

## 영향 범위 밖

- 알림 목록 조회 API(`GET /notifications`, cursor 페이지네이션)는 그대로 `id` 기준 유지.

## 검증

- `./gradlew test --tests "com.dbidding.notification.*"` 통과
- 프론트 `notificationKey`/`notificationApi`/`useNotificationStream`/`NotificationBell`/`NotificationToastStack` 테스트 통과
- `npx tsc --noEmit` 통과
