package com.dbidding.global.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** 같은 Redis key의 동시 state miss를 하나의 초기화 작업으로 합친다. */
@Component
public class RedisStateSingleFlight {
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> inFlight = new ConcurrentHashMap<>();

    public boolean execute(String key, Supplier<Boolean> supplier) {
        CompletableFuture<Boolean> created = new CompletableFuture<>();
        CompletableFuture<Boolean> current = inFlight.putIfAbsent(key, created);
        if (current == null) {
            try {
                created.complete(supplier.get());
            } catch (RuntimeException exception) {
                created.completeExceptionally(exception);
            } finally {
                inFlight.remove(key, created);
            }
            current = created;
        }
        return current.join();
    }
}
