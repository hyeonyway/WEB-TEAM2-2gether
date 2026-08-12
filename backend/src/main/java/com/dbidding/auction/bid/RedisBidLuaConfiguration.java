package com.dbidding.auction.bid;

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
    public RedisScript<Long> auctionCloseRequestScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/auction-close-request.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
