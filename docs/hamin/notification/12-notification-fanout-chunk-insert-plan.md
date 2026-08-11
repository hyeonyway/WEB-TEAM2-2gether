# 알림 fan-out batch insert를 청크로 나눠 프리페어드 스테이트먼트 한도 방지

담당: D(임하민). 이슈 #207. [7-notification-fanout-batch-insert.md](7-notification-fanout-batch-insert.md)(#190)에서 추가한 `saveAllIgnoringDuplicates`의 사후 지적 사항을 해소한다.

## 배경

PR #199 코드래빗 리뷰에서 `saveAllIgnoringDuplicates`가 대상 유저 수에 제한을 두지 않는다는 지적을 받았다. `WishlistService.findUserIdsByCardId`는 대상 유저 수 제한이 없고, 이 메서드가 만드는 멀티-VALUES `INSERT IGNORE`는 유저 1명당 `?` 5개를 쓰므로 유저 수가 13,108명을 넘으면 플레이스홀더 총합이 65,540개가 되어 MySQL 서버 사이드 프리페어드 스테이트먼트의 65,535개 한도를 넘는다. 재조회 SELECT(`findByAuctionIdAndTypeAndBidIdAndUserIdIn`, `user_id IN (...)`)도 유저 1명당 `?` 1개라 같은 종류의 한도를 안고 있다(65,535명이 넘어야 걸림).

지금은 JDBC URL에 `useServerPrepStmts`가 없어 클라이언트 사이드 프리페어드 스테이트먼트로 동작해 당장 한도에 걸리지 않지만, 나중에 서버 사이드 프리페어드 스테이트먼트를 켜는 순간 찜 유저가 많은 인기 카드에서만 재현되는 잠복 버그가 된다. 큰 SQL 패킷 자체도 부담이라 청크 처리가 맞는 방향이다.

## 설계

`NotificationService.saveAllIgnoringDuplicates`에서 `userIds`를 고정 크기(10,000명)로 나눠 INSERT IGNORE만 청크별로 반복 실행하고, 재조회 SELECT는 전체 `userIds`를 대상으로 한 번만 실행한다.

- INSERT는 유저 1명당 `?` 5개라 청크 크기 10,000명 기준 청크당 `?` 50,000개로 65,535 한도에 여유 있게 들어간다.
- SELECT(`findByAuctionIdAndTypeAndBidIdAndUserIdIn`)는 유저 1명당 `?` 1개라 65,535명이 넘어야 한도에 걸린다 — INSERT 청크 크기(10,000)보다 훨씬 큰 규모라, 재조회는 청크로 나누지 않고 전체 목록을 한 번에 조회한다(리턴값을 쓰는 라이브 push 경로(`NotificationEventListener.handleAuctionOpened`)가 청크마다 조각난 리스트가 아니라 한 번의 조회 결과를 그대로 쓸 수 있다).
- `NotificationRepository.findByAuctionIdAndTypeAndBidIdAndUserIdIn`의 시그니처는 그대로 둔다(이미 `Collection<Integer>`를 받아 전체 목록을 그대로 넘길 수 있음).
- 마지막 청크가 청크 크기보다 작아도(나머지) 문제없이 처리되도록 `subList` 기반으로 나눈다.
- 청크 개수와 무관하게 반환 순서/개수는 기존과 동일(각 유저당 정확히 1건)해야 한다.

## 작업 항목

- [ ] `NotificationService`에 청크 크기 상수(10,000) 추가, INSERT만 청크 루프로 변경(재조회 SELECT는 전체 목록으로 1회 유지)
- [ ] 청크 경계 테스트 추가: 정확히 청크 크기(10,000명), 청크 크기+1(10,001명 → 2개 청크)에서 INSERT가 청크 수만큼 나가고 최종 결과 개수/유저 매핑이 올바른지 검증
  - 유저 10,000명 이상을 매번 개별 INSERT로 만드는 건 느리므로 테스트용 유저 생성은 `jdbcTemplate.batchUpdate`로 배치 처리한다.

## 범위 밖으로 남긴 것

- `useServerPrepStmts` 활성화 자체 — 이 이슈는 활성화 여부와 무관하게 안전하도록 만드는 것이 목적이고, 성능 튜닝으로 서버 사이드 프리페어드 스테이트먼트를 켜는 결정은 별도 사안.
- `NotificationReconciliationService`(복구 배치) 쪽은 이미 `saveAllIgnoringDuplicates`를 그대로 호출하므로 별도 수정 없이 이 변경의 이점을 그대로 받는다.

> 이 문서는 claude의 도움을 받아 작성하였습니다.
