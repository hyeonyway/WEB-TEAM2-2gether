# 프론트엔드 기준 API 명세서

> 기준 화면: 홈, 카드 시세 목록/상세, 경매 목록/상세/입찰, 판매 등록, 나의 대시보드, 마이페이지
> Base URL: `/api`
> 인증: Access/Refresh Token을 `HttpOnly + Secure + SameSite=Lax` 쿠키로 전달
> 날짜: ISO-8601 UTC 문자열, 금액: 정수 원 단위, ID: `string`

## 0. 공통 규약

### 성공 응답

단건 응답은 리소스를 그대로 반환한다. 목록은 cursor 기반으로 반환해 무한 스크롤을 지원한다.

```json
{
  "items": [],
  "nextCursor": "opaque-cursor",
  "hasNext": true
}
```

### 오류 응답

```json
{
  "code": "BID_TOO_LOW",
  "message": "최소 입찰가 이상 입력해 주세요.",
  "fieldErrors": {
    "amount": "최소 입찰가는 139000원입니다."
  },
  "traceId": "01J..."
}
```

| HTTP | 용도 |
|---|---|
| `400` | 형식·필드 검증 실패 |
| `401` | 인증 만료 또는 미인증 |
| `403` | 권한 부족·제재 계정 |
| `404` | 리소스 없음 |
| `409` | 동시성 충돌·이미 종료·중복 요청 |
| `422` | 비즈니스 규칙 위반 |
| `423` | 로그인 잠금·계정 정지 |
| `429` | 로그인 시도·API 호출 제한 |

### 변경 요청 공통 헤더

입찰, 경매 등록, 찜, 신고처럼 재시도될 수 있는 변경 요청은 다음 헤더를 사용한다.

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

동일 사용자·동일 엔드포인트·동일 키의 재요청은 최초 결과를 반환한다. 다른 요청 본문에 같은 키를 재사용하면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.

---

# 1. 인증/계정 도메인

## 1.1 인증

### 회원가입

`POST /auth/signup`

```json
{
  "email": "collector@example.com",
  "password": "Password123!",
  "name": "포켓컬렉터",
  "phone": "01012345678"
}
```

```json
{
  "user": {
    "id": "usr_01",
    "email": "collector@example.com",
    "name": "포켓컬렉터",
    "role": "USER",
    "status": "ACTIVE"
  }
}
```

오류: `EMAIL_ALREADY_EXISTS`, `WEAK_PASSWORD`, `INVALID_PHONE`.

### 로그인

`POST /auth/login`

```json
{
  "email": "collector@example.com",
  "password": "Password123!"
}
```

```json
{
  "user": {
    "id": "usr_01",
    "email": "collector@example.com",
    "name": "포켓컬렉터",
    "role": "USER",
    "status": "ACTIVE"
  },
  "loginFailureCount": 0
}
```

서버는 Access/Refresh 쿠키를 발급한다.

- 이메일별 로그인 실패 횟수 증가는 원자적으로 처리한다.
- 성공 시 실패 횟수를 `0`으로 초기화한다.
- 연속 실패 기준 초과 시 `423 LOGIN_LOCKED`와 `lockedUntil`을 반환한다.
- 여러 서버 인스턴스에서도 동일한 카운트가 보장되어야 한다.

### 세션 새로고침과 Refresh Rotation

`POST /auth/refresh`

요청 본문은 없으며 Refresh 쿠키를 사용한다. 성공 시 기존 Refresh Token을 폐기하고 Access/Refresh 쿠키를 모두 재발급한다.

```json
{
  "userId": "usr_01",
  "expiresAt": "2026-07-24T13:30:00Z"
}
```

- 이미 사용된 Refresh Token이 다시 제출되면 토큰 패밀리 전체를 폐기한다.
- 응답: `401 REFRESH_TOKEN_REUSED` 또는 `401 REFRESH_TOKEN_EXPIRED`.
- 프론트는 일반 API에서 `401 ACCESS_TOKEN_EXPIRED`를 한 번 받으면 refresh 후 원 요청을 한 번만 재시도한다.

### 로그아웃

`POST /auth/logout`

현재 Refresh 세션을 폐기하고 인증 쿠키를 만료한다. 응답은 `204 No Content`.

### 현재 세션 조회

`GET /auth/session`

```json
{
  "authenticated": true,
  "user": {
    "id": "usr_01",
    "name": "포켓컬렉터",
    "email": "collector@example.com",
    "role": "USER",
    "status": "ACTIVE"
  }
}
```

## 1.2 계정 관리

### 내 정보 조회

`GET /users/me`

```json
{
  "id": "usr_01",
  "name": "포켓컬렉터",
  "email": "collector@example.com",
  "phone": "01012345678",
  "status": "ACTIVE",
  "sanction": null,
  "defaultShippingAddress": {
    "recipient": "포켓컬렉터",
    "postalCode": "04782",
    "address1": "서울시 성동구 성수이로 12길 24",
    "address2": "101동 101호"
  }
}
```

### 회원 정보 수정

`PATCH /users/me`

```json
{
  "name": "포켓컬렉터",
  "phone": "01012345678"
}
```

### 기본 배송지 수정

`PUT /users/me/shipping-address`

```json
{
  "recipient": "포켓컬렉터",
  "phone": "01012345678",
  "postalCode": "04782",
  "address1": "서울시 성동구 성수이로 12길 24",
  "address2": "101동 101호"
}
```

## 1.3 문의·신고·제재

### 관리자 문의 등록

`POST /inquiries`

```json
{
  "type": "AUCTION",
  "title": "경매 문의",
  "content": "문의 내용입니다.",
  "auctionId": "auc_01"
}
```

문의 유형: `AUCTION | SHIPPING | PAYMENT | PRODUCT | ETC`

### 내 문의 목록/상세

- `GET /inquiries?cursor=&size=20&status=`
- `GET /inquiries/{inquiryId}`

상태: `WAITING | ANSWERED | CLOSED`

### 유저 신고 등록

`POST /reports`

```json
{
  "targetUserId": "usr_02",
  "type": "AUCTION_INTERFERENCE",
  "reason": "반복적인 허위 입찰이 의심됩니다.",
  "auctionId": "auc_01",
  "evidenceImageIds": ["img_01"]
}
```

신고 유형: `AUCTION_INTERFERENCE | ABUSIVE_MESSAGE | SUSPECTED_FRAUD | ETC`

### 내 신고 목록/상세

- `GET /reports?cursor=&size=20&status=`
- `GET /reports/{reportId}`

상태: `RECEIVED | REVIEWING | RESOLVED | REJECTED`

### 관리자 API

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/admin/inquiries` | 문의 목록 |
| `POST` | `/admin/inquiries/{id}/answer` | 문의 답변 |
| `GET` | `/admin/reports` | 신고 목록 |
| `POST` | `/admin/reports/{id}/resolve` | 신고 처리 |
| `POST` | `/admin/users/{userId}/sanctions` | 경고·일시정지·영구정지 |
| `DELETE` | `/admin/users/{userId}/sanctions/{id}` | 제재 해제 |

```json
{
  "type": "SUSPEND",
  "reason": "경매 방해 행위",
  "endsAt": "2026-08-01T00:00:00Z"
}
```

제재 유형: `WARNING | SUSPEND | PERMANENT_BAN`. 제재된 사용자는 Filter Chain에서 경매 등록·입찰 등 보호 API가 차단된다.

## 1.4 Filter Chain 적용 순서

```text
Request ID
→ CORS
→ Rate Limit
→ Access Token 검증
→ 사용자/제재 상태 확인
→ 역할 권한 확인
→ Idempotency
→ Controller
```

공개 API: 회원가입, 로그인, refresh, 카드/경매 공개 조회.
로그인 필요: 입찰, 찜, 지갑, 대시보드, 판매 등록, 계정, 문의/신고.
관리자 필요: `/admin/**`.

---

# 2. 입찰·지갑 정합성

## 2.1 지갑

### 지갑 조회

`GET /wallet`

```json
{
  "currency": "KRW_POINT",
  "totalBalance": 850000,
  "availableBalance": 712000,
  "frozenBalance": 138000,
  "version": 17
}
```

### 지갑 원장

`GET /wallet/transactions?cursor=&size=20&type=`

유형: `MOCK_CHARGE | BID_FREEZE | BID_RELEASE | WIN_DEBIT | REFUND`

```json
{
  "items": [
    {
      "id": "wtx_01",
      "type": "BID_FREEZE",
      "amount": -138000,
      "auctionId": "auc_01",
      "balanceAfter": 712000,
      "createdAt": "2026-07-24T10:00:00Z"
    }
  ],
  "nextCursor": null,
  "hasNext": false
}
```

### 목업 잔액 충전

`POST /wallet/mock-charges`

```json
{
  "amount": 50000
}
```

허용 금액과 1일 한도는 서버가 검증하며 운영 환경에서는 비활성화한다.

## 2.2 입찰

### 입찰 사전 정보 조회

`GET /auctions/{auctionId}/bid-context`

입찰 팝업을 열 때 최신 값으로 조회한다.

```json
{
  "auctionId": "auc_01",
  "status": "OPEN",
  "version": 31,
  "currentPrice": 138000,
  "minimumBid": 139000,
  "bidIncrement": 1000,
  "myBidStatus": "OUTBID",
  "myBidAmount": 132000,
  "wallet": {
    "availableBalance": 712000,
    "frozenBalance": 138000
  },
  "recentBids": [
    {
      "id": "bid_31",
      "amount": 138000,
      "bidderAlias": "po***or",
      "isHighest": true,
      "createdAt": "2026-07-24T10:00:00Z"
    }
  ]
}
```

### 입찰 요청

`POST /auctions/{auctionId}/bids`

필수 헤더: `Idempotency-Key`

```json
{
  "amount": 139000,
  "expectedAuctionVersion": 31
}
```

```json
{
  "bid": {
    "id": "bid_32",
    "amount": 139000,
    "status": "LEADING",
    "createdAt": "2026-07-24T10:00:02Z"
  },
  "auction": {
    "id": "auc_01",
    "version": 32,
    "currentPrice": 139000,
    "minimumBid": 140000,
    "bidCount": 369,
    "endsAt": "2026-07-24T11:00:00Z"
  },
  "wallet": {
    "availableBalance": 711000,
    "frozenBalance": 139000
  }
}
```

검증 오류:

| code | 조건 |
|---|---|
| `AUCTION_NOT_OPEN` | 시작 전·마감·취소 |
| `SELLER_CANNOT_BID` | 본인 경매 입찰 |
| `BID_TOO_LOW` | 최신 최소 입찰가 미만 |
| `INVALID_BID_UNIT` | 호가 단위 불일치 |
| `INSUFFICIENT_BALANCE` | 가용 잔액 부족 |
| `ALREADY_LEADING` | 이미 최고가 입찰 중 |
| `AUCTION_VERSION_CONFLICT` | 동시 입찰로 버전 변경 |
| `ACCOUNT_RESTRICTED` | 계정 제재 |

`AUCTION_VERSION_CONFLICT` 응답은 최신 `currentPrice`, `minimumBid`, `version`을 포함해 프론트가 팝업 값을 즉시 갱신하게 한다.

### 최근 입찰 내역

`GET /auctions/{auctionId}/bids?cursor=&size=20`

공개 응답에서는 사용자 ID를 노출하지 않고 `bidderAlias`만 제공한다.

### 입찰 포기

`POST /auctions/{auctionId}/bids/withdraw`

```json
{
  "expectedAuctionVersion": 32,
  "reason": "USER_REQUEST"
}
```

- 서비스 정책상 포기가 허용되는 상태에서만 처리한다.
- 최고 입찰자의 포기는 기본적으로 금지하거나 관리자 승인 흐름으로 보낸다.
- 포기 성공 시 해당 사용자의 동결액을 해제한다.
- 재입찰 금지 정책이 있다면 `withdrawnAt`, `rebidAllowed`를 응답한다.

## 2.3 하나의 트랜잭션 경계

입찰 요청 하나에서 아래 작업은 반드시 한 트랜잭션으로 처리한다.

```text
경매 행 잠금 또는 원자적 버전 갱신
→ 상태/시간/최소가/판매자 검증
→ 기존 최고 입찰자 동결액 해제
→ 신규 입찰자 기존 동결액과 신규 금액의 차액 계산
→ 지갑 가용액 차감·동결액 증가
→ 입찰 저장
→ 경매 현재가·입찰 수·버전 갱신
→ Outbox 이벤트 저장
→ Commit
```

- DB 커밋 이후 Outbox 이벤트를 WebSocket/알림으로 발행한다.
- 락 획득 순서는 `auction → wallet(userId 오름차순)`으로 고정해 교착을 줄인다.
- 재입찰은 전체 금액을 다시 동결하지 않고 기존 동결액과의 차액만 반영한다.
- 실패 시 입찰·지갑·경매 값이 모두 원복되어야 한다.

---

# 3. 실시간 전파

## 3.1 WebSocket 연결

`GET /ws`

- 인증 쿠키로 연결 사용자를 식별한다.
- 미인증 사용자는 공개 경매 가격 채널만 구독 가능하다.
- 로그인 사용자는 개인 알림·대시보드 채널을 추가로 구독한다.

구독 채널:

| 채널 | 용도 |
|---|---|
| `/topic/auctions/{auctionId}` | 경매 가격·입찰 수·마감 |
| `/user/queue/notifications` | 상회 입찰·종료 임박·종료·찜 |
| `/user/queue/dashboard` | 참여/찜/낙찰 목록 갱신 |
| `/user/queue/wallet` | 가용·동결 잔액 변경 |

모든 이벤트 공통 형식:

```json
{
  "eventId": "evt_01",
  "type": "AUCTION_PRICE_UPDATED",
  "occurredAt": "2026-07-24T10:00:02Z",
  "aggregateId": "auc_01",
  "version": 32,
  "sequence": 1082,
  "payload": {}
}
```

## 3.2 이벤트

### 경매가 갱신

`AUCTION_PRICE_UPDATED`

```json
{
  "auctionId": "auc_01",
  "currentPrice": 139000,
  "minimumBid": 140000,
  "bidCount": 369,
  "leadingBidderAlias": "po***or",
  "endsAt": "2026-07-24T11:00:00Z"
}
```

### 상회 입찰

`BID_OUTBID`

```json
{
  "auctionId": "auc_01",
  "cardName": "피카츄 P 메가 에볼루션 프로모카드",
  "myBidAmount": 138000,
  "currentPrice": 139000,
  "minimumBid": 140000
}
```

### 종료 임박

`AUCTION_ENDING_SOON`

```json
{
  "auctionId": "auc_01",
  "endsAt": "2026-07-24T11:00:00Z",
  "remainingSeconds": 300
}
```

같은 사용자·경매·임계값 조합은 한 번만 발송한다.

### 경매 종료

`AUCTION_ENDED`

```json
{
  "auctionId": "auc_01",
  "result": "WON",
  "winningPrice": 139000,
  "winnerUserId": "usr_01"
}
```

개인 채널에서는 `result`가 `WON | LOST | NO_BID`로 전달된다.

### 찜 변경

`FAVORITE_CHANGED`

```json
{
  "auctionId": "auc_01",
  "favorite": true,
  "favoriteCount": 2793
}
```

### 지갑 변경

`WALLET_UPDATED`

```json
{
  "availableBalance": 711000,
  "frozenBalance": 139000,
  "reason": "BID_FREEZE",
  "auctionId": "auc_01"
}
```

## 3.3 알림 API

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/notifications?cursor=&size=20&unreadOnly=` | 알림 목록 |
| `GET` | `/notifications/unread-count` | 읽지 않은 개수 |
| `PATCH` | `/notifications/{id}/read` | 단건 읽음 |
| `PATCH` | `/notifications/read-all` | 전체 읽음 |
| `GET` | `/notification-settings` | 알림 설정 |
| `PUT` | `/notification-settings` | 상회·임박·종료·찜 알림 설정 |

WebSocket 재연결 후 프론트는 마지막 `sequence` 이후 누락 이벤트를 조회한다.

`GET /events/missed?afterSequence=1082`

---

# 4. 경매 라이프사이클 & 관측

## 4.1 홈

### 홈 개요

`GET /home/insights`

현재 진행 중인 경매의 상승·입찰·프리미엄 인사이트를 반환한다. 인사이트 수치는 카드 종이 아닌 경매 건수다.

`GET /home/market?days=30`

종료 경매의 일별 평균 낙찰가와 입찰 수, 기간 요약을 반환한다.

`GET /home/top-gainers?limit=5`

전일 대비 시세가 상승한 카드를 반환한다. 순위 항목은 `auctionId` 없이 `cardId`를 사용해 카드 상세로 이동한다.

```json
[
  {"id":"RISING","title":"경매가 상승","value":8,"changeRate":9.4,"note":"시작가 대비 상승률이 높은 경매부터 확인하세요.","sort":"CHANGE_HIGH"},
  {"id":"NEW_BIDS","title":"신규 입찰","value":8,"changeRate":null,"note":"입찰 수가 많은 경매부터 확인하세요.","sort":"BID_COUNT"},
  {"id":"ACTIVE","title":"프리미엄 경매","value":3,"changeRate":null,"note":"현재 경매가가 높은 경매부터 확인하세요.","sort":"PRICE_HIGH"}
]
```

```json
{
  "marketSummary": {
    "monthlyWinningPriceTotal": 7466250,
    "monthlyEndedAuctionCount": 241,
    "monthlyBidCount": 942,
    "monthlyHighestPrice": 248875
  },
  "marketHistory": [
    {
      "date": "06/19",
      "averagePrice": 151000,
      "bidCount": 112
    },
    {
      "date": "07/19",
      "averagePrice": 248875,
      "bidCount": 392
    }
  ]
}
```

```json
{
  "periodDays": 30,
  "gainers": [
    {
      "cardId": 2,
      "name": "피카츄 P 스칼렛&바이올렛 프로모 카드",
      "price": 135000,
      "changeRate": 8.9,
      "theme": "gold",
      "bidCount": 105,
      "currentDate": "2026-07-27",
      "previousDate": "2026-07-25",
      "imageUrl": "pokemon-cards/card.webp",
      "priceHistory": [{"date": "07/27", "price": 135000}]
    }
  ],
  "losers": []
}
```

가격 변동 API는 `GET /api/home/price-movers?limit=5`이며 오늘을 제외한 최근
30일 내 각 카드의 최근 두 유효 거래를 비교한다. 상승과 하락 목록을 한 번에
받으므로 탭 전환 시 추가 요청하지 않는다.

홈 이동 규칙:

| 홈 요소 | 이동 경로 | 경매 화면 초기 상태 |
|---|---|---|
| 경매가 상승 | `/auction?sort=CHANGE_HIGH` | 상승률 높은순 |
| 신규 입찰 | `/auction?sort=BID_COUNT` | 입찰 수 높은순 |
| 프리미엄 경매 | `/auction?sort=PRICE_HIGH` | 경매가 높은순 |
| 가격 변동 카드 | `/cards/{cardId}` | 해당 카드 상세 |

가격 변동 순위는 진행 경매 존재 여부와 관계없이 확정 통계의 최근 두 거래를
비교하며 `cardId`로 카드 상세에 이동한다.

## 4.2 카드 시세

### 카드 목록

`GET /cards?keyword=&psaGrade=&cursor=&size=20`

```json
{
  "items": [
    {
      "id": "card_01",
      "name": "피카츄 P 메가 에볼루션 프로모카드",
      "marketPrice": 138000,
      "changeRate": 2.7,
      "bidCount": 368,
      "psaGrade": 10,
      "language": "JP",
      "thumbnailUrl": "https://..."
    }
  ],
  "nextCursor": "opaque-cursor",
  "hasNext": true
}
```

### 카드 상세·시세 추이

- `GET /cards/{cardId}`
- `GET /cards/{cardId}/price-history?range=30D&interval=1D`
- `GET /cards/{cardId}/transactions?cursor=&size=20`

```json
{
  "cardId": "card_01",
  "range": "30D",
  "summary": {
    "currentPrice": 138000,
    "lowPrice": 124000,
    "highPrice": 149000,
    "averagePrice": 136500,
    "changeRate": 12.1,
    "tradeVolume": 368
  },
  "points": [
    {
      "at": "2026-07-24T00:00:00Z",
      "averageWinningPrice": 138000,
      "tradeVolume": 14
    }
  ]
}
```

## 4.3 경매 조회

### 경매 목록

`GET /auctions?keyword=&psaGrade=&sort=BID_COUNT&status=OPEN&cursor=&size=20`

정렬: `BID_COUNT | PRICE_HIGH | PRICE_LOW | CHANGE_HIGH`

```json
{
  "items": [
    {
      "id": "auc_01",
      "card": {
        "id": "card_01",
        "name": "피카츄 P 메가 에볼루션 프로모카드",
        "psaGrade": 10,
        "language": "JP",
        "thumbnailUrl": "https://..."
      },
      "seller": {
        "id": "usr_02",
        "nickname": "포켓컬렉터",
        "tradeCount": 128,
        "trustScore": 98
      },
      "startPrice": 42000,
      "currentPrice": 138000,
      "bidIncrement": 1000,
      "minimumBid": 139000,
      "bidCount": 368,
      "favorite": true,
      "favoriteCount": 2792,
      "startsAt": "2026-07-24T08:00:00Z",
      "endsAt": "2026-07-24T12:00:00Z",
      "status": "OPEN",
      "version": 31,
      "myBidStatus": "OUTBID",
      "myBidAmount": 132000
    }
  ],
  "nextCursor": "opaque-cursor",
  "hasNext": true
}
```

상태: `SCHEDULED | OPEN | ENDING | ENDED | CANCELLED | FAILED`

### 경매 상세

`GET /auctions/{auctionId}`

목록 필드에 아래 정보를 추가한다.

```json
{
  "description": "카드 상태와 보관 방법입니다.",
  "sellerMemo": "낙찰 확인 후 2영업일 이내 발송합니다.",
  "shippingFee": 3000,
  "buyNowPrice": 650000,
  "photos": [
    {
      "id": "img_01",
      "url": "https://...",
      "order": 0,
      "representative": true
    }
  ],
  "psaCertification": {
    "certificationNumber": "12345678",
    "grade": 10,
    "population": 1248,
    "verified": true
  }
}
```

### 찜

- `PUT /auctions/{auctionId}/favorite`
- `DELETE /auctions/{auctionId}/favorite`

응답:

```json
{
  "auctionId": "auc_01",
  "favorite": true,
  "favoriteCount": 2793
}
```

## 4.4 판매 등록 보조 API

### OCR 자동 입력

`POST /cards/ocr`

`multipart/form-data`, 필드명 `image`. PNG/JPG, 최대 10MB.

```json
{
  "cardName": "피카츄 AR 프로모 카드",
  "setName": "SV2D",
  "year": 2023,
  "cardNumber": "173/165",
  "language": "JP",
  "confidence": 0.98
}
```

### PSA 인증 조회

`GET /psa-certifications/{certificationNumber}`

인증번호는 숫자 8자리다.

```json
{
  "certificationNumber": "12345678",
  "verified": true,
  "cardName": "피카츄 AR 프로모 카드",
  "setName": "SV2D",
  "year": 2023,
  "cardNumber": "173/165",
  "language": "JP",
  "psaGrade": 10,
  "population": 1248
}
```

PSA 등급과 Population은 인증 응답으로만 입력하며 경매 등록 요청에서 임의 값을 신뢰하지 않는다.

### 이미지 업로드

`POST /uploads/images`

`multipart/form-data`, 필드명 `images`, 최대 8장, 장당 10MB.

```json
{
  "images": [
    {
      "id": "img_01",
      "uploadToken": "upl_01",
      "url": "https://...",
      "order": 0
    }
  ]
}
```

## 4.5 경매 등록

`POST /auctions`

필수 헤더: `Idempotency-Key`

```json
{
  "card": {
    "name": "피카츄 AR 프로모 카드",
    "setName": "SV2D",
    "year": 2023,
    "cardNumber": "173/165",
    "language": "JP",
    "gradeType": "PSA",
    "selfGrade": null,
    "psaCertificationNumber": "12345678"
  },
  "description": "카드 상태, 보관 방법, 흠집 및 특이사항",
  "sellerMemo": "구매자에게 전달할 추가 내용",
  "imageUploadTokens": ["upl_01", "upl_02"],
  "startPrice": 42000,
  "bidIncrement": 1000,
  "buyNowPrice": 65000,
  "durationHours": 12,
  "shippingFee": 3000
}
```

```json
{
  "id": "auc_01",
  "status": "OPEN",
  "startsAt": "2026-07-24T10:00:00Z",
  "endsAt": "2026-07-24T22:00:00Z",
  "version": 1
}
```

서버 검증:

- 카드명과 이미지 1장 이상, 설명 필수
- 이미지 최대 8장
- 시작가·호가 단위 `> 0`
- 즉시 구매가는 시작가보다 큼
- 경매 시간 `1~24시간`
- 배송비 `>= 0`
- 자체 평가: `MINT | NEAR_MINT | EXCELLENT | GOOD | FAIR | POOR`
- PSA 평가: 유효한 인증번호 필수, 등급/Population은 서버 조회값 사용

## 4.6 나의 대시보드

무한 스크롤을 위해 각 탭을 별도 cursor 목록으로 제공한다.

| Method | Endpoint | 화면 |
|---|---|---|
| `GET` | `/me/auctions/participating?keyword=&cursor=&size=20` | 참여 중인 경매 |
| `GET` | `/me/auctions/favorites?keyword=&cursor=&size=20` | 찜한 카드 |
| `GET` | `/me/auctions/won?keyword=&cursor=&size=20` | 최근 나의 낙찰 |

응답 아이템은 경매 목록 DTO를 재사용하고 낙찰 목록에는 다음 필드를 추가한다.

```json
{
  "winningPrice": 171000,
  "wonAt": "2026-07-24T12:00:00Z",
  "deliveryStatus": "PENDING"
}
```

배송 상태: `PENDING | REQUESTED | SHIPPING | DELIVERED`.

### 배송 신청

`POST /me/auctions/{auctionId}/delivery`

기본 배송지를 사용하거나 요청 본문에서 배송지를 덮어쓸 수 있다.

## 4.7 경매 마감 처리

스케줄러 처리:

```text
종료 대상 조회
→ 경매 단위 락/상태 조건부 갱신
→ 낙찰자 동결액 실제 차감
→ 비낙찰자 잔여 동결액 해제
→ 판매자 정산 예정 원장 생성
→ 낙찰 결과·카드 집계 반영
→ Outbox 이벤트 저장
→ ENDED 전환
```

- `auctionId + closeVersion`을 멱등 키로 사용한다.
- `OPEN/ENDING → ENDED` 조건부 갱신에 성공한 작업자만 후속 처리를 수행한다.
- 재실행 시 기존 낙찰·지갑 원장·집계 결과를 반환하고 중복 생성하지 않는다.
- 실패 경매는 `FAILED`로 바로 확정하지 말고 재시도 큐와 운영자 확인 대상으로 분리한다.

운영자용 API:

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/admin/auctions/close-failures` | 마감 실패 목록 |
| `POST` | `/admin/auctions/{id}/close/retry` | 멱등 재처리 |
| `POST` | `/admin/auctions/{id}/cancel` | 관리자 취소·환불 |

## 4.8 관측과 부하테스트 기준

내부 운영 엔드포인트:

- `/actuator/health`
- `/actuator/prometheus`
- `/actuator/metrics`

필수 메트릭:

| 메트릭 | 의미 |
|---|---|
| `bid_request_total{result}` | 입찰 성공·실패 코드 |
| `bid_transaction_duration_seconds` | 입찰 트랜잭션 지연 |
| `auction_lock_wait_seconds` | 경매 락 대기 |
| `wallet_invariant_violation_total` | 잔액 정합성 위반 |
| `auction_close_total{result}` | 마감 성공·재시도·실패 |
| `auction_close_lag_seconds` | 예정 종료와 실제 종료 차이 |
| `websocket_connections` | 연결 수 |
| `websocket_event_delivery_total{type,result}` | 이벤트 발행 결과 |
| `outbox_pending_count` | 미발행 이벤트 수 |
| `login_failure_total{result}` | 로그인 실패·잠금 |
| `refresh_token_reuse_total` | Refresh 재사용 탐지 |

모든 요청 로그에는 `traceId`, `userId`, `auctionId`, `idempotencyKey`를 가능한 범위에서 포함하되 토큰·비밀번호·전체 개인정보는 기록하지 않는다.

부하테스트 핵심 시나리오:

1. 한 경매에 동시 입찰 100~1,000건
2. 동일 사용자의 중복 입찰 재시도
3. 여러 경매에 동일 지갑으로 동시 입찰
4. 마감 직전 입찰과 스케줄러의 경합
5. 마감 작업 중 서버 재시작 후 재처리
6. WebSocket 다중 연결 상태에서 가격·상회 이벤트 전파
7. 동일 계정 로그인 실패 동시 요청

검증 불변식:

```text
지갑 총액 = 가용액 + 동결액
경매 최고 입찰가 = 유효 입찰 중 최대 금액
최고 입찰자는 정확히 한 명
종료된 경매의 낙찰·차감·정산 원장은 각각 최대 한 건
동일 Idempotency-Key의 비즈니스 결과는 항상 동일
```

---

# 5. 프론트엔드 연결 기준

현재 `auctionApi.ts`의 임시 응답 DTO는 `snake_case`와 page-number 응답을 사용한다. 이 명세의 목표 계약은 `camelCase`와 cursor 응답이므로 실제 연동 시 `auctionMapper`를 새 계약에 맞게 수정하거나, 팀 표준이 `snake_case`라면 본 문서의 JSON 필드명을 일괄 변환해야 한다. 두 형식을 혼용하지 않는다.

## TanStack Query Key

```ts
['session']
['wallet']
['home', 'overview']
['cards', 'list', filters]
['cards', 'detail', cardId]
['cards', 'price-history', cardId, range]
['auctions', 'list', filters]
['auctions', 'detail', auctionId]
['auctions', 'bid-context', auctionId]
['dashboard', 'participating', filters]
['dashboard', 'favorites', filters]
['dashboard', 'won', filters]
['notifications', filters]
['inquiries', filters]
['reports', filters]
```

## Mutation 후 캐시 갱신

| Mutation | 처리 |
|---|---|
| 로그인/로그아웃 | `session`, `wallet`, `dashboard` 초기화 |
| 입찰 | 응답값으로 상세·목록 캐시 즉시 갱신 |
| 찜 | 목록·상세·대시보드 찜 캐시 갱신 |
| 경매 등록 | 경매 목록 무효화 후 `/auction/{id}` 이동 |
| 문의/신고 등록 | 해당 목록 무효화 |
| WebSocket 이벤트 | `version`이 더 클 때만 캐시에 반영 |

프론트의 남은 시간은 `endsAt`을 기준으로 표시하되, 서버 응답의 `Date` 헤더로 클라이언트 시계 오차를 보정한다.

## 목업/API 전환

홈 화면도 다른 목록 화면과 동일하게 데이터 계층을 거친다.

```text
HomePage
→ homeQueries.overview()
→ fetchHomeOverview()
→ `localStorage.USE_MOCK_API === "true"`: `mockup-data.json.home`
→ `localStorage.USE_MOCK_API !== "true"`: 실제 홈 API
→ 실제 API 모드: `GET /api/home`
```

홈 컴포넌트는 인사이트 수치·문구·정렬 값, 시장 요약, 차트, Top 5 데이터를 직접 하드코딩하지 않는다.
