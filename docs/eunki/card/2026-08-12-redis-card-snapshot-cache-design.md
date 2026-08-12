# Redis 카드 Snapshot Read-Through Cache 설계

**관련 이슈:** #395  
**선행 이슈:** #392

## 목적

Redis-first 경매 생성은 카드명·세트명·PSA 등급·언어·썸네일 snapshot이 필요하다. 카드는 경매·지갑·주문과
달리 Redis가 승인 기준인 데이터가 아닌 저빈도 참조 데이터다. Redis 메모리 1GB 환경에서 전체 카드를
전수 적재하지 않고, 실제 요청된 카드만 MySQL에서 읽어 Redis cache에 보관한다.

## 키와 값 계약

키는 `card:cache:{cardId}`이며 Redis Hash로 저장한다.

| 필드 | 설명 |
| --- | --- |
| `name` | 카드명 |
| `setName` | 카드 세트명 |
| `psaGrade` | PSA 등급, 없으면 빈 값 |
| `language` | 카드 언어, 없으면 빈 값 |
| `thumbnailUrl` | 대표 이미지 경로 |

각 Hash에는 TTL을 적용한다. 기본값은 24시간(`86400`초)이며 application property로 조절한다.

## Read-through 흐름

```text
Redis 경매 생성
       │
       ▼
HGETALL card:cache:{cardId}
       │
 ┌─────┴─────┐
 hit          miss
 │             │
 ▼             ▼
snapshot    MySQL card_metadata 조회
반환             │
                 ▼
             HSETNX + EXPIRE
                 │
                 ▼
             snapshot 반환
```

동시 miss에서는 여러 요청이 MySQL을 조회할 수 있으나, `HSETNX`로 최초 snapshot을 보존한다. 카드 참조는
저빈도이므로 distributed lock은 두지 않는다.

## 갱신·실패 정책

- 카드 생성·수정 시 cache key를 삭제한다. 다음 요청이 MySQL의 최신 snapshot을 다시 적재한다.
- Redis cache 저장 실패는 MySQL 원본 저장을 되돌리지 않는다. 다음 cache miss가 다시 적재한다.
- 존재하지 않는 cardId는 기존 `CardException.notFound()`를 반환하고 cache를 만들지 않는다.
- Redis 장애는 Redis-first 경매 승인 경로의 오류로 처리한다. 경매 생성에서 MySQL 단독 fallback으로
  계속 승인하지 않는다.

## 경매 state와의 관계

경매 생성 Lua는 cache에서 가져온 card snapshot을 `auction:state:{auctionId}`에 복사한다. 따라서 생성 후
card cache TTL이 만료되거나 key가 삭제돼도 기존 경매의 표시 및 즉시 낙찰은 경매 state snapshot으로 동작한다.

## 검증

- cache hit에서는 MySQL 카드 조회가 발생하지 않는다.
- cache miss에서는 MySQL snapshot 조회 후 Hash와 TTL이 생성된다.
- 존재하지 않는 카드는 cache에 저장하지 않고 기존 예외를 반환한다.
- Redis 경매 생성은 cache snapshot으로 PSA 검증 및 Lua 입력을 구성한다.
