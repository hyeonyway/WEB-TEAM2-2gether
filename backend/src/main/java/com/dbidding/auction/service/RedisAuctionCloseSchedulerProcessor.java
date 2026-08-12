package com.dbidding.auction.service;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis profile에서는 DB를 즉시 종료하지 않는다. Redis의 실시간 context를 종료 상태로 전이하고
 * 같은 Lua 실행에서 close-requested event를 남겨 Stream consumer가 DB projection을 수행하게 한다.
 */
@Component
@Profile("redis")
@RequiredArgsConstructor
class RedisAuctionCloseSchedulerProcessor implements AuctionCloseSchedulerProcessor {
    private static final String STREAM_KEY = "event:timeline";

    private final AuctionRepository auctionRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> auctionCloseRequestScript;

    @Override
    public List<Integer> processDueAuctions(Instant now, int limit) {
        List<Integer> auctionIds = auctionRepository.findDueAuctionIds(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING), now, PageRequest.of(0, limit)
        );
        return auctionIds.stream()
                .filter(auctionId -> Long.valueOf(1L).equals(redisTemplate.execute(
                        auctionCloseRequestScript,
                        List.of("auction:state:" + auctionId, STREAM_KEY),
                        String.valueOf(auctionId), now.toString(), String.valueOf(now.toEpochMilli())
                )))
                .toList();
    }
}
