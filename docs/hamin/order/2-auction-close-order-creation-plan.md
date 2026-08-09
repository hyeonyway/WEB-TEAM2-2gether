# Order 2단계 계획 — 경매 종료 시 Order 생성을 closeLockedAuction에서 직접 호출

담당: 임하민. 이슈: [#232](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/232)
(브랜치 `feature/232-order-creation-on-auction-close` 예정). 배경: `1-purchase-confirm-cancel-plan.md`.

## 배경

Order 1단계에서는 `AuctionClosedEvent`를 구독하는 `OrderEventListener`
(`@Async @TransactionalEventListener(phase = AFTER_COMMIT)`)로 주문을 생성했다. 이후 PR #228
CodeRabbit 리뷰에서 이벤트 기반 생성은 유실 위험이 있다는 지적을 받아 리스너를 제거했다
(`9b8e0a8 refactor: OrderEventListener 제거, Order 생성 트리거는 후속 이슈로 분리`).
`OrderService.createFromAuctionClosed(auctionId, winnerId, sellerId, cardName, winningPrice)`는
그때 이미 원시값만 받도록 정리해뒀다(auction 패키지의 이벤트 타입을 몰라도 되게).

이후 wallet 정산 작업(이슈 #237, PR #278) 도중 이벤트 기반 방식을 다시 쓰자는 논의가 있었으나,
동일한 유실 위험 문제로 재도입하지 않기로 설득해 확정했다. PR #278은 결국 order↔wallet 연동만
포트 없는 직접 호출로 정리했고(`8921028 refactor: 주문 지갑 정산을 Port·Adapter 없이 직접 호출로
교체`, `OrderService`가 `WalletSettlementPort` 대신 `WalletService`를 직접 주입), auction→order
생성 트리거는 여전히 비어 있는 상태로 dev에 남아 있다.

## 목표

`AuctionCommandService.closeLockedAuction()`이 낙찰 처리를 하는 분기(유찰이 아닌 경우)에서
`OrderService.createFromAuctionClosed(...)`를 **같은 트랜잭션 안에서 직접** 호출한다. 실패하면
그 트랜잭션 전체(주문 생성 + 낙찰 처리 + wallet capture)가 롤백되고, 마감 처리 자체를 다시
시도할 수 있어야 한다.

## 범위

- 대상은 `closeLockedAuction`의 낙찰(`winner != null`) 분기뿐이다. 유찰 분기는 원래부터 주문
  생성 대상이 아니다(`createFromAuctionClosed`도 `winnerId == null`이면 바로 반환).
- Port로 감쌀지 여부: 이슈 본문엔 "담당자 판단"이라고 적혀 있지만, `WalletSettlementPort` 제거
  사례(PR #278)와 같은 방향으로 **포트 없이 `OrderService`를 직접 주입해 호출**한다 — auction/
  order 양쪽에서 이미 port-adapter 구조를 걷어내는 중이라는 사용자 확인에 따른 것.

## 구현 내용

### 1. `AuctionCommandService`에 `OrderService` 의존성 추가

`com.dbidding.order.OrderService` import 후 `private final OrderService orderService;` 필드
추가. `@RequiredArgsConstructor`라 생성자는 자동으로 갱신되지만, 아래 테스트들이 생성자를 직접
호출하고 있어 인자 추가가 필요하다.

### 2. `closeLockedAuction` 수정 (`AuctionCommandService.java:468-490`)

현재:
```java
Bid winner = winningBid.get();
winner.markWon();
auction.closeWithWinningBid(winner, closedAt);
walletService.capture(winner.getBidderId(), auction.getId(), winner.getBidPrice());
publishAuctionClosed(auction, winner, closedAt);
```

변경 후:
```java
Bid winner = winningBid.get();
winner.markWon();
auction.closeWithWinningBid(winner, closedAt);
walletService.capture(winner.getBidderId(), auction.getId(), winner.getBidPrice());
CardSnapshot card = cardService.getCardSnapshot(auction.getItemId());
orderService.createFromAuctionClosed(
        auction.getId(), winner.getBidderId(), auction.getSellerId(), card.name(), winner.getBidPrice()
);
publishAuctionClosed(auction, winner, closedAt, card);
```

- `publishAuctionClosed`는 지금도 내부에서 `cardService.getCardSnapshot(...)`를 호출한다
  (`AuctionCommandService.java:516`). 낙찰 분기에서 order 생성에 쓸 `CardSnapshot`을 먼저
  조회해 `publishAuctionClosed`에 파라미터로 넘기고, 그 메서드 내부의 중복 조회를 제거한다
  (시그니처를 `publishAuctionClosed(Auction, Bid, Instant, CardSnapshot)`로 변경). 유찰
  분기(`closeWithoutTrade`)는 order 생성이 없으니 그 호출부에서 별도로 한 번 조회해서 넘긴다.
- `createFromAuctionClosed`가 던지는 예외(고유 제약 위반이 아닌 그 외 `DataIntegrityViolationException`,
  기타 `RuntimeException`)는 별도 try/catch 없이 그대로 전파한다. `walletService.capture(...)`가
  이미 실행된 뒤 order 생성이 실패해도 같은 트랜잭션이라 capture까지 함께 롤백된다.

### 3. 트랜잭션·재시도 동작 — 기존 구조가 이미 요구사항을 만족, 추가 로직 불필요

| 호출 경로 | 트랜잭션 | 실패 시 동작 |
|---|---|---|
| 즉시구매 (`participate`, `AuctionCommandService.java:141`) | `@Transactional` | 주문 생성 실패 → 입찰 자체가 롤백되고 사용자에게 에러 응답. 경매는 `OPEN`으로 남아 이후 마감 스케줄러가 `closeTime` 도래 시 정상적으로 다시 닫는다 |
| 스케줄러 마감 (`closeDueAuction`, `AuctionCommandService.java:271`, `REQUIRES_NEW`) | 독립 트랜잭션 | 주문 생성 실패 → 해당 시도 전체 롤백(auction 상태 원복). `AuctionDueClosingService.closeDueAuctionWithRetry`는 `PessimisticLockingFailureException`일 때만 최대 3회 즉시 재시도하고, 그 외 예외는 로그 후 스킵하지만 auction row는 여전히 `OPEN`/`ENDING` + `closeTime <= now`라 다음 백업 스케줄러 주기(기본 60초)가 다시 마감 대상으로 집어 재시도한다 |
| 수동 `closeAuction` | `@Transactional` | 컨트롤러 미연결(테스트 전용)이라 실사용 영향 없음. 동일하게 롤백만 되고 별도 재시도는 없음 |

→ 이슈가 요구한 "실패 시 롤백 + 재시도"는 스케줄러 경로 기준으로 새 코드 없이 기존
`AuctionDueClosingService` 재시도/백업 스케줄러 구조로 이미 충족된다. 재시도 로직은 추가하지
않는다.

### 4. 테스트

`new AuctionCommandService(...)` 생성자를 직접 호출하는 3개 파일에 `orderService` mock 인자
추가 필요:

- `AuctionServiceCloseTest.java` — 낙찰 케이스(`종료_시각이_지난_경매의_최고_입찰을_낙찰_처리한다`)에
  `verify(orderService).createFromAuctionClosed(1, 3, 2, "리자몽", 45_000L)` 검증 추가. 유찰
  케이스(`입찰이_없는_종료_대상_경매는_거래_없이_종료한다`)에 `verifyNoInteractions(orderService)`
  추가.
- `AuctionServiceBidTest.java` — 즉시구매 낙찰 케이스들(`즉시구매가_이상_입찰은_즉시구매가로_낙찰되고_경매를_종료한다`
  등, 135번째 줄 부근)에 `orderService` 호출 검증 추가.
- `AuctionRegistrationContractTest.java` — 생성자 인자만 갱신(경매 등록 시나리오라 close 경로를
  타지 않으므로 호출 검증은 불필요할 가능성이 높음, 실제 파일 확인 후 최종 판단).
- 신규 케이스: order 생성이 예외를 던질 때 트랜잭션 전체가 롤백되는지는 Mockito 단위 테스트로는
  검증이 어렵다(실제 트랜잭션이 없음). `orderService.createFromAuctionClosed(...)`가 예외를
  던지면 `closeLockedAuction` 호출자(`closeAuction`/`closeDueAuction`)에게 그대로 전파되는지만
  단위 테스트로 확인하고, 실제 롤백 여부는 기존 `AuctionDueClosingParallelIntegrationTest`류
  통합 테스트에 케이스를 보탤지 구현 중 판단한다.

## 결론

새 포트나 재시도 로직 없이, `closeLockedAuction`의 낙찰 분기에 `orderService.createFromAuctionClosed(...)`
직접 호출 한 줄(+ `CardSnapshot` 중복 조회 제거를 위한 `publishAuctionClosed` 시그니처 조정)만
추가하면 이슈 232 요구사항이 충족된다.

> 이 문서는 claude의 도움을 받아 작성하였습니다.
