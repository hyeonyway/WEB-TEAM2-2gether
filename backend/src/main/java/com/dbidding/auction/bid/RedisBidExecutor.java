package com.dbidding.auction.bid;

import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.BidResponses;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis Lua가 경매·지갑 상태를 전이하고 Stream 이벤트를 같은 EVAL 안에서 추가한 결과를
 * HTTP 응답으로 변환한다. Redis 상태가 승인 기준이며, MySQL ID는 비동기 영속화 전에는 없다.
 */
@Service
@Profile("bid-redis-stream")
@RequiredArgsConstructor
@Slf4j
public class RedisBidExecutor implements BidExecutor {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> bidAcceptScript;
    private final Clock clock;

    @Override
    public BidExecutionResult execute(BidCommand command) {
        Instant now = clock.instant();
        String eventId = command.auctionId() + ":" + command.bidderId() + ":" + command.idempotencyKey();
        List<String> keys = List.of(
                "auction:state:" + command.auctionId(),
                "wallet:balance:" + command.bidderId(),
                "wallet:hold:" + command.auctionId() + ":" + command.bidderId(),
                "auction:bid:idempotency:" + command.auctionId() + ":" + command.bidderId() + ":" + command.idempotencyKey(),
                "auction:bid-events:" + command.auctionId()
        );
        String raw = redisTemplate.execute(
                bidAcceptScript,
                keys,
                String.valueOf(command.bidderId()), String.valueOf(command.price()), command.idempotencyKey(), eventId,
                String.valueOf(now.toEpochMilli())
        );
        String[] fields = raw.split("\\|", -1);
        if (!"ACCEPTED".equals(fields[0]) || fields.length != 9) {
            throw com.dbidding.auction.exception.AuctionException.invalidBidRequest("Redis 입찰 승인에 실패했습니다.");
        }
        BidResponses.BidResult result = new BidResponses.BidResult(
                new BidResponses.BidDetail(null, Long.valueOf(fields[2]), BidStatus.LEADING, now, fields[1]),
                new BidResponses.AuctionSnapshot(command.auctionId(), Long.valueOf(fields[2]), null,
                        Integer.valueOf(fields[4]), Instant.ofEpochMilli(Long.parseLong(fields[8]))),
                new BidResponses.WalletSummary(Long.parseLong(fields[5]), Long.parseLong(fields[6]))
        );
        return new BidExecutionResult(result, null);
    }
}
