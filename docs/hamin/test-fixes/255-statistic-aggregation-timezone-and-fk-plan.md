# 이슈 255 — StatisticAggregationMySqlIntegrationTest 통계 집계 로직 및 FK 데이터 셋업 버그

담당: 임하민. 이슈: [#255](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/255)
(브랜치 `fix/255-statistic-aggregation-timezone-and-fk`).

`AGENTS.md`상 원래 담당 범위는 `notifications`/`psa`/`upload`/`wishlist`인데, 이번 작업
(`card`/`statistic` 패키지 테스트 코드만 수정)은 사용자가 채팅으로 "255 진행해"라고
명시적으로 지시해서 진행한다.

## 원인 A — Asia/Seoul ↔ UTC 타임존 불일치 (3건)

`DailyStatisticAggregationService.aggregate(date)`는 `date`를 Asia/Seoul 기준 하루로
해석해 UTC로 변환한 뒤 그 범위로 `close_time`을 조회한다. 그런데 테스트가 넣은
`close_time` 리터럴(`'2026-07-27 18:00:00'`)이 실제로 저장/조회되는 시점을 디버그
쿼리로 확인해보니, Hibernate 네이티브 쿼리의 `LocalDateTime` 파라미터 바인딩이
`hibernate.jdbc.time_zone=UTC` 설정과 별개로 **JVM 기본 타임존**의 영향을 받는다는
걸 확인했다:

- 순수 JDBC(`JdbcTemplate` 파라미터 바인딩)로 같은 범위를 조회하면 정상적으로 매칭됨
- `StatisticAggregationRepository`의 JPA 네이티브 쿼리(`@Query(nativeQuery = true)`)로
  같은 범위를 조회하면 매칭되지 않음
- 이 Mac의 JVM 기본 타임존은 `Asia/Seoul`이고, 실제 배포 환경은 `Dockerfile`에
  `ENV TZ=UTC` / `ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"`로 JVM 기본 타임존을
  UTC로 강제하고 있어 **프로덕션에서는 재현되지 않는** 로컬 전용 테스트 버그다

**수정**: 프로덕션 코드는 건드리지 않고, 테스트 클래스 실행 동안만 `@BeforeAll`/`@AfterAll`로
JVM 기본 타임존(`TimeZone.setDefault`)을 UTC로 고정해 개발자 로컬 머신의 타임존 설정과
무관하게 항상 같은 결과가 나오도록 했다.

## 원인 B — 존재하지 않는 item_id로 FK 위반 (1건)

`최근_30일의_최근_두_유효_거래를_native_query로_조회한다()`는 `item_daily_statistics`에
`item_id = 1`과 `item_id = 2`로 직접 row를 넣는데, `setUp()`에는 `card_metadata`에
`id = 1`짜리 카드만 있고 `id = 2`는 없어서 FK 위반이 났다. 원인 A와 무관한 별개의
테스트 데이터 셋업 누락이었다.

**수정**: 이 테스트 메서드 안에서만 `card_metadata`에 `id = 2` 카드를 추가로 생성하도록
했다(다른 테스트들은 카드가 1개만 있다고 가정하고 `item_daily_statistics` row 개수를
assert하므로, `setUp()`에 공통으로 추가하면 그 테스트들이 깨진다).

## 결과

- `StatisticAggregationMySqlIntegrationTest` 4개 테스트 전부 통과
- 전체 스위트 실행 결과 실패 0건 — 처음 발견한 9개 실패 테스트 클래스가 모두 해결됨
  ([#252](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/252),
  [#253](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/253),
  [#254](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/254), #255)

## 커밋 이력

1. `fix: 통계 집계 테스트의 타임존 의존성과 FK 위반 데이터 셋업 수정`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
