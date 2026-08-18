package com.dbidding.auction.bid.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Profile("redis")
@Configuration
public class RedisBidLuaConfiguration {

    @Bean
    public RedisScript<String> bidAcceptScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/bid-accept.lua"));
        script.setResultType(String.class);
        return script;
    }

    @Bean
    public RedisScript<String> walletTransitionScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/wallet-transition.lua"));
        script.setResultType(String.class);
        return script;
    }

    @Bean
    public RedisScript<Long> walletBootstrapScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/wallet-bootstrap.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<Long> auctionStateSeedScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/auction-state-seed.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<String> auctionCloseRequestScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/auction-close-request.lua"));
        script.setResultType(String.class);
        return script;
    }

    @Bean
    public RedisScript<String> auctionEndingTransitionScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/auction-ending-transition.lua"));
        script.setResultType(String.class);
        return script;
    }

    @Bean
    public RedisScript<Long> auctionActiveIndexGcScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/auction-active-index-gc.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<Long> auctionEndingWindowIndexGcScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/auction-ending-window-index-gc.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<Long> cardActiveAuctionCountScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/card-active-auction-count.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<String> auctionCreateScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/auction-create.lua"));
        script.setResultType(String.class);
        return script;
    }

    @Bean
    public RedisScript<String> orderWalletTransitionScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/order-wallet-transition.lua"));
        script.setResultType(String.class);
        return script;
    }

    @Bean
    public RedisScript<Long> orderStateSeedScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/order-state-seed.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<String> orderStateReadScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/order-state-read.lua"));
        script.setResultType(String.class);
        return script;
    }

    @Bean
    public RedisScript<Long> auctionSequenceSyncScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/auction-sequence-sync.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
