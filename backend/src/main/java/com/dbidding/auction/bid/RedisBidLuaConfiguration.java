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
}
