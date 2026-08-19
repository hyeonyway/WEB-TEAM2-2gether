# 6.8 9차 최종 부하 테스트

## Redis Profile 운영 조건

#529(경매 목록 조회 Redis 중복조회 제거)가 main에 머지된 직후, 8차와 같은 6개 시나리오 표준 세트를 다시 돌렸다. 대상 환경은 다음과 같다.

- prod(`api.dbidding.shop`), RAM 1.8GiB, vCPU 2개, `-Xmx1280m -XX:+UseG1GC`
- `SPRING_PROFILES_ACTIVE=redis` 단일 프로필, HikariCP max=30

8차는 `local-sse,sse-virtual-threads` 프로필에 JVM 로컬 SSE fan-out + 가상스레드, `NOTIFICATION_RECOVERY_NON_URGENT_ENABLED=false` 조건이었으니 이번과는 조건이 다르다. 그래서 이번 라운드는 실험 조건을 재현한 게 아니라 **지금 실제로 배포돼 있는 그대로**(플랫폼 스레드, Redis Pub/Sub 기반 SSE, notification 복구 스케줄러 기본 켜짐)를 잰 스냅샷이다. 8차와의 절대값 비교는 RAM 증설 효과가 다시 나오는지 확인하는 용도로만 쓴다. 프로필이 다르다는 걸 감안하지 않은 채 모든 차이를 이번에 고쳐진 것으로 해석하지는 않는다.

## 동일 표준 시나리오 재측정

8차와 같은 6개 시나리오(pure-throughput 250/500/1000, hot-auction-pattern, bid-only-load 분산/핫경매집중)를 순서대로 총 76분에 걸쳐 실행했다.

<table header-row="true">
<tr>
<td>시나리오</td>
<td>총 요청</td>
<td>http_req_failed</td>
<td>med(ms)</td>
</tr>
<tr>
<td>pure-throughput 250</td>
<td>133,935</td>
<td>0.19%</td>
<td>382</td>
</tr>
<tr>
<td>pure-throughput 500</td>
<td>135,111</td>
<td>2.47%</td>
<td>580</td>
</tr>
<tr>
<td>pure-throughput 1000</td>
<td>134,567</td>
<td>12.55%</td>
<td>1,866</td>
</tr>
<tr>
<td>hot-auction-pattern</td>
<td>123,943</td>
<td>1.32%</td>
<td>262</td>
</tr>
<tr>
<td>bid-only-load 분산</td>
<td>132,758</td>
<td>0.00%</td>
<td>306</td>
</tr>
<tr>
<td>bid-only-load 핫경매집중</td>
<td>141,632</td>
<td>0.00%</td>
<td>141</td>
</tr>
</table>

> 📷 **캡처 위치** — 6개 시나리오 실행 구간을 표시한 Grafana 타임라인(2026-08-16 04:31~05:47 UTC).

## MySQL / Redis / Backend 지표

이번 회차부터는 서버·Prometheus 지표에 더해 DB(MySQL)와 Redis 원본 데이터도 직접 수집했다.

8차 대비 가장 크게 달라진 쪽은 Hikari와 Hot Auction이다. `bid-only-load 핫경매집중` 시나리오에서 6개 시나리오 전체 Hikari active 최댓값이 10/30을 넘지 않았다(8차는 매 시나리오 30/30 포화). 같은 시나리오 p95도 52,506ms(8차)에서 90~140ms대로 떨어졌다. 원인은 [6.6](Performance-Redis-기반-입찰-처리-전환)에서 다룬 `RedisBidExecutor` 전환이다. 입찰 승인 경로에 MySQL이 아예 안 낀다.

목록 조회 p95도 내려왔다. [6.7](Performance-Redis-Read-Path-최적화)의 #529 수정 덕에 `GET /api/auctions` p95가 220~630ms대다. #529 수정 전 기준선(같은 세션에서 확인한 값)인 1,100~1,400ms와 견주면 55~75% 감소다.

GC와 스왑은 8차와 마찬가지로 안정적이었다. 76분 세션 동안 Full GC 0회, 진짜 heap OOM 0회. 스왑은 여전히 발생하지만(hot-auction-pattern 기준 pswpout 최대 163page/s) 8차의 가장 심한 구간(235.7/312.5page/s)보다는 낮다.

MySQL `Innodb_row_lock_current_waits`는 세션 내내 순간값 0이었지만 누적 `Innodb_row_lock_waits`는 14,686건 늘었다. 짧게 걸렸다 바로 풀리는 대기가 계속 있었다는 얘기다. Redis는 `total_error_replies`가 세션 중 75,805건 늘었다. 같은 세션에서 `auction:bid:idempotency:*` 키가 74,669개 늘어난 것과 규모가 맞아떨어지니 정책 거부(400/409) 응답이 주된 원인으로 보인다. 정확한 내역까지는 이번에 확정하지 않았다.

## Tomcat 스레드 50 → 80 실험

pure-throughput 고QPS 구간마다 `tomcat_connector_threads_busy`가 설정값(50)에 그대로 붙는 걸 보고 스레드를 80으로 올려 비슷한 부하 수준에서 다시 쟀다. 결과는 반대로 나왔다. CPU가 90~94%까지 올라가면서(50스레드일 때는 피크 88%) 목록 p95가 852ms까지 나빠졌다. 2vCPU 환경에서는 스레드 수보다 CPU 자체가 먼저 병목이 되니 스레드를 더 늘려봐야 컨텍스트 스위칭 비용만 커졌다. 설정은 이 실험 뒤 50으로 되돌렸다.

## 남은 병목 분석

- 목록 조회는 여전히 다른 API보다 2~4배 느리다. 항목당 Redis read가 20회 남아 있으니 구조적으로 당연한 차이지만 완전히 없앤 건 아니다.
- 입찰 응답을 돌려주기 전에 지갑·경매 Redis PUBLISH가 동기로 일어난다. 응답 지연에 직접 영향을 줄 수 있는 구조라 별도 이슈로 남겨뒀다.
- 세션 인증으로 바꾼 뒤로는 인증된 요청마다 Redis read+write가 붙는다. `spring.session.redis.*` 튜닝은 아직 손대지 못했다.
- 콜드시드 rewind로 프로젝션 파이프라인이 전역으로 멈추는 사고를 이 세션 중에 실제로 겪었다. MySQL `timeline_events`에 이전 최고입찰자 불일치 오류 1건이 낀 채 62,506건이 밀려 쌓였다. 원인을 캐 보니 100% 시드 테스트 경매였고 실고객 영향은 없었다. 다만 프로젝션 오류 하나가 전체를 막는 구조 자체는 팀이 의도한 설계다(정합성 깨지면 운영자가 확인). 세션 종료 후 별도로 정리했다.
- Tomcat 스레드는 이미 2vCPU 한계에 붙어 있다. 더 올리는 방향으로는 안 풀린다. vCPU 증설이나 가상스레드 쪽으로 가야 한다.

## 결론

같은 조건에서 8차 대비 확실히 개선된 지표(목록 p95, Hot Auction 락 경합, Hikari 사용량)와 프로필 차이 탓에 아직 완전한 A/B가 아닌 지표(SSE 처리 방식, 1000-tier 실패율)는 나눠서 봐야 한다. 전체 원본 데이터(35개 구간 × 전체 메트릭)는 메인 저장소 `docs/hyeonmoon/observability/raw-data/9-round9-prometheus-raw-data.md`와 `docs/hyeonmoon/observability/13-round9-redis-executor-full-suite.md`에 있다. 위키가 아니라 코드 저장소 쪽 문서다.

> 이 문서는 codex의 도움을 받아 작성하였습니다

<!-- HUMANIZE-SUMMARY v1.6.1
run_id: 2026-08-16-007
genre: 리포트 (팀 기술 위키)
metrics:
  char_in: ~3,010
  char_out: ~2,980
  change_rate: ~20%
  self_check: 6/6
  grade: A
categories:  # before → after
  E-1 em-dash 동일 리듬 부연절(결론문 — 부연): 6 → 0
  J-1 문단·불릿 볼드 리드인 "**X.**": 9 → 1 (의미 강조 1건만 존치)
  C-11 연결어미(-고/-지만/-어서) 직후 쉼표: 3 → 0
  J-2 scare quote 강조: 3 → 0
  I-3 "~다는 뜻이다" 결말: 1 → 0
  A-18 괄호 중첩 좌향 수식(8차 조건 나열): 1 → 0 (절로 분리)
self_check:
  - 고유명사·수치·인용 100% 보존: OK (표 6행 24셀, 133,935/135,111/134,567/123,943/132,758/141,632, 0.19/2.47/12.55/1.32/0.00/0.00%, 382/580/1,866/262/306/141, 52,506ms, 90~140ms, 220~630ms, 1,100~1,400ms, 55~75%, 163page/s, 235.7/312.5page/s, 14,686, 75,805, 74,669, 62,506, 76분, 90~94%, 88%, 852ms, 10/30, 30/30, 20회, 2~4배, 35개 구간, 1.8GiB, 2vCPU, 50→80 전부 원형)
  - 코드·설정값 백틱 span 무수정: OK (`api.dbidding.shop`, `-Xmx1280m -XX:+UseG1GC`, `SPRING_PROFILES_ACTIVE=redis`, `local-sse,sse-virtual-threads`, `NOTIFICATION_RECOVERY_NON_URGENT_ENABLED=false`, `tomcat_connector_threads_busy`, `RedisBidExecutor`, `GET /api/auctions`, `Innodb_row_lock_current_waits`, `Innodb_row_lock_waits`, `total_error_replies`, `auction:bid:idempotency:*`, `timeline_events`, `spring.session.redis.*`, raw-data 경로 2건)
  - HTML <table>·"> 📷" 콜아웃·위키 링크 [6.6]/[6.7] 원형 보존: OK
  - 변경률 30% 이하: OK
  - 장르·register 보존: OK (리포트 반말 서술체 "~다" 유지)
  - S1 잔존 0건 / 인공 표현 추가 없음: OK
highlights:
  - id: E-1
    before: "원인은 [6.6](...)에서 다룬 `RedisBidExecutor` 전환이다 — 입찰 승인 경로에 MySQL이 아예 안 낀다."
    after: "원인은 [6.6](...)에서 다룬 `RedisBidExecutor` 전환이다. 입찰 승인 경로에 MySQL이 아예 안 낀다."
  - id: J-1
    before: "**Hikari와 Hot Auction — 8차 대비 가장 큰 변화.** `bid-only-load 핫경매집중` 시나리오에서…"
    after: "8차 대비 가장 크게 달라진 쪽은 Hikari와 Hot Auction이다. `bid-only-load 핫경매집중` 시나리오에서…"
  - id: A-18
    before: "이 조건은 8차(`local-sse,sse-virtual-threads`, JVM 로컬 SSE fan-out + 가상스레드, `NOTIFICATION_...=false`)와 다르다."
    after: "8차는 `local-sse,sse-virtual-threads` 프로필에 JVM 로컬 SSE fan-out + 가상스레드, `NOTIFICATION_...=false` 조건이었으니 이번과는 조건이 다르다."
  - id: J-2
    before: "\"실험 조건을 재현\"한 게 아니라 … 모든 차이를 \"이번에 고쳐졌다\"로 해석하지 않는다."
    after: "실험 조건을 재현한 게 아니라 … 모든 차이를 이번에 고쳐진 것으로 해석하지는 않는다."
  - id: I-3
    before: "…14,686건 늘었다 — 짧게 걸렸다 바로 풀리는 대기가 계속 있었다는 뜻이다."
    after: "…14,686건 늘었다. 짧게 걸렸다 바로 풀리는 대기가 계속 있었다는 얘기다."
residual_findings: (없음)
grade_reason: "A — S1 잔존 0건, 변경률 약 20%, 자체검증 6항 통과. 표·콜아웃·백틱·위키 링크 전량 원형 보존, 리포트 register 그대로."
-->
