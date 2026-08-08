# 이슈 264 — 프론트 타임존 표시 정리 + 카운트다운 훅 통합

담당: 임하민. 이슈: [#264](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/264)
(브랜치 `refactor/264-frontend-timezone-countdown`).

`AGENTS.md`상 원래 담당 범위는 `notifications`/`psa`/`upload`/`wishlist`인데, #261~#263과
동일하게 사용자가 채팅에서 명시적으로 지시해서 진행한다. 백엔드(#261~#263)와 달리 이번은
프론트엔드 전용 작업이라 API 계약 변경은 없다.

## 1. Asia/Seoul 하드코딩 정리 — 완전 제거가 아니라 "의도를 명확히 하고 통일"

`frontend/src/utils/dateTime.ts`의 `formatKoreanDate`/`formatKoreanMonthDay`가
`timeZone:'Asia/Seoul'`을 하드코딩하고 있는데, 두 사용처를 따로 봐야 한다.

- **`PriceChangeAreaChart.tsx`(차트 X축)**: `formatKoreanMonthDay`가 받는 값은
  백엔드의 `ItemDailyStatistic.statisticsDate`(`LocalDate`, 예: `"2026-08-04"`)가
  Jackson으로 직렬화된 날짜-only 문자열이다. 이건 "어떤 순간"이 아니라 "백엔드가
  Asia/Seoul 기준으로 집계한 영업일"을 의미한다. `new Date("2026-08-04")`는 이 문자열을
  UTC 00:00으로 해석하므로, 뷰어의 브라우저 타임존으로 포맷하면(예: UTC-8) 하루 전
  날짜("08/03")로 잘못 표시된다. **이 값은 Asia/Seoul로 고정하는 게 맞다** — 뷰어가
  어디에 있든 백엔드가 집계한 "그 날"을 그대로 보여줘야 한다. 이 사용처는 그대로 둔다.
- **`NotificationBell.tsx`(7일 지난 알림의 절대 날짜 표시)**: `formatRelativeTime`이
  받는 `notification.createdAt`은 진짜 순간(`Instant`)이다. "이 알림이 언제
  왔는지"를 보여주는 거라 `AuctionDetailPage`의 `formatBidTime`, `OrdersPanel`,
  `AuctionBidDialog`가 이미 하는 것처럼 **뷰어의 브라우저 로컬 타임존**으로 보여주는 게
  일관적이다. 지금은 이 부분만 Asia/Seoul로 고정돼 있어 같은 앱 안에서 기준이 갈린다.

즉 "하드코딩 제거"가 목표가 아니라, **두 종류의 서로 다른 의미(백엔드 영업일 vs 뷰어
기준 순간)를 구분해서 각자 맞는 기준을 쓰도록 정리**하는 게 목표다.

- `dateTime.ts`에 `formatLocalDate`(브라우저 로컬 타임존, `formatKoreanDate`와 포맷은
  동일하고 `timeZone` 옵션만 빠짐)를 추가하고, 기존 두 함수 위에는 "왜 Asia/Seoul로
  고정하는지" 설명하는 주석을 남긴다.
- `NotificationBell.tsx`의 `formatRelativeTime` fallback을 `formatKoreanDate` →
  `formatLocalDate`로 교체한다.
- `PriceChangeAreaChart.tsx`는 변경하지 않는다.

## 2. 카운트다운 훅 통합

`AuctionCatalog.tsx`(`remainingTime` + `nowUtc()`/`parseUtc()` 기반 인라인 tick)와
`AuctionDetailPage.tsx`(`useAuctionNow` + `formatRemaining`)가 거의 동일한 로직을
각자 구현하고 있다. 종료 여부 판단(`!['OPEN','ENDING'].includes(status) ||
remaining==='경매 종료'`)도 두 곳에 그대로 중복돼 있다.

다만 `AuctionCatalog`는 여러 경매를 `.map()`으로 그리면서 **하나의 공유 tick**을
쓴다(경매마다 별도 `setInterval`을 만들지 않음). 이 구조를 지켜야 하므로, "매
카드마다 훅을 부르는" 통합은 React Hooks 규칙 위반이라 할 수 없다. 대신
`frontend/src/hooks/useCountdown.ts`에 다음 세 개를 분리해서 만든다.

- `useCountdownNow()`: 1초마다 갱신되는 `now`를 반환하는 훅(컴포넌트 최상위에서
  한 번만 호출 — 그리드 전체에서 하나, 상세 페이지에서 하나)
- `formatRemaining(endsAt, now)`: 순수 함수, `"HH:MM:SS"` 또는 `'경매 종료'`
- `isAuctionEnded(status, remaining)`: 순수 함수, 종료 여부 판단

`AuctionCatalog.tsx`는 그리드 전체에 훅 하나, `AuctionDetailPage.tsx`는 페이지에
훅 하나를 그대로 쓰고, `formatRemaining`/`isAuctionEnded`는 각자 필요한 곳(그리드는
`.map()` 안에서 경매마다, 상세는 한 번)에서 순수 함수로 호출한다.

경매 종료 절대시각(`endsAt`)을 받아 diff를 계산하는 방식 자체는 이미 타임존과 무관하게
항상 정확하므로(절대 시점끼리의 차이), 계산 로직은 바꾸지 않고 위치만 옮긴다.

`frontend/src/utils/utc.ts`의 `nowUtc`/`parseUtc`는 `AuctionCatalog.tsx`가 유일한
소비자였는데, 이 훅으로 옮기면서 전체 프론트에서 미사용이 되어 파일 자체를 삭제한다
(`utcDateParts`는 원래도 아무 데서도 안 쓰던 죽은 코드였다 — 이번 정리로 같이 없어짐,
이번 이슈가 만든 문제는 아님).

## 테스트 관련 주의

`formatLocalDate`는 설계상 "뷰어(테스트 실행 환경)의 로컬 타임존에 따라 달라지는" 게
맞는 동작이라, 백엔드에서 겪었던 것과 같은 문제(테스트가 실행 환경의 타임존에 따라
다른 값을 기대하게 되는 것)를 프론트 테스트에서도 만들 수 있다. 이 vitest 환경의 기본
타임존도 `Asia/Seoul`이라(`Intl.DateTimeFormat().resolvedOptions().timeZone`으로
확인) 특정 문자열을 하드코딩해서 assert하면 다른 타임존 머신에서 깨질 수 있다.
`formatLocalDate`는 정확한 문자열이 아니라 포맷 형태(정규식)만 검증한다.

## 변경 파일

- `frontend/src/hooks/useCountdown.ts` (신규): `useCountdownNow`/`formatRemaining`/
  `isAuctionEnded`
- `frontend/src/hooks/useCountdown.test.ts` (신규)
- `frontend/src/utils/dateTime.ts`: `formatLocalDate` 추가, 기존 두 함수에 주석 추가
- `frontend/src/utils/dateTime.test.ts`: `formatLocalDate` 테스트 추가
- `frontend/src/components/NotificationBell.tsx`: `formatKoreanDate` →
  `formatLocalDate`
- `frontend/src/pages/auction/components/AuctionCatalog.tsx`: 인라인 tick/format을
  공용 훅으로 교체
- `frontend/src/pages/auction-detail/AuctionDetailPage.tsx`: 동일
- `frontend/src/utils/utc.ts` (삭제): 마지막 소비자가 없어짐

## 결과

- `npx tsc --noEmit` 통과
- `useCountdown.test.ts`(6개), `dateTime.test.ts`(4개, `formatLocalDate` 추가분 포함) 통과
- 전체 스위트(`npx vitest run`) 239개 중 17개 실패, 222개 통과. 실패 17개는 전부
  `api/authApi`, `api/walletApi`, `auth/AuthProvider`, `api/authenticatedRequest`,
  `queries/walletQueries`, `app/router`, `components/auth/AuthModal` 관련이라 이번
  변경(카운트다운/타임존 표시)과 무관한 파일들이다. `dev`에 이번 변경 없이 그대로
  돌려봐도 동일한 17개가 실패해서 **사전 존재하는 문제**임을 확인했다(#261~#263에서
  겪었던 것과 같은 패턴 — 매번 원인 불명 실패를 만나면 `dev` 단독 실행으로
  비교 확인).
- 브라우저로 직접 확인(`localStorage`의 `USE_MOCK_API`/`DEBUG_USER_ID`로 mock
  모드 + 가짜 로그인 상태를 켜서 확인):
  - `AuctionCatalog`(경매 목록): mock 데이터의 `ends_at`을 임시로 미래 시각으로
    바꿔서 카운트다운이 `01:59:33` → `01:59:07`로 실제로 흐르는 것을 확인(검증
    후 mock 데이터는 원복, 커밋에 포함하지 않음)
  - `NotificationBell`: 7일 넘게 지난 mock 알림 5건이 전부 `formatLocalDate`
    경로로 "2026. 07. 30." 형식으로 정상 표시되는 것을 확인
  - `PriceChangeAreaChart`(카드 시세 차트): 변경하지 않은 `formatKoreanMonthDay`가
    여전히 정상 동작하는 것을 확인(회귀 없음)
  - `AuctionDetailPage`(경매 상세)는 `fetchAuctionDetail`에 mock 분기가 없어서
    (`api/auctionApi.ts`의 사전 존재하는 mock 커버리지 공백, 이번 이슈와 무관)
    실제 백엔드 없이는 브라우저로 직접 확인할 수 없었다. 다만 이 페이지의
    카운트다운은 `AuctionCatalog`에서 이미 확인한 것과 완전히 동일한 공용 훅/함수를
    그대로 쓰고 페이지 고유 로직이 없어서, 타입체크와 기존 `AuctionDetailPage.test.tsx`
    통과로 충분히 커버된다고 판단했다

## 커밋 이력

1. `refactor: 경매 카운트다운 로직을 공용 훅으로 통합`
2. `refactor: 알림의 절대 날짜 표시를 브라우저 로컬 타임존으로 통일`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
