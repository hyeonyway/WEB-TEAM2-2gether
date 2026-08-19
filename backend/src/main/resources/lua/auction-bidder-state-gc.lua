-- KEYS[1..]: candidate keys collected by the caller via SCAN - auction:bidder:{auctionId}:{bidderId}
--            (Hash) 또는 auction:recent-bids:{auctionId} (Stream) 둘 다 이 스크립트로 정리할 수 있다.
--            삭제 대상 키의 타입은 신경 쓰지 않는다 - 아래 EXISTS 체크만 통과하면 DEL한다.
-- ARGV[1..]: auctionId for each KEYS[n], same order and length as KEYS
-- auction:state는 경매 종료 시 TTL이 걸려 결국 사라지지만, 여기서 다루는 키들은 만료 로직이 없어
-- state가 사라진 뒤에도 영구 잔존한다 - 그 state가 이미 없는(=경매가 끝난 지 오래된) 것만 지운다.
local removed = 0
for index, key in ipairs(KEYS) do
    local auctionId = ARGV[index]
    if redis.call('EXISTS', 'auction:state:' .. auctionId) == 0 then
        redis.call('DEL', key)
        removed = removed + 1
    end
end
return removed
