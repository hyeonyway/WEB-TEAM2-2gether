package com.dbidding.auction.sse.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AuctionSseExecutorConfig {
    @Value("${AUCTION_SSE_CORE_POOL_SIZE:4}")
    private int corePoolSize;

    @Value("${AUCTION_SSE_MAX_POOL_SIZE:8}")
    private int maxPoolSize;

    @Value("${AUCTION_SSE_QUEUE_CAPACITY:2000}")
    private int queueCapacity;

    @Bean(name = "auctionSseTaskExecutor")
    public ThreadPoolTaskExecutor auctionSseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("auction-sse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
