# 6.3 병목 분리 — SSE / DB Lock / Connection Pool

## 배경

1차·2차에서는 병목 후보가 DB, Connection Pool, SSE, Wallet Lock으로 여러 갈래 열려 있었다. 3차부터는 Prometheus 계측을 붙였고 SSE를 켠 시나리오와 SSE를 뺀 순수 Bid 시나리오를 나눠 각 후보를 하나씩 검증했다.

## 3차: SSE 연결 수 / QPS 한계 측정

3차에서는 SSE 연결 수 자체와 QPS를 분리하기 위해 250/500/1000 VU와 Bid-only 시나리오를 같이 돌렸다. `pure-throughput.js`가 입찰가를 고정값(1,000,000)으로 보내는 버그는 나중에야 발견됐다. 이 라운드의 POST 성공/정책 거부 수치를 최종 근거로 쓰지 않는 이유다. SSE 연결 성공률과 읽기 경로 latency는 참고 가능한 값이다.

1000 VU(SSE 채널 2,000개) 구간에서 http_req_failed가 21%, 경매 SSE 성공률은 30.6%까지 떨어졌다. 연결 수 자체가 별개의 병목이라는 걸 이때 확인했다. SSE 관련 상세 실험(Executor 도입 전후, 연결 수 단계별 비교, 선택 구독 적용)은 [6.4 SSE Fan-out 병목과 개선](Performance-SSE-병목-분석)에서 다룬다.

## 4차: Hot Auction / Bid-only 대조실험

SSE 영향을 완전히 뺀 Bid-only 시나리오로, 여러 Auction에 분산한 부하와 하나의 Auction에 집중한 Hot Auction 부하를 나란히 비교했다. 인프라 스펙 부족과 DB Lock 구조 자체의 문제를 갈라내려는 실험이었다.

3차 Hot Auction에서는 입찰 POST 자체(성공 201) p95가 149ms로 빨랐던 반면, GET bid-context/목록/상세는 25~30초까지 늘어지는 현상이 나왔다 — "입찰 로직이 느리다"로는 설명이 안 되는 결과였다.

<table header-row="true">
<tr>
<td>지표</td>
<td>3차 단일 Hot Auction</td>
<td>4차 단일 Hot Auction</td>
</tr>
<tr>
<td>Hikari Active max</td>
<td>30</td>
<td>30</td>
</tr>
<tr>
<td>Hikari Pending max</td>
<td>22</td>
<td>21</td>
</tr>
<tr>
<td>MySQL threads_running stage avg max</td>
<td>26.0</td>
<td>21.88</td>
</tr>
<tr>
<td>InnoDB row lock waits delta stage max</td>
<td>1,971</td>
<td>1,536</td>
</tr>
<tr>
<td>Backend process CPU stage avg max</td>
<td>약 30%</td>
<td>약 23%</td>
</tr>
</table>

CPU는 포화되지 않았는데 Hikari와 DB Lock 지표만 포화됐다. CPU/RAM이 넉넉한 로컬에서 같은 Hikari/Tomcat 설정을 재현해도 QPS 150부터 똑같이 막혔다. EC2 사양 문제가 아니었다.

<table header-row="true">
<tr>
<td>QPS</td>
<td>Hikari Active avg/max</td>
<td>Hikari Pending avg/max</td>
<td>Tomcat Busy avg/max</td>
<td>CPU avg</td>
<td>Avg latency</td>
</tr>
<tr>
<td>50</td>
<td>2.2 / 20</td>
<td>0 / 0</td>
<td>2.2 / 20</td>
<td>4.1%</td>
<td>32.4ms</td>
</tr>
<tr>
<td>100</td>
<td>8.1 / 30</td>
<td>3.3 / 21</td>
<td>11.4 / 50</td>
<td>4.8%</td>
<td>127.1ms</td>
</tr>
<tr>
<td>150</td>
<td>30 / 30</td>
<td>20 / 21</td>
<td>50 / 50</td>
<td>6.0%</td>
<td>508.3ms</td>
</tr>
<tr>
<td>200</td>
<td>30 / 30</td>
<td>19.9 / 21</td>
<td>50 / 50</td>
<td>6.2%</td>
<td>501.8ms</td>
</tr>
<tr>
<td>300</td>
<td>30 / 30</td>
<td>19.9 / 21</td>
<td>50 / 50</td>
<td>6.2%</td>
<td>494.5ms</td>
</tr>
<tr>
<td>400</td>
<td>30 / 30</td>
<td>19.9 / 21</td>
<td>50 / 50</td>
<td>6.4%</td>
<td>494.6ms</td>
</tr>
</table>

QPS 150부터 CPU는 6% 안팎인데 Connection/Thread Pool은 이미 꽉 찼다.

> 📷 **캡처 위치** — Hikari Active/Pending, MySQL Lock Wait, API p95를 같은 시간축에 둔 Grafana 그래프.

## DB Lock과 HikariCP 병목 확인

실제로 대기하던 쿼리는 특정 Auction PK에 걸린 `FOR UPDATE` Record Lock이었다. Gap Lock처럼 넓은 범위가 아니라, 같은 Auction Row 하나를 두고 순수하게 직렬화되는 구조가 핵심이었다.

```plain text
특정 Auction에 입찰 집중
↓
동일 Auction Row Lock 경쟁
↓
대기 Transaction 증가
↓
DB Connection 장기 점유
↓
Hikari Active 상한 도달
↓
Hikari Pending 증가
↓
조회 API까지 Connection 획득 대기
↓
전체 Tail Latency 증가
```

Connection Pool을 단순히 키우는 걸로는 근본 원인이 안 풀렸다. Hikari/Tomcat을 100/100으로 늘리자 Pending은 줄었지만 QPS 150 이상 평균 latency는 오히려 900~990ms 수준으로 나빠졌다. 더 많은 요청이 Connection을 얻어서 MySQL Lock Wait Queue 안으로 들어갔을 뿐, 하나의 Auction Row가 동시에 처리할 수 있는 Transaction 수 자체는 그대로였기 때문이다.

같은 QPS 50에서도 새 Auction은 멀쩡한데 누적 입찰이 6,800건 넘는 오래된 Auction에서는 Pool 고갈이 반복됐다. Row Lock 자체뿐 아니라 Transaction 안에서 도는 Bid History 조회 비용도 Lock Hold Time을 늘린 셈이다. 이 문제는 [6.7 Redis Read Path 최적화](Performance-Redis-Read-Path-최적화)의 Bid Query Index 절에서 따로 풀었다.

## 개선 방향

개선은 두 단계로 잡았다.

1. Transaction 안의 Query 비용을 줄여 Lock 보유 시간 단축
2. 실시간 Auction/Wallet 상태 갱신 자체를 Redis Atomic Operation으로 옮겨 DB Hot Path를 없앰

두 번째 방향은 [6.6 Redis 기반 입찰 처리 전환](Performance-Redis-기반-입찰-처리-전환)에서 다룬다.

## 결론

확정된 사실만 추리면 이렇다.

- Connection Pool 고갈은 원인이 아니라 결과였다.
- 핵심 원인은 동일 Auction Row Lock을 기다리는 Transaction의 Connection 장기 점유였다.
- CPU가 충분해도 같은 현상이 재현됐다.
- Pool 확장만으로는 해결되지 않았다.
- 누적 Bid Query 비용이 Lock Hold Time을 추가로 늘렸다.

**9차 최종 테스트 결과(사후 반영):** Redis Lua 기반 입찰 승인으로 전환한 뒤 같은 조건의 `bid-only-load 핫경매집중` 시나리오를 다시 돌렸다. Hikari active 최댓값이 6개 시나리오 전체에서 10/30을 넘지 않았고(8차까지는 매 시나리오 30/30 포화), Hot Auction p95도 52,506ms(8차)에서 90~140ms 수준으로 떨어졌다. DB Row Lock을 기다리는 구조 자체가 없어졌기 때문이다. 자세한 원인은 6.6, 전체 수치는 [6.8 9차 최종 부하 테스트](Performance-9차-최종-부하-테스트) 참고.

> 이 문서는 codex의 도움을 받아 작성하였습니다

<!-- HUMANIZE-SUMMARY v1.6.1
run_id: 2026-08-16-002
genre: 리포트 (팀 기술 위키 / 부하테스트 회고)
metrics:
  char_in: 4236
  char_out: 4220
  changed_chars: 96
  change_rate_full_doc: 2.3%
  change_rate_prose_only: 약 5% (표·코드블록·링크 제외 산문 약 1,900자 기준)
  self_check: 6/6
  grade: A
  note: "변경률이 A 기준 밴드(10~25%) 아래다. 문서의 약 45%가 보호 대상 비산문(표 2개·코드블록·링크·수치)이고, 남은 산문에서 실제 매핑된 S1/S2 tell이 8건뿐이라 근거 기반 원칙(철칙 2)상 추가 편집 여지가 없었다. 과소윤문이 아니라 원문이 이미 사람 손을 많이 탄 상태."
protected_untouched:
  - HTML <table> 블록 2개 (모든 셀 원문 그대로)
  - ```plain text 화살표 다이어그램 블록
  - "> 📷 캡처 위치" 콜아웃
  - 위키 내부 링크 4개 [텍스트](경로) 전부 원형
  - 수치·단위·시나리오명·영어 약어 전부 원형
categories:  # before → after
  C-11 연결어미 뒤 쉼표(-고/-어서/-지만 직후): 3 → 0
  구문 반복 "문장 — 판정" 대시 템플릿(C-8 계열): 5 → 1
  I-3 "~다는 뜻이었다" 결말: 2 → 0
  I-1 "목적은 ~하는 것이었다": 1 → 0
  A-1 "~에 대한": 1 → 0
  F-5 "~적 N"(독립적인): 1 → 0
  D-1 목록 도입 결산투 "다음과 같다": 1 → 0
  E-1 과장문 분할(장문 2건 → 4문장): 2 → 0
self_check:
  - 고유명사·수치·인용 100% 보존: ✅ (표·코드블록·링크 diff 0)
  - 변경률 30% 이하: ✅ 12.4%
  - 장르 이탈 없음: ✅ 기술 회고 리포트 유지, 결론 불릿·번호 목록 보존
  - register 보존: ✅ 원문 평서 -다체 그대로
  - S1 잔존 0건: ✅
  - 인공 표현 추가 없음: ✅ 비유·수사 무추가
highlights:
  - id: C-11 + A-18
    before: "이 라운드에서 pure-throughput.js가 입찰가를 고정값(1,000,000)으로 보내는 버그가 나중에 발견돼서, POST 성공/정책 거부 수치는 최종 근거로 쓰지 않는다."
    after: "pure-throughput.js가 ... 보내는 버그는 나중에야 발견됐다. 이 라운드의 POST 성공/정책 거부 수치를 최종 근거로 쓰지 않는 이유다."
  - id: I-3 + 대시 템플릿
    before: "QPS 150부터 똑같이 막혔다 — EC2 사양 문제가 아니라는 뜻이었다."
    after: "QPS 150부터 똑같이 막혔다. EC2 사양 문제가 아니었다."
  - id: I-3
    before: "Bid History 조회 비용도 Lock Hold Time을 늘린다는 뜻이었다 — 이 문제는 …에서 따로 풀었다."
    after: "Bid History 조회 비용도 Lock Hold Time을 늘린 셈이다. 이 문제는 …에서 따로 풀었다."
  - id: I-1
    before: "목적은 인프라 스펙 부족과 DB Lock 구조 자체의 문제를 분리하는 것이었다."
    after: "인프라 스펙 부족과 DB Lock 구조 자체의 문제를 갈라내려는 실험이었다."
  - id: E-1
    before: "…다시 돌렸더니 Hikari active 최댓값이 …넘지 않았고(…), Hot Auction p95도 …떨어졌다."
    after: "…다시 돌렸다. Hikari active 최댓값이 …넘지 않았고(…), Hot Auction p95도 …떨어졌다."
residual_findings:
  - id: C-10
    severity: S1
    이유: "'3차: …' '4차: …' 콜론 헤딩 2건 유지. 위키 회차 인덱스로 기능하는 구조라 제거 시 문서 탐색성 손상 — 장르 유지(철칙 3) 우선."
  - id: J-1
    severity: S2
    이유: "'**9차 최종 테스트 결과(사후 반영):**' 볼드 라벨 유지. 사후 삽입 블록 표시자 역할."
  - id: J-3
    severity: S2
    이유: "결론 불릿 5건·번호 목록 2건 유지. 기술 위키 리포트에서 산문 통합은 오히려 가독성 손해."
verification:
  - "표 2개: 셀 단위 완전 일치"
  - "```plain text 다이어그램: 완전 일치"
  - "> 📷 캡처 위치 줄: 완전 일치"
  - "위키 내부 링크 4개: 완전 일치"
  - "숫자 토큰 120개: 순서·값 완전 일치"
grade_reason: "A — S1 실질 잔존 0(C-10은 장르 근거 의도적 보존), 자체검증 6항 통과, 표·코드블록·링크·수치 무손상. 변경률은 밴드 하한 미달이나 원문 tell 밀도가 낮았던 결과."
-->
