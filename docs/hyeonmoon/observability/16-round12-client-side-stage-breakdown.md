# 12차 부하테스트 — 클라이언트 측(k6) 스테이지별 breakdown

**대상 환경:** prod(`api.dbidding.shop`), blue-green 배포(`backend-green`, 8080).
11차(`15-round11-consumer-bottleneck-persists-503-storm-resolved.md`)와 인프라·배포
조건 동일 — 11차 이후 새로 머지되거나 재배포된 것 없음. 이번 세션 시작 전
`dbidding` DB를 `DROP DATABASE`/`CREATE DATABASE`로 초기화하고 Flyway로
V1~V10을 재적용한 뒤, `hot-auction-pattern.js`가 요구하는 즉시낙찰가 NULL
경매 200개 조건을 맞추기 위해 시드 풀(`3001001`~`3001300`) 중 즉시낙찰가가
들어간 200건을 다시 NULL로 되돌리고 대응하는 Redis `auction:state:*` 캐시
300개를 전부 삭제했다(11차 §2.3와 동일 절차).

**작성일:** 2026-08-20.

## 0. 이 문서의 범위 — 11차와의 차이

11차는 CLAUDE.md가 요구하는 "서버 실측(Prometheus histogram_quantile)이
권위 있는 수치"라는 원칙에 따라 API별·스테이지별 서버 측 p95/p99를
전수 조사했다. **이번 12차는 사용자가 명시적으로 요청한 대로 클라이언트
측(k6) 수치를 스테이지 단위로 쪼개는 것 자체가 목적**이다 — 서버 측
Prometheus 재조사(HikariCP/GC/Redis exporter 등)는 이번 문서 범위 밖이며,
그 기준선은 11차 문서를 참조한다. 즉 이 문서는 "11차가 이미 확인한 결론
위에 클라이언트 관점의 스테이지별 해상도를 추가하는" 보완 문서다.

### 측정 방법

- 6개 시나리오(11차와 동일 세트) 각각을 `k6 run`/`sse/k6-sse run`에
  `--out csv=<file>.csv.gz` 옵션을 추가해 실행했다 — k6가 기본 제공하는
  raw 메트릭 CSV 출력으로, `http_req_duration`을 포함한 모든 개별 요청의
  `(timestamp, scenario, name, metric_value)`를 초 단위 epoch로 기록한다.
- 각 실행 직전 `date +%s`로 시작 epoch를 기록해두고, 스크립트의 실제 스테이지
  구성(`backend/src/test/k6/scenarios/pure-throughput.js`,
  `bid-only-load.js`의 `qpsStageTargets` 기본값 `[50,100,150,200,300,400]`,
  스테이지당 `STAGE_DURATION` 기본 2분)과 CLAUDE.md/11차가 이미 검증한
  "메인 구간 시작 오프셋"(pure-throughput/bid-only-load: SSE 있으면 +36초,
  없으면 +6초, hot-auction-pattern: +70초)을 결합해 6개(또는 hot-auction의
  경우 1개) 스테이지 시간창을 계산했다.
- 스크립트(`stage_breakdown.py`, 세션 스크래치패드 보관, 저장소에는 커밋
  안 함)가 각 시간창에 속하는 `http_req_duration` 샘플을 `(scenario, name)`
  단위로 모아 p50/p95/p99/max를 선형보간 방식으로 직접 계산한다 — k6
  자체 요약(`summaryTrendStats`)이나 Prometheus 히스토그램 버킷 근사가
  아니라 **개별 요청 원본값에서 계산한 정확한 백분위수**다.
- 원본 CSV(`*-raw.csv.gz`, 시나리오당 6.6MB~55MB)와 스테이지별 표
  (`*-stage-breakdown.md`)는 전부 `backend/src/test/k6/result/`에 있다
  (§4 참조 목록).

---

## 1. 전체 실행 요약 (k6 클라이언트 집계) + 11차 비교

조건 동일(같은 인프라, DB만 리셋) — 표 아래 11차 대비 변화 없음 확인.

| 시나리오 | 라운드 | med(ms) | p95(ms) | p99(ms) | max(ms) | http_req_failed | bid_server_error |
|---|---|---|---|---|---|---|---|
| pure-throughput 250 | 11차 | 267.7 | 8905.8 | 9054.3 | 9255.8 | 0% | 0% |
| pure-throughput 250 | 12차 | 137.3 | 8281.2 | 8670.9 | 8874.9 | 0% | 0% |
| pure-throughput 500 | 11차 | 378.7 | 8910.0 | 9863.0 | 10017.6(min -2087, 계측버그) | 0% | 0% |
| pure-throughput 500 | 12차 | 300.6 | 8928.5 | 9256.0 | 9453.1 | 0% | 0% |
| pure-throughput 1000 | 11차 | 1257.6 | 7389.0 | 18508.3 | 60004.4 | 1.88% | 3.82% |
| pure-throughput 1000 | 12차 | 1089.2 | 8677.6 | 18366.4 | 60003.0 | 1.82% | 3.41% |
| hot-auction-pattern | 11차 | 96.3 | 119.4 | 228.0 | 753.6 | 0% | 0% |
| hot-auction-pattern | 12차 | 96.6 | 119.2 | 145.4 | 306.0 | 0% | 0% |
| bid-only-load(분산) | 11차 | 185.1 | 8835.1 | 8971.7 | 9157.0 | 0% | 0% |
| bid-only-load(분산) | 12차 | 151.1 | 8661.1 | 8800.4 | 8998.2 | 0% | 0% |
| bid-only-load(단일 핫옥션) | 11차 | 120.5 | 4380.2 | 6060.9 | 6464.7 | 0% | 0% |
| bid-only-load(단일 핫옥션) | 12차 | 114.6 | 5553.1 | 8936.4 | 9257.3 | 0% | 0% |

전반적으로 11차와 같은 자릿수·같은 붕괴 패턴이다. 유일하게 눈에 띄는 차이는
**bid-only-load(단일 핫옥션)의 p99/max가 11차(6.1s/6.5s)보다 12차(8.9s/9.3s)에서
더 나쁘다** — §2에서 스테이지별로 보면 이건 우연한 실행별 변동이 아니라
스테이지6(QPS 400)에서 분산 시나리오와 거의 같은 수준(p95 8.8s)까지
따라붙은 것으로, 표본 수가 적어(11차 대비 이번 실행의 후반 스테이지
누적 요청 수가 비슷) 단정하긴 이르다 — §5 한계 참고.

11차의 `http_req_duration.min=-2087ms`(SSE 계측 버그) 이번 회차엔 재현되지
않았다 — 6개 실행 전부 `min`이 양수로 정상 범위였다. 우연히 이번엔 그
타이밍 레이스가 안 걸린 것으로 보이며, 버그 자체가 고쳐졌다고 단정할
근거는 없다(코드 diff 확인 안 함, §5).

---

## 2. 스테이지별 breakdown — 전체 표

스테이지 = QPS 목표치, 2분씩: 1=50, 2=100, 3=150, 4=200, 5=300, 6=400
(`bidContextReads`/`bidWrites`/`generalReads` 세 하위 시나리오에
0.4/0.2/0.4 비율로 분배된 값). `n`=해당 스테이지·API 조합에서 실제
완료된 요청 수, p50/p95/p99/max는 개별 요청 원본값에서 선형보간으로
계산한 값(ms). hot-auction-pattern만 QPS 계단이 없어 스테이지가 1개다.

### 2.1 pure-throughput SSE_VUS=250

| stage | scenario | name | n | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| 1 | bidContextReads | GET /api/auctions/:id/bid-context | 2364 | 70.2 | 124.7 | 154.0 | 222.6 |
| 2 | bidContextReads | GET /api/auctions/:id/bid-context | 3564 | 84.6 | 98.6 | 112.8 | 273.3 |
| 3 | bidContextReads | GET /api/auctions/:id/bid-context | 5964 | 94.3 | 116.5 | 159.4 | 381.1 |
| 4 | bidContextReads | GET /api/auctions/:id/bid-context | 8364 | 100.5 | 153.3 | 200.3 | 344.3 |
| 5 | bidContextReads | GET /api/auctions/:id/bid-context | 11862 | 157.5 | 410.7 | 647.9 | 739.4 |
| 6 | bidContextReads | GET /api/auctions/:id/bid-context | 14320 | 2985.3 | 8535.3 | 8651.6 | 8725.3 |
| 1 | bidWrites | GET /api/auctions/:id/bid-context | 1182 | 73.5 | 129.5 | 157.9 | 209.5 |
| 2 | bidWrites | GET /api/auctions/:id/bid-context | 1782 | 84.9 | 98.4 | 113.3 | 247.7 |
| 3 | bidWrites | GET /api/auctions/:id/bid-context | 2982 | 95.1 | 117.2 | 159.9 | 381.0 |
| 4 | bidWrites | GET /api/auctions/:id/bid-context | 4182 | 100.8 | 154.2 | 199.7 | 352.9 |
| 5 | bidWrites | GET /api/auctions/:id/bid-context | 5932 | 158.1 | 416.1 | 649.8 | 720.5 |
| 6 | bidWrites | GET /api/auctions/:id/bid-context | 6918 | 2507.9 | 8557.7 | 8654.3 | 8712.4 |
| 1 | bidWrites | POST /api/auctions/:id/bids | 1181 | 72.8 | 99.6 | 122.1 | 166.4 |
| 2 | bidWrites | POST /api/auctions/:id/bids | 1781 | 75.6 | 95.6 | 108.6 | 222.8 |
| 3 | bidWrites | POST /api/auctions/:id/bids | 2981 | 89.5 | 110.6 | 137.7 | 330.5 |
| 4 | bidWrites | POST /api/auctions/:id/bids | 4182 | 94.0 | 139.6 | 178.8 | 349.2 |
| 5 | bidWrites | POST /api/auctions/:id/bids | 5900 | 141.1 | 364.7 | 612.4 | 724.6 |
| 6 | bidWrites | POST /api/auctions/:id/bids | 6425 | 2336.9 | 8518.2 | 8630.8 | 8693.7 |
| 1 | generalReads | GET /api/auctions | 1200 | 79.5 | 121.8 | 153.6 | 200.3 |
| 2 | generalReads | GET /api/auctions | 1800 | 97.0 | 128.5 | 141.2 | 255.6 |
| 3 | generalReads | GET /api/auctions | 3000 | 120.5 | 155.7 | 252.7 | 415.5 |
| 4 | generalReads | GET /api/auctions | 4200 | 135.2 | 192.0 | 246.1 | 360.4 |
| 5 | generalReads | GET /api/auctions | 5910 | 191.2 | 443.8 | 651.0 | 764.0 |
| 6 | generalReads | GET /api/auctions | 7340 | 3419.0 | 8546.4 | 8676.3 | 8771.1 |
| 1 | generalReads | GET /api/auctions/:id | 1164 | 52.5 | 69.0 | 87.1 | 118.9 |
| 2 | generalReads | GET /api/auctions/:id | 1765 | 61.4 | 73.1 | 85.7 | 176.6 |
| 3 | generalReads | GET /api/auctions/:id | 2964 | 67.6 | 76.4 | 87.2 | 116.3 |
| 4 | generalReads | GET /api/auctions/:id | 4165 | 70.0 | 87.2 | 119.0 | 304.3 |
| 5 | generalReads | GET /api/auctions/:id | 5953 | 102.0 | 354.9 | 605.6 | 679.9 |
| 6 | generalReads | GET /api/auctions/:id | 6970 | 2483.3 | 8502.4 | 8612.5 | 8666.3 |

스테이지 1~4는 p95 60~430ms로 안정, 스테이지5(QPS 300)부터 p95가
400~650ms로 꺾이기 시작, 스테이지6(QPS 400)에서 전 API 동시에
p50 2.3~3.4s, p95 8.5s대로 완전 포화.

### 2.2 pure-throughput SSE_VUS=500

| stage | scenario | name | n | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| 1 | bidContextReads | GET /api/auctions/:id/bid-context | 2321 | 63.7 | 99.8 | 123.5 | 187.8 |
| 2 | bidContextReads | GET /api/auctions/:id/bid-context | 3523 | 87.9 | 101.6 | 114.6 | 228.8 |
| 3 | bidContextReads | GET /api/auctions/:id/bid-context | 5921 | 97.6 | 122.8 | 170.4 | 367.0 |
| 4 | bidContextReads | GET /api/auctions/:id/bid-context | 8307 | 119.1 | 286.5 | 323.7 | 426.3 |
| 5 | bidContextReads | GET /api/auctions/:id/bid-context | 11377 | 1113.3 | 2739.2 | 2982.0 | 3450.8 |
| 6 | bidContextReads | GET /api/auctions/:id/bid-context | 13470 | 7468.5 | 9021.7 | 9169.0 | 9220.2 |
| 1 | bidWrites | GET /api/auctions/:id/bid-context | 1161 | 66.2 | 105.8 | 120.2 | 154.5 |
| 2 | bidWrites | GET /api/auctions/:id/bid-context | 1761 | 88.7 | 102.5 | 112.6 | 209.9 |
| 3 | bidWrites | GET /api/auctions/:id/bid-context | 2960 | 97.7 | 123.5 | 171.1 | 333.3 |
| 4 | bidWrites | GET /api/auctions/:id/bid-context | 4153 | 119.2 | 288.6 | 324.8 | 401.8 |
| 5 | bidWrites | GET /api/auctions/:id/bid-context | 5655 | 1106.6 | 2711.0 | 2982.0 | 3474.6 |
| 6 | bidWrites | GET /api/auctions/:id/bid-context | 6527 | 7667.3 | 9020.6 | 9167.0 | 9214.5 |
| 1 | bidWrites | POST /api/auctions/:id/bids | 1160 | 64.2 | 89.0 | 102.8 | 136.3 |
| 2 | bidWrites | POST /api/auctions/:id/bids | 1760 | 86.8 | 99.5 | 110.0 | 197.0 |
| 3 | bidWrites | POST /api/auctions/:id/bids | 2959 | 97.3 | 119.6 | 152.6 | 317.5 |
| 4 | bidWrites | POST /api/auctions/:id/bids | 4146 | 116.6 | 278.6 | 319.9 | 403.4 |
| 5 | bidWrites | POST /api/auctions/:id/bids | 5519 | 1075.9 | 2696.6 | 2972.3 | 3387.1 |
| 6 | bidWrites | POST /api/auctions/:id/bids | 6171 | 7796.8 | 9003.7 | 9144.0 | 9204.8 |
| 1 | generalReads | GET /api/auctions | 1200 | 75.5 | 116.7 | 128.7 | 179.1 |
| 2 | generalReads | GET /api/auctions | 1800 | 102.3 | 134.9 | 145.8 | 172.7 |
| 3 | generalReads | GET /api/auctions | 2964 | 123.2 | 161.3 | 228.2 | 384.3 |
| 4 | generalReads | GET /api/auctions | 4107 | 172.4 | 325.7 | 374.5 | 428.6 |
| 5 | generalReads | GET /api/auctions | 5739 | 1139.3 | 2752.7 | 3004.5 | 3408.1 |
| 6 | generalReads | GET /api/auctions | 6896 | 7385.1 | 9044.4 | 9193.3 | 9269.7 |
| 1 | generalReads | GET /api/auctions/:id | 1122 | 45.5 | 67.3 | 92.3 | 120.4 |
| 2 | generalReads | GET /api/auctions/:id | 1723 | 62.9 | 74.3 | 86.9 | 159.4 |
| 3 | generalReads | GET /api/auctions/:id | 2955 | 69.7 | 81.0 | 91.8 | 113.9 |
| 4 | generalReads | GET /api/auctions/:id | 4200 | 75.9 | 163.2 | 230.8 | 388.1 |
| 5 | generalReads | GET /api/auctions/:id | 5641 | 1067.1 | 2695.9 | 2987.7 | 3399.7 |
| 6 | generalReads | GET /api/auctions/:id | 6589 | 7536.9 | 8981.1 | 9122.9 | 9175.6 |

250과 같은 전개지만 스테이지5에서 더 일찍, 더 세게 꺾인다(p95
2.7~2.8s vs 250의 400~650ms). 스테이지6은 250보다 살짝 더 나쁨(p95
9.0s대 vs 8.5s대).

### 2.3 pure-throughput SSE_VUS=1000

| stage | scenario | name | n | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| 1 | bidContextReads | GET /api/auctions/:id/bid-context | 2234 | 68.6 | 98.7 | 118.7 | 155.3 |
| 2 | bidContextReads | GET /api/auctions/:id/bid-context | 3439 | 89.8 | 109.1 | 125.5 | 206.0 |
| 3 | bidContextReads | GET /api/auctions/:id/bid-context | 5710 | 105.6 | 179.2 | 231.2 | 2122.0 |
| 4 | bidContextReads | GET /api/auctions/:id/bid-context | 8176 | 199.5 | 422.5 | 536.3 | 651.5 |
| 5 | bidContextReads | GET /api/auctions/:id/bid-context | 10864 | 2178.8 | 4144.6 | 4437.7 | 4493.9 |
| 6 | bidContextReads | GET /api/auctions/:id/bid-context | 13400 | 6400.8 | 16095.6 | 28631.6 | 60003.0 |
| 1 | bidWrites | GET /api/auctions/:id/bid-context | 1117 | 72.8 | 101.0 | 111.3 | 147.8 |
| 2 | bidWrites | GET /api/auctions/:id/bid-context | 1719 | 90.7 | 109.3 | 124.8 | 200.2 |
| 3 | bidWrites | GET /api/auctions/:id/bid-context | 2855 | 106.0 | 178.3 | 238.6 | 2120.8 |
| 4 | bidWrites | GET /api/auctions/:id/bid-context | 4088 | 201.0 | 421.2 | 537.7 | 653.7 |
| 5 | bidWrites | GET /api/auctions/:id/bid-context | 5336 | 2154.5 | 4139.7 | 4436.2 | 4491.4 |
| 6 | bidWrites | GET /api/auctions/:id/bid-context | 6733 | 6414.6 | 16628.0 | 28877.4 | 60002.1 |
| 1 | bidWrites | POST /api/auctions/:id/bids | 1116 | 68.3 | 91.9 | 102.2 | 132.5 |
| 2 | bidWrites | POST /api/auctions/:id/bids | 1718 | 88.4 | 106.6 | 121.4 | 173.8 |
| 3 | bidWrites | POST /api/auctions/:id/bids | 2853 | 104.2 | 176.3 | 223.2 | 2128.1 |
| 4 | bidWrites | POST /api/auctions/:id/bids | 4076 | 188.6 | 411.9 | 531.4 | 645.1 |
| 5 | bidWrites | POST /api/auctions/:id/bids | 5140 | 2107.9 | 4117.2 | 4412.7 | 4488.0 |
| 6 | bidWrites | POST /api/auctions/:id/bids | 6102 | 6362.8 | 14245.5 | 22616.6 | 60002.3 |
| 1 | generalReads | GET /api/auctions | 1200 | 84.0 | 126.9 | 142.3 | 179.3 |
| 2 | generalReads | GET /api/auctions | 1671 | 113.5 | 143.5 | 154.4 | 167.1 |
| 3 | generalReads | GET /api/auctions | 2910 | 146.2 | 230.0 | 294.6 | 421.9 |
| 4 | generalReads | GET /api/auctions | 4020 | 280.9 | 482.0 | 585.9 | 683.6 |
| 5 | generalReads | GET /api/auctions | 5525 | 2230.3 | 4157.6 | 4456.4 | 4520.1 |
| 6 | generalReads | GET /api/auctions | 6858 | 6410.3 | 15562.4 | 28635.3 | 60002.1 |
| 1 | generalReads | GET /api/auctions/:id | 1035 | 48.0 | 61.1 | 71.3 | 102.2 |
| 2 | generalReads | GET /api/auctions/:id | 1765 | 63.6 | 77.6 | 96.6 | 172.0 |
| 3 | generalReads | GET /api/auctions/:id | 2800 | 72.8 | 95.4 | 136.1 | 2099.5 |
| 4 | generalReads | GET /api/auctions/:id | 4164 | 102.3 | 338.3 | 451.4 | 584.4 |
| 5 | generalReads | GET /api/auctions/:id | 5335 | 2092.0 | 4089.1 | 4391.0 | 4437.4 |
| 6 | generalReads | GET /api/auctions/:id | 6600 | 6358.7 | 15578.4 | 28568.3 | 60002.0 |

세 시나리오 중 가장 이른 붕괴 — 스테이지3부터 이미 개별 요청의 max가
2.1s를 찍는 이상값이 섞이고, 스테이지5(p95 4.1s)를 거쳐 스테이지6에선
p99가 22.6~28.9s, **max가 정확히 60002~60003ms로 4개 API 모두에서
반복 등장**한다 — k6 기본 HTTP 타임아웃(60초)에 걸려 서버 응답을 아예
못 받고 강제 종료된 요청이 다수 있었다는 뜻이다.

### 2.4 hot-auction-pattern (스테이지 없음, 5분 고정구간)

| stage | scenario | name | n | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| 1 | coldBids | GET /api/auctions/:id/bid-context | 5376 | 97.6 | 118.3 | 136.2 | 306.0 |
| 1 | coldBids | POST /api/auctions/:id/bids | 5374 | 96.5 | 116.1 | 130.8 | 248.5 |
| 1 | hotAuction1 | GET /api/auctions/:id/bid-context | 4182 | 101.3 | 120.5 | 136.7 | 280.2 |
| 1 | hotAuction1 | POST /api/auctions/:id/bids | 4180 | 87.6 | 109.6 | 124.2 | 257.2 |
| 1 | hotAuction2 | GET /api/auctions/:id/bid-context | 4182 | 101.3 | 120.2 | 137.1 | 283.1 |
| 1 | hotAuction2 | POST /api/auctions/:id/bids | 4180 | 88.0 | 110.5 | 125.1 | 242.1 |
| 1 | hotAuction3 | GET /api/auctions/:id/bid-context | 4182 | 101.3 | 119.9 | 137.2 | 284.9 |
| 1 | hotAuction3 | POST /api/auctions/:id/bids | 4180 | 87.7 | 110.6 | 126.3 | 242.8 |

QPS 계단이 없고 4개 시나리오가 5분간 고정 iters/s(18/14/14/14)로만
도는 구조라 스테이지 자체가 1개뿐이다. 전 구간 p95 109~120ms, p99
124~137ms로 안정적 — 다른 시나리오들과 달리 "포화 안 됨"이 아니라
"애초에 포화 지점까지 부하를 안 올린 시나리오"로 봐야 한다.

### 2.5 bid-only-load(분산, 경매 200개)

| stage | scenario | name | n | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| 1 | bidContextReads | GET /api/auctions/:id/bid-context | 2401 | 62.1 | 92.8 | 100.7 | 171.6 |
| 2 | bidContextReads | GET /api/auctions/:id/bid-context | 3603 | 87.3 | 98.0 | 105.2 | 181.3 |
| 3 | bidContextReads | GET /api/auctions/:id/bid-context | 6004 | 95.5 | 112.4 | 129.2 | 308.2 |
| 4 | bidContextReads | GET /api/auctions/:id/bid-context | 8402 | 102.9 | 134.5 | 183.3 | 354.0 |
| 5 | bidContextReads | GET /api/auctions/:id/bid-context | 11884 | 208.6 | 905.3 | 1179.6 | 1233.4 |
| 6 | bidContextReads | GET /api/auctions/:id/bid-context | 13780 | 4992.0 | 8784.8 | 8865.7 | 8998.2 |
| 1 | bidWrites | GET /api/auctions/:id/bid-context | 1201 | 63.0 | 94.7 | 101.1 | 151.5 |
| 2 | bidWrites | GET /api/auctions/:id/bid-context | 1801 | 87.6 | 100.1 | 107.1 | 199.7 |
| 3 | bidWrites | GET /api/auctions/:id/bid-context | 3002 | 96.2 | 112.9 | 127.4 | 271.2 |
| 4 | bidWrites | GET /api/auctions/:id/bid-context | 4201 | 103.3 | 134.4 | 181.1 | 344.3 |
| 5 | bidWrites | GET /api/auctions/:id/bid-context | 5942 | 209.2 | 899.4 | 1175.4 | 1230.3 |
| 6 | bidWrites | GET /api/auctions/:id/bid-context | 6661 | 5057.0 | 8790.9 | 8867.9 | 8909.3 |
| 1 | bidWrites | POST /api/auctions/:id/bids | 1200 | 62.5 | 86.9 | 93.5 | 151.4 |
| 2 | bidWrites | POST /api/auctions/:id/bids | 1801 | 86.4 | 96.9 | 101.7 | 170.9 |
| 3 | bidWrites | POST /api/auctions/:id/bids | 3000 | 95.2 | 111.0 | 122.5 | 271.2 |
| 4 | bidWrites | POST /api/auctions/:id/bids | 4200 | 100.9 | 131.5 | 158.6 | 336.9 |
| 5 | bidWrites | POST /api/auctions/:id/bids | 5878 | 200.6 | 881.2 | 1149.5 | 1242.5 |
| 6 | bidWrites | POST /api/auctions/:id/bids | 6220 | 4892.0 | 8756.5 | 8837.8 | 8914.3 |
| 1 | generalReads | GET /api/auctions | 1201 | 82.2 | 122.8 | 140.5 | 200.7 |
| 2 | generalReads | GET /api/auctions | 1802 | 116.7 | 134.8 | 140.1 | 202.2 |
| 3 | generalReads | GET /api/auctions | 3004 | 135.3 | 153.2 | 166.5 | 307.9 |
| 4 | generalReads | GET /api/auctions | 4204 | 150.8 | 180.0 | 219.5 | 383.6 |
| 5 | generalReads | GET /api/auctions | 5989 | 265.6 | 951.7 | 1213.3 | 1281.2 |
| 6 | generalReads | GET /api/auctions | 7094 | 5095.8 | 8813.6 | 8898.1 | 8966.0 |
| 1 | generalReads | GET /api/auctions/:id | 1200 | 42.1 | 60.5 | 68.6 | 114.2 |
| 2 | generalReads | GET /api/auctions/:id | 1800 | 62.6 | 72.8 | 81.3 | 141.4 |
| 3 | generalReads | GET /api/auctions/:id | 3000 | 68.0 | 77.9 | 91.5 | 220.2 |
| 4 | generalReads | GET /api/auctions/:id | 4198 | 70.7 | 81.5 | 123.1 | 216.6 |
| 5 | generalReads | GET /api/auctions/:id | 5905 | 139.4 | 865.0 | 1131.6 | 1187.3 |
| 6 | generalReads | GET /api/auctions/:id | 6699 | 4901.6 | 8743.0 | 8822.7 | 8864.7 |

SSE 없이도 같은 QPS 계단에서 같은 붕괴 패턴이 재현된다(스테이지5부터
p95 900ms~1.3s, 스테이지6 p95 8.7~8.8s) — SSE fan-out 비용을 뺀
순수 입찰 처리 자체의 한계다.

### 2.6 bid-only-load(단일 핫옥션, HOT_AUCTION_ID=3001001)

| stage | scenario | name | n | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| 1 | (none) | GET /api/auctions (setup) | 1 | 67.0 | 67.0 | 67.0 | 67.0 |
| 1 | (none) | https://api.dbidding.shop/api/auth/login | 50 | 141.1 | 157.2 | 160.0 | 162.1 |
| 1 | bidContextReads | GET /api/auctions/:id/bid-context | 2387 | 62.0 | 91.3 | 103.6 | 267.7 |
| 2 | bidContextReads | GET /api/auctions/:id/bid-context | 3588 | 85.7 | 96.6 | 108.1 | 311.2 |
| 3 | bidContextReads | GET /api/auctions/:id/bid-context | 5988 | 92.8 | 103.2 | 116.6 | 274.9 |
| 4 | bidContextReads | GET /api/auctions/:id/bid-context | 8389 | 97.0 | 118.9 | 193.7 | 413.7 |
| 5 | bidContextReads | GET /api/auctions/:id/bid-context | 11967 | 114.5 | 226.1 | 363.6 | 436.0 |
| 6 | bidContextReads | GET /api/auctions/:id/bid-context | 14824 | 2158.4 | 6159.7 | 6765.4 | 8552.8 |
| 1 | bidWrites | GET /api/auctions/:id/bid-context | 1193 | 62.6 | 93.6 | 101.7 | 157.6 |
| 2 | bidWrites | GET /api/auctions/:id/bid-context | 1794 | 86.4 | 97.8 | 112.9 | 296.2 |
| 3 | bidWrites | GET /api/auctions/:id/bid-context | 2994 | 93.4 | 104.0 | 117.1 | 265.2 |
| 4 | bidWrites | GET /api/auctions/:id/bid-context | 4195 | 97.4 | 119.0 | 189.1 | 400.5 |
| 5 | bidWrites | GET /api/auctions/:id/bid-context | 5983 | 114.8 | 227.9 | 365.3 | 445.8 |
| 6 | bidWrites | GET /api/auctions/:id/bid-context | 7195 | 1888.9 | 6132.9 | 6766.6 | 8566.5 |
| 1 | bidWrites | POST /api/auctions/:id/bids | 1193 | 61.7 | 83.2 | 91.8 | 238.9 |
| 2 | bidWrites | POST /api/auctions/:id/bids | 1793 | 74.8 | 93.6 | 100.0 | 205.1 |
| 3 | bidWrites | POST /api/auctions/:id/bids | 2993 | 76.9 | 98.1 | 106.7 | 227.1 |
| 4 | bidWrites | POST /api/auctions/:id/bids | 4194 | 80.5 | 106.4 | 126.6 | 391.6 |
| 5 | bidWrites | POST /api/auctions/:id/bids | 5973 | 95.0 | 194.4 | 313.7 | 401.0 |
| 6 | bidWrites | POST /api/auctions/:id/bids | 6817 | 1792.4 | 6010.5 | 6724.0 | 8499.9 |
| 1 | generalReads | GET /api/auctions | 1200 | 82.2 | 119.1 | 133.7 | 314.1 |
| 2 | generalReads | GET /api/auctions | 1800 | 113.5 | 132.5 | 139.6 | 347.3 |
| 3 | generalReads | GET /api/auctions | 3000 | 130.9 | 142.7 | 164.4 | 305.8 |
| 4 | generalReads | GET /api/auctions | 4200 | 140.5 | 162.6 | 296.9 | 451.1 |
| 5 | generalReads | GET /api/auctions | 6001 | 173.2 | 273.5 | 414.0 | 499.3 |
| 6 | generalReads | GET /api/auctions | 7597 | 2210.6 | 6204.4 | 6804.7 | 8548.3 |
| 1 | generalReads | GET /api/auctions/:id | 1188 | 40.4 | 64.7 | 73.7 | 119.5 |
| 2 | generalReads | GET /api/auctions/:id | 1788 | 61.1 | 73.2 | 97.4 | 171.5 |
| 3 | generalReads | GET /api/auctions/:id | 2989 | 66.2 | 74.5 | 82.4 | 158.3 |
| 4 | generalReads | GET /api/auctions/:id | 4189 | 67.8 | 76.4 | 91.0 | 317.9 |
| 5 | generalReads | GET /api/auctions/:id | 5969 | 70.8 | 168.0 | 270.1 | 400.5 |
| 6 | generalReads | GET /api/auctions/:id | 7223 | 1870.4 | 6087.8 | 6731.2 | 8528.2 |

### 2.7 단일 핫옥션 vs 분산 풀 비교 — 락 경합보다 전체 포화가 더 크다

§2.5(분산)와 §2.6(단일 핫옥션)의 `generalReads`·`GET /api/auctions`
행만 나란히 놓으면:

| 스테이지 | 분산 p95(ms) | 단일 핫옥션 p95(ms) |
|---|---|---|
| 1 | 122.8 | 119.1 |
| 2 | 134.8 | 132.5 |
| 3 | 153.2 | 142.7 |
| 4 | 180.0 | 162.6 |
| 5 | 951.7 | 273.5 |
| 6 | 8813.6 | 6204.4 |

스테이지 5까지는 단일 핫옥션 쪽이 오히려 더 낮다(같은 행에 쓰기가
몰려도 순차 처리되는 게 200개 분산 조회보다 가벼움). 스테이지 6에서는
둘 다 무너지지만 분산 쪽이 더 나쁘다(8.8s vs 6.2s) — 11차 결론
("락 경합보다 전체 처리량 포화가 지배적 요인")과 같은 방향이며, 이번
회차에서도 재확인됐다. 다만 `bidWrites`·`POST /api/auctions/:id/bids`
행만 보면 단일 핫옥션도 스테이지6에서 p99 6.7s/max 8.5s까지 올라가
분산(p99 8.8s/max 8.9s)과 거의 붙는다 — §1에서 지적한 11차 대비 악화도
같은 맥락(표본 수는 비슷한데 후반 스테이지 결과가 실행마다 흔들릴 수
있다는 뜻, §5).

### 2.8 SSE_VUS 증가가 붕괴를 앞당긴다

`bidWrites`·`POST /api/auctions/:id/bids`의 스테이지6 p95만 모으면:

| SSE_VUS | 스테이지6 p95(ms) |
|---|---|
| 250 | 8518.2 |
| 500 | 9003.7 |
| 1000 | 14245.5 |

동시 SSE 연결 수 자체가 이 QPS-스테이지 붕괴의 심각도에 영향을 준다 —
정확한 인과 메커니즘(SSE fan-out이 톰캣 스레드/커넥션을 잠식하는지,
가상스레드 admission control(#589)의 캡이 걸리는지 등)은 서버 측
지표 교차 확인이 필요하며 이 문서 범위 밖이다(§5).

---

## 3. 한계·캐비어트

- **서버 측 상관관계 없음.** 이 문서는 클라이언트 측 수치만 다룬다.
  스테이지6 붕괴의 원인(HikariCP 고갈? GC? Redis? 커넥터 스레드 고갈?)은
  11차 문서의 서버 측 분석을 참조해야 하며, 이번 라운드 자체 데이터로
  재검증하지 않았다.
- **event:timeline 컨슈머 병목 재확인만 함, 정량 비교 안 함.** 각 시나리오
  사이 gate 체크에서 `redis_stream_group_lag`가 2,338~10,094까지 쌓였고
  드레인에 매번 수 분~10분 이상 걸렸다(11차와 같은 단일 스레드 병목,
  §11차 문서 참고) — 이번 문서는 이 수치를 스테이지별로 쪼개 분석하지
  않았다.
- **`bid-context` 503 재확인 안 함.** 11차는 6개 시나리오 전체에서 503이
  0건이었다고 결론 냈다. 이번 라운드에서도 같은 인프라·같은 배포 상태이므로
  재현될 것으로 예상하지만, Prometheus로 직접 재확인하지 않았다 —
  단정하지 않는다.
- **k6 자체 threshold 실패로 인한 종료 코드(99)는 정상.** pure500/pure1000/
  bid-only(분산)/bid-only(단일)는 전부 exit 99로 끝났는데, 이는 k6가
  SLO(threshold)를 위반했다고 판단해 붙이는 코드이며 실행 자체는 끝까지
  정상 완료됐다(각 로그에 `=== ... SUMMARY ===`와 최종 iteration 카운트가
  찍힘, JSON/CSV 산출물도 정상 크기로 존재). pure250과 hot-auction-pattern만
  임계치를 통과해 exit 0.
- **표본 수 불균형.** 스테이지가 진행될수록(같은 2분 동안 응답이 느려지므로)
  완료된 요청 수가 자연히 늘어난다(예: pure250 stage1 n=2364 vs stage6
  n=14320) — 후반 스테이지의 p99/max는 표본이 더 많아 통계적으로 더
  안정적이지만, 애초에 "느려서 오래 붙잡혀 있다가 뒤늦게 완료된 요청"이
  섞여 있어 순수 신규 유입 부하만의 응답시간과는 다르다는 점을
  감안해야 한다.
- **`stage_breakdown.py`는 이 저장소에 커밋돼 있지 않다.** 세션
  스크래치패드에만 있는 일회성 분석 스크립트다 — 재현하려면 같은 로직을
  다시 작성해야 한다(로직 자체는 위 §0 "측정 방법"에 전부 기술).

---

## 4. 원본 데이터 참조

`backend/src/test/k6/result/`:

- `round12-pure250-20260819.json` / `round12-pure250-raw.csv.gz` / `round12-pure250-stage-breakdown.md`
- `round12-pure500-20260819.json` / `round12-pure500-raw.csv.gz` / `round12-pure500-stage-breakdown.md`
- `round12-pure1000-20260819.json` / `round12-pure1000-raw.csv.gz` / `round12-pure1000-stage-breakdown.md`
- `round12-hotauction-20260819.json` / `round12-hotauction-raw.csv.gz` / `round12-hotauction-stage-breakdown.md`
- `round12-bidonly-20260819.json` / `round12-bidonly-raw.csv.gz` / `round12-bidonly-stage-breakdown.md`
- `round12-bidonly-hot-20260819.json` / `round12-bidonly-hot-raw.csv.gz` / `round12-bidonly-hot-stage-breakdown.md`

서버 측 기준선·근본원인 분석: [`15-round11-consumer-bottleneck-persists-503-storm-resolved.md`](15-round11-consumer-bottleneck-persists-503-storm-resolved.md)와
그 raw-data(`raw-data/11-round11-prometheus-raw-data.md`).

> 이 문서는 codex의 도움을 받아 작성하였습니다
