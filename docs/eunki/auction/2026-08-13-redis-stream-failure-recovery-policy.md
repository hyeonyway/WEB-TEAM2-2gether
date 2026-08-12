# Redis Stream 실패·복구 운영 정책

## 목적

Redis 승인 경로는 경매·지갑·주문 상태를 Lua로 먼저 변경하고, 같은 Redis 연산에서 `event:timeline`에
이벤트를 기록한다. 사용자는 이 시점에 성공 응답을 받는다. MySQL 반영은 이후 단일 consumer가
비동기로 수행한다.

따라서 MySQL projection이 실패해도 이미 승인된 Redis 상태를 되돌리지 않는다. 이 문서는 장애가
발생했을 때 **어떤 이벤트가 재시도되고, 어떤 이벤트가 운영자 조치를 기다리며, 무엇을 근거로
복구를 완료했다고 판단하는지**를 정의한다.

## 현재 구성

```text
Client
  │
  ▼
Redis Lua: 상태 전이 + XADD
  │                    │
  │                    └── event:timeline (AOF 보존)
  ▼
HTTP 성공 응답                    │
                                 ▼
                  auction-timeline-persistence group
                                 │
                  leader lock 보유 consumer 1개
                                 │
                  inbox PENDING → MySQL projection
                                 │
                         PROCESSED 또는 ERROR
```

| 구성 요소 | 현재 값 | 역할 |
| --- | --- | --- |
| Stream | `event:timeline` | 모든 Redis 승인 이벤트의 순서 있는 원본 |
| Consumer group | `auction-timeline-persistence` | 실시간 MySQL projection cursor와 PEL 소유자 |
| Consumer | `auction-timeline-single` | `COUNT 1`로 순차 처리하는 단일 worker |
| Leader lock | `event:timeline:consumer-leader-lock` | 다중 앱 인스턴스에서도 worker를 하나로 제한 |
| Inbox | `auction_bid_event_inbox` | 수신 원본, projection 상태, 오류 사유 보관 |
| AOF | `appendfsync always` 전제 | Redis state와 Stream의 영속 근거 |

Stream 레코드는 ACK 뒤에도 삭제하지 않는다. `XDEL`/자동 `XTRIM`을 수행하면 AOF 기반 replay의
근거가 사라지므로, retention/checkpoint 정책이 확정되기 전에는 사용하지 않는다.

## 상태와 용어

### Redis PEL

PEL(Pending Entries List)은 consumer group이 전달했지만 아직 ACK하지 않은 Stream entry 목록이다.

- PEL에 남음: 현재 consumer가 아직 처리를 끝내지 못했으므로 같은 group이 다시 claim할 수 있다.
- ACK됨: 실시간 group 관점에서 처리가 끝났다는 의미다. Stream 원본이 지워졌다는 뜻은 아니다.
- `claimIdle`보다 오래 유휴인 PEL은 새 leader가 `XCLAIM`으로 회수한다.

### MySQL inbox

| 상태 | 의미 | Redis state seeding 영향 |
| --- | --- | --- |
| `PENDING` | 이벤트는 수신·보존됐으나 projection 전 또는 미완료 | MySQL 기반 Redis seed 금지 |
| `PROCESSED` | projection transaction이 commit됨 | 최신 Stream도 PROCESSED이고 다른 미완료 행이 없을 때만 seed 가능 |
| `ERROR` | 계약·도메인·DB projection 오류가 기록됨 | MySQL 기반 Redis seed 금지, 복구 조치 필요 |

`stream_id` unique, 지갑/원장 `event_id` unique, `auctionVersion`, `walletVersion`은 replay가 중복
반영돼도 과거 상태가 최신 상태를 덮지 않도록 하는 안전장치다. 이들은 replay를 무조건 안전하게
만드는 만능 장치가 아니며, 이벤트 계약과 순서가 정상이라는 전제가 필요하다.

## 정상 처리 순서

1. Lua가 Redis 상태 변경과 `XADD event:timeline`을 하나의 원자 실행으로 처리한다.
2. consumer가 이벤트를 읽는다.
3. consumer는 별도 트랜잭션으로 inbox 행을 `PENDING`으로 기록한다.
4. consumer는 도메인 projection transaction을 실행한다.
5. 성공하면 inbox를 `PROCESSED`로 바꾸고 ACK한다.
6. ACK 뒤에도 Stream entry는 유지된다.

이 순서에서 API 응답과 MySQL 반영 사이의 짧은 차이는 정상적인 eventual consistency다.

## 장애 유형별 처리 기준

### 1. Redis Lua 또는 XADD 실패

Lua 실행은 Redis 상태 변경과 `XADD`를 함께 수행한다. 실행이 실패하면 둘 다 적용되지 않는다.

- HTTP 요청은 실패 응답을 받는다.
- Stream 이벤트가 없으므로 consumer/replay 대상도 없다.
- Redis AOF 쓰기 실패 같은 Redis 자체 장애는 Redis 운영 장애로 분류한다.

### 2. consumer가 Stream을 읽기 전에 중단됨

이벤트는 Stream에 남고 group cursor는 아직 진행되지 않는다.

- 새 leader가 같은 group으로 `XREADGROUP`을 계속한다.
- MySQL 반영 전이므로 inbox 행이 없을 수 있다.
- API 승인 상태는 Redis에 그대로 남는다.

### 3. inbox 기록 자체가 실패함

예: MySQL 연결 단절, connection pool 고갈, DB 전체 장애.

- 이벤트를 ACK하지 않는다.
- entry는 PEL에 남거나 다시 전달된다.
- 같은 consumer는 즉시, 다른 leader는 `claimIdle` 뒤에 회수해 다시 기록을 시도한다.
- 이 경우에는 DB가 회복될 때까지 자동 재시도가 적절하다. 아직 MySQL이 이벤트를 수신했다는
  사실 자체가 없기 때문이다.

### 4. projection transaction이 실패함

예: lock timeout, deadlock, 외래 키/제약 오류, 이벤트 계약 오류, 코드 버그.

현재 구현은 inbox에 `ERROR`를 기록하고 ACK하지 않아 PEL 재전달 대상으로 남긴다.

이 동작은 **일시 장애와 영구 오류를 구분하지 않는 현재 임시 정책**이다. 따라서 다음 문제가 있다.

- lock timeout처럼 일시적인 오류에는 자동 재시도가 도움이 된다.
- 스키마 불일치·잘못된 payload·도메인 규칙 위반처럼 영구적인 오류는 같은 head entry만 반복하며
  단일 consumer를 막고 backlog를 키운다.
- 뒤 이벤트도 global order 때문에 MySQL projection이 지연될 수 있다.

이 정책은 아래 “결정이 필요한 항목”에서 팀 합의 후 코드와 문서를 함께 바꾼다.

### 5. consumer가 ACK 직전에 중단됨

MySQL commit 뒤 ACK 전 중단되면 같은 이벤트가 PEL에서 다시 전달될 수 있다.

- inbox `stream_id` unique와 원장 `event_id` unique가 중복 insert를 방지한다.
- projection은 이미 적용된 버전이면 멱등적으로 종료한다.
- 재전달된 이벤트는 성공 후 ACK한다.

### 6. leader lock 보유 인스턴스가 비정상 종료됨

정상 종료는 owner token을 확인해 lock을 바로 해제한다. 강제 종료·프로세스 kill이면 해제 코드가
실행되지 않는다.

- 다른 인스턴스는 lock TTL이 만료될 때까지 consumer를 시작하지 않는다.
- 기본 최대 lease는 5분이다.
- lock 소유 인스턴스가 정상 동작 중이면 heartbeat가 lease를 연장한다.
- TTL 만료 뒤 새 인스턴스가 lock을 획득하고 PEL/새 entry를 이어서 처리한다.

운영자가 프로세스를 강제 종료한 경우에는 기다리거나, 장애 조치가 확인된 뒤 해당 lock key만
수동 삭제할 수 있다. 소유자가 실제로 살아 있는 상태에서 lock을 지우면 consumer가 둘이 될 수
있으므로 무조건 삭제해서는 안 된다.

## 운영자가 확인할 순서

### MySQL projection이 늦을 때

1. Stream 길이와 group lag를 확인한다.
2. PEL 수와 가장 오래된 pending entry의 idle time을 확인한다.
3. inbox의 `PENDING`, `ERROR` 행 및 `failure_message`를 확인한다.
4. leader lock의 TTL과 consumer 인스턴스 로그를 확인한다.
5. MySQL 연결·lock timeout·pool 포화 여부를 확인한다.

예시 명령은 단일 Redis에서 다음과 같다.

```bash
redis-cli XLEN event:timeline
redis-cli XINFO GROUPS event:timeline
redis-cli XPENDING event:timeline auction-timeline-persistence
redis-cli PTTL event:timeline:consumer-leader-lock
```

MySQL에서는 inbox 상태와 오류 원인을 확인한다.

```sql
SELECT projection_status, COUNT(*)
FROM auction_bid_event_inbox
GROUP BY projection_status;

SELECT stream_id, event_type, auction_id, projection_status, failure_message, occurred_at
FROM auction_bid_event_inbox
WHERE projection_status IN ('PENDING', 'ERROR')
ORDER BY id;
```

### Redis state miss가 발생했을 때

경매·지갑·주문 state seeder는 최신 Stream이 MySQL에서 `PROCESSED`이고 inbox에 `PENDING`/`ERROR`가
없을 때만 MySQL projection을 Redis에 조건부 seed한다.

따라서 lag 또는 ERROR가 있으면 `stateRecoveryRequired`가 정상 동작이다. 이 상황에서 MySQL 값을
강제로 Redis에 덮어쓰면, Redis에서 이미 승인됐지만 아직 MySQL에 반영되지 않은 금액·최고입찰자·주문
상태가 과거 값으로 돌아갈 수 있다.

## Replay 원칙

ACK는 Stream 삭제가 아니므로 과거 entry는 AOF에 남는다. replay의 목적은 Redis 상태를 다시 승인하는
것이 아니라 **이미 승인된 이벤트를 MySQL projection에 다시 적용하는 것**이다.

replay를 시작하기 전 반드시 다음을 만족해야 한다.

1. `ERROR`의 원인이 코드·스키마·참조 데이터 관점에서 해결됐다.
2. 실시간 consumer를 중지하거나 leader lock을 확보해 replay와 실시간 projection이 동시에 같은
   MySQL 상태를 바꾸지 않게 했다.
3. 시작/종료 Stream ID와 대상 범위를 기록했다.
4. MySQL 백업 또는 최소한 inbox·orders·wallets·auctions의 현재 상태를 보존했다.

replay는 별도 consumer group 또는 운영 도구로 지정된 Stream ID 범위를 읽어 기존 projection service를
호출해야 한다. 기존 실시간 group의 cursor를 되감아서는 안 된다. replay 뒤에는 아래를 검증한다.

- 대상 inbox가 `PROCESSED`인지
- `PENDING`/`ERROR`가 사라졌는지
- `auctions.last_bid_event_version`과 Redis auction sequence가 일치하는지
- `wallets.projection_version`과 Redis `walletVersion`이 일치하는지
- `orders` 상태와 Redis `order:state:*`의 `projectionStatus`가 일치하는지

현재 replay 도구는 구현돼 있지 않다. 운영자가 inbox/DB를 수동 수정한 뒤 재소비하는 방식도 현재는
지원하지 않으므로, 실제 장애에서 임의의 Stream group 생성·cursor 변경을 수행하기 전에 별도
운영 절차와 승인자가 필요하다.

## 관측·backpressure 원칙

Redis는 MySQL 처리량을 늘리지 않는다. 승인 유입량이 단일 consumer의 지속 처리량보다 크면
`event:timeline`은 유실 없는 버퍼로 쌓인다. backlog가 계속 증가하는 동안 API가 빠르게 성공하는 것은
처리량이 늘어난 것이 아니라 MySQL 부하를 뒤로 미룬 것이다.

운영 지표는 최소한 다음을 포함해야 한다.

| 지표 | 의미 | 조치 예시 |
| --- | --- | --- |
| Stream length (`XLEN`) | 보존된 전체 이벤트 수 | 메모리/AOF 성장 확인 |
| group lag | 실시간 consumer가 읽지 못한 이벤트 수 | consumer/DB 처리량 확인 |
| PEL count·oldest idle | 전달됐지만 ACK되지 않은 이벤트 | DB 장애·영구 오류 분류 |
| inbox PENDING | 수신됐으나 projection 미완료 | consumer 정지 또는 순서 보류 확인 |
| inbox ERROR | 자동 또는 수동 복구가 필요한 오류 | 오류 원인 수정 후 replay 계획 |
| Redis used_memory/AOF 크기 | Stream 보존 비용 | 임계치/retention 재검토 |

현재 위 지표의 Micrometer 노출과 backlog 임계치 기반 승인 제한은 구현돼 있지 않다. 운영 정책 확정 뒤
별도 작업으로 추가한다. `XTRIM`은 backpressure의 대체 수단이 아니다. 미반영 이벤트를 trim하면
복구 근거를 잃는다.

## 결정이 필요한 항목

### projection 실패의 ACK 정책

다음 둘 중 하나를 팀이 선택해야 한다.

| 선택지 | 처리 | 장점 | 위험 |
| --- | --- | --- | --- |
| A. 모든 projection 실패를 PEL에서 자동 재시도 | inbox ERROR 기록 후 ACK하지 않음 | 일시 DB 장애가 자동 회복됨 | 영구 오류가 head-of-line blocking과 무한 재시도를 만듦 |
| B. 모든 projection 실패를 ERROR로 ACK | inbox ERROR 기록 후 ACK | Stream 진행이 멈추지 않고 원인을 DB inbox에서 관리 | 일시 DB 장애도 운영 replay가 필요 |
| C. 오류 분류형 | transient만 PEL 재시도, permanent는 ERROR 후 ACK | 자동 복구와 무한 반복 방지를 함께 얻음 | 예외 분류 기준·재시도 횟수·전환 로직이 필요 |

권장안은 C다.

- transient: Redis/MySQL 연결 오류, lock timeout, deadlock, connection pool 일시 고갈
- permanent: 이벤트 형식 오류, 필수 참조 데이터 없음, schema/컬럼 불일치, 도메인 규칙 위반
- transient도 무한 재시도하지 않고, 최대 횟수 또는 최대 경과 시간을 넘으면 ERROR 후 ACK로 전환한다.

단, 이 선택은 Redis 승인 이후 MySQL projection의 복구 책임과 운영 인력의 대응 시간에 영향을 주므로,
구현 전에 팀이 확정한다.

## 문서 정합성 주의

이 저장소에는 이전 키·정책을 설명하는 문서가 남아 있다.

- 현재 Stream 키는 `event:timeline`이며, `auction:timeline-events`는 이전 명칭이다.
- 현재 consumer는 ACK 뒤 `XDEL`하지 않는다.
- 현재 코드의 projection 실패는 inbox ERROR 기록 후 PEL에 남기는 A 정책이다.
- 일부 이전 문서는 projection 실패 뒤 ACK/XDEL하고 inbox ERROR를 수동 복구 기준으로 삼는다.

ACK 정책이 확정되는 다음 운영 이슈에서 위 문서들을 현재 코드와 최종 정책에 맞춰 함께 정리한다.
