-- KEYS[1]: active auctions ZSET (score: closeTimeEpochMillis, member: auctionId)
-- ARGV[1]: cardId, ARGV[2]: currentEpochMillis
-- State hashes are the Redis source of truth while the redis profile is active.
local auctionIds = redis.call('ZRANGEBYSCORE', KEYS[1], ARGV[2], '+inf')
local count = 0

for _, auctionId in ipairs(auctionIds) do
    local stateKey = 'auction:state:' .. auctionId
    local itemId = redis.call('HGET', stateKey, 'itemId')
    local status = redis.call('HGET', stateKey, 'status')
    if itemId == ARGV[1] and (status == 'OPEN' or status == 'ENDING') then
        count = count + 1
    end
end

return count
