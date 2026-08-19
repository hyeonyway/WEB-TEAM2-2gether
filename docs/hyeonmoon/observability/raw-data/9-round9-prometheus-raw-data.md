# 9차 부하테스트 — Prometheus 원시 집계 데이터

이 문서는 K6 결과 JSON의 실제 종료 시각과 실행 시간을 기준으로 stage를 재구성하고, 각 구간 끝 시점에 Prometheus range/vector query를 평가해 만든 원시 집계표다. p50/p95/p99는 서버의 `http_server_requests_seconds_bucket` histogram으로 계산했다. 값은 Prometheus 원 단위(시간은 ms 변환)를 유지하며, `N/A`/빈 표는 그 시간대에 해당 시계열이 없었음을 뜻한다.

수집 범위는 테스트 대상 백엔드, backend/mysql/redis node exporter, MySQL exporter, Redis exporter다. 8차와 달리 이번 회차는 Redis exporter 데이터도 실제로 수집됐다 (8차 시점엔 관련 시계열이 없었음). Grafana/Prometheus 자기 관측 메트릭과 정적 build/info/config 시계열은 성능 측정값이 아니므로 제외했다.

## 실행 목록

| 결과 파일 | 시나리오 | 실제 실행 (UTC) | K6 전체 | 평균 요청률 | avg | med | p95 | p99 | max |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| [`round9-pure-throughput-sse250-20260816.json`](../../../../backend/src/test/k6/result/round9-pure-throughput-sse250-20260816.json) | pure-throughput | 2026-08-16T04:31:26.000Z ~ 2026-08-16T04:45:09.000Z | 133,935 | 162.65 req/s | 3,175.77 | 382.25 | 8,527.04 | 17,522.14 | 60,003.15 |
| [`round9-pure-throughput-sse500-20260816.json`](../../../../backend/src/test/k6/result/round9-pure-throughput-sse500-20260816.json) | pure-throughput | 2026-08-16T04:45:10.000Z ~ 2026-08-16T04:58:56.000Z | 135,111 | 163.67 req/s | 3,304.58 | 580.48 | 10,008.33 | 20,227.83 | 59,999.20 |
| [`round9-pure-throughput-sse1000-20260816.json`](../../../../backend/src/test/k6/result/round9-pure-throughput-sse1000-20260816.json) | pure-throughput | 2026-08-16T04:58:57.000Z ~ 2026-08-16T05:12:46.000Z | 134,567 | 162.20 req/s | 3,136.12 | 1,865.84 | 10,011.38 | 29,627.94 | 60,056.83 |
| [`round9-hot-auction-pattern-20260816.json`](../../../../backend/src/test/k6/result/round9-hot-auction-pattern-20260816.json) | hot-auction-pattern | 2026-08-16T05:12:48.000Z ~ 2026-08-16T05:20:54.000Z | 123,943 | 254.88 req/s | 1,875.97 | 261.93 | 15,707.34 | 30,001.55 | 165,567.66 |
| [`round9-bid-only-load-noSSE-20260816.json`](../../../../backend/src/test/k6/result/round9-bid-only-load-noSSE-20260816.json) | bid-only-load (SSE 없음, 분산) | 2026-08-16T05:20:57.000Z ~ 2026-08-16T05:33:17.000Z | 132,758 | 179.44 req/s | 2,785.93 | 306.04 | 9,773.91 | 19,976.15 | 20,357.28 |
| [`round9-bid-only-load-singleHotAuction-20260816.json`](../../../../backend/src/test/k6/result/round9-bid-only-load-singleHotAuction-20260816.json) | bid-only-load (SSE 없음, 핫경매집중) | 2026-08-16T05:33:18.000Z ~ 2026-08-16T05:45:34.000Z | 141,632 | 192.38 req/s | 1,601.62 | 140.70 | 8,584.21 | 9,296.66 | 9,673.72 |

---

## round9-pure-throughput-sse250-20260816.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-16T04:31:26.000Z ~ 2026-08-16T04:45:09.000Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-16T04:32:30.000Z ~ 2026-08-16T04:34:30.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 1,233 | 162.83 | 155.19 | 221.61 | 243.83 | 280.00 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,209 | 52.05 | 52.13 | 82.98 | 88.72 | 109.67 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,669 | 66.29 | 64.92 | 89.40 | 99.21 | 143.31 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 274.40 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,217 | 59.73 | 58.16 | 85.15 | 89.05 | 114.19 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.18 | 0.51 | 0.97 | 1.50 | 2.49 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 44.10 | 43.34 | 49.63 | 50.19 | 49.09 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.12 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 29.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 16.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 4.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.13 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.03 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 105.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 105.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.21 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.22 |
| `process_rss_avg` | instance=backend, job=backend-node | 17,139,200.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,443,328.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 148,678,656.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,666,112.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 23,144,960.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,182,208.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,816,768.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,498,112.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,461,248.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 156,725,248.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,969,728.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,605,248.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,715,200.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,892,544.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 4.12 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 9.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 26.31 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.36 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 10.84 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 42.18 |
| `node_load1_avg` | instance=backend, job=backend-node | 0.54 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.03 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.07 |
| `node_load1_avg` | instance=redis, job=redis-node | 0.89 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 4.57 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 89.14 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 112.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 390,173,184.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 287,697,920.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 258,860,544.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 565,054,464.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,878,033,920.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,073,585,152.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,655,195,136.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1.14 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 26.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 106.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 19.43 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 48,270.86 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.75 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.25 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 21,783,258.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 22,142,360.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.82 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 945,148.57 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 12,828.57 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,035,386.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,235.43 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 100 — 2026-08-16T04:34:30.000Z ~ 2026-08-16T04:36:30.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 1 | 99.37 | 95.43 | 124.61 | 132.30 | 99.37 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 1 | 80.54 | 79.77 | 106.76 | 110.83 | 80.54 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 6 | 75.95 | 81.09 | 97.37 | 99.47 | 94.32 |
| method=GET, status=200, uri=/api/auctions | 2,127 | 289.22 | 288.00 | 351.35 | 413.49 | 487.06 |
| method=GET, status=200, uri=/api/auctions/stream | 1 | 1,597,658.97 | 30,000.00 | 30,000.00 | 30,000.00 | 1,597,658.97 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,057 | 73.12 | 76.58 | 88.33 | 89.38 | 109.67 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,286 | 93.22 | 93.38 | 109.41 | 129.98 | 228.39 |
| method=GET, status=200, uri=/api/auth/csrf | 1 | 43.77 | 41.94 | 44.46 | 44.68 | 43.77 |
| method=GET, status=200, uri=/api/auth/me | 1 | 43.47 | 42.31 | 47.93 | 49.85 | 43.47 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 87.16 |
| method=GET, status=200, uri=/api/notifications/unread-count | 2 | 53.87 | 53.50 | 59.12 | 61.04 | 54.51 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 1 | 82.26 | 78.29 | 88.36 | 89.25 | 82.26 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 1 | 0.25 | 0.57 | 1.25 | 1.37 | 0.25 |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 1 | 0.36 | 0.57 | 7.79 | 8.27 | 0.36 |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 1 | 4.27 | 4.89 | 5.52 | 5.58 | 4.27 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 1 | 0.48 | 0.57 | 2.30 | 2.42 | 0.48 |
| method=OPTIONS, status=200, uri=/api/auth/me | 1 | 0.42 | 0.50 | 0.95 | 0.99 | 0.42 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.91 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 1 | 0.49 | 0.57 | 1.25 | 1.37 | 0.49 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 1 | 2.73 | 2.62 | 2.78 | 2.79 | 2.73 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,067 | 80.86 | 78.79 | 93.85 | 105.85 | 159.80 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 18 | 62.17 | 61.52 | 80.53 | 87.69 | 84.51 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.27 | 0.53 | 1.00 | 2.66 | 4.53 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 61.28 | 59.84 | 66.13 | 66.91 | 66.94 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.50 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 2.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 28.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 27.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.27 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.03 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 106.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 111.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.32 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.40 |
| `process_rss_avg` | instance=backend, job=backend-node | 17,059,840.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,280,000.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 153,079,808.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,517,120.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 23,101,440.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,283,072.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,774,272.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,059,840.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,317,888.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 153,440,256.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,859,136.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,375,872.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,764,352.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,892,544.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 251.25 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.25 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 252.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 13.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 23.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 31.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.00 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 15.76 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.92 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 36.88 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.54 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.08 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.26 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.40 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 1.14 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 28.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 128.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 393,057,792.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 246,339,072.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 260,123,136.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 563,558,400.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,878,033,920.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,073,588,224.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,655,195,136.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1.14 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 8.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 123.43 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 78,466.29 |
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
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.12 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 22,810,861.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 23,334,568.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.77 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,598,665.14 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 21,362.29 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,752,504.00 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 2,032.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 150 — 2026-08-16T04:36:30.000Z ~ 2026-08-16T04:38:30.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 5 | 90.12 | 83.99 | 109.48 | 111.37 | 104.25 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 80.54 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 8 | 80.49 | 80.16 | 107.70 | 111.02 | 102.55 |
| method=GET, status=200, uri=/api/auctions | 3,351 | 325.20 | 330.47 | 402.60 | 475.27 | 526.59 |
| method=GET, status=200, uri=/api/auctions/stream | 1 | 69,158.47 | 30,000.00 | 30,000.00 | 30,000.00 | 1,597,658.97 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,215 | 81.21 | 79.23 | 95.29 | 121.77 | 206.30 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,877 | 103.49 | 100.66 | 129.34 | 164.83 | 238.42 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 43.77 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 43.47 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 1 | 1,726,735.21 | 30,000.00 | 30,000.00 | 30,000.00 | 1,726,735.21 |
| method=GET, status=200, uri=/api/me/wallet/stream | 1 | 1,718,708.09 | 30,000.00 | 30,000.00 | 30,000.00 | 1,718,708.09 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 87.16 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 54.51 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 82.26 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 0.25 |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | 0.36 |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 4.27 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.48 |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 0.42 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.91 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.49 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 2.73 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,218 | 90.30 | 84.69 | 108.73 | 141.53 | 216.04 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 56 | 69.06 | 66.77 | 92.37 | 123.26 | 114.12 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.26 | 0.53 | 1.00 | 1.96 | 6.74 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 14 | 68.77 | 70.30 | 87.56 | 89.10 | 80.66 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 26.62 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 41.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 5.71 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.50 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.05 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 113.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.46 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.52 |
| `process_rss_avg` | instance=backend, job=backend-node | 17,059,840.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,162,240.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 152,630,272.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,645,120.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,974,976.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,005,568.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,718,464.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,059,840.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,276,928.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 158,834,688.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 17,170,432.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,343,104.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,309,696.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,888,448.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 250.25 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 251.50 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.50 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 252.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 21.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 37.38 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 54.77 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.82 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 21.49 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 49.13 |
| `node_load1_avg` | instance=backend, job=backend-node | 3.19 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.09 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.68 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.57 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 1.14 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 56.00 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 147.43 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 388,733,952.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 245,763,072.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 266,819,584.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 562,249,728.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,879,579,136.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,073,272,832.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,655,199,232.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1.14 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 20.57 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 194.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 90.29 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 115,190.86 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 195.43 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 3.43 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.75 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 24,408,588.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 25,287,480.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.72 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 2,498,065.14 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 31,418.29 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,737,733.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 202.29 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 2,145.14 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 200 — 2026-08-16T04:38:30.000Z ~ 2026-08-16T04:40:30.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | 104.25 |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 102.55 |
| method=GET, status=200, uri=/api/auctions | 4,533 | 395.69 | 389.85 | 499.74 | 541.31 | 779.35 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 69,158.47 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,474 | 100.31 | 91.23 | 146.30 | 171.32 | 360.44 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 13,521 | 127.28 | 121.99 | 175.18 | 199.97 | 393.70 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | 1,726,735.21 |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | 1,718,708.09 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 87.16 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0.91 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,280 | 110.39 | 104.79 | 152.77 | 174.18 | 366.68 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 192 | 89.82 | 88.80 | 127.99 | 137.80 | 147.32 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.43 | 0.61 | 1.60 | 2.38 | 6.74 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 22 | 81.59 | 79.69 | 112.97 | 129.97 | 113.97 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 25.12 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 56.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.60 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.03 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.59 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.66 |
| `process_rss_avg` | instance=backend, job=backend-node | 17,191,936.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,276,928.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 149,048,832.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,372,224.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 23,158,784.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,455,616.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,618,112.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,412,096.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,276,928.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 149,938,176.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,900,096.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,465,984.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,797,120.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,618,112.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 250.62 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 36.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.89 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 68.84 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.52 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 24.67 |
| `node_load1_avg` | instance=backend, job=backend-node | 4.74 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.09 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 1.13 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.66 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 2.29 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 642.29 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 169.14 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 388,838,912.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 252,590,080.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 272,781,312.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 560,547,328.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,880,094,208.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,070,297,600.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,655,199,744.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1.14 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 68.57 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 194.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 115.43 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 21.71 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 145,112.00 |
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
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 27,093,372.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 28,645,240.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.63 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 3,340,702.86 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 40,048.00 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 3,659,541.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 34.29 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,829.71 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 300 — 2026-08-16T04:40:30.000Z ~ 2026-08-16T04:42:30.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 5,946 | 414.77 | 410.25 | 499.81 | 584.38 | 779.35 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,599 | 109.89 | 107.39 | 143.64 | 166.01 | 360.44 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 17,136 | 136.81 | 133.57 | 174.71 | 199.43 | 393.70 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 1 | 0.89 | 0.50 | 0.95 | 0.99 | 0.89 |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,350 | 123.66 | 122.49 | 156.07 | 178.79 | 366.68 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,839 | 92.06 | 89.52 | 123.24 | 139.24 | 264.43 |
| method=POST, status=403, uri=UNKNOWN | 91 | 0.33 | 0.56 | 1.75 | 2.52 | 2.67 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 26 | 90.84 | 90.23 | 111.40 | 129.07 | 115.43 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 7.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 24.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 66.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.87 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.04 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.64 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.67 |
| `process_rss_avg` | instance=backend, job=backend-node | 17,188,352.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,402,880.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 148,580,352.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,430,592.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 23,012,864.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,108,480.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,618,112.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,412,096.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,539,072.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 155,578,368.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,797,696.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,166,976.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,420,288.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,618,112.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 22.50 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.05 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 77.67 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.43 |
| `node_load1_avg` | instance=backend, job=backend-node | 8.45 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.03 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 1.05 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.56 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 14.86 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 84.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 152.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 373,641,728.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 249,277,440.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 271,686,656.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 554,732,032.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,880,094,208.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,067,190,272.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,655,205,376.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 5.71 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 27.43 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 171.43 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 6.86 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 141,348.57 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 1,459.43 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 7,378.29 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.00 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 30,006,026.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 30,555,864.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.55 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 2,186,473.14 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 47,748.57 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,508,820.57 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,750.86 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 400 — 2026-08-16T04:42:30.000Z ~ 2026-08-16T04:44:30.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 5,328 | 390.87 | 399.28 | 474.81 | 521.54 | 694.97 |
| method=GET, status=200, uri=/api/auctions/stream | 1 | 236,417.98 | 30,000.00 | 30,000.00 | 30,000.00 | 236,417.98 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,315 | 102.95 | 100.00 | 133.53 | 155.34 | 282.56 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 16,653 | 128.81 | 126.45 | 164.50 | 190.91 | 322.78 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.89 |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,176 | 119.38 | 117.98 | 153.41 | 175.27 | 230.83 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 4,687 | 89.74 | 86.65 | 120.79 | 133.90 | 264.43 |
| method=POST, status=403, uri=UNKNOWN | 55 | 0.37 | 0.57 | 1.50 | 4.03 | 3.90 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 19 | 95.57 | 92.11 | 159.94 | 175.15 | 175.15 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.62 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 10.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 24.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 74.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.96 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.01 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.62 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.59 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.64 |
| `process_rss_avg` | instance=backend, job=backend-node | 17,096,704.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,227,776.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 155,348,992.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,730,624.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,961,664.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,081,856.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,700,032.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,113,088.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,473,536.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 158,593,024.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 17,088,512.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,130,112.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,395,712.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,749,184.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 250.25 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 251.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 251.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 43.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.71 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 74.03 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.35 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 17.89 |
| `node_load1_avg` | instance=backend, job=backend-node | 6.32 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.71 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.65 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 0.00 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 85.71 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 341.71 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 355,217,920.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 253,185,024.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 266,613,760.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 554,048,000.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,880,095,744.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,068,600,320.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,653,619,200.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 18.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 69.71 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 885.71 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 130,976.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 30,823,978.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 31,005,656.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.54 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,357,996.57 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 42,408.00 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,646,888.00 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 2,137.14 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


## round9-pure-throughput-sse500-20260816.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-16T04:45:10.000Z ~ 2026-08-16T04:58:56.000Z
- 설정: `{"sseVUs":500,"totalSseConnections":1000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-16T04:46:16.000Z ~ 2026-08-16T04:48:16.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 1,198 | 175.70 | 162.59 | 269.70 | 320.39 | 499.51 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 845,406.03 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,239 | 50.60 | 47.23 | 84.74 | 88.53 | 202.14 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,659 | 67.72 | 64.57 | 96.19 | 105.68 | 184.94 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 180.59 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,216 | 59.54 | 56.45 | 85.37 | 88.92 | 130.98 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 143.29 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.17 | 0.52 | 0.99 | 1.28 | 1.22 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 48.92 | 51.73 | 55.50 | 55.84 | 97.48 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.12 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 29.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 16.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 8.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.06 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.22 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.23 |
| `process_rss_avg` | instance=backend, job=backend-node | 17,178,624.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,473,536.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 152,269,312.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,758,272.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,972,416.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,115,136.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,716,416.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,375,232.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,473,536.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 154,939,392.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 17,285,120.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,392,256.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,575,936.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,732,800.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 500.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 500.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 4.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 9.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 28.51 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.47 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 11.79 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 45.09 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.53 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.41 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.27 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 147.43 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 193.14 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 206.86 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 347,718,144.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 247,901,184.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 270,280,704.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 557,384,704.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,879,884,288.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,067,987,456.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,653,089,792.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 8.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 35.43 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 125.71 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 16.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 101.71 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 48,233.14 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 29.71 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 6.00 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.38 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 31,423,389.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 31,708,544.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.55 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 938,545.14 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 18,435.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,034,690.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,237.71 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 100 — 2026-08-16T04:48:16.000Z ~ 2026-08-16T04:50:16.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 2,102 | 292.06 | 291.04 | 353.48 | 409.12 | 414.65 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,057 | 74.96 | 77.11 | 88.76 | 100.00 | 153.26 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,247 | 94.94 | 94.51 | 110.95 | 133.95 | 192.18 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,048 | 81.79 | 79.18 | 95.83 | 111.96 | 150.77 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 27 | 62.47 | 62.91 | 87.24 | 97.47 | 94.14 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.28 | 0.53 | 1.17 | 1.71 | 2.55 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 60.91 | 58.72 | 86.68 | 88.92 | 68.23 |

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
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 27.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.03 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.33 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.40 |
| `process_rss_avg` | instance=backend, job=backend-node | 17,367,040.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,473,536.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 150,960,128.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,157,696.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 23,065,088.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,107,456.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,772,224.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,367,040.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,473,536.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 152,137,728.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,732,160.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,171,072.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,424,384.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,863,872.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 500.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 500.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 13.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 22.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 39.27 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.40 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 15.89 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.09 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.31 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.27 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.36 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 3.43 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 75.43 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 166.86 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 332,304,384.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 254,334,464.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 273,479,168.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 552,865,792.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,879,867,904.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,068,769,280.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,653,089,792.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 2.29 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 18.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 177.14 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 77,600.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.12 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 32,478,767.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 33,442,200.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.54 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,572,330.29 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 32,267.43 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,736,768.00 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 2,002.29 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 150 — 2026-08-16T04:50:16.000Z ~ 2026-08-16T04:52:16.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 3,269 | 347.20 | 337.27 | 451.57 | 499.33 | 712.76 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,272 | 85.93 | 81.29 | 110.23 | 137.81 | 299.62 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,845 | 110.12 | 106.24 | 147.54 | 176.25 | 362.41 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,202 | 95.79 | 92.39 | 127.63 | 152.39 | 294.89 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 67 | 71.35 | 73.88 | 90.00 | 108.35 | 111.13 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.41 | 0.55 | 1.66 | 5.03 | 8.90 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 68.79 | 67.11 | 87.24 | 89.03 | 85.70 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 26.62 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 41.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 5.71 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.51 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.06 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.62 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.49 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.59 |
| `process_rss_avg` | instance=backend, job=backend-node | 17,244,672.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,320,960.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 150,201,344.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,553,984.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,982,656.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,036,800.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,466,048.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,346,560.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,473,536.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 153,550,848.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,945,152.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,166,976.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,354,752.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,568,960.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 500.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 500.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 22.38 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 43.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 21.29 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.74 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 59.56 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.37 |
| `node_load1_avg` | instance=backend, job=backend-node | 3.99 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.49 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.53 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 166.86 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 933.71 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 112.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 349,526,016.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 250,765,824.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 274,463,232.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 553,793,536.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,878,517,760.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,062,291,968.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,653,089,792.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 51.43 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 529.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 120.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 549.71 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 465.14 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 112,742.86 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 148.57 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 3.43 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.00 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 34,547,164.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 35,592,264.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.50 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 2,461,963.43 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 50,185.14 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,719,445.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 132.57 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,985.14 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 200 — 2026-08-16T04:52:16.000Z ~ 2026-08-16T04:54:16.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 4,448 | 432.69 | 427.63 | 547.61 | 610.93 | 712.76 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,507 | 109.76 | 107.26 | 152.75 | 176.00 | 299.62 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 13,457 | 139.37 | 137.78 | 188.98 | 216.85 | 362.41 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,205 | 121.23 | 119.39 | 165.38 | 191.33 | 294.89 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 251 | 94.91 | 93.83 | 130.15 | 155.69 | 186.70 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.52 | 0.58 | 2.33 | 3.77 | 8.90 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 11 | 85.61 | 83.09 | 123.03 | 131.98 | 116.37 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 7.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 25.12 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 58.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 5.71 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.66 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.06 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.12 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.63 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.67 |
| `process_rss_avg` | instance=backend, job=backend-node | 17,124,352.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,219,584.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 151,761,408.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,443,904.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 23,029,760.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,189,376.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,617,600.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,522,688.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,334,272.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 155,295,744.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,945,152.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,408,640.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,379,328.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,818,816.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 500.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 500.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 41.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.71 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 76.04 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.37 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 24.17 |
| `node_load1_avg` | instance=backend, job=backend-node | 5.07 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.56 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.55 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 6.86 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 406.86 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 113.14 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 328,822,272.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 254,033,408.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 271,600,640.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 550,182,912.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,876,252,160.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,047,715,840.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,653,145,088.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1.14 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 61.71 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 70.86 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 48.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 141,187.43 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.88 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 37,192,248.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 38,562,280.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.45 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 3,261,739.43 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 69,621.71 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 3,607,404.57 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 121.14 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,598.86 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 300 — 2026-08-16T04:54:16.000Z ~ 2026-08-16T04:56:16.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 1 | 125.95 | 123.03 | 133.10 | 133.99 | 125.95 |
| method=GET, status=200, uri=/api/auctions | 5,880 | 420.85 | 413.59 | 507.33 | 575.94 | 633.00 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,519 | 111.42 | 109.20 | 146.28 | 168.18 | 299.62 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 16,864 | 138.94 | 136.97 | 177.03 | 200.95 | 322.13 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 1 | 71.96 | 78.29 | 88.36 | 89.25 | 71.96 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 1 | 103.80 | 105.92 | 111.26 | 111.73 | 103.80 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 1 | 1.76 | 1.92 | 2.08 | 2.09 | 1.76 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,101 | 127.09 | 124.92 | 164.38 | 191.95 | 289.94 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,998 | 92.77 | 90.81 | 123.89 | 137.59 | 234.44 |
| method=POST, status=403, uri=UNKNOWN | 82 | 0.44 | 0.60 | 2.17 | 2.67 | 5.15 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 18 | 97.44 | 95.79 | 128.25 | 133.02 | 129.15 |

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
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 70.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.96 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.01 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.64 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.65 |
| `process_rss_avg` | instance=backend, job=backend-node | 17,002,496.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,432,576.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 154,137,088.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,476,672.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,926,848.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,116,672.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,731,776.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,104,896.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,465,344.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 160,813,056.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,752,640.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,048,192.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,866,752.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,806,528.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 500.25 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 46.61 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 79.03 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.58 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 20.84 |
| `node_load1_avg` | instance=backend, job=backend-node | 5.90 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.04 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.39 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.65 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 9.14 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 60.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 62.86 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 334,001,664.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 256,668,160.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 272,920,576.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 547,934,720.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,876,229,120.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,053,899,776.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,653,163,520.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 8.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 20.57 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 45.71 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 94.86 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 135,243.43 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 39,737,500.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 40,198,376.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.41 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,961,912.00 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 83,905.14 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,310,945.14 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,681.14 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 400 — 2026-08-16T04:56:16.000Z ~ 2026-08-16T04:58:16.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | 125.95 |
| method=GET, status=200, uri=/api/auctions | 5,415 | 401.08 | 403.57 | 495.96 | 575.28 | 672.60 |
| method=GET, status=200, uri=/api/auctions/stream | 1 | 51,060.47 | 30,000.00 | 30,000.00 | 30,000.00 | 51,060.47 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,353 | 106.29 | 103.60 | 143.78 | 167.32 | 271.50 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 15,731 | 132.82 | 129.03 | 175.81 | 201.95 | 353.20 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 71.96 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 103.80 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 1.76 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,355 | 130.24 | 123.51 | 195.17 | 268.80 | 297.87 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 3,973 | 95.15 | 89.02 | 142.12 | 197.90 | 251.06 |
| method=POST, status=403, uri=UNKNOWN | 64 | 0.35 | 0.55 | 1.82 | 2.35 | 2.55 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 13 | 105.98 | 98.25 | 150.44 | 155.36 | 154.78 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 25.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 73.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 4.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 1.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.05 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 125.12 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.59 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.65 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,826,368.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,465,344.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 157,121,536.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,186,368.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 23,092,736.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 16,945,152.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,720,512.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,941,056.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,465,344.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 161,222,656.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,687,104.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,355,392.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,555,456.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,769,664.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 500.88 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 43.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 46.97 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 76.41 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.04 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 17.49 |
| `node_load1_avg` | instance=backend, job=backend-node | 4.51 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.18 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.80 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 464.00 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 156.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 317.71 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 268,043,776.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 247,793,664.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 274,690,048.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 545,733,120.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,869,769,728.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,055,064,576.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,652,371,456.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 546.29 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 18.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 20.57 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 1,677.71 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 2,009.14 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 126,228.57 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 34.29 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.00 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 40,599,432.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 40,914,008.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.40 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,431,684.57 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 76,473.14 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,750,485.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 2,017.14 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


## round9-pure-throughput-sse1000-20260816.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-16T04:58:57.000Z ~ 2026-08-16T05:12:46.000Z
- 설정: `{"sseVUs":1000,"totalSseConnections":2000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-16T05:00:08.000Z ~ 2026-08-16T05:02:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 1,144 | 188.27 | 186.86 | 261.13 | 284.95 | 672.60 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 867,335.72 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,279 | 51.19 | 50.99 | 77.09 | 87.97 | 254.25 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,605 | 70.14 | 70.15 | 93.35 | 101.58 | 353.20 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 30 | 37.94 | 37.20 | 53.50 | 55.44 | 156.72 |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | 35.70 |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 188.83 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,194 | 61.61 | 61.22 | 85.53 | 89.02 | 297.87 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 55.73 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.19 | 0.52 | 0.99 | 1.68 | 3.24 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 45.31 | 44.74 | 55.36 | 55.81 | 53.28 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.62 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 29.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 14.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 4.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.17 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.05 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.26 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.29 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,757,760.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,347,072.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 153,631,744.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,595,456.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,944,256.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,012,224.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,999,040.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,941,056.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,465,344.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 173,096,960.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,818,176.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,142,400.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,354,752.00 |
| `process_rss_max` | instance=redis, job=redis-node | 23,031,808.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 4.62 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 7.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 44.66 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 35.02 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 3.08 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 12.88 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.05 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.07 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.40 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.32 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 2,050.29 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 284.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 72.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 177,299,456.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 242,773,504.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 269,926,400.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 543,902,720.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,770,535,936.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,053,557,760.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,832,128.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 3,461.71 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 136.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 28.57 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 3,256.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 202.29 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 48,521.14 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 33.14 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.00 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.38 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 42,064,241.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 42,399,120.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.41 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 924,099.43 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 19,328.00 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,020,036.57 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,216.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 100 — 2026-08-16T05:02:08.000Z ~ 2026-08-16T05:04:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 2,057 | 295.07 | 301.04 | 353.87 | 389.87 | 511.25 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,998 | 74.30 | 76.69 | 88.82 | 96.90 | 238.82 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,080 | 95.30 | 95.13 | 111.77 | 131.00 | 322.88 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 1 | 12.04 | 11.88 | 12.51 | 12.57 | 12.04 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 156.72 |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | 35.70 |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,009 | 82.65 | 80.25 | 98.87 | 109.93 | 265.05 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 16 | 62.16 | 63.91 | 84.26 | 88.43 | 69.91 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.28 | 0.53 | 1.00 | 2.73 | 3.29 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 61.79 | 64.31 | 66.83 | 67.05 | 61.79 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 28.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 21.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.27 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.03 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.37 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.45 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,521,728.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,412,096.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 152,965,120.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,727,552.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,929,408.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,307,648.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,912,000.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,539,648.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,461,248.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 152,981,504.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,998,400.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,052,288.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,575,936.00 |
| `process_rss_max` | instance=redis, job=redis-node | 23,031,808.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 10.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 19.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 15.50 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.58 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 45.97 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.67 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.77 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.04 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.39 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.56 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 210.29 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 122.29 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 65.14 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 187,336,192.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 244,700,672.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 266,629,120.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 543,718,912.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,760,196,096.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,051,831,296.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,847,488.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 213.71 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 58.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 81.14 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 510.86 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 80,520.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.00 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.62 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.50 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 43,002,138.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 43,647,328.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.40 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,538,294.86 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 30,624.00 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,697,797.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,916.57 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 150 — 2026-08-16T05:04:08.000Z ~ 2026-08-16T05:06:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 3,240 | 381.00 | 362.05 | 534.01 | 607.56 | 624.71 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,201 | 88.40 | 83.25 | 119.36 | 149.30 | 224.65 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,675 | 117.85 | 110.98 | 169.42 | 201.82 | 313.31 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 12.04 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,136 | 102.71 | 97.18 | 147.95 | 178.78 | 238.17 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 79 | 74.56 | 76.01 | 97.46 | 107.76 | 110.13 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.46 | 0.59 | 2.10 | 3.45 | 3.66 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 76.57 | 75.50 | 126.39 | 132.65 | 115.58 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.12 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 26.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 37.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.52 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.04 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.53 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.60 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,522,240.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,481,728.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 152,854,528.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,382,464.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 23,056,896.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 16,719,872.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,904,832.00 |
| `process_rss_max` | instance=backend, job=backend-node | 17,051,648.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,575,936.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 162,619,392.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,973,824.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,371,776.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,174,528.00 |
| `process_rss_max` | instance=redis, job=redis-node | 23,003,136.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 21.62 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 45.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 1.68 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 20.20 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.94 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 67.03 |
| `node_load1_avg` | instance=backend, job=backend-node | 3.38 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.01 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.26 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.56 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 45.71 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 66.29 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 74.29 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 194,886,144.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 277,980,672.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 267,116,032.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 541,185,024.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,758,596,608.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,058,720,256.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,847,488.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 29.71 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 46.86 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 69.71 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 111,345.14 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.75 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 44,650,892.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 45,641,824.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.38 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 2,412,637.71 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 50,010.29 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,664,901.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 182.86 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,701.71 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 200 — 2026-08-16T05:06:08.000Z ~ 2026-08-16T05:08:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 4,345 | 505.69 | 500.32 | 638.49 | 702.38 | 748.23 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,392 | 133.84 | 130.87 | 187.82 | 215.57 | 298.95 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 13,127 | 166.54 | 164.16 | 224.61 | 251.07 | 337.86 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 110.17 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 3.79 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,685 | 147.90 | 145.33 | 203.27 | 233.22 | 306.49 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 616 | 110.97 | 108.68 | 153.65 | 181.69 | 216.25 |
| method=POST, status=403, uri=UNKNOWN | 153 | 0.46 | 0.57 | 1.78 | 5.35 | 7.27 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 101.35 | 94.74 | 170.01 | 177.17 | 159.83 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.00 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 9.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 25.00 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 46.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.70 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.03 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.12 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.66 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.69 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,442,368.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,420,288.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 147,820,032.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,395,776.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 23,007,232.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 16,879,616.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,884,352.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,478,208.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,420,288.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 153,280,512.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,912,384.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,244,800.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,518,592.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,999,040.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 43.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 22.31 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 45.17 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 84.56 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.56 |
| `node_load1_avg` | instance=backend, job=backend-node | 6.68 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.33 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.55 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 444.57 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 285.71 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 177.14 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 181,182,464.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 247,269,376.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 275,518,976.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 540,563,456.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,757,129,728.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,061,354,496.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,819,328.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 388.57 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 254.86 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 76.57 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 658.29 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 157.71 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 21.71 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 130,017.14 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 202.29 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 3.43 |
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
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 47,180,352.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 48,389,296.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.35 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 2,907,830.86 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 67,657.14 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 3,233,192.00 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 198.86 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,194.29 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 300 — 2026-08-16T05:08:08.000Z ~ 2026-08-16T05:10:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 5,946 | 436.82 | 427.76 | 526.42 | 604.24 | 748.23 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,641 | 115.07 | 112.96 | 151.70 | 172.26 | 319.57 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,283 | 144.37 | 142.93 | 185.05 | 210.55 | 400.68 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 110.17 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 4,897 | 100.67 | 98.93 | 133.68 | 156.15 | 208.77 |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 3.79 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,969 | 131.95 | 129.58 | 170.63 | 194.39 | 307.20 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,981 | 97.72 | 95.71 | 131.02 | 151.73 | 267.61 |
| method=POST, status=403, uri=UNKNOWN | 87 | 0.30 | 0.55 | 1.42 | 2.18 | 7.27 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 10 | 89.05 | 87.24 | 109.18 | 111.31 | 159.83 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 7.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 25.12 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 52.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.82 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.04 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.50 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.65 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.67 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,457,728.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,420,288.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 149,421,568.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,361,984.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 23,065,088.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,080,320.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,862,848.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,474,112.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,420,288.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 151,584,768.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,728,064.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,248,896.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 18,305,024.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,999,040.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 82.85 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.47 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 19.05 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 45.89 |
| `node_load1_avg` | instance=backend, job=backend-node | 7.06 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.54 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.51 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 1,937.14 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 363.43 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 37.71 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 175,297,024.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 247,765,504.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 280,861,184.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 540,271,104.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,753,487,872.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,054,927,360.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,812,672.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 3,324.57 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 59.43 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 43.43 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 2,883.43 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 30.86 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 130,298.29 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 49,609,441.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 50,139,304.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.33 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,790,061.71 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 107,665.14 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,154,114.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 102.86 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,505.14 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 400 — 2026-08-16T05:10:08.000Z ~ 2026-08-16T05:12:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 5,478 | 442.15 | 426.58 | 558.11 | 628.68 | 797.28 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,465 | 117.19 | 114.46 | 156.95 | 187.09 | 348.24 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 10,089 | 146.23 | 143.10 | 194.96 | 230.79 | 400.68 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 6,791 | 103.24 | 100.35 | 140.04 | 171.47 | 328.22 |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,520 | 139.23 | 134.14 | 192.35 | 232.51 | 340.41 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,505 | 103.07 | 98.84 | 149.45 | 180.50 | 230.66 |
| method=POST, status=403, uri=UNKNOWN | 87 | 0.34 | 0.57 | 1.65 | 2.71 | 3.03 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 94.88 | 100.00 | 110.66 | 111.61 | 110.65 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 9.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 25.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 49.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.80 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.04 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.62 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.65 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,343,040.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,420,288.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 151,691,776.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,103,936.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 22,979,584.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,082,368.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,859,776.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,343,040.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,420,288.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 153,047,040.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,494,592.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 23,105,536.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,551,360.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,974,464.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1,001.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 43.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 17.72 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 46.05 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 81.05 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.46 |
| `node_load1_avg` | instance=backend, job=backend-node | 6.64 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.03 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.46 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.56 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 6,651.43 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 42.29 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 77.71 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 147,848,704.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 252,517,376.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 279,917,568.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 537,577,984.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,679,257,088.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,056,367,616.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,875,648.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 12,163.43 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 18.29 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 80.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 34,072.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 5.71 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 122,596.57 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 152.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 2.29 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.38 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.62 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 50,674,744.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 51,048,408.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.32 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,442,546.29 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 104,597.71 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,784,206.86 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,651.43 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


## round9-hot-auction-pattern-20260816.json

- 시나리오: `hot-auction-pattern`
- K6 실행: 2026-08-16T05:12:48.000Z ~ 2026-08-16T05:20:54.000Z
- 설정: `{"sseUsers":500,"hotAuctionCount":3,"hotAuctionRate":14,"coldAuctionRatePerAuction":0.09,"duration":"5m"}`

### 0-1min — 2026-08-16T05:14:27.000Z ~ 2026-08-16T05:15:27.000Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 863,882.01 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 237.55 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,976 | 102.39 | 101.35 | 129.50 | 154.13 | 260.24 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | 856,312.45 |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 31.65 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 854,297.04 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 12,207 | 0.58 | 0.56 | 1.50 | 3.07 | 198.97 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 61.79 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 218.03 |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 186.26 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,339 | 87.20 | 83.03 | 107.68 | 129.01 | 224.63 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,633 | 69.33 | 69.00 | 88.45 | 105.24 | 187.41 |
| method=POST, status=403, uri=UNKNOWN | 80 | 0.16 | 0.52 | 0.98 | 1.54 | 3.50 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 12 | 67.50 | 69.35 | 87.47 | 89.08 | 118.45 |

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
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 13.33 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.38 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.44 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.51 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,818,752.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,412,096.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 149,880,832.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,495,616.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 23,558,144.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,048,576.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,806,528.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,818,752.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,412,096.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 156,495,872.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,842,752.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 25,440,256.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,170,432.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,843,392.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 463.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 12.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 16.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 17.54 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 34.47 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 63.70 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.45 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.58 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.04 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.51 |
| `node_load1_avg` | instance=redis, job=redis-node | 0.82 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 5,825.33 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 25.33 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 60.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 70,711,296.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 255,521,792.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 272,497,664.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 533,296,128.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,471,321,600.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,060,393,984.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,781,952.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 7,622.67 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 32.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 61.33 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 10,002.67 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 198.67 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 64,878.67 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 3.50 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 52,229,618.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 52,966,904.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.32 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,716,608.00 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 5,302.67 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,835,530.67 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,185.33 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### 1-2min — 2026-08-16T05:15:27.000Z ~ 2026-08-16T05:16:27.000Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 863,882.01 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,595 | 98.78 | 97.04 | 124.68 | 150.23 | 263.86 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | 856,312.45 |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 31.65 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 854,297.04 |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 12,789 | 0.56 | 0.56 | 1.44 | 2.65 | 198.97 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 61.79 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 186.26 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,320 | 85.47 | 81.62 | 100.18 | 119.22 | 224.63 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,271 | 66.05 | 65.11 | 87.15 | 98.88 | 187.41 |
| method=POST, status=403, uri=UNKNOWN | 80 | 0.15 | 0.51 | 0.97 | 1.19 | 1.77 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 4 | 69.48 | 72.70 | 87.80 | 89.14 | 118.45 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 24.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 12.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.26 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.41 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.50 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,720,448.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,412,096.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 150,499,328.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,620,544.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 26,647,552.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,196,032.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,719,488.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,818,752.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,412,096.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 152,891,392.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,973,824.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,160,576.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,379,328.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,827,008.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 10.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 14.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.07 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 59.36 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.28 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 25.70 |
| `node_load1_avg` | instance=backend, job=backend-node | 3.25 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.02 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.69 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.22 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 9,442.67 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 289.33 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 85.33 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 108,872,704.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 249,288,704.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 272,395,264.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 534,465,536.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,406,049,792.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,056,417,792.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,803,456.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 11,218.67 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 81.33 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 116.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 20,302.67 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 66.67 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 62,832.00 |
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
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.75 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 54,068,380.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 54,739,400.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.30 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,693,502.67 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 4,530.67 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,806,832.00 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,121.33 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### 2-3min — 2026-08-16T05:16:27.000Z ~ 2026-08-16T05:17:27.000Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,601 | 97.86 | 94.16 | 138.65 | 156.42 | 263.86 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 884 | 0.60 | 0.57 | 1.49 | 2.33 | 30.58 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,340 | 84.72 | 80.61 | 113.80 | 132.72 | 224.63 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,251 | 65.83 | 63.99 | 87.61 | 101.92 | 187.41 |
| method=POST, status=403, uri=UNKNOWN | 80 | 0.21 | 0.50 | 0.95 | 0.99 | 1.77 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 74.00 | 67.11 | 87.24 | 89.03 | 118.45 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 6.00 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 7.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 24.00 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 10.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.10 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.38 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.42 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,992,832.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,528,832.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 154,311,680.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,326,656.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,261,952.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,024,000.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,584,320.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,211,968.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,674,240.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 156,954,624.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,662,528.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,344,896.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,326,080.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,659,072.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 10.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 12.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.15 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 65.47 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.18 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 25.96 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.68 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.01 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.72 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.54 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 3,250.67 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 300.00 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 113.33 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 114,914,304.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 238,894,080.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 271,536,128.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 531,949,568.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,366,510,080.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,051,651,072.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,844,416.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 4,492.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 74.67 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 109.33 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 3,110.67 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 17.33 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 63,652.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 322.67 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 5.33 |
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
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 55,829,256.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 56,550,824.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.29 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,702,530.67 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 4,514.67 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,816,186.67 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,148.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### 3-4min — 2026-08-16T05:17:27.000Z ~ 2026-08-16T05:18:27.000Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,605 | 96.07 | 95.47 | 110.99 | 128.91 | 263.86 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 10,009 | 0.51 | 0.54 | 1.30 | 2.34 | 30.58 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,325 | 82.62 | 79.86 | 96.84 | 106.23 | 215.70 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,267 | 64.70 | 64.42 | 85.65 | 88.85 | 148.76 |
| method=POST, status=403, uri=UNKNOWN | 80 | 0.15 | 0.51 | 0.97 | 1.19 | 1.39 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 67.14 | 64.31 | 86.87 | 88.96 | 88.35 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.00 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 25.00 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 10.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.42 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.49 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,784,960.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,383,424.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 149,331,968.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 15,984,640.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,372,544.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,147,904.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,622,208.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,892,480.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,383,424.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 149,790,720.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,551,936.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,443,200.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,362,944.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,622,208.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 10.50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 13.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 72.08 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.27 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 25.93 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.27 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.46 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.72 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.51 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 690.67 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 84.00 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 52.00 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 117,697,536.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 250,620,928.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 271,877,120.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 526,221,312.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,350,237,696.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,050,461,184.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,847,488.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 928.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 12.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 46.67 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 6,469.33 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 37.33 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 63,766.67 |
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
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.25 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 57,608,368.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 58,247,896.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.28 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,707,580.00 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 4,468.00 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,821,637.33 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 62.67 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,232.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### 4-5min — 2026-08-16T05:18:27.000Z ~ 2026-08-16T05:19:27.000Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,624 | 97.21 | 96.68 | 111.34 | 129.65 | 261.37 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 17,568 | 0.51 | 0.54 | 1.31 | 2.54 | 27.68 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,700 | 85.25 | 81.86 | 98.68 | 109.03 | 215.70 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 920 | 65.23 | 64.98 | 86.28 | 88.91 | 121.81 |
| method=POST, status=403, uri=UNKNOWN | 80 | 0.21 | 0.52 | 0.98 | 2.94 | 2.86 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 62.53 | 61.52 | 82.77 | 88.14 | 88.35 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 8.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 25.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 9.33 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.10 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.37 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.47 |
| `process_rss_avg` | instance=backend, job=backend-node | 15,814,656.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,383,424.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 152,870,912.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,104,448.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,208,704.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,308,672.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,550,528.00 |
| `process_rss_max` | instance=backend, job=backend-node | 15,880,192.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,383,424.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 153,460,736.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,642,048.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,316,224.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,580,032.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,622,208.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 501.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 7.50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 13.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 59.37 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.22 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 24.97 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 46.93 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.20 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.43 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.63 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 762.67 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 26.67 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 81.33 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 116,928,512.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 247,964,672.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 270,649,344.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 524,880,896.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,336,150,528.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,050,607,616.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,851,584.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1,041.33 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 5.33 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 68.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 1,653.33 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 51,069.33 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
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
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 59,135,096.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 59,461,448.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.27 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 762,089.33 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 1,988.00 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 816,761.33 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 74.67 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,957.33 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


## round9-bid-only-load-noSSE-20260816.json

- 시나리오: `bid-only-load (SSE 없음, 분산)`
- K6 실행: 2026-08-16T05:20:57.000Z ~ 2026-08-16T05:33:17.000Z
- 설정: `{"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m","hotAuctionId":null}`

### QPS 50 — 2026-08-16T05:21:08.000Z ~ 2026-08-16T05:23:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 1,259 | 161.74 | 152.36 | 220.73 | 239.76 | 332.24 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,143 | 48.52 | 45.64 | 83.20 | 88.43 | 119.35 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,603 | 63.99 | 61.31 | 88.99 | 98.37 | 151.36 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 31.08 |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 228.09 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,195 | 56.26 | 54.03 | 81.67 | 88.04 | 118.06 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.18 | 0.50 | 0.96 | 1.00 | 4.51 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 45.65 | 42.88 | 60.40 | 61.29 | 59.54 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 28.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 10.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.10 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.12 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.21 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.26 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,105,472.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,381,376.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 152,826,368.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,374,784.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,196,928.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,099,776.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,925,312.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,105,472.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,514,496.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 160,403,456.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,752,640.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,443,200.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,424,384.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,941,696.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 5.12 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 7.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.41 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 14.17 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 40.92 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 27.26 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.07 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.34 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.14 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 1,097.14 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 118.86 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 82.29 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 228,110,336.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 241,892,352.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 272,271,360.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 527,606,784.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,266,881,536.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,050,767,360.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,865,920.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 689.14 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 16.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 98.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 1,326.86 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 81.14 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 6.86 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 50,080.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 340.57 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 2,912.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.75 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.75 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 58,855,666.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 59,117,088.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.29 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 937,849.14 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 11,200.00 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,026,369.14 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 144.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,480.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 100 — 2026-08-16T05:23:08.000Z ~ 2026-08-16T05:25:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 1,739 | 263.93 | 274.47 | 320.90 | 354.48 | 425.95 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,795 | 68.13 | 71.24 | 87.78 | 89.25 | 124.27 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,296 | 87.28 | 88.67 | 103.27 | 110.93 | 181.05 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 1 | 178.19 | 167.77 | 177.84 | 178.73 | 178.19 |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 1 | 242.15 | 234.88 | 244.95 | 245.84 | 242.15 |
| method=GET, status=200, uri=/api/statistic/market | 1 | 324.56 | 328.96 | 355.02 | 357.33 | 324.56 |
| method=GET, status=200, uri=/api/statistic/price-movers | 1 | 865.66 | 850.05 | 890.31 | 893.89 | 865.66 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 174.90 |
| method=GET, status=404, uri=/** | 1 | 80.46 | 78.29 | 88.36 | 89.25 | 80.46 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 1 | 0.33 | 0.50 | 0.95 | 0.99 | 0.33 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 1 | 33.47 | 30.76 | 33.27 | 33.50 | 33.47 |
| method=OPTIONS, status=200, uri=/api/cards | 1 | 0.33 | 0.53 | 1.00 | 2.03 | 0.33 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 1 | 32.43 | 30.76 | 33.27 | 33.50 | 32.92 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 1 | 31.87 | 30.76 | 33.27 | 33.50 | 32.63 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 1 | 32.12 | 30.76 | 33.27 | 33.50 | 32.49 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 228.09 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,742 | 75.73 | 76.16 | 88.62 | 95.77 | 154.35 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 18 | 60.06 | 61.52 | 80.53 | 87.69 | 68.10 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.15 | 0.50 | 0.95 | 0.99 | 4.51 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 60.54 | 62.91 | 66.69 | 67.02 | 63.65 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 28.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 14.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.15 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.25 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.33 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,197,632.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,475,584.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 153,782,272.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,591,360.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,259,392.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,266,688.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,941,696.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,367,616.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,641,472.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 164,073,472.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,863,232.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,480,064.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,551,360.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,941,696.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.25 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 8.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 15.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.43 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 30.16 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.41 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 13.81 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.31 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.28 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.46 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 6,837.71 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 438.86 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 36.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 220,023,808.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 240,560,640.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 272,056,320.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 527,339,520.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,263,337,984.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,045,559,296.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,859,776.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 7,941.71 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 190.86 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 28.57 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 144.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 146.29 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 1.14 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 71,349.71 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.75 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.50 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 59,417,314.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 59,769,368.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.29 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,348,939.43 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 15,932.57 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,475,325.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 128.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,745.14 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 150 — 2026-08-16T05:25:08.000Z ~ 2026-08-16T05:27:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 2,960 | 307.62 | 320.47 | 354.51 | 357.54 | 480.24 |
| method=GET, status=200, uri=/api/auctions/stream | 7 | 614,595.03 | 7,158.28 | 30,000.00 | 30,000.00 | 72,661.60 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,971 | 78.27 | 78.41 | 88.73 | 97.17 | 204.87 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,896 | 98.31 | 96.79 | 110.60 | 125.90 | 245.12 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 1 | 51.65 | 53.13 | 55.64 | 55.87 | 51.65 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 178.19 |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 1 | 47.17 | 47.68 | 50.33 | 54.80 | 47.17 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 242.15 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 324.56 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 865.66 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 1 | 72.19 | 78.29 | 88.36 | 89.25 | 72.19 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 174.90 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 80.46 |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 5 | 0.34 | 0.50 | 0.95 | 0.99 | 0.41 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.68 |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 33.47 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.33 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 1 | 0.18 | 0.50 | 0.95 | 0.99 | 0.18 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 32.92 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 32.63 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 32.49 |
| method=OPTIONS, status=200, uri=/api/wallet | 1 | 0.32 | 0.50 | 0.95 | 0.99 | 0.32 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 1 | 59.89 | 58.72 | 61.24 | 61.46 | 59.89 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,912 | 85.66 | 80.06 | 96.96 | 105.12 | 191.62 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 42 | 68.24 | 65.59 | 87.75 | 170.68 | 159.31 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.30 | 0.55 | 1.28 | 1.71 | 2.78 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 10 | 65.63 | 64.78 | 84.45 | 88.47 | 70.95 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 2.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 27.12 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 24.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.23 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.38 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.42 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,057,856.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,473,536.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 149,506,560.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,385,536.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,279,872.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,049,088.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,932,480.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,211,968.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,473,536.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 153,346,048.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,850,944.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,529,216.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,416,192.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,941,696.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.12 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1.62 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.62 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 3.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 21.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 45.07 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.45 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 20.64 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 49.24 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.26 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.47 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.74 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 1,029.71 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 67.43 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 74.29 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 210,618,368.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 243,491,840.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 271,750,656.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 526,143,488.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,261,944,320.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,045,218,304.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,850,560.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 2,068.57 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 16.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 129.14 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 208.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 9.14 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 111,144.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 1,114.29 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 6,468.57 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.00 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 60,700,529.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 61,585,072.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.29 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 2,256,715.43 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 26,013.71 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 2,471,382.86 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 73.14 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 2,298.29 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 200 — 2026-08-16T05:27:08.000Z ~ 2026-08-16T05:29:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 4,121 | 334.81 | 332.58 | 410.22 | 444.09 | 596.99 |
| method=GET, status=200, uri=/api/auctions/stream | 1 | 9,858.18 | 9,305.76 | 9,950.01 | 10,007.27 | 72,661.60 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,210 | 83.12 | 79.16 | 93.26 | 102.35 | 265.29 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,496 | 105.91 | 105.13 | 129.28 | 148.75 | 370.30 |
| method=GET, status=200, uri=/api/auth/csrf | 1 | 52.05 | 53.13 | 55.64 | 55.87 | 52.05 |
| method=GET, status=200, uri=/api/auth/me | 1 | 57.52 | 58.72 | 61.24 | 61.46 | 57.52 |
| method=GET, status=200, uri=/api/cards | 1 | 70.83 | 78.88 | 89.47 | 129.73 | 70.83 |
| method=GET, status=200, uri=/api/me/notifications/stream | 1 | 108,681.27 | 30,000.00 | 30,000.00 | 30,000.00 | 108,681.27 |
| method=GET, status=200, uri=/api/me/wallet/stream | 1 | 108,744.18 | 30,000.00 | 30,000.00 | 30,000.00 | 108,744.18 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 1 | 55.15 | 53.13 | 55.64 | 55.87 | 55.15 |
| method=GET, status=200, uri=/api/statistic/insights | 1 | 10.72 | 10.52 | 11.18 | 15.10 | 242.15 |
| method=GET, status=200, uri=/api/statistic/market | 1 | 8.59 | 9.12 | 9.79 | 21.25 | 324.56 |
| method=GET, status=200, uri=/api/statistic/price-movers | 1 | 178.08 | 168.36 | 178.95 | 196.83 | 865.66 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 5 | 89.59 | 89.48 | 110.66 | 111.61 | 101.84 |
| method=GET, status=200, uri=/api/wishlists | 2 | 61.12 | 50.33 | 87.24 | 89.03 | 74.75 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 174.90 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0.41 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 1 | 0.31 | 0.50 | 0.95 | 0.99 | 0.31 |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0.68 |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 33.47 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.18 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 32.92 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 32.63 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 32.49 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.32 |
| method=OPTIONS, status=200, uri=/api/wishlists | 1 | 0.43 | 0.53 | 1.00 | 1.33 | 0.43 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 59.89 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,038 | 91.84 | 89.58 | 108.08 | 127.92 | 257.02 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 115 | 70.69 | 72.70 | 88.11 | 89.48 | 159.31 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.29 | 0.54 | 1.28 | 2.31 | 4.35 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 10 | 74.21 | 75.10 | 88.04 | 89.19 | 88.25 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 26.62 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 32.00 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.32 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.49 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.54 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,167,424.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,571,840.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 149,263,360.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,324,608.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,280,896.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 16,964,608.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,861,824.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,293,888.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,604,608.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 156,360,704.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,748,544.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,369,472.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,321,984.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,917,120.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 28.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 44.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 25.22 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 50.21 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 57.68 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.78 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.60 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.48 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.60 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 1,262.86 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 571.43 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 277.71 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 200,904,192.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 253,080,576.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 267,226,624.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 523,666,432.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,256,315,392.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,050,040,832.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,835,712.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 1,250.29 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 192.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 26.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 1,291.43 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 105.14 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 77.71 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 142,481.14 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.62 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.00 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 63,031,936.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 64,246,048.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.27 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 3,138,080.00 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 36,410.29 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 3,435,065.14 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 121.14 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 2,077.71 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 300 — 2026-08-16T05:29:08.000Z ~ 2026-08-16T05:31:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 5,728 | 389.25 | 395.13 | 444.39 | 483.74 | 676.86 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 9,858.18 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,659 | 100.88 | 100.15 | 128.69 | 133.96 | 344.87 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 17,082 | 127.19 | 125.78 | 154.45 | 171.02 | 408.29 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 52.05 |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | 57.52 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 70.83 |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | 108,681.27 |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | 108,744.18 |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 55.15 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 10.72 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 8.59 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 178.08 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 101.84 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 74.75 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 5 | 6.39 | 6.52 | 8.11 | 8.33 | 7.43 |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0.36 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | 0.31 |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0.43 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,173 | 110.61 | 110.26 | 133.61 | 151.79 | 312.67 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,336 | 86.68 | 84.20 | 105.54 | 111.63 | 122.89 |
| method=POST, status=403, uri=UNKNOWN | 142 | 0.32 | 0.54 | 1.49 | 2.40 | 4.35 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 10 | 87.27 | 81.49 | 124.15 | 132.20 | 114.33 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.62 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 7.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 24.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 41.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.46 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 126.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.60 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.63 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,117,760.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,497,088.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 150,900,736.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,422,912.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,275,776.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,054,720.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,797,312.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,117,760.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,604,608.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 154,013,696.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,908,288.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,725,824.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,326,080.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,900,736.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 1.50 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 44.12 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 69.87 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.55 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 26.93 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 49.42 |
| `node_load1_avg` | instance=backend, job=backend-node | 4.36 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.58 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.52 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 309.71 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 296.00 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 68.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 197,287,424.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 254,356,992.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 264,542,208.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 520,814,592.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,255,471,104.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,049,724,928.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,835,200.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 244.57 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 73.14 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 25.14 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 53.71 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 192.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 163,804.57 |
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
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 67,294,604.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 68,270,576.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.25 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 3,414,453.71 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 47,452.57 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 3,781,931.43 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 139.43 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,884.57 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 400 — 2026-08-16T05:31:08.000Z ~ 2026-08-16T05:33:08.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 5,863 | 424.31 | 394.78 | 475.30 | 1,233.61 | 7,865.98 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,488 | 112.69 | 98.46 | 133.08 | 281.87 | 6,386.29 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 16,937 | 139.35 | 124.80 | 166.45 | 347.03 | 6,500.66 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | 7.43 |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,185 | 120.04 | 110.79 | 154.18 | 338.08 | 784.73 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 3,974 | 94.13 | 82.80 | 109.76 | 195.59 | 6,366.96 |
| method=POST, status=403, uri=UNKNOWN | 41 | 0.53 | 0.58 | 2.87 | 4.07 | 4.35 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 89.31 | 83.89 | 98.42 | 99.68 | 194.15 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 5.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 9.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 24.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 37.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 1.74 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.50 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.55 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.60 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,327,168.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,414,144.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 163,024,896.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,507,392.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,276,288.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,273,856.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,759,936.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,683,008.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,592,320.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 166,465,536.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,924,672.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,701,248.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,534,976.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,892,544.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 76.02 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.42 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 17.45 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 44.64 |
| `node_load1_avg` | instance=backend, job=backend-node | 6.52 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.70 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.49 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 40,564.57 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 56.00 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 9.14 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 148,264,960.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 241,817,600.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 266,813,952.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 518,439,936.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,231,760,896.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,051,251,200.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,839,808.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 65,179.43 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 14.86 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 5.71 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 34,969.14 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 119,460.57 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 110.86 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 30.88 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.62 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 68,709,916.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 68,891,184.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.24 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,356,974.86 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 48,458.29 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,646,818.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 121.14 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,692.57 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


## round9-bid-only-load-singleHotAuction-20260816.json

- 시나리오: `bid-only-load (SSE 없음, 핫경매집중)`
- K6 실행: 2026-08-16T05:33:18.000Z ~ 2026-08-16T05:45:34.000Z
- 설정: `{"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m","hotAuctionId":3001095}`

### QPS 50 — 2026-08-16T05:33:24.000Z ~ 2026-08-16T05:35:24.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 1,258 | 214.94 | 214.11 | 243.92 | 263.01 | 7,865.98 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,143 | 61.87 | 59.81 | 86.24 | 89.25 | 6,386.29 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,601 | 77.69 | 78.52 | 96.12 | 99.98 | 6,500.66 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 165.89 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,193 | 67.20 | 66.83 | 87.29 | 89.15 | 784.73 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 7 | 55.61 | 55.92 | 66.27 | 66.94 | 6,366.96 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.20 | 0.51 | 0.97 | 1.61 | 3.91 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 107.33 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 1.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 28.62 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 11.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.09 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.25 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.44 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,138,240.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,338,368.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 158,623,232.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,462,848.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,244,544.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,387,520.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,732,800.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,265,216.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,436,672.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 168,157,184.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,859,136.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,320,320.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,694,720.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,732,800.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 5.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 7.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.48 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 16.78 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 47.46 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 37.29 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.75 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.03 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.35 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.60 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 212.57 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 173.71 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 11.43 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 136,392,192.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 234,447,872.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 269,515,776.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 527,476,736.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,193,390,080.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,051,971,584.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,872,064.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 187.43 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 27.43 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 10.29 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 11.43 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 188.57 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 66,832.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 69,753,309.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 69,828,256.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.24 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 938,153.14 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 9,132.57 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,031,113.14 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 16.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 2,926.86 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 100 — 2026-08-16T05:35:24.000Z ~ 2026-08-16T05:37:24.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 1,790 | 263.07 | 270.54 | 298.52 | 352.41 | 447.86 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,701 | 69.93 | 72.53 | 88.06 | 89.43 | 231.72 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,230 | 87.74 | 86.69 | 101.43 | 111.07 | 215.81 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 2 | 1,800,129.23 | 30,000.00 | 30,000.00 | 30,000.00 | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 2 | 1,800,127.85 | 30,000.00 | 30,000.00 | 30,000.00 | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 1 | 61.59 | 64.31 | 66.83 | 67.05 | 61.59 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 1 | 5.47 | 4.89 | 5.52 | 5.58 | 5.47 |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 1 | 5.53 | 4.89 | 5.52 | 5.58 | 5.53 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 1 | 4.78 | 4.89 | 5.52 | 5.58 | 4.78 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 1 | 5.13 | 4.89 | 5.52 | 5.58 | 5.13 |
| method=OPTIONS, status=200, uri=/api/wallet | 1 | 0.38 | 0.50 | 0.95 | 0.99 | 0.38 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 165.89 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,054 | 75.22 | 76.55 | 88.39 | 89.44 | 190.23 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 688 | 60.21 | 60.10 | 66.79 | 82.87 | 147.33 |
| method=POST, status=403, uri=UNKNOWN | 161 | 0.23 | 0.53 | 1.05 | 1.62 | 2.48 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 87.04 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 29.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 13.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.10 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.25 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.29 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,109,568.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,436,672.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 156,877,824.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,571,392.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,122,176.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,130,496.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,694,912.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,109,568.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,436,672.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 162,476,032.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 17,166,336.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,312,128.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,432,576.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,732,800.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 1.88 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 1.88 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 9.12 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 16.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 48.20 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 29.61 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.48 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 16.36 |
| `node_load1_avg` | instance=backend, job=backend-node | 0.91 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.01 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.31 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.53 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 274.29 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 78.86 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 4.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 145,487,360.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 243,461,120.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 270,409,216.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 527,065,088.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,194,439,680.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,052,337,664.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,872,064.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 315.43 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 24.00 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 3.43 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 192.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 70,573.71 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 5.71 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.38 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.12 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 69,889,016.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 69,976,352.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.25 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 899,299.43 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 12,770.29 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,011,346.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 2,684.57 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 150 — 2026-08-16T05:37:24.000Z ~ 2026-08-16T05:39:24.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 2,919 | 297.17 | 290.91 | 350.11 | 373.39 | 487.39 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,967 | 75.39 | 78.13 | 88.47 | 89.39 | 233.63 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,831 | 95.07 | 94.73 | 107.59 | 111.76 | 283.25 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 61.59 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | 5.47 |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 5.53 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 4.78 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 5.13 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0.38 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,131 | 82.78 | 78.79 | 89.30 | 98.86 | 190.23 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,811 | 64.56 | 64.25 | 83.00 | 88.61 | 209.55 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.22 | 0.52 | 0.99 | 1.58 | 1.71 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |

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
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 20.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.17 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 129.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.33 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.35 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,338,944.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,436,672.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 160,403,456.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,603,648.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,313,152.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 16,793,600.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,712,320.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,371,712.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,436,672.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 165,449,728.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 17,297,408.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,541,504.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,272,832.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,712,320.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 18.12 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 23.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 40.11 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.56 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 15.94 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 49.10 |
| `node_load1_avg` | instance=backend, job=backend-node | 1.47 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.24 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.45 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 105.14 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 406.86 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 4.57 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 214,801,408.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 240,362,496.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 270,183,424.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 526,557,184.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,165,788,672.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,051,234,816.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,874,112.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 89.14 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 35.43 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 6.86 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 161.14 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 84,288.00 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.25 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 70,101,896.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 70,180,536.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.25 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,061,500.57 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 20,988.57 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,226,421.71 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 54.86 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 2,240.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 200 — 2026-08-16T05:39:24.000Z ~ 2026-08-16T05:41:24.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 4,165 | 309.37 | 322.32 | 356.37 | 438.97 | 625.77 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,121 | 78.65 | 78.50 | 88.76 | 98.23 | 233.63 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,430 | 98.75 | 96.25 | 110.70 | 136.52 | 354.59 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,167 | 85.71 | 79.31 | 94.58 | 107.09 | 238.25 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,976 | 66.79 | 65.40 | 86.08 | 89.23 | 240.41 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.25 | 0.52 | 0.99 | 1.68 | 1.97 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 3.00 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 27.00 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 26.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.23 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.12 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.41 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.44 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,371,712.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,436,672.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 166,766,592.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,490,496.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,234,816.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,214,976.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,759,424.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,371,712.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,436,672.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 170,430,464.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 16,875,520.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,357,184.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,485,824.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,974,464.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 25.62 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 34.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 48.36 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.71 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 13.71 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 50.04 |
| `node_load1_avg` | instance=backend, job=backend-node | 2.98 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.43 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.71 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 70.86 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 1,099.43 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 14.86 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 2.29 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 208,166,400.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 235,891,200.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 271,012,864.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 515,539,456.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,165,795,328.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,040,311,808.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,888,448.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 32.00 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 741.71 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 0.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 480.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 91,891.43 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.12 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.38 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.12 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 70,466,235.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 70,670,048.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.25 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,196,100.57 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 29,290.29 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,409,450.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 138.29 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,190.86 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 300 — 2026-08-16T05:41:24.000Z ~ 2026-08-16T05:43:24.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 5,853 | 340.07 | 336.26 | 433.21 | 484.92 | 625.77 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,904 | 86.96 | 83.14 | 108.33 | 127.78 | 261.86 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 17,631 | 109.73 | 106.96 | 134.43 | 161.19 | 354.59 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,001 | 93.44 | 90.27 | 111.78 | 131.00 | 256.96 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 4,865 | 74.80 | 76.56 | 93.63 | 106.22 | 240.41 |
| method=POST, status=403, uri=UNKNOWN | 160 | 0.33 | 0.56 | 1.49 | 2.40 | 2.73 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 4.00 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 6.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 26.00 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 35.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.36 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.52 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.58 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,366,592.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,436,672.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 160,483,328.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,747,008.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,318,272.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,055,744.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,880,256.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,502,784.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,436,672.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 165,347,328.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 17,113,088.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,762,688.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,428,480.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,962,176.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 39.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 61.45 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.43 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 14.34 |
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 50.21 |
| `node_load1_avg` | instance=backend, job=backend-node | 3.62 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.03 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.36 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.76 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 304.00 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 144.00 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 230.86 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 217,599,488.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 241,589,248.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 270,302,720.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 515,454,976.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,165,760,000.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,037,177,856.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,886,912.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 251.43 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 43.43 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 888.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 62.86 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 5.71 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 113,766.86 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 164.57 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 2.29 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.25 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 5.50 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 0.38 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 70,789,815.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 70,901,360.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.25 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 1,254,346.29 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 42,164.57 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,540,786.29 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 692.57 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 1,040.00 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


### QPS 400 — 2026-08-16T05:43:24.000Z ~ 2026-08-16T05:45:24.000Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions | 7,095 | 352.33 | 341.16 | 435.91 | 507.87 | 700.63 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 6,701 | 92.34 | 90.02 | 111.09 | 137.30 | 335.79 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 20,511 | 115.64 | 113.58 | 141.13 | 168.70 | 376.96 |
| method=GET, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/notifications/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/me/wallet/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=400, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=401, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=GET, status=503, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/** | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/processed-events | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/admin/auction-stream/recovery/status | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/csrf | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/auth/me | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | N/A |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | N/A |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 146 | 107.52 | 104.41 | 130.94 | 172.69 | 317.77 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 5,975 | 79.15 | 79.39 | 97.64 | 125.24 | 307.82 |
| method=POST, status=403, uri=UNKNOWN | 82 | 0.27 | 0.53 | 1.12 | 1.85 | 2.73 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 76.52 | 78.29 | 88.36 | 89.25 | 88.67 |

#### 백엔드 애플리케이션 · Hikari

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 6.00 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 9.00 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 23.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, pool=HikariPool-1 | 0.00 |

#### JVM GC

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 41.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.85 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, instance=backend, job=backend-spring | 0.00 |

#### JVM 스레드 · 프로세스

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 127.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 128.00 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.57 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring | 0.58 |
| `process_rss_avg` | instance=backend, job=backend-node | 16,195,072.00 |
| `process_rss_avg` | instance=monitoring, job=monitoring-node | 17,296,384.00 |
| `process_rss_avg` | instance=monitoring-prometheus, job=prometheus | 155,297,792.00 |
| `process_rss_avg` | instance=mysql, job=mysql-exporter | 16,628,736.00 |
| `process_rss_avg` | instance=mysql, job=mysql-node | 27,224,576.00 |
| `process_rss_avg` | instance=redis, job=redis-exporter | 17,104,896.00 |
| `process_rss_avg` | instance=redis, job=redis-node | 22,962,176.00 |
| `process_rss_max` | instance=backend, job=backend-node | 16,461,824.00 |
| `process_rss_max` | instance=monitoring, job=monitoring-node | 17,436,672.00 |
| `process_rss_max` | instance=monitoring-prometheus, job=prometheus | 164,728,832.00 |
| `process_rss_max` | instance=mysql, job=mysql-exporter | 17,100,800.00 |
| `process_rss_max` | instance=mysql, job=mysql-node | 27,299,840.00 |
| `process_rss_max` | instance=redis, job=redis-exporter | 17,412,096.00 |
| `process_rss_max` | instance=redis, job=redis-node | 22,962,176.00 |

#### SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=auction | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=notification | 2.00 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, instance=backend, job=backend-spring, stream=wallet | 2.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |
| `sse_broadcast_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, instance=backend, job=backend-spring | 0.00 |

#### Tomcat

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 1.00 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, instance=backend, job=backend-spring | 50.00 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, instance=backend, job=backend-spring | 30.00 |

#### 노드 · OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | instance=redis, job=redis-node | 49.73 |
| `node_cpu_pct_avg` | instance=backend, job=backend-node | 70.76 |
| `node_cpu_pct_avg` | instance=monitoring, job=monitoring-node | 2.45 |
| `node_cpu_pct_avg` | instance=mysql, job=mysql-node | 9.53 |
| `node_load1_avg` | instance=backend, job=backend-node | 4.32 |
| `node_load1_avg` | instance=monitoring, job=monitoring-node | 0.01 |
| `node_load1_avg` | instance=mysql, job=mysql-node | 0.22 |
| `node_load1_avg` | instance=redis, job=redis-node | 1.55 |
| `node_major_fault_delta` | instance=backend, job=backend-node | 3,050.29 |
| `node_major_fault_delta` | instance=monitoring, job=monitoring-node | 52.57 |
| `node_major_fault_delta` | instance=mysql, job=mysql-node | 5.71 |
| `node_major_fault_delta` | instance=redis, job=redis-node | 0.00 |
| `node_mem_available_avg` | instance=backend, job=backend-node | 201,141,760.00 |
| `node_mem_available_avg` | instance=monitoring, job=monitoring-node | 237,892,096.00 |
| `node_mem_available_avg` | instance=mysql, job=mysql-node | 256,885,760.00 |
| `node_mem_available_avg` | instance=redis, job=redis-node | 517,219,840.00 |
| `node_swap_free_avg` | instance=backend, job=backend-node | 2,164,488,192.00 |
| `node_swap_free_avg` | instance=monitoring, job=monitoring-node | 3,037,204,992.00 |
| `node_swap_free_avg` | instance=mysql, job=mysql-node | 2,646,876,160.00 |
| `node_swap_free_avg` | instance=redis, job=redis-node | 0.00 |
| `node_swap_in_delta` | instance=backend, job=backend-node | 5,548.57 |
| `node_swap_in_delta` | instance=monitoring, job=monitoring-node | 28.57 |
| `node_swap_in_delta` | instance=mysql, job=mysql-node | 1.14 |
| `node_swap_in_delta` | instance=redis, job=redis-node | 0.00 |
| `node_swap_out_delta` | instance=backend, job=backend-node | 8,504.00 |
| `node_swap_out_delta` | instance=monitoring, job=monitoring-node | 0.00 |
| `node_swap_out_delta` | instance=mysql, job=mysql-node | 0.00 |
| `node_swap_out_delta` | instance=redis, job=redis-node | 0.00 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | instance=mysql, job=mysql-exporter | 106,819.43 |
| `mysql_row_lock_current_max` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_time_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_row_lock_waits_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_slow_queries_delta` | instance=mysql, job=mysql-exporter | 0.00 |
| `mysql_threads_connected_avg` | instance=mysql, job=mysql-exporter | 31.00 |
| `mysql_threads_running_avg` | instance=mysql, job=mysql-exporter | 2.00 |
| `mysql_up_avg` | instance=mysql, job=mysql-exporter | 1.00 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_up_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_connected_clients_avg` | instance=redis, job=redis-exporter | 6.00 |
| `redis_blocked_clients_avg` | instance=redis, job=redis-exporter | 1.00 |
| `redis_memory_used_bytes_avg` | instance=redis, job=redis-exporter | 70,764,034.00 |
| `redis_memory_used_bytes_max` | instance=redis, job=redis-exporter | 70,824,792.00 |
| `redis_mem_fragmentation_ratio_avg` | instance=redis, job=redis-exporter | 1.26 |
| `redis_keyspace_hits_delta` | instance=redis, job=redis-exporter | 745,899.43 |
| `redis_keyspace_misses_delta` | instance=redis, job=redis-exporter | 49,601.14 |
| `redis_commands_processed_delta` | instance=redis, job=redis-exporter | 1,048,841.14 |
| `redis_evicted_keys_delta` | instance=redis, job=redis-exporter | 0.00 |
| `redis_expired_keys_delta` | instance=redis, job=redis-exporter | 125.71 |
| `redis_total_error_replies_delta` | instance=redis, job=redis-exporter | 218.29 |
| `redis_rejected_connections_delta` | instance=redis, job=redis-exporter | 0.00 |


> 이 문서는 Claude Code의 도움을 받아 작성하였습니다