# Frontend Auction Wallet 접점 구현 계획

## 목표

Auction 담당자가 입찰 UI에서 실제 Wallet 가용액과 인증 상태를 사용할 수 있게
공통 hook·Query key·상태 갱신 계약을 제공한다. 입찰, 상회 입찰, 경매 종료와
낙찰 규칙은 Auction 프론트와 백엔드가 계속 소유한다.

## 백엔드 책임

입찰 자금 처리는 프론트가 Wallet API를 조합해 구현하지 않는다.

```text
Auction 입찰 API
→ AuctionService 트랜잭션
→ WalletPort
→ WalletService hold/release/capture
→ Auction 응답
```

- 입찰 성공 시 입찰가와 배송비를 합친 금액이 hold된다.
- 상회 입찰 해제와 낙찰 차감은 백엔드 트랜잭션에서 처리한다.
- 프론트는 `wallet_holds` 상태나 Wallet Repository 동작을 알지 않는다.
- 입찰 전에 Wallet 충전 API를 자동 호출하지 않는다.

## 제공 인터페이스

Account·Wallet 담당은 다음 공통 기능을 제공한다.

- `useWalletBalance()`: 총잔액·동결액·가용액 조회
- `walletQueryKeys.balance()`: 입찰 성공 뒤 무효화할 key
- `useAuthGate()`: 미인증 Wallet·입찰 동작에서 Auth 모달 진입
- `WalletBalance`: 금액 표시가 필요할 때 재사용 가능한 컴포넌트

Auction 담당은 입찰 mutation과 Auction Query key를 계속 소유한다.

## 화면 연결 계약

### 입찰 팝업 열기

```text
입찰 버튼 클릭
→ useAuthGate로 인증 확인
→ anonymous면 현재 화면 위 Auth 모달
→ authenticated면 최신 Wallet Balance 확인
→ 입찰가 + 배송비와 availableBalance 비교 안내
```

프론트 비교는 빠른 안내를 위한 것이며 서버 입찰 요청의 검증을 대체하지 않는다.
팝업을 연 뒤 다른 탭·요청에서 잔액이나 최소 입찰가가 바뀔 수 있다.

### 입찰 성공

```text
Auction 입찰 mutation 성공
→ Auction 담당 Query 무효화
→ walletQueryKeys.balance() 무효화
→ 재조회한 동결액·가용액 표시
```

프론트에서 `availableBalance - (입찰가 + 배송비)`를 새 Wallet 상태로 저장하지
않는다. 재입찰은 기존 hold와 증가분 계산이 다르므로 반드시 서버를 다시
조회한다.

### 상회 입찰과 낙찰

상회 입찰 알림이나 경매 종료 응답 뒤 Wallet 변경을 알 수 있는 시점에 Balance
Query를 무효화한다. SSE나 Notification을 이 문서에서 새로 소유하지 않으며,
해당 담당자가 이벤트 수신 지점에서 Wallet Query invalidation만 호출할 수 있게
계약을 전달한다.

## 다른 도메인 변경 절차

1. Account·Wallet 브랜치에서 공통 hook과 Query key를 먼저 구현한다.
2. Auction 담당자에게 import 경로, 반환 타입, invalidation 시점을 전달한다.
3. Auction 파일 연결은 담당자 브랜치 또는 합의한 작은 후속 PR에서 수행한다.
4. Account·Wallet 작업이 Auction UI 구조나 오류 문구를 임의로 재설계하지 않는다.

## 오류 경계

| 오류 | 소유 |
|---|---|
| 로그인 필요 | Auth gate |
| Wallet 조회 실패 | Wallet Query |
| 가용 잔액 부족 | Auction 입찰 응답과 Wallet 최신 조회 |
| 최소 입찰가 변경 | Auction |
| 종료된 경매 | Auction |
| 동시 입찰 충돌 | Auction |
| hold·capture 상태 전이 | Backend Wallet |

백엔드가 오류 코드를 제공하기 전에는 Account·Wallet 계층이 Auction 응답
문자열을 해석하지 않는다.

## 필수 테스트

- 미인증 상태에서 입찰 API 없이 Auth 모달 진입 계약이 호출된다.
- Wallet 표시가 `availableBalance`를 사용한다.
- 입찰 성공 뒤 Auction과 Wallet Query가 각각 한 번 무효화된다.
- 입찰 실패 뒤 Wallet cache를 예상 금액으로 직접 변경하지 않는다.
- 배송비를 포함한 필요 금액을 화면에 표시한다.
- 재입찰 성공 뒤 서버 Wallet 값을 다시 조회한다.

Auction 컴포넌트 테스트는 Auction 담당자가 소유하고, Wallet hook과 Query key
테스트는 Account·Wallet 담당이 소유한다.

## 완료 기준

- Auction 프론트가 하드코딩 Wallet 금액을 사용하지 않는다.
- 입찰 성공 뒤 동결액과 가용액이 서버 상태와 일치한다.
- 프론트가 hold·release·capture를 직접 호출하거나 재구현하지 않는다.
- 다른 담당자 파일 변경이 합의된 연결 범위로 제한된다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
