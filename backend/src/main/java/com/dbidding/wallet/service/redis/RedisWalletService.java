package com.dbidding.wallet.service.redis;

import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.dto.WalletTransactionResponse;
import com.dbidding.wallet.domain.WalletAmountPolicy;
import com.dbidding.wallet.exception.IdempotencyConflictException;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.exception.InvalidWalletAmountException;
import com.dbidding.wallet.exception.InvalidIdempotencyKeyException;
import com.dbidding.wallet.metrics.WalletMetrics;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.service.WalletService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.dbidding.global.redis.RedisIntegerValue;

/** Redis Lua 승인 결과를 기존 지갑 API 계약으로 변환한다. */
@Service
@Profile("redis")
public class RedisWalletService extends WalletService {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> walletTransitionScript;
    private final RedisWalletStateSeeder stateSeeder;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public RedisWalletService(
            WalletRepository walletRepository, PointRecordRepository pointRecordRepository,
            WalletHoldRepository walletHoldRepository, WalletMetrics walletMetrics, Clock clock,
            ApplicationEventPublisher eventPublisher,
            StringRedisTemplate redisTemplate, RedisScript<String> walletTransitionScript, RedisWalletStateSeeder stateSeeder
    ) {
        super(walletRepository, pointRecordRepository, walletHoldRepository, walletMetrics, clock, eventPublisher);
        this.redisTemplate = redisTemplate;
        this.walletTransitionScript = walletTransitionScript;
        this.stateSeeder = stateSeeder;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public WalletBalanceResponse getBalance(Integer userId) {
        stateSeeder.seedIfAbsent(userId);
        Object available = redisTemplate.opsForHash().get(balanceKey(userId), "availableBalance");
        Object frozen = redisTemplate.opsForHash().get(balanceKey(userId), "frozenBalance");
        Object version = redisTemplate.opsForHash().get(balanceKey(userId), "walletVersion");
        if (available == null || frozen == null || version == null) {
            return super.getBalance(userId);
        }
        long availableBalance = RedisIntegerValue.parseLongExact(available.toString());
        long frozenBalance = RedisIntegerValue.parseLongExact(frozen.toString());
        return new WalletBalanceResponse(Math.addExact(availableBalance, frozenBalance), frozenBalance, availableBalance,
                RedisIntegerValue.parseLongExact(version.toString()));
    }

    @Override
    @Transactional
    public void provision(Integer userId) {
        super.provision(userId);
        String key = balanceKey(userId);
        redisTemplate.opsForHash().putAll(key, java.util.Map.of(
                "availableBalance", "0", "frozenBalance", "0", "walletVersion", "0"
        ));
        // wallet:balance는 종결 상태가 없어 항상 미래에 다시 쓰일 수 있으므로 TTL을 걸지 않는다
        // (259443e1에서 다른 Lua 경로들은 이미 제거했으나 이 Java 경로가 누락돼 있었다).
    }

    @Override
    public WalletTransactionResponse charge(Integer userId, long amount, String idempotencyKey) {
        if (amount < 1_000L) throw new InvalidWalletAmountException("충전 금액은 1,000원 이상이어야 합니다.");
        WalletAmountPolicy.validateTransactionAmount(amount);
        validateIdempotencyKey(idempotencyKey);
        return transition(userId, amount, idempotencyKey, "wallet.charged.v1");
    }

    @Override
    public WalletTransactionResponse refund(Integer userId, long amount, String idempotencyKey) {
        if (amount <= 0) throw new InvalidWalletAmountException("환불 금액은 0원보다 커야 합니다.");
        WalletAmountPolicy.validateTransactionAmount(amount);
        validateIdempotencyKey(idempotencyKey);
        return transition(userId, amount, idempotencyKey, "wallet.refunded.v1");
    }

    @Override
    public WalletTransactionResponse settle(Integer sellerId, Integer auctionId, long amount) {
        if (amount <= 0) throw new InvalidWalletAmountException("정산 금액은 0원보다 커야 합니다.");
        WalletAmountPolicy.validateBalanceAmount(amount);
        return transition(sellerId, amount, "settlement:" + auctionId, "wallet.settled.v1");
    }

    @Override
    public WalletTransactionResponse cancelRefund(Integer buyerId, Integer auctionId, long amount) {
        if (amount <= 0) throw new InvalidWalletAmountException("환불 금액은 0원보다 커야 합니다.");
        WalletAmountPolicy.validateBalanceAmount(amount);
        return transition(buyerId, amount, "cancel-refund:" + auctionId, "wallet.cancel-refunded.v1");
    }

    private WalletTransactionResponse transition(Integer userId, long amount, String idempotencyKey, String eventType) {
        stateSeeder.seedIfAbsent(userId);
        String requestHash = eventType + ":" + amount;
        String raw = redisTemplate.execute(walletTransitionScript, List.of(
                balanceKey(userId), "wallet:idempotency:" + userId + ":" + idempotencyKey, "event:timeline"
        ), UUID.randomUUID().toString(), eventType, userId.toString(), String.valueOf(amount), idempotencyKey, requestHash,
                Instant.now(clock).toString(), String.valueOf(WalletAmountPolicy.MAX_TRANSACTION_AMOUNT),
                String.valueOf(WalletAmountPolicy.MAX_BALANCE));
        String[] fields = raw.split("\\|", -1);
        if (!"ACCEPTED".equals(fields[0])) {
            String reason = fields.length > 1 ? fields[1] : "";
            if ("INSUFFICIENT_BALANCE".equals(reason)) throw new InsufficientAvailableBalanceException();
            if ("IDEMPOTENCY_CONFLICT".equals(reason)) throw new IdempotencyConflictException();
            if ("AMOUNT_LIMIT_EXCEEDED".equals(reason)) {
                throw new InvalidWalletAmountException("1회 거래 금액은 1,000억 원 이하여야 합니다.");
            }
            if ("BALANCE_LIMIT_EXCEEDED".equals(reason)) {
                throw new InvalidWalletAmountException("지갑 총 보유액은 1조 원 이하여야 합니다.");
            }
            throw new IllegalStateException("Redis 지갑 상태가 올바르지 않습니다.");
        }
        if (fields.length != 6) throw new IllegalStateException("Redis 지갑 승인 응답이 올바르지 않습니다.");
        long availableBalance = RedisIntegerValue.parseLongExact(fields[2]);
        long frozenBalance = RedisIntegerValue.parseLongExact(fields[3]);
        long walletVersion = RedisIntegerValue.parseLongExact(fields[4]);
        long balance = Math.addExact(availableBalance, frozenBalance);
        if (!Boolean.parseBoolean(fields[5])) {
            eventPublisher.publishEvent(new com.dbidding.wallet.sse.WalletBalanceChangedEvent(
                    userId,
                    new WalletBalanceResponse(balance, frozenBalance, availableBalance, walletVersion),
                    walletVersion,
                    Instant.now(clock)
            ));
        }
        return new WalletTransactionResponse(null, eventType, "wallet.refunded.v1".equals(eventType) ? -amount : amount, balance);
    }

    private String balanceKey(Integer userId) { return "wallet:balance:" + userId; }

    /**
     * Redis 입찰 Stream을 MySQL에 projection할 때도 부모의 hold/release/capture를 재사용한다.
     * 이 시점에 DB 버전을 다시 증가시키면 Redis walletVersion과 두 개의 독립 카운터가 되므로,
     * browser SSE는 Redis Lua 승인 직후 원본 버전으로 발행하므로 projection에서는 발행하지 않는다.
     */
    @Override
    protected void publishBalanceChanged(com.dbidding.wallet.domain.Wallet wallet, WalletBalanceResponse balance) {
        // Redis가 승인·버전 부여의 단일 원본이다.
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 64) throw new InvalidIdempotencyKeyException();
    }
}
