# Frontend Wallet 잔액 구현 계획

## 목표

Header와 마이페이지의 하드코딩 포인트를 실제 Wallet 조회 API로 교체한다.
총잔액, 동결액, 가용액의 의미를 구분하고 서버 응답을 Wallet 상태의 단일
원본으로 사용한다.

## 백엔드 계약

```http
GET /api/wallet
Authorization: Bearer <access-token>
```

```json
{
  "totalBalance": 850000,
  "frozenBalance": 120000,
  "availableBalance": 730000
}
```

- `totalBalance`: Wallet에 실제로 남은 전체 포인트
- `frozenBalance`: 활성 `wallet_holds`의 합계
- `availableBalance`: 새 입찰·환불에 사용할 수 있는 금액

프론트에서 `totalBalance - frozenBalance`를 다시 계산하지 않는다. 백엔드가
동결 정책을 바꾸더라도 화면은 `availableBalance`를 그대로 사용한다.

## 프론트 구조

```text
frontend/src/
├── dto/walletDto.ts
├── api/walletApi.ts
├── queries/
│   ├── walletQueries.ts
│   └── walletQueryKeys.ts
└── components/wallet/
    └── WalletBalance.tsx
```

```ts
type WalletBalanceDto = {
  totalBalance: number;
  frozenBalance: number;
  availableBalance: number;
};
```

MVP에서는 Wallet 잔액 필드를 프론트 `number`로 받되, `totalBalance`,
`frozenBalance`, `availableBalance`와 거래 후 잔액은 모두 0 이상
`9,007,199,254,740,991` 이하의 안전한 정수임을 백엔드가 보장해야 한다.
현재 백엔드에는 이 상한 검증이 없으므로 Wallet 프론트 연동 전에 거래 입력뿐
아니라 거래 결과 잔액에도 상한 검증을 추가한다.

이 상한을 백엔드에서 보장할 수 없으면 일부 필드만 바꾸지 않고 모든 Wallet
요청·응답 금액을 10진수 문자열로 반환하고 프론트에서 `bigint` 또는 decimal로
변환하는 계약을 별도로 합의한다. 프론트는 안전한 정수가 아닌 숫자를 그대로
화면에 표시하지 않는다.

## Query 계약

Wallet Query key는 한 파일에서만 생성한다.

```ts
const walletQueryKeys = {
  all: ['wallet'] as const,
  balance: () => [...walletQueryKeys.all, 'balance'] as const,
};
```

- 인증 상태가 `authenticated`일 때만 조회한다.
- 로그인 사용자 변경 가능성이 있으므로 로그아웃 시 전체 Wallet key를 제거한다.
- 충전·환불·입찰 성공 뒤 `balance()`를 무효화한다.
- Header와 마이페이지는 같은 Query를 구독하며 별도 로컬 잔액을 만들지 않는다.

## 화면 연결

### Header

- 가용 잔액을 축약하지 않고 천 단위 구분으로 표시한다.
- `initializing`과 `anonymous` 상태에서는 전자지갑 영역 전체를 렌더링하지
  않는다.
- `authenticated`로 확정된 뒤 Wallet 조회 중에는 레이아웃 크기를 유지하는
  skeleton을 표시한다.
- 로그인 뒤에만 Wallet 가용 잔액과 충전 진입점을 표시한다.
- 화면 폭이 760px 이하로 줄어들어도 가용 잔액과 `충전하기`는 유지한다.
  공간이 부족하면 지갑 아이콘과 `내 전자지갑` 보조 문구만 숨긴다.

### 마이페이지

- 총잔액·동결액·가용액을 모두 표시한다.
- 가용액 설명에 활성 입찰의 동결액이 제외된다는 점을 안내한다.
- 충전·환불 버튼은 다음 단계의 다이얼로그 진입점만 제공한다.

### Auction 접점

이 단계에서는 Auction 파일을 직접 수정하지 않는다. Auction 담당자가 사용할
수 있도록 `useWalletBalance()`와 `WalletBalanceDto`를 export한다. 입찰 화면
연결 방식은 [Auction Wallet 접점](5-auction-wallet-integration.md)에서
정의한다.

## 오류 처리

| 상태 | 처리 |
|---|---|
| initializing | Wallet 요청과 Header 전자지갑 렌더링을 시작하지 않음 |
| anonymous | Wallet 요청을 하지 않고 Header 전자지갑 전체를 숨김 |
| authenticated, loading | Header와 마이페이지 Wallet 영역에 skeleton 표시 |
| 401 | 인증 계층의 Refresh 흐름 사용 |
| 404 | 지갑 준비 실패 안내, 0원으로 위장하지 않음 |
| 네트워크·5xx | 마지막 서버 값을 새 값처럼 표시하지 않고 재시도 제공 |

## 필수 테스트

- API 응답을 `WalletBalanceDto`로 반환한다.
- anonymous 상태에서 Wallet API를 호출하지 않는다.
- initializing·anonymous 상태에서 Header 전자지갑이 렌더링되지 않는다.
- authenticated 상태에서만 Header 전자지갑과 충전 진입점이 보인다.
- 760px 이하에서도 Header 가용 잔액이 숨겨지지 않는다.
- Header와 마이페이지가 같은 Query cache 값을 표시한다.
- 총잔액·동결액·가용액의 각 label이 올바른 필드와 연결된다.
- 로그아웃 시 Wallet Query가 제거된다.
- 404와 네트워크 오류를 0원 잔액으로 표시하지 않는다.

## 완료 기준

- `850,000` 등 하드코딩 Wallet 금액이 Account·Wallet 소유 화면에서 제거된다.
- 로그인 전에는 Header에 전자지갑과 충전 진입점이 노출되지 않는다.
- 화면이 가용액을 직접 재계산하지 않는다.
- Header와 마이페이지가 같은 서버 상태를 표시한다.
- Auction 담당자에게 재사용 가능한 조회 hook과 Query key가 제공된다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
