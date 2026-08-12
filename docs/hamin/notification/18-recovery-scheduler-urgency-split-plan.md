# 알림 복구 스케줄러 긴급도 재조정 (이슈 #373)

담당: D(임하민). [6-notification-recovery-batch.md](6-notification-recovery-batch.md) 결정 5에서 나눈 긴급/비긴급 2-스케줄러 구조를 더 정확한 기준으로 재조정하는 라운드.

## 배경

이슈 #373 본문의 문제 제기: `UrgentNotificationRecoveryScheduler`(1분 30초 주기)가 상회입찰 복구(`recoverOutbidNotifications`)를 경매 상태(`OPEN`/`ENDING`)와 무관하게 매 사이클 돌린다. 결정 5가 "앤티스나이핑 자동 연장 기준(5분) 안에만 복구되면 재입찰 시간이 확보된다"는 근거로 상회입찰을 긴급 그룹에 넣었는데, 이 5분 연장은 실제로는 `ENDING` 상태에만 해당하는 얘기라 `OPEN`(마감 여유 있음)까지 급하게 돌 필요가 없다.

이슈 본문은 "경매 생성 복구는 범위 밖"이라고 명시했지만, 실제 작업 지시(대화)에서 범위가 넓어졌다 — 최종 확정 범위는 아래와 같다:

- **ENDING 경매의 상회입찰 복구만** 긴급 유지: 1분 30초.
- **나머지 전부**(경매 생성 복구, 경매 종료 복구, OPEN 경매의 상회입찰 복구)를 5분 주기 하나로 통일.

이슈 본문 대비 확장된 부분(경매 생성 복구 포함)은 이슈에도 반영해둔다.

## 확정 설계

### 1. `BidRepository`에 경매 상태로 거르는 조회 추가

기존 `findAuctionIdsByStatus(BidStatus)`는 경매 상태를 모른 채 LEADING bid의 auctionId만 반환한다. ENDING/OPEN을 나누려면 auction과 join해서 걸러야 한다.

```java
@Query("select b.auction.id from Bid b where b.status = :bidStatus and b.auction.status in :auctionStatuses")
List<Integer> findAuctionIdsByStatusAndAuctionStatusIn(
        @Param("bidStatus") BidStatus bidStatus,
        @Param("auctionStatuses") Collection<AuctionStatus> auctionStatuses
);
```

기존 `findAuctionIdsByStatus`는 이 메서드로 완전히 대체되어 다른 호출처가 없으므로 제거한다(`BidRepositoryTest`의 해당 테스트도 신규 메서드 기준으로 교체).

### 2. `NotificationReconciliationService.recoverOutbidNotifications`에 상태 파라미터 추가

```java
public void recoverOutbidNotifications(Instant windowStart, Collection<AuctionStatus> activeAuctionStatuses)
```

- LEADING bid 후보 조회를 `findAuctionIdsByStatusAndAuctionStatusIn(LEADING, activeAuctionStatuses)`로 교체.
- "최근 종료된 경매도 후보에 합친다"는 종료 경계 캐치(결정 3의 버그 수정)는 **`activeAuctionStatuses`와 무관하게 그대로 유지**한다. 종료 직전 경매가 `ENDING`을 거치지 않고 `OPEN`에서 바로 닫히는 경우도 있어서(앤티스나이핑 연장이 한 번도 안 걸리면 `OPEN` 그대로 마감), 이 캐치를 ENDING 전용으로만 남기면 OPEN 쪽에서 종료 경계 유실 버그가 재발한다. 두 호출(ENDING/OPEN) 모두 같은 종료 경매 캐치를 도니 약간의 쿼리 중복이 생기지만, `alreadyNotified` 체크가 멱등이라 중복 알림은 안 나가고 비용도 미미하다.

### 3. 스케줄러 재배치 (기존 2개 클래스 유지, 담당 메서드만 이동)

새 스케줄러 클래스를 추가하거나 이름을 바꾸지 않는다 — 클래스/프로퍼티 키 이름 변경은 배포 환경의 `.env` 오버라이드(`NOTIFICATION_RECOVERY_URGENT_*`, `NOTIFICATION_RECOVERY_RESULT_*`, `application.yml` 참고: [15-scheduler-toggle-properties.md](15-scheduler-toggle-properties.md))를 조용히 무력화할 위험이 있고, 이번 지시는 "주기/그룹 재배치"이지 "이름 재정비"가 아니라서 범위를 넘는다고 판단했다. 대신 각 클래스의 책임과 Javadoc만 갱신한다.

- **`UrgentNotificationRecoveryScheduler`** (`notification.recovery.urgent.fixed-delay-ms`, 기본 90000ms 유지)
  - `recoverOutbidNotifications(windowStart, List.of(AuctionStatus.ENDING))`만 호출.
  - `recoverAuctionOpenedNotifications` 호출 제거(비긴급 쪽으로 이동).
- **`AuctionResultNotificationRecoveryScheduler`** (`notification.recovery.result.fixed-delay-ms`, 기본값 420000→**300000**(5분)으로 변경)
  - `recoverAuctionOpenedNotifications(openedWindowStart)`
  - `recoverAuctionClosedNotifications(closedWindowStart)`
  - `recoverOutbidNotifications(outbidWindowStart, List.of(AuctionStatus.OPEN))`
  - 클래스명이 "결과 통보"보다 넓은 책임을 갖게 돼 다소 부정확해지지만(경매 생성/OPEN 상회입찰까지 포함), 위 이유로 이름은 그대로 두고 클래스 Javadoc에 실제 책임 범위를 명시한다.

윈도우 값(결정 5: interval의 2~3배 이상 유지):
- `openedWindowStart` / `outbidWindowStart`: `now - 15분` (기존 10분 → 인터벌이 90초→5분으로 늘어난 만큼 비례 확대. 3배 기준)
- `closedWindowStart`: `now - 20분` (기존 유지 — 7분 인터벌 기준 이미 20분이었고, 5분 인터벌 기준으로는 여유가 더 늘 뿐이라 바꿀 이유 없음)

### 4. `application.yml` 기본값 갱신

```yaml
notification:
  recovery:
    result:
      fixed-delay-ms: ${NOTIFICATION_RECOVERY_RESULT_FIXED_DELAY_MS:300000}
```

`urgent` 쪽 키는 값 변경 없음(90000 유지).

## 테스트 갱신

- `BidRepositoryTest`: `findAuctionIdsByStatus` 테스트를 `findAuctionIdsByStatusAndAuctionStatusIn`로 교체 + ENDING/OPEN 필터가 실제로 걸러지는지 확인하는 케이스 추가.
- `NotificationReconciliationServiceTest`: 기존 `recoverOutbidNotifications` 호출부에 상태 파라미터 추가(기존 동작 검증 테스트는 `List.of(OPEN, ENDING)`으로 광범위하게 유지), `activeAuctionStatuses`가 실제로 리포지토리 호출에 전달되는지 확인하는 케이스 1개 추가.
- `UrgentNotificationRecoverySchedulerTest`: `recoverAuctionOpenedNotifications` 호출 검증 제거, `recoverOutbidNotifications(window, List.of(ENDING))` 호출만 검증하도록 수정.
- `AuctionResultNotificationRecoverySchedulerTest`: 3개 메서드 호출(생성/종료/OPEN 상회입찰) 검증으로 확장, window 값 갱신.

영향받는 테스트 클래스만 실행하고 전체 스위트는 마지막에 한 번 돌린다.

## 범위

- `notification/recovery` 패키지(서비스/스케줄러), `auction/repository/BidRepository`(읽기 전용 조회 추가), `application.yml`, 관련 테스트.
- 새 스케줄러 클래스/프로퍼티 키 신설 없음.
- `recoverAuctionClosedNotifications`의 window/로직 자체는 변경 없음(호출 주기만 7분→5분으로 바뀜).

## 구현 완료

계획대로 구현했다. 변경 파일: `BidRepository`, `NotificationReconciliationService`, `UrgentNotificationRecoveryScheduler`, `AuctionResultNotificationRecoveryScheduler`, `application.yml`, 관련 테스트 4개.

### 성능 확인: join 추가로 조회가 느려지지 않는지 로컬 DB로 실측

`findAuctionIdsByStatus` → `findAuctionIdsByStatusAndAuctionStatusIn`로 바꾸면서 `bids`-`auctions` join이 하나 늘었는데, 실제로 성능 저하가 있는지 로컬 DB(bids 136,150건, auctions 25,819건, LEADING bid 66건)에서 `EXPLAIN`으로 확인했다.

- 기존 쿼리(`bids.status='LEADING'`만): `idx_bids_status`로 66 rows, `type=ref`.
- 신규 + `auctions.status IN ('ENDING')`: 옵티마이저가 `auctions`를 `idx_auctions_status`로 먼저 좁힌 뒤(14 rows, **Using index**, 커버링) `bids`를 `idx_bids_auction_id`로 join — 후보군이 오히려 66→14로 줄어 더 가벼움.
- 신규 + `auctions.status IN ('OPEN')`: `bids.status`로 66 rows 잡은 뒤 `auctions`를 **PRIMARY KEY**로 `eq_ref` join(포인트 조회) — 기존과 거의 동일한 비용에 PK lookup 66회 추가되는 정도.

두 경우 다 `type`이 `ref`/`eq_ref`(최선의 동등 조건 join)이고 풀스캔·filesort 없음. `bids.status`(`idx_bids_status`)와 `auctions.status`(`idx_auctions_status`)가 이미 있고 join 키(`bids.auction_id` ↔ `auctions.id`)도 양쪽 다 인덱스(FK 인덱스/PK)라, 추가 마이그레이션 없이 기존 인덱스만으로 충분했다.

### 테스트

- 영향받는 4개 테스트 클래스(`BidRepositoryTest`, `NotificationReconciliationServiceTest`, `UrgentNotificationRecoverySchedulerTest`, `AuctionResultNotificationRecoverySchedulerTest`) 개별 실행 통과.
- 전체 스위트(`./gradlew test`) 통과.

> 이 문서는 claude의 도움을 받아 작성하였습니다.
