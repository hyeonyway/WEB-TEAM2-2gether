# 4차 부하테스트 — Prometheus 원시 집계 데이터

이 문서는 K6 결과 JSON의 실제 종료 시각과 실행 시간을 기준으로 stage를 재구성하고, 각 구간 끝 시점에 Prometheus range/vector query를 평가해 만든 원시 집계표다. p50/p95/p99는 서버의 `http_server_requests_seconds_bucket` histogram으로 계산했다. 값은 Prometheus 원 단위(시간은 ms 변환)를 유지하며, `N/A`/빈 표는 그 시간대에 해당 시계열이 없었음을 뜻한다.

수집 범위는 테스트 대상 백엔드, backend/mysql/redis node exporter, MySQL exporter, Redis exporter다. Grafana/Prometheus 자기 관측 메트릭과 정적 build/info/config 시계열은 성능 측정값이 아니므로 제외했다.

## 실행 목록

| 결과 파일 | 시나리오 | 실제 실행 (UTC) | K6 전체 | 평균 지연 | med | p95 | p99 | max |
|---|---|---|---:|---:|---:|---:|---:|---:|
| [`baseline-postmerge-pure-throughput-sse250-fullramp-20260811.json`](../../../../backend/src/test/k6/result/baseline-postmerge-pure-throughput-sse250-fullramp-20260811.json) | pure-throughput | 2026-08-11T12:53:16.252Z ~ 2026-08-11T13:06:59.992Z | 74,544 | 90.49 req/s | 15,462.37 | 10,020.33 | 60,000.59 | 60,002.37 | 60,076.23 |
| [`baseline-postmerge-pure-throughput-sse250-fullramp-rerun-20260811.json`](../../../../backend/src/test/k6/result/baseline-postmerge-pure-throughput-sse250-fullramp-rerun-20260811.json) | pure-throughput | 2026-08-11T13:37:59.610Z ~ 2026-08-11T13:51:43.720Z | 92,989 | 112.84 req/s | 9,042.03 | 4,690.09 | 35,293.09 | 60,000.42 | 60,017.65 |
| [`baseline-postmerge-pure-throughput-sse500-fullramp-20260811.json`](../../../../backend/src/test/k6/result/baseline-postmerge-pure-throughput-sse500-fullramp-20260811.json) | pure-throughput | 2026-08-11T13:52:38.616Z ~ 2026-08-11T14:06:21.169Z | 86,024 | 104.58 req/s | 9,577.04 | 335.97 | 51,899.04 | 59,999.13 | 60,036.78 |
| [`baseline-postmerge-pure-throughput-sse1000-fullramp-20260811.json`](../../../../backend/src/test/k6/result/baseline-postmerge-pure-throughput-sse1000-fullramp-20260811.json) | pure-throughput | 2026-08-11T14:07:19.921Z ~ 2026-08-11T14:21:03.052Z | 77,104 | 93.67 req/s | 21,195.52 | 11,682.9 | 59,998.78 | 60,001.24 | 114,788.74 |
| [`baseline-postmerge-hot-auction-pattern-sse250-20260811.json`](../../../../backend/src/test/k6/result/baseline-postmerge-hot-auction-pattern-sse250-20260811.json) | hot-auction-pattern | 2026-08-11T14:22:27.563Z ~ 2026-08-11T14:30:28.554Z | 36,472 | 75.83 req/s | 556.41 | 56.69 | 3,560.17 | 9,230.33 | 14,415.16 |
| [`baseline-postmerge-bid-only-load-noSSE-20260811.json`](../../../../backend/src/test/k6/result/baseline-postmerge-bid-only-load-noSSE-20260811.json) | bid-only-load (SSE 없음) | 2026-08-11T14:31:14.808Z ~ 2026-08-11T14:43:26.831Z | 141,455 | 193.24 req/s | 1,325.51 | 31.87 | 5,314.73 | 18,457.03 | 21,240.74 |
| [`baseline-postmerge-bid-only-load-singleHotAuction-20260811.json`](../../../../backend/src/test/k6/result/baseline-postmerge-bid-only-load-singleHotAuction-20260811.json) | bid-only-load (SSE 없음) | 2026-08-11T14:45:41.288Z ~ 2026-08-11T14:57:54.099Z | 58,995 | 80.51 req/s | 20,617.34 | 13,562.12 | 60,000.78 | 60,002.06 | 60,034.27 |

---

## baseline-postmerge-pure-throughput-sse250-fullramp-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T12:53:16.252Z ~ 2026-08-11T13:06:59.992Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T12:53:51.252Z ~ 2026-08-11T12:55:51.252Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,242 | 611.59 | 561 | 887.31 | 2,524.83 | 7,259.29 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,014 | 356.15 | 266.49 | 588.69 | 5,756.85 | 6,654.27 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,387 | 420.44 | 332.18 | 675.86 | 2,876.44 | 7,133.49 |
| method=GET, status=200, uri=/api/statistic/market | N/A | N/A | N/A | N/A | N/A | 1,732.73 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 353.7 |
| method=OPTIONS, status=200, uri=/api/auctions | N/A | N/A | N/A | N/A | N/A | 17.55 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 2,682.39 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 2,691.37 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 2,669.78 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 2,666.34 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,102.93 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 440.59 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,493 | 658.85 | 565.09 | 987.02 | 5,830.14 | 7,314.62 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 458 | 353.61 | 312.09 | 641.54 | 1,200.03 | 6,905.6 |
| method=POST, status=401, uri=/api/auth/refresh | N/A | N/A | N/A | N/A | N/A | 972.92 |
| method=POST, status=401, uri=UNKNOWN | 32 | 26.8 | 32.16 | 42.13 | 44.22 | 48.46 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 292.38 | 319.3 | 354.05 | 357.14 | 354.37 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 6,244.2 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 46.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.36 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.75 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.88 |
| `process_rss_avg` | job=backend-node | 11,615,232 |
| `process_rss_avg` | job=monitoring-node | 18,583,552 |
| `process_rss_avg` | job=mysql-exporter | 16,674,304 |
| `process_rss_avg` | job=mysql-node | 22,260,736 |
| `process_rss_avg` | job=prometheus | 109,437,440 |
| `process_rss_avg` | job=redis-exporter | 17,203,200 |
| `process_rss_avg` | job=redis-node | 22,367,232 |
| `process_rss_max` | job=backend-node | 12,787,712 |
| `process_rss_max` | job=monitoring-node | 18,583,552 |
| `process_rss_max` | job=mysql-exporter | 16,953,344 |
| `process_rss_max` | job=mysql-node | 22,343,680 |
| `process_rss_max` | job=prometheus | 109,813,760 |
| `process_rss_max` | job=redis-exporter | 17,416,192 |
| `process_rss_max` | job=redis-node | 22,507,520 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500.25 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 99.06 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.11 |
| `node_cpu_pct_avg` | job=mysql-node | 31.3 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 26.21 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.78 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 107,406.86 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 11.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 40,019,968 |
| `node_mem_available_avg` | job=monitoring-node | 417,814,016 |
| `node_mem_available_avg` | job=mysql-node | 250,593,280 |
| `node_mem_available_avg` | job=redis-node | 566,067,200 |
| `node_swap_free_avg` | job=backend-node | 2,419,595,776 |
| `node_swap_free_avg` | job=monitoring-node | 3,092,824,064 |
| `node_swap_free_avg` | job=mysql-node | 2,673,403,904 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 169,232 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 9.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 79,161.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 169,043.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 17,808 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 137.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T12:55:51.252Z ~ 2026-08-11T12:57:51.252Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,776 | 869.62 | 529.26 | 1,018.85 | 14,536.08 | 36,757.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,549 | 413.7 | 258.92 | 615.98 | 1,306.39 | 35,475.34 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,906 | 551.96 | 340.97 | 752.76 | 3,229.58 | 35,676.88 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 51,459.75 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 15,440.41 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 1,732.73 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 19,439.41 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 353.7 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 17.55 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 2,682.39 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 2,691.37 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 2,669.78 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 2,666.34 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 389.15 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 219 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 750 | 848.51 | 559.61 | 1,017.7 | 3,020.79 | 35,956.96 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 679 | 477.3 | 307.93 | 665.87 | 1,453.13 | 32,522.21 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 972.92 |
| method=POST, status=401, uri=UNKNOWN | 9 | 54.16 | 44.74 | 170.01 | 177.17 | 168.05 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 426.1 | 447.39 | 608.45 | 622.77 | 622.12 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 43,967.5 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.4 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.6 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.4 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.07 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 50.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 6.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.8 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.76 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.86 |
| `process_rss_avg` | job=backend-node | 13,376,950.86 |
| `process_rss_avg` | job=monitoring-node | 18,583,552 |
| `process_rss_avg` | job=mysql-exporter | 16,561,152 |
| `process_rss_avg` | job=mysql-node | 22,353,408 |
| `process_rss_avg` | job=prometheus | 109,928,448 |
| `process_rss_avg` | job=redis-exporter | 17,907,712 |
| `process_rss_avg` | job=redis-node | 22,495,232 |
| `process_rss_max` | job=backend-node | 17,072,128 |
| `process_rss_max` | job=monitoring-node | 18,583,552 |
| `process_rss_max` | job=mysql-exporter | 17,068,032 |
| `process_rss_max` | job=mysql-node | 22,724,608 |
| `process_rss_max` | job=prometheus | 109,944,832 |
| `process_rss_max` | job=redis-exporter | 18,071,552 |
| `process_rss_max` | job=redis-node | 22,495,232 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500.2 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 501 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.6 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 4 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.53 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.1 |
| `node_cpu_pct_avg` | job=mysql-node | 21.76 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 37.74 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.47 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 117,733.71 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 9.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 55,265,572.57 |
| `node_mem_available_avg` | job=monitoring-node | 417,883,648 |
| `node_mem_available_avg` | job=mysql-node | 249,746,944 |
| `node_mem_available_avg` | job=redis-node | 566,067,200 |
| `node_swap_free_avg` | job=backend-node | 2,323,116,032 |
| `node_swap_free_avg` | job=monitoring-node | 3,092,824,064 |
| `node_swap_free_avg` | job=mysql-node | 2,673,446,912 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 190,844.57 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 2.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 147,114.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 120,649.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,821.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 43.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T12:57:51.252Z ~ 2026-08-11T12:59:51.252Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,151 | 375.53 | 302.57 | 851.75 | 2,253.07 | 36,757.38 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,291 | 192.72 | 147.16 | 475.21 | 923.12 | 35,475.34 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,310 | 246.53 | 200.37 | 572.82 | 1,113.69 | 35,676.88 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 51,459.75 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 15,440.41 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 1,732.73 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 19,439.41 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 144 | 318,375.84 | 30,000 | 30,000 | 30,000 | 358,981.32 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 357,463.93 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 17.55 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 2,682.39 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 2,691.37 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 2,669.78 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 2,666.34 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 655 | 562.03 | 348.26 | 2,178.8 | 4,033.69 | 35,956.96 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 391 | 269.53 | 189.48 | 621.88 | 2,430.24 | 32,522.21 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 972.92 |
| method=POST, status=401, uri=UNKNOWN | 5 | 191.51 | 100 | 429.5 | 443.81 | 383.43 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 163.49 | 167.77 | 177.84 | 178.73 | 1,799.43 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 43,967.5 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11.63 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.25 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 128 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 18.02 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.51 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.89 |
| `process_rss_avg` | job=backend-node | 12,271,616 |
| `process_rss_avg` | job=monitoring-node | 18,729,984 |
| `process_rss_avg` | job=mysql-exporter | 16,512,000 |
| `process_rss_avg` | job=mysql-node | 22,372,352 |
| `process_rss_avg` | job=prometheus | 110,174,208 |
| `process_rss_avg` | job=redis-exporter | 17,900,544 |
| `process_rss_avg` | job=redis-node | 22,669,312 |
| `process_rss_max` | job=backend-node | 15,532,032 |
| `process_rss_max` | job=monitoring-node | 18,845,696 |
| `process_rss_max` | job=mysql-exporter | 16,982,016 |
| `process_rss_max` | job=mysql-node | 22,728,704 |
| `process_rss_max` | job=prometheus | 110,206,976 |
| `process_rss_max` | job=redis-exporter | 18,202,624 |
| `process_rss_max` | job=redis-node | 22,757,376 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 345.13 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 31.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 99.42 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.1 |
| `node_cpu_pct_avg` | job=mysql-node | 19.8 |
| `node_cpu_pct_avg` | job=redis-node | 0.55 |
| `node_load1_avg` | job=backend-node | 29.01 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.39 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 155,894.86 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 8 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 48,340,480 |
| `node_mem_available_avg` | job=monitoring-node | 417,758,720 |
| `node_mem_available_avg` | job=mysql-node | 248,336,896 |
| `node_mem_available_avg` | job=redis-node | 562,618,368 |
| `node_swap_free_avg` | job=backend-node | 2,196,052,480 |
| `node_swap_free_avg` | job=monitoring-node | 3,092,824,064 |
| `node_swap_free_avg` | job=mysql-node | 2,673,446,912 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 246,155.43 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 4.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 164,134.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 150,684.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 3,385.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 30.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T12:59:51.252Z ~ 2026-08-11T13:01:51.252Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,229 | 966.16 | 551.44 | 1,662.51 | 5,674.13 | 30,854.74 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,271 | 483.99 | 273.37 | 892.55 | 1,751.63 | 35,475.34 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,271 | 662.74 | 372.31 | 1,051.06 | 5,071.97 | 29,991.91 |
| method=GET, status=200, uri=/api/auctions/stream | 7 | 434,001.32 | 30,000 | 30,000 | 30,000 | 439,996.21 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 358,981.32 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 366,010.3 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 368 | 2,274.97 | 765.54 | 6,356.55 | 30,000 | 38,036.5 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 336 | 531.07 | 374.96 | 1,089.65 | 1,696.51 | 26,180.62 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 383.43 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 4 | 151.78 | 105.92 | 295.27 | 299.05 | 1,799.43 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 43,967.5 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24.6 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.4 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.8 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 161.33 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 14.54 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 111.2 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 112 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.43 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.74 |
| `process_rss_avg` | job=backend-node | 10,002,944 |
| `process_rss_avg` | job=monitoring-node | 18,706,432 |
| `process_rss_avg` | job=mysql-exporter | 16,330,752 |
| `process_rss_avg` | job=mysql-node | 22,261,248 |
| `process_rss_avg` | job=prometheus | 137,061,888 |
| `process_rss_avg` | job=redis-exporter | 17,316,864 |
| `process_rss_avg` | job=redis-node | 22,749,184 |
| `process_rss_max` | job=backend-node | 14,397,440 |
| `process_rss_max` | job=monitoring-node | 18,706,432 |
| `process_rss_max` | job=mysql-exporter | 16,842,752 |
| `process_rss_max` | job=mysql-node | 22,478,848 |
| `process_rss_max` | job=prometheus | 144,175,104 |
| `process_rss_max` | job=redis-exporter | 17,510,400 |
| `process_rss_max` | job=redis-node | 22,749,184 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 459 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 257.4 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 459 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 274 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 77.19 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.4 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.22 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.6 |
| `node_cpu_pct_avg` | job=mysql-node | 16.52 |
| `node_cpu_pct_avg` | job=redis-node | 0.4 |
| `node_load1_avg` | job=backend-node | 24.61 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.9 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 165,200 |
| `node_major_fault_delta` | job=monitoring-node | 70.86 |
| `node_major_fault_delta` | job=mysql-node | 33.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,258,880 |
| `node_mem_available_avg` | job=monitoring-node | 392,754,688 |
| `node_mem_available_avg` | job=mysql-node | 249,121,280 |
| `node_mem_available_avg` | job=redis-node | 545,229,824 |
| `node_swap_free_avg` | job=backend-node | 2,117,645,312 |
| `node_swap_free_avg` | job=monitoring-node | 3,092,824,064 |
| `node_swap_free_avg` | job=mysql-node | 2,673,446,912 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 235,676.57 |
| `node_swap_in_delta` | job=monitoring-node | 78.86 |
| `node_swap_in_delta` | job=mysql-node | 1.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 156,985.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 95,934.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 7,146.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 25.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T13:01:51.252Z ~ 2026-08-11T13:03:51.252Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `process_rss_avg` | job=backend-node | 11,600,896 |
| `process_rss_avg` | job=monitoring-node | 18,859,008 |
| `process_rss_avg` | job=mysql-exporter | 16,590,336 |
| `process_rss_avg` | job=mysql-node | 22,183,424 |
| `process_rss_avg` | job=prometheus | 144,472,064 |
| `process_rss_avg` | job=redis-exporter | 17,594,368 |
| `process_rss_avg` | job=redis-node | 22,777,344 |
| `process_rss_max` | job=backend-node | 15,196,160 |
| `process_rss_max` | job=monitoring-node | 18,968,576 |
| `process_rss_max` | job=mysql-exporter | 16,945,152 |
| `process_rss_max` | job=mysql-node | 22,515,712 |
| `process_rss_max` | job=prometheus | 144,961,536 |
| `process_rss_max` | job=redis-exporter | 17,866,752 |
| `process_rss_max` | job=redis-node | 23,011,328 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 92.75 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.09 |
| `node_cpu_pct_avg` | job=mysql-node | 1.16 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 14.63 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.39 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 168,238.86 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 0 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 59,951,104 |
| `node_mem_available_avg` | job=monitoring-node | 386,673,152 |
| `node_mem_available_avg` | job=mysql-node | 248,004,608 |
| `node_mem_available_avg` | job=redis-node | 550,125,568 |
| `node_swap_free_avg` | job=backend-node | 2,040,274,432 |
| `node_swap_free_avg` | job=monitoring-node | 3,092,824,064 |
| `node_swap_free_avg` | job=mysql-node | 2,673,446,912 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 273,770.29 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 0 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 179,315.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 318.86 |
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
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T13:03:51.252Z ~ 2026-08-11T13:05:51.252Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,072 | 420.18 | 338.74 | 1,241.43 | 1,770.99 | 149,912.26 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,106 | 209.88 | 121.88 | 661.52 | 976.51 | 127,987.32 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,831 | 301.17 | 221.4 | 823.78 | 1,312.42 | 128,416.75 |
| method=GET, status=200, uri=/api/auctions/stream | 533 | 663,919.33 | 30,000 | 30,000 | 30,000 | 724,830.38 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 298 | 686,486.17 | 30,000 | 30,000 | 30,000 | 740,188.37 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 122,329.42 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 122,068.7 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 122,291.88 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 383 | 2,587.08 | 374.75 | 1,415.03 | 30,000 | 153,996.14 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 365 | 762.13 | 29.46 | 787.66 | 1,737.27 | 150,284.69 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 5 | 263.38 | 13.98 | 966.37 | 980.68 | 970.32 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 101.41 | 61.52 | 200.65 | 30,000 | 127,698.27 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 118,963.61 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.71 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.29 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.71 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 105.69 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 16.41 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 119.29 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 124 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.32 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.59 |
| `process_rss_avg` | job=backend-node | 13,283,328 |
| `process_rss_avg` | job=monitoring-node | 18,829,312 |
| `process_rss_avg` | job=mysql-exporter | 16,446,976 |
| `process_rss_avg` | job=mysql-node | 22,255,104 |
| `process_rss_avg` | job=prometheus | 116,909,056 |
| `process_rss_avg` | job=redis-exporter | 17,401,856 |
| `process_rss_avg` | job=redis-node | 22,593,024 |
| `process_rss_max` | job=backend-node | 15,196,160 |
| `process_rss_max` | job=monitoring-node | 18,931,712 |
| `process_rss_max` | job=mysql-exporter | 17,080,320 |
| `process_rss_max` | job=mysql-node | 22,491,136 |
| `process_rss_max` | job=prometheus | 144,420,864 |
| `process_rss_max` | job=redis-exporter | 17,674,240 |
| `process_rss_max` | job=redis-node | 22,712,320 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 28 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 154 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 21.71 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 4 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 6 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.78 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 12.47 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 17.42 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.1 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 174,438.86 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 2.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 73,427,456 |
| `node_mem_available_avg` | job=monitoring-node | 400,817,152 |
| `node_mem_available_avg` | job=mysql-node | 248,064,512 |
| `node_mem_available_avg` | job=redis-node | 552,075,776 |
| `node_swap_free_avg` | job=backend-node | 1,997,869,056 |
| `node_swap_free_avg` | job=monitoring-node | 3,092,978,176 |
| `node_swap_free_avg` | job=mysql-node | 2,673,448,960 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 250,109.71 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 2.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 155,273.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 88,124.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 4,748.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 30.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-postmerge-pure-throughput-sse250-fullramp-rerun-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T13:37:59.610Z ~ 2026-08-11T13:51:43.720Z
- 설정: `{"sseVUs":250,"totalSseConnections":500,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T13:38:34.610Z ~ 2026-08-11T13:40:34.610Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,283 | 200.89 | 14.14 | 1,346.15 | 3,744.38 | 4,849.6 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,178 | 36.58 | 6.73 | 206.3 | 335.62 | 1,594.72 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,693 | 84.32 | 9.36 | 303.3 | 1,670.79 | 3,957.93 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 2 | 58.14 | 16.78 | 98.95 | 99.79 | 99.77 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 25.69 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 810.75 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 25.88 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,193 | 115.73 | 21.02 | 380.41 | 944.89 | 4,216.62 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 35 | 211.14 | 205.05 | 398.18 | 525.44 | 522.31 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 73 | 2.7 | 0.63 | 5.31 | 93.27 | 90.77 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 7 | 10.16 | 6.65 | 31.88 | 33.22 | 28.24 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.38 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 32 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.71 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 7.15 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.06 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 109 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.56 |
| `process_rss_avg` | job=backend-node | 13,232,640 |
| `process_rss_avg` | job=monitoring-node | 18,702,336 |
| `process_rss_avg` | job=mysql-exporter | 16,637,952 |
| `process_rss_avg` | job=mysql-node | 22,344,704 |
| `process_rss_avg` | job=prometheus | 116,019,200 |
| `process_rss_avg` | job=redis-exporter | 17,812,480 |
| `process_rss_avg` | job=redis-node | 22,523,904 |
| `process_rss_max` | job=backend-node | 14,667,776 |
| `process_rss_max` | job=monitoring-node | 18,702,336 |
| `process_rss_max` | job=mysql-exporter | 17,027,072 |
| `process_rss_max` | job=mysql-node | 22,577,152 |
| `process_rss_max` | job=prometheus | 116,117,504 |
| `process_rss_max` | job=redis-exporter | 18,255,872 |
| `process_rss_max` | job=redis-node | 22,523,904 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 0.75 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 58.55 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 20.16 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 6.96 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 0.19 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 67,054.86 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 16 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 49,019,904 |
| `node_mem_available_avg` | job=monitoring-node | 408,771,072 |
| `node_mem_available_avg` | job=mysql-node | 248,497,152 |
| `node_mem_available_avg` | job=redis-node | 564,757,504 |
| `node_swap_free_avg` | job=backend-node | 2,066,425,344 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,673,782,784 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 132,864 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 3.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 63,598.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 112,987.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,370.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 34.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T13:40:34.610Z ~ 2026-08-11T13:42:34.610Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,682 | 13.97 | 12.68 | 21.54 | 37.22 | 4,849.6 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,639 | 12.31 | 6.38 | 10.91 | 20.73 | 7,688.63 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,984 | 11.19 | 8.33 | 14.79 | 26.05 | 7,703.91 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 683.37 |
| method=GET, status=200, uri=/api/statistic/insights | 1 | 136.58 | 146.42 | 14,887.15 | 15,576 | 136.58 |
| method=GET, status=200, uri=/api/statistic/market | 1 | 137.33 | 146.42 | 1,574.3 | 1,746.52 | 137.33 |
| method=GET, status=200, uri=/api/statistic/price-movers | 1 | 536.36 | 520.11 | 19,462.25 | 22,217.64 | 536.36 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 1 | 125.29 | 123.03 | 133.1 | 133.99 | 125.29 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 2 | 1.84 | 2.48 | 19.01 | 21.7 | 2.6 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 1 | 16.6 | 16.14 | 2,648.05 | 2,820.26 | 16.6 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 12.32 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 1 | 4.34 | 4.96 | 2,648.05 | 2,820.26 | 4.34 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 1 | 41.74 | 42.2 | 2,648.05 | 2,820.26 | 41.74 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 1 | 26.11 | 25.42 | 2,648.05 | 2,820.26 | 26.11 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 2.48 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,646 | 24.24 | 19.03 | 24.37 | 46.42 | 7,761.23 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 522.31 |
| method=POST, status=401, uri=/api/auth/refresh | 1 | 679.75 | 675.15 | 930.45 | 973.5 | 679.75 |
| method=POST, status=401, uri=UNKNOWN | 75 | 0.6 | 0.55 | 1.25 | 8.86 | 90.77 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 11 | 5.93 | 5.59 | 8.04 | 8.32 | 28.24 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 22.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.36 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.13 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.28 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.34 |
| `process_rss_avg` | job=backend-node | 12,881,408 |
| `process_rss_avg` | job=monitoring-node | 18,849,792 |
| `process_rss_avg` | job=mysql-exporter | 16,508,928 |
| `process_rss_avg` | job=mysql-node | 22,306,304 |
| `process_rss_avg` | job=prometheus | 116,150,272 |
| `process_rss_avg` | job=redis-exporter | 17,972,224 |
| `process_rss_avg` | job=redis-node | 22,648,832 |
| `process_rss_max` | job=backend-node | 13,524,992 |
| `process_rss_max` | job=monitoring-node | 18,964,480 |
| `process_rss_max` | job=mysql-exporter | 17,158,144 |
| `process_rss_max` | job=mysql-node | 22,413,312 |
| `process_rss_max` | job=prometheus | 116,248,576 |
| `process_rss_max` | job=redis-exporter | 18,063,360 |
| `process_rss_max` | job=redis-node | 22,786,048 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250.63 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 251 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 1.38 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 5 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 51.44 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 28.55 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 2.42 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 0.22 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 27,305.14 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 9.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 45,857,792 |
| `node_mem_available_avg` | job=monitoring-node | 409,186,304 |
| `node_mem_available_avg` | job=mysql-node | 249,129,472 |
| `node_mem_available_avg` | job=redis-node | 562,995,712 |
| `node_swap_free_avg` | job=backend-node | 2,058,691,072 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,673,782,784 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 40,997.71 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 10.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 16,547.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 151,475.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 26.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T13:42:34.610Z ~ 2026-08-11T13:44:34.610Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,893 | 160.99 | 70.09 | 399.76 | 2,882.31 | 8,363.69 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,792 | 80.95 | 31.17 | 235.08 | 786.07 | 7,688.63 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,517 | 105.61 | 44.89 | 284.78 | 1,020.32 | 8,063.1 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 683.37 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 136.58 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 137.33 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 536.36 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 75,131.72 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 125.29 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 2.6 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 16.6 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 12.32 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 4.34 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 41.74 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 26.11 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,507 | 130.5 | 82.02 | 349.57 | 479.27 | 8,270.41 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 303 | 156.91 | 143.68 | 349.87 | 441.58 | 2,122.56 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 679.75 |
| method=POST, status=401, uri=UNKNOWN | 59 | 4.62 | 0.72 | 29.08 | 77.85 | 87.01 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 14 | 38.09 | 22.37 | 210.27 | 221.01 | 436.95 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 2 | 993.02 | 1,464.11 | 6,297.22 | 6,986.07 | 1,444.01 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13.88 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 15.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 58.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 7.53 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 111 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.53 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.66 |
| `process_rss_avg` | job=backend-node | 13,217,280 |
| `process_rss_avg` | job=monitoring-node | 18,964,480 |
| `process_rss_avg` | job=mysql-exporter | 16,566,784 |
| `process_rss_avg` | job=mysql-node | 22,231,552 |
| `process_rss_avg` | job=prometheus | 116,477,952 |
| `process_rss_avg` | job=redis-exporter | 18,063,360 |
| `process_rss_avg` | job=redis-node | 22,665,216 |
| `process_rss_max` | job=backend-node | 15,249,408 |
| `process_rss_max` | job=monitoring-node | 18,964,480 |
| `process_rss_max` | job=mysql-exporter | 17,158,144 |
| `process_rss_max` | job=mysql-node | 22,306,816 |
| `process_rss_max` | job=prometheus | 116,510,720 |
| `process_rss_max` | job=redis-exporter | 18,063,360 |
| `process_rss_max` | job=redis-node | 22,769,664 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 27.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 95.92 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.19 |
| `node_cpu_pct_avg` | job=mysql-node | 40.34 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 10.25 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.71 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 89,794.29 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 30.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 43,351,552 |
| `node_mem_available_avg` | job=monitoring-node | 409,497,600 |
| `node_mem_available_avg` | job=mysql-node | 250,340,352 |
| `node_mem_available_avg` | job=redis-node | 562,774,016 |
| `node_swap_free_avg` | job=backend-node | 2,042,916,352 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,673,931,264 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 160,641.14 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 21.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 70,125.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 252,601.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,901.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 149.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T13:44:34.610Z ~ 2026-08-11T13:46:34.610Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,882 | 252.95 | 73.68 | 1,448.61 | 2,727.3 | 11,225.35 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,723 | 141.33 | 32.42 | 764.79 | 1,944.99 | 11,168.41 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,256 | 160.25 | 47.28 | 810.32 | 2,102.82 | 11,253.85 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 75,131.72 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,239 | 178.5 | 81.97 | 487.18 | 2,225.9 | 11,268.9 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 169 | 403.75 | 183.43 | 1,646.4 | 2,471.04 | 3,022.07 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 47 | 1.24 | 0.71 | 5.1 | 7.82 | 87.01 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 19 | 188.31 | 29.36 | 1,485.34 | 1,728.72 | 1,788.02 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 1 | 8,062.35 | 7,874.11 | 8,518.35 | 8,575.62 | 8,062.35 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.86 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.43 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 68.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 10.1 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 121 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.52 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.72 |
| `process_rss_avg` | job=backend-node | 13,689,344 |
| `process_rss_avg` | job=monitoring-node | 18,964,480 |
| `process_rss_avg` | job=mysql-exporter | 16,556,544 |
| `process_rss_avg` | job=mysql-node | 22,367,744 |
| `process_rss_avg` | job=prometheus | 116,592,640 |
| `process_rss_avg` | job=redis-exporter | 17,972,736 |
| `process_rss_avg` | job=redis-node | 22,630,400 |
| `process_rss_max` | job=backend-node | 14,446,592 |
| `process_rss_max` | job=monitoring-node | 18,964,480 |
| `process_rss_max` | job=mysql-exporter | 16,977,920 |
| `process_rss_max` | job=mysql-node | 22,544,384 |
| `process_rss_max` | job=prometheus | 116,903,936 |
| `process_rss_max` | job=redis-exporter | 18,165,760 |
| `process_rss_max` | job=redis-node | 22,630,400 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 25.86 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.91 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 39.78 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 14 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 0.82 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 72,578.29 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 25.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 47,263,744 |
| `node_mem_available_avg` | job=monitoring-node | 409,723,392 |
| `node_mem_available_avg` | job=mysql-node | 245,229,568 |
| `node_mem_available_avg` | job=redis-node | 563,002,880 |
| `node_swap_free_avg` | job=backend-node | 2,050,185,216 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,674,020,352 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 148,988.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 12.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 92,013.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 233,875.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 5,408 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 67.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T13:46:34.610Z ~ 2026-08-11T13:48:34.610Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,250 | 871.44 | 312.59 | 1,512.66 | 21,170.88 | 33,263.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,387 | 465.98 | 131.86 | 897.47 | 2,403.39 | 33,242.91 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,176 | 591.65 | 187.59 | 1,115.44 | 18,456.91 | 33,278.7 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 418 | 1,427.21 | 319.3 | 2,451.71 | 30,000 | 33,538.27 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 608 | 1,051.14 | 216.24 | 1,334.81 | 29,207.21 | 33,858.74 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 7 | 66.1 | 5.59 | 340.54 | 354.44 | 311.21 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 3,771.94 | 425.02 | 21,474.84 | 22,620.16 | 17,373.95 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 8,062.35 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 26.2 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.4 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14.8 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 94.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 28.97 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.15 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 112.2 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 116 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.57 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 1 |
| `process_rss_avg` | job=backend-node | 13,382,656 |
| `process_rss_avg` | job=monitoring-node | 18,888,704 |
| `process_rss_avg` | job=mysql-exporter | 16,800,768 |
| `process_rss_avg` | job=mysql-node | 22,297,088 |
| `process_rss_avg` | job=prometheus | 117,067,776 |
| `process_rss_avg` | job=redis-exporter | 17,913,344 |
| `process_rss_avg` | job=redis-node | 22,630,400 |
| `process_rss_max` | job=backend-node | 14,577,664 |
| `process_rss_max` | job=monitoring-node | 18,964,480 |
| `process_rss_max` | job=mysql-exporter | 17,211,392 |
| `process_rss_max` | job=mysql-node | 22,667,264 |
| `process_rss_max` | job=prometheus | 117,297,152 |
| `process_rss_max` | job=redis-exporter | 18,296,832 |
| `process_rss_max` | job=redis-node | 22,630,400 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.6 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 96.65 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 14.53 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 19.44 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 0.69 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 157,804.57 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 1.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 64,626,688 |
| `node_mem_available_avg` | job=monitoring-node | 410,013,184 |
| `node_mem_available_avg` | job=mysql-node | 251,444,736 |
| `node_mem_available_avg` | job=redis-node | 563,042,304 |
| `node_swap_free_avg` | job=backend-node | 2,067,019,264 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,674,020,352 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 274,404.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 0 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 155,413.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 97,117.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 3,332.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 27.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T13:48:34.610Z ~ 2026-08-11T13:50:34.610Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,755 | 282.4 | 209.62 | 450.69 | 1,745.24 | 33,263.06 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,685 | 140.93 | 97.56 | 299.73 | 564.47 | 33,242.91 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 14,299 | 190.46 | 129.65 | 351.56 | 767.07 | 33,278.7 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,009 | 312.2 | 250.33 | 490.79 | 1,248.61 | 33,538.27 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,613 | 166.64 | 121.89 | 345.39 | 673.18 | 33,858.74 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 15 | 20.8 | 7.69 | 119.68 | 131.31 | 311.21 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 18 | 143.07 | 156.59 | 228.17 | 242.49 | 17,373.95 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.29 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.71 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.57 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 124.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 7.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 110.29 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 112 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.82 |
| `process_rss_avg` | job=backend-node | 13,173,248 |
| `process_rss_avg` | job=monitoring-node | 18,911,232 |
| `process_rss_avg` | job=mysql-exporter | 16,579,072 |
| `process_rss_avg` | job=mysql-node | 22,165,504 |
| `process_rss_avg` | job=prometheus | 117,526,528 |
| `process_rss_avg` | job=redis-exporter | 17,402,368 |
| `process_rss_avg` | job=redis-node | 22,630,400 |
| `process_rss_max` | job=backend-node | 15,466,496 |
| `process_rss_max` | job=monitoring-node | 18,944,000 |
| `process_rss_max` | job=mysql-exporter | 17,190,912 |
| `process_rss_max` | job=mysql-node | 22,384,640 |
| `process_rss_max` | job=prometheus | 118,214,656 |
| `process_rss_max` | job=redis-exporter | 17,575,936 |
| `process_rss_max` | job=redis-node | 22,630,400 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 98.99 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 49.45 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 26.39 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 1.21 |
| `node_load1_avg` | job=redis-node | 0.02 |
| `node_major_fault_delta` | job=backend-node | 138,354.29 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 49.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 52,077,568 |
| `node_mem_available_avg` | job=monitoring-node | 410,472,448 |
| `node_mem_available_avg` | job=mysql-node | 249,122,816 |
| `node_mem_available_avg` | job=redis-node | 563,057,664 |
| `node_swap_free_avg` | job=backend-node | 2,064,162,304 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,674,020,352 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 225,598.86 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 17.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 81,873.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 366,829.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 9,100.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 138.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-postmerge-pure-throughput-sse500-fullramp-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T13:52:38.616Z ~ 2026-08-11T14:06:21.169Z
- 설정: `{"sseVUs":500,"totalSseConnections":1000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T13:53:13.616Z ~ 2026-08-11T13:55:13.616Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,369 | 395 | 108.5 | 3,179.47 | 5,575.11 | 6,154.3 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,192 | 74.2 | 17.49 | 355.21 | 657.07 | 3,263.56 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,891 | 164.27 | 40.63 | 567.43 | 2,501.1 | 4,511.3 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 846,161.58 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 846,122.97 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 943.58 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 251.15 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,193 | 198.75 | 64.69 | 1,018.44 | 1,582.28 | 2,119.94 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 125 | 277.23 | 185.35 | 703.3 | 1,453.13 | 1,437.57 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 56 | 75.09 | 0.78 | 44.18 | 1,714.41 | 1,510.47 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 4 | 333.27 | 78.29 | 970.84 | 981.58 | 915.55 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.17 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.33 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 60 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.33 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 24.13 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 9.05 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.06 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 111 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.34 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.55 |
| `process_rss_avg` | job=backend-node | 12,516,864 |
| `process_rss_avg` | job=monitoring-node | 18,872,320 |
| `process_rss_avg` | job=mysql-exporter | 16,630,272 |
| `process_rss_avg` | job=mysql-node | 22,206,976 |
| `process_rss_avg` | job=prometheus | 118,394,880 |
| `process_rss_avg` | job=redis-exporter | 18,345,984 |
| `process_rss_avg` | job=redis-node | 22,601,728 |
| `process_rss_max` | job=backend-node | 13,365,248 |
| `process_rss_max` | job=monitoring-node | 18,956,288 |
| `process_rss_max` | job=mysql-exporter | 17,244,160 |
| `process_rss_max` | job=mysql-node | 22,335,488 |
| `process_rss_max` | job=prometheus | 118,476,800 |
| `process_rss_max` | job=redis-exporter | 18,345,984 |
| `process_rss_max` | job=redis-node | 22,601,728 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 25.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.17 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 75.59 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.19 |
| `node_cpu_pct_avg` | job=mysql-node | 19.68 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 5.23 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 0.26 |
| `node_load1_avg` | job=redis-node | 0.02 |
| `node_major_fault_delta` | job=backend-node | 105,313.14 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 35.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 37,949,440 |
| `node_mem_available_avg` | job=monitoring-node | 413,342,208 |
| `node_mem_available_avg` | job=mysql-node | 242,960,896 |
| `node_mem_available_avg` | job=redis-node | 562,839,552 |
| `node_swap_free_avg` | job=backend-node | 2,094,901,248 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,673,160,192 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 171,904 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 40 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 81,373.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 102,019.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 2 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,406.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 64 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T13:55:13.616Z ~ 2026-08-11T13:57:13.616Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,694 | 62.25 | 15.36 | 296.3 | 501.66 | 6,154.3 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,698 | 39.7 | 7.75 | 193.93 | 398.05 | 8,716.39 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,087 | 48.32 | 11.02 | 235.12 | 456.1 | 8,739.2 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 1 | 57.9 | 58.72 | 61.24 | 61.46 | 57.9 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 251.15 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,603 | 63.87 | 20.66 | 316.24 | 475.34 | 8,816.08 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 58 | 166.2 | 145.4 | 371.34 | 432.18 | 1,437.57 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 73 | 0.96 | 0.58 | 2.38 | 18.79 | 1,510.47 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 7.5 | 6.64 | 9.58 | 9.74 | 915.55 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.71 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.29 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.43 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 38.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.51 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.39 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.75 |
| `process_rss_avg` | job=backend-node | 12,302,848 |
| `process_rss_avg` | job=monitoring-node | 18,939,392 |
| `process_rss_avg` | job=mysql-exporter | 16,571,904 |
| `process_rss_avg` | job=mysql-node | 22,216,192 |
| `process_rss_avg` | job=prometheus | 118,476,800 |
| `process_rss_avg` | job=redis-exporter | 18,378,752 |
| `process_rss_avg` | job=redis-node | 22,671,872 |
| `process_rss_max` | job=backend-node | 15,138,816 |
| `process_rss_max` | job=monitoring-node | 18,956,288 |
| `process_rss_max` | job=mysql-exporter | 16,863,232 |
| `process_rss_max` | job=mysql-node | 22,421,504 |
| `process_rss_max` | job=prometheus | 118,476,800 |
| `process_rss_max` | job=redis-exporter | 18,477,056 |
| `process_rss_max` | job=redis-node | 22,732,800 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7.86 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 74.43 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 30.25 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 2.96 |
| `node_load1_avg` | job=monitoring-node | 0.02 |
| `node_load1_avg` | job=mysql-node | 0.64 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 46,222.86 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 8 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 41,517,056 |
| `node_mem_available_avg` | job=monitoring-node | 411,096,064 |
| `node_mem_available_avg` | job=mysql-node | 246,824,448 |
| `node_mem_available_avg` | job=redis-node | 562,839,552 |
| `node_swap_free_avg` | job=backend-node | 2,096,250,368 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,673,160,192 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 79,011.43 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 8 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 29,848 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 155,958.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,603.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 26.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T13:57:13.616Z ~ 2026-08-11T13:59:13.616Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,857 | 58.82 | 58.92 | 93.47 | 120.09 | 867.72 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,949 | 27.4 | 26.95 | 45.37 | 59.94 | 8,716.39 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 8,710 | 40.65 | 40.44 | 65.52 | 87.47 | 8,739.2 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 57.9 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,865 | 78.39 | 79.32 | 121.98 | 147.58 | 8,816.08 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 23 | 46.12 | 41.38 | 89.48 | 97.9 | 430.18 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 80 | 1.15 | 0.71 | 4.02 | 5.1 | 20.72 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 13 | 25.65 | 26.56 | 43.2 | 44.43 | 40.7 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 54.86 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.83 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.02 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.65 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.69 |
| `process_rss_avg` | job=backend-node | 15,767,552 |
| `process_rss_avg` | job=monitoring-node | 18,796,032 |
| `process_rss_avg` | job=mysql-exporter | 16,798,208 |
| `process_rss_avg` | job=mysql-node | 22,246,400 |
| `process_rss_avg` | job=prometheus | 118,870,016 |
| `process_rss_avg` | job=redis-exporter | 18,378,752 |
| `process_rss_avg` | job=redis-node | 22,502,400 |
| `process_rss_max` | job=backend-node | 17,965,056 |
| `process_rss_max` | job=monitoring-node | 18,821,120 |
| `process_rss_max` | job=mysql-exporter | 17,125,376 |
| `process_rss_max` | job=mysql-node | 22,405,120 |
| `process_rss_max` | job=prometheus | 119,263,232 |
| `process_rss_max` | job=redis-exporter | 18,477,056 |
| `process_rss_max` | job=redis-node | 22,568,960 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 11 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.93 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 50.1 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 10.1 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.83 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 12,906.29 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 46.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 40,372,224 |
| `node_mem_available_avg` | job=monitoring-node | 411,400,704 |
| `node_mem_available_avg` | job=mysql-node | 247,990,784 |
| `node_mem_available_avg` | job=redis-node | 562,839,552 |
| `node_swap_free_avg` | job=backend-node | 2,095,350,784 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,673,160,192 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 21,045.71 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 80 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 6,414.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 266,085.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 694.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 27.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T13:59:13.616Z ~ 2026-08-11T14:01:13.616Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,406 | 267.74 | 71.76 | 420.55 | 5,488.61 | 22,623.11 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,566 | 134.59 | 32.63 | 136.45 | 3,715.15 | 22,287.05 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,468 | 167.7 | 48.22 | 139.67 | 3,791.5 | 22,468.28 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,243 | 167.46 | 93.63 | 141.97 | 178.48 | 23,480.59 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 14 | 43.94 | 37.75 | 93.16 | 98.63 | 120.75 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 30 | 352.33 | 0.75 | 4,760.26 | 5,533.35 | 4,743.66 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 4 | 34.49 | 27.96 | 60.4 | 61.29 | 56.73 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10.33 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.33 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.11 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 33.33 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.22 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 18.57 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.23 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.43 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.51 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.72 |
| `process_rss_avg` | job=backend-node | 13,639,680 |
| `process_rss_avg` | job=monitoring-node | 18,792,448 |
| `process_rss_avg` | job=mysql-exporter | 16,508,928 |
| `process_rss_avg` | job=mysql-node | 22,355,456 |
| `process_rss_avg` | job=prometheus | 119,263,232 |
| `process_rss_avg` | job=redis-exporter | 17,484,800 |
| `process_rss_avg` | job=redis-node | 22,556,672 |
| `process_rss_max` | job=backend-node | 15,216,640 |
| `process_rss_max` | job=monitoring-node | 18,792,448 |
| `process_rss_max` | job=mysql-exporter | 17,113,088 |
| `process_rss_max` | job=mysql-node | 22,634,496 |
| `process_rss_max` | job=prometheus | 119,263,232 |
| `process_rss_max` | job=redis-exporter | 18,083,840 |
| `process_rss_max` | job=redis-node | 22,671,360 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 14.17 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.17 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.25 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.23 |
| `node_cpu_pct_avg` | job=mysql-node | 18.2 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 13.8 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.06 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 121,538.29 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 70.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 57,916,928 |
| `node_mem_available_avg` | job=monitoring-node | 410,194,432 |
| `node_mem_available_avg` | job=mysql-node | 245,469,184 |
| `node_mem_available_avg` | job=redis-node | 562,842,624 |
| `node_swap_free_avg` | job=backend-node | 2,092,994,560 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,673,160,192 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 212,588.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 33.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 130,768 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 124,816 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 315.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 14.86 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 30.88 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T14:01:13.616Z ~ 2026-08-11T14:03:13.616Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,408 | 616.84 | 272.77 | 2,357.76 | 5,125.33 | 35,100.15 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,208 | 220.32 | 109.87 | 697.93 | 1,753.78 | 32,948.08 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,091 | 315.89 | 174.76 | 1,070.05 | 2,542.98 | 34,113.63 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 104 | 582,098.47 | 30,000 | 30,000 | 30,000 | 599,803.38 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 33,000.67 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 34,242.86 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 33,435.97 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 524 | 678.71 | 315.59 | 3,404.66 | 5,523.12 | 23,480.59 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 303 | 373.11 | 140.93 | 1,485.34 | 5,578.69 | 7,091.79 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 4 | 457.11 | 30.76 | 1,377.97 | 1,420.92 | 4,743.66 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 5 | 172.11 | 55.92 | 608.45 | 622.77 | 547.04 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.33 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.5 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 102.42 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 18.17 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 110.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 112 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_rss_avg` | job=backend-node | 13,790,208 |
| `process_rss_avg` | job=monitoring-node | 18,765,824 |
| `process_rss_avg` | job=mysql-exporter | 16,613,888 |
| `process_rss_avg` | job=mysql-node | 22,229,504 |
| `process_rss_avg` | job=prometheus | 117,252,096 |
| `process_rss_avg` | job=redis-exporter | 17,637,376 |
| `process_rss_avg` | job=redis-node | 22,671,360 |
| `process_rss_max` | job=backend-node | 17,063,936 |
| `process_rss_max` | job=monitoring-node | 18,792,448 |
| `process_rss_max` | job=mysql-exporter | 16,965,632 |
| `process_rss_max` | job=mysql-node | 22,675,456 |
| `process_rss_max` | job=prometheus | 120,049,664 |
| `process_rss_max` | job=redis-exporter | 17,817,600 |
| `process_rss_max` | job=redis-node | 22,671,360 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 466.5 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 500 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 33.83 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.52 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 18.27 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 19.33 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.47 |
| `node_load1_avg` | job=redis-node | 0.03 |
| `node_major_fault_delta` | job=backend-node | 143,886.86 |
| `node_major_fault_delta` | job=monitoring-node | 4.57 |
| `node_major_fault_delta` | job=mysql-node | 3.43 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 52,370,944 |
| `node_mem_available_avg` | job=monitoring-node | 405,799,424 |
| `node_mem_available_avg` | job=mysql-node | 247,698,944 |
| `node_mem_available_avg` | job=redis-node | 562,866,176 |
| `node_swap_free_avg` | job=backend-node | 2,070,593,536 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,673,160,192 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 255,249.14 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 11.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 163,604.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 117,398.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 12,549.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 43.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T14:03:13.616Z ~ 2026-08-11T14:05:13.616Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,930 | 341.97 | 237.31 | 855.58 | 1,973.06 | 35,100.15 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,968 | 219.14 | 137.06 | 521.09 | 1,253.47 | 32,948.08 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,510 | 306.48 | 172.55 | 651.49 | 1,651.22 | 34,113.63 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 603,421.16 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 33,000.67 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 34,242.86 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 33,435.97 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,416 | 564.76 | 284.73 | 908.21 | 2,365.81 | 25,264.02 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,611 | 335.8 | 162.18 | 534.24 | 1,592.72 | 24,971.44 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 9 | 35.15 | 8.39 | 214.75 | 221.91 | 1,341.09 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 268.5 | 309.65 | 353.09 | 356.95 | 547.04 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.67 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.83 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 210.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 24.98 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 16.19 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.36 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 112 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 112 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.6 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.81 |
| `process_rss_avg` | job=backend-node | 12,652,032 |
| `process_rss_avg` | job=monitoring-node | 18,808,832 |
| `process_rss_avg` | job=mysql-exporter | 16,647,680 |
| `process_rss_avg` | job=mysql-node | 22,282,752 |
| `process_rss_avg` | job=prometheus | 114,978,816 |
| `process_rss_avg` | job=redis-exporter | 18,096,128 |
| `process_rss_avg` | job=redis-node | 22,671,360 |
| `process_rss_max` | job=backend-node | 13,094,912 |
| `process_rss_max` | job=monitoring-node | 18,874,368 |
| `process_rss_max` | job=mysql-exporter | 17,555,456 |
| `process_rss_max` | job=mysql-node | 22,839,296 |
| `process_rss_max` | job=prometheus | 114,978,816 |
| `process_rss_max` | job=redis-exporter | 18,210,816 |
| `process_rss_max` | job=redis-node | 22,671,360 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 300 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 500 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 300 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 98 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 36.13 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 19.57 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.53 |
| `node_load1_avg` | job=redis-node | 0.01 |
| `node_major_fault_delta` | job=backend-node | 138,857.14 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 144 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 45,630,464 |
| `node_mem_available_avg` | job=monitoring-node | 405,682,688 |
| `node_mem_available_avg` | job=mysql-node | 245,208,064 |
| `node_mem_available_avg` | job=redis-node | 562,868,224 |
| `node_swap_free_avg` | job=backend-node | 2,037,560,832 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,673,160,192 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 233,329.14 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 20.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 118,843.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 237,048 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 5,082.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 69.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 30.88 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-postmerge-pure-throughput-sse1000-fullramp-20260811.json

- 시나리오: `pure-throughput`
- K6 실행: 2026-08-11T14:07:19.921Z ~ 2026-08-11T14:21:03.052Z
- 설정: `{"sseVUs":1000,"totalSseConnections":2000,"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m"}`

### QPS 50 — 2026-08-11T14:07:54.921Z ~ 2026-08-11T14:09:54.921Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 344 | 475.86 | 346.07 | 1,046.09 | 3,883.37 | 5,165.37 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 186 | 127.42 | 72.7 | 422.34 | 495.79 | 9,428.53 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 802 | 250.33 | 159.02 | 625.71 | 2,146.01 | 9,789.83 |
| method=GET, status=200, uri=/api/auctions/stream | 10 | 7,829.12 | 6,622.78 | 12,808.43 | 30,000 | 874,131.85 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 35 | 78,255.69 | 30,000 | 30,000 | 30,000 | 875,497.22 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 862,135.99 |
| method=GET, status=401, uri=UNKNOWN | 132 | 3.04 | 0.55 | 3.74 | 164.19 | 2,096.11 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 393.89 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 469.53 |
| method=POST, status=200, uri=/api/sse/tickets | 954 | 33.11 | 0.65 | 20.13 | 1,283.72 | 2,066.85 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 11 | 2,548.62 | 2,952.79 | 3,516.5 | 3,566.61 | 11,892.98 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 9,616.77 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 5 | 3.11 | 3.67 | 5.38 | 5.55 | 1,180.54 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.33 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.6 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 43.2 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.6 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 33.69 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 14.16 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.62 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.33 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.11 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.22 |
| `process_rss_avg` | job=backend-node | 13,199,872 |
| `process_rss_avg` | job=monitoring-node | 19,021,824 |
| `process_rss_avg` | job=mysql-exporter | 16,717,312 |
| `process_rss_avg` | job=mysql-node | 22,328,832 |
| `process_rss_avg` | job=prometheus | 115,765,248 |
| `process_rss_avg` | job=redis-exporter | 18,083,840 |
| `process_rss_avg` | job=redis-node | 22,536,192 |
| `process_rss_max` | job=backend-node | 14,647,296 |
| `process_rss_max` | job=monitoring-node | 19,087,360 |
| `process_rss_max` | job=mysql-exporter | 17,244,160 |
| `process_rss_max` | job=mysql-node | 22,663,168 |
| `process_rss_max` | job=prometheus | 115,765,248 |
| `process_rss_max` | job=redis-exporter | 18,296,832 |
| `process_rss_max` | job=redis-node | 22,536,192 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 829.33 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 479 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 493 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 33.33 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 96.9 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.1 |
| `node_cpu_pct_avg` | job=mysql-node | 5.88 |
| `node_cpu_pct_avg` | job=redis-node | 0.57 |
| `node_load1_avg` | job=backend-node | 7.74 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 0.48 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 153,921.14 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 0 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 52,511,232 |
| `node_mem_available_avg` | job=monitoring-node | 404,954,112 |
| `node_mem_available_avg` | job=mysql-node | 239,925,248 |
| `node_mem_available_avg` | job=redis-node | 553,778,688 |
| `node_swap_free_avg` | job=backend-node | 2,036,105,216 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,139,456 |
| `node_swap_free_avg` | job=mysql-node | 2,673,647,616 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 280,933.71 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 0 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 149,082.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 32,979.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 854.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 5.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T14:09:54.921Z ~ 2026-08-11T14:11:54.921Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 416 | 360.7 | 265.64 | 954.74 | 1,750.2 | 38,575.98 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 293 | 132.52 | 74.57 | 414.29 | 655.88 | 35,626.15 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 1,006 | 206.67 | 133.2 | 624.86 | 1,151.29 | 37,443.71 |
| method=GET, status=200, uri=/api/auctions/stream | 8 | 25,927.33 | 25,769.8 | 28,346.78 | 28,575.85 | 87,159.22 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 78,658.79 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 4 | 1.62 | 0.75 | 5.38 | 5.55 | 2,096.11 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 37,400.71 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 38,266.38 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 2,140.24 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 109 | 10.42 | 0.71 | 20.41 | 332.72 | 2,066.85 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 121 | 406.61 | 327.35 | 1,210.94 | 1,795.88 | 35,621.39 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 19 | 417.3 | 328.96 | 1,521.13 | 1,735.88 | 34,814.27 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 3 | 189.42 | 9.79 | 438.44 | 445.6 | 369.49 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 1 | 1,338.37 | 1,252.7 | 1,413.76 | 1,428.08 | 38,111.03 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12.2 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.2 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.2 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 32.5 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.2 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 120.2 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 125 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.19 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.39 |
| `process_rss_avg` | job=backend-node | 14,330,880 |
| `process_rss_avg` | job=monitoring-node | 19,053,568 |
| `process_rss_avg` | job=mysql-exporter | 16,523,776 |
| `process_rss_avg` | job=mysql-node | 22,253,056 |
| `process_rss_avg` | job=prometheus | 116,499,456 |
| `process_rss_avg` | job=redis-exporter | 18,522,112 |
| `process_rss_avg` | job=redis-node | 22,536,192 |
| `process_rss_max` | job=backend-node | 15,581,184 |
| `process_rss_max` | job=monitoring-node | 19,087,360 |
| `process_rss_max` | job=mysql-exporter | 17,010,688 |
| `process_rss_max` | job=mysql-node | 22,507,520 |
| `process_rss_max` | job=prometheus | 116,940,800 |
| `process_rss_max` | job=redis-exporter | 18,800,640 |
| `process_rss_max` | job=redis-node | 22,536,192 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 996.4 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 543 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 1,000 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 543 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 20.4 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.5 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 9.55 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 8.83 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 0.74 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 154,720 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 25.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 60,005,376 |
| `node_mem_available_avg` | job=monitoring-node | 406,367,232 |
| `node_mem_available_avg` | job=mysql-node | 238,729,728 |
| `node_mem_available_avg` | job=redis-node | 560,846,848 |
| `node_swap_free_avg` | job=backend-node | 2,026,276,352 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,143,040 |
| `node_swap_free_avg` | job=mysql-node | 2,673,648,640 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 256,353.14 |
| `node_swap_in_delta` | job=monitoring-node | 2.29 |
| `node_swap_in_delta` | job=mysql-node | 2.29 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 152,657.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 55,381.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 3,390.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 28.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T14:11:54.921Z ~ 2026-08-11T14:13:54.921Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 362 | 471.01 | 216.99 | 2,174.33 | 3,544.24 | 38,575.98 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 504 | 198.26 | 46.42 | 979.79 | 2,217.87 | 35,626.15 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 1,224 | 249.25 | 65.6 | 916.41 | 3,238.52 | 37,443.71 |
| method=GET, status=200, uri=/api/auctions/stream | 759 | 268,063.13 | 30,000 | 30,000 | 30,000 | 312,754.67 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 1 | 352,443.36 | 30,000 | 30,000 | 30,000 | 352,443.36 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 1,681.41 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 33,299.65 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 37,400.71 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 38,266.38 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 208 | 43.4 | 0.65 | 131.98 | 2,026.55 | 2,215.75 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 57 | 5,609.69 | 6,203.84 | 10,558.46 | 11,274.29 | 35,621.39 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 10 | 553.18 | 6.76 | 5,082.38 | 5,597.77 | 34,814.27 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 1 | 423.93 | 402.65 | 442.92 | 446.5 | 423.93 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 38,111.03 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 16.17 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13.83 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7.17 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 96 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 41.03 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 13 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.62 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 109.17 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 112 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.21 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.35 |
| `process_rss_avg` | job=backend-node | 12,925,952 |
| `process_rss_avg` | job=monitoring-node | 18,817,024 |
| `process_rss_avg` | job=mysql-exporter | 16,423,424 |
| `process_rss_avg` | job=mysql-node | 22,187,520 |
| `process_rss_avg` | job=prometheus | 116,037,632 |
| `process_rss_avg` | job=redis-exporter | 17,714,688 |
| `process_rss_avg` | job=redis-node | 22,701,056 |
| `process_rss_max` | job=backend-node | 15,450,112 |
| `process_rss_max` | job=monitoring-node | 18,817,024 |
| `process_rss_max` | job=mysql-exporter | 16,904,192 |
| `process_rss_max` | job=mysql-node | 22,319,104 |
| `process_rss_max` | job=prometheus | 116,940,800 |
| `process_rss_max` | job=redis-exporter | 18,280,448 |
| `process_rss_max` | job=redis-node | 22,921,216 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 607 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 675.5 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 999 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 676 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 33.33 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.33 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.15 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 5.88 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 7.63 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 0.21 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 179,361.14 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 4.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 57,805,824 |
| `node_mem_available_avg` | job=monitoring-node | 406,606,848 |
| `node_mem_available_avg` | job=mysql-node | 238,184,448 |
| `node_mem_available_avg` | job=redis-node | 556,540,928 |
| `node_swap_free_avg` | job=backend-node | 2,024,448,000 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,136 |
| `node_swap_free_avg` | job=mysql-node | 2,673,661,952 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 264,141.71 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 3.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 142,021.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 24,595.43 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,587.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 3.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T14:13:54.921Z ~ 2026-08-11T14:15:54.921Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,039 | 618.82 | 247.36 | 2,013.41 | 23,033.75 | 33,682.37 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,084 | 340.08 | 111.85 | 716.57 | 2,369.39 | 32,278.96 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,523 | 409.21 | 150.71 | 807.99 | 2,414.9 | 32,529.24 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 312,754.67 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 352,443.36 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 72 | 2.64 | 0.8 | 9.75 | 24.55 | 568.03 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 33,299.65 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 142 | 15.37 | 0.74 | 44.74 | 244.57 | 2,215.75 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 558 | 922.14 | 306.01 | 1,641.93 | 25,924.42 | 33,735.15 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 556 | 586.47 | 174.04 | 1,160.24 | 24,137.72 | 32,879.47 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 4 | 35.78 | 10.49 | 98.42 | 99.68 | 423.93 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 33,669.96 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.2 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 11.8 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12.6 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.18 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 133.37 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.18 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 26.93 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 15.83 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.63 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 111.2 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 112 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.3 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.58 |
| `process_rss_avg` | job=backend-node | 12,179,968 |
| `process_rss_avg` | job=monitoring-node | 18,817,024 |
| `process_rss_avg` | job=mysql-exporter | 16,752,640 |
| `process_rss_avg` | job=mysql-node | 22,411,264 |
| `process_rss_avg` | job=prometheus | 115,736,576 |
| `process_rss_avg` | job=redis-exporter | 18,094,592 |
| `process_rss_avg` | job=redis-node | 22,626,304 |
| `process_rss_max` | job=backend-node | 13,352,960 |
| `process_rss_max` | job=monitoring-node | 18,817,024 |
| `process_rss_max` | job=mysql-exporter | 16,977,920 |
| `process_rss_max` | job=mysql-node | 22,835,200 |
| `process_rss_max` | job=prometheus | 115,736,576 |
| `process_rss_max` | job=redis-exporter | 18,292,736 |
| `process_rss_max` | job=redis-node | 22,626,304 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 334 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 733.4 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 334 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 748 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 32.6 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 97.5 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 18.93 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 11.91 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.04 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 166,933.71 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 29.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 50,618,880 |
| `node_mem_available_avg` | job=monitoring-node | 407,107,584 |
| `node_mem_available_avg` | job=mysql-node | 237,865,984 |
| `node_mem_available_avg` | job=redis-node | 566,266,368 |
| `node_swap_free_avg` | job=backend-node | 2,024,064,000 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,673,668,096 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 255,246.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 12.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 148,456 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 105,788.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 3,665.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 29.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T14:15:54.921Z ~ 2026-08-11T14:17:54.921Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,178 | 611.83 | 385.37 | 1,672.96 | 7,384.48 | 36,320.01 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,238 | 294.73 | 169.75 | 944 | 1,721.57 | 24,358.04 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,026 | 420.31 | 233.02 | 1,314.25 | 2,957.56 | 35,846.88 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 93 | 2.56 | 0.73 | 12.79 | 14.86 | 28.36 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 34,627.07 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 36,528.09 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 34,559.52 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 274 | 14.04 | 0.7 | 21.04 | 430.39 | 982.05 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 710 | 705.07 | 426.02 | 1,725.52 | 8,859.9 | 36,424.02 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 284 | 313.63 | 225.09 | 1,060.84 | 1,638.05 | 25,313.66 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 3 | 92.78 | 27.96 | 176.72 | 178.51 | 159.09 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 1 | 378.36 | 402.65 | 442.92 | 446.5 | 387.46 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 34,134.45 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.6 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.4 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 17.2 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 107.5 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 18.53 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 116.4 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 118 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.45 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.8 |
| `process_rss_avg` | job=backend-node | 12,472,320 |
| `process_rss_avg` | job=monitoring-node | 18,817,024 |
| `process_rss_avg` | job=mysql-exporter | 16,461,312 |
| `process_rss_avg` | job=mysql-node | 22,299,648 |
| `process_rss_avg` | job=prometheus | 115,736,576 |
| `process_rss_avg` | job=redis-exporter | 17,727,488 |
| `process_rss_avg` | job=redis-node | 22,626,304 |
| `process_rss_max` | job=backend-node | 13,025,280 |
| `process_rss_max` | job=monitoring-node | 18,817,024 |
| `process_rss_max` | job=mysql-exporter | 16,850,944 |
| `process_rss_max` | job=mysql-node | 22,540,288 |
| `process_rss_max` | job=prometheus | 115,736,576 |
| `process_rss_max` | job=redis-exporter | 18,149,376 |
| `process_rss_max` | job=redis-node | 22,626,304 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 334 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 772.4 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 334 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 805 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.4 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 94.58 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.12 |
| `node_cpu_pct_avg` | job=mysql-node | 17.24 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 14.02 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.35 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 152,547.43 |
| `node_major_fault_delta` | job=monitoring-node | 3.43 |
| `node_major_fault_delta` | job=mysql-node | 20.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 52,702,720 |
| `node_mem_available_avg` | job=monitoring-node | 407,595,520 |
| `node_mem_available_avg` | job=mysql-node | 238,575,616 |
| `node_mem_available_avg` | job=redis-node | 571,906,048 |
| `node_swap_free_avg` | job=backend-node | 1,989,532,160 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,673,668,096 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 255,877.71 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 8 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 158,728 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 108,882.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 6,214.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 36.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T14:17:54.921Z ~ 2026-08-11T14:19:54.921Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,042 | 229.63 | 212.78 | 434.5 | 676.28 | 102,340.36 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,016 | 144.51 | 126.5 | 326.64 | 441.35 | 3,121.81 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,786 | 173.51 | 160.82 | 353.96 | 491.89 | 102,283.82 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 707,766.69 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 642 | 589,546.15 | 30,000 | 30,000 | 30,000 | 722,453.35 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 1,001.83 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 102,003.47 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 102,154.62 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 102,155.26 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 96 | 2.85 | 0.83 | 13.42 | 21.03 | 982.05 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 246 | 261.65 | 247.08 | 506.27 | 605.77 | 102,213.07 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 114 | 164.18 | 150.99 | 392.21 | 470.01 | 3,626.86 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 4 | 262.69 | 1 | 533.18 | 536.13 | 525.06 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 796.45 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 98,454.71 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 118 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.84 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 123 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 123 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.51 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.69 |
| `process_rss_avg` | job=backend-node | 13,553,664 |
| `process_rss_avg` | job=monitoring-node | 18,817,024 |
| `process_rss_avg` | job=mysql-exporter | 16,642,560 |
| `process_rss_avg` | job=mysql-node | 22,286,848 |
| `process_rss_avg` | job=prometheus | 115,736,576 |
| `process_rss_avg` | job=redis-exporter | 18,282,496 |
| `process_rss_avg` | job=redis-node | 22,626,304 |
| `process_rss_max` | job=backend-node | 14,635,008 |
| `process_rss_max` | job=monitoring-node | 18,817,024 |
| `process_rss_max` | job=mysql-exporter | 17,145,856 |
| `process_rss_max` | job=mysql-node | 22,515,712 |
| `process_rss_max` | job=prometheus | 115,736,576 |
| `process_rss_max` | job=redis-exporter | 18,563,072 |
| `process_rss_max` | job=redis-node | 22,626,304 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 136 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 263 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 92.86 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.13 |
| `node_cpu_pct_avg` | job=mysql-node | 12.78 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 11.79 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 0.75 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 158,963.43 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 4.57 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 51,046,912 |
| `node_mem_available_avg` | job=monitoring-node | 413,907,968 |
| `node_mem_available_avg` | job=mysql-node | 236,465,664 |
| `node_mem_available_avg` | job=redis-node | 571,495,424 |
| `node_swap_free_avg` | job=backend-node | 1,971,425,792 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,673,668,096 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 275,310.86 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 5.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 150,782.86 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 83,988.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,474.29 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 32 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-postmerge-hot-auction-pattern-sse250-20260811.json

- 시나리오: `hot-auction-pattern`
- K6 실행: 2026-08-11T14:22:27.563Z ~ 2026-08-11T14:30:28.554Z
- 설정: `{"auctionCount":200,"hotAuctionCount":3,"hotAuctionRate":14,"coldAuctionRatePerAuction":0.09,"coldAuctionRate":18,"sseUsers":250,"totalSseConnections":500,"duration":"5m"}`

### 0~1분 — 2026-08-11T14:23:32.563Z ~ 2026-08-11T14:24:32.563Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,043 | 128.96 | 52.77 | 305.98 | 2,386.26 | 10,998.74 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 202.68 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 136,400.05 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 22.73 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 170.19 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 223.01 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,805 | 112.09 | 73.31 | 336.42 | 503.39 | 11,027.42 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,229 | 182.46 | 152.62 | 485.33 | 694.71 | 10,969.33 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 32 | 2.66 | 1 | 12.3 | 13.65 | 16.31 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 127.72 | 22.37 | 420.55 | 442.02 | 380.34 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.5 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 22 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 50.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.33 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.45 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 110.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 111 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.5 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.69 |
| `process_rss_avg` | job=backend-node | 12,781,568 |
| `process_rss_avg` | job=monitoring-node | 18,923,520 |
| `process_rss_avg` | job=mysql-exporter | 16,901,120 |
| `process_rss_avg` | job=mysql-node | 22,220,800 |
| `process_rss_avg` | job=prometheus | 117,080,064 |
| `process_rss_avg` | job=redis-exporter | 17,289,216 |
| `process_rss_avg` | job=redis-node | 22,720,512 |
| `process_rss_max` | job=backend-node | 13,266,944 |
| `process_rss_max` | job=monitoring-node | 18,923,520 |
| `process_rss_max` | job=mysql-exporter | 17,158,144 |
| `process_rss_max` | job=mysql-node | 22,421,504 |
| `process_rss_max` | job=prometheus | 117,178,368 |
| `process_rss_max` | job=redis-exporter | 17,387,520 |
| `process_rss_max` | job=redis-node | 22,720,512 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 16 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 98.01 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.23 |
| `node_cpu_pct_avg` | job=mysql-node | 44.1 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 4.41 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 0.68 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 38,376 |
| `node_major_fault_delta` | job=monitoring-node | 1.33 |
| `node_major_fault_delta` | job=mysql-node | 13.33 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 61,118,464 |
| `node_mem_available_avg` | job=monitoring-node | 415,304,704 |
| `node_mem_available_avg` | job=mysql-node | 232,359,936 |
| `node_mem_available_avg` | job=redis-node | 570,753,024 |
| `node_swap_free_avg` | job=backend-node | 1,983,537,152 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,673,111,040 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 89,693.33 |
| `node_swap_in_delta` | job=monitoring-node | 1.33 |
| `node_swap_in_delta` | job=mysql-node | 13.33 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 32,032 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 141,277.33 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 12 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 165,853.33 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,556 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 6.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 1~2분 — 2026-08-11T14:24:32.563Z ~ 2026-08-11T14:25:32.563Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,599 | 26.65 | 25.29 | 47.08 | 59.73 | 10,998.74 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 202.68 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 170.19 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 223.01 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,525 | 47.04 | 43.44 | 82.33 | 102.27 | 11,027.42 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,059 | 21.42 | 18.72 | 45.22 | 71.06 | 10,969.33 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 40 | 0.66 | 0.6 | 1.66 | 5.17 | 13.54 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 11 | 13.86 | 11.88 | 25.73 | 27.51 | 380.34 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 1 | 300.89 | 336.18 | 603.93 | 621.87 | 300.89 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 8 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 18.67 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.33 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.26 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 111 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 111 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.6 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.62 |
| `process_rss_avg` | job=backend-node | 13,039,616 |
| `process_rss_avg` | job=monitoring-node | 18,923,520 |
| `process_rss_avg` | job=mysql-exporter | 16,635,904 |
| `process_rss_avg` | job=mysql-node | 22,368,256 |
| `process_rss_avg` | job=prometheus | 117,178,368 |
| `process_rss_avg` | job=redis-exporter | 17,356,800 |
| `process_rss_avg` | job=redis-node | 22,810,624 |
| `process_rss_max` | job=backend-node | 13,328,384 |
| `process_rss_max` | job=monitoring-node | 18,923,520 |
| `process_rss_max` | job=mysql-exporter | 17,080,320 |
| `process_rss_max` | job=mysql-node | 22,491,136 |
| `process_rss_max` | job=prometheus | 117,178,368 |
| `process_rss_max` | job=redis-exporter | 17,600,512 |
| `process_rss_max` | job=redis-node | 23,113,728 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 6 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 90.54 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 67.26 |
| `node_cpu_pct_avg` | job=redis-node | 0.33 |
| `node_load1_avg` | job=backend-node | 6.87 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.46 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 9,078.67 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 34.67 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 43,532,288 |
| `node_mem_available_avg` | job=monitoring-node | 415,234,048 |
| `node_mem_available_avg` | job=mysql-node | 238,813,184 |
| `node_mem_available_avg` | job=redis-node | 570,753,024 |
| `node_swap_free_avg` | job=backend-node | 1,986,599,936 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,673,112,064 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 12,228 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 37.33 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 6,380 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 141,332 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,416 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 342.67 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 4.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 2~3분 — 2026-08-11T14:25:32.563Z ~ 2026-08-11T14:26:32.563Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,597 | 103.03 | 48.9 | 344.43 | 610.24 | 10,998.74 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
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
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0.31 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,716 | 101.21 | 63.46 | 321.27 | 462.25 | 11,027.42 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,951 | 171.37 | 114.15 | 484.74 | 965.47 | 10,969.33 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 31 | 1.45 | 0.96 | 5.06 | 5.49 | 13.54 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 12 | 118.9 | 47.54 | 344.88 | 355.31 | 380.34 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 1 | 191.2 | 192.93 | 343.41 | 355.01 | 300.89 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 12 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 10 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 16 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.33 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.62 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 109 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 111 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.55 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.64 |
| `process_rss_avg` | job=backend-node | 13,046,784 |
| `process_rss_avg` | job=monitoring-node | 18,989,056 |
| `process_rss_avg` | job=mysql-exporter | 16,577,536 |
| `process_rss_avg` | job=mysql-node | 22,484,992 |
| `process_rss_avg` | job=prometheus | 117,243,904 |
| `process_rss_avg` | job=redis-exporter | 17,989,632 |
| `process_rss_avg` | job=redis-node | 22,704,128 |
| `process_rss_max` | job=backend-node | 17,510,400 |
| `process_rss_max` | job=monitoring-node | 19,054,592 |
| `process_rss_max` | job=mysql-exporter | 16,867,328 |
| `process_rss_max` | job=mysql-node | 22,589,440 |
| `process_rss_max` | job=prometheus | 117,440,512 |
| `process_rss_max` | job=redis-exporter | 17,989,632 |
| `process_rss_max` | job=redis-node | 22,704,128 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 27.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 94.19 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.19 |
| `node_cpu_pct_avg` | job=mysql-node | 59.33 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 7.55 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 2.94 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 49,770.67 |
| `node_major_fault_delta` | job=monitoring-node | 1.33 |
| `node_major_fault_delta` | job=mysql-node | 33.33 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 59,949,056 |
| `node_mem_available_avg` | job=monitoring-node | 414,472,192 |
| `node_mem_available_avg` | job=mysql-node | 239,834,112 |
| `node_mem_available_avg` | job=redis-node | 570,761,216 |
| `node_swap_free_avg` | job=backend-node | 1,985,976,320 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,673,117,184 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 75,954.67 |
| `node_swap_in_delta` | job=monitoring-node | 1.33 |
| `node_swap_in_delta` | job=mysql-node | 28 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 34,412 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 130,925.33 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 8 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 140,397.33 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,350.67 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 7.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 3~4분 — 2026-08-11T14:26:32.563Z ~ 2026-08-11T14:27:32.563Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,600 | 40.03 | 39.06 | 63.84 | 108.89 | 8,998.64 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
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
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,231 | 64.12 | 61.28 | 99.89 | 157.8 | 9,049.94 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,356 | 28.37 | 22.03 | 64.13 | 138.86 | 1,329.93 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 40 | 0.94 | 0.68 | 4.54 | 5.38 | 12.82 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 9 | 18.19 | 14.68 | 37.19 | 38.76 | 336.98 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 5 | 72.9 | 78.27 | 196.8 | 310.05 | 300.89 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6.75 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 7 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 23.25 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 20 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.33 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.6 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.14 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.57 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.58 |
| `process_rss_avg` | job=backend-node | 14,929,920 |
| `process_rss_avg` | job=monitoring-node | 19,054,592 |
| `process_rss_avg` | job=mysql-exporter | 16,703,488 |
| `process_rss_avg` | job=mysql-node | 22,233,088 |
| `process_rss_avg` | job=prometheus | 117,440,512 |
| `process_rss_avg` | job=redis-exporter | 18,481,152 |
| `process_rss_avg` | job=redis-node | 22,704,128 |
| `process_rss_max` | job=backend-node | 17,641,472 |
| `process_rss_max` | job=monitoring-node | 19,054,592 |
| `process_rss_max` | job=mysql-exporter | 17,190,912 |
| `process_rss_max` | job=mysql-node | 22,302,720 |
| `process_rss_max` | job=prometheus | 117,440,512 |
| `process_rss_max` | job=redis-exporter | 18,776,064 |
| `process_rss_max` | job=redis-node | 22,704,128 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 6.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 7 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 87.92 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.11 |
| `node_cpu_pct_avg` | job=mysql-node | 79.22 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 8.21 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 4.57 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 9,361.33 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 30.67 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 56,522,752 |
| `node_mem_available_avg` | job=monitoring-node | 414,356,480 |
| `node_mem_available_avg` | job=mysql-node | 244,184,064 |
| `node_mem_available_avg` | job=redis-node | 570,761,216 |
| `node_swap_free_avg` | job=backend-node | 1,985,941,504 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,673,119,232 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 18,350.67 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 30.67 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 2,844 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 133,637.33 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 9,528 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 650.67 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### 4~5분 — 2026-08-11T14:27:32.563Z ~ 2026-08-11T14:28:32.563Z (60s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,600 | 41.44 | 45.04 | 63.9 | 84.19 | 8,998.64 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
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
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2,176 | 64.31 | 63.47 | 99.06 | 125.15 | 9,049.94 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,409 | 26.1 | 22.28 | 55.13 | 78.89 | 1,329.93 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 40 | 0.84 | 0.58 | 4.89 | 6.57 | 12.82 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 13 | 13.27 | 11.88 | 25.17 | 27.4 | 336.98 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 300.89 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 13 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 17.33 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.33 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.16 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 106 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.54 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.54 |
| `process_rss_avg` | job=backend-node | 14,628,864 |
| `process_rss_avg` | job=monitoring-node | 18,771,968 |
| `process_rss_avg` | job=mysql-exporter | 16,718,848 |
| `process_rss_avg` | job=mysql-node | 22,336,512 |
| `process_rss_avg` | job=prometheus | 117,440,512 |
| `process_rss_avg` | job=redis-exporter | 18,749,440 |
| `process_rss_avg` | job=redis-node | 22,691,840 |
| `process_rss_max` | job=backend-node | 14,667,776 |
| `process_rss_max` | job=monitoring-node | 18,771,968 |
| `process_rss_max` | job=mysql-exporter | 17,309,696 |
| `process_rss_max` | job=mysql-node | 22,503,424 |
| `process_rss_max` | job=prometheus | 117,440,512 |
| `process_rss_max` | job=redis-exporter | 19,169,280 |
| `process_rss_max` | job=redis-node | 22,704,128 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 250 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 250 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 6.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 83.46 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.21 |
| `node_cpu_pct_avg` | job=mysql-node | 85.6 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 6.59 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 5.36 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 2,149.33 |
| `node_major_fault_delta` | job=monitoring-node | 1.33 |
| `node_major_fault_delta` | job=mysql-node | 33.33 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 42,924,032 |
| `node_mem_available_avg` | job=monitoring-node | 414,612,480 |
| `node_mem_available_avg` | job=mysql-node | 242,680,832 |
| `node_mem_available_avg` | job=redis-node | 570,761,216 |
| `node_swap_free_avg` | job=backend-node | 1,986,096,128 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,673,119,232 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 4,909.33 |
| `node_swap_in_delta` | job=monitoring-node | 1.33 |
| `node_swap_in_delta` | job=mysql-node | 30.67 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 622.67 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 131,994.67 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 1 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 9,029.33 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 733.33 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 6.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 32 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-postmerge-bid-only-load-noSSE-20260811.json

- 시나리오: `bid-only-load (SSE 없음)`
- K6 실행: 2026-08-11T14:31:14.808Z ~ 2026-08-11T14:43:26.831Z
- 설정: `{"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m","hotAuctionId":null}`

### QPS 50 — 2026-08-11T14:31:14.808Z ~ 2026-08-11T14:33:14.808Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,358 | 72.14 | 11.89 | 274.75 | 1,343.97 | 10,613.28 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,250 | 22.02 | 5.79 | 126.67 | 315.35 | 669.95 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,928 | 36.41 | 8.96 | 187.61 | 479.22 | 10,609.16 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 505,758.34 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 505,711.79 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 629.1 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,285 | 54.04 | 20.3 | 258.59 | 643.85 | 1,645.84 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 22 | 384.15 | 294.74 | 720.3 | 788.31 | 758.78 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 79 | 1.51 | 0.59 | 2.99 | 46.47 | 66.39 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 6.02 | 5.42 | 9.3 | 9.69 | 9.24 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.13 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 28.88 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 33.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.27 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 109 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.16 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.29 |
| `process_rss_avg` | job=backend-node | 12,982,784 |
| `process_rss_avg` | job=monitoring-node | 18,806,784 |
| `process_rss_avg` | job=mysql-exporter | 16,652,800 |
| `process_rss_avg` | job=mysql-node | 22,319,616 |
| `process_rss_avg` | job=prometheus | 117,964,800 |
| `process_rss_avg` | job=redis-exporter | 18,755,584 |
| `process_rss_avg` | job=redis-node | 22,667,264 |
| `process_rss_max` | job=backend-node | 13,307,904 |
| `process_rss_max` | job=monitoring-node | 19,034,112 |
| `process_rss_max` | job=mysql-exporter | 17,031,168 |
| `process_rss_max` | job=mysql-node | 22,474,752 |
| `process_rss_max` | job=prometheus | 118,095,872 |
| `process_rss_max` | job=redis-exporter | 18,804,736 |
| `process_rss_max` | job=redis-node | 22,667,264 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 0.63 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 29.63 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 23.64 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 2.61 |
| `node_load1_avg` | job=monitoring-node | 0.04 |
| `node_load1_avg` | job=mysql-node | 0.43 |
| `node_load1_avg` | job=redis-node | 0.01 |
| `node_major_fault_delta` | job=backend-node | 34,405.71 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 10.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 53,556,736 |
| `node_mem_available_avg` | job=monitoring-node | 412,116,480 |
| `node_mem_available_avg` | job=mysql-node | 238,680,576 |
| `node_mem_available_avg` | job=redis-node | 570,777,600 |
| `node_swap_free_avg` | job=backend-node | 2,042,822,656 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,673,119,232 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 77,202.29 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 5.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 40,138.29 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 108,329.14 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,608 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 16 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T14:33:14.808Z ~ 2026-08-11T14:35:14.808Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,830 | 33.42 | 11.99 | 180.7 | 317.78 | 10,613.28 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,770 | 37.53 | 6.01 | 150.65 | 988.28 | 5,643.45 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,402 | 34.13 | 8.98 | 155.47 | 424.82 | 10,609.16 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 505,711.79 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 629.1 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1,753 | 44.86 | 20.28 | 226.91 | 403.75 | 1,645.84 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 39 | 165.98 | 123.03 | 653.19 | 774.88 | 758.78 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 74 | 0.49 | 0.53 | 1.18 | 6.08 | 66.39 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 8 | 44.9 | 6.64 | 193.5 | 199.76 | 190.95 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 5.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 24.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 1.75 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 14 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 24 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.71 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.19 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.11 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 105.38 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.21 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.31 |
| `process_rss_avg` | job=backend-node | 13,430,784 |
| `process_rss_avg` | job=monitoring-node | 18,935,808 |
| `process_rss_avg` | job=mysql-exporter | 16,614,400 |
| `process_rss_avg` | job=mysql-node | 22,285,824 |
| `process_rss_avg` | job=prometheus | 118,095,872 |
| `process_rss_avg` | job=redis-exporter | 18,452,480 |
| `process_rss_avg` | job=redis-node | 22,667,264 |
| `process_rss_max` | job=backend-node | 13,602,816 |
| `process_rss_max` | job=monitoring-node | 19,017,728 |
| `process_rss_max` | job=mysql-exporter | 17,289,216 |
| `process_rss_max` | job=mysql-node | 22,523,904 |
| `process_rss_max` | job=prometheus | 118,095,872 |
| `process_rss_max` | job=redis-exporter | 18,452,480 |
| `process_rss_max` | job=redis-node | 22,667,264 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 8.38 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 36.14 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 32.32 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 0.93 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 0.74 |
| `node_load1_avg` | job=redis-node | 0.12 |
| `node_major_fault_delta` | job=backend-node | 33,826.29 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 5.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 66,623,488 |
| `node_mem_available_avg` | job=monitoring-node | 412,475,904 |
| `node_mem_available_avg` | job=mysql-node | 238,409,216 |
| `node_mem_available_avg` | job=redis-node | 570,746,880 |
| `node_swap_free_avg` | job=backend-node | 2,080,521,216 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,673,120,256 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 55,552 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 3.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 18,384 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 153,634.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,235.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 19.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.25 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T14:35:14.808Z ~ 2026-08-11T14:37:14.808Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 3,048 | 13.09 | 12.32 | 18.33 | 30.03 | 1,426.39 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 3,045 | 6.25 | 6.02 | 8.33 | 13.65 | 5,643.45 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 9,139 | 9.88 | 9.25 | 13.85 | 23.33 | 5,664.8 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
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
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 3,027 | 22.62 | 20.77 | 31.49 | 44.63 | 1,577.89 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 5 | 6.77 | 6.52 | 8.11 | 8.33 | 730.13 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.51 | 0.54 | 1.14 | 4.61 | 6.79 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 14 | 6.77 | 6.47 | 9.37 | 9.7 | 190.95 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.25 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.75 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 37.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.71 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.56 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.04 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.37 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.45 |
| `process_rss_avg` | job=backend-node | 12,566,528 |
| `process_rss_avg` | job=monitoring-node | 18,814,464 |
| `process_rss_avg` | job=mysql-exporter | 16,610,816 |
| `process_rss_avg` | job=mysql-node | 22,352,384 |
| `process_rss_avg` | job=prometheus | 118,095,872 |
| `process_rss_avg` | job=redis-exporter | 18,514,944 |
| `process_rss_avg` | job=redis-node | 22,667,264 |
| `process_rss_max` | job=backend-node | 13,447,168 |
| `process_rss_max` | job=monitoring-node | 19,017,728 |
| `process_rss_max` | job=mysql-exporter | 16,977,920 |
| `process_rss_max` | job=mysql-node | 22,540,288 |
| `process_rss_max` | job=prometheus | 118,095,872 |
| `process_rss_max` | job=redis-exporter | 18,952,192 |
| `process_rss_max` | job=redis-node | 22,667,264 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2.5 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 44.56 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 53.15 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 2.87 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 1.3 |
| `node_load1_avg` | job=redis-node | 0.02 |
| `node_major_fault_delta` | job=backend-node | 8,640 |
| `node_major_fault_delta` | job=monitoring-node | 11.43 |
| `node_major_fault_delta` | job=mysql-node | 13.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 64,976,384 |
| `node_mem_available_avg` | job=monitoring-node | 412,320,768 |
| `node_mem_available_avg` | job=mysql-node | 242,343,936 |
| `node_mem_available_avg` | job=redis-node | 570,537,984 |
| `node_swap_free_avg` | job=backend-node | 2,121,541,120 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,673,123,328 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 15,280 |
| `node_swap_in_delta` | job=monitoring-node | 3.43 |
| `node_swap_in_delta` | job=mysql-node | 16 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 329.14 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 275,296 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 1,977.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 36.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 2.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T14:37:14.808Z ~ 2026-08-11T14:39:14.808Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 4,256 | 17.88 | 13.23 | 34.01 | 153.9 | 545.92 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 4,239 | 7.98 | 6.34 | 12.55 | 36.68 | 373.98 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 12,742 | 12.92 | 10.01 | 23.45 | 78.3 | 463.1 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 24.94 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 1 | 32.6 | 30.76 | 33.27 | 33.5 | 32.6 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,223 | 29.77 | 25.32 | 50.15 | 147.86 | 504.66 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 17 | 12.52 | 6.64 | 72.7 | 86.12 | 383.46 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.6 | 0.57 | 1.37 | 7.41 | 7.48 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 6 | 7.83 | 6.64 | 13.63 | 13.91 | 190.95 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 2.5 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 6 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 27.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 58.29 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.43 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.79 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.08 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.49 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.55 |
| `process_rss_avg` | job=backend-node | 13,545,472 |
| `process_rss_avg` | job=monitoring-node | 18,923,520 |
| `process_rss_avg` | job=mysql-exporter | 16,718,848 |
| `process_rss_avg` | job=mysql-node | 22,230,528 |
| `process_rss_avg` | job=prometheus | 118,243,328 |
| `process_rss_avg` | job=redis-exporter | 17,558,528 |
| `process_rss_avg` | job=redis-node | 22,667,264 |
| `process_rss_max` | job=backend-node | 13,578,240 |
| `process_rss_max` | job=monitoring-node | 18,972,672 |
| `process_rss_max` | job=mysql-exporter | 17,117,184 |
| `process_rss_max` | job=mysql-node | 22,454,272 |
| `process_rss_max` | job=prometheus | 119,144,448 |
| `process_rss_max` | job=redis-exporter | 18,952,192 |
| `process_rss_max` | job=redis-node | 22,667,264 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 2.13 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 5 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 61.18 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.14 |
| `node_cpu_pct_avg` | job=mysql-node | 69.48 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 1.87 |
| `node_load1_avg` | job=monitoring-node | 0.03 |
| `node_load1_avg` | job=mysql-node | 1.56 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 8,622.86 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 137.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 108,120,576 |
| `node_mem_available_avg` | job=monitoring-node | 411,066,368 |
| `node_mem_available_avg` | job=mysql-node | 243,325,440 |
| `node_mem_available_avg` | job=redis-node | 570,540,032 |
| `node_swap_free_avg` | job=backend-node | 2,153,678,336 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,668,957,696 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 14,518.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 147.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 14,611.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 374,916.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 389.71 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 11.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 3.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T14:39:14.808Z ~ 2026-08-11T14:41:14.808Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 6,078 | 43.78 | 25.7 | 145.29 | 225.84 | 439.24 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 6,071 | 23.38 | 11.85 | 92.3 | 184.38 | 373.98 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 18,221 | 34 | 19.65 | 116.02 | 200.62 | 559.88 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 2 | 27.79 | 6.99 | 55.36 | 55.81 | 52.32 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 67.99 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 5,914 | 74.59 | 45.9 | 232.15 | 334.44 | 521.31 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 107 | 92.74 | 64.31 | 242.15 | 330.69 | 356.04 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 80 | 0.92 | 0.67 | 3.06 | 7.41 | 7.48 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 25 | 32.62 | 11.65 | 176.72 | 293.06 | 280.33 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 9.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 3.13 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 85.71 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.15 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.16 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 108 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.71 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.82 |
| `process_rss_avg` | job=backend-node | 13,812,224 |
| `process_rss_avg` | job=monitoring-node | 18,938,368 |
| `process_rss_avg` | job=mysql-exporter | 16,657,920 |
| `process_rss_avg` | job=mysql-node | 22,300,672 |
| `process_rss_avg` | job=prometheus | 119,537,664 |
| `process_rss_avg` | job=redis-exporter | 17,309,696 |
| `process_rss_avg` | job=redis-node | 22,695,424 |
| `process_rss_max` | job=backend-node | 13,901,824 |
| `process_rss_max` | job=monitoring-node | 18,972,672 |
| `process_rss_max` | job=mysql-exporter | 17,121,280 |
| `process_rss_max` | job=mysql-node | 22,544,384 |
| `process_rss_max` | job=prometheus | 119,930,880 |
| `process_rss_max` | job=redis-exporter | 17,657,856 |
| `process_rss_max` | job=redis-node | 22,798,336 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 11.88 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 82.54 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.2 |
| `node_cpu_pct_avg` | job=mysql-node | 89.56 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 3.4 |
| `node_load1_avg` | job=monitoring-node | 0.06 |
| `node_load1_avg` | job=mysql-node | 3.9 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 4,506.29 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 224 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 121,560,576 |
| `node_mem_available_avg` | job=monitoring-node | 408,940,544 |
| `node_mem_available_avg` | job=mysql-node | 249,526,784 |
| `node_mem_available_avg` | job=redis-node | 570,027,008 |
| `node_swap_free_avg` | job=backend-node | 2,169,948,672 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,147,648 |
| `node_swap_free_avg` | job=mysql-node | 2,668,969,984 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 9,342.86 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 237.71 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 163.43 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 530,558.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 2,478.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 113.14 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 5.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T14:41:14.808Z ~ 2026-08-11T14:43:14.808Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 7,374 | 167.36 | 150.33 | 295.65 | 473.6 | 2,761.45 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 7,040 | 100.29 | 75.99 | 223.09 | 338.22 | 2,807.74 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 21,357 | 129.49 | 102.62 | 255.2 | 415.82 | 2,842.01 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 52.32 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 67.99 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 4,147 | 229.02 | 214.17 | 360.8 | 482.64 | 3,170.6 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 2,457 | 122.54 | 101.3 | 255.47 | 432.48 | 2,695.88 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 41 | 4.09 | 1 | 20.13 | 31.54 | 30.82 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 22 | 107.49 | 117.44 | 194.24 | 199.91 | 280.33 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 560.26 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21.25 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 26 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 104 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 3.42 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.03 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 108.63 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 109 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.82 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.87 |
| `process_rss_avg` | job=backend-node | 13,688,832 |
| `process_rss_avg` | job=monitoring-node | 18,984,960 |
| `process_rss_avg` | job=mysql-exporter | 16,699,904 |
| `process_rss_avg` | job=mysql-node | 22,116,352 |
| `process_rss_avg` | job=prometheus | 118,319,104 |
| `process_rss_avg` | job=redis-exporter | 17,985,536 |
| `process_rss_avg` | job=redis-node | 22,551,040 |
| `process_rss_max` | job=backend-node | 14,032,896 |
| `process_rss_max` | job=monitoring-node | 19,099,648 |
| `process_rss_max` | job=mysql-exporter | 17,207,296 |
| `process_rss_max` | job=mysql-node | 22,413,312 |
| `process_rss_max` | job=prometheus | 119,930,880 |
| `process_rss_max` | job=redis-exporter | 18,280,448 |
| `process_rss_max` | job=redis-node | 22,601,728 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 96.63 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 93.15 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 11.35 |
| `node_load1_avg` | job=monitoring-node | 0.05 |
| `node_load1_avg` | job=mysql-node | 12.66 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 11,593.14 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 137.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 84,974,592 |
| `node_mem_available_avg` | job=monitoring-node | 405,592,576 |
| `node_mem_available_avg` | job=mysql-node | 244,566,016 |
| `node_mem_available_avg` | job=redis-node | 568,502,272 |
| `node_swap_free_avg` | job=backend-node | 2,166,413,312 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,153,792 |
| `node_swap_free_avg` | job=mysql-node | 2,668,969,472 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 37,924.57 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 174.86 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 36,664 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 1.14 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 601,384 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 2 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 19,348.57 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 402.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 9.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

---

## baseline-postmerge-bid-only-load-singleHotAuction-20260811.json

- 시나리오: `bid-only-load (SSE 없음)`
- K6 실행: 2026-08-11T14:45:41.288Z ~ 2026-08-11T14:57:54.099Z
- 설정: `{"qpsStages":[50,100,150,200,300,400],"stageDuration":"2m","hotAuctionId":3001001}`

### QPS 50 — 2026-08-11T14:45:41.288Z ~ 2026-08-11T14:47:41.288Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,211 | 58.98 | 14.99 | 287.86 | 443.81 | 4,822.64 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,171 | 33.66 | 6.74 | 194.14 | 391.47 | 3,429.15 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 3,583 | 95.79 | 27.91 | 435.61 | 671.67 | 4,294.67 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
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
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,211.52 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 925 | 46.16 | 41.07 | 50.29 | 266.42 | 3,210.03 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 275 | 445.84 | 456.79 | 882.56 | 1,052.95 | 4,188.81 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 72 | 1.03 | 0.58 | 3.09 | 18.85 | 38.12 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

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
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 28.57 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.29 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 2.04 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 107.25 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 111 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.15 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.24 |
| `process_rss_avg` | job=backend-node | 13,220,864 |
| `process_rss_avg` | job=monitoring-node | 18,833,408 |
| `process_rss_avg` | job=mysql-exporter | 16,691,712 |
| `process_rss_avg` | job=mysql-node | 22,279,168 |
| `process_rss_avg` | job=prometheus | 115,150,848 |
| `process_rss_avg` | job=redis-exporter | 17,653,760 |
| `process_rss_avg` | job=redis-node | 22,679,552 |
| `process_rss_max` | job=backend-node | 13,570,048 |
| `process_rss_max` | job=monitoring-node | 18,833,408 |
| `process_rss_max` | job=mysql-exporter | 17,035,264 |
| `process_rss_max` | job=mysql-node | 22,388,736 |
| `process_rss_max` | job=prometheus | 115,150,848 |
| `process_rss_max` | job=redis-exporter | 17,899,520 |
| `process_rss_max` | job=redis-node | 22,679,552 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 4.25 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 26 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 48.63 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 43.39 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 52.83 |
| `node_cpu_pct_avg` | job=redis-node | 0.36 |
| `node_load1_avg` | job=backend-node | 2.04 |
| `node_load1_avg` | job=monitoring-node | 0.01 |
| `node_load1_avg` | job=mysql-node | 1.7 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 67,389.71 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 13.71 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 71,130,624 |
| `node_mem_available_avg` | job=monitoring-node | 405,399,552 |
| `node_mem_available_avg` | job=mysql-node | 239,978,496 |
| `node_mem_available_avg` | job=redis-node | 568,431,104 |
| `node_swap_free_avg` | job=backend-node | 2,118,096,896 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,159,936 |
| `node_swap_free_avg` | job=mysql-node | 2,668,965,888 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 129,920 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 12.57 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 70,808 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 102,012.57 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 84,872 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 253.71 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 5.63 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 100 — 2026-08-11T14:47:41.288Z ~ 2026-08-11T14:49:41.288Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,581 | 22.34 | 15.22 | 35.74 | 60.24 | 6,381.63 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,645 | 7.16 | 6.66 | 11.43 | 16.13 | 728.83 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,830 | 54.86 | 37.17 | 104.57 | 227.53 | 6,655.84 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
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
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 1,211.52 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 798 | 60.61 | 54.89 | 88.39 | 201.55 | 1,003.46 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 807 | 20.85 | 8.58 | 88.52 | 234.21 | 1,131.16 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 75 | 0.32 | 0.51 | 0.96 | 1.02 | 17.46 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 4.63 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 25.38 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 19.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 9.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.43 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.19 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 106 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 107 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.18 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.22 |
| `process_rss_avg` | job=backend-node | 13,429,760 |
| `process_rss_avg` | job=monitoring-node | 18,833,408 |
| `process_rss_avg` | job=mysql-exporter | 16,637,440 |
| `process_rss_avg` | job=mysql-node | 22,420,480 |
| `process_rss_avg` | job=prometheus | 115,544,064 |
| `process_rss_avg` | job=redis-exporter | 18,128,896 |
| `process_rss_avg` | job=redis-node | 22,700,544 |
| `process_rss_max` | job=backend-node | 13,697,024 |
| `process_rss_max` | job=monitoring-node | 18,833,408 |
| `process_rss_max` | job=mysql-exporter | 17,031,168 |
| `process_rss_max` | job=mysql-node | 22,839,296 |
| `process_rss_max` | job=prometheus | 115,937,280 |
| `process_rss_max` | job=redis-exporter | 18,161,664 |
| `process_rss_max` | job=redis-node | 22,941,696 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 6.38 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 34 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 28.29 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.15 |
| `node_cpu_pct_avg` | job=mysql-node | 74.73 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 0.99 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 3.02 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 14,812.57 |
| `node_major_fault_delta` | job=monitoring-node | 2.29 |
| `node_major_fault_delta` | job=mysql-node | 30.86 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 73,750,016 |
| `node_mem_available_avg` | job=monitoring-node | 405,992,960 |
| `node_mem_available_avg` | job=mysql-node | 245,249,536 |
| `node_mem_available_avg` | job=redis-node | 568,294,400 |
| `node_swap_free_avg` | job=backend-node | 2,149,658,624 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,159,936 |
| `node_swap_free_avg` | job=mysql-node | 2,668,965,888 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 29,532.57 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 35.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 2,469.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 124,922.29 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 0 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 23,803.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 172.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 0 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 6.5 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 150 — 2026-08-11T14:49:41.288Z ~ 2026-08-11T14:51:41.288Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 2,090 | 262.68 | 254.47 | 471.94 | 590.88 | 6,422.58 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,972 | 201.18 | 195.81 | 421.2 | 517.78 | 7,900.07 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 5,912 | 587.7 | 586.29 | 797.08 | 925.28 | 8,237.2 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
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
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 13 | 679.92 | 677.48 | 841.1 | 884.05 | 6,760.91 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,692 | 315.21 | 277.55 | 612.21 | 800.59 | 8,110.48 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 15 | 0.5 | 0.54 | 1.17 | 1.35 | 12.19 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 2 | 214.58 | 201.33 | 243.83 | 245.62 | 232.07 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.86 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0.14 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20.14 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 21.21 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.64 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 109.14 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 111 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.23 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.27 |
| `process_rss_avg` | job=backend-node | 13,779,456 |
| `process_rss_avg` | job=monitoring-node | 18,833,408 |
| `process_rss_avg` | job=mysql-exporter | 16,615,424 |
| `process_rss_avg` | job=mysql-node | 22,345,216 |
| `process_rss_avg` | job=prometheus | 115,937,280 |
| `process_rss_avg` | job=redis-exporter | 18,358,272 |
| `process_rss_avg` | job=redis-node | 22,634,496 |
| `process_rss_max` | job=backend-node | 14,979,072 |
| `process_rss_max` | job=monitoring-node | 18,833,408 |
| `process_rss_max` | job=mysql-exporter | 16,891,904 |
| `process_rss_max` | job=mysql-node | 22,691,840 |
| `process_rss_max` | job=prometheus | 115,937,280 |
| `process_rss_max` | job=redis-exporter | 18,423,808 |
| `process_rss_max` | job=redis-node | 22,634,496 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 45.09 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.16 |
| `node_cpu_pct_avg` | job=mysql-node | 78.25 |
| `node_cpu_pct_avg` | job=redis-node | 0.37 |
| `node_load1_avg` | job=backend-node | 2.15 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 16.68 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 41,440 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 33.14 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 45,001,728 |
| `node_mem_available_avg` | job=monitoring-node | 406,531,072 |
| `node_mem_available_avg` | job=mysql-node | 247,410,688 |
| `node_mem_available_avg` | job=redis-node | 568,297,984 |
| `node_swap_free_avg` | job=backend-node | 2,136,266,240 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,159,936 |
| `node_swap_free_avg` | job=mysql-node | 2,668,965,888 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 86,152 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 6.86 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 44,180.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 130,389.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 9 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 137,057.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,292.57 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 1.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 21.88 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 200 — 2026-08-11T14:51:41.288Z ~ 2026-08-11T14:53:41.288Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,464 | 466.39 | 283.73 | 1,485.9 | 3,217.65 | 29,481.1 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 1,244 | 274.91 | 217.96 | 581.27 | 1,680.41 | 34,481.83 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 4,308 | 798.24 | 630.85 | 1,335.44 | 12,946.81 | 24,706.56 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
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
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 2 | 1,454.15 | 715.83 | 2,469.61 | 2,498.24 | 6,760.91 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 963 | 922.43 | 453.39 | 3,465.05 | 12,901.01 | 23,647.65 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 5 | 87.74 | 6.99 | 346.33 | 355.6 | 337.42 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 232.07 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 29.86 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 18.86 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 20 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 59.63 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.22 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 14.31 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 111 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.19 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.28 |
| `process_rss_avg` | job=backend-node | 13,242,953.14 |
| `process_rss_avg` | job=monitoring-node | 18,831,360 |
| `process_rss_avg` | job=mysql-exporter | 16,348,672 |
| `process_rss_avg` | job=mysql-node | 22,181,376 |
| `process_rss_avg` | job=prometheus | 114,736,128 |
| `process_rss_avg` | job=redis-exporter | 18,587,648 |
| `process_rss_avg` | job=redis-node | 22,634,496 |
| `process_rss_max` | job=backend-node | 15,495,168 |
| `process_rss_max` | job=monitoring-node | 18,927,616 |
| `process_rss_max` | job=mysql-exporter | 16,719,872 |
| `process_rss_max` | job=mysql-node | 22,265,856 |
| `process_rss_max` | job=prometheus | 115,937,280 |
| `process_rss_max` | job=redis-exporter | 18,685,952 |
| `process_rss_max` | job=redis-node | 22,634,496 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 89.99 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.18 |
| `node_cpu_pct_avg` | job=mysql-node | 65.27 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 12.47 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 11.32 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 140,146.06 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 346.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 67,125,248 |
| `node_mem_available_avg` | job=monitoring-node | 406,467,072 |
| `node_mem_available_avg` | job=mysql-node | 245,571,072 |
| `node_mem_available_avg` | job=redis-node | 568,309,760 |
| `node_swap_free_avg` | job=backend-node | 2,064,335,433.14 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,159,936 |
| `node_swap_free_avg` | job=mysql-node | 2,665,018,368 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 282,039.06 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 123.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 150,551.25 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 1,819.43 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 88,021.71 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 17 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 629,187.43 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 930.29 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 11.43 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 20.13 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 300 — 2026-08-11T14:53:41.288Z ~ 2026-08-11T14:55:41.288Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 1,913 | 280.1 | 232.61 | 516.67 | 1,264.33 | 29,481.1 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 2,047 | 274.3 | 190.14 | 496.81 | 1,030.82 | 34,481.83 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 6,085 | 695.29 | 581.52 | 1,040.07 | 4,129.61 | 24,706.56 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 1 | 109.37 | 105.92 | 111.26 | 111.73 | 109.37 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 5 | 407.6 | 357.91 | 608.45 | 622.77 | 2,209.09 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 1,776 | 416.67 | 422.86 | 625.12 | 802.29 | 23,647.65 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 7 | 1.69 | 0.6 | 9.37 | 9.7 | 379.87 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 527.79 | 581.61 | 791.88 | 802.62 | 717.1 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

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
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 57.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 0 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 5.97 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.01 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 110.75 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 112 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.23 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.27 |
| `process_rss_avg` | job=backend-node | 13,282,816 |
| `process_rss_avg` | job=monitoring-node | 18,959,360 |
| `process_rss_avg` | job=mysql-exporter | 16,492,032 |
| `process_rss_avg` | job=mysql-node | 22,181,376 |
| `process_rss_avg` | job=prometheus | 113,415,168 |
| `process_rss_avg` | job=redis-exporter | 18,275,840 |
| `process_rss_avg` | job=redis-node | 22,643,712 |
| `process_rss_max` | job=backend-node | 15,355,904 |
| `process_rss_max` | job=monitoring-node | 19,058,688 |
| `process_rss_max` | job=mysql-exporter | 16,748,544 |
| `process_rss_max` | job=mysql-node | 22,437,888 |
| `process_rss_max` | job=prometheus | 114,597,888 |
| `process_rss_max` | job=redis-exporter | 18,554,880 |
| `process_rss_max` | job=redis-node | 22,728,704 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
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
| `node_cpu_pct_avg` | job=backend-node | 63.1 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.17 |
| `node_cpu_pct_avg` | job=mysql-node | 83.06 |
| `node_cpu_pct_avg` | job=redis-node | 0.34 |
| `node_load1_avg` | job=backend-node | 5.48 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 15.49 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 118,270.86 |
| `node_major_fault_delta` | job=monitoring-node | 0 |
| `node_major_fault_delta` | job=mysql-node | 128 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 52,078,080 |
| `node_mem_available_avg` | job=monitoring-node | 406,882,304 |
| `node_mem_available_avg` | job=mysql-node | 239,784,960 |
| `node_mem_available_avg` | job=redis-node | 568,311,808 |
| `node_swap_free_avg` | job=backend-node | 2,062,809,600 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,159,936 |
| `node_swap_free_avg` | job=mysql-node | 2,663,702,528 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 204,755.43 |
| `node_swap_in_delta` | job=monitoring-node | 0 |
| `node_swap_in_delta` | job=mysql-node | 611.43 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 115,252.57 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 127,822.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 7 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 383,662.86 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 1,536 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 1.14 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 19.75 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

### QPS 400 — 2026-08-11T14:55:41.288Z ~ 2026-08-11T14:57:41.288Z (120s)

#### HTTP 서버 히스토그램 (서버 측)

| method / uri / status | 요청수 | 평균(ms) | p50(ms) | p95(ms) | p99(ms) | max(ms) |
|---|---:|---:|---:|---:|---:|---:|
| method=GET, status=200, uri=/api/auctions | 785 | 636.38 | 277.81 | 1,423.3 | 5,483.24 | 27,894.67 |
| method=GET, status=200, uri=/api/auctions/{auctionId} | 893 | 728.98 | 200.85 | 1,311.69 | 21,610.84 | 27,905.9 |
| method=GET, status=200, uri=/api/auctions/{auctionId}/bid-context | 2,707 | 1,193.5 | 585.49 | 1,943.45 | 23,608.53 | 28,309.07 |
| method=GET, status=200, uri=/api/auctions/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/test/load/sse-status | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/api/users/{userId}/notifications/stream | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=200, uri=/error | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=401, uri=UNKNOWN | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=404, uri=/** | 0 | N/A | N/A | N/A | N/A | 109.37 |
| method=GET, status=500, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId} | 0 | N/A | N/A | N/A | N/A | 0 |
| method=GET, status=500, uri=/api/auctions/{auctionId}/bid-context | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auctions | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/cards | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/insights | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/market | 0 | N/A | N/A | N/A | N/A | 0 |
| method=OPTIONS, status=200, uri=/api/statistic/price-movers | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/auth/login | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=200, uri=/api/sse/tickets | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=201, uri=/api/auctions/{auctionId}/bids | 1 | 1,539 | 1,610.61 | 1,771.67 | 1,785.99 | 1,539 |
| method=POST, status=400, uri=/api/auctions/{auctionId}/bids | 775 | 2,033.7 | 515.13 | 7,028.75 | 29,457.15 | 33,561.07 |
| method=POST, status=401, uri=/api/auth/refresh | 0 | N/A | N/A | N/A | N/A | 0 |
| method=POST, status=401, uri=UNKNOWN | 3 | 431.47 | 145.4 | 1,377.97 | 1,420.92 | 1,156.74 |
| method=POST, status=409, uri=/api/auctions/{auctionId}/bids | 3 | 528.17 | 518.44 | 881.36 | 892.1 | 879.62 |
| method=POST, status=500, uri=/api/auctions/{auctionId}/bids | 0 | N/A | N/A | N/A | N/A | 0 |

#### 백엔드 애플리케이션·JVM·Hikari·Tomcat·도메인·SSE

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `hikari_active_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_active_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 30 |
| `hikari_idle_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `hikari_pending_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 19.25 |
| `hikari_pending_max` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 21 |
| `hikari_timeout_delta` | application=dbidding, exported_application=dbidding, job=backend-spring, pool=HikariPool-1 | 0 |
| `jvm_gc_pause_count_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 51.43 |
| `jvm_gc_pause_count_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 1.14 |
| `jvm_gc_pause_sum_delta` | action=end of major GC, application=dbidding, cause=G1 Compaction Pause, exported_application=dbidding, gc=G1 Old Generation, job=backend-spring | 22.36 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Evacuation Pause, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 11.55 |
| `jvm_gc_pause_sum_delta` | action=end of minor GC, application=dbidding, cause=G1 Humongous Allocation, exported_application=dbidding, gc=G1 Young Generation, job=backend-spring | 0.06 |
| `jvm_threads_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 109.5 |
| `jvm_threads_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 110 |
| `process_cpu_usage_avg` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.21 |
| `process_cpu_usage_max` | application=dbidding, exported_application=dbidding, job=backend-spring | 0.28 |
| `process_rss_avg` | job=backend-node | 12,709,376 |
| `process_rss_avg` | job=monitoring-node | 18,886,656 |
| `process_rss_avg` | job=mysql-exporter | 16,384,000 |
| `process_rss_avg` | job=mysql-node | 22,194,688 |
| `process_rss_avg` | job=prometheus | 113,020,928 |
| `process_rss_avg` | job=redis-exporter | 18,530,816 |
| `process_rss_avg` | job=redis-node | 22,724,608 |
| `process_rss_max` | job=backend-node | 13,385,728 |
| `process_rss_max` | job=monitoring-node | 18,919,424 |
| `process_rss_max` | job=mysql-exporter | 16,707,584 |
| `process_rss_max` | job=mysql-node | 22,642,688 |
| `process_rss_max` | job=prometheus | 113,020,928 |
| `process_rss_max` | job=redis-exporter | 18,632,704 |
| `process_rss_max` | job=redis-node | 22,724,608 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_avg` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=auction | 0 |
| `sse_connections_max` | application=dbidding, exported_application=dbidding, job=backend-spring, stream=notification | 0 |
| `sse_saturated_delta` | application=dbidding, executor=auction, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification-fanout, exported_application=dbidding, job=backend-spring | 0 |
| `sse_saturated_delta` | application=dbidding, executor=notification, exported_application=dbidding, job=backend-spring | 0 |
| `tomcat_busy_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 1.5 |
| `tomcat_busy_max` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_busy_max` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 3 |
| `tomcat_current_avg` | application=dbidding, connector=main, exported_application=dbidding, job=backend-spring | 50 |
| `tomcat_current_avg` | application=dbidding, connector=management, exported_application=dbidding, job=backend-spring | 10 |

#### 노드·OS (backend/mysql/redis)

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `node_cpu_pct_avg` | job=backend-node | 88.79 |
| `node_cpu_pct_avg` | job=monitoring-node | 1.12 |
| `node_cpu_pct_avg` | job=mysql-node | 37.41 |
| `node_cpu_pct_avg` | job=redis-node | 0.35 |
| `node_load1_avg` | job=backend-node | 5.87 |
| `node_load1_avg` | job=monitoring-node | 0 |
| `node_load1_avg` | job=mysql-node | 12.43 |
| `node_load1_avg` | job=redis-node | 0 |
| `node_major_fault_delta` | job=backend-node | 138,731.43 |
| `node_major_fault_delta` | job=monitoring-node | 1.14 |
| `node_major_fault_delta` | job=mysql-node | 514.29 |
| `node_major_fault_delta` | job=redis-node | 0 |
| `node_mem_available_avg` | job=backend-node | 57,149,440 |
| `node_mem_available_avg` | job=monitoring-node | 407,373,312 |
| `node_mem_available_avg` | job=mysql-node | 236,765,696 |
| `node_mem_available_avg` | job=redis-node | 568,311,808 |
| `node_swap_free_avg` | job=backend-node | 2,058,097,152 |
| `node_swap_free_avg` | job=monitoring-node | 3,093,170,688 |
| `node_swap_free_avg` | job=mysql-node | 2,663,829,504 |
| `node_swap_free_avg` | job=redis-node | 0 |
| `node_swap_in_delta` | job=backend-node | 246,451.43 |
| `node_swap_in_delta` | job=monitoring-node | 1.14 |
| `node_swap_in_delta` | job=mysql-node | 209.14 |
| `node_swap_in_delta` | job=redis-node | 0 |
| `node_swap_out_delta` | job=backend-node | 161,893.71 |
| `node_swap_out_delta` | job=monitoring-node | 0 |
| `node_swap_out_delta` | job=mysql-node | 0 |
| `node_swap_out_delta` | job=redis-node | 0 |

#### MySQL exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `mysql_questions_delta` | job=mysql-exporter | 55,662.86 |
| `mysql_row_lock_current_max` | job=mysql-exporter | 18 |
| `mysql_row_lock_time_delta` | job=mysql-exporter | 966,505.14 |
| `mysql_row_lock_waits_delta` | job=mysql-exporter | 755.43 |
| `mysql_slow_queries_delta` | job=mysql-exporter | 21.71 |
| `mysql_threads_connected_avg` | job=mysql-exporter | 31 |
| `mysql_threads_running_avg` | job=mysql-exporter | 13.38 |
| `mysql_up_avg` | job=mysql-exporter | 1 |

#### Redis exporter

| 메트릭 | 라벨 | 값 |
|---|---|---:|
| `redis_commands_delta` | job=redis-exporter | 64 |
| `redis_connected_clients_avg` | job=redis-exporter | 1 |
| `redis_up_avg` | job=redis-exporter | 1 |

> 이 문서는 codex의 도움을 받아 작성하였습니다
