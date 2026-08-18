# 순수 SSE fan-out 부하테스트 전용 코드 제거

이슈: #593

## 배경

순수 SSE fan-out 부하테스트(#569)를 위해 추가했던 `@Profile("test")` 전용 프로덕션 코드를,
해당 부하테스트 시리즈(#569/#495/#575/#579/#585)가 결론까지 끝나서 더 이상 독립적인 SSE
fan-out 부하테스트를 돌릴 계획이 없어 제거한다. 최종 결론은
[`495-sse-virtual-thread-fanout-conclusion.md`](495-sse-virtual-thread-fanout-conclusion.md) 참고.

`@Profile("test")`가 붙은 파일 전부, 이 그룹끼리만 서로 참조하고 그룹 밖 프로덕션 코드에서는
아무도 안 쓴다는 걸 확인했다.

**`SseLoadTestStatusController`(`/api/test/load/sse-status`, 프로필 가드 없음)는 제외** —
`pure-fanout.js`뿐 아니라 `final-auction-load.js`/`hot-auction-pattern.js` 같은 다른 기존
부하테스트 스크립트에서도 계속 쓰는 공용 유틸이라 남겨둔다.

## 작업 내용

1. 프로덕션 파일 삭제:
   - `sse/test/SseFanoutTestEventService.java`, `SseFanoutTestEventController.java`, `SseFanoutTestEventResult.java`
   - `auction/sse/AuctionSseTestAuctionReader.java`, `AuctionSseTestBidApplicationService.java`, `AuctionSseTestEventController.java`
   - `notification/sse/NotificationSseTestPushService.java`
   - `wallet/sse/WalletSseTestPushService.java`
2. `SseFanoutTestEventServiceTest.java` 삭제(이 그룹 전용 테스트)
3. `AuctionSseContractTest.java`에서 이 그룹을 검증하는 테스트 메서드 2개
   (`테스트_이벤트_엔드포인트는_test_프로필에서만_활성화된다`,
   `테스트_입찰_이벤트는_버전과_가격을_순차적으로_증가시킨다`)만 제거 — 파일 전체는
   `AuctionSseConnectionManager` 공용 계약 테스트라 유지. 안 쓰게 되는 import(`Profile`,
   `Duration`, `Optional`)도 정리.

## 범위 밖

`backend/src/test/k6/sse/pure-fanout.js`(k6 스크립트)도 이 엔드포인트들이 없으면 더 이상
동작하지 않게 되지만, k6 스크립트 자체는 코드 컴파일에 영향 없어 이번 작업 범위 밖 —
필요하면 나중에 별도로 정리.

## 완료 기준

- [x] `AuctionSseContractTest` 통과
- [x] `./gradlew compileJava compileTestJava` 통과

> 이 문서는 claude의 도움을 받아 작성되었습니다.
