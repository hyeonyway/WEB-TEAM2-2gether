package com.dbidding.sse.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CountingDiscardPolicy implements RejectedExecutionHandler {
    private final String executorName;
    private final Counter discardedTasks;

    public CountingDiscardPolicy(MeterRegistry registry, String executorName) {
        this.executorName = executorName;
        this.discardedTasks = Counter.builder("dbidding.sse.broadcast.saturated")
                .tag("executor", executorName)
                .description("포화된 executor에서 discard로 유실된 작업 수")
                .register(registry);
    }

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            log.warn("event=executor.unavailable executor={} reason=shutdown", executorName);
            return;
        }
        discardedTasks.increment();
        log.warn("event=executor.saturated executor={} action=discard activeCount={} queueSize={}",
                executorName, executor.getActiveCount(), executor.getQueue().size());
    }
}
