package com.dbidding.auction.service.redis;

import java.time.Instant;
import java.util.List;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.auction.service.AuctionCloseSchedulerProcessor;
import com.dbidding.auction.sse.AuctionStreamPayload;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import com.dbidding.global.redis.RedisIntegerValue;
import com.dbidding.wallet.domain.WalletAmountPolicy;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.sse.WalletBalanceChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis profile에서는 DB를 즉시 종료하지 않는다. Redis의 실시간 context를 종료 상태로 전이하고
 * 같은 Lua 실행에서 close-requested event를 남겨 Stream consumer가 DB projection을 수행하게 한다.
 */
@Component
@Profile("redis")
class RedisAuctionCloseSchedulerProcessor implements AuctionCloseSchedulerProcessor {
    private static final String STREAM_KEY = "event:timeline";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> auctionCloseRequestScript;
    private final AuctionEventPublisher auctionEventPublisher;
    private final AuctionStreamPublisher auctionStreamPublisher;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    RedisAuctionCloseSchedulerProcessor(StringRedisTemplate redisTemplate,
                                        @Qualifier("auctionCloseRequestScript") RedisScript<String> auctionCloseRequestScript,
                                        AuctionEventPublisher auctionEventPublisher,
                                        AuctionStreamPublisher auctionStreamPublisher,
                                        ApplicationEventPublisher eventPublisher) {
        this.redisTemplate = redisTemplate;
        this.auctionCloseRequestScript = auctionCloseRequestScript;
        this.auctionEventPublisher = auctionEventPublisher;
        this.auctionStreamPublisher = auctionStreamPublisher;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<Integer> processDueAuctions(Instant now, int limit) {
        java.util.Set<String> dueAuctionIds = redisTemplate.opsForZSet()
                .rangeByScore("auction:active:by-close-time", 0, now.toEpochMilli(), 0, limit);
        if (dueAuctionIds == null || dueAuctionIds.isEmpty()) return List.of();
        List<Integer> auctionIds = dueAuctionIds.stream().map(Integer::valueOf).toList();
        return auctionIds.stream().filter(auctionId -> closeAndPublish(auctionId, now)).toList();
    }

    private boolean closeAndPublish(Integer auctionId, Instant now) {
        String raw = redisTemplate.execute(auctionCloseRequestScript,
                List.of("auction:state:" + auctionId, STREAM_KEY, "auction:ending-window:by-close-time",
                        "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate",
                        "auction:active:by-open-time"),
                String.valueOf(auctionId), now.toString(), String.valueOf(now.toEpochMilli()),
                Long.toString(WalletAmountPolicy.MAX_BALANCE));
        if (raw == null || !raw.startsWith("ACCEPTED|")) return false;
        String[] fields = raw.split("\\|", -1);
        if (fields.length != 16) throw new IllegalStateException("Redis 경매 종료 승인 응답이 올바르지 않습니다.");
        Integer winnerId = fields[1].isBlank() ? null : Integer.valueOf(fields[1]);
        Long winningPrice = winnerId == null ? null : RedisIntegerValue.parseLongExact(fields[2]);
        AuctionClosedEvent event = new AuctionClosedEvent(
                auctionId, Integer.valueOf(fields[4]), fields[5], nullable(fields[6]), nullable(fields[7]), nullable(fields[8]),
                winnerId, Integer.valueOf(fields[3]), RedisIntegerValue.parseLongExact(fields[9]),
                RedisIntegerValue.parseLongExact(fields[10]), winningPrice,
                RedisIntegerValue.parseLongExact(fields[11]), Integer.valueOf(fields[12]), now, AuctionStatus.ENDED, now);
        auctionEventPublisher.publishClosed(event);
        auctionStreamPublisher.publish(AuctionStreamPayload.closed(event));
        if (winnerId != null) {
            long available = RedisIntegerValue.parseLongExact(fields[13]);
            long frozen = RedisIntegerValue.parseLongExact(fields[14]);
            long version = RedisIntegerValue.parseLongExact(fields[15]);
            eventPublisher.publishEvent(new WalletBalanceChangedEvent(winnerId,
                    new WalletBalanceResponse(Math.addExact(available, frozen), frozen, available, version), version, now));
        }
        return true;
    }

    private String nullable(String value) { return value == null || value.isBlank() ? null : value; }

}
