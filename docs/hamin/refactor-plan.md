# 리팩토링 계획 (진행 중 — 항목 추가될 수 있음)

담당 패키지(`notifications`, `psa`, `upload`, `wishlist`) 범위에서 진행할 리팩토링 항목을 정리한다. 아직 구현 전이며, 항목별로 이슈를 만들어 순서대로 작업할 예정이다.

## 1. NotificationAsyncConfig를 global AsyncConfig로 이동

- 현재: `notification.config.NotificationAsyncConfig`에 `@EnableAsync`가 선언되어 있음.
- 변경: `com.dbidding.global.config.AsyncConfig` 신설(`@Configuration` + `@EnableAsync`), `NotificationAsyncConfig` 삭제.
- 근거: `notes/package-structure.md`에 `global/config/`가 `RedisConfig, AsyncConfig, WebConfig`를 갖는 것으로 이미 설계돼 있고, 기존 코드의 TODO 주석에도 명시돼 있음(`global/config/AsyncConfig`가 생기면 통합하고 삭제).

## 2. batch 패키지의 Notification 복구 관련 파일을 notification 패키지로 이동

현재 `batch` 패키지에 있는 아래 4개 파일을 `notification` 패키지 하위로 옮긴다.

- `batch/config/NotificationRecoverySchedulingConfig.java`
- `batch/scheduler/AuctionResultNotificationRecoveryScheduler.java`
- `batch/scheduler/UrgentNotificationRecoveryScheduler.java`
- `batch/service/NotificationReconciliationService.java`

## 3. notification/wishlist 도메인에서 다른 도메인 호출용 port/adapter 제거 → 직접 Service 호출

현재 consumer-owned port 패턴으로 연결된 아래 케이스들을 port/adapter 없이 해당 도메인의 Service를 직접 호출하는 방식으로 바꾼다.

- `notification/port/WishlistUserFinder` + `notification/adapter/WishlistUserFinderAdapter` → `WishlistService` 직접 호출
- `notification/port/CardNameFinder` + `notification/adapter/CardNameFinderAdapter` → `CardPriceService` 직접 호출
- `wishlist/WishlistCardAdapter`(card 도메인이 정의한 `card.port.CardWishlistPort`를 wishlist가 구현하는 반대 방향 케이스) → card가 `WishlistService`(또는 상응하는 wishlist 쪽 조회 메서드) 직접 호출

**범위 제외**: `upload/adapter/AuctionImageUploadAdapter` — 이 리팩토링 대상이 아님(임하민이 배치한 게 아님).

> 이 문서는 claude의 도움을 받아 작성하였습니다.