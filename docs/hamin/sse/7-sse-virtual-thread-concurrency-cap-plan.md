# SSE 브로드캐스트 가상스레드 동시성 캡 (계획)

refs #495

## 배경

부하테스트로 확인한 바, `sse-virtual-threads` 프로필에서 SSE 브로드캐스트(Auction/Notification fan-out)가 backend CPU(2 vCPU cap)를 거의 다 잡아먹어서, 같은 CPU를 나눠 써야 하는 입찰 처리 쪽 latency가 크게 저하된다 (`dbidding-loadtest-local/부하테스트-요약.md` "SSE 없이 순수 처리량 상한 탐색" 섹션 — SSE_VUS=0일 때 backend CPU 0.29 core, SSE_VUS=300일 때 1.996 core로 SSE 하나가 약 1.7 core를 잡아먹음).

가상 스레드는 생성 비용이 거의 없어 무제한으로 뜨면 순간적으로 CPU를 전부 점유할 수 있다. 세마포어로 동시 실행 개수를 제한하면 이 CPU를 SSE와 입찰 처리 사이에서 나눌 수 있는 손잡이가 될 것으로 예상한다.

## 이번 이슈 범위

**코드 변경만.** 값 튜닝을 위한 부하테스트 스윕(무제한→200→100→50→25→10 등)은 별도로 진행하며 이번 작업에는 포함하지 않는다.

## 변경 사항

1. `VirtualThreadSseTaskExecutor`
   - `maxConcurrency` 파라미터 추가 (0이면 무제한 — 기존 동작 유지)
   - `maxConcurrency > 0`이면 `Semaphore`로 `execute()` 동시 실행 개수 제한
   - permit 획득은 `execute()` 호출 스레드(브로드캐스터 자신의 가상 스레드)에서 블로킹 — task 완료 시 반납, 초과분은 대기열처럼 자연히 밀림

2. `AuctionSseExecutorConfig`
   - `AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY` 환경변수(기본 0) 추가, `sse-virtual-threads` 프로필의 auction-sse executor에 연결

3. `NotificationExecutorConfig`
   - `NOTIFICATION_FANOUT_VIRTUAL_MAX_CONCURRENCY` 환경변수(기본 0) 추가, notification-sse executor에 연결

4. `VirtualThreadSseTaskExecutorTest`
   - 세마포어 동작 자체를 검증하는 유닛 테스트 추가 — `maxConcurrency`로 설정한 상한을 동시 실행 개수가 넘지 않는지 확인

## 검증

- `./gradlew test --tests "com.dbidding.sse.config.*"` 통과
- 기존 동작(캡 미설정 시 무제한) 회귀 없는지 기존 테스트로 확인

## 다음 (별도 작업)

- `dbidding-loadtest-local`에서 경매 3~5개 분산 세팅(RATE=130, SSE_VUS=300)으로 `AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY`/`NOTIFICATION_FANOUT_VIRTUAL_MAX_CONCURRENCY` 값을 스윕하며 bid latency 회복 vs `auction_sse_delivery_latency` 저하 트레이드오프 측정
