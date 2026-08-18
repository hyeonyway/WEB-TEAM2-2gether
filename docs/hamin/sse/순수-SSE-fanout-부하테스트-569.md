# 순수 SSE fan-out 부하테스트 (#569, 2026-08-18)

지금까지는 실제 입찰(`POST /api/auctions/:id/bids`)이 만드는 SSE 이벤트로 fan-out 비용을 봤는데, 여기엔 항상 DB 락 경합·bid 처리 로직이 섞여 있었다. 이번엔 그걸 완전히 걷어내고 **실제 입찰 처리 없이 Redis publish로 이벤트만 직접 주입**해서 auction/notification/wallet 3채널의 순수 fan-out 비용만 threadpool vs `sse-virtual-threads`로 비교한다.

**설계**: 경매 15개, 각 경매 담당 입찰자 계정 10명(총 150명, `/api/me/stream` 연결 대상일 뿐 실제 입찰 없음), auction 구독자 500명(**전원이 15개 경매 전부 구독**), 이벤트 QPS 130 고정. 자세한 설계 논의는 [569-pure-sse-fanout-loadtest-plan.md](569-pure-sse-fanout-loadtest-plan.md)(이슈 [#569](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/569)) 참고.

**구현**: 기존 `/api/auctions/stream/test-events/random-bid`가 Redis publish를 안 거치고 `connectionManager.broadcast()`를 직접 호출하던 걸 실제 `AuctionStreamPublisher.publish()` 경로를 타도록 고치고, notification/wallet에도 같은 패턴의 테스트 전용 발행 서비스를 추가했다. 세 채널을 한 번에 묶어 발행하는 `POST /api/test/sse-fanout/random-bid-event`(`@Profile("test")`)를 새 k6 스크립트 [`sse/pure-fanout.js`](../../../backend/src/test/k6/sse/pure-fanout.js)가 QPS130으로 호출한다.

## 실행 전 만난 함정들

1. **`test` 프로필 누락** — `up-all-redis-sse.sh`(`PROFILE=""`)로는 `@Profile("test")`인 새 컨트롤러들이 아예 등록 안 돼서 전부 스프링 기본 404. `PROFILE=test` (threadpool) / `PROFILE=test,sse-virtual-threads` (가상스레드)로 직접 `up-all.sh`를 불러야 함.
2. **프로필 오타 한 글자**: `sse-virtual-thread`(s 없음)로 띄우면 Spring이 이걸 존재하지 않는 프로필로 조용히 무시해서 threadpool 그대로 뜬다 — 에러 없이 조용히 잘못된 비교가 나올 뻔함.
3. **`SessionCsrfFilter`가 익명 POST를 전부 막음** — `/api/auth/login`, `/api/auth/signup`을 뺀 모든 POST/PUT/PATCH/DELETE에 걸리는데, 조합 발행 엔드포인트가 세션 쿠키 없는 익명 POST라 403으로 100% 막혔다. 백엔드(공용 보안 필터)는 안 건드리고, `pure-fanout.js`가 이미 로그인해둔 bidder 세션의 쿠키+CSRF 토큰을 실어 보내도록 k6 스크립트만 수정해서 해결.
4. `seed/seed-load-test-auctions.js`가 세션 인증 전환(#469) 전 JWT 방식 그대로 남아있어서(`Authorization: Bearer` 사용) 같은 CSRF 필터에 막힘 — 이 스크립트는 안 고치고, `pure-fanout.js`의 `setup()`이 이미 로그인해둔 bidder 세션으로 자체적으로 경매 15개를 시드하도록 우회.

## threadpool vs 가상스레드 비교 (QPS130, 500명 전원 15개 경매 구독, 150명 me-stream, 2분)

| 지표 | Threadpool (`20260818T114357-pure-fanout.json`) | Virtual Thread (`20260818T113753-pure-fanout.json`) |
|---|---|---|
| `fanout_publish_success` | 100% | 100% |
| `containerCpu.backend.peakCores`(cap 2.0) | 2.0058 | 2.0046 |
| `peakCfsThrottledPeriodsPerSec` | 10.06 | 10.03 |
| `auction_sse_delivery_latency` avg/p95/p99/max | **44.7ms / 113ms / 189ms / 535ms** | **1,527.7ms / 5,681ms / 9,096ms / 10,119ms** |
| `me_sse_delivery_latency` avg/p99 | 32,746ms / 60,222ms | 33,996ms / 69,804ms |
| `http_req_duration`(발행 POST) avg | 156.9ms | 169.2ms |
| `dropped_iterations` | 37 | 34 |
| `virtualExecutorAvgTaskDurationMs["auction-sse"]` | (해당 없음, threadpool) | 1,176.7ms |
| `virtualExecutorActiveMax["auction-sse"]` | (해당 없음) | 211,566 |
| backend 컨테이너 메모리 peak(cap 903MB) | 893.0MB (98.9%) | 899.0MB (99.6%) |
| JVM 힙 peak 사용(`-Xmx384m`) | 258.3MB / 384MB (67.3%) | 318.2MB / 384MB (82.9%) |
| GC pause 30초 구간 최댓값 | 556.0ms | **1,710.3ms** |

*(메모리 지표는 두 결과 JSON엔 원래 없었음 — `run-k6.sh`에 자동 수집을 막 추가한 직후라 이 두 실행 시점엔 아직 반영 전이었고, 컨테이너 이름은 매 프로필 전환마다 재생성돼 id가 바뀌지만 mysql/redis 컨테이너는 재시작 없이 계속 떠있었던 걸 기준점 삼아 Prometheus에서 두 실행 시각의 실제 backend 컨테이너 id를 역추적해서 사후 조회함.)*

**핵심 발견 — 같은 CPU 포화도인데 auction 지연시간이 threadpool이 30~50배 빠름**: 둘 다 backend CPU가 cap(2 core)에 완전히 포화(스로틀링 ~10/s로 동일)돼있는데, `auction_sse_delivery_latency`는 threadpool이 압도적으로 좋다(avg 44.7ms vs 1,527.7ms). 원인은 동시성 제한 유무로 보임 — threadpool은 `auctionSseBroadcastTaskExecutor`(core4/max8)로 동시 실행 개수가 자연히 묶이고 emitter 500개 send가 한 스레드 안에서 순차 처리되는 반면, `VirtualThreadSseTaskExecutor`는 `AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY` 기본값이 0(무제한)이라 이벤트가 들어오는 족족 새 가상스레드를 만들어버려 `virtualExecutorActiveMax`가 211,566까지 치솟고 개별 task 평균 처리시간이 1.2초까지 늘어난다. me(notification/wallet) 쪽은 둘 다 30초 이상으로 비슷하게 심각 — CPU 자체가 없어서 채널 차이 없이 둘 다 무너진 것으로 보임.

**메모리도 CPU와 마찬가지로 이미 거의 다 찬 상태**(컨테이너 메모리 903MB cap 대비 98.9~99.6%) — CPU만 병목인 게 아니라 메모리도 같이 여유가 없다. 그 안에서도 virtual thread 쪽이 힙 사용량(318MB vs 258MB)과 GC pause 30초 최댓값(1,710ms vs 556ms, 3배)이 뚜렷하게 더 나쁨 — `virtualExecutorActiveMax`가 211,566까지 쌓인 것과 같은 맥락으로, 처리 못 하고 쌓여있는 가상스레드 task/객체들이 그만큼 힙을 더 먹고 GC 부담도 키우는 것으로 보인다.

**⚠️ 이 비교는 "완전 과부하 상태에서 뭐가 덜 무너지나"를 보는 것** — QPS130×500명×15경매 전부 구독은 이 2vCPU 환경에 CPU를 완전히 소진시켜서(포화+스로틀링), "동일 총 처리량에서 CPU를 얼마나 효율적으로 쓰는지"를 깨끗하게 보려던 원래 목적엔 안 맞는 부하 수준이 됐다. **다음: QPS/구독자 수를 낮춰서 CPU 여유 있는 지점에서 한 쌍 더 받아 정상 부하 비교도 필요.**

## `AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY` 캡 스윕 (같은 포화 조건, QPS130/500명/15경매)

위 "무제한" virtual thread 결과가 CPU만 포화된 게 아니라 `virtualExecutorActiveMax`가 211,566까지 치솟는 **진짜 executor 포화 상태**였다는 걸 확인했으니, 2026-08-14 세마포어 캡 실험(그땐 executor가 안 포화돼서 효과가 안 보였음) 때와 달리 이번엔 캡 효과가 뚜렷하게 드러날 조건이다. `PROFILE=test,sse-virtual-threads AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY=<값>`으로 200/100 두 지점을 스윕.

| 지표 | 무제한(cap=0, `113753`) | cap=10000 (`122242`) | cap=1000 1차 (`121605`) | cap=1000 2차 (`122722`) | cap=200 (`115816`) | cap=100 (`120319`) | cap=50 (`120755`) | threadpool 참고(`114357`) |
|---|---|---|---|---|---|---|---|---|
| `virtualExecutorActiveMax["auction-sse"]` | 211,566 | **10,000**(캡 고정) | **1,000**(캡 고정) | **1,000**(캡 고정) | **200**(캡 고정) | **100**(캡 고정) | **50**(캡 고정) | (해당 없음) |
| `virtualExecutorAvgTaskDurationMs["auction-sse"]` | 1,176.7ms | 249.96ms | 28.07ms | 28.84ms | 6.10ms | 3.05ms | 1.44ms | (해당 없음) |
| `auction_sse_delivery_latency` avg/p95/p99/max | **1,527.7 / 5,681 / 9,096 / 10,119ms** | 10,980.3 / 42,511 / 56,577 / 71,893ms | 13,541.4 / 48,933 / 64,938 / 79,321ms | 7,792.7 / 33,890 / 47,088 / 63,186ms | 8,669.8 / 34,918 / 46,919 / 61,393ms | 7,634.5 / 30,830 / 43,496 / 56,713ms | 7,822.8 / 32,013 / 44,713 / 59,693ms | **44.7 / 113 / 189 / 535ms** |
| `me_sse_delivery_latency` avg/p95 | 33,996 / - | 25,869 / 34,905 | 23,903 / 31,412 | 33,321 / 46,217 | 36,867 / 52,514 | 37,075 / 50,913 | 34,660 / - | 32,746 / - |
| backend CPU 피크 | 2.0046/2 (포화) | 2.0066/2 (포화) | 2.0047/2 (포화) | 2.0052/2 (포화) | 2.0036/2 (포화) | 1.9993/2 (포화) | 2.0021/2 (포화) | 2.0058/2 (포화) |
| backend 컨테이너 메모리 피크 | 899.0MB/903MB(99.6%) | 940.2MB/946.9MB(99.30%) | 940.7MB/946.9MB(99.35%) | 941.7MB/946.9MB(99.45%) | 898.3MB/903MB(99.5%) | 897.1MB/903MB(99.3%) | **945.4MB/946.9MB(99.84%, 역대 최고)** | 893.0MB/903MB(98.9%) |
| JVM 힙 피크 | 318.2/384MB(82.9%) | 307.3/402.7MB(76.3%) | 374.7/402.7MB(93.1%) | 278.5/402.7MB(69.2%) | 316.2/384MB(82.4%) | 295.4/384MB(76.9%) | 323.9/402.7MB(80.5%) | 258.3/384MB(67.3%) |
| GC pause 30초 최댓값 | 1,710.3ms | 1,160.2ms | 880.1ms | 656.2ms | 844.1ms | 897.6ms | 518.1ms(가장 낮음) | 556.0ms |

**재현성 확인 — cap=1000을 한 번 더 돌려보니 avg 13,541.4ms → 7,792.7ms로 거의 2배 차이**, heap peak도 93.1% → 69.2%로 크게 흔들림. **"cap=1000이 유독 최악"이라는 앞선 결론은 철회** — 두 번의 cap=1000 결과(7,793~13,541ms)는 그냥 cap=50~10000 전체가 걸쳐있는 "캡 걸리면 대략 7,600~13,500ms대"라는 넓은 범위 안에 있는 것으로 보는 게 맞다. 즉 **캡 값(50 vs 200 vs 1000 vs 10000)끼리는 서로 유의미하게 구별이 안 되고(전부 노이즈 범위 안), "캡이 있냐 없냐"만 확실히 갈린다** — 무제한(1,527.7ms)만 확실히 좋고 나머지는 다 비슷하게 나쁨.

**결론(수정)**: 캡을 얼마로 걸든(50~10,000) 무제한보다 5~9배 나쁘다는 건 재확인됨(2회 반복해도 유지). 다만 캡 값들 사이의 순서·단조성(예: "1000이 200보다 나쁘다")은 **1회성 노이즈였을 가능성이 높아 신뢰하지 않는 게 맞음** — 다른 캡 값들도 반복 실행 없인 순서를 논하기 어려움.

동시성 개수(`virtualExecutorActiveMax`)는 캡 그대로 정확히 걸리고, 개별 task 평균 소요시간은 캡이 커질수록 자연스럽게 늘어난다(1.44ms→~28ms→1,176.7ms) — **세마포어 메커니즘 자체는 항상 예상대로 동작**했다는 뜻. 그런데도 유저 체감 delivery latency가 캡 있을 때 전부 나쁜 건, #575에서 짚은 "broadcast() 순회가 send executor의 acquire()를 기다리며 블로킹된다"는 메커니즘이 캡 크기와 무관하게 항상 어느 정도 발동하기 때문으로 보인다.

**다음**: (1) #575(dispatch non-blocking화)를 실제 구현해서 캡과 무관하게 broadcast() 자체가 안 막히게 만드는 게 근본 해법일 가능성이 높음 — "캡을 걸면 무조건 무제한보다 나쁘다"는 재확인된 결과가 그 이슈의 근거를 강화함. (2) 캡 값들 사이의 순서를 알고 싶다면 각 값을 최소 2~3회씩 반복 실행해야 함 — 지금 데이터로는 "무제한 vs 캡 있음"만 결론 낼 수 있음.

## #575 수정 검증 (`VirtualThreadSseTaskExecutor` acquire()를 태스크 스레드로 이동, 2026-08-18)

위 캡 스윕에서 나온 "캡을 걸면 broadcast() 순회 자체가 막혀서 오히려 느려진다"는 가설을 실제로 고쳐서
검증함(이슈 [#575](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/575), 브랜치
`fix/575-sse-broadcast-nonblocking-dispatch`). `concurrencyLimiter.acquire()`를 `execute()`
호출자(=broadcast() 순회 루프)가 아니라 `super.execute()`가 새로 띄우는 가상스레드 안에서
하도록 옮겨서, 캡이 꽉 찬 상태에서도 호출자는 항상 즉시 리턴하게 만들었다.

같은 조건(QPS130/500명/15경매)에서 cap=100으로 재현:

| 지표 | fix 전 (`20260818T120319`) | fix 후 (`20260818T133403`) |
|---|---|---|
| `auction_sse_delivery_latency` avg/p95/p99/max | 7,634.5 / 30,830 / 43,496 / 56,713ms | **1,980.9 / 7,936 / 9,122 / 9,585ms** |
| `virtualExecutorActiveMax["auction-sse"]` | 100(캡 꽉 참) | **2** |
| `virtualExecutorAvgTaskDurationMs["auction-sse"]` | 3.05ms | 0.056ms |
| backend CPU 피크 | 1.9993/2 (포화) | 2.0044/2 (포화) |
| backend 컨테이너 메모리 피크 | 897.1MB/903MB(99.3%) | 942.1MB/946.9MB(99.5%) |
| JVM 힙 피크 | 295.4/384MB(76.9%) | 346.5/402.7MB(86.1%) |
| GC pause 30초 최댓값 | 897.6ms | 1,931.6ms |

**auction_sse_delivery_latency가 무제한(1,527.7ms) 수준까지 회복됨(7,634.5ms → 1,980.9ms, ~3.9배 개선)** —
#575의 원인 분석과 수정이 유효함을 실측으로 확인.

**`virtualExecutorActiveMax`가 100(캡 꽉 참)에서 2로 떨어진 이유**: broadcast()가 더 이상
안 막히니 emitter마다 즉시 새 가상스레드가 뜨고, 그 안에서 acquire()가 끝나자마자 매우
짧게(0.056ms) send만 하고 끝나버려서, 어느 순간에 봐도 동시에 실행 중인 게 몇 개 안 되는 것 —
캡 100 자체가 병목이 될 일이 거의 없어졌다는 뜻.

**⚠️ `virtualExecutorAvgTaskDurationMs`가 3.05ms→0.056ms로 준 건 "send가 실제로 빨라져서"보다는
"측정 구간이 바뀌어서"가 더 큰 이유**: `startNanos`를 기록하는 위치가 수정 전엔 acquire() 직후·
`super.execute()`(새 가상스레드 생성) **호출 전**이었는데, 수정 후엔 acquire()가 새 가상스레드
**안으로** 들어가면서 `startNanos`도 그 스레드가 이미 떠서 acquire까지 통과한 뒤로 밀렸다.
즉 수정 전엔 "새 가상스레드가 실제로 스케줄링돼서 뜨는 시간"(CPU 2코어가 포화된 상태라 이것도
공짜가 아니었음)까지 taskDuration에 같이 잡혔고, 수정 후엔 그게 측정 구간 밖으로 빠지면서
순수 send 시간만 남았다. 시스템 전체 혼잡도가 줄어든 실질 효과도 어느 정도 있겠지만, 이 숫자
하나만으로 "send 자체가 4배 빨라졌다"고 해석하면 과장 — 진짜 신뢰할 유저 체감 지표는
`auction_sse_delivery_latency` 쪽.

**메모리/GC는 오히려 소폭 나빠짐**(GC pause 897.6→1,931.6ms, 힙 76.9%→86.1%) — broadcast()가
안 막히니 순간적으로 더 많은 가상스레드/클로저 객체가 생성됐다 버려지는 처리량 자체가 늘어난
결과로 보이며, delivery latency 개선폭(3.9배)에 비하면 감수할 만한 트레이드오프로 판단됨.

**`me_sse_delivery_latency`는 그대로 나쁨**(36,024ms) — 이번 수정 범위(auction 채널의 캡 대기
문제) 밖의 별개 병목(backend CPU 자체 부족, wallet은 여전히 threadpool 고정)이라 예상대로 무관.

### threadpool과 비교하면 — 여전히 44배 차이, "가상스레드 vs threadpool" 격차는 안 좁혀짐

| | threadpool (`114357`) | 가상스레드 무제한 (`113753`) | 가상스레드 cap=100, fix 전 (`120319`) | 가상스레드 cap=100, fix 후 (`133403`) |
|---|---|---|---|---|
| `auction_sse_delivery_latency` avg | **44.7ms** | 1,527.7ms | 7,634.5ms | 1,980.9ms |
| threadpool 대비 배율 | 1배 | 34배 | 171배 | **44배** |

**#575 수정이 고친 건 "캡을 걸면 가상스레드 무제한보다 더 나빠지던" 부작용이지, "가상스레드가 threadpool보다 나쁘다"는 근본 격차가 아니다** — fix 후 cap=100(1,980.9ms)은 가상스레드 무제한(1,527.7ms) 수준을 캡을 걸고도 유지하는 데 성공한 것뿐, threadpool(44.7ms)에는 여전히 44배 못 미친다.

**threadpool이 이 부하 수준(QPS130×500명, 2vCPU 포화)에서 계속 이기는 이유로 보이는 것**: threadpool은 `auctionSseBroadcastTaskExecutor`(core4/max8)에서 이벤트 1건당 **스레드 하나가 500개 emitter send를 전부 순차로 처리**(`SynchronousSseSendDispatcher`, 스레드 생성 없음). 가상스레드 쪽은 (fix 후에도) 이벤트 1건마다 **500개의 새 가상스레드를 생성**해서 각자 처리 — CPU가 이미 2코어로 완전히 포화된 상태에서는, 개별 생성·스케줄링·Micrometer 계측 오버헤드가 500배로 곱해지는 이 방식이 "스레드 하나가 500번 도는" 것보다 불리한 것으로 보인다.

**결론**: 이번 QPS130/500명 조건에서는 threadpool 프로필이 여전히 최선이고, 가상스레드+#575 수정 조합은 "가상스레드를 꼭 써야 한다면 캡을 걸어도 안전하다(무제한 수준 유지)"는 것을 보여준 것으로 이해하는 게 정확함 — threadpool을 능가하는 결론은 이 실험 범위에서 아직 안 나옴.

### 왜 이 조건에서 threadpool이 유리한가

- **가상스레드의 장점(블로킹 I/O를 공짜로 많이 기다리기)이 애초에 발휘될 여지가 없다.** 로컬호스트라 `emitter.send()`가 거의 즉시 끝나는 순수 CPU 작업에 가깝고, "많은 수를 동시에 블로킹 대기시켜야 하는" 상황 자체가 없다. 그 결과 가상스레드는 장점(공짜 대기) 없이 단점(생성·스케줄링 비용)만 지불하는 구조가 된다.
- **CPU가 이미 2코어로 완전히 포화된 상태에서는, 동시성 단위를 늘려도(스레드를 더 만들어도) 처리 가능한 총량은 안 늘어나고 스케줄링/컨텍스트 스위칭 비용만 늘어난다.** threadpool은 이벤트 1건당 스레드 1개(`auctionSseBroadcastTaskExecutor`)가 500개 emitter를 전부 순차로(`SynchronousSseSendDispatcher`) 처리 — 코어 수(2)에 근접한 동시성(core4/max8)을 유지해서 경합이 적다. 가상스레드는 (#575 수정 후에도) 이벤트 1건마다 500개의 새 가상스레드를 만들어서, 코어 수 대비 수백 배 많은 동시 실행 단위가 소수 carrier 스레드를 두고 경쟁한다.
- **생성·계측 오버헤드가 500배로 곱해진다.** 가상스레드 하나 생성 + `submitted`/`active`/`completed`/`failures`/`taskDuration` Micrometer 호출 5번이 이벤트 1건당 500번씩, 초당 130건이면 초당 65,000번 반복된다. threadpool은 이 오버헤드 자체가 없다(스레드 재사용, 계측도 이벤트당 1회 수준).
- **짧게 살고 버려지는 객체(클로저, 가상스레드 자체)가 GC 압박을 늘린다** — 실제로 가상스레드 쪽이 threadpool보다 힙 사용량·GC pause가 항상 더 나쁘게 나온 것과 일치.

요약하면, 가상스레드는 "동시에 기다려야 할 게 많은데 그 대기가 진짜 블로킹(I/O)인 상황"에서 강점이 있는데, 지금 벤치마크는 그 상황 자체를 안 만들고 있어서 강점 없이 비용만 낸다.

### 실험 계획 — 가상스레드가 유리한 조건 만들기

**실험 A: emitter.send()에 인위적 지연 주입**

- **가설**: 진짜 I/O 대기(네트워크 latency)가 생기면, threadpool(고정 4~8스레드)은 그 지연 동안 다음 emitter로 못 넘어가서 500명 배달 총 시간이 `지연 × 500 / 스레드수`로 늘어나는 반면, 가상스레드는 수천 개가 동시에 블로킹돼도 비용이 거의 없어서 오히려 유리해진다.
- **구현**: `SseEmitterRegistry.send()` 직전(또는 send 자체를 감싸는 지점)에 테스트 전용 인위적 지연을 넣는다 — 예: `SSE_SEND_ARTIFICIAL_DELAY_MS` 환경변수(기본 0, `@Profile("test")` 등으로 프로덕션엔 영향 없게 게이팅)로 `Thread.sleep(N)`을 emitter별 send 직전에 끼워 넣는 방식. 실제 네트워크 latency를 흉내 내는 것이라, I/O 블로킹처럼 동작하도록 sleep이면 충분(가상스레드는 `Thread.sleep()`도 캐리어를 안 붙잡고 park하므로 이 시뮬레이션이 유효함).
- **스윕**: 지연 0ms(baseline, 지금 결과) / 1ms / 5ms / 20ms — 각각 threadpool vs 가상스레드(무제한, 그리고 cap 하나)로 QPS130/500명 그대로 재현.
- **관찰 지표**: `auction_sse_delivery_latency`(교차점 찾기), backend CPU 피크(지연이 커지면 스레드가 자느라 CPU 점유율 자체는 오히려 낮아져야 정상 — "이제 CPU가 아니라 동시성 한계를 재고 있다"는 신호), 스레드/가상스레드 활성 개수.
- **기대**: 지연이 어느 지점(threadpool의 유효 동시성 4~8에 걸리는 시점, 대략 `지연 × 500 / 8`이 유의미해지는 수준)을 넘으면 가상스레드가 threadpool을 역전할 것으로 예상.

**실험 B: 구독자 수를 대폭 늘려서 threadpool의 "스레드 하나가 순차로 다 도는" 구조 자체를 병목으로 만들기**

- **가설**: threadpool은 이벤트 1건당 브로드캐스트를 스레드 1개가 담당하므로, 구독자 수가 커지면 이벤트 1건 처리 시간이 구독자 수에 비례해서 늘어난다(O(N), N=구독자 수). 가상스레드는 청크 분배 없이 1:1이라도 최소한 병렬로 나눠 처리되니, 구독자 수가 아주 커지면 threadpool의 단일 스레드 순회가 먼저 무너질 것으로 예상.
- **구현**: 기존 `pure-fanout.js`의 `SSE_SUBSCRIBERS` 파라미터를 500 → 2,000 → 5,000 → 10,000 → 20,000으로 스윕. 다만 "SSE 없이 순수 처리량 상한 탐색" 섹션에서 `MAX_VUS`를 과도하게 키우면 nginx `worker_connections`/loopback ephemeral port 고갈로 클라이언트 쪽 아티팩트(`reset by peer`)가 섞였던 전례가 있으니, 구독자 수를 올릴 때 `MAX_VUS`/nginx 설정도 같이 확인해야 함. 총 이벤트량(QPS×구독자수)이 너무 커지지 않도록 구독자 수를 올릴 때 `EVENT_QPS`는 오히려 낮춰서(예: QPS 20~30) "브로드캐스트 1건이 구독자 수만큼 도는 비용" 자체만 격리해서 보는 것도 고려.
- **관찰 지표**: 위와 동일(`auction_sse_delivery_latency`, backend CPU, `virtualExecutorAvgTaskDurationMs`) + threadpool 쪽은 특히 `dbidding.auction.sse.send.duration`(Timer, 이번 세션에서 07번 대시보드에 추가한 패널)로 "이벤트 1건이 구독자 전원한테 다 나가는 데 걸리는 총 시간"이 구독자 수에 비례해서 늘어나는지 직접 확인 가능.
- **기대**: 어느 구독자 수 지점부터 threadpool의 단일 스레드 순회 시간이 가상스레드의 (오버헤드 포함) fan-out 시간을 넘어서는 크로스오버가 나타날 것.ㄴ

## 실험 A 실측 결과: emitter.send() 인위적 지연 스윕 (2026-08-18, 이슈 [#579](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/579))

`SseEmitterRegistry.send()`에 `SSE_SEND_ARTIFICIAL_DELAY_MS`(emitter별 send 직렬화 락을 잡은 채로
sleep)를 추가해서, QPS130/구독자500(위 조건과 동일, 이미 2vCPU 포화 상태) 그대로 delay
5/20/50/100ms × threadpool/가상스레드(무제한)를 스윕했다. 자세한 구현/계획은
`579-sse-send-artificial-delay-plan.md`(브랜치 `feature/579-sse-send-artificial-delay` 전용 — dev에 미병합이라 이 저장소엔 없음) 참고.

`sweep-sse-send-delay.sh`의 자동 재시도가 threshold crossing(실패는 아니고 k6 exit code만 실패)을
진짜 실패로 오인해 곧바로 재시도했는데, 그 재시도의 `setup()`(150명 로그인)이 직전 무거운 실행의
executor 백로그가 아직 안 빠진 채 CPU가 계속 포화 상태라 504로 죽는 경우가 있었다(`virtual` 4개
조건 전부, `threadpool` 100ms 1회). 이 재시도 실패분은 분석에서 제외했고, 스크립트의 재시도
판정 로직(exit code 대신 결과 JSON 존재 여부로 판단 + 재시도 전 CPU/큐 drain 대기) 개선은
별도 후속 작업으로 남겨둔다.

| profile | delay | auction avg/p95/p99 | me avg/p95/p99 | backend CPU | heap peak | container mem | GC pause 최댓값 | callerRunsSaturated (broadcast/wallet/notif-fanout, 회/초) |
|---|---|---|---|---|---|---|---|---|
| threadpool | 5ms | 5,583/10,917/12,606ms | 36,981/89,915/93,701ms | 2.01/2 | 81.5%(328MB) | 99.7% | 412ms | 25.1/0.0/0.0 |
| threadpool | 20ms | 56,272/117,151/123,643ms | 21,196/50,106/54,348ms | 2.00/2 | 94.0%(378MB) | 100.0% | 3,258ms | 51.1/48.8/5.7 |
| threadpool | 50ms | 69,507/130,169/133,971ms | 16,994/42,296/48,621ms | 1.93/2 | 91.7%(369MB) | 99.99% | 7,333ms | 59.1/96.3/67.3 |
| threadpool | 100ms | 74,797/134,485/138,069ms | 14,280/45,279/60,388ms | 1.75/2 | 90.5%(364MB) | 99.997% | 10,905ms | 53.4/101.0/82.0 |
| threadpool | 100ms(재시도) | 69,227/118,342/123,854ms | 44,083/88,632/93,671ms | 1.75/2 | 93.9%(378MB) | 99.997% | 10,386ms | 9.7/19.3/2.2 |
| virtual | 5ms | **1,964/7,881/20,912ms** | **2,469/9,500/23,503ms** | 1.98/2 | 68.6%(276MB) | 99.99% | 270ms | 0/0/0 |
| virtual | 20ms | **500/4,272/7,266ms** | **733/4,282/10,846ms** | 1.98/2 | 94.5%(381MB) | 99.99% | 946ms | 0/0/0 |
| virtual | 50ms | **1,706/2,693/10,966ms** | **1,294/4,431/13,616ms** | 1.99/2 | 75.0%(302MB) | 99.99% | 826ms | 0/0/0 |
| virtual | 100ms | **8,038/15,222/16,793ms** | **3,369/13,017/15,209ms** | 1.98/2 | 89.2%(359MB) | 99.95% | 624ms | 0/0/0 |

**핵심 발견 — 가상스레드가 테스트한 모든 delay에서 threadpool을 압도함**: 같은 5ms에서도
auction 배달 avg가 threadpool 5,583ms vs 가상스레드 1,964ms, 20ms에서는 56,272ms vs 500ms로
거의 100배 차이. `callerRunsSaturated`도 가상스레드는 전 구간 0 — executor 백프레셔 자체가
안 걸림. 위 "왜 threadpool이 유리한가" 절의 무지연(0ms) 결론(threadpool이 34~44배 우세)과
정반대 방향이 delay 5ms부터 바로 나타난다 — **실험 A의 가설(진짜 blocking I/O가 생기면
가상스레드가 유리해진다)이 확인됨**.

**threadpool은 20ms에서 이미 포화 상한에 도달** — 20→50→100ms로 delay를 더 늘려도 별로 안
나빠진다(56s→70s→75s, 완만). 예상대로 50/100ms는 "더 무너진다"는 확인 사살일 뿐 새 정보는
아니었다.

**가상스레드 곡선은 단조증가가 아님**(5ms:1,964 → 20ms:500 → 50ms:1,706 → 100ms:8,038) —
조건당 1회씩만 측정해서 반복성/노이즈를 걸러내지 못한 결과로 보인다. CPU가 이미 두 프로필
다 거의 2/2코어 꽉 찬 채로 돌고 있어서(QPS130×구독자500 자체가 이미 CPU 상한에 걸린
상태 — 위 "이 조건에서 threadpool이 유리한 이유" 절과 동일한 조건), "순수한 delay 효과"만
보기엔 잡음이 크다.

**threadpool의 wallet은 `CountingDiscardPolicy`로 조용히 이벤트를 드롭한다**(포화율
96~101회/초) — 못 받은 이벤트는 지연 측정 자체가 안 되므로, threadpool의
`me_sse_delivery_latency`는 실제보다 낙관적으로 보일 개연성이 있다. 가상스레드는 이 문제가
없다(캐퍼시티 제한이 없어 드롭도 없음).

**결론**: "가상스레드가 blocking I/O 조건에서 threadpool을 이긴다"는 실험 A의 가설은 확인됐다.
다만 이 스윕은 이미 CPU 포화 상태(QPS130/500명) 위에 delay를 얹은 것이라 "delay만의 순수한
효과"와 "원래도 포화 직전이었던 부하"가 섞여 있다 — QPS/구독자 수를 낮춘 2차 스윕(다음
섹션 예정)으로 CPU 여유가 있는 지점에서 재확인이 필요하다.

### 재현성 확인 — 가상스레드 5ms를 3번 재실행

"가상스레드 20ms(avg 500ms)가 5ms(avg 1,964ms)보다 좋다"는 위 결과가 진짜 역전인지 노이즈인지
확인하려고, backend를 매번 새로 재기동(force-recreate)해서 가상스레드 5ms만 3회 반복했다
(QPS130/구독자500 동일).

| 회차 | `auction_sse_delivery_latency` avg/med/p95/p99 | CPU | heap peak | container mem | GC pause 최댓값 |
|---|---|---|---|---|---|
| 1회 (`145201`) | 1,964 / - / 7,881 / 20,912ms | 1.98/2 | 68.6%(276MB) | 99.99% | 270ms |
| 2회 (`152644`) | 1,646 / 284 / 4,912 / 7,120ms | 1.99/2 | 93.6%(377MB) | 99.99% | 2,070ms |
| 3회 (`153343`) | 1,113 / 44 / 6,788 / 10,419ms | 1.98/2 | 61.6%(248MB) | 99.99% | 582ms |

**3번 다 avg 1,100~2,000ms대, med는 20~300ms대로 일관됨** — threadpool 5ms(avg 5,583ms)보다
매번 확실히 낫다. 반면 "20ms가 5ms보다 좋다"는 역전은 재현이 안 돼서, 앞서 의심한 대로
**1회성 노이즈였던 것으로 결론**. 가상스레드 곡선의 비단조성(5→20→50→100ms)은 조건당 반복
없이 1회씩만 측정한 데 따른 잡음이지, 실제 delay 구간별 성능 차이로 보면 안 된다.

**부수 발견 — `auction_sse_connected`/`me_sse_connected` rate가 항상 정확히 0.5로 나오는 건
측정 아티팩트, 실제 연결 실패가 아님**: 스크립트가 `client.on('open', ...add(true))`,
`client.on('error', ...add(false))`로 기록하는데, `gracefulRampDown`(5s) 안에 VU가 자연 종료를
못 하고 k6가 강제로 끊으면 그 VU는 이미 연결 성공 시점에 `add(true)`를 찍은 뒤 강제 종료
시점에 `error` 콜백이 다시 불려 `add(false)`도 찍는다. 강제 종료된 VU가 많을수록(로그의
`N interrupted iterations`) rate가 0.5에 가까워질 뿐, 절반이 진짜로 연결에 실패한 게 아니다 —
`auction_sse_delivery_latency` 등 실제 관심 지표에는 영향 없음.

**스크립트 수정**: `pure-fanout.js`의 `thresholds`(`rate>0.99`류)가 이런 인위적 지연/과부하
스윕에서는 유효한 측정값이 나왔는데도 k6 exit code만 실패로 떨어뜨려서(그리고 그 가짜 실패를
스윕 스크립트가 재시도하다 백엔드가 아직 회복 중인 채로 재시도해서 로그인이 504로 죽는 문제까지
이어졌음), `thresholds: {}`로 비웠다(브랜치 `feature/579-sse-send-artificial-delay`). 관련 지표
자체는 metrics에 그대로 남아 결과 JSON에서 확인 가능.

## 실험 A 2차 스윕 — QPS/구독자 수를 낮춰서 CPU 포화 없이 재측정 (2026-08-18)

1차 스윕(QPS130/구독자500)은 delay 없이도 이미 backend CPU가 2/2 core 포화 상태라 "delay만의
순수한 효과"를 못 봤다. `EVENT_QPS=20 SSE_SUBSCRIBERS=100 ./sweep-sse-send-delay.sh`로 CPU
여유가 있는 지점에서 같은 delay(5/20/50/100ms) × threadpool/가상스레드를 재스윕했다.

| profile | delay(ms) | backend CPU | heap peak | container mem | GC pause 최댓값 | auction_sse_delivery avg/p95/p99 | me_sse_delivery avg/p95/p99 | auction_sse_events rate | callerRunsSaturated(broadcast/wallet) |
|---|---|---|---|---|---|---|---|---|---|
| threadpool | 5 | 0.56/2 core | 66.8%(269MB) | 94.2% | 104ms | 285/537/571ms | 11/17/22ms | 593.6/s | 0.0 / 0.0 |
| threadpool | 20 | 0.72/2 core | 61.2%(246MB) | 92.9% | 122ms | 1,044/1,975/2,067ms | 42/63/348ms | 507.1/s | 3.0 / 0.0 |
| threadpool | 50 | 0.67/2 core | 58.9%(237MB) | 90.5% | 150ms | 3,320/6,007/6,525ms | 1,215/3,667/4,055ms | 472.9/s | 4.0 / 0.0 |
| threadpool | 100 | 0.66/2 core | 67.7%(273MB) | 97.9% | 128ms | **24,227/43,249/46,014ms** | 12,773/22,429/22,617ms | 358.6/s | 4.4 / 0.45 |
| virtual | 5 | 0.66/2 core | 69.8%(281MB) | 93.3% | 104ms | **9/11/12ms** | 12/18/22ms | 1,345.3/s | 0.0 / 0.0 |
| virtual | 20 | 0.66/2 core | 52.2%(210MB) | 90.8% | 142ms | **25/28/33ms** | 38/64/69ms | 1,344.1/s | 0.0 / 0.0 |
| virtual | 50 | 0.76/2 core | 49.6%(200MB) | 91.4% | 174ms | 2,294/3,856/4,055ms | 1,342/4,997/5,193ms | 1,343.7/s | 0.0 / 0.0 |
| virtual | 100 | 0.80/2 core | 92.0%(370MB) | 99.9% | **1,100ms** | **47,188/89,919/93,704ms** | 6,193/13,135/13,449ms | 805.3/s | 0.0 / 0.46 |

(연결 성공률은 이번엔 전 구간 100% — 1차 스윕에서 본 "rate=0.5" 아티팩트도 없음, VU 강제 종료
자체가 안 일어난 것으로 보임. CPU도 전 구간 0.56~0.80/2 core로 여유 있어서, 포화가 결과를
왜곡하지 않는다.)

**핵심 발견 1 — 5ms/20ms에서는 가상스레드가 압도적**: `auction_sse_delivery_latency` avg가
threadpool 285ms/1,044ms 대비 가상스레드는 **9ms/25ms** — 30~40배 차이. CPU 포화 없이 순수하게
비교해도 "blocking I/O가 조금이라도 있으면 가상스레드가 유리하다"는 실험 A의 원래 가설이
깨끗하게 확인된다.

**핵심 발견 2 — 100ms에서는 오히려 가상스레드가 threadpool보다 더 나쁘다(47,188ms vs
24,227ms), 그리고 이건 스레드 모델의 문제가 아니다**: `EVENT_QPS=20`이면 이벤트 간격이
평균 1/20 = **50ms**다. 구독자 전원이 15개 경매 전부 구독하므로 이벤트마다 매 emitter에
send가 한 번씩 들어가는데, `SseEmitterRegistry.send()`는 emitter별 `ReentrantLock`으로 send를
직렬화한다(#508). 즉 **같은 emitter를 향한 send 큐는 "50ms마다 하나씩 들어오는데 처리(=delay)에
100ms 걸리는" 구조**가 되어, 유입률(20/s)이 처리율(1000ms/100ms=10/s)의 2배(ρ=2)인 **원천적으로
불안정한 대기열**이 된다 — 이건 스레드 모델(threadpool/가상스레드)과 무관하게, 정적으로도
당연히 무너지는 조건이다. delay=50ms는 정확히 그 경계(ρ=1, 유입 간격=처리 시간)라서 두 프로필
다 여기서부터 눈에 띄게 나빠지기 시작한다(threadpool 1,044→3,320ms, 가상스레드 25→2,294ms).

**그럼 왜 가상스레드가 threadpool보다 더 심하게 무너지나**: threadpool은 `auctionSseTaskExecutor`
(core4/max8) + `auctionSseBroadcastTaskExecutor`가 각각 queue capacity 2000으로 **유한**하고,
꽉 차면 `CountingCallerRunsPolicy`가 발동해 더 이상 새 task를 안 받고 caller 스레드에서 동기
실행시킨다 — 결과적으로 큐에 쌓일 수 있는 작업량 자체에 상한이 있는 **자연스러운 backpressure**
역할을 한다. 가상스레드(`VirtualThreadSseTaskExecutor`, 캡 미설정 시 무제한)는 이런 상한이
없어서, 대기열이 불안정해지면 emitter당 lock을 기다리는 가상스레드가 **무제한으로 계속
쌓인다** — `auction_sse_events` 처리율이 805.3/s로 떨어진 것(5~50ms 구간의 ~1,344/s 대비 40%
감소)과 일치. 즉 threadpool은 큐 상한 때문에 "일부 요청을 희생하고 나머지는 어떻게든 처리"하는
쪽으로, 가상스레드는 "전부 다 받아서 다 같이 늦어지는" 쪽으로 무너지는 것으로 보인다.

**결론**: 이 100ms 데이터 포인트는 "가상스레드가 threadpool보다 나쁘다"는 재역전으로 읽으면
안 된다 — **EVENT_QPS=20에 delay=100ms를 같이 쓴 실험 설계 자체가 이론적으로 불안정한
조건(ρ=2)이라, 어느 스레드 모델을 쓰든 무너지는 게 정상**이다. **delay와 QPS를 같이 바꿀 땐
반드시 `delay < 1/QPS`(emitter당 처리율이 유입률을 넘는) 범위로 스윕해야 유효한 비교가 된다**
— 이번 조건(QPS20) 기준으로는 delay 5/20ms만 안정 구간, 50ms는 경계, 100ms는 무효.

**정정 — 캡을 걸어도 이 실패 모드는 안 고쳐짐, "admission control" 추측은 틀렸음**: 위에서
"캡이 있으면 threadpool처럼 backpressure가 걸려서 덜 나빠질 것"이라고 예상했는데,
`AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY=100`(delay=100ms, 나머지 동일 조건)으로 재현해보니
avg **48,561ms**로 무제한(47,188ms)과 거의 동일했다. `virtualExecutorActiveMax`가 정확히
100(캡 꽉 참)까지 차고 `avgTaskDurationMs`도 delay 그대로(~105ms)인데도 전체 지연은 안 줄었다
— **`VirtualThreadSseTaskExecutor`의 캡은 세마포어일 뿐, threadpool의 bounded queue +
`CountingCallerRunsPolicy`처럼 "꽉 차면 거부/caller-run으로 떠넘기는" 메커니즘이 없다.**
병목이 "emitter별 락 대기"에서 "세마포어 permit 대기"로 옮겨갈 뿐, 유입이 처리율을 넘어서는
근본 구조(ρ=2)는 그대로라 대기열이 무제한으로 계속 쌓인다 — 그리고 이번엔 cap 값(100)이
구독자 수(100)와 같아서 사실상 무제한과 다를 게 없었던 점도 있다. **결론: 이 ρ>1 폭주는
동시성 캡으로 못 고친다** — 필요한 건 emitter별로 오래된/밀린 send를 버리는 정책 같은 실제
"거부" 메커니즘이고, 캡(세마포어)은 그 역할을 못 한다.

## 실험 B — 구독자 수 스윕 (2026-08-18, delay=5ms/QPS=20 고정)

delay/QPS는 안정 구간(5ms ≪ 1/20=50ms)에 고정하고, `SSE_SUBSCRIBERS`만 500 → 2,000으로
올려서 "브로드캐스트 1건이 구독자 수만큼 도는 비용"을 봤다(`sweep-sse-subscribers.sh`,
이슈 #569). nginx/Tomcat/컨테이너 fd 상한은 미리 올려둠(`worker_connections`/
`worker_rlimit_nofile` 8192→32768, `SERVER_TOMCAT_MAX_CONNECTIONS` 3000→32768,
컨테이너 `ulimits.nofile` 8192→65536).

| profile | subscribers | auction avg/p95/p99 | me avg/p95/p99 | CPU | heap peak | GC pause 최댓값 | container mem | 비고 |
|---|---|---|---|---|---|---|---|---|
| threadpool | 500 | 1,353/2,563/2,736ms | 12/19/33ms | 1.00/2 | 279MB/402MB(69%) | 330ms | 99.8% | auction-broadcast CallerRuns 3.4/s |
| threadpool | 2,000 | **22,335/52,404/57,998ms** | 885/6,441/8,056ms | **2.00/2 포화** | 373MB/402MB(93%) | **2,482ms** | 99.97% | - |
| virtual | 500 | **12/18/23ms** | 15/22/28ms | 1.00/2 | 311MB/402MB(77%) | 239ms | 99.6% | callerRuns 0 |
| virtual | 2,000 | 816/1,763/1,783ms(⚠️신뢰불가) | 507/1,118/1,292ms(⚠️신뢰불가) | 1.95/2 | **힙 crash** | 408ms | 99.99% | `fanout_publish_success` 0%, `http_req_failed` 85% |

**threadpool 2,000명**: 예상대로 더 나빠짐(500명 1,353ms → 2,000명 22,335ms) — 브로드캐스트
스레드 하나가 `2,000×5ms=10초`씩 붙잡히는 O(N) 구조가 그대로 심해진 것. CPU도 2/2 완전
포화, GC pause 2.5초.

**virtual 2,000명 — "느려진" 게 아니라 진짜 힙 OOM으로 죽었다.** `fanout_publish_success`
rate=0%(2,229건 전부 실패), `http_req_failed` 85%, 백엔드 로그에
`java.lang.OutOfMemoryError: Java heap space`가 Tomcat 요청 스레드/Redis 이벤트루프
양쪽에서 발생. 표의 virtual/2,000 latency 숫자는 이미 대부분 실패한 뒤 살아남은 극소수
요청만 반영한 것이라 의미 없다(⚠️ 표시).

**원인**: JDK 가상스레드는 콜스택을 **힙에**(StackChunk 객체로) 저장한다 — 플랫폼 스레드처럼
네이티브 메모리가 아니다. `AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY=0`(무제한)에 구독자
2,000명×QPS20 = 초당 4만 건의 send task가 제출되는데, `SimpleAsyncTaskExecutor`는 큐/거부
정책이 없어서 제출 즉시 새 가상스레드를 계속 만든다. 처리 속도가 그 제출 속도를 잠깐이라도
못 따라가면 아직 안 끝난 가상스레드(+각자 들고 있는 직렬화된 payload)가 힙에 계속 쌓이고,
`-Xmx384m`(t4g.micro 재현)이라는 작은 힙이 순식간에 바닥난 것으로 보인다.

**실험 A의 ρ>1 폭주와는 다른 종류의 실패로 처음엔 보였다**: 실험 A(delay>1/QPS)는 병목이
"emitter별 락 대기"라 전역 동시성 캡으로 못 고쳤는데(위 참고), 이번엔 병목이 "동시에 살아있는
가상스레드 개수 자체(=힙에 쌓인 StackChunk 개수)"라서 `AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY`로
캡을 걸면 이 OOM을 막을 수 있을 것으로 예상하고 검증했다.

### 캡=100으로 재현 — 여전히 죽는다. 캡의 구조적 한계를 발견

`AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY=100`(delay=5ms, 구독자 2,000명, 나머지 동일)으로
재현했으나(`20260818T175005-pure-fanout.json`) **여전히 죽었다** — `fanout_publish_success`
0%(2,178건 전부 실패), container 메모리 99.99%, `heapMaxBytes: None`(조회 시점에 이미 응답
불가 상태). 이번엔 로그에 `OutOfMemoryError`가 직접 안 찍히고 대신
`RedisCommandTimeoutException`(Redis 커맨드가 1초를 못 지킴)이 났는데, 이는 원인이 아니라
결과다 — 메모리 압박으로 GC/스케줄링이 마비되면서 Redis 커맨드 하나 보내는 것조차 타임아웃난
것으로 보인다.

**캡이 왜 안 먹혔는가 — #575 수정과 정면으로 충돌하는 구조적 한계**: #575에서
`concurrencyLimiter.acquire()`를 broadcast() 호출자가 아니라 **새로 띄운 가상스레드 안**으로
옮겼다(broadcast() 순회 자체가 캡 때문에 안 막히게 하려고). 그런데 이 설계는 **캡이 걸려있어도
task가 제출되는 즉시 가상스레드 객체(+힙에 상주하는 StackChunk)가 먼저 만들어지고, 그 다음에야
세마포어를 기다리는** 구조다. 즉 캡은 "동시에 몇 개가 **실행**되냐"만 제한하지 "몇 개가
**대기 상태로 살아있냐**"는 전혀 제한하지 않는다. 제출 속도(구독자2,000×QPS20=초당4만)가
캡이 뚫어주는 처리 속도(캡100 → 초당 최대 20,000/delay=5ms=... 대략 캡/delay 수준)보다
빠르면, 세마포어 대기 중인 가상스레드가 힙에 무제한으로 쌓이는 건 **캡 값과 무관하게 똑같이
일어난다.** delay=100ms 스윕에서 "캡을 걸어도 latency가 안 줄었다"는 앞선 발견과 근본적으로
같은 메커니즘이었던 것 — 그때는 latency로, 이번엔 heap OOM으로 드러났을 뿐이다.

**결론**: `VirtualThreadSseTaskExecutor`의 캡(세마포어)은 broadcast() 호출자가 안 막히게
해주는 것(#575)일 뿐, 힙 고갈을 막는 진짜 admission control이 아니다. 진짜 막으려면
세마포어를 획득하기 *전에*(가상스레드를 만들기도 전에) 제출 자체를 거부/큐잉해야 하는데,
지금 구조는 그렇게 안 돼 있다 — 설정값(캡 크기) 튜닝으로 해결할 문제가 아니라 구조적
한계다. threadpool의 bounded queue + `CountingCallerRunsPolicy`는 정확히 "제출 시점에
거부"하는 구조라 이런 실패 모드 자체가 없다는 점과 대비된다.

## tryAcquire + discard로 구조적 한계 수정 (#585, 2026-08-18)

위 결론에 따라 `VirtualThreadSseTaskExecutor.execute()`를 고쳤다(브랜치
`feature/585-virtual-executor-admission-control`) — `Semaphore.acquire()`(블로킹, 새
가상스레드 안)를 `Semaphore.tryAcquire()`(논블로킹, **호출자 스레드**)로 바꿔서, permit이
없으면 가상스레드 자체를 만들지 않고 그 자리에서 discard한다. `AuctionSseConnectionManager
.broadcast()`의 emitter 순회도 캡 초과 시 매번 같은 앞쪽 구독자만 permit을 따는 편향을
막기 위해 `eventId` 기준으로 순회 시작 위치를 회전하도록 같이 고쳤다. 필요 캡 값은
Little's Law(`구독자 수 × QPS × delay`)로 추정 — 자세한 배경/근거는 이슈
[#585](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/585) 참고.

### 검증 — 캡=200(Little's Law 계산값)으로 구독자 2,000명 재현

`feature/579`(delay 실험)와 `feature/585`(이번 수정)를 로컬에서 합친 브랜치로, 이전에
힙 OOM으로 크래시했던 조건(구독자 2,000명, delay=5ms, QPS=20)을 캡=200으로 재현했다
(`20260818T183657-pure-fanout.json`).

| 지표 | 캡=100(수정 전, 크래시) | 캡=200(수정 후) |
|---|---|---|
| 결과 | 힙 OOM 크래시, `fanout_publish_success` 0% | **크래시 없음**, `fanout_publish_success` 100% |
| `auction_sse_delivery_latency` avg/p95/p99 | (측정 불가 — 크래시) | **51,205 / 131,285 / 153,345ms** |
| container 메모리 peak | 99.99% | 99.99% |
| heap peak | (측정 불가) | **96.3%**(388/402MB) |
| GC pause 최댓값 | - | **1,028ms** |
| discard(`dbidding.sse.broadcast.saturated{auction-sse}`) | (해당 없음, 캡이 안 먹혀서 discard 자체가 안 됨) | **1,572회/초** |
| `virtualExecutorActiveMax[auction-sse]` | 100(캡 꽉 참, 그런데도 크래시) | 200(캡 꽉 참) |

**핵심 성과 — 힙 OOM은 완전히 막혔다**: `fanout_publish_success`/`http_req_failed`/
`auction_sse_connected`가 전부 정상(100%/0%/100%) — 서버가 살아남았다. discard 기반
admission control이 의도대로 작동한다는 뜻.

**다만 discard가 대량으로 일어나고, 그 결과 배달 지연이 여전히 심각하다**: `fanout_
publish_success`(이벤트 **발행** POST 요청 성공률)는 100%인데, 이건 "구독자 2,000명한테
다 배달됐다"는 뜻이 전혀 아니다 — 실제 배달 지표(`auction_sse_delivery_latency`)는
avg 51초·p99 153초로 여전히 나쁘고, discard도 초당 1,572건이나 발생한다. 즉 **"발행 성공률"과
"배달 성공률"은 완전히 다른 지표**라는 걸 이번에 명확히 확인함 — discard 방식은 서버를
살리는 대신 상당수 이벤트를 조용히 버리는 트레이드오프를 그대로 감수한다.

**캡=200(Little's Law 계산값)인데도 힙이 96.3%까지 차는 이유**: Little's Law(`L=λW`)는
평균적으로 필요한 동시성만 알려주는데, discard 기반 loss 시스템(Erlang-B 성격)은 도착이
버스트/랜덤이라 **평균에 딱 맞춘 캡으로는 여전히 상당한 손실률이 남는다** — 정확히 평균에
맞추면 이론적으로도 여유(headroom)가 0인 셈이라, 순간적으로 넘치는 만큼은 계속 버려야 한다.
게다가 GC pause(최대 1,028ms) 동안은 모든 가상스레드가 멈춰 permit도 안 풀리니, 그 구간
동안 실제 처리율이 뚝 떨어지는 것도 discard를 더 키운다.

**결론**: 캡을 200으로 늘려도(이전 100 대비) 여전히 힙이 거의 꽉 차 있어서, 캡을 더
올리는 게 안전한지는 불확실하다 — 오히려 "구독자 2,000명 자체가 이 박스(2vCPU,
`-Xmx384m`=t4g.micro 재현)가 실시간으로 감당하기엔 과한 트래픽"이라는 신호로 보는 게
맞다. 캡을 더 올리기보다는, 이 캡(200)에서 discard가 거의 0에 수렴하는 구독자 수(이 박스의
실제 처리 상한)를 역으로 찾는 게 다음 단계로 더 의미 있을 것으로 보임.

### 구독자 1,000명으로 재시도 — threadpool vs 가상스레드(캡=200) 직접 비교

같은 조건(delay=5ms, QPS=20, 캡=200)에서 구독자 수만 2,000 → 1,000으로 낮춰서
threadpool과 가상스레드를 나란히 비교했다(`20260818T185101`=가상스레드,
`20260818T185607`=threadpool).

| 지표 | 가상스레드(캡200) | threadpool |
|---|---|---|
| `auction_sse_delivery_latency` avg/med/p95/p99 | 891 / **30** / 5,969 / 9,980ms | 2,833 / 2,825 / 5,387 / **5,757ms** |
| `auction_sse_events`(실제 배달률) | **11,400/s** | 4,711/s |
| discard(`saturated{auction-sse}`) | **2,263회/초** | (해당 없음, `auction-broadcast` 4/s만) |
| CPU | 1.98/2(거의 포화) | 1.72/2 |
| heap peak | 90.2%(363/402MB) | 81.0%(326/402MB) |
| GC pause 최댓값 | 976ms | 258ms |
| container mem | 99.98% | 99.83% |

**단순히 "가상스레드가 이겼다"고 하기엔 트레이드오프가 섞여있다**:

- 중앙값은 가상스레드가 압도적(30ms vs 2,825ms, ~94배) — 대부분의 요청이 거의 즉시 배달됨.
- 그런데 가상스레드는 초당 2,263건을 그냥 버린다. discard된 건 클라이언트에 아예 도착을
  안 하므로 지연 측정 표본에 안 잡힌다 — 위 latency 숫자는 "버려지지 않고 살아남은 것들만"의
  지연이라는 뜻.
- 그런데도 가상스레드가 실제로 배달한 총량(11,400/s)이 threadpool(4,711/s)보다 2배 이상
  많다 — threadpool은 안 버리는 대신 하나도 안 늦게 못 보내서(1,000명×5ms=5초를 스레드
  하나가 순차 처리), 2분짜리 테스트 안에 처리 못 하고 큐에 계속 쌓이는 게 많아 결국 총
  배달량 자체가 더 적어진다.
- CPU도 가상스레드가 1.98/2로 거의 다 쓴다 — 버리는 대신 그만큼 더 많은 일을 실제로 처리
  하고 있다는 뜻.

**결론**: 구독자 1,000명(2,000명보다 낮은, 서버가 어느 정도는 버티는 구간)에서 가상스레드가
속도도 빠르고 총 처리량도 더 많지만, 그 대가로 16~20% 정도의 메시지를 조용히 유실한다.
threadpool은 유실은 없지만 다 늦게(중앙값 2.8초) 배달하고 총량 자체도 적다. 이제 "어느
쪽이 나으냐"는 순수 성능 문제가 아니라 **"가끔 SSE 업데이트 하나 놓쳐도 되는지"라는
서비스 정책 문제**로 넘어간다 — 이 프로젝트의 auction 실시간 갱신처럼 다음 이벤트나
재조회로 자연스럽게 따라잡는 성격이면 가상스레드+discard 쪽이 유리하고, 무손실이
중요하면 threadpool(또는 더 낮은 부하)이 유리하다.

### 구독자 500명으로도 비교 — 진짜 깨끗한 스위트스팟

같은 조건(delay=5ms, QPS=20, 캡=200)에서 구독자 수를 500으로 더 낮춰서 재비교했다
(`20260818T190321`=threadpool, `20260818T192045`=가상스레드).

| 지표 | threadpool | 가상스레드(캡200) |
|---|---|---|
| `auction_sse_delivery_latency` avg/med/p95/p99 | 1,346/1,343/2,556/2,729ms | **10.5/9/16/36ms** |
| `auction_sse_events`(배달률) | 2,496/s | **6,689/s** |
| discard(`saturated{auction-sse}`) | (해당 없음) | **31회/초**(거의 없음) |
| CPU | 0.98/2 | 0.87/2 |
| heap peak | 84.3%(339/402MB) | 81.9%(330/402MB) |
| GC pause 최댓값 | 382ms | 423ms |
| container mem | 99.98% | 99.73% |

**구독자 500명은 진짜 깨끗한 승리 구간**: 가상스레드가 지연은 ~128배 빠르고(10.5ms vs
1,346ms), 배달량도 2.7배 많은데, discard도 초당 31건으로 거의 무시할 수준(1,000명 때
2,263건/초였던 것과 비교하면 확연히 다름)이다. 즉 **500명에서는 "빠르면서 손실도 거의
없는" 진짜 스위트스팟**이고, 1,000명부터 discard가 급격히 늘기 시작하는 것으로 보인다 —
500과 1,000 사이 어딘가가 이 박스(2vCPU, `-Xmx384m`, 캡200, delay5ms/QPS20)의 "손실 없이
버틸 수 있는" 실질적 상한에 가까울 것으로 추정된다.

### 구독자 수별 요약 (delay=5ms, QPS=20, 가상스레드 캡=200)

| 구독자 수 | threadpool avg | 가상스레드 avg | 가상스레드 discard/s | 가상스레드 heap peak | 비고 |
|---|---|---|---|---|---|
| 500 | 1,346ms | **10.5ms**(~128배) | 31 | 81.9% | 스위트스팟 — 압도적 우위, 손실 거의 없음 |
| 1,000 | 2,833ms | **891ms**(중앙값 30ms) | 2,263 | 90.2% | 가상스레드가 여전히 빠르고 총 배달량도 많지만 손실 발생 |
| 2,000 | (미실측, threadpool은 캡 없이도 안 죽음) | 51,205ms | 1,572 | 96.3% | 손실은 상대적으로 줄지만 지연 자체가 심각 — 이미 서버 용량 초과 |

## 캡별 힙 비용 스윕 시도 (2026-08-18) — 두 번 다 캡이 병목이 아니어서 실패

"캡을 힙 예산 기준으로 정하려면 task 1개당 힙 비용을 알아야 한다"는 문제의식에서, 구독자
수는 고정하고 캡만 바꿔가며 heap peak 변화의 기울기를 보려 했다(`sweep-sse-cap-heap.sh`).
두 번 다 캡이 실제로 병목이 되지 않아 유효한 신호를 못 얻었다.

### 1차 시도 — 구독자 2,000명 고정, 캡 50/100/200/400

| cap | `virtualExecutorActiveMax` | heap peak | discard/s | `auction_sse_delivery` avg/p95/p99 |
|---|---|---|---|---|
| 50 | 3 | 96.1%(368.9MB) | 568.7 | 44,369/115,828/137,064ms |
| 100 | 2 | 94.7%(363.5MB) | 383.6 | 39,372/105,371/127,509ms |
| 200 | 3 | 94.9%(364.3MB) | 276.7 | 35,253/99,814/123,871ms |
| 400 | 2 | 95.5%(366.7MB) | 163.3 | 39,472/113,590/140,987ms |

**실패 원인**: `virtualExecutorActiveMax`가 캡 값(50~400)과 무관하게 2~3에 머물렀다 —
구독자 2,000명 자체가 캡과 무관하게 이미 힙을 거의 다 차지하는 조건이라(위 "구독자
2,000명" 결과에서도 heap 96.3%, GC pause 1초씩 확인됨), 캡을 뭘 주든 시스템이 이미
GC 스래싱 상태였다. GC pause 동안은 active count가 0에 가깝게 떨어지므로 샘플링된
"최대 active"도 낮게 나온다. 즉 이건 "캡의 순수한 heap 비용"이 아니라 "이미 무너진
상태에서는 캡이 의미가 없다"는 걸 재확인한 것에 가깝다.

### 2차 시도 — 구독자 500명 고정, 캡 50/100/200/300

| cap | `virtualExecutorActiveMax` | heap peak | discard/s | `auction_sse_delivery` avg/p95/p99 |
|---|---|---|---|---|
| 50 | 2 | 65.5%(251.4MB) | 136.1 | 22/24/501ms |
| 100 | 0 | 72.2%(277.1MB) | 64.2 | 14/21/84ms |
| 200 | 0 | 61.0%(234.4MB) | 33.2 | 11/18/47ms |
| 300 | 2 | 51.3%(197.0MB) | 20.7 | 9/15/25ms |

**실패 원인**: 이번엔 시스템이 안 무너졌지만(지연 자체는 전부 우수), 여전히
`virtualExecutorActiveMax`가 0~2로 캡과 무관하게 낮다 — 구독자 500명×QPS20=초당 1만 건
수요에 Little's Law로 필요한 동시성은 `500×20×0.005=50`뿐이라, **캡=50부터 이미 "거의
안 걸리는 캡"**이었다. heap peak가 캡이 커질수록 오히려 줄어드는(251→277→234→197MB)
것도 캡이 실질적 병목이 아니었다는 방증 — 재실행 간 노이즈로 보인다.

**교훈**: 캡을 항상 병목으로 만들려면 **캡 값을 Little's Law 추정치(이 조건에서 50)보다
확실히 작게** 잡아야 한다.

### 3차 시도 — 구독자 500명 고정, 캡 5/10/20/40 — 캡은 드디어 병목으로 작동, 그런데 heap 기울기는 여전히 안 나옴

| cap | `virtualExecutorActiveMax`(=cap?) | heap peak | discard/s | avg task duration | `auction_sse_delivery` avg/p95/p99 |
|---|---|---|---|---|---|
| 5 | **5**(일치) | 67.3%(258.6MB) | 6,476.3 | 5.36ms | 2,782/10,856/14,320ms |
| 10 | **10**(일치) | 65.1%(249.9MB) | 5,907.3 | 5.32ms | 41/65/359ms |
| 20 | **20**(일치) | 56.6%(217.5MB, ↓) | 4,955.2 | 5.32ms | 29/48/156ms |
| 40 | **40**(일치) | 82.9%(318.5MB) | 3,709.2 | 5.44ms | 52/159/938ms |

**드디어 캡이 실제 병목으로 확인됨** — `virtualExecutorActiveMax`가 캡 값과 정확히 일치.
discard율도 캡이 커질수록 깔끔하게 단조감소(6,476→5,907→4,955→3,709/s, 이론과 일치).
task당 평균 소요시간도 캡과 무관하게 ~5.3ms로 고정(delay가 정확히 반영, 캡이 커져도
개별 task 자체는 안 느려짐 — 정상).

**그런데 heap은 캡에 비례해서 안 늘어남** — cap=20이 오히려 cap=5/10보다 heap이 낮다
(217.5MB). 선형회귀하면 기울기가 나오긴 하지만(캡 1당 ~1.8MB) cap=20의 튀는 값 때문에
신뢰하기 어렵다.

**결론 — 이 방법(순간 heap peak 비교)으로는 task 1개당 heap 비용을 못 뽑아낸다**: 캡이
5~40 수준이면 거기서 나오는 StackChunk+payload 총량 자체가 너무 작아서, JVM 전체 heap
노이즈(GC 타이밍, 캡이 커질수록 처리량(`auction_sse_events` 613→1,178→2,069→3,299/s)도
늘어서 그만큼 늘어나는 다른 단명 객체 양) 속에 묻힌다. Prometheus의 순간 heap-usage
샘플링 해상도로는 이 정도 규모의 캡 차이가 만드는 heap 차이를 못 잡아낸다. 필요하다면
힙 덤프 분석이나 JFR 할당 프로파일링처럼 더 직접적인 방법이 필요해 보인다 — 이번
"캡당 heap 비용 정량화" 시도는 여기서 보류.

## 종합 결론 — 가상스레드 채택/캡 설계의 논리

### 1. 왜 가상스레드를 쓰는가

SSE fan-out은 "이벤트 1건 → 구독자 N명에게 개별 send"인데, 각 send는 실제 운영에서는
blocking I/O다.
- **threadpool**: OS 스레드가 비싸서 소수(4~8개)만 유지 → 이벤트 1건당 그 소수 스레드가
  N명을 **순차 처리**(O(N)). 구독자가 늘수록 이벤트 1건 처리 시간이 그대로 늘어난다.
- **가상스레드**: 생성 비용이 거의 없어 N명을 각자 독립 스레드로 **병렬 처리** → 이벤트
  1건의 fan-out 시간이 N과 거의 무관하게 delay 수준으로 유지된다.

실측: **delay가 있고(≈진짜 네트워크) 안정 구간(ρ<1)**에서 가상스레드가 threadpool보다
30~128배 빠름(실험 A 2차, 실험 B 500명). 반대로 **delay=0(순수 CPU, loopback)이거나 이미
CPU 포화**면 가상스레드가 오히려 34~44배 느림(최초 QPS130 비교) — 가상스레드는 "기다릴
게 있을 때만" 이득이고, 없으면 생성/스케줄링/계측 오버헤드만 비용으로 남는다. **채택
조건은 "구독자 많고, 실제 I/O 지연 있고, 그 지연이 이벤트 발행 주기보다 충분히 짧다"**는 것.

### 2. 왜 executor를 분리했는가 (broadcast용 vs send용, #507)

broadcast()는 "이벤트를 구독자 목록에 매핑해 순회·조율"하는 역할이고, 실제 개별 send는
별개다. 같은 executor를 쓰면 send 쪽이 포화됐을 때(캡 꽉 참 등) 순회 자체가 막혀서, 느린
구독자 하나 때문에 전체 이벤트 처리 자체가 밀리는 문제가 생긴다. 그래서 **broadcast
전용(캡 없음, 순수 조율) / send 전용(캡 있음, 실제 I/O)**으로 분리 — 순회는 항상 즉시
끝나고, 병목은 send 계층에만 국한되게 함. #575도 같은 철학의 연장(호출자가 캡 때문에
안 막히게).

### 3. 왜 캡을 걸었는가 (#495, 재확인 #585)

가상스레드의 "무제한 생성이 공짜"라는 건 **CPU 얘기**지 메모리(힙) 얘기가 아니다 — JDK
가상스레드는 콜스택을 힙에(StackChunk) 저장한다. 캡 없이 delay 있는 상태에서 구독자가
많으면(QPS×N이 처리 가능량 초과), 처리 못 한 가상스레드가 힙에 쌓여 **실제로 OOM
크래시**가 났다(구독자 2,000명 실측). threadpool은 스레드 풀 자체가 유한해서 이런
무제한 폭주가 애초에 불가능(자연스러운 캡) — 가상스레드는 그 자연스러운 한계가 없어서
**인위적으로 캡을 걸어야 threadpool과 동등한 안전성**을 갖는다.

### 4. 왜 tryAcquire+discard로 구현했는가 (#585)

- **1차 구현(#575)**: acquire()를 새 가상스레드 **안**에서 블로킹 — 호출자는 안 막히지만,
  가상스레드 객체는 제출 즉시 만들어지고 그 다음에 permit을 기다림 → 대기 중 가상스레드가
  캡과 무관하게 무제한으로 쌓임(OOM의 직접 원인).
- **수정(#585)**: `tryAcquire()`(논블로킹)를 호출자 스레드에서 먼저 확인. permit 없으면
  가상스레드 자체를 아예 안 만들고 버림 → "동시에 살아있는 가상스레드 수"가 캡으로 정확히
  상한 걸리면서, 호출자도 여전히 안 막힌다.
- 이건 threadpool의 bounded queue + CallerRuns/Discard policy와 같은 철학 — **"용량
  초과분은 제출 시점에 거부"**하는 게 진짜 admission control이다. 1차 구현은 "실행
  시점에 대기"였을 뿐 진짜 거부가 아니었다.

### 5. 캡 값은 어떻게 정하나

Little's Law(`구독자수×QPS×delay`)로 하한 계산이 가능하지만, discard 기반은 loss
system(Erlang-B) 성격이라 평균에 딱 맞추면 여전히 손실이 남는다 — 여유가 더 필요하다.
궁극적으로는 "힙이 감당 가능한 동시 작업 수"가 진짜 상한인데, 이번 세션에서 그 정밀
계측(캡당 heap 비용)은 순간 샘플링 노이즈에 묻혀 실패했고, 힙 덤프/JFR 같은 더 직접적인
방법이 필요하다. 실전적으로는 "예상 최대 부하로 Little's Law 계산 → 여유 두고 캡 설정 →
실측으로 discard/latency 확인 후 조정"이 현실적이다.

### 6. 최종 정책 결론

- 가상스레드+캡+discard는 **실제 프로덕션에 가까운 조건(delay 있음, 서버가 감당 가능한
  부하)에서 threadpool보다 확실히 낫다**.
- 대가는 "가끔 이벤트 유실" — auction 실시간 갱신처럼 다음 이벤트/재조회로 자연 복구되는
  케이스엔 괜찮고, 무손실이 필수면 threadpool이나 더 낮은 부하가 맞다.
- **서버 용량 자체를 넘는 트래픽은 어떤 스레드 모델·캡 값도 못 구한다** — 그땐 코드
  튜닝이 아니라 인스턴스 스펙(힙) 자체를 올리는 게 답이다.
- executor를 broadcast/send, 그리고 도메인(auction/notification/wallet)별로 나눈 건
  "한쪽 포화가 다른 쪽 전체를 막지 않게" 격리하려는 것 — 스레드 모델과 무관하게 항상
  유효한 설계 원칙이다.

> 이 문서는 claude의 도움을 받아 작성되었습니다.
