package com.dbidding.auction.bid.redis;

import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.exception.CardException;
import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.repository.CardMetadataRepository;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis-first 경매 command가 사용하는 MySQL 원본의 카드 snapshot read-through cache다. */
@Component
@Profile("redis")
public class RedisCardStateReader {
    private final StringRedisTemplate redisTemplate;
    private final CardMetadataRepository cardMetadataRepository;
    private final long ttlSeconds;
    private final long ttlJitterSeconds;

    public RedisCardStateReader(
            StringRedisTemplate redisTemplate,
            CardMetadataRepository cardMetadataRepository,
            @Value("${card.snapshot-cache.ttl-seconds:86400}") long ttlSeconds,
            @Value("${card.snapshot-cache.ttl-jitter-seconds:3600}") long ttlJitterSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.cardMetadataRepository = cardMetadataRepository;
        this.ttlSeconds = ttlSeconds;
        this.ttlJitterSeconds = ttlJitterSeconds;
    }

    public CardSnapshot getCardSnapshot(Integer cardId) {
        String key = key(cardId);
        Map<Object, Object> state = redisTemplate.opsForHash().entries(key);
        if (!state.isEmpty()) return fromCache(cardId, state);
        CardMetadata card = cardMetadataRepository.findById(cardId).orElseThrow(CardException::notFound);
        CardSnapshot snapshot = snapshot(card);
        putIfAbsent(key, snapshot);
        return snapshot;
    }

    /** Redis cache hit을 먼저 사용하고, miss 카드만 단일 MySQL 조회로 채운다. */
    public Map<Integer, CardSnapshot> getCardSnapshots(Collection<Integer> cardIds) {
        Map<Integer, CardSnapshot> snapshots = new HashMap<>();
        java.util.List<Integer> missingIds = new java.util.ArrayList<>();
        for (Integer cardId : cardIds) {
            Map<Object, Object> state = redisTemplate.opsForHash().entries(key(cardId));
            if (state.isEmpty()) missingIds.add(cardId);
            else snapshots.put(cardId, fromCache(cardId, state));
        }
        if (missingIds.isEmpty()) return snapshots;

        Map<Integer, CardMetadata> metadataById = new HashMap<>();
        cardMetadataRepository.findAllWithCardSetByIdIn(missingIds).forEach(card -> metadataById.put(card.getId(), card));
        for (Integer cardId : missingIds) {
            CardMetadata card = metadataById.get(cardId);
            if (card == null) throw CardException.notFound();
            CardSnapshot snapshot = snapshot(card);
            putIfAbsent(key(cardId), snapshot);
            snapshots.put(cardId, snapshot);
        }
        return snapshots;
    }

    private CardSnapshot fromCache(Integer cardId, Map<Object, Object> state) {
        return new CardSnapshot(cardId, required(state, "name"), required(state, "setName"),
                nullable(state.get("psaGrade")), nullable(state.get("language")), required(state, "thumbnailUrl"));
    }

    private CardSnapshot snapshot(CardMetadata card) {
        return new CardSnapshot(card.getId(), card.getName(), card.getCardSet().getName(), card.getPsaGrade(),
                card.getLanguage(), card.getImagePath());
    }

    private void putIfAbsent(String key, CardSnapshot snapshot) {
        var hashes = redisTemplate.opsForHash();
        hashes.putIfAbsent(key, "name", snapshot.name());
        hashes.putIfAbsent(key, "setName", snapshot.setName());
        hashes.putIfAbsent(key, "psaGrade", nullableValue(snapshot.psaGrade()));
        hashes.putIfAbsent(key, "language", nullableValue(snapshot.language()));
        hashes.putIfAbsent(key, "thumbnailUrl", snapshot.thumbnailUrl());
        redisTemplate.expire(key, Duration.ofSeconds(ttlFor(snapshot.cardId())));
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

    private String nullableValue(String value) { return value == null ? "" : value; }
    private String key(Integer cardId) { return "card:cache:" + cardId; }

    long ttlFor(Integer cardId) {
        if (ttlJitterSeconds <= 0) return ttlSeconds;
        return ttlSeconds + Math.floorMod(cardId.longValue(), ttlJitterSeconds + 1);
    }
}
