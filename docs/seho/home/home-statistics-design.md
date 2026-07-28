# Home 통계 API 설계

**목표:** 홈 화면에서 경매 인사이트, 최근 30일 경매가·입찰량, 전일 상승 카드 TOP5를 각각 조회한다.

**Architecture:** 진행 경매 인사이트와 종료 경매 통계는 `auctions`에서 집계하고, 카드 시세 등락은 `item_statistics`의 기준 시점별 최신 데이터를 비교한다. 조회 전용 서비스가 집계 결과를 프론트 응답 형태로 조립한다.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, MySQL, React Query, JUnit 5, Mockito, MockMvc

## 집계 기준

- 모든 날짜 경계는 `Asia/Seoul` 기준이다.
- 진행 경매는 `OPEN`, `ENDING` 상태다.
- 종료 경매는 `ENDED` 상태다.
- 인사이트는 카드가 아닌 경매 매물 단위로 계산한다.
- 종료 경매의 `current_price`를 최종 낙찰가로 사용한다.
- `close_time`을 실제 경매 종료 시각으로 사용한다.

## API

| Method | Path | 기능 |
|---|---|---|
| GET | `/api/home/insights` | 진행 경매 인사이트 |
| GET | `/api/home/market?days=30` | 종료 경매의 일별 가격·입찰 통계 |
| GET | `/api/home/top-gainers?limit=5` | 전일 대비 시세 상승 카드 |

### 경매 인사이트

`GET /api/home/insights`

- 경매가 상승
  - `current_price > start_price`인 진행 경매 수다.
  - `changeRate`는 대상 경매들의 시작가 대비 상승률 평균이다.
  - 경매 목록은 `CHANGE_HIGH`로 이동한다.
- 신규 입찰
  - `bid_count > 0`인 진행 경매 수다.
  - 경매 목록은 `BID_COUNT`로 이동한다.
- 프리미엄 경매
  - 진행 경매를 현재가로 정렬했을 때 상위 10%의 경매 수다.
  - 상위 10%는 올림하고 진행 경매가 있으면 최소 1건이다.
  - 경매 목록은 `PRICE_HIGH`로 이동한다.

같은 카드에 여러 경매가 있으면 각각 별도 경매로 집계한다. 따라서 프론트의 단위도 `종`이 아닌 `건`을 사용한다.

### 최근 30일 경매가·입찰량

`GET /api/home/market?days=30`

- 오늘을 포함한 최근 30일을 조회한다.
- 일별 평균 경매가는 해당 날짜에 종료된 경매의 `current_price` 평균이다.
- 일별 입찰량은 해당 날짜에 종료된 경매의 `bid_count` 합계다.
- 무거래일 가격은 직전 거래일 가격을 유지하고 입찰량은 0으로 반환한다.
- 기간 첫날 이전에도 거래가 없으면 가격을 0으로 시작한다.
- 누락된 날짜 없이 요청한 일수만큼의 포인트를 반환한다.

요약 응답은 현재 평균가, 1일·7일·기간 변화율, 기간 내 총 입찰 수를 포함한다. 비교 기준 가격이 0이면 변화율은 `0.00`이다.

### 전일 상승 TOP5

`GET /api/home/top-gainers?limit=5`

1. 어제 종료 시점 이전의 카드별 최신 통계를 조회한다.
2. 그제 종료 시점 이전의 카드별 최신 통계를 조회한다.
3. 가격은 `latest_price`, 값이 없으면 `avg_price`를 사용한다.
4. 두 가격이 모두 양수이고 전일 가격이 상승한 카드만 남긴다.
5. 상승률, 전일 가격 내림차순과 카드 ID 오름차순으로 정렬한다.

진행 경매 존재 여부는 순위 조건에 포함하지 않는다. 응답은 `auctionId` 없이 `cardId`를 제공하며 프론트는 `/cards/{cardId}`로 이동한다.

## 구현 구조

```text
home
├── controller/HomeController
├── service/HomeService
├── repository/HomeAuctionRepository
└── dto/HomeResponses
```

- Controller는 요청 파라미터 범위만 검증한다.
- Service는 `@Transactional(readOnly = true)`로 날짜 계산과 응답 조립을 담당한다.
- Repository는 projection 기반 `COUNT`, `AVG`, `SUM` 쿼리로 전체 엔티티 로딩을 피한다.
- `Clock`을 주입해 시간에 의존하는 로직을 고정된 시각으로 테스트한다.
- 카드 테마 계산은 카드 목록과 홈 TOP5가 같은 `CardTheme` 규칙을 사용한다.

## 인덱스

홈 집계 쿼리를 위해 다음 인덱스를 사용한다.

```sql
INDEX idx_auctions_status_close_time (status, close_time);
INDEX idx_auctions_status_current_price (status, current_price);
```

카드별 기준 시점 통계 조회에는 기존의
`item_statistics(item_id, statistics_date)` 유니크 인덱스를 사용한다.

## 테스트

- 진행 상태만 인사이트에 포함되는지 검증한다.
- 상승 경매, 입찰 경매, 상위 10% 경계를 검증한다.
- 종료 경매의 일별 평균가와 입찰 합계를 검증한다.
- 무거래일 가격 이월과 30개 시계열 포인트 생성을 검증한다.
- 1일·7일·기간 변화율과 0원 기준값을 검증한다.
- 기준 시점 이전 최신 카드 통계 선택과 TOP5 정렬을 검증한다.
- Controller의 JSON 계약과 파라미터 범위를 검증한다.

## 완료 조건

- 세 API가 독립적으로 호출되고 한 영역의 실패가 다른 영역을 막지 않는다.
- 인사이트는 진행 경매, 시장 통계는 종료 경매만 사용한다.
- TOP5는 전일과 전전일 기준 시점의 카드 시세를 비교한다.
- 프론트는 TOP5에서 카드 상세 화면으로 이동한다.

> 이 문서는 Codex의 도움을 받아 작성하였습니다.
