# Wallet 개발 계획

Wallet은 사용자의 총 포인트, 실제 잔액 변경 원장, 경매에 묶인 금액을 관리한다. 실제 PG와 판매자 정산·출금은 이번 범위에 포함하지 않는다.

## 구현 단계

1. [Wallet 엔티티](1-entity.md) — 완료
2. [회원가입 Wallet 생성 연동](2-wallet-provisioning.md) — 완료
3. [모의 충전·환불과 포인트 원장](3-charge-and-refund.md) — 완료
4. [지갑 잔액 조회](4-balance-query.md) — 완료
5. [Auction 입찰 홀드·해제·낙찰 차감 연동](5-auction-wallet-integration.md) — 완료
6. [소비자 소유 Port·Adapter 경계 리팩터링](6-consumer-owned-port-adapter-refactor.md) — 완료

충전·환불 원장과 Wallet 잠금을 먼저 구현해야 잔액 조회와 Auction 연동이 같은 계산식과 동시성 규칙을 재사용할 수 있다.

Account의 `WalletProvisioningPort`와 Auction의 `WalletPort`는 각 소비자 도메인이
연결 Adapter와 함께 소유한다. Wallet은 자금 규칙을 제공하는 `WalletService`와
Entity·Repository를 소유하며 Account·Auction의 Port를 import하지 않는다.

## 잔액 정의

```text
totalBalance = wallets.point
frozenBalance = SUM(wallet_holds.amount WHERE status = 'HELD')
availableBalance = totalBalance - frozenBalance
```

모든 금액은 MySQL `BIGINT`, Java `long`이다. `wallets`에 `held_amount` 같은 중복 집계 컬럼을 추가하지 않는다.

## 자금 흐름

| 동작 | 총잔액 | 동결액 | 가용액 | 기록 |
|---|---:|---:|---:|---|
| 충전 | 증가 | 불변 | 증가 | `point_records.CHARGE` 양수 |
| 모의 환불 | 감소 | 불변 | 감소 | `point_records.REFUND` 음수 |
| 입찰 | 불변 | 증가 | 감소 | `wallet_holds.HELD` |
| 상회 입찰 | 불변 | 감소 | 증가 | `HELD → RELEASED` |
| 낙찰 | 감소 | 감소 | 원칙상 불변 | `HELD → CAPTURED`, `AUCTION_CAPTURE` 음수 |

입찰 hold와 낙찰 차감 금액은 `입찰가 + 배송비`다. 같은 사용자가 같은 경매에서 금액을 올리면 기존 활성 hold와 새 총액의 차이만 추가로 확보한다.

## API

| Method | Path | 기능 |
|---|---|---|
| POST | `/api/wallet/charges` | `Idempotency-Key` 기반 모의 포인트 충전 |
| POST | `/api/wallet/refunds` | 가용 잔액 범위의 모의 포인트 환불 |
| GET | `/api/wallet` | 로그인 사용자의 총액·동결액·가용액 조회 |

충전·환불은 실제 PG를 호출하지 않는다. 동일 `Idempotency-Key`와 동일 요청은 최초 결과를 반환하며, 같은 key를 다른 금액이나 거래 유형으로 재사용하면 409다.

Wallet 거래의 409 응답은 프론트가 원인을 안정적으로 구분할 수 있도록 다음
오류 코드를 제공한다.

| code | 조건 |
|---|---|
| `INSUFFICIENT_AVAILABLE_BALANCE` | 활성 hold를 제외한 가용 잔액보다 환불액이 큼 |
| `IDEMPOTENCY_CONFLICT` | 같은 멱등키를 다른 금액이나 거래 유형에 재사용 |

## 동시성 원칙

- 충전·환불·홀드·해제·낙찰 차감은 모두 대상 wallet row를 `PESSIMISTIC_WRITE`로 잠근다.
- Auction은 공개된 `WalletPort` 메서드만 호출하고 Wallet의 락 메서드나 Repository를 직접 사용하지 않는다.
- 입찰과 Wallet 변경은 같은 Spring 트랜잭션에서 동기 처리한다.
- SSE·알림 이벤트만 DB 커밋 이후 처리한다.
- 실제 잔액 변경과 `point_records` 저장은 반드시 한 트랜잭션이다.

## 거래 유형과 멱등성

- `CHARGE`: 양수, `auction_id=null`, `idempotency_key` 필수
- `REFUND`: 음수, `auction_id=null`, `idempotency_key` 필수
- `AUCTION_CAPTURE`: 음수, `auction_id` 필수, WalletHold의 CAPTURED 상태로 중복 차감 방지
- hold와 release는 실제 총잔액 변경이 아니므로 `point_records`에 남기지 않는다.

## 완료 기준

- 회원가입 트랜잭션 안에서 초기 잔액 0인 Wallet이 정확히 하나 생성된다.
- 충전 최소 금액 1,000원과 환불 가용 잔액 제한을 지킨다.
- 동일 충전·환불 요청이 잔액과 원장을 중복 변경하지 않는다.
- 활성 hold가 없으면 동결액은 0이고 RELEASED·CAPTURED는 합산하지 않는다.
- 입찰가와 배송비 전체를 hold하고 낙찰 때만 총잔액을 실제 차감한다.
- Wallet 자금 정합성을 비동기 이벤트 처리에 맡기지 않는다.
- 판매자 정산·출금과 실제 PG 연동을 구현했다고 주장하지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
