# 경매 SSE 실시간 payload 설계

## 1. 목표와 범위

경매 목록과 대시보드가 상태 변경을 실시간으로 전달받을 수 있도록 공개 SSE
연결과 payload 계약을 정의한다.

이번 구현의 소유 범위는 `sse/auction` 패키지다.

- SSE 연결 생성·정리와 heartbeat
- 경매 실시간 payload 3종
- payload 수신 후 연결된 클라이언트 전체 broadcast
- JSON 직렬화 및 연결 관리 테스트

auction·notification 도메인의 이벤트, 서비스, 포트는 변경하지 않는다.
경매 트랜잭션에서 payload를 발행하는 연결 작업도 각 도메인 담당 범위로 남긴다.

## 2. SSE 연결 계약

```http
GET /api/auctions/stream
Accept: text/event-stream
```

응답 헤더는 다음과 같다.

```http
Content-Type: text/event-stream
Cache-Control: no-cache
X-Accel-Buffering: no
```

연결이 생성되면 서버는 `connected` 이벤트와 3초의 재연결 시간을 전달한다.
이후 25초마다 SSE comment 형식의 heartbeat를 보내 중간 프록시가 유휴 연결을
종료하지 않도록 한다.

경매 상태 변경 payload는 다음 이벤트 이름으로 전송한다.

```text
event: auction-updated
data: { ...payload }
```

브라우저 `EventSource`는 연결 종료 시 서버가 전달한 재연결 시간을 기준으로
자동 재연결한다. 현재 구현은 이벤트 replay buffer와 `Last-Event-ID` 복구를
지원하지 않는다.

## 3. 연결 관리

`AuctionSseConnectionManager`는 `CopyOnWriteArraySet<SseEmitter>`로 현재
연결을 관리한다.

1. 새 `SseEmitter`를 집합에 등록한다.
2. completion, timeout, error callback에서 emitter를 제거한다.
3. broadcast 시 현재 emitter 전체에 같은 payload를 전달한다.
4. 전송 중 `IOException` 또는 완료된 emitter 접근이 발생하면 해당 연결만
   제거하고 나머지 전송은 계속한다.

연결 timeout은 30분이다. 클라이언트는 timeout 또는 네트워크 단절 후
`EventSource`의 재연결 동작으로 새 연결을 생성한다.

현재 배포 환경은 단일 애플리케이션 인스턴스를 전제로 한다. emitter와 이벤트
발행이 같은 프로세스에 있으므로 Redis Pub/Sub 같은 인스턴스 간 relay는
사용하지 않는다.

## 4. 공통 payload 계약

payload는 `sse/auction/payload`가 소유한다. auction 도메인의 enum이나 DTO에
의존하지 않고 다음 공통 타입을 사용한다.

```java
public sealed interface AuctionPayload permits
        AuctionCreatedPayload,
        BidPlacedPayload,
        AuctionClosedPayload {

    AuctionPayloadType type();
    Integer auctionId();
    Long auctionVersion();
}
```

이벤트 타입은 세 종류다.

```java
public enum AuctionPayloadType {
    AUCTION_CREATED,
    BID_PLACED,
    AUCTION_CLOSED
}
```

상태도 SSE 계층의 독립 enum으로 유지한다.

```java
public enum AuctionPayloadStatus {
    SCHEDULED,
    OPEN,
    ENDING,
    ENDED,
    CANCELLED,
    FAILED
}
```

Java record 필드는 camelCase를 사용하고 공개 JSON은 snake_case로
직렬화한다. ID는 `Integer`, 가격과 경매 버전은 `Long`, 시각은
`LocalDateTime`이다.

`auctionVersion`은 클라이언트가 같은 경매의 늦게 도착한 이벤트를 구분하는
기준이다. 현재 캐시 버전보다 작거나 같은 payload는 적용하지 않는다.

## 5. payload 3종

### `AuctionCreatedPayload`

```java
public record AuctionCreatedPayload(
        AuctionPayloadType type,
        Integer auctionId,
        Integer cardId,
        String cardName,
        String cardPsaGrade,
        String cardLanguage,
        String cardThumbnailUrl,
        Integer sellerId,
        Long startPrice,
        Long currentPrice,
        Long bidIncrement,
        Integer bidCount,
        LocalDateTime endsAt,
        AuctionPayloadStatus status,
        Long auctionVersion,
        LocalDateTime occurredAt
) implements AuctionPayload {}
```

- `type`은 생성자에서 `AUCTION_CREATED`로 고정한다.
- `cardPsaGrade`, `cardLanguage`, `cardThumbnailUrl`만 nullable이다.
- 생성 직후 `currentPrice == startPrice`, `bidCount == 0`,
  `status == OPEN`이어야 한다.

### `BidPlacedPayload`

```java
public record BidPlacedPayload(
        AuctionPayloadType type,
        Integer auctionId,
        Integer bidderId,
        Integer previousBidderId,
        Long startPrice,
        Long currentPrice,
        Long bidIncrement,
        Integer bidCount,
        LocalDateTime endsAt,
        AuctionPayloadStatus status,
        Long auctionVersion,
        LocalDateTime occurredAt
) implements AuctionPayload {}
```

- `type`은 `BID_PLACED`로 고정한다.
- 최초 입찰이면 `previousBidderId`는 null이다.
- 기존 최고 입찰이 있으면 `previousBidderId`에 상회 입찰된 사용자를 담는다.
- 별도 outbid SSE payload는 만들지 않는다.
- 입찰 이벤트는 기존 목록 항목을 갱신하므로 카드 스냅샷과 판매자 ID를 반복하지
  않는다.
- `bidCount >= 1`이어야 한다.

### `AuctionClosedPayload`

```java
public record AuctionClosedPayload(
        AuctionPayloadType type,
        Integer auctionId,
        Integer cardId,
        String cardName,
        String cardPsaGrade,
        String cardLanguage,
        String cardThumbnailUrl,
        Integer winnerId,
        Integer sellerId,
        Long startPrice,
        Long finalPrice,
        Long bidIncrement,
        Integer bidCount,
        LocalDateTime endsAt,
        AuctionPayloadStatus status,
        Long auctionVersion,
        LocalDateTime closedAt,
        LocalDateTime occurredAt
) implements AuctionPayload {}
```

- `type`은 `AUCTION_CLOSED`로 고정한다.
- `winnerId == null`이면 유찰이다.
- 유찰이어도 `finalPrice`는 화면에 표시할 최종 가격으로 non-null이다.
- 종료 payload의 상태는 `ENDED`다.

## 6. payload 소비 흐름

```text
payload 발행
    → AuctionSseEventListener
    → AuctionSseConnectionManager.broadcast(payload)
    → event: auction-updated
    → 연결된 EventSource 전체 수신
```

`AuctionSseEventListener`는 세 payload를 각각
`@TransactionalEventListener(phase = AFTER_COMMIT)`으로 구독한다. DB
트랜잭션이 롤백된 상태가 클라이언트에 먼저 전달되지 않도록 commit 이후에만
broadcast한다.

리스너는 payload를 다시 조회하거나 변환하지 않는다. 발행자가 렌더링에 필요한
스냅샷을 완성해서 전달하고 SSE 계층은 전달만 담당한다.

## 7. k6 검증 전략

k6의 `k6/x/sse` 확장을 사용해 동시 연결 수와 오류율을 측정한다. 테스트
스크립트는 `backend/src/test/k6/sse/auction-stream.js`에 둔다.

기본 검증 기준:

| 지표 | 기준 |
|---|---|
| SSE 연결 성공률 | 99% 초과 |
| 연결 오류율 | 1% 미만 |
| 계약에 맞지 않는 payload | 0건 |
| 목표 동시 연결 | 단계별 50 → 500 |

로컬 스모크 테스트:

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e VUS=1 \
  -e RAMP_UP=1s \
  -e HOLD=20s \
  -e RAMP_DOWN=1s \
  src/test/k6/sse/auction-stream.js
```

50개 동시 연결:

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e VUS=50 \
  -e HOLD=1m \
  src/test/k6/sse/auction-stream.js
```

500개 스트레스 테스트:

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e VUS=500 \
  -e RAMP_UP=30s \
  -e HOLD=3m \
  -e RAMP_DOWN=30s \
  src/test/k6/sse/auction-stream.js
```

`sse-load-test` 프로필은
`POST /api/auctions/stream/test-events/random-bid`를 활성화한다. 이 API는
DB에서 디버그 사용자 ID `1`이 입찰한 `OPEN` 또는 `ENDING` 경매 중 종료
시각이 남은 경매를 무작위로 읽고, 경매별 메모리 상태에서 가격·입찰 수·버전을
증가시킨 `BID_PLACED` payload를 발행한다. DB 데이터는 변경하지 않는다.
k6는 SSE 연결 부하와 이 API의 일정 속도 호출을 함께 수행한다.

## 8. 테스트 및 완료 기준

- 연결 직후 `connected` 이벤트와 재연결 시간이 전송되어야 한다.
- broadcast 시 모든 활성 emitter가 같은 payload를 받아야 한다.
- 전송에 실패한 emitter만 제거되어야 한다.
- 세 payload type은 호출자가 전달한 값과 관계없이 정해진 값으로 고정되어야
  한다.
- 공개 JSON 필드가 snake_case여야 한다.
- `previousBidderId`와 `winnerId`의 null 계약이 유지되어야 한다.
- SSE 구현과 테스트가 auction·notification 패키지를 import하지 않아야 한다.
- 최종 변경 범위가 `sse/auction`과 해당 SSE 테스트로 제한되어야 한다.
- 프론트는 SSE 수신 시 목록과 대시보드 React Query 캐시를 직접 수정하고,
  REST query invalidation이나 재조회를 실행하지 않아야 한다.

## 9. 후속 과제

- auction 담당 영역에서 트랜잭션 완료 후 SSE payload 발행 연결
- 멀티 인스턴스 전환 시 인스턴스 간 이벤트 relay 검토
- 필요 시 짧은 replay buffer와 `Last-Event-ID` 복구 정책 추가

> 이 문서는 Codex의 도움을 받아 작성하였습니다.
