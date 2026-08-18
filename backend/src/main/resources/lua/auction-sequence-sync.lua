-- KEYS[1]: auction:sequence, ARGV[1]: MySQL 기준 실제 최대 경매 ID
-- 현재 값이 목표값보다 작을 때만 목표값으로 올린다(뒤로 되돌리지 않는다).
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local target = tonumber(ARGV[1])
if target > current then
    redis.call('SET', KEYS[1], target)
    return 1
end
return 0
