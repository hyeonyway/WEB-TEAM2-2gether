# #501 Redis 조회 트랜잭션 경계 분리 설계

## 1. 배경

Redis 프로필에서 경매 목록, 입찰 내역, 입찰 참여를 `4:4:2` 비율로 호출하고 QPS를
`50 → 100 → 150 → 200 → 300 → 400`으로 높이면 HikariCP 30개가 모두 active가 된 채
약 30초 동안 반환되지 않는 구간이 재현됐다. 이때 Tomcat 요청 스레드도 함께 고갈되고,
Redis cache hit ratio는 주기적으로 크게 하락했다.

재현 로그의 요청 스레드는 `AuctionQueryService.getBidContext()` 진입 시 시작된 read-only
트랜잭션이 JDBC 커넥션을 보유한 채 `RedisWalletSeedBatchCoordinator`의 future를 기다리고
있었다. 반면 `wallet-cold-seed-batch` 스레드는 실제 DB 배치 조회를 시작하려고 커넥션을
요청했지만 풀의 30개가 요청 스레드에 선점되어 있었다.

`RedisAuctionStateSeeder.seedIfAbsent()`에도 같은 구조가 있다. 메서드가 read-only
트랜잭션으로 시작한 뒤 Redis miss를 확인하고 `auction-cold-seed-batch`의 future를
`join()`한다. 따라서 `AuctionQueryService`의 트랜잭션만 제거하면 지갑 cold miss는
완화되지만, 경매 state가 동시에 miss인 경우 동일한 커넥션 기아가 다시 발생할 수 있다.

이 문제는 DB가 느려서 생기는 일반적인 풀 포화가 아니라, 요청 스레드가 실제 DB 작업에
필요한 커넥션을 먼저 보유하고 배치 스레드를 기다리는 스레드 간 커넥션 기아다.

## 2. 목표

- Redis 조회, cache hit 확인, single-flight 대기, batch future 대기 중에는 JDBC 커넥션을
  보유하지 않는다.
- DB fallback과 cold-seed용 DB 조회만 짧은 read-only 트랜잭션에서 수행한다.
- 기존 경매 목록, 상세, 입찰 내역, 입찰 컨텍스트의 응답 계약과 예외 계약을 유지한다.
- 작은 커넥션 풀에서 동시 cold miss가 발생해도 요청 스레드와 배치 스레드 사이의
  커넥션 기아가 재발하지 않음을 자동화 테스트로 증명한다.
- 실제 혼합 부하에서 Hikari active, pending, Tomcat busy thread, Redis hit ratio 및 DB
  query를 함께 확인한다.

## 3. 비목표

- 경매 목록의 Redis N+1 조회를 pipeline 또는 Lua로 합치는 작업
- cache TTL, warm-up 대상 500개, batch window와 batch size 조정
- Hikari pool 크기 또는 Tomcat thread 수를 늘려 증상을 우회하는 작업
- Redis key 구조와 API 응답 스키마 변경
- JWT/session 전환 및 Spring Session hash 복구

Redis 명령 수와 hit ratio 자체를 개선하는 작업은 별도 이슈로 분리한다.

## 4. 현재 문제 구조

### 4.1 입찰 컨텍스트의 지갑 cold miss

```text
HTTP request thread
  AuctionQueryService.getBidContext()
    read-only transaction 시작 + JDBC connection 점유
    RedisWalletService.getBalance()
      RedisWalletStateSeeder.seedIfAbsent()
        RedisWalletSeedBatchCoordinator.requestSeedData().join()

wallet-cold-seed-batch thread
  WalletSeedData.resolveBatch()
    WalletHoldRepository / WalletRepository
      JDBC connection 대기
```

동시 요청 수가 pool 크기에 도달하면 request thread는 모두 future를 기다리고, future를
완료할 batch thread는 커넥션을 얻지 못한다.

### 4.2 경매 state cold miss

```text
HTTP request thread
  RedisAuctionStateSeeder.seedIfAbsent()
    read-only transaction 시작 + JDBC connection 점유
    Redis miss 확인
    RedisAuctionSeedBatchCoordinator.requestSeedData().join()

auction-cold-seed-batch thread
  AuctionSeedData.resolveBatch()
    Auction / Bid / Image repository
      JDBC connection 대기
```

경매 ID별 single-flight는 같은 key의 중복 DB 조회는 막지만, 서로 다른 경매 ID가 동시에
miss일 때 request thread의 커넥션 선점을 막지는 못한다.

## 5. 설계

### 5.1 `AuctionQueryService`를 Redis 라우터로 변경

`AuctionQueryService`의 클래스 레벨 `@Transactional(readOnly = true)`를 제거한다. 이
서비스는 다음 순서만 담당한다.

1. Redis reader 존재 여부와 Redis state를 확인한다.
2. 필요한 경우 cold seed를 요청하고 완료를 기다린다.
3. Redis state가 유효하면 Redis 응답 DTO를 만든다.
4. Redis를 사용하지 않거나 state 복구 후에도 fallback이 필요하면 DB 조회 서비스에
   위임한다.

이 계층에서는 repository를 직접 호출하지 않는다. 따라서 Redis hit와 cold-seed 대기
구간은 트랜잭션 밖이며 JDBC 커넥션을 획득하지 않는다.

### 5.2 DB 조회를 `DbAuctionQueryService`로 분리

새 `DbAuctionQueryService`에 `@Transactional(readOnly = true)`를 적용하고 다음 DB 조회와
DTO 조립을 이동한다.

- Redis 프로필이 아닐 때의 경매 검색
- 대시보드 참여 경매와 유찰 경매 조회
- 경매 상세 fallback
- 입찰 내역 fallback
- 입찰 컨텍스트 fallback
- 위 기능이 사용하는 auction, bid, image, card 조회와 entity 기반 DTO 변환

외부 API는 계속 `AuctionQueryService`를 호출한다. DB 서비스는 facade 뒤의 내부
collaborator이므로 controller와 API 계약은 바뀌지 않는다. entity와 lazy association을
사용하는 DTO 조립은 DB 서비스 트랜잭션 안에서 완료하고 entity를 Redis 라우터로 반환하지
않는다.

`getBidContext()` fallback에서 입찰 내역을 다시 facade의 `getBids()`로 호출하지 않고,
DB 서비스 내부의 DB 전용 helper를 사용한다. 이를 통해 fallback 도중 Redis 분기가 다시
실행되거나 트랜잭션 경계가 우연히 바뀌는 것을 막는다.

### 5.3 cold-seed DB loader의 트랜잭션을 batch thread에 배치

`RedisAuctionStateSeeder.seedIfAbsent()`의 `@Transactional`을 제거한다. Redis 확인,
projection catch-up 확인, single-flight, future `join()`, Redis Lua seed는 모두 트랜잭션
밖에서 수행한다.

경매 seed용 repository 조회는 별도 `AuctionSeedDataLoader`에 모으고
`@Transactional(readOnly = true)`를 적용한다. `RedisAuctionSeedBatchCoordinator`의
단일 batch thread가 이 bean을 호출할 때만 커넥션을 획득한다. DB entity에서 seed에 필요한
값을 읽는 작업도 이 트랜잭션 안에서 끝낸다.

카드 snapshot cache 확인과 Redis Lua 쓰기는 DB 트랜잭션 밖에서 수행한다. 카드 cache
miss로 DB 조회가 필요하면 `RedisCardStateReader` repository 호출이 자체적으로 짧게
커넥션을 사용하되, request thread가 batch future를 기다리면서 커넥션을 보유하지는 않는다.

`seedAllIfAbsent()` 기동 warm-up도 같은 loader를 재사용해 DB 조회가 끝난 뒤 Redis seed를
수행한다. 기동 warm-up은 현재 장애의 직접 경로는 아니지만 동일한 트랜잭션 원칙을 적용해
Redis I/O 동안 DB 커넥션을 유지하지 않게 한다.

지갑 cold-seed coordinator는 이미 요청 스레드에서 트랜잭션을 시작하지 않는다. 이번
변경에서는 `AuctionQueryService`의 외부 트랜잭션을 제거함으로써 지갑 batch thread가
필요한 커넥션을 요청 스레드가 선점하지 않게 한다. 지갑 batch의 쿼리 수나 구조 변경은
별도 Redis 개선 범위로 남긴다.

### 5.4 호출 흐름

```text
AuctionQueryService (transaction 없음)
  ├─ Redis hit ──────────────────────────────> Redis DTO 반환
  ├─ Redis miss
  │    └─ state seeder (transaction 없음)
  │         └─ batch coordinator
  │              └─ AuctionSeedDataLoader (짧은 read-only transaction)
  │         └─ Redis seed
  │    └─ Redis DTO 반환
  └─ DB fallback
       └─ DbAuctionQueryService (read-only transaction) ──> DB DTO 반환
```

중요한 불변식은 `future.join()`을 호출하는 스레드에 활성 DB 트랜잭션이 없어야 한다는
것이다.

## 6. 오류 처리와 계약 유지

- auction 또는 card가 없을 때의 기존 `AuctionException`/`CardException`을 유지한다.
- projection catch-up이 완료되지 않았을 때 `stateRecoveryRequired()`를 그대로 반환한다.
- batch loader 예외는 `CompletableFuture`를 통해 기존과 같이 요청에 전파한다.
- Redis seed 직후에도 state를 읽지 못한 경우에만 기존 DB fallback을 실행한다.
- Redis timeout 정책과 DB fallback 정책은 이번 변경에서 확대하지 않는다. timeout을
  cache miss처럼 취급해 DB 트래픽을 늘리는 동작을 새로 추가하지 않는다.

## 7. 엄격한 테스트 전략

### 7.1 실패를 먼저 고정하는 커넥션 기아 회귀 테스트

Testcontainers MySQL과 Redis를 사용하고 Hikari maximum pool size를 2, connection
timeout을 짧게 설정한다. 서로 다른 user/auction의 동시 cold miss 요청을 pool 크기보다
많이 실행한다.

테스트는 다음을 검증한다.

- 모든 요청이 제한 시간 안에 완료된다.
- `SQLTransientConnectionException`과 Hikari connection timeout이 없다.
- batch thread의 DB 작업이 시작되는 동안 request thread가 JDBC 커넥션을 보유하지 않는다.
- Hikari active의 관측 peak가 request 동시성만큼 증가하지 않고, batch DB 작업에 필요한
  범위 안에 머문다.
- 경매와 지갑 Redis key가 실제로 생성되고 반환 DTO 값이 DB projection과 일치한다.

이 테스트는 변경 전 코드에서 request thread 두 개가 pool 두 개를 점유하고 batch thread가
timeout되는 형태로 실패해야 하며, 변경 후 통과해야 한다.

### 7.2 트랜잭션 경계 통합 테스트

- Redis가 미리 warm 상태인 `search`, `getDetail`, `getBids`, `getBidContext` 호출에서
  `TransactionSynchronizationManager` 기준 활성 트랜잭션이 없음을 확인한다.
- 동일 호출 전후 Hikari active가 증가하지 않음을 확인한다.
- Redis가 비활성 또는 fallback 상태일 때 `DbAuctionQueryService` 안에서는 실제
  read-only 트랜잭션이 활성화되어 있음을 확인한다.
- DB fallback DTO를 트랜잭션 밖에서 직렬화해도 lazy loading 예외가 발생하지 않음을
  확인한다.

### 7.3 기능 회귀 테스트

- `AuctionQueryServiceTest`의 검색 정렬, cursor, 상세, 입찰 내역, 입찰 컨텍스트 계약을
  Redis router와 DB query service 테스트로 재배치한다.
- `RedisAuctionStateSeederTest`, `RedisAuctionSeedBatchCoordinatorTest`,
  `RedisWalletStateSeederTest`, `RedisWalletSeedBatchCoordinatorTest`를 실행한다.
- 경매 state와 wallet state Lua integration test를 실행한다.
- backend 전체 테스트를 실행해 non-redis profile의 조회 경로도 확인한다.

### 7.4 실제 부하 검증

로컬 또는 검증 환경에서 사용자 1,000명, 경매 목록:입찰 내역:입찰 참여 `4:4:2`,
`50 → 100 → 150 → 200 → 300 → 400 QPS` 시나리오를 동일하게 실행한다. warm 상태와
cold 상태를 분리해서 각 구간에서 다음을 수집한다.

- API별 p95/p99, 오류율, timeout
- Hikari active/idle/pending과 connection timeout 로그
- Tomcat busy/max thread
- Redis command latency, connected clients, cache hit ratio
- MySQL general log가 아닌 slow query log와 쿼리별 호출 횟수

합격 기준은 Hikari pending과 connection timeout이 0이고, 부하 종료 후 active가 즉시
정상 수준으로 복귀하며, 동일 주기의 Tomcat thread 고갈이 재현되지 않는 것이다. p95/p99와
Redis hit ratio는 변경 전후 수치를 함께 기록하되, 이번 이슈에서 제외한 Redis N+1 때문에
남는 지연은 별도 이슈의 기준값으로 넘긴다.

## 8. 예상 변경 파일

- `auction/service/AuctionQueryService.java`: Redis 라우팅만 남기고 클래스 트랜잭션 제거
- `auction/service/DbAuctionQueryService.java`: DB 조회와 DTO 조립 분리
- `auction/bid/RedisAuctionStateSeeder.java`: request thread 트랜잭션 제거
- `auction/bid/AuctionSeedDataLoader.java`: batch thread의 DB read-only 경계 추가
- `auction/bid/RedisAuctionSeedBatchCoordinator.java`: loader 위임
- 관련 단위, Testcontainers 통합, 동시성 회귀 테스트

구현 중 entity 의존 관계 때문에 이름이나 DTO record가 추가될 수 있으나, 위의 트랜잭션
불변식과 API 계약은 변경하지 않는다.

## 9. 구현 순서

1. pool size 2에서 현재 구조가 timeout되는 동시성 회귀 테스트를 먼저 작성한다.
2. DB fallback 서비스와 기존 계약 테스트를 분리한다.
3. `AuctionQueryService`의 트랜잭션을 제거하고 DB 호출을 전부 위임한다.
4. 경매 cold-seed DB loader를 batch thread 트랜잭션으로 분리한다.
5. 트랜잭션/Hikari 관측 테스트와 전체 회귀 테스트를 통과시킨다.
6. 실제 혼합 부하를 실행하고 변경 전 재현 로그와 비교한다.

## 10. 롤백

API와 Redis key를 변경하지 않으므로 문제가 생기면 서비스 분리 커밋을 되돌려 기존 조회
서비스로 복구할 수 있다. 설정값이나 데이터 마이그레이션은 필요하지 않다. 단, 롤백하면
커넥션 기아도 다시 발생하므로 pool 증설은 임시 완화일 뿐 해결책으로 간주하지 않는다.
