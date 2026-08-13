package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomEndingExtensionProviderTest {
    private final RandomEndingExtensionProvider provider = new RandomEndingExtensionProvider();

    @Test
    void 매번_60초에서_120초_사이의_연장값을_돌려준다() {
        IntStream.range(0, 200).forEach(ignored -> {
            Duration extension = provider.next();

            assertThat(extension).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(120));
        });
    }
}
