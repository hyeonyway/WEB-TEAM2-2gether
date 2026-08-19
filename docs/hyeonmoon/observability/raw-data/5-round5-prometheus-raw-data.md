# 5차 부하테스트 — Prometheus 원시 집계 데이터

이 문서는 K6 결과 JSON의 실제 종료 시각과 실행 시간을 기준으로 stage를 재구성하고, 각 구간 끝 시점에 Prometheus range/vector query를 평가해 만든 원시 집계표다. p50/p95/p99는 서버의 `http_server_requests_seconds_bucket` histogram으로 계산했다. 값은 Prometheus 원 단위(시간은 ms 변환)를 유지하며, `N/A`/빈 표는 그 시간대에 해당 시계열이 없었음을 뜻한다.

수집 범위는 테스트 대상 백엔드, backend/mysql/redis node exporter, MySQL exporter, Redis exporter다. Grafana/Prometheus 자기 관측 메트릭과 정적 build/info/config 시계열은 성능 측정값이 아니므로 제외했다.

## 실행 목록

| 결과 파일 | 시나리오 | 실제 실행 (UTC) | K6 전체 | 평균 지연 | med | p95 | p99 | max |
|---|---|---|---:|---:|---:|---:|---:|---:|
| [`round5-pure-throughput-sse250-fullramp-20260812.json`](../../../../backend/src/test/k6/result/round5-pure-throughput-sse250-fullramp-20260812.json) | pure-throughput | 2026-08-12T12:33:15.897Z ~ 2026-08-12T12:46:57.846Z | 113,140 | 137.65 req/s | 5,801.49 | 305.29 | 24,328.73 | 35,870.92 | 60,004.78 |
| [`round5-pure-throughput-sse500-fullramp-20260812.json`](../../../../backend/src/test/k6/result/round5-pure-throughput-sse500-fullramp-20260812.json) | pure-throughput | 2026-08-12T12:47:49.721Z ~ 2026-08-12T13:01:33.072Z | 92,579 | 112.44 req/s | 9,647.41 | 5,335.77 | 41,473.95 | 53,021.41 | 60,015.25 |
| [`round5-pure-throughput-sse1000-fullramp-20260812.json`](../../../../backend/src/test/k6/result/round5-pure-throughput-sse1000-fullramp-20260812.json) | pure-throughput | 2026-08-12T13:02:28.389Z ~ 2026-08-12T13:16:25.966Z | 108,318 | 129.32 req/s | 9,950.57 | 10,008.84 | 15,005.4 | 60,000.77 | 60,025.77 |
| [`round5-pure-throughput-sse1000-rerun-20260812.json`](../../../../backend/src/test/k6/result/round5-pure-throughput-sse1000-rerun-20260812.json) | pure-throughput | 2026-08-12T13:48:06.318Z ~ 2026-08-12T14:01:52.160Z | 80,640 | 97.65 req/s | 18,411.01 | 15,721.13 | 60,000.62 | 60,002.32 | 60,033.21 |
| [`round5-pure-throughput-sse1000-rerun2-20260812.json`](../../../../backend/src/test/k6/result/round5-pure-throughput-sse1000-rerun2-20260812.json) | pure-throughput | 2026-08-12T14:25:36.939Z ~ 2026-08-12T14:39:19.328Z | 121,005 | 147.14 req/s | 12,634.91 | 10,008.92 | 50,182.49 | 60,000.35 | 83,178.09 |
| [`round5-hot-auction-pattern-sse250-20260813.json`](../../../../backend/src/test/k6/result/round5-hot-auction-pattern-sse250-20260813.json) | hot-auction-pattern | 2026-08-12T15:58:08.028Z ~ 2026-08-12T16:06:10.211Z | 64,427 | 133.62 req/s | 5,703.92 | 1,842.68 | 21,481.55 | 42,643.89 | 86,515.83 |
| [`round5-bid-only-load-noSSE-20260813.json`](../../../../backend/src/test/k6/result/round5-bid-only-load-noSSE-20260813.json) | bid-only-load (SSE 없음) | 2026-08-12T16:06:55.712Z ~ 2026-08-12T16:19:13.640Z | 134,560 | 182.35 req/s | 2,203.32 | 45.39 | 7,707.87 | 28,800.45 | 30,802.24 |
| [`round5-bid-only-load-singleHotAuction-20260813.json`](../../../../backend/src/test/k6/result/round5-bid-only-load-singleHotAuction-20260813.json) | bid-only-load (SSE 없음) | 2026-08-12T16:19:46.444Z ~ 2026-08-12T16:31:59.828Z | 50,708 | 69.14 req/s | 26,635.55 | 32,949.31 | 59,998.78 | 60,000.45 | 60,014.84 |

---

## round5-pure-throughput-sse250-fullramp-20260812.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-12T12:33:15.897Z ~ 2026-08-12T12:46:57.846Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-12T12:33:50.897Z ~ 2026-08-12T12:35:50.897Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,187 | 69.15 | 17.91 | 508.69 | 787.54 | 1,747.28 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 920 | 7.98 | 7.27 | 13.7 | 19.57 | 667.51 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,418 | 37.96 | 12.55 | 293.68 | 655.28 | 1,428.03 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 290.09 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 59.02 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,102 | 61.06 | 22.9 | 590.93 | 1,893.66 | 2,165.34 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2 | 450.21 | 178.96 | 885.84 | 893 | 2,102.76 |
| method=POST, status=401, uri=UNKNOWN | 78 | 1.91 | 0.68 | 3.01 | 46.53 | 46.09 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 17.71 | 13.98 | 33 | 33.44 | 29.2 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 26.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 18.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.99 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.4 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.76 |
| `process_rss_avg` | job=backend-node | 13,997,056 |
| `process_rss_avg` | job=monitoring-node | 17,641,472 |
| `process_rss_avg` | job=mysql-exporter | 16,401,920 |
| `process_rss_avg` | job=mysql-node | 22,554,624 |
| `process_rss_avg` | job=prometheus | 98,963,456 |
| `process_rss_avg` | job=redis-exporter | 17,637,888 |
| `process_rss_avg` | job=redis-node | 22,876,160 |
| `process_rss_max` | job=backend-node | 17,035,264 |
| `process_rss_max` | job=monitoring-node | 17,641,472 |
| `process_rss_max` | job=mysql-exporter | 16,818,176 |
| `process_rss_max` | job=mysql-node | 22,691,840 |
| `process_rss_max` | job=prometheus | 99,012,608 |
| `process_rss_max` | job=redis-exporter | 18,227,200 |
| `process_rss_max` | job=redis-node | 23,093,248 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 9 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 58.04 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.19 |
| `node_cpu_pct_avg` | job=mysql-node | 25.6 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 2.22 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 0.54 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 63,118.86 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 1,128 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 45,390,848 |
| `node_mem_available_avg` | job=monitoring-node | 391,327,232 |
| `node_mem_available_avg` | job=mysql-node | 292,718,080 |
| `node_mem_available_avg` | job=redis-node | 565,772,288 |
| `node_swap_free_avg` | job=backend-node | 2,371,154,944 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,060,416 |
| `node_swap_free_avg` | job=mysql-node | 2,635,508,736 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 139,211.43 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 2,027.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 101,681.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 109,150.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,304 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 8 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-12T12:35:50.897Z ~ 2026-08-12T12:37:50.897Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,494 | 93.36 | 13.4 | 373.57 | 1,704 | 3,053.39 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,448 | 36.73 | 6.66 | 148.92 | 528.02 | 2,927.91 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,451 | 52.97 | 10.48 | 215 | 963.56 | 2,300.54 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 59.02 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,416 | 65.38 | 20.7 | 278.33 | 431.37 | 8,001.51 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 72 | 185.69 | 177.36 | 442.92 | 571.77 | 2,102.76 |
| method=POST, status=401, uri=UNKNOWN | 63 | 3.05 | 0.58 | 3.62 | 122.36 | 125.43 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 14 | 47.27 | 6.76 | 323.17 | 350.96 | 347.74 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.29 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 26 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.75 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.54 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.71 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.57 |
| `process_rss_avg` | job=backend-node | 13,701,632 |
| `process_rss_avg` | job=monitoring-node | 17,623,040 |
| `process_rss_avg` | job=mysql-exporter | 16,682,496 |
| `process_rss_avg` | job=mysql-node | 22,521,856 |
| `process_rss_avg` | job=prometheus | 98,736,640 |
| `process_rss_avg` | job=redis-exporter | 17,928,192 |
| `process_rss_avg` | job=redis-node | 22,929,408 |
| `process_rss_max` | job=backend-node | 16,015,360 |
| `process_rss_max` | job=monitoring-node | 17,641,472 |
| `process_rss_max` | job=mysql-exporter | 17,190,912 |
| `process_rss_max` | job=mysql-node | 22,732,800 |
| `process_rss_max` | job=prometheus | 99,012,608 |
| `process_rss_max` | job=redis-exporter | 18,141,184 |
| `process_rss_max` | job=redis-node | 22,929,408 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8.43 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 50.99 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 31.56 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 2.93 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 0.93 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 76,538.29 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 433.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 47,971,328 |
| `node_mem_available_avg` | job=monitoring-node | 391,911,424 |
| `node_mem_available_avg` | job=mysql-node | 287,499,264 |
| `node_mem_available_avg` | job=redis-node | 565,719,040 |
| `node_swap_free_avg` | job=backend-node | 2,260,446,720 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,070,656 |
| `node_swap_free_avg` | job=mysql-node | 2,635,512,832 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 124,096 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 698.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 66,330.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 157,433.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,434.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 45.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-12T12:37:50.897Z ~ 2026-08-12T12:39:50.897Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,591 | 311.41 | 152.53 | 635.03 | 5,911.94 | 29,526.34 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,583 | 193.07 | 61.58 | 324.57 | 2,755.94 | 23,529.17 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 7,686 | 222.35 | 88.61 | 421.74 | 3,157.31 | 24,469.64 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 1,314.43 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,064 | 208.52 | 176.27 | 394.49 | 569.75 | 23,855.75 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 578 | 146.89 | 126.14 | 291.52 | 472.12 | 1,227.11 |
| method=POST, status=401, uri=UNKNOWN | 37 | 14.66 | 0.94 | 46.98 | 261.28 | 1,080.18 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 13 | 125.71 | 102.96 | 326.06 | 351.54 | 347.74 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.83 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.17 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11.17 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 77.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 11.07 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.51 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.81 |
| `process_rss_avg` | job=backend-node | 11,874,816 |
| `process_rss_avg` | job=monitoring-node | 17,494,016 |
| `process_rss_avg` | job=mysql-exporter | 16,608,768 |
| `process_rss_avg` | job=mysql-node | 22,349,824 |
| `process_rss_avg` | job=prometheus | 98,320,384 |
| `process_rss_avg` | job=redis-exporter | 17,849,856 |
| `process_rss_avg` | job=redis-node | 22,929,408 |
| `process_rss_max` | job=backend-node | 15,491,072 |
| `process_rss_max` | job=monitoring-node | 17,494,016 |
| `process_rss_max` | job=mysql-exporter | 16,998,400 |
| `process_rss_max` | job=mysql-node | 22,515,712 |
| `process_rss_max` | job=prometheus | 98,320,384 |
| `process_rss_max` | job=redis-exporter | 18,272,256 |
| `process_rss_max` | job=redis-node | 22,929,408 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 96.42 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.13 |
| `node_cpu_pct_avg` | job=mysql-node | 37.28 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 13.04 |
| `node_load1_avg` | job=monitoring-node | 0.06 |
| `node_load1_avg` | job=mysql-node | 1.4 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 159,818.29 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 630.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 46,325,248 |
| `node_mem_available_avg` | job=monitoring-node | 392,023,552 |
| `node_mem_available_avg` | job=mysql-node | 288,191,488 |
| `node_mem_available_avg` | job=redis-node | 565,719,040 |
| `node_swap_free_avg` | job=backend-node | 2,170,119,680 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,080,896 |
| `node_swap_free_avg` | job=mysql-node | 2,635,522,048 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 309,586.29 |
| `node_swap_in_delta` | job=monitoring-node | 8 |
| `node_swap_in_delta` | job=mysql-node | 1,858.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 175,109.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 4.57 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 221,155.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 9,001.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 153.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 1.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-12T12:39:50.897Z ~ 2026-08-12T12:41:50.897Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,142 | 16.53 | 13.83 | 29.43 | 73.98 | 29,526.34 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,223 | 7.44 | 6.56 | 12.87 | 24.92 | 23,529.17 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,549 | 13.02 | 10.94 | 23.77 | 52.42 | 24,469.64 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 1 | 63.73 | 64.54 | 1,192.41 | 1,383.81 | 1,314.43 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,150 | 27.55 | 24.74 | 43.8 | 95.65 | 23,855.75 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 17 | 21.47 | 10.49 | 117.44 | 130.86 | 1,227.11 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.75 | 0.58 | 2.04 | 7.41 | 1,080.18 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 16 | 10.62 | 8.39 | 35.23 | 38.36 | 341.02 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 43.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 103.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.52 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.58 |
| `process_rss_avg` | job=backend-node | 11,788,288 |
| `process_rss_avg` | job=monitoring-node | 17,625,088 |
| `process_rss_avg` | job=mysql-exporter | 16,766,464 |
| `process_rss_avg` | job=mysql-node | 22,393,344 |
| `process_rss_avg` | job=prometheus | 98,402,304 |
| `process_rss_avg` | job=redis-exporter | 17,843,200 |
| `process_rss_avg` | job=redis-node | 22,929,408 |
| `process_rss_max` | job=backend-node | 15,065,088 |
| `process_rss_max` | job=monitoring-node | 17,625,088 |
| `process_rss_max` | job=mysql-exporter | 17,121,280 |
| `process_rss_max` | job=mysql-node | 22,495,232 |
| `process_rss_max` | job=prometheus | 98,451,456 |
| `process_rss_max` | job=redis-exporter | 18,145,280 |
| `process_rss_max` | job=redis-node | 22,929,408 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 5 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 67.61 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 70.28 |
| `node_cpu_pct_avg` | job=redis-node | 0.39 |
| `node_load1_avg` | job=backend-node | 8.2 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 3.21 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 37,546.29 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 1,285.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 46,962,176 |
| `node_mem_available_avg` | job=monitoring-node | 392,679,936 |
| `node_mem_available_avg` | job=mysql-node | 276,523,520 |
| `node_mem_available_avg` | job=redis-node | 565,725,184 |
| `node_swap_free_avg` | job=backend-node | 2,147,378,688 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,080,896 |
| `node_swap_free_avg` | job=mysql-node | 2,635,260,928 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 51,165.71 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 3,977.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 30,864 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 780.57 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 391,326.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 177.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 11.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-12T12:41:50.897Z ~ 2026-08-12T12:43:50.897Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,581 | 191.63 | 82.25 | 811.06 | 1,924.07 | 7,105.64 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,579 | 116.86 | 41.12 | 449.93 | 1,305.99 | 15,974.37 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 10,679 | 136.98 | 62.94 | 525.43 | 1,396.73 | 16,074.61 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 1,314.43 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,285 | 137.72 | 33.35 | 361.83 | 1,074.19 | 16,184.9 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 709 | 140.62 | 87.16 | 382.32 | 841.1 | 5,375.22 |
| method=POST, status=401, uri=UNKNOWN | 30 | 34.19 | 0.68 | 149.88 | 603.08 | 568.97 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 57.35 | 44.74 | 170.01 | 177.17 | 169.27 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.71 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11.29 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.43 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 68.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 9.46 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.51 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.83 |
| `process_rss_avg` | job=backend-node | 13,873,152 |
| `process_rss_avg` | job=monitoring-node | 17,625,088 |
| `process_rss_avg` | job=mysql-exporter | 16,805,376 |
| `process_rss_avg` | job=mysql-node | 22,280,704 |
| `process_rss_avg` | job=prometheus | 98,451,456 |
| `process_rss_avg` | job=redis-exporter | 17,090,048 |
| `process_rss_avg` | job=redis-node | 22,929,408 |
| `process_rss_max` | job=backend-node | 17,342,464 |
| `process_rss_max` | job=monitoring-node | 17,625,088 |
| `process_rss_max` | job=mysql-exporter | 17,334,272 |
| `process_rss_max` | job=mysql-node | 22,589,440 |
| `process_rss_max` | job=prometheus | 98,451,456 |
| `process_rss_max` | job=redis-exporter | 17,227,776 |
| `process_rss_max` | job=redis-node | 22,929,408 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 30.43 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 91.21 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 47.27 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 8.36 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 3.27 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 114,438.86 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 558.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 64,414,208 |
| `node_mem_available_avg` | job=monitoring-node | 392,813,056 |
| `node_mem_available_avg` | job=mysql-node | 260,975,104 |
| `node_mem_available_avg` | job=redis-node | 565,735,424 |
| `node_swap_free_avg` | job=backend-node | 2,135,397,376 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,080,896 |
| `node_swap_free_avg` | job=mysql-node | 2,634,865,152 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 219,772.57 |
| `node_swap_in_delta` | job=monitoring-node | 3.43 |
| `node_swap_in_delta` | job=mysql-node | 2,092.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 134,385.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 290,984 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 3,917.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 53.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-12T12:43:50.897Z ~ 2026-08-12T12:45:50.897Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,175 | 234.45 | 170.95 | 491.68 | 1,639.25 | 14,301.16 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,261 | 142.38 | 85.75 | 307.21 | 730.44 | 15,974.37 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,958 | 188.72 | 118.19 | 390.6 | 882.87 | 16,074.61 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,229 | 320.04 | 213.52 | 605.84 | 2,550.14 | 16,184.9 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,536 | 157.39 | 108.29 | 352.37 | 785.46 | 14,242.06 |
| method=POST, status=401, uri=UNKNOWN | 7 | 107.72 | 9.79 | 420.55 | 442.02 | 568.97 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 14 | 144.23 | 134.22 | 393.71 | 436.66 | 418.44 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.67 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.83 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 163.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 31.68 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 7.04 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.83 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107.17 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.58 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.83 |
| `process_rss_avg` | job=backend-node | 12,605,440 |
| `process_rss_avg` | job=monitoring-node | 17,625,088 |
| `process_rss_avg` | job=mysql-exporter | 16,609,792 |
| `process_rss_avg` | job=mysql-node | 22,390,272 |
| `process_rss_avg` | job=prometheus | 98,582,528 |
| `process_rss_avg` | job=redis-exporter | 17,498,112 |
| `process_rss_avg` | job=redis-node | 22,929,408 |
| `process_rss_max` | job=backend-node | 17,715,200 |
| `process_rss_max` | job=monitoring-node | 17,625,088 |
| `process_rss_max` | job=mysql-exporter | 17,178,624 |
| `process_rss_max` | job=mysql-node | 22,568,960 |
| `process_rss_max` | job=prometheus | 98,844,672 |
| `process_rss_max` | job=redis-exporter | 18,006,016 |
| `process_rss_max` | job=redis-node | 22,929,408 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 96.64 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 36.03 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 16.38 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 4.33 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 158,091.43 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 204.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 53,965,824 |
| `node_mem_available_avg` | job=monitoring-node | 393,141,248 |
| `node_mem_available_avg` | job=mysql-node | 246,498,304 |
| `node_mem_available_avg` | job=redis-node | 565,735,424 |
| `node_swap_free_avg` | job=backend-node | 2,110,953,472 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,080,896 |
| `node_swap_free_avg` | job=mysql-node | 2,634,877,440 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 263,673.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 910.86 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 137,643.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 260,745.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,377.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 90.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 2.29 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## round5-pure-throughput-sse500-fullramp-20260812.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-12T12:47:49.721Z ~ 2026-08-12T13:01:33.072Z
- 설정: `{"sseVUs":500,"totalSseConnections":1000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-12T12:48:24.721Z ~ 2026-08-12T12:50:24.721Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,216 | 171.54 | 13.75 | 817.24 | 2,253.07 | 8,593.64 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,141 | 116.04 | 7.7 | 425.77 | 3,311.3 | 8,502.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,544 | 134.29 | 11.83 | 559.05 | 2,501.82 | 9,026.11 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 847,888.3 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 847,735 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 77 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 885.63 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 22.73 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,051 | 112.82 | 21.49 | 463.58 | 867.94 | 9,091.53 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 41 | 172.09 | 139.81 | 457.91 | 523.6 | 8,671.92 |
| method=POST, status=401, uri=UNKNOWN | 62 | 4.62 | 0.6 | 15.8 | 105.45 | 115.26 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 6.63 | 6.52 | 8.11 | 8.33 | 7.22 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 8 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 61.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 2.85 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.02 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.32 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.2 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.45 |
| `process_rss_avg` | job=backend-node | 13,788,672 |
| `process_rss_avg` | job=monitoring-node | 17,702,912 |
| `process_rss_avg` | job=mysql-exporter | 16,619,008 |
| `process_rss_avg` | job=mysql-node | 22,364,672 |
| `process_rss_avg` | job=prometheus | 98,910,208 |
| `process_rss_avg` | job=redis-exporter | 17,919,488 |
| `process_rss_avg` | job=redis-node | 22,802,432 |
| `process_rss_max` | job=backend-node | 17,588,224 |
| `process_rss_max` | job=monitoring-node | 17,702,912 |
| `process_rss_max` | job=mysql-exporter | 17,039,360 |
| `process_rss_max` | job=mysql-node | 22,499,328 |
| `process_rss_max` | job=prometheus | 99,106,816 |
| `process_rss_max` | job=redis-exporter | 18,378,752 |
| `process_rss_max` | job=redis-node | 22,884,352 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 60.36 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.19 |
| `node_cpu_pct_avg` | job=mysql-node | 21.14 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 4.94 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 1.04 |
| `node_load1_avg` | job=redis-node | 0.02 |
| `node_major_fault_delta` | job=backend-node | 108,906.29 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 212.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,155,968 |
| `node_mem_available_avg` | job=monitoring-node | 394,533,376 |
| `node_mem_available_avg` | job=mysql-node | 245,246,464 |
| `node_mem_available_avg` | job=redis-node | 565,743,616 |
| `node_swap_free_avg` | job=backend-node | 2,151,335,424 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,080,896 |
| `node_swap_free_avg` | job=mysql-node | 2,634,906,624 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 187,245.71 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 814.86 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 106,185.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 111,971.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,874.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 42.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-12T12:50:24.721Z ~ 2026-08-12T12:52:24.721Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,786 | 25.74 | 12.14 | 81.88 | 280.11 | 4,593.23 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,675 | 9.09 | 5.95 | 9.64 | 48.47 | 3,591.55 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,193 | 17.6 | 9.99 | 32.28 | 152.39 | 3,701.62 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 22.73 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,720 | 40.67 | 19.94 | 44.46 | 692.34 | 6,861.75 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 5 | 161.21 | 134.22 | 429.5 | 443.81 | 919.94 |
| method=POST, status=401, uri=UNKNOWN | 78 | 0.55 | 0.56 | 1.26 | 7.44 | 110.68 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 5.7 | 5.94 | 8.04 | 8.32 | 7.22 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 27.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.23 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_rss_avg` | job=backend-node | 14,162,944 |
| `process_rss_avg` | job=monitoring-node | 17,702,912 |
| `process_rss_avg` | job=mysql-exporter | 16,785,920 |
| `process_rss_avg` | job=mysql-node | 22,327,296 |
| `process_rss_avg` | job=prometheus | 99,237,888 |
| `process_rss_avg` | job=redis-exporter | 17,344,000 |
| `process_rss_avg` | job=redis-node | 22,851,584 |
| `process_rss_max` | job=backend-node | 16,420,864 |
| `process_rss_max` | job=monitoring-node | 17,702,912 |
| `process_rss_max` | job=mysql-exporter | 17,113,088 |
| `process_rss_max` | job=mysql-node | 22,605,824 |
| `process_rss_max` | job=prometheus | 99,368,960 |
| `process_rss_max` | job=redis-exporter | 17,453,056 |
| `process_rss_max` | job=redis-node | 22,884,352 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 37.41 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 32.81 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 3.9 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.52 |
| `node_load1_avg` | job=redis-node | 0.01 |
| `node_major_fault_delta` | job=backend-node | 27,744 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 237.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 49,449,984 |
| `node_mem_available_avg` | job=monitoring-node | 394,632,192 |
| `node_mem_available_avg` | job=mysql-node | 233,538,048 |
| `node_mem_available_avg` | job=redis-node | 565,762,560 |
| `node_swap_free_avg` | job=backend-node | 2,148,276,224 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,080,896 |
| `node_swap_free_avg` | job=mysql-node | 2,634,911,744 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 56,406.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 1,144 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 25,702.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 165,995.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 780.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 10.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-12T12:52:24.721Z ~ 2026-08-12T12:54:24.721Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,904 | 78.98 | 12.76 | 269.93 | 1,522.92 | 4,593.23 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,705 | 38.99 | 6.51 | 163.33 | 271.04 | 10,037.41 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,409 | 50.73 | 10.82 | 196.94 | 368.74 | 10,042.65 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,654 | 62.28 | 20.89 | 254.04 | 375.68 | 10,060.3 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 142 | 129.13 | 122.02 | 274.75 | 350.96 | 919.94 |
| method=POST, status=401, uri=UNKNOWN | 70 | 0.99 | 0.56 | 3.13 | 18.96 | 52.94 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 7 | 9.42 | 6.99 | 20.69 | 22.03 | 18.26 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.57 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.43 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.86 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 40 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.52 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 101.71 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 102 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.37 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_rss_avg` | job=backend-node | 14,037,504 |
| `process_rss_avg` | job=monitoring-node | 17,702,912 |
| `process_rss_avg` | job=mysql-exporter | 16,728,576 |
| `process_rss_avg` | job=mysql-node | 22,446,080 |
| `process_rss_avg` | job=prometheus | 99,450,880 |
| `process_rss_avg` | job=redis-exporter | 17,659,904 |
| `process_rss_avg` | job=redis-node | 22,884,352 |
| `process_rss_max` | job=backend-node | 15,716,352 |
| `process_rss_max` | job=monitoring-node | 17,702,912 |
| `process_rss_max` | job=mysql-exporter | 17,289,216 |
| `process_rss_max` | job=mysql-node | 22,568,960 |
| `process_rss_max` | job=prometheus | 99,500,032 |
| `process_rss_max` | job=redis-exporter | 18,079,744 |
| `process_rss_max` | job=redis-node | 22,884,352 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 9.14 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 57.13 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 48.52 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 2.52 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.39 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 48,707.43 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 278.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 49,544,192 |
| `node_mem_available_avg` | job=monitoring-node | 395,063,296 |
| `node_mem_available_avg` | job=mysql-node | 232,524,800 |
| `node_mem_available_avg` | job=redis-node | 565,600,256 |
| `node_swap_free_avg` | job=backend-node | 2,148,281,856 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,080,896 |
| `node_swap_free_avg` | job=mysql-node | 2,634,911,744 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 79,305.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 985.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 32,005.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 259,328 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,428.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 34.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-12T12:54:24.721Z ~ 2026-08-12T12:56:24.721Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,018 | 200.87 | 17.24 | 971.34 | 2,441.93 | 14,089.66 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,713 | 112.58 | 7.56 | 495 | 1,597.12 | 14,168.02 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,584 | 116.04 | 12.99 | 454.49 | 1,482.75 | 14,143.51 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,415 | 148.48 | 24.84 | 546.71 | 2,764.53 | 14,233.93 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 87 | 696.31 | 357.91 | 3,092.38 | 4,638.56 | 4,627.59 |
| method=POST, status=401, uri=UNKNOWN | 32 | 94.86 | 0.67 | 590.56 | 1,689.35 | 1,602.55 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 8.83 | 9.09 | 9.72 | 9.77 | 18.26 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.33 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.67 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.17 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 70.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 46.08 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.61 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.19 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.33 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.36 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.48 |
| `process_rss_avg` | job=backend-node | 13,394,944 |
| `process_rss_avg` | job=monitoring-node | 17,702,912 |
| `process_rss_avg` | job=mysql-exporter | 16,683,008 |
| `process_rss_avg` | job=mysql-node | 22,290,944 |
| `process_rss_avg` | job=prometheus | 100,302,848 |
| `process_rss_avg` | job=redis-exporter | 17,719,296 |
| `process_rss_avg` | job=redis-node | 22,884,352 |
| `process_rss_max` | job=backend-node | 15,368,192 |
| `process_rss_max` | job=monitoring-node | 17,702,912 |
| `process_rss_max` | job=mysql-exporter | 17,096,704 |
| `process_rss_max` | job=mysql-node | 22,425,600 |
| `process_rss_max` | job=prometheus | 101,072,896 |
| `process_rss_max` | job=redis-exporter | 18,096,128 |
| `process_rss_max` | job=redis-node | 22,884,352 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 18.17 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 85.07 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.37 |
| `node_cpu_pct_avg` | job=mysql-node | 32.84 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 6.49 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.88 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 112,670.86 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 36.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 48,593,920 |
| `node_mem_available_avg` | job=monitoring-node | 392,231,936 |
| `node_mem_available_avg` | job=mysql-node | 230,331,392 |
| `node_mem_available_avg` | job=redis-node | 565,545,984 |
| `node_swap_free_avg` | job=backend-node | 2,141,041,152 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,080,896 |
| `node_swap_free_avg` | job=mysql-node | 2,634,911,744 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 207,012.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 28.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 122,169.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 152,452.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 2 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 11,274.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 27.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 5.71 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-12T12:56:24.721Z ~ 2026-08-12T12:58:24.721Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,099 | 484.98 | 177.91 | 1,255.68 | 17,671.15 | 27,749.16 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,093 | 265.37 | 86.12 | 541.34 | 2,209.22 | 21,476.65 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,640 | 373.02 | 125.34 | 644.99 | 4,915.35 | 24,293.93 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 915 | 596.06 | 226.02 | 1,462.57 | 19,079.2 | 27,029.83 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,058 | 473.77 | 123.18 | 1,006.45 | 17,709.58 | 26,379.82 |
| method=POST, status=401, uri=UNKNOWN | 10 | 25.78 | 4.89 | 191.26 | 199.31 | 1,602.55 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 225.91 | 234.88 | 244.95 | 245.84 | 225.91 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 160 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 21.56 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 12.8 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105.83 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.42 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.51 |
| `process_rss_avg` | job=backend-node | 12,746,240 |
| `process_rss_avg` | job=monitoring-node | 17,735,680 |
| `process_rss_avg` | job=mysql-exporter | 16,812,544 |
| `process_rss_avg` | job=mysql-node | 22,461,952 |
| `process_rss_avg` | job=prometheus | 101,072,896 |
| `process_rss_avg` | job=redis-exporter | 18,243,584 |
| `process_rss_avg` | job=redis-node | 22,939,136 |
| `process_rss_max` | job=backend-node | 14,426,112 |
| `process_rss_max` | job=monitoring-node | 17,833,984 |
| `process_rss_max` | job=mysql-exporter | 17,022,976 |
| `process_rss_max` | job=mysql-node | 22,704,128 |
| `process_rss_max` | job=prometheus | 101,072,896 |
| `process_rss_max` | job=redis-exporter | 18,358,272 |
| `process_rss_max` | job=redis-node | 23,277,568 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 41.67 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.17 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 96.99 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 29.95 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 14.48 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 3.28 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 165,974.86 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 587.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 59,392,512 |
| `node_mem_available_avg` | job=monitoring-node | 382,660,096 |
| `node_mem_available_avg` | job=mysql-node | 262,935,552 |
| `node_mem_available_avg` | job=redis-node | 565,551,104 |
| `node_swap_free_avg` | job=backend-node | 2,112,713,728 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,080,896 |
| `node_swap_free_avg` | job=mysql-node | 2,622,746,112 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 278,634.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 523.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 135,464 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 178,267.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 32,520 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 70.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 3.43 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 5.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-12T12:58:24.721Z ~ 2026-08-12T13:00:24.721Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,103 | 411.21 | 172.05 | 1,494.29 | 3,667.19 | 27,749.16 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,087 | 167.74 | 75.79 | 523.81 | 1,033.18 | 21,476.65 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,599 | 227.05 | 109.81 | 667.47 | 1,748.36 | 24,293.93 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 933 | 329.68 | 218.73 | 954.44 | 2,000 | 27,029.83 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,147 | 231.9 | 104.74 | 618.21 | 1,467.45 | 26,379.82 |
| method=POST, status=401, uri=UNKNOWN | 8 | 99.71 | 22.37 | 340.54 | 354.44 | 581.04 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 124.26 | 58.72 | 416.07 | 441.13 | 416.23 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.8 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12.2 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.33 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 80 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.33 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 28.4 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 10.52 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.21 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.6 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.47 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 1 |
| `process_rss_avg` | job=backend-node | 11,827,200 |
| `process_rss_avg` | job=monitoring-node | 17,532,928 |
| `process_rss_avg` | job=mysql-exporter | 16,788,992 |
| `process_rss_avg` | job=mysql-node | 22,395,904 |
| `process_rss_avg` | job=prometheus | 101,953,536 |
| `process_rss_avg` | job=redis-exporter | 18,358,272 |
| `process_rss_avg` | job=redis-node | 22,872,064 |
| `process_rss_max` | job=backend-node | 14,241,792 |
| `process_rss_max` | job=monitoring-node | 17,833,984 |
| `process_rss_max` | job=mysql-exporter | 17,231,872 |
| `process_rss_max` | job=mysql-node | 23,019,520 |
| `process_rss_max` | job=prometheus | 110,493,696 |
| `process_rss_max` | job=redis-exporter | 18,358,272 |
| `process_rss_max` | job=redis-node | 22,986,752 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 30.6 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.2 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.21 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.63 |
| `node_cpu_pct_avg` | job=mysql-node | 25.82 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 15.02 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 3.41 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 175,249.14 |
| `node_major_fault_delta` | job=monitoring-node | 11.43 |
| `node_major_fault_delta` | job=mysql-node | 233.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 64,608,256 |
| `node_mem_available_avg` | job=monitoring-node | 381,242,368 |
| `node_mem_available_avg` | job=mysql-node | 256,869,376 |
| `node_mem_available_avg` | job=redis-node | 565,551,104 |
| `node_swap_free_avg` | job=backend-node | 2,108,016,128 |
| `node_swap_free_avg` | job=monitoring-node | 3,024,062,464 |
| `node_swap_free_avg` | job=mysql-node | 2,622,751,744 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 281,061.71 |
| `node_swap_in_delta` | job=monitoring-node | 5.71 |
| `node_swap_in_delta` | job=mysql-node | 753.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 144,041.14 |
| `node_swap_out_delta` | job=monitoring-node | 116.57 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 155,968 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,843.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 35.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## round5-pure-throughput-sse1000-fullramp-20260812.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-12T13:02:28.389Z ~ 2026-08-12T13:16:25.966Z
- 설정: `{"sseVUs":1000,"totalSseConnections":2000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-12T13:03:03.389Z ~ 2026-08-12T13:05:03.389Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,071 | 291.98 | 147.06 | 1,182.17 | 2,340.53 | 2,556.09 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 963 | 103.07 | 11.88 | 442.92 | 952.27 | 1,232.31 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,065 | 180.28 | 63.42 | 869.97 | 1,561.78 | 1,838.57 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 872,308.96 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 872,173.47 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 862,458.17 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 350.46 |
| method=POST, status=200, uri=/api/sse/tickets | 443 | 13.9 | 0.55 | 3.01 | 637.09 | 1,356.91 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 906 | 182.79 | 81.65 | 549.24 | 873.98 | 10,733.65 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 83 | 255.77 | 230.41 | 636.79 | 829.47 | 841.35 |
| method=POST, status=401, uri=UNKNOWN | 49 | 22.59 | 0.83 | 197.97 | 345.46 | 1,580.98 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 120.36 | 6.99 | 243.83 | 245.62 | 235.06 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 105.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.42 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 103.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.17 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.34 |
| `process_rss_avg` | job=backend-node | 12,489,216 |
| `process_rss_avg` | job=monitoring-node | 17,686,528 |
| `process_rss_avg` | job=mysql-exporter | 16,389,632 |
| `process_rss_avg` | job=mysql-node | 22,239,744 |
| `process_rss_avg` | job=prometheus | 119,386,112 |
| `process_rss_avg` | job=redis-exporter | 18,031,104 |
| `process_rss_avg` | job=redis-node | 22,986,752 |
| `process_rss_max` | job=backend-node | 13,246,464 |
| `process_rss_max` | job=monitoring-node | 17,801,216 |
| `process_rss_max` | job=mysql-exporter | 16,666,624 |
| `process_rss_max` | job=mysql-node | 22,437,888 |
| `process_rss_max` | job=prometheus | 131,121,152 |
| `process_rss_max` | job=redis-exporter | 18,358,272 |
| `process_rss_max` | job=redis-node | 22,986,752 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 951.38 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 951.25 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 79.07 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 18.1 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 7.79 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 1.1 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 141,043.43 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 233.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 44,433,920 |
| `node_mem_available_avg` | job=monitoring-node | 378,290,176 |
| `node_mem_available_avg` | job=mysql-node | 257,495,552 |
| `node_mem_available_avg` | job=redis-node | 564,793,344 |
| `node_swap_free_avg` | job=backend-node | 2,093,544,960 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,936,512 |
| `node_swap_free_avg` | job=mysql-node | 2,622,774,784 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 253,572.57 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 768 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 107,557.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 98,430.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,353.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 35.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-12T13:05:03.389Z ~ 2026-08-12T13:07:03.389Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,656 | 12.67 | 11.97 | 16.47 | 27.73 | 2,556.09 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,661 | 6.39 | 6 | 9.28 | 15.71 | 1,232.31 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,977 | 10.92 | 10.31 | 14.54 | 27.68 | 1,838.57 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 862,970.69 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 862,458.17 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 350.46 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 1,356.91 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,649 | 19.29 | 19.66 | 25.12 | 37.02 | 10,733.65 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 841.35 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.46 | 0.54 | 1.04 | 3.95 | 312.18 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 5.33 | 4.99 | 6.43 | 6.88 | 235.06 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 60.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.96 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 106 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.26 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.34 |
| `process_rss_avg` | job=backend-node | 13,252,608 |
| `process_rss_avg` | job=monitoring-node | 17,683,456 |
| `process_rss_avg` | job=mysql-exporter | 16,562,176 |
| `process_rss_avg` | job=mysql-node | 22,332,416 |
| `process_rss_avg` | job=prometheus | 99,827,712 |
| `process_rss_avg` | job=redis-exporter | 17,854,464 |
| `process_rss_avg` | job=redis-node | 22,873,088 |
| `process_rss_max` | job=backend-node | 13,803,520 |
| `process_rss_max` | job=monitoring-node | 17,801,216 |
| `process_rss_max` | job=mysql-exporter | 17,358,848 |
| `process_rss_max` | job=mysql-node | 22,417,408 |
| `process_rss_max` | job=prometheus | 99,827,712 |
| `process_rss_max` | job=redis-exporter | 18,247,680 |
| `process_rss_max` | job=redis-node | 22,986,752 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 0.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 33.6 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 32.83 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 4.77 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.7 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 13,728 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 293.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 50,727,424 |
| `node_mem_available_avg` | job=monitoring-node | 386,323,456 |
| `node_mem_available_avg` | job=mysql-node | 253,406,720 |
| `node_mem_available_avg` | job=redis-node | 564,798,464 |
| `node_swap_free_avg` | job=backend-node | 2,086,015,488 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,941,632 |
| `node_swap_free_avg` | job=mysql-node | 2,622,787,584 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 17,392 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 1,157.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 7,666.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 156,763.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-12T13:07:03.389Z ~ 2026-08-12T13:09:03.389Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | N/A | N/A | N/A | N/A | N/A | 22,000.94 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | N/A | N/A | N/A | N/A | N/A | 2,822.65 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | N/A | N/A | N/A | N/A | N/A | 21,514.94 |
| method=GET, status=200, uri=/api/auctions/stream | N/A | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | N/A | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | N/A | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | N/A | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | N/A | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | N/A | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | N/A | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | N/A | N/A | N/A | N/A | N/A | 22,616.72 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | N/A | N/A | N/A | N/A | N/A | 578.91 |
| method=POST, status=401, uri=UNKNOWN | N/A | N/A | N/A | N/A | N/A | 2,493.85 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | N/A | N/A | N/A | N/A | N/A | 214.78 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 101 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_rss_avg` | job=backend-node | 13,395,968 |
| `process_rss_avg` | job=monitoring-node | 17,612,800 |
| `process_rss_avg` | job=mysql-exporter | 16,741,888 |
| `process_rss_avg` | job=mysql-node | 22,320,640 |
| `process_rss_avg` | job=prometheus | 100,237,312 |
| `process_rss_avg` | job=redis-exporter | 17,395,200 |
| `process_rss_avg` | job=redis-node | 22,835,200 |
| `process_rss_max` | job=backend-node | 16,252,928 |
| `process_rss_max` | job=monitoring-node | 17,612,800 |
| `process_rss_max` | job=mysql-exporter | 17,162,240 |
| `process_rss_max` | job=mysql-node | 22,577,152 |
| `process_rss_max` | job=prometheus | 100,483,072 |
| `process_rss_max` | job=redis-exporter | 18,051,072 |
| `process_rss_max` | job=redis-node | 22,835,200 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 94.89 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 2.42 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 4.3 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.48 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 165,029.71 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 6.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 42,009,600 |
| `node_mem_available_avg` | job=monitoring-node | 386,757,632 |
| `node_mem_available_avg` | job=mysql-node | 245,744,640 |
| `node_mem_available_avg` | job=redis-node | 564,801,536 |
| `node_swap_free_avg` | job=backend-node | 2,071,350,784 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,941,632 |
| `node_swap_free_avg` | job=mysql-node | 2,622,787,584 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 268,149.71 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 9.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 162,067.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 7,486.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 32.75 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-12T13:09:03.389Z ~ 2026-08-12T13:11:03.389Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `process_rss_avg` | job=backend-node | 10,398,720 |
| `process_rss_avg` | job=monitoring-node | 17,693,696 |
| `process_rss_avg` | job=mysql-exporter | 16,723,456 |
| `process_rss_avg` | job=mysql-node | 22,528,000 |
| `process_rss_avg` | job=prometheus | 99,671,040 |
| `process_rss_avg` | job=redis-exporter | 17,444,864 |
| `process_rss_avg` | job=redis-node | 22,835,200 |
| `process_rss_max` | job=backend-node | 16,244,736 |
| `process_rss_max` | job=monitoring-node | 17,743,872 |
| `process_rss_max` | job=mysql-exporter | 17,002,496 |
| `process_rss_max` | job=mysql-node | 22,941,696 |
| `process_rss_max` | job=prometheus | 100,483,072 |
| `process_rss_max` | job=redis-exporter | 17,637,376 |
| `process_rss_max` | job=redis-node | 22,835,200 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 92.16 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 1.48 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 3.97 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 0.06 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 217,579.43 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 174.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 52,122,112 |
| `node_mem_available_avg` | job=monitoring-node | 391,199,744 |
| `node_mem_available_avg` | job=mysql-node | 241,448,448 |
| `node_mem_available_avg` | job=redis-node | 564,803,072 |
| `node_swap_free_avg` | job=backend-node | 2,047,320,576 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,941,632 |
| `node_swap_free_avg` | job=mysql-node | 2,622,787,584 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 310,536 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 184 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 107,944 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 251.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 1.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-12T13:11:03.389Z ~ 2026-08-12T13:13:03.389Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `process_rss_avg` | job=backend-node | 8,504,832 |
| `process_rss_avg` | job=monitoring-node | 17,506,304 |
| `process_rss_avg` | job=mysql-exporter | 16,558,592 |
| `process_rss_avg` | job=mysql-node | 22,365,696 |
| `process_rss_avg` | job=prometheus | 98,361,344 |
| `process_rss_avg` | job=redis-exporter | 17,473,536 |
| `process_rss_avg` | job=redis-node | 22,835,200 |
| `process_rss_max` | job=backend-node | 10,371,072 |
| `process_rss_max` | job=monitoring-node | 17,604,608 |
| `process_rss_max` | job=mysql-exporter | 17,088,512 |
| `process_rss_max` | job=mysql-node | 22,736,896 |
| `process_rss_max` | job=prometheus | 98,361,344 |
| `process_rss_max` | job=redis-exporter | 17,817,600 |
| `process_rss_max` | job=redis-node | 22,835,200 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 93.55 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 2.09 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 3.02 |
| `node_load1_avg` | job=monitoring-node | 0.06 |
| `node_load1_avg` | job=mysql-node | 0.04 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 177,394.29 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 304 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 50,279,424 |
| `node_mem_available_avg` | job=monitoring-node | 395,333,120 |
| `node_mem_available_avg` | job=mysql-node | 253,531,648 |
| `node_mem_available_avg` | job=redis-node | 564,826,112 |
| `node_swap_free_avg` | job=backend-node | 2,044,967,424 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,941,632 |
| `node_swap_free_avg` | job=mysql-node | 2,622,181,888 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 290,354.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 12.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 59,980.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 4,616 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 168 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 32.13 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-12T13:13:03.389Z ~ 2026-08-12T13:15:03.389Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `process_rss_avg` | job=backend-node | 7,407,616 |
| `process_rss_avg` | job=monitoring-node | 17,604,608 |
| `process_rss_avg` | job=mysql-exporter | 16,565,248 |
| `process_rss_avg` | job=mysql-node | 22,254,592 |
| `process_rss_avg` | job=prometheus | 98,410,496 |
| `process_rss_avg` | job=redis-exporter | 17,317,888 |
| `process_rss_avg` | job=redis-node | 22,835,200 |
| `process_rss_max` | job=backend-node | 9,302,016 |
| `process_rss_max` | job=monitoring-node | 17,604,608 |
| `process_rss_max` | job=mysql-exporter | 16,945,152 |
| `process_rss_max` | job=mysql-node | 22,474,752 |
| `process_rss_max` | job=prometheus | 98,492,416 |
| `process_rss_max` | job=redis-exporter | 17,596,416 |
| `process_rss_max` | job=redis-node | 22,835,200 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 94.11 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.42 |
| `node_cpu_pct_avg` | job=mysql-node | 1.34 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 6.7 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 0 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 209,954.29 |
| `node_major_fault_delta` | job=monitoring-node | 84.57 |
| `node_major_fault_delta` | job=mysql-node | 13.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 47,571,456 |
| `node_mem_available_avg` | job=monitoring-node | 393,214,976 |
| `node_mem_available_avg` | job=mysql-node | 257,002,496 |
| `node_mem_available_avg` | job=redis-node | 564,835,328 |
| `node_swap_free_avg` | job=backend-node | 2,042,066,432 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,941,632 |
| `node_swap_free_avg` | job=mysql-node | 2,622,095,360 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 323,749.71 |
| `node_swap_in_delta` | job=monitoring-node | 3.43 |
| `node_swap_in_delta` | job=mysql-node | 27.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 95,395.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 1,372.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 22.25 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## round5-pure-throughput-sse1000-rerun-20260812.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-12T13:48:06.318Z ~ 2026-08-12T14:01:52.160Z
- 설정: `{"sseVUs":1000,"totalSseConnections":2000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-12T13:48:41.318Z ~ 2026-08-12T13:50:41.318Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 314.42 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 2,161.67 |
| method=POST, status=401, uri=UNKNOWN | 5 | 1.89 | 2.27 | 3.09 | 3.14 | 16.96 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 10.19 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.37 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.15 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.16 |
| `process_rss_avg` | job=backend-node | 13,784,064 |
| `process_rss_avg` | job=monitoring-node | 17,526,784 |
| `process_rss_avg` | job=mysql-exporter | 16,340,992 |
| `process_rss_avg` | job=mysql-node | 22,311,936 |
| `process_rss_avg` | job=prometheus | 99,815,424 |
| `process_rss_avg` | job=redis-exporter | 17,413,632 |
| `process_rss_avg` | job=redis-node | 22,843,392 |
| `process_rss_max` | job=backend-node | 15,532,032 |
| `process_rss_max` | job=monitoring-node | 17,641,472 |
| `process_rss_max` | job=mysql-exporter | 16,769,024 |
| `process_rss_max` | job=mysql-node | 22,564,864 |
| `process_rss_max` | job=prometheus | 99,913,728 |
| `process_rss_max` | job=redis-exporter | 17,776,640 |
| `process_rss_max` | job=redis-node | 23,048,192 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 95.98 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 4.84 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 10.47 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.08 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 170,626.29 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 61.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 54,375,936 |
| `node_mem_available_avg` | job=monitoring-node | 398,641,152 |
| `node_mem_available_avg` | job=mysql-node | 261,359,616 |
| `node_mem_available_avg` | job=redis-node | 558,402,048 |
| `node_swap_free_avg` | job=backend-node | 2,232,584,192 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,941,632 |
| `node_swap_free_avg` | job=mysql-node | 2,620,455,936 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 268,619.43 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 85.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 174,098.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 15,328 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 18,876.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 13.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 3.43 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-12T13:50:41.318Z ~ 2026-08-12T13:52:41.318Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 78 | 1,544.66 | 1,000 | 4,509.72 | 5,483.24 | 6,896.01 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 304 | 676.53 | 429.5 | 1,345.76 | 1,932.66 | 108,247.56 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 633 | 1,565.88 | 691.74 | 2,348.81 | 21,594.14 | 112,880.88 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 42,117.02 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 59,065.58 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 9 | 71,441.56 | 30,000 | 30,000 | 30,000 | 112,613.14 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 7 | 42,913.82 | 30,000 | 30,000 | 30,000 | 74,219.74 |
| method=POST, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 59,084.12 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 23.02 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 34.52 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.9 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 8.5 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.21 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.4 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 112 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.39 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_rss_avg` | job=backend-node | 12,919,296 |
| `process_rss_avg` | job=monitoring-node | 17,543,168 |
| `process_rss_avg` | job=mysql-exporter | 16,440,832 |
| `process_rss_avg` | job=mysql-node | 22,290,432 |
| `process_rss_avg` | job=prometheus | 99,769,344 |
| `process_rss_avg` | job=redis-exporter | 17,211,392 |
| `process_rss_avg` | job=redis-node | 22,706,176 |
| `process_rss_max` | job=backend-node | 15,622,144 |
| `process_rss_max` | job=monitoring-node | 17,772,544 |
| `process_rss_max` | job=mysql-exporter | 16,842,752 |
| `process_rss_max` | job=mysql-node | 22,511,616 |
| `process_rss_max` | job=prometheus | 100,044,800 |
| `process_rss_max` | job=redis-exporter | 17,858,560 |
| `process_rss_max` | job=redis-node | 22,884,352 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 83 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 341 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 89 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 682 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 4.5 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 6 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 93.53 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 3.11 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 10.22 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.01 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 201,625.14 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 18.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 53,739,008 |
| `node_mem_available_avg` | job=monitoring-node | 397,698,048 |
| `node_mem_available_avg` | job=mysql-node | 254,850,048 |
| `node_mem_available_avg` | job=redis-node | 558,340,096 |
| `node_swap_free_avg` | job=backend-node | 2,159,127,040 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,941,632 |
| `node_swap_free_avg` | job=mysql-node | 2,620,456,960 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 305,094.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 51.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 145,705.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 7,685.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 57,149.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 1.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-12T13:52:41.318Z ~ 2026-08-12T13:54:41.318Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | N/A | N/A | N/A | N/A | N/A | 24,683.74 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | N/A | N/A | N/A | N/A | N/A | 108,247.56 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | N/A | N/A | N/A | N/A | N/A | 112,880.88 |
| method=GET, status=200, uri=/api/auctions/stream | N/A | N/A | N/A | N/A | N/A | 283,060.45 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | N/A | N/A | N/A | N/A | N/A | 309,149.28 |
| method=GET, status=500, uri=/api/auctions | N/A | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | N/A | N/A | N/A | N/A | N/A | 59,065.58 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | N/A | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | N/A | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | N/A | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | N/A | N/A | N/A | N/A | N/A | 112,613.14 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | N/A | N/A | N/A | N/A | N/A | 54,834.87 |
| method=POST, status=401, uri=UNKNOWN | N/A | N/A | N/A | N/A | N/A | 2,017.29 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | N/A | N/A | N/A | N/A | N/A | 168,029.17 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_rss_avg` | job=backend-node | 11,866,112 |
| `process_rss_avg` | job=monitoring-node | 17,641,472 |
| `process_rss_avg` | job=mysql-exporter | 16,398,336 |
| `process_rss_avg` | job=mysql-node | 22,313,472 |
| `process_rss_avg` | job=prometheus | 99,270,656 |
| `process_rss_avg` | job=redis-exporter | 17,244,672 |
| `process_rss_avg` | job=redis-node | 22,806,016 |
| `process_rss_max` | job=backend-node | 15,241,216 |
| `process_rss_max` | job=monitoring-node | 17,641,472 |
| `process_rss_max` | job=mysql-exporter | 16,904,192 |
| `process_rss_max` | job=mysql-node | 22,634,496 |
| `process_rss_max` | job=prometheus | 99,860,480 |
| `process_rss_max` | job=redis-exporter | 17,383,424 |
| `process_rss_max` | job=redis-node | 22,999,040 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 77 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 77 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.38 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 8.01 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 17.82 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.01 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 186,147.43 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 13.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 59,897,563.43 |
| `node_mem_available_avg` | job=monitoring-node | 398,148,096 |
| `node_mem_available_avg` | job=mysql-node | 254,671,872 |
| `node_mem_available_avg` | job=redis-node | 558,535,680 |
| `node_swap_free_avg` | job=backend-node | 2,129,139,419.43 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,941,632 |
| `node_swap_free_avg` | job=mysql-node | 2,620,467,200 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 254,598.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 64 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 167,155.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 37,627.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 1.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-12T13:54:41.318Z ~ 2026-08-12T13:56:41.318Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,911 | 475.77 | 346.03 | 1,220.65 | 2,111.35 | 24,683.74 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,790 | 249.41 | 191.17 | 513.49 | 1,254.59 | 21,140.21 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,738 | 314.35 | 244.83 | 702.07 | 1,480.02 | 21,951.02 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 283,060.45 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 309,149.28 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 1,503.06 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,081 | 585.48 | 467.09 | 1,489.55 | 2,145.92 | 109,885.76 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,538 | 253.98 | 222.38 | 564.09 | 1,691.06 | 1,876.13 |
| method=POST, status=401, uri=UNKNOWN | 6 | 81.48 | 34.95 | 292.11 | 298.42 | 2,017.29 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 168,029.17 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 102.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 15.39 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.7 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.89 |
| `process_rss_avg` | job=backend-node | 12,253,184 |
| `process_rss_avg` | job=monitoring-node | 17,638,912 |
| `process_rss_avg` | job=mysql-exporter | 16,446,464 |
| `process_rss_avg` | job=mysql-node | 22,314,496 |
| `process_rss_avg` | job=prometheus | 101,253,120 |
| `process_rss_avg` | job=redis-exporter | 16,823,808 |
| `process_rss_avg` | job=redis-node | 22,872,064 |
| `process_rss_max` | job=backend-node | 15,720,448 |
| `process_rss_max` | job=monitoring-node | 17,772,544 |
| `process_rss_max` | job=mysql-exporter | 16,842,752 |
| `process_rss_max` | job=mysql-node | 22,614,016 |
| `process_rss_max` | job=prometheus | 101,433,344 |
| `process_rss_max` | job=redis-exporter | 17,244,160 |
| `process_rss_max` | job=redis-node | 23,109,632 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 77 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 77 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 99.33 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.19 |
| `node_cpu_pct_avg` | job=mysql-node | 44.81 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 27.41 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.59 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 162,353.14 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 249.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 43,041,280 |
| `node_mem_available_avg` | job=monitoring-node | 398,303,232 |
| `node_mem_available_avg` | job=mysql-node | 257,629,696 |
| `node_mem_available_avg` | job=redis-node | 558,605,312 |
| `node_swap_free_avg` | job=backend-node | 2,116,319,744 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,941,632 |
| `node_swap_free_avg` | job=mysql-node | 2,620,164,096 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 265,349.71 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 654.86 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 157,170.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 2,646.86 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 217,002.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,326.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 69.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-12T13:56:41.318Z ~ 2026-08-12T13:58:41.318Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,010 | 305.88 | 289.66 | 487.92 | 644.95 | 22,677.81 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,951 | 184.18 | 169.49 | 351.62 | 484.07 | 21,140.21 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 11,953 | 231.09 | 213.93 | 414.21 | 552.88 | 21,392.86 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 1,503.06 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,578 | 397.41 | 381.96 | 599.01 | 858.55 | 26,812.19 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,423 | 207.32 | 191.05 | 402.2 | 564.71 | 1,876.13 |
| method=POST, status=401, uri=UNKNOWN | 14 | 42.02 | 33.55 | 120.8 | 131.53 | 327.33 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 294.18 | 447.39 | 494.74 | 498.95 | 478.58 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 52.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.51 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 109.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.88 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.91 |
| `process_rss_avg` | job=backend-node | 8,721,408 |
| `process_rss_avg` | job=monitoring-node | 17,489,920 |
| `process_rss_avg` | job=mysql-exporter | 16,373,760 |
| `process_rss_avg` | job=mysql-node | 22,248,448 |
| `process_rss_avg` | job=prometheus | 101,449,728 |
| `process_rss_avg` | job=redis-exporter | 17,199,104 |
| `process_rss_avg` | job=redis-node | 22,811,648 |
| `process_rss_max` | job=backend-node | 11,427,840 |
| `process_rss_max` | job=monitoring-node | 17,489,920 |
| `process_rss_max` | job=mysql-exporter | 16,900,096 |
| `process_rss_max` | job=mysql-node | 22,441,984 |
| `process_rss_max` | job=prometheus | 101,564,416 |
| `process_rss_max` | job=redis-exporter | 17,477,632 |
| `process_rss_max` | job=redis-node | 22,814,720 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 77 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 77 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 99.77 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 56.22 |
| `node_cpu_pct_avg` | job=redis-node | 0.58 |
| `node_load1_avg` | job=backend-node | 35.77 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.69 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 166,445.71 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 325.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 45,695,488 |
| `node_mem_available_avg` | job=monitoring-node | 398,175,232 |
| `node_mem_available_avg` | job=mysql-node | 266,743,808 |
| `node_mem_available_avg` | job=redis-node | 548,069,376 |
| `node_swap_free_avg` | job=backend-node | 2,110,400,512 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,941,632 |
| `node_swap_free_avg` | job=mysql-node | 2,619,246,080 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 237,457.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 1,386.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 111,724.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 304,141.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,512 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 61.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-12T13:58:41.318Z ~ 2026-08-12T14:00:41.318Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,597 | 774.62 | 380.19 | 1,782.33 | 3,947.79 | 31,395.44 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,589 | 392 | 199.81 | 850.05 | 3,227.19 | 31,139.33 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,944 | 580 | 269.14 | 1,126.75 | 3,494.43 | 31,551.68 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 688 | 995.7 | 507.37 | 2,132.74 | 3,935.86 | 31,208.47 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 873 | 381.17 | 239.11 | 988.46 | 2,244.84 | 29,976.17 |
| method=POST, status=401, uri=UNKNOWN | 8 | 68.47 | 34.95 | 288.95 | 297.79 | 298.32 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 298.92 | 300 | 352.12 | 356.76 | 478.58 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.43 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.43 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 16.29 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 132.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 27.84 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.6 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.05 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 108.43 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.57 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.89 |
| `process_rss_avg` | job=backend-node | 12,613,632 |
| `process_rss_avg` | job=monitoring-node | 17,485,312 |
| `process_rss_avg` | job=mysql-exporter | 16,496,640 |
| `process_rss_avg` | job=mysql-node | 22,383,616 |
| `process_rss_avg` | job=prometheus | 101,368,832 |
| `process_rss_avg` | job=redis-exporter | 17,530,368 |
| `process_rss_avg` | job=redis-node | 22,810,624 |
| `process_rss_max` | job=backend-node | 14,946,304 |
| `process_rss_max` | job=monitoring-node | 17,489,920 |
| `process_rss_max` | job=mysql-exporter | 16,760,832 |
| `process_rss_max` | job=mysql-node | 22,757,376 |
| `process_rss_max` | job=prometheus | 101,564,416 |
| `process_rss_max` | job=redis-exporter | 17,686,528 |
| `process_rss_max` | job=redis-node | 22,810,624 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 77 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 77 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.14 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.6 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.25 |
| `node_cpu_pct_avg` | job=mysql-node | 25.17 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 28.83 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.15 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 156,808 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 464 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 61,658,624 |
| `node_mem_available_avg` | job=monitoring-node | 397,578,752 |
| `node_mem_available_avg` | job=mysql-node | 251,177,984 |
| `node_mem_available_avg` | job=redis-node | 552,331,264 |
| `node_swap_free_avg` | job=backend-node | 2,108,538,368 |
| `node_swap_free_avg` | job=monitoring-node | 3,023,941,632 |
| `node_swap_free_avg` | job=mysql-node | 2,619,348,992 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 263,792 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 1,338.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 198,532.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 123,217.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,536 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 30.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 4.57 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 33 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## round5-pure-throughput-sse1000-rerun2-20260812.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-12T14:25:36.939Z ~ 2026-08-12T14:39:19.328Z
- 설정: `{"sseVUs":1000,"totalSseConnections":2000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-12T14:26:11.939Z ~ 2026-08-12T14:28:11.939Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | N/A | N/A | N/A | N/A | N/A | 789.15 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,147.56 |
| method=POST, status=200, uri=/api/sse/tickets | 186 | 40.13 | 0.76 | 330.4 | 796.81 | 1,407.03 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 2 | 67.53 | 78.29 | 88.36 | 89.25 | 67.53 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.26 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.13 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 60.64 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.51 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.31 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.5 |
| `process_rss_avg` | job=backend-node | 13,489,664 |
| `process_rss_avg` | job=monitoring-node | 16,662,528 |
| `process_rss_avg` | job=mysql-exporter | 16,638,976 |
| `process_rss_avg` | job=mysql-node | 22,210,048 |
| `process_rss_avg` | job=prometheus | 89,706,496 |
| `process_rss_avg` | job=redis-exporter | 18,291,712 |
| `process_rss_avg` | job=redis-node | 22,675,456 |
| `process_rss_max` | job=backend-node | 14,266,368 |
| `process_rss_max` | job=monitoring-node | 16,662,528 |
| `process_rss_max` | job=mysql-exporter | 16,973,824 |
| `process_rss_max` | job=mysql-node | 22,392,832 |
| `process_rss_max` | job=prometheus | 89,886,720 |
| `process_rss_max` | job=redis-exporter | 18,366,464 |
| `process_rss_max` | job=redis-node | 22,773,760 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 801.5 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 603 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 892 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 630 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 94.92 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 2 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 2.98 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 0.02 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 154,017.14 |
| `node_major_fault_delta` | job=monitoring-node | 6.86 |
| `node_major_fault_delta` | job=mysql-node | 51.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 47,366,656 |
| `node_mem_available_avg` | job=monitoring-node | 410,424,320 |
| `node_mem_available_avg` | job=mysql-node | 264,583,680 |
| `node_mem_available_avg` | job=redis-node | 563,953,664 |
| `node_swap_free_avg` | job=backend-node | 2,082,092,032 |
| `node_swap_free_avg` | job=monitoring-node | 3,014,926,336 |
| `node_swap_free_avg` | job=mysql-node | 2,617,950,208 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 293,666.29 |
| `node_swap_in_delta` | job=monitoring-node | 5.71 |
| `node_swap_in_delta` | job=mysql-node | 0 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 170,617.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 182.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 28.38 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-12T14:28:11.939Z ~ 2026-08-12T14:30:11.939Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `process_rss_avg` | job=backend-node | 12,817,920 |
| `process_rss_avg` | job=monitoring-node | 16,662,528 |
| `process_rss_avg` | job=mysql-exporter | 16,328,704 |
| `process_rss_avg` | job=mysql-node | 22,347,776 |
| `process_rss_avg` | job=prometheus | 89,337,856 |
| `process_rss_avg` | job=redis-exporter | 17,447,936 |
| `process_rss_avg` | job=redis-node | 22,773,760 |
| `process_rss_max` | job=backend-node | 15,396,864 |
| `process_rss_max` | job=monitoring-node | 16,662,528 |
| `process_rss_max` | job=mysql-exporter | 16,916,480 |
| `process_rss_max` | job=mysql-node | 22,671,360 |
| `process_rss_max` | job=prometheus | 89,452,544 |
| `process_rss_max` | job=redis-exporter | 17,600,512 |
| `process_rss_max` | job=redis-node | 22,773,760 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 96.57 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 1.12 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 5.3 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 0.02 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 183,856 |
| `node_major_fault_delta` | job=monitoring-node | 22.86 |
| `node_major_fault_delta` | job=mysql-node | 16 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 46,566,912 |
| `node_mem_available_avg` | job=monitoring-node | 410,059,264 |
| `node_mem_available_avg` | job=mysql-node | 262,708,224 |
| `node_mem_available_avg` | job=redis-node | 563,953,664 |
| `node_swap_free_avg` | job=backend-node | 2,057,894,912 |
| `node_swap_free_avg` | job=monitoring-node | 3,014,930,432 |
| `node_swap_free_avg` | job=mysql-node | 2,617,950,208 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 313,627.43 |
| `node_swap_in_delta` | job=monitoring-node | 24 |
| `node_swap_in_delta` | job=mysql-node | 3.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 121,251.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 158.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 22 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-12T14:30:11.939Z ~ 2026-08-12T14:32:11.939Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `process_rss_avg` | job=backend-node | 11,100,672 |
| `process_rss_avg` | job=monitoring-node | 16,872,448 |
| `process_rss_avg` | job=mysql-exporter | 16,358,400 |
| `process_rss_avg` | job=mysql-node | 22,265,856 |
| `process_rss_avg` | job=prometheus | 90,370,048 |
| `process_rss_avg` | job=redis-exporter | 17,849,344 |
| `process_rss_avg` | job=redis-node | 22,773,760 |
| `process_rss_max` | job=backend-node | 14,594,048 |
| `process_rss_max` | job=monitoring-node | 16,924,672 |
| `process_rss_max` | job=mysql-exporter | 17,047,552 |
| `process_rss_max` | job=mysql-node | 22,499,328 |
| `process_rss_max` | job=prometheus | 90,501,120 |
| `process_rss_max` | job=redis-exporter | 18,399,232 |
| `process_rss_max` | job=redis-node | 22,773,760 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 87.99 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 1.1 |
| `node_cpu_pct_avg` | job=redis-node | 0.39 |
| `node_load1_avg` | job=backend-node | 4.66 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 0.05 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 210,730.29 |
| `node_major_fault_delta` | job=monitoring-node | 17.14 |
| `node_major_fault_delta` | job=mysql-node | 3.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 50,950,144 |
| `node_mem_available_avg` | job=monitoring-node | 407,572,992 |
| `node_mem_available_avg` | job=mysql-node | 257,979,392 |
| `node_mem_available_avg` | job=redis-node | 563,963,904 |
| `node_swap_free_avg` | job=backend-node | 2,052,591,104 |
| `node_swap_free_avg` | job=monitoring-node | 3,014,930,432 |
| `node_swap_free_avg` | job=mysql-node | 2,617,950,208 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 298,819.43 |
| `node_swap_in_delta` | job=monitoring-node | 64 |
| `node_swap_in_delta` | job=mysql-node | 3.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 128,822.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 158.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 19.13 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-12T14:32:11.939Z ~ 2026-08-12T14:34:11.939Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `process_rss_avg` | job=backend-node | 11,563,520 |
| `process_rss_avg` | job=monitoring-node | 16,785,408 |
| `process_rss_avg` | job=mysql-exporter | 16,618,496 |
| `process_rss_avg` | job=mysql-node | 22,260,736 |
| `process_rss_avg` | job=prometheus | 89,481,216 |
| `process_rss_avg` | job=redis-exporter | 17,761,280 |
| `process_rss_avg` | job=redis-node | 22,872,064 |
| `process_rss_max` | job=backend-node | 12,681,216 |
| `process_rss_max` | job=monitoring-node | 16,900,096 |
| `process_rss_max` | job=mysql-exporter | 16,814,080 |
| `process_rss_max` | job=mysql-node | 22,429,696 |
| `process_rss_max` | job=prometheus | 89,530,368 |
| `process_rss_max` | job=redis-exporter | 18,206,720 |
| `process_rss_max` | job=redis-node | 22,904,832 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 86.99 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 1.13 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 3.06 |
| `node_load1_avg` | job=monitoring-node | 0.05 |
| `node_load1_avg` | job=mysql-node | 0 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 188,494.86 |
| `node_major_fault_delta` | job=monitoring-node | 25.14 |
| `node_major_fault_delta` | job=mysql-node | 68.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 70,829,568 |
| `node_mem_available_avg` | job=monitoring-node | 410,897,920 |
| `node_mem_available_avg` | job=mysql-node | 257,190,912 |
| `node_mem_available_avg` | job=redis-node | 563,975,168 |
| `node_swap_free_avg` | job=backend-node | 2,038,428,672 |
| `node_swap_free_avg` | job=monitoring-node | 3,015,081,984 |
| `node_swap_free_avg` | job=mysql-node | 2,617,950,208 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 303,632 |
| `node_swap_in_delta` | job=monitoring-node | 11.43 |
| `node_swap_in_delta` | job=mysql-node | 90.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 79,502.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 158.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 19 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-12T14:34:11.939Z ~ 2026-08-12T14:36:11.939Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `process_rss_avg` | job=backend-node | 10,890,752 |
| `process_rss_avg` | job=monitoring-node | 16,660,992 |
| `process_rss_avg` | job=mysql-exporter | 16,517,120 |
| `process_rss_avg` | job=mysql-node | 22,283,776 |
| `process_rss_avg` | job=prometheus | 89,718,784 |
| `process_rss_avg` | job=redis-exporter | 17,343,488 |
| `process_rss_avg` | job=redis-node | 22,753,280 |
| `process_rss_max` | job=backend-node | 16,113,664 |
| `process_rss_max` | job=monitoring-node | 16,723,968 |
| `process_rss_max` | job=mysql-exporter | 16,805,888 |
| `process_rss_max` | job=mysql-node | 22,446,080 |
| `process_rss_max` | job=prometheus | 89,718,784 |
| `process_rss_max` | job=redis-exporter | 17,457,152 |
| `process_rss_max` | job=redis-node | 22,753,280 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 91.91 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.1 |
| `node_cpu_pct_avg` | job=mysql-node | 1.1 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 3.46 |
| `node_load1_avg` | job=monitoring-node | 0.18 |
| `node_load1_avg` | job=mysql-node | 0 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 198,512 |
| `node_major_fault_delta` | job=monitoring-node | 6.86 |
| `node_major_fault_delta` | job=mysql-node | 11.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 44,688,896 |
| `node_mem_available_avg` | job=monitoring-node | 413,411,840 |
| `node_mem_available_avg` | job=mysql-node | 256,628,736 |
| `node_mem_available_avg` | job=redis-node | 563,637,760 |
| `node_swap_free_avg` | job=backend-node | 2,032,529,408 |
| `node_swap_free_avg` | job=monitoring-node | 3,015,081,984 |
| `node_swap_free_avg` | job=mysql-node | 2,617,950,208 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 308,937.14 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 8 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 102,539.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 161.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 19 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-12T14:36:11.939Z ~ 2026-08-12T14:38:11.939Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `process_rss_avg` | job=backend-node | 12,464,640 |
| `process_rss_avg` | job=monitoring-node | 16,630,784 |
| `process_rss_avg` | job=mysql-exporter | 16,582,656 |
| `process_rss_avg` | job=mysql-node | 22,271,488 |
| `process_rss_avg` | job=prometheus | 89,718,784 |
| `process_rss_avg` | job=redis-exporter | 17,788,928 |
| `process_rss_avg` | job=redis-node | 22,753,280 |
| `process_rss_max` | job=backend-node | 16,633,856 |
| `process_rss_max` | job=monitoring-node | 16,687,104 |
| `process_rss_max` | job=mysql-exporter | 17,072,128 |
| `process_rss_max` | job=mysql-node | 22,413,312 |
| `process_rss_max` | job=prometheus | 89,718,784 |
| `process_rss_max` | job=redis-exporter | 18,100,224 |
| `process_rss_max` | job=redis-node | 22,753,280 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 93.35 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 1.08 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 3.53 |
| `node_load1_avg` | job=monitoring-node | 0.11 |
| `node_load1_avg` | job=mysql-node | 0 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 130,350.86 |
| `node_major_fault_delta` | job=monitoring-node | 109.71 |
| `node_major_fault_delta` | job=mysql-node | 0 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 46,114,304 |
| `node_mem_available_avg` | job=monitoring-node | 414,566,912 |
| `node_mem_available_avg` | job=mysql-node | 256,533,504 |
| `node_mem_available_avg` | job=redis-node | 563,359,744 |
| `node_swap_free_avg` | job=backend-node | 2,029,388,288 |
| `node_swap_free_avg` | job=monitoring-node | 3,015,081,984 |
| `node_swap_free_avg` | job=mysql-node | 2,617,950,208 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 220,993.14 |
| `node_swap_in_delta` | job=monitoring-node | 36.57 |
| `node_swap_in_delta` | job=mysql-node | 0 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 80,026.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 161.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 18.13 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## round5-hot-auction-pattern-sse250-20260813.json

- 시나리오: `hot-auction-pattern`
- K6 실행: 2026-08-12T15:58:08.028Z ~ 2026-08-12T16:06:10.211Z
- 설정: `{"auctionCount":200,"hotAuctionCount":3,"hotAuctionRate":14,"coldAuctionRatePerAuction":0.09,"coldAuctionRate":18,"sseUsers":250,"totalSseConnections":500,"duration":"5m"}`

### 0~1분 — 2026-08-12T15:59:13.028Z ~ 2026-08-12T16:00:13.028Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 1,103 | 446.74 | 429.17 | 788.98 | 938.99 | 24,888 |
| method=GET, status=200, uri=/api/test/load/sse-status | 3 | 6.06 | 5.59 | 8.25 | 8.36 | 356.3 |
| method=GET, status=400, uri=/api/auctions/stream | 1,533 | 3.61 | 0.63 | 9.09 | 99.25 | 1,086.98 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 338.03 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 855.03 |
| method=POST, status=401, uri=UNKNOWN | 7 | 26.75 | 4.89 | 128.63 | 133.1 | 119.54 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 21.33 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.95 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 97.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 98 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.24 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.37 |
| `process_rss_avg` | job=backend-node | 14,462,976 |
| `process_rss_avg` | job=monitoring-node | 16,803,840 |
| `process_rss_avg` | job=mysql-exporter | 16,258,048 |
| `process_rss_avg` | job=mysql-node | 22,534,144 |
| `process_rss_avg` | job=prometheus | 97,984,512 |
| `process_rss_avg` | job=redis-exporter | 18,448,384 |
| `process_rss_avg` | job=redis-node | 22,716,416 |
| `process_rss_max` | job=backend-node | 15,106,048 |
| `process_rss_max` | job=monitoring-node | 16,822,272 |
| `process_rss_max` | job=mysql-exporter | 16,588,800 |
| `process_rss_max` | job=mysql-node | 22,634,496 |
| `process_rss_max` | job=prometheus | 98,246,656 |
| `process_rss_max` | job=redis-exporter | 18,673,664 |
| `process_rss_max` | job=redis-node | 22,716,416 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 98.44 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.13 |
| `node_cpu_pct_avg` | job=mysql-node | 19.58 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 7.59 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.39 |
| `node_load1_avg` | job=redis-node | 0.02 |
| `node_major_fault_delta` | job=backend-node | 72,970.67 |
| `node_major_fault_delta` | job=monitoring-node | 4 |
| `node_major_fault_delta` | job=mysql-node | 61.33 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 63,038,464 |
| `node_mem_available_avg` | job=monitoring-node | 413,360,128 |
| `node_mem_available_avg` | job=mysql-node | 255,605,760 |
| `node_mem_available_avg` | job=redis-node | 562,343,936 |
| `node_swap_free_avg` | job=backend-node | 1,977,226,240 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,162,752 |
| `node_swap_free_avg` | job=mysql-node | 2,614,501,376 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 154,225.33 |
| `node_swap_in_delta` | job=monitoring-node | 4 |
| `node_swap_in_delta` | job=mysql-node | 148 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 112,942.67 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 13,625.33 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 18 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 24 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 11 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 1~2분 — 2026-08-12T16:00:13.028Z ~ 2026-08-12T16:01:13.028Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 643 | 1,150.76 | 1,010.53 | 4,424.5 | 5,791.05 | 24,888 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 356.3 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 1,086.98 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 338.03 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 855.03 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 44 | 1,713.67 | 1,252.7 | 5,332.92 | 5,647.88 | 17,600.18 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 241 | 563.38 | 324.13 | 2,738.04 | 5,208.36 | 17,170.03 |
| method=POST, status=401, uri=UNKNOWN | 3 | 0.51 | 0.5 | 0.95 | 0.99 | 119.54 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 761.95 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 18.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.97 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.19 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.41 |
| `process_rss_avg` | job=backend-node | 14,341,120 |
| `process_rss_avg` | job=monitoring-node | 16,928,768 |
| `process_rss_avg` | job=mysql-exporter | 16,499,712 |
| `process_rss_avg` | job=mysql-node | 22,495,232 |
| `process_rss_avg` | job=prometheus | 99,000,320 |
| `process_rss_avg` | job=redis-exporter | 18,526,208 |
| `process_rss_avg` | job=redis-node | 22,716,416 |
| `process_rss_max` | job=backend-node | 14,692,352 |
| `process_rss_max` | job=monitoring-node | 16,928,768 |
| `process_rss_max` | job=mysql-exporter | 16,838,656 |
| `process_rss_max` | job=mysql-node | 22,585,344 |
| `process_rss_max` | job=prometheus | 99,033,088 |
| `process_rss_max` | job=redis-exporter | 18,747,392 |
| `process_rss_max` | job=redis-node | 22,716,416 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 96.68 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.26 |
| `node_cpu_pct_avg` | job=mysql-node | 24.24 |
| `node_cpu_pct_avg` | job=redis-node | 0.39 |
| `node_load1_avg` | job=backend-node | 11.02 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 3.83 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 60,721.33 |
| `node_major_fault_delta` | job=monitoring-node | 1.33 |
| `node_major_fault_delta` | job=mysql-node | 38.67 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 57,212,928 |
| `node_mem_available_avg` | job=monitoring-node | 413,547,520 |
| `node_mem_available_avg` | job=mysql-node | 253,974,528 |
| `node_mem_available_avg` | job=redis-node | 562,225,152 |
| `node_swap_free_avg` | job=backend-node | 1,946,399,744 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,162,752 |
| `node_swap_free_avg` | job=mysql-node | 2,614,501,376 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 137,456 |
| `node_swap_in_delta` | job=monitoring-node | 1.33 |
| `node_swap_in_delta` | job=mysql-node | 129.33 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 89,112 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 10,382.67 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 16,277.33 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 30.67 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 6.67 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 2~3분 — 2026-08-12T16:01:13.028Z ~ 2026-08-12T16:02:13.028Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,417 | 425.35 | 337.99 | 921.15 | 1,425.72 | 24,888 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 7.93 |
| method=GET, status=400, uri=/api/auctions/stream | 996 | 15.92 | 1.91 | 15.53 | 772.42 | 941.9 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 1.6 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 485 | 526.03 | 290.14 | 1,209.75 | 7,547.69 | 17,600.18 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 3,168 | 412.62 | 255.35 | 966.37 | 6,262.47 | 17,170.03 |
| method=POST, status=401, uri=UNKNOWN | 8 | 8 | 5.59 | 20.69 | 22.03 | 119.54 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 13 | 245.46 | 201.33 | 473.7 | 494.74 | 761.95 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 84 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 13.19 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 109 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 109 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.53 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.63 |
| `process_rss_avg` | job=backend-node | 10,539,008 |
| `process_rss_avg` | job=monitoring-node | 16,928,768 |
| `process_rss_avg` | job=mysql-exporter | 16,408,576 |
| `process_rss_avg` | job=mysql-node | 22,571,008 |
| `process_rss_avg` | job=prometheus | 99,098,624 |
| `process_rss_avg` | job=redis-exporter | 17,740,800 |
| `process_rss_avg` | job=redis-node | 22,749,184 |
| `process_rss_max` | job=backend-node | 12,029,952 |
| `process_rss_max` | job=monitoring-node | 16,928,768 |
| `process_rss_max` | job=mysql-exporter | 16,961,536 |
| `process_rss_max` | job=mysql-node | 22,786,048 |
| `process_rss_max` | job=prometheus | 99,295,232 |
| `process_rss_max` | job=redis-exporter | 18,452,480 |
| `process_rss_max` | job=redis-node | 22,847,488 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 96.91 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.1 |
| `node_cpu_pct_avg` | job=mysql-node | 75.49 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 8.42 |
| `node_load1_avg` | job=monitoring-node | 0.07 |
| `node_load1_avg` | job=mysql-node | 8.46 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 81,245.33 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 146.67 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 55,998,464 |
| `node_mem_available_avg` | job=monitoring-node | 413,180,928 |
| `node_mem_available_avg` | job=mysql-node | 255,393,792 |
| `node_mem_available_avg` | job=redis-node | 562,114,560 |
| `node_swap_free_avg` | job=backend-node | 1,934,195,712 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,162,752 |
| `node_swap_free_avg` | job=mysql-node | 2,614,503,424 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 140,892 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 588 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 75,818.67 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 85,662.67 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 9 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 462,049.33 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 2,273.33 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 29.33 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 18 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 3~4분 — 2026-08-12T16:02:13.028Z ~ 2026-08-12T16:03:13.028Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,049 | 358.68 | 308.31 | 771.39 | 1,415.02 | 24,888 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 3,625 | 2.58 | 0.72 | 6.29 | 31.34 | 941.9 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 891 | 317.55 | 242.34 | 779.95 | 1,629.7 | 17,600.18 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 3,712 | 351.66 | 269.2 | 949.59 | 1,865.32 | 17,170.03 |
| method=POST, status=401, uri=UNKNOWN | 19 | 1.46 | 1 | 4.07 | 4.17 | 119.54 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 12 | 299.67 | 284.22 | 765.04 | 797.25 | 761.95 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21.25 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 62.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 10.5 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.52 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.66 |
| `process_rss_avg` | job=backend-node | 10,711,040 |
| `process_rss_avg` | job=monitoring-node | 16,928,768 |
| `process_rss_avg` | job=mysql-exporter | 16,848,896 |
| `process_rss_avg` | job=mysql-node | 22,543,360 |
| `process_rss_avg` | job=prometheus | 99,786,752 |
| `process_rss_avg` | job=redis-exporter | 18,006,016 |
| `process_rss_avg` | job=redis-node | 22,847,488 |
| `process_rss_max` | job=backend-node | 14,974,976 |
| `process_rss_max` | job=monitoring-node | 16,928,768 |
| `process_rss_max` | job=mysql-exporter | 17,203,200 |
| `process_rss_max` | job=mysql-node | 22,712,320 |
| `process_rss_max` | job=prometheus | 99,819,520 |
| `process_rss_max` | job=redis-exporter | 18,202,624 |
| `process_rss_max` | job=redis-node | 22,847,488 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 95.06 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 77.3 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 7.23 |
| `node_load1_avg` | job=monitoring-node | 0.06 |
| `node_load1_avg` | job=mysql-node | 13.33 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 72,965.33 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 158.67 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 55,473,152 |
| `node_mem_available_avg` | job=monitoring-node | 412,300,288 |
| `node_mem_available_avg` | job=mysql-node | 256,847,872 |
| `node_mem_available_avg` | job=redis-node | 562,114,560 |
| `node_swap_free_avg` | job=backend-node | 1,916,297,216 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,162,752 |
| `node_swap_free_avg` | job=mysql-node | 2,614,505,472 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 122,848 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 732 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 96,637.33 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 111,889.33 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 10 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 485,126.67 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 2,680 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 60 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 13.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 4~5분 — 2026-08-12T16:03:13.028Z ~ 2026-08-12T16:04:13.028Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,637 | 401.17 | 318.76 | 959.74 | 2,203.45 | 7,798.11 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 5,585 | 6.05 | 0.7 | 17.37 | 57.97 | 1,065.99 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 912 | 324.87 | 253.9 | 819.62 | 1,679.81 | 8,078.39 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,641 | 357.27 | 291.62 | 909.51 | 1,758.72 | 7,900.65 |
| method=POST, status=401, uri=UNKNOWN | 27 | 4.32 | 0.91 | 5.59 | 60.4 | 58.47 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 12 | 410.01 | 190.14 | 1,628.51 | 1,757.36 | 1,563.15 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 46.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 8.12 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.39 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.52 |
| `process_rss_avg` | job=backend-node | 13,408,256 |
| `process_rss_avg` | job=monitoring-node | 16,826,368 |
| `process_rss_avg` | job=mysql-exporter | 16,467,968 |
| `process_rss_avg` | job=mysql-node | 22,564,864 |
| `process_rss_avg` | job=prometheus | 99,819,520 |
| `process_rss_avg` | job=redis-exporter | 17,921,024 |
| `process_rss_avg` | job=redis-node | 22,763,520 |
| `process_rss_max` | job=backend-node | 16,285,696 |
| `process_rss_max` | job=monitoring-node | 16,891,904 |
| `process_rss_max` | job=mysql-exporter | 16,588,800 |
| `process_rss_max` | job=mysql-node | 22,761,472 |
| `process_rss_max` | job=prometheus | 99,819,520 |
| `process_rss_max` | job=redis-exporter | 18,202,624 |
| `process_rss_max` | job=redis-node | 22,847,488 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 94.86 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.07 |
| `node_cpu_pct_avg` | job=mysql-node | 57.62 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 7.97 |
| `node_load1_avg` | job=monitoring-node | 0.09 |
| `node_load1_avg` | job=mysql-node | 15.33 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 60,700 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 104 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 61,508,608 |
| `node_mem_available_avg` | job=monitoring-node | 412,136,448 |
| `node_mem_available_avg` | job=mysql-node | 255,736,832 |
| `node_mem_available_avg` | job=redis-node | 562,114,560 |
| `node_swap_free_avg` | job=backend-node | 1,899,850,752 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,162,752 |
| `node_swap_free_avg` | job=mysql-node | 2,614,509,568 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 106,949.33 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 582.67 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 109,138.67 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 92,805.33 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 5 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 351,158.67 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 2,044 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 30.67 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 13.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## round5-bid-only-load-noSSE-20260813.json

- 시나리오: `bid-only-load (SSE 없음)`
- K6 실행: 2026-08-12T16:06:55.712Z ~ 2026-08-12T16:19:13.640Z
- 설정: `{"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m","hotAuctionId":null}`

### QPS 50 — 2026-08-12T16:06:55.712Z ~ 2026-08-12T16:08:55.712Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,030 | 81.97 | 15.98 | 349.84 | 665.27 | 11,443 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,009 | 45.56 | 7.06 | 399.65 | 756.54 | 8,076.71 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,403 | 162.68 | 12.89 | 502.39 | 1,634.04 | 8,232.44 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 525,601.99 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 2,214.57 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 571 | 239.07 | 36.06 | 273.7 | 5,368.71 | 4,893.11 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,087 | 202.27 | 21.59 | 1,232.92 | 1,841.13 | 8,753.86 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 54 | 306.63 | 194.62 | 952.95 | 2,337.18 | 11,509.08 |
| method=POST, status=401, uri=UNKNOWN | 63 | 3.05 | 0.65 | 11.36 | 77.18 | 100.78 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 6.51 | 7.04 | 8.34 | 26.27 | 7.45 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 64 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.17 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.11 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.7 |
| `process_rss_avg` | job=backend-node | 14,491,136 |
| `process_rss_avg` | job=monitoring-node | 16,801,792 |
| `process_rss_avg` | job=mysql-exporter | 16,251,392 |
| `process_rss_avg` | job=mysql-node | 22,498,304 |
| `process_rss_avg` | job=prometheus | 100,343,808 |
| `process_rss_avg` | job=redis-exporter | 18,087,936 |
| `process_rss_avg` | job=redis-node | 22,901,248 |
| `process_rss_max` | job=backend-node | 15,704,064 |
| `process_rss_max` | job=monitoring-node | 16,982,016 |
| `process_rss_max` | job=mysql-exporter | 16,711,680 |
| `process_rss_max` | job=mysql-node | 22,663,168 |
| `process_rss_max` | job=prometheus | 100,343,808 |
| `process_rss_max` | job=redis-exporter | 18,202,624 |
| `process_rss_max` | job=redis-node | 23,072,768 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 16 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 60.87 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.11 |
| `node_cpu_pct_avg` | job=mysql-node | 24.73 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 6.02 |
| `node_load1_avg` | job=monitoring-node | 0.07 |
| `node_load1_avg` | job=mysql-node | 0.85 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 69,939.43 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 208 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 72,357,888 |
| `node_mem_available_avg` | job=monitoring-node | 411,834,368 |
| `node_mem_available_avg` | job=mysql-node | 250,479,616 |
| `node_mem_available_avg` | job=redis-node | 562,122,752 |
| `node_swap_free_avg` | job=backend-node | 1,873,043,456 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,162,752 |
| `node_swap_free_avg` | job=mysql-node | 2,614,527,488 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 144,960 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 1,091.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 85,870.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 109,660.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 2 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 8,852.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 696 |
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

### QPS 100 — 2026-08-12T16:08:55.712Z ~ 2026-08-12T16:10:55.712Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,669 | 27.8 | 13.15 | 83.89 | 379.39 | 11,443 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,690 | 25.59 | 6.35 | 123.1 | 486.15 | 8,076.71 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,039 | 26.52 | 10.88 | 105 | 362.2 | 8,232.44 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 525,601.99 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 4,893.11 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,651 | 52.58 | 20.38 | 232.08 | 757.21 | 8,753.86 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 18 | 273.07 | 100 | 733.72 | 790.99 | 2,341.61 |
| method=POST, status=401, uri=UNKNOWN | 77 | 0.84 | 0.56 | 2.32 | 15.84 | 80.09 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 44.03 | 6.06 | 334.75 | 353.28 | 310.69 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.63 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 35.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.99 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.16 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.24 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_rss_avg` | job=backend-node | 14,920,704 |
| `process_rss_avg` | job=monitoring-node | 16,949,248 |
| `process_rss_avg` | job=mysql-exporter | 16,482,304 |
| `process_rss_avg` | job=mysql-node | 22,507,008 |
| `process_rss_avg` | job=prometheus | 100,409,344 |
| `process_rss_avg` | job=redis-exporter | 18,202,624 |
| `process_rss_avg` | job=redis-node | 22,737,408 |
| `process_rss_max` | job=backend-node | 18,149,376 |
| `process_rss_max` | job=monitoring-node | 16,982,016 |
| `process_rss_max` | job=mysql-exporter | 16,887,808 |
| `process_rss_max` | job=mysql-node | 22,634,496 |
| `process_rss_max` | job=prometheus | 100,605,952 |
| `process_rss_max` | job=redis-exporter | 18,202,624 |
| `process_rss_max` | job=redis-node | 23,179,264 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 43.74 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.11 |
| `node_cpu_pct_avg` | job=mysql-node | 35.41 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 2.15 |
| `node_load1_avg` | job=monitoring-node | 0.05 |
| `node_load1_avg` | job=mysql-node | 0.53 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 55,901.71 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 281.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,660,800 |
| `node_mem_available_avg` | job=monitoring-node | 411,338,240 |
| `node_mem_available_avg` | job=mysql-node | 241,774,592 |
| `node_mem_available_avg` | job=redis-node | 562,414,080 |
| `node_swap_free_avg` | job=backend-node | 1,889,890,816 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,162,752 |
| `node_swap_free_avg` | job=mysql-node | 2,614,533,632 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 119,002.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 1,515.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 66,521.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 158,178.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,845.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 14.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 1.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-12T16:10:55.712Z ~ 2026-08-12T16:12:55.712Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,831 | 13.16 | 12.75 | 16.61 | 22.46 | 1,275.22 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,923 | 6.37 | 6.05 | 9.04 | 12.78 | 944.83 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,633 | 11.35 | 10.78 | 15 | 25.97 | 1,327.64 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 128.54 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,859 | 21.06 | 20.13 | 27.5 | 38.02 | 1,406.42 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 5 | 8.29 | 7.69 | 12.3 | 12.53 | 796.92 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.49 | 0.57 | 1.66 | 2.67 | 15.68 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 13 | 6.82 | 7.17 | 9.4 | 9.71 | 310.69 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 34.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.54 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.36 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.39 |
| `process_rss_avg` | job=backend-node | 14,573,056 |
| `process_rss_avg` | job=monitoring-node | 16,977,920 |
| `process_rss_avg` | job=mysql-exporter | 16,454,144 |
| `process_rss_avg` | job=mysql-node | 22,494,720 |
| `process_rss_avg` | job=prometheus | 100,687,872 |
| `process_rss_avg` | job=redis-exporter | 17,530,368 |
| `process_rss_avg` | job=redis-node | 22,749,184 |
| `process_rss_max` | job=backend-node | 15,564,800 |
| `process_rss_max` | job=monitoring-node | 16,977,920 |
| `process_rss_max` | job=mysql-exporter | 16,756,736 |
| `process_rss_max` | job=mysql-node | 22,585,344 |
| `process_rss_max` | job=prometheus | 100,737,024 |
| `process_rss_max` | job=redis-exporter | 18,112,512 |
| `process_rss_max` | job=redis-node | 22,749,184 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 44.67 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.12 |
| `node_cpu_pct_avg` | job=mysql-node | 57.46 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 1.13 |
| `node_load1_avg` | job=monitoring-node | 0.05 |
| `node_load1_avg` | job=mysql-node | 1.46 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 5,965.71 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 389.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 70,478,848 |
| `node_mem_available_avg` | job=monitoring-node | 410,707,968 |
| `node_mem_available_avg` | job=mysql-node | 247,859,200 |
| `node_mem_available_avg` | job=redis-node | 562,869,248 |
| `node_swap_free_avg` | job=backend-node | 1,940,047,360 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,162,752 |
| `node_swap_free_avg` | job=mysql-node | 2,614,482,432 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 9,801.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 1,371.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 2,426.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 3,342.86 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 270,097.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 0 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 0 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-12T16:12:55.712Z ~ 2026-08-12T16:14:55.712Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,659 | 48.27 | 13.57 | 107.77 | 586.38 | 7,083.14 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,589 | 23.69 | 6.04 | 34.67 | 288.95 | 4,214.5 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 10,891 | 38.09 | 11.4 | 83.81 | 404.22 | 6,978.1 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 128.54 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,430 | 29.52 | 22.98 | 37.41 | 134.14 | 6,998.82 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 22 | 54.8 | 7.51 | 501.84 | 529.87 | 507.46 |
| method=POST, status=401, uri=UNKNOWN | 71 | 2.61 | 0.53 | 1 | 142.72 | 138.23 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 7.56 | 6.64 | 16.22 | 16.67 | 15.87 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 54.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.6 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 106 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.43 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.5 |
| `process_rss_avg` | job=backend-node | 14,872,064 |
| `process_rss_avg` | job=monitoring-node | 16,904,192 |
| `process_rss_avg` | job=mysql-exporter | 16,590,848 |
| `process_rss_avg` | job=mysql-node | 22,416,384 |
| `process_rss_avg` | job=prometheus | 100,966,400 |
| `process_rss_avg` | job=redis-exporter | 18,386,944 |
| `process_rss_avg` | job=redis-node | 22,749,184 |
| `process_rss_max` | job=backend-node | 15,306,752 |
| `process_rss_max` | job=monitoring-node | 17,068,032 |
| `process_rss_max` | job=mysql-exporter | 16,941,056 |
| `process_rss_max` | job=mysql-node | 22,642,688 |
| `process_rss_max` | job=prometheus | 101,261,312 |
| `process_rss_max` | job=redis-exporter | 18,616,320 |
| `process_rss_max` | job=redis-node | 22,749,184 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 14.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 62.57 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 65.37 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 1.26 |
| `node_load1_avg` | job=monitoring-node | 0.09 |
| `node_load1_avg` | job=mysql-node | 2.84 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 22,488 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 486.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 73,016,320 |
| `node_mem_available_avg` | job=monitoring-node | 410,544,128 |
| `node_mem_available_avg` | job=mysql-node | 247,775,232 |
| `node_mem_available_avg` | job=redis-node | 562,527,232 |
| `node_swap_free_avg` | job=backend-node | 1,957,125,120 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,162,752 |
| `node_swap_free_avg` | job=mysql-node | 2,614,455,808 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 51,874.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 2,265.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 28,802.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 335,232 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 139.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 5.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 1.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-12T16:14:55.712Z ~ 2026-08-12T16:16:55.712Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 5,781 | 62.28 | 33.14 | 198.46 | 283.65 | 7,083.14 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 5,955 | 33.75 | 12.12 | 140.41 | 235.46 | 4,214.5 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 17,618 | 56.58 | 27.1 | 188.58 | 273.23 | 6,978.1 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 5,752 | 85.76 | 47.96 | 244.09 | 343.85 | 6,998.82 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 235 | 106.99 | 85 | 248.3 | 299.05 | 1,105.47 |
| method=POST, status=401, uri=UNKNOWN | 80 | 1.44 | 0.76 | 6.29 | 9.3 | 138.23 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 14 | 20.38 | 15.38 | 43.06 | 44.4 | 213.29 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.25 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 69.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 4.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.7 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.73 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.82 |
| `process_rss_avg` | job=backend-node | 13,559,296 |
| `process_rss_avg` | job=monitoring-node | 17,068,032 |
| `process_rss_avg` | job=mysql-exporter | 16,758,784 |
| `process_rss_avg` | job=mysql-node | 22,427,648 |
| `process_rss_avg` | job=prometheus | 101,326,848 |
| `process_rss_avg` | job=redis-exporter | 17,689,088 |
| `process_rss_avg` | job=redis-node | 22,749,184 |
| `process_rss_max` | job=backend-node | 14,876,672 |
| `process_rss_max` | job=monitoring-node | 17,068,032 |
| `process_rss_max` | job=mysql-exporter | 17,117,184 |
| `process_rss_max` | job=mysql-node | 22,601,728 |
| `process_rss_max` | job=prometheus | 101,523,456 |
| `process_rss_max` | job=redis-exporter | 18,309,120 |
| `process_rss_max` | job=redis-node | 22,749,184 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 20.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 82.6 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.1 |
| `node_cpu_pct_avg` | job=mysql-node | 92.2 |
| `node_cpu_pct_avg` | job=redis-node | 0.38 |
| `node_load1_avg` | job=backend-node | 6.99 |
| `node_load1_avg` | job=monitoring-node | 0.13 |
| `node_load1_avg` | job=mysql-node | 7 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 13,884.57 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 648 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 76,341,248 |
| `node_mem_available_avg` | job=monitoring-node | 410,079,232 |
| `node_mem_available_avg` | job=mysql-node | 237,457,920 |
| `node_mem_available_avg` | job=redis-node | 563,077,120 |
| `node_swap_free_avg` | job=backend-node | 1,948,436,992 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,162,752 |
| `node_swap_free_avg` | job=mysql-node | 2,614,483,968 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 24,136 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 2,977.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 7,336 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 545,848 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 4,826.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 113.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 7.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-12T16:16:55.712Z ~ 2026-08-12T16:18:55.712Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 6,129 | 155.9 | 147.1 | 280.44 | 351.47 | 7,083.14 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 6,034 | 98.21 | 81.91 | 220.32 | 297.51 | 4,214.5 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 18,007 | 144.16 | 128.61 | 269.83 | 351.62 | 4,488.33 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,297 | 200.76 | 190.26 | 337.7 | 423.95 | 6,998.82 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,126 | 107.62 | 94.24 | 233.84 | 300.58 | 1,105.47 |
| method=POST, status=401, uri=UNKNOWN | 33 | 2 | 1.22 | 5.98 | 6.79 | 138.23 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 17 | 132.2 | 142.61 | 251.66 | 265.08 | 249.13 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.57 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.43 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.43 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 78.93 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.24 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107.43 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.82 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.84 |
| `process_rss_avg` | job=backend-node | 14,301,184 |
| `process_rss_avg` | job=monitoring-node | 16,922,624 |
| `process_rss_avg` | job=mysql-exporter | 16,668,160 |
| `process_rss_avg` | job=mysql-node | 22,405,632 |
| `process_rss_avg` | job=prometheus | 101,284,864 |
| `process_rss_avg` | job=redis-exporter | 17,427,968 |
| `process_rss_avg` | job=redis-node | 22,781,952 |
| `process_rss_max` | job=backend-node | 14,852,096 |
| `process_rss_max` | job=monitoring-node | 17,068,032 |
| `process_rss_max` | job=mysql-exporter | 17,317,888 |
| `process_rss_max` | job=mysql-node | 22,568,960 |
| `process_rss_max` | job=prometheus | 101,523,456 |
| `process_rss_max` | job=redis-exporter | 18,149,376 |
| `process_rss_max` | job=redis-node | 23,011,328 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 94.03 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.13 |
| `node_cpu_pct_avg` | job=mysql-node | 82.67 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 14.83 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 12.04 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 22,529.14 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 38.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 61,974,016 |
| `node_mem_available_avg` | job=monitoring-node | 409,905,152 |
| `node_mem_available_avg` | job=mysql-node | 233,748,992 |
| `node_mem_available_avg` | job=redis-node | 562,737,664 |
| `node_swap_free_avg` | job=backend-node | 1,960,325,120 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,430,016 |
| `node_swap_free_avg` | job=mysql-node | 2,614,513,664 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 53,156.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 36.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 28,452.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 521,533.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 8,595.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 177.14 |
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

---

## round5-bid-only-load-singleHotAuction-20260813.json

- 시나리오: `bid-only-load (SSE 없음)`
- K6 실행: 2026-08-12T16:19:46.444Z ~ 2026-08-12T16:31:59.828Z
- 설정: `{"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m","hotAuctionId":3001001}`

### QPS 50 — 2026-08-12T16:19:46.444Z ~ 2026-08-12T16:21:46.444Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,162 | 85.53 | 15.17 | 427.13 | 843.38 | 16,221.11 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,238 | 59 | 7.32 | 310.4 | 513.98 | 15,864.84 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,600 | 188.74 | 44.27 | 786.67 | 1,112.89 | 15,918.37 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 760.39 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,160.4 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 859 | 33.14 | 25.71 | 35.6 | 212.06 | 15,792.14 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 339 | 461.36 | 470.85 | 1,160.47 | 1,576.97 | 15,690.86 |
| method=POST, status=401, uri=UNKNOWN | 71 | 0.68 | 0.53 | 1.36 | 18.9 | 52.02 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 249.13 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 44.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.45 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 109 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.28 |
| `process_rss_avg` | job=backend-node | 15,799,296 |
| `process_rss_avg` | job=monitoring-node | 16,775,680 |
| `process_rss_avg` | job=mysql-exporter | 16,398,848 |
| `process_rss_avg` | job=mysql-node | 22,550,528 |
| `process_rss_avg` | job=prometheus | 102,211,584 |
| `process_rss_avg` | job=redis-exporter | 17,883,136 |
| `process_rss_avg` | job=redis-node | 22,639,616 |
| `process_rss_max` | job=backend-node | 17,924,096 |
| `process_rss_max` | job=monitoring-node | 16,777,216 |
| `process_rss_max` | job=mysql-exporter | 16,994,304 |
| `process_rss_max` | job=mysql-node | 22,732,800 |
| `process_rss_max` | job=prometheus | 102,244,352 |
| `process_rss_max` | job=redis-exporter | 18,268,160 |
| `process_rss_max` | job=redis-node | 22,724,608 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 42.03 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 64.65 |
| `node_cpu_pct_avg` | job=redis-node | 0.39 |
| `node_load1_avg` | job=backend-node | 7.1 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 7.58 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 61,165.71 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 289.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 66,772,480 |
| `node_mem_available_avg` | job=monitoring-node | 407,904,768 |
| `node_mem_available_avg` | job=mysql-node | 225,192,960 |
| `node_mem_available_avg` | job=redis-node | 561,770,496 |
| `node_swap_free_avg` | job=backend-node | 1,995,401,216 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,900,032 |
| `node_swap_free_avg` | job=mysql-node | 2,614,513,664 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 100,168 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 464 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 60,803.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 101,814.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 10 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 101,905.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,033.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 20.57 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 6 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-12T16:21:46.444Z ~ 2026-08-12T16:23:46.444Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,611 | 163.09 | 72.48 | 502.77 | 636.42 | 1,107.14 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,754 | 123.28 | 11.92 | 420.46 | 569.93 | 896.49 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,017 | 438.11 | 487.37 | 982.25 | 1,222.16 | 1,701.04 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 495 | 50.65 | 29.31 | 205.24 | 512.35 | 1,570.54 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,071 | 222.8 | 255.15 | 551.15 | 725.22 | 1,662.96 |
| method=POST, status=401, uri=UNKNOWN | 69 | 0.35 | 0.51 | 0.97 | 1.19 | 18.55 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 16.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 14.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.71 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.31 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.05 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.19 |
| `process_rss_avg` | job=backend-node | 18,217,472 |
| `process_rss_avg` | job=monitoring-node | 16,773,120 |
| `process_rss_avg` | job=mysql-exporter | 16,553,984 |
| `process_rss_avg` | job=mysql-node | 22,421,504 |
| `process_rss_avg` | job=prometheus | 102,640,640 |
| `process_rss_avg` | job=redis-exporter | 18,168,832 |
| `process_rss_avg` | job=redis-node | 22,865,920 |
| `process_rss_max` | job=backend-node | 18,386,944 |
| `process_rss_max` | job=monitoring-node | 16,773,120 |
| `process_rss_max` | job=mysql-exporter | 16,969,728 |
| `process_rss_max` | job=mysql-node | 22,519,808 |
| `process_rss_max` | job=prometheus | 102,969,344 |
| `process_rss_max` | job=redis-exporter | 18,444,288 |
| `process_rss_max` | job=redis-node | 22,978,560 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 26.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 23.61 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 93.17 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 1.6 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 7.2 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 5,136 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 13.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 99,219,456 |
| `node_mem_available_avg` | job=monitoring-node | 407,820,288 |
| `node_mem_available_avg` | job=mysql-node | 224,627,712 |
| `node_mem_available_avg` | job=redis-node | 562,073,600 |
| `node_swap_free_avg` | job=backend-node | 2,016,397,312 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,908,224 |
| `node_swap_free_avg` | job=mysql-node | 2,614,513,664 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 11,539.43 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 10.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 2,307.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 122,066.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 24,310.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 381.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 12.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-12T16:23:46.444Z ~ 2026-08-12T16:25:46.444Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,319 | 584.67 | 348.22 | 1,104.76 | 9,030.17 | 17,992.73 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 997 | 426.49 | 264.58 | 602.64 | 8,895.35 | 17,843.2 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,418 | 1,361.63 | 861.32 | 4,194.88 | 17,381.22 | 18,349.04 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2 | 258.74 | 223.7 | 352.12 | 356.76 | 654.07 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 688 | 738.68 | 357.91 | 1,320.7 | 16,028.82 | 17,273.16 |
| method=POST, status=401, uri=UNKNOWN | 6 | 0.48 | 0.63 | 1.04 | 1.05 | 1.2 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.25 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 69.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 10.24 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.06 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.16 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.21 |
| `process_rss_avg` | job=backend-node | 12,833,792 |
| `process_rss_avg` | job=monitoring-node | 16,838,656 |
| `process_rss_avg` | job=mysql-exporter | 16,397,824 |
| `process_rss_avg` | job=mysql-node | 22,469,632 |
| `process_rss_avg` | job=prometheus | 102,754,304 |
| `process_rss_avg` | job=redis-exporter | 17,594,368 |
| `process_rss_avg` | job=redis-node | 22,690,304 |
| `process_rss_max` | job=backend-node | 17,899,520 |
| `process_rss_max` | job=monitoring-node | 16,904,192 |
| `process_rss_max` | job=mysql-exporter | 16,949,248 |
| `process_rss_max` | job=mysql-node | 22,630,400 |
| `process_rss_max` | job=prometheus | 102,969,344 |
| `process_rss_max` | job=redis-exporter | 18,419,712 |
| `process_rss_max` | job=redis-node | 22,949,888 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 70.31 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 68.45 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 2.51 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 19.11 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 124,213.71 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 34.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,635,200 |
| `node_mem_available_avg` | job=monitoring-node | 408,404,992 |
| `node_mem_available_avg` | job=mysql-node | 223,079,424 |
| `node_mem_available_avg` | job=redis-node | 562,073,600 |
| `node_swap_free_avg` | job=backend-node | 2,002,571,776 |
| `node_swap_free_avg` | job=monitoring-node | 3,017,985,024 |
| `node_swap_free_avg` | job=mysql-node | 2,614,513,664 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 266,620.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 212.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 151,979.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 80,008 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 58,120 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 476.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 3.43 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 21.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-12T16:25:46.444Z ~ 2026-08-12T16:27:46.444Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,186 | 581.75 | 353.55 | 977.55 | 3,153.22 | 20,564.28 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,074 | 367.96 | 295.92 | 742.67 | 1,765.71 | 20,223.75 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,757 | 1,146.14 | 868.47 | 1,419.2 | 18,315.42 | 21,386.15 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2 | 466.16 | 473.7 | 497.37 | 499.47 | 1,405.22 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 719 | 821.85 | 423.78 | 3,671.6 | 5,474.43 | 17,273.16 |
| method=POST, status=401, uri=UNKNOWN | 5 | 4.64 | 1.4 | 10.91 | 11.13 | 10.85 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 751.51 | 760.57 | 800.83 | 804.41 | 751.51 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 40 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 8.07 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.57 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.28 |
| `process_rss_avg` | job=backend-node | 12,491,264 |
| `process_rss_avg` | job=monitoring-node | 16,805,888 |
| `process_rss_avg` | job=mysql-exporter | 16,498,176 |
| `process_rss_avg` | job=mysql-node | 22,398,976 |
| `process_rss_avg` | job=prometheus | 102,353,920 |
| `process_rss_avg` | job=redis-exporter | 17,164,288 |
| `process_rss_avg` | job=redis-node | 22,732,800 |
| `process_rss_max` | job=backend-node | 14,135,296 |
| `process_rss_max` | job=monitoring-node | 16,904,192 |
| `process_rss_max` | job=mysql-exporter | 16,904,192 |
| `process_rss_max` | job=mysql-node | 22,622,208 |
| `process_rss_max` | job=prometheus | 102,682,624 |
| `process_rss_max` | job=redis-exporter | 17,375,232 |
| `process_rss_max` | job=redis-node | 22,781,952 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 63.44 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 71 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 4.52 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 15 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 94,957.71 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 235.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 49,076,224 |
| `node_mem_available_avg` | job=monitoring-node | 408,902,656 |
| `node_mem_available_avg` | job=mysql-node | 230,593,024 |
| `node_mem_available_avg` | job=redis-node | 562,073,600 |
| `node_swap_free_avg` | job=backend-node | 2,079,156,224 |
| `node_swap_free_avg` | job=monitoring-node | 3,018,010,624 |
| `node_swap_free_avg` | job=mysql-node | 2,612,936,704 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 206,294.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 11.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 137,194.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 8,045.71 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 70,261.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 11 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 347,308.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 648 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 97.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 19.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-12T16:27:46.444Z ~ 2026-08-12T16:29:46.444Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,662 | 345.64 | 264.79 | 588.03 | 782.34 | 20,564.28 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,632 | 238.61 | 208.55 | 517.51 | 691.89 | 20,223.75 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,549 | 808.48 | 733.19 | 1,256.08 | 1,730.26 | 21,386.15 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3 | 602.09 | 581.61 | 791.88 | 802.62 | 1,405.22 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,936 | 545.06 | 546.87 | 775.16 | 910.15 | 17,273.16 |
| method=POST, status=401, uri=UNKNOWN | 6 | 0.57 | 0.63 | 1.66 | 1.73 | 10.85 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 672.65 | 671.09 | 711.35 | 714.93 | 751.51 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.12 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 108.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 109 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.23 |
| `process_rss_avg` | job=backend-node | 12,388,864 |
| `process_rss_avg` | job=monitoring-node | 16,969,728 |
| `process_rss_avg` | job=mysql-exporter | 16,314,368 |
| `process_rss_avg` | job=mysql-node | 22,524,416 |
| `process_rss_avg` | job=prometheus | 102,244,352 |
| `process_rss_avg` | job=redis-exporter | 17,511,936 |
| `process_rss_avg` | job=redis-node | 22,880,256 |
| `process_rss_max` | job=backend-node | 12,910,592 |
| `process_rss_max` | job=monitoring-node | 17,035,264 |
| `process_rss_max` | job=mysql-exporter | 16,891,904 |
| `process_rss_max` | job=mysql-node | 22,867,968 |
| `process_rss_max` | job=prometheus | 102,244,352 |
| `process_rss_max` | job=redis-exporter | 17,952,768 |
| `process_rss_max` | job=redis-node | 22,913,024 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 48.28 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.11 |
| `node_cpu_pct_avg` | job=mysql-node | 89.53 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 2.58 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 21.84 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 83,836.57 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 3.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 37,970,432 |
| `node_mem_available_avg` | job=monitoring-node | 409,124,352 |
| `node_mem_available_avg` | job=mysql-node | 246,851,072 |
| `node_mem_available_avg` | job=redis-node | 562,073,600 |
| `node_swap_free_avg` | job=backend-node | 2,129,980,928 |
| `node_swap_free_avg` | job=monitoring-node | 3,018,010,624 |
| `node_swap_free_avg` | job=mysql-node | 2,611,359,744 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 133,062.86 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 3.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 96,572.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 117,742.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 15 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 643,274.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,978.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-12T16:29:46.444Z ~ 2026-08-12T16:31:46.444Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,501 | 409.31 | 323.58 | 977.8 | 2,301.39 | 16,353.9 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,514 | 299.57 | 263.05 | 675.44 | 1,421.71 | 16,037.32 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,819 | 877.27 | 850.86 | 1,413.33 | 2,340.11 | 16,504.32 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=400, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 1 | 843.33 | 850.05 | 890.31 | 893.89 | 843.33 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3 | 677.81 | 581.61 | 970.84 | 981.58 | 958.09 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,058 | 637.22 | 556.93 | 2,158.38 | 2,756.62 | 16,641.52 |
| method=POST, status=401, uri=UNKNOWN | 6 | 40.93 | 11.88 | 173.36 | 177.84 | 168.91 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 709.52 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.88 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 62.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 8.59 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.08 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 109 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.19 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.24 |
| `process_rss_avg` | job=backend-node | 12,504,576 |
| `process_rss_avg` | job=monitoring-node | 17,035,264 |
| `process_rss_avg` | job=mysql-exporter | 16,503,296 |
| `process_rss_avg` | job=mysql-node | 22,345,216 |
| `process_rss_avg` | job=prometheus | 103,718,912 |
| `process_rss_avg` | job=redis-exporter | 18,116,608 |
| `process_rss_avg` | job=redis-node | 22,913,024 |
| `process_rss_max` | job=backend-node | 13,807,616 |
| `process_rss_max` | job=monitoring-node | 17,035,264 |
| `process_rss_max` | job=mysql-exporter | 17,096,704 |
| `process_rss_max` | job=mysql-node | 22,777,856 |
| `process_rss_max` | job=prometheus | 104,210,432 |
| `process_rss_max` | job=redis-exporter | 18,345,984 |
| `process_rss_max` | job=redis-node | 22,913,024 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 62.85 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 89.81 |
| `node_cpu_pct_avg` | job=redis-node | 0.39 |
| `node_load1_avg` | job=backend-node | 6.25 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 19.86 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 120,036.57 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 194.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 56,465,920 |
| `node_mem_available_avg` | job=monitoring-node | 408,548,352 |
| `node_mem_available_avg` | job=mysql-node | 242,775,040 |
| `node_mem_available_avg` | job=redis-node | 561,956,864 |
| `node_swap_free_avg` | job=backend-node | 2,121,448,448 |
| `node_swap_free_avg` | job=monitoring-node | 3,018,010,624 |
| `node_swap_free_avg` | job=mysql-node | 2,611,359,744 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 206,650.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 491.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 138,450.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 95,160 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 23 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 361,942.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 921.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 46.86 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 23.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 2 |
| `redis_up_avg` | job=redis-exporter | 1 |

> 이 문서는 codex의 도움을 받아 작성하였습니다
