package com.dbidding.auction.stream;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Profile("redis")
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AuctionBidStreamProperties.class)
public class AuctionBidStreamConfiguration {
}
