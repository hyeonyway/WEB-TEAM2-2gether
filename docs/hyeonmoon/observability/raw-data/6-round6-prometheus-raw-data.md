# 6차 부하테스트 — Prometheus 원시 집계 데이터

이 문서는 K6 결과 JSON의 실제 종료 시각과 실행 시간을 기준으로 stage를 재구성하고, 각 구간 끝 시점에 Prometheus range/vector query를 평가해 만든 원시 집계표다. p50/p95/p99는 서버의 `http_server_requests_seconds_bucket` histogram으로 계산했다. 값은 Prometheus 원 단위(시간은 ms 변환)를 유지하며, `N/A`/빈 표는 그 시간대에 해당 시계열이 없었음을 뜻한다.

수집 범위는 테스트 대상 백엔드, backend/mysql/redis node exporter, MySQL exporter, Redis exporter다. Grafana/Prometheus 자기 관측 메트릭과 정적 build/info/config 시계열은 성능 측정값이 아니므로 제외했다.

## 실행 목록

| 결과 파일 | 시나리오 | 실제 실행 (UTC) | K6 전체 | 평균 지연 | med | p95 | p99 | max |
|---|---|---|---:|---:|---:|---:|---:|---:|
| [`round6-vt-pure-throughput-sse250-20260813.json`](../../../../backend/src/test/k6/result/round6-vt-pure-throughput-sse250-20260813.json) | pure-throughput | 2026-08-13T02:07:19.137Z ~ 2026-08-13T02:21:01.610Z | 136,390 | 165.83 req/s | 2,913.26 | 94.04 | 8,233.23 | 18,283.9 | 60,003.07 |
| [`round6-vt-pure-throughput-sse500-20260813.json`](../../../../backend/src/test/k6/result/round6-vt-pure-throughput-sse500-20260813.json) | pure-throughput | 2026-08-13T02:21:35.965Z ~ 2026-08-13T02:35:25.980Z | 126,060 | 151.88 req/s | 2,425.1 | 66.84 | 10,010.11 | 20,964.05 | 60,001.74 |
| [`round6-vt-pure-throughput-sse1000-20260813.json`](../../../../backend/src/test/k6/result/round6-vt-pure-throughput-sse1000-20260813.json) | pure-throughput | 2026-08-13T02:35:55.338Z ~ 2026-08-13T02:49:45.382Z | 128,026 | 154.24 req/s | 5,101.43 | 280.45 | 23,961.84 | 53,725.35 | 60,042.14 |
| [`round6-vt-hot-auction-pattern-sse250-20260813.json`](../../../../backend/src/test/k6/result/round6-vt-hot-auction-pattern-sse250-20260813.json) | hot-auction-pattern | 2026-08-13T02:50:59.252Z ~ 2026-08-13T02:59:06.201Z | 51,980 | 106.75 req/s | 16,455.16 | 16,254.84 | 39,196.57 | 40,995.7 | 69,737.73 |
| [`round6-vt-bid-only-load-noSSE-20260813.json`](../../../../backend/src/test/k6/result/round6-vt-bid-only-load-noSSE-20260813.json) | bid-only-load (SSE 없음) | 2026-08-13T02:59:46.426Z ~ 2026-08-13T03:12:04.248Z | 131,262 | 177.9 req/s | 3,765.03 | 722.39 | 10,526.33 | 12,148.63 | 18,699.35 |
| [`round6-vt-bid-only-load-singleHotAuction-20260813.json`](../../../../backend/src/test/k6/result/round6-vt-bid-only-load-singleHotAuction-20260813.json) | bid-only-load (SSE 없음) | 2026-08-13T03:12:33.806Z ~ 2026-08-13T03:24:45.785Z | 50,737 | 69.31 req/s | 28,529.01 | 38,855.6 | 43,547.32 | 48,370.02 | 51,545.27 |

---

## round6-vt-pure-throughput-sse250-20260813.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-13T02:07:19.137Z ~ 2026-08-13T02:21:01.610Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-13T02:07:54.137Z ~ 2026-08-13T02:09:54.137Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,253 | 99.99 | 18.88 | 591.51 | 946.62 | 1,283.39 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,020 | 9.23 | 7.99 | 45.44 | 114.24 | 2,567.04 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,520 | 58.27 | 14.86 | 335.23 | 614.81 | 2,657.64 |
| method=GET, status=404, uri=/** | 2 | 20.04 | 2.1 | 38.59 | 39.03 | 58.32 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 762.23 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 181.88 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,073 | 27.61 | 24.74 | 202.75 | 551.55 | 2,949.22 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 1,697.44 |
| method=POST, status=401, uri=UNKNOWN | 79 | 1.6 | 0.78 | 6.68 | 18.51 | 19.9 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 12.5 | 9.79 | 21.81 | 22.26 | 17.66 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 29.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.93 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.45 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.8 |
| `process_rss_avg` | job=backend-node | 14,183,936 |
| `process_rss_avg` | job=monitoring-node | 17,137,664 |
| `process_rss_avg` | job=mysql-exporter | 16,714,752 |
| `process_rss_avg` | job=mysql-node | 22,693,888 |
| `process_rss_avg` | job=prometheus | 123,496,448 |
| `process_rss_avg` | job=redis-exporter | 18,286,592 |
| `process_rss_avg` | job=redis-node | 23,003,648 |
| `process_rss_max` | job=backend-node | 16,347,136 |
| `process_rss_max` | job=monitoring-node | 17,137,664 |
| `process_rss_max` | job=mysql-exporter | 17,129,472 |
| `process_rss_max` | job=mysql-node | 23,150,592 |
| `process_rss_max` | job=prometheus | 123,600,896 |
| `process_rss_max` | job=redis-exporter | 18,722,816 |
| `process_rss_max` | job=redis-node | 23,191,552 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 5.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 37 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 48.88 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 58.95 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 29.44 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 4.07 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 0.77 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 32,576 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 1,350.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 48,595,968 |
| `node_mem_available_avg` | job=monitoring-node | 385,424,384 |
| `node_mem_available_avg` | job=mysql-node | 267,711,488 |
| `node_mem_available_avg` | job=redis-node | 561,809,408 |
| `node_swap_free_avg` | job=backend-node | 2,569,869,824 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,379,904 |
| `node_swap_free_avg` | job=mysql-node | 2,604,260,864 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 55,723.43 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 2,323.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 37,010.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 112,542.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,150.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 9.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-13T02:09:54.137Z ~ 2026-08-13T02:11:54.137Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,775 | 35.13 | 13.77 | 196.77 | 378.61 | 1,283.39 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,722 | 6.56 | 6.27 | 9.15 | 12.91 | 2,567.04 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,246 | 21.04 | 11.89 | 78.29 | 244.04 | 2,657.64 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 38.05 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 762.23 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 181.88 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,728 | 31.78 | 20.74 | 80.53 | 283.59 | 2,949.22 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 7 | 161.16 | 100 | 420.55 | 442.02 | 1,697.44 |
| method=POST, status=401, uri=UNKNOWN | 79 | 1.06 | 0.53 | 1.59 | 35.29 | 34.49 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 13 | 19.13 | 7.55 | 121.91 | 131.76 | 120.18 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 41.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.61 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.27 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.33 |
| `process_rss_avg` | job=backend-node | 15,522,816 |
| `process_rss_avg` | job=monitoring-node | 17,170,432 |
| `process_rss_avg` | job=mysql-exporter | 16,483,840 |
| `process_rss_avg` | job=mysql-node | 22,669,824 |
| `process_rss_avg` | job=prometheus | 123,070,464 |
| `process_rss_avg` | job=redis-exporter | 17,862,656 |
| `process_rss_avg` | job=redis-node | 22,822,912 |
| `process_rss_max` | job=backend-node | 16,572,416 |
| `process_rss_max` | job=monitoring-node | 17,268,736 |
| `process_rss_max` | job=mysql-exporter | 16,838,656 |
| `process_rss_max` | job=mysql-node | 23,085,056 |
| `process_rss_max` | job=prometheus | 123,715,584 |
| `process_rss_max` | job=redis-exporter | 17,960,960 |
| `process_rss_max` | job=redis-node | 22,904,832 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 1.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 39.2 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.32 |
| `node_cpu_pct_avg` | job=mysql-node | 38.19 |
| `node_cpu_pct_avg` | job=redis-node | 0.47 |
| `node_load1_avg` | job=backend-node | 1.91 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.4 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 27,965.71 |
| `node_major_fault_delta` | job=monitoring-node | 51.43 |
| `node_major_fault_delta` | job=mysql-node | 289.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 77,036,544 |
| `node_mem_available_avg` | job=monitoring-node | 396,630,528 |
| `node_mem_available_avg` | job=mysql-node | 264,279,040 |
| `node_mem_available_avg` | job=redis-node | 557,341,696 |
| `node_swap_free_avg` | job=backend-node | 2,527,029,760 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,379,904 |
| `node_swap_free_avg` | job=mysql-node | 2,604,266,496 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 39,476.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 505.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 31,355.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 164,956.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 568 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 13.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-13T02:11:54.137Z ~ 2026-08-13T02:13:54.137Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,979 | 32.47 | 14.85 | 133.93 | 278.59 | 6,011.83 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,883 | 15.73 | 6.9 | 72.53 | 189.71 | 390.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,811 | 28.14 | 13.1 | 129.02 | 246.87 | 6,013.94 |
| method=GET, status=404, uri=/** | 1 | 48.88 | 47.54 | 50.05 | 50.28 | 48.88 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,881 | 45.12 | 24.4 | 191.69 | 338.99 | 6,038.06 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 46 | 131.21 | 134.22 | 268.44 | 334.75 | 411.19 |
| method=POST, status=401, uri=UNKNOWN | 75 | 0.99 | 0.56 | 6.57 | 11.66 | 34.49 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 19 | 11.04 | 7.69 | 34.39 | 38.2 | 120.18 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 68.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.82 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.4 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.52 |
| `process_rss_avg` | job=backend-node | 14,155,264 |
| `process_rss_avg` | job=monitoring-node | 17,088,512 |
| `process_rss_avg` | job=mysql-exporter | 16,521,216 |
| `process_rss_avg` | job=mysql-node | 22,668,288 |
| `process_rss_avg` | job=prometheus | 121,759,232 |
| `process_rss_avg` | job=redis-exporter | 17,480,192 |
| `process_rss_avg` | job=redis-node | 22,904,832 |
| `process_rss_max` | job=backend-node | 14,692,352 |
| `process_rss_max` | job=monitoring-node | 17,240,064 |
| `process_rss_max` | job=mysql-exporter | 17,014,784 |
| `process_rss_max` | job=mysql-node | 22,876,160 |
| `process_rss_max` | job=prometheus | 122,126,336 |
| `process_rss_max` | job=redis-exporter | 18,092,032 |
| `process_rss_max` | job=redis-node | 22,904,832 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 47 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 55.21 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 59.83 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 1.93 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 3.77 |
| `node_load1_avg` | job=redis-node | 0.03 |
| `node_major_fault_delta` | job=backend-node | 31,041.14 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 813.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 71,597,568 |
| `node_mem_available_avg` | job=monitoring-node | 397,433,856 |
| `node_mem_available_avg` | job=mysql-node | 261,136,384 |
| `node_mem_available_avg` | job=redis-node | 553,104,896 |
| `node_swap_free_avg` | job=backend-node | 2,472,941,568 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,382,976 |
| `node_swap_free_avg` | job=mysql-node | 2,603,995,136 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 50,881.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 2,220.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 10,992 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 1,024 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 274,501.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,177.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 40 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-13T02:13:54.137Z ~ 2026-08-13T02:15:54.137Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,105 | 30.82 | 25.05 | 68.35 | 100.64 | 6,011.83 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,192 | 12.06 | 9.28 | 27.74 | 44.92 | 390.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,447 | 27.34 | 20.6 | 65.43 | 104.19 | 6,013.94 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 48.88 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,095 | 43.33 | 35.31 | 93.22 | 134.38 | 6,038.06 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 31 | 30.87 | 16.43 | 119.12 | 131.2 | 411.19 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.88 | 0.7 | 2.74 | 5.27 | 11.2 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 23 | 15.21 | 9.32 | 27.96 | 97.9 | 96.02 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 94.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.76 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.55 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_rss_avg` | job=backend-node | 14,171,648 |
| `process_rss_avg` | job=monitoring-node | 17,190,912 |
| `process_rss_avg` | job=mysql-exporter | 16,574,464 |
| `process_rss_avg` | job=mysql-node | 22,617,088 |
| `process_rss_avg` | job=prometheus | 121,147,392 |
| `process_rss_avg` | job=redis-exporter | 17,633,280 |
| `process_rss_avg` | job=redis-node | 23,012,864 |
| `process_rss_max` | job=backend-node | 14,401,536 |
| `process_rss_max` | job=monitoring-node | 17,240,064 |
| `process_rss_max` | job=mysql-exporter | 16,912,384 |
| `process_rss_max` | job=mysql-node | 23,072,768 |
| `process_rss_max` | job=prometheus | 121,147,392 |
| `process_rss_max` | job=redis-exporter | 17,862,656 |
| `process_rss_max` | job=redis-node | 23,298,048 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 64.94 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.77 |
| `node_cpu_pct_avg` | job=mysql-node | 76.39 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 2.11 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 5.47 |
| `node_load1_avg` | job=redis-node | 0.01 |
| `node_major_fault_delta` | job=backend-node | 2,656 |
| `node_major_fault_delta` | job=monitoring-node | 227.43 |
| `node_major_fault_delta` | job=mysql-node | 626.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 47,161,856 |
| `node_mem_available_avg` | job=monitoring-node | 397,847,040 |
| `node_mem_available_avg` | job=mysql-node | 250,627,072 |
| `node_mem_available_avg` | job=redis-node | 553,525,248 |
| `node_swap_free_avg` | job=backend-node | 2,468,226,048 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,388,096 |
| `node_swap_free_avg` | job=mysql-node | 2,602,053,632 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 3,508.57 |
| `node_swap_in_delta` | job=monitoring-node | 132.57 |
| `node_swap_in_delta` | job=mysql-node | 3,196.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 2,732.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 1.14 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 387,118.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,358.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 59.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-13T02:15:54.137Z ~ 2026-08-13T02:17:54.137Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,505 | 120.9 | 89.71 | 270.54 | 391.64 | 7,090.62 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,448 | 68.98 | 35.06 | 202.85 | 296.76 | 6,297.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 16,399 | 119.09 | 90.22 | 267.02 | 376.6 | 7,211.69 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,821 | 152.14 | 127.73 | 320.18 | 425.12 | 7,150.13 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 464 | 141.59 | 108.95 | 298.11 | 392.63 | 6,990.44 |
| method=POST, status=401, uri=UNKNOWN | 71 | 1.85 | 0.86 | 6.68 | 11.72 | 12.55 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 31 | 67.95 | 37.05 | 238.24 | 342.28 | 347.28 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 129.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.4 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.68 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.77 |
| `process_rss_avg` | job=backend-node | 14,059,520 |
| `process_rss_avg` | job=monitoring-node | 17,223,680 |
| `process_rss_avg` | job=mysql-exporter | 16,646,144 |
| `process_rss_avg` | job=mysql-node | 22,529,024 |
| `process_rss_avg` | job=prometheus | 121,147,392 |
| `process_rss_avg` | job=redis-exporter | 18,108,416 |
| `process_rss_avg` | job=redis-node | 22,887,936 |
| `process_rss_max` | job=backend-node | 14,270,464 |
| `process_rss_max` | job=monitoring-node | 17,240,064 |
| `process_rss_max` | job=mysql-exporter | 17,018,880 |
| `process_rss_max` | job=mysql-node | 22,933,504 |
| `process_rss_max` | job=prometheus | 121,147,392 |
| `process_rss_max` | job=redis-exporter | 18,124,800 |
| `process_rss_max` | job=redis-node | 23,015,424 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 33 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 83.37 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.57 |
| `node_cpu_pct_avg` | job=mysql-node | 95.66 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 4.83 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 10.02 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 13,345.14 |
| `node_major_fault_delta` | job=monitoring-node | 228.57 |
| `node_major_fault_delta` | job=mysql-node | 786.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 57,419,264 |
| `node_mem_available_avg` | job=monitoring-node | 394,627,584 |
| `node_mem_available_avg` | job=mysql-node | 242,147,840 |
| `node_mem_available_avg` | job=redis-node | 553,531,392 |
| `node_swap_free_avg` | job=backend-node | 2,465,391,616 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,388,096 |
| `node_swap_free_avg` | job=mysql-node | 2,602,065,920 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 25,552 |
| `node_swap_in_delta` | job=monitoring-node | 20.57 |
| `node_swap_in_delta` | job=mysql-node | 2,842.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 4,694.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 1.14 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 475,981.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 14,577.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 299.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 7.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-13T02:17:54.137Z ~ 2026-08-13T02:19:54.137Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 6,798 | 167.51 | 159.12 | 290.3 | 370.85 | 7,242.76 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 6,574 | 100.87 | 90.18 | 220.03 | 292.64 | 6,297.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 19,845 | 160.82 | 147.2 | 288.69 | 401.78 | 7,211.69 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 2 | 389.73 | 416.06 | 3,501.53 | 3,563.62 | 3,380.15 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,681 | 208.56 | 198.05 | 341.7 | 428.5 | 7,150.13 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 3,557 | 112.49 | 100.92 | 233.04 | 317.14 | 6,990.44 |
| method=POST, status=401, uri=UNKNOWN | 21 | 3.36 | 2.1 | 9.16 | 9.66 | 42.3 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 25 | 131.19 | 134.22 | 243.83 | 263.51 | 347.28 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.63 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 134.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.52 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.77 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.83 |
| `process_rss_avg` | job=backend-node | 12,938,752 |
| `process_rss_avg` | job=monitoring-node | 17,219,072 |
| `process_rss_avg` | job=mysql-exporter | 16,462,848 |
| `process_rss_avg` | job=mysql-node | 22,402,048 |
| `process_rss_avg` | job=prometheus | 121,196,544 |
| `process_rss_avg` | job=redis-exporter | 17,567,744 |
| `process_rss_avg` | job=redis-node | 22,863,872 |
| `process_rss_max` | job=backend-node | 14,606,336 |
| `process_rss_max` | job=monitoring-node | 17,240,064 |
| `process_rss_max` | job=mysql-exporter | 17,145,856 |
| `process_rss_max` | job=mysql-node | 22,732,800 |
| `process_rss_max` | job=prometheus | 121,540,608 |
| `process_rss_max` | job=redis-exporter | 17,788,928 |
| `process_rss_max` | job=redis-node | 22,863,872 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 90.8 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 96.93 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 11.79 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 19.16 |
| `node_load1_avg` | job=redis-node | 0.03 |
| `node_major_fault_delta` | job=backend-node | 33,880 |
| `node_major_fault_delta` | job=monitoring-node | 8 |
| `node_major_fault_delta` | job=mysql-node | 356.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 37,733,888 |
| `node_mem_available_avg` | job=monitoring-node | 392,486,400 |
| `node_mem_available_avg` | job=mysql-node | 232,647,680 |
| `node_mem_available_avg` | job=redis-node | 554,409,984 |
| `node_swap_free_avg` | job=backend-node | 2,388,052,480 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,388,096 |
| `node_swap_free_avg` | job=mysql-node | 2,601,987,072 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 55,987.43 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 1,661.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 36,205.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 32 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 502,851.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,164.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 139.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 14.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## round6-vt-pure-throughput-sse500-20260813.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-13T02:21:35.965Z ~ 2026-08-13T02:35:25.980Z
- 설정: `{"sseVUs":500,"totalSseConnections":1000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-13T02:22:10.965Z ~ 2026-08-13T02:24:10.965Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,161 | 79.04 | 13.74 | 338.16 | 862.57 | 5,626.1 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,080 | 96.03 | 7.46 | 385.25 | 1,703.07 | 2,367.58 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,361 | 112.81 | 13.34 | 457.74 | 2,218.91 | 8,694.85 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 856,882.72 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 856,997.37 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 851,485.99 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 3,380.15 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,936.59 |
| method=POST, status=200, uri=/api/sse/tickets | 37 | 0.33 | 0.5 | 0.95 | 0.99 | 177.4 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,081 | 94.7 | 21.36 | 403.37 | 992.76 | 1,901.7 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 35 | 273.57 | 230.41 | 938.03 | 975.02 | 948.25 |
| method=POST, status=401, uri=UNKNOWN | 70 | 4.76 | 0.58 | 11.11 | 120.57 | 113.71 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 24.51 | 7.69 | 60.68 | 61.35 | 226.37 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 8 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 36.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.51 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 100.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_rss_avg` | job=backend-node | 14,634,496 |
| `process_rss_avg` | job=monitoring-node | 17,358,848 |
| `process_rss_avg` | job=mysql-exporter | 16,600,576 |
| `process_rss_avg` | job=mysql-node | 22,625,792 |
| `process_rss_avg` | job=prometheus | 121,540,608 |
| `process_rss_avg` | job=redis-exporter | 17,458,688 |
| `process_rss_avg` | job=redis-node | 22,917,120 |
| `process_rss_max` | job=backend-node | 16,990,208 |
| `process_rss_max` | job=monitoring-node | 17,358,848 |
| `process_rss_max` | job=mysql-exporter | 16,973,824 |
| `process_rss_max` | job=mysql-node | 22,847,488 |
| `process_rss_max` | job=prometheus | 121,540,608 |
| `process_rss_max` | job=redis-exporter | 17,608,704 |
| `process_rss_max` | job=redis-node | 22,949,888 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 496 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 495.88 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 54.66 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.26 |
| `node_cpu_pct_avg` | job=mysql-node | 26.85 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 3.93 |
| `node_load1_avg` | job=monitoring-node | 0.1 |
| `node_load1_avg` | job=mysql-node | 2.79 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 100,512 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 37.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 49,110,528 |
| `node_mem_available_avg` | job=monitoring-node | 396,712,448 |
| `node_mem_available_avg` | job=mysql-node | 220,997,120 |
| `node_mem_available_avg` | job=redis-node | 548,003,840 |
| `node_swap_free_avg` | job=backend-node | 2,250,198,528 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,486,400 |
| `node_swap_free_avg` | job=mysql-node | 2,602,016,768 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 169,504 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 37.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 86,093.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 106,437.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,500.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 28.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-13T02:24:10.965Z ~ 2026-08-13T02:26:10.965Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,608 | 70.08 | 13.54 | 241.69 | 1,809.14 | 9,559.37 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,669 | 30.63 | 6.61 | 159.64 | 354.05 | 2,367.58 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,934 | 53.6 | 12.72 | 217.96 | 657.04 | 9,541.69 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 856,882.72 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 856,997.37 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 851,485.99 |
| method=GET, status=404, uri=/** | 1 | 65.03 | 64.31 | 66.83 | 67.05 | 65.03 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,936.59 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 177.4 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,605 | 57.44 | 20.89 | 238.77 | 421.32 | 9,533.09 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 51 | 166.18 | 159.38 | 355.02 | 427.26 | 948.25 |
| method=POST, status=401, uri=UNKNOWN | 72 | 0.61 | 0.52 | 0.98 | 11.7 | 113.71 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 6.5 | 6.29 | 6.92 | 6.98 | 58.49 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 28.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.19 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.22 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_rss_avg` | job=backend-node | 14,561,280 |
| `process_rss_avg` | job=monitoring-node | 17,358,848 |
| `process_rss_avg` | job=mysql-exporter | 16,475,136 |
| `process_rss_avg` | job=mysql-node | 22,563,840 |
| `process_rss_avg` | job=prometheus | 121,540,608 |
| `process_rss_avg` | job=redis-exporter | 17,985,536 |
| `process_rss_avg` | job=redis-node | 22,949,888 |
| `process_rss_max` | job=backend-node | 16,396,288 |
| `process_rss_max` | job=monitoring-node | 17,358,848 |
| `process_rss_max` | job=mysql-exporter | 16,982,016 |
| `process_rss_max` | job=mysql-node | 22,859,776 |
| `process_rss_max` | job=prometheus | 121,540,608 |
| `process_rss_max` | job=redis-exporter | 18,132,992 |
| `process_rss_max` | job=redis-node | 22,949,888 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 43.78 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.22 |
| `node_cpu_pct_avg` | job=mysql-node | 38.3 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 2.21 |
| `node_load1_avg` | job=monitoring-node | 0.06 |
| `node_load1_avg` | job=mysql-node | 2.01 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 55,886.86 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 65.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 55,722,496 |
| `node_mem_available_avg` | job=monitoring-node | 398,989,824 |
| `node_mem_available_avg` | job=mysql-node | 220,897,280 |
| `node_mem_available_avg` | job=redis-node | 548,003,840 |
| `node_swap_free_avg` | job=backend-node | 2,219,442,688 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,486,400 |
| `node_swap_free_avg` | job=mysql-node | 2,602,016,768 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 91,240 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 74.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 35,883.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 154,616 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,982.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 32 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-13T02:26:10.965Z ~ 2026-08-13T02:28:10.965Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,855 | 14.5 | 13.59 | 21.73 | 32.91 | 9,559.37 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,857 | 6.91 | 6.51 | 10.58 | 14.91 | 1,955.16 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,568 | 14.13 | 13.1 | 21.57 | 37.75 | 9,541.69 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 65.03 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,833 | 23.4 | 21.99 | 32.51 | 45.98 | 9,533.09 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 11 | 9.38 | 8.04 | 19.57 | 21.81 | 390.53 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.46 | 0.53 | 1.11 | 3.25 | 12.04 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 10 | 6.35 | 6.29 | 8.07 | 8.33 | 8.31 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 41.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.5 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.36 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.43 |
| `process_rss_avg` | job=backend-node | 13,612,032 |
| `process_rss_avg` | job=monitoring-node | 17,358,848 |
| `process_rss_avg` | job=mysql-exporter | 16,608,768 |
| `process_rss_avg` | job=mysql-node | 22,729,216 |
| `process_rss_avg` | job=prometheus | 121,540,608 |
| `process_rss_avg` | job=redis-exporter | 17,415,168 |
| `process_rss_avg` | job=redis-node | 23,228,416 |
| `process_rss_max` | job=backend-node | 13,975,552 |
| `process_rss_max` | job=monitoring-node | 17,358,848 |
| `process_rss_max` | job=mysql-exporter | 16,977,920 |
| `process_rss_max` | job=mysql-node | 23,162,880 |
| `process_rss_max` | job=prometheus | 121,540,608 |
| `process_rss_max` | job=redis-exporter | 17,481,728 |
| `process_rss_max` | job=redis-node | 23,343,104 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 6 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 46.93 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 62.31 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 1.72 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 2.05 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 8,932.57 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 49.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 53,886,464 |
| `node_mem_available_avg` | job=monitoring-node | 399,387,136 |
| `node_mem_available_avg` | job=mysql-node | 224,538,624 |
| `node_mem_available_avg` | job=redis-node | 560,909,312 |
| `node_swap_free_avg` | job=backend-node | 2,215,797,248 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,486,400 |
| `node_swap_free_avg` | job=mysql-node | 2,602,016,768 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 12,670.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 56 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 5,003.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 267,865.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 35.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 4.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-13T02:28:10.965Z ~ 2026-08-13T02:30:10.965Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,048 | 22.21 | 18.35 | 46.79 | 92.8 | 183.44 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,064 | 9.88 | 8.26 | 20.95 | 34.62 | 90.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,168 | 21.69 | 17.55 | 46.17 | 97.73 | 415.97 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 65.03 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,021 | 35.32 | 30.35 | 62.93 | 126.19 | 282.33 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 21 | 30.35 | 12.58 | 248.3 | 264.41 | 248.52 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.79 | 0.63 | 3.23 | 4.61 | 4.74 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 14 | 10.58 | 8.85 | 30.2 | 32.88 | 31.41 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 8 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 59.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.58 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.52 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.61 |
| `process_rss_avg` | job=backend-node | 13,464,576 |
| `process_rss_avg` | job=monitoring-node | 17,259,008 |
| `process_rss_avg` | job=mysql-exporter | 16,793,088 |
| `process_rss_avg` | job=mysql-node | 22,145,024 |
| `process_rss_avg` | job=prometheus | 121,540,608 |
| `process_rss_avg` | job=redis-exporter | 18,219,008 |
| `process_rss_avg` | job=redis-node | 22,948,352 |
| `process_rss_max` | job=backend-node | 13,766,656 |
| `process_rss_max` | job=monitoring-node | 17,358,848 |
| `process_rss_max` | job=mysql-exporter | 16,990,208 |
| `process_rss_max` | job=mysql-node | 22,528,000 |
| `process_rss_max` | job=prometheus | 121,540,608 |
| `process_rss_max` | job=redis-exporter | 18,612,224 |
| `process_rss_max` | job=redis-node | 23,080,960 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 5.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 11 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 64.55 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.38 |
| `node_cpu_pct_avg` | job=mysql-node | 80.83 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 3.52 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 3.78 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 3,517.71 |
| `node_major_fault_delta` | job=monitoring-node | 37.71 |
| `node_major_fault_delta` | job=mysql-node | 574.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 57,764,864 |
| `node_mem_available_avg` | job=monitoring-node | 400,612,352 |
| `node_mem_available_avg` | job=mysql-node | 256,827,392 |
| `node_mem_available_avg` | job=redis-node | 562,573,312 |
| `node_swap_free_avg` | job=backend-node | 2,216,130,048 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,486,400 |
| `node_swap_free_avg` | job=mysql-node | 2,581,297,152 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 4,184 |
| `node_swap_in_delta` | job=monitoring-node | 4.57 |
| `node_swap_in_delta` | job=mysql-node | 604.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 2,325.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 10,634.29 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 378,906.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 787.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 25.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-13T02:30:10.965Z ~ 2026-08-13T02:32:10.965Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,538 | 126.75 | 109.56 | 290.78 | 399.1 | 606.9 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,512 | 77.77 | 44.01 | 229.48 | 318.55 | 534.37 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 16,530 | 126.73 | 107.5 | 295.43 | 408.36 | 690.62 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,789 | 160.37 | 153.75 | 348.34 | 442.04 | 629.97 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 509 | 132.1 | 116.64 | 286.19 | 355.91 | 464.65 |
| method=POST, status=401, uri=UNKNOWN | 72 | 1.55 | 0.72 | 6.78 | 10.3 | 10.91 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 26 | 118.06 | 94.74 | 295.27 | 344.59 | 350.8 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14.63 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 81.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.72 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.77 |
| `process_rss_avg` | job=backend-node | 13,443,584 |
| `process_rss_avg` | job=monitoring-node | 17,186,816 |
| `process_rss_avg` | job=mysql-exporter | 16,801,792 |
| `process_rss_avg` | job=mysql-node | 22,317,568 |
| `process_rss_avg` | job=prometheus | 119,549,952 |
| `process_rss_avg` | job=redis-exporter | 17,665,024 |
| `process_rss_avg` | job=redis-node | 22,929,408 |
| `process_rss_max` | job=backend-node | 14,032,896 |
| `process_rss_max` | job=monitoring-node | 17,186,816 |
| `process_rss_max` | job=mysql-exporter | 17,326,080 |
| `process_rss_max` | job=mysql-node | 22,728,704 |
| `process_rss_max` | job=prometheus | 121,540,608 |
| `process_rss_max` | job=redis-exporter | 18,612,224 |
| `process_rss_max` | job=redis-node | 22,929,408 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 34.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 86.47 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 96.75 |
| `node_cpu_pct_avg` | job=redis-node | 0.39 |
| `node_load1_avg` | job=backend-node | 6.39 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 11.33 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 19,264 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 232 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 65,166,336 |
| `node_mem_available_avg` | job=monitoring-node | 402,572,800 |
| `node_mem_available_avg` | job=mysql-node | 264,398,336 |
| `node_mem_available_avg` | job=redis-node | 562,521,088 |
| `node_swap_free_avg` | job=backend-node | 2,216,380,416 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,490,496 |
| `node_swap_free_avg` | job=mysql-node | 2,578,336,768 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 28,833.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 441.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 17,653.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 1.14 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 491,901.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 10,376 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 209.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 12.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-13T02:32:10.965Z ~ 2026-08-13T02:34:10.965Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,493 | 178.2 | 161.49 | 314.36 | 449.03 | 2,401.07 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,155 | 111.71 | 96 | 243.09 | 348.79 | 1,290.69 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 15,964 | 176.67 | 159.8 | 322.13 | 449.64 | 1,479.08 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,519 | 226.82 | 209.11 | 385.76 | 498.88 | 2,140.31 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,249 | 123.64 | 108.52 | 263.15 | 352.05 | 1,202.4 |
| method=POST, status=401, uri=UNKNOWN | 25 | 13.24 | 1 | 60.96 | 174.04 | 174.21 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 17 | 79.15 | 53.13 | 192.94 | 199.65 | 350.8 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.71 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.29 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.29 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 86.82 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.13 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.11 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.74 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.77 |
| `process_rss_avg` | job=backend-node | 11,748,352 |
| `process_rss_avg` | job=monitoring-node | 17,265,664 |
| `process_rss_avg` | job=mysql-exporter | 16,664,064 |
| `process_rss_avg` | job=mysql-node | 22,132,736 |
| `process_rss_avg` | job=prometheus | 117,706,752 |
| `process_rss_avg` | job=redis-exporter | 17,498,112 |
| `process_rss_avg` | job=redis-node | 23,060,480 |
| `process_rss_max` | job=backend-node | 14,802,944 |
| `process_rss_max` | job=monitoring-node | 17,317,888 |
| `process_rss_max` | job=mysql-exporter | 17,014,784 |
| `process_rss_max` | job=mysql-node | 22,495,232 |
| `process_rss_max` | job=prometheus | 117,952,512 |
| `process_rss_max` | job=redis-exporter | 18,165,760 |
| `process_rss_max` | job=redis-node | 23,060,480 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 91.1 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 80.68 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 13.01 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 21.65 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 61,885.71 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 133.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 39,320,576 |
| `node_mem_available_avg` | job=monitoring-node | 402,995,200 |
| `node_mem_available_avg` | job=mysql-node | 263,659,520 |
| `node_mem_available_avg` | job=redis-node | 562,532,352 |
| `node_swap_free_avg` | job=backend-node | 2,196,547,584 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,578,333,696 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 100,526.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 228.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 86,164.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 428,197.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 5,413.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 94.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 13.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## round6-vt-pure-throughput-sse1000-20260813.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-13T02:35:55.338Z ~ 2026-08-13T02:49:45.382Z
- 설정: `{"sseVUs":1000,"totalSseConnections":2000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-13T02:36:30.338Z ~ 2026-08-13T02:38:30.338Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 880 | 317.76 | 230.49 | 966.02 | 1,948.8 | 4,808.01 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 664 | 170.06 | 56.97 | 549.6 | 1,322.49 | 1,968.76 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,307 | 275.92 | 185.22 | 871.46 | 1,588.06 | 2,037.17 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 863,319.63 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 858,901.71 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 858,399.79 |
| method=GET, status=401, uri=UNKNOWN | 166 | 0.86 | 0.57 | 5.95 | 35.5 | 159.25 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 449.79 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 834.31 |
| method=POST, status=200, uri=/api/sse/tickets | 328 | 11.27 | 0.61 | 13.42 | 181.64 | 33,817.84 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 556 | 498.19 | 216.24 | 952.95 | 7,215.55 | 8,854.91 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 159 | 216.1 | 119.3 | 581.61 | 966.37 | 5,533.52 |
| method=POST, status=401, uri=UNKNOWN | 18 | 6.62 | 0.73 | 51.45 | 55.03 | 117.12 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 13.99 | 14.68 | 15.31 | 15.37 | 13.99 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 16.4 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13.6 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.4 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 89.95 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 11.12 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.07 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 101.8 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.23 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.43 |
| `process_rss_avg` | job=backend-node | 13,234,176 |
| `process_rss_avg` | job=monitoring-node | 17,129,472 |
| `process_rss_avg` | job=mysql-exporter | 16,662,528 |
| `process_rss_avg` | job=mysql-node | 22,152,704 |
| `process_rss_avg` | job=prometheus | 117,985,280 |
| `process_rss_avg` | job=redis-exporter | 17,743,872 |
| `process_rss_avg` | job=redis-node | 23,060,480 |
| `process_rss_max` | job=backend-node | 14,200,832 |
| `process_rss_max` | job=monitoring-node | 17,129,472 |
| `process_rss_max` | job=mysql-exporter | 17,158,144 |
| `process_rss_max` | job=mysql-node | 22,380,544 |
| `process_rss_max` | job=prometheus | 118,083,584 |
| `process_rss_max` | job=redis-exporter | 18,776,064 |
| `process_rss_max` | job=redis-node | 23,060,480 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 984.8 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 877.4 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 30.8 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.4 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 91.63 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 15.22 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 8.27 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 1.51 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 179,180.57 |
| `node_major_fault_delta` | job=monitoring-node | 27.43 |
| `node_major_fault_delta` | job=mysql-node | 30.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 65,163,264 |
| `node_mem_available_avg` | job=monitoring-node | 402,619,904 |
| `node_mem_available_avg` | job=mysql-node | 262,269,952 |
| `node_mem_available_avg` | job=redis-node | 562,532,352 |
| `node_swap_free_avg` | job=backend-node | 2,065,924,608 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,578,395,648 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 283,820.57 |
| `node_swap_in_delta` | job=monitoring-node | 3.43 |
| `node_swap_in_delta` | job=mysql-node | 16 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 145,529.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 57,901.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,474.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 22.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 1.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-13T02:38:30.338Z ~ 2026-08-13T02:40:30.338Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,826 | 15.85 | 13.57 | 26.11 | 77.66 | 4,808.01 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,698 | 7.42 | 6.6 | 11.77 | 27.56 | 1,968.76 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,288 | 15.42 | 13.49 | 23.92 | 68.72 | 2,037.17 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 159.25 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 98.13 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 33,817.84 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,757 | 24.3 | 21.31 | 35.21 | 79.47 | 8,854.91 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 5,533.52 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.73 | 0.6 | 2.27 | 8.81 | 81.58 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 8.99 | 8.04 | 13.63 | 13.91 | 37.04 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 76.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.21 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.52 |
| `process_rss_avg` | job=backend-node | 14,097,920 |
| `process_rss_avg` | job=monitoring-node | 17,266,688 |
| `process_rss_avg` | job=mysql-exporter | 16,620,544 |
| `process_rss_avg` | job=mysql-node | 22,286,336 |
| `process_rss_avg` | job=prometheus | 118,083,584 |
| `process_rss_avg` | job=redis-exporter | 17,894,912 |
| `process_rss_avg` | job=redis-node | 22,859,776 |
| `process_rss_max` | job=backend-node | 15,437,824 |
| `process_rss_max` | job=monitoring-node | 17,391,616 |
| `process_rss_max` | job=mysql-exporter | 17,035,264 |
| `process_rss_max` | job=mysql-node | 22,433,792 |
| `process_rss_max` | job=prometheus | 118,083,584 |
| `process_rss_max` | job=redis-exporter | 18,776,064 |
| `process_rss_max` | job=redis-node | 22,925,312 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 1.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 43.08 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 42.74 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 4.66 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.18 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 44,949.71 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 105.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,631,104 |
| `node_mem_available_avg` | job=monitoring-node | 402,200,064 |
| `node_mem_available_avg` | job=mysql-node | 261,701,632 |
| `node_mem_available_avg` | job=redis-node | 562,532,352 |
| `node_swap_free_avg` | job=backend-node | 2,056,470,016 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,578,399,232 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 65,372.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 92.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 19,753.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 161,395.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 882.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 13.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-13T02:40:30.338Z ~ 2026-08-13T02:42:30.338Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,953 | 39.6 | 15.38 | 208.48 | 470.12 | 2,157.21 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,973 | 17.36 | 7.49 | 49.96 | 246.03 | 1,294.79 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,887 | 37.03 | 15.44 | 161.29 | 444.11 | 2,159.25 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 98.13 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,913 | 45.18 | 26.12 | 172.86 | 382.07 | 2,172.81 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 41 | 146.72 | 145.4 | 311.58 | 481.06 | 632.32 |
| method=POST, status=401, uri=UNKNOWN | 78 | 6.52 | 0.59 | 6.43 | 278.54 | 291.3 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 7 | 7.89 | 7.69 | 10.77 | 11.1 | 13.27 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 113.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.35 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.05 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.45 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.53 |
| `process_rss_avg` | job=backend-node | 13,777,920 |
| `process_rss_avg` | job=monitoring-node | 17,227,264 |
| `process_rss_avg` | job=mysql-exporter | 16,610,816 |
| `process_rss_avg` | job=mysql-node | 22,228,992 |
| `process_rss_avg` | job=prometheus | 118,083,584 |
| `process_rss_avg` | job=redis-exporter | 17,905,152 |
| `process_rss_avg` | job=redis-node | 23,023,616 |
| `process_rss_max` | job=backend-node | 14,557,184 |
| `process_rss_max` | job=monitoring-node | 17,244,160 |
| `process_rss_max` | job=mysql-exporter | 17,137,664 |
| `process_rss_max` | job=mysql-node | 22,458,368 |
| `process_rss_max` | job=prometheus | 118,083,584 |
| `process_rss_max` | job=redis-exporter | 18,591,744 |
| `process_rss_max` | job=redis-node | 23,056,384 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 9.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 62 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 62.46 |
| `node_cpu_pct_avg` | job=redis-node | 0.4 |
| `node_load1_avg` | job=backend-node | 2.64 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 2.31 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 29,218.29 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 169.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 69,880,832 |
| `node_mem_available_avg` | job=monitoring-node | 401,837,568 |
| `node_mem_available_avg` | job=mysql-node | 257,170,944 |
| `node_mem_available_avg` | job=redis-node | 562,232,832 |
| `node_swap_free_avg` | job=backend-node | 2,067,389,440 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,578,399,232 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 38,434.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 266.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 24,387.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 263,038.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 757.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 16 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-13T02:42:30.338Z ~ 2026-08-13T02:44:30.338Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,194 | 53.2 | 45.73 | 119.87 | 191.33 | 2,157.21 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,129 | 23.89 | 19.91 | 51.42 | 105.35 | 531.84 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,486 | 55.89 | 45.99 | 128.34 | 218.49 | 2,159.25 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 2 | 147.66 | 100 | 199.09 | 200.88 | 195.39 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,085 | 76.88 | 65.61 | 162.2 | 269.18 | 2,172.81 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 63 | 52.91 | 37.75 | 128.63 | 189.02 | 485.5 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.8 | 0.6 | 3.32 | 6.5 | 291.3 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 11 | 34.28 | 16.78 | 167.77 | 176.72 | 174.94 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 142.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.32 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.64 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.71 |
| `process_rss_avg` | job=backend-node | 13,420,544 |
| `process_rss_avg` | job=monitoring-node | 17,108,992 |
| `process_rss_avg` | job=mysql-exporter | 16,660,480 |
| `process_rss_avg` | job=mysql-node | 22,295,552 |
| `process_rss_avg` | job=prometheus | 118,083,584 |
| `process_rss_avg` | job=redis-exporter | 17,922,048 |
| `process_rss_avg` | job=redis-node | 22,939,648 |
| `process_rss_max` | job=backend-node | 13,856,768 |
| `process_rss_max` | job=monitoring-node | 17,108,992 |
| `process_rss_max` | job=mysql-exporter | 17,092,608 |
| `process_rss_max` | job=mysql-node | 22,433,792 |
| `process_rss_max` | job=prometheus | 118,083,584 |
| `process_rss_max` | job=redis-exporter | 18,661,376 |
| `process_rss_max` | job=redis-node | 23,154,688 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 10.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 24 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 77.84 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 81.98 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 4.05 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 6.98 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 13,277.71 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 425.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 49,222,144 |
| `node_mem_available_avg` | job=monitoring-node | 402,067,456 |
| `node_mem_available_avg` | job=mysql-node | 254,003,712 |
| `node_mem_available_avg` | job=redis-node | 562,061,312 |
| `node_swap_free_avg` | job=backend-node | 2,064,650,752 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,577,096,704 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 21,033.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 309.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 7,016 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 1,360 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 374,281.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,132.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 43.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 5.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-13T02:44:30.338Z ~ 2026-08-13T02:46:30.338Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,802 | 239.56 | 130.4 | 521.66 | 1,798.75 | 24,650.52 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,898 | 161.77 | 54.35 | 344.68 | 862.57 | 18,960.63 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,886 | 247.19 | 127.6 | 451.68 | 1,323.92 | 24,369.89 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 195.39 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,289 | 308.74 | 175.57 | 459.02 | 1,690.98 | 24,515.65 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 394 | 176.8 | 146.58 | 418.14 | 586.08 | 1,492.94 |
| method=POST, status=401, uri=UNKNOWN | 27 | 6.32 | 1.22 | 26.84 | 84.11 | 74.04 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 161 | 167.77 | 425.02 | 442.92 | 409.84 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.57 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.29 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14.86 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 147.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 15.88 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 100.29 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.63 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.81 |
| `process_rss_avg` | job=backend-node | 14,584,320 |
| `process_rss_avg` | job=monitoring-node | 17,120,256 |
| `process_rss_avg` | job=mysql-exporter | 16,633,856 |
| `process_rss_avg` | job=mysql-node | 22,331,904 |
| `process_rss_avg` | job=prometheus | 118,083,584 |
| `process_rss_avg` | job=redis-exporter | 17,593,344 |
| `process_rss_avg` | job=redis-node | 22,827,008 |
| `process_rss_max` | job=backend-node | 18,108,416 |
| `process_rss_max` | job=monitoring-node | 17,231,872 |
| `process_rss_max` | job=mysql-exporter | 17,055,744 |
| `process_rss_max` | job=mysql-node | 22,618,112 |
| `process_rss_max` | job=prometheus | 118,083,584 |
| `process_rss_max` | job=redis-exporter | 18,350,080 |
| `process_rss_max` | job=redis-node | 22,827,008 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 34.57 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.14 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 95.06 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 52.77 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 8.92 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 10.64 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 119,204.57 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 163.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 54,713,344 |
| `node_mem_available_avg` | job=monitoring-node | 401,988,096 |
| `node_mem_available_avg` | job=mysql-node | 254,616,064 |
| `node_mem_available_avg` | job=redis-node | 562,064,384 |
| `node_swap_free_avg` | job=backend-node | 2,045,031,424 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,575,794,176 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 182,258.29 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 366.86 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 107,270.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 259,978.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 5,501.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 99.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 5.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-13T02:46:30.338Z ~ 2026-08-13T02:48:30.338Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,873 | 219.91 | 197.67 | 422.31 | 745.99 | 24,650.52 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,915 | 138.84 | 109.88 | 319.71 | 512.25 | 18,960.63 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 15,213 | 205.94 | 174.34 | 413.2 | 710.48 | 24,369.89 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,838 | 266.62 | 240.19 | 467.21 | 679.88 | 24,515.65 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,691 | 162.56 | 134.03 | 350.47 | 675.56 | 1,708.04 |
| method=POST, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 74.04 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 163.8 | 167.77 | 177.84 | 178.73 | 409.84 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 474.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 9.71 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.73 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.79 |
| `process_rss_avg` | job=backend-node | 11,801,088 |
| `process_rss_avg` | job=monitoring-node | 17,236,480 |
| `process_rss_avg` | job=mysql-exporter | 16,852,992 |
| `process_rss_avg` | job=mysql-node | 22,410,752 |
| `process_rss_avg` | job=prometheus | 118,607,872 |
| `process_rss_avg` | job=redis-exporter | 17,336,832 |
| `process_rss_avg` | job=redis-node | 22,981,120 |
| `process_rss_max` | job=backend-node | 17,473,536 |
| `process_rss_max` | job=monitoring-node | 17,362,944 |
| `process_rss_max` | job=mysql-exporter | 17,399,808 |
| `process_rss_max` | job=mysql-node | 22,679,552 |
| `process_rss_max` | job=prometheus | 118,607,872 |
| `process_rss_max` | job=redis-exporter | 17,625,088 |
| `process_rss_max` | job=redis-node | 23,089,152 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.25 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 85.95 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 16.44 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 10.92 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 135,114.29 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 156.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 49,678,336 |
| `node_mem_available_avg` | job=monitoring-node | 402,501,632 |
| `node_mem_available_avg` | job=mysql-node | 258,594,304 |
| `node_mem_available_avg` | job=redis-node | 562,069,504 |
| `node_swap_free_avg` | job=backend-node | 2,001,510,912 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,575,797,760 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 188,697.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 258.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 93,842.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 402,580.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,334.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 104 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 12.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## round6-vt-hot-auction-pattern-sse250-20260813.json

- 시나리오: `hot-auction-pattern`
- K6 실행: 2026-08-13T02:50:59.252Z ~ 2026-08-13T02:59:06.201Z
- 설정: `{"auctionCount":200,"hotAuctionCount":3,"hotAuctionRate":14,"coldAuctionRatePerAuction":0.09,"coldAuctionRate":18,"sseUsers":500,"totalSseConnections":1000,"duration":"5m"}`

### 0~1분 — 2026-08-13T02:52:04.252Z ~ 2026-08-13T02:53:04.252Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 1,562 | 1,088.35 | 737.54 | 4,053.38 | 6,565.27 | 7,415.51 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 862,045.94 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 3,945.27 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 853,435.73 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 853,768.13 |
| method=GET, status=400, uri=/api/auctions/stream | 1,655 | 7.5 | 0.78 | 31.85 | 55.41 | 3,813.15 |
| method=GET, status=401, uri=UNKNOWN | 3 | 0.84 | 1 | 1.71 | 1.74 | 1.57 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 99.63 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 2,359.4 |
| method=POST, status=200, uri=/api/sse/tickets | 108 | 205.69 | 4.75 | 1,897.79 | 1,979.56 | 1,886.51 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 180 | 489.55 | 300 | 1,910.57 | 2,369.39 | 2,291.51 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 693 | 308.72 | 260.15 | 695.7 | 1,619.2 | 1,847.99 |
| method=POST, status=401, uri=UNKNOWN | 6 | 21.72 | 1 | 54.81 | 55.7 | 99.13 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 85.18 | 55.92 | 131.98 | 133.77 | 118.23 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14.67 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 49.06 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 18.6 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.14 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.28 |
| `process_rss_avg` | job=backend-node | 13,204,480 |
| `process_rss_avg` | job=monitoring-node | 17,313,792 |
| `process_rss_avg` | job=mysql-exporter | 16,759,808 |
| `process_rss_avg` | job=mysql-node | 22,373,376 |
| `process_rss_avg` | job=prometheus | 118,607,872 |
| `process_rss_avg` | job=redis-exporter | 17,959,936 |
| `process_rss_avg` | job=redis-node | 23,016,448 |
| `process_rss_max` | job=backend-node | 13,942,784 |
| `process_rss_max` | job=monitoring-node | 17,313,792 |
| `process_rss_max` | job=mysql-exporter | 16,904,192 |
| `process_rss_max` | job=mysql-node | 22,519,808 |
| `process_rss_max` | job=prometheus | 118,607,872 |
| `process_rss_max` | job=redis-exporter | 18,292,736 |
| `process_rss_max` | job=redis-node | 23,117,824 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 452 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 498 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 33.67 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 96.37 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.19 |
| `node_cpu_pct_avg` | job=mysql-node | 34.2 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 4.91 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 4.38 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 71,073.33 |
| `node_major_fault_delta` | job=monitoring-node | 1.33 |
| `node_major_fault_delta` | job=mysql-node | 94.67 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 97,272,832 |
| `node_mem_available_avg` | job=monitoring-node | 402,216,960 |
| `node_mem_available_avg` | job=mysql-node | 254,724,096 |
| `node_mem_available_avg` | job=redis-node | 562,098,176 |
| `node_swap_free_avg` | job=backend-node | 1,965,404,160 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,575,798,272 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 140,282.67 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 206.67 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 90,497.33 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 29,996 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 96,618.67 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 509.33 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 9.33 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 9.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 1~2분 — 2026-08-13T02:53:04.252Z ~ 2026-08-13T02:54:04.252Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,365 | 595.96 | 504.15 | 1,402.21 | 2,411.62 | 22,173.92 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 3,945.27 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 1,208 | 6.21 | 0.84 | 10.77 | 155.25 | 3,813.15 |
| method=GET, status=401, uri=UNKNOWN | 3 | 7.52 | 2.8 | 12.44 | 12.55 | 12.42 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 2,359.4 |
| method=POST, status=200, uri=/api/sse/tickets | 3 | 21.27 | 1 | 44.18 | 44.63 | 1,886.51 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 388 | 331.24 | 277.2 | 825.44 | 1,447.76 | 20,419.07 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,487 | 343.95 | 270.56 | 888.32 | 1,280.74 | 20,442.3 |
| method=POST, status=401, uri=UNKNOWN | 5 | 61.63 | 0.67 | 241.59 | 245.17 | 245.72 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 249.39 | 44.74 | 494.74 | 498.95 | 454.74 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 74.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 12.42 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.27 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_rss_avg` | job=backend-node | 13,913,088 |
| `process_rss_avg` | job=monitoring-node | 17,312,768 |
| `process_rss_avg` | job=mysql-exporter | 17,093,632 |
| `process_rss_avg` | job=mysql-node | 22,446,080 |
| `process_rss_avg` | job=prometheus | 118,607,872 |
| `process_rss_avg` | job=redis-exporter | 17,427,456 |
| `process_rss_avg` | job=redis-node | 23,146,496 |
| `process_rss_max` | job=backend-node | 15,855,616 |
| `process_rss_max` | job=monitoring-node | 17,444,864 |
| `process_rss_max` | job=mysql-exporter | 17,727,488 |
| `process_rss_max` | job=mysql-node | 22,716,416 |
| `process_rss_max` | job=prometheus | 118,607,872 |
| `process_rss_max` | job=redis-exporter | 17,481,728 |
| `process_rss_max` | job=redis-node | 23,244,800 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 498.5 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 92.6 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 72.31 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 4.84 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 10.84 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 73,021.33 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 16 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 52,140,032 |
| `node_mem_available_avg` | job=monitoring-node | 401,731,584 |
| `node_mem_available_avg` | job=mysql-node | 252,482,560 |
| `node_mem_available_avg` | job=redis-node | 562,098,176 |
| `node_swap_free_avg` | job=backend-node | 1,929,880,576 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,575,798,272 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 127,013.33 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 14.67 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 85,801.33 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 77,925.33 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 17 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 297,132 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,589.33 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 10.67 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 26.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 2~3분 — 2026-08-13T02:54:04.252Z ~ 2026-08-13T02:55:04.252Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,526 | 547.53 | 520.08 | 1,026.42 | 1,371.06 | 22,173.92 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 670 | 4.98 | 2.84 | 10.55 | 16.27 | 1,858.1 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 12.42 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 42.38 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 217 | 359.87 | 292.11 | 712.19 | 826.33 | 20,419.07 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,647 | 372.66 | 291.11 | 839.38 | 1,231.6 | 20,442.3 |
| method=POST, status=401, uri=UNKNOWN | 3 | 145.27 | 1 | 296.84 | 299.37 | 290.21 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 296.15 | 201.33 | 438.44 | 445.6 | 454.74 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 45.36 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.25 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_rss_avg` | job=backend-node | 14,126,080 |
| `process_rss_avg` | job=monitoring-node | 17,248,256 |
| `process_rss_avg` | job=mysql-exporter | 16,580,608 |
| `process_rss_avg` | job=mysql-node | 22,187,008 |
| `process_rss_avg` | job=prometheus | 118,607,872 |
| `process_rss_avg` | job=redis-exporter | 17,408,000 |
| `process_rss_avg` | job=redis-node | 23,097,344 |
| `process_rss_max` | job=backend-node | 17,244,160 |
| `process_rss_max` | job=monitoring-node | 17,248,256 |
| `process_rss_max` | job=mysql-exporter | 16,691,200 |
| `process_rss_max` | job=mysql-node | 22,249,472 |
| `process_rss_max` | job=prometheus | 118,607,872 |
| `process_rss_max` | job=redis-exporter | 17,440,768 |
| `process_rss_max` | job=redis-node | 23,097,344 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 96.01 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 45.94 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 5.33 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 14.66 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 85,313.33 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 6.67 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 60,260,352 |
| `node_mem_available_avg` | job=monitoring-node | 401,006,592 |
| `node_mem_available_avg` | job=mysql-node | 253,242,368 |
| `node_mem_available_avg` | job=redis-node | 562,098,176 |
| `node_swap_free_avg` | job=backend-node | 1,929,175,040 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,575,798,272 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 136,517.33 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 6.67 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 66,460 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 58,972 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 7 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 190,540 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,024 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 12 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 19.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 3~4분 — 2026-08-13T02:55:04.252Z ~ 2026-08-13T02:56:04.252Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,057 | 974.99 | 487.54 | 1,587.98 | 19,961.1 | 22,173.92 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 823 | 5.5 | 1.5 | 9.1 | 15.14 | 1,168.56 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 12.42 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 42.38 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 215 | 479.19 | 292.11 | 1,014.01 | 1,680.41 | 20,419.07 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,699 | 513.82 | 244.85 | 1,066.78 | 2,598.46 | 20,442.3 |
| method=POST, status=401, uri=UNKNOWN | 3 | 11.09 | 1 | 21.81 | 22.26 | 290.21 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 202.07 | 134.22 | 296.84 | 299.37 | 454.74 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.33 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.67 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 32 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.74 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.36 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.41 |
| `process_rss_avg` | job=backend-node | 12,599,296 |
| `process_rss_avg` | job=monitoring-node | 17,248,256 |
| `process_rss_avg` | job=mysql-exporter | 16,722,944 |
| `process_rss_avg` | job=mysql-node | 22,375,424 |
| `process_rss_avg` | job=prometheus | 118,607,872 |
| `process_rss_avg` | job=redis-exporter | 17,571,840 |
| `process_rss_avg` | job=redis-node | 23,097,344 |
| `process_rss_max` | job=backend-node | 13,565,952 |
| `process_rss_max` | job=monitoring-node | 17,248,256 |
| `process_rss_max` | job=mysql-exporter | 17,006,592 |
| `process_rss_max` | job=mysql-node | 22,790,144 |
| `process_rss_max` | job=prometheus | 118,607,872 |
| `process_rss_max` | job=redis-exporter | 18,194,432 |
| `process_rss_max` | job=redis-node | 23,097,344 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.33 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 92.28 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 49.33 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 4.99 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 14.93 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 87,756 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 17.33 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 48,536,576 |
| `node_mem_available_avg` | job=monitoring-node | 399,980,544 |
| `node_mem_available_avg` | job=mysql-node | 252,395,520 |
| `node_mem_available_avg` | job=redis-node | 562,102,272 |
| `node_swap_free_avg` | job=backend-node | 1,927,142,400 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,575,798,272 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 141,998.67 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 14.67 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 63,234.67 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 47,912 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 18 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 195,400 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 865.33 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 28 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 14.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 4~5분 — 2026-08-13T02:56:04.252Z ~ 2026-08-13T02:57:04.252Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,269 | 529.39 | 434.68 | 1,046.51 | 3,989.27 | 19,761.35 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 1,121 | 10.05 | 0.92 | 19.8 | 129.63 | 1,168.56 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 42.38 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 371 | 350.15 | 262.33 | 778.46 | 3,963.3 | 19,207.19 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,889 | 385.89 | 239.93 | 1,054.02 | 2,485.41 | 19,130.03 |
| method=POST, status=401, uri=UNKNOWN | 5 | 65.16 | 78.29 | 129.74 | 133.32 | 290.21 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 425.22 | 212.51 | 1,306.39 | 1,406.6 | 1,308.86 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 52 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 11.05 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.27 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_rss_avg` | job=backend-node | 12,422,144 |
| `process_rss_avg` | job=monitoring-node | 17,248,256 |
| `process_rss_avg` | job=mysql-exporter | 16,944,128 |
| `process_rss_avg` | job=mysql-node | 22,341,632 |
| `process_rss_avg` | job=prometheus | 118,607,872 |
| `process_rss_avg` | job=redis-exporter | 18,325,504 |
| `process_rss_avg` | job=redis-node | 23,228,416 |
| `process_rss_max` | job=backend-node | 13,090,816 |
| `process_rss_max` | job=monitoring-node | 17,248,256 |
| `process_rss_max` | job=mysql-exporter | 17,477,632 |
| `process_rss_max` | job=mysql-node | 22,458,368 |
| `process_rss_max` | job=prometheus | 118,607,872 |
| `process_rss_max` | job=redis-exporter | 18,325,504 |
| `process_rss_max` | job=redis-node | 23,228,416 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 90.59 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 73.88 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 4.7 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 17.5 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 87,580 |
| `node_major_fault_delta` | job=monitoring-node | 2.67 |
| `node_major_fault_delta` | job=mysql-node | 9.33 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 55,035,904 |
| `node_mem_available_avg` | job=monitoring-node | 399,548,416 |
| `node_mem_available_avg` | job=mysql-node | 251,858,944 |
| `node_mem_available_avg` | job=redis-node | 562,106,368 |
| `node_swap_free_avg` | job=backend-node | 1,924,159,488 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,575,798,272 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 132,989.33 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 12 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 73,369.33 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 77,332 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 3 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 369,924 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,646.67 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 48 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 26.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## round6-vt-bid-only-load-noSSE-20260813.json

- 시나리오: `bid-only-load (SSE 없음)`
- K6 실행: 2026-08-13T02:59:46.426Z ~ 2026-08-13T03:12:04.248Z
- 설정: `{"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m","hotAuctionId":null}`

### QPS 50 — 2026-08-13T02:59:46.426Z ~ 2026-08-13T03:01:46.426Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,126 | 79.5 | 15.34 | 328.5 | 501.11 | 3,382.79 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,161 | 34.11 | 6.76 | 127.51 | 842.89 | 1,533.13 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,465 | 77.32 | 14.46 | 331.06 | 903.63 | 2,200.46 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 46 | 490,665.85 | 30,000 | 30,000 | 30,000 | 530,424.63 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 6,503 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 3,288.53 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,166 | 110.92 | 22.22 | 416.27 | 2,224.57 | 2,379.25 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 54 | 262.95 | 163.58 | 481.59 | 2,421.29 | 4,060.51 |
| method=POST, status=401, uri=UNKNOWN | 66 | 1.41 | 0.57 | 4.96 | 35.9 | 418.84 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 82.57 | 7.69 | 242.71 | 245.39 | 233.74 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.57 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.43 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.86 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 40 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.01 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99.29 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.19 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_rss_avg` | job=backend-node | 13,499,904 |
| `process_rss_avg` | job=monitoring-node | 17,379,328 |
| `process_rss_avg` | job=mysql-exporter | 16,755,712 |
| `process_rss_avg` | job=mysql-node | 22,352,384 |
| `process_rss_avg` | job=prometheus | 137,916,416 |
| `process_rss_avg` | job=redis-exporter | 17,633,280 |
| `process_rss_avg` | job=redis-node | 23,027,712 |
| `process_rss_max` | job=backend-node | 14,123,008 |
| `process_rss_max` | job=monitoring-node | 17,379,328 |
| `process_rss_max` | job=mysql-exporter | 17,182,720 |
| `process_rss_max` | job=mysql-node | 22,540,288 |
| `process_rss_max` | job=prometheus | 142,528,512 |
| `process_rss_max` | job=redis-exporter | 17,666,048 |
| `process_rss_max` | job=redis-node | 23,027,712 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 15 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 48.2 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.41 |
| `node_cpu_pct_avg` | job=mysql-node | 28.47 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 5.5 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 3.17 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 59,721.14 |
| `node_major_fault_delta` | job=monitoring-node | 17.14 |
| `node_major_fault_delta` | job=mysql-node | 331.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 77,325,824 |
| `node_mem_available_avg` | job=monitoring-node | 389,217,280 |
| `node_mem_available_avg` | job=mysql-node | 244,908,032 |
| `node_mem_available_avg` | job=redis-node | 566,183,424 |
| `node_swap_free_avg` | job=backend-node | 1,871,257,600 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,494,592 |
| `node_swap_free_avg` | job=mysql-node | 2,575,900,160 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 115,096 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 1,085.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 87,429.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 100,547.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,598.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 656 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-13T03:01:46.426Z ~ 2026-08-13T03:03:46.426Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,786 | 14.05 | 13.3 | 20.71 | 31.24 | 3,382.79 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,760 | 6.62 | 6.28 | 9.49 | 14.71 | 1,533.13 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,319 | 14.34 | 13.52 | 20.99 | 35.87 | 2,200.46 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 530,424.63 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 3,288.53 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,767 | 23.11 | 21.27 | 31.48 | 60.87 | 2,379.25 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1 | 8.07 | 7.69 | 8.32 | 8.37 | 2,398.42 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.33 | 0.5 | 0.95 | 0.99 | 196.43 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 9.17 | 7.34 | 20.97 | 22.09 | 233.74 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 18.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.32 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.21 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.27 |
| `process_rss_avg` | job=backend-node | 13,838,336 |
| `process_rss_avg` | job=monitoring-node | 17,341,952 |
| `process_rss_avg` | job=mysql-exporter | 16,811,520 |
| `process_rss_avg` | job=mysql-node | 22,347,264 |
| `process_rss_avg` | job=prometheus | 136,826,880 |
| `process_rss_avg` | job=redis-exporter | 17,829,888 |
| `process_rss_avg` | job=redis-node | 22,883,328 |
| `process_rss_max` | job=backend-node | 13,971,456 |
| `process_rss_max` | job=monitoring-node | 17,379,328 |
| `process_rss_max` | job=mysql-exporter | 17,412,096 |
| `process_rss_max` | job=mysql-node | 22,798,336 |
| `process_rss_max` | job=prometheus | 143,052,800 |
| `process_rss_max` | job=redis-exporter | 17,928,192 |
| `process_rss_max` | job=redis-node | 23,019,520 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 1.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 27.18 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 42 |
| `node_cpu_pct_avg` | job=redis-node | 0.39 |
| `node_load1_avg` | job=backend-node | 1.47 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 1.51 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 4,561.14 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 33.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 107,032,064 |
| `node_mem_available_avg` | job=monitoring-node | 384,230,400 |
| `node_mem_available_avg` | job=mysql-node | 246,829,056 |
| `node_mem_available_avg` | job=redis-node | 567,966,720 |
| `node_swap_free_avg` | job=backend-node | 1,932,994,048 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,513,024 |
| `node_swap_free_avg` | job=mysql-node | 2,576,613,376 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 6,700.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 9.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 153,889.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-13T03:03:46.426Z ~ 2026-08-13T03:05:46.426Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,872 | 139.94 | 18.17 | 330.29 | 1,730.93 | 6,033.22 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,905 | 67.82 | 8.34 | 200.78 | 400.57 | 8,748.55 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,662 | 109.56 | 18.7 | 307.76 | 562.13 | 7,299.77 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,597 | 105.64 | 27.43 | 319.8 | 460.67 | 5,420.23 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 271 | 142.67 | 129.34 | 321.85 | 416.75 | 448.58 |
| method=POST, status=401, uri=UNKNOWN | 62 | 3.45 | 0.61 | 3.25 | 83.44 | 85.82 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 10 | 33.53 | 6.57 | 124.15 | 132.2 | 130.33 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 46.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.61 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.36 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.59 |
| `process_rss_avg` | job=backend-node | 13,342,720 |
| `process_rss_avg` | job=monitoring-node | 17,195,008 |
| `process_rss_avg` | job=mysql-exporter | 16,534,528 |
| `process_rss_avg` | job=mysql-node | 22,332,928 |
| `process_rss_avg` | job=prometheus | 122,954,240 |
| `process_rss_avg` | job=redis-exporter | 17,885,184 |
| `process_rss_avg` | job=redis-node | 22,976,512 |
| `process_rss_max` | job=backend-node | 14,196,736 |
| `process_rss_max` | job=monitoring-node | 17,211,392 |
| `process_rss_max` | job=mysql-exporter | 17,113,088 |
| `process_rss_max` | job=mysql-node | 22,450,176 |
| `process_rss_max` | job=prometheus | 135,974,912 |
| `process_rss_max` | job=redis-exporter | 17,928,192 |
| `process_rss_max` | job=redis-node | 23,126,016 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 17.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 66.75 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.24 |
| `node_cpu_pct_avg` | job=mysql-node | 59.24 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 2.66 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 3.54 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 67,955.43 |
| `node_major_fault_delta` | job=monitoring-node | 5.71 |
| `node_major_fault_delta` | job=mysql-node | 227.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 62,978,560 |
| `node_mem_available_avg` | job=monitoring-node | 390,635,008 |
| `node_mem_available_avg` | job=mysql-node | 247,050,240 |
| `node_mem_available_avg` | job=redis-node | 567,975,936 |
| `node_swap_free_avg` | job=backend-node | 1,956,409,344 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,525,312 |
| `node_swap_free_avg` | job=mysql-node | 2,576,613,376 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 130,796.57 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 208 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 50,856 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 249,217.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 5,406.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 86.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 5.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-13T03:05:46.426Z ~ 2026-08-13T03:07:46.426Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,171 | 21.07 | 18.68 | 40.88 | 64.14 | 6,033.22 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,174 | 9.55 | 8.31 | 18.64 | 31.74 | 8,748.55 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,520 | 22.96 | 19.57 | 48.03 | 85.29 | 7,299.77 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,135 | 35.98 | 31.23 | 68.7 | 108.51 | 5,420.23 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 27 | 13.05 | 10.72 | 25.73 | 27.51 | 448.58 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.58 | 0.58 | 1.66 | 3.6 | 85.82 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 10 | 8.59 | 8.04 | 13.35 | 13.86 | 130.33 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 56 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.47 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.5 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.58 |
| `process_rss_avg` | job=backend-node | 14,021,120 |
| `process_rss_avg` | job=monitoring-node | 17,326,080 |
| `process_rss_avg` | job=mysql-exporter | 16,958,976 |
| `process_rss_avg` | job=mysql-node | 22,234,624 |
| `process_rss_avg` | job=prometheus | 121,290,752 |
| `process_rss_avg` | job=redis-exporter | 17,511,936 |
| `process_rss_avg` | job=redis-node | 22,923,264 |
| `process_rss_max` | job=backend-node | 14,565,376 |
| `process_rss_max` | job=monitoring-node | 17,342,464 |
| `process_rss_max` | job=mysql-exporter | 17,215,488 |
| `process_rss_max` | job=mysql-node | 22,323,200 |
| `process_rss_max` | job=prometheus | 121,487,360 |
| `process_rss_max` | job=redis-exporter | 17,715,200 |
| `process_rss_max` | job=redis-node | 22,978,560 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 60.35 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 84.75 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 2.21 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 4.26 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 3,586.29 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 30.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 74,149,376 |
| `node_mem_available_avg` | job=monitoring-node | 393,253,376 |
| `node_mem_available_avg` | job=mysql-node | 251,279,360 |
| `node_mem_available_avg` | job=redis-node | 568,000,512 |
| `node_swap_free_avg` | job=backend-node | 1,975,435,264 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,529,408 |
| `node_swap_free_avg` | job=mysql-node | 2,576,613,376 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 5,339.43 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 18.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 421.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 376,049.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 150.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 21.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-13T03:07:46.426Z ~ 2026-08-13T03:09:46.426Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,518 | 160.41 | 154.3 | 297.58 | 383.27 | 698.48 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,418 | 104.51 | 98.79 | 240.59 | 322.88 | 561.03 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 16,223 | 185.51 | 180.78 | 331.18 | 436.02 | 752.72 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,851 | 219.57 | 216.66 | 373.11 | 460.67 | 692.56 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,106 | 132.56 | 124.79 | 275.04 | 345.73 | 472.46 |
| method=POST, status=401, uri=UNKNOWN | 55 | 1.42 | 1 | 5.03 | 7.72 | 8.29 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 23 | 74.08 | 41.94 | 190.14 | 199.09 | 192.4 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 72 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.31 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.07 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.64 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.68 |
| `process_rss_avg` | job=backend-node | 14,326,784 |
| `process_rss_avg` | job=monitoring-node | 17,342,464 |
| `process_rss_avg` | job=mysql-exporter | 16,611,328 |
| `process_rss_avg` | job=mysql-node | 22,447,104 |
| `process_rss_avg` | job=prometheus | 121,683,968 |
| `process_rss_avg` | job=redis-exporter | 17,956,864 |
| `process_rss_avg` | job=redis-node | 23,126,016 |
| `process_rss_max` | job=backend-node | 14,548,992 |
| `process_rss_max` | job=monitoring-node | 17,342,464 |
| `process_rss_max` | job=mysql-exporter | 17,141,760 |
| `process_rss_max` | job=mysql-node | 22,966,272 |
| `process_rss_max` | job=prometheus | 121,749,504 |
| `process_rss_max` | job=redis-exporter | 18,137,088 |
| `process_rss_max` | job=redis-node | 23,228,416 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 42.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 75.48 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 98.72 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 3.84 |
| `node_load1_avg` | job=monitoring-node | 0.06 |
| `node_load1_avg` | job=mysql-node | 14.48 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 3,636.57 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 370.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 75,043,328 |
| `node_mem_available_avg` | job=monitoring-node | 394,144,768 |
| `node_mem_available_avg` | job=mysql-node | 243,252,736 |
| `node_mem_available_avg` | job=redis-node | 567,846,912 |
| `node_swap_free_avg` | job=backend-node | 1,977,502,208 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,531,456 |
| `node_swap_free_avg` | job=mysql-node | 2,576,606,720 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 12,930.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 433.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 2,710.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 9.14 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 455,176 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 10,435.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 196.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 17 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-13T03:09:46.426Z ~ 2026-08-13T03:11:46.426Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,967 | 172.28 | 160.55 | 290.95 | 389.42 | 1,799.41 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,800 | 110 | 101.18 | 234.84 | 318.45 | 1,723.12 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 17,553 | 192.56 | 182.18 | 326.21 | 429.72 | 1,797.62 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,221 | 229.38 | 218.6 | 356.42 | 444.49 | 1,825.77 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 3,358 | 122.49 | 112.14 | 244.55 | 337.88 | 1,600.56 |
| method=POST, status=401, uri=UNKNOWN | 21 | 3.17 | 1.4 | 11.32 | 12.33 | 11.71 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 89.88 | 78.29 | 174.48 | 178.06 | 192.4 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 98.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.7 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.05 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.69 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.71 |
| `process_rss_avg` | job=backend-node | 13,137,920 |
| `process_rss_avg` | job=monitoring-node | 17,276,416 |
| `process_rss_avg` | job=mysql-exporter | 16,535,040 |
| `process_rss_avg` | job=mysql-node | 22,304,256 |
| `process_rss_avg` | job=prometheus | 121,749,504 |
| `process_rss_avg` | job=redis-exporter | 18,333,696 |
| `process_rss_avg` | job=redis-node | 23,232,512 |
| `process_rss_max` | job=backend-node | 13,414,400 |
| `process_rss_max` | job=monitoring-node | 17,342,464 |
| `process_rss_max` | job=mysql-exporter | 17,080,320 |
| `process_rss_max` | job=mysql-node | 22,458,368 |
| `process_rss_max` | job=prometheus | 121,749,504 |
| `process_rss_max` | job=redis-exporter | 18,399,232 |
| `process_rss_max` | job=redis-node | 23,482,368 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 81.69 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.22 |
| `node_cpu_pct_avg` | job=mysql-node | 97.36 |
| `node_cpu_pct_avg` | job=redis-node | 0.4 |
| `node_load1_avg` | job=backend-node | 5.9 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 24.88 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 19,035.43 |
| `node_major_fault_delta` | job=monitoring-node | 5.71 |
| `node_major_fault_delta` | job=mysql-node | 85.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 63,440,896 |
| `node_mem_available_avg` | job=monitoring-node | 395,604,992 |
| `node_mem_available_avg` | job=mysql-node | 244,826,112 |
| `node_mem_available_avg` | job=redis-node | 565,202,432 |
| `node_swap_free_avg` | job=backend-node | 1,975,459,840 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,531,456 |
| `node_swap_free_avg` | job=mysql-node | 2,576,730,624 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 32,456 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 83.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 17,534.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 441,720 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 8,612.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 125.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 16.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## round6-vt-bid-only-load-singleHotAuction-20260813.json

- 시나리오: `bid-only-load (SSE 없음)`
- K6 실행: 2026-08-13T03:12:33.806Z ~ 2026-08-13T03:24:45.785Z
- 설정: `{"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m","hotAuctionId":3001001}`

### QPS 50 — 2026-08-13T03:12:33.806Z ~ 2026-08-13T03:14:33.806Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,072 | 18.14 | 15.1 | 37.12 | 76.88 | 1,799.41 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,143 | 7.38 | 7.06 | 11.04 | 14.45 | 1,723.12 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,321 | 65.33 | 56.15 | 88.5 | 373.69 | 1,797.62 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 431.41 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 869 | 29.56 | 26.9 | 39.03 | 63.75 | 1,825.77 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 238 | 14.23 | 8.51 | 49.59 | 89.12 | 1,600.56 |
| method=POST, status=401, uri=UNKNOWN | 75 | 0.32 | 0.5 | 0.95 | 0.99 | 11.71 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 192.38 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 14.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.13 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.14 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.15 |
| `process_rss_avg` | job=backend-node | 13,735,936 |
| `process_rss_avg` | job=monitoring-node | 17,166,336 |
| `process_rss_avg` | job=mysql-exporter | 16,591,872 |
| `process_rss_avg` | job=mysql-node | 22,221,824 |
| `process_rss_avg` | job=prometheus | 121,749,504 |
| `process_rss_avg` | job=redis-exporter | 17,572,352 |
| `process_rss_avg` | job=redis-node | 23,165,952 |
| `process_rss_max` | job=backend-node | 13,873,152 |
| `process_rss_max` | job=monitoring-node | 17,166,336 |
| `process_rss_max` | job=mysql-exporter | 16,977,920 |
| `process_rss_max` | job=mysql-node | 22,663,168 |
| `process_rss_max` | job=prometheus | 121,749,504 |
| `process_rss_max` | job=redis-exporter | 17,637,376 |
| `process_rss_max` | job=redis-node | 23,289,856 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 5.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 27 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 25.93 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.23 |
| `node_cpu_pct_avg` | job=mysql-node | 80.4 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 1.72 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 8 |
| `node_load1_avg` | job=redis-node | 0.02 |
| `node_major_fault_delta` | job=backend-node | 19,533.71 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 272 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 74,614,784 |
| `node_mem_available_avg` | job=monitoring-node | 396,817,920 |
| `node_mem_available_avg` | job=mysql-node | 248,357,376 |
| `node_mem_available_avg` | job=redis-node | 562,049,024 |
| `node_swap_free_avg` | job=backend-node | 1,974,030,336 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,531,456 |
| `node_swap_free_avg` | job=mysql-node | 2,576,687,104 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 32,062.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 13.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 22,369.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 163.43 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 93,680 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 297.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 5.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-13T03:14:33.806Z ~ 2026-08-13T03:16:33.806Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,647 | 362.03 | 350.66 | 661.61 | 784.96 | 1,022.42 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,430 | 298.35 | 293.31 | 592.11 | 713.84 | 1,208.39 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,565 | 970.65 | 968.39 | 1,352.73 | 1,428.37 | 11,501.47 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 431.41 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 17 | 448.24 | 473.7 | 682.27 | 709.12 | 11,583.71 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,416 | 378.36 | 370.2 | 669.4 | 801.82 | 1,167.94 |
| method=POST, status=401, uri=UNKNOWN | 27 | 0.6 | 0.57 | 2.38 | 4.11 | 8.54 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 18.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.36 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.07 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.16 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_rss_avg` | job=backend-node | 13,562,368 |
| `process_rss_avg` | job=monitoring-node | 17,166,336 |
| `process_rss_avg` | job=mysql-exporter | 16,491,520 |
| `process_rss_avg` | job=mysql-node | 22,228,480 |
| `process_rss_avg` | job=prometheus | 121,749,504 |
| `process_rss_avg` | job=redis-exporter | 18,034,688 |
| `process_rss_avg` | job=redis-node | 22,994,944 |
| `process_rss_max` | job=backend-node | 13,799,424 |
| `process_rss_max` | job=monitoring-node | 17,166,336 |
| `process_rss_max` | job=mysql-exporter | 16,982,016 |
| `process_rss_max` | job=mysql-node | 22,654,976 |
| `process_rss_max` | job=prometheus | 121,749,504 |
| `process_rss_max` | job=redis-exporter | 18,264,064 |
| `process_rss_max` | job=redis-node | 23,252,992 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 21.81 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 100 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 0.73 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 18.07 |
| `node_load1_avg` | job=redis-node | 0.02 |
| `node_major_fault_delta` | job=backend-node | 3,698.29 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 4.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 42,414,592 |
| `node_mem_available_avg` | job=monitoring-node | 396,843,008 |
| `node_mem_available_avg` | job=mysql-node | 244,079,616 |
| `node_mem_available_avg` | job=redis-node | 562,054,144 |
| `node_swap_free_avg` | job=backend-node | 1,974,618,624 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,531,456 |
| `node_swap_free_avg` | job=mysql-node | 2,576,564,224 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 10,970.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 4.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 965.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 98,987.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 51,837.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 781.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 27.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-13T03:16:33.806Z ~ 2026-08-13T03:18:33.806Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,248 | 643.95 | 390.21 | 841.1 | 10,584.71 | 11,132.75 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,040 | 339.17 | 294.3 | 605.25 | 870.18 | 11,304.89 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,493 | 1,292.68 | 1,001.31 | 1,503.24 | 11,060.44 | 12,166.01 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3 | 634.18 | 671.09 | 881.36 | 892.1 | 11,583.71 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 744 | 438.07 | 389.33 | 736.78 | 976.66 | 10,740.39 |
| method=POST, status=401, uri=UNKNOWN | 5 | 1.71 | 1 | 5.31 | 5.54 | 4.36 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 415.71 | 357.91 | 533.18 | 536.13 | 693.25 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 24 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.73 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 100.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.15 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.22 |
| `process_rss_avg` | job=backend-node | 13,137,920 |
| `process_rss_avg` | job=monitoring-node | 17,264,640 |
| `process_rss_avg` | job=mysql-exporter | 16,384,000 |
| `process_rss_avg` | job=mysql-node | 22,195,200 |
| `process_rss_avg` | job=prometheus | 121,880,576 |
| `process_rss_avg` | job=redis-exporter | 17,627,136 |
| `process_rss_avg` | job=redis-node | 23,007,232 |
| `process_rss_max` | job=backend-node | 13,668,352 |
| `process_rss_max` | job=monitoring-node | 17,297,408 |
| `process_rss_max` | job=mysql-exporter | 16,994,304 |
| `process_rss_max` | job=mysql-node | 22,274,048 |
| `process_rss_max` | job=prometheus | 121,880,576 |
| `process_rss_max` | job=redis-exporter | 18,264,064 |
| `process_rss_max` | job=redis-node | 23,220,224 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 55.43 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 77.32 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 1.61 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 23.53 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 87,371.43 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 35.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 49,463,296 |
| `node_mem_available_avg` | job=monitoring-node | 396,981,760 |
| `node_mem_available_avg` | job=mysql-node | 243,764,224 |
| `node_mem_available_avg` | job=redis-node | 562,067,456 |
| `node_swap_free_avg` | job=backend-node | 1,968,411,648 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,531,456 |
| `node_swap_free_avg` | job=mysql-node | 2,576,564,224 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 175,115.43 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 5.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 84,352 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 74,614.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 42,008 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 449.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 1.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 18.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-13T03:18:33.806Z ~ 2026-08-13T03:20:33.806Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,677 | 359.63 | 348.98 | 654.63 | 781.43 | 11,132.75 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,344 | 292.74 | 283.29 | 601.21 | 753.77 | 11,304.89 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,458 | 958.97 | 968.42 | 1,371.01 | 1,504.63 | 12,166.01 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 2 | 49.71 | 1 | 98.95 | 99.79 | 98.84 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3 | 395.04 | 402.65 | 612.93 | 623.67 | 892.99 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,594 | 451.81 | 426.08 | 792.19 | 944.99 | 10,740.39 |
| method=POST, status=401, uri=UNKNOWN | 5 | 3.69 | 0.67 | 13.7 | 13.93 | 13.21 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 346.09 | 402.65 | 531.34 | 535.76 | 515.21 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 19.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.59 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.07 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.17 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.2 |
| `process_rss_avg` | job=backend-node | 15,681,536 |
| `process_rss_avg` | job=monitoring-node | 17,297,408 |
| `process_rss_avg` | job=mysql-exporter | 16,480,768 |
| `process_rss_avg` | job=mysql-node | 22,301,184 |
| `process_rss_avg` | job=prometheus | 121,573,376 |
| `process_rss_avg` | job=redis-exporter | 17,440,768 |
| `process_rss_avg` | job=redis-node | 23,105,024 |
| `process_rss_max` | job=backend-node | 16,801,792 |
| `process_rss_max` | job=monitoring-node | 17,297,408 |
| `process_rss_max` | job=mysql-exporter | 17,059,840 |
| `process_rss_max` | job=mysql-node | 22,695,936 |
| `process_rss_max` | job=prometheus | 121,880,576 |
| `process_rss_max` | job=redis-exporter | 17,604,608 |
| `process_rss_max` | job=redis-node | 23,220,224 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 25.3 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.22 |
| `node_cpu_pct_avg` | job=mysql-node | 99.9 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 0.82 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 27.61 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 17,345.14 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 32 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 58,455,040 |
| `node_mem_available_avg` | job=monitoring-node | 398,210,560 |
| `node_mem_available_avg` | job=mysql-node | 242,211,328 |
| `node_mem_available_avg` | job=redis-node | 562,073,600 |
| `node_swap_free_avg` | job=backend-node | 1,963,933,696 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,531,456 |
| `node_swap_free_avg` | job=mysql-node | 2,576,564,224 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 25,308.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 3.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 11,485.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 98,292.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 11 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 209,782.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,125.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 27.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-13T03:20:33.806Z ~ 2026-08-13T03:22:33.806Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,306 | 368.07 | 353.07 | 668.75 | 811.68 | 1,149.33 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,646 | 299.49 | 290.69 | 602.49 | 721.79 | 1,091.55 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,493 | 977.18 | 985.92 | 1,381.09 | 1,539.65 | 1,858.97 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 98.84 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 5 | 529.28 | 536.87 | 617.4 | 624.56 | 710.98 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,443 | 442.81 | 421.56 | 795.98 | 954.67 | 1,208.55 |
| method=POST, status=401, uri=UNKNOWN | 7 | 3.25 | 0.75 | 20.69 | 22.03 | 16.93 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 517.58 | 473.7 | 791.88 | 802.62 | 717.26 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.55 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.09 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.16 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.19 |
| `process_rss_avg` | job=backend-node | 13,124,096 |
| `process_rss_avg` | job=monitoring-node | 17,221,632 |
| `process_rss_avg` | job=mysql-exporter | 16,756,224 |
| `process_rss_avg` | job=mysql-node | 22,195,200 |
| `process_rss_avg` | job=prometheus | 121,300,992 |
| `process_rss_avg` | job=redis-exporter | 17,348,608 |
| `process_rss_avg` | job=redis-node | 23,052,288 |
| `process_rss_max` | job=backend-node | 13,590,528 |
| `process_rss_max` | job=monitoring-node | 17,297,408 |
| `process_rss_max` | job=mysql-exporter | 17,010,688 |
| `process_rss_max` | job=mysql-node | 22,552,576 |
| `process_rss_max` | job=prometheus | 121,700,352 |
| `process_rss_max` | job=redis-exporter | 17,547,264 |
| `process_rss_max` | job=redis-node | 23,166,976 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 23.2 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.22 |
| `node_cpu_pct_avg` | job=mysql-node | 100 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 0.65 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 27.79 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 11,970.29 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 2.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 57,951,744 |
| `node_mem_available_avg` | job=monitoring-node | 400,192,512 |
| `node_mem_available_avg` | job=mysql-node | 241,104,384 |
| `node_mem_available_avg` | job=redis-node | 562,087,936 |
| `node_swap_free_avg` | job=backend-node | 1,962,197,504 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,531,456 |
| `node_swap_free_avg` | job=mysql-node | 2,576,564,224 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 15,770.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 2.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 6,254.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 97,003.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 2 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 168,552 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,018.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 27.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-13T03:22:33.806Z ~ 2026-08-13T03:24:33.806Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,678 | 343.61 | 334.48 | 638.16 | 784.37 | 1,180.12 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,355 | 301.67 | 291.21 | 616.04 | 788.25 | 1,170.88 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,454 | 949.54 | 963.37 | 1,372.78 | 1,479.62 | 1,858.97 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 5 | 546.13 | 536.87 | 697.93 | 712.25 | 644.94 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,592 | 485 | 455.14 | 833.69 | 964.73 | 1,208.55 |
| method=POST, status=401, uri=UNKNOWN | 7 | 0.59 | 0.6 | 1.29 | 1.38 | 16.93 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 457.16 | 402.65 | 782.94 | 800.83 | 748.76 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.51 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.08 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 100.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.17 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_rss_avg` | job=backend-node | 13,344,768 |
| `process_rss_avg` | job=monitoring-node | 17,260,544 |
| `process_rss_avg` | job=mysql-exporter | 16,484,864 |
| `process_rss_avg` | job=mysql-node | 22,015,488 |
| `process_rss_avg` | job=prometheus | 120,889,344 |
| `process_rss_avg` | job=redis-exporter | 18,309,120 |
| `process_rss_avg` | job=redis-node | 22,867,968 |
| `process_rss_max` | job=backend-node | 13,459,456 |
| `process_rss_max` | job=monitoring-node | 17,408,000 |
| `process_rss_max` | job=mysql-exporter | 16,924,672 |
| `process_rss_max` | job=mysql-node | 22,126,592 |
| `process_rss_max` | job=prometheus | 120,889,344 |
| `process_rss_max` | job=redis-exporter | 18,751,488 |
| `process_rss_max` | job=redis-node | 22,867,968 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 23.96 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.22 |
| `node_cpu_pct_avg` | job=mysql-node | 100 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 0.55 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 28.24 |
| `node_load1_avg` | job=redis-node | 0.09 |
| `node_major_fault_delta` | job=backend-node | 11,414.86 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 5.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 49,671,168 |
| `node_mem_available_avg` | job=monitoring-node | 400,661,504 |
| `node_mem_available_avg` | job=mysql-node | 240,630,784 |
| `node_mem_available_avg` | job=redis-node | 562,102,272 |
| `node_swap_free_avg` | job=backend-node | 1,960,850,432 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,531,456 |
| `node_swap_free_avg` | job=mysql-node | 2,576,564,224 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 14,741.71 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 3.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 9,746.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 98,649.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 14 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 254,283.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,190.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 27.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

> 이 문서는 codex의 도움을 받아 작성하였습니다
