-- KEYS[1]: auction state hash, KEYS[2]: active auction ZSET, KEYS[3]: recent bids stream,
-- KEYS[4]: OPEN auction ending-window ZSET, KEYS[5]: active-by-bid-count index,
-- KEYS[6]: active-by-price index, KEYS[7]: active-by-change-rate index, KEYS[8]: active-by-open-time index
-- ARGV: closeTimeEpochMillis, auctionId, state field count, state field/value pairs,
--       participant count, (bidderId, status, amount)*, recent bid count,
--       (bidId, bidderId, bidPrice, sequence, occurredAt)*,
--       bidCount, currentPrice, changeRateBasisPoints, openTimeEpochMillis
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end

local position = 3
local stateFieldCount = tonumber(ARGV[position])
position = position + 1
local stateArguments = {}
for index = 1, stateFieldCount * 2 do
    table.insert(stateArguments, ARGV[position])
    position = position + 1
end

local participantCount = tonumber(ARGV[position])
position = position + 1
for index = 1, participantCount do
    local bidderId = ARGV[position]
    local status = ARGV[position + 1]
    local amount = ARGV[position + 2]
    position = position + 3
    redis.call('HSET', 'auction:bidder:' .. ARGV[2] .. ':' .. bidderId, 'status', status, 'amount', amount)
    redis.call('SADD', 'auction:dashboard:participating:' .. bidderId, ARGV[2])
end

local recentBidCount = tonumber(ARGV[position])
position = position + 1
for index = 1, recentBidCount do
    local bidId = ARGV[position]
    local bidderId = ARGV[position + 1]
    local bidPrice = ARGV[position + 2]
    local sequence = ARGV[position + 3]
    local occurredAt = ARGV[position + 4]
    position = position + 5
    redis.call('XADD', KEYS[3], 'MAXLEN', 50, '*',
        'bidId', bidId, 'bidderId', bidderId, 'bidPrice', bidPrice,
        'sequence', sequence, 'occurredAt', occurredAt)
end

redis.call('HSET', KEYS[1], unpack(stateArguments))
local status = redis.call('HGET', KEYS[1], 'status')
-- 종료 상태는 EXPIRE만 걸고 활성 인덱스 5종에는 아예 넣지 않는다: 활성 인덱스 GC(auction-active-index-gc.lua)는
-- 24시간 이상 지난 뒤에야 상태를 재확인하는데, state의 TTL(최대 6시간)이 그보다 짧아 GC가 확인할 시점엔 이미
-- state가 사라져 status를 영영 알 수 없게 된다 - 즉 한 번 넣으면 어떤 GC로도 못 지우는 영구 리크가 된다.
if status == 'ENDED' or status == 'CANCELLED' or status == 'FAILED' then
    redis.call('EXPIRE', KEYS[1], 3600 + (tonumber(ARGV[2]) % 18001))
else
    redis.call('ZADD', KEYS[2], ARGV[1], ARGV[2])
    if status == 'OPEN' then
        local estimatedCloseTimeEpochMillis = tonumber(redis.call('HGET', KEYS[1], 'estimatedCloseTimeEpochMillis')) or tonumber(ARGV[1])
        redis.call('ZADD', KEYS[4], estimatedCloseTimeEpochMillis - 300000, ARGV[2])
    end
    redis.call('ZADD', KEYS[5], ARGV[position], ARGV[2])
    redis.call('ZADD', KEYS[6], ARGV[position + 1], ARGV[2])
    redis.call('ZADD', KEYS[7], ARGV[position + 2], ARGV[2])
    redis.call('ZADD', KEYS[8], ARGV[position + 3], ARGV[2])
end
return 1
