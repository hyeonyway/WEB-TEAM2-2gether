-- KEYS: auction sequence, seller idempotency result, single timeline stream, ending-window index,
--       active-by-bid-count index, active-by-price index, active-by-change-rate index, active-by-open-time index
-- ARGV: sellerId, itemId, cardName, cardSetName, cardPsaGrade, cardLanguage, cardThumbnailUrl, auctionName, description,
--       sellerMemo, psaCertification, selfGrade, psaVerified, startPrice, buyNowPrice, deliveryFee, bidPriceUnit,
--       imagePaths, closeTime, closeTimeEpochMillis, idempotencyKey, idempotencyRequestHash, occurredAt,
--       occurredAtEpochMillis
local existing = redis.call('GET', KEYS[2])
if existing then
    local separator = string.find(existing, '|')
    if string.sub(existing, 1, separator - 1) ~= ARGV[22] then
        return 'REJECTED|IDEMPOTENCY_CONFLICT'
    end
    return string.sub(existing, separator + 1) .. '|true'
end

local auctionId = redis.call('INCR', KEYS[1])
local stateKey = 'auction:state:' .. auctionId
if redis.call('EXISTS', stateKey) == 1 then
    return 'REJECTED|ID_COLLISION'
end
redis.call('HSET', stateKey,
    'status', 'OPEN',
    'sellerId', ARGV[1],
    'itemId', ARGV[2],
    'cardName', ARGV[3],
    'cardSetName', ARGV[4],
    'cardPsaGrade', ARGV[5],
    'cardLanguage', ARGV[6],
    'cardThumbnailUrl', ARGV[7],
    'auctionName', ARGV[8],
    'description', ARGV[9],
    'sellerMemo', ARGV[10],
    'psaCertification', ARGV[11],
    'selfGrade', ARGV[12],
    'psaVerified', ARGV[13],
    'startPrice', ARGV[14],
    'currentPrice', ARGV[14],
    'buyNowPrice', ARGV[15],
    'deliveryFee', ARGV[16],
    'bidIncrement', ARGV[17],
    'imagePaths', ARGV[18],
    'openTime', ARGV[23],
    'closeTime', ARGV[19],
    'closeTimeEpochMillis', ARGV[20],
    'estimatedCloseTime', ARGV[19],
    'estimatedCloseTimeEpochMillis', ARGV[20],
    'highestBidderId', '',
    'highestHoldAmount', '0',
    'sequence', '0',
    'bidCount', '0')
redis.call('ZADD', 'auction:active:by-close-time', ARGV[20], auctionId)
redis.call('ZADD', KEYS[4], ARGV[20] - 300000, auctionId)
redis.call('ZADD', KEYS[5], 0, auctionId)
redis.call('ZADD', KEYS[6], ARGV[14], auctionId)
redis.call('ZADD', KEYS[7], 0, auctionId)
redis.call('ZADD', KEYS[8], ARGV[24], auctionId)

local streamId = redis.call('XADD', KEYS[3], '*',
    'schemaVersion', '1',
    'eventType', 'auction.created.v1',
    'auctionId', auctionId,
    'sellerId', ARGV[1],
    'itemId', ARGV[2],
    'cardName', ARGV[3],
    'auctionName', ARGV[8],
    'description', ARGV[9],
    'sellerMemo', ARGV[10],
    'psaCertification', ARGV[11],
    'selfGrade', ARGV[12],
    'psaVerified', ARGV[13],
    'startPrice', ARGV[14],
    'buyNowPrice', ARGV[15],
    'deliveryFee', ARGV[16],
    'bidPriceUnit', ARGV[17],
    'imagePaths', ARGV[18],
    'closeTime', ARGV[19],
    'idempotencyKey', ARGV[21],
    'idempotencyRequestHash', ARGV[22],
    'occurredAt', ARGV[23])

local result = 'ACCEPTED|' .. auctionId .. '|' .. streamId .. '|OPEN|' .. ARGV[23] .. '|' .. ARGV[18]
redis.call('SET', KEYS[2], ARGV[22] .. '|' .. result, 'EX', 86400)
return result .. '|false'
