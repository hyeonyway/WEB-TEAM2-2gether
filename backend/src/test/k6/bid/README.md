# 실제 경매 입찰 k6 테스트

`POST /api/auctions/{auctionId}/bids`를 실제로 호출해 입찰, 지갑 hold, DB 락,
SSE·알림 후처리까지 포함한 경로에 부하를 준다. 테스트 데이터가 실제로
변경되므로 로컬 또는 부하 테스트 전용 환경에서만 실행한다.

## 실행

`002-user.sql` 적용 후 백엔드와 스크립트를 실행한다. k6의 `setup()`이
300개의 전용 계정 중 기본으로 `k6-user001@dbidding.local`부터
`k6-user010@dbidding.local`까지 10개 계정을 공통 비밀번호
`K6LoadTest123!`로 로그인한다. 로그인은 기본 10개씩 병렬 처리한다. 이후
`POST /api/auth/login`을 호출해 액세스 토큰을 발급받고, 이후 모든 입찰 요청에
`Authorization: Bearer ...` 헤더를 자동으로 붙인다. 기본 부하는
**초당 100회, 1분, 최대 300 VU**다.

로그인 중에는 각 배치가 끝날 때마다 `[setup/login] 10/300명 완료` 형식으로
계정 수, 진행률, 경과 시간을 출력한다. 토큰과 이메일은 로그에 출력하지 않는다.

```bash
cd backend

./src/test/k6/sse/k6-sse run \
  -e BASE_URL=http://localhost:8080 \
  -e AUCTION_IDS='101,102,103' \
  src/test/k6/bid/auction-bid.js
```

`AUCTION_IDS`를 생략하면 진행 중인 경매를 최대 100개까지 자동 조회한다.
본 부하 전에는 30초 동안 초당 1회에서 20회까지 점진적으로 높이는 웜업을
실행하며, 본 측정은 35초 뒤 시작한다. 웜업 결과는 본 테스트 임계치에서 제외된다.
강한 경쟁 부하에는 여러 계정 로그인을 권장하며 JSON 배열을 사용한다.

```bash
./src/test/k6/sse/k6-sse run \
  -e LOGIN_USERS='[{"email":"bidder1@example.com","password":"password1"},{"email":"bidder2@example.com","password":"password2"}]' \
  -e AUCTION_IDS='101,102' \
  src/test/k6/bid/auction-bid.js
```

## 부하 조절

```bash
./src/test/k6/sse/k6-sse run \
  -e EMAIL='bidder@example.com' \
  -e PASSWORD='password' \
  -e RATE=300 \
  -e DURATION=2m \
  -e PRE_ALLOCATED_VUS=300 \
  -e MAX_VUS=800 \
  src/test/k6/bid/auction-bid.js
```

로그인 입력은 `LOGIN_USERS` → `EMAIL`/`PASSWORD` → `ACCESS_TOKENS` → 자동
생성 계정 순서로 선택한다. `ACCESS_TOKENS`를 사용하면 setup의 로그인 요청을
건너뛰므로 만료 전 토큰으로 테스트를 반복할 때 유용하다.

프로젝트에는 k6와 `xk6-sse`가 포함된 실행 파일
`src/test/k6/sse/k6-sse`가 있으므로 전역 `k6` 설치 없이 위 명령을 실행할 수
있다. 별도로 k6를 설치했다면 `./src/test/k6/sse/k6-sse` 대신 `k6`를 써도 된다.

## 환경변수

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8080` | 테스트 대상 백엔드 주소. 마지막 `/`는 자동 제거한다. |
| `RATE` | `100` | 본 부하에서 초당 시작할 반복 수. 반복마다 컨텍스트 조회와 입찰 요청을 각각 한 번 호출한다. |
| `DURATION` | `1m` | 본 부하 지속 시간. |
| `PRE_ALLOCATED_VUS` | `100` | 본 부하 전에 미리 준비할 VU 수. |
| `MAX_VUS` | `300` | 목표 도착률을 처리하기 위해 늘릴 수 있는 최대 VU 수. 부족하면 `dropped_iterations`가 발생한다. |
| `WARMUP_RATE` | `20` | 웜업 종료 시점의 초당 반복 수. 웜업은 1 RPS에서 시작한다. |
| `WARMUP_DURATION` | `30s` | 웜업 지속 시간. |
| `MAIN_START_TIME` | `35s` | 전체 시나리오 시작 후 본 부하가 시작되는 시점. 일반적으로 웜업 시간에 graceful stop 5초를 더한다. |
| `SETUP_TIMEOUT` | `10m` | 로그인과 경매 조회를 포함한 setup 제한 시간. |
| `LOAD_TEST_USER_COUNT` | `10` | 자동 생성해 로그인할 계정 수. SQL에는 300명이 준비되므로 최대 `300` 사용을 권장한다. |
| `LOGIN_BATCH_SIZE` | `10` | `http.batch()`로 동시에 로그인할 계정 수. 서버 CPU 상황에 따라 `5`~`20`을 권장한다. |
| `LOAD_TEST_EMAIL_PREFIX` | `k6-user` | 자동 생성 계정 이메일의 접두사. |
| `LOAD_TEST_EMAIL_DOMAIN` | `dbidding.local` | 자동 생성 계정 이메일의 도메인. |
| `LOAD_TEST_PASSWORD` | `K6LoadTest123!` | 자동 생성한 300개 계정의 공통 비밀번호. |
| `AUCTION_IDS` | 없음 | 쉼표로 구분한 입찰 대상 경매 ID. 없으면 진행 중 경매를 최대 100개까지 조회한다. |
| `LOGIN_USERS` | 없음 | `[{"email":"...","password":"..."}]` 형식의 로그인 계정 배열. |
| `EMAIL` | 없음 | 단일 로그인 계정 이메일. `PASSWORD`와 함께 사용한다. |
| `PASSWORD` | 없음 | 단일 로그인 계정 비밀번호. `EMAIL`과 함께 사용한다. |
| `ACCESS_TOKENS` | 없음 | 쉼표로 구분한 사전 발급 Access Token. 지정하면 로그인 API를 호출하지 않는다. |

`RATE=100`은 사용자 100명이라는 뜻이 아니라 초당 반복 100회를 의미한다.
반복 한 번에 HTTP 요청이 2개이므로 모두 정상 처리되면 초당 요청은 대략 200개다.
응답이 느려 `MAX_VUS`로도 목표 도착률을 유지하지 못하면 사용되지 못한 반복은
`dropped_iterations`에 기록된다.

## 사용자와 경매 선택

각 VU는 `(__VU - 1) % tokens.length`로 선택한 계정을 반복해서 사용한다. 따라서
300개 토큰과 300 VU를 사용하면 VU 1부터 300까지 계정 1부터 300에 대응한다.
토큰이 10개이고 VU가 300개라면 여러 VU가 동일 계정을 순환 공유한다. 사용자는
요청마다 무작위로 바뀌지 않는다.

경매는 `AUCTION_IDS` 또는 자동 조회 결과 중에서 반복마다 무작위로 선택한다.
단일 경매를 지정하면 DB 락 경합 측정에 가깝고, 여러 경매를 지정하면 전체 처리량
측정에 가깝다.

## 웜업과 본 측정

기본 실행 순서는 setup 로그인 및 경매 조회 → 30초 웜업 → 5초 종료 여유 →
1분 본 부하다. 웜업도 실제 컨텍스트 조회와 입찰 요청을 수행하지만 본 부하 전용
threshold에는 포함되지 않는다. 웜업 시간을 변경할 때는 다음처럼 본 부하 시작
시점도 함께 조절한다.

```bash
-e WARMUP_DURATION=1m \
-e MAIN_START_TIME=1m5s
```

## 결과 지표

- `bid_accepted`: 실제 `201 Created` 비율
- `bid_contentions`: 가격 조회 후 다른 요청이 선점해 발생한 `409 Conflict` 수
- `bid_accepted_or_contended`: `201`과 정상 경쟁 `409`의 합산 비율
- `bid_rejected`: 인증, 잔액 부족, 종료 경매 등 정상 경쟁 외 거절 수
- `bid_end_to_end_duration`: 컨텍스트 조회부터 입찰 응답까지 전체 소요 시간

본 부하에서는 check 성공률 99% 초과, 성공 또는 정상 충돌 비율 99% 초과,
HTTP 실패율 1% 미만, 컨텍스트 조회 p95 500ms 미만, 입찰 요청 p95 1초 미만을
통과 조건으로 사용한다. 정상적인 동시 입찰 경쟁에서 발생하는 `409 Conflict`는
실패로 계산하지 않는다.

각 반복은 최신 `minimum_bid`를 조회한 뒤 고유한 `Idempotency-Key`로 실제
입찰한다. 한 경매에 부하를 몰면 DB 경합 테스트가 되고, 여러 경매 ID를 주면
전체 처리량 테스트가 된다. 가격이 계속 상승하고 지갑 hold가 변경되므로 충분한
잔액과 테스트 종료 시각을 먼저 확인한다.
