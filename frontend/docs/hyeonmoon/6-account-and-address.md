# Frontend Account와 배송지 구현 계획

## 목표

마이페이지의 정적 사용자 정보를 Account API로 교체하고 배송지
조회·추가·수정·삭제 UI를 제공한다. 배송지는 Account 백엔드 구현이 완료된 뒤
가장 마지막에 연결한다.

## 백엔드 선행 조건

현재 Account 백엔드는 signup, login, refresh, logout을 구현했지만 현재 사용자
정보 조회·수정과 배송지 Controller는 아직 구현 전이다. 프론트 API 코드는
다음 계약이 백엔드에 반영된 뒤 작성한다.

```http
GET /api/users/me
```

현재 사용자 응답은 최소한 `id`, `email`, `nickname`, `role`, `status`를
포함한다. JWT를 decode해 이 값을 대신 만들지 않는다.

배송지 기본 경로는 다음과 같다.

```http
GET    /api/users/me/addresses
POST   /api/users/me/addresses
PUT    /api/users/me/addresses/{addressId}
DELETE /api/users/me/addresses/{addressId}
```

```json
{
  "addressName": "집",
  "address": "서울특별시 강남구 테헤란로 1",
  "detailedAddress": "101호",
  "postalCode": "06236",
  "defaultAddress": true
}
```

응답에는 `id`와 위 필드를 포함하고 `userId`는 노출하지 않는다.

## 프론트 구조

```text
frontend/src/
├── dto/
│   ├── accountDto.ts
│   └── addressDto.ts
├── api/
│   ├── accountApi.ts
│   └── addressApi.ts
├── queries/
│   ├── accountQueries.ts
│   └── addressQueries.ts
└── components/account/
    ├── AccountSummary.tsx
    ├── AddressList.tsx
    └── AddressDialog.tsx
```

마이페이지 전체를 다시 만들지 않고 Account·Wallet 영역을 독립 컴포넌트로
연결한다. 구매·판매·관심·알림 구역은 해당 담당자의 기존 구현을 유지한다.
마이페이지 Route 보호와 로그인 후 목적지 복귀는
[인증 세션과 API Client](2-auth-session-and-api-client.md)의
`RequireAuth`와 `useAuthGate()`를 사용하며 이 단계에서 별도 인증 상태를
만들지 않는다.

## 현재 사용자 정보

- Auth 상태가 `authenticated`일 때만 조회한다.
- `anonymous` 상태에서는 마이페이지 정적 사용자 정보와 하위 내용을 렌더링하지
  않는다.
- Header와 마이페이지는 같은 Account Query를 사용한다.
- 이메일·닉네임은 서버 응답을 표시한다.
- `role`은 관리자 UI가 실제로 생기기 전까지 사용자에게 별도 노출하지 않는다.
- `ACTIVE`가 아닌 계정은 백엔드 로그인 단계에서 차단하므로 프론트가 상태별
  인증 우회를 만들지 않는다.
- 로그아웃과 Refresh 실패 시 Account Query를 제거한다.

## 배송지 검증

- 배송지 이름: 필수, 최대 50자
- 기본 주소: 필수, 최대 255자
- 상세 주소: 선택, 최대 255자
- 우편번호: 숫자 5자리
- 기본 배송지 여부: boolean

프론트 검증은 입력 편의를 위한 것이며 소유권과 기본 배송지 단일화는 백엔드가
최종 보장한다.

## 배송지 상태 흐름

### 생성·수정

```text
폼 입력
→ 클라이언트 검증
→ POST 또는 PUT
→ 성공: 배송지 목록 Query 무효화
→ 실패: 입력 유지
```

기본 배송지로 저장하면 서버가 기존 기본 배송지를 해제한다. 프론트가 목록
cache 여러 항목을 직접 바꿔 단일화하지 않고 재조회한다.

### 삭제

- 삭제 확인을 받은 뒤 요청한다.
- 성공은 204이며 목록 Query를 무효화한다.
- 기본 배송지를 삭제해도 다른 주소를 프론트에서 임의로 기본 지정하지 않는다.
- 존재하지 않거나 다른 사용자 소유인 주소는 모두 404로 처리한다.

## 다른 화면과의 경계

Auction 등록·낙찰·배송 화면에서 배송지가 필요해지더라도 이 단계에서 해당
화면을 수정하지 않는다. Address DTO와 선택 컴포넌트의 공개 계약을 전달하고,
실제 연결은 배송·주문 도메인 소유자가 확정된 뒤 진행한다.

## 오류 처리

| 상태 | 처리 |
|---|---|
| 400 | 필드별 형식·길이 오류 |
| 401 | 인증 Refresh 흐름 또는 Auth 모달 |
| 404, Account | 현재 사용자 정보 조회 실패 |
| 404, Address | 존재하지 않거나 접근할 수 없는 배송지 |
| 409 | 백엔드가 정의한 충돌 메시지 표시 |
| 네트워크·5xx | 입력을 유지하고 재시도 제공 |

## 필수 테스트

- anonymous 상태에서 Account·Address API를 호출하지 않는다.
- `/mypage` 직접 접근과 Header 진입 모두 인증 gate를 통과한 뒤에만 Account
  내용을 렌더링한다.
- Header와 마이페이지가 같은 Account Query를 사용한다.
- 배송지 필드 길이와 우편번호 형식을 검증한다.
- 생성·수정 성공 뒤 목록 Query를 무효화한다.
- 실패 뒤 Form 입력을 유지한다.
- 삭제 전 확인과 204 성공 흐름을 검증한다.
- 로그아웃 뒤 Account·Address cache가 제거된다.

## 완료 기준

- 마이페이지 Account·배송지 하드코딩 정보가 제거된다.
- 비로그인 사용자는 마이페이지의 정적 사용자·Wallet·배송지 정보를 볼 수 없다.
- JWT decode나 임의 사용자 값으로 프로필을 만들지 않는다.
- 다른 사용자의 배송지 존재 여부를 화면에서 구분해 노출하지 않는다.
- 다른 담당자 영역을 재설계하지 않고 독립 Account 컴포넌트로 연결한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
