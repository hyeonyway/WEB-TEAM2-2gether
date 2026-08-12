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

/**
 * Redis 승인 상태에서 활성 경매의 변경 가능한 read model만 읽는다.
 *
 * <p>현재 키 형식은 단일 Redis 인스턴스를 전제로 한다. Cluster로 확장할 때는 Lua에서 함께
 * 접근하는 경매 키에 같은 hash tag를 적용해야 한다.</p>
 */
@Component
@Profile("redis")
public class RedisAuctionRealtimeStateReader {
    private final StringRedisTemplate redisTemplate;

    public RedisAuctionRealtimeStateReader(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RealtimeState read(Integer auctionId, Integer userId) {
        Snapshot snapshot = readSnapshot(auctionId);
        if (snapshot == null) return null;
        try {
            List<BidResponses.BidSummary> recentBids = recentBids(auctionId, snapshot.highestBidderId());
            Map<Object, Object> myBid = userId == null ? Map.of() : redisTemplate.opsForHash().entries(bidderKey(auctionId, userId));
            MyBidStatus myBidStatus = myBid.isEmpty() ? MyBidStatus.NONE : MyBidStatus.valueOf(required(myBid, "status"));
            Long myBidAmount = myBid.isEmpty() ? null : Long.valueOf(required(myBid, "amount"));
            return new RealtimeState(snapshot.status(), snapshot.currentPrice(), snapshot.bidIncrement(), snapshot.bidCount(), snapshot.closeTime(), snapshot.buyNowPrice(),
                    myBidStatus, myBidAmount, recentBids);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** MySQL projection 전에도 활성 경매를 응답하기 위한 Redis 원본 상태다. */
    public AuctionState readAuctionState(Integer auctionId) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(stateKey(auctionId));
        if (fields.isEmpty()) return null;
        try {
            return new AuctionState(
                    auctionId, AuctionStatus.valueOf(required(fields, "status")), Integer.valueOf(required(fields, "sellerId")),
                    Integer.valueOf(required(fields, "itemId")), required(fields, "cardName"), nullableString(fields.get("cardPsaGrade")),
                    nullableString(fields.get("cardLanguage")), nullableString(fields.get("cardThumbnailUrl")), required(fields, "auctionName"),
                    required(fields, "description"), nullableString(fields.get("sellerMemo")), nullableString(fields.get("psaCertification")),
                    nullableString(fields.get("selfGrade")), Boolean.parseBoolean(required(fields, "psaVerified")),
                    Long.valueOf(required(fields, "startPrice")), Long.valueOf(required(fields, "currentPrice")),
                    Long.valueOf(required(fields, "bidIncrement")), Integer.valueOf(required(fields, "bidCount")),
                    nullableLong(fields.get("buyNowPrice")), Long.valueOf(required(fields, "deliveryFee")),
                    Instant.parse(required(fields, "openTime")), Instant.parse(required(fields, "closeTime")),
                    splitLines(required(fields, "imagePaths"))
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public List<Integer> activeAuctionIds() {
        java.util.Set<String> ids = redisTemplate.opsForZSet().range("auction:active:by-close-time", 0, -1);
        if (ids == null) return null;
        return ids.stream().map(Integer::valueOf).toList();
    }

    public Snapshot readSnapshot(Integer auctionId) {
        AuctionState state = readAuctionState(auctionId);
        if (state == null) return null;
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(stateKey(auctionId));
        return new Snapshot(state.status(), state.currentPrice(), state.bidIncrement(), state.bidCount(), state.closeTime(),
                state.buyNowPrice(), nullableInteger(fields.get("highestBidderId")));
    }

    private List<BidResponses.BidSummary> recentBids(Integer auctionId, Integer highestBidderId) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().reverseRange(
                recentBidKey(auctionId), Range.unbounded(), org.springframework.data.redis.connection.Limit.limit().count(5)
        );
        if (records == null) return List.of();
        return records.stream().map(record -> summary(record, highestBidderId)).toList();
    }

    private BidResponses.BidSummary summary(MapRecord<String, Object, Object> record, Integer highestBidderId) {
        Map<Object, Object> values = record.getValue();
        Integer bidderId = Integer.valueOf(value(values.get("bidderId")));
        long sequence = Long.parseLong(value(values.get("sequence")));
        return new BidResponses.BidSummary(Long.MAX_VALUE - sequence, Long.valueOf(value(values.get("bidPrice"))), alias(bidderId),
                bidderId.equals(highestBidderId), Instant.parse(value(values.get("occurredAt"))));
    }

    private String required(Map<Object, Object> fields, String name) {
        String value = value(fields.get(name));
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + name);
        return value;
    }
    private String value(Object value) { return value == null ? null : value.toString(); }
    private Long nullableLong(Object value) { String text = value(value); return text == null || text.isBlank() ? null : Long.valueOf(text); }
    private Integer nullableInteger(Object value) { String text = value(value); return text == null || text.isBlank() ? null : Integer.valueOf(text); }
    private String nullableString(Object value) { String text = value(value); return text == null || text.isBlank() ? null : text; }
    private List<String> splitLines(String value) { return List.of(value.split("\\n", -1)); }
    private String stateKey(Integer auctionId) { return "auction:state:" + auctionId; }
    private String recentBidKey(Integer auctionId) { return "auction:recent-bids:" + auctionId; }
    private String bidderKey(Integer auctionId, Integer userId) { return "auction:bidder:" + auctionId + ":" + userId; }
    private String alias(Integer bidderId) { return bidderId == null ? "" : bidderId < 100 ? "user-" + bidderId + "***" : "user-" + String.valueOf(bidderId).substring(0, 2) + "***"; }

    public record RealtimeState(AuctionStatus status, long currentPrice, long bidIncrement, int bidCount,
                                Instant closeTime, Long buyNowPrice, MyBidStatus myBidStatus,
                                Long myBidAmount, List<BidResponses.BidSummary> recentBids) { }
    public record Snapshot(AuctionStatus status, long currentPrice, long bidIncrement, int bidCount,
                           Instant closeTime, Long buyNowPrice, Integer highestBidderId) { }
    public record AuctionState(Integer auctionId, AuctionStatus status, Integer sellerId, Integer itemId, String cardName,
                               String cardPsaGrade, String cardLanguage, String cardThumbnailUrl, String auctionName, String description, String sellerMemo, String psaCertification,
                               String selfGrade, boolean psaVerified, long startPrice, long currentPrice,
                               long bidIncrement, int bidCount, Long buyNowPrice, long deliveryFee,
                               Instant openTime, Instant closeTime, List<String> imagePaths) { }
}
