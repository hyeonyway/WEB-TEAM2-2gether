# 경매 SSE 전파 구조

## 목적

경매의 생성·입찰·종료 상태를 연결 중인 화면에 즉시 반영한다. 연결이 끊겼다가
복구되면 누락 이벤트를 재생하지 않고, 활성 화면이 REST API로 현재 상태를 다시
조회한다.

## 서버 전파 흐름

```text
경매 트랜잭션
    -> AuctionOpenedEvent | BidPlacedEvent | AuctionClosedEvent 발행
    -> AuctionSseEventListener (@TransactionalEventListener AFTER_COMMIT)
    -> AuctionSseConnectionManager.broadcast(payload) (@Async)
    -> 단조 증가 SSE id 부여
    -> 연결된 모든 SseEmitter에 event name + payload broadcast
```

`AFTER_COMMIT`으로 구독하므로 롤백된 경매 상태는 전파하지 않는다. `broadcast`와
heartbeat는 `auctionSseTaskExecutor`에서 실행되어 요청 처리 스레드와 분리된다.
전송 실패한 emitter만 제거하며, 다른 연결의 전송은 계속한다.

| 도메인 이벤트 | SSE event name | 핵심 payload |
| --- | --- | --- |
| 경매 생성 | `AUCTION_CREATED` | 경매·카드 스냅샷·판매자 |
| 입찰 | `BID_PLACED` | 현재가·입찰 수·입찰자·이전 최고 입찰자 |
| 경매 종료 | `AUCTION_CLOSED` | 종료 상태·낙찰가·낙찰자·카드 스냅샷 |

모든 상태 변경 이벤트는 SSE `id`를 가진다. 이 ID는 연결 중 도착 순서가 뒤바뀐
이전 이벤트를 프론트가 버리기 위한 단조 증가 값이다.

## 저장과 재연결 정책

서버는 SSE 이벤트 이력이나 경매별 최신 상태를 저장하지 않는다. 따라서
`Last-Event-ID`를 읽거나 replay payload, `replay-reset` 이벤트를 만들지 않는다.

브라우저 `EventSource`는 연결 종료 후 3초를 기준으로 자동 재연결한다. 공유 연결이
다시 `open`되면 프론트는 한 번만 재연결 신호를 발행하고, 해당 스트림을 구독 중인
활성 화면의 React Query를 무효화한다. 그 결과 REST API가 현재 경매 상태를
권위 있는 값으로 다시 가져온다.

```text
SSE 연결 종료
    -> EventSource 자동 재연결
    -> shared EventSource open (최초 open 제외)
    -> 활성 구독 화면의 query invalidate
    -> REST refetch
    -> 최신 경매 상태 렌더링
```

## 프론트 수신 흐름

`useAuctionStream`은 애플리케이션 전체에서 `EventSource` 하나만 생성하고, 각
화면의 구독자에게 payload를 fan-out한다. 마지막 구독자가 해제되면 연결도 닫는다.

| 활성 구독 지점 | 연결 중 이벤트 처리 | 재연결 처리 |
| --- | --- | --- |
| 경매 목록 | 목록 React Query 캐시 갱신·정렬 | 경매 목록 query refetch |
| 대시보드 참여 경매 | 참여 경매 캐시 갱신 | 대시보드 query refetch |
| 경매 상세 | 현재 입찰가·입찰 내역·입찰 컨텍스트 캐시 갱신 | 상세·입찰 내역·입찰 컨텍스트 refetch |
| 입찰 팝업 | 입찰 컨텍스트 캐시 갱신 | 입찰 컨텍스트·입찰 내역 refetch |
| 지갑 동기화 | 내 지갑에 영향 있는 이벤트만 무효화 | 지갑 잔액 refetch |

프론트 캐시에 이미 반영된 `event_id`보다 작거나 같은 이벤트는 무시한다. 따라서
비동기 전송으로 이벤트 도착 순서가 바뀌어도 화면 상태를 이전 값으로 되돌리지 않는다.

## 범위와 한계

- 서버 재시작이나 장시간 단절 후에도 replay를 시도하지 않는다.
- 재연결 후 최신성은 활성 화면의 REST refetch가 보장한다.
- 비활성 화면은 구독하지 않으며, 다음 진입 시 일반 REST 조회가 기준 상태가 된다.
- 멀티 인스턴스 환경에서는 인스턴스 간 SSE event relay가 별도로 필요하다.
