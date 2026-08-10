# 입찰 락 처리 계획 1단계 — DB/Redis 전환용 BidExecutor 인터페이스 도입

담당: 임하민. 이슈: [#326](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/326)
(브랜치 `feature/326-bid-executor-interface`). 관련: [#323](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/323)
(Redis Stream 기반 입찰 배치 영속화, `docs/seho/redis/경매-입찰-redis-stream-배치-영속화-설계.md`).

## 배경

현재 입찰 처리는 `AuctionCommandService.participateInternal()`
(`AuctionCommandService.java:160-243`) 하나의 `@Transactional` 안에서 Auction row lock
(`findByIdForUpdate`, `AuctionRepository.java:85-87`)과 Wallet/WalletHold row lock
(`WalletService.hold/release`, `WalletRepository.java:17-36`)을 순서대로 획득하고, 가격
판단 → wallet hold/release → `Bid` 저장까지 동기적으로 처리한다.

이걸 Redis Lua Script 기반으로 전환하려는 방향은 다음과 같이 확정했다(대화로 검토):

- 대상은 Auction lock과 Wallet 관련 lock 전부. 가격/자기입찰/최고입찰자/지갑 잔액 판단을
  Lua Script 안에서 원자적으로 처리한다.
- 승인된 입찰은 Redis Stream에 이벤트로 발행하고, 이미 #323에서 구현된 `redis` 프로필
  consumer(`AuctionBidStreamConsumer`)가 비동기로 DB에 반영한다. 이벤트 계약(`auctionVersion`,
  `idempotencyKey` 등, `BidAcceptedStreamEvent.java`)은 그대로 따른다.
- SSE 실시간 전파는 락 모드와 무관하게 Redis Pub/Sub로 통일한다(별도 작업, 이번 범위 아님).
- 전환 스위치는 `@ConditionalOnProperty`가 아니라 `spring.profiles.active=redis` 프로필로
  통일한다 — #323의 consumer가 이미 `@Profile("redis")`로 게이팅되어 있어서, 스위치를
  분리하면 executor와 consumer가 어긋나는 상태(Lua가 입찰을 승인했는데 consumer가 안 떠서
  DB에 영영 반영되지 않는 등)가 생길 수 있다.

이번 이슈는 Lua 알고리즘이나 Redis 상태 동기화가 아니라, DB 버전과 Redis 버전을 프로필로
스위칭할 수 있는 인터페이스/구현체 골격을 먼저 만드는 것이다.

## 목표

`BidExecutor` 인터페이스를 도입하고 `DbBidExecutor`(`@Profile("!redis")`)와
`RedisBidExecutor`(`@Profile("redis")`)로 나눈다. DB 구현체는 기존
`participateInternal()` 로직을 동작 변화 없이 그대로 이관한다. Redis 구현체는 실제 판단
로직 없는 placeholder Lua Script를 EVAL하는 골격만 구현한다.

## 범위

### 포함
- `BidExecutor` 인터페이스 + `BidCommand`
- `DbBidExecutor`: 기존 `participateInternal` 로직 이관, `@Transactional` 경계를 이
  구현체 안으로 한정
- `AuctionClosingService` 추출: `closeLockedAuction`과 그 헬퍼를 `AuctionCommandService`와
  `DbBidExecutor`(즉시구매 시 낙찰 처리를 위해)가 공유할 수 있도록 분리
- `RedisBidExecutor`: RedisScript EVAL + placeholder Lua Script, 결과를
  `BidResponses.BidResult`로 매핑하는 골격
- `AuctionCommandService.participate()`를 `BidExecutor` 호출로 위임하는 얇은
  오케스트레이터로 정리(`@Transactional` 제거, 메트릭 래핑은 유지)
- 두 프로필 각각에서의 빈 선택/동작을 검증하는 테스트, 기존 테스트 이전/분리

### 제외 (다른 작업)
- Lua Script의 실제 판단 로직(가격/자기입찰/최고입찰자/지갑 잔액 검증 등)
- Redis 상 auction/wallet 상태 시딩 및 동기화 전략
- Redis Pub/Sub 기반 SSE 전파 전환
- 경매 마감 스케줄러(`closeAuction`/`closeDueAuction`) 자체의 Redis 전환 — 마감은 지금처럼
  DB 락 기준으로 유지한다. `AuctionClosingService` 추출은 이 두 경로의 동작을 바꾸지 않고
  그대로 위임만 한다.

## 설계

### 패키지 구조

새 클래스는 `com.dbidding.auction.bid` 패키지에 둔다(#323이 `com.dbidding.auction.stream`을
쓴 것과 같은 결로, 관심사별 하위 패키지 분리).

```
com.dbidding.auction.bid
├── BidCommand.java          record(auctionId, bidderId, price, idempotencyKey)
├── BidExecutor.java         interface execute(BidCommand): BidResponses.BidResult
├── DbBidExecutor.java       @Service @Profile("!redis")
├── RedisBidExecutor.java    @Service @Profile("redis")
└── RedisBidLuaConfiguration.java   @Profile("redis") RedisScript<String> 빈

com.dbidding.auction.service
└── AuctionClosingService.java   @Service (신규, closeLockedAuction 이관)
```

### 프로필 배타성

`DbBidExecutor`와 `RedisBidExecutor`가 같은 `BidExecutor` 타입이라, 정확히 하나만 존재해야
주입이 모호해지지 않는다. `DbBidExecutor`엔 `@Profile("!redis")`, `RedisBidExecutor`엔
`@Profile("redis")`를 붙여 배타적으로 만든다(#323의 `AuctionBidStreamConsumer`는 `redis`
단독 조건만 있고 반대편 빈이 없어서 신경 쓸 필요가 없었던 것과 다른 점).

## 구현 내용

### 1. `BidCommand`/`BidExecutor`

```java
public record BidCommand(Integer bidderId, Integer auctionId, Long price, String idempotencyKey) {}

public interface BidExecutor {
    BidResponses.BidResult execute(BidCommand command);
}
```

`BidCreateRequest`를 그대로 넘기지 않고 필요한 필드만 담은 별도 record로 만드는 이유: 나중에
Redis 구현체가 Lua ARGV로 직렬화하기 쉬운 원시값 위주 형태가 필요하고, HTTP DTO
(`BidCreateRequest`)에 `BidExecutor`가 의존하지 않게 하기 위함.

### 2. `AuctionClosingService` 추출 (`AuctionCommandService.java:479-505`, `554-571`, `530-552`)

`closeLockedAuction`, `closeResponse`, `closedWinningBid`, `publishAuctionClosed`을 그대로
옮긴다. 이 메서드들은 `bidRepository`, `cardService`, `walletService`, `orderService`,
`auctionEventPublisher`에 의존하는데, 이관 후 `AuctionCommandService`는 이 중
`bidRepository`/`walletService`/`orderService`를 더 이상 쓰지 않게 된다(`cardService`는
`create()`가 여전히 써서 유지, `auctionEventPublisher`는 `create()`의 `publishOpened`가
써서 유지).

- `AuctionCommandService.closeAuctionInternal()`은 `closedWinningBid`/`closeResponse` 호출을
  `auctionClosingService.closedWinningBid(...)`/`auctionClosingService.closeResponse(...)`로,
  `closeLockedAuction(...)` 호출을 `auctionClosingService.closeLockedAuction(...)`로 바꾼다.
- `closeAuction()`/`closeDueAuction()`의 메트릭 래핑(`Timer.Sample`, `CloseResult`)과
  `findByIdForUpdate`(`LockOperation.CLOSE`)는 그대로 `AuctionCommandService`에 남는다 —
  이번 작업은 마감 경로의 동작을 바꾸지 않는다.
- `DbBidExecutor`는 즉시구매(buyNow) 입찰 시 `auctionClosingService.closeLockedAuction(auction, bidAt)`을
  호출해 낙찰 처리를 위임한다(기존 `participateInternal` 216번째 줄의 직접 호출과 동일한
  자리, 같은 트랜잭션 안에서 실행되는 것도 동일).

### 3. `DbBidExecutor` — `participateInternal` 이관 (`AuctionCommandService.java:160-243`과
그 헬퍼들: `bidPrice`, `isBuyNowBid`, `validateNotSellerBid`, `validateNotCurrentLeadingBidder`,
`findIdempotentBidResponse`, `placeBid`, `holdBidAmount`, `outbidPreviousLeadingBid`,
`requiresPreviousHoldRelease`, `shouldReleasePreviousHoldFirst`, `publishBidPlaced`,
`highestBid`, `bidResult`, `validateIdempotencyKey`, `bidRequestHash`, `sha256`,
`appendDigestValue`, `findByIdForUpdate`(BID 전용))

로직/로그/메트릭 순서는 전부 동일하게 유지한다. 필요한 의존성:
`auctionRepository`, `bidRepository`, `walletService`, `auctionEventPublisher`,
`ApplicationEventPublisher`(close_time_extended 이벤트용), `clock`, `auctionMetrics`,
`auctionClosingService`.

`validateIdempotencyKey`/`sha256`/`appendDigestValue`는 `AuctionCommandService.create()`도
그대로 쓰고 있어서(`createRequestHash`), 두 클래스에 중복 정의하는 대신
`com.dbidding.auction.bid` 아래 작은 유틸(예: `RequestHashes`, static 메서드)로 뽑아서
양쪽이 같이 쓴다. 로직 변경은 없다.

`execute(BidCommand command)`에 `@Transactional`을 건다 — 지금 `participate()`에 걸려있던
것과 동일한 경계(같은 스레드, 같은 메서드 안에서 시작/커밋)이므로 동작 변화 없음.

### 4. `RedisBidExecutor` — 골격만

```java
@Service
@Profile("redis")
@RequiredArgsConstructor
public class RedisBidExecutor implements BidExecutor {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<String>> bidStubScript;

    @Override
    public BidResponses.BidResult execute(BidCommand command) {
        List<String> raw = redisTemplate.execute(
                bidStubScript,
                List.of("auction:%d".formatted(command.auctionId())),
                String.valueOf(command.bidderId()), String.valueOf(command.price())
        );
        // TODO(#?): 실제 Lua 판단 로직이 붙기 전까지는 응답 매핑이 placeholder다.
        ...
    }
}
```

`bid-stub.lua`(`backend/src/main/resources/lua/bid-stub.lua`)는 실제 판단 없이 인자를
그대로 반환하는 정도로만 작성해서 EVAL 왕복과 `RedisScript` 빈 배선이 되는지만 확인한다.
이 스텁으로는 의미 있는 `BidResult`를 만들 수 없으므로(Redis 쪽에 auction/wallet 상태가
아직 없음), `RedisBidExecutor` 빈 생성 시점에 `log.warn`으로 "placeholder 구현, 실제
서비스에 쓰면 안 됨"을 남긴다. `redis` 프로필은 이번 작업 이후에도 로컬/스테이징 실험
용도로만 켠다.

### 5. `AuctionCommandService.participate()` 정리

```java
public BidResponses.BidResult participate(
        Integer userId, Integer auctionId, BidCreateRequest request, String idempotencyKey
) {
    Timer.Sample sample = auctionMetrics.start();
    try {
        BidResponses.BidResult result = bidExecutor.execute(
                new BidCommand(userId, auctionId, request.price(), idempotencyKey)
        );
        auctionMetrics.finishBid(sample, BidResult.ACCEPTED);
        return result;
    } catch (AuctionException exception) {
        auctionMetrics.finishBid(sample, BidResult.REJECTED);
        throw exception;
    } catch (RuntimeException exception) {
        auctionMetrics.finishBid(sample, BidResult.ERROR);
        throw exception;
    }
}
```

`@Transactional` 제거(더 이상 이 메서드 안에서 직접 DB 접근을 하지 않음 — `DbBidExecutor`가
자체 트랜잭션을 가짐). 컨트롤러 쪽 호출부(`AuctionController.java:55`)는 시그니처가 그대로라
변경 없음.

### 6. 설정 파일

- `backend/src/main/resources/lua/bid-stub.lua` 추가
- `RedisBidLuaConfiguration`(`@Profile("redis")`)에서 `RedisScript` 빈 등록 — 이미 있는
  `spring-boot-starter-data-redis` 의존성/연결 설정(`application.yml:11-19`,
  `application-redis.yml`)을 그대로 쓰고 새 설정 추가는 없음

## 테스트 영향

기존 코드를 수정하므로 관련 테스트를 모두 찾아서 반영한다.

- `AuctionServiceBidTest.java`(291줄, 입찰 로직 전체를 다룸) → 이 파일을 삭제하고
  `com.dbidding.auction.bid.DbBidExecutorTest`로 새로 옮겨서, `DbBidExecutor`를 직접
  생성해 같은 케이스(자기입찰 거부, 최고입찰자 중복 거부, wallet hold/release 순서,
  멱등 응답, 즉시구매 시 낙찰 위임)를 검증한다. `auctionClosingService`는 mock으로 대체하고
  `closeLockedAuction` 호출 여부만 검증(실제 낙찰 로직은 아래 새 테스트가 담당).
- `AuctionServiceCloseTest.java`(185줄) → `closeLockedAuction`의 실제 낙찰/유찰 로직
  (`walletService.capture`, `orderService.createFromAuctionClosed`, `publishAuctionClosed`
  검증)은 새 `AuctionClosingServiceTest`로 옮긴다. 남는 `AuctionServiceCloseTest`는
  `closeAuction`/`closeDueAuction`의 오케스트레이션(메트릭, ENDED/FAILED 단락, lock 재시도
  경로)만 검증하도록 축소하고, `auctionClosingService`는 mock으로 대체.
- `AuctionRegistrationContractTest.java`(217줄, `create()` 대상) → 생성자 인자만 갱신
  (`bidRepository`/`walletService`/`orderService` 제거, `bidExecutor`/`auctionClosingService`는
  안 쓰이므로 mock 또는 null로 채움). 검증 로직 변경 없음.
- `AuctionBidWalletLockOrderConcurrencyTest.java`(298줄, Testcontainers, Spring 컨텍스트로
  `participate()` 호출) → DI로 빈을 받으므로 생성자 변경엔 영향 없음. 다만 기본 프로필(`!redis`)로
  뜨는지 확인하는 것으로 충분하고, 이 테스트가 검증하는 교차 wallet lock 순서(`shouldReleasePreviousHoldFirst`)
  로직은 그대로 `DbBidExecutor`로 옮겨가므로 통과 여부로 이관 정확성을 재확인한다.
- 신규: `DbBidExecutor`/`RedisBidExecutor` 중 정확히 하나만 빈으로 뜨는지 확인하는 프로필
  스위칭 테스트(`@ActiveProfiles`로 각각 슬라이스 컨텍스트 로드, `BidExecutor` 빈 타입 검증).

## 결론

`participateInternal`의 판단/락 로직은 그대로 `DbBidExecutor`로 옮기고, 즉시구매가 트리거하는
낙찰 처리는 `AuctionClosingService`로 뽑아 `AuctionCommandService`(마감 스케줄러 경로)와
공유한다. `RedisBidExecutor`는 이번 단계에서 실제 판단 없는 스텁이며, `redis`/`!redis`
프로필로 정확히 하나의 구현체만 뜨게 해서 #323의 Stream consumer 스위치와 하나의 토글로
묶는다. 다음 단계(Lua 판단 로직, Redis 상태 동기화, Pub/Sub SSE 전환)는 별도 이슈로 분리한다.

> 이 문서는 Claude의 도움을 받아 작성하였습니다.
