package com.dbidding.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimeConfigTest {

    @Test
    void 애플리케이션_Clock은_UTC를_사용한다() {
        assertThat(new TimeConfig().clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
