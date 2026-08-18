import http from 'k6/http';
import sse from 'k6/x/sse';
import {sleep} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';

// 순수 SSE fan-out 부하테스트(#569) — 실제 입찰 처리 없이 /api/test/sse-fanout/random-bid-event
// (경매 브로드캐스트 + notification/wallet push를 전부 실제 Redis publish 경로로 발행)만 호출해서
// threadpool(up-all-redis-sse.sh) vs virtual thread(up-all-redis-sse-virtual.sh) 프로필 간
// fan-out 비용을 비교한다. 경매 15개 · 경매당 입찰자 10명(총 150명, notification/wallet 대상) ·
// auction 구독자 500명(전원이 15개 경매 전부 구독) · 이벤트 QPS 130 고정 부하가 설계 기본값.
const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');

const auctionCount = 15;
const biddersPerAuction = positiveInt(__ENV.BIDDERS_PER_AUCTION, 10);
const biddersTotal = auctionCount * biddersPerAuction;
const sseSubscribers = positiveInt(__ENV.SSE_SUBSCRIBERS, 500);
const eventQps = positiveInt(__ENV.EVENT_QPS, 130);
const duration = __ENV.DURATION || '3m';
const sseRampUp = __ENV.SSE_RAMP_UP || '30s';
const sseDuration = __ENV.SSE_DURATION || addDurations(duration, '30s');
const mainStartTime = __ENV.MAIN_START_TIME || addDurations(sseRampUp, '5s');
const loginBatchSize = positiveInt(__ENV.LOGIN_BATCH_SIZE, 25);
const preAllocatedVUs = positiveInt(__ENV.PRE_ALLOCATED_VUS, 50);
const maxVUs = positiveInt(__ENV.MAX_VUS, 300);
const resultFile = __ENV.K6_RESULT_FILE;

const auctionSseConnected = new Rate('auction_sse_connected');
const auctionSseEvents = new Counter('auction_sse_events');
const auctionSseDeliveryLatency = new Trend('auction_sse_delivery_latency');
const auctionSseDeliveryTimestampInvalid = new Counter('auction_sse_delivery_timestamp_invalid');
// 알림+지갑 SSE가 /api/me/stream 하나로 합쳐져 있다(#557).
const meSseConnected = new Rate('me_sse_connected');
const meSseEvents = new Counter('me_sse_events');
const meSseDeliveryLatency = new Trend('me_sse_delivery_latency');
const meSseDeliveryTimestampInvalid = new Counter('me_sse_delivery_timestamp_invalid');
const sseBarrierReady = new Rate('sse_barrier_ready');
const fanoutPublishSuccess = new Rate('fanout_publish_success');
const fanoutServerError = new Rate('fanout_server_error');

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '5m',
  batchPerHost: loginBatchSize,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    auctionSse: {
      executor: 'ramping-vus', exec: 'auctionSse', startVUs: 0,
      stages: [{target: sseSubscribers, duration: sseRampUp}, {target: sseSubscribers, duration: sseDuration}],
      gracefulRampDown: '5s',
    },
    meSse: {
      executor: 'ramping-vus', exec: 'meSse', startVUs: 0,
      stages: [{target: biddersTotal, duration: sseRampUp}, {target: biddersTotal, duration: sseDuration}],
      gracefulRampDown: '5s',
    },
    sseReadiness: {
      executor: 'per-vu-iterations', exec: 'waitForSse', vus: 1, iterations: 1,
      maxDuration: mainStartTime, gracefulStop: '5s',
    },
    eventPublisher: {
      executor: 'constant-arrival-rate', exec: 'publishEvent',
      startTime: mainStartTime, rate: eventQps, timeUnit: '1s', duration,
      preAllocatedVUs, maxVUs, gracefulStop: '10s',
    },
  },
  thresholds: {
    auction_sse_connected: ['rate>0.99'],
    me_sse_connected: ['rate>0.99'],
    sse_barrier_ready: ['rate>0.99'],
    fanout_publish_success: ['rate>0.99'],
    fanout_server_error: ['rate<0.01'],
  },
};

export function setup() {
  const auctionIds = configuredAuctionIds();
  if (auctionIds.length !== auctionCount) {
    throw new Error(`AUCTION_IDS에 서로 다른 진행 중 경매 ID ${auctionCount}개를 지정하세요.`);
  }
  const sessions = login(loadTestUsers(biddersTotal));
  const userIds = resolveUserIds(sessions);
  const bidders = sessions.map((session, index) => ({...session, userId: userIds[index]}));
  const biddersByAuction = {};
  auctionIds.forEach((auctionId, index) => {
    biddersByAuction[auctionId] = bidders.slice(index * biddersPerAuction, (index + 1) * biddersPerAuction);
  });
  return {auctionIds, biddersByAuction};
}

export function auctionSse(data) {
  const url = `${baseUrl}/api/auctions/stream?auctionIds=${data.auctionIds.join(',')}`;
  sse.open(url, {headers: {Accept: 'text/event-stream'}, tags: {name: 'GET /api/auctions/stream'}}, client => {
    client.on('open', () => auctionSseConnected.add(true));
    client.on('event', event => {
      auctionSseEvents.add(1);
      recordDeliveryLatency(event.data, 'published_at', auctionSseDeliveryLatency, auctionSseDeliveryTimestampInvalid);
    });
    client.on('error', () => auctionSseConnected.add(false));
  });
}

export function meSse(data) {
  const session = sessionOf(data);
  const url = `${baseUrl}/api/me/stream`;
  sse.open(url, {headers: {Accept: 'text/event-stream', Cookie: `SESSION=${session.cookie}`}, tags: {name: 'GET /api/me/stream'}}, client => {
    client.on('open', () => meSseConnected.add(true));
    client.on('event', event => {
      meSseEvents.add(1);
      const timestampField = event.name === 'wallet-state-changed' ? 'updated_at' : 'createdAt';
      recordDeliveryLatency(event.data, timestampField, meSseDeliveryLatency, meSseDeliveryTimestampInvalid);
    });
    client.on('error', () => meSseConnected.add(false));
  });
}

export function waitForSse() {
  const deadline = Date.now() + durationToSeconds(mainStartTime) * 1000;
  while (true) {
    const response = http.get(`${baseUrl}/api/test/load/sse-status?expected=1`, {tags: {name: 'GET /api/test/load/sse-status'}});
    const ready = response.status === 200
      && response.json('auctionConnected') >= sseSubscribers
      && response.json('notificationConnected') >= biddersTotal;
    if (ready) { sseBarrierReady.add(true); return; }
    if (Date.now() >= deadline) { sseBarrierReady.add(false); return; }
    sleep(1);
  }
}

export function publishEvent(data) {
  const auctionId = data.auctionIds[Math.floor(Math.random() * data.auctionIds.length)];
  const bidders = data.biddersByAuction[auctionId];
  const [outbid, newBidder] = pickTwoDistinct(bidders);
  const response = http.post(
    `${baseUrl}/api/test/sse-fanout/random-bid-event?auctionId=${auctionId}&outbidUserId=${outbid.userId}&newBidderUserId=${newBidder.userId}`,
    null,
    {responseCallback: http.expectedStatuses(202, 404, 500), tags: {name: 'POST /api/test/sse-fanout/random-bid-event'}},
  );
  fanoutPublishSuccess.add(response.status === 202);
  fanoutServerError.add(response.status >= 500);
}

export function handleSummary(data) {
  const result = {
    generatedAt: new Date().toISOString(),
    scenario: 'pure-fanout',
    testConfig: {auctionCount, biddersPerAuction, biddersTotal, sseSubscribers, eventQps, duration},
    ...data,
  };
  return resultFile ? {[resultFile]: JSON.stringify(result, null, 2), stdout: summaryText(data)} : {stdout: summaryText(data)};
}

function recordDeliveryLatency(rawData, timestampField, latencyMetric, invalidMetric) {
  try {
    const payload = JSON.parse(rawData);
    const timestamp = Date.parse(payload[timestampField]);
    if (!Number.isFinite(timestamp)) { invalidMetric.add(1); return; }
    latencyMetric.add(Date.now() - timestamp);
  } catch {
    invalidMetric.add(1);
  }
}

function pickTwoDistinct(pool) {
  const first = Math.floor(Math.random() * pool.length);
  let second = Math.floor(Math.random() * (pool.length - 1));
  if (second >= first) second += 1;
  return [pool[first], pool[second]];
}

function sessionOf(data) {
  const bidders = data.auctionIds.flatMap(auctionId => data.biddersByAuction[auctionId]);
  return bidders[(__VU - 1) % bidders.length];
}

// 세션 인증(#469 이후): 로그인 응답은 accessToken이 아니라 Set-Cookie(SESSION)와
// csrfToken을 준다. setup()은 VU 컨텍스트 밖이라 응답 쿠키가 어느 VU의 쿠키jar에도
// 안 들어가므로, 쿠키 값을 직접 뽑아 매 요청에 Cookie 헤더로 수동 첨부한다.
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

// setup()은 VU 하나로 취급되어 쿠키jar를 공유한다 — 배치 안의 서로 다른 유저 로그인이
// 이 jar를 같이 쓰면 응답이 뒤섞일 수 있어(hot-auction-pattern.js에서 실측된 문제),
// 요청마다 독립된 빈 jar를 준다.
function loginBatchWithRetry(users, attempt = 0) {
  const responses = http.batch(users.map(user => ({method: 'POST', url: `${baseUrl}/api/auth/login`, body: JSON.stringify(user), params: {headers: {'Content-Type': 'application/json'}, jar: new http.CookieJar(), responseCallback: http.expectedStatuses(200, 500)}})));
  const failedIndexes = responses.reduce((acc, response, index) => { if (response.status === 500) acc.push(index); return acc; }, []);
  if (failedIndexes.length === 0 || attempt >= 3) return responses;
  const retried = loginBatchWithRetry(failedIndexes.map(index => users[index]), attempt + 1);
  failedIndexes.forEach((originalIndex, i) => { responses[originalIndex] = retried[i]; });
  return responses;
}

function resolveUserIds(sessions) {
  const responses = http.batch(sessions.map(session => ({
    method: 'GET', url: `${baseUrl}/api/auth/me`,
    params: {headers: {Cookie: `SESSION=${session.cookie}`}, tags: {name: 'GET /api/auth/me (setup)'}},
  })));
  return responses.map((response, index) => {
    if (response.status !== 200) throw new Error(`유저 정보 조회 실패 (index=${index}, status=${response.status})`);
    return response.json('userId');
  });
}

function loadTestUsers(count) {
  return Array.from({length: count}, (_, index) => ({
    email: `k6-user${String(index + 1).padStart(5, '0')}@dbidding.local`,
    password: __ENV.LOAD_TEST_PASSWORD || 'K6LoadTest123!',
  }));
}

function configuredAuctionIds() { return [...new Set(csv(__ENV.AUCTION_IDS).map(Number).filter(id => Number.isInteger(id) && id > 0))]; }
function csv(value) { return (value || '').split(',').map(item => item.trim()).filter(Boolean); }
function positiveInt(value, fallback) { const parsed = Number(value); return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback; }
function durationToSeconds(value) { const match = String(value).match(/^(\d+)(ms|s|m|h)$/); if (!match) throw new Error(`duration 형식 오류: ${value}`); return Number(match[1]) * ({ms: 0.001, s: 1, m: 60, h: 3600}[match[2]]); }
function addDurations(first, second) { return `${durationToSeconds(first) + durationToSeconds(second)}s`; }
function summaryText(data) { const values = data.metrics.fanout_publish_success?.values || {}; return `\n=== PURE SSE FAN-OUT SUMMARY ===\n이벤트 발행: ${values.rate ? (values.rate * 100).toFixed(2) : 0}% 성공\n`; }
