-- KEYS[1]: card cache hash
-- ARGV: name, setName, psaGrade, language, thumbnailUrl, ttlSeconds
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
redis.call('HSET', KEYS[1], 'name', ARGV[1], 'setName', ARGV[2], 'psaGrade', ARGV[3], 'language', ARGV[4], 'thumbnailUrl', ARGV[5])
redis.call('EXPIRE', KEYS[1], ARGV[6])
return 1
