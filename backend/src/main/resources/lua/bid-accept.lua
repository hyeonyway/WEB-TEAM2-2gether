-- KEYS: auction state, bidder balance, bidder hold, idempotency result, single timeline stream
-- ARGV: bidderId, price, idempotencyKey, requestHash, nowEpochMillis, nowIsoInstant
local function iso8601(epochMillis)
    local seconds = math.floor(epochMillis / 1000)
    local day = math.floor(seconds / 86400)
    local time = seconds - day * 86400
    local z = day + 719468
    local era = math.floor((z >= 0 and z or z - 146096) / 146097)
    local doe = z - era * 146097
    local yoe = math.floor((doe - math.floor(doe / 1460) + math.floor(doe / 36524) - math.floor(doe / 146096)) / 365)
    local year = yoe + era * 400
    local doy = doe - (365 * yoe + math.floor(yoe / 4) - math.floor(yoe / 100))
    local monthPrime = math.floor((5 * doy + 2) / 153)
    local dayOfMonth = doy - math.floor((153 * monthPrime + 2) / 5) + 1
    local month = monthPrime + (monthPrime < 10 and 3 or -9)
    year = year + (month <= 2 and 1 or 0)
    return string.format('%04d-%02d-%02dT%02d:%02d:%02dZ', year, month, dayOfMonth,
        math.floor(time / 3600), math.floor((time % 3600) / 60), time % 60)
end
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
local requestedPrice = tonumber(ARGV[2])
local buyNowPrice = tonumber(redis.call('HGET', KEYS[1], 'buyNowPrice'))
local cardName = redis.call('HGET', KEYS[1], 'cardName') or ''

if not status or not sellerId or not currentPrice or not bidIncrement or not closeTime or not closeTimeEpochMillis then
    return 'REJECTED|STATE_MISSING'
end
if status ~= 'OPEN' and status ~= 'ENDING' then return 'REJECTED|NOT_OPEN' end
if sellerId == ARGV[1] then return 'REJECTED|SELLER' end
if closeTimeEpochMillis and tonumber(ARGV[5]) >= closeTimeEpochMillis then return 'REJECTED|CLOSED' end
local buyNow = buyNowPrice and requestedPrice >= buyNowPrice
local price = buyNow and buyNowPrice or requestedPrice
if not buyNow and highestBidderId == ARGV[1] then return 'REJECTED|LEADING_BIDDER' end
if price < currentPrice + bidIncrement then return 'REJECTED|LOW_PRICE' end
if buyNow and cardName == '' then return 'REJECTED|STATE_MISSING' end

local available = tonumber(redis.call('HGET', KEYS[2], 'availableBalance') or '0')
local existingBidderHold = highestBidderId == ARGV[1] and highestHoldAmount or 0
local requiredAvailable = price - existingBidderHold
if available < requiredAvailable then return 'REJECTED|INSUFFICIENT_BALANCE' end

local newAvailable = available - requiredAvailable
local newFrozen = tonumber(redis.call('HGET', KEYS[2], 'frozenBalance') or '0') + requiredAvailable
local bidderWalletVersion = redis.call('HINCRBY', KEYS[2], 'walletVersion', 1)
redis.call('HSET', KEYS[2], 'availableBalance', newAvailable, 'frozenBalance', newFrozen)
redis.call('EXPIRE', KEYS[2], 3600 + (tonumber(ARGV[1]) % 18001))
redis.call('HSET', KEYS[3], 'amount', price)

local previousBidderId = highestBidderId or ''
if highestBidderId and highestBidderId ~= '' and highestBidderId ~= ARGV[1] then
    local previousBalanceKey = 'wallet:balance:' .. highestBidderId
    local previousHoldKey = 'wallet:hold:' .. string.match(KEYS[1], 'auction:state:(.+)') .. ':' .. highestBidderId
    redis.call('HINCRBY', previousBalanceKey, 'availableBalance', highestHoldAmount)
    redis.call('HINCRBY', previousBalanceKey, 'frozenBalance', -highestHoldAmount)
    redis.call('HINCRBY', previousBalanceKey, 'walletVersion', 1)
    redis.call('EXPIRE', previousBalanceKey, 3600 + (tonumber(highestBidderId) % 18001))
    redis.call('DEL', previousHoldKey)
    redis.call('HSET', 'auction:bidder:' .. string.match(KEYS[1], 'auction:state:(.+)') .. ':' .. highestBidderId,
        'status', 'OUTBID', 'amount', highestHoldAmount)
end

local auctionVersion = redis.call('HINCRBY', KEYS[1], 'sequence', 1)
local bidCount = redis.call('HINCRBY', KEYS[1], 'bidCount', 1)
local nextCloseTime = closeTime
local nextCloseTimeEpochMillis = closeTimeEpochMillis
local nextStatus = status
if buyNow then
    nextCloseTime = ARGV[6]
    nextCloseTimeEpochMillis = tonumber(ARGV[5])
    nextStatus = 'ENDED'
elseif tonumber(ARGV[5]) >= closeTimeEpochMillis - 300000 then
    nextCloseTimeEpochMillis = closeTimeEpochMillis + 300000
    nextCloseTime = iso8601(nextCloseTimeEpochMillis)
    nextStatus = 'ENDING'
end
redis.call('HSET', KEYS[1], 'currentPrice', price, 'highestBidderId', ARGV[1], 'highestHoldAmount', price,
    'closeTime', nextCloseTime, 'closeTimeEpochMillis', nextCloseTimeEpochMillis, 'status', nextStatus)
local activeAuctionIndex = 'auction:active:by-close-time'
if buyNow then
    redis.call('EXPIRE', KEYS[1], 3600 + (tonumber(string.match(KEYS[1], 'auction:state:(.+)')) % 18001))
    redis.call('ZREM', activeAuctionIndex, string.match(KEYS[1], 'auction:state:(.+)'))
else
    redis.call('ZADD', activeAuctionIndex, nextCloseTimeEpochMillis, string.match(KEYS[1], 'auction:state:(.+)'))
end

redis.call('XADD', 'auction:recent-bids:' .. string.match(KEYS[1], 'auction:state:(.+)'), 'MAXLEN', 50, '*',
    'bidderId', ARGV[1], 'bidPrice', price, 'sequence', auctionVersion, 'occurredAt', ARGV[6])
redis.call('HSET', 'auction:bidder:' .. string.match(KEYS[1], 'auction:state:(.+)') .. ':' .. ARGV[1],
    'status', buyNow and 'WON' or 'LEADING', 'amount', price)
redis.call('SADD', 'auction:dashboard:participating:' .. ARGV[1], string.match(KEYS[1], 'auction:state:(.+)'))

local streamId = redis.call('XADD', KEYS[5], '*',
    'schemaVersion', '1', 'eventType', buyNow and 'auction.buy-now.v1' or 'bid.accepted.v1',
    'auctionId', string.match(KEYS[1], 'auction:state:(.+)'), 'auctionVersion', auctionVersion,
    'bidderId', ARGV[1], 'requestedPrice', requestedPrice, 'bidPrice', price,
    'previousBidderId', previousBidderId == '' and 'null' or previousBidderId,
    'idempotencyKey', ARGV[3], 'idempotencyRequestHash', ARGV[4],
    'currentPrice', price, 'bidCount', bidCount, 'closeTime', nextCloseTime,
    'auctionStatus', nextStatus, 'occurredAt', ARGV[6])

local pendingOrderStatus = ''
if buyNow then
    redis.call('HINCRBY', KEYS[2], 'frozenBalance', -price)
    redis.call('DEL', KEYS[3])
    newFrozen = tonumber(redis.call('HGET', KEYS[2], 'frozenBalance'))
    local auctionId = string.match(KEYS[1], 'auction:state:(.+)')
    redis.call('HSET', 'order:state:' .. auctionId,
        'auctionId', auctionId, 'buyerId', ARGV[1], 'sellerId', sellerId, 'cardName', cardName,
        'price', price, 'status', 'PENDING_CONFIRM', 'projectionStatus', 'PENDING', 'streamId', streamId, 'createdAt', ARGV[6])
    redis.call('SADD', 'order:state:buyer:' .. ARGV[1], auctionId)
    redis.call('SADD', 'order:state:seller:' .. sellerId, auctionId)
    pendingOrderStatus = 'PENDING'
end

local result = 'ACCEPTED|' .. streamId .. '|' .. price .. '|' .. auctionVersion .. '|' .. bidCount
    .. '|' .. newAvailable .. '|' .. newFrozen .. '|' .. bidderWalletVersion .. '|' .. (price + bidIncrement) .. '|' .. nextCloseTime
    .. '|' .. (buyNow and 'WON' or 'LEADING') .. '|' .. pendingOrderStatus
redis.call('SET', KEYS[4], ARGV[4] .. '|' .. result, 'EX', 86400)
return result
