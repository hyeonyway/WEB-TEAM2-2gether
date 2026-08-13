# 알림 복구 배치 INSERT IGNORE 데드락 재시도

이슈 #429.

## 배경

`UrgentNotificationRecoveryScheduler`(90초, ENDING 상회입찰 전용)와 `NonUrgentNotificationRecoveryScheduler`(5분, OPEN+ENDING 상회입찰 등)가 둘 다 `notification` 테이블에 벌크 `INSERT IGNORE`를 실행하는데, 겹치는 대상이 두 갈래로 존재한다.

1. `recoverOpenOutbidNotifications`(non-urgent)가 `bids.status=LEADING` 기준으로 후보를 뽑을 때 ENDING 경매도 구조적으로 포함한다([[20-nonurgent-outbid-no-join-plan]], #416에서 의도적으로 `auctions` join을 제거한 결과).
2. `recoverOutbidNotificationsForCandidates`는 urgent/non-urgent 양쪽에서 공통으로 호출되며, 최근 종료된 경매(`CLOSED_STATUSES`, `recentlyClosed`)를 후보에 추가하는 로직이 join 여부와 무관하게 항상 실행된다. urgent의 windowStart(10분)와 non-urgent의 windowStart(15분)가 겹치므로 같은 최근 종료 경매를 양쪽이 동시에 후보로 잡을 수 있다.

두 스케줄러가 겹치는 `(user_id, auction_id, type, bid_id)` 유니크 키 range에 대해 서로 다른 순서로 gap lock을 잡으면서 `CannotAcquireLockException`(데드락)이 발생한다. `INSERT IGNORE`(#414)의 멱등성은 최종 데이터 정합성만 보장할 뿐 동시 실행 시의 락 경합 자체는 막지 못한다.

`auctions` join을 복원해 겹침 빈도를 줄이는 대안도 검토했으나, join 비용은 5분마다(하루 288회) 무조건 발생하는 반면 데드락은 두 스케줄러 주기가 우연히 겹칠 때만 드물게 발생해 비효율적이고, 원인 2번(`recentlyClosed` 겹침)은 join과 무관하게 남아 데드락을 완전히 없애지 못한다. 따라서 겹침 구조는 그대로 두고(성능 이점 + #416이 의도한 non-urgent 백업 백스톱 유지) 데드락 발생 시에만 비용을 지불하는 재시도로 대응한다.

## 재시도 위치 — 중요한 제약

MySQL은 데드락 희생 트랜잭션을 **전체 롤백**한다(문제의 SQL 문 하나만이 아니라). 따라서 재시도는 반드시 **새 트랜잭션**으로 다시 시도해야 하며, 이미 롤백된 트랜잭션 안에서 재시도하는 건 무효하다.

처음에는 `NotificationService` 내부에 재시도를 넣으려 했으나 두 가지 이유로 폐기했다:

- `insertRowsIgnoringDuplicates`(private)를 감싸는 재시도 루프를 `insertAllIgnoringDuplicates`(`@Transactional`) 안에 그대로 두면, 프록시가 트랜잭션을 한 번만 열기 때문에 재시도도 같은(이미 롤백된) 트랜잭션 안에서 도는 셈이라 위 제약을 위반한다.
- 이를 피하려고 실제 INSERT를 `@Transactional(REQUIRES_NEW)`인 별도 빈(`NotificationBulkInsertWriter`)으로 분리해 시도했으나, `NotificationServiceBulkInsertTest`(`@DataJpaTest`, 테스트당 트랜잭션 롤백)에서 실제로 실행해보니 **행 잠금 대기로 테스트가 무한정 멈추는 문제**가 재현됐다. 원인: 테스트 메서드 앞부분에서 `save()`로 같은 유니크 키의 알림 행을 커밋 없이(테스트 트랜잭션 안에서) 먼저 만들고, 뒤이어 `saveAllIgnoringDuplicates`가 REQUIRES_NEW로 같은 키에 `INSERT IGNORE`를 시도하면서 아직 안 끝난(그래서 안 풀린) 테스트 트랜잭션의 락을 기다리게 된다 — 그 테스트 트랜잭션은 이 호출이 끝나야 종료되므로 자기 자신을 기다리는 데드락/행-락 대기가 발생한다. `mysql> SHOW PROCESSLIST`로 확인 완료(`INSERT IGNORE ...`가 `update` 상태로 멈춰 있고, 앞선 커넥션이 `Sleep` 상태로 잠금을 쥐고 있음).

그래서 최종적으로는 **재시도 루프를 `NotificationReconciliationService`(호출자) 쪽에 둔다.** `notificationService.insertAllIgnoringDuplicates(rows)` 자체가 이미 (a) 서로 다른 빈 간의 프록시 경유 호출이라 자체-호출(self-invocation) 문제가 없고, (b) `insertAllIgnoringDuplicates`가 감싼 여러 청크 전체가 이미 하나의 논리적 재시도 단위이며, (c) `INSERT IGNORE`의 멱등성 덕분에 이미 커밋된 청크를 포함해 전체를 다시 실행해도 안전하다. 즉 "데드락 나면 트랜잭션 전체를 처음부터 다시 시작한다"는 표준적인 해법을 그대로 적용한 것이고, `NotificationService`의 트랜잭션 구조는 전혀 바꾸지 않는다.

## 변경

프로젝트 컨벤션상 Spring Batch 등 무거운 프레임워크를 새로 들이지 않고 필요한 부분을 직접 구현하는 편이라(AGENTS.md), Spring Retry 의존성 추가 없이 수동 재시도 루프로 구현한다.

### `NotificationReconciliationService`

```java
private static final int DEADLOCK_MAX_ATTEMPTS = 3;
private static final long DEADLOCK_RETRY_BACKOFF_MS = 100;

private void insertAllIgnoringDuplicatesWithDeadlockRetry(List<NotificationInsertRow> rows) {
    for (int attempt = 1; ; attempt++) {
        try {
            notificationService.insertAllIgnoringDuplicates(rows);
            return;
        } catch (CannotAcquireLockException exception) {
            if (attempt >= DEADLOCK_MAX_ATTEMPTS) {
                throw exception;
            }
            log.warn("event=notification.recovery.insert.deadlock.retry attempt={}", attempt, exception);
            sleepBeforeRetry(DEADLOCK_RETRY_BACKOFF_MS * attempt);
        }
    }
}
```

- `recoverAuctionOpenedNotifications`, `recoverAuctionClosedNotifications`, `recoverOutbidNotificationsForCandidates` 세 호출부 모두 `notificationService.insertAllIgnoringDuplicates(rows)` 직접 호출을 이 헬퍼 호출로 교체.
- 대상 예외는 `org.springframework.dao.CannotAcquireLockException`(MySQL 데드락이 이 타입으로 변환됨, 로그에서 확인된 그대로).
- 최대 3회 시도, 시도 간 100ms/200ms 선형 backoff — urgent 스케줄러 주기(90초)에 비해 무시할 수 있는 지연.
- 3회 모두 실패하면 기존과 동일하게 예외를 그대로 던져 스케줄러 로그(`event=notification.recovery.*.failed`)로 남고 다음 주기에서 재수렴.
- 라이브 이벤트 경로(`NotificationEventListener` → `saveAllIgnoringDuplicates`)는 이번 이슈의 데드락 재현 경로(urgent/non-urgent 겹침)와 무관해 스코프에서 제외.

## 테스트

- `NotificationReconciliationServiceTest`: `notificationService.insertAllIgnoringDuplicates`가 `CannotAcquireLockException`을 N회 던지다 성공하는 경우 재시도 후 정상 종료되는 케이스, `DEADLOCK_MAX_ATTEMPTS`를 다 채우고도 실패하면 예외가 그대로 전파되는 케이스, 데드락이 아닌 다른 예외는 재시도 없이 즉시 전파되는 케이스를 추가.
- 기존 스케줄러/서비스 테스트는 변경 없음(`NotificationService`는 손대지 않음, 시그니처 불변).

## 구현 완료

- `NotificationReconciliationService`에 데드락 재시도 헬퍼 추가, 3개 호출부 교체.
- `NotificationReconciliationServiceTest`에 재시도 성공/소진/비데드락-즉시전파 테스트 3건 추가, 기존 15건 포함 18건 통과 확인.
- (폐기) `NotificationBulkInsertWriter`(REQUIRES_NEW 빈) 방식은 시도 후 되돌림 — 위 "재시도 위치" 절 참고.
