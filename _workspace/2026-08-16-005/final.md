# 6.6 Redis 기반 입찰 처리 전환

## 배경

[6.3](Performance-병목-분리-SSE-DB-Lock-Connection-Pool)에서 확정한 핵심 원인은 구조에 있었다. 같은 Auction Row에 `FOR UPDATE` Record Lock을 걸려고 기다리는 Transaction이 그동안 DB Connection을 붙잡고 놓지 않는다. Composite Index([6.7](Performance-Redis-Read-Path-최적화))로 Transaction 안의 조회 비용은 줄였지만, 하나의 Auction 상태를 MySQL Row Lock으로 직렬화하는 구조 자체는 그대로 남아 있었다. Connection Pool을 100/100까지 늘려도 QPS 150 이상에서 평균 latency가 오히려 900~990ms로 나빠졌던 게 그 증거다. 더 많은 요청이 Connection을 얻어 봤자 한 Auction Row가 동시에 처리할 수 있는 Transaction 수 자체는 그대로이기 때문이다.

구조적 한계를 풀려면 입찰 승인 경로에서 MySQL Row Lock을 아예 없애야 했다. 그래서 입찰 승인 자체를 Redis Lua 스크립트로 옮겼다.

## Redis Lua 기반 입찰 승인

`bid-accept.lua`가 경매 상태(현재가·최고 입찰자·버전)와 지갑 잔액을 같은 Redis 인스턴스 안에서 원자적으로 검증하고 갱신한다. Redis는 명령을 단일 스레드로 순서대로 처리한다. 같은 경매에 여러 입찰이 몰려도 Lua 스크립트 실행이 알아서 직렬화되는 이유다. MySQL의 `SELECT ... FOR UPDATE`처럼 Transaction이 잠금을 오래 붙들고 있는 구조가 아니라, 스크립트 하나가 밀리초 이하로 끝나고 다음 요청으로 넘어간다.

입찰 승인 응답은 이 Lua 스크립트 실행 결과만으로 만들어진다. HTTP 요청 하나당 MySQL 접근이 아예 없다.

## MySQL 승인 경로 제거

코드에서는 `BidExecutor` 구현체를 프로필로 갈라 넣었다.

- `DbBidExecutor`(`@Profile("!redis")`) — 기존 경로. 경매 row를 `findByIdForUpdate()`로 직접 잠그고 MySQL Transaction 안에서 입찰을 승인한다.
- `RedisBidExecutor`(`@Profile("redis")`) — 새 경로. `bid-accept.lua` EVAL 하나로 승인하고 MySQL 반영은 Redis Stream을 거쳐 완전히 비동기로 처리한다.

`redis` 프로필이 켜진 배포에서는 후자가 뜬다. 입찰 승인 경로에 MySQL이 전혀 관여하지 않는다. MySQL 쪽 반영(Bid 테이블 적재, 지갑 정산 등)은 `AuctionBidStreamConsumer`가 Redis Stream을 순서대로 소비하면서 별도로 처리한다. 승인은 즉시, 영속화는 그 뒤에 비동기로 따라온다.

## HikariCP / Hot Auction 변화

실제 효과는 9차 최종 테스트에서 확인했다. 8차(RAM 증설 검증, `local-sse,sse-virtual-threads` 프로필, `DbBidExecutor` 경로)와 9차(`redis` 단일 프로필, `RedisBidExecutor` 경로)를 같은 `bid-only-load 핫경매집중` 시나리오로 나란히 비교했다.

<table header-row="true">
<tr>
<td>지표</td>
<td>8차(DbBidExecutor)</td>
<td>9차(RedisBidExecutor)</td>
</tr>
<tr>
<td>Hikari active 최댓값(6개 시나리오 전체)</td>
<td>매 시나리오 30/30(포화)</td>
<td>10/30을 넘지 않음</td>
</tr>
<tr>
<td>핫경매집중 p95</td>
<td>52,506ms</td>
<td>90~140ms대(QPS400까지)</td>
</tr>
<tr>
<td>핫경매집중 max</td>
<td>60,037ms(클라이언트 타임아웃)</td>
<td>9,674ms</td>
</tr>
<tr>
<td>핫경매집중 http_req_failed</td>
<td>6.29%</td>
<td>0.00%</td>
</tr>
</table>

8차 문서는 이 핫경매집중 지연을 "RAM과 무관한 구조적 DB Lock 문제, 해결 안 됨"으로 결론지었다. 그 결론 자체는 `DbBidExecutor` 경로에서라면 맞는 말이다. 다만 이 프로젝트에서 영구적으로 못 푸는 문제는 아니었다. `RedisBidExecutor` 경로는 애초에 MySQL Row Lock을 거치지 않으니 같은 조건에서 이 병목 자체가 발생할 수 없다.

Hikari 소진도 같은 이유로 사라졌다. 8차까지 "RAM 증설로도 못 푼 별도 과제"로 남아 있던 Connection Pool 포화도 입찰 승인 경로에서 MySQL 커넥션을 아예 안 쓰게 되니 자연히 풀렸다.

## 남은 질문

`DbBidExecutor`가 완전히 죽은 코드인지, 로컬 개발이나 `redis` 프로필을 안 켠 배포용으로 일부러 남겨둔 것인지는 이번 조사에서 확인하지 않았다. Redis 승인 경로 자체의 새로운 리스크(Redis 장애 시 대응, Stream 적체로 인한 MySQL 반영 지연 등)는 [6.7](Performance-Redis-Read-Path-최적화)과 [6.8](Performance-9차-최종-부하-테스트)에서 이어서 다룬다.

> 이 문서는 codex의 도움을 받아 작성하였습니다

<!-- HUMANIZE-SUMMARY v1.6.1
run_id: 2026-08-16-005
genre: 리포트 (기술 위키)
metrics:
  char_in: 2118
  char_out: 2073
  change_rate: 12.4%
  self_check: 6/6
  grade: A
categories:  # before → after
  A-1 "~에 대한" 번역투: 1 → 0
  A-11 "~를 위해" 목적절: 1 → 0
  A-18 좌향 관형절 3어절+: 2 → 0
  C-11 연결어미/조사 뒤 쉼표: 4 → 0
  H-3 메타 진입 지시사("이 전환은/이 구조적"): 3 → 0
  J-2 따옴표 강조(비인용): 2 → 0
  em-dash 삽입절 반복: 4 → 1
self_check:
  - 고유명사·수치·인용·표·링크·코드 100% 보존: ✅ (표 4행 수치, 클래스명, [6.3]/[6.7]/[6.8] 링크, 백틱 전부 원형)
  - 변경률 30% 이하: ✅ (12.4%)
  - 장르 이탈 없음: ✅ (기술 리포트 유지, 표/불릿 구조 그대로)
  - register 보존: ✅ (~다체 격식 유지)
  - S1 잔존 0건: ✅
  - 인공 표현 추가 없음: ✅ (비유·수사 무추가)
highlights:
  - id: A-1 / A-18 / J-2
    before: "확정한 핵심 원인은 \"동일 Auction Row에 대한 FOR UPDATE Record Lock을 기다리는 Transaction이 DB Connection을 오래 붙잡는\" 구조였다."
    after: "확정한 핵심 원인은 구조에 있었다. 같은 Auction Row에 FOR UPDATE Record Lock을 걸려고 기다리는 Transaction이 그동안 DB Connection을 붙잡고 놓지 않는다."
  - id: C-11
    before: "Redis는 명령을 단일 스레드로 순서대로 처리하기 때문에, 같은 경매에 여러 입찰이 몰려도 …"
    after: "Redis는 명령을 단일 스레드로 순서대로 처리한다. 같은 경매에 여러 입찰이 몰려도 … 직렬화되는 이유다."
  - id: H-3 / A-15
    before: "이 전환은 코드 레벨에서 BidExecutor 구현체를 프로필로 가르는 방식으로 들어갔다."
    after: "코드에서는 BidExecutor 구현체를 프로필로 갈라 넣었다."
  - id: C-11 (주어 뒤 쉼표)
    before: "남아 있던 Connection Pool 포화가, 입찰 승인 경로에서 … 자연히 해소됐다."
    after: "남아 있던 Connection Pool 포화도 입찰 승인 경로에서 … 자연히 풀렸다."
  - id: em-dash
    before: "별도로 처리한다 — 승인은 즉시, 영속화는 그 뒤에 비동기로 따라오는 구조다."
    after: "별도로 처리한다. 승인은 즉시, 영속화는 그 뒤에 비동기로 따라온다."
residual_findings: (없음)
grade_reason: "A — S1 잔존 0건, 변경률 12.4%, 자체검증 6항 통과. 표·수치·코드·위키 링크 무변경, 기술 리포트 register 그대로."
-->
