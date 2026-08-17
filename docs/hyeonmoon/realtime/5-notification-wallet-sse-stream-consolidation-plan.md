# 알림·지갑 SSE 스트림을 유저당 커넥션 1개로 통합

**상태:** 제안
**대상:** `SessionNotificationSseController`, `SessionWalletSseController`,
`NotificationSseConnectionManager`, `WalletSseConnectionManager`,
`useNotificationStream`, `useWalletStream`
**선행 문서:** [경매 SSE 선택 구독과 개인화 지갑 동기화 설계](3-auction-sse-selective-subscription.md)
**이슈:** #557

## 1. 배경

지갑 SSE(`GET /api/me/wallet/stream`)를 부하테스트 시나리오(`pure-throughput.js`,
`hot-auction-pattern.js`)에 새로 추가하고 `SSE_VUS=1000`으로 돌려본 결과, 두 가지
용량 병목을 확인했다.

1. `SERVER_TOMCAT_MAX_CONNECTIONS`(기존 3000) — `auction`+`notification`+`wallet`
   SSE 3종류 × 1000커넥션만으로 거의 소진돼, `bid-context` 같은 일반 API 요청이
   연결 자리를 못 잡고 타임아웃/503이 다발했다.
2. `walletSseTaskExecutor`(core=2/max=4/queue=500) — 형제 executor
   (`AUCTION_SSE_*`/`AUCTION_SSE_BROADCAST_*`/`NOTIFICATION_*`/`NOTIFICATION_FANOUT_*`는
   전부 core=4/max=8/queue=2000)보다 훨씬 작게 잡혀있어, 실부하에서
   `CountingDiscardPolicy`가 대량 discard를 발생시켰다.

두 값은 prod env var로 즉시 상향 조치했다(`SERVER_TOMCAT_MAX_CONNECTIONS=4000`,
`WALLET_SSE_*`를 형제 수준으로 조정). 하지만 이는 대증 처방이고, 근본 원인은
**로그인한 유저 한 명이 SSE 커넥션을 3개(`auction`/`notification`/`wallet`)나
동시에 열고 있는 구조** 자체다. 이 문서는 그중 `notification`과 `wallet`을
커넥션 1개로 합쳐 유저당 SSE 연결 수를 줄이는 안을 다룬다.

## 2. 목표와 범위

### 목표

- 로그인 유저 한 명이 여는 SSE 커넥션을 3개에서 2개로 줄인다
  (`auction`은 이번 스코프에서 제외, §2.1 참고).
- 1000명 동시 접속 기준 총 SSE 커넥션을 3000 → 2000으로 낮춰, Tomcat
  `maxConnections`·워커 스레드에 걸리는 압박을 완화한다.
- 알림과 지갑 각각의 기존 payload 형식, 재연결 시 REST 재조회(catch-up) 동작,
  세션 종료 시 강제 종료 동작은 그대로 유지한다.
- 프론트 컴포넌트가 소비하는 훅 인터페이스(`useNotificationStream`,
  `useWalletStream`)의 외부 시그니처는 최대한 유지해 호출부 변경을 최소화한다.

### 범위 제외

- **`auction` SSE는 이번 스코프에서 제외한다.** `GET /api/auctions/stream`은
  `@CurrentUser`가 없어 인증이 필수가 아니고, `auctionIds` 쿼리파라미터로
  페이지마다 최대 15개까지 동적으로 구독 대상이 바뀐다(선행 문서 §3.1). "유저당
  고정 스트림 1개"라는 이번 설계의 전제와 근본적으로 다른 구독 모델이라 별도
  검토가 필요하다.
- 지갑 홀드·해제·정산 규칙, 알림 저장/발행 로직 자체는 변경하지 않는다.
- Redis Pub/Sub 채널 분리·동적 채널 생성은 다루지 않는다(선행 문서 §6과 동일한
  이유로 단일 채널을 유지한다).
- `WALLET_SSE_*`/`SERVER_TOMCAT_MAX_CONNECTIONS` 용량 튜닝값 자체의 최적치 산정은
  별도 이슈로 다룬다.

## 3. 핵심 결정

### 3.1 API: `GET /api/me/stream` 하나로 통합, `event:` 이름으로 타입 구분

```http
GET /api/me/stream
Accept: text/event-stream
Cookie: SESSION=...
```

SSE 스펙 자체가 한 커넥션에서 `event: <이름>` 필드로 여러 종류 이벤트를 구분해
보내는 기능을 제공하므로 새 프로토콜이 필요 없다. 서버는 기존 이벤트 이름을
그대로 쓴다.

```text
event: notification-created   (또는 알림 도메인 기존 이벤트명)
data: {...NotificationResponse}

event: wallet-state-changed   (WalletSseConnectionManager.WALLET_STATE_CHANGED)
data: {...WalletSsePayload}
```

`SessionNotificationSseController`, `SessionWalletSseController` 두 컨트롤러를
하나(`SessionMeStreamController` 가칭)로 병합하고, 기존 `/api/me/notifications/stream`,
`/api/me/wallet/stream`은 제거한다. 두 엔드포인트 모두 이미 `@CurrentUser Integer
userId` + `HttpSession`만 받고 다른 파라미터가 없어 병합 자체는 간단하다.

### 3.2 서버: emitter 레지스트리를 공유한다

`NotificationSseConnectionManager`와 `WalletSseConnectionManager`는 이미 같은
기반 위에서 동작한다.

```text
NotificationSseConnectionManager.registry  : SseEmitterRegistry<Integer>
WalletSseConnectionManager.registry        : SseEmitterRegistry<Integer>
둘 다 SessionSseConnectionRegistry로 세션 종료 연동
```

즉 지금도 "유저ID → SseEmitter 집합"이라는 동일한 구조를 각자 따로 들고 있을
뿐이다. 통합안은 이 등록 대상을 하나의 공유 `SseEmitterRegistry<Integer>`로
합치는 것이다.

- 새 `MeSseConnectionManager`가 커넥션 등록(`connect(userId, sessionId)`)을
  전담하고 emitter 1개를 발급한다.
- `NotificationSseConnectionManager.push(userId, payload)`와
  `WalletSseConnectionManager.push(userId, payload)`는 각자의 publisher/listener
  호출 지점(`NotificationEventListener`, `WalletSseEventListener`,
  `WalletSseRedisSubscriber` 등)은 그대로 두고, 마지막 emitter 조회·전송 단계만
  공유 레지스트리를 바라보게 바꾼다. 두 도메인의 이벤트 생성·발행 경로 자체는
  건드리지 않는다.
- 전송 executor는 당분간 도메인별로 분리 유지한다(`notificationFanOutTaskExecutor`,
  `walletSseTaskExecutor`). 같은 emitter에 대한 동시 write는 이미
  `SseEmitterRegistry`/`PerConnectionSseSendDispatcher`가 연결 단위로 직렬화하는
  책임을 지고 있으므로(관련: [SSE emitter 동시 전송 수정](4-sse-emitter-concurrent-send-fix.md)),
  이 계약이 통합 후에도 유지되는지 우선 확인한다. 필요하면 이후 별도 이슈로
  executor까지 통합한다.

### 3.3 재연결·세션 종료

- `SessionSseTerminationPublisher`는 세션 ID 기준으로 종료를 발행하므로, 커넥션이
  1개로 줄어도 로직 변경이 필요 없을 가능성이 높다 — 다만 지금 알림/지갑 두
  커넥션에 각각 걸려있던 종료 처리가 하나의 emitter에 정확히 한 번만 걸리는지는
  구현 시 확인한다.
- 재연결 시 REST 재조회(catch-up)는 알림·지갑이 이미 각자 다른 트리거로 동작한다
  (`useWalletStream`의 `recoverBalance()`, `useNotificationStream`의 재연결 시
  `invalidateQueries`). 커넥션이 하나로 합쳐지면 "재연결"이라는 이벤트 자체가
  하나이므로, 이 하나의 재연결 신호에서 두 캐시(알림/지갑)를 모두 무효화하도록
  합친다.

### 3.4 프론트: 훅 통합과 호출 위치 정리

현재 두 훅은 서로 다른 컴포넌트 트리에서 독립적으로 호출된다.

```text
AuthProvider.tsx  -> useWalletStream(status === 'authenticated')
App.tsx           -> useNotificationStream({enabled: status === 'authenticated', onNotificationCreated: push})
```

둘 다 `status === 'authenticated'`라는 같은 조건에 이미 묶여 있으므로, 공유
`EventSource` 하나를 여는 새 훅(예: `useMeStream`)을 만들어 한 곳(예:
`AuthProvider`)에서 호출하고, `useNotificationStream`/`useWalletStream`은 그
공유 연결을 구독하는 얇은 wrapper로 남기거나, 컴포넌트에서 직접
`useMeStream`의 알림/지갑 콜백을 등록하는 형태로 정리한다. 각 훅이 독립적으로
갖고 있던 재연결 backoff 상태(`consecutiveFailures` 등)는 공유 연결 쪽 하나로
합친다 — 두 군데서 따로 재시도 타이머를 돌리면 정합성이 깨진다.

## 4. 관찰성과 검증

- 통합 전후로 유저당 SSE 커넥션 수(`sse_connections_max{stream=...}`)가 3 → 2로
  줄어드는지, 같은 부하(`SSE_VUS`)에서 Tomcat 커넥션 사용량이 실제로 감소하는지
  `pure-throughput.js`/`hot-auction-pattern.js`로 재측정한다.
- 알림 이벤트가 지갑 emitter로, 지갑 이벤트가 알림 emitter로 새지 않는지 —
  `event:` 이름 기준으로 프론트가 정확히 분기하는지 확인한다.
- 연결 종료·timeout·전송 실패 뒤 공유 레지스트리에서 두 도메인 모두 누수 없이
  정리되는지 확인한다.
- 세션 강제 종료(다른 기기 로그인 등) 시 하나의 emitter만 정확히 닫히는지
  확인한다.
- 재연결 시 알림 목록·안읽음 카운트·지갑 잔액이 모두 REST로 다시 맞춰지는지
  확인한다.
- 기존 `NotificationExecutorConfigTest`, `WalletSseExecutorConfigTest`,
  `useNotificationStream.test.tsx`, `useWalletStream.test.tsx`를 새 구조에 맞게
  갱신한다.

## 5. 완료 기준

- [ ] `GET /api/me/stream` 하나로 알림·지갑 이벤트를 모두 받을 수 있다.
- [ ] 기존 `/api/me/notifications/stream`, `/api/me/wallet/stream`은 제거된다.
- [ ] 공유 `SseEmitterRegistry`에서 알림·지갑 push가 각자의 `event:` 이름으로
      정확히 전달된다.
- [ ] 세션 종료·재연결 시 기존 동작(강제 종료, REST 재조회)이 그대로 유지된다.
- [ ] 프론트 훅이 통합되고, 재연결 backoff 상태 중복이 없다.
- [ ] 부하테스트로 유저당 SSE 커넥션 3 → 2, 총 커넥션 감소를 수치로 확인한다.
- [ ] 관련 백엔드·프론트 테스트가 새 구조를 반영해 갱신된다.

> 이 문서는 claude의 도움을 받아 작성되었습니다.
