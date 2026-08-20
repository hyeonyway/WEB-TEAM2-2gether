# 실험 A(emitter.send() 인위적 지연) 한계와 재설계

관련: 이슈 [#569](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/569), [#579](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/579)

## 발견한 한계

실험 A는 `SseEmitterRegistry.send()`가 `emitter.send()` 호출 직전에 `Thread.sleep(N)`을
넣어서 "실제 네트워크 I/O 지연"을 흉내냈다. **이 모델이 실제 메커니즘과 다르다.**

`emitter.send()`는 결국 `HttpServletResponse`의 OutputStream에 쓰는 것이고(Spring MVC +
Tomcat, blocking I/O), 이건 TCP 소켓의 **커널 send 버퍼**에 데이터를 복사하는 작업이다.
버퍼에 여유가 있으면 이 write는 거의 즉시 끝난다(memcpy 수준). **클라이언트가 그 데이터를
못 읽어가서(느린 네트워크, 백그라운드 탭, 앱이 멈춤 등) 버퍼가 꽉 찼을 때만** write가
블로킹된다 — TCP 백프레셔다.

즉 실제 지연은 "모든 send가 균일하게 느림"이 아니라, **"대부분은 거의 0ms, 소수의
느린/막힌 클라이언트만 블로킹"**이라는 치우친 분포다. `SSE_SEND_ARTIFICIAL_DELAY_MS`로
모든 send에 똑같이 5/20/50/100ms를 건 실험 A는 이 분포를 전혀 재현하지 못했다.

## 결론에 미치는 영향

- **threadpool 쪽 위험이 실험 A보다 실제로는 더 큽니다.** 구독자 500명 중 진짜
  느린/멈춘 클라이언트가 딱 1명만 있어도, threadpool은 한 스레드가 순차로 순회하니까
  그 1명에서 멈춰서(소켓 타임아웃까지) 나머지 499명 전부가 그 뒤에서 밀린다. 실험 A는
  "다 같이 N ms씩"이었지만 실제로는 "대부분은 즉시, 1명 때문에 나머지 전부가 무한정
  대기"에 더 가깝다 — threadpool한테 훨씬 나쁜 시나리오다.
- **가상 스레드는 오히려 이 시나리오에 원래 목적대로 더 잘 맞는다** — 느린 클라이언트
  하나가 자기 가상 스레드만 파킹시키고, 나머지는 전혀 영향받지 않는다. "가상 스레드가
  blocking I/O 있을 때 유리하다"는 방향성 자체는 오히려 더 강하게 맞는 얘기가 될 수
  있지만, 실험 A가 실측한 **구체적인 숫자(5/20/50/100ms에서의 배율)는 이 실제 분포를
  대변하지 못한다** — 다시 재현해야 한다.

## 재설계 방향

모든 emitter에 균일한 delay를 서버 쪽에서 주입하는 대신, **구독자 중 일부만 "느린
리더"로 만들어서 그들의 클라이언트 쪽 읽기를 실제로 늦추는 방식**이 필요하다 — 서버가
아니라 클라이언트(k6) 쪽에서 진짜 TCP 버퍼를 채우는 방식으로.

### 후보 접근

1. **k6 `client.on('event')` 핸들러 안에서 일부 VU만 동기적으로 오래 머무르기** —
   `k6/x/sse` 확장의 읽기 루프가 "청크 읽기 → 파싱 → 콜백 호출 → 다음 청크 읽기"
   순서로 동작한다면, 콜백 안에서 오래 블로킹(busy-wait 또는 긴 처리)하는 동안 그
   VU는 다음 read를 못 하게 되고, 그 사이 서버가 계속 write하면 결국 커널 버퍼가
   찬다. **다만 이게 진짜로 서버 쪽 backpressure로 이어지는지(k6 x/sse 확장의 실제
   구현이 이 가정과 맞는지)는 검증 안 됨 — 먼저 작은 PoC로 확인 필요.**
2. **일부 VU를 아예 이벤트 루프에서 `sleep()`으로 장시간 멈추기** — 콜백이 아니라
   SSE 연결을 유지한 VU 자체를 명시적으로 재우는 방식. k6 시나리오 설계상 가능한지
   확인 필요(현재 `auctionSse`/`meSse` 함수 구조상 `sse.open()`의 콜백 안에서
   전체 흐름이 도는데, 그 밖에서 별도로 슬립을 걸 지점이 마땅치 않을 수 있음 —
   `sse.open()` 자체의 동작 방식을 다시 봐야 함).
3. **비율 파라미터화** — `SLOW_READER_RATIO`(예: 1~5%) 환경변수로 전체 구독자 중
   몇 %를 느린 리더로 만들지 조절 가능하게 해서, "느린 클라이언트 비율에 따라
   threadpool/가상 스레드가 어떻게 갈리는지" 스윕할 수 있게 설계.

### 검증해야 할 것

재설계 전에, **PoC로 "k6 VU 하나가 콜백에서 오래 머무르면 실제로 서버 쪽
`SseEmitter.send()`가 블로킹되는지"부터 확인**해야 한다 — 이게 안 맞으면 접근 자체를
바꿔야 한다(예: 서버 쪽에서 커넥션별 소켓 버퍼 크기를 낮춰서 backpressure가 더 쉽게
걸리게 만드는 것도 고려 가능).

## PoC 1차 시도 결과 — 검증 실패, 원인 후보 2가지 (2026-08-20)

k6 대신 raw 소켓으로 먼저 확인했다: 정상 리더(reader A, curl)와 **연결만 하고 전혀
안 읽는 리더**(reader B, python 소켓으로 헤더만 읽고 이후 `recv()`를 전혀 안 함) 둘 다
같은 경매를 구독시키고, `/api/test/sse-fanout/random-bid-event`를 65초간 초당
~450건(connection당 새 TLS 커넥션, `Connection: close`)씩 쏴서 reader B 쪽 커널
버퍼가 실제로 쌓이는지, 그리고 reader A의 배달 지연이 커지는지를 봤다.

**관찰**: reader B는 65초간 전혀 안 읽었는데도 종료 시점에 한 번에 9.97MB를
drain했다 — 데이터가 커널(또는 그 경로 어딘가)에 계속 쌓이긴 했다는 뜻. 하지만
정작 보려던 지표(`published_at`~reader A 실제 수신 시각의 델타, 즉 broadcast 시작
이후 개별 emitter로 실제 나가는 데 걸리는 시간)는 pump 내내 평균 5~10ms로 평평했고
reader B가 안 읽는 것과 아무 상관관계가 안 보였다. **"느린 리더가 threadpool의
단일 broadcast 스레드를 막는다"는 가설을 이 PoC로는 확인하지 못했다.**

### 원인 후보 1 — 테스트 하네스 자체가 confound였다

대신 `occurred_at`→`published_at`(broadcast 호출 *이전* 단계, reader B와 무관한
구간) 지연이 1.5초에서 32초까지 계속 불어나는 게 관찰됐다. pump 스크립트가 요청마다
새 TLS 커넥션을 맺어(`Connection: close`) 초당 450건을 쐈더니, `random-bid-event`
엔드포인트 자체가 과부하로 밀린 것으로 보인다(입찰 순번 계산의
`ConcurrentHashMap.compute()` 직렬화 또는 톰캣 커넥션 처리 병목 추정) — 즉 이번
1차 PoC는 "느린 리더가 broadcast를 막는지"가 아니라 "테스트 하네스가 서버를
과부하시켰는지"를 잰 꼴이 됐다. **커넥션을 재사용하는 pump로 고쳐서 이 confound부터
제거해야 한다.**

### 원인 후보 2 — Docker Desktop(macOS) 네트워킹이 TCP backpressure를 못 전달할 수 있다

reader B가 전혀 안 읽었는데도 9.97MB가 매끄럽게 다 전달된 것 자체가 의심스럽다.
Docker Desktop for Mac은 호스트↔컨테이너 트래픽을 vpnkit/gVisor 기반 유저스페이스
프록시를 거쳐 포워딩하는데, 이 프록시가 진짜 커널 TCP zero-window backpressure를
양 끝까지 그대로 전달하지 않고 자체적으로 대량 버퍼링해버릴 가능성이 있다 — 그렇다면
호스트에서 아무리 안 읽어도 컨테이너 쪽(백엔드) `emitter.send()`는 절대 블로킹되지
않는다. `/proc/net/tcp`로 컨테이너 내부에서 reader B 소켓을 포트 매칭으로 찾으려
했으나, 호스트(macOS)에서 본 ephemeral port(49618)가 컨테이너 내부 테이블에서
전혀 안 잡혀서(NAT/프록시 계층에서 포트가 바뀜) 직접 확인은 못 했다 — 이것도 이
프록시 계층 존재의 방증일 수 있다.

**검증 방법**: pump를 커넥션 재사용으로 고친 뒤에도 여전히 배달 지연이 안 보이면,
reader B를 macOS 호스트가 아니라 **백엔드와 같은 컨테이너(또는 같은 docker
네트워크의 별도 컨테이너) 안에서** 실행해서 Docker Desktop의 포워딩 계층을
완전히 우회하고 재시도해야 한다.

### 원인 후보 3(가장 유력) — nginx location 매칭 누락으로 실제 SSE 경로가 버퍼링 켜진 채로 프록시되고 있었다

`nginx/conf.d/default.conf`에서 `proxy_buffering off`는 `location /api/sse/`와
`location /ws/`에만 걸려 있다. 그런데 실제 auction/me SSE 컨트롤러의 경로는
`/api/auctions/stream`([AuctionSseController.java:32](../backend/src/main/java/com/dbidding/auction/sse/AuctionSseController.java:32))과
`/api/me/stream`([SessionMeSseController.java:18](../backend/src/main/java/com/dbidding/global/security/session/SessionMeSseController.java:18))이라 `/api/sse/`
prefix에 안 걸린다 — nginx가 prefix 매칭이라 이 두 경로는 **`location /`(일반
API, `proxy_buffering` 기본값 on)로 빠진다.**

즉 이번 PoC(그리고 이전 실험 A/B 전부)가 쓴 `/api/auctions/stream`은 nginx
기본 버퍼링(메모리 버퍼 + 모자라면 `proxy_max_temp_file_size` 기본값 1024MB까지
**디스크 임시파일로 스풀**)으로 프록시되고 있었을 가능성이 높다. 이러면:

- nginx는 백엔드로부터 계속 읽어서 자기 버퍼/임시파일에 쌓아두고, 클라이언트에게는
  버퍼에서 내보낸다.
- 클라이언트(reader B)가 아무리 안 읽어도 **백엔드 쪽 `emitter.send()`는 nginx가
  항상 잘 받아주는 것처럼 보여서 절대 블로킹되지 않는다** — 9.97MB가 매끄럽게 다
  전달된 것과 정확히 들어맞는다.
- Docker Desktop 네트워킹 계층 의심(원인 후보 2)보다 이게 훨씬 직접적이고 확실한
  설명이다 — 확인 순서상 이것부터 배제해야 한다.

**질문: "버퍼를 nginx에 쌓는 게 원래 맞는 방식인가?"** — 아니다, 이건 의도된
설계가 아니라 **location 매칭 버그(경로 드리프트)로 보인다.** `/api/sse/` 블록
자체의 주석("SSE 이벤트를 버퍼링하지 않고 즉시 전달")이 이미 "SSE는 버퍼링 꺼야
한다"는 설계 의도를 명시하고 있고, 이건 표준적인 원칙이기도 하다:
- **일반 REST API는 버퍼링을 켜두는 게 맞다** — 느린 클라이언트가 있어도 nginx가
  응답을 대신 들고 있어주는 동안 백엔드 스레드는 바로 다음 요청을 처리하러
  돌아갈 수 있다(백엔드 스레드를 슬로우 클라이언트로부터 보호).
- **SSE/스트리밍은 반대로 버퍼링을 꺼야 한다** — 두 가지 이유. (1) 목적 자체가
  "실시간 push"인데 버퍼링을 켜면 지연이 생겨 실시간성이 깨진다. (2) 더 심각한
  건 실패 모드다 — 백엔드가 블로킹되면 스레드 하나가 멈추는 선에서 자연스러운
  backpressure가 걸리지만(threadpool/가상스레드 어느 쪽이든 어느 정도 상한이
  있음), **nginx가 대신 버퍼링하면 느린 클라이언트 하나당 계속 쌓이는 메모리·
  디스크 사용량에 구조적 상한이 없다** — 연결이 오래 유지되는 SSE 특성상 계속
  이벤트가 발행되는데 그걸 다 nginx가 받아서 쌓아두면, 느린 클라이언트가
  여러 명이면 nginx worker 메모리 고갈이나 디스크 임시파일 폭증으로 이어질 수
  있다. 즉 지금 상태는 "backpressure가 안 걸려서 안전하다"가 아니라
  **"진짜 병목(느린 클라이언트)이 백엔드가 아니라 nginx로 옮겨갔을 뿐, 더 안
  보이는 곳에서 더 위험한 방식으로 존재한다"**는 뜻이다.

**확인 완료 — 운영 환경도 동일한 버그를 갖고 있다(2026-08-20)**: 운영 EC2
(`ubuntu@ip-10-0-0-128`)의 `~/nginx/conf.d/default.conf`를 직접 확인한 결과,
로컬 로드테스트 설정과 `location` 블록 구성이 완전히 동일했다(`upstream_server`가
`include upstream-active.inc`로 blue/green 전환용으로 바뀐 것과 `listen 443 ssl
http2`인 것만 다르고, `location /api/sse/`·`location /`·`proxy_buffering` 배치는
100% 같음). 즉 **이건 로드테스트 환경만의 문제가 아니라 실제 운영 환경의 구성
오류다** — `/api/auctions/stream`과 `/api/me/stream`으로 나가는 실제 SSE
트래픽이 지금 운영에서도 `proxy_buffering` 기본값(on)이 걸린 `location /`으로
빠지고 있다. 이 이슈는 이번 #579/#569 실험 범위를 벗어나는 별도의 실제 운영
버그이므로, 이 노트에서 확인 사실만 기록해두고 별도 이슈로 분리해 고치는 게
맞다(수정 방향은 위 "PoC를 계속하려면" 절 참고 — `location` 정규식을 실제
컨트롤러 경로에 맞추면 됨).

**PoC를 계속하려면**: `location ~ ^/api/(auctions/stream|me/stream)`처럼 실제
컨트롤러 경로에 맞게 임시로 고치거나, 아예 nginx를 안 거치고 백엔드 포트
(`18080`)에 직접 붙어서 nginx 버퍼링 변수 자체를 제거한 뒤 재시도해야, 원래
검증하려던 "느린 리더가 백엔드 broadcast를 막는지"를 nginx 영향 없이 순수하게
볼 수 있다.

## nginx 60초 send_timeout에 대응하는 백엔드 쪽 안전망은 없다 (2026-08-20)

"nginx는 TCP 60초 타임아웃 후 커넥션을 버리고 버퍼를 정리하는데, 이 버퍼링을
백엔드가 떠안게 되면 백엔드 쪽엔 그런 안전망이 있냐"는 질문에 대한 확인 결과.

`AuctionSseConnectionManager.connect()`가 `new SseEmitter(CONNECTION_TIMEOUT_MILLIS)`
(30분)로 emitter를 만들긴 하지만([AuctionSseConnectionManager.java:27,63](../backend/src/main/java/com/dbidding/auction/sse/AuctionSseConnectionManager.java:27)),
이건 nginx의 `send_timeout`과 성격이 다르다:

- nginx `send_timeout`은 "60초 동안 클라이언트에 1바이트도 못 내보내면 즉시 끊는다"는
  **능동적 write 타임아웃**이다.
- `SseEmitter(timeout)`은 Spring MVC의 **비동기 요청(AsyncContext) 타임아웃**이다 —
  "emitter가 완료도 타임아웃도 없이 열려 있는 채로 30분이 지나면 정리한다"는 뜻이지,
  "지금 진행 중인 `emitter.send()` 호출이 오래 블로킹되고 있다"를 감지해서 끊는
  메커니즘이 아니다.

`emitter.send()`가 실제로 블로킹되는 순간 그 스레드는 OS의 blocking write()
시스템콜 안에 들어가 있어서, Spring의 async 타임아웃 타이머가 개입할 수 없다
(스레드 자체가 자바 코드로 못 돌아옴). 이 블로킹을 풀 수 있는 건 (1) 클라이언트가
다시 읽어서 write가 자연히 끝나거나, (2) 커널의 TCP 재전송 타임아웃(리눅스 기본
`tcp_retries2`, 보통 15~20분)이 다 돼서 커널이 소켓을 죽은 것으로 판단하고 write에
에러를 리턴하는 것 — 그제서야 `SseEmitterRegistry.send()`의 `catch (IOException)`이
잡혀 `removeAndComplete()`가 호출된다([SseEmitterRegistry.java:128-133](../backend/src/main/java/com/dbidding/sse/SseEmitterRegistry.java:128))
— 이 두 가지뿐이다.

**결론**: "느리지만 완전히 죽지는 않은" 클라이언트를 위한 빠른 컷오프가 백엔드엔
전혀 없다. 완전히 응답 없는 클라이언트조차 커널 타임아웃(십수 분 단위)까지
스레드를 붙잡을 수 있고, 어쩌다 한 번씩만 읽어가는 클라이언트는 이론상 영원히
스레드를 묶어둘 수 있다.

**#614 수정 계획에 대한 함의**: 지금은 nginx가 (의도한 건 아니지만) 60초마다
느린 클라이언트를 정리해주는 안전망 역할을 하고 있다. `proxy_buffering off`만
켜서 이 버그를 고치면 그 안전망이 사라지고, 백엔드가 직접 몇 분~몇십 분 단위로
스레드를 붙잡히는 상황에 노출된다 — threadpool 프로필이면 최악의 경우 스레드풀
전체가 소수의 느린 클라이언트에 다 묶일 수 있다. **#614를 고칠 때 nginx의
`proxy_buffering off`만이 아니라, 백엔드 쪽에도 "일정 시간 진행이 없으면
emitter를 강제로 끊는" 메커니즘(예: emitter별 마지막 성공 send 시각을 추적하는
워치독)이 같이 필요할 수 있다.**

## PoC 2차 시도 검증 — 실제 k6/x/sse 확장으로 재확인 (2026-08-20)

raw 소켓 PoC 다음 단계로, k6의 `k6/x/sse` 확장(`sse.go`)이 실제로 콜백을 오래
블로킹시키면 read loop가 멈추는 구조인지 소스를 직접 확인했다 — `readEvents()`가
별도 고루틴에서 소켓을 읽어 완성된 이벤트를 **unbuffered 채널**(`readChan`)로
넘기고, JS 콜백(`client.on('event', ...)`)은 메인 `select` 루프에서 그 채널을
받아 동기 실행한다. 콜백이 오래 걸리면 메인 루프가 `select`로 못 돌아오니,
리더 고루틴은 다음 이벤트를 채널에 넘기려다 블로킹되고 — bufio 버퍼(기본 4KB)를
소비한 뒤로는 소켓에서 더 안 읽는다. 이론상 raw 소켓 PoC와 같은 메커니즘.

이걸 실제 k6 스크립트(`poc-slow-reader.js`, 백엔드 포트 18080 직결로 nginx 우회)로
검증했다. **1차 시도에서 스크립트 버그 발견**: setup 콜백(`sse.open(url, opts, client
=> {...})`) 끝에 `sleep()`을 넣었더니, `sse.open()`은 setup 콜백이 리턴한 뒤에야
read loop(readEvents 고루틴 + 메인 select)를 시작하는 구조라서 "연결 유지"가
아니라 "read loop 시작 자체를 지연"시키는 결과가 됐다 — normalReader까지
avg 21.8초/med 25.5초로 나와서 처음엔 헷갈렸다. `sleep()`을 콜백 끝이 아니라
**이벤트 핸들러 안**(첫 이벤트를 받은 뒤)으로 옮기고 재시도.

**결과 — 명확하게 재현됨**: slowReader가 첫 이벤트를 받고 콜백 안에서 25초
`sleep()`, 그동안:
- normalReader의 처리량 자체가 눈에 띄게 느려짐(500개당 ~1초였다가 5초로) —
  개별 배달 지연은 낮게 유지됐지만(~4ms) broadcast 전체 처리 속도가 느려진 것.
- slowReader가 깨어난 시점(`woke up` 로그)과 **정확히 일치**해서, normalReader가
  밀려있던 이벤트를 0.1초 사이에 몰아 받으며 지연이 **7,294ms / 4,205ms /
  1,267ms**로 치솟음.
- 전체 `delivery_latency_ms` 분포: avg=497ms, med=4ms, p90=9ms, **p95=6,117ms,
  max=11,928ms** — 대다수는 정상(중앙값 4ms)인데 slowReader stall 구간에 걸린
  꼬리(top 5%)만 초 단위로 치솟는 뚜렷한 이중 분포.

**결론**: k6의 `k6/x/sse` 콜백을 실제로 오래 블로킹시키면, raw 소켓 PoC와 같은
방식으로 진짜 서버 backpressure(broadcast 지연)를 유발하는 게 실측으로
확인됐다. 이제 `pure-fanout.js`에 `SLOW_READER_RATIO` 파라미터를 추가해서
threadpool vs 가상스레드 본실험으로 넘어갈 수 있다.

## `pure-fanout.js`에 `SLOW_READER_RATIO` 파라미터 추가 (2026-08-20)

PoC 검증이 끝났으니 `pure-fanout.js`(`backend/src/test/k6/sse/pure-fanout.js`)에
실제 파라미터를 추가했다.

- **`SLOW_READER_RATIO`**(기본 0): auction 구독자 중 몇 %를 느린 리더로 만들지.
  `__VU % sseSubscribers < round(ratio * sseSubscribers)`로 결정적 배정한다.
- **`SLOW_READER_DELAY_MS`**(기본 2000): 느린 리더가 이벤트를 하나 받을 때마다
  콜백 안에서 sleep할 시간. **매 이벤트마다** 걸어서 "가끔 멈췄다 캐치업"이 아니라
  "계속 못 따라가는" 느린 리더를 모델링한다.
- 지표를 정상/느린 리더로 분리했다(`auction_sse_delivery_latency_normal`,
  `auction_sse_delivery_latency_slow`) — 느린 리더 자신의 지연은 설계상 당연히
  크므로, 전체 지표에 섞으면 "정상 구독자가 느린 리더 때문에 얼마나 피해를 보는지"를
  못 본다. 기존 `auction_sse_delivery_latency`(전체 합산)는 하위호환을 위해 유지.
  느린 리더 VU 수 확인용으로 `auction_sse_slow_reader_vus` Counter도 추가.

**구현 중 발견한 버그**: 처음엔 `__VU % 1000 < round(ratio * 1000)`로 짰는데,
`sseSubscribers`가 1000보다 작으면(예: 500, 스모크 테스트에선 4) 비율이 왜곡된다
— 스모크 테스트(`SSE_SUBSCRIBERS=4, SLOW_READER_RATIO=0.5`)에서 **4명 전원이
슬로우 리더로 잡히는** 것으로 발견해서, 분모를 고정 1000이 아니라 실제
`sseSubscribers`로 고쳤다.

**검증(스모크 테스트, 백엔드 포트 18080 직결)**: `SSE_SUBSCRIBERS=4,
SLOW_READER_RATIO=0.5, SLOW_READER_DELAY_MS=3000`으로 실행한 결과 —
`auction_sse_delivery_latency_normal` avg=1.46ms(정상), `auction_sse_delivery_
latency_slow` avg=25.3s(설계대로 느림)로 깔끔하게 분리됨을 확인. `SLOW_READER_
RATIO=0`(기본값)으로도 재실행해 기존 `auction_sse_delivery_latency` 지표가
그대로 동작하는 것(하위호환)도 확인했다.

**다음**: QPS130/구독자500 기준으로 `SLOW_READER_RATIO`를 1~5%로 스윕하면서
threadpool vs 가상스레드(무제한 + 캡) 본실험을 진행한다. 단, 이번 실험은 nginx의
`proxy_buffering` 버그(#614)가 안 고쳐진 상태에서는 무의미하므로("종합 — 지금
인프라 조건에서 서버 쪽 SSE는 사실상 blocking I/O가 없다" 절 참고), **본실험은
nginx를 우회(백엔드 포트 직결)하거나 #614가 수정된 뒤에** 진행해야 한다.

## 본실험 1차 시도 — 짧은 테스트 시간 탓에 커널 버퍼가 안 찼다 (2026-08-20)

nginx 우회(백엔드 18080 직결) 상태로 QPS130/구독자500 조건에서 threadpool
프로필로 `SLOW_READER_RATIO=0.01`(느린 리더 8명), `0.05`(37명) 각각 45초
main duration으로 돌렸는데, **두 경우 다 정상 리더가 전혀 영향을 안 받았다**
(`auction_sse_delivery_latency_normal` avg 48~49ms, max 374~631ms — delay=0
baseline과 사실상 같은 수준). 느린 리더 자신은 설계대로 심하게 처졌다(avg
45.8~45.9초, max ~91.7초).

**원인 분석**: `SLOW_READER_DELAY_MS=2000`(기본값)으로 매 이벤트마다 2초씩
sleep하면, 느린 리더의 순 수신률은 대략 이벤트 1개/2초(≈0.5개/초)인데 반해
유입률은 QPS130 × (전원이 15개 경매 구독) ≈ 초당 130개다. 이 미스매치로 그
리더의 커널 소켓 버퍼에 초당 약 `(130-0.5)×300B ≈ 39KB`씩 쌓이는 건 맞다.
근데 이 로컬 환경의 TCP 커널 버퍼는 `net.ipv4.tcp_wmem` max(4MB, `docker exec
backend cat /proc/sys/net/ipv4/tcp_wmem` 확인값)까지 자동 확장되므로, 45초
동안 쌓여봐야 `39KB × 45s ≈ 1.75MB`로 **4MB 한도의 절반도 못 채운다.** 즉
`emitter.send()`의 실제 OS write() 호출은 이 45초 내내 커널 버퍼에 여유가
있어서 **한 번도 블로킹되지 않았다** — 느린 리더 자신의 큰 지연은 순전히
"커널 버퍼에 쌓여서 클라이언트가 늦게 읽어가는" 현상이지, 백엔드 쪽
backpressure(브로드캐스트 스레드가 블로킹되는 것)는 전혀 발생하지 않은
것이다.

**커널 버퍼가 다 차는 데 필요한 시간**: `4MB ÷ 39KB/s ≈ 107초`. 즉 순유입률이
이 정도인 조건에서는 **테스트의 SSE 활성 구간(warmup+main+tail)이 최소
110~120초는 넘어야** 실제 블로킹이 발생할 여지가 생긴다 — 45초는 애초에
어림도 없었다. 1차 raw 소켓/k6 PoC(노트 앞부분)가 재현에 성공했던 건 정확히
이 이유 때문이었다 — 그땐 stall을 35~65초 동안 걸고 QPS를 최대한 올려서
(1,088/s) 순유입량 자체를 훨씬 크게 만들어 훨씬 빨리 4MB를 채웠다.

**수정 방향**: 본실험은 (1) `DURATION`을 충분히 늘리거나(예: 150s, SSE 활성
구간 총 180초+로 107초 문턱을 여유 있게 넘김), (2) `SLOW_READER_DELAY_MS`를
테스트 전체 SSE 지속시간보다 훨씬 크게 잡아서(예: 600000ms=10분) 느린 리더가
사실상 "첫 이벤트 이후 다시는 안 읽는" 완전히 멈춘 클라이언트가 되게 해서
드레인율을 0에 가깝게 만드는 것, 이 두 가지를 같이 적용해서 재시도한다.

## 본실험 2차(수정판) 결과 — threadpool은 견고, 가상스레드(캡 200)는 완전히 무너짐 (2026-08-20)

`DURATION=150s`(SSE 활성 구간 총 ~220초, 107초 문턱을 여유 있게 넘김),
`SLOW_READER_DELAY_MS=600000`(10분 — 테스트 전체보다 훨씬 김)으로 재시도했다.
**추가로 발견한 사실**: `SseEmitterRegistry.register()`가 연결 직후 보내는
`connected` 핸드셰이크 이벤트([SseEmitterRegistry.java:86](../backend/src/main/java/com/dbidding/sse/SseEmitterRegistry.java:86))도
k6/x/sse의 `'event'` 콜백을 그대로 타는데, JSON이 아니라서 파싱은 실패하지만
그 직후 `sleep(600)`이 그대로 실행된다 — 즉 느린 리더는 **첫 실제 입찰
이벤트를 받기도 전에, 연결하자마자 오는 `connected` 이벤트에서 걸려서 테스트
시작부터 끝까지 완전히 멈춘 클라이언트**가 된다(의도한 것보다 더 극단적이지만,
이번 목적엔 오히려 더 깨끗한 조건). `auction_sse_delivery_latency_slow`가
매 실행마다 `None`으로 나오는 게 그 증거다.

nginx 우회(백엔드 18080 직결), QPS130/구독자500 조건에서 threadpool vs
가상스레드(캡 200, #585 admission control 적용됨)를 `SLOW_READER_RATIO`
1%/5%로 비교했다:

| profile | ratio | 느린 리더 수 | `auction_sse_delivery_latency_normal` avg/med/p95/p99/max | `me_sse_delivery_latency` avg |
|---|---|---|---|---|
| threadpool | 1% | 6 | 50.4 / 40 / 115 / 179 / **1,030ms** | 45,682ms |
| threadpool | 5% | 37 | 37.3 / 30 / 97 / 127 / **385ms** | 33,385ms |
| 가상스레드(캡200) | 1% | 6 | 31,539 / 20,430 / 102,961 / 134,327 / **179,879ms** | 47,223ms |
| 가상스레드(캡200) | 5% | 38 | 32,850 / 22,168 / 105,599 / 134,314 / **167,152ms** | 9,173ms |

**핵심 발견 — 완전히 뒤집힌 결과**: 이 실험을 시작한 계기였던 원래 우려
("threadpool은 단일 브로드캐스트 스레드가 느린 리더 1명에 멈추면 나머지
499명 전부가 밀린다, 가상스레드는 느린 클라이언트 1개가 자기 가상스레드만
파킹시켜서 원래 목적대로 유리할 것")가 **정반대로 나왔다**:

- **threadpool은 완전히 멈춘 리더가 1~5%(6~38명) 있어도 정상 리더는 거의
  영향이 없다** — avg 37~50ms, max 385~1,030ms로 delay=0 baseline과 큰 차이
  없이 견고하다. 비율을 5%로 올려도 더 나빠지지 않았다(오히려 살짝 나음,
  노이즈 범위).
- **가상스레드(캡 200)는 단 1%(6명)만 있어도 정상 리더까지 avg 31.5초로
  완전히 무너진다.** 5%로 올려도 별로 더 나빠지지 않는다(32.8초) — 이미
  1%에서 포화 상태라는 뜻.

**왜 이렇게 뒤집혔는가(가설)**: threadpool은 `auctionSseBroadcastTaskExecutor`
(core4/max8)의 각 스레드가 이벤트 1건당 500개 emitter를 순차로 순회하며
send한다. 느린 emitter에 도달하면 그 write가 커널 버퍼가 찬 뒤로는 블로킹될
수 있지만, **다른 이벤트들은 풀의 다른(아직 안 막힌) 스레드가 계속 처리**하고,
큐가 꽉 차면 `CountingCallerRunsPolicy`가 발동해 어떻게든 처리는 계속된다 —
소수의 스레드가 소수의 느린 emitter에 발이 묶여도 전체 처리량에 주는 타격이
제한적이다. 반면 가상스레드(#585 tryAcquire+discard 캡 적용)는 이벤트마다
emitter별로 새 가상스레드를 만들고 세마포어(캡 200)를 놓고 경쟁하는데, **느린
emitter에 걸린 가상스레드가 세마포어 permit을 붙잡은 채 영원히 안 풀어주면**,
캡이 200이라는 전역 상한을 몇 개의 "죽은" emitter가 야금야금 갉아먹으면서
전체 시스템의 유효 동시성이 계속 줄어드는 구조로 보인다 — 캡이 "느린
emitter"와 "정상 emitter"를 구분 못 하고 공유 자원이기 때문에, 느린 emitter
때문에 잠긴 permit 수만큼 정상 emitter들도 permit 경쟁에서 밀린다.
threadpool의 CallerRunsPolicy 같은 "일단 어떻게든 처리" 메커니즘이 가상스레드
쪽엔 없다(캡이 꽉 차면 그냥 discard).

**결론 — 이번 세션 전체 실험 A의 최종 정리**: "가상스레드가 blocking I/O
상황에서 threadpool보다 유리하다"는 원래 가설은, **모든 리더에게 균일한
지연이 걸리는 비현실적 모델(1차 실험 A)에서는 확인됐지만, 실제로 있을 법한
"소수의 리더만 완전히 멈추는" 현실적 모델(이번 재설계)에서는 정반대로
뒤집힌다.** 소수의 완전히 죽은 클라이언트로부터 시스템 전체를 보호하는
능력은 threadpool의 bounded pool + CallerRunsPolicy 쪽이 가상스레드의
전역 세마포어 캡보다 훨씬 낫다 — **이 프로젝트의 SSE 워크로드에는 가상스레드
채택이 이 시나리오 기준으로는 불리하다**는 것이 이번 실험의 최종 결론이다.
단, 이 결론은 여전히 "동시성 캡(#585)이 느린 emitter를 구분 없이 취급한다"는
현재 구현 한계에 기반한 것이므로, emitter별로 "이 정도 지연되면 그냥 이
emitter만 강제로 끊는다"는 개별 컷오프 메커니즘(이 노트의 "nginx 60초
send_timeout에 대응하는 백엔드 쪽 안전망은 없다" 절 참고)을 가상스레드
쪽에 추가하면 결과가 달라질 수 있다 — 그건 별도 후속 실험 대상이다.

## 캡 없는(무제한) 가상스레드 — 40초 만에 메모리 99.6%, 크래시 직전 (2026-08-20)

캡(200)을 뺀 순수 무제한 가상스레드(`AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY=0`)로
같은 조건(nginx 우회, QPS130/구독자500, `SLOW_READER_RATIO=0.01`)을 재시도했다.
**150초 본실험 전에 안전 확인용으로 40초 단축 시험부터 돌렸다** — #569 실험 B에서
캡 없는 가상스레드가 느린 클라이언트 때문에 실제 힙 OOM으로 크래시난 전례가
있었기 때문.

**결과 — 40초 만에 이미 위험 신호**:
- `docker stats` 기준 컨테이너 메모리가 **899.6MiB/903MiB(99.6%)**까지 참 —
  cap=200 결과(90.2~96.3%, "구독자 1,000/2,000명" 절 참고)보다도 빠르게, 훨씬
  적은 구독자 수(500명)·훨씬 짧은 시간(40초)에 거의 다 찼다.
- `auction_sse_delivery_latency_normal` avg=**4,389ms**/med=4,137ms/
  max=20,113ms — cap=200(150초, avg 31,539ms)보다는 낮지만, 이건 **테스트
  시간이 150초의 1/4도 안 됐기 때문**이지 상황이 더 나은 게 아니다. 메모리가
  이미 99.6%로 한계에 다다른 채로 40초 만에 끊긴 것이므로, 150초까지 이어가면
  cap=200보다 더 나빠지거나 실제로 크래시했을 가능성이 높다.
- 테스트 종료(연결이 다 닫힌) 직후 메모리가 845MiB로 소폭 내려감 — 완전히
  죽지는 않았지만 GC 여유가 거의 없는 상태였다.
- `fanout_publish_success` 100%, `auction_sse_connected` 100% — 이번엔
  용케 크래시 전에 테스트가 끝나서 겉보기엔 "성공"으로 보이지만, 시간을
  늘렸다면 #569 실험 B의 2,000명 시나리오처럼 `OutOfMemoryError`로 이어질
  개연성이 커서 **150초 본실험은 하지 않기로 결정**했다(사용자 확인 후 중단).

**결론**: 캡 없는 가상스레드는 캡 200보다도 더 나쁘다 — 정상 리더 지연이
비슷하거나 더 나쁠 뿐 아니라, **서버 자체가 크래시할 위험까지 추가된다.**
이는 "본실험 2차" 절의 결론(가상스레드가 소수의 완전히 죽은 리더에 훨씬
취약하다)을 한 번 더 강화한다 — 캡을 걸면 그나마 살아는 있으면서 느려지는
데 그치지만(discard 없이 정상 리더까지 다 같이 지연), 캡을 빼면 살아있는
채로도 안 끝나고 크래시 직전까지 간다. **이 시나리오(소수의 영구 정체
클라이언트) 기준으로는 가상스레드를 캡 있이 쓰든 없이 쓰든 threadpool보다
확실히 못하다**는 게 최종 결론이다.

## 종합 — 지금 인프라 조건에서 서버 쪽 SSE는 사실상 blocking I/O가 없다 (2026-08-20)

원인 후보 3(nginx location 매칭 버그)을 운영에서도 확인한 뒤 나온 질문: "지금
인프라 조건이면 아예 서버 쪽 SSE는 blocking I/O가 없는 거 아니냐?" —
**"전혀 없다"는 아니고 "현실적으로 거의 발생 안 한다"가 정확한 결론이다.**

**왜 거의 안 생기는가**:
1. nginx가 버퍼링 켜진 채로 SSE를 받으면, 백엔드→nginx 소켓(로컬호스트)은 nginx가
   쉬지 않고 읽어가니까 거의 항상 즉시 끝난다. 백엔드는 실제 브라우저 클라이언트가
   얼마나 느린지 전혀 못 느낀다.
2. nginx 쪽 버퍼(메모리 몇 KB + 디스크 임시파일 최대 `proxy_max_temp_file_size`
   기본 1GB)가 실제로 다 차야 nginx가 백엔드로부터 읽는 걸 멈추고, 그제서야
   백엔드→nginx 소켓이 차서 `emitter.send()`가 블로킹된다. 이벤트 300바이트짜리를
   계속 보낸다 쳐도 1GB를 채우려면 클라이언트가 몇 시간 단위로 계속 정체돼야 한다.
3. 근데 그 전에 nginx의 `send_timeout`(이 설정엔 명시 안 돼있어서 기본값 60초)이
   먼저 발동한다 — 클라이언트에 60초 동안 단 1바이트도 못 내보내면 nginx가 그
   연결 자체를 끊어버린다. 즉 "완전히 멈춘 클라이언트"는 1GB는커녕 60초 만에
   연결이 끊기고, "느리지만 계속 조금씩은 읽어가는 클라이언트"만 1GB까지 버틸 수
   있는데 이건 현실 트래픽에서 거의 안 나오는 패턴이다.

**결론이 실험 전체 해석에 미치는 영향**:
- 이 nginx 버그가 안 고쳐진 지금 상태에서는, #569 최초 비교(delay=0, loopback
  조건, threadpool이 34~44배 이김)가 오히려 **지금 운영 환경의 실제 동작에 훨씬
  가깝다** — 실제로도 emitter.send()는 사실상 항상 non-blocking이기 때문이다.
- #579(인위적 지연)와 이번 재설계(느린 리더)에서 재현하려던 "느린 클라이언트 →
  진짜 blocking I/O → 가상스레드가 유리해짐" 시나리오는, **지금 nginx 설정
  하에서는 애초에 발생할 수 없는 조건을 재현하려던 것**이었다.
- 역설적으로, 가상스레드의 원래 장점(blocking I/O를 공짜로 기다리기)이 의미
  있으려면 먼저 nginx 버그를 고쳐서 버퍼링을 꺼야 한다 — 근데 그 순간 threadpool은
  "느린 클라이언트 1명이 전체를 막는" 원래 취약점에 그대로 노출된다. 즉 **이
  nginx 버그를 고치는 게 "가상스레드 채택 논리" 전체의 성립 전제조건**이라는 뜻이
  된다. 버그를 고치지 않은 채 threadpool vs 가상스레드를 논하는 건 무의미하다.

**다음 진행 방향**: nginx 버그는 이번 실험 범위 밖의 별도 운영 이슈이므로
[#614](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/614)로
분리해서 만들어뒀고, 원래 하려던 "느린 리더 PoC"는 nginx를 우회(백엔드 포트에
직접 연결)해서 계속 진행한다 — nginx가 고쳐지기 전에도 "백엔드가 진짜로 느린
리더를 만나면 blocking되는지" 자체는 확인 가능하고, 그 결과가 nginx 버그 수정
여부와 무관하게 여전히 유효한 실험 설계 검증이기 때문이다.

## PoC 2차 시도 — nginx 우회 후 재현 성공 (2026-08-20)

1차 PoC 스크립트(`slow_reader.py`/`pump_events.py`)를 백엔드 컨테이너 포트
(`18080`, TLS 없이)에 직접 붙게 고쳐서 nginx(#614 버그로 backpressure를 흡수함)를
완전히 우회한 뒤 재시도했다. `pump_events.py`도 커넥션 재사용(keep-alive)으로
고쳐진 버전을 그대로 사용해 이전의 "테스트 하네스 자체가 서버를 과부하시키는"
confound(원인 후보 1)도 같이 제거된 상태였다.

**조건**: reader A(정상 리더) + reader B(헤더만 읽고 40초간 전혀 `recv()` 안 함,
`SO_RCVBUF=2048`) 둘 다 같은 경매(`auctionId=3001361`) 구독. pump가 35초간
커넥션 20개로 `random-bid-event`를 최대한 빠르게 발행(1,088/s, 총 38,121건).

**결과 — 이번엔 명확하게 재현됨**:
- B가 멈춰있던 구간(pump 시작~35초)에는 reader A가 정상적으로 즉시 배달받음
  (`published_at`→수신 지연 avg ~1ms).
- **B가 `recv()`를 다시 시작한 시점(stall 종료, t+35s)과 정확히 일치해서**, 아무
  상관 없어야 할 정상 리더 A의 배달 지연이 **avg 1,527.5ms / max 14,646.5ms**로
  급등했다 — B가 막혀 있는 동안 A로 가는 이벤트도 broadcast 큐 뒤에 같이 밀려
  있다가, B가 뚫리는 순간에야 한꺼번에 몰려서 배달된 것으로 보인다.
- B의 드레인 자체도 한 번에 안 끝나고 **28초에 걸쳐 서서히 12.98MB**가 나왔다 —
  이미 커널 로컬 버퍼에 있던 걸 한 번에 읽은 게 아니라, 서버가 그 28초 동안
  계속 조금씩 밀어내고 있었다는 뜻이다(1차 PoC에서 nginx를 거쳤을 때 9.97MB가
  한 번에 즉시 drain된 것과 대비됨).

**결론**: nginx를 우회하니 "느린 리더 하나가 같은 경매를 구독하는 다른 정상
구독자까지 지연시킨다"는 원래 가설이 명확하게 재현됐다 — threadpool 프로필의
`SseEmitterRegistry.send()`가 emitter별 `ReentrantLock`으로 직렬화돼 있고,
느린 emitter에 대한 `emitter.send()`가 실제로 블로킹되면 그 이벤트를 처리하던
스레드(및 executor 큐 뒤에 쌓인 다른 이벤트들)가 다 같이 지연된다는 메커니즘이
실측으로 확인된 것. **PoC의 원래 목적("k6에서 일부 VU만 느린 리더로 만드는
방식이 실제로 서버 backpressure를 유발하는지")이 검증 완료됐으므로**, 이제
`pure-fanout.js`에 `SLOW_READER_RATIO`(1~5%) 파라미터를 추가해서 threadpool
vs 가상스레드 본실험으로 넘어갈 수 있다. 단, k6의 `k6/x/sse` 확장이 이
PoC와 같은 방식(콜백 안에서 오래 블로킹 → 다음 read 안 함)으로 동작하는지는
별도 확인이 필요하다(이 PoC는 raw 소켓으로 직접 확인한 것이라 k6 확장의 실제
구현과는 다를 수 있음).

## #615 — emitter별 send 직렬화를 tryLock+discard로 수정 (2026-08-20)

본실험(threadpool vs 가상스레드 캡200)에서 가상스레드가 죽은 리더 1~5%만
있어도 완전히 무너지는 걸 확인한 뒤([이슈 #615](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/615)),
같은 브랜치(`feature/579-sse-send-artificial-delay`)에서 로컬로 원인을
고쳐봤다. dev 브랜치는 이번 실험용 test-only API가 다 제거된 상태라, 별도
브랜치를 새로 파지 않고 이 브랜치에서 이어서 작업했다.

### 원인 재확인

`VirtualThreadSseTaskExecutor.execute()`는 세마포어를 `tryAcquire()`(논블로킹)로
확인해서 캡 초과 시 discard하지만(#585), 이건 "동시 실행 중" 개수만 제한하고
"대기 중" 개수는 제한 안 한다. `SseEmitterRegistry.send()`는 emitter별
`ReentrantLock.lock()`(무제한 대기)으로 직렬화하는데, 죽은 emitter 하나에
첫 가상스레드가 `emitter.send()` 안에서 블로킹되면, 그 뒤로 같은 emitter를
향한 모든 이벤트가 새 가상스레드를 만들어 그 락을 무제한 대기하면서 **permit을
쥔 채로 안 놓는다** — 죽은 emitter 1개가 QPS×시간만큼의 기회로 캡 전체를
잠식할 수 있다. 부하가 아니라 설계 결함(#585가 세마포어 레벨에서 고친 패턴이
그 아래 `ReentrantLock` 레벨엔 안 고쳐진 것)이라는 게 핵심.

### 1차 시도 — CAS + 최신값 코알레싱: 실패, 새 버그 발견

`sending`(AtomicBoolean) + `pending`(AtomicReference) 조합으로 "담당자만
실제로 보내고, 나머지는 최신값 슬롯에 덮어쓰고 즉시 리턴"하는 방식을 구현했다.
그런데 실측하니 **수정 후가 수정 전보다 오히려 더 나빴다**(같은 조건에서 avg
18,559ms vs 10,383ms). 원인: `EmitterSendState`가 **emitter당 1개**인데,
emitter 하나(구독자 1명)가 **경매 15개를 동시에 구독**한다
([AuctionSseController]). 경매 A와 경매 B의 브로드캐스트가 거의 동시에 같은
emitter로 보내려 하면, 슬롯 하나를 공유하는 이 설계에서 **서로 전혀 관련
없는 두 경매의 이벤트가 서로를 덮어써버린다.** "같은 경매의 낡은 값을 최신값이
대체해도 된다"는 전제가, 실제로는 "15개 경매의 이벤트가 서로를 덮어쓴다"로
잘못 적용된 것 — 건강한 emitter까지 불필요하게 느려진 이유였다.

두 가지 대안을 검토했다: (1) coalescing 키를 (emitter, auctionId)로 세분화 —
`SseEmitterRegistry<K>`가 3개 도메인(auction/notification/wallet)이 공유하는
범용 클래스라, `send()` 시그니처·모든 호출부·내부 자료구조·기존 테스트 13개를
다 고쳐야 하는 침습적 변경. (2) 그냥 tryLock+discard로 단순화 — 공유 슬롯
자체가 없어져서 교차 오염이 구조적으로 불가능. (2)를 선택했다.

### 2차 시도 — tryLock + discard

`ReentrantLock.lock()`(무제한 대기)을 `tryLock()`(논블로킹)으로 바꿔서, 이미
다른 스레드가 이 emitter에 보내는 중이면 이번 이벤트는 블로킹 없이 그냥
버린다. 공유 상태가 전혀 없어서(#615 1차 시도의 슬롯 같은 게 없음) 서로 다른
경매 이벤트가 섞일 일이 없다. `SseEmitterRegistryTest` 13개 전부 통과.

### 검증 함정 — 패딩으로 버퍼를 빨리 채우려다 새 confound(대역폭 포화)를 만듦

QPS130/구독자500(원래 본실험 조건)에서 재검증했더니 또 실패했다(수정본이
여전히 수정 전보다 나쁨, avg 19,974ms vs 10,383ms). 원인 분석 중 사용자가
제안한 방법(SSE 이벤트에 주석 라인으로 더미 바이트를 채워 메시지를 키우면
죽은 emitter 커널 버퍼가 차는 시간을 단축할 수 있다)을 `AuctionSseConnectionManager`에
`SSE_SEND_PADDING_BYTES` 손잡이로 구현했는데, 이걸 QPS130/구독자500과
같이 쓰니 **구독자500×8KB×QPS130 ≈ 초당 520MB**라는 죽은 리더 문제와
전혀 무관한 새로운 대역폭 병목을 만들어버렸다 — 지금까지 이 조사 내내
반복된 패턴(진짜 신호를 confound가 가림)이 여기서도 재발한 것.

**교훈**: 버퍼 채우는 속도(=죽은 emitter 1개 기준 QPS×페이로드크기)와 전체
대역폭(=구독자수×QPS×페이로드크기)은 서로 다른 축이다. 버퍼를 빨리 채우고
싶다고 QPS나 페이로드를 무작정 키우면, 구독자 수가 많을 때 전체 대역폭이
같이 폭발한다. **깨끗하게 격리하려면**: QPS는 낮게 고정(CPU 여유 확보),
구독자 수는 캡 크기와 비슷하거나 살짝 웃도는 수준으로만(캡 경합이 실제로
생기게), 페이로드 크기만 조절해서 버퍼 채우는 시간을 원하는 수준(수십 초)으로
맞춘다 — 대역폭 = 구독자수 × (버퍼크기 ÷ 목표시간)으로 계산해서 감당 가능한
수준인지 미리 확인.

### 최종 검증 — 깨끗한 조건에서 확인, 2배 스케일까지 재현

`SSE_SUBSCRIBERS`를 캡 근처(250, 캡200)로 낮추고 `SSE_SEND_PADDING_BYTES=4096`
(대역폭 ≈20MB/s)로 재시도한 결과:

| profile | 구독자/캡 | avg | med | p95 | p99 | max |
|---|---|---|---|---|---|---|
| threadpool | 250/— | 44.3ms | 9ms | 289ms | 686ms | 794ms |
| 가상스레드(tryLock+discard) | 250/200 | 8.8ms | 6ms | 21ms | 51ms | 286ms |

**전 구간에서 3~14배 개선** — 드디어 fix가 제대로 작동함을 확인. 구독자
수/캡을 2배(500/400)로 늘려 재현성도 확인했다:

| profile | 구독자/캡 | avg | med | p95 | p99 | max |
|---|---|---|---|---|---|---|
| threadpool | 500/— | 328ms | 15ms | 68ms | 161ms | **120,148ms** |
| 가상스레드(tryLock+discard) | 500/400 | 21.9ms | 15ms | 69ms | 128ms | **346ms** |

**핵심 발견**: threadpool은 두 스케일 다 median/p95는 멀쩡한데 **max가
수십 초~2분까지 치솟는 꼬리가 일관되게 재현**된다 — 구독자가 늘면 그 꼬리
사건 빈도는 줄지만(p99: 686ms→161ms) 크기는 여전히 크다. 이건 이전에
추론했던 메커니즘과 정확히 일치한다: `broadcast()`가 `@Async("auctionSseBroadcastTaskExecutor")`
(core4/max8, 큐 2000, `CountingCallerRunsPolicy`)라서, 죽은 emitter들이
브로드캐스트 풀 스레드를 하나씩 소진 → 큐 2000개가 다 참 → 결국 Redis
pub/sub 리스너 스레드 자신이 `CallerRunsPolicy`로 전체 순회를 떠맡다가
그마저 죽은 emitter를 만나 막히는 체인이, 아주 가끔이지만 실제로 발동한다.
가상스레드(tryLock+discard)는 죽은 emitter 1개당 permit 1개만 묶이고
나머지는 즉시 버려지므로 이런 누적/전파 자체가 구조적으로 불가능하다.

### 최종 결론(수정)

"가상스레드가 blocking I/O 상황에서 threadpool보다 유리하다"는 실험 A의
원래 가설은, **제대로 된 admission control(캡 + tryLock+discard)을 갖추면
실제로 맞는 얘기였다.** 처음 이 재설계를 시작했을 때 나온 "가상스레드가
소수의 완전히 죽은 리더에 훨씬 취약하다"는 결론은 뒤집혔다 — 그 취약성은
가상스레드 자체의 한계가 아니라 `SseEmitterRegistry`의 `ReentrantLock.lock()`
(무제한 대기)이라는 구체적인 구현 결함 때문이었고, `tryLock()`+discard로
고치자 threadpool보다 확실히, 그리고 두 스케일에서 일관되게 나은 결과가
나왔다. threadpool은 median 기준으로는 여전히 나쁘지 않지만, 소수의 요청이
수십 초~수 분씩 걸리는 **꼬리 위험**을 구조적으로 안고 있다(브로드캐스트
풀 소진 → 큐 포화 → 호출자 스레드까지 전파되는 체인).

**남은 과제**: `SseEmitterRegistryTest`에 이번에 발견한 회귀(emitter 1개가
여러 키를 구독할 때 서로 다른 키의 이벤트가 섞이면 안 된다)를 막는 테스트
추가, `AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY` 운영값 재검토(#615 수정으로
가상스레드가 확실히 유리해졌으니 프로필 전환 논의 재개 가능), notification/wallet
쪽 `SseEmitterRegistry.send()`도 같은 수정의 혜택을 받는지 별도 확인(공유
클래스라 이미 적용돼 있지만 그쪽 특유의 워크로드로 실측은 안 해봄).

## 추가 확인 — 실제 손실률과 CPU 사용률 (2026-08-20)

위 검증에서 "배달된 이벤트의 지연시간"만 봤지 "발행된 이벤트 중 몇 %가 실제로
도착했는지"는 안 봤다는 지적을 받고 추가로 확인했다.

### 손실률 — threadpool이 오히려 훨씬 심하게 유실시킨다

`auction_sse_events`(정상 리더가 실제로 받은 이벤트 총합)를 이론상 최댓값
(발행된 이벤트 수 × 정상 리더 수, 모두 15개 경매를 구독하므로 이벤트마다
정상 리더 전원에게 나가는 게 정상)과 비교했다:

| profile | 구독자(정상/죽음) | 이론상 최대 | 실제 전달 | **손실률** |
|---|---|---|---|---|
| 가상스레드(tryLock+discard) | 250 (235/15) | ≈458,485 | 457,587 | **≈0.2%** |
| 가상스레드(tryLock+discard) | 500 (464/36) | ≈928,000 | 926,440 | **≈0.17%** |
| threadpool | 250 (235/15) | ≈458,485 | 68,451 | **≈85%** |

threadpool은 discard 메커니즘 자체가 없어서 이론상 손실률 0%여야 하는데,
실측은 정반대로 훨씬 심했다. 원인: `broadcast()`가 `@Async`로 스레드풀
(core4)에 이벤트 1건 = 순회 1번을 통째로 맡기는데, 이 순회는 구독자
전원(죽은 리더 포함)을 **하나의 for 루프 안에서 순차로** 처리한다. 죽은
emitter를 만나면 예외(Tomcat 커넥터 타임아웃, ~60초)가 나야 다음으로
넘어간다 — 죽은 리더가 15명이면 초반 몇 개 이벤트의 순회가 이들을 순서대로
만나면서 **최대 15×60초**까지 잡아먹을 수 있고, 그동안 core=4 스레드가
전부 여기 묶여서 뒤에 발행된 이벤트들은 큐(2000)에 쌓인 채 시작도 못 하고
90초짜리 테스트가 끝나버린다(한 번 타임아웃난 죽은 emitter는 레지스트리
에서 제거되니 시간이 충분하면 정리되지만, 90초로는 어림도 없었음).

**즉 "median은 멀쩡한데 꼬리(max)만 가끔 폭발한다"는 이전 해석은 절반만
맞았다** — median이 멀쩡했던 건 "배달된 것들 중에서"의 median이었고,
정작 대부분의 이벤트는 애초에 배달 자체가 안 됐다. 실제 체감 손실은
지연시간 percentile만 봐서는 전혀 안 드러나는 수준으로 훨씬 심각하다.

### CPU 사용률 — 전 구간 포화 없음(confound 아님을 재확인)

Prometheus(cAdvisor)에서 각 테스트 실행 시점의 backend 컨테이너 CPU를
사후 조회했다(컨테이너가 프로필 전환마다 재생성돼 id가 바뀌므로, 각
테스트의 대략적인 실행 시간대에 활성이었던 컨테이너 id를 `container_spec_
cpu_quota==200000`으로 특정한 뒤 조회 — #569 문서에서도 쓴 방법):

| profile | 구독자 | CPU peak | CPU avg |
|---|---|---|---|
| threadpool | 250 | 1.528/2 (76%) | 1.259/2 (63%) |
| 가상스레드(tryLock+discard) | 250 | 1.497/2 (75%) | 1.095/2 (55%) |
| threadpool | 500 | 1.483/2 (74%) | 1.172/2 (59%) |
| 가상스레드(tryLock+discard) | 500 | 1.783/2 (89%) | 0.791/2 (40%) |

전 구간 2코어 완전 포화(2.0)는 없었다 — 이 스케일 조건(QPS20, 구독자
250~500, 패딩4KB)이 CPU confound 없이 깨끗하게 두 프로필을 비교할 수
있는 조건이었다는 걸 재확인했다. 즉 앞서 관측한 지연시간·손실률 차이는
CPU 자원 경합이 아니라 순수하게 아키텍처(emitter별 순차 순회 vs
tryLock+discard) 차이에서 비롯된 것으로 봐도 된다.

> 이 문서는 claude의 도움을 받아 작성되었습니다.
