-- KEYS[1]: order state, KEYS[2]: settlement/refund wallet, KEYS[3]: idempotency result, KEYS[4]: timeline Stream,
-- KEYS[5]: order orderId -> auctionId index
-- ARGV: actorId, targetStatus, eventType, orderId, auctionId, idempotencyKey, requestHash, eventId, occurredAt
local existing = redis.call('GET', KEYS[3])
if existing then
    local separator = string.find(existing, '|')
    if string.sub(existing, 1, separator - 1) ~= ARGV[7] then return 'REJECTED|IDEMPOTENCY_CONFLICT' end
    return string.sub(existing, separator + 1) .. '|true'
end

local status = redis.call('HGET', KEYS[1], 'status')
local buyerId = redis.call('HGET', KEYS[1], 'buyerId')
local sellerId = redis.call('HGET', KEYS[1], 'sellerId')
local price = tonumber(redis.call('HGET', KEYS[1], 'price'))
if not status or not buyerId or not sellerId or not price then return 'REJECTED|STATE_MISSING' end
if status ~= 'PENDING_CONFIRM' then return 'REJECTED|INVALID_STATUS' end

local completing = ARGV[2] == 'COMPLETED'
if completing and buyerId ~= ARGV[1] then return 'REJECTED|ACCESS_DENIED' end
if not completing and ARGV[3] == 'order.buyer-cancelled.v1' and buyerId ~= ARGV[1] then return 'REJECTED|ACCESS_DENIED' end
if not completing and ARGV[3] == 'order.seller-cancelled.v1' and sellerId ~= ARGV[1] then return 'REJECTED|ACCESS_DENIED' end

local walletUserId = completing and sellerId or buyerId
local available = tonumber(redis.call('HGET', KEYS[2], 'availableBalance'))
local frozen = tonumber(redis.call('HGET', KEYS[2], 'frozenBalance'))
if not available or not frozen then return 'REJECTED|STATE_MISSING' end
local nextAvailable = available + price
local walletVersion = redis.call('HINCRBY', KEYS[2], 'walletVersion', 1)
local orderVersion = redis.call('HINCRBY', KEYS[1], 'orderVersion', 1)
redis.call('HSET', KEYS[2], 'availableBalance', nextAvailable, 'frozenBalance', frozen)
redis.call('EXPIRE', KEYS[2], 3600 + (tonumber(walletUserId) % 18001))
redis.call('HSET', KEYS[1], 'status', ARGV[2], 'projectionStatus', 'PENDING', 'lastStreamEventId', ARGV[8])
-- 완료/취소된 주문은 order:state와 by-order-id 인덱스를 같은 TTL로 만료시켜야
-- 재조회 시 seedIfAbsent가 만료된 order:state를 by-order-id만 보고 살아있다고 오판하지 않는다.
local orderTtl = 3600 + (tonumber(ARGV[5]) % 18001)
redis.call('EXPIRE', KEYS[1], orderTtl)
redis.call('EXPIRE', KEYS[5], orderTtl)

local transactionType = completing and 'ORDER_SETTLEMENT' or 'ORDER_CANCEL_REFUND'
local streamId = redis.call('XADD', KEYS[4], '*',
    'schemaVersion', '1', 'eventType', ARGV[3], 'eventId', ARGV[8],
    'orderId', ARGV[4], 'auctionId', ARGV[5], 'orderVersion', orderVersion,
    'actorId', ARGV[1], 'buyerId', buyerId, 'sellerId', sellerId, 'status', ARGV[2],
    'walletUserId', walletUserId, 'walletVersion', walletVersion,
    'availableBalance', nextAvailable, 'frozenBalance', frozen,
    'transactionType', transactionType, 'transactionAmount', price,
    'idempotencyKey', ARGV[6], 'occurredAt', ARGV[9])
local result = 'ACCEPTED|' .. streamId .. '|' .. ARGV[2] .. '|' .. orderVersion .. '|' .. walletVersion
    .. '|' .. nextAvailable .. '|' .. frozen .. '|' .. walletUserId
redis.call('SET', KEYS[3], ARGV[7] .. '|' .. result, 'EX', 86400)
return result .. '|false'
