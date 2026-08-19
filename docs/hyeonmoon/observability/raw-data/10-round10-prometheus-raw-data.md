# 10차 부하테스트 — Prometheus 원시 집계 데이터

이 문서는 K6 결과 JSON의 실제 종료 시각과 실행 시간을 기준으로 stage를 재구성하고, 각 구간 끝 시점에 Prometheus range/vector query를 평가해 만든 원시 집계표다. p50/p95/p99는 서버의 `http_server_requests_seconds_bucket` histogram으로 계산했다. 값은 Prometheus 원 단위(시간은 ms 변환)를 유지하며, `N/A`/빈 표는 그 시간대에 해당 시계열이 없었음을 뜻한다.

수집 범위는 테스트 대상 백엔드, backend/mysql/redis node exporter, MySQL exporter, Redis exporter다. SSE 섹션은 9차와 달리 `stream=notification`/`stream=wallet` 대신 `stream=me`(PR #558/#562로 병합된 `GET /api/me/stream`)로 나온다 — 엔드포인트 자체가 바뀐 결과이지 수집 누락이 아니다. Grafana/Prometheus 자기 관측 메트릭과 정적 build/info/config 시계열은 성능 측정값이 아니므로 제외했다.

## 실행 목록

| 결과 파일 | 시나리오 | 실제 실행 (UTC) | K6 전체 요청 | 평균 요청률 | avg | med | p95 | p99 | max |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| [`round10-pure-throughput-sse250-20260817.json`](../../../../backend/src/test/k6/result/round10-pure-throughput-sse250-20260817.json) | pure-throughput (SSE_VUS=250) | 2026-08-17T16:37:16Z ~ 2026-08-17T16:50:58Z | 139,010 | 169.18 req/s | 2,337.06 | 260.05 | 8,940.51 | 9,107.88 | 9,261.62 |
| [`round10-pure-throughput-sse500-20260817.json`](../../../../backend/src/test/k6/result/round10-pure-throughput-sse500-20260817.json) | pure-throughput (SSE_VUS=500) | 2026-08-17T16:51:17Z ~ 2026-08-17T17:05:02Z | 130,664 | 158.51 req/s | 1,895.86 | 202.48 | 8,501.16 | 8,739.55 | 9,002.49 |
| [`round10-pure-throughput-sse1000-20260817.json`](../../../../backend/src/test/k6/result/round10-pure-throughput-sse1000-20260817.json) | pure-throughput (SSE_VUS=1000) | 2026-08-17T17:06:33Z ~ 2026-08-17T17:20:33Z | 128,334 | 154.92 req/s | 1,732.97 | 183.09 | 6,988.24 | 10,185.06 | 37,992.66 |
| [`round10-hot-auction-pattern-20260817.json`](../../../../backend/src/test/k6/result/round10-hot-auction-pattern-20260817.json) | hot-auction-pattern | 2026-08-17T17:22:08Z ~ 2026-08-17T17:30:24Z | 29,749 | 61.29 req/s | 84.32 | 84.15 | 108.48 | 149.75 | 270.52 |
| [`round10-bid-only-load-noSSE-20260817.json`](../../../../backend/src/test/k6/result/round10-bid-only-load-noSSE-20260817.json) | bid-only-load (SSE 없음, 분산) | 2026-08-17T17:30:57Z ~ 2026-08-17T17:43:21Z | 134,677 | 183.12 req/s | 785.10 | 121.34 | 4,347.43 | 5,771.68 | 9,777.18 |
| [`round10-bid-only-load-singleHotAuction-20260817.json`](../../../../backend/src/test/k6/result/round10-bid-only-load-singleHotAuction-20260817.json) | bid-only-load (SSE 없음, 핫경매집중, `HOT_AUCTION_ID=3001001`) | 2026-08-17T17:43:30Z ~ 2026-08-17T17:55:54Z | 136,580 | 187.41 req/s | 373.36 | 103.00 | 2,338.70 | 3,104.09 | 3,253.87 |

**실행 순서 관련 특이사항:** `hot-auction-pattern`은 최초 시도(2026-08-17T17:20:42Z)가 스크립트의 `AUCTION_IDS` 필수 검증(`scenarios/hot-auction-pattern.js:81`, `#390`으로 도입된 선택 구독 요구사항)에 걸려 즉시 실패했다 — 런북(§2.2)에는 이 환경변수가 문서화되어 있지 않다. `AUCTION_IDS=3001001..3001200`(시드된 300개 중 200개, `HOT_AUCTION_IDS`는 기본값으로 앞 3개 사용)으로 재시도했으나 두 번째 시도도 macOS BSD `seq -s,`가 큰 정수를 과학적 표기(`3.001e+06`)로 출력하는 문제로 재차 실패했다. `python3`로 생성한 순수 정수 CSV로 세 번째 시도에서 성공했다. 위 표의 시각은 성공한 세 번째 실행 기준이며, 이 두 번의 실패(총 ~90초)는 어느 시나리오의 측정 구간에도 포함되지 않는다.

---

## round10-pure-throughput-sse250-20260817.json

- 시나리오: `pure-throughput-sse250`
- K6 실행: 2026-08-17T16:37:16Z ~ 2026-08-17T16:50:58Z

### QPS50 — 2026-08-17T16:37:52.000Z ~ 2026-08-17T16:39:52.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 1,248 | 69.77 | 66.78 | 102.58 | 130.95 | 216.71 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,153 | 33.98 | 33.72 | 50.06 | 55.62 | 67.73 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,584 | 55.16 | 53.43 | 81.92 | 89.34 | 183.30 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 16 | 60.81 | 60.40 | 86.35 | 88.85 | 81.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.92 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 3.21 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,185 | 54.90 | 53.36 | 77.34 | 87.65 | 127.54 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 41.79 | 42.10 | 49.49 | 50.16 | 48.23 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 132.92 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.24 | 0.53 | 1.05 | 1.96 | 2.72 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 29.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 16.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 8.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.18 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.05 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 108.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 108.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.27 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.31 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,031,744.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 122,662,912.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,346,368.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,067,008.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,744,000.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,293,376.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,899,200.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,031,744.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,489,728.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,533,248.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,559,552.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,933,440.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,871,424.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,040,000.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 1,434.29 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 3.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 5.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 3.14 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 45.39 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 35.26 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 25.59 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.11 |
| `node_load1_avg` | instance=redis, job=redis-node | 0.75 |
| `node_load1_avg` | instance=backend, job=backend-node | 0.91 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.22 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 116.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 32.00 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 120.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 350,288,384.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 536,807,424.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 309,494,272.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 243,746,816.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,505,472.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,701,299,712.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,571,948,032.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 11.43 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 33.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 104.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 32.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 93,978.29 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 305.14 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 2.29 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.12 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 13,328,399.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 13,656,544.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 2.40 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 929,324.57 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 4,850.29 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,016,613.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 3,438.86 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS100 — 2026-08-17T16:39:52.000Z ~ 2026-08-17T16:41:52.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 1,729 | 105.95 | 106.19 | 133.55 | 164.61 | 276.24 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,828 | 53.36 | 53.55 | 62.33 | 76.22 | 73.77 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,338 | 78.90 | 78.01 | 95.61 | 109.87 | 264.14 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 81.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 1 | 1.10 | 1.22 | 1.38 | 1.39 | 1.10 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,745 | 78.03 | 77.61 | 91.75 | 106.54 | 191.53 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 22 | 60.71 | 60.72 | 78.29 | 87.24 | 75.52 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 62.11 | 61.52 | 80.53 | 87.69 | 67.65 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.21 | 0.52 | 0.99 | 1.68 | 4.34 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 2.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 27.62 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 28.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.03 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 108.12 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 109.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.36 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.41 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 15,926,784.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 128,883,712.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,365,312.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,181,696.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,616,000.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,285,696.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,785,536.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,031,744.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 133,312,512.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,365,312.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,436,672.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,720,448.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,809,984.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,101,440.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 1,147.43 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 7.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 11.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 3.00 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.41 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 44.87 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 30.96 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.03 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.42 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.02 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.52 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 117.71 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 22.86 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 227.43 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 354,353,152.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 532,654,592.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 304,854,528.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 239,516,160.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,505,472.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,701,299,712.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,572,067,840.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 40.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 2.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 50.29 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 46.86 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 11.43 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 118,310.86 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 161.14 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 2.29 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.50 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.62 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 14,275,132.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 14,789,648.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 2.28 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,372,898.29 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 6,627.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,501,770.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 4,565.71 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS150 — 2026-08-17T16:41:52.000Z ~ 2026-08-17T16:43:52.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 2,971 | 133.53 | 128.21 | 192.19 | 245.32 | 311.57 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,987 | 63.15 | 61.42 | 84.64 | 106.40 | 204.96 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,937 | 96.11 | 90.60 | 136.13 | 194.92 | 281.52 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 1.10 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,894 | 94.97 | 89.86 | 129.93 | 185.30 | 242.64 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 70 | 76.76 | 75.00 | 110.66 | 209.83 | 222.99 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 12 | 71.32 | 74.10 | 87.94 | 89.17 | 87.10 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.39 | 0.56 | 1.63 | 2.20 | 4.34 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 6.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 26.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 42.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 6.86 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.50 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.06 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 112.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 120.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.51 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.57 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 15,962,112.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 134,885,376.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,594,688.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,112,064.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,728,640.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,175,616.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,674,944.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 15,994,880.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 135,409,664.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,627,456.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,596,416.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,851,520.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,564,224.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,859,776.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 1,147.43 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 15.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 27.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 34.12 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.56 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.98 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 63.81 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 32.50 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.09 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.57 |
| `node_load1_avg` | instance=backend, job=backend-node | 3.18 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.64 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 2.29 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 249.14 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 162.29 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 354,925,056.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 540,788,224.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 298,880,512.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 243,653,120.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,505,472.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,701,297,664.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,571,469,312.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 2.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 59.43 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 99.43 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 5,368.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 1,113.14 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 142,331.43 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 1,036.57 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 9.14 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.50 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 7.62 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 16,219,380.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 17,381,104.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 2.09 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 2,289,538.29 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 10,547.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,513,941.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 6,589.71 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS200 — 2026-08-17T16:43:52.000Z ~ 2026-08-17T16:45:52.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 4,155 | 173.19 | 161.59 | 274.85 | 333.83 | 401.33 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,194 | 72.31 | 65.23 | 119.04 | 181.82 | 312.19 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,529 | 121.39 | 105.78 | 216.32 | 268.03 | 401.30 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 1.10 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,982 | 118.90 | 104.33 | 207.63 | 254.80 | 351.27 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 177 | 99.21 | 88.26 | 180.08 | 212.14 | 222.99 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 14 | 97.30 | 87.24 | 164.42 | 176.05 | 163.62 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.50 | 0.60 | 1.66 | 2.90 | 2.96 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.50 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 7.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 25.50 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 56.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 8.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.61 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.09 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.50 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.65 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.71 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 15,994,880.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 126,564,864.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,513,792.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,212,416.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,785,984.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,177,152.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,539,264.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 15,994,880.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 135,409,664.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,627,456.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,461,248.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,835,136.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,564,224.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,802,432.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 1,147.43 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 26.62 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 36.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 49.38 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.58 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.07 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 78.38 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 33.57 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.02 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.71 |
| `node_load1_avg` | instance=backend, job=backend-node | 4.67 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.90 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 110.86 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 189.71 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 366,761,472.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 547,783,680.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 309,316,608.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 247,022,592.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,505,472.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,701,285,376.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,570,479,104.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 13.71 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 460.57 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 641.14 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 166,613.71 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 4.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 3,874.29 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 900.57 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 3.00 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 7.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 19,412,840.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 20,984,272.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.87 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 3,164,745.14 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 13,834.29 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 3,460,802.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 8,537.14 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS300 — 2026-08-17T16:45:52.000Z ~ 2026-08-17T16:47:52.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 5,857 | 208.58 | 205.61 | 288.01 | 341.56 | 409.69 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,755 | 112.49 | 111.50 | 153.17 | 176.82 | 312.19 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 17,400 | 167.68 | 165.14 | 229.68 | 260.81 | 401.30 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 1 | 161.79 | 167.77 | 177.84 | 178.73 | 161.79 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,150 | 167.68 | 166.30 | 230.62 | 264.00 | 351.27 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,494 | 128.42 | 125.28 | 173.21 | 200.49 | 269.44 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 21 | 124.68 | 122.10 | 180.08 | 197.08 | 194.68 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 146 | 0.66 | 0.65 | 2.90 | 4.10 | 5.03 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 10.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 21.62 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 76.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.91 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.04 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.78 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.81 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,061,440.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,294,656.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,475,904.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,476,096.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,750,656.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,455,168.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,702,080.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,125,952.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 125,526,016.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,475,904.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,702,912.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,835,136.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,760,832.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,913,024.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 1,147.43 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 49.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.58 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 45.22 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 91.90 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 31.68 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.06 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.44 |
| `node_load1_avg` | instance=backend, job=backend-node | 6.95 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.88 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 3.43 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 0.00 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 86.86 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 367,362,048.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 543,485,440.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 306,568,704.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 245,144,064.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,505,472.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,701,243,904.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,570,481,664.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 2.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 88.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 678.86 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 181,539.43 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 668.57 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 4.57 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 8.88 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 23,200,326.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 24,626,112.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.70 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 3,491,924.57 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 17,700.57 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 3,861,062.86 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 11,309.71 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS400 — 2026-08-17T16:47:52.000Z ~ 2026-08-17T16:49:52.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 6,945 | 190.65 | 187.11 | 251.68 | 298.40 | 409.69 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 6,545 | 110.03 | 106.53 | 149.63 | 176.61 | 275.29 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 19,867 | 157.07 | 151.79 | 208.74 | 242.88 | 371.49 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 133 | 131.01 | 127.63 | 173.16 | 192.60 | 197.94 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,508 | 161.91 | 155.07 | 218.10 | 251.82 | 343.90 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 4,478 | 121.58 | 119.07 | 162.08 | 196.37 | 309.30 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 18 | 122.99 | 119.30 | 205.80 | 220.12 | 206.04 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 59 | 0.56 | 0.59 | 2.69 | 5.23 | 5.03 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 10.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 21.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 99.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 1.40 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.62 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 131.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.79 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.80 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 15,928,320.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,039,168.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,536,832.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,288,192.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,744,512.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,163,840.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,553,088.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,195,584.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,268,544.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,606,976.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,526,784.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,962,112.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,482,304.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,675,456.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 251.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 1,434.29 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.56 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 46.16 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 91.97 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 25.85 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.03 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.50 |
| `node_load1_avg` | instance=backend, job=backend-node | 7.78 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.72 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 6.86 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 163.43 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 78.86 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 364,550,144.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 543,371,264.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 306,124,800.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 245,916,160.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,505,472.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,701,172,224.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,570,481,664.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 5.71 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 385.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 69.71 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 7,393.14 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 160,785.14 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 755.43 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 6.86 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.62 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 10.00 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 25,513,804.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 25,928,672.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.63 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,806,474.29 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 18,891.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,149,590.86 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 14,064.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |



## round10-pure-throughput-sse500-20260817.json

- 시나리오: `pure-throughput-sse500`
- K6 실행: 2026-08-17T16:51:17Z ~ 2026-08-17T17:05:02Z

### QPS50 — 2026-08-17T16:51:53.000Z ~ 2026-08-17T16:53:53.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 1,258 | 74.45 | 73.67 | 106.60 | 137.38 | 385.66 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 867,087.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,142 | 35.39 | 33.66 | 49.87 | 57.79 | 288.36 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,126 | 59.98 | 59.13 | 86.57 | 102.74 | 367.60 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 1,475 | 47.67 | 45.19 | 70.31 | 86.14 | 183.96 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 839,294.57 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 704 | 62.50 | 60.21 | 86.16 | 89.46 | 326.46 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1 | 58.67 | 58.72 | 61.24 | 61.46 | 255.82 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 51.70 | 53.13 | 60.68 | 61.35 | 306.67 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 175.48 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.15 | 0.50 | 0.96 | 1.00 | 1.87 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.50 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 29.50 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 21.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.23 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.02 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.27 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.31 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 15,982,592.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 123,659,264.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,428,288.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,226,752.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,785,472.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,188,928.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,724,608.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,146,432.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,624,896.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,701,184.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,461,248.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,929,344.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,494,592.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,171,072.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 2,290.29 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 2.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 3.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.56 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 43.77 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 35.77 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 29.83 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.11 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.34 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.80 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 4.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 36.57 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 361,437,696.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 542,691,328.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 287,212,544.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 245,476,864.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,562,816.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,701,238,272.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,570,481,664.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 2.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 4.57 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 29.71 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 5.71 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 104,126.86 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 77.71 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.50 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 26,517,379.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 26,644,560.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.61 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 597,421.71 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 5,691.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 673,009.14 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 3,521.14 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS100 — 2026-08-17T16:53:53.000Z ~ 2026-08-17T16:55:53.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 1,789 | 95.64 | 94.44 | 129.86 | 136.22 | 175.10 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | 30,000.00 | 30,000.00 | 30,000.00 | 867,087.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,699 | 48.32 | 49.15 | 60.03 | 69.64 | 117.27 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,077 | 76.21 | 76.69 | 95.23 | 106.98 | 173.67 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 2,152 | 58.61 | 59.19 | 84.03 | 89.15 | 119.81 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 2 | 1,800,365.68 | 30,000.00 | 30,000.00 | 30,000.00 | 1,800,365.87 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 1 | 46.90 | 47.54 | 50.05 | 50.28 | 46.90 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 1 | 66.43 | 64.31 | 66.83 | 67.05 | 66.43 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 962 | 77.95 | 77.65 | 92.08 | 101.15 | 148.51 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 13 | 60.40 | 61.52 | 76.06 | 86.79 | 67.60 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 60.75 | 59.42 | 81.65 | 87.91 | 76.59 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.20 | 0.51 | 0.97 | 1.23 | 1.21 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.62 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 28.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 24.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.24 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.03 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.33 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.39 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,016,384.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 125,214,720.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,389,888.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,347,072.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,777,792.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,307,712.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,676,480.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,146,432.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 125,411,328.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,422,656.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,522,688.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,777,792.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,535,552.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,892,544.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 2,290.29 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 4.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 8.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.62 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.97 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 41.98 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 29.54 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.32 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.67 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.82 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 4.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 58.29 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 364,428,800.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 542,222,848.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 292,731,392.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 250,617,344.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,562,816.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,701,238,272.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,570,483,200.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 54.86 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 11.43 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 108,666.29 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 2,956.57 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 669.71 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 26,815,568.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 27,120,528.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.61 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 845,918.86 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 8,356.57 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 951,504.00 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 4,240.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS150 — 2026-08-17T16:55:53.000Z ~ 2026-08-17T16:57:53.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 2,913 | 117.30 | 115.28 | 155.93 | 204.14 | 307.78 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,971 | 56.23 | 56.28 | 65.30 | 77.36 | 117.27 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,145 | 89.89 | 85.13 | 114.99 | 157.54 | 277.40 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 3,683 | 68.41 | 66.20 | 88.89 | 111.33 | 245.75 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 1,800,365.87 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 46.90 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 66.43 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,633 | 92.19 | 88.28 | 118.58 | 151.68 | 254.84 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 26 | 70.26 | 71.77 | 93.95 | 98.79 | 98.10 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 4 | 71.37 | 65.24 | 97.90 | 99.58 | 90.65 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.28 | 0.53 | 1.19 | 2.31 | 3.62 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 2.50 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 27.50 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 41.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.01 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 131.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.44 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.50 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,099,328.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 125,411,328.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,422,656.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,207,808.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,777,792.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,074,752.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,697,984.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,121,856.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 125,411,328.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,422,656.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,485,824.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,777,792.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,453,632.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,900,736.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 2,290.29 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 12.62 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 20.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.58 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 49.07 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 55.28 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 29.18 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.03 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.61 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.13 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.94 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 5.71 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 2.29 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 35.43 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 367,705,088.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 536,184,832.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 281,724,928.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 250,374,144.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,562,816.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,701,238,272.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,570,486,784.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 5.71 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 2.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 36.57 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 3.43 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 128,125.71 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.50 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 27,536,812.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 27,921,168.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.60 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,397,301.71 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 13,881.14 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,570,094.86 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 142.86 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 5,852.57 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS200 — 2026-08-17T16:57:53.000Z ~ 2026-08-17T16:59:53.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 4,171 | 142.25 | 135.77 | 218.95 | 290.63 | 385.43 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,114 | 62.01 | 60.13 | 83.72 | 106.66 | 147.02 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 7,233 | 105.36 | 96.70 | 168.31 | 225.33 | 340.86 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 5,195 | 81.77 | 76.33 | 132.34 | 184.75 | 328.61 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,258 | 106.96 | 98.02 | 169.33 | 222.85 | 314.91 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 61 | 85.16 | 80.16 | 140.93 | 189.25 | 195.19 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 16 | 77.42 | 76.70 | 103.55 | 110.19 | 105.42 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.40 | 0.58 | 1.54 | 2.28 | 3.62 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 26.12 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 53.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 4.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.61 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.05 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.62 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 130.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.58 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.64 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,195,584.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,554,752.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,571,136.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,060,864.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,593,472.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,236,032.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,670,848.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,244,736.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 125,411,328.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,684,800.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,682,432.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,777,792.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,609,280.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,872,064.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 2,862.86 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 20.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.57 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.47 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 70.89 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 30.52 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.11 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.69 |
| `node_load1_avg` | instance=backend, job=backend-node | 3.17 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.83 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 2.29 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 1.14 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 581.71 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 360,557,568.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 538,792,960.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 285,398,528.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 255,790,592.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,562,816.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,701,238,272.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,566,590,464.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 437.71 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 6.86 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 3,538.29 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 146,276.57 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 556.57 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 4.57 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 7.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 28,895,104.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 29,616,096.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.56 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,934,442.29 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 19,588.57 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,174,137.14 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 112.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 7,434.29 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS300 — 2026-08-17T16:59:53.000Z ~ 2026-08-17T17:01:53.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 5,794 | 213.49 | 208.42 | 298.32 | 351.22 | 452.52 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,840 | 116.72 | 115.31 | 175.85 | 253.34 | 400.45 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 10,040 | 178.86 | 177.20 | 254.78 | 323.71 | 576.96 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 7,395 | 145.38 | 143.50 | 215.15 | 291.53 | 528.05 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,781 | 176.38 | 176.18 | 253.17 | 294.96 | 448.92 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 325 | 136.70 | 133.31 | 188.14 | 215.42 | 242.38 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 18 | 138.97 | 139.81 | 205.80 | 220.12 | 209.84 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.71 | 0.65 | 2.88 | 4.05 | 6.04 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 6.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 14.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 23.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 72.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 5.71 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.92 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.08 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.50 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 130.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.76 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.79 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,244,736.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 178,269,696.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,430,848.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 16,926,720.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,568,896.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,351,232.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,665,216.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,244,736.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 186,351,616.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,512,768.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,326,080.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,753,216.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,723,968.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,798,336.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 2,290.29 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 43.62 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.67 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 43.71 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 90.77 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 29.92 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.22 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.35 |
| `node_load1_avg` | instance=backend, job=backend-node | 5.38 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.74 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 296.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 4.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 277.71 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 311,751,680.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 536,478,720.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 280,157,184.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 255,657,984.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,559,232.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,701,198,336.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,566,035,456.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 160.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 298.29 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 3.43 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 506.29 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 159,818.29 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 7,921.14 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 12,341.71 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.50 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 8.75 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 30,996,938.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 31,985,608.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.51 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 2,447,760.00 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 27,190.86 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,769,953.14 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 9,672.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS400 — 2026-08-17T17:01:53.000Z ~ 2026-08-17T17:03:53.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 7,260 | 194.92 | 190.26 | 266.81 | 323.22 | 452.52 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 6,854 | 122.75 | 120.18 | 166.81 | 192.18 | 400.45 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 10,456 | 174.99 | 170.51 | 228.85 | 263.48 | 576.96 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 10,726 | 140.74 | 136.48 | 187.95 | 215.45 | 528.05 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 1 | 2.48 | 2.62 | 2.78 | 2.79 | 2.48 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,267 | 178.25 | 173.43 | 234.54 | 260.09 | 448.92 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,105 | 135.79 | 130.38 | 182.77 | 215.49 | 293.75 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 16 | 145.90 | 134.22 | 277.90 | 295.58 | 279.49 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 68 | 0.63 | 0.64 | 2.68 | 4.75 | 6.04 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.62 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 12.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 21.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 96.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 4.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 1.41 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.06 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.12 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.79 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.80 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,244,736.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 145,235,968.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,463,616.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,134,592.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,802,368.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,003,072.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,695,424.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,244,736.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 186,351,616.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,512,768.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,440,768.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,015,360.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,543,744.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,052,288.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 2,290.29 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.76 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 44.14 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 93.04 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 23.94 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.10 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.55 |
| `node_load1_avg` | instance=backend, job=backend-node | 8.26 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.66 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 14.86 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 1,130.29 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 14.86 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 348,760,576.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 533,488,128.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 260,871,680.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 255,102,464.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,623,232.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,700,484,608.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,566,125,568.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 3.43 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 4,682.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 11.43 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 8,044.57 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 159,013.71 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.38 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 8.12 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 32,655,933.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 33,014,736.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.48 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,512,765.71 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 34,877.71 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,844,410.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 11,851.43 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |



## round10-pure-throughput-sse1000-20260817.json

- 시나리오: `pure-throughput-sse1000`
- K6 실행: 2026-08-17T17:06:33Z ~ 2026-08-17T17:20:33Z

### QPS50 — 2026-08-17T17:07:09.000Z ~ 2026-08-17T17:09:09.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 1,178 | 75.06 | 74.24 | 110.90 | 132.32 | 192.20 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 848,487.03 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,142 | 32.44 | 31.28 | 46.79 | 54.06 | 56.04 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 1,460 | 63.35 | 63.17 | 88.57 | 105.45 | 161.14 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 2,022 | 44.55 | 41.79 | 65.45 | 83.19 | 86.67 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 469 | 67.88 | 67.08 | 88.05 | 99.71 | 135.19 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2 | 52.91 | 53.13 | 55.64 | 55.87 | 54.19 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 47.64 | 50.33 | 55.36 | 55.81 | 50.41 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 173.19 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.13 | 0.50 | 0.96 | 1.00 | 1.27 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 2.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 29.12 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 26.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.01 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.12 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.28 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.30 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,199,680.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,895,232.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,396,032.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,245,696.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,788,032.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,211,456.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,736,384.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,216,064.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 125,009,920.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,455,424.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,453,056.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,904,768.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,637,952.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,904,832.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4,576.00 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 2.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 6.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 47.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.60 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 41.33 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 34.97 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 29.71 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.02 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.14 |
| `node_load1_avg` | instance=backend, job=backend-node | 0.82 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.90 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 5.71 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 10.29 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 62.86 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 365,341,184.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 538,268,160.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 240,388,096.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 260,438,016.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,665,216.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,696,557,568.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,566,148,096.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 3.43 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 5.71 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 85.71 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 101,522.29 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.88 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 33,956,615.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 34,019,208.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.48 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 413,272.00 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 6,413.71 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 479,133.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 3,189.71 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS100 — 2026-08-17T17:09:09.000Z ~ 2026-08-17T17:11:09.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 1,705 | 91.47 | 89.40 | 131.54 | 149.75 | 192.20 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,676 | 45.77 | 47.04 | 63.06 | 67.06 | 103.11 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,115 | 77.15 | 77.31 | 99.04 | 118.31 | 173.92 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 2,958 | 55.06 | 56.53 | 81.89 | 88.52 | 153.46 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 681 | 80.57 | 79.18 | 99.88 | 110.50 | 135.19 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 4 | 66.31 | 67.11 | 87.24 | 89.03 | 68.91 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 4 | 63.42 | 61.52 | 85.00 | 88.58 | 71.20 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.19 | 0.51 | 0.97 | 1.61 | 1.96 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 2.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 28.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 29.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 4.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.38 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.06 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.33 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.39 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,214,016.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,878,848.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,725,760.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,213,952.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,965,696.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,338,432.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,618,624.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,347,136.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,878,848.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,807,680.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,432,576.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,166,912.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,842,752.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,740,992.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 5,720.00 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 4.38 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 11.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 47.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.64 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 46.31 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 41.49 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 30.64 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.02 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.25 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.69 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.63 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 30.86 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 41.14 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 137.14 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 364,600,320.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 536,509,440.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 232,010,240.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 260,586,496.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,665,216.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,696,597,504.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,566,160,384.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 2.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 118.86 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 3.43 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 102,222.86 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 10,509.71 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 16,262.86 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.62 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 34,018,048.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 34,148,200.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.48 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 578,704.00 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 9,513.14 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 670,449.14 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 3,649.14 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS150 — 2026-08-17T17:11:09.000Z ~ 2026-08-17T17:13:09.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 2,880 | 132.96 | 122.00 | 222.04 | 351.16 | 535.09 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,896 | 54.56 | 53.89 | 63.79 | 82.35 | 117.13 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,548 | 107.97 | 94.75 | 200.00 | 295.08 | 402.17 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 5,120 | 70.48 | 64.47 | 110.35 | 153.35 | 304.01 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,107 | 116.12 | 103.83 | 200.55 | 271.70 | 389.27 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 34 | 96.63 | 86.04 | 190.14 | 216.99 | 211.24 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 11 | 85.09 | 82.02 | 128.63 | 133.10 | 124.76 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.24 | 0.53 | 1.05 | 2.31 | 2.61 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 2.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 27.62 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 41.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 4.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.53 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.05 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.46 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.52 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,191,488.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,741,632.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,605,440.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,283,072.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,925,248.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,110,080.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,665,216.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,191,488.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,878,848.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,656,128.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,371,136.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,974,400.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,891,904.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,716,416.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4,576.00 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 14.12 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 22.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 47.75 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.70 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.93 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 56.67 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 26.51 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.42 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.03 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.85 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 9.14 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 12.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 26.29 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 364,751,360.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 532,654,592.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 221,677,056.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 254,899,712.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,665,216.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,696,597,504.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,566,180,864.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 6.86 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 6.86 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 11.43 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 114,712.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 517.71 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 4.57 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.50 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 6.12 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 34,257,392.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 34,357,896.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.49 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 966,203.43 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 16,219.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,118,395.43 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 237.71 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 5,022.86 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS200 — 2026-08-17T17:13:09.000Z ~ 2026-08-17T17:15:09.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 4,060 | 172.99 | 156.54 | 298.11 | 359.12 | 535.09 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,112 | 63.51 | 58.97 | 98.60 | 156.60 | 205.18 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,008 | 128.37 | 107.64 | 261.02 | 316.98 | 402.17 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 7,251 | 96.83 | 76.45 | 211.07 | 252.54 | 304.01 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,518 | 147.65 | 127.90 | 279.96 | 331.37 | 389.27 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 65 | 138.58 | 138.41 | 225.37 | 255.68 | 248.71 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 133.57 | 134.22 | 214.75 | 221.91 | 217.68 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.46 | 0.57 | 2.45 | 4.61 | 5.04 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.00 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 7.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 26.00 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 56.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 5.71 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.65 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.05 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.59 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.65 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,019,456.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,731,392.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,442,624.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,711,104.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,061,440.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,009,728.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,588,416.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,183,296.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,731,392.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,520,960.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 18,411,520.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,318,464.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,474,112.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,740,992.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4,576.00 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 21.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 40.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.69 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 46.16 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 72.93 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 25.91 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.53 |
| `node_load1_avg` | instance=backend, job=backend-node | 4.24 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 1.69 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 5.71 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 200.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 368,921,600.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 535,151,616.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 217,197,568.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 250,694,656.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,665,216.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,696,597,504.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,922,816.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 44.57 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 129,443.43 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 320.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 3.43 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 8.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 34,756,880.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 35,089,720.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.49 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,403,051.43 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 22,965.71 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,618,461.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 264.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 6,587.43 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS300 — 2026-08-17T17:15:09.000Z ~ 2026-08-17T17:17:09.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 5,765 | 189.25 | 180.96 | 296.92 | 357.10 | 535.09 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,725 | 109.17 | 106.75 | 174.45 | 197.93 | 244.06 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,954 | 165.82 | 163.89 | 254.51 | 292.66 | 500.11 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 10,282 | 130.80 | 127.95 | 210.34 | 245.29 | 388.02 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,084 | 168.53 | 167.37 | 257.92 | 296.80 | 449.64 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 122 | 136.12 | 131.52 | 208.60 | 245.54 | 248.71 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 18 | 124.73 | 134.22 | 192.38 | 199.54 | 217.68 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.67 | 0.62 | 3.67 | 5.48 | 7.36 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 7.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 11.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 22.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 77.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 5.71 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.94 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.07 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.74 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.79 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,199,680.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,731,392.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,546,048.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,395,712.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,865,344.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,078,848.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,582,272.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,314,368.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,731,392.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,762,624.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,752,064.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,945,728.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,371,712.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,757,376.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4,576.00 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 44.12 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.66 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 44.37 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 90.10 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 26.24 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.02 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.41 |
| `node_load1_avg` | instance=backend, job=backend-node | 6.35 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 1.09 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 3.43 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 348.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 112.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 367,005,696.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 528,811,008.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 202,138,112.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 252,903,936.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,665,216.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,694,122,496.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,942,272.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1,202.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 194.29 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 1,710.86 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 18.29 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 148,950.86 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 451.43 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 4.57 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.38 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 7.00 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 36,272,100.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 37,210,400.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.47 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,897,916.57 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 32,091.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,195,293.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 2.29 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 8,739.43 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS400 — 2026-08-17T17:17:09.000Z ~ 2026-08-17T17:19:09.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 7,350 | 195.70 | 187.19 | 284.21 | 350.30 | 608.32 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 6,938 | 127.62 | 122.77 | 180.09 | 215.71 | 494.29 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,821 | 184.18 | 177.01 | 254.50 | 299.30 | 550.84 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 15,523 | 147.31 | 141.85 | 205.39 | 244.01 | 506.15 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,106 | 192.03 | 184.69 | 263.44 | 303.09 | 528.04 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 565 | 141.49 | 136.04 | 199.98 | 238.98 | 373.57 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 11 | 150.29 | 134.22 | 218.10 | 222.58 | 221.60 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 82 | 0.81 | 0.63 | 4.47 | 7.89 | 8.35 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 6.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 10.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 23.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 78.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 2.09 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.17 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 130.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.78 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.80 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 15,985,664.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,397,568.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,471,808.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,299,968.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,106,048.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,061,952.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,539,776.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,158,720.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,731,392.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,471,808.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,616,896.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,810,560.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,498,688.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,740,992.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1,001.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4,576.00 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.67 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 42.51 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 95.48 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 22.21 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.02 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.49 |
| `node_load1_avg` | instance=backend, job=backend-node | 10.44 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.89 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 28,027.43 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 52.57 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 365,988,864.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 527,702,528.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 149,985,792.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 258,555,392.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,671,360.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,582,488,576.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,936,640.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 79,225.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 86.86 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 96,198.86 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 9.14 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 153,321.14 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 8.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 8.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 37,832,378.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 38,122,112.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.43 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,342,330.29 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 44,849.14 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,671,642.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 3.43 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 10,056.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |



## round10-hot-auction-pattern-20260817.json

- 시나리오: `hot-auction-pattern`
- K6 실행: 2026-08-17T17:22:08Z ~ 2026-08-17T17:30:24Z

### 0-1min — 2026-08-17T17:23:18.000Z ~ 2026-08-17T17:24:18.000Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,280 | 80.90 | 79.54 | 100.09 | 114.36 | 204.59 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 1,324 | 60.76 | 59.80 | 85.68 | 117.83 | 198.82 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,534 | 80.42 | 79.32 | 97.75 | 107.31 | 190.90 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 736 | 62.22 | 62.63 | 85.43 | 89.06 | 97.84 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 53.36 | 54.06 | 60.40 | 61.29 | 57.18 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 162.02 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 80 | 0.17 | 0.52 | 0.98 | 1.99 | 1.82 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 26.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 8.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.08 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 122.50 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 124.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.37 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.40 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,097,280.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,850,176.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,660,224.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,275,904.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 14,442,496.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,679,488.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,435,840.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,097,280.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,850,176.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,660,224.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,424,384.00 |
| `process_rss_max` | instance=backend, job=backend-node | 14,442,496.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,310,272.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,614,016.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 668.00 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 7.50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 8.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 44.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.66 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.08 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 53.94 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 31.87 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 0.74 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.35 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.73 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 4.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 666.67 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 21.33 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 368,328,704.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 529,803,264.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 68,787,200.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 259,007,488.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,673,408.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,293,597,184.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,935,104.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 4.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 564.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 14.67 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 538.67 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 68,058.67 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.75 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 39,135,554.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 39,723,408.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.41 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 997,132.00 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 6,498.67 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,080,157.33 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 608.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### 1-2min — 2026-08-17T17:24:18.000Z ~ 2026-08-17T17:25:18.000Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 1 | 94.86 | 94.74 | 99.47 | 99.89 | 94.86 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | 30,000.00 | 30,000.00 | 30,000.00 | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,242 | 80.47 | 78.96 | 103.65 | 143.97 | 232.08 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 1,357 | 59.89 | 59.46 | 84.28 | 88.79 | 198.82 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 1,800,323.49 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 50.09 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 47.16 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,538 | 80.48 | 79.36 | 99.08 | 123.77 | 190.90 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 694 | 62.59 | 61.98 | 86.15 | 97.09 | 184.96 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 61.09 | 63.38 | 66.74 | 67.03 | 64.35 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 162.02 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 80 | 0.19 | 0.54 | 1.17 | 1.54 | 1.82 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 6.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 26.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 8.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.08 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 122.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 122.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.41 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.43 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,097,280.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,850,176.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,254,720.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,339,392.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 14,442,496.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,738,880.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,473,728.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,097,280.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,850,176.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,320,256.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,600,512.00 |
| `process_rss_max` | instance=backend, job=backend-node | 14,442,496.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 15,986,688.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,589,440.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 1,336.00 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 7.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 8.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 44.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.61 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.09 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 54.31 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 33.20 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.35 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.41 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.86 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 4.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 96.00 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 20.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 361,515,008.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 529,405,952.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 71,152,640.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 257,759,232.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,673,408.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,293,572,608.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,935,104.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 4.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 120.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 22.67 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 736.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 69,442.67 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 2.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 74.67 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 4.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 3.00 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 40,564,444.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 41,026,992.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.38 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,130,978.67 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 5,780.00 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,215,634.67 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 610.67 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### 2-3min — 2026-08-17T17:25:18.000Z ~ 2026-08-17T17:26:18.000Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 94.86 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,250 | 78.50 | 78.53 | 97.94 | 106.54 | 232.08 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 1,346 | 60.59 | 60.49 | 85.06 | 89.47 | 198.82 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 1,800,323.49 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 50.09 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 47.16 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 1 | 23.16 | 25.17 | 27.68 | 27.91 | 23.16 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,584 | 79.02 | 78.47 | 96.60 | 104.27 | 190.90 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 664 | 61.25 | 61.55 | 83.62 | 88.31 | 184.96 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 57.81 | 55.92 | 60.96 | 61.40 | 64.35 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 80 | 0.15 | 0.50 | 0.95 | 0.99 | 1.82 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.00 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 6.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 25.00 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 8.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.08 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 122.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 122.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.40 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.40 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,123,904.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,850,176.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,250,624.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,313,792.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 14,671,872.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,586,304.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,403,072.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,228,352.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,850,176.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,316,160.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,690,624.00 |
| `process_rss_max` | instance=backend, job=backend-node | 14,835,712.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 15,798,272.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,507,520.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 1,336.00 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 7.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 9.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 44.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.64 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.09 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 54.96 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 33.54 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.42 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.14 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.86 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 2.67 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 386.67 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 20.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 361,223,168.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 524,892,160.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 80,626,688.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 253,586,432.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,673,408.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,282,934,272.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,931,008.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 2.67 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 378.67 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 20.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 7,018.67 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 70,112.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 1,025.33 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 5.33 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.50 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 41,829,076.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 42,341,136.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.37 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,142,593.33 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 5,664.00 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,227,409.33 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 610.67 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### 3-4min — 2026-08-17T17:26:18.000Z ~ 2026-08-17T17:27:18.000Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 94.86 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,194 | 77.85 | 78.17 | 97.46 | 104.56 | 232.08 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 1,405 | 59.96 | 59.54 | 85.66 | 95.55 | 198.82 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 1,800,323.49 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 50.09 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 47.16 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 23.16 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,580 | 78.24 | 77.95 | 94.99 | 99.29 | 183.94 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 605 | 60.38 | 60.89 | 82.22 | 88.03 | 184.96 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 57.65 | 57.32 | 66.13 | 66.91 | 64.35 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 80 | 0.12 | 0.50 | 0.95 | 0.99 | 1.82 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.00 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 27.00 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 8.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.07 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 122.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 124.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.39 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.40 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,285,696.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,850,176.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,377,600.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,342,464.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 14,734,336.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,982,592.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,405,120.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,351,232.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,850,176.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,447,232.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,735,680.00 |
| `process_rss_max` | instance=backend, job=backend-node | 14,835,712.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,302,080.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,474,752.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 1,336.00 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 6.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 7.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 44.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.57 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.00 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 54.18 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 33.21 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.56 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.67 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.95 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 1.33 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 42.67 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 16.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 365,915,136.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 526,638,080.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 78,667,776.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 252,908,544.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,673,408.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,274,246,656.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,990,400.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 1.33 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 49.33 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 21.33 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 2,524.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 6.67 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 70,818.67 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.75 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 43,042,108.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 43,554,144.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.35 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,168,684.00 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 5,626.67 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,254,232.00 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 5.33 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 620.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### 4-5min — 2026-08-17T17:27:18.000Z ~ 2026-08-17T17:28:18.000Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,234 | 79.04 | 78.38 | 97.90 | 110.20 | 225.75 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 1,365 | 61.54 | 61.28 | 86.17 | 94.32 | 178.07 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 23.16 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,616 | 79.64 | 78.68 | 96.08 | 102.78 | 176.54 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 612 | 61.28 | 60.94 | 83.97 | 89.04 | 184.96 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 59.80 | 58.72 | 85.00 | 88.58 | 71.50 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 80 | 0.20 | 0.53 | 1.00 | 1.33 | 1.47 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 2.50 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 27.50 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 8.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.10 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 122.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 124.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.40 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.41 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,384,000.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 124,850,176.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,367,360.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,371,136.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 14,700,544.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,225,280.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,566,912.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,482,304.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,850,176.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,439,040.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,412,096.00 |
| `process_rss_max` | instance=backend, job=backend-node | 14,700,544.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,293,888.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,671,360.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 501.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 1,336.00 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 6.50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 8.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 44.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.53 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 46.96 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 55.01 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 35.88 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.50 |
| `node_load1_avg` | instance=backend, job=backend-node | 4.24 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.89 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 322.67 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 649.33 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 367,433,728.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 524,716,032.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 86,377,472.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 256,071,680.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,673,408.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,267,340,800.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,849,088.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 270.67 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 518.67 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 1,520.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 3,232.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 70,602.67 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.75 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 44,194,876.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 44,545,648.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.34 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,160,914.67 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 5,634.67 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,245,878.67 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 124.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 606.67 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |



## round10-bid-only-load-noSSE-20260817.json

- 시나리오: `bid-only-load-distributed`
- K6 실행: 2026-08-17T17:30:57Z ~ 2026-08-17T17:43:21Z

### QPS50 — 2026-08-17T17:31:03.000Z ~ 2026-08-17T17:33:03.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 1,142 | 69.99 | 68.92 | 100.00 | 111.85 | 125.28 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 521,940.18 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,257 | 34.26 | 32.36 | 53.33 | 61.34 | 89.83 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,056 | 56.70 | 56.72 | 83.49 | 88.50 | 98.55 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 1,544 | 44.52 | 43.05 | 60.96 | 82.94 | 116.18 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 661 | 60.57 | 59.93 | 84.18 | 88.57 | 95.50 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 42.44 | 41.94 | 44.46 | 44.68 | 49.90 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 206.33 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.14 | 0.50 | 0.96 | 1.00 | 1.03 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 29.12 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 10.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.12 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.26 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.33 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,363,520.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 122,775,552.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,492,800.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 16,913,920.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 14,726,144.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,980,032.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,586,368.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,691,200.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 124,850,176.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,647,936.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,317,888.00 |
| `process_rss_max` | instance=backend, job=backend-node | 14,827,520.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,449,536.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,892,544.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4.57 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 2.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 3.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.69 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 44.38 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 31.37 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 30.27 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 0.93 |
| `node_load1_avg` | instance=backend, job=backend-node | 0.99 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.75 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 5.71 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 45.71 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 56.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 369,444,352.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 520,534,528.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 97,152,000.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 252,414,464.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,687,232.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,265,853,952.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,435,392.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 98.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 62.86 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 106,934.86 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.38 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 44,203,149.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 44,233,360.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.36 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 576,434.29 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 5,389.71 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 650,370.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 78.86 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 3,517.71 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS100 — 2026-08-17T17:33:03.000Z ~ 2026-08-17T17:35:03.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 1,828 | 96.73 | 95.95 | 130.12 | 134.09 | 189.59 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 521,940.18 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,825 | 48.93 | 50.20 | 62.35 | 66.70 | 100.85 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,140 | 75.98 | 76.30 | 89.44 | 99.14 | 176.62 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 2,339 | 58.05 | 59.51 | 81.70 | 88.18 | 182.04 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,045 | 78.24 | 77.45 | 89.17 | 98.54 | 108.46 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 10 | 61.55 | 62.22 | 79.41 | 87.47 | 67.68 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 4 | 62.11 | 64.31 | 85.00 | 88.58 | 68.03 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 206.33 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.15 | 0.51 | 0.97 | 1.33 | 1.49 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 2.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 27.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 14.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.13 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.30 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.38 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,390,656.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 118,833,152.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,521,472.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,252,352.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 14,794,752.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,897,088.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,528,000.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,822,272.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 120,700,928.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,643,840.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,592,320.00 |
| `process_rss_max` | instance=backend, job=backend-node | 14,823,424.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,310,272.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,798,336.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4.57 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 5.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 9.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.64 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.42 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 37.32 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 29.22 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.08 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.24 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.33 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.81 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 8.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 86.86 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 36.57 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 369,953,792.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 520,212,992.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 83,656,704.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 254,763,008.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,719,488.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,265,889,792.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,431,808.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 8.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 68.57 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 29.71 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 111,949.71 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 297.14 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 2.29 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.62 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.75 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 44,466,152.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 44,712,160.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.37 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 872,995.43 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 8,083.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 981,491.43 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 4,277.71 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS150 — 2026-08-17T17:35:03.000Z ~ 2026-08-17T17:37:03.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 3,082 | 112.67 | 114.73 | 140.05 | 155.72 | 284.66 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,971 | 55.57 | 55.57 | 64.25 | 66.77 | 119.28 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,235 | 85.55 | 82.06 | 98.85 | 108.33 | 304.03 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 3,845 | 65.84 | 64.84 | 87.07 | 93.39 | 289.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,733 | 87.25 | 82.55 | 99.05 | 109.35 | 292.46 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 27 | 67.24 | 68.83 | 87.41 | 89.07 | 74.82 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 10 | 73.34 | 69.91 | 124.15 | 132.20 | 133.06 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.21 | 0.52 | 0.99 | 1.36 | 1.49 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 2.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 27.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 22.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.22 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.41 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.46 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,155,136.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 116,965,376.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,427,776.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,029,120.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 14,807,040.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,929,856.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,509,056.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,318,464.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 116,965,376.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,590,592.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,424,384.00 |
| `process_rss_max` | instance=backend, job=backend-node | 14,807,040.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,404,480.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,765,568.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4.57 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 13.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 17.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.73 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 49.64 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 49.51 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 31.32 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.07 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.51 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.47 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 1.11 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 94.86 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 82.29 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 45.71 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 371,645,952.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 520,176,128.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 87,490,048.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 259,120,128.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,734,848.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,265,894,912.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,432,320.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 8.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 80.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 51.43 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 133,797.71 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 2,844.57 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 11.43 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 6.00 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 45,160,930.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 45,546,528.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.36 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,455,993.14 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 13,577.14 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,633,355.43 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 129.14 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 6,028.57 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS200 — 2026-08-17T17:37:03.000Z ~ 2026-08-17T17:39:03.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 4,130 | 123.76 | 121.52 | 154.80 | 182.80 | 395.69 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,317 | 59.20 | 59.02 | 66.42 | 80.77 | 119.28 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 7,242 | 92.90 | 90.01 | 111.57 | 134.09 | 304.03 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 5,438 | 71.26 | 71.09 | 88.81 | 99.15 | 289.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,344 | 93.22 | 91.04 | 111.03 | 132.35 | 292.46 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 60 | 71.87 | 73.63 | 88.45 | 94.42 | 94.50 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 17 | 71.38 | 66.76 | 92.11 | 98.42 | 133.06 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.30 | 0.56 | 1.35 | 1.72 | 2.79 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.50 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 26.50 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 29.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.28 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.50 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 130.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.51 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.55 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,208,384.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 117,129,216.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,594,688.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,198,592.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 14,773,248.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,996,928.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,523,392.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,314,368.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 118,276,096.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,692,992.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,530,880.00 |
| `process_rss_max` | instance=backend, job=backend-node | 14,905,344.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,384,000.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,712,320.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4.57 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 16.50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 24.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.63 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 50.19 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 60.65 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 31.82 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.05 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.50 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.83 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 1.26 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 6.86 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 56.00 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 42.29 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 369,851,392.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 518,478,848.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 83,623,424.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 259,111,424.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,734,848.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,264,877,056.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,433,856.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 6.86 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 57.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 56.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 843.43 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 1.14 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 156,237.71 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 793.14 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 4.57 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.88 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 6.12 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 46,368,026.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 47,078,688.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.35 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 2,004,644.57 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 18,885.71 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,249,576.00 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 176.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 7,929.14 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS300 — 2026-08-17T17:39:03.000Z ~ 2026-08-17T17:41:03.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 6,114 | 166.87 | 161.96 | 238.38 | 283.37 | 398.55 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,982 | 77.79 | 69.51 | 121.55 | 147.78 | 340.97 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 10,297 | 127.12 | 123.17 | 187.52 | 227.55 | 426.44 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 7,840 | 101.98 | 98.53 | 154.53 | 188.15 | 363.15 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,272 | 124.90 | 121.19 | 184.06 | 219.69 | 400.09 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 144 | 96.78 | 94.30 | 138.13 | 176.05 | 264.57 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 18 | 102.27 | 105.92 | 138.69 | 153.01 | 146.40 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.52 | 0.63 | 2.10 | 3.36 | 3.90 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.62 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 24.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 43.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.47 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.50 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 130.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.67 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.75 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,347,136.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 118,276,096.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,509,696.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,184,256.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 14,617,088.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,873,536.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,448,640.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,384,000.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 118,276,096.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,692,992.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,625,088.00 |
| `process_rss_max` | instance=backend, job=backend-node | 14,770,176.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,273,408.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,585,344.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 5.71 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 34.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.66 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.90 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 79.12 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 34.05 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.01 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.58 |
| `node_load1_avg` | instance=backend, job=backend-node | 4.45 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 1.81 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 805.71 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 80.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 372,009,472.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 517,769,216.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 101,758,464.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 259,235,328.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,734,848.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,252,461,568.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,431,808.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 2.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 498.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 27.43 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 8,280.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 185,728.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 7,510.86 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 18.29 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.88 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 8.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 48,215,690.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 49,193,808.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.33 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 2,800,270.86 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 26,955.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 3,145,092.57 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 195.43 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 10,459.43 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS400 — 2026-08-17T17:41:03.000Z ~ 2026-08-17T17:43:03.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 7,905 | 177.31 | 176.27 | 222.05 | 243.84 | 424.14 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 7,596 | 108.37 | 107.21 | 131.59 | 144.38 | 340.97 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,170 | 155.16 | 152.73 | 178.33 | 197.09 | 426.44 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 11,083 | 124.70 | 124.09 | 151.23 | 161.24 | 363.15 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,362 | 155.31 | 152.58 | 178.82 | 198.64 | 400.09 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 881 | 120.28 | 121.14 | 145.05 | 155.35 | 264.57 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 19 | 119.28 | 121.31 | 137.57 | 152.78 | 146.40 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 114 | 0.67 | 0.68 | 2.45 | 3.50 | 3.90 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.00 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 12.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 22.00 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 50.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.69 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.76 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.78 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,498,688.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 117,411,840.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,384,256.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,101,312.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,183,872.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,020,992.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,524,928.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,498,688.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 118,276,096.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,557,824.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,457,152.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,286,272.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,453,632.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,080,960.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4.57 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 48.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.65 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.50 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 89.44 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 29.67 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.75 |
| `node_load1_avg` | instance=backend, job=backend-node | 6.14 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 1.58 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 739.43 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 22.86 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 371,771,904.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 515,044,352.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 130,503,168.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 260,450,304.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,734,848.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,236,404,224.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,420,032.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 816.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 26.29 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 5.71 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 189,456.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 275.43 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 2.29 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 9.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 50,854,373.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 51,872,320.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.31 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 2,377,996.57 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 35,373.71 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,770,197.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 13,258.29 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |



## round10-bid-only-load-singleHotAuction-20260817.json

- 시나리오: `bid-only-load-hotauction`
- K6 실행: 2026-08-17T17:43:30Z ~ 2026-08-17T17:55:54Z

### QPS50 — 2026-08-17T17:43:36.000Z ~ 2026-08-17T17:45:36.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 1,203 | 76.50 | 74.91 | 106.72 | 146.45 | 3,782.89 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,197 | 35.11 | 33.43 | 49.43 | 54.94 | 338.91 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,096 | 60.65 | 60.02 | 86.88 | 118.88 | 3,734.71 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 1,506 | 47.18 | 45.26 | 69.56 | 85.49 | 3,679.23 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 634 | 61.76 | 60.70 | 85.82 | 89.39 | 1,936.32 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 54 | 60.44 | 59.28 | 79.04 | 94.95 | 1,264.82 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 1,098.36 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 165.53 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.17 | 0.50 | 0.95 | 0.99 | 3.58 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 2.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 28.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 11.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.10 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 134.50 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 142.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.27 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.32 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,298,496.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 116,842,496.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,293,632.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,366,016.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 14,972,928.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,900,672.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,684,672.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,367,616.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 116,940,800.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,471,808.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,555,456.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,122,432.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,396,288.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,831,104.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4.57 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 3.12 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 5.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.69 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 45.35 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 32.58 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 30.08 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.02 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.33 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.41 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 1.62 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 2.29 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 244.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 37.71 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 375,463,936.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 503,539,712.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 68,316,672.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 255,420,928.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,734,848.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,225,727,488.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,263,360.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 2.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 354.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 35.43 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 1,340.57 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 103,013.71 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 102.86 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 52,566,375.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 52,632,544.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.29 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 572,745.14 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 5,989.71 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 647,740.57 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 3,484.57 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS100 — 2026-08-17T17:45:36.000Z ~ 2026-08-17T17:47:36.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 1,768 | 95.02 | 94.48 | 129.18 | 133.43 | 188.53 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,828 | 49.13 | 50.16 | 63.24 | 67.05 | 124.00 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,150 | 75.01 | 76.10 | 88.89 | 97.51 | 173.79 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 2,244 | 57.73 | 58.84 | 81.46 | 88.52 | 187.75 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 558 | 76.51 | 76.65 | 88.34 | 89.38 | 152.64 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 489 | 60.17 | 60.55 | 72.38 | 86.06 | 89.54 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 1,098.36 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 165.53 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.16 | 0.50 | 0.96 | 1.00 | 1.11 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.62 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 28.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 13.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 130.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 132.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.29 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.33 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,136,192.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 117,694,464.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,426,752.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 16,869,376.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,217,152.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,905,792.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,517,760.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,314,368.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 118,906,880.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,443,136.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,453,056.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,405,056.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,371,712.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,786,048.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4.57 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 6.12 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 10.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.71 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.29 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 35.16 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 28.81 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.02 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.34 |
| `node_load1_avg` | instance=backend, job=backend-node | 0.94 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.97 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 81.14 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 13.71 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 373,316,608.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 492,656,128.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 67,491,328.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 255,643,648.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,747,136.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,225,781,248.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,263,360.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 75.43 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 4.57 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 364.57 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 106,523.43 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 219.43 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 2.29 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 6.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 52,701,094.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 52,820,128.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.30 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 555,288.00 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 7,955.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 651,353.14 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 4,149.71 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS150 — 2026-08-17T17:47:36.000Z ~ 2026-08-17T17:49:36.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 2,971 | 108.42 | 110.70 | 132.98 | 151.83 | 278.32 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,025 | 55.91 | 55.44 | 65.06 | 78.64 | 152.23 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,177 | 83.96 | 79.97 | 96.70 | 103.73 | 238.38 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 3,817 | 65.09 | 64.50 | 86.65 | 89.43 | 197.65 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 1 | 0.78 | 0.50 | 0.95 | 0.99 | 0.78 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 656 | 84.75 | 80.16 | 96.86 | 100.77 | 180.96 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,101 | 66.07 | 65.91 | 86.68 | 88.92 | 88.45 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.18 | 0.51 | 0.97 | 1.61 | 2.61 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 28.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 20.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.18 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 130.50 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 132.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.38 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.41 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,269,312.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 118,906,880.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,323,840.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,149,440.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,314,944.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,893,504.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,563,840.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,384,000.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 118,906,880.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,443,136.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,678,336.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,380,480.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,588,800.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,671,360.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4.57 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 9.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 14.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.65 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 49.68 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 46.50 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 27.97 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.03 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.56 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.44 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.70 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 2.29 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 67.43 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 34.29 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 372,860,928.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 495,972,352.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 68,669,952.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 262,673,920.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,759,424.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,225,712,128.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,263,360.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 1.14 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 41.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 32.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 100.57 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 119,810.29 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 137.14 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 2.29 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 6.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 52,804,870.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 52,859,888.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.30 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 731,734.86 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 13,227.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 881,844.57 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 80.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 6,003.43 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS200 — 2026-08-17T17:49:36.000Z ~ 2026-08-17T17:51:36.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 4,277 | 116.91 | 116.12 | 151.31 | 167.08 | 318.20 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,118 | 59.64 | 58.55 | 66.94 | 99.95 | 250.17 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 7,268 | 90.44 | 85.95 | 110.30 | 141.41 | 301.04 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 5,326 | 69.96 | 69.27 | 88.45 | 100.18 | 248.15 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.78 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 835 | 88.92 | 84.36 | 106.23 | 129.28 | 253.34 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,630 | 70.77 | 71.33 | 88.66 | 102.70 | 258.51 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.29 | 0.55 | 1.18 | 1.96 | 2.61 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.00 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 27.00 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 28.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.27 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 131.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 132.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.49 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.52 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,379,904.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 117,684,224.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,480,000.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,401,856.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,489,024.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,039,424.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,624,768.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,379,904.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 118,906,880.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,660,224.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,641,472.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,642,624.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,338,944.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,917,120.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 4.57 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 17.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 23.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.70 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 50.19 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 59.25 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 29.34 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.06 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.53 |
| `node_load1_avg` | instance=backend, job=backend-node | 3.74 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.55 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 216.00 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 70.86 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 373,521,408.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 505,791,488.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 75,507,712.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 259,058,176.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,759,424.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,225,251,328.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,343,232.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 2.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 43.43 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 38.86 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 2,913.14 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 135,235.43 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 5,726.86 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 219.43 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 7.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 52,674,125.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 52,764,688.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.31 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 973,018.29 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 19,056.00 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,180,876.57 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 692.57 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 7,746.29 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS300 — 2026-08-17T17:51:36.000Z ~ 2026-08-17T17:53:36.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 5,954 | 143.33 | 141.23 | 193.94 | 218.19 | 321.47 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 6,044 | 64.48 | 60.32 | 89.27 | 119.42 | 250.17 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 10,380 | 106.59 | 99.45 | 153.70 | 176.90 | 301.04 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 7,612 | 84.97 | 81.49 | 127.27 | 141.79 | 248.15 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 820 | 101.80 | 93.10 | 149.04 | 170.68 | 253.34 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,673 | 83.46 | 80.04 | 124.52 | 133.16 | 258.51 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.40 | 0.56 | 1.92 | 2.75 | 3.20 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.62 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 25.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 37.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.38 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 130.50 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 132.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.61 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.67 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,391,680.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 117,276,672.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,518,400.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,277,952.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,264,768.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,959,040.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,573,056.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,510,976.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 117,276,672.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,660,224.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,690,624.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,335,424.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,474,112.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,683,648.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 5.71 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 27.50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 43.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.64 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 49.82 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 72.83 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 27.44 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.02 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.65 |
| `node_load1_avg` | instance=backend, job=backend-node | 4.88 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.58 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 2.29 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 26.29 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 16.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 375,294,464.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 513,682,944.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 79,464,960.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 260,485,120.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,759,424.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,224,906,240.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,423,104.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 1.14 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 33.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 12.57 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 1,766.86 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 155,310.86 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 100.57 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 7.88 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 52,784,456.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 52,895,584.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.32 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,137,411.43 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 26,548.57 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,419,998.86 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 193.14 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 10,474.29 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS400 — 2026-08-17T17:53:36.000Z ~ 2026-08-17T17:55:36.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/admin/users | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions | 8,137 | 166.42 | 165.88 | 215.72 | 238.26 | 413.95 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | 30,000.00 | 30,000.00 | 30,000.00 | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 8,009 | 96.58 | 96.84 | 123.37 | 133.20 | 282.02 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 13,514 | 140.90 | 142.21 | 172.95 | 185.06 | 342.97 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 10,750 | 113.91 | 114.84 | 142.04 | 155.45 | 304.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/me/stream | 2 | 1,800,347.58 | 30,000.00 | 30,000.00 | 30,000.00 | 1,800,347.77 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/notifications/unread-count | 1 | 88.02 | 78.29 | 88.36 | 89.25 | 88.02 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=200, uri=/api/wallet | 1 | 121.59 | 123.03 | 133.10 | 133.99 | 121.59 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 358 | 135.31 | 138.13 | 171.48 | 185.37 | 319.96 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 3,804 | 108.58 | 108.64 | 133.01 | 150.80 | 275.74 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 114.56 | 123.03 | 133.10 | 133.99 | 116.06 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0.00 |
| method=POST, status=403, uri=UNKNOWN | 146 | 0.50 | 0.61 | 2.28 | 3.27 | 3.42 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 6.50 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 9.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 23.50 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 48.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 130.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 132.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.75 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.78 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 16,343,040.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 117,276,672.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 20,582,912.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,583,104.00 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,320,064.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,805,952.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,568,448.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 16,343,040.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 117,276,672.00 |
| `process_rss_max` | instance=redis, job=redis-node | 20,770,816.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,752,064.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,323,136.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,322,560.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 22,691,840.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=me | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `me_sse_send_count_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 5.71 |
| `auction_sse_send_failures_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 47.38 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis/monitoring)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.63 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.22 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 88.74 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 24.82 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.75 |
| `node_load1_avg` | instance=backend, job=backend-node | 5.91 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.72 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 4.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 657.14 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 22.86 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 372,548,608.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 512,268,800.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 105,290,240.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 255,450,112.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 2,870,759,424.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,224,699,392.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,565,423,104.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 3.43 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 613.71 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 21.71 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 2,565.71 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 170,041.14 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 62.86 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.38 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 10.00 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 52,812,784.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 52,906,336.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.32 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,017,432.00 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 36,201.14 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,376,186.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 150.86 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 13,626.29 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |



> 이 문서는 Claude Code의 도움을 받아 작성하였습니다
