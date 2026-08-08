# 백엔드 테스트 실패 원인 분석/수정 (이슈 252~255)

`./gradlew test`를 Docker(Testcontainers) 켠 상태로 실행했을 때 드러난 실패 9개 클래스를
원인별로 나눠 하나씩 이슈화하고 고친 기록. 전부 테스트 코드만 수정했고, 프로덕션 코드
변경은 없다.

| 이슈 | 원인 | 문서 |
|---|---|---|
| [#252](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/252) | `auctions.version` 컬럼 제거 후 테스트 setup SQL 미갱신 (6개 클래스) | [계획 문서](252-auction-version-column-test-fix-plan.md) |
| [#253](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/253) | `CardPriceServiceTest`가 로컬 개발 DB 시드 데이터와 섞임 | [계획 문서](253-card-price-test-db-isolation-plan.md) |
| [#254](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/254) | 세션 SSE 연결 종료 도입 후 빈 등록/설정 누락 | [계획 문서](254-session-sse-registry-bean-missing-plan.md) |
| [#255](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/255) | 통계 집계 테스트의 타임존 의존성 + FK 데이터 셋업 누락 | [계획 문서](255-statistic-aggregation-timezone-and-fk-plan.md) |

> 이 문서는 claude의 도움을 받아 작성하였습니다.
