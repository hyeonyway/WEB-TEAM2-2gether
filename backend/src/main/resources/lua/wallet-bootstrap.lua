-- KEYS[1]: wallet balance hash
-- ARGV: availableBalance, frozenBalance, walletVersion
local current = tonumber(redis.call('HGET', KEYS[1], 'walletVersion') or '-1')
local target = tonumber(ARGV[3])
if current >= target then return 0 end
redis.call('HSET', KEYS[1], 'availableBalance', ARGV[1], 'frozenBalance', ARGV[2], 'walletVersion', ARGV[3])
return 1
