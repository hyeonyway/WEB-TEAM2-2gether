# 경매 SSE 선택 구독과 개인화 지갑 동기화 설계

**상태:** 제안
**대상:** `AuctionSseConnectionManager`, 경매 목록·검색·상세 프론트 훅, 지갑 실시간 동기화
**선행 문서:** [SSE 아키텍처 개요](1-sse-architecture.md), [SSE Payload 사전 직렬화](2-sse-payload-pre-serialization.md)

## 1. 배경

현재 공개 경매 SSE는 `GET /api/auctions/stream` 연결 하나에 모든 경매 이벤트를
broadcast한다. 따라서 경매 A의 입찰 이벤트도 경매 B를 보거나, 전혀 다른 목록을
보는 모든 연결에 전송된다.

현재 프론트의 `useAuctionStream`은 이 전역 연결을 공유하고, 로그인 사용자의
`useAuctionWalletSync`는 자신이 입찰자 또는 이전 선두 입찰자인 이벤트를 받으면
지갑 조회를 무효화한다. 즉 지갑 헤더를 최신화하려고 전역 경매 이벤트 전체를
받는 구조다.

경매 수와 SSE 연결 수가 늘면 이 구조의 fan-out 비용은 이벤트 수에 전체 연결 수가
곱해진다. 이번 변경은 공개 경매 이벤트를 화면이 실제로 관찰하는 경매에만 보내고,
어느 화면에서나 필요한 지갑 상태는 별도 개인화 SSE로 전달한다.

## 2. 목표와 범위

### 목표

- 경매 이벤트의 로컬 emitter 전송 대상을 해당 `auctionId` 구독 연결로 제한한다.
- 목록·검색 화면은 viewport 기준 최대 16개 경매만 구독한다.
- 상세 화면은 해당 경매 1개만 구독한다.
- 로그인 사용자의 지갑 잔액·홀드 변화는 화면의 경매 구독 여부와 무관하게
  개인화 SSE로 갱신한다.
- Redis Pub/Sub 기반 다중 인스턴스 릴레이와 SSE payload 형식은 유지한다.
- SSE 유실·재연결 사이의 상태는 기존 REST 조회로 복구한다.

### 범위 제외

- 입찰 DB 락, 입찰 순서, 지갑 홀드·해제·정산 규칙 변경
- Redis Streams의 입찰/지갑 projection 계약 변경
- Redis 채널을 경매별로 동적 생성·구독하는 구조
- 과거 이벤트 replay 또는 영속 이벤트 로그 도입
- 알림 inbox의 전달 방식 변경

## 3. 핵심 결정

### 3.1 공개 경매 SSE는 `auctionIds` 집합을 구독한다

공개 스트림은 경매 ID 집합을 URL query로 받는다.

```http
GET /api/auctions/stream?auctionIds=42,57,81
Accept: text/event-stream
```

- 경매 상세는 ID 하나로 연결한다. 예: `auctionIds=42`.
- 목록·검색은 현재 viewport에 보이는 카드와 앞뒤 여유 카드의 ID를 연결한다.
- `auctionIds`는 양의 정수만 허용하고, 중복을 제거한다.
- 서버는 연결당 최대 16개까지만 허용한다. 누락·빈 값·초과 값은 `400 Bad Request`로
  거절한다. 전역 broadcast를 우회적으로 되살리지 않기 위한 안전장치다.
- 공개 시세이므로 이 endpoint 자체는 인증하지 않는다. 구독 ID는 권한 판단의 근거가
  아니며, 비공개 경매 정책이 도입되면 별도 조회 권한 검증을 추가한다.

`EventSource`는 연결 이후 request body나 임의 메시지를 서버로 보낼 수 없다. 따라서
구독 집합 변경은 별도 subscribe API가 아니라 **기존 연결을 닫고 새 query URL로
재연결**하는 방식으로 처리한다. 서버 연결 ID를 발급해 상태 변경 API를 두면 SSE
단방향 모델을 우회하고 정리·경합 처리가 복잡해지므로 채택하지 않는다.

### 3.2 서버는 경매 ID별 emitter 인덱스를 유지한다

`AuctionSseConnectionManager`는 단일 `Set<SseEmitter>` 대신 다음 두 인덱스를
유지한다.

```text
auctionId -> Set<SseEmitter>       이벤트 대상 조회
emitter   -> Set<auctionId>        completion/timeout/error 시 정리
```

연결 등록 시 전달받은 모든 `auctionIds`에 같은 emitter를 등록한다. 이벤트 수신 시
`payload.auctionId()`로 emitter 집합을 한 번 조회하고, 그 집합에만 기존 dispatcher로
전송한다. 같은 emitter가 한 경매 ID에 중복 등록되지 않도록 집합을 사용한다.

```text
BidPlaced(auctionId=42)
  -> Redis Pub/Sub 수신
  -> local emittersByAuctionId.get(42)
  -> 그 emitter들만 fan-out
```

completion, timeout, error, 전송 실패는 emitter가 가진 전체 구독 ID에서 함께 제거한다.
어떤 경매의 emitter 집합이 비면 map key도 제거한다. heartbeat와 전체 연결 종료는
`emitter -> ids`의 emitter 집합을 기준으로 순회해 같은 연결에 heartbeat를 중복 전송하지
않는다.

기존처럼 payload의 `publishedAt` 확정과 JSON 직렬화는 fan-out 전에 정확히 한 번만
수행한다. Redis subscriber는 단일 채널 메시지를 모든 앱 인스턴스에서 한 번
역직렬화할 수 있지만, 각 인스턴스는 자신이 보유한 해당 경매 연결에만 네트워크
전송한다.

### 3.3 목록과 검색은 viewport 주변 15개를 계산한다

프론트는 카드의 표시 순서를 유지하고 `IntersectionObserver`로 viewport에 실제로
교차한 첫·마지막 카드 인덱스를 추적한다. 구독 집합은 그 범위의 앞 3개와 뒤 3개를
포함하도록 계산한다.

```text
현재 보이는 카드: index 12 .. 20 (최대 9개)
구독 범위:          index  9 .. 23 (최대 15개)
```

이 방식은 화면 폭·카드 높이에 따라 달라지는 pixel `rootMargin`만 쓰는 것보다
"카드 3개 앞뒤" 정책을 정확히 보장한다. 새 검색 결과·필터·정렬·페이지 데이터가
들어오면 이전 observer를 정리하고 새 카드 목록으로 다시 관찰한다.

구독 ID는 정렬·중복 제거한 안정된 signature로 비교한다. 실제 집합이 바뀐 경우에만
250~500ms debounce 뒤 다음을 수행한다.

1. 이전 `EventSource`를 닫는다.
2. 새 `auctionIds` URL로 연결한다.
3. 연결 성공 또는 재연결 뒤 해당 목록 query를 invalidate/refetch한다.

3번은 연결 교체 중 놓친 이벤트를 DB 기준 상태로 복구한다. SSE 이벤트는 화면 갱신
힌트이고, 현재가·입찰 수·종료 상태의 source of truth는 DB 조회 결과다.

상세 페이지는 동일 훅에 ID 하나를 전달한다. 무한 스크롤은 이미 로드된 카드 중
viewport 주변 ID만 사용하므로 스크롤을 많이 해도 구독 수가 누적되지 않는다.

### 3.4 지갑은 경매 구독과 분리한 개인화 SSE로 갱신한다

사용자가 과거에 참여한 모든 경매를 공개 SSE 구독 집합에 계속 더하면, 인기 경매의
과거 참여자까지 오래 남아 fan-out이 다시 커진다. 지갑 갱신을 위해 해당 경매의
모든 공개 현재가 이벤트를 받을 이유도 없다.

따라서 로그인 뒤 앱 shell은 별도 지갑 개인화 스트림을 하나 연결한다.

```http
GET /api/me/wallet/stream
Accept: text/event-stream
```

- 인증은 현재 인증 모드의 `@CurrentUser` 계약으로 처리한다. JWT 모드에서는 SSE
  티켓, 세션 모드에서는 HttpOnly session cookie를 사용한다.
- 연결은 화면 전환과 무관하게 로그인 상태 동안 유지하고, 로그아웃 시 닫는다.
- 서버는 `userId -> Set<SseEmitter>`로만 관리한다. 다른 사용자의 지갑 상태는 절대
  같은 연결로 보내지 않는다.
- 지갑 projection이 실제 DB transaction에 반영된 뒤에만, 해당 사용자에게
  `wallet-state-changed` 이벤트를 보낸다.
- 외부 payload는 내부 `WalletStateChangedStreamEvent`를 그대로 노출하지 않는다.
  `walletVersion`, `availableBalance`, `frozenBalance`, `updatedAt`만 담는 별도 DTO를
  사용한다. event ID, idempotency key, 내부 stream 정보는 브라우저에 보내지 않는다.
- `local-sse`에서는 `wallets.projection_version`이 버전 원본이다. Redis 승인 모드에서는
  Redis 지갑 hash의 `walletVersion`만 증가시키고, Stream projection이 그 값을 DB에
  복사한다. Redis 입찰 결과를 DB hold/release/capture로 투영할 때 DB 버전을 다시
  증가시키거나 SSE를 별도 발행하면 두 카운터가 충돌하므로 금지한다.
- Stream 재전달로 DB projection 갱신이 no-op이면 지갑 SSE도 발행하지 않는다.
- 프론트는 수신한 version이 현재 보유한 version보다 큰 경우에만 헤더와 지갑 query
  cache를 갱신한다. `GET /api/wallet` 응답도 `walletVersion`을 포함한다. 재연결
  복구 결과를 cache에 반영하면 이 버전으로 high-water mark도 올려, 그 뒤 늦게
  재전달된 낮은 버전 SSE가 REST snapshot을 덮어쓰지 못하게 한다.
- 지갑 SSE의 emitter 전송은 전용 bounded executor로 분리한다. 전송 순서가
  뒤바뀌어도 version 비교가 오래된 snapshot을 버리므로, 느린 연결이 지갑 변경
  transaction 후처리를 오래 점유하지 않는다.

이로써 입찰 성공, 상회입찰로 인한 홀드 해제, 낙찰 차감, 충전·환불 같은 지갑 변화가
사용자가 어떤 목록·검색·상세 화면을 보고 있든 반영된다. 현재 전역 경매 이벤트를
이용해 지갑 query를 무효화하는 `useAuctionWalletSync`는 이 개인화 스트림으로 대체한다.

나의 대시보드에서 참여 경매의 현재가를 실시간 표시해야 한다면, 지갑 스트림에
과거 참여 경매 전체를 자동 구독시키지 않는다. 대시보드가 실제로 표시하는 카드도
동일한 viewport 선택 구독으로 다루며, 상회입찰 자체는 기존 개인화 알림으로 전달한다.

## 4. 프론트 연결 관리 계약

기존 전역 `useAuctionStream`은 페이지가 추가될수록 무조건 같은 전역 연결을 공유한다.
변경 후에는 활성 화면이 요구하는 `auctionIds`를 연결 관리자에 등록하고, 관리자가
하나의 `EventSource`를 공유한다.

```text
목록/검색/상세 화면
  -> useAuctionStream({ auctionIds, onAuctionUpdated, onReconnected })
  -> subscription manager가 활성 ID 집합을 계산
  -> URL이 달라질 때만 debounce 후 EventSource 교체
```

라우트 전환 중 이전 화면의 cleanup과 새 화면의 등록이 겹쳐도, 마지막으로 확정된 ID
signature만 연결한다. 목록·상세가 동시에 렌더링되는 특수 UI는 총 15개 제한을 넘기지
않도록 우선순위를 명시하거나, 두 번째 공개 SSE 연결을 만들지 않고 화면 설계를
조정한다.

## 5. SSE 릴레이 프로필: `local-sse`와 Redis Pub/Sub

선택 구독은 **연결을 보유한 인스턴스 안에서** 수행하는 공통 책임이다. Redis 사용
여부는 이벤트를 그 로컬 연결 관리자까지 어떻게 전달하느냐만 바꾼다. 따라서
`auctionId -> emitter` 인덱스, 최대 16개 검증, emitter 정리, heartbeat, payload
사전 직렬화는 두 프로필에서 동일하게 사용한다.

### 5.1 `local-sse` 프로필

로컬 개발 또는 단일 프로세스에서 Redis Pub/Sub 릴레이를 쓰지 않을 때의 흐름이다.

```text
AuctionStreamPublisher
  -> LocalAuctionStreamPublisher
  -> AuctionSseConnectionManager.broadcast(payload)
  -> payload.auctionId의 local emitter만 fan-out
```

- `LocalAuctionStreamPublisher`만 bean으로 등록한다.
- Redis publisher·subscriber·`RedisMessageListenerContainer`를 SSE 릴레이 목적으로
  만들지 않는다.
- 이 프로필은 "SSE Pub/Sub 릴레이를 생략"한다는 뜻이다. 입찰 처리나 다른 기능이
  Redis를 별도로 사용하는지와는 독립적이다.
- 단일 앱 인스턴스이므로 이벤트를 발행한 프로세스가 SSE 연결도 보유하며, 직접 호출로
  충분하다.

### 5.2 기본 Redis Pub/Sub 프로필

배포 환경과 멀티 인스턴스 가능성을 고려한 기본 흐름이다.

```text
AuctionStreamPublisher
  -> RedisAuctionStreamPublisher
  -> Redis channel: auction SSE update
  -> 각 인스턴스의 AuctionStreamRedisSubscriber
  -> 각 인스턴스의 AuctionSseConnectionManager
  -> 그 인스턴스에서 auctionId를 구독한 emitter만 fan-out
```

- `local-sse`가 활성화되지 않은 경우 `RedisAuctionStreamPublisher`와
  `AuctionStreamRedisSubscriber`를 등록한다.
- Redis subscriber는 모든 앱 인스턴스에서 메시지를 한 번 역직렬화할 수 있다. 하지만
  실제 SSE 전송은 각 인스턴스의 `emittersByAuctionId.get(payload.auctionId())`에만
  한정된다.
- 따라서 다중 인스턴스에서도 발행 인스턴스와 SSE 연결 인스턴스가 달라도 이벤트가
  전달된다.
- Redis 연결/ACL 오류는 기존 배포 설정처럼 앱 기동 단계에서 드러나야 한다. Redis를
  사용할 프로필에서 Pub/Sub을 조용히 local 동작으로 fallback하지 않는다. 그렇게 하면
  여러 인스턴스 중 일부 연결이 이벤트를 받지 못하는 장애를 숨기게 된다.

### 5.3 개인화 지갑 SSE의 프로필별 릴레이

지갑 SSE도 같은 원칙을 적용한다. `userId -> emitter` 인덱스와 browser payload는
공통이고, publish 경로만 프로필에 따라 바뀐다.

```text
local-sse
Wallet DB 반영 후 -> LocalWalletSsePublisher -> 해당 userId emitter push

Redis Pub/Sub
Wallet DB 반영 후 -> RedisWalletSsePublisher -> wallet SSE channel
                  -> 각 인스턴스의 subscriber
                  -> 해당 userId의 local emitter만 push
```

지갑 이벤트는 내부 `WalletStateChangedStreamEvent`가 DB projection을 완료한 뒤 별도
browser DTO로 변환한다. Redis 모드에서도 사용자별 동적 채널을 만들지 않고 단일
지갑 SSE 채널을 유지한 뒤, 각 인스턴스의 `userId` 인덱스로 최종 대상을 좁힌다.
이벤트에 지갑 snapshot과 사용자 ID가 있으므로 추가 DB 조회가 필요 없다.

`ObjectMapper` bean은 두 프로필에 모두 필요하다. 현재처럼 Redis 설정 클래스 안이
아닌 전역 Jackson 설정에서 제공해야 `local-sse`에서도 공개·개인화 SSE payload를
동일한 방식으로 직렬화할 수 있다.

### 5.4 프로필 선택과 검증

| 환경 | 활성 프로필 | SSE 릴레이 | 확인할 사항 |
|---|---|---|---|
| 로컬 단일 프로세스 | `local-sse` | 직접 local broadcast | Redis Pub/Sub 없이 선택 구독과 지갑 갱신이 동작하는지 |
| 배포/Redis 사용 | `local-sse` 미활성 | Redis Pub/Sub | Redis 인증·listener container·다중 인스턴스 릴레이가 정상인지 |

프로필별 구현체는 하나만 등록되어야 한다. 공통 controller와 connection manager에
`if (local)` 분기를 넣지 않고, publisher/subscriber adapter를 profile로 교체한다.
이렇게 하면 API와 프론트 훅은 어느 모드에서도 바뀌지 않는다.

## 6. Redis와 확장성 판단

이번 변경에서는 Redis Pub/Sub 채널을 `auction:{auctionId}`처럼 동적으로 나누지
않는다. 채널별 subscribe/unsubscribe는 앱 인스턴스마다 연결 수를 추적하고 경합을
처리해야 하며, 채널 수가 많아질수록 운영 복잡도도 커진다.

단일 채널을 유지해도 효과가 큰 이유는 병목이 Redis 메시지 역직렬화보다 emitter별
SSE 전송이기 때문이다.

```text
변경 전: 이벤트 1건 × 전체 SSE 연결 수
변경 후: 이벤트 1건 × 해당 auctionId의 로컬 구독 연결 수
```

예외적으로 모든 사용자가 같은 핫 경매를 보는 경우에는 전송 수가 줄지 않는다. 이는
그 경매를 실제로 보는 사용자에게 필요한 전송이므로, 별도 핫 경매 backpressure·
coalescing 문제로 측정 후 다룬다.

## 7. 관찰성과 검증

`auctionId`를 Micrometer/Prometheus tag로 붙이지 않는다. 경매 수만큼 시계열이 늘어나는
고카디널리티 문제가 생긴다. 대신 다음 집계 지표를 둔다.

- 전체 공개 경매 SSE 연결 수
- 현재 구독 중인 경매 ID 수
- 이벤트 1건당 실제 fan-out 대상 수 distribution
- 연결당 구독 ID 수 distribution
- 전송 성공·실패 수와 send duration
- 지갑 개인화 SSE 연결 수·전송 실패 수·payload version 역전 무시 수

테스트와 부하 검증은 아래를 포함한다.

- `auctionIds=42,57` 연결은 42·57 이벤트만 받고 다른 경매 이벤트를 받지 않는다.
- `local-sse`에서는 `LocalAuctionStreamPublisher`가 선택 구독 인덱스에 직접 전달하고,
  Redis SSE relay bean이 등록되지 않는다.
- Redis Pub/Sub 모드에서는 publisher가 채널에 발행하고 subscriber가 같은 선택 구독
  인덱스에 전달한다. 다른 `auctionId` emitter에 전송하지 않는다.
- 같은 emitter가 여러 ID를 구독해도 heartbeat는 한 번만 받는다.
- 연결 종료·timeout·전송 실패 뒤 모든 인덱스에서 누수 없이 제거된다.
- 15개 초과, 빈 값, 음수, 중복 query의 검증 결과를 확인한다.
- 목록 카드 순서와 visible range가 바뀔 때 debounce 이전에는 재연결하지 않고, 최종
  signature로만 한 번 재연결한다.
- 검색 결과 교체·무한 스크롤·상세 진입/이탈에서 stale observer와 EventSource가 남지
  않는다.
- 지갑 이벤트는 대상 사용자 연결에만 전달되고, version이 낮거나 같은 이벤트가
  프론트 cache를 덮어쓰지 않는다.
- 지갑 SSE도 두 프로필에서 동일한 browser payload와 사용자 격리를 보장하며, Redis
  모드에서는 다른 인스턴스의 사용자 연결까지 전달된다.
- 부하 테스트에서 전체 연결 수보다 해당 경매 구독자 수에 비례해 실제 전송 수가
  감소하는지 meter와 k6 수신 수로 비교한다.

## 8. 완료 기준

- [ ] 전역 공개 경매 SSE 연결을 `auctionIds` 선택 구독으로 교체한다.
- [ ] 서버 emitter 인덱스와 정리·heartbeat가 다중 경매 구독을 정확히 처리한다.
- [ ] 목록·검색·상세가 최대 16개 정책에 따라 연결을 관리한다.
- [ ] 로그인 사용자의 지갑은 개인화 SSE로 화면과 무관하게 최신화된다.
- [ ] `local-sse` 직접 전달과 Redis Pub/Sub 릴레이가 같은 선택 구독 계약을 만족한다.
- [ ] 기존 Redis 릴레이와 payload 사전 직렬화 계약이 유지된다.
- [ ] 단위·SSE 계약·프론트 훅·부하 테스트로 선택 fan-out 감소를 검증한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
