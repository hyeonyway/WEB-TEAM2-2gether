package com.dbidding.auction.bid;

import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.exception.CardException;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis-first 경매 command가 사용하는 카드 snapshot read model이다. */
@Component
@Profile("redis")
public class RedisCardStateReader {
    private final StringRedisTemplate redisTemplate;

    public RedisCardStateReader(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public CardSnapshot getCardSnapshot(Integer cardId) {
        Map<Object, Object> state = redisTemplate.opsForHash().entries("card:state:" + cardId);
        if (state.isEmpty()) throw CardException.notFound();
        return new CardSnapshot(cardId, required(state, "name"), required(state, "setName"),
                nullable(state.get("psaGrade")), nullable(state.get("language")), required(state, "thumbnailUrl"));
    }

    private String required(Map<Object, Object> state, String field) {
        String value = nullable(state.get(field));
        if (value == null) throw CardException.notFound();
        return value;
    }

    private String nullable(Object value) {
        String text = value == null ? null : value.toString();
        return text == null || text.isBlank() ? null : text;
    }
}
