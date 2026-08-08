# 이슈 252 — auctions.version 컬럼 제거 후 남은 테스트 setup SQL 정리

담당: 임하민. 이슈: [#252](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/252)
(브랜치 `fix/252-auction-version-column-test-cleanup`).

`AGENTS.md`상 원래 담당 범위는 `notifications`/`psa`/`upload`/`wishlist`인데, 이번 작업(`wallet`/
`auction`/`card` 패키지의 테스트 코드만 수정)은 사용자가 채팅으로 "252부터 브랜치 파서 해결해봐"라고
명시적으로 지시해서 진행한다.

## 원인

커밋 `d8c8e25`(`경매 version 컬럼과 SSE 버전 제거`)에서 `auctions` 테이블의 `version` 컬럼을
`schema.sql`에서 제거했다(낙관적 락 → 비관적 행 락 전환). 그런데 그보다 먼저 작성된 테스트들의
`setUp()`이 여전히 `INSERT INTO auctions (..., version) VALUES (..., 1)`로 삽입하고 있어서,
Testcontainers로 새로 뜨는 DB(현재 `schema.sql` 기준, `version` 컬럼 없음)에서 실행하면
`Unknown column 'version' in 'field list'`로 실패한다.

## 범위

아래 6개 테스트 클래스의 `auctions` INSERT 구문에서 `version` 컬럼명과 값만 제거한다. 그 외
코드/로직 변경은 없다.

- `WalletLedgerRepositoryTest`
- `WalletHoldRepositoryTest`
- `WalletCaptureIntegrationTest`
- `WalletTransactionConcurrencyTest`
- `AuctionBidWalletLockOrderConcurrencyTest`
- `StatisticAggregationMySqlIntegrationTest`

`CardPriceServiceTest`에도 같은 패턴의 `version` 컬럼 삽입 코드가 남아 있지만, 이 테스트는
`@DataJpaTest` + `spring.test.database.replace: none`으로 개발자 로컬 MySQL(스키마가
`d8c8e25` 이전 상태로 남아있어 `version` 컬럼이 물리적으로 아직 존재함)에 붙기 때문에 지금
당장 실패하지 않는다. 별도 원인(로컬 DB 시드 데이터 오염)으로 이슈 [#253](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/253)에서 다루므로 이번 작업 범위에서 제외한다.

## 커밋 이력

1. `fix: 경매 version 컬럼 제거로 실패하던 wallet/auction 테스트 setup SQL 수정`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
