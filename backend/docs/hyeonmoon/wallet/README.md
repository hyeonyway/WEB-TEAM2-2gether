# Wallet 개발 계획

Wallet은 사용자의 총 포인트와 경매에 묶인 금액을 관리한다. 이번 문서 범위는 Wallet 생성과 잔액 조회까지이며 충전·환불 원장과 홀드 상태 전이는 후속 계획으로 분리한다.

## 구현 단계

1. [Wallet 엔티티](1-entity.md)
2. [회원가입 Wallet 생성 연동](2-wallet-provisioning.md)
3. [지갑 잔액 조회](3-balance-query.md)

## 잔액 정의

```text
totalBalance = wallets.point
frozenBalance = SUM(wallet_holds.amount WHERE status = 'HELD')
availableBalance = totalBalance - frozenBalance
```

모든 값은 MySQL `BIGINT`, Java `long`이다. `wallets`에 `held_amount` 같은 중복 집계 컬럼을 추가하지 않는다.

## API

| Method | Path | 기능 |
|---|---|---|
| GET | `/api/wallet` | 로그인 사용자의 총액·동결액·가용액 조회 |

## 완료 기준

- 회원가입 트랜잭션 안에서 초기 잔액 0인 Wallet이 정확히 하나 생성된다.
- 활성 hold가 없으면 동결액은 0이다.
- 여러 활성 hold가 있으면 합계가 정확하다.
- Wallet이나 hold가 없어도 정의된 도메인 예외 또는 0 합계로 일관되게 처리한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
