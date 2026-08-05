import http from 'k6/http';
import {check} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const requestRate = positiveNumber(__ENV.RATE, 100);
const duration = __ENV.DURATION || '1m';
const preAllocatedVUs = positiveNumber(__ENV.PRE_ALLOCATED_VUS, 100);
const maxVUs = positiveNumber(__ENV.MAX_VUS, 300);
const warmupRate = positiveNumber(__ENV.WARMUP_RATE, 20);
const warmupDuration = __ENV.WARMUP_DURATION || '30s';
const mainStartTime = __ENV.MAIN_START_TIME || '35s';
const loadTestUserCount = positiveInteger(__ENV.LOAD_TEST_USER_COUNT, 300);
const loadTestEmailPrefix = __ENV.LOAD_TEST_EMAIL_PREFIX || 'k6-user';
const loadTestEmailDomain = __ENV.LOAD_TEST_EMAIL_DOMAIN || 'dbidding.local';
const loadTestPassword = __ENV.LOAD_TEST_PASSWORD || 'K6LoadTest123!';

const bidAccepted = new Rate('bid_accepted');
const bidAcceptedOrContended = new Rate('bid_accepted_or_contended');
const bidContentions = new Counter('bid_contentions');
const bidRejected = new Counter('bid_rejected');
const bidEndToEndDuration = new Trend('bid_end_to_end_duration', true);

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '10m',
  scenarios: {
    warmupAuctionBids: {
      executor: 'ramping-arrival-rate',
      exec: 'warmup',
      startRate: 1,
      timeUnit: '1s',
      stages: [{target: warmupRate, duration: warmupDuration}],
      preAllocatedVUs: Math.min(preAllocatedVUs, 20),
      maxVUs,
      gracefulStop: '5s',
    },
    realAuctionBids: {
      executor: 'constant-arrival-rate',
      exec: 'bid',
      startTime: mainStartTime,
      rate: requestRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
      gracefulStop: '10s',
    },
  },
  thresholds: {
    'checks{scenario:realAuctionBids}': ['rate>0.99'],
    'bid_accepted_or_contended{scenario:realAuctionBids}': ['rate>0.99'],
    'http_req_failed{scenario:realAuctionBids}': ['rate<0.01'],
    'http_req_duration{name:GET /api/auctions/:id/bid-context,scenario:realAuctionBids}': ['p(95)<500'],
    'http_req_duration{name:POST /api/auctions/:id/bids,scenario:realAuctionBids}': ['p(95)<1000'],
  },
};

export function setup() {
  const tokens = loginAndGetAccessTokens();
  if (tokens.length === 0) {
    throw new Error('로그인할 EMAIL/PASSWORD 또는 LOGIN_USERS가 필요합니다.');
  }

  const auctionIds = loadAuctionIds(tokens[0]);
  if (auctionIds.length === 0) {
    throw new Error('입찰할 진행 중 경매가 없습니다. AUCTION_IDS를 지정해 주세요.');
  }

  return {tokens, auctionIds};
}

export function warmup(data) {
  performBid(data);
}

export function bid(data) {
  performBid(data);
}

export default function (data) {
  performBid(data);
}

function performBid({tokens, auctionIds}) {
  const token = tokens[(__VU - 1) % tokens.length];
  const auctionId = auctionIds[Math.floor(Math.random() * auctionIds.length)];
  const headers = {Authorization: `Bearer ${token}`};
  const startedAt = Date.now();

  const contextResponse = http.get(
    `${baseUrl}/api/auctions/${auctionId}/bid-context`,
    {
      headers,
      tags: {name: 'GET /api/auctions/:id/bid-context'},
    },
  );

  const contextOk = check(contextResponse, {
    '입찰 컨텍스트 조회 성공': response => response.status === 200,
  });
  if (!contextOk) {
    bidAcceptedOrContended.add(false);
    bidRejected.add(1, {status: String(contextResponse.status), phase: 'context'});
    return;
  }

  const context = contextResponse.json();
  const price = Number(context.minimum_bid);
  if (!Number.isSafeInteger(price) || price < 1) {
    bidAcceptedOrContended.add(false);
    bidRejected.add(1, {status: 'invalid_context', phase: 'context'});
    return;
  }

  const bidResponse = http.post(
    `${baseUrl}/api/auctions/${auctionId}/bids`,
    JSON.stringify({price}),
    {
      headers: {
        ...headers,
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey(auctionId),
      },
      tags: {name: 'POST /api/auctions/:id/bids'},
      responseCallback: http.expectedStatuses(201, 409),
    },
  );

  const accepted = bidResponse.status === 201;
  const contended = bidResponse.status === 409;
  bidAccepted.add(accepted);
  bidAcceptedOrContended.add(accepted || contended);
  bidEndToEndDuration.add(Date.now() - startedAt);

  if (contended) {
    // 최신 minimum_bid를 읽은 뒤 다른 VU가 먼저 입찰한 정상적인 경쟁 충돌이다.
    bidContentions.add(1);
  } else if (!accepted) {
    bidRejected.add(1, {status: String(bidResponse.status), phase: 'bid'});
  }

  check(bidResponse, {
    '입찰 성공 또는 동시 입찰 충돌': response => response.status === 201 || response.status === 409,
  });
}

function loginAndGetAccessTokens() {
  let users = [];
  if (__ENV.LOGIN_USERS) {
    try {
      users = JSON.parse(__ENV.LOGIN_USERS);
    } catch (error) {
      throw new Error(`LOGIN_USERS JSON 형식이 잘못되었습니다: ${error.message}`);
    }
  } else if (__ENV.EMAIL && __ENV.PASSWORD) {
    users = [{email: __ENV.EMAIL, password: __ENV.PASSWORD}];
  } else if (__ENV.ACCESS_TOKENS) {
    return csv(__ENV.ACCESS_TOKENS);
  } else {
    users = loadTestUsers();
  }

  if (!Array.isArray(users) || users.length === 0) {
    throw new Error('로그인 계정 목록이 비어 있습니다.');
  }

  return users.map((user, index) => {
    const response = http.post(
      `${baseUrl}/api/auth/login`,
      JSON.stringify({email: user.email, password: user.password}),
      {
        headers: {'Content-Type': 'application/json'},
        tags: {name: 'POST /api/auth/login (setup)'},
      },
    );
    const accessToken = response.json('accessToken');
    if (response.status !== 200 || typeof accessToken !== 'string' || accessToken.length === 0) {
      throw new Error(`로그인 계정 ${index + 1} 토큰 발급 실패 (status=${response.status})`);
    }
    return accessToken;
  });
}

function loadTestUsers() {
  return Array.from({length: loadTestUserCount}, (_, index) => ({
    email: `${loadTestEmailPrefix}${String(index + 1).padStart(3, '0')}@${loadTestEmailDomain}`,
    password: loadTestPassword,
  }));
}

function loadAuctionIds(token) {
  const configuredIds = csv(__ENV.AUCTION_IDS)
    .map(Number)
    .filter(id => Number.isInteger(id) && id > 0);
  if (configuredIds.length > 0) {
    return configuredIds;
  }

  const response = http.get(`${baseUrl}/api/auctions?size=100`, {
    headers: {Authorization: `Bearer ${token}`},
    tags: {name: 'GET /api/auctions (setup)'},
  });
  if (response.status !== 200) {
    throw new Error(`경매 자동 조회 실패 (status=${response.status})`);
  }

  const content = response.json('content');
  return Array.isArray(content)
    ? content
      .filter(auction => auction.status === 'OPEN' || auction.status === 'ENDING')
      .map(auction => auction.id)
    : [];
}

function idempotencyKey(auctionId) {
  return `k6-${auctionId}-${__VU}-${__ITER}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
}

function csv(value) {
  return (value || '').split(',').map(item => item.trim()).filter(Boolean);
}

function positiveNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function positiveInteger(value, fallback) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}
