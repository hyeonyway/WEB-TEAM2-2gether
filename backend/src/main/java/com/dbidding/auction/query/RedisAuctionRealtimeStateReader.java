package com.dbidding.auction.query;

import com.dbidding.auction.BidderAlias;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.global.redis.RedisIntegerValue;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisCallback;
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
        StoredAuctionState stored = readStoredAuctionState(auctionId);
        if (stored == null) return null;
        return read(stored, userId);
    }

    public RealtimeState read(StoredAuctionState stored, Integer userId) {
        Integer auctionId = stored.state().auctionId();
        try {
            AuctionState state = stored.state();
            List<BidResponses.BidSummary> recentBids = recentBids(auctionId, stored.highestBidderId());
            Map<Object, Object> myBid = userId == null ? Map.of() : redisTemplate.opsForHash().entries(bidderKey(auctionId, userId));
            MyBidState myBidState = parseMyBidStateOrNone(myBid);
            return new RealtimeState(state.status(), state.currentPrice(), state.bidIncrement(), state.bidCount(), state.closeTime(), state.buyNowPrice(),
                    myBidState.status(), myBidState.amount(), recentBids);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return null;
        }
    }

    /** MySQL projection 전에도 활성 경매를 응답하기 위한 Redis 원본 상태다. */
    public AuctionState readAuctionState(Integer auctionId) {
        StoredAuctionState stored = readStoredAuctionState(auctionId);
        return stored == null ? null : stored.state();
    }

    public StoredAuctionState readStoredAuctionState(Integer auctionId) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(stateKey(auctionId));
        AuctionState state = parseAuctionState(auctionId, fields);
        if (state == null) return null;
        return new StoredAuctionState(state, highestBidderId(fields));
    }

    /** highestBidderId 하나가 손상돼도 나머지 필드로 만들어진 state 자체는 살린다. */
    private Integer highestBidderId(Map<Object, Object> fields) {
        try {
            return nullableInteger(fields.get("highestBidderId"));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return null;
        }
    }

    /** 후보 경매 state를 한 connection의 pipeline으로 읽고 입력 순서대로 유효 state만 반환한다. */
    public Map<Integer, AuctionState> readAuctionStates(List<Integer> auctionIds) {
        if (auctionIds.isEmpty()) return Map.of();
        List<Map<Object, Object>> fieldsList = pipelinedHashGetAll(auctionIds.stream().map(this::stateKey).toList());
        Map<Integer, AuctionState> states = new LinkedHashMap<>();
        for (int index = 0; index < auctionIds.size(); index++) {
            AuctionState state = parseAuctionState(auctionIds.get(index), fieldsList.get(index));
            if (state != null) states.put(auctionIds.get(index), state);
        }
        return states;
    }

    /** 목록에 포함된 경매 중 실제 참여 경매의 bidder state만 batch로 읽는다. */
    public Map<Integer, MyBidState> readMyBidStates(List<Integer> auctionIds, Integer userId) {
        if (userId == null || auctionIds.isEmpty()) return Map.of();
        List<String> idValues = auctionIds.stream().map(String::valueOf).toList();
        Map<Object, Boolean> membership = redisTemplate.opsForSet().isMember(
                participatingKey(userId), idValues.toArray()
        );
        List<Integer> participatingIds = java.util.stream.IntStream.range(0, auctionIds.size())
                .filter(index -> Boolean.TRUE.equals(membership.get(idValues.get(index))))
                .mapToObj(auctionIds::get)
                .toList();
        if (participatingIds.isEmpty()) return Map.of();

        List<Map<Object, Object>> fieldsList = pipelinedHashGetAll(
                participatingIds.stream().map(auctionId -> bidderKey(auctionId, userId)).toList()
        );
        Map<Integer, MyBidState> states = new LinkedHashMap<>();
        for (int index = 0; index < participatingIds.size(); index++) {
            Map<Object, Object> fields = fieldsList.get(index);
            // 손상된(파싱 불가) bidder state는 미참여 상태로 취급한다 - read(StoredAuctionState, Integer)와 동일한 처리.
            if (!fields.isEmpty()) states.put(participatingIds.get(index), parseMyBidStateOrNone(fields));
        }
        return states;
    }

    private List<Map<Object, Object>> pipelinedHashGetAll(List<String> keys) {
        List<Object> responses = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.hashCommands().hGetAll(redisTemplate.getStringSerializer().serialize(key));
            }
            return null;
        });
        return responses.stream().map(this::hashResponse).toList();
    }

    private AuctionState parseAuctionState(Integer auctionId, Map<Object, Object> fields) {
        if (fields.isEmpty()) return null;
        try {
            return new AuctionState(
                    auctionId, AuctionStatus.valueOf(required(fields, "status")), Integer.valueOf(required(fields, "sellerId")),
                    Integer.valueOf(required(fields, "itemId")), required(fields, "cardName"), required(fields, "cardSetName"), nullableString(fields.get("cardPsaGrade")),
                    nullableString(fields.get("cardLanguage")), nullableString(fields.get("cardThumbnailUrl")), required(fields, "auctionName"),
                    required(fields, "description"), nullableString(fields.get("sellerMemo")), nullableString(fields.get("psaCertification")),
                    nullableString(fields.get("selfGrade")), Boolean.parseBoolean(required(fields, "psaVerified")),
                    RedisIntegerValue.parseLongExact(required(fields, "startPrice")),
                    RedisIntegerValue.parseLongExact(required(fields, "currentPrice")),
                    RedisIntegerValue.parseLongExact(required(fields, "bidIncrement")),
                    Math.toIntExact(RedisIntegerValue.parseLongExact(required(fields, "bidCount"))),
                    nullableLong(fields.get("buyNowPrice")), RedisIntegerValue.parseLongExact(required(fields, "deliveryFee")),
                    Instant.parse(required(fields, "openTime")), Instant.parse(required(fields, "closeTime")),
                    splitLines(required(fields, "imagePaths")),
                    optionalInstant(fields.get("estimatedCloseTime")).orElse(Instant.parse(required(fields, "closeTime")))
            );
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> hashResponse(Object response) {
        return response instanceof Map<?, ?> ? (Map<Object, Object>) response : Map.of();
    }

    private MyBidState parseMyBidState(Map<Object, Object> fields) {
        return fields.isEmpty()
                ? new MyBidState(MyBidStatus.NONE, null)
                : new MyBidState(
                        MyBidStatus.valueOf(required(fields, "status")),
                        RedisIntegerValue.parseLongExact(required(fields, "amount"))
                );
    }

    /** 손상된 bidder state를 미참여(NONE)로 취급한다 - 목록 조회와 단건 조회가 같은 기준으로 fallback한다. */
    private MyBidState parseMyBidStateOrNone(Map<Object, Object> fields) {
        try {
            return parseMyBidState(fields);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return new MyBidState(MyBidStatus.NONE, null);
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
        StoredAuctionState stored = readStoredAuctionState(auctionId);
        if (stored == null) return null;
        AuctionState state = stored.state();
        return new Snapshot(state.status(), state.currentPrice(), state.bidIncrement(), state.bidCount(), state.closeTime(),
                state.buyNowPrice(), stored.highestBidderId());
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
        long sequence = RedisIntegerValue.parseLongExact(value(values.get("sequence")));
        Long bidId = nullableLong(values.get("bidId"));
        // Long.MAX_VALUE - sequence는 JSON으로 프론트에 전달되며 JS Number(double)로 변환될 때
        // 정밀도가 깨져(그 크기대의 double 표현 간격이 sequence 차이보다 커서) 서로 다른 입찰이
        // 같은 id로 뭉개진다. 프론트가 이미 "DB에 아직 없는 실시간 입찰"을 음수 id로 표시하는
        // 관례(-event_id)를 따르므로 여기서도 -sequence를 쓴다.
        return new BidResponses.BidSummary(bidId == null ? -sequence : bidId,
                RedisIntegerValue.parseLongExact(value(values.get("bidPrice"))), BidderAlias.mask(bidderId),
                bidderId.equals(highestBidderId), Instant.parse(value(values.get("occurredAt"))));
    }

    private String required(Map<Object, Object> fields, String name) {
        String value = value(fields.get(name));
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + name);
        return value;
    }
    private String value(Object value) { return value == null ? null : value.toString(); }
    private Long nullableLong(Object value) { String text = value(value); return text == null || text.isBlank() ? null : RedisIntegerValue.parseLongExact(text); }
    private Integer nullableInteger(Object value) { String text = value(value); return text == null || text.isBlank() ? null : Integer.valueOf(text); }
    private java.util.Optional<Instant> optionalInstant(Object value) { String text = value(value); return text == null || text.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(Instant.parse(text)); }
    private String nullableString(Object value) { String text = value(value); return text == null || text.isBlank() ? null : text; }
    private List<String> splitLines(String value) { return List.of(value.split("\\n", -1)); }
    private String stateKey(Integer auctionId) { return "auction:state:" + auctionId; }
    private String recentBidKey(Integer auctionId) { return "auction:recent-bids:" + auctionId; }
    private String bidderKey(Integer auctionId, Integer userId) { return "auction:bidder:" + auctionId + ":" + userId; }
    private String participatingKey(Integer userId) { return "auction:dashboard:participating:" + userId; }

    public record RealtimeState(AuctionStatus status, long currentPrice, long bidIncrement, int bidCount,
                                Instant closeTime, Long buyNowPrice, MyBidStatus myBidStatus,
                                Long myBidAmount, List<BidResponses.BidSummary> recentBids) { }
    public record Snapshot(AuctionStatus status, long currentPrice, long bidIncrement, int bidCount,
                           Instant closeTime, Long buyNowPrice, Integer highestBidderId) { }
    public record StoredAuctionState(AuctionState state, Integer highestBidderId) { }
    public record MyBidState(MyBidStatus status, Long amount) { }
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
