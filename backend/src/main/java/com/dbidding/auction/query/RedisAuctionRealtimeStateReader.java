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
import org.springframework.data.redis.core.ZSetOperations;
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
                    Integer.valueOf(required(fields, "itemId")), required(fields, "cardName"), required(fields, "cardSetName"), nullableString(fields.get("cardPsaGrade")),
                    nullableString(fields.get("cardLanguage")), nullableString(fields.get("cardThumbnailUrl")), required(fields, "auctionName"),
                    required(fields, "description"), nullableString(fields.get("sellerMemo")), nullableString(fields.get("psaCertification")),
                    nullableString(fields.get("selfGrade")), Boolean.parseBoolean(required(fields, "psaVerified")),
                    Long.valueOf(required(fields, "startPrice")), Long.valueOf(required(fields, "currentPrice")),
                    Long.valueOf(required(fields, "bidIncrement")), Integer.valueOf(required(fields, "bidCount")),
                    nullableLong(fields.get("buyNowPrice")), Long.valueOf(required(fields, "deliveryFee")),
                    Instant.parse(required(fields, "openTime")), Instant.parse(required(fields, "closeTime")),
                    splitLines(required(fields, "imagePaths")),
                    optionalInstant(fields.get("estimatedCloseTime")).orElse(Instant.parse(required(fields, "closeTime")))
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public List<Integer> activeAuctionIds() {
        java.util.Set<String> ids = redisTemplate.opsForZSet().range("auction:active:by-close-time", 0, -1);
        if (ids == null) return List.of();
        return ids.stream().map(Integer::valueOf).toList();
    }

    /**
     * 정렬 기준별 ZSET에서 bound 이후 batchSize개만 가져온다. bound가 null이면 처음부터다.
     * withinBoundOffset은 같은 score(bound)에 batchSize를 넘는 동점이 있을 때, 그 동점 구간 안에서
     * 얼마나 건너뛸지를 나타낸다 — Redis 자체의 동일 score 내 lex 순서(멤버 문자열 기준)는 우리가
     * 원하는 숫자 auctionId tie-break와 다르므로, 호출부가 반환된 값을 직접 재정렬/비교해서
     * 정확한 순서와 경계를 만든다(이 메서드는 범위만 좁혀줄 뿐 정확한 순서를 보장하지 않음).
     */
    public List<ZSetOperations.TypedTuple<String>> activeIdsBatch(String zsetKey, boolean descending, Double bound, long withinBoundOffset, int batchSize) {
        java.util.Set<ZSetOperations.TypedTuple<String>> tuples = descending
                ? redisTemplate.opsForZSet().reverseRangeByScoreWithScores(zsetKey, Double.NEGATIVE_INFINITY,
                        bound == null ? Double.POSITIVE_INFINITY : bound, withinBoundOffset, batchSize)
                : redisTemplate.opsForZSet().rangeByScoreWithScores(zsetKey, bound == null ? Double.NEGATIVE_INFINITY : bound,
                        Double.POSITIVE_INFINITY, withinBoundOffset, batchSize);
        return tuples == null ? List.of() : List.copyOf(tuples);
    }

    public List<Integer> participatingAuctionIds(Integer userId) {
        java.util.Set<String> ids = redisTemplate.opsForSet().members("auction:dashboard:participating:" + userId);
        if (ids == null) return List.of();
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
        Long bidId = nullableLong(values.get("bidId"));
        // Long.MAX_VALUE - sequence는 JSON으로 프론트에 전달되며 JS Number(double)로 변환될 때
        // 정밀도가 깨져(그 크기대의 double 표현 간격이 sequence 차이보다 커서) 서로 다른 입찰이
        // 같은 id로 뭉개진다. 프론트가 이미 "DB에 아직 없는 실시간 입찰"을 음수 id로 표시하는
        // 관례(-event_id)를 따르므로 여기서도 -sequence를 쓴다.
        return new BidResponses.BidSummary(bidId == null ? -sequence : bidId, Long.valueOf(value(values.get("bidPrice"))), alias(bidderId),
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
    private java.util.Optional<Instant> optionalInstant(Object value) { String text = value(value); return text == null || text.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(Instant.parse(text)); }
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
    public record AuctionState(Integer auctionId, AuctionStatus status, Integer sellerId, Integer itemId, String cardName, String cardSetName,
                               String cardPsaGrade, String cardLanguage, String cardThumbnailUrl, String auctionName, String description, String sellerMemo, String psaCertification,
                               String selfGrade, boolean psaVerified, long startPrice, long currentPrice,
                               long bidIncrement, int bidCount, Long buyNowPrice, long deliveryFee,
                               Instant openTime, Instant closeTime, List<String> imagePaths, Instant estimatedCloseTime) {
        public AuctionState(Integer auctionId, AuctionStatus status, Integer sellerId, Integer itemId, String cardName, String cardSetName,
                            String cardPsaGrade, String cardLanguage, String cardThumbnailUrl, String auctionName, String description, String sellerMemo,
                            String psaCertification, String selfGrade, boolean psaVerified, long startPrice, long currentPrice, long bidIncrement,
                            int bidCount, Long buyNowPrice, long deliveryFee, Instant openTime, Instant closeTime, List<String> imagePaths) {
            this(auctionId, status, sellerId, itemId, cardName, cardSetName, cardPsaGrade, cardLanguage, cardThumbnailUrl, auctionName,
                    description, sellerMemo, psaCertification, selfGrade, psaVerified, startPrice, currentPrice, bidIncrement, bidCount,
                    buyNowPrice, deliveryFee, openTime, closeTime, imagePaths, closeTime);
        }
    }
}
