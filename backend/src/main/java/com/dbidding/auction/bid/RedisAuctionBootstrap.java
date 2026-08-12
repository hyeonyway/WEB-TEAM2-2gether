package com.dbidding.auction.bid;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.card.service.CardService;
import java.time.Clock;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis 입찰 경로가 시작 전에 활성 경매의 승인 상태를 MySQL projection에서 구성한다. */
@Configuration
@Profile("redis")
@RequiredArgsConstructor
public class RedisAuctionBootstrap {
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final CardService cardService;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Bean
    ApplicationRunner redisAuctionStateBootstrap() {
        return arguments -> auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(
                EnumSet.of(AuctionStatus.OPEN, AuctionStatus.ENDING), clock.instant()
        ).forEach(this::seed);
    }

    private void seed(Auction auction) {
        Bid leading = bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(
                auction.getId(), BidStatus.LEADING
        ).orElse(null);
        Map<String, String> state = new HashMap<>();
        state.put("status", auction.getStatus().name());
        state.put("sellerId", String.valueOf(auction.getSellerId()));
        state.put("cardName", cardService.getCardSnapshot(auction.getItemId()).name());
        state.put("currentPrice", String.valueOf(auction.getCurrentPrice()));
        state.put("bidIncrement", String.valueOf(auction.getBidPriceUnit()));
        state.put("closeTime", auction.getCloseTime().toString());
        state.put("closeTimeEpochMillis", String.valueOf(auction.getCloseTime().toEpochMilli()));
        state.put("highestBidderId", leading == null ? "" : String.valueOf(leading.getBidderId()));
        state.put("highestHoldAmount", String.valueOf(leading == null ? 0L : leading.getBidPrice()));
        state.put("sequence", String.valueOf(auction.getBidCount()));
        state.put("bidCount", String.valueOf(auction.getBidCount()));
        if (auction.getBuyNowPrice() != null) state.put("buyNowPrice", String.valueOf(auction.getBuyNowPrice()));
        redisTemplate.opsForHash().putAll("auction:state:" + auction.getId(), state);
    }
}
