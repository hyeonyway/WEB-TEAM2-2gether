-- KEYS: order state, orderId index, buyer index, seller index
-- ARGV: orderId, auctionId, buyerId, sellerId, cardName, price, status, createdAt
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
redis.call('HSET', KEYS[1],
    'orderId', ARGV[1], 'auctionId', ARGV[2], 'buyerId', ARGV[3], 'sellerId', ARGV[4],
    'cardName', ARGV[5], 'price', ARGV[6], 'status', ARGV[7], 'createdAt', ARGV[8],
    'orderVersion', '0', 'projectionStatus', 'PROJECTED')
redis.call('SET', KEYS[2], ARGV[2])
redis.call('SADD', KEYS[3], ARGV[2])
redis.call('SADD', KEYS[4], ARGV[2])
return 1
