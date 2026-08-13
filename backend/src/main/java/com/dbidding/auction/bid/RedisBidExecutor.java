package com.dbidding.auction.bid;

import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.IdempotencyKeys;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis Lua가 경매·지갑 상태를 전이하고 Stream 이벤트를 같은 EVAL 안에서 추가한 결과를
 * HTTP 응답으로 변환한다. Redis 상태가 승인 기준이며, MySQL ID는 비동기 영속화 전에는 없다.
 */
@Service
@Profile("redis")
@Slf4j
public class RedisBidExecutor implements BidExecutor {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> bidAcceptScript;
    private final Clock clock;
    private final RedisAuctionStateSeeder auctionStateSeeder;
    private final com.dbidding.wallet.service.RedisWalletStateSeeder walletStateSeeder;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public RedisBidExecutor(
            StringRedisTemplate redisTemplate,
            RedisScript<String> bidAcceptScript,
            Clock clock,
            @Nullable RedisAuctionStateSeeder auctionStateSeeder,
            @Nullable com.dbidding.wallet.service.RedisWalletStateSeeder walletStateSeeder,
            ApplicationEventPublisher eventPublisher
    ) {
        this.redisTemplate = redisTemplate;
        this.bidAcceptScript = bidAcceptScript;
        this.clock = clock;
        this.auctionStateSeeder = auctionStateSeeder;
        this.walletStateSeeder = walletStateSeeder;
        this.eventPublisher = eventPublisher;
    }

    /** Lua 단독 통합 테스트가 기존 준비된 Redis 상태를 사용할 수 있도록 유지한다. */
    RedisBidExecutor(StringRedisTemplate redisTemplate, RedisScript<String> bidAcceptScript, Clock clock) {
        this(redisTemplate, bidAcceptScript, clock, null, null, event -> { });
    }

    @Override
    public BidExecutionResult execute(BidCommand command) {
        if (auctionStateSeeder != null) auctionStateSeeder.seedIfAbsent(command.auctionId());
        if (walletStateSeeder != null) walletStateSeeder.seedIfAbsent(command.bidderId());
        Instant now = clock.instant();
        String requestHash = IdempotencyKeys.sha256(command.price());
        List<String> keys = List.of(
                "auction:state:" + command.auctionId(),
                "wallet:balance:" + command.bidderId(),
                "wallet:hold:" + command.auctionId() + ":" + command.bidderId(),
                "auction:bid:idempotency:" + command.auctionId() + ":" + command.bidderId() + ":" + command.idempotencyKey(),
                "event:timeline",
                "auction:ending-window:by-close-time"
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
        if (fields.length != 27 && fields.length != 28) {
            throw AuctionException.invalidBidRequest("Redis 입찰 승인 응답이 올바르지 않습니다.");
        }
        Instant publicCloseTime = Instant.parse(fields.length == 28 ? fields[26] : fields[9]);
        BidResponses.BidResult result = new BidResponses.BidResult(
                new BidResponses.BidDetail(null, Long.valueOf(fields[2]), BidStatus.valueOf(fields[10]), now, fields[1]),
                new BidResponses.AuctionSnapshot(command.auctionId(), Long.valueOf(fields[2]), Long.valueOf(fields[8]),
                        Integer.valueOf(fields[4]), publicCloseTime),
                new BidResponses.WalletSummary(Long.parseLong(fields[5]), Long.parseLong(fields[6])),
                fields[11].isBlank() ? null : new BidResponses.PendingOrder(command.auctionId(), fields[11], fields[1])
        );
        boolean replayed = Boolean.parseBoolean(fields[fields.length - 1]);
        if (replayed) {
            return new BidExecutionResult(result, null);
        }
        publishWalletChanged(command.bidderId(), fields[5], fields[6], fields[7], now);
        if (!fields[23].isBlank()) {
            publishWalletChanged(Integer.valueOf(fields[15]), fields[23], fields[24], fields[25], now);
        }
        AuctionCloseData closeData = "ENDED".equals(fields[16])
                ? new AuctionCloseData(Integer.valueOf(fields[12]), fields[18], nullable(fields[19]),
                        nullable(fields[20]), nullable(fields[21]), Integer.valueOf(fields[22]))
                : null;
        return new BidExecutionResult(result, new BidEventData(
                Integer.valueOf(fields[12]),
                "null".equals(fields[15]) ? null : Integer.valueOf(fields[15]),
                "null".equals(fields[15]) ? null : Long.valueOf(fields[3]),
                Long.valueOf(fields[13]),
                Long.valueOf(fields[14]),
                com.dbidding.auction.domain.AuctionStatus.valueOf(fields[16]),
                Boolean.parseBoolean(fields[17]),
                closeData
        ));
    }

    private void publishWalletChanged(Integer userId, String available, String frozen, String version, Instant occurredAt) {
        long availableBalance = Long.parseLong(available);
        long frozenBalance = Long.parseLong(frozen);
        long walletVersion = Long.parseLong(version);
        eventPublisher.publishEvent(new com.dbidding.wallet.sse.WalletBalanceChangedEvent(
                userId,
                new com.dbidding.wallet.dto.WalletBalanceResponse(
                        availableBalance + frozenBalance, frozenBalance, availableBalance, walletVersion),
                walletVersion,
                occurredAt
        ));
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value;
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
