package com.dbidding.auction.stream;

import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.repository.AuctionBidEventInboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis state miss를 MySQL projection으로 복원하기 전에 Stream 소비 완료 여부를 확인한다. */
@Component
@Profile("redis")
@RequiredArgsConstructor
public class RedisProjectionCatchUpVerifier {
    private static final String STREAM_KEY = "event:timeline";
    private final StringRedisTemplate redisTemplate;
    private final AuctionBidEventInboxRepository inboxRepository;

    public boolean isCaughtUp() {
        List<MapRecord<String, Object, Object>> latest = redisTemplate.opsForStream().reverseRange(
                STREAM_KEY, org.springframework.data.domain.Range.unbounded(), Limit.limit().count(1)
        );
        if (latest == null || latest.isEmpty()) return true;
        String streamId = latest.getFirst().getId().getValue();
        return inboxRepository.findByStreamId(streamId)
                .map(inbox -> inbox.getProjectionStatus() == AuctionBidEventProjectionStatus.PROCESSED)
                .orElse(false)
                && !inboxRepository.existsByProjectionStatus(AuctionBidEventProjectionStatus.PENDING)
                && !inboxRepository.existsByProjectionStatus(AuctionBidEventProjectionStatus.ERROR);
    }
}
