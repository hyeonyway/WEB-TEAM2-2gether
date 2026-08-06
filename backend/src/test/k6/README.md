# 최종 경매 부하 테스트

`002-user.sql`의 `k6-user00001`부터 `k6-user01000`까지 로그인한 뒤, 사용자마다 경매 SSE와 알림 SSE를 하나씩 연결한다. 두 SSE 연결이 모두 준비될 때까지 대기한 뒤 실제 입찰 API를 초당 1000회 호출한다. 실제 입찰 1회가 경매 이벤트와 알림 이벤트를 함께 발생시키므로 별도의 이벤트 발행 시나리오는 두지 않는다.

실행 전 200개의 진행 중 경매 ID를 지정한다.

```bash
cd backend
K6_WEB_DASHBOARD=true \
./src/test/k6/sse/k6-sse run \
  -e BASE_URL=http://localhost:8080 \
  -e AUCTION_IDS="$(seq -s, 101 300)" \
  src/test/k6/final-auction-load.js
```

웹 대시보드는 k6 버전에 따라 기본 포트 `5665`에서 확인한다. 주요 설정은 `USERS=1000`, `BID_RATE=1000`이며, 필요할 때 환경변수로 조정할 수 있다.

기본 비밀번호는 `K6LoadTest123!`이다. 다른 비밀번호를 쓰는 사용자 데이터라면 `-e PASSWORD=...`를 지정한다. 매번 무작위 사용자·경매를 선택해 실제 bid-context 조회와 bid 등록 API를 호출한다. 따라서 실제 입찰, 지갑 hold, DB lock, 경매 SSE 및 알림 SSE 후처리가 모두 발생한다.

입찰 시나리오는 `/api/test/load/sse-status?expected=USERS`가 `ready=true`를 반환할 때까지 대기하므로 고정된 SSE 준비 시간을 사용하지 않는다.
