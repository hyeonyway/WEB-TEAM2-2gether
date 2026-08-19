# 부하테스트 운영 가이드 — 서버 접근 · 실행 · 보고서 작성법

이 문서는 회고나 설계 문서가 아니라 **매번 부하테스트를 돌릴 때 그대로 따라
하는 런북**이다. 1~11번 문서(k6 시나리오 설계, SLO, 계측, 5~8차 결과)는
"왜 이렇게 만들었는지"를 담고 있고, 이 문서는 "그래서 실제로 뭘 어떻게
치는지"만 담는다. 새로 합류한 사람이나 다른 PC에서 그대로 재현할 수 있게
쓴다.

---

## 1. 서버 접근

### 1.1 대화형 접속 (`aws-ssh.sh`)

리포지토리 상위(`../aws-ssh.sh`, 즉 `WEB-TEAM2-2gether`와 같은 부모
디렉터리)에 있다. 인자 하나만 받고 그대로 인터랙티브 SSH 세션을 연다 —
커맨드 전달(`aws-ssh.sh backend "명령어"` 같은 형태)은 지원 안 함.

```bash
../aws-ssh.sh backend      # 백엔드 EC2 (ubuntu, ap-northeast-2)
../aws-ssh.sh db           # DB — private subnet이라 backend를 점프호스트로 내부적으로 경유
../aws-ssh.sh nat          # NAT 인스턴스
../aws-ssh.sh monitoring   # Grafana/Prometheus 호스트
../aws-ssh.sh db-example   # DB 예시/참고용 인스턴스
```

키 파일은 `aws-keys/` 밑에 있고 스크립트가 실행 시점에 `chmod 400`을 매번
걸어준다.

### 1.2 스크립트로 서버 명령 실행할 때 (비대화형)

`aws-ssh.sh`는 인자 패스스루가 안 되므로, 로그 확인·모니터링 쿼리처럼
자동화가 필요한 작업은 스크립트 안의 호스트/키 정보를 그대로 꺼내 직접
`ssh` 커맨드를 쓴다:

```bash
BACKEND_HOST="ec2-54-116-168-19.ap-northeast-2.compute.amazonaws.com"
BACKEND_KEY="../aws-keys/2gether-backend-key.pem"

ssh -i "$BACKEND_KEY" "ubuntu@$BACKEND_HOST" "docker logs --tail 100 <container>"
```

DB는 private subnet이라 backend를 점프호스트로 거쳐야 한다:

```bash
DB_HOST="10.0.10.159"
DB_KEY="../aws-keys/2gether-db-key.pem"

ssh -i "$DB_KEY" \
  -o ProxyCommand="ssh -i $BACKEND_KEY -W %h:%p ubuntu@$BACKEND_HOST" \
  "ubuntu@$DB_HOST" "mysql -e '...'"
```

**호스트명이 바뀔 수 있다** — 특히 모니터링 호스트는 이번 세션 중에도
IP가 바뀌어서 `Host key verification failed`가 났다. 테스트 시작 전에
`aws-ssh.sh` 안의 호스트명이 최신인지 한 번 확인하고, 바뀌었으면
`ssh-keygen -R <옛호스트명>` 으로 known_hosts 정리 후
`-o StrictHostKeyChecking=accept-new`로 재접속하면 된다.

### 1.3 모니터링 (Grafana/Prometheus)

모니터링 호스트에 SSH로 들어가서 `curl localhost:9090/api/v1/query...`
형태로 Prometheus HTTP API를 직접 치는 게 제일 빠르다. **range query로
"최근 N시간"을 조회할 때 epoch timestamp는 로컬에서 오프셋 계산하지 말고
반드시 원격 호스트에서 직접 계산할 것**:

```bash
# 원격에서 직접 계산 — 로컬 시계/오프셋 계산 실수로 미래 시각이 되는 사고를 막는다
ssh ... "start=\$(($(date -u +%s)-7200)); end=\$(date -u +%s); \
  curl -s 'localhost:9090/api/v1/query_range?query=...&start='\$start'&end='\$end'&step=15s'"
```

과거 이 문서 작성 세션에서 로컬 계산 실수(빼야 할 걸 더해서 미래
타임스탬프가 됨)로 "데이터 없음"을 겪은 적 있다 — 위 패턴으로 방지한다.

---

## 2. 부하테스트 실행

### 2.1 스크립트 위치

```
backend/src/test/k6/scenarios/
├── pure-throughput.js        # QPS 계단(250/500/1000), SSE 포함
├── hot-auction-pattern.js    # 실사용 패턴 근사, SSE 포함
└── bid-only-load.js          # SSE 없이 입찰/조회만 (분산 또는 HOT_AUCTION_ID로 집중)
```

세션 인증 기반이다(JWT 아님, `#469`로 전환 완료). 로그인 후
`SESSION` 쿠키 + `csrfToken`을 받아서, GET은 `Cookie` 헤더만, POST/PUT/
PATCH/DELETE는 `Cookie` + `X-CSRF-Token`을 같이 보낸다
(`SessionCsrfFilter` 요구사항). SSE 연결도 티켓 없이 세션 쿠키만으로
`GET /api/me/notifications/stream`에 바로 연결한다.

CLI 대신 로컬 GUI로 돌리고 싶으면 `backend/src/test/k6/dashboard/server.py`
(표준 라이브러리만 사용, 설치 불필요)를 실행하고 `http://127.0.0.1:8787`에
접속한다. 시나리오 3개를 드롭다운으로 고르고 주요 파라미터를 폼으로 채워서
실행·중지·실시간 로그 확인까지 가능하다. 시나리오별로 SSE 바이너리
(`sse/k6-sse`)와 일반 `k6`를 자동으로 선택해준다.

### 2.2 표준 실행 세트 (한 차수 = 아래 6개 실행)

```bash
cd backend/src/test/k6

# 1) pure-throughput 3종 (250/500/1000 tier)
# sse/x/sse를 import하므로 일반 k6가 아니라 sse/k6-sse 바이너리로 돌려야 한다.
BASE_URL=https://api.dbidding.shop sse/k6-sse run -e SSE_VUS=250  scenarios/pure-throughput.js
BASE_URL=https://api.dbidding.shop sse/k6-sse run -e SSE_VUS=500  scenarios/pure-throughput.js
BASE_URL=https://api.dbidding.shop sse/k6-sse run -e SSE_VUS=1000 scenarios/pure-throughput.js

# 2) hot-auction-pattern (마찬가지로 sse/k6-sse 필요)
BASE_URL=https://api.dbidding.shop sse/k6-sse run scenarios/hot-auction-pattern.js

# 3) bid-only-load — 분산 (SSE 없음, 일반 k6로 충분)
BASE_URL=https://api.dbidding.shop k6 run scenarios/bid-only-load.js

# 4) bid-only-load — 핫경매 집중 (특정 경매 하나에 몰아서 순수 락 경합 한계 측정)
BASE_URL=https://api.dbidding.shop \
  HOT_AUCTION_ID=<시드된 경매ID> \
  k6 run scenarios/bid-only-load.js
```

주요 환경변수 (`bid-only-load.js` 기준, 다른 스크립트도 대부분 공유):

| 변수 | 기본값 | 용도 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 대상 서버 |
| `QPS_STAGES` | `50,100,150,200,300,400` | 콤마구분 QPS 계단 |
| `STAGE_DURATION` | `2m` | 계단 하나당 유지 시간 |
| `REST_DURATION` | (없음) | 지정하면 계단 사이에 낮은 target으로 쉬는 구간 삽입 (예: `5s`) |
| `REST_TARGET` | `0` | 쉬는 구간의 target QPS |
| `HOT_AUCTION_ID` | (없음) | 지정하면 입찰이 이 경매 하나로만 몰림 |
| `LOAD_TEST_USER_COUNT` | `500` | 로그인시킬 테스트 계정 수 |
| `LOGIN_BATCH_SIZE` | `25` | 로그인 배치 크기 |
| `K6_RESULT_FILE` | (없음) | 지정하면 요약 JSON 저장 (`--summary-export`와 유사) |

`bid-only-load.js`에는 없고 다른 두 스크립트에만 있는 변수:

| 변수 | 스크립트 | 기본값 | 용도 |
|---|---|---|---|
| `SSE_VUS` | `pure-throughput.js` | `250` | SSE 동시접속 tier, `250`/`500`/`1000`만 허용 |
| `SSE_USERS` | `hot-auction-pattern.js` | `500` | SSE 동시접속 유저 수 |
| `HOT_AUCTION_COUNT` | `hot-auction-pattern.js` | `3` | 핫 경매 개수 |
| `HOT_AUCTION_RATE` | `hot-auction-pattern.js` | `14` | 핫 경매 1개당 초당 입찰 |
| `DURATION` | `hot-auction-pattern.js` | `5m` | 본 구간 유지 시간 |

### 2.3 실행 전 체크리스트

1. **재배포 확인** — JVM 옵션(`-Xmx` 등), 프로필(`SPRING_PROFILES_ACTIVE`),
   HikariCP 풀 크기가 의도한 값인지 배포 서버에서 직접 확인
   (`docker inspect <container> --format '{{json .Config.Env}}'` 또는
   `docker exec <container> env`). 과거 이 세션에서 `-Xmx384m`(실험용
   잔재)이 그대로 배포돼 있던 걸 놓칠 뻔한 적 있다.
2. **모니터링 주소 확인** — §1.3, IP 바뀌었는지.
3. **웜업 대기** — 재배포 직후면 JIT/커넥션풀/캐시가 덜 데워진 상태라
   최소 10분 정도 실트래픽이나 저강도 트래픽으로 흘려보낸 뒤 시작한다.
4. 시드 데이터가 있는지 (`GET /api/test/load/sse-status`로 확인 가능,
   인증 불필요) — 없으면 `seed/seed-load-test-auctions.js`로 즉시낙찰
   안 걸리는 경매를 미리 만들어둔다.

---

## 3. 로컬 재현 환경

prod에 직접 부하를 걸지 않고 **`performance_schema.data_lock_waits`,
slow query log 등을 마음대로 켜놓고 실시간으로 봐야 할 때** 로컬에서
그대로 재현할 수 있다. 이 프로젝트 로컬 개발용 docker 스택이 이미
구성되어 있다(테스트 시점 기준):

```
dbidding-backend-redis     backend, redis 프로필,  포트 8080/9091(mgmt)
dbidding-backend-redis-b   backend, 다른 브랜치용, 포트 8081/9092(mgmt)
dbidding-redis             Redis,                  포트 6379
mysql8                     MySQL 8.4,              포트 3306
dbidding-frontend-redis    프론트,                  포트 5173
```

`docker ps`로 존재 여부 확인 먼저 할 것 — **이미 다른 세션/팀원이 띄워둔
공용 상태일 수 있으니, 함부로 새로 만들거나 flush하기 전에 반드시 확인**.

### 3.1 k6 설치

```bash
brew install k6
```

### 3.2 로컬 실행 시 prod와 다른 점 (재현 정확도에 영향)

- **HikariCP 풀 크기 기본값이 다르다.** prod는
  `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30`으로 명시 설정돼 있는데,
  로컬 컨테이너를 그냥 띄우면 Spring Boot 기본값(10)으로 뜬다. prod
  커넥션풀 현상을 재현하려면 컨테이너를 **이 env var를 추가해서
  재생성**해야 한다(같은 이미지로 `docker stop/rm` 후 `docker run`
  재생성, 기존 env 전부 유지하고 이 값만 추가).
- 컨테이너 안에서 `PASSWORD_HASH_ITERATIONS=100`처럼 테스트용으로 완화된
  값이 있을 수 있다 — prod와 비교할 땐 이 차이를 감안한다.

### 3.3 절대 하지 말 것 — `redis-cli flushall`

**로컬 `dbidding-redis`를 `FLUSHALL` 하면 안 된다.** 이미 떠 있는
백엔드 컨테이너들이 이 Redis를 실서비스처럼 쓰고 있어서, flush하는 순간
Stream 컨슈머 그룹(`event:timeline` 등)이 사라져
`NOGROUP No such key` 에러가 나고 leader lock도 깨진다.

- 특정 키 패턴만 지워야 할 때는 `SCAN`으로 걸러서 그 키들만 `DEL`한다
  (예: 테스트 계정 지갑 캐시만 초기화하고 싶으면
  `wallet:balance:<범위>`만 지운다).
- 실수로 이미 flush했다면: 영향받은 백엔드 컨테이너를 재기동하면
  `RedisAuctionStateWarmUp`이 MySQL에서 다시 채워준다. **단, 여러
  컨테이너를 동시에 `docker restart`하면 안 된다** — 컨테이너마다 PID
  1이라 스키마 체크용 임시 DB 이름(`{db}_schema_check_$$`)이 겹쳐서
  `Table 'shedlock' already exists` 로 죽는다. **반드시 한 번에 하나씩,
  이전 컨테이너의 헬스체크(`/actuator/health`)가 200 뜨는 걸 확인한
  뒤 다음 컨테이너를 재기동**한다.

### 3.4 락 대기 실시간 관측

```sql
-- consumer 활성화 불필요, InnoDB 락 인스트루먼트는 기본 on
SELECT * FROM performance_schema.data_lock_waits;
```

2초 간격 폴링 스크립트로 테스트 내내 찍어두면, Hikari 풀이 꽉 찼을 때
그게 **진짜 row-lock 경합 때문인지 아니면 그냥 풀 크기 대비 처리량
부족인지** 구분할 수 있다(후자면 이 테이블이 테스트 내내 0으로 나온다).

### 3.5 Redis 커맨드 실측(MONITOR)으로 캐시 미스 여부 검증하기

특정 Lua 스크립트(예: `wallet-bootstrap.lua`)가 실제로 몇 번, 어떤
키에 대해 실행됐는지 보려면:

```bash
# 1) 실제 실행 중인 스크립트의 SHA를 직접 확인 (로컬 파일로 재계산 X —
#    컨테이너 이미지의 .lua 파일이 워킹트리보다 오래됐을 수 있어 SHA가
#    다를 수 있다. 실제 트래픽에서 MONITOR로 찍히는 SHA를 써야 정확함)
redis-cli -h 127.0.0.1 -p 6379 monitor > /tmp/monitor.log &
# ... 트래픽 발생 ...
grep -aio '"EVALSHA"' /tmp/monitor.log  # 대소문자/바이너리 세션 데이터 있으면 grep -a 필수

# 2) 확인된 SHA로 특정 키 패턴 재호출 횟수 집계
grep -a '"<확인된SHA>"' /tmp/monitor.log \
  | grep -oaE '"wallet:balance:[0-9]+"' | sort | uniq -c | sort -rn
```

`grep`이 바이너리로 인식하면(Java 직렬화된 세션 값 등이 로그에 섞여
있으면) 매치가 전부 조용히 사라진다 — **항상 `-a`를 붙인다.**

---

## 4. 보고서 작성 규칙

부하테스트 결과 문서는 `docs/hyeonmoon/observability/`에
`N-round{차수}-....md` 형식으로 넣는다(기존 7~11번 문서가 예시).

### 4.1 필수 포함 항목

1. **각 실행(시나리오)별 P95/P99** — 전체 실행 기준 종합표 하나에
   시나리오 6개를 행으로, `med/p95/p99/max`를 열로 넣는다
   (10번 문서 §1 표 형식 그대로).
2. **Stage(구간)별 P95/P99** — 종합표만으로는 "어느 QPS 계단부터
   무너지기 시작했는지"가 안 보인다. 시나리오마다 QPS 계단별로 별도
   소제목을 두고, 그 안에 API별 요청수/평균/p95/p99를 다시 표로 넣는다
   (10번 문서 §2 형식). **SSE 없는 시나리오도 예외 없이 같은 깊이로
   넣는다** — "SSE가 있어야 자세히 본다"는 편의적 생략을 하지 않는다.
3. **직전 차수와의 비교** — 최소한 조건이 같은 실행(보통 1000-tier나
   가장 부담이 컸던 시나리오)을 이전 차수들과 나란히 비교하는 표를
   따로 둔다(10번 문서 §1 "4차례 재측정 비교" 형식). 조건이 달라졌으면
   (RAM/스레드모델/스케줄러 on-off 등) 그 차이를 표 위에 명시한다.
4. **p95/p99는 서버 실측 기준을 우선** — 가능하면 k6 클라이언트 값이
   아니라 서버의 `http_server_requests_seconds_bucket`을
   `histogram_quantile()`로 그 구간 끝 시각 기준으로 계산한 값을 쓴다
   (클라이언트 값은 네트워크/큐잉 지연이 섞여 서버 처리시간과 다르다).
5. **분량 제약 없음** — 문서가 길어지는 걸 이유로 정보를 줄이지 않는다.
   과거 요청사항: "문서가 길어져도 괜찮으니까 최대한 많은 정보를
   담아줘야함."

### 4.2 문서 구조 템플릿

```markdown
# N차 부하테스트 — <한줄 목적>

**대상 환경:** prod(호스트, 스펙, JVM 옵션, 활성 프로필 전부 명시)
**작성일:** YYYY-MM-DD, <선행 문서> 대비 뭐가 바뀌었는지
**배경:** 이번 테스트를 왜 하는지 (이전 결론의 무엇을 검증/반박하는지)

---

## 0. 결론 먼저

(가장 중요한 발견 1~2문장 + 핵심 숫자)

## 1. 정량 데이터 종합표 (전체 실행)

(시나리오 × [총요청/실패율/med/p95/p99/max/SSE연결/GC/OOM 등] 표)

### 직전 차수와 비교

(같은 조건 실행을 라운드별로 나열한 표)

## 2. 구간별 + API별 상세

(시나리오마다: 구간별 완료수/시스템 지표 표 → 구간마다 API별 요청수/평균/p95/p99 표)

## 3. (필요시) 근본원인 분석 / 이슈 연결

## 4. 한계 및 다음 단계
```

---

## 5. 알려진 함정 모음

- **k6 `setup()`은 단일 VU 취급** — `http.batch()`로 여러 계정을
  동시에 로그인시키면 그 배치 안 요청들이 쿠키 jar를 공유해서, 응답
  도착 순서가 꼬이면 A 계정 요청이 B 계정 세션 쿠키를 주워가는 사고가
  난다(`#500`으로 조사 후 서버 버그 아님으로 결론, k6 스크립트 버그로
  확정). 고치는 법: 로그인 요청마다 `params.jar`에 `new
  http.CookieJar()`를 개별로 넣는다.
- **JWT→세션 전환(`#469`) 이후 k6 스크립트도 반드시 같이 바꿀 것** —
  `Authorization: Bearer` 대신 `Cookie: SESSION=...`, 쓰기 요청엔
  `X-CSRF-Token` 헤더 추가, SSE는 티켓 발급 없이 세션 쿠키로 바로 연결.
- **`brew services`로 로컬 mysql이 하나도 안 뜨면** — launchd가 이
  실행 환경(샌드박스/비대화형 셸)에서 제대로 동작 안 할 수 있다.
  `mysqld_safe`를 직접 백그라운드로 띄우는 게 더 안정적이다(단, 그 전에
  이미 팀 공용 docker mysql 컨테이너가 없는지부터 확인 — 있으면 새로
  깔 필요 자체가 없다, §3 참고).
- **Redis maxmemory-policy가 `noeviction`이면** 캐시가 예상 없이
  사라지는 원인에서 "LRU 축출"은 배제해도 된다 — `INFO memory`/
  `INFO stats`의 `evicted_keys`로 바로 확인 가능.

---

> 이 문서는 Claude의 도움을 받아 작성하였습니다
