package com.dbidding.auth;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StartServerScriptTest {

    @Test
    void JWT_SECRET이_없으면_DB_접속_전에_실행을_중단한다() throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder("bash", "scripts/start-server.sh", "true")
            .redirectErrorStream(true);
        Map<String, String> environment = processBuilder.environment();
        String path = environment.get("PATH");
        environment.clear();
        if (path != null) {
            environment.put("PATH", path);
        }
        environment.put("DB_HOST", "127.0.0.1");
        environment.put("DB_PORT", "1");
        environment.put("DB_NAME", "dbidding");
        environment.put("DB_USERNAME", "dbidding");
        environment.put("DB_PASSWORD", "dbidding");
        environment.put("DB_SCHEMA_WAIT_SECONDS", "1");
        environment.put("SCHEMA_FILE", "/dev/null");

        Process process = processBuilder.start();
        boolean completed = process.waitFor(5, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(completed).isTrue();
        assertThat(process.exitValue()).isNotZero();
        assertThat(output).contains("JWT_SECRET 환경변수가 필요합니다.");
        assertThat(output).doesNotContain("MySQL 연결을 기다립니다");
    }
}
