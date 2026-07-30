# Notification 엔티티 + 목록조회 골격

담당: D(임하민). 이 라운드는 설계 정리만 하고, 코드는 이 문서 피드백 이후 다음 단계에서 작성한다.

## 범위

`feature-api-spec.md` 7.3절 S1 스코프인 "Notification 엔티티 + 목록조회 골격"까지만 다룬다. 실시간 푸시(WebSocket/FCM)는 P1로 제외.

이벤트 발행 쪽(`BidOutbid`/`AuctionClosedEvent`/`AuctionCreatedEvent`)은 auction/bid 패키지가 아직 비어있어 실제로 발행되는 이벤트 자체가 없다. 그래서 이번 라운드는 **실제 이벤트 리스너 없이, Notification 엔티티/레포지토리/목록조회 API + 리스너가 나중에 쓸 내부 저장 헬퍼**까지만 만든다. 리스너 연동은 auction/bid 이벤트가 실제로 발행되기 시작하면 별도로 진행한다.

## 스키마 (`schema.sql`)

```sql
CREATE TABLE notification (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    INT          NOT NULL,
    auction_id INT          NOT NULL,
    message    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notification_auction FOREIGN KEY (auction_id) REFERENCES auctions (id),
    INDEX idx_notification_user_id (user_id),
    INDEX idx_notification_auction_id (auction_id)
)
```

- `created_at`, `is_read` 컬럼이 없다 → "언제 생겼는지/읽었는지"는 스키마 범위 밖. 정렬은 `id desc`(BIGINT auto-increment라 생성 순서와 동일)로 대체한다. (→ [2-read-status-and-navigation.md](2-read-status-and-navigation.md)에서 추가)
- `package-structure.md`의 PK 규칙상 `notification`은 예외적으로 `id`가 `Long`(BIGINT)이다 — Wishlist(`Integer`)와 다르다.
- `auction_id`는 FK로 `auctions`를 향하지만 Auction 엔티티는 다른 담당 소관 → Wishlist와 동일하게 `Integer auctionId` 필드만 갖고 Auction 엔티티는 참조하지 않는다.

## API / 헬퍼

| 기능 | 형태 | 설명 |
|---|---|---|
| 알림 저장 | `NotificationService.save(Integer userId, Integer auctionId, String message)` | 외부 API 아님 — notification 패키지 내부(이벤트 리스너)에서만 호출하는 내부 헬퍼 |
| 알림 목록 조회 | `GET /api/users/{userId}/notifications` | `[{ id, auctionId, message }]`, `id desc` 정렬 |

- 인증 미들웨어가 없어 Wishlist와 동일하게 `userId`는 경로 변수로 받는다 (추후 `@CurrentUser`로 교체 예정, TODO 주석 남길 것).
- 등록/삭제 API는 없다 — 알림 생성은 오직 이벤트 리스너를 통해서만 발생한다 (사용자가 직접 POST로 알림을 만들 수 없음). 여기가 Wishlist와 가장 다른 지점이다.
- 읽음 처리 API는 스키마에 컬럼이 없어 이번 범위에서 제외한다.

## 설계 결정: 호출 방식 — Spring 이벤트(pub/sub) + `@Async` 리스너

메서드 직접 호출 대신 **Spring 이벤트**를 쓴다. 이유: 알림을 만들어야 하는 "사실"(경매 생성, 상회 입찰, 낙찰 등)의 소비자가 notification 하나뿐이 아닐 수 있다(대시보드 캐시 무효화, 랭킹 갱신 등). 발행자(auction/bid)가 소비자마다 한 줄씩 직접 호출하게 하면 소비자가 늘어날수록 발행자 코드가 계속 늘어나는 강결합이 되므로, "이벤트 하나 던지면 관심 있는 쪽이 알아서 구독"하는 구조로 간다.

**등장인물**

1. **이벤트 클래스** — 발행자(auction)가 소유. `AuctionCreatedEvent { auctionId, cardId, sellerId }` — 이미 `feature-api-spec.md` 5.3에 이 payload로 합의돼 있다. notification은 이 클래스 하나만 알면 되고, auction의 나머지 코드는 몰라도 된다.
2. **발행** — `AuctionService`(auction 담당 소유, `@Transactional` 메서드)가 경매 저장 로직 끝에서 `applicationEventPublisher.publishEvent(new AuctionCreatedEvent(...))`를 호출. 이 호출은 즉시 리턴된다. auction 쪽은 notification 패키지를 import할 필요가 없다.
3. **구독** — notification 패키지의 `NotificationEventListener`(`@Component`)가 `AuctionCreatedEvent`를 파라미터로 받는 메서드에 `@TransactionalEventListener`를 붙여둔다. Spring이 기동 시점에 "이 이벤트 타입엔 이 메서드"로 등록해두고, 발행될 때마다 자동으로 호출한다 — 폴링이나 큐를 직접 만드는 게 아니다.
4. **`@Async` + `@TransactionalEventListener(phase = AFTER_COMMIT)`** 조합:
   - `AFTER_COMMIT`: `AuctionService`의 트랜잭션이 **실제로 커밋된 뒤에만** 리스너가 실행된다 — 커밋 전 데이터를 조회해서 못 찾는 문제를 프레임워크가 막아준다 (직접 호출 방식이었으면 사람이 호출 위치를 신경 써야 했던 부분).
   - `@Async`: 리스너가 별도 스레드에서 돌기 때문에 발행자는 리스너가 끝나길 기다리지 않는다.

```java
// notification 패키지
@Component
public class NotificationEventListener {

    private final WishlistUserFinder wishlistUserFinder; // notification 소유 Port, 아래 "wishlist 의존성" 참고
    private final CardService cardService; // card 패키지, 아래 "카드 이름 의존성" 참고
    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionCreated(AuctionCreatedEvent event) {
        String cardName = cardService.getName(event.cardId());
        List<Integer> userIds = wishlistUserFinder.findUserIdsByCardId(event.cardId());
        String message = cardName + " 카드의 경매가 등록되었습니다.";
        for (Integer userId : userIds) {
            notificationService.save(userId, event.auctionId(), message);
        }
    }
}
```

`NotificationService`는 `save()`/`findAll()`만 갖는 얇은 데이터 레이어로 남기고, "이벤트 반응 + 문구 조립 + fan-out" 같은 오케스트레이션은 전부 `NotificationEventListener`가 맡는다 — 컨트롤러가 쓰는 `findAll()`이 비동기/이벤트 관심사와 안 섞이게 하기 위함.

**주의**: `@TransactionalEventListener`는 발행 시점에 활성 트랜잭션이 있어야 동작한다(`AuctionService`가 `@Transactional`이어야 함). 트랜잭션 없이 발행하면 기본 설정상 리스너가 조용히 실행되지 않는다(`fallbackExecution=true`로 바꿀 수 있음). 경매 생성은 당연히 트랜잭션 안에서 일어날 거라 문제는 없을 것으로 예상.

`@EnableAsync` 설정은 `global/config/AsyncConfig`에 둔다(패키지 구조안에 이미 예정된 이름). `@Async` 메서드에서 예외가 나면 기본적으로 호출자에게 전파되지 않고 로그만 남기므로, `AsyncConfigurer`로 예외 핸들러를 등록해 알림 실패가 조용히 묻히지 않게 해야 한다(스트레치 — 이번 라운드 범위 밖).

### 열려있는 의존성 1: 카드 이름 조회 (notification → card)

알림 목록에서 "OO 카드의 경매가 등록되었습니다"처럼 카드 이름을 보여주려면, 그 이름을 어디선가 조회해야 한다. **A안(채택)**: `NotificationEventListener`가 알림을 만드는 시점(쓰기 시점)에 `CardService.getName(cardId)`를 호출해서 이름을 알아내고, 완성된 문자열을 `message`에 미리 박아 저장한다.

- 읽기 시점(`GET /api/users/{userId}/notifications`)은 card 패키지를 전혀 몰라도 된다 — `message` 컬럼에 이미 완성된 텍스트가 들어있으므로 조회 API는 지금 계획대로(`{id, auctionId, message}`) 유지된다.
- 쓰기 시점(리스너 안)에서만 notification → card로 한 방향 의존성이 생긴다. Repository/Entity 직접 참조가 아니라 `CardService`의 public 메서드 호출이라 패키지 규칙 위반은 아니다.
- card 패키지도 아직 비어있어(정세호 담당, 미착수), `CardService.getName(cardId)` 같은 조회 메서드가 실제로 생기기 전까지는 이 부분도 열린 의존성으로 남겨둔다.

### 열려있는 의존성 2: `wishlistService.findUserIdsByCardId(cardId)`

같은 담당자(D) 소관이라 지금 바로 만들 수 있다 — `WishlistRepository`에 `findByCardId(Integer cardId)`를 derived query로 추가하고 userId만 뽑아 반환한다. 이번 라운드에 포함.

**구현 시 변경**: 같은 담당자 소관이라도 `NotificationEventListener`가 `WishlistService`를 직접 참조하면 도메인 간 결합이 생긴다(코드리뷰 지적 반영). 그래서 notification이 소유하는 `notification/port/WishlistUserFinder` 인터페이스를 두고, `notification/adapter/WishlistUserFinderAdapter`가 `WishlistService`를 감싸서 구현하는 방식으로 바꿨다. 리스너는 Port와 ID만 참조한다.

이번 라운드는 auction/card 쪽 서비스가 아직 없어 `NotificationEventListener`와 실제 이벤트 클래스는 만들지 않고, 위 구조 결정만 문서화해둔다. auction이 `AuctionCreatedEvent`를 실제로 발행하기 시작하고 card가 `getName(cardId)`를 열어주면, 그때 리스너를 실제로 구현한다.

## 다음 단계에서 만들 파일 (피드백 반영 후)

이번 라운드(auction/card 의존성 없이 바로 가능한 범위):

`backend/src/main/java/com/dbidding/notification/`
- `Notification.java` — `id`(Long, PK), `userId`(Integer), `auctionId`(Integer), `message`(String). Lombok `@Getter`, `@NoArgsConstructor(access = PROTECTED)` + 정적 팩토리 `Notification.of(userId, auctionId, message)`.
- `NotificationRepository.java` — `JpaRepository<Notification, Long>` + `findByUserIdOrderByIdDesc(Integer userId)`.
- `NotificationService.java` — `save(userId, auctionId, message)`(내부 헬퍼), `findAll(userId)`(목록 조회). 이벤트/비동기 관심사는 여기 안 넣는다.
- `NotificationController.java` — `GET /api/users/{userId}/notifications` 단일 엔드포인트.
- `dto/NotificationResponse.java` — `{ id, auctionId, message }`.

`backend/src/main/java/com/dbidding/wishlist/`
- `WishlistRepository`에 `findByCardId(Integer cardId)` 추가.
- `WishlistService`에 `findUserIdsByCardId(Integer cardId)` 추가 — 나중에 `NotificationEventListener`가 호출할 진입점.

`backend/src/test/java/com/dbidding/notification/`
- `NotificationServiceTest.java`, `NotificationControllerTest.java` — Wishlist 테스트와 동일한 패턴.

auction이 `AuctionCreatedEvent`를 실제로 발행하고 card가 `getName(cardId)`를 열어준 뒤 추가할 것 (지금은 보류):
- `auction/event/AuctionCreatedEvent.java` (auction 담당 소유)
- `notification/NotificationEventListener.java` — `@Async @TransactionalEventListener(AFTER_COMMIT)`
- `global/config/AsyncConfig`(`@EnableAsync`, 필요시 `AsyncConfigurer`로 예외 핸들러)

## 커밋 단위 (예정)

1. `feat: Notification 엔티티 및 레포지토리 작성`
2. `feat: 알림 목록 조회 API 구현`
3. `feat: Wishlist에 카드별 찜한 유저 조회 기능 추가`
4. `test: Notification 서비스/컨트롤러 단위 테스트 작성`

> 이 문서는 claude의 도움을 받아 작성하였습니다.