-- KEYS[1..]: auction:dashboard:participating:{bidderId} SET들, collected by the caller via SCAN
-- 이 SET들은 사용자가 참여한 경매 id를 누적만 하고 제거 로직이 없어 영구 잔존한다 - state가 이미
-- 사라진(=경매가 끝난 지 오래된) auctionId 멤버만 골라 지운다. 여러 SET을 한 번의 스크립트 호출로
-- 묶어 처리해, GC 실행 1회당 Redis 왕복 횟수를 키 개수만큼이 아니라 배치 수만큼으로 줄인다.
local removed = 0
for _, key in ipairs(KEYS) do
    local members = redis.call('SMEMBERS', key)
    for _, auctionId in ipairs(members) do
        if redis.call('EXISTS', 'auction:state:' .. auctionId) == 0 then
            redis.call('SREM', key, auctionId)
            removed = removed + 1
        end
    end
end
return removed
