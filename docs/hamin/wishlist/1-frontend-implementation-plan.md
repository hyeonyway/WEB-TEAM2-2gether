# 위시리스트(찜하기) 프론트엔드 구현 계획

지금까지 프론트엔드의 찜하기 기능은 백엔드 위시리스트 API와 전혀 연결되지 않고, `localStorage`만으로 동작하는 임시 구현이었다. 이 문서는 이를 실제 `wishlist` 도메인 API와 연동하는 작업의 계획을 정리한다.

## 현재 상태

### 백엔드 — `wishlist` 도메인 (이미 구현됨)

`WishlistController` (`/api/users/{userId}/wishlists`, `userId`는 아직 `@PathVariable` — JWT 인증 미들웨어 도입 전 임시 방식):

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| `POST` | `/api/users/{userId}/wishlists` | 찜 추가, body `{ "cardId": number }` | `201` `WishlistResponse` |
| `DELETE` | `/api/users/{userId}/wishlists/{cardId}` | 찜 해제 | `204` |
| `GET` | `/api/users/{userId}/wishlists` | 찜 목록 조회 | `200` `WishlistResponse[]` |

이미 찜한 카드를 다시 추가하면 `409 Conflict`.

### 프론트엔드 — 현재 구현 (`hooks/useCardFavorites.ts`)

- 찜 여부/토글을 전부 `localStorage`(`favorite-card-ids` 키)로만 관리한다. 백엔드 호출이 전혀 없다.
- 같은 브라우저 내 여러 컴포넌트 동기화를 위해 `window` 커스텀 이벤트(`card-favorites-change`)를 사용한다.
- 사용처: `CardFavoriteButton.tsx`(카탈로그 카드의 찜 버튼), `CardsPage.tsx`(찜 필터 "나의 찜"), `CardDetailPage.tsx`(찜 토글 + `wishlist_count` 표시).

### 발견한 이슈 — API 응답 네이밍 불일치

다른 도메인(`card`, `auction`) 응답은 전부 `@JsonProperty`로 `snake_case`를 명시한다 (`market_price`, `card_id` 등). 반면 `WishlistResponse`에는 이 어노테이션이 없어서 자바 필드명 그대로 `cardId`(camelCase)로 내려온다. → **백엔드 `WishlistResponse`에 `@JsonProperty("card_id")`를 추가해 다른 API와 표기법을 통일한다.**

## 결정된 사항

| 항목 | 결정 |
|---|---|
| 기존 localStorage 로직 처리 | 완전히 서버 API 기반으로 교체. 단, `USE_MOCK_API` 로컬스토리지 플래그가 `true`일 때는 (다른 도메인의 `sellApi.ts`와 동일한 패턴으로) 기존 `favorite-card-ids` localStorage 배열을 그대로 재사용해 목업 동작 |
| userId 확보 방식 | 기존 `debugAuthStorage.getDebugUserId()` 재사용. 경매/카드 API처럼 헤더가 아니라 위시리스트는 URL 경로(`/api/users/{userId}/wishlists`)에 넣어야 함 |
| `WishlistResponse` JSON 네이밍 | `@JsonProperty("card_id")` 추가해서 다른 도메인과 통일 |
| 비로그인(`DEBUG_USER_ID` 미설정) 상태에서 찜 버튼 클릭 시 | 확인 버튼이 필요한 `alert`가 아니라, 화면 하단에 잠깐 떴다가 자동으로 사라지는 토스트(스낵바)로 안내. 현재 프로젝트에 토스트 컴포넌트가 없어 신규로 만들어야 함 |

## 변경/신규 파일

### 백엔드

- `backend/src/main/java/com/dbidding/wishlist/dto/WishlistResponse.java` — `cardId` 필드에 `@JsonProperty("card_id")` 추가

### 프론트엔드 — 신규

- `dto/wishlistDto.ts`
  - `WishlistResponseDto { id: number; card_id: number }` (서버 응답 그대로)
  - `WishlistDto { id: number; cardId: number }` (내부 사용, 다른 도메인의 `*ResponseDto`/`*Dto` 분리 패턴과 동일)
- `api/wishlistApi.ts` — `sellApi.ts`와 동일하게 `isMockApiEnabled()`로 분기
  - mock: 기존 `favorite-card-ids` localStorage 배열을 읽고 써서 흉내
  - real: 호출하는 쪽이 넘겨준 userId를 경로에 넣어 `request()`로 GET/POST/DELETE 호출. userId 존재 여부 판단은 이 계층의 책임이 아니라 상위(`useWishlist` 훅)에서 미리 걸러서 넘긴다
- `queries/wishlistQueries.ts` — `wishlistQueries.list(userId)` (`queryOptions`, `queryKey`는 `['wishlists', userId]`)
- `queries/wishlistMutations.ts` — 찜 추가/해제 mutation
  - 성공 시 위시리스트 목록 캐시를 낙관적으로 갱신
  - 카드 목록/상세 쿼리(`cardQueryKeys`)도 invalidate해서 `wishlist_count` 최신화
- `components/Toast.tsx` — 전역 토스트. `useCardFavorites`가 쓰던 것과 같은 커스텀 이벤트 패턴으로 `showToast(message)` 함수를 export하고, `main.tsx`에 `<ToastContainer/>` 한 번만 마운트

### 프론트엔드 — 교체

- `hooks/useCardFavorites.ts` → `hooks/useWishlist.ts`로 대체
  - 외부에 노출하는 반환 형태(`{ favoriteCardIds, toggleFavorite }`)는 그대로 유지해서 사용처 수정을 최소화
  - 내부적으로 `useQuery(wishlistQueries.list(userId))` + mutation으로 동작
  - userId가 없을 때 `toggleFavorite` 호출 시 `showToast('로그인이 필요합니다')` 후 조기 반환
- import 교체 대상: `CardFavoriteButton.tsx`, `CardsPage.tsx`, `CardDetailPage.tsx`

## 시퀀스 (실제 API 모드, 찜 추가)

```mermaid
sequenceDiagram
    actor User as 사용자
    participant UI as CardFavoriteButton / CardDetailPage
    participant Hook as useWishlist
    participant RQ as react-query
    participant BE as 백엔드

    User->>UI: 찜 버튼 클릭
    UI->>Hook: toggleFavorite(cardId)
    alt userId 없음 (비로그인)
        Hook-->>UI: showToast("로그인이 필요합니다")
    else userId 있음
        Hook->>RQ: mutate(add, {userId, cardId})
        RQ-->>UI: 낙관적 업데이트 (즉시 active 표시)
        RQ->>BE: POST /api/users/{userId}/wishlists {cardId}
        alt 성공 (201)
            BE-->>RQ: WishlistResponse
            RQ->>RQ: wishlists 쿼리 확정 + 카드 쿼리 invalidate
        else 이미 찜한 카드 (409)
            BE-->>RQ: 409 Conflict
            RQ->>RQ: 낙관적 업데이트 롤백
        end
    end
```

## 남은 논의/구현 시 확인할 점

- mock 모드와 real 모드 전환 시 로컬스토리지 `favorite-card-ids` 데이터가 서로 공유되지 않는 점은 의도된 동작(모드별로 독립)으로 본다.
- `CardsPage`의 "찜 많은 순"(`FAVORITE`) 정렬과 카드 상세의 `wishlist_count`는 이미 서버가 카드 목록/상세 응답에 포함해서 내려주고 있어(집계는 이 문서의 범위 밖), 프론트는 찜 토글 후 관련 쿼리를 invalidate하는 것만 신경 쓰면 된다.

## 구현 중 추가로 발견한 사항

- **`httpClient.ts`의 204 처리 누락**: 공용 `request()`가 항상 `response.json()`을 호출하는데, 위시리스트 삭제(`DELETE`)는 바디 없는 `204 No Content`를 반환한다. 빈 바디에 `response.json()`을 호출하면 `SyntaxError`가 나서 삭제 요청이 무조건 실패했다. 이 프로젝트에서 `DELETE`로 `request()`를 쓴 첫 사례라 지금까지 드러나지 않았던 버그이며, `response.status===204`일 때 파싱을 건너뛰도록 수정했다.
- **wishlist API에 인가(authorization) 검증이 없음**: `WishlistController`는 `@CurrentUser`가 아니라 `@PathVariable Integer userId`를 그대로 받는다. 이 프로젝트에 `TestAuthFilter`(`X-Debug-User-Id` 헤더 기반)로 "현재 사용자"를 식별하는 임시 장치가 있지만, wishlist 컨트롤러는 이걸 전혀 쓰지 않고 URL의 `userId`를 그대로 신뢰한다. 즉 지금은 누구든 임의의 `userId`로 다른 사용자의 위시리스트를 조회·조작할 수 있다. 코드에도 `// TODO: 인증 미들웨어(JwtAuthFilter) 도입되면 @PathVariable Integer userId를 @CurrentUser Integer userId로 교체`라고 명시돼 있어, 이번 이슈 범위 밖의 알려진 제약으로 남겨둔다.

## 검증 결과

- 프론트 `npm run typecheck`, `npm run build`, 백엔드 `./gradlew compileJava compileTestJava` 모두 통과.
- **Mock API 모드**: 브라우저에서 직접 클릭해 비로그인 토스트, 찜 추가/해제, 새로고침 후 상태 유지, "나의 찜" 필터, 카드 상세 페이지 토글을 확인.
- **실제 백엔드 API 연동**: 로컬 MySQL(`dbidding` DB, 기존 시드 데이터)에 백엔드를 직접 기동해 `curl`로 `POST`(201, `card_id` snake_case 확인)/중복 `POST`(409)/`DELETE`(204) 계약을 먼저 확인한 뒤, 프론트를 `USE_MOCK_API=false`로 붙여 UI에서 찜을 토글하고 그 결과가 실제 DB에 반영되는 것을 `curl`로 대조 확인했다. 테스트로 생성한 위시리스트 레코드는 이후 정리했다.

> 이 문서는 claude의 도움을 받아 작성하였습니다.
