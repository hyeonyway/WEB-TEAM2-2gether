# Frontend React Router 내부 이동 통일 설계

## 구현 상태

구현 완료. GitHub 이슈 #112에서 공통 Header와 Home, Card, Auction,
Dashboard, Sell 화면의 내부 이동을 React Router 계약으로 통일했다.

## 배경

프론트에는 이미 `BrowserRouter`와 Route 정의가 있지만 일부 화면은 일반
`<a href>`나 `window.location.href`로 같은 서비스 안의 경로를 이동한다. 이
방식은 SPA Route 이동이 아니라 새 문서를 요청하므로 다음 메모리 상태가 모두
초기화된다.

- 메모리에만 저장하는 Access Token
- TanStack Query cache와 진행 중인 요청
- 인증·Wallet·알림의 React 상태
- 화면에서 유지 중인 검색·필터 상태

새로고침 시 Refresh Token으로 인증을 복구할 수 있더라도, 내부 메뉴 이동마다
전체 애플리케이션을 다시 시작하는 것은 별개의 문제다. 내부 경로는 Router가
처리하고 실제 외부 문서 이동만 브라우저 기본 동작을 사용한다.

## 목표

- 서비스 내부 링크가 문서 전체를 다시 불러오지 않게 한다.
- 내부 이동 전후에 Access Token과 QueryClient 인스턴스를 유지한다.
- URL path와 search parameter를 Router 상태의 단일 원본으로 사용한다.
- 직접 URL 접근과 새로고침은 기존 Route 계약대로 동작하게 한다.
- 다른 도메인의 API와 화면 비즈니스 로직은 변경하지 않는다.

## 범위

### 포함

- Header 로고·메인 메뉴·마이페이지의 Router 이동 상태 재검증
- Home 인사이트의 정렬 조건 포함 경매 목록 이동
- Home 가격 변동 카드의 시세 상세 이동
- Card 목록의 시세 상세 이동과 상세 Route parameter 조회
- Auction 목록의 상세 이동
- Auction 상세의 카드 시세 상세 이동
- Dashboard의 전체 경매 이동
- Sell의 경매 목록 이동과 등록 성공 뒤 상세 이동
- SPA 이동 중 Access Token·Query cache 유지 및 직접 URL 접근 회귀 테스트

### 제외

- 외부 URL, 다운로드, 새 문서 이동
- 현재 상세 URL을 클립보드에 복사하기 위한 `window.location.href` 읽기
- 로그인 성공 뒤 원래 목적지로 자동 복귀하는 기능
- Route 구조, 인증 정책, Auction·Card·Dashboard·Sell API 변경
- 알림 payload에 목적지 Route를 새로 정의하는 작업

## 현재 코드 조사

Header는 이미 `Link`와 `useLocation()`을 사용하므로 새 구조를 만들지 않고 회귀
검증만 수행한다. 다음 위치에는 아직 풀 네비게이션 또는 Router 외부 경로 해석이
남아 있다.

| 화면 | 현재 방식 | 변경 방식 |
|---|---|---|
| Home 인사이트 | `window.location.href` | search parameter를 포함한 `Link` |
| Home 가격 변동 순위 | `<a href>` | 카드 상세 `Link` |
| Card 목록 | `<a href>` | 카드 상세 `Link` |
| Card 상세 ID | `window.location.pathname` 파싱 | `useParams()` |
| Auction 목록 | 버튼에서 `window.location.href` | 경매 상세 `Link` |
| Auction 상세 | `<a href>` | 카드 상세 `Link` |
| Dashboard | `<a href>` | 경매 목록 `Link` |
| Sell Header | `<a href>` | 경매 목록 `Link` |
| Sell 등록 성공 | 지연 후 `window.location.href` | 지연 후 `useNavigate()` |

Card 상세의 공유 기능은 현재 페이지 URL을 읽어 클립보드에 복사할 뿐 이동하지
않으므로 `window.location.href`를 유지한다.

## 설계 선택

### 사용자에게 보이는 링크

목적지가 렌더링 시점에 정해지는 로고, 메뉴, 목록 카드와 상세 링크에는
`Link`를 사용한다. 브라우저 기본 링크 의미와 키보드 접근성을 유지하면서 일반
클릭만 Router가 처리한다. 새 탭 열기, 주소 복사와 보조 클릭은 링크의 기본
행동을 유지한다.

Home 인사이트는 클릭 가능한 `article role="link"` 대신 `Link` 자체를 카드
컨테이너로 사용한다. 별도 Enter·Space 핸들러와 `tabIndex`를 제거하고 네이티브
링크 키보드 동작을 사용한다.

### 동작 완료 뒤 이동

Sell 등록 성공처럼 비동기 동작의 결과로 목적지가 정해지는 경우에는
`useNavigate()`를 사용한다. 현재 성공 안내를 보여 주는 500ms 지연은 유지하되,
타이머가 실행되면 `navigate('/auction/{id}')`를 호출한다. API 성공·오류 정책과
입력 상태는 변경하지 않는다.

### Route parameter와 search parameter

- 상세 화면 ID는 `useParams()`에서 읽고 양의 정수인지 기존 방식대로 검증한다.
- Home 인사이트의 정렬 조건은 `/auction?sort={sort}` 형태로 유지한다.
- 카드 상세에서 진행 경매 검색에 사용하는 `keyword` parameter도 기존
  `Link` 구현을 유지한다.
- Router가 모르는 query parameter는 삭제하거나 별도 전역 상태로 복제하지
  않는다.

## 상태 보존 계약

```text
내부 Link 클릭 또는 navigate() 호출
→ History entry 변경
→ AppRoutes가 목적지 화면 렌더링
→ Browser document와 QueryClient는 그대로 유지
→ 메모리 Access Token 유지
→ 목적지 Query는 기존 cache 정책에 따라 재사용 또는 조회
```

Router 이동을 인증 유지 수단으로 사용하지 않는다. 앱 최초 접근과 실제
새로고침에서는 기존 `AuthProvider`가 Refresh Token으로 Access Token을 복구한다.
이번 작업은 불필요한 문서 재시작을 제거하는 역할만 담당한다.

## PR 리뷰 보완 설계

### Card 상세 Route ID 검증

`useParams()`에서 변환한 `cardId`는 양의 정수일 때만 유효하다. 유효성 결과를
Card 상세 Query의 `enabled`에 전달해 `NaN`, 0, 음수, 소수 ID로 API 요청을
보내지 않는다. 비활성 Query도 `isPending`일 수 있으므로 잘못된 ID 화면을 로딩
화면보다 먼저 렌더링한다.

### Sell 등록 요청 직렬화

버튼의 비활성 상태와 별개로 `submit` 진입 시 `useRef` 기반 동기 잠금을
확인한다. 첫 호출은 API 요청 전에 잠금을 획득하고 요청이 끝나면 해제한다.
동일 렌더 사이클에서 이어진 호출은 즉시 반환해 같은 멱등성 키를 사용하는
불필요한 중복 요청을 만들지 않는다. 실패 후 재시도는 허용한다.

### 등록 성공 후 Auction 목록 동기화

경매 등록이 성공하면 기존 등록 준비 상태를 초기화하는 것과 함께
`auctionQueryKeys.lists()`를 무효화한다. 이후 경매 목록으로 이동할 때
`staleTime`이 남아 있어도 새 경매를 포함한 데이터를 다시 조회한다. 상세
화면으로 이동하는 기존 500ms 성공 안내와 경로는 유지한다.

## 오류와 경계 조건

- 유효하지 않은 카드·경매 ID 직접 접근은 기존 오류 화면을 유지한다.
- 공개 Route와 보호 Route의 구분은 `RequireAuth`와 `useAuthGate()`가 계속
  담당한다.
- `Link`의 `to`에는 사용자 입력을 그대로 붙이지 않고 숫자 ID 또는
  `encodeURIComponent()`로 처리한 search value를 사용한다.
- 외부 URL을 Router로 보내지 않는다.
- `window.location.href` 문자열 읽기와 값 대입을 구분한다. 공유 기능의 읽기는
  허용하고 내부 이동을 위한 대입만 제거한다.
- Router 이동 후 뒤로가기는 기존 history entry로 복귀해야 한다. 보호 Route
  차단처럼 명시적으로 대체해야 하는 경우에만 기존 `replace` 정책을 유지한다.

## 테스트

### 내부 이동

- Card와 Auction 목록의 상세 진입이 `MemoryRouter` 경로를 변경한다.
- Home 인사이트는 `sort` search parameter를 보존해 경매 목록으로 이동한다.
- Home 가격 변동 항목은 해당 카드 상세로 이동한다.
- Dashboard와 Sell Header의 경매 목록 링크가 SPA 경로를 변경한다.
- Auction 상세의 카드 시세 링크가 해당 카드 상세로 이동한다.
- Sell 등록 성공 뒤 500ms가 지나면 생성된 경매 상세로 이동한다.

### 상태 보존

- 내부 이동 전 설정한 메모리 Access Token이 이동 뒤에도 동일하다.
- 동일한 QueryClient에 저장한 cache 값이 이동 뒤에도 남아 있다.
- 이동 과정에서 `window.location` 문서 이동을 사용하지 않는다.

### 직접 접근과 회귀

- `/cards/:cardId`는 브라우저 전역 pathname이 아니라 Router parameter로 API를
  조회한다.
- 유효하지 않은 Card Route ID는 API를 호출하지 않고 오류 화면을 표시한다.
- 연속된 Sell 등록 호출은 진행 중 한 번만 API를 실행하며 실패 뒤 재시도할 수
  있다.
- Sell 등록 성공 시 Auction 목록 Query를 무효화한다.
- `/auction/:auctionId`와 기존 공개 Route 직접 접근이 유지된다.
- `/sell`, `/dashboard`, `/mypage`의 인증 gate가 유지된다.
- Header 활성 메뉴는 `useLocation()` 경로를 기준으로 유지된다.
- TypeScript 검사, 전체 Vitest와 프로덕션 빌드를 실행한다.

## 구현 순서

1. Router 이동과 메모리 상태 보존을 검증하는 실패 테스트를 추가한다.
2. 선언형 내부 이동을 `Link`로 교체한다.
3. Card 상세 parameter를 `useParams()`로 변경한다.
4. Sell 등록 성공 이동을 `useNavigate()`로 변경한다.
5. 남은 내부 `<a href>`와 `window.location.href` 대입을 전수 검사한다.
6. 전체 프론트 검증 후 이 문서와 이슈 체크리스트를 완료 상태로 갱신한다.

## 완료 기준

- [x] 서비스 내부 이동에 일반 `<a href>`가 남아 있지 않다.
- [x] 내부 이동을 위한 `window.location.href` 대입이 남아 있지 않다.
- [x] Card 상세 ID는 Router parameter를 사용한다.
- [x] 내부 이동 중 Access Token과 Query cache가 유지된다.
- [x] 직접 URL 접근과 인증 보호 Route가 회귀하지 않는다.
- [x] 다른 도메인의 API·비즈니스 로직을 변경하지 않는다.
- [x] TypeScript 검사, 전체 Vitest와 프로덕션 빌드를 통과한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
