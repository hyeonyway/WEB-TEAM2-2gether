package com.dbidding.wallet.sse;

import com.dbidding.sse.config.CountingDiscardPolicy;
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

    /**
     * caller는 {@code WalletSseConnectionManager}(SSE 전용 백그라운드 스레드)다. 이 executor는
     * 순수 SSE send만 하고 다른 부작용(DB write 등)이 없어, 포화 시 discard해도 잔고
     * 값 자체는 유실되지 않는다(재연결/재조회 시 최신 값을 다시 받는다) — API 스레드
     * 보호를 우선한다.
     */
    @Bean(name = "walletSseTaskExecutor")
    public TaskExecutor walletSseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("wallet-sse-");
        executor.setRejectedExecutionHandler(new CountingDiscardPolicy(meterRegistry, "wallet"));
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
