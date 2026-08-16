# Notification SSE 재연결 시 REST 재조회 추가

이슈: #546

## 배경

Auction(`useAuctionStream`의 `onReconnected` 콜백 → 구독 화면이 `bid-context`/`bids` 쿼리 무효화)과
Wallet(`useWalletStream`의 `recoverBalance()` → `GET /api/wallet` 재조회)은 SSE가 재연결되면
REST로 현재 상태를 다시 맞추는 로직이 있다.

Notification(`useNotificationStream`)만 이 로직이 없다 — `eventSource.onopen`에서
`consecutiveFailures`만 0으로 리셋할 뿐, 재연결 자체를 감지해 REST로 캐치업하는 트리거가 없다.
평소엔 알림 드로어를 열 때(`useNotifications`의 목록 쿼리가 `enabled: isOpen`)마다 REST로
재조회되므로 대부분의 경우 문제가 없지만, 드로어를 열어둔 채(또는 배지 카운트만 보고 있는 채)
SSE가 오래 끊겼다 재연결되는 경우엔 최신 상태가 보장되지 않는다.

## 변경 사항

- `useNotificationStream`에 `useWalletStream`과 동일한 패턴으로 `opened` 지역 변수를 추가한다.
  `eventSource.onopen`에서 `opened`가 이미 `true`였던 경우(=재연결)에만
  `queryClient.invalidateQueries({queryKey: notificationQueryKeys.all})`를 호출해 list(both
  unreadOnly 값)와 unreadCount를 함께 무효화한다. 최초 연결 시에는 호출하지 않는다.
- `useNotificationStream.test.tsx`에 재연결 시 invalidate가 호출되는지, 최초 연결 시에는
  호출되지 않는지 검증하는 케이스를 추가한다.

## 범위 밖

- Auction/Wallet 쪽 재연결 로직 변경 없음.
- 드로어 open 트리거 REST 재조회(`useNotifications`)는 그대로 유지 — 이번 변경과 별개로 계속 동작한다.

## 완료 기준

- [ ] `useNotificationStream` 재연결 시 REST 재조회(invalidate) 반영
- [ ] `useNotificationStream.test.tsx` 케이스 추가/통과

> 이 문서는 claude의 도움을 받아 작성되었습니다.
