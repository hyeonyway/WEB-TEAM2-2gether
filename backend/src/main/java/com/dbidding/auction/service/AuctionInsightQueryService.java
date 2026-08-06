package com.dbidding.auction.service;

import com.dbidding.auction.repository.AuctionInsightQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionInsightQueryService {
    private final AuctionInsightQueryRepository repository;

    public AuctionInsight getOpenAuctionInsight() {
        var aggregate = repository.aggregateOpenAuctionInsight();
        return new AuctionInsight(
                aggregate.getTotalCount(),
                aggregate.getRisingCount(),
                aggregate.getAverageRisingRate(),
                aggregate.getBidAuctionCount()
        );
    }

    public record AuctionInsight(
            Long totalCount,
            Long risingCount,
            Double averageRisingRate,
            Long bidAuctionCount
    ) {
    }
}
