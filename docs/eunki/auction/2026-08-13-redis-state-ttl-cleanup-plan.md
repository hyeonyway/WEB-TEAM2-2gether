# #419 Redis 경매·지갑·주문 상태 생명주기(TTL) 부여 계획

## 배경

Redis 전환 이후 `auction:state:{auctionId}`, `wallet:balance:{userId}`, `order:state:{auctionId}` 해시에
TTL이 없어 경매가 종료되거나 유저 활동이 끊겨도 메모리에 영구히 남는다. 추가로
`RedisOrderRealtimeStateReader`는 참여 인덱스(`order:state:buyer/seller:{userId}`)가 비어 있으면
DB로 폴백하지 않고 빈 목록을 반환해, 인덱스가 아직 시딩되지 않았거나 TTL로 사라진 경우 과거 주문이
안 보이는 맹점이 있다.

## TTL 정책

- 1시간(3600초) 기본 + 최대 5시간(18000초) 지터 = 1~6시간 범위. `id % 18001`로 결정론적 지터를 계산해
  같은 auction/user는 항상 같은 TTL을 받는다(카드 스냅샷 캐시의 `ttlFor` 패턴과 동일).
- `auction:state:{auctionId}`: 경매가 **마감될 때만** 부여한다(`bid-accept.lua`의 buy-now 분기,
  `auction-close-request.lua`의 스케줄러 마감). 진행 중(OPEN/ENDING) 상태에는 TTL을 걸지 않는다.
- `wallet:balance:{userId}`: 모든 갱신 지점(충전/환불/정산/취소환불/입찰 hold·release, 최초 생성)에서
  롤링 갱신한다. 활동이 없으면 만료되고, 활동이 있으면 매번 새로 6시간까지 연장될 수 있다.
- `order:state:{auctionId}`: **완료/취소로 전이될 때만** 부여한다(`order-wallet-transition.lua`).
  `order:state:by-order-id:{orderId}`도 반드시 같은 시점에 같은 TTL로 맞춘다 — 어긋나면
  `RedisOrderCommandService.findOrderState`가 만료된 `order:state`를 살아있는 `by-order-id`만 보고
  재시딩을 건너뛰어 존재하는 주문을 못 찾는 오류가 난다.

## 주문 목록 조회 DB Fallback

`RedisOrderRealtimeStateReader.findForBuyer/findForSeller`가 조회 전에
`RedisOrderListStateSeeder.seedIfRequired(userId, asBuyer)`를 먼저 호출한다.

- `order:state:seeded:{buyer|seller}:{userId}` 마커가 있으면 그대로 Redis 인덱스만 읽는다.
- 마커가 없으면(최초 조회 또는 마커 TTL 만료) `RedisProjectionCatchUpVerifier`로 캐치업을 확인한 뒤,
  MySQL에서 해당 유저의 전체 구매/판매 주문을 읽어 각각 `RedisOrderStateSeeder.seedIfAbsent(Order)`로
  시딩하고, 마커를 같은 1~6시간 TTL로 남긴다.
- 개별 `order:state` 해시가 마커보다 먼저 만료되는 좁은 창은 남아있다(엔트리별 TTL이 각자 다른
  시각에 계산되므로) — 다음 마커 만료 주기에 다시 전체 재시딩되며 자연 회복된다.

## 참고

- 기존 `RedisAuctionStateSeeder`/`RedisWalletStateSeeder`와 동일하게 `RedisStateSingleFlight` +
  `RedisProjectionCatchUpVerifier` 조합으로 동시 state miss를 하나의 초기화로 합치고, 캐치업 전
  복구를 시도하지 않는다.
- 관련 이슈: #419
