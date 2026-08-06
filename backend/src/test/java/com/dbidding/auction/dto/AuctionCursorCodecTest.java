package com.dbidding.auction.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dbidding.auction.domain.AuctionSort;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
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

    @Test
    void 입찰수순_cursor에_정렬값이_없으면_거부한다() {
        String encoded = encodeRaw("v3|BID_COUNT|||17");

        assertThatThrownBy(() -> codec.decode(encoded, AuctionSort.BID_COUNT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void 최신순_cursor에_시작_시각이_없으면_거부한다() {
        String encoded = encodeRaw("v3|LATEST|||17");

        assertThatThrownBy(() -> codec.decode(encoded, AuctionSort.LATEST))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void 경매_ID가_양수가_아니면_거부한다() {
        String encoded = encodeRaw("v3|PRICE_HIGH|45000||0");

        assertThatThrownBy(() -> codec.decode(encoded, AuctionSort.PRICE_HIGH))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void 입찰수순_cursor가_음수이면_거부한다() {
        String encoded = encodeRaw("v3|BID_COUNT|-1||17");

        assertThatThrownBy(() -> codec.decode(encoded, AuctionSort.BID_COUNT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void 입찰수순_cursor가_int_범위를_벗어나면_거부한다() {
        String encoded = encodeRaw("v3|BID_COUNT|2147483648||17");

        assertThatThrownBy(() -> codec.decode(encoded, AuctionSort.BID_COUNT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    private String encodeRaw(String raw) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
