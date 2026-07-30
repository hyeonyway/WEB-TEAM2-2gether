import sse from 'k6/x/sse';
import {Counter, Rate} from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const streamUrl = `${baseUrl}/api/auctions/stream`;

const connectSuccess = new Rate('sse_connect_success');
const connectionErrors = new Rate('sse_connection_errors');
const auctionEvents = new Counter('sse_auction_events');
const invalidPayloads = new Counter('sse_invalid_payloads');

const eventTypes = new Set([
  'AUCTION_CREATED',
  'BID_PLACED',
  'AUCTION_CLOSED',
]);

export const options = {
  scenarios: {
    auctionSseConnections: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        {duration: __ENV.RAMP_UP || '10s', target: Number(__ENV.VUS || 50)},
        {duration: __ENV.HOLD || '30s', target: Number(__ENV.VUS || 50)},
        {duration: __ENV.RAMP_DOWN || '10s', target: 0},
      ],
      gracefulRampDown: '1s',
    },
  },
  thresholds: {
    sse_connect_success: ['rate>0.99'],
    sse_connection_errors: ['rate<0.01'],
    sse_invalid_payloads: ['count==0'],
  },
};

export default function () {
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
}

function isValidPayload(payload) {
  return payload !== null
    && typeof payload === 'object'
    && eventTypes.has(payload.type)
    && Number.isInteger(payload.auction_id)
    && Number.isInteger(payload.card_id)
    && typeof payload.card_name === 'string'
    && Number.isFinite(payload.start_price)
    && Number.isFinite(payload.bid_increment)
    && Number.isInteger(payload.bid_count)
    && typeof payload.ends_at === 'string'
    && Number.isFinite(payload.auction_version)
    && typeof payload.occurred_at === 'string';
}
