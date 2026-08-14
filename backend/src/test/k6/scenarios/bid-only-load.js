// SSE 연결 없이 입찰/조회 HTTP 부하만 걸어서, SSE 팬아웃 비용을 뺀
// "순수 입찰 처리 자체"의 한계를 격리해서 본다. pure-throughput.js와
// QPS 계단·트래픽 구성비·경매 풀은 동일하게 맞춰서 SSE 유무만 변수로 통제한다.
import http from 'k6/http';
import {check} from 'k6';
import {Counter, Rate} from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
// HOT_AUCTION_ID를 지정하면 bidContextRead/bidWrite가 이 경매 하나에만 몰린다
// (같은 행에 대한 순수 락 경합 한계 측정용). 안 주면 기존처럼 풀 전체에 분산.
const hotAuctionId = positiveIntOrNull(__ENV.HOT_AUCTION_ID);
const stageDuration = __ENV.STAGE_DURATION || '2m';
const qpsStages = qpsStageTargets(__ENV.QPS_STAGES).map(rate => ({target: rate, duration: stageDuration}));
const userCount = positiveInt(__ENV.LOAD_TEST_USER_COUNT, 500);
const loginBatchSize = positiveInt(__ENV.LOGIN_BATCH_SIZE, 25);
const preAllocatedVUs = positiveInt(__ENV.PRE_ALLOCATED_VUS, 200);
const maxVUs = positiveInt(__ENV.MAX_VUS, 1000);
const resultFile = __ENV.K6_RESULT_FILE;

const bidServerError = new Rate('bid_server_error');
const bidPolicyRejected = new Counter('bid_policy_rejected');

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '15m',
  batchPerHost: loginBatchSize,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  scenarios: {
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
    'http_req_duration{name:GET /api/auctions,scenario:generalReads}': ['p(95)<300', 'p(99)<600'],
    'http_req_duration{name:GET /api/auctions/:id,scenario:generalReads}': ['p(95)<300', 'p(99)<600'],
  },
};

export function setup() {
  const sessions = login(loadTestUsers());
  const auctions = loadOpenAuctions(sessions[0]);
  if (auctions.length === 0) throw new Error('진행 중인 경매가 없습니다. AUCTION_IDS를 지정하거나 시드 데이터를 확인하세요.');
  return {sessions, auctions};
}

export function bidContextRead(data) {
  const auction = targetAuction(data.auctions);
  http.get(`${baseUrl}/api/auctions/${auction.id}/bid-context`, {
    headers: authorization(data.sessions),
    responseCallback: http.expectedStatuses(200),
    tags: {name: 'GET /api/auctions/:id/bid-context'},
  });
}

export function bidWrite(data) {
  const auction = targetAuction(data.auctions);
  const headers = authorization(data.sessions);
  const context = http.get(`${baseUrl}/api/auctions/${auction.id}/bid-context`, {
    headers,
    responseCallback: http.expectedStatuses(200),
    tags: {name: 'GET /api/auctions/:id/bid-context'},
  });
  if (context.status !== 200) { bidServerError.add(context.status >= 500); return; }
  const price = Number(context.json('minimum_bid'));
  if (!Number.isSafeInteger(price) || price < 1) return;
  const response = http.post(`${baseUrl}/api/auctions/${auction.id}/bids`, JSON.stringify({price}), {
    headers: {...writeHeaders(data.sessions), 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey(auction.id)},
    responseCallback: http.expectedStatuses(201, 400, 409),
    tags: {name: 'POST /api/auctions/:id/bids'},
  });
  bidServerError.add(response.status >= 500);
  if (response.status === 400 || response.status === 409) bidPolicyRejected.add(1, {status: String(response.status)});
  check(response, {'서버가 정책대로 응답함(성공/최소가 거부/동시 입찰 충돌)': r => r.status === 201 || r.status === 400 || r.status === 409});
}

export function generalRead(data) {
  const headers = authorization(data.sessions);
  if (__ITER % 2 === 0) {
    http.get(`${baseUrl}/api/auctions?size=20`, {headers, responseCallback: http.expectedStatuses(200), tags: {name: 'GET /api/auctions'}});
  } else {
    const auction = randomAuction(data.auctions);
    http.get(`${baseUrl}/api/auctions/${auction.id}`, {headers, responseCallback: http.expectedStatuses(200), tags: {name: 'GET /api/auctions/:id'}});
  }
}

export function handleSummary(data) {
  const result = {
    generatedAt: new Date().toISOString(),
    scenario: 'bid-only-load (SSE 없음)',
    configuration: {qpsStages: qpsStages.map(stage => stage.target), stageDuration, hotAuctionId},
    ...data,
  };
  return resultFile ? {[resultFile]: JSON.stringify(result, null, 2), stdout: summaryText(data)} : {stdout: summaryText(data)};
}

function arrivalScenario(exec, share) {
  return {
    executor: 'ramping-arrival-rate', exec, startRate: Math.round(qpsStages[0].target * share), timeUnit: '1s',
    stages: qpsStages.map(stage => ({target: Math.round(stage.target * share), duration: stage.duration})),
    preAllocatedVUs, maxVUs, gracefulStop: '10s',
  };
}

function loadOpenAuctions(session) {
  const configured = csv(__ENV.AUCTION_IDS).map(Number).filter(Number.isInteger).map(id => ({id}));
  if (configured.length > 0) return configured;
  const response = http.get(`${baseUrl}/api/auctions?size=100`, {headers: {Cookie: `SESSION=${session.cookie}`}, tags: {name: 'GET /api/auctions (setup)'}});
  if (response.status !== 200) throw new Error(`경매 자동 조회 실패 (status=${response.status})`);
  const content = response.json('content');
  return Array.isArray(content) ? content.filter(auction => auction.status === 'OPEN' || auction.status === 'ENDING').map(auction => ({id: auction.id})) : [];
}

// 세션 인증(#469 이후): 로그인 응답은 accessToken이 아니라 Set-Cookie(SESSION)와
// csrfToken을 준다. setup()은 VU 컨텍스트 밖이라 응답 쿠키가 어느 VU의 쿠키jar에도
// 안 들어가므로, 쿠키 값을 직접 뽑아 매 요청에 Cookie 헤더로 수동 첨부한다.
function login(users) {
  const sessions = [];
  for (let start = 0; start < users.length; start += loginBatchSize) {
    const responses = http.batch(users.slice(start, start + loginBatchSize).map(user => ({method: 'POST', url: `${baseUrl}/api/auth/login`, body: JSON.stringify(user), params: {headers: {'Content-Type': 'application/json'}, responseCallback: http.expectedStatuses(200)}})));
    responses.forEach((response, index) => {
      if (response.status !== 200) throw new Error(`로그인 실패 (index=${start + index}, status=${response.status})`);
      const cookie = response.cookies.SESSION && response.cookies.SESSION[0] && response.cookies.SESSION[0].value;
      if (!cookie) throw new Error(`세션 쿠키를 받지 못했습니다 (index=${start + index})`);
      sessions.push({cookie, csrfToken: response.json('csrfToken')});
    });
  }
  return sessions;
}

function loadTestUsers() { return Array.from({length: userCount}, (_, index) => ({email: `k6-user${String(index + 1).padStart(5, '0')}@dbidding.local`, password: __ENV.LOAD_TEST_PASSWORD || 'K6LoadTest123!'})); }
function sessionOf(sessions) { return sessions[(__VU - 1) % sessions.length]; }
// 조회(GET)는 세션 쿠키만 있으면 된다.
function authorization(sessions) { return {Cookie: `SESSION=${sessionOf(sessions).cookie}`}; }
// 상태변경(POST/PUT/PATCH/DELETE)은 SessionCsrfFilter가 쿠키 + X-CSRF-Token을 같이 요구한다.
function writeHeaders(sessions) { const session = sessionOf(sessions); return {Cookie: `SESSION=${session.cookie}`, 'X-CSRF-Token': session.csrfToken}; }
function randomAuction(auctions) { return auctions[Math.floor(Math.random() * auctions.length)]; }
function targetAuction(auctions) { return hotAuctionId !== null ? {id: hotAuctionId} : randomAuction(auctions); }
function positiveIntOrNull(value) { const parsed = Number(value); return Number.isInteger(parsed) && parsed > 0 ? parsed : null; }
function idempotencyKey(auctionId) { return `k6-bidonly-${auctionId}-${__VU}-${__ITER}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`; }
function csv(value) { return (value || '').split(',').map(item => item.trim()).filter(Boolean); }
function positiveInt(value, fallback) { const parsed = Number(value); return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback; }
function qpsStageTargets(value) {
  const parsed = csv(value).map(Number).filter(n => Number.isFinite(n) && n > 0);
  return parsed.length > 0 ? parsed : [50, 100, 150, 200, 300, 400];
}
function summaryText(data) { const values = data.metrics.http_reqs?.values || {}; return `\n=== BID-ONLY (NO SSE) SUMMARY ===\nHTTP 요청: ${values.count || 0} (${(values.rate || 0).toFixed(2)} req/s)\n`; }
