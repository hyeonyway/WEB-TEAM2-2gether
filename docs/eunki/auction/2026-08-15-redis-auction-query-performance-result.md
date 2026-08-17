# Redis 경매 조회 성능 개선 결과

## 검증 대상

- before: `origin/dev` `ab126014`
- after: `fix/503-redis-auction-query-performance` `bf3e26e4`
- MySQL: 로컬 Test DB, Hikari maximum pool size 30
- Redis: 로컬 Redis 7.4, Lettuce pool max-active 30, command timeout 5초
- 애플리케이션: Redis + SSE virtual threads 프로필, Tomcat port 18080, management port 19091
- 사용자: 부하 계정 1,000명, session cookie + CSRF token 인증
- 대상 경매: `3000039`, OPEN, buy-now 없음
- 트래픽: 50/100/150/200/300/400 operations per second, 단계별 10초
- 비율: 목록/입찰 내역/입찰 참여 = 4:4:2
- 입찰 참여 1회는 bid-context 조회 후 해당 `minimum_bid`로 POST한다.

워밍업 40 ops/s 5초를 마친 직후 Redis `CONFIG RESETSTAT`을 실행해 로그인과 워밍업 traffic은 Redis hit ratio에서 제외했다. before process를 종료한 뒤 같은 port와 환경 변수로 after process를 시작했다.

## 자동화 검증

- 전체 backend 테스트: 682건, failures 0, errors 0, 2분 8초
- 실제 Redis 목록 명령 회귀 테스트(`size=20`, 로그인, 참여 경매 없음)
  - `HGETALL`: 20회
  - `SMISMEMBER`: 1회
  - `XREVRANGE`: 0회
  - `keyspace_misses`: 0
- Redis command failure는 DB fallback이나 cold seed로 변환하지 않고 원래 예외를 전파한다.

## 부하 테스트 결과

### Redis cache

| 지표 | before | after | 변화 |
| --- | ---: | ---: | ---: |
| keyspace hits | 1,168,114 | 1,125,691 | -3.6% |
| keyspace misses | 93,742 | 2,531 | -97.3% |
| hit ratio | 92.57% | 99.78% | +7.21%p |

로그인 목록에서 모든 카드의 존재하지 않는 bidder hash를 읽던 동작을 제거한 결과다. after에 남은 miss는 session/입찰 등 전체 혼합 workload의 miss를 포함한다.

### API p95

| 목표 QPS | API | before | after | 변화 |
| ---: | --- | ---: | ---: | ---: |
| 50 | 목록 | 37.1ms | 16.6ms | -55.2% |
| 50 | 입찰 내역 | 3.3ms | 6.5ms | +98.0% |
| 100 | 목록 | 50.7ms | 12.4ms | -75.5% |
| 100 | 입찰 내역 | 5.4ms | 3.8ms | -28.9% |
| 150 | 목록 | 116.8ms | 19.5ms | -83.3% |
| 150 | 입찰 내역 | 10.6ms | 4.3ms | -59.8% |
| 200 | 목록 | 975.5ms | 26.8ms | -97.3% |
| 200 | 입찰 내역 | 88.0ms | 8.0ms | -90.9% |
| 300 | 목록 | 6,336.6ms | 165.1ms | -97.4% |
| 300 | 입찰 내역 | 4,514.3ms | 117.4ms | -97.4% |
| 400 | 목록 | 11,545.3ms | 3,074.1ms | -73.4% |
| 400 | 입찰 내역 | 9,618.0ms | 2,965.0ms | -69.2% |

50 QPS 입찰 내역 p95는 절대값 3.3ms에서 6.5ms로 증가했지만 이후 단계에서는 감소했다. 한 번의 실행 순서로 얻은 작은 절대값 차이이므로 저부하 회귀로 단정하지 않는다.

### 처리량과 resource saturation

| 목표 QPS | 지표 | before | after |
| ---: | --- | ---: | ---: |
| 200 | achieved ops/s | 191.2 | 200.0 |
| 200 | Tomcat busy max | 98 | 4 |
| 300 | achieved ops/s | 189.2 | 298.2 |
| 300 | Tomcat busy max | 200 | 60 |
| 400 | achieved ops/s | 187.2 | 306.3 |
| 400 | Tomcat busy max | 200 | 200 |
| 400 | Hikari active max | 4 | 13 |
| 400 | Hikari pending max | 0 | 0 |

after는 더 많은 요청과 유효 입찰을 처리해 400 QPS에서 Hikari active가 더 높았지만 pending은 두 실행 모두 0이었다. 300 QPS까지 Tomcat saturation이 해소됐고, 400 QPS에서는 여전히 busy 200에 도달하므로 #503 이후의 별도 병목이 남아 있다.

## 상태 코드와 오류

- 목록과 입찰 내역: 모든 단계에서 HTTP 200
- 입찰: 낮은 단계에서는 201, 높은 단계에서는 동일 hot auction의 stale `minimum_bid` 경쟁 때문에 HTTP 400이 증가
- 400 QPS에서 최고 입찰자 재입찰 충돌 HTTP 409 1건
- HTTP 5xx: 0
- Redis command timeout/connection failure: 0
- Spring Session `creationTime key must not be null`: 0
- Hikari pending/timeout: 0

입찰 400은 backend 장애가 아니라 bid-context 조회 이후 다른 사용자가 먼저 가격을 올려 Redis 입찰 조건이 달라진 business rejection이다.

## API 계약과 데이터 정합성 확인

- 목록, 입찰 내역, bid-context를 Redis 프로필과 DB 프로필로 각각 호출했을 때 top-level 필드와 content item 필드는 동일했다.
- 전체 backend 테스트 682건으로 정렬, 페이지 응답, 입찰 상태 계산을 포함한 기존 계약의 회귀가 없음을 확인했다.
- 같은 부하 DB를 사용한 응답 값은 일치하지 않았다. 대상 경매 `3000039`는 Redis 현재가가 `2,494,841`, DB 현재가가 `682,841`이었다.

값 차이는 #503 조회 변경이 아니라 부하 테스트 전에 발생한 기존 projection 중단 상태에서 비롯됐다. Redis Stream은 모두 소비되어 `lag=0`, `pending=0`이었지만, MySQL inbox에는 `ERROR` 1건 뒤로 `PENDING` 5,862건이 쌓여 있었다. 최초 오류는 `이전 최고 입찰자 정보가 DB 상태와 일치하지 않습니다.`였으며, 현재 consumer는 `ERROR`가 하나라도 있으면 `hasProjectionError()`에서 이후 DB projection 전체를 멈춘다. 따라서 Stream 소비 완료는 DB 도메인 테이블 반영 완료를 의미하지 않는다.

#503은 읽기 명령 수와 왕복 횟수만 줄이고 write/projection 경로를 변경하지 않는다. 정확한 Redis/DB 값 동등성 A/B는 별도 projection 장애를 복구하고 동일 snapshot에서 다시 수행해야 한다.

## 제약과 후속 관찰

- 현 Actuator는 Hikari와 Lettuce command timer는 노출하지만 Commons Pool의 Redis active/borrowed connection gauge를 노출하지 않는다. 따라서 Redis pool active max는 이번 A/B에서 직접 비교하지 못했다.
- before/after는 동일 DB/Redis를 순차 사용했다. 두 process 모두 별도 기동과 동일 워밍업을 거쳤지만, 완전히 독립된 data snapshot을 사용한 benchmark는 아니다.
- MySQL inbox의 기존 projection 오류 때문에 Redis 프로필과 DB 프로필의 동일 값 비교는 완료하지 못했다. 응답 schema와 자동화된 API 계약 회귀만 확인했다.
- 400 QPS에서 Tomcat busy 200과 수초 지연이 남는다. #503은 Redis 조회 증폭을 제거했지만 단일 hot auction의 경쟁, 경고 로그 출력량, session/CSRF Redis traffic 등 다음 병목은 별도로 분석해야 한다.
