-- KEYS[1]: OPEN auction ending-window ZSET
-- ARGV[1]: maximum members to inspect
-- A due OPEN auction stays here until the ENDING transition script consumes it.
local auctionIds = redis.call('ZRANGE', KEYS[1], 0, tonumber(ARGV[1]) - 1)
local removed = 0
for _, auctionId in ipairs(auctionIds) do
    local status = redis.call('HGET', 'auction:state:' .. auctionId, 'status')
    if status ~= 'OPEN' then
        redis.call('ZREM', KEYS[1], auctionId)
        removed = removed + 1
    end
end
return removed
