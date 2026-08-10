package com.dbidding.auction.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Redis 없이 이 인스턴스의 로컬 커넥션에 직접 broadcast한다(#346) — 단일 인스턴스 로컬
 * 개발 등 Redis 없는 환경에서 {@code local-sse} 프로필로 켠다. 기본값은
 * {@link RedisAuctionStreamPublisher}.
 */
@Component
@Profile("local-sse")
@RequiredArgsConstructor
public class LocalAuctionStreamPublisher implements AuctionStreamPublisher {
    private final AuctionSseConnectionManager connectionManager;

    @Override
    public void publish(AuctionStreamPayload payload) {
        connectionManager.broadcast(payload);
    }
}
