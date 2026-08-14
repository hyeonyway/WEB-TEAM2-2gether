# 이슈 482: 마감시각 나노초/마이크로초 반올림 불일치로 정상 입찰이 거부되는 문제 수정

## 1. 이슈 경계

- 대응 이슈: [#482](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/482)
- 관련 이슈: [#462](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/462) (같은 근본 원인의 다른 발현, `closeAuction` 비교는 이미 수정됨)
- 목표: `Auction.validateStreamBid`의 마감시각 역행 검증이 저장소 간 정밀도 차이로 스퓨리어스하게 실패하지 않도록 한다.
- 비목표: 이 이슈 하나의 검증 실패가 전역 프로젝션 파이프라인을 막는 설계(`hasProjectionError()`가 auctionId 무관 전역 플래그인 문제) 자체는 이번 수정 범위 밖이다. 필요하면 별도 이슈로 다룬다.

## 2. 현재 상태와 문제

`Auction.validateStreamBid`(`backend/src/main/java/com/dbidding/auction/domain/Auction.java:269`):

```java
if (closeTime.isBefore(this.closeTime)) {
    throw new IllegalArgumentException("일반 입찰은 경매 마감 시각을 앞당길 수 없습니다.");
}
```

`closeTime`은 `bid.accepted.v1` 스트림 이벤트에 담긴 값(Redis Lua가 `auction:state:{id}`에서 HGET한 `closeTime` 문자열, `Instant.toString()` 그대로 나노초까지 보존), `this.closeTime`은 MySQL `auctions.close_time`(`TIMESTAMP(6)`, 마이크로초까지만 저장) 컬럼에서 읽은 값이다.

같은 마감시각이라도 원본 `Instant`의 나노초 끝 3자리가 500ns 이상이면 MySQL 저장 시 마이크로초 단위로 올림된다. 그러면 이벤트에 담긴(반올림 전) 값이 MySQL에 저장된(반올림 후, 더 늦은 시각) 값보다 `isBefore()`로 앞서게 되어, 실제로는 동일한 마감시각인데 검증이 실패한다.

### 실측 사례 (2026-08-14, auctionId=2)

`timeline_events.payload`에서 직접 확인:
- 경매 생성 이벤트 closeTime: `2026-08-14T18:05:39.281821965Z`
- 첫 입찰(auction_version=1) 이벤트 closeTime: `2026-08-14T18:05:39.281821965Z` (생성 시점과 완전히 동일)

MySQL `auctions.close_time` 실제 저장값: `2026-08-14 18:05:39.281822` — 원본 나노초 `...965`가 마이크로초로 올림되어 `...822`가 됨. 원본(`...281821965`ns)이 저장값(`...281822000`ns)보다 정확히 35ns 이전이라 첫 입찰부터 검증 실패, 이후 버전 2~9 입찰이 전부 프로젝션 안 되고 `PENDING`으로 쌓임(전역 파이프라인 정지 부작용).

## 3. 이미 있는 선례: `#462`

`AuctionBidStreamPersistenceService.closeAuction`(같은 정밀도 문제를 다루는 다른 비교)은 이미 `#462`에서 양쪽을 밀리초로 truncate하고 비교하도록 고쳐졌다:

```java
auction.getCloseTime().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        .isAfter(event.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
```

`validateStreamBid`의 `closeTime.isBefore(this.closeTime)`에는 이 truncate가 빠져 있었다 — 같은 클래스의 문제인데 이 자리만 놓친 것으로 보인다.

## 4. 결정

`validateStreamBid`의 마감시각 비교도 `#462`와 동일하게 양쪽을 **밀리초로 truncate한 뒤** 비교한다. 마이크로초(MySQL 저장 정밀도)가 아니라 밀리초를 기준으로 맞추는 이유는 `#462`와 비교 기준을 통일해 이 도메인 전체가 "마감시각 비교는 밀리초 단위"라는 하나의 규칙을 따르게 하기 위함이다 — 두 곳이 서로 다른 정밀도를 쓰면 또 다른 조합의 경계값 버그가 생길 수 있다.

```java
if (closeTime.truncatedTo(ChronoUnit.MILLIS).isBefore(this.closeTime.truncatedTo(ChronoUnit.MILLIS))) {
    throw new IllegalArgumentException("일반 입찰은 경매 마감 시각을 앞당길 수 없습니다.");
}
```

`java.time.temporal.ChronoUnit`을 `Auction.java` 상단에 정식 import하고(현재 파일은 `java.time.Duration`/`java.time.Instant`만 import되어 있음), `AuctionBidStreamPersistenceService`처럼 매번 풀네임을 쓰지 않는다.

### 다른 `Instant` 비교 지점 점검

`Auction.java`와 `AuctionBidStreamPersistenceService.java` 내 Redis-유래 값과 MySQL-유래 값을 직접 비교하는 다른 지점이 있는지 점검한다 (`isBefore`/`isAfter`/`equals` on `Instant` 검색). 현재까지 확인된 것은 `validateStreamBid`(이번 수정 대상)와 `closeAuction`(`#462`에서 이미 수정) 두 곳뿐이나, 회귀 검증 시 재확인한다.

## 5. 작업 내용

- [ ] `Auction.java:269` 비교를 양쪽 `.truncatedTo(ChronoUnit.MILLIS)` 후 비교하도록 수정
- [ ] `ChronoUnit` import 정리
- [ ] 회귀 테스트: 나노초 끝자리가 마이크로초 반올림 경계(예: `.xxxxxx500` 이상)에 걸리는 closeTime으로 생성한 경매의 첫 입찰이 정상 처리되는지 검증 — 경계값을 명시적으로 구성해서 테스트해야 우연히 통과하는 걸 막을 수 있음
- [ ] `Auction.java`/`AuctionBidStreamPersistenceService.java`의 다른 `Instant` 비교 지점 전수 점검 (3절 참고)
- [ ] 수정 배포 후, 현재 막혀 있는 auctionId=2의 ERROR 이벤트를 관리자 replay(`/api/admin/auction-stream/recovery/replay`)로 해소

## 6. 참고 사항

- `#462`: 같은 근본 원인(Redis 나노초 vs MySQL 마이크로초)의 이전 발현, `closeAuction` 비교 수정 사례
- 이 예외 하나가 전역 프로젝션 파이프라인을 막는 문제(`AuctionBidStreamPersistenceService.hasProjectionError()`가 auctionId 무관 전역 플래그)는 이번 수정과 별개 사안 — `#482` 이슈 본문에도 선택 항목으로만 남겨둠
