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

## #263에서 확인한 것 — `UtcTime`은 삭제할 수 없다

이슈를 처음 만들 때는 "전체 마이그레이션 후 `UtcTime` 헬퍼를 삭제한다"고
적었는데, `auction/sse/AuctionSseTestBidApplicationService`가 `Auction`
엔티티를 거치지 않는 raw JDBC 읽기 결과를 `UtcTime.toInstant()`로 변환하는
걸 계속 쓰고 있어서 삭제 대상에서 제외했다. 자세한 내용은
[263 계획 문서](263-statistic-aggregation-instant-plan.md) 참고.

## #263 테스트 중 발견한 이번 시리즈와 무관한 회귀

전체 스위트 실행 중 `WalletTransactionConcurrencyTest`가 실패하는 걸
발견했는데, `dev`에 이번 시리즈 변경 없이 그대로 돌려봐도 같은 실패가
나서 무관함을 확인했다. 방금 머지된 PR #267(`WalletHoldRepository`의
`@Lock` 제거)이 원인으로 추정되며 wallet 패키지 소관이라 별도로 플래그만
했다.

> 이 문서는 claude의 도움을 받아 작성하였습니다.
