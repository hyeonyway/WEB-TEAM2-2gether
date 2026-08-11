# SSE Payload 사전 직렬화 설계

**관련 이슈:** #375  
**대상:** `AuctionSseConnectionManager`, `NotificationSseConnectionManager`

## 1. 배경

현재 두 SSE 연결 매니저는 fan-out 루프 안에서 각 `SseEmitter`에 Java payload
객체를 전달한다.

```java
emitters.forEach(emitter -> send(emitter, SseEmitter.event().data(payload)));
```

`SseEmitter.send()`는 HTTP message converter를 거쳐 data를 직렬화한다. 따라서
동일 이벤트를 N개 연결에 보내면, 내용이 같은 JSON payload도 최대 N번 생성된다.
공개 경매 스트림은 모든 연결에 같은 이벤트를 보내므로, 연결 수가 늘수록 이
반복 비용이 커진다. 알림 스트림도 한 사용자에게 열린 탭이 여러 개인 경우 같은
문제가 생긴다.

연결별로 달라지는 값은 event id와 전송 성공·실패뿐이다. `publishedAt`을 포함한
payload 본문은 fan-out 전에 확정할 수 있다.

## 2. 목표와 범위

### 목표

- broadcast 또는 push 1회당 payload JSON 직렬화는 정확히 1회 수행한다.
- 모든 emitter는 같은 JSON data 본문을 받는다.
- 기존 SSE event name, Auction event id, `publishedAt`, 연결 제거 정책을 유지한다.
- JSON 문자열을 다시 문자열로 인코딩해 클라이언트가 따옴표 포함 문자열을 받는
  문제를 막는다.

### 범위 제외

- Redis Pub/Sub 채널의 wire message와 역직렬화 형식 변경
- SSE payload 필드·이벤트 이름 변경
- heartbeat·`connected` 이벤트 최적화
- executor, backpressure, 재연결 또는 replay 정책 변경

## 3. 변경 전후 흐름

### 현재

```text
payload 객체
  → emitter A: HTTP converter가 JSON 직렬화
  → emitter B: HTTP converter가 같은 객체를 다시 JSON 직렬화
  → emitter C: HTTP converter가 같은 객체를 다시 JSON 직렬화
```

### 목표

```text
payload 객체
  → ObjectMapper.writeValueAsString(payload) 1회
  → JSON 문자열
       → emitter A
       → emitter B
       → emitter C
```

## 4. 구현 계약

### 4.1 공통 규칙

각 연결 매니저는 Jackson `ObjectMapper`를 주입받아 fan-out 전에 payload를
직렬화한다.

```java
String serializedPayload = objectMapper.writeValueAsString(payload);
emitters.forEach(emitter -> send(emitter,
    SseEmitter.event().name(eventName)
        .data(serializedPayload, MediaType.APPLICATION_JSON)));
```

`data(String)`만 사용하면 converter가 JSON 문자열을 일반 문자열로 취급해
`"{...}"`처럼 이중 인코딩할 수 있다. 반드시
`data(serializedPayload, MediaType.APPLICATION_JSON)`으로 JSON media type을
명시한다.

직렬화에는 Redis publisher/subscriber가 사용하는 것과 같은 Jackson 2 계열
`ObjectMapper`를 쓴다. `Instant`, `@JsonNaming`, `@JsonInclude` 결과가 기존
SSE 응답과 달라지지 않는지를 계약 테스트로 검증한다.

### 4.2 Auction SSE

`AuctionSseConnectionManager.broadcast(AuctionStreamPayload event)`의 순서는
다음과 같다.

1. event id를 1회 증가시킨다.
2. `event.withPublishedAt(clock.instant())`로 발행 시각을 1회 확정한다.
3. 확정된 payload를 JSON으로 1회 직렬화한다.
4. 각 emitter에 같은 JSON data와 동일 event id·event name을 보낸다.

event id는 broadcaster 단위로 하나이므로 기존처럼 모든 연결에 같은 id를 보낸다.
직렬화 실패 시 emitter를 순회하지 않으며, payload를 보낸 것으로 기록하지 않는다.

### 4.3 Notification SSE

`NotificationSseConnectionManager.push(Integer userId, NotificationResponse payload)`는
해당 사용자 emitter 집합이 존재할 때에만 JSON을 1회 직렬화한다. 연결이 없으면
기존처럼 직렬화·전송 모두 하지 않는다.

각 emitter에는 기존 `notification-created` event name과 같은 JSON data를 보낸다.
한 탭의 전송 실패가 다른 탭 전송을 막지 않고, 실패한 emitter만 기존 정책대로
연결·세션 레지스트리에서 제거한다.

## 5. 오류 처리와 관찰

JSON 직렬화 실패는 payload 생성 또는 Jackson 설정 오류로, 특정 emitter의 네트워크
오류와 성격이 다르다. `IOException`을 도메인 의미가 드러나는
`IllegalStateException`으로 감싸고, emitter 제거 없이 fan-out 전체를 중단한다.

이 이슈에서는 새 메트릭을 추가하지 않는다. 오류 로그에는 최소한 stream
(`auction` 또는 `notification`)과 event type을 포함해 원인을 구분할 수 있게 한다.
직렬화 실패율·지연 시간의 별도 계측은 실제 병목 측정이 필요해질 때 관측성 이슈로
분리한다.

## 6. 테스트 기준

### 단위 테스트

- emitter가 여러 개여도 `ObjectMapper.writeValueAsString`은 payload당 1회만
  호출된다.
- Auction fan-out은 각 emitter에 같은 JSON data, 같은 event id·event name을
  전달하고 `publishedAt`은 1회 확정된다.
- Notification fan-out은 같은 사용자 emitter에만 같은 JSON data를 전달한다.
- Notification 연결이 없을 때는 직렬화를 호출하지 않는다.
- 한 emitter의 `send` 실패는 그 emitter만 제거하고 나머지 전송은 계속한다.
- 직렬화 실패는 emitter에 `send`하지 않고 호출자에게 명확한 예외로 전파한다.

### SSE 계약 테스트

- HTTP 응답의 `data:` 뒤 값이 JSON object인지 확인한다. JSON 문자열로 이중
  인코딩된 `data: "{...}"`는 실패다.
- `AuctionStreamPayload`의 snake_case, nullable 필드 제외, `Instant` 형식이
  기존 계약과 동일한지 확인한다.

## 7. 완료 기준

- [ ] Auction·Notification payload가 fan-out 전에 각각 1회 직렬화된다.
- [ ] JSON data의 media type을 명시해 이중 인코딩이 발생하지 않는다.
- [ ] 기존 event name, event id, `publishedAt`, emitter 정리 동작이 유지된다.
- [ ] 사전 직렬화와 오류 경로 단위 테스트가 통과한다.
- [ ] 실제 SSE 응답 계약 테스트가 통과한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
