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

## 재검토(2차): 후보 조회 방식 재설계 + 경매 생성 알림 batch insert 통합

1차 구현을 리뷰하며 세 가지를 다시 짚었다.

### 1. ENDING 상회입찰 복구: `bids` 조인 제거

`Auction.placeBid` → `extendCloseTimeIfNeeded`([Auction.java:171-191](../../../backend/src/main/java/com/dbidding/auction/domain/Auction.java))을 확인한 결과, 경매가 `ENDING`이 되는 유일한 경로는 "마감 임박 윈도우 안에 입찰이 들어와 앤티스나이핑 연장이 트리거되는 경우"뿐이다. 즉 **ENDING ⟹ 그 트리거 입찰이 항상 LEADING으로 남아있음**이 구조적으로 보장된다(다른 경로로 ENDING이 세팅되는 곳은 코드 전체에 없음 — 스케줄러(`AuctionDeadlineScheduler`/`AuctionClosingScheduler`)는 `ENDED`/`FAILED`로만 전환하고 ENDING을 세팅하는 시간 기반 경로는 존재하지 않는다는 것도 별도로 확인했다).

그래서 `bids.status=LEADING`과 조인할 필요 없이 `AuctionRepository`에 `findIdsByStatus(AuctionStatus)`(단일 상태 id 프로젝션, 기존 `findDueAuctionIds` 패턴과 동일)를 추가해 `auctions.status=ENDING`만으로 후보를 뽑는다. 종료 경계 캐치(최근 종료 경매 병합)는 그대로 유지 — ENDING은 정의상 마감 임박이라 스캔 사이에 닫힐 확률이 가장 높은 쪽이라 오히려 더 필요하다.

### 2. OPEN 상회입찰 복구: 조인은 유지(제거하면 역효과)

OPEN은 ENDING과 달리 입찰이 하나도 없는 신규 등록 경매도 포함되는 기본 상태라, `auctions.status=OPEN`만으로 후보를 뽑으면 "입찰이 실제로 있는 경매"보다 훨씬 넓은 집합(입찰 없는 경매까지 전부)이 잡혀서 이후 `findLatestBidPerBidderByAuctionIdIn` 비용이 불필요하게 커진다. 그래서 OPEN 쪽은 `bids.status=LEADING`과의 조인(`BidRepository.findAuctionIdsByStatusAndAuctionStatus`, 이번에 `Collection<AuctionStatus>`에서 단일 `AuctionStatus`로 시그니처도 단순화)을 그대로 유지한다. 종료 경계 캐치도 그대로 유지 — 오히려 OPEN이 ENDING을 거치지 않고 바로 닫히는 경우(막판 스나이핑이 없는 대다수 경매)가 더 흔해서, 이 캐치를 없애면 재발 위험이 ENDING보다 크다.

결과적으로 `recoverOutbidNotifications(windowStart, activeAuctionStatuses)` 하나였던 메서드를 `recoverEndingOutbidNotifications(windowStart)`/`recoverOpenOutbidNotifications(windowStart)` 둘로 나누고, 공통 로직(종료 경계 캐치 + outbid 판정 + 저장)은 private 헬퍼로 추출했다. 두 메서드가 애초에 다른 조회 전략을 쓰게 된 이상, "상태를 파라미터로 받는 제네릭 메서드 하나"보다 "이름이 곧 의도를 말해주는 메서드 둘"이 더 정확하다고 판단했다.

### 3. 경매 생성 알림 복구: 경매별 개별 INSERT를 하나로 통합

`recoverAuctionOpenedNotifications`가 윈도우 안에 열린 경매마다 `saveAllIgnoringDuplicates`를 개별 호출하던 걸(경매 N개 → INSERT N번), 여러 경매의 `(userId, auctionId, message)` 행을 모아 한 번에(청크 단위로) INSERT하도록 바꿨다. 덤으로 이 복구 배치 호출은 원래도 반환값(재조회 SELECT 결과)을 안 썼는데 라이브 이벤트 경로가 쓰는 기존 메서드를 그대로 호출해서 경매마다 불필요한 재조회까지 하고 있었다 — 그래서 재조회가 없는 전용 메서드(`NotificationService.insertAllIgnoringDuplicates(List<NotificationInsertRow>, NotificationType)`)를 새로 만들었다. 라이브 경로(`NotificationEventListener.handleAuctionOpened`, 항상 경매 1개 + SSE push 필요)가 쓰는 기존 `saveAllIgnoringDuplicates`는 그대로 둔다 — 요구사항이 다른 두 호출부를 억지로 하나의 시그니처로 합치지 않았다.

### 4. 스케줄러 리네이밍(사용자 확정)

1차에서는 배포 환경 `.env` 오버라이드(`NOTIFICATION_RECOVERY_RESULT_*`)를 깨뜨릴 위험 때문에 `AuctionResultNotificationRecoveryScheduler`란 이름과 `notification.recovery.result.*` 키를 그대로 남겨뒀는데, 리뷰 중 "이제 하는 일에 안 맞는 이름이니 바꾸자"는 요청이 있었다. 프로퍼티 키까지 같이 바꿀지 클래스명만 바꿀지 확인한 결과 **"프로퍼티 키도 같이 변경"**으로 확정 — `NonUrgentNotificationRecoveryScheduler`로 리네이밍하고 `notification.recovery.non-urgent.*` / `NOTIFICATION_RECOVERY_NON_URGENT_*`로 전부 통일했다(배포 서버 `.env`에 기존 `NOTIFICATION_RECOVERY_RESULT_*` 오버라이드가 있었다면 이제 적용되지 않고 새 기본값 300000ms로 동작하게 됨 — 사용자가 이 트레이드오프를 알고 선택함). [15-scheduler-toggle-properties.md](15-scheduler-toggle-properties.md)에 이 변경을 가리키는 안내를 추가해뒀다.

### 2차 변경 파일

`AuctionRepository`(`findIdsByStatus` 추가), `BidRepository`(`findAuctionIdsByStatusAndAuctionStatus`로 시그니처 단순화), `NotificationReconciliationService`(상회입찰 메서드 분리 + 경매 생성 알림 batch insert), `NotificationService`(`insertAllIgnoringDuplicates` 추가), `NotificationInsertRow`(신규 레코드), `UrgentNotificationRecoveryScheduler`, `NonUrgentNotificationRecoveryScheduler`(리네이밍), `application.yml`, 관련 테스트 6개 + 신규 real-DB 테스트(`NotificationServiceBulkInsertTest`).

영향받는 테스트 클래스 개별 실행과 전체 스위트(`./gradlew test`) 모두 통과.

> 이 문서는 claude의 도움을 받아 작성하였습니다.
