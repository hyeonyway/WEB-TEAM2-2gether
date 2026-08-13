package com.dbidding.auction.service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!redis")
public class RandomEndingExtensionProvider implements EndingExtensionProvider {
    private static final long MIN_SECONDS = 60;
    private static final long MAX_SECONDS = 120;

    @Override
    public Duration next() {
        return Duration.ofSeconds(ThreadLocalRandom.current().nextLong(MIN_SECONDS, MAX_SECONDS + 1));
    }
}
