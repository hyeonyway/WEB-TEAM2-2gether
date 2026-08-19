# 8차 부하테스트 — Prometheus 원시 집계 데이터

이 문서는 K6 결과 JSON의 실제 종료 시각과 실행 시간을 기준으로 stage를 재구성하고, 각 구간 끝 시점에 Prometheus range/vector query를 평가해 만든 원시 집계표다. p50/p95/p99는 서버의 `http_server_requests_seconds_bucket` histogram으로 계산했다. 값은 Prometheus 원 단위(시간은 ms 변환)를 유지하며, `N/A`/빈 표는 그 시간대에 해당 시계열이 없었음을 뜻한다.

수집 범위는 테스트 대상 백엔드, backend/mysql/redis node exporter, MySQL exporter, Redis exporter다. Grafana/Prometheus 자기 관측 메트릭과 정적 build/info/config 시계열은 성능 측정값이 아니므로 제외했다.

## 실행 목록

| 결과 파일 | 시나리오 | 실제 실행 (UTC) | K6 전체 | 평균 지연 | med | p95 | p99 | max |
|---|---|---|---:|---:|---:|---:|---:|---:|
| [`round8-ram2gb-pure-throughput-sse250-20260813.json`](../../../../backend/src/test/k6/result/round8-ram2gb-pure-throughput-sse250-20260813.json) | pure-throughput | 2026-08-13T06:35:18.745Z ~ 2026-08-13T06:49:00.996Z | 128,811 | 156.66 req/s | 3,797.06 | 428.25 | 9,612.67 | 22,565.08 | 60,022.35 |
| [`round8-ram2gb-pure-throughput-sse500-20260813.json`](../../../../backend/src/test/k6/result/round8-ram2gb-pure-throughput-sse500-20260813.json) | pure-throughput | 2026-08-13T06:49:27.776Z ~ 2026-08-13T07:03:09.541Z | 128,826 | 156.77 req/s | 4,117.31 | 962.04 | 10,011.92 | 27,490.35 | 60,005.91 |
| [`round8-ram2gb-pure-throughput-sse1000-20260813.json`](../../../../backend/src/test/k6/result/round8-ram2gb-pure-throughput-sse1000-20260813.json) | pure-throughput | 2026-08-13T07:03:36.904Z ~ 2026-08-13T07:17:19.867Z | 134,229 | 163.1 req/s | 3,813.99 | 1,623.74 | 14,541.86 | 33,982.45 | 60,109.31 |
| [`round8-ram2gb-hot-auction-pattern-sse250-20260813.json`](../../../../backend/src/test/k6/result/round8-ram2gb-hot-auction-pattern-sse250-20260813.json) | hot-auction-pattern | 2026-08-13T07:17:51.216Z ~ 2026-08-13T07:25:53.560Z | 152,821 | 316.83 req/s | 2,539.86 | 1,063.55 | 6,047.93 | 6,425.13 | 10,467.6 |
| [`round8-ram2gb-bid-only-load-noSSE-20260813.json`](../../../../backend/src/test/k6/result/round8-ram2gb-bid-only-load-noSSE-20260813.json) | bid-only-load (SSE 없음) | 2026-08-13T07:26:23.739Z ~ 2026-08-13T07:38:35.290Z | 118,930 | 162.57 req/s | 5,096.14 | 1,827.28 | 12,863.91 | 13,202.13 | 14,543.15 |
| [`round8-ram2gb-bid-only-load-singleHotAuction-20260813.json`](../../../../backend/src/test/k6/result/round8-ram2gb-bid-only-load-singleHotAuction-20260813.json) | bid-only-load (SSE 없음) | 2026-08-13T07:39:10.113Z ~ 2026-08-13T07:51:21.639Z | 49,626 | 67.84 req/s | 29,200.6 | 40,167.18 | 52,505.7 | 56,579.12 | 60,036.89 |

---

## round8-ram2gb-pure-throughput-sse250-20260813.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-13T06:35:18.745Z ~ 2026-08-13T06:49:00.996Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-13T06:35:53.745Z ~ 2026-08-13T06:37:53.745Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,256 | 160.4 | 20.56 | 1,044.17 | 1,449.01 | 2,007.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 994 | 10.21 | 9.82 | 18.94 | 22.14 | 693.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,509 | 76.81 | 20.11 | 526.78 | 997.5 | 1,663.21 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 10 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 457.49 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 16.6 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,158 | 122.34 | 32.21 | 902.09 | 1,663.39 | 2,321.79 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 1,069.41 |
| method=POST, status=401, uri=UNKNOWN | 80 | 2.62 | 0.67 | 19.57 | 40.82 | 43.66 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 4 | 9.11 | 9.15 | 10.33 | 11.01 | 11.21 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 13.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.21 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.39 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 94 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 95 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.44 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.9 |
| `process_rss_avg` | job=backend-node | 20,803,584 |
| `process_rss_avg` | job=monitoring-node | 17,051,648 |
| `process_rss_avg` | job=mysql-exporter | 16,520,704 |
| `process_rss_avg` | job=mysql-node | 22,223,872 |
| `process_rss_avg` | job=prometheus | 101,482,496 |
| `process_rss_max` | job=backend-node | 21,417,984 |
| `process_rss_max` | job=monitoring-node | 17,084,416 |
| `process_rss_max` | job=mysql-exporter | 16,949,248 |
| `process_rss_max` | job=mysql-node | 22,429,696 |
| `process_rss_max` | job=prometheus | 101,482,496 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 6.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 35 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 49.38 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 52.05 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.24 |
| `node_cpu_pct_avg` | job=mysql-node | 35.58 |
| `node_load1_avg` | job=backend-node | 3.67 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 0.65 |
| `node_major_fault_delta` | job=backend-node | 2,257.14 |
| `node_major_fault_delta` | job=monitoring-node | 8 |
| `node_major_fault_delta` | job=mysql-node | 563.43 |
| `node_mem_available_avg` | job=backend-node | 407,023,616 |
| `node_mem_available_avg` | job=monitoring-node | 405,781,504 |
| `node_mem_available_avg` | job=mysql-node | 267,972,608 |
| `node_swap_free_avg` | job=backend-node | 3,170,400,256 |
| `node_swap_free_avg` | job=monitoring-node | 3,010,101,248 |
| `node_swap_free_avg` | job=mysql-node | 2,572,611,584 |
| `node_swap_in_delta` | job=backend-node | 8,582.35 |
| `node_swap_in_delta` | job=monitoring-node | 3.43 |
| `node_swap_in_delta` | job=mysql-node | 1,070.86 |
| `node_swap_out_delta` | job=backend-node | 1,806.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 6,622.86 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 112,925.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 100 — 2026-08-13T06:37:53.745Z ~ 2026-08-13T06:39:53.745Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,760 | 16.86 | 15.92 | 22.06 | 46.42 | 2,007.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,751 | 8.49 | 8.24 | 10.91 | 14.17 | 693.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,265 | 17.34 | 16.43 | 22.21 | 33.4 | 1,663.21 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 10 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 457.49 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 16.6 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,742 | 28.73 | 26.9 | 34.3 | 53.42 | 2,321.79 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 5 | 8.59 | 8.46 | 9.79 | 174.47 | 1,069.41 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.44 | 0.51 | 0.98 | 1.85 | 43.66 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 8.79 | 8.78 | 10.63 | 11.07 | 11.21 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 18.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.31 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 95 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.26 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_rss_avg` | job=backend-node | 20,447,232 |
| `process_rss_avg` | job=monitoring-node | 17,049,600 |
| `process_rss_avg` | job=mysql-exporter | 16,346,112 |
| `process_rss_avg` | job=mysql-node | 22,222,336 |
| `process_rss_avg` | job=prometheus | 101,548,032 |
| `process_rss_max` | job=backend-node | 20,496,384 |
| `process_rss_max` | job=monitoring-node | 17,084,416 |
| `process_rss_max` | job=mysql-exporter | 16,945,152 |
| `process_rss_max` | job=mysql-node | 22,421,504 |
| `process_rss_max` | job=prometheus | 102,006,784 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 1.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 32.57 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 44.44 |
| `node_load1_avg` | job=backend-node | 1.46 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.14 |
| `node_major_fault_delta` | job=backend-node | 313.14 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 168 |
| `node_mem_available_avg` | job=backend-node | 377,453,568 |
| `node_mem_available_avg` | job=monitoring-node | 405,101,056 |
| `node_mem_available_avg` | job=mysql-node | 273,864,192 |
| `node_swap_free_avg` | job=backend-node | 3,169,172,992 |
| `node_swap_free_avg` | job=monitoring-node | 3,010,101,248 |
| `node_swap_free_avg` | job=mysql-node | 2,570,170,368 |
| `node_swap_in_delta` | job=backend-node | 1,182.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 674.29 |
| `node_swap_out_delta` | job=backend-node | 188.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 165,757.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 150 — 2026-08-13T06:39:53.745Z ~ 2026-08-13T06:41:53.745Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,938 | 21.39 | 18 | 33.8 | 127.84 | 360.51 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,971 | 8.89 | 8.48 | 12.28 | 16.78 | 38.92 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,863 | 20.81 | 18.71 | 31.72 | 82.98 | 342.7 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,925 | 35.58 | 31.54 | 53.25 | 140.96 | 481.29 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 10 | 30 | 11.88 | 124.15 | 132.2 | 121.3 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.41 | 0.51 | 0.96 | 1.5 | 2.88 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 19 | 12.1 | 8.62 | 56.76 | 60.57 | 57.69 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 26.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 30.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.31 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 96 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.4 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.5 |
| `process_rss_avg` | job=backend-node | 20,379,648 |
| `process_rss_avg` | job=monitoring-node | 16,883,200 |
| `process_rss_avg` | job=mysql-exporter | 16,453,120 |
| `process_rss_avg` | job=mysql-node | 22,320,640 |
| `process_rss_avg` | job=prometheus | 102,006,784 |
| `process_rss_max` | job=backend-node | 20,496,384 |
| `process_rss_max` | job=monitoring-node | 17,076,224 |
| `process_rss_max` | job=mysql-exporter | 16,855,040 |
| `process_rss_max` | job=mysql-node | 22,740,992 |
| `process_rss_max` | job=prometheus | 102,006,784 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 49.28 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.22 |
| `node_cpu_pct_avg` | job=mysql-node | 69.71 |
| `node_load1_avg` | job=backend-node | 1.02 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 2.27 |
| `node_major_fault_delta` | job=backend-node | 74.29 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 402.29 |
| `node_mem_available_avg` | job=backend-node | 375,403,008 |
| `node_mem_available_avg` | job=monitoring-node | 403,803,648 |
| `node_mem_available_avg` | job=mysql-node | 269,390,848 |
| `node_swap_free_avg` | job=backend-node | 3,168,714,752 |
| `node_swap_free_avg` | job=monitoring-node | 3,010,101,248 |
| `node_swap_free_avg` | job=mysql-node | 2,570,171,392 |
| `node_swap_in_delta` | job=backend-node | 164.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 1,849.14 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 276,795.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 342.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 13.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 200 — 2026-08-13T06:41:53.745Z ~ 2026-08-13T06:43:53.745Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,137 | 36.61 | 29.13 | 85.31 | 124.04 | 360.51 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,173 | 13 | 10.59 | 26.28 | 57.19 | 184.83 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,464 | 38.04 | 26.67 | 102.44 | 161.78 | 496.58 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,083 | 54.53 | 42.02 | 127.88 | 194.68 | 481.29 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 39 | 32.13 | 23.49 | 103.55 | 126.61 | 130.53 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.51 | 0.55 | 1.57 | 2.32 | 4.29 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 30 | 16.45 | 12.58 | 32.72 | 60.06 | 59.91 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 43.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.41 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 96.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 97 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.54 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.59 |
| `process_rss_avg` | job=backend-node | 20,447,232 |
| `process_rss_avg` | job=monitoring-node | 16,793,600 |
| `process_rss_avg` | job=mysql-exporter | 16,724,992 |
| `process_rss_avg` | job=mysql-node | 22,183,424 |
| `process_rss_avg` | job=prometheus | 102,006,784 |
| `process_rss_max` | job=backend-node | 20,447,232 |
| `process_rss_max` | job=monitoring-node | 16,793,600 |
| `process_rss_max` | job=mysql-exporter | 17,195,008 |
| `process_rss_max` | job=mysql-node | 22,261,760 |
| `process_rss_max` | job=prometheus | 102,006,784 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 9 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 64.19 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 88.69 |
| `node_load1_avg` | job=backend-node | 1.98 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 3.8 |
| `node_major_fault_delta` | job=backend-node | 115.43 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 538.29 |
| `node_mem_available_avg` | job=backend-node | 377,662,976 |
| `node_mem_available_avg` | job=monitoring-node | 402,160,640 |
| `node_mem_available_avg` | job=mysql-node | 267,116,032 |
| `node_swap_free_avg` | job=backend-node | 3,168,634,880 |
| `node_swap_free_avg` | job=monitoring-node | 3,010,101,248 |
| `node_swap_free_avg` | job=mysql-node | 2,570,174,976 |
| `node_swap_in_delta` | job=backend-node | 109.71 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 2,712 |
| `node_swap_out_delta` | job=backend-node | 73.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 387,350.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,378.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 61.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 5.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 300 — 2026-08-13T06:43:53.745Z ~ 2026-08-13T06:45:53.745Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,314 | 183.14 | 172.22 | 322.9 | 428.14 | 960.53 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,134 | 119.62 | 109.89 | 253.45 | 344.36 | 948.87 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 15,480 | 215.07 | 205.19 | 357.74 | 469.35 | 1,158.6 |
| method=GET, status=401, uri=UNKNOWN | 1 | 1.56 | 1.58 | 1.75 | 2.73 | 2.71 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,354 | 257.38 | 246.1 | 424.38 | 510.35 | 800.89 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,339 | 136.17 | 126.9 | 279.96 | 401.62 | 754.01 |
| method=POST, status=401, uri=UNKNOWN | 47 | 1.57 | 1.09 | 4.19 | 5.31 | 5.07 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 30 | 149.59 | 129.74 | 340.54 | 424.13 | 400.05 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 52.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.76 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.64 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.67 |
| `process_rss_avg` | job=backend-node | 20,456,448 |
| `process_rss_avg` | job=monitoring-node | 17,002,496 |
| `process_rss_avg` | job=mysql-exporter | 16,625,152 |
| `process_rss_avg` | job=mysql-node | 22,151,680 |
| `process_rss_avg` | job=prometheus | 102,105,088 |
| `process_rss_max` | job=backend-node | 20,578,304 |
| `process_rss_max` | job=monitoring-node | 17,035,264 |
| `process_rss_max` | job=mysql-exporter | 16,896,000 |
| `process_rss_max` | job=mysql-node | 22,458,368 |
| `process_rss_max` | job=prometheus | 102,268,928 |
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
| `node_cpu_pct_avg` | job=backend-node | 74.96 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 99.36 |
| `node_load1_avg` | job=backend-node | 4.04 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 17.63 |
| `node_major_fault_delta` | job=backend-node | 189.71 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 642.29 |
| `node_mem_available_avg` | job=backend-node | 359,788,544 |
| `node_mem_available_avg` | job=monitoring-node | 404,002,816 |
| `node_mem_available_avg` | job=mysql-node | 257,586,176 |
| `node_swap_free_avg` | job=backend-node | 3,163,907,072 |
| `node_swap_free_avg` | job=monitoring-node | 3,010,101,248 |
| `node_swap_free_avg` | job=mysql-node | 2,567,992,320 |
| `node_swap_in_delta` | job=backend-node | 809.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 2,715.43 |
| `node_swap_out_delta` | job=backend-node | 2,501.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 1,969.14 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 427,385.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 14,738.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 230.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 20 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 400 — 2026-08-13T06:45:53.745Z ~ 2026-08-13T06:47:53.745Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,635 | 171.88 | 165.04 | 288.98 | 367.67 | 960.53 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,518 | 112.62 | 106.45 | 235.63 | 312.51 | 948.87 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 16,667 | 207.35 | 199.56 | 339.22 | 439.33 | 1,158.6 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 2.71 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,238 | 242.57 | 235.53 | 390.99 | 452.73 | 800.89 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 3,113 | 122.79 | 117.02 | 244.09 | 332.92 | 754.01 |
| method=POST, status=401, uri=UNKNOWN | 19 | 2.08 | 0.85 | 8.6 | 9.55 | 8.75 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 19 | 136.01 | 125.83 | 273.17 | 294.63 | 400.05 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 59.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.79 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.65 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.68 |
| `process_rss_avg` | job=backend-node | 20,058,112 |
| `process_rss_avg` | job=monitoring-node | 17,035,264 |
| `process_rss_avg` | job=mysql-exporter | 16,573,952 |
| `process_rss_avg` | job=mysql-node | 21,832,192 |
| `process_rss_avg` | job=prometheus | 102,268,928 |
| `process_rss_max` | job=backend-node | 20,418,560 |
| `process_rss_max` | job=monitoring-node | 17,035,264 |
| `process_rss_max` | job=mysql-exporter | 16,859,136 |
| `process_rss_max` | job=mysql-node | 21,876,736 |
| `process_rss_max` | job=prometheus | 102,268,928 |
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
| `node_cpu_pct_avg` | job=backend-node | 75.52 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 99.55 |
| `node_load1_avg` | job=backend-node | 8.06 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 24.26 |
| `node_major_fault_delta` | job=backend-node | 81.14 |
| `node_major_fault_delta` | job=monitoring-node | 6.86 |
| `node_major_fault_delta` | job=mysql-node | 38.86 |
| `node_mem_available_avg` | job=backend-node | 292,283,392 |
| `node_mem_available_avg` | job=monitoring-node | 405,797,888 |
| `node_mem_available_avg` | job=mysql-node | 249,901,056 |
| `node_swap_free_avg` | job=backend-node | 3,145,157,632 |
| `node_swap_free_avg` | job=monitoring-node | 3,010,101,248 |
| `node_swap_free_avg` | job=mysql-node | 2,567,364,608 |
| `node_swap_in_delta` | job=backend-node | 41.14 |
| `node_swap_in_delta` | job=monitoring-node | 4.57 |
| `node_swap_in_delta` | job=mysql-node | 29.71 |
| `node_swap_out_delta` | job=backend-node | 1,816 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 423,117.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 8,497.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 132.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 17.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

---

## round8-ram2gb-pure-throughput-sse500-20260813.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-13T06:49:27.776Z ~ 2026-08-13T07:03:09.541Z
- 설정: `{"sseVUs":500,"totalSseConnections":1000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-13T06:50:02.776Z ~ 2026-08-13T06:52:02.776Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,155 | 16.06 | 15.77 | 21.81 | 27.22 | 696.59 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,246 | 8.6 | 8.29 | 11.05 | 16.11 | 484.68 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,602 | 17.61 | 17.4 | 22.24 | 31.97 | 821.61 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 869,542.11 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 869,145.88 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 73.77 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 63.13 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 14.87 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,198 | 28.11 | 26.96 | 35.66 | 49.44 | 522.4 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 709.71 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.35 | 0.51 | 0.96 | 1.85 | 4.76 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 7.61 | 7.69 | 8.32 | 8.37 | 206.73 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 14.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.11 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 96 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.2 |
| `process_rss_avg` | job=backend-node | 19,635,712 |
| `process_rss_avg` | job=monitoring-node | 16,859,136 |
| `process_rss_avg` | job=mysql-exporter | 16,302,080 |
| `process_rss_avg` | job=mysql-node | 21,970,944 |
| `process_rss_avg` | job=prometheus | 102,067,712 |
| `process_rss_max` | job=backend-node | 19,984,384 |
| `process_rss_max` | job=monitoring-node | 16,859,136 |
| `process_rss_max` | job=mysql-exporter | 16,691,200 |
| `process_rss_max` | job=mysql-node | 22,093,824 |
| `process_rss_max` | job=prometheus | 102,531,072 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 0.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 23.29 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.22 |
| `node_cpu_pct_avg` | job=mysql-node | 35.5 |
| `node_load1_avg` | job=backend-node | 0.82 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 2.23 |
| `node_major_fault_delta` | job=backend-node | 197.71 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 29.71 |
| `node_mem_available_avg` | job=backend-node | 280,398,848 |
| `node_mem_available_avg` | job=monitoring-node | 403,762,176 |
| `node_mem_available_avg` | job=mysql-node | 251,127,808 |
| `node_swap_free_avg` | job=backend-node | 3,137,654,784 |
| `node_swap_free_avg` | job=monitoring-node | 3,010,101,248 |
| `node_swap_free_avg` | job=mysql-node | 2,567,364,608 |
| `node_swap_in_delta` | job=backend-node | 218.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 20.57 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 111,741.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 100 — 2026-08-13T06:52:02.776Z ~ 2026-08-13T06:54:02.776Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,811 | 16.48 | 15.6 | 22.9 | 35.88 | 67.77 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,829 | 8.37 | 8.15 | 11.03 | 13.78 | 83.48 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,461 | 18.17 | 17.78 | 25.07 | 34.94 | 91.85 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 869,542.11 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 869,145.88 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 73.77 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 63.13 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 14.87 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,808 | 30.23 | 27.62 | 38.27 | 80.31 | 338.42 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.37 | 0.51 | 0.96 | 1.01 | 1.85 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 11 | 10.84 | 8.39 | 25.17 | 27.4 | 26.14 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.16 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.24 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.32 |
| `process_rss_avg` | job=backend-node | 19,254,272 |
| `process_rss_avg` | job=monitoring-node | 16,871,424 |
| `process_rss_avg` | job=mysql-exporter | 16,429,568 |
| `process_rss_avg` | job=mysql-node | 21,934,080 |
| `process_rss_avg` | job=prometheus | 102,076,416 |
| `process_rss_max` | job=backend-node | 19,681,280 |
| `process_rss_max` | job=monitoring-node | 16,990,208 |
| `process_rss_max` | job=mysql-exporter | 16,994,304 |
| `process_rss_max` | job=mysql-node | 21,995,520 |
| `process_rss_max` | job=prometheus | 102,076,416 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 1.38 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 31.2 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 48.42 |
| `node_load1_avg` | job=backend-node | 1.21 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.88 |
| `node_major_fault_delta` | job=backend-node | 148.57 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 33.14 |
| `node_mem_available_avg` | job=backend-node | 301,128,704 |
| `node_mem_available_avg` | job=monitoring-node | 401,788,416 |
| `node_mem_available_avg` | job=mysql-node | 251,897,856 |
| `node_swap_free_avg` | job=backend-node | 3,135,960,576 |
| `node_swap_free_avg` | job=monitoring-node | 3,010,101,248 |
| `node_swap_free_avg` | job=mysql-node | 2,567,364,608 |
| `node_swap_in_delta` | job=backend-node | 10.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 25.14 |
| `node_swap_out_delta` | job=backend-node | 1,140.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 171,893.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 150 — 2026-08-13T06:54:02.776Z ~ 2026-08-13T06:56:02.776Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,054 | 21.76 | 19.18 | 38.45 | 81.94 | 223.88 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,986 | 9.62 | 9.19 | 13.75 | 19.95 | 57.87 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,059 | 23.22 | 20.55 | 38.99 | 79.42 | 363.1 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 73.77 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,983 | 39.22 | 33.32 | 74.18 | 154.35 | 359.69 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 18 | 27.05 | 11.18 | 161.06 | 175.38 | 168.08 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.43 | 0.53 | 1.11 | 1.5 | 1.85 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 14 | 11.42 | 10.25 | 30.2 | 32.88 | 29.17 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 26.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 29.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.3 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.39 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.46 |
| `process_rss_avg` | job=backend-node | 18,999,808 |
| `process_rss_avg` | job=monitoring-node | 16,932,864 |
| `process_rss_avg` | job=mysql-exporter | 16,386,048 |
| `process_rss_avg` | job=mysql-node | 22,154,752 |
| `process_rss_avg` | job=prometheus | 104,669,184 |
| `process_rss_max` | job=backend-node | 19,546,112 |
| `process_rss_max` | job=monitoring-node | 16,949,248 |
| `process_rss_max` | job=mysql-exporter | 17,002,496 |
| `process_rss_max` | job=mysql-node | 22,503,424 |
| `process_rss_max` | job=prometheus | 112,447,488 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3.38 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 49.12 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.36 |
| `node_cpu_pct_avg` | job=mysql-node | 74.41 |
| `node_load1_avg` | job=backend-node | 1.48 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 2.76 |
| `node_major_fault_delta` | job=backend-node | 38.86 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 16 |
| `node_mem_available_avg` | job=backend-node | 316,614,144 |
| `node_mem_available_avg` | job=monitoring-node | 399,779,840 |
| `node_mem_available_avg` | job=mysql-node | 256,459,776 |
| `node_swap_free_avg` | job=backend-node | 3,133,513,728 |
| `node_swap_free_avg` | job=monitoring-node | 3,010,101,248 |
| `node_swap_free_avg` | job=mysql-node | 2,567,364,608 |
| `node_swap_in_delta` | job=backend-node | 44.57 |
| `node_swap_in_delta` | job=monitoring-node | 4.57 |
| `node_swap_in_delta` | job=mysql-node | 13.71 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 282,596.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 777.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 13.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 200 — 2026-08-13T06:56:02.776Z ~ 2026-08-13T06:58:02.776Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,141 | 71.68 | 39.45 | 217.64 | 327.59 | 457.86 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,269 | 30.48 | 13.4 | 122.36 | 207.98 | 345.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,603 | 81.18 | 38.37 | 251.39 | 358.71 | 801.35 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 1 | 1.72 | 1.59 | 10.48 | 11.04 | 1.72 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,088 | 102.35 | 56.96 | 285.43 | 403.22 | 547.41 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 74 | 112.66 | 98.25 | 296.05 | 389.23 | 364.28 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.71 | 0.64 | 2.62 | 4.61 | 4.6 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 15 | 32.59 | 12.93 | 142.05 | 153.68 | 146.44 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 40 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.86 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.46 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.05 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 96.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.56 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.61 |
| `process_rss_avg` | job=backend-node | 18,931,712 |
| `process_rss_avg` | job=monitoring-node | 17,063,936 |
| `process_rss_avg` | job=mysql-exporter | 16,608,768 |
| `process_rss_avg` | job=mysql-node | 22,053,888 |
| `process_rss_avg` | job=prometheus | 121,164,800 |
| `process_rss_max` | job=backend-node | 19,050,496 |
| `process_rss_max` | job=monitoring-node | 17,080,320 |
| `process_rss_max` | job=mysql-exporter | 16,994,304 |
| `process_rss_max` | job=mysql-node | 22,466,560 |
| `process_rss_max` | job=prometheus | 129,007,616 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 14 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 67.68 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.9 |
| `node_cpu_pct_avg` | job=mysql-node | 92.97 |
| `node_load1_avg` | job=backend-node | 2.47 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 7.91 |
| `node_major_fault_delta` | job=backend-node | 14.86 |
| `node_major_fault_delta` | job=monitoring-node | 33.14 |
| `node_major_fault_delta` | job=mysql-node | 37.71 |
| `node_mem_available_avg` | job=backend-node | 319,180,288 |
| `node_mem_available_avg` | job=monitoring-node | 314,337,280 |
| `node_mem_available_avg` | job=mysql-node | 256,333,824 |
| `node_swap_free_avg` | job=backend-node | 3,133,514,752 |
| `node_swap_free_avg` | job=monitoring-node | 3,010,207,744 |
| `node_swap_free_avg` | job=mysql-node | 2,567,364,608 |
| `node_swap_in_delta` | job=backend-node | 27.43 |
| `node_swap_in_delta` | job=monitoring-node | 18.29 |
| `node_swap_in_delta` | job=mysql-node | 40 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 44.57 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 387,717.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,320 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 107.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 11.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 300 — 2026-08-13T06:58:02.776Z ~ 2026-08-13T07:00:02.776Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,290 | 184.73 | 175.2 | 326.03 | 422.53 | 653.96 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,898 | 124.38 | 115.85 | 262.89 | 365.12 | 627.26 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 15,042 | 227.01 | 216.92 | 386 | 495.4 | 1,061.42 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 1.72 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,840 | 266.54 | 257.85 | 433.25 | 517.82 | 790.14 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,625 | 135.79 | 125.93 | 285.74 | 373.46 | 502.7 |
| method=POST, status=401, uri=UNKNOWN | 35 | 1.75 | 0.97 | 6.22 | 7.96 | 7.77 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 14 | 134.16 | 126.76 | 239.35 | 244.72 | 241.81 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 52.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.7 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 97 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.61 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.63 |
| `process_rss_avg` | job=backend-node | 18,786,816 |
| `process_rss_avg` | job=monitoring-node | 17,080,320 |
| `process_rss_avg` | job=mysql-exporter | 16,792,064 |
| `process_rss_avg` | job=mysql-node | 22,006,784 |
| `process_rss_avg` | job=prometheus | 120,644,608 |
| `process_rss_max` | job=backend-node | 19,390,464 |
| `process_rss_max` | job=monitoring-node | 17,080,320 |
| `process_rss_max` | job=mysql-exporter | 17,149,952 |
| `process_rss_max` | job=mysql-node | 22,351,872 |
| `process_rss_max` | job=prometheus | 125,997,056 |
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
| `node_cpu_pct_avg` | job=backend-node | 72.84 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.11 |
| `node_cpu_pct_avg` | job=mysql-node | 99.81 |
| `node_load1_avg` | job=backend-node | 4 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 22.5 |
| `node_major_fault_delta` | job=backend-node | 16 |
| `node_major_fault_delta` | job=monitoring-node | 44.57 |
| `node_major_fault_delta` | job=mysql-node | 324.57 |
| `node_mem_available_avg` | job=backend-node | 313,137,152 |
| `node_mem_available_avg` | job=monitoring-node | 267,595,264 |
| `node_mem_available_avg` | job=mysql-node | 252,495,872 |
| `node_swap_free_avg` | job=backend-node | 3,132,963,840 |
| `node_swap_free_avg` | job=monitoring-node | 3,011,177,984 |
| `node_swap_free_avg` | job=mysql-node | 2,567,327,232 |
| `node_swap_in_delta` | job=backend-node | 18.29 |
| `node_swap_in_delta` | job=monitoring-node | 16 |
| `node_swap_in_delta` | job=mysql-node | 156.57 |
| `node_swap_out_delta` | job=backend-node | 1,052.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 771.43 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 399,594.3 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 8,275.19 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 116.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 19.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 400 — 2026-08-13T07:00:02.776Z ~ 2026-08-13T07:02:02.776Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,973 | 179.31 | 170.39 | 309.99 | 411.16 | 1,821.62 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,976 | 119.01 | 114.34 | 243.4 | 324.86 | 1,530.75 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 15,565 | 226.98 | 218.85 | 362.61 | 473.57 | 1,834.42 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,302 | 263.03 | 254.32 | 421.33 | 486.59 | 1,249.17 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,641 | 134.55 | 126.68 | 266.33 | 345.05 | 834.78 |
| method=POST, status=401, uri=UNKNOWN | 23 | 2.17 | 1 | 8.39 | 10.91 | 11.28 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 118.25 | 123.03 | 193.5 | 199.76 | 284.01 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 56 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.74 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.05 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.74 |
| `process_rss_avg` | job=backend-node | 18,903,040 |
| `process_rss_avg` | job=monitoring-node | 17,063,936 |
| `process_rss_avg` | job=mysql-exporter | 16,689,664 |
| `process_rss_avg` | job=mysql-node | 21,931,008 |
| `process_rss_avg` | job=prometheus | 129,418,752 |
| `process_rss_max` | job=backend-node | 18,903,040 |
| `process_rss_max` | job=monitoring-node | 17,080,320 |
| `process_rss_max` | job=mysql-exporter | 16,891,904 |
| `process_rss_max` | job=mysql-node | 22,122,496 |
| `process_rss_max` | job=prometheus | 144,658,432 |
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
| `node_cpu_pct_avg` | job=backend-node | 72.37 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.72 |
| `node_cpu_pct_avg` | job=mysql-node | 99.75 |
| `node_load1_avg` | job=backend-node | 3.97 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 27.74 |
| `node_major_fault_delta` | job=backend-node | 106.29 |
| `node_major_fault_delta` | job=monitoring-node | 140.57 |
| `node_major_fault_delta` | job=mysql-node | 574.86 |
| `node_mem_available_avg` | job=backend-node | 289,363,968 |
| `node_mem_available_avg` | job=monitoring-node | 252,865,536 |
| `node_mem_available_avg` | job=mysql-node | 242,447,360 |
| `node_swap_free_avg` | job=backend-node | 3,126,974,976 |
| `node_swap_free_avg` | job=monitoring-node | 3,011,482,624 |
| `node_swap_free_avg` | job=mysql-node | 2,567,079,424 |
| `node_swap_in_delta` | job=backend-node | 89.14 |
| `node_swap_in_delta` | job=monitoring-node | 58.29 |
| `node_swap_in_delta` | job=mysql-node | 425.14 |
| `node_swap_out_delta` | job=backend-node | 812.57 |
| `node_swap_out_delta` | job=monitoring-node | 1.14 |
| `node_swap_out_delta` | job=mysql-node | 472 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 395,475.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,689.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 101.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 20.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

---

## round8-ram2gb-pure-throughput-sse1000-20260813.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-13T07:03:36.904Z ~ 2026-08-13T07:17:19.867Z
- 설정: `{"sseVUs":1000,"totalSseConnections":2000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-13T07:04:11.904Z ~ 2026-08-13T07:06:11.904Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,241 | 16.66 | 16.01 | 22.29 | 32.25 | 477.69 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,143 | 9.01 | 8.6 | 12.42 | 16.58 | 509.9 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,576 | 18.83 | 18.83 | 25.15 | 33.07 | 767.34 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 845,853.38 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 845,717.41 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 845,143.46 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 63.31 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 269.11 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,186 | 29.08 | 27.26 | 36.46 | 55.22 | 579.38 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 479.82 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.37 | 0.51 | 0.98 | 1.15 | 8.15 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 8.56 | 8.39 | 10.91 | 11.13 | 116.88 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 16 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.34 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 97 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.16 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_rss_avg` | job=backend-node | 18,837,504 |
| `process_rss_avg` | job=monitoring-node | 17,039,360 |
| `process_rss_avg` | job=mysql-exporter | 16,739,840 |
| `process_rss_avg` | job=mysql-node | 21,845,504 |
| `process_rss_avg` | job=prometheus | 116,158,976 |
| `process_rss_max` | job=backend-node | 18,935,808 |
| `process_rss_max` | job=monitoring-node | 17,039,360 |
| `process_rss_max` | job=mysql-exporter | 17,141,760 |
| `process_rss_max` | job=mysql-node | 21,995,520 |
| `process_rss_max` | job=prometheus | 117,768,192 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 0.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 23.49 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.24 |
| `node_cpu_pct_avg` | job=mysql-node | 36.17 |
| `node_load1_avg` | job=backend-node | 0.88 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 1.72 |
| `node_major_fault_delta` | job=backend-node | 1,544 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 26.29 |
| `node_mem_available_avg` | job=backend-node | 228,239,872 |
| `node_mem_available_avg` | job=monitoring-node | 282,445,824 |
| `node_mem_available_avg` | job=mysql-node | 248,053,248 |
| `node_swap_free_avg` | job=backend-node | 3,069,323,264 |
| `node_swap_free_avg` | job=monitoring-node | 3,011,728,896 |
| `node_swap_free_avg` | job=mysql-node | 2,565,051,392 |
| `node_swap_in_delta` | job=backend-node | 2,676.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 18.29 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 111,790.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 100 — 2026-08-13T07:06:11.904Z ~ 2026-08-13T07:08:11.904Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,821 | 16.77 | 15.81 | 24.1 | 38.07 | 89.59 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,616 | 8.84 | 8.33 | 11.89 | 18.26 | 80.87 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,153 | 19.23 | 18.88 | 26.89 | 50.28 | 416.86 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 845,143.46 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 63.31 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 269.11 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,712 | 30.47 | 28.2 | 39.31 | 71.67 | 402.77 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2 | 30.03 | 12.58 | 49.77 | 50.22 | 48.67 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.36 | 0.51 | 0.96 | 1.15 | 8.15 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 8.58 | 8.04 | 9.58 | 9.74 | 10.96 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.86 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.16 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.05 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 94.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 95 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.24 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.33 |
| `process_rss_avg` | job=backend-node | 18,935,808 |
| `process_rss_avg` | job=monitoring-node | 16,941,056 |
| `process_rss_avg` | job=mysql-exporter | 16,504,320 |
| `process_rss_avg` | job=mysql-node | 21,765,632 |
| `process_rss_avg` | job=prometheus | 120,371,712 |
| `process_rss_max` | job=backend-node | 18,935,808 |
| `process_rss_max` | job=monitoring-node | 17,039,360 |
| `process_rss_max` | job=mysql-exporter | 16,654,336 |
| `process_rss_max` | job=mysql-node | 21,901,312 |
| `process_rss_max` | job=prometheus | 127,229,952 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
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
| `node_cpu_pct_avg` | job=backend-node | 31.32 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.5 |
| `node_cpu_pct_avg` | job=mysql-node | 51.33 |
| `node_load1_avg` | job=backend-node | 0.72 |
| `node_load1_avg` | job=monitoring-node | 0.05 |
| `node_load1_avg` | job=mysql-node | 1 |
| `node_major_fault_delta` | job=backend-node | 129.14 |
| `node_major_fault_delta` | job=monitoring-node | 48 |
| `node_major_fault_delta` | job=mysql-node | 25.14 |
| `node_mem_available_avg` | job=backend-node | 223,503,360 |
| `node_mem_available_avg` | job=monitoring-node | 271,138,816 |
| `node_mem_available_avg` | job=mysql-node | 249,057,792 |
| `node_swap_free_avg` | job=backend-node | 3,063,431,168 |
| `node_swap_free_avg` | job=monitoring-node | 3,011,851,776 |
| `node_swap_free_avg` | job=mysql-node | 2,565,054,464 |
| `node_swap_in_delta` | job=backend-node | 225.14 |
| `node_swap_in_delta` | job=monitoring-node | 34.29 |
| `node_swap_in_delta` | job=mysql-node | 11.43 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 162,483.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 157.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 2.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 150 — 2026-08-13T07:08:11.904Z ~ 2026-08-13T07:10:11.904Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,971 | 20.35 | 18.85 | 35.29 | 52.2 | 89.59 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,864 | 10.26 | 9.36 | 15.81 | 26.19 | 134.91 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,753 | 23.22 | 21.07 | 38.55 | 61.6 | 416.86 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,889 | 42.52 | 34.03 | 86.5 | 236.52 | 402.77 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 13 | 13.73 | 12.41 | 24.89 | 27.35 | 48.67 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.42 | 0.52 | 0.99 | 2.9 | 3.07 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 15 | 12.36 | 10.14 | 29.92 | 32.83 | 29.31 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 29.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.33 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 97 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.39 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.46 |
| `process_rss_avg` | job=backend-node | 18,935,808 |
| `process_rss_avg` | job=monitoring-node | 17,022,976 |
| `process_rss_avg` | job=mysql-exporter | 16,657,408 |
| `process_rss_avg` | job=mysql-node | 21,856,256 |
| `process_rss_avg` | job=prometheus | 121,806,848 |
| `process_rss_max` | job=backend-node | 18,935,808 |
| `process_rss_max` | job=monitoring-node | 17,039,360 |
| `process_rss_max` | job=mysql-exporter | 17,047,552 |
| `process_rss_max` | job=mysql-node | 21,991,424 |
| `process_rss_max` | job=prometheus | 122,134,528 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 50.31 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 77.48 |
| `node_load1_avg` | job=backend-node | 1.07 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 2.24 |
| `node_major_fault_delta` | job=backend-node | 10.29 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 54.86 |
| `node_mem_available_avg` | job=backend-node | 223,394,304 |
| `node_mem_available_avg` | job=monitoring-node | 289,129,984 |
| `node_mem_available_avg` | job=mysql-node | 250,312,704 |
| `node_swap_free_avg` | job=backend-node | 3,063,431,168 |
| `node_swap_free_avg` | job=monitoring-node | 3,011,985,408 |
| `node_swap_free_avg` | job=mysql-node | 2,565,054,464 |
| `node_swap_in_delta` | job=backend-node | 21.71 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 11.43 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 273,538.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 396.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 11.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 200 — 2026-08-13T07:10:11.904Z ~ 2026-08-13T07:12:11.904Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,114 | 91.35 | 65.68 | 252.01 | 353.46 | 549.34 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,111 | 38.39 | 16.31 | 151.64 | 243.86 | 463.18 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,330 | 103.97 | 64.96 | 281.96 | 399.85 | 785.12 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,968 | 128.8 | 85.97 | 335.15 | 433.04 | 601.14 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 112 | 107.26 | 96.05 | 247.18 | 359.7 | 375.5 |
| method=POST, status=401, uri=UNKNOWN | 80 | 1.61 | 0.74 | 3.32 | 46.42 | 46.54 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 13 | 67.96 | 41.94 | 166.65 | 176.5 | 159.5 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.25 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 41.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.86 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.45 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.06 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 96.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.59 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.67 |
| `process_rss_avg` | job=backend-node | 19,017,728 |
| `process_rss_avg` | job=monitoring-node | 17,039,360 |
| `process_rss_avg` | job=mysql-exporter | 16,514,560 |
| `process_rss_avg` | job=mysql-node | 21,918,720 |
| `process_rss_avg` | job=prometheus | 108,988,928 |
| `process_rss_max` | job=backend-node | 19,066,880 |
| `process_rss_max` | job=monitoring-node | 17,039,360 |
| `process_rss_max` | job=mysql-exporter | 16,805,888 |
| `process_rss_max` | job=mysql-node | 22,224,896 |
| `process_rss_max` | job=prometheus | 121,610,240 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 20.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 49 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 72.01 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.25 |
| `node_cpu_pct_avg` | job=mysql-node | 95.62 |
| `node_load1_avg` | job=backend-node | 3.55 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 8.7 |
| `node_major_fault_delta` | job=backend-node | 299.43 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 115.43 |
| `node_mem_available_avg` | job=backend-node | 228,782,080 |
| `node_mem_available_avg` | job=monitoring-node | 362,334,720 |
| `node_mem_available_avg` | job=mysql-node | 250,211,328 |
| `node_swap_free_avg` | job=backend-node | 3,061,198,336 |
| `node_swap_free_avg` | job=monitoring-node | 3,011,985,408 |
| `node_swap_free_avg` | job=mysql-node | 2,565,063,680 |
| `node_swap_in_delta` | job=backend-node | 366.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 168 |
| `node_swap_out_delta` | job=backend-node | 509.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 378,893.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,876.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 130.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 10 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 300 — 2026-08-13T07:12:11.904Z ~ 2026-08-13T07:14:11.904Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,017 | 186.93 | 174.55 | 341.22 | 435.54 | 904.27 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,706 | 128.68 | 119.62 | 279.75 | 382.12 | 647.48 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 14,445 | 236.42 | 226.22 | 405.79 | 497.1 | 1,169.97 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 1 | 15.02 | 14.68 | 15.31 | 15.37 | 15.02 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,041 | 274.1 | 263.74 | 440.85 | 529.48 | 758.63 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,267 | 146.17 | 132.5 | 292.83 | 417.03 | 617.13 |
| method=POST, status=401, uri=UNKNOWN | 43 | 1.33 | 0.79 | 3.88 | 10.65 | 46.54 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 11 | 105.49 | 111.85 | 167.77 | 176.72 | 164.16 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 50.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.78 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 97 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.63 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.65 |
| `process_rss_avg` | job=backend-node | 19,034,112 |
| `process_rss_avg` | job=monitoring-node | 17,039,360 |
| `process_rss_avg` | job=mysql-exporter | 16,985,088 |
| `process_rss_avg` | job=mysql-node | 21,950,464 |
| `process_rss_avg` | job=prometheus | 103,250,432 |
| `process_rss_max` | job=backend-node | 19,197,952 |
| `process_rss_max` | job=monitoring-node | 17,039,360 |
| `process_rss_max` | job=mysql-exporter | 17,281,024 |
| `process_rss_max` | job=mysql-node | 22,056,960 |
| `process_rss_max` | job=prometheus | 106,479,616 |
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
| `node_cpu_pct_avg` | job=backend-node | 76.5 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.27 |
| `node_cpu_pct_avg` | job=mysql-node | 99.5 |
| `node_load1_avg` | job=backend-node | 5.75 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 23.31 |
| `node_major_fault_delta` | job=backend-node | 4,390.86 |
| `node_major_fault_delta` | job=monitoring-node | 5.71 |
| `node_major_fault_delta` | job=mysql-node | 165.71 |
| `node_mem_available_avg` | job=backend-node | 231,576,576 |
| `node_mem_available_avg` | job=monitoring-node | 360,687,616 |
| `node_mem_available_avg` | job=mysql-node | 248,933,888 |
| `node_swap_free_avg` | job=backend-node | 3,042,590,208 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,016,128 |
| `node_swap_free_avg` | job=mysql-node | 2,565,163,008 |
| `node_swap_in_delta` | job=backend-node | 4,912 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 353.14 |
| `node_swap_out_delta` | job=backend-node | 10,785.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 395,553.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 9,304 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 137.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 18.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 400 — 2026-08-13T07:14:11.904Z ~ 2026-08-13T07:16:11.904Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,574 | 185.21 | 174.44 | 329.86 | 425 | 904.27 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,537 | 127.72 | 120.84 | 272.1 | 361.34 | 648.64 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 14,818 | 241.98 | 232.72 | 405.39 | 502.86 | 1,169.97 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 15.02 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,701 | 273.44 | 263.79 | 436.39 | 528.34 | 878.34 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,512 | 148.55 | 137.26 | 301.3 | 422.05 | 649.08 |
| method=POST, status=401, uri=UNKNOWN | 33 | 0.89 | 0.69 | 3.34 | 5.19 | 9.85 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 118.16 | 111.85 | 237.12 | 244.28 | 230.84 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 50.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.69 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.61 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.63 |
| `process_rss_avg` | job=backend-node | 19,197,952 |
| `process_rss_avg` | job=monitoring-node | 17,039,360 |
| `process_rss_avg` | job=mysql-exporter | 16,388,096 |
| `process_rss_avg` | job=mysql-node | 21,959,168 |
| `process_rss_avg` | job=prometheus | 114,598,400 |
| `process_rss_max` | job=backend-node | 19,197,952 |
| `process_rss_max` | job=monitoring-node | 17,039,360 |
| `process_rss_max` | job=mysql-exporter | 16,908,288 |
| `process_rss_max` | job=mysql-node | 22,384,640 |
| `process_rss_max` | job=prometheus | 121,786,368 |
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
| `node_cpu_pct_avg` | job=backend-node | 75.12 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.1 |
| `node_cpu_pct_avg` | job=mysql-node | 99.54 |
| `node_load1_avg` | job=backend-node | 6.53 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 26.27 |
| `node_major_fault_delta` | job=backend-node | 1,621.71 |
| `node_major_fault_delta` | job=monitoring-node | 22.86 |
| `node_major_fault_delta` | job=mysql-node | 197.71 |
| `node_mem_available_avg` | job=backend-node | 201,954,816 |
| `node_mem_available_avg` | job=monitoring-node | 296,656,384 |
| `node_mem_available_avg` | job=mysql-node | 246,961,152 |
| `node_swap_free_avg` | job=backend-node | 2,992,564,224 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,081,664 |
| `node_swap_free_avg` | job=mysql-node | 2,565,132,288 |
| `node_swap_in_delta` | job=backend-node | 2,363.43 |
| `node_swap_in_delta` | job=monitoring-node | 18.29 |
| `node_swap_in_delta` | job=mysql-node | 307.43 |
| `node_swap_out_delta` | job=backend-node | 3,824 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 382,251.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 8,213.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 113.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 21.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

---

## round8-ram2gb-hot-auction-pattern-sse250-20260813.json

- 시나리오: `hot-auction-pattern`
- K6 실행: 2026-08-13T07:17:51.216Z ~ 2026-08-13T07:25:53.560Z
- 설정: `{"auctionCount":200,"hotAuctionCount":3,"hotAuctionRate":14,"coldAuctionRatePerAuction":0.09,"coldAuctionRate":18,"sseUsers":500,"totalSseConnections":1000,"duration":"5m"}`

### 0~1분 — 2026-08-13T07:18:56.216Z ~ 2026-08-13T07:19:56.216Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 356.68 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,387 | 533.95 | 505.29 | 954.1 | 1,095.77 | 1,608.24 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 862,622.5 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 76.22 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 865,852.42 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 844,323.69 |
| method=GET, status=400, uri=/api/auctions/stream | 14,388 | 0.91 | 0.64 | 3.25 | 7.3 | 300.87 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 14.71 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 46.79 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 35.1 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 861 | 374.55 | 368.6 | 605.87 | 727.91 | 1,118.61 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,283 | 316.18 | 292.33 | 601.38 | 742.53 | 895.83 |
| method=POST, status=401, uri=UNKNOWN | 27 | 0.72 | 0.63 | 2.45 | 3.77 | 7.68 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 206.87 | 178.96 | 266.2 | 267.99 | 249.97 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.25 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 9.33 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.36 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.06 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.39 |
| `process_rss_avg` | job=backend-node | 16,035,840 |
| `process_rss_avg` | job=monitoring-node | 17,039,360 |
| `process_rss_avg` | job=mysql-exporter | 16,562,176 |
| `process_rss_avg` | job=mysql-node | 22,071,296 |
| `process_rss_avg` | job=prometheus | 119,288,832 |
| `process_rss_max` | job=backend-node | 16,134,144 |
| `process_rss_max` | job=monitoring-node | 17,039,360 |
| `process_rss_max` | job=mysql-exporter | 16,879,616 |
| `process_rss_max` | job=mysql-node | 22,183,936 |
| `process_rss_max` | job=prometheus | 122,916,864 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 37.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 73.79 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.88 |
| `node_cpu_pct_avg` | job=mysql-node | 99.54 |
| `node_load1_avg` | job=backend-node | 3.05 |
| `node_load1_avg` | job=monitoring-node | 0.09 |
| `node_load1_avg` | job=mysql-node | 10.3 |
| `node_major_fault_delta` | job=backend-node | 23,442.67 |
| `node_major_fault_delta` | job=monitoring-node | 14.67 |
| `node_major_fault_delta` | job=mysql-node | 5.33 |
| `node_mem_available_avg` | job=backend-node | 102,750,208 |
| `node_mem_available_avg` | job=monitoring-node | 256,927,744 |
| `node_mem_available_avg` | job=mysql-node | 239,320,064 |
| `node_swap_free_avg` | job=backend-node | 2,521,679,872 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,145,152 |
| `node_swap_free_avg` | job=mysql-node | 2,565,132,288 |
| `node_swap_in_delta` | job=backend-node | 43,077.33 |
| `node_swap_in_delta` | job=monitoring-node | 1.33 |
| `node_swap_in_delta` | job=mysql-node | 4 |
| `node_swap_out_delta` | job=backend-node | 46,344 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 95,617.33 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 8 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 148,310.67 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,561.33 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 27.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### 1~2분 — 2026-08-13T07:19:56.216Z ~ 2026-08-13T07:20:56.216Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,505 | 536.76 | 507.39 | 925.39 | 1,066.39 | 1,608.24 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 862,622.5 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 76.22 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 865,852.42 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 844,323.69 |
| method=GET, status=400, uri=/api/auctions/stream | 7,533 | 1.04 | 0.61 | 2.47 | 5.55 | 300.87 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 14.71 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 46.79 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 35.1 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 788 | 381.69 | 375.38 | 617.53 | 784.95 | 1,283.81 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,629 | 306.62 | 280.37 | 582.63 | 727.83 | 985.36 |
| method=POST, status=401, uri=UNKNOWN | 19 | 0.48 | 0.58 | 1.28 | 1.37 | 7.68 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 190.17 | 190.14 | 200.21 | 201.1 | 249.97 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.25 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 8 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.67 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.21 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.32 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.35 |
| `process_rss_avg` | job=backend-node | 16,021,504 |
| `process_rss_avg` | job=monitoring-node | 16,902,144 |
| `process_rss_avg` | job=mysql-exporter | 16,379,904 |
| `process_rss_avg` | job=mysql-node | 22,063,104 |
| `process_rss_avg` | job=prometheus | 118,079,488 |
| `process_rss_max` | job=backend-node | 16,121,856 |
| `process_rss_max` | job=monitoring-node | 17,039,360 |
| `process_rss_max` | job=mysql-exporter | 16,814,080 |
| `process_rss_max` | job=mysql-node | 22,241,280 |
| `process_rss_max` | job=prometheus | 118,079,488 |
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
| `node_cpu_pct_avg` | job=backend-node | 53.61 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.27 |
| `node_cpu_pct_avg` | job=mysql-node | 99.97 |
| `node_load1_avg` | job=backend-node | 2.85 |
| `node_load1_avg` | job=monitoring-node | 0.1 |
| `node_load1_avg` | job=mysql-node | 21.01 |
| `node_major_fault_delta` | job=backend-node | 2,761.33 |
| `node_major_fault_delta` | job=monitoring-node | 1.33 |
| `node_major_fault_delta` | job=mysql-node | 64 |
| `node_mem_available_avg` | job=backend-node | 121,394,176 |
| `node_mem_available_avg` | job=monitoring-node | 254,327,808 |
| `node_mem_available_avg` | job=mysql-node | 238,424,064 |
| `node_swap_free_avg` | job=backend-node | 2,467,695,616 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,167,680 |
| `node_swap_free_avg` | job=mysql-node | 2,565,132,288 |
| `node_swap_in_delta` | job=backend-node | 4,440 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 426.67 |
| `node_swap_out_delta` | job=backend-node | 4,661.33 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 95,416 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 4 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 140,806.67 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,630.67 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 24.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### 2~3분 — 2026-08-13T07:20:56.216Z ~ 2026-08-13T07:21:56.216Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,516 | 542.95 | 509.67 | 950.84 | 1,176.57 | 1,608.24 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 1.87 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 6,127 | 0.64 | 0.58 | 2.03 | 3.91 | 282.3 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 14.71 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 3 | 32.25 | 2.1 | 66.55 | 67 | 62.7 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 2.91 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 743 | 379.02 | 369.17 | 605.36 | 734.11 | 1,283.81 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,692 | 297.92 | 279.03 | 553.86 | 692.85 | 985.36 |
| method=POST, status=401, uri=UNKNOWN | 15 | 0.4 | 0.55 | 1.56 | 1.71 | 3.72 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 98.01 | 100 | 110.66 | 111.61 | 277.47 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.33 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.11 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.32 |
| `process_rss_avg` | job=backend-node | 16,175,104 |
| `process_rss_avg` | job=monitoring-node | 16,928,768 |
| `process_rss_avg` | job=mysql-exporter | 16,416,768 |
| `process_rss_avg` | job=mysql-node | 22,064,128 |
| `process_rss_avg` | job=prometheus | 109,189,120 |
| `process_rss_max` | job=backend-node | 16,252,928 |
| `process_rss_max` | job=monitoring-node | 17,027,072 |
| `process_rss_max` | job=mysql-exporter | 16,838,656 |
| `process_rss_max` | job=mysql-node | 22,167,552 |
| `process_rss_max` | job=prometheus | 118,079,488 |
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
| `node_cpu_pct_avg` | job=backend-node | 49.62 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.32 |
| `node_cpu_pct_avg` | job=mysql-node | 99.94 |
| `node_load1_avg` | job=backend-node | 2.12 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 24.29 |
| `node_major_fault_delta` | job=backend-node | 336 |
| `node_major_fault_delta` | job=monitoring-node | 1.33 |
| `node_major_fault_delta` | job=mysql-node | 4 |
| `node_mem_available_avg` | job=backend-node | 146,282,496 |
| `node_mem_available_avg` | job=monitoring-node | 264,423,424 |
| `node_mem_available_avg` | job=mysql-node | 237,176,832 |
| `node_swap_free_avg` | job=backend-node | 2,460,262,400 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,190,208 |
| `node_swap_free_avg` | job=mysql-node | 2,565,132,288 |
| `node_swap_in_delta` | job=backend-node | 506.67 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 2.67 |
| `node_swap_out_delta` | job=backend-node | 4,122.67 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 94,288 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 2 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 136,084 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,654.67 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 25.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### 3~4분 — 2026-08-13T07:21:56.216Z ~ 2026-08-13T07:22:56.216Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,621 | 516.76 | 487.75 | 898.04 | 1,035.07 | 1,547.77 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 5,369 | 0.8 | 0.6 | 2.1 | 4.54 | 282.3 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 62.7 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 732 | 369.31 | 360.69 | 585.94 | 710.35 | 1,283.81 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,899 | 294.8 | 271.82 | 534.56 | 731.18 | 985.36 |
| method=POST, status=401, uri=UNKNOWN | 16 | 0.45 | 0.55 | 1.89 | 2.06 | 2 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 298.77 | 268.44 | 429.5 | 443.81 | 513.17 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 8 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.67 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.17 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.05 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_rss_avg` | job=backend-node | 16,285,696 |
| `process_rss_avg` | job=monitoring-node | 16,953,344 |
| `process_rss_avg` | job=mysql-exporter | 16,557,056 |
| `process_rss_avg` | job=mysql-node | 22,119,424 |
| `process_rss_avg` | job=prometheus | 101,666,816 |
| `process_rss_max` | job=backend-node | 16,490,496 |
| `process_rss_max` | job=monitoring-node | 17,027,072 |
| `process_rss_max` | job=mysql-exporter | 16,838,656 |
| `process_rss_max` | job=mysql-node | 22,192,128 |
| `process_rss_max` | job=prometheus | 101,666,816 |
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
| `node_cpu_pct_avg` | job=backend-node | 48.89 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 99.93 |
| `node_load1_avg` | job=backend-node | 2.13 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 25.66 |
| `node_major_fault_delta` | job=backend-node | 364 |
| `node_major_fault_delta` | job=monitoring-node | 1.33 |
| `node_major_fault_delta` | job=mysql-node | 2.67 |
| `node_mem_available_avg` | job=backend-node | 153,111,552 |
| `node_mem_available_avg` | job=monitoring-node | 275,359,744 |
| `node_mem_available_avg` | job=mysql-node | 234,976,256 |
| `node_swap_free_avg` | job=backend-node | 2,459,943,936 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,186,112 |
| `node_swap_free_avg` | job=mysql-node | 2,565,132,288 |
| `node_swap_in_delta` | job=backend-node | 541.33 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 2.67 |
| `node_swap_out_delta` | job=backend-node | 650.67 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 96,537.33 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 3 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 150,005.33 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,737.33 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 26 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### 4~5분 — 2026-08-13T07:22:56.216Z ~ 2026-08-13T07:23:56.216Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,569 | 530.38 | 501.33 | 939.21 | 1,063.95 | 1,378.8 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 5,272 | 0.9 | 0.6 | 2.3 | 6.36 | 151.63 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 62.7 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 732 | 376.11 | 358.92 | 608.73 | 712.46 | 981.11 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,787 | 293.17 | 267.42 | 555.33 | 684.37 | 983.27 |
| method=POST, status=401, uri=UNKNOWN | 15 | 0.43 | 0.55 | 1.9 | 2.06 | 2.08 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 204.94 | 193.87 | 241.59 | 245.17 | 513.17 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.13 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.06 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_rss_avg` | job=backend-node | 16,211,968 |
| `process_rss_avg` | job=monitoring-node | 16,879,616 |
| `process_rss_avg` | job=mysql-exporter | 16,347,136 |
| `process_rss_avg` | job=mysql-node | 22,041,600 |
| `process_rss_avg` | job=prometheus | 97,670,144 |
| `process_rss_max` | job=backend-node | 16,211,968 |
| `process_rss_max` | job=monitoring-node | 16,879,616 |
| `process_rss_max` | job=mysql-exporter | 16,502,784 |
| `process_rss_max` | job=mysql-node | 22,208,512 |
| `process_rss_max` | job=prometheus | 101,666,816 |
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
| `node_cpu_pct_avg` | job=backend-node | 49.91 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.31 |
| `node_cpu_pct_avg` | job=mysql-node | 99.94 |
| `node_load1_avg` | job=backend-node | 1.73 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 26.36 |
| `node_major_fault_delta` | job=backend-node | 1,221.33 |
| `node_major_fault_delta` | job=monitoring-node | 1.33 |
| `node_major_fault_delta` | job=mysql-node | 13.33 |
| `node_mem_available_avg` | job=backend-node | 150,757,376 |
| `node_mem_available_avg` | job=monitoring-node | 354,199,552 |
| `node_mem_available_avg` | job=mysql-node | 233,800,704 |
| `node_swap_free_avg` | job=backend-node | 2,443,432,960 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,186,112 |
| `node_swap_free_avg` | job=mysql-node | 2,565,132,288 |
| `node_swap_in_delta` | job=backend-node | 1,110.67 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 8 |
| `node_swap_out_delta` | job=backend-node | 9,973.33 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 95,150.67 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 2 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 142,878.67 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,666.67 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 27.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

---

## round8-ram2gb-bid-only-load-noSSE-20260813.json

- 시나리오: `bid-only-load (SSE 없음)`
- K6 실행: 2026-08-13T07:26:23.739Z ~ 2026-08-13T07:38:35.290Z
- 설정: `{"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m","hotAuctionId":null}`

### QPS 50 — 2026-08-13T07:26:23.739Z ~ 2026-08-13T07:28:23.739Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,257 | 16.77 | 16.45 | 22 | 26.72 | 72.19 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,143 | 9.28 | 8.63 | 12.44 | 25.17 | 50.19 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,602 | 19.47 | 19.47 | 25.77 | 36.11 | 1,433.71 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 535 | 485,722.29 | 30,000 | 30,000 | 30,000 | 516,760.07 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 116.74 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 89.4 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,198 | 29.82 | 29.48 | 37.45 | 49.44 | 880.27 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 721.32 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.31 | 0.5 | 0.95 | 0.99 | 1.64 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 8.57 | 8.39 | 9.65 | 9.76 | 9.31 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 12.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.1 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 96 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.15 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.17 |
| `process_rss_avg` | job=backend-node | 16,330,752 |
| `process_rss_avg` | job=monitoring-node | 16,986,112 |
| `process_rss_avg` | job=mysql-exporter | 16,401,408 |
| `process_rss_avg` | job=mysql-node | 22,123,520 |
| `process_rss_avg` | job=prometheus | 96,202,752 |
| `process_rss_max` | job=backend-node | 16,412,672 |
| `process_rss_max` | job=monitoring-node | 16,986,112 |
| `process_rss_max` | job=mysql-exporter | 16,900,096 |
| `process_rss_max` | job=mysql-node | 22,360,064 |
| `process_rss_max` | job=prometheus | 96,202,752 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 58.5 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 468 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 1.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 20.02 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.23 |
| `node_cpu_pct_avg` | job=mysql-node | 39.89 |
| `node_load1_avg` | job=backend-node | 0.58 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.7 |
| `node_major_fault_delta` | job=backend-node | 372.57 |
| `node_major_fault_delta` | job=monitoring-node | 14.86 |
| `node_major_fault_delta` | job=mysql-node | 11.43 |
| `node_mem_available_avg` | job=backend-node | 331,233,792 |
| `node_mem_available_avg` | job=monitoring-node | 398,028,288 |
| `node_mem_available_avg` | job=mysql-node | 242,993,664 |
| `node_swap_free_avg` | job=backend-node | 2,513,286,656 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,186,112 |
| `node_swap_free_avg` | job=mysql-node | 2,565,128,704 |
| `node_swap_in_delta` | job=backend-node | 420.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 9.14 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 1.14 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 111,731.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 100 — 2026-08-13T07:28:23.739Z ~ 2026-08-13T07:30:23.739Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,741 | 21.83 | 17.68 | 55.28 | 87.35 | 149.56 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,786 | 11.52 | 9.21 | 26 | 46.27 | 84.24 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,287 | 27.71 | 20.53 | 76.83 | 133.33 | 250.89 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 516,760.07 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 89.4 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,747 | 38.21 | 31.17 | 74.98 | 184.25 | 445.96 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 5 | 19.87 | 9.79 | 43.62 | 44.52 | 39.53 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.45 | 0.52 | 0.99 | 6.01 | 6.3 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 10.46 | 9.09 | 20.41 | 21.98 | 18.5 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 20.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.17 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 96 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.22 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_rss_avg` | job=backend-node | 16,412,672 |
| `process_rss_avg` | job=monitoring-node | 16,967,168 |
| `process_rss_avg` | job=mysql-exporter | 16,530,432 |
| `process_rss_avg` | job=mysql-node | 21,902,848 |
| `process_rss_avg` | job=prometheus | 104,536,064 |
| `process_rss_max` | job=backend-node | 16,412,672 |
| `process_rss_max` | job=monitoring-node | 16,986,112 |
| `process_rss_max` | job=mysql-exporter | 16,838,656 |
| `process_rss_max` | job=mysql-node | 22,196,224 |
| `process_rss_max` | job=prometheus | 120,623,104 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 5 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 27.72 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.04 |
| `node_cpu_pct_avg` | job=mysql-node | 53.98 |
| `node_load1_avg` | job=backend-node | 0.6 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.73 |
| `node_major_fault_delta` | job=backend-node | 273.14 |
| `node_major_fault_delta` | job=monitoring-node | 46.86 |
| `node_major_fault_delta` | job=mysql-node | 373.71 |
| `node_mem_available_avg` | job=backend-node | 466,312,704 |
| `node_mem_available_avg` | job=monitoring-node | 352,891,904 |
| `node_mem_available_avg` | job=mysql-node | 251,186,688 |
| `node_swap_free_avg` | job=backend-node | 2,547,824,128 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,186,112 |
| `node_swap_free_avg` | job=mysql-node | 2,562,949,120 |
| `node_swap_in_delta` | job=backend-node | 194.29 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 115.43 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 3,590.86 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 166,171.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 565.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 11.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 150 — 2026-08-13T07:30:23.739Z ~ 2026-08-13T07:32:23.739Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,954 | 23.56 | 20.03 | 40.35 | 108.78 | 272.93 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,971 | 10.26 | 9.39 | 14.77 | 26.67 | 110.76 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,886 | 26.59 | 22.09 | 46.32 | 130.79 | 414.69 |
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
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,942 | 47.5 | 34.81 | 123.41 | 323.43 | 561.37 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 15 | 17.84 | 10.14 | 57.88 | 60.79 | 60.51 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.46 | 0.56 | 1.35 | 2.2 | 6.3 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 9.04 | 8.39 | 12.3 | 12.53 | 18.5 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 40 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.34 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 96.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.37 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.47 |
| `process_rss_avg` | job=backend-node | 16,516,608 |
| `process_rss_avg` | job=monitoring-node | 16,883,712 |
| `process_rss_avg` | job=mysql-exporter | 16,313,856 |
| `process_rss_avg` | job=mysql-node | 21,764,608 |
| `process_rss_avg` | job=prometheus | 115,375,104 |
| `process_rss_max` | job=backend-node | 16,551,936 |
| `process_rss_max` | job=monitoring-node | 16,982,016 |
| `process_rss_max` | job=mysql-exporter | 16,723,968 |
| `process_rss_max` | job=mysql-node | 21,884,928 |
| `process_rss_max` | job=prometheus | 117,391,360 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 9 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 45.1 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.22 |
| `node_cpu_pct_avg` | job=mysql-node | 80.28 |
| `node_load1_avg` | job=backend-node | 1.19 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 4.39 |
| `node_major_fault_delta` | job=backend-node | 464 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 348.57 |
| `node_mem_available_avg` | job=backend-node | 494,355,456 |
| `node_mem_available_avg` | job=monitoring-node | 269,445,632 |
| `node_mem_available_avg` | job=mysql-node | 259,187,200 |
| `node_swap_free_avg` | job=backend-node | 2,553,625,600 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,187,136 |
| `node_swap_free_avg` | job=mysql-node | 2,560,798,720 |
| `node_swap_in_delta` | job=backend-node | 1,491.43 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 944 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 277,866.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,403.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 24 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 200 — 2026-08-13T07:32:23.739Z ~ 2026-08-13T07:34:23.739Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,088 | 123.91 | 99.83 | 296.27 | 415.32 | 562.8 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,083 | 64.01 | 29.49 | 207.02 | 289.39 | 532.05 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,241 | 154.26 | 149.35 | 354.3 | 459.78 | 1,020.01 |
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
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,795 | 176.39 | 170.46 | 406.62 | 507.91 | 776.44 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 195 | 155.77 | 140.21 | 349.75 | 481.32 | 559.91 |
| method=POST, status=401, uri=UNKNOWN | 78 | 0.84 | 0.68 | 2.66 | 3.96 | 4.03 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 21 | 87.83 | 82.02 | 203.56 | 219.67 | 219.97 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 54.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.55 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.52 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.63 |
| `process_rss_avg` | job=backend-node | 16,520,192 |
| `process_rss_avg` | job=monitoring-node | 17,047,552 |
| `process_rss_avg` | job=mysql-exporter | 16,643,584 |
| `process_rss_avg` | job=mysql-node | 21,830,656 |
| `process_rss_avg` | job=prometheus | 106,384,896 |
| `process_rss_max` | job=backend-node | 16,793,600 |
| `process_rss_max` | job=monitoring-node | 17,113,088 |
| `process_rss_max` | job=mysql-exporter | 16,994,304 |
| `process_rss_max` | job=mysql-node | 22,024,192 |
| `process_rss_max` | job=prometheus | 121,516,032 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 27.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 62.33 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.82 |
| `node_cpu_pct_avg` | job=mysql-node | 97.21 |
| `node_load1_avg` | job=backend-node | 2.29 |
| `node_load1_avg` | job=monitoring-node | 0.07 |
| `node_load1_avg` | job=mysql-node | 10.15 |
| `node_major_fault_delta` | job=backend-node | 897.14 |
| `node_major_fault_delta` | job=monitoring-node | 13.71 |
| `node_major_fault_delta` | job=mysql-node | 193.14 |
| `node_mem_available_avg` | job=backend-node | 497,871,360 |
| `node_mem_available_avg` | job=monitoring-node | 268,522,496 |
| `node_mem_available_avg` | job=mysql-node | 257,686,528 |
| `node_swap_free_avg` | job=backend-node | 2,555,694,080 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,269,056 |
| `node_swap_free_avg` | job=mysql-node | 2,560,806,912 |
| `node_swap_in_delta` | job=backend-node | 1,121.14 |
| `node_swap_in_delta` | job=monitoring-node | 8 |
| `node_swap_in_delta` | job=mysql-node | 266.29 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 2.29 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 368,480 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 13,340.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 182.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 14.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 300 — 2026-08-13T07:34:23.739Z ~ 2026-08-13T07:36:23.739Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,731 | 197.24 | 178.43 | 331.28 | 438.85 | 4,001.2 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,408 | 135.5 | 125.78 | 275.72 | 357.45 | 4,050.02 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 13,442 | 261.72 | 243.8 | 415.75 | 550.15 | 4,281.14 |
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
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,299 | 306.29 | 282.27 | 469.69 | 616.01 | 4,004.99 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,589 | 146.1 | 131.75 | 274.42 | 385.65 | 3,873.65 |
| method=POST, status=401, uri=UNKNOWN | 27 | 1 | 0.71 | 2.73 | 5.26 | 4.56 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 7 | 143.33 | 134.22 | 239.35 | 244.72 | 231.53 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 66.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.82 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.06 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.54 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.57 |
| `process_rss_avg` | job=backend-node | 16,698,880 |
| `process_rss_avg` | job=monitoring-node | 17,113,088 |
| `process_rss_avg` | job=mysql-exporter | 16,446,976 |
| `process_rss_avg` | job=mysql-node | 21,782,528 |
| `process_rss_avg` | job=prometheus | 117,078,016 |
| `process_rss_max` | job=backend-node | 17,137,664 |
| `process_rss_max` | job=monitoring-node | 17,113,088 |
| `process_rss_max` | job=mysql-exporter | 16,936,960 |
| `process_rss_max` | job=mysql-node | 22,007,808 |
| `process_rss_max` | job=prometheus | 120,926,208 |
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
| `node_cpu_pct_avg` | job=backend-node | 67.26 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.93 |
| `node_cpu_pct_avg` | job=mysql-node | 96.28 |
| `node_load1_avg` | job=backend-node | 4.23 |
| `node_load1_avg` | job=monitoring-node | 0.07 |
| `node_load1_avg` | job=mysql-node | 24.51 |
| `node_major_fault_delta` | job=backend-node | 16,510.86 |
| `node_major_fault_delta` | job=monitoring-node | 52.57 |
| `node_major_fault_delta` | job=mysql-node | 233.14 |
| `node_mem_available_avg` | job=backend-node | 464,016,384 |
| `node_mem_available_avg` | job=monitoring-node | 260,910,592 |
| `node_mem_available_avg` | job=mysql-node | 255,937,536 |
| `node_swap_free_avg` | job=backend-node | 2,556,503,552 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,478,976 |
| `node_swap_free_avg` | job=mysql-node | 2,560,872,960 |
| `node_swap_in_delta` | job=backend-node | 26,790.86 |
| `node_swap_in_delta` | job=monitoring-node | 28.57 |
| `node_swap_in_delta` | job=mysql-node | 444.57 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 11.43 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 356,459.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,835.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 105.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 20.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 400 — 2026-08-13T07:36:23.739Z ~ 2026-08-13T07:38:23.739Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,784 | 181.16 | 173.37 | 299.22 | 391.04 | 4,001.2 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,857 | 124.21 | 120.84 | 242.3 | 334.34 | 4,050.02 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 14,347 | 249.41 | 242.92 | 396.45 | 486.68 | 4,281.14 |
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
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,791 | 294.6 | 276.37 | 517.07 | 629.3 | 4,004.99 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,995 | 135.65 | 128.67 | 253.22 | 349.53 | 3,873.65 |
| method=POST, status=401, uri=UNKNOWN | 16 | 0.61 | 0.7 | 1.5 | 1.7 | 4.56 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 10 | 136.2 | 128.63 | 258.37 | 266.42 | 260.22 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 77.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.93 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 97 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.55 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.57 |
| `process_rss_avg` | job=backend-node | 16,866,304 |
| `process_rss_avg` | job=monitoring-node | 17,048,576 |
| `process_rss_avg` | job=mysql-exporter | 16,557,568 |
| `process_rss_avg` | job=mysql-node | 22,029,312 |
| `process_rss_avg` | job=prometheus | 110,479,360 |
| `process_rss_max` | job=backend-node | 16,969,728 |
| `process_rss_max` | job=monitoring-node | 17,113,088 |
| `process_rss_max` | job=mysql-exporter | 17,293,312 |
| `process_rss_max` | job=mysql-node | 22,405,120 |
| `process_rss_max` | job=prometheus | 112,517,120 |
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
| `node_cpu_pct_avg` | job=backend-node | 64.11 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.26 |
| `node_cpu_pct_avg` | job=mysql-node | 99.8 |
| `node_load1_avg` | job=backend-node | 4.4 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 26.88 |
| `node_major_fault_delta` | job=backend-node | 21.71 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 83.43 |
| `node_mem_available_avg` | job=backend-node | 358,806,528 |
| `node_mem_available_avg` | job=monitoring-node | 281,690,624 |
| `node_mem_available_avg` | job=mysql-node | 253,799,424 |
| `node_swap_free_avg` | job=backend-node | 2,556,821,504 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,489,216 |
| `node_swap_free_avg` | job=mysql-node | 2,561,073,152 |
| `node_swap_in_delta` | job=backend-node | 14.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 118.86 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 361,475.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,981.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 76.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 20.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

---

## round8-ram2gb-bid-only-load-singleHotAuction-20260813.json

- 시나리오: `bid-only-load (SSE 없음)`
- K6 실행: 2026-08-13T07:39:10.113Z ~ 2026-08-13T07:51:21.639Z
- 설정: `{"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m","hotAuctionId":3001001}`

### QPS 50 — 2026-08-13T07:39:10.113Z ~ 2026-08-13T07:41:10.113Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,258 | 25.5 | 21.44 | 56.3 | 92.1 | 561.48 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,143 | 11.36 | 11.11 | 16.56 | 21.77 | 585.73 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,605 | 100.09 | 81.22 | 207.37 | 592.2 | 963.63 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 2 | 1.34 | 1.75 | 2.06 | 2.09 | 1.94 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 48.74 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 578 | 47.86 | 45.72 | 66.02 | 111.14 | 856 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 623 | 19.1 | 14.03 | 48 | 120.52 | 607.08 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.33 | 0.5 | 0.95 | 0.99 | 9.87 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 397.63 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 26.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 14.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.12 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 97 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.13 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.15 |
| `process_rss_avg` | job=backend-node | 17,030,144 |
| `process_rss_avg` | job=monitoring-node | 17,166,848 |
| `process_rss_avg` | job=mysql-exporter | 16,482,304 |
| `process_rss_avg` | job=mysql-node | 21,749,760 |
| `process_rss_avg` | job=prometheus | 99,284,992 |
| `process_rss_max` | job=backend-node | 17,420,288 |
| `process_rss_max` | job=monitoring-node | 17,350,656 |
| `process_rss_max` | job=mysql-exporter | 17,002,496 |
| `process_rss_max` | job=mysql-node | 21,848,064 |
| `process_rss_max` | job=prometheus | 104,366,080 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 5 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 18.95 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.23 |
| `node_cpu_pct_avg` | job=mysql-node | 91.38 |
| `node_load1_avg` | job=backend-node | 2.4 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 8.29 |
| `node_major_fault_delta` | job=backend-node | 42.29 |
| `node_major_fault_delta` | job=monitoring-node | 9.14 |
| `node_major_fault_delta` | job=mysql-node | 179.43 |
| `node_mem_available_avg` | job=backend-node | 477,674,496 |
| `node_mem_available_avg` | job=monitoring-node | 373,267,456 |
| `node_mem_available_avg` | job=mysql-node | 250,622,976 |
| `node_swap_free_avg` | job=backend-node | 2,609,333,760 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,499,456 |
| `node_swap_free_avg` | job=mysql-node | 2,561,125,376 |
| `node_swap_in_delta` | job=backend-node | 44.57 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 404.57 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 1.14 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 94,684.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,458.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 32 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 6.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 100 — 2026-08-13T07:41:10.113Z ~ 2026-08-13T07:43:10.113Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,410 | 361.55 | 379.31 | 750.76 | 893.09 | 1,460.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,245 | 287.13 | 301.43 | 679.94 | 802.24 | 1,067.8 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,907 | 1,007.43 | 1,121.04 | 1,516.25 | 1,742.86 | 2,005.78 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 1,854.86 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 449.96 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 328.89 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 2,005.58 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 1.94 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 2.12 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0.58 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.88 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.52 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 2.44 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 1.12 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 48.74 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 69 | 193.21 | 89.48 | 608.45 | 688.98 | 698.86 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,030 | 369.54 | 391.83 | 744.65 | 877.95 | 1,094.42 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 17.54 |
| method=POST, status=401, uri=UNKNOWN | 40 | 0.76 | 0.56 | 1.49 | 12.09 | 11.58 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 397.63 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 16 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.16 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.16 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.22 |
| `process_rss_avg` | job=backend-node | 16,965,632 |
| `process_rss_avg` | job=monitoring-node | 16,908,288 |
| `process_rss_avg` | job=mysql-exporter | 16,433,152 |
| `process_rss_avg` | job=mysql-node | 21,791,232 |
| `process_rss_avg` | job=prometheus | 99,227,648 |
| `process_rss_max` | job=backend-node | 16,965,632 |
| `process_rss_max` | job=monitoring-node | 17,055,744 |
| `process_rss_max` | job=mysql-exporter | 16,764,928 |
| `process_rss_max` | job=mysql-node | 22,220,800 |
| `process_rss_max` | job=prometheus | 100,712,448 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 40.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 21.52 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 99.99 |
| `node_load1_avg` | job=backend-node | 0.8 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 16.77 |
| `node_major_fault_delta` | job=backend-node | 684.57 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 176 |
| `node_mem_available_avg` | job=backend-node | 476,189,184 |
| `node_mem_available_avg` | job=monitoring-node | 397,743,104 |
| `node_mem_available_avg` | job=mysql-node | 242,676,224 |
| `node_swap_free_avg` | job=backend-node | 2,609,836,032 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,505,600 |
| `node_swap_free_avg` | job=mysql-node | 2,561,126,400 |
| `node_swap_in_delta` | job=backend-node | 1,521.14 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 392 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 85,483.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 3 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 34,284.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 499.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 2.29 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 25.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 150 — 2026-08-13T07:43:10.113Z ~ 2026-08-13T07:45:10.113Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,399 | 421.44 | 409.93 | 775.76 | 993.5 | 1,460.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,264 | 362.3 | 351.25 | 730.81 | 858.46 | 1,491.61 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,953 | 1,152.59 | 1,195.82 | 1,630.07 | 1,779.23 | 2,154.07 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 1,854.86 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 449.96 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 328.89 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 2,005.58 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0.74 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 2.12 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0.58 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0.88 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0.52 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 2.44 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 1.12 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 5 | 604.02 | 536.87 | 787.41 | 801.73 | 759.22 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 898 | 430.86 | 415.98 | 788.99 | 1,028.02 | 1,223.62 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 17.54 |
| method=POST, status=401, uri=UNKNOWN | 7 | 0.54 | 0.6 | 1.64 | 1.73 | 11.58 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 547.04 | 581.61 | 621.88 | 625.45 | 547.04 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 16 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 8 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.19 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.09 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 96.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.16 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.19 |
| `process_rss_avg` | job=backend-node | 16,923,136 |
| `process_rss_avg` | job=monitoring-node | 16,933,888 |
| `process_rss_avg` | job=mysql-exporter | 16,347,648 |
| `process_rss_avg` | job=mysql-node | 21,508,608 |
| `process_rss_avg` | job=prometheus | 100,712,448 |
| `process_rss_max` | job=backend-node | 17,076,224 |
| `process_rss_max` | job=monitoring-node | 17,055,744 |
| `process_rss_max` | job=mysql-exporter | 16,715,776 |
| `process_rss_max` | job=mysql-node | 21,827,584 |
| `process_rss_max` | job=prometheus | 100,712,448 |
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
| `node_cpu_pct_avg` | job=backend-node | 21.12 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.23 |
| `node_cpu_pct_avg` | job=mysql-node | 99.99 |
| `node_load1_avg` | job=backend-node | 0.87 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 27.36 |
| `node_major_fault_delta` | job=backend-node | 72 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 733.71 |
| `node_mem_available_avg` | job=backend-node | 430,477,824 |
| `node_mem_available_avg` | job=monitoring-node | 403,373,056 |
| `node_mem_available_avg` | job=mysql-node | 246,052,352 |
| `node_swap_free_avg` | job=backend-node | 2,609,836,032 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,505,600 |
| `node_swap_free_avg` | job=mysql-node | 2,560,466,944 |
| `node_swap_in_delta` | job=backend-node | 74.29 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 504 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 2,307.43 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 82,389.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 19,964.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 382.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 27.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 200 — 2026-08-13T07:45:10.113Z ~ 2026-08-13T07:47:10.113Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,360 | 404.69 | 394.58 | 713.66 | 881.71 | 1,476.13 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,331 | 337.64 | 337.15 | 679.93 | 856.96 | 1,491.61 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,920 | 1,119.38 | 1,151.87 | 1,502.34 | 1,747.87 | 2,202.29 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 5 | 500.84 | 500 | 617.4 | 624.56 | 759.22 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,325 | 461.11 | 451.28 | 793.73 | 958.59 | 1,238.37 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 6 | 0.46 | 0.5 | 0.95 | 0.99 | 1.62 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 183.31 | 190.14 | 200.21 | 201.1 | 547.04 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 16 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 9.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.19 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.1 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.15 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.16 |
| `process_rss_avg` | job=backend-node | 16,968,704 |
| `process_rss_avg` | job=monitoring-node | 16,916,480 |
| `process_rss_avg` | job=mysql-exporter | 16,445,952 |
| `process_rss_avg` | job=mysql-node | 21,745,664 |
| `process_rss_avg` | job=prometheus | 98,767,872 |
| `process_rss_max` | job=backend-node | 17,076,224 |
| `process_rss_max` | job=monitoring-node | 16,916,480 |
| `process_rss_max` | job=mysql-exporter | 17,031,168 |
| `process_rss_max` | job=mysql-node | 21,913,600 |
| `process_rss_max` | job=prometheus | 100,712,448 |
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
| `node_cpu_pct_avg` | job=backend-node | 20.9 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 99.99 |
| `node_load1_avg` | job=backend-node | 0.48 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 29.02 |
| `node_major_fault_delta` | job=backend-node | 682.29 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 49.14 |
| `node_mem_available_avg` | job=backend-node | 405,617,152 |
| `node_mem_available_avg` | job=monitoring-node | 403,867,648 |
| `node_mem_available_avg` | job=mysql-node | 249,890,816 |
| `node_swap_free_avg` | job=backend-node | 2,609,836,032 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,505,600 |
| `node_swap_free_avg` | job=mysql-node | 2,559,839,232 |
| `node_swap_in_delta` | job=backend-node | 312 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 156.57 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 86,142.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 4 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 114,381.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 960 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 27.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 300 — 2026-08-13T07:47:10.113Z ~ 2026-08-13T07:49:10.113Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,218 | 433.37 | 419.66 | 776.28 | 933.62 | 1,476.13 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,318 | 354.74 | 347.54 | 721.84 | 868.74 | 1,147.87 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,945 | 1,156.73 | 1,191.82 | 1,587.39 | 1,764.53 | 2,202.29 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2 | 645.75 | 626.35 | 796.36 | 803.52 | 742.13 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 962 | 446.65 | 437.75 | 785.17 | 964.58 | 1,238.37 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 5 | 0.45 | 0.5 | 0.95 | 0.99 | 1.62 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 613.28 | 357.91 | 885.84 | 893 | 950.88 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.22 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.05 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.15 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_rss_avg` | job=backend-node | 17,002,496 |
| `process_rss_avg` | job=monitoring-node | 16,916,480 |
| `process_rss_avg` | job=mysql-exporter | 16,482,816 |
| `process_rss_avg` | job=mysql-node | 21,696,000 |
| `process_rss_avg` | job=prometheus | 98,447,360 |
| `process_rss_max` | job=backend-node | 17,072,128 |
| `process_rss_max` | job=monitoring-node | 16,916,480 |
| `process_rss_max` | job=mysql-exporter | 16,977,920 |
| `process_rss_max` | job=mysql-node | 21,786,624 |
| `process_rss_max` | job=prometheus | 98,775,040 |
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
| `node_cpu_pct_avg` | job=backend-node | 23.56 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 100 |
| `node_load1_avg` | job=backend-node | 0.34 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 28.63 |
| `node_major_fault_delta` | job=backend-node | 1,024 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 46.86 |
| `node_mem_available_avg` | job=backend-node | 449,812,480 |
| `node_mem_available_avg` | job=monitoring-node | 404,203,008 |
| `node_mem_available_avg` | job=mysql-node | 250,444,288 |
| `node_swap_free_avg` | job=backend-node | 2,696,486,912 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,505,600 |
| `node_swap_free_avg` | job=mysql-node | 2,559,934,464 |
| `node_swap_in_delta` | job=backend-node | 908.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 98.29 |
| `node_swap_out_delta` | job=backend-node | 632 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 81,120 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 3 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 48,937.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 518.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 28.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 400 — 2026-08-13T07:49:10.113Z ~ 2026-08-13T07:51:10.113Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,274 | 415.24 | 399.64 | 768.31 | 999.41 | 1,319.97 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,299 | 347.17 | 337.72 | 701.36 | 924.2 | 1,329.79 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,835 | 1,131.11 | 1,154.31 | 1,694.58 | 1,908.3 | 2,498.2 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 2 | 230.15 | 360.98 | 444.27 | 484.12 | 428.68 |
| method=GET, status=200, uri=/api/statistic/market | 2 | 253.07 | 357.91 | 494.74 | 498.95 | 485.34 |
| method=GET, status=200, uri=/api/statistic/price-movers | 2 | 1,864.45 | 2,004.73 | 2,133.21 | 2,144.63 | 2,077.68 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 5 | 766.94 | 686 | 1,058.99 | 1,070.79 | 1,057.8 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,179 | 579.32 | 576.57 | 917.56 | 1,048.04 | 1,183.08 |
| method=POST, status=401, uri=/api/auth/refresh | 2 | 1.19 | 1.41 | 1.74 | 20.68 | 1.52 |
| method=POST, status=401, uri=UNKNOWN | 6 | 0.36 | 0.5 | 0.95 | 0.99 | 0.89 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 745.3 | 760.57 | 800.83 | 804.41 | 950.88 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 14.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.17 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.05 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 96.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 97 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.15 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_rss_avg` | job=backend-node | 17,072,128 |
| `process_rss_avg` | job=monitoring-node | 16,932,864 |
| `process_rss_avg` | job=mysql-exporter | 16,393,216 |
| `process_rss_avg` | job=mysql-node | 21,710,848 |
| `process_rss_avg` | job=prometheus | 108,597,248 |
| `process_rss_max` | job=backend-node | 17,072,128 |
| `process_rss_max` | job=monitoring-node | 17,047,552 |
| `process_rss_max` | job=mysql-exporter | 16,785,408 |
| `process_rss_max` | job=mysql-node | 21,864,448 |
| `process_rss_max` | job=prometheus | 114,733,056 |
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
| `node_cpu_pct_avg` | job=backend-node | 19.66 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.02 |
| `node_cpu_pct_avg` | job=mysql-node | 99.99 |
| `node_load1_avg` | job=backend-node | 0.22 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 27.6 |
| `node_major_fault_delta` | job=backend-node | 131.43 |
| `node_major_fault_delta` | job=monitoring-node | 24 |
| `node_major_fault_delta` | job=mysql-node | 108.57 |
| `node_mem_available_avg` | job=backend-node | 557,449,216 |
| `node_mem_available_avg` | job=monitoring-node | 349,691,392 |
| `node_mem_available_avg` | job=mysql-node | 248,738,816 |
| `node_swap_free_avg` | job=backend-node | 2,813,272,576 |
| `node_swap_free_avg` | job=monitoring-node | 3,012,508,160 |
| `node_swap_free_avg` | job=mysql-node | 2,559,934,464 |
| `node_swap_in_delta` | job=backend-node | 100.57 |
| `node_swap_in_delta` | job=monitoring-node | 4.57 |
| `node_swap_in_delta` | job=mysql-node | 109.71 |
| `node_swap_out_delta` | job=backend-node | 0 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 83,173.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 19 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 277,797.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,008 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 2.29 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 29 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

> 이 문서는 codex의 도움을 받아 작성하였습니다
