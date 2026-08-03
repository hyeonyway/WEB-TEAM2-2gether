# 알림 실시간 푸시 (SSE) 프론트엔드 연동 계획

담당: D(임하민). [3-frontend-integration-plan.md](3-frontend-integration-plan.md)(REST 목록/읽음 처리, "SSE 제외"로 범위 밖 처리)와 [4-realtime-sse.md](4-realtime-sse.md)(백엔드 SSE 구현, 이슈 #161 / PR #167 완료)에 이어지는 라운드. 백엔드가 이미 유저별 알림 SSE(`/api/users/{userId}/notifications/stream`, 티켓 인증)를 구현했으니, 이번엔 프론트에서 그 스트림을 연결해 실시간으로 배지/목록을 갱신한다.

이 문서는 설계만 다룬다. 코드는 피드백 반영 후 작성한다.

## 범위

- `useNotificationStream` 훅 신규 — 티켓 발급 → `EventSource` 연결 → 알림 수신 시 React Query 캐시 갱신.
- 로그인 상태에서 앱 전역에 하나만 연결되도록 배치.
- 수신한 알림을 목록/안읽음 카운트 캐시에 직접 반영(재조회 없이).
- 화면 우측 하단 알림 팝업 스택 UI(결정 6).

범위 밖: 알림 설정 on/off, 여러 탭 간 동기화 최적화(`BroadcastChannel` 등), SSE 실패 시 폴링 폴백(대신 결정 7의 refetch 안전망으로 대체).

## 배경

- 지금 `NotificationBell`은 드로어를 열 때마다 강제로 REST 재조회하는 방식으로 버텨왔다(3-frontend-integration-plan.md) — "SSE가 붙으면 이벤트 수신 시 invalidate하는 방식으로 대체될 예정"이라고 이미 적어뒀다.
- 프론트에 `useAuctionStream` 훅이 있지만 이건 **공개** SSE(`/api/auctions/stream`, 인증 불필요)라서 그대로 재사용할 수 없다. 티켓 기반 개인화 SSE 연결은 프론트 어디에도 아직 없다 — 대시보드 쪽(`/api/dashboard/stream`)도 미구현 — 이번이 프론트 최초의 티켓 인증 SSE 연동이다.

## 결정 1: userId를 어디서 구하나 — 액세스 토큰의 `sub` 클레임을 프론트에서 직접 읽는다

`/api/users/{userId}/notifications/stream` 경로엔 `{userId}`가 필요한데, 지금 프론트는 자기 userId를 알 방법이 없다. 로그인/리프레시 응답(`LoginResponse`/`RefreshResponse`)엔 `accessToken`만 있고, "내 정보 조회" API도 없다.

백엔드 `JwtTokenProvider.issue()`가 액세스 토큰을 `Jwts.builder().subject(userId.toString())`로 만든다 — 즉 JWT의 표준 `sub` 클레임에 userId가 그대로 들어있다. 서명 검증 없이 payload만 base64url 디코드해서 읽어도 안전한 이유: 이 값은 실제 인가에 쓰이지 않는다. 백엔드도 SSE 경로의 `{userId}`를 인증 근거로 신뢰하지 않고 `@CurrentUser`(티켓 검증 결과)와 값이 같은지만 확인하므로(`4-realtime-sse.md` 결정 7), 클라이언트가 자기 토큰의 클레임을 스스로 읽어 URL을 구성하는 데 보안 문제가 없다.

```ts
// frontend/src/api/jwtClaims.ts (신규)
export function decodeAccessTokenUserId(accessToken: string): number | null {
  const payload = accessToken.split('.')[1];
  if (!payload) return null;
  try {
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    const userId = Number(json.sub);
    return Number.isInteger(userId) && userId > 0 ? userId : null;
  } catch {
    return null;
  }
}
```

대안(account 담당인 김현문에게 "내 정보 조회" API를 새로 요청)도 검토했지만, 다른 담당의 새 엔드포인트를 기다릴 필요 없이 지금 바로 개발 가능한 이 방식으로 확정했다.

## 결정 2: 티켓 발급 → `EventSource` 연결, 재연결은 훅이 직접 관리

`useAuctionStream`과 다르게 이 스트림은 인증이 필요하다:

1. `POST /api/sse/tickets`(`authenticatedRequest`로 `Authorization` 헤더를 실어 호출) → `{ ticket, expiresInSeconds }`.
2. `new EventSource(`${API_BASE}/api/users/${userId}/notifications/stream?ticket=${ticket}`)`.

**중요한 함정**: SSE 티켓은 30초 TTL의 1회용이다(`4-realtime-sse.md`/`5-current-user-and-sse-auth.md`). 브라우저 `EventSource`의 표준 재연결 동작은 "같은 URL로 재연결"인데, 이미 소비된 티켓으로는 재연결이 실패한다. 그래서 브라우저의 자동 재연결에 맡기지 않고, 훅이 `onerror`/연결 종료를 감지하면 **새 티켓을 다시 발급받아 새 `EventSource`를 생성**하는 방식으로 재연결을 직접 구현해야 한다.

**재연결 정책**: 지수 백오프는 두지 않는다. 연결이 끊기면 짧은 고정 지연(2초) 후 바로 재시도하고, 실패하면 다시 2초 후 재시도 — 매번 같은 간격으로 재시도한다. 재시도 횟수 상한도 두지 않는다(로그인 상태인 한 계속 시도) — 결정 7의 refetch 안전망이 있어 SSE가 잠깐 끊겨도 드로어를 열면 최신 상태를 다시 보장하기 때문에, 재연결 실패가 몇 번 반복돼도 사용자 경험에 큰 문제가 없다.

```ts
// frontend/src/api/notificationTicketApi.ts (신규)
export async function issueSseTicket(): Promise<{ ticket: string; expiresInSeconds: number }> {
  return authenticatedRequest('/api/sse/tickets', {method: 'POST'});
}
```

## 결정 3: payload는 기존 `NotificationDto`(camelCase) 그대로 파싱

백엔드가 `event: notification-created`로 기존 `NotificationResponse`를 그대로 보낸다(`4-realtime-sse.md` 결정 3) — `useAuctionStream`이 하는 snake_case ↔ camelCase 변환이 여기선 필요 없다. REST 응답(`NotificationDto`)과 필드가 완전히 같다.

```ts
// frontend/src/hooks/useNotificationStream.ts (신규)
export type NotificationStreamPayload = NotificationDto;

function parsePayload(data: string): NotificationStreamPayload | null {
  try {
    const raw = JSON.parse(data) as Partial<NotificationDto>;
    if (
      typeof raw.id !== 'number'
      || typeof raw.auctionId !== 'number'
      || typeof raw.message !== 'string'
      || typeof raw.isRead !== 'boolean'
      || typeof raw.createdAt !== 'string'
    ) return null;
    return raw as NotificationDto;
  } catch {
    return null;
  }
}
```

## 결정 4: 수신 시 캐시를 직접 patch — invalidate로 재조회하지 않는다

`auctionStreamCache.ts`가 이미 쓰는 컨벤션(SSE로 받은 데이터로 캐시를 직접 갱신, refetch 없음)을 그대로 따른다:

```ts
// frontend/src/queries/notificationStreamCache.ts (신규)
export function applyNotificationCreated(
  current: InfiniteData<NotificationPageDto> | undefined,
  notification: NotificationDto,
  unreadOnly: boolean,
): InfiniteData<NotificationPageDto> | undefined {
  if (!current) return current;
  if (unreadOnly && notification.isRead) return current;
  const [firstPage, ...restPages] = current.pages;
  if (!firstPage) return current;
  return {...current, pages: [{...firstPage, items: [notification, ...firstPage.items]}, ...restPages]};
}
```

수신 시 `notificationQueryKeys.list(false)`와 `list(true)` 두 캐시 모두에 위 함수를 적용하고, `unreadCount` 캐시는 `notification.isRead`가 `false`일 때만 +1 한다.

## 결정 5: 훅은 `NotificationBell`이 아니라 앱 전역(`App.tsx`)에서 호출한다

`useAuctionStream`은 필요한 페이지 컴포넌트가 직접 호출하는 구조지만, 알림은 로그인한 유저라면 어느 페이지에 있든 실시간으로 받아야 한다(벨 아이콘은 `Header`에 항상 떠 있지만 드로어는 열려있을 때만 마운트된다). `NotificationBell` 안에서 훅을 쓰면 드로어를 닫을 때마다 연결이 끊기므로, `App.tsx`(`AuthProvider`/`QueryClientProvider` 안쪽, 라우트와 무관하게 항상 마운트되는 지점)에서 한 번만 연결한다.

- `useAuth().status === 'authenticated'`일 때만 연결(`enabled`).
- `status`가 `anonymous`로 바뀌거나 언마운트되면 연결을 닫는다.

```tsx
// frontend/src/app/App.tsx
export default function App() {
  useNotificationStream();
  return (
    <>
      <AppRoutes/>
      <ToastContainer/>
    </>
  );
}
```

## 결정 6: 우측 하단 알림 팝업 스택

`components/Toast.tsx`의 기존 `showToast`(단순 문구 토스트, 클릭 동작 없음)와는 별도로, 알림 전용 팝업 컴포넌트(`NotificationToastStack`)를 새로 만든다 — 알림 팝업은 클릭 시 읽음 처리+이동, X 클릭 시 그냥 닫기라는 별도 인터랙션이 필요해서 기존 `showToast`로는 표현이 안 된다.

동작 스펙:

- **위치**: 화면 우측 하단 고정, 여러 개면 세로로 쌓인다.
- **본문 클릭**: `markAsRead(id)` 호출 + `/auction/{auctionId}`로 이동 + 팝업 제거.
- **X 버튼 클릭**: 읽음 처리 없이 팝업만 제거(알림 자체는 목록에 안읽음으로 남는다).
- **자동 소멸**: 팝업이 뜬 지 30초가 지나면 자동으로 사라진다(읽음 처리는 안 함 — X와 동일하게 팝업만 제거). 알림마다 독립된 타이머를 가진다(새 알림이 와도 기존 타이머는 리셋되지 않는다).
- **여러 개 동시 도착**: 새 알림은 스택에 쌓인다. 각 팝업을 절대 위치가 아니라 flex 컬럼 컨테이너 안에서 순서대로 렌더링하면, 하나가 사라졌을 때(자동 소멸/X/클릭 무관) 나머지가 자동으로 리플로우되어 빈자리를 채운다 — 별도 위치 계산 로직이 필요 없다.

```ts
// frontend/src/hooks/useNotificationToasts.ts (신규)
type NotificationToast = NotificationDto & {receivedAt: number};

function useNotificationToasts() {
  const [toasts, setToasts] = useState<NotificationToast[]>([]);

  const push = (notification: NotificationDto) => {
    setToasts(current => [...current, {...notification, receivedAt: Date.now()}]);
  };
  const dismiss = (id: number) => {
    setToasts(current => current.filter(toast => toast.id !== id));
  };
  // 각 toast는 마운트 시 자신의 30초 setTimeout(() => dismiss(toast.id))을 건다(useEffect, id 기준).

  return {toasts, push, dismiss};
}
```

`useNotificationStream`이 알림을 수신하면 (a) 결정 4의 캐시 patch와 (b) 이 훅의 `push`를 함께 호출한다.

## 결정 7: 드로어를 열 때마다 하던 강제 refetch는 유지한다

SSE는 best-effort라(연결 끊김, 재연결 지연 등) 놓치는 알림이 있을 수 있다. 드로어를 열 때 강제 refetch하는 기존 로직(`3-frontend-integration-plan.md`)을 없애지 않고 안전망으로 그대로 둔다 — SSE는 "빠른 실시간 갱신"을, refetch는 "언제 열어도 정확한 최신 상태"를 보장하는 역할 분담.

## 파일 계획

`frontend/src/api/`
- `jwtClaims.ts` — 신규(결정 1).
- `notificationTicketApi.ts` — 신규(결정 2).

`frontend/src/hooks/`
- `useNotificationStream.ts` — 신규(결정 2, 3, 5). 수신 콜백에서 캐시 patch + `useNotificationToasts.push` 호출.
- `useNotificationToasts.ts` — 신규(결정 6). 팝업 상태(추가/제거/30초 자동 소멸) 관리.

`frontend/src/queries/`
- `notificationStreamCache.ts` — 신규(결정 4).

`frontend/src/components/`
- `NotificationToastStack.tsx` — 신규(결정 6). 우측 하단 고정, 본문 클릭(읽음+이동)/X(닫기만) 인터랙션.

`frontend/src/app/`
- `App.tsx` — `useNotificationStream()`/`NotificationToastStack` 배치(결정 5, 6).

변경 없음: `notificationApi.ts`/`notificationDto.ts`/`notificationQueries.ts`/`notificationMutations.ts`/`useNotifications.ts`/`NotificationBell.tsx`(강제 refetch 로직 그대로 유지, 결정 7).

## 커밋 단위 (예정)

1. `feat: 액세스 토큰에서 userId 클레임 디코드 유틸 추가`
2. `feat: 알림 SSE 티켓 발급 API 및 useNotificationStream 훅 구현(고정 지연 재연결)`
3. `feat: 알림 SSE 수신 시 목록/안읽음 캐시 직접 갱신`
4. `feat: 알림 팝업 스택(NotificationToastStack) 구현`
5. `feat: App 전역에 알림 SSE 연결 및 팝업 배치`
6. `test: useNotificationStream/팝업/캐시 갱신 테스트 작성`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
