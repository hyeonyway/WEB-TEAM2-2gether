# 6.4 SSE Fan-out 병목과 개선

## 배경

SSE는 단순히 연결만 유지하는 기능이 아니라, 입찰 이벤트가 발생할 때 여러 연결에 동시에 fan-out해야 하는 별도 성능 경로다. 초기 구조에서는 요청 처리 흐름과 SSE 전송 책임이 충분히 분리되지 않아 연결 수가 늘어날수록 일반 요청 latency까지 같이 나빠졌다.

## Broadcast Executor 포화

2차 테스트에서는 입찰 rate를 50으로 고정하고 SSE 사용자 수를 100 → 200 → 300 → 500으로 늘렸다. 사용자 1명당 경매 SSE·알림 SSE 두 채널을 유지하므로 500명이면 약 1,000개 이상의 장기 연결이 필요했다.

동일한 `500 VU / arrival-rate 50 / 3분` 조건으로 전용 executor 도입 전후를 비교했다.

<table header-row="true">
<tr>
<td>지표</td>
<td>Executor 적용 전</td>
<td>적용 후</td>
</tr>
<tr>
<td>k6 평균 duration</td>
<td>3.0s</td>
<td>229ms</td>
</tr>
<tr>
<td>p95</td>
<td>8.0s</td>
<td>789ms</td>
</tr>
<tr>
<td>p99</td>
<td>9.0s</td>
<td>2.0s</td>
</tr>
<tr>
<td>SSE 처리량</td>
<td>261/s</td>
<td>1,475/s</td>
</tr>
<tr>
<td>HTTP 요청률</td>
<td>45.9/s</td>
<td>62.5/s</td>
</tr>
<tr>
<td>System CPU</td>
<td>약 1.7%</td>
<td>약 57.9%</td>
</tr>
<tr>
<td>Executor active</td>
<td>없음</td>
<td>최대 3 / core 4</td>
</tr>
<tr>
<td>Executor queue</td>
<td>-</td>
<td>최대 382 / 2000</td>
</tr>
<tr>
<td>Rejected task</td>
<td>-</td>
<td>0</td>
</tr>
</table>

CPU 사용률이 크게 뛴 건 악화가 아니라, 기존엔 요청 스레드가 blocking socket write에 묶여 CPU를 제대로 못 썼다는 뜻으로 봤다.

> ⚠️ 이 테스트의 `http_req_duration`엔 REST 요청과 장시간 열린 SSE 요청이 섞여 있다. p95/p99 감소를 입찰 API latency 개선으로 단정하지 않는다.

연결 수 단계별로도 같은 패턴이 나왔다.

<table header-row="true">
<tr>
<td>사용자 수</td>
<td>Executor</td>
<td>관측 latency</td>
<td>해석</td>
</tr>
<tr>
<td>100</td>
<td>없음</td>
<td>약 118~165ms</td>
<td>낮은 연결 수에서는 안정</td>
</tr>
<tr>
<td>200</td>
<td>없음</td>
<td>약 172~543ms</td>
<td>연결 증가에 따라 지연 증가</td>
</tr>
<tr>
<td>300</td>
<td>없음</td>
<td>약 702ms~2s</td>
<td>Broadcast 비용 가시화</td>
</tr>
<tr>
<td>500</td>
<td>없음</td>
<td>약 8~9s</td>
<td>연결/전송 병목 심화</td>
</tr>
<tr>
<td>500</td>
<td>있음</td>
<td>약 789ms~2s</td>
<td>전용 Executor 후 개선</td>
</tr>
<tr>
<td>300</td>
<td>있음</td>
<td>약 245ms~2s</td>
<td>개선 구조 재검증</td>
</tr>
</table>

500명부터는 SSE 채널 수가 1,000개를 넘기면서 Nginx/Backend FD 제한과 Tomcat `max-connections=1000` 설정도 같이 걸렸다.

3차에서는 SSE 연결 수와 QPS를 분리하려고 250/500/1000 VU로 다시 돌렸다(이 라운드는 `pure-throughput.js`의 입찰가 고정 버그가 나중에 발견돼서 POST 관련 수치는 근거로 안 쓰고 SSE 연결 성공률·읽기 경로 latency만 참고한다).

<table header-row="true">
<tr>
<td>조건</td>
<td>실제 SSE 채널 수</td>
<td>http_req_failed</td>
<td>경매 SSE 성공</td>
<td>알림 SSE 성공</td>
<td>p95</td>
<td>p99</td>
</tr>
<tr>
<td>250 VU 1차</td>
<td>500</td>
<td>0.094%</td>
<td>64.4%</td>
<td>100%</td>
<td>1.56s</td>
<td>10.87s</td>
</tr>
<tr>
<td>250 VU 2차</td>
<td>500</td>
<td>1.575%</td>
<td>100%</td>
<td>74.4%</td>
<td>20.39s</td>
<td>31.51s</td>
</tr>
<tr>
<td>250 VU 재부팅 후</td>
<td>500</td>
<td>5.028%</td>
<td>100%</td>
<td>100%</td>
<td>20.29s</td>
<td>45.39s</td>
</tr>
<tr>
<td>500 VU</td>
<td>1,000</td>
<td>0.094%</td>
<td>100%</td>
<td>100%</td>
<td>11.52s</td>
<td>13.65s</td>
</tr>
<tr>
<td>1000 VU</td>
<td>2,000</td>
<td>21.013%</td>
<td>30.6%</td>
<td>43.7%</td>
<td>57.28s</td>
<td>60.00s</td>
</tr>
</table>

같은 250 VU에서도 편차가 커서 250~500을 안전 구간으로 단정하진 않는다. 다만 1000 VU에서는 중앙값·tail latency·연결 성공률이 전부 크게 나빠졌다. 연결 수 자체가 별개의 병목이라는 걸 이때 확인했다.

> 📷 **캡처 위치** — SSE 연결 수별 latency/CPU 변화와 executor 도입 전후 비교 그래프.

SSE와 DB Lock이 서로 다른 축인지도 같은 50→400 QPS ramp로 검증했다.

<table header-row="true">
<tr>
<td>지표</td>
<td>SSE ON</td>
<td>SSE OFF</td>
</tr>
<tr>
<td>http_req_failed</td>
<td>3.638%</td>
<td>1.479%</td>
</tr>
<tr>
<td>Median</td>
<td>202ms</td>
<td>51ms</td>
</tr>
<tr>
<td>p95</td>
<td>21.49s</td>
<td>12.27s</td>
</tr>
<tr>
<td>p99</td>
<td>37.36s</td>
<td>17.75s</td>
</tr>
<tr>
<td>Max</td>
<td>60.01s</td>
<td>31.46s</td>
</tr>
</table>

SSE는 명확한 악화 요인이지만 꺼도 높은 latency가 남았다 — SSE와 DB Lock은 서로 다른 병목 축이었다.

## 전체 Broadcast 구조의 문제

초기 구조는 입찰 이벤트 하나가 발생할 때 요청 스레드 또는 이벤트 발행 스레드가 연결된 모든 `SseEmitter`를 순회하며 socket write를 했다.

```plain text
입찰 이벤트 발생
↓
다수 SSE 연결 순회
↓
Blocking Write / 느린 Client 발생
↓
HOL Blocking
↓
요청 Thread 또는 Broadcast Thread 체류 증가
↓
HTTP latency 악화
```

전용 executor로 책임을 분리하면 요청 스레드는 실제 전송이 아니라 작업 제출까지만 한다. 다만 구조에 따라 trade-off가 갈렸다.

- 연결별 Runnable: 병렬 전송 가능, HOL 완화 / 대신 이벤트당 Runnable N개와 큐 작업이 늘어남
- 하나의 Broadcast Task: 작업 생성 비용 감소 / 대신 느린 연결 하나가 뒤 연결을 막을 수 있음

## 선택 구독 적용

여기까지의 개선(전용 executor)은 fan-out 자체를 줄이지 않고 "빠르게 처리"하는 방향이었다. 경매 A의 이벤트가 경매 A를 보지 않는 연결에까지 나가는 구조 자체가 문제였다.

그래서 공개 경매 SSE를 전역 broadcast에서 `auctionIds` 선택 구독으로 바꿨다. 핵심 변경:

- `GET /api/auctions/stream?auctionIds=42,57,81`처럼 구독할 경매 ID 집합을 쿼리로 받는다. 상세 화면은 ID 1개, 목록·검색 화면은 viewport에 보이는 카드와 앞뒤 여유분을 합쳐 최대 16개까지만 구독한다.
- 서버는 `Set<SseEmitter>` 단일 목록 대신 경매 ID별 emitter 인덱스를 유지해서 이벤트 발생 시 그 경매를 구독 중인 연결에만 전송한다.
- `EventSource`는 연결 후 임의 메시지를 보낼 수 없어서 구독 대상이 바뀌면 별도 subscribe API 대신 기존 연결을 닫고 새 query로 재연결하는 방식을 썼다. 서버에 연결 상태 변경 API를 따로 두면 SSE 단방향 모델을 깨고 정리·경합 처리가 복잡해지기 때문이다.
- 로그인 사용자의 지갑 잔액/홀드 변화는 화면이 그 경매를 구독하는지와 무관하게 별도 개인화 SSE로 전달해서 지갑 갱신 때문에 전역 경매 이벤트를 다 받을 필요를 없앴다.
- Redis Pub/Sub 기반 다중 인스턴스 릴레이와 SSE payload 형식은 그대로 유지했다.

## 개선 전후 SSE 연결 성공률

이 변경은 전용 executor 개선과 달리, 단독으로 분리해서 재현한 k6 before/after 세션이 따로 없다 — 설계·구현 단계에서 기능/구조 검증으로 확인했고 정량적인 재현은 9차 최종 테스트의 SSE 시나리오에 통합됐다.

**9차 최종 테스트 결과(사후 반영):** 선택 구독 + Redis 전환 이후 pure-throughput 250/500/1000-tier, hot-auction-pattern 전 시나리오에서 경매·알림 SSE 연결 성공률이 100%로 유지됐다. hot-auction-pattern의 bid-context p95도 8차의 898~954ms에서 111~139ms로 줄었다 — 다만 이 구간은 선택 구독 단독 효과가 아니라 6.6의 Redis 전환과 함께 나온 결과라, 두 변경을 분리해서 기여도를 나누진 않는다.

## 결론

확인된 사실:

- SSE는 별도의 성능 예산이 필요한 핵심 경로다.
- 연결 수 증가와 Broadcast Fan-out 모두 Backend latency에 영향을 준다.
- 전용 Executor는 요청 처리와 전송 책임을 분리하는 데 효과가 있었다.
- 선택 구독으로 전역 broadcast 자체를 없애서 fan-out 비용이 "연결 수 × 전체 이벤트 수"에서 "그 경매를 보는 연결 수 × 그 경매 이벤트 수"로 줄었다.
- SSE를 제거해도 Hot Auction DB 병목은 남는다 — SSE와 DB Lock은 계속 독립적으로 다뤄야 한다.

> 이 문서는 codex의 도움을 받아 작성하였습니다

<!-- HUMANIZE-SUMMARY v1.6.1
run_id: 2026-08-16-003
genre: 리포트 (팀 기술 위키 / 부하테스트 회고)
metrics:
  char_in: 약 5,300 (표·코드블록·마크업 포함, 약 60%가 보존 대상)
  char_out: 약 5,280
  change_rate: 약 1.6% (전체 기준) / 약 4.5% (산문 구간 기준)
  self_check: 6/6
  grade: A
categories:  # before → after
  C-11 연결어미 뒤 쉼표(-아서/-어서/-고/-지만 직후): 8 → 0
  D-3 '본질적으로/근본적으로' 계열: 1 → 0
  E-2 '~고 있다' 진행형 과잉: 1 → 0
  E-1 장문 리듬 균일: 1건 문장 분리로 완화
  A-18 좌향 관형 수식: 2 → 2 (기술 서술상 필요 — 미개입)
  J-1/J-3 굵게·불릿: 미개입 (위키 라벨·trade-off 대조표, 보존 지시 준수)
self_check:
  - 고유명사·수치·인용 100% 보존: ✅ (표·수치·%·ms·VU·코드 백틱·HTML table·plain text 블록 전부 원형)
  - 변경률 30% 이하: ✅
  - 장르 이탈 없음: ✅ (기술 회고 리포트 유지)
  - register 보존: ✅ (평서 '~다' 문어체 유지)
  - S1 잔존 0건: ✅
  - 인공 표현 추가 없음: ✅ (어휘 삭제·쉼표 제거·문장 분리만, 신규 비유 0)
preserved_verbatim:
  - HTML <table> 4개 (총 30행) 전부 무수정
  - ```plain text``` 다이어그램 블록 무수정
  - "> ⚠️" / "> 📷" 콜아웃 마커 및 본문 무수정
  - 인라인 코드(`http_req_duration`, `max-connections=1000`, `SseEmitter`, `EventSource`, `pure-throughput.js`, GET 엔드포인트 등) 무수정
  - 하단 "> 이 문서는 codex의 도움을 받아 작성하였습니다" 무수정
highlights:
  - id: C-11
    before: "요청 처리 흐름과 SSE 전송 책임이 충분히 분리되지 않아서, 연결 수가 늘어날수록"
    after: "요청 처리 흐름과 SSE 전송 책임이 충분히 분리되지 않아 연결 수가 늘어날수록"
  - id: E-2 + I-3
    before: "blocking socket write에 묶여서 CPU를 제대로 못 쓰고 있었다는 뜻으로 해석했다."
    after: "blocking socket write에 묶여 CPU를 제대로 못 썼다는 뜻으로 봤다."
  - id: D-3
    before: "근본적으로는 경매 A의 이벤트가 경매 A를 보지 않는 연결에까지 나가는 구조 자체가 문제였다."
    after: "경매 A의 이벤트가 경매 A를 보지 않는 연결에까지 나가는 구조 자체가 문제였다."
  - id: E-1 + C-11
    before: "연결 성공률이 전부 크게 나빠져서, 연결 수 자체가 별개의 병목이라는 걸 이때 확인했다."
    after: "연결 성공률이 전부 크게 나빠졌다. 연결 수 자체가 별개의 병목이라는 걸 이때 확인했다."
  - id: C-11
    before: "경매 ID별 emitter 인덱스를 유지해서, 이벤트 발생 시 그 경매를 구독 중인 연결에만 전송한다."
    after: "경매 ID별 emitter 인덱스를 유지해서 이벤트 발생 시 그 경매를 구독 중인 연결에만 전송한다."
residual_findings: (없음 — A-18 좌향 수식 2건은 기술 정확도 우선으로 의도적 미개입, S2 이하)
grade_reason: "A — S1 잔존 0건, 자체검증 6항 통과. 입력의 약 60%가 보존 지시 대상(표·다이어그램·콜아웃·코드)이라 전체 변경률이 A 기준(10~25%)보다 낮으나, 산문 구간만 보면 4.5%로 원문 품질이 이미 높았던 케이스. 수치·표·사실관계 무변경."
-->
