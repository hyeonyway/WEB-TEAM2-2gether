# 경매 온디맨드 콜드시드 배치 코디네이터

## Context

[[wallet-cold-seed-batch-coordinator-design]]에서 지갑에 적용한 것과 동일한 N+1
문제가 `RedisAuctionStateSeeder.seedIfAbsent(Integer auctionId)`에도 있었다.
서로 다른 auctionId가 동시에 콜드로 진입하면(경매 목록/상세를 대량으로 처음
조회하는 트래픽 등), 경매 1건당 다음 6개 쿼리가 개별로 나갔다:

- `auctionRepository.findByIdAndStatusNot` (경매 자체)
- `bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc` (낙찰 후보)
- `cardStateReader.getCardSnapshot` (카드 스냅샷 — 자체 Redis 캐시가 있어 매번 MySQL을
  때리진 않지만 캐시 미스 시에는 마찬가지)
- `auctionImageRepository.findByAuctionIdOrderById` (이미지)
- `bidRepository.findLatestBidPerBidderByAuctionIdIn(List.of(id))` (배치 시그니처인데 단건 호출)
- `bidRepository.findRecentFiveByAuctionIdIn(List.of(id))` (배치 시그니처인데 단건 호출)

이미 `seedAllIfAbsent(List<Auction>)`가 warm-up 호출부를 위해 이 6개를 배치로
묶는 로직을 갖고 있었지만(경매 목록을 이미 들고 있는 caller 전용), 실제 온디맨드
단건 경로(`seedIfAbsent`)는 이 배치 로직을 전혀 타지 않고 있었다.

## 설계

지갑과 동일한 레이어링: `RedisStateSingleFlight`(같은 auctionId dedupe) 뒤에
`RedisAuctionSeedBatchCoordinator`를 얹어, 서로 다른 auctionId를 짧은 윈도우
(기본 5ms, `auction.state-seeding.auction-cold-batch.window-ms`)로 모아 6개
쿼리를 한 번씩만 실행하고 각 대기자에게 fan-out한다. 배치 크기(기본 200,
`.max-batch-size`) 도달 시 조기 flush, `flush()` 전체 `try/catch(Throwable)`로
대기자 전원 실패 처리, `@PreDestroy`에서 종료 전 동기 flush — 전부 지갑 코디네이터와
동일한 안전장치.

`AuctionSeedData` record + 정적 `resolveBatch(...)` 헬퍼로 배치 조회/그룹핑
로직을 캡슐화했다. `seedAllIfAbsent`는 이미 caller가 `List<Auction>` 엔티티를
들고 있어 재조회가 낭비이므로 기존 인라인 로직을 그대로 두고, `resolveBatch`는
코디네이터(ID 목록만 갖고 시작하는 경로) 전용으로만 사용한다.

새 리포지토리 메서드: `AuctionRepository.findByIdInAndStatusNot(Collection<Integer>, AuctionStatus)`
— 기존 단건 `findByIdAndStatusNot`의 배치 버전.

`seedIfAbsent`에서만 쓰이던 개별 6-쿼리 조합 로직(`private seed(Auction auction)`)은
이제 아무도 호출하지 않아 삭제했다.

## 변경 파일

- `RedisAuctionSeedBatchCoordinator.java` (신규)
- `AuctionSeedData.java` (신규)
- `RedisAuctionStateSeeder.java` (`seedIfAbsent`가 코디네이터를 거치도록 변경, 죽은
  단건 `seed(Auction)` 오버로드 삭제)
- `AuctionRepository.java` (`findByIdInAndStatusNot` 추가)
- `application-redis.yml` (`auction.state-seeding.auction-cold-batch.*`)

## 후속: 경매 warm-up과 지갑 warm-up 통합

`RedisWalletStateWarmUp`(HELD 지갑을 독립적으로 스캔해 기동 시 warm-up)과
`RedisAuctionStateWarmUp`(마감임박/최근 활성 경매를 warm-up)이 서로 무관하게
각자 다른 후보 집합을 골라서, "지금 warm-up한 경매의 낙찰 후보 지갑이 warm-up
안 됐을 수도 있는" 정렬 불일치가 있었다. 재기동 직후 입찰에 실제로 영향을 주는
건 "이 경매의 현재 낙찰자 지갑"이지 "아무 HELD 지갑 상위 N개"가 아니므로, 둘을
합쳤다:

- `RedisAuctionStateSeeder.seedAllIfAbsent`가 `void` 대신 이번에 warm-up한
  경매들의 **낙찰 후보(LEADING) userId 목록**을 반환하도록 변경
- `RedisAuctionStateWarmUp`이 그 목록을 그대로 `RedisWalletStateSeeder.
  seedAllIfAbsent(leadingBidderIds)`에 넘겨 같은 실행 안에서 지갑까지 시딩
- 독립적으로 HELD 지갑을 스캔하던 `RedisWalletStateWarmUp`/
  `WalletHoldRepository.findDistinctHeldUserIds`는 삭제 — warm-up 대상에 없는
  경매(캡을 넘는 경매)의 낙찰자 지갑은 어차피 그 경매 자체도 warm-up 안 됐으니,
  해당 경매를 처음 열람/입찰하는 순간 경매·지갑 둘 다 온디맨드 배치 코디네이터로
  콜드시드되는 것으로 충분하다(별도 안전망이 필요 없음)

## 검증

- `RedisAuctionSeedBatchCoordinatorTest`: 동시 콜드미스 시 배치 1회 수행, 배치
  크기 조기 flush, 종료된/존재하지 않는 경매는 빈 값, flush 중 예외 시 대기자
  전원 실패(hang 없음)
- `./gradlew test --tests "com.dbidding.auction.*" --tests "com.dbidding.wallet.*"
  --tests "com.dbidding.order.*"` — 알려진 사전 이슈 9건 제외 전부 통과, 회귀 없음
