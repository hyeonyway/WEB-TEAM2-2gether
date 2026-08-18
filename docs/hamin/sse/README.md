# SSE 공통 인프라 / fan-out 성능 (이슈 #362, #495, #569, #585 등)

Auction/Notification/Wallet이 공유하는 SSE 등록·전송·executor 인프라와, threadpool vs
`sse-virtual-threads` 프로필 fan-out 성능 비교 시리즈. 도메인별 SSE 적용(알림 실시간 푸시
설계 등)은 각 도메인 폴더(`../notification/`, `../auction/`) 참고.

| 이슈 | 범위 | 상태 | 문서 |
|---|---|---|---|
| — | SSE executor task lifecycle / 연결 메트릭 계측 | 완료 | [계획 문서](6-sse-executor-and-connection-metrics-plan.md) |
| — | Auction/Notification/Wallet 공통 emitter 등록·전송·heartbeat 로직 통합(#508) | 완료 | [계획 문서](508-sse-common-registry-metrics-plan.md) |
| [#495](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/495) | 가상스레드 SSE 브로드캐스트 동시성 캡 추가 | 완료(검증은 #569로 이어짐) | [초기 계획 문서](7-sse-virtual-thread-concurrency-cap-plan.md) → [최종 결론](495-sse-virtual-thread-fanout-conclusion.md) |
| [#569](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/569) | 순수 SSE fan-out 부하테스트(threadpool vs 가상스레드) | 완료 | [계획 문서](569-pure-sse-fanout-loadtest-plan.md), [실측 전체 기록](순수-SSE-fanout-부하테스트-569.md) |
| [#579](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/579) | emitter.send() 인위적 지연 실험 | 완료(실험 코드는 미병합, 로컬 브랜치에만 존재) | [최종 결론](495-sse-virtual-thread-fanout-conclusion.md) |
| [#585](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/585) | 가상스레드 캡을 tryAcquire+discard 기반 진짜 admission control로 수정 | 완료 | [계획 문서](585-virtual-executor-admission-control-plan.md) |

**최종 결론은 [`495-sse-virtual-thread-fanout-conclusion.md`](495-sse-virtual-thread-fanout-conclusion.md) 하나로 정리돼 있다** —
왜 가상스레드를 쓰는지, 왜 broadcast/send executor를 분리했는지, 왜 캡을 걸었는지, 캡 값을
어떻게 정하는지, 최종 정책 결론까지. `순수-SSE-fanout-부하테스트-569.md`/`부하테스트-요약.md`는
그 결론에 이르기까지의 실측 원본 기록(k6 결과 파일명, 스윕 과정, 막다른 길 포함)이다.

관련이지만 도메인 소속이라 다른 폴더에 있는 문서: [`../auction/575-sse-broadcast-nonblocking-dispatch-plan.md`](../auction/575-sse-broadcast-nonblocking-dispatch-plan.md)(#575, `AuctionSseConnectionManager.broadcast()` non-blocking dispatch), [`../notification/544-notification-executor-discard-policy-plan.md`](../notification/544-notification-executor-discard-policy-plan.md)(#544), [`../notification/546-notification-sse-reconnect-refetch-plan.md`](../notification/546-notification-sse-reconnect-refetch-plan.md)(#546).

> 이 문서는 claude의 도움을 받아 작성하였습니다.
