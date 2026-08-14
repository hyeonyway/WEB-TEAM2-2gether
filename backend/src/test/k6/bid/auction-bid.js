import http from 'k6/http';
import sse from 'k6/x/sse';
import {check} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const requestRate = positiveNumber(__ENV.RATE, 100);
const duration = __ENV.DURATION || '1m';
const preAllocatedVUs = positiveNumber(__ENV.PRE_ALLOCATED_VUS, 100);
const maxVUs = positiveNumber(__ENV.MAX_VUS, 300);
const warmupRate = positiveNumber(__ENV.WARMUP_RATE, 20);
const warmupDuration = __ENV.WARMUP_DURATION || '30s';
// realAuctionBids는 warmup이 끝나고 5초 뒤에 시작한다(직접 겹치지 않게 여유만 둠).
const mainStartTime = __ENV.MAIN_START_TIME || formatSecondsAsDuration(parseDurationToSeconds(warmupDuration) + 5);
const loadTestUserCount = positiveInteger(__ENV.LOAD_TEST_USER_COUNT, 10);
const loadTestEmailPrefix = __ENV.LOAD_TEST_EMAIL_PREFIX || 'k6-user';
const loadTestEmailDomain = __ENV.LOAD_TEST_EMAIL_DOMAIN || 'dbidding.local';
const loadTestPassword = __ENV.LOAD_TEST_PASSWORD || 'K6LoadTest123!';
const loginBatchSize = positiveInteger(__ENV.LOGIN_BATCH_SIZE, 10);
const resultFile = __ENV.K6_RESULT_FILE;
// SSE_VUS=0으로 SSE 부하를 완전히 끄고 입찰만 격리해서 볼 수 있어야 하므로
// positiveInteger(0을 걸러냄)가 아니라 nonNegativeInteger를 쓴다.
const sseVUs = nonNegativeInteger(__ENV.SSE_VUS, 300);
// SSE 시나리오는 t=0에 시작하니, realAuctionBids가 끝나는 시점(mainStartTime + duration)에
// 맞춰서 같이 끝나도록 기본값을 잡는다. 예전엔 2m45s로 고정이라 bids가 다 끝나도 한참
// 더 돌면서 화면에 진행바가 남았다.
const sseDuration = __ENV.SSE_DURATION
  || formatSecondsAsDuration(parseDurationToSeconds(mainStartTime) + parseDurationToSeconds(duration));
const loadTestUserIdStart = positiveInteger(__ENV.LOAD_TEST_USER_ID_START, 910001);
const loadTestUserNumberWidth = positiveInteger(__ENV.LOAD_TEST_USER_NUMBER_WIDTH, 5);
// AuctionSseController#stream의 auctionIds Set은 @Size(max=16)이라 VU당 이 이상 못 붙인다.
const maxAuctionSseSubscriptions = 16;

const bidAccepted = new Rate('bid_accepted');
const bidAcceptedOrContended = new Rate('bid_accepted_or_contended');
const bidServerError = new Rate('bid_server_error');
const bidContentions = new Counter('bid_contentions');
const bidRejected = new Counter('bid_rejected');
const bidEndToEndDuration = new Trend('bid_end_to_end_duration', true);
const sseAuctionConnectSuccess = new Rate('sse_auction_connect_success');
const sseAuctionConnectionErrors = new Counter('sse_auction_connection_errors');
const sseAuctionEvents = new Counter('sse_auction_events');
const auctionSseDeliveryLatency = new Trend('auction_sse_delivery_latency', true);
const auctionSseDeliveryTimestampInvalid = new Counter('auction_sse_delivery_timestamp_invalid');
const sseNotificationConnectSuccess = new Rate('sse_notification_connect_success');
const sseNotificationConnectionErrors = new Counter('sse_notification_connection_errors');
const sseNotificationEvents = new Counter('sse_notification_events');

// SSE_VUS=0이면 SSE 시나리오 자체를 scenarios에서 빼서, 입찰 부하만
// 격리해서 볼 수 있게 한다(k6 constant-vus executor는 vus:0을 허용하지 않음).
const scenarios = {
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
};
if (sseVUs > 0) {
  scenarios.auctionSseConnections = {
    executor: 'constant-vus',
    exec: 'auctionSse',
    vus: sseVUs,
    duration: sseDuration,
    gracefulStop: '5s',
  };
  scenarios.notificationSseConnections = {
    executor: 'constant-vus',
    exec: 'notificationSse',
    vus: sseVUs,
    duration: sseDuration,
    gracefulStop: '5s',
  };
}

const thresholds = {
  'checks{scenario:realAuctionBids}': ['rate>0.99'],
  'bid_server_error{scenario:realAuctionBids}': ['rate<0.01'],
  'http_req_failed{scenario:realAuctionBids}': ['rate<0.01'],
  'http_req_duration{name:GET /api/auctions/:id/bid-context,scenario:realAuctionBids}': ['p(95)<500'],
  'http_req_duration{name:POST /api/auctions/:id/bids,scenario:realAuctionBids}': ['p(95)<1000'],
};
if (sseVUs > 0) {
  thresholds['sse_auction_connect_success'] = ['rate>0.99'];
  thresholds['sse_notification_connect_success'] = ['rate>0.99'];
}

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '10m',
  batchPerHost: loginBatchSize,
  summaryTrendStats: ['avg', 'min', 'med', 'p(85)', 'p(95)', 'p(99)', 'max'],
  scenarios,
  thresholds,
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

  const notificationTickets = issueNotificationTickets(tokens);
  const notificationUserIds = loadNotificationUserIds(tokens.length);
  return {tokens, auctionIds, notificationTickets, notificationUserIds};
}

export function warmup(data) {
  performBid(data);
}

export function bid(data) {
  performBid(data);
}

export function auctionSse({auctionIds}) {
  const query = subscribedAuctionIds(auctionIds).map(id => `auctionIds=${id}`).join('&');
  sse.open(`${baseUrl}/api/auctions/stream?${query}`, {
    headers: {Accept: 'text/event-stream'},
    tags: {name: 'GET /api/auctions/stream'},
  }, client => {
    client.on('open', () => sseAuctionConnectSuccess.add(true));
    client.on('error', () => {
      sseAuctionConnectionErrors.add(1);
      sseAuctionConnectSuccess.add(false);
    });
    client.on('event', event => {
      sseAuctionEvents.add(1);
      recordAuctionSseDeliveryLatency(event);
    });
  });
}

function recordAuctionSseDeliveryLatency(event) {
  if (!['AUCTION_CREATED', 'BID_PLACED', 'AUCTION_CLOSED'].includes(event.name)) return;
  try {
    const publishedAt = Date.parse(JSON.parse(event.data).published_at);
    const latency = Date.now() - publishedAt;
    if (!Number.isFinite(publishedAt) || latency < 0) {
      auctionSseDeliveryTimestampInvalid.add(1);
      return;
    }
    auctionSseDeliveryLatency.add(latency);
  } catch {
    auctionSseDeliveryTimestampInvalid.add(1);
  }
}

export function notificationSse({notificationTickets, notificationUserIds}) {
  const index = (__VU - 1) % notificationTickets.length;
  const ticket = notificationTickets[index];
  const userId = notificationUserIds[index];
  sse.open(`${baseUrl}/api/users/${userId}/notifications/stream?ticket=${encodeURIComponent(ticket)}`, {
    headers: {Accept: 'text/event-stream'},
    tags: {name: 'GET /api/users/:userId/notifications/stream'},
  }, client => {
    client.on('open', () => sseNotificationConnectSuccess.add(true));
    client.on('error', () => {
      sseNotificationConnectionErrors.add(1);
      sseNotificationConnectSuccess.add(false);
    });
    client.on('event', () => sseNotificationEvents.add(1));
  });
}

export default function (data) {
  performBid(data);
}

export function handleSummary(data) {
  const result = {
    generatedAt: new Date().toISOString(),
    testConfig: {
      baseUrl,
      auctionIds: csv(__ENV.AUCTION_IDS),
      auctionSelection: __ENV.AUCTION_IDS ? 'CONFIGURED' : 'AUTO_OPEN_AUCTIONS',
      credentialSource: credentialSource(),
      loadTestUserCount,
      loginBatchSize,
      warmupRate,
      warmupDuration,
      mainStartTime,
      rate: requestRate,
      duration,
      preAllocatedVUs,
      maxVUs,
      setupTimeout: options.setupTimeout,
      summaryTrendStats: options.summaryTrendStats,
      sseVUs,
      sseDuration,
      loadTestUserIdStart,
    },
    ...data,
  };
  const output = {stdout: summaryText(data)};
  if (resultFile) {
    output[resultFile] = JSON.stringify(result, null, 2);
  }
  return output;
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
      responseCallback: http.expectedStatuses(201, 400, 409),
    },
  );

  const accepted = bidResponse.status === 201;
  const priceRejected = bidResponse.status === 400;
  const contended = bidResponse.status === 409;
  bidAccepted.add(accepted);
  bidAcceptedOrContended.add(accepted || contended);
  bidServerError.add(bidResponse.status >= 500);
  bidEndToEndDuration.add(Date.now() - startedAt);

  if (contended) {
    // 최신 minimum_bid를 읽은 뒤 다른 VU가 먼저 입찰한 정상적인 경쟁 충돌이다.
    bidContentions.add(1);
  } else if (!accepted && !priceRejected) {
    bidRejected.add(1, {status: String(bidResponse.status), phase: 'bid'});
  }

  check(bidResponse, {
    '서버가 정책대로 응답함(성공/최소가 거부/동시 입찰 충돌)': response =>
      response.status === 201 || response.status === 400 || response.status === 409,
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

  const tokens = [];
  const loginStartedAt = Date.now();
  const totalBatches = Math.ceil(users.length / loginBatchSize);
  console.log(`[setup/login] 시작: ${users.length}명, 배치 크기 ${loginBatchSize}, 총 ${totalBatches}단계`);

  for (let start = 0; start < users.length; start += loginBatchSize) {
    const batchUsers = users.slice(start, start + loginBatchSize);
    const responses = http.batch(batchUsers.map(user => ({
      method: 'POST',
      url: `${baseUrl}/api/auth/login`,
      body: JSON.stringify({email: user.email, password: user.password}),
      params: {
        headers: {'Content-Type': 'application/json'},
        tags: {name: 'POST /api/auth/login (setup)'},
        responseCallback: http.expectedStatuses(200),
      },
    })));

    responses.forEach((response, batchIndex) => {
      const userIndex = start + batchIndex + 1;
      if (response.status !== 200 || response.body === null) {
        const cause = response.error || '응답 본문 없음';
        throw new Error(`로그인 계정 ${userIndex} 요청 실패 (status=${response.status}, cause=${cause})`);
      }
      const accessToken = response.json('accessToken');
      if (typeof accessToken !== 'string' || accessToken.length === 0) {
        throw new Error(`로그인 계정 ${userIndex} 응답에 Access Token이 없습니다.`);
      }
      tokens.push(accessToken);
    });

    const completed = Math.min(start + batchUsers.length, users.length);
    const progress = ((completed / users.length) * 100).toFixed(1);
    const elapsedSeconds = ((Date.now() - loginStartedAt) / 1000).toFixed(1);
    console.log(`[setup/login] ${completed}/${users.length}명 완료 (${progress}%, ${elapsedSeconds}초)`);
  }
  console.log(`[setup/login] 완료: ${tokens.length}개 Access Token 발급`);
  return tokens;
}

function loadTestUsers() {
  return Array.from({length: loadTestUserCount}, (_, index) => ({
    email: `${loadTestEmailPrefix}${String(index + 1).padStart(loadTestUserNumberWidth, '0')}@${loadTestEmailDomain}`,
    password: loadTestPassword,
  }));
}

function issueNotificationTickets(tokens) {
  const tickets = [];
  for (let start = 0; start < tokens.length; start += loginBatchSize) {
    const batchTokens = tokens.slice(start, start + loginBatchSize);
    const responses = http.batch(batchTokens.map(token => ({
      method: 'POST',
      url: `${baseUrl}/api/sse/tickets`,
      body: null,
      params: {
        headers: {Authorization: `Bearer ${token}`},
        tags: {name: 'POST /api/sse/tickets (setup)'},
        responseCallback: http.expectedStatuses(200),
      },
    })));
    responses.forEach((response, batchIndex) => {
      const index = start + batchIndex + 1;
      if (response.status !== 200 || response.body === null) {
        throw new Error(`알림 SSE 티켓 ${index} 발급 실패 (status=${response.status}, cause=${response.error || '응답 본문 없음'})`);
      }
      const ticket = response.json('ticket');
      if (typeof ticket !== 'string' || ticket.length === 0) {
        throw new Error(`알림 SSE 티켓 ${index} 응답이 올바르지 않습니다.`);
      }
      tickets.push(ticket);
    });
  }
  console.log(`[setup/sse] 알림 SSE 티켓 ${tickets.length}개 발급 완료`);
  return tickets;
}

function loadNotificationUserIds(count) {
  const configured = csv(__ENV.NOTIFICATION_USER_IDS)
    .map(Number)
    .filter(id => Number.isInteger(id) && id > 0);
  if (configured.length > 0) {
    if (configured.length < count) {
      throw new Error(`NOTIFICATION_USER_IDS는 ${count}개 이상 필요합니다.`);
    }
    return configured.slice(0, count);
  }
  return Array.from({length: count}, (_, index) => loadTestUserIdStart + index);
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

// VU마다 서로 다른 auctionIds 구간을 구독하게 해서(회전 window) 실제 목록 화면처럼
// 전체 경매에 SSE 부하를 분산시킨다. 대상이 max 이하면 전부 그대로 구독한다.
function subscribedAuctionIds(auctionIds) {
  if (auctionIds.length <= maxAuctionSseSubscriptions) {
    return auctionIds;
  }
  const start = (__VU - 1) % auctionIds.length;
  const picked = [];
  for (let i = 0; i < maxAuctionSseSubscriptions; i++) {
    picked.push(auctionIds[(start + i) % auctionIds.length]);
  }
  return picked;
}

function parseDurationToSeconds(value) {
  const match = String(value).match(/^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$/);
  if (!match || value === '') {
    throw new Error(`잘못된 duration 형식입니다: ${value}`);
  }
  const [, hours, minutes, seconds] = match;
  return (Number(hours) || 0) * 3600 + (Number(minutes) || 0) * 60 + (Number(seconds) || 0);
}

function formatSecondsAsDuration(totalSeconds) {
  return `${totalSeconds}s`;
}

function positiveNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function nonNegativeInteger(value, fallback) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

function positiveInteger(value, fallback) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function credentialSource() {
  if (__ENV.LOGIN_USERS) return 'LOGIN_USERS';
  if (__ENV.EMAIL && __ENV.PASSWORD) return 'EMAIL_PASSWORD';
  if (__ENV.ACCESS_TOKENS) return 'ACCESS_TOKENS';
  return 'GENERATED_LOAD_TEST_USERS';
}

function summaryText(data) {
  const requests = data.metrics.http_reqs?.values || {};
  const failed = data.metrics.http_req_failed?.values || {};
  const durationValues = data.metrics.http_req_duration?.values || {};
  const saved = resultFile ? `\n결과 JSON: ${resultFile}` : '';
  return [
    '\n=== K6 FINAL SUMMARY ===',
    `HTTP 요청: ${requests.count || 0} (${(requests.rate || 0).toFixed(2)} req/s)`,
    `HTTP 실패율: ${((failed.rate || 0) * 100).toFixed(2)}%`,
    `응답시간: p85=${formatMilliseconds(durationValues['p(85)'])}, p95=${formatMilliseconds(durationValues['p(95)'])}, p99=${formatMilliseconds(durationValues['p(99)'])}`,
    `${saved}\n`,
  ].join('\n');
}

function formatMilliseconds(value) {
  return Number.isFinite(value) ? `${value.toFixed(2)}ms` : '-';
}
