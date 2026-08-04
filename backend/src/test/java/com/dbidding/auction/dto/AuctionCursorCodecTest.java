package com.dbidding.auction.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dbidding.auction.domain.AuctionSort;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AuctionCursorCodecTest {
    private final AuctionCursorCodec codec = new AuctionCursorCodec();

    @Test
    void 정렬값과_경매_ID를_opaque_cursor로_왕복한다() {
        AuctionCursor cursor = new AuctionCursor(AuctionSort.PRICE_HIGH, 45_000L, null, 17);

        String encoded = codec.encode(cursor);

        assertThat(encoded).doesNotContain("PRICE_HIGH").doesNotContain("45000");
        assertThat(codec.decode(encoded, AuctionSort.PRICE_HIGH)).isEqualTo(cursor);
    }

    @Test
    void 요청_정렬과_다른_cursor는_거부한다() {
        String encoded = codec.encode(new AuctionCursor(AuctionSort.BID_COUNT, 5L, null, 17));

        assertThatThrownBy(() -> codec.decode(encoded, AuctionSort.PRICE_LOW))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void 손상된_cursor는_거부한다() {
        assertThatThrownBy(() -> codec.decode("not-a-cursor", AuctionSort.BID_COUNT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void 최신순_cursor는_시작_시각과_경매_ID를_왕복한다() {
        AuctionCursor cursor = new AuctionCursor(
                AuctionSort.LATEST,
                null,
                LocalDateTime.of(2026, 8, 4, 12, 30, 15, 123_000_000),
                17
        );

        String encoded = codec.encode(cursor);

        assertThat(codec.decode(encoded, AuctionSort.LATEST)).isEqualTo(cursor);
    }
}
