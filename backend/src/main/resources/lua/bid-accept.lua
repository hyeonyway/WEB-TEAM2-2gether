-- KEYS: auction state, bidder balance, bidder hold, idempotency result, #323 timeline stream
-- ARGV: bidderId, price, idempotencyKey, requestHash, nowEpochMillis, nowIsoInstant
local existing = redis.call('GET', KEYS[4])
if existing then
    local separator = string.find(existing, '|')
    if string.sub(existing, 1, separator - 1) ~= ARGV[4] then
        return 'REJECTED|IDEMPOTENCY_CONFLICT'
    end
    return string.sub(existing, separator + 1)
end

local status = redis.call('HGET', KEYS[1], 'status')
local sellerId = redis.call('HGET', KEYS[1], 'sellerId')
local currentPrice = tonumber(redis.call('HGET', KEYS[1], 'currentPrice'))
local bidIncrement = tonumber(redis.call('HGET', KEYS[1], 'bidIncrement'))
local closeTime = redis.call('HGET', KEYS[1], 'closeTime')
local closeTimeEpochMillis = tonumber(redis.call('HGET', KEYS[1], 'closeTimeEpochMillis'))
local highestBidderId = redis.call('HGET', KEYS[1], 'highestBidderId')
local highestHoldAmount = tonumber(redis.call('HGET', KEYS[1], 'highestHoldAmount') or '0')
local price = tonumber(ARGV[2])

if status ~= 'OPEN' then return 'REJECTED|NOT_OPEN' end
if sellerId == ARGV[1] then return 'REJECTED|SELLER' end
if highestBidderId == ARGV[1] then return 'REJECTED|LEADING_BIDDER' end
if closeTimeEpochMillis and tonumber(ARGV[5]) >= closeTimeEpochMillis then return 'REJECTED|CLOSED' end
if price < currentPrice + bidIncrement then return 'REJECTED|LOW_PRICE' end

local available = tonumber(redis.call('HGET', KEYS[2], 'availableBalance') or '0')
if available < price then return 'REJECTED|INSUFFICIENT_BALANCE' end

local newAvailable = available - price
local newFrozen = tonumber(redis.call('HGET', KEYS[2], 'frozenBalance') or '0') + price
local bidderWalletVersion = redis.call('HINCRBY', KEYS[2], 'version', 1)
redis.call('HSET', KEYS[2], 'availableBalance', newAvailable, 'frozenBalance', newFrozen)
redis.call('HSET', KEYS[3], 'amount', price)

local previousBidderId = highestBidderId or ''
if highestBidderId and highestBidderId ~= '' then
    local previousBalanceKey = 'wallet:balance:' .. highestBidderId
    local previousHoldKey = 'wallet:hold:' .. string.match(KEYS[1], 'auction:state:(.+)') .. ':' .. highestBidderId
    redis.call('HINCRBY', previousBalanceKey, 'availableBalance', highestHoldAmount)
    redis.call('HINCRBY', previousBalanceKey, 'frozenBalance', -highestHoldAmount)
    redis.call('HINCRBY', previousBalanceKey, 'version', 1)
    redis.call('DEL', previousHoldKey)
end

local auctionVersion = redis.call('HINCRBY', KEYS[1], 'sequence', 1)
local bidCount = redis.call('HINCRBY', KEYS[1], 'bidCount', 1)
redis.call('HSET', KEYS[1], 'currentPrice', price, 'highestBidderId', ARGV[1], 'highestHoldAmount', price)

local streamId = redis.call('XADD', KEYS[5], '*',
    'schemaVersion', '1', 'eventType', 'bid.accepted.v1',
    'auctionId', string.match(KEYS[1], 'auction:state:(.+)'), 'auctionVersion', auctionVersion,
    'bidderId', ARGV[1], 'requestedPrice', price, 'bidPrice', price,
    'previousBidderId', previousBidderId == '' and 'null' or previousBidderId,
    'idempotencyKey', ARGV[3], 'idempotencyRequestHash', ARGV[4],
    'currentPrice', price, 'bidCount', bidCount, 'closeTime', closeTime,
    'auctionStatus', status, 'occurredAt', ARGV[6])

local result = 'ACCEPTED|' .. streamId .. '|' .. price .. '|' .. auctionVersion .. '|' .. bidCount
    .. '|' .. newAvailable .. '|' .. newFrozen .. '|' .. bidderWalletVersion .. '|' .. closeTime
redis.call('SET', KEYS[4], ARGV[4] .. '|' .. result, 'EX', 86400)
return result
