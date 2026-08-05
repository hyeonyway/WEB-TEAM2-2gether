# 실제 경매 입찰 k6 테스트

`POST /api/auctions/{auctionId}/bids`를 실제로 호출해 입찰, 지갑 hold, DB 락,
SSE·알림 후처리까지 포함한 경로에 부하를 준다. 테스트 데이터가 실제로
변경되므로 로컬 또는 부하 테스트 전용 환경에서만 실행한다.

## 실행

`002-user.sql` 적용 후 백엔드와 스크립트를 실행한다. k6의 `setup()`이 기본으로
`k6-user001@dbidding.local`부터 `k6-user300@dbidding.local`까지 300개 계정을
공통 비밀번호 `K6LoadTest123!`로 로그인한다. 이후
`POST /api/auth/login`을 호출해 액세스 토큰을 발급받고, 이후 모든 입찰 요청에
`Authorization: Bearer ...` 헤더를 자동으로 붙인다. 기본 부하는
**초당 100회, 1분, 최대 300 VU**다.

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

`ACCESS_TOKENS`는 로그인 API를 사용할 수 없는 환경에서만 사용할 수 있는
예외 입력이다. 일반 실행에서는 `EMAIL`/`PASSWORD` 또는 `LOGIN_USERS`만
전달하면 된다.

프로젝트에는 k6와 `xk6-sse`가 포함된 실행 파일
`src/test/k6/sse/k6-sse`가 있으므로 전역 `k6` 설치 없이 위 명령을 실행할 수
있다. 별도로 k6를 설치했다면 `./src/test/k6/sse/k6-sse` 대신 `k6`를 써도 된다.

- `RATE`: 초당 입찰 시도 수. 기본값 `100`
- `DURATION`: 테스트 지속 시간. 기본값 `1m`
- `PRE_ALLOCATED_VUS`: 미리 확보할 VU. 기본값 `100`
- `MAX_VUS`: 최대 VU. 기본값 `300`
- `WARMUP_RATE`: 웜업 마지막 초당 요청 수. 기본값 `20`
- `WARMUP_DURATION`: 웜업 지속 시간. 기본값 `30s`
- `MAIN_START_TIME`: 본 부하 시작 시점. 기본값 `35s`
- `LOAD_TEST_USER_COUNT`: 자동 생성할 로그인 계정 수. 기본값 `300`
- `LOAD_TEST_EMAIL_PREFIX`: 계정 이메일 접두사. 기본값 `k6-user`
- `LOAD_TEST_EMAIL_DOMAIN`: 계정 이메일 도메인. 기본값 `dbidding.local`
- `LOAD_TEST_PASSWORD`: 300개 계정의 공통 비밀번호. 기본값 `K6LoadTest123!`
- `SETUP_TIMEOUT`: 300개 계정 로그인 제한 시간. 기본값 `10m`
- `bid_accepted`: 실제 `201 Created` 비율
- `bid_contentions`: 가격 조회 후 다른 요청이 선점해 발생한 `409 Conflict` 수
- `bid_accepted_or_contended`: `201`과 정상 경쟁 `409`의 합산 비율
- `bid_rejected`: 인증, 잔액 부족, 종료 경매 등 정상 경쟁 외 거절 수

각 반복은 최신 `minimum_bid`를 조회한 뒤 고유한 `Idempotency-Key`로 실제
입찰한다. 한 경매에 부하를 몰면 DB 경합 테스트가 되고, 여러 경매 ID를 주면
전체 처리량 테스트가 된다. 가격이 계속 상승하고 지갑 hold가 변경되므로 충분한
잔액과 테스트 종료 시각을 먼저 확인한다.
