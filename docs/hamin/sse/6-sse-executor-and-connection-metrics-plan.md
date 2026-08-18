# SSE Executor 및 연결 종료 메트릭 계획

## 목표

Grafana에서 Auction, Notification, Wallet SSE 전송 경로의 executor 상태와 SSE 연결 종료 원인을 함께 관찰한다. executor 적체가 SSE 연결 문제로 이어지는지 같은 대시보드에서 판단할 수 있어야 한다.

대상 executor는 다음과 같다.

| Stream | Executor | 기본 프로필 | `sse-virtual-threads` 프로필 |
| --- | --- | --- | --- |
| Auction | `auctionSseTaskExecutor` | 플랫폼 스레드 풀 | 가상 스레드 per-task executor |
| Notification | `notificationFanOutTaskExecutor` | 플랫폼 스레드 풀 | 가상 스레드 per-task executor |
| Wallet | `walletSseTaskExecutor` | 플랫폼 스레드 풀 | 해당 없음 |

`notificationTaskExecutor`는 알림 저장·발행(origin) 작업용이므로 이 문서의 SSE fan-out 관측 대상에서 제외한다.

## 범위

- 세 executor의 Prometheus 메트릭을 백엔드 Actuator endpoint에 노출한다.
- Auction, Notification, Wallet SSE 연결의 종료 횟수와 종료 원인을 계측한다.
- Grafana 패널에서 사용할 PromQL과 경보 판단 기준을 문서화한다.
- 플랫폼 스레드와 가상 스레드 프로필의 메트릭 등록·값을 테스트한다.

## 비범위

- 프론트에서 재연결 식별자나 재연결 횟수를 전달하지 않는다.
- 신규 Grafana dashboard JSON이나 Prometheus scrape 설정을 이 저장소에 추가하지 않는다. 이 저장소 밖의 모니터링 환경에서 아래 쿼리를 이용한다.
- executor 크기, queue capacity, SSE 재연결 정책 자체는 이번 변경에서 조정하지 않는다.

## Executor 메트릭

### 공통 태그

가상 스레드 executor(직접 계측)는 다음 태그를 사용한다.

| 태그 | 값 |
| --- | --- |
| `executor` | `auction-sse`, `notification-sse` |
| `thread_type` | `virtual` (고정) |

platform executor는 Spring Boot가 이미 자동 계측하므로 별도 태그 체계를 두지 않고, 아래 "플랫폼 스레드 풀" 절의 `name` 태그를 그대로 쓴다.

### 플랫폼 스레드 풀

**구현 중 변경**: 별도로 `ExecutorServiceMetrics`를 수동 바인딩하지 않는다. Spring Boot는 컨텍스트의 모든 `ThreadPoolTaskExecutor`/`ThreadPoolTaskScheduler` 빈을 자동으로 계측해 `name=<빈 이름>` 태그(예: `name="auctionSseTaskExecutor"`, `name="notificationFanOutTaskExecutor"`, `name="walletSseTaskExecutor"`)로 이미 노출하고 있다 (로컬 `/actuator/prometheus`로 실측 확인, 이 문서 작성 시점 기준 변경 전에도 존재). 수동 바인딩을 추가하면 같은 `ThreadPoolExecutor`를 `executor`/`thread_type` 태그로 한 번 더 등록하는 순수 중복이 되고(Gauge가 `NaN`으로 찍히는 등 부작용도 관측됨), 새 정보를 제공하지 않는다. 따라서 platform executor는 아래 기존 Spring Boot 메트릭을 `name` 태그로 그대로 사용한다.

| Prometheus 메트릭(실측) | 의미 |
| --- | --- |
| `executor_active_threads{name=...}` | 현재 실행 중인 send task 수 |
| `executor_queued_tasks{name=...}` | executor queue에서 대기 중인 task 수 |
| `executor_queue_remaining_tasks{name=...}` | queue의 남은 수용량 |
| `executor_pool_size_threads{name=...}` | 현재 생성된 플랫폼 스레드 수 |
| `executor_pool_core_threads{name=...}` / `executor_pool_max_threads{name=...}` | 설정된 core/max thread 수 |
| `executor_completed_tasks_total{name=...}` | 완료된 task 누적 수 |

`name` 값은 각각 `auctionSseTaskExecutor`, `notificationFanOutTaskExecutor`, `walletSseTaskExecutor`다.

### 가상 스레드 per-task executor

`SimpleAsyncTaskExecutor#setVirtualThreads(true)`에는 고정 pool과 작업 대기 queue가 없다. 따라서 platform executor와 같은 pool/queue Gauge를 만드는 것은 의미가 없고, executor 제출 경계를 감싸 task lifecycle을 계측한다.

| 메트릭 | 의미 |
| --- | --- |
| `dbidding.sse.executor.submitted` | 제출된 virtual task 누적 수 |
| `dbidding.sse.executor.active` | 현재 실행 중인 virtual task 수 |
| `dbidding.sse.executor.completed` | 정상 종료된 virtual task 누적 수 |
| `dbidding.sse.executor.failures` | 실행 중 예외로 종료된 task 누적 수 |
| `dbidding.sse.executor.task.duration` | task 실행 시간 Timer 및 histogram |

가상 스레드 executor에서는 queue 사용률이나 thread pool 포화율 패널을 표시하지 않는다. `active` 급증, 실행 시간 p95 상승, 실패 증가를 포화 또는 느린 I/O의 관측 신호로 사용한다.

### 기존 포화 메트릭 유지

플랫폼 스레드 풀의 `CountingCallerRunsPolicy`가 이미 발행하는 다음 메트릭은 유지한다.

| 메트릭 | 태그 | 의미 |
| --- | --- | --- |
| `dbidding.sse.broadcast.saturated` | `executor=auction|notification-fanout|wallet` | pool과 queue가 모두 찬 뒤 CallerRuns로 실행된 작업 수 |
| `dbidding.sse.broadcast.saturated.caller-runs.duration` | 동일 | 호출 스레드에서 직접 실행한 작업 시간 |

이 값이 증가한 시점은 queue가 이미 포화된 뒤이므로, 큐 사용률 패널의 사전 경보와 함께 사용한다.

## SSE 연결 종료 메트릭

재연결 여부는 프론트가 식별자를 제공하지 않는 한 신규 탭 연결과 구분할 수 없다. 따라서 재연결 횟수는 계측하지 않고, 서버가 확실히 알 수 있는 연결 종료만 계측한다.

| 메트릭 | 태그 | 의미 |
| --- | --- | --- |
| `dbidding.sse.connections.closed` | `stream`, `reason` | 종료된 SSE 연결의 누적 수 |
| `dbidding.sse.connection.duration` | `stream`, `reason` | 연결 수립부터 제거까지의 지속 시간 |
| `dbidding.sse.connections` | `stream` | 현재 연결 수 Gauge |
| `dbidding.*.sse.send.failures` | stream별 기존 또는 신규 메트릭 | emitter 전송 실패 누적 수 |

`stream`은 `auction`, `notification`, `wallet`이다. `reason`은 다음으로 제한한다.

| 종료 원인 | 기록 위치 |
| --- | --- |
| `completion` | `SseEmitter.onCompletion` |
| `timeout` | `SseEmitter.onTimeout` |
| `error` | `SseEmitter.onError` |
| `send_failure` | `emitter.send()`의 `IOException` 또는 `IllegalStateException` |

각 emitter에 연결 시작 시각과 종료 기록 여부를 보관한다. `onError` 뒤 `onCompletion`이 연속 호출돼도 연결 종료 counter와 duration이 중복 기록되지 않도록 제거 경로를 멱등 처리한다. `send_failure`는 종료 원인으로 한 번 기록하고 이후 completion callback에서는 다시 기록하지 않는다.

Auction과 Notification은 현재 연결 수 Gauge가 있으며, Wallet에도 같은 Gauge를 추가한다. Auction의 send failure metric은 이미 있으므로 공통 태그 체계로 정리할 때 기존 dashboard 호환성을 고려한다.

## 구현 구조

1. SSE executor 관측을 위한 공통 decorator를 추가한다.
   - platform executor는 Spring Boot 자동 계측을 그대로 쓴다 (수동 바인딩 없음).
   - virtual executor(`VirtualThreadSseTaskExecutor`, `SimpleAsyncTaskExecutor` 상속)는 `execute()` 경계를 감싸 lifecycle counter, active Gauge, Timer를 기록한다.
2. `AuctionSseExecutorConfig`, `NotificationExecutorConfig`에서 `sse-virtual-threads` 프로필 빈을 `VirtualThreadSseTaskExecutor`로 교체한다. `WalletSseExecutorConfig`는 변경 없음(virtual 프로필 대상 아님).
3. `AuctionSseConnectionManager`, `NotificationSseConnectionManager`, `WalletSseConnectionManager`에 stream별 connection metrics(`SseConnectionCloseMetrics` 공유)를 주입한다.
4. emitter 등록 시 시작 시각을 기록하고, 모든 제거 경로가 하나의 종료 기록 메서드를 거치게 한다.
5. 기존 공개 API와 SSE payload·frontend URL은 변경하지 않는다.

## Grafana 패널 및 PromQL

아래 metric 이름은 로컬 `/actuator/prometheus` 실측으로 확인했다 (Micrometer Prometheus naming convention이 Gauge/Counter에 baseUnit을 접미사로 붙인다).

| 패널 | 플랫폼 스레드 executor (`name` 태그) | 가상 스레드 executor (`executor`+`thread_type="virtual"` 태그) |
| --- | --- | --- |
| 실행량 | `executor_active_threads / executor_pool_max_threads` | `dbidding_sse_executor_active` |
| queue 사용률 | `executor_queued_tasks / (executor_queued_tasks + executor_queue_remaining_tasks)` | 표시하지 않음 |
| 처리량 | `rate(executor_completed_tasks_total[1m])` | `rate(dbidding_sse_executor_completed_total[1m])` |
| task 지연 | 필요 시 executor 완료율과 함께 해석 | `histogram_quantile(0.95, rate(dbidding_sse_executor_task_duration_seconds_bucket[5m]))` |
| CallerRuns | `rate(dbidding_sse_broadcast_saturated_total[5m])` | 해당 없음 |

platform executor의 `name` 값: `auctionSseTaskExecutor`, `notificationFanOutTaskExecutor`, `walletSseTaskExecutor`.

연결 품질 패널은 세 stream에 공통으로 둔다.

```promql
# 비정상 종료율: timeout, error, send_failure만 합산
sum by (stream) (
  rate(dbidding_sse_connections_closed_total{reason=~"timeout|error|send_failure"}[5m])
)

# 종료 원인별 증가율
sum by (stream, reason) (
  rate(dbidding_sse_connections_closed_total[5m])
)

# 연결 지속 시간 p95
histogram_quantile(
  0.95,
  sum by (le, stream, reason) (
    rate(dbidding_sse_connection_duration_seconds_bucket[5m])
  )
)
```

운영 판단은 다음 순서로 한다.

1. platform executor의 queue 사용률이 지속 상승하는지 본다.
2. `saturated` 또는 CallerRuns가 증가하는지 확인한다.
3. 같은 시점의 `send_failure`, `timeout`, `error` 종료 증가 여부를 비교한다.
4. 가상 스레드에서는 active task와 task duration p95가 증가하는지 비교한다.

## 검증 계획

- platform executor: Spring Boot 자동 계측(`name` 태그)을 그대로 쓰므로 별도 바인딩 테스트를 추가하지 않는다.
- virtual executor: block된 task를 이용해 submitted, active, completed, failures, duration 메트릭과 실제 virtual thread 실행을 검증한다 (`VirtualThreadSseTaskExecutorTest`).
- 각 executor config: 기존 포화(CallerRuns) 테스트가 그대로 통과하는지 확인한다.
- 각 SSE connection manager: completion, timeout, error, send failure마다 종료 counter가 한 번만 증가하고 duration이 기록되는지 검증한다.
- Wallet: 현재 연결 수 Gauge 등록과 emitter 제거 후 값 감소를 검증한다.
- 회귀: 기존 Auction/Notification SSE 연결·전송 테스트를 모두 실행한다.

## 완료 기준

- `/actuator/prometheus`에서 Auction, Notification fan-out, Wallet executor의 상태 메트릭을 프로필별로 확인할 수 있다.
- 플랫폼 스레드 executor는 pool·queue 상태를, 가상 스레드 executor는 task lifecycle 상태를 제공한다.
- 세 SSE stream의 종료 수와 종료 원인·지속 시간을 Grafana에서 조회할 수 있다.
- SSE 연결 식별자나 재연결 정보를 프론트에서 추가로 보내지 않는다.
