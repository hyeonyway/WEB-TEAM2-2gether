# Redis 비동기 승인 프론트엔드 계약 설계

**관련 이슈:** #406  
**연계 이슈:** #351, #383, #384, #404

## 목적

`redis` 프로필에서는 입찰·지갑·주문 명령이 Redis Lua에서 먼저 승인되고 MySQL projection은
`event:timeline` consumer가 나중에 처리한다. 프론트엔드는 MySQL PK 또는 거래 PK가 아직 없다는
이유만으로 승인 성공을 오류로 처리하지 않는다.

이 문서는 기존 URL과 요청 형식은 유지하면서 Redis 승인 응답을 화면과 React Query cache에 반영하는
계약을 정의한다. 이 계약은 기본 실행에 섞지 않고 프론트 API 프로필로 분리한다.

## 프론트 API 프로필

| 프로필 | 활성화 방법 | 허용 응답 | 화면 동작 |
| --- | --- | --- | --- |
| `db` (기본) | `VITE_API_PROFILE` 미설정 또는 다른 값 | 기존 MySQL 동기 계약 | `transactionId`, `order.id`가 숫자가 아닌 응답은 계약 오류로 처리한다. |
| `redis` | `VITE_API_PROFILE=redis` | `transactionId=null`, `bid.id=null`, `order.id=null`, `stream_id` | Redis 승인 성공으로 처리하고 pending/projection UI를 표시한다. |

로컬 Redis 백엔드 연동은 다음처럼 실행한다.

```bash
VITE_API_PROFILE=redis VITE_API_BASE_URL=http://localhost:18080 npm run dev
```

`db` 프로필에서 Redis 전용 nullable 응답을 묵인하면 기존 동기 API 계약 오류를 숨길 수 있으므로,
nullable ID 허용은 반드시 `redis` 프로필에만 한정한다.

## 응답 상태

| API | Redis 승인 직후 정상 응답 | 프론트 처리 |
| --- | --- | --- |
| 입찰 | `bid.id=null`, `event_id`, 경매·지갑 snapshot | 응답 snapshot을 즉시 cache에 반영한다. `event_id`는 표시용 PK가 아니므로 DOM key로 쓰지 않는다. |
| 지갑 충전/환불 | `transactionId=null`, `transactionType=wallet.*.v1`, `balance` | `redis` 프로필에서만 `null`과 이벤트 타입을 허용하고 잔액 query cache를 응답 balance로 갱신한다. |
| 즉시 낙찰 | `pendingOrder`, `pendingOrder.auction_id`, `stream_id`, `status=PENDING` | 낙찰 성공을 즉시 표시한다. 주문 목록의 pending 행은 orderId가 생길 때까지 명령 버튼을 비활성화한다. |
| 주문 목록 | `id=null`, `stream_id`, `PENDING_CONFIRM` | Redis pending 주문으로 표시한다. MySQL projection 완료 뒤 목록 재조회 결과의 실제 id로 교체한다. |

## Cache 갱신 규칙

1. 명령 성공 응답의 auction/wallet snapshot은 optimistic 계산보다 우선한다.
2. 지갑 응답의 `balance`는 `availableBalance`와 `totalBalance`에 반영한다. 기존 frozen 금액은 유지한다.
3. 입찰 성공 뒤에는 경매 상세·목록·입찰 context·wallet query를 invalidate하여 projection/SSE 수신과
   오래된 화면이 합쳐지지 않게 한다.
4. 주문 mutation 성공 뒤에는 주문 목록과 지갑 query를 invalidate한다.
5. pending 주문은 `id`가 없으므로 `auctionId + streamId`를 UI key로 사용한다.

## 주문 명령 제약

기존 주문 명령 URL은 MySQL `orderId`를 path parameter로 받는다. 따라서 projection 전 pending 주문에는
구매확정·구매취소·판매취소 버튼을 노출하지 않는다. 이는 오류가 아니라 `주문 영속화 중`이라는 정상 상태다.

## 범위

- 프론트 API 프로필, 타입, API validator/mapper, cache 갱신, pending 주문 표시만 변경한다.
- 백엔드 URL·요청 헤더·SSE protocol은 변경하지 않는다.
- projection 실패, Stream replay, 클라이언트 주문 Idempotency-Key는 별도 운영/API 계약 이슈에서 다룬다.
