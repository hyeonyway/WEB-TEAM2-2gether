package com.dbidding.auction.sse;

/**
 * 경매 전역 스트림 이벤트를 모든 서버 인스턴스에 전파하기 위한 발행 지점. 저장이 필요 없는
 * 브로드캐스트라 로컬 Spring 이벤트를 한 번 더 거치지 않고 {@code AuctionCommandService}가
 * 이벤트를 조립하는 자리에서 곧바로 호출한다.
 */
public interface AuctionStreamPublisher {
    String CHANNEL = "auction:stream";

    void publish(AuctionStreamPayload payload);
}
