package com.dbidding.sse;

import org.springframework.core.task.TaskExecutor;

/** 커넥션(emitter) 1개당 독립 task로 세분화해 executor에 위임한다(#362). */
public class PerConnectionSseSendDispatcher implements SseSendDispatcher {
    private final TaskExecutor executor;

    public PerConnectionSseSendDispatcher(TaskExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void dispatch(Runnable sendTask) {
        executor.execute(sendTask);
    }
}
