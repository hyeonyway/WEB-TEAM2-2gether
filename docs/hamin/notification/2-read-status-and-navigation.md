# 읽음 상태, 목록 API 분리, 이동 기능, 인증 전환

담당: D(임하민). [1-entity-and-list.md](1-entity-and-list.md) 구현 완료 후 이어지는 확장 라운드. 실시간 푸시(WebSocket/FCM)는 여전히 P1로 제외하고, 이번 라운드는 아래 4가지로 스코프를 한정한다.

## 스키마 변경 (`schema.sql`)

```sql
CREATE TABLE notification
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    INT          NOT NULL,
    auction_id INT          NOT NULL,
    message    VARCHAR(300) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id),

    INDEX idx_notification_user_id (user_id),
    INDEX idx_notification_user_id_is_read (user_id, is_read)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
```

> 이 블록은 최종 확정본이다. 처음 이 라운드를 시작할 때는 `fk_notification_auction`과 `idx_notification_auction_id`가 있었고 `idx_notification_user_id` 단독 인덱스는 없었는데, 구현 도중 두 가지를 바로잡았다 — 아래 "인덱스/제약 정정" 절 참고.

- `created_at`: 표시용일 뿐 정렬 기준이 아니므로(정렬은 계속 `id desc`) 별도 인덱스를 걸지 않는다. `TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)`는 `users`/`bids`/`wallet_holds` 등 기존 컨벤션과 동일.
- `is_read`: `BOOLEAN NOT NULL DEFAULT FALSE`. 안읽은 알림 조회(`WHERE user_id = ? AND is_read = false`)가 실제 필터 조건이라 `(user_id, is_read)` 복합 인덱스를 추가한다.
- `message`는 `card_metadata.name`(200) + 최장 알림 문구(22자) 대비 여유를 두려고 `VARCHAR(300)`으로 확장했다(초기엔 255였다).
- `cardId`는 이번 라운드에 컬럼으로 추가하지 않는다 — 현재 3개 이벤트(`AuctionCreatedEvent`/`BidOutbidEvent`/`AuctionClosedEvent`) 모두 `auctionId`를 갖고 있어 "경매 상세로 이동"만으로 충분하고, 카드 단독(경매에 안 묶인) 알림 타입이 생기면 그때 추가한다.

### 인덱스/제약 정정

처음엔 `idx_notification_user_id (user_id)` 단독 인덱스를 제거하고 `(user_id, is_read)` 복합 인덱스 하나로 통합했었다 — "최좌측 접두 규칙으로 `user_id` 단독 조회도 커버된다"는 이유였는데, 이건 `WHERE` 조건만 보고 `ORDER BY`를 놓친 판단이었다.

- **전체 조회**(`findByUserIdOrderByIdDesc`, `is_read` 필터 없음)에서 `(user_id, is_read)` 인덱스를 쓰면, InnoDB 입장에서 `user_id`만 고정값이고 `is_read`는 0/1 두 그룹으로 갈리기 때문에 인덱스상 행 순서가 `(is_read, id)` 기준이 된다. 즉 `is_read=0` 그룹 전체(id 순) 다음에 `is_read=1` 그룹 전체(id 순)가 오는 식이라, `ORDER BY id DESC`를 그대로 만족 못 하고 MySQL이 결과를 다시 정렬(filesort)해야 한다 — 손해.
- **안읽음 조회**(`findByUserIdAndIsReadFalseOrderByIdDesc`)는 `user_id`와 `is_read` 둘 다 고정값이라 문제없다 — 남은 인덱스 순서가 그대로 `id` 순이라 filesort가 없다.
- 그래서 단독 `idx_notification_user_id (user_id)`를 다시 추가했다. 전체 조회는 이 인덱스로 `(user_id, id)` 순서를 그대로 얻어 filesort 없이 `ORDER BY id DESC`를 만족하고, 안읽음 조회는 복합 인덱스를 쓴다. 결과적으로 인덱스가 하나 늘지만(쓰기 비용은 소폭 증가), 두 조회 패턴 모두 filesort 없이 처리된다는 이득이 더 크다.

`auction_id`의 FK(`fk_notification_auction`)와 그 인덱스(`idx_notification_auction_id`)도 제거했다 — notification에서 `auctions` 테이블로 조인해서 조회하는 경로가 없고(응답에 필요한 카드 이름은 이미 쓰기 시점에 `message`로 박아둠), FK가 있어도 실제로 참조 무결성을 활용하는 쿼리가 없다. FK 없이 정합성이 깨질 위험(예: 경매 삭제 후 orphan 알림)은 이번 스코프에서는 감수하고, 필요해지면 애플리케이션 레벨에서 다룬다.

### PATCH 응답을 204로 정정

`markAsRead`/`markAllAsRead` 컨트롤러 메서드를 처음엔 `void`로 선언했는데, 이러면 스프링이 기본값인 `200 OK` + 빈 바디로 응답한다. 프론트 `httpClient.request`는 `204`만 빈 바디로 취급하고 그 외엔 전부 `response.json()`을 호출하므로, `200` + 빈 바디는 그대로 파싱 에러가 난다. `WishlistController.remove`(`DELETE`)가 `ResponseEntity.noContent().build()`(204)를 쓰는 것과 동일하게, 두 PATCH도 `ResponseEntity<Void>` + `.noContent().build()`로 바꿨다. `NotificationControllerTest`의 해당 두 테스트도 `status().isNoContent()`로 맞췄다.

`Notification.java`에는 `isRead`(기본값 `false`인 필드), `createdAt`(`LocalDateTime`, `@CreationTimestamp`로 자동 채움) 추가. 상태 변경은 setter 대신 도메인 메서드로:

```java
public void markAsRead() {
    this.isRead = true;
}
```

## 읽음 처리 API

`is_read` 컬럼만 추가하고 바꿀 방법이 없으면 죽은 컬럼이 되므로, 이번 라운드에 같이 넣는다.

| 기능 | 형태 |
|---|---|
| 개별 읽음 처리 | `PATCH /api/notifications/{notificationId}/read` |
| 전체 읽음 처리 | `PATCH /api/notifications/read-all` |

- 개별 처리는 `notificationRepository.findById`로 조회 후 `userId` 소유권을 확인하고(`Wishlist`의 `ResponseStatusException(HttpStatus.CONFLICT, ...)` 패턴과 동일하게, 존재하지 않거나 본인 소유가 아니면 `ResponseStatusException(HttpStatus.NOT_FOUND, ...)`), `notification.markAsRead()` 호출 — `@Transactional` 안에서 더티 체킹으로 저장되므로 별도 `save()` 불필요.
- 전체 처리는 벌크 업데이트로: `@Modifying @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")`. N개를 각각 조회 후 저장하는 방식보다 DB 왕복이 적다.
- 요청 바디가 없는 PATCH라 별도 Request DTO는 만들지 않는다.

## 목록 조회 API 분리 (전체 vs 안읽음)

기존 `GET /api/notifications`에 쿼리 파라미터를 추가하는 방식을 택한다 (별도 엔드포인트 대신) — 같은 리소스를 필터링하는 것뿐이라 엔드포인트를 늘리지 않는 편이 낫다고 판단.

```
GET /api/notifications             // 전체
GET /api/notifications?read=false  // 안읽은 알림만
```

- `read=false`일 때만 안읽음 필터가 적용되고, 그 외(파라미터 없음 포함)는 전체 목록을 반환한다. "읽은 것만" 보는 뷰는 이번 스코프에 없다.
- `NotificationRepository`에 `findByUserIdAndIsReadFalseOrderByIdDesc(Integer userId)` 추가, 기존 `findByUserIdOrderByIdDesc`와 나란히 둔다.
- `NotificationResponse`에 `isRead`, `createdAt` 추가: `{ id, auctionId, message, isRead, createdAt }`.

## 경매/카드 페이지 이동

응답에 이미 있는 `auctionId`를 그대로 활용 — 스키마/API 변경 없음. 프론트에서 알림 항목 전체를 클릭 가능하게 만들어(하이퍼텍스트 스타일, Slack/Gmail류 패턴) `auctionId` 기준으로 경매 상세 페이지로 이동시킨다. 읽음 처리는 클릭 시 함께 트리거하는 흐름을 권장(위 "개별 읽음 처리" API 재사용).

## 인증 전환: `@PathVariable userId` → `@CurrentUser userId`

`global/security/CurrentUser`/`CurrentUserArgumentResolver`/`CurrentUserProvider` 인프라가 이미 있고 `WalletController`가 이미 이 패턴을 쓰고 있어, "Auth 완성 후"가 아니라 이번 라운드에 바로 전환한다.

- 라우트: `/api/users/{userId}/notifications` → `/api/notifications`
- `@PathVariable Integer userId` → `@CurrentUser Integer userId`
- GET 요청에는 body가 없으므로 `NotificationRequestDto`는 만들지 않는다 — 인증은 `@CurrentUser`, 필터는 `@RequestParam Boolean read`로 충분.

**기존 테스트 변경**: `NotificationControllerTest`는 `WalletControllerTest`와 동일한 패턴으로 바꾼다.
- `@MockitoBean private CurrentUserProvider currentUserProvider;` 추가
- `@BeforeEach`에서 `given(currentUserProvider.getCurrentUserId()).willReturn(1);`
- 요청 경로를 `/api/users/1/notifications` → `/api/notifications`로 변경

`@WebMvcTest`는 `WebConfig`(`WebMvcConfigurer`)를 슬라이스에 자동 포함시켜 `CurrentUserArgumentResolver`가 등록되므로, `CurrentUserProvider`만 목으로 채워주면 `@CurrentUser`가 정상 동작한다(Wallet 테스트와 동일). `NotificationServiceTest`는 서비스 시그니처(`Integer userId` 직접 전달)가 그대로라 변경 불필요 — 인증 방식 전환은 컨트롤러 레이어에만 영향을 준다.

## 다음 단계에서 만들 파일

`backend/src/main/java/com/dbidding/notification/`
- `Notification.java` — `isRead`, `createdAt` 필드 및 `markAsRead()` 추가.
- `NotificationRepository.java` — `findByUserIdAndIsReadFalseOrderByIdDesc`, `markAllAsReadByUserId` 추가.
- `NotificationService.java` — `findUnread(userId)`, `markAsRead(userId, notificationId)`, `markAllAsRead(userId)` 추가.
- `NotificationController.java` — `@CurrentUser`로 전환, `read` 쿼리 파라미터 처리, 읽음 처리 엔드포인트 2개 추가.
- `dto/NotificationResponse.java` — `isRead`, `createdAt` 필드 추가.

`backend/src/main/resources/schema.sql`
- `notification` 테이블에 `is_read`, `created_at` 컬럼 추가, `idx_notification_user_id_is_read (user_id, is_read)` 복합 인덱스 추가(단독 `idx_notification_user_id`는 유지 — 위 "인덱스/제약 정정" 참고), `fk_notification_auction`/`idx_notification_auction_id` 제거.

`backend/src/test/java/com/dbidding/notification/`
- 읽음 처리(개별/전체), `read=false` 필터, `@CurrentUser` 전환에 대한 테스트 보강.

## 커밋 단위 (예정)

1. `feat: notification 스키마에 is_read/created_at 컬럼 추가`
2. `feat: 알림 읽음 처리(개별/전체) API 구현`
3. `feat: 알림 목록 조회에 안읽음 필터 추가`
4. `refactor: 알림 API를 @CurrentUser 기반 인증으로 전환`
5. `test: 읽음 처리 및 필터 조회 테스트 작성`

> 이 문서는 claude의 도움을 받아 작성하였습니다.