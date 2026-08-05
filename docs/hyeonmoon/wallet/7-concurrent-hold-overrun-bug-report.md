# 동시 입찰 Hold 초과 버그 조사 보고서

이 문서는 부하 테스트 중 발견된 두 가지 증상 — ① Wallet 잔액 조회 refetch 부하,
② 입찰 hold 합계가 충전 총액을 초과하는 문제 — 중 ②를 로컬에서 재현하고
근본 원인을 특정한 결과를 남긴다. 코드 수정은 이 문서의 범위에 포함하지
않는다.

## 배경

부하 테스트 중 다음 두 가지가 관찰됐다.

1. 지갑 잔액(`GET /api/wallet`) 조회가 반복적으로 refetch되며 부하가 발생한다.
2. API로 입찰을 진행할 때 지갑의 hold(동결) 합계가 충전한 총액보다 커질 수
   있고, 한번 그렇게 되면 지갑에 접근할 수 없게 된다.

①은 조사 결과 별도의 설계 이슈로 확인됐다. 프론트의
`frontend/src/hooks/useAuctionWalletSync.ts`가 공개 경매 SSE 스트림의
`BID_PLACED`/`AUCTION_CLOSED` 이벤트 중 현재 로그인 사용자와 관련된 이벤트마다
`queryClient.invalidateQueries(walletQueryKeys.balance())`를 호출해
`GET /api/wallet`을 즉시 재조회한다. 입찰 이벤트 1건당 지갑 조회 1건이 붙는
구조라 부하 테스트처럼 입찰이 몰리는 상황에서는 조회도 그만큼 몰린다. 이 문서는
②에 집중한다.

## 증상 (②)

1. 동시에 여러 경매에 입찰하면 각 hold는 개별적으로 잔액 검증을 통과하지만,
   합계가 지갑 총액을 초과할 수 있다.
2. hold 합계가 총액을 초과한 뒤에는 `WalletService.getBalance()`의 불변식
   검증이 항상 실패해 `GET /api/wallet`이 계속 실패하고, 해당 사용자는 지갑
   조회 자체가 불가능해진다.

## 재현 환경

- MySQL 8.4 (Docker `mysql:8.4` — 프로젝트 테스트 코드/CI가 사용하는 버전과 동일)
- 백엔드: `debug-auth` 프로필로 로컬 실행 (`X-Debug-User-Id` 헤더로 인증 우회).
  `auction-mock` 프로필이 아닌, 실제 `WalletService`·실제 DB 경로를 사용했다.
- `schema.sql`과 `required-data/*.sql`을 순서대로 적용한 뒤, 재현을 위해 별도로
  격리된 테스트 유저(`id=99001`, 지갑 잔액 10,000원)와 격리된 테스트 경매 5개
  (`id=9999001`~`9999005`, 각각 현재가 5,000원, 입찰 단위 1,000원, 판매자는
  별도 유저 `99002`)를 직접 `INSERT`로 구성했다.

## 재현 절차와 결과

### 1) 완전 동시 요청 — 재현됨

5개의 서로 다른 경매에 동시에(같은 순간에 curl을 백그라운드로 병렬 실행)
6,000원씩 `POST /api/auctions/{auctionId}/bids`로 입찰했다.

```bash
for aid in 9999001 9999002 9999003 9999004 9999005; do
  curl -s -X POST "http://localhost:8080/api/auctions/$aid/bids" \
    -H "X-Debug-User-Id: 99001" \
    -H "Idempotency-Key: race-$aid-..." \
    -H "Content-Type: application/json" \
    -d '{"price":6000}' &
done
wait
```

결과: 5건 모두 `201 Created`. 각 응답은 개별적으로
`"wallet":{"available_balance":4000,"frozen_balance":6000}`를 보고했다 — 즉
각 트랜잭션이 서로의 hold를 전혀 보지 못한 채 "내가 처음 거는 hold"로
계산했다.

DB로 직접 확인한 결과:

```text
user_id=99001, point=10000, held_sum=30000, should_be_available=-20000
```

`wallet_holds`에 5건의 `HELD` 행(각 6,000원)이 모두 남아 있었다. 지갑 총액
10,000원에 대해 hold 합계 30,000원 — 3배 초과다.

이후 `GET /api/wallet`을 호출하면:

```json
{"status":500,"error":"Internal Server Error",
 "message":"지갑 잔액 상태가 올바르지 않습니다.",
 "path":"/api/wallet"}
```

`WalletService.validateFrozenBalance()`가 `InvalidWalletBalanceException`을
던지고, `WalletController.getBalance()`에는 이 예외에 대한 처리가 없어 그대로
500으로 노출된다. 증상 ②의 두 번째 절반("hold가 커지면 지갑 접근이 안 된다")과
정확히 일치한다.

### 2) 순차 요청 — 정상 동작 확인

같은 조건에서 두 요청을 순차적으로(A가 끝난 뒤 B 요청) 보내면:

- A(6,000원 입찰): `201`, `available_balance=4000, frozen_balance=6000`
- B(다른 경매에 6,000원 입찰): `409 Conflict`,
  `InsufficientAvailableBalanceException: 사용 가능한 잔액이 부족합니다.`

즉 검증 로직 자체(추가 hold 금액이 가용 잔액을 넘는지 확인하는 로직)는
올바르다. 문제는 순수하게 **동시성** 상황에서만 발생한다.

### 3) 순수 DB 레벨 락 검증 — 정상 동작 확인

Java/Spring 계층을 배제하고 `wallets` 테이블에 대한 `SELECT ... FOR UPDATE`만
raw SQL 세션 두 개로 직접 검증했다. 세션 1이 트랜잭션을 열고 8초간 유지하는
동안 세션 2의 같은 `FOR UPDATE` 조회는 세션 1이 커밋할 때까지 정확히
블로킹됐다(7초 대기 후 통과). 즉 `wallets.user_id`(UNIQUE 인덱스)에 대한
비관적 락 자체는 DB 레벨에서 정상 동작한다. 문제는 락이 안 걸리는 게
아니라, **락을 얻은 뒤 읽는 다른 값이 락과 무관하게 오래된 값을 본다**는
데 있다.

## 근본 원인

`WalletService.holdObserved()`([backend/src/main/java/com/dbidding/wallet/service/WalletService.java:118](../../../backend/src/main/java/com/dbidding/wallet/service/WalletService.java)):

```java
Wallet wallet = lockWallet(userId, Operation.HOLD);                  // wallets 행 FOR UPDATE — 정상
long frozenBefore = walletRepository.sumHeldAmount(wallet.getId());  // wallet_holds 는 평범한 SELECT
```

`sumHeldAmount`([backend/src/main/java/com/dbidding/wallet/repository/WalletRepository.java](../../../backend/src/main/java/com/dbidding/wallet/repository/WalletRepository.java))는
`FOR UPDATE`가 아닌 일반 native SELECT다.

MySQL InnoDB의 기본 격리 수준인 REPEATABLE READ에서는, 한 트랜잭션의 **첫
일반(non-locking) SELECT가 그 트랜잭션 전체에 쓰일 스냅샷을 고정**시키고,
이후 같은 트랜잭션의 모든 일반 SELECT는 테이블이 달라도 그 스냅샷을 그대로
본다. `AuctionCommandService.participate()` 트랜잭션은 `hold()`를 부르기 전에
이미 `findIdempotentBidResponse()`에서 `bids` 테이블에 일반 SELECT를 한 번
날린다 — 이 시점에 스냅샷이 고정된다.

여러 입찰 요청이 거의 동시에 시작되면:

1. 각 트랜잭션이 시작 직후 "아직 hold가 없다"는 상태의 스냅샷을 각자
   고정한다.
2. `wallets` 행 락은 요청이 들어온 순서대로 정상적으로 걸리고 풀린다 —
   FOR UPDATE는 항상 최신 커밋 데이터를 읽고 잠그므로 `wallet.getPoint()` 값
   자체는 정확하다.
3. 하지만 락을 얻은 뒤 실행하는 `sumHeldAmount()`는 일반 SELECT이므로, 락과
   무관하게 1번에서 고정된 스냅샷을 본다 — 그 사이 다른 트랜잭션이 커밋한
   hold를 못 본다.
4. 그 결과 `availableBefore` 계산이 항상 "hold 없음" 기준으로 이뤄지고,
   검증을 통과한 여러 hold가 누적된다.

즉 **`wallets` 행 자체는 제대로 잠기지만, `wallet_holds` 합계 조회가 그 락과
무관한 트랜잭션 스냅샷을 보기 때문에** 검증이 무력화된다. 이는 동시 요청
개수가 늘어날수록(부하가 커질수록) 재현 확률이 올라가는 성격의 버그이며,
실제 부하 테스트에서 관찰된 증상과 일치한다.

## 영향 범위

- 실제 프로덕션 경로(`WalletService` + `AuctionCommandService`의 실제 DB
  트랜잭션)에서 발생한다. `auction-mock` 프로필 전용이 아니다.
- 한 사용자가 짧은 시간에 서로 다른 경매 여러 건에 동시 입찰할 가능성이
  높아지는 상황(부하 테스트, 매크로/봇, 다중 탭 등)에서 재현 확률이 커진다.
- 한번 불변식이 깨지면 `WalletController.getBalance()`에 예외 처리가 없어
  해당 사용자의 `GET /api/wallet`이 이후 계속 500으로 실패한다. 이는 앞서
  ①에서 설명한 SSE 기반 자동 refetch(`useAuctionWalletSync`)와 맞물려,
  깨진 순간부터 오류가 반복적으로 눈에 띄게 노출되는 방식으로 나타난다.
- 별개로, `auction-mock` 프로필의 `FakeWalletAdapter`
  ([backend/src/main/java/com/dbidding/auction/adapter/FakeWalletAdapter.java](../../../backend/src/main/java/com/dbidding/auction/adapter/FakeWalletAdapter.java))에도
  "잔액 확인 후 기록"이 원자적이지 않은 별도 성격의 레이스가 존재하지만,
  이번에 재현한 실제 DB 경로의 버그와는 무관한 별개 코드(테스트/병행 개발용
  목 구현)다. 필요하면 별도 이슈로 다룬다.

## 권장 다음 단계 (구현은 이 문서 범위 밖)

- `wallet_holds` 합계를 락과 무관한 스냅샷에서 읽지 않도록 하는 방법 검토.
  예를 들어 `sumHeldAmount`를 잠금 읽기(`LOCK IN SHARE MODE`/`FOR UPDATE`)로
  바꾸거나, hold 관련 조회를 `wallets` 행 락 획득 이후의 새 스냅샷에서
  수행하도록 트랜잭션 구조를 조정하는 방안이 있다. 다만 `AGENTS.md`는 중복
  aggregate 컬럼(`wallets.held_amount` 같은) 추가를 명시적으로 금지하므로,
  캐시 컬럼 방식은 이 제약과 함께 재검토가 필요하다.
- `WalletController`가 `InvalidWalletBalanceException`을 처리하지 않는 문제는
  이번 재현 중 발견된 2차 결함이다. 근본 원인이 고쳐지더라도, 불변식이 깨진
  상태에서 조회 자체가 영구적으로 막히지 않도록 별도로 다뤄야 한다.
- 이번 재현에 사용한 로컬 fixture와 절차를 회귀 테스트로 남기는 것을 고려한다
  (`backend/src/test/java/com/dbidding/wallet/service/WalletTransactionConcurrencyTest.java`에
  "서로 다른 경매에 대한 동시 hold" 케이스 추가).

## 재현에 사용한 로컬 환경 메모

- `docker run mysql:8.4` (컨테이너명 `dbidding-mysql`, 포트 3307)
- 백엔드: `SPRING_PROFILES_ACTIVE=debug-auth`,
  `SPRING_DATASOURCE_URL`에 `allowPublicKeyRetrieval=true` 추가(로컬 MySQL 8.4
  기본 인증 플러그인 때문에 필요), `JWT_SECRET`은 로컬 테스트용 임의 값 사용.
- 위 컨테이너와 프로세스는 이 문서 작성 시점 기준 로컬에 계속 떠 있는 상태이며,
  후속 검증을 위해 유지할지 정리할지는 별도로 결정한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
