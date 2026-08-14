-- KEYS: auction state, bidder balance, bidder hold, idempotency result, single timeline stream, ending-window index,
--       active-by-bid-count index, active-by-price index, active-by-change-rate index, active-by-open-time index
-- ARGV: bidderId, price, idempotencyKey, requestHash, nowEpochMillis, nowIsoInstant, maxBalance
local function integerString(value)
    return string.format('%.0f', value)
end

local existing = redis.call('GET', KEYS[4])
if existing then
    local separator = string.find(existing, '|')
    if string.sub(existing, 1, separator - 1) ~= ARGV[4] then
        return 'REJECTED|IDEMPOTENCY_CONFLICT'
    end
    return string.sub(existing, separator + 1) .. '|true'
end

local status = redis.call('HGET', KEYS[1], 'status')
local sellerId = redis.call('HGET', KEYS[1], 'sellerId')
local currentPrice = tonumber(redis.call('HGET', KEYS[1], 'currentPrice'))
local bidIncrement = tonumber(redis.call('HGET', KEYS[1], 'bidIncrement'))
local closeTime = redis.call('HGET', KEYS[1], 'closeTime')
local closeTimeEpochMillis = tonumber(redis.call('HGET', KEYS[1], 'closeTimeEpochMillis'))
local estimatedCloseTime = redis.call('HGET', KEYS[1], 'estimatedCloseTime') or closeTime
local highestBidderId = redis.call('HGET', KEYS[1], 'highestBidderId')
local highestHoldAmount = tonumber(redis.call('HGET', KEYS[1], 'highestHoldAmount') or '0')
local requestedPrice = tonumber(ARGV[2])
local buyNowPrice = tonumber(redis.call('HGET', KEYS[1], 'buyNowPrice'))
local cardName = redis.call('HGET', KEYS[1], 'cardName') or ''
local itemId = redis.call('HGET', KEYS[1], 'itemId')
local startPrice = redis.call('HGET', KEYS[1], 'startPrice')
local cardPsaGrade = redis.call('HGET', KEYS[1], 'cardPsaGrade') or ''
local cardLanguage = redis.call('HGET', KEYS[1], 'cardLanguage') or ''
local cardThumbnailUrl = redis.call('HGET', KEYS[1], 'cardThumbnailUrl') or ''
local maxBalance = tonumber(ARGV[7])

if not status or not sellerId or not itemId or not startPrice or not currentPrice or not bidIncrement or not closeTime or not closeTimeEpochMillis or not requestedPrice or not maxBalance then
    return 'REJECTED|STATE_MISSING'
end
if status ~= 'OPEN' and status ~= 'ENDING' then return 'REJECTED|NOT_OPEN' end
if sellerId == ARGV[1] then return 'REJECTED|SELLER' end
if closeTimeEpochMillis and tonumber(ARGV[5]) >= closeTimeEpochMillis then return 'REJECTED|CLOSED' end
local buyNow = buyNowPrice and requestedPrice >= buyNowPrice
local price = buyNow and buyNowPrice or requestedPrice
if price > maxBalance then return 'REJECTED|AMOUNT_LIMIT_EXCEEDED' end
if not buyNow and highestBidderId == ARGV[1] then return 'REJECTED|LEADING_BIDDER' end
if price < currentPrice + bidIncrement then return 'REJECTED|LOW_PRICE' end
if buyNow and cardName == '' then return 'REJECTED|STATE_MISSING' end

local available = tonumber(redis.call('HGET', KEYS[2], 'availableBalance') or '0')
local frozen = tonumber(redis.call('HGET', KEYS[2], 'frozenBalance') or '0')
if available + frozen > maxBalance then return 'REJECTED|BALANCE_LIMIT_EXCEEDED' end
local existingBidderHold = highestBidderId == ARGV[1] and highestHoldAmount or 0
local requiredAvailable = price - existingBidderHold
if available < requiredAvailable then return 'REJECTED|INSUFFICIENT_BALANCE' end

local newAvailable = available - requiredAvailable
local newFrozen = frozen + requiredAvailable
local bidderWalletVersion = redis.call('HINCRBY', KEYS[2], 'walletVersion', 1)
local newAvailableString = integerString(newAvailable)
local newFrozenString = integerString(newFrozen)
local priceString = integerString(price)
local requestedPriceString = integerString(requestedPrice)
local bidderWalletVersionString = integerString(bidderWalletVersion)
redis.call('HSET', KEYS[2], 'availableBalance', newAvailableString, 'frozenBalance', newFrozenString)
redis.call('HSET', KEYS[3], 'amount', priceString)

local previousBidderId = highestBidderId or ''
local previousAvailable = ''
local previousFrozen = ''
local previousWalletVersion = ''
if highestBidderId and highestBidderId ~= '' and highestBidderId ~= ARGV[1] then
    local previousBalanceKey = 'wallet:balance:' .. highestBidderId
    local previousHoldKey = 'wallet:hold:' .. string.match(KEYS[1], 'auction:state:(.+)') .. ':' .. highestBidderId
    previousAvailable = redis.call('HINCRBY', previousBalanceKey, 'availableBalance', integerString(highestHoldAmount))
    previousFrozen = redis.call('HINCRBY', previousBalanceKey, 'frozenBalance', integerString(-highestHoldAmount))
    previousWalletVersion = redis.call('HINCRBY', previousBalanceKey, 'walletVersion', 1)
    previousAvailable = integerString(previousAvailable)
    previousFrozen = integerString(previousFrozen)
    previousWalletVersion = integerString(previousWalletVersion)
    redis.call('DEL', previousHoldKey)
    redis.call('HSET', 'auction:bidder:' .. string.match(KEYS[1], 'auction:state:(.+)') .. ':' .. highestBidderId,
        'status', 'OUTBID', 'amount', integerString(highestHoldAmount))
end

local auctionVersion = redis.call('HINCRBY', KEYS[1], 'sequence', 1)
local bidCount = redis.call('HINCRBY', KEYS[1], 'bidCount', 1)
local auctionVersionString = integerString(auctionVersion)
local bidCountString = integerString(bidCount)
local nextCloseTime = closeTime
local nextCloseTimeEpochMillis = closeTimeEpochMillis
local nextStatus = status
local closeTimeExtended = false
if buyNow then
    nextCloseTime = ARGV[6]
    nextCloseTimeEpochMillis = tonumber(ARGV[5])
    nextStatus = 'ENDED'
end
redis.call('HSET', KEYS[1], 'currentPrice', priceString, 'highestBidderId', ARGV[1], 'highestHoldAmount', priceString,
    'closeTime', nextCloseTime, 'closeTimeEpochMillis', nextCloseTimeEpochMillis, 'status', nextStatus)
local activeAuctionIndex = 'auction:active:by-close-time'
local changeRateBasisPoints = math.floor((price - tonumber(startPrice)) * 10000 / tonumber(startPrice))
if buyNow then
    redis.call('EXPIRE', KEYS[1], 3600 + (tonumber(string.match(KEYS[1], 'auction:state:(.+)')) % 18001))
    redis.call('ZREM', activeAuctionIndex, string.match(KEYS[1], 'auction:state:(.+)'))
    redis.call('ZREM', KEYS[6], string.match(KEYS[1], 'auction:state:(.+)'))
    redis.call('ZREM', KEYS[7], string.match(KEYS[1], 'auction:state:(.+)'))
    redis.call('ZREM', KEYS[8], string.match(KEYS[1], 'auction:state:(.+)'))
    redis.call('ZREM', KEYS[9], string.match(KEYS[1], 'auction:state:(.+)'))
    redis.call('ZREM', KEYS[10], string.match(KEYS[1], 'auction:state:(.+)'))
else
    redis.call('ZADD', activeAuctionIndex, nextCloseTimeEpochMillis, string.match(KEYS[1], 'auction:state:(.+)'))
    redis.call('ZADD', KEYS[7], bidCountString, string.match(KEYS[1], 'auction:state:(.+)'))
    redis.call('ZADD', KEYS[8], priceString, string.match(KEYS[1], 'auction:state:(.+)'))
    redis.call('ZADD', KEYS[9], changeRateBasisPoints, string.match(KEYS[1], 'auction:state:(.+)'))
end

redis.call('XADD', 'auction:recent-bids:' .. string.match(KEYS[1], 'auction:state:(.+)'), 'MAXLEN', 50, '*',
    'bidderId', ARGV[1], 'bidPrice', priceString, 'sequence', auctionVersionString, 'occurredAt', ARGV[6])
redis.call('HSET', 'auction:bidder:' .. string.match(KEYS[1], 'auction:state:(.+)') .. ':' .. ARGV[1],
    'status', buyNow and 'WON' or 'LEADING', 'amount', priceString)
redis.call('SADD', 'auction:dashboard:participating:' .. ARGV[1], string.match(KEYS[1], 'auction:state:(.+)'))

local streamId = redis.call('XADD', KEYS[5], '*',
    'schemaVersion', '1', 'eventType', buyNow and 'auction.buy-now.v1' or 'bid.accepted.v1',
    'auctionId', string.match(KEYS[1], 'auction:state:(.+)'), 'auctionVersion', auctionVersionString,
    'bidderId', ARGV[1], 'requestedPrice', requestedPriceString, 'bidPrice', priceString,
    'previousBidderId', previousBidderId == '' and 'null' or previousBidderId,
    'idempotencyKey', ARGV[3], 'idempotencyRequestHash', ARGV[4],
    'currentPrice', priceString, 'bidCount', bidCountString, 'closeTime', nextCloseTime,
    'auctionStatus', nextStatus, 'occurredAt', ARGV[6])

local pendingOrderStatus = ''
if buyNow then
    redis.call('HINCRBY', KEYS[2], 'frozenBalance', integerString(-price))
    redis.call('DEL', KEYS[3])
    newFrozen = tonumber(redis.call('HGET', KEYS[2], 'frozenBalance'))
    local auctionId = string.match(KEYS[1], 'auction:state:(.+)')
    redis.call('HSET', 'order:state:' .. auctionId,
        'auctionId', auctionId, 'buyerId', ARGV[1], 'sellerId', sellerId, 'cardName', cardName,
        'price', priceString, 'status', 'PENDING_CONFIRM', 'projectionStatus', 'PENDING', 'streamId', streamId, 'createdAt', ARGV[6])
    redis.call('SADD', 'order:state:buyer:' .. ARGV[1], auctionId)
    redis.call('SADD', 'order:state:seller:' .. sellerId, auctionId)
    pendingOrderStatus = 'PENDING'
end

local result = 'ACCEPTED|' .. streamId .. '|' .. priceString .. '|' .. auctionVersionString .. '|' .. bidCountString
    .. '|' .. newAvailableString .. '|' .. integerString(newFrozen) .. '|' .. bidderWalletVersionString
    .. '|' .. integerString(price + bidIncrement) .. '|' .. nextCloseTime
    .. '|' .. (buyNow and 'WON' or 'LEADING') .. '|' .. pendingOrderStatus
    .. '|' .. itemId .. '|' .. startPrice .. '|' .. bidIncrement .. '|' .. (previousBidderId == '' and 'null' or previousBidderId)
    .. '|' .. nextStatus .. '|' .. tostring(closeTimeExtended)
    .. '|' .. cardName .. '|' .. cardPsaGrade .. '|' .. cardLanguage .. '|' .. cardThumbnailUrl .. '|' .. sellerId
    .. '|' .. previousAvailable .. '|' .. previousFrozen .. '|' .. previousWalletVersion
    .. '|' .. estimatedCloseTime
redis.call('SET', KEYS[4], ARGV[4] .. '|' .. result, 'EX', 86400)
return result .. '|false'
