# 6차 vs 8차 — 구간별/API별 원본 데이터 (정정본, 서술 없이 데이터만)

**용도:** 10-round8 문서 §2/§7의 표를 narrative 없이 데이터만 순서대로
정리한 참조용 문서. 6차(RAM 903MB) vs 8차(RAM 1.8GiB) 직접 대조용.

**측정 방법:** `http_server_requests_seconds_count`/`_sum`/`_bucket`
(Prometheus, `uri` 라벨별)을 구간 경계 시각에 point query로 찍어 delta
계산(요청수/평균), p95/p99는 `histogram_quantile()`로 구간 길이만큼의
`rate()` range를 구간 끝 시각에 평가. k6 클라이언트 측 값이 아니라
**서버가 실제로 처리한 시간 기준.**

**이상치 정정 내역**(10-round8 §7.1 조사 결과 — 자세한 원인은 그 문서
참고): 6차 세션 중 실제로 8번의 서버 프리즈가 있었다(2번은 확인된 Full
GC, 나머지 6번은 원인 미특정 — Young GC 추정). 이 중 2곳(pure-throughput
500 QPS400, hot-auction 0-1min)은 구간 경계가 프리즈 순간과 정확히
겹쳐서 최초 조회 시 요청수가 음수/과대로 나왔던 계산 아티팩트였고, 경계를
20초 옆으로 밀어 재조회해 정정했다. **그 외 극단적으로 높은 p99 값들
(수천~2만ms대)은 아티팩트가 아니라 그 프리즈들과 실제로 겹치는 진짜
이벤트다** — 제거하지 않고 그대로 실었다.

---

## pure-throughput 250

### 6차 (RAM 903MB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| QPS50 | GET bid-context | 3,219 | 61 | 335 | 615 |
| QPS50 | POST bids | 1,073 | 143 | 202 | 551 |
| QPS50 | GET /api/auctions | 1,146 | 108 | 592 | 947 |
| QPS50 | GET /api/auctions/:id | 1,000 | 35 | 45 | 114 |
| QPS100 | GET bid-context | 5,041 | 20 | 78 | 244 |
| QPS100 | POST bids | 1,679 | 31 | 98 | 288 |
| QPS100 | GET /api/auctions | 1,654 | 34 | 197 | 379 |
| QPS100 | GET /api/auctions/:id | 1,707 | 7 | 9 | 13 |
| QPS150 | GET bid-context | 8,591 | 27 | 129 | 247 |
| QPS150 | POST bids | 2,872 | 44 | 200 | 338 |
| QPS150 | GET /api/auctions | 2,901 | 31 | 134 | 279 |
| QPS150 | GET /api/auctions/:id | 2,816 | 15 | 73 | 190 |
| QPS200 | GET bid-context | 12,220 | 26 | 65 | 104 |
| QPS200 | POST bids | 4,075 | 42 | 93 | 134 |
| QPS200 | GET /api/auctions | 4,041 | 29 | 68 | 101 |
| QPS200 | GET /api/auctions/:id | 4,106 | 12 | 28 | 45 |
| QPS300 | GET bid-context | 16,130 | 109 | 267 | 377 |
| QPS300 | POST bids | 5,245 | 139 | 318 | 422 |
| QPS300 | GET /api/auctions | 5,402 | 111 | 271 | 392 |
| QPS300 | GET /api/auctions/:id | 5,369 | 63 | 203 | 297 |
| QPS400 | GET bid-context | 19,245 | 164 | 289 | 402 |
| QPS400 | POST bids | 5,890 | 159 | 301 | 392 |
| QPS400 | GET /api/auctions | 6,679 | 172 | 290 | 371 |
| QPS400 | GET /api/auctions/:id | 6,337 | 102 | 220 | 293 |

### 8차 (RAM 1.8GiB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| QPS50 | GET bid-context | 3,238 | 80 | 527 | 998 |
| QPS50 | POST bids | 1,079 | 137 | 900 | 1,663 |
| QPS50 | GET /api/auctions | 1,159 | 171 | 1,044 | 1,449 |
| QPS50 | GET /api/auctions/:id | 1,000 | 26 | 19 | 22 |
| QPS100 | GET bid-context | 5,059 | 17 | 22 | 33 |
| QPS100 | POST bids | 1,686 | 29 | 34 | 54 |
| QPS100 | GET /api/auctions | 1,641 | 17 | 22 | 46 |
| QPS100 | GET /api/auctions/:id | 1,732 | 9 | 11 | 14 |
| QPS150 | GET bid-context | 8,638 | 20 | 32 | 83 |
| QPS150 | POST bids | 2,879 | 35 | 53 | 140 |
| QPS150 | GET /api/auctions | 2,891 | 21 | 34 | 128 |
| QPS150 | GET /api/auctions/:id | 2,868 | 9 | 12 | 17 |
| QPS200 | GET bid-context | 12,240 | 36 | 102 | 162 |
| QPS200 | POST bids | 4,077 | 52 | 127 | 194 |
| QPS200 | GET /api/auctions | 4,109 | 35 | 85 | 124 |
| QPS200 | GET /api/auctions/:id | 4,051 | 13 | 26 | 57 |
| QPS300 | GET bid-context | 15,305 | 204 | 358 | 469 |
| QPS300 | POST bids | 4,707 | 214 | 407 | 493 |
| QPS300 | GET /api/auctions | 5,249 | 175 | 323 | 428 |
| QPS300 | GET /api/auctions/:id | 5,071 | 111 | 253 | 344 |
| QPS400 | GET bid-context | 16,655 | 207 | 339 | 439 |
| QPS400 | POST bids | 5,288 | 173 | 342 | 431 |
| QPS400 | GET /api/auctions | 5,684 | 173 | 289 | 368 |
| QPS400 | GET /api/auctions/:id | 5,489 | 112 | 236 | 313 |

---

## pure-throughput 500

### 6차 (RAM 903MB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| QPS50 | GET bid-context | 2,941 | 113 | 458 | 2,219 |
| QPS50 | POST bids | 980 | 100 | 408 | 987 |
| QPS50 | GET /api/auctions | 1,016 | 79 | 338 | 863 |
| QPS50 | GET /api/auctions/:id | 945 | 96 | 385 | 1,703 |
| QPS100 | GET bid-context | 4,767 | 50 | 218 | 657 |
| QPS100 | POST bids | 1,601 | 57 | 247 | 422 |
| QPS100 | GET /api/auctions | 1,591 | 64 | 242 | 1,809 |
| QPS100 | GET /api/auctions/:id | 1,577 | 29 | 160 | 354 |
| QPS150 | GET bid-context | 8,343 | 14 | 22 | 38 |
| QPS150 | POST bids | 2,780 | 23 | 32 | 46 |
| QPS150 | GET /api/auctions | 2,790 | 14 | 22 | 33 |
| QPS150 | GET /api/auctions/:id | 2,771 | 7 | 11 | 15 |
| QPS200 | GET bid-context | 11,943 | 21 | 46 | 98 |
| QPS200 | POST bids | 3,980 | 34 | 63 | 127 |
| QPS200 | GET /api/auctions | 3,990 | 22 | 47 | 93 |
| QPS200 | GET /api/auctions/:id | 3,973 | 10 | 21 | 35 |
| QPS300 | GET bid-context | 16,209 | 117 | 295 | 408 |
| QPS300 | POST bids | 5,238 | 146 | 345 | 440 |
| QPS300 | GET /api/auctions | 5,423 | 117 | 291 | 399 |
| QPS300 | GET /api/auctions/:id | 5,408 | 71 | 229 | 319 |
| QPS400\* | GET bid-context | 17,586 | 212 | 329 | 536 |
| QPS400\* | POST bids | 5,299 | 208 | 351 | 734 |
| QPS400\* | GET /api/auctions | 6,028 | 214 | 323 | 652 |
| QPS400\* | GET /api/auctions/:id | 5,725 | 162 | 248 | 426 |

\*경계 20초 조정한 정정값(원인: 10-round8 §7.1)

### 8차 (RAM 1.8GiB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| QPS50 | GET bid-context | 3,435 | 18 | 22 | 32 |
| QPS50 | POST bids | 1,144 | 28 | 36 | 49 |
| QPS50 | GET /api/auctions | 1,200 | 16 | 22 | 27 |
| QPS50 | GET /api/auctions/:id | 1,090 | 9 | 11 | 16 |
| QPS100 | GET bid-context | 5,238 | 18 | 25 | 35 |
| QPS100 | POST bids | 1,746 | 30 | 38 | 79 |
| QPS100 | GET /api/auctions | 1,782 | 16 | 23 | 36 |
| QPS100 | GET /api/auctions/:id | 1,710 | 9 | 11 | 14 |
| QPS150 | GET bid-context | 8,833 | 23 | 39 | 79 |
| QPS150 | POST bids | 2,941 | 38 | 74 | 156 |
| QPS150 | GET /api/auctions | 2,890 | 21 | 38 | 82 |
| QPS150 | GET /api/auctions/:id | 3,000 | 10 | 14 | 20 |
| QPS200 | GET bid-context | 12,387 | 78 | 251 | 359 |
| QPS200 | POST bids | 4,110 | 98 | 285 | 403 |
| QPS200 | GET /api/auctions | 4,128 | 68 | 218 | 328 |
| QPS200 | GET /api/auctions/:id | 4,135 | 29 | 122 | 208 |
| QPS300 | GET bid-context | 14,887 | 228 | 386 | 495 |
| QPS300 | POST bids | 4,463 | 225 | 413 | 496 |
| QPS300 | GET /api/auctions | 5,229 | 187 | 326 | 423 |
| QPS300 | GET /api/auctions/:id | 4,838 | 125 | 263 | 365 |
| QPS400 | GET bid-context | 15,354 | 229 | 363 | 474 |
| QPS400 | POST bids | 4,805 | 199 | 377 | 450 |
| QPS400 | GET /api/auctions | 4,939 | 184 | 310 | 411 |
| QPS400 | GET /api/auctions/:id | 4,931 | 121 | 243 | 325 |

---

## pure-throughput 1000

### 6차 (RAM 903MB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| QPS50 | GET bid-context | 2,026 | 276 | 871 | 1,588 |
| QPS50 | POST bids | 629 | 435 | 852 | 6,789 |
| QPS50 | GET /api/auctions | 773 | 318 | 966 | 1,949 |
| QPS50 | GET /api/auctions/:id | 583 | 170 | 550 | 1,322 |
| QPS100 | GET bid-context | 5,201 | 38 | 24 | 69 |
| QPS100 | POST bids | 1,755 | 48 | 35 | 79 |
| QPS100 | GET /api/auctions | 1,743 | 32 | 26 | 78 |
| QPS100 | GET /api/auctions/:id | 1,719 | 23 | 12 | 28 |
| QPS150 | GET bid-context | 8,662 | 35 | 161 | 444 |
| QPS150 | POST bids | 2,887 | 44 | 189 | 386 |
| QPS150 | GET /api/auctions | 2,840 | 37 | 208 | 470 |
| QPS150 | GET /api/auctions/:id | 2,936 | 16 | 50 | 246 |
| QPS200 | GET bid-context | 12,262 | 52 | 128 | 218 |
| QPS200 | POST bids | 4,085 | 72 | 162 | 268 |
| QPS200 | GET /api/auctions | 4,112 | 50 | 120 | 191 |
| QPS200 | GET /api/auctions/:id | 4,061 | 22 | 51 | 105 |
| QPS300 | GET bid-context | 9,562 | 213 | 452 | 1,324 |
| QPS300 | POST bids | 2,943 | 249 | 451 | 1,595 |
| QPS300 | GET /api/auctions | 3,058 | 204 | 522 | 1,799 |
| QPS300 | GET /api/auctions/:id | 3,122 | 137 | 345 | 863 |
| QPS400 | GET bid-context | 15,178 | 206 | 413 | 710 |
| QPS400 | POST bids | 4,657 | 230 | 443 | 679 |
| QPS400 | GET /api/auctions | 4,832 | 223 | 422 | 746 |
| QPS400 | GET /api/auctions/:id | 4,852 | 140 | 320 | 512 |

### 8차 (RAM 1.8GiB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| QPS50 | GET bid-context | 3,129 | 19 | 25 | 33 |
| QPS50 | POST bids | 1,042 | 29 | 36 | 55 |
| QPS50 | GET /api/auctions | 1,086 | 17 | 22 | 32 |
| QPS50 | GET /api/auctions/:id | 1,000 | 9 | 12 | 17 |
| QPS100 | GET bid-context | 4,959 | 19 | 27 | 50 |
| QPS100 | POST bids | 1,653 | 30 | 39 | 71 |
| QPS100 | GET /api/auctions | 1,707 | 17 | 24 | 38 |
| QPS100 | GET /api/auctions/:id | 1,600 | 9 | 12 | 18 |
| QPS150 | GET bid-context | 8,529 | 23 | 39 | 62 |
| QPS150 | POST bids | 2,842 | 41 | 86 | 235 |
| QPS150 | GET /api/auctions | 2,807 | 20 | 35 | 52 |
| QPS150 | GET /api/auctions/:id | 2,878 | 10 | 16 | 26 |
| QPS200 | GET bid-context | 12,109 | 97 | 282 | 400 |
| QPS200 | POST bids | 4,021 | 121 | 334 | 432 |
| QPS200 | GET /api/auctions | 4,000 | 85 | 252 | 353 |
| QPS200 | GET /api/auctions/:id | 4,077 | 36 | 152 | 244 |
| QPS300 | GET bid-context | 14,328 | 237 | 406 | 497 |
| QPS300 | POST bids | 4,322 | 240 | 428 | 516 |
| QPS300 | GET /api/auctions | 4,990 | 189 | 341 | 436 |
| QPS300 | GET /api/auctions/:id | 4,647 | 129 | 280 | 382 |
| QPS400 | GET bid-context | 14,728 | 243 | 405 | 503 |
| QPS400 | POST bids | 4,173 | 230 | 421 | 509 |
| QPS400 | GET /api/auctions | 4,571 | 187 | 330 | 425 |
| QPS400 | GET /api/auctions/:id | 4,538 | 129 | 272 | 361 |

---

## hot-auction-pattern (1분 단위, API는 bid-context/bids만)

### 6차 (RAM 903MB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| 0-1min\* | GET bid-context | 1,388 | 1,670 | 5,705 | 21,251 |
| 0-1min\* | POST bids | 900 | 463 | 1,118 | 1,895 |
| 1-2min\* | GET bid-context | 2,524 | 596 | 1,407 | 2,655 |
| 1-2min\* | POST bids | 2,158 | 342 | 868 | 1,277 |
| 2-3min | GET bid-context | 2,471 | 845 | 1,026 | 1,371 |
| 2-3min | POST bids | 1,819 | 470 | 819 | 1,202 |
| 3-4min | GET bid-context | 2,548 | 746 | 1,588 | 19,961 |
| 3-4min | POST bids | 2,775 | 395 | 1,055 | 1,987 |
| 4-5min | GET bid-context | 3,563 | 530 | 939 | 1,064 |
| 4-5min | POST bids | 3,530 | 311 | 575 | 693 |

\*경계 20초 조정한 정정값(원인: 10-round8 §7.1). 0-1min의 극단적 p99는
아티팩트가 아니라 실제 프리즈(02:53:00 UTC)와 겹친 진짜 값.

### 8차 (RAM 1.8GiB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| 0-1min | GET bid-context | 2,540 | 534 | 954 | 1,096 |
| 0-1min | POST bids | 2,360 | 332 | 603 | 739 |
| 1-2min | GET bid-context | 3,509 | 536 | 925 | 1,066 |
| 1-2min | POST bids | 3,416 | 324 | 594 | 739 |
| 2-3min | GET bid-context | 3,536 | 538 | 951 | 1,177 |
| 2-3min | POST bids | 3,477 | 314 | 572 | 703 |
| 3-4min | GET bid-context | 3,592 | 524 | 898 | 1,035 |
| 3-4min | POST bids | 3,587 | 310 | 551 | 726 |
| 4-5min | GET bid-context | 3,563 | 530 | 939 | 1,064 |
| 4-5min | POST bids | 3,530 | 311 | 575 | 693 |

---

## bid-only-load 분산(noSSE)

### 6차 (RAM 903MB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| QPS50 | GET bid-context | 3,140 | 99 | 331 | 904 |
| QPS50 | POST bids | 1,070 | 118 | 419 | 2,250 |
| QPS50 | GET /api/auctions | 1,054 | 166 | 329 | 501 |
| QPS50 | GET /api/auctions/:id | 1,016 | 34 | 128 | 843 |
| QPS100 | GET bid-context | 5,107 | 14 | 21 | 36 |
| QPS100 | POST bids | 1,703 | 23 | 31 | 61 |
| QPS100 | GET /api/auctions | 1,745 | 14 | 21 | 31 |
| QPS100 | GET /api/auctions/:id | 1,660 | 7 | 9 | 15 |
| QPS150 | GET bid-context | 8,468 | 100 | 308 | 562 |
| QPS150 | POST bids | 2,814 | 100 | 320 | 454 |
| QPS150 | GET /api/auctions | 2,825 | 126 | 330 | 1,731 |
| QPS150 | GET /api/auctions/:id | 2,822 | 62 | 201 | 401 |
| QPS200 | GET bid-context | 12,294 | 22 | 48 | 85 |
| QPS200 | POST bids | 4,097 | 35 | 68 | 108 |
| QPS200 | GET /api/auctions | 4,101 | 21 | 41 | 64 |
| QPS200 | GET /api/auctions/:id | 4,094 | 9 | 19 | 32 |
| QPS300 | GET bid-context | 15,984 | 169 | 331 | 436 |
| QPS300 | POST bids | 4,954 | 182 | 355 | 445 |
| QPS300 | GET /api/auctions | 5,434 | 146 | 298 | 383 |
| QPS300 | GET /api/auctions/:id | 5,331 | 94 | 241 | 323 |
| QPS400 | GET bid-context | 17,551 | 192 | 326 | 430 |
| QPS400 | POST bids | 5,532 | 167 | 324 | 420 |
| QPS400 | GET /api/auctions | 6,010 | 172 | 291 | 389 |
| QPS400 | GET /api/auctions/:id | 5,795 | 110 | 235 | 318 |

### 8차 (RAM 1.8GiB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| QPS50 | GET bid-context | 3,264 | 20 | 26 | 36 |
| QPS50 | POST bids | 1,087 | 30 | 37 | 49 |
| QPS50 | GET /api/auctions | 1,176 | 17 | 22 | 27 |
| QPS50 | GET /api/auctions/:id | 1,000 | 9 | 12 | 25 |
| QPS100 | GET bid-context | 5,077 | 27 | 77 | 133 |
| QPS100 | POST bids | 1,691 | 37 | 75 | 183 |
| QPS100 | GET /api/auctions | 1,624 | 22 | 55 | 87 |
| QPS100 | GET /api/auctions/:id | 1,763 | 11 | 26 | 46 |
| QPS150 | GET bid-context | 8,665 | 30 | 46 | 131 |
| QPS150 | POST bids | 2,889 | 48 | 122 | 323 |
| QPS150 | GET /api/auctions | 2,939 | 27 | 40 | 109 |
| QPS150 | GET /api/auctions/:id | 2,837 | 10 | 15 | 27 |
| QPS200 | GET bid-context | 12,050 | 141 | 354 | 460 |
| QPS200 | POST bids | 3,957 | 161 | 405 | 505 |
| QPS200 | GET /api/auctions | 4,038 | 113 | 296 | 415 |
| QPS200 | GET /api/auctions/:id | 4,004 | 58 | 207 | 289 |
| QPS300 | GET bid-context | 13,435 | 261 | 416 | 550 |
| QPS300 | POST bids | 3,921 | 244 | 437 | 578 |
| QPS300 | GET /api/auctions | 4,650 | 197 | 331 | 439 |
| QPS300 | GET /api/auctions/:id | 4,458 | 135 | 276 | 357 |
| QPS400 | GET bid-context | 14,355 | 249 | 396 | 487 |
| QPS400 | POST bids | 4,809 | 195 | 424 | 583 |
| QPS400 | GET /api/auctions | 4,903 | 181 | 299 | 391 |
| QPS400 | GET /api/auctions/:id | 4,743 | 124 | 242 | 334 |

---

## bid-only-load 핫경매집중

### 6차 (RAM 903MB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| QPS50 | GET bid-context | 3,153 | 65 | 89 | 374 |
| QPS50 | POST bids | 1,050 | 26 | 40 | 83 |
| QPS50 | GET /api/auctions | 1,103 | 19 | 37 | 77 |
| QPS50 | GET /api/auctions/:id | 1,000 | 7 | 11 | 14 |
| QPS100 | GET bid-context | 4,435 | 975 | 1,353 | 1,428 |
| QPS100 | POST bids | 1,311 | 389 | 670 | 800 |
| QPS100 | GET /api/auctions | 1,555 | 363 | 662 | 785 |
| QPS100 | GET /api/auctions/:id | 1,447 | 300 | 592 | 714 |
| QPS150 | GET bid-context | 3,628 | 1,245 | 1,503 | 11,060 |
| QPS150 | POST bids | 804 | 425 | 740 | 976 |
| QPS150 | GET /api/auctions | 1,280 | 605 | 841 | 10,585 |
| QPS150 | GET /api/auctions/:id | 1,113 | 330 | 605 | 870 |
| QPS200 | GET bid-context | 4,465 | 958 | 1,371 | 1,505 |
| QPS200 | POST bids | 1,603 | 439 | 792 | 944 |
| QPS200 | GET /api/auctions | 1,669 | 355 | 655 | 781 |
| QPS200 | GET /api/auctions/:id | 1,426 | 292 | 601 | 754 |
| QPS300 | GET bid-context | 4,489 | 981 | 1,381 | 1,540 |
| QPS300 | POST bids | 1,398 | 438 | 796 | 954 |
| QPS300 | GET /api/auctions | 1,360 | 371 | 669 | 812 |
| QPS300 | GET /api/auctions/:id | 1,612 | 300 | 602 | 722 |
| QPS400 | GET bid-context | 4,457 | 952 | 1,373 | 1,480 |
| QPS400 | POST bids | 1,599 | 472 | 833 | 964 |
| QPS400 | GET /api/auctions | 1,661 | 344 | 638 | 784 |
| QPS400 | GET /api/auctions/:id | 1,420 | 296 | 616 | 788 |

### 8차 (RAM 1.8GiB)

| 구간 | API | 요청수 | 평균(ms) | p95(ms) | p99(ms) |
|---|---|---:|---:|---:|---:|
| QPS50 | GET bid-context | 3,221 | 100 | 207 | 592 |
| QPS50 | POST bids | 1,073 | 33 | 64 | 119 |
| QPS50 | GET /api/auctions | 1,149 | 26 | 56 | 92 |
| QPS50 | GET /api/auctions/:id | 1,000 | 11 | 17 | 22 |
| QPS100 | GET bid-context | 3,870 | 906 | 1,516 | 1,743 |
| QPS100 | POST bids | 1,111 | 315 | 737 | 875 |
| QPS100 | GET /api/auctions | 1,336 | 336 | 751 | 893 |
| QPS100 | GET /api/auctions/:id | 1,289 | 245 | 680 | 802 |
| QPS150 | GET bid-context | 3,950 | 1,151 | 1,630 | 1,779 |
| QPS150 | POST bids | 914 | 430 | 789 | 1,027 |
| QPS150 | GET /api/auctions | 1,455 | 421 | 776 | 993 |
| QPS150 | GET /api/auctions/:id | 1,214 | 362 | 731 | 858 |
| QPS200 | GET bid-context | 3,925 | 1,122 | 1,502 | 1,748 |
| QPS200 | POST bids | 1,305 | 459 | 793 | 958 |
| QPS200 | GET /api/auctions | 1,350 | 407 | 714 | 882 |
| QPS200 | GET /api/auctions/:id | 1,328 | 342 | 680 | 857 |
| QPS300 | GET bid-context | 3,931 | 1,145 | 1,587 | 1,765 |
| QPS300 | POST bids | 1,076 | 466 | 788 | 964 |
| QPS300 | GET /api/auctions | 1,285 | 425 | 776 | 934 |
| QPS300 | GET /api/auctions/:id | 1,262 | 353 | 722 | 869 |
| QPS400 | GET bid-context | 3,857 | 1,141 | 1,695 | 1,908 |
| QPS400 | POST bids | 1,099 | 573 | 920 | 1,049 |
| QPS400 | GET /api/auctions | 1,226 | 419 | 768 | 999 |
| QPS400 | GET /api/auctions/:id | 1,286 | 355 | 701 | 924 |

---

## 원본

- 6차 데이터 원본: `/private/tmp/.../scratchpad/api_stage_report_round6.md`,
  정정 계산: `/private/tmp/.../scratchpad/fix_round6_gaps.py` 실행 결과
- 8차 데이터 원본: `/private/tmp/.../scratchpad/api_stage_report_output.md`
- 프리즈(스크랩 갭) 탐지: `up{job="backend-spring"}` range query,
  15초 step, 6차 세션 전체(02:07~03:25 UTC) — 상세는 10-round8 문서 §7.1
