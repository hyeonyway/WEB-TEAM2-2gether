import sse from 'k6/x/sse';
import http from 'k6/http';
import {sleep} from 'k6';
import {Counter, Rate} from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const streamUrl = `${baseUrl}/api/auctions/stream`;

const connectSuccess = new Rate('sse_connect_success');
const connectionErrors = new Rate('sse_connection_errors');
const auctionEvents = new Counter('sse_auction_events');
const invalidPayloads = new Counter('sse_invalid_payloads');
const publishSuccess = new Rate('sse_test_event_publish_success');

const eventTypes = new Set([
  'AUCTION_CREATED',
  'BID_PLACED',
  'AUCTION_CLOSED',
]);

export const options = {
  scenarios: {
    auctionSseConnections: {
      executor: 'ramping-vus',
      exec: 'subscribe',
      startVUs: 0,
      stages: [
        {duration: __ENV.RAMP_UP || '10s', target: Number(__ENV.VUS || 50)},
        {duration: __ENV.HOLD || '30s', target: Number(__ENV.VUS || 50)},
        {duration: __ENV.RAMP_DOWN || '10s', target: 0},
      ],
      gracefulRampDown: '1s',
    },
    auctionEventPublisher: {
      executor: 'constant-arrival-rate',
      exec: 'publishEvents',
      startTime: __ENV.RAMP_UP || '10s',
      duration: __ENV.HOLD || '30s',
      rate: Number(__ENV.EVENT_RATE || 2),
      timeUnit: '1s',
      preAllocatedVUs: 1,
      maxVUs: 10,
    },
  },
  thresholds: {
    sse_connect_success: ['rate>0.99'],
    sse_connection_errors: ['rate<0.01'],
    sse_invalid_payloads: ['count==0'],
    sse_test_event_publish_success: ['rate>0.99'],
  },
};

export function subscribe() {
  sse.open(
    streamUrl,
    {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
      },
      tags: {
        name: 'GET /api/auctions/stream',
      },
    },
    client => {
      client.on('open', () => {
        connectSuccess.add(true);
      });

      client.on('event', event => {
        if (event.name !== 'auction-updated') {
          return;
        }

        auctionEvents.add(1);
        try {
          const payload = JSON.parse(event.data);
          if (!isValidPayload(payload)) {
            invalidPayloads.add(1);
          }
        } catch {
          invalidPayloads.add(1);
        }
      });

      client.on('error', () => {
        connectionErrors.add(true);
        connectSuccess.add(false);
      });
    },
  );

  // 서버 미기동이나 연결 종료 시 VU가 즉시 재접속을 반복하지 않게 한다.
  sleep(Number(__ENV.RECONNECT_DELAY || 1));
}

export function publishEvents() {
  const response = http.post(
    `${baseUrl}/api/auctions/stream/test-events/random-bid`,
    null,
    {tags: {name: 'POST /api/auctions/stream/test-events/:type'}},
  );

  publishSuccess.add(response.status === 202);
}

function isValidPayload(payload) {
  const commonValid = payload !== null
    && typeof payload === 'object'
    && eventTypes.has(payload.type)
    && Number.isInteger(payload.auction_id ?? payload.auctionId)
    && Number.isFinite(payload.start_price ?? payload.startPrice)
    && Number.isFinite(payload.bid_increment ?? payload.bidIncrement)
    && Number.isInteger(payload.bid_count ?? payload.bidCount)
    && typeof (payload.ends_at ?? payload.endsAt) === 'string'
    && Number.isFinite(payload.auction_version ?? payload.auctionVersion)
    && typeof (payload.occurred_at ?? payload.occurredAt) === 'string';
  if (!commonValid) {
    return false;
  }
  if (payload.type === 'BID_PLACED') {
    return Number.isInteger(payload.bidder_id ?? payload.bidderId);
  }
  return Number.isInteger(payload.card_id ?? payload.cardId)
    && typeof (payload.card_name ?? payload.cardName) === 'string';
}
