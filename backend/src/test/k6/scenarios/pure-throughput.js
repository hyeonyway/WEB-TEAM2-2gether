import http from 'k6/http';
import sse from 'k6/x/sse';
import {check, sleep} from 'k6';
import {Counter, Rate} from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const sseVUs = sseTier(__ENV.SSE_VUS, 250);
const stageDuration = __ENV.STAGE_DURATION || '2m';
const qpsStages = [50, 100, 150, 200, 300, 400].map(rate => ({target: rate, duration: stageDuration}));
const sseRampUp = __ENV.SSE_RAMP_UP || '30s';
const sseDuration = __ENV.SSE_DURATION || totalDuration();
const mainStartTime = __ENV.MAIN_START_TIME || addDurations(sseRampUp, '5s');
const userCount = positiveInt(__ENV.LOAD_TEST_USER_COUNT, sseVUs);
const loginBatchSize = positiveInt(__ENV.LOGIN_BATCH_SIZE, 25);
const preAllocatedVUs = positiveInt(__ENV.PRE_ALLOCATED_VUS, 200);
const maxVUs = positiveInt(__ENV.MAX_VUS, 1000);
const resultFile = __ENV.K6_RESULT_FILE;

const bidServerError = new Rate('bid_server_error');
const bidPolicyRejected = new Counter('bid_policy_rejected');
const sseAuctionConnectSuccess = new Rate('sse_auction_connect_success');
const sseNotificationConnectSuccess = new Rate('sse_notification_connect_success');

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '15m',
  batchPerHost: loginBatchSize,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    auctionSse: {
      executor: 'ramping-vus', exec: 'auctionSse', startVUs: 0,
      stages: [{target: sseVUs, duration: sseRampUp}, {target: sseVUs, duration: sseDuration}],
      gracefulRampDown: '5s',
    },
    notificationSse: {
      executor: 'ramping-vus', exec: 'notificationSse', startVUs: 0,
      stages: [{target: sseVUs, duration: sseRampUp}, {target: sseVUs, duration: sseDuration}],
      gracefulRampDown: '5s',
    },
    bidContextReads: arrivalScenario('bidContextRead', 0.4),
    bidWrites: arrivalScenario('bidWrite', 0.2),
    generalReads: arrivalScenario('generalRead', 0.4),
  },
  thresholds: {
    'http_req_failed{scenario:bidContextReads}': ['rate<0.005'],
    'http_req_failed{scenario:bidWrites}': ['rate<0.01'],
    'http_req_failed{scenario:generalReads}': ['rate<0.005'],
    'bid_server_error{scenario:bidWrites}': ['rate<0.01'],
    'http_req_duration{name:GET /api/auctions/:id/bid-context,scenario:bidContextReads}': ['p(95)<200', 'p(99)<350'],
    'http_req_duration{name:POST /api/auctions/:id/bids,status:201}': ['p(95)<800', 'p(99)<1000'],
    'http_req_duration{name:POST /api/auctions/:id/bids,status:400}': ['p(95)<400', 'p(99)<600'],
    'http_req_duration{name:POST /api/auctions/:id/bids,status:409}': ['p(95)<400', 'p(99)<600'],
    'http_req_duration{name:GET /api/auctions,scenario:generalReads}': ['p(95)<300', 'p(99)<600'],
    'http_req_duration{name:GET /api/auctions/:id,scenario:generalReads}': ['p(95)<300', 'p(99)<600'],
    'sse_auction_connect_success': ['rate>0.99'],
    'sse_notification_connect_success': ['rate>0.99'],
  },
};

export function setup() {
  const tokens = login(loadTestUsers());
  const auctions = loadOpenAuctions(tokens[0]);
  if (auctions.length === 0) throw new Error('진행 중인 경매가 없습니다. AUCTION_IDS를 지정하거나 시드 데이터를 확인하세요.');
  return {tokens, auctions};
}

export function auctionSse() {
  sse.open(`${baseUrl}/api/auctions/stream`, {headers: {Accept: 'text/event-stream'}, tags: {name: 'GET /api/auctions/stream'}}, client => {
    client.on('open', () => sseAuctionConnectSuccess.add(true));
    client.on('error', () => sseAuctionConnectSuccess.add(false));
  });
}

export function notificationSse(data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const ticketResponse = http.post(`${baseUrl}/api/sse/tickets`, null, {
    headers: {Authorization: `Bearer ${token}`},
    responseCallback: http.expectedStatuses(200),
    tags: {name: 'POST /api/sse/tickets'},
  });
  const ticket = ticketResponse.status === 200 ? ticketResponse.json('ticket') : null;
  if (typeof ticket !== 'string') {
    sseNotificationConnectSuccess.add(false);
    return;
  }
  const userId = 910001 + ((__VU - 1) % data.tokens.length);
  sse.open(`${baseUrl}/api/users/${userId}/notifications/stream?ticket=${encodeURIComponent(ticket)}`, {headers: {Accept: 'text/event-stream'}, tags: {name: 'GET /api/users/:userId/notifications/stream'}}, client => {
    client.on('open', () => sseNotificationConnectSuccess.add(true));
    client.on('error', () => sseNotificationConnectSuccess.add(false));
  });
}

export function bidContextRead(data) {
  const auction = randomAuction(data.auctions);
  http.get(`${baseUrl}/api/auctions/${auction.id}/bid-context`, {
    headers: authorization(data.tokens),
    tags: {name: 'GET /api/auctions/:id/bid-context'},
  });
}

export function bidWrite(data) {
  const auction = randomAuction(data.auctions);
  const response = http.post(`${baseUrl}/api/auctions/${auction.id}/bids`, JSON.stringify({price: auction.minimumBid}), {
    headers: {...authorization(data.tokens), 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey(auction.id)},
    responseCallback: http.expectedStatuses(201, 400, 409),
    tags: {name: 'POST /api/auctions/:id/bids'},
  });
  bidServerError.add(response.status >= 500);
  if (response.status === 400 || response.status === 409) bidPolicyRejected.add(1, {status: String(response.status)});
  check(response, {'서버가 정책대로 응답함(성공/최소가 거부/동시 입찰 충돌)': r => r.status === 201 || r.status === 400 || r.status === 409});
}

export function generalRead(data) {
  const headers = authorization(data.tokens);
  if (__ITER % 2 === 0) {
    http.get(`${baseUrl}/api/auctions?size=20`, {headers, tags: {name: 'GET /api/auctions'}});
  } else {
    const auction = randomAuction(data.auctions);
    http.get(`${baseUrl}/api/auctions/${auction.id}`, {headers, tags: {name: 'GET /api/auctions/:id'}});
  }
}

export function handleSummary(data) {
  const result = {
    generatedAt: new Date().toISOString(),
    scenario: 'pure-throughput',
    configuration: {sseVUs, totalSseConnections: sseVUs * 2, qpsStages: qpsStages.map(stage => stage.target), stageDuration},
    ...data,
  };
  return resultFile ? {[resultFile]: JSON.stringify(result, null, 2), stdout: summaryText(data)} : {stdout: summaryText(data)};
}

function arrivalScenario(exec, share) {
  return {
    executor: 'ramping-arrival-rate', exec, startTime: mainStartTime, startRate: Math.round(50 * share), timeUnit: '1s',
    stages: qpsStages.map(stage => ({target: Math.round(stage.target * share), duration: stage.duration})),
    preAllocatedVUs, maxVUs, gracefulStop: '10s',
  };
}

function loadOpenAuctions(token) {
  const configured = csv(__ENV.AUCTION_IDS).map(Number).filter(Number.isInteger).map(id => ({id, minimumBid: positiveInt(__ENV.DEFAULT_BID_PRICE, 1000000)}));
  if (configured.length > 0) return configured;
  const response = http.get(`${baseUrl}/api/auctions?size=100`, {headers: {Authorization: `Bearer ${token}`}, tags: {name: 'GET /api/auctions (setup)'}});
  if (response.status !== 200) throw new Error(`경매 자동 조회 실패 (status=${response.status})`);
  const content = response.json('content');
  return Array.isArray(content) ? content.filter(auction => auction.status === 'OPEN' || auction.status === 'ENDING').map(auction => ({id: auction.id, minimumBid: positiveInt(auction.current_price || auction.start_price, 1000000)})) : [];
}

function login(users) {
  const tokens = [];
  for (let start = 0; start < users.length; start += loginBatchSize) {
    const responses = http.batch(users.slice(start, start + loginBatchSize).map(user => ({method: 'POST', url: `${baseUrl}/api/auth/login`, body: JSON.stringify(user), params: {headers: {'Content-Type': 'application/json'}, responseCallback: http.expectedStatuses(200)}})));
    responses.forEach((response, index) => {
      if (response.status !== 200) throw new Error(`로그인 실패 (index=${start + index}, status=${response.status})`);
      tokens.push(response.json('accessToken'));
    });
  }
  return tokens;
}

function loadTestUsers() { return Array.from({length: userCount}, (_, index) => ({email: `k6-user${String(index + 1).padStart(5, '0')}@dbidding.local`, password: __ENV.LOAD_TEST_PASSWORD || 'K6LoadTest123!'})); }
function authorization(tokens) { return {Authorization: `Bearer ${tokens[(__VU - 1) % tokens.length]}`}; }
function randomAuction(auctions) { return auctions[Math.floor(Math.random() * auctions.length)]; }
function idempotencyKey(auctionId) { return `k6-throughput-${auctionId}-${__VU}-${__ITER}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`; }
function csv(value) { return (value || '').split(',').map(item => item.trim()).filter(Boolean); }
function positiveInt(value, fallback) { const parsed = Number(value); return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback; }
function sseTier(value, fallback) { const tier = positiveInt(value, fallback); if (![250, 500, 1000].includes(tier)) throw new Error('SSE_VUS는 250, 500, 1000 중 하나여야 합니다.'); return tier; }
function totalDuration() { return `${qpsStages.reduce((seconds, stage) => seconds + durationToSeconds(stage.duration), durationToSeconds(sseRampUp) + 10)}s`; }
function addDurations(first, second) { return `${durationToSeconds(first) + durationToSeconds(second)}s`; }
function durationToSeconds(value) { const match = String(value).match(/^(\d+)(ms|s|m|h)$/); if (!match) throw new Error(`duration 형식 오류: ${value}`); return Number(match[1]) * ({ms: 0.001, s: 1, m: 60, h: 3600}[match[2]]); }
function summaryText(data) { const values = data.metrics.http_reqs?.values || {}; return `\n=== PURE THROUGHPUT SUMMARY ===\nHTTP 요청: ${values.count || 0} (${(values.rate || 0).toFixed(2)} req/s)\n`; }
