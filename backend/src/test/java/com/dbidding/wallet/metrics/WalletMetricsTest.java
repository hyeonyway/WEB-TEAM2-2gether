package com.dbidding.wallet.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class WalletMetricsTest {

    private SimpleMeterRegistry registry;
    private WalletMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new WalletMetrics(registry);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void Wallet_작업의_성공_처리시간을_기록한다() {
        String result = metrics.observe(WalletMetrics.Operation.HOLD, () -> "held");

        assertThat(result).isEqualTo("held");
        assertThat(timerCount("hold", "success")).isEqualTo(1);
    }

    @Test
    void 예상된_도메인_예외는_rejected로_기록한다() {
        assertThatThrownBy(() -> metrics.observe(
                WalletMetrics.Operation.HOLD,
                () -> {
                    throw new InsufficientAvailableBalanceException();
                }
        )).isInstanceOf(InsufficientAvailableBalanceException.class);

        assertThat(timerCount("hold", "rejected")).isEqualTo(1);
        assertThat(timerCount("hold", "error")).isZero();
    }

    @Test
    void 예상하지_못한_예외는_error로_기록한다() {
        assertThatThrownBy(() -> metrics.observe(
                WalletMetrics.Operation.CAPTURE,
                () -> {
                    throw new IllegalStateException("unexpected");
                }
        )).isInstanceOf(IllegalStateException.class);

        assertThat(timerCount("capture", "error")).isEqualTo(1);
    }

    @Test
    void 트랜잭션이_커밋된_뒤에만_committed를_기록한다() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.observe(WalletMetrics.Operation.RELEASE, () -> "released");

        assertThat(outcomeCount("release", "committed")).isZero();
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(outcomeCount("release", "committed")).isEqualTo(1);
    }

    @Test
    void 상위_트랜잭션이_롤백되면_rollback을_기록한다() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.observe(WalletMetrics.Operation.HOLD, () -> "held");
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(outcomeCount("hold", "rollback")).isEqualTo(1);
        assertThat(outcomeCount("hold", "committed")).isZero();
    }

    @Test
    void Wallet_락_대기시간을_작업별로_기록한다() {
        Timer.Sample sample = metrics.start();

        metrics.finishLockWait(sample, WalletMetrics.Operation.CAPTURE);

        assertThat(registry.get("dbidding.wallet.lock.wait")
                .tag("operation", "capture")
                .timer()
                .count()).isEqualTo(1);
    }

    private void completeTransaction(int status) {
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
    }

    private double timerCount(String operation, String result) {
        return registry.get("dbidding.wallet.operation.duration")
                .tag("operation", operation)
                .tag("result", result)
                .timer()
                .count();
    }

    private double outcomeCount(String operation, String outcome) {
        return registry.get("dbidding.wallet.transaction.outcomes")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .counter()
                .count();
    }
}
