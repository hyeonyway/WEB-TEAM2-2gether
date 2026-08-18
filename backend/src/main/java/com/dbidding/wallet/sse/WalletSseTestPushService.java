package com.dbidding.wallet.sse;

import com.dbidding.wallet.dto.WalletBalanceResponse;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 실제 지갑 상태 전이 없이 wallet SSE fan-out만 재현하는 테스트 전용 발행자(#569). 실제
 * {@link WalletSsePublisher} 빈(Redis 경로)을 그대로 태워, publish→subscribe→push 전체 경로의
 * fan-out 비용을 측정할 수 있게 한다.
 */
@Service
@Profile("test")
@RequiredArgsConstructor
public class WalletSseTestPushService {
    private static final long INCREMENT = 1_000L;

    private final WalletSsePublisher publisher;
    private final Clock clock;
    private final ConcurrentMap<Integer, Long> walletVersions = new ConcurrentHashMap<>();

    public WalletBalanceChangedEvent publishTestBalanceChange(Integer userId) {
        long version = walletVersions.merge(userId, 1L, Long::sum);
        long totalBalance = 1_000_000L + version * INCREMENT;
        var balance = new WalletBalanceResponse(totalBalance, 0L, totalBalance, version);
        var event = new WalletBalanceChangedEvent(userId, balance, version, clock.instant());
        publisher.publish(event);
        return event;
    }
}
