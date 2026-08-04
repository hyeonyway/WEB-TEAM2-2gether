# Frontend Auction Wallet 접점 구현 계획

## 구현 상태

PR #155에서 Auction 입찰 API, 인증 진입, `bid-context` 기반 Wallet snapshot과
Auction Query 갱신을 연결했다. 이슈 #116에서는 입찰 성공 뒤 공통
`walletQueryKeys.balance()`도 무효화해 Header와 마이페이지 Wallet이 서버 값을
다시 조회하도록 연결했다. 이후 기존 Auction SSE가 연결된 상태에서는 현재
사용자가 새 최고 입찰자·상회 입찰당한 이전 최고 입찰자·낙찰자인 경우에도 같은
Query를 무효화하도록 확장했다.

입찰 hold 금액은 배송비를 제외한 입찰가로 확정했다. 배송비는 경매 상세에서
별도로 표시하며 Wallet 가용액 검증과 예상 잔여액 계산에는 포함하지 않는다.

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

- 입찰 성공 시 배송비를 제외한 입찰가가 hold된다.
- 상회 입찰 해제와 낙찰 차감은 백엔드 트랜잭션에서 처리한다.
- 프론트는 `wallet_holds` 상태나 Wallet Repository 동작을 알지 않는다.
- 입찰 전에 Wallet 충전 API를 자동 호출하지 않는다.

## 제공 인터페이스

Account·Wallet 담당은 다음 공통 기능을 제공한다.

- `useWalletBalance()`: 총잔액·동결액·가용액 조회
- `walletQueryKeys.balance()`: 입찰 성공 뒤 무효화할 key
- `useAuthGate()`: 미인증 Wallet·입찰 동작의 이동 차단과 토스트 안내
- `WalletBalance`: 금액 표시가 필요할 때 재사용 가능한 컴포넌트

Auction 담당은 입찰 mutation, Auction Query key와 `bid-context`를 계속 소유한다.
입찰 화면은 별도 Wallet 조회를 중복 호출하지 않고 `bid-context`가 반환하는
`available_balance`, `frozen_balance`를 사용한다. 공통 `useWalletBalance()`는
Header와 마이페이지가 사용하므로 입찰 성공 시 동일한 Query key만 무효화한다.

## 화면 연결 계약

### 입찰 팝업 열기

```text
입찰 버튼 클릭
→ useAuthGate로 인증 확인
→ anonymous면 이동하지 않고 로그인 필요 토스트 표시
→ authenticated면 bid-context의 최신 Wallet snapshot 확인
→ 입찰가와 availableBalance 비교 안내
```

프론트 비교는 빠른 안내를 위한 것이며 서버 입찰 요청의 검증을 대체하지 않는다.
팝업을 연 뒤 다른 탭·요청에서 잔액이나 최소 입찰가가 바뀔 수 있다.

### 입찰 성공

```text
Auction 입찰 mutation 성공
→ Auction 담당 Query 무효화
→ bid-context Query 무효화
→ walletQueryKeys.balance() 무효화
→ 각 화면이 서버의 동결액·가용액을 다시 조회
```

프론트에서 `availableBalance - 입찰가`를 새 Wallet cache로 저장하지 않는다.
화면의 예상 잔여액은 안내용일 뿐이며, 재입찰은 기존 hold와 증가분 계산이
다르므로 반드시 서버를 다시 조회한다.

### 상회 입찰과 낙찰

전역 `AuthProvider`가 기존 Auction SSE를 한 번 구독한다. JWT `sub`는 권한
판단이 아니라 현재 브라우저 사용자의 Wallet cache 갱신 대상을 고르는 용도로만
사용한다.

- `BID_PLACED.bidder_id`가 현재 사용자면 새 hold를 반영한다.
- `BID_PLACED.previous_bidder_id`가 현재 사용자면 상회 입찰로 해제된 hold를
  반영한다.
- `AUCTION_CLOSED.winner_id`가 현재 사용자면 낙찰 차감과 hold 종료를 반영한다.
- 위 ID가 모두 현재 사용자와 다르면 Wallet Query를 무효화하지 않는다.

이 작업은 Auction SSE emitter, payload 또는 Notification을 새로 구현하지 않는다.
기존 공개 Auction 스트림을 소비해 `walletQueryKeys.balance()`만 무효화하며,
실제 Wallet 값은 항상 서버 API에서 다시 조회한다.

### 충전·환불의 탭 간 동기화

충전과 환불은 Auction SSE 이벤트가 아니므로 같은 사용자가 다른 탭에서 거래해도
기존 탭은 이를 알 수 없다. 성공한 탭은 `dbidding-wallet` BroadcastChannel에
`WALLET_CHANGED` 신호만 발행하고, 인증된 다른 탭은 이를 받으면
`walletQueryKeys.balance()`를 무효화한다.

- 메시지에는 잔액, 사용자 ID, Access Token을 포함하지 않는다.
- 현재 탭은 mutation 성공 처리에서 직접 Query를 무효화한다.
- 다른 탭은 메시지에 실린 값을 신뢰하지 않고 Wallet API를 다시 조회한다.
- BroadcastChannel을 지원하지 않는 환경에서는 현재 탭의 기존 갱신만 유지한다.
- 구독은 인증 상태에서만 유지하고 해제 시 채널을 닫는다.

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

## 검증 범위

본인 입찰 mutation의 Wallet Query 무효화와 함께 기존 Auction SSE에 대한 다음
회귀 테스트를 유지한다.

- 현재 사용자가 새 최고 입찰자면 Wallet Query가 무효화된다.
- 현재 사용자가 상회 입찰당하면 해제된 hold를 다시 조회한다.
- 현재 사용자가 낙찰되면 차감된 Wallet을 다시 조회한다.
- Wallet이 변하지 않는 다른 사용자의 이벤트는 무시한다.

TypeScript 검사와 프로덕션 빌드로 import, Query key와 번들 회귀도 확인한다.

## 완료 기준

- Auction 프론트가 하드코딩 Wallet 금액을 사용하지 않는다.
- 입찰 성공 뒤 동결액과 가용액이 서버 상태와 일치한다.
- Header와 마이페이지의 공통 Wallet Query가 입찰 성공 뒤 다시 조회된다.
- 상회 입찰과 낙찰 SSE 뒤 영향을 받은 현재 사용자의 Wallet만 다시 조회된다.
- 프론트가 hold·release·capture를 직접 호출하거나 재구현하지 않는다.
- 배송비를 입찰 hold와 Wallet 예상 잔여액에 포함하지 않는다.
- 다른 담당자 파일 변경이 합의된 연결 범위로 제한된다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
