package com.dbidding.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AuctionSchemaIndexTest {

    @Test
    void cursorSortColumnsHaveIndexes() throws IOException {
        String schema = readSchema();

        assertThat(schema)
                .contains("INDEX idx_auctions_status_open_time_id "
                        + "(status, open_time DESC, id DESC)")
                .contains("INDEX idx_auctions_status_bid_count_id "
                        + "(status, bid_count DESC, id DESC)")
                .contains("INDEX idx_auctions_status_current_price "
                        + "(status, current_price)");
    }

    private String readSchema() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
