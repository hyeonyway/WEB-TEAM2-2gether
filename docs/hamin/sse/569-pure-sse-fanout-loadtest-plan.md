# 순수 SSE fan-out 부하테스트: notification/wallet Redis publish 테스트 엔드포인트 추가

이슈: #569

## 배경

threadpool(`up-all-redis-sse.sh`) vs virtual thread(`up-all-redis-sse-virtual.sh`) 간 SSE fan-out
비용만 비교하는 부하테스트를 설계함(세션 내 논의). 실제 입찰 처리 없이 Redis publish로 이벤트를
직접 주입해서 auction/notification/wallet 3채널의 fan-out만 측정한다.

- auction: `/api/auctions/stream/test-events/random-bid`가 이미 있지만
  `AuctionSseConnectionManager.broadcast()`를 직접 호출해 실제 Redis publish 경로를 건너뜀.
- notification/wallet: 이런 테스트 전용 발행 엔드포인트가 아예 없음.

## 트래픽 모델 (확정된 설계)

- 경매 15개, 각 경매 담당 입찰자 계정 10명(총 150명) — 실제 입찰 없이 `/api/me/stream`
  (notification+wallet 통합 커넥션, #557) 연결 대상으로만 씀.
- auction 구독자 500명, **전원이 15개 경매를 전부 구독**(1커넥션 = auctionIds 15개).
- 이벤트 QPS 130 고정(계단 없음), 경매당 균등 확률로 선택(~8.7/s/auction).
- 이벤트 1건 = 조합 엔드포인트 1회 호출 → auction broadcast(500명 전원) + notification/wallet
  push(그 경매 담당 10명 중 2명: 직전 최고입찰자 역할 + 신규입찰자 역할)를 모두 실제 Redis
  publish 경로로 동시 발행. 실제 입찰 1건의 효과를 재현.

## 변경 사항

1. **`AuctionSseTestAuctionReader`**: 특정 auctionId로 스냅샷을 조회하는 `findAuction(Integer
   auctionId)` 추가(기존 `findRandomActiveAuction()`은 유지, 다른 곳에서 계속 씀).
2. **`AuctionSseTestBidApplicationService`**: `connectionManager.broadcast()` 직접 호출 대신
   `AuctionStreamPublisher.publish()`(Redis 경로)를 타도록 변경. 클래스/메서드를 `public`으로 올려
   패키지 밖(조합 서비스)에서 재사용 가능하게 함. 특정 auctionId를 받는 `publishBidFor(Integer
   auctionId)` 메서드 추가, 기존 `publishRandomBid()`과 payload 조립 로직 공유.
3. **`NotificationSseTestPushService`**(신규, `notification.sse` 패키지, `@Profile("test")`,
   `public`): 실제 `NotificationPushPublisher` 빈을 호출해 가짜 `NotificationResponse`를 Redis로
   발행.
4. **`WalletSseTestPushService`**(신규, `wallet.sse` 패키지, `@Profile("test")`, `public`): 실제
   `WalletSsePublisher` 빈을 호출해 가짜 `WalletBalanceChangedEvent`를 Redis로 발행.
5. **`SseFanoutTestEventController`/`SseFanoutTestEventService`**(신규, `sse.test` 패키지,
   `@Profile("test")`): 위 세 개를 한 번에 묶어 발행하는 `POST /api/test/sse-fanout/random-bid-event`
   엔드포인트. auctionId + 대상 유저 2명(outbid/새 입찰자)을 요청 파라미터로 받음 — 어떤 유저가 어떤
   경매 담당인지는 k6가 알고 있으므로 백엔드가 별도로 계산하지 않음.
6. **신규 k6 스크립트** `backend/src/test/k6/sse/pure-fanout.js`: `hot-auction-pattern.js`의
   로그인/세션 헬퍼를 재사용. `auctionSse`(500 VU, 15개 auctionIds 전부 구독) + `meSse`(150 VU) +
   `eventPublisher`(constant-arrival-rate 130/s, 조합 엔드포인트 호출) 시나리오. `handleSummary()`에
   `generatedAt`/`state`/`testConfig`를 채워 기존 `run-k6.sh` 후처리(runInfo/Prometheus
   serverMetrics 병합)에 그대로 올라타게 함 — 별도 병합 스크립트 불필요.

## 범위 밖

- 기존 `/api/auctions/stream/test-events/random-bid`(랜덤 활성 경매 대상)의 동작 자체는 유지 —
  publish 경로만 Redis로 바뀜.
- `WalletSseExecutorConfig`(항상 threadpool 고정, virtual thread 토글 없음) 자체는 안 건드림 —
  이번 비교에서 wallet 실제 send는 baseline으로 남는다는 점만 인지하고 진행.
- `run-k6.sh`/Prometheus 쿼리 스크립트 자체는 수정하지 않음(기존 컨벤션만 따르면 그대로 동작 확인됨).
- 이번 브랜치에서는 `SessionCsrfFilter`/`seed/seed-load-test-auctions.js` 자체는 안 건드림(둘 다
  별도 이슈 대상 — 아래 "설계와 달라진 부분" 참고).

## 설계와 달라진 부분 (실제 실행하며 드러남)

로컬 스택(`~/dbidding-loadtest-local`)에서 실행해보며 원래 설계에 없던 문제 세 가지를 발견해서
`pure-fanout.js`만 수정해 대응했다(백엔드 프로덕션 코드는 안 건드림):

1. **`SessionCsrfFilter`가 익명 POST를 전부 막음** — `/api/auth/login`, `/api/auth/signup`을 뺀
   모든 POST/PUT/PATCH/DELETE에 걸려서, 세션 쿠키 없이 호출하던 조합 발행 엔드포인트가 403으로
   100% 막혔다. `pure-fanout.js`가 이미 로그인해둔 bidder 세션의 쿠키+CSRF 토큰을 실어 보내도록
   수정. (별도 이슈 [#575](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/575)로
   연결됨 — 이건 CSRF 필터 자체가 아니라 이 발견 과정에서 나온 가상스레드 cap 스윕에서 확인된
   `broadcast()` 블로킹 문제.)
2. **`seed/seed-load-test-auctions.js`가 세션 인증 전환(#469) 전 JWT 방식 그대로 남아있어서** 같은
   CSRF 필터에 막혀 사용 불가 — 이 스크립트는 안 고치고, `pure-fanout.js`의 `setup()`이 자체적으로
   (이미 로그인해둔 bidder 세션으로) 경매 15개를 시드하도록 우회.
3. **본측정 전 웜업 단계 없음** — `auction-bid.js` 등 다른 스크립트처럼 낮은 rate로 미리 돌려
   JIT/커넥션풀을 데우는 단계가 빠져있어서 추가(`WARMUP_RATE`/`WARMUP_DURATION`, 결과는 별도
   `fanout_warmup_*` 메트릭으로 분리).

## 완료 기준

- [x] `AuctionSseTestBidApplicationService`가 Redis publish 경로를 타도록 변경, 기존 auction
      test-event 관련 테스트(`AuctionSseContractTest` 등) 통과
- [x] notification/wallet 테스트 발행 서비스 추가, 관련 단위 테스트 추가
- [x] 조합 엔드포인트 추가 및 통합 확인
- [x] `./gradlew compileJava compileTestJava` 통과
- [x] `pure-fanout.js` 문법 확인(`node --check`) 및 로컬 스택에서 실제 threadpool/가상스레드
      비교 실행까지 완료 — 결과·분석은 `~/dbidding-loadtest-local/순수-SSE-fanout-부하테스트-569.md`
      참고(별도 저장소라 이 PR에는 포함 안 됨)

> 이 문서는 claude의 도움을 받아 작성되었습니다.
