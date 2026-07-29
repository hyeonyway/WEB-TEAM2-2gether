# 홈 화면 통계 API 설계

**목표:** 홈 화면에서 경매 인사이트, 최근 30일 경매가·입찰량, 최근 가격 대비 상승·하락 카드 TOP5를 각각 조회한다.

**Architecture:** 진행 경매 인사이트만 `auctions`에서 실시간 집계한다. 완료된 시장과 카드 시세는 일간 확정 통계와 최신 30일 요약 통계로 분리하고, 화면 API는 통계 테이블만 조회한다.

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
| GET | `/api/home/price-movers?limit=5` | 30일 내 최근 가격 대비 상승·하락 카드 |
| GET | `/api/home/top-gainers?limit=5` | 상승 목록 호환 API |

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

- 오늘을 제외하고 `오늘-30일`부터 어제까지 조회한다.
- 일별 평균 경매가는 해당 날짜에 종료된 경매의 `current_price` 평균이다.
- 일별 입찰량은 해당 날짜에 종료된 경매에 연결된 실제 `bids` 행의 합계다.
- 무거래일 가격은 직전 거래일 가격을 유지하고 입찰량은 0으로 반환한다.
- 기간 첫날 이전에도 거래가 없으면 가격을 0으로 시작한다.
- 누락된 날짜 없이 요청한 일수만큼의 포인트를 반환한다.

요약 응답은 최근 30일 낙찰가 총합, 낙찰 카드 수, 총 입찰 수와 최고 낙찰가를
포함한다. 이 값들은 어제 기준 시장 통계 행에 30일 누적
필드로 저장하며 홈 API는 별도 재집계 없이 조회한다.

### 최근 가격 변동 TOP5

`GET /api/home/price-movers?limit=5`

1. `item_daily_statistics`에서 오늘을 제외한 최근 30일의 카드별 유효 가격을 조회한다.
2. 날짜가 가장 최근인 두 거래 가격을 비교한다.
3. 가격은 `latest_price`, 값이 없으면 `average_price`를 사용한다.
4. 양수 변동은 `gainers`, 음수 변동은 `losers`에 담는다.
5. 상승은 변동률 내림차순, 하락은 변동률 오름차순으로 정렬한다.

응답은 비교 날짜, 최근 거래일 입찰 수, 카드 이미지와 30일 가격 이력을 제공한다.
기존 `/top-gainers`는 배포 호환을 위해 새 응답의 `gainers`를 기존 형태로 감싸 반환한다.

## 통계 스키마와 구현 구조

- `item_daily_statistics`: 카드별 날짜의 가격·입찰량·종료 경매 수
- `item_statistics`: 카드당 한 행인 최신 30일 평균·최저·최고·입찰 누계
- `market_daily_statistics`: 홈 시장 그래프용 전체 종료 경매 일간 통계

카드 목록과 상세 요약은 `item_statistics`, 카드 상세 그래프와 TOP5는
`item_daily_statistics`, 홈 시장 그래프는 `market_daily_statistics`를 조회한다.
거래가 없는 그래프 날짜는 서비스에서 가격을 이월하고 입찰량을 0으로 채운다.

```text
home
├── controller/HomeController
├── service/HomeService
├── repository/HomeAuctionRepository
├── repository/MarketDailyStatisticRepository
└── dto/HomeResponses
```

- Controller는 요청 파라미터 범위만 검증한다.
- Service는 `@Transactional(readOnly = true)`로 날짜 계산과 응답 조립을 담당한다.
- 서버 시작 시와 매일 서울 시간 00:10에 마지막 시장 통계 다음 날부터
  어제까지 누락 날짜를 순서대로 보충한다. 날짜별 집계는 독립 트랜잭션이다.
- 통계 테이블이 비어 있으면 어제만 집계하며 거래가 없는 날도 0건 시장 행을 만든다.
- 특정 날짜가 실패하면 즉시 중단하고 다음 실행에서 그 날짜부터 다시 시도한다.
- 동일 날짜 집계를 재실행해도 행이 증가하지 않는다.
- `Clock`을 주입해 시간에 의존하는 로직을 고정된 시각으로 테스트한다.
- 카드 테마 계산은 카드 목록과 홈 TOP5가 같은 `CardTheme` 규칙을 사용한다.

## 인덱스

홈 집계 쿼리를 위해 다음 인덱스를 사용한다.

```sql
INDEX idx_auctions_status_close_time (status, close_time);
INDEX idx_auctions_status_current_price (status, current_price);
```

카드 일간 통계에는 `UNIQUE(item_id, statistics_date)`와
`INDEX(statistics_date, item_id)`를 사용한다. 최신 요약은 `item_id`가 기본 키다.

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
