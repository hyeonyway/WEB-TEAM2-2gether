package com.dbidding.auction.adapter;

import com.dbidding.auction.port.AuctionCardStatisticPort;
import com.dbidding.statistics.service.ItemStatisticCommandService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!auction-mock")
@RequiredArgsConstructor
public class AuctionCardStatisticAdapter implements AuctionCardStatisticPort {
    private final ItemStatisticCommandService itemStatisticCommandService;

    @Override
    public void recordAuctionOpened(Integer itemId, LocalDateTime date) {
        itemStatisticCommandService.recordAuctionOpened(itemId, date);
    }

    @Override
    public void recordBid(Integer itemId, LocalDateTime date) {
        itemStatisticCommandService.recordBid(itemId, date);
    }

    @Override
    public void recordAuctionCompleted(Integer itemId, long winningPrice, LocalDateTime date) {
        itemStatisticCommandService.recordAuctionCompleted(itemId, winningPrice, date);
    }

    @Override
    public void recordAuctionClosedWithoutTrade(Integer itemId, LocalDateTime date) {
        itemStatisticCommandService.recordAuctionClosedWithoutTrade(itemId, date);
    }
}
