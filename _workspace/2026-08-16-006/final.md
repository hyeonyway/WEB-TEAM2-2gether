# 6.7 Redis Read Path 최적화

## 배경

[6.6](Performance-Redis-기반-입찰-처리-전환)에서 입찰 승인 자체는 Redis Lua로 옮겼지만 읽기 경로(입찰 이력 조회, Cold Miss 시 상태 복원, 목록 중복 조회, 목록 정렬)에도 병목이 따로 있었다. 이 페이지는 그 네 가지를 어떻게 풀었는지 정리한다.

## Bid Query Index

Hot Auction 테스트에서 같은 rate라도 누적 입찰 수가 적은 새 Auction은 안정적으로 처리되지만 수천 건의 Bid History가 쌓인 오래된 Auction에서는 HikariCP가 빠르게 포화됐다. Row Lock 자체뿐 아니라 Transaction 안에서 도는 Bid 조회 비용까지 Lock Hold Time을 늘렸다.

누적 Bid가 약 7천 건인 Auction에서 EXPLAIN ANALYZE를 확인해 보니 일부 조회는 많은 row를 읽고 filesort까지 했다.

<table header-row="true">
<tr>
<td>Query</td>
<td>Rows Examined</td>
<td>Latency</td>
<td>Filesort</td>
</tr>
<tr>
<td>최신 입찰 조회</td>
<td>7,080</td>
<td>7ms</td>
<td>발생</td>
</tr>
<tr>
<td>최고가/조건 조회</td>
<td>7,024</td>
<td>4ms</td>
<td>발생</td>
</tr>
<tr>
<td>상태 조건 조회</td>
<td>7,080</td>
<td>2ms</td>
<td>없음/낮음</td>
</tr>
</table>

기존엔 `auction_id` 단일 인덱스 중심이라 조건과 정렬을 한꺼번에 커버하지 못했다. 조회 패턴에 맞춰 Composite Index(`idx_bids_auction_status_amount`)를 추가했다.

<table header-row="true">
<tr>
<td>항목</td>
<td>Before</td>
<td>After</td>
</tr>
<tr>
<td>Query 1 Rows Examined</td>
<td>7,080</td>
<td>1</td>
</tr>
<tr>
<td>Query 2 Rows Examined</td>
<td>7,024</td>
<td>3</td>
</tr>
<tr>
<td>Query 3 Rows Examined</td>
<td>7,080</td>
<td>3</td>
</tr>
<tr>
<td>Filesort</td>
<td>발생</td>
<td>제거</td>
</tr>
</table>

같은 Auction·rate 50 조건에서 MySQL CPU는 1.84~1.97 core → 0.54 core(약 71~73% 감소), Hikari active는 30 → 2(약 93% 감소)까지 줄었다.

> 📷 **캡처 위치** — 인덱스 적용 전후 EXPLAIN 결과와 rows examined 비교.

## Cold Seed N+1

Redis를 실시간 상태 저장소로 쓰면 정상 경로는 빨라진다. 대신 Redis 재시작이나 Cache Miss 뒤에 여러 Auction 상태를 다시 읽어오는 Cold Seed 과정에서 DB 접근이 몰릴 수 있다. 초기 구현은 필요한 Auction마다 개별 조회가 일어나서 N+1 형태의 DB 접근이 생길 여지가 있었다.

SingleFlight를 같은 Auction ID에 걸면 동일 키의 중복 조회는 막을 수 있지만 서로 다른 Auction ID에서 Cold Miss가 동시에 나면 N+1 문제는 그대로 남는다. 그래서 Cold Seed 요청을 짧은 시간(약 5ms Window, 최대 200건) 동안 모아 Batch Query로 처리하는 Coordinator를 뒀다.

```plain text
Cold Miss 요청들
↓
Seed Coordinator
↓
짧은 Window 동안 Auction ID 수집
↓
Batch DB Query
↓
Redis Seed
↓
각 요청 결과 반환
```

> 📷 **캡처 위치** — Cold miss 수에 따른 Query 수/DB connection 사용량 전후 비교.

## Auction 목록 중복 조회

9차 최종 테스트 준비 중 목록(`GET /api/auctions`) p95가 상세·bid-context보다 10배 이상 느리다는 걸 발견했다(약 1,100~1,400ms vs 66~198ms). 원인은 두 겹이었다.

1. 목록 항목 하나를 만들 때 `AuctionQueryService.redisSummary()`가 bid-context급 전체 조회(`read()`)를 재사용했다. 이미 배치로 들고 있는 스냅샷을 다시 읽는 데다 응답에 쓰지도 않는 최근 입찰 내역(XREVRANGE)까지 매번 불렀다.
2. `fetchRedisSortedPage()`가 keyword/psaGrade 필터가 있든 없든 항상 고정 배치(50개)만큼 Redis 상태를 읽었다. 필터가 없으면 ZSET 정렬 결과가 그대로 답이라 여유분이 필요 없는데도.

size=20·필터 없음 기준 요청 1번당 Redis 왕복이 약 110회였다. 고칠 지점도 둘이었다.

- 목록 전용 경량 조회(`readMyBidSummary()`, HGETALL 1번)를 새로 만들어서 `redisSummary()`가 이걸 쓰게 바꿨다. 전체 조회는 실제로 최근 입찰 내역을 쓰는 상세/bid-context 경로에만 남겨뒀다.
- 필터가 없으면 배치 크기를 `limit`으로 줄이고 필터가 있을 때만 기존처럼 여유분(50) + 재시도를 유지했다. 필터 검색 자체의 확장성 문제는 범위 밖으로 남겨뒀다.

요청 1번당 Redis 왕복은 약 110회 → 약 40회로 줄었다. 9차 최종 테스트에서 목록 p95는 서버 실측 기준 약 220~630ms대로 내려왔다. 상세/bid-context(90~200ms대)보다는 여전히 2~4배 높은데, 항목당 Redis read가 20회 남아 있으니 구조적으로 당연한 결과다. 다만 수정 전의 8~10배 격차는 사라졌다.

## ZSET 기반 목록 최적화

Auction 목록은 단순 조회가 아니라 여러 정렬 기준(종료 임박순·입찰 수·현재 가격·가격 변동률·등록 시각)을 지원해야 했다. 기존엔 종료 시각 기준 ZSET에서 후보를 넓게 가져온 뒤 각 Auction Hash를 읽고 애플리케이션에서 다시 정렬/Cursor 계산을 했다.

그래서 정렬 기준별로 별도 ZSET(`auction:close-time`, `auction:bid-count`, `auction:price`, `auction:change-rate`, `auction:open-time`)을 유지하는 쪽으로 바꿨다. Auction 상태가 바뀌면 관련 Score도 같이 갱신한다. 여러 값이 동시에 바뀌는 입찰 경로에서는 Lua 스크립트로 Hash 상태와 Index 갱신을 하나의 Redis Atomic Operation으로 묶었다.

Redis ZSET Score는 Double 기반이라 모든 정렬 조건을 하나의 Composite Score에 억지로 넣으면 정밀도 문제가 생긴다. Cursor는 (1) 현재 Cursor Score까지 탐색 (2) 경계 Score와 같은 항목 추가 조회 (3) Auction ID를 tie-breaker로 최종 순서 결정, 이 3단계로 처리해서 Score가 같은 Auction이 페이지 경계에서 누락되거나 중복되는 걸 줄였다.

Keyword/PSA 여부/일부 Status 조합은 검색 빈도와 cardinality를 고려해 Redis Index로 만들지 않고 후보 조회 뒤 애플리케이션 레이어에서 처리한다. Index를 너무 늘리면 입찰/상태 변경 때 쓰기 비용과 메모리 사용량이 커지기 때문이다.

> 📷 **캡처 위치** — 전체 조회·정렬 방식과 ZSET 범위 조회 방식의 흐름 비교 다이어그램.

<table header-row="true">
<tr>
<td>항목</td>
<td>기존 구조</td>
<td>ZSET 구조(9차 실측)</td>
</tr>
<tr>
<td>목록 p95/p99</td>
<td>측정 안 함(ZSET 도입이 애초 설계였음)</td>
<td>220~630ms대(위 "Auction 목록 중복 조회" 절 참고)</td>
</tr>
<tr>
<td>Redis command 수</td>
<td>-</td>
<td>요청당 약 40회(중복조회 제거 후)</td>
</tr>
<tr>
<td>Backend CPU</td>
<td>-</td>
<td>ZSET 단독 효과로 분리 측정 안 함</td>
</tr>
<tr>
<td>DB Query</td>
<td>기존 DB/혼합 경로</td>
<td>목록 조회 경로에 DB 접근 없음(Redis 단독)</td>
</tr>
<tr>
<td>Cursor 정확성</td>
<td>기능 테스트</td>
<td>9차 부하 중 페이지네이션 관련 오류 없음</td>
</tr>
</table>

이 표의 "기존 구조" 열은 ZSET이 처음부터 목록 조회의 기본 설계였기 때문에 비교 대상 라운드가 따로 없다. Redis 도입 전(MySQL 기반) 목록 조회와 직접 비교하지도 않았다.

## 결론

네 가지 최적화는 모두 "정상 경로에서 필요한 최소한만 Redis에 묻는다"는 한 방향이었다. Bid Query Index와 Cold Seed Batch는 각각 DB Lock Hold Time과 재시딩 때 DB 부하를 줄였다. Auction 목록 중복 조회 제거와 ZSET 구조는 목록 조회 자체의 왕복 횟수를 줄였다. 전체 효과는 [6.8 9차 최종 부하 테스트](Performance-9차-최종-부하-테스트)에서 종합한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다

<!-- HUMANIZE-SUMMARY v1.6.1
run_id: 2026-08-16-006
genre: 리포트 (기술 위키)
metrics:
  char_in: 3298
  char_out: 3247
  prose_char_in: 2570
  change_rate: 13.4%
  self_check: 6/6
  grade: A
categories:  # before → after
  C-11 연결어미 뒤 쉼표: 8 → 0
  A-18 좌향 장수식·긴 복문: 3 → 0
  em dash 삽입절 반복: 4 → 1 (캡처 콜아웃 형식만 유지)
  J-1 본문 볼드+콜론 강조: 1 → 0
  A-10 "~할 수 있다" 남발: 4 → 2
  H-1 문두 "그래서" 반복: 3 → 2
  F-4 한자어투 "유무와 무관하게": 1 → 0
  중복 표현(애초에+처음부터 / 중복으로+다시): 2 → 0
preserved:  # 사용자 지정 보존 항목 전수 확인
  HTML table 3개: 원문 바이트 동일
  plain text 다이어그램 블록: 원문 동일
  "> 📷" 콜아웃 3개: 원문 동일
  위키 내부 링크 2개: 원문 동일
  백틱 코드/함수명 9개: 원문 동일
  수치·퍼센트·ms·core 값 전부: 원문 동일
  말미 codex 고지 문장: 원문 동일
self_check:
  - 고유명사·수치·인용 100% 보존: 통과
  - 변경률 30% 이하: 통과 (13.4%)
  - 장르 이탈 없음: 통과 (기술 리포트체 유지)
  - register 보존: 통과 (해라체 평서 유지)
  - S1 잔존 0건: 통과
  - 인공 표현 추가 없음: 통과
highlights:
  - id: C-11
    before: "입찰 승인 자체는 Redis Lua로 옮겼지만, 읽기 경로(...)에도 별도의 병목이 있었다"
    after: "입찰 승인 자체는 Redis Lua로 옮겼지만 읽기 경로(...)에도 병목이 따로 있었다"
  - id: A-15
    before: "오래된 Auction에서는 HikariCP가 빠르게 포화되는 차이가 나타났다"
    after: "오래된 Auction에서는 HikariCP가 빠르게 포화됐다"
  - id: J-1
    before: "**9차 최종 테스트 결과:** 목록 p95가 서버 실측 기준 약 220~630ms대로 내려왔다"
    after: "9차 최종 테스트에서 목록 p95는 서버 실측 기준 약 220~630ms대로 내려왔다"
  - id: F-4
    before: "keyword/psaGrade 필터 유무와 무관하게 항상 고정 배치(50개)만큼"
    after: "keyword/psaGrade 필터가 있든 없든 항상 고정 배치(50개)만큼"
  - id: 중복표현
    before: "애초에 ZSET이 처음부터 목록 조회의 기본 설계였기 때문에 별도 비교 대상 라운드가 없다"
    after: "ZSET이 처음부터 목록 조회의 기본 설계였기 때문에 비교 대상 라운드가 따로 없다"
residual_findings: (없음)
grade_reason: "A. S1 잔존 0건, 변경률 13.4%, 자체검증 6항 통과. 표·다이어그램·콜아웃·링크·백틱·수치 전량 원형 보존."
-->
