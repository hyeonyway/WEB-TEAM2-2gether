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

## Validation 오류 세부 규격

- `@Valid`, `@NotNull`, `@NotBlank`, `@Size` 등의 DTO 본문 검증 실패인
  `MethodArgumentNotValidException`과 요청 파라미터·모델 바인딩 실패인 `BindException`은
  모두 `400 Bad Request`, `code: "INVALID_REQUEST"`로 변환한다.
- `message`에는 선언한 validation 메시지 중 **첫 번째 field error의 기본 메시지**를 넣는다.
  예: `"비밀번호는 필수 입력값입니다."`
- 이번 공통 계약은 `code`, `message`만 포함하므로 필드별 오류 목록이나 필드명은 응답에
  추가하지 않는다. 폼 단위의 복수 오류 표시가 필요해질 때는 호환성을 검토한 별도 계약으로
  `errors` 배열을 추가한다.
- JSON 파싱 실패, 타입 변환 실패, 필수 request parameter·header 누락도 클라이언트가 수정할
  수 있는 요청 오류로 보고 같은 `INVALID_REQUEST` 정책을 적용한다. 멱등성 키의 형식 오류처럼
  명확한 독립 계약이 있는 경우에만 기존의 세부 코드(`INVALID_IDEMPOTENCY_KEY`)를 유지한다.

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
- `JwtAuthFilter`, `SseTicketAuthFilter`처럼 현재 `response.sendError(...)`를 사용하는 경로는
  컨테이너 기본 HTML 또는 빈 본문이 아닌 JSON을 반환하도록 전환한다.
- 후속 구현에서는 status, `Content-Type: application/json`, UTF-8 인코딩과
  `ApiErrorResponse` 직렬화를 한 곳에서 처리하는 공통 **Filter Error Response Writer**를 둔다.
  Filter, `AuthenticationEntryPoint`, `AccessDeniedHandler`가 이를 함께 사용해 응답 형식과
  오류 코드가 갈라지지 않게 한다.
- SSE 연결 수립 전의 인증 오류는 위 JSON 계약을 사용한다.
- 파일 업로드·외부 API 호출 실패도 공통 오류 코드로 변환한다.

### SSE 연결 수립 후 오류

- HTTP 응답이 이미 SSE로 시작된 뒤에는 상태 코드나 JSON HTTP 오류 본문으로 전환하지 않는다.
- 클라이언트가 처리 가능한 업무 오류는 아래처럼 `error` 이벤트로 전송한다. data는 공통 오류
  계약과 동일하게 `code`, `message`만 포함한다.

  ```text
  event: error
  data: {"code":"AUCTION_CLOSED","message":"이미 종료된 경매입니다."}

  ```

- 오류 이벤트를 보낸 뒤 연결을 유지할지 종료할지는 오류 성격에 따라 구현에서 결정한다.
  인증 무효·재연결이 필요한 오류는 연결을 종료하고, 일시적 또는 특정 경매의 업무 오류는
  연결 유지 여부를 해당 SSE 기능의 계약과 함께 명시한다.
- 전송 자체 실패, client disconnect, emitter timeout은 클라이언트에 재전송할 수 없으므로
  서버에서 연결을 정리하고 로그·메트릭으로만 기록한다.

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

### 테스트 전환 기준

- 서비스·도메인 단위 테스트는 HTTP 예외인 `ResponseStatusException`의 발생 여부 대신,
  전환된 도메인 또는 애플리케이션 예외(예: `AuctionNotFoundException`)와 그 의미를 검증한다.
- `@RestControllerAdvice`가 적용되는 Web Controller 테스트는 HTTP status뿐 아니라
  `jsonPath("$.code")`, `jsonPath("$.message")`를 검증한다. Validation 오류는 첫 번째 field
  error 메시지와 `INVALID_REQUEST`를 함께 고정한다.
- Security filter·entry point·access denied handler 테스트는 JSON content type, UTF-8,
  status, `code`, `message`를 검증한다. `sendError`의 빈 본문에 의존하는 기존 기대값은 제거한다.
- SSE 테스트는 연결 수립 전 JSON 오류와 연결 수립 후 `event: error` payload를 구분해 검증한다.
- `AuctionCursorCodecTest`, `AuctionCommandServiceTest` 등 기존에
  `ResponseStatusException`을 직접 검증하는 테스트는 해당 예외 전환 PR에서 함께 수정한다.
  이 정책 문서 PR에서는 테스트 구현을 바꾸지 않는다.

기존 wallet의 `{ code, message }` 형식을 기준으로 유지한다. 빈 본문 또는 단일 `code`를 반환하던 API는 후속 PR에서 공통 형식으로 전환하며, 프론트엔드 호출부와 계약 테스트를 함께 갱신한다.

## 프론트엔드 소비 규칙

- 공통 HTTP client는 오류 응답 JSON의 `code`와 `message`를 각각 파싱해 `HttpError`에 저장한다.
- 응답 body를 한 번 읽은 뒤 JSON object인지 확인하고, 문자열 `code`와 비어 있지 않은 문자열
  `message`만 채택한다. JSON 파싱 실패, HTML 오류 페이지, 빈 본문, `message` 누락은 서버 원문을
  노출하지 않는다.
- 위 경우 HTTP 상태 코드 기반 fallback 메시지를 사용한다. 예: 400은 `"요청 정보를 확인해 주세요."`,
  401은 `"로그인이 필요하거나 로그인 정보가 만료되었습니다."`, 403은 `"접근 권한이 없습니다."`,
  404는 `"요청한 정보를 찾을 수 없습니다."`, 그 외에는 `"요청 처리 중 오류가 발생했습니다."`를 사용한다.
- UI는 인증·인가 흐름을 HTTP 상태 코드로 처리하고, 세부 비즈니스 분기가 필요한 경우 `code`를 사용한다.
- 서버가 반환한 원문 오류 body, HTML 오류 페이지, JSON 문자열 전체를 사용자에게 직접 표시하지 않는다.
- `fetch`를 직접 사용하는 OCR·S3 업로드 등은 공통 HTTP client를 사용하도록 전환하거나, 동일한 오류 파싱 규칙을 적용한다.

현재 `httpClient.ts`의 `HttpError`는 오류 body 전체를 `message`로 저장하고 `code`만 파싱한다.
공통 오류 응답 도입 PR에서 위 규칙으로 `code`와 `message`를 분리해 전달하도록 전환하고, JSON·빈
본문·HTML 응답의 fallback 동작을 단위 테스트로 고정한다.

## 현재 상태

- wallet은 `WalletErrorResponse(code, message)`를 사용한다.
- auth/JWT 일부는 빈 본문 또는 단일 `code`를 반환한다.
- auction, card, notification, wishlist, upload에는 `ResponseStatusException` 직접 사용이 존재한다.
- wallet, order, account, security에는 `@ResponseStatus` 기반 도메인 예외가 존재한다.
