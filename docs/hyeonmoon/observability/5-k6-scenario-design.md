# k6 부하테스트 시나리오 설계

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** (1) 기존 k6 스크립트가 정책적 실패(400/409)를 `http_req_failed`와
자체 임계값에서 진짜 실패로 잘못 세는 문제를 고치고, (2) "시스템 한계 자체를
보는" 시나리오①과 "실제 트래픽 분포를 재현하는" 시나리오②를 새로 만든다.

**Architecture:** 기존 `auction-bid.js`/`final-auction-load.js`의
`responseCallback`과 판정 로직만 수정한다(Task 1). 시나리오①②는 새 파일로
추가한다(Task 2, 3).

## Task 1: 정책적 실패 처리 통일

**Files:**
- Modify: `backend/src/test/k6/bid/auction-bid.js`
- Modify: `backend/src/test/k6/final-auction-load.js`

**변경 내용(두 파일 동일 패턴 적용):**
- `responseCallback: http.expectedStatuses(201, 409)` → `http.expectedStatuses(201, 400, 409)`로
  변경. 400(최소가 미달)도 409(경합 충돌)와 동급의 "서버가 정책대로 응답한
  정상 케이스"로 취급한다.
- 5xx만 잡는 별도 Rate 메트릭(`bid_server_error`)을 추가하고, 기존
  `bid_accepted_or_contended`류 임계값을 5xx 기준으로 재정의한다 —
  "낙찰율"(201만) 자체는 정보성 지표로 남기되 pass/fail 임계값에서 뺀다
  (경합이 심하면 낙찰율은 원래 낮다 — 이건 실패가 아니다).
- `check()` 메시지도 "성공 또는 경쟁 충돌"에서 "서버가 정책대로 응답함(성공/가격경합/동시입찰충돌)"으로
  갱신해 400 포함을 명시한다.

**검증:**
- [ ] 두 파일 다 반영 후, 로컬에서 짧게 실행해 `http_req_failed`가 400
      비율만큼 부풀지 않는지 확인
- [ ] `bid_server_error` 임계값(`rate<0.01`)이 5xx 발생 여부로만 흔들리는지 확인

## Task 2: 시나리오① 순수 처리량 테스트

**Files:**
- Add: `backend/src/test/k6/scenarios/pure-throughput.js`

**설계(확정치):**
- **축 1 — SSE 동시 연결 수:** 250 / 500 / 1000, **3개 티어를 독립 실행**
  (한 실행 안에서 연결 수를 바꾸지 않는다 — GC/스왑 등 이전 단계 잔여효과가
  다음 단계에 섞이는 걸 막기 위해). `SSE_VUS` 환경변수로 티어를 넘긴다.
- **축 2 — QPS 계단식 램프:** 각 티어 안에서 동일하게 50→100→150→200→300→400 req/s,
  단계별 1~2분 유지(`ramping-arrival-rate`).
- **트래픽 구성비:** bid-context 조회 : 입찰 POST : 일반조회 = 4:2:4.
- **SLO 판정:** [`1-slo-error-budget.md`](1-slo-error-budget.md)의 카테고리별
  목표를 그대로 threshold로 사용(하나로 뭉뚱그린 200ms/500ms 쓰지 않는다).
  입찰 쓰기는 상태코드별(201/400/409/500)로 나눠서 본다(같은 문서 참고).
- **실행 순서:** 250 → (텀) → 500 → (텀) → 1000. 텀은 이전 실행의 스왑/GC가
  가라앉을 시간(수 분)을 둔다.
- **산출물:** 3개 티어의 latency-vs-QPS 곡선을 나란히 비교. 1000 티어
  곡선이 낮은 QPS에서부터 꺾이면 "연결 수(=SSE fan-out 비용)"가 원인,
  티어 간 곡선이 비슷하면 "순수 처리량"이 원인으로 판정한다.

## Task 3: 시나리오② 실사용 패턴 테스트

**Files:**
- Add: `backend/src/test/k6/scenarios/hot-auction-pattern.js`

**설계(확정치):**
- 경매 200개 동시 오픈(seed 데이터 또는 setup에서 생성/조회).
- 핫 경매 2~3개: 경매당 20~40 req/s(실측 `dbidding_bid_step_duration_seconds{step="hold"}` p95=350ms
  기준, 단일 행이 감당 가능한 처리량은 초당 3~10건 — 이 범위면 큐가 확실히
  밀리는 스트레스 구간이 관찰된다. 50 이상은 전부 타임아웃돼 곡선을 못 봄).
- 비핫 경매(나머지 ~197개): 경매당 0.15 req/s(약 7초에 1번) — 합산
  약 30 req/s로, 핫 경매 합(60~120 req/s)이 전체의 70~80%를 차지하게
  맞춘 파레토 분포.
- 사용자 1,000명 접속, 전원 SSE(auction+notification) 연결 유지.
- **특별히 확인할 것:** 지금 `AuctionSseConnectionManager.broadcast()`는
  auctionId 구분 없이 전체 연결에 방송한다([`../realtime/1-sse-architecture.md`](../realtime/1-sse-architecture.md)에
  "트래픽 문제가 실제로 발생하면 그때 보이는 항목만 구독"하기로 명시돼
  있음) — 이 시나리오가 그 트리거 조건에 해당하는지, 즉 핫 경매 이벤트가
  비핫 경매 시청자에게까지 불필요하게 fan-out되는 비용이 실측으로 유의미한지
  확인한다. 유의미하면 별도 이슈로 auctionId 스코프 분리를 제안한다.

## Task 4: 실행 전 체크리스트

- [ ] Task 1(정책적 실패 처리) 먼저 병합돼 있어야 시나리오①②의 실패율
      숫자를 신뢰할 수 있다.
- [ ] `waitForSse` 류 barrier가 VU 1개만 폴링하는 구조인지 확인(이미
      `final-auction-load.js`에는 반영돼 있음 — 신규 시나리오 파일에도
      동일하게 적용).
- [ ] `SERVER_TOMCAT_MAX_CONNECTIONS`가 SSE 목표 연결수의 2배(연결
      2종 × 유저수) 이상으로 설정돼 있는지 확인.

## 완료 기준

- [ ] Task 1 두 파일 반영, 짧은 실행으로 검증
- [ ] `pure-throughput.js`, `hot-auction-pattern.js` 작성 완료
- [ ] 두 시나리오 모두 로컬/스테이징에서 최소 1회 정상 종료(크래시 없이
      `handleSummary` 출력까지 도달) 확인
- [ ] 이후 [`4-redis-baseline-comparison.md`](4-redis-baseline-comparison.md)의
      기준선 측정으로 연결

> 이 문서는 Claude의 도움을 받아 작성하였습니다
