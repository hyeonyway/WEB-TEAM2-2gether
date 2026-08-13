-- KEYS[1]: auction state, KEYS[2]: ending-window index, KEYS[3]: active close-time index, KEYS[4]: timeline stream
-- ARGV[1]: auction id, ARGV[2]: now epoch millis, ARGV[3]: now ISO-8601,
--          ARGV[4]: new close ISO-8601, ARGV[5]: new close epoch millis
local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'OPEN' then
    return 'NOOP|' .. (status or 'MISSING')
end

local closeTimeEpochMillis = tonumber(redis.call('HGET', KEYS[1], 'closeTimeEpochMillis'))
if not closeTimeEpochMillis then
    return 'NOOP|STATE_MISSING'
end

local nowEpochMillis = tonumber(ARGV[2])
if nowEpochMillis >= closeTimeEpochMillis then
    redis.call('ZREM', KEYS[2], ARGV[1])
    return 'NOOP|EXPIRED'
end
if closeTimeEpochMillis - 300000 > nowEpochMillis then
    return 'NOOP|TOO_EARLY'
end

redis.call('HSET', KEYS[1],
    'status', 'ENDING',
    'closeTime', ARGV[4],
    'closeTimeEpochMillis', ARGV[5])
redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('ZADD', KEYS[3], ARGV[5], ARGV[1])
local streamId = redis.call('XADD', KEYS[4], '*',
    'schemaVersion', '1',
    'eventType', 'auction.ending-started.v1',
    'auctionId', ARGV[1],
    'closeTime', ARGV[4],
    'closeTimeEpochMillis', ARGV[5],
    'occurredAt', ARGV[3])
return 'TRANSITIONED|' .. streamId .. '|' .. ARGV[4]
