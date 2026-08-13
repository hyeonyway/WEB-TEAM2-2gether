package com.dbidding.auction.service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class RandomEndingExtensionProvider implements EndingExtensionProvider {
    private static final long MIN_SECONDS = 60;
    private static final long MAX_SECONDS = 120;

    @Override
    public Duration next() {
        return Duration.ofSeconds(ThreadLocalRandom.current().nextLong(MIN_SECONDS, MAX_SECONDS + 1));
    }
}
