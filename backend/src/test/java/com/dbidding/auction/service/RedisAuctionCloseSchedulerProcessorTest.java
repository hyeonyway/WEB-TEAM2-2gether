package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisAuctionCloseSchedulerProcessorTest {
    private final AuctionRepository auctionRepository = mock(AuctionRepository.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final RedisScript<Long> auctionCloseRequestScript = mock(RedisScript.class);
    private final RedisAuctionCloseSchedulerProcessor processor = new RedisAuctionCloseSchedulerProcessor(
            auctionRepository, redisTemplate, auctionCloseRequestScript
    );

    @Test
    void 종료된_경매_context를_갱신하고_종료_요청_event를_발행한다() {
        Instant now = Instant.parse("2026-08-12T01:00:00Z");
        when(auctionRepository.findDueAuctionIds(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING), now, PageRequest.of(0, 100)
        )).thenReturn(List.of(11, 12));
        when(redisTemplate.execute(eq(auctionCloseRequestScript), anyList(), eq("11"), eq(now.toString()), eq("1786496400000")))
                .thenReturn(1L);
        when(redisTemplate.execute(eq(auctionCloseRequestScript), anyList(), eq("12"), eq(now.toString()), eq("1786496400000")))
                .thenReturn(0L);

        assertThat(processor.processDueAuctions(now, 100)).containsExactly(11);
        verify(redisTemplate).execute(eq(auctionCloseRequestScript),
                eq(List.of("auction:state:11", "auction:timeline-events")),
                eq("11"), eq(now.toString()), eq("1786496400000"));
    }
}
