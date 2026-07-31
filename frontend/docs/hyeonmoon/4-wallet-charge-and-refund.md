# Frontend Wallet 충전·환불 구현 계획

## 구현 상태

구현 완료. Header의 충전 팝업과 마이페이지 Wallet 카드의 환불 팝업이 실제
백엔드 모의 거래 API를 호출한다. 거래 성공 뒤 로컬 잔액을 계산하지 않고
`walletQueryKeys.balance()`를 무효화해 서버 잔액을 다시 조회한다.

## 목표

현재 Header의 로컬 포인트 증가를 실제 모의 충전 API로 교체하고, 마이페이지에
가용액 기반 모의 환불을 추가한다. 실제 PG나 계좌 송금은 구현하지 않으며
백엔드 PointRecord와 멱등성 계약을 그대로 사용한다.

## 백엔드 계약

### 충전

```http
POST /api/wallet/charges
Authorization: Bearer <access-token>
Idempotency-Key: <uuid>
Content-Type: application/json

{ "amount": 50000 }
```

### 환불

```http
POST /api/wallet/refunds
Authorization: Bearer <access-token>
Idempotency-Key: <uuid>
Content-Type: application/json

{ "amount": 10000 }
```

응답 형식은 동일하다.

```json
{
  "transactionId": 1,
  "transactionType": "CHARGE",
  "amount": 50000,
  "balance": 50000
}
```

- 충전은 1,000원 이상이다.
- 충전·환불 금액은 양수다.
- 환불은 활성 hold를 제외한 가용 잔액 이하여야 한다.
- `Idempotency-Key`는 필수이며 최대 64자다.
- 같은 Wallet에서 같은 키와 다른 요청 내용을 사용하면 409다.

## 프론트 구조

```text
frontend/src/
├── api/walletApi.ts
├── dto/walletDto.ts
├── queries/walletMutations.ts
└── components/wallet/
    ├── WalletChargeDialog.tsx
    ├── WalletChargeDialog.test.tsx
    ├── WalletRefundDialog.tsx
    └── WalletRefundDialog.test.tsx
```

Header는 다이얼로그 열림 여부만 관리하고 금액 state와 mutation은 각
다이얼로그가 소유한다. 성공 뒤 로컬 포인트를 더하거나 빼지 않고 Wallet
Balance Query를 무효화한다.

## 멱등키 생명주기

```text
사용자가 새 거래 시작
→ crypto.randomUUID() 한 번 생성
→ POST 요청
→ 응답 유실·네트워크 오류 재시도: 같은 키 유지
→ 성공 또는 사용자가 거래 취소: 키 폐기
→ 새 충전·환불 시작: 새 키 생성
```

- 렌더링할 때마다 키를 생성하지 않는다.
- mutation 재실행 시 자동으로 새 키를 만들지 않는다.
- 충전과 환불 사이에 키를 공유하지 않는다.
- 409는 같은 요청을 자동 반복하지 않고 사용자에게 충돌을 안내한다.

## 충전 UI

- 최소 1,000원을 프론트에서도 검증한다.
- 빠른 선택 금액과 직접 입력 금액은 하나의 `amount` state를 사용한다.
- 실제 결제가 아닌 개발용 모의 충전임을 명시한다.
- 제출 중 닫기·중복 제출 정책을 일관되게 적용한다.
- 성공 뒤 서버가 반환한 거래 금액을 안내하고 Wallet Query를 다시 조회한다.

## 환불 UI

- 현재 `availableBalance`를 최대 환불 가능액으로 표시한다.
- 0원 이하 또는 가용액 초과 입력은 제출 전에 안내한다.
- 프론트 검증을 통과해도 서버의 동시성·hold 검증을 최종 기준으로 사용한다.
- 성공 뒤 반환 금액과 최신 Wallet Balance를 표시한다.
- 실제 계좌 환불이나 지급 완료로 오해할 문구를 사용하지 않는다.

## 오류 처리

| 상태 | 처리 |
|---|---|
| 400 | 최소 금액·양수·요청 형식 확인 |
| 401 | 인증 Refresh 흐름 사용 |
| 404 | Wallet을 찾지 못했다는 안내 |
| 409 | 잔액 부족 또는 멱등키 충돌 공통 안내 |
| 네트워크 오류 | 입력과 멱등키를 유지하고 명시적 재시도 제공 |
| 5xx | 입력 유지, 자동 반복 요청 금지 |

백엔드가 잔액 부족과 멱등키 충돌을 구조화된 오류 코드로 나누면 409 메시지를
세분화한다. 그 전에는 응답 문자열을 파싱하지 않는다.

## 필수 테스트

- 충전 1,000원 미만과 환불 0원 이하는 요청하지 않는다.
- 환불 가용액 초과를 안내한다.
- 같은 거래의 네트워크 재시도는 같은 Idempotency-Key를 사용한다.
- 새 거래는 이전 거래와 다른 키를 사용한다.
- 제출 중 중복 클릭이 요청을 늘리지 않는다.
- 성공 뒤 Wallet Balance Query를 무효화한다.
- 409와 네트워크 오류 뒤 입력값이 유지된다.

## 완료 기준

- [x] Header의 로컬 Wallet 증가 state가 제거된다.
- [x] 충전·환불이 실제 백엔드 원장 API를 통해 반영된다.
- [x] 모든 표시 잔액은 재조회한 서버 응답과 일치한다.
- [x] UI가 모의 거래임을 명확하게 표시한다.
- [x] 동일 거래의 명시적 재시도는 기존 멱등키를 유지한다.
- [x] 새 금액 선택과 새 팝업은 새로운 멱등키를 사용한다.
- [x] 타입 검사, API·컴포넌트 테스트, 프로덕션 빌드를 통과한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
