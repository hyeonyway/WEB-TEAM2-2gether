# 이슈 253 — CardPriceServiceTest DB 격리

담당: 임하민. 이슈: [#253](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/253)
(브랜치 `fix/253-card-price-test-db-isolation`).

`AGENTS.md`상 원래 담당 범위는 `notifications`/`psa`/`upload`/`wishlist`인데, 이번 작업(`card`
패키지 테스트 코드만 수정)은 사용자가 채팅으로 "253 해결하자"고 명시적으로 지시해서 진행한다.

## 원인

`CardPriceServiceTest`는 `@DataJpaTest`인데 `@AutoConfigureTestDatabase`/`@ServiceConnection`
같은 격리 수단이 전혀 없어서, `application-test.yml`의 `spring.test.database.replace: none`
설정 그대로 개발자 로컬에 설치된 실제 `dbidding` DB에 연결된다. 로컬 DB에는 이미 시드 데이터
(`required-data/001-pokemon-card.sql` 등)가 들어있어서, 테스트가 만든 데이터만 기대한
assertion이 로컬 시드 데이터와 섞여 실패했다.

## 수정

이 저장소의 `WalletHoldRepositoryTest`/`WalletLedgerRepositoryTest`가 이미 쓰고 있는 패턴을
그대로 적용했다 — `@Testcontainers` + `@Container @ServiceConnection MySQLContainer` +
`@DataJpaTest(properties = "spring.sql.init.mode=always")`. 매 실행마다 컨테이너로 빈 MySQL을
새로 띄우고 `schema.sql`을 그대로 적용해서, 로컬 DB 상태와 무관하게 항상 깨끗한 스키마에서
테스트가 실행된다.

부수적으로, 이 컨테이너는 현재 `schema.sql` 기준(= `auctions.version` 컬럼 없음)으로 스키마를
만들기 때문에, 테스트 안에 남아있던 `INSERT INTO auctions (..., version)` 삽입도 함께
제거했다(이슈 #252와 동일한 증상 — 로컬 DB 격리를 걷어내기 전에는 로컬 DB에 남아있던 구버전
컬럼 덕에 우연히 통과하고 있었을 뿐, 실제로는 스키마와 안 맞는 코드였다).

## 결과

- `CardPriceServiceTest` 5개 테스트 전부 통과
- 전체 테스트 스위트 실행 결과 남은 실패는 이미 별도 이슈로 분리해둔 것과 정확히 일치 —
  [#254](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/254)(세션 SSE 빈
  누락, 3건), [#255](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/255)
  (통계 집계 로직, 4건). 이번 변경으로 인한 회귀 없음.

## 커밋 이력

1. `fix: CardPriceServiceTest가 로컬 DB 대신 격리된 Testcontainers MySQL을 쓰도록 수정`

> 이 문서는 claude의 도움을 받아 작성하였습니다.
