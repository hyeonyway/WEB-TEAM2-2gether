package com.dbidding.sse.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CountingCallerRunsPolicy implements RejectedExecutionHandler {
    private final String executorName;
    private final Counter saturatedTasks;

    public CountingCallerRunsPolicy(MeterRegistry registry, String executorName) {
        this.executorName = executorName;
        this.saturatedTasks = Counter.builder("dbidding.sse.broadcast.rejected")
                .tag("executor", executorName)
                .description("포화된 SSE executor에서 CallerRuns로 처리한 작업 수")
                .register(registry);
    }

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            log.warn("event=sse.executor.rejected executor={} reason=shutdown", executorName);
            return;
        }
        saturatedTasks.increment();
        log.warn("event=sse.executor.saturated executor={} action=caller_runs activeCount={} queueSize={}",
                executorName, executor.getActiveCount(), executor.getQueue().size());
        task.run();
    }
}
