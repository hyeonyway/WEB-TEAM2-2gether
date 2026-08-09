# API 오류 응답 및 예외 처리 정책

> 관련 이슈: #288

## 목적

도메인별로 다른 예외 처리 방식과 오류 응답을 공통 계약으로 통일한다. 이 문서는 후속 구현 PR의 판단 기준이며, 이슈 #288 자체는 정책 정의만 다룬다.

## 공통 오류 응답 계약

모든 JSON API 오류 응답은 아래 형식을 사용한다.

```json
{
  "code": "AUCTION_NOT_FOUND",
  "message": "경매를 찾을 수 없습니다."
}
```

- `code`는 클라이언트 분기와 모니터링에 사용하는 안정적인 식별자다.
- `message`는 사용자에게 표시 가능한 한국어 설명이다.
- `path`, `timestamp`, `traceId`는 현재 계약에 포함하지 않는다. 요청 추적 정보는 access log와 서버 로그에서 관리한다.
- 오류 응답에는 stack trace, SQL, 토큰, 내부 예외 클래스명 등 구현 정보를 포함하지 않는다.

## 오류 코드 규칙

- 대문자 `SNAKE_CASE`를 사용한다.
- 코드명은 HTTP 상태가 아니라 실패 원인을 표현한다.
- 여러 도메인에서 같은 의미로 쓰이는 코드는 하나만 유지한다. 예: `RESOURCE_NOT_FOUND`를 도메인마다 임의로 중복 정의하지 않는다.
- 도메인 식별이 필요한 경우에만 `AUCTION_`, `WALLET_`, `ORDER_`, `AUTH_` 같은 접두사를 사용한다.
- 클라이언트가 같은 처리를 해야 하는 오류는 같은 코드를 사용한다.

## HTTP 상태 코드 매핑

| 상황 | 상태 | 예시 코드 |
| --- | --- | --- |
| 요청 본문·파라미터·헤더 검증 실패 | 400 Bad Request | `INVALID_REQUEST`, `INVALID_IDEMPOTENCY_KEY` |
| 인증 정보 없음·유효하지 않은 토큰 | 401 Unauthorized | `UNAUTHORIZED`, `INVALID_TOKEN` |
| 인증된 사용자의 권한 부족 | 403 Forbidden | `FORBIDDEN`, `AUCTION_SELLER_BID_FORBIDDEN` |
| 대상 리소스 없음 | 404 Not Found | `AUCTION_NOT_FOUND`, `WALLET_NOT_FOUND` |
| 중복 생성·멱등성 키 충돌·현재 상태와 충돌 | 409 Conflict | `IDEMPOTENCY_CONFLICT`, `INSUFFICIENT_AVAILABLE_BALANCE` |
| 외부 의존성 일시 실패 | 502 Bad Gateway 또는 503 Service Unavailable | 구현 시 의존성 성격에 따라 결정 |
| 처리 중 알 수 없는 오류 | 500 Internal Server Error | `INTERNAL_SERVER_ERROR` |

## 계층별 책임

### Domain

- 도메인 불변식 위반과 상태 전이 실패를 표현한다.
- HTTP 상태나 `ResponseEntity`에 의존하지 않는다.
- `IllegalArgumentException`과 `IllegalStateException`을 외부 API 계약으로 그대로 노출하지 않는다.

### Service 및 Adapter

- 도메인·저장소·외부 연동 예외를 서비스 의미의 예외로 변환한다.
- `ResponseStatusException`을 새로 추가하지 않는다. 기존 사용처는 후속 이슈에서 공통 도메인 예외 또는 오류 코드 기반 예외로 전환한다.
- 외부 연동의 원인 예외는 보존하되, 클라이언트 메시지로 직접 전달하지 않는다.

### Web 계층

- 전역 `@RestControllerAdvice`가 도메인 예외와 Spring validation 예외를 공통 오류 응답으로 변환한다.
- Controller-local `@ExceptionHandler`와 도메인별 Advice는 전역 처리기로 단계적으로 이전한다.
- Controller는 성공 응답만 조립하고 예외별 응답 본문을 직접 만들지 않는다.

### ControllerAdvice 밖의 경로

- Spring Security filter·authentication entry point·access denied handler는 동일한 `{ code, message }` 계약을 직접 작성한다.
- SSE 연결 수립 전의 인증 오류는 위 계약을 사용한다. 연결 수립 후 오류는 SSE event 또는 연결 종료 정책을 별도로 정의한다.
- 파일 업로드·외부 API 호출 실패도 공통 오류 코드로 변환한다.

## 로깅 기준

- 예상 가능한 4xx 비즈니스 예외는 요청 식별 정보와 오류 코드 중심으로 `WARN` 또는 도메인 특성에 맞는 `INFO`로 기록한다. stack trace는 기본적으로 남기지 않는다.
- 5xx 및 외부 연동 실패는 오류 코드, 원인 예외, stack trace를 `ERROR`로 기록한다.
- 비밀번호, access/refresh token, 세션 ID, 개인정보, 원문 SQL은 로그에 기록하지 않는다.

## 호환성과 전환

1. 공통 `ApiErrorResponse`와 전역 처리기를 추가한다.
2. security/auth 경로를 공통 계약으로 전환한다.
3. auction·wallet·order의 비즈니스 예외를 전환한다.
4. card·wishlist·notification·upload·SSE 경로를 전환한다.
5. 각 단계에서 대표 실패 응답의 status, `code`, `message`를 API 테스트로 고정한다.

기존 wallet의 `{ code, message }` 형식을 기준으로 유지한다. 빈 본문 또는 단일 `code`를 반환하던 API는 후속 PR에서 공통 형식으로 전환하며, 프론트엔드 호출부와 계약 테스트를 함께 갱신한다.

## 현재 상태

- wallet은 `WalletErrorResponse(code, message)`를 사용한다.
- auth/JWT 일부는 빈 본문 또는 단일 `code`를 반환한다.
- auction, card, notification, wishlist, upload에는 `ResponseStatusException` 직접 사용이 존재한다.
- wallet, order, account, security에는 `@ResponseStatus` 기반 도메인 예외가 존재한다.
