package com.dbidding.wallet.metrics;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dbidding.wallet.exception.IdempotencyConflictException;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.exception.InvalidIdempotencyKeyException;
import com.dbidding.wallet.exception.InvalidWalletAmountException;
import com.dbidding.wallet.exception.InvalidWalletBalanceException;
import com.dbidding.wallet.exception.InvalidWalletHoldStateException;
import com.dbidding.wallet.exception.WalletAlreadyExistsException;
import com.dbidding.wallet.exception.WalletNotFoundException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class WalletMetrics {

    private static final Duration[] OPERATION_SLOS = {
            Duration.ofMillis(10),
            Duration.ofMillis(50),
            Duration.ofMillis(100),
            Duration.ofMillis(300),
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2)
    };
    private static final Duration[] LOCK_SLOS = {
            Duration.ofMillis(1),
            Duration.ofMillis(5),
            Duration.ofMillis(10),
            Duration.ofMillis(50),
            Duration.ofMillis(100),
            Duration.ofMillis(500),
            Duration.ofSeconds(1)
    };

    private final MeterRegistry registry;
    private final Map<Operation, Map<Result, Timer>> operationTimers = new EnumMap<>(Operation.class);
    private final Map<Operation, Timer> lockTimers = new EnumMap<>(Operation.class);
    private final Map<Operation, Map<TransactionOutcome, Counter>> transactionCounters =
            new EnumMap<>(Operation.class);

    public WalletMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (Operation operation : Operation.values()) {
            Map<Result, Timer> timers = new EnumMap<>(Result.class);
            for (Result result : Result.values()) {
                timers.put(result, Timer.builder("dbidding.wallet.operation.duration")
                        .description("Wallet hold/release/capture 처리시간")
                        .tag("operation", operation.tag())
                        .tag("result", result.tag())
                        .publishPercentileHistogram()
                        .serviceLevelObjectives(OPERATION_SLOS)
                        .register(registry));
            }
            operationTimers.put(operation, timers);

            lockTimers.put(operation, Timer.builder("dbidding.wallet.lock.wait")
                    .description("Wallet row 비관적 락 획득 대기시간")
                    .tag("operation", operation.tag())
                    .publishPercentileHistogram()
                    .serviceLevelObjectives(LOCK_SLOS)
                    .register(registry));

            Map<TransactionOutcome, Counter> counters = new EnumMap<>(TransactionOutcome.class);
            for (TransactionOutcome outcome : TransactionOutcome.values()) {
                counters.put(outcome, Counter.builder("dbidding.wallet.transaction.outcomes")
                        .description("Wallet 작업이 참여한 트랜잭션의 최종 결과")
                        .tag("operation", operation.tag())
                        .tag("outcome", outcome.tag())
                        .register(registry));
            }
            transactionCounters.put(operation, counters);
        }
    }

    public <T> T observe(Operation operation, Supplier<T> action) {
        Timer.Sample sample = start();
        try {
            T result = action.get();
            sample.stop(operationTimers.get(operation).get(Result.SUCCESS));
            registerTransactionOutcome(operation);
            return result;
        } catch (RuntimeException exception) {
            Result result = isExpectedRejection(exception) ? Result.REJECTED : Result.ERROR;
            sample.stop(operationTimers.get(operation).get(result));
            registerTransactionOutcome(operation);
            throw exception;
        }
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void finishLockWait(Timer.Sample sample, Operation operation) {
        sample.stop(lockTimers.get(operation));
    }

    private void registerTransactionOutcome(Operation operation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionOutcome outcome = status == STATUS_COMMITTED
                        ? TransactionOutcome.COMMITTED
                        : TransactionOutcome.ROLLBACK;
                transactionCounters.get(operation).get(outcome).increment();
            }
        });
    }

    private boolean isExpectedRejection(RuntimeException exception) {
        return exception instanceof IdempotencyConflictException
                || exception instanceof InsufficientAvailableBalanceException
                || exception instanceof InvalidIdempotencyKeyException
                || exception instanceof InvalidWalletAmountException
                || exception instanceof InvalidWalletBalanceException
                || exception instanceof InvalidWalletHoldStateException
                || exception instanceof WalletAlreadyExistsException
                || exception instanceof WalletNotFoundException;
    }

    public enum Operation {
        HOLD("hold"),
        RELEASE("release"),
        CAPTURE("capture");

        private final String tag;

        Operation(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    private enum Result {
        SUCCESS("success"),
        REJECTED("rejected"),
        ERROR("error");

        private final String tag;

        Result(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    private enum TransactionOutcome {
        COMMITTED("committed"),
        ROLLBACK("rollback");

        private final String tag;

        TransactionOutcome(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }
}
