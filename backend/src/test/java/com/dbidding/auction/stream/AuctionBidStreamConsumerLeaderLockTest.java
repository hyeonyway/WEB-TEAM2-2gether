package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AuctionBidStreamConsumerLeaderLockTest {
    @Test
    @SuppressWarnings("unchecked")
    void 락을_획득하면_리더가_되고_해제시_조건부_삭제한다() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(org.mockito.ArgumentMatchers.eq(AuctionBidStreamConsumerLeaderLock.KEY),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Duration.class))).thenReturn(true);
        AuctionBidStreamConsumerLeaderLock lock = new AuctionBidStreamConsumerLeaderLock(redisTemplate, properties());

        assertThat(lock.tryAcquire()).isTrue();
        assertThat(lock.isLeader()).isTrue();
        lock.releaseAfterRun();

        assertThat(lock.isLeader()).isFalse();
        verify(redisTemplate).execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(java.util.List.of(AuctionBidStreamConsumerLeaderLock.KEY)),
                org.mockito.ArgumentMatchers.anyString());
        lock.shutdownHeartbeat();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 이미_다른_인스턴스가_락을_가지면_리더가_되지_않는다() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Duration.class))).thenReturn(false);
        AuctionBidStreamConsumerLeaderLock lock = new AuctionBidStreamConsumerLeaderLock(redisTemplate, properties());

        assertThat(lock.tryAcquire()).isFalse();
        assertThat(lock.isLeader()).isFalse();
        lock.releaseAfterRun();
        lock.shutdownHeartbeat();
    }

    @Test
    @SuppressWarnings("unchecked")
    void heartbeat_갱신에_성공하면_리더_상태를_유지한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(1L);
        AuctionBidStreamConsumerLeaderLock lock = new AuctionBidStreamConsumerLeaderLock(redisTemplate, properties());
        setLeader(lock, true);

        renew(lock);

        assertThat(lock.isLeader()).isTrue();
        lock.shutdownHeartbeat();
    }

    @Test
    @SuppressWarnings("unchecked")
    void heartbeat_갱신이_거부되면_리더_상태를_해제한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(0L);
        AuctionBidStreamConsumerLeaderLock lock = new AuctionBidStreamConsumerLeaderLock(redisTemplate, properties());
        setLeader(lock, true);

        renew(lock);

        assertThat(lock.isLeader()).isFalse();
        lock.shutdownHeartbeat();
    }

    @Test
    @SuppressWarnings("unchecked")
    void heartbeat_Redis_예외도_리더_상태를_해제한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));
        AuctionBidStreamConsumerLeaderLock lock = new AuctionBidStreamConsumerLeaderLock(redisTemplate, properties());
        setLeader(lock, true);

        renew(lock);

        assertThat(lock.isLeader()).isFalse();
        lock.shutdownHeartbeat();
    }

    private AuctionBidStreamProperties properties() {
        return new AuctionBidStreamProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), 2, Duration.ofSeconds(3), 10);
    }

    private void renew(AuctionBidStreamConsumerLeaderLock lock) throws Exception {
        java.lang.reflect.Method method = AuctionBidStreamConsumerLeaderLock.class.getDeclaredMethod("renewLease");
        method.setAccessible(true);
        method.invoke(lock);
    }

    private void setLeader(AuctionBidStreamConsumerLeaderLock lock, boolean leader) throws Exception {
        java.lang.reflect.Field field = AuctionBidStreamConsumerLeaderLock.class.getDeclaredField("leader");
        field.setAccessible(true);
        field.set(lock, leader);
    }
}
