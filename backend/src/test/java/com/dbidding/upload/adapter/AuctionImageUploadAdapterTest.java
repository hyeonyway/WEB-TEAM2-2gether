package com.dbidding.upload.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuctionImageUploadAdapterTest {

    private final AuctionImageUploadAdapter adapter = new AuctionImageUploadAdapter();

    @Test
    void resolvesUploadTokensAsOrderedAuctionImages() {
        var images = adapter.resolveImages(List.of(
                "upload/2026/07/31/11111111-1111-1111-1111-111111111111.jpg",
                "upload/2026/07/31/22222222-2222-2222-2222-222222222222.webp"
        ));

        assertThat(images)
                .extracting("imagePath", "sortOrder", "representative")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "upload/2026/07/31/11111111-1111-1111-1111-111111111111.jpg", 0, true),
                        org.assertj.core.groups.Tuple.tuple(
                                "upload/2026/07/31/22222222-2222-2222-2222-222222222222.webp", 1, false)
                );
    }
}
