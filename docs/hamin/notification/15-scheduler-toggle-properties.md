# 스케줄러 on/off 프로퍼티 키를 application.yml에 명시

이슈 #366

## 배경

다음 4개 스케줄러는 `@ConditionalOnProperty(..., matchIfMissing = true)`로 on/off 토글이 이미 가능하지만, 관련 프로퍼티 키가 `application.yml`에 전혀 선언되어 있지 않고 코드 기본값(`true`)에만 의존하고 있음.

- `AuctionDeadlineScheduler` - `auction.deadline.scheduler.enabled`
- `AuctionClosingScheduler` - `auction.closing.scheduler.enabled`, `auction.closing.scheduler.fixed-delay-ms`
- `UrgentNotificationRecoveryScheduler` - `notification.recovery.urgent.enabled`, `notification.recovery.urgent.fixed-delay-ms`
- `AuctionResultNotificationRecoveryScheduler` - `notification.recovery.result.enabled`, `notification.recovery.result.fixed-delay-ms`

## 작업 내용

1. 위 프로퍼티 키를 `application.yml`에 기본값(현재 코드 기본값과 동일)과 함께 명시.
2. 기존 `application.yml` 전체가 `${ENV_VAR:default}` 패턴(예: `AUCTION_IMAGE_BUCKET`, `SESSION_TIMEOUT`)으로 환경변수 오버라이드를 지원하므로, 동일한 패턴으로 각 키에 환경변수를 매핑.

### 적용할 키/환경변수 매핑

```yaml
auction:
  deadline:
    scheduler:
      enabled: ${AUCTION_DEADLINE_SCHEDULER_ENABLED:true}
  closing:
    scheduler:
      enabled: ${AUCTION_CLOSING_SCHEDULER_ENABLED:true}
      fixed-delay-ms: ${AUCTION_CLOSING_SCHEDULER_FIXED_DELAY_MS:60000}

notification:
  recovery:
    urgent:
      enabled: ${NOTIFICATION_RECOVERY_URGENT_ENABLED:true}
      fixed-delay-ms: ${NOTIFICATION_RECOVERY_URGENT_FIXED_DELAY_MS:90000}
    result:
      enabled: ${NOTIFICATION_RECOVERY_RESULT_ENABLED:true}
      fixed-delay-ms: ${NOTIFICATION_RECOVERY_RESULT_FIXED_DELAY_MS:420000}
```

`AuctionDeadlineScheduler`에는 `fixed-delay-ms` 개념이 없음(이벤트 기반 1회성 스케줄이라 고정 주기가 없음) — `enabled` 키만 추가.

## 범위

- `application.yml` 수정만. 코드(스케줄러 클래스) 변경 없음 — 이미 프로퍼티를 읽도록 되어 있어서 키를 노출만 하면 됨.
- 테스트 영향 없음(기본값 동일하게 유지하므로 기존 스케줄러 동작 변화 없음).
