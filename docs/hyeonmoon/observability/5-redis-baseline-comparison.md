# Redis 도입 전 기준선(Baseline)

**배경:** Redis는 캐시 설정(`chore/292-redis-cache-configuration`)만 들어가
있고, 입찰 경합/지갑 hold 로직을 Redis로 옮기는 작업은 아직 시작 전이다.
전환 효과를 객관적으로 비교하려면 전환 전 상태를 **재현 가능한 조건으로**
측정해서 남겨야 한다.

**왜 organic 트래픽 수치를 기준선으로 안 쓰나:** 그 수치는 그 시점에
우연히 섞여 있던 트래픽의 평균이라 재현이 안 된다. 같은 조건으로 다시 못
돌리면 "Redis 덕분에 좋아졌다"인지 "그날 트래픽이 가벼웠다"인지 구분이 안
된다. 그래서 기준선은 반드시 [`1-k6-scenario-design.md`](1-k6-scenario-design.md)의
**통제된 시나리오**를 돌려서 나온 결과로 잡는다.

**왜 제일 마지막에 재나:** DB/락 지연은 지금도 잴 수 있고(`docs/eunki`
계획으로 이미 계측돼 있음), SSE 연결 수립/전달 지연은 [`3-metrics-gap-and-instrumentation.md`](3-metrics-gap-and-instrumentation.md)가
끝나야 잴 수 있다. 이 둘을 따로 두 번 측정할 이유가 없으므로, **#334와
#331이 모두 `dev`에 병합된 뒤 한 번만** 전체 기준선을 잡는다.

## 선행 조건

1. #334(`1-k6-scenario-design.md`, `2-slo-error-budget.md`) 병합 완료.
2. #331(`3-metrics-gap-and-instrumentation.md`, `4-grafana-dashboard-redesign.md`)
   병합 및 배포 완료 — SSE 연결 수립 시간, 이벤트 end-to-end 전달 지연까지
   기준선에 포함하려면 필요.

이 문서는 위 선행 작업이 끝나기 전까지는 절차만 정의하고, 실제 수치는 비워
둔다.

## 측정 대상과 메트릭

| 메트릭 | 의미 | 출처 |
|---|---|---|
| `dbidding_auction_lock_wait_seconds` | 경매 row 락 대기 시간 | 기존(`docs/eunki`) |
| `dbidding_wallet_lock_wait_seconds` | 지갑 row 락 대기 시간 | 기존 |
| `dbidding_bid_critical_section_duration_seconds` | 락 획득 이후 임계구역 전체 | 기존 |
| `dbidding_bid_db_flush_duration_seconds` | 실제 DB flush(커밋) 시간 | 기존 |
| `dbidding_bid_step_duration_seconds{step="hold"\|"outbid"\|"save"}` | 입찰 처리 단계별 시간 | 기존 |
| `mysql_global_status_innodb_row_lock_time_avg`/`_max` | MySQL 서버 쪽 교차검증 | 기존 |
| [`2-slo-error-budget.md`](2-slo-error-budget.md)의 카테고리별 p95/p99(상태코드별 포함) | 엔드포인트 레이턴시 전반 | 기존 |
| SSE 연결 수(auction/notification) | 동시 연결 수 | #331 신규 |
| SSE 연결 수립 시간 | 연결→최초 이벤트까지 | #331 신규 |
| SSE 이벤트 end-to-end 전달 지연 | 발행~클라 수신 | #331 신규(k6 계측 이식) |
| `dbidding.sse.broadcast.rejected_total` | 브로드캐스트 묵시적 실패(드랍) | #331 신규 |

2026-08-10 organic 트래픽 기준 참고치(정식 기준선 아님, 방향성 참고용):
`auction_lock_wait` p95 234ms, `wallet_lock_wait` p95 46ms,
`bid_step{hold}` p95 350ms(가장 무거운 단계), `bid_step{outbid}` p95 176ms,
`bid_step{save}` p95 33ms. Redis 전환 시 `hold`(지갑 홀드)와
`auction_lock_wait`가 우선 타겟이 될 근거로 참고한다.

## 절차

1. k6 시나리오 ①(순수 처리량)과 ②(실사용 패턴)를 각각 1회 실행한다.
2. 각 실행의 k6 `handleSummary` JSON 결과를 `baseline-pre-redis-<시나리오>-<YYYYMMDD>.json`
   이름으로 보관한다.
3. 같은 실행 시간 구간으로 위 표의 Prometheus 메트릭(기존+신규 전부)을
   쿼리해 p50/p95/p99 스냅샷을 같은 이름 규칙의 마크다운/CSV로 남긴다.
4. 이 문서 하단 "기준선 기록"에 결과 표를 추가한다.
5. Redis 전환 완료 후 **동일한 시나리오**로 재실행하고, 같은 형식으로
   `post-redis-*` 결과를 남겨 나란히 비교한다.

## 기준선 기록

> #334, #331이 모두 병합된 뒤 시나리오 ①②를 실행하고 이 표를 채운다.

### 시나리오 ① 순수 처리량 (SSE 250/500/1000 × QPS 램프)

| SSE 연결수 | QPS 단계 | p95 | p99 | 5xx율 | 정책적 실패율 | SSE 연결 수립 p95 |
|---|---|---|---|---|---|---|
| (미실행) | | | | | | |

### 시나리오 ② 실사용 패턴 (경매 200개, 핫 2~3개)

| 지표 | 값 |
|---|---|
| (미실행) | |

> 이 문서는 Claude의 도움을 받아 작성하였습니다
