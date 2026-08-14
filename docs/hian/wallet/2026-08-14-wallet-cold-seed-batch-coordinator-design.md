# #451/#452 — 지갑 온디맨드 콜드시드 캐싱과 배치 코디네이터

## Context

`RedisWalletStateSeeder.seedIfAbsent(Integer userId)`는 콜드 유저 1명마다:
1. `RedisProjectionCatchUpVerifier.isCaughtUp()` — 전역 상태 하나를 확인하는데도 매번 3쿼리
2. `WalletHoldRepository.findHeldRowsForUsers`/`WalletRepository.findBootstrapRowsForUsers` —
   둘 다 이미 `WHERE user_id IN (:userIds)` 배치 시그니처인데 `List.of(userId)` 싱글톤으로 호출

서로 다른 userId N명이 동시에 콜드로 진입하면(재기동/Redis flush 직후, 또는 k6 QPS300
버스트처럼 신규 유저가 몰릴 때) 5N개의 쿼리가 나간다. `RedisStateSingleFlight`는 같은
userId 동시 요청만 묶어주므로 이 문제를 해결하지 못한다.

## #451 — isCaughtUp() 전역 캐싱

`isCaughtUp()`은 엔티티와 무관한 전역 불리언 하나다. `RedisStateSingleFlight`를 **고정된
전역 키**로 사용하고 짧은 TTL(기본 500ms) 캐시를 얹어, 서로 다른 엔티티가 동시에
콜드미스 나도 실제 조회는 TTL 주기당 한 번만 나가게 했다. 이로써 `5N → 2N`.

파일: `RedisProjectionCatchUpVerifier.java`

## #452 — 지갑 배치 코디네이터

남은 유저별 쿼리 2개(`findHeldRowsForUsers`, `findBootstrapRowsForUsers`)를 배치로
묶기 위해 `RedisWalletSeedBatchCoordinator`를 새로 만들었다. `RedisStateSingleFlight`
(같은 userId 동시요청 dedupe) **뒤에** 얹는 별도 레이어로, 코디네이터에는 항상
userId당 최대 1건만 들어온다는 불변식을 유지한다.

- 시간 윈도우(기본 5ms) 동안 서로 다른 userId 요청을 모아 배치 조회 1회로 묶고,
  각 대기자의 `CompletableFuture`로 fan-out한다.
- 배치 크기가 `maxBatchSize`(기본 200)에 도달하면 윈도우를 기다리지 않고 조기 flush
  (Kafka `linger.ms`/`batch.size`와 동일한 패턴).
- **트레이드오프**: 이 윈도우는 경합이 전혀 없는 단독 콜드 요청에도 지연 하한선을
  만든다. `seedIfAbsent`가 입찰(`RedisBidExecutor`) 동기 경로에서 직접 호출되므로
  기본값을 5ms로 작게 잡았다.
- `flush()`는 리포지토리 호출부터 grouping까지 전체를 `try/catch(Throwable)`로
  감싸 모든 대기자를 실패 처리한다 — 그렇지 않으면 예외가 스케줄러 스레드에서
  조용히 삼켜지고 대기 중이던 모든 `.join()` 호출자가 영원히 걸려 Tomcat 스레드가
  고갈된다.
- `@PreDestroy`에서 종료 전 남은 배치를 동기적으로 flush한다 —
  `ScheduledThreadPoolExecutor.shutdown()`은 기본적으로 대기 중인 지연 작업을
  실행하지 않고 버리기 때문.

이로써 `2N → O(배치 수)`.

파일: `RedisWalletSeedBatchCoordinator.java`, `WalletSeedData.java` (배치 결과를
유저별로 묶는 `resolveBatch` 공용 헬퍼 포함)

## 기동 시 warm-up (auction과 동일한 패턴)

`RedisAuctionStateWarmUp`이 기동 시 마감 임박 활성 경매를 미리 Redis에 올려두는
것처럼, `RedisWalletStateWarmUp`을 추가해 **현재 자금이 묶여있는(HELD) 지갑**을
기동 시 제한적으로(`recent-limit`, 기본 200) 미리 시딩한다.

다만 이 warm-up은 "재기동 직후 기존 유저 다수가 동시에 콜드"인 상황만 줄여줄 뿐,
**신규 유저가 몰리는 이벤트성 버스트는 warm-up 대상에 없으므로 해결하지 못한다** —
이 경우는 여전히 `RedisWalletSeedBatchCoordinator`가 담당한다. 두 메커니즘은
경쟁이 아니라 상호 보완 관계:
- warm-up: 부팅 시 알려진 대상을 미리 채워 콜드미스 자체를 줄임
- 배치 코디네이터: warm-up이 못 잡는(신규/예측 불가) 콜드미스를 저비용으로 처리

`RedisWalletStateSeeder.seedAllIfAbsent(List<Integer> userIds)`가 이 warm-up의
호출부로, `isCaughtUp()`을 배치 전체에 한 번만 확인하고(`RedisAuctionStateSeeder.
seedAllIfAbsent`와 동일한 패턴) `WalletSeedData.resolveBatch`를 재사용해 배치
코디네이터의 로직과 중복 없이 시딩한다.

파일: `RedisWalletStateWarmUp.java`, `WalletHoldRepository.findDistinctHeldUserIds`

## 설정 (`application-redis.yml`)

```yaml
auction:
  catchup-verification:
    cache-ttl: ${AUCTION_CATCHUP_CACHE_TTL:PT0.5S}
  state-seeding:
    wallet-cold-batch:
      window-ms: ${WALLET_COLD_SEED_BATCH_WINDOW_MS:5}
      max-batch-size: ${WALLET_COLD_SEED_MAX_BATCH_SIZE:200}
    wallet-warm-up:
      enabled: ${WALLET_STATE_WARM_UP_ENABLED:true}
      recent-limit: ${WALLET_STATE_WARM_UP_RECENT_LIMIT:200}
```

## 검증

- 단위 테스트: `RedisProjectionCatchUpVerifierTest`(캐시/TTL/동시성),
  `RedisWalletSeedBatchCoordinatorTest`(윈도우/크기 조기flush/예외 전파/빈 지갑),
  `RedisWalletStateSeederTest`(seedAllIfAbsent 배치 검증), `RedisWalletStateWarmUpTest`
- `./gradlew test --tests "com.dbidding.wallet.*" --tests "com.dbidding.auction.*"
  --tests "com.dbidding.order.*"` — 사전에 알려진 `timeline_events` 스키마 무관
  실패(9건)를 제외하고 전부 통과, 회귀 없음
- 남은 항목: 이슈에 명시된 "k6 QPS300 다이렉트 버스트" 재현으로 배치 윈도우가
  실제 지연/쿼리 수에 주는 영향을 Docker로 가볍게 측정
