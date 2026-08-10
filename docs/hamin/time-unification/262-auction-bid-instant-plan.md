# 이슈 262 — Auction/Bid 시간 필드 LocalDateTime → Instant 전환

담당: 임하민. 이슈: [#262](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/262)
(브랜치 `refactor/262-auction-bid-instant`).

`AGENTS.md`상 원래 담당 범위는 `notifications`/`psa`/`upload`/`wishlist`인데, #261과 동일하게
사용자가 채팅에서 명시적으로 지시해서 `auction`/`dashboard` 패키지까지 진행한다.

## 배경

#261에서 처리한 4개 패키지보다 훨씬 큰 범위다. `Auction`/`Bid`는 `@CreationTimestamp` 같은
자동 생성 필드가 아니라, 서비스가 직접 `LocalDateTime` 값을 만들어 생성자/메서드 인자로
넘기는 구조라 엔티티 타입을 바꾸면 호출부 전체가 같이 바뀐다. 조사 결과 예상보다 훨씬 넓게
퍼져있다(이벤트, SSE payload, 리포지토리 파라미터, 커서 인코딩, 대시보드, 알림 복구
배치까지). 스키마(`TIMESTAMP(6)`)는 그대로 두고 Java 타입만 바꾼다.

## 변경 범위

**엔티티**: `Auction`(openTime/estimatedCloseTime/closeTime, closeWithWinningBid/
closeWithoutTrade/extendCloseTimeIfNeeded/placeBid 파라미터), `Bid`(createdAt,
leading()/생성자 파라미터).

**이벤트**: `AuctionOpenedEvent`/`AuctionClosedEvent`/`BidPlacedEvent`(closeTime,
occurredAt), `AuctionCloseScheduleChangedEvent`(closeTime).

**DTO**: `AuctionResponses.DashboardAuction`(estimatedCloseTime/closeTime — 나머지
필드는 이미 Instant), `AuctionCloseResponse`(closedAt, 내부용이라 API 계약 영향 없음),
`AuctionCursor`(timeValue), `AuctionCursorCodec`(버전 `v3`→`v4`로 올려 옛 포맷 커서를
명시적으로 거부).

**리포지토리**: `AuctionRepository`의 `searchByCursor`(openTimeCursor/now),
`countByItemIdAndStatusInAndCloseTimeAfter`, `findCloseTargetsForUpdate`(now),
`findByStatusInAndOpenTimeGreaterThanEqual`, `findByStatusInAndCloseTimeGreaterThanEqual`.

**서비스/스케줄러**: `AuctionCommandService`(now() 헬퍼가 `clock.instant()` 반환,
`UtcTime.toInstant()` 경계 변환 3곳 제거), `AuctionQueryService`(Clock 미주입이던
`LocalDateTime.now()` 수정 + Clock 필드 추가), `AuctionDeadlineScheduler`(`closeTime`이
이미 Instant가 되므로 `scheduledCloseTime.atZone(zone()).toInstant()` 변환이 통째로
불필요해져 제거, 그 결과 `zone()`/`ZoneId` 임포트도 죽은 코드가 되어 함께 삭제),
`AuctionClosingScheduler`, `CardAuctionAdapter`(card 패키지, `Instant.now()` 대신
`Clock` 사용 지점).

**범위 밖에서 직접 영향받는 곳** (Auction 타입 변경의 직접적 결과, 선택 아님):
- `notification/recovery/NotificationReconciliationService` — `AuctionRepository`의
  `findByStatusInAndOpenTimeGreaterThanEqual`/`findByStatusInAndCloseTimeGreaterThanEqual`를
  호출하는 3개 public 메서드의 `windowStart` 파라미터
- `notification/recovery/scheduler/AuctionResultNotificationRecoveryScheduler`,
  `UrgentNotificationRecoveryScheduler` — 위 메서드에 넘기는 `now`/`windowStart`
- `dashboard/DashboardService` — `AuctionResponses.DashboardAuction`의 타입 변경을
  그대로 받아씀, `UtcTime.toInstant()` 래핑 제거

**변경하지 않는 것**:
- `statistic/repository/StatisticAggregationRepository`, `statistic/service/
  DailyStatisticAggregationService` — native query 파라미터라 `Auction` 엔티티
  Java 타입과 무관하게 독립적으로 동작. #263에서 별도로 다룸.
- `auction/sse/AuctionSseTestAuctionReader`/`AuctionSseTestBidApplicationService` —
  `Auction` 엔티티를 거치지 않고 raw JDBC로 직접 읽는 test-profile 전용 코드라
  `LocalDateTime` 그대로 유지, `UtcTime.toInstant()`로 경계에서 변환하는 기존 패턴도
  유지(이 파일이 `UtcTime`의 마지막 소비자가 되므로, #263에서 "UtcTime 전체 제거"는
  재검토 필요).
- `auction/repository/AuctionInsightQueryRepository` — `CURRENT_TIMESTAMP` JPQL
  함수 비교라 바인딩 파라미터가 없어 영향 없음.

## 주의할 기술적 함정

`Instant`는 `LocalDateTime`의 `.plusHours()`/`.minusMinutes()`/`.plusDays()` 같은
캘린더 단위 편의 메서드가 없다(`.plusSeconds()`/`.minusSeconds()`는 있음). 전부
`Duration` 기반으로 바꿔야 한다(예: `closeTime.minusHours(1)` →
`closeTime.minus(Duration.ofHours(1))`). 누락되면 컴파일 에러로 바로 드러나므로
컴파일 반복 실행으로 잡는다.

## 결과

계획대로 진행했고, 컴파일은 첫 시도에 전부 통과했다(사전에 영향 범위를 꼼꼼히
추적한 덕). 다만 전체 테스트 스위트를 돌렸을 때 예상 못한 실패가 하나 나왔다.

### 예상 밖 발견 — 테스트에서만 드러나는 Instant/JVM 타임존 문제

`CardPriceServiceTest`(card 패키지, 이번 이슈 범위 밖)의
`상세는_최근_30일_통계와_요약값을_반환한다()`가 "activeAuctionCount expected 2 but
was 0"으로 실패했다. `CardAuctionAdapter.countActiveAuctions()`가 호출하는
`countByItemIdAndStatusInAndCloseTimeAfter`에 `Instant` 파라미터를 넘기도록
바꾼 게 원인이었다.

`InstantBindingDebugTest`라는 임시 스크래치 테스트로 직접 파고든 결과:
`@Testcontainers`/`@ServiceConnection`을 쓰는 테스트는 Spring Boot가
컨테이너로부터 JDBC URL을 자체적으로 구성하기 때문에, `application.yml`에
수동으로 넣어둔 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`가
**테스트에는 전혀 적용되지 않는다.** `hibernate.jdbc.time_zone=UTC`도 이
문제를 못 막는다(`hibernate.timezone.default_storage=NORMALIZE_UTC`로도
재현 확인, 해결 안 됨) — `Instant`를 타임존 없는 `TIMESTAMP` 컬럼에 바인딩할
때 최종적으로 JVM 기본 타임존(이 Mac은 `Asia/Seoul`)이 쓰인다.

`LocalDateTime`은 원래 타임존 변환이 필요 없는 값이라 이 문제 자체가 존재하지
않았다. `Instant`로 바꾸면서 처음으로 드러난 것이다. 같은 이유로 `Auction`을
`auctionRepository.save()`(Hibernate 경유)로 넣는 다른 테스트들
(`AuctionRepositoryWindowQueryTest` 등)은 쓰기/읽기가 같은 방향으로
치우쳐 있어 상대 비교가 우연히 맞아떨어져 통과했었고, `CardPriceServiceTest`만
raw native SQL로 `LocalDateTime` 리터럴을 직접 넣는 방식이라 어긋남이
드러났다.

운영 환경은 `Dockerfile`의 `TZ=UTC`/`-Duser.timezone=UTC`로 이미 보호되어
영향이 없지만, 테스트 JVM은 그 보호를 받지 못했다. `backend/build.gradle`의
`test` 태스크에 `systemProperty 'user.timezone', 'UTC'`를 추가해 운영과
동일하게 맞췄다(디버깅용 `InstantBindingDebugTest`는 원인 확인 후 삭제,
커밋에 포함되지 않음).

### 최종 확인

- `compileJava`/`compileTestJava` 첫 시도에 통과
- 이슈 262로 새로 수정한 테스트 21개 클래스 전부 통과
- **전체 스위트(`./gradlew test`) 449개 테스트 전부 통과, 실패/에러 0건**

## 커밋 이력

1. `refactor: Auction/Bid 도메인 시간 필드를 Instant로 전환`
2. `refactor: Auction 이벤트/DTO/리포지토리/커서 코덱을 Instant로 전환`
3. `refactor: Auction 서비스/스케줄러의 Clock 사용과 Instant 전환 정리`
4. `refactor: 알림 복구 배치와 대시보드가 받는 Instant 변경 반영`
5. `test: Instant 전환에 맞춰 Auction/Bid 관련 테스트 수정`
6. `fix: 테스트에서 Instant가 JVM 기본 타임존을 타 검증이 로컬 환경에 좌우되는 문제 수정`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
