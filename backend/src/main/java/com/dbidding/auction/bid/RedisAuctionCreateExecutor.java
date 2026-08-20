package com.dbidding.auction.bid;

import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.domain.AuctionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("redis")
@RequiredArgsConstructor
public class RedisAuctionCreateExecutor {
    private static final String TIMELINE_STREAM = "event:timeline";

    private final StringRedisTemplate redisTemplate;
    @Qualifier("auctionCreateScript")
    private final RedisScript<String> auctionCreateScript;
    private final Clock clock;
    private final RedisAuctionSequenceSync auctionSequenceSync;

    public RedisAuctionCreateResult execute(RedisAuctionCreateCommand command) {
        return execute(command, true);
    }

    /**
     * {@code auction:sequence} 카운터가 MySQL 최대 ID보다 뒤처져 있으면 Lua 스크립트가
     * {@code ID_COLLISION}을 반환한다. 이 경우 재시작 없이도 즉시 카운터를 재동기화하고
     * 딱 한 번만 재시도한다({@link RedisAuctionSequenceSync}는 뒤로 되돌리지 않는 멱등
     * 연산이라 재시도해도 안전하다). 재시도 후에도 충돌이면 더 이상 자동 복구할 수 없는
     * 상황이므로 예외를 던져 클라이언트의 재시도에 맡긴다.
     */
    private RedisAuctionCreateResult execute(RedisAuctionCreateCommand command, boolean allowResyncRetry) {
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
        if (allowResyncRetry && isIdCollision(raw)) {
            log.warn("event=auction.create.id_collision.detected sellerId={} itemId={} - resyncing auction:sequence and retrying once",
                    command.sellerId(), command.itemId());
            auctionSequenceSync.sync();
            return execute(command, false);
        }
        return parse(raw, command.closeTime());
    }

    private boolean isIdCollision(String raw) {
        String[] fields = raw.split("\\|", -1);
        return fields.length == 2 && "REJECTED".equals(fields[0]) && "ID_COLLISION".equals(fields[1]);
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
