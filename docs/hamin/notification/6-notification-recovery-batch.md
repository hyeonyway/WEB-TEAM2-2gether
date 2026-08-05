# 알림 저장 소실 복구 배치 설계

담당: D(임하민). [4-realtime-sse.md](4-realtime-sse.md)에 이어지는 라운드. 설계 논의 후 구현까지 진행했다 — 이 문서는 결정 사항과 근거를 남기기 위한 설계 문서다.

설계 논의 당시엔 `AuctionStatus`를 `ACTIVE`/`CLOSED`라는 가상의 이름으로 표기했는데, 실제 구현하며 확인한 진짜 enum은 `OPEN`/`ENDING`(진행 중 — `ENDING`은 앤티스나이핑 연장이 걸린 상태로, 입찰은 여전히 가능)과 `ENDED`/`FAILED`(각각 낙찰/유찰로 종료)다. 아래 모든 내용은 실제 enum 이름으로 맞췄다.

## 배경: 왜 필요한가

`NotificationEventListener`(4단계)는 `@Async @TransactionalEventListener(phase = AFTER_COMMIT)`로 동작한다. 이 방식은 auction/bid 커밋과 알림 저장을 분리해주지만, 대신 두 가지 유실 지점이 생긴다.

- 비동기 리스너 실행 중 예외가 나면 이벤트는 그냥 사라진다 — 별도 재시도나 dead-letter가 없음.
- 커밋은 됐는데 비동기 실행 직전에 앱이 죽으면(배포, 재시작 등) 그 이벤트는 처리되지 못한 채 유실된다.

즉 이벤트 경로만으로는 "알림을 못 받은 유저가 있는데 아무도 모른다"는 상황이 생길 수 있다. 이번 라운드는 이 유실을 주기적으로 스캔해서 채워 넣는 **복구 배치**를 설계한다. 라이브 이벤트 경로(4단계)는 그대로 두고, 이 배치는 어디까지나 백스톱이다.

## 범위

- 세 가지 알림 유형(경매 생성/경매 종료/상회 입찰)에 대한 유실 복구 쿼리 설계.
- `Notification` 스키마에 `type` 컬럼 추가(복구 시 "이미 보냈는지" 판단 키).
- 복구 배치의 도메인 배치 위치.
- 스케줄러 interval/window 결정.
- 필요 인덱스 검토.

범위 밖: `BidStatus.CANCELLED` 활용(현재 미사용, 아래 결정 9), 낙찰 못 한 입찰자에 대한 경매 종료 알림(현재 세팅에서 원래 안 보냄, 이번 라운드에서 새로 추가하지 않음), 경매 생성 알림 fan-out insert 자체의 성능 개선(결정 8, 별도 이슈 #190으로 분리).

## 결정 1: `Notification`에 `type` 컬럼을 추가한다

현재 `Notification`(`notification/Notification.java`)은 `userId/auctionId/message/isRead/createdAt`뿐이라, 복구 배치가 "이 유저에게 이 알림을 이미 보냈는지"를 구조적으로 판단할 방법이 없다(메시지 문자열 비교는 논외). `type`(enum: `AUCTION_OPENED`, `OUTBID`, `AUCTION_WON`, `AUCTION_UNSOLD`) 컬럼을 추가해 복구 쿼리의 존재 여부 체크 키로 쓴다.

이 변경은 `NotificationService.save(userId, auctionId, message)`(4단계 기존 구현)의 시그니처에 `type`을 추가해야 한다는 뜻이기도 하다 — 라이브 이벤트 경로인 `NotificationEventListener`의 세 핸들러(`handleAuctionOpened`/`handleBidPlaced`/`handleAuctionClosed`)도 각자 맞는 `type`을 넘기도록 같이 고쳐야 한다. 새 컬럼 하나 추가하는 게 기존 라이브 경로 코드까지 건드리는 변경이라, 별개 작업으로 나누지 않고 `type` 컬럼 추가와 같은 이슈/커밋 단위로 묶는다.

## 결정 2: dedup은 DB 유니크 제약 없이 4개 타입 전부 "존재 체크 → insert"로 통일한다

처음엔 `AUCTION_OPENED`/`AUCTION_WON`/`AUCTION_UNSOLD`(경매당 유저당 1회성 이벤트)만 `(user_id, auction_id, type)` unique 제약으로 DB 레벨 dedup을 걸고, 반복 가능한 `OUTBID`만 시간 비교로 판단하는 방향을 검토했다. 이 경우 OUTBID는 unique 제약을 걸면 재입찰→재outbid의 정당한 반복 알림까지 막혀버리므로 **"내 최신 bid가 OUTBID 상태인데, 그 bid의 `created_at` 이후로 생성된 OUTBID 알림이 없다"**는 시간 비교로만 판단해야 했다.

이 unique 제약을 MySQL에서 실제로 구현하려면(OUTBID만 제외하는) 생성 컬럼(`CASE WHEN type='OUTBID' THEN NULL ELSE type END`) 같은 우회가 필요했는데, 이 정도 스키마 트릭을 감수할 가치가 없다고 판단해 최종적으로는 **unique 제약 자체를 없애고 4개 타입 전부 OUTBID와 같은 방식(존재 체크 → 없으면 insert, 레이스로 인한 드문 중복은 감수)으로 통일했다.**

이 결정에 이르기까지 검토했던 대안들:
- **인-프로세스 락**: 라이브 이벤트 경로와 배치가 현재 같은 JVM에서 돌아서(단일 인스턴스), `(user_id, auction_id, type)` 키로 in-process 락을 걸면 스키마 변경 없이 레이스를 완전히 없앨 수 있었다. 하지만 다중 인스턴스로 스케일아웃할 계획이 실제로 있어서, 인스턴스마다 따로 노는 락은 그 시점에 무의미해지는 임시방편이라 채택하지 않았다.
- **별도 dedup 테이블**(`notification_dedup_key(user_id, auction_id, type)` PK): DB 레벨에서 크로스 인스턴스로 안전하게 dedup할 수 있는 방법이었지만, 테이블과 트랜잭션을 하나 더 추가하는 비용이 "가끔 중복 알림 하나 더 가는" 리스크를 막는 데 비해 과하다고 판단해 채택하지 않았다.

결과적으로 4개 타입 모두 레이스가 나면 드물게 중복 알림이 갈 수 있다는 걸 감수하기로 했었다 — 유실보다는 훨씬 나은 실패 모드이고, 스키마는 `type` 컬럼 하나 추가하는 것으로 단순하게 유지된다. **다만 이 결정은 아래 결정 2-1에서 뒤집혔다.**

## 결정 2-1 (PR #193 코드래빗 리뷰 이후 재설계): `bid_id`를 추가해 실제 유니크 제약으로 되돌아간다

PR #193 리뷰에서 코드래빗이 결정 2의 판단에 대해 두 가지 문제를 지적했다.

1. **`OUTBID`의 `createdAt` 비교 자체가 취약하다.** 라이브 이벤트 저장이 비동기(`@Async` 스레드풀)라 순서가 뒤바뀔 수 있다. 더 오래된 bid의 알림 저장이 지연돼서 최신 bid보다 늦게 DB에 들어가면, "이 bid 이후에 생성된 OUTBID 알림이 있다"는 시간 비교가 그 지연된(엉뚱한) 알림을 최신 bid의 알림으로 오인해서 진짜 필요한 복구를 건너뛴다. 코드래빗이 파이썬으로 반례까지 만들어 증명했다.
2. **4개 타입 전부에서 존재 체크와 insert가 원자적이지 않다.** 라이브 리스너, 배치, (나중에) 여러 인스턴스가 동시에 처리하면 둘 다 insert할 수 있다.

두 문제 모두 `Notification`에 "이 알림이 어떤 bid에 대한 것인지"를 저장해두면 한 번에 해결된다:

- bid와 무관한 3개 타입(`AUCTION_OPENED`/`AUCTION_WON`/`AUCTION_UNSOLD`)은 `bid_id = 0`(sentinel, `Notification.NO_BID`)을 쓴다.
- `OUTBID`는 실제 그 bid의 id — **상회입찰 "당한" bid**(`previousLeadingBid`/`latestBid`)의 id를 저장한다. "상회입찰 한 bid"(새로 리더가 된 쪽)가 아니라 "당한 bid"를 쓰는 이유는, 배치 복구(`recoverOutbidNotifications`)가 이미 그 bid를 순회하며 들고 있어서 추가 조회 없이 바로 키로 쓸 수 있기 때문이다. 반대로 "상회입찰 한 bid"를 쓰려면 "이 유저를 이긴 다음 bid가 뭐였는지"를 역으로 찾는 별도 조회가 필요하고, 그 관계를 저장하는 컬럼도 없다.
- `UNIQUE (user_id, auction_id, type, bid_id)` 하나로 끝난다. MySQL 유니크 인덱스는 `NULL`만 서로 다른 값으로 취급하고 `0`은 일반 값과 동일하게 취급한다 — 그래서 1회성 3개 타입은 `bid_id=0` 고정이라 겹치면 그대로 막히고(원래 원하던 "경매당 유저당 1회"), `OUTBID`는 bid마다 값이 달라서 재입찰→재outbid의 정당한 반복은 안 막히면서 같은 bid에 대한 중복만 막힌다.

이걸로 결정 2에서 검토했던 대안(인-프로세스 락, 별도 dedup 테이블)이 다시 불필요해졌다 — 인-프로세스 락은 여전히 다중 인스턴스 스케일아웃 계획 때문에 기각 상태고, 별도 dedup 테이블은 `bid_id` 컬럼 하나 추가로 같은 효과를 내서 테이블을 하나 더 만들 이유가 없어졌다.

구현 변경 사항:
- `BidPlacedEvent`에 `previousBidId` 필드 추가 — `AuctionCommandService.publishBidPlaced`가 이미 `previousLeadingBid`를 들고 있어서 `previousLeadingBid.getId()`만 추가하면 됐다(auction 도메인 담당자와 소통 후 직접 수정).
- `Notification`에 `bidId` 필드/컬럼, `NO_BID = 0L` 상수, `ofBid(...)`/`NotificationService.saveForBid(...)` 신설(기존 `of`/`save`는 그대로 두고 `bid_id=NO_BID`로 위임).
- `NotificationRepository.existsByUserIdAndAuctionIdAndTypeAndBidId(...)`로 4개 타입 전부 같은 방식의 존재 체크. 기존 `existsByUserIdAndAuctionIdAndType`/`existsByUserIdAndAuctionIdAndTypeAndCreatedAtAfter`는 제거.
- 실제 유니크 제약이 다시 생겼으므로, 배치(`NotificationReconciliationService.saveIgnoringDuplicate`)와 라이브 리스너(`NotificationEventListener`) 양쪽 다 `DataIntegrityViolationException`을 잡아 무시하는 코드가 다시 필요해졌다.
- 라이브 리스너 쪽은 저장이 중복으로 실패해도 **SSE push는 그대로 보낸다** — `NotificationRepository.findByUserIdAndAuctionIdAndTypeAndBidId(...)`로 배치가 이미 저장해둔 알림을 찾아서 push한다. 배치는 저장만 하고 push를 안 하므로, 이 경우 push까지 스킵하면 지금 SSE로 연결돼 있는 유저가 실시간 알림을 놓친다. 네 핸들러를 `saveAndPush` 하나로 통일해 이 로직을 공유한다.

## 결정 3: 타입별 복구 쿼리

**경매 생성 (`AUCTION_OPENED`)**
1. `auctions` WHERE `status IN ('OPEN', 'ENDING') AND open_time >= :windowStart` (최근 window 안에 열린 경매만, 전체 활성 경매 아님)
2. 각 경매의 `item_id`로 `wishlists`에서 찜 유저 조회
3. `(userId, auctionId, AUCTION_OPENED)` 알림 존재 여부 체크 → 없으면 생성

2번은 스캔 시점 기준 찜 유저를 조회한다 — 라이브 이벤트 경로는 경매가 열리는 순간의 찜 유저 스냅샷에만 보내지만, 배치는 그 이후(예: window 안에서) 새로 찜한 유저에게도 "경매 등록" 알림을 보낸다. 라이브 경로와 완전히 동일하게 재현하려면 "찜한 시점이 `open_time` 이전"이라는 조건을 추가해야 하지만, 의도적으로 넣지 않기로 했다 — 새로 찜한 유저 입장에서도 "지금 활성 경매가 있다"는 정보 자체는 여전히 유효하고 유용하므로, 굳이 막을 이유가 없다고 판단했다.

**경매 종료 (`AUCTION_WON` / `AUCTION_UNSOLD`)**
1. `auctions` WHERE `status IN ('ENDED', 'FAILED') AND close_time >= :windowStart` (`ENDED`=낙찰, `FAILED`=유찰)
2. `sellerId`는 auction에서, 낙찰자는 `bids` WHERE `auction_id = ? AND status = 'WON'`으로 조회(별도 낙찰 테이블 불필요 — 이미 `BidStatus.WON`이 있음)
3. `(sellerId/winnerId, auctionId, type)` 알림 존재 여부 체크 → 없으면 생성

**상회 입찰 (`OUTBID`)**
1. `bids` WHERE `status = 'LEADING'` → 현재 활성 경매들의 리더 bid 집합 추출 (window 없이 매 사이클 전체 스캔, 아래 설명)
2. **+ `auctions` WHERE `status IN ('ENDED','FAILED') AND close_time >= :windowStart`인 경매도 후보에 합친다** (아래 "종료 경계" 설명 참고)
3. 그 경매들에서 유저별 최신 bid(`MAX(id) GROUP BY bidder_id, auction_id`)가 `status = 'OUTBID'`인 것만 추출
4. 각 row에 대해 `(bidderId, auctionId, OUTBID, bidId)` 알림이 정확히 존재하는지 체크(`existsByUserIdAndAuctionIdAndTypeAndBidId`) → 없으면 생성 (결정 2-1 전에는 `created_at` 이후 존재 여부로 판단했으나, 비동기 저장 순서가 뒤바뀌는 레이스에 취약해 `bid_id` 정확 일치로 교체했다)

**종료 경계 버그(PR #193 코드래빗 리뷰로 발견, 수정 완료)**: 처음 구현은 1번만으로 후보를 뽑았는데, 이러면 "상회입찰 직후 ~ 다음 스캔 사이에 경매가 종료되는" 경우를 놓친다 — `closeLockedAuction`이 낙찰 bid를 `LEADING → WON`으로 바꾸는 순간 그 경매엔 더 이상 LEADING bid가 없어져서, `findAuctionIdsByStatus(LEADING)`가 그 경매를 아예 안 돌려준다. 경매 종료 복구는 낙찰자/판매자에게만 알리므로(결정 9), outbid된 나머지 유저는 세 복구 로직 어디에도 안 걸려 **영구 유실**된다. 그래서 2번처럼 최근 종료된 경매도 후보 집합에 합치도록 고쳤다 — 낙찰자는 그 경매에서 `status=WON`이라 3~4번에서 자연히 스킵되고, 나머지 outbid된 유저만 잡힌다. 추가 비용은 `recoverAuctionClosedNotifications`가 이미 쓰는 것과 동일한 인덱스(`idx_auctions_status_close_time`) 기반 쿼리 하나뿐이라 미미하다(호출 빈도만 7분→90초로 늘어남).

리뷰 스레드에서 "종료 임박 입찰은 앤티스나이핑으로 5분 연장되고, `UrgentNotificationRecoveryScheduler`가 90초 간격이니 그 안에 최소 2번은 복구가 돌아서 `WON` 전환 전에 이미 잡히지 않냐"는 반박이 나왔다. 이 반박 자체는 **"수정 전" 코드(1번만으로 후보를 뽑던 버전) 기준으로는 불충분한 논리였다** — `fixedDelay`는 이전 실행 완료 시점부터 다음 실행까지의 간격이라 5분 동안 정확히 2회 실행된다는 보장이 없고, 앱이 중단됐다 재기동하는 경우엔 재기동 시점에 이미 `WON`으로 전환돼 있어서 "종료 전에 잡힌다"는 전제 자체가 깨지기 때문이다.

**다만 이건 "수정 전" 코드에 대한 반박이고, 지금 코드(2번: 최근 종료된 경매도 후보에 포함)는 앱이 얼마나 오래 멈췄다 재기동하든 상관없이 이 케이스를 잡는다.** 이유: `AuctionCommandService.closeLockedAuction(auction, closedAt)`의 `closedAt`은 항상 **실제로 종료 처리가 실행되는 시점의 `now()`**이고, `closeWithWinningBid`/`closeWithoutTrade`가 `closeTime`을 그 값으로 덮어쓴다 — 즉 `close_time`은 원래 예정된 마감 시각이 아니라 "언제 실제로 처리됐는가"를 반영한다. `AuctionDeadlineScheduler.scheduleOnStartup()`은 `ApplicationReadyEvent`에서 바로 밀린 경매를 즉시 닫기 때문에, 앱이 15분이든 1시간이든 멈췄다 재기동해도 재기동 직후 `close_time`이 "최근" 값으로 다시 찍히고, 90초 뒤 첫 복구 실행에서 `close_time >= now - 10분` 조건을 항상 통과한다.

진짜 남는 잔여 리스크는 이 outage 시나리오가 아니라, 결정 5에서 이미 인지하고 감수하기로 한 것과 같다 — **복구 스케줄러 자체가 window(10분)보다 오래 다운되는 경우**(예: 경매는 정상적으로 닫혔는데 복구 스케줄러만 별도 설정 등으로 오래 멈춰서 그 window를 놓치는 경우)만 여전히 못 막는다. 회귀 테스트(`NotificationReconciliationServiceTest.상회입찰_직후_경매가_종료돼_LEADING이_사라져도_outbid_유저를_복구한다`)로 종료 경계 자체는 검증한다.

"유저별 최신 bid"를 가릴 때 `created_at`이 아니라 `id`(AUTO_INCREMENT PK)로 그룹핑한다. `id`는 입찰 순서를 그대로 반영하면서(`created_at`과 동일한 정보) 항상 유일해 동시각 충돌 걱정이 없고, InnoDB는 모든 세컨더리 인덱스 leaf에 PK(`id`)를 자동으로 붙이기 때문에 `(auction_id, user_id)` 같은 복합 인덱스만 있으면 `MAX(id)`가 인덱스만으로 바로 해결된다(`created_at`은 이 혜택이 없어 별도로 복합 인덱스에 명시해야만 정렬 기준으로 쓸 수 있다). 이 그룹핑으로 뽑은 `latestBid`의 `id`는 결정 2-1의 `bid_id` dedup 키로도 그대로 재사용된다 — 별도 조회 없이 `latestBid.getId()`가 곧 존재 체크·저장 양쪽의 키다.

경매 생성/종료는 "최근 window 안의 활동"만 스캔하도록 설계했다 — 이 두 쿼리는 `auctions` 테이블에서 필터링하는데, `open_time`/`close_time` 기준 없이 상태만 걸면 결과 집합이 계속 쌓이는 이력(특히 `ENDED`/`FAILED`)까지 다 딸려오기 때문이다.

상회 입찰은 다르다. `markOutbid()`/`markWon()` 로직상 `status = 'LEADING'`인 row는 **경매당 최대 1개**로 자연스럽게 제한된다(현재 리더). 즉 이 결과 집합 크기는 `bids` 테이블 총량이 아니라 **"현재 활성 경매 수"**에 이미 비례한다 — `auctions.status IN ('OPEN', 'ENDING')`과 같은 성격이다. 그래서 여기는 굳이 `created_at` window로 더 좁힐 필요가 없고, 매 사이클마다 활성 경매 전체를 재확인해도 비용이 커지지 않는다(결정 7에서 다시 다룸).

## 결정 4: 구현 위치는 `notification`도 `auction`/`wishlist`도 아닌 `batch` 도메인

이 배치는 auction(활성/종료 경매), wishlist(찜 유저), bid(입찰 상태)를 읽어서 notification에 쓰는 순수 크로스 도메인 오케스트레이션이다. 어느 한쪽 도메인에 넣으면 그 도메인이 원래 몰라도 될 다른 도메인 내부를 알아야 하게 된다.

이미 알려진 문제(메모: `notification-wishlist-port-todo`)와 같은 함정을 피하기 위해서이기도 하다 — `WishlistUserFinderAdapter`/`CardNameFinderAdapter`가 provider 패키지가 아니라 `notification/adapter/`에 임시로 얹혀서 역방향 결합이 생긴 전례가 있다. 여기서 또 "ActiveAuctionFinder"/"BidFinder" 포트를 notification 쪽에 만들면 같은 빚이 하나 더 늘어난다.

`backend/src/main/java/com/dbidding/batch`는 현재 `.gitkeep`만 있는 빈 패키지로, 정확히 이런 크로스 도메인 배치 작업을 위해 예약된 자리로 판단해 여기에 둔다. `batch`는 다른 도메인이 참조할 일이 없는 top-level 오케스트레이터라 각 도메인의 기존 public 서비스(`AuctionQueryService`, `WishlistService`, `BidRepository`, `NotificationService.save`)를 직접 호출해도 역방향 결합 문제가 없다.

구현 구조:
- `batch/service/NotificationReconciliationService.java` — 결정 3의 세 쿼리/생성 로직
- `batch/scheduler/UrgentNotificationRecoveryScheduler.java` — 경매 생성 + 상회 입찰
- `batch/scheduler/AuctionResultNotificationRecoveryScheduler.java` — 경매 종료
- `batch/config/NotificationRecoverySchedulingConfig.java` — 전용 `TaskScheduler` 빈

**PR #193 리뷰에서도 같은 논쟁이 나왔다.** 코드래빗이 "`batch`가 `Auction`/`Bid`/`AuctionRepository`/`BidRepository`/`WishlistService`를 직접 참조하니 소비자 소유 Port로 분리하라"고 지적했는데, "그러면 (auction/wishlist 담당자의) 어댑터 구현을 또 기다려야 해서 지연된다"고 반박하자 코드래빗이 리뷰를 철회했다 — 이 프로젝트 규모에서는 단순 조회를 위해 포트/어댑터를 미리 만드는 것보다 직접 호출하는 게 낫다는 판단과 같은 결론이다.

## 결정 5: 스케줄러는 "타입"이 아니라 "긴급도" 기준 2개로 나눈다

처음엔 경매 종료만 정보성(늦어도 유저가 할 수 있는 게 없음)이라 느긋하게, 나머지 둘은 급하게 생각했지만 논의하면서 재조정했다.

- **경매 생성**: 즉시구매가가 있어서 늦게 알면 손해일 수 있다고 생각했지만, "즉시구매가는 보통 아무나 살 만큼 싸게 잡지 않는다"는 판단으로 초 단위로 급할 필요는 없다고 정리.
- **상회 입찰**: 늦게 알면 재입찰 기회를 잃는 게 맞지만, 앤티스나이핑 자동 연장 기준(5분)이 자연스러운 상한선이 된다 — 그 5분 안에만 복구되면 재입찰 시간은 확보된다.
- **경매 종료**: 여전히 순수 정보성(결과 통보) — 가장 느긋해도 된다.

그래서 최종적으로 "긴급/비긴급 2개 스케줄러"로 나누되, 둘 다 원래 생각보다 완화된 수치로 정했다:

| 스케줄러 | 대상 | interval | window |
|---|---|---|---|
| `UrgentNotificationRecoveryScheduler` | 경매 생성 + 상회 입찰 | 1~2분 | 경매 생성만 5~10분(상회 입찰은 앤티스나이핑 5분 연장 기준 안쪽에 복구되면 되지만, 결정 3에서 정리했듯 window 없이 매 사이클 활성 경매 전체를 재확인하므로 별도 window 불필요) |
| `AuctionResultNotificationRecoveryScheduler` | 경매 종료 | 5~10분 | 15~30분 |

window는 항상 interval의 2~3배 이상으로 잡는다 — job 실행이 한 번 밀리거나(느린 쿼리, GC pause) 스케줄러가 지연돼도 다음 스캔에서 놓치지 않도록 하는 여유분이다. 이 여유를 벗어나 job 자체가 window보다 오래 다운되는 경우는 이 설계로 못 막는 잔여 리스크로 남긴다.

**잔여 리스크: 멀티 인스턴스 환경에서의 중복 실행.** 지금은 단일 인스턴스 운영이라 문제없지만, 나중에 서버를 여러 인스턴스로 스케일아웃하면 `@Scheduled` 잡이 인스턴스마다 각각 실행돼 같은 스캔을 중복으로 돈다. 다만 이건 이번에 새로 생기는 문제가 아니라 기존 `AuctionClosingScheduler`(단순 on/off 플래그만 있고 분산 락 없음)도 이미 안고 있는 것과 동일한 리스크라, 인스턴스를 늘리는 시점에 두 스케줄러를 함께 처리하면 된다(예: `ShedLock` 같은 분산 락 도입).

## 결정 6: 서버 스펙(t4g.micro)을 고려해도 interval 자체는 문제가 아니다

운영 서버가 t4g.micro(2 vCPU 버스터블, RAM 1GB)라 1~2분 interval이 부담되는지 검토했다. 결론은 interval 자체보다 **쿼리가 인덱스를 타는지**가 관건이라는 것 — window로 결과 집합을 좁혀놨기 때문에 인덱스만 있으면 각 쿼리는 수십 ms 수준이라 1~2분마다 돌아도 부담이 크지 않다. 대신 인덱스 없이 풀스캔이 섞이면 버스터블 CPU 크레딧을 갉아먹는 게 진짜 위험 요소다(결정 7).

## 결정 7: 필요 인덱스 검토 — 1개 신규 인덱스 필요

기존 `schema.sql`을 확인한 결과, 결정 3의 쿼리 중 일부는 인덱스가 부족했다.

| 용도 | 필요 인덱스 | 기존 상태 |
|---|---|---|
| 경매 종료 window (`status IN ('ENDED','FAILED') AND close_time >= ...`) | `(status, close_time)` | ✅ `idx_auctions_status_close_time` 이미 있음 |
| 경매 생성 fan-out (`item_id`로 찜 유저 조회) | `item_id` | ✅ `idx_wishlists_item_id` 이미 있음 |
| 상회 입찰 1단계 (`status='LEADING'`) | `bids(status)` | ❌ 없음 — `bids`엔 `user_id`/`auction_id`/`(auction_id, bid_price)`만 인덱스됨 |
| 경매 생성 window (`status IN ('OPEN','ENDING') AND open_time >= ...`) | `auctions.status`만으로 충분, 복합 인덱스 불필요 (아래 설명) | ✅ `idx_auctions_status` 이미 있음 |
| 알림 존재 체크 (`user_id, auction_id, type`) | `notification.user_id`만으로 충분, 복합 인덱스 불필요 (아래 설명) | ✅ `idx_notification_user_id` 이미 있음 |

예정 마이그레이션:
```sql
ALTER TABLE bids ADD INDEX idx_bids_status (status);
```

`bids(status, created_at)` 복합도 처음엔 검토했지만 `created_at`을 빼고 `status` 단일 인덱스로 정리했다. 이유는 `auctions(status, open_time)`을 뺀 것과 같은 논리다 — `status='LEADING'`인 row는 경매당 최대 1개로 자연 제한돼(결정 3) 결과 집합이 이미 "현재 활성 경매 수"만큼으로 작다. `created_at`을 복합으로 추가해도 이 집합 크기가 더 줄지 않고, row lookup 횟수도 동일해서 실익이 거의 없다(반면 인덱스 엔트리당 몇 바이트만 더 든다). 그래서 단일 인덱스로 충분하다고 판단했다.

`auctions(status, open_time)` 복합 인덱스도 같은 이유로 뺐다. `bids.status`와 달리:

- `bids`는 `status`에 인덱스가 **아예 없어서** `WHERE status='LEADING'`만 걸어도 계속 커지는 `bids` 테이블 전체를 스캔해야 한다 — 결과 집합은 작아도, 인덱스가 없으면 그 작은 결과를 찾는 비용이 **테이블 총량**에 비례한다. 그래서 `status` 단일 인덱스라도 반드시 필요.
- `auctions`는 `idx_auctions_status`가 **이미 있다.** `WHERE status IN ('OPEN','ENDING')`만으로도 이미 "현재 활성 경매 수"만큼만 읽어오고, 여기서 `open_time` 필터를 DB에서 더 좁히든 서버 메모리에서 거르든 차이가 미미하다. 그래서 `(status, open_time)` 복합 인덱스는 지금 스케일에서 마이그레이션 비용 대비 이득이 작다고 판단해 뺐다. 나중에 동시 활성 경매 수 자체가 크게 늘면 그때 다시 검토한다.

`bids(status)`는 사실 배치 전용도 아니다. `AuctionCommandService.java:437`의 `findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(auctionId, LEADING)`(입찰마다 도는 라이브 쿼리)도 지금 `status` 인덱스가 없어서 `idx_bids_auction_id`만 타고 나머지는 메모리 필터링 중이었다 — 이번에 배치를 계기로 기존 갭을 같이 메꾸는 셈이다.

`notification(user_id, auction_id, type)` 복합 인덱스도 처음엔 필요하다고 판단했다가 재검토 후 뺐다. `idx_notification_user_id`가 이미 있어서 `user_id`로 먼저 좁힌 뒤 `auction_id`/`type`은 그 안에서 걸러도 되는데, 관건은 "유저 한 명당 알림이 몇 개나 쌓이는가"다.

다만 이건 `bids.status`/`auctions.status`와 성격이 다르다는 점은 짚어둔다. `bids`의 LEADING row 수(경매당 최대 1개)나 `auctions`의 ACTIVE row 수는 **비즈니스 로직이 구조적으로 보장하는 상한**이지만, 유저별 알림 총량은 그런 구조적 상한 없이 계속 누적된다. 다만 이 차이가 실제로 문제가 되려면 유저 한 명당 알림이 수천 건은 쌓여야 하는데, 그 정도 row 수는 `user_id` 인덱스로 좁힌 뒤 필터링해도 여전히 수 ms 수준이라 실용적으로는 걱정할 수준이 아니다. 그래서 복합 인덱스 없이 진행한다.

### Q&A: 복합 인덱스가 없으면 `open_time` 필터를 DB WHERE절과 서버 코드 중 어디서 걸러야 하나

복합 인덱스를 안 만들기로 했다고 해서 "그럼 `open_time` 조건은 서버에서 거르자"는 결론이 되는 건 아니다. `WHERE status IN ('OPEN','ENDING') AND open_time >= ?`처럼 **DB의 WHERE절에 그대로 두는 쪽이 항상 같거나 더 싸다.**

이유: `idx_auctions_status`만 있는 상태에서 이 쿼리를 돌리면 MySQL은 (1) `status` 인덱스로 ACTIVE row를 좁히고, (2) `open_time`은 인덱스에 없으니 좁혀진 각 row를 실제로 읽어서 그 자리에서 걸러낸다(`Using where`). "DB가 ACTIVE 전부를 주고 서버가 걸러라"로 바꿔도 (1)(2) 과정 자체는 동일하게 발생한다 — 차이는 그다음이다.

- **DB에서 필터링**: 조건에 안 맞는 row는 걸러진 뒤 결과 셋에 안 들어감 → JDBC 직렬화도, 앱에서의 엔티티 역직렬화(Hibernate 객체 생성)도 안 함.
- **서버에서 필터링**: ACTIVE 전부가 결과 셋에 포함돼 직렬화 → 앱에서 전부 역직렬화/엔티티 hydrate → 그 다음에야 걸러서 버림.

즉 네트워크 전송 비용을 무시해도(같은 AZ라 사실상 0이라 쳐도), **버려질 row까지 직렬화·역직렬화·객체 생성하는 CPU/메모리 비용**이 서버 필터링 쪽에 추가로 붙는다. `open_time` 비교 연산 자체는 DB에서 하나 서버에서 하나 똑같이 가볍다 — 차이는 "버릴 row를 객체로 만드느냐 마느냐"에서만 난다.

물론 이 차이도 결국 ACTIVE row 수 규모에 달려 있다. 지금처럼 활성 경매 수가 작으면 이 차이 역시 무시할 만큼 작지만(위 결론과 같은 이유), DB WHERE절에 두는 쪽은 추가 비용(마이그레이션 등) 없이 공짜로 얻는 이득이라 그냥 유지한다.

경매 생성 fan-out(찜 유저 수만큼) 때문에 `notification` 테이블 자체의 전체 쓰기량이 `bids`보다 많을 수 있다는 점도 짚었지만, 이건 "테이블 총 쓰기량"이지 "유저 한 명당 읽기 대상 row 수"와는 별개다 — 이번 결정은 후자 기준이다.

## 결정 8 (발견, 범위 밖으로 분리): 경매 생성 fan-out이 유저당 개별 INSERT/트랜잭션이다

인덱스 검토 중 `NotificationEventListener.handleAuctionOpened`(`notification/NotificationEventListener.java:26-30`)를 실제로 열어보니, 찜 유저 fan-out이 batch insert가 아니라 유저 한 명당 `notificationService.save(...)`를 개별 호출하는 구조였다(DB round trip N번, 트랜잭션 commit N번, SSE push도 유저마다 개별 호출, 전부 `@Async` 단일 스레드에서 순차 실행). 인기 카드처럼 찜 유저가 수백 명이면 이게 이번에 논의한 인덱스보다 훨씬 큰 병목일 수 있다.

이건 이번 복구 배치 설계와는 별개의 **기존 이슈**로 판단해 이번 라운드 범위에서 제외했다. 나중에 `saveAll` + JDBC batch 설정 또는 bulk insert로 개선하는 걸 별도 이슈로 다룬다.

## 결정 9 (참고, 이번 라운드에서 다루지 않음): `BidStatus.CANCELLED` 미사용

`BidStatus`는 `LEADING`/`OUTBID`/`WON`/`CANCELLED` 4개인데, `CANCELLED`가 정의만 돼 있고 실제로 세팅되는 곳이 없다(`Bid.java`, 전체 검색 결과 — 설계 논의 당시엔 이 값이 `LOST`/`WITHDRAWN` 두 개였으나 이후 `CANCELLED` 하나로 리팩터링됐고, 미사용이라는 결론 자체는 그대로다). 경매가 끝나도 낙찰 못 한 나머지 bid는 그냥 `OUTBID` 상태로 남는다. 이번 복구 설계는 "낙찰자/판매자"만 종료 알림을 받는 기존 동작을 그대로 전제하며, "낙찰 실패자에게도 종료를 알린다"는 기능 확장은 다루지 않는다. 나중에 그 기능이 필요해지면 `closeLockedAuction`에서 나머지 bid들의 상태 처리(`CANCELLED` 세팅)부터 다시 설계해야 한다.

## 구현 완료 (이슈 #189, PR #193)

- `type`/`bid_id` 컬럼 추가, `(user_id, auction_id, type, bid_id)` 유니크 제약(결정 1, 2, 2-1)
- `NotificationService.save`/`saveForBid`, `NotificationEventListener` 네 핸들러를 `saveAndPush`로 통일 + 중복 레이스 시 SSE push 유지(결정 2-1)
- `batch` 도메인 구현(`NotificationReconciliationService`, 스케줄러 2개, 전용 스케줄링 설정)(결정 4, 5)
- `bids(status)` 인덱스 마이그레이션(결정 7)
- 종료 경계 OUTBID 누락 버그 수정 + 회귀 테스트(결정 3, PR #193 리뷰로 발견)

## 별도 이슈로 분리

- 결정 8(경매 생성 fan-out batch insert 개선) — 이슈 #190
- 결정 9(`BidStatus.CANCELLED`/낙찰 실패자 종료 알림) — 아직 이슈화 안 함, 필요해지면 별도 논의

> 이 문서는 claude의 도움을 받아 작성하였습니다.