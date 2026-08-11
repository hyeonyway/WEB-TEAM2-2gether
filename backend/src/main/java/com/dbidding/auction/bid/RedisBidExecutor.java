package com.dbidding.auction.bid;

import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.IdempotencyKeys;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
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
@Profile("redis")
@RequiredArgsConstructor
@Slf4j
public class RedisBidExecutor implements BidExecutor {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> bidAcceptScript;
    private final Clock clock;

    @Override
    public BidExecutionResult execute(BidCommand command) {
        Instant now = clock.instant();
        String requestHash = IdempotencyKeys.sha256(command.price());
        List<String> keys = List.of(
                "auction:state:" + command.auctionId(),
                "wallet:balance:" + command.bidderId(),
                "wallet:hold:" + command.auctionId() + ":" + command.bidderId(),
                "auction:bid:idempotency:" + command.auctionId() + ":" + command.bidderId() + ":" + command.idempotencyKey(),
                "auction:timeline-events"
        );
        String raw = redisTemplate.execute(
                bidAcceptScript,
                keys,
                String.valueOf(command.bidderId()), String.valueOf(command.price()), command.idempotencyKey(), requestHash,
                String.valueOf(now.toEpochMilli()), now.toString()
        );
        String[] fields = raw.split("\\|", -1);
        if (!"ACCEPTED".equals(fields[0])) {
            throw rejection(fields.length > 1 ? fields[1] : "UNKNOWN");
        }
        if (fields.length != 10) {
            throw AuctionException.invalidBidRequest("Redis 입찰 승인 응답이 올바르지 않습니다.");
        }
        BidResponses.BidResult result = new BidResponses.BidResult(
                new BidResponses.BidDetail(null, Long.valueOf(fields[2]), BidStatus.LEADING, now, fields[1]),
                new BidResponses.AuctionSnapshot(command.auctionId(), Long.valueOf(fields[2]), Long.valueOf(fields[8]),
                        Integer.valueOf(fields[4]), Instant.parse(fields[9])),
                new BidResponses.WalletSummary(Long.parseLong(fields[5]), Long.parseLong(fields[6]))
        );
        return new BidExecutionResult(result, null);
    }

    private RuntimeException rejection(String reason) {
        return switch (reason) {
            case "IDEMPOTENCY_CONFLICT" -> AuctionException.idempotencyConflict();
            case "INSUFFICIENT_BALANCE" -> new InsufficientAvailableBalanceException();
            case "SELLER" -> AuctionException.sellerBidForbidden();
            case "LEADING_BIDDER" -> AuctionException.leadingBidderConflict();
            default -> AuctionException.invalidBidRequest("Redis 입찰 조건을 만족하지 않습니다.");
        };
    }
}
