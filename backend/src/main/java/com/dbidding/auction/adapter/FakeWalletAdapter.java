package com.dbidding.auction.adapter;

import com.dbidding.auction.port.WalletPort;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("auction-mock")
public class FakeWalletAdapter implements WalletPort {
    private static final long DEFAULT_BALANCE = 1_000_000L;

    private final Map<Integer, Long> balances = new ConcurrentHashMap<>();
    private final Map<HoldKey, Long> holds = new ConcurrentHashMap<>();

    @Override
    public WalletSnapshot getWallet(Integer userId) {
        return snapshot(userId);
    }

    @Override
    public WalletSnapshot holdBidAmount(Integer userId, Integer auctionId, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("동결 금액은 0 이상이어야 합니다.");
        }
        balances.putIfAbsent(userId, DEFAULT_BALANCE);
        HoldKey key = new HoldKey(userId, auctionId);
        long previousHold = holds.getOrDefault(key, 0L);
        long additionalHold = amount - previousHold;
        if (availableBalance(userId) < additionalHold) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }
        holds.put(key, amount);
        return snapshot(userId);
    }

    @Override
    public WalletSnapshot releaseBidHold(Integer userId, Integer auctionId) {
        holds.remove(new HoldKey(userId, auctionId));
        return snapshot(userId);
    }

    @Override
    public WalletSnapshot confirmWinningBid(Integer userId, Integer auctionId, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("확정 차감 금액은 0 이상이어야 합니다.");
        }
        balances.putIfAbsent(userId, DEFAULT_BALANCE);
        holds.remove(new HoldKey(userId, auctionId));
        long balance = balances.get(userId);
        if (balance < amount) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }
        balances.put(userId, balance - amount);
        return snapshot(userId);
    }

    private WalletSnapshot snapshot(Integer userId) {
        balances.putIfAbsent(userId, DEFAULT_BALANCE);
        long frozenBalance = frozenBalance(userId);
        return new WalletSnapshot(balances.get(userId) - frozenBalance, frozenBalance);
    }

    private long availableBalance(Integer userId) {
        balances.putIfAbsent(userId, DEFAULT_BALANCE);
        return balances.get(userId) - frozenBalance(userId);
    }

    private long frozenBalance(Integer userId) {
        return holds.entrySet().stream()
                .filter(entry -> entry.getKey().userId().equals(userId))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private record HoldKey(Integer userId, Integer auctionId) {
    }
}
