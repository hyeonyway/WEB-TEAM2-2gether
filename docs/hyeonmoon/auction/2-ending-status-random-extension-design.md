# 마감임박(ENDING) 진입 시간 기준 전환 + 단발 랜덤 연장 — 설계 문서

**관련 이슈:** #418
**스코프:** DB 기반 입찰 경로(`!redis` 프로필)만. Redis/Lua 경로는 명시적으로
제외(마지막 장 참고).

## 1. 배경 및 문제

현재 구현(`Auction.extendCloseTimeIfNeeded`)은 이렇다.

```java
private boolean extendCloseTimeIfNeeded(Instant bidAt, Duration extensionWindow, Duration extensionDuration) {
    Instant extensionThreshold = closeTime.minus(extensionWindow);   // closeTime - 5분
    if (bidAt.isBefore(extensionThreshold)) return false;
    Instant extendedCloseTime = closeTime.plus(extensionDuration);   // closeTime + 5분
    closeTime = extendedCloseTime;
    estimatedCloseTime = extendedCloseTime;
    status = AuctionStatus.ENDING;
    return true;
}
```

- **`ENDING` 상태는 시간이 아니라 입찰이 트리거한다.** 마감 5분 전에
  입찰이 없으면 상태는 계속 `OPEN`으로 남아있다가 조용히 마감된다.
- **입찰이 들어올 때마다 5분씩 반복 연장**된다. 이 반복 때문에 여러
  경매의 마감시각이 특정 시간대로 몰릴 수 있다는 우려가 있다.
- **`closeTime`이 API 응답(`ends_at`)에 그대로 노출**돼서, 정확한 마감
  타이밍을 노리는 스나이핑을 막을 방법이 없다.

## 2. 목표 동작

1. `ENDING` 진입은 **입찰과 무관하게 순수 시간 기준**으로 바뀐다 — OPEN
   경매의 남은 시간이 5분 이하가 되는 순간 자동으로 `ENDING`이 된다.
2. 그 전환 시점에 실제 마감시각(`closeTime`)에 **1~2분 사이 랜덤 값을
   딱 한 번만** 더한다. 이후 그 경매에 입찰이 더 들어와도 추가 연장은
   없다.
3. 고객에게 노출되는 마감시각은 이 전환 시점에 **얼려서(freeze)** 그
   이후 진짜 `closeTime`(랜덤 연장 반영값)과 달라지게 한다 — 화면에서
   실시간 카운트다운을 통해 정확한 마감 타이밍을 역산할 수 없게 한다.

## 3. 스케줄러 — 기존 구조 확장 (신규 스케줄러 추가 안 함)

`AuctionDeadlineScheduler`는 이미 "전체 경매 중 다음 이벤트 1건"에만
정밀 타이머를 걸고, 그 이벤트가 바뀌면(`AuctionCloseScheduleChangedEvent`)
재스케줄하는 단일 롤링 타이머 패턴이다. 이 패턴을 그대로 재사용한다 —
**타이머를 2개 만드는 게 아니라, "다음 타겟이 뭐냐"의 계산 기준만
상태별로 분기한다.**

```text
OPEN 경매의 다음 타겟   = closeTime - 5분   (ENDING 진입 시각)
ENDING 경매의 다음 타겟 = closeTime          (진짜 마감 시각, 랜덤 연장 반영됨)
→ 전체 경매 중 이 값이 가장 이른 것 하나를 골라 타이머를 건다
```

타이머 발동 시, 대상 경매의 **현재 상태를 다시 확인**해 분기한다.

- **OPEN이었다면** → `ENDING` 전환 + `closeTime += random(1~2분)` +
  `estimatedCloseTime`을 전환 시점 값(연장 전)으로 얼림 + SSE로
  `AUCTION_ENDING_STARTED` 발행 → `scheduleNext()` 재호출(이 경매의
  다음 타겟은 이제 자신의 새 `closeTime`이 되어 다른 경매들과 다시
  경합한다)
- **ENDING이었다면** → 기존 마감 처리(`AuctionCloseSchedulerProcessor`)
  그대로

**왜 이렇게 하나(왜 폴링이 아니라 이 방식인가):** 브레인스토밍 과정에서
정밀 타이머(A)와 폴링(B)을 비교했다. 폴링은 인덱스만 있으면 경매 수와
무관하게 저렴하지만(`WHERE status='OPEN' AND close_time <= ?` 형태의
단건 배치 쿼리), **폴링 주기만큼 "이미 5분 안쪽인데 아직 ENDING 안 뜬"
지연이 구조적으로 생긴다**(예: 60초 주기면 최악 59초 지연). 반면 기존
스케줄러 확장은 사실상 추가 상시 비용이 거의 없으면서(이벤트 드리븐,
평소엔 유휴) 지연도 없다. 실제 마감 타이밍(진짜 `closeTime`)은 이 설계와
무관하게 기존 정밀 스케줄러가 이미 보장하고 있었으므로, 이번 변경은
그 메커니즘을 하나 더 재사용하는 것뿐이다.

**백업 안전망:** `AuctionClosingScheduler`(60초 폴러)에도 "지난
ENDING-진입-시각인데 아직 `OPEN`인 경매"를 찾아 처리하는 로직을
추가한다 — 정밀 타이머가 재시작·장애 등으로 놓쳤을 경우를 대비한
기존과 동일한 이중 안전장치 패턴이다.

## 4. 마스킹 — 신규 필드 없이 `estimatedCloseTime` 재활용

`estimatedCloseTime`은 이미 존재하는 컬럼이며, 현재는 `closeTime`과
항상 동일한 값으로 같이 갱신된다. 이번 변경으로 **갱신 시점만** 바뀐다.

- OPEN 상태: 지금처럼 `closeTime`과 계속 동기화(변경 없음)
- ENDING 진입 순간: 그 시점 값으로 **얼리고 이후 갱신 중단**
- `closeTime`은 내부적으로 계속 정확한 진짜 마감시각을 유지(랜덤 연장
  반영), 스케줄러의 실제 마감 처리에만 쓰이고 고객 응답에는 절대
  노출하지 않는다

**새 필드를 만들지 않는 이유:** 처음에는 `publicCloseTime` 같은 신규
필드를 검토했으나, 확인 결과 고객향 응답(`ends_at`)에 실제로 쓰이는
값과 `estimatedCloseTime`의 기존 소비처가 아래처럼 충돌하지 않는다는
것을 확인했다.

| 소비처 | 현재 사용 필드 | 영향 |
|---|---|---|
| `AuctionQueryService`/`AuctionCommandService`의 `ends_at`(목록/상세) | `closeTime` (직접) | **변경 필요** — `estimatedCloseTime`으로 소스 교체 |
| `AuctionStreamPayload.endsAt`(SSE) | `event.closeTime()` (직접) | **변경 필요** — 동일하게 교체 |
| `DashboardService.snapshot()`의 `ends_at`(`/api/dashboard`) | 이미 `estimatedCloseTime` | 변경 불필요 |
| `AuctionInsightQueryRepository.aggregateOpenAuctionInsight()` | `estimatedCloseTime`, 단 `status='OPEN'` 필터가 선행 | 영향 없음(ENDING 경매는 이 쿼리에 애초에 안 들어옴) |
| `DashboardService` 정렬(`ENDING_SOON`) | `estimatedCloseTime` | 얼린 값 기준으로 정렬됨(의도된 동작 — 실제 랜덤 연장분은 정렬에서도 숨겨짐) |

즉 **`ends_at`을 채우는 모든 응답 조립 코드가 `closeTime` 대신
`estimatedCloseTime`을 보게만 바꾸면**, 상태별 분기(`if ENDING then...`)
없이 하나의 필드로 통일된다 — OPEN일 땐 어차피 두 값이 같고, ENDING일
땐 자동으로 얼린 값이 나간다.

## 5. SSE 브로드캐스트

기존 패턴(`AuctionStreamPublisher.publish(AuctionStreamPayload.xxx(...))`,
`AUCTION_CREATED`/`BID_PLACED`/`AUCTION_CLOSED`)에 `AUCTION_ENDING_STARTED`
타입을 추가한다. 스케줄러가 ENDING 전환을 처리하는 지점에서 발행하며,
`endsAt` 필드엔 얼린 `estimatedCloseTime`을 담아 SSE로도 진짜 마감시각이
새지 않게 한다.

## 6. 프론트엔드

`useCountdown`/`AuctionCatalog`/`AuctionDetailPage`가 지금은 `status`와
무관하게 `endsAt` 기반 HH:MM:SS 카운트다운을 그린다(`isAuctionEnded`만
`OPEN`/`ENDING` 여부를 본다). `status === 'ENDING'`이면 카운트다운 계산
자체를 멈추고 정적 "마감임박" 표시로 바꾼다. 백엔드가 이미 얼린 값만
내려주므로 프론트가 실수로 정확한 시간을 렌더링해도 큰 문제는 없지만,
그래도 카운트다운을 안 그리는 게 의도를 명확히 한다.

## 7. 제외 — Redis 경로

`redis` 프로필에서는 구조가 완전히 다르다.

- `AuctionDeadlineScheduler`가 `isRedisProfile()` 체크로 아예 안 돎
  (`scheduleOnStartup`/`reschedule` 둘 다 조용히 return)
- 마감 처리 자체가 60초 백업 폴러(`AuctionClosingScheduler` +
  `RedisAuctionCloseSchedulerProcessor`) 하나로만 이뤄짐 — 정밀 타이머가
  원래 없음
- 5분 반복 연장 로직이 `bid-accept.lua`(85~86줄, `closeTimeEpochMillis -
  300000` 하드코딩)에 Java와 별개로 구현돼 있음
- `Auction.applyStreamBid()`가 스트림 리플레이 시 `closeTime`/
  `estimatedCloseTime`을 항상 같이 덮어써서, DB 경로의 "얼리기" 규칙을
  그대로 옮겨야 함

현재 별도로 Redis 쪽 데드라인 스케줄러 작업이 진행 중이므로, 그 작업이
정리된 뒤 별도 이슈로 이어서 설계한다. 개략적인 방향(폴링 기반 ENDING
판정, Redis 플래그로 단발 연장 보장, `applyStreamBid`에 얼리기 규칙
이식)은 이슈 #418 논의에 기록해뒀다.

## 8. 완료 조건

- OPEN 경매가 입찰 없이도 남은시간 5분 이하가 되면 자동으로 `ENDING`
  전환된다
- `ENDING` 전환 시 정확히 1회만 1~2분 사이 랜덤 값이 `closeTime`에
  더해지고, 이후 같은 경매에 입찰이 더 들어와도 추가 연장이 없다
- 목록/상세/대시보드 API와 SSE 페이로드 어디에도 ENDING 이후의 진짜
  `closeTime`(랜덤 연장 반영값)이 노출되지 않는다
- 정밀 스케줄러가 놓친 경우를 대비한 60초 백업 폴러 안전망이 ENDING
  전환에도 동작한다
- 기존 마감 처리·입찰 관련 테스트가 모두 통과한다

## 9. 남은 확인 사항 (구현 계획 단계에서 결정)

- 랜덤 연장 값의 정확한 분포(1~2분 사이 균등분포로 가정, 초 단위
  해상도까지 고려할지)
- `AuctionDeadlineScheduler`의 "다음 타겟" 계산 쿼리를 어떻게 짤지
  (상태별로 다른 정렬 키를 계산하는 단일 쿼리 vs 두 쿼리 중 더 이른 것
  선택)
- 기존 `AuctionCloseScheduleChangedEvent`를 ENDING 전환에도 그대로
  재사용할지, 별도 이벤트 타입이 필요한지
- 회귀 테스트 범위(스케줄러 단위 테스트, 마스킹 필드 단위 테스트,
  SSE 페이로드 테스트, 프론트 카운트다운 컴포넌트 테스트)

> 이 문서는 Claude의 도움을 받아 작성하였습니다
