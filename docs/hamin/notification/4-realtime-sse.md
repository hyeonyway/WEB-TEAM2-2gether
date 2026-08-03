# 알림 실시간 푸시 (SSE) 연동 계획

담당: D(임하민). [1-entity-and-list.md](1-entity-and-list.md) → [2-read-status-and-navigation.md](2-read-status-and-navigation.md) → [3-frontend-integration-plan.md](3-frontend-integration-plan.md)에 이어지는 라운드. 지금까지는 알림을 DB에 쌓기만 했고 프론트는 드로어를 열 때마다 재조회했다. 이번 라운드는 auction에서 실제로 발생하는 이벤트를 받아 (1) 알림을 저장하고 (2) 그 자리에서 웹 UI로 실시간 푸시하는 것까지 다룬다.

이 문서는 1차 설계 → 피드백 반영을 마친 버전이다. 각 절 끝에 결정 배경을 남긴다.

## 범위

- auction 도메인이 발행할 예정인 `AuctionCreated`/`BidPlaced`/`AuctionClosed` 이벤트를 구독해 알림을 저장하는 흐름 정리.
- 유저 개인별 SSE 연결(`/api/users/{userId}/notifications/stream`, 티켓 인증) 구현 계획.
- 알림이 저장되는 순간 해당 유저의 SSE 연결로 바로 push하는 흐름 설계.

범위 밖: 프론트 `EventSource` 훅 구현(다음 라운드), 알림 설정/타입별 on-off, `Notification` 엔티티/스키마 변경(이번 라운드는 변경 없음 — 아래 결정 5).

## 배경: 지금 뭐가 막혀 있고 왜 그런가

- `auction`은 지금 `AuctionEventPort.AuctionEvent`(type/auctionId/actorId/amount/occurredAt만 있는 얇은 단일 record)만 발행한다. 리치한 필드(카드 이름, 판매자, 낙찰가 등)를 담은 `AuctionCreated`/`BidPlaced`/`AuctionClosed` 세 이벤트로 나누는 작업은 auction 담당(이은기) 몫이고 아직 코드에 반영되지 않았다.
- `notification/event/*`(`AuctionCreatedEvent`/`BidOutbidEvent`/`AuctionClosedEvent`)는 "auction이 실제 이벤트를 만들면 그쪽으로 교체하고 삭제할 임시 계약"으로 주석이 달려 있다. 이번 라운드도 이 전략을 유지한다(아래 결정 1).

## 결정 1: 이벤트 계약은 여전히 auction 발행 예정 클래스의 임시 대체물이다

`module-interfaces.md`의 원칙("도메인 이벤트는 발행자가 shape 소유")과 기존 `notification/event/*`의 TODO 주석 그대로, notification이 이벤트 클래스를 영구 소유하지 않는다. auction(이은기)이 실제 `auction/event/AuctionCreatedEvent`/`BidPlacedEvent`/`AuctionClosedEvent`를 만들면 그 클래스로 교체하고 `notification/event/*`는 삭제한다 — 지금과 동일한 전략을 계속 쓴다.

다만 이번에 `BidOutbidEvent`가 `BidPlacedEvent`로 합쳐지므로(결정 2), 나중에 1:1로 갈아끼우기 쉽도록 **필드 이름/타입을 auction 쪽과 미리 맞춰두는 게 중요**하다. auction이 실제 클래스를 만들기 전에 아래 shape(특히 `BidPlacedEvent`의 `previousBidderId` 필드명)를 이은기와 확인해두는 걸 권장한다 — 맞으면 나중에 import 한 줄만 바뀌고, 안 맞으면 그때 가서 필드 매핑 코드를 새로 짜야 한다.

```java
// notification/event/*.java — 여전히 "auction 실제 클래스로 교체 예정" 임시 계약
// TODO: auction 담당(이은기)이 실제 이벤트 클래스를 auction/event에 만들면 그쪽 클래스로 교체하고 이 파일은 삭제.
```

## 결정 2: `BidOutbidEvent`를 없애고 `BidPlacedEvent`에 흡수한다

auction은 `bidPlaced` 하나로 상회 입찰 정보까지 다 담아 보낼 예정이다(별도 outbid 이벤트 없음). `sse/auction/payload`도 이미 같은 선택을 했다 — "별도 outbid SSE payload는 만들지 않는다. `previousBidderId`가 있으면 상회 입찰된 사용자"로 취급한다(`경매-SSE-실시간-payload-설계.md` 5절). notification도 동일하게 간다.

```java
// notification/event/BidPlacedEvent.java (신규, BidOutbidEvent 대체)
public record BidPlacedEvent(
        Integer auctionId,
        Integer cardId,
        String cardName,
        Integer bidderId,
        Integer previousBidderId  // null이면 최초 입찰(상회 입찰 알림 없음)
) {
}
```

`notification/event/BidOutbidEvent.java`는 삭제한다. `AuctionCreatedEvent`/`AuctionClosedEvent`는 필드 그대로 유지(이미 지금 리스너가 쓰는 값과 일치).

`NotificationEventListener` 변경:

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleBidPlaced(BidPlacedEvent event) {
    if (event.previousBidderId() == null) {
        return; // 최초 입찰은 상회 입찰 대상이 없음
    }
    String message = event.cardName() + " 카드 경매에 상회 입찰이 발생했습니다.";
    Notification saved = notificationService.save(event.previousBidderId(), event.auctionId(), message);
    notificationSseConnectionManager.push(event.previousBidderId(), NotificationResponse.from(saved));
}
```

`handleAuctionCreated`/`handleAuctionClosed`도 `notificationService.save(...)` 호출 직후 같은 방식으로 `notificationSseConnectionManager.push(userId, NotificationResponse.from(saved))`를 추가한다(결정 4). `NotificationService.save()`가 이미 `Notification`을 반환하므로 추가 조회 없이 바로 DTO 변환이 가능하다.

## 결정 3: SSE push payload는 기존 `NotificationResponse`를 그대로 쓴다

REST 목록 응답용 `NotificationResponse`(`id`/`auctionId`/`message`/`isRead`/`createdAt`)가 이미 프론트 팝업/목록에 필요한 필드를 전부 담고 있다. 굳이 몸통이 같은 DTO를 하나 더 만들 이유가 없어 별도 SSE 전용 타입은 만들지 않고 `NotificationResponse.from(notification)`을 SSE push에도 그대로 쓴다. 나중에 SSE payload에만 필요한 필드가 실제로 생기면 그때 갈라낸다.

## 결정 4: 저장과 push는 같은 리스너 메서드 안에서 순차 처리한다

`notificationService.save(...)` → `notificationSseConnectionManager.push(...)`를 한 트랜잭션 이벤트 핸들러 메서드 안에서 순서대로 부른다(위 결정 2 코드 예시). 저장을 위한 내부 이벤트(`NotificationCreatedEvent`)를 한 번 더 발행해 push 책임을 분리하는 방안도 검토했지만, 지금 규모에서는 오버엔지니어링이라 판단해 채택하지 않는다.

push 실패(연결 없음/전송 에러)는 알림 저장 자체를 실패시키지 않는다 — `save()`가 먼저 커밋 경로를 타고, `push()`는 실시간성이 없어도 REST 목록 조회로 여전히 확인 가능한 best-effort 부가 기능이기 때문이다.

## 결정 5: `Notification` 엔티티/스키마는 이번 라운드에 변경하지 않는다

`schema.sql`과 `Notification.java`는 이미 필드가 정확히 일치한다(`id/user_id/auction_id/message/is_read/created_at`). 새 컬럼(예: 알림 종류 구분용 `type`) 추가 요구가 없는 걸로 확인했으므로 이번 라운드는 엔티티/스키마를 그대로 둔다.

## 결정 6: 유저별 SSE 연결 관리 — 로컬 `Map<userId, Set<SseEmitter>>`

이미 팀이 확정한 방향과 정확히 일치한다(`docs/hyeonmoon/realtime/1-sse-architecture.md` 2절/5절): 단일 인스턴스 환경이라 Redis Pub/Sub 등 인스턴스 간 릴레이가 필요 없고, "정세호/임하민이 각자 패키지 안에서 로컬 emitter 레지스트리만 관리"하기로 이미 회의에서 정리돼 있다. 다른 대안(Redis Pub/Sub, 공용 `global.realtime` 모듈)은 이미 검토 후 기각된 상태라 다시 검토하지 않는다.

기존 `sse/auction/AuctionSseConnectionManager`(브로드캐스트, `Set<SseEmitter>`)와 같은 뼈대를 쓰되 키를 `userId`로 추가한다:

```java
// notification/NotificationSseConnectionManager.java (신규)
@Component
public class NotificationSseConnectionManager {
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long RECONNECT_TIME_MILLIS = 3_000L;

    private final ConcurrentMap<Integer, Set<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    public SseEmitter connect(Integer userId) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MILLIS);
        Set<SseEmitter> emitters = emittersByUserId.computeIfAbsent(
                userId, id -> new CopyOnWriteArraySet<>());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> removeAndComplete(userId, emitter));
        emitter.onError(error -> removeAndComplete(userId, emitter));

        send(emitter, SseEmitter.event().name("connected").reconnectTime(RECONNECT_TIME_MILLIS).data("connected"));
        return emitter;
    }

    public void push(Integer userId, NotificationResponse payload) {
        Set<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null) {
            return; // 접속 중인 탭 없음 — REST 목록 조회로 나중에 확인
        }
        emitters.forEach(emitter -> send(emitter,
                SseEmitter.event().name("notification-created").data(payload)));
    }

    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        emittersByUserId.values().forEach(emitters ->
                emitters.forEach(emitter -> send(emitter, SseEmitter.event().comment("heartbeat"))));
    }

    // send/removeAndComplete/remove: AuctionSseConnectionManager와 동일한 패턴
    //   (emittersByUserId에서 빈 Set은 정리해 맵이 무한히 커지지 않게 한다)
}
```

`AuctionSseConnectionManager`와 다른 점은 딱 하나: 브로드캐스트가 아니라 특정 `userId` 소유 emitter에만 보낸다는 것. 한 유저가 탭을 여러 개 열면(`Set`) 전부에게 push한다. 빈 `Set`은 emitter가 모두 사라지면 맵에서 제거해 메모리 누수를 막는다(브로드캐스트 매니저에는 없던 정리 로직 — 유저 수만큼 키가 늘어날 수 있으니 필요).

## 결정 7: 인증 — 이미 있는 `SseTicketAuthFilter` 경로를 그대로 쓴다

`global/security/SseTicketAuthFilter`에 `/api/users/{userId}/notifications/stream`이 이미 티켓 인증 대상 경로로 등록돼 있다(`PERSONALIZED_SSE_PATHS`). `docs/hyeonmoon/auth/5-current-user-and-sse-auth.md`/`docs/hyeonmoon/realtime/README.md`에 이미 이 경로와 컨트롤러 계약이 합의돼 있으므로 새로 설계할 부분은 없고 그대로 맞춰 구현만 하면 된다.

```java
// notification/NotificationSseController.java (신규)
@RestController
@RequiredArgsConstructor
public class NotificationSseController {
    private final NotificationSseConnectionManager connectionManager;

    @GetMapping(value = "/api/users/{userId}/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable Integer userId,
            @CurrentUser Integer currentUserId,
            HttpServletResponse response
    ) {
        if (!userId.equals(currentUserId)) {
            throw new UnauthorizedException();
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return connectionManager.connect(currentUserId);
    }
}
```

- `TicketProvider`를 직접 주입하지 않는다 — `SseTicketAuthFilter`가 이미 검증해서 `@CurrentUser`로 꺼내 쓰게 해준다(문서 원칙: "대시보드/알림 컨트롤러는 `TicketProvider`를 직접 호출하지 않는다").
- 경로의 `{userId}`는 인증 근거가 아니라 라우팅 값일 뿐이라는 게 팀 합의이므로, `@CurrentUser`와 값이 다르면 `UnauthorizedException`(401)으로 끊는다 — `SseTicketAuthFilter` 자체가 JWT/티켓 사용자 불일치 시 하는 처리와 동일한 톤.
- 프론트는 `POST /api/sse/tickets`로 티켓을 먼저 받고 `EventSource(".../notifications/stream?ticket=...")`로 연결해야 한다(SSE는 커스텀 헤더를 못 실음) — 이 부분은 다음 라운드(프론트 훅) 몫이라 여기선 계약만 확인.

## 변경/신규 파일

`backend/src/main/java/com/dbidding/notification/`
- `event/BidPlacedEvent.java` — 신규, `BidOutbidEvent.java` 대체(삭제). TODO 주석 유지(결정 1).
- `event/AuctionCreatedEvent.java`, `event/AuctionClosedEvent.java` — 유지, 그대로.
- `NotificationEventListener.java` — `handleBidOutbid` → `handleBidPlaced`로 교체, 세 핸들러 모두 저장 직후 `notificationSseConnectionManager.push(...)` 추가.
- `NotificationSseConnectionManager.java` — 신규.
- `NotificationSseController.java` — 신규.

`backend/src/test/java/com/dbidding/notification/`
- `NotificationSseConnectionManagerTest.java` — connect/push/heartbeat/실패 시 emitter 정리, 유저별 격리(다른 유저에게는 안 감) 검증. `AuctionSseConnectionManagerTest` 패턴 재사용.
- `NotificationSseControllerTest.java` — `AuctionSseControllerTest`/`SseTicketCurrentUserWebMvcTest` 패턴, `userId` 불일치 시 401 검증.
- `NotificationEventListenerTest.java` — `handleBidPlaced`의 `previousBidderId == null` 분기, push 호출 검증 갱신.

변경 없음: `Notification.java`, `NotificationRepository.java`, `NotificationService.java`, `NotificationResponse.java`(SSE push에도 그대로 재사용, 결정 3), `schema.sql`(결정 5), `global/security/*`(경로가 이미 반영돼 있음).

## 커밋 단위 (예정)

1. `refactor: BidOutbidEvent를 BidPlacedEvent로 통합`
2. `feat: 유저별 알림 SSE 연결 관리자(NotificationSseConnectionManager) 구현`
3. `feat: 알림 SSE 스트림 컨트롤러 및 티켓 인증 연동`
4. `feat: 알림 생성 시 SSE push 연결(NotificationResponse 재사용)`
5. `test: 알림 SSE 연결 관리자/컨트롤러/리스너 테스트 작성`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
