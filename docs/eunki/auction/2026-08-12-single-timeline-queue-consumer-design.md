# 단일 Timeline Queue · 단일 Consumer 설계

**관련 이슈:** #383  
**연계 이슈:** #323, #351, #365, #384

## 결정

Redis 승인 경로에서 생성하는 모든 경매·지갑 이벤트는 단일 Redis Stream
`auction:timeline-events`에만 기록한다. MySQL projection은 전역적으로 활성화된 consumer 하나가
Redis Stream ID 순서대로 한 건씩 처리한다. Stream sharding과 병렬 consumer는 현재 범위에서 사용하지
않는다.

이 결정은 경매·지갑 상태 전이의 DB projection 순서를 단순하게 유지하기 위한 것이다. 처리량 확장은
의도적으로 뒤로 미루며, backlog를 숨기는 것이 아니라 관측·제한해야 하는 용량 한계로 취급한다.

## 토폴로지와 처리 순서

```text
Redis Lua (승인 상태 전이 + XADD)
                │
                ▼
  auction:timeline-events (단일 durable Stream)
                │
                ▼
auction-timeline-persistence Consumer Group
                │
        leader lock 보유 인스턴스 1개
                │
                ▼
  auction-timeline-single (COUNT 1, 순차 처리)
                │
                ▼
 MySQL inbox 기록 → projection transaction → ACK
```

- producer는 Lua 안에서 Redis 상태 변경과 `XADD`를 함께 실행한다. HTTP 성공 응답은 이 둘이 성공한 뒤에만 반환한다.
- consumer는 `auction:timeline-events:consumer-leader-lock`을 가진 인스턴스 하나만 실행한다.
  consumer 이름도 `auction-timeline-single`로 고정한다.
- `XREADGROUP`는 `COUNT 1`로 읽고, 한 이벤트의 inbox 기록·projection·ACK가 끝난 뒤 다음 이벤트를 처리한다.
- 여러 애플리케이션 인스턴스가 떠도 consumer의 병렬 DB projection은 발생하지 않는다. leader가 바뀌면
  PEL은 같은 consumer group에서 회수한다.

## MySQL과 잠금의 역할

Redis Lua는 실시간 승인 경로의 경합을 직렬화한다. MySQL은 비동기 projection이므로 클라이언트 요청
thread가 DB 행 잠금을 기다리지는 않는다.

다만 MySQL projection 자체의 트랜잭션·잠금이 사라지는 것은 아니다. 단일 consumer가 같은 Stream
이벤트를 순차 처리하므로 이 경로 내부의 지갑·경매 행 잠금 경합은 만들지 않지만, DB 처리량은 여전히
전체 시스템의 지속 가능 TPS 상한이다. 유입량이 이를 넘으면 Stream backlog가 증가한다. 이 상태를
성능 향상으로 오해하지 않으며, Stream 길이·consumer lag·PEL·inbox `ERROR/PENDING`을 모니터링하고
임계치 기반 승인 제한은 별도 작업으로 둔다.

따라서 `walletVersion`은 현재 단일 queue에서 shard 간 순서 역전을 해결하기 위한 장치가 아니다.
이벤트 snapshot의 세대 식별·멱등 projection·향후 복구 검증을 위한 값으로 유지하되, 현재 순서 보장은
전역 Stream ID와 단일 consumer가 담당한다.

## 내구성·실패·재구성

- Redis는 AOF를 활성화한다. `appendfsync everysec`은 최대 약 1초 유실 가능성이 있고, 그보다 강한
  내구성 요구는 `appendfsync always`의 지연 비용을 별도 검증한다.
- consumer는 inbox 수신 기록 자체가 실패한 경우에만 ACK하지 않아 PEL 재전달로 재시도한다.
- projection 또는 이벤트 계약 검증 실패는 inbox에 `ERROR`와 실패 사유를 남긴 뒤 ACK한다. 이 경우
  자동 재시도가 아니라 운영자가 inbox 오류를 해결한 후 Stream을 재생하거나 inbox projection을
  재실행하는 복구 절차를 사용한다.
- ACK 후에는 `XDEL`을 하지 않는다. Stream은 AOF에 남는 재구성 원본이며, 이미 ACK된 이벤트를
  다시 투영해야 하면 새 consumer group을 `0-0`부터 읽거나 운영용 replay 절차를 사용한다.
- Stream 보존 기간과 trim은 아직 자동화하지 않는다. MySQL의 일관된 checkpoint/재구성 절차가
  확정되기 전에는 `XTRIM`으로 원본을 제거하지 않는다. Redis 메모리 증가와 AOF 용량은 운영 지표다.

## 범위 밖

- auctionId 또는 walletId 기반 Stream shard와 병렬 consumer
- shard 간 순서 역전을 전제로 한 wallet projection 병렬화
- backlog 임계치에서 Lua 승인을 거절하는 backpressure
- Stream replay·MySQL snapshot 기반 Redis bootstrap의 자동화
- Stream retention/trim checkpoint 자동화

위 항목은 단일 consumer의 DB 처리량과 Redis 메모리 관측값을 근거로 별도 이슈에서 설계한다.
