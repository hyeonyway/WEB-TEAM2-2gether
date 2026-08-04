package com.dbidding.global.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {
    @Bean
    public Clock clock() {
        return Clock.system(ZoneOffset.UTC);
    }
}
