# 향후 병렬 Timeline Projection 설계

**선행 결정:** [단일 Timeline Queue · 단일 Consumer 설계](2026-08-12-single-timeline-queue-consumer-design.md)  
**관련 이슈:** #383  
**상태:** 향후 확장 검토용 — 현재 구현에는 적용하지 않음

## 목적

현재 `auction:timeline-events` 단일 Stream과 단일 consumer는 전역 순서를 보장하는 대신 MySQL
projection 처리량이 하나의 consumer에 제한된다. 병렬화가 필요해질 때는 경매별 순서를 유지하면서
서로 독립적인 경매를 동시에 처리할 수 있어야 한다.

지갑은 한 사용자가 여러 경매에 동시에 입찰할 수 있는 공유 자원이다. 특히 상회입찰 하나는 신규
입찰자의 hold뿐 아니라 **이전 최고 입찰자의 hold release**도 변경한다. 따라서 인기 경매에 반복 참여해
최고 입찰자가 되는 사용자는 본인이 새 요청을 보내지 않아도 여러 입찰 이벤트의 지갑 대상이 된다.

일반 사용자의 지갑 충돌 비율은 낮을 수 있지만, 이 hot wallet 시나리오는 실제로 발생할 수 있다. 이는
경매 shard 병렬화의 효율을 완전히 부정하지는 않되, 잔액·hold 정합성을 희소성에 기대지 않고 version
기반 낙관적 처리와 재시도로 보장해야 하는 이유다.

## 권장 구조: 경매 shard + 지갑 버전 기반 낙관적 projection

```text
Lua 승인
  ├─ auction:timeline-events:{shard-0}
  ├─ auction:timeline-events:{shard-1}
  └─ ... (shard = hash(auctionId) % N)
                 │
        shard별 단일 consumer
                 │
        MySQL auction/bid projection
                 │
  wallet event snapshot + walletVersion 낙관적 projection
                 ▼
            wallets / wallet_holds / point_records
```

1. producer는 `shard = stableHash(auctionId) mod N`으로 하나의 Stream을 선택한다. 같은 `auctionId`는
   항상 같은 shard에 기록되므로 해당 경매의 `auctionVersion`과 입찰 이력 순서는 shard consumer 하나가 보장한다.
2. shard마다 active consumer는 하나만 둔다. 서로 다른 shard는 동시에 DB projection한다.
3. 이벤트에는 Redis 승인 직후의 `walletVersion`, `availableBalance`, `frozenBalance`, hold snapshot을 포함한다.
4. MySQL 지갑 projection은 `projection_version < :walletVersion`일 때만 snapshot을 적용하는
   version 기반 낙관적 처리를 사용한다.
   늦게 도착한 더 낮은 세대 이벤트는 현재 지갑 상태를 되돌리지 않는다.
5. `eventId` 또는 Stream ID unique inbox로 같은 이벤트의 중복 이력 반영을 막는다.

경매의 현재가·최고 입찰자·마감 상태는 Redis Lua가 승인 시점에 결정하므로, 병렬 consumer는 이 결정을
다시 경쟁해서 재계산하지 않고 event snapshot을 MySQL에 투영한다.

## 지갑 충돌을 다루는 방법

### 승인 경로

동시 입찰·충전·환불은 모두 Redis Lua에서 같은 `wallet:balance:{userId}` hash를 갱신한다. 상회입찰은
이전 최고 입찰자의 같은 hash와 해당 auction별 hold도 갱신한다. Redis 단일 인스턴스에서는 Lua 실행
자체가 직렬화되므로, 두 경매의 입찰이 같은 지갑 가용 잔액을 동시에 소비해 음수가 되거나 release와
새 hold가 교차해 잘못 계산되는 일은 승인 단계에서 발생하지 않는다.

### MySQL projection 경로

서로 다른 경매 shard가 같은 지갑의 이벤트를 동시에 투영할 수 있다. 이때 DB wallet 행 잠금으로
명령을 재실행하면 병렬화 이점이 사라지고, 도착 순서에 따라 오래된 상태가 최신 상태를 덮을 위험도 있다.

따라서 병렬화 버전에서는 기존의 **version 기반 낙관적 처리**를 유지하되, 명령을 다시 실행하지 않고
승인 snapshot을 조건부로 projection한다.

```sql
UPDATE wallets
SET point = :availableBalance,
    frozen_point = :frozenBalance,
    projection_version = :walletVersion
WHERE user_id = :userId
  AND projection_version < :walletVersion;
```

- update count가 `1`이면 최신 snapshot을 반영한 것이다.
- update count가 `0`이면 같은 event 재전달이거나 더 최신 지갑 세대가 이미 반영된 것이다. 재전달이면
  멱등 처리로 끝내고, 정상 명령 처리에서 같은 지갑의 세대 충돌이라면 최신 snapshot을 다시 읽어 필요한
  이력 projection만 확인·재시도한다.
- `point_records`와 `wallet_holds`는 `event_id` unique 제약으로 멱등 insert/upsert 한다.
- hold 상태도 마지막 walletVersion을 저장해 더 낮은 세대 이벤트가 `HELD`/`RELEASED`/`CAPTURED`를
  되돌리지 못하게 한다.

이 방식에도 MySQL row lock은 짧은 조건부 UPDATE 동안 존재한다. 대부분의 이벤트는 서로 다른 wallet
행을 병렬로 갱신할 수 있지만, 아래 경우는 같은 지갑의 version 충돌을 정상적으로 예상해야 한다.

- 한 사용자가 여러 경매에 짧은 시간 간격으로 입찰한다.
- 인기 경매에서 같은 사용자가 이전 최고 입찰자로 반복 교체되어 hold release 대상이 된다.
- 입찰과 충전·환불·정산·취소 환불이 같은 지갑에 교차한다.
- consumer 장애 후 replay가 실시간 shard 처리와 겹친다.

충돌한 지갑은 version 조건 실패 후 최신 Redis 승인 snapshot을 기준으로 재시도한다. `eventId` 멱등
제약이 있으므로 재시도는 point record·hold 이력을 중복 생성하지 않는다. 즉 lock을 없애는 대신 그 범위를
“Redis에서 이미 확정된 snapshot을 기록하는 짧은 projection”으로 축소하고, hot wallet의 충돌은 낙관적
재시도 비용으로 국소화한다.

## 필요한 계약과 전제

| 항목 | 단일 consumer 현재 구조 | 병렬 shard 구조 |
| --- | --- | --- |
| 경매 순서 | 전역 Stream 순서 | 같은 auctionId의 shard 순서 |
| 지갑 순서 | 전역 Stream 순서 | walletVersion 조건부 projection |
| consumer 수 | 전역 1개 | shard별 1개 |
| DB wallet 처리 | 기존 명령 재실행 가능 | snapshot 조건부 upsert 필요 |
| failure/replay | global inbox 복구 | eventId 멱등 + shard별 inbox 복구 |
| Stream key | `auction:timeline-events` | `auction:timeline-events:{shard}` |

병렬화 전에 반드시 갖춰야 하는 조건은 다음과 같다.

- 모든 지갑 변경 이벤트가 `walletVersion`, 변경 후 available/frozen 잔액, hold snapshot, `eventId`를 포함한다.
- 어떤 경로도 MySQL WalletService의 hold/release/capture를 다시 실행하지 않고 event snapshot만 projection한다.
- `wallets.projection_version`, `wallet_holds.projection_version`, 이력 `event_id` unique 제약과 replay 테스트가 준비돼 있다.
- producer와 consumer가 동일한 shard 계산 함수와 shard 개수 변경 절차를 사용한다. shard 수를 즉시 바꾸면
  같은 auction의 순서가 두 Stream으로 갈라지므로, drain·migration 없이 변경하지 않는다.
- Redis Cluster로 확장할 경우 Lua의 모든 동시 갱신 key가 동일 hash slot에 있어야 한다. auction key와
  서로 다른 사용자 wallet key를 하나의 Lua에서 함께 다루는 현재 모델은 Cluster 전환 시 별도 key/트랜잭션
  설계가 필요하다.

## 도입 판단 기준

다음 관측값이 확인될 때만 병렬화를 별도 이슈로 시작한다.

- 단일 consumer의 지속 처리량보다 승인 유입량이 계속 높아 Stream lag가 정상 임계치를 초과한다.
- DB projection 시간이 병목이고, inbox 오류나 Redis 승인 처리량이 병목이 아님을 확인했다.
- 샘플링한 이벤트에서 동일 `userId`의 지갑 변경 충돌과 이전 최고 입찰자 release 충돌 비율을 측정해,
  hot wallet 재시도 비용보다 shard 병렬화 이득이 크다.
- snapshot projection·복구·중복 replay 통합 테스트가 단일 queue 환경에서 먼저 안정화됐다.

그 전에는 단일 queue의 backlog를 처리량 문제로 정확히 관측하고, 임계치 기반 입찰 제한과 consumer DB
최적화를 먼저 검토한다. 병렬화는 backlog를 감추는 방법이 아니라, 위 전제가 충족된 뒤의 확장 방법이다.
