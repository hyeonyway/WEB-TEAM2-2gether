# 스케줄러 4종 ShedLock 적용 계획

이슈: [#542 perf: 멀티 인스턴스 배포 시 주기 스케줄러 4종의 중복 조회 (ShedLock 미적용)](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/542)

## 배경

멀티 인스턴스 배포 시 아래 4개 스케줄러가 인스턴스마다 독립적으로 실행되어 같은 DB/Redis
상태를 인스턴스 수만큼 중복 조회한다. 결과 정합성은 각자의 방식(DB 비관적 락, Redis Lua
원자성, INSERT IGNORE + 유니크 제약)으로 이미 보장되므로 버그는 아니지만, 조회 자체가 낭비다.

`DailyStatisticScheduler`가 이미 `@SchedulerLock`(ShedLock, JDBC 기반 `LockProvider`)으로
같은 문제를 해결해둔 전례가 있어 동일 패턴을 적용한다.

## 스코프

적용 대상:

| 클래스 | 메서드 | 현재 주기 |
|---|---|---|
| `AuctionClosingScheduler` | `closeDueAuctions()` | 60초 (`fixedDelayString`) |
| `RedisAuctionActiveIndexCleanupScheduler` | `removeTerminalEntries()` | 1시간 (`fixedDelayString`) |
| `NonUrgentNotificationRecoveryScheduler` | `recover()` | 5분 (`fixedDelayString`) |
| `UrgentNotificationRecoveryScheduler` | `recover()` | 90초 (`fixedDelayString`) |

스코프 제외 (이슈 #542에도 명시):
- `AuctionDeadlineScheduler` — `@Scheduled`가 아니라 `TaskScheduler.schedule(...)` 기반
  1회성 트리거라 어노테이션 방식이 바로 안 맞음. 별도 이슈로 분리.
- SSE 하트비트 3종(auction/notification/wallet) — 인스턴스-로컬 emitter만 다뤄서 인스턴스
  간 중복이 의미 없음.
- `AuctionClosingScheduler` ↔ `AuctionDeadlineScheduler` 간 인스턴스 내부 중복 — 별도 논의
  대상.

## 락 파라미터 설계

`DailyStatisticScheduler`와 동일하게 `lockAtLeastFor`/`lockAtMostFor`를 명시한다.
`lockAtLeastFor`는 극단적으로 빠르게 끝나는 실행(예: 처리할 항목이 없는 턴)이 반복 재획득으로
스래싱하는 걸 막는 최소 보유 시간, `lockAtMostFor`는 인스턴스가 언락 없이 죽었을 때의 안전판
(다음 실행 주기 대비 충분히 크게, 그러나 `@EnableSchedulerLock(defaultLockAtMostFor = PT30M)`
기본값보다는 각 잡의 실제 주기에 맞게 타이트하게).

| 락 이름 | lockAtLeastFor | lockAtMostFor | 근거 |
|---|---|---|---|
| `auction-closing-backup-scheduler` | PT10S | PT5M | 60초 주기, 배치 100건 처리 시간 여유 확보 |
| `auction-active-index-cleanup` | PT10S | PT10M | 1시간 주기, Lua GC라 실제로는 초 단위지만 여유 있게 |
| `notification-recovery-non-urgent` | PT10S | PT10M | 5분 주기 |
| `notification-recovery-urgent` | PT10S | PT2M | 90초 주기 |

## 테스트

기존 3개 스케줄러(`AuctionClosingSchedulerTest`,
`NonUrgentNotificationRecoverySchedulerTest`, `UrgentNotificationRecoverySchedulerTest`)는
Spring 컨텍스트 없이 순수 Mockito로 생성자 직접 호출하는 단위 테스트라 `@SchedulerLock`
어노테이션 추가에 영향받지 않는다(AOP 프록시가 없는 환경이라 annotation 자체는 아무 동작도
안 함) — 수정 불필요.

`DailyStatisticSchedulerShedLockTest`와 동일한 패턴으로, 각 스케줄러 클래스마다 reflection
기반으로 `@SchedulerLock`의 `name`/`lockAtLeastFor`/`lockAtMostFor` 값을 검증하는 테스트를
추가한다.

## 완료 조건

- 4개 메서드에 `@SchedulerLock` 적용
- 각 클래스별 ShedLock 어노테이션 검증 테스트 추가
- 기존 스케줄러 단위 테스트 그대로 통과
- 전체 스위트는 최종 통합 시 1회만 실행(작업 중에는 영향받는 테스트 클래스만)

---
이 문서는 claude의 도움을 받아 작성되었습니다.
