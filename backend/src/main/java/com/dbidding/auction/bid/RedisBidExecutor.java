package com.dbidding.auction.bid;

import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.BidResponses;
import jakarta.annotation.PostConstruct;
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
 * Lua 판단 로직이 붙기 전까지의 골격 구현체(이슈 #326). {@code bid-stub.lua}는 실제 입찰
 * 판단을 하지 않고 인자를 그대로 반환하기만 하므로, 이 클래스가 만드는
 * {@link BidResponses.BidResult}는 의미 있는 값이 아니다 — EVAL 왕복과 빈 배선이 되는지
 * 확인하는 용도로만 쓴다. 실제 판단 로직과 Redis 상태 동기화는 별도 이슈에서 다룬다.
 * {@code redis} 프로필은 이 스텁이 채워지기 전까지 실제 서비스에 켜면 안 된다.
 */
@Service
@Profile("redis")
@RequiredArgsConstructor
@Slf4j
public class RedisBidExecutor implements BidExecutor {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> bidStubScript;
    private final Clock clock;

    @PostConstruct
    void warnPlaceholder() {
        log.warn(
                "event=auction.bid.redis_executor.placeholder "
                        + "message=\"RedisBidExecutor는 실제 판단 로직이 없는 placeholder입니다. "
                        + "redis 프로필을 실제 서비스에 쓰지 마세요.\""
        );
    }

    @Override
    public BidResponses.BidResult execute(BidCommand command) {
        String raw = redisTemplate.execute(
                bidStubScript,
                List.of("auction:%d".formatted(command.auctionId())),
                String.valueOf(command.bidderId()), String.valueOf(command.price())
        );
        log.debug("event=auction.bid.redis_executor.stub_response auctionId={} raw={}", command.auctionId(), raw);
        Instant now = clock.instant();
        return new BidResponses.BidResult(
                new BidResponses.BidDetail(0L, command.price(), BidStatus.LEADING, now),
                new BidResponses.AuctionSnapshot(command.auctionId(), command.price(), command.price(), 0, now),
                new BidResponses.WalletSummary(0L, 0L)
        );
    }
}
