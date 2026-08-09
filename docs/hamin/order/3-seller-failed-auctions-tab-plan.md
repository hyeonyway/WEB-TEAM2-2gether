# 판매자 유찰 경매 조회 API 및 대시보드 유찰 탭 계획

담당: 임하민. 이슈: [#233](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/233)
(브랜치 `feature/233-seller-failed-auctions` 예정). `auction` 패키지(담당 이은기) API 추가가
포함돼 있는데, 사용자가 직접 이슈 처리를 지시했으므로 [#232](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/232)와
동일하게 그 지시를 명시적 허가로 간주하고 진행한다.

## 배경

대시보드 "내가 판 주문"(`OrdersPanel.tsx`)은 `order` 테이블을 조회해서 보여주는데, 유찰된
경매는 낙찰자가 없어 애초에 `Order`가 생성되지 않는다(`OrderService.createFromAuctionClosed`가
`winnerId == null`이면 즉시 반환). 그래서 판매자가 "내 경매가 유찰됐는지"를 확인할 방법이
대시보드에 없다.

## API 설계

이슈 본문은 `GET /api/auctions/mine?status=FAILED`를 예시로 들었지만, 지금 필요한 건 "유찰
목록" 하나뿐이고 다른 상태 필터를 함께 지원할 계획도 없다. Order 1단계에서 `?role=buyer|seller`
대신 전용 경로(`/api/orders/purchases`, `/api/orders/sales`)로 바꾼 선례를 따라 전용
엔드포인트로 간다.

```
GET /api/auctions/mine/failed
```

- 인증 필요(`@CurrentUser Integer userId`) — 요청자 본인이 판매자인 경매만 조회.
- 응답: 판매자 관점에서 필요한 최소 필드만 — `Order`처럼 프론트가 바로 리스트에 뿌릴 수 있는
  평평한 구조로 맞춘다(아래 4절).
- 정렬: 최근 마감순(`closeTime desc`) — `참여 중인 경매`/`최근 나의 낙찰`과 동일하게 최신이
  위로.

## 1. Backend

### `AuctionRepository`

```java
List<Auction> findBySellerIdAndStatusOrderByCloseTimeDesc(Integer sellerId, AuctionStatus status);
```

### `AuctionResponses`에 DTO 추가

`OrderResponse`와 필드를 맞춰서 프론트가 두 응답을 같은 모양으로 다룰 수 있게 한다(4절 참고).

```java
public record FailedAuctionSummary(
        Integer id,
        @JsonProperty("card_name") String cardName,
        @JsonProperty("start_price") Long startPrice,
        @JsonProperty("closed_at") Instant closedAt
) {
}
```

`price`가 아니라 `start_price`인 이유: 유찰은 거래 자체가 없었으니 "거래금액"이라는 개념이
없다 — 그나마 의미 있는 숫자는 경매를 올린 시작가다(유찰 정의상 낙찰 입찰이 없으므로
`currentPrice`는 항상 `startPrice`와 같다, `AuctionCommandService.closeLockedAuction` 참고).

### `AuctionQueryService`

```java
public List<AuctionResponses.FailedAuctionSummary> getFailedAuctions(Integer sellerId) {
    List<Auction> auctions = auctionRepository.findBySellerIdAndStatusOrderByCloseTimeDesc(
            sellerId, AuctionStatus.FAILED
    );
    Map<Integer, CardSnapshot> cards = cardSnapshots(auctions);
    return auctions.stream()
            .map(auction -> new AuctionResponses.FailedAuctionSummary(
                    auction.getId(),
                    cards.get(auction.getItemId()).name(),
                    auction.getStartPrice(),
                    auction.getCloseTime()
            ))
            .toList();
}
```

`cardSnapshots(...)`는 이미 있는 private 헬퍼(카드 배치 조회)를 재사용한다.

### `AuctionController`

```java
@GetMapping("/mine/failed")
public List<AuctionResponses.FailedAuctionSummary> getFailedAuctions(@CurrentUser Integer userId) {
    return auctionQueryService.getFailedAuctions(userId);
}
```

`/api/auctions/{auctionId}`(단일 세그먼트)·`/api/auctions/{auctionId}/bids`(2세그먼트, 두
번째가 리터럴 `bids`) 매핑과 세그먼트 수/리터럴이 달라 경로 충돌 없음.

## 2. Frontend

### `dto/auctionDto.ts`

```ts
export type FailedAuctionResponseDto={
  id:number;
  card_name:string;
  start_price:number;
  closed_at:string;
};

export type FailedAuctionDto={
  id:number;
  cardName:string;
  startPrice:number;
  closedAt:string;
};
```

### `api/auctionApi.ts`

```ts
export async function fetchFailedAuctions():Promise<FailedAuctionDto[]>{
  const response=await authenticatedRequest<FailedAuctionResponseDto[]>('/api/auctions/mine/failed');
  return response.map(dto=>({
    id:dto.id,cardName:dto.card_name,startPrice:dto.start_price,closedAt:dto.closed_at,
  }));
}
```

### `queries/auctionQueries.ts`

```ts
failedForSeller:()=>queryOptions({
  queryKey:[...auctionQueryKeys.all,'failed-for-seller'],
  queryFn:fetchFailedAuctions,
  staleTime:10_000,
}),
```

### `OrdersPanel.tsx`

- `statusFilters`에 `{id:'FAILED',label:'유찰'}` 추가하되, **판매(`role==='seller'`)일 때만
  탭에 노출**한다 — 구매자 관점에는 유찰 개념이 없다. `role`이 `buyer`로 바뀌는 순간
  `statusFilter`가 `'FAILED'`로 남아있으면 `'ALL'`로 되돌린다.
- `useQuery(auctionQueries.failedForSeller())`를 `enabled: role==='seller'`로 추가 호출.
- 화면에 보여줄 목록/로딩/에러 상태는 **현재 선택된 필터에 해당하는 쿼리**를 따라간다:
  ```ts
  const activeQuery=statusFilter==='FAILED'?failedAuctions:orders;
  const list=statusFilter==='FAILED'
    ? (failedAuctions.data??[]).map(toFailedRow)
    : (orders.data??[]).filter(order=>statusFilter==='ALL'||order.status===statusFilter);
  ```
  `toFailedRow`는 `FailedAuctionDto`를 기존 `<li className="order-row">` 렌더링이 그대로
  먹을 수 있는 모양으로 맞춘다: `{id, auctionId: id, cardName, price: startPrice, status:
  'FAILED', createdAt: closedAt}`. 상태값이 기존 `OrderStatus` 유니온에 없는 `'FAILED'`라
  이 리스트 렌더링 지역 한정으로 유니온을 넓힌 타입을 쓴다(백엔드 `OrderStatus` enum 자체는
  건드리지 않음 — 유찰은 애초에 Order가 아니다).
- 액션 버튼은 이미 `order.status==='PENDING_CONFIRM'` 조건으로만 렌더링되므로 `'FAILED'`
  행에는 자연히 아무 버튼도 안 뜬다(이슈가 요구한 "읽기 전용" 그대로 충족).
- `statusLabel`에 `FAILED` → `'유찰'` 분기 추가.
- "거래금액" 레이블은 유찰 행에서는 의미가 달라지므로 `statusFilter==='FAILED'`일 때
  `'시작가'`로 바꿔 보여준다.

### `tailwind.css`

`.order-status-badge.cancelled`/`.pending_confirm`처럼 `.failed` 배지 색을 추가한다(취소는
빨강이 이미 있으니, 유찰은 "거래가 아예 없었다"는 중립적 느낌으로 회색 계열):
```css
.order-status-badge.failed{background:#f3f3f3;color:#666}
```

## 3. 테스트

- `AuctionQueryServiceTest` — `getFailedAuctions` 신규 케이스: 판매자 본인의 `FAILED` 경매만
  마감 최신순으로 반환하는지.
- `AuctionControllerTest`(신규, `@WebMvcTest(AuctionController.class)`) — 지금 `AuctionController`
  자체엔 슬라이스 테스트가 없어서(`DashboardControllerTest` 패턴을 따름) 새 엔드포인트
  하나만 검증하는 최소 테스트로 시작: 인증 사용자 기준 `/api/auctions/mine/failed` 호출 시
  `AuctionQueryService.getFailedAuctions(userId)`에 위임하고 JSON 필드(`card_name`,
  `start_price`, `closed_at`)가 스네이크케이스로 나가는지, 미인증 요청은 401인지.
- `frontend/src/api/auctionApi.test.ts` — `fetchFailedAuctions` 매핑 테스트 추가(기존
  `fetchAuctions` 등과 동일한 패턴).
- `OrdersPanel.tsx` 자체는 기존에도 컴포넌트 테스트가 없어(선례 없음) 이번에도 추가하지
  않고, 브라우저에서 직접 확인한다(유찰 경매가 있는 판매자 계정으로 탭 전환 확인).

## 실제 구현 결과

계획과 동일하게 구현했다. 브라우저에서 신규 가입 계정으로 로그인 후 로컬 DB에 해당
계정 소유의 `FAILED` 경매를 하나 심어 "내가 판 주문 → 유찰" 탭까지 실제로 확인했다:
카드명 링크, "유찰" 배지(회색), "시작가 15,000원", 마감 시각이 정상 표시되고 액션
버튼은 뜨지 않았다. 검증에 쓴 테스트 계정/경매 행은 확인 후 DB에서 정리했다.
로컬 백엔드 실행을 위해 `.claude/launch.json`에 `backend-dev` 설정(`.env` 로드 후
`./gradlew bootRun`)을 추가했다(세션 로컬 설정이라 git에는 안 잡힘).

> 이 문서는 claude의 도움을 받아 작성하였습니다.
