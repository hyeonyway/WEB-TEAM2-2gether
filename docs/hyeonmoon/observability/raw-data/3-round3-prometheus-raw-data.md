# 3차 부하테스트 — Prometheus 원시 집계 데이터

이 문서는 K6 결과 JSON의 실제 종료 시각과 실행 시간을 기준으로 stage를 재구성하고, 각 구간 끝 시점에 Prometheus range/vector query를 평가해 만든 원시 집계표다. p50/p95/p99는 서버의 `http_server_requests_seconds_bucket` histogram으로 계산했다. 값은 Prometheus 원 단위(시간은 ms 변환)를 유지하며, `N/A`/빈 표는 그 시간대에 해당 시계열이 없었음을 뜻한다.

수집 범위는 테스트 대상 백엔드, backend/mysql/redis node exporter, MySQL exporter, Redis exporter다. Grafana/Prometheus 자기 관측 메트릭과 정적 build/info/config 시계열은 성능 측정값이 아니므로 제외했다.

## 실행 목록

| 결과 파일 | 시나리오 | 실제 실행 (UTC) | K6 전체 | 평균 지연 | med | p95 | p99 | max |
|---|---|---|---:|---:|---:|---:|---:|---:|
| [`baseline-pre-redis-pure-throughput-sse250-20260811.json`](../../../../backend/src/test/k6/result/baseline-pre-redis-pure-throughput-sse250-20260811.json) | pure-throughput | 2026-08-11T04:40:03.684Z ~ 2026-08-11T04:53:44.856Z | | 115,997 | 141.26 req/s | 3,115.81 | 50.51 | 17,257.27 | 31,800.39 | 60,007.22 |
| [`baseline-post-jvm-fix-pure-throughput-sse250-20260811.json`](../../../../backend/src/test/k6/result/baseline-post-jvm-fix-pure-throughput-sse250-20260811.json) | pure-throughput | 2026-08-11T05:36:49.328Z ~ 2026-08-11T05:50:30.917Z | | 113,902 | 138.64 req/s | 4,961.02 | 201.83 | 21,487.66 | 37,358.6 | 60,009.86 |
| [`baseline-post-parallelgc-pure-throughput-sse250-20260811.json`](../../../../backend/src/test/k6/result/baseline-post-parallelgc-pure-throughput-sse250-20260811.json) | pure-throughput | 2026-08-11T06:00:02.696Z ~ 2026-08-11T06:07:44.810Z | | 48,726 | 105.44 req/s | 15,007.94 | 14,450.64 | 60,000.12 | 60,000.81 | 60,071.79 |
| [`baseline-post-g1-pure-throughput-sse500-20260811.json`](../../../../backend/src/test/k6/result/baseline-post-g1-pure-throughput-sse500-20260811.json) | pure-throughput | 2026-08-11T06:13:37.016Z ~ 2026-08-11T06:27:22.885Z | | 98,343 | 119.08 req/s | 11,010.27 | 11,330.16 | 31,754.98 | 42,885.18 | 60,009.17 |
| [`baseline-post-g1-pure-throughput-sse1000-20260811.json`](../../../../backend/src/test/k6/result/baseline-post-g1-pure-throughput-sse1000-20260811.json) | pure-throughput | 2026-08-11T06:27:48.642Z ~ 2026-08-11T06:34:46.997Z | | 25,070 | 59.93 req/s | 20,637.58 | 10,142.52 | 60,000.5 | 60,030.93 | 126,089.21 |
| [`baseline-post-g1-pure-throughput-sse500-lowqps-20260811.json`](../../../../backend/src/test/k6/result/baseline-post-g1-pure-throughput-sse500-lowqps-20260811.json) | pure-throughput | 2026-08-11T06:39:47.527Z ~ 2026-08-11T06:49:29.449Z | | 58,586 | 100.68 req/s | 2,130.13 | 80.58 | 11,514.79 | 13,650.7 | 23,737.5 |
| [`baseline-post-g1-pure-throughput-sse1000-lowqps-20260811.json`](../../../../backend/src/test/k6/result/baseline-post-g1-pure-throughput-sse1000-lowqps-20260811.json) | pure-throughput | 2026-08-11T06:51:17.739Z ~ 2026-08-11T07:01:01.544Z | | 54,368 | 93.13 req/s | 15,499.28 | 11,084.5 | 57,278.36 | 60,001 | 79,778.54 |
| [`hot-auction-pattern-sse500-20260811.json`](../../../../backend/src/test/k6/result/hot-auction-pattern-sse500-20260811.json) | hot-auction-pattern | 2026-08-11T07:12:55.166Z ~ 2026-08-11T07:21:00.501Z | | 26,738 | 55.09 req/s | 9,872.68 | 3,580.6 | 56,461.62 | 60,000.23 | 60,007.03 |
| [`baseline-post-g1-pure-throughput-sse250-lowqps-20260811.json`](../../../../backend/src/test/k6/result/baseline-post-g1-pure-throughput-sse250-lowqps-20260811.json) | pure-throughput | 2026-08-11T07:21:29.121Z ~ 2026-08-11T07:31:09.911Z | | 61,597 | 106.06 req/s | 403.19 | 33.75 | 1,556.77 | 10,872.43 | 17,143.64 |
| [`bid-only-load-noSSE-20260811.json`](../../../../backend/src/test/k6/result/bid-only-load-noSSE-20260811.json) | bid-only-load (SSE 없음) | 2026-08-11T09:22:54.958Z ~ 2026-08-11T09:35:07.035Z | | 126,206 | 172.39 req/s | 2,881.5 | 51.27 | 12,269.69 | 17,751.18 | 31,459.53 |
| [`bid-only-load-singleHotAuction-20260811.json`](../../../../backend/src/test/k6/result/bid-only-load-singleHotAuction-20260811.json) | bid-only-load (SSE 없음) | 2026-08-11T09:36:57.791Z ~ 2026-08-11T09:49:09.910Z | | 69,412 | 94.81 req/s | 10,304.2 | 5,730.52 | 26,180.93 | 35,901.35 | 36,936.26 |
| [`baseline-post-g1-pure-throughput-sse250-lowqps-rerun-20260811.json`](../../../../backend/src/test/k6/result/baseline-post-g1-pure-throughput-sse250-lowqps-rerun-20260811.json) | pure-throughput | 2026-08-11T10:23:40.799Z ~ 2026-08-11T10:33:24.237Z | | 54,550 | 93.5 req/s | 5,972.54 | 4,701.21 | 20,393.91 | 31,508.55 | 42,940.62 |
| [`baseline-post-g1-pure-throughput-sse250-lowqps-postreboot-20260811.json`](../../../../backend/src/test/k6/result/baseline-post-g1-pure-throughput-sse250-lowqps-postreboot-20260811.json) | pure-throughput | 2026-08-11T10:50:06.144Z ~ 2026-08-11T10:59:49.624Z | | 52,848 | 90.57 req/s | 4,166.22 | 113.35 | 20,284.53 | 45,387.21 | 55,847.39 |
| [`baseline-serial-noheap-pure-throughput-sse250-lowqps-20260811.json`](../../../../backend/src/test/k6/result/baseline-serial-noheap-pure-throughput-sse250-lowqps-20260811.json) | pure-throughput | 2026-08-11T11:18:30.901Z ~ 2026-08-11T11:28:11.820Z | | 51,299 | 88.31 req/s | 343.1 | 18.18 | 3,227.45 | 4,402.64 | 8,935.64 |

---

## baseline-pre-redis-pure-throughput-sse250-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T04:40:03.684Z ~ 2026-08-11T04:53:44.856Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T04:40:38.684Z ~ 2026-08-11T04:42:38.684Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,258 | 13.29 | 12.37 | 20.5 | 27.3 | 416.75 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,143 | 6.81 | 6.4 | 10.29 | 14.96 | 20.16 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,601 | 8.9 | 8.21 | 13.51 | 19.76 | 52.22 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 7.02 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 75,499.62 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 3.21 |
| method=GET, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 7.12 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 8.05 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 139.94 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 75,412 |
| method=GET, status=200, uri=/api/wallet | 5 | 5.42 | 4.89 | 8.11 | 8.33 | 21.17 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0.35 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0.29 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0.49 |
| method=OPTIONS, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 95.71 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 13.55 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 436.72 |
| method=POST, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,048 | 22.43 | 20.99 | 34.04 | 63.6 | 427.47 |
| method=POST, status=201, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 146 | 7.07 | 6.71 | 8.84 | 13.59 | 19.55 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 120 | 0.36 | 0.53 | 1 | 1.67 | 1.63 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | N/A | N/A | N/A | N/A | N/A | 5.66 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 5.34 | 5.59 | 6.85 | 6.96 | 6.99 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 65.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 0.45 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 83.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 86 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.24 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_rss_avg` | job=backend-node | 15,204,352 |
| `process_rss_avg` | job=monitoring-node | 18,925,568 |
| `process_rss_avg` | job=mysql-exporter | 16,819,200 |
| `process_rss_avg` | job=mysql-node | 22,566,400 |
| `process_rss_avg` | job=prometheus | 109,436,928 |
| `process_rss_avg` | job=redis-exporter | 18,042,880 |
| `process_rss_avg` | job=redis-node | 22,284,800 |
| `process_rss_max` | job=backend-node | 15,417,344 |
| `process_rss_max` | job=monitoring-node | 18,944,000 |
| `process_rss_max` | job=mysql-exporter | 17,108,992 |
| `process_rss_max` | job=mysql-node | 22,683,648 |
| `process_rss_max` | job=prometheus | 109,568,000 |
| `process_rss_max` | job=redis-exporter | 18,157,568 |
| `process_rss_max` | job=redis-node | 22,573,056 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 0.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 29 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 37.68 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 21.06 |
| `node_cpu_pct_avg` | job=redis-node | 0.56 |
| `node_load1_avg` | job=backend-node | 0.33 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.09 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 2,909.71 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 994.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 78,136,832 |
| `node_mem_available_avg` | job=monitoring-node | 412,274,176 |
| `node_mem_available_avg` | job=mysql-node | 226,564,608 |
| `node_mem_available_avg` | job=redis-node | 567,460,352 |
| `node_swap_free_avg` | job=backend-node | 2,640,409,600 |
| `node_swap_free_avg` | job=monitoring-node | 3,157,344,256 |
| `node_swap_free_avg` | job=mysql-node | 2,759,921,664 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 6,353.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 1,277.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 3,850.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 108,801.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,264 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T04:42:38.684Z ~ 2026-08-11T04:44:38.684Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,731 | 12.7 | 11.81 | 19.97 | 27.08 | 416.75 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,803 | 6.44 | 6.18 | 9.53 | 13.17 | 20.77 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,303 | 9.08 | 8.32 | 13.87 | 20.66 | 52.22 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 21.17 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 95.71 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 436.72 |
| method=POST, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 872 | 21.12 | 20.13 | 27.34 | 37.46 | 77.68 |
| method=POST, status=201, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 889 | 7.1 | 6.62 | 9.68 | 15.48 | 35.62 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 120 | 0.37 | 0.53 | 1 | 2.08 | 2.13 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 2 | 5.01 | 4.93 | 5.59 | 6.71 | 5.66 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 5.83 | 6.29 | 6.92 | 6.98 | 6.99 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 78.86 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 0.5 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 83 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 84 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.27 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.33 |
| `process_rss_avg` | job=backend-node | 15,213,056 |
| `process_rss_avg` | job=monitoring-node | 18,757,632 |
| `process_rss_avg` | job=mysql-exporter | 16,917,504 |
| `process_rss_avg` | job=mysql-node | 22,500,352 |
| `process_rss_avg` | job=prometheus | 115,639,808 |
| `process_rss_avg` | job=redis-exporter | 18,105,344 |
| `process_rss_avg` | job=redis-node | 22,254,592 |
| `process_rss_max` | job=backend-node | 15,286,272 |
| `process_rss_max` | job=monitoring-node | 18,796,544 |
| `process_rss_max` | job=mysql-exporter | 17,121,280 |
| `process_rss_max` | job=mysql-node | 22,740,992 |
| `process_rss_max` | job=prometheus | 131,928,064 |
| `process_rss_max` | job=redis-exporter | 18,419,712 |
| `process_rss_max` | job=redis-node | 22,274,048 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 1.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 29 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 38.01 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.77 |
| `node_cpu_pct_avg` | job=mysql-node | 23.44 |
| `node_cpu_pct_avg` | job=redis-node | 0.51 |
| `node_load1_avg` | job=backend-node | 0.69 |
| `node_load1_avg` | job=monitoring-node | 0.05 |
| `node_load1_avg` | job=mysql-node | 0.19 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 246.86 |
| `node_major_fault_delta` | job=monitoring-node | 21.71 |
| `node_major_fault_delta` | job=mysql-node | 100.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 80,516,608 |
| `node_mem_available_avg` | job=monitoring-node | 375,525,888 |
| `node_mem_available_avg` | job=mysql-node | 233,413,120 |
| `node_mem_available_avg` | job=redis-node | 567,377,920 |
| `node_swap_free_avg` | job=backend-node | 2,638,499,840 |
| `node_swap_free_avg` | job=monitoring-node | 3,157,280,768 |
| `node_swap_free_avg` | job=mysql-node | 2,759,925,760 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 265.14 |
| `node_swap_in_delta` | job=monitoring-node | 17.14 |
| `node_swap_in_delta` | job=mysql-node | 13.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 14.86 |
| `node_swap_out_delta` | job=monitoring-node | 85.71 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 147,046.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,827.43 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T04:44:38.684Z ~ 2026-08-11T04:46:38.684Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,963 | 15.18 | 13.4 | 24.74 | 41.6 | 132.6 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,971 | 7.43 | 6.75 | 12.44 | 17.78 | 25.29 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,901 | 10.87 | 9.58 | 18.14 | 27.13 | 158.55 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,345 | 25.14 | 23.56 | 37.04 | 49.61 | 156.54 |
| method=POST, status=201, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,606 | 9.31 | 8.19 | 16.34 | 24.38 | 75.94 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 120 | 0.51 | 0.58 | 1.66 | 2.09 | 3.73 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 7 | 6.88 | 5.59 | 12.16 | 12.5 | 11.74 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 7.68 | 6.99 | 10.63 | 11.07 | 10.43 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 128 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 0.87 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 83 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 84 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.42 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.49 |
| `process_rss_avg` | job=backend-node | 15,167,488 |
| `process_rss_avg` | job=monitoring-node | 18,591,744 |
| `process_rss_avg` | job=mysql-exporter | 17,021,440 |
| `process_rss_avg` | job=mysql-node | 22,542,336 |
| `process_rss_avg` | job=prometheus | 126,707,712 |
| `process_rss_avg` | job=redis-exporter | 18,083,840 |
| `process_rss_avg` | job=redis-node | 22,183,424 |
| `process_rss_max` | job=backend-node | 15,667,200 |
| `process_rss_max` | job=monitoring-node | 18,903,040 |
| `process_rss_max` | job=mysql-exporter | 17,432,576 |
| `process_rss_max` | job=mysql-node | 22,675,456 |
| `process_rss_max` | job=prometheus | 130,740,224 |
| `process_rss_max` | job=redis-exporter | 18,231,296 |
| `process_rss_max` | job=redis-node | 22,511,616 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 29 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 59.03 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.47 |
| `node_cpu_pct_avg` | job=mysql-node | 35.31 |
| `node_cpu_pct_avg` | job=redis-node | 0.8 |
| `node_load1_avg` | job=backend-node | 1.69 |
| `node_load1_avg` | job=monitoring-node | 0.1 |
| `node_load1_avg` | job=mysql-node | 0.62 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 275.43 |
| `node_major_fault_delta` | job=monitoring-node | 30.86 |
| `node_major_fault_delta` | job=mysql-node | 113.14 |
| `node_major_fault_delta` | job=redis-node | 1.14 |
| `node_mem_available_avg` | job=backend-node | 85,972,992 |
| `node_mem_available_avg` | job=monitoring-node | 249,307,648 |
| `node_mem_available_avg` | job=mysql-node | 235,432,960 |
| `node_mem_available_avg` | job=redis-node | 560,998,400 |
| `node_swap_free_avg` | job=backend-node | 2,637,922,816 |
| `node_swap_free_avg` | job=monitoring-node | 3,156,844,544 |
| `node_swap_free_avg` | job=mysql-node | 2,759,940,096 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 273.14 |
| `node_swap_in_delta` | job=monitoring-node | 14.86 |
| `node_swap_in_delta` | job=mysql-node | 13.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 571.43 |
| `node_swap_out_delta` | job=monitoring-node | 82.29 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 239,504 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 46.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 6.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,740.57 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T04:46:38.684Z ~ 2026-08-11T04:48:38.684Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,114 | 23.98 | 20.38 | 49.16 | 75.77 | 132.6 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,217 | 9.23 | 8.01 | 18.08 | 27.02 | 48.93 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,497 | 15.36 | 12.51 | 32.21 | 49.07 | 165.49 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 1 | 4.77 | 4.89 | 5.52 | 5.58 | 4.77 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,965 | 34.3 | 30.11 | 60.89 | 99.75 | 161.33 |
| method=POST, status=201, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,190 | 14.05 | 11.61 | 29.53 | 46.89 | 91.52 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 120 | 0.8 | 0.63 | 2.71 | 8.32 | 11.51 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 5 | 5.54 | 5.59 | 8.11 | 8.33 | 11.74 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 11.85 | 9.09 | 26 | 27.57 | 24.17 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 180.57 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 1.43 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 83.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 84 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.58 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.64 |
| `process_rss_avg` | job=backend-node | 14,774,272 |
| `process_rss_avg` | job=monitoring-node | 18,165,248 |
| `process_rss_avg` | job=mysql-exporter | 16,860,160 |
| `process_rss_avg` | job=mysql-node | 22,591,488 |
| `process_rss_avg` | job=prometheus | 134,833,152 |
| `process_rss_avg` | job=redis-exporter | 17,777,664 |
| `process_rss_avg` | job=redis-node | 22,179,840 |
| `process_rss_max` | job=backend-node | 14,872,576 |
| `process_rss_max` | job=monitoring-node | 18,378,752 |
| `process_rss_max` | job=mysql-exporter | 17,170,432 |
| `process_rss_max` | job=mysql-node | 22,794,240 |
| `process_rss_max` | job=prometheus | 138,113,024 |
| `process_rss_max` | job=redis-exporter | 18,231,296 |
| `process_rss_max` | job=redis-node | 22,245,376 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 9 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 29 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 79.96 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.98 |
| `node_cpu_pct_avg` | job=mysql-node | 48.19 |
| `node_cpu_pct_avg` | job=redis-node | 0.67 |
| `node_load1_avg` | job=backend-node | 3.97 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 1.43 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 211.43 |
| `node_major_fault_delta` | job=monitoring-node | 49.14 |
| `node_major_fault_delta` | job=mysql-node | 187.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 93,006,336 |
| `node_mem_available_avg` | job=monitoring-node | 239,721,984 |
| `node_mem_available_avg` | job=mysql-node | 234,235,392 |
| `node_mem_available_avg` | job=redis-node | 564,739,584 |
| `node_swap_free_avg` | job=backend-node | 2,635,933,696 |
| `node_swap_free_avg` | job=monitoring-node | 3,156,430,336 |
| `node_swap_free_avg` | job=mysql-node | 2,759,938,048 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 202.29 |
| `node_swap_in_delta` | job=monitoring-node | 30.86 |
| `node_swap_in_delta` | job=mysql-node | 77.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 1,156.57 |
| `node_swap_out_delta` | job=monitoring-node | 91.43 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 332,020.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 456 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 32 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 3,990.86 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T04:48:38.684Z ~ 2026-08-11T04:50:38.684Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,930 | 142.84 | 124.2 | 280.62 | 358.13 | 692.33 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,855 | 67.94 | 52.11 | 177.61 | 241.31 | 350.3 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 17,671 | 97.45 | 81.09 | 219.98 | 288.77 | 457.17 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 4.77 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,289 | 172.73 | 161.09 | 323.86 | 406.41 | 539.78 |
| method=POST, status=201, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 3,530 | 98.04 | 79.75 | 223.59 | 294.06 | 406.2 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 120 | 3.85 | 0.99 | 13.11 | 27.82 | 38.85 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 9 | 53.85 | 44.74 | 147.64 | 154.8 | 146.27 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 14 | 82.07 | 59.65 | 210.27 | 221.01 | 217.41 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 16.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 233.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 3.2 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.79 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.83 |
| `process_rss_avg` | job=backend-node | 14,995,968 |
| `process_rss_avg` | job=monitoring-node | 17,940,480 |
| `process_rss_avg` | job=mysql-exporter | 16,829,440 |
| `process_rss_avg` | job=mysql-node | 22,676,480 |
| `process_rss_avg` | job=prometheus | 134,224,896 |
| `process_rss_avg` | job=redis-exporter | 17,686,528 |
| `process_rss_avg` | job=redis-node | 22,210,560 |
| `process_rss_max` | job=backend-node | 15,396,864 |
| `process_rss_max` | job=monitoring-node | 18,071,552 |
| `process_rss_max` | job=mysql-exporter | 17,350,656 |
| `process_rss_max` | job=mysql-node | 22,994,944 |
| `process_rss_max` | job=prometheus | 137,588,736 |
| `process_rss_max` | job=redis-exporter | 17,817,600 |
| `process_rss_max` | job=redis-node | 22,245,376 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 27.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 45.13 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.37 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.07 |
| `node_cpu_pct_avg` | job=mysql-node | 64.28 |
| `node_cpu_pct_avg` | job=redis-node | 0.69 |
| `node_load1_avg` | job=backend-node | 18.24 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 2.19 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 580.57 |
| `node_major_fault_delta` | job=monitoring-node | 42.29 |
| `node_major_fault_delta` | job=mysql-node | 227.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 92,730,880 |
| `node_mem_available_avg` | job=monitoring-node | 234,039,808 |
| `node_mem_available_avg` | job=mysql-node | 231,616,512 |
| `node_mem_available_avg` | job=redis-node | 566,792,192 |
| `node_swap_free_avg` | job=backend-node | 2,632,840,192 |
| `node_swap_free_avg` | job=monitoring-node | 3,155,951,616 |
| `node_swap_free_avg` | job=mysql-node | 2,759,027,200 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 627.43 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 101.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 2,139.43 |
| `node_swap_out_delta` | job=monitoring-node | 137.14 |
| `node_swap_out_delta` | job=mysql-node | 522.29 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 455,582.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 15,261.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 451.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 4,649.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T04:50:38.684Z ~ 2026-08-11T04:52:38.684Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,781 | 444.05 | 307.9 | 741.93 | 1,960.54 | 20,787.53 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,914 | 230.89 | 128.53 | 587.38 | 784.6 | 20,671.49 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,594 | 304.81 | 169.38 | 629.37 | 971.29 | 20,705.96 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/participating-auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/dashboard/recent-wins | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/orders/purchases | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wishlists | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 247 | 603.69 | 513.52 | 792.33 | 10,455.86 | 20,664.68 |
| method=POST, status=201, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=204, uri=/api/auth/logout | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,121 | 294.61 | 156.81 | 624.52 | 1,001.18 | 20,723.32 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 17 | 180.68 | 15.38 | 1,539.03 | 1,739.46 | 1,529.86 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 4 | 88.55 | 78.29 | 130.86 | 133.55 | 146.27 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 255.39 | 89.48 | 438.44 | 445.6 | 425.82 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.86 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.29 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 113.03 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 48.92 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 108.43 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.55 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.72 |
| `process_rss_avg` | job=backend-node | 12,431,360 |
| `process_rss_avg` | job=monitoring-node | 18,169,856 |
| `process_rss_avg` | job=mysql-exporter | 16,823,296 |
| `process_rss_avg` | job=mysql-node | 22,543,872 |
| `process_rss_avg` | job=prometheus | 135,510,528 |
| `process_rss_avg` | job=redis-exporter | 17,883,136 |
| `process_rss_avg` | job=redis-node | 22,022,144 |
| `process_rss_max` | job=backend-node | 14,987,264 |
| `process_rss_max` | job=monitoring-node | 18,202,624 |
| `process_rss_max` | job=mysql-exporter | 17,338,368 |
| `process_rss_max` | job=mysql-node | 22,859,776 |
| `process_rss_max` | job=prometheus | 138,158,080 |
| `process_rss_max` | job=redis-exporter | 17,948,672 |
| `process_rss_max` | job=redis-node | 22,097,920 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.14 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 80.21 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.08 |
| `node_cpu_pct_avg` | job=mysql-node | 23.86 |
| `node_cpu_pct_avg` | job=redis-node | 0.41 |
| `node_load1_avg` | job=backend-node | 23.15 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 2.26 |
| `node_load1_avg` | job=redis-node | 0.07 |
| `node_major_fault_delta` | job=backend-node | 112,584 |
| `node_major_fault_delta` | job=monitoring-node | 19.43 |
| `node_major_fault_delta` | job=mysql-node | 75.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,263,488 |
| `node_mem_available_avg` | job=monitoring-node | 238,883,328 |
| `node_mem_available_avg` | job=mysql-node | 229,144,064 |
| `node_mem_available_avg` | job=redis-node | 568,243,200 |
| `node_swap_free_avg` | job=backend-node | 2,446,788,096 |
| `node_swap_free_avg` | job=monitoring-node | 3,155,886,080 |
| `node_swap_free_avg` | job=mysql-node | 2,754,265,088 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 179,860.57 |
| `node_swap_in_delta` | job=monitoring-node | 4.57 |
| `node_swap_in_delta` | job=mysql-node | 125.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 140,046.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 191,657.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 8,458.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 173.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 542.86 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-post-jvm-fix-pure-throughput-sse250-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T05:36:49.328Z ~ 2026-08-11T05:50:30.917Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T05:37:24.328Z ~ 2026-08-11T05:39:24.328Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,277 | 196.38 | 30.95 | 711.06 | 964.7 | 3,818.71 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,098 | 43.13 | 19.49 | 152.24 | 424.82 | 528.72 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,546 | 84.01 | 22.1 | 395.74 | 592.82 | 3,808.42 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 725.75 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 2.22 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 1 | 25.7 | 25.29 | 27.91 | 84.08 | 77.13 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 692.99 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 17.06 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 897.7 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 973 | 179.27 | 44.54 | 740.19 | 1,049.96 | 5,193.55 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 201 | 101.97 | 9.78 | 551.34 | 750.48 | 784.24 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 96.02 |
| method=POST, status=401, uri=UNKNOWN | 107 | 5.39 | 1.75 | 26 | 68.45 | 82.04 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 5 | 13.7 | 7.24 | 514.13 | 532.32 | 507.83 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 8.15 | 7.78 | 333.77 | 353.09 | 336.66 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 16 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 29.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 101.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.57 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.85 |
| `process_rss_avg` | job=backend-node | 11,908,608 |
| `process_rss_avg` | job=monitoring-node | 17,992,704 |
| `process_rss_avg` | job=mysql-exporter | 16,559,104 |
| `process_rss_avg` | job=mysql-node | 22,507,008 |
| `process_rss_avg` | job=prometheus | 105,832,960 |
| `process_rss_avg` | job=redis-exporter | 18,403,328 |
| `process_rss_avg` | job=redis-node | 22,315,008 |
| `process_rss_max` | job=backend-node | 13,590,528 |
| `process_rss_max` | job=monitoring-node | 18,046,976 |
| `process_rss_max` | job=mysql-exporter | 16,883,712 |
| `process_rss_max` | job=mysql-node | 22,847,488 |
| `process_rss_max` | job=prometheus | 108,027,904 |
| `process_rss_max` | job=redis-exporter | 18,403,328 |
| `process_rss_max` | job=redis-node | 22,315,008 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250.63 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 86.1 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 20.76 |
| `node_cpu_pct_avg` | job=redis-node | 0.53 |
| `node_load1_avg` | job=backend-node | 7.39 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 0.32 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 99,910.86 |
| `node_major_fault_delta` | job=monitoring-node | 6.86 |
| `node_major_fault_delta` | job=mysql-node | 173.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 45,768,192 |
| `node_mem_available_avg` | job=monitoring-node | 367,300,096 |
| `node_mem_available_avg` | job=mysql-node | 256,963,584 |
| `node_mem_available_avg` | job=redis-node | 577,228,800 |
| `node_swap_free_avg` | job=backend-node | 2,370,860,544 |
| `node_swap_free_avg` | job=monitoring-node | 3,148,226,560 |
| `node_swap_free_avg` | job=mysql-node | 2,723,056,128 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 172,177.14 |
| `node_swap_in_delta` | job=monitoring-node | 3.43 |
| `node_swap_in_delta` | job=mysql-node | 179.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 96,835.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 110,554.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,525.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 54.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,174.86 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T05:39:24.328Z ~ 2026-08-11T05:41:24.328Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,745 | 25.77 | 12.53 | 116.58 | 236.6 | 3,818.71 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,670 | 14.64 | 6.46 | 59.88 | 195.32 | 684.2 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,127 | 19.9 | 8.89 | 82.44 | 215.7 | 3,808.42 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 2.22 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 25.7 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 17.06 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 576 | 58.97 | 21.46 | 282.64 | 796.85 | 5,193.55 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,114 | 36.88 | 7.46 | 142.61 | 667.89 | 8,624.72 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 96.02 |
| method=POST, status=401, uri=UNKNOWN | 113 | 0.98 | 0.62 | 3.5 | 16.83 | 82.04 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 3 | 4.76 | 4.89 | 5.52 | 5.58 | 507.83 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 6.24 | 6.32 | 6.98 | 8.05 | 336.66 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 22.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.93 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.33 |
| `process_rss_avg` | job=backend-node | 12,492,288 |
| `process_rss_avg` | job=monitoring-node | 17,895,424 |
| `process_rss_avg` | job=mysql-exporter | 16,630,784 |
| `process_rss_avg` | job=mysql-node | 22,444,544 |
| `process_rss_avg` | job=prometheus | 100,855,808 |
| `process_rss_avg` | job=redis-exporter | 18,128,896 |
| `process_rss_avg` | job=redis-node | 22,444,544 |
| `process_rss_max` | job=backend-node | 14,032,896 |
| `process_rss_max` | job=monitoring-node | 17,895,424 |
| `process_rss_max` | job=mysql-exporter | 16,916,480 |
| `process_rss_max` | job=mysql-node | 22,544,384 |
| `process_rss_max` | job=prometheus | 100,855,808 |
| `process_rss_max` | job=redis-exporter | 18,128,896 |
| `process_rss_max` | job=redis-node | 22,577,152 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 53.1 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.4 |
| `node_cpu_pct_avg` | job=mysql-node | 20.65 |
| `node_cpu_pct_avg` | job=redis-node | 0.49 |
| `node_load1_avg` | job=backend-node | 3.72 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 0.19 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 55,762.29 |
| `node_major_fault_delta` | job=monitoring-node | 50.29 |
| `node_major_fault_delta` | job=mysql-node | 97.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 48,387,072 |
| `node_mem_available_avg` | job=monitoring-node | 412,074,496 |
| `node_mem_available_avg` | job=mysql-node | 257,370,624 |
| `node_mem_available_avg` | job=redis-node | 577,101,824 |
| `node_swap_free_avg` | job=backend-node | 2,300,445,184 |
| `node_swap_free_avg` | job=monitoring-node | 3,148,382,208 |
| `node_swap_free_avg` | job=mysql-node | 2,723,057,664 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 84,869.71 |
| `node_swap_in_delta` | job=monitoring-node | 3.43 |
| `node_swap_in_delta` | job=mysql-node | 12.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 36,869.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 132,294.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 3,675.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 36.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,286.86 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T05:41:24.328Z ~ 2026-08-11T05:43:24.328Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,666 | 234.59 | 131.83 | 734.94 | 2,990.15 | 7,371.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,519 | 104.75 | 55.07 | 283.46 | 1,151.69 | 3,887.33 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 7,735 | 146.47 | 77.6 | 428.32 | 2,111.16 | 7,307.54 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 553 | 169.38 | 51.51 | 475.95 | 2,176.12 | 2,838.76 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,726 | 127.53 | 91.75 | 339.82 | 890.31 | 8,624.72 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 72 | 23.47 | 0.98 | 21.95 | 1,206.17 | 1,203.47 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 7 | 144.38 | 27.96 | 420.55 | 442.02 | 439.07 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 75.17 | 9.79 | 154.35 | 156.14 | 141.41 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 7 | 2,362.4 | 2,297.94 | 3,787.87 | 3,907.22 | 3,681.76 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 60.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 9.92 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 106 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.47 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.69 |
| `process_rss_avg` | job=backend-node | 12,201,472 |
| `process_rss_avg` | job=monitoring-node | 17,727,488 |
| `process_rss_avg` | job=mysql-exporter | 16,388,096 |
| `process_rss_avg` | job=mysql-node | 22,475,264 |
| `process_rss_avg` | job=prometheus | 121,697,280 |
| `process_rss_avg` | job=redis-exporter | 17,828,864 |
| `process_rss_avg` | job=redis-node | 22,425,600 |
| `process_rss_max` | job=backend-node | 14,725,120 |
| `process_rss_max` | job=monitoring-node | 17,895,424 |
| `process_rss_max` | job=mysql-exporter | 16,928,768 |
| `process_rss_max` | job=mysql-node | 22,720,512 |
| `process_rss_max` | job=prometheus | 127,778,816 |
| `process_rss_max` | job=redis-exporter | 18,128,896 |
| `process_rss_max` | job=redis-node | 22,425,600 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 32.38 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 89.09 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.18 |
| `node_cpu_pct_avg` | job=mysql-node | 25.88 |
| `node_cpu_pct_avg` | job=redis-node | 0.44 |
| `node_load1_avg` | job=backend-node | 8.45 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.58 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 136,603.43 |
| `node_major_fault_delta` | job=monitoring-node | 14.86 |
| `node_major_fault_delta` | job=mysql-node | 60.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 53,325,312 |
| `node_mem_available_avg` | job=monitoring-node | 271,040,000 |
| `node_mem_available_avg` | job=mysql-node | 256,690,688 |
| `node_mem_available_avg` | job=redis-node | 576,729,088 |
| `node_swap_free_avg` | job=backend-node | 2,240,415,744 |
| `node_swap_free_avg` | job=monitoring-node | 3,148,382,208 |
| `node_swap_free_avg` | job=mysql-node | 2,723,063,808 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 246,104 |
| `node_swap_in_delta` | job=monitoring-node | 5.71 |
| `node_swap_in_delta` | job=mysql-node | 12.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 119,436.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 195,237.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,523.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 185.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,240.11 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T05:43:24.328Z ~ 2026-08-11T05:45:24.328Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,088 | 87.89 | 42.36 | 268.15 | 465.73 | 10,436.56 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,106 | 42.05 | 19.02 | 165.94 | 252.14 | 3,887.33 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,306 | 57.18 | 28.08 | 196.55 | 314.9 | 7,307.54 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,171 | 91.01 | 52.94 | 292.11 | 408.25 | 2,838.76 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,930 | 63.79 | 29.45 | 224.65 | 324.21 | 7,318.58 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 105 | 3.01 | 1 | 14.54 | 22.82 | 1,203.47 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 9 | 36.5 | 11.18 | 147.64 | 154.8 | 439.07 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 76.66 | 25.17 | 197.97 | 200.66 | 193.43 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 3,681.76 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 53.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.73 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.68 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.81 |
| `process_rss_avg` | job=backend-node | 11,673,600 |
| `process_rss_avg` | job=monitoring-node | 17,833,984 |
| `process_rss_avg` | job=mysql-exporter | 16,406,016 |
| `process_rss_avg` | job=mysql-node | 22,412,288 |
| `process_rss_avg` | job=prometheus | 124,954,624 |
| `process_rss_avg` | job=redis-exporter | 17,448,960 |
| `process_rss_avg` | job=redis-node | 22,415,872 |
| `process_rss_max` | job=backend-node | 14,438,400 |
| `process_rss_max` | job=monitoring-node | 17,866,752 |
| `process_rss_max` | job=mysql-exporter | 16,896,000 |
| `process_rss_max` | job=mysql-node | 22,491,136 |
| `process_rss_max` | job=prometheus | 130,605,056 |
| `process_rss_max` | job=redis-exporter | 17,809,408 |
| `process_rss_max` | job=redis-node | 22,556,672 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 16.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 93.1 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.98 |
| `node_cpu_pct_avg` | job=mysql-node | 41.33 |
| `node_cpu_pct_avg` | job=redis-node | 0.55 |
| `node_load1_avg` | job=backend-node | 16.03 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.76 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 91,405.71 |
| `node_major_fault_delta` | job=monitoring-node | 11.43 |
| `node_major_fault_delta` | job=mysql-node | 76.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 48,372,224 |
| `node_mem_available_avg` | job=monitoring-node | 251,628,032 |
| `node_mem_available_avg` | job=mysql-node | 257,531,904 |
| `node_mem_available_avg` | job=redis-node | 576,729,088 |
| `node_swap_free_avg` | job=backend-node | 2,187,756,544 |
| `node_swap_free_avg` | job=monitoring-node | 3,148,382,208 |
| `node_swap_free_avg` | job=mysql-node | 2,723,074,048 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 153,740.57 |
| `node_swap_in_delta` | job=monitoring-node | 4.57 |
| `node_swap_in_delta` | job=mysql-node | 24 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 61,097.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 307,245.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 4,963.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 164.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,387.47 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T05:45:24.328Z ~ 2026-08-11T05:47:24.328Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,310 | 104.63 | 97.67 | 199.03 | 276.98 | 10,436.56 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,324 | 53.23 | 46.46 | 116.95 | 192.9 | 3,887.33 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,963 | 72.82 | 66.36 | 144.43 | 218.39 | 1,558.37 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,032 | 148.22 | 140.86 | 259.17 | 342.14 | 1,576.09 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,311 | 72.09 | 63.2 | 159.08 | 249.31 | 1,292.11 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 72 | 3.6 | 1.4 | 11.18 | 20.69 | 46.37 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 7 | 48.67 | 33.55 | 86.12 | 88.81 | 439.07 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 7 | 80.29 | 44.74 | 239.35 | 244.72 | 230.75 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 1,889.78 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 8 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.6 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 35.03 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.53 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105.2 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 109 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.77 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.83 |
| `process_rss_avg` | job=backend-node | 13,402,624 |
| `process_rss_avg` | job=monitoring-node | 17,866,752 |
| `process_rss_avg` | job=mysql-exporter | 16,665,600 |
| `process_rss_avg` | job=mysql-node | 22,563,328 |
| `process_rss_avg` | job=prometheus | 127,952,896 |
| `process_rss_avg` | job=redis-exporter | 17,629,696 |
| `process_rss_avg` | job=redis-node | 22,368,256 |
| `process_rss_max` | job=backend-node | 14,712,832 |
| `process_rss_max` | job=monitoring-node | 17,866,752 |
| `process_rss_max` | job=mysql-exporter | 17,215,488 |
| `process_rss_max` | job=mysql-node | 22,867,968 |
| `process_rss_max` | job=prometheus | 137,822,208 |
| `process_rss_max` | job=redis-exporter | 18,071,552 |
| `process_rss_max` | job=redis-node | 22,605,824 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 26.8 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 99.14 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.82 |
| `node_cpu_pct_avg` | job=mysql-node | 41.95 |
| `node_cpu_pct_avg` | job=redis-node | 0.76 |
| `node_load1_avg` | job=backend-node | 23.69 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.13 |
| `node_load1_avg` | job=redis-node | 0.04 |
| `node_major_fault_delta` | job=backend-node | 61,772.57 |
| `node_major_fault_delta` | job=monitoring-node | 24 |
| `node_major_fault_delta` | job=mysql-node | 56 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 50,525,696 |
| `node_mem_available_avg` | job=monitoring-node | 243,429,376 |
| `node_mem_available_avg` | job=mysql-node | 259,307,008 |
| `node_mem_available_avg` | job=redis-node | 560,742,400 |
| `node_swap_free_avg` | job=backend-node | 2,171,929,600 |
| `node_swap_free_avg` | job=monitoring-node | 3,148,382,208 |
| `node_swap_free_avg` | job=mysql-node | 2,723,074,048 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 105,502.86 |
| `node_swap_in_delta` | job=monitoring-node | 8 |
| `node_swap_in_delta` | job=mysql-node | 21.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 72,526.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 277,189.55 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,435.8 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 224.01 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,458.29 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T05:47:24.328Z ~ 2026-08-11T05:49:24.328Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,897 | 378.17 | 200.17 | 852.28 | 4,509.72 | 25,130.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,035 | 181.93 | 105.98 | 480.14 | 1,410.34 | 24,919.86 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,110 | 236.44 | 138.45 | 569.42 | 1,700.6 | 25,145.48 |
| method=GET, status=404, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 1 | 884.8 | 850.05 | 890.31 | 893.89 | 884.8 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 2,921.24 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 6,238.21 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 810 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 290 | 374.6 | 259.68 | 1,091.64 | 2,945.63 | 25,301.53 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 3,445 | 216.7 | 137.72 | 410.89 | 1,322.24 | 24,652.49 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 15 | 74.29 | 53.13 | 194.06 | 199.87 | 360.97 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 9 | 113.18 | 89.48 | 192.38 | 199.54 | 184.02 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 174.3 | 167.77 | 177.84 | 178.73 | 721.26 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 2 | 3,007.3 | 2,863.31 | 3,543.35 | 3,571.98 | 3,413.64 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 16.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 115.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 12.33 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 108.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.51 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.83 |
| `process_rss_avg` | job=backend-node | 13,513,216 |
| `process_rss_avg` | job=monitoring-node | 17,865,728 |
| `process_rss_avg` | job=mysql-exporter | 16,486,912 |
| `process_rss_avg` | job=mysql-node | 22,382,592 |
| `process_rss_avg` | job=prometheus | 128,489,984 |
| `process_rss_avg` | job=redis-exporter | 17,739,776 |
| `process_rss_avg` | job=redis-node | 22,261,760 |
| `process_rss_max` | job=backend-node | 14,843,904 |
| `process_rss_max` | job=monitoring-node | 17,866,752 |
| `process_rss_max` | job=mysql-exporter | 16,920,576 |
| `process_rss_max` | job=mysql-node | 22,593,536 |
| `process_rss_max` | job=prometheus | 137,822,208 |
| `process_rss_max` | job=redis-exporter | 17,985,536 |
| `process_rss_max` | job=redis-node | 22,261,760 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.38 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 4 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.76 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.7 |
| `node_cpu_pct_avg` | job=mysql-node | 31.6 |
| `node_cpu_pct_avg` | job=redis-node | 0.4 |
| `node_load1_avg` | job=backend-node | 30.4 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.67 |
| `node_load1_avg` | job=redis-node | 0.03 |
| `node_major_fault_delta` | job=backend-node | 150,480 |
| `node_major_fault_delta` | job=monitoring-node | 17.14 |
| `node_major_fault_delta` | job=mysql-node | 184 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 65,858,048 |
| `node_mem_available_avg` | job=monitoring-node | 249,314,304 |
| `node_mem_available_avg` | job=mysql-node | 253,318,656 |
| `node_mem_available_avg` | job=redis-node | 565,850,112 |
| `node_swap_free_avg` | job=backend-node | 2,132,128,768 |
| `node_swap_free_avg` | job=monitoring-node | 3,148,382,208 |
| `node_swap_free_avg` | job=mysql-node | 2,723,074,048 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 256,662.86 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 18.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 122,082.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 286,752 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 11,507.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 378.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 624 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-post-parallelgc-pure-throughput-sse250-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T06:00:02.696Z ~ 2026-08-11T06:07:44.810Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[200,300,400],"stageDuration":"2m"}`

### QPS 200 — 2026-08-11T06:00:37.696Z ~ 2026-08-11T06:02:37.696Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,544 | 473.16 | 452.38 | 757.49 | 980.57 | 1,684.48 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,348 | 194.44 | 153.89 | 419.29 | 501.17 | 681.63 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 7,297 | 275.88 | 250.18 | 507.63 | 643.57 | 1,344.35 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 36,750.52 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 139.56 |
| method=GET, status=200, uri=/api/wallet | 5 | 74.86 | 67.11 | 129.74 | 133.32 | 118.58 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 6.21 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 188.71 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 3.3 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 1.26 |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 4.05 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 189.74 |
| method=OPTIONS, status=200, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 130.2 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 3.34 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 2.85 |
| method=OPTIONS, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 1.33 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 693.8 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 24.98 |
| method=POST, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 481.95 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,620 | 483.32 | 465.83 | 783.64 | 979.78 | 1,272.91 |
| method=POST, status=201, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 1,790.97 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 600 | 286.4 | 282.38 | 492 | 750.27 | 1,092.69 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 954.36 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 1,231.05 |
| method=POST, status=401, uri=UNKNOWN | 94 | 21.44 | 19.17 | 47.26 | 115.87 | 1,195.27 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 6 | 261.82 | 234.88 | 603.98 | 621.88 | 573.18 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 11 | 190.69 | 153.03 | 344.88 | 355.31 | 327 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11.38 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS MarkSweep, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 72 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS MarkSweep, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 1.31 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.81 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.91 |
| `process_rss_avg` | job=backend-node | 13,201,408 |
| `process_rss_avg` | job=monitoring-node | 17,952,256 |
| `process_rss_avg` | job=mysql-exporter | 16,504,832 |
| `process_rss_avg` | job=mysql-node | 22,496,768 |
| `process_rss_avg` | job=prometheus | 127,524,864 |
| `process_rss_avg` | job=redis-exporter | 18,166,784 |
| `process_rss_avg` | job=redis-node | 22,443,520 |
| `process_rss_max` | job=backend-node | 13,615,104 |
| `process_rss_max` | job=monitoring-node | 18,124,800 |
| `process_rss_max` | job=mysql-exporter | 17,014,784 |
| `process_rss_max` | job=mysql-node | 22,589,440 |
| `process_rss_max` | job=prometheus | 134,438,912 |
| `process_rss_max` | job=redis-exporter | 18,235,392 |
| `process_rss_max` | job=redis-node | 22,462,464 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 39.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 99.9 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.68 |
| `node_cpu_pct_avg` | job=mysql-node | 36.49 |
| `node_cpu_pct_avg` | job=redis-node | 0.6 |
| `node_load1_avg` | job=backend-node | 20.61 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 0.47 |
| `node_load1_avg` | job=redis-node | 0.01 |
| `node_major_fault_delta` | job=backend-node | 21,982.86 |
| `node_major_fault_delta` | job=monitoring-node | 25.14 |
| `node_major_fault_delta` | job=mysql-node | 88 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,705,344 |
| `node_mem_available_avg` | job=monitoring-node | 299,501,056 |
| `node_mem_available_avg` | job=mysql-node | 252,444,672 |
| `node_mem_available_avg` | job=redis-node | 578,489,856 |
| `node_swap_free_avg` | job=backend-node | 2,554,916,352 |
| `node_swap_free_avg` | job=monitoring-node | 3,147,948,032 |
| `node_swap_free_avg` | job=mysql-node | 2,720,862,720 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 48,353.14 |
| `node_swap_in_delta` | job=monitoring-node | 57.14 |
| `node_swap_in_delta` | job=mysql-node | 19.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 43,840 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 207,818.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 19,694.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 171.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 3,196.57 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T06:02:37.696Z ~ 2026-08-11T06:04:37.696Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,480 | 435.33 | 372.01 | 698.96 | 2,216.83 | 13,150.21 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,245 | 211.13 | 187.22 | 390.88 | 645.81 | 12,961.02 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,889 | 287.97 | 249.08 | 479.05 | 913.05 | 13,024.98 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 36,750.52 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 139.56 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 118.58 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 6.21 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 3.3 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 1.26 |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 130.2 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 3.34 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 2.85 |
| method=OPTIONS, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 1.33 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 693.8 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 24.98 |
| method=POST, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 481.95 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 698 | 554.12 | 455.74 | 710.69 | 1,769.88 | 13,295.85 |
| method=POST, status=201, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 1,790.97 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,107 | 235.51 | 216.67 | 421.15 | 593.24 | 12,975.01 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 21 | 51.81 | 30.76 | 366.86 | 431.29 | 434.33 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 14 | 189.27 | 201.33 | 393.71 | 436.66 | 573.18 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 206.71 | 190.14 | 337.64 | 353.86 | 353.38 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 4,146.93 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS MarkSweep, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 70.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS MarkSweep, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 4.49 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 106 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.75 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.89 |
| `process_rss_avg` | job=backend-node | 12,467,712 |
| `process_rss_avg` | job=monitoring-node | 17,821,696 |
| `process_rss_avg` | job=mysql-exporter | 16,415,232 |
| `process_rss_avg` | job=mysql-node | 22,498,304 |
| `process_rss_avg` | job=prometheus | 128,837,632 |
| `process_rss_avg` | job=redis-exporter | 17,755,648 |
| `process_rss_avg` | job=redis-node | 22,310,912 |
| `process_rss_max` | job=backend-node | 12,713,984 |
| `process_rss_max` | job=monitoring-node | 17,936,384 |
| `process_rss_max` | job=mysql-exporter | 16,769,024 |
| `process_rss_max` | job=mysql-node | 22,925,312 |
| `process_rss_max` | job=prometheus | 129,257,472 |
| `process_rss_max` | job=redis-exporter | 18,362,368 |
| `process_rss_max` | job=redis-node | 22,310,912 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 99.72 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.5 |
| `node_cpu_pct_avg` | job=mysql-node | 34.05 |
| `node_cpu_pct_avg` | job=redis-node | 0.47 |
| `node_load1_avg` | job=backend-node | 37.12 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 0.74 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 66,837.71 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 88 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 42,237,952 |
| `node_mem_available_avg` | job=monitoring-node | 255,990,784 |
| `node_mem_available_avg` | job=mysql-node | 253,718,528 |
| `node_mem_available_avg` | job=redis-node | 578,342,912 |
| `node_swap_free_avg` | job=backend-node | 2,408,924,160 |
| `node_swap_free_avg` | job=monitoring-node | 3,147,948,032 |
| `node_swap_free_avg` | job=mysql-node | 2,720,866,304 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 113,130.29 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 16 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 87,595.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 237,384 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 9,988.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 96 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,597.71 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T06:04:37.696Z ~ 2026-08-11T06:06:37.696Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,136 | 558.09 | 350.22 | 798.6 | 2,423.08 | 36,568.44 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,184 | 274.36 | 175.05 | 346.08 | 974.42 | 36,097.85 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,377 | 399.99 | 239.32 | 447.19 | 1,295.83 | 40,997.91 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 3 | 526.55 | 679.37 | 2,555.47 | 2,801.74 | 2,667.8 |
| method=GET, status=500, uri=/api/auctions | N/A | N/A | N/A | N/A | N/A | 47,058.23 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | N/A | N/A | N/A | N/A | N/A | 45,996.15 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | N/A | N/A | N/A | N/A | N/A | 46,023.71 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/wallet/charges | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 458 | 594.22 | 442.8 | 758.33 | 1,788.67 | 36,137.81 |
| method=POST, status=201, uri=/api/auth/signup | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,585 | 290.15 | 209.92 | 422.13 | 1,153.76 | 35,844.86 |
| method=POST, status=401, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 8 | 41.27 | 11.88 | 126.39 | 132.65 | 434.33 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 9 | 192.33 | 201.33 | 263.96 | 267.54 | 505.4 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 151.69 | 145.4 | 238.24 | 244.5 | 353.38 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 1 | 1,699.42 | 1,621.66 | 3,987.13 | 4,233.4 | 47,045.89 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.17 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.83 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12.17 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15.07 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS MarkSweep, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=Ergonomics, exported_application=dbidding, gc=PS MarkSweep, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 41.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS MarkSweep, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=Ergonomics, exported_application=dbidding, gc=PS MarkSweep, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 3.45 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=PS Scavenge, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.66 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.87 |
| `process_rss_avg` | job=backend-node | 12,001,280 |
| `process_rss_avg` | job=monitoring-node | 17,352,192 |
| `process_rss_avg` | job=mysql-exporter | 16,600,576 |
| `process_rss_avg` | job=mysql-node | 22,473,216 |
| `process_rss_avg` | job=prometheus | 132,446,720 |
| `process_rss_avg` | job=redis-exporter | 18,178,048 |
| `process_rss_avg` | job=redis-node | 22,310,912 |
| `process_rss_max` | job=backend-node | 12,562,432 |
| `process_rss_max` | job=monitoring-node | 17,936,384 |
| `process_rss_max` | job=mysql-exporter | 17,092,608 |
| `process_rss_max` | job=mysql-node | 22,634,496 |
| `process_rss_max` | job=prometheus | 136,728,576 |
| `process_rss_max` | job=redis-exporter | 18,472,960 |
| `process_rss_max` | job=redis-node | 22,310,912 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 251 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.33 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 93.38 |
| `node_cpu_pct_avg` | job=monitoring-node | 4.29 |
| `node_cpu_pct_avg` | job=mysql-node | 25.38 |
| `node_cpu_pct_avg` | job=redis-node | 0.43 |
| `node_load1_avg` | job=backend-node | 37 |
| `node_load1_avg` | job=monitoring-node | 0.07 |
| `node_load1_avg` | job=mysql-node | 0.78 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 126,109.71 |
| `node_major_fault_delta` | job=monitoring-node | 1,635.43 |
| `node_major_fault_delta` | job=mysql-node | 156.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 47,294,976 |
| `node_mem_available_avg` | job=monitoring-node | 261,257,216 |
| `node_mem_available_avg` | job=mysql-node | 253,060,608 |
| `node_mem_available_avg` | job=redis-node | 578,345,984 |
| `node_swap_free_avg` | job=backend-node | 2,324,374,016 |
| `node_swap_free_avg` | job=monitoring-node | 3,146,125,824 |
| `node_swap_free_avg` | job=mysql-node | 2,719,986,176 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 198,502.86 |
| `node_swap_in_delta` | job=monitoring-node | 458.29 |
| `node_swap_in_delta` | job=mysql-node | 5.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 133,114.29 |
| `node_swap_out_delta` | job=monitoring-node | 523.43 |
| `node_swap_out_delta` | job=mysql-node | 694.86 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 151,324.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 8,738.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 78.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 993.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-post-g1-pure-throughput-sse500-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T06:13:37.016Z ~ 2026-08-11T06:27:22.885Z
- 설정: `{"sseVUs":500,"totalSseConnections":1000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T06:14:12.016Z ~ 2026-08-11T06:16:12.016Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,158 | 564.76 | 346.99 | 1,588.49 | 6,207.36 | 7,509.29 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,054 | 191.15 | 133.72 | 510.19 | 972.96 | 3,114.14 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,058 | 252.34 | 153.68 | 634.86 | 1,893.73 | 16,036.79 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 338.73 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,521.01 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 327.02 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 898 | 395.51 | 297.02 | 1,724.66 | 4,603.49 | 8,036.58 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 191 | 299.22 | 276.33 | 635.3 | 872.42 | 3,707.42 |
| method=POST, status=401, uri=UNKNOWN | 82 | 15.14 | 6.99 | 70.09 | 103.32 | 750.23 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 602.82 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 172.3 | 246.07 | 296.84 | 299.37 | 298.65 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.63 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 51.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 11.95 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 172 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.55 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.85 |
| `process_rss_avg` | job=backend-node | 11,874,816 |
| `process_rss_avg` | job=monitoring-node | 18,940,928 |
| `process_rss_avg` | job=mysql-exporter | 16,530,432 |
| `process_rss_avg` | job=mysql-node | 22,434,816 |
| `process_rss_avg` | job=prometheus | 124,047,360 |
| `process_rss_avg` | job=redis-exporter | 18,010,112 |
| `process_rss_avg` | job=redis-node | 22,384,640 |
| `process_rss_max` | job=backend-node | 12,734,464 |
| `process_rss_max` | job=monitoring-node | 19,075,072 |
| `process_rss_max` | job=mysql-exporter | 17,014,784 |
| `process_rss_max` | job=mysql-node | 22,552,576 |
| `process_rss_max` | job=prometheus | 124,047,360 |
| `process_rss_max` | job=redis-exporter | 18,452,480 |
| `process_rss_max` | job=redis-node | 22,536,192 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 22.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 46.25 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 94.86 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 23.23 |
| `node_cpu_pct_avg` | job=redis-node | 0.52 |
| `node_load1_avg` | job=backend-node | 11.03 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 0.22 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 157,365.71 |
| `node_major_fault_delta` | job=monitoring-node | 68.57 |
| `node_major_fault_delta` | job=mysql-node | 77.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 48,870,912 |
| `node_mem_available_avg` | job=monitoring-node | 332,195,840 |
| `node_mem_available_avg` | job=mysql-node | 254,267,904 |
| `node_mem_available_avg` | job=redis-node | 577,801,216 |
| `node_swap_free_avg` | job=backend-node | 2,376,905,728 |
| `node_swap_free_avg` | job=monitoring-node | 3,106,263,040 |
| `node_swap_free_avg` | job=mysql-node | 2,718,519,296 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 256,798.86 |
| `node_swap_in_delta` | job=monitoring-node | 33.14 |
| `node_swap_in_delta` | job=mysql-node | 65.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 173,157.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 105,126.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 12,193.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 68.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,125.71 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T06:16:12.016Z ~ 2026-08-11T06:18:12.016Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,592 | 398.06 | 205.29 | 980.11 | 5,504.72 | 7,720.9 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,474 | 117.6 | 66.97 | 318.5 | 475.01 | 4,558.07 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,623 | 204.49 | 110.72 | 477.35 | 1,423.99 | 16,036.79 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 338.73 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,521.01 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 177.87 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,152 | 263.96 | 174.32 | 597.15 | 1,035.4 | 11,230.18 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 383 | 242.68 | 230.53 | 444.81 | 684.51 | 3,707.42 |
| method=POST, status=401, uri=UNKNOWN | 79 | 11.52 | 7.41 | 32.72 | 103.67 | 110.52 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 8 | 117.39 | 79.6 | 263.1 | 572.01 | 602.82 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 340.88 | 328.96 | 355.02 | 357.33 | 340.88 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 16 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.29 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 57.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 103.14 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.67 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.85 |
| `process_rss_avg` | job=backend-node | 11,767,296 |
| `process_rss_avg` | job=monitoring-node | 19,041,280 |
| `process_rss_avg` | job=mysql-exporter | 16,459,776 |
| `process_rss_avg` | job=mysql-node | 22,582,784 |
| `process_rss_avg` | job=prometheus | 120,526,336 |
| `process_rss_avg` | job=redis-exporter | 18,051,072 |
| `process_rss_avg` | job=redis-node | 22,194,176 |
| `process_rss_max` | job=backend-node | 14,532,608 |
| `process_rss_max` | job=monitoring-node | 19,075,072 |
| `process_rss_max` | job=mysql-exporter | 16,973,824 |
| `process_rss_max` | job=mysql-node | 22,937,600 |
| `process_rss_max` | job=prometheus | 125,358,080 |
| `process_rss_max` | job=redis-exporter | 18,137,088 |
| `process_rss_max` | job=redis-node | 22,233,088 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 18.43 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.14 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 99.46 |
| `node_cpu_pct_avg` | job=monitoring-node | 3.42 |
| `node_cpu_pct_avg` | job=mysql-node | 28.28 |
| `node_cpu_pct_avg` | job=redis-node | 0.56 |
| `node_load1_avg` | job=backend-node | 19.3 |
| `node_load1_avg` | job=monitoring-node | 0.07 |
| `node_load1_avg` | job=mysql-node | 0.6 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 122,067.43 |
| `node_major_fault_delta` | job=monitoring-node | 4,457.14 |
| `node_major_fault_delta` | job=mysql-node | 92.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,250,688 |
| `node_mem_available_avg` | job=monitoring-node | 284,532,736 |
| `node_mem_available_avg` | job=mysql-node | 253,971,456 |
| `node_mem_available_avg` | job=redis-node | 577,138,176 |
| `node_swap_free_avg` | job=backend-node | 2,267,718,144 |
| `node_swap_free_avg` | job=monitoring-node | 3,077,201,408 |
| `node_swap_free_avg` | job=mysql-node | 2,718,532,096 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 203,282.29 |
| `node_swap_in_delta` | job=monitoring-node | 2,617.14 |
| `node_swap_in_delta` | job=mysql-node | 59.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 108,588.57 |
| `node_swap_out_delta` | job=monitoring-node | 2,416 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 139,829.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 9,557.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 78.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,613.71 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T06:18:12.016Z ~ 2026-08-11T06:20:12.016Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,966 | 287.84 | 257.12 | 489.52 | 1,124.68 | 9,531.22 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,021 | 159.57 | 124.24 | 323.03 | 471.94 | 8,103.79 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,943 | 201.93 | 170.77 | 379.26 | 569.98 | 11,197.4 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,808 | 350.85 | 310.73 | 567.32 | 1,302.81 | 11,230.18 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,178 | 215.81 | 197.97 | 385.72 | 488.65 | 8,008.52 |
| method=POST, status=401, uri=UNKNOWN | 74 | 12.05 | 7.69 | 44.74 | 49.21 | 1,630.63 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 15 | 149.97 | 111.85 | 323.17 | 350.96 | 311.3 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 4 | 123.06 | 83.89 | 197.97 | 200.66 | 340.88 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.86 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.14 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15.29 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 68.77 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.84 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.69 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.87 |
| `process_rss_avg` | job=backend-node | 12,185,088 |
| `process_rss_avg` | job=monitoring-node | 18,989,056 |
| `process_rss_avg` | job=mysql-exporter | 16,589,824 |
| `process_rss_avg` | job=mysql-node | 22,511,616 |
| `process_rss_avg` | job=prometheus | 119,549,952 |
| `process_rss_avg` | job=redis-exporter | 17,989,632 |
| `process_rss_avg` | job=redis-node | 22,216,704 |
| `process_rss_max` | job=backend-node | 14,839,808 |
| `process_rss_max` | job=monitoring-node | 19,070,976 |
| `process_rss_max` | job=mysql-exporter | 16,896,000 |
| `process_rss_max` | job=mysql-node | 22,765,568 |
| `process_rss_max` | job=prometheus | 122,122,240 |
| `process_rss_max` | job=redis-exporter | 17,989,632 |
| `process_rss_max` | job=redis-node | 22,216,704 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 45 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 99.03 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.33 |
| `node_cpu_pct_avg` | job=mysql-node | 44.98 |
| `node_cpu_pct_avg` | job=redis-node | 0.62 |
| `node_load1_avg` | job=backend-node | 27.85 |
| `node_load1_avg` | job=monitoring-node | 0.05 |
| `node_load1_avg` | job=mysql-node | 0.67 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 128,141.71 |
| `node_major_fault_delta` | job=monitoring-node | 406.86 |
| `node_major_fault_delta` | job=mysql-node | 101.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 47,613,952 |
| `node_mem_available_avg` | job=monitoring-node | 290,721,792 |
| `node_mem_available_avg` | job=mysql-node | 256,057,344 |
| `node_mem_available_avg` | job=redis-node | 576,856,064 |
| `node_swap_free_avg` | job=backend-node | 2,238,053,888 |
| `node_swap_free_avg` | job=monitoring-node | 3,064,133,632 |
| `node_swap_free_avg` | job=mysql-node | 2,718,542,848 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 209,440 |
| `node_swap_in_delta` | job=monitoring-node | 348.57 |
| `node_swap_in_delta` | job=mysql-node | 53.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 79,364.57 |
| `node_swap_out_delta` | job=monitoring-node | 185.14 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 238,139.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 14,011.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 149.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 3,988.57 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T06:20:12.016Z ~ 2026-08-11T06:22:12.016Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,781 | 470.62 | 351.64 | 772.92 | 1,730.51 | 16,168.29 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,619 | 263.9 | 185.23 | 508.74 | 943.1 | 14,272.07 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 7,999 | 345.02 | 240.89 | 600.68 | 1,326.49 | 20,561.88 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | N/A | N/A | N/A | N/A | N/A | 480,515.1 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | N/A | N/A | N/A | N/A | N/A | 2,241.9 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | N/A | N/A | N/A | N/A | N/A | 51.62 |
| method=OPTIONS, status=200, uri=/api/notifications | N/A | N/A | N/A | N/A | N/A | 155.74 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | N/A | N/A | N/A | N/A | N/A | 17.48 |
| method=OPTIONS, status=200, uri=/api/statistic/market | N/A | N/A | N/A | N/A | N/A | 9.34 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | N/A | N/A | N/A | N/A | N/A | 7,476.43 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 779 | 629.58 | 481.79 | 918.05 | 1,827.45 | 14,743.88 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,465 | 339.13 | 223.94 | 611.63 | 1,913.72 | 14,372.22 |
| method=POST, status=401, uri=UNKNOWN | 25 | 45.21 | 16.78 | 88.36 | 488.43 | 1,630.63 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 6 | 234.48 | 240.47 | 343.44 | 355.02 | 336.35 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 102.86 | 105.92 | 130.86 | 133.55 | 197.45 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 11,750.82 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.14 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.71 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15.14 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 88 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 8.98 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 109.71 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 119 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.69 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.8 |
| `process_rss_avg` | job=backend-node | 12,806,144 |
| `process_rss_avg` | job=monitoring-node | 19,070,976 |
| `process_rss_avg` | job=mysql-exporter | 16,504,832 |
| `process_rss_avg` | job=mysql-node | 22,642,688 |
| `process_rss_avg` | job=prometheus | 120,707,072 |
| `process_rss_avg` | job=redis-exporter | 18,219,008 |
| `process_rss_avg` | job=redis-node | 22,289,408 |
| `process_rss_max` | job=backend-node | 14,524,416 |
| `process_rss_max` | job=monitoring-node | 19,070,976 |
| `process_rss_max` | job=mysql-exporter | 16,928,768 |
| `process_rss_max` | job=mysql-node | 23,003,136 |
| `process_rss_max` | job=prometheus | 124,088,320 |
| `process_rss_max` | job=redis-exporter | 18,251,776 |
| `process_rss_max` | job=redis-node | 22,478,848 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 489.86 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 194.46 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.14 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.98 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.2 |
| `node_cpu_pct_avg` | job=mysql-node | 32.39 |
| `node_cpu_pct_avg` | job=redis-node | 0.49 |
| `node_load1_avg` | job=backend-node | 41.8 |
| `node_load1_avg` | job=monitoring-node | 0.07 |
| `node_load1_avg` | job=mysql-node | 1.51 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 128,229.71 |
| `node_major_fault_delta` | job=monitoring-node | 361.14 |
| `node_major_fault_delta` | job=mysql-node | 190.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 74,493,440 |
| `node_mem_available_avg` | job=monitoring-node | 304,457,728 |
| `node_mem_available_avg` | job=mysql-node | 255,518,720 |
| `node_mem_available_avg` | job=redis-node | 576,732,672 |
| `node_swap_free_avg` | job=backend-node | 2,211,661,312 |
| `node_swap_free_avg` | job=monitoring-node | 3,082,263,040 |
| `node_swap_free_avg` | job=mysql-node | 2,715,002,880 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 209,584 |
| `node_swap_in_delta` | job=monitoring-node | 92.57 |
| `node_swap_in_delta` | job=mysql-node | 13.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 111,985.14 |
| `node_swap_out_delta` | job=monitoring-node | 104 |
| `node_swap_out_delta` | job=mysql-node | 1,324.57 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 190,194.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 11,440 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 75.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,456 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T06:22:12.016Z ~ 2026-08-11T06:24:12.016Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,967 | 447.75 | 361.23 | 693.4 | 1,064.89 | 16,168.29 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,899 | 217.44 | 194.47 | 434.12 | 671.86 | 14,272.07 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,929 | 310.54 | 251.62 | 525.15 | 835.83 | 20,561.88 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 4,242.73 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 11,013.23 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 4,967.38 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 16,346.16 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 1 | 595,794.21 | 30,000 | 30,000 | 30,000 | 595,794.21 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 30.3 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 2,241.9 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 23.77 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 51.62 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 155.74 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 17.48 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 9.34 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 108.2 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 7,476.43 |
| method=POST, status=200, uri=/api/sse/tickets | 1 | 46.18 | 47.54 | 50.05 | 50.28 | 46.18 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 688 | 631.09 | 483.09 | 871.67 | 2,501.82 | 14,743.88 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,016 | 284.37 | 229.64 | 536.52 | 976.08 | 14,372.22 |
| method=POST, status=401, uri=UNKNOWN | 10 | 48.26 | 30.76 | 168.89 | 176.94 | 479.91 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 5 | 405.18 | 100 | 966.37 | 980.68 | 983.93 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 173.02 | 134.22 | 263.96 | 267.54 | 253.05 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 11,750.82 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13.25 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 148.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.06 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 113.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 123 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.7 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.81 |
| `process_rss_avg` | job=backend-node | 12,274,176 |
| `process_rss_avg` | job=monitoring-node | 18,969,088 |
| `process_rss_avg` | job=mysql-exporter | 16,470,016 |
| `process_rss_avg` | job=mysql-node | 22,496,768 |
| `process_rss_avg` | job=prometheus | 129,904,640 |
| `process_rss_avg` | job=redis-exporter | 18,333,696 |
| `process_rss_avg` | job=redis-node | 22,470,656 |
| `process_rss_max` | job=backend-node | 12,853,248 |
| `process_rss_max` | job=monitoring-node | 19,070,976 |
| `process_rss_max` | job=mysql-exporter | 17,035,264 |
| `process_rss_max` | job=mysql-node | 22,724,608 |
| `process_rss_max` | job=prometheus | 136,151,040 |
| `process_rss_max` | job=redis-exporter | 18,382,848 |
| `process_rss_max` | job=redis-node | 22,716,416 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 461.63 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 462 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 130.29 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.95 |
| `node_cpu_pct_avg` | job=monitoring-node | 3.18 |
| `node_cpu_pct_avg` | job=mysql-node | 34.7 |
| `node_cpu_pct_avg` | job=redis-node | 0.48 |
| `node_load1_avg` | job=backend-node | 44.5 |
| `node_load1_avg` | job=monitoring-node | 0.08 |
| `node_load1_avg` | job=mysql-node | 1.46 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 152,114.29 |
| `node_major_fault_delta` | job=monitoring-node | 2,406.86 |
| `node_major_fault_delta` | job=mysql-node | 54.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 61,714,432 |
| `node_mem_available_avg` | job=monitoring-node | 269,792,768 |
| `node_mem_available_avg` | job=mysql-node | 252,632,064 |
| `node_mem_available_avg` | job=redis-node | 576,614,400 |
| `node_swap_free_avg` | job=backend-node | 2,171,527,680 |
| `node_swap_free_avg` | job=monitoring-node | 3,063,816,704 |
| `node_swap_free_avg` | job=mysql-node | 2,713,821,184 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 219,453.71 |
| `node_swap_in_delta` | job=monitoring-node | 1,036.57 |
| `node_swap_in_delta` | job=mysql-node | 37.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 98,733.71 |
| `node_swap_out_delta` | job=monitoring-node | 884.57 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 209,939.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 2 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 15,545.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 105.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,461.71 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T06:24:12.016Z ~ 2026-08-11T06:26:12.016Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,002 | 420.72 | 342.81 | 668 | 1,179.55 | 12,706.07 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,033 | 232.09 | 188.63 | 439.56 | 755.67 | 12,381.05 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,185 | 311.05 | 244.61 | 522.04 | 832.88 | 12,769.99 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 4,242.73 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 11,013.23 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 4,967.38 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 16,346.16 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 595,794.21 |
| method=GET, status=404, uri=/** | 1 | 393.48 | 402.65 | 442.92 | 446.5 | 393.48 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 30.3 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 2,241.9 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 23.77 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 51.62 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 155.74 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 17.48 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 9.34 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 108.2 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 7,476.43 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 46.18 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 702 | 568.64 | 478.61 | 889.09 | 1,304 | 13,229.51 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,101 | 261.28 | 225.7 | 482.59 | 707.6 | 12,510.38 |
| method=POST, status=401, uri=UNKNOWN | 18 | 24.98 | 16.08 | 138.69 | 153.01 | 160.73 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 3 | 183.3 | 150.99 | 265.08 | 267.76 | 983.93 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 264.6 | 268.44 | 296.84 | 299.37 | 282.61 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 3 | 2,141.77 | 2,241.46 | 2,487.09 | 12,015.48 | 11,750.82 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 116.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.4 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 113 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 119 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.7 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.79 |
| `process_rss_avg` | job=backend-node | 12,169,216 |
| `process_rss_avg` | job=monitoring-node | 19,072,512 |
| `process_rss_avg` | job=mysql-exporter | 16,391,168 |
| `process_rss_avg` | job=mysql-node | 22,734,336 |
| `process_rss_avg` | job=prometheus | 130,073,600 |
| `process_rss_avg` | job=redis-exporter | 17,537,536 |
| `process_rss_avg` | job=redis-node | 22,315,008 |
| `process_rss_max` | job=backend-node | 13,225,984 |
| `process_rss_max` | job=monitoring-node | 19,197,952 |
| `process_rss_max` | job=mysql-exporter | 16,822,272 |
| `process_rss_max` | job=mysql-node | 23,011,328 |
| `process_rss_max` | job=prometheus | 130,396,160 |
| `process_rss_max` | job=redis-exporter | 17,809,408 |
| `process_rss_max` | job=redis-node | 22,315,008 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 462 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 462 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 166.86 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.92 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.7 |
| `node_cpu_pct_avg` | job=mysql-node | 35.89 |
| `node_cpu_pct_avg` | job=redis-node | 0.5 |
| `node_load1_avg` | job=backend-node | 42.3 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 1.1 |
| `node_load1_avg` | job=redis-node | 0.01 |
| `node_major_fault_delta` | job=backend-node | 147,829.71 |
| `node_major_fault_delta` | job=monitoring-node | 658.29 |
| `node_major_fault_delta` | job=mysql-node | 51.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 55,187,456 |
| `node_mem_available_avg` | job=monitoring-node | 272,975,360 |
| `node_mem_available_avg` | job=mysql-node | 255,078,400 |
| `node_mem_available_avg` | job=redis-node | 576,476,672 |
| `node_swap_free_avg` | job=backend-node | 2,155,161,088 |
| `node_swap_free_avg` | job=monitoring-node | 3,054,323,712 |
| `node_swap_free_avg` | job=mysql-node | 2,713,821,184 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 218,554.29 |
| `node_swap_in_delta` | job=monitoring-node | 347.43 |
| `node_swap_in_delta` | job=mysql-node | 13.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 98,797.71 |
| `node_swap_out_delta` | job=monitoring-node | 123.43 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 214,168 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 13,285.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 115.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,528 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-post-g1-pure-throughput-sse1000-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T06:27:48.642Z ~ 2026-08-11T06:34:46.997Z
- 설정: `{"sseVUs":1000,"totalSseConnections":2000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T06:28:23.642Z ~ 2026-08-11T06:30:23.642Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 353 | 819.83 | 622.91 | 2,568.72 | 3,267.75 | 4,565.46 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 135 | 344.84 | 69.91 | 1,386.92 | 3,391.23 | 3,246.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 825 | 511.06 | 382.17 | 1,416.37 | 3,385.51 | 3,629.08 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 836,894.4 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 862,254.73 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 848,644.1 |
| method=GET, status=401, uri=UNKNOWN | 167 | 3.67 | 0.55 | 12.76 | 349.99 | 359.72 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,584.14 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 577 | 20.63 | 0.68 | 52.01 | 404.44 | 3,230.3 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 32 | 3,148.64 | 3,012.44 | 3,825.21 | 3,914.68 | 3,907.79 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1 | 2,281.63 | 2,326.44 | 2,487.5 | 2,501.82 | 2,281.63 |
| method=POST, status=401, uri=UNKNOWN | 8 | 87.77 | 11.18 | 484.22 | 496.84 | 635.17 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 61.78 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.29 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 10.67 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.51 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 103.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.17 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.36 |
| `process_rss_avg` | job=backend-node | 12,630,016 |
| `process_rss_avg` | job=monitoring-node | 19,152,896 |
| `process_rss_avg` | job=mysql-exporter | 16,536,576 |
| `process_rss_avg` | job=mysql-node | 22,508,544 |
| `process_rss_avg` | job=prometheus | 130,274,816 |
| `process_rss_avg` | job=redis-exporter | 17,662,464 |
| `process_rss_avg` | job=redis-node | 22,302,720 |
| `process_rss_max` | job=backend-node | 12,980,224 |
| `process_rss_max` | job=monitoring-node | 19,185,664 |
| `process_rss_max` | job=mysql-exporter | 16,920,576 |
| `process_rss_max` | job=mysql-node | 22,601,728 |
| `process_rss_max` | job=prometheus | 136,687,616 |
| `process_rss_max` | job=redis-exporter | 18,120,704 |
| `process_rss_max` | job=redis-node | 22,302,720 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 939.75 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 566 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,001 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 566 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 18.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.75 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 4 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.43 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.53 |
| `node_cpu_pct_avg` | job=mysql-node | 4.34 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 9.38 |
| `node_load1_avg` | job=monitoring-node | 0.08 |
| `node_load1_avg` | job=mysql-node | 0.15 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 153,949.71 |
| `node_major_fault_delta` | job=monitoring-node | 476.57 |
| `node_major_fault_delta` | job=mysql-node | 8 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 74,746,368 |
| `node_mem_available_avg` | job=monitoring-node | 266,002,944 |
| `node_mem_available_avg` | job=mysql-node | 250,679,296 |
| `node_mem_available_avg` | job=redis-node | 576,398,336 |
| `node_swap_free_avg` | job=backend-node | 2,101,117,952 |
| `node_swap_free_avg` | job=monitoring-node | 3,059,891,200 |
| `node_swap_free_avg` | job=mysql-node | 2,713,821,184 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 259,019.43 |
| `node_swap_in_delta` | job=monitoring-node | 1,284.57 |
| `node_swap_in_delta` | job=mysql-node | 1.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 172,976 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 26,022.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,250.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 6.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 121.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T06:30:23.642Z ~ 2026-08-11T06:32:23.642Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 827 | 933.04 | 305 | 4,566.98 | 8,418.14 | 37,376.14 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 487 | 229.8 | 120.8 | 782.04 | 1,356.49 | 3,356.96 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 1,888 | 607.63 | 175.69 | 977.76 | 3,844 | 36,550.46 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 271 | 225,573.84 | 30,000 | 30,000 | 30,000 | 862,254.73 |
| method=GET, status=200, uri=/error | 2 | 231,242.28 | 30,000 | 30,000 | 30,000 | 235,746.56 |
| method=GET, status=401, uri=UNKNOWN | 202 | 53.85 | 0.86 | 18.92 | 1,720.67 | 1,817.18 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,584.14 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 311 | 42.23 | 0.8 | 350.96 | 903.14 | 3,230.3 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 137 | 558.48 | 464.93 | 1,252.7 | 1,753.78 | 3,907.79 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 208 | 625.81 | 238.61 | 967.86 | 6,571.3 | 36,478.64 |
| method=POST, status=401, uri=UNKNOWN | 9 | 116.43 | 22.37 | 769.51 | 798.15 | 738.29 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 1 | 141.1 | 145.4 | 155.47 | 156.36 | 141.1 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 37,997 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 59.42 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 43 | 3,256.57 | 2,572.51 | 10,093.17 | 11,181.23 | 10,120.6 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.67 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.17 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 88 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 34.24 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 8.55 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.36 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104.33 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.5 |
| `process_rss_avg` | job=backend-node | 12,487,168 |
| `process_rss_avg` | job=monitoring-node | 19,185,664 |
| `process_rss_avg` | job=mysql-exporter | 16,497,152 |
| `process_rss_avg` | job=mysql-node | 22,596,608 |
| `process_rss_avg` | job=prometheus | 130,155,520 |
| `process_rss_avg` | job=redis-exporter | 17,253,376 |
| `process_rss_avg` | job=redis-node | 22,302,720 |
| `process_rss_max` | job=backend-node | 12,783,616 |
| `process_rss_max` | job=monitoring-node | 19,185,664 |
| `process_rss_max` | job=mysql-exporter | 16,887,808 |
| `process_rss_max` | job=mysql-node | 22,769,664 |
| `process_rss_max` | job=prometheus | 130,969,600 |
| `process_rss_max` | job=redis-exporter | 17,686,528 |
| `process_rss_max` | job=redis-node | 22,302,720 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,001 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 676.33 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,001 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 802 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 16.83 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 95.82 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.61 |
| `node_cpu_pct_avg` | job=mysql-node | 9.8 |
| `node_cpu_pct_avg` | job=redis-node | 0.4 |
| `node_load1_avg` | job=backend-node | 11.61 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 0.28 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 172,014.86 |
| `node_major_fault_delta` | job=monitoring-node | 82.29 |
| `node_major_fault_delta` | job=mysql-node | 13.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 58,445,824 |
| `node_mem_available_avg` | job=monitoring-node | 259,238,912 |
| `node_mem_available_avg` | job=mysql-node | 253,498,368 |
| `node_mem_available_avg` | job=redis-node | 576,420,864 |
| `node_swap_free_avg` | job=backend-node | 2,066,355,712 |
| `node_swap_free_avg` | job=monitoring-node | 3,059,980,800 |
| `node_swap_free_avg` | job=mysql-node | 2,713,821,184 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 260,830.86 |
| `node_swap_in_delta` | job=monitoring-node | 50.29 |
| `node_swap_in_delta` | job=mysql-node | 9.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 160,981.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 55,454.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,924.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 30.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 413.71 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T06:32:23.642Z ~ 2026-08-11T06:34:23.642Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 683 | 271.84 | 248.16 | 591.06 | 847.36 | 37,376.14 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 614 | 174.03 | 120.8 | 452.07 | 697.34 | 3,356.96 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,042 | 202.38 | 154.3 | 548.22 | 767.35 | 36,550.46 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 259,241.07 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 258,935.55 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 1,817.18 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 190 | 8.49 | 0.73 | 52.85 | 149.65 | 3,230.3 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 284 | 956.88 | 555.71 | 3,125.78 | 3,438.84 | 3,907.79 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 206 | 287.61 | 248.3 | 767.73 | 1,038.84 | 36,478.64 |
| method=POST, status=401, uri=UNKNOWN | 10 | 79.13 | 0.88 | 264.52 | 267.65 | 738.29 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 1 | 66.52 | 64.31 | 66.83 | 67.05 | 141.1 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 37,997 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 59.42 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 10,120.6 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.33 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 69.56 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.66 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105.67 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 106 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.23 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.58 |
| `process_rss_avg` | job=backend-node | 12,563,456 |
| `process_rss_avg` | job=monitoring-node | 19,267,584 |
| `process_rss_avg` | job=mysql-exporter | 16,443,904 |
| `process_rss_avg` | job=mysql-node | 22,474,752 |
| `process_rss_avg` | job=prometheus | 130,993,152 |
| `process_rss_avg` | job=redis-exporter | 17,682,432 |
| `process_rss_avg` | job=redis-node | 22,302,720 |
| `process_rss_max` | job=backend-node | 12,943,360 |
| `process_rss_max` | job=monitoring-node | 19,316,736 |
| `process_rss_max` | job=mysql-exporter | 16,912,384 |
| `process_rss_max` | job=mysql-node | 22,716,416 |
| `process_rss_max` | job=prometheus | 133,304,320 |
| `process_rss_max` | job=redis-exporter | 17,879,040 |
| `process_rss_max` | job=redis-node | 22,302,720 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,001 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 516.33 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,001 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 525 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 17 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 96.55 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.18 |
| `node_cpu_pct_avg` | job=mysql-node | 13.57 |
| `node_cpu_pct_avg` | job=redis-node | 0.4 |
| `node_load1_avg` | job=backend-node | 14.46 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 0.35 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 170,313.14 |
| `node_major_fault_delta` | job=monitoring-node | 1,706.29 |
| `node_major_fault_delta` | job=mysql-node | 21.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 64,422,912 |
| `node_mem_available_avg` | job=monitoring-node | 263,817,728 |
| `node_mem_available_avg` | job=mysql-node | 251,921,408 |
| `node_mem_available_avg` | job=redis-node | 576,434,176 |
| `node_swap_free_avg` | job=backend-node | 2,026,204,672 |
| `node_swap_free_avg` | job=monitoring-node | 3,048,412,160 |
| `node_swap_free_avg` | job=mysql-node | 2,713,821,184 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 260,018.29 |
| `node_swap_in_delta` | job=monitoring-node | 820.57 |
| `node_swap_in_delta` | job=mysql-node | 14.86 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 147,861.71 |
| `node_swap_out_delta` | job=monitoring-node | 733.71 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 77,804.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 3 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 13,515.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 54.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 788.57 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T06:34:23.642Z ~ 2026-08-11T06:36:23.642Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 118 | 769.2 | 316.89 | 3,075.5 | 4,337.92 | 40,850.09 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 90 | 259.3 | 78.29 | 844.08 | 2,956.37 | 39,870.28 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 428 | 400.14 | 186.15 | 2,320.48 | 3,131.49 | 39,991.46 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 1,215 | 442,425.02 | 30,000 | 30,000 | 30,000 | 824,787.67 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 707 | 308,283.26 | 30,000 | 30,000 | 30,000 | 757,202.67 |
| method=GET, status=200, uri=/error | 9 | 455,418.28 | 30,000 | 30,000 | 30,000 | 476,271.16 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 157.93 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 56,794 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 56,783.75 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 56,773.01 |
| method=OPTIONS, status=200, uri=/api/auctions | 1 | 349.17 | 328.96 | 355.02 | 357.33 | 353.29 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 4 | 77.1 | 94.74 | 153.23 | 155.92 | 229.99 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1 | 974.59 | 939.52 | 979.79 | 983.37 | 42,246.72 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 5 | 3,063.65 | 2,505.4 | 6,871.95 | 7,101.01 | 39,950.73 |
| method=POST, status=401, uri=UNKNOWN | 28 | 7.03 | 0.68 | 60.68 | 84.33 | 1,102.84 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 32 | 7,017.96 | 5,726.62 | 15,127.83 | 15,624.14 | 56,770.7 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.67 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24.83 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 45.15 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.06 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.15 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.27 |
| `process_rss_avg` | job=backend-node | 12,966,912 |
| `process_rss_avg` | job=monitoring-node | 19,398,656 |
| `process_rss_avg` | job=mysql-exporter | 16,520,192 |
| `process_rss_avg` | job=mysql-node | 22,482,944 |
| `process_rss_avg` | job=prometheus | 127,409,664 |
| `process_rss_avg` | job=redis-exporter | 17,944,576 |
| `process_rss_avg` | job=redis-node | 22,302,720 |
| `process_rss_max` | job=backend-node | 13,234,176 |
| `process_rss_max` | job=monitoring-node | 19,447,808 |
| `process_rss_max` | job=mysql-exporter | 16,859,136 |
| `process_rss_max` | job=mysql-node | 22,962,176 |
| `process_rss_max` | job=prometheus | 136,028,160 |
| `process_rss_max` | job=redis-exporter | 18,272,256 |
| `process_rss_max` | job=redis-node | 22,302,720 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 598.17 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 259.17 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,001 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 582 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8.33 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 86.39 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.67 |
| `node_cpu_pct_avg` | job=mysql-node | 4.56 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 15.57 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 0.22 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 168,665.14 |
| `node_major_fault_delta` | job=monitoring-node | 105.14 |
| `node_major_fault_delta` | job=mysql-node | 152 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 88,791,552 |
| `node_mem_available_avg` | job=monitoring-node | 259,706,880 |
| `node_mem_available_avg` | job=mysql-node | 251,697,664 |
| `node_mem_available_avg` | job=redis-node | 576,282,624 |
| `node_swap_free_avg` | job=backend-node | 1,991,478,272 |
| `node_swap_free_avg` | job=monitoring-node | 3,044,145,152 |
| `node_swap_free_avg` | job=mysql-node | 2,713,812,992 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 251,556.57 |
| `node_swap_in_delta` | job=monitoring-node | 34.29 |
| `node_swap_in_delta` | job=mysql-node | 4.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 117,794.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 33.14 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 16,096 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 4 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 219,900.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 9.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 4.23 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 228.57 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T06:36:23.642Z ~ 2026-08-11T06:38:23.642Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 40,850.09 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3 | 44.09 | 6.29 | 130.86 | 133.55 | 31,617.56 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 39,991.46 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 824,787.67 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 757,202.67 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 476,271.16 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 26.68 |
| method=GET, status=404, uri=/** | 1 | 12.25 | 11.92 | 12.58 | 346.28 | 12.25 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 56,794 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 56,783.75 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 56,773.01 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 353.29 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 3 | 0.91 | 0.55 | 16.06 | 16.63 | 15.58 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 135.48 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 42,246.72 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 39,950.73 |
| method=POST, status=401, uri=UNKNOWN | 103 | 18.46 | 0.55 | 2.18 | 545.82 | 1,102.84 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 56,770.7 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.14 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.86 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 10.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 17.1 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.47 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95.86 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.04 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_rss_avg` | job=backend-node | 12,685,312 |
| `process_rss_avg` | job=monitoring-node | 19,426,304 |
| `process_rss_avg` | job=mysql-exporter | 16,547,840 |
| `process_rss_avg` | job=mysql-node | 22,625,792 |
| `process_rss_avg` | job=prometheus | 121,772,032 |
| `process_rss_avg` | job=redis-exporter | 18,272,256 |
| `process_rss_avg` | job=redis-node | 22,364,672 |
| `process_rss_max` | job=backend-node | 13,074,432 |
| `process_rss_max` | job=monitoring-node | 19,447,808 |
| `process_rss_max` | job=mysql-exporter | 17,063,936 |
| `process_rss_max` | job=mysql-node | 22,781,952 |
| `process_rss_max` | job=prometheus | 122,523,648 |
| `process_rss_max` | job=redis-exporter | 18,272,256 |
| `process_rss_max` | job=redis-node | 22,564,864 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 46 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 24.06 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.51 |
| `node_cpu_pct_avg` | job=mysql-node | 2.32 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 4.01 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 0.03 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 49,024 |
| `node_major_fault_delta` | job=monitoring-node | 744 |
| `node_major_fault_delta` | job=mysql-node | 0 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 112,450,048 |
| `node_mem_available_avg` | job=monitoring-node | 304,816,128 |
| `node_mem_available_avg` | job=mysql-node | 252,327,936 |
| `node_mem_available_avg` | job=redis-node | 576,196,608 |
| `node_swap_free_avg` | job=backend-node | 2,076,200,448 |
| `node_swap_free_avg` | job=monitoring-node | 3,053,323,776 |
| `node_swap_free_avg` | job=mysql-node | 2,713,788,416 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 77,988.57 |
| `node_swap_in_delta` | job=monitoring-node | 449.14 |
| `node_swap_in_delta` | job=mysql-node | 0 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 36,756.57 |
| `node_swap_out_delta` | job=monitoring-node | 448 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 12,582.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T06:38:23.642Z ~ 2026-08-11T06:40:23.642Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1 | 63.71 | 64.31 | 66.83 | 67.05 | 100 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 121.13 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 757,202.67 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 12.25 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 15.58 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 571 | 24.99 | 24.28 | 41.01 | 46.98 | 54.34 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 467 | 2.35 | 1.37 | 3.07 | 24.91 | 293.25 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 120 | 0.79 | 0.64 | 2.86 | 4.18 | 599.7 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 12.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.94 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 93.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 95 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.04 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.1 |
| `process_rss_avg` | job=backend-node | 13,147,648 |
| `process_rss_avg` | job=monitoring-node | 19,323,392 |
| `process_rss_avg` | job=mysql-exporter | 16,635,904 |
| `process_rss_avg` | job=mysql-node | 22,546,432 |
| `process_rss_avg` | job=prometheus | 113,364,992 |
| `process_rss_avg` | job=redis-exporter | 18,272,256 |
| `process_rss_avg` | job=redis-node | 22,395,392 |
| `process_rss_max` | job=backend-node | 13,217,792 |
| `process_rss_max` | job=monitoring-node | 19,406,848 |
| `process_rss_max` | job=mysql-exporter | 17,031,168 |
| `process_rss_max` | job=mysql-node | 22,953,984 |
| `process_rss_max` | job=prometheus | 120,758,272 |
| `process_rss_max` | job=redis-exporter | 18,272,256 |
| `process_rss_max` | job=redis-node | 22,511,616 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 72 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 72 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 410 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 410 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 44.5 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 13.39 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 5.1 |
| `node_cpu_pct_avg` | job=redis-node | 0.58 |
| `node_load1_avg` | job=backend-node | 0.72 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.06 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 25,339.43 |
| `node_major_fault_delta` | job=monitoring-node | 36.57 |
| `node_major_fault_delta` | job=mysql-node | 170.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 82,019,840 |
| `node_mem_available_avg` | job=monitoring-node | 397,244,416 |
| `node_mem_available_avg` | job=mysql-node | 246,951,424 |
| `node_mem_available_avg` | job=redis-node | 568,626,176 |
| `node_swap_free_avg` | job=backend-node | 2,105,246,720 |
| `node_swap_free_avg` | job=monitoring-node | 3,085,740,032 |
| `node_swap_free_avg` | job=mysql-node | 2,713,788,416 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 61,514.29 |
| `node_swap_in_delta` | job=monitoring-node | 18.29 |
| `node_swap_in_delta` | job=mysql-node | 3.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 28,926.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 23,552 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 3,809.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 724.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-post-g1-pure-throughput-sse500-lowqps-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T06:39:47.527Z ~ 2026-08-11T06:49:29.449Z
- 설정: `{"sseVUs":500,"totalSseConnections":1000,"qpsStages":[50,100,150,200],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T06:40:22.527Z ~ 2026-08-11T06:42:22.527Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,177 | 215.96 | 99.19 | 800.83 | 1,714.73 | 3,753.04 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,129 | 79.11 | 27 | 217.85 | 851.12 | 4,166.34 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,455 | 117.84 | 42.44 | 433.11 | 932.27 | 4,159.25 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 54.34 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 293.25 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,016 | 224.9 | 80.9 | 446.57 | 2,704.04 | 7,679.93 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 63 | 222.24 | 202.57 | 430.62 | 1,234.8 | 1,364.92 |
| method=POST, status=401, uri=UNKNOWN | 81 | 5.5 | 0.79 | 20.83 | 81.54 | 365.6 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 2 | 170.12 | 156.59 | 199.09 | 200.88 | 191.09 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 7.2 | 7.51 | 39.93 | 43.78 | 8.24 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.25 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 68.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 8.31 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 103.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.36 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.6 |
| `process_rss_avg` | job=backend-node | 12,228,608 |
| `process_rss_avg` | job=monitoring-node | 19,207,168 |
| `process_rss_avg` | job=mysql-exporter | 16,509,952 |
| `process_rss_avg` | job=mysql-node | 22,435,840 |
| `process_rss_avg` | job=prometheus | 106,049,536 |
| `process_rss_avg` | job=redis-exporter | 18,137,088 |
| `process_rss_avg` | job=redis-node | 22,389,760 |
| `process_rss_max` | job=backend-node | 14,589,952 |
| `process_rss_max` | job=monitoring-node | 19,271,680 |
| `process_rss_max` | job=mysql-exporter | 17,096,704 |
| `process_rss_max` | job=mysql-node | 22,712,320 |
| `process_rss_max` | job=prometheus | 106,127,360 |
| `process_rss_max` | job=redis-exporter | 18,137,088 |
| `process_rss_max` | job=redis-node | 22,507,520 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 14 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 89.27 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 18.4 |
| `node_cpu_pct_avg` | job=redis-node | 0.53 |
| `node_load1_avg` | job=backend-node | 9.13 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.69 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 128,061.71 |
| `node_major_fault_delta` | job=monitoring-node | 25.14 |
| `node_major_fault_delta` | job=mysql-node | 122.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 46,515,712 |
| `node_mem_available_avg` | job=monitoring-node | 438,608,384 |
| `node_mem_available_avg` | job=mysql-node | 244,816,896 |
| `node_mem_available_avg` | job=redis-node | 556,296,704 |
| `node_swap_free_avg` | job=backend-node | 2,057,835,008 |
| `node_swap_free_avg` | job=monitoring-node | 3,101,744,128 |
| `node_swap_free_avg` | job=mysql-node | 2,713,788,416 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 227,317.71 |
| `node_swap_in_delta` | job=monitoring-node | 12.57 |
| `node_swap_in_delta` | job=mysql-node | 300.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 122,212.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 95,125.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 3,125.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 46.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,881.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T06:42:22.527Z ~ 2026-08-11T06:44:22.527Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,752 | 26.89 | 18.85 | 59.31 | 161.58 | 3,753.04 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,787 | 16.2 | 9.36 | 32.92 | 197.25 | 4,166.34 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,311 | 20.42 | 13.73 | 42.37 | 165.03 | 4,159.25 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 54.34 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 293.25 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,753 | 38.08 | 26.74 | 84.87 | 228.62 | 7,679.93 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 6 | 159.43 | 206.92 | 343.44 | 355.02 | 1,364.92 |
| method=POST, status=401, uri=UNKNOWN | 119 | 3.76 | 0.64 | 3.77 | 33.33 | 365.6 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 7 | 11.67 | 10.05 | 28.74 | 32.59 | 191.09 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 14.11 | 16.08 | 21.53 | 22.2 | 70.09 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 48 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.89 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.53 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.69 |
| `process_rss_avg` | job=backend-node | 12,776,960 |
| `process_rss_avg` | job=monitoring-node | 19,329,024 |
| `process_rss_avg` | job=mysql-exporter | 16,480,768 |
| `process_rss_avg` | job=mysql-node | 22,254,080 |
| `process_rss_avg` | job=prometheus | 124,656,128 |
| `process_rss_avg` | job=redis-exporter | 18,410,496 |
| `process_rss_avg` | job=redis-node | 22,467,584 |
| `process_rss_max` | job=backend-node | 15,175,680 |
| `process_rss_max` | job=monitoring-node | 19,378,176 |
| `process_rss_max` | job=mysql-exporter | 16,863,232 |
| `process_rss_max` | job=mysql-node | 22,376,448 |
| `process_rss_max` | job=prometheus | 132,890,624 |
| `process_rss_max` | job=redis-exporter | 18,530,304 |
| `process_rss_max` | job=redis-node | 22,634,496 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 82.37 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.05 |
| `node_cpu_pct_avg` | job=mysql-node | 32.13 |
| `node_cpu_pct_avg` | job=redis-node | 0.66 |
| `node_load1_avg` | job=backend-node | 7.27 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.67 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 33,492.57 |
| `node_major_fault_delta` | job=monitoring-node | 89.14 |
| `node_major_fault_delta` | job=mysql-node | 57.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 43,036,672 |
| `node_mem_available_avg` | job=monitoring-node | 295,902,720 |
| `node_mem_available_avg` | job=mysql-node | 247,960,576 |
| `node_mem_available_avg` | job=redis-node | 559,800,320 |
| `node_swap_free_avg` | job=backend-node | 2,042,172,928 |
| `node_swap_free_avg` | job=monitoring-node | 3,103,084,032 |
| `node_swap_free_avg` | job=mysql-node | 2,713,788,416 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 48,354.29 |
| `node_swap_in_delta` | job=monitoring-node | 66.29 |
| `node_swap_in_delta` | job=mysql-node | 18.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 16,881.14 |
| `node_swap_out_delta` | job=monitoring-node | 97.14 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 170,052.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 227.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 6.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 3,526.86 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T06:44:22.527Z ~ 2026-08-11T06:46:22.527Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,992 | 64.23 | 60.58 | 102.82 | 148.94 | 557.33 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,943 | 29.8 | 27.71 | 49.03 | 85.08 | 325.76 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,902 | 44.13 | 41.39 | 74.79 | 110.41 | 4,159.25 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,917 | 87.84 | 83.73 | 133.25 | 199.66 | 676.98 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 33 | 55.69 | 40.82 | 168.89 | 194.84 | 435 |
| method=POST, status=401, uri=UNKNOWN | 120 | 2.15 | 0.88 | 8.3 | 11.11 | 260.35 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 9 | 27.5 | 26.1 | 48.09 | 49.88 | 48.68 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 7 | 27.32 | 25.17 | 43.06 | 44.4 | 70.09 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 64 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.82 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.72 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.74 |
| `process_rss_avg` | job=backend-node | 12,921,344 |
| `process_rss_avg` | job=monitoring-node | 19,476,480 |
| `process_rss_avg` | job=mysql-exporter | 16,556,032 |
| `process_rss_avg` | job=mysql-node | 22,346,240 |
| `process_rss_avg` | job=prometheus | 135,460,352 |
| `process_rss_avg` | job=redis-exporter | 17,835,008 |
| `process_rss_avg` | job=redis-node | 22,364,160 |
| `process_rss_max` | job=backend-node | 13,426,688 |
| `process_rss_max` | job=monitoring-node | 19,509,248 |
| `process_rss_max` | job=mysql-exporter | 16,830,464 |
| `process_rss_max` | job=mysql-node | 22,630,400 |
| `process_rss_max` | job=prometheus | 139,575,296 |
| `process_rss_max` | job=redis-exporter | 18,227,200 |
| `process_rss_max` | job=redis-node | 22,364,160 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 6.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 13 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 99.89 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.3 |
| `node_cpu_pct_avg` | job=mysql-node | 50.2 |
| `node_cpu_pct_avg` | job=redis-node | 0.78 |
| `node_load1_avg` | job=backend-node | 10.72 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.01 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 14,462.86 |
| `node_major_fault_delta` | job=monitoring-node | 118.86 |
| `node_major_fault_delta` | job=mysql-node | 61.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 60,474,368 |
| `node_mem_available_avg` | job=monitoring-node | 255,982,592 |
| `node_mem_available_avg` | job=mysql-node | 255,207,936 |
| `node_mem_available_avg` | job=redis-node | 563,802,112 |
| `node_swap_free_avg` | job=backend-node | 2,041,808,896 |
| `node_swap_free_avg` | job=monitoring-node | 3,103,139,328 |
| `node_swap_free_avg` | job=mysql-node | 2,713,788,416 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 20,331.43 |
| `node_swap_in_delta` | job=monitoring-node | 68.57 |
| `node_swap_in_delta` | job=mysql-node | 40 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 16,284.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 279,197.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,494.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 54.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 5,857.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T06:46:22.527Z ~ 2026-08-11T06:48:22.527Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,591 | 280.92 | 234.79 | 662.61 | 957.2 | 3,024.97 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,291 | 161.6 | 112.28 | 474.04 | 655.55 | 2,838.68 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 10,143 | 198.87 | 156.12 | 532.46 | 740.74 | 2,708.95 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,459 | 284.26 | 179.6 | 704.4 | 936.2 | 2,509.39 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 511 | 242.26 | 197.41 | 516.28 | 701.81 | 2,370.3 |
| method=POST, status=401, uri=UNKNOWN | 69 | 12.76 | 2.62 | 67.11 | 149.88 | 139.81 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 8 | 196.94 | 212.51 | 481.59 | 496.32 | 470.53 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 180.05 | 44.74 | 478.96 | 495.79 | 491.23 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 1 | 2,133.87 | 2,073.74 | 2,140.11 | 2,146.01 | 2,133.87 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 8.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13.63 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 72 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.02 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 121.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 142 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.73 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.8 |
| `process_rss_avg` | job=backend-node | 13,383,168 |
| `process_rss_avg` | job=monitoring-node | 19,189,760 |
| `process_rss_avg` | job=mysql-exporter | 16,594,432 |
| `process_rss_avg` | job=mysql-node | 22,368,256 |
| `process_rss_avg` | job=prometheus | 131,155,456 |
| `process_rss_avg` | job=redis-exporter | 17,358,336 |
| `process_rss_avg` | job=redis-node | 22,400,000 |
| `process_rss_max` | job=backend-node | 14,147,584 |
| `process_rss_max` | job=monitoring-node | 19,509,248 |
| `process_rss_max` | job=mysql-exporter | 16,961,536 |
| `process_rss_max` | job=mysql-node | 22,740,992 |
| `process_rss_max` | job=prometheus | 135,274,496 |
| `process_rss_max` | job=redis-exporter | 18,075,648 |
| `process_rss_max` | job=redis-node | 22,581,248 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 501 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 825.14 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 35.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 99.82 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.92 |
| `node_cpu_pct_avg` | job=mysql-node | 51.23 |
| `node_cpu_pct_avg` | job=redis-node | 0.68 |
| `node_load1_avg` | job=backend-node | 28.29 |
| `node_load1_avg` | job=monitoring-node | 0.16 |
| `node_load1_avg` | job=mysql-node | 1.72 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 36,877.71 |
| `node_major_fault_delta` | job=monitoring-node | 346.29 |
| `node_major_fault_delta` | job=mysql-node | 36.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 56,305,664 |
| `node_mem_available_avg` | job=monitoring-node | 265,217,536 |
| `node_mem_available_avg` | job=mysql-node | 260,236,288 |
| `node_mem_available_avg` | job=redis-node | 575,803,392 |
| `node_swap_free_avg` | job=backend-node | 2,040,157,184 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,395,904 |
| `node_swap_free_avg` | job=mysql-node | 2,712,819,712 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 61,302.86 |
| `node_swap_in_delta` | job=monitoring-node | 77.71 |
| `node_swap_in_delta` | job=mysql-node | 29.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 37,481.14 |
| `node_swap_out_delta` | job=monitoring-node | 122.29 |
| `node_swap_out_delta` | job=mysql-node | 406.86 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 287,202.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 9,187.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 98.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 4,827.43 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-post-g1-pure-throughput-sse1000-lowqps-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T06:51:17.739Z ~ 2026-08-11T07:01:01.544Z
- 설정: `{"sseVUs":1000,"totalSseConnections":2000,"qpsStages":[50,100,150,200],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T06:51:52.739Z ~ 2026-08-11T06:53:52.739Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 703 | 763.52 | 484.82 | 2,512.11 | 6,594.21 | 8,699.88 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 544 | 352.01 | 219.22 | 1,042.77 | 1,816.22 | 2,732.36 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 1,820 | 521.42 | 325.12 | 1,520.62 | 3,564.17 | 8,069.04 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 583,673.54 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 622,859.01 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 600,963.15 |
| method=GET, status=401, uri=UNKNOWN | 762 | 8.94 | 0.76 | 26.62 | 123.7 | 973.12 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 1 | 1,040.14 | 1,036.87 | 1,070.05 | 1,073 | 1,040.14 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | N/A | N/A | N/A | N/A | N/A | 1.42 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | N/A | N/A | N/A | N/A | N/A | 1,508.45 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 489.97 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 1,523 | 24.97 | 0.66 | 50.98 | 766.23 | 1,501.39 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 263 | 1,517.35 | 920.88 | 4,586.41 | 5,498.58 | 4,375.38 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 102 | 593.31 | 391.47 | 2,621.72 | 4,139.27 | 3,953.54 |
| method=POST, status=401, uri=UNKNOWN | 11 | 4.93 | 0.75 | 31.04 | 33.05 | 94.62 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 2 | 40.97 | 33.55 | 55.36 | 55.81 | 52.07 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 1 | 4,660.67 | 5,010.8 | 5,655.04 | 5,712.31 | 4,660.67 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.33 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24.17 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.33 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 115.4 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 25.94 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 101.83 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.19 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_rss_avg` | job=backend-node | 13,725,696 |
| `process_rss_avg` | job=monitoring-node | 19,427,328 |
| `process_rss_avg` | job=mysql-exporter | 16,517,120 |
| `process_rss_avg` | job=mysql-node | 22,278,144 |
| `process_rss_avg` | job=prometheus | 132,606,464 |
| `process_rss_avg` | job=redis-exporter | 17,715,200 |
| `process_rss_avg` | job=redis-node | 22,486,528 |
| `process_rss_max` | job=backend-node | 15,687,680 |
| `process_rss_max` | job=monitoring-node | 19,476,480 |
| `process_rss_max` | job=mysql-exporter | 16,912,384 |
| `process_rss_max` | job=mysql-node | 22,540,288 |
| `process_rss_max` | job=prometheus | 134,922,240 |
| `process_rss_max` | job=redis-exporter | 17,928,192 |
| `process_rss_max` | job=redis-node | 22,700,032 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 824.67 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 254.5 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,002 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 548 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.17 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 96.35 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.5 |
| `node_cpu_pct_avg` | job=mysql-node | 9.99 |
| `node_cpu_pct_avg` | job=redis-node | 0.4 |
| `node_load1_avg` | job=backend-node | 6.91 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 0.33 |
| `node_load1_avg` | job=redis-node | 0.01 |
| `node_major_fault_delta` | job=backend-node | 150,094.86 |
| `node_major_fault_delta` | job=monitoring-node | 18.29 |
| `node_major_fault_delta` | job=mysql-node | 14.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 67,557,376 |
| `node_mem_available_avg` | job=monitoring-node | 307,208,704 |
| `node_mem_available_avg` | job=mysql-node | 253,026,816 |
| `node_mem_available_avg` | job=redis-node | 575,837,184 |
| `node_swap_free_avg` | job=backend-node | 2,031,313,920 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,291,456 |
| `node_swap_free_avg` | job=mysql-node | 2,708,766,720 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 269,534.86 |
| `node_swap_in_delta` | job=monitoring-node | 11.43 |
| `node_swap_in_delta` | job=mysql-node | 5.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 170,811.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 67,313.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,089.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 12.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 642.29 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T06:53:52.739Z ~ 2026-08-11T06:55:52.739Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,051 | 426.17 | 357.07 | 1,010.09 | 1,584.37 | 36,978.96 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 921 | 254.19 | 167.44 | 762.8 | 1,006.64 | 2,732.36 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,001 | 312.33 | 233.87 | 836.47 | 1,413.4 | 35,146.67 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 369 | 141,903.46 | 30,000 | 30,000 | 30,000 | 1,127,056.02 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 1 | 1,115,414.15 | 30,000 | 30,000 | 30,000 | 1,115,414.15 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 109 | 11.48 | 0.72 | 20.32 | 642.46 | 973.12 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 1,040.14 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 1.42 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 1,508.45 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 1 | 4,900.11 | 5,113.06 | 8,017.27 | 8,475.4 | 4,900.11 |
| method=POST, status=200, uri=/api/sse/tickets | 487 | 9.51 | 0.84 | 29.01 | 249.98 | 1,501.39 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 668 | 563.03 | 494.58 | 1,294.46 | 1,684.09 | 35,177.89 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 244 | 276.89 | 199.09 | 741.18 | 1,462.08 | 36,294.76 |
| method=POST, status=401, uri=UNKNOWN | 3 | 72.42 | 67.11 | 87.24 | 89.03 | 87.31 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 52.07 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 633.62 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 4,660.67 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 26 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 114.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 7.08 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.54 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.9 |
| `process_rss_avg` | job=backend-node | 12,710,400 |
| `process_rss_avg` | job=monitoring-node | 19,421,184 |
| `process_rss_avg` | job=mysql-exporter | 16,602,624 |
| `process_rss_avg` | job=mysql-node | 22,266,368 |
| `process_rss_avg` | job=prometheus | 132,229,632 |
| `process_rss_avg` | job=redis-exporter | 17,928,192 |
| `process_rss_avg` | job=redis-node | 22,435,840 |
| `process_rss_max` | job=backend-node | 14,274,560 |
| `process_rss_max` | job=monitoring-node | 19,476,480 |
| `process_rss_max` | job=mysql-exporter | 16,982,016 |
| `process_rss_max` | job=mysql-node | 22,466,560 |
| `process_rss_max` | job=prometheus | 132,476,928 |
| `process_rss_max` | job=redis-exporter | 17,928,192 |
| `process_rss_max` | job=redis-node | 22,564,864 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 807.25 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 802.75 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 950 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 977 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 42 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.5 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 95.19 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.42 |
| `node_cpu_pct_avg` | job=mysql-node | 15.08 |
| `node_cpu_pct_avg` | job=redis-node | 0.44 |
| `node_load1_avg` | job=backend-node | 12.62 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 0.61 |
| `node_load1_avg` | job=redis-node | 0.03 |
| `node_major_fault_delta` | job=backend-node | 169,449.14 |
| `node_major_fault_delta` | job=monitoring-node | 404.57 |
| `node_major_fault_delta` | job=mysql-node | 29.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 57,782,784 |
| `node_mem_available_avg` | job=monitoring-node | 271,273,472 |
| `node_mem_available_avg` | job=mysql-node | 252,357,632 |
| `node_mem_available_avg` | job=redis-node | 575,844,352 |
| `node_swap_free_avg` | job=backend-node | 1,972,042,240 |
| `node_swap_free_avg` | job=monitoring-node | 3,095,366,144 |
| `node_swap_free_avg` | job=mysql-node | 2,708,766,720 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 251,691.43 |
| `node_swap_in_delta` | job=monitoring-node | 26.29 |
| `node_swap_in_delta` | job=mysql-node | 22.86 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 158,280 |
| `node_swap_out_delta` | job=monitoring-node | 218.29 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 84,141.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 5,195.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 52.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,361.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T06:55:52.739Z ~ 2026-08-11T06:57:52.739Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,073 | 927.39 | 261.6 | 5,079.12 | 11,935.24 | 61,266.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,037 | 184.24 | 114.36 | 491.36 | 962.66 | 52,899 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,133 | 234.93 | 149.7 | 567.76 | 1,476.14 | 53,550.59 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 426 | 266,780.05 | 30,000 | 30,000 | 30,000 | 1,127,056.02 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 1,187 | 188,036.41 | 30,000 | 30,000 | 30,000 | 1,115,414.15 |
| method=GET, status=200, uri=/error | 1 | 85,912.39 | 30,000 | 30,000 | 30,000 | 85,912.39 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 687.43 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 1,040.14 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 1.42 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 1,508.45 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 4,900.11 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 484.87 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 402 | 566.9 | 322.97 | 2,275.74 | 5,048.02 | 35,177.89 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 73 | 309.68 | 137.95 | 1,070.05 | 2,644.98 | 36,294.76 |
| method=POST, status=401, uri=UNKNOWN | 18 | 331.34 | 7.69 | 1,700.09 | 1,771.67 | 3,109.3 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 4,660.67 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10.17 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.19 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 104.99 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.19 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 55.7 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 9.97 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.71 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 103.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.32 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.48 |
| `process_rss_avg` | job=backend-node | 13,236,224 |
| `process_rss_avg` | job=monitoring-node | 19,394,560 |
| `process_rss_avg` | job=mysql-exporter | 16,603,648 |
| `process_rss_avg` | job=mysql-node | 22,318,592 |
| `process_rss_avg` | job=prometheus | 136,851,456 |
| `process_rss_avg` | job=redis-exporter | 18,141,184 |
| `process_rss_avg` | job=redis-node | 22,392,832 |
| `process_rss_max` | job=backend-node | 16,084,992 |
| `process_rss_max` | job=monitoring-node | 19,460,096 |
| `process_rss_max` | job=mysql-exporter | 16,945,152 |
| `process_rss_max` | job=mysql-node | 22,528,000 |
| `process_rss_max` | job=prometheus | 140,865,536 |
| `process_rss_max` | job=redis-exporter | 18,190,336 |
| `process_rss_max` | job=redis-node | 22,392,832 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 587.17 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 516.17 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 725 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.5 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 4 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.6 |
| `node_cpu_pct_avg` | job=mysql-node | 21.85 |
| `node_cpu_pct_avg` | job=redis-node | 5.9 |
| `node_load1_avg` | job=backend-node | 12.24 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.63 |
| `node_load1_avg` | job=redis-node | 0.03 |
| `node_major_fault_delta` | job=backend-node | 166,714.29 |
| `node_major_fault_delta` | job=monitoring-node | 1,107.43 |
| `node_major_fault_delta` | job=mysql-node | 26.29 |
| `node_major_fault_delta` | job=redis-node | 22.86 |
| `node_mem_available_avg` | job=backend-node | 65,078,784 |
| `node_mem_available_avg` | job=monitoring-node | 255,355,392 |
| `node_mem_available_avg` | job=mysql-node | 253,578,240 |
| `node_mem_available_avg` | job=redis-node | 563,655,680 |
| `node_swap_free_avg` | job=backend-node | 1,961,737,728 |
| `node_swap_free_avg` | job=monitoring-node | 3,075,602,944 |
| `node_swap_free_avg` | job=mysql-node | 2,708,766,720 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 249,168 |
| `node_swap_in_delta` | job=monitoring-node | 358.86 |
| `node_swap_in_delta` | job=mysql-node | 16 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 131,778.29 |
| `node_swap_out_delta` | job=monitoring-node | 436.57 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 146,604.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 3,817.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 48 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,782.86 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T06:57:52.739Z ~ 2026-08-11T06:59:52.739Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,528 | 306.71 | 257.25 | 582.8 | 1,587.15 | 61,266.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,471 | 192.46 | 150.32 | 429.07 | 867.94 | 52,899 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 10,570 | 228.22 | 182.05 | 487.69 | 899.26 | 53,550.59 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 284,233.1 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 326,997.37 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 85,912.39 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 1 | 470.87 | 473.7 | 497.37 | 499.47 | 470.87 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,670 | 374.63 | 321.15 | 683.91 | 1,596.3 | 20,362.43 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,855 | 202.69 | 157.78 | 425.21 | 768.62 | 20,120.16 |
| method=POST, status=401, uri=UNKNOWN | 21 | 30.88 | 12.58 | 181.19 | 197.3 | 3,109.3 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 4 | 246.5 | 167.77 | 433.97 | 444.71 | 424.54 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 45.55 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 2 | 2,449.03 | 2,326.44 | 2,487.5 | 2,501.82 | 2,479.02 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.43 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.43 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.57 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 107.87 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 7.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 113.14 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 127 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.68 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.78 |
| `process_rss_avg` | job=backend-node | 13,468,672 |
| `process_rss_avg` | job=monitoring-node | 19,536,896 |
| `process_rss_avg` | job=mysql-exporter | 16,487,936 |
| `process_rss_avg` | job=mysql-node | 22,348,800 |
| `process_rss_avg` | job=prometheus | 135,888,896 |
| `process_rss_avg` | job=redis-exporter | 18,321,408 |
| `process_rss_avg` | job=redis-node | 22,392,832 |
| `process_rss_max` | job=backend-node | 14,364,672 |
| `process_rss_max` | job=monitoring-node | 19,591,168 |
| `process_rss_max` | job=mysql-exporter | 16,846,848 |
| `process_rss_max` | job=mysql-node | 22,499,328 |
| `process_rss_max` | job=prometheus | 136,060,928 |
| `process_rss_max` | job=redis-exporter | 18,321,408 |
| `process_rss_max` | job=redis-node | 22,392,832 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 368 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 4 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 368 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 4 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 378.71 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.03 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.4 |
| `node_cpu_pct_avg` | job=mysql-node | 45.04 |
| `node_cpu_pct_avg` | job=redis-node | 16.92 |
| `node_load1_avg` | job=backend-node | 26.91 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.28 |
| `node_load1_avg` | job=redis-node | 0.37 |
| `node_major_fault_delta` | job=backend-node | 122,170.29 |
| `node_major_fault_delta` | job=monitoring-node | 32 |
| `node_major_fault_delta` | job=mysql-node | 30.86 |
| `node_major_fault_delta` | job=redis-node | 2.29 |
| `node_mem_available_avg` | job=backend-node | 56,288,768 |
| `node_mem_available_avg` | job=monitoring-node | 248,302,080 |
| `node_mem_available_avg` | job=mysql-node | 253,396,992 |
| `node_mem_available_avg` | job=redis-node | 518,215,168 |
| `node_swap_free_avg` | job=backend-node | 1,960,321,536 |
| `node_swap_free_avg` | job=monitoring-node | 3,073,196,032 |
| `node_swap_free_avg` | job=mysql-node | 2,708,770,816 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 197,432 |
| `node_swap_in_delta` | job=monitoring-node | 11.43 |
| `node_swap_in_delta` | job=mysql-node | 9.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 105,244.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 295,396.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 4,869.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 67.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 3,554.29 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## hot-auction-pattern-sse500-20260811.json

- 시나리오: `hot-auction-pattern`
- K6 실행: 2026-08-11T07:12:55.166Z ~ 2026-08-11T07:21:00.501Z
- 설정: `{"auctionCount":200,"hotAuctionCount":3,"hotAuctionRate":14,"coldAuctionRatePerAuction":0.09,"coldAuctionRate":18,"sseUsers":500,"totalSseConnections":1000,"duration":"5m"}`

### 0~1분 — 2026-08-11T07:14:00.166Z ~ 2026-08-11T07:15:00.166Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 1,849 | 586.44 | 369.22 | 1,310.32 | 5,990.29 | 35,698.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 469.47 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 344.2 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 33.28 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 185 | 694.9 | 300 | 903.73 | 2,798.89 | 36,454.66 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 569 | 429.97 | 238.2 | 743.42 | 2,668.84 | 39,590.34 |
| method=POST, status=401, uri=UNKNOWN | 3 | 40.48 | 1 | 87.24 | 89.03 | 80.68 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 713.87 | 671.09 | 711.35 | 714.93 | 713.87 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 33,358.32 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 36.07 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.19 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.26 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.47 |
| `process_rss_avg` | job=backend-node | 12,982,272 |
| `process_rss_avg` | job=monitoring-node | 19,628,032 |
| `process_rss_avg` | job=mysql-exporter | 16,446,464 |
| `process_rss_avg` | job=mysql-node | 22,413,312 |
| `process_rss_avg` | job=prometheus | 118,087,680 |
| `process_rss_avg` | job=redis-exporter | 18,423,808 |
| `process_rss_avg` | job=redis-node | 22,433,792 |
| `process_rss_max` | job=backend-node | 13,148,160 |
| `process_rss_max` | job=monitoring-node | 19,628,032 |
| `process_rss_max` | job=mysql-exporter | 16,769,024 |
| `process_rss_max` | job=mysql-node | 22,573,056 |
| `process_rss_max` | job=prometheus | 132,145,152 |
| `process_rss_max` | job=redis-exporter | 18,423,808 |
| `process_rss_max` | job=redis-node | 22,433,792 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 502 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 502 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 94.02 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.98 |
| `node_cpu_pct_avg` | job=mysql-node | 4.64 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 2.07 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.05 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 85,532 |
| `node_major_fault_delta` | job=monitoring-node | 30.67 |
| `node_major_fault_delta` | job=mysql-node | 4 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 64,949,248 |
| `node_mem_available_avg` | job=monitoring-node | 423,092,224 |
| `node_mem_available_avg` | job=mysql-node | 250,830,848 |
| `node_mem_available_avg` | job=redis-node | 561,717,248 |
| `node_swap_free_avg` | job=backend-node | 2,011,723,776 |
| `node_swap_free_avg` | job=monitoring-node | 3,099,635,712 |
| `node_swap_free_avg` | job=mysql-node | 2,706,509,824 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 151,765.33 |
| `node_swap_in_delta` | job=monitoring-node | 8 |
| `node_swap_in_delta` | job=mysql-node | 2.67 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 73,765.33 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 3,380 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 99,933.33 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 25.33 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 256 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 1~2분 — 2026-08-11T07:15:00.166Z ~ 2026-08-11T07:16:00.166Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,855 | 116.99 | 89.45 | 268.37 | 356.22 | 35,698.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 469.47 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 344.2 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 33.28 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 880 | 165.1 | 142.12 | 345.85 | 450.02 | 36,454.66 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,513 | 195.2 | 183.18 | 420.04 | 524.9 | 39,590.34 |
| method=POST, status=401, uri=UNKNOWN | 28 | 3.94 | 2.8 | 9.65 | 15.07 | 80.68 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 10 | 117.61 | 134.22 | 214.75 | 221.91 | 281.67 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 192.5 | 190.14 | 346.33 | 355.6 | 713.87 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 33,358.32 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12.33 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.67 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.67 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 34.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.09 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107.67 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.72 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.77 |
| `process_rss_avg` | job=backend-node | 11,917,312 |
| `process_rss_avg` | job=monitoring-node | 19,628,032 |
| `process_rss_avg` | job=mysql-exporter | 16,463,872 |
| `process_rss_avg` | job=mysql-node | 22,338,560 |
| `process_rss_avg` | job=prometheus | 138,797,056 |
| `process_rss_avg` | job=redis-exporter | 18,194,432 |
| `process_rss_avg` | job=redis-node | 22,433,792 |
| `process_rss_max` | job=backend-node | 12,251,136 |
| `process_rss_max` | job=monitoring-node | 19,628,032 |
| `process_rss_max` | job=mysql-exporter | 16,781,312 |
| `process_rss_max` | job=mysql-node | 22,405,120 |
| `process_rss_max` | job=prometheus | 138,829,824 |
| `process_rss_max` | job=redis-exporter | 18,358,272 |
| `process_rss_max` | job=redis-node | 22,433,792 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 502 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 502 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 20 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.38 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.27 |
| `node_cpu_pct_avg` | job=mysql-node | 35.63 |
| `node_cpu_pct_avg` | job=redis-node | 0.6 |
| `node_load1_avg` | job=backend-node | 13.66 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.17 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 74,508 |
| `node_major_fault_delta` | job=monitoring-node | 4 |
| `node_major_fault_delta` | job=mysql-node | 30.67 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 58,998,784 |
| `node_mem_available_avg` | job=monitoring-node | 331,209,728 |
| `node_mem_available_avg` | job=mysql-node | 250,480,640 |
| `node_mem_available_avg` | job=redis-node | 563,009,536 |
| `node_swap_free_avg` | job=backend-node | 1,987,360,768 |
| `node_swap_free_avg` | job=monitoring-node | 3,099,635,712 |
| `node_swap_free_avg` | job=mysql-node | 2,706,509,824 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 130,782.67 |
| `node_swap_in_delta` | job=monitoring-node | 4 |
| `node_swap_in_delta` | job=mysql-node | 30.67 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 51,658.67 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 110,893.33 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 21 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 459,884 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 2,778.67 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 6.67 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 12.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,094.67 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 2~3분 — 2026-08-11T07:16:00.166Z ~ 2026-08-11T07:17:00.166Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2 | 4,301.71 | 5,010.8 | 5,655.04 | 5,712.31 | 4,301.71 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,022 | 55.04 | 15.83 | 291.06 | 599.45 | 35,698.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0.89 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,020 | 61.95 | 32.09 | 159.94 | 550.29 | 36,454.66 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 710 | 85.43 | 49.18 | 241.17 | 580.12 | 39,590.34 |
| method=POST, status=401, uri=UNKNOWN | 58 | 98.34 | 1.22 | 1,172.17 | 1,379.76 | 1,398.08 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 68 | 26.35 | 9.2 | 118.56 | 193.72 | 281.67 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 24 | 12.3 | 8.04 | 30.2 | 32.88 | 713.87 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 33,358.32 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11.33 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.67 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 8 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 26 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.86 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107.67 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.32 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.47 |
| `process_rss_avg` | job=backend-node | 12,729,344 |
| `process_rss_avg` | job=monitoring-node | 19,628,032 |
| `process_rss_avg` | job=mysql-exporter | 16,262,144 |
| `process_rss_avg` | job=mysql-node | 22,465,536 |
| `process_rss_avg` | job=prometheus | 140,140,544 |
| `process_rss_avg` | job=redis-exporter | 17,092,608 |
| `process_rss_avg` | job=redis-node | 22,433,792 |
| `process_rss_max` | job=backend-node | 13,434,880 |
| `process_rss_max` | job=monitoring-node | 19,628,032 |
| `process_rss_max` | job=mysql-exporter | 16,908,288 |
| `process_rss_max` | job=mysql-node | 22,548,480 |
| `process_rss_max` | job=prometheus | 140,140,544 |
| `process_rss_max` | job=redis-exporter | 17,215,488 |
| `process_rss_max` | job=redis-node | 22,433,792 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 502 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 502 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 19 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 88 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 45.84 |
| `node_cpu_pct_avg` | job=redis-node | 0.8 |
| `node_load1_avg` | job=backend-node | 13.06 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.96 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 86,369.33 |
| `node_major_fault_delta` | job=monitoring-node | 4 |
| `node_major_fault_delta` | job=mysql-node | 66.67 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 65,274,880 |
| `node_mem_available_avg` | job=monitoring-node | 323,899,392 |
| `node_mem_available_avg` | job=mysql-node | 251,675,648 |
| `node_mem_available_avg` | job=redis-node | 562,900,992 |
| `node_swap_free_avg` | job=backend-node | 1,984,401,408 |
| `node_swap_free_avg` | job=monitoring-node | 3,099,635,712 |
| `node_swap_free_avg` | job=mysql-node | 2,706,509,824 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 138,281.33 |
| `node_swap_in_delta` | job=monitoring-node | 2.67 |
| `node_swap_in_delta` | job=mysql-node | 46.67 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 70,802.67 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 100,322.67 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 41,370.67 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 674.67 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 4,465.33 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 3~4분 — 2026-08-11T07:17:00.166Z ~ 2026-08-11T07:18:00.166Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 4,301.71 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,319 | 132 | 61.82 | 593.95 | 1,322.75 | 35,698.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 139 | 299,387.2 | 30,000 | 30,000 | 30,000 | 1,462,622.54 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 895 | 126.3 | 56.11 | 424.58 | 920.73 | 36,454.66 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,227 | 189.35 | 135.19 | 651.2 | 1,330.45 | 39,590.34 |
| method=POST, status=401, uri=UNKNOWN | 28 | 2.84 | 0.95 | 8.37 | 12.29 | 1,398.08 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 8 | 111.33 | 89.48 | 290.53 | 298.11 | 1,042.14 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 141.79 | 145.4 | 155.47 | 156.36 | 713.87 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 33,358.32 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 20 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.09 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 796.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 1,042 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.35 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.58 |
| `process_rss_avg` | job=backend-node | 13,290,496 |
| `process_rss_avg` | job=monitoring-node | 19,628,032 |
| `process_rss_avg` | job=mysql-exporter | 16,638,976 |
| `process_rss_avg` | job=mysql-node | 22,603,776 |
| `process_rss_avg` | job=prometheus | 122,511,360 |
| `process_rss_avg` | job=redis-exporter | 17,477,632 |
| `process_rss_avg` | job=redis-node | 22,493,184 |
| `process_rss_max` | job=backend-node | 14,950,400 |
| `process_rss_max` | job=monitoring-node | 19,628,032 |
| `process_rss_max` | job=mysql-exporter | 17,117,184 |
| `process_rss_max` | job=mysql-node | 22,659,072 |
| `process_rss_max` | job=prometheus | 123,002,880 |
| `process_rss_max` | job=redis-exporter | 17,739,776 |
| `process_rss_max` | job=redis-node | 22,564,864 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 469.25 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 502 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 920 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 9 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 17 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.88 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.27 |
| `node_cpu_pct_avg` | job=mysql-node | 34.27 |
| `node_cpu_pct_avg` | job=redis-node | 0.59 |
| `node_load1_avg` | job=backend-node | 13.46 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.48 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 81,498.67 |
| `node_major_fault_delta` | job=monitoring-node | 2.67 |
| `node_major_fault_delta` | job=mysql-node | 36 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 73,823,232 |
| `node_mem_available_avg` | job=monitoring-node | 349,888,512 |
| `node_mem_available_avg` | job=mysql-node | 253,349,888 |
| `node_mem_available_avg` | job=redis-node | 562,680,832 |
| `node_swap_free_avg` | job=backend-node | 1,982,656,512 |
| `node_swap_free_avg` | job=monitoring-node | 3,099,635,712 |
| `node_swap_free_avg` | job=mysql-node | 2,706,509,824 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 128,128 |
| `node_swap_in_delta` | job=monitoring-node | 1.33 |
| `node_swap_in_delta` | job=mysql-node | 37.33 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 92,274.67 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 97,682.67 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 12 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 214,766.67 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,625.33 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 6.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,118.67 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 4~5분 — 2026-08-11T07:18:00.166Z ~ 2026-08-11T07:19:00.166Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `process_rss_avg` | job=backend-node | 13,790,208 |
| `process_rss_avg` | job=monitoring-node | 19,628,032 |
| `process_rss_avg` | job=mysql-exporter | 16,196,608 |
| `process_rss_avg` | job=mysql-node | 22,467,584 |
| `process_rss_avg` | job=prometheus | 123,002,880 |
| `process_rss_avg` | job=redis-exporter | 18,034,688 |
| `process_rss_avg` | job=redis-node | 22,540,288 |
| `process_rss_max` | job=backend-node | 14,749,696 |
| `process_rss_max` | job=monitoring-node | 19,628,032 |
| `process_rss_max` | job=mysql-exporter | 16,535,552 |
| `process_rss_max` | job=mysql-node | 22,515,712 |
| `process_rss_max` | job=prometheus | 123,002,880 |
| `process_rss_max` | job=redis-exporter | 18,264,064 |
| `process_rss_max` | job=redis-node | 22,540,288 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 91.24 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.08 |
| `node_cpu_pct_avg` | job=mysql-node | 16.62 |
| `node_cpu_pct_avg` | job=redis-node | 0.42 |
| `node_load1_avg` | job=backend-node | 14.83 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.37 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 99,034.67 |
| `node_major_fault_delta` | job=monitoring-node | 1.33 |
| `node_major_fault_delta` | job=mysql-node | 12 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 46,409,728 |
| `node_mem_available_avg` | job=monitoring-node | 358,394,880 |
| `node_mem_available_avg` | job=mysql-node | 250,986,496 |
| `node_mem_available_avg` | job=redis-node | 562,573,312 |
| `node_swap_free_avg` | job=backend-node | 1,982,230,528 |
| `node_swap_free_avg` | job=monitoring-node | 3,099,635,712 |
| `node_swap_free_avg` | job=mysql-node | 2,706,509,824 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 151,756 |
| `node_swap_in_delta` | job=monitoring-node | 1.33 |
| `node_swap_in_delta` | job=mysql-node | 4 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 70,088 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 60,101.33 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 5 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 273,457.33 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,205.33 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 417.33 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-post-g1-pure-throughput-sse250-lowqps-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T07:21:29.121Z ~ 2026-08-11T07:31:09.911Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T07:22:04.121Z ~ 2026-08-11T07:24:04.121Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,287 | 107.08 | 13.49 | 555.89 | 1,246.88 | 1,628.62 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,125 | 26.33 | 6.62 | 158.08 | 262.56 | 551.62 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,621 | 56.42 | 9.83 | 279.34 | 748.04 | 25,673.53 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 158 | 60,374.77 | 30,000 | 30,000 | 30,000 | 391,770.1 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 426,353.68 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 16.1 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 70.07 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 3 | 2.34 | 1.92 | 5.38 | 5.55 | 25.79 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,088 | 58.92 | 22.02 | 303.86 | 436.66 | 5,702.74 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 40 | 96.15 | 76.43 | 229.29 | 260.61 | 5,941.93 |
| method=POST, status=401, uri=UNKNOWN | 98 | 1.39 | 0.61 | 4.68 | 39.93 | 92.35 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 5 | 7.57 | 5.59 | 15.1 | 15.32 | 14.42 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 23 | 9.79 | 38.59 | 39.03 | 156.94 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 21.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.7 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.21 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.44 |
| `process_rss_avg` | job=backend-node | 12,862,464 |
| `process_rss_avg` | job=monitoring-node | 19,610,624 |
| `process_rss_avg` | job=mysql-exporter | 16,513,024 |
| `process_rss_avg` | job=mysql-node | 22,403,072 |
| `process_rss_avg` | job=prometheus | 115,447,808 |
| `process_rss_avg` | job=redis-exporter | 17,696,768 |
| `process_rss_avg` | job=redis-node | 22,351,360 |
| `process_rss_max` | job=backend-node | 13,099,008 |
| `process_rss_max` | job=monitoring-node | 19,628,032 |
| `process_rss_max` | job=mysql-exporter | 16,900,096 |
| `process_rss_max` | job=mysql-node | 22,605,824 |
| `process_rss_max` | job=prometheus | 118,398,976 |
| `process_rss_max` | job=redis-exporter | 18,386,944 |
| `process_rss_max` | job=redis-node | 22,405,120 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 145.75 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 245.88 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 15 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 50.05 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.27 |
| `node_cpu_pct_avg` | job=mysql-node | 22.03 |
| `node_cpu_pct_avg` | job=redis-node | 0.54 |
| `node_load1_avg` | job=backend-node | 3.26 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 0.33 |
| `node_load1_avg` | job=redis-node | 0.01 |
| `node_major_fault_delta` | job=backend-node | 70,970.29 |
| `node_major_fault_delta` | job=monitoring-node | 10.29 |
| `node_major_fault_delta` | job=mysql-node | 35.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 48,577,024 |
| `node_mem_available_avg` | job=monitoring-node | 432,267,264 |
| `node_mem_available_avg` | job=mysql-node | 256,225,792 |
| `node_mem_available_avg` | job=redis-node | 562,831,872 |
| `node_swap_free_avg` | job=backend-node | 2,030,777,856 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,967,296 |
| `node_swap_free_avg` | job=mysql-node | 2,706,436,096 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 132,929.14 |
| `node_swap_in_delta` | job=monitoring-node | 6.86 |
| `node_swap_in_delta` | job=mysql-node | 12.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 46,187.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 108,502.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 616 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 11.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 35 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,237.71 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T07:24:04.121Z ~ 2026-08-11T07:26:04.121Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,813 | 71.86 | 14.14 | 346.67 | 1,482.66 | 5,056.37 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,815 | 38.77 | 7.18 | 218.33 | 485.87 | 4,742.16 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,438 | 48.25 | 10.82 | 254.43 | 587.63 | 5,012.7 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 70,477.53 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 1 | 141.55 | 145.4 | 155.47 | 156.36 | 141.55 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 25.79 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,746 | 56.91 | 24.22 | 310.25 | 497.9 | 4,812.33 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 45 | 173.29 | 132.35 | 419.06 | 522.49 | 530.73 |
| method=POST, status=401, uri=UNKNOWN | 109 | 3.41 | 0.61 | 5.07 | 135.34 | 142 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 13 | 4.78 | 4.91 | 6.61 | 6.91 | 29.43 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 10 | 84.12 | 6.82 | 407.13 | 439.34 | 385.37 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 1,857.82 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 27.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.18 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.34 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.5 |
| `process_rss_avg` | job=backend-node | 12,682,240 |
| `process_rss_avg` | job=monitoring-node | 19,554,304 |
| `process_rss_avg` | job=mysql-exporter | 16,544,768 |
| `process_rss_avg` | job=mysql-node | 22,437,888 |
| `process_rss_avg` | job=prometheus | 119,824,384 |
| `process_rss_avg` | job=redis-exporter | 17,920,000 |
| `process_rss_avg` | job=redis-node | 22,322,688 |
| `process_rss_max` | job=backend-node | 13,504,512 |
| `process_rss_max` | job=monitoring-node | 19,619,840 |
| `process_rss_max` | job=mysql-exporter | 17,117,184 |
| `process_rss_max` | job=mysql-node | 22,573,056 |
| `process_rss_max` | job=prometheus | 120,627,200 |
| `process_rss_max` | job=redis-exporter | 18,132,992 |
| `process_rss_max` | job=redis-node | 22,392,832 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 112.88 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 113 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 58.94 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 33.85 |
| `node_cpu_pct_avg` | job=redis-node | 0.6 |
| `node_load1_avg` | job=backend-node | 6.56 |
| `node_load1_avg` | job=monitoring-node | 0.08 |
| `node_load1_avg` | job=mysql-node | 0.48 |
| `node_load1_avg` | job=redis-node | 0.02 |
| `node_major_fault_delta` | job=backend-node | 56,428.57 |
| `node_major_fault_delta` | job=monitoring-node | 13.71 |
| `node_major_fault_delta` | job=mysql-node | 35.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 43,129,344 |
| `node_mem_available_avg` | job=monitoring-node | 429,130,752 |
| `node_mem_available_avg` | job=mysql-node | 259,044,352 |
| `node_mem_available_avg` | job=redis-node | 562,864,128 |
| `node_swap_free_avg` | job=backend-node | 2,041,259,008 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,969,856 |
| `node_swap_free_avg` | job=mysql-node | 2,706,436,096 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 103,182.86 |
| `node_swap_in_delta` | job=monitoring-node | 5.71 |
| `node_swap_in_delta` | job=mysql-node | 29.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 44,870.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 181,694.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 3,109.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 40 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 35 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 3,513.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T07:26:04.121Z ~ 2026-08-11T07:28:04.121Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,018 | 17.14 | 15.18 | 30.89 | 43.99 | 5,056.37 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,011 | 8.56 | 7.51 | 16.13 | 24.06 | 4,742.16 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,045 | 13.03 | 11.62 | 23.43 | 32.99 | 5,012.7 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 141.55 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,976 | 28.72 | 26.68 | 44.03 | 61.1 | 4,812.33 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 6 | 14.75 | 13.28 | 32.16 | 33.27 | 530.73 |
| method=POST, status=401, uri=UNKNOWN | 120 | 0.7 | 0.64 | 2.3 | 4.18 | 142 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 16 | 7.56 | 7.46 | 13 | 13.79 | 29.43 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 16 | 8.88 | 8.85 | 14.4 | 15.18 | 385.37 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 1,857.82 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 38.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.48 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.5 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.59 |
| `process_rss_avg` | job=backend-node | 13,272,064 |
| `process_rss_avg` | job=monitoring-node | 19,734,528 |
| `process_rss_avg` | job=mysql-exporter | 16,562,176 |
| `process_rss_avg` | job=mysql-node | 22,531,584 |
| `process_rss_avg` | job=prometheus | 117,651,456 |
| `process_rss_avg` | job=redis-exporter | 17,782,272 |
| `process_rss_avg` | job=redis-node | 22,347,776 |
| `process_rss_max` | job=backend-node | 13,508,608 |
| `process_rss_max` | job=monitoring-node | 19,750,912 |
| `process_rss_max` | job=mysql-exporter | 17,059,840 |
| `process_rss_max` | job=mysql-node | 22,806,528 |
| `process_rss_max` | job=prometheus | 120,627,200 |
| `process_rss_max` | job=redis-exporter | 18,235,392 |
| `process_rss_max` | job=redis-node | 22,380,544 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 113 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 113 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 6 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 68.57 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.19 |
| `node_cpu_pct_avg` | job=mysql-node | 54.34 |
| `node_cpu_pct_avg` | job=redis-node | 0.78 |
| `node_load1_avg` | job=backend-node | 3.27 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 0.8 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 9,733.71 |
| `node_major_fault_delta` | job=monitoring-node | 9.14 |
| `node_major_fault_delta` | job=mysql-node | 50.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 52,355,584 |
| `node_mem_available_avg` | job=monitoring-node | 422,152,704 |
| `node_mem_available_avg` | job=mysql-node | 261,083,648 |
| `node_mem_available_avg` | job=redis-node | 562,864,128 |
| `node_swap_free_avg` | job=backend-node | 2,042,664,960 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,969,856 |
| `node_swap_free_avg` | job=mysql-node | 2,706,436,096 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 13,812.57 |
| `node_swap_in_delta` | job=monitoring-node | 5.71 |
| `node_swap_in_delta` | job=mysql-node | 37.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 6,000 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 298,672 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 81.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 6.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 35 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 5,973.71 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T07:28:04.121Z ~ 2026-08-11T07:30:04.121Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,198 | 60.2 | 38.09 | 148.29 | 269.86 | 631.4 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,226 | 28.8 | 18.91 | 71.45 | 146.3 | 553.61 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,634 | 41.91 | 27.49 | 100.5 | 182.51 | 577.84 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,086 | 83.5 | 58.1 | 194.81 | 275.89 | 1,019.08 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 62 | 77.8 | 55.92 | 208.04 | 256.36 | 265.13 |
| method=POST, status=401, uri=UNKNOWN | 120 | 3.11 | 0.97 | 10.14 | 22.09 | 68.92 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 22 | 55.11 | 27.03 | 501.84 | 529.87 | 507.84 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 34 | 27.63 | 15.38 | 66.18 | 82.77 | 79.37 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 1 | 670.87 | 675.15 | 1,873.44 | 1,974.69 | 670.87 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 16.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 53.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.2 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.74 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.8 |
| `process_rss_avg` | job=backend-node | 13,278,720 |
| `process_rss_avg` | job=monitoring-node | 19,595,264 |
| `process_rss_avg` | job=mysql-exporter | 16,576,512 |
| `process_rss_avg` | job=mysql-node | 22,378,496 |
| `process_rss_avg` | job=prometheus | 114,751,488 |
| `process_rss_avg` | job=redis-exporter | 17,942,528 |
| `process_rss_avg` | job=redis-node | 22,354,944 |
| `process_rss_max` | job=backend-node | 13,492,224 |
| `process_rss_max` | job=monitoring-node | 19,595,264 |
| `process_rss_max` | job=mysql-exporter | 16,982,016 |
| `process_rss_max` | job=mysql-node | 22,638,592 |
| `process_rss_max` | job=prometheus | 115,265,536 |
| `process_rss_max` | job=redis-exporter | 18,366,464 |
| `process_rss_max` | job=redis-node | 22,380,544 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 113 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 113 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 13.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 94.12 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 70.43 |
| `node_cpu_pct_avg` | job=redis-node | 0.86 |
| `node_load1_avg` | job=backend-node | 6.83 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 2.19 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 13,960 |
| `node_major_fault_delta` | job=monitoring-node | 6.86 |
| `node_major_fault_delta` | job=mysql-node | 62.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 44,486,144 |
| `node_mem_available_avg` | job=monitoring-node | 434,371,584 |
| `node_mem_available_avg` | job=mysql-node | 263,711,232 |
| `node_mem_available_avg` | job=redis-node | 562,960,896 |
| `node_swap_free_avg` | job=backend-node | 2,042,490,368 |
| `node_swap_free_avg` | job=monitoring-node | 3,103,120,384 |
| `node_swap_free_avg` | job=mysql-node | 2,706,416,128 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 22,363.43 |
| `node_swap_in_delta` | job=monitoring-node | 4.57 |
| `node_swap_in_delta` | job=mysql-node | 59.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 11,393.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 44.57 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 397,602.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 4,638.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 152 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 35 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 8,201.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## bid-only-load-noSSE-20260811.json

- 시나리오: `bid-only-load (SSE 없음)`
- K6 실행: 2026-08-11T09:22:54.958Z ~ 2026-08-11T09:35:07.035Z
- 설정: `{"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T09:22:54.958Z ~ 2026-08-11T09:24:54.958Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,202 | 77.77 | 12.13 | 249.2 | 1,650.12 | 1,674.05 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,184 | 27.29 | 5.63 | 144.41 | 219.89 | 1,527.04 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,586 | 39.42 | 9.2 | 161.65 | 351.25 | 1,574.77 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 2 | 1,800,955.38 | 30,000 | 30,000 | 30,000 | 1,800,963.16 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 1 | 10.11 | 10.49 | 11.11 | 11.17 | 10.11 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 510.18 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,173 | 59.35 | 21.71 | 237.9 | 342.18 | 4,561.13 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 26 | 153.09 | 152.86 | 265.08 | 292.74 | 275.17 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 106 | 2.93 | 0.53 | 1.87 | 202.89 | 219.78 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 6.72 | 5.59 | 9.65 | 9.76 | 8.49 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 19.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.64 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 95.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.2 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_rss_avg` | job=backend-node | 14,258,176 |
| `process_rss_avg` | job=monitoring-node | 19,569,152 |
| `process_rss_avg` | job=mysql-exporter | 16,434,176 |
| `process_rss_avg` | job=mysql-node | 22,491,648 |
| `process_rss_avg` | job=prometheus | 122,374,656 |
| `process_rss_avg` | job=redis-exporter | 17,977,344 |
| `process_rss_avg` | job=redis-node | 22,652,928 |
| `process_rss_max` | job=backend-node | 17,379,328 |
| `process_rss_max` | job=monitoring-node | 19,701,760 |
| `process_rss_max` | job=mysql-exporter | 16,752,640 |
| `process_rss_max` | job=mysql-node | 22,609,920 |
| `process_rss_max` | job=prometheus | 124,047,360 |
| `process_rss_max` | job=redis-exporter | 18,206,720 |
| `process_rss_max` | job=redis-node | 22,855,680 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 47 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 45.62 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.23 |
| `node_cpu_pct_avg` | job=mysql-node | 26.26 |
| `node_cpu_pct_avg` | job=redis-node | 0.54 |
| `node_load1_avg` | job=backend-node | 2.61 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 0.94 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 62,587.43 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 307.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 54,632,960 |
| `node_mem_available_avg` | job=monitoring-node | 425,733,632 |
| `node_mem_available_avg` | job=mysql-node | 268,984,832 |
| `node_mem_available_avg` | job=redis-node | 569,032,704 |
| `node_swap_free_avg` | job=backend-node | 2,131,947,008 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,322,688 |
| `node_swap_free_avg` | job=mysql-node | 2,680,035,328 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 117,945.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 1,091.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 26,705.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 146,892.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,370.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 20.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,402.29 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T09:24:54.958Z ~ 2026-08-11T09:26:54.958Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,722 | 12.6 | 11.72 | 17.85 | 33.51 | 1,674.05 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,770 | 5.7 | 5.28 | 8.13 | 13.64 | 1,527.04 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,239 | 9.26 | 8.63 | 12.91 | 22.78 | 1,574.77 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 1,800,963.16 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 10.11 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 510.18 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,736 | 22.17 | 20.69 | 29.28 | 41.85 | 4,561.13 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 275.17 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 120 | 0.4 | 0.53 | 1.04 | 1.63 | 219.78 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 3 | 5.82 | 4.89 | 8.18 | 8.35 | 7.93 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 5.39 | 5.07 | 6.64 | 6.92 | 8.49 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 27.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.4 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.08 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.26 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.42 |
| `process_rss_avg` | job=backend-node | 17,447,936 |
| `process_rss_avg` | job=monitoring-node | 19,566,592 |
| `process_rss_avg` | job=mysql-exporter | 16,584,704 |
| `process_rss_avg` | job=mysql-node | 22,589,952 |
| `process_rss_avg` | job=prometheus | 123,852,800 |
| `process_rss_avg` | job=redis-exporter | 18,223,104 |
| `process_rss_avg` | job=redis-node | 22,712,320 |
| `process_rss_max` | job=backend-node | 18,345,984 |
| `process_rss_max` | job=monitoring-node | 19,681,280 |
| `process_rss_max` | job=mysql-exporter | 16,986,112 |
| `process_rss_max` | job=mysql-node | 22,749,184 |
| `process_rss_max` | job=prometheus | 125,890,560 |
| `process_rss_max` | job=redis-exporter | 18,337,792 |
| `process_rss_max` | job=redis-node | 22,712,320 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 1.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 33.84 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.67 |
| `node_cpu_pct_avg` | job=mysql-node | 35.78 |
| `node_cpu_pct_avg` | job=redis-node | 0.64 |
| `node_load1_avg` | job=backend-node | 1.42 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 0.8 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 3,512 |
| `node_major_fault_delta` | job=monitoring-node | 10.29 |
| `node_major_fault_delta` | job=mysql-node | 185.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 79,399,936 |
| `node_mem_available_avg` | job=monitoring-node | 424,525,312 |
| `node_mem_available_avg` | job=mysql-node | 269,144,576 |
| `node_mem_available_avg` | job=redis-node | 569,032,704 |
| `node_swap_free_avg` | job=backend-node | 2,146,749,952 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,322,688 |
| `node_swap_free_avg` | job=mysql-node | 2,680,037,376 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 5,753.14 |
| `node_swap_in_delta` | job=monitoring-node | 4.57 |
| `node_swap_in_delta` | job=mysql-node | 491.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 4,657.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 198,585.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 3,490.29 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T09:26:54.958Z ~ 2026-08-11T09:28:54.958Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,919 | 33.7 | 13.32 | 177.18 | 273.04 | 411.2 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,867 | 18.91 | 6.22 | 107.01 | 223.58 | 417.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,722 | 28.39 | 10.12 | 133.02 | 258.05 | 8,937.96 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 1,800,963.16 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 1 | 45.25 | 47.54 | 50.05 | 50.28 | 45.25 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 10.11 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,847 | 47.48 | 25.3 | 227.94 | 342.64 | 572.95 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 77 | 140.76 | 110.86 | 330.69 | 464.75 | 476.4 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 110 | 1.13 | 0.59 | 4.47 | 22.59 | 23.5 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 3 | 7.34 | 5.24 | 13.77 | 13.94 | 13.07 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 17.62 | 6.99 | 53.69 | 55.48 | 52.39 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 42.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.83 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.43 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.7 |
| `process_rss_avg` | job=backend-node | 14,275,072 |
| `process_rss_avg` | job=monitoring-node | 19,619,328 |
| `process_rss_avg` | job=mysql-exporter | 16,600,064 |
| `process_rss_avg` | job=mysql-node | 22,668,288 |
| `process_rss_avg` | job=prometheus | 157,657,600 |
| `process_rss_avg` | job=redis-exporter | 18,337,792 |
| `process_rss_avg` | job=redis-node | 22,697,984 |
| `process_rss_max` | job=backend-node | 16,977,920 |
| `process_rss_max` | job=monitoring-node | 19,681,280 |
| `process_rss_max` | job=mysql-exporter | 17,088,512 |
| `process_rss_max` | job=mysql-node | 22,937,600 |
| `process_rss_max` | job=prometheus | 167,620,608 |
| `process_rss_max` | job=redis-exporter | 18,337,792 |
| `process_rss_max` | job=redis-node | 22,974,464 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 61.73 |
| `node_cpu_pct_avg` | job=monitoring-node | 3.86 |
| `node_cpu_pct_avg` | job=mysql-node | 52.94 |
| `node_cpu_pct_avg` | job=redis-node | 0.74 |
| `node_load1_avg` | job=backend-node | 2.26 |
| `node_load1_avg` | job=monitoring-node | 0.05 |
| `node_load1_avg` | job=mysql-node | 2.77 |
| `node_load1_avg` | job=redis-node | 0.02 |
| `node_major_fault_delta` | job=backend-node | 38,468.57 |
| `node_major_fault_delta` | job=monitoring-node | 45.71 |
| `node_major_fault_delta` | job=mysql-node | 290.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 63,990,784 |
| `node_mem_available_avg` | job=monitoring-node | 272,885,248 |
| `node_mem_available_avg` | job=mysql-node | 271,262,720 |
| `node_mem_available_avg` | job=redis-node | 569,032,704 |
| `node_swap_free_avg` | job=backend-node | 2,143,748,608 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,322,688 |
| `node_swap_free_avg` | job=mysql-node | 2,679,269,888 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 59,210.29 |
| `node_swap_in_delta` | job=monitoring-node | 8 |
| `node_swap_in_delta` | job=mysql-node | 542.86 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 15,198.86 |
| `node_swap_out_delta` | job=monitoring-node | 5.71 |
| `node_swap_out_delta` | job=mysql-node | 676.57 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 304,589.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,900.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 49.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 5,712 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T09:28:54.958Z ~ 2026-08-11T09:30:54.958Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,142 | 21.29 | 17.09 | 48.24 | 80.63 | 411.2 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,152 | 10.48 | 8.08 | 24.55 | 48.78 | 417.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,439 | 16.5 | 13.17 | 37.69 | 66.09 | 8,937.96 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 45.25 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,111 | 37.26 | 31.74 | 74.29 | 122.41 | 572.95 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 24 | 21.33 | 11.71 | 66.83 | 109.36 | 476.4 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 120 | 0.72 | 0.64 | 2.34 | 3.83 | 23.5 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 5 | 12.09 | 6.99 | 26.84 | 27.74 | 27.54 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 7 | 8 | 8.39 | 10.77 | 11.1 | 52.39 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 26 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 60.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.65 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.11 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.61 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.73 |
| `process_rss_avg` | job=backend-node | 13,654,016 |
| `process_rss_avg` | job=monitoring-node | 19,554,304 |
| `process_rss_avg` | job=mysql-exporter | 16,567,296 |
| `process_rss_avg` | job=mysql-node | 22,390,272 |
| `process_rss_avg` | job=prometheus | 155,631,616 |
| `process_rss_avg` | job=redis-exporter | 18,337,792 |
| `process_rss_avg` | job=redis-node | 22,581,248 |
| `process_rss_max` | job=backend-node | 13,897,728 |
| `process_rss_max` | job=monitoring-node | 19,718,144 |
| `process_rss_max` | job=mysql-exporter | 16,912,384 |
| `process_rss_max` | job=mysql-node | 22,482,944 |
| `process_rss_max` | job=prometheus | 166,277,120 |
| `process_rss_max` | job=redis-exporter | 18,337,792 |
| `process_rss_max` | job=redis-node | 22,614,016 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 72.4 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.44 |
| `node_cpu_pct_avg` | job=mysql-node | 72.24 |
| `node_cpu_pct_avg` | job=redis-node | 0.9 |
| `node_load1_avg` | job=backend-node | 5.49 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 3.43 |
| `node_load1_avg` | job=redis-node | 0.01 |
| `node_major_fault_delta` | job=backend-node | 3,254.86 |
| `node_major_fault_delta` | job=monitoring-node | 30.86 |
| `node_major_fault_delta` | job=mysql-node | 532.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 87,548,416 |
| `node_mem_available_avg` | job=monitoring-node | 247,716,864 |
| `node_mem_available_avg` | job=mysql-node | 269,330,432 |
| `node_mem_available_avg` | job=redis-node | 569,032,704 |
| `node_swap_free_avg` | job=backend-node | 2,147,171,840 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,322,688 |
| `node_swap_free_avg` | job=mysql-node | 2,678,025,216 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 4,497.14 |
| `node_swap_in_delta` | job=monitoring-node | 4.57 |
| `node_swap_in_delta` | job=mysql-node | 1,004.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 1,507.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 250.29 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 420,955.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 469.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 25.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 8,242.29 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T09:30:54.958Z ~ 2026-08-11T09:32:54.958Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,867 | 98.48 | 87.56 | 242.75 | 317.87 | 448.51 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,832 | 58.42 | 42.15 | 178.69 | 247.98 | 535.85 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 17,549 | 76.78 | 64.03 | 207.99 | 277.42 | 449.79 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 45.25 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 5,579 | 141.87 | 137.56 | 313.03 | 404.12 | 668.12 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 203 | 127.42 | 115.92 | 277.12 | 367.76 | 414.92 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 120 | 2.21 | 0.89 | 8.04 | 16.71 | 17.01 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 7 | 45.59 | 22.37 | 108.29 | 111.14 | 101.02 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 21 | 53.87 | 51.73 | 146.52 | 154.57 | 152.48 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 8.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 82.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.98 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.82 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.91 |
| `process_rss_avg` | job=backend-node | 14,028,288 |
| `process_rss_avg` | job=monitoring-node | 19,800,064 |
| `process_rss_avg` | job=mysql-exporter | 16,538,624 |
| `process_rss_avg` | job=mysql-node | 22,398,464 |
| `process_rss_avg` | job=prometheus | 152,997,376 |
| `process_rss_avg` | job=redis-exporter | 18,337,792 |
| `process_rss_avg` | job=redis-node | 22,614,016 |
| `process_rss_max` | job=backend-node | 14,553,088 |
| `process_rss_max` | job=monitoring-node | 19,849,216 |
| `process_rss_max` | job=mysql-exporter | 17,092,608 |
| `process_rss_max` | job=mysql-node | 22,609,920 |
| `process_rss_max` | job=prometheus | 157,167,616 |
| `process_rss_max` | job=redis-exporter | 18,337,792 |
| `process_rss_max` | job=redis-node | 22,614,016 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 27.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 93.01 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.8 |
| `node_cpu_pct_avg` | job=mysql-node | 88.62 |
| `node_cpu_pct_avg` | job=redis-node | 0.97 |
| `node_load1_avg` | job=backend-node | 11.89 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 7.63 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 2,690.29 |
| `node_major_fault_delta` | job=monitoring-node | 20.57 |
| `node_major_fault_delta` | job=mysql-node | 486.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 91,963,392 |
| `node_mem_available_avg` | job=monitoring-node | 250,234,880 |
| `node_mem_available_avg` | job=mysql-node | 260,637,184 |
| `node_mem_available_avg` | job=redis-node | 569,047,040 |
| `node_swap_free_avg` | job=backend-node | 2,148,027,392 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,322,688 |
| `node_swap_free_avg` | job=mysql-node | 2,677,805,568 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 5,811.43 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 905.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 16 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 30.86 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 522,702.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 2 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 14,256 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 321.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 9.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 10,330.29 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T09:32:54.958Z ~ 2026-08-11T09:34:54.958Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,835 | 223.44 | 177.59 | 437.94 | 890.11 | 19,781.44 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,932 | 141.81 | 95.63 | 302.35 | 707.33 | 20,361.95 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 14,762 | 170.2 | 120.77 | 341.63 | 800.8 | 19,661.42 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,798 | 310.78 | 236.76 | 684.76 | 2,623.51 | 19,573.53 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,628 | 146.25 | 117.28 | 287.57 | 626.35 | 18,734.99 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 22 | 9.49 | 6.29 | 62.08 | 66.1 | 61.91 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 4 | 67.76 | 53.13 | 110.07 | 111.49 | 101.02 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 172.65 | 134.22 | 241.59 | 245.17 | 226.67 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 4 | 2,036.52 | 1,947.39 | 2,451.71 | 2,494.66 | 5,539.28 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.71 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.29 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.29 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 87.21 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.96 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99.43 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.67 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.85 |
| `process_rss_avg` | job=backend-node | 13,003,776 |
| `process_rss_avg` | job=monitoring-node | 19,545,600 |
| `process_rss_avg` | job=mysql-exporter | 16,260,608 |
| `process_rss_avg` | job=mysql-node | 22,402,560 |
| `process_rss_avg` | job=prometheus | 149,992,960 |
| `process_rss_avg` | job=redis-exporter | 17,359,872 |
| `process_rss_avg` | job=redis-node | 22,614,016 |
| `process_rss_max` | job=backend-node | 13,463,552 |
| `process_rss_max` | job=monitoring-node | 19,849,216 |
| `process_rss_max` | job=mysql-exporter | 16,637,952 |
| `process_rss_max` | job=mysql-node | 22,491,136 |
| `process_rss_max` | job=prometheus | 154,435,584 |
| `process_rss_max` | job=redis-exporter | 18,337,792 |
| `process_rss_max` | job=redis-node | 22,614,016 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.79 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.76 |
| `node_cpu_pct_avg` | job=mysql-node | 55.06 |
| `node_cpu_pct_avg` | job=redis-node | 0.61 |
| `node_load1_avg` | job=backend-node | 24.24 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 9.44 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 122,619.43 |
| `node_major_fault_delta` | job=monitoring-node | 13.71 |
| `node_major_fault_delta` | job=mysql-node | 145.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,052,544 |
| `node_mem_available_avg` | job=monitoring-node | 251,633,664 |
| `node_mem_available_avg` | job=mysql-node | 261,480,448 |
| `node_mem_available_avg` | job=redis-node | 569,049,088 |
| `node_swap_free_avg` | job=backend-node | 2,136,498,176 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,322,688 |
| `node_swap_free_avg` | job=mysql-node | 2,677,625,856 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 217,542.86 |
| `node_swap_in_delta` | job=monitoring-node | 3.43 |
| `node_swap_in_delta` | job=mysql-node | 251.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 114,742.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 18.29 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 373,450.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 13,822.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 128 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 3,801.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## bid-only-load-singleHotAuction-20260811.json

- 시나리오: `bid-only-load (SSE 없음)`
- K6 실행: 2026-08-11T09:36:57.791Z ~ 2026-08-11T09:49:09.910Z
- 설정: `{"qpsStages":[25,50,100,150,250,400],"stageDuration":"2m","hotAuctionId":3000306}`

### QPS 25 — 2026-08-11T09:36:57.791Z ~ 2026-08-11T09:38:57.791Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 950 | 94.86 | 15.18 | 375.23 | 1,908.11 | 3,283.2 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 914 | 11.23 | 6.61 | 21.13 | 126.76 | 2,748.39 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,795 | 56.29 | 17.17 | 236.05 | 531.22 | 2,713.35 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 3 | 34.99 | 20.97 | 86.12 | 88.81 | 68.47 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 314 | 22.08 | 21.31 | 34.33 | 45.21 | 472.12 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 697 | 49.56 | 30.31 | 42.84 | 148.39 | 9,372.75 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 234 | 502.08 | 162.8 | 2,915.51 | 3,160.08 | 3,129.31 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 112 | 0.89 | 0.58 | 2.11 | 22.48 | 42.4 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 241.76 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 188.55 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 18.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.97 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.17 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_rss_avg` | job=backend-node | 12,988,416 |
| `process_rss_avg` | job=monitoring-node | 19,481,088 |
| `process_rss_avg` | job=mysql-exporter | 16,278,016 |
| `process_rss_avg` | job=mysql-node | 21,990,912 |
| `process_rss_avg` | job=prometheus | 127,815,680 |
| `process_rss_avg` | job=redis-exporter | 17,526,784 |
| `process_rss_avg` | job=redis-node | 22,614,016 |
| `process_rss_max` | job=backend-node | 13,246,464 |
| `process_rss_max` | job=monitoring-node | 19,558,400 |
| `process_rss_max` | job=mysql-exporter | 16,797,696 |
| `process_rss_max` | job=mysql-node | 22,077,440 |
| `process_rss_max` | job=prometheus | 128,487,424 |
| `process_rss_max` | job=redis-exporter | 17,899,520 |
| `process_rss_max` | job=redis-node | 22,614,016 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 11 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 49.5 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 40.72 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.19 |
| `node_cpu_pct_avg` | job=mysql-node | 29.21 |
| `node_cpu_pct_avg` | job=redis-node | 0.48 |
| `node_load1_avg` | job=backend-node | 4.71 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.75 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 58,933.71 |
| `node_major_fault_delta` | job=monitoring-node | 13.71 |
| `node_major_fault_delta` | job=mysql-node | 139.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 53,018,624 |
| `node_mem_available_avg` | job=monitoring-node | 309,748,736 |
| `node_mem_available_avg` | job=mysql-node | 263,291,392 |
| `node_mem_available_avg` | job=redis-node | 569,057,280 |
| `node_swap_free_avg` | job=backend-node | 2,092,513,792 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,322,688 |
| `node_swap_free_avg` | job=mysql-node | 2,672,574,464 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 102,977.14 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 251.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 37,764.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 128,921.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 84,283.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 166.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,443.43 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 50 — 2026-08-11T09:38:57.791Z ~ 2026-08-11T09:40:57.791Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 803 | 13.89 | 13.43 | 19.94 | 29.05 | 2,493.39 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 914 | 6.62 | 6.38 | 9.47 | 12.43 | 268.3 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,577 | 21.88 | 21.23 | 27.89 | 41.06 | 2,713.35 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 68.47 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 472.12 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 835 | 35.38 | 34.83 | 43.79 | 57.21 | 9,372.75 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 23 | 10.98 | 6.76 | 41.94 | 44.18 | 3,129.31 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 120 | 0.37 | 0.51 | 0.98 | 2.26 | 22.6 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 14.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.16 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.34 |
| `process_rss_avg` | job=backend-node | 13,529,088 |
| `process_rss_avg` | job=monitoring-node | 19,496,960 |
| `process_rss_avg` | job=mysql-exporter | 16,602,624 |
| `process_rss_avg` | job=mysql-node | 22,132,224 |
| `process_rss_avg` | job=prometheus | 134,871,040 |
| `process_rss_avg` | job=redis-exporter | 18,227,200 |
| `process_rss_avg` | job=redis-node | 22,538,240 |
| `process_rss_max` | job=backend-node | 13,643,776 |
| `process_rss_max` | job=monitoring-node | 19,496,960 |
| `process_rss_max` | job=mysql-exporter | 16,994,304 |
| `process_rss_max` | job=mysql-node | 22,515,712 |
| `process_rss_max` | job=prometheus | 154,935,296 |
| `process_rss_max` | job=redis-exporter | 18,292,736 |
| `process_rss_max` | job=redis-node | 22,614,016 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 1.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 22.11 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.92 |
| `node_cpu_pct_avg` | job=mysql-node | 40.3 |
| `node_cpu_pct_avg` | job=redis-node | 0.52 |
| `node_load1_avg` | job=backend-node | 1.48 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.38 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 5,651.43 |
| `node_major_fault_delta` | job=monitoring-node | 9.14 |
| `node_major_fault_delta` | job=mysql-node | 86.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 70,476,288 |
| `node_mem_available_avg` | job=monitoring-node | 376,885,760 |
| `node_mem_available_avg` | job=mysql-node | 262,253,056 |
| `node_mem_available_avg` | job=redis-node | 568,503,808 |
| `node_swap_free_avg` | job=backend-node | 2,115,588,608 |
| `node_swap_free_avg` | job=monitoring-node | 3,102,324,736 |
| `node_swap_free_avg` | job=mysql-node | 2,672,574,464 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 12,073.14 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 108.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 6,330.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 129,181.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 83.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 5.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,852.57 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T09:40:57.791Z ~ 2026-08-11T09:42:57.791Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,813 | 48.58 | 16.66 | 234.62 | 346.66 | 499.44 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,622 | 29.65 | 7.46 | 188.37 | 314.79 | 456.71 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,150 | 89.59 | 34.62 | 417.26 | 562.04 | 5,314.05 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 54 | 9.47 | 3.78 | 48.37 | 78.96 | 78.13 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 18.52 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 754 | 58.08 | 52.58 | 87.24 | 131.98 | 553.58 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 962 | 126.34 | 11.45 | 489.91 | 585.32 | 5,306.16 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 110 | 0.53 | 0.54 | 1.3 | 9.84 | 11.16 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=404, uri=/** | 1 | 36.94 | 36.4 | 38.96 | 30,000 | 36.94 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 24 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.72 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.26 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.38 |
| `process_rss_avg` | job=backend-node | 13,043,200 |
| `process_rss_avg` | job=monitoring-node | 19,628,032 |
| `process_rss_avg` | job=mysql-exporter | 16,332,800 |
| `process_rss_avg` | job=mysql-node | 21,963,264 |
| `process_rss_avg` | job=prometheus | 150,407,680 |
| `process_rss_avg` | job=redis-exporter | 18,522,112 |
| `process_rss_avg` | job=redis-node | 22,597,632 |
| `process_rss_max` | job=backend-node | 13,844,480 |
| `process_rss_max` | job=monitoring-node | 19,628,032 |
| `process_rss_max` | job=mysql-exporter | 16,920,576 |
| `process_rss_max` | job=mysql-node | 22,380,544 |
| `process_rss_max` | job=prometheus | 152,424,448 |
| `process_rss_max` | job=redis-exporter | 18,554,880 |
| `process_rss_max` | job=redis-node | 22,597,632 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 5.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 27 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 48.17 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.9 |
| `node_cpu_pct_avg` | job=mysql-node | 72.96 |
| `node_cpu_pct_avg` | job=redis-node | 0.52 |
| `node_load1_avg` | job=backend-node | 1.42 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 2.46 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 57,708.57 |
| `node_major_fault_delta` | job=monitoring-node | 2,560 |
| `node_major_fault_delta` | job=mysql-node | 74.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,047,424 |
| `node_mem_available_avg` | job=monitoring-node | 259,815,936 |
| `node_mem_available_avg` | job=mysql-node | 261,358,592 |
| `node_mem_available_avg` | job=redis-node | 564,641,792 |
| `node_swap_free_avg` | job=backend-node | 2,142,979,072 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,810,688 |
| `node_swap_free_avg` | job=mysql-node | 2,672,574,464 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 94,270.86 |
| `node_swap_in_delta` | job=monitoring-node | 682.29 |
| `node_swap_in_delta` | job=mysql-node | 114.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 13,602.29 |
| `node_swap_out_delta` | job=monitoring-node | 1,121.14 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 186,154.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 72,475.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 464 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 6.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 1,728 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T09:42:57.791Z ~ 2026-08-11T09:44:57.791Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,693 | 167.1 | 178.5 | 408.94 | 486.67 | 801.14 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,646 | 122.56 | 117.66 | 334.68 | 436.92 | 556.09 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 7,946 | 377.77 | 417.81 | 674.9 | 778.94 | 5,314.05 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 2.62 |
| method=GET, status=404, uri=/** | 24 | 2.33 | 2.02 | 4.18 | 5.3 | 78.13 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 192 | 213.1 | 100 | 675.56 | 774.88 | 815.25 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,287 | 273.47 | 253.8 | 614.26 | 763.52 | 5,306.16 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 88 | 0.36 | 0.53 | 1.01 | 1.61 | 11.16 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 36.94 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 32 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_rss_avg` | job=backend-node | 13,463,552 |
| `process_rss_avg` | job=monitoring-node | 19,628,032 |
| `process_rss_avg` | job=mysql-exporter | 16,539,136 |
| `process_rss_avg` | job=mysql-node | 22,027,264 |
| `process_rss_avg` | job=prometheus | 155,056,128 |
| `process_rss_avg` | job=redis-exporter | 18,669,568 |
| `process_rss_avg` | job=redis-node | 22,467,584 |
| `process_rss_max` | job=backend-node | 13,824,000 |
| `process_rss_max` | job=monitoring-node | 19,628,032 |
| `process_rss_max` | job=mysql-exporter | 16,936,960 |
| `process_rss_max` | job=mysql-node | 22,122,496 |
| `process_rss_max` | job=prometheus | 160,018,432 |
| `process_rss_max` | job=redis-exporter | 18,685,952 |
| `process_rss_max` | job=redis-node | 22,597,632 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 35.38 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 37.05 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.4 |
| `node_cpu_pct_avg` | job=mysql-node | 99.7 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 1.5 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 12.79 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 5,300.57 |
| `node_major_fault_delta` | job=monitoring-node | 400 |
| `node_major_fault_delta` | job=mysql-node | 33.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 75,687,424 |
| `node_mem_available_avg` | job=monitoring-node | 257,270,272 |
| `node_mem_available_avg` | job=mysql-node | 260,539,392 |
| `node_mem_available_avg` | job=redis-node | 564,645,888 |
| `node_swap_free_avg` | job=backend-node | 2,150,475,264 |
| `node_swap_free_avg` | job=monitoring-node | 3,045,154,304 |
| `node_swap_free_avg` | job=mysql-node | 2,672,612,864 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 12,266.29 |
| `node_swap_in_delta` | job=monitoring-node | 152 |
| `node_swap_in_delta` | job=mysql-node | 24 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 2,593.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 176,881.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 8 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 299,065.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,899.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 1.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 21.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 283.43 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 250 — 2026-08-11T09:44:57.791Z ~ 2026-08-11T09:46:57.791Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,015 | 232.67 | 225.59 | 430.53 | 518.73 | 801.14 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,547 | 184.72 | 180.2 | 380.74 | 474.35 | 639.84 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,190 | 517.91 | 514.99 | 714.32 | 829.57 | 1,099.56 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 2.62 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 78.13 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 1 | 58.91 | 58.72 | 61.24 | 61.46 | 58.91 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 10 | 585.09 | 559.24 | 854.52 | 886.73 | 823.72 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,245 | 256.82 | 240.6 | 499.79 | 684.29 | 933.18 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 18 | 0.34 | 0.5 | 0.95 | 0.99 | 1.51 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 7 | 224.25 | 201.33 | 420.55 | 442.02 | 421.56 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 36.94 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 304.51 | 300 | 429.5 | 443.81 | 387.74 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 38.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.12 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.06 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 99.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 100 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_rss_avg` | job=backend-node | 13,304,320 |
| `process_rss_avg` | job=monitoring-node | 19,580,928 |
| `process_rss_avg` | job=mysql-exporter | 16,556,544 |
| `process_rss_avg` | job=mysql-node | 21,971,456 |
| `process_rss_avg` | job=prometheus | 150,990,848 |
| `process_rss_avg` | job=redis-exporter | 18,401,280 |
| `process_rss_avg` | job=redis-node | 22,391,296 |
| `process_rss_max` | job=backend-node | 13,824,000 |
| `process_rss_max` | job=monitoring-node | 19,628,032 |
| `process_rss_max` | job=mysql-exporter | 17,010,688 |
| `process_rss_max` | job=mysql-node | 22,188,032 |
| `process_rss_max` | job=prometheus | 156,348,416 |
| `process_rss_max` | job=redis-exporter | 18,685,952 |
| `process_rss_max` | job=redis-node | 22,441,984 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 44.92 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.9 |
| `node_cpu_pct_avg` | job=mysql-node | 99.64 |
| `node_cpu_pct_avg` | job=redis-node | 0.58 |
| `node_load1_avg` | job=backend-node | 1.77 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 25.43 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 26,169.14 |
| `node_major_fault_delta` | job=monitoring-node | 133.71 |
| `node_major_fault_delta` | job=mysql-node | 3.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 60,224,512 |
| `node_mem_available_avg` | job=monitoring-node | 255,860,736 |
| `node_mem_available_avg` | job=mysql-node | 258,834,432 |
| `node_mem_available_avg` | job=redis-node | 562,174,976 |
| `node_swap_free_avg` | job=backend-node | 2,147,705,856 |
| `node_swap_free_avg` | job=monitoring-node | 3,050,039,296 |
| `node_swap_free_avg` | job=mysql-node | 2,672,692,736 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 57,779.43 |
| `node_swap_in_delta` | job=monitoring-node | 42.29 |
| `node_swap_in_delta` | job=mysql-node | 2.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 36,091.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 176,385.82 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 7 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 135,994.97 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,665.1 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 26 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 80 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T09:46:57.791Z ~ 2026-08-11T09:48:57.791Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,289 | 293.13 | 219.26 | 441.23 | 894.25 | 13,457.59 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,301 | 207.6 | 174.15 | 380.39 | 525.65 | 13,190.71 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,832 | 584.23 | 490.64 | 754.49 | 2,884.1 | 13,840.7 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 58.91 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 13 | 730.85 | 727.01 | 1,234.8 | 1,392.29 | 1,221.97 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,250 | 372.96 | 305.07 | 662.03 | 970.1 | 13,146.85 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 14 | 0.3 | 0.5 | 0.95 | 0.99 | 0.96 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 6 | 204.24 | 190.14 | 343.44 | 355.02 | 421.56 |
| method=POST, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 443.17 | 447.39 | 697.93 | 712.25 | 662.04 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 36.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.42 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.06 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.88 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 99 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.28 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.33 |
| `process_rss_avg` | job=backend-node | 13,397,504 |
| `process_rss_avg` | job=monitoring-node | 19,537,920 |
| `process_rss_avg` | job=mysql-exporter | 16,538,624 |
| `process_rss_avg` | job=mysql-node | 22,035,968 |
| `process_rss_avg` | job=prometheus | 150,487,040 |
| `process_rss_avg` | job=redis-exporter | 17,678,336 |
| `process_rss_avg` | job=redis-node | 22,319,104 |
| `process_rss_max` | job=backend-node | 14,032,896 |
| `process_rss_max` | job=monitoring-node | 19,701,760 |
| `process_rss_max` | job=mysql-exporter | 17,285,120 |
| `process_rss_max` | job=mysql-node | 22,233,088 |
| `process_rss_max` | job=prometheus | 158,130,176 |
| `process_rss_max` | job=redis-exporter | 17,989,632 |
| `process_rss_max` | job=redis-node | 22,433,792 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 62.37 |
| `node_cpu_pct_avg` | job=monitoring-node | 2.76 |
| `node_cpu_pct_avg` | job=mysql-node | 83.88 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 1.56 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 24.9 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 94,544 |
| `node_major_fault_delta` | job=monitoring-node | 602.29 |
| `node_major_fault_delta` | job=mysql-node | 36.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 44,066,816 |
| `node_mem_available_avg` | job=monitoring-node | 258,417,152 |
| `node_mem_available_avg` | job=mysql-node | 258,698,240 |
| `node_mem_available_avg` | job=redis-node | 554,488,320 |
| `node_swap_free_avg` | job=backend-node | 2,144,258,560 |
| `node_swap_free_avg` | job=monitoring-node | 3,051,870,720 |
| `node_swap_free_avg` | job=mysql-node | 2,672,701,440 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 143,886.86 |
| `node_swap_in_delta` | job=monitoring-node | 246.86 |
| `node_swap_in_delta` | job=mysql-node | 17.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 76,821.71 |
| `node_swap_out_delta` | job=monitoring-node | 430.86 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 149,790.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 16 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 289,144 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,971.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 22.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 84.57 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-post-g1-pure-throughput-sse250-lowqps-rerun-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T10:23:40.799Z ~ 2026-08-11T10:33:24.237Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T10:24:15.799Z ~ 2026-08-11T10:26:15.799Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,153 | 299.38 | 176.36 | 700.2 | 893.44 | 5,121.15 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 981 | 131.72 | 56.05 | 397.11 | 1,195.43 | 7,917.12 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,475 | 195.45 | 94.8 | 547.91 | 1,490.31 | 8,179.15 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 2,025.72 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 24 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,087 | 362.98 | 203.06 | 930.39 | 2,872.14 | 8,393.93 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 57 | 326.45 | 294.76 | 614.43 | 755.21 | 803.58 |
| method=POST, status=401, uri=UNKNOWN | 98 | 18.16 | 5.75 | 43.06 | 638.88 | 718.84 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 5 | 244.57 | 315.27 | 1,042.08 | 1,067.41 | 1,005.58 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 124.19 | 105.87 | 236.65 | 244.18 | 226.62 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 37.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.86 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 100.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.66 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.87 |
| `process_rss_avg` | job=backend-node | 12,769,792 |
| `process_rss_avg` | job=monitoring-node | 17,934,336 |
| `process_rss_avg` | job=mysql-exporter | 16,435,200 |
| `process_rss_avg` | job=mysql-node | 22,167,040 |
| `process_rss_avg` | job=prometheus | 102,302,720 |
| `process_rss_avg` | job=redis-exporter | 18,446,336 |
| `process_rss_avg` | job=redis-node | 22,736,896 |
| `process_rss_max` | job=backend-node | 16,814,080 |
| `process_rss_max` | job=monitoring-node | 17,960,960 |
| `process_rss_max` | job=mysql-exporter | 16,875,520 |
| `process_rss_max` | job=mysql-node | 22,327,296 |
| `process_rss_max` | job=prometheus | 104,697,856 |
| `process_rss_max` | job=redis-exporter | 18,567,168 |
| `process_rss_max` | job=redis-node | 22,953,984 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 15.38 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 49.63 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 99.43 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.1 |
| `node_cpu_pct_avg` | job=mysql-node | 28.67 |
| `node_cpu_pct_avg` | job=redis-node | 23.68 |
| `node_load1_avg` | job=backend-node | 11.16 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.62 |
| `node_load1_avg` | job=redis-node | 0.39 |
| `node_major_fault_delta` | job=backend-node | 144,294.86 |
| `node_major_fault_delta` | job=monitoring-node | 29.71 |
| `node_major_fault_delta` | job=mysql-node | 25.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 46,138,880 |
| `node_mem_available_avg` | job=monitoring-node | 403,079,168 |
| `node_mem_available_avg` | job=mysql-node | 261,424,128 |
| `node_mem_available_avg` | job=redis-node | 511,718,400 |
| `node_swap_free_avg` | job=backend-node | 2,345,107,968 |
| `node_swap_free_avg` | job=monitoring-node | 3,088,644,096 |
| `node_swap_free_avg` | job=mysql-node | 2,672,726,016 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 267,530.29 |
| `node_swap_in_delta` | job=monitoring-node | 10.29 |
| `node_swap_in_delta` | job=mysql-node | 19.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 137,922.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 116,083.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 4,590.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 38.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,315.43 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T10:26:15.799Z ~ 2026-08-11T10:28:15.799Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,677 | 359.08 | 103.06 | 1,646.9 | 2,783.38 | 12,052.03 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,529 | 120.75 | 39.09 | 314.06 | 1,184.7 | 7,917.12 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,826 | 187.69 | 60.25 | 642.42 | 1,842.78 | 12,052.57 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 2,025.72 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 24 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,462 | 231.59 | 116.16 | 621.88 | 1,206.94 | 12,786.42 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 71 | 251.75 | 210.27 | 533.18 | 1,209.75 | 1,082.98 |
| method=POST, status=401, uri=UNKNOWN | 88 | 49.51 | 4.58 | 36.77 | 1,837.97 | 1,836.45 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 9 | 93.29 | 50.33 | 215.56 | 223.14 | 1,005.58 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 7 | 122.42 | 55.92 | 340.54 | 354.44 | 300.22 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.71 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12.43 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.14 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 43.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.33 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 101.71 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.8 |
| `process_rss_avg` | job=backend-node | 10,973,696 |
| `process_rss_avg` | job=monitoring-node | 18,022,400 |
| `process_rss_avg` | job=mysql-exporter | 16,289,280 |
| `process_rss_avg` | job=mysql-node | 22,223,872 |
| `process_rss_avg` | job=prometheus | 98,932,736 |
| `process_rss_avg` | job=redis-exporter | 18,542,592 |
| `process_rss_avg` | job=redis-node | 22,780,928 |
| `process_rss_max` | job=backend-node | 15,376,384 |
| `process_rss_max` | job=monitoring-node | 18,169,856 |
| `process_rss_max` | job=mysql-exporter | 16,924,672 |
| `process_rss_max` | job=mysql-node | 22,392,832 |
| `process_rss_max` | job=prometheus | 101,548,032 |
| `process_rss_max` | job=redis-exporter | 18,542,592 |
| `process_rss_max` | job=redis-node | 22,933,504 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 25.43 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.14 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 99.14 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.1 |
| `node_cpu_pct_avg` | job=mysql-node | 35.02 |
| `node_cpu_pct_avg` | job=redis-node | 0.56 |
| `node_load1_avg` | job=backend-node | 16.57 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 0.95 |
| `node_load1_avg` | job=redis-node | 0.15 |
| `node_major_fault_delta` | job=backend-node | 123,517.71 |
| `node_major_fault_delta` | job=monitoring-node | 14.86 |
| `node_major_fault_delta` | job=mysql-node | 69.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 52,897,792 |
| `node_mem_available_avg` | job=monitoring-node | 406,067,712 |
| `node_mem_available_avg` | job=mysql-node | 263,483,904 |
| `node_mem_available_avg` | job=redis-node | 560,596,992 |
| `node_swap_free_avg` | job=backend-node | 2,231,754,240 |
| `node_swap_free_avg` | job=monitoring-node | 3,088,666,624 |
| `node_swap_free_avg` | job=mysql-node | 2,672,729,088 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 229,203.43 |
| `node_swap_in_delta` | job=monitoring-node | 8 |
| `node_swap_in_delta` | job=mysql-node | 99.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 115,345.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 172,956.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,360 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 66.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,987.43 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T10:28:15.799Z ~ 2026-08-11T10:30:15.799Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,824 | 354.01 | 243.4 | 534.01 | 3,682.93 | 12,052.03 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,830 | 192.19 | 121.71 | 354.14 | 1,542.61 | 8,669.51 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,479 | 245.87 | 159.91 | 423.1 | 2,179.87 | 12,052.57 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,312 | 388.45 | 318.28 | 554.84 | 973.97 | 12,786.42 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 574 | 208.48 | 199.67 | 381.91 | 536.5 | 1,746.65 |
| method=POST, status=401, uri=UNKNOWN | 73 | 11.53 | 7.55 | 38.59 | 52.34 | 1,836.45 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 13 | 933.71 | 167.77 | 9,234.18 | 9,864.11 | 8,670.05 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 167.16 | 97.37 | 296.05 | 299.21 | 464.21 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 1 | 495.62 | 475.86 | 7,649.58 | 8,401.86 | 9,546.76 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 26.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 77.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.12 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 103.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.69 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.87 |
| `process_rss_avg` | job=backend-node | 13,011,456 |
| `process_rss_avg` | job=monitoring-node | 17,965,056 |
| `process_rss_avg` | job=mysql-exporter | 16,651,776 |
| `process_rss_avg` | job=mysql-node | 22,155,776 |
| `process_rss_avg` | job=prometheus | 98,082,816 |
| `process_rss_avg` | job=redis-exporter | 18,444,288 |
| `process_rss_avg` | job=redis-node | 22,581,248 |
| `process_rss_max` | job=backend-node | 13,611,008 |
| `process_rss_max` | job=monitoring-node | 18,128,896 |
| `process_rss_max` | job=mysql-exporter | 17,022,976 |
| `process_rss_max` | job=mysql-node | 22,351,872 |
| `process_rss_max` | job=prometheus | 98,082,816 |
| `process_rss_max` | job=redis-exporter | 18,542,592 |
| `process_rss_max` | job=redis-node | 22,872,064 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 39 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 99.06 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.1 |
| `node_cpu_pct_avg` | job=mysql-node | 51.47 |
| `node_cpu_pct_avg` | job=redis-node | 0.63 |
| `node_load1_avg` | job=backend-node | 25.6 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.73 |
| `node_load1_avg` | job=redis-node | 0.02 |
| `node_major_fault_delta` | job=backend-node | 111,446.86 |
| `node_major_fault_delta` | job=monitoring-node | 10.29 |
| `node_major_fault_delta` | job=mysql-node | 184 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,655,680 |
| `node_mem_available_avg` | job=monitoring-node | 407,361,536 |
| `node_mem_available_avg` | job=mysql-node | 262,668,800 |
| `node_mem_available_avg` | job=redis-node | 560,605,184 |
| `node_swap_free_avg` | job=backend-node | 2,194,146,304 |
| `node_swap_free_avg` | job=monitoring-node | 3,088,666,624 |
| `node_swap_free_avg` | job=mysql-node | 2,672,737,792 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 199,971.43 |
| `node_swap_in_delta` | job=monitoring-node | 9.14 |
| `node_swap_in_delta` | job=mysql-node | 252.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 89,618.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 258,870.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 16,946.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 219.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 4,401.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T10:30:15.799Z ~ 2026-08-11T10:32:15.799Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,992 | 412.43 | 305.04 | 659.85 | 3,199.75 | 14,445.23 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,087 | 242.48 | 169.13 | 423.97 | 999.84 | 13,863.05 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,157 | 288.27 | 216.07 | 492.15 | 1,048.74 | 14,276.96 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,670 | 505.03 | 401.73 | 801.22 | 2,313.32 | 14,362.55 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,031 | 305.32 | 195.66 | 468.08 | 1,358.64 | 13,963.2 |
| method=POST, status=401, uri=UNKNOWN | 29 | 32.68 | 15.61 | 54.53 | 425.02 | 1,836.45 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 19 | 214.75 | 160.32 | 1,127.43 | 1,370.81 | 8,670.05 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 7 | 243.37 | 257.25 | 349.23 | 356.18 | 464.21 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 1 | 1,679.48 | 1,610.61 | 1,771.67 | 1,785.99 | 9,546.76 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.71 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.29 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.86 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 78.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.99 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107.86 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 113 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.68 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.85 |
| `process_rss_avg` | job=backend-node | 12,538,880 |
| `process_rss_avg` | job=monitoring-node | 18,128,896 |
| `process_rss_avg` | job=mysql-exporter | 16,232,448 |
| `process_rss_avg` | job=mysql-node | 22,186,496 |
| `process_rss_avg` | job=prometheus | 98,082,816 |
| `process_rss_avg` | job=redis-exporter | 18,340,864 |
| `process_rss_avg` | job=redis-node | 22,609,920 |
| `process_rss_max` | job=backend-node | 12,996,608 |
| `process_rss_max` | job=monitoring-node | 18,128,896 |
| `process_rss_max` | job=mysql-exporter | 16,781,312 |
| `process_rss_max` | job=mysql-node | 22,462,464 |
| `process_rss_max` | job=prometheus | 98,082,816 |
| `process_rss_max` | job=redis-exporter | 18,411,520 |
| `process_rss_max` | job=redis-node | 22,609,920 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 247.14 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 134.3 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.14 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.89 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 47.27 |
| `node_cpu_pct_avg` | job=redis-node | 0.59 |
| `node_load1_avg` | job=backend-node | 32.6 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 2.12 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 117,421.71 |
| `node_major_fault_delta` | job=monitoring-node | 13.71 |
| `node_major_fault_delta` | job=mysql-node | 110.86 |
| `node_major_fault_delta` | job=redis-node | 2.29 |
| `node_mem_available_avg` | job=backend-node | 64,286,208 |
| `node_mem_available_avg` | job=monitoring-node | 409,453,056 |
| `node_mem_available_avg` | job=mysql-node | 262,201,344 |
| `node_mem_available_avg` | job=redis-node | 559,732,736 |
| `node_swap_free_avg` | job=backend-node | 2,172,114,432 |
| `node_swap_free_avg` | job=monitoring-node | 3,088,666,624 |
| `node_swap_free_avg` | job=mysql-node | 2,672,738,304 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 222,057.14 |
| `node_swap_in_delta` | job=monitoring-node | 11.43 |
| `node_swap_in_delta` | job=mysql-node | 134.86 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 102,868.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 239,198.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 11,712 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 139.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 3,105.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-post-g1-pure-throughput-sse250-lowqps-postreboot-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T10:50:06.144Z ~ 2026-08-11T10:59:49.624Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T10:50:41.144Z ~ 2026-08-11T10:52:41.144Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,221 | 261.02 | 134.81 | 780.16 | 1,518.34 | 6,026.47 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,117 | 108.78 | 39.33 | 352.49 | 834.86 | 4,524.27 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,354 | 135.66 | 63.39 | 433.74 | 1,120.6 | 5,887.3 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 2,125.3 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 28.36 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,095 | 267.13 | 131.86 | 839.72 | 1,431.66 | 6,010.06 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 21 | 366.88 | 383.08 | 601.97 | 2,074.26 | 2,899.06 |
| method=POST, status=401, uri=UNKNOWN | 109 | 9.24 | 4.54 | 34.95 | 95 | 109.16 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 50.18 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 45.44 | 40.11 | 569.96 | 615.07 | 560.41 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 5,160.11 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 48 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.09 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 106 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.65 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.83 |
| `process_rss_avg` | job=backend-node | 11,314,688 |
| `process_rss_avg` | job=monitoring-node | 18,481,152 |
| `process_rss_avg` | job=mysql-exporter | 16,398,848 |
| `process_rss_avg` | job=mysql-node | 22,186,496 |
| `process_rss_avg` | job=prometheus | 99,278,848 |
| `process_rss_avg` | job=redis-exporter | 18,369,024 |
| `process_rss_avg` | job=redis-node | 22,646,784 |
| `process_rss_max` | job=backend-node | 15,785,984 |
| `process_rss_max` | job=monitoring-node | 18,481,152 |
| `process_rss_max` | job=mysql-exporter | 16,740,352 |
| `process_rss_max` | job=mysql-node | 22,392,832 |
| `process_rss_max` | job=prometheus | 99,426,304 |
| `process_rss_max` | job=redis-exporter | 18,391,040 |
| `process_rss_max` | job=redis-node | 22,646,784 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 5.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 14 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 46.38 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.3 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 33.1 |
| `node_cpu_pct_avg` | job=redis-node | 0.57 |
| `node_load1_avg` | job=backend-node | 7.48 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 0.55 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 136,725.71 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 76.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 71,809,536 |
| `node_mem_available_avg` | job=monitoring-node | 399,781,376 |
| `node_mem_available_avg` | job=mysql-node | 266,155,520 |
| `node_mem_available_avg` | job=redis-node | 559,462,400 |
| `node_swap_free_avg` | job=backend-node | 2,499,734,528 |
| `node_swap_free_avg` | job=monitoring-node | 3,088,744,448 |
| `node_swap_free_avg` | job=mysql-node | 2,673,160,192 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 260,971.43 |
| `node_swap_in_delta` | job=monitoring-node | 3.43 |
| `node_swap_in_delta` | job=mysql-node | 4.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 165,268.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 154,126.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 10,029.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 48 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 2,333.71 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T10:52:41.144Z ~ 2026-08-11T10:54:41.144Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,721 | 153.51 | 55.49 | 446.58 | 2,429.52 | 6,516.95 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,679 | 64.88 | 19.44 | 270.66 | 417.44 | 4,524.27 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,117 | 92.79 | 32.62 | 300.74 | 575.87 | 6,502.6 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 2,125.3 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 28.36 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,617 | 205.77 | 72.18 | 507.99 | 3,212.28 | 7,429.9 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 69 | 413.22 | 219.97 | 2,505.4 | 3,149.64 | 3,089.4 |
| method=POST, status=401, uri=UNKNOWN | 99 | 10.97 | 1.57 | 74.38 | 181.87 | 186.9 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 8 | 83.79 | 31.26 | 288.95 | 297.79 | 270.46 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 16 | 218.05 | 50.33 | 1,181.12 | 1,381.55 | 1,380.42 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 3 | 2,787.77 | 2,780.66 | 3,193.48 | 5,149.34 | 10,582.98 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 41.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.79 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.55 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.76 |
| `process_rss_avg` | job=backend-node | 11,075,584 |
| `process_rss_avg` | job=monitoring-node | 18,481,152 |
| `process_rss_avg` | job=mysql-exporter | 16,418,304 |
| `process_rss_avg` | job=mysql-node | 22,202,368 |
| `process_rss_avg` | job=prometheus | 99,143,680 |
| `process_rss_avg` | job=redis-exporter | 17,876,992 |
| `process_rss_avg` | job=redis-node | 22,646,784 |
| `process_rss_max` | job=backend-node | 14,712,832 |
| `process_rss_max` | job=monitoring-node | 18,481,152 |
| `process_rss_max` | job=mysql-exporter | 16,969,728 |
| `process_rss_max` | job=mysql-node | 22,310,912 |
| `process_rss_max` | job=prometheus | 99,143,680 |
| `process_rss_max` | job=redis-exporter | 18,214,912 |
| `process_rss_max` | job=redis-node | 22,646,784 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 85.52 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 34.85 |
| `node_cpu_pct_avg` | job=redis-node | 0.85 |
| `node_load1_avg` | job=backend-node | 7.54 |
| `node_load1_avg` | job=monitoring-node | 0.08 |
| `node_load1_avg` | job=mysql-node | 0.86 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 112,210.29 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 101.71 |
| `node_major_fault_delta` | job=redis-node | 48 |
| `node_mem_available_avg` | job=backend-node | 57,129,984 |
| `node_mem_available_avg` | job=monitoring-node | 404,564,992 |
| `node_mem_available_avg` | job=mysql-node | 270,566,912 |
| `node_mem_available_avg` | job=redis-node | 550,248,448 |
| `node_swap_free_avg` | job=backend-node | 2,399,611,904 |
| `node_swap_free_avg` | job=monitoring-node | 3,088,756,736 |
| `node_swap_free_avg` | job=mysql-node | 2,673,162,752 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 188,180.57 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 179.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 90,742.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 163,099.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 13,331.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 76.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 3,261.71 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T10:54:41.144Z ~ 2026-08-11T10:56:41.144Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,977 | 199.75 | 95.18 | 485.03 | 2,121.67 | 13,014.63 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,813 | 80.75 | 38.74 | 243.62 | 371.73 | 11,854.68 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,671 | 118.46 | 58.84 | 315.57 | 627.32 | 11,849.4 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,552 | 178.67 | 122.9 | 409.46 | 521.75 | 14,723.58 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 350 | 214.7 | 173.99 | 351.25 | 472.12 | 11,845.14 |
| method=POST, status=401, uri=UNKNOWN | 94 | 67.55 | 2.62 | 20.32 | 2,716.57 | 2,589.92 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 14 | 95.37 | 89.48 | 216.99 | 222.35 | 270.46 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 15 | 63.07 | 43.34 | 231.53 | 243.16 | 1,380.42 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 1 | 14,849.7 | 15,032.39 | 15,676.63 | 15,733.9 | 14,849.7 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.38 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 60.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 101.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.83 |
| `process_rss_avg` | job=backend-node | 12,446,720 |
| `process_rss_avg` | job=monitoring-node | 18,530,304 |
| `process_rss_avg` | job=mysql-exporter | 16,584,704 |
| `process_rss_avg` | job=mysql-node | 22,211,584 |
| `process_rss_avg` | job=prometheus | 99,143,680 |
| `process_rss_avg` | job=redis-exporter | 18,496,512 |
| `process_rss_avg` | job=redis-node | 22,646,784 |
| `process_rss_max` | job=backend-node | 15,396,864 |
| `process_rss_max` | job=monitoring-node | 18,612,224 |
| `process_rss_max` | job=mysql-exporter | 16,781,312 |
| `process_rss_max` | job=mysql-node | 22,446,080 |
| `process_rss_max` | job=prometheus | 99,143,680 |
| `process_rss_max` | job=redis-exporter | 18,829,312 |
| `process_rss_max` | job=redis-node | 22,646,784 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 24.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.83 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.1 |
| `node_cpu_pct_avg` | job=mysql-node | 55.28 |
| `node_cpu_pct_avg` | job=redis-node | 0.67 |
| `node_load1_avg` | job=backend-node | 15.13 |
| `node_load1_avg` | job=monitoring-node | 0.18 |
| `node_load1_avg` | job=mysql-node | 1.9 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 78,165.71 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 161.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 50,437,632 |
| `node_mem_available_avg` | job=monitoring-node | 404,034,560 |
| `node_mem_available_avg` | job=mysql-node | 267,919,872 |
| `node_mem_available_avg` | job=redis-node | 554,852,352 |
| `node_swap_free_avg` | job=backend-node | 2,344,752,640 |
| `node_swap_free_avg` | job=monitoring-node | 3,088,756,736 |
| `node_swap_free_avg` | job=mysql-node | 2,673,164,288 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 138,648 |
| `node_swap_in_delta` | job=monitoring-node | 3.43 |
| `node_swap_in_delta` | job=mysql-node | 269.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 76,892.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 278,024 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,435.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 152 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 4,777.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T10:56:41.144Z ~ 2026-08-11T10:58:41.144Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,153 | 481.16 | 106.86 | 2,705.83 | 5,676.52 | 24,708.11 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,321 | 212.1 | 45.86 | 502.77 | 3,031.17 | 20,193.41 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,744 | 256.89 | 69.73 | 599.36 | 3,145.2 | 20,420.87 |
| method=GET, status=404, uri=/** | 3 | 281.66 | 373.68 | 1,670.11 | 1,765.68 | 1,472.92 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,770 | 232.72 | 138.64 | 267.81 | 2,224.69 | 14,723.58 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 53 | 371.49 | 89.48 | 2,103.24 | 2,698.67 | 11,845.14 |
| method=POST, status=401, uri=UNKNOWN | 61 | 9.97 | 2.62 | 91.32 | 105.57 | 2,589.92 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 8 | 44.68 | 41.94 | 66.13 | 66.91 | 270.46 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 7 | 58.41 | 64.31 | 86.12 | 88.81 | 506.11 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 7 | 3,261.8 | 2,863.31 | 4,241.28 | 4,284.23 | 14,849.7 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.83 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 52.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 7.4 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.32 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.64 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.8 |
| `process_rss_avg` | job=backend-node | 13,288,960 |
| `process_rss_avg` | job=monitoring-node | 18,561,024 |
| `process_rss_avg` | job=mysql-exporter | 16,108,544 |
| `process_rss_avg` | job=mysql-node | 22,220,800 |
| `process_rss_avg` | job=prometheus | 99,143,680 |
| `process_rss_avg` | job=redis-exporter | 18,445,312 |
| `process_rss_avg` | job=redis-node | 22,646,784 |
| `process_rss_max` | job=backend-node | 16,240,640 |
| `process_rss_max` | job=monitoring-node | 18,743,296 |
| `process_rss_max` | job=mysql-exporter | 16,621,568 |
| `process_rss_max` | job=mysql-node | 22,450,176 |
| `process_rss_max` | job=prometheus | 99,143,680 |
| `process_rss_max` | job=redis-exporter | 18,546,688 |
| `process_rss_max` | job=redis-node | 22,646,784 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 27.33 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.33 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.92 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.1 |
| `node_cpu_pct_avg` | job=mysql-node | 35.74 |
| `node_cpu_pct_avg` | job=redis-node | 0.53 |
| `node_load1_avg` | job=backend-node | 24.5 |
| `node_load1_avg` | job=monitoring-node | 0.05 |
| `node_load1_avg` | job=mysql-node | 3.23 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 111,453.71 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 60.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 54,756,864 |
| `node_mem_available_avg` | job=monitoring-node | 403,345,920 |
| `node_mem_available_avg` | job=mysql-node | 269,491,200 |
| `node_mem_available_avg` | job=redis-node | 554,856,448 |
| `node_swap_free_avg` | job=backend-node | 2,278,341,120 |
| `node_swap_free_avg` | job=monitoring-node | 3,088,756,736 |
| `node_swap_free_avg` | job=mysql-node | 2,673,164,288 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 198,208 |
| `node_swap_in_delta` | job=monitoring-node | 4.57 |
| `node_swap_in_delta` | job=mysql-node | 85.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 130,745.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 192,950.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 9,241.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 83.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 3,721.14 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-serial-noheap-pure-throughput-sse250-lowqps-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T11:18:30.901Z ~ 2026-08-11T11:28:11.820Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T11:19:05.901Z ~ 2026-08-11T11:21:05.901Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,247 | 110.04 | 14.2 | 672.05 | 955.78 | 6,218.82 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,154 | 100.43 | 6.86 | 103.7 | 6,072.61 | 7,195.73 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,401 | 128.87 | 11.69 | 524.27 | 5,955.12 | 6,262.22 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 119,798.78 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 119,780.55 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0.97 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 85.07 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 28.27 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 45.92 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 45.97 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 87.44 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 43.5 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 61 | 215.83 | 30.44 | 747.15 | 6,399.5 | 6,373.18 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,125 | 105.47 | 8.37 | 478.96 | 1,131.01 | 6,315.37 |
| method=POST, status=401, uri=UNKNOWN | 75 | 2.38 | 0.65 | 13.56 | 35.46 | 37.41 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 5 | 28.48 | 7.22 | 97.9 | 99.58 | 94.69 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 10 | 31.64 | 10.87 | 84.45 | 88.47 | 87.37 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 35.43 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 50.29 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 21.45 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 0.77 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.27 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.5 |
| `process_rss_avg` | job=backend-node | 13,166,080 |
| `process_rss_avg` | job=monitoring-node | 18,486,272 |
| `process_rss_avg` | job=mysql-exporter | 16,560,128 |
| `process_rss_avg` | job=mysql-node | 22,133,760 |
| `process_rss_avg` | job=prometheus | 112,175,104 |
| `process_rss_avg` | job=redis-exporter | 18,195,968 |
| `process_rss_avg` | job=redis-node | 22,704,128 |
| `process_rss_max` | job=backend-node | 13,602,816 |
| `process_rss_max` | job=monitoring-node | 18,587,648 |
| `process_rss_max` | job=mysql-exporter | 17,145,856 |
| `process_rss_max` | job=mysql-node | 22,228,992 |
| `process_rss_max` | job=prometheus | 113,152,000 |
| `process_rss_max` | job=redis-exporter | 18,399,232 |
| `process_rss_max` | job=redis-node | 22,704,128 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 43.43 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 17.62 |
| `node_cpu_pct_avg` | job=redis-node | 0.4 |
| `node_load1_avg` | job=backend-node | 2.1 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.45 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 42,374.86 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 11.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 64,059,904 |
| `node_mem_available_avg` | job=monitoring-node | 407,730,688 |
| `node_mem_available_avg` | job=mysql-node | 264,717,312 |
| `node_mem_available_avg` | job=redis-node | 550,394,368 |
| `node_swap_free_avg` | job=backend-node | 2,653,485,056 |
| `node_swap_free_avg` | job=monitoring-node | 3,092,862,976 |
| `node_swap_free_avg` | job=mysql-node | 2,673,102,848 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 62,162.29 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 0 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 51,925.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 124,948.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 30.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 2.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 198.86 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T11:21:05.901Z ~ 2026-08-11T11:23:05.901Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,728 | 63 | 12.32 | 546.35 | 686.87 | 6,218.82 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,826 | 12.52 | 6.07 | 20.6 | 123.26 | 7,195.73 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,554 | 32.03 | 10.24 | 101.05 | 595.5 | 6,262.22 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 85.07 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1 | 29.79 | 30.76 | 33.27 | 33.5 | 6,373.18 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,765 | 28.13 | 7.08 | 94.48 | 569.48 | 6,315.37 |
| method=POST, status=401, uri=UNKNOWN | 78 | 0.4 | 0.51 | 0.96 | 3.61 | 37.41 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 5 | 93.08 | 6.29 | 346.33 | 355.6 | 355.16 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 6.91 | 7.22 | 8.27 | 8.37 | 87.37 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 9.14 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 65.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 7.24 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 0.52 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 101.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.22 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.42 |
| `process_rss_avg` | job=backend-node | 13,680,128 |
| `process_rss_avg` | job=monitoring-node | 18,477,056 |
| `process_rss_avg` | job=mysql-exporter | 16,631,808 |
| `process_rss_avg` | job=mysql-node | 22,252,032 |
| `process_rss_avg` | job=prometheus | 110,731,264 |
| `process_rss_avg` | job=redis-exporter | 17,198,080 |
| `process_rss_avg` | job=redis-node | 22,681,600 |
| `process_rss_max` | job=backend-node | 14,217,216 |
| `process_rss_max` | job=monitoring-node | 18,575,360 |
| `process_rss_max` | job=mysql-exporter | 16,961,536 |
| `process_rss_max` | job=mysql-node | 22,401,024 |
| `process_rss_max` | job=prometheus | 111,038,464 |
| `process_rss_max` | job=redis-exporter | 17,428,480 |
| `process_rss_max` | job=redis-node | 22,835,200 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 30.56 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.19 |
| `node_cpu_pct_avg` | job=mysql-node | 21.48 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 1.11 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.54 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 12,222.86 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 53.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,749,888 |
| `node_mem_available_avg` | job=monitoring-node | 408,785,920 |
| `node_mem_available_avg` | job=mysql-node | 259,325,952 |
| `node_mem_available_avg` | job=redis-node | 554,868,736 |
| `node_swap_free_avg` | job=backend-node | 2,607,890,432 |
| `node_swap_free_avg` | job=monitoring-node | 3,092,995,072 |
| `node_swap_free_avg` | job=mysql-node | 2,673,129,472 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 19,800 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 0 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 3,157.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 155,636.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 54.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 2.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 68.57 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T11:23:05.901Z ~ 2026-08-11T11:25:05.901Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,973 | 14.59 | 11.78 | 18.37 | 82.76 | 1,158.73 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,982 | 6.76 | 5.59 | 9.11 | 41.09 | 641.34 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,953 | 11.6 | 9.56 | 14.85 | 63.08 | 1,075.09 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 32.06 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,944 | 8.13 | 6.58 | 10.67 | 49.05 | 679.28 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.43 | 0.52 | 0.99 | 3.25 | 18.41 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 17 | 6.16 | 5.15 | 14.33 | 15.17 | 355.16 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 16 | 7.15 | 6.41 | 18.45 | 21.59 | 19.52 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 5.71 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 98.29 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 1.96 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 0.65 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 100.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.5 |
| `process_rss_avg` | job=backend-node | 13,517,824 |
| `process_rss_avg` | job=monitoring-node | 18,575,360 |
| `process_rss_avg` | job=mysql-exporter | 16,425,472 |
| `process_rss_avg` | job=mysql-node | 22,384,128 |
| `process_rss_avg` | job=prometheus | 111,038,464 |
| `process_rss_avg` | job=redis-exporter | 17,666,048 |
| `process_rss_avg` | job=redis-node | 22,554,624 |
| `process_rss_max` | job=backend-node | 13,922,304 |
| `process_rss_max` | job=monitoring-node | 18,575,360 |
| `process_rss_max` | job=mysql-exporter | 16,736,256 |
| `process_rss_max` | job=mysql-node | 22,831,104 |
| `process_rss_max` | job=prometheus | 111,038,464 |
| `process_rss_max` | job=redis-exporter | 17,960,960 |
| `process_rss_max` | job=redis-node | 22,802,432 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 1.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 38.01 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.71 |
| `node_cpu_pct_avg` | job=mysql-node | 28.87 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 1.11 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.46 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 4,683.43 |
| `node_major_fault_delta` | job=monitoring-node | 19.43 |
| `node_major_fault_delta` | job=mysql-node | 1.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 52,573,184 |
| `node_mem_available_avg` | job=monitoring-node | 411,420,160 |
| `node_mem_available_avg` | job=mysql-node | 254,533,120 |
| `node_mem_available_avg` | job=redis-node | 556,275,712 |
| `node_swap_free_avg` | job=backend-node | 2,604,853,248 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,012,480 |
| `node_swap_free_avg` | job=mysql-node | 2,673,131,520 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 7,965.71 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 1.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 3,228.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 219,162.29 |
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
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T11:25:05.901Z ~ 2026-08-11T11:27:05.901Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,169 | 86.36 | 11.92 | 546.56 | 661.98 | 1,143.55 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,075 | 40.61 | 5.88 | 173.68 | 515.04 | 4,064.74 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,241 | 61.18 | 9.9 | 465.81 | 590.74 | 4,069.62 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 202.51 |
| method=GET, status=200, uri=/api/auctions/stream | 2 | 40,654.02 | 30,000 | 30,000 | 30,000 | 44,746.48 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 1 | 0.55 | 0.54 | 29.82 | 32.81 | 0.55 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 1.26 |
| method=OPTIONS, status=200, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0.87 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 7.82 |
| method=OPTIONS, status=200, uri=/api/notifications/unread-count | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/wallet | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 4,146 | 50.22 | 6.71 | 402.83 | 572.51 | 1,093.34 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 194.78 |
| method=POST, status=401, uri=UNKNOWN | 73 | 0.92 | 0.57 | 6.15 | 8.89 | 9.34 |
| method=POST, status=403, uri=/api/auctions/{auctionId}/bids | 15 | 60.72 | 5.94 | 465.81 | 493.16 | 488.39 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 16 | 47.03 | 6.79 | 463.17 | 492.63 | 470.47 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 16 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 30.86 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 99.43 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 14.56 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=CodeCache GC Threshold, exported_application=dbidding, gc=MarkSweepCompact, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=Allocation Failure, exported_application=dbidding, gc=Copy, job=backend-spring | 0.73 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 100.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.38 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.67 |
| `process_rss_avg` | job=backend-node | 13,999,104 |
| `process_rss_avg` | job=monitoring-node | 18,608,128 |
| `process_rss_avg` | job=mysql-exporter | 16,633,344 |
| `process_rss_avg` | job=mysql-node | 22,256,640 |
| `process_rss_avg` | job=prometheus | 118,231,040 |
| `process_rss_avg` | job=redis-exporter | 18,436,096 |
| `process_rss_avg` | job=redis-node | 22,364,160 |
| `process_rss_max` | job=backend-node | 15,192,064 |
| `process_rss_max` | job=monitoring-node | 18,706,432 |
| `process_rss_max` | job=mysql-exporter | 17,027,072 |
| `process_rss_max` | job=mysql-node | 22,548,480 |
| `process_rss_max` | job=prometheus | 125,194,240 |
| `process_rss_max` | job=redis-exporter | 18,616,320 |
| `process_rss_max` | job=redis-node | 22,364,160 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 253 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 49.52 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.3 |
| `node_cpu_pct_avg` | job=mysql-node | 33.39 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 1.25 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 1.11 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 14,547.43 |
| `node_major_fault_delta` | job=monitoring-node | 12.57 |
| `node_major_fault_delta` | job=mysql-node | 1.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 55,859,200 |
| `node_mem_available_avg` | job=monitoring-node | 405,336,576 |
| `node_mem_available_avg` | job=mysql-node | 254,674,432 |
| `node_mem_available_avg` | job=redis-node | 566,361,088 |
| `node_swap_free_avg` | job=backend-node | 2,603,170,304 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,012,480 |
| `node_swap_free_avg` | job=mysql-node | 2,673,131,520 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 21,273.14 |
| `node_swap_in_delta` | job=monitoring-node | 9.14 |
| `node_swap_in_delta` | job=mysql-node | 0 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 5,692.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 233,404.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 61.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 4.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 3 |
| `redis_up_avg` | job=redis-exporter | 1 |

> 이 문서는 codex의 도움을 받아 작성하였습니다
