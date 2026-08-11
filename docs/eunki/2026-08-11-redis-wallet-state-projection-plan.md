# #360 Redis 기반 지갑 상태 전이 및 MySQL Projection 전환 계획

## 전제

- #351의 `bid-accept.lua`가 경매와 입찰자의 지갑 hold를 같은 Redis Lua 실행에서 전이한다.
- #323의 Redis Stream consumer는 MySQL 영속화·재시도·PEL 회수를 담당한다.
- Redis 프로필만 이 경로를 사용하고, 프로필 미지정 시 기존 MySQL `WalletService` 경로를 유지한다.

## 상태와 불변식

- `wallet:balance:{userId}`는 `availableBalance`, `frozenBalance`, `walletVersion`을 가진다.
- `wallet:hold:{auctionId}:{userId}`는 해당 경매의 HELD 금액 또는 최종 상태를 가진다.
- 하나의 승인 명령은 지갑 상태 변경과 Stream `XADD`를 반드시 한 Lua 실행에서 수행한다.
- 지갑 한 개의 성공 상태 전이는 `walletVersion`을 정확히 1 증가시킨다.
- 재전달된 이벤트는 `eventId`로 MySQL 이력을 중복 생성하지 않고, projection은 더 작은 `walletVersion`을 덮어쓰지 않는다.

## 구현 순서

1. `WalletService`를 포트로 분리하고, 기존 구현을 `!redis` 프로필 구현체로 유지한다.
2. `redis` 프로필 구현체와 Lua를 추가해 충전·환불·정산·주문 취소 환불을 Redis 상태 변경과 `wallet.updated.v1` 발행으로 처리한다.
3. #351의 `bid-accept.lua` 결과와 Stream payload에 hold/release/capture의 지갑 버전·변경 후 잔액을 포함한다.
4. Stream 이벤트 계약을 일반 지갑 변경까지 확장하고, consumer가 DB 도메인 판단 대신 event snapshot을 조건부 projection한다.
5. MySQL schema/domain에 projection version 및 eventId 멱등성 저장소를 추가하고, wallets/wallet_holds/point_records를 단조 버전으로 반영한다.
6. MySQL 이력에서 Redis 지갑 key를 재구성하는 bootstrap 경로와 Redis Lua 통합·consumer 재전달 통합 테스트를 추가한다.

## 의존성 및 PR 순서

#360은 #351의 Lua에 입찰 hold/release/capture를 포함시켜야 하므로 #351 위에 stacked로 구현한다. #351 병합 후 `origin/dev` 기준 rebase하여 독립 PR로 정리한다.
