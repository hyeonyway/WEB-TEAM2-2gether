package com.dbidding.auction.adapter;

import com.dbidding.auction.port.AuctionEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!auction-mock")
@RequiredArgsConstructor
public class SpringAuctionEventPublisher implements AuctionEventPort {
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(AuctionEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
