package com.dbidding.global.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class UtcTime {
    private UtcTime() {
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
