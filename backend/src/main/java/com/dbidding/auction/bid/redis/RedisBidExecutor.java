package com.dbidding.auction.bid.redis;

import com.dbidding.auction.bid.dto.AuctionCloseData;
import com.dbidding.auction.bid.dto.BidCommand;
import com.dbidding.auction.bid.dto.BidEventData;
import com.dbidding.auction.bid.dto.BidExecutionResult;
import com.dbidding.auction.bid.BidExecutor;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.IdempotencyKeys;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.domain.WalletAmountPolicy;
import com.dbidding.global.redis.RedisIntegerValue;
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
    private final com.dbidding.wallet.service.redis.RedisWalletStateSeeder walletStateSeeder;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public RedisBidExecutor(
            StringRedisTemplate redisTemplate,
            RedisScript<String> bidAcceptScript,
            Clock clock,
            @Nullable RedisAuctionStateSeeder auctionStateSeeder,
            @Nullable com.dbidding.wallet.service.redis.RedisWalletStateSeeder walletStateSeeder,
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
        if (command.price() > WalletAmountPolicy.MAX_BALANCE) {
            throw AuctionException.invalidBidRequest("입찰 금액은 1조 원 이하여야 합니다.");
        }
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
                "auction:ending-window:by-close-time",
                "auction:active:by-bid-count",
                "auction:active:by-price",
                "auction:active:by-change-rate",
                "auction:active:by-open-time"
        );
        String raw = redisTemplate.execute(
                bidAcceptScript,
                keys,
                String.valueOf(command.bidderId()), String.valueOf(command.price()), command.idempotencyKey(), requestHash,
                String.valueOf(now.toEpochMilli()), now.toString(), String.valueOf(WalletAmountPolicy.MAX_BALANCE)
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
                new BidResponses.BidDetail(null, RedisIntegerValue.parseLongExact(fields[2]), BidStatus.valueOf(fields[10]), now, fields[1]),
                new BidResponses.AuctionSnapshot(command.auctionId(), RedisIntegerValue.parseLongExact(fields[2]),
                        RedisIntegerValue.parseLongExact(fields[8]),
                        Integer.valueOf(fields[4]), publicCloseTime),
                new BidResponses.WalletSummary(RedisIntegerValue.parseLongExact(fields[5]),
                        RedisIntegerValue.parseLongExact(fields[6])),
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
                "null".equals(fields[15]) ? null : RedisIntegerValue.parseLongExact(fields[3]),
                RedisIntegerValue.parseLongExact(fields[13]),
                RedisIntegerValue.parseLongExact(fields[14]),
                com.dbidding.auction.domain.AuctionStatus.valueOf(fields[16]),
                Boolean.parseBoolean(fields[17]),
                closeData
        ));
    }

    private void publishWalletChanged(Integer userId, String available, String frozen, String version, Instant occurredAt) {
        long availableBalance = RedisIntegerValue.parseLongExact(available);
        long frozenBalance = RedisIntegerValue.parseLongExact(frozen);
        long walletVersion = RedisIntegerValue.parseLongExact(version);
        eventPublisher.publishEvent(new com.dbidding.wallet.sse.WalletBalanceChangedEvent(
                userId,
                new com.dbidding.wallet.dto.WalletBalanceResponse(
                        Math.addExact(availableBalance, frozenBalance), frozenBalance, availableBalance, walletVersion),
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
            case "AMOUNT_LIMIT_EXCEEDED" -> AuctionException.invalidBidRequest("입찰 금액은 1조 원 이하여야 합니다.");
            case "BALANCE_LIMIT_EXCEEDED" -> AuctionException.invalidBidRequest("지갑 총 보유액은 1조 원 이하여야 합니다.");
            case "SELLER" -> AuctionException.sellerBidForbidden();
            case "LEADING_BIDDER" -> AuctionException.leadingBidderConflict();
            default -> AuctionException.invalidBidRequest("Redis 입찰 조건을 만족하지 않습니다.");
        };
    }
}
