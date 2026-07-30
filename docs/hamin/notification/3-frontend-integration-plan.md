# 알림 목록 프론트엔드 연동 계획 (SSE 제외)

담당: D(임하민). [1-entity-and-list.md](1-entity-and-list.md), [2-read-status-and-navigation.md](2-read-status-and-navigation.md)에서 구현한 알림 API(`/api/notifications`)를 프론트에 붙인다. 실시간 갱신(WebSocket/SSE)은 `frontend/docs/frontend-api-spec.md` 3장에 설계돼 있지만 이번 범위 밖이다 — 붙기 전까지는 폴링도 하지 않고, 드로어를 열 때마다 강제로 새로 조회한다(아래 "refetch 정책" 참고).

관련 이슈: [#118](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/118)(백엔드 cursor pagination/unread-count), [#119](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/119)(이 문서 기반 프론트 구현).

## 범위

- Header에 알림 벨 아이콘 + 오른쪽에서 슬라이드되는 드로어(바깥 클릭 시 닫힘)
- 안읽음 개수 배지 (전용 카운트 API 기반, 없으면 아이콘만)
- 드로어 상단 전체/안읽음 토글(기본값 전체)
- 스크롤 기반 무한 로딩(서버 cursor pagination)
- 항목 클릭 시 읽음 처리, 항목 내 "이동" 버튼 클릭 시 읽음 처리 + 경매 상세로 이동
- 전체 읽음 처리 버튼
- 읽은 알림은 칙칙하게, 안읽은 알림은 하이라이트

범위 밖: SSE/폴링 실시간 갱신, 알림 설정(`notification-settings`).

## v1과 달라진 점 — cursor pagination을 이번에 반영하기로 결정

이 문서 초안(v1)은 "실제 구현 기준으로 붙이고, cursor 페이지네이션·unread-count·설정 API가 필요해지면 그때 백엔드부터 확장한다"고 적었었다. 그런데 요구사항에 "한 번에 다 로딩하지 말고 스크롤 시 추가 로딩"이 명시돼 있어, 이번 라운드에 바로 백엔드를 확장하기로 했다(클라이언트에서 전체를 미리 받아두고 화면에만 나눠 보여주는 방식도 고려했으나, 진짜 서버사이드 페이지네이션이 `frontend-api-spec.md` 3.3절 방향과도 맞고 장기적으로 스케일하므로 이 쪽을 선택). `#118`에서 구현 완료.

### 확장된 백엔드 계약

```
GET /api/notifications?cursor=&size=20&read=
→ { "items": NotificationResponse[], "nextCursor": number|null, "hasNext": boolean }

GET /api/notifications/unread-count
→ { "count": number }
```

- `cursor`: 이전 페이지 마지막 항목의 `id` (없으면 첫 페이지). 정렬 기준(`id DESC`)을 그대로 커서로 써서 `WHERE id < :cursor` keyset pagination — 기존 인덱스(`idx_notification_user_id`, `(user_id, is_read)`, 2번 문서에서 확정) 그대로 활용.
- `size`: 기본 20.
- `read=false`: 안읽음만, 그 외(생략 포함): 전체. 파라미터 이름은 스펙의 `unreadOnly` 대신 기존 컨벤션인 `read`를 그대로 유지했다.
- 응답이 배열 → `{ items, nextCursor, hasNext }` 봉투 구조로 바뀐 게 이번 breaking change. 프론트는 `response.items`로 접근해야 한다.
- `unread-count`는 페이지네이션 도입 후 "로드된 페이지 안의 개수"로는 배지 숫자가 부정확해지는 문제를 해결하기 위해 신규 추가 — `countByUserIdAndIsReadFalse` 단일 쿼리.

## 구현 전에 짚어야 할 것 (v1에서 유지되는 부분)

### PATCH 응답 204 — 이미 반영됨

`markAsRead`/`markAllAsRead`는 `ResponseEntity<Void>` + `.noContent().build()`(204)로 이미 고쳐져 있다(`NotificationControllerTest`도 `isNoContent()`로 맞춰짐). v1 작성 시점엔 열린 질문이었으나 현재는 해결된 상태.

### userId를 프론트가 넘길 필요가 없다

`httpClient.request`가 모든 요청에 `X-Debug-User-Id` 헤더를 자동으로 붙인다. `@CurrentUser`로 전환된 알림 API는 URL에 `userId`가 없으니 매 함수에 `userId` 파라미터를 넘길 필요가 없다.

### 응답 필드는 snake_case 변환이 필요 없다

`NotificationResponse`는 `id`, `auctionId`, `message`, `isRead`, `createdAt` 카멜케이스 그대로 내려온다 — `wishlistDto.ts`의 `mapWishlist` 같은 변환 함수가 필요 없다. 이 필드들은 이제 `items` 배열 안에 중첩된다는 점만 다르다.

### 읽음 처리와 이동을 분리한다

v1은 "항목 전체 클릭형(하이퍼텍스트 스타일)"로 읽음 처리와 이동을 한 번의 클릭에 묶는 안이었다. 실제로는 다음과 같이 분리하기로 결정했다:

- 알림 항목(행) 클릭 → `markAsRead(id)`만 호출, 이동 없음.
- 항목 안의 별도 "이동" 버튼 클릭 → `markAsRead(id)` + 경매 상세로 이동. 버튼 클릭도 읽음 처리를 포함한다(행을 먼저 클릭하지 않고 버튼만 눌러도 읽음 처리됨).
- 이동은 현재 프로젝트 컨벤션대로 `window.location.href` 풀 네비게이션을 사용한다. 이 프로젝트는 아직 client-side 라우터가 없다(`package.json`에 `react-router-dom` 없음, `main.tsx`가 `window.location.pathname`으로 페이지를 고름). Router 도입은 별도 이슈 [#112](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/112)(아직 OPEN, 미구현)의 스코프이고 `Header.tsx`도 그 영향 범위에 있다. `#112`가 머지되면 이 "이동" 버튼도 `useNavigate()`로 후속 변경이 필요하다.
- SSE 연동 이후엔 알림 도착 팝업에 별도 "읽음 처리" 버튼이 생길 예정이지만 이번 범위 밖이다.

## 파일 계획

`wishlistApi.ts`/`wishlistDto.ts`/`wishlistQueries.ts`/`wishlistMutations.ts`/`useWishlist.ts` 구조를 따르되, 무한 스크롤이 있어 `useQuery` 대신 `useInfiniteQuery`를 쓴다.

`frontend/src/dto/notificationDto.ts`
```ts
export type NotificationDto = {
  id: number;
  auctionId: number;
  message: string;
  isRead: boolean;
  createdAt: string; // ISO-8601, LocalDateTime 직렬화 문자열
};

export type NotificationPageDto = {
  items: NotificationDto[];
  nextCursor: number | null;
  hasNext: boolean;
};
```

`frontend/src/api/notificationApi.ts`
```ts
export async function fetchNotifications(params: {cursor?: number; size?: number; unreadOnly?: boolean}): Promise<NotificationPageDto> {
  const query = new URLSearchParams();
  if (params.cursor != null) query.set('cursor', String(params.cursor));
  query.set('size', String(params.size ?? 20));
  if (params.unreadOnly) query.set('read', 'false');
  return request<NotificationPageDto>(`/api/notifications?${query}`);
}

export async function fetchUnreadCount(): Promise<number> {
  const {count} = await request<{count: number}>('/api/notifications/unread-count');
  return count;
}

export async function markNotificationAsRead(notificationId: number): Promise<void> {
  await request<void>(`/api/notifications/${notificationId}/read`, {method: 'PATCH'});
}

export async function markAllNotificationsAsRead(): Promise<void> {
  await request<void>('/api/notifications/read-all', {method: 'PATCH'});
}
```
Mock API 모드(`isMockApiEnabled()`)는 로컬스토리지 기반으로 지원한다 — `wishlistApi.ts`의 `readMockCardIds`/`writeMockCardIds` 패턴과 동일하게 알림 배열을 로컬스토리지에 두고, cursor/size를 슬라이싱해 그럴듯하게 페이지네이션을 흉내낸다.

`frontend/src/queries/notificationQueries.ts`
```ts
export const notificationQueryKeys = {
  all: ['notifications'] as const,
  list: (unreadOnly: boolean) => [...notificationQueryKeys.all, 'list', unreadOnly] as const,
  unreadCount: ['notifications', 'unread-count'] as const,
};

export const notificationQueries = {
  list: (unreadOnly: boolean) => infiniteQueryOptions({
    queryKey: notificationQueryKeys.list(unreadOnly),
    queryFn: ({pageParam}) => fetchNotifications({cursor: pageParam, unreadOnly}),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.nextCursor ?? undefined : undefined,
    staleTime: 30_000,
  }),
  unreadCount: () => queryOptions({
    queryKey: notificationQueryKeys.unreadCount,
    queryFn: fetchUnreadCount,
    staleTime: 30_000,
  }),
};
```

`frontend/src/queries/notificationMutations.ts` — `markAsRead`/`markAllAsRead`. wishlist의 optimistic update 패턴과 동일하게 해당 항목(또는 전체)의 `isRead`를 무한 쿼리 캐시(모든 페이지) 안에서 즉시 `true`로 바꾸고, `unreadCount` 캐시도 낙관적으로 감소시킨 뒤 실패 시 롤백, `onSettled`에서 `notificationQueryKeys.all` 전체 invalidate.

`frontend/src/hooks/useNotifications.ts`
```ts
export function useNotifications(unreadOnly: boolean) {
  const {data, fetchNextPage, hasNextPage, isFetchingNextPage, refetch} = useInfiniteQuery(notificationQueries.list(unreadOnly));
  const {data: unreadCount = 0} = useQuery(notificationQueries.unreadCount());
  const notifications = data?.pages.flatMap(page => page.items) ?? [];
  // markAsRead, markAllAsRead는 mutation 래핑
  return {notifications, unreadCount, hasNextPage, fetchNextPage, isFetchingNextPage, refetch, markAsRead, markAllAsRead};
}
```
`unreadCount`는 이제 전용 API(`/unread-count`)에서 온다 — 목록이 페이지네이션되므로 로드된 페이지만으로 세면 부정확하기 때문이다(v1은 전체 목록을 한 번에 받아왔기 때문에 클라이언트에서 세는 것으로 충분했다).

## UI 계획

`frontend/src/components/NotificationBell.tsx`(신규)
- `Header.tsx`의 `head-account-actions` 안, `header-wallet` 버튼과 `header-account-nav`(마이페이지/로그인) 사이에 벨 아이콘(`lucide-react`의 `Bell`) + 안읽음 배지.
- 클릭하면 오른쪽에서 슬라이드되는 드로어를 열고, 배경(바깥 영역) 클릭 시 닫힌다.
- 드로어를 열 때마다 강제로 refetch한다 — SSE가 아직 없어 벨이 새 알림을 확인하는 유일한 수단이라, staleTime 캐시만 믿기보다 열 때마다 최신 상태를 보장한다. SSE 연동 후에는 이벤트 수신 시 invalidate하는 방식으로 대체될 예정.
- 상단에 전체/안읽음 토글(기본값 전체).
- 항목 클릭 시: `markAsRead(id)`만 호출(이동 없음). 항목 내 "이동" 버튼 클릭 시: `markAsRead(id)` 호출 후 `/auction/{auctionId}`로 `window.location.href` 이동.
- 목록 스크롤이 하단 근처에 도달하면(`IntersectionObserver`) `fetchNextPage()` 호출.
- 읽은 알림은 칙칙하게(낮은 opacity/무채색), 안읽은 알림은 하이라이트(배경색/좌측 바 등)로 구분.
- 드로어 하단에 "전체 읽음" 버튼 → `markAllAsRead()`.
- `Header.tsx`가 `@ts-nocheck` 프로토타입 상태라, 새 컴포넌트는 타입 체크 대상으로 만들고 `Header.tsx`에 끼워 넣는 지점만 최소로 건드린다.
- 디자인은 `tailwind.css`의 기존 색상 변수(`--muted`, `--border`, `--green` 등)와 `wallet-charge-*` 류 카드/오버레이 톤을 따라간다.

## 해결된 질문 (v1 "열린 질문" 갱신)

1. ~~PATCH 204 전환 시점~~ — 이미 반영됨.
2. Mock API 모드 지원 범위 — 로컬스토리지 기반으로 구현하기로 결정.
3. 드롭다운에 안읽음 전용/전체 탭 토글 — 이번에 포함, 기본값 전체.
4. staleTime/refetch 정책 — 드로어를 열 때마다 강제 refetch. SSE 도입 전까지의 임시 방편이며, SSE가 붙으면 이벤트 수신 시 invalidate하는 방식으로 대체한다.

> 이 문서는 claude의 도움을 받아 작성하였습니다.