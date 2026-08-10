# 관측성/부하테스트 개선 계획

모니터링 서버(Grafana/Prometheus) 대시보드가 5개로 쪼개져 있어 한눈에 상태를
보기 어렵고, 성공/실패 레이턴시 구분, SSE 연결 수, 정책적 실패 분류 등 여러
관측 공백이 있다는 문제에서 출발했다. 부하테스트로 실측한 데이터를 근거로
SLO를 정하고, 그 SLO를 실제로 검증할 수 있게 계측을 보강한 뒤, 대시보드를
Four Golden Signals 기준으로 재구성한다. 동시에 Redis 도입 전 기준선을
남기고, 그 기준선을 재현 가능하게 측정할 k6 시나리오를 설계한다.

## 배경

- 부하테스트 중 backend/DB 서버(`t4g.micro`, 2vCPU/903MB, swap 사용 중)의
  물리적 한계, HikariCP/Tomcat 커넥터 설정, wallet_holds 데드락, SSE
  브로드캐스트 fan-out 비용 등을 조사하며 다수의 관측 공백을 발견했다.
- SSE 아키텍처 자체는 [`../realtime/1-sse-architecture.md`](../realtime/1-sse-architecture.md)에서
  이미 "MVP는 전체 연결, 트래픽 문제가 실제로 발생하면 그때 보이는 항목만
  구독"하기로 팀이 합의했다. 시나리오②(실사용 패턴)가 바로 그 트리거 조건에
  해당하는지를 데이터로 확인하는 게 목적 중 하나다.
- 입찰 처리 구간 메트릭(`dbidding_bid_*`)은 이미 `docs/eunki/2026-08-09-bid-load-test-metrics-plan.md`로
  구현되어 있다. 이 계획은 그 위에 SSE/Tomcat/실패분류 쪽 공백만 추가로 메운다.

## 관련 이슈와 문서

문서 하나만으로는 이슈/브랜치를 따로 두지 않고, 머지 순서가 같은 것끼리
묶었다. #330(SLO)은 #334에, #332(Grafana)는 #331에 합쳤다. #333(Redis
베이스라인)은 나머지가 전부 병합된 뒤 마지막에 한 번만 재는 게 맞아서
독립 이슈로 유지한다 — DB/락 지연과 SSE 계측을 따로 두 번 잴 이유가 없다.

| 이슈 | 브랜치 | 포함 문서 | 선행 조건 |
|---|---|---|---|
| #334 | `fix/334-k6-policy-failure-and-scenario-design` | [1. k6 시나리오 설계](1-k6-scenario-design.md), [2. SLO/Error Budget](2-slo-error-budget.md) | 없음 — 제일 먼저 `dev`에 머지 |
| #331 | `feature/331-observability-metrics-instrumentation` | [3. 관측 공백 및 계측 보강](3-metrics-gap-and-instrumentation.md), [4. Grafana 대시보드 재구성](4-grafana-dashboard-redesign.md) | #334 (metrics가 대시보드 패널로 쓰이고, SLO 목표선을 참조함) |
| #333 | `chore/333-redis-pre-migration-baseline` | [5. Redis 도입 전 베이스라인](5-redis-baseline-comparison.md) | #334, #331 둘 다 병합 완료 |

문서 번호(1~5)는 실제로 실행/머지돼야 하는 순서를 그대로 나타낸다:
k6 시나리오가 있어야 뭐라도 돌릴 수 있고 → SLO로 그 결과를 판정할 기준을
잡고 → (다음 이슈에서) 관측 공백을 메우고 → 전부 한 대시보드에 모은 뒤 →
**모든 계측이 갖춰진 상태에서 마지막에 한 번만** Redis 도입 전 베이스라인을
잰다.

인증 성능 개선(#335)은 팀 소유 도메인 폴더인 [`../auth/6-password-hash-cost-tuning.md`](../auth/6-password-hash-cost-tuning.md)에
별도로 둔다(관측성 작업이 아니라 Account 도메인 작업이라 폴더 성격상 분리,
다른 이슈와 의존관계 없음).

## 공통 원칙

- 이 폴더의 계측 변경은 기존 `dbidding_bid_*`, `dbidding_wallet_*`,
  `dbidding_auction_*` 메트릭 이름/태그를 바꾸지 않는다 — 새 메트릭만 추가한다.
- 메트릭 태그에 auction/user/bid/request ID를 넣지 않는다(카디널리티 제약,
  `docs/eunki` 계획과 동일한 제약 유지).
- SLO 윈도우는 30일 rolling 같은 상시 운영 개념 대신 **부하테스트 세션
  단위**로 잡는다 — 이 프로젝트는 상시 트래픽이 아니라 세션 단위로 부하를
  가하는 구조이기 때문이다.

> 이 문서는 Claude의 도움을 받아 작성하였습니다
