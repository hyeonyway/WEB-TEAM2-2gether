-- KEYS[1]: active auction ZSET
-- ARGV[1]: stale close-time upper bound (epoch millis), ARGV[2]: maximum entries to inspect
-- OPEN/ENDING entries are deliberately retained: the close scheduler must be able to retry them.
local auctionIds = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
local removed = 0
for _, auctionId in ipairs(auctionIds) do
    local status = redis.call('HGET', 'auction:state:' .. auctionId, 'status')
    if status == 'ENDED' or status == 'CANCELLED' or status == 'FAILED' then
        redis.call('ZREM', KEYS[1], auctionId)
        removed = removed + 1
    end
end
return removed
