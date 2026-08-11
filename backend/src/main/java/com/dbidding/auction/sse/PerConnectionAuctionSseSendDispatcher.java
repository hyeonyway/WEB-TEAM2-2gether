package com.dbidding.auction.sse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * {@code sse-virtual-threads} 프로필 — 커넥션 1개당 독립 task로 세분화한다(#362).
 * 전역 브로드캐스트라 notification의 유저별 채널보다 연결 수가 많을 수 있어, 느린
 * 클라이언트 하나가 나머지 전송을 막지 않게 하는 이득이 여기서 더 크게 나타난다.
 */
@Component
@Profile("sse-virtual-threads")
public class PerConnectionAuctionSseSendDispatcher implements AuctionSseSendDispatcher {
    private final TaskExecutor auctionSseTaskExecutor;

    public PerConnectionAuctionSseSendDispatcher(@Qualifier("auctionSseTaskExecutor") TaskExecutor auctionSseTaskExecutor) {
        this.auctionSseTaskExecutor = auctionSseTaskExecutor;
    }

    @Override
    public void dispatch(Runnable sendTask) {
        auctionSseTaskExecutor.execute(sendTask);
    }
}
