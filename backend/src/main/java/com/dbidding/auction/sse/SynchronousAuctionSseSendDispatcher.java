package com.dbidding.auction.sse;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 기본 프로필 — {@code broadcast()} 호출 스레드에서 emitter를 순차 처리한다(#362 이전
 * 동작 그대로, 비교/롤백용 baseline).
 */
@Component
@Profile("!sse-virtual-threads")
public class SynchronousAuctionSseSendDispatcher implements AuctionSseSendDispatcher {
    @Override
    public void dispatch(Runnable sendTask) {
        sendTask.run();
    }
}
