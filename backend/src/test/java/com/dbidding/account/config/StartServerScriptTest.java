package com.dbidding.account.config;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StartServerScriptTest {

    @Test
    void JWT_SECRET_없이도_Redis_연결_검증까지_진행한다() throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder("bash", "scripts/start-server.sh", "true")
            .redirectErrorStream(true);
        Map<String, String> environment = processBuilder.environment();
        String path = environment.get("PATH");
        environment.clear();
        if (path != null) {
            environment.put("PATH", path);
        }
        environment.put("REDIS_HOST", "127.0.0.1");
        environment.put("REDIS_PORT", "1");
        environment.put("REDIS_USERNAME", "default");
        environment.put("REDIS_PASSWORD", "redis");
        environment.put("REDIS_WAIT_SECONDS", "1");

        Process process = processBuilder.start();
        boolean completed = process.waitFor(5, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isNotZero();
        assertThat(output).doesNotContain("JWT_SECRET 환경변수가 필요합니다.");
        assertThat(output).contains("Redis 연결을 기다립니다");
    }
}
