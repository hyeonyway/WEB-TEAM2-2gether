-- KEYS[1]: order state, KEYS[2]: settlement/refund wallet, KEYS[3]: idempotency result, KEYS[4]: timeline Stream
-- ARGV: actorId, targetStatus, eventType, orderId, auctionId, idempotencyKey, requestHash, eventId, occurredAt
local existing = redis.call('GET', KEYS[3])
if existing then
    local separator = string.find(existing, '|')
    if string.sub(existing, 1, separator - 1) ~= ARGV[7] then return 'REJECTED|IDEMPOTENCY_CONFLICT' end
    return string.sub(existing, separator + 1)
end

local status = redis.call('HGET', KEYS[1], 'status')
local buyerId = redis.call('HGET', KEYS[1], 'buyerId')
local sellerId = redis.call('HGET', KEYS[1], 'sellerId')
local price = tonumber(redis.call('HGET', KEYS[1], 'price'))
if not status or not buyerId or not sellerId or not price then return 'REJECTED|STATE_MISSING' end
if status ~= 'PENDING_CONFIRM' then return 'REJECTED|INVALID_STATUS' end

local completing = ARGV[2] == 'COMPLETED'
if completing and buyerId ~= ARGV[1] then return 'REJECTED|ACCESS_DENIED' end
if not completing and buyerId ~= ARGV[1] and sellerId ~= ARGV[1] then return 'REJECTED|ACCESS_DENIED' end

local available = tonumber(redis.call('HGET', KEYS[2], 'availableBalance'))
local frozen = tonumber(redis.call('HGET', KEYS[2], 'frozenBalance'))
if not available or not frozen then return 'REJECTED|STATE_MISSING' end
local nextAvailable = available + price
local walletVersion = redis.call('HINCRBY', KEYS[2], 'walletVersion', 1)
local orderVersion = redis.call('HINCRBY', KEYS[1], 'orderVersion', 1)
redis.call('HSET', KEYS[2], 'availableBalance', nextAvailable, 'frozenBalance', frozen)
redis.call('HSET', KEYS[1], 'status', ARGV[2], 'projectionStatus', 'PENDING', 'lastStreamEventId', ARGV[8])

local walletUserId = completing and sellerId or buyerId
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
redis.call('SET', KEYS[3], ARGV[7] .. '|' .. result, 'EX', 86400)
return result
