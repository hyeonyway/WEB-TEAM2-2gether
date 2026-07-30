# 경매 SSE k6 테스트

## 필요성

기본 k6에는 SSE 클라이언트가 포함되어 있지 않다. 자동 확장 설치도
`k6/x/sse`를 찾지 못할 수 있으므로 `xk6-sse`가 포함된 전용 바이너리를
빌드해 사용한다.

## 설치

```bash
brew install go
go install go.k6.io/xk6/cmd/xk6@latest
```

프로젝트의 `backend` 디렉터리에서 SSE 전용 k6를 빌드한다.

```bash
cd /Users/admin/Desktop/WEB-TEAM5-2gether/backend

GOTOOLCHAIN=go1.25.1 "$(go env GOPATH)/bin/xk6" build \
  --k6-version v1.2.1 \
  --with github.com/phymbert/xk6-sse@v0.1.12 \
  --output ./src/test/k6/sse/k6-sse
```

`xk6-sse`는 k6 v1 모듈을 사용하므로 `--k6-version v1.2.1`을 생략하면
k6 v2 바이너리가 생성되어 `k6/x/sse`를 불러오지 못할 수 있다. macOS에서
Go 1.26으로 빌드한 k6 v1 바이너리가 강제 종료될 수 있어 빌드 toolchain도
Go 1.25.1로 고정한다.

## 실행

테스트 payload 발행 API는 운영에서 노출되지 않으며 `sse-load-test` 프로필에서만
활성화된다. 백엔드 서버를 해당 프로필로 먼저 실행한다.

```bash
SPRING_PROFILES_ACTIVE=sse-load-test ./gradlew bootRun
```

다른 터미널에서 스모크 테스트를 실행한다. k6는 SSE 연결을 유지하면서
DB의 진행 중인 경매를 매번 무작위로 골라 가격·입찰 수·버전이 증가하는
`BID_PLACED` payload를 발행하므로 브라우저에서도 실제 경매처럼 확인할 수 있다.

발행 API만 수동으로 확인하려면 다음 명령을 사용한다.

```bash
curl -X POST http://localhost:8080/api/auctions/stream/test-events/random-bid
```

```bash
cd /Users/admin/Desktop/WEB-TEAM5-2gether/backend

./src/test/k6/sse/k6-sse run \
  -e BASE_URL=http://localhost:8080 \
  -e VUS=1 \
  -e RAMP_UP=1s \
  -e HOLD=20s \
  -e RAMP_DOWN=1s \
  src/test/k6/sse/auction-stream.js
```

동시 연결 수는 `VUS`, 유지 시간은 `HOLD`로 변경한다.
초당 테스트 이벤트 수는 `EVENT_RATE`로 변경한다.

```bash
./src/test/k6/sse/k6-sse run \
  -e BASE_URL=http://localhost:8080 \
  -e VUS=50 \
  -e EVENT_RATE=5 \
  -e HOLD=1m \
  src/test/k6/sse/auction-stream.js
```

생성된 `backend/src/test/k6/sse/k6-sse`는 로컬 실행 바이너리이므로 Git에
추가하지 않는다.
