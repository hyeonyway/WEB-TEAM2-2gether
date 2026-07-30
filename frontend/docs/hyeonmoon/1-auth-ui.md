# Frontend 인증 UI 구현 계획

## 목표

백엔드에만 있던 회원가입·로그인·로그아웃 기능을 현재 화면 위 공통 모달로
제공한다. 인증을 위해 별도 페이지로 이동하지 않고 사용자가 보던 경로와 화면
상태를 유지한다.

## 백엔드 계약

### 회원가입

```http
POST /api/auth/signup
Content-Type: application/json
```

```json
{
  "email": "collector@example.com",
  "password": "Password123!",
  "nickname": "포켓컬렉터"
}
```

성공은 201이며 `id`, `email`, `nickname`, `role`, `status`를 반환한다.
이메일 또는 닉네임 중복은 모두 409다.

### 로그인

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "collector@example.com",
  "password": "Password123!"
}
```

성공은 200이며 본문으로 `{ "accessToken": "..." }`를 반환한다. Refresh
Token은 `refreshToken` HttpOnly 쿠키로 발급한다. 자격 증명이 틀렸거나 계정
상태가 `ACTIVE`가 아니면 401이다.

### 로그아웃

```http
POST /api/auth/logout
Cookie: refreshToken=<http-only-cookie>
```

Refresh Token 존재 여부와 관계없이 204를 반환하고 쿠키를 만료한다.

## 프론트 구조

```text
frontend/src/
├── api/
│   ├── authApi.ts
│   └── accessTokenStore.ts
├── dto/authDto.ts
├── queries/authMutations.ts
└── components/auth/
    ├── AuthModal.tsx
    ├── LoginForm.tsx
    ├── SignupForm.tsx
    └── AuthModal.css
```

Header는 모달의 열림 여부만 관리한다. 입력, 검증, mutation 오류는 각 Form이
소유하고 Auth API가 Access Token 저장과 Refresh 쿠키 요청 옵션을 담당한다.

## 사용자 흐름

### 회원가입

```text
Header 로그인
→ 로그인 모달
→ 회원가입 모드 전환
→ 입력 검증
→ POST /api/auth/signup
→ 성공: 이메일을 유지하고 로그인 모드 전환
→ 409: 이메일·닉네임 공통 중복 안내
```

회원가입 필드는 다음 백엔드 제약과 동일하게 검증한다.

- 이메일: 필수, 이메일 형식, 최대 255자
- 비밀번호: 8자 이상 128자 이하
- 비밀번호 확인: 프론트 전용 일치 검증
- 닉네임: 2자 이상 30자 이하

### 로그인

```text
이메일·비밀번호 입력
→ POST /api/auth/login
→ Access Token 메모리 저장
→ 현재 URL을 유지하고 모달 닫기
```

이메일은 필수·이메일 형식·최대 255자, 비밀번호는 필수·최대 128자다. 401은
계정 존재 여부를 노출하지 않도록 “이메일 또는 비밀번호가 일치하지 않습니다.”
한 문구로 표시한다.

### 로그아웃

```text
POST /api/auth/logout
→ 성공·실패와 관계없이 Access Token 제거
→ auth Query cache 제거
→ 홈으로 이동
```

서버 요청 중에는 로그아웃 버튼을 비활성화해 중복 요청을 막는다.

## 모달 동작

- Header 로그인 버튼으로 연다.
- 로그인과 회원가입은 같은 모달 안에서 전환한다.
- 닫기 버튼, 바깥 영역, `Escape`로 닫는다.
- 모달을 열고 닫아도 URL을 변경하지 않는다.
- 제출 중에는 버튼을 비활성화한다.
- 회원가입 성공 뒤 입력한 이메일과 완료 안내를 로그인 Form에 전달한다.
- 620px 이하에서는 하단 sheet 형태로 표시하고 로그인 진입 버튼을 숨기지 않는다.

## 현재 단계의 제한

Access Token은 메모리에만 있으므로 문서 전체가 다시 로드되면 사라진다. 앱
시작 Refresh는 [인증 세션과 API Client](2-auth-session-and-api-client.md)에서
구현하고, 전체 화면의 SPA 내부 링크 통일은 Account·Wallet 연동이 끝난 뒤
GitHub #112에서 진행한다. Access Token을 localStorage나 sessionStorage에
저장하는 방식으로 우회하지 않는다.

## 필수 테스트

- 기존 공개 Router 경로가 동일하게 렌더링된다.
- 모달 열기·닫기와 로그인·회원가입 전환이 동작한다.
- 회원가입 필드 경계와 비밀번호 확인 불일치를 검증한다.
- 회원가입 성공·409·네트워크 오류를 검증한다.
- 로그인 성공·401·네트워크 오류를 검증한다.
- 요청 중 중복 클릭이 서버 요청을 늘리지 않는다.
- 로그아웃 성공·실패 모두 토큰과 인증 cache를 제거한다.
- Access Token이 브라우저 저장소에 기록되지 않는다.

## 완료 기준

- 인증 전용 `/login`, `/signup` 페이지가 없다.
- 로그인과 회원가입 성공 뒤 사용자가 보던 화면을 유지한다.
- 로그인 성공 뒤 Header가 로그아웃 상태로 즉시 바뀐다.
- 모바일에서도 인증 모달 진입점이 보인다.
- Auth 요청은 모두 `credentials: "include"`를 사용한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
