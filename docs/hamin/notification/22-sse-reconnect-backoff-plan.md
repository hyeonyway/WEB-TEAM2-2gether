# SSE 재연결 백오프/포기조건 도입 계획 (#483)

담당: D(임하민). [5-frontend-sse-integration.md](5-frontend-sse-integration.md)(알림 SSE)와 지갑 SSE(`useWalletStream`)에 이어지는 라운드. 이슈 [#483](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/483) — 인증이 영구 실패한 상태에서도 SSE 재연결이 고정 2초 간격으로 무한 반복되는 문제를 고친다.

이 문서는 설계만 다룬다. 코드는 피드백 반영 후 작성한다.

## 범위

- `useWalletStream`/`useNotificationStream`의 `onerror` 재연결에 지수 백오프(최대 상한 포함) 적용.
- 연속 실패가 임계치를 넘으면 `/api/auth/me`로 세션을 재검증하고, 실제로 죽어 있으면 기존 세션 무효화 경로(`setSessionUserId(null)`)를 태워 `AuthProvider`가 `anonymous`로 전환되게 한다.
- 두 훅이 중복 구현하던 백오프 계산을 공유 유틸로 추출.

범위 밖: `useWalletStream`에 티켓 발급 가드 추가(이슈가 지목한 원인 1은 `#469` 세션 인증 전환으로 이미 해소됨 — 현재 `useWalletStream`은 애초에 티켓을 발급하지 않고 세션 쿠키로만 연결한다), 재연결 로직 자체의 대규모 리팩터(두 훅을 하나의 공용 훅으로 합치는 것)는 이번 스코프에서 제외.

## 배경 — 원인 1은 이미 해소됨

이슈 본문은 `useWalletStream`이 매 재시도마다 `issueSseTicket()`부터 호출한다고 지적하지만, 현재 `dev` 기준 `useWalletStream.ts`는 이미 `EventSource(streamUrl(), {withCredentials:true})`로 세션 쿠키 인증만 쓴다 — 티켓 발급 코드 자체가 없다. `#469`(세션 인증 단일화, PR #487)에서 지갑/알림 스트림 모두 세션 쿠키 기반으로 이관되면서 원인 1(티켓 재발급 무한 반복)은 사라졌다. 알림 쪽 `getSessionUserId()` 가드도 여전히 남아있지만, 두 훅 모두 호출부(`AuthProvider`/`App.tsx`)에서 `enabled = status === 'authenticated'`로 이미 게이팅되므로 방어적 코드 그 이상은 아니다.

남은 건 원인 2 — `onerror`가 실패 원인/횟수와 무관하게 고정 2초 뒤 무조건 재시도하는 부분이다. 세션이 쿠키 만료/서버 강제 로그아웃 등으로 죽으면, `AuthProvider`가 이를 스스로 감지할 방법이 없다(초기 마운트 때 `/api/auth/me`를 한 번 호출할 뿐, 이후 재검증 트리거가 없다). 그 결과 `status`는 계속 `authenticated`로 남고, SSE는 401을 받을 때마다 2초 뒤 재시도를 영원히 반복한다.

## 결정 1: "인증 실패냐 아니냐"는 `onerror`만으로 구분하지 않는다

`EventSource`의 `error` 이벤트는 상태 코드를 노출하지 않는다(네트워크 일시 장애든 401이든 동일하게 `onerror`만 뜬다). 그래서 매 실패마다 재로그인 상태로 단정하지 않고, **연속 실패 횟수가 임계치(5회)를 넘을 때만** `/api/auth/me`로 실제 세션 상태를 확인한다.

- 재검증 결과 세션이 살아있으면: 그냥 지수 백오프를 계속한다(백엔드가 잠깐 내려간 경우처럼 복구될 수 있는 상황을 로그아웃으로 오판하지 않기 위함).
- 재검증 결과 세션이 죽었으면: 기존 `AuthProvider.initialize()`의 실패 처리와 동일하게 `setSessionUserId(null)` + `clearCsrfToken()`을 호출한다. 이러면 `AuthProvider`의 `status`가 `anonymous`로 바뀌고, `useWalletStream(status==='authenticated')`/`useNotificationStream({enabled: status==='authenticated'})` 호출부의 `enabled`가 `false`로 넘어가면서 훅의 effect cleanup이 타이머/EventSource를 정리한다 — 훅 내부에 별도의 "포기" 상태를 만들 필요가 없다.
- `anonymous` 전환은 이미 `RequireAuth`가 감지해서 `showAuthRequiredToast()`("로그인이 필요합니다") + `/`로 리다이렉트한다 — 이슈가 요구하는 "재로그인이 필요합니다 같은 사용자 노출 상태"를 새로 만들지 않고 기존 경로를 재사용한다.

세션이 아직 살아있는데 SSE만 계속 실패하는 경우(예: 프록시/방화벽이 SSE만 막는 경우)는 이번 스코프에서 사용자 노출 UI를 추가하지 않는다 — 이슈 체크리스트의 핵심 시나리오(인증 영구 실패)가 아니고, 백오프 상한(30초)만으로도 서버 부하 문제는 해결된다.

## 결정 2: 지수 백오프 상수와 재검증 주기

새 파일 `frontend/src/hooks/sseReconnectPolicy.ts`:

```ts
export const SSE_RECONNECT_BASE_DELAY_MS = 2_000;
export const SSE_RECONNECT_MAX_DELAY_MS = 30_000;
export const SSE_REVALIDATE_AFTER_FAILURES = 5;

export function nextSseReconnectDelayMs(consecutiveFailures: number): number {
  const exponent = Math.max(0, consecutiveFailures - 1);
  return Math.min(SSE_RECONNECT_BASE_DELAY_MS * 2 ** exponent, SSE_RECONNECT_MAX_DELAY_MS);
}

export function shouldRevalidateSession(consecutiveFailures: number): boolean {
  return consecutiveFailures > 0 && consecutiveFailures % SSE_REVALIDATE_AFTER_FAILURES === 0;
}
```

지연 시퀀스: 2s → 4s → 8s → 16s → 30s(상한, 이후 고정) — 실제 사고 사례의 "3초 간격 6,376회"보다 훨씬 완만해지고, 5번째 실패마다(30초 상한 도달 후에는 약 150초마다) 세션을 재검증한다.

두 훅 모두 `consecutiveFailures`를 effect 스코프의 지역 변수로 두고(`stopped`/`opened`와 동일한 패턴), `onopen` 성공 시 0으로 리셋한다.

## 결정 3: 세션 재검증도 공유 함수로 추출

새 파일 `frontend/src/auth/session/sessionRevalidation.ts` — `AuthProvider.initialize()`가 초기 마운트 때 하는 `/api/auth/me` 호출과 별개로, SSE 실패 임계치 도달 시에만 쓰는 가벼운 버전:

```ts
export async function revalidateSession(): Promise<boolean> {
  try {
    const current = await request<CurrentAccountResponseDto>('/api/auth/me', {credentials: 'include'});
    if (getSessionUserId() !== current.userId) setSessionUserId(current.userId);
    return true;
  } catch {
    setSessionUserId(null);
    clearCsrfToken();
    return false;
  }
}
```

CSRF 토큰은 성공 시 재발급하지 않는다 — 세션이 살아있다면 기존 CSRF 토큰도 유효하고, 굳이 매 재검증마다 `/api/auth/csrf`를 추가로 부를 이유가 없다. `AuthProvider.initialize()`는 그대로 두고 건드리지 않는다(마운트 시 CSRF까지 함께 받아와야 하는 별개 책임).

## 테스트

- `sseReconnectPolicy.test.ts`: 지연 시퀀스/상한, 재검증 트리거 주기 검증.
- `useWalletStream.test.tsx`/`useNotificationStream.test.tsx`: 연속 `onerror` N회 후 백오프 지연이 늘어나는지, 임계치 도달 시 `/api/auth/me`를 호출하는지, 401이면 `setSessionUserId(null)`이 호출되어 이후 재연결을 시도하지 않는지(effect가 `enabled=false`로 재실행되는 상위 시나리오는 `AuthProvider` 쪽 책임이므로 훅 테스트에서는 "세션 무효화 함수 호출 여부"까지만 검증).
