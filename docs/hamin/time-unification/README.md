# 서버 시간 처리 LocalDateTime → Instant 통일 (이슈 261~264)

서버 전체에서 `LocalDateTime`과 `Instant`가 혼용되어 실행 환경(로컬/운영/CI)에
따라 결과가 달라질 수 있는 문제를 정리하는 시리즈. `Instant`로 통일하고,
통계 집계의 일/30일 단위 경계는 Asia/Seoul 기준으로 계산하되 API로는 항상
`Instant`로 내려주는 방향으로 진행한다.

| 이슈 | 범위 | 상태 | 문서 |
|---|---|---|---|
| [#261](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/261) | Upload/Notification/Order/Wallet/Account Clock 주입 + Instant 전환 | 완료 | [계획 문서](261-clock-injection-and-instant-plan.md) |
| [#262](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/262) | Auction/Bid LocalDateTime → Instant 전환 | 완료 | [계획 문서](262-auction-bid-instant-plan.md) |
| [#263](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/263) | 통계 집계 경계 Instant 정리 + UtcTime 헬퍼 정리 | 미착수 | - |
| [#264](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/264) | 프론트 타임존 표시 정리 + 카운트다운 훅 통합 | 미착수 | - |

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

> 이 문서는 claude의 도움을 받아 작성하였습니다.
