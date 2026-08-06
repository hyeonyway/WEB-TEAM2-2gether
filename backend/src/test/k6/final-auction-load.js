import http from 'k6/http';
import sse from 'k6/x/sse';
import {check, sleep} from 'k6';
import {Counter, Rate} from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const users = positiveInt(__ENV.USERS, 1000);
// 실제 입찰 1회가 경매 이벤트와 알림 이벤트를 함께 발생시킨다.
const bidRate = positiveInt(__ENV.BID_RATE, 1000);
const duration = __ENV.DURATION || '1m';
const sseDuration = __ENV.SSE_DURATION || duration;
const batchSize = positiveInt(__ENV.LOGIN_BATCH_SIZE, 25);
const auctionIds = csv(__ENV.AUCTION_IDS).map(Number).filter(Number.isInteger);

const loginOk = new Rate('login_success');
const bidOk = new Rate('bid_success_or_conflict');
const auctionSseOk = new Rate('auction_sse_connected');
const notificationSseOk = new Rate('notification_sse_connected');
const auctionEvents = new Counter('auction_sse_events');
const notificationEvents = new Counter('notification_sse_events');

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '15m',
  batchPerHost: batchSize,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    auctionSse: {executor: 'constant-vus', exec: 'auctionSse', vus: users, duration: sseDuration, gracefulStop: '5s'},
    notificationSse: {executor: 'constant-vus', exec: 'notificationSse', vus: users, duration: sseDuration, gracefulStop: '5s'},
    bids: {executor: 'constant-arrival-rate', exec: 'bid', rate: bidRate, timeUnit: '1s', duration, preAllocatedVUs: 250, maxVUs: 1000, tags: {phase: 'main'}},
  },
  thresholds: {
    'login_success': ['rate>0.99'],
    'auction_sse_connected': ['rate>0.99'],
    'notification_sse_connected': ['rate>0.99'],
    'bid_success_or_conflict{phase:main}': ['rate>0.99'],
  },
};

export function setup() {
  const credentials = loadCredentials();
  const tokens = login(credentials);
  const tickets = issueTickets(tokens);
  if (auctionIds.length === 0) throw new Error('AUCTION_IDS에 200개 경매 ID를 지정하세요.');
  console.log(`[setup] 로그인 ${tokens.length}명, SSE 티켓 ${tickets.length}개, 경매 ${auctionIds.length}개 준비 완료`);
  return {tokens, tickets, userIds: credentials.map((_, i) => 910001 + i)};
}

export function auctionSse() {
  sse.open(`${baseUrl}/api/auctions/stream`, {headers: {Accept: 'text/event-stream'}, tags: {name: 'auction_sse'}}, client => {
    client.on('open', () => auctionSseOk.add(true));
    client.on('event', () => auctionEvents.add(1));
    client.on('error', () => auctionSseOk.add(false));
  });
}

export function notificationSse(data) {
  const i = (__VU - 1) % data.tickets.length;
  const token = data.tokens[i];
  const ticketResponse = http.post(`${baseUrl}/api/sse/tickets`, null, {
    headers: {Authorization: `Bearer ${token}`, Accept: 'application/json'},
    responseCallback: http.expectedStatuses(200),
    tags: {name: 'notification_sse_ticket'},
  });
  if (ticketResponse.status !== 200) return;
  const ticket = ticketResponse.json('ticket');
  sse.open(`${baseUrl}/api/users/${data.userIds[i]}/notifications/stream?ticket=${encodeURIComponent(ticket)}`, {headers: {Accept: 'text/event-stream'}, tags: {name: 'notification_sse'}}, client => {
    client.on('open', () => notificationSseOk.add(true));
    client.on('event', () => notificationEvents.add(1));
    client.on('error', () => notificationSseOk.add(false));
  });
}

export function bid(data) {
  if (__ITER === 0) waitForSse(data.tokens[0]);
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  placeBid(token, auctionIds[Math.floor(Math.random() * auctionIds.length)], 'bid');
}

function waitForSse(token) {
  while (true) {
    const response = http.get(`${baseUrl}/api/test/load/sse-status?expected=${users}`, {
      headers: {Authorization: `Bearer ${token}`}, tags: {name: 'sse_barrier'},
    });
    if (response.status === 200 && response.json('ready') === true) return;
    sleep(1);
  }
}

function placeBid(token, auctionId, metricName) {
  const headers = {Authorization: `Bearer ${token}`, Accept: 'application/json'};
  const context = http.get(`${baseUrl}/api/auctions/${auctionId}/bid-context`, {headers, tags: {name: `${metricName}_context`}});
  if (context.status !== 200) { if (metricName === 'bid') bidOk.add(false); return false; }
  const price = Number(context.json('minimum_bid'));
  if (!Number.isSafeInteger(price) || price < 1) { if (metricName === 'bid') bidOk.add(false); return false; }
  const response = http.post(`${baseUrl}/api/auctions/${auctionId}/bids`, JSON.stringify({price}), {headers: {...headers, 'Content-Type': 'application/json', 'Idempotency-Key': `k6-${metricName}-${__VU}-${__ITER}-${Date.now()}`}, responseCallback: http.expectedStatuses(201, 409), tags: {name: `${metricName}_place`, phase: 'main'}});
  const accepted = response.status === 201 || response.status === 409;
  if (metricName === 'bid') bidOk.add(accepted);
  check(response, {'실제 경매 API 응답 성공 또는 경쟁 충돌': r => accepted});
  return accepted;
}

function login(credentials) {
  const tokens = [];
  const startedAt = Date.now();
  progress(0, credentials.length, startedAt);
  for (let start = 0; start < credentials.length; start += batchSize) {
    const responses = http.batch(credentials.slice(start, start + batchSize).map(c => ({method: 'POST', url: `${baseUrl}/api/auth/login`, body: JSON.stringify(c), params: {headers: {'Content-Type': 'application/json'}, responseCallback: http.expectedStatuses(200)}})));
    responses.forEach(r => { const ok = r.status === 200; loginOk.add(ok); if (!ok) throw new Error(`로그인 실패: ${r.status}`); tokens.push(r.json('accessToken')); });
    progress(tokens.length, credentials.length, startedAt);
  }
  console.log('');
  return tokens;
}

function progress(completed, total, startedAt) {
  const percent = ((completed / total) * 100).toFixed(1);
  const elapsed = ((Date.now() - startedAt) / 1000).toFixed(1);
  const message = `\r로그인 진행 중: ${completed}/${total} (${percent}%) | 경과 ${elapsed}s`;
  // ANSI 커서 제어를 지원하는 터미널에서는 이전 진행 문구를 지우고 같은 줄을 갱신한다.
  console.log(`\u001b[2K\u001b[1G${message}`);
}

function issueTickets(tokens) {
  const tickets = [];
  for (let start = 0; start < tokens.length; start += batchSize) {
    const responses = http.batch(tokens.slice(start, start + batchSize).map(token => ({method: 'POST', url: `${baseUrl}/api/sse/tickets`, params: {headers: {Authorization: `Bearer ${token}`}, responseCallback: http.expectedStatuses(200)}})));
    responses.forEach(r => { if (r.status !== 200) throw new Error(`SSE 티켓 발급 실패: ${r.status}`); tickets.push(r.json('ticket')); });
  }
  return tickets;
}

function loadCredentials() { return Array.from({length: users}, (_, i) => ({email: `k6-user${String(i + 1).padStart(5, '0')}@dbidding.local`, password: __ENV.PASSWORD || 'K6LoadTest123!'})); }
function csv(v) { return (v || '').split(',').map(x => x.trim()).filter(Boolean); }
function positiveInt(v, fallback) { const n = Number(v); return Number.isInteger(n) && n > 0 ? n : fallback; }
function addDurations(a, b) {
  const seconds = value => { const m = String(maybeValue(value)).match(/^(\d+)(ms|s|m|h)$/); if (!m) throw new Error(`duration 형식 오류: ${value}`); return Number(m[1]) * ({ms: 0.001, s: 1, m: 60, h: 3600}[m[2]]); };
  return `${seconds(a) + seconds(b)}s`;
}
function maybeValue(value) { return value; }
