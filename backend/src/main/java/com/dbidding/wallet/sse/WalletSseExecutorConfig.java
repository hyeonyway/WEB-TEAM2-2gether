package com.dbidding.wallet.sse;

import com.dbidding.sse.config.CountingCallerRunsPolicy;
import com.dbidding.sse.metrics.SseMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WalletSseExecutorConfig {

    private final MeterRegistry meterRegistry;

    @Value("${WALLET_SSE_CORE_POOL_SIZE:2}")
    private int corePoolSize;

    @Value("${WALLET_SSE_MAX_POOL_SIZE:4}")
    private int maxPoolSize;

    @Value("${WALLET_SSE_QUEUE_CAPACITY:500}")
    private int queueCapacity;

    public WalletSseExecutorConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Bean(name = "walletSseTaskExecutor")
    public TaskExecutor walletSseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("wallet-sse-");
        executor.setRejectedExecutionHandler(new CountingCallerRunsPolicy(meterRegistry, "wallet"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /** #508 — {@code WalletSseConnectionManager}의 메트릭 배선. */
    @Bean(name = "walletSseMetrics")
    public SseMetrics walletSseMetrics(Clock clock) {
        return new SseMetrics(meterRegistry, "wallet", clock);
    }
}
