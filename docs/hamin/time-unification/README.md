# 서버 시간 처리 LocalDateTime → Instant 통일 (이슈 261~264)

서버 전체에서 `LocalDateTime`과 `Instant`가 혼용되어 실행 환경(로컬/운영/CI)에
따라 결과가 달라질 수 있는 문제를 정리하는 시리즈. `Instant`로 통일하고,
통계 집계의 일/30일 단위 경계는 Asia/Seoul 기준으로 계산하되 API로는 항상
`Instant`로 내려주는 방향으로 진행한다.

| 이슈 | 범위 | 상태 | 문서 |
|---|---|---|---|
| [#261](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/261) | Upload/Notification/Order/Wallet/Account Clock 주입 + Instant 전환 | 완료 | [계획 문서](261-clock-injection-and-instant-plan.md) |
| [#262](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/262) | Auction/Bid LocalDateTime → Instant 전환 | 완료 | [계획 문서](262-auction-bid-instant-plan.md) |
| [#263](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/263) | 통계 집계 경계 Instant 전환 | 완료 | [계획 문서](263-statistic-aggregation-instant-plan.md) |
| [#264](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/264) | 프론트 타임존 표시 정리 + 카운트다운 훅 통합 | 완료 | [계획 문서](264-frontend-timezone-countdown-plan.md) |

별도 이슈로 분리해 이번 시리즈에서 처리하지 않는 것:
[#265](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/265)(카운트다운 clock drift 보정),
[#266](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/266)(통계/포인트 엔티티 created_at/updated_at 매핑 누락).

## #262에서 발견한 부수 이슈

`Instant`를 Testcontainers `@ServiceConnection` 기반 테스트에서 JDBC
파라미터로 바인딩하면, `application.yml`의 `connectionTimeZone=UTC`가
테스트에는 적용되지 않아 JVM 기본 타임존이 쓰인다. `backend/build.gradle`의
`test` 태스크에 `systemProperty 'user.timezone', 'UTC'`를 추가해 운영과
동일한 조건으로 맞췄다. 자세한 내용은
[262 계획 문서](262-auction-bid-instant-plan.md)의 "예상 밖 발견" 참고.

## #263에서 정리한 것 — `UtcTime` 삭제

이슈를 처음 만들 때는 "전체 마이그레이션 후 `UtcTime` 헬퍼를 삭제한다"고
적었는데, `auction/sse/AuctionSseTestBidApplicationService`가 `Auction`
엔티티를 거치지 않는 raw JDBC 읽기 결과를 `UtcTime.toInstant()`로 변환하는
걸 계속 쓰고 있어서 한 번은 "그래서 삭제 불가"로 결론 냈었다. 다시 보니
그 호출 한 줄을 `.toInstant(ZoneOffset.UTC)` 인라인 변환으로 바꾸면
그만이라, 그렇게 바꾸고 `UtcTime`을 삭제했다. 자세한 내용은
[263 계획 문서](263-statistic-aggregation-instant-plan.md) 참고.

## #263 — 남은 `LocalDateTime` 전부 제거

사용자가 "native query라 의미 없어도 남은 LocalDateTime을 전부 Instant로
바꿔라"고 지시해서, `auction/sse/AuctionSseTestAuctionReader`(SSE 테스트용
raw JDBC 리더)와 `card/service/CardPriceServiceTest`의 native query
파라미터까지 전부 `Instant`로 바꿨다. 다만 MySQL Connector/J가
`resultSet.getObject(column, Instant.class)`를 지원하지 않는다는 걸 실제
Testcontainers MySQL로 확인해서(`SQLException: Conversion not supported
for type java.time.Instant`), JDBC 읽기 자체는 `LocalDateTime`으로 하고
그 자리에서 즉시 `.toInstant(ZoneOffset.UTC)`로 변환하는 방식으로
처리했다 — `Snapshot` 같은 타입 시그니처에는 `LocalDateTime`이 전혀
노출되지 않는다. 이제 `backend/src` 전체에서 실제 타입으로 쓰이는
`LocalDateTime`은 없다(주석 텍스트 한 곳 제외). 자세한 내용은
[263 계획 문서](263-statistic-aggregation-instant-plan.md) 참고.

## #263 테스트 중 발견한 이번 시리즈와 무관한 회귀

전체 스위트 실행 중 `WalletTransactionConcurrencyTest`가 실패하는 걸
발견했는데, `dev`에 이번 시리즈 변경 없이 그대로 돌려봐도 같은 실패가
나서 무관함을 확인했다. 방금 머지된 PR #267(`WalletHoldRepository`의
`@Lock` 제거)이 원인으로 추정되며 wallet 패키지 소관이라 별도로 플래그만
했다.

## #264 — 프론트엔드는 백엔드와 다른 종류의 정리가 필요했다

백엔드(#261~#263)는 "LocalDateTime을 Instant로" 한 방향이었지만, 프론트는
API 계약 변경이 없어서 타입 통일이 아니라 **"이미 두 가지 기준(Asia/Seoul
고정 vs 브라우저 로컬)이 혼재해 있는데, 그중 뭐가 맞는 기준인지 구분해서
통일"** 하는 작업이었다. 통계 차트처럼 백엔드가 집계한 "영업일"을 보여주는
값은 Asia/Seoul로 고정하는 게 맞고, 알림처럼 "언제 일어난 일"을 보여주는
값은 브라우저 로컬이 맞다 — 무조건 하나로 통일하면 오히려 틀린 값이 나온다.
자세한 내용은 [264 계획 문서](264-frontend-timezone-countdown-plan.md) 참고.

경매 카운트다운 중복 구현은 그리드/상세 페이지가 각자 쓰는 "공유 tick 훅 +
순수 함수"로 분리해서 통합했다(경매 카드마다 훅을 부르면 React Hooks 규칙
위반이라, 훅은 페이지당 한 번만 부르고 순수 함수는 필요한 곳마다 부르는
구조를 유지).

> 이 문서는 claude의 도움을 받아 작성하였습니다.
