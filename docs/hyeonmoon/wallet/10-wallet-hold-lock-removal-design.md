# wallet_holds 개별 락 제거 설계

**관련 이슈:** [#393](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/393)

**배경 문서:** [`docs/hyeonmoon/auction/1-bid-wallet-lock-ordering-deadlock.md`](../auction/1-bid-wallet-lock-ordering-deadlock.md)(지갑 순서 규칙 원본),
[`docs/hyeonmoon/observability/6-capacity-postmerge-retest.md`](../observability/6-capacity-postmerge-retest.md)(오늘 부하테스트에서 발견한 근거)

---

## 1. 문제

dev→main 배포 후 부하테스트 도중 `wallet_holds`/`wallets` 교차 데드락이
36건 발생했다(배포 후 약 2시간). 실제 InnoDB 데드락 그래프
(`SHOW ENGINE INNODB STATUS`)로 확인한 사례:

```
*** TX1(트랜잭션 1292400): lockWallet(user_id=910491) 대기 중
    이미 wallet_holds PK 행(heap72, status=HELD) 배타 잠금 보유

*** TX2(트랜잭션 1292384): sumHeldAmountForUpdate(wallet_id=523) 실행 중
    이미 wallets 행(heap523, 같은 유저) 배타 잠금 보유
    TX1이 든 wallet_holds 행(heap72) 대기 → 데드락 감지, TX2가 희생되어 롤백
```

`EXPLAIN`으로 확인한 결과 인덱스 문제는 아니다(`idx_wallet_holds_wallet_status`,
`ref` 타입, `filtered=100%` 정확히 사용).

## 2. 원인

`DbBidExecutor.shouldReleasePreviousHoldFirst()`는 한 트랜잭션이 건드리는
**지갑 2개 사이의 순서**만 보장한다(작은 userId 먼저 잠금):

```java
// DbBidExecutor.java
private boolean shouldReleasePreviousHoldFirst(Bid previousLeadingBid, Integer currentBidderId) {
    return requiresPreviousHoldRelease(previousLeadingBid, currentBidderId)
        && previousLeadingBid.getBidderId() < currentBidderId;
}
```

이 규칙은 **같은 두 지갑을 반대 순서로 잠그려는 두 트랜잭션 사이의 2-사이클**은
막지만, 서로 다른 두 개의 독립 거래(예: 유저 X가 경매 A에서 유저 Y를
이겨서 Y의 홀드를 풀면서 동시에 유저 Z가 경매 B에서 X를 이겨서 X의 홀드를
푸는 상황처럼 3개 이상 지갑이 얽힌 사이클)는 막지 못한다.

**그런데 지갑당 잠금 자원이 원래 하나(지갑 행)여야 하는데, 지금 코드는
`wallet_holds`에 별도 락을 하나 더 건다:**

```java
// WalletHoldRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<WalletHold> findFirstByWalletIdAndAuctionIdOrderByIdDesc(Integer walletId, Integer auctionId);

// WalletRepository.java
@Query(value = "... FOR UPDATE", nativeQuery = true)
long sumHeldAmountForUpdate(@Param("walletId") Integer walletId);
```

지갑당 잠금 자원이 2개(지갑 행 + 홀드 행)가 되면서, 앞서 말한 3자 이상
사이클이 이 두 번째 자원을 경유해서 실제로 생길 수 있게 된다 — 오늘 잡힌
데드락 그래프가 정확히 이 패턴이다.

## 3. 왜 지금 제거해도 안전한가

### 3.1 `redis` 프로필 꺼진 상태(현재 prod)

`wallet_holds`를 건드리는 코드는 `WalletService.hold/release/capture`
(항상 `lockWallet()`을 먼저 호출) **하나뿐**이고, 이 클래스는
`@Profile("!redis")`다. 호출자는:

- `DbBidExecutor`(`@Profile("!redis")`) — 동기 입찰 경로
- `AuctionCommandService.closeDueAuction`(→ `AuctionDueClosingService`,
  경매 마감 스케줄러의 DB 경로) — `@Profile` 없이 항상 활성이지만,
  스케줄러 자체가 `DbAuctionCloseSchedulerProcessor`(`@Profile("!redis")`)를
  거쳐야만 호출된다

즉 **지갑 행 락이 이미 해당 지갑의 홀드 데이터 전체를 배타적으로 보호**하고
있으므로(동시에 이 지갑을 건드리려는 다른 트랜잭션은 `lockWallet()` 단계에서
막힌다), `wallet_holds` 전용 락은 순수하게 중복이다.

### 3.2 `redis` 프로필 켜진 상태(향후)

#387(`feature/redis-auction-close-scheduler`) 병합으로 확인된 구조:

- 입찰 수락은 `RedisBidExecutor`(Lua, `bid-accept.lua`)가 처리하고
  `WalletService.hold/release`를 아예 호출하지 않는다(지갑 가용/동결
  잔액 계산을 Lua 안에서 직접 함).
- 경매 마감은 `RedisAuctionCloseSchedulerProcessor`가 MySQL을 직접
  건드리지 않고 Lua로 Redis 상태만 `ENDED`로 바꾸고 `auction.close-requested.v1`
  이벤트를 stream에 남긴다.
- 이 이벤트는 `AuctionBidStreamConsumer`의 **단일 스레드**
  (`Executors.newSingleThreadExecutor()`)가 순서대로 소비해
  `AuctionBidStreamPersistenceService.project()` → `closeAuction()` →
  `walletService.capture()`를 호출한다.
- 입찰 이벤트 재생(`persistBid` → `applyWalletTransition` →
  `walletService.hold/release`)도 **같은 단일 스레드, 같은 `project()`
  디스패치**를 거친다.

**결론: redis 켜진 상태에서 `WalletService.hold/release/capture`(MySQL 락
버전)를 호출하는 경로는 전부 하나의 단일 스레드 안에서 순서대로만
실행된다.** 동시성 자체가 없으므로 락 유무가 correctness에 영향을 주지
않는다.

`WalletProjectionService`(redis stream의 지갑 상태변경 이벤트 소비, 낙관적
버전 체크 `projectionVersion` 사용)는 원래도 락이 없었고 이번 변경과 무관하다
— 얘도 같은 단일 스레드 안에서만 호출되므로 이미 안전하다.

### 3.3 과거에 이 락을 제거했다가 되돌린 이유(git history)

이 락은 한 번 "중복"이라며 제거됐다가 하루도 안 돼 데드락이 나서
되돌려졌다(당시 커밋 로그: `sumHeldAmountForUpdate`가 이미 지갑의 모든
`HELD` 행을 잠그니 중복이라는 논리로 제거 → 되돌림, 설명 없음). 당시엔
`WalletProjectionService`가 `lockWallet()`을 거치지 않고 `wallet_holds`를
직접 터치하는 경로였고, 경매 마감 캡처도 지금처럼 stream 단일 스레드로
통일돼 있지 않았을 가능성이 있다 — 즉 그때는 실제로 동시 접근 위험이
있었을 수 있다. **#387 병합으로 경매 마감 캡처까지 stream 단일 스레드로
통일된 지금은 그 위험이 없어졌다는 게 이번 재조사의 핵심 결론이다.**

## 4. 변경 사항

- `WalletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc`에서
  `@Lock(LockModeType.PESSIMISTIC_WRITE)` 제거
- `WalletRepository.sumHeldAmountForUpdate`(`FOR UPDATE` 버전) 제거,
  `WalletService.holdObserved/releaseObserved/captureObserved`의 호출부를
  기존에 이미 존재하는 락 없는 `sumHeldAmount`로 교체
- `findTopByWalletIdAndAuctionIdOrderByIdDesc`(락 없는, `WalletProjectionService`가
  쓰는 것과 같은 형태)와 `findFirstBy...`가 이제 완전히 동일한 동작이 되므로,
  후속 정리로 둘을 하나로 합치는 것도 검토(이번 PR 범위 밖으로 미룰 수 있음)

## 5. 안전성 재확인 계획

- 재현 테스트: `bid-only-load.js` 핫경매 집중 시나리오를 부하 중 앱 로그에
  `Deadlock found` 0건인지 확인(오늘 기준 36건 → 0건이 목표)
- 단위/통합 테스트로 지갑 락 순서(작은 userId 먼저) 여전히 correctness
  보장하는지 확인 — 특히 동시에 여러 지갑을 오가는 outbid 시나리오
- (참고용, 이번 변경과 별개) `docs/hyeonmoon/observability/6-capacity-postmerge-retest.md`
  2.3절 — 이 데드락 제거가 핫경매 집중 시나리오의 latency/처리량 자체를
  개선하지는 않는다(그건 지갑이 여러 경매에 걸쳐 공유되는 근본적인 직렬화
  지점이라 별개 문제). 이 변경의 목표는 **에러(데드락으로 인한 트랜잭션
  실패) 제거**이지 **속도 개선**이 아니다.

## 6. 롤백 계획

락을 다시 걸어야 하는 상황이 생긴다면(예: redis 프로필에서 스트림 컨슈머를
멀티스레드로 바꾸는 등 3.2절의 "단일 스레드" 전제가 깨지는 변경이 생기는
경우), 이 문서의 3.2절 전제 조건을 다시 확인한 뒤 락을 되돌려야 한다 —
즉 이 변경은 "현재 아키텍처(redis off는 DbBidExecutor 단일 호출자, redis on은
stream 단일 스레드)에 한정된 안전성 증명"이며, 두 전제 중 하나라도 깨지면
재검토가 필요하다.

> 이 문서는 Claude의 도움을 받아 작성하였습니다
