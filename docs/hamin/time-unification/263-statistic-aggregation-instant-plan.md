# 이슈 263 — 통계 집계 경계 계산 Instant 전환

담당: 임하민. 이슈: [#263](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/263)
(브랜치 `refactor/263-statistic-aggregation-instant`).

`AGENTS.md`상 원래 담당 범위는 `notifications`/`psa`/`upload`/`wishlist`인데, #261/#262와
동일하게 사용자가 채팅에서 명시적으로 지시해서 `statistic` 패키지까지 진행한다.

## 이슈 생성 시점과 달라진 점

이슈를 처음 만들 때는 "#262에서 `Auction.closeTime`이 `Instant`로 바뀌면
`StatisticAggregationRepository`의 `LocalDateTime` 파라미터와 타입이 안 맞게 된다"고
적었는데, #262 작업 중 직접 확인해보니 **사실이 아니었다**:
`StatisticAggregationRepository.aggregateItems`/`aggregateMarket`은 native query라
`:from`/`:to` 파라미터가 `Auction` 엔티티의 Java 필드 타입과 무관하게 독립적으로
바인딩된다(엔티티 프로퍼티 경로를 안 타고 raw SQL에 직접 바인딩). 그래서 #262를 그대로
머지해도 이 리포지토리는 컴파일도, 동작도 깨지지 않는다.

또 하나 정정: 원래 "전체 마이그레이션 후 `UtcTime` 헬퍼를 삭제한다"고 적었는데, #262
조사 중 `auction/sse/AuctionSseTestBidApplicationService`가 `AuctionSseTestAuctionReader`로
raw JDBC를 통해 읽은 `LocalDateTime`을 `UtcTime.toInstant()`로 변환하는 걸 확인했다(이
리더는 `Auction` 엔티티를 거치지 않는 test-profile 전용 코드라 이번 마이그레이션과
무관하게 그대로 남는다). 즉 `UtcTime`은 이번 시리즈가 끝나도 **삭제할 수 없다** — 이
이슈에서는 삭제하지 않는다.

## 그럼 이 이슈에서 왜 바꾸나

타입이 안 맞아서 강제로 바꿔야 하는 건 아니지만, `DailyStatisticAggregationService`가
경계를 계산해서 `LocalDateTime`으로 넘기는 방식은 여전히 "이 `LocalDateTime`은 UTC
벽시계 값"이라는 암묵적 규약에 의존한다 — #261/#262로 없애려던 바로 그 종류의
모호함이다. `Instant`로 바꾸면 이 규약 의존이 없어지고, 계산 자체도 더 단순해진다
(`ZonedDateTime.toInstant()`는 애초에 타임존 무관하게 같은 절대 시점을 내므로
`.withZoneSameInstant(UTC)` 단계가 통째로 불필요해짐).

## Instant 네이티브 쿼리 바인딩과 JVM 타임존 — #262에서 배운 것

#262에서 `Instant` 파라미터를 파생(JPQL) 쿼리에 바인딩할 때 JVM 기본 타임존의 영향을
받는 걸 발견해 `backend/build.gradle`의 테스트 태스크에
`systemProperty 'user.timezone', 'UTC'`를 추가했다. 이번에 보니
`StatisticAggregationMySqlIntegrationTest`는 **native 쿼리 + `LocalDateTime`**
조합에서도 정확히 같은 현상이 있어(이미 이슈 #255에서 발견되어 `@BeforeAll`/`@AfterAll`로
JVM 타임존을 UTC로 고정해둔 상태) — 즉 이건 `Instant`만의 문제가 아니라
"Hibernate native query 파라미터 바인딩이 `hibernate.jdbc.time_zone`과 무관하게 JVM
기본 타임존을 탄다"는 더 넓은 현상이다. 이 테스트는 이미 스스로 보호되어 있고,
`build.gradle`의 전역 수정도 있어 이중으로 안전하다. `Instant`로 바꿔도 새로운 리스크가
아니다.

## 변경 범위

- `statistic/repository/StatisticAggregationRepository.java` — `aggregateItems`/
  `aggregateMarket`의 `from`/`to`: `LocalDateTime` → `Instant`
- `statistic/service/DailyStatisticAggregationService.java` — `fromUtc`/`toUtc` 계산을
  `Instant`로, `UTC` `ZoneId` 상수와 `.withZoneSameInstant(UTC).toLocalDateTime()` 단계
  제거하고 `date.atStartOfDay(SEOUL).toInstant()`로 단순화
- `card.service.DailyStatisticAggregationServiceTest` — mock 검증 리터럴을 `Instant`로

## 변경하지 않는 것

- `global/time/UtcTime.java` — 위에서 정리한 대로 `AuctionSseTestBidApplicationService`가
  여전히 써서 삭제 불가
- `StatisticAggregationMySqlIntegrationTest` — raw SQL 문자열 리터럴로 데이터를 넣고
  `aggregate(LocalDate)` 시그니처만 호출하는 테스트라 내부 타입 변경과 무관, 수정 불필요
- `refreshRollingSnapshots`/`refreshChangeRates`, `StatisticQueryService`,
  `DailyStatisticScheduler` — `LocalDate`만 다뤄서 대상 아님
- `AuctionInsightQueryRepository`의 `CURRENT_TIMESTAMP` JPQL 비교 — 바인딩 파라미터가
  없어 무관

## 결과

- `compileJava`/`compileTestJava` 통과
- `DailyStatisticAggregationServiceTest`(mock), `StatisticAggregationMySqlIntegrationTest`
  (실제 MySQL), `DailyStatisticSchedulerTest` 전부 통과 — native query에 `Instant`를
  바인딩해도 실제 MySQL에서 정상 동작함을 확인
- 전체 스위트 실행 중 `WalletTransactionConcurrencyTest`의
  `같은_경매의_동시_hold는_HELD를_중복_생성하지_않는다()` 실패를 발견했으나, `dev`에
  이번 변경 없이 그대로 체크아웃해서 돌려봐도 동일하게 실패함을 확인해 **이번 이슈와
  무관함**을 확인했다. 원인은 방금 머지된 PR #267(`WalletHoldRepository`의
  `@Lock(PESSIMISTIC_WRITE)` 제거)로 추정되며, wallet 패키지 소관이라 별도로
  플래그만 하고 이 PR에는 포함하지 않는다.

## 커밋 이력

1. `refactor: 통계 집계 경계 계산을 Instant로 전환`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
