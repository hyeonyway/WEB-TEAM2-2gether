package com.dbidding.auction.adapter;

import com.dbidding.auction.repository.StatisticAuctionQueryRepository;
import com.dbidding.statistic.port.StatisticAuctionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatisticAuctionAdapter implements StatisticAuctionPort {
    private final StatisticAuctionQueryRepository repository;

    @Override
    public InsightAggregate aggregateInsights() {
        var aggregate = repository.aggregateInsights();
        return new InsightAggregate(
                aggregate.getTotalCount(),
                aggregate.getRisingCount(),
                aggregate.getAverageRisingRate(),
                aggregate.getBidAuctionCount()
        );
    }
}
