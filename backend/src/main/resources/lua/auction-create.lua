-- KEYS: auction sequence, seller idempotency result, single timeline stream
-- ARGV: sellerId, itemId, cardName, cardPsaGrade, cardLanguage, cardThumbnailUrl, auctionName, description,
--       sellerMemo, psaCertification, selfGrade, psaVerified, startPrice, buyNowPrice, deliveryFee, bidPriceUnit,
--       imagePaths, closeTime, closeTimeEpochMillis, idempotencyKey, idempotencyRequestHash, occurredAt
local existing = redis.call('GET', KEYS[2])
if existing then
    local separator = string.find(existing, '|')
    if string.sub(existing, 1, separator - 1) ~= ARGV[21] then
        return 'REJECTED|IDEMPOTENCY_CONFLICT'
    end
    return string.sub(existing, separator + 1)
end

local auctionId = redis.call('INCR', KEYS[1])
local stateKey = 'auction:state:' .. auctionId
redis.call('HSET', stateKey,
    'status', 'OPEN',
    'sellerId', ARGV[1],
    'itemId', ARGV[2],
    'cardName', ARGV[3],
    'cardPsaGrade', ARGV[4],
    'cardLanguage', ARGV[5],
    'cardThumbnailUrl', ARGV[6],
    'auctionName', ARGV[7],
    'description', ARGV[8],
    'sellerMemo', ARGV[9],
    'psaCertification', ARGV[10],
    'selfGrade', ARGV[11],
    'psaVerified', ARGV[12],
    'startPrice', ARGV[13],
    'currentPrice', ARGV[13],
    'buyNowPrice', ARGV[14],
    'deliveryFee', ARGV[15],
    'bidIncrement', ARGV[16],
    'imagePaths', ARGV[17],
    'openTime', ARGV[22],
    'closeTime', ARGV[18],
    'closeTimeEpochMillis', ARGV[19],
    'highestBidderId', '',
    'highestHoldAmount', '0',
    'sequence', '0',
    'bidCount', '0')
redis.call('ZADD', 'auction:active:by-close-time', ARGV[19], auctionId)

local streamId = redis.call('XADD', KEYS[3], '*',
    'schemaVersion', '1',
    'eventType', 'auction.created.v1',
    'auctionId', auctionId,
    'sellerId', ARGV[1],
    'itemId', ARGV[2],
    'cardName', ARGV[3],
    'auctionName', ARGV[7],
    'description', ARGV[8],
    'sellerMemo', ARGV[9],
    'psaCertification', ARGV[10],
    'selfGrade', ARGV[11],
    'psaVerified', ARGV[12],
    'startPrice', ARGV[13],
    'buyNowPrice', ARGV[14],
    'deliveryFee', ARGV[15],
    'bidPriceUnit', ARGV[16],
    'imagePaths', ARGV[17],
    'closeTime', ARGV[18],
    'idempotencyKey', ARGV[20],
    'idempotencyRequestHash', ARGV[21],
    'occurredAt', ARGV[22])

local result = 'ACCEPTED|' .. auctionId .. '|' .. streamId .. '|OPEN|' .. ARGV[22] .. '|' .. ARGV[18]
redis.call('SET', KEYS[2], ARGV[21] .. '|' .. result, 'EX', 86400)
return result
