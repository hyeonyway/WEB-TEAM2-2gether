# SSE 부하테스트 실험 A: emitter.send() 인위적 지연 주입

이슈: #579

## 배경

#569 순수 SSE fan-out 부하테스트에서 threadpool과 가상스레드(`sse-virtual-threads` 프로필)를
비교한 결과, 현재 조건(구독자 500명, in-memory `emitter.send()`)에서는 threadpool이 가상스레드보다
확실히 우세했다(같은 조건에서 threadpool 44.7ms vs 가상스레드 훨씬 느림 — #575 문서 참고). 가상스레드는
blocking I/O가 섞여 있을 때 캐리어 스레드를 점유하지 않는 이점이 커지는데, 로컬 부하테스트 환경에서는
`emitter.send()`가 거의 즉시 반환돼 그 이점이 드러날 조건 자체가 없는 것으로 추정된다.

## 작업 내용

1. `SseEmitterRegistry.send()`가 실제 `emitter.send(event)` 호출 직전에 인위적 지연을 넣을 수
   있도록 `artificialSendDelayMillis` 필드 추가 — 기본값 0(no-op)
2. 환경변수 `SSE_SEND_ARTIFICIAL_DELAY_MS`(기본값 0)로 제어, `MeSseConnectionManager`(알림+지갑
   공유 registry)와 `AuctionSseConnectionManager` 양쪽에 동일하게 적용
3. 지연은 emitter별 send 직렬화 락(`sendLock`)을 잡은 채로 sleep한다 — 실제 느린 네트워크 write도
   같은 emitter에 대한 동시 send를 막는 것과 동일한 특성을 재현하기 위함
4. 로컬 부하테스트 스택(`~/dbidding-loadtest-local`)에서 0/1/5/20ms로 스윕하며 threadpool vs
   가상스레드 배달 지연을 비교, 가상스레드가 threadpool을 앞서는 지점이 있는지 탐색

## 범위 밖

- 구독자 수 스윕(500 → 2,000~20,000)은 별도 실험(실험 B)으로 분리, 이번 이슈에서는 다루지 않음
- `me_sse_delivery_latency`(notification/wallet)가 모든 조건에서 나쁜 현상의 원인 조사는 하지 않음

## 완료 기준

- [ ] `SseEmitterRegistry`/`MeSseConnectionManager`/`AuctionSseConnectionManager` 관련 기존 테스트 통과
- [ ] `./gradlew compileJava compileTestJava` 통과
- [ ] `~/dbidding-loadtest-local`에서 0/1/5/20ms 스윕 실측, threadpool vs 가상스레드 배달 지연
      비교 결과를 `~/dbidding-loadtest-local/순수-SSE-fanout-부하테스트-569.md`에 반영

> 이 문서는 claude의 도움을 받아 작성되었습니다.
