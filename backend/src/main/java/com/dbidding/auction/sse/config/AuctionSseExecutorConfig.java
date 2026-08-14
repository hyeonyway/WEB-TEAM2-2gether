package com.dbidding.auction.sse.config;

import com.dbidding.sse.config.CountingCallerRunsPolicy;
import com.dbidding.sse.config.VirtualThreadSseTaskExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@Slf4j
@RequiredArgsConstructor
public class AuctionSseExecutorConfig {
    private final MeterRegistry meterRegistry;
    @Value("${AUCTION_SSE_CORE_POOL_SIZE:4}")
    private int corePoolSize;

    @Value("${AUCTION_SSE_MAX_POOL_SIZE:8}")
    private int maxPoolSize;

    @Value("${AUCTION_SSE_QUEUE_CAPACITY:2000}")
    private int queueCapacity;

    /**
     * {@code broadcast()}/{@code heartbeat()}는 DB 접근 없이 순수 네트워크 SSE
     * send만 하는 작업이다.
     */
    @Bean(name = "auctionSseTaskExecutor")
    @Profile("!sse-virtual-threads")
    public TaskExecutor auctionSseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("auction-sse-");
        executor.setRejectedExecutionHandler(new CountingCallerRunsPolicy(meterRegistry, "auction"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * {@code sse-virtual-threads} 프로필 전용 — 위와 동일한 워크로드를 가상 스레드로
     * 처리한다(#362). 공유 자원이 없는 전역 브로드캐스트라 커넥션 1개당 독립
     * task로 세분화해도(디스패처 참고) 스레드 고갈 위험이 없다.
     */
    @Bean(name = "auctionSseTaskExecutor")
    @Profile("sse-virtual-threads")
    public TaskExecutor auctionSseVirtualTaskExecutor() {
        return new VirtualThreadSseTaskExecutor("auction-sse-", meterRegistry, "auction-sse");
    }
}
