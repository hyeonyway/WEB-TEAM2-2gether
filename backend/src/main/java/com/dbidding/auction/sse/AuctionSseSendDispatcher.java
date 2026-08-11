package com.dbidding.auction.sse;

/**
 * {@code AuctionSseConnectionManager.broadcast()}가 emitter별 send를 호출 스레드에서
 * 순차 실행할지, 커넥션 1개당 독립 task로 세분화할지를 프로필({@code sse-virtual-threads})에
 * 따라 결정한다(#362).
 */
public interface AuctionSseSendDispatcher {
    void dispatch(Runnable sendTask);
}
