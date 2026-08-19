# 6.2 초기 부하 테스트와 Baseline 수립

## 배경

초기 입찰 구조는 MySQL Transaction을 중심으로 경매 상태, 지갑, 입찰 이력을 처리했다. 첫 부하 테스트에서는 "최대 몇 QPS까지 버티는가"만 단순하게 확인하려 했다.

초기 공통 환경은 다음과 같다.

<table header-row="true">
<tr>
<td>항목</td>
<td>내용</td>
</tr>
<tr>
<td>Backend</td>
<td>Spring Boot / Java 21</td>
</tr>
<tr>
<td>CPU</td>
<td>2 Core</td>
</tr>
<tr>
<td>RAM</td>
<td>약 1GB</td>
</tr>
<tr>
<td>Swap</td>
<td>3GB</td>
</tr>
<tr>
<td>Database</td>
<td>MySQL 8.4</td>
</tr>
<tr>
<td>Load Test</td>
<td>k6</td>
</tr>
</table>

이 시점엔 아직 Prometheus/Grafana 계측이 없었다 — k6 클라이언트 측 지표와 서버에서 직접 확인한 HikariCP/파일디스크립터 상태에 의존했다.

---

## 1차 (08/04) 입찰 처리량 탐색

QPS 50, 150, 300으로 테스트했다.

<table header-row="true">
<tr>
<td>목표 QPS</td>
<td>실제 iter/s</td>
<td>GET p95</td>
<td>POST p95</td>
<td>Iteration p95</td>
<td>최대 VU</td>
</tr>
<tr>
<td>50</td>
<td>49.70</td>
<td>2.38s</td>
<td>2.29s</td>
<td>4.99s</td>
<td>302</td>
</tr>
<tr>
<td>150</td>
<td>93.46</td>
<td>7.21s</td>
<td>6.84s</td>
<td>12.84s</td>
<td>800</td>
</tr>
<tr>
<td>300</td>
<td>94.29</td>
<td>7.14s</td>
<td>6.98s</td>
<td>13.77s</td>
<td>800</td>
</tr>
</table>

> 📷 **캡처 위치** — 2026-08-04, 1차 테스트 중 QPS 150/300 구간 iter/s 정체(약 94/s) 그래프. 정확한 시각은 당시 k6 로그/터미널 기록에서 확인.

150과 300에서 실제 처리량이 약 94 iter/s 부근에서 정체됐고 최대 VU는 800까지 올라갔다. 서버 쪽에서는 HikariCP connection 10개가 모두 사용 중이었고 pending도 약 200까지 쌓였다.

이때는 원인을 하나로 좁히지 못했다 — Wallet/Auction 재조회, 단일 계정 Wallet Lock, 작은 Connection Pool 크기를 모두 후보로 열어 두고 다음 라운드로 넘겼다.

## 2차 (08/06) SSE 연결 수 증가 실험

입찰 rate는 50으로 고정하고 SSE 연결 사용자 수를 100 → 200 → 300 → 500으로 늘려가며 관찰했다.

사용자 한 명이 경매 SSE와 알림 SSE 두 채널을 물기 때문에, 500명이면 약 1,000개 이상의 장기 연결을 유지해야 했다. 이 수준에서 파일디스크립터 제한과 Tomcat `max-connections=1000` 설정이 같이 걸리는 걸 확인했다.

같은 500 VU 조건에서 SSE 전용 executor를 붙이기 전/후를 비교하면 전체 k6 p95가 약 8s → 789ms로 줄었고 SSE 처리량은 약 261/s → 1,475/s로 늘었다.

> 📷 **캡처 위치** — 2026-08-06, 2차 테스트 SSE executor 적용 전/후 p95·처리량 비교 그래프(k6 요약 또는 당시 캡처).

다만 이 수치를 곧바로 "입찰 API가 빨라졌다"로 해석하지는 않았다. 당시 `http_req_duration`엔 오래 붙어 있는 SSE 연결 요청이 일반 API 요청과 섞여서 잡혔기 때문에, SSE executor 효과와 입찰 API 자체의 개선을 그 시점 지표만으로는 갈라낼 수 없었다.

## 초기 테스트의 한계

1차·2차를 거치며 구조적인 한계 몇 가지가 분명해졌다.

- 원인을 좁히지 못했다. 병목 후보(DB, Connection Pool, SSE, Wallet Lock)를 동시에 의심할 수밖에 없었고 어느 것이 진짜 원인인지 실험으로 분리하지 못했다.
- 지표가 섞였다. SSE 장기 연결과 일반 API 요청이 같은 `http_req_duration`에 잡혀서 개선 효과를 특정 계층에 귀속시킬 수 없었다.
- 판단이 정성적이었다. Prometheus 같은 정량 계측이 없어서 서버에 직접 접속해 HikariCP 상태나 파일디스크립터 수를 눈으로 확인하는 방식으로만 원인을 짐작했다.

## 3차부터 Prometheus 기반 정량 계측 도입

그래서 3차부터는 시나리오를 SSE 유무로 먼저 나누고 Prometheus/Grafana로 HikariCP·MySQL row lock·JVM 지표를 실시간으로 교차검증하는 방식으로 전환했다. 이후 라운드(3~4차 병목 분리, 5~8차 JVM/Swap/GC, 9차 최종 검증)는 전부 이 계측 위에서 진행됐다 — 자세한 내용은 [6.3 병목 분리](Performance-병목-분리-SSE-DB-Lock-Connection-Pool) 이후를 참고.

> 이 문서는 codex의 도움을 받아 작성하였습니다

<!-- HUMANIZE-SUMMARY v1.6.1
run_id: 2026-08-16-001
genre: 리포트 (기술 위키 / 부하테스트 회고)
metrics:
  char_in: 2318
  char_out: 2277
  change_rate: 8.7%
  self_check: 6/6
  grade: A
categories:  # before → after
  C-11 연결어미 뒤 쉼표: 6 → 0
  C-10 콜론 부제 헤딩: 2 → 0
  J-1 불릿 볼드 라벨 남발: 3 → 0
  I-1 "~것이었다" 결말: 1 → 0
  주술 불일치("수치는 ~해석하지 않는다"): 1 → 0
preserved_verbatim:
  - HTML <table> 블록 2개 (전체 셀 값 무변경)
  - "> 📷 캡처 위치" placeholder 2개
  - 모든 수치·날짜·단위 (49.70 / 93.46 / 94.29 / 2.38s / 7.21s / 13.77s / 800 / 08/04 / 08/06 / 789ms / 1,475/s / max-connections=1000 등)
  - 영어 약어·고유명사 (MySQL, HikariCP, Prometheus, Grafana, Tomcat, k6, SSE, QPS, VU, JVM, GC, Swap, Wallet/Auction, codex)
  - 큰따옴표 직접 인용 2건, 위키 내부 링크 1건, 하단 고지 문구
self_check:
  - 고유명사·수치·인용 100% 보존: OK
  - 변경률 30% 이하: OK (8.7%)
  - 장르 이탈 없음: OK (기술 리포트 유지)
  - register 보존: OK (해라체 '~다' 서술 유지)
  - S1 잔존 0건: OK
  - 인공 표현 추가 없음: OK
highlights:
  - id: C-11
    before: "입찰 rate는 50으로 고정하고, SSE 연결 사용자 수를 100 → 200 → …"
    after: "입찰 rate는 50으로 고정하고 SSE 연결 사용자 수를 100 → 200 → …"
  - id: J-1
    before: "- **원인을 못 좁혔다.** 병목 후보(…)를 동시에 의심할 수밖에 없었고, 어느 것이 …"
    after: "- 원인을 좁히지 못했다. 병목 후보(…)를 동시에 의심할 수밖에 없었고 어느 것이 …"
  - id: 주술 불일치
    before: "다만 이 수치는 곧바로 \"입찰 API가 빨라졌다\"로 해석하지 않는다."
    after: "다만 이 수치를 곧바로 \"입찰 API가 빨라졌다\"로 해석하지는 않았다."
  - id: C-10
    before: "## 1차 (08/04): 입찰 처리량 탐색"
    after: "## 1차 (08/04) 입찰 처리량 탐색"
  - id: I-1
    before: "첫 부하 테스트의 목적은 단순히 \"…\"를 확인하는 것이었다."
    after: "첫 부하 테스트에서는 \"…\"만 단순하게 확인하려 했다."
residual_findings:
  - id: J-3
    severity: S2
    reason: "'초기 테스트의 한계' 3항 불릿을 산문으로 합치지 않고 유지. 기술 위키에서 열거는 관용적이며 스캔 가치가 커 의도적으로 보존. 볼드 라벨 공식만 제거해 AI 시그니처를 해소."
  - id: 원문 보존
    severity: -
    reason: "'물기 때문에,' '섞여서 잡혔기 때문에,'의 쉼표는 C-11 대상('-고/-며/-지만/-면서/-아서/-어서' 직후)이 아니고 긴 종속절 뒤 호흡이라 유지."
grade_reason: "A — S1 잔존 0건, 변경률 8.7%, 자체검증 6항 통과. 표·수치·placeholder 전량 무변경, 리포트 register 그대로."
-->
