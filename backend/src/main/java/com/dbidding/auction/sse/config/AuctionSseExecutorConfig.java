package com.dbidding.auction.sse.config;

import com.dbidding.sse.PerConnectionSseSendDispatcher;
import com.dbidding.sse.SseSendDispatcher;
import com.dbidding.sse.SynchronousSseSendDispatcher;
import com.dbidding.sse.config.CountingCallerRunsPolicy;
import com.dbidding.sse.config.VirtualThreadSseTaskExecutor;
import com.dbidding.sse.metrics.SseMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

    /**
     * broadcast() 전용 pool 크기(#507). 기본 프로필에서는 {@code SynchronousSseSendDispatcher}가
     * emitter별 send를 broadcast() 호출 스레드에서 바로 실행하므로(#362), 이 pool이 사실상
     * 실제 send I/O까지 전부 떠안는다 — 그래서 기존에 heartbeat와 공유하던 pool과 같은 크기를
     * 기본값으로 둔다(줄이면 느린 클라이언트 몇 개만으로도 이 pool이 포화돼 caller-runs로
     * 이어질 위험이 커진다).
     */
    @Value("${AUCTION_SSE_BROADCAST_CORE_POOL_SIZE:4}")
    private int broadcastCorePoolSize;

    @Value("${AUCTION_SSE_BROADCAST_MAX_POOL_SIZE:8}")
    private int broadcastMaxPoolSize;

    @Value("${AUCTION_SSE_BROADCAST_QUEUE_CAPACITY:2000}")
    private int broadcastQueueCapacity;

    /**
     * heartbeat()와({@code sse-virtual-threads}에서는) emitter별 실제 전송을 담당한다.
     * broadcast()는 이 executor를 안 쓴다(#507) — 느린 클라이언트 하나 때문에 이 pool이
     * 막혀도, broadcast() 호출자(Redis pub/sub 스레드 또는 입찰 처리 Tomcat 스레드)는
     * 별도 executor로 계속 돌 수 있어야 한다.
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
     * {@code AuctionSseConnectionManager.broadcast()} 전용(#507) — 가상 스레드 사용 여부와
     * 무관하게 항상 heartbeat/send와 별개의 executor를 쓴다. 이 프로필에서는 send가
     * broadcast() 호출 스레드에서 바로 실행되므로(#362) 이 pool이 실질적으로 send I/O까지
     * 떠안는다 — 느린 클라이언트 하나가 이 pool을 막아도 heartbeat용 pool은 영향받지
     * 않고, 이 pool 자체가 포화돼도(queue+CallerRunsPolicy) heartbeat 쪽 호출자는 안전하다.
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

    /** #508 — {@code AuctionSseConnectionManager}의 메트릭 배선. */
    @Bean(name = "auctionSseMetrics")
    public SseMetrics auctionSseMetrics(Clock clock) {
        return new SseMetrics(meterRegistry, "auction", clock);
    }

    /** 기본 프로필 — emitter별 send를 broadcast() 호출 스레드에서 순차 실행한다(#362 baseline). */
    @Bean(name = "auctionSseSendDispatcher")
    @Profile("!sse-virtual-threads")
    public SseSendDispatcher auctionSseSendDispatcher() {
        return new SynchronousSseSendDispatcher();
    }

    /** {@code sse-virtual-threads} 프로필 — 커넥션 1개당 독립 task로 세분화한다(#362). */
    @Bean(name = "auctionSseSendDispatcher")
    @Profile("sse-virtual-threads")
    public SseSendDispatcher auctionSseSendDispatcherVirtual(@Qualifier("auctionSseTaskExecutor") TaskExecutor auctionSseTaskExecutor) {
        return new PerConnectionSseSendDispatcher(auctionSseTaskExecutor);
    }
}
