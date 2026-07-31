# Frontend Account·Wallet 개발 계획

김현문 담당 백엔드인 Account와 Wallet을 현재 프론트 화면에 연결하는 순서를
관리한다. 백엔드에 대응 화면이 없는 회원가입·로그인·로그아웃, 환불, 배송지는
이 계획에서 새로 만든다. Auction, Card, Dashboard 등 다른 담당자의 화면과
비즈니스 로직은 소유하지 않는다.

## 목표

1. Account의 회원가입·로그인·Refresh·로그아웃 API를 실제 UI에 연결한다.
2. Access Token은 메모리, Refresh Token은 HttpOnly 쿠키로 관리한다.
3. Wallet의 총잔액·동결액·가용액을 Header와 마이페이지에 표시한다.
4. 모의 충전·환불을 멱등하게 요청하고 서버 잔액을 단일 원본으로 사용한다.
5. 입찰 화면에는 Wallet 조회와 인증 진입 계약만 제공하고 Auction 로직은
   담당 도메인에 남긴다.
6. Account의 현재 사용자 정보와 배송지 API가 준비되면 마이페이지에 연결한다.

## 담당 경계

프론트 코드의 소유권은 화면 파일이 아니라 기능 책임을 기준으로 판단한다.

| 범위 | 이 계획에서 수행 | 수행하지 않음 |
|---|---|---|
| 공통 Header | 인증 버튼, 로그인 후 Wallet 요약, 마이페이지 Auth gate | 카드·경매 메뉴 정책 변경 |
| 인증 | Auth API, 토큰 상태, Refresh, 인증 모달, `/mypage` 보호 | 다른 도메인 화면을 일괄 보호 |
| Wallet | 조회·충전·환불 API와 UI | 입찰·낙찰 규칙 재구현 |
| Auction 접점 | Wallet hook, Query key, 인증 gate 계약 제공 | Auction API·컴포넌트 임의 수정 |
| 마이페이지 | Account·Wallet·배송지 구역 | 구매·판매·알림 이력 소유권 변경 |

다른 담당자의 파일 변경이 필요한 경우 공통 hook이나 컴포넌트를 먼저 제공한
뒤 연결 위치와 Query 무효화 계약을 전달한다. 해당 화면의 레이아웃, API 호출,
오류 문구를 이 계획만으로 일방적으로 변경하지 않는다.

## 기준 기술

- React 19
- TypeScript
- Vite
- React Router
- TanStack Query 5
- Fetch API
- Vitest, React Testing Library, jsdom

## 계약 우선순위

프론트 연동 시 다음 순서로 현재 계약을 판단한다.

1. 현재 백엔드 Controller와 DTO
2. `docs/hyeonmoon/account`, `auth`, `wallet`, `user`의 최신 설계
3. `../docs/feature-api-spec.md`
4. `frontend/docs/frontend-api-spec.md`

기존 프론트 문서의 Access·Refresh Token 동시 쿠키 저장, 문자열 사용자 ID,
`name`·`phone` 회원가입 필드, `/wallet/mock-charges` 경로는 현재 백엔드와
맞지 않는다. 새 구현은 Access Token 응답 본문, Refresh Token HttpOnly 쿠키,
`Integer` 사용자 ID와 `/api/**` 경로를 사용한다.

## 구현 순서

| 순서 | 이슈 | 상태 | 문서 | 완료 결과 |
|---|---|---|---|---|
| 1 | #78 | 구현 완료 | [인증 UI](1-auth-ui.md) | Router·테스트 기반, 회원가입·로그인·로그아웃 모달 |
| 2 | #113 | 구현 완료 | [인증 세션과 API Client](2-auth-session-and-api-client.md) | 앱 시작 Refresh, Bearer와 단일 401 갱신 |
| 3 | #114 | 구현 완료 | [Wallet 잔액](3-wallet-balance.md) | Header·마이페이지의 실제 총액·동결액·가용액 |
| 4 | #115 | 구현 완료 | [Wallet 충전·환불](4-wallet-charge-and-refund.md) | 멱등 모의 거래와 서버 잔액 동기화 |
| 5 | #116 | **다음** | [Auction Wallet 접점](5-auction-wallet-integration.md) | 입찰 화면에 Wallet·Auth 계약 전달 |
| 6 | #117 | 백엔드 선행 | [Account와 배송지](6-account-and-address.md) | 현재 사용자 정보와 배송지 CRUD UI |
| 7 | #112 | 최종 | 별도 공통 작업 | 전체 화면의 SPA 내부 이동 통일 |

각 설계 문서를 하나의 이슈와 PR로 처리한다. #112는 위 연동이 모두 끝난 뒤
Header, Home, Card, Auction, Dashboard, Sell의 `<a href>`와
`window.location.href`를 React Router 이동으로 한 번에 교체한다.

## 공통 상태 원칙

- 서버 응답을 Account와 Wallet 상태의 단일 원본으로 사용한다.
- Access Token은 JavaScript 메모리에만 저장한다.
- Refresh Token은 HttpOnly 쿠키로만 전달하고 프론트에서 읽지 않는다.
- 인증 요청은 `credentials: "include"`를 사용한다.
- Header 전자지갑은 `authenticated` 상태에서만 표시한다.
- Header 마이페이지와 `/mypage` 직접 접근은 같은 Auth gate를 사용하고, 로그인
  성공 뒤 검증된 내부 목적지로 이동한다.
- Wallet Query key는 한 모듈에서 정의하고 거래 성공 뒤 같은 key를 무효화한다.
- 입찰가, 배송비, 활성 hold를 프론트에서 별도 원장처럼 관리하지 않는다.
- `X-Debug-User-Id`는 다른 도메인의 비운영 개발이 끝날 때까지 해당 호출에만
  유지하고 Account·Wallet 운영 흐름에는 사용하지 않는다.

## 공통 오류 처리

| 상태 | 처리 |
|---|---|
| 400 | 입력 필드 또는 요청 형식 오류 표시 |
| 401, 로그인 | 이메일·비밀번호를 구분하지 않는 공통 자격 증명 오류 |
| 401, 인증이 필요한 일반 API | Refresh 한 번 뒤 원 요청을 최대 한 번 재시도 |
| 401, signup/login/refresh/logout | 자동 Refresh·재시도 없이 엔드포인트별 오류 처리 |
| 401, Refresh | 메모리 토큰과 인증 Query를 지우고 anonymous 전환 |
| 404 | Wallet·사용자·배송지 없음 표시 |
| 409, 회원가입 | 이메일 또는 닉네임 중복 안내 |
| 409, Wallet | 잔액 부족 또는 멱등키 충돌 안내, 자동 재시도 금지 |
| 네트워크·5xx | 입력을 유지하고 사용자 재시도 제공 |

백엔드 오류 본문이 아직 공통 코드로 구조화되지 않은 범위는 HTTP 상태를
기준으로 처리한다. 프론트가 응답 메시지 문자열을 파싱해 도메인 규칙을
추측하지 않는다.

## 공통 검증

각 단계는 API 모듈 테스트, 사용자 동작 컴포넌트 테스트, 전체 빌드를 포함한다.

```bash
cd frontend
npm run typecheck
npm test
npm run build
```

수동 검증은 데스크톱과 모바일에서 수행한다. API 실패·중복 제출·브라우저
새로고침·로그아웃 뒤 접근을 포함하며, 테스트 소스가 없거나 명령이 없는 상태를
통과로 보고하지 않는다.

## 완료 기준

- Account·Wallet의 구현된 백엔드 API가 하드코딩 UI를 대체한다.
- 비로그인 상태에서는 Header 전자지갑과 마이페이지 개인정보가 노출되지 않는다.
- 새로고침과 내부 화면 이동 뒤에도 Refresh 쿠키로 인증 상태를 복구한다.
- Wallet 가용액은 서버 응답을 사용하며 충전·환불이 중복 반영되지 않는다.
- Auction은 Wallet의 내부 Repository나 hold 규칙을 프론트에 복제하지 않는다.
- 다른 담당자 화면 변경은 합의된 공통 인터페이스 연결로 제한한다.
- 배송지는 Account 백엔드 API 구현 뒤 마지막 단계에서 연결한다.
- 실제 PG 결제를 구현했다고 표시하지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
