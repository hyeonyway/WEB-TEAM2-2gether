package com.dbidding.auction.query;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.BidResponses;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis 승인 상태에서 활성 경매의 변경 가능한 read model만 읽는다. */
@Component
@Profile("redis")
public class RedisAuctionRealtimeStateReader {
    private final StringRedisTemplate redisTemplate;

    public RedisAuctionRealtimeStateReader(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RealtimeState read(Integer auctionId, Integer userId) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(stateKey(auctionId));
        if (fields.isEmpty()) return null;
        try {
            AuctionStatus status = AuctionStatus.valueOf(required(fields, "status"));
            long currentPrice = Long.parseLong(required(fields, "currentPrice"));
            long bidIncrement = Long.parseLong(required(fields, "bidIncrement"));
            int bidCount = Integer.parseInt(required(fields, "bidCount"));
            Instant closeTime = Instant.parse(required(fields, "closeTime"));
            Long buyNowPrice = nullableLong(fields.get("buyNowPrice"));
            List<BidResponses.BidSummary> recentBids = recentBids(auctionId, userId);
            Map<Object, Object> myBid = userId == null ? Map.of() : redisTemplate.opsForHash().entries(bidderKey(auctionId, userId));
            MyBidStatus myBidStatus = myBid.isEmpty() ? MyBidStatus.NONE : MyBidStatus.valueOf(required(myBid, "status"));
            Long myBidAmount = myBid.isEmpty() ? null : Long.valueOf(required(myBid, "amount"));
            return new RealtimeState(status, currentPrice, bidIncrement, bidCount, closeTime, buyNowPrice,
                    myBidStatus, myBidAmount, recentBids);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private List<BidResponses.BidSummary> recentBids(Integer auctionId, Integer userId) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().reverseRange(
                recentBidKey(auctionId), Range.unbounded(), org.springframework.data.redis.connection.Limit.limit().count(5)
        );
        if (records == null) return List.of();
        return records.stream().map(record -> summary(record, userId)).toList();
    }

    private BidResponses.BidSummary summary(MapRecord<String, Object, Object> record, Integer userId) {
        Map<Object, Object> values = record.getValue();
        Integer bidderId = Integer.valueOf(value(values.get("bidderId")));
        return new BidResponses.BidSummary(null, Long.valueOf(value(values.get("bidPrice"))), alias(bidderId),
                userId != null && userId.equals(bidderId), Instant.parse(value(values.get("occurredAt"))));
    }

    private String required(Map<Object, Object> fields, String name) {
        String value = value(fields.get(name));
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + name);
        return value;
    }
    private String value(Object value) { return value == null ? null : value.toString(); }
    private Long nullableLong(Object value) { String text = value(value); return text == null || text.isBlank() ? null : Long.valueOf(text); }
    private String stateKey(Integer auctionId) { return "auction:state:" + auctionId; }
    private String recentBidKey(Integer auctionId) { return "auction:recent-bids:" + auctionId; }
    private String bidderKey(Integer auctionId, Integer userId) { return "auction:bidder:" + auctionId + ":" + userId; }
    private String alias(Integer bidderId) { return bidderId == null ? "" : bidderId < 100 ? "user-" + bidderId + "***" : "user-" + String.valueOf(bidderId).substring(0, 2) + "***"; }

    public record RealtimeState(AuctionStatus status, long currentPrice, long bidIncrement, int bidCount,
                                Instant closeTime, Long buyNowPrice, MyBidStatus myBidStatus,
                                Long myBidAmount, List<BidResponses.BidSummary> recentBids) { }
}
