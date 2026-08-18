package com.dbidding.auction.bid.redis;

import com.dbidding.auction.bid.dto.RedisAuctionCreateCommand;
import com.dbidding.auction.bid.dto.RedisAuctionCreateResult;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.domain.AuctionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
@RequiredArgsConstructor
public class RedisAuctionCreateExecutor {
    private static final String TIMELINE_STREAM = "event:timeline";

    private final StringRedisTemplate redisTemplate;
    @Qualifier("auctionCreateScript")
    private final RedisScript<String> auctionCreateScript;
    private final Clock clock;

    public RedisAuctionCreateResult execute(RedisAuctionCreateCommand command) {
        Instant occurredAt = clock.instant();
        String raw = redisTemplate.execute(auctionCreateScript, List.of(
                        "auction:sequence",
                        "auction:create:idempotency:" + command.sellerId() + ':' + command.idempotencyKey(),
                        TIMELINE_STREAM,
                        "auction:ending-window:by-close-time",
                        "auction:active:by-bid-count",
                        "auction:active:by-price",
                        "auction:active:by-change-rate",
                        "auction:active:by-open-time"
                ),
                command.sellerId().toString(), command.itemId().toString(), required(command.cardName()), required(command.cardSetName()),
                nullable(command.cardPsaGrade()), nullable(command.cardLanguage()), nullable(command.cardThumbnailUrl()),
                required(command.auctionName()), required(command.description()), nullable(command.sellerMemo()),
                nullable(command.psaCertification()), nullable(command.selfGrade()), Boolean.toString(command.psaVerified()),
                Long.toString(command.startPrice()), nullable(command.buyNowPrice()), Long.toString(command.deliveryFee()),
                Long.toString(command.bidPriceUnit()), String.join("\n", command.imagePaths()), command.closeTime().toString(),
                Long.toString(command.closeTime().toEpochMilli()), command.idempotencyKey(), command.idempotencyRequestHash(), occurredAt.toString(),
                Long.toString(occurredAt.toEpochMilli()));
        return parse(raw, command.closeTime());
    }

    private RedisAuctionCreateResult parse(String raw, Instant closeTime) {
        String[] fields = raw.split("\\|", -1);
        if (fields.length == 2 && "REJECTED".equals(fields[0]) && "IDEMPOTENCY_CONFLICT".equals(fields[1])) {
            throw AuctionException.idempotencyConflict();
        }
        if (fields.length == 2 && "REJECTED".equals(fields[0]) && "ID_COLLISION".equals(fields[1])) {
            throw AuctionException.invalidRequest("경매 ID 발급이 기존 경매와 충돌했습니다. 다시 시도해 주세요.");
        }
        if (fields.length != 7 || !"ACCEPTED".equals(fields[0])) {
            throw AuctionException.invalidRequest("경매 생성 Redis 상태 전이에 실패했습니다.");
        }
        return new RedisAuctionCreateResult(Integer.valueOf(fields[1]), fields[2], AuctionStatus.valueOf(fields[3]),
                Instant.parse(fields[4]), closeTime, Boolean.parseBoolean(fields[6]));
    }

    private String required(String value) {
        if (value == null || value.isBlank()) throw AuctionException.invalidRequest("경매 생성 필수 정보가 없습니다.");
        return value;
    }

    private String nullable(Object value) {
        return value == null ? "" : value.toString();
    }
}
