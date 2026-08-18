package com.dbidding.auction.bid.redis;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dbidding.auction.repository.AuctionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisAuctionSequenceSyncTest {
    @Test
    void 경매가_하나도_없으면_동기화하지_않는다() throws Exception {
        var auctionRepository = mock(AuctionRepository.class);
        var redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") RedisScript<Long> script = mock(RedisScript.class);
        when(auctionRepository.findMaxId()).thenReturn(null);
        RedisAuctionSequenceSync sync = new RedisAuctionSequenceSync(auctionRepository, redisTemplate, script);

        sync.redisAuctionSequenceSyncRunner().run(null);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void 최대_ID를_카운터_동기화_스크립트에_그대로_전달한다() throws Exception {
        var auctionRepository = mock(AuctionRepository.class);
        var redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") RedisScript<Long> script = mock(RedisScript.class);
        when(auctionRepository.findMaxId()).thenReturn(502);
        when(redisTemplate.execute(eq(script), eq(List.of("auction:sequence")), eq("502"))).thenReturn(1L);
        RedisAuctionSequenceSync sync = new RedisAuctionSequenceSync(auctionRepository, redisTemplate, script);

        sync.redisAuctionSequenceSyncRunner().run(null);

        verify(redisTemplate).execute(eq(script), eq(List.of("auction:sequence")), eq("502"));
    }

    @Test
    void 스크립트가_변경_없음을_반환해도_예외_없이_끝난다() throws Exception {
        var auctionRepository = mock(AuctionRepository.class);
        var redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") RedisScript<Long> script = mock(RedisScript.class);
        when(auctionRepository.findMaxId()).thenReturn(100);
        when(redisTemplate.execute(eq(script), eq(List.of("auction:sequence")), eq("100"))).thenReturn(0L);
        RedisAuctionSequenceSync sync = new RedisAuctionSequenceSync(auctionRepository, redisTemplate, script);

        sync.redisAuctionSequenceSyncRunner().run(null);

        verify(redisTemplate).execute(eq(script), eq(List.of("auction:sequence")), eq("100"));
    }
}
