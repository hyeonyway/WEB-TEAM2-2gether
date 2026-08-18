# Redis / DB-lock 패키지 경계 재설계 (#572)

## 배경

Redis 마이그레이션(#469 이후 일련의 작업) 과정에서 `auction.bid`, `auction.adapter`,
`auction.service`, `dashboard`, `order`, `wallet.service` 여러 패키지에 "Redis 승인
경로" 구현체와 "DB row-lock(마이그레이션 이전 monolith) 경로" 구현체가 한 패키지 안에
섞여 쌓였다. `#572` 원래 범위는 `local-sse`/`auction-mock`/`debug-auth`처럼 완전히 죽은
프로필 분기를 지우는 것이었는데, 논의 중 이 DB row-lock 구현체들은 지울 잔재가 아니라
**의도적으로 유지하는 대체 실행 경로**라는 결론이 나왔다 — Redis 비동기 처리와 DB 동기
row-lock 처리가 서로 다른 부하 프로파일(피크 QPS vs 단일 row 극한 경합)에서 유리하기
때문이다. 그래서 `#572`에 "지우기"가 아니라 "패키지를 정리해서 두 경로를 명확히
구분하기"가 스코프로 추가됐다.

> 이 문서의 원본(`3-redis-dblock-package-boundary-design.md`)은 커밋된 적 없이
> 로컬에서만 존재하다 유실됐다(git 이력·dangling object 어디에도 없음, 복구 불가).
> 아래 내용은 그 원본을 복구한 게 아니라, 이슈 본문에 남아있는 "적용 원칙" 3줄과
> 9~14번 항목 명세를 근거로 실제 코드(`@Profile` 분기 지점 전수 조사)를 다시 조사해서
> 재구성한 것이다.

## 결론 — 패턴 3가지로 분류하고, 실제로 섞인 패키지만 쪼갠다

기존 코드를 조사해보면 Redis/DB 분기는 이미 세 가지 다른 모양으로 존재한다. 패턴마다
쪼개는 방식이 다르므로 먼저 구분한다.

### 패턴 A — 공용 인터페이스 + 완전히 분리된 두 구현체 (전환 불필요)

인터페이스 하나에 `Redis*`/`Db*`(또는 이름이 다른) 구현체 두 개가 각각 `@Profile`로
등록되는 형태. 이미 목표 구조라 손댈 게 패키지 이동뿐이다.

- `auction.bid.BidExecutor` ← `RedisBidExecutor` / `DbBidExecutor`
- `auction.service.AuctionCloseSchedulerProcessor` ← `RedisAuctionCloseSchedulerProcessor` /
  `DbAuctionCloseSchedulerProcessor`
- `auction.service.AuctionEndingTransitionProcessor` ← `RedisAuctionEndingTransitionProcessor` /
  `AuctionEndingTransitionService`
- `card.port.CardAuctionPort` ← `auction.adapter.RedisCardAuctionAdapter` /
  `auction.adapter.CardAuctionAdapter`
- `dashboard.DashboardQueryService` ← `RedisDashboardService` / `DashboardService`

### 패턴 B — 한 클래스 안에서 `if(redis)` 분기 (패턴 A로 전환 필요)

인터페이스 없이 한 클래스가 필드 존재 여부(`@Autowired(required = false)`)나
`Environment.matchesProfiles("redis")`로 내부 분기하는 형태. 이번에 패턴 A로
바꾼다 — Redis 쪽 로직을 별 클래스로 추출하고, 원래 클래스는 위임만 하는 얇은
파사드로 남긴다.

- `auction.service.AuctionQueryService` — `realtimeStateReader`(Redis 전용 빈)가
  주입됐는지로 분기, `searchRedisActiveAuctions`/`redisDetail` 등 Redis 전용
  private 메서드를 자체 보유. DB 쪽은 이미 별 클래스(`DbAuctionQueryService`)로
  위임하고 있어서 절반만 패턴 A다.
- `auction.service.AuctionDeadlineScheduler` — `nextTarget()`에서
  `isRedisProfile()`로 분기해 Redis ZSET 조회 / `AuctionRepository` 조회 중 하나를
  고른다.

**예외:** `AuctionCommandService.createInRedis()`도 `redisAuctionCreateExecutor != null`
분기를 갖고 있지만, Redis 쪽 로직 비중이 작아(생성 커맨드를 `RedisAuctionCreateExecutor`
호출 하나로 위임) 파일 자체를 쪼개지 않는다 — 이슈 본문에 명시된 예외.

### 패턴 C — 공용 코어 + Redis 위성 (dblock 서브패키지 없음)

DB 경로가 "대체 구현체"가 아니라 애초에 유일한 공용 코어라, Redis 쪽만 추가로
얹힌 형태. 두 가지 하위 모양이 있다:

- **C1 (상속형, wallet)**: `RedisWalletService extends WalletService` — `hold`/
  `release`/`capture` 등을 상속받아 그대로 재사용하고 Redis 승인 결과 변환 로직만
  추가한다. 검토 결과 `BidExecutor`식 완전 인터페이스 분리보다 지금 구조(상속)가 더
  적합하다는 결론이라 인터페이스로 승격하지 않는다.
- **C2 (추가형, order)**: `RedisOrderCommandService`는 `OrderService`를 상속하거나
  구현하지 않는 완전히 별도 클래스다. `OrderController`가 `OrderService`(항상 존재)와
  `Optional<RedisOrderCommandService>`(프로필에 따라 있을 수도 없을 수도)를 각각
  주입받아 호출 시점에 고른다. `OrderService`는 두 경로 모두에서 그대로 쓰이는 공용
  코어로, 로직·위치를 바꾸지 않는다.

두 모양 다 `dblock` 서브패키지가 없다 — DB 경로가 "별도 구현체"가 아니라 "원래
공용 코어"이기 때문이다.

## 적용 원칙

1. 실제로 Redis/DB-lock 두 구현체가 한 패키지에 섞여 있는 패키지만
   `<domain>.redis` / `<domain>.dblock` 하위 패키지로 쪼갠다. 100% 한 프로필만
   쓰는 패키지(`auction.controller`, `auction.query`, `auction.stream`)는 안 건드린다.
2. 패턴 C(`order`, `wallet.service`)는 `dblock` 서브패키지 없이 `redis`만 만든다.
   공용 코어(`OrderService`, `WalletService`)는 로직·위치 변경 없이 그대로 둔다.
3. 새로 추출/이동하는 클래스는 원래 파일이 쓰던 접미사(`Service`/`Scheduler`/
   `Executor` 등)를 유지하고 `Redis`/`Db` 접두사만 붙인다. 형제 클래스와 접미사가
   다르면(`RedisAuctionEndingTransitionProcessor` vs `AuctionEndingTransitionService`)
   통일한다.

## 도메인별 목표 구조

### auction/bid (이슈 #9)

- `auction/bid/redis/`: `RedisAuctionCreateExecutor`, `RedisAuctionCreateCommand`,
  `RedisAuctionCreateResult`, `RedisAuctionSeedBatchCoordinator`,
  `RedisAuctionSequenceSync`, `RedisAuctionStateSeeder`, `RedisAuctionStateWarmUp`,
  `RedisBidExecutor`, `RedisBidLuaConfiguration`, `RedisCardStateReader`,
  `AuctionSeedDataLoader`, `AuctionSeedData`, `AuctionSeedDbData`
  (뒤 세 파일은 이름에 `Redis` 접두사가 없지만 Redis 시딩 경로 전용이라 함께 이동 —
  `AuctionSeedDataLoader`/`RedisAuctionSeedBatchCoordinator`에서만 참조됨을 확인)
- `auction/bid/dblock/`: `DbBidExecutor`
- `auction/bid/` (유지): `BidExecutor`(interface), `BidCommand`, `BidExecutionResult`,
  `AuctionCloseData`(`RedisBidExecutor`/`DbBidExecutor` 양쪽에서 참조하는 공용 DTO),
  `BidEventData`

### auction/adapter + auction/port (이슈 #10, #6)

- `auction/adapter/redis/`: `RedisCardAuctionAdapter`
- `auction/adapter/dblock/`: `CardAuctionAdapter`
- `auction/adapter/`(유지, `#572` 6번 항목에서 별도 삭제): `MockAuctionCardAdapter`,
  `InMemoryAuctionEventAdapter`, `MockImageUploadAdapter`, `SpringAuctionEventPublisher`
  — 이들은 `auction-mock` 축이라 이번 redis/dblock 재설계와 무관, 6·7번 항목에서
  먼저 삭제된 뒤 이 패키지엔 `SpringAuctionEventPublisher`만 남는다.
- `auction/port/AuctionCardPort.java` — 6번 항목에서 고아 인터페이스로 판정, 삭제.

### auction/service (이슈 #11, 스코프에서 제일 큼)

- 신규 + 이동 `auction/service/redis/`: `RedisAuctionQueryService`(`AuctionQueryService`의
  Redis 분기 로직 전체 추출), `RedisAuctionDeadlineScheduler`(`AuctionDeadlineScheduler`의
  `isRedisProfile()` 분기 중 Redis ZSET 조회 경로), `RedisAuctionActiveIndexCleanupScheduler`,
  `RedisAuctionCloseSchedulerProcessor`, `RedisAuctionEndingTransitionService`(← 기존
  `RedisAuctionEndingTransitionProcessor`, 형제 클래스 `AuctionEndingTransitionService`와
  접미사 통일)
- 신규 + 이동 `auction/service/dblock/`: `DbAuctionDeadlineScheduler`(`AuctionDeadlineScheduler`의
  DB 조회 경로), `AuctionEndingTransitionService`, `DbAuctionCloseSchedulerProcessor`,
  `DbAuctionQueryService`
- `auction/service/`(유지, 얇은 파사드/공용): `AuctionQueryService`(Redis 분기 로직 빠지고
  `realtimeStateReader == null ? dbAuctionQueryService.search(...) : redisAuctionQueryService.search(...)`
  형태로 위임만 남김), `AuctionCommandService`(`createInRedis()` 안 쪼갬, 예외 명시됨),
  `AuctionCloseSchedulerProcessor`/`AuctionEndingTransitionProcessor`(interface),
  `AuctionClosingScheduler`, `AuctionDueClosingService`, `AuctionEndingPolicy`,
  `EndingExtensionProvider`/`RandomEndingExtensionProvider`,
  `AuctionCloseScheduleChangedEvent`
- **`AuctionDeadlineScheduler` 자체는 파사드로 남기지 않고 완전히 두 클래스로
  대체한다** — `AuctionQueryService`와 달리 이 클래스 타입을 직접 주입받는 외부
  호출자가 없고(`ApplicationReadyEvent`/`@Scheduled` 트리거로만 동작), `nextTarget()`
  외 로직(스케줄 등록·재시도·로깅)이 두 프로필 다 동일해서 위임형 파사드를 두면
  오히려 간접 호출만 늘어난다.

### dashboard (이슈 #12)

- `dashboard/redis/`: `RedisDashboardService`, `RedisDashboardStateSeeder`
- `dashboard/dblock/`: `DashboardService`
- `dashboard/`(유지): `DashboardQueryService`(interface, 이미 패턴 A), `DashboardController`,
  `DashboardQueryService`를 소비하는 나머지 조회/정렬 타입

### order (이슈 #13, 패턴 C2 — dblock 없음)

- `order/redis/`: `RedisOrderCommandService`, `RedisOrderListStateSeeder`,
  `RedisOrderStateSeeder`, `order/realtime/RedisOrderRealtimeStateReader`,
  `order/realtime/RedisOrderRealtimeStateProjection`(`order/realtime/` 서브패키지 자체를
  `order/redis/realtime/`으로 옮기거나 흡수 — 실제 이동 시점에 두 파일 다 Redis 전용임을
  재확인하고 결정)
- `order/`(유지, 로직·위치 변경 없음): `OrderService`(공용 코어), `Order`, `OrderController`,
  `OrderRepository`, `OrderStatus`, `adapter/`, `dto/`, `event/`, `exception/`, `port/`

### wallet/service (이슈 #14, 패턴 C1 — dblock 없음)

- `wallet/service/redis/`: `RedisWalletService`, `RedisWalletSeedBatchCoordinator`,
  `RedisWalletStateSeeder`
- `wallet/service/`(유지, 로직·위치 변경 없음): `WalletService`(공용 코어, `RedisWalletService`가
  상속), `WalletProjectionService`, `WalletSeedData`

## 스코프에서 제외한 것

- `auction.controller`, `auction.query`, `auction.stream` — 100% 한 프로필만 쓰는
  패키지라 원칙 1에 따라 안 건드림.
- `local-sse`/`auction-mock`/`debug-auth` 삭제 자체(이슈 1·2·5·6·7·8번 항목) — 이
  문서의 대상이 아니고, redis/dblock 재설계보다 먼저 끝내기로 함(단순 삭제라 충돌
  가능성이 낮음).
- `#573` 잔여 갭(`RedisWalletStateSeeder`의 전역 catch-up 체크)은 `#580`에서 별도
  진행 중이므로 이 작업에서는 건드리지 않는다.

## 검증

각 도메인 이동/추출 작업 뒤 `./gradlew compileJava compileTestJava`로 컴파일 확인,
가능하면 해당 슬라이스 테스트도 로컬에서 실행한다. 패키지 이동은 클래스 접근 제한자
(`class Foo` → 패키지 프라이빗)에 영향을 줄 수 있는 파일이 여럿이라(`class
DbAuctionCloseSchedulerProcessor`, `class RedisAuctionEndingTransitionProcessor` 등
package-private), 이동 시 접근 제한자를 `public`으로 올려야 하는지 파일별로 확인이
필요하다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
