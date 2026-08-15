# Realtime(SSE) 개발 계획

경매 목록/상세, 대시보드/랭킹, 나의 대시보드, 웹 알림이 폴링 대신 실시간으로 갱신되도록 SSE(Server-Sent Events)를 도입한다. 웹소켓·Polling·Redis Pub/Sub·Redis Streams는 팀 회의에서 검토 후 채택하지 않았으며, 이유는 [1-sse-architecture.md](1-sse-architecture.md)에 정리했다.

이 디렉토리는 실시간성 관련 설계만 다룬다. 경매/입찰 자체의 동시성 제어(DB 비관적 락)는 기존 `auction`/`bid` 담당(이은기) 문서를, 지갑 홀드·해제·낙찰 차감은 [../wallet/5-auction-wallet-integration.md](../wallet/5-auction-wallet-integration.md)를 참고한다.

## 구현 단계

1. [SSE 아키텍처 개요](1-sse-architecture.md) — 기술 선택과 이유, 담당 배분, 미정 항목
2. [SSE Payload 사전 직렬화](2-sse-payload-pre-serialization.md) — fan-out 전 JSON을 한 번만 만들고 emitter에는 동일한 JSON data를 전달하는 성능 개선
3. [경매 SSE 선택 구독](3-auction-sse-selective-subscription.md) — 상세·목록·검색 화면이 실제로 관찰하는 경매만 구독하고, 지갑 갱신은 개인화 스트림으로 분리하는 개선 설계
4. [emitter 동시 send 붕괴 수정](4-sse-emitter-concurrent-send-fix.md) — 선택 구독으로 emitter가 다중 키를 구독하게 되며 생긴 동시 send 충돌 버그(#520) 원인·수정·검증

`TicketProvider` 인터페이스/구현 계획은 A(김현문) 담당 문서로 옮겨졌다:
[../auth/5-current-user-and-sse-auth.md](../auth/5-current-user-and-sse-auth.md).
대시보드/알림 컨트롤러는 티켓 인터페이스를 직접 주입하지 않고 공통
`@CurrentUser Integer userId` 계약만 사용한다.

## API 요약

| Method | Path | 인증 | 담당 | 비고 |
|---|---|---|---|---|
| GET | `/api/auctions/stream` | 불필요 | 정세호 | 경매 목록 전체 연결(MVP, auctionId 필터링 없음) |
| GET | `/api/auctions/{auctionId}/stream` | 불필요 | 정세호 | 경매 상세 현재가/입찰. 이은기가 발행하는 이벤트를 구독 |
| GET | `/api/dashboard/stream` | 티켓 | 정세호 | 홈 대시보드 인사이트/랭킹 |
| GET | `/api/users/{userId}/auctions/stream` | 티켓 | 정세호 | 나의 참여경매 현재가 |
| GET | `/api/users/{userId}/notifications/stream` | 티켓 | 임하민 | 웹 알림 |
| POST | `/api/sse/tickets` | JWT(기존) | 김현문 | SSE 인증용 1회용 티켓 발급 |

사용자별 스트림의 `{userId}`는 라우팅 값일 뿐 인증 근거가 아니다. 실제
컨트롤러와 서비스는 티켓 검증 결과가 주입된 `@CurrentUser Integer userId`를
기준으로 데이터를 조회하고, PathVariable이 필요하면 두 ID의 일치를 검증한다.

이은기는 위 스트림을 직접 만들지 않고 `BidPlacedEvent`/`BidOutbidEvent`/`AuctionClosedEvent` 등 도메인 이벤트 발행만 담당한다. 정세호/임하민이 각자 `@EventListener`로 구독해 자기 SSE emitter에 push한다.

## 주요 규칙 (회의 결론)

- Redis Pub/Sub, Redis Streams, WebSocket, Polling 전부 **미채택**. 단일 인스턴스 환경이라 인스턴스 간 릴레이가 필요 없고, 순수 인메모리 `@EventListener` → 로컬 `SseEmitter` push만으로 충분하다. 이에 따라 별도 `global.realtime` 공용 모듈도 만들지 않는다 — 정세호/임하민이 각자 패키지 안에서 로컬 emitter 레지스트리만 관리한다.
- 공개 시세 데이터(경매 목록/상세)는 인증 없이 SSE 접근을 허용한다. 개인화 데이터(대시보드/알림)만 티켓으로 인증한다.
- SSE 티켓도 단일 인스턴스의 인메모리 저장소에 30초 동안만 보관한다. 검증 시
  원자적으로 제거해 한 번만 사용할 수 있게 하고, 만료된 미사용 티켓은
  주기적으로 정리한다. 멀티 인스턴스로 전환할 때만 공유 저장소 구현으로
  교체한다.
- 경매 목록은 스크롤/페이지네이션에 따른 재구독 로직이 프론트에서 복잡해, MVP는 auctionId 필터링 없이 **전체 연결**로 구현한다. 트래픽 문제가 실제로 생기면 그때 보이는 항목만 구독하도록 개선한다.
- SSE 인증은 `fetch`+`Authorization` 헤더 방식을 검토했으나, 통제하기 어려운 프론트 복잡성보다 통제 가능한 백엔드 복잡성(티켓 발급/검증)이 늘어나는 쪽이 문제 발생 시 더 유연하다고 판단해 `EventSource`+티켓 방식을 최종 채택했다.
- 멀티 인스턴스로 스케일아웃하게 되면 그때 Redis Pub/Sub 발행 한 줄을 추가하면 되고, 로컬 emitter 관리 로직은 바뀌지 않는다.

## 완료 기준

이 영역은 다수 항목이 팀 논의 대기 중이라 완료 기준을 분리한다.

- [1-sse-architecture.md](1-sse-architecture.md)의 "미정 항목" 전부 팀 확정
- 티켓 구현 완료 기준은 [../auth/5-current-user-and-sse-auth.md](../auth/5-current-user-and-sse-auth.md) 참고(팀 확정과 무관하게 즉시 진행 가능)

> 이 문서는 codex의 도움을 받아 작성하였습니다
