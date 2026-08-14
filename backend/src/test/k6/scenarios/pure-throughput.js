import http from 'k6/http';
import sse from 'k6/x/sse';
import {check, sleep} from 'k6';
import {Counter, Rate} from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const sseVUs = sseTier(__ENV.SSE_VUS, 250);
const stageDuration = __ENV.STAGE_DURATION || '2m';
// QPS_STAGES로 재현할 계단 구간만 골라 돌릴 수 있다(예: QPS_STAGES=200,300,400).
// 지정 안 하면 기본 전체 계단(50~400)을 돈다.
const qpsStages = qpsStageTargets(__ENV.QPS_STAGES).map(rate => ({target: rate, duration: stageDuration}));
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
  const sessions = login(loadTestUsers());
  const auctions = loadOpenAuctions(sessions[0]);
  if (auctions.length === 0) throw new Error('진행 중인 경매가 없습니다. AUCTION_IDS를 지정하거나 시드 데이터를 확인하세요.');
  return {sessions, auctions};
}

export function auctionSse(data) {
  // /api/auctions/stream이 선택 구독으로 바뀌어(feature/390) auctionIds가 필수다
  // (최대 15개, 콤마 구분). 목록 페이지에서 보이는 경매 몇 개를 구독하는 상황을
  // 흉내내 매 VU가 무작위 최대 15개를 고른다.
  const auctionIds = subscribedAuctionIds(data.auctions);
  const url = `${baseUrl}/api/auctions/stream?auctionIds=${auctionIds.join(',')}`;
  sse.open(url, {headers: {Accept: 'text/event-stream'}, tags: {name: 'GET /api/auctions/stream'}}, client => {
    client.on('open', () => sseAuctionConnectSuccess.add(true));
    client.on('error', () => sseAuctionConnectSuccess.add(false));
  });
}

function subscribedAuctionIds(auctions) {
  const shuffled = auctions.map(auction => auction.id).sort(() => Math.random() - 0.5);
  return shuffled.slice(0, Math.min(15, shuffled.length));
}

export function notificationSse(data) {
  // 세션 인증(#469 이후): 티켓 발급(POST /api/sse/tickets) 없이 세션 쿠키로 바로 연결한다.
  // 개인화 여부는 서버가 세션에서 판별하므로 URL에 userId도 필요 없다.
  const session = sessionOf(data.sessions);
  sse.open(`${baseUrl}/api/me/notifications/stream`, {headers: {Accept: 'text/event-stream', Cookie: `SESSION=${session.cookie}`}, tags: {name: 'GET /api/me/notifications/stream'}}, client => {
    client.on('open', () => sseNotificationConnectSuccess.add(true));
    client.on('error', () => sseNotificationConnectSuccess.add(false));
  });
}

export function bidContextRead(data) {
  const auction = randomAuction(data.auctions);
  http.get(`${baseUrl}/api/auctions/${auction.id}/bid-context`, {
    headers: authorization(data.sessions),
    responseCallback: http.expectedStatuses(200),
    tags: {name: 'GET /api/auctions/:id/bid-context'},
  });
}

export function bidWrite(data) {
  const auction = randomAuction(data.auctions);
  const headers = authorization(data.sessions);
  // setup() 때 잡아둔 minimumBid는 테스트 도중 다른 VU들이 계속 입찰하면서 바로 stale해진다.
  // 매번 최신 minimum_bid를 다시 조회해야 정책적 거부(400)에 다 튕기지 않는다.
  const context = http.get(`${baseUrl}/api/auctions/${auction.id}/bid-context`, {
    headers,
    responseCallback: http.expectedStatuses(200),
    tags: {name: 'GET /api/auctions/:id/bid-context', scenario: 'bidWrites'},
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
    scenario: 'pure-throughput',
    configuration: {sseVUs, totalSseConnections: sseVUs * 2, qpsStages: qpsStages.map(stage => stage.target), stageDuration},
    ...data,
  };
  return resultFile ? {[resultFile]: JSON.stringify(result, null, 2), stdout: summaryText(data)} : {stdout: summaryText(data)};
}

function arrivalScenario(exec, share) {
  return {
    executor: 'ramping-arrival-rate', exec, startTime: mainStartTime, startRate: Math.round(qpsStages[0].target * share), timeUnit: '1s',
    stages: qpsStages.map(stage => ({target: Math.round(stage.target * share), duration: stage.duration})),
    preAllocatedVUs, maxVUs, gracefulStop: '10s',
  };
}

function loadOpenAuctions(session) {
  const configured = csv(__ENV.AUCTION_IDS).map(Number).filter(Number.isInteger).map(id => ({id, minimumBid: positiveInt(__ENV.DEFAULT_BID_PRICE, 1000000)}));
  if (configured.length > 0) return configured;
  const response = http.get(`${baseUrl}/api/auctions?size=100`, {headers: {Cookie: `SESSION=${session.cookie}`}, tags: {name: 'GET /api/auctions (setup)'}});
  if (response.status !== 200) throw new Error(`경매 자동 조회 실패 (status=${response.status})`);
  const content = response.json('content');
  return Array.isArray(content) ? content.filter(auction => auction.status === 'OPEN' || auction.status === 'ENDING').map(auction => ({id: auction.id, minimumBid: positiveInt(auction.current_price || auction.start_price, 1000000)})) : [];
}

// 세션 인증(#469 이후): 로그인 응답은 accessToken이 아니라 Set-Cookie(SESSION)와
// csrfToken을 준다. setup()은 VU 컨텍스트 밖이라 응답 쿠키가 어느 VU의 쿠키jar에도
// 안 들어가므로, 쿠키 값을 직접 뽑아 매 요청에 Cookie 헤더로 수동 첨부한다.
// #500: 동시 신규 로그인 버스트에서 Spring Session Redis 세션 생성 경합으로
// 가끔 500이 남(일시적, 재시도하면 대부분 성공) — setup 단계에서만 재시도로 완화.
function login(users) {
  const sessions = [];
  for (let start = 0; start < users.length; start += loginBatchSize) {
    const responses = loginBatchWithRetry(users.slice(start, start + loginBatchSize));
    responses.forEach((response, index) => {
      if (response.status !== 200) throw new Error(`로그인 실패 (index=${start + index}, status=${response.status})`);
      const cookie = response.cookies.SESSION && response.cookies.SESSION[0] && response.cookies.SESSION[0].value;
      if (!cookie) throw new Error(`세션 쿠키를 받지 못했습니다 (index=${start + index})`);
      sessions.push({cookie, csrfToken: response.json('csrfToken')});
    });
  }
  return sessions;
}

function loginBatchWithRetry(users, attempt = 0) {
  const responses = http.batch(users.map(user => ({method: 'POST', url: `${baseUrl}/api/auth/login`, body: JSON.stringify(user), params: {headers: {'Content-Type': 'application/json'}, responseCallback: http.expectedStatuses(200, 500)}})));
  const failedIndexes = responses.reduce((acc, response, index) => { if (response.status === 500) acc.push(index); return acc; }, []);
  if (failedIndexes.length === 0 || attempt >= 3) return responses;
  const retried = loginBatchWithRetry(failedIndexes.map(index => users[index]), attempt + 1);
  failedIndexes.forEach((originalIndex, i) => { responses[originalIndex] = retried[i]; });
  return responses;
}

function loadTestUsers() { return Array.from({length: userCount}, (_, index) => ({email: `k6-user${String(index + 1).padStart(5, '0')}@dbidding.local`, password: __ENV.LOAD_TEST_PASSWORD || 'K6LoadTest123!'})); }
function sessionOf(sessions) { return sessions[(__VU - 1) % sessions.length]; }
// 조회(GET)는 세션 쿠키만 있으면 된다.
function authorization(sessions) { return {Cookie: `SESSION=${sessionOf(sessions).cookie}`}; }
// 상태변경(POST/PUT/PATCH/DELETE)은 SessionCsrfFilter가 쿠키 + X-CSRF-Token을 같이 요구한다.
function writeHeaders(sessions) { const session = sessionOf(sessions); return {Cookie: `SESSION=${session.cookie}`, 'X-CSRF-Token': session.csrfToken}; }
function randomAuction(auctions) { return auctions[Math.floor(Math.random() * auctions.length)]; }
function idempotencyKey(auctionId) { return `k6-throughput-${auctionId}-${__VU}-${__ITER}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`; }
function csv(value) { return (value || '').split(',').map(item => item.trim()).filter(Boolean); }
function qpsStageTargets(value) {
  const parsed = csv(value).map(Number).filter(n => Number.isFinite(n) && n > 0);
  return parsed.length > 0 ? parsed : [50, 100, 150, 200, 300, 400];
}
function positiveInt(value, fallback) { const parsed = Number(value); return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback; }
function sseTier(value, fallback) { const tier = positiveInt(value, fallback); if (![250, 500, 1000].includes(tier)) throw new Error('SSE_VUS는 250, 500, 1000 중 하나여야 합니다.'); return tier; }
function totalDuration() { return `${qpsStages.reduce((seconds, stage) => seconds + durationToSeconds(stage.duration), durationToSeconds(sseRampUp) + 10)}s`; }
function addDurations(first, second) { return `${durationToSeconds(first) + durationToSeconds(second)}s`; }
function durationToSeconds(value) { const match = String(value).match(/^(\d+)(ms|s|m|h)$/); if (!match) throw new Error(`duration 형식 오류: ${value}`); return Number(match[1]) * ({ms: 0.001, s: 1, m: 60, h: 3600}[match[2]]); }
function summaryText(data) { const values = data.metrics.http_reqs?.values || {}; return `\n=== PURE THROUGHPUT SUMMARY ===\nHTTP 요청: ${values.count || 0} (${(values.rate || 0).toFixed(2)} req/s)\n`; }
