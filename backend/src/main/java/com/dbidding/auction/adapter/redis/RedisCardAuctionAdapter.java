package com.dbidding.auction.adapter.redis;

import com.dbidding.card.port.CardAuctionPort;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Redis의 활성 경매 인덱스와 state hash를 기준으로 카드별 진행 경매 수를 집계한다. */
@Component
@Profile("redis")
@RequiredArgsConstructor
public class RedisCardAuctionAdapter implements CardAuctionPort {
    private static final String ACTIVE_BY_CLOSE_TIME = "auction:active:by-close-time";

    private final StringRedisTemplate redisTemplate;
    @Qualifier("cardActiveAuctionCountScript") private final RedisScript<Long> cardActiveAuctionCountScript;
    private final Clock clock;

    @Override
    public int countActiveAuctions(Integer cardId) {
        Long count = redisTemplate.execute(cardActiveAuctionCountScript, List.of(ACTIVE_BY_CLOSE_TIME),
                String.valueOf(cardId), String.valueOf(clock.instant().toEpochMilli()));
        return count == null ? 0 : Math.toIntExact(count);
    }
}
