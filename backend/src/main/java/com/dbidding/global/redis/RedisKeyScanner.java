package com.dbidding.global.redis;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public final class RedisKeyScanner {

    private RedisKeyScanner() {
    }

    /**
     * KEYS 커맨드 대신 SCAN(커서 기반, 논블로킹)으로 순회한다. 매 호출은 커서 0에서 새로 시작해
     * 끝(커서가 다시 0)까지 훑는다 - limit은 정상적인 1회 실행 상한이 아니라, 예상 밖으로 매칭되는
     * 키가 폭증했을 때 무한정 오래 걸리는 것만 막는 안전 상한이다. limit을 "매 실행마다 처리할
     * 정상적인 배치 크기"로 작게 잡으면, 매칭 키 수가 limit을 넘는 순간부터 매 실행이 커서 0에서
     * 시작해 항상 같은 앞부분만 다시 모으고 뒤쪽 키에는 영원히 도달하지 못하는 문제가 생긴다.
     */
    public static List<String> scanKeys(StringRedisTemplate redisTemplate, String pattern, int limit) {
        List<String> keys = new ArrayList<>();
        try (Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(connection ->
                connection.keyCommands().scan(ScanOptions.scanOptions().match(pattern).count(500).build()))) {
            while (cursor.hasNext() && keys.size() < limit) {
                keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        }
        return keys;
    }

    /** SCAN으로 찾은 키를 batchSize만큼씩 묶어 script(KEYS=배치, ARGV 없음)를 반복 실행하고 제거된 개수를 합산한다. */
    public static long scanAndCleanup(StringRedisTemplate redisTemplate, String pattern, int scanLimit, int batchSize, RedisScript<Long> script) {
        List<String> keys = scanKeys(redisTemplate, pattern, scanLimit);
        long removed = 0;
        for (int offset = 0; offset < keys.size(); offset += batchSize) {
            List<String> batch = keys.subList(offset, Math.min(offset + batchSize, keys.size()));
            Long result = redisTemplate.execute(script, batch);
            removed += result == null ? 0 : result;
        }
        return removed;
    }

    /** scanAndCleanup과 같지만, 배치의 각 키에 대응하는 ARGV 하나를 keyToArg로 계산해 KEYS와 나란히 전달한다. */
    public static long scanAndCleanup(StringRedisTemplate redisTemplate, String pattern, int scanLimit, int batchSize,
                                       RedisScript<Long> script, java.util.function.Function<String, String> keyToArg) {
        List<String> keys = scanKeys(redisTemplate, pattern, scanLimit);
        long removed = 0;
        for (int offset = 0; offset < keys.size(); offset += batchSize) {
            List<String> batch = keys.subList(offset, Math.min(offset + batchSize, keys.size()));
            Object[] args = batch.stream().map(keyToArg).toArray();
            Long result = redisTemplate.execute(script, batch, args);
            removed += result == null ? 0 : result;
        }
        return removed;
    }
}
