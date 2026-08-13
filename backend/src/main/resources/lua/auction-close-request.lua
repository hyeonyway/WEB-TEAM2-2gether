-- KEYS[1] = auction context, KEYS[2] = auction timeline stream
-- ARGV[1] = auction id, ARGV[2] = occurredAt ISO-8601, ARGV[3] = occurredAt epoch millis
-- A repeated deadline/backup scheduler invocation must not append a second close request.
if redis.call('HGET', KEYS[1], 'closeRequestedAt') then
    return 0
end

redis.call('HSET', KEYS[1],
    'status', 'ENDED',
    'closeTime', ARGV[2],
    'closeTimeEpochMillis', ARGV[3],
    'closeRequestedAt', ARGV[2])
redis.call('ZREM', 'auction:active:by-close-time', ARGV[1])
redis.call('EXPIRE', KEYS[1], 3600 + (tonumber(ARGV[1]) % 18001))

redis.call('XADD', KEYS[2], '*',
    'schemaVersion', '1',
    'eventType', 'auction.close-requested.v1',
    'auctionId', ARGV[1],
    'occurredAt', ARGV[2])
return 1
