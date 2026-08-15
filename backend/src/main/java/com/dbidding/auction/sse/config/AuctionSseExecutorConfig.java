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

    /** {@code sse-virtual-threads} 전용 — 0이면 무제한(기본값), 양수면 동시 실행 개수를 이 값으로 제한한다. */
    @Value("${AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY:0}")
    private int virtualMaxConcurrency;

    @Value("${AUCTION_SSE_BROADCAST_CORE_POOL_SIZE:2}")
    private int broadcastCorePoolSize;

    @Value("${AUCTION_SSE_BROADCAST_MAX_POOL_SIZE:4}")
    private int broadcastMaxPoolSize;

    @Value("${AUCTION_SSE_BROADCAST_QUEUE_CAPACITY:2000}")
    private int broadcastQueueCapacity;

    /**
     * emitter별 실제 전송({@code send()})만 담당한다. {@code sse-virtual-threads}에서는
     * {@code AUCTION_SSE_VIRTUAL_MAX_CONCURRENCY}로 캡을 걸 수 있다(#507 이후
     * {@code broadcast()}는 이 executor를 안 쓰므로, 캡이 꽉 차도 send 자체만 지연되고
     * 순회 호출자는 안 묶인다).
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
        return new VirtualThreadSseTaskExecutor("auction-sse-", meterRegistry, "auction-sse", virtualMaxConcurrency);
    }

    /**
     * {@code AuctionSseConnectionManager.broadcast()} 전용(#507). emitter 집합을 순회하며
     * 실제 send는 {@code auctionSseTaskExecutor}에 위임만 하는 가벼운 코디네이션 작업이라,
     * send용 캡(세마포어/스레드풀)과 예산을 나눠 쓰면 안 된다 — 그러면 캡이 꽉 찼을 때
     * broadcast() 호출자(Redis pub/sub 스레드 또는 입찰 처리 Tomcat 스레드)까지 블로킹된다.
     * 그래서 이 executor에는 캡을 두지 않는다.
     */
    @Bean(name = "auctionSseBroadcastTaskExecutor")
    @Profile("!sse-virtual-threads")
    public TaskExecutor auctionSseBroadcastTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(broadcastCorePoolSize);
        executor.setMaxPoolSize(broadcastMaxPoolSize);
        executor.setQueueCapacity(broadcastQueueCapacity);
        executor.setThreadNamePrefix("auction-sse-broadcast-");
        executor.setRejectedExecutionHandler(new CountingCallerRunsPolicy(meterRegistry, "auction-broadcast"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /** {@code sse-virtual-threads} 프로필 전용 — 캡 없는 가상 스레드(#507, 3-arg 생성자). */
    @Bean(name = "auctionSseBroadcastTaskExecutor")
    @Profile("sse-virtual-threads")
    public TaskExecutor auctionSseBroadcastVirtualTaskExecutor() {
        return new VirtualThreadSseTaskExecutor("auction-sse-broadcast-", meterRegistry, "auction-sse-broadcast");
    }
}
