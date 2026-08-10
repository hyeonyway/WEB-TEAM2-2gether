# Grafana 대시보드 재구성

**배경:** 지금 `monitoring/grafana/dashboards`에 5개(`01-load-test-overview`,
`02-spring-http-jvm`, `03-infrastructure`, `04-mysql`,
`05-auction-bid-wallet`)로 쪼개져 있어 한눈에 상태를 보기 어렵다. Google의
Four Golden Signals(Latency/Traffic/Errors/Saturation) 프레임워크로 재확인해
보니, 지금까지 찾은 관측 공백들이 정확히 이 4개 시그널 각각에 하나씩
대응됐다 — 그래서 새 대시보드도 이 구조를 그대로 쓴다.

**이 문서는 이슈 #332 소속이지만, `/Users/admin/Documents/softeer/monitoring`이
git 저장소가 아니라 별도의 코드 브랜치가 없다.** 이 저장소(WEB-TEAM2-2gether)에는
문서만 남기고, 대시보드 JSON 자체는 모니터링 서버에 직접 반영한다. 문서는
이 계측을 실제로 만드는 [`feature/331-observability-metrics-instrumentation`](3-metrics-gap-and-instrumentation.md)
브랜치에 같이 실어둔다 — 대시보드가 소비할 메트릭을 만드는 작업과 가장
가깝기 때문이다.

**선행 조건:** [`2-slo-error-budget.md`](2-slo-error-budget.md)(목표치),
[`3-metrics-gap-and-instrumentation.md`](3-metrics-gap-and-instrumentation.md)의
신규 메트릭이 먼저 배포돼 있어야 Traffic(SSE 연결 수), Saturation(Tomcat
커넥터별) 섹션의 패널을 채울 수 있다.

## 구조 — 통합 Overview 1개 + 기존 5개는 드릴다운으로 유지

기존 5개 대시보드는 삭제하지 않고 유지한다(각 영역 담당자가 상세 디버깅할 때
필요). 새로 만드는 건 최상위 **Overview** 대시보드 하나이며, 각 섹션 제목에
해당 상세 대시보드로 가는 Grafana dashboard link를 건다.

### 1. Latency (지연)

- 카테고리별(인증/bid-context/일반조회) p95·p99를 **성공(2xx)과 실패(5xx)
  레이턴시를 분리**해서 나란히 표시 — `http_server_requests_seconds_bucket`에
  이미 `status` 라벨이 있어 쿼리만 바꾸면 됨, 코드 변경 불필요.
- **입찰 쓰기는 2xx/5xx 이분법이 아니라 상태코드별(201/400/409/500)로 전부
  따로** 표시한다 — 400도 락 획득 이후에 거부되는 구조라 락 경합 시
  201과 레이턴시가 크게 안 벌어질 수 있어, 뭉치면 성공 경로의 진짜 비용이
  희석돼 보인다. 근거는 [`2-slo-error-budget.md`](2-slo-error-budget.md)의
  "입찰 쓰기 — 상태코드별 레이턴시" 참고.
- SSE 연결 수립 시간, SSE 이벤트 end-to-end 전달 지연(문서 2에서 추가된
  메트릭).
- 패널마다 [`2-slo-error-budget.md`](2-slo-error-budget.md) 목표치를 임계선으로
  표시.

### 2. Traffic (트래픽)

- 엔드포인트별 RPS(기존 `01`/`02`에서 이관).
- **SSE 동시 연결 수**(auction/notification 스트림별, 문서 2 신규 메트릭).
- MySQL QPS(기존 `04`에서 이관).
- (범위 밖으로 명시) 정적 자산/CloudFront 트래픽은 이 스택의 시야 밖 —
  필요해지면 별도로 CloudWatch 데이터소스 연동 검토.

### 3. Errors (에러)

- 5xx 비율(명시적 실패) — SLO 판정에 쓰는 유일한 지표.
- 정책적 실패(400/409) 비율 — 별도 패널로 계속 추적하되 알람 임계값은
  느슨하게(경합 상황에서 정상적으로 튈 수 있음을 감안).
- **SSE 브로드캐스트 거부(묵시적 실패)** — 문서 2 신규 카운터.
- 5xx의 p50 대 p99 격차 패널 — 격차가 크면 "빠른 실패+느린 실패가 섞여
  있다"는 신호로 해석.

### 4. Saturation (포화도)

- CPU/메모리/스왑(node_exporter, 인스턴스별로 분리해서 표시 — 지금처럼
  섞어서 보여주지 않는다).
- HikariCP pool(active/idle/pending/max), MySQL 커넥션.
- **Tomcat 커넥터별 스레드**(문서 2 신규 메트릭, `main`/`management` 분리).
- 파일 디스크립터, 디스크.
- 각 자원마다 "현재 사용량 / 한계치" 비율로도 하나씩 — "지금 얼마나
  여유있나"를 숫자 하나로 보이게.

### 5. Business (도메인, Golden Signals 범위 밖 — 기존 `05` 그대로 이관)

- 입찰/지갑 관련 커스텀 메트릭(`dbidding_bid_*`, `dbidding_wallet_*`,
  `dbidding_auction_lock_wait_seconds` 등) — 이미 `05-auction-bid-wallet.json`에
  있는 패널을 그대로 가져온다, 신규 작업 아님.

## Alerting

Alertmanager는 설치돼 있지 않다(확인 완료). 새로 컴포넌트를 추가하기보다
**Grafana 자체 Unified Alerting**을 쓴다 — 이미 떠 있는 Grafana 안에서 룰
설정 가능하고 Slack webhook 연동도 간단하다. 알람 채널은 Slack으로 예정이나
**이번 작업 범위에는 포함하지 않는다** — 대시보드 구조가 먼저 안정되고 나서
후속 이슈로 진행한다.

## 완료 기준

- [ ] Overview 대시보드 1개, 4+1 섹션 구조로 생성
- [ ] 각 섹션에 위 패널 전부 포함, SLO 임계선 표시
- [ ] 기존 5개 대시보드는 삭제하지 않고 유지, Overview에서 드릴다운 링크 연결
- [ ] `monitoring/grafana/dashboards/`에 새 JSON 파일 추가(기존 파일 수정 안 함,
      단 이 저장소가 아니라 모니터링 서버 로컬 경로이므로 이 이슈의 PR 대상은
      아님 — 완료 후 이슈 #332에 결과만 코멘트로 남긴다)

> 이 문서는 Claude의 도움을 받아 작성하였습니다
