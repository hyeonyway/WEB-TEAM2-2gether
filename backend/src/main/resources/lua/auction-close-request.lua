-- KEYS[1] = auction context, KEYS[2] = auction timeline stream, KEYS[3] = ending-window index,
-- KEYS[4] = active-by-bid-count index, KEYS[5] = active-by-price index, KEYS[6] = active-by-change-rate index,
-- KEYS[7] = active-by-open-time index
-- ARGV[1] = auction id, ARGV[2] = occurredAt ISO-8601, ARGV[3] = occurredAt epoch millis,
-- ARGV[4] = max balance
local function integerString(value)
    return string.format('%.0f', value)
end

-- A repeated deadline/backup scheduler invocation must not append a second close request.
if redis.call('HGET', KEYS[1], 'closeRequestedAt') then
    return 'REPLAYED'
end

local winnerId = redis.call('HGET', KEYS[1], 'highestBidderId') or ''
local winningPrice = tonumber(redis.call('HGET', KEYS[1], 'highestHoldAmount') or '0')
local sellerId = redis.call('HGET', KEYS[1], 'sellerId')
local itemId = redis.call('HGET', KEYS[1], 'itemId')
local cardName = redis.call('HGET', KEYS[1], 'cardName') or ''
local cardPsaGrade = redis.call('HGET', KEYS[1], 'cardPsaGrade') or ''
local cardLanguage = redis.call('HGET', KEYS[1], 'cardLanguage') or ''
local cardThumbnailUrl = redis.call('HGET', KEYS[1], 'cardThumbnailUrl') or ''
local startPrice = tonumber(redis.call('HGET', KEYS[1], 'startPrice'))
local currentPrice = tonumber(redis.call('HGET', KEYS[1], 'currentPrice'))
local bidIncrement = tonumber(redis.call('HGET', KEYS[1], 'bidIncrement'))
local bidCount = tonumber(redis.call('HGET', KEYS[1], 'bidCount'))
if not sellerId or not itemId or not startPrice or not currentPrice or not bidIncrement or not bidCount then
    return 'REJECTED|STATE_MISSING'
end
local maxBalance = tonumber(ARGV[4])
if not maxBalance or winningPrice > maxBalance or startPrice > maxBalance
        or currentPrice > maxBalance or bidIncrement > maxBalance then
    return 'REJECTED|AMOUNT_LIMIT_EXCEEDED'
end

local winningPriceText = integerString(winningPrice)
local startPriceText = integerString(startPrice)
local currentPriceText = integerString(currentPrice)
local bidIncrementText = integerString(bidIncrement)
local bidCountText = integerString(bidCount)

local winnerAvailable = ''
local winnerFrozen = ''
local winnerWalletVersion = ''
if winnerId ~= '' then
    local winnerBalanceKey = 'wallet:balance:' .. winnerId
    local winnerHoldKey = 'wallet:hold:' .. ARGV[1] .. ':' .. winnerId
    local currentWinnerAvailable = tonumber(redis.call('HGET', winnerBalanceKey, 'availableBalance'))
    local currentWinnerFrozen = tonumber(redis.call('HGET', winnerBalanceKey, 'frozenBalance'))
    local currentWinnerVersion = redis.call('HGET', winnerBalanceKey, 'walletVersion')
    if not currentWinnerAvailable or not currentWinnerFrozen or not currentWinnerVersion or currentWinnerFrozen < winningPrice then
        return 'REJECTED|WALLET_STATE_MISSING'
    end
    winnerAvailable = integerString(currentWinnerAvailable)
    winnerFrozen = integerString(redis.call('HINCRBY', winnerBalanceKey, 'frozenBalance', integerString(-winningPrice)))
    winnerWalletVersion = integerString(redis.call('HINCRBY', winnerBalanceKey, 'walletVersion', 1))
    redis.call('DEL', winnerHoldKey)
    redis.call('HSET', 'auction:bidder:' .. ARGV[1] .. ':' .. winnerId, 'status', 'WON')
end

redis.call('HSET', KEYS[1],
    'status', 'ENDED',
    'closeTime', ARGV[2],
    'closeTimeEpochMillis', ARGV[3],
    'closeRequestedAt', ARGV[2])
redis.call('ZREM', 'auction:active:by-close-time', ARGV[1])
redis.call('ZREM', KEYS[3], ARGV[1])
redis.call('ZREM', KEYS[4], ARGV[1])
redis.call('ZREM', KEYS[5], ARGV[1])
redis.call('ZREM', KEYS[6], ARGV[1])
redis.call('ZREM', KEYS[7], ARGV[1])
redis.call('EXPIRE', KEYS[1], 3600 + (tonumber(ARGV[1]) % 18001))

redis.call('XADD', KEYS[2], '*',
    'schemaVersion', '1',
    'eventType', 'auction.close-requested.v1',
    'auctionId', ARGV[1],
    'occurredAt', ARGV[2])
return 'ACCEPTED|' .. winnerId .. '|' .. winningPriceText .. '|' .. sellerId .. '|' .. itemId
    .. '|' .. cardName .. '|' .. cardPsaGrade .. '|' .. cardLanguage .. '|' .. cardThumbnailUrl
    .. '|' .. startPriceText .. '|' .. currentPriceText .. '|' .. bidIncrementText .. '|' .. bidCountText
    .. '|' .. winnerAvailable .. '|' .. winnerFrozen .. '|' .. winnerWalletVersion
