# Redis 대시보드 및 최근 입찰 조회 설계

## 목적

`redis` 프로필의 입찰 승인은 MySQL projection보다 먼저 완료된다. 따라서 참여 중인 경매 대시보드와
경매 상세의 최근 입찰은 MySQL `bids`를 기준으로 조회하지 않고 Redis 승인 상태를 기준으로 반환한다.

## Redis read model

- `auction:dashboard:participating:{userId}`: 사용자가 한 번이라도 입찰한 활성 경매 ID Set
- `auction:state:{auctionId}`: 현재가, 상태, 마감 시각, 입찰 수와 카드 요약을 포함한 경매 상태
- `auction:bidder:{auctionId}:{userId}`: 해당 사용자의 `LEADING` 또는 `OUTBID` 상태와 금액
- `auction:recent-bids:{auctionId}`: 최신 입찰 50개 Stream. API에는 역순으로 최대 5개만 반환한다.

`bid-accept.lua`는 입찰 승인과 동시에 bidder state, 참여 인덱스, recent-bids Stream을 갱신한다.
경매 state miss 시 `auction-state-seed.lua`가 기존 MySQL projection의 사용자별 마지막 입찰 상태와 최근
5개 입찰을 state 생성과 한 번의 Lua 실행에서 함께 시딩한다. state가 이미 존재하면 시딩하지 않아 Redis의
더 최신 승인 상태를 덮어쓰지 않는다.

## 조회 경계

- `GET /api/dashboard/participating-auctions`: Redis 참여 인덱스 → 경매/사용자 상태로 계산한다.
- `GET /api/auctions/{auctionId}/bids`: Redis state가 있으면 `auction:recent-bids`의 최근 5건을 반환한다.
- 최근 낙찰과 종료 경매 이력은 active Redis state에서 제거되므로 MySQL projection을 유지한다.
- `!redis` 프로필은 기존 `DashboardService`와 MySQL 입찰 조회를 유지한다.

## 일관성

Redis state가 존재하는 동안에는 대시보드와 입찰 컨텍스트가 같은 승인 원본을 보므로 projection lag에 의해
현재가 또는 내 입찰 상태가 뒤처지지 않는다. Redis state miss는 기존 catch-up 검증을 통과한 경우에만
MySQL projection으로 시딩한다. Stream projection이 따라잡지 못한 상태에서는 오래된 DB 값으로 승인 경로를
복원하지 않고 복구 필요 오류로 처리한다.
