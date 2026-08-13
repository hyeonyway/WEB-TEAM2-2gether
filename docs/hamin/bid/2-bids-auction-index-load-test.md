# bids 인덱스 부하테스트 기록

관련 이슈: [#410](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/410)

k6 + Docker(Prometheus/Grafana/cAdvisor)로 `t4g.micro` 스펙(2 vCPU/903MB)을 재현한
**별도 로컬 부하테스트 환경**(`dbidding-loadtest-local`, 이 저장소 밖)에서 진행한 조사 기록이다.
`watch-db-locks.sh`, `summarize-db-locks.sh`, `run-k6-with-diag.sh` 같은 스크립트와
`db-lock-diag-results/*.log`, `k6-results/*.json` 파일은 전부 그 환경에만 있고 이 저장소에는
없다 — 재현하려면 해당 환경이 필요하다.

테스트마다 아래 표에 한 줄씩 append. 컬럼 뜻:

- **프로필**: SSE전달 / 입찰처리 / SSE브로드캐스트 (부하테스트 환경 README의 3축 참고)
- **변수**: RATE / DURATION / AUCTION_IDS / VUs(pre→max)
- **입찰 결과**: bid_accepted_or_contended rate(성공+경합 인정) / bid_accepted rate(순수 성공)
- **DB**: HikariCP active/pending 피크, MySQL 컨테이너 CPU 피크(코어, cap=2)
- **backend CPU**: backend 컨테이너 CPU 피크(코어, cap=2)
- **SSE**: 경매/알림 SSE 연결 수(시도), 연결 성공률
- **비고**: 특이사항, 원인 분석 링크 등

| 실행시각 | k6 결과파일 | 프로필 | RATE/기간/경매수 | VUs(pre→max) | bid_accepted_or_contended | bid_accepted | HikariCP active/pending 피크 | MySQL CPU 피크 | backend CPU 피크 | SSE 연결 수(경매/알림) | SSE 연결성공률(경매/알림) | 비고 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2026-08-12 23:33:21 | `20260812T233321-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 200 / 1m / 3000009 | 300→3000 | 13.9% (828/5950) | 3.7% (220/5911) | 30 / 20 | 1.95 core (cap 2 core, CFS 스로틀링 발생) | 1.50 core | 300 / 41,560 | 100% / 99.88% | 단일 auction row에 200rps 집중 → DB row lock 경쟁으로 HikariCP 풀 고갈·MySQL CPU 포화. `innodb_deadlock_detect=OFF`로도 동일 재현되어 데드락 감지 오버헤드는 배제. SSE/서버 자체는 정상(예외 0건). 다음: RATE 낮춰서 감당 가능한 처리량 탐색 중. |
| 2026-08-13 00:00:53 | `20260813T000053-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 150 / 1m / 3000009 | 300→3000 | 15.1% (776/5124) | 5.4% (277/5124) | 30 / 20 | 1.99 core (cap 2 core, CFS 스로틀링 여전히 발생, ~9.9/s) | 1.47 core | 300 / 41,491 | 100% / 100% | RATE 200→150으로 낮췄지만 HikariCP·MySQL CPU 포화는 그대로(변화 없음) — 단일 row 직렬화라 임계 RATE가 150보다 훨씬 낮은 듯. 다음: RATE 더 낮춰서(예: 50 이하) 재시도 필요. |
| 2026-08-13 00:05:08 | `20260813T000508-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 50 / 1m / 3000009 | 300→3000 | 35.1% (1162/3315) | 28.9% (959/3315) | **3 / 0** | **0.87 core (스로틀링 거의 없음, ~0.5/s)** | 1.92 core | 300 / 72,120 | 100% / 100% | **HikariCP 풀이 처음으로 고갈되지 않음(pending=0)**, MySQL CPU도 cap 대비 여유 큼. 150→50 사이에 임계점이 있는 것으로 보임. 다음: 임계 RATE를 더 좁히려면 50~150 사이(예: 80, 100) 재시도. |
| 2026-08-13 00:08:15 | `20260813T000815-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 100 / 1m / 3000009 | 300→3000 | 16.1% (606/3754) | 6.6% (248/3754) | 30 / 20 (완전 고갈) | 1.99 core (스로틀링 ~9.8/s, RATE=200과 거의 동일) | 1.52 core | 300 / 42,378 | 100% / 100% | RATE=100에서 이미 RATE=200과 같은 수준으로 풀 고갈·CPU 포화 재현 — 50→100 사이에서 급격히 무너짐(cliff). 다음: 50~100 사이(예: 60, 70, 80)로 더 좁혀서 임계 RATE 특정. |
| 2026-08-13 00:13:56 | `20260813T001356-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 75 / 1m / 3000009 | 300→3000 | 15.6% (683/4385) | 6.4% (279/4385) | 30 / 21 (완전 고갈) | 1.98 core (스로틀링 ~9.8/s) | 1.77 core | 300 / 44,209 | 100% / 100% | RATE=75도 이미 고갈됨 — cliff가 50~75 사이로 더 좁혀짐(75/100/150/200 전부 거의 동일한 포화 양상). 다음: 50~75 사이(예: 55, 60, 65)로 더 좁혀서 임계 RATE 특정. |
| 2026-08-13 00:17:10 | `20260813T001710-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 60 / 1m / 3000009 | 300→3000 | 21.1% (826/3914) | 11.9% (466/3914) | 30 / 20 (완전 고갈) | 1.81 core (스로틀링 ~7.76/s, 75~200보다는 약간 낮음) | 1.75 core | 300 / 80,653 | 100% / 100% | RATE=60도 여전히 풀 고갈되지만 CPU/스로틀링은 조금 완화됨(75 대비). cliff가 50~60 사이로 더 좁혀짐. 다음: 55 정도로 더 좁혀서 임계 RATE 특정. |
| 2026-08-13 00:22:15 | `20260813T002215-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 55 / 1m / 3000009 | 300→3000 | 21.2% (767/3615) | 12.8% (463/3615) | 30 / 14 (여전히 active 꽉 참, pending은 60보다 낮음) | 1.85 core (스로틀링 ~8.08/s) | 1.78 core | 300 / 77,636 | 100% / 100% | RATE=55도 여전히 고갈(active=30). 50은 멀쩡했고 55부터 무너지므로 **임계 RATE는 50~55 사이**로 특정됨. 이 정도면 실용적으로 "50 rps 이하가 안전선"으로 결론 내려도 될 듯. |
| 2026-08-13 00:27:43 | `20260813T002743-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 52 / 1m / 3000009 | 300→3000 | 19.4% (666/3435) | 10.6% (365/3435) | 30 / 21 (완전 고갈) | 1.82 core (스로틀링 ~7.9/s) | 1.71 core | 300 / 76,204 | 100% / 100% | RATE=52도 이미 고갈됨 — **임계 RATE는 50~52 사이로 더 좁혀짐**. 50이 확인된 안전 상한, 52부터는 무너지는 것으로 사실상 확정 가능. → 다음 두 행에서 뒤집힘, 아래 "50 rps 불안정성 조사" 참고. |
| 2026-08-13 00:31:02 | `20260813T003102-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 50 / 1m / 3000009 | 300→3000 | 24.6% (817/3315) | 15.0% (497/3315) | 30 / 20 (완전 고갈, 00:05 재실행인데 이번엔 고갈) | 1.84 core (스로틀링 ~8.0/s) | 1.74 core | 300 / 80,948 | 100% / 100% | **같은 RATE=50인데 00:05 실행과 결과가 정반대** — bid 락 대기 합이 45s→1,052s(15s 구간 최대)로 23배, DB 실제 커밋 처리량은 오히려 87.5/s→64.3/s로 낮아짐. 지갑 락 대기는 두 실행이 동일(~0.5s)이라 지갑 쪽 원인은 배제. "50=안전선" 결론 보류. |
| 2026-08-13 00:39:20 | `20260813T003920-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 50 / 1m / 3000009 | 300→3000 | 17.7% (588/3315) | 7.75% (257/3315) | 30 / 20+ (완전 고갈, `watch-db-locks.sh`로 실시간 캡처) | 1.96 core | 1.70 core | 300 / 73,060 | 100% / 100% | **RATE=50 3번째 실행도 고갈 — 2/3이 고갈**이라 00:05 실행이 예외였던 쪽. `db-lock-diag-results/20260813T003924-db-locks.log`에서 실제 잠긴 쿼리 확인: `SELECT ... FROM auctions WHERE id=3000009 FOR UPDATE`, PK에 순수 X record lock(gap 아님) — 인덱스/락 확산 문제 아님, 순수 단일 row 직렬화. 자세한 원인 후보는 아래 섹션 참고. |
| 2026-08-13 00:50:30 | `20260813T005030-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 50 / 1m / **2000002(새 경매, bid 이력 0건)** | 300→3000 | 35.1% (1162/3314) | 28.5% (946/3314) | **5 / 0** | **0.57 core** | 1.94 core | 300 / 64,025 | 100% / 100% | **새 경매로 바꾸니 완전히 클린** — Com_commit 피크 87.7/s(역대 최고), end-to-end median 92.5ms. 3000009(6,800+건 누적)와 정반대. → 원인이 "임계 이용률 불안정성"이 아니라 "경매별 누적 입찰 수"라는 결정적 증거. |
| 2026-08-13 00:57:29 | `20260813T005729-auction-bid.json` | Redis pub/sub / DB row lock / 스레드풀 (`up-all-redis-sse.sh`) | 50 / 1m / 2000002(1,162→2,116건 누적) | 300→3000 | 35.6% (1179/3315) | 28.8% (954/3315) | **3 / 0** | **0.64 core** | 1.92 core | 300 / 64,863 | 100% / 100% | 2,100건 누적된 상태에서도 여전히 클린(Com_commit 88/s, 역대 최고). **티핑포인트는 2,100~7,000건 사이** — 근본 원인은 아래 "원인 확정" 섹션 참고. |

## 50 rps 불안정성 조사 (2026-08-13)

RATE=50을 3번 돌렸는데 1번은 클린(00:05), 2번은 고갈(00:31, 00:39) — 같은 RATE에서 결과가 갈린다. 배제한 것과 남은 후보:

**배제됨**
- gap lock/인덱스 누락 → `db-lock-diag-results/20260813T003924-db-locks.log`에서 실제 쿼리 확인, `auctions` PK에 순수 X record lock 하나뿐
- `innodb_deadlock_detect` 오버헤드 → OFF로 바꿔도 동일 재현 (앞서 확인)
- wallet_holds 누적(유저당 900~1200건) → `performance_schema.events_statements_summary_by_digest` 확인 결과 지금도 평균 0.1~0.7ms로 빠름
- backend JVM GC/힙 누수 → 고갈 실행의 G1 Old Gen 사용량이 오히려 더 낮았음

**남은 후보 (우선순위 순, → 결과)**
1. 자기강화적 큐잉(임계 이용률 근처의 불안정성) → 부차적 요인으로 강등(아래 확정 원인의 증상일 뿐)
2. 세션 전체에 걸쳐 트랜잭션당 처리 시간이 서서히 증가 → **확정** (검증: 새 경매 2000002로 재현, 아래 참고)
3. Redis pub/sub 잔여 부하 누적 → 미확인 상태로 종료(원인이 이미 확정돼서 추가 조사 안 함)

## 원인 확정: `bids` 조회 쿼리의 filesort가 auction별 누적 입찰 수에 선형으로 느려짐

**검증**: 새 경매 2000002(bid 이력 0건)로 RATE=50을 2번 실행 → 둘 다 클린 (HikariCP active 3~5/pending 0, MySQL CPU 0.57~0.64 core, Com_commit 87.7~88/s 역대 최고). 3000009(6,800+건 누적)와 정반대 결과로, "경매별 누적 입찰 수"가 진짜 변수임을 확정.

**메커니즘** (`performance_schema.events_statements_summary_by_digest` + `EXPLAIN`으로 확인):

```sql
-- 최고가 조회 (① 락 안 / ② 읽기 경로, 둘 다 status만 다름)
SELECT ... FROM bids WHERE auction_id=? AND status=?/IN(...)
  ORDER BY bid_price DESC, created_at LIMIT 1
-- ③ 입찰 내역 페이지네이션 (읽기 경로)
SELECT ... FROM bids WHERE auction_id=?
  ORDER BY created_at DESC, id DESC LIMIT ?
```

`EXPLAIN` 결과 셋 다 `key: idx_bids_auction_id`, `Extra: Using filesort`, `rows: 7080`(3000009 기준). `idx_bids_auction_price(auction_id, bid_price)` 인덱스가 있는데도 `status`(①②) 또는 `created_at`(③)이 어떤 인덱스에도 같이 안 걸려 있어서, **그 auction_id에 해당하는 모든 row를 긁어와 메모리에서 재정렬**한 뒤 `LIMIT`으로 1~5개만 자름. 실측: 3000009 기준 평균 1.6~16.4ms, 최대 119~613ms, 호출당 평균 2,638~2,676행 스캔(반환은 1~5행뿐).

①(`highestBid()`)은 **`auctions` row의 `FOR UPDATE` 락을 쥔 트랜잭션 안에서** 실행되므로 락 보유 시간이 누적 입찰 수만큼 늘어나고, 단일 row 직렬화 구조라 그 증가분이 큐 폭증으로 증폭됨. ②③은 락 밖(읽기 경로)이라 락 보유엔 안 걸리지만 요청 자체의 latency를 직접 늘림. 티핑포인트는 이 환경(2 vCPU, RATE=50) 기준 **대략 2,100~7,000건 사이**.

### 코드 위치

| # | 메서드 | 위치 | 락 안/밖 |
|---|---|---|---|
| ① | `findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc` | `DbBidExecutor.java:290-292`, 호출: `DbBidExecutor.java:85` | **락 안** (`auctions FOR UPDATE` 보유 중) |
| ② | `findFirstByAuctionIdAndStatusInOrderByBidPriceDescCreatedAtAsc` | `AuctionQueryService.java:357` | 락 밖 (읽기) |
| ③ | `findByAuctionIdOrderByCreatedAtDescIdDesc` | `AuctionQueryService.java:272` | 락 밖 (읽기) |

메서드 시그니처는 전부 `backend/src/main/java/com/dbidding/auction/repository/BidRepository.java` (Spring Data 파생 쿼리).

### 인덱스 처방 및 ORDER BY 필요성 검토

`Bid.java`의 `markOutbid()`/`markWon()`을 보면 새 입찰이 들어올 때마다 이전 `LEADING` bid를 즉시 `OUTBID`로 바꾸고, 낙찰 시에만 `LEADING`→`WON`으로 전환한다. 즉 **경매 하나당 `status=LEADING`인 row는 항상 0개 아니면 1개, `status IN (LEADING, WON)`도 항상 0개 아니면 1개** — 애초에 여러 개 나올 수 없다.

- **①②는 `ORDER BY`가 사실상 장식**: 결과가 원래 1개뿐이라 정렬할 대상이 없음. 느린 이유는 "정렬을 못해서"가 아니라 "`status` equality 필터에 인덱스가 없어서 정렬할 필요도 없는 후보를 다 긁어모으고 있는 것". → `CREATE INDEX idx_bids_auction_status ON bids (auction_id, status);` 하나면 바로 그 1개 row로 꽂혀서 `bid_price`/`created_at` 정렬 자체가 필요 없어짐. `idx_bids_auction_price`는 이 인덱스 추가 후 실질적으로 안 쓰이게 될 가능성 높음(사용 여부 확인 후 정리 대상).
- **③은 진짜 정렬이 필요함**: 입찰 내역을 최신순으로 페이지네이션하는 목록 조회라 결과가 여러 건이고 시간순 정렬이 실제 요구사항. → `CREATE INDEX idx_bids_auction_created ON bids (auction_id, created_at DESC, id DESC);`로 정렬을 인덱스가 대신하게(covering) 해야 함.

**결론**: 근본 원인은 RATE나 임계 이용률이 아니라 **`bids` 테이블에 `(auction_id, status)` / `(auction_id, created_at DESC, id DESC)` 복합 인덱스가 없는 것**. 다음 검증 스텝은 이 두 인덱스를 추가한 뒤 3000009(7,000+건 누적된 경매)로 RATE=50~200을 다시 돌려 filesort가 없어지고 문제가 해소되는지 확인하는 것.

---

# 인덱스 수정 후 (`fix/410-bids-auction-index-filesort` 브랜치)

`schema.sql`에 `idx_bids_auction_status (auction_id, status)`, `idx_bids_auction_created (auction_id, created_at DESC, id DESC)` 추가하고 안 쓰는 `idx_bids_auction_price` 제거. `required-data`의 `003`/`004` OPEN 경매도 즉시낙찰가 NULL·종료시각 7일로 수정(즉시낙찰 조기 종료 방지). 2000002에 SQL로 7,194건 강제 누적(`LEADING` 1개로 정리) 후 재검증.

## EXPLAIN 비교 (수정 전 vs 후)

| 쿼리 | 수정 전 (3000009, 7,080건) | 수정 후 (2000002, 7,194건) |
|---|---|---|
| `highestBid()` (`status='LEADING'`, ①락 안) | `key: idx_bids_auction_id`, `rows: 7080`, `Extra: Using filesort` | `key: idx_bids_auction_status`, **`rows: 1`**, `Extra: Using filesort`(정렬 대상이 1건이라 사실상 무비용) |
| 입찰 내역 페이지네이션 (③읽기 경로) | `key: idx_bids_auction_id`, `rows: 7080`, `Extra: Using filesort` | `key: idx_bids_auction_created`, **`rows: 3`**, **`Extra: Using index`**(커버링 인덱스, filesort 완전히 사라짐) |

`rows`(스캔량)가 7,080 → 1~3으로 줄어든 게 핵심 — `status`/`created_at`이 인덱스에 없어서 auction마다 전체 row를 훑어야 했던 게, 인덱스 추가 후 바로 필요한 1~3건으로 꽂히도록 바뀜.

| 실행시각 | k6 결과파일 | 프로필 | RATE/기간/경매수 | VUs(pre→max) | bid_accepted_or_contended | bid_accepted | HikariCP active/pending 피크 | MySQL CPU 피크 | backend CPU 피크 | SSE 연결 수(경매/알림) | SSE 연결성공률(경매/알림) | 비고 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2026-08-13 01:43:47 | `20260813T014347-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 (프로필 기본값으로 복귀됨) | 200 / 1m / 2000002(0→194건 누적) | 300→1,674 | 11.5% (1323/11526) | 1.66% (191/11526) | 33 / 20 (완전 고갈) | 0.56 core (수정 전 동일 과부하 상황의 1.95~1.99 core보다 훨씬 낮음) | 1.96 core | 300 / 41,781 | 100% / 100% | 갓 리셋한 fresh row에도 RATE=200은 여전히 풀 고갈 — **인덱스 수정과 무관한 정상 현상**(단일 row 직렬화의 물리적 상한은 원래도 RATE 50~55 수준이라 200은 그걸 4배 넘음). MySQL CPU는 훨씬 낮게 유지됨(filesort가 없어져서 과부하여도 DB가 덜 바쁨)이 **backend CPU는 이미 1.96 core로 포화** — 병목이 DB에서 backend로 넘어간 첫 신호. |
| 2026-08-13 01:55:33 | `20260813T015533-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | 50 / 1m / **2000002(7,194건 누적, SQL로 강제 적재)** | 300→3000 | 36.4% (1207/3315) | **30.7% (1018/3315, 역대 최고)** | **2 / 0** | **0.54 core** | 1.98 core | 300 / 64,264 | 100% / 100% | **인덱스 수정 검증 성공** — 수정 전 3000009(~7,080건, RATE=50)는 30/20 완전 고갈·1.84~1.99 core였는데, 같은 규모(7,194건) 누적 상태에서 완전히 클린. Com_commit 피크 90.7/s(역대 최고), end-to-end median 83ms. **DB는 클린한데 backend CPU는 이미 1.98 core로 포화** — DB 원인은 해결됐지만 backend가 다음 병목. |
| 2026-08-13 02:25:02 | `20260813T022502-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | **70** / 1m / 2000002(7,194건+ 누적) | 300→3000 | 30.1% (1361/4515) | 23.0% (1040/4515) | **5 / 0** | **0.77 core** | 1.95 core | 300 / 63,253 | 100% / 100% | **수정 전 임계치(50~55rps)를 넘는 RATE=70도 완전히 건강함** — HikariCP 고갈 없음, Com_commit 피크 111.2/s(역대 최고 경신), end-to-end median 85ms. 인덱스 수정으로 안전 처리량 상한이 70rps 이상으로 올라간 것 확인. 다만 **backend CPU는 이미 1.95 core로 캡에 근접** — 아래 "backend CPU 병목 발견" 섹션 참고. |
| 2026-08-13 02:28:45 | `20260813T022845-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | **100** / 1m / 2000002(7,194건+ 누적) | 300→3000 | 23.8% (1505/6316) | 15.5% (976/6316) | **6 / 0** | **1.00 core** | 1.97 core | 300 / 58,487 | 100% / 100% | 수정 전 RATE=100은 30/20 완전 고갈이었는데 지금은 여전히 건강함 — Com_commit 피크 **138.4/s (역대 최고 재경신)**, end-to-end median 100ms. `backend CPU 1.97 core`로 이미 캡 수준. |
| 2026-08-13 02:33:01 | `20260813T023301-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | **150** / 1m / 2000002(7,194건+ 누적) | 300→3000 | 16.8% (1569/9314) | 7.3% (684/9314) | **30 / 20 (main phase 진입 직후 ~15초간 일시 고갈) → 3 / 0 (이후 자연 회복)** | 1.52 core | 1.98 core | 300 / 56,231 | 100% / 100% | 수정 전과 다른 패턴 — **끝까지 안 풀리는 고갈이 아니라 시작 직후 잠깐 몰렸다가 스스로 회복**. Com_commit 피크는 오히려 **177.2/s로 역대 최고 경신**. end-to-end median 192ms로 RATE=100(100ms)보다 확실히 나빠짐. 다음: "초반 버스트 취약성"과 "지속 가능한 처리량 한계"를 구분해서 봐야 할 듯. |
| 2026-08-13 02:42:28 | `20260813T024228-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | **175** / 1m / 2000002(7,194건+ 누적) | 300→3000 | 13.6% (1467/10811) | 4.3% (467/10811) | **30 / 20~21 (main phase 시작~끝까지 계속 고갈, 회복 안 됨)** | 1.67 core | 1.94 core | 300 / 54,003 | 100% / 100% | RATE=150은 15초 뒤 회복됐는데 175는 **끝까지(55초+) 안 풀림**. Com_commit 피크는 188.5/s(역대 최고)까지 찍혔지만 그 수준을 유지하지 못하고 유입(175/s)에 못 따라잡힘. end-to-end median 476ms로 급격히 나빠짐. **진짜 상한은 150~175 사이**로 좁혀짐. |
| 2026-08-13 02:46:11 | `20260813T024611-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | **160** / 1m / 2000002(7,194건+ 누적) | 300→3000 | 15.5% (1535/9896) | 6.2% (613/9896) | **30 / 0~19 (몰림↔풀림 반복, 완전 고갈도 완전 회복도 아님)** | 1.60 core | 1.94 core | 300 / 54,226 | 100% / 100% | 150(완전 회복)과 175(완전 고갈) 사이 **정확히 경계 지점**. Com_commit 피크 184.1/s(150의 177.2와 175의 188.5 사이), end-to-end median 327ms로 수치도 정확히 중간. **진짜 경계는 150~160 사이**로 더 좁혀짐. |
| 2026-08-13 02:54:54 | `20260813T025454-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | **155** / 1m / 2000002(7,194건+ 누적) | 300→3000 | 15.2% (1464/9615) | 6.0% (580/9615) | **30 / 21→5→0→22 (mainEnd 직전까지 몰려있음, 회복 아님)** | 1.82 core | 1.94 core | 300 / 61,933 | 100% / 100% | mainStartTime(+35s)+duration(60s)=+95s가 실제 부하 종료 시점인데, 마지막 파도(pending=22)가 **+86~96s로 그 시점을 걸쳐서** 발생 — pending이 0으로 떨어진 건 +101s(부하가 이미 멈춘 뒤). "두 번 파도 후 회복"이 아니라 **부하가 끝날 때까지 계속 몰려있었고 회복 여부는 확인 안 됨**(이전 기록 정정). **155는 150보다 확실히 더 나쁨**. |
| 2026-08-13 02:58:01 | `20260813T025801-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | **150 (재현 테스트)** / 1m / 2000002(7,194건+ 누적) | 300→3000 | 17.6% (1636/9316) | 7.6% (710/9316) | **30 / 20 (+80~90s에만, mainEnd(+95s) 직후 0)** | 1.64 core | 1.97 core | 300 / 63,928 | 100% / 100% | 150 2회차 — 1회차(median 192ms, Com_commit 177.2/s)와 거의 동일(median 206ms, Com_commit 174.1/s)하게 재현됨. **다만 diag 로그의 `trx_in_lock_wait`는 이번에도 +37~92s 내내 흔들려서**, 아래 caveat 참고 — "150이 완전히 조용하다"는 뜻이 아니라 "Hikari pending 기준으로 반복 가능하게 양호하다"는 뜻으로 정정. |
| 2026-08-13 03:05:21 | `20260813T030521-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | **130** (backend 재시작 전, 오늘 15번째+ 누적 테스트) / 1m / 2000002(7,194건+ 누적) | 300→3000 | 16.5% (1335/8114) | 7.6% (618/8114) | **30 / 20 (두 번의 파도: +45~55s, +75~85s)** | 1.63 core | 1.94 core | 300 / 59,676 | 100% / 100% | RATE는 150보다 낮은데 median 346ms로 150(192/206ms)보다 나쁨 — backend CFS 스로틀링 8.5/s로 MySQL(1.63 core)보다 훨씬 심하게 포화됨. 아래 "backend CPU 병목 발견" 섹션 참고. |
| 2026-08-13 03:22:49 | `20260813T032249-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | **130 (backend 방금 재시작, 콜드 상태)** / 1m / 2000002(7,194건+ 누적) | 300→3000 | 11.3% (835/7389) | 1.5% (113/7389) | (latency로 심각도 확인, 상세 미조회) | (미조회) | 1.78 core | 300 / (미조회) | (미조회) | **오히려 훨씬 나빠짐**(median 2,965ms!) — 메모리 누수 해소 효과보다 **JIT 콜드 스타트 효과가 압도**. 재시작 직후라 JIT가 인터프리터/C1 단계라 원래보다 몇 배 느림. 이 결과로는 메모리 누수 이론을 검증할 수 없음(변수가 섞임) → 웜업 필요. |
| 2026-08-13 03:26:00 | `20260813T032600-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | **100 (버리는 웜업 라운드)** / 1m / 2000002(7,194건+ 누적) | 300→3000 | 20.9% (1318/6315) | 10.9% (687/6315) | (웜업용, 비교 대상 아님) | (웜업용) | 1.93 core | 300 / (미조회) | (미조회) | median 181ms — 콜드(2,965ms)보다 크게 개선됐지만 재시작 전 RATE=100(median 100ms)에는 아직 못 미침. 순수 웜업용, 비교 데이터로 안 씀. |
| 2026-08-13 03:28:06 | `20260813T032806-auction-bid.json` | JVM local SSE / DB row lock / 스레드풀 | **130 (재시작+웜업 후)** / 1m / 2000002(7,194건+ 누적) | 300→3000 | 19.5% (1577/8070) | 9.9% (795/8070) | **30 / 20 (딱 한 번의 파도, +45~55s, 이후 60초+ 완전히 깨끗)** | 1.95 core | 1.95 core | 300 / (미조회) | (미조회) | 재시작 전(파도 2번, median 346ms)보다 **뚜렷하게 개선**(파도 1번, median 142ms) — 그런데 **backend CPU(1.95 core)·CFS 스로틀링(9.11/s)·GC pause(754ms/30s)는 재시작 전후 거의 동일**. 아래 섹션 참고. |

**측정 지표 관련 주의**: `trx_in_lock_wait`(diag 로그, DB 엔진 단위 — 이미 커넥션을 잡은 트랜잭션이 row lock을 기다리는 수)는 150을 포함한 거의 모든 RATE에서 main phase 내내 어느 정도 흔들린다 — row 하나로 직렬화되는 구조라 원래 있는 현상. 반면 `hikaricp_connections_pending`(Prometheus, 애플리케이션 단위 — 커넥션 풀 자체를 못 받아 대기하는 요청 수)은 RATE별로 뚜렷하게 갈린다. 그래서 위 표의 "회복/고갈" 판정은 **pending + end-to-end latency** 기준으로 봐야 정확하고, `trx_in_lock_wait`의 흔들림만으로 "무너졌다"고 판단하면 안 된다(150도 흔들리지만 pending/latency는 양호).

## backend CPU 병목 발견 (2026-08-13, RATE=130~175 재조사)

지금까지 MySQL/HikariCP만 보고 있었는데, RATE=70부터 이미 backend 컨테이너 CPU가 위 표처럼 1.9 core대로 포화돼 있었다 — **병목이 이미 DB에서 backend로 넘어가 있었다.**

| RATE | backend CFS 스로틀링 | MySQL CPU |
|---|---|---|
| 70 | 9.02/s | (낮음) |
| 100 | 9.24/s | (낮음) |
| 150(1차) | 9.44/s | 1.64 core |
| 155 | 8.55/s | 1.82 core |
| 160 | 9.23/s | 1.60 core |
| 175 | 9.22/s | 1.67 core |
| 130(재시작 전) | 8.47/s | 1.63 core |
| 130(재시작+웜업 후) | 9.11/s | 1.59 core |

**RATE 70부터 이미 backend가 2 vCPU 캡에 걸려서 계속 스로틀링당하고 있고, RATE를 더 올려도 backend CPU 자체는 거의 안 바뀐다** — 즉 인덱스 수정으로 DB 부담을 없앤 뒤로는, 이 환경(2 vCPU, 실제 t4g.micro 스펙)에서 **Spring Boot 애플리케이션 자체가 새 병목**이 됐다. JVM 힙이 `-Xmx384m`으로 작게 잡혀 있어서 GC pause도 30초 구간에 686~1,462ms(구간의 2~5%)씩 나옴.

### 메모리 누수 의심 → 재시작으로 검증

backend 프로세스가 01:40에 뜬 이후 테스트를 15번+ 재시작 없이 누적해서 돌렸는데, JVM RSS가 698MB(힙 캡 384MB의 거의 2배)까지 올라가 있었고 컨테이너 메모리도 902.4/903MB(99.93%)로 꽉 차 있었음 — SSE 연결(매 테스트 300+300개)이 서버 쪽에서 완전히 정리 안 되고 누적되는 게 아닌지 의심해서 backend를 재시작해 검증:

- **재시작 직후(콜드) RATE=130**: median 2,965ms로 오히려 훨씬 나빠짐 — JIT 콜드 스타트 효과가 지배적이라 이 비교 자체는 무효.
- **웜업(RATE=100 1회) 후 RATE=130**: median 142ms, pending 파도 1번뿐(재시작 전엔 2번, median 346ms) — **확실히 개선됨.**
- **하지만 backend CPU(1.95 core)/CFS 스로틀링(9.11/s)/GC pause(754ms)는 재시작 전후로 거의 동일.**

**결론**: 누적된 상태(메모리 관련 뭔가)가 결과를 "더 나쁘게" 만들고 있었던 건 맞지만(파도 2번→1번, latency 346→142ms), **그 밑바탕에 있는 backend CPU/GC 캡 자체는 누적 artifact가 아니라 이 RATE 수준이 실제로 요구하는 비용**이다. 즉 원인이 두 개 겹쳐 있었음:
1. (해소됨) 장시간 누적 테스트로 인한 JVM 메모리/GC 상태 악화 — 재시작으로 리셋 가능
2. (해소 안 됨) backend 컨테이너 2 vCPU 캡 자체의 처리 한계 — RATE 자체를 낮추거나 CPU를 늘리거나 코드/GC 튜닝 없이는 해결 안 됨

---

# SSE 브로드캐스트 vs 입찰 처리 비용 격리 (2026-08-13)

`SSE_VUS`를 0으로 주면 `positiveInteger`가 0을 걸러내고 기본값 300으로 되돌리는 버그가 있어서 `backend/src/test/k6/bid/auction-bid.js`를 고쳤다(#410) — `nonNegativeInteger` 헬퍼 추가, `SSE_VUS=0`일 때 SSE 시나리오 자체를 `scenarios`에서 빼고 관련 threshold도 조건부로 등록. RATE/누적 입찰 수(2000002, 7,194건+)는 그대로 두고 SSE 연결 수만 300→0으로 바꿔서 비교.

| 실행시각 | k6 결과파일 | RATE / SSE_VUS | bid_accepted_or_contended | bid_accepted | HikariCP active/pending 피크 | MySQL CPU 피크 | backend CPU 피크 | backend CFS 스로틀링 | GC pause(30s 최대) | end-to-end median | 비고 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 2026-08-13 03:28:06 | `20260813T032806-auction-bid.json` | 130 / **300**(기존) | 19.5% (1577/8070) | 9.9% (795/8070) | 30 / 20 | 1.95 core | 1.95 core | 9.11/s | 754ms | 142ms | 재시작+웜업 후 baseline (backend CPU 병목 섹션과 동일 실행) |
| 2026-08-13 03:58:47 | `20260813T035847-auction-bid.json` | 130 / **0** | 92.6% (7518/8115) | **90.4% (7337/8115)** | **2 / 0** | **0.60 core** | **0.45 core** | **0** | **174ms** | **7ms** | **SSE 연결만 껐는데 전부 극적으로 개선** — backend CPU 4.4배 감소, CFS 스로틀링 완전히 사라짐, GC pause 4.3배 감소, bid_accepted율 9배, latency 20배 개선. **backend CPU 병목의 원인은 입찰/API 처리 자체가 아니라 거의 전부 SSE 브로드캐스트(300+300명한테 이벤트 전송)였다는 게 확정.** |

**결론**: "backend가 2 vCPU 캡에 걸려서 못 버틴다"고 본 현상은, 입찰 처리 로직이 무거운 게 아니라 **SSE 브로드캐스트(경매 이벤트 300명 + 알림 300명에게 실시간 전송)가 CPU/GC를 거의 다 잡아먹고 있었던 것**이 원인이었다. 인덱스 수정(DB) → SSE 격리(backend) 순으로 병목이 계속 한 단계씩 벗겨진 셈.

이 SSE 비용 자체는 이번 이슈(#410, `bids` 인덱스 누락) 범위 밖이라 여기서는 원인 분석까지만 기록하고 별도 후속 조사로 남긴다.

## 후속 조사 계획 (이슈 #410 범위 밖, 별도 이슈로 분리 예정)

1. **RATE=130 고정, SSE_VUS를 0→50→100→150→200→300 단계적으로 올리며 backend CPU 측정** — 구독자 수 대비 브로드캐스트 비용이 선형인지 어느 지점에서 급격히 나빠지는지 확인. "동시 접속자 X명일 때 이 인스턴스가 버틸 수 있는가"를 보여주는 캐패시티 플래닝 자료.
2. **auction 브로드캐스트 vs notification 푸시 분리**: 지금은 `SSE_VUS` 하나가 `auctionSseConnections`/`notificationSseConnections`(`auction-bid.js:53,60`) 둘 다 같이 통제해서 어느 쪽이 CPU를 더 먹는지 구분이 안 됨 — `AUCTION_SSE_VUS`/`NOTIFICATION_SSE_VUS`처럼 따로 받는 env var를 추가해서 한쪽씩 꺼보고 확인. (fan-out 배율상 auction 쪽이 유력하다고 추측은 되지만 아직 미검증)
3. **`sse-virtual-threads` 프로필로 RATE=130~175 재테스트** — SSE_VUS=0 실험은 "SSE가 병목이다"를 확인하는 진단용일 뿐 실제 운영에서 SSE를 끌 수는 없음. 실제로 켤 수 있는 개선책은 이 프로필(SSE 브로드캐스트 디스패치를 가상 스레드로)이라, 이걸로 같은 RATE에서 backend CPU/GC가 실제로 개선되는지 확인하는 게 가장 실질적인 다음 스텝.
4. 힙 크기(`-Xmx`) 조정 실험은 위 1~3번으로 SSE 비용의 구조를 먼저 파악한 뒤 진행.

> 이 문서는 Claude의 도움을 받아 작성하였습니다
