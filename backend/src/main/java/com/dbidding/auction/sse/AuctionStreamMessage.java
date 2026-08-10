package com.dbidding.auction.sse;

/**
 * Redis 채널로 나가는 실제 wire 메시지. {@link AuctionStreamPayload#type()}이
 * {@code @JsonIgnore}라 JSON 왕복에서 유실되므로 별도 필드로 감싼다.
 */
public record AuctionStreamMessage(AuctionStreamEventType type, AuctionStreamPayload payload) {
}
