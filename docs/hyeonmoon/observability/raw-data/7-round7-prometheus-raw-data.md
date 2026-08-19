# 7차 부하테스트 — Prometheus 원시 집계 데이터

이 문서는 K6 결과 JSON의 실제 종료 시각과 실행 시간을 기준으로 stage를 재구성하고, 각 구간 끝 시점에 Prometheus range/vector query를 평가해 만든 원시 집계표다. p50/p95/p99는 서버의 `http_server_requests_seconds_bucket` histogram으로 계산했다. 값은 Prometheus 원 단위(시간은 ms 변환)를 유지하며, `N/A`/빈 표는 그 시간대에 해당 시계열이 없었음을 뜻한다.

수집 범위는 테스트 대상 백엔드, backend/mysql/redis node exporter, MySQL exporter, Redis exporter다. Grafana/Prometheus 자기 관측 메트릭과 정적 build/info/config 시계열은 성능 측정값이 아니므로 제외했다.

## 실행 목록

| 결과 파일 | 시나리오 | 실제 실행 (UTC) | K6 전체 | 평균 지연 | med | p95 | p99 | max |
|---|---|---|---:|---:|---:|---:|---:|---:|
| [`round7-gclog-pure-throughput-sse1000-20260813.json`](../../../../backend/src/test/k6/result/round7-gclog-pure-throughput-sse1000-20260813.json) | pure-throughput | 2026-08-13T05:25:13.423Z ~ 2026-08-13T05:39:01.611Z | 85,841 | 103.65 req/s | 14,572.53 | 10,013.03 | 60,001.37 | 60,002.42 | 60,186.41 |

---

## round7-gclog-pure-throughput-sse1000-20260813.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-13T05:25:13.423Z ~ 2026-08-13T05:39:01.611Z
- 설정: `{"sseVUs":1000,"totalSseConnections":2000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-13T05:25:48.423Z ~ 2026-08-13T05:27:48.423Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 391 | 1,291.8 | 654.31 | 9,283.84 | 10,816.48 | 10,421.14 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 266 | 228.42 | 43.54 | 428.38 | 5,219.82 | 5,251.51 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 763 | 271.11 | 218.55 | 700.32 | 1,010.32 | 22,044.25 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,099.8 |
| method=POST, status=200, uri=/api/sse/tickets | 24 | 1.76 | 0.86 | 9.86 | 10.92 | 2,098.68 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 58 | 106.03 | 74.1 | 315.93 | 349.52 | 10,988.46 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 36 | 48.37 | 33.55 | 181.19 | 197.3 | 9,277.9 |
| method=POST, status=401, uri=UNKNOWN | 10 | 6.36 | 1.4 | 31.32 | 33.11 | 1,190.18 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15.17 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15.17 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 8.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 61.07 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 9.69 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 98.17 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 103 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.48 |
| `process_rss_avg` | job=backend-node | 14,476,288 |
| `process_rss_avg` | job=monitoring-node | 17,031,168 |
| `process_rss_avg` | job=mysql-exporter | 16,604,160 |
| `process_rss_avg` | job=mysql-node | 22,206,976 |
| `process_rss_avg` | job=prometheus | 100,814,848 |
| `process_rss_max` | job=backend-node | 16,334,848 |
| `process_rss_max` | job=monitoring-node | 17,031,168 |
| `process_rss_max` | job=mysql-exporter | 16,957,440 |
| `process_rss_max` | job=mysql-node | 22,343,680 |
| `process_rss_max` | job=prometheus | 101,044,224 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 996.5 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 942.67 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 33.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 96.38 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 7.6 |
| `node_load1_avg` | job=backend-node | 11.09 |
| `node_load1_avg` | job=monitoring-node | 0.09 |
| `node_load1_avg` | job=mysql-node | 0.08 |
| `node_major_fault_delta` | job=backend-node | 170,093.71 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 786.29 |
| `node_mem_available_avg` | job=backend-node | 60,930,560 |
| `node_mem_available_avg` | job=monitoring-node | 398,627,328 |
| `node_mem_available_avg` | job=mysql-node | 247,558,144 |
| `node_swap_free_avg` | job=backend-node | 2,220,606,464 |
| `node_swap_free_avg` | job=monitoring-node | 3,008,221,184 |
| `node_swap_free_avg` | job=mysql-node | 2,576,144,384 |
| `node_swap_in_delta` | job=backend-node | 259,515.43 |
| `node_swap_in_delta` | job=monitoring-node | 3.43 |
| `node_swap_in_delta` | job=mysql-node | 643.43 |
| `node_swap_out_delta` | job=backend-node | 177,326.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 23,636.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 17,392 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 4.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 4.57 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 100 — 2026-08-13T05:27:48.423Z ~ 2026-08-13T05:29:48.423Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,357 | 453.18 | 363.48 | 801.28 | 1,334.12 | 40,105.92 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,287 | 356.37 | 196.53 | 625.52 | 7,637.33 | 37,458.04 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,953 | 346.4 | 250.5 | 623.93 | 1,338.34 | 40,093.61 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 203,436.55 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 6 | 182,887.3 | 30,000 | 30,000 | 30,000 | 198,690.44 |
| method=GET, status=200, uri=/error | 3 | 199,123.61 | 30,000 | 30,000 | 30,000 | 199,331.61 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,099.8 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 2,098.68 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 648 | 717.07 | 502.7 | 1,592.72 | 8,630.02 | 10,988.46 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 267 | 947.14 | 250.01 | 686 | 30,000 | 42,248.45 |
| method=POST, status=401, uri=UNKNOWN | 11 | 34.57 | 13.28 | 147.64 | 154.8 | 921.83 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15.67 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14.33 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 8.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 142.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 10.06 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 106 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.38 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.79 |
| `process_rss_avg` | job=backend-node | 12,837,376 |
| `process_rss_avg` | job=monitoring-node | 16,992,256 |
| `process_rss_avg` | job=mysql-exporter | 16,427,520 |
| `process_rss_avg` | job=mysql-node | 22,201,344 |
| `process_rss_avg` | job=prometheus | 101,044,224 |
| `process_rss_max` | job=backend-node | 14,954,496 |
| `process_rss_max` | job=monitoring-node | 17,031,168 |
| `process_rss_max` | job=mysql-exporter | 17,055,744 |
| `process_rss_max` | job=mysql-node | 22,372,352 |
| `process_rss_max` | job=prometheus | 101,044,224 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 968.33 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 923.83 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 1,000 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 34.17 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.33 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.7 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 29.44 |
| `node_load1_avg` | job=backend-node | 14.44 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 0.4 |
| `node_major_fault_delta` | job=backend-node | 166,461.71 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 316.57 |
| `node_mem_available_avg` | job=backend-node | 48,597,504 |
| `node_mem_available_avg` | job=monitoring-node | 415,361,024 |
| `node_mem_available_avg` | job=mysql-node | 247,246,848 |
| `node_swap_free_avg` | job=backend-node | 2,092,296,192 |
| `node_swap_free_avg` | job=monitoring-node | 3,008,221,184 |
| `node_swap_free_avg` | job=mysql-node | 2,576,129,536 |
| `node_swap_in_delta` | job=backend-node | 250,872 |
| `node_swap_in_delta` | job=monitoring-node | 4.57 |
| `node_swap_in_delta` | job=mysql-node | 288 |
| `node_swap_out_delta` | job=backend-node | 145,833.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 499.43 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 114,835.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,124.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 57.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 150 — 2026-08-13T05:29:48.423Z ~ 2026-08-13T05:31:48.423Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,124 | 367.08 | 342.25 | 761.58 | 988.75 | 38,897.3 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 960 | 190.99 | 146.12 | 475.49 | 697.04 | 35,740.62 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,986 | 261.52 | 229.67 | 585.6 | 795.56 | 39,188.4 |
| method=GET, status=200, uri=/api/auctions/stream | 62 | 347,600.77 | 30,000 | 30,000 | 30,000 | 368,377 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 198,690.44 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 199,331.61 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 570 | 462.81 | 441 | 858.25 | 1,146.52 | 16,995.91 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 184 | 284.11 | 257.25 | 635.3 | 860.34 | 42,248.45 |
| method=POST, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 3,612.19 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 504.39 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10.6 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.2 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 8.4 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 98.94 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.19 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 8.45 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.26 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 102.6 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.38 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.68 |
| `process_rss_avg` | job=backend-node | 13,659,136 |
| `process_rss_avg` | job=monitoring-node | 16,875,520 |
| `process_rss_avg` | job=mysql-exporter | 16,522,752 |
| `process_rss_avg` | job=mysql-node | 22,205,440 |
| `process_rss_avg` | job=prometheus | 101,349,888 |
| `process_rss_max` | job=backend-node | 14,872,576 |
| `process_rss_max` | job=monitoring-node | 16,875,520 |
| `process_rss_max` | job=mysql-exporter | 17,121,280 |
| `process_rss_max` | job=mysql-node | 22,446,080 |
| `process_rss_max` | job=prometheus | 101,711,872 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 884.2 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 905 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 905 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 905 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 30.2 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.2 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.76 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.25 |
| `node_cpu_pct_avg` | job=mysql-node | 19.63 |
| `node_load1_avg` | job=backend-node | 17.97 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 1.52 |
| `node_major_fault_delta` | job=backend-node | 172,313.14 |
| `node_major_fault_delta` | job=monitoring-node | 22.86 |
| `node_major_fault_delta` | job=mysql-node | 51.43 |
| `node_mem_available_avg` | job=backend-node | 61,181,440 |
| `node_mem_available_avg` | job=monitoring-node | 415,802,368 |
| `node_mem_available_avg` | job=mysql-node | 247,070,208 |
| `node_swap_free_avg` | job=backend-node | 2,057,251,840 |
| `node_swap_free_avg` | job=monitoring-node | 3,008,221,184 |
| `node_swap_free_avg` | job=mysql-node | 2,576,101,376 |
| `node_swap_in_delta` | job=backend-node | 253,818.29 |
| `node_swap_in_delta` | job=monitoring-node | 105.14 |
| `node_swap_in_delta` | job=mysql-node | 37.71 |
| `node_swap_out_delta` | job=backend-node | 155,477.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 95,211.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 2 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 4,716.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 37.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 200 — 2026-08-13T05:31:48.423Z ~ 2026-08-13T05:33:48.423Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 618 | 1,351.12 | 329.36 | 6,781.69 | 15,161.23 | 43,587.77 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 561 | 350.99 | 114.51 | 557 | 5,043.01 | 42,104.7 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 1,710 | 834.21 | 237.18 | 775.07 | 30,000 | 42,721.85 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 368,377 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 75 | 458,494.04 | 30,000 | 30,000 | 30,000 | 477,137.72 |
| method=GET, status=200, uri=/error | 1 | 473,660.61 | 30,000 | 30,000 | 30,000 | 473,660.61 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 56,760.1 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 56,699.44 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 57,043.7 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 322 | 1,394.61 | 438.44 | 6,179.98 | 30,000 | 43,377.47 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 232 | 554.36 | 220.9 | 766.83 | 9,284.29 | 42,091.62 |
| method=POST, status=401, uri=UNKNOWN | 3 | 209.51 | 234.88 | 433.97 | 444.71 | 3,612.19 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 188.25 | 191.27 | 282.75 | 296.55 | 504.39 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 56,926.46 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21.8 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 72 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 46.88 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 8.51 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 104.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 105 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.26 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.53 |
| `process_rss_avg` | job=backend-node | 13,917,184 |
| `process_rss_avg` | job=monitoring-node | 16,921,088 |
| `process_rss_avg` | job=mysql-exporter | 16,522,752 |
| `process_rss_avg` | job=mysql-node | 22,120,960 |
| `process_rss_avg` | job=prometheus | 101,842,944 |
| `process_rss_max` | job=backend-node | 17,219,584 |
| `process_rss_max` | job=monitoring-node | 17,006,592 |
| `process_rss_max` | job=mysql-exporter | 16,809,984 |
| `process_rss_max` | job=mysql-node | 22,351,872 |
| `process_rss_max` | job=prometheus | 102,105,088 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 853 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 888.25 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 853 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 905 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.75 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 4 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 95.34 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 15.79 |
| `node_load1_avg` | job=backend-node | 19.4 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 1.49 |
| `node_major_fault_delta` | job=backend-node | 172,242.29 |
| `node_major_fault_delta` | job=monitoring-node | 17.14 |
| `node_major_fault_delta` | job=mysql-node | 67.43 |
| `node_mem_available_avg` | job=backend-node | 82,170,880 |
| `node_mem_available_avg` | job=monitoring-node | 416,239,104 |
| `node_mem_available_avg` | job=mysql-node | 247,610,368 |
| `node_swap_free_avg` | job=backend-node | 2,023,681,024 |
| `node_swap_free_avg` | job=monitoring-node | 3,008,221,184 |
| `node_swap_free_avg` | job=mysql-node | 2,576,101,376 |
| `node_swap_in_delta` | job=backend-node | 246,548.57 |
| `node_swap_in_delta` | job=monitoring-node | 18.29 |
| `node_swap_in_delta` | job=mysql-node | 98.29 |
| `node_swap_out_delta` | job=backend-node | 162,262.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 42,733.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 13,421.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 17.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 2.29 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 300 — 2026-08-13T05:33:48.423Z ~ 2026-08-13T05:35:48.423Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,101 | 790.1 | 299.44 | 1,470.61 | 6,256.34 | 43,587.77 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,050 | 302.98 | 163.3 | 621.04 | 1,288.94 | 42,104.7 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,415 | 500.63 | 246.54 | 746.17 | 1,698.07 | 42,721.85 |
| method=GET, status=200, uri=/api/auctions/stream | 714 | 547,480.14 | 30,000 | 30,000 | 30,000 | 620,610.82 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 739 | 539,076.57 | 30,000 | 30,000 | 30,000 | 618,509.05 |
| method=GET, status=200, uri=/error | 1 | 498,870.98 | 30,000 | 30,000 | 30,000 | 498,870.98 |
| method=GET, status=500, uri=/api/auctions | 6 | 38,523.72 | 30,000 | 30,000 | 30,000 | 56,760.1 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 1 | 38,543.63 | 30,000 | 30,000 | 30,000 | 56,699.44 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 10 | 38,546.5 | 30,000 | 30,000 | 30,000 | 57,043.7 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 466 | 1,204.88 | 454.7 | 2,308.54 | 30,000 | 43,377.47 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 169 | 701.58 | 237.68 | 1,622.54 | 6,814.68 | 42,091.62 |
| method=POST, status=401, uri=UNKNOWN | 9 | 239.09 | 8.39 | 697.93 | 712.25 | 670.12 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 481.79 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 2 | 38,657.27 | 30,000 | 30,000 | 30,000 | 56,926.46 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.33 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.57 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 109.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 41.58 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 10.92 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105.83 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 113 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_rss_avg` | job=backend-node | 13,665,792 |
| `process_rss_avg` | job=monitoring-node | 16,896,000 |
| `process_rss_avg` | job=mysql-exporter | 16,692,224 |
| `process_rss_avg` | job=mysql-node | 22,202,368 |
| `process_rss_avg` | job=prometheus | 102,105,088 |
| `process_rss_max` | job=backend-node | 14,831,616 |
| `process_rss_max` | job=monitoring-node | 17,002,496 |
| `process_rss_max` | job=mysql-exporter | 17,047,552 |
| `process_rss_max` | job=mysql-node | 22,396,928 |
| `process_rss_max` | job=prometheus | 102,105,088 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 511.17 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 313.17 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 853 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 693 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=wallet, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 33.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.33 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 96.93 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 17.15 |
| `node_load1_avg` | job=backend-node | 17.49 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 0.76 |
| `node_major_fault_delta` | job=backend-node | 167,091.43 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 65.14 |
| `node_mem_available_avg` | job=backend-node | 48,629,248 |
| `node_mem_available_avg` | job=monitoring-node | 416,712,704 |
| `node_mem_available_avg` | job=mysql-node | 247,842,304 |
| `node_swap_free_avg` | job=backend-node | 2,004,763,136 |
| `node_swap_free_avg` | job=monitoring-node | 3,008,221,184 |
| `node_swap_free_avg` | job=mysql-node | 2,576,101,376 |
| `node_swap_in_delta` | job=backend-node | 261,057.14 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 145.14 |
| `node_swap_out_delta` | job=backend-node | 155,186.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 87,848 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,869.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 18.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 5.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

### QPS 400 — 2026-08-13T05:35:48.423Z ~ 2026-08-13T05:37:48.423Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,150 | 261.17 | 206.51 | 560.16 | 1,345.84 | 39,374.25 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,224 | 160.65 | 113.21 | 365.95 | 984.58 | 39,196.95 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,446 | 252 | 194.66 | 525.19 | 1,287.21 | 38,529.77 |
| method=GET, status=200, uri=/api/auctions/stream | 243 | 706,651.95 | 30,000 | 30,000 | 30,000 | 725,673.37 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 11 | 711,145.02 | 30,000 | 30,000 | 30,000 | 739,754.89 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 498,870.98 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 56,760.1 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 56,699.44 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 57,043.7 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,798 | 350.29 | 286.12 | 686.14 | 1,533.21 | 39,078.4 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,950 | 181.19 | 139.98 | 416.88 | 848.7 | 37,253.67 |
| method=POST, status=401, uri=UNKNOWN | 2 | 30.48 | 6.99 | 55.36 | 55.81 | 670.12 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 178.11 | 81.67 | 433.97 | 444.71 | 416.63 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 56,926.46 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.13 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 161.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.71 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=GCLocker Initiated GC, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 109.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 113 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.64 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.81 |
| `process_rss_avg` | job=backend-node | 12,314,112 |
| `process_rss_avg` | job=monitoring-node | 16,960,512 |
| `process_rss_avg` | job=mysql-exporter | 16,607,744 |
| `process_rss_avg` | job=mysql-node | 22,043,136 |
| `process_rss_avg` | job=prometheus | 102,105,088 |
| `process_rss_max` | job=backend-node | 18,157,568 |
| `process_rss_max` | job=monitoring-node | 17,121,280 |
| `process_rss_max` | job=mysql-exporter | 17,018,880 |
| `process_rss_max` | job=mysql-node | 22,310,912 |
| `process_rss_max` | job=prometheus | 102,105,088 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 152.88 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 36.75 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 223 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 42 |
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
| `node_cpu_pct_avg` | job=backend-node | 99.12 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.22 |
| `node_cpu_pct_avg` | job=mysql-node | 70.69 |
| `node_load1_avg` | job=backend-node | 18.64 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 9.01 |
| `node_major_fault_delta` | job=backend-node | 166,947.43 |
| `node_major_fault_delta` | job=monitoring-node | 9.14 |
| `node_major_fault_delta` | job=mysql-node | 92.57 |
| `node_mem_available_avg` | job=backend-node | 57,602,560 |
| `node_mem_available_avg` | job=monitoring-node | 416,579,584 |
| `node_mem_available_avg` | job=mysql-node | 248,989,696 |
| `node_swap_free_avg` | job=backend-node | 1,995,818,496 |
| `node_swap_free_avg` | job=monitoring-node | 3,008,243,712 |
| `node_swap_free_avg` | job=mysql-node | 2,576,967,680 |
| `node_swap_in_delta` | job=backend-node | 259,078.86 |
| `node_swap_in_delta` | job=monitoring-node | 8 |
| `node_swap_in_delta` | job=mysql-node | 59.43 |
| `node_swap_out_delta` | job=backend-node | 151,660.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 318,930.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,769.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 70.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 8 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

수집 시계열 없음.

> 이 문서는 codex의 도움을 받아 작성하였습니다
