-- KEYS[1]: wallet balance hash, KEYS[2..]: held amount hashes
-- ARGV: availableBalance, frozenBalance, walletVersion, held amounts
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
redis.call('HSET', KEYS[1], 'availableBalance', ARGV[1], 'frozenBalance', ARGV[2], 'walletVersion', ARGV[3])
local userId = string.match(KEYS[1], 'wallet:balance:(.+)')
redis.call('EXPIRE', KEYS[1], 3600 + (tonumber(userId) % 18001))
for index = 2, #KEYS do
    redis.call('HSETNX', KEYS[index], 'amount', ARGV[index + 2])
end
return 1
