# 이슈 490: 회원가입/로그인 입력값 검증 강화

## 1. 이슈 경계

- 대응 이슈: [#490](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/490)
- 목표: 이메일 형식, 비밀번호 복잡도, 닉네임 문자종류 검증을 프로덕션에서 확인된 구멍 기준으로 강화한다.
- 비목표: 지갑 충전 상한(`#484`에서 별도 처리), 기존 가입자 데이터 소급 정정(신규 가입/신규 값에만 적용)

## 2. 현재 상태와 문제 (프로덕션에서 직접 확인됨)

| 항목 | 현재 검증 | 확인된 문제 |
|---|---|---|
| 이메일 | `@Email`(Jakarta 기본) + `@Size(max=255)` (`SignupRequest.java:8-9`, `LoginRequest.java:8-9`), 프론트 `emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/` (`SignupForm.tsx:16`, `LoginForm.tsx`) | `1@1.2`(TLD 숫자 1자리), `!@#.ㄷ`(특수문자+한글 자모 TLD) 둘 다 프로덕션에서 실제 가입 성공(curl로 확인, HTTP 409=형식통과·중복만 걸림) |
| 비밀번호 | `@Size(min=8, max=128)` (`SignupRequest.java:11-12`) | 문자종류 제한 전혀 없음, `aaaaaaaa` 통과 |
| 닉네임 | `@Size(min=2, max=30)` (`SignupRequest.java:14-15`) | 문자종류 제한 전혀 없음, 특수문자/공백 다 통과 |

백/프론트 정규식이 서로 다른 곳에서 각자 관리되고 있어(`SignupRequest`/`LoginRequest`의 `@Email` vs `SignupForm.tsx`/`LoginForm.tsx`의 `emailPattern`), 하나만 고치면 다시 어긋난다.

## 3. 결정

### 3.1 이메일 — 커스텀 정규식으로 교체

Jakarta 기본 `@Email`은 구조(`@`, `.` 존재 여부)만 검사하고 TLD의 현실성은 안 본다. 아래 정규식으로 백/프론트 동시 교체:

```
^[A-Za-z0-9](?:[A-Za-z0-9._%+-]*[A-Za-z0-9])?@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\.[A-Za-z]{2,}$
```

- local part: 영숫자로 시작/끝, 중간에 `._%+-` 허용
- 도메인 라벨: 영숫자/하이픈, 하이픈으로 시작/끝 불가
- 마지막 TLD: **알파벳 2자 이상**(숫자·단일 자모 차단)

백엔드는 `@Email`을 지우고 `@Pattern(regexp = EMAIL_REGEX)`으로 교체(또는 병행 유지 — `@Email`도 같이 걸어도 무해하나 중복이라 하나만 남기는 쪽 권장). 프론트 `emailPattern`도 동일 문자열로 교체.

**정규식 drift 방지**: 이번엔 백엔드 상수(`SignupRequest` 근처)와 프론트 상수(`emailPattern`)에 정규식을 나란히 정의하되, 파일 상단 주석에 "이 정규식은 backend `SignupRequest.EMAIL_PATTERN`/`LoginRequest`와 동일하게 유지해야 함, 한쪽만 고치지 말 것"을 명시한다. 언어가 달라(Java/TS) 코드 레벨로 단일 소스화하긴 어려우니 주석+PR 리뷰로 동기화를 강제하는 수준으로 둔다.

### 3.2 비밀번호 — 개인정보보호위원회 지침 기반 커스텀 검증

영문/숫자/특수문자 중:
- 3종류 모두 조합 → 8자 이상
- 2종류 조합 → 10자 이상
- 1종류만 사용 → 거부

길이 기준이 조합 종류수에 따라 달라지는 **조건부 규칙**이라 `@Pattern` 하나로 표현 불가 — 커스텀 `ConstraintValidator` 작성.

```java
package com.dbidding.account.validation;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordPolicyValidator.class)
public @interface PasswordPolicy {
    String message() default "비밀번호는 문자 조합 규칙을 만족해야 합니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

```java
public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, String> {
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) return true; // @NotBlank가 별도로 처리
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        int kinds = (hasLetter ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        if (kinds <= 1) return false;
        int minLength = kinds >= 3 ? 8 : 10;
        return password.length() >= minLength;
    }
}
```

`SignupRequest.password`에 `@PasswordPolicy` 추가(기존 `@Size(min=8, max=128)`는 상한 128 유지 목적으로 그대로 둠 — `PasswordPolicy`는 최소 길이만 조건부로 검사, 최대 길이는 `@Size`가 계속 담당). `LoginRequest.password`는 변경하지 않는다(기존 계정의 로그인은 가입 당시 규칙을 따랐을 뿐이므로 재검증 대상 아님).

프론트 `SignupForm.tsx`의 `validateSignup`에도 동일 로직을 JS로 구현해 서버 요청 전에 사전 안내(서버 검증은 유지, 이건 UX 보조).

### 3.3 닉네임 — 문자종류 제한

길이(2~30자)는 유지, 문자종류만 제한:

```java
@Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다.")
```

공백을 명시적으로 제외(정규식에 공백 미포함이라 자동으로 막힘). `SignupRequest.nickname`에 `@Size`와 함께 추가. 프론트 `validateSignup`에도 동일 정규식 검증 추가.

### 3.4 `@Valid List<T>` deprecated 경고 수정

`ImageUploadRequests.java:14`:
```java
// Before
@NotEmpty @Size(max = 10) @Valid List<FileMeta> files
// After
@NotEmpty @Size(max = 10) List<@Valid FileMeta> files
```
Bean Validation 2.0+ 컨테이너 엘리먼트 제약 규칙에 맞춰 타입 인자에 붙인다. 동작(목록 내 개별 `FileMeta` 검증)은 동일하게 유지되어야 하므로 회귀 테스트로 확인.

### 3.5 회원가입 입력 규칙 안내

가입 폼의 이메일·비밀번호·닉네임 입력란이 focus 상태일 때 해당 입력란 바로 아래에 작은 안내문을 표시한다. 안내문은 검증을 대체하지 않는 UX 보조이며, 서버와 프론트가 적용하는 실제 규칙을 그대로 설명한다.

- 이메일: `example@domain.com` 형식, 영문 2자 이상 TLD
- 비밀번호: 영문/숫자/특수문자 3종 조합은 8자 이상, 2종 조합은 10자 이상
- 닉네임: 2~30자, 한글·영문·숫자만 사용

focus가 다른 필드로 이동하거나 모달을 닫으면 이전 안내문은 숨긴다. 오류 메시지는 기존처럼 제출 또는 입력 검증 뒤에 표시한다.

## 4. 작업 내용

- [ ] `PasswordPolicy`/`PasswordPolicyValidator` 작성
- [ ] `SignupRequest`: `@Email` → 커스텀 이메일 `@Pattern`, `password`에 `@PasswordPolicy` 추가, `nickname`에 `@Pattern` 추가
- [ ] `LoginRequest`: 이메일 정규식만 동일하게 교체(비밀번호는 변경 없음)
- [ ] `ImageUploadRequests.java:14`의 `@Valid` 위치 수정
- [ ] 프론트 `SignupForm.tsx`/`LoginForm.tsx`의 `emailPattern` 교체, `validateSignup`에 비밀번호/닉네임 규칙 추가
- [ ] 회원가입 입력란 focus 시 규칙 안내문 표시 및 프론트 테스트 추가
- [ ] 백엔드 단위 테스트: 이메일(`1@1.2`, `!@#.ㄷ` 등 실제 프로덕션에서 통과했던 사례를 회귀 케이스로 고정), 비밀번호(1종류/2종류/3종류 × 경계 길이), 닉네임(특수문자/공백)
- [ ] 프론트 테스트: 동일 케이스로 `validateSignup` 단위 테스트
- [ ] `ImageUploadRequestsTest` 등 관련 테스트에서 개별 파일 검증이 여전히 걸리는지 확인

## 5. 참고 사항

- 지갑 충전 상한은 `#484`에서 별도로 처리(Redis Lua 지수표기 500 에러 방지와 함께)
- 기존 가입자 중 이 규칙에 안 맞는 이메일/비밀번호/닉네임이 이미 있을 수 있음 — 로그인은 그대로 허용, 신규가입/비밀번호변경 시에만 적용
- 비밀번호 규칙 근거: 개인정보보호위원회 "개인정보의 안전성 확보조치 기준" 비밀번호 작성규칙

> 이 문서는 codex의 도움을 받아 작성하였습니다
