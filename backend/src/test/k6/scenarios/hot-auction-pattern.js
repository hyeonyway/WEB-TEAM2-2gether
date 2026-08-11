import http from 'k6/http';
import sse from 'k6/x/sse';
import {check, sleep} from 'k6';
import {Counter, Rate} from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
// 기본치는 6-capacity-baseline-findings.md에서 확인한 이 인스턴스(t4g.micro,
// vCPU 2개)의 안전 운영선(SSE 500연결 + 전체 QPS 100~150 부근)을 기준으로
// 잡았다. 원래 설계(1000명 고정, hotAuctionRate=30)는 SSE 연결 수 자체가
// QPS와 무관하게 이 박스를 무너뜨리는 걸 확인하기 전 계산이라 그대로 못 쓴다.
// 필요하면 SSE_USERS/HOT_AUCTION_RATE/COLD_AUCTION_RATE_PER_AUCTION로 올려서
// 어디서 다시 깨지는지 찾는 용도로도 쓸 수 있다.
const sseUsers = positiveInt(__ENV.SSE_USERS, 500);
const hotAuctionCount = hotCount(__ENV.HOT_AUCTION_COUNT, 3);
// 시도율(hot+cold 합) 60/s 목표 — 매 시도가 GET+POST 쌍이라 실제 HTTP는 ~120 req/s.
// pure-throughput에서 500conn+200QPS(읽기 위주)는 에러 없이 버틴 걸 감안해
// 쓰기 위주인 이 시나리오는 그보다 낮게, 원래 안(60 req/s)의 2배로 잡았다.
const hotAuctionRate = positiveInt(__ENV.HOT_AUCTION_RATE, 14);
const coldAuctionRatePerAuction = positiveNumber(__ENV.COLD_AUCTION_RATE_PER_AUCTION, 0.09);
// k6 constant-arrival-rate는 정수 요청률만 받으므로, 비핫 경매 합산 목표를 가장 가까운 정수로 맞춘다.
const coldAuctionRate = Math.round((200 - hotAuctionCount) * coldAuctionRatePerAuction);
const duration = __ENV.DURATION || '5m';
const sseRampUp = __ENV.SSE_RAMP_UP || '60s';
const sseDuration = __ENV.SSE_DURATION || addDurations(duration, '90s');
const mainStartTime = __ENV.MAIN_START_TIME || addDurations(sseRampUp, '5s');
const userIdStart = positiveInt(__ENV.LOAD_TEST_USER_ID_START, 910001);
const loginBatchSize = positiveInt(__ENV.LOGIN_BATCH_SIZE, 25);
const preAllocatedVUs = positiveInt(__ENV.PRE_ALLOCATED_VUS, 200);
const maxVUs = positiveInt(__ENV.MAX_VUS, 1000);
const resultFile = __ENV.K6_RESULT_FILE;

const bidServerError = new Rate('bid_server_error');
const bidPolicyRejected = new Counter('bid_policy_rejected');
const auctionSseConnected = new Rate('auction_sse_connected');
const notificationSseConnected = new Rate('notification_sse_connected');
const auctionSseEvents = new Counter('auction_sse_events');
const notificationSseEvents = new Counter('notification_sse_events');
const sseBarrierReady = new Rate('sse_barrier_ready');

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '15m',
  batchPerHost: loginBatchSize,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    auctionSse: {
      executor: 'ramping-vus', exec: 'auctionSse', startVUs: 0,
      stages: [{target: sseUsers, duration: sseRampUp}, {target: sseUsers, duration: sseDuration}],
      gracefulRampDown: '5s',
    },
    notificationSse: {
      executor: 'ramping-vus', exec: 'notificationSse', startVUs: 0,
      stages: [{target: sseUsers, duration: sseRampUp}, {target: sseUsers, duration: sseDuration}],
      gracefulRampDown: '5s',
    },
    sseReadiness: {
      executor: 'per-vu-iterations', exec: 'waitForSse', vus: 1, iterations: 1,
      maxDuration: mainStartTime, gracefulStop: '5s',
    },
    ...hotBidScenarios(),
    coldBids: bidScenario('coldBid', coldAuctionRate),
  },
  thresholds: {
    'http_req_failed{scenario:hotBid1}': ['rate<0.01'],
    'http_req_failed{scenario:hotBid2}': ['rate<0.01'],
    'http_req_failed{scenario:hotBid3}': ['rate<0.01'],
    'http_req_failed{scenario:coldBids}': ['rate<0.01'],
    'bid_server_error{phase:hot}': ['rate<0.01'],
    'bid_server_error{phase:cold}': ['rate<0.01'],
    'http_req_duration{name:POST /api/auctions/:id/bids,status:201}': ['p(95)<800', 'p(99)<1000'],
    'http_req_duration{name:POST /api/auctions/:id/bids,status:400}': ['p(95)<400', 'p(99)<600'],
    'http_req_duration{name:POST /api/auctions/:id/bids,status:409}': ['p(95)<400', 'p(99)<600'],
    'auction_sse_connected': ['rate>0.99'],
    'notification_sse_connected': ['rate>0.99'],
    'sse_barrier_ready': ['rate>0.99'],
  },
};

export function setup() {
  const auctionIds = configuredAuctionIds();
  if (auctionIds.length !== 200) throw new Error('AUCTION_IDS에 서로 다른 진행 중 경매 ID 200개를 지정하세요.');
  const hotAuctionIds = configuredHotAuctionIds(auctionIds);
  const tokens = login(loadTestUsers());
  return {auctionIds, hotAuctionIds, coldAuctionIds: auctionIds.filter(id => !hotAuctionIds.includes(id)), tokens};
}

export function auctionSse() {
  sse.open(`${baseUrl}/api/auctions/stream`, {headers: {Accept: 'text/event-stream'}, tags: {name: 'GET /api/auctions/stream'}}, client => {
    client.on('open', () => auctionSseConnected.add(true));
    client.on('event', () => auctionSseEvents.add(1));
    client.on('error', () => auctionSseConnected.add(false));
  });
}

export function notificationSse(data) {
  const index = (__VU - 1) % data.tokens.length;
  const ticketResponse = http.post(`${baseUrl}/api/sse/tickets`, null, {
    headers: {Authorization: `Bearer ${data.tokens[index]}`}, responseCallback: http.expectedStatuses(200), tags: {name: 'POST /api/sse/tickets'},
  });
  const ticket = ticketResponse.status === 200 ? ticketResponse.json('ticket') : null;
  if (typeof ticket !== 'string') { notificationSseConnected.add(false); return; }
  sse.open(`${baseUrl}/api/users/${userIdStart + index}/notifications/stream?ticket=${encodeURIComponent(ticket)}`, {headers: {Accept: 'text/event-stream'}, tags: {name: 'GET /api/users/:userId/notifications/stream'}}, client => {
    client.on('open', () => notificationSseConnected.add(true));
    client.on('event', () => notificationSseEvents.add(1));
    client.on('error', () => notificationSseConnected.add(false));
  });
}

export function waitForSse(data) {
  const token = data.tokens[0];
  const deadline = Date.now() + durationToSeconds(mainStartTime) * 1000;
  while (true) {
    const response = http.get(`${baseUrl}/api/test/load/sse-status?expected=${sseUsers}`, {headers: {Authorization: `Bearer ${token}`}, tags: {name: 'GET /api/test/load/sse-status'}});
    const ready = response.status === 200 && response.json('ready') === true;
    if (ready) { sseBarrierReady.add(true); return; }
    if (Date.now() >= deadline) { sseBarrierReady.add(false); return; }
    sleep(1);
  }
}

export function hotBid1(data) { placeBid(data, 0, 'hot'); }
export function hotBid2(data) { placeBid(data, 1, 'hot'); }
export function hotBid3(data) { placeBid(data, 2, 'hot'); }
export function coldBid(data) { placeBid(data, Math.floor(Math.random() * data.coldAuctionIds.length), 'cold', data.coldAuctionIds); }

export function handleSummary(data) {
  const result = {
    generatedAt: new Date().toISOString(), scenario: 'hot-auction-pattern',
    configuration: {auctionCount: 200, hotAuctionCount, hotAuctionRate, coldAuctionRatePerAuction, coldAuctionRate, sseUsers, totalSseConnections: sseUsers * 2, duration},
    ...data,
  };
  return resultFile ? {[resultFile]: JSON.stringify(result, null, 2), stdout: summaryText(data)} : {stdout: summaryText(data)};
}

function placeBid(data, index, phase, auctionIds = data.hotAuctionIds) {
  const auctionId = auctionIds[index];
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const context = http.get(`${baseUrl}/api/auctions/${auctionId}/bid-context`, {headers: {Authorization: `Bearer ${token}`}, tags: {name: 'GET /api/auctions/:id/bid-context', phase}});
  if (context.status !== 200) { bidServerError.add(context.status >= 500, {phase}); return; }
  const price = Number(context.json('minimum_bid'));
  if (!Number.isSafeInteger(price) || price < 1) return;
  const response = http.post(`${baseUrl}/api/auctions/${auctionId}/bids`, JSON.stringify({price}), {
    headers: {Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey(auctionId)},
    responseCallback: http.expectedStatuses(201, 400, 409), tags: {name: 'POST /api/auctions/:id/bids', phase},
  });
  bidServerError.add(response.status >= 500, {phase});
  if (response.status === 400 || response.status === 409) bidPolicyRejected.add(1, {phase, status: String(response.status)});
  check(response, {'서버가 정책대로 응답함(성공/최소가 거부/동시 입찰 충돌)': r => r.status === 201 || r.status === 400 || r.status === 409});
}

function hotBidScenarios() {
  const scenarios = {};
  for (let index = 1; index <= hotAuctionCount; index += 1) scenarios[`hotAuction${index}`] = bidScenario(`hotBid${index}`, hotAuctionRate);
  return scenarios;
}
function bidScenario(exec, rate) { return {executor: 'constant-arrival-rate', exec, startTime: mainStartTime, rate, timeUnit: '1s', duration, preAllocatedVUs, maxVUs, gracefulStop: '10s'}; }
function configuredAuctionIds() { return [...new Set(csv(__ENV.AUCTION_IDS).map(Number).filter(id => Number.isInteger(id) && id > 0))]; }
function configuredHotAuctionIds(auctionIds) {
  const configured = csv(__ENV.HOT_AUCTION_IDS).map(Number).filter(id => Number.isInteger(id) && id > 0);
  const hotIds = configured.length === 0 ? auctionIds.slice(0, hotAuctionCount) : configured;
  if (hotIds.length !== hotAuctionCount || hotIds.some(id => !auctionIds.includes(id))) throw new Error(`HOT_AUCTION_IDS에는 AUCTION_IDS에 포함된 경매 ID ${hotAuctionCount}개를 지정하세요.`);
  return hotIds;
}
function login(users) {
  const tokens = [];
  for (let start = 0; start < users.length; start += loginBatchSize) {
    const responses = http.batch(users.slice(start, start + loginBatchSize).map(user => ({method: 'POST', url: `${baseUrl}/api/auth/login`, body: JSON.stringify(user), params: {headers: {'Content-Type': 'application/json'}, responseCallback: http.expectedStatuses(200)}})));
    responses.forEach((response, index) => { if (response.status !== 200) throw new Error(`로그인 실패 (index=${start + index}, status=${response.status})`); tokens.push(response.json('accessToken')); });
  }
  return tokens;
}
function loadTestUsers() { return Array.from({length: sseUsers}, (_, index) => ({email: `k6-user${String(index + 1).padStart(5, '0')}@dbidding.local`, password: __ENV.LOAD_TEST_PASSWORD || 'K6LoadTest123!'})); }
function hotCount(value, fallback) { const count = positiveInt(value, fallback); if (![2, 3].includes(count)) throw new Error('HOT_AUCTION_COUNT는 2 또는 3이어야 합니다.'); return count; }
function positiveInt(value, fallback) { const parsed = Number(value); return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback; }
function positiveNumber(value, fallback) { const parsed = Number(value); return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback; }
function csv(value) { return (value || '').split(',').map(item => item.trim()).filter(Boolean); }
function idempotencyKey(auctionId) { return `k6-hot-${auctionId}-${__VU}-${__ITER}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`; }
function durationToSeconds(value) { const match = String(value).match(/^(\d+)(ms|s|m|h)$/); if (!match) throw new Error(`duration 형식 오류: ${value}`); return Number(match[1]) * ({ms: 0.001, s: 1, m: 60, h: 3600}[match[2]]); }
function addDurations(first, second) { return `${durationToSeconds(first) + durationToSeconds(second)}s`; }
function summaryText(data) { const values = data.metrics.http_reqs?.values || {}; return `\n=== HOT AUCTION PATTERN SUMMARY ===\nHTTP 요청: ${values.count || 0} (${(values.rate || 0).toFixed(2)} req/s)\n`; }
