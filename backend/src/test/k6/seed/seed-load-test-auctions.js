// 부하테스트 전용 경매 시드 스크립트 (일회성).
//
// 기존 경매들은 buy_now_price가 minimum_bid 바로 위(10~12 증분)에 걸려있어서
// 부하를 걸면 몇 초 안에 즉시낙찰로 CLOSED 되고 테스트 도중 경매 풀이 말라버린다.
// 이 스크립트는 buyNowPrice를 아예 안 넣어서(=즉시낙찰 off) 테스트 내내 안 닫히는
// 경매를 만든다. AuctionCreateRequest.buyNowPrice는 @Positive만 있고 @NotNull이
// 없어서 생략하면 null로 들어가고, Auction.nextMinimumBid()는 buyNowPrice==null이면
// 캡을 안 건다(Auction.java:146).
//
// imageUploadTokens는 AuctionImageUploadAdapter(prod 기본 어댑터)가 실제 S3 존재
// 여부를 검증하지 않고 non-blank 문자열이면 그대로 통과시키므로 placeholder로
// 충분하다.
//
// 실행:
//   cd backend/src/test/k6
//   ./sse/k6-sse run -e BASE_URL=https://api.dbidding.shop -e COUNT=300 seed/seed-load-test-auctions.js
//
// 결과: results/seeded-auction-ids-<타임스탬프>.json 에 생성된 auction id 배열 저장.
// 이 id들을 hot-auction-pattern.js의 AUCTION_IDS로 그대로 넣으면 된다.
import http from 'k6/http';
import {check} from 'k6';
import {Counter} from 'k6/metrics';

// k6 OSS는 handleSummary에서 각 VU iteration의 반환값을 못 모은다(cloud 전용 기능).
// 그래서 생성된 auction id는 console.log(`SEEDED_ID:<id>`)로 stdout에 찍고,
// 실행 로그에서 `grep '^SEEDED_ID:' | cut -d: -f2`로 뽑아 쓴다.

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const count = positiveInt(__ENV.COUNT, 300);
const sellerCount = positiveInt(__ENV.SELLER_COUNT, 10);
const cardIdMax = positiveInt(__ENV.CARD_ID_MAX, 12864);
const resultFile = __ENV.K6_RESULT_FILE || `${__ENV.RESULT_DIR || './results'}/seeded-auction-ids.json`;

const createFailures = new Counter('auction_create_failures');

export const options = {
  scenarios: {
    seedAuctions: {
      executor: 'shared-iterations',
      exec: 'seedAuction',
      vus: Math.min(10, count),
      iterations: count,
      maxDuration: '5m',
    },
  },
  thresholds: {
    auction_create_failures: ['count==0'],
  },
};

export function setup() {
  const users = Array.from({length: sellerCount}, (_, index) => ({
    email: `k6-user${String(index + 1).padStart(5, '0')}@dbidding.local`,
    password: __ENV.LOAD_TEST_PASSWORD || 'K6LoadTest123!',
  }));
  const responses = http.batch(users.map(user => ({
    method: 'POST', url: `${baseUrl}/api/auth/login`, body: JSON.stringify(user),
    params: {headers: {'Content-Type': 'application/json'}, responseCallback: http.expectedStatuses(200)},
  })));
  const tokens = responses.map((response, index) => {
    if (response.status !== 200) throw new Error(`시드용 판매자 로그인 실패 (index=${index}, status=${response.status})`);
    return response.json('accessToken');
  });
  return {tokens};
}

export function seedAuction(data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const cardId = 1 + Math.floor(Math.random() * cardIdMax);
  const startPrice = 10000 + Math.floor(Math.random() * 5000) * 10;
  const response = http.post(`${baseUrl}/api/auctions`, JSON.stringify({
    itemId: cardId,
    auctionName: `[LOAD-TEST] 부하테스트 경매 #${__VU}-${__ITER}`,
    description: '부하테스트 전용 경매입니다. 즉시낙찰 없음.',
    imageUploadTokens: ['load-test/placeholder.webp'],
    startPrice,
    bidIncrement: 1000,
    // buyNowPrice 의도적으로 생략 — 즉시낙찰 off
    durationHours: 24,
    shippingFee: 3000,
  }), {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': `k6-seed-auction-${__VU}-${__ITER}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`,
    },
    responseCallback: http.expectedStatuses(201),
    tags: {name: 'POST /api/auctions (seed)'},
  });
  const ok = response.status === 201;
  if (!ok) {
    createFailures.add(1);
    console.error(`SEED_FAILED:status=${response.status} body=${response.body}`);
  } else {
    console.log(`SEEDED_ID:${response.json('id')}`);
  }
  check(response, {'경매 생성 성공': r => r.status === 201});
}

export function handleSummary(data) {
  return {
    [resultFile]: JSON.stringify({
      generatedAt: new Date().toISOString(),
      requested: count,
      failed: data.metrics.auction_create_failures ? data.metrics.auction_create_failures.values.count : 0,
    }, null, 2),
    stdout: `\n=== SEED SUMMARY ===\n요청: ${count}건 (생성된 id는 로그의 SEEDED_ID: 라인 참고)\n`,
  };
}

function positiveInt(value, fallback) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}
