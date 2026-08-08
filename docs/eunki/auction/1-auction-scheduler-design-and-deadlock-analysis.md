# 경매 마감 스케줄러 구조 및 데드락/성능 분석 보고서

> **문서 목적:** 현재 경매 마감 스케줄러(`AuctionClosingScheduler`, `AuctionDeadlineScheduler`)의 동작 방식과 마감 배치 처리 시 발생하는 데드락(Deadlock) 및 실시간 입찰 요청과의 락 경합(Lock Contention) 원인을 분석하고, 이를 해결하기 위한 대안 아키텍처들을 비교 및 평가한다.

---

## 1. 경매 마감 스케줄러 아키텍처 개요

현재 경매 마감 시스템은 마감 시각 누락을 방지하고 이중 안전망을 구축하기 위해 **이중 스케줄러 구조(Dual-Scheduler Structure)**로 설계되어 있다.

```text
               ┌────────────────────────────────────────────────────────┐
               │              AuctionDeadlineScheduler                  │
               │   (이벤트 기반 Exact-Time 스케줄러, 단일 타이머)       │
               └───────────────────────────┬────────────────────────────┘
                                           │
                                           ▼ (마감 시각 도달 시)
┌──────────────────────────────┐    ┌───────────────────────────────────┐
│   AuctionClosingScheduler    │    │   AuctionCommandService           │
│ (60초 주기 백업 스케줄러)    ├───>│   .closeDueAuctions(now, limit)       │
└──────────────────────────────┘    └─────────────────┬─────────────────┘
                                                      │
                                                      ▼
                                   ┌────────────────────────────────────┐
                                   │  AuctionRepository                 │
                                   │  .findCloseTargetsForUpdate(...)   │
                                   │  (SELECT ... FOR UPDATE LIMIT 100) │
                                   └────────────────────────────────────┘
```

### 1.1 `AuctionClosingScheduler` (주기적 백업 스케줄러)
- **위치**: [`AuctionClosingScheduler.java`](file:///Users/hian/java/WEB-TEAM2-2gether/backend/src/main/java/com/dbidding/auction/service/AuctionClosingScheduler.java)
- **동작 방식**: 기본값으로 `@Scheduled(fixedDelay = 60000)`을 통해 60초마다 실행되며, `auction.closing.scheduler.fixed-delay-ms` 설정으로 조정할 수 있다.
- **역할**: 타이머 누락이나 서버 재시작 과정에서 미처 마감되지 못하고 지난 경매들을 최종 구출(Fallback)하는 백업 역할을 수행한다.

### 1.2 `AuctionDeadlineScheduler` (이벤트 기반 정밀 스케줄러)
- **위치**: [`AuctionDeadlineScheduler.java`](file:///Users/hian/java/WEB-TEAM2-2gether/backend/src/main/java/com/dbidding/auction/service/AuctionDeadlineScheduler.java)
- **동작 방식**:
  1. DB에서 마감 예정 시각이 가장 빠른 경매 1건(`findNextCloseTarget`)을 찾는다.
  2. 해당 경매의 `closeTime` 시각에 맞추어 `TaskScheduler`에 단일 원샷 타이머를 예약한다.
  3. 마감 시각이 되면 예약된 작업이 실행되어 마감을 수행한 뒤, `finally` 블록에서 다음 임박 경매를 찾아 체이닝(Chaining) 재예약한다.
  4. 새로운 경매 생성이나 마감 시간 연장 이벤트(`AuctionCloseScheduleChangedEvent`)가 발생하면 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 기존 예약을 취소하고 타이머를 즉시 재설정한다.

---

## 2. 현재 마감 배치 처리 및 데드락/성능 문제점

두 스케줄러는 실제 마감 로직 실행 시 공통 메서드인 [`AuctionCommandService.closeDueAuctions(now, limit)`](file:///Users/hian/java/WEB-TEAM2-2gether/backend/src/main/java/com/dbidding/auction/service/AuctionCommandService.java#L270-L299)를 호출한다. 이 과정에서 다음과 같은 치명적인 동시성 문제가 발생한다.

### 2.1 마감 대상 조회 시점의 광범위한 비관적 락 (`SELECT ... FOR UPDATE`)
- `closeDueAuctions()` 메서드는 진입 시 단일 `@Transactional` 환경에서 `auctionRepository.findCloseTargetsForUpdate` 쿼리를 실행한다.
- 이 쿼리는 `a.status IN :statuses AND a.closeTime <= :now` 조건에 해당하는 경매 최대 100건(`CLOSE_BATCH_SIZE`)을 **조회하는 첫 시점부터 `SELECT ... FOR UPDATE`로 몽땅 잠그고 시작**한다.

### 2.2 다중 지갑 처리의 락 순서 불일치 가능성
마감 처리 시 낙찰자의 지갑을 정산하기 위해 `walletService.capture()`가 호출되며 `wallets` 테이블에 락이 적용된다. 입찰은 새 입찰자의 예치와 기존 최고 입찰자의 예치 해제를 함께 처리하므로, 서로 다른 두 지갑을 처리하는 요청들이 반대 순서로 지갑을 잡으면 순환 대기가 가능하다. 이 때문에 지갑 락은 사용자 ID 오름차순으로 통일해야 한다.

### 2.3 MySQL Gap Lock / Next-Key Lock 충돌
- `closeTime <= :now` 범위 검색에 비관적 락을 적용하면 레코드 락뿐만 아니라 **인덱스 간격(Gap)에 대한 Gap Lock / Next-Key Lock**이 함께 형성된다.
- 이로 인해 마감 시각 부근에 수행되는 경매 생성, 입찰 시간 연장 등 갭 공간 접근 요청들이 락 충돌을 일으키며 데드락 및 타임아웃을 유발한다.

### 2.4 실시간 사용자 입찰 요청 블로킹 및 성능 저하
- 마감 배치 트랜잭션이 100개 경매 락을 잡고 순차적으로 처리하는 동안, 해당 경매들에 마감 직전 입찰(`participate()`)하려는 일반 사용자 요청들은 `findByIdForUpdate` 구문에서 DB 락을 기다리느라 **스레드가 멈춘 채(Lock Wait) 블로킹**된다.
- **사이드 이펙트**:
  - **입찰 레이턴시 폭증**: 수 ms의 입찰 API 응답이 수 초 이상으로 지연됨.
  - **입찰 실패**: `innodb_lock_wait_timeout` 초과로 사용자에게 `500 Internal Server Error` 전달.
  - **커넥션 풀 고갈**: HikariCP 커넥션이 락 대기로 고갈되어 시스템 전체 API 마비(Cascading Failure).

---

## 3. 배치 처리 vs 개별/청크 트랜잭션 트레이드오프

| 구분 | 대형 단일 배치 트랜잭션 (기존 방식) | 개별/청크 단위 트랜잭션 (개선 방식) |
| :--- | :--- | :--- |
| **트랜잭션 범위** | 1개 트랜잭션에 100건 전체 묶음 | 1건(또는 소규모 청크)당 1개 트랜잭션 |
| **락 점유 시간** | **길음** (100건 정산이 끝날 때까지 락 유지) | **매우 짧음** (수 ms 점유 후 즉시 커밋) |
| **데드락 위험** | **높음** (광범위한 락 점유와 다중 지갑 정산이 겹침) | **낮음** (필요한 락만 잡고, 지갑 순서를 통일) |
| **장애 격리성** | 100건 중 1건 실패 시 **전체 롤백** | 1건 실패 시 **해당 건만 롤백**, 나머지는 성공 |
| **입찰 사용자 UX** | 마감 배치 완료까지 **입찰 요청 블로킹** | 락 지연 없이 **실시간 입찰 처리 가능** |

---

## 4. 추천 개선 아키텍처 (No-Lock Read + Granular Transaction)

데드락 및 입찰 블로킹을 해결하기 위한 인-메모리 최적화 구조는 다음과 같다.

```text
[ 스케줄러 / 마감 프로세서 ]
          │
          ▼ 1. No-Lock ID 조회
┌─────────────────────────────────────────────────────────────┐
│ SELECT id FROM auctions                                     │
│ WHERE status IN ('OPEN', 'ENDING') AND close_time <= NOW()  │
│ ORDER BY close_time ASC, id ASC LIMIT 100;                  │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼ 2. 마감 대상 ID 목록 전달 (예: [1, 2, 3, ... 100])
┌──────────────────────────┴──────────────────────────────────┐
│                   Parallel Task Executor                    │
└──────┬──────────────────────┬──────────────────────┬────────┘
       │                      │                      │
       ▼                      ▼                      ▼
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│  경매 #1 마감  │       │  경매 #2 마감  │       │  경매 #3 마감  │
│(단건 TX/Lock)│       │(단건 TX/Lock)│       │(단건 TX/Lock)│
└──────────────┘       └──────────────┘       └──────────────┘
```

---

## 5. 경매 마감 아키텍처 대안 비교 및 종합 평가 (Architecture Comparison)

스케줄러 기반 마감 처리 구조가 최선인지 검증하기 위해, 경매/커머스 도메인에서 활용되는 **4가지 주요 아키텍처 패턴**을 비교 평가한다.

### 5.1 대안 아키텍처 비교표

| 비교 항목 | **Option A: Spring In-Memory 스케줄러 + ThreadPool** (현재 적용 방식) | **Option B: DB `SKIP LOCKED` 기반 Worker Polling** | **Option C: Redis / RabbitMQ Delay Queue (이벤트 기반)** | **Option D: ShedLock / Quartz Clustered (분산 스케줄러)** |
| :--- | :--- | :--- | :--- | :--- |
| **핵심 매커니즘** | In-Memory `TaskScheduler` + 비동기 ThreadPool 1건 마감 | `SELECT id ... FOR UPDATE SKIP LOCKED` 쿼리로 병렬 Polling | 경매 마감 시각에 맞춰 Delay Queue에 메시지 발행 후 Consumer 처리 | DB/Redis 락을 통해 분산 서버 중 1대만 스케줄러 실행 |
| **추가 인프라** | **없음** (Spring Boot + MySQL) | **없음** (MySQL 8.0+) | **필요** (Redis Redisson / RabbitMQ / Kafka) | **없음** 또는 Redis |
| **Exact-Time 정밀도** | **높음** (단일 타이머 지정 시각 즉시 실행) | 보통 (Polling 주기에 의존) | **매우 높음** (지정 지연 시간 후 메시지 수신) | 보통 (Cron / Polling 주기 의존) |
| **Scale-Out (다중 서버) 대응** | ⚠️ **약함** (서버마다 타이머 동작하여 중복 마감 시도) | **강함** (`SKIP LOCKED`로 중복 작업 자동 스킵) | **매우 강함** (Consumer 경쟁 소비로 중복 없음) | **강함** (단 1대만 리더로 실행) |
| **데드락 방지** | **우수** (조회 락 제거 + 단건 `REQUIRES_NEW`) | **우수** (`SKIP LOCKED`로 락 대기 자체를 스킵) | **우수** (Consumer가 단건 마감 처리) | 보통 (마감 처리 방식에 따름) |
| **구현 및 운용 난이도** | **매우 낮음** | **낮음** | 보통 ~ 높음 (인프라 운용 및 메시지 누락 케이스 대응 필요) | 낮음 ~ 보통 |

---

### 5.2 상세 아키텍처 분석

#### Option A: Spring In-Memory 스케줄러 + ThreadPool (현재 구조)
- **특징**: 단일 애플리케이션 메모리에서 `TaskScheduler`가 임박 경매 마감 시각을 기억하고 즉시 실행함.
- **장점**: 외부 인프라가 전혀 필요 없고 구현이 직관적이며 마감 시각 정확도가 높음.
- **한계**: **다중 서버(Scale-Out) 확장 시 문제가 발생함.** 인스턴스가 2대 이상으로 늘어나면 각 서버의 메모리 타이머가 동일한 경매를 동시에 마감하려고 시도하게 됨. (단건 `REQUIRES_NEW` 락에 의해 정상 처리되더라도 불필요한 락 경합 및 DB 트래픽 유발)

#### Option B: DB `SKIP LOCKED` 기반 Worker Polling
- **쿼리 형태**:
  ```sql
  SELECT id FROM auctions
  WHERE status IN ('OPEN', 'ENDING') AND close_time <= NOW()
  ORDER BY close_time ASC
  FOR UPDATE SKIP LOCKED LIMIT 10;
  ```
- **장점**: 여러 Worker(또는 다중 서버 인스턴스)가 동시에 마감 작업을 수행하더라도, 이미 다른 Worker가 처리 중인 행은 `SKIP LOCKED`로 즉시 스킵하므로 **추가 인프라 없이 Scale-Out 동시성이 완벽히 보장**됨.
- **한계**: Polling 방식이므로 마감 시각과 실제 처리 시각 간에 Polling 주기(예: 1~5초) 만큼의 미세한 마감 지연이 발생할 수 있음.

#### Option C: Redis Redisson Delayed Queue / RabbitMQ Delay Exchange (추천 대규모 확장 모델)
- **동작 방식**: 경매가 생성되거나 시간이 연장될 때 마감 예정 시각을 타겟으로 메시지를 Delay Queue에 발행 (`publishDeadlineEvent`). 마감 시각이 되면 Consumer 스레드가 메시지를 소비하여 단건 마감 수행.
- **장점**:
  - 이벤트 기반으로 마감 정밀도가 매우 높음.
  - Scale-Out 환경에서 Consumer 분산 처리가 완벽하게 동작함.
  - DB Polling 트래픽이 완전히 제거됨.
- **한계**: Redis Redisson이나 RabbitMQ 등 추가 인프라 구축 및 메시지 유실 대비 보조 스케줄러(Fallback)를 여전히 유지해야 함.

#### Option D: ShedLock / Quartz Clustered
- **동작 방식**: DB나 Redis를 활용해 락을 잡은 단 1대의 서버(Leader)만 스케줄러를 실행.
- **장점**: 다중 서버 환경에서 기존 스케줄러 코드를 거의 수정하지 않고 중복 실행을 방지함.
- **한계**: 단 1대의 서버로 마감 부하가 몰릴 수 있음.

---

## 6. 이중 스케줄러 구조에서의 중복 실행 및 동시성 평가

현재 구현인 `AuctionDeadlineScheduler`(정밀 타이머)와 `AuctionClosingScheduler`(10초 주기 백업)가 동시 운용되는 구조에서 **중복 실행 및 경합 문제**를 평가한다.

### 6.1 동시 실행 시나리오 (Race Condition)
- 마감 시각이 `12:00:00`인 경매 A가 존재할 때:
  - `AuctionDeadlineScheduler`가 `12:00:00` 정각에 원샷 타이머로 트리거되어 `closeDueAuctions()` 실행.
  - 마침 `AuctionClosingScheduler`의 10초 주기 타이머 틱도 `12:00:00`에 돌면, 두 스케줄러가 동시에 `findDueAuctionIds()`를 쿼리하여 [경매 A.id]를 중복으로 가져올 수 있음.

### 6.2 데이터 정합성 평가 (Idempotency 100% 보장)
- **중복 낙찰 / 중복 지갑 차감 발생 여부**: **절대 발생하지 않음**
- **보장 매커니즘**:
  1. `AuctionCommandService.closeDueAuction(auctionId, now)` 메서드는 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 안에서 실행된다.
  2. 두 스레드가 동일한 경매 ID로 진입 시 DB의 `findByIdForUpdate(auctionId)` 구문에서 선점한 스레드(스레드 1)가 먼저 락을 잡는다.
  3. 스레드 1(Deadline)이 경매 상태를 `OPEN` $\rightarrow$ `ENDED` (또는 `FAILED`)로 변경하고 낙찰/정산 후 트랜잭션을 커밋한다.
  4. 대기하던 스레드 2(Closing)가 락을 바통 터치받아 최신 데이터를 조회하면 `isDueCloseTarget()`의 조건(`status == OPEN || status == ENDING`)이 `false`가 된다.
  5. 스레드 2는 아무런 갱신이나 이벤트 발행 없이 `Optional.empty()`를 반환하고 안전하게 멱등(NOP) 종료된다.

### 6.3 비효율 요소 및 최적화 추천
- **미세한 락 대기 오버헤드**: 마감 시각이 정확히 겹칠 때 한 스레드가 잠시 DB 락을 기다렸다가 상태 체크 후 리턴하는 미세한 찌름 오버헤드가 존재함.
- **최적화 추천**:
  - `AuctionClosingScheduler`(백업)의 주기 간격을 10초에서 **1분 ~ 5분**으로 확장한다. 백업 스케줄러의 본래 목적은 누락 구출이므로, 10초 간격은 불필요하게 잦아 Deadline 스케줄러와 겹칠 확률만 높인다.
