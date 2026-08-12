-- KEYS[1]: auction state hash, KEYS[2]: active auction ZSET
-- ARGV: closeTimeEpochMillis, auctionId, followed by hash field/value pairs
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
redis.call('HSET', KEYS[1], unpack(ARGV, 3))
redis.call('ZADD', KEYS[2], ARGV[1], ARGV[2])
return 1
