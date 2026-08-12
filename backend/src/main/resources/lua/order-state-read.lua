-- KEYS[1]: orderId -> auctionId index
-- Redis Lua combines dependent GET and HGETALL into one client round trip.
local auctionId = redis.call('GET', KEYS[1])
if not auctionId then return '' end
local fields = redis.call('HGETALL', 'order:state:' .. auctionId)
if #fields == 0 then return '' end
local state = {}
for index = 1, #fields, 2 do state[fields[index]] = fields[index + 1] end
return cjson.encode(state)
