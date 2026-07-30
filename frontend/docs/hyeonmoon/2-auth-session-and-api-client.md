# Frontend 인증 세션과 API Client 구현 계획

## 목표

메모리 Access Token과 HttpOnly Refresh Token을 사용해 앱 시작, 문서 재로딩,
Access Token 만료 뒤에도 인증 상태를 안전하게 복구한다. 다른 담당 도메인의
API 호출을 일괄 변경하지 않고 Account·Wallet 요청부터 적용 가능한 공통 인증
계층을 제공한다.

## 선행 조건

- [인증 UI](1-auth-ui.md)
- `POST /api/auth/refresh`

React Router가 있어도 `<a href>`나 `window.location.href`를 사용하면 문서
전체가 다시 로드된다. 이 단계에서는 전체 화면의 링크를 부분적으로 고치지 않고
Refresh 쿠키로 인증 상태를 복구한다. Header, Home, Card, Auction, Dashboard,
Sell의 내부 이동 통일은 모든 Account·Wallet 연동이 끝난 뒤 GitHub #112에서
한 번에 처리한다.

## 인증 상태

```ts
type AuthStatus = 'initializing' | 'authenticated' | 'anonymous';
```

- `initializing`: 앱 시작 Refresh 결과를 기다리는 상태
- `authenticated`: 메모리에 유효한 Access Token이 있는 상태
- `anonymous`: Refresh 쿠키가 없거나 인증이 만료된 상태

JWT payload를 사용자 프로필 표시의 원본으로 사용하지 않는다. Header 닉네임과
권한은 현재 사용자 조회 API가 생긴 뒤 서버 응답으로 표시한다.

## 프론트 구조

```text
frontend/src/
├── auth/
│   ├── AuthProvider.tsx
│   ├── useAuth.ts
│   ├── useAuthGate.ts
│   └── RequireAuth.tsx
├── api/
│   ├── accessTokenStore.ts
│   ├── authApi.ts
│   └── authenticatedRequest.ts
└── app/App.tsx
```

기존 `httpClient`를 즉시 모든 도메인에 강제하지 않는다.
`authenticatedRequest()`를 Account·Wallet API부터 사용하고, 다른 도메인은
담당자가 준비됐을 때 같은 계층으로 옮긴다.

## 앱 시작 복구

```text
App mount
→ status=initializing
→ POST /api/auth/refresh (credentials: include)
→ 200: Access Token 저장, authenticated
→ 401: 토큰 제거, anonymous
→ 네트워크·5xx: anonymous 처리 후 재시도 UI 제공
```

Refresh Token은 브라우저가 쿠키로 자동 전송한다. 프론트는 쿠키 값을 읽거나
보관하지 않는다. 앱 시작 또는 실제 문서 새로고침마다 Refresh를 한 번 호출하는
것은 정상 동작이며 일반 API 요청마다 호출하지 않는다.

## 인증 요청 흐름

```text
Account·Wallet API 요청
→ Access Token을 Authorization: Bearer에 추가
→ 성공 응답 반환
→ 401이면 진행 중인 refreshPromise 확인
→ 없으면 POST /api/auth/refresh 한 번 실행
→ 새 Access Token 저장
→ 원 요청을 최대 한 번 재시도
→ Refresh 실패 또는 재시도 401이면 anonymous
```

여러 요청이 동시에 401을 받아도 module scope의 `refreshPromise` 하나만
공유한다. 다음 요청은 자동 Refresh 대상에서 제외한다.

- `/api/auth/signup`
- `/api/auth/login`
- `/api/auth/refresh`
- `/api/auth/logout`

원 요청은 재시도 여부를 내부 플래그로 표시해 무한 반복을 막는다.

## 인증 진입과 보호 경로

`useAuthGate()`는 인증이 필요한 동작과 내부 경로를 공통으로 보호한다.

```text
Header 마이페이지 클릭
→ initializing: 인증 확인이 끝날 때까지 이동 보류
→ anonymous: 이동하지 않고 Auth 모달 열기, returnTo=/mypage 저장
→ authenticated: /mypage 이동
→ 모달 로그인 성공: 검증된 내부 returnTo로 이동
```

주소창이나 새로고침으로 `/mypage`에 직접 접근한 경우 `RequireAuth`가 페이지
내용을 먼저 렌더링하지 않는다. `initializing` 동안에는 인증 확인 화면을
표시하고, `anonymous`로 확정되면 Auth 모달을 연다. 사용자가 모달을 닫고
로그인하지 않으면 홈으로 이동한다.

`returnTo`는 `/`로 시작하는 애플리케이션 내부 경로만 허용한다. 절대 URL,
프로토콜 상대 URL, 알 수 없는 경로는 홈으로 대체해 open redirect를 막는다.
일반 Header 로그인처럼 보호 동작 없이 연 모달은 로그인 성공 뒤 현재 경로를
유지한다.

## 다른 도메인 보호 원칙

- 이 단계에서 `/auction`, `/dashboard`, `/sell` 전체를 강제로 보호하지 않는다.
- `/mypage`와 Header의 마이페이지 진입은 Account 보호 범위로 포함한다.
- Account·Wallet 기능 버튼은 `useAuthGate()`로 인증 모달을 열 수 있다.
- 다른 담당 도메인은 필요할 때 같은 gate를 호출하되 화면 파일 수정은 담당자와
  합의한다.
- 비운영 `X-Debug-User-Id`가 필요한 기존 호출은 전환 전까지 유지할 수 있다.
- 같은 요청에 Bearer와 debug header를 동시에 보내지 않는다.

## Query cache 정리

anonymous 전환과 로그아웃 시 최소한 다음 사용자별 cache를 제거한다.

```text
['auth']
['account']
['wallet']
```

공개 카드·경매 cache는 로그인 종료만으로 제거하지 않는다. 다른 사용자의
개인화 데이터가 추가되면 해당 도메인이 사용자별 Query key를 제공한다.

## 필수 테스트

- 앱 시작 Refresh 성공 시 `authenticated`가 된다.
- 일반 공개 화면에서 Refresh 401이면 `anonymous`가 되고 모달을 강제로 열지
  않는다.
- 비로그인 사용자가 Header 마이페이지를 누르면 이동하지 않고 Auth 모달을 연다.
- `/mypage` 직접 접근은 페이지 내용을 렌더링하지 않고 Auth 모달을 연다.
- 보호 진입에서 로그인하면 `/mypage`로 이동하고, 모달을 닫으면 홈으로 이동한다.
- 외부 URL 형태의 `returnTo`는 홈으로 대체한다.
- 일반 Account·Wallet 요청에 Bearer 헤더가 포함된다.
- 동시 401 여러 건이 Refresh 한 건만 발생시킨다.
- Refresh 성공 뒤 각 원 요청을 한 번만 재시도한다.
- Refresh 실패 뒤 토큰과 사용자별 cache를 제거한다.
- signup, login, refresh, logout은 자동 Refresh하지 않는다.
- 문서 재로딩 뒤 Refresh 성공 시 인증 상태가 복구된다.
- 같은 요청에 Bearer와 `X-Debug-User-Id`가 함께 포함되지 않는다.

## 완료 기준

- 브라우저 새로고침 뒤 Refresh 쿠키로 로그인 상태가 복구된다.
- 비로그인 사용자는 Header 클릭과 직접 URL 접근 모두에서 `/mypage` 내용을 볼
  수 없다.
- 동시 401이 Refresh 요청 폭증이나 무한 재시도를 만들지 않는다.
- 다른 담당 도메인의 보호 정책을 이 단계에서 강제로 변경하지 않는다.
- Access Token과 Refresh Token을 로그에 남기지 않는다.
- 전체 화면의 SPA 내부 이동 코드는 이 단계에서 변경하지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
