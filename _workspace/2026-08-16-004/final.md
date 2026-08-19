# 6.5 JVM Memory / Swap / GC 병목

## 1. 배경 및 문제 상황
SSE Fan-out은 독립적인 I/O 작업을 대량으로 만든다. 기존 Bounded Thread Pool보다 Virtual Thread가 더 적합할 것으로 보고 5차부터 도입을 시도했다.
막상 돌려 보니 Virtual Thread 자체의 효과보다 물리 RAM이 작아서 생기는 GC/Swap 문제가 훨씬 크게 드러났다. 이 페이지는 5~8차에 걸쳐 Virtual Thread 실험과 JVM Memory/Swap/GC 병목을 어떻게 분리했는지 정리한다.

---

## 2. 테스트 과정
### 5차
Virtual Thread를 적용하려 했지만 Profile 설정 오타로 실제 적용되지 않았다. 동시에 Notification Recovery Scheduler의 과도한 로드와 작은 물리 메모리가 겹치며 OOM이 발생했다.
### 6차
Virtual Thread를 정상 적용하고 문제가 확인된 Scheduler를 비활성화했다.
### 7차
같은 903MB RAM 환경에서 다시 높은 실패율과 Full GC가 나타나 GC Log, Major Page Fault, Swap을 함께 분석했다.
### 8차
RAM을 약 1.8GiB로 늘리고 Heap을 재조정해 JVM/OS Memory 병목을 재검증했다.

---

> 📷 **캡처/그래프 삽입 위치** — 5~8차별 실패율, Full GC, swap I/O를 비교한 그래프 또는 GC 로그 캡처를 넣어 주세요.

## 3. 라운드별 결과
<table header-row=\"true\">
<tr>
<td>Round</td>
<td>Virtual Thread</td>
<td>RAM</td>
<td>1000-tier 실패율</td>
<td>Full GC</td>
<td>OOM</td>
</tr>
<tr>
<td>5차</td>
<td>미적용</td>
<td>903MB</td>
<td>82.68\\~98.38%</td>
<td>다수</td>
<td>예, 3회 중 2회</td>
</tr>
<tr>
<td>6차</td>
<td>적용</td>
<td>903MB</td>
<td>12.65%</td>
<td>세션 전체 2회</td>
<td>아니오</td>
</tr>
<tr>
<td>7차</td>
<td>적용</td>
<td>903MB</td>
<td>42.27%</td>
<td>4회</td>
<td>아니오</td>
</tr>
<tr>
<td>8차</td>
<td>적용</td>
<td>1.8GiB</td>
<td>9.30%</td>
<td>0회</td>
<td>아니오</td>
</tr>
</table>
5차→6차는 Virtual Thread 적용과 Scheduler 비활성화가 동시에 일어났기 때문에 개선분을 Virtual Thread 하나의 효과로 해석하지 않는다.

---

## 4. 6차 k6 결과
### Pure Throughput
<table header-row=\"true\">
<tr>
<td>Tier</td>
<td>총 요청</td>
<td>실패율</td>
<td>Server Error</td>
<td>Median</td>
<td>p95</td>
<td>p99</td>
<td>Max</td>
<td>SSE 연결 성공</td>
</tr>
<tr>
<td>250</td>
<td>136,390</td>
<td>0.19%</td>
<td>0.30%</td>
<td>94ms</td>
<td>8.23s</td>
<td>18.28s</td>
<td>60.00s</td>
<td>100% / 100%</td>
</tr>
<tr>
<td>500</td>
<td>126,060</td>
<td>2.10%</td>
<td>3.91%</td>
<td>67ms</td>
<td>10.01s</td>
<td>20.96s</td>
<td>60.00s</td>
<td>100% / 100%</td>
</tr>
<tr>
<td>1000</td>
<td>128,026</td>
<td>12.65%</td>
<td>18.52%</td>
<td>281ms</td>
<td>23.96s</td>
<td>53.73s</td>
<td>60.04s</td>
<td>100% / 75.1%</td>
</tr>
</table>
1000-tier가 처음 OOM 없이 완주했지만 tail latency와 실패율은 여전히 높았다.

---

## 5. 7차 — GC / Swap 분석
7차에서는 Full GC 4회와 높은 실패율이 다시 나타났다.
GC pause 구간에서 Major Page Fault와 Swap I/O가 함께 증가했다. Old Generation이 Heap 상한에 가까운 상태에서 OS가 Memory Page를 Swap으로 밀어내고, GC가 다시 해당 Page를 읽으면서 긴 Pause가 발생하는 패턴으로 해석했다.
즉 문제는 단순 Heap 크기만이 아니었다.
903MB 물리 RAM 안에서 다음 자원을 함께 감당해야 했다.
- JVM Heap
- Metaspace
- Thread Stack
- Direct Buffer
- OS Page Cache
- Docker / Nginx
- Socket / SSE Connection

---

## 6. 8차 — RAM 증설 검증
RAM을 약 1.8GiB로 늘리고 Heap을 조정했다.
### 1000-tier
<table header-row=\"true\">
<tr>
<td>지표</td>
<td>8차</td>
</tr>
<tr>
<td>Total Requests</td>
<td>134,229</td>
</tr>
<tr>
<td>Failure Rate</td>
<td>9.30%</td>
</tr>
<tr>
<td>Bid Server Error</td>
<td>15.25%</td>
</tr>
<tr>
<td>Median</td>
<td>1.624s</td>
</tr>
<tr>
<td>p95</td>
<td>14.542s</td>
</tr>
<tr>
<td>p99</td>
<td>33.982s</td>
</tr>
<tr>
<td>Max</td>
<td>60.109s</td>
</tr>
<tr>
<td>SSE 연결 성공</td>
<td>100% / 100%</td>
</tr>
<tr>
<td>Full GC</td>
<td>0</td>
</tr>
<tr>
<td>OOM</td>
<td>없음</td>
</tr>
</table>
pure250 기준 Swap-in도 약 339.4 page/s → 4.1 page/s로 크게 감소했다.
RAM 증설 후 Full GC와 Swap Pressure가 크게 줄어 JVM/OS Memory 계층의 불안정성은 완화됐다.

---

## 7. 남은 병목
8차에서도 단일 Hot Auction은 여전히 매우 느렸다.
Hot Bid 시나리오의 p95는 약 52.5초, p99는 약 56.6초 수준까지 올라갔다.
두 문제는 나눠서 봐야 한다.
```plain text
SSE / Thread / Memory 문제
→ 8차에서 안정성 크게 개선

Hot Auction DB Lock 문제
→ 여전히 남음
```
즉 RAM 증설과 Virtual Thread가 DB Row Lock 문제를 해결한 것은 아니다.

---

## 8. 결론
Virtual Thread는 많은 I/O 작업을 다루는 SSE Fan-out 구조를 단순화하는 방향으로 채택할 가치가 있었다.
하지만 실제 안정화 과정에서는 Virtual Thread보다 물리 RAM 부족과 Swap Thrashing이 더 큰 문제였다.
확정된 결론은 다음과 같다.
- 5→6차 개선을 Virtual Thread 단독 효과로 주장하지 않는다.
- 7차에서 GC/Swap 병목을 확인했다.
- 8차 RAM 증설로 Full GC와 Swap Pressure가 크게 감소했다.
- Hot Auction DB Lock은 별도 병목으로 남았다.

**9차 최종 테스트 결과(사후 반영):** 8차와 같은 RAM 1.8GiB/`-Xmx1280m` 설정을 유지한 채 6개 시나리오 전체를 약 76분 동안 다시 돌렸다. 여기서도 Full GC 0회, 진짜 heap OOM 0회를 확인했다. 다만 9차는 `SPRING_PROFILES_ACTIVE=redis` 단일 프로필(플랫폼 스레드, Redis 기반 SSE fan-out)로 돌린 터라 8차의 `local-sse,sse-virtual-threads` 조합과 완전한 A/B 비교는 아니다. Virtual Thread 자체의 효과만 격리해 보려면 8차와 같은 프로필로 라운드를 한 번 더 잡아야 한다. Hot Auction DB Lock 문제는 이 페이지가 아니라 [6.6 Redis 기반 입찰 처리 전환](Performance-Redis-기반-입찰-처리-전환)에서 다른 경로로 풀렸다. 전체 수치는 [6.8 9차 최종 부하 테스트](Performance-9차-최종-부하-테스트) 참고.

> 이 문서는 codex의 도움을 받아 작성하였습니다

<!-- HUMANIZE-SUMMARY v1.6.1
run_id: 2026-08-16-004
genre: 리포트 (팀 기술 위키, 부하테스트 회고)
scope: 사용자 지정 부분 윤문 — "1. 배경 및 문제 상황" 2문장 + "9차 최종 테스트 결과" 문단만 대상. 나머지 본문·표·수치·다이어그램·링크는 원문 그대로 유지.
metrics:
  char_in: ~3,480
  char_out: ~3,500
  change_rate: ~7.2% (지정 구간 기준 ~46%, 문서 전체 기준)
  self_check: 6/6
  grade: A
categories:  # before → after (지정 구간 한정)
  A-18 좌향 수식 누적 장문: 2 → 0
  A-10 "~할 수 있다고 판단해" hedge: 1 → 0
  A-5/A-19 "~에서 오는" 조사 번역투: 1 → 0
  E-1 100자 초과 장문 연속: 3 → 0
  대시(—) 접속 삽입구: 1 → 0
  E-2 "재확인/재검증" 중복 접두: 2 → 0
self_check:
  - 고유명사·수치·인용 100% 보존: ✅ (1.8GiB, -Xmx1280m, 76분, 0회, 프로필명, 위키 링크 slug 전부 원형)
  - 변경률 30% 이하: ✅
  - 장르 이탈 없음: ✅ (위키 리포트체 유지)
  - register 보존: ✅ (평서 격식 '~다' 유지, 구어체 하강 없음)
  - S1 잔존 0건: ✅ (지정 구간 기준)
  - 인공 표현 추가 없음: ✅
highlights:
  - id: A-18 + E-1
    before: "SSE Fan-out은 많은 독립 I/O 작업을 만들기 때문에 기존 Bounded Thread Pool보다 Virtual Thread가 더 적합할 수 있다고 판단해 5차부터 도입을 시도했다."
    after: "SSE Fan-out은 독립적인 I/O 작업을 대량으로 만든다. 기존 Bounded Thread Pool보다 Virtual Thread가 더 적합할 것으로 보고 5차부터 도입을 시도했다."
  - id: A-5
    before: "그런데 실제로는 Virtual Thread 자체의 효과보다 작은 물리 RAM에서 오는 GC/Swap 문제가"
    after: "막상 돌려 보니 Virtual Thread 자체의 효과보다 물리 RAM이 작아서 생기는 GC/Swap 문제가"
  - id: E-1
    before: "…6개 시나리오 전체(약 76분)를 다시 돌렸을 때도 Full GC 0회, 진짜 heap OOM 0회를 재확인했다."
    after: "…6개 시나리오 전체를 약 76분 동안 다시 돌렸다. 여기서도 Full GC 0회, 진짜 heap OOM 0회를 확인했다."
  - id: 대시 삽입구 제거
    before: "완전한 A/B는 아니다 — Virtual Thread 자체의 효과만 다시 격리해서 재검증하려면 8차와 같은 프로필로 별도 라운드가 필요하다."
    after: "완전한 A/B 비교는 아니다. Virtual Thread 자체의 효과만 격리해 보려면 8차와 같은 프로필로 라운드를 한 번 더 잡아야 한다."
residual_findings:
  - id: C-11
    severity: S1(단발)
    where: "5. 7차 — GC / Swap 분석 / '…Swap으로 밀어내고, GC가 다시…'"
    reason: "기검수 원문 구간이라 사용자 지시(2개소만 수정)에 따라 미수정. 문서 전체 1회뿐이라 AI 신호 강도는 낮음(6회+가 강신호)."
  - id: H-4
    severity: S2
    where: "5절·7절 문두 '즉' 2회"
    reason: "기검수 원문 구간, 범위 밖. 2회는 임계 이하."
grade_reason: "A — 지정 구간 S1 잔존 0건, 수치·표·링크·코드 100% 보존, 자체검증 6항 통과. 전체 변경률 7.2%는 사용자가 범위를 2개소로 한정한 결과이며 과소윤문이 아님."
-->
