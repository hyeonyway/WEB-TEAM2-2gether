# Blue-Green 배포 + Readiness Gate 설계 (#586)

## 배경

`#586`에서 기동 시 Redis warm-up 범위를 "정렬 기준별 상위 500개"에서 "활성(OPEN/ENDING)
경매 전체(안전 상한 이내)"로 넓혔다. 이러면 기동 시간(warm-up이 끝나는 시점)이 이전보다
길어질 수 있는데, 지금 배포 구조는 Spring Boot가 뜨자마자(정확히는 내장 Tomcat이 포트를
열자마자) nginx가 그 컨테이너로 트래픽을 넘길 수 있어서, warm-up이 끝나기 전에 요청이
들어오면 콜드 Redis/DB를 그대로 맞는다.

원하는 배포 순서:

```
DB 초기화 → Redis 초기화 → Spring 시작(Flyway 포함) → Redis warm-up → (준비 완료) → nginx 전환
```

nginx가 무료판이라 능동 health check(업스트림 자동 장애조치)가 없어서, **배포 스크립트가
readiness 엔드포인트를 폴링하다가 준비되면 nginx 쪽을 전환**하는 방식으로 그 역할을
대신해야 한다.

## 결론 — port-swap 방식 blue-green으로 간다

처음엔 "두 컨테이너를 오래 동시 운용하는" 정통 blue-green을 검토했다가, 이론적
최악 케이스(두 인스턴스 모두 부하테스트 피크치 700MiB 동시 도달) 계산으로는 t4g.micro
(RAM 1.8GiB)에서 못 맞을 것 같아서 한 번은 "롤링 재시작(다운타임 감수)"으로 방향을
틀었었다. 그런데 실제 `docker stats` 실측(컨테이너 하나, 250 접속·50 QPS 부하 중)
**665.8MiB / 1.791GiB**를 확인한 뒤 다시 계산해보니 여유가 있는 것으로 나와서,
**최종적으로 port-swap 방식 blue-green(컷오버 순간만 짧게 두 컨테이너가 겹치는 버전)
으로 확정**했다. 자세한 계산은 아래 "메모리 검토" 참고.

## 코드 쪽 준비 상태 (완료)

- `#586`에서 Spring Boot Actuator readiness/liveness probe를 켰다
  (`management.endpoint.health.probes.enabled=true`,
  `management.health.{liveness,readiness}state.enabled=true`).
- `RedisAuctionStateWarmUp`은 `ApplicationRunner`다. Spring Boot는 기본적으로 모든
  `ApplicationRunner`/`CommandLineRunner`가 끝난 뒤에야(`ApplicationReadyEvent` 직전)
  readiness 상태를 `ACCEPTING_TRAFFIC`으로 바꾼다 — 별도 이벤트 발행 코드 없이 warm-up
  완료 시점과 readiness가 자연히 맞물린다.
- 결과적으로 `/actuator/health/readiness`(관리 포트, 기본 `9091`)는 warm-up이 끝나기
  전까지 `503`을 반환하고, 끝나면 `200`을 반환한다. 배포 스크립트는 이 엔드포인트만
  폴링하면 된다.

## 현재 운영 인프라 확인 (SSH로 직접 확인, 2026-08-18)

`backend` 호스트(`ec2-54-116-168-19...`)에서 확인:

- `/home/ubuntu/docker-compose.yml`: `backend` 서비스 하나, `container_name: backend`
  고정, 호스트 포트 `8080`(API)·`9091`(actuator/Prometheus) 고정 바인딩.
- `/home/ubuntu/nginx/conf.d/default.conf`: `upstream backend_server { server backend:8080; }`
  — 도커 DNS 이름으로 단일 백엔드를 가리키는 정적 설정. 컷오버 스크립트가 이 파일의
  `server` 지시어(또는 별도 upstream 파일 include)를 바꿔치기하는 대상이 된다.
- 인스턴스 타입은 `t4g.micro`, RAM은 8차 부하테스트 때 1.8GiB로 증설됨
  (`docs/hyeonmoon/observability/10-round8-ram-upgrade-verification.md`).
- 배포된 컨테이너의 `SCHEMA_FILE` 환경변수가 아직 구 `schema.sql` 경로를 가리킴 —
  `#571`/`#581`(Flyway 전환) 이후 compose 파일이 갱신 안 된 상태. 이 설계와는 별개
  이슈지만, 이번 작업 때 같이 정리하는 게 좋다.

## 메모리 검토

**1차 계산(이론적 최악 케이스, 기각됨):** Grafana `JVM Memory` 패널의 부하테스트 피크치
(Heap Used max 700MiB + Non-heap Used max 225MiB ≈ 925MiB)를 두 인스턴스 모두 동시에
찍는다고 가정하면, `925×2 + OS/nginx/모니터링 오버헤드(350~550MiB) ≈ 2200~2400MiB`로
가용 RAM(~1932MiB)을 넘는다. 이 계산으로 한 번은 blue-green을 접었었다.

**2차 계산(실측, 채택):** 위 계산은 "두 인스턴스 모두 부하테스트 최악 피크에 동시 도달"을
가정한 게 지나치게 비관적이었다. 실제 컷오버 상황은 old가 평소 트래픽(피크가 아님)을
받는 동안 new가 warm-up을 도는 것이라, `docker stats`로 직접 재본 값을 쓰는 게 맞다:

```
컨테이너 1개 실측(250 접속, 50 QPS 부하 중)     665.8 MiB / 1.791 GiB (호스트 전체 풀)

겹치는 동안(old 평소 부하 + new warm-up) 2개    ≈ 1,332 MiB
+ nginx 컨테이너                                ≈    20 MiB
+ 호스트 OS/node_exporter 등 컨테이너 밖 오버헤드 ≈   150~200 MiB
──────────────────────────────────────────────
필요 총량                                       ≈ 1,500~1,550 MiB

가용 RAM (docker stats 기준 호스트 풀)          ≈ 1,791 MiB (≈1,834 MiB)
```

여유 약 250~300MiB. 넉넉하진 않지만 맞는다. 단, 이 실측은 250접속/50QPS라는 특정
조건에서 나온 값이고, 컷오버가 실제 트래픽 피크와 겹치거나 warm-up 자체가 예상보다
무거우면 마진이 줄어들 수 있다 — 그래서 readiness 실패 시 자동 롤백(아래 스크립트
2번 단계)이 안전장치로 반드시 필요하다.

## 컷오버 스크립트 설계

전제: green 컨테이너는 blue와 다른 이름·포트로 뜬다(예: `backend-green`, 호스트 포트
`8081`/`9092`). 컷오버가 끝나면 다음 배포 때는 색이 뒤바뀐다(현재 살아있는 쪽이 무엇이든
그 반대색으로 새 컨테이너를 올리는 방식 — 완전히 대칭적인 blue/green 고정 역할이 아니라
매번 "현재 서빙 중이 아닌 포트"에 새로 올리는 alternating 방식이 실제로는 더 단순하다).

```
1. green 컨테이너 기동 (호스트 포트 8081/9092, docker-compose.yml의 backend 서비스
   정의를 재사용하되 container_name과 포트만 다르게)
2. 반복:
     curl -sf http://localhost:9092/actuator/health/readiness
     200이면 3으로, 그 외엔 N초 대기 후 재시도, 총 대기시간 상한(예: 5분) 넘으면
     green을 내리고 배포 실패로 종료 (blue는 그대로 서비스 계속 — 자동 롤백)
3. nginx conf.d의 upstream backend_server가 green(8081)을 가리키도록 갱신
   (server 지시어 값 교체 또는 include하는 파일 스왑)
4. `nginx -s reload` — 워커 프로세스만 무중단 교체, 기존 연결은 유지한 채 새 연결부터
   새 upstream으로 감
5. 짧은 유예시간(진행 중이던 blue 쪽 요청이 끝날 시간, 예: 30초) 대기
6. blue 컨테이너 정지·제거
7. Prometheus 스크레이프 타겟이 9091 고정이면, green이 실제로 쓰는 포트(9092)로
   컷오버 시점에만 잠깐 어긋난다 — 다음 배포에서 다시 8080/9091로 되돌아오는
   alternating 방식이면 별도 타겟 갱신 없이 짧은 공백만 감수하면 된다. 완전히 없애려면
   두 색 모두 상시 스크레이프 대상에 넣고 `up` 메트릭으로 필터링하는 방법도 있음(후속)
```

readiness 실패 시 자동 롤백(2번 단계에서 타임아웃)이 핵심 — green이 절대 안전장치
없이 바로 트래픽을 받는 일이 없어야 한다.

## 힙 재조정 (별도 작업, 이번에 같이 결정됨)

- `-Xmx1280m` → **`~1000m`로 낮춘다.** 실측 피크(Heap 700MiB)에 GC 촉발 전 급격한
  할당 버스트 대비 여유를 얹은 값.
- round8과 같은 방법론(k6 6-시나리오 + `-Xlog:gc*` 로그, Full GC 발생 여부로 확인,
  반복 실행)으로 **반드시 재검증 후 적용** — round6/7에서 같은 설정으로도 세션마다
  Full GC 발생 횟수가 0~4회로 들쭉날쭉했던 전례가 있어서, 한 번의 관측만으로 확정하면
  위험하다.
- blue-green 컷오버 메모리 마진(§ 메모리 검토)에도 직접 도움이 된다 — 힙 캡을 낮추면
  이론상 순간 최대치가 낮아지므로 여유가 더 늘어난다(단, 실사용은 캡보다 이미 낮았으므로
  체감 효과는 크지 않을 수 있음).

## 구현 완료

- [x] `docker-compose.prod.yml`을 blue/green 두 서비스로 분리하는 구조로 설계.
      **git엔 안 둔다** — 호스트 `~/deploy/docker-compose.prod.yml`이 유일한 원본이고,
      nginx와 같은 이유(인프라 토폴로지, 자주 안 바뀜, 자동 반영되면 구조적으로 깨질
      수 있음)로 CI가 건드리지 않는다. 서버가 원본인데 git에 또 사본을 두면 둘이
      어긋날 위험만 생긴다(오늘 인시던트도 근본적으로 이런 종류의 불일치였다) — 그래서
      파일 자체를 git에 커밋하는 대신, 필요한 전체 내용은 이 문서의 "적용 절차"에
      코드블록으로 남겨서 재구성 가능하게 해둔다. Flyway 전환(#571/#587) 이후 스키마/
      시드 데이터가 전부 Spring Boot 기동 시 자동 처리되므로 옛 `SCHEMA_FILE`/
      `DB_SCHEMA_SYNC_MODE`/`DB_SNAPSHOT_DIR`/`INITIAL_DATA_DIR` env var는 전부
      제거해야 한다(오늘 이 불일치로 실제 장애가 났었다 — "2026-08-18 인시던트" 참고).
      `-Xmx`도 1000m로.
- [x] nginx 설정도 같은 이유로 **git에 안 둔다** — 호스트 `~/nginx/`가 원본, 계속
      수동 관리(팀 컨벤션). 이 작업을 위해 바뀌는 부분은 딱 하나, upstream 대상을
      `upstream-active.inc`라는 별도 파일로 분리해서 include하는 것뿐이다. 이후
      배포부터는 컷오버 스크립트가 `upstream-active.inc` 파일 하나만 갱신+reload한다.
      아래 nginx 설정 문법은 실제 `nginx:latest` 이미지로 `nginx -t` 검증 완료
      (와일드카드 include로 인한 이중 로딩 버그를 잡아서 고쳤다 — 확장자를 `.conf`가
      아닌 `.inc`로 둬야 `nginx.conf`의 `include conf.d/*.conf`에 다시 안 걸린다)
- [x] `deploy/blue-green-deploy.sh` — **이것만 git에 둔다**, CI가 매 배포마다 최신으로
      호스트에 동기화(scp)한다. 순수 자동화 로직(비밀값·토폴로지 정의가 없음)이라
      매번 최신으로 갱신되는 게 맞고, 실제로 CI가 프로그래밍적으로 가져다 쓸 원본이
      필요하니 git에 있어야 한다. readiness 폴링, 자동 롤백 포함. nginx 컨테이너
      자체는 건드리지 않고 `docker exec nginx nginx -s reload`로 설정만 다시 읽힘.
      색 판별/전환 로직은 로컬에서 단위 검증(blue↔green 양방향, 잘못된 파일 시 실패 경로)
- [x] `.github/workflows/backend-deploy.yml`의 `deploy-prod`가 `blue-green-deploy.sh`를
      호출하도록 교체 — GitHub Actions가 SSH로 직접 실행하는 기존 구조 유지. 동기화
      대상은 이 스크립트 하나뿐, `docker-compose.prod.yml`/nginx는 안 건드림.

### 적용 절차 (최초 1회, 사람이 직접)

1. 호스트에 `~/deploy/docker-compose.prod.yml`을 아래 내용으로 만든다(`mkdir -p ~/deploy`).
   `blue-green-deploy.sh`가 기본으로 자기와 같은 디렉터리(`~/deploy/`)의
   `docker-compose.prod.yml`을 찾는다. 기존 `~/docker-compose.yml`의 `backend` 서비스
   설정(env var 값)을 그대로 옮기되, 아래처럼 blue/green 두 서비스로 나누고 옛
   스키마 관련 env var는 뺀다:

   ```yaml
   x-backend-common: &backend-common
     ulimits:
       nofile: { soft: 8192, hard: 8192 }
     image: ghcr.io/softeerbootcamp-8th/web-team2-2gether/backend:latest
     restart: unless-stopped
     environment:
       DB_HOST: ${DB_HOST}
       DB_PORT: ${DB_PORT:-3306}
       DB_NAME: ${DB_NAME:-dbidding}
       DB_USERNAME: ${DB_USERNAME}
       DB_PASSWORD: ${DB_PASSWORD}
       AWS_REGION: ${AWS_REGION}
       AUCTION_IMAGE_BUCKET: ${AUCTION_IMAGE_BUCKET}
       SPRING_DATASOURCE_URL: "jdbc:mysql://${DB_HOST}:${DB_PORT:-3306}/${DB_NAME:-dbidding}?sslMode=${DB_USE_SSL}&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
       JAVA_TOOL_OPTIONS: "-Duser.timezone=UTC -Xmx1000m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump-%p.hprof -Xlog:gc*,gc+heap=debug,safepoint:file=/app/logs/gc-%p.log:time,uptime,level,tags:filecount=3,filesize=20M -Djdk.tracePinnedThreads=full"
       PASSWORD_HASH_ITERATIONS: "100"
       SPRING_PROFILES_ACTIVE: "redis,sse-virtual-threads"
       WALLET_SSE_CORE_POOL_SIZE: 4
       WALLET_SSE_MAX_POOL_SIZE: 8
       WALLET_SSE_QUEUE_CAPACITY: 2000
       LOGGING_LEVEL_ROOT: INFO
       LOGGING_LEVEL_ORG_SPRINGFRAMEWORK: WARN
       LOGGING_LEVEL_ORG_HIBERNATE: WARN
       LOGGING_LEVEL_COM_DBIDDING: INFO
       TZ: Asia/Seoul
       SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: "30"
       SERVER_TOMCAT_THREADS_MAX: "50"
       SERVER_TOMCAT_THREADS_MIN_SPARE: "30"
       SERVER_TOMCAT_MAX_CONNECTIONS: "4000"
       SERVER_TOMCAT_ACCEPT_COUNT: "300"
       REDIS_HOST: ${REDIS_HOST}
       REDIS_PORT: ${REDIS_PORT}
       REDIS_USERNAME: ${REDIS_USERNAME}
       REDIS_PASSWORD: ${REDIS_PASSWORD}
       REDIS_SSL_ENABLED: ${REDIS_SSL_ENABLED}
       REDIS_CONNECT_TIMEOUT: 5s
       REDIS_TIMEOUT: 5s
       SLACK_LOG_WEBHOOK_URL: ${SLACK_LOG_WEBHOOK_URL}
     volumes:
       - /home/ubuntu/logs:/app/logs
     networks:
       - app-network

   services:
     backend-blue:
       <<: *backend-common
       container_name: backend-blue
       ports: ["8080:8080", "9091:9091"]
     backend-green:
       <<: *backend-common
       container_name: backend-green
       ports: ["8081:8080", "9092:9091"]

   networks:
     app-network:
       # 기존 nginx가 떠 있는 네트워크를 그대로 쓴다(external: true) - 이 값 없이
       # 그냥 `driver: bridge`만 쓰면 compose가 이 파일이 있는 디렉터리 이름
       # (`~/deploy/` → 프로젝트명 "deploy")을 접두어로 붙여 `deploy_app-network`라는
       # *새* 네트워크를 만들어버린다. nginx는 원래 `~/docker-compose.yml`(프로젝트명
       # "ubuntu")로 떴으므로 그 네트워크는 `ubuntu_app-network`다 - 이름이 다르면
       # backend-blue/green이 떠도 nginx가 DNS로 못 찾아서 컷오버가 502로 끝난다.
       # 실제로 한 번 이 문제로 배포가 막혔다(아래 인시던트 참고). 호스트에서
       # `docker network ls`로 nginx가 실제로 붙어있는 네트워크 이름을 먼저 확인하고
       # 그 이름을 여기 `name:`에 넣을 것 - "ubuntu_app-network"는 이 환경 기준값이라
       # 호스트/프로젝트명이 다르면 값이 다를 수 있다.
       name: ubuntu_app-network
       external: true
   ```

   (`SLACK_LOG_WEBHOOK_URL`은 기존 host `~/docker-compose.yml`에 평문으로 박혀 있던
   값인데, git에 이 문서로도 남기지 않기 위해 여기선 env var 참조로 바꿔뒀다 —
   `~/.env`에 실제 값을 추가해두거나, 호스트 파일엔 기존처럼 평문으로 둬도 된다.
   `JWT_SECRET`/`JWT_SECURE_COOKIE`는 애초에 안 넣었다 - 현재 코드베이스 어디서도
   JWT_SECRET을 참조하지 않는다(#469에서 세션 인증으로 완전히 전환됨, `#587` 이후
   `start-server.sh`도 이 값을 요구하지 않는다 - `StartServerScriptTest`의
   `JWT_SECRET_없이도_Redis_연결_검증까지_진행한다` 테스트로 확인됨). 넣어도 아무도
   안 읽으니 무해하지만, 이미 안 쓰는 값이라 넣을 이유가 없다.)

2. 호스트 `~/nginx/conf.d/default.conf`의 `upstream backend_server { ... }` 블록을
   아래처럼 바꾼다(원래는 `server backend:8080;` 한 줄이었던 것):

   ```nginx
   upstream backend_server {
       include /etc/nginx/conf.d/upstream-active.inc;
   }
   ```

3. `~/nginx/conf.d/upstream-active.inc` 파일을 새로 만든다:

   ```nginx
   server backend-blue:8080;
   ```

   (확장자를 `.conf`가 아닌 `.inc`로 — `nginx.conf`의 `include conf.d/*.conf;`가 이
   파일을 다시 읽어버려서 문법 에러 나는 걸 피하려는 것)

   **`~/nginx/conf.d/` 디렉터리 소유권을 `ubuntu`로 바꿔둔다** — 원래 이 디렉터리가
   root 소유라, 컷오버 스크립트(CI가 SSH로 `ubuntu` 사용자 권한으로 실행)가
   `upstream-active.inc`를 갱신하려고 할 때 `mv: ... Permission denied`로 실패한다
   (실제로 한 번 이 문제로 배포가 막혔다 — 아래 인시던트 참고). `sudo cp`로 파일만
   만들어두면 파일은 root 소유로 남아 스크립트가 여전히 못 덮어쓰므로, 디렉터리
   전체를 넘겨야 한다:

   ```bash
   sudo chown -R ubuntu:ubuntu ~/nginx/conf.d
   ```

   컨테이너 안 nginx 프로세스는 이 디렉터리를 읽기 전용(`:ro`)으로만 마운트하므로
   호스트 쪽 소유권을 바꿔도 컨테이너의 읽기 권한에는 영향 없다.

4. `docker exec nginx nginx -t && docker exec nginx nginx -s reload`로 반영 확인
5. 기존 단일 `backend` 컨테이너를 위 `docker-compose.prod.yml` 기준 `backend-blue`로
   맞춰 재기동(컨테이너 이름 변경 필요 - 재생성 다운타임 발생)

### 2026-08-18 인시던트

이 설계를 처음 실제 적용하는 과정에서 프로덕션이 몇 분간 다운됐다. 원인: 그 시점에
`docker-compose.prod.yml`의 `SCHEMA_FILE`을 Flyway 새 경로로 이미 바꿔놨는데, 실제
적용 대상은 그 경로가 없는 옛(Flyway 반영 전) 이미지였다 — `backend-blue` 컨테이너가
"스키마 파일을 읽을 수 없습니다" 크래시 루프에 빠졌고, nginx는 이미 내려간 `backend`를
여전히 가리키고 있어서 504가 났다. 백업(`docker-compose.yml`/`nginx/` 사본)을 미리
떠둬서 원복은 됐지만, 그 과정에서도 컨테이너 이름이 꼬이는 등 완전히 깔끔하게
복구되지는 않았다.

교훈:
- **실기 적용은 반드시 격리된 환경에서 전체 흐름(컨테이너 기동→readiness→nginx 전환→
  이전 컨테이너 정리)을 먼저 드라이런하고 나서** 프로덕션에 적용한다 — 로컬 문법/스키마
  검증만으로는 이런 버전 불일치를 못 잡는다.
- 이 사고를 계기로 `start-server.sh`가 이미 리팩터링돼(#587) 스키마/시드 로직 자체가
  Flyway로 완전히 이관됐다는 걸 알게 됐다 — `SCHEMA_FILE` 같은 옛 env var 자체를
  compose에서 제거해 이 실패 유형을 구조적으로 없앴다.
- 되돌리기 쉬운 상태(백업, 이전 이미지 digest 고정, 컷오버 스크립트의 readiness 타임아웃
  자동 롤백)를 미리 갖춰둔 덕에 완전한 장기 장애로는 번지지 않았다.

### 2026-08-18 인시던트 #2 — 첫 실제 CI 배포에서 컷오버 실패

위 인시던트를 수습하고 SCHEMA_FILE 문제를 구조적으로 없앤 뒤, `main` 머지로 처음
CI가 실제로 `blue-green-deploy.sh`를 돌렸을 때 또 다른 두 문제로 컷오버가 막혔다.
이번엔 웜업·readiness까지는 전부 성공했고 nginx 전환 단계에서만 실패해서, 서비스가
완전히 죽지는 않고(구 색이 계속 트래픽을 받는 중이었음) "새 배포가 안 붙는" 상태로
남았다 — 그러다 사람이 직접 `~/nginx/conf.d/upstream-active.inc` 갱신을 시도하는
과정에서 구 색 컨테이너가 사라지면서 짧게 502가 났다(직접 개입 중 발생, 자동 롤백
경로는 아님).

1. **`mv: ... Permission denied`** — `~/nginx/conf.d/`가 root 소유라, CI가 SSH로
   붙는 `ubuntu` 사용자 권한으로는 `upstream-active.inc`를 덮어쓸 수 없었다. 최초
   셋업 때 `sudo cp`로 파일만 만들어두고 디렉터리 소유권은 안 바꿨던 게 원인 — 위
   "적용 절차" 3번에 `chown -R ubuntu:ubuntu` 단계를 추가해 해결.
2. **`backend-green`이 nginx와 다른 도커 네트워크에 뜸** — `docker-compose.prod.yml`의
   `networks.app-network`를 `driver: bridge`(새 네트워크 생성)로 뒀는데, 이 compose
   파일이 `~/deploy/`에서 실행되니 프로젝트명이 "deploy"로 잡혀 `deploy_app-network`가
   새로 만들어졌다. 정작 nginx는 `~/docker-compose.yml`(프로젝트명 "ubuntu")로 뜬
   `ubuntu_app-network`에 있어서, backend-green이 떠도 nginx가 DNS로 찾을 방법이
   없었다(`docker exec nginx getent hosts backend-green` 실패로 확인). 위 "적용 절차"
   1번의 compose 예시에 `external: true`로 기존 네트워크를 참조하도록 수정.

교훈:
- 다중 서비스를 서로 다른 compose 프로젝트(=서로 다른 디렉터리)로 나눌 때는 **네트워크를
  공유해야 하면 반드시 `external: true`로 명시**해야 한다 — 같은 네트워크 "이름"을 써도
  프로젝트명이 다르면 실제로는 다른 네트워크가 된다. `docker network ls`로 실제
  네트워크 이름을 직접 확인하고 넣을 것, 환경마다 다를 수 있다.
- 호스트 파일 권한을 `sudo cp`/`sudo tee`로 임시 우회하면 "지금 당장은 되는데 다음에
  다른 사용자·자동화가 건드릴 때 막히는" 함정이 된다 — 애초에 그 리소스를 누가
  지속적으로 관리할지(이번 경우 CI의 `ubuntu` 사용자) 소유권 자체를 맞춰두는 게 맞다.
- 이번에도 readiness 게이트와 컷오버 실패 시 자동 롤백 덕분에, nginx 전환 전까지는
  구 색이 계속 트래픽을 받고 있어서 "배포 실패"가 "서비스 다운"으로 바로 이어지지
  않았다 — 다만 그 뒤 사람이 수동으로 개입하는 과정에서 별도 실수로 짧은 502가
  있었으니, 수동 복구 절차 자체도 이 문서에 정리해두는 게 다음번엔 낫다.

## 남은 결정 사항

- [ ] Prometheus 스크레이프 타겟 처리 방식(§ 컷오버 스크립트 7번 항목) — alternating
      공백 감수 vs 양쪽 상시 스크레이프
- [ ] `-Xmx1000m` 재검증 부하테스트 라운드 일정
- [ ] **실제 배포 파이프라인을 바꾸는 변경이라, main 머지 전 EC2에서 최소 한 번
      드라이런 권장** — 로컬에서는 nginx 문법·compose 스키마·스크립트 로직만
      검증했고, 실제 컨테이너 기동·포트 바인딩·readiness 왕복까지는 아직 안 해봄

## 관련

- `#586` — Redis warm-up 전체 확장 + readiness probe 노출 (완료)
- `docs/hyeonmoon/observability/10-round8-ram-upgrade-verification.md` — 현재 RAM/힙
  설정의 근거
