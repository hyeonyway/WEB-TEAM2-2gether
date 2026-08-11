package com.dbidding.wallet.service;

import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.dto.WalletTransactionResponse;
import com.dbidding.wallet.exception.IdempotencyConflictException;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.exception.InvalidWalletAmountException;
import com.dbidding.wallet.metrics.WalletMetrics;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Redis Lua 승인 결과를 기존 지갑 API 계약으로 변환한다. */
@Service
@Profile("redis")
public class RedisWalletService extends WalletService {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> walletTransitionScript;
    private final Clock clock;

    public RedisWalletService(
            WalletRepository walletRepository, PointRecordRepository pointRecordRepository,
            WalletHoldRepository walletHoldRepository, WalletMetrics walletMetrics, Clock clock,
            StringRedisTemplate redisTemplate, RedisScript<String> walletTransitionScript
    ) {
        super(walletRepository, pointRecordRepository, walletHoldRepository, walletMetrics, clock);
        this.redisTemplate = redisTemplate;
        this.walletTransitionScript = walletTransitionScript;
        this.clock = clock;
    }

    @Override
    public WalletBalanceResponse getBalance(Integer userId) {
        Object available = redisTemplate.opsForHash().get(balanceKey(userId), "availableBalance");
        Object frozen = redisTemplate.opsForHash().get(balanceKey(userId), "frozenBalance");
        if (available == null || frozen == null) {
            return super.getBalance(userId);
        }
        long availableBalance = Long.parseLong(available.toString());
        long frozenBalance = Long.parseLong(frozen.toString());
        return new WalletBalanceResponse(availableBalance + frozenBalance, frozenBalance, availableBalance);
    }

    @Override
    @Transactional
    public void provision(Integer userId) {
        super.provision(userId);
        redisTemplate.opsForHash().putAll(balanceKey(userId), java.util.Map.of(
                "availableBalance", "0", "frozenBalance", "0", "walletVersion", "0"
        ));
    }

    @Override
    public WalletTransactionResponse charge(Integer userId, long amount, String idempotencyKey) {
        if (amount < 1_000L) throw new InvalidWalletAmountException("충전 금액은 1,000원 이상이어야 합니다.");
        return transition(userId, amount, idempotencyKey, "wallet.charged.v1");
    }

    @Override
    public WalletTransactionResponse refund(Integer userId, long amount, String idempotencyKey) {
        if (amount <= 0) throw new InvalidWalletAmountException("환불 금액은 0원보다 커야 합니다.");
        return transition(userId, amount, idempotencyKey, "wallet.refunded.v1");
    }

    @Override
    public WalletTransactionResponse settle(Integer sellerId, Integer auctionId, long amount) {
        if (amount <= 0) throw new InvalidWalletAmountException("정산 금액은 0원보다 커야 합니다.");
        return transition(sellerId, amount, "settlement:" + auctionId, "wallet.settled.v1");
    }

    @Override
    public WalletTransactionResponse cancelRefund(Integer buyerId, Integer auctionId, long amount) {
        if (amount <= 0) throw new InvalidWalletAmountException("환불 금액은 0원보다 커야 합니다.");
        return transition(buyerId, amount, "cancel-refund:" + auctionId, "wallet.cancel-refunded.v1");
    }

    private WalletTransactionResponse transition(Integer userId, long amount, String idempotencyKey, String eventType) {
        String requestHash = eventType + ":" + amount;
        String raw = redisTemplate.execute(walletTransitionScript, List.of(
                balanceKey(userId), "wallet:idempotency:" + userId + ":" + idempotencyKey, "auction:timeline-events"
        ), UUID.randomUUID().toString(), eventType, userId.toString(), String.valueOf(amount), idempotencyKey, requestHash,
                Instant.now(clock).toString());
        String[] fields = raw.split("\\|", -1);
        if (!"ACCEPTED".equals(fields[0])) {
            if ("INSUFFICIENT_BALANCE".equals(fields.length > 1 ? fields[1] : "")) throw new InsufficientAvailableBalanceException();
            if ("IDEMPOTENCY_CONFLICT".equals(fields.length > 1 ? fields[1] : "")) throw new IdempotencyConflictException();
            throw new IllegalStateException("Redis 지갑 상태가 올바르지 않습니다.");
        }
        long balance = Long.parseLong(fields[2]) + Long.parseLong(fields[3]);
        return new WalletTransactionResponse(null, eventType, "wallet.refunded.v1".equals(eventType) ? -amount : amount, balance);
    }

    private String balanceKey(Integer userId) { return "wallet:balance:" + userId; }
}
