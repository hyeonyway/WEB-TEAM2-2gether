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

## 7. 관측성 — 신규 카운터

기존 `AuctionMetrics`에 이미 `dbidding.auction.extensions`(마감 임박 입찰로
연장된 횟수) 카운터가 있다. 이번 변경으로 "연장"의 의미 자체가 바뀌므로
(입찰 트리거 반복 연장 → 시간 트리거 단발 연장), 이 카운터를 재해석하기보다
**신규 카운터를 하나 추가**한다.

- `dbidding.auction.ending.transitions` — ENDING 전환(=단발 랜덤 연장 적용)이
  발생한 횟수. 스케줄러가 OPEN→ENDING 처리하는 지점에서 1 증가.

이걸로 Grafana에서 "ENDING 전환이 얼마나 자주 일어나는지", 그리고 바로
아래(7.1절)에서 감수하기로 한 리스크(정밀 스케줄러가 5분 넘게 밀려서
ENDING을 못 거치고 바로 마감되는 경우)가 실제로 얼마나 발생하는지
나중에 간접적으로 확인할 수 있다(마감 건수 대비 ENDING 전환 건수 비율이
비정상적으로 낮아지면 감지 가능).

## 7.1 알려진 리스크 (감수하기로 결정)

정밀 스케줄러와 60초 백업 폴러 둘 다 **같은 JVM 안에서 도는 작업이라, GC
Full-GC 정지 같은 JVM 전체 정지 상황엔 똑같이 무력하다**(오늘 부하테스트로
실측한 최악 케이스: Full GC 1회당 최대 76초 정지). 이 정지가 마침 어떤
경매의 ENDING 진입 시각과 겹치면, 그 경매는 ENDING을 거치지 못하고 원래
`closeTime`이 그대로 지나가버릴 수 있다.

이 경우 기존 마감 처리 쿼리(`findDueAuctionIds`, `status in (OPEN,
ENDING)`)가 `OPEN` 상태도 이미 포함하고 있어서 **그냥 곧장 마감 처리됨**
(무한정 안 닫히거나 이상하게 두 번 연장되는 버그는 없음). 다만 **그 경매에
마감 직전 입찰이 있었다면, 원래 약속된 "1~2분은 더 버텨준다"는 공정성
보장을 못 받고 조용히 닫히는 셈**이다. 이건 진짜 시스템 장애급 상황(GC
튜닝이 해결해야 할 별개 이슈)에서만 발생하므로, **이번 기능 범위에서
별도로 방어하지 않고 감수하기로 결정했다.** 7장의 신규 카운터로 실제
발생 빈도만 관측한다.

## 8. 제외 — Redis 경로

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

## 9. 완료 조건

- OPEN 경매가 입찰 없이도 남은시간 5분 이하가 되면 자동으로 `ENDING`
  전환된다
- `ENDING` 전환 시 정확히 1회만 1~2분 사이 랜덤 값이 `closeTime`에
  더해지고, 이후 같은 경매에 입찰이 더 들어와도 추가 연장이 없다
- 진행 중 경매를 나타내는 목록/상세/대시보드 API와 SSE의 `endsAt` 어디에도
  ENDING 이후의 진짜 `closeTime`(랜덤 연장 반영값)이 노출되지 않는다. 종료 후
  `closedAt`은 종료 사실을 기록하는 실제 시각으로 유지한다.
- 정밀 스케줄러가 놓친 경우를 대비한 60초 백업 폴러 안전망이 ENDING
  전환에도 동작한다
- 기존 마감 처리·입찰 관련 테스트가 모두 통과한다

## 10. 확정된 결정 사항 (브레인스토밍 중 정리됨)

- **랜덤 연장 분포**: 1~2분 사이 균등분포로 확정
- **"다음 타겟" 계산 쿼리**: 단일 쿼리(CASE 계산식)가 아니라 **"OPEN 중
  가장 이른 것" + "ENDING 중 가장 이른 것" 두 쿼리를 각각 뽑아 Java에서
  비교**하는 방식으로 확정 — JPQL 그대로 재사용 가능하고 코드가 더
  단순함. 리스케줄 자체가 자주 일어나지 않아 쿼리 2번의 비용은 무시 가능
- **`AuctionCloseScheduleChangedEvent` 재사용**: 별도 이벤트 타입 안 만들고
  기존 이벤트를 ENDING 전환 시에도 그대로 발행하는 걸로 확정(의미가
  "closeTime이 바뀌었다"로 정확히 일치함)
- **정밀 스케줄러의 JVM 정지(Full GC 등) 취약성**: 백업 폴러도 같은 JVM
  안에서 돌아 동일하게 취약함을 확인, 이번 기능 범위에서 별도 방어
  없이 감수하기로 결정(7.1절)

## 11. 회귀 테스트 범위 (확정)

이번 변경은 시간·상태·공개 응답이 함께 바뀌므로, 단위 테스트 하나로는
충분하지 않다. 아래 네 경계를 최소 회귀 범위로 둔다. 테스트에서 랜덤값이나
현재 시각을 실제 값에 의존시키지 않는다. `Clock`과 1~2분 연장값 공급자는
주입하거나 고정해, 1분·2분 경계값을 재현 가능하게 검증한다.

### 11.1 도메인 규칙 — `AuctionTest`

기존의 “마감 직전 입찰마다 5분 연장” 테스트는 삭제하거나 다음 규칙으로
교체한다.

- `OPEN` 경매가 ENDING 진입 시각에 도달하면 상태가 `ENDING`으로 바뀌고,
  `estimatedCloseTime`은 **연장 전** `closeTime`으로 고정된다.
- 실제 `closeTime`은 주입한 랜덤값만큼 한 번 늘어난다. 1분과 2분 모두를
  경계값으로 검증한다.
- 이미 `ENDING`인 경매를 다시 처리해도 상태·두 시각 모두 변하지 않는다.
  따라서 중복 스케줄 실행이나 백업 폴러와 정밀 타이머의 경합이 두 번째
  연장을 만들지 못한다.
- `OPEN`과 `ENDING`의 일반 입찰은 현재가·입찰 수만 바꾸며, `closeTime`과
  `estimatedCloseTime`을 바꾸지 않는다. 특히 ENDING 중 입찰이 과거의 반복
  연장 규칙을 되살리지 않는지를 검증한다.
- 기존의 종료 허용 범위(`OPEN`/`ENDING`만 종료 가능)와 종료 시각 이후 입찰
  거부 테스트는 유지한다.

도메인 단위 테스트는 JPA나 스케줄러를 띄우지 않는다. 단발성 보장의 중심은
상태 전이 메서드 자체이므로, 여기서 먼저 고정한다.

### 11.2 정밀 타이머와 백업 폴러 — `AuctionDeadlineSchedulerTest`,
`AuctionClosingSchedulerTest` 및 종료 처리 서비스 테스트

정밀 타이머는 “가장 이른 실제 마감”만 보던 기존 계약을 다음으로 바꾼다.

| 상황 | 예약/처리 대상 | 검증할 결과 |
| --- | --- | --- |
| OPEN의 `closeTime - 5분`이 가장 이르다 | ENDING 진입 시각 | 해당 시각에 실행되고, ENDING 전환 뒤 새 실제 `closeTime`으로 다시 예약 |
| ENDING의 `closeTime`이 가장 이르다 | 실제 마감 시각 | 기존 마감 처리만 수행 |
| OPEN 전환 시각과 ENDING 실제 마감이 경쟁 | 두 조회 결과 비교 | 더 이른 한 건만 예약 |
| 예약 콜백 실행 전 대상 상태가 바뀜 | 현재 DB 상태 재확인 | 이미 ENDING/종료된 경매를 다시 연장하지 않고, 닫힐 시각 전에는 조기 종료하지 않음 |
| 다음 대상 없음·처리 예외 | 기존 롤링 타이머 경로 | 기존 예약 취소·`finally` 재예약 보장 유지 |

`AuctionCloseScheduleChangedEvent`의 AFTER_COMMIT 재예약 테스트는 유지하고,
ENDING 전환에서 발행한 이벤트도 커밋 뒤에만 재예약되는 케이스를 추가한다.
`redis` 프로필에서는 정밀 타이머가 계속 비활성이라는 기존 프로필 테스트도
유지한다.

60초 백업 폴러는 별도 안전망으로 다음 세 가지를 검증한다.

- ENDING 진입 시각은 지났지만 실제 `closeTime` 전인 OPEN 경매를 ENDING으로
  전환하고, 바로 종료 처리하지 않는다.
- 실제 `closeTime`이 지난 ENDING 경매는 기존대로 낙찰/유찰 처리한다.
- 실제 `closeTime`도 이미 지난 OPEN 경매는 7.1절의 감수한 장애 시나리오대로
  추가 연장 없이 바로 종료한다. 이 동작을 명시적으로 테스트해 “늦은 폴러가
  과거 경매를 되살리는” 회귀를 막는다.

스케줄러 테스트에는 실제 `@Scheduled` 대기나 `sleep`을 넣지 않는다. 기존
`CapturingTaskScheduler`·고정 `Clock`·mock processor를 사용하고, 상태 전환과
종료 처리는 서비스 단위에서 따로 검증한다.

### 11.3 공개 시간 마스킹과 SSE 계약 — `AuctionQueryServiceTest`,
`DashboardServiceTest`, `AuctionSseContractTest`

ENDING 상태의 테스트 fixture는 반드시 `estimatedCloseTime != closeTime`으로
만든다. 두 값이 같으면 실제 시각 유출 회귀를 잡지 못한다.

- 목록과 상세 DB 조회 응답의 `endsAt`은 `estimatedCloseTime`이어야 한다.
  OPEN에서는 두 값이 같아 기존 API 계약이 유지되는 것도 확인한다.
- 대시보드의 `estimatedCloseTime` 기반 표시·정렬은 그대로 유지된다. ENDING의
  랜덤 연장분이 대시보드 정렬이나 필터에 노출되지 않는지를 fixture로 검증한다.
- `AUCTION_ENDING_STARTED` SSE payload는 상태 `ENDING`과 얼린 `endsAt`만
  전송한다. 일반 입찰이 ENDING 상태에서 발행하는 SSE도 같은 얼린 `endsAt`을
  유지한다.
- 종료 뒤의 `closedAt`은 이미 종료 사실을 알리는 시각이므로 실제 종료 시각으로
  유지해도 스나이핑 정보가 아니다. 다만 **진행 중 경매를 나타내는** SSE의
  `endsAt`에는 실제 `closeTime`을 넣지 않는다고 계약을 한정한다.
- SSE enum/event name, snake_case 직렬화, 선택 구독 fan-out의 기존 계약 테스트는
  유지한다. 이번 변경 때문에 기존 구독 대상 제한이 느슨해지지 않도록 한다.

Redis 실시간 조회·Lua·stream projection은 8장에서 제외한 별도 경로다. 이 이슈의
마스킹 회귀 테스트도 DB 경로에 한정한다. Redis 응답의 `closeTime`까지 바꾸는
테스트를 섞어 “미구현 경로가 우연히 통과한 것처럼” 보이게 하지 않는다.

### 11.4 프론트 표시와 SSE 반영 — `useCountdown.test.ts`,
`AuctionCatalog.test.tsx`, `AuctionDetailPage.test.tsx`

- `OPEN`은 기존처럼 `endsAt` 기준 `HH:MM:SS` 카운트다운을 표시한다.
- `ENDING`은 `endsAt`이 미래여도 카운트다운을 계산·표시하지 않고 정적
  **“마감임박”** 문구를 표시한다. 이 상태를 경매 종료로 오인해 입찰 버튼을
  비활성화하지는 않는다.
- `ENDED`/`FAILED` 또는 시간이 지난 OPEN/ENDING은 기존의 “경매 종료” 처리와
  입찰 불가 상태를 유지한다.
- 목록과 상세에서 `AUCTION_ENDING_STARTED` SSE를 받으면 status와 `endsAt`이
  query cache에 반영되고, 즉시 “마감임박”으로 다시 렌더링된다. 상세의 현재가·
  최근 입찰 갱신은 이 이벤트 타입에서 일어나지 않아야 한다.

프론트 테스트는 실제 1초 타이머를 기다리지 않고 fake timer와 고정된 `endsAt`을
사용한다. 브라우저 E2E·실시간 Redis 연결은 이번 이슈의 필수 회귀 범위가 아니다.

### 11.5 실행 기준

구현 중에는 아래 대상 테스트를 먼저 실행한다. 변경된 scheduler API에 맞춰
테스트 클래스가 분리되면 동등한 패키지의 후속 클래스를 포함한다.

```bash
cd backend
./gradlew test --tests 'com.dbidding.auction.domain.AuctionTest' \
  --tests 'com.dbidding.auction.service.AuctionDeadlineSchedulerTest' \
  --tests 'com.dbidding.auction.service.AuctionDeadlineSchedulerTransactionTest' \
  --tests 'com.dbidding.auction.service.AuctionClosingSchedulerTest' \
  --tests 'com.dbidding.auction.service.AuctionDueClosingServiceTest' \
  --tests 'com.dbidding.auction.service.AuctionQueryServiceTest' \
  --tests 'com.dbidding.dashboard.DashboardServiceTest' \
  --tests 'com.dbidding.auction.sse.AuctionSseContractTest'

cd ../frontend
npm test -- --run src/hooks/useCountdown.test.ts \
  src/pages/auction/components/AuctionCatalog.test.tsx \
  src/pages/auction-detail/AuctionDetailPage.test.tsx
```

PR 직전에는 전체 백엔드 테스트와 프론트 typecheck를 실행한다. 프론트 전체 테스트의
기존 판매 이미지 업로드 테스트 정합성 문제는 #401의 별도 범위이며, 이 변경의
성공/실패 판단과 섞지 않는다.

> 이 문서는 codex의 도움을 받아 작성하였습니다
