package com.dbidding.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class Jackson2ObjectMapperConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("local-sse"))
            .withUserConfiguration(Jackson2ObjectMapperConfig.class);

    @Test
    void local_sse_프로필에서도_Instant를_ISO_문자열로_직렬화하는_Jackson2_mapper를_제공한다() throws Exception {
        contextRunner.run(context -> {
            String json = context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                    .writeValueAsString(Map.of("publishedAt", Instant.parse("2026-08-11T12:00:00Z")));

            assertThat(json).contains("\"publishedAt\":\"2026-08-11T12:00:00Z\"");
        });
    }
}
