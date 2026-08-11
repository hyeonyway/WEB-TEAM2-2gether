-- Placeholder script for BidExecutor Redis wiring verification (issue #326).
-- Performs no real bid judgment; simply echoes the inputs it received so the
-- EVAL round trip and RedisScript bean wiring can be verified before the real
-- Lua bid algorithm lands. Do not use this script's output for anything else.
-- KEYS[1] = auction context key, ARGV[1] = bidderId, ARGV[2] = price
return KEYS[1] .. ':' .. ARGV[1] .. ':' .. ARGV[2]
